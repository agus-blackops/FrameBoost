import { EntityDamageCause, EquipmentSlot, Player, system, world } from "@minecraft/server";
import { ID, esCreativo } from "./util.js";
import { ganarVida } from "./vidas.js";
import { abrirTienda } from "./tienda.js";

const DURACION_ESTRELLA = 20 * 12;
const ESPERA_FUEGO = 8;
/** Altura aproximada de cada monstruo, para saber si caíste encima. */
const ALTURAS = { [ID.champiGrunon]: 0.8, "minecraft:spider": 0.9, "minecraft:cave_spider": 0.5, "minecraft:slime": 1.0 };

/** @type {Map<string, number>} jugador -> tick en que termina su estrella */
const estrellaHasta = new Map();
/** @type {Map<string, number>} jugador -> tick en que puede volver a disparar */
const fuegoListo = new Map();
/** @type {Map<string, number>} jugador -> tick en que puede volver a aplastar */
const aplastarListo = new Map();

/** @param {Player} jugador */
export function tieneEstrella(jugador) {
  return (estrellaHasta.get(jugador.id) ?? 0) > system.currentTick;
}

/** @param {Player} jugador */
function comerSuperChampinon(jugador) {
  jugador.addEffect("health_boost", 20 * 120, { amplifier: 1 });
  jugador.addEffect("instant_health", 1, { amplifier: 1 });
  jugador.addEffect("strength", 20 * 60, { amplifier: 0 });
  jugador.onScreenDisplay.setTitle("§c¡SÚPER!", {
    subtitle: "§f+4 corazones durante 2 minutos",
    fadeInDuration: 5,
    stayDuration: 30,
    fadeOutDuration: 10,
  });
  jugador.playSound("random.levelup", { pitch: 0.8 });
}

/** @param {Player} jugador */
function dispararFuego(jugador) {
  const ahora = system.currentTick;
  if ((fuegoListo.get(jugador.id) ?? 0) > ahora) return;
  fuegoListo.set(jugador.id, ahora + ESPERA_FUEGO);

  const dir = jugador.getViewDirection();
  const cabeza = jugador.getHeadLocation();
  const inicio = { x: cabeza.x + dir.x * 1.2, y: cabeza.y + dir.y * 1.2 - 0.2, z: cabeza.z + dir.z * 1.2 };
  const bola = jugador.dimension.spawnEntity(ID.bolaFuego, inicio);
  const velocidad = { x: dir.x * 1.4, y: dir.y * 1.4 + 0.1, z: dir.z * 1.4 };
  const proyectil = bola.getComponent("minecraft:projectile");
  if (proyectil) {
    proyectil.owner = jugador;
    proyectil.shoot(velocidad);
  } else {
    bola.applyImpulse(velocidad);
  }
  jugador.playSound("mob.blaze.shoot", { volume: 0.6, pitch: 1.4 });

  if (esCreativo(jugador)) return;
  const equipo = jugador.getComponent("minecraft:equippable");
  const flor = equipo?.getEquipment(EquipmentSlot.Mainhand);
  const durabilidad = flor?.getComponent("minecraft:durability");
  if (!equipo || !flor || !durabilidad) return;
  if (durabilidad.damage + 1 >= durabilidad.maxDurability) {
    equipo.setEquipment(EquipmentSlot.Mainhand, undefined);
    jugador.playSound("random.break");
    jugador.sendMessage("§6Tu Flor de Fuego se marchitó.");
  } else {
    durabilidad.damage += 1;
    equipo.setEquipment(EquipmentSlot.Mainhand, flor);
  }
}

