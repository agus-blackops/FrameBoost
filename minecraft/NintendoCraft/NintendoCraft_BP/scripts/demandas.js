import { Player, system, world } from "@minecraft/server";
import { ActionFormData, FormCancelationReason } from "@minecraft/server-ui";
import { ID, azar, contarItem, darItem, quitarItem } from "./util.js";

const ABOGADO = "nc:abogado";
const DIMENSIONES = ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"];
/** Si el abogado no llega en este tiempo, la demanda llega "por correo". */
const ESPERA_MAXIMA = 20 * 20;
const COSTO_ABOGADO_PROPIO = 10;

/** Nombres "registrados". Ponérselos a una mascota o a un objeto trae problemas. */
const PALABRAS_PROHIBIDAS = [
  "mario", "luigi", "peach", "bowser", "yoshi", "toad", "wario", "zelda", "link",
  "pikachu", "pokemon", "pokémon", "kirby", "donkey kong", "samus", "metroid",
  "splatoon", "palworld", "emulador",
];

const MOTIVOS_AL_AZAR = [
  "Tu casa se parece demasiado a un castillo con una princesa adentro.",
  "Saltaste de una forma muy parecida a un salto registrado.",
  "Usaste el color rojo y un bigote en la misma semana.",
  "Pisaste un champiñón sin tener licencia de champiñones.",
  "Tienes tuberías verdes. Las tuberías verdes son propiedad intelectual.",
  "Grabaste un video de tu mundo sin pedir permiso.",
  "Dijiste que el juego era \"divertido\" sin pagar por usar esa palabra.",
  "Tu perro se parece sospechosamente a un personaje famoso.",
  "Hablaste de emuladores en voz alta (te escuchamos).",
  "Recogiste una moneda con demasiada alegría.",
  "Tu mundo tiene más de tres colores. Eso es diseño de niveles registrado.",
];

/**
 * @typedef {{ motivos: string[], multa: number, abogadoId?: string, desde: number, enJuicio: boolean }} Caso
 * @type {Map<string, Caso>} jugador -> demanda pendiente
 */
const casos = new Map();

/** @param {string} texto */
function palabraProhibida(texto) {
  const t = texto.toLowerCase();
  return PALABRAS_PROHIBIDAS.find((p) => t.includes(p));
}

/** Busca un sitio libre cerca del jugador para que aparezca el abogado. */
function lugarParaAbogado(jugador) {
  const { x, y, z } = jugador.location;
  for (let intento = 0; intento < 16; intento++) {
    const angulo = Math.random() * Math.PI * 2;
    const distancia = azar(6, 10);
    const pos = { x: Math.floor(x + Math.cos(angulo) * distancia), y: Math.floor(y) + azar(-2, 2), z: Math.floor(z + Math.sin(angulo) * distancia) };
    try {
      const pies = jugador.dimension.getBlock(pos);
      const cabeza = pies?.above();
      const suelo = pies?.below();
      if (pies?.isAir && cabeza?.isAir && suelo && !suelo.isAir && !suelo.isLiquid) {
        return { x: pos.x + 0.5, y: pos.y, z: pos.z + 0.5 };
      }
    } catch {
      // Fuera del mundo o chunk sin cargar.
    }
  }
  return jugador.location;
}

/**
 * Presenta una demanda contra el jugador. Si ya tiene una pendiente, se suma.
 * @param {Player} jugador
 * @param {string} motivo
 * @param {number} multa
 */
export function demandar(jugador, motivo, multa) {
  const caso = casos.get(jugador.id);
  if (caso) {
    caso.motivos.push(motivo);
    caso.multa += multa;
    jugador.sendMessage(`§c[Nintendo Legal] Se agregó un cargo a tu demanda: §f${motivo}`);
    return;
  }
  let abogadoId;
  try {
    const abogado = jugador.dimension.spawnEntity(ABOGADO, lugarParaAbogado(jugador));
    abogado.nameTag = "§cAbogado de Nintendo";
    abogadoId = abogado.id;
  } catch {
    abogadoId = undefined;
  }
  casos.set(jugador.id, { motivos: [motivo], multa, abogadoId, desde: system.currentTick, enJuicio: false });
  jugador.onScreenDisplay.setTitle("§l§4¡TE DEMANDARON!", {
    subtitle: "§fUn abogado de Nintendo viene hacia ti",
    fadeInDuration: 5,
    stayDuration: 50,
    fadeOutDuration: 10,
  });
  jugador.sendMessage(`§c[Nintendo Legal] Motivo: §f${motivo}`);
  jugador.playSound("mob.evocation_illager.prepare_summon", { volume: 0.6 });
}

