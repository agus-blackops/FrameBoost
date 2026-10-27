import { GameMode, system, world } from "@minecraft/server";
import { ActionFormData, MessageFormData } from "@minecraft/server-ui";
import { JUEGOS, agregarABiblioteca, biblioteca, cartucho } from "./juegos.js";
import { ID, contarItem, darItem, quitarItem, registrarPrueba } from "./util.js";

const DLC_NETHER = "nc:dlc_nether";
/** "Comisión por usar la tienda" que se suma a todo. */
const COMISION = 0.1;

const PODERES = [
  { item: ID.superChampinon, nombre: "Súper Champiñón", precio: 30, icono: "textures/items/nc_super_champinon" },
  { item: ID.florFuego, nombre: "Flor de Fuego", precio: 75, icono: "textures/items/nc_flor_fuego" },
  { item: ID.estrella, nombre: "Estrella de Invencibilidad", precio: 120, icono: "textures/items/nc_estrella" },
  { item: ID.champinonVida, nombre: "Champiñón 1-UP", precio: 150, icono: "textures/items/nc_champinon_vida" },
  { item: ID.bloquePregunta, nombre: "Bloque ?", precio: 45, icono: "textures/blocks/nc_bloque_pregunta" },
  { item: ID.tuberia, nombre: "Tubería", precio: 25, icono: "textures/blocks/nc_tuberia_lado" },
];

const EXPANSIONES = [
  { dlc: DLC_NETHER, nombre: "Expansión: El Nether", precio: 99, icono: "textures/blocks/netherrack" },
];

/** @param {number} precio */
const precioFinal = (precio) => Math.ceil(precio * (1 + COMISION));

/**
 * @param {import("@minecraft/server").Player} jugador
 * @param {string} nombre
 * @param {number} precio
 * @param {() => void} entregar
 * @param {() => void} volver
 */
function confirmarCompra(jugador, nombre, precio, entregar, volver) {
  const total = precioFinal(precio);
  new MessageFormData()
    .title("§l§cNintendo Shop")
    .body(
      `§f¿Comprar §e${nombre}§f?\n\n` +
        `§7Precio: §6${precio} monedas\n` +
        `§7Comisión por usar la tienda: §6${total - precio} monedas\n` +
        `§7Total: §6${total} monedas\n\n` +
        `§8Esta compra no es reembolsable. Ni lo pienses.`
    )
    .button1("Comprar")
    .button2("Volver")
    .show(jugador)
    .then((r) => {
      if (r.canceled) return;
      if (r.selection !== 0) return volver();
      if (!quitarItem(jugador, ID.moneda, total)) {
        jugador.sendMessage(`§cNo te alcanza: necesitas §6${total} monedas§c. Nintendo no hace rebajas.`);
        jugador.playSound("note.bass");
        return;
      }
      entregar();
      registrarPrueba(jugador, "comision");
      jugador.sendMessage(`§a¡Compraste ${nombre}! §7Gracias por tu compra. Te enviamos un correo que nadie va a leer.`);
      jugador.playSound("random.orb", { pitch: 1.2 });
    });
}

/**
 * Menú genérico: una lista de botones con "Volver" al final.
 * @param {import("@minecraft/server").Player} jugador
 * @param {string} cuerpo
 * @param {{ texto: string, icono?: string, elegir: () => void }[]} opciones
 */
function menu(jugador, cuerpo, opciones) {
  const formulario = new ActionFormData().title("§l§cNintendo Shop").body(cuerpo);
  for (const o of opciones) formulario.button(o.texto, o.icono);
  formulario.button("§8Volver");
  formulario.show(jugador).then((r) => {
    if (r.canceled || r.selection === undefined) return;
    const opcion = opciones[r.selection];
    if (opcion) opcion.elegir();
    else abrirTienda(jugador);
  });
}

/** @param {import("@minecraft/server").Player} jugador @param {"nintendo" | "basura"} tipo */
function menuJuegos(jugador, tipo) {
  const cuerpo =
    tipo === "nintendo"
      ? "§fJuegos de Nintendo. §cDescuento: -0%§f.\n§7Estos precios no han bajado desde 2017 y nunca lo harán."
      : "§fJuegos destacados por la comunidad.\n§7Nadie sabe cómo pasaron el control de calidad. Igual son caros.";
  const opciones = JUEGOS.filter((j) => j.tipo === tipo).map((juego) => ({
    texto: `${juego.nombre}\n§6${juego.precio} monedas`,
    icono: tipo === "nintendo" ? "textures/items/nc_juego_nintendo" : "textures/items/nc_juego_basura",
    elegir: () =>
      confirmarCompra(
        jugador,
        juego.nombre,
        juego.precio,
        () => {
          agregarABiblioteca(jugador, juego);
          const sobra = jugador.getComponent("minecraft:inventory")?.container?.addItem(cartucho(juego));
          if (sobra) jugador.dimension.spawnItem(sobra, jugador.location);
        },
        () => menuJuegos(jugador, tipo)
      ),
  }));
  menu(jugador, cuerpo, opciones);
}

