//! Codificador PNG mínimo (RGBA 8 bits) con su propio deflate: LZ77 con tabla hash
//! y códigos Huffman fijos. Suficiente para iconos y sin dependencias.

/// CRC-32 (polinomio de PNG / zlib).
pub fn crc32(data: &[u8]) -> u32 {
    let mut table = [0u32; 256];
    for (n, slot) in table.iter_mut().enumerate() {
        let mut c = n as u32;
        for _ in 0..8 {
            c = if c & 1 != 0 { 0xEDB8_8320 ^ (c >> 1) } else { c >> 1 };
        }
        *slot = c;
    }
    let mut crc = 0xFFFF_FFFFu32;
    for &b in data {
        crc = table[((crc ^ b as u32) & 0xFF) as usize] ^ (crc >> 8);
    }
    crc ^ 0xFFFF_FFFF
}

pub fn adler32(data: &[u8]) -> u32 {
    let (mut a, mut b) = (1u32, 0u32);
    for chunk in data.chunks(5552) {
        for &x in chunk {
            a += x as u32;
            b += a;
        }
        a %= 65521;
        b %= 65521;
    }
    (b << 16) | a
}

/// Escritor de bits LSB-primero, como pide deflate.
struct Bits {
    out: Vec<u8>,
    acc: u64,
    n: u32,
}

impl Bits {
    fn new() -> Self {
        Bits { out: Vec::new(), acc: 0, n: 0 }
    }
    fn put(&mut self, value: u32, count: u32) {
        self.acc |= (value as u64) << self.n;
        self.n += count;
        while self.n >= 8 {
            self.out.push(self.acc as u8);
            self.acc >>= 8;
            self.n -= 8;
        }
    }
    /// Los códigos Huffman se escriben empezando por el bit más significativo.
    fn put_code(&mut self, code: u32, len: u32) {
        let mut rev = 0;
        for i in 0..len {
            rev |= ((code >> i) & 1) << (len - 1 - i);
        }
        self.put(rev, len);
    }
    fn finish(mut self) -> Vec<u8> {
        if self.n > 0 {
            self.out.push(self.acc as u8);
        }
        self.out
    }
}

const LEN_BASE: [u16; 29] = [3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258];
const LEN_EXTRA: [u8; 29] = [0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0];
const DIST_BASE: [u16; 30] = [1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577];
const DIST_EXTRA: [u8; 30] = [0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13];

fn put_literal(bits: &mut Bits, sym: u32) {
    // Tabla Huffman fija (RFC 1951, 3.2.6).
    match sym {
        0..=143 => bits.put_code(0x30 + sym, 8),
        144..=255 => bits.put_code(0x190 + (sym - 144), 9),
        256..=279 => bits.put_code(sym - 256, 7),
        _ => bits.put_code(0xC0 + (sym - 280), 8),
    }
}

fn put_match(bits: &mut Bits, len: usize, dist: usize) {
    let li = LEN_BASE.iter().rposition(|&b| b as usize <= len).unwrap();
    put_literal(bits, 257 + li as u32);
    bits.put((len - LEN_BASE[li] as usize) as u32, LEN_EXTRA[li] as u32);
    let di = DIST_BASE.iter().rposition(|&b| b as usize <= dist).unwrap();
    bits.put_code(di as u32, 5);
    bits.put((dist - DIST_BASE[di] as usize) as u32, DIST_EXTRA[di] as u32);
}

/// Comprime con deflate (un único bloque con Huffman fijo).
pub fn deflate(data: &[u8]) -> Vec<u8> {
    const WINDOW: usize = 32768;
    const HASH: usize = 1 << 15;
    const CHAIN: usize = 64;
    let mut head = vec![usize::MAX; HASH];
    let mut prev = vec![usize::MAX; data.len().max(1)];
    let hash = |i: usize| -> usize {
        ((data[i] as usize) << 10 ^ (data[i + 1] as usize) << 5 ^ data[i + 2] as usize) & (HASH - 1)
    };
    let mut bits = Bits::new();
    bits.put(1, 1); // último bloque
    bits.put(1, 2); // Huffman fijo
    let mut i = 0;
    let insert = |i: usize, head: &mut Vec<usize>, prev: &mut Vec<usize>| {
        if i + 2 < data.len() {
            let h = hash(i);
            prev[i] = head[h];
            head[h] = i;
        }
    };
    while i < data.len() {
        let mut best_len = 0;
        let mut best_dist = 0;
        if i + 2 < data.len() {
            let mut cand = head[hash(i)];
            let mut steps = 0;
            while cand != usize::MAX && i - cand <= WINDOW && steps < CHAIN {
                let max = (data.len() - i).min(258);
                let mut l = 0;
                while l < max && data[cand + l] == data[i + l] {
                    l += 1;
                }
                if l > best_len {
                    best_len = l;
                    best_dist = i - cand;
                    if l == max {
                        break;
                    }
                }
                cand = prev[cand];
                steps += 1;
            }
        }
        if best_len >= 3 {
            put_match(&mut bits, best_len, best_dist);
            for k in i..i + best_len {
                insert(k, &mut head, &mut prev);
            }
            i += best_len;
        } else {
            put_literal(&mut bits, data[i] as u32);
            insert(i, &mut head, &mut prev);
            i += 1;
        }
    }
    put_literal(&mut bits, 256);
    bits.finish()
}