/** @param {Caso} caso */
function quitarAbogado(caso) {
  if (!caso.abogadoId) return;
  const abogado = world.getEntity(caso.abogadoId);
  if (!abogado?.isValid) return;
  abogado.dimension.spawnParticle("minecraft:knockback_roar_particle", abogado.location);
  abogado.remove();
}

/**
 * Cobra la multa. Si no alcanza, Nintendo embarga todas las monedas.
 * @param {Player} jugador
 * @param {number} monto
 */
function cobrar(jugador, monto) {
  if (monto <= 0) return;
  if (quitarItem(jugador, ID.moneda, monto)) {
    jugador.sendMessage(`§7Pagaste §6${monto} monedas§7. Caso cerrado.`);
    jugador.playSound("note.bass");
    return;
  }
  quitarItem(jugador, ID.moneda, contarItem(jugador, ID.moneda));
  jugador.addEffect("slowness", 20 * 30, { amplifier: 1 });
  jugador.addEffect("mining_fatigue", 20 * 30, { amplifier: 1 });
  jugador.onScreenDisplay.setTitle("§l§4EMBARGO", {
    subtitle: "§fTe quitaron todas tus monedas + 30 s de arresto domiciliario",
    fadeInDuration: 5,
    stayDuration: 60,
    fadeOutDuration: 10,
  });
  jugador.playSound("mob.wither.hurt", { volume: 0.5 });
}

/** @param {Player} jugador @param {Caso} caso */
function cerrarCaso(jugador, caso) {
  quitarAbogado(caso);
  casos.delete(jugador.id);
}

/**
 * Abre el juicio. Si el jugador tiene otro menú abierto, se reintenta.
 * @param {Player} jugador
 * @param {Caso} caso
 */
function juicio(jugador, caso) {
  caso.enJuicio = true;
  const monedas = contarItem(jugador, ID.moneda);
  const formulario = new ActionFormData()
    .title("§l§4DEMANDA OFICIAL")
    .body(
      `§fCaso Nº ${azar(1000, 9999)}-NL\n` +
        `§7Demandante: §fNintendo\n` +
        `§7Demandado: §f${jugador.name}\n\n` +
        `§7Motivo${caso.motivos.length > 1 ? "s" : ""}:\n` +
        caso.motivos.map((m) => `§f- ${m}`).join("\n") +
        `\n\n§7Multa: §6${caso.multa} monedas\n§7Tienes: §6${monedas} monedas`
    )
    .button(`Pagar la multa\n§6${caso.multa} monedas`)
    .button("Defenderme yo mismo\n§720% de ganar, si pierdo pago el doble")
    .button(`Contratar a mi abogado\n§7${COSTO_ABOGADO_PROPIO} monedas, 60% de ganar`)
    .button("Llegar a un acuerdo\n§7Pago la mitad y firmo un contrato secreto");

  formulario.show(jugador).then((r) => {
    if (!jugador.isValid) return;
    if (r.canceled) {
      if (r.cancelationReason === FormCancelationReason.UserBusy) {
        system.runTimeout(() => jugador.isValid && juicio(jugador, caso), 20);
        return;
      }
      // Cerrar el juicio cuenta como huir: el abogado vuelve con más multa.
      cerrarCaso(jugador, caso);
      jugador.sendMessage("§c[Nintendo Legal] Huiste del juicio. Volveremos con la multa aumentada un 50%.");
      const multa = Math.ceil(caso.multa * 1.5);
      system.runTimeout(() => {
        if (jugador.isValid) demandar(jugador, "Huir de un juicio de Nintendo.", multa);
      }, 20 * 60);
      return;
    }
    switch (r.selection) {
      case 0:
        cobrar(jugador, caso.multa);
        break;
      case 1:
        if (Math.random() < 0.2) {
          jugador.sendMessage("§a¡Ganaste el juicio! §7El juez dijo: \"objeción aceptada\". Te dan 5 monedas.");
          darItem(jugador, ID.moneda, 5);
          jugador.playSound("random.levelup");
        } else {
          jugador.sendMessage("§cPerdiste. §7Tu defensa fue \"no sabía\". La multa se duplica.");
          cobrar(jugador, caso.multa * 2);
        }
        break;
      case 2:
        if (!quitarItem(jugador, ID.moneda, COSTO_ABOGADO_PROPIO)) {
          jugador.sendMessage("§cNo puedes pagar un abogado. §7Te asignan uno de oficio, que se quedó dormido.");
          cobrar(jugador, caso.multa);
        } else if (Math.random() < 0.6) {
          jugador.sendMessage("§a¡Tu abogado ganó! §7Encontró una coma mal puesta en la demanda.");
          jugador.playSound("random.levelup");
        } else {
          jugador.sendMessage("§cTu abogado perdió. §7Resulta que también trabajaba para Nintendo.");
          cobrar(jugador, caso.multa);
        }
        break;
      case 3:
        jugador.sendMessage("§7Firmaste un acuerdo de confidencialidad. No puedes hablar de esto. Nunca.");
        cobrar(jugador, Math.ceil(caso.multa / 2));
        break;
    }
    cerrarCaso(jugador, caso);
  });
}

