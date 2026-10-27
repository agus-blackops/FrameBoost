import { Player, system, world } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";
import { demandar } from "./demandas.js";
import { ID, contarItem, darItem, leerPruebas, quitarItem } from "./util.js";
import { mensajeBarra } from "./vidas.js";

const TASA_JUDICIAL = 15;
const BONO_PRUEBA = 0.25;
const ESPERA_ENTRE_DEMANDAS = 20 * 60 * 5;

/** Motivos para demandar a Nintendo. `prueba` es lo que el jugador tiene que haber vivido. */
const MOTIVOS = [
  { texto: "Me cobraron un 10% de comisión por usar la tienda", prueba: "comision", probabilidad: 0.35, premio: 40 },
  { texto: "El Nether era gratis y ahora es DLC", prueba: "nether", probabilidad: 0.3, premio: 80 },
  { texto: "El Pato Clicker no da monedas", prueba: "pato", probabilidad: 0.4, premio: 25 },
  { texto: "Mi Pokemán se cerró solo", prueba: "pokeman", probabilidad: 0.3, premio: 50 },
  { texto: "Me pegó el caparazón azul", prueba: "caparazon", probabilidad: 0.15, premio: 150 },
  { texto: "Me demandaron por cualquier cosa", prueba: "demandado", probabilidad: 0.25, premio: 70 },
  { texto: "Los precios no bajan desde 2017", prueba: undefined, probabilidad: 0.1, premio: 200 },
];

const ABOGADOS = [
  { texto: "Defenderme yo mismo", costo: 0, bono: 0, frase: "Te pusiste tu mejor camisa." },
  { texto: "Abogado barato", costo: 10, bono: 0.1, frase: "Tu abogado llegó en patineta." },
  { texto: "Abogado carísimo", costo: 60, bono: 0.3, frase: "Tu abogado trajo tres maletines y un perro de traje." },
];

/** @type {Map<string, number>} jugador -> tick en que puede volver a demandar */
const proximaDemanda = new Map();

/** @param {Player} jugador */
function abrirFormulario(jugador) {
  const ahora = system.currentTick;
  const listo = proximaDemanda.get(jugador.id) ?? 0;
  if (listo > ahora) {
    jugador.sendMessage(
      `§7El tribunal está saturado de demandas contra Nintendo. Vuelve en ${Math.ceil((listo - ahora) / 20 / 60)} min.`
    );
    return;
  }
  const pruebas = leerPruebas(jugador);
  const formulario = new ActionFormData()
    .title("§l§2DEMANDAR A NINTENDO")
    .body(
      `§fElige el motivo de tu demanda.\n` +
        `§7Tasa judicial: §6${TASA_JUDICIAL} monedas§7. Tienes §6${contarItem(jugador, ID.moneda)}§7.\n` +
        `§7Si tienes §apruebas§7 (algo que de verdad te pasó), tienes más probabilidad de ganar.`
    );
  for (const m of MOTIVOS) {
    const conPrueba = m.prueba !== undefined && pruebas.includes(m.prueba);
    formulario.button(`${m.texto}\n${conPrueba ? "§2Tienes pruebas" : "§8Sin pruebas"} §7- puedes ganar §6${m.premio}`);
  }
  formulario.show(jugador).then((r) => {
    if (r.canceled || r.selection === undefined) return;
    const motivo = MOTIVOS[r.selection];
    if (motivo) elegirAbogado(jugador, motivo, motivo.prueba !== undefined && pruebas.includes(motivo.prueba));
  });
}

/**
 * @param {Player} jugador
 * @param {typeof MOTIVOS[number]} motivo
 * @param {boolean} conPrueba
 */