/// Flujo zlib: cabecera + deflate + Adler-32.
pub fn zlib(data: &[u8]) -> Vec<u8> {
    let mut out = vec![0x78, 0x01];
    out.extend(deflate(data));
    out.extend(adler32(data).to_be_bytes());
    out
}

fn chunk(out: &mut Vec<u8>, kind: &[u8; 4], body: &[u8]) {
    out.extend((body.len() as u32).to_be_bytes());
    let mut crc_input = kind.to_vec();
    crc_input.extend_from_slice(body);
    out.extend_from_slice(kind);
    out.extend_from_slice(body);
    out.extend(crc32(&crc_input).to_be_bytes());
}

fn paeth(a: u8, b: u8, c: u8) -> u8 {
    let p = a as i16 + b as i16 - c as i16;
    let (pa, pb, pc) = ((p - a as i16).abs(), (p - b as i16).abs(), (p - c as i16).abs());
    if pa <= pb && pa <= pc {
        a
    } else if pb <= pc {
        b
    } else {
        c
    }
}

/// Codifica una imagen RGBA (fila a fila, sin premultiplicar) como PNG.
pub fn encode_rgba(width: u32, height: u32, rgba: &[u8]) -> Vec<u8> {
    assert_eq!(rgba.len(), (width * height * 4) as usize, "tamaño de imagen incorrecto");
    let stride = width as usize * 4;
    let mut raw = Vec::with_capacity((stride + 1) * height as usize);
    let zero = vec![0u8; stride];
    for y in 0..height as usize {
        let row = &rgba[y * stride..(y + 1) * stride];
        let up = if y == 0 { &zero[..] } else { &rgba[(y - 1) * stride..y * stride] };
        // Elige el filtro con menor suma de valores absolutos (heurística habitual).
        let mut best: Option<(u64, u8, Vec<u8>)> = None;
        for f in 0..5u8 {
            let mut line = Vec::with_capacity(stride);
            for x in 0..stride {
                let a = if x >= 4 { row[x - 4] } else { 0 };
                let b = up[x];
                let c = if x >= 4 { up[x - 4] } else { 0 };
                let pred = match f {
                    0 => 0,
                    1 => a,
                    2 => b,
                    3 => ((a as u16 + b as u16) / 2) as u8,
                    _ => paeth(a, b, c),
                };
                line.push(row[x].wrapping_sub(pred));
            }
            let score: u64 = line.iter().map(|&v| (v as i8).unsigned_abs() as u64).sum();
            if best.as_ref().map_or(true, |(s, _, _)| score < *s) {
                best = Some((score, f, line));
            }
        }
        let (_, f, line) = best.unwrap();
        raw.push(f);
        raw.extend(line);
    }
    let mut out = vec![0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A];
    let mut ihdr = Vec::new();
    ihdr.extend(width.to_be_bytes());
    ihdr.extend(height.to_be_bytes());
    ihdr.extend([8, 6, 0, 0, 0]); // 8 bits, RGBA, deflate, filtros estándar, sin entrelazado
    chunk(&mut out, b"IHDR", &ihdr);
    chunk(&mut out, b"IDAT", &zlib(&raw));
    chunk(&mut out, b"IEND", &[]);
    out
}