/** Avanza los casos: cuando el abogado llega, empieza el juicio. */
function revisarCasos() {
  const ahora = system.currentTick;
  const jugadores = new Map(world.getAllPlayers().map((j) => [j.id, j]));
  for (const [id, caso] of casos) {
    const jugador = jugadores.get(id);
    if (!jugador) {
      quitarAbogado(caso);
      casos.delete(id);
      continue;
    }
    if (caso.enJuicio) continue;
    const abogado = caso.abogadoId ? world.getEntity(caso.abogadoId) : undefined;
    let llego = false;
    if (abogado?.isValid && abogado.dimension.id === jugador.dimension.id) {
      const dx = abogado.location.x - jugador.location.x;
      const dy = abogado.location.y - jugador.location.y;
      const dz = abogado.location.z - jugador.location.z;
      llego = dx * dx + dy * dy + dz * dz < 9;
    }
    if (llego) {
      juicio(jugador, caso);
    } else if (ahora - caso.desde > ESPERA_MAXIMA) {
      jugador.sendMessage("§7[Nintendo Legal] El abogado no pudo llegar, así que te mandó la demanda por correo.");
      juicio(jugador, caso);
    }
  }
}

/** Quita abogados que quedaron sin caso (por ejemplo, al recargar el mundo). */
function limpiarAbogados() {
  const activos = new Set([...casos.values()].map((c) => c.abogadoId));
  for (const id of DIMENSIONES) {
    for (const abogado of world.getDimension(id).getEntities({ type: ABOGADO })) {
      if (!activos.has(abogado.id)) abogado.remove();
    }
  }
}

/** Mascotas y objetos con nombres "registrados" se confiscan y traen demanda. */
function revisarNombres() {
  for (const jugador of world.getAllPlayers()) {
    for (const ser of jugador.dimension.getEntities({ location: jugador.location, maxDistance: 16 })) {
      if (ser instanceof Player || ser.typeId === ABOGADO || !ser.nameTag) continue;
      const palabra = palabraProhibida(ser.nameTag);
      if (!palabra) continue;
      const nombre = ser.nameTag;
      ser.nameTag = `Personaje Genérico Nº ${azar(1, 999)}`;
      demandar(jugador, `Llamar "${nombre}" a un ser sin licencia.`, 25);
    }

    const inv = jugador.getComponent("minecraft:inventory")?.container;
    if (!inv) continue;
    for (let i = 0; i < inv.size; i++) {
      const item = inv.getItem(i);
      if (!item?.nameTag || !palabraProhibida(item.nameTag)) continue;
      const nombre = item.nameTag;
      item.nameTag = "Objeto Genérico";
      inv.setItem(i, item);
      demandar(jugador, `Tener un objeto llamado "${nombre}". Fue confiscado y renombrado.`, 15);
    }
  }
}

/** De vez en cuando, Nintendo encuentra un motivo cualquiera para demandar. */
function patrullaLegal() {
  for (const jugador of world.getAllPlayers()) {
    if (casos.has(jugador.id) || Math.random() > 0.4) continue;
    const motivo = MOTIVOS_AL_AZAR[Math.floor(Math.random() * MOTIVOS_AL_AZAR.length)];
    demandar(jugador, motivo, azar(2, 8) * 5);
  }
}

export function iniciarDemandas() {
  // Pegarle a un abogado sale caro.
  world.afterEvents.entityHitEntity.subscribe((ev) => {
    const jugador = ev.damagingEntity;
    if (!(jugador instanceof Player) || ev.hitEntity.typeId !== ABOGADO) return;
    const caso = casos.get(jugador.id);
    if (caso && !caso.enJuicio) {
      caso.multa += 10;
      jugador.sendMessage("§c[Nintendo Legal] Agresión a un abogado: §6+10 monedas§c de multa.");
    } else {
      jugador.sendMessage("§7El abogado ni se inmutó. Tiene un seguro muy caro.");
    }
    jugador.playSound("note.bass");
  });

  system.runInterval(revisarCasos, 10);
  system.runInterval(revisarNombres, 20 * 5);
  system.runInterval(limpiarAbogados, 20 * 10);
  system.runInterval(patrullaLegal, 20 * 60 * 8);
}
