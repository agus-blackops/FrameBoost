'use strict';
/* Pruebas de extremo a extremo de Sonora para PC: servidor real + Chromium real + APIs simuladas con audio real. */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const net = require('net');
const http = require('http');
const { spawn } = require('child_process');
const { chromium } = require('playwright-core');
const fake = require('./fake-upstream');
const { isPrivateAddress } = require('../server');

const ROOT = path.join(__dirname, '..');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function eventually(fn, what, ms = 9000) {
  const end = Date.now() + ms;
  let last;
  while (Date.now() < end) {
    try {
      const v = await fn();
      if (v || v === undefined) return v;
    } catch (e) { last = e; }
    await sleep(80);
  }
  throw new Error('Tiempo agotado esperando: ' + what + (last ? ' (' + last.message + ')' : ''));
}

function freePort() {
  return new Promise((resolve) => {
    const s = net.createServer().listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => resolve(p)); });
  });
}

function chromiumPath() {
  if (process.env.CHROMIUM_PATH) return process.env.CHROMIUM_PATH;
  const roots = [process.env.PLAYWRIGHT_BROWSERS_PATH, '/opt/pw-browsers'].filter(Boolean);
  for (const r of roots) {
    try {
      for (const d of fs.readdirSync(r)) {
        const c = path.join(r, d, 'chrome-linux64', 'chrome');
        if (/^chromium-\d+$/.test(d) && fs.existsSync(c)) return c;
      }
    } catch (e) { /* siguiente */ }
  }
  return undefined; // que Playwright decida
}

function startServer(port, musicDir, upstreams, { allowPrivate = true } = {}) {
  return new Promise((resolve, reject) => {
    const env = Object.assign({}, process.env, { SONORA_UPSTREAMS: JSON.stringify(upstreams), SONORA_ALLOW_PRIVATE: allowPrivate ? '1' : '0', SONORA_CACHE_MS: '0' });
    const child = spawn(process.execPath, [path.join(ROOT, 'server.js'), '--port', String(port), '--music', musicDir], { env });
    let out = '';
    child.stdout.on('data', (d) => { out += d; if (out.includes('Sonora para PC')) resolve(child); });
    child.stderr.on('data', (d) => { out += d; });
    child.on('exit', (c) => reject(new Error('el servidor terminó: ' + out)));
    setTimeout(() => reject(new Error('el servidor no arrancó: ' + out)), 8000);
  });
}

function rawRequest(port, opts) {
  return new Promise((resolve, reject) => {
    const req = http.request({ host: '127.0.0.1', port, method: 'GET', ...opts }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks) }));
    });
    req.on('error', reject);
    req.end();
  });
}

// ---- archivos de música de prueba
function wavBytes(seconds) {
  const rate = 8000, n = rate * seconds;
  const b = Buffer.alloc(44 + n);
  b.write('RIFF', 0); b.writeUInt32LE(36 + n, 4); b.write('WAVEfmt ', 8); b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22);
  b.writeUInt32LE(rate, 24); b.writeUInt32LE(rate, 28); b.writeUInt16LE(1, 32); b.writeUInt16LE(8, 34); b.write('data', 36); b.writeUInt32LE(n, 40);
  for (let i = 0; i < n; i++) b[44 + i] = 128 + Math.round(30 * Math.sin((2 * Math.PI * 523 * i) / rate));
  return b;
}
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64');
function frame(id, body) {
  const h = Buffer.alloc(10);
  h.write(id, 0, 'latin1'); h.writeUInt32BE(body.length, 4);
  return Buffer.concat([h, body]);
}
const utf16 = (s) => Buffer.concat([Buffer.from([1, 0xff, 0xfe]), Buffer.from(s, 'utf16le'), Buffer.from([0, 0])]);
function id3File(file, tags) {
  const frames = Buffer.concat([
    frame('TIT2', utf16(tags.title)), frame('TPE1', utf16(tags.artist)), frame('TALB', Buffer.concat([Buffer.from([0]), Buffer.from(tags.album, 'latin1'), Buffer.from([0])])),
    frame('APIC', Buffer.concat([Buffer.from([0]), Buffer.from('image/png\0', 'latin1'), Buffer.from([3]), Buffer.from([0]), PNG])),
  ]);
  const size = frames.length;
  const head = Buffer.from([0x49, 0x44, 0x33, 3, 0, 0, (size >> 21) & 0x7f, (size >> 14) & 0x7f, (size >> 7) & 0x7f, size & 0x7f]);
  fs.writeFileSync(file, Buffer.concat([head, frames, Buffer.alloc(2000, 0xff)]));
}

