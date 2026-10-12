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
| Orbe de Restauración (clic derecho) | Devuelve los objetos y mobs que desaparecieron sin morir, sin ser recogidos y sin expirar |
| Antidote (golpear) | Un solo uso: si golpeas a `FaultierLocket0` le quita toda la armadura y todos los efectos. Contra otros jugadores no hace nada y no se gasta |

**Orbe — límites:** no puede saber qué hizo el mod de tu hermano, así que detecta "desapareció cerca de un jugador sin causa normal". Solo ve lo que ocurre en un radio de 48 bloques de algún jugador, con el addon ya activo; el registro se pierde al cerrar el mundo; los mobs vuelven sin datos extra (domesticación, armadura, vida) y los bloques borrados no se restauran. Un objeto borrado a menos de 5 bloques de un jugador se confunde con "recogido".

**El elegido:** `FaultierLocket0` está en la lista `NO_ELEGIDOS` de `BP/scripts/main.js`: los objetos no le obedecen y ve "no es el elegido". Para agregar más, edita la lista o usa `/tag <jugador> add fb_no_elegido`.

Regenerar texturas y zips: `python3 build.py`
