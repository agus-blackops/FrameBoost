/**
 * Static validation of the behavior and resource packs, and of their
 * agreement with the TypeScript layer. These checks catch the mistakes that
 * Minecraft otherwise reports only at runtime, in the content log, on one
 * device at a time.
 */
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { DEFAULT_CONFIG } from "../src/config/gameConfig";
import { FACTIONS } from "../src/data/factions";
import { ENTITY, ENTITY_EVENT, ENTITY_PROPERTY, FAMILY, ITEM, ITEM_COMPONENT, ORDER_STATE, channelTag, factionTag } from "../src/data/ids";
import { UNITS } from "../src/data/units";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const BP = join(ROOT, "behavior_pack");
const RP = join(ROOT, "resource_pack");
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

type Json = any; // eslint-disable-line @typescript-eslint/no-explicit-any

function walk(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    return statSync(p).isDirectory() ? walk(p) : [p];
  });
}

function readJson(path: string): Json {
  return JSON.parse(readFileSync(path, "utf8"));
}

function jsonFiles(dir: string): string[] {
  return walk(dir).filter((p) => p.endsWith(".json") && !p.includes(`${join("scripts", "")}`));
}

function pngSize(path: string): { width: number; height: number } {
  const b = readFileSync(path);
  expect(b.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])), `${path} is not a PNG`).toBe(true);
  return { width: b.readUInt32BE(16), height: b.readUInt32BE(20) };
}

/** Collects every value of `key` anywhere inside a JSON tree. */
function collect(node: Json, key: string, out: Json[] = []): Json[] {
  if (Array.isArray(node)) node.forEach((n) => collect(n, key, out));
  else if (node && typeof node === "object") {
    for (const [k, v] of Object.entries(node)) {
      if (k === key) out.push(v);
      collect(v, key, out);
    }
  }
  return out;
}

const bpManifest = readJson(join(BP, "manifest.json"));
const rpManifest = readJson(join(RP, "manifest.json"));
const pkg = readJson(join(ROOT, "package.json"));

const bpEntities = new Map<string, Json>(
  readdirSync(join(BP, "entities")).map((f) => {
    const j = readJson(join(BP, "entities", f));
    return [j["minecraft:entity"].description.identifier, j];
  }),
);
const rpEntities = new Map<string, Json>(
  readdirSync(join(RP, "entity")).map((f) => {
    const j = readJson(join(RP, "entity", f));
    return [j["minecraft:client_entity"].description.identifier, j];
  }),
);
const geometries = new Map<string, Json>();
for (const f of walk(join(RP, "models"))) {
  for (const g of readJson(f)["minecraft:geometry"]) geometries.set(g.description.identifier, g);
}
const animations = new Set<string>();
for (const f of walk(join(RP, "animations"))) for (const k of Object.keys(readJson(f).animations)) animations.add(k);
const renderControllers = new Map<string, Json>();
for (const f of walk(join(RP, "render_controllers"))) {
  for (const [k, v] of Object.entries(readJson(f).render_controllers)) renderControllers.set(k, v);
}
const lang = new Map<string, string>(
  readFileSync(join(RP, "texts", "en_US.lang"), "utf8")
    .split(/\r?\n/)
    .filter((l) => l.includes("=") && !l.startsWith("#"))
    .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)] as [string, string]),
);

describe("JSON syntax", () => {
  it("every pack JSON file parses as strict JSON", () => {
    for (const f of [...jsonFiles(BP), ...jsonFiles(RP)]) {
      expect(() => readJson(f), relative(ROOT, f)).not.toThrow();
    }
  });
});

describe("manifests", () => {
  it("use format 2 with valid, unique v4 UUIDs", () => {
    const uuids = [bpManifest.header.uuid, ...bpManifest.modules.map((m: Json) => m.uuid), rpManifest.header.uuid, ...rpManifest.modules.map((m: Json) => m.uuid)];
    for (const u of uuids) expect(u).toMatch(UUID_RE);
    expect(new Set(uuids).size).toBe(uuids.length);
    expect(bpManifest.format_version).toBe(2);
    expect(rpManifest.format_version).toBe(2);
  });

  it("link the two packs to each other at matching versions", () => {
    const bpDep = bpManifest.dependencies.find((d: Json) => d.uuid === rpManifest.header.uuid);
    const rpDep = rpManifest.dependencies.find((d: Json) => d.uuid === bpManifest.header.uuid);
    expect(bpDep?.version).toEqual(rpManifest.header.version);
    expect(rpDep?.version).toEqual(bpManifest.header.version);
    expect(bpManifest.header.min_engine_version).toEqual(rpManifest.header.min_engine_version);
  });

  it("declare script modules at exactly the versions the code is typed against", () => {
    for (const name of ["@minecraft/server", "@minecraft/server-ui"]) {
      const dep = bpManifest.dependencies.find((d: Json) => d.module_name === name);
      expect(dep, name).toBeDefined();
      expect(dep.version).toBe(pkg.devDependencies[name]);
      const installed = readJson(join(ROOT, "node_modules", name, "package.json")).version;
      expect(installed).toBe(dep.version);
    }
  });

  it("point the script module at the bundle entry", () => {
    const script = bpManifest.modules.find((m: Json) => m.type === "script");
    expect(script.language).toBe("javascript");
    expect(script.entry).toBe("scripts/main.js");
  });

  it("have pack icons", () => {
    expect(existsSync(join(BP, "pack_icon.png"))).toBe(true);
    expect(existsSync(join(RP, "pack_icon.png"))).toBe(true);
  });
});

