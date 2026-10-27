import { ItemStack, Player, system, world } from "@minecraft/server";
import { ID, azar, darItem, registrarPrueba } from "./util.js";
import { mensajeBarra } from "./vidas.js";

/**
 * @typedef {object} Juego
 * @property {string} id
 * @property {string} nombre
 * @property {number} precio
 * @property {"nintendo" | "basura"} tipo
 * @property {string} descripcion
 * @property {number} espera ticks entre partidas
 * @property {(jugador: Player) => void} jugar
 */

/** @param {Player} jugador @param {string} titulo @param {string} subtitulo */
function pantalla(jugador, titulo, subtitulo) {
  jugador.onScreenDisplay.setTitle(titulo, { subtitle: subtitulo, fadeInDuration: 5, stayDuration: 40, fadeOutDuration: 10 });
}

/** @type {Juego[]} */
export const JUEGOS = [
  // ------------------------------------------------- Juegos de Nintendo
  {
    id: "kart",
    nombre: "Mareo Kart 9 Deluxe",
    precio: 420,
    tipo: "nintendo",
    descripcion: "Turbo de 20 segundos. Cuidado con el caparazón azul.",
    espera: 20 * 60,
    jugar(jugador) {
      jugador.addEffect("speed", 20 * 20, { amplifier: 3 });
      jugador.addEffect("jump_boost", 20 * 20, { amplifier: 1 });
      pantalla(jugador, "§c¡TURBO!", "§fMareo Kart 9 Deluxe");
      jugador.playSound("firework.launch");
      if (Math.random() < 0.25) {
        system.runTimeout(() => {
          if (!jugador.isValid) return;
          jugador.removeEffect("speed");
          jugador.applyKnockback({ x: 0, z: 0 }, 1.2);
          jugador.addEffect("slowness", 20 * 4, { amplifier: 3 });
          pantalla(jugador, "§9¡CAPARAZÓN AZUL!", "§fIba primero. Ya no.");
          registrarPrueba(jugador, "caparazon");
          jugador.playSound("random.explode", { volume: 0.5 });
        }, 20 * 8);
      }
    },
  },
  {
    id: "zelga",
    nombre: "La Leyenda de Zelga: Lágrimas del Bloque",
    precio: 450,
    tipo: "nintendo",
    descripcion: "Paravela: caes lento y saltas alto durante 30 segundos.",
    espera: 20 * 60,
    jugar(jugador) {
      jugador.addEffect("slow_falling", 20 * 30, { amplifier: 0 });
      jugador.addEffect("jump_boost", 20 * 30, { amplifier: 2 });
      pantalla(jugador, "§a¡Paravela equipada!", "§fLa Leyenda de Zelga");
      jugador.playSound("random.levelup", { pitch: 1.6 });
    },
  },
  {
    id: "pokeman",
    nombre: "Pokemán Escarlato y Púrpuro",
    precio: 400,
    tipo: "nintendo",
    descripcion: "Atrapa un Pokemán salvaje. A veces el juego se cierra solo.",
    espera: 20 * 90,
    jugar(jugador) {
      if (Math.random() < 0.3) {
        jugador.addEffect("blindness", 20 * 3, { amplifier: 0 });
        pantalla(jugador, "§cEl programa se cerró", "§7Error 0x00B06. Gracias por tu paciencia.");
        registrarPrueba(jugador, "pokeman");
        jugador.playSound("note.bass");
        return;
      }
      const especies = ["minecraft:fox", "minecraft:rabbit", "minecraft:parrot", "minecraft:ocelot", "minecraft:frog", "minecraft:armadillo"];
      const especie = especies[Math.floor(Math.random() * especies.length)];
      const ser = jugador.dimension.spawnEntity(especie, jugador.location);
      ser.nameTag = `Pokemán Salvaje (Nv. ${azar(1, 100)})`;
      pantalla(jugador, "§e¡Atrapaste un Pokemán!", "§fNo es igual al del anuncio");
      jugador.playSound("random.orb");
    },
  },
  {
    id: "smosh",
    nombre: "Súper Smosh Hermanos Definitivo",
    precio: 390,
    tipo: "nintendo",
    descripcion: "¡Smash! Manda a volar a todos los bichos cercanos.",
    espera: 20 * 30,
    jugar(jugador) {
      const { x, z } = jugador.location;
      for (const ser of jugador.dimension.getEntities({ location: jugador.location, maxDistance: 6, families: ["mob"] })) {
        if (ser instanceof Player) continue;
        const dx = ser.location.x - x;
        const dz = ser.location.z - z;
        const largo = Math.hypot(dx, dz) || 1;
        ser.applyKnockback({ x: (dx / largo) * 2.5, z: (dz / largo) * 2.5 }, 0.7);
      }
      jugador.dimension.spawnParticle("minecraft:huge_explosion_emitter", jugador.location);
      pantalla(jugador, "§6¡SMASH!", "§fSúper Smosh Hermanos Definitivo");
      jugador.playSound("random.explode", { volume: 0.6, pitch: 1.4 });
    },
  },
  {
    id: "animales",
    nombre: "Animales Cruzados: Nuevo Horizonte",
    precio: 380,
    tipo: "nintendo",
    descripcion: "Planta flores a tu alrededor y pide un préstamo a Tom Nuk.",
    espera: 20 * 60 * 3,
    jugar(jugador) {
      const flores = ["minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:oxeye_daisy", "minecraft:allium"];
      const base = jugador.location;
      for (let dx = -3; dx <= 3; dx++) {
        for (let dz = -3; dz <= 3; dz++) {
          if (Math.random() > 0.35) continue;
          try {
            const bloque = jugador.dimension.getBlock({ x: Math.floor(base.x) + dx, y: Math.floor(base.y), z: Math.floor(base.z) + dz });
            const suelo = bloque?.below();
            if (bloque?.isAir && (suelo?.typeId === "minecraft:grass_block" || suelo?.typeId === "minecraft:dirt")) {
              bloque.setType(flores[Math.floor(Math.random() * flores.length)]);
            }
          } catch {
            // Chunk sin cargar.
          }
        }
      }
      darItem(jugador, ID.moneda, 10);
      jugador.sendMessage("§a[Tom Nuk] §fTe presté 10 monedas. Ahora me debes 98.000. ¡Sin prisa! (Con prisa.)");
      jugador.playSound("random.orb", { pitch: 0.8 });
    },
  },
  {
    id: "kirbi",
    nombre: "Kirbi y la Tierra Olvidada",
    precio: 350,
    tipo: "nintendo",
    descripcion: "¡Inhalar! Atrae los objetos tirados cerca y te hace flotar.",
    espera: 20 * 20,
    jugar(jugador) {
      for (const item of jugador.dimension.getEntities({ location: jugador.location, maxDistance: 12, type: "minecraft:item" })) {
        item.teleport(jugador.location);
      }
      jugador.addEffect("levitation", 20 * 2, { amplifier: 0 });
      jugador.addEffect("slow_falling", 20 * 6, { amplifier: 0 });
      pantalla(jugador, "§d¡INHALAR!", "§fKirbi y la Tierra Olvidada");
      jugador.playSound("mob.ghast.affectionate_scream", { volume: 0.4, pitch: 2 });
    },
  },

  // ------------------------------------------------------ Juegos basura
  {
    id: "pared",
    nombre: "Simulador de Mirar una Pared VR",
    precio: 60,
    tipo: "basura",
    descripcion: "10 segundos de pura inmersión. Gráficos de nueva generación.",
    espera: 20 * 15,
    jugar(jugador) {
      jugador.addEffect("darkness", 20 * 10, { amplifier: 0 });
      jugador.addEffect("slowness", 20 * 10, { amplifier: 5 });
      pantalla(jugador, "§7Mirando la pared...", "§8Experiencia completada: 10/10 paredes");
    },
  },
  {
    id: "pato",
    nombre: "Pato Clicker Deluxe Edition",
    precio: 35,
    tipo: "basura",
    descripcion: "Haz clic. Gana monedas (2% de probabilidad).",
    espera: 5,
    jugar(jugador) {
      jugador.playSound("random.click");
      if (Math.random() < 0.02) {
        darItem(jugador, ID.moneda, 1);
        mensajeBarra(jugador, "§eClic. ¡+1 moneda! ¡RÉCORD MUNDIAL!");
      } else {
        mensajeBarra(jugador, "§7Clic. (+0 monedas)", 20);
        registrarPrueba(jugador, "pato");
      }
    },
  },
  {
    id: "cubo",
    nombre: "Cubo Rojo: El Juego",
    precio: 50,
    tipo: "basura",
    descripcion: "Pon un cubo rojo. Ganas. Fin.",
    espera: 20 * 10,
    jugar(jugador) {
      const golpe = jugador.getBlockFromViewDirection({ maxDistance: 6 });
      const destino = golpe?.block.above();
      if (destino?.isAir) destino.setType("minecraft:red_concrete");
      pantalla(jugador, "§c¡GANASTE!", "§fGracias por jugar Cubo Rojo: El Juego");
    },
  },
  {
    id: "caracol",
    nombre: "Carreras de Caracoles 3D Extremo",
    precio: 40,
    tipo: "basura",
    descripcion: "¡Velocidad máxima!",
    espera: 20 * 20,
    jugar(jugador) {
      jugador.addEffect("slowness", 20 * 15, { amplifier: 5 });
      pantalla(jugador, "§a¡VELOCIDAD MÁXIMA!", "§f0,001 km/h");
    },
  },
  {
    id: "descarga",
    nombre: "Simulador de Descargas",
    precio: 55,
    tipo: "basura",
    descripcion: "La emoción de ver una barra que se llena.",
    espera: 20 * 15,
    jugar(jugador) {
      let progreso = 0;
      const tarea = system.runInterval(() => {
        if (!jugador.isValid) return system.clearRun(tarea);
        progreso = Math.min(99, progreso + azar(5, 15));
        mensajeBarra(jugador, `§fDescargando... §a${"|".repeat(progreso / 5)}§8${"|".repeat(20 - progreso / 5)} §f${progreso}%`, 20);
        if (progreso >= 99) {
          system.clearRun(tarea);
          system.runTimeout(() => jugador.isValid && pantalla(jugador, "§cError", "§7Espacio insuficiente. Borra Minecraft."), 40);
        }
      }, 10);
    },
  },
  {
    id: "futbol",
    nombre: "Fútbol de Granja 2009 HD Remasterizado",
    precio: 45,
    tipo: "basura",
    descripcion: "Incluye un balón oficial (es un cerdo).",
    espera: 20 * 60,
    jugar(jugador) {
      const balon = jugador.dimension.spawnEntity("minecraft:pig", jugador.location);
      balon.nameTag = "Balón Oficial";
      pantalla(jugador, "§a¡GOOOL!", "§fNo había portería");
    },
  },
];