/** @param {import("@minecraft/server").Player} jugador */
function menuPoderes(jugador) {
  menu(
    jugador,
    "§fPoderes y bloques oficiales.\n§7Ahora con precios premium.",
    PODERES.map((p) => ({
      texto: `${p.nombre}\n§6${p.precio} monedas`,
      icono: p.icono,
      elegir: () => confirmarCompra(jugador, p.nombre, p.precio, () => darItem(jugador, p.item, 1), () => menuPoderes(jugador)),
    }))
  );
}

/** @param {import("@minecraft/server").Player} jugador */
function menuExpansiones(jugador) {
  menu(
    jugador,
    "§fContenido descargable.\n§7Partes del juego que antes eran gratis.",
    EXPANSIONES.map((e) => {
      const comprado = Boolean(jugador.getDynamicProperty(e.dlc));
      return {
        texto: `${e.nombre}\n${comprado ? "§2Ya es tuyo" : `§6${e.precio} monedas`}`,
        icono: e.icono,
        elegir: () => {
          if (comprado) {
            jugador.sendMessage("§7Ya compraste esta expansión. No se puede comprar dos veces (todavía).");
            return;
          }
          confirmarCompra(jugador, e.nombre, e.precio, () => jugador.setDynamicProperty(e.dlc, true), () => menuExpansiones(jugador));
        },
      };
    })
  );
}

/** Mis juegos: se pueden volver a descargar gratis si perdiste el cartucho. */
function menuBiblioteca(jugador) {
  const mios = biblioteca(jugador)
    .map((id) => JUEGOS.find((j) => j.id === id))
    .filter((j) => j !== undefined);
  if (mios.length === 0) {
    menu(jugador, "§fTu biblioteca está vacía.\n§7Nintendo está muy decepcionado de ti.", []);
    return;
  }
  menu(
    jugador,
    "§fTus juegos. Si perdiste un cartucho, descárgalo otra vez gratis.\n§7(Es lo único gratis de la tienda.)",
    mios.map((juego) => ({
      texto: `${juego.nombre}\n§2Descargar otra vez`,
      icono: juego.tipo === "nintendo" ? "textures/items/nc_juego_nintendo" : "textures/items/nc_juego_basura",
      elegir: () => {
        const inv = jugador.getComponent("minecraft:inventory")?.container;
        if (inv) {
          for (let i = 0; i < inv.size; i++) {
            if (inv.getItem(i)?.getDynamicProperty("nc:juego") === juego.id) {
              jugador.sendMessage("§7Ya tienes ese cartucho. No intentes duplicar juegos, el abogado te está viendo.");
              return;
            }
          }
        }
        const sobra = inv?.addItem(cartucho(juego));
        if (sobra) jugador.dimension.spawnItem(sobra, jugador.location);
        jugador.sendMessage(`§aDescargaste otra vez ${juego.nombre}.`);
      },
    }))
  );
}

/** @param {import("@minecraft/server").Player} jugador */
export function abrirTienda(jugador) {
  const monedas = contarItem(jugador, ID.moneda);
  menu(
    jugador,
    `§fBienvenido a la §cNintendo Shop§f.\nTienes §6${monedas} monedas§f.\n§7Todos los precios llevan un 10% de comisión. Porque sí.`,
    [
      { texto: "§cJuegos de Nintendo\n§8Precio completo, siempre", icono: "textures/items/nc_juego_nintendo", elegir: () => menuJuegos(jugador, "nintendo") },
      { texto: "§7Juegos Destacados\n§8(Juegos basura)", icono: "textures/items/nc_juego_basura", elegir: () => menuJuegos(jugador, "basura") },
      { texto: "Poderes y bloques", icono: "textures/items/nc_estrella", elegir: () => menuPoderes(jugador) },
      { texto: "Expansiones (DLC)", icono: "textures/blocks/netherrack", elegir: () => menuExpansiones(jugador) },
      { texto: "Mi biblioteca", icono: "textures/items/nc_catalogo_tienda", elegir: () => menuBiblioteca(jugador) },
    ]
  );
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
    registrarPrueba(jugador, "nether");
    jugador.onScreenDisplay.setTitle("§l§cCONTENIDO DESCARGABLE", {
      subtitle: `§fCompra "El Nether" en la Nintendo Shop por ${EXPANSIONES[0].precio} monedas`,
      fadeInDuration: 5,
      stayDuration: 60,
      fadeOutDuration: 10,
    });
    jugador.sendMessage("§c[Nintendo] El Nether ahora es una expansión de pago. §7Sal del portal y búscala en la Nintendo Shop.");
    jugador.playSound("note.bass");
  });
}
