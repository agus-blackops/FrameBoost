#!/usr/bin/env node
'use strict';
/*
 * Sonora para PC: servidor local sin dependencias.
 *  - sirve la interfaz (public/)
 *  - hace de proxy de las APIs (Deezer, Apple Podcasts, radio-browser, LRCLIB, feeds RSS)
 *    para que el navegador no choque con CORS
 *  - indexa tu carpeta de música y la reproduce con soporte de Range
 * Solo escucha en 127.0.0.1 y rechaza peticiones con otro Host (protección contra DNS rebinding).
 */
const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const os = require('os');
const dns = require('dns').promises;
const net = require('net');
const { spawn } = require('child_process');
const crypto = require('crypto');
const { readTags } = require('./lib/id3');

const args = process.argv.slice(2);
const argVal = (name) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
};

const PORT = Number(argVal('--port') || process.env.PORT || 5174);
const MUSIC_DIR = path.resolve(argVal('--music') || process.env.SONORA_MUSIC_DIR || path.join(os.homedir(), 'Music'));
const ALLOW_PRIVATE = process.env.SONORA_ALLOW_PRIVATE === '1'; // solo para pruebas
const PUBLIC = path.join(__dirname, 'public');
const CACHE_MS = process.env.SONORA_CACHE_MS !== undefined ? Number(process.env.SONORA_CACHE_MS) : 60000;

const UPSTREAMS = Object.assign({
  deezer: 'https://api.deezer.com',
  apple: 'https://itunes.apple.com',
  appleTop: 'https://rss.applemarketingtools.com',
  radio: [
    'https://de1.api.radio-browser.info', 'https://de2.api.radio-browser.info',
    'https://fi1.api.radio-browser.info', 'https://nl1.api.radio-browser.info',
    'https://at1.api.radio-browser.info', 'https://all.api.radio-browser.info',
  ],
  lrclib: 'https://lrclib.net',
}, process.env.SONORA_UPSTREAMS ? JSON.parse(process.env.SONORA_UPSTREAMS) : {});

const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon', '.json': 'application/json; charset=utf-8',
  '.mp3': 'audio/mpeg', '.m4a': 'audio/mp4', '.aac': 'audio/aac', '.ogg': 'audio/ogg', '.opus': 'audio/ogg',
  '.flac': 'audio/flac', '.wav': 'audio/wav', '.webm': 'audio/webm',
};
const AUDIO_EXT = new Set(['.mp3', '.m4a', '.aac', '.ogg', '.opus', '.flac', '.wav']);

// ---------------------------------------------------------------- utilidades

function send(res, code, body, headers) {
  res.writeHead(code, Object.assign({ 'Cache-Control': 'no-store' }, headers));
  res.end(body);
}

function sendJson(res, code, obj) {
  send(res, code, JSON.stringify(obj), { 'Content-Type': 'application/json; charset=utf-8' });
}

/** Direcciones que no deben alcanzarse desde el proxy de feeds (evita usarlo contra tu red local). */
function isPrivateAddress(addr) {
  if (net.isIPv4(addr)) {
    const [a, b] = addr.split('.').map(Number);
    return a === 10 || a === 127 || a === 0 || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168) || (a === 100 && b >= 64 && b <= 127);
  }
  if (net.isIPv6(addr)) {
    const l = addr.toLowerCase();
    return l === '::1' || l === '::' || l.startsWith('fc') || l.startsWith('fd') || l.startsWith('fe80') ||
      (l.startsWith('::ffff:') && isPrivateAddress(l.slice(7)));
  }
  return true;
}

async function assertPublicUrl(urlStr) {
  const u = new URL(urlStr);
  if (u.protocol !== 'http:' && u.protocol !== 'https:') throw new Error('protocolo no permitido');
  if (ALLOW_PRIVATE) return u;
  const host = u.hostname.replace(/^\[|\]$/g, ''); // las direcciones IPv6 llegan entre corchetes
  const addrs = net.isIP(host) ? [{ address: host }] : await dns.lookup(host, { all: true });
  if (!addrs.length || addrs.some((a) => isPrivateAddress(a.address))) throw new Error('dirección no permitida');
  return u;
}

/** GET con redirecciones manuales (http<->https), límite de tamaño y de tiempo. */
async function fetchLimited(urlStr, { maxBytes = 8 * 1024 * 1024, checkPublic = false, headers = {} } = {}) {
  let url = urlStr;
  for (let hop = 0; hop < 6; hop++) {
    if (checkPublic) await assertPublicUrl(url);
    const res = await fetch(url, {
      redirect: 'manual',
      signal: AbortSignal.timeout(15000),
      headers: Object.assign({ 'User-Agent': 'Sonora-PC/1.6.2', 'Accept-Language': 'es,en;q=0.8' }, headers),
    });
    if (res.status >= 300 && res.status < 400 && res.headers.get('location')) {
      url = new URL(res.headers.get('location'), url).toString();
      continue;
    }
    if (!res.ok) {
      const err = new Error('HTTP ' + res.status);
      err.status = res.status;
      throw err;
    }
    const buf = Buffer.from(await res.arrayBuffer());
    if (buf.length > maxBytes) throw new Error('respuesta demasiado grande');
    return { body: buf, type: res.headers.get('content-type') || 'application/octet-stream' };
  }
  throw new Error('demasiadas redirecciones');
}

