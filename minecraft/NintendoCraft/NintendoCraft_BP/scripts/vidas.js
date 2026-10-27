import { Player, system, world } from "@minecraft/server";
import { ID, contarItem, darItem, quitarItem } from "./util.js";

const VIDAS_INICIALES = 3;
const VIDAS_MAX = 99;
const MONEDAS_POR_VIDA = 100;

/** @param {Player} jugador */
export function getVidas(jugador) {
  const v = jugador.getDynamicProperty("nc:vidas");
  return typeof v === "number" ? v : VIDAS_INICIALES;
}

/** @param {Player} jugador @param {number} vidas */
function setVidas(jugador, vidas) {
  jugador.setDynamicProperty("nc:vidas", Math.max(0, Math.min(VIDAS_MAX, vidas)));
}

/** @param {Player} jugador */
export function ganarVida(jugador) {
  setVidas(jugador, getVidas(jugador) + 1);
  jugador.onScreenDisplay.setTitle("§a1-UP", {
    subtitle: `§fVidas x${getVidas(jugador)}`,
    fadeInDuration: 5,
    stayDuration: 30,
    fadeOutDuration: 10,
  });
  jugador.playSound("random.levelup", { pitch: 1.4 });
}

/** @param {Player} jugador */
function darBienvenida(jugador) {
  jugador.onScreenDisplay.setTitle("§l§c¡Nintendo compró Minecraft!", {
    subtitle: "§eBienvenido a NintendoCraft",
    fadeInDuration: 10,
    stayDuration: 80,
    fadeOutDuration: 20,
  });
  const lineas = [
    "§l§6=== Comunicado oficial ===",
    "§fA partir de hoy, Minecraft forma parte de la familia Nintendo. Cambios:",
    "§e• §fTus Minecoins ahora son §6Monedas§f. Cada 100 monedas = §a1 vida extra§f.",
    "§e• §fTienes §c3 vidas§f. Si las pierdes todas: §cGAME OVER§f.",
    "§e• §fHan aparecido §6Bloques ?§f en el cielo. ¡Golpéalos saltando desde abajo!",
    "§e• §fAgáchate sobre una §2Tubería§f para viajar a la siguiente.",
    "§e• §fPuedes aplastar monstruos saltando encima de ellos.",
    "§e• §fModo Familiar: los creepers ya no rompen bloques.",
    "§e• §fCompra juegos y poderes en la §cNintendo Shop§f (usa la Tarjeta). Todo es carísimo.",
    "§e• §fEl §cNether§f ahora es una expansión de pago (99 monedas en la Nintendo Shop).",
    "§e• §fNuestro §4departamento legal§f vigila tu mundo. No le pongas nombres registrados a nada.",
    "§7(Parodia no oficial. No afiliada con Nintendo ni con Mojang.)",
  ];
  for (const l of lineas) jugador.sendMessage(l);
  darItem(jugador, ID.catalogo, 1);
  darItem(jugador, ID.moneda, 10);
  darItem(jugador, ID.bloquePregunta, 2);
  darItem(jugador, ID.tuberia, 2);
  jugador.playSound("random.levelup");
}

const AVISOS = [
  "§7[Aviso Legal] Tu mundo ha sido revisado. No se encontraron infracciones... por ahora.",
  "§7[Noticias] El Ender Dragon saldrá como DLC de pago el próximo año.",
  "§7[Noticias] Steve ahora se llama oficialmente \"Steve Deluxe Edition\".",
  "§7[Aviso] Los mods ahora necesitan la aprobación de 47 departamentos.",
  "§7[Tienda] ¡Oferta especial! Todos los precios siguen igual que siempre.",
  "§7[Aviso] Recuerda: los bloques de tierra ahora son artículos de colección.",
  "§7[Noticias] Se anunció \"Minecraft Party\": 80 minijuegos de picar piedra.",
  "§7[Aviso] Hoy no te cobramos nada. Disfruta este momento histórico.",
];

