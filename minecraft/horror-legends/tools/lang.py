"""Every piece of text in the add-on, in Spanish and English.

build.py writes these to the .lang files of both packs (es_ES and es_MX get
the Spanish, en_US and en_GB the English). `\\n` is a line break in forms.
"""

from lang_kept import KEPT

S = {
    # ---------------------------------------------------------------- pack
    "pack.name": ("§4Horror Legends§r", "§4Horror Legends§r"),
    "pack.description": (
        "Terror por capítulos: HerobrineGamer788, el Morador, Herobrine, la Presencia, el Hombre de la Niebla, null y la Noche Roja. Y un final.",
        "Chapter-by-chapter horror: HerobrineGamer788, the Cave Dweller, Herobrine, the Presence, the Man From The Fog, null and the Red Night. And an ending.",
    ),

    # ------------------------------------------------------- entities, items
    "entity.hl:herobrine.name": ("Herobrine", "Herobrine"),
    "entity.hl:herobrine_boss.name": ("Herobrine, el Minero Hueco", "Herobrine, the Hollow Miner"),
    "entity.hl:null.name": ("null", "null"),
    "entity.hl:fog_man.name": ("El Hombre de la Niebla", "The Man From The Fog"),
    "entity.hl:cave_dweller.name": ("El Morador de las Cuevas", "The Cave Dweller"),
    "entity.hl:fake_player.name": ("HerobrineGamer788", "HerobrineGamer788"),
    "item.spawn_egg.entity.hl:herobrine.name": ("Generar Herobrine", "Spawn Herobrine"),
    "item.spawn_egg.entity.hl:herobrine_boss.name": ("Generar a Herobrine (forma verdadera)", "Spawn Herobrine (true form)"),
    "item.spawn_egg.entity.hl:null.name": ("Generar null", "Spawn null"),
    "item.spawn_egg.entity.hl:fog_man.name": ("Generar al Hombre de la Niebla", "Spawn The Man From The Fog"),
    "item.spawn_egg.entity.hl:cave_dweller.name": ("Generar al Morador de las Cuevas", "Spawn The Cave Dweller"),
    "item.spawn_egg.entity.hl:fake_player.name": ("Generar a HerobrineGamer788", "Spawn HerobrineGamer788"),
    "item.hl:journal.name": ("Diario del Superviviente", "Survivor's Journal"),
    "item.hl:flashlight.name": ("Linterna", "Flashlight"),
    "item.hl:battery.name": ("Pila", "Battery"),
    "item.hl:hollow_pickaxe.name": ("§4Pico del Minero Hueco", "§4Pickaxe of the Hollow Miner"),
    "item.hl:page_1.name": ("Página arrancada I", "Torn Page I"),
    "item.hl:page_2.name": ("Página arrancada II", "Torn Page II"),
    "item.hl:page_3.name": ("Página arrancada III", "Torn Page III"),
    "item.hl:page_4.name": ("Página arrancada IV", "Torn Page IV"),
    "item.hl:page_5.name": ("Página arrancada V", "Torn Page V"),
    "tile.hl:corrupted_block.name": ("Bloque Corrupto", "Corrupted Block"),
    "tile.hl:ward_lantern.name": ("Farol Protector", "Ward Lantern"),

    # ---------------------------------------------------------- first join
    "hl.intro.1": (
        "§8[???]§r §7Este mundo no está tan vacío como parece. Cada día que pase, algo más se dará cuenta de que estás aquí.",
        "§8[???]§r §7This world is not as empty as it looks. Every day you stay, something else notices you're here.",
    ),
    "hl.intro.2": (
        "§8[???]§r §7Lee el §fDiario§7. Fabrica una §fLinterna§7 y §fPilas§7. Un §fFarol Protector§7 mantiene tu casa a salvo... casi siempre.",
        "§8[???]§r §7Read the §fJournal§7. Craft a §fFlashlight§7 and §fBatteries§7. A §fWard Lantern§7 keeps your home safe... mostly.",
    ),

    # ------------------------------------------------------------ chapters
    "hl.j.prologue": ("Prólogo", "Prologue"),
    "hl.j.chapter_n": ("Capítulo %s", "Chapter %s"),
    "hl.ch.0.name": ("Solo", "Alone"),
    "hl.ch.0.intro": ("§8[???]§r §7Estás solo en este mundo. ¿Verdad?", "§8[???]§r §7You're alone in this world. Aren't you?"),
    "hl.ch.0.text": (
        "Pasos detrás de ti. Una puerta que se abre en una casa vacía. Un cofre que se cierra. Alguien que susurra tu nombre.\\n\\nNo hay nadie. Todavía.",
        "Footsteps behind you. A door opening in an empty house. A chest closing. Someone whispering your name.\\n\\nThere's nobody there. Yet.",
    ),
    "hl.ch.1.name": ("El jugador", "The Player"),
    "hl.ch.1.intro": ("§8[???]§r §7Alguien más ha encontrado tu mundo.", "§8[???]§r §7Someone else has found your world."),
    "hl.ch.1.text": (
        "Un jugador entra a tu partida. Se llama §fHerobrineGamer788§r. Parece simpático: te saluda, pica, construye, te regala comida.\\n\\nLuego empieza a copiarte. Luego deja de moverse cuando lo miras.\\n\\nNo le pegues cuando se vuelva raro.",
        "A player joins your game. His name is §fHerobrineGamer788§r. He seems friendly: says hi, mines, builds, gives you food.\\n\\nThen he starts copying you. Then he stops moving when you look at him.\\n\\nDon't hit him once he turns strange.",
    ),
    "hl.ch.2.name": ("Lo que vive abajo", "What Lives Below"),
    "hl.ch.2.intro": ("§8[???]§r §7Algo respira en las cuevas.", "§8[???]§r §7Something breathes in the caves."),
    "hl.ch.2.text": (
        "Cuanto más tiempo pasas bajo tierra, más lo oyes: chasquidos, pasitos rápidos, un susurro. Luego asoma detrás de una esquina.\\n\\nSi lo pillas mirando, huye. Si esperas demasiado, viene a por ti.\\n\\nOdia la luz.",
        "The longer you stay underground, the more you hear it: clicking, quick little steps, a whisper. Then it peeks round a corner.\\n\\nCatch it looking and it runs. Wait too long and it comes for you.\\n\\nIt hates light.",
    ),
    "hl.ch.3.name": ("Ojos blancos", "White Eyes"),
    "hl.ch.3.intro": ("§8[???]§r §7Él te ha visto.", "§8[???]§r §7He has seen you."),
    "hl.ch.3.text": (
        "Una figura quieta al borde de lo que ves, con los ojos blancos. Si la miras, desaparece. A veces está justo detrás de ti.\\n\\nPirámides de arena. Árboles sin hojas. Túneles perfectos. Tus antorchas, rojas.",
        "A still figure at the edge of your sight, with white eyes. Look at it and it's gone. Sometimes it's right behind you.\\n\\nSand pyramids. Leafless trees. Perfect tunnels. Your torches, red.",
    ),
    "hl.ch.4.name": ("La Presencia", "The Presence"),
    "hl.ch.4.intro": ("§8[???]§r §7Ya no estás solo en tu casa.", "§8[???]§r §7You're no longer alone in your house."),
    "hl.ch.4.text": (
        "Las luces se apagan una a una. Unos pasos te siguen y se paran cuando te paras. Alguien golpea el cristal de la ventana. Una cajita de música suena en otra habitación.\\n\\nY a veces, al despertar, había alguien junto a tu cama.\\n\\nUn §fFarol Protector§r la mantiene fuera.",
        "The lights go out one by one. Footsteps follow you and stop when you stop. Something taps on the window glass. A music box plays in another room.\\n\\nAnd sometimes, when you wake, someone was standing by your bed.\\n\\nA §fWard Lantern§r keeps it out.",
    ),
    "hl.ch.5.name": ("La niebla", "The Fog"),
    "hl.ch.5.intro": ("§8[???]§r §7Mañana habrá niebla. No la mires.", "§8[???]§r §7There will be fog tomorrow. Don't look into it."),
    "hl.ch.5.text": (
        "Llega una niebla espesa y, dentro, una figura alta y pálida que te observa. Cada vez que apartas la vista está más cerca. Si la miras demasiado, se va... o viene corriendo.\\n\\nDe noche llama a tu puerta. A veces no espera a que abras.",
        "A thick fog rolls in, and inside it a tall pale figure watches you. Every time you look away it's closer. Stare too long and it leaves... or comes running.\\n\\nAt night it knocks on your door. Sometimes it doesn't wait for you to open.",
    ),
    "hl.ch.6.name": ("El error", "The Error"),
    "hl.ch.6.intro": ("§8[???]§r §7Algo se ha roto en el código.", "§8[???]§r §7Something has broken in the code."),
    "hl.ch.6.text": (
        "Aparece por el rabillo del ojo. Escribe en el chat. Los bloques se vuelven magenta y negro. El juego parece colgarse.\\n\\nSi no lo miras, acaba detrás de ti.",
        "It shows up at the corner of your eye. It writes in the chat. Blocks turn magenta and black. The game seems to freeze.\\n\\nIf you don't look at it, it ends up behind you.",
    ),
    "hl.ch.7.name": ("La Noche Roja", "The Red Night"),
    "hl.ch.7.intro": ("§8[???]§r §7La luna se está volviendo roja.", "§8[???]§r §7The moon is turning red."),
    "hl.ch.7.text": (
        "Cada cinco días, una noche entera se tiñe de rojo. Todo lo que te caza viene el doble de veces, y tus faroles pueden apagarse.\\n\\nSi has encontrado las cinco páginas, ya sabes cómo acabar con esto.",
        "Every five days a whole night turns red. Everything that hunts you comes twice as often, and your lanterns may go out.\\n\\nIf you have found all five pages, you know how to end this.",
    ),

    # ------------------------------------------------------------- journal
    "hl.j.title": ("Diario del Superviviente", "Survivor's Journal"),
    "hl.j.day": ("§7Día §f%s§7 del encantamiento", "§7Day §f%s§7 of the haunting"),
    "hl.j.fear": ("§7Miedo:", "§7Fear:"),
    "hl.j.pages_count": ("§7Páginas: §f%s/%s", "§7Pages: §f%s/%s"),
    "hl.j.peace": ("§aHas roto la maldición. Descansa... unos días.", "§aYou broke the curse. Rest... for a few days."),
    "hl.j.wins": ("§7Veces que has vencido a Herobrine: §f%s", "§7Times you have beaten Herobrine: §f%s"),
    "hl.j.chapters": ("Capítulos", "Chapters"),
    "hl.j.chapters_body": ("La historia hasta ahora.", "The story so far."),
    "hl.j.bestiary": ("Bestiario", "Bestiary"),
    "hl.j.bestiary_body": ("Todo lo que has visto. Lo que no, aún es ???", "Everything you have seen. What you haven't is still ???"),
    "hl.j.unknown": ("Aún no lo has visto. Aún.", "You haven't seen it yet. Yet."),
    "hl.j.pages": ("Páginas arrancadas", "Torn Pages"),
    "hl.j.pages_body": (
        "Has leído §f%s de %s§r páginas de un diario que no es tuyo. Cada criatura deja una cuando sobrevives a ella.",
        "You have read §f%s of %s§r pages of a diary that isn't yours. Each creature leaves one when you live through it.",
    ),
    "hl.j.page_missing": ("§8Página %s: perdida", "§8Page %s: missing"),
    "hl.j.ritual": ("§4El Ritual", "§4The Ritual"),
    "hl.j.settings": ("Ajustes", "Settings"),
    "hl.j.settings_body": ("Pulsa una amenaza para activarla o desactivarla.", "Tap a threat to switch it on or off."),
    "hl.j.intensity": ("Intensidad", "Intensity"),
    "hl.j.jumpscares": ("Sustos fuertes", "Jumpscares"),
    "hl.j.tests": ("Pruebas", "Tests"),
    "hl.j.tests_body": ("Provoca cualquier cosa al momento, para verla sin esperar días.", "Trigger anything right now, to see it without waiting days."),
    "hl.j.on": ("§aSÍ", "§aON"),
    "hl.j.off": ("§cNO", "§cOFF"),
    "hl.j.in_days": ("§8en %s días", "§8in %s days"),
    "hl.j.close": ("Cerrar", "Close"),
    "hl.j.back": ("Volver", "Back"),
    "hl.t.lights": ("La Presencia: se apagan las luces", "The Presence: lights out"),
    "hl.t.window": ("La Presencia: la ventana", "The Presence: the window"),
    "hl.t.scare": ("Susto (prueba)", "Jumpscare (test)"),
    "hl.t.items": ("Darme objetos (linterna, pilas, farol, páginas)", "Give me items (flashlight, batteries, lantern, pages)"),
    "hl.t.ritual": ("Empezar el ritual ya", "Start the ritual now"),
    "hl.t.day": ("Avanzar un día", "Skip ahead a day"),

    # ------------------------------------------------------------- threats
    "hl.threat.ambience": ("Sonidos", "Sounds"),
    "hl.threat.gamer": ("HerobrineGamer788", "HerobrineGamer788"),
    "hl.threat.dweller": ("El Morador de las Cuevas", "The Cave Dweller"),
    "hl.threat.herobrine": ("Herobrine", "Herobrine"),
    "hl.threat.presence": ("La Presencia", "The Presence"),
    "hl.threat.fog": ("El Hombre de la Niebla", "The Man From The Fog"),
    "hl.threat.null": ("null", "null"),
    "hl.threat.bloodnight": ("La Noche Roja", "The Red Night"),
    "hl.threat.boss": ("§4El Minero Hueco", "§4The Hollow Miner"),
    "hl.lore.ambience": (
        "§lSonidos§r\\n\\nPasos detrás de ti, puertas, cofres, minería lejana, el siseo de un creeper que no está, respiración, uñas arañando la pared, tu nombre susurrado.\\n\\n§eConsejo:§r no te des la vuelta. O sí.",
        "§lSounds§r\\n\\nFootsteps behind you, doors, chests, distant mining, the hiss of a creeper that isn't there, breathing, nails on the wall, your name whispered.\\n\\n§eTip:§r don't turn around. Or do.",
    ),
    "hl.lore.gamer": (
        "§lHerobrineGamer788§r\\n§8(un jugador que no debería estar)§r\\n\\nEntra a tu mundo como cualquiera. Juega, te copia, se vuelve raro y al final deja de fingir.\\n\\n§eConsejo:§r cuando se revele, busca en el suelo donde estaba.",
        "§lHerobrineGamer788§r\\n§8(a player who shouldn't be here)§r\\n\\nHe joins your world like anyone else. He plays, copies you, turns strange and in the end stops pretending.\\n\\n§eTip:§r when he reveals himself, look on the ground where he stood.",
    ),
    "hl.lore.dweller": (
        "§lEl Morador de las Cuevas§r\\n§8(inspirado en Cave Dweller / From The Caves)§r\\n\\nAlgo que fue una persona y aprendió a moverse como una araña: codos por encima del lomo, cuatro ojos que brillan, dientes de aguja. Te acecha bajo tierra, huye si lo pillas mirando, y te persigue si esperas demasiado.\\n\\n§eConsejo:§r la linterna lo espanta. Sobrevive a él y dejará algo.",
        "§lThe Cave Dweller§r\\n§8(inspired by Cave Dweller / From The Caves)§r\\n\\nSomething that was a person once and learned to move like a spider: elbows above its back, four glowing eyes, needle teeth. It stalks you underground, flees if you catch it looking, and hunts you if you wait too long.\\n\\n§eTip:§r the flashlight drives it off. Live through it and it leaves something behind.",
    ),
    "hl.lore.herobrine": (
        "§lHerobrine§r\\n§8(la leyenda de siempre)§r\\n\\nLo que la mina dejó de un minero: encorvado bajo una joroba de la que sale su propia columna, el pecho reventado, la cara medio quemada, la mandíbula colgando. Te observa desde lejos y desaparece si lo miras. Si le pegas, está detrás de ti.\\n\\n§eConsejo:§r míralo fijamente tres veces.",
        "§lHerobrine§r\\n§8(the old legend)§r\\n\\nWhat the mine left of a miner: bent under a hunch his own spine grows out of, chest burst open, half his face burned, jaw hanging. He watches from afar and vanishes if you look. Hit him and he's behind you.\\n\\n§eTip:§r stare him down three times.",
    ),
    "hl.lore.presence": (
        "§lLa Presencia§r\\n§8(algo en tu casa)§r\\n\\nNo tiene forma. O no quiere que la veas. Apaga las luces, te sigue, llama a la ventana, se queda junto a tu cama.\\n\\n§eConsejo:§r un Farol Protector la mantiene fuera.",
        "§lThe Presence§r\\n§8(something in your house)§r\\n\\nIt has no shape. Or it doesn't want you to see it. It puts out the lights, follows you, taps on the window, stands by your bed.\\n\\n§eTip:§r a Ward Lantern keeps it out.",
    ),
    "hl.lore.fog": (
        "§lEl Hombre de la Niebla§r\\n§8(inspirado en The Man From The Fog)§r\\n\\nPiernas como zancos, una cintura que cabría en una mano, ojos negros enormes y una mandíbula que cuelga abierta. Se acerca cuando no miras. De noche llama a tu puerta.\\n\\n§eConsejo:§r sobrevive a una niebla en la que lo hayas visto.",
        "§lThe Man From The Fog§r\\n§8(inspired by The Man From The Fog)§r\\n\\nStilt legs, a waist you could close a hand round, huge black eyes and a jaw that hangs open. He comes closer when you aren't looking. At night he knocks on your door.\\n\\n§eTip:§r live through a fog in which you saw him.",
    ),
    "hl.lore.null": (
        "§lnull§r\\n§8(inspirado en The Broken Script)§r\\n\\nUn jugador que el juego no consiguió montar: cada parte flota separada, con la caja de F3+B alrededor. Escribe en el chat, corrompe bloques, finge que el juego se cuelga.\\n\\n§eConsejo:§r míralo a los ojos. Dos veces.",
        "§lnull§r\\n§8(inspired by The Broken Script)§r\\n\\nA player the game failed to put back together: every part floating apart, the F3+B box round it. It writes in the chat, corrupts blocks, pretends the game has frozen.\\n\\n§eTip:§r look it in the eye. Twice.",
    ),
    "hl.lore.bloodnight": (
        "§lLa Noche Roja§r\\n\\nCada cinco días, una noche de niebla roja en la que todo viene el doble de veces y tus faroles pueden apagarse. Termina al amanecer.",
        "§lThe Red Night§r\\n\\nEvery five days, a night of red fog when everything comes twice as often and your lanterns may go out. It ends at dawn.",
    ),
    "hl.lore.boss": (
        "§l§4El Minero Hueco§r\\n§8(Herobrine, en su forma verdadera)§r\\n\\nCuatro bloques de hueso ardiendo por dentro, el casco de minero todavía puesto, un pico fundido en el brazo y una corona de vértebras. Se teletransporta, llama a los rayos, invoca cosas de las cuevas y apaga la luz. A mitad de vida, enfurece.\\n\\n§eConsejo:§r la luz de la linterna le quema.",
        "§l§4The Hollow Miner§r\\n§8(Herobrine, in his true form)§r\\n\\nFour blocks of bone burning from within, his miner's helmet still on, a pickaxe fused into his arm and a crown of vertebrae. He teleports, calls down lightning, summons things from the caves and puts out the light. At half health he flies into a rage.\\n\\n§eTip:§r flashlight light burns him.",
    ),

    # --------------------------------------------------------------- pages
    "hl.page.dropped": ("§7Algo ha caído al suelo...", "§7Something fell to the ground..."),
    "hl.page.count": ("§7Páginas leídas: §f%s/%s", "§7Pages read: §f%s/%s"),
    "hl.page.all": (
        "§4Las cinco páginas.§r §7El diario tiembla en tus manos. Ábrelo: ahora sabes cómo llamarlo.",
        "§4All five pages.§r §7The journal trembles in your hands. Open it: now you know how to call him.",
    ),
    "hl.page.1.title": ("I. El jugador", "I. The Player"),
    "hl.page.1.text": (
        "§oDía 3. Entró un jugador a mi mundo. Se llamaba HerobrineGamer788. Me regaló pan.\\n\\nDía 4. Hoy me ha copiado cada paso. Cada bloque que picaba, él lo picaba. Cuando me agachaba, se agachaba.\\n\\nDía 5. Ya no parpadea.",
        "§oDay 3. A player joined my world. His name was HerobrineGamer788. He gave me bread.\\n\\nDay 4. Today he copied my every step. Every block I mined, he mined. When I sneaked, he sneaked.\\n\\nDay 5. He doesn't blink any more.",
    ),
    "hl.page.2.title": ("II. Lo que vive abajo", "II. What Lives Below"),
    "hl.page.2.text": (
        "§oHay algo en la mina que imita mis pasos. Anda a cuatro patas, pero no siempre. Tiene cuatro ojos.\\n\\nOdia la luz. Llevo la linterna siempre encendida.\\n\\nSe me están acabando las pilas.",
        "§oThere's something in the mine that copies my footsteps. It walks on all fours, but not always. It has four eyes.\\n\\nIt hates light. I keep the flashlight on all the time.\\n\\nI'm running out of batteries.",
    ),
    "hl.page.3.title": ("III. Ojos blancos", "III. White Eyes"),
    "hl.page.3.text": (
        "§oEncontré su casco en el fondo de la mina. Era un minero. Bajó demasiado hondo, donde la piedra respira, y algo subió con él.\\n\\nAhora tiene los ojos blancos y la columna fuera del cuerpo. Sigue arrastrando su pico.",
        "§oI found his helmet at the bottom of the mine. He was a miner. He dug too deep, where the stone breathes, and something came back up with him.\\n\\nNow his eyes are white and his spine is outside his body. He still drags his pickaxe.",
    ),
    "hl.page.4.title": ("IV. La niebla", "IV. The Fog"),
    "hl.page.4.text": (
        "§oLa niebla no es niebla. Es él, esperando.\\n\\nSi lo miras, se va. Si no lo miras, se acerca.\\n\\nLlamó a mi puerta tres veces. La cuarta no llamó.",
        "§oThe fog isn't fog. It's him, waiting.\\n\\nIf you look at him, he leaves. If you don't, he comes closer.\\n\\nHe knocked on my door three times. The fourth time he didn't knock.",
    ),
    "hl.page.5.title": ("V. El error", "V. The Error"),
    "hl.page.5.text": (
        "§oLos bloques se rompen en magenta y negro. El mundo se deshace donde él pisa.\\n\\nSi juntas estas cinco páginas, llámalo por su nombre de noche, bajo el cielo. Solo así se le puede herir.\\n\\nYo ya no puedo. Creo que ya no soy yo el que está jugando.",
        "§oBlocks break into magenta and black. The world comes apart where he walks.\\n\\nIf you gather these five pages, call him by his name at night, under the sky. Only then can he be hurt.\\n\\nI can't any more. I don't think I'm the one playing now.",
    ),

    # -------------------------------------------------------------- ritual
    "hl.ritual.body": (
        "Con las cinco páginas puedes llamarlo por su nombre. Vendrá en su forma verdadera, y esta vez se le puede herir.\\n\\n§7Debe ser de noche y al aire libre. Lleva armadura, comida, tu mejor espada y la linterna con pilas: la luz le quema.",
        "With all five pages you can call him by his name. He'll come in his true form, and this time he can be hurt.\\n\\n§7It must be night, under the open sky. Bring armour, food, your best sword and the flashlight with batteries: light burns him.",
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
