import { GameMode, ItemStack } from "@minecraft/server";

export const ID = {
  moneda: "nc:moneda",
  superChampinon: "nc:super_champinon",
  champinonVida: "nc:champinon_vida",
  florFuego: "nc:flor_fuego",
  estrella: "nc:estrella",
  catalogo: "nc:catalogo_tienda",
  juegoNintendo: "nc:juego_nintendo",
  juegoBasura: "nc:juego_basura",
  formularioDemanda: "nc:formulario_demanda",
  bloquePregunta: "nc:bloque_pregunta",
  bloqueUsado: "nc:bloque_usado",
  tuberia: "nc:tuberia",
  bolaFuego: "nc:bola_fuego",
  champiGrunon: "nc:champi_grunon",
};

/** @param {import("@minecraft/server").Player} jugador */
function inventario(jugador) {
  return jugador.getComponent("minecraft:inventory")?.container;
}

/**
 * @param {import("@minecraft/server").Player} jugador
 * @param {string} tipo
 */
export function contarItem(jugador, tipo) {
  const inv = inventario(jugador);
  if (!inv) return 0;
  let total = 0;
  for (let i = 0; i < inv.size; i++) {
    const item = inv.getItem(i);
    if (item?.typeId === tipo) total += item.amount;
  }
  return total;
}

/**
 * Quita `cantidad` objetos del inventario. No quita nada si no hay suficientes.
 * @param {import("@minecraft/server").Player} jugador
 * @param {string} tipo
 * @param {number} cantidad
 * @returns {boolean} si se pudieron quitar
 */
export function quitarItem(jugador, tipo, cantidad) {
  const inv = inventario(jugador);
  if (!inv || contarItem(jugador, tipo) < cantidad) return false;
  let falta = cantidad;
  for (let i = 0; i < inv.size && falta > 0; i++) {
    const item = inv.getItem(i);
    if (item?.typeId !== tipo) continue;
    const usar = Math.min(item.amount, falta);
    falta -= usar;
    if (usar === item.amount) {
      inv.setItem(i);
    } else {
      item.amount -= usar;
      inv.setItem(i, item);
    }
  }
  return true;
}

/**
 * Da objetos al jugador; lo que no quepa cae al suelo a sus pies.
 * @param {import("@minecraft/server").Player} jugador
 * @param {string} tipo
 * @param {number} cantidad
 */
export function darItem(jugador, tipo, cantidad = 1) {
  const inv = inventario(jugador);
  let falta = cantidad;
  while (falta > 0) {
    const pila = new ItemStack(tipo, 1);
    pila.amount = Math.min(falta, pila.maxAmount);
    falta -= pila.amount;
    const sobra = inv ? inv.addItem(pila) : pila;
    if (sobra) jugador.dimension.spawnItem(sobra, jugador.location);
  }
}

/** @param {import("@minecraft/server").Player} jugador */
export function esCreativo(jugador) {
  return jugador.getGameMode() === GameMode.Creative;
}

/** @param {number} min @param {number} max entero en [min, max] */
export function azar(min, max) {
  return min + Math.floor(Math.random() * (max - min + 1));
}

/** @param {import("@minecraft/server").Vector3} v */
export function centro(v) {
  return { x: Math.floor(v.x) + 0.5, y: Math.floor(v.y) + 0.5, z: Math.floor(v.z) + 0.5 };
}

/**
 * Guarda una "prueba" de algo que Nintendo le hizo al jugador, para usarla
 * cuando demande a Nintendo.
 * @param {import("@minecraft/server").Player} jugador
 * @param {string} clave
 */
export function registrarPrueba(jugador, clave) {
  const pruebas = leerPruebas(jugador);
  if (pruebas.includes(clave)) return;
  pruebas.push(clave);
  jugador.setDynamicProperty("nc:pruebas", JSON.stringify(pruebas));
  jugador.sendMessage("§8(Guardaste una prueba para demandar a Nintendo.)");
}

/** @param {import("@minecraft/server").Player} jugador @returns {string[]} */
export function leerPruebas(jugador) {
  const datos = jugador.getDynamicProperty("nc:pruebas");
  if (typeof datos !== "string") return [];
  try {
    return JSON.parse(datos);
  } catch {
    return [];
  }
}