/** @type {Map<string, number>} "jugador:juego" -> tick en que se puede volver a jugar */
const listoPara = new Map();

/**
 * Crea el cartucho de un juego.
 * @param {Juego} juego
 */
export function cartucho(juego) {
  const item = new ItemStack(juego.tipo === "nintendo" ? ID.juegoNintendo : ID.juegoBasura, 1);
  item.nameTag = `§r${juego.tipo === "nintendo" ? "§c" : "§7"}${juego.nombre}`;
  item.setLore([`§7${juego.descripcion}`, "§8Clic derecho para jugar"]);
  item.setDynamicProperty("nc:juego", juego.id);
  return item;
}

/** @param {Player} jugador @returns {string[]} */
export function biblioteca(jugador) {
  const datos = jugador.getDynamicProperty("nc:biblioteca");
  if (typeof datos !== "string") return [];
  try {
    return JSON.parse(datos);
  } catch {
    return [];
  }
}

/** @param {Player} jugador @param {Juego} juego */
export function agregarABiblioteca(jugador, juego) {
  const lista = biblioteca(jugador);
  if (!lista.includes(juego.id)) lista.push(juego.id);
  jugador.setDynamicProperty("nc:biblioteca", JSON.stringify(lista));
}

/**
 * Juega el juego del cartucho. Los de Nintendo a veces piden una actualización antes.
 * @param {Player} jugador
 * @param {ItemStack} item
 */
