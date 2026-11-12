//! El icono de Sonora dibujado con campos de distancia con signo (SDF).
//! Todo se define en un cuadrado unidad [0,1]×[0,1] (y hacia abajo), así cualquier
//! tamaño sale nítido y con bordes suavizados sin supermuestreo.

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Layer {
    /// Icono completo: fondo "squircle" con degradado + glifo (iconos clásicos, PC, tienda).
    Full,
    /// Capa de fondo del icono adaptable de Android (cubre todo el lienzo).
    Background,
    /// Capa frontal del icono adaptable: solo el glifo, dentro de la zona segura.
    Foreground,
    /// Glifo blanco opaco para iconos temáticos (Android 13+).
    Monochrome,
}

type V = (f32, f32);

fn sub(a: V, b: V) -> V {
    (a.0 - b.0, a.1 - b.1)
}
fn len(a: V) -> f32 {
    (a.0 * a.0 + a.1 * a.1).sqrt()
}
fn smoothstep(e0: f32, e1: f32, x: f32) -> f32 {
    let t = ((x - e0) / (e1 - e0)).clamp(0.0, 1.0);
    t * t * (3.0 - 2.0 * t)
}
fn mix(a: [f32; 3], b: [f32; 3], t: f32) -> [f32; 3] {
    [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]
}
fn hex(c: u32) -> [f32; 3] {
    [((c >> 16) & 255) as f32 / 255.0, ((c >> 8) & 255) as f32 / 255.0, (c & 255) as f32 / 255.0]
}

/// Distancia a un segmento con extremos redondeados (cápsula).
fn capsule(p: V, a: V, b: V, r: f32) -> f32 {
    let pa = sub(p, a);
    let ba = sub(b, a);
    let h = ((pa.0 * ba.0 + pa.1 * ba.1) / (ba.0 * ba.0 + ba.1 * ba.1)).clamp(0.0, 1.0);
    len((pa.0 - ba.0 * h, pa.1 - ba.1 * h)) - r
}

/// Elipse girada (distancia aproximada, exacta en el borde, que es lo que importa).
fn ellipse(p: V, c: V, rx: f32, ry: f32, angle: f32) -> f32 {
    let (s, co) = angle.sin_cos();
    let d = sub(p, c);
    let x = d.0 * co + d.1 * s;
    let y = -d.0 * s + d.1 * co;
    let k = ((x / rx).powi(2) + (y / ry).powi(2)).sqrt();
    (k - 1.0) * rx.min(ry)
}

/// Arco de anillo centrado en `c` que apunta a la derecha (±`span` radianes), con puntas redondas.
fn arc(p: V, c: V, radius: f32, thick: f32, span: f32) -> f32 {
    let d = sub(p, c);
    let theta = d.1.atan2(d.0);
    if theta.abs() <= span {
        (len(d) - radius).abs() - thick
    } else {
        let end = (c.0 + radius * span.cos(), c.1 + radius * span.sin() * theta.signum());
        len(sub(p, end)) - thick
    }
}

/// Curva cuadrática como cadena de cápsulas.
fn bezier_stroke(p: V, p0: V, p1: V, p2: V, r: f32) -> f32 {
    let at = |t: f32| {
        let u = 1.0 - t;
        (u * u * p0.0 + 2.0 * u * t * p1.0 + t * t * p2.0, u * u * p0.1 + 2.0 * u * t * p1.1 + t * t * p2.1)
    };
    let mut d = f32::MAX;
    let n = 24;
    let mut prev = at(0.0);
    for i in 1..=n {
        let cur = at(i as f32 / n as f32);
        // el trazo se afina hacia la punta, como la bandera de una corchea
        let rr = r * (1.0 - 0.35 * (i as f32 / n as f32));
        d = d.min(capsule(p, prev, cur, rr));
        prev = cur;
    }
    d
}

/// Desplazamiento que centra visualmente el glifo dentro del cuadrado.
const SHIFT: V = (-0.045, -0.02);

