# Poder Supremo (Minecraft Bedrock addon)

Instalar: abre `dist/PoderSupremo.mcaddon` en Minecraft Bedrock (o descomprime el `.zip` y copia `BP` y `RP` a `development_behavior_packs` / `development_resource_packs`).
Activa en el mundo **Experimentos → "Beta APIs"** (o "Holiday Creator Features") y ambos packs.

Kit completo: `/scriptevent fb:kit`

| Objeto | Qué hace |
|---|---|
| Mazo Aimbot | Aim assist al sostenerlo; **agachado = aimbot total**. Daño extra + smash al caer |
| Espada del Titán | +34 de daño extra |
| Hacha de Tormenta | +22 de daño y rayo |
| Corazón Divino (click derecho) | Regeneración X, Fuerza X, Resistencia, Absorción, Velocidad por 5 min |
| Núcleo Inmortal (en inventario) | Con ≤3 corazones te cura al máximo y da resistencia (cooldown 30 s) |

**El elegido:** `FaultierLocket0` está en la lista `NO_ELEGIDOS` de `BP/scripts/main.js`: los objetos no le obedecen y ve "no es el elegido". Para agregar más, edita la lista o usa `/tag <jugador> add fb_no_elegido`.

Regenerar texturas y zips: `python3 build.py`