function jugarCartucho(jugador, item) {
  const id = item.getDynamicProperty("nc:juego");
  const juego = JUEGOS.find((j) => j.id === id);
  if (!juego) {
    jugador.sendMessage("§7Este cartucho está vacío. Alguien sopló demasiado fuerte.");
    return;
  }
  const clave = `${jugador.id}:${juego.id}`;
  const ahora = system.currentTick;
  const listo = listoPara.get(clave) ?? 0;
  if (listo > ahora) {
    mensajeBarra(jugador, `§7Descansa un poco. Puedes volver a jugar en ${Math.ceil((listo - ahora) / 20)} s.`);
    return;
  }
  listoPara.set(clave, ahora + juego.espera);

  if (juego.tipo === "nintendo" && Math.random() < 0.15) {
    // Actualización obligatoria de 5 segundos antes de poder jugar.
    let paso = 0;
    const tarea = system.runInterval(() => {
      if (!jugador.isValid) return system.clearRun(tarea);
      paso++;
      mensajeBarra(jugador, `§fActualización obligatoria 1.0.${azar(1, 9)} (${paso * 3} de 15 GB)...`, 30);
      if (paso >= 5) {
        system.clearRun(tarea);
        juego.jugar(jugador);
      }
    }, 20);
    return;
  }
  juego.jugar(jugador);
}

export function iniciarJuegos() {
  world.afterEvents.itemUse.subscribe((ev) => {
    const tipo = ev.itemStack.typeId;
    if (tipo === ID.juegoNintendo || tipo === ID.juegoBasura) jugarCartucho(ev.source, ev.itemStack);
  });
}