describe("entities", () => {
  it("every behavior entity has a client entity, a name and a known identifier", () => {
    for (const id of bpEntities.keys()) {
      expect(rpEntities.has(id), id).toBe(true);
      expect(lang.has(`entity.${id}.name`), `lang entity.${id}.name`).toBe(true);
    }
    expect(new Set(bpEntities.keys())).toEqual(new Set(Object.values(ENTITY)));
  });

  it("client entities reference existing geometry, textures, animations and render controllers", () => {
    for (const [id, ce] of rpEntities) {
      const d = ce["minecraft:client_entity"].description;
      for (const g of Object.values<string>(d.geometry)) expect(geometries.has(g), `${id} geometry ${g}`).toBe(true);
      for (const t of Object.values<string>(d.textures)) expect(existsSync(join(RP, `${t}.png`)), `${id} texture ${t}`).toBe(true);
      for (const a of Object.values<string>(d.animations ?? {})) expect(animations.has(a), `${id} animation ${a}`).toBe(true);
      for (const name of d.scripts?.animate ?? []) {
        const key = typeof name === "string" ? name : (Object.keys(name)[0] ?? "");
        expect(d.animations?.[key], `${id} animate ${key}`).toBeDefined();
      }
      for (const rc of d.render_controllers) expect(renderControllers.has(rc), `${id} render controller ${rc}`).toBe(true);
      if (d.spawn_egg) expect(lang.has(`item.spawn_egg.entity.${id}.name`), `${id} spawn egg name`).toBe(true);
    }
  });

  it("geometry UV boxes fit their textures, and textures match the declared size", () => {
    for (const [id, ce] of rpEntities) {
      const d = ce["minecraft:client_entity"].description;
      const geo = geometries.get(d.geometry.default);
      const { texture_width: tw, texture_height: th } = geo.description;
      for (const t of Object.values<string>(d.textures)) expect(pngSize(join(RP, `${t}.png`)), `${id} ${t}`).toEqual({ width: tw, height: th });
      for (const bone of geo.bones) {
        for (const c of bone.cubes ?? []) {
          const [w, h, dd] = c.size.map((n: number) => Math.ceil(n));
          const [u, v] = c.uv;
          expect(u + 2 * (w + dd), `${bone.name} uv width`).toBeLessThanOrEqual(tw);
          expect(v + dd + h, `${bone.name} uv height`).toBeLessThanOrEqual(th);
        }
      }
    }
  });

  it("animation bones exist in the geometry that plays them", () => {
    const animFiles = walk(join(RP, "animations")).map(readJson);
    const animBones = new Map<string, string[]>();
    for (const f of animFiles) for (const [k, v] of Object.entries<Json>(f.animations)) animBones.set(k, Object.keys(v.bones ?? {}));
    for (const [id, ce] of rpEntities) {
      const d = ce["minecraft:client_entity"].description;
      const bones = new Set(geometries.get(d.geometry.default).bones.map((b: Json) => b.name.toLowerCase()));
      for (const a of Object.values<string>(d.animations ?? {})) {
        for (const b of animBones.get(a) ?? []) expect(bones.has(b.toLowerCase()), `${id}: ${a} bone ${b}`).toBe(true);
      }
    }
  });

  it("events triggered from script are declared, and every component group referenced exists", () => {
    const unit = bpEntities.get(ENTITY.spearman)["minecraft:entity"];
    expect(unit.events[ENTITY_EVENT.orderMove]).toBeDefined();
    expect(unit.events[ENTITY_EVENT.orderIdle]).toBeDefined();
    const marker = bpEntities.get(ENTITY.rallyMarker)["minecraft:entity"];
    expect(marker.events[ENTITY_EVENT.markerExpire]).toBeDefined();
    for (const e of [unit, marker]) {
      const groups = new Set(Object.keys(e.component_groups ?? {}));
      for (const list of collect(e.events, "component_groups")) for (const g of list) expect(groups.has(g), g).toBe(true);
      for (const ev of collect(e.components, "event")) expect(e.events[ev], ev).toBeDefined();
      for (const ev of collect(e.component_groups, "event")) expect(e.events[ev], ev).toBeDefined();
    }
  });
});

