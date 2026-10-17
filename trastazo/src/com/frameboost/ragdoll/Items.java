package com.frameboost.ragdoll;

/** Catalogue of tools and things to place, grouped in palettes. */
final class Items {
    private Items() { }

    static final int HAND = 0, DELETE = 1, FREEZE = 2, CUT = 3, INSPECT = 4, ROPE = 5, WELD = 6, HINGE = 7, ACTIVATE = 8,
            HUMAN = 10, GIANT = 11, KID = 12, CROWD = 13, ROBOT = 14, ZOMBIE = 15, SKELETON = 16, SOLDIER = 17, MEDIC = 18, FIREFIGHTER = 19, THUG = 20,
            CRATE = 30, BIGCRATE = 31, PLANK = 32, BARREL = 33, BALL = 34, BALLOON = 35, WHEEL = 36, MOTOR = 37, THRUSTER = 38, BOWLING = 39,
            MELON = 40, STEEL = 41, PLATFORM = 42,
            GRENADE = 50, DYNAMITE = 51, TNT = 52, XBARREL = 53, C4 = 54, DETONATE = 55, NUKE = 56,
            PISTOL = 60, RIFLE = 61, SHOTGUN = 62, ROCKET = 63, KNIFE = 64, SWORD = 65, GUN_PISTOL = 66, GUN_RIFLE = 67, GUN_MG = 68, TURRET = 69, DRONE = 70,
            FIRE = 80, LIGHTNING = 81, PUSH = 82,
            SYR_HEAL = 90, SYR_ADRENALINE = 91, SYR_LIFE = 92, SYR_ZOMBIE = 93, SYR_ACID = 94, SYR_SEDATIVE = 95,
            ROPE_ITEM = 100, ANCHOR_ITEM = 101, HOUSE = 110, BRIDGE = 111, TOWER = 112, WALL = 113, CAR = 114, DOG = 120, PIG = 121, RAT = 122, POOP = 123, BLOCK = 130, MOD_CREATE = 999, MOD_BASE = 1000, STYLE_BASE = 2000;

    static final class Item {
        final int id;
        final String name, hint;
        Item(int id, String name, String hint) { this.id = id; this.name = name; this.hint = hint; }
    }

    static final String PLACE = "Toca para colocar";
    static final String[] CAT_NAMES = {"Herramientas", "Gente", "Objetos", "Explosivos", "Armas", "Efectos", "Jeringas", "Taller", "Estilo", "Desafío", "Construir", "Animales"};
    static final int CAT_TOOLS = 0, CAT_PEOPLE = 1, CAT_OBJECTS = 2, CAT_EXPLOSIVES = 3, CAT_WEAPONS = 4, CAT_FX = 5, CAT_SYRINGES = 6, CAT_MODS = 7, CAT_STYLE = 8, CAT_CHAL = 9, CAT_BUILD = 10, CAT_ANIMALS = 11;
    /** Tabs shown in the normal sandbox, the constructions first (the easiest way to start building). */
    static final int[] PALETTE = {CAT_BUILD, 0, 1, CAT_ANIMALS, 2, 3, 4, 5, 6, 7, 8};