/** @param {Player} jugador */
function usarEstrella(jugador) {
  if (!esCreativo(jugador)) {
    const equipo = jugador.getComponent("minecraft:equippable");
    const pila = equipo?.getEquipment(EquipmentSlot.Mainhand);
    if (!equipo || pila?.typeId !== ID.estrella) return;
    if (pila.amount > 1) {
      pila.amount -= 1;
      equipo.setEquipment(EquipmentSlot.Mainhand, pila);
    } else {
      equipo.setEquipment(EquipmentSlot.Mainhand, undefined);
    }
  }
  estrellaHasta.set(jugador.id, system.currentTick + DURACION_ESTRELLA);
  jugador.addEffect("resistance", DURACION_ESTRELLA, { amplifier: 4, showParticles: false });
  jugador.addEffect("fire_resistance", DURACION_ESTRELLA, { amplifier: 0, showParticles: false });
  jugador.addEffect("speed", DURACION_ESTRELLA, { amplifier: 1, showParticles: false });
  jugador.addEffect("jump_boost", DURACION_ESTRELLA, { amplifier: 1, showParticles: false });
  jugador.onScreenDisplay.setTitle("§e¡INVENCIBLE!", {
    subtitle: "§fToca a los monstruos para derrotarlos",
    fadeInDuration: 5,
    stayDuration: 30,
    fadeOutDuration: 10,
  });
  jugador.playSound("random.totem", { volume: 0.6 });
}

/** Brillo de la estrella y daño a los monstruos que toques. */
function efectoEstrella() {
  const ahora = system.currentTick;
  for (const jugador of world.getAllPlayers()) {
    const fin = estrellaHasta.get(jugador.id);
    if (fin === undefined) continue;
    if (fin <= ahora) {
      estrellaHasta.delete(jugador.id);
      jugador.sendMessage("§7El efecto de la estrella terminó.");
      continue;
    }
    const { x, y, z } = jugador.location;
    jugador.dimension.spawnParticle("minecraft:totem_particle", {
      x: x + (Math.random() - 0.5),
      y: y + Math.random() * 1.8,
      z: z + (Math.random() - 0.5),
    });
    for (const enemigo of jugador.dimension.getEntities({ location: jugador.location, maxDistance: 2, families: ["monster"] })) {
      enemigo.applyDamage(20, { cause: EntityDamageCause.entityAttack, damagingEntity: jugador });
    }
  }
}

/** Saltar encima de un monstruo lo aplasta y te hace rebotar. */
function revisarAplastamientos() {
  const ahora = system.currentTick;
  for (const jugador of world.getAllPlayers()) {
    if (jugador.getVelocity().y > -0.2 || (aplastarListo.get(jugador.id) ?? 0) > ahora) continue;
    const pies = jugador.location;
    const cercanos = jugador.dimension.getEntities({ location: pies, maxDistance: 2.5, families: ["monster"] });
    for (const enemigo of cercanos) {
      const alto = ALTURAS[enemigo.typeId] ?? 1.9;
      const dy = pies.y - enemigo.location.y;
      const dx = pies.x - enemigo.location.x;
      const dz = pies.z - enemigo.location.z;
      if (dy < alto - 0.5 || dy > alto + 0.6 || dx * dx + dz * dz > 0.8) continue;

      aplastarListo.set(jugador.id, ahora + 6);
      const dano = enemigo.typeId === ID.champiGrunon ? 100 : 6;
      enemigo.applyDamage(dano, { cause: EntityDamageCause.entityAttack, damagingEntity: jugador });
      jugador.applyKnockback({ x: 0, z: 0 }, 0.6);
      jugador.dimension.playSound("mob.slime.small", enemigo.location, { pitch: 1.4 });
      jugador.dimension.spawnParticle("minecraft:basic_crit_particle", { x: enemigo.location.x, y: pies.y, z: enemigo.location.z });
      break;
    }
  }
}

export function iniciarPoderes() {
  world.afterEvents.itemCompleteUse.subscribe((ev) => {
    const jugador = ev.source;
    if (ev.itemStack.typeId === ID.superChampinon) comerSuperChampinon(jugador);
    else if (ev.itemStack.typeId === ID.champinonVida) ganarVida(jugador);
  });

  world.afterEvents.itemUse.subscribe((ev) => {
    const jugador = ev.source;
    switch (ev.itemStack.typeId) {
      case ID.florFuego:
        dispararFuego(jugador);
        break;
      case ID.estrella:
        usarEstrella(jugador);
        break;
      case ID.catalogo:
        abrirTienda(jugador);
        break;
    }
  });

  system.runInterval(revisarAplastamientos, 1);
  system.runInterval(efectoEstrella, 3);
}