const cache = new Map(); // url -> { at, value }
async function cached(url, opts) {
  const hit = cache.get(url);
  if (CACHE_MS > 0 && hit && Date.now() - hit.at < CACHE_MS) return hit.value;
  const value = await fetchLimited(url, opts);
  if (CACHE_MS > 0) cache.set(url, { at: Date.now(), value });
  if (cache.size > 300) cache.delete(cache.keys().next().value);
  return value;
}

async function proxy(res, url, opts) {
  try {
    const r = await cached(url, opts);
    send(res, 200, r.body, { 'Content-Type': r.type });
  } catch (e) {
    sendJson(res, e.status === 404 ? 404 : 502, { error: String(e.message || e) });
  }
}

// ---------------------------------------------------------------- biblioteca local

let library = [];
let scanning = null;

function parseFileName(file) {
  const base = path.basename(file, path.extname(file)).replace(/_/g, ' ').trim();
  const m = base.match(/^(.{1,80}?)\s+-\s+(.+)$/);
  return m ? { artist: m[1].trim(), title: m[2].trim() } : { artist: '', title: base };
}

function walk(dir, out, depth = 0) {
  if (depth > 8 || out.length >= 5000) return;
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch (e) {
    return;
  }
  for (const e of entries) {
    if (e.name.startsWith('.')) continue;
    const full = path.join(dir, e.name);
    if (e.isDirectory()) walk(full, out, depth + 1);
    else if (e.isFile() && AUDIO_EXT.has(path.extname(e.name).toLowerCase())) out.push(full);
    if (out.length >= 5000) return;
  }
}

function scanLibrary() {
  const files = [];
  walk(MUSIC_DIR, files);
  files.sort();
  library = files.map((file, i) => {
    const ext = path.extname(file).toLowerCase();
    let tags = {};
    if (ext === '.mp3') {
      try {
        tags = readTags(file);
      } catch (e) {
        tags = {};
      }
    }
    const guess = parseFileName(file);
    let added = 0;
    try {
      added = fs.statSync(file).mtimeMs;
    } catch (e) { /* ignorar */ }
    return {
      id: i,
      key: 'local:' + crypto.createHash('sha1').update(file).digest('hex').slice(0, 12),
      file,
      title: tags.title || guess.title,
      artist: tags.artist || guess.artist || '',
      album: tags.album || path.basename(path.dirname(file)),
      hasCover: Boolean(tags.cover),
      added,
      ext,
    };
  });
  return library;
}

function libraryJson() {
  return library.map((t) => ({
    id: t.id, key: t.key, title: t.title, artist: t.artist, album: t.album, hasCover: t.hasCover, added: t.added,
  }));
}

function serveFile(req, res, file, type) {
  let stat;
  try {
    stat = fs.statSync(file);
  } catch (e) {
    return send(res, 404, 'No encontrado');
  }
  const range = req.headers.range && /^bytes=(\d*)-(\d*)$/.exec(req.headers.range);
  if (range) {
    let start = range[1] === '' ? stat.size - Number(range[2]) : Number(range[1]);
    let end = range[1] !== '' && range[2] !== '' ? Number(range[2]) : stat.size - 1;
    if (!(start >= 0) || start > end || start >= stat.size) {
      return send(res, 416, '', { 'Content-Range': 'bytes */' + stat.size });
    }
    end = Math.min(end, stat.size - 1);
    res.writeHead(206, {
      'Content-Type': type, 'Accept-Ranges': 'bytes', 'Content-Length': end - start + 1,
      'Content-Range': `bytes ${start}-${end}/${stat.size}`, 'Cache-Control': 'no-store',
    });
    fs.createReadStream(file, { start, end }).pipe(res);
  } else {
    res.writeHead(200, { 'Content-Type': type, 'Accept-Ranges': 'bytes', 'Content-Length': stat.size, 'Cache-Control': 'no-store' });
    fs.createReadStream(file).pipe(res);
  }
}

// ---------------------------------------------------------------- rutas

