"""Every piece of text in the add-on, in Spanish and English.

build.py writes these to the .lang files of both packs (es_ES and es_MX get
the Spanish, en_US and en_GB the English). `\n` is a line break in forms.
"""

from lang_kept import KEPT

S = {
    # -------------------------------------------- pack, entities, items, acts
    "pack.name": (
        "§4Horror Legends: §cFears§r",
        "§4Horror Legends: §cFears§r",
    ),
    "pack.description": (
        'Terror en tres actos con las leyendas y creepypastas de Minecraft: HerobrineGamer788, el Morador de las Cuevas, el Aldeano Sin Nombre, Herobrine, el Endermán Sin Rostro, el Falso Árbol, Soup.AVI, el Evil Hunter, el Stalker, Jenny Dweller, el Hombre de la Niebla, null, Entity 303 y el Minero Hueco. Sonidos 3D y fuertes, y los Miedos.',
        'Horror in three acts with the legends and creepypastas of Minecraft: HerobrineGamer788, the Cave Dweller, the Nameless Villager, Herobrine, the Faceless Enderman, the False Tree, Soup.AVI, the Evil Hunter, the Stalker, Jenny Dweller, the Man From The Fog, null, Entity 303 and the Hollow Miner. Loud 3D sound, and the Fears.',
    ),
    "entity.hh:herobrine.name": (
        "Herobrine",
        "Herobrine",
    ),
    "entity.hh:herobrine_boss.name": (
        "Herobrine, el Minero Hueco",
        "Herobrine, the Hollow Miner",
    ),
    "entity.hh:null.name": (
        "null",
        "null",
    ),
    "entity.hh:fog_man.name": (
        "El Hombre de la Niebla",
        "The Man From The Fog",
    ),
    "entity.hh:cave_dweller.name": (
        "El Morador de las Cuevas",
        "The Cave Dweller",
    ),
    "entity.hh:fake_player.name": (
        "HerobrineGamer788",
        "HerobrineGamer788",
    ),
    "entity.hh:entity_303.name": (
        "Entity 303",
        "Entity 303",
    ),
    "item.spawn_egg.entity.hh:herobrine.name": (
        "Generar: Herobrine",
        "Spawn Herobrine",
    ),
    "item.spawn_egg.entity.hh:herobrine_boss.name": (
        "Generar: Herobrine, el Minero Hueco",
        "Spawn Herobrine, the Hollow Miner",
    ),
    "item.spawn_egg.entity.hh:null.name": (
        "Generar: null",
        "Spawn null",
    ),
    "item.spawn_egg.entity.hh:fog_man.name": (
        "Generar: El Hombre de la Niebla",
        "Spawn The Man From The Fog",
    ),
    "item.spawn_egg.entity.hh:cave_dweller.name": (
        "Generar: El Morador de las Cuevas",
        "Spawn The Cave Dweller",
    ),
    "item.spawn_egg.entity.hh:fake_player.name": (
        "Generar: HerobrineGamer788",
        "Spawn HerobrineGamer788",
    ),
    "item.spawn_egg.entity.hh:entity_303.name": (
        "Generar: Entity 303",
        "Spawn Entity 303",
    ),
    "item.hh:journal.name": (
        "Diario del Superviviente",
        "Survivor's Journal",
    ),
    "item.hh:flashlight.name": (
        "Linterna",
        "Flashlight",
    ),
    "item.hh:battery.name": (
        "Pila",
        "Battery",
    ),
    "item.hh:silver_cross.name": (
        "§fCruz de Plata",
        "§fSilver Cross",
    ),
    "item.hh:hollow_pickaxe.name": (
        "§4Pico del Minero Hueco",
        "§4Pickaxe of the Hollow Miner",
    ),
    "item.hh:page_1.name": (
        "Página arrancada I",
        "Torn Page I",
    ),
    "item.hh:page_2.name": (
        "Página arrancada II",
        "Torn Page II",
    ),
    "item.hh:page_3.name": (
        "Página arrancada III",
        "Torn Page III",
    ),
    "item.hh:page_4.name": (
        "Página arrancada IV",
        "Torn Page IV",
    ),
    "item.hh:page_5.name": (
        "Página arrancada V",
        "Torn Page V",
    ),
    "item.hh:page_6.name": (
        "Página arrancada VI",
        "Torn Page VI",
    ),
    "item.hh:page_7.name": (
        "Página arrancada VII",
        "Torn Page VII",
    ),
    "tile.hh:corrupted_block.name": (
        "Bloque Corrupto",
        "Corrupted Block",
    ),
    "tile.hh:ward_lantern.name": (
        "Farol Protector",
        "Ward Lantern",
    ),
    "hh.intro.1": (
        "§8[???]§r §7Este mundo no está tan vacío como parece. Cada día que pase, algo más se dará cuenta de que estás aquí.",
        "§8[???]§r §7This world is not as empty as it looks. Every day you stay, something else notices you're here.",
    ),
    "hh.intro.2": (
        "§8[???]§r §7Lee el §fDiario§7. Fabrica una §fLinterna§7 y §fPilas§7. Un §fFarol Protector§7 mantiene tu casa a salvo... casi siempre.",
        "§8[???]§r §7Read the §fJournal§7. Craft a §fFlashlight§7 and §fBatteries§7. A §fWard Lantern§7 keeps your home safe... mostly.",
    ),
    "hh.act.n": (
        "Acto %s",
        "Act %s",
    ),
    "hh.act.1.name": (
        "Visitantes",
        "Visitors",
    ),
    "hh.act.1.intro": (
        "§8[???]§r §7Estás solo en este mundo. ¿Verdad?",
        "§8[???]§r §7You're alone in this world. Aren't you?",
    ),
    "hh.act.1.text": (
        'Primero, sonidos que no están: pasos, puertas, tu nombre. Ahora vienen de un lugar: oirás de qué lado.\n\nLuego llegan los visitantes. §fHerobrineGamer788§r entra a tu partida. Bajo tierra, el §fMorador§r te acecha. En el campo, un §faldeano sin nombre§r te mira. Y muy abajo, bajo la pizarra, hay una §3Ciudad Antigua§r donde nadie hace ruido.\n\nCada uno tiene sus miedos, y el juego los conoce.',
        "First, sounds that aren't there: footsteps, doors, your name. Now they come from somewhere: you will hear which side.\n\nThen the visitors come. §fHerobrineGamer788§r joins your game. Underground, the §fCave Dweller§r stalks you. In the field, a §fnameless villager§r watches you. And far below, under the deepslate, there is an §3Ancient City§r where nobody makes a sound.\n\nEveryone has their fears, and the game knows them.",
    ),
    "hh.act.2.name": (
        "El bosque",
        "The Woods",
    ),
    "hh.act.2.intro": (
        '§8[???]§r §7No mires por la ventana de noche.',
        "§8[???]§r §7Don't look out of the window at night.",
    ),
    "hh.act.2.text": (
        '§fHerobrine§r te observa con su cola de vértebras. Un §fEnderman sin rostro§r parpadea a tu alrededor. En la jungla y la taiga, un §ftronco§r te saluda. En tu casa, un hombre sin cara §fcome sopa y llora§r. Algo responde a un ritual y §fte caza§r; algo muy alto §fsolo se mueve cuando no miras§r; una chica muy simpática quiere que §fla sigas§r. Llega la §fniebla§r y, dentro, un hombre con chubasquero. Llaman a tu puerta con tu voz. Encuentras §ftu propia tumba§r. Y a las tres de la mañana, todo te mira.',
        '§fHerobrine§r watches you with his tail of vertebrae. A §ffaceless Enderman§r blinks around you. In the jungle and the taiga, a §ftrunk§r waves at you. In your house, a man with no face §feats soup and cries§r. Something answers a ritual and §fhunts you§r; something tall §fonly moves when you look away§r; a friendly girl wants you to §ffollow her§r. The §ffog§r rolls in and, in it, a man in a raincoat. Someone knocks on your door with your voice. You find §fyour own grave§r. And at three in the morning, everything looks at you.',
    ),
    "hh.act.3.name": (
        "El error",
        "The Error",
    ),
    "hh.act.3.intro": (
        '§8[???]§r §7Algo se ha roto en el código.',
        '§8[???]§r §7Something has broken in the code.',
    ),
    "hh.act.3.text": (
        '§fnull§r corrompe el mundo. §fEntity 303§r entra a tu partida y juega con ella.\n\nCada cuatro días, la §4Noche Roja§r.\n\nSi has encontrado las siete páginas, ya sabes cómo acabar con esto.',
        '§fnull§r corrupts the world. §fEntity 303§r joins your game and plays with it.\n\nEvery four days, the §4Red Night§r.\n\nIf you have found all seven pages, you know how to end this.',
    ),
    "hh.arrive.ambience": (
        "§8§o...",
        "§8§o...",
    ),
    "hh.arrive.gamer": (
        "§8[???]§r §7Alguien más ha encontrado tu mundo.",
        "§8[???]§r §7Someone else has found your world.",
    ),
    "hh.arrive.dweller": (
        "§8[???]§r §7Algo respira en las cuevas.",
        "§8[???]§r §7Something breathes in the caves.",
    ),
    "hh.arrive.herobrine": (
        "§8[???]§r §7Él te ha visto.",
        "§8[???]§r §7He has seen you.",
    ),
    "hh.arrive.fog": (
        "§8[???]§r §7Habrá niebla. No la mires.",
        "§8[???]§r §7There will be fog. Don't look into it.",
    ),
    "hh.arrive.null": (
        "§8[???]§r §7null",
        "§8[???]§r §7null",
    ),
    "hh.arrive.e303": (
        "§8[???]§r §7Alguien tiene permisos de administrador en tu mundo. No eres tú.",
        "§8[???]§r §7Someone has operator permissions in your world. It isn't you.",
    ),
    "hh.arrive.redNight": (
        "§8[???]§r §7La luna se está volviendo roja.",
        "§8[???]§r §7The moon is turning red.",
    ),
    # ------------------------------------------------------------- journal
    "hh.j.title": ("Diario del Superviviente", "Survivor's Journal"),
    "hh.j.day": ("§7Día §f%s§7 del encantamiento", "§7Day §f%s§7 of the haunting"),
    "hh.j.fear": ("§7Miedo:", "§7Fear:"),
    "hh.j.pages_count": ("§7Páginas: §f%s/%s", "§7Pages: §f%s/%s"),
    "hh.j.peace": ("§aHas roto la maldición. Descansa... unos días.", "§aYou broke the curse. Rest... for a few days."),
    "hh.j.wins": ("§7Veces que has vencido a Herobrine: §f%s", "§7Times you have beaten Herobrine: §f%s"),
    "hh.j.bestiary": ("Bestiario", "Bestiary"),
    "hh.j.bestiary_body": ("Todo lo que has visto. Lo que no, aún es ???", "Everything you have seen. What you haven't is still ???"),
    "hh.j.unknown": ("Aún no lo has visto. Aún.", "You haven't seen it yet. Yet."),
    "hh.j.pages": ("Páginas arrancadas", "Torn Pages"),
    "hh.j.pages_body": (
        "Has leído §f%s de %s§r páginas de un diario que no es tuyo. Siete de ellas guardan una y la dejan cuando sobrevives.",
        "You have read §f%s of %s§r pages of a diary that isn't yours. Seven of them each keep one and leave it when you live through them.",
    ),
    "hh.j.page_missing": ("§8Página %s: perdida", "§8Page %s: missing"),
    "hh.j.ritual": ("§4El Ritual", "§4The Ritual"),
    "hh.j.settings": ("Ajustes", "Settings"),
    "hh.j.settings_body": ("Pulsa una amenaza para activarla o desactivarla. Cada una dice en cuántos días llega.", "Tap a threat to switch it on or off. Each says how many days until it arrives."),
    "hh.j.intensity": ("Intensidad", "Intensity"),
    "hh.j.jumpscares": ("Sustos fuertes", "Jumpscares"),
    "hh.j.tests": ("Pruebas", "Tests"),
    "hh.j.tests_body": ("Provoca cualquier cosa al momento, para verla sin esperar días.", "Trigger anything right now, to see it without waiting days."),
    "hh.j.on": ("§aSÍ", "§aON"),
    "hh.j.off": ("§cNO", "§cOFF"),
    "hh.j.in_days": ("§8en %s días", "§8in %s days"),
    "hh.j.close": ("Cerrar", "Close"),
    "hh.j.back": ("Volver", "Back"),
    "hh.t.scare": ("Susto (prueba)", "Jumpscare (test)"),
    "hh.t.items": ("Darme objetos (linterna, pilas, farol, páginas)", "Give me items (flashlight, batteries, lantern, pages)"),
    "hh.t.ritual": ("Empezar el ritual ya", "Start the ritual now"),
    "hh.t.day": ("Avanzar un día", "Skip ahead a day"),

    # ---------------------------------------------------------------- acts
    "hh.j.acts": (
        "Actos",
        "Acts",
    ),
    "hh.j.acts_body": (
        "La historia hasta ahora.",
        "The story so far.",
    ),
    # ------------------------------------------------------------- threats
    "hh.threat.ambience": (
        "Sonidos",
        "Sounds",
    ),
    "hh.threat.gamer": (
        "HerobrineGamer788",
        "HerobrineGamer788",
    ),
    "hh.threat.dweller": (
        "El Morador de las Cuevas",
        "The Cave Dweller",
    ),
    "hh.threat.herobrine": (
        "Herobrine",
        "Herobrine",
    ),
    "hh.threat.fog": (
        "El Hombre de la Niebla",
        "The Man From The Fog",
    ),
    "hh.threat.null": (
        "null",
        "null",
    ),
    "hh.threat.e303": (
        "Entity 303",
        "Entity 303",
    ),
    "hh.threat.redNight": (
        "La Noche Roja",
        "The Red Night",
    ),
    "hh.threat.boss": (
        "§4El Minero Hueco",
        "§4The Hollow Miner",
    ),
    "hh.lore.ambience": (
        "§lSonidos§r\n\nPasos detrás de ti, puertas, cofres, minería lejana, el siseo de un creeper que no está, respiración, uñas arañando la pared, golpes, tu nombre susurrado.\n\n§eConsejo:§r no te des la vuelta. O sí.",
        "§lSounds§r\n\nFootsteps behind you, doors, chests, distant mining, the hiss of a creeper that isn't there, breathing, nails on the wall, knocking, your name whispered.\n\n§eTip:§r don't turn around. Or do.",
    ),
    "hh.lore.gamer": (
        "§lHerobrineGamer788§r\n§8(un jugador que no debería estar)§r\n\nEntra a tu mundo como cualquiera, con una sudadera granate y sus cascos con una luz roja que nunca parpadea. Juega, te copia, se vuelve raro y al final deja de fingir.\n\n§eConsejo:§r cuando se revele, busca en el suelo donde estaba.",
        "§lHerobrineGamer788§r\n§8(a player who shouldn't be here)§r\n\nHe joins your world like anyone else, wearing a headset whose red light never blinks. He plays, copies you, turns strange and in the end stops pretending.\n\n§eTip:§r when he reveals himself, look on the ground where he stood.",
    ),
    "hh.lore.dweller": (
        "§lEl Morador de las Cuevas§r\n§8(inspirado en Cave Dweller / From The Caves)§r\n\nUn cuerpo pálido y sin ojos, encorvado, que anda apoyado en los nudillos como algo que antes caminaba de pie. Su hocico de cuatro labios se abre como una flor sobre anillos de dientes. Huye si lo pillas mirando; si esperas demasiado, viene a por ti.\n\n§eConsejo:§r la linterna lo espanta. Sobrevive a él y dejará algo.",
        "§lThe Cave Dweller§r\n§8(inspired by Cave Dweller / From The Caves)§r\n\nA pale, eyeless body hunched over, knuckle-walking like something that used to walk upright. Its four-lipped snout opens like a flower over rings of teeth. It flees if you catch it looking; wait too long and it comes for you.\n\n§eTip:§r the flashlight drives it off. Live through it and it leaves something behind.",
    ),
    "hh.lore.herobrine": (
        "§lHerobrine§r\n§8(la leyenda de siempre)§r\n\nTres bloques de alto, el estómago abierto y cosido, las costillas abiertas como dedos, media cara quemada hasta el hueso, la mandíbula colgando hasta el pecho. Su columna sale de la espalda y la arrastra detrás de él como una cola de huesos, y le salen astillas de hueso de los hombros y de la cabeza. Te observa desde lejos y desaparece si lo miras.\n\n§eConsejo:§r míralo fijamente tres veces. No le pegues.",
        "§lHerobrine§r\n§8(the old legend)§r\n\nThree blocks tall, his stomach open and stitched, his ribs splayed like fingers, half his face burned to the bone, his jaw hanging to his chest. His spine comes out of his back and drags behind him like a tail of bone, and splinters of bone grow out of his shoulders and head. He watches from afar and vanishes if you look.\n\n§eTip:§r stare him down three times. Don't hit him.",
    ),
    "hh.lore.fog": (
        "§lEl Hombre de la Niebla§r\n§8(inspirado en The Man From The Fog)§r\n\nMás de tres bloques de cuerpo gris y famélico con un impermeable amarillo empapado hasta los tobillos y un sombrero de agua calado. Bajo el ala, dos puntos blancos y una sonrisa de oreja a oreja. Se acerca cuando no miras. De noche llama a tu puerta.\n\n§eConsejo:§r sobrevive a una niebla en la que lo hayas visto.",
        "§lThe Man From The Fog§r\n§8(inspired by The Man From The Fog)§r\n\nOver three blocks of grey, starved body in a soaked yellow slicker down to his ankles and a pulled-down sou'wester. Inside the hood, two white points and a grin from ear to ear. He comes closer when you don't look. At night he knocks on your door.\n\n§eTip:§r live through a fog in which you saw him.",
    ),
    "hh.lore.null": (
        "§lnull§r\n§8(inspirado en The Broken Script)§r\n\nUn jugador que el juego no consigue dibujar: negro, roto en franjas que no encajan, con la cabeza suelta sobre el cuerpo y los brazos flotando, un borde rojo y otro azul, dos ojos blancos y la caja de F3+B alrededor. Escribe en el chat, corrompe bloques, finge que el juego se cuelga.\n\n§eConsejo:§r míralo a los ojos. Dos veces.",
        "§lnull§r\n§8(inspired by The Broken Script)§r\n\nA player the game can't draw: black, torn into slices that don't line up, fringed red and blue, two white eyes and the F3+B box round it. It writes in the chat, corrupts blocks, pretends the game has frozen.\n\n§eTip:§r look it in the eye. Twice.",
    ),
    "hh.lore.e303": (
        "§lEntity 303§r\n§8(el hacker que nunca se fue)§r\n\nUna túnica blanca con la capucha acabada en punta y deshecha en tiras, ojos rojos, una máscara con una sonrisa cosida. Entra a tu partida, escribe en el chat, te cambia los objetos de mano, te echa (o lo finge). En persona flota, levanta las manos y tu mundo es suyo: vuelas, te teletransporta, caen rayos. Luego te banea.\n\n§eConsejo:§r aguanta su espectáculo. Al final deja algo.",
        "§lEntity 303§r\n§8(the hacker who never left)§r\n\nA white robe, its hood running to a point and coming apart in strips, red eyes, a mask with a stitched grin. He joins your game, writes in the chat, swaps what you're holding, kicks you (or pretends to). In person he floats, raises his hands and your world is his: you fly, you're teleported, lightning falls. Then he bans you.\n\n§eTip:§r sit through his show. In the end he leaves something.",
    ),
    "hh.lore.redNight": (
        "§lLa Noche Roja§r\n\nCada cuatro días, una noche de niebla roja en la que todo viene el doble de veces y tus faroles pueden apagarse. Termina al amanecer.",
        "§lThe Red Night§r\n\nEvery four days, a night of red fog when everything comes twice as often and your lanterns may go out. It ends at dawn.",
    ),
    "hh.lore.boss": (
        "§l§4El Minero Hueco§r\n§8(Herobrine, en su forma verdadera)§r\n\nCasi cinco bloques de pizarra agrietada con lava por dentro, el casco de minero con la lámpara encendida, un pico en lugar de mano derecha, un puño de piedra, y la columna levantada como un mástil sobre su espalda con una lámpara de minero colgando de la punta. Se teletransporta, llama a los rayos, invoca Moradores, apaga la luz y pica con la cola. A mitad de vida, enfurece.\n\n§eConsejo:§r la luz de la linterna le quema.",
        "§l§4The Hollow Miner§r\n§8(Herobrine, in his true form)§r\n\nNearly five blocks of cracked deepslate with lava inside, his miner's helmet with the lamp still lit, a pickaxe for a right hand, a fist of stone, and his spine raised like a mast over his back, a miner's lamp hanging from the tip. He teleports, calls down lightning, summons Dwellers, puts out the light and stings. At half health he flies into a rage.\n\n§eTip:§r flashlight light burns him.",
    ),
    # --------------------------------------------------------------- pages
    "hh.page.1.title": (
        "I. El jugador",
        "I. The Player",
    ),
    "hh.page.1.text": (
        "§oDía 3. Entró un jugador a mi mundo. Se llamaba HerobrineGamer788. Llevaba unos cascos con una luz roja. Me regaló pan.\n\nDía 4. Hoy me ha copiado cada paso. Cada bloque que picaba, él lo picaba.\n\nDía 5. La luz de sus cascos no parpadea. Él tampoco.",
        "§oDay 3. A player joined my world. His name was HerobrineGamer788. He wore a headset with a red light. He gave me bread.\n\nDay 4. Today he copied my every step. Every block I mined, he mined.\n\nDay 5. The light on his headset doesn't blink. Neither does he.",
    ),
    "hh.page.2.title": (
        "II. Lo que vive abajo",
        "II. What Lives Below",
    ),
    "hh.page.2.text": (
        "§oHay algo en la mina que imita mis pasos. No tiene ojos. No le hacen falta.\n\nCuando abre la boca, se abre toda la cara.\n\nOdia la luz. Se me están acabando las pilas.",
        "§oThere's something in the mine that copies my footsteps. It has no eyes. It doesn't need them.\n\nWhen it opens its mouth, its whole face opens.\n\nIt hates light. I'm running out of batteries.",
    ),
    "hh.page.3.title": (
        "III. Ojos blancos",
        "III. White Eyes",
    ),
    "hh.page.3.text": (
        "§oEncontré su pico clavado en su propio hombro. Era un minero. Bajó demasiado hondo, donde la piedra respira, y algo subió con él.\n\nAhora tiene los ojos blancos y la columna fuera del cuerpo, arrastrándola detrás de él como una cola.",
        "§oI found his pickaxe buried in his own shoulder. He was a miner. He dug too deep, where the stone breathes, and something came back up with him.\n\nNow his eyes are white and his spine is outside his body, dragging behind him like a tail.",
    ),
    "hh.page.4.title": (
        "IV. La niebla",
        "IV. The Fog",
    ),
    "hh.page.4.text": (
        "§oLa niebla no es niebla. Es él, esperando, con la capucha puesta y esa sonrisa.\n\nSi lo miras, se va. Si no lo miras, se acerca.\n\nLlamó a mi puerta tres veces. La cuarta no llamó.",
        "§oThe fog isn't fog. It's him, waiting, hood up, with that grin.\n\nIf you look at him, he leaves. If you don't, he comes closer.\n\nHe knocked on my door three times. The fourth time he didn't knock.",
    ),
    "hh.page.5.title": (
        "V. El error",
        "V. The Error",
    ),
    "hh.page.5.text": (
        "§oLos bloques se rompen en magenta y negro. El mundo se deshace donde él pisa.\n\nHay otro. Dice que es administrador. Me banea cada noche y cada mañana sigo aquí.",
        "§oBlocks break into magenta and black. The world comes apart where he walks.\n\nThere's another one. He says he's an admin. He bans me every night and every morning I'm still here.",
    ),
    "hh.page.6.title": (
        'VI. La tumba',
        'VI. The Grave',
    ),
    "hh.page.6.text": (
        '§oDe noche sonó una campana a mi espalda. Fui a mirar: tierra removida, una lápida, una rosa negra. Y en la piedra, mi nombre.\n\nMe acerqué. Algo golpeaba desde abajo, despacio, como quien llama a una puerta.\n\nCreo que quería salir. O que quería que yo entrara.',
        '§oA bell rang behind me at night. I went to look: freshly dug earth, a headstone, a black rose. And on the stone, my name.\n\nI went closer. Something was knocking from underneath, slowly, like someone at a door.\n\nI think it wanted out. Or it wanted me in.',
    ),
    "hh.page.7.title": (
        "VII. El nombre",
        "VII. The Name",
    ),
    "hh.page.7.text": (
        "§oTodos le sirven a él. El que está en la mina, el de la cola de hueso.\n\nSi juntas estas siete páginas, llámalo por su nombre de noche, bajo el cielo. Vendrá en su forma verdadera, y solo así se le puede herir.\n\nYo ya no puedo. Creo que ya no soy yo el que está jugando.",
        "§oThey all serve him. The one in the mine, the one with the tail of bone.\n\nIf you gather these seven pages, call him by his name at night, under the sky. He will come in his true form, and only then can he be hurt.\n\nI can't any more. I don't think I'm the one playing now.",
    ),
    "hh.threat.scares": ("Sustos", "Scares"),
    "hh.lore.scares": (
        "§lSustos§r\n\nNo es una criatura: son los momentos en que algo está demasiado cerca. Una cara delante de la tuya lo que dura un parpadeo. Un aliento en la nuca y algo que te roza. Una puerta que se cierra de golpe en una casa vacía. La pantalla rota en estática. Un golpe que duele y no deja marca. Tu nombre, escrito delante de tus ojos.",
        "§lScares§r\n\nNot a creature: the moments when something is too close. A face right in front of yours for the length of a blink. Breath on the back of your neck and something brushing past. A door slamming in an empty house. The screen tearing into static. A blow that hurts and leaves no mark. Your name, written across your eyes.",
    ),
    "hh.arrive.scares": ("§8[???]§r §7No parpadees. No te des la vuelta. No mires detrás de la puerta.", "§8[???]§r §7Don't blink. Don't turn round. Don't look behind the door."),
    "hh.scare.neck": ("§7§oAlgo te ha rozado la nuca.", "§7§oSomething brushed the back of your neck."),
    "hh.scare.name_sub": ("§8te estoy mirando", "§8i'm watching you"),
    # ------------------------------------------------------------ Fears
    "hh.threat.ancient": ("La Ciudad Antigua", "The Ancient City"),
    "hh.lore.ancient": (
        "§lLa Ciudad Antigua§r\n\nMuy abajo, bajo la pizarra, hay una ciudad donde nadie hace ruido. Al entrar, el juego te dice dónde estás. Dentro, la ciudad está despierta: las velas se apagan solas, los chilladores gritan sin nada cerca, tus pasos vuelven un momento tarde, la oscuridad late como un corazón, el marco del portal zumba y hay susurros. Y si te quedas un minuto, alguien te observa desde las ruinas.\n\n§eConsejo:§r no corras, no hagas ruido y no te quedes.",
        "§lThe Ancient City§r\n\nFar below, under the deepslate, there is a city where nobody makes a sound. When you walk in, the game tells you where you are. Inside, the city is awake: candles go out by themselves, shriekers scream with nothing near, your footsteps come back a moment late, the dark pulses like a heart, the portal frame hums and there are whispers. And stay a minute and someone watches you from the ruins.\n\n§eTip:§r don't run, don't make noise and don't stay.",
    ),
    "hh.arrive.ancient": ("§8[???]§r §7Bajo la pizarra hay una ciudad. Allí abajo, nadie hace ruido.", "§8[???]§r §7Under the deepslate there is a city. Down there, nobody makes a sound."),
    # ------------------------------------------------------------ 1.2
    "entity.hh:nameless_villager.name": ("Aldeano Sin Nombre", "Nameless Villager"),
    "entity.hh:faceless_enderman.name": ("Endermán Sin Rostro", "Faceless Enderman"),
    "item.spawn_egg.entity.hh:nameless_villager.name": ("Generar: Aldeano Sin Nombre", "Spawn Nameless Villager"),
    "item.spawn_egg.entity.hh:faceless_enderman.name": ("Generar: Endermán Sin Rostro", "Spawn Faceless Enderman"),
    "hh.threat.nameless": ("El Aldeano Sin Nombre", "The Nameless Villager"),
    "hh.threat.faceless": ("El Endermán Sin Rostro", "The Faceless Enderman"),
    "hh.lore.nameless": (
        "§lEl Aldeano Sin Nombre§r\n§8(inspirado en las creepypastas de aldeanos de Minecraft)§r\n\nTodos los aldeanos tienen un oficio y un nombre. Este no. Está en el campo, al borde de lo que alcanzas a ver, con una túnica gris ceniza, los brazos colgando hasta las rodillas y la cabeza demasiado ladeada. Los ojos son dos agujeros blancos. Canturrea, bajo y lento, desde donde está. Si no lo miras, se acerca. Si lo miras, se queda quieto y, al rato, levanta un brazo y te señala. Si llega a tu espalda, te pone una mano en el hombro.\n\n§eConsejo:§r mantenlo a la vista, o acércate a él: desaparece. La linterna también lo espanta.",
        "§lThe Nameless Villager§r\n§8(inspired by Minecraft villager creepypastas)§r\n\nEvery villager has a job and a name. This one has neither. It stands in the fields at the edge of what you can see, in an ash-grey robe, arms hanging to its knees, head tipped too far. The eyes are two white holes. It hums, low and slow, from where it stands. If you don't look, it comes closer. If you look, it stands still and after a while lifts an arm and points at you. If it gets behind you, it puts a hand on your shoulder.\n\n§eTip:§r keep it in sight, or walk up to it: it vanishes. The flashlight scares it off too.",
    ),
    "hh.lore.faceless": (
        "§lEl Endermán Sin Rostro§r\n§8(inspirado en las creepypastas de Endermen)§r\n\nUn Enderman más alto que los del juego, sin cara: solo una línea violeta donde irían los ojos. No camina: parpadea. Oyes su sonido al desaparecer de un sitio y al aparecer en otro, en 3D, así que sabes hacia dónde se fue. Cada vez está más cerca. Si lo miras de frente, la línea se abre y viene a por ti.\n\n§eConsejo:§r escucha. Una luz lo manda lejos.",
        "§lThe Faceless Enderman§r\n§8(inspired by Enderman creepypastas)§r\n\nAn Enderman taller than the ones in the game, with no face: only a violet line where the eyes would be. It doesn't walk: it blinks. You hear it vanish from one place and arrive at another, in 3D, so you know which way it went. Every time it is closer. Look straight at it and the line opens, and it comes for you.\n\n§eTip:§r listen. A light sends it away.",
    ),
    "hh.arrive.nameless": ("§8[???]§r §7Hay un aldeano en el campo. No tiene nombre. No dejes de mirarlo.", "§8[???]§r §7There's a villager in the field. It has no name. Don't stop looking at it."),
    "hh.arrive.faceless": ("§8[???]§r §7Si oyes un Enderman y no ves ojos, no lo mires.", "§8[???]§r §7If you hear an Enderman and see no eyes, don't look at it."),
    "hh.nameless.point": ("§7§oEl aldeano te señala.", "§7§oThe villager points at you."),
    "hh.nameless.touch": ("§8una mano en tu hombro", "§8a hand on your shoulder"),
    "hh.faceless.seen": ("§5§oLa línea violeta se abre.", "§5§oThe violet line opens."),
    "hh.faceless.sub": ("§8te vi", "§8i saw you"),
    # ------------------------------------------------------------ 1.4
    "entity.hh:false_tree.name": ("El Falso Árbol", "The False Tree"),
    "item.spawn_egg.entity.hh:false_tree.name": ("Generar: El Falso Árbol", "Spawn The False Tree"),
    "hh.threat.falsetree": ("El Falso Árbol", "The False Tree"),
    "hh.lore.falsetree": (
        "§lEl Falso Árbol§r\n§8(al estilo de las criaturas de Doctor Nowhere)§r\n\nVive en la jungla y en la taiga, y parece otro tronco: alto, fino y oscuro, entre los demás árboles. Puedes pasar a su lado todo el día. Si lo miras bien, o te acercas demasiado, se endereza: casi cuatro bloques de extremidades negras y larguísimas con ramitas creciéndole, y una cara blanca y plana, como dibujada por un niño, con una corona de puntas. Levanta una mano y te saluda. Si apartas la vista, vuelve a ser un tronco, pero más cerca. Si se acerca demasiado, ya no saluda.\n\n§eConsejo:§r no le quites los ojos de encima y no te acerques a ningún tronco raro. La linterna lo espanta.",
        "§lThe False Tree§r\n§8(in the style of Doctor Nowhere's creatures)§r\n\nIt lives in the jungle and the taiga, and it looks like another trunk: tall, thin and dark, among the other trees. You can walk past it all day. Look at it properly, or get too close, and it straightens up: nearly four blocks of long black limbs with twigs growing out of them, and a flat white face like a child's drawing with a crown of points. It raises a hand and waves. Look away and it is a trunk again, but closer. If it gets too close, it doesn't wave any more.\n\n§eTip:§r keep your eyes on it and stay away from any strange trunk. The flashlight scares it off.",
    ),
    "hh.arrive.falsetree": ("§8[???]§r §7En la jungla y en la taiga, no todos los troncos son árboles.", "§8[???]§r §7In the jungle and the taiga, not every trunk is a tree."),
    "hh.falsetree.seen": ("§8§oEse tronco te saluda.", "§8§oThat trunk is waving at you."),
    "hh.falsetree.sub": ("§8ya no saludo", "§8i'm not waving now"),
    # ------------------------------------------------------------ 1.5
    "entity.hh:soup_man.name": ("El Hombre de la Sopa", "The Soup Man"),
    "item.spawn_egg.entity.hh:soup_man.name": ("Generar: El Hombre de la Sopa", "Spawn The Soup Man"),
    "entity.hh:soup_mascot.name": ("La Cabeza Grande", "The Big Head"),
    "item.spawn_egg.entity.hh:soup_mascot.name": ("Generar: La Cabeza Grande", "Spawn The Big Head"),
    "hh.threat.soup": ("Soup.AVI", "Soup.AVI"),
    "hh.lore.soup": (
        "§lSoup.AVI§r\n§8(la cinta de la habitación blanca)§r\n\nUna cinta que nadie debería tener. En tu casa, o fuera de noche, hay un hombre sentado en un taburete blanco comiendo sopa. Su cara es un bloque de cuadros censurados. Le tiemblan los hombros: está llorando. Detrás de él, alguien con una enorme cabeza de botarga le frota la espalda, en círculos, sin parar. La cinta corre: ves el contador.\n\nSi miras demasiado tiempo, o te acercas, paran, y las dos cabezas se giran hacia ti. Luego se va la luz, y hay otro. §cDetrás de ti.§r\n\n§eCómo sobrevivir:§r date la vuelta y míralo. Si no lo haces, empieza a frotarte la espalda a ti. Golpearlos, o alumbrar la cinta con la linterna antes de que se giren, la detiene.",
        "§lSoup.AVI§r\n§8(the tape of the white room)§r\n\nA tape nobody should have. In your house, or outside at night, a man is sitting on a white stool eating soup. His face is a block of censored squares. His shoulders shake: he is crying. Behind him, someone in a huge mascot head rubs his back, round and round, without stopping. The tape is running: you can see the counter.\n\nWatch for too long, or walk up to them, and they stop, and both heads turn to you. Then the lights go, and there is another one. §cBehind you.§r\n\n§eHow to survive:§r turn round and look at it. If you don't, it starts rubbing your back instead. Hitting them, or shining the flashlight on the tape before they turn, stops it.",
    ),
    "hh.arrive.soup": ("§8[???]§r §7Si oyes a alguien sorber sopa y llorar en tu casa, no mires demasiado.", "§8[???]§r §7If you hear someone slurping soup and crying in your house, don't watch for too long."),
    "hh.soup.minor": ("§8§oAlguien está comiendo sopa. Y llorando.", "§8§oSomeone is eating soup. And crying."),
    "hh.soup.seen": ("§7§oSe han dado la vuelta.", "§7§oThey have turned round."),
    "hh.soup.another": ("§8hay otro", "§8there's another one"),
    "hh.soup.sub": ("§8ya está, ya está", "§8there, there"),
    "hh.soup.rub": ("§7§oAlgo te frota la espalda. Ya está... ya está...", "§7§oSomething is rubbing your back. There, there..."),
    # ------------------------------------------------------------ 1.6
    "entity.hh:evil_hunter.name": ("Evil Hunter", "Evil Hunter"),
    "item.spawn_egg.entity.hh:evil_hunter.name": ("Generar: Evil Hunter", "Spawn Evil Hunter"),
    "hh.threat.hunter": ("Evil Hunter", "Evil Hunter"),
    "hh.lore.hunter": (
        "§lEvil Hunter§r\n§8(del mod de Java de DregIr)§r\n\nAlgo respondió a un ritual que nadie recuerda haber hecho. Primero las señales: madera que arde donde no hay fuego, una risa a lo lejos, chispas a tu espalda. Luego la §4Cacería§r: un hombre corrompido, con una §cchaqueta de franela roja§r gastada y quemada sobre ropa oscura. Del pecho para arriba está calcinado, con grietas que brillan como lava; de la cintura para abajo se vuelve sombra y humo. La cara casi no se distingue: solo dos puntos rojos. Un aura roja lo rodea. Se queda quieto al borde de la noche y te mira. Y luego corre.\n\nNo se le puede hacer daño. Atraviesa puertas y ventanas, y sus garras te prenden fuego.\n\n§eCómo sobrevivir:§r aguanta 45 segundos hasta que se consuma, o fabrica una §fCruz de Plata§r (hierro y oro) y úsala cuando esté cerca: la Cacería termina al instante. La linterna lo ralentiza un momento, y no entra en la luz de una Linterna de Guarda.",
        "§lEvil Hunter§r\n§8(from DregIr's Java mod)§r\n\nSomething answered a ritual nobody remembers doing. First the signs: wood burning where there is no fire, a laugh far off, sparks behind you. Then the §4Hunt§r: a corrupted man in a worn, scorched §cred flannel jacket§r over dark clothes. From the chest up he is charred, with cracks that glow like lava; from the waist down he turns to shadow and smoke. You can barely see a face: just two red points. A red aura surrounds him. He stands at the edge of the night and looks at you. Then he runs.\n\nIt can't be hurt. It smashes through doors and windows, and its claws set you on fire.\n\n§eHow to survive:§r last 45 seconds until it burns out, or craft a §fSilver Cross§r (iron and gold) and use it when it is close: the Hunt ends at once. The flashlight slows it for a moment, and it won't enter a Ward Lantern's light.",
    ),
    "hh.arrive.hunter": ("§8[???]§r §7Si hueles a quemado y no hay fuego, busca plata.", "§8[???]§r §7If you smell burning and there is no fire, find silver."),
    "hh.hunter.title": ("§4§lLA CACERÍA", "§4§lTHE HUNT"),
    "hh.hunter.sub": ("§cte ha encontrado", "§cit has found you"),
    "hh.hunter.signs": ("§6§oHuele a quemado.", "§6§oSomething is burning."),
    "hh.hunter.run": ("§4corre", "§4run"),
    "hh.hunter.over": ("§7§oSe ha consumido. Por ahora.", "§7§oIt has burned out. For now."),
    "hh.cross.nothing": ("§7La cruz está fría. No hay nada cerca.", "§7The cross is cold. Nothing is near."),
    "hh.cross.rest": ("§7La cruz todavía quema. Espera.", "§7The cross is still burning. Wait."),
    "hh.cross.banished": ("§fLa cruz lo destierra.", "§fThe cross banishes it."),
    "entity.hh:stalker.name": ("El Stalker", "The Stalker"),
    "item.spawn_egg.entity.hh:stalker.name": ("Generar: El Stalker", "Spawn The Stalker"),
    "hh.threat.stalker": ("El Stalker", "The Stalker"),
    "hh.lore.stalker": (
        "§lEl Stalker§r\n§8(The Parallel / el Stalker de Twixxel)§r\n\nMás de cuatro bloques de huesos negros como la brea, con las costillas marcadas, las rodillas dobladas al revés y los brazos tan largos que casi tocan el suelo, con manos pálidas de dedos huesudos. Y una cabeza §fovalada, beige, como una máscara§r: cuencas en forma de huevo mucho más grandes que sus ojos, con una §fdiminuta pupila blanca§r arriba en cada una, una nariz pequeña y una sonrisa enorme de §fdientes humanos§r, negra por dentro. Cuando caza a cuatro patas, la boca se le abre larguísima.\n\nNo se le puede hacer daño, y §cNUNCA se mueve mientras lo miras§r. Solo cuando apartas la vista.\n\n· §fLejos§r: te observa desde el borde del mundo. Aparta la vista y está más cerca. Y más cerca. Y luego, detrás de ti.\n· §fDetrás§r: date la vuelta y desaparece. Si no lo haces, te toca.\n· §fPersecución§r: a cuatro patas, rompiendo puertas y ventanas. Si lo miras, se congela.\n\n§eCómo sobrevivir:§r no le quites la vista de encima, y date la vuelta.",
        "§lThe Stalker§r\n§8(The Parallel / Twixxel's Stalker)§r\n\nMore than four blocks of pitch-black bones, ribs showing, knees bent the wrong way and arms so long they nearly touch the ground, with pale, bony hands. And an §foval, beige head like a mask§r: egg-shaped sockets far bigger than its eyes, with a §ftiny white pupil§r high up in each, a small nose and a huge grin of §fhuman teeth§r, black inside. When it hunts on all fours, the mouth opens unnaturally long.\n\nIt can't be hurt, and it §cNEVER moves while you are looking at it§r. Only when you look away.\n\n· §fFar§r: it watches you from the edge of the world. Look away and it is closer. And closer. And then, behind you.\n· §fBehind§r: turn round and it is gone. If you don't, it touches you.\n· §fChase§r: on all fours, smashing through doors and windows. Look at it and it freezes.\n\n§eHow to survive:§r keep your eyes on it, and turn round.",
    ),
    "hh.arrive.stalker": ("§8[???]§r §7Si algo muy alto te mira desde lejos, no apartes la vista.", "§8[???]§r §7If something very tall is watching you from far away, don't look away."),
    "hh.stalker.seen": ("§7§oNo se mueve. Mientras lo mires.", "§7§oIt isn't moving. While you watch it."),
    "hh.stalker.sub": ("§8no apartes la vista", "§8don't look away"),
    "hh.stalker.touch": ("§7§oAlgo frío te toca la nuca.", "§7§oSomething cold touches the back of your neck."),
    "hh.stalker.chase": ("§8no dejes de mirarlo", "§8don't stop looking at it"),
    "entity.hh:jenny_dweller.name": ("Jenny", "Jenny"),
    "item.spawn_egg.entity.hh:jenny_dweller.name": ("Generar: Jenny Dweller", "Spawn Jenny Dweller"),
    "hh.threat.jenny": ("Jenny Dweller", "Jenny Dweller"),
    "hh.lore.jenny": (
        "§lJenny Dweller§r\n§8(Jenny y el Cave Dweller, fusionados)§r\n\nUna chica simpática con sudadera roja y coleta, sola en medio de ninguna parte (o en el fondo de una cueva), que se pone MUY contenta de verte. Te saluda. Te habla. Te regala una flor. Quiere que vengas con ella, un poquito más lejos, que tiene algo que enseñarte, y cada vez que te acercas se ríe y sale corriendo.\n\nCuando por fin la alcanzas, deja de fingir: es un §fDweller§r de las cuevas que llevaba puesta a Jenny. Más alta, encorvada y de piel gris-blanca, con la sudadera hecha jirones, un cuello larguísimo, su pelo y su coleta sobre una cara pálida con dos huecos negros por ojos y una §fboca que se abre en dos mitades§r llena de dientes, y brazos hasta las rodillas con garras negras. Y te caza. Pega tan fuerte que te puede matar.\n\nSi la ignoras, no le gusta nada: viene a buscarte.\n\n§eCómo sobrevivir:§r no la sigas. Si ya te caza, una Linterna de Guarda la para y la linterna la ralentiza. Se la puede matar.",
        "§lJenny Dweller§r\n§8(Jenny and the Cave Dweller, fused)§r\n\nA friendly girl in a red hoodie and a ponytail, all alone in the middle of nowhere (or at the bottom of a cave), who is SO happy to see you. She waves. She talks to you. She gives you a flower. She wants you to come with her, just a bit further, she has something to show you, and every time you get close she giggles and runs on ahead.\n\nWhen you finally catch up with her, she stops pretending: she is a cave §fDweller§r that was wearing Jenny. Taller, hunched and grey-white, the hoodie in rags, a neck far too long, her hair and ponytail on a pale face with two black hollows for eyes and a §fmouth that splits into two halves§r full of teeth, and arms to the knees with black claws. And she hunts you. She hits hard enough to kill.\n\nIf you ignore her, she doesn't like it at all: she comes to find you.\n\n§eHow to survive:§r don't follow her. If she's already hunting you, a Ward Lantern stops her and the flashlight slows her down. She can be killed.",
    ),
    "hh.arrive.jenny": ("§8[???]§r §7Si una chica te pide que la sigas, no la sigas.", "§8[???]§r §7If a girl asks you to follow her, don't."),
    "hh.jenny.say.0": ("¿Hola? ¿Hay alguien? ¿Quieres jugar?", "Hello? Is someone there? Do you want to play?"),
    "hh.jenny.say.1": ("¡Hola! ¡Por fin alguien! ¿Estás solo? ¡Ven, ven!", "Hi! Finally, someone! Are you on your own? Come here, come!"),
    "hh.jenny.say.2": ("¡Por aquí! Quiero enseñarte una cosa.", "This way! I want to show you something."),
    "hh.jenny.say.3": ("Toma, te he dejado una flor. ¡Es para ti! Sígueme, ya casi llegamos.", "Here, I left you a flower. It's for you! Follow me, we're nearly there."),
    "hh.jenny.say.4": ("Un poquito más... ya casi... jijiji.", "Just a little bit further... nearly there... hee hee."),
    "hh.jenny.say.5": ("Ya hemos llegado.", "We're here."),
    "hh.jenny.say.6": ("¿Por qué no vienes? Me estás poniendo triste.", "Why aren't you coming? You're making me sad."),
    "hh.jenny.say.7": ("Vale. Pues voy yo.", "Fine. Then I'll come to you."),
    "hh.jenny.sub": ("§8ya no tengo que fingir", "§8i don't have to pretend now"),
    "hh.jenny.gone": ("§7§oSe ha ido. Por ahora.", "§7§oShe's gone. For now."),
    "hh.threat.phobias": ("Los Miedos", "The Fears"),
    "hh.lore.phobias": (
        "§lLos Miedos§r\n\nTodos le tienen miedo a algo, y el juego sabe a qué. Si te quedas a oscuras sin luz, en un saliente muy alto, en agua muy profunda o en un túnel por el que apenas cabes, un miedo se va llenando. Cuando se llena, ocurre algo hecho a su medida. Y es muy fuerte.\n\n§eConsejo:§r lleva luz, no mires abajo, sal del agua y no te metas en túneles estrechos. El Diario muestra cuánto se ha llenado cada miedo.",
        "§lThe Fears§r\n\nEveryone is afraid of something, and the game knows what. Stand in the dark with no light, on a ledge very high up, in very deep water, or in a tunnel you barely fit in, and a fear slowly fills. When it is full, something made exactly for it happens. And it is loud.\n\n§eTip:§r carry a light, don't look down, get out of the water and stay out of narrow tunnels. The journal shows how full each fear is.",
    ),
    "hh.arrive.phobias": ("§8[???]§r §7Sabemos de qué tienes miedo.", "§8[???]§r §7We know what you're afraid of."),
    "hh.phobia.dark": ("§7La oscuridad", "§7The dark"),
    "hh.phobia.heights": ("§7Las alturas", "§7Heights"),
    "hh.phobia.water": ("§7El agua profunda", "§7Deep water"),
    "hh.phobia.closed": ("§7Los espacios cerrados", "§7Tight spaces"),
    "hh.j.fears": ("Tus miedos", "Your fears"),
    "hh.j.fears_body": ("Cada barra se llena mientras estás expuesto a ese miedo. Al llenarse, ocurre.", "Each bar fills while you are exposed to that fear. When it is full, it happens."),
    "hh.j.loud": ("Sonidos fuertes", "Loud sounds"),
    "hh.j.audio3d": ("Sonido 3D", "3D sound"),
    "hh.fear.dark_sub": ("§8no estás solo aquí", "§8you aren't alone in here"),
    "hh.fear.heights_bar": ("§7§oEl suelo está muy lejos. Y te llama.", "§7§oThe ground is very far. And it is calling you."),
    "hh.fear.heights_sub": ("§8salta", "§8jump"),
    "hh.fear.water_bar": ("§3§oAlgo enorme pasa por debajo.", "§3§oSomething enormous passes underneath."),
    "hh.fear.water_sub": ("§8más hondo", "§8deeper"),
    "hh.fear.closed_bar": ("§7§oLas paredes golpean. Desde el otro lado.", "§7§oThe walls knock. From the other side."),
    "hh.fear.closed_sub": ("§8no hay salida", "§8there is no way out"),
    # ------------------------------------------------------------ 8.2.0
    "hh.threat.steps": ("Pasos", "Footsteps"),
    "hh.threat.countdown": ("La Cuenta Atrás", "The Countdown"),
    "hh.threat.grave": ("Tu Tumba", "Your Grave"),
    "hh.threat.hour": ("La Hora Muerta", "The Dead Hour"),
    "hh.lore.steps": (
        "§lPasos§r\n\nAlguien camina detrás de ti, paso a paso. Cuando andas, anda; cuando te paras, se para. Pero cada vez que te paras da un paso más, un poco más cerca. Si te das la vuelta no hay nadie.",
        "§lFootsteps§r\n\nSomeone walks behind you, step for step. When you walk, they walk; when you stop, they stop. But every time you stop they take one more step, a little closer. Turn round and there is nobody.",
    ),
    "hh.lore.countdown": (
        "§lLa Cuenta Atrás§r\n\nUn mensaje de nadie: «Cuando llegue a cero, date la vuelta». Luego los números, uno por segundo. Al llegar a cero la pantalla se va a negro, y cuando vuelve hay una cara delante de la tuya. O, a veces, nada... y un minuto después, un grito.",
        "§lThe Countdown§r\n\nA message from nobody: \"When it reaches zero, turn around.\" Then the numbers, one a second. At zero the screen goes black, and when it comes back there is a face in front of yours. Or sometimes nothing... and a minute later, a scream.",
    ),
    "hh.lore.grave": (
        "§lTu Tumba§r\n\nDe noche, una campana a tu espalda. Ve a mirar: un montículo de tierra recién removida, una lápida, una rosa negra, y en la piedra tu nombre y la fecha de mañana. Si te acercas, algo golpea. Desde abajo.",
        "§lYour Grave§r\n\nAt night, a bell behind you. Go and look: a mound of freshly dug earth, a headstone, a black rose, and on the stone your name and tomorrow's date. Come close and something knocks. From underneath.",
    ),
    "hh.lore.hour": (
        "§lLa Hora Muerta§r\n\nLas tres de la mañana. Todos los animales y aldeanos a tu alrededor se paran donde están y se giran a mirarte, todos a la vez, mientras una campana que nadie tiene suena tres veces. Luego siguen a lo suyo como si nada. Y lejos, alguien grita.",
        "§lThe Dead Hour§r\n\nThree in the morning. Every animal and villager around you stops where it is and turns to look at you, all at once, while a bell nobody owns tolls three times. Then they carry on as if nothing happened. And far away, someone screams.",
    ),
    "hh.arrive.steps": ("§8[???]§r §7Cuando te paras, ¿se paran contigo?", "§8[???]§r §7When you stop, do they stop with you?"),
    "hh.arrive.countdown": ("§8[???]§r §710, 9, 8...", "§8[???]§r §710, 9, 8..."),
    "hh.arrive.grave": ("§8[???]§r §7Alguien está cavando. Tiene tu tamaño.", "§8[???]§r §7Someone is digging. It's your size."),
    "hh.arrive.hour": ("§8[???]§r §7A las tres, todo te mira.", "§8[???]§r §7At three o'clock, everything looks at you."),
    "hh.ancient.title": ("§3La Ciudad Antigua", "§3The Ancient City"),
    "hh.ancient.sub": ("§8Aquí abajo, nadie hace ruido.", "§8Down here, nobody makes a sound."),
    "hh.ancient.candles": ("§7§oLas velas se apagan solas.", "§7§oThe candles go out by themselves."),
    "hh.ancient.steps": ("§7§oTus pasos vuelven, un poco tarde.", "§7§oYour footsteps come back, a little late."),
    "hh.ancient.portal": ("§5§oEl marco zumba. Algo intenta pasar.", "§5§oThe frame hums. Something is trying to come through."),
    "hh.ancient.whisper.1": ("§3§oAquí vivía gente. Ahora solo escucha.", "§3§oPeople lived here. Now it only listens."),
    "hh.ancient.whisper.2": ("§3§oNo corras.", "§3§oDon't run."),
    "hh.ancient.whisper.3": ("§3§oNos quedamos callados demasiado tiempo.", "§3§oWe kept quiet too long."),
    "hh.ancient.whisper.4": ("§3§oTe oye respirar.", "§3§oIt can hear you breathing."),
    "hh.ancient.whisper.5": ("§3§oLa puerta no era para salir.", "§3§oThe door was not for leaving."),
    "hh.ancient.whisper.6": ("§3§oQuédate. Como nosotros.", "§3§oStay. Like us."),
    "hh.steps.end": ("§7§oTe respiran en la nuca.", "§7§oSomeone breathes on your neck."),
    "hh.countdown.start": ("§7Cuando llegue a cero, date la vuelta.", "§7When it reaches zero, turn around."),
    "hh.countdown.nothing": ("§8...todavía no.", "§8...not yet."),
    "hh.grave.sign": ("AQUÍ YACE\n%s\nDÍA %s", "HERE LIES\n%s\nDAY %s"),
    "hh.grave.below": ("§7§oAlgo golpea desde abajo.", "§7§oSomething knocks from underneath."),
    "hh.hour.sub": ("§8la hora muerta", "§8the dead hour"),
    "hh.threat.visit": ("La Visita", "The Visit"),
    "hh.lore.visit": (
        "§lLa Visita§r\n\nDe noche, en casa, tres golpes en la puerta. Luego una voz en el chat, con tu nombre: «Ábreme. Soy yo. Hace frío fuera.»\n\nSi abres, no hay nadie, solo el viento. Y un momento después alguien te da las gracias por dejarle entrar, justo detrás de ti. Si no abres, sigue llamando, cada vez más fuerte. Prueba el picaporte. Y luego, silencio.\n\n§eConsejo:§r no abras. Y si ya has abierto, no te des la vuelta.",
        "§lThe Visit§r\n\nAt night, at home, three knocks on the door. Then a voice in the chat, with your name: \"Open up. It's me. It's cold out here.\"\n\nOpen it and there is nobody, only the wind. A moment later someone thanks you for letting them in, right behind you. Don't open it, and it keeps knocking, louder and louder. It tries the handle. And then, silence.\n\n§eTip:§r don't open it. And if you already have, don't turn around.",
    ),
    "hh.arrive.visit": ("§8[???]§r §7Si llaman a la puerta de noche, no abras. Tú ya estás dentro.", "§8[???]§r §7If someone knocks at night, don't open. You're already inside."),
    "hh.visit.1": ("Ábreme. Soy yo. Hace frío fuera.", "Open up. It's me. It's cold out here."),
    "hh.visit.2": ("¿Por qué no me abres?", "Why won't you open the door?"),
    "hh.visit.3": ("Sé que estás ahí. Te oigo respirar.", "I know you're in there. I can hear you breathing."),
    "hh.visit.4": ("ÁBREME", "OPEN THE DOOR"),
    "hh.visit.nobody": ("§7§oNo hay nadie. Solo el viento.", "§7§oThere's nobody there. Only the wind."),
    "hh.visit.thanks": ("Gracias por dejarme entrar.", "Thanks for letting me in."),
    "hh.visit.scare_sub": ("§8ahora yo vivo aquí", "§8i live here now"),
    "hh.visit.silence": ("§7§oHan dejado de llamar.", "§7§oThe knocking has stopped."),
    "hh.visit.sign": ("TE VI\nPOR LA\nVENTANA", "I SAW YOU\nTHROUGH THE\nWINDOW"),
    "hh.page.dropped": (
        "§7Algo ha caído al suelo...",
        "§7Something fell to the ground...",
    ),
    "hh.page.count": (
        "§7Páginas leídas: §f%s/%s",
        "§7Pages read: §f%s/%s",
    ),
    "hh.page.all": (
        "§4Las siete páginas.§r §7El diario tiembla en tus manos. Ábrelo: ahora sabes cómo llamarlo.",
        "§4All seven pages.§r §7The journal trembles in your hands. Open it: now you know how to call him.",
    ),
    "hh.goat.charge": (
        "§4§lViene a por ti.",
        "§4§lIt's coming for you.",
    ),
    "hh.sw.voice": (
        "§7§o...%s... ven aquí...",
        "§7§o...%s... come here...",
    ),
    "hh.sw.reveal": (
        "§4§lEso no es una vaca.",
        "§4§lThat isn't a cow.",
    ),
    "hh.e303.come": (
        "Hola, %s. Vamos a jugar.",
        "Hi, %s. Let's play.",
    ),
    "hh.e303.fly": (
        "¿Sabes volar?",
        "Can you fly?",
    ),
    "hh.e303.where": (
        "¿Dónde estás ahora?",
        "Where are you now?",
    ),
    "hh.e303.hit": (
        "No puedes pegar a un administrador.",
        "You can't hit an admin.",
    ),
    "hh.e303.kick": (
        "§fDesconectado",
        "§fDisconnected",
    ),
    "hh.e303.kick_sub": (
        "§7Has sido expulsado del servidor.",
        "§7You were kicked from the server.",
    ),
    "hh.e303.ban.title": (
        "§4Baneado",
        "§4Banned",
    ),
    "hh.e303.ban.body": (
        "Has sido baneado permanentemente de este mundo.\n\nMotivo: §fseguir aquí§r\nBaneado por: §cEntity303§r\n\n§8...pero puedes quedarte, %s. Ahora eres nuestro.",
        "You have been permanently banned from this world.\n\nReason: §fstill being here§r\nBanned by: §cEntity303§r\n\n§8...but you can stay, %s. You're ours now.",
    ),
    "hh.e303.ban.ok": (
        "Aceptar",
        "OK",
    ),
    "hh.e303.chat.1": (
        "Este mundo era mío antes que tuyo.",
        "This world was mine before it was yours.",
    ),
    "hh.e303.chat.2": (
        "Me banearon. No me fui.",
        "They banned me. I didn't leave.",
    ),
    "hh.e303.chat.3": (
        "Bonita casa, %s.",
        "Nice house, %s.",
    ),
    "hh.e303.chat.4": (
        "¿Has visto a Notch? Yo tampoco.",
        "Have you seen Notch? Neither have I.",
    ),
    "hh.e303.chat.5": (
        "/op Entity303",
        "/op Entity303",
    ),
    "hh.e303.chat.6": (
        "Sé dónde guardas tus diamantes.",
        "I know where you keep your diamonds.",
    ),
    # -------------------------------------------------------------- ritual
    "hh.ritual.body": (
        "Con las siete páginas puedes llamarlo por su nombre. Vendrá en su forma verdadera, y esta vez se le puede herir.\n\n§7Debe ser de noche y al aire libre. Lleva armadura, comida, tu mejor espada y la linterna con pilas: la luz le quema.",
        "With all seven pages you can call him by his name. He'll come in his true form, and this time he can be hurt.\n\n§7It must be night, under the open sky. Bring armour, food, your best sword and the flashlight with batteries: light burns him.",
    ),
    "hh.ritual.ready": ("§4Está todo listo. ¿Lo llamas?", "§4Everything is ready. Will you call him?"),
    "hh.ritual.begin": ("§4Decir su nombre", "§4Say his name"),
    "hh.ritual.need_pages": ("§7Te faltan páginas.", "§7You're missing pages."),
    "hh.ritual.need_night": ("§7Solo puede ser de noche.", "§7It can only be at night."),
    "hh.ritual.need_sky": ("§7Tiene que ser al aire libre, bajo el cielo.", "§7It has to be outside, under the sky."),
    "hh.ritual.busy": ("§7Ya viene.", "§7He's already coming."),
    "hh.ritual.title": ("§4§lHEROBRINE", "§4§lHEROBRINE"),
    "hh.ritual.sub": ("§7Has dicho su nombre.", "§7You said his name."),

    # ---------------------------------------------------------------- boss
    "hh.boss.sub": ("§cEl Minero Hueco", "§cThe Hollow Miner"),
    "hh.boss.rage": ("§4§l¡ENFURECE!", "§4§lHE'S ENRAGED!"),
    "hh.boss.bolts": ("§e¡Rayos! ¡Muévete!", "§eLightning! Move!"),
    "hh.boss.summon": ("§7Algo sube de las cuevas...", "§7Something climbs up from the caves..."),
    "hh.boss.retreat.far": ("§7Herobrine se ha perdido en la oscuridad. Volverá cuando lo llames.", "§7Herobrine has gone back into the dark. He'll come when you call."),
    "hh.boss.retreat.dawn": ("§7Sale el sol y Herobrine se retira. Esta noche no.", "§7The sun comes up and Herobrine withdraws. Not tonight."),
    "hh.boss.retreat.died": ("§8Herobrine se ríe. Las páginas siguen en tu diario.", "§8Herobrine laughs. The pages are still in your journal."),
    "hh.win.title": ("§6§lHAS ROTO LA MALDICIÓN", "§6§lYOU BROKE THE CURSE"),
    "hh.win.sub": ("§eAmanece.", "§eDawn."),
    "hh.win.epilogue.1": (
        "§8[???]§r §7El pico cae al suelo, todavía caliente. Es tuyo.",
        "§8[???]§r §7The pickaxe drops to the ground, still warm. It's yours.",
    ),
    "hh.win.epilogue.2": (
        "§8[???]§r §7Durante unos días, el mundo está en silencio. Las páginas se han vuelto a dispersar.",
        "§8[???]§r §7For a few days the world is quiet. The pages have scattered again.",
    ),
    "hh.win.epilogue.3": (
        "§8[???]§r §7Pero él no muere. Nunca muere. Nos vemos pronto. §8(%s)",
        "§8[???]§r §7But he doesn't die. He never dies. See you soon. §8(%s)",
    ),

    # ----------------------------------------------------- items and misc
    "hh.flash.bar": ("§eLinterna", "§eFlashlight"),
    "hh.flash.empty": ("§cLa linterna no tiene pilas.", "§cThe flashlight has no batteries."),
    "hh.flash.swap": ("§7Pila nueva.", "§7Fresh battery."),
    "hh.flash.dead": ("§cLa linterna parpadea... y se apaga.", "§cThe flashlight flickers... and dies."),
    "hh.flash.boss": ("§e¡La luz le quema!", "§eThe light burns him!"),
    "hh.ward.snuffed": ("§4Algo ha apagado tu Farol Protector.", "§4Something snuffed out your Ward Lantern."),
}

LANG = {k: v for k, v in KEPT.items()}
LANG.update(S)
