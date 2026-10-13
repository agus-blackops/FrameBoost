# Armador de Torre (CLP)

App web estática: eliges **PC Gamer**, **PC de trabajo** u **PC de oficina**, escribes tu
presupuesto en pesos chilenos y te recomienda una torre completa y compatible
(procesador, placa, RAM, GPU, SSD, fuente, gabinete y cooler si hace falta).

## Usar
Abre `index.html` en el navegador, o sirve la carpeta: `npx http-server pc-builder`.

## Cómo decide
`engine.js` prueba todas las combinaciones compatibles (socket, tipo de RAM, watts de la
fuente, gráficos integrados) que caben en el presupuesto y se queda con la de mejor puntaje.
Ofrece varias variantes (equilibrada, máximo FPS, a futuro, mejor precio).

## Precios
Los precios en `catalog.js` son **referenciales**. Edítalos con lo que veas en tiendas.
