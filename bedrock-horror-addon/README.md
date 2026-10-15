# Horror Stalkers (Minecraft Bedrock addon)

Tres criaturas de terror que te acechan. / Three stalking horror creatures.

| Criatura | Dónde aparece | Comportamiento |
|---|---|---|
| El Acechador (`horror:stalker`) | Superficie, luz baja | Se congela mientras lo miras y corre cuando apartas la vista |
| La Bestia del Bosque (`horror:woodbeast`) | Bosques, de noche | Cazador rápido a cuatro patas |
| El Susurrante (`horror:whisperer`) | Cuevas oscuras | Semitransparente; su golpe causa ceguera |

Extras (script): latido del corazón que se acelera con la cercanía, y sonidos ambientales de noche y en cuevas.

## Instalar
Abre `dist/HorrorStalkers.mcaddon` en Minecraft Bedrock 1.21+ (o renombra el `.zip` a `.mcaddon`).
Crea un mundo y activa ambos packs (BP y RP). Usa los huevos de aparición o
`/summon horror:stalker`.

## Desarrollo
```
python3 tools/generate_assets.py   # regenera JSON, modelos, texturas
python3 tools/build.py             # crea dist/HorrorStalkers.mcaddon y .zip
python3 tools/validate.py          # revisa JSON y referencias BP<->RP
```
