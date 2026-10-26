import { GameMode, system, world } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";
import { ID, contarItem, darItem, quitarItem } from "./util.js";

const DLC_NETHER = "nc:dlc_nether";
const PRECIO_NETHER = 30;

const PRODUCTOS = [
  { item: ID.superChampinon, nombre: "Súper Champiñón", precio: 10, icono: "textures/items/nc_super_champinon" },
  { item: ID.florFuego, nombre: "Flor de Fuego", precio: 25, icono: "textures/items/nc_flor_fuego" },
  { item: ID.estrella, nombre: "Estrella de Invencibilidad", precio: 40, icono: "textures/items/nc_estrella" },
  { item: ID.champinonVida, nombre: "Champiñón 1-UP", precio: 50, icono: "textures/items/nc_champinon_vida" },
  { item: ID.bloquePregunta, nombre: "Bloque ?", precio: 15, icono: "textures/blocks/nc_bloque_pregunta" },
  { item: ID.tuberia, nombre: "Tubería", precio: 8, icono: "textures/blocks/nc_tuberia_lado" },
  { dlc: DLC_NETHER, nombre: "Expansión: El Nether", precio: PRECIO_NETHER, icono: "textures/blocks/netherrack" },
];

/** @param {import("@minecraft/server").Player} jugador */
export function abrirTienda(jugador) {
  const monedas = contarItem(jugador, ID.moneda);
  const formulario = new ActionFormData()
    .title("§l§cTienda Oficial")
    .body(`Tienes §6${monedas} monedas§r.\n§7Precios sin descuento. Como siempre.`);
  for (const p of PRODUCTOS) {
    const comprado = p.dlc && jugador.getDynamicProperty(p.dlc);
    formulario.button(`${p.nombre}\n${comprado ? "§2Ya es tuyo" : `§6${p.precio} monedas`}`, p.icono);
  }
  formulario.button("Salir");

  formulario.show(jugador).then((respuesta) => {
    if (respuesta.canceled || respuesta.selection === undefined) return;
    const producto = PRODUCTOS[respuesta.selection];
    if (!producto) return;
    if (producto.dlc && jugador.getDynamicProperty(producto.dlc)) {
      jugador.sendMessage("§7Ya compraste esta expansión. No se puede comprar dos veces (todavía).");
      return;
    }
    if (!quitarItem(jugador, ID.moneda, producto.precio)) {
      jugador.sendMessage(`§cNo te alcanza: cuesta §6${producto.precio} monedas§c. Nintendo no hace rebajas.`);
      jugador.playSound("note.bass");
      return;
    }
    if (producto.dlc) jugador.setDynamicProperty(producto.dlc, true);
    else if (producto.item) darItem(jugador, producto.item, 1);
    jugador.sendMessage(`§a¡Compraste ${producto.nombre}! §7Gracias por tu compra.`);
    jugador.playSound("random.orb", { pitch: 1.2 });
  });
}

/** El Nether ahora es contenido descargable: sin la expansión, te devuelven. */
export function iniciarTienda() {
  world.afterEvents.playerDimensionChange.subscribe((ev) => {
    const { player: jugador, toDimension, fromDimension, fromLocation } = ev;
    if (toDimension.id === "minecraft:the_end") {
      jugador.sendMessage("§7[Nintendo] El End es gratis por tiempo limitado. ¡Disfrútalo antes de que cobremos!");
      return;
    }
    if (toDimension.id !== "minecraft:nether" || jugador.getDynamicProperty(DLC_NETHER)) return;
    // En creativo el portal es instantáneo y te mandaría de vuelta sin parar.
    if (jugador.getGameMode() === GameMode.Creative) return;
    // Esperar a que termine de cargar el Nether antes de devolverlo.
    system.runTimeout(() => {
      if (jugador.isValid) jugador.teleport(fromLocation, { dimension: fromDimension });
    }, 10);
    jugador.onScreenDisplay.setTitle("§l§cCONTENIDO DESCARGABLE", {
      subtitle: `§fCompra "El Nether" en la Tienda por ${PRECIO_NETHER} monedas`,
      fadeInDuration: 5,
      stayDuration: 60,
      fadeOutDuration: 10,
    });
    jugador.sendMessage("§c[Nintendo] El Nether ahora es una expansión de pago. §7Sal del portal y búscala en el Catálogo.");
    jugador.playSound("note.bass");
  });
}