/// El glifo: una corchea con dos ondas de sonido.
pub fn glyph(p: V) -> f32 {
    let p = sub(p, SHIFT);
    let head = ellipse(p, (0.392, 0.655), 0.108, 0.080, -0.38);
    let stem = capsule(p, (0.484, 0.300), (0.484, 0.640), 0.024);
    let flag = bezier_stroke(p, (0.484, 0.300), (0.585, 0.330), (0.600, 0.470), 0.026);
    let wave1 = arc(p, (0.505, 0.525), 0.185, 0.026, 0.62);
    let wave2 = arc(p, (0.505, 0.525), 0.285, 0.026, 0.70);
    head.min(stem).min(flag).min(wave1).min(wave2)
}

/// Borde del "squircle" (superelipse n = 4.6) del icono completo.
fn squircle(p: V) -> f32 {
    let n = 4.6f32;
    let r = 0.465f32;
    let x = (p.0 - 0.5).abs() / r;
    let y = (p.1 - 0.5).abs() / r;
    ((x.powf(n) + y.powf(n)).powf(1.0 / n) - 1.0) * r
}

const GREEN_LIGHT: u32 = 0x22E06B;
const GREEN_DARK: u32 = 0x0A5C37;

/// Degradado diagonal con un brillo suave arriba a la izquierda y ondas tenues de fondo.
fn background_color(p: V) -> [f32; 3] {
    let t = ((p.0 + p.1) * 0.5).clamp(0.0, 1.0);
    let mut c = mix(hex(GREEN_LIGHT), hex(GREEN_DARK), smoothstep(0.0, 1.0, t));
    let glow = (1.0 - len(sub(p, (0.22, 0.18))) / 0.75).max(0.0);
    c = mix(c, [1.0, 1.0, 1.0], 0.16 * glow * glow);
    // anillos concéntricos muy tenues que continúan las ondas del glifo
    let d = len(sub(p, (0.505 + SHIFT.0, 0.525 + SHIFT.1)));
    let ring = (((d - 0.385) / 0.10).fract() - 0.5).abs();
    if d > 0.36 {
        c = mix(c, [1.0, 1.0, 1.0], 0.035 * smoothstep(0.42, 0.5, ring) * (1.0 - smoothstep(0.55, 0.75, d)));
    }
    c
}

/// Mezcla `src` (con opacidad `a`) sobre `dst` (RGBA no premultiplicado).
fn over(dst: &mut [f32; 4], src: [f32; 3], a: f32) {
    if a <= 0.0 {
        return;
    }
    let out_a = a + dst[3] * (1.0 - a);
    for i in 0..3 {
        dst[i] = if out_a > 0.0 { (src[i] * a + dst[i] * dst[3] * (1.0 - a)) / out_a } else { 0.0 };
    }
    dst[3] = out_a;
}

/// Píxel de la capa en el punto `q` del lienzo; `px` es el tamaño de un píxel en unidades del lienzo.
fn shade(layer: Layer, q: V, px: f32) -> [f32; 4] {
    let cov = |d: f32| (0.5 - d / px).clamp(0.0, 1.0);
    // en las capas adaptables el glifo se reduce a la zona visible (72 de 108 dp)
    let (g, gpx) = match layer {
        Layer::Foreground | Layer::Monochrome => {
            let k = 108.0 / 72.0;
            ((0.5 + (q.0 - 0.5) * k, 0.5 + (q.1 - 0.5) * k), px * k)
        }
        _ => (q, px),
    };
    let gcov = |d: f32| (0.5 - d / gpx).clamp(0.0, 1.0);
    let mut c = [0.0f32; 4];
    match layer {
        Layer::Background => {
            c = { let b = background_color(q); [b[0], b[1], b[2], 1.0] };
        }
        Layer::Full => {
            let a = cov(squircle(q));
            over(&mut c, background_color(q), a);
            let shadow = 0.30 * (1.0 - smoothstep(0.0, 0.04, glyph((g.0 - 0.006, g.1 - 0.014)))) * a;
            over(&mut c, hex(0x03301C), shadow);
            over(&mut c, [1.0, 1.0, 1.0], gcov(glyph(g)) * a);
        }
        Layer::Foreground => {
            let shadow = 0.30 * (1.0 - smoothstep(0.0, 0.04, glyph((g.0 - 0.006, g.1 - 0.014))));
            over(&mut c, hex(0x03301C), shadow);
            over(&mut c, [1.0, 1.0, 1.0], gcov(glyph(g)));
        }
        Layer::Monochrome => over(&mut c, [1.0, 1.0, 1.0], gcov(glyph(g))),
    }
    c
}