async function handleApi(req, res, url) {
  const p = url.pathname;
  const qs = url.search;

  if (p.startsWith('/api/deezer/')) {
    return proxy(res, UPSTREAMS.deezer + '/' + p.slice('/api/deezer/'.length) + qs);
  }
  if (p === '/api/apple/search') return proxy(res, UPSTREAMS.apple + '/search' + qs);
  if (p === '/api/apple/lookup') return proxy(res, UPSTREAMS.apple + '/lookup' + qs);
  if (p.startsWith('/api/apple/top/')) {
    const cc = p.slice('/api/apple/top/'.length).toLowerCase();
    if (!/^[a-z]{2}$/.test(cc)) return sendJson(res, 400, { error: 'país inválido' });
    const mk = (c) => `${UPSTREAMS.appleTop}/api/v2/${c}/podcasts/top/30/podcasts.json`;
    try {
      const r = await cached(mk(cc));
      return send(res, 200, r.body, { 'Content-Type': r.type });
    } catch (e) {
      if (cc === 'us') return sendJson(res, 502, { error: String(e.message) });
      return proxy(res, mk('us'));
    }
  }
  if (p.startsWith('/api/radio/')) {
    const tail = '/' + p.slice('/api/radio/'.length) + qs;
    let last = 'sin servidores';
    for (const base of UPSTREAMS.radio) {
      try {
        const r = await cached(base + tail);
        return send(res, 200, r.body, { 'Content-Type': r.type });
      } catch (e) {
        last = String(e.message || e);
      }
    }
    return sendJson(res, 502, { error: last });
  }
  if (p.startsWith('/api/lrclib/')) {
    return proxy(res, UPSTREAMS.lrclib + '/api/' + p.slice('/api/lrclib/'.length) + qs);
  }
  if (p === '/api/feed') {
    const target = url.searchParams.get('url');
    if (!target) return sendJson(res, 400, { error: 'falta url' });
    return proxy(res, target, { checkPublic: true });
  }
  if (p === '/api/library') {
    if (url.searchParams.get('rescan') === '1' || !scanning) {
      scanning = true;
      scanLibrary();
    }
    return sendJson(res, 200, { dir: MUSIC_DIR, tracks: libraryJson() });
  }
  const m = /^\/media\/(\d+)(\/cover)?$/.exec(p);
  if (m) {
    const t = library[Number(m[1])];
    if (!t) return send(res, 404, 'No encontrado');
    if (m[2]) {
      let cover = null;
      try {
        cover = readTags(t.file).cover;
      } catch (e) { /* sin portada */ }
      if (!cover) return send(res, 404, 'Sin portada');
      return send(res, 200, cover.data, { 'Content-Type': cover.mime || 'image/jpeg', 'Cache-Control': 'max-age=3600' });
    }
    return serveFile(req, res, t.file, MIME[t.ext] || 'application/octet-stream');
  }
  return sendJson(res, 404, { error: 'ruta desconocida' });
}

function handleStatic(req, res, url) {
  let rel = decodeURIComponent(url.pathname);
  if (rel === '/') rel = '/index.html';
  const file = path.normalize(path.join(PUBLIC, rel));
  if (!file.startsWith(PUBLIC + path.sep)) return send(res, 403, 'Prohibido');
  serveFile(req, res, file, MIME[path.extname(file).toLowerCase()] || 'application/octet-stream');
}

function hostAllowed(req) {
  const host = (req.headers.host || '').toLowerCase();
  return host === `127.0.0.1:${PORT}` || host === `localhost:${PORT}`;
}

const server = http.createServer(async (req, res) => {
  try {
    if (!hostAllowed(req)) return send(res, 403, 'Host no permitido');
    if (req.method !== 'GET' && req.method !== 'HEAD') return send(res, 405, 'Método no permitido');
    const url = new URL(req.url, `http://127.0.0.1:${PORT}`);
    if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/media/')) {
      // otro sitio web abierto en tu navegador no debe poder usar este servidor
      const origin = req.headers.origin;
      if (origin && origin !== `http://127.0.0.1:${PORT}` && origin !== `http://localhost:${PORT}`) {
        return send(res, 403, 'Origen no permitido');
      }
      return await handleApi(req, res, url);
    }
    return handleStatic(req, res, url);
  } catch (e) {
    try {
      sendJson(res, 500, { error: String(e.message || e) });
    } catch (e2) { /* respuesta ya cerrada */ }
  }
});

function openBrowser(url) {
  const cmd = process.platform === 'win32' ? ['cmd', ['/c', 'start', '', url]]
    : process.platform === 'darwin' ? ['open', [url]] : ['xdg-open', [url]];
  try {
    spawn(cmd[0], cmd[1], { stdio: 'ignore', detached: true }).on('error', () => {}).unref();
  } catch (e) { /* sin navegador: se muestra la URL */ }
}

if (require.main === module) {
  server.listen(PORT, '127.0.0.1', () => {
    const url = `http://localhost:${PORT}`;
    console.log(`Sonora para PC 1.6.2 → ${url}`);
    console.log(`Tu música: ${MUSIC_DIR}`);
    if (args.includes('--open')) openBrowser(url);
  });
  server.on('error', (e) => {
    console.error(e.code === 'EADDRINUSE' ? `El puerto ${PORT} está ocupado. Usa --port OTRO.` : e.message);
    process.exit(1);
  });
}

module.exports = { server, isPrivateAddress };
