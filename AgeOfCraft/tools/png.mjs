// Minimal RGBA PNG writer and drawing helpers. Node >= 22.2 (zlib.crc32).
import { crc32, deflateSync } from "node:zlib";

export class Canvas {
  constructor(width, height) {
    this.width = width;
    this.height = height;
    this.data = new Uint8Array(width * height * 4);
  }

  set(x, y, color) {
    if (!color || x < 0 || y < 0 || x >= this.width || y >= this.height) return;
    const [r, g, b, a] = color;
    const i = (y * this.width + x) * 4;
    this.data[i] = r;
    this.data[i + 1] = g;
    this.data[i + 2] = b;
    this.data[i + 3] = a;
  }

  rect(x, y, w, h, colorAt) {
    for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, colorAt(i, j));
  }

  /**
   * Paints a Bedrock box-UV cube of size (w, h, d) whose UV origin is (u, v).
   * `colorAt(face, x, y, faceW, faceH)` is called per pixel; faces are
   * top, bottom, right, front, left, back (front is the north / -Z face).
   */
  box(u, v, w, h, d, colorAt) {
    const faces = [
      ["top", u + d, v, w, d],
      ["bottom", u + d + w, v, w, d],
      ["right", u, v + d, d, h],
      ["front", u + d, v + d, w, h],
      ["left", u + d + w, v + d, d, h],
      ["back", u + 2 * d + w, v + d, w, h],
    ];
    for (const [face, fx, fy, fw, fh] of faces) this.rect(fx, fy, fw, fh, (x, y) => colorAt(face, x, y, fw, fh));
  }

  toPng() {
    const raw = Buffer.alloc((this.width * 4 + 1) * this.height);
    for (let y = 0; y < this.height; y++) {
      raw[y * (this.width * 4 + 1)] = 0; // filter: none
      Buffer.from(this.data.buffer, y * this.width * 4, this.width * 4).copy(raw, y * (this.width * 4 + 1) + 1);
    }
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(this.width, 0);
    ihdr.writeUInt32BE(this.height, 4);
    ihdr[8] = 8; // bit depth
    ihdr[9] = 6; // RGBA
    return Buffer.concat([
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      chunk("IHDR", ihdr),
      chunk("IDAT", deflateSync(raw, { level: 9 })),
      chunk("IEND", Buffer.alloc(0)),
    ]);
  }
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body) >>> 0);
  return Buffer.concat([len, body, crc]);
}

export function hex(h, alpha = 255) {
  const n = parseInt(h.replace("#", ""), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255, alpha];
}

/** Brightens (amount > 0) or darkens (amount < 0) a colour by a fraction. */
export function shade(color, amount) {
  const [r, g, b, a] = color;
  const f = (c) => Math.max(0, Math.min(255, Math.round(amount >= 0 ? c + (255 - c) * amount : c * (1 + amount))));
  return [f(r), f(g), f(b), a];
}

/** Deterministic PRNG so regenerated textures are byte-identical. */
export function rng(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Adds light per-pixel variation to a base colour. */
export function grain(random, base, strength = 0.08) {
  return shade(base, (random() - 0.5) * 2 * strength);
}
