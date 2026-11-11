'use strict';
/** Servidor que imita Deezer, Apple Podcasts, radio-browser y LRCLIB, con audio WAV real. */
const http = require('http');

const SONGS = [
  ['Monaco', 'Bad Bunny', 'Nadie Sabe Lo Que Va a Pasar Mañana'], ["Hips Don't Lie", 'Shakira', 'Oral Fixation Vol. 2'],
  ['Despacito', 'Luis Fonsi', 'Vida'], ['La Bachata', 'Manuel Turizo', '2000'], ['Bohemian Rhapsody', 'Queen', 'A Night at the Opera'],
  ['Flowers', 'Miley Cyrus', 'Endless Summer Vacation'], ['De Música Ligera', 'Soda Stereo', 'Canción Animal'], ['Tusa', 'KAROL G', 'KG0516'],
  ['Blinding Lights', 'The Weeknd', 'After Hours'], ['Ella Baila Sola', 'Eslabon Armado', 'Desvelado'],
];

function wav(seconds, freq) {
  const rate = 8000;
  const n = rate * seconds;
  const b = Buffer.alloc(44 + n);
  b.write('RIFF', 0); b.writeUInt32LE(36 + n, 4); b.write('WAVEfmt ', 8); b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20);
  b.writeUInt16LE(1, 22); b.writeUInt32LE(rate, 24); b.writeUInt32LE(rate, 28); b.writeUInt16LE(1, 32); b.writeUInt16LE(8, 34);
  b.write('data', 36); b.writeUInt32LE(n, 40);
  for (let i = 0; i < n; i++) b[44 + i] = 128 + Math.round(30 * Math.sin((2 * Math.PI * freq * i) / rate));
  return b;
}

