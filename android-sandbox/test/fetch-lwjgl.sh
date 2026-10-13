#!/usr/bin/env bash
# Downloads LWJGL (EGL + OpenGL ES bindings) so the GPU engine can be tested headless on Mesa (llvmpipe).
# Also needs: apt-get install libegl1 libgles2 libegl-mesa0 libgl1-mesa-dri
set -euo pipefail
cd "$(dirname "$0")" && mkdir -p lib
for f in lwjgl/3.3.4/lwjgl-3.3.4.jar lwjgl/3.3.4/lwjgl-3.3.4-natives-linux.jar lwjgl-egl/3.3.4/lwjgl-egl-3.3.4.jar \
         lwjgl-opengles/3.3.4/lwjgl-opengles-3.3.4.jar lwjgl-opengles/3.3.4/lwjgl-opengles-3.3.4-natives-linux.jar; do
  n=$(basename "$f"); [ -f "lib/$n" ] && continue
  for t in 1 2 3 4 5; do curl -fsS -o "lib/$n" "https://repo.maven.apache.org/maven2/org/lwjgl/$f" && break; sleep $((t * 4)); done
done