    static Item[][] CATS = {
        {
            new Item(DELETE, "Borrar", "Toca o desliza sobre algo para borrarlo"),
            new Item(FREEZE, "Clavar", "Toca una pieza para fijarla en el aire (otra vez para soltarla)"),
            new Item(CUT, "Cortar", "Desliza para cortar articulaciones, cuerdas y uniones"),
            new Item(INSPECT, "Inspeccionar", "Toca a una persona: estado de cada parte del cuerpo"),
            new Item(ROPE, "Cuerda", "Toca dos puntos para unirlos con una cuerda"),
            new Item(WELD, "Soldar", "Toca dos piezas para unirlas de forma rígida"),
            new Item(HINGE, "Eje", "Toca dos piezas para unirlas con un eje que gira (ruedas, bisagras)"),
            new Item(ACTIVATE, "Activar", "Motores, propulsores, armas, explosivos... o personas (saltan)"),
        },
        {
            new Item(HUMAN, "Humano", PLACE),
            new Item(GIANT, "Gigante", PLACE),
            new Item(KID, "Niño", PLACE),
            new Item(CROWD, "Multitud", PLACE),
            new Item(ROBOT, "Robot", PLACE + " · sin sangre, suelta chispas"),
            new Item(ZOMBIE, "Zombi", PLACE + " · caza y contagia"),
            new Item(SKELETON, "Esqueleto", PLACE),
            new Item(SOLDIER, "Soldado", PLACE + " · IA: dispara a zombis y matones, nunca a civiles"),
            new Item(MEDIC, "Médico", PLACE + " · IA: cura heridos y reanima a los muertos"),
            new Item(FIREFIGHTER, "Bombero", PLACE + " · IA: apaga lo que arde"),
            new Item(THUG, "Matón", PLACE + " · IA: busca pelea con quien pille"),
        },
        {
            new Item(CRATE, "Caja", PLACE),
            new Item(BIGCRATE, "Caja grande", PLACE),
            new Item(PLANK, "Tabla", PLACE),
            new Item(BARREL, "Barril", PLACE),
            new Item(BALL, "Pelota", PLACE),
            new Item(BALLOON, "Globo", PLACE + " · flota"),
            new Item(WHEEL, "Rueda", PLACE),
            new Item(MOTOR, "Rueda motor", PLACE + " · se activa al unirla con Eje; Activar cambia el sentido"),
            new Item(THRUSTER, "Propulsor", PLACE + " · enciéndelo con Activar; únelo con Soldar"),
            new Item(BOWLING, "Bolos", PLACE + " · bola pesadísima"),
            new Item(MELON, "Sandía", PLACE + " · revienta al golpear"),
            new Item(STEEL, "Bloque acero", PLACE),
            new Item(PLATFORM, "Plataforma", "Toca para colocar una plataforma fija"),
        },
        {
            new Item(GRENADE, "Granada", PLACE + " · explota en 3 s"),
            new Item(DYNAMITE, "Dinamita", PLACE + " · explota en 4 s"),
            new Item(TNT, "TNT", "Explota con fuego, golpes o explosiones"),
            new Item(XBARREL, "Barril rojo", "Explota con fuego, golpes o explosiones"),
            new Item(C4, "C4", PLACE + " · se pega a lo que toca; Activar uno detona todos"),
            new Item(DETONATE, "Detonar", "Toca para provocar una explosión"),
            new Item(NUKE, "Nuclear", "Toca para una explosión gigantesca"),
        },
        {
            new Item(PISTOL, "Pistola", "Toca para disparar al punto"),
            new Item(RIFLE, "Rifle", "Mantén pulsado para ráfaga · atraviesa"),
            new Item(SHOTGUN, "Escopeta", "Toca para disparar perdigones"),
            new Item(ROCKET, "Cohete", "Toca para lanzar un cohete explosivo"),
            new Item(KNIFE, "Cuchillo", PLACE + " · agarra y apuñala"),
            new Item(SWORD, "Espada", PLACE + " · agarra y corta"),
            new Item(GUN_PISTOL, "Pistola (obj.)", PLACE + " · dispara con Activar; suéldala a algo"),
            new Item(GUN_RIFLE, "Rifle (obj.)", PLACE + " · dispara con Activar · atraviesa"),
            new Item(GUN_MG, "Ametralladora", PLACE + " · Activar la enciende y la apaga"),
            new Item(TURRET, "Torreta", PLACE + " · IA: dispara sola a zombis y matones"),
            new Item(DRONE, "Dron", PLACE + " · IA: patrulla y ataca desde el aire"),
        },
        {
            new Item(FIRE, "Fuego", "Desliza sobre algo para prenderle fuego"),
            new Item(LIGHTNING, "Rayo", "Toca para lanzar un rayo"),
            new Item(PUSH, "Onda", "Toca para empujar con una onda de choque"),
        },
        {
            new Item(SYR_HEAL, "Curación", "Cura heridas, huesos rotos y quemaduras"),
            new Item(SYR_ADRENALINE, "Adrenalina", "Lo despierta y lo hace casi imparable"),
            new Item(SYR_LIFE, "Resurrección", "Revive a alguien muerto (con cabeza)"),
            new Item(SYR_ZOMBIE, "Virus zombi", "En segundos será un zombi"),
            new Item(SYR_ACID, "Ácido", "Lo disuelve hasta dejar el esqueleto"),
            new Item(SYR_SEDATIVE, "Sedante", "Se duerme un buen rato"),
        },
        {}, {}, {},
        {
            new Item(HOUSE, "Casa", PLACE + " · paredes, suelo y tejado unidos; se derrumba con un golpe fuerte"),
            new Item(BRIDGE, "Puente", PLACE + " · tablero sobre dos pilares"),
            new Item(TOWER, "Torre", PLACE + " · ladrillos apilados; tirarla es divertido"),
            new Item(WALL, "Muro", PLACE + " · un muro de ladrillos para derribar"),
            new Item(CAR, "Coche", PLACE + " · un coche con motor que arranca solo"),
        },
        {
            new Item(DOG, "Perro", PLACE + " · pasea, ladra a los zombis y se come a los muertos"),
            new Item(PIG, "Cerdo", PLACE + " · lento y glotón: se lo come todo"),
            new Item(RAT, "Rata", PLACE + " · pequeña, rápida y carroñera"),
            new Item(POOP, "Caca", PLACE + " · huele fatal"),
        },
    };

