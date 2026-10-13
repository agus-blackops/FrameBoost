package com.frameboost.ragdoll;

/** Catalogue of tools and spawnable items, grouped in categories. */
final class Tools {
    static final int HAND = 0, DELETE = 1, FREEZE = 2, CUT = 3, ROPE = 4, WELD = 5, HINGE = 6, ACTIVATE = 7, ROTATE = 8, INSPECT = 9,
            SYR_HEAL = 70, SYR_ADRENALINE = 71, SYR_LIFE = 72, SYR_ZOMBIE = 73, SYR_ACID = 74, SYR_SEDATIVE = 75,
            GUN_PISTOL = 80, GUN_RIFLE = 81, GUN_MINIGUN = 82, C4 = 83,
            MOTOR = 60, THRUSTER = 61,
            HUMAN = 10, GIANT = 11, KID = 12, CROWD = 13, ROBOT = 14, ZOMBIE = 15, SKELETON = 16,
            WHEEL = 17, BOWLING = 18, MELON = 19,
            CRATE = 20, BIGCRATE = 21, PLANK = 22, BARREL = 23, BALL = 24, BALLOON = 25, STEEL = 26, PLATFORM = 27,
            GRENADE = 30, DYNAMITE = 31, TNT = 32, XBARREL = 33, DETONATE = 34, NUKE = 35,
            PISTOL = 40, RIFLE = 41, SHOTGUN = 42, ROCKET = 43, KNIFE = 44, SWORD = 45,
            FIRE = 50, LIGHTNING = 51, PUSH = 52,
            MOD_CREATE = 999, MOD_BASE = 1000, STYLE_BASE = 2000, STYLE_TEX = 2100;

    static final class Item {
        final int id;
        final String name, hint;
        Item(int id, String name, String hint) { this.id = id; this.name = name; this.hint = hint; }
    }

    static final String[] CAT_NAMES = {"Herramientas", "Gente", "Objetos", "Explosivos", "Armas", "Efectos", "Jeringas", "Taller", "Estilo", "Desafío"};
    static final int CAT_SYRINGES = 6, CAT_MODS = 7, CAT_STYLE = 8, CAT_CHAL = 9;

    static final String PLACE = "Toca para colocar";

    static Item[][] CATS = {
        {
            new Item(HAND, "Mano", "Arrastra lo que quieras · 2 dedos: mover y zoom"),
            new Item(DELETE, "Borrar", "Toca o desliza sobre algo para borrarlo"),
            new Item(FREEZE, "Clavar", "Toca una pieza para clavarla (otra vez para soltarla)"),
            new Item(CUT, "Cortar", "Desliza para cortar articulaciones, cuerdas y estructuras"),
            new Item(INSPECT, "Inspeccionar", "Toca a una persona para ver el estado de cada parte del cuerpo"),
            new Item(ROTATE, "Girar", "Arrastra alrededor de un objeto para girarlo"),
            new Item(ROPE, "Cuerda", "Toca dos puntos para unirlos con una cuerda (en el aire = gancho)"),
            new Item(WELD, "Soldar", "Toca dos piezas para unirlas de forma rígida"),
            new Item(HINGE, "Eje", "Toca dos piezas para unirlas con un eje que gira (ruedas, bisagras)"),
            new Item(ACTIVATE, "Activar", "Toca motores, propulsores, explosivos... o personas para que salten"),
        },
        {
            new Item(HUMAN, "Humano", PLACE),
            new Item(GIANT, "Gigante", PLACE),
            new Item(KID, "Niño", PLACE),
            new Item(CROWD, "Multitud", PLACE),
            new Item(ROBOT, "Robot", PLACE + " · sin sangre, suelta chispas"),
            new Item(ZOMBIE, "Zombi", PLACE + " · sangre verde"),
            new Item(SKELETON, "Esqueleto", PLACE),
        },
        {
            new Item(CRATE, "Caja", PLACE),
            new Item(BIGCRATE, "Caja grande", PLACE),
            new Item(PLANK, "Tabla", PLACE),
            new Item(BARREL, "Barril", PLACE),
            new Item(BALL, "Pelota", PLACE),
            new Item(BALLOON, "Globo", PLACE),
            new Item(WHEEL, "Rueda", PLACE + " · rueda de camión"),
            new Item(MOTOR, "Rueda motor", PLACE + " · arranca al ponerle un Eje (o con Activar)"),
            new Item(THRUSTER, "Propulsor", PLACE + " · enciéndelo con Activar; únelo con Soldar"),
            new Item(BOWLING, "Bolos", PLACE + " · bola pesadísima"),
            new Item(MELON, "Sandía", PLACE + " · revienta al golpear"),
            new Item(STEEL, "Bloque acero", PLACE),
            new Item(PLATFORM, "Plataforma", "Toca para colocar una plataforma fija"),
        },
        {
            new Item(GRENADE, "Granada", "Toca para colocar · explota en 3 s"),
            new Item(DYNAMITE, "Dinamita", "Toca para colocar · explota en 4 s"),
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
            new Item(GUN_PISTOL, "Pistola (obj.)", PLACE + " · dispara con Activar; suéldala a algo o a una mano"),
            new Item(GUN_RIFLE, "Rifle (obj.)", PLACE + " · dispara con Activar · atraviesa"),
            new Item(GUN_MINIGUN, "Ametralladora", PLACE + " · Activar la enciende y la apaga"),
            new Item(KNIFE, "Cuchillo", PLACE + " · agarra y apuñala"),
            new Item(SWORD, "Espada", PLACE + " · agarra y corta"),
        },
        {
            new Item(FIRE, "Fuego", "Desliza sobre algo para prenderle fuego"),
            new Item(LIGHTNING, "Rayo", "Toca para lanzar un rayo"),
            new Item(PUSH, "Onda", "Toca para empujar con una onda de choque"),
        },
        {
            new Item(SYR_HEAL, "Curación", "Toca a alguien: cura heridas, huesos rotos y quemaduras"),
            new Item(SYR_ADRENALINE, "Adrenalina", "Toca a alguien: lo despierta y lo hace casi imparable"),
            new Item(SYR_LIFE, "Resurrección", "Toca a alguien muerto (con cabeza) para revivirlo"),
            new Item(SYR_ZOMBIE, "Virus zombi", "Toca a alguien: en segundos será un zombi"),
            new Item(SYR_ACID, "Ácido", "Toca a alguien: lo disuelve hasta dejar el esqueleto"),
            new Item(SYR_SEDATIVE, "Sedante", "Toca a alguien: se duerme un buen rato"),
        },
        {}, {}, {},
    };

