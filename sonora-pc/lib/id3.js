'use strict';
/** Lector mínimo de etiquetas ID3v2 (título, artista, álbum y portada) para archivos MP3. */
const fs = require('fs');

function synchsafe(b, o) {
  return ((b[o] & 0x7f) << 21) | ((b[o + 1] & 0x7f) << 14) | ((b[o + 2] & 0x7f) << 7) | (b[o + 3] & 0x7f);
}

function decode(buf, enc) {
  if (!buf.length) return '';
  let s;
  if (enc === 1) { // UTF-16 con BOM
    if (buf[0] === 0xff && buf[1] === 0xfe) s = buf.subarray(2).toString('utf16le');
    else if (buf[0] === 0xfe && buf[1] === 0xff) s = Buffer.from(buf.subarray(2)).swap16().toString('utf16le');
    else s = buf.toString('utf16le');
  } else if (enc === 2) {
    s = Buffer.from(buf).swap16().toString('utf16le');
  } else if (enc === 3) {
    s = buf.toString('utf8');
  } else {
    s = buf.toString('latin1');
  }
  return s.replace(/\0+$/g, '').split('\0')[0].trim();
}

/** Posición del terminador nulo según la codificación (2 bytes en UTF-16). */
function nullAt(buf, from, enc) {
  if (enc === 1 || enc === 2) {
    for (let i = from; i + 1 < buf.length; i += 2) if (buf[i] === 0 && buf[i + 1] === 0) return i;
    return -1;
  }
  return buf.indexOf(0, from);
}

function readTags(file) {
  const fd = fs.openSync(file, 'r');
  try {
    const head = Buffer.alloc(10);
    if (fs.readSync(fd, head, 0, 10, 0) < 10 || head.toString('latin1', 0, 3) !== 'ID3') return {};
    const version = head[3];
    const size = synchsafe(head, 6);
    if (size <= 0 || size > 12 * 1024 * 1024) return {};
    const tag = Buffer.alloc(size);
    fs.readSync(fd, tag, 0, size, 10);
    const out = {};
    let pos = 0;
    if (head[5] & 0x40) { // cabecera extendida
      pos = version === 4 ? synchsafe(tag, 0) : tag.readUInt32BE(0) + 4;
    }
    while (pos + 10 <= tag.length) {
      const id = tag.toString('latin1', pos, pos + 4);
      if (!/^[A-Z0-9]{4}$/.test(id)) break;
      const fsize = version === 4 ? synchsafe(tag, pos + 4) : tag.readUInt32BE(pos + 4);
      const start = pos + 10;
      if (fsize <= 0 || start + fsize > tag.length) break;
      const data = tag.subarray(start, start + fsize);
      if (id === 'TIT2') out.title = decode(data.subarray(1), data[0]);
      else if (id === 'TPE1') out.artist = decode(data.subarray(1), data[0]);
      else if (id === 'TALB') out.album = decode(data.subarray(1), data[0]);
      else if (id === 'APIC' && !out.cover) {
        const enc = data[0];
        const mimeEnd = data.indexOf(0, 1);
        if (mimeEnd > 0) {
          let mime = data.toString('latin1', 1, mimeEnd);
          if (!mime.includes('/')) mime = 'image/' + mime.toLowerCase();
          const descEnd = nullAt(data, mimeEnd + 2, enc);
          if (descEnd >= 0) {
            const skip = descEnd + (enc === 1 || enc === 2 ? 2 : 1);
            out.cover = { mime, data: Buffer.from(data.subarray(skip)) };
          }
        }
      }
      pos = start + fsize;
    }
    return out;
  } finally {
    fs.closeSync(fd);
  }
}

module.exports = { readTags };
