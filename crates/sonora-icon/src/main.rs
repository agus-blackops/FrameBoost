//! sonora-icon: genera el icono de Sonora 1.7 para Android y para la versión de PC.
//!
//! Uso: `cargo run -p sonora-icon --release -- [RAÍZ_DEL_REPO]`
//! Escribe los PNG de Android (mipmaps clásicos y capas del icono adaptable),
//! los iconos de la versión de PC (PNG + favicon.ico) y la imagen de 512 px para tiendas.

mod png;
mod render;

use render::{render, Layer};
use std::fs;
use std::path::{Path, PathBuf};

const DENSITIES: [(&str, f32); 5] = [("mdpi", 1.0), ("hdpi", 1.5), ("xhdpi", 2.0), ("xxhdpi", 3.0), ("xxxhdpi", 4.0)];

fn write(path: &Path, bytes: &[u8]) -> std::io::Result<()> {
    if let Some(dir) = path.parent() {
        fs::create_dir_all(dir)?;
    }
    fs::write(path, bytes)?;
    println!("  {:>7} B  {}", bytes.len(), path.display());
    Ok(())
}

fn png(layer: Layer, size: u32) -> Vec<u8> {
    png::encode_rgba(size, size, &render(layer, size))
}

fn main() -> std::io::Result<()> {
    let root = std::env::args().nth(1).map(PathBuf::from).unwrap_or_else(|| PathBuf::from("."));
    let android = root.join("musicapp/res");
    let pc = root.join("sonora-pc/public");
    if !android.is_dir() || !pc.is_dir() {
        eprintln!("No encuentro musicapp/res y sonora-pc/public dentro de {}", root.display());
        std::process::exit(2);
    }
    println!("Android:");
    for (name, scale) in DENSITIES {
        let dir = android.join(format!("mipmap-{name}"));
        write(&dir.join("ic_launcher.png"), &png(Layer::Full, (48.0 * scale) as u32))?;
        let adaptive = (108.0 * scale) as u32;
        write(&dir.join("ic_launcher_background.png"), &png(Layer::Background, adaptive))?;
        write(&dir.join("ic_launcher_foreground.png"), &png(Layer::Foreground, adaptive))?;
        write(&dir.join("ic_launcher_monochrome.png"), &png(Layer::Monochrome, adaptive))?;
    }
    write(&root.join("musicapp/docs/icon-512.png"), &png(Layer::Full, 512))?;
    println!("PC:");
    write(&pc.join("icon-512.png"), &png(Layer::Full, 512))?;
    write(&pc.join("icon-192.png"), &png(Layer::Full, 192))?;
    let ico: Vec<(u32, Vec<u8>)> = [16, 24, 32, 48, 64, 256].iter().map(|&s| (s, png(Layer::Full, s))).collect();
    write(&pc.join("favicon.ico"), &png::encode_ico(&ico))?;
    Ok(())
}
