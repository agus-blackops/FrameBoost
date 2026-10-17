"""Every piece of text in the add-on, in Spanish and English.

build.py writes these to the .lang files of both packs (es_ES and es_MX get
the Spanish, en_US and en_GB the English). `\n` is a line break in forms.
"""

from lang_kept import KEPT

S = {
    # -------------------------------------------- pack, entities, items, acts
    "pack.name": (
        "§4Horror Legends: §cRemastered§r",
        "§4Horror Legends: §cRemastered§r",
    ),
    "pack.description": (
        "Terror en tres actos con 15 leyendas: HerobrineGamer788, el Morador, el Mimic, la Estatua que Llora, Herobrine, el Hombre Cabra, el Hombre de la Niebla, el Skinwalker, la Sombra, el Ahogado, null, Entity 303, Siren Head, el Wendigo y el Minero Hueco.",
        "Horror in three acts with 15 legends: HerobrineGamer788, the Cave Dweller, the Mimic, the Weeping Statue, Herobrine, the Goatman, the Man From The Fog, the Skinwalker, the Shadow, the Drowned One, null, Entity 303, Siren Head, the Wendigo and the Hollow Miner.",
    ),
    "entity.hl:herobrine.name": (
        "Herobrine",
        "Herobrine",
    ),
    "entity.hl:herobrine_boss.name": (
        "Herobrine, el Minero Hueco",
        "Herobrine, the Hollow Miner",
    ),
    "entity.hl:null.name": (
        "null",
        "null",
    ),
    "entity.hl:fog_man.name": (
        "El Hombre de la Niebla",
        "The Man From The Fog",
    ),
    "entity.hl:cave_dweller.name": (
        "El Morador de las Cuevas",
        "The Cave Dweller",
    ),
    "entity.hl:fake_player.name": (
        "HerobrineGamer788",
        "HerobrineGamer788",
    ),
    "entity.hl:siren_head.name": (
        "Siren Head",
        "Siren Head",
    ),
    "entity.hl:goatman.name": (
        "El Hombre Cabra",
        "The Goatman",
    ),
    "entity.hl:weeping_statue.name": (
        "La Estatua que Llora",
        "The Weeping Statue",
    ),
    "entity.hl:mimic.name": (
        "El Mimic",
        "The Mimic",
    ),
    "entity.hl:shadow.name": (
        "La Sombra",
        "The Shadow",
    ),
    "entity.hl:skinwalker.name": (
        "El Skinwalker",
        "The Skinwalker",
    ),
    "entity.hl:wendigo.name": (
        "El Wendigo",
        "The Wendigo",
    ),
    "entity.hl:entity_303.name": (
        "Entity 303",
        "Entity 303",
    ),
    "entity.hl:drowned_one.name": (
        "El Ahogado",
        "The Drowned One",
    ),
    "item.spawn_egg.entity.hl:herobrine.name": (
        "Generar: Herobrine",
        "Spawn Herobrine",
    ),
    "item.spawn_egg.entity.hl:herobrine_boss.name": (
        "Generar: Herobrine, el Minero Hueco",
        "Spawn Herobrine, the Hollow Miner",
    ),
    "item.spawn_egg.entity.hl:null.name": (
        "Generar: null",
        "Spawn null",
    ),
    "item.spawn_egg.entity.hl:fog_man.name": (
        "Generar: El Hombre de la Niebla",
        "Spawn The Man From The Fog",
    ),
    "item.spawn_egg.entity.hl:cave_dweller.name": (
        "Generar: El Morador de las Cuevas",
        "Spawn The Cave Dweller",
    ),
    "item.spawn_egg.entity.hl:fake_player.name": (
        "Generar: HerobrineGamer788",
        "Spawn HerobrineGamer788",
    ),
    "item.spawn_egg.entity.hl:siren_head.name": (
        "Generar: Siren Head",
        "Spawn Siren Head",
    ),
    "item.spawn_egg.entity.hl:goatman.name": (
        "Generar: El Hombre Cabra",
        "Spawn The Goatman",
    ),
    "item.spawn_egg.entity.hl:weeping_statue.name": (
        "Generar: La Estatua que Llora",
        "Spawn The Weeping Statue",
    ),
    "item.spawn_egg.entity.hl:mimic.name": (
        "Generar: El Mimic",
        "Spawn The Mimic",
    ),
    "item.spawn_egg.entity.hl:shadow.name": (
        "Generar: La Sombra",
        "Spawn The Shadow",
    ),
    "item.spawn_egg.entity.hl:skinwalker.name": (
        "Generar: El Skinwalker",
        "Spawn The Skinwalker",
    ),
    "item.spawn_egg.entity.hl:wendigo.name": (
        "Generar: El Wendigo",
        "Spawn The Wendigo",
    ),
    "item.spawn_egg.entity.hl:entity_303.name": (
        "Generar: Entity 303",
        "Spawn Entity 303",
    ),
    "item.spawn_egg.entity.hl:drowned_one.name": (
        "Generar: El Ahogado",
        "Spawn The Drowned One",
    ),
    "item.hl:journal.name": (
        "Diario del Superviviente",
        "Survivor's Journal",
    ),
    "item.hl:flashlight.name": (
        "Linterna",
        "Flashlight",
    ),
    "item.hl:battery.name": (
        "Pila",
        "Battery",
    ),
    "item.hl:hollow_pickaxe.name": (
        "§4Pico del Minero Hueco",
        "§4Pickaxe of the Hollow Miner",
    ),
    "item.hl:page_1.name": (
        "Página arrancada I",
        "Torn Page I",
    ),
    "item.hl:page_2.name": (
        "Página arrancada II",
        "Torn Page II",
    ),
    "item.hl:page_3.name": (
        "Página arrancada III",
        "Torn Page III",
    ),
    "item.hl:page_4.name": (
        "Página arrancada IV",
        "Torn Page IV",
    ),
    "item.hl:page_5.name": (
        "Página arrancada V",
        "Torn Page V",
    ),
    "item.hl:page_6.name": (
        "Página arrancada VI",
        "Torn Page VI",
    ),
    "item.hl:page_7.name": (
        "Página arrancada VII",
        "Torn Page VII",
    ),
    "tile.hl:corrupted_block.name": (
        "Bloque Corrupto",
        "Corrupted Block",
    ),
    "tile.hl:ward_lantern.name": (
        "Farol Protector",
        "Ward Lantern",
    ),
    "hl.intro.1": (
        "§8[???]§r §7Este mundo no está tan vacío como parece. Cada día que pase, algo más se dará cuenta de que estás aquí.",
        "§8[???]§r §7This world is not as empty as it looks. Every day you stay, something else notices you're here.",
    ),
    "hl.intro.2": (
        "§8[???]§r §7Lee el §fDiario§7. Fabrica una §fLinterna§7 y §fPilas§7. Un §fFarol Protector§7 mantiene tu casa a salvo... casi siempre.",
        "§8[???]§r §7Read the §fJournal§7. Craft a §fFlashlight§7 and §fBatteries§7. A §fWard Lantern§7 keeps your home safe... mostly.",
    ),
    "hl.act.n": (
        "Acto %s",
        "Act %s",
    ),
    "hl.act.1.name": (
        "Visitantes",
        "Visitors",
    ),
    "hl.act.1.intro": (
        "§8[???]§r §7Estás solo en este mundo. ¿Verdad?",
        "§8[???]§r §7You're alone in this world. Aren't you?",
    ),
    "hl.act.1.text": (
        "Primero, sonidos que no están: pasos, puertas, tu nombre.\n\nLuego llegan los visitantes. §fHerobrineGamer788§r entra a tu partida. Bajo tierra, el §fMorador§r te acecha. Aparecen §fcofres§r donde no los había. Y en algún sitio hay una §festatua§r con la cara entre las manos que no estaba ahí ayer.",
        "First, sounds that aren't there: footsteps, doors, your name.\n\nThen the visitors come. §fHerobrineGamer788§r joins your game. Underground, the §fCave Dweller§r stalks you. §fChests§r turn up where there were none. And somewhere there's a §fstatue§r with its face in its hands that wasn't there yesterday.",
    ),
    "hl.act.2.name": (
        "El bosque",
        "The Woods",
    ),
    "hl.act.2.intro": (
        "§8[???]§r §7No salgas al bosque de noche.",
        "§8[???]§r §7Don't go into the woods at night.",
    ),
    "hl.act.2.text": (
        "§fHerobrine§r te observa con su cola de vértebras. Algo grita como una cabra entre los árboles. Llega la §fniebla§r. Una vaca te mira con ojos de persona. Un §fhombre con sombrero§r entra en tu casa. Y en el agua, algo te agarra del tobillo.",
        "§fHerobrine§r watches you with his tail of vertebrae. Something screams like a goat between the trees. The §ffog§r rolls in. A cow looks at you with human eyes. A §fman in a hat§r gets into your house. And in the water, something takes you by the ankle.",
    ),
    "hl.act.3.name": (
        "El error",
        "The Error",
    ),
    "hl.act.3.intro": (
        "§8[???]§r §7Algo se ha roto en el código. Y algo muy alto se acerca.",
        "§8[???]§r §7Something has broken in the code. And something very tall is coming.",
    ),
    "hl.act.3.text": (
        "§fnull§r corrompe el mundo. §fEntity 303§r entra a tu partida y juega con ella. Una sirena suena a lo lejos y algo más alto que los árboles la lleva en lugar de cabeza. En la nieve, algo te escucha respirar.\n\nCada cuatro días, la §4Noche Roja§r.\n\nSi has encontrado las siete páginas, ya sabes cómo acabar con esto.",
        "§fnull§r corrupts the world. §fEntity 303§r joins your game and plays with it. A siren wails far away, and something taller than the trees carries it instead of a head. In the snow, something listens to you breathe.\n\nEvery four days, the §4Red Night§r.\n\nIf you have found all seven pages, you know how to end this.",
    ),
    "hl.arrive.ambience": (
        "§8§o...",
        "§8§o...",
    ),
    "hl.arrive.gamer": (
        "§8[???]§r §7Alguien más ha encontrado tu mundo.",
        "§8[???]§r §7Someone else has found your world.",
    ),
    "hl.arrive.dweller": (
        "§8[???]§r §7Algo respira en las cuevas.",
        "§8[???]§r §7Something breathes in the caves.",
    ),
    "hl.arrive.mimic": (
        "§8[???]§r §7No abras cofres que no hayas puesto tú.",
        "§8[???]§r §7Don't open chests you didn't place.",
    ),
    "hl.arrive.statue": (
        "§8[???]§r §7No parpadees.",
        "§8[???]§r §7Don't blink.",
    ),
    "hl.arrive.herobrine": (
        "§8[???]§r §7Él te ha visto.",
        "§8[???]§r §7He has seen you.",
    ),
    "hl.arrive.goatman": (
        "§8[???]§r §7Esta noche se oyen cabras en el bosque. No hay cabras en el bosque.",
        "§8[???]§r §7There are goats in the woods tonight. There are no goats in the woods.",
    ),
    "hl.arrive.fog": (
        "§8[???]§r §7Habrá niebla. No la mires.",
        "§8[???]§r §7There will be fog. Don't look into it.",
    ),
    "hl.arrive.skinwalker": (
        "§8[???]§r §7Cuenta las vacas. Ayer había una menos.",
        "§8[???]§r §7Count the cows. Yesterday there was one fewer.",
    ),
    "hl.arrive.shadow": (
        "§8[???]§r §7Ya no estás solo en tu casa.",
        "§8[???]§r §7You're no longer alone in your house.",
    ),
    "hl.arrive.drowned": (
        "§8[???]§r §7No te bañes de noche.",
        "§8[???]§r §7Don't swim at night.",
    ),
    "hl.arrive.null": (
        "§8[???]§r §7null",
        "§8[???]§r §7null",
    ),
    "hl.arrive.e303": (
        "§8[???]§r §7Alguien tiene permisos de administrador en tu mundo. No eres tú.",
        "§8[???]§r §7Someone has operator permissions in your world. It isn't you.",
    ),
    "hl.arrive.siren": (
        "§8[???]§r §7Si oyes una sirena, no la busques.",
        "§8[???]§r §7If you hear a siren, don't go looking for it.",
    ),
    "hl.arrive.wendigo": (
        "§8[???]§r §7Hace frío. Algo tiene hambre. No corras.",
        "§8[???]§r §7It's cold. Something is hungry. Don't run.",
    ),
    "hl.arrive.redNight": (
        "§8[???]§r §7La luna se está volviendo roja.",
        "§8[???]§r §7The moon is turning red.",
    ),
    # ------------------------------------------------------------- journal
    "hl.j.title": ("Diario del Superviviente", "Survivor's Journal"),
    "hl.j.day": ("§7Día §f%s§7 del encantamiento", "§7Day §f%s§7 of the haunting"),
    "hl.j.fear": ("§7Miedo:", "§7Fear:"),
    "hl.j.pages_count": ("§7Páginas: §f%s/%s", "§7Pages: §f%s/%s"),
    "hl.j.peace": ("§aHas roto la maldición. Descansa... unos días.", "§aYou broke the curse. Rest... for a few days."),
    "hl.j.wins": ("§7Veces que has vencido a Herobrine: §f%s", "§7Times you have beaten Herobrine: §f%s"),
    "hl.j.bestiary": ("Bestiario", "Bestiary"),
    "hl.j.bestiary_body": ("Todo lo que has visto. Lo que no, aún es ???", "Everything you have seen. What you haven't is still ???"),
    "hl.j.unknown": ("Aún no lo has visto. Aún.", "You haven't seen it yet. Yet."),
    "hl.j.pages": ("Páginas arrancadas", "Torn Pages"),
    "hl.j.pages_body": (
        "Has leído §f%s de %s§r páginas de un diario que no es tuyo. Siete de ellas guardan una y la dejan cuando sobrevives.",
        "You have read §f%s of %s§r pages of a diary that isn't yours. Seven of them each keep one and leave it when you live through them.",
    ),
    "hl.j.page_missing": ("§8Página %s: perdida", "§8Page %s: missing"),
    "hl.j.ritual": ("§4El Ritual", "§4The Ritual"),
    "hl.j.settings": ("Ajustes", "Settings"),
    "hl.j.settings_body": ("Pulsa una amenaza para activarla o desactivarla. Cada una dice en cuántos días llega.", "Tap a threat to switch it on or off. Each says how many days until it arrives."),
    "hl.j.intensity": ("Intensidad", "Intensity"),
    "hl.j.jumpscares": ("Sustos fuertes", "Jumpscares"),
    "hl.j.tests": ("Pruebas", "Tests"),
    "hl.j.tests_body": ("Provoca cualquier cosa al momento, para verla sin esperar días.", "Trigger anything right now, to see it without waiting days."),
    "hl.j.on": ("§aSÍ", "§aON"),
    "hl.j.off": ("§cNO", "§cOFF"),
    "hl.j.in_days": ("§8en %s días", "§8in %s days"),
    "hl.j.close": ("Cerrar", "Close"),
    "hl.j.back": ("Volver", "Back"),
    "hl.t.scare": ("Susto (prueba)", "Jumpscare (test)"),
    "hl.t.items": ("Darme objetos (linterna, pilas, farol, páginas)", "Give me items (flashlight, batteries, lantern, pages)"),
    "hl.t.ritual": ("Empezar el ritual ya", "Start the ritual now"),
    "hl.t.day": ("Avanzar un día", "Skip ahead a day"),

    # ---------------------------------------------------------------- acts
    "hl.j.acts": (
        "Actos",
        "Acts",
    ),
    "hl.j.acts_body": (
        "La historia hasta ahora.",
        "The story so far.",
    ),
    # ------------------------------------------------------------- threats
    "hl.threat.ambience": (
        "Sonidos",
        "Sounds",
    ),
    "hl.threat.gamer": (
        "HerobrineGamer788",
        "HerobrineGamer788",
    ),
    "hl.threat.dweller": (
        "El Morador de las Cuevas",
        "The Cave Dweller",
    ),
    "hl.threat.mimic": (
        "El Mimic",
        "The Mimic",
    ),
    "hl.threat.statue": (
        "La Estatua que Llora",
        "The Weeping Statue",
    ),
    "hl.threat.herobrine": (
        "Herobrine",
        "Herobrine",
    ),
    "hl.threat.goatman": (
        "El Hombre Cabra",
        "The Goatman",
    ),
    "hl.threat.fog": (
        "El Hombre de la Niebla",
        "The Man From The Fog",
    ),
    "hl.threat.skinwalker": (
        "El Skinwalker",
        "The Skinwalker",
    ),
    "hl.threat.shadow": (
        "La Sombra",
        "The Shadow",
    ),
    "hl.threat.drowned": (
        "El Ahogado",
        "The Drowned One",
    ),
    "hl.threat.null": (
        "null",
        "null",
    ),
    "hl.threat.e303": (
        "Entity 303",
        "Entity 303",
    ),
    "hl.threat.siren": (
        "Siren Head",
        "Siren Head",
    ),
    "hl.threat.wendigo": (
        "El Wendigo",
        "The Wendigo",
    ),
    "hl.threat.redNight": (
        "La Noche Roja",
        "The Red Night",
    ),
    "hl.threat.boss": (
        "§4El Minero Hueco",
        "§4The Hollow Miner",
    ),
    "hl.lore.ambience": (
        "§lSonidos§r\n\nPasos detrás de ti, puertas, cofres, minería lejana, el siseo de un creeper que no está, respiración, uñas arañando la pared, golpes, tu nombre susurrado.\n\n§eConsejo:§r no te des la vuelta. O sí.",
        "§lSounds§r\n\nFootsteps behind you, doors, chests, distant mining, the hiss of a creeper that isn't there, breathing, nails on the wall, knocking, your name whispered.\n\n§eTip:§r don't turn around. Or do.",
    ),
    "hl.lore.gamer": (
        "§lHerobrineGamer788§r\n§8(un jugador que no debería estar)§r\n\nEntra a tu mundo como cualquiera, con sus cascos con una luz roja que nunca parpadea. Juega, te copia, se vuelve raro y al final deja de fingir.\n\n§eConsejo:§r cuando se revele, busca en el suelo donde estaba.",
        "§lHerobrineGamer788§r\n§8(a player who shouldn't be here)§r\n\nHe joins your world like anyone else, wearing a headset whose red light never blinks. He plays, copies you, turns strange and in the end stops pretending.\n\n§eTip:§r when he reveals himself, look on the ground where he stood.",
    ),
    "hl.lore.dweller": (
        "§lEl Morador de las Cuevas§r\n§8(inspirado en Cave Dweller / From The Caves)§r\n\nUn cuerpo pálido y sin ojos sobre cuatro patas larguísimas, con las rodillas por encima del lomo. Su hocico de cuatro labios se abre como una flor sobre anillos de dientes. Huye si lo pillas mirando; si esperas demasiado, viene a por ti.\n\n§eConsejo:§r la linterna lo espanta. Sobrevive a él y dejará algo.",
        "§lThe Cave Dweller§r\n§8(inspired by Cave Dweller / From The Caves)§r\n\nA pale, eyeless body on four very long limbs, knees above its back. Its four-lipped snout opens like a flower over rings of teeth. It flees if you catch it looking; wait too long and it comes for you.\n\n§eTip:§r the flashlight drives it off. Live through it and it leaves something behind.",
    ),
    "hl.lore.mimic": (
        "§lEl Mimic§r\n§8(el cofre que muerde)§r\n\nUn cofre igual que los tuyos, donde antes no había nada. Si lo abres, la tapa es una boca llena de dientes y le salen seis patas.\n\n§eConsejo:§r se le puede matar, y suelta lo que se tragó: hierro, oro, esmeraldas... a veces un diamante.",
        "§lThe Mimic§r\n§8(the chest that bites)§r\n\nA chest just like yours, where there was nothing before. Open it and the lid is a mouth full of teeth, and six legs unfold.\n\n§eTip:§r it can be killed, and drops what it swallowed: iron, gold, emeralds... sometimes a diamond.",
    ),
    "hl.lore.statue": (
        "§lLa Estatua que Llora§r\n§8(inspirada en los Ángeles Llorosos y SCP-173)§r\n\nUna figura de piedra con la cara entre las manos. No se mueve mientras alguien la mira. Cada vez que apartas la vista, o parpadeas, está más cerca, con las manos más bajas y la boca más abierta.\n\n§eConsejo:§r no le quites los ojos de encima hasta que se canse. Un Farol Protector la detiene.",
        "§lThe Weeping Statue§r\n§8(inspired by the Weeping Angels and SCP-173)§r\n\nA stone figure with its face in its hands. It doesn't move while anyone is looking. Every time you look away, or blink, it's closer, its hands lower, its mouth wider.\n\n§eTip:§r keep your eyes on it until it gives up. A Ward Lantern stops it.",
    ),
    "hl.lore.herobrine": (
        "§lHerobrine§r\n§8(la leyenda de siempre)§r\n\nTres bloques de alto, el estómago abierto y cosido, las costillas abiertas como dedos, media cara quemada hasta el hueso, la mandíbula colgando hasta el pecho. Su columna sale de la espalda y se arquea sobre su cabeza como la cola de un escorpión. Te observa desde lejos y desaparece si lo miras.\n\n§eConsejo:§r míralo fijamente tres veces. No le pegues.",
        "§lHerobrine§r\n§8(the old legend)§r\n\nThree blocks tall, his stomach open and stitched, his ribs splayed like fingers, half his face burned to the bone, his jaw hanging to his chest. His spine comes out of his back and arches over his head like a scorpion's tail. He watches from afar and vanishes if you look.\n\n§eTip:§r stare him down three times. Don't hit him.",
    ),
    "hl.lore.goatman": (
        "§lEl Hombre Cabra§r\n§8(la leyenda de los bosques)§r\n\nPatas de cabra, cuernos de carnero, ojos amarillos, brazos demasiado humanos. Grita como una cabra entre los árboles de noche. Se acerca cuando no miras; cuando lo pillas, grita y huye... o viene a por ti.\n\n§eConsejo:§r la luz y los golpes lo espantan.",
        "§lThe Goatman§r\n§8(the legend of the woods)§r\n\nGoat legs, ram's horns, yellow eyes, arms too human. He screams like a goat between the trees at night. He comes closer when you don't look; when you catch him he screams and runs... or comes for you.\n\n§eTip:§r light and a beating send him running.",
    ),
    "hl.lore.fog": (
        "§lEl Hombre de la Niebla§r\n§8(inspirado en The Man From The Fog)§r\n\nMás de tres bloques de cuerpo gris y famélico en un chubasquero negro empapado. Dentro de la capucha, dos puntos blancos y una sonrisa de oreja a oreja. Se acerca cuando no miras. De noche llama a tu puerta.\n\n§eConsejo:§r sobrevive a una niebla en la que lo hayas visto.",
        "§lThe Man From The Fog§r\n§8(inspired by The Man From The Fog)§r\n\nOver three blocks of grey, starved body in a soaked black raincoat. Inside the hood, two white points and a grin from ear to ear. He comes closer when you don't look. At night he knocks on your door.\n\n§eTip:§r live through a fog in which you saw him.",
    ),
    "hl.lore.skinwalker": (
        "§lEl Skinwalker§r\n§8(la leyenda del desierto)§r\n\nUna vaca que siempre te está mirando, con ojos de persona y dientes de persona. Oyes tu nombre en el campo, a alguien herido donde no hay nadie. Si te acercas se levanta: algo flaco con el cráneo de la vaca por cara y su piel a la espalda.\n\n§eConsejo:§r no te acerques a vacas que te miran. Herido, huye.",
        "§lThe Skinwalker§r\n§8(the desert legend)§r\n\nA cow that is always looking at you, with a person's eyes and a person's teeth. You hear your name in the field, someone hurt where nobody is. Get close and it stands up: a starved thing with the cow's skull for a face and its hide on its back.\n\n§eTip:§r don't go near cows that stare. Hurt, it flees.",
    ),
    "hl.lore.shadow": (
        "§lLa Sombra§r\n§8(el Hombre del Sombrero)§r\n\nUna figura negra y plana con sombrero de ala ancha y un abrigo que se deshace en humo. Apaga las luces de tu casa, te sigue, mira por la ventana, y está a los pies de tu cama cuando te despiertas.\n\n§eConsejo:§r la linterna o mirarla fijamente la hacen desaparecer. Un Farol Protector la mantiene fuera.",
        "§lThe Shadow§r\n§8(the Hat Man)§r\n\nA flat black figure in a wide-brimmed hat and a coat that frays into smoke. It puts out the lights in your house, follows you, looks in through the window, and stands at the foot of your bed when you wake.\n\n§eTip:§r the flashlight, or staring at it, makes it vanish. A Ward Lantern keeps it out.",
    ),
    "hl.lore.drowned": (
        "§lEl Ahogado§r\n§8(lo que no salió del agua)§r\n\nHinchado, con un camisón roto y verde y el pelo negro tapándole la cara, salvo un ojo. Gorgotea bajo la superficie. De noche te mira desde la orilla; si nadas, te agarra desde abajo y tira de ti.\n\n§eConsejo:§r pégale para que te suelte, o sal del agua.",
        "§lThe Drowned One§r\n§8(what never came out of the water)§r\n\nSwollen, in a torn green nightdress, black hair over its face but for one eye. It gurgles under the surface. At night it watches from the shallows; if you swim, it grabs you from below and pulls.\n\n§eTip:§r hit it to make it let go, or get out of the water.",
    ),
    "hl.lore.null": (
        "§lnull§r\n§8(inspirado en The Broken Script)§r\n\nUn jugador que el juego no consigue dibujar: negro, roto en franjas que no encajan, con un borde rojo y otro azul, dos ojos blancos y la caja de F3+B alrededor. Escribe en el chat, corrompe bloques, finge que el juego se cuelga.\n\n§eConsejo:§r míralo a los ojos. Dos veces.",
        "§lnull§r\n§8(inspired by The Broken Script)§r\n\nA player the game can't draw: black, torn into slices that don't line up, fringed red and blue, two white eyes and the F3+B box round it. It writes in the chat, corrupts blocks, pretends the game has frozen.\n\n§eTip:§r look it in the eye. Twice.",
    ),
    "hl.lore.e303": (
        "§lEntity 303§r\n§8(el hacker que nunca se fue)§r\n\nCapucha blanca, ojos rojos, una máscara con una sonrisa cosida. Entra a tu partida, escribe en el chat, te cambia los objetos de mano, te echa (o lo finge). En persona flota, levanta las manos y tu mundo es suyo: vuelas, te teletransporta, caen rayos. Luego te banea.\n\n§eConsejo:§r aguanta su espectáculo. Al final deja algo.",
        "§lEntity 303§r\n§8(the hacker who never left)§r\n\nWhite hood, red eyes, a mask with a stitched grin. He joins your game, writes in the chat, swaps what you're holding, kicks you (or pretends to). In person he floats, raises his hands and your world is his: you fly, you're teleported, lightning falls. Then he bans you.\n\n§eTip:§r sit through his show. In the end he leaves something.",
    ),
    "hl.lore.siren": (
        "§lSiren Head§r\n§8(inspirado en la criatura de Trevor Henderson)§r\n\nCasi seis bloques de piel seca sobre huesos, y donde debería estar la cabeza, un poste con dos altavoces oxidados. Primero oyes la sirena, muy lejos. Luego voces por el altavoz. Si lo miras demasiado, viene a zancadas.\n\n§eConsejo:§r escóndete donde no te vea (bajo techo, tras una colina) hasta que te pierda.",
        "§lSiren Head§r\n§8(inspired by Trevor Henderson's creature)§r\n\nNearly six blocks of dried skin over bones, and where the head should be, a pole with two rusted loudspeakers. First you hear the siren, far away. Then voices through the speakers. Watch it too long and it comes, in huge strides.\n\n§eTip:§r hide where it can't see you (under a roof, behind a hill) until it loses you.",
    ),
    "hl.lore.wendigo": (
        "§lEl Wendigo§r\n§8(el hambre que aprendió a andar)§r\n\nPiel azulada sobre las costillas, dedos negros de congelación, un cráneo de ciervo con una cornamenta enorme. Silba entre los árboles. Caza por el oído: si corres, te oye; si te agachas y no te mueves, te pierde. Sus garras te dejan muerto de hambre.\n\n§eConsejo:§r en la nieve, agáchate.",
        "§lThe Wendigo§r\n§8(hunger that learned to walk)§r\n\nBlue-white skin over its ribs, fingers black with frostbite, a deer's skull with huge antlers. It whistles between the trees. It hunts by sound: run and it hears you; crouch and keep still and it loses you. Its claws leave you starving.\n\n§eTip:§r in the snow, crouch.",
    ),
    "hl.lore.redNight": (
        "§lLa Noche Roja§r\n\nCada cuatro días, una noche de niebla roja en la que todo viene el doble de veces y tus faroles pueden apagarse. Termina al amanecer.",
        "§lThe Red Night§r\n\nEvery four days, a night of red fog when everything comes twice as often and your lanterns may go out. It ends at dawn.",
    ),
    "hl.lore.boss": (
        "§l§4El Minero Hueco§r\n§8(Herobrine, en su forma verdadera)§r\n\nCasi cinco bloques de pizarra agrietada con lava por dentro, el casco de minero con la lámpara encendida, un pico en lugar de mano derecha, un puño de piedra, y la columna arqueada sobre su cabeza hasta un aguijón de obsidiana. Se teletransporta, llama a los rayos, invoca Moradores, apaga la luz y pica con la cola. A mitad de vida, enfurece.\n\n§eConsejo:§r la luz de la linterna le quema.",
        "§l§4The Hollow Miner§r\n§8(Herobrine, in his true form)§r\n\nNearly five blocks of cracked deepslate with lava inside, his miner's helmet with the lamp still lit, a pickaxe for a right hand, a fist of stone, and his spine arching over his head to an obsidian sting. He teleports, calls down lightning, summons Dwellers, puts out the light and stings. At half health he flies into a rage.\n\n§eTip:§r flashlight light burns him.",
    ),
    # --------------------------------------------------------------- pages
    "hl.page.1.title": (
        "I. El jugador",
        "I. The Player",
    ),
    "hl.page.1.text": (
        "§oDía 3. Entró un jugador a mi mundo. Se llamaba HerobrineGamer788. Llevaba unos cascos con una luz roja. Me regaló pan.\n\nDía 4. Hoy me ha copiado cada paso. Cada bloque que picaba, él lo picaba.\n\nDía 5. La luz de sus cascos no parpadea. Él tampoco.",
        "§oDay 3. A player joined my world. His name was HerobrineGamer788. He wore a headset with a red light. He gave me bread.\n\nDay 4. Today he copied my every step. Every block I mined, he mined.\n\nDay 5. The light on his headset doesn't blink. Neither does he.",
    ),
    "hl.page.2.title": (
        "II. Lo que vive abajo",
        "II. What Lives Below",
    ),
    "hl.page.2.text": (
        "§oHay algo en la mina que imita mis pasos. No tiene ojos. No le hacen falta.\n\nCuando abre la boca, se abre toda la cara.\n\nOdia la luz. Se me están acabando las pilas.",
        "§oThere's something in the mine that copies my footsteps. It has no eyes. It doesn't need them.\n\nWhen it opens its mouth, its whole face opens.\n\nIt hates light. I'm running out of batteries.",
    ),
    "hl.page.3.title": (
        "III. Ojos blancos",
        "III. White Eyes",
    ),
    "hl.page.3.text": (
        "§oEncontré su pico clavado en su propio hombro. Era un minero. Bajó demasiado hondo, donde la piedra respira, y algo subió con él.\n\nAhora tiene los ojos blancos y la columna fuera del cuerpo, levantada sobre su cabeza como la cola de un escorpión.",
        "§oI found his pickaxe buried in his own shoulder. He was a miner. He dug too deep, where the stone breathes, and something came back up with him.\n\nNow his eyes are white and his spine is outside his body, raised over his head like a scorpion's tail.",
    ),
    "hl.page.4.title": (
        "IV. La niebla",
        "IV. The Fog",
    ),
    "hl.page.4.text": (
        "§oLa niebla no es niebla. Es él, esperando, con la capucha puesta y esa sonrisa.\n\nSi lo miras, se va. Si no lo miras, se acerca.\n\nLlamó a mi puerta tres veces. La cuarta no llamó.",
        "§oThe fog isn't fog. It's him, waiting, hood up, with that grin.\n\nIf you look at him, he leaves. If you don't, he comes closer.\n\nHe knocked on my door three times. The fourth time he didn't knock.",
    ),
    "hl.page.5.title": (
        "V. El error",
        "V. The Error",
    ),
    "hl.page.5.text": (
        "§oLos bloques se rompen en magenta y negro. El mundo se deshace donde él pisa.\n\nHay otro. Dice que es administrador. Me banea cada noche y cada mañana sigo aquí.",
        "§oBlocks break into magenta and black. The world comes apart where he walks.\n\nThere's another one. He says he's an admin. He bans me every night and every morning I'm still here.",
    ),
    "hl.page.6.title": (
        "VI. La sirena",
        "VI. The Siren",
    ),
    "hl.page.6.text": (
        "§oLa sirena suena de noche, siempre a lo lejos. Una vez la vi: más alta que los pinos, quieta, con dos altavoces donde debería tener la cabeza.\n\nLa miré demasiado. Me escondí en una cueva y esperé a que dejara de oírse.\n\nPor el altavoz decía mi nombre.",
        "§oThe siren wails at night, always far away. I saw it once: taller than the pines, perfectly still, two loudspeakers where its head should be.\n\nI looked at it too long. I hid in a cave and waited until I couldn't hear it any more.\n\nThrough the speakers it was saying my name.",
    ),
    "hl.page.7.title": (
        "VII. El nombre",
        "VII. The Name",
    ),
    "hl.page.7.text": (
        "§oTodos le sirven a él. El que está en la mina, el de la cola de hueso.\n\nSi juntas estas siete páginas, llámalo por su nombre de noche, bajo el cielo. Vendrá en su forma verdadera, y solo así se le puede herir.\n\nYo ya no puedo. Creo que ya no soy yo el que está jugando.",
        "§oThey all serve him. The one in the mine, the one with the tail of bone.\n\nIf you gather these seven pages, call him by his name at night, under the sky. He will come in his true form, and only then can he be hurt.\n\nI can't any more. I don't think I'm the one playing now.",
    ),
    "hl.threat.scares": ("Sustos", "Scares"),
    "hl.lore.scares": (
        "§lSustos§r\n\nNo es una criatura: son los momentos en que algo está demasiado cerca. Una cara delante de la tuya lo que dura un parpadeo. Un aliento en la nuca y algo que te roza. Una puerta que se cierra de golpe en una casa vacía. La pantalla rota en estática. Un golpe que duele y no deja marca. Tu nombre, escrito delante de tus ojos.",
        "§lScares§r\n\nNot a creature: the moments when something is too close. A face right in front of yours for the length of a blink. Breath on the back of your neck and something brushing past. A door slamming in an empty house. The screen tearing into static. A blow that hurts and leaves no mark. Your name, written across your eyes.",
    ),
    "hl.arrive.scares": ("§8[???]§r §7No parpadees. No te des la vuelta. No mires detrás de la puerta.", "§8[???]§r §7Don't blink. Don't turn round. Don't look behind the door."),
    "hl.scare.neck": ("§7§oAlgo te ha rozado la nuca.", "§7§oSomething brushed the back of your neck."),
    "hl.scare.name_sub": ("§8te estoy mirando", "§8i'm watching you"),
    "hl.page.dropped": (
        "§7Algo ha caído al suelo...",
        "§7Something fell to the ground...",
    ),
    "hl.page.count": (
        "§7Páginas leídas: §f%s/%s",
        "§7Pages read: §f%s/%s",
    ),
    "hl.page.all": (
        "§4Las siete páginas.§r §7El diario tiembla en tus manos. Ábrelo: ahora sabes cómo llamarlo.",
        "§4All seven pages.§r §7The journal trembles in your hands. Open it: now you know how to call him.",
    ),
    "hl.mimic.wake": (
        "§6¡No era un cofre!",
        "§6That wasn't a chest!",
    ),
    "hl.goat.charge": (
        "§4§lViene a por ti.",
        "§4§lIt's coming for you.",
    ),
    "hl.sw.voice": (
        "§7§o...%s... ven aquí...",
        "§7§o...%s... come here...",
    ),
    "hl.sw.reveal": (
        "§4§lEso no es una vaca.",
        "§4§lThat isn't a cow.",
    ),
    "hl.drowned.grab": (
        "§3§lAlgo te agarra desde abajo. ¡Pégale!",
        "§3§lSomething has you from below. Hit it!",
    ),
    "hl.siren.hunt": (
        "§4§l¡ESCÓNDETE!",
        "§4§lHIDE!",
    ),
    "hl.siren.lost": (
        "§7La sirena se aleja. Te ha perdido.",
        "§7The siren moves away. It lost you.",
    ),
    "hl.wendigo.charge": (
        "§b§lTe ha oído.",
        "§b§lIt heard you.",
    ),
    "hl.e303.come": (
        "Hola, %s. Vamos a jugar.",
        "Hi, %s. Let's play.",
    ),
    "hl.e303.fly": (
        "¿Sabes volar?",
        "Can you fly?",
    ),
    "hl.e303.where": (
        "¿Dónde estás ahora?",
        "Where are you now?",
    ),
    "hl.e303.hit": (
        "No puedes pegar a un administrador.",
        "You can't hit an admin.",
    ),
    "hl.e303.kick": (
        "§fDesconectado",
        "§fDisconnected",
    ),
    "hl.e303.kick_sub": (
        "§7Has sido expulsado del servidor.",
        "§7You were kicked from the server.",
    ),
    "hl.e303.ban.title": (
        "§4Baneado",
        "§4Banned",
    ),
    "hl.e303.ban.body": (
        "Has sido baneado permanentemente de este mundo.\n\nMotivo: §fseguir aquí§r\nBaneado por: §cEntity303§r\n\n§8...pero puedes quedarte, %s. Ahora eres nuestro.",
        "You have been permanently banned from this world.\n\nReason: §fstill being here§r\nBanned by: §cEntity303§r\n\n§8...but you can stay, %s. You're ours now.",
    ),
    "hl.e303.ban.ok": (
        "Aceptar",
        "OK",
    ),
    "hl.e303.chat.1": (
        "Este mundo era mío antes que tuyo.",
        "This world was mine before it was yours.",
    ),
    "hl.e303.chat.2": (
        "Me banearon. No me fui.",
        "They banned me. I didn't leave.",
    ),
    "hl.e303.chat.3": (
        "Bonita casa, %s.",
        "Nice house, %s.",
    ),
    "hl.e303.chat.4": (
        "¿Has visto a Notch? Yo tampoco.",
        "Have you seen Notch? Neither have I.",
    ),
    "hl.e303.chat.5": (
        "/op Entity303",
        "/op Entity303",
    ),
    "hl.e303.chat.6": (
        "Sé dónde guardas tus diamantes.",
        "I know where you keep your diamonds.",
    ),
    # -------------------------------------------------------------- ritual
    "hl.ritual.body": (
        "Con las siete páginas puedes llamarlo por su nombre. Vendrá en su forma verdadera, y esta vez se le puede herir.\n\n§7Debe ser de noche y al aire libre. Lleva armadura, comida, tu mejor espada y la linterna con pilas: la luz le quema.",
        "With all seven pages you can call him by his name. He'll come in his true form, and this time he can be hurt.\n\n§7It must be night, under the open sky. Bring armour, food, your best sword and the flashlight with batteries: light burns him.",
    ),
    "hl.ritual.ready": ("§4Está todo listo. ¿Lo llamas?", "§4Everything is ready. Will you call him?"),
    "hl.ritual.begin": ("§4Decir su nombre", "§4Say his name"),
    "hl.ritual.need_pages": ("§7Te faltan páginas.", "§7You're missing pages."),
    "hl.ritual.need_night": ("§7Solo puede ser de noche.", "§7It can only be at night."),
    "hl.ritual.need_sky": ("§7Tiene que ser al aire libre, bajo el cielo.", "§7It has to be outside, under the sky."),
    "hl.ritual.busy": ("§7Ya viene.", "§7He's already coming."),
    "hl.ritual.title": ("§4§lHEROBRINE", "§4§lHEROBRINE"),
    "hl.ritual.sub": ("§7Has dicho su nombre.", "§7You said his name."),

    # ---------------------------------------------------------------- boss
    "hl.boss.sub": ("§cEl Minero Hueco", "§cThe Hollow Miner"),
    "hl.boss.rage": ("§4§l¡ENFURECE!", "§4§lHE'S ENRAGED!"),
    "hl.boss.bolts": ("§e¡Rayos! ¡Muévete!", "§eLightning! Move!"),
    "hl.boss.summon": ("§7Algo sube de las cuevas...", "§7Something climbs up from the caves..."),
    "hl.boss.retreat.far": ("§7Herobrine se ha perdido en la oscuridad. Volverá cuando lo llames.", "§7Herobrine has gone back into the dark. He'll come when you call."),
    "hl.boss.retreat.dawn": ("§7Sale el sol y Herobrine se retira. Esta noche no.", "§7The sun comes up and Herobrine withdraws. Not tonight."),
    "hl.boss.retreat.died": ("§8Herobrine se ríe. Las páginas siguen en tu diario.", "§8Herobrine laughs. The pages are still in your journal."),
    "hl.win.title": ("§6§lHAS ROTO LA MALDICIÓN", "§6§lYOU BROKE THE CURSE"),
    "hl.win.sub": ("§eAmanece.", "§eDawn."),
    "hl.win.epilogue.1": (
        "§8[???]§r §7El pico cae al suelo, todavía caliente. Es tuyo.",
        "§8[???]§r §7The pickaxe drops to the ground, still warm. It's yours.",
    ),
    "hl.win.epilogue.2": (
        "§8[???]§r §7Durante unos días, el mundo está en silencio. Las páginas se han vuelto a dispersar.",
        "§8[???]§r §7For a few days the world is quiet. The pages have scattered again.",
    ),
    "hl.win.epilogue.3": (
        "§8[???]§r §7Pero él no muere. Nunca muere. Nos vemos pronto. §8(%s)",
        "§8[???]§r §7But he doesn't die. He never dies. See you soon. §8(%s)",
    ),

    # ----------------------------------------------------- items and misc
    "hl.flash.bar": ("§eLinterna", "§eFlashlight"),
    "hl.flash.empty": ("§cLa linterna no tiene pilas.", "§cThe flashlight has no batteries."),
    "hl.flash.swap": ("§7Pila nueva.", "§7Fresh battery."),
    "hl.flash.dead": ("§cLa linterna parpadea... y se apaga.", "§cThe flashlight flickers... and dies."),
    "hl.flash.boss": ("§e¡La luz le quema!", "§eThe light burns him!"),
    "hl.ward.snuffed": ("§4Algo ha apagado tu Farol Protector.", "§4Something snuffed out your Ward Lantern."),
}

LANG = {k: v for k, v in KEPT.items()}
LANG.update(S)
