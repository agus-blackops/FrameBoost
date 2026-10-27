import { ItemStack, system, world } from "@minecraft/server";
import { ID, azar, centro, darItem } from "./util.js";
import { mensajeBarra } from "./vidas.js";

// ---------------------------------------------------------------- Bloque ?

/** @typedef {{ monedas?: [number, number], item?: string }} Premio */

/** Premios del Bloque ?: [peso, premio]. @type {[number, Premio][]} */
const PREMIOS = [
  [60, { monedas: [1, 3] }],
  [5, { monedas: [8, 12] }],
  [15, { item: ID.superChampinon }],
  [10, { item: ID.florFuego }],
  [5, { item: ID.estrella }],
  [5, { item: ID.champinonVida }],
];

function sortearPremio() {
  const total = PREMIOS.reduce((s, [peso]) => s + peso, 0);
  let r = Math.random() * total;
  for (const [peso, premio] of PREMIOS) {
    r -= peso;
    if (r < 0) return premio;
  }
  return PREMIOS[0][1];
}

/**
 * Entrega el premio de un Bloque ? situado en `pos`.
 * @param {import("@minecraft/server").Dimension} dim
 * @param {import("@minecraft/server").Vector3} pos
 * @param {import("@minecraft/server").Player | undefined} jugador
 */
function soltarPremio(dim, pos, jugador) {
  const premio = sortearPremio();
  const c = centro(pos);
  if (premio.monedas) {
    const n = azar(premio.monedas[0], premio.monedas[1]);
    if (jugador) {
      darItem(jugador, ID.moneda, n);
      jugador.sendMessage(`§6+${n} moneda${n === 1 ? "" : "s"}`);
    } else {
      const pila = new ItemStack(ID.moneda, 1);
      pila.amount = n;
      dim.spawnItem(pila, c);
    }
    dim.playSound("random.orb", c, { pitch: 1.6 });
    dim.spawnParticle("minecraft:villager_happy", { x: c.x, y: c.y + 1, z: c.z });
    return;
  }
  // Los poderes salen por encima del bloque (o por debajo si arriba hay algo).
  const arriba = dim.getBlock({ x: pos.x, y: pos.y + 1, z: pos.z });
  const salida = arriba?.isAir ? { x: c.x, y: c.y + 1, z: c.z } : { x: c.x, y: c.y - 1, z: c.z };
  dim.spawnItem(new ItemStack(premio.item ?? ID.moneda, 1), salida);
  dim.playSound("random.pop", c, { pitch: 0.8 });
  dim.spawnParticle("minecraft:totem_particle", salida);
}

/**
 * Golpe a un Bloque ?: se convierte en Bloque Usado y suelta su premio.
 * @param {import("@minecraft/server").Block} bloque
 * @param {import("@minecraft/server").Player | undefined} jugador
 */
export function golpearBloque(bloque, jugador) {
  if (bloque.typeId !== ID.bloquePregunta) return;
  const { dimension, location } = bloque;
  bloque.setType(ID.bloqueUsado);
  dimension.playSound("random.wood_click", centro(location));
  soltarPremio(dimension, location, jugador);
}

/** Detecta cuando un jugador golpea un Bloque ? con la cabeza al saltar. */
function revisarCabezazos() {
  for (const jugador of world.getAllPlayers()) {
    if (jugador.getVelocity().y <= 0) continue;
    const { x, y, z } = jugador.location;
    try {
      const bloque = jugador.dimension.getBlock({ x: Math.floor(x), y: Math.floor(y + 1.8 + 0.45), z: Math.floor(z) });
      if (bloque?.typeId === ID.bloquePregunta) golpearBloque(bloque, jugador);
    } catch {
      // Fuera del mundo o en un chunk sin cargar.
    }
  }
}

/** "Nintendo puso Bloques ? por todas partes": aparecen en el cielo cerca de los jugadores. */
function generarBloquesEnElCielo() {
  for (const jugador of world.getAllPlayers()) {
    if (jugador.dimension.id !== "minecraft:overworld" || Math.random() > 0.35) continue;
    const angulo = Math.random() * Math.PI * 2;
    const distancia = azar(5, 12);
    const x = Math.floor(jugador.location.x + Math.cos(angulo) * distancia);
    const z = Math.floor(jugador.location.z + Math.sin(angulo) * distancia);
    try {
      const suelo = jugador.dimension.getTopmostBlock({ x, z });
      if (!suelo || suelo.isLiquid || suelo.typeId.includes("leaves")) continue;
      if (Math.abs(suelo.location.y - jugador.location.y) > 12) continue;
      const hueco = [1, 2, 3].map((dy) => suelo.offset({ x: 0, y: dy, z: 0 }));
      if (!hueco.every((b) => b?.isAir)) continue;
      // A 3 bloques del suelo: justo a la altura de un salto.
      const destino = hueco[2];
      if (!destino) continue;
      destino.setType(ID.bloquePregunta);
      jugador.dimension.spawnParticle("minecraft:totem_particle", centro(destino.location));
      jugador.dimension.playSound("random.pop", centro(destino.location), { pitch: 1.5 });
    } catch {
      // Chunk sin cargar: lo intentamos en la siguiente ronda.
    }
  }
}