function elegirAbogado(jugador, motivo, conPrueba) {
  const formulario = new ActionFormData()
    .title("§l§2DEMANDAR A NINTENDO")
    .body(`§fMotivo: §e${motivo.texto}\n§7Nintendo ya contrató a 300 abogados. ¿Y tú?`);
  for (const a of ABOGADOS) formulario.button(`${a.texto}\n${a.costo ? `§6${a.costo} monedas` : "§2Gratis"} §7(+${a.bono * 100}%)`);
  formulario.show(jugador).then((r) => {
    if (r.canceled || r.selection === undefined) return;
    const abogado = ABOGADOS[r.selection];
    if (!abogado) return;
    const total = TASA_JUDICIAL + abogado.costo;
    if (!quitarItem(jugador, ID.moneda, total)) {
      jugador.sendMessage(`§cNecesitas §6${total} monedas§c para demandar (tasa + abogado). La justicia no es barata.`);
      jugador.playSound("note.bass");
      return;
    }
    proximaDemanda.set(jugador.id, system.currentTick + ESPERA_ENTRE_DEMANDAS);
    const probabilidad = Math.min(0.9, motivo.probabilidad + (conPrueba ? BONO_PRUEBA : 0) + abogado.bono);
    celebrarJuicio(jugador, motivo, conPrueba, abogado, probabilidad);
  });
}

/**
 * El juicio pasa en la barra de acción y al final se lee el veredicto.
 * @param {Player} jugador
 * @param {typeof MOTIVOS[number]} motivo
 * @param {boolean} conPrueba
 * @param {typeof ABOGADOS[number]} abogado
 * @param {number} probabilidad
 */
function celebrarJuicio(jugador, motivo, conPrueba, abogado, probabilidad) {
  const escenas = [
    "§7El juez entra a la sala...",
    `§7${abogado.frase}`,
    "§7Nintendo llega con 300 abogados y un Champi Gruñón de testigo...",
    conPrueba ? "§aPresentas tus pruebas. ¡El juez levanta una ceja!" : "§7No tienes pruebas. Improvisas un dibujo...",
    "§7El juez lo está pensando...",
  ];
  let paso = 0;
  const tarea = system.runInterval(() => {
    if (!jugador.isValid) return system.clearRun(tarea);
    if (paso < escenas.length) {
      mensajeBarra(jugador, escenas[paso], 40);
      jugador.playSound("random.click");
      paso++;
      return;
    }
    system.clearRun(tarea);
    if (Math.random() < probabilidad) ganar(jugador, motivo);
    else perder(jugador);
  }, 35);
}

/** @param {Player} jugador @param {typeof MOTIVOS[number]} motivo */
function ganar(jugador, motivo) {
  let premio = motivo.premio;
  let detalle = `§fNintendo te paga §6${premio} monedas`;
  if (Math.random() < 0.25) {
    premio = Math.ceil(premio / 2);
    detalle = `§fNintendo apeló... solo te pagan §6${premio} monedas`;
  }
  darItem(jugador, ID.moneda, premio);
  jugador.onScreenDisplay.setTitle("§l§a¡LE GANASTE A NINTENDO!", {
    subtitle: detalle,
    fadeInDuration: 5,
    stayDuration: 70,
    fadeOutDuration: 15,
  });
  jugador.dimension.spawnParticle("minecraft:totem_particle", jugador.location);
  jugador.playSound("random.totem", { volume: 0.6 });
  if (motivo.prueba === "nether" && !jugador.getDynamicProperty("nc:dlc_nether")) {
    jugador.setDynamicProperty("nc:dlc_nether", true);
    jugador.sendMessage("§aEl juez ordenó devolverte el Nether gratis. §7Nintendo dice que fue \"un error de sistema\".");
  }
  world.sendMessage(`§a[Noticias] §f¡${jugador.name} le ganó un juicio a Nintendo! Los expertos no lo pueden creer.`);
}

/** @param {Player} jugador */
function perder(jugador) {
  jugador.onScreenDisplay.setTitle("§l§cPERDISTE EL JUICIO", {
    subtitle: "§fY ahora Nintendo te contrademanda",
    fadeInDuration: 5,
    stayDuration: 60,
    fadeOutDuration: 15,
  });
  jugador.playSound("mob.wither.hurt", { volume: 0.4 });
  system.runTimeout(() => {
    if (jugador.isValid) demandar(jugador, "Demandar a Nintendo y perder (daños a su reputación impecable).", 20);
  }, 20 * 4);
}

export function iniciarDemandarANintendo() {
  world.afterEvents.itemUse.subscribe((ev) => {
    if (ev.itemStack.typeId === ID.formularioDemanda) abrirFormulario(ev.source);
  });
}