    static final String[] THEMES = {"Habitación", "Laboratorio", "Noche", "Ladrillos", "Neón", "Desierto", "Sandbox"};

    static void rebuild(java.util.List<String[]> mods) {
        Item[] m = new Item[mods.size() + 1];
        m[0] = new Item(MOD_CREATE, "+ Crear mod", "Escribe tu propio mod · mantén pulsado un mod tuyo para editarlo");
        for (int i = 0; i < mods.size(); i++) m[i + 1] = new Item(MOD_BASE + i, mods.get(i)[0], mods.get(i)[1]);
        CATS[CAT_MODS] = m;
        Item[] st = new Item[THEMES.length];
        for (int i = 0; i < THEMES.length; i++) st[i] = new Item(STYLE_BASE + i, THEMES[i], "Cambia el fondo de la sala");
        CATS[CAT_STYLE] = st;
    }

    /** The hand is not in any palette: it is always active. This is what the HUD shows with no tool picked. */
    static final Item HAND_ITEM = new Item(HAND, "Mano", "Arrastra cualquier cosa · mantén pulsado: menú · elige algo a la izquierda");

    static Item find(int id) {
        if (id == HAND) return HAND_ITEM;
        for (Item[] cat : CATS) for (Item it : cat) if (it.id == id) return it;
        return HAND_ITEM;
    }

    static boolean isBuild(int id) { return id >= HOUSE && id <= CAR; }
    static boolean isAnimal(int id) { return id >= DOG && id <= RAT; }

    static boolean isSpawn(int id) {
        return isBuild(id) || (id >= HUMAN && id <= PLATFORM) || (id >= GRENADE && id <= XBARREL) || id == C4 || id == KNIFE || id == SWORD
                || (id >= GUN_PISTOL && id <= DRONE) || (id >= DOG && id <= POOP);
    }

    static boolean isSyringe(int id) { return id >= SYR_HEAL && id <= SYR_SEDATIVE; }
    static boolean isShot(int id) { return id == PISTOL || id == RIFLE || id == SHOTGUN || id == ROCKET; }

    static final String[] KEYS = {"human", "giant", "kid", "robot", "zombie", "skeleton", "soldier", "medic", "firefighter", "thug", "crate", "bigcrate",
            "plank", "barrel", "ball", "balloon", "steel", "wheel", "motor", "thruster", "bowling", "melon", "grenade", "dynamite", "tnt", "xbarrel",
            "knife", "sword", "platform", "c4", "pistolgun", "riflegun", "minigun", "turret", "drone", "dog", "pig", "rat", "poop"};
    static final int[] KEY_IDS = {HUMAN, GIANT, KID, ROBOT, ZOMBIE, SKELETON, SOLDIER, MEDIC, FIREFIGHTER, THUG, CRATE, BIGCRATE,
            PLANK, BARREL, BALL, BALLOON, STEEL, WHEEL, MOTOR, THRUSTER, BOWLING, MELON, GRENADE, DYNAMITE, TNT, XBARREL,
            KNIFE, SWORD, PLATFORM, C4, GUN_PISTOL, GUN_RIFLE, GUN_MG, TURRET, DRONE, DOG, PIG, RAT, POOP};

    static int keyId(String k) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(k)) return KEY_IDS[i];
        return -1;
    }
}