/** Cada cierto tiempo llega un "comunicado oficial" de broma. */
function avisoOficial() {
  for (const jugador of world.getAllPlayers()) {
    if (Math.random() < 0.3 && quitarItem(jugador, ID.moneda, 5)) {
      jugador.sendMessage("§7[Nintendo Online] Cuota de suscripción del mes: §c-5 monedas§7. ¡Gracias!");
      jugador.playSound("note.bass");
    } else {
      jugador.sendMessage(AVISOS[Math.floor(Math.random() * AVISOS.length)]);
    }
  }
}

/** @type {Map<string, number>} jugador -> tick hasta el que no se dibuja el marcador */
const marcadorPausado = new Map();

/**
 * Muestra un mensaje en la barra de acción sin que el marcador lo tape enseguida.
 * @param {Player} jugador
 * @param {string} texto
 * @param {number} ticks
 */
export function mensajeBarra(jugador, texto, ticks = 40) {
  marcadorPausado.set(jugador.id, system.currentTick + ticks);
  jugador.onScreenDisplay.setActionBar(texto);
}

/** Muestra el marcador y convierte cada 100 monedas en una vida. */
function actualizarMarcador() {
  for (const jugador of world.getAllPlayers()) {
    if (contarItem(jugador, ID.moneda) >= MONEDAS_POR_VIDA && quitarItem(jugador, ID.moneda, MONEDAS_POR_VIDA)) {
      ganarVida(jugador);
    }
    if ((marcadorPausado.get(jugador.id) ?? 0) > system.currentTick) continue;
    const monedas = contarItem(jugador, ID.moneda);
    jugador.onScreenDisplay.setActionBar(`§6Monedas §fx${monedas}    §cVidas §fx${getVidas(jugador)}`);
  }
}

export function iniciarVidas() {
  world.afterEvents.entityDie.subscribe(
    (ev) => {
      const jugador = ev.deadEntity;
      if (!(jugador instanceof Player)) return;
      const vidas = getVidas(jugador) - 1;
      if (vidas <= 0) {
        jugador.setDynamicProperty("nc:game_over", true);
        setVidas(jugador, VIDAS_INICIALES);
      } else {
        setVidas(jugador, vidas);
      }
    },
    { entityTypes: ["minecraft:player"] }
  );

  world.afterEvents.playerSpawn.subscribe((ev) => {
    const jugador = ev.player;
    if (ev.initialSpawn) {
      if (!jugador.getDynamicProperty("nc:bienvenida")) {
        jugador.setDynamicProperty("nc:bienvenida", true);
        // Esperar un poco para que el jugador vea el título al cargar.
        system.runTimeout(() => darBienvenida(jugador), 60);
      }
      return;
    }
    if (jugador.getDynamicProperty("nc:game_over")) {
      jugador.setDynamicProperty("nc:game_over", undefined);
      jugador.onScreenDisplay.setTitle("§l§cGAME OVER", {
        subtitle: "§7Nintendo te regala 3 vidas nuevas. Solo por esta vez.",
        fadeInDuration: 10,
        stayDuration: 70,
        fadeOutDuration: 20,
      });
      jugador.playSound("mob.wither.death", { volume: 0.4 });
    } else {
      jugador.onScreenDisplay.setTitle(`§fx ${getVidas(jugador)}`, {
        subtitle: "§cVidas restantes",
        fadeInDuration: 5,
        stayDuration: 40,
        fadeOutDuration: 10,
      });
    }
  });

  // Modo Familiar: las explosiones de creeper no rompen bloques.
  world.beforeEvents.explosion.subscribe((ev) => {
    if (ev.source?.typeId === "minecraft:creeper") ev.setImpactedBlocks([]);
  });

  system.runInterval(actualizarMarcador, 10);
  system.runInterval(avisoOficial, 20 * 60 * 12);
}