/// Archivo .ico de Windows con imágenes PNG embebidas (válido desde Windows Vista).
pub fn encode_ico(images: &[(u32, Vec<u8>)]) -> Vec<u8> {
    let mut out = vec![0, 0, 1, 0];
    out.extend((images.len() as u16).to_le_bytes());
    let mut offset = 6 + 16 * images.len() as u32;
    for (size, png) in images {
        let s = if *size >= 256 { 0 } else { *size as u8 };
        out.extend([s, s, 0, 0]);
        out.extend(1u16.to_le_bytes()); // planos
        out.extend(32u16.to_le_bytes()); // bits por píxel
        out.extend((png.len() as u32).to_le_bytes());
        out.extend(offset.to_le_bytes());
        offset += png.len() as u32;
    }
    for (_, png) in images {
        out.extend_from_slice(png);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Descompresor deflate de referencia (solo para las pruebas): bloques almacenados y Huffman fijo.
    fn inflate_fixed(data: &[u8]) -> Vec<u8> {
        let mut pos = 0usize;
        let mut bit = |n: u32, pos: &mut usize| -> u32 {
            let mut v = 0;
            for i in 0..n {
                let b = (data[*pos / 8] >> (*pos % 8)) & 1;
                v |= (b as u32) << i;
                *pos += 1;
            }
            v
        };
        let mut out = Vec::new();
        let last = bit(1, &mut pos);
        assert_eq!(last, 1);
        assert_eq!(bit(2, &mut pos), 1, "Huffman fijo");
        let read_code = |pos: &mut usize, bitf: &mut dyn FnMut(u32, &mut usize) -> u32| -> u32 {
            let mut code = 0u32;
            for len in 1..=9 {
                code = (code << 1) | bitf(1, pos);
                match len {
                    7 if code <= 0x17 => return 256 + code,
                    8 if (0x30..=0xBF).contains(&code) => return code - 0x30,
                    8 if (0xC0..=0xC7).contains(&code) => return 280 + code - 0xC0,
                    9 if (0x190..=0x1FF).contains(&code) => return 144 + code - 0x190,
                    _ => {}
                }
            }
            panic!("código inválido");
        };
        loop {
            let sym = read_code(&mut pos, &mut bit);
            if sym < 256 {
                out.push(sym as u8);
            } else if sym == 256 {
                break;
            } else {
                let li = (sym - 257) as usize;
                let len = LEN_BASE[li] as usize + bit(LEN_EXTRA[li] as u32, &mut pos) as usize;
                let mut di = 0u32;
                for _ in 0..5 {
                    di = (di << 1) | bit(1, &mut pos);
                }
                let dist = DIST_BASE[di as usize] as usize + bit(DIST_EXTRA[di as usize] as u32, &mut pos) as usize;
                for _ in 0..len {
                    let v = out[out.len() - dist];
                    out.push(v);
                }
            }
        }
        out
    }

    #[test]
    fn checksums_match_known_values() {
        assert_eq!(crc32(b"123456789"), 0xCBF4_3926);
        assert_eq!(crc32(b""), 0);
        assert_eq!(adler32(b"Wikipedia"), 0x11E6_0398);
        assert_eq!(adler32(b""), 1);
    }

    #[test]
    fn deflate_round_trips() {
        let mut samples: Vec<Vec<u8>> = vec![vec![], b"a".to_vec(), b"abcabcabcabcabcabc".to_vec(), vec![7u8; 70000]];
        let mut x = 12345u32;
        let noise: Vec<u8> = (0..50000).map(|_| { x = x.wrapping_mul(1103515245).wrapping_add(12345); (x >> 16) as u8 }).collect();
        samples.push(noise);
        let mut mixed = Vec::new();
        for i in 0..40000u32 {
            mixed.push(((i / 7) % 13) as u8 * 19);
        }
        samples.push(mixed);
        for s in samples {
            assert_eq!(inflate_fixed(&deflate(&s)), s, "ida y vuelta con {} bytes", s.len());
        }
    }

    #[test]
    fn repetitive_data_compresses() {
        let data = vec![42u8; 100_000];
        assert!(deflate(&data).len() < 1000);
    }

    #[test]
    fn png_has_valid_structure() {
        let png = encode_rgba(3, 2, &[255u8; 24]);
        assert_eq!(&png[..8], &[0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A]);
        assert_eq!(&png[12..16], b"IHDR");
        assert_eq!(u32::from_be_bytes(png[16..20].try_into().unwrap()), 3);
        assert_eq!(u32::from_be_bytes(png[20..24].try_into().unwrap()), 2);
        // cada chunk lleva un CRC correcto
        let mut p = 8;
        while p < png.len() {
            let len = u32::from_be_bytes(png[p..p + 4].try_into().unwrap()) as usize;
            let crc = u32::from_be_bytes(png[p + 8 + len..p + 12 + len].try_into().unwrap());
            assert_eq!(crc32(&png[p + 4..p + 8 + len]), crc);
            p += 12 + len;
        }
        assert_eq!(&png[png.len() - 8..png.len() - 4], b"IEND");
    }

    #[test]
    fn ico_directory_points_at_each_png() {
        let a = encode_rgba(1, 1, &[1, 2, 3, 4]);
        let b = encode_rgba(2, 2, &[9; 16]);
        let ico = encode_ico(&[(16, a.clone()), (256, b.clone())]);
        assert_eq!(&ico[..6], &[0, 0, 1, 0, 2, 0]);
        assert_eq!(ico[6], 16);
        assert_eq!(ico[22], 0, "256 se escribe como 0");
        let off_a = u32::from_le_bytes(ico[18..22].try_into().unwrap()) as usize;
        let off_b = u32::from_le_bytes(ico[34..38].try_into().unwrap()) as usize;
        assert_eq!(&ico[off_a..off_a + a.len()], &a[..]);
        assert_eq!(&ico[off_b..off_b + b.len()], &b[..]);
    }
}