/// Dibuja la capa a `size`×`size` píxeles y devuelve RGBA de 8 bits.
pub fn render(layer: Layer, size: u32) -> Vec<u8> {
    let px = 1.0 / size as f32;
    let mut out = Vec::with_capacity((size * size * 4) as usize);
    for y in 0..size {
        for x in 0..size {
            let q = ((x as f32 + 0.5) * px, (y as f32 + 0.5) * px);
            let c = shade(layer, q, px);
            for v in &c[..3] {
                out.push((v.clamp(0.0, 1.0) * 255.0 + 0.5) as u8);
            }
            out.push((c[3].clamp(0.0, 1.0) * 255.0 + 0.5) as u8);
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn at(img: &[u8], size: u32, x: u32, y: u32) -> [u8; 4] {
        let i = ((y * size + x) * 4) as usize;
        [img[i], img[i + 1], img[i + 2], img[i + 3]]
    }

    #[test]
    fn full_icon_has_transparent_corners_and_white_glyph() {
        let s = 192;
        let img = render(Layer::Full, s);
        assert_eq!(at(&img, s, 0, 0)[3], 0, "esquina transparente (forma de squircle)");
        assert_eq!(at(&img, s, s - 1, s - 1)[3], 0);
        let mid_edge = at(&img, s, s / 2, 12);
        assert_eq!(mid_edge[3], 255, "borde superior centrado: dentro del fondo");
        // centro de la cabeza de la nota: blanco
        let hx = ((0.392 + SHIFT.0) * s as f32) as u32;
        let hy = ((0.655 + SHIFT.1) * s as f32) as u32;
        assert_eq!(at(&img, s, hx, hy), [255, 255, 255, 255]);
    }

    #[test]
    fn adaptive_layers_follow_android_rules() {
        let s = 216;
        let bg = render(Layer::Background, s);
        assert!(bg.chunks(4).all(|p| p[3] == 255), "el fondo es opaco en todo el lienzo");
        let fg = render(Layer::Foreground, s);
        // fuera del círculo de la zona segura (66 dp de 108) solo puede haber transparencia
        let safe = 33.0 / 108.0 * s as f32;
        for y in 0..s {
            for x in 0..s {
                let d = ((x as f32 + 0.5 - s as f32 / 2.0).powi(2) + (y as f32 + 0.5 - s as f32 / 2.0).powi(2)).sqrt();
                if d > safe {
                    assert!(at(&fg, s, x, y)[3] < 8, "píxel visible fuera de la zona segura en ({x},{y})");
                }
            }
        }
        let mono = render(Layer::Monochrome, s);
        assert!(mono.chunks(4).any(|p| p[3] == 255));
        assert!(mono.chunks(4).all(|p| p[3] == 0 || (p[0] == 255 && p[1] == 255 && p[2] == 255)), "monocromo: solo blanco");
    }

    #[test]
    fn rendering_is_deterministic_and_scales() {
        assert_eq!(render(Layer::Full, 48), render(Layer::Full, 48));
        // el glifo ocupa una fracción parecida a cualquier tamaño
        let white = |s: u32| render(Layer::Full, s).chunks(4).filter(|p| p[0] > 240 && p[1] > 240 && p[3] > 240).count() as f32 / (s * s) as f32;
        let (a, b) = (white(96), white(384));
        assert!((a - b).abs() < 0.02, "proporción de glifo {a} vs {b}");
        assert!(a > 0.06 && a < 0.25, "el glifo no es ni diminuto ni enorme: {a}");
    }
}
