'use strict';
/* Sonora para PC: interfaz y reproductor. Sin dependencias ni paso de compilación. */
(() => {
  // ================================================================ utilidades
  const $ = (s, el = document) => el.querySelector(s);
  const enc = encodeURIComponent;

  function h(tag, props, ...kids) {
    const e = document.createElement(tag);
    for (const [k, v] of Object.entries(props || {})) {
      if (v == null || v === false) continue;
      if (k === 'class') e.className = v;
      else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
      else if (k === 'text') e.textContent = v;
      else e.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids.flat()) {
      if (kid == null || kid === false) continue;
      e.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
    }
    return e;
  }

  const fold = (s) => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const hashCode = (s) => { let x = 0; for (let i = 0; i < s.length; i++) x = (x * 31 + s.charCodeAt(i)) | 0; return Math.abs(x); };

  function fmtTime(ms) {
    if (!(ms > 0)) return '0:00';
    const s = Math.floor(ms / 1000);
    const hh = Math.floor(s / 3600);
    const mm = Math.floor((s % 3600) / 60);
    const ss = String(s % 60).padStart(2, '0');
    return hh ? `${hh}:${String(mm).padStart(2, '0')}:${ss}` : `${mm}:${ss}`;
  }

  function minutesText(ms) {
    const m = Math.max(1, Math.round(ms / 60000));
    return m >= 60 ? `${Math.floor(m / 60)} h ${m % 60} min` : `${m} min`;
  }

  const store = {
    get(k, d) {
      try {
        const v = localStorage.getItem('sonora.' + k);
        return v == null ? d : JSON.parse(v);
      } catch (e) { return d; }
    },
    set(k, v) {
      try { localStorage.setItem('sonora.' + k, JSON.stringify(v)); } catch (e) { /* sin almacenamiento */ }
    },
  };

  async function getJson(url) {
    const r = await fetch(url);
    if (!r.ok) { const e = new Error('HTTP ' + r.status); e.status = r.status; throw e; }
    return r.json();
  }
  const dz = (p) => getJson('/api/deezer/' + p);

  function toast(msg) {
    const box = $('#toast');
    const t = h('div', { text: msg });
    box.append(t);
    setTimeout(() => t.remove(), 2800);
  }

  // ================================================================ modelos

  function trackFromDeezer(o, album) {
    const al = o.album || album || {};
    const ar = o.artist || {};
    return {
      key: 'dz:' + o.id, kind: 'preview', id: o.id,
      title: o.title_short || o.title || 'Sin título', artist: ar.name || 'Artista desconocido',
      artistId: ar.id || 0, album: al.title || '', albumId: al.id || 0,
      art: al.cover_big || al.cover_medium || al.cover || null, dur: (o.duration || 0) * 1000,
      url: o.preview || '', at: Date.now(),
    };
  }

  function trackFromRadio(o) {
    const place = o.state || o.country || '';
    const tags = String(o.tags || '').split(',').filter(Boolean).slice(0, 3).join(' · ');
    return {
      key: 'radio:' + o.stationuuid, kind: 'radio', title: String(o.name || '').trim(),
      artist: tags || place, album: place, art: o.favicon || null, dur: 0, url: o.url_resolved || o.url || '',
    };
  }

  function trackFromLocal(l) {
    return {
      key: l.key, kind: 'local', title: l.title || 'Sin título', artist: l.artist || 'Artista desconocido',
      album: l.album || '', art: l.hasCover ? `/media/${l.id}/cover` : null, dur: 0, url: `/media/${l.id}`, added: l.added || 0,
    };
  }

  function parseDeezerTracks(body) {
    if (body.error) throw new Error(body.error.message || 'error');
    const out = [];
    const seen = new Set();
    for (const o of body.data || []) {
      if (!o || !o.preview || (o.type && o.type !== 'track')) continue;
      if (seen.has(o.id)) continue;
      seen.add(o.id);
      out.push(trackFromDeezer(o));
    }
    return out;
  }

  function parseRadios(list) {
    const out = [];
    const names = new Set();
    for (const o of Array.isArray(list) ? list : []) {
      const url = o.url_resolved || o.url;
      const name = String(o.name || '').trim();
      if (!url || !name || names.has(fold(name))) continue;
      names.add(fold(name));
      out.push(trackFromRadio(o));
    }
    return out;
  }

  function item(kind, id, title, sub, art, feed) {
    return { key: kind + ':' + id, kind, id, title: title || 'Sin título', sub: sub || '', art: art || null, feed: feed || null };
  }

  function parseItems(kind, body) {
    if (body.error) throw new Error('error');
    let data = body.data;
    if (!data) data = (body.feed && body.feed.results) || body.results || [];
    const out = [];
    const seen = new Set();
    for (const o of data) {
      let it;
      if (kind === 'album') it = item(kind, o.id, o.title, o.artist && o.artist.name, o.cover_big || o.cover_medium);
      else if (kind === 'artist') it = item(kind, o.id, o.name, 'Artista', o.picture_big || o.picture_medium);
      else if (kind === 'playlist') it = item(kind, o.id, o.title, o.nb_tracks ? o.nb_tracks + ' canciones' : 'Playlist', o.picture_big || o.picture_medium);
      else if (kind === 'genre') { if (!(o.id > 0)) continue; it = item(kind, o.id, o.name, 'Género', o.picture_big || o.picture_medium); }
      else {
        const id = o.collectionId != null ? o.collectionId : o.id;
        let art = o.artworkUrl600 || '';
        if (!art && o.artworkUrl100) art = o.artworkUrl100.replace('100x100', '600x600');
        it = item('podcast', Number(id), o.collectionName || o.name, o.artistName, art, o.feedUrl);
      }
      if (!(Number(it.id) > 0) || seen.has(it.key)) continue;
      seen.add(it.key);
      out.push(it);
    }
    return out;
  }

  // Los ítems se recuerdan para poder abrir sus páginas después de recargar.
  const items = store.get('items', {});
  function remember(it) {
    items[it.key] = it;
    const keys = Object.keys(items);
    if (keys.length > 400) delete items[keys[0]];
    store.set('items', items);
    return it;
  }

  // ================================================================ catálogo

  const RADIO_QS = 'hidebroken=true&order=clickcount&reverse=true&limit=40';
  const lang = (navigator.language || 'es-AR').split('-');
  const region = (lang[1] || '').toUpperCase();
  const radioSearch = (extra) => `/api/radio/json/stations/search?${extra}&${RADIO_QS}`;
  const q = (text) => `search?q=${enc(text)}&order=RANKING&limit=40`;

  const FANDOM = ['The Living Tombstone', 'Black Gryph0n', 'Baasik', 'CG5', 'NateWantsToBattle', 'DAGames', 'TryHardNinja',
    'Rockit Gaming', 'Miracle Of Sound', 'Random Encounters', 'Dan Bull', 'Griffinilla', 'Jonathan Young', 'Jack Stauber'];

  const SECTIONS = [
    { id: 'top', title: 'Top 50 mundial', sub: 'Lo más escuchado ahora en Deezer', type: 'tracks', path: 'chart/0/tracks?limit=50', featured: true },
    { id: 'radio', title: 'Radios en vivo', sub: 'Emisoras reales, canciones completas', type: 'radio',
      url: radioSearch(region.length === 2 ? 'countrycode=' + region : 'language=spanish'), featured: true },
    { id: 'releases', title: 'Nuevos lanzamientos', sub: 'Álbumes recién salidos', type: 'items', itemKind: 'album', path: 'editorial/0/releases?limit=25', featured: true },
    { id: 'artists', title: 'Artistas populares', sub: 'Los más escuchados', type: 'items', itemKind: 'artist', path: 'chart/0/artists?limit=25', featured: true },
    { id: 'playlists', title: 'Playlists populares', sub: 'Listas que están sonando', type: 'items', itemKind: 'playlist', path: 'chart/0/playlists?limit=25', featured: true },
    { id: 'reggaeton', title: 'Reggaetón', sub: 'Los éxitos del género', type: 'tracks', path: q('reggaeton'), featured: true },
    { id: 'pop', title: 'Pop latino', sub: 'Para cantar a todo pulmón', type: 'tracks', path: q('pop latino'), featured: true },
    { id: 'rock', title: 'Rock en español', sub: 'Clásicos y nuevos', type: 'tracks', path: q('rock en español'), featured: true },
    { id: 'fandom-artists', title: 'Fandoms y gaming', sub: 'The Living Tombstone, CG5, Black Gryph0n y más', type: 'items', itemKind: 'artist', names: FANDOM, featured: true },
    { id: 'tls', title: 'The Living Tombstone', sub: 'Sus temas más escuchados', type: 'tracks', path: q('artist:"The Living Tombstone"'), featured: true },
    { id: 'genres', title: 'Géneros', sub: 'Explora el top de cada estilo', type: 'items', itemKind: 'genre', path: 'genre' },
    { id: 'podcasts', title: 'Podcasts populares', sub: 'Episodios completos', type: 'items', itemKind: 'podcast', special: 'applecharts' },
    ...[['cumbia', 'Cumbia', 'Para mover el esqueleto', 'cumbia'], ['trap', 'Trap', 'Lo que suena en la calle', 'trap'],
      ['hiphop', 'Hip hop', 'Rimas y ritmo', 'hip hop'], ['electronic', 'Electrónica', 'Para bailar sin parar', 'electronic dance'],
      ['jazz', 'Jazz', 'Suave y elegante', 'jazz'], ['classical', 'Clásica', 'Los grandes compositores', 'classical piano'],
      ['indie', 'Indie', 'Sonidos alternativos', 'indie'], ['salsa', 'Salsa', 'Ritmo caribeño', 'salsa'],
      ['bachata', 'Bachata', 'Para bailar de a dos', 'bachata'], ['ballads', 'Baladas', 'Para suspirar', 'baladas románticas'],
      ['workout', 'Para entrenar', 'Energía para el gimnasio', 'workout'], ['chill', 'Para relajarse', 'Calma y buena onda', 'chill'],
      ['party', 'Fiesta', 'Que no pare la música', 'party hits'], ['lofi', 'Lo-fi', 'Para estudiar o trabajar', 'lofi'],
    ].map(([id, title, sub, term]) => ({ id, title, sub, type: 'tracks', path: q(term) })),
    ...[['radio-news', 'Radios de noticias', 'Actualidad en vivo', 'news', true], ['radio-sports', 'Radios de deportes', 'Partidos y análisis', 'sports', true],
      ['radio-rock', 'Radios de rock', 'Rock las 24 horas', 'rock', true], ['radio-pop', 'Radios de pop', 'Éxitos del momento', 'pop', true],
      ['radio-latin', 'Radios latinas', 'Salsa, cumbia y más', 'latin', true], ['radio-classical', 'Radios de clásica', 'Música para concentrarse', 'classical'],
      ['radio-jazz', 'Radios de jazz', 'Jazz en vivo', 'jazz'], ['radio-electronic', 'Radios electrónicas', 'Beats sin pausa', 'electronic'],
    ].map(([id, title, sub, tag, es]) => ({ id, title, sub, type: 'radio', url: radioSearch('tag=' + tag + (es ? '&language=spanish' : '')) })),
  ];
  for (const s of SECTIONS) { s.state = 'idle'; s.tracks = []; s.items = []; }
  const section = (id) => SECTIONS.find((s) => s.id === id);

  const listeners = new Set();
  const changed = () => listeners.forEach((f) => f());

  async function artistByName(name) {
    const body = await dz(`search/artist?limit=5&q=${enc(name)}`);
    const want = fold(name);
    for (const o of body.data || []) {
      if (fold(o.name) === want && o.id > 0) return item('artist', o.id, o.name, 'Artista', o.picture_big || o.picture_medium);
    }
    return null;
  }

  async function loadSection(s) {
    if (s.state === 'loading') return;
    s.state = 'loading';
    changed();
    try {
      if (s.type === 'radio') {
        let list = parseRadios(await getJson(s.url));
        if (list.length < 5 && !s.url.includes('language=')) {
          for (const t of parseRadios(await getJson(radioSearch('language=spanish')))) if (!list.some((x) => x.key === t.key)) list.push(t);
        }
        s.tracks = list;
        if (!list.length) throw new Error('vacío');
      } else if (s.type === 'items') {
        let list;
        if (s.names) {
          const found = await Promise.allSettled(s.names.map(artistByName));
          if (found.every((r) => r.status === 'rejected')) throw new Error('sin conexión');
          list = found.filter((r) => r.status === 'fulfilled' && r.value).map((r) => r.value);
          list = list.filter((it, i) => list.findIndex((x) => x.key === it.key) === i);
        } else if (s.special === 'applecharts') {
          list = parseItems('podcast', await getJson('/api/apple/top/' + (region.length === 2 ? region.toLowerCase() : 'us')));
        } else {
          list = parseItems(s.itemKind, await dz(s.path));
        }
        if (!list.length) throw new Error('vacío');
        list.forEach(remember);
        s.items = list;
      } else {
        s.tracks = parseDeezerTracks(await dz(s.path));
        if (!s.tracks.length) throw new Error('vacío');
      }
      s.state = 'ready';
    } catch (e) {
      s.state = 'failed';
    }
    changed();
  }

  // Contenido de una página de ítem: { state, tracks }
  const itemData = {};
  function parseEpisodes(xml, pod) {
    const doc = new DOMParser().parseFromString(xml.trim(), 'text/xml');
    if (doc.querySelector('parsererror')) throw new Error('feed inválido');
    const first = (parent, name) => parent.getElementsByTagNameNS('*', name)[0] || parent.getElementsByTagName(name)[0];
    const text = (parent, name) => { const n = first(parent, name); return n ? n.textContent.trim() : ''; };
    const channel = doc.getElementsByTagName('channel')[0];
    let channelArt = pod.art;
    if (!channelArt && channel) {
      for (const n of channel.getElementsByTagNameNS('*', 'image')) { if (n.getAttribute('href')) { channelArt = n.getAttribute('href'); break; } }
    }
    const out = [];
    for (const it of doc.getElementsByTagName('item')) {
      if (out.length >= 100) break;
      const enc2 = first(it, 'enclosure');
      const url = enc2 && enc2.getAttribute('url');
      const type = (enc2 && enc2.getAttribute('type')) || '';
      if (!url || (type && !type.toLowerCase().startsWith('audio'))) continue;
      let art = null;
      for (const n of it.getElementsByTagNameNS('*', 'image')) { if (n.getAttribute('href')) { art = n.getAttribute('href'); break; } }
      const guid = text(it, 'guid') || url;
      const dur = text(it, 'duration').split(':').reduce((a, p) => a * 60 + (Number(p) || 0), 0) * 1000;
      const pub = Date.parse(text(it, 'pubDate')) || 0;
      const t = { key: `ep:${pod.key}:${guid}`, kind: 'episode', title: text(it, 'title') || 'Episodio', artist: pod.title, album: pod.title,
        art: art || channelArt, dur, pub, url: url.trim() };
      if (!out.some((x) => x.key === t.key)) out.push(t);
    }
    return out;
  }

  async function fetchItemTracks(it) {
    if (it.kind === 'album') {
      const body = await dz(`album/${it.id}/tracks?limit=100`);
      const album = { title: it.title, id: it.id, cover_big: it.art };
      const out = [];
      for (const o of body.data || []) if (o.preview) out.push(trackFromDeezer(o, album));
      return out;
    }
    if (it.kind === 'playlist') return parseDeezerTracks(await dz(`playlist/${it.id}/tracks?limit=100`));
    if (it.kind === 'genre') return parseDeezerTracks(await dz(`chart/${it.id}/tracks?limit=50`));
    if (it.kind === 'artist') return parseDeezerTracks(await dz(`artist/${it.id}/top?limit=50`));
    if (!it.feed) {
      const r = await getJson(`/api/apple/lookup?id=${it.id}`);
      const f = r.results && r.results[0] && r.results[0].feedUrl;
      if (!f) throw new Error('sin feed');
      it.feed = f;
      remember(it);
    }
    const res = await fetch('/api/feed?url=' + enc(it.feed));
    if (!res.ok) throw new Error('HTTP ' + res.status);
    return parseEpisodes(await res.text(), it);
  }

  async function loadItem(it, force) {
    const d = itemData[it.key] || (itemData[it.key] = { state: 'idle', tracks: [] });
    if (d.state === 'loading' || (d.state === 'ready' && !force)) return d;
    d.state = 'loading';
    changed();
    try {
      d.tracks = await fetchItemTracks(it);
      d.state = d.tracks.length ? 'ready' : 'failed';
    } catch (e) {
      d.state = 'failed';
    }
    changed();
    return d;
  }

  // ================================================================ biblioteca del usuario

  const lib = {
    likes: store.get('likes', []),
    lists: store.get('lists', {}),
    history: store.get('history', []),
    follows: store.get('follows', []),
    progress: store.get('progress', {}),
    searches: store.get('searches', []),
    local: [],
    localLoaded: false,
    localDir: '',
  };
  const save = (k) => store.set(k, lib[k]);
  const isLiked = (t) => !!t && lib.likes.some((x) => x.key === t.key);
  function toggleLike(t) {
    if (!t) return;
    const i = lib.likes.findIndex((x) => x.key === t.key);
    if (i >= 0) lib.likes.splice(i, 1); else lib.likes.unshift(slim(t));
    save('likes');
    changed();
  }
  const slim = (t) => ({ ...t, at: undefined });
  function recordPlayed(t) {
    const i = lib.history.findIndex((x) => x.key === t.key);
    if (i >= 0) lib.history.splice(i, 1);
    lib.history.unshift(slim(t));
    lib.history.length = Math.min(lib.history.length, 50);
    save('history');
  }
  function addToList(name, t) {
    const l = lib.lists[name];
    if (!l || l.some((x) => x.key === t.key)) return false;
    l.push(slim(t));
    save('lists');
    changed();
    return true;
  }
  function createList(name) {
    name = (name || '').trim();
    if (!name || lib.lists[name]) return false;
    lib.lists[name] = [];
    save('lists');
    changed();
    return true;
  }
  function renameList(from, to) {
    to = (to || '').trim();
    if (!to || !lib.lists[from] || (to !== from && lib.lists[to])) return false;
    const next = {};
    for (const [k, v] of Object.entries(lib.lists)) next[k === from ? to : k] = v;
    lib.lists = next;
    save('lists');
    changed();
    return true;
  }
  function deleteList(name) { delete lib.lists[name]; save('lists'); changed(); }
  const isFollowing = (it) => lib.follows.some((x) => x.key === it.key);
  function toggleFollow(it) {
    const i = lib.follows.findIndex((x) => x.key === it.key);
    if (i >= 0) lib.follows.splice(i, 1); else lib.follows.unshift(remember(it));
    save('follows');
    changed();
  }
  function addSearch(text) {
    text = (text || '').trim();
    if (text.length < 2) return;
    lib.searches = [text, ...lib.searches.filter((x) => fold(x) !== fold(text))].slice(0, 10);
    save('searches');
  }
  function saveProgress(t, pos, dur) {
    if (!t || t.kind !== 'episode') return;
    const finished = dur > 0 && pos >= dur - 15000;
    if (finished) delete lib.progress[t.key];
    else if (pos >= 5000) { delete lib.progress[t.key]; lib.progress[t.key] = { pos, dur, t: slim(t), at: Date.now() }; }
    else return;
    save('progress');
  }
  const progressOf = (t) => (lib.progress[t.key] ? lib.progress[t.key].pos : 0);
  const inProgress = () => Object.values(lib.progress).sort((a, b) => b.at - a.at).map((p) => p.t);

  async function loadLocal(force) {
    if (lib.localLoaded && !force) return;
    try {
      const body = await getJson('/api/library' + (force ? '?rescan=1' : ''));
      lib.local = body.tracks.map(trackFromLocal);
      lib.localDir = body.dir;
      lib.localLoaded = true;
    } catch (e) {
      lib.local = [];
    }
    changed();
  }

  // ================================================================ letras

  const lyricsCache = new Map();
  function parseLrc(text) {
    const lines = [];
    for (const raw of text.split(/\r?\n/)) {
      const stamps = [...raw.matchAll(/\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]/g)];
      if (!stamps.length) continue;
      const last = stamps[stamps.length - 1];
      const body = raw.slice(last.index + last[0].length).trim();
      for (const m of stamps) {
        let ms = Number(m[1]) * 60000 + Number(m[2]) * 1000;
        if (m[3]) ms += m[3].length === 1 ? Number(m[3]) * 100 : m[3].length === 2 ? Number(m[3]) * 10 : Number(m[3]);
        lines.push({ ms, text: body });
      }
    }
    return lines.sort((a, b) => a.ms - b.ms);
  }
  const has = (o, k) => o && o[k] != null && String(o[k]).length > 0;
  function lyricsFromJson(o) {
    const synced = has(o, 'syncedLyrics') ? parseLrc(o.syncedLyrics) : [];
    let plain = has(o, 'plainLyrics') ? String(o.plainLyrics).trim() : '';
    if (!plain && synced.length) plain = synced.map((l) => l.text).join('\n');
    return { synced, plain, instrumental: !!o.instrumental };
  }
  async function fetchLyrics(t) {
    if (t.kind === 'radio' || t.kind === 'episode') return { none: true, kind: t.kind };
    if (lyricsCache.has(t.key)) return lyricsCache.get(t.key);
    let qs = `artist_name=${enc(t.artist)}&track_name=${enc(t.title)}`;
    if (t.album) qs += `&album_name=${enc(t.album)}`;
    if (t.dur > 0) qs += `&duration=${Math.round(t.dur / 1000)}`;
    let res = null;
    try {
      res = lyricsFromJson(await getJson('/api/lrclib/get?' + qs));
    } catch (e) {
      if (e.status !== 404) throw e;
      const list = await getJson(`/api/lrclib/search?track_name=${enc(t.title)}&artist_name=${enc(t.artist)}`);
      const pick = (Array.isArray(list) ? list : []).filter((o) => has(o, 'syncedLyrics') || has(o, 'plainLyrics') || o.instrumental);
      const best = pick.find((o) => has(o, 'syncedLyrics')) || pick[0];
      res = best ? lyricsFromJson(best) : { synced: [], plain: '', instrumental: false };
    }
    lyricsCache.set(t.key, res);
    return res;
  }
  function currentLine(lines, posMs) {
    let lo = 0, hi = lines.length - 1, ans = -1;
    const p = posMs + 200;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      if (lines[mid].ms <= p) { ans = mid; lo = mid + 1; } else hi = mid - 1;
    }
    return ans;
  }

  // ================================================================ reproductor

  const audio = new Audio();
  audio.preload = 'auto';
  const player = {
    queue: [], order: [], pos: -1, name: '', shuffle: false, repeat: 0,
    speed: store.get('speed', 1), volume: store.get('volume', 0.8), muted: false,
    state: 'idle', // idle | loading | playing | paused
    token: 0, errors: 0, pendingSeek: 0, sleepEndsAt: 0, sleepAtEnd: false, sleepTimer: null,
  };
  audio.volume = player.volume;
  const listenersP = new Set();
  const pchanged = () => { scheduleSession(); listenersP.forEach((f) => f()); };

  const current = () => (player.pos >= 0 && player.pos < player.order.length ? player.queue[player.order[player.pos]] : null);
  const queueTracks = () => player.order.map((i) => player.queue[i]);

  function buildOrder(start) {
    const n = player.queue.length;
    player.order = [...Array(n).keys()];
    if (player.shuffle) {
      const rest = player.order.filter((i) => i !== start);
      for (let i = rest.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [rest[i], rest[j]] = [rest[j], rest[i]]; }
      player.order = n ? [start, ...rest] : [];
      player.pos = n ? 0 : -1;
    } else {
      player.pos = n ? start : -1;
    }
  }

  function rememberEpisode() {
    const t = current();
    if (t && t.kind === 'episode' && audio.duration) saveProgress(t, audio.currentTime * 1000, audio.duration * 1000);
  }

  async function ensureUrl(t) {
    if (t.kind === 'preview' && t.id && (!t.url || Date.now() - (t.at || 0) > 60000)) {
      const o = await dz('track/' + t.id);
      if (!o.preview) throw new Error('sin vista previa');
      t.url = o.preview;
      t.at = Date.now();
    }
    if (!t.url) throw new Error('sin enlace');
  }

  async function load(autoplay, seekMs) {
    rememberEpisode();
    const tk = ++player.token;
    const t = current();
    audio.pause();
    if (!t) { player.state = 'idle'; pchanged(); return; }
    player.state = 'loading';
    player.pendingSeek = seekMs || (t.kind === 'episode' ? progressOf(t) : 0);
    pchanged();
    try {
      await ensureUrl(t);
    } catch (e) {
      if (tk === player.token) fail();
      return;
    }
    if (tk !== player.token) return;
    audio.src = t.url;
    audio.playbackRate = t.kind === 'radio' ? 1 : player.speed;
    audio.preservesPitch = true;
    audio.load();
    if (autoplay) {
      try { await audio.play(); } catch (e) { if (tk === player.token && e.name !== 'AbortError') { player.state = 'paused'; pchanged(); } }
    } else {
      player.state = 'paused';
      pchanged();
    }
  }

  function fail() {
    const t = current();
    player.errors++;
    toast(t ? `No se pudo reproducir "${t.title}"${t.kind !== 'local' ? ' (revisa tu conexión)' : ''}` : 'Error de reproducción');
    if (player.errors < Math.min(player.order.length, 5) && player.pos + 1 < player.order.length) {
      player.pos++;
      load(true);
    } else {
      player.state = 'paused';
      pchanged();
    }
  }

  audio.addEventListener('loadedmetadata', () => {
    const t = current();
    if (t && audio.duration && isFinite(audio.duration) && t.kind !== 'radio') t.dur = audio.duration * 1000;
    if (player.pendingSeek > 0 && t && t.kind !== 'radio') { audio.currentTime = player.pendingSeek / 1000; player.pendingSeek = 0; }
    pchanged();
  });
  audio.addEventListener('playing', () => {
    player.state = 'playing';
    player.errors = 0;
    const t = current();
    if (t && player.recorded !== player.token + ':' + t.key) { player.recorded = player.token + ':' + t.key; recordPlayed(t); }
    pchanged();
    setMediaSession();
  });
  audio.addEventListener('pause', () => {
    if (audio.ended) return;
    if (player.state === 'playing') player.state = 'paused';
    rememberEpisode();
    pchanged();
  });
  audio.addEventListener('waiting', () => { if (player.state === 'playing') { player.state = 'loading'; pchanged(); } });
  audio.addEventListener('canplay', () => { if (player.state === 'loading' && !audio.paused) { player.state = 'playing'; pchanged(); } });
  audio.addEventListener('error', () => { if (audio.src && audio.getAttribute('src') !== '') fail(); });
  audio.addEventListener('ended', () => {
    const t = current();
    if (t && t.kind === 'episode') saveProgress(t, audio.duration * 1000, audio.duration * 1000);
    if (player.sleepAtEnd) {
      player.sleepAtEnd = false;
      player.state = 'paused';
      if (player.repeat !== 2 && player.order.length > 1) player.pos = (player.pos + 1) % player.order.length;
      load(false);
      return;
    }
    if (player.repeat === 2) { audio.currentTime = 0; audio.play(); return; }
    advance(false);
  });
  audio.addEventListener('timeupdate', () => uiTick());
  setInterval(() => { if (player.state === 'playing') rememberEpisode(); }, 15000);

  function advance(user) {
    if (!player.order.length) return;
    if (player.pos + 1 < player.order.length) { player.pos++; load(true); }
    else if (player.repeat === 1 || (user && player.order.length > 1)) {
      if (player.shuffle) buildOrder(player.order[Math.floor(Math.random() * player.order.length)]); else player.pos = 0;
      load(true);
    } else { player.pos = 0; load(false); }
  }

  const api = {
    playList(tracks, index, name) {
      if (!tracks || !tracks.length) return;
      if (name && name.startsWith('Búsqueda: ')) addSearch(name.slice(10));
      player.queue = tracks.map((t) => ({ ...t }));
      player.name = name || '';
      buildOrder(Math.max(0, Math.min(index, tracks.length - 1)));
      player.errors = 0;
      load(true);
    },
    shufflePlay(tracks, name) {
      if (!tracks || !tracks.length) return;
      player.shuffle = true;
      api.playList(tracks, Math.floor(Math.random() * tracks.length), name);
    },
    playNext(t) {
      if (!current()) { api.playList([t], 0, t.title); return; }
      player.queue.push({ ...t });
      player.order.splice(player.pos + 1, 0, player.queue.length - 1);
      pchanged();
    },
    enqueue(t) {
      if (!current()) { api.playList([t], 0, t.title); return; }
      player.queue.push({ ...t });
      player.order.push(player.queue.length - 1);
      pchanged();
    },
    toggle() {
      if (!current()) return;
      if (audio.paused || player.state === 'paused') api.play(); else api.pause();
    },
    play() {
      if (!current()) return;
      if (!audio.getAttribute('src')) { load(true, player.pendingSeek); return; }
      audio.play().catch(() => {});
    },
    pause() { audio.pause(); },
    next() { player.errors = 0; advance(true); },
    prev() {
      if (!current()) return;
      if ((audio.currentTime > 3 && current().kind !== 'radio') || player.pos === 0) { audio.currentTime = 0; if (audio.paused) api.play(); return; }
      player.pos--;
      load(true);
    },
    jumpTo(i) { if (i >= 0 && i < player.order.length) { player.pos = i; player.errors = 0; load(true); } },
    removeAt(i) {
      if (i < 0 || i >= player.order.length) return;
      const wasPlaying = player.state === 'playing' || player.state === 'loading';
      const qi = player.order[i];
      player.queue.splice(qi, 1);
      player.order.splice(i, 1);
      player.order = player.order.map((x) => (x > qi ? x - 1 : x));
      if (!player.order.length) { api.stop(); return; }
      if (i < player.pos) { player.pos--; pchanged(); }
      else if (i === player.pos) { if (player.pos >= player.order.length) player.pos = player.order.length - 1; load(wasPlaying); }
      else pchanged();
    },
    clearUpcoming() { while (player.order.length > player.pos + 1) api.removeAt(player.order.length - 1); },
    stop() {
      player.token++;
      rememberEpisode();
      audio.pause();
      audio.removeAttribute('src');
      audio.load();
      player.queue = []; player.order = []; player.pos = -1; player.state = 'idle';
      api.cancelSleep();
      store.set('session', null);
      pchanged();
    },
    seek(ms) {
      const t = current();
      if (!t || t.kind === 'radio') return;
      if (!audio.getAttribute('src')) { player.pendingSeek = ms; pchanged(); return; }
      audio.currentTime = Math.max(0, ms / 1000);
    },
    skip(delta) {
      const dur = audio.duration ? audio.duration * 1000 : Infinity;
      api.seek(Math.max(0, Math.min(audio.currentTime * 1000 + delta, dur - 1000)));
    },
    toggleShuffle() {
      player.shuffle = !player.shuffle;
      const cur = player.pos >= 0 ? player.order[player.pos] : 0;
      buildOrder(cur);
      pchanged();
    },
    cycleRepeat() { player.repeat = (player.repeat + 1) % 3; pchanged(); },
    setSpeed(s) {
      player.speed = Math.max(0.5, Math.min(2, s));
      store.set('speed', player.speed);
      const t = current();
      if (t && t.kind !== 'radio') audio.playbackRate = player.speed;
      pchanged();
    },
    setVolume(v) { player.volume = v; audio.volume = v; store.set('volume', v); },
    setSleep(minutes) {
      api.cancelSleep();
      if (minutes <= 0) return;
      player.sleepEndsAt = Date.now() + minutes * 60000;
      player.sleepTimer = setTimeout(() => {
        player.sleepEndsAt = 0;
        player.sleepTimer = null;
        fadeOut();
        pchanged();
      }, minutes * 60000);
      pchanged();
    },
    sleepEnd() { api.cancelSleep(); player.sleepAtEnd = true; pchanged(); },
    cancelSleep() {
      if (player.sleepTimer) clearTimeout(player.sleepTimer);
      player.sleepTimer = null; player.sleepEndsAt = 0; player.sleepAtEnd = false;
      pchanged();
    },
  };

  function fadeOut() {
    if (audio.paused) return;
    let i = 0;
    const tk = player.token;
    const id = setInterval(() => {
      i++;
      if (tk !== player.token) { clearInterval(id); audio.volume = player.volume; return; }
      audio.volume = Math.max(0, player.volume * (1 - i / 10));
      if (i >= 10) { clearInterval(id); audio.pause(); audio.volume = player.volume; }
    }, 300);
  }

  // ---- sesión: se guarda la cola y se vuelve a ella en pausa
  let sessionTimer = null;
  function scheduleSession() {
    if (sessionTimer) return;
    sessionTimer = setTimeout(() => { sessionTimer = null; saveSession(); }, 2500);
  }
  function saveSession() {
    if (player.pos < 0) return;
    const start = Math.max(0, player.pos - 50);
    const end = Math.min(player.order.length, start + 300);
    store.set('session', {
      tracks: queueTracks().slice(start, end).map(slim), pos: player.pos - start,
      ms: Math.round((audio.currentTime || player.pendingSeek / 1000 || 0) * 1000),
      shuffle: player.shuffle, repeat: player.repeat, name: player.name,
    });
  }
  function restoreSession() {
    const s = store.get('session', null);
    if (!s || !s.tracks || !s.tracks.length) return;
    player.queue = s.tracks;
    player.order = s.tracks.map((_, i) => i);
    player.pos = Math.max(0, Math.min(s.pos || 0, s.tracks.length - 1));
    player.shuffle = !!s.shuffle;
    player.repeat = s.repeat || 0;
    player.name = s.name || '';
    const t = current();
    player.pendingSeek = t && t.kind === 'radio' ? 0 : s.ms || 0;
    player.state = 'paused';
  }
  window.addEventListener('beforeunload', () => { rememberEpisode(); saveSession(); });

  function setMediaSession() {
    if (!('mediaSession' in navigator)) return;
    const t = current();
    if (!t) return;
    try {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: t.title, artist: t.artist, album: t.album || '',
        artwork: t.art ? [{ src: new URL(t.art, location.href).toString() }] : [],
      });
      const set = (a, f) => { try { navigator.mediaSession.setActionHandler(a, f); } catch (e) { /* no soportado */ } };
      set('play', () => api.play());
      set('pause', () => api.pause());
      set('previoustrack', () => api.prev());
      set('nexttrack', () => api.next());
      set('seekbackward', () => api.skip(-15000));
      set('seekforward', () => api.skip(30000));
      set('seekto', (d) => api.seek(d.seekTime * 1000));
    } catch (e) { /* sin MediaSession */ }
  }

  // ================================================================ piezas de interfaz

  function cover(obj, { round = false, glyph = '♪' } = {}) {
    const seed = obj.title || obj.name || '?';
    const hue = hashCode(String(seed)) % 360;
    const c = h('div', { class: 'cover' + (round ? ' round' : '') });
    c.style.background = `linear-gradient(135deg,hsl(${hue} 70% 45%),hsl(${(hue + 50) % 360} 70% 28%))`;
    c.append(glyph);
    if (obj.art) {
      const img = h('img', { src: obj.art, alt: '', loading: 'lazy', referrerpolicy: 'no-referrer' });
      img.addEventListener('error', () => img.remove());
      c.append(img);
    }
    return c;
  }

  function status(text, retry) {
    return h('div', { class: 'status' }, text, retry ? h('button', { text: 'Reintentar', onclick: retry }) : null);
  }

  function shelf(title, sub, body, href) {
    return h('section', { class: 'shelf' },
      h('div', { class: 'shelf-head' }, h('div', null, h('h2', { text: title }), sub ? h('div', { class: 'sub', text: sub }) : null),
        href ? h('a', { href, text: 'Ver todo' }) : null),
      body);
  }

  function trackCard(t, list, i, name) {
    const card = h('div', { class: 'card', title: t.title, onclick: () => api.playList(list, i, name) },
      cover(t), h('div', { class: 't', text: t.title }), h('div', { class: 's', text: t.kind === 'episode' ? episodeInfo(t) : t.artist }),
      h('div', { class: 'play-fab', text: '▶' }));
    return card;
  }

  function itemCard(it) {
    const round = it.kind === 'artist';
    return h('div', { class: 'card', onclick: () => { remember(it); location.hash = `#/item/${it.kind}/${it.id}`; } },
      cover(it, { round, glyph: it.kind === 'podcast' ? '🎙' : '♪' }), h('div', { class: 't', text: it.title }),
      h('div', { class: 's', text: round ? '' : it.sub }));
  }

  const row = (cards) => h('div', { class: 'row' }, cards);
  const trackRowCards = (tracks, name, max = 20) => row(tracks.slice(0, max).map((t, i) => trackCard(t, tracks, i, name)));
  const itemRowCards = (list, max = 20) => row(list.slice(0, max).map(itemCard));

  function sectionShelf(s, href) {
    let body;
    if (s.state === 'ready') body = s.type === 'items' ? itemRowCards(s.items) : trackRowCards(s.tracks, s.title);
    else if (s.state === 'failed') body = status('Sin conexión.', () => loadSection(s));
    else body = status('Cargando…');
    return shelf(s.title, s.sub, body, href || `#/section/${s.id}`);
  }

  function episodeInfo(t) {
    const parts = [];
    if (t.pub) parts.push(new Date(t.pub).toLocaleDateString('es', { day: 'numeric', month: 'short' }));
    const done = progressOf(t);
    const dur = t.dur || (lib.progress[t.key] && lib.progress[t.key].dur) || 0;
    if (dur) parts.push(done ? 'faltan ' + minutesText(dur - done) : minutesText(dur));
    return parts.join(' · ');
  }

  function trackRows(tracks, { name, ctx, showAlbum = true } = {}) {
    const wrap = h('div', { class: 'tracks' });
    tracks.forEach((t, i) => {
      const now = current();
      const playing = now && now.key === t.key;
      const liked = isLiked(t);
      const r = h('div', { class: 'trow' + (playing ? ' playing' : '') + (showAlbum ? '' : ' noalbum'), 'data-key': t.key, ondblclick: () => api.playList(tracks, i, name) },
        h('div', { class: 'idx', text: playing ? '♫' : i + 1, onclick: () => api.playList(tracks, i, name) }),
        h('div', { class: 'main', onclick: () => api.playList(tracks, i, name), style: 'cursor:pointer' }, cover(t),
          h('div', { style: 'min-width:0' }, h('div', { class: 'tt', text: t.title }),
            h('div', { class: 'ta', text: t.kind === 'episode' ? episodeInfo(t) : t.kind === 'radio' ? 'Radio en vivo · ' + t.artist : t.artist }))),
        showAlbum ? h('div', { class: 'alb', text: t.kind === 'episode' ? '' : t.album || '' }) : null,
        h('button', { class: liked ? 'liked' : '', text: liked ? '♥' : '♡', title: 'Me gusta', 'aria-label': 'Me gusta', onclick: () => toggleLike(t) }),
        h('div', { class: 'dur', text: t.kind === 'radio' ? 'EN VIVO' : fmtTime(t.dur) }),
        h('button', { text: '⋯', title: 'Más opciones', 'aria-label': 'Más opciones', onclick: (ev) => trackMenu(ev, t, ctx) }));
      wrap.append(r);
    });
    return wrap;
  }

  // ---- menús y diálogos
  function closeMenu() { $('#menu').hidden = true; }
  function popup(ev, entries) {
    ev.stopPropagation();
    const m = $('#menu');
    m.replaceChildren(...entries.filter(Boolean).map(([label, fn]) => h('button', { text: label, onclick: () => { closeMenu(); fn(); } })));
    m.hidden = false;
    const r = ev.currentTarget.getBoundingClientRect();
    m.style.left = Math.max(8, Math.min(r.left - 140, innerWidth - 230)) + 'px';
    m.style.top = Math.max(8, Math.min(r.bottom + 4, innerHeight - m.offsetHeight - 8)) + 'px';
  }
  document.addEventListener('click', closeMenu);

  function modal(build) {
    const box = $('#modal');
    const close = () => { box.hidden = true; box.replaceChildren(); };
    box.replaceChildren(h('div', { class: 'dialog' }, build(close)));
    box.hidden = false;
    box.onclick = (e) => { if (e.target === box) close(); };
    return close;
  }
  function askText(title, initial, ok) {
    modal((close) => {
      const input = h('input', { type: 'text', value: initial || '', 'aria-label': title });
      const go = () => { const v = input.value; close(); ok(v); };
      input.addEventListener('keydown', (e) => { if (e.key === 'Enter') go(); if (e.key === 'Escape') close(); });
      setTimeout(() => input.focus(), 0);
      return [h('h3', { text: title }), input, h('div', { class: 'foot' }, h('button', { class: 'btn ghost', text: 'Cancelar', onclick: close }), h('button', { class: 'btn green', text: 'Aceptar', onclick: go }))];
    });
  }
  function choose(title, options, pick) {
    modal((close) => [h('h3', { text: title }), h('div', { class: 'opts' }, options.map(([label, val]) => h('button', { text: label, onclick: () => { close(); pick(val); } }))),
      h('div', { class: 'foot' }, h('button', { class: 'btn ghost', text: 'Cerrar', onclick: close }))]);
  }
  function confirmBox(title, text, ok) {
    modal((close) => [h('h3', { text: title }), h('p', { class: 'sub', text }), h('div', { class: 'foot' },
      h('button', { class: 'btn ghost', text: 'Cancelar', onclick: close }), h('button', { class: 'btn green', text: 'Eliminar', onclick: () => { close(); ok(); } }))]);
  }

  function addToPlaylistDialog(t) {
    const names = Object.keys(lib.lists);
    choose('Añadir a playlist', [['+ Nueva playlist', '__new'], ...names.map((n) => [n, n])], (v) => {
      if (v === '__new') {
        askText('Nueva playlist', '', (name) => {
          if (!createList(name)) { toast('Ese nombre no está disponible'); return; }
          addToList(name.trim(), t); toast('Añadida a ' + name.trim());
        });
      } else toast(addToList(v, t) ? 'Añadida a ' + v : 'Ya estaba en ' + v);
    });
  }

  function trackMenu(ev, t, ctx) {
    popup(ev, [
      [isLiked(t) ? 'Quitar de Me gusta' : 'Añadir a Me gusta', () => toggleLike(t)],
      ['Reproducir a continuación', () => { api.playNext(t); toast('Se reproducirá a continuación'); }],
      ['Añadir a la cola', () => { api.enqueue(t); toast('Añadida a la cola'); }],
      ['Añadir a playlist…', () => addToPlaylistDialog(t)],
      t.artistId ? ['Ir al artista', () => { remember(item('artist', t.artistId, t.artist, 'Artista', null)); location.hash = `#/item/artist/${t.artistId}`; }] : null,
      ctx && ctx.playlist ? ['Quitar de esta playlist', () => { const l = lib.lists[ctx.playlist]; const i = l.findIndex((x) => x.key === t.key); if (i >= 0) { l.splice(i, 1); save('lists'); changed(); } }] : null,
    ]);
  }

  // ================================================================ vistas

  const main = $('#view');
  const views = {};
  let routeToken = 0;

  function routeParts() {
    const hash = location.hash.replace(/^#/, '') || '/';
    const [p, qs] = hash.split('?');
    return { parts: p.split('/').filter(Boolean).map(decodeURIComponent), params: new URLSearchParams(qs || '') };
  }

  function greeting() {
    const hr = new Date().getHours();
    return hr < 5 ? 'Buenas noches' : hr < 12 ? 'Buenos días' : hr < 20 ? 'Buenas tardes' : 'Buenas noches';
  }

  function tile(title, glyph, href, art) {
    return h('a', { class: 'tile', href }, cover({ title, art }, { glyph }), title);
  }

  views.home = () => {
    for (const s of SECTIONS) if (s.featured && s.state === 'idle') loadSection(s);
    if (!lib.localLoaded) loadLocal();
    const root = h('div', null, h('h1', { text: greeting() }));
    const tiles = [tile('Canciones que te gustan', '♥', '#/likes'), tile('Tu música', '📁', '#/local'),
      tile('Top 50 mundial', '♪', '#/section/top'), tile('Radios en vivo', '📻', '#/section/radio')];
    if (lib.history.length) tiles.push(tile('Escuchado recientemente', '⏱', '#/history'));
    Object.keys(lib.lists).slice(0, 3).forEach((n) => tiles.push(tile(n, '♪', '#/playlist/' + enc(n))));
    root.append(h('div', { class: 'tiles' }, tiles));
    const resume = inProgress();
    if (lib.history.length) root.append(shelf('Escuchado recientemente', null, trackRowCards(lib.history, 'Escuchado recientemente', 15), '#/history'));
    if (resume.length) root.append(shelf('Seguir escuchando', 'Podcasts que dejaste a medias', trackRowCards(resume, 'Seguir escuchando', 10)));
    if (lib.follows.length) root.append(shelf('Tus podcasts', null, itemRowCards(lib.follows)));
    for (const s of SECTIONS) if (s.featured) root.append(sectionShelf(s));
    root.append(h('p', { class: 'muted' }, 'Más géneros, radios y podcasts en ', h('a', { href: '#/search', class: 'link', text: 'Buscar' }), '.'));
    return root;
  };

  views.search = ({ params }) => {
    const text = params.get('q') || '';
    const root = h('div', null, h('h1', { text: 'Buscar' }));
    const input = h('input', { type: 'search', placeholder: '¿Qué quieres escuchar?', value: text, 'aria-label': 'Buscar', id: 'q' });
    let timer = null;
    input.addEventListener('input', () => {
      clearTimeout(timer);
      timer = setTimeout(() => { history.replaceState(null, '', '#/search' + (input.value.trim() ? '?q=' + enc(input.value.trim()) : '')); routeNoFocusLoss(); }, 350);
    });
    root.append(h('div', { class: 'searchbox' }, '🔍', input));
    if (!text.trim()) {
      if (lib.searches.length) {
        root.append(h('div', { class: 'shelf-head' }, h('h2', { text: 'Búsquedas recientes' }),
          h('a', { href: '#', text: 'Borrar', onclick: (e) => { e.preventDefault(); lib.searches = []; save('searches'); render(); } })));
        root.append(h('div', { class: 'chips' }, lib.searches.map((s) => h('a', { class: 'chip', href: '#/search?q=' + enc(s), text: s }))));
      }
      root.append(h('h2', { style: 'margin:26px 0 12px', text: 'Explorar todo' }));
      root.append(h('div', { class: 'grid' }, SECTIONS.map((s) => {
        const hue = hashCode(s.title) % 360;
        const c = h('a', { class: 'card', href: `#/section/${s.id}` }, h('div', { class: 't', text: s.title }), h('div', { class: 's', text: s.sub }));
        c.style.background = `linear-gradient(135deg,hsl(${hue} 55% 38%),hsl(${(hue + 40) % 360} 55% 24%))`;
        c.style.minHeight = '96px';
        return c;
      })));
      setTimeout(() => input.focus(), 0);
      return root;
    }
    const out = h('div');
    root.append(out);
    runSearch(text.trim(), out, ++routeToken);
    setTimeout(() => { input.focus(); input.setSelectionRange(input.value.length, input.value.length); }, 0);
    return root;
  };

  async function runSearch(text, out, token) {
    const blocks = {};
    const order = ['Canciones', 'Artistas', 'Álbumes', 'Radios', 'Podcasts'];
    const set = (name, node) => {
      if (token !== routeToken) return;
      blocks[name] = node;
      out.replaceChildren(...order.filter((n) => blocks[n]).map((n) => blocks[n]));
    };
    order.forEach((n) => set(n, null));
    let failures = 0;
    const done = () => { if (token === routeToken && failures === 5) out.replaceChildren(status('No hay conexión a internet.')); };
    const local = lib.local.filter((t) => fold(t.title + ' ' + t.artist + ' ' + t.album).includes(fold(text)));
    dz(`search?limit=40&q=${enc(text)}`).then((b) => {
      const tracks = [...local, ...parseDeezerTracks(b)];
      if (tracks.length) set('Canciones', h('section', { class: 'shelf' }, h('h2', { text: 'Canciones' }), trackRows(tracks.slice(0, 15), { name: 'Búsqueda: ' + text })));
      else set('Canciones', null);
    }).catch(() => { failures++; if (local.length) set('Canciones', h('section', { class: 'shelf' }, h('h2', { text: 'Canciones' }), trackRows(local, { name: 'Búsqueda: ' + text }))); done(); });
    dz(`search/artist?limit=8&q=${enc(text)}`).then((b) => {
      const l = parseItems('artist', b); l.forEach(remember);
      if (l.length) set('Artistas', shelf('Artistas', null, itemRowCards(l)));
    }).catch(() => { failures++; done(); });
    dz(`search/album?limit=8&q=${enc(text)}`).then((b) => {
      const l = parseItems('album', b); l.forEach(remember);
      if (l.length) set('Álbumes', shelf('Álbumes', null, itemRowCards(l)));
    }).catch(() => { failures++; done(); });
    getJson(radioSearch('name=' + enc(text)).replace('limit=40', 'limit=10')).then((b) => {
      const l = parseRadios(b);
      if (l.length) set('Radios', shelf('Radios', null, trackRowCards(l, 'Radios')));
    }).catch(() => { failures++; done(); });
    getJson(`/api/apple/search?media=podcast&entity=podcast&limit=10&term=${enc(text)}`).then((b) => {
      const l = parseItems('podcast', b); l.forEach(remember);
      if (l.length) set('Podcasts', shelf('Podcasts', null, itemRowCards(l)));
    }).catch(() => { failures++; done(); });
    setTimeout(() => { if (token === routeToken && !out.children.length) out.replaceChildren(status(`No se encontró nada para "${text}"`)); }, 2500);
  }

  views.podcasts = ({ params }) => {
    const text = params.get('q') || '';
    const pop = section('podcasts');
    if (pop.state === 'idle') loadSection(pop);
    const root = h('div', null, h('h1', { text: 'Podcasts' }));
    const input = h('input', { type: 'search', placeholder: 'Buscar podcasts', value: text, 'aria-label': 'Buscar podcasts', id: 'q' });
    let timer = null;
    input.addEventListener('input', () => {
      clearTimeout(timer);
      timer = setTimeout(() => { history.replaceState(null, '', '#/podcasts' + (input.value.trim() ? '?q=' + enc(input.value.trim()) : '')); routeNoFocusLoss(); }, 350);
    });
    root.append(h('div', { class: 'searchbox' }, '🔍', input));
    if (text.trim()) {
      const out = h('div', null, status('Buscando…'));
      root.append(out);
      const tk = routeToken;
      getJson(`/api/apple/search?media=podcast&entity=podcast&limit=25&term=${enc(text.trim())}`).then((b) => {
        if (tk !== routeToken) return;
        const l = parseItems('podcast', b); l.forEach(remember);
        out.replaceChildren(l.length ? h('div', { class: 'grid' }, l.map(itemCard)) : status(`No se encontraron podcasts para "${text.trim()}"`));
      }).catch(() => { if (tk === routeToken) out.replaceChildren(status('No hay conexión a internet.')); });
      setTimeout(() => { input.focus(); input.setSelectionRange(input.value.length, input.value.length); }, 0);
      return root;
    }
    const resume = inProgress();
    if (resume.length) root.append(shelf('Seguir escuchando', 'Continúa donde lo dejaste', trackRowCards(resume, 'Seguir escuchando', 10)));
    if (lib.follows.length) root.append(shelf('Tus podcasts', null, itemRowCards(lib.follows)));
    root.append(h('h2', { style: 'margin:26px 0 12px', text: 'Temas' }));
    root.append(h('div', { class: 'chips' }, ['Noticias', 'Comedia', 'Deportes', 'Tecnología', 'Historia', 'Negocios', 'Salud', 'Música', 'Ciencia', 'Crimen real', 'Educación', 'Cine']
      .map((s) => h('a', { class: 'chip', href: '#/podcasts?q=' + enc(s), text: s }))));
    root.append(sectionShelf(pop, '#/section/podcasts'));
    return root;
  };

  views.section = ({ parts }) => {
    const s = section(parts[1]);
    if (!s) return h('div', { class: 'empty', text: 'Sección no encontrada.' });
    if (s.state === 'idle') loadSection(s);
    const root = h('div', null, h('h1', { text: s.title }), h('div', { class: 'sub', text: s.sub }));
    if (s.state !== 'ready') { root.append(s.state === 'failed' ? status('Sin conexión.', () => loadSection(s)) : status('Cargando…')); return root; }
    if (s.type === 'items') root.append(h('div', { class: 'grid', style: 'margin-top:18px' }, s.items.map(itemCard)));
    else {
      root.append(h('div', { class: 'actions' }, h('button', { class: 'fab', text: '▶', 'aria-label': 'Reproducir', onclick: () => api.playList(s.tracks, 0, s.title) }),
        h('button', { class: 'btn ghost', text: 'Aleatorio', onclick: () => api.shufflePlay(s.tracks, s.title) })));
      root.append(trackRows(s.tracks, { name: s.title }));
    }
    return root;
  };

  function listPage(title, sub, tracks, { name, ctx, empty, extra } = {}) {
    const root = h('div', null, h('h1', { text: title }), sub ? h('div', { class: 'sub', text: sub }) : null);
    if (!tracks.length) { root.append(h('div', { class: 'empty', text: empty || 'Nada por aquí todavía.' })); if (extra) root.append(extra); return root; }
    root.append(h('div', { class: 'actions' }, h('button', { class: 'fab', text: '▶', 'aria-label': 'Reproducir', onclick: () => api.playList(tracks, 0, name || title) }),
      h('button', { class: 'btn ghost', text: 'Aleatorio', onclick: () => api.shufflePlay(tracks, name || title) }), extra));
    root.append(trackRows(tracks, { name: name || title, ctx }));
    return root;
  }

  views.likes = () => listPage('Canciones que te gustan', lib.likes.length + ' canciones', lib.likes, { empty: 'Las canciones que marques con ♥ aparecerán aquí.' });
  views.history = () => listPage('Escuchado recientemente', null, lib.history, {
    empty: 'Aún no has escuchado nada.',
    extra: lib.history.length ? h('button', { class: 'btn ghost', text: 'Borrar historial', onclick: () => { lib.history = []; save('history'); render(); } }) : null });
  views.local = () => {
    if (!lib.localLoaded) loadLocal();
    const root = listPage('Tu música', lib.localLoaded ? `${lib.local.length} canciones en ${lib.localDir}` : 'Buscando canciones…', lib.local, {
      empty: lib.localLoaded ? `No se encontraron canciones en ${lib.localDir}. Copia tus MP3 ahí o inicia Sonora con --music CARPETA.` : 'Buscando canciones…',
      extra: h('button', { class: 'btn ghost', text: 'Volver a buscar', onclick: () => { loadLocal(true); toast('Buscando canciones…'); } }) });
    return root;
  };
  views.playlist = ({ parts }) => {
    const name = parts[1];
    const l = lib.lists[name];
    if (!l) return h('div', { class: 'empty', text: 'Playlist no encontrada.' });
    return listPage(name, l.length + ' canciones', l, {
      ctx: { playlist: name }, empty: 'Esta playlist está vacía. Usa ⋯ › "Añadir a playlist…" en cualquier canción.',
      extra: [h('button', { class: 'btn ghost', text: 'Cambiar nombre', onclick: () => askText('Cambiar nombre', name, (v) => { if (renameList(name, v)) location.hash = '#/playlist/' + enc(v.trim()); else toast('Ese nombre no está disponible'); }) }),
        h('button', { class: 'btn ghost', text: 'Eliminar', onclick: () => confirmBox(`¿Eliminar "${name}"?`, 'La playlist se borrará. Las canciones no se eliminan.', () => { deleteList(name); location.hash = '#/library'; }) })] });
  };

  views.library = () => {
    if (!lib.localLoaded) loadLocal();
    const root = h('div', null, h('h1', { text: 'Tu biblioteca' }));
    root.append(h('div', { class: 'actions' }, h('button', { class: 'btn green', text: '+ Nueva playlist', onclick: () => askText('Nueva playlist', '', (v) => { if (createList(v)) location.hash = '#/playlist/' + enc(v.trim()); else toast('Ese nombre no está disponible'); }) })));
    const cards = [
      h('a', { class: 'card', href: '#/likes' }, cover({ title: 'Me gusta' }, { glyph: '♥' }), h('div', { class: 't', text: 'Canciones que te gustan' }), h('div', { class: 's', text: lib.likes.length + ' canciones' })),
      h('a', { class: 'card', href: '#/local' }, cover({ title: 'Tu música' }, { glyph: '📁' }), h('div', { class: 't', text: 'Tu música' }), h('div', { class: 's', text: lib.localLoaded ? lib.local.length + ' canciones' : 'Carpeta del PC' })),
      h('a', { class: 'card', href: '#/history' }, cover({ title: 'Historial' }, { glyph: '⏱' }), h('div', { class: 't', text: 'Escuchado recientemente' }), h('div', { class: 's', text: lib.history.length + ' canciones' })),
      ...Object.entries(lib.lists).map(([n, l]) => h('a', { class: 'card', href: '#/playlist/' + enc(n) }, cover({ title: n }), h('div', { class: 't', text: n }), h('div', { class: 's', text: 'Playlist · ' + l.length + ' canciones' }))),
      ...lib.follows.map(itemCard),
    ];
    root.append(h('div', { class: 'grid' }, cards));
    return root;
  };

  views.item = ({ parts }) => {
    const [, kind, id] = parts;
    const it = items[`${kind}:${id}`];
    if (!it) return h('div', { class: 'empty' }, 'No se encontró este elemento. ', h('a', { class: 'link', href: '#/', text: 'Volver al inicio' }));
    const d = itemData[it.key] || { state: 'idle', tracks: [] };
    if (d.state === 'idle') loadItem(it);
    const kinds = { album: 'Álbum', artist: 'Artista', playlist: 'Playlist', genre: 'Género', podcast: 'Podcast' };
    const root = h('div');
    const unit = kind === 'podcast' ? 'episodios' : 'canciones';
    const hero = h('div', { class: 'hero' }, cover(it, { round: kind === 'artist', glyph: kind === 'podcast' ? '🎙' : '♪' }),
      h('div', null, h('div', { class: 'kind', text: kinds[kind] }), h('h1', { text: it.title }),
        h('div', { class: 'sub', text: [it.sub && kind !== 'artist' && kind !== 'genre' ? it.sub : '', d.state === 'ready' ? `${d.tracks.length} ${unit}` : ''].filter(Boolean).join(' · ') })));
    root.append(hero);
    const actions = h('div', { class: 'actions' });
    if (d.state === 'ready') {
      actions.append(h('button', { class: 'fab', text: '▶', 'aria-label': 'Reproducir', onclick: () => api.playList(d.tracks, 0, it.title) }));
      if (kind !== 'podcast') actions.append(h('button', { class: 'btn ghost', text: 'Aleatorio', onclick: () => api.shufflePlay(d.tracks, it.title) }));
    }
    if (kind === 'podcast') actions.append(h('button', { class: isFollowing(it) ? 'btn ghost' : 'btn', text: isFollowing(it) ? 'Siguiendo' : 'Seguir', onclick: () => toggleFollow(it) }));
    root.append(actions);
    if (d.state === 'ready') root.append(trackRows(d.tracks, { name: it.title, showAlbum: kind !== 'album' && kind !== 'podcast' }));
    else root.append(d.state === 'failed' ? status('No se pudo cargar. Revisa tu conexión.', () => loadItem(it, true)) : status('Cargando…'));
    return root;
  };

  // ---- navegación y render
  function render() {
    const { parts, params } = routeParts();
    const name = parts[0] || 'home';
    const view = views[name] || views.home;
    routeToken++;
    const top = main.scrollTop;
    main.replaceChildren(view({ parts, params }));
    main.scrollTop = main.dataset.route === location.hash ? top : 0;
    main.dataset.route = location.hash;
    document.querySelectorAll('#navlinks a').forEach((a) => a.classList.toggle('on', a.dataset.r === (['home', 'search', 'podcasts', 'library'].includes(name) ? name : name === 'likes' || name === 'history' || name === 'local' || name === 'playlist' ? 'library' : name === 'section' || name === 'item' ? '' : 'home')));
    renderNavLists();
  }
  // Mantiene el foco del buscador mientras se actualizan los resultados.
  function routeNoFocusLoss() {
    const q2 = $('#q');
    const pos = q2 ? q2.selectionStart : 0;
    render();
    const n = $('#q');
    if (n) { n.focus(); n.setSelectionRange(pos, pos); }
  }

  function renderNavLists() {
    const box = $('#navlists');
    box.replaceChildren(...Object.keys(lib.lists).map((n) => h('a', { href: '#/playlist/' + enc(n), text: n })));
    if (!Object.keys(lib.lists).length) box.append(h('div', { class: 'muted', style: 'padding:6px 12px;font-size:12px', text: 'Aún no tienes playlists' }));
  }

  // Los cambios de datos actualizan la vista actual, salvo que el usuario esté escribiendo.
  let repaint = null;
  listeners.add(() => {
    if (repaint) return;
    repaint = setTimeout(() => {
      repaint = null;
      const a = document.activeElement;
      if (a && a.tagName === 'INPUT' && a.type !== 'range') { renderBarOnly(); return; }
      render();
    }, 30);
  });
  const renderBarOnly = () => renderNavLists();

  // ================================================================ barra del reproductor, cola y letra

  const bar = {
    track: $('#bar-track'), play: $('#b-play'), seek: $('#seek'), now: $('#t-now'), total: $('#t-total'),
    shuffle: $('#b-shuffle'), repeat: $('#b-repeat'), speed: $('#b-speed'), sleep: $('#b-sleep'),
    mute: $('#b-mute'), vol: $('#vol'), lyrics: $('#b-lyrics'), queue: $('#b-queue'),
    back: $('#b-skipback'), fwd: $('#b-skipfwd'),
  };
  let dragging = false;
  let shownKey = null;

  function updateBar() {
    const t = current();
    if (!t) {
      bar.track.replaceChildren();
      shownKey = null;
    } else if (shownKey !== t.key) {
      shownKey = t.key;
      const badge = t.kind === 'preview' ? 'VISTA PREVIA 30 s' : t.kind === 'radio' ? '● EN VIVO' : t.kind === 'episode' ? 'PODCAST' : '';
      bar.track.replaceChildren(cover(t), h('div', { style: 'min-width:0' },
        h('div', { class: 'tt' }, t.title, badge ? h('span', { class: 'badge', text: badge }) : null), h('div', { class: 'ta', text: t.artist })),
        h('button', { id: 'bar-like', text: isLiked(t) ? '♥' : '♡', 'aria-label': 'Me gusta', onclick: () => toggleLike(current()) }));
      document.title = `${t.title} · ${t.artist} — Sonora`;
    }
    const likeBtn = $('#bar-like');
    if (likeBtn && t) { likeBtn.textContent = isLiked(t) ? '♥' : '♡'; likeBtn.style.color = isLiked(t) ? 'var(--green)' : ''; }
    const active = player.state === 'playing' || player.state === 'loading';
    bar.play.textContent = player.state === 'loading' ? '…' : active ? '⏸' : '▶';
    bar.play.setAttribute('aria-label', active ? 'Pausa' : 'Reproducir');
    bar.shuffle.classList.toggle('on', player.shuffle);
    bar.repeat.classList.toggle('on', player.repeat > 0);
    bar.repeat.textContent = player.repeat === 2 ? '🔂' : '🔁';
    bar.speed.textContent = (player.speed === Math.floor(player.speed) ? player.speed : player.speed) + '×';
    bar.speed.classList.toggle('on', player.speed !== 1);
    const ep = t && t.kind === 'episode';
    bar.back.hidden = !ep;
    bar.fwd.hidden = !ep;
    bar.sleep.classList.toggle('on', !!(player.sleepEndsAt || player.sleepAtEnd));
    bar.lyrics.classList.toggle('on', sideOpen && sideTab === 'lyrics');
    bar.queue.classList.toggle('on', sideOpen && sideTab === 'queue');
    bar.vol.value = Math.round(player.volume * 100);
    bar.mute.textContent = audio.muted || player.volume === 0 ? '🔇' : '🔊';
    uiTick();
    if (sideOpen) renderSide();
    markPlayingRows();
  }

  function markPlayingRows() {
    const t = current();
    document.querySelectorAll('.trow').forEach((r) => {
      const on = !!t && r.dataset.key === t.key;
      r.classList.toggle('playing', on);
      const idx = $('.idx', r);
      if (idx && on) idx.textContent = '♫';
    });
  }

  function uiTick() {
    const t = current();
    const live = t && t.kind === 'radio';
    const cur = audio.currentTime * 1000 || (audio.getAttribute('src') ? 0 : player.pendingSeek);
    const dur = audio.duration && isFinite(audio.duration) ? audio.duration * 1000 : t ? t.dur : 0;
    if (!dragging) {
      bar.seek.value = live ? 1000 : dur > 0 ? Math.round((1000 * cur) / dur) : 0;
      bar.now.textContent = fmtTime(cur);
    }
    bar.total.textContent = live ? 'EN VIVO' : fmtTime(dur);
    bar.seek.disabled = !!live;
    bar.sleep.textContent = player.sleepEndsAt ? '⏲ ' + fmtTime(Math.max(0, player.sleepEndsAt - Date.now())) : player.sleepAtEnd ? '⏲ fin' : '⏲';
    if (sideOpen && sideTab === 'lyrics') updateLyricsHighlight(cur);
  }
  setInterval(() => { if (player.sleepEndsAt) uiTick(); }, 1000);

  bar.seek.addEventListener('input', () => { dragging = true; const d = audio.duration ? audio.duration * 1000 : (current() || {}).dur || 0; bar.now.textContent = fmtTime((bar.seek.value / 1000) * d); });
  bar.seek.addEventListener('change', () => { dragging = false; const d = audio.duration ? audio.duration * 1000 : (current() || {}).dur || 0; api.seek((bar.seek.value / 1000) * d); });
  bar.play.addEventListener('click', () => api.toggle());
  $('#b-next').addEventListener('click', () => api.next());
  $('#b-prev').addEventListener('click', () => api.prev());
  bar.shuffle.addEventListener('click', () => api.toggleShuffle());
  bar.repeat.addEventListener('click', () => api.cycleRepeat());
  bar.back.addEventListener('click', () => api.skip(-15000));
  bar.fwd.addEventListener('click', () => api.skip(30000));
  bar.vol.addEventListener('input', () => { audio.muted = false; api.setVolume(bar.vol.value / 100); updateBar(); });
  bar.mute.addEventListener('click', () => { audio.muted = !audio.muted; updateBar(); });
  bar.speed.addEventListener('click', () => {
    const t = current();
    if (t && t.kind === 'radio') { toast('La radio en vivo no admite cambiar la velocidad'); return; }
    choose('Velocidad de reproducción', [0.5, 0.75, 1, 1.25, 1.5, 2].map((s) => [s + '×' + (s === 1 ? ' (normal)' : ''), s]), (v) => api.setSpeed(v));
  });
  bar.sleep.addEventListener('click', () => choose('Temporizador de apagado',
    [['Desactivado', 0], ['5 minutos', 5], ['10 minutos', 10], ['15 minutos', 15], ['30 minutos', 30], ['45 minutos', 45], ['1 hora', 60], ['Al terminar esta canción', -1]],
    (v) => { if (v === 0) api.cancelSleep(); else if (v < 0) { api.sleepEnd(); toast('Se detendrá al terminar esta canción'); } else { api.setSleep(v); toast(`Se detendrá en ${v} min`); } }));

  let sideOpen = false;
  let sideTab = 'queue';
  function openSide(tab) {
    if (sideOpen && sideTab === tab) { closeSide(); return; }
    sideOpen = true;
    sideTab = tab;
    $('#side').hidden = false;
    $('#app').classList.add('with-side');
    document.querySelectorAll('.side-tabs [data-tab]').forEach((b) => b.classList.toggle('on', b.dataset.tab === tab));
    updateBar();
  }
  function closeSide() {
    sideOpen = false;
    $('#side').hidden = true;
    $('#app').classList.remove('with-side');
    updateBar();
  }
  bar.lyrics.addEventListener('click', () => openSide('lyrics'));
  bar.queue.addEventListener('click', () => openSide('queue'));
  $('#side-close').addEventListener('click', closeSide);
  document.querySelectorAll('.side-tabs [data-tab]').forEach((b) => b.addEventListener('click', () => {
    sideTab = b.dataset.tab;
    document.querySelectorAll('.side-tabs [data-tab]').forEach((x) => x.classList.toggle('on', x === b));
    updateBar();
  }));

  let lyricsState = { key: null, res: null, error: false, loading: false };
  let litLine = -2;
  function renderSide() {
    const body = $('#side-body');
    if (sideTab === 'queue') { renderQueue(body); return; }
    const t = current();
    if (!t) { body.replaceChildren(h('div', { class: 'empty', text: 'No hay nada sonando.' })); return; }
    if (lyricsState.key !== t.key) {
      lyricsState = { key: t.key, res: null, error: false, loading: true };
      const key = t.key;
      fetchLyrics(t).then((r) => { if (lyricsState.key === key) { lyricsState = { key, res: r, error: false, loading: false }; renderSide(); } })
        .catch(() => { if (lyricsState.key === key) { lyricsState = { key, res: null, error: true, loading: false }; renderSide(); } });
    }
    const L = lyricsState;
    litLine = -2;
    if (L.loading) { body.replaceChildren(h('div', { class: 'empty', text: 'Buscando letra…' })); return; }
    if (L.error) { body.replaceChildren(h('div', { class: 'empty' }, 'No se pudo cargar la letra. ', h('a', { class: 'link', href: '#', text: 'Reintentar', onclick: (e) => { e.preventDefault(); lyricsState = { key: null }; renderSide(); } }))); return; }
    const r = L.res;
    if (r.none) { body.replaceChildren(h('div', { class: 'empty', text: r.kind === 'radio' ? 'La radio en vivo no tiene letra.' : 'Los podcasts no tienen letra.' })); return; }
    if (r.instrumental && !r.synced.length && !r.plain) { body.replaceChildren(h('div', { class: 'empty', text: 'Instrumental ♪' })); return; }
    if (r.synced.length) {
      body.replaceChildren(...r.synced.map((l, i) => h('div', { class: 'ly', 'data-i': i, text: l.text || '♪', onclick: () => api.seek(l.ms) })));
      updateLyricsHighlight(audio.currentTime * 1000);
    } else if (r.plain) body.replaceChildren(h('div', { class: 'plain-lyrics', text: r.plain }));
    else body.replaceChildren(h('div', { class: 'empty', text: 'No encontramos la letra de esta canción' }));
  }
  function updateLyricsHighlight(posMs) {
    const r = lyricsState.res;
    if (!r || !r.synced || !r.synced.length) return;
    const now = currentLine(r.synced, posMs);
    if (now === litLine) return;
    litLine = now;
    const lines = document.querySelectorAll('#side-body .ly');
    lines.forEach((el, i) => { el.classList.toggle('on', i === now); el.classList.toggle('past', i < now); });
    if (now >= 0 && lines[now]) lines[now].scrollIntoView({ block: 'center', behavior: 'smooth' });
  }

  function renderQueue(body) {
    const all = queueTracks();
    if (player.pos < 0 || !all.length) { body.replaceChildren(h('div', { class: 'empty', text: 'No hay nada en la cola.' })); return; }
    const mk = (t, i) => h('div', { class: 'qrow' + (i === player.pos ? ' now' : ''), onclick: () => api.jumpTo(i) }, cover(t),
      h('div', { style: 'min-width:0' }, h('div', { class: 'tt', text: t.title }), h('div', { class: 'ta sub', text: t.artist })),
      h('button', { class: 'x', text: '✕', 'aria-label': 'Quitar de la cola', onclick: (e) => { e.stopPropagation(); api.removeAt(i); } }));
    const parts = [h('div', { class: 'side-h', text: 'Reproduciendo ahora' }), mk(all[player.pos], player.pos)];
    if (player.pos + 1 < all.length) {
      parts.push(h('div', { class: 'side-h', style: 'display:flex;justify-content:space-between' }, 'A continuación',
        h('a', { class: 'link', href: '#', text: 'Limpiar', onclick: (e) => { e.preventDefault(); api.clearUpcoming(); } })));
      for (let i = player.pos + 1; i < all.length; i++) parts.push(mk(all[i], i));
    }
    body.replaceChildren(...parts);
  }

  listenersP.add(updateBar);

  // ================================================================ atajos de teclado
  document.addEventListener('keydown', (e) => {
    const tag = (document.activeElement || {}).tagName;
    if (tag === 'INPUT' && document.activeElement.type !== 'range' || tag === 'TEXTAREA') {
      if (e.key === 'Escape') document.activeElement.blur();
      return;
    }
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    switch (e.key) {
      case ' ': e.preventDefault(); api.toggle(); break;
      case 'ArrowRight': e.preventDefault(); e.shiftKey ? api.next() : api.skip(5000); break;
      case 'ArrowLeft': e.preventDefault(); e.shiftKey ? api.prev() : api.skip(-5000); break;
      case 'ArrowUp': e.preventDefault(); api.setVolume(Math.min(1, player.volume + 0.05)); updateBar(); break;
      case 'ArrowDown': e.preventDefault(); api.setVolume(Math.max(0, player.volume - 0.05)); updateBar(); break;
      case 'm': case 'M': audio.muted = !audio.muted; updateBar(); break;
      case 'l': case 'L': openSide('lyrics'); break;
      case 'q': case 'Q': openSide('queue'); break;
      case 's': case 'S': api.toggleShuffle(); break;
      case 'r': case 'R': api.cycleRepeat(); break;
      case 'f': case 'F': if (current()) toggleLike(current()); break;
      case '/': e.preventDefault(); location.hash = '#/search'; setTimeout(() => { const i = $('#q'); if (i) i.focus(); }, 50); break;
      default: break;
    }
  });

  // ================================================================ arranque

  $('#navlinks').replaceChildren(
    ...[['home', '#/', '🏠 Inicio'], ['search', '#/search', '🔍 Buscar'], ['podcasts', '#/podcasts', '🎙 Podcasts'], ['library', '#/library', '📚 Tu biblioteca']]
      .map(([r, href, label]) => h('a', { href, 'data-r': r, text: label })));
  window.addEventListener('hashchange', render);
  restoreSession();
  render();
  updateBar();
  // permite a las pruebas y a la consola inspeccionar el estado
  window.__sonora = { player, api, lib, SECTIONS, audio, parseEpisodes, parseLrc, currentLine };
})();