function start(port = 0) {
  const state = { offline: false, log: [], base: '', ep404: false };
  const SHORT = wav(3, 440);
  const LONG = wav(40, 330);

  const esc = (s) => String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  const json = (res, obj, code = 200) => { res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(typeof obj === 'string' ? obj : JSON.stringify(obj)); };

  const dzTrack = (i, offset) => {
    const s = SONGS[(i + offset) % SONGS.length];
    const id = 1000 + i + offset * 100;
    return {
      id, readable: true, title: s[0] + (i >= SONGS.length ? ` (Live ${i})` : ''), title_short: s[0] + (i >= SONGS.length ? ` (Live ${i})` : ''), duration: 30,
      preview: `${state.base}/audio/short.wav?id=${id}&hdnea=old`, type: 'track',
      artist: { id: 50 + ((i + offset) % SONGS.length), name: s[1] },
      album: { id: 7000 + ((i + offset) % SONGS.length), title: s[2], cover_big: `${state.base}/img/${(i + offset) % 10}.png`, cover_medium: `${state.base}/img/${(i + offset) % 10}.png`, type: 'album' },
    };
  };
  const dzList = (n, offset) => ({ data: [...Array(n).keys()].map((i) => dzTrack(i, offset)).concat([{ id: 9, title: 'Sin vista previa', preview: '', type: 'track', artist: { name: 'X' }, album: { title: 'Y' } }]), total: n });
  const items = (kind, n) => ({ data: [...Array(n).keys()].map((i) => {
    if (kind === 'album') return { id: 300 + i, title: `Álbum ${i + 1}`, cover_big: `${state.base}/img/${i}.png`, artist: { id: 50 + i, name: SONGS[i % 10][1] }, type: 'album' };
    if (kind === 'playlist') return { id: 400 + i, title: `Lista ${i + 1}`, nb_tracks: 20 + i, picture_big: `${state.base}/img/${i}.png`, type: 'playlist' };
    return { id: 50 + i, name: SONGS[i % 10][1], picture_big: `${state.base}/img/${i}.png`, type: 'artist' };
  }) });

  const rss = (title) => `<?xml version="1.0" encoding="UTF-8"?><rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"><channel><title>${title}</title><itunes:image href="${state.base}/img/3.png"/>`
    + `<item><title>Episodio 3: El final</title><guid>g-3</guid><pubDate>Fri, 03 Oct 2025 08:00:00 +0000</pubDate><itunes:duration>0:40</itunes:duration><enclosure url="${state.base}/ep/ep3.mp3" type="audio/mpeg"/></item>`
    + `<item><title>Episodio 2 (video)</title><guid>g-2v</guid><enclosure url="${state.base}/ep/ep2.mp4" type="video/mp4"/></item>`
    + `<item><title>Episodio 2</title><pubDate>Fri, 26 Sep 2025 08:00:00 GMT</pubDate><itunes:duration>3723</itunes:duration><enclosure url="${state.base}/ep/ep2.mp3" type="audio/mpeg"/></item>`
    + `<item><title>Solo texto</title><guid>g-text</guid></item>`
    + `<item><title>Episodio 1</title><guid>g-1</guid><itunes:duration>1:02:03</itunes:duration><enclosure url="${state.base}/ep/ep1.mp3" type="audio/mpeg"/></item></channel></rss>`;

  const radios = () => [
    ['Radio Mitre', 'news,talk'], ['Los 40 Argentina', 'pop,hits'], ['Aspen 102.3', 'classic hits,80s'], ['Rock & Pop 95.9', 'rock'], ['La 100', 'pop,latin'], ['Radio Mitre', 'duplicate'],
  ].map(([name, tags], i) => ({ stationuuid: 'uuid-' + i, name, url: `${state.base}/audio/radio.wav`, url_resolved: `${state.base}/audio/radio.wav`, favicon: `${state.base}/img/${i}.png`, tags, country: 'Argentina', state: 'Buenos Aires', clickcount: 500 - i }))
    .concat([{ stationuuid: 'broken', name: 'Roto', url: '', tags: 'x' }]);

  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64');

  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://x');
    const p = url.pathname;
    state.log.push(req.url);
    if (p === '/__ctl') {
      for (const [k, v] of url.searchParams) state[k] = v === '1' || v === 'true' ? true : v === '0' || v === 'false' ? false : v;
      return json(res, { ok: true });
    }
    if (p === '/__log') return json(res, state.log);
    if (p === '/__clear') { state.log.length = 0; return json(res, { ok: true }); }
    if (p.startsWith('/img/')) {
      const n = Math.abs([...p].reduce((a, c) => a * 31 + c.charCodeAt(0), 7));
      const hue = n % 360;
      res.writeHead(200, { 'Content-Type': 'image/svg+xml' });
      return res.end(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="hsl(${hue},70%,48%)"/><stop offset="1" stop-color="hsl(${(hue + 60) % 360},70%,26%)"/></linearGradient></defs><rect width="100" height="100" fill="url(#g)"/><circle cx="70" cy="32" r="20" fill="#fff" fill-opacity=".18"/></svg>`);
    }
    if (p.startsWith('/audio/')) {
      const buf = p.includes('long') ? LONG : SHORT;
      const range = req.headers.range && /^bytes=(\d*)-(\d*)$/.exec(req.headers.range);
      if (range) {
        const start = Number(range[1] || 0);
        const end = range[2] ? Math.min(Number(range[2]), buf.length - 1) : buf.length - 1;
        res.writeHead(206, { 'Content-Type': 'audio/wav', 'Accept-Ranges': 'bytes', 'Content-Range': `bytes ${start}-${end}/${buf.length}`, 'Content-Length': end - start + 1 });
        return res.end(buf.subarray(start, end + 1));
      }
      res.writeHead(200, { 'Content-Type': 'audio/wav', 'Accept-Ranges': 'bytes', 'Content-Length': buf.length });
      return res.end(buf);
    }
    if (p.startsWith('/ep/')) {
      if (state.ep404) { res.writeHead(404); return res.end(); }
      res.writeHead(302, { Location: `${state.base}/audio/long.wav` });
      return res.end();
    }
    if (state.offline) { res.writeHead(503); return res.end('offline'); }

    if (p.startsWith('/feeds/')) {
      if (p.includes('/999.')) { res.writeHead(500); return res.end('boom'); }
      res.writeHead(200, { 'Content-Type': 'application/rss+xml' });
      return res.end(rss('Podcast ' + p.split('/').pop()));
    }
    // ---- Deezer
    if (p.startsWith('/deezer/')) {
      const d = p.slice('/deezer/'.length);
      if (d === 'genre') return json(res, { data: [{ id: 0, name: 'Todo' }, { id: 132, name: 'Pop' }, { id: 116, name: 'Rap/Hip Hop' }, { id: 152, name: 'Rock' }] });
      if (d === 'search/artist') {
        const name = url.searchParams.get('q');
        if (name.includes('zzzz')) return json(res, { data: [] });
        const known = ['The Living Tombstone', 'CG5', 'Black Gryph0n'];
        if (known.some((k) => k.toLowerCase() === name.toLowerCase())) {
          return json(res, { data: [{ id: 8001, name: name + ' Tribute Band' }, { id: 7000 + Math.abs([...name].reduce((a, c) => a * 31 + c.charCodeAt(0), 0)) % 900, name: known.find((k) => k.toLowerCase() === name.toLowerCase()), picture_big: `${state.base}/img/7.png` }] });
        }
        if (/shakira|soda|luis|bad/i.test(name)) return json(res, { data: [{ id: 51, name: 'Shakira', picture_big: `${state.base}/img/1.png` }] });
        return json(res, { data: [{ id: 8002, name: 'Otro Artista' }] });
      }
      if (d === 'search/album') return json(res, (url.searchParams.get('q') || '').includes('zzzz') ? { data: [] } : items('album', 3));
      if (d === 'search') {
        const qv = url.searchParams.get('q') || '';
        if (qv.includes('zzzz')) return json(res, { data: [], total: 0 });
        return json(res, dzList(15, Math.abs([...qv].reduce((a, c) => a + c.charCodeAt(0), 0)) % SONGS.length));
      }
      if (d.startsWith('track/')) { const id = d.split('/')[1]; return json(res, { id: Number(id), preview: `${state.base}/audio/short.wav?id=${id}&hdnea=fresh` }); }
      if (d.startsWith('chart/0/artists')) return json(res, items('artist', 6));
      if (d.startsWith('chart/0/playlists')) return json(res, items('playlist', 5));
      if (d.startsWith('chart/')) return json(res, dzList(50, 0));
      if (d.startsWith('editorial/0/releases')) return json(res, items('album', 6));
      if (d.startsWith('album/')) return json(res, { data: [0, 1, 2, 3].map((i) => ({ id: 9000 + i, title: `Pista ${i + 1}`, title_short: `Pista ${i + 1}`, duration: 200 + i, preview: `${state.base}/audio/short.wav?id=${9000 + i}`, artist: { id: 50, name: 'Bad Bunny' }, type: 'track' })) });
      if (d.startsWith('playlist/')) return json(res, dzList(20, 4));
      if (d.startsWith('artist/')) return json(res, dzList(12, 3));
      return json(res, { error: { message: 'not found' } }, 404);
    }
    // ---- Apple
    if (p.startsWith('/appletop/')) {
      if (p.includes('/ar/')) { res.writeHead(404); return res.end('nope'); }
      const rows = [['101', 'Radio Ambulante', 'NPR'], ['102', 'Entiende Tu Mente', 'Molo Cebrián'], ['103', 'Tanto Monta', 'Ivoox'], ['101', 'Duplicado', 'X'], ['0', 'Sin id', 'X']];
      return json(res, { feed: { results: rows.map(([id, name, artistName]) => ({ id, name, artistName, artworkUrl100: `${state.base}/img/${id}/100x100bb.png` })) } });
    }
    if (p === '/apple/search') {
      const term = url.searchParams.get('term') || '';
      if (term.includes('zzzz')) return json(res, { resultCount: 0, results: [] });
      return json(res, { resultCount: 2, results: [
        { collectionId: 201, collectionName: term + ' al día', artistName: 'Voces', artworkUrl600: `${state.base}/img/201.png`, feedUrl: `${state.base}/feeds/201.xml` },
        { collectionId: 202, collectionName: 'Otro programa', artistName: 'Radio X', artworkUrl600: `${state.base}/img/202.png`, feedUrl: `${state.base}/feeds/202.xml` }] });
    }
    if (p === '/apple/lookup') {
      const id = url.searchParams.get('id');
      return json(res, { resultCount: 1, results: [{ collectionId: Number(id), feedUrl: `${state.base}/feeds/${id}.xml` }] });
    }
    // ---- radio-browser
    if (p.startsWith('/radio/')) {
      const tag = url.searchParams.get('tag');
      const name = url.searchParams.get('name');
      let list = radios();
      if (name) list = list.filter((r) => r.name.toLowerCase().includes(name.toLowerCase()));
      if (tag === 'news') list = list.slice(0, 3);
      return json(res, list);
    }
    // ---- LRCLIB
    if (p.startsWith('/lrclib/api/')) {
      const track = url.searchParams.get('track_name') || '';
      const search = p.endsWith('/search');
      if (track === 'Monaco') return json(res, { instrumental: true, plainLyrics: null, syncedLyrics: null });
      if (track === 'Flowers') return search ? json(res, []) : json(res, { message: 'nope' }, 404);
      if (track === "Hips Don't Lie") return search ? json(res, [{ id: 1, instrumental: false, plainLyrics: "Ladies up in here tonight\nNo, no, no", syncedLyrics: null }]) : json(res, { message: 'nope' }, 404);
      if (search) return json(res, []);
      return json(res, { id: 7, instrumental: false, plainLyrics: 'uno\ndos', syncedLyrics: '[ar:X]\n[00:00.50] Primera línea\n[00:01.00] Segunda línea\n[00:01.50] Tercera línea\n[00:02.00] \n[00:02.50] Última línea' });
    }
    res.writeHead(404); res.end('?');
  });

  return new Promise((resolve) => {
    server.listen(port, '127.0.0.1', () => {
      state.base = `http://127.0.0.1:${server.address().port}`;
      resolve({ server, state, base: state.base, close: () => new Promise((r) => server.close(r)) });
    });
  });
}

module.exports = { start, SONGS };