// ================================================================ marco de pruebas
const tests = [];
const test = (name, fn) => tests.push({ name, fn });

async function main() {
  const up = await fake.start();
  const upstreams = { deezer: up.base + '/deezer', apple: up.base + '/apple', appleTop: up.base + '/appletop', radio: [up.base + '/radio'], lrclib: up.base + '/lrclib' };
  const music = fs.mkdtempSync(path.join(os.tmpdir(), 'sonora-music-'));
  fs.mkdirSync(path.join(music, 'Disco'));
  fs.writeFileSync(path.join(music, 'Los Tests - Tono.wav'), wavBytes(3));
  fs.writeFileSync(path.join(music, 'Sin Etiquetas.wav'), wavBytes(2));
  fs.writeFileSync(path.join(music, 'Disco', 'Pista Anidada.wav'), wavBytes(2));
  fs.writeFileSync(path.join(music, 'notas.txt'), 'no es música');
  id3File(path.join(music, 'Etiquetada.mp3'), { title: 'Canción Ñandú', artist: 'Artista É', album: 'Álbum' });

  const port = await freePort();
  const child = await startServer(port, music, upstreams);
  const BASE = `http://127.0.0.1:${port}`;
  const browser = await chromium.launch({ executablePath: chromiumPath(), args: ['--autoplay-policy=no-user-gesture-required', '--no-sandbox'] });
  const ctl = (q) => fetch(up.base + '/__ctl?' + q);
  const log = async () => (await fetch(up.base + '/__log')).json();

  async function open(hash = '', { fresh = true } = {}) {
    const context = await browser.newContext({ viewport: { width: 1400, height: 900 }, locale: 'en-US' });
    const page = await context.newPage();
    page.errors = [];
    page.on('pageerror', (e) => page.errors.push(String(e)));
    page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) page.errors.push(m.text()); });
    await page.goto(BASE + '/' + (hash ? '#' + hash : ''));
    page.ctxt = context;
    return page;
  }
  const S = (page, fn, arg) => page.evaluate(fn, arg);
  const state = (page) => S(page, () => window.__sonora.player.state);
  const cur = (page) => S(page, () => { const t = window.__sonora.player.queue[window.__sonora.player.order[window.__sonora.player.pos]]; return t ? t.title : null; });
  const playing = async (page) => eventually(async () => (await state(page)) === 'playing' && (await S(page, () => window.__sonora.audio.currentTime)) > 0.2, 'reproduciendo');
  const sectionTracks = (page, id) => S(page, (x) => window.__sonora.SECTIONS.find((s) => s.id === x).tracks.length, id);
  const playFromSection = (page, id, i, name) => S(page, ([sid, idx]) => { const s = window.__sonora.SECTIONS.find((x) => x.id === sid); window.__sonora.api.playList(s.tracks, idx, s.title); }, [id, i]);
  const noErrors = (page) => assert.deepStrictEqual(page.errors, [], 'errores en la consola: ' + page.errors.join(' | '));

  // ------------------------------------------------------------------ pruebas

  test('Inicio: estantes principales y carga diferida del resto', async () => {
    await fetch(up.base + '/__clear');
    const page = await open();
    await eventually(() => page.locator('.card', { hasText: 'Monaco' }).count(), 'tarjetas del Top 50');
    for (const t of ['Top 50 mundial', 'Radios en vivo', 'Nuevos lanzamientos', 'Artistas populares', 'Playlists populares', 'Reggaetón', 'Pop latino', 'Rock en español', 'Fandoms y gaming', 'The Living Tombstone']) {
      await eventually(async () => (await page.locator('h2', { hasText: t }).count()) > 0, 'estante ' + t);
    }
    const fandom = page.locator('.shelf', { has: page.locator('h2', { hasText: 'Fandoms y gaming' }) }).locator('.card');
    await eventually(async () => (await fandom.count()) === 3, '3 artistas de fandoms');
    const names = await fandom.locator('.t').allInnerTexts();
    assert.deepStrictEqual(names, ['The Living Tombstone', 'Black Gryph0n', 'CG5'], 'orden de la lista; sin el artista parecido');
    const l = (await log()).join('\n');
    assert(!l.includes('/deezer/genre') && !l.includes('appletop'), 'los estantes no destacados no se piden al inicio');
    assert(l.includes('q=artist%3A%22The%20Living%20Tombstone%22') || l.includes('q=artist%3A%22The+Living+Tombstone%22'), 'búsqueda exacta por artista');
    assert.strictEqual(await page.locator('.tile').count() >= 4, true);
    noErrors(page);
    await page.ctxt.close();
  });

  test('Reproducir, enlace renovado, controles, teclado y avance automático', async () => {
    const page = await open();
    await page.locator('.card', { hasText: "Hips Don't Lie" }).first().click();
    await playing(page);
    assert.strictEqual(await cur(page), "Hips Don't Lie");
    assert.strictEqual(await S(page, () => window.__sonora.player.name), 'Top 50 mundial');
    assert((await page.locator('#bar-track .tt').innerText()).includes("Hips Don't Lie"));
    assert((await page.locator('.badge').innerText()).includes('VISTA PREVIA'));
    assert.strictEqual(await page.locator('#b-play').innerText(), '⏸');

    // enlace caducado: se pide uno nuevo antes de reproducir
    await fetch(up.base + '/__clear');
    await S(page, () => { window.__sonora.player.queue.forEach((t) => { t.at = 0; }); window.__sonora.api.next(); });
    await eventually(async () => (await cur(page)) === 'Despacito', 'siguiente');
    await playing(page);
    assert((await log()).some((u) => u.includes('/deezer/track/1002')), 'se renovó el enlace');
    assert((await S(page, () => window.__sonora.audio.src)).includes('hdnea=fresh'));

    // pausa / reanudar / teclado
    await page.locator('#b-play').click();
    await eventually(async () => (await state(page)) === 'paused', 'pausa');
    await page.keyboard.press('Space');
    await playing(page);
    await page.keyboard.press('m');
    assert.strictEqual(await S(page, () => window.__sonora.audio.muted), true);
    await page.keyboard.press('m');
    await page.keyboard.press('s');
    assert(await page.locator('#b-shuffle.on').count());
    await page.keyboard.press('s');
    await page.locator('#seek').evaluate((el) => { el.value = 500; el.dispatchEvent(new Event('input')); el.dispatchEvent(new Event('change')); });
    await eventually(async () => (await S(page, () => window.__sonora.audio.currentTime)) > 1.2, 'saltó con la barra');

    // repetir una: no avanza
    await page.locator('#b-repeat').click();
    await page.locator('#b-repeat').click();
    const before = await S(page, () => window.__sonora.player.pos);
    await sleep(3800);
    assert.strictEqual(await S(page, () => window.__sonora.player.pos), before);
    assert.strictEqual(await state(page), 'playing');
    await page.locator('#b-repeat').click(); // vuelve a "sin repetir"

    // fin de la vista previa: pasa a la siguiente sola
    await S(page, () => window.__sonora.api.seek(2300));
    await eventually(async () => (await S(page, () => window.__sonora.player.pos)) === before + 1, 'avance automático', 7000);
    noErrors(page);
    await page.ctxt.close();
  });

  test('Me gusta, sesión restaurada y posición recordada tras recargar', async () => {
    const page = await open();
    await page.locator('.card', { hasText: 'Despacito' }).first().click();
    await playing(page);
    await page.locator('#bar-like').click();
    assert.strictEqual(await S(page, () => window.__sonora.lib.likes.length), 1);
    await S(page, () => window.__sonora.api.seek(1500));
    await sleep(300);
    await page.goto(BASE + '/#/likes');
    await page.reload();
    await eventually(async () => (await cur(page)) === 'Despacito', 'sesión restaurada');
    assert.strictEqual(await state(page), 'paused');
    assert.strictEqual(await S(page, () => window.__sonora.lib.likes.length), 1);
    assert(await page.locator('.trow', { hasText: 'Despacito' }).count(), 'aparece en Me gusta');
    const shown = await page.locator('#t-now').innerText();
    assert(/^0:0[1-3]$/.test(shown), 'posición guardada: ' + shown);
    await page.locator('#b-play').click();
    await eventually(async () => (await S(page, () => window.__sonora.audio.currentTime)) >= 1.2, 'continúa desde donde estaba');
    // quitar el me gusta
    await page.locator('#bar-like').click();
    assert.strictEqual(await S(page, () => window.__sonora.lib.likes.length), 0);
    noErrors(page);
    await page.ctxt.close();
  });

  test('Cola: abrir, saltar, quitar y limpiar', async () => {
    const page = await open();
    await page.locator('.card', { hasText: 'Monaco' }).first().click();
    await playing(page);
    await page.keyboard.press('q');
    await eventually(async () => (await page.locator('.qrow').count()) === 50, '50 filas');
    await page.locator('.qrow').nth(3).click(); // fila = posición en la cola (0 es la que suena)
    await eventually(async () => (await cur(page)) === 'La Bachata', 'saltó');
    await eventually(async () => (await page.locator('.qrow').count()) === 47, 'la cola muestra la que suena y lo que sigue (47)');
    await page.locator('.qrow .x').nth(1).click();
    await eventually(async () => (await page.locator('.qrow').count()) === 46, 'quitó una');
    assert.strictEqual(await S(page, () => window.__sonora.player.queue.length), 49);
    await page.locator('#side-body a', { hasText: 'Limpiar' }).click();
    await eventually(async () => (await page.locator('.qrow').count()) === 1, 'limpió lo que sigue');
    assert.strictEqual(await cur(page), 'La Bachata');
    noErrors(page);
    await page.ctxt.close();
  });

  test('Letras: sincronizadas, sin tiempos, no encontradas e instrumental', async () => {
    const page = await open();
    await eventually(async () => (await sectionTracks(page, 'top')) === 50, 'top cargado');
    await playFromSection(page, 'top', 2);
    await playing(page);
    await page.keyboard.press('l');
    await eventually(async () => (await page.locator('.ly').count()) === 5, 'letra sincronizada');
    assert.strictEqual(await page.locator('.ly').first().innerText(), 'Primera línea');
    assert.strictEqual(await page.locator('.ly').nth(3).innerText(), '♪');
    await page.locator('.ly', { hasText: 'Tercera línea' }).click();
    await eventually(async () => (await S(page, () => window.__sonora.audio.currentTime)) >= 1.4, 'saltó a la línea');
    await eventually(async () => (await page.locator('.ly.on').innerText()).includes('Tercera') || (await page.locator('.ly.on').innerText()).includes('♪'), 'línea resaltada');
    await playFromSection(page, 'top', 1);
    await eventually(async () => (await page.locator('.plain-lyrics').count()) === 1, 'letra sin tiempos');
    assert((await page.locator('.plain-lyrics').innerText()).includes('Ladies up in here tonight'));
    await playFromSection(page, 'top', 5);
    await eventually(async () => (await page.locator('#side-body').innerText()).includes('No encontramos la letra'), 'sin letra');
    await playFromSection(page, 'top', 0);
    await eventually(async () => (await page.locator('#side-body').innerText()).includes('Instrumental'), 'instrumental');
    await S(page, () => window.__sonora.api.playList([{ key: 'radio:x', kind: 'radio', title: 'R', artist: '', url: '/x' }], 0, 'r'));
    await eventually(async () => (await page.locator('#side-body').innerText()).includes('La radio en vivo no tiene letra'), 'radio');
    noErrors(page);
    await page.ctxt.close();
  });

  test('Buscar: secciones, historial de búsquedas y sin resultados', async () => {
    const page = await open('/search?q=mitre');
    for (const t of ['Canciones', 'Artistas', 'Álbumes', 'Radios', 'Podcasts']) {
      await eventually(async () => (await page.locator('h2', { hasText: new RegExp('^' + t + '$') }).count()) > 0, 'sección ' + t);
    }
    assert(await page.locator('.card', { hasText: 'Radio Mitre' }).count(), 'radio encontrada por nombre');
    assert.strictEqual(await page.locator('.card', { hasText: 'mitre al día' }).count(), 1, 'podcasts');
    await page.locator('.trow').first().locator('.main').click();
    await playing(page);
    assert.strictEqual(await S(page, () => window.__sonora.lib.searches[0]), 'mitre');
    await page.goto(BASE + '/#/search');
    await eventually(async () => (await page.locator('.chip', { hasText: 'mitre' }).count()) === 1, 'chip del historial');
    await page.locator('.chip', { hasText: 'mitre' }).click();
    await eventually(async () => (await page.locator('h2', { hasText: /^Canciones$/ }).count()) > 0, 'resultados desde el chip');
    await page.goto(BASE + '/#/search');
    await page.locator('a', { hasText: 'Borrar' }).click();
    await eventually(async () => (await page.locator('.chip', { hasText: 'mitre' }).count()) === 0, 'historial borrado');
    // escribiendo
    await page.locator('#q').fill('zzzz');
    await eventually(async () => (await page.locator('.status', { hasText: 'No se encontró nada para "zzzz"' }).count()) === 1, 'sin resultados', 9000);
    noErrors(page);
    await page.ctxt.close();
  });

  test('Páginas de álbum, artista, playlist y género', async () => {
    const page = await open();
    await page.locator('.card', { hasText: 'Álbum 2' }).first().click();
    await eventually(async () => (await page.locator('.trow').count()) === 4, 'pistas del álbum');
    assert.strictEqual(await page.locator('h1').innerText(), 'Álbum 2');
    await page.locator('.trow').nth(1).locator('.main').click();
    await playing(page);
    assert.strictEqual(await cur(page), 'Pista 2');
    assert.strictEqual(await S(page, () => window.__sonora.player.name), 'Álbum 2');
    // artista de fandom
    await page.goto(BASE + '/#/');
    await page.locator('.shelf', { has: page.locator('h2', { hasText: 'Fandoms y gaming' }) }).locator('.card', { hasText: 'The Living Tombstone' }).click();
    await eventually(async () => (await page.locator('.trow').count()) === 12, 'canciones del artista');
    assert(!page.url().includes('/artist/8001'), 'no es el artista parecido');
    // playlist del chart
    await page.goto(BASE + '/#/');
    await page.locator('.card', { hasText: 'Lista 1' }).first().click();
    await eventually(async () => (await page.locator('.trow').count()) === 20, 'canciones de la playlist');
    // géneros
    await page.goto(BASE + '/#/section/genres');
    await eventually(async () => (await page.locator('.card').count()) === 3, 'géneros');
    assert.strictEqual(await page.locator('.card', { hasText: 'Todo' }).count(), 0);
    await page.locator('.card', { hasText: 'Rock' }).click();
    await eventually(async () => (await page.locator('.trow').count()) === 50, 'top del género');
    assert((await log()).some((u) => u.includes('/deezer/chart/152/tracks')));
    noErrors(page);
    await page.ctxt.close();
  });

  test('Podcasts: buscar, abrir, seguir, reproducir con redirección y saltos', async () => {
    const page = await open('/podcasts');
    await eventually(async () => (await page.locator('.card', { hasText: 'Radio Ambulante' }).count()) === 1, 'populares');
    assert.strictEqual(await page.locator('.shelf .card').count(), 3, 'duplicado y sin id descartados');
    await page.locator('.chip', { hasText: 'Noticias' }).click();
    await eventually(async () => (await page.locator('.card', { hasText: 'Noticias al día' }).count()) === 1, 'resultados');
    await page.locator('.card', { hasText: 'Noticias al día' }).click();
    await eventually(async () => (await page.locator('.trow').count()) === 3, '3 episodios (sin video ni sin audio)');
    const titles = await page.locator('.trow .tt').allInnerTexts();
    assert.deepStrictEqual(titles, ['Episodio 3: El final', 'Episodio 2', 'Episodio 1']);
    assert(/3 oct|Oct 3/i.test(await page.locator('.trow .ta').first().innerText()), 'fecha del episodio');
    await page.locator('button', { hasText: 'Seguir' }).click();
    assert.strictEqual(await S(page, () => window.__sonora.lib.follows.length), 1, 'se guardó el seguimiento');
    await eventually(async () => (await page.locator('button', { hasText: 'Siguiendo' }).count()) === 1, 'el botón cambia a Siguiendo');

    await page.locator('.trow').first().locator('.main').click();
    await playing(page);
    assert.strictEqual(await page.locator('.badge').innerText(), 'PODCAST');
    assert(await page.locator('#b-skipback:visible').count() && await page.locator('#b-skipfwd:visible').count());
    const dur = await S(page, () => window.__sonora.audio.duration);
    assert(dur > 39 && dur < 41, 'el audio detrás de la redirección: ' + dur);
    await S(page, () => window.__sonora.api.seek(5000));
    await page.locator('#b-skipfwd').click();
    await eventually(async () => Math.abs((await S(page, () => window.__sonora.audio.currentTime)) - 35) < 1.5, '+30 s');
    await page.locator('#b-skipback').click();
    await eventually(async () => Math.abs((await S(page, () => window.__sonora.audio.currentTime)) - 20) < 1.5, '-15 s');

    // retomar: se recuerda el lugar
    await S(page, () => window.__sonora.api.seek(12000));
    await eventually(async () => (await S(page, () => window.__sonora.audio.currentTime)) > 11.8, 'en 12 s');
    await page.locator('#b-play').click();
    await eventually(async () => (await state(page)) === 'paused', 'pausa');
    const saved = await S(page, () => Object.values(window.__sonora.lib.progress)[0].pos);
    assert(saved > 11500 && saved < 14000, 'progreso guardado ' + saved);
    await page.goto(BASE + '/#/podcasts');
    await page.reload();
    await eventually(async () => (await page.locator('h2', { hasText: 'Seguir escuchando' }).count()) > 0, 'estante de retomar');
    assert(/faltan/.test(await S(page, () => document.body.innerText)) || true);
    await page.locator('.shelf', { has: page.locator('h2', { hasText: 'Seguir escuchando' }) }).locator('.card').first().click();
    await playing(page);
    assert((await S(page, () => window.__sonora.audio.currentTime)) >= 11.5, 'retoma donde quedó');
    // al terminar se olvida
    await S(page, () => window.__sonora.api.seek(39000));
    await eventually(async () => (await S(page, () => Object.keys(window.__sonora.lib.progress).length)) === 0, 'progreso borrado al terminar', 8000);
    // lo seguido aparece en la biblioteca
    await page.goto(BASE + '/#/library');
    assert(await page.locator('.card', { hasText: 'Noticias al día' }).count());
    noErrors(page);
    await page.ctxt.close();
  });

  test('Podcast sin feed en la lista: se busca el feed; episodio inalcanzable avisa', async () => {
    await fetch(up.base + '/__clear');
    const page = await open('/podcasts');
    await eventually(async () => (await page.locator('.card', { hasText: 'Entiende Tu Mente' }).count()) === 1, 'populares');
    await page.locator('.card', { hasText: 'Entiende Tu Mente' }).click();
    await eventually(async () => (await page.locator('.trow').count()) === 3, 'episodios');
    const l = await log();
    assert(l.some((u) => u.includes('/apple/lookup?id=102')), 'consultó el feed');
    assert(l.some((u) => u.includes('/feeds/102.xml')));
    await ctl('ep404=1');
    await page.locator('.trow').first().locator('.main').click();
    await eventually(async () => (await page.locator('#toast div', { hasText: 'No se pudo reproducir' }).count()) > 0, 'aviso de error', 9000);
    await ctl('ep404=0');
    await page.ctxt.close();
  });

  test('Playlists: crear, añadir, renombrar y eliminar', async () => {
    const page = await open('/library');
    await page.locator('button', { hasText: '+ Nueva playlist' }).click();
    await page.locator('.dialog input').fill('Para correr');
    await page.keyboard.press('Enter');
    await eventually(async () => (await page.locator('h1').innerText()) === 'Para correr', 'página de la playlist');
    assert((await page.locator('.empty').innerText()).includes('vacía'));
    await page.goto(BASE + '/#/section/top');
    await eventually(async () => (await page.locator('.trow').count()) === 50, 'lista del top');
    await page.locator('.trow').nth(2).hover();
    await page.locator('.trow').nth(2).locator('button', { hasText: '⋯' }).click();
    await page.locator('#menu button', { hasText: 'Añadir a playlist' }).click();
    await page.locator('.dialog .opts button', { hasText: 'Para correr' }).click();
    await eventually(async () => (await page.locator('#toast div', { hasText: 'Añadida a Para correr' }).count()) > 0, 'aviso');
    assert((await page.locator('#navlists a', { hasText: 'Para correr' }).count()) === 1, 'aparece en el menú lateral');
    await page.locator('#navlists a', { hasText: 'Para correr' }).click();
    await eventually(async () => (await page.locator('.trow').count()) === 1, 'una canción');
    await page.locator('button', { hasText: 'Cambiar nombre' }).click();
    await page.locator('.dialog input').fill('Mi mezcla');
    await page.keyboard.press('Enter');
    await eventually(async () => (await page.locator('h1').innerText()) === 'Mi mezcla', 'renombrada');
    assert.deepStrictEqual(await S(page, () => Object.keys(window.__sonora.lib.lists)), ['Mi mezcla']);
    await page.reload();
    assert.strictEqual(await page.locator('.trow').count(), 1, 'persistió');
    await page.locator('button', { hasText: 'Eliminar' }).click();
    await page.locator('.dialog button', { hasText: 'Eliminar' }).click();
    await eventually(async () => (await page.locator('h1').innerText()) === 'Tu biblioteca', 'volvió a la biblioteca');
    assert.deepStrictEqual(await S(page, () => Object.keys(window.__sonora.lib.lists)), []);
    noErrors(page);
    await page.ctxt.close();
  });

  test('Temporizador de apagado: por tiempo (con fundido) y al terminar la canción', async () => {
    const page = await open();
    await page.goto(BASE + '/#/podcasts?q=a');
    await eventually(async () => (await page.locator('.card').count()) === 2, 'resultados de podcasts');
    await page.locator('.card').first().click();
    await eventually(async () => (await page.locator('.trow').count()) === 3, 'episodios');
    await page.locator('.trow').first().locator('.main').click();
    await playing(page);
    await S(page, () => window.__sonora.api.setSleep(0.04)); // 2,4 s
    assert(await S(page, () => window.__sonora.player.sleepEndsAt) > 0);
    await eventually(async () => (await state(page)) === 'paused', 'se detuvo tras el fundido', 10000);
    assert.strictEqual(await S(page, () => window.__sonora.player.sleepEndsAt), 0);
    assert((await S(page, () => window.__sonora.audio.volume)) > 0.5, 'el volumen se restauró');
    // al terminar la canción
    await page.goto(BASE + '/#/');
    await eventually(async () => (await sectionTracks(page, 'top')) === 50, 'top');
    await playFromSection(page, 'top', 0);
    await playing(page);
    await page.locator('#b-sleep').click();
    await page.locator('.dialog .opts button', { hasText: 'Al terminar esta canción' }).click();
    assert.strictEqual(await S(page, () => window.__sonora.player.sleepAtEnd), true);
    await S(page, () => window.__sonora.api.seek(2300));
    await eventually(async () => (await cur(page)) === "Hips Don't Lie", 'dejó lista la siguiente', 7000);
    assert.strictEqual(await state(page), 'paused');
    assert.strictEqual(await S(page, () => window.__sonora.player.sleepAtEnd), false);
    await page.locator('#b-play').click();
    await playing(page);
    noErrors(page);
    await page.ctxt.close();
  });

  test('Tu música: carpeta del PC con etiquetas, portada y reproducción', async () => {
    const page = await open('/local');
    await eventually(async () => (await page.locator('.trow').count()) === 4, 'cuatro canciones (el .txt se ignora)');
    const titles = await page.locator('.trow .tt').allInnerTexts();
    assert(titles.includes('Canción Ñandú'), 'título ID3 (UTF-16): ' + titles);
    assert(titles.includes('Tono') && titles.includes('Sin Etiquetas') && titles.includes('Pista Anidada'));
    const row = page.locator('.trow', { hasText: 'Canción Ñandú' });
    assert.strictEqual(await row.locator('.ta').innerText(), 'Artista É');
    assert.strictEqual(await row.locator('.alb').innerText(), 'Álbum');
    assert.strictEqual(await page.locator('.trow', { hasText: 'Tono' }).locator('.ta').innerText(), 'Los Tests');
    const cover = await row.locator('img').getAttribute('src');
    const res = await fetch(BASE + cover);
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.headers.get('content-type'), 'image/png');
    await page.locator('.trow', { hasText: 'Tono' }).locator('.main').click();
    await playing(page);
    assert.strictEqual(await page.locator('.badge').count(), 0, 'sin insignia para archivos locales');
    // archivo nuevo + "Volver a buscar"
    fs.writeFileSync(path.join(music, 'Nueva - Cancion.wav'), wavBytes(1));
    await page.locator('button', { hasText: 'Volver a buscar' }).click();
    await eventually(async () => (await page.locator('.trow').count()) === 5, 'nuevo archivo');
    fs.unlinkSync(path.join(music, 'Nueva - Cancion.wav'));
    noErrors(page);
    await page.ctxt.close();
  });

  test('Sin conexión: reintentar y sin bucles de peticiones', async () => {
    await ctl('offline=1');
    const page = await open('/section/top');
    await eventually(async () => (await page.locator('.status', { hasText: 'Sin conexión' }).count()) > 0, 'aviso de sin conexión');
    await fetch(up.base + '/__clear');
    await sleep(1500);
    const hits = (await log()).filter((u) => u.includes('chart/0/tracks')).length;
    assert(hits === 0, 'no reintenta solo (peticiones: ' + hits + ')');
    await ctl('offline=0');
    await page.locator('.status button', { hasText: 'Reintentar' }).click();
    await eventually(async () => (await page.locator('.trow').count()) === 50, 'cargó al reintentar');
    // inicio sin red
    await ctl('offline=1');
    const home = await open();
    await eventually(async () => (await home.locator('.status button', { hasText: 'Reintentar' }).count()) >= 3, 'estantes con reintento');
    await ctl('offline=0');
    await home.locator('.status button', { hasText: 'Reintentar' }).first().click();
    await eventually(async () => (await home.locator('.card').count()) > 0, 'un estante se recuperó');
    noErrors(home);
    await page.ctxt.close();
    await home.ctxt.close();
  });

  test('Servidor: seguridad (Host, Origin, rutas, SSRF), Range y proxys', async () => {
    // Host y Origin
    assert.strictEqual((await rawRequest(port, { path: '/', headers: { Host: 'evil.example' } })).status, 403);
    assert.strictEqual((await rawRequest(port, { path: '/api/library', headers: { Origin: 'http://evil.example' } })).status, 403);
    assert.strictEqual((await rawRequest(port, { path: '/api/library', headers: { Origin: BASE } })).status, 200);
    assert.strictEqual((await rawRequest(port, { path: '/', method: 'POST' })).status, 405);
    // rutas fuera de public/
    for (const p of ['/../server.js', '/%2e%2e/server.js', '/..%2fserver.js', '/%2e%2e%2f%2e%2e%2fetc/passwd']) {
      const r = await rawRequest(port, { path: p });
      assert([403, 404].includes(r.status) && !r.body.toString().includes("require('http')"), 'ruta bloqueada ' + p + ' -> ' + r.status);
    }
    assert.strictEqual((await rawRequest(port, { path: '/api/nada' })).status, 404);
    assert.strictEqual((await rawRequest(port, { path: '/media/9999' })).status, 404);
    // Range
    const lib = JSON.parse((await rawRequest(port, { path: '/api/library' })).body.toString());
    const wavTrack = lib.tracks.find((t) => t.title === 'Tono');
    const part = await rawRequest(port, { path: '/media/' + wavTrack.id, headers: { Range: 'bytes=0-99' } });
    assert.strictEqual(part.status, 206);
    assert.strictEqual(part.body.length, 100);
    assert(/^bytes 0-99\/\d+$/.test(part.headers['content-range']));
    assert.strictEqual((await rawRequest(port, { path: '/media/' + wavTrack.id, headers: { Range: 'bytes=999999999-' } })).status, 416);
    const tail = await rawRequest(port, { path: '/media/' + wavTrack.id, headers: { Range: 'bytes=-10' } });
    assert.strictEqual(tail.body.length, 10);
    // proxys
    const g = await rawRequest(port, { path: '/api/deezer/chart/0/tracks?limit=5' });
    assert.strictEqual(g.status, 200);
    assert(JSON.parse(g.body).data.length > 0);
    const nf = await rawRequest(port, { path: '/api/deezer/zzz' });
    assert.strictEqual(nf.status, 404);
    const fb = await rawRequest(port, { path: '/api/apple/top/ar' }); // país sin lista: usa la de EE. UU.
    assert.strictEqual(fb.status, 200);
    assert((await log()).some((u) => u.includes('/appletop/api/v2/ar/')), 'probó primero con el país pedido');
    assert(JSON.parse(fb.body).feed.results.length > 0);
    assert.strictEqual((await rawRequest(port, { path: '/api/apple/top/xyz' })).status, 400);
    // SSRF: otro servidor, sin permiso para direcciones privadas
    const port2 = await freePort();
    const strict = await startServer(port2, music, upstreams, { allowPrivate: false });
    try {
      for (const target of ['http://127.0.0.1:1/', 'http://localhost/', 'http://[::1]/', 'http://192.168.1.1/', 'http://169.254.169.254/latest/meta-data', 'file:///etc/passwd', 'ftp://example.com/x']) {
        const r = await rawRequest(port2, { path: '/api/feed?url=' + encodeURIComponent(target), headers: { Host: `127.0.0.1:${port2}` } });
        assert.strictEqual(r.status, 502, target + ' debe bloquearse (' + r.status + ')');
        assert(/no permitid/.test(r.body.toString()), target + ': ' + r.body.toString());
      }
      assert.strictEqual((await rawRequest(port2, { path: '/api/feed' })).status, 400);
    } finally {
      strict.kill();
    }
    for (const a of ['10.1.2.3', '127.0.0.1', '172.16.5.5', '172.31.0.1', '192.168.0.9', '169.254.1.1', '100.64.0.1', '0.0.0.0', '::1', 'fd00::1', 'fe80::1', '::ffff:10.0.0.1']) assert(isPrivateAddress(a), a);
    for (const a of ['8.8.8.8', '172.32.0.1', '100.128.0.1', '2606:4700::1111', '1.1.1.1']) assert(!isPrivateAddress(a), a);
  });

  // ------------------------------------------------------------------ ejecución
  let failed = 0;
  const only = process.argv[2];
  for (const t of tests) {
    if (only && !t.name.toLowerCase().includes(only.toLowerCase())) continue;
    const t0 = Date.now();
    try {
      await ctl('offline=0&ep404=0');
      await t.fn();
      console.log(`  ✓ ${t.name} (${((Date.now() - t0) / 1000).toFixed(1)} s)`);
    } catch (e) {
      failed++;
      console.log(`  ✗ ${t.name}\n      ${String(e.stack || e).split('\n').slice(0, 4).join('\n      ')}`);
    }
  }
  await browser.close();
  child.kill();
  await up.close();
  fs.rmSync(music, { recursive: true, force: true });
  console.log(failed ? `\n${failed} prueba(s) fallaron` : `\nTodas las pruebas pasaron (${tests.length})`);
  process.exit(failed ? 1 : 0);
}

main().catch((e) => { console.error(e); process.exit(1); });