// ---------------------------------------------------------------- Tuberías

const CLAVE_TUBERIAS = "nc:tuberias";
/** @type {Map<string, boolean>} jugadores que deben soltar "agacharse" antes de volver a viajar */
const esperandoSoltar = new Map();

/** @returns {{x: number, y: number, z: number, d: string}[]} */
function leerTuberias() {
  const datos = world.getDynamicProperty(CLAVE_TUBERIAS);
  if (typeof datos !== "string") return [];
  try {
    return JSON.parse(datos);
  } catch {
    return [];
  }
}

/** @param {{x: number, y: number, z: number, d: string}[]} lista */
function guardarTuberias(lista) {
  world.setDynamicProperty(CLAVE_TUBERIAS, JSON.stringify(lista));
}

/** @param {{x: number, y: number, z: number, d: string}} t */
const claveTuberia = (t) => `${t.d}:${t.x},${t.y},${t.z}`;

/** @param {import("@minecraft/server").Block} bloque */
function registrarTuberia(bloque) {
  const nueva = { ...bloque.location, d: bloque.dimension.id };
  const lista = leerTuberias();
  if (lista.some((t) => claveTuberia(t) === claveTuberia(nueva))) return;
  lista.push(nueva);
  guardarTuberias(lista);
}

/** @param {import("@minecraft/server").Vector3} pos @param {string} dim */
function quitarTuberia(pos, dim) {
  const clave = claveTuberia({ ...pos, d: dim });
  guardarTuberias(leerTuberias().filter((t) => claveTuberia(t) !== clave));
}

/**
 * Lleva al jugador a la siguiente tubería de la lista.
 * @param {import("@minecraft/server").Player} jugador
 * @param {import("@minecraft/server").Block} origen
 */
function viajarPorTuberia(jugador, origen) {
  let lista = leerTuberias();
  const claveOrigen = claveTuberia({ ...origen.location, d: origen.dimension.id });
  if (!lista.some((t) => claveTuberia(t) === claveOrigen)) {
    // Tubería colocada antes de que el mod la registrara (p. ej. con estructuras).
    registrarTuberia(origen);
    lista = leerTuberias();
  }
  const inicio = lista.findIndex((t) => claveTuberia(t) === claveOrigen);
  for (let paso = 1; paso < lista.length; paso++) {
    const destino = lista[(inicio + paso) % lista.length];
    const dimension = world.getDimension(destino.d);
    let bloque;
    try {
      bloque = dimension.getBlock(destino);
    } catch {
      bloque = undefined;
    }
    if (bloque && bloque.typeId !== ID.tuberia) {
      // La rompieron sin que nos enteráramos (explosión, comando...).
      quitarTuberia(destino, destino.d);
      continue;
    }
    dimension.playSound("mob.shulker.teleport", origen.location);
    jugador.teleport({ x: destino.x + 0.5, y: destino.y + 1, z: destino.z + 0.5 }, { dimension });
    system.runTimeout(() => {
      jugador.playSound("mob.shulker.teleport", { pitch: 0.7 });
      mensajeBarra(jugador, "§2¡Viaje por tubería completado!");
    }, 2);
    return;
  }
  jugador.sendMessage("§eNecesitas al menos §22 tuberías§e para viajar. ¡Coloca otra!");
  jugador.playSound("note.bass");
}

function revisarTuberias() {
  for (const jugador of world.getAllPlayers()) {
    if (!jugador.isSneaking) {
      esperandoSoltar.delete(jugador.id);
      continue;
    }
    if (esperandoSoltar.get(jugador.id) || !jugador.isOnGround) continue;
    const { x, y, z } = jugador.location;
    let bloque;
    try {
      bloque = jugador.dimension.getBlock({ x: Math.floor(x), y: Math.floor(y - 0.1), z: Math.floor(z) });
    } catch {
      continue;
    }
    if (bloque?.typeId !== ID.tuberia) continue;
    esperandoSoltar.set(jugador.id, true);
    viajarPorTuberia(jugador, bloque);
  }
}

// ----------------------------------------------------------------- Inicio

/** @param {import("@minecraft/server").StartupEvent} ev */
export function registrarComponentesDeBloques(ev) {
  ev.blockComponentRegistry.registerCustomComponent("nc:bloque_pregunta", {
    onPlayerInteract: (e) => golpearBloque(e.block, e.player),
    onPlayerBreak: (e) => soltarPremio(e.dimension, e.block.location, e.player),
  });
  ev.blockComponentRegistry.registerCustomComponent("nc:tuberia", {
    onPlace: (e) => {
      const bloque = e.block;
      system.run(() => registrarTuberia(bloque));
    },
    onPlayerBreak: (e) => {
      const pos = e.block.location;
      const dim = e.dimension.id;
      system.run(() => quitarTuberia(pos, dim));
    },
  });
}

export function iniciarBloques() {
  system.runInterval(revisarCabezazos, 1);
  system.runInterval(revisarTuberias, 4);
  system.runInterval(generarBloquesEnElCielo, 20 * 45);
}