    static final String[] THEMES = {"Habitación", "Laboratorio", "Noche", "Ladrillos", "Neón", "Desierto"};

    /** Rebuilds the two dynamic categories (workshop mods and visual styles). */
    static void rebuild(ModEngine mods) {
        Item[] m = new Item[mods.mods.size() + 1];
        m[0] = new Item(MOD_CREATE, "+ Crear mod", "Escribe tu propio mod · mantén pulsado un mod tuyo para editarlo");
        for (int i = 0; i < mods.mods.size(); i++) {
            Mod mod = mods.mods.get(i);
            m[i + 1] = new Item(MOD_BASE + i, mod.name, mod.desc.isEmpty() ? "Toca para activar o desactivar" : mod.desc);
        }
        CATS[CAT_MODS] = m;
        Item[] st = new Item[THEMES.length + 1];
        for (int i = 0; i < THEMES.length; i++) st[i] = new Item(STYLE_BASE + i, THEMES[i], "Cambia el fondo de la sala");
        st[THEMES.length] = new Item(STYLE_TEX, "Texturas", "Activa o desactiva vetas, remaches, rayas y adornos");
        CATS[CAT_STYLE] = st;
    }

    static Item find(int id) {
        for (Item[] cat : CATS) for (Item it : cat) if (it.id == id) return it;
        return CATS[0][0];
    }

    static boolean isSpawn(int id) {
        return (id >= HUMAN && id <= STEEL + 1) || (id >= GRENADE && id <= XBARREL) || id == KNIFE || id == SWORD
                || id == MOTOR || id == THRUSTER || (id >= GUN_PISTOL && id <= C4);
    }

    /** Item keys usable from mod scripts. */
    static final String[] KEYS = {"human", "giant", "kid", "robot", "zombie", "skeleton", "crate", "bigcrate", "plank", "barrel", "ball",
            "balloon", "steel", "wheel", "bowling", "melon", "grenade", "dynamite", "tnt", "xbarrel", "knife", "sword", "platform",
            "motor", "thruster", "pistolgun", "riflegun", "minigun", "c4"};
    static final int[] KEY_IDS = {HUMAN, GIANT, KID, ROBOT, ZOMBIE, SKELETON, CRATE, BIGCRATE, PLANK, BARREL, BALL,
            BALLOON, STEEL, WHEEL, BOWLING, MELON, GRENADE, DYNAMITE, TNT, XBARREL, KNIFE, SWORD, PLATFORM, MOTOR, THRUSTER, GUN_PISTOL, GUN_RIFLE, GUN_MINIGUN, C4};

    static int keyId(String k) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(k)) return KEY_IDS[i];
        return -1;
    }

    static boolean isSyringe(int id) { return id >= SYR_HEAL && id <= SYR_SEDATIVE; }

    static boolean isWeapon(int id) {
        return id == PISTOL || id == RIFLE || id == SHOTGUN || id == ROCKET;
    }
}