describe("spearman agrees with script data and config", () => {
  const unit = bpEntities.get(ENTITY.spearman)["minecraft:entity"];
  const props = unit.description.properties;

  it("faction property covers every faction and the render controller has a skin for each", () => {
    expect(props[ENTITY_PROPERTY.faction].range).toEqual([0, FACTIONS.length]);
    expect(props[ENTITY_PROPERTY.faction].client_sync).toBe(true);
    FACTIONS.forEach((f, i) => expect(f.propertyIndex).toBe(i + 1));
    const skins = renderControllers.get("controller.render.aoc.faction_unit").arrays.textures["Array.faction_skins"];
    expect(skins.length).toBe(FACTIONS.length + 1);
    const textures = rpEntities.get(ENTITY.spearman)["minecraft:client_entity"].description.textures;
    for (const s of skins) expect(textures[s.replace("Texture.", "")], s).toBeDefined();
  });

  it("order property values match the script's states", () => {
    expect(props[ENTITY_PROPERTY.order].values).toEqual(Object.values(ORDER_STATE));
  });

  it("has one channel filter pair per configured move channel", () => {
    const moving = JSON.stringify(unit.component_groups["aoc:moving"]);
    for (let ch = 0; ch < DEFAULT_CONFIG.moveChannelCount; ch++) expect(moving).toContain(`"${channelTag(ch)}"`);
    expect(moving).not.toContain(`"${channelTag(DEFAULT_CONFIG.moveChannelCount)}"`);
    expect(moving).toContain(`"${FAMILY.rallyMarker}"`);
  });

  it("hostility and friendly-fire filters mention every faction", () => {
    const idle = JSON.stringify(unit.component_groups["aoc:idle"]);
    for (const f of FACTIONS) expect(idle).toContain(`"${factionTag(f.key)}"`);
  });

  it("stats match UnitDef", () => {
    const def = UNITS.find((u) => u.entityId === ENTITY.spearman)!;
    expect(unit.components["minecraft:health"].max).toBe(def.maxHealth);
    expect(unit.components["minecraft:health"].value).toBe(def.maxHealth);
    expect(unit.components["minecraft:attack"].damage).toBe(def.meleeDamage);
    expect(unit.components["minecraft:movement"].value).toBe(def.movementSpeed);
    expect(unit.components["minecraft:type_family"].family).toContain(FAMILY.unit);
  });

  it("can follow an order to its maximum distance, and safety timers outlast the script timeout", () => {
    expect(unit.components["minecraft:follow_range"].value).toBeGreaterThanOrEqual(DEFAULT_CONFIG.maxOrderDistance);
    const nat = unit.component_groups["aoc:moving"]["minecraft:behavior.nearest_attackable_target"];
    expect(nat.entity_types[0].max_dist).toBeGreaterThanOrEqual(DEFAULT_CONFIG.maxOrderDistance);
    const timeoutSecs = DEFAULT_CONFIG.orderTimeoutTicks / 20;
    expect(unit.component_groups["aoc:moving"]["minecraft:timer"].time).toBeGreaterThan(timeoutSecs);
    const marker = bpEntities.get(ENTITY.rallyMarker)["minecraft:entity"];
    expect(marker.components["minecraft:timer"].time).toBeGreaterThan(timeoutSecs);
    expect(marker.components["minecraft:type_family"].family).toContain(FAMILY.rallyMarker);
  });
});

describe("items", () => {
  const item = readJson(join(BP, "items", "commander_map.json"))["minecraft:item"];
  const atlas = readJson(join(RP, "textures", "item_texture.json"));

  it("commander map uses the script component, a known icon and a localized name", () => {
    expect(item.description.identifier).toBe(ITEM.commanderMap);
    expect(item.components[ITEM_COMPONENT.commanderMap]).toBeDefined();
    const icon = item.components["minecraft:icon"].textures.default;
    const tex = atlas.texture_data[icon]?.textures;
    expect(tex, icon).toBeDefined();
    expect(existsSync(join(RP, `${tex}.png`))).toBe(true);
    expect(lang.has(item.components["minecraft:display_name"].value)).toBe(true);
  });

  it("recipe produces the commander map", () => {
    const r = readJson(join(BP, "recipes", "commander_map.json"))["minecraft:recipe_shapeless"];
    expect(r.result.item).toBe(ITEM.commanderMap);
  });
});

describe("script bundle", () => {
  const bundle = join(BP, "scripts", "main.js");
  it.runIf(existsSync(bundle))("imports only the game modules declared in the manifest", () => {
    const src = readFileSync(bundle, "utf8");
    const imports = [...src.matchAll(/^import .* from "([^"]+)";?$/gm)].map((m) => m[1]);
    const declared = new Set(bpManifest.dependencies.map((d: Json) => d.module_name).filter(Boolean));
    expect(imports.length).toBeGreaterThan(0);
    for (const i of imports) expect(declared.has(i), i).toBe(true);
  });
});
