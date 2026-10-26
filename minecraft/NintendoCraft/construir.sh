#!/usr/bin/env bash
# Empaqueta los dos packs en dist/NintendoCraft.mcaddon (un zip con otro nombre).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf dist && mkdir -p dist
zip -qr dist/NintendoCraft.mcaddon NintendoCraft_BP NintendoCraft_RP -x '*.DS_Store'
echo "Listo: dist/NintendoCraft.mcaddon"
