// Los 7 Pecados Capitales — poderes especiales de cada criatura.
import { world, system } from "@minecraft/server";

const NS = "pecados";
const DIMENSIONES = ["overworld", "nether", "the_end"];

function es(entidad, nombre) {
  return entidad?.typeId === `${NS}:${nombre}`;
}

function valida(entidad) {
  try {
    return entidad !== undefined && (typeof entidad.isValid === "function" ? entidad.isValid() : entidad.isValid);
  } catch {
    return false;
  }
}

function efecto(entidad, tipo, ticks, amplificador = 0) {
  try {
    entidad.addEffect(tipo, ticks, { amplifier: amplificador, showParticles: true });
  } catch {}
}

function distancia(a, b) {
  const dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
  return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

function curar(entidad, cantidad) {
  const vida = entidad.getComponent("minecraft:health");
  if (vida) vida.setCurrentValue(Math.min(vida.effectiveMax, vida.currentValue + cantidad));
}

function porcentajeVida(entidad) {
  const vida = entidad.getComponent("minecraft:health");
  return vida ? vida.currentValue / vida.effectiveMax : 1;
}

function aviso(jugador, texto) {
  try {
    jugador.sendMessage(texto);
  } catch {}
}

function sonido(entidad, id) {
  try {
    entidad.runCommandAsync(`playsound ${id} @a ~ ~ ~ 1 1`);
  } catch {}
}

function empujarHacia(victima, origen, horizontal, vertical) {
  let dx = origen.x - victima.location.x;
  let dz = origen.z - victima.location.z;
  const largo = Math.sqrt(dx * dx + dz * dz) || 1;
  try {
    victima.applyKnockback(dx / largo, dz / largo, horizontal, vertical);
  } catch {}
}

// ---------------------------------------------------------------------------
// Golpes de los pecados
// ---------------------------------------------------------------------------

function ranuraEnMano(jugador) {
  return jugador.selectedSlotIndex ?? jugador.selectedSlot;
}

function objetoEnMano(jugador) {
  try {
    return jugador.getComponent("minecraft:inventory")?.container?.getItem(ranuraEnMano(jugador));
  } catch {
    return undefined;
  }
}

function robarObjeto(avaro, jugador, nombre = "Avaricity") {
  const inv = jugador.getComponent("minecraft:inventory")?.container;
  const bolsa = avaro.getComponent("minecraft:inventory")?.container;
  if (!inv || !bolsa || bolsa.emptySlotsCount === 0) return;
  const ranura = ranuraEnMano(jugador);
  const objeto = inv.getItem(ranura);
  if (!objeto) return;
  const sobrante = bolsa.addItem(objeto);
  inv.setItem(ranura, sobrante);
  efecto(avaro, "speed", 200, 2);
  sonido(avaro, "random.orb");
  aviso(jugador, `§6¡${nombre} te ha robado lo que tenías en la mano! §7Mátalo para recuperarlo.`);
}

world.afterEvents.entityHitEntity.subscribe(({ damagingEntity: atacante, hitEntity: victima }) => {
  if (!valida(atacante) || !valida(victima)) return;
  const jugador = victima.typeId === "minecraft:player";

  if (es(atacante, "superbius")) {
    // Humillación: lanza a la víctima por los aires.
    empujarHacia(victima, {
      x: 2 * victima.location.x - atacante.location.x,
      z: 2 * victima.location.z - atacante.location.z,
    }, 0.6, 0.9);
    if (jugador) aviso(victima, "§5Superbius: §d«¡Arrodíllate ante tu rey!»");
  } else if (es(atacante, "iratus")) {
    try {
      victima.setOnFire(5, true);
    } catch {}
  } else if (es(atacante, "avaricity")) {
    if (jugador && Math.random() < 0.35) robarObjeto(atacante, victima);
  } else if (es(atacante, "envidium")) {
    // Roba tu fuerza y a veces aparece a tu espalda.
    efecto(atacante, "strength", 200, 0);
    if (Math.random() < 0.3) {
      try {
        const vista = victima.getViewDirection();
        const destino = {
          x: victima.location.x - vista.x * 2,
          y: victima.location.y,
          z: victima.location.z - vista.z * 2,
        };
        atacante.teleport(destino, { dimension: victima.dimension, facingLocation: victima.location });
        sonido(atacante, "mob.endermen.portal");
      } catch {}
    }
  } else if (es(atacante, "glotonix")) {
    curar(atacante, 3);
    if (jugador) aviso(victima, "§6Glotonix devora tu energía...");
  } else if (es(atacante, "lujurius")) {
    efecto(victima, "slowness", 60, 0);
  } else if (es(atacante, "perezoth")) {
    efecto(victima, "mining_fatigue", 120, 1);
  }
});

// ---------------------------------------------------------------------------
// Daño recibido por los pecados
// ---------------------------------------------------------------------------

world.afterEvents.entityHurt.subscribe(({ hurtEntity: herido, damage, damageSource }) => {
  if (!valida(herido)) return;
  const agresor = damageSource?.damagingEntity;

  if (es(herido, "iratus") && porcentajeVida(herido) < 0.5 && !herido.getEffect?.("strength")) {
    // Furia: más fuerte y rápido cuando está herido.
    efecto(herido, "strength", 300, 1);
    efecto(herido, "speed", 300, 1);
    sonido(herido, "mob.ravager.roar");
  } else if (es(herido, "superbius") && valida(agresor) && agresor.typeId === "minecraft:player") {
    // La soberbia devuelve parte de cada golpe.
    if (Math.random() < 0.3) {
      try {
        agresor.applyDamage(Math.max(1, Math.ceil(damage * 0.4)));
        aviso(agresor, "§5«¿Cómo te atreves a tocarme?»");
      } catch {}
    }
  } else if (es(herido, "envidium") && Math.random() < 0.25) {
    efecto(herido, "invisibility", 60, 0);
  }
});

// ---------------------------------------------------------------------------
// Auras (cada 2 segundos)
// ---------------------------------------------------------------------------

system.runInterval(() => {
  for (const id of DIMENSIONES) {
    let dim;
    try {
      dim = world.getDimension(id);
    } catch {
      continue;
    }
    for (const pecado of dim.getEntities({ families: ["pecado"] })) {
      try {
        aura(pecado, dim);
      } catch {}
    }
  }
}, 40);

function jugadoresCerca(dim, centro, radio) {
  return dim.getPlayers({ location: centro, maxDistance: radio });
}

function aura(pecado, dim) {
  const pos = pecado.location;

  if (es(pecado, "perezoth")) {
    for (const j of jugadoresCerca(dim, pos, 7)) {
      efecto(j, "slowness", 60, 1);
      efecto(j, "mining_fatigue", 60, 0);
    }
  } else if (es(pecado, "lujurius")) {
    for (const j of jugadoresCerca(dim, pos, 10)) {
      if (distancia(j.location, pos) > 2.5) empujarHacia(j, pos, 0.5, 0.05);
    }
  } else if (es(pecado, "glotonix")) {
    for (const objeto of dim.getEntities({ type: "minecraft:item", location: pos, maxDistance: 3 })) {
      objeto.kill();
      curar(pecado, 4);
      sonido(pecado, "random.burp");
    }
  } else if (es(pecado, "superbius")) {
    if (porcentajeVida(pecado) < 0.5) efecto(pecado, "regeneration", 50, 0);
  } else if (es(pecado, "envidium")) {
    if (Math.random() < 0.08) efecto(pecado, "invisibility", 60, 0);
  }
}

// ---------------------------------------------------------------------------
// Esencias: al beberlas otorgan el poder del pecado
// ---------------------------------------------------------------------------

const ESENCIAS = {
  soberbia: [["resistance", 600, 1], ["absorption", 600, 1]],
  avaricia: [["luck", 1200, 0], ["haste", 1200, 1]],
  lujuria: [["regeneration", 300, 1]],
  ira: [["strength", 600, 1], ["fire_resistance", 600, 0]],
  gula: [["saturation", 20, 4], ["health_boost", 1200, 1]],
  envidia: [["invisibility", 1200, 0], ["night_vision", 1200, 0]],
  pereza: [["slow_falling", 1200, 0], ["jump_boost", 1200, 1]],
};

world.afterEvents.itemCompleteUse.subscribe(({ itemStack, source }) => {
  const prefijo = `${NS}:esencia_`;
  if (!itemStack?.typeId.startsWith(prefijo)) return;
  for (const [tipo, ticks, amp] of ESENCIAS[itemStack.typeId.slice(prefijo.length)] ?? []) {
    efecto(source, tipo, ticks, amp);
  }
});

// ---------------------------------------------------------------------------
// El Rey de los Pecados (jefe)
// ---------------------------------------------------------------------------

const REY = `${NS}:rey_pecados`;
const SIERVOS = ["superbius", "avaricity", "lujurius", "iratus", "glotonix", "envidium", "perezoth"];
const proximoPoder = new Map();

function barraDeAccion(jugador, texto) {
  try {
    jugador.onScreenDisplay.setActionBar(texto);
  } catch {}
}

function rayo(dim, pos) {
  try {
    dim.spawnEntity("minecraft:lightning_bolt", pos);
  } catch {}
}

function alAzar(lista) {
  return lista[Math.floor(Math.random() * lista.length)];
}

// Cada poder recibe al rey, su dimensión y los jugadores cercanos.
const PODERES_REY = [
  ["§5Soberbia: §d«¡Postraos ante vuestro rey!»", (rey, dim, js) => {
    for (const j of js) {
      empujarHacia(j, { x: 2 * j.location.x - rey.location.x, z: 2 * j.location.z - rey.location.z }, 1.0, 1.2);
    }
  }],
  ["§6Avaricia: §e«Todo lo tuyo es mío.»", (rey, dim, js) => {
    robarObjeto(rey, alAzar(js), "El Rey de los Pecados");
  }],
  ["§dLujuria: §f«Ven a mí...»", (rey, dim, js) => {
    for (const j of js) empujarHacia(j, rey.location, 1.4, 0.2);
  }],
  ["§cIra: §4«¡ARDED!»", (rey, dim, js) => {
    for (const j of js) {
      try {
        dim.createExplosion(j.location, 1.5, { breaksBlocks: false, causesFire: false, source: rey });
      } catch {}
      try {
        j.setOnFire(4, true);
      } catch {}
    }
  }],
  ["§6Gula: §e«Tu fuerza alimenta la mía.»", (rey, dim, js) => {
    for (const j of js) efecto(j, "hunger", 200, 2);
    curar(rey, 10 + 5 * js.length);
  }],
  ["§2Envidia: §a«Lo que tú tienes, lo quiero yo.»", (rey, dim, js) => {
    for (const j of js) efecto(j, "weakness", 200, 1);
    const v = alAzar(js);
    try {
      const vista = v.getViewDirection();
      rey.teleport({ x: v.location.x - vista.x * 3, y: v.location.y, z: v.location.z - vista.z * 3 },
        { dimension: v.dimension, facingLocation: v.location });
      sonido(rey, "mob.endermen.portal");
    } catch {}
  }],
  ["§7Pereza: §8«Descansad... para siempre.»", (rey, dim, js) => {
    for (const j of js) {
      efecto(j, "slowness", 100, 2);
      efecto(j, "mining_fatigue", 200, 2);
    }
  }],
];

function invocarSiervos(rey, dim, cantidad) {
  for (let i = 0; i < cantidad; i++) {
    const ang = Math.random() * Math.PI * 2;
    const pos = { x: rey.location.x + Math.cos(ang) * 4, y: rey.location.y, z: rey.location.z + Math.sin(ang) * 4 };
    try {
      dim.spawnEntity(`${NS}:${alAzar(SIERVOS)}`, pos);
      rayo(dim, pos);
    } catch {}
  }
}

function turnoDelRey(rey, dim) {
  const js = jugadoresCerca(dim, rey.location, 24);
  const vida = porcentajeVida(rey);
  const fase = rey.getDynamicProperty("fase") ?? 0;

  if (fase < 1 && vida < 0.66) {
    rey.setDynamicProperty("fase", 1);
    for (const j of js) aviso(j, "§4§lEl Rey de los Pecados: §r§c«¡Venid, hijos míos!»");
    invocarSiervos(rey, dim, 2);
    sonido(rey, "mob.wither.ambient");
  } else if (fase < 2 && vida < 0.33) {
    rey.setDynamicProperty("fase", 2);
    for (const j of js) aviso(j, "§4§lEl Rey de los Pecados: §r§c«¡Los siete pecados arden en mí!»");
    invocarSiervos(rey, dim, 3);
    sonido(rey, "mob.enderdragon.growl");
  }
  if ((rey.getDynamicProperty("fase") ?? 0) >= 2) {
    efecto(rey, "strength", 40, 1);
    efecto(rey, "speed", 40, 1);
  }

  if (js.length === 0) return;
  const ahora = system.currentTick;
  if (ahora < (proximoPoder.get(rey.id) ?? 0)) return;
  proximoPoder.set(rey.id, ahora + (fase >= 2 ? 80 : 140));

  const [frase, poder] = alAzar(PODERES_REY);
  for (const j of js) barraDeAccion(j, frase);
  poder(rey, dim, js);
}

system.runInterval(() => {
  for (const id of DIMENSIONES) {
    let dim;
    try {
      dim = world.getDimension(id);
    } catch {
      continue;
    }
    for (const rey of dim.getEntities({ type: REY })) {
      try {
        turnoDelRey(rey, dim);
      } catch {}
    }
  }
}, 20);

world.afterEvents.entityDie.subscribe(({ deadEntity }) => {
  if (deadEntity?.typeId !== REY) return;
  proximoPoder.delete(deadEntity.id);
  world.sendMessage("§6§l¡El Rey de los Pecados ha caído! §r§eLos siete pecados vuelven a dormir... por ahora.");
});

// La Corona de los Pecados invoca al rey.
world.afterEvents.itemUse.subscribe(({ itemStack, source: jugador }) => {
  if (itemStack?.typeId !== `${NS}:corona_pecados` || jugador.typeId !== "minecraft:player") return;

  let creativo = false;
  try {
    creativo = jugador.getGameMode() === "creative";
  } catch {}
  if (!creativo) {
    const inv = jugador.getComponent("minecraft:inventory")?.container;
    const ranura = ranuraEnMano(jugador);
    const objeto = inv?.getItem(ranura);
    if (!objeto || objeto.typeId !== itemStack.typeId) return;
    if (objeto.amount > 1) {
      objeto.amount -= 1;
      inv.setItem(ranura, objeto);
    } else {
      inv.setItem(ranura, undefined);
    }
  }

  const dim = jugador.dimension;
  const vista = jugador.getViewDirection();
  const pos = { x: jugador.location.x + vista.x * 5, y: jugador.location.y, z: jugador.location.z + vista.z * 5 };
  for (const j of jugadoresCerca(dim, pos, 48)) {
    try {
      j.onScreenDisplay.setTitle("§4Rey de los Pecados", { subtitle: "§7Los siete se hacen uno...", fadeInDuration: 10, stayDuration: 50, fadeOutDuration: 20 });
    } catch {}
  }
  sonido(jugador, "mob.wither.spawn");
  for (let i = 0; i < 3; i++) system.runTimeout(() => rayo(dim, pos), 15 * i);
  system.runTimeout(() => {
    try {
      dim.spawnEntity(REY, pos);
      rayo(dim, pos);
    } catch {}
  }, 60);
});

// La Espada de los Siete Pecados aplica un pecado al azar en cada golpe.
const GOLPES_ESPADA = [
  (j, v) => empujarHacia(v, { x: 2 * v.location.x - j.location.x, z: 2 * v.location.z - j.location.z }, 0.5, 0.6),
  (j, v) => efecto(j, "speed", 60, 1),
  (j, v) => efecto(v, "slowness", 60, 1),
  (j, v) => {
    try {
      v.setOnFire(4, true);
    } catch {}
  },
  (j, v) => curar(j, 2),
  (j, v) => efecto(v, "weakness", 100, 0),
  (j, v) => efecto(v, "wither", 60, 0),
];

world.afterEvents.entityHitEntity.subscribe(({ damagingEntity: j, hitEntity: v }) => {
  if (j?.typeId !== "minecraft:player" || !valida(v)) return;
  if (objetoEnMano(j)?.typeId !== `${NS}:espada_pecados`) return;
  alAzar(GOLPES_ESPADA)(j, v);
});
