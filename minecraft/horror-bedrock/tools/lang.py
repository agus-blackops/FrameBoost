"""Every piece of text in the add-on, in Spanish and English.

build.py writes these to the .lang files of both packs (es_ES and es_MX get
the Spanish, en_US and en_GB the English). `\n` is a line break in forms.
"""

NAMES = {
    "green_steve": ("Green Steve", "Green Steve"),
    "knocker": ("The Knocker", "The Knocker"),
    "smile_dog": ("Smile Dog", "Smile Dog"),
    "lick": ("The Lick", "The Lick"),
    "mimicer": ("The Mimicer", "The Mimicer"),
    "boiled_one": ("The Boiled One", "The Boiled One"),
    "jeff": ("Jeff the Killer", "Jeff the Killer"),
    "slenderman": ("Slenderman", "Slenderman"),
}

S = {
    # ------------------------------------------------------------------ pack
    "pack.name": ("§4Horror Legends: §fBedrock§r", "§4Horror Legends: §fBedrock§r"),
    "pack.description": (
        "Terror en tres actos con las leyendas de Minecraft y las creepypastas clásicas: Green Steve, The Knocker, "
        "Smile Dog, The Lick, The Mimicer, The Boiled One, Jeff the Killer y Slenderman con sus ocho páginas. "
        "Estructuras, notas con la historia e introducción.",
        "Horror in three acts with the legends of Minecraft and the classic creepypastas: Green Steve, The Knocker, "
        "Smile Dog, The Lick, The Mimicer, The Boiled One, Jeff the Killer and Slenderman with his eight pages. "
        "Structures, lore notes and an introduction.",
    ),

    # ----------------------------------------------------------------- items
    "item.hb:journal.name": ("§eDiario", "§eJournal"),
    "item.hb:flashlight.name": ("Linterna", "Flashlight"),
    "item.hb:battery.name": ("Pila", "Battery"),
    "item.hb:page.name": ("§fPágina", "§fPage"),
    "tile.hb:ward_lantern.name": ("Farol Protector", "Ward Lantern"),
    "tile.hb:slender_page.name": ("Página", "Page"),

    # ------------------------------------------------------------------ acts
    "hb.act.n": ("§4Acto %s", "§4Act %s"),
    "hb.act.1.name": ("§fSe ha unido a la partida", "§fJoined the Game"),
    "hb.act.1.intro": (
        "§7Alguien se ha unido a la partida. En este mundo no hay nadie más.",
        "§7Someone joined the game. There is nobody else in this world.",
    ),
    "hb.act.1.text": (
        "Primero, los ruidos: pasos que no están, una puerta, tu nombre.\n\n"
        "Luego, en el chat: §eGreen Steve se ha unido a la partida§r. Por la noche alguien llama a tu puerta, "
        "tres golpes lentos, y espera a que abras: §fThe Knocker§r. Y en la esquina oscura de la habitación hay un "
        "husky sentado que te sonríe con dientes de persona: §fSmile Dog§r.\n\n"
        "Alguien estuvo en este mundo antes que tú. Lo que construyó sigue ahí fuera, y en cada sitio dejó una nota.",
        "First, the noises: footsteps that aren't there, a door, your name.\n\n"
        "Then, in the chat: §eGreen Steve joined the game§r. At night someone knocks on your door, three slow "
        "knocks, and waits for you to open it: §fThe Knocker§r. And in the dark corner of the room a husky sits and "
        "smiles at you with human teeth: §fSmile Dog§r.\n\n"
        "Someone was in this world before you. What they built is still out there, and in each place they left a note.",
    ),
    "hb.act.2.name": ("§fLos Otros", "§fThe Others"),
    "hb.act.2.intro": (
        "§7Ya no son solo historias. Hay otros, y saben dónde estás.",
        "§7They aren't just stories any more. There are others, and they know where you are.",
    ),
    "hb.act.2.text": (
        "En el fondo de las cuevas, entre cruces de piedra, algo de pie con una máscara blanca: §fThe Lick§r.\n\n"
        "En el chat aparecen frases con tu nombre que tú no has escrito. Algo demasiado alto te imita desde los "
        "árboles: §fThe Mimicer§r.\n\n"
        "Una emisora de números en una frecuencia muerta, y una figura roja de cinco bloques en el campo. No la "
        "mires: §fThe Boiled One§r.\n\n"
        "Las luces de tu casa se apagan una a una. Alguien susurra: «Ve a dormir». §fJeff the Killer§r.",
        "At the back of the caves, among crosses of stone, something standing in a white mask: §fThe Lick§r.\n\n"
        "Lines with your name on them appear in the chat, things you never wrote. Something far too tall mimics "
        "you from the trees: §fThe Mimicer§r.\n\n"
        "A numbers station on a dead frequency, and a red figure five blocks tall out in the field. Don't look at "
        "it: §fThe Boiled One§r.\n\n"
        "The lights in your house go out one by one. Someone whispers: \"Go to sleep.\" §fJeff the Killer§r.",
    ),
    "hb.act.3.name": ("§fLas Páginas", "§fThe Pages"),
    "hb.act.3.intro": (
        "§7Llega la niebla. Hay páginas clavadas en los árboles.",
        "§7The fog comes in. There are pages pinned to the trees.",
    ),
    "hb.act.3.text": (
        "Una noche de niebla aparecen ocho páginas clavadas en los troncos a tu alrededor. Arráncalas todas.\n\n"
        "Con cada página los tambores suenan más fuerte, la niebla se espesa y él está más cerca: muy alto, muy "
        "delgado, traje negro y sin cara. §fSlenderman§r.\n\n"
        "No le mires. Mirarle trae la estática, y la estática duele. Si te alcanza, o se acaba el tiempo, las páginas "
        "se las lleva el viento.\n\n"
        "Encuentra las ocho y te dejará en paz unas noches.",
        "One foggy night eight pages appear, pinned to the trunks around you. Tear them all down.\n\n"
        "With every page the drums get louder, the fog thicker, and he is closer: very tall, very thin, a black "
        "suit and no face. §fSlenderman§r.\n\n"
        "Don't look at him. Looking brings the static, and the static hurts. If he reaches you, or time runs out, the "
        "wind takes the pages.\n\n"
        "Find all eight and he will leave you in peace for a few nights.",
    ),

    # ------------------------------------------------------------- arrivals
    "hb.arrive.greensteve": ("§e§oGreen Steve se ha unido a la partida.", "§e§oGreen Steve joined the game."),
    "hb.arrive.knocker": ("§7§oEsta noche alguien llamará a tu puerta. No abras.", "§7§oTonight someone will knock at your door. Don't open it."),
    "hb.arrive.smiledog": ("§7§oHay un perro sentado en la oscuridad. Sonríe.", "§7§oThere is a dog sitting in the dark. It's smiling."),
    "hb.arrive.lick": ("§7§oEn las cuevas han aparecido cruces en el suelo.", "§7§oCrosses have appeared on the floors of the caves."),
    "hb.arrive.mimicer": ("§7§oAlgo ha aprendido tu voz.", "§7§oSomething has learned your voice."),
    "hb.arrive.boiled": ("§4§oHay una emisora nueva. Solo lee números.", "§4§oThere is a new station. It only reads numbers."),
    "hb.arrive.jeff": ("§7§oVe a dormir.", "§7§oGo to sleep."),
    "hb.arrive.slender": ("§8§oSiempre observa. Sin ojos.", "§8§oAlways watches. No eyes."),
    "hb.arrive.ruins": ("§7§oAlguien estuvo aquí antes que tú.", "§7§oSomeone was here before you."),

    # --------------------------------------------------------------- threats
    "hb.threat.ambience": ("Sonidos", "Sounds"),
    "hb.threat.scares": ("Sustos", "Scares"),
    "hb.threat.ruins": ("Estructuras y notas", "Structures and notes"),
    "hb.threat.greensteve": ("Green Steve", "Green Steve"),
    "hb.threat.knocker": ("The Knocker", "The Knocker"),
    "hb.threat.smiledog": ("Smile Dog", "Smile Dog"),
    "hb.threat.lick": ("The Lick", "The Lick"),
    "hb.threat.mimicer": ("The Mimicer", "The Mimicer"),
    "hb.threat.boiled": ("The Boiled One", "The Boiled One"),
    "hb.threat.jeff": ("Jeff the Killer", "Jeff the Killer"),
    "hb.threat.slender": ("Slenderman", "Slenderman"),

    "hb.lore.ambience": (
        "§lSonidos§r\n\nPasos detrás de ti, una puerta, un cofre que se abre solo, el siseo de un creeper que no está, "
        "respiración, golpes en la pared, tu nombre susurrado. Vienen de un sitio: oirás de qué lado.",
        "§lSounds§r\n\nFootsteps behind you, a door, a chest opening by itself, the hiss of a creeper that isn't "
        "there, breathing, knocks on the wall, your name whispered. They come from somewhere: you will hear which side.",
    ),
    "hb.lore.scares": (
        "§lSustos§r\n\nUna cara delante de la tuya durante un parpadeo. Aliento en la nuca. Una puerta que se cierra "
        "de golpe en una casa vacía. La pantalla rompiéndose en estática. Un golpe que duele y no deja marca.",
        "§lScares§r\n\nA face in front of yours for the length of a blink. Breath on your neck. A door slamming in an "
        "empty house. The screen tearing into static. A blow that hurts and leaves no mark.",
    ),
    "hb.lore.ruins": (
        "§lEstructuras§r\n\nAlguien estuvo en este mundo antes que tú. Con los días irás encontrando lo que "
        "construyó: una cabaña, una perrera, un campo de cruces, un templo azul, una antena de radio, un campamento. "
        "En cada uno hay un cofre con una página de su diario. El chat te dice más o menos dónde.",
        "§lStructures§r\n\nSomeone was in this world before you. As the days go by you will find what they built: a "
        "cabin, a kennel, a field of crosses, a blue temple, a radio mast, a camp. In each there is a chest with a "
        "page of their diary. The chat tells you roughly where.",
    ),
    "hb.lore.greensteve": (
        "§lGreen Steve§r\n\n«Green Steve se ha unido a la partida». Es igual que Steve, pero verde de arriba abajo y "
        "con los ojos en blanco. Escribe «Voy para allá». Cada vez que apartas la vista está más cerca. Cuando te "
        "alcanza dice «Hola, hermano», y empieza el siseo a tu alrededor.\n\n§eConsejo:§r no le quites el ojo de "
        "encima... aunque mirarle demasiado también le llama.",
        "§lGreen Steve§r\n\n\"Green Steve joined the game.\" He looks exactly like Steve, but green all over, with "
        "blank eyes. He writes \"I am coming.\" Every time you look away he is closer. When he reaches you he says "
        "\"Hello, brother\", and the hissing starts all around you.\n\n§eTip:§r don't take your eyes off him... "
        "though staring too long calls him too.",
    ),
    "hb.lore.knocker": (
        "§lThe Knocker§r\n\nUn muerto pálido, demasiado alto y delgado, con dedos largos y la boca colgando. Aprende "
        "dónde vives. Por la noche llama a tu puerta: tres golpes lentos, y otra vez. Nunca entra por su cuenta: "
        "espera a que abras. Si no abres, deja de llamar... y golpea una ventana. Sabe dónde duermes.\n\n"
        "§eConsejo:§r no abras la puerta.",
        "§lThe Knocker§r\n\nA pale dead thing, too tall and too thin, with long fingers and a hanging mouth. It "
        "learns where you live. At night it knocks on your door: three slow knocks, and again. It never comes in on "
        "its own: it waits for you to open. If you don't, the knocking stops... and it taps on a window. It knows "
        "where you sleep.\n\n§eTip:§r don't open the door.",
    ),
    "hb.lore.smiledog": (
        "§lSmile Dog§r\n\nUn husky sentado en la oscuridad de una habitación, con el hocico estirado en una sonrisa "
        "humana llena de dientes de persona. Si lo miras demasiado tiempo se te mete en la cabeza: la foto, el "
        "flash, las palabras. DIFUNDE LA PALABRA. Luego se levanta.\n\n§eConsejo:§r se le puede matar cuando corre.",
        "§lSmile Dog§r\n\nA husky sitting in the dark of a room, its muzzle pulled back into a human smile full of "
        "human teeth. Look at it too long and it gets into your head: the picture, the flash, the words. SPREAD THE "
        "WORD. Then it gets up.\n\n§eTip:§r it can be killed once it runs.",
    ),
    "hb.lore.lick": (
        "§lThe Lick§r\n\nAl fondo de una cueva, siempre al fondo, una figura de negro completamente quieta, de cara a "
        "ti. Una máscara blanca de porcelana y una lengua larga y roja colgando de la sonrisa. A su alrededor, el "
        "suelo está marcado con cruces de piedra. No hace nada. Hasta que te acercas o le apuntas con la luz.\n\n"
        "§eConsejo:§r no se le puede herir. Sal de la cueva.",
        "§lThe Lick§r\n\nAt the back of a cave, always at the back, a figure in black standing perfectly still, "
        "facing you. A white porcelain mask and a long red tongue hanging out of the smile. Around it, the floor is "
        "marked with crosses of stone. It does nothing. Until you come close or shine a light on it.\n\n"
        "§eTip:§r it can't be hurt. Get out of the cave.",
    ),
    "hb.lore.mimicer": (
        "§lThe Mimicer§r\n\nTiene forma de Steve, pero mal: demasiado alto, brazos y piernas demasiado largos, "
        "agujeros vacíos por ojos. Habla. En el chat, con TU nombre, dice cosas que tú nunca dijiste; desde los "
        "árboles hace ruidos de aldeano. Si lo miras mucho, o te acercas, corre hacia ti con esas piernas largas.\n\n"
        "§eConsejo:§r no sabe trepar. Sube tres bloques.",
        "§lThe Mimicer§r\n\nShaped like Steve, but wrong: too tall, arms and legs too long, empty holes for eyes. It "
        "talks. In the chat, under YOUR name, it says things you never said; from the trees, it makes villager "
        "sounds. Look at it too long, or get too close, and it runs at you on those long legs.\n\n§eTip:§r it "
        "can't climb. Build up three blocks.",
    ),
    "hb.lore.boiled": (
        "§lThe Boiled One§r\n\nEl Doctor Nowhere. Primero, la emisora: una voz plana leyendo números en una "
        "frecuencia que nadie usa. Luego aparece de pie en el campo: una figura carmesí y marchita de cinco bloques, "
        "con la cabeza deforme y dos puntos rojos por ojos. Los que la miran demasiado sufren el Fenómeno: todo se "
        "vuelve rojo, no te tienes en pie y duele.\n\n§eConsejo:§r NO LO MIRES.",
        "§lThe Boiled One§r\n\nDoctor Nowhere. First, the station: a flat voice reading numbers on a frequency "
        "nobody uses. Then it is standing in the field: a crimson, withered figure five blocks tall, with a "
        "misshapen head and two red points for eyes. Those who look too long suffer the Phenomenon: everything goes "
        "red, you can't stand straight, and it hurts.\n\n§eTip:§r DON'T LOOK AT IT.",
    ),
    "hb.lore.jeff": (
        "§lJeff the Killer§r\n\nSudadera blanca, pelo negro, la cara blanca como la tiza: ojos que no se pueden "
        "cerrar y una sonrisa cortada en las mejillas. Entra en las casas de noche. Las luces se apagan una a una y "
        "alguien susurra muy cerca: «Ve a dormir». Si duermes, al despertar está junto a la cama.\n\n§eConsejo:§r "
        "a él sí se le puede matar.",
        "§lJeff the Killer§r\n\nWhite hoodie, black hair, a chalk-white face: eyes that can't close and a smile cut "
        "into the cheeks. He comes into houses at night. The lights go out one by one and someone whispers, very "
        "close: \"Go to sleep.\" If you do, when you wake he is beside the bed.\n\n§eTip:§r he can be killed.",
    ),
    "hb.lore.slender": (
        "§lSlenderman§r\n\nMuy alto, muy delgado, de pie entre los árboles al borde de lo que ves. Traje negro, "
        "camisa blanca, sin cara. Cuando quiere, de su espalda salen largos tentáculos negros. Una noche de niebla "
        "clava ocho páginas en los árboles. Encuéntralas todas antes de que te encuentre él.\n\n§eConsejo:§r no le "
        "mires. Cuando está detrás de ti, corre.",
        "§lSlenderman§r\n\nVery tall, very thin, standing among the trees at the edge of what you can see. Black "
        "suit, white shirt, no face. When he wants to, long black tendrils come out of his back. One foggy night he "
        "pins eight pages to the trees. Find them all before he finds you.\n\n§eTip:§r don't look at him. When "
        "he's behind you, run.",
    ),

    # ----------------------------------------------------------- the journal
    "hb.j.title": ("§lDiario", "§lJournal"),
    "hb.j.day": ("§7Día §f%s§7 del encantamiento", "§7Day §f%s§7 of the haunting"),
    "hb.j.fear": ("§7Miedo:", "§7Fear:"),
    "hb.j.peace": ("§aHas encontrado las ocho páginas. Descansa... unas noches.", "§aYou found all eight pages. Rest... for a few nights."),
    "hb.j.wins": ("§7Veces que has encontrado las ocho páginas: §f%s", "§7Times you have found all eight pages: §f%s"),
    "hb.j.acts": ("La historia", "The story"),
    "hb.j.acts_body": ("Tres actos. Cada uno empieza al caer la noche.", "Three acts. Each one begins at nightfall."),
    "hb.j.bestiary": ("Criaturas", "Creatures"),
    "hb.j.bestiary_body": ("Lo que has visto. Lo que aún no, son signos de interrogación.", "What you have seen. What you haven't yet are question marks."),
    "hb.j.notes": ("Notas", "Notes"),
    "hb.j.notes_body": (
        "Las páginas del diario de quien estuvo aquí antes que tú. Están en los cofres de las estructuras; úsalas "
        "para leerlas.",
        "The pages of the diary of whoever was here before you. They are in the chests of the structures; use them "
        "to read them.",
    ),
    "hb.j.settings": ("Ajustes", "Settings"),
    "hb.j.settings_body": ("Pulsa algo para cambiarlo. Lo que desactives desaparece al momento.", "Press something to change it. Whatever you switch off is gone at once."),
    "hb.j.tests": ("Pruebas", "Tests"),
    "hb.j.tests_body": ("Para ver cualquier cosa ahora mismo, sin esperar.", "To see anything right now, without waiting."),
    "hb.j.close": ("Cerrar", "Close"),
    "hb.j.back": ("Volver", "Back"),
    "hb.j.unknown": ("Aún no lo has visto.", "You haven't seen it yet."),
    "hb.j.in_days": ("§8en %s días", "§8in %s days"),
    "hb.j.on": ("§aSí", "§aOn"),
    "hb.j.off": ("§cNo", "§cOff"),
    "hb.j.intensity": ("Intensidad", "Intensity"),
    "hb.j.jumpscares": ("Sustos fuertes", "Jumpscares"),
    "hb.j.loud": ("Sonidos más fuertes", "Louder sounds"),
    "hb.j.audio3d": ("Sonido 3D", "3D sound"),
    "hb.int.low": ("Baja", "Low"),
    "hb.int.normal": ("Normal", "Normal"),
    "hb.int.high": ("Alta", "High"),
    "hb.int.nightmare": ("§4Pesadilla", "§4Nightmare"),
    "hb.t.scare": ("Susto", "Jumpscare"),
    "hb.t.items": ("Objetos y notas", "Items and notes"),
    "hb.t.day": ("Avanzar un día", "Skip a day"),
    "hb.t.intro": ("Ver la introducción", "Watch the introduction"),

    # ------------------------------------------------------- sounds, scares
    "hb.ambience.whisper": ("§8§o...%s...", "§8§o...%s..."),
    "hb.scare.neck": ("§7§oAlgo te ha rozado la nuca.", "§7§oSomething brushed the back of your neck."),
    "hb.scare.name_sub": ("§8te estoy mirando", "§8i'm watching you"),

    # ----------------------------------------------------------- Green Steve
    "hb.gs.joined": ("Green Steve se ha unido a la partida", "Green Steve joined the game"),
    "hb.gs.left": ("Green Steve ha abandonado la partida", "Green Steve left the game"),
    "hb.gs.coming": ("Voy para allá.", "I am coming."),
    "hb.gs.brother": ("Hola, hermano.", "Hello, brother."),
    "hb.gs.sub": ("§2Hola, hermano.", "§2Hello, brother."),
    "hb.gs.hurt": ("§2§o¿Por qué me haces daño, hermano?", "§2§oWhy do you hurt me, brother?"),

    # ------------------------------------------------------------ The Knocker
    "hb.kn.door": ("§7§oAlguien está llamando a la puerta.", "§7§oSomeone is knocking at the door."),
    "hb.kn.window": ("§7§oAlgo golpea el cristal.", "§7§oSomething is tapping on the glass."),
    "hb.kn.sub": ("§fNo debiste abrir.", "§fYou shouldn't have opened it."),
    "hb.kn.bed": ("§fSabe dónde duermes.", "§fIt knows where you sleep."),

    # ------------------------------------------------------------- Smile Dog
    "hb.sd.word": ("difunde la palabra", "spread the word"),
    "hb.sd.word_big": ("§4§lDIFUNDE LA PALABRA", "§4§lSPREAD THE WORD"),
    "hb.sd.look": ("§7§oNo puedes dejar de mirarlo.", "§7§oYou can't stop looking at it."),
    "hb.sd.sub": ("§4difunde la palabra", "§4spread the word"),

    # -------------------------------------------------------------- The Lick
    "hb.lk.crosses": ("§7§oHay cruces en el suelo detrás de ti.", "§7§oThere are crosses on the floor behind you."),
    "hb.lk.seen": ("§7§oAl fondo de la cueva hay alguien de pie.", "§7§oSomeone is standing at the back of the cave."),
    "hb.lk.sub": ("§4Te has acercado demasiado.", "§4You came too close."),

    # ----------------------------------------------------------- The Mimicer
    "hb.mi.say.1": ("¿hola?", "hello?"),
    "hb.mi.say.2": ("¿hay alguien ahí?", "is anyone there?"),
    "hb.mi.say.3": ("ven aquí", "come here"),
    "hb.mi.say.4": ("estoy aquí", "i'm over here"),
    "hb.mi.say.5": ("ayuda", "help"),
    "hb.mi.say.6": ("¿por qué no contestas?", "why won't you answer?"),
    "hb.mi.say.7": ("te veo", "i see you"),
    "hb.mi.say.8": ("baja de ahí", "come down"),
    "hb.mi.seen": ("§7§oEs demasiado alto.", "§7§oIt's far too tall."),
    "hb.mi.sub": ("§7Tiene tu voz.", "§7It has your voice."),
    "hb.mi.up": ("§7§oSe ha quedado abajo, mirándote.", "§7§oIt stays at the bottom, looking up at you."),

    # -------------------------------------------------------- The Boiled One
    "hb.bo.signal": ("§4· · · señal desconocida · · ·", "§4· · · unknown signal · · ·"),
    "hb.bo.line.1": ("§4ESTÁS MIRANDO", "§4YOU ARE LOOKING"),
    "hb.bo.line.2": ("§4EL FENÓMENO HA EMPEZADO", "§4THE PHENOMENON HAS BEGUN"),
    "hb.bo.line.3": ("§4NO HAY NADIE EN NINGUNA PARTE", "§4THERE IS NO ONE NOWHERE"),
    "hb.bo.line.4": ("§4EL DOCTOR NOWHERE TE ESCUCHA", "§4DOCTOR NOWHERE HEARS YOU"),
    "hb.bo.stare": ("§4No lo mires.", "§4Don't look at it."),
    "hb.bo.sub": ("§4EL FENÓMENO", "§4THE PHENOMENON"),
    "hb.bo.bed": ("§4Te miraba mientras dormías.", "§4It was watching you sleep."),

    # ------------------------------------------------------- Jeff the Killer
    "hb.jf.sleep": ("Ve a dormir.", "Go to sleep."),
    "hb.jf.sub": ("§fVe a dormir.", "§fGo to sleep."),
    "hb.jf.bed": ("§fVe a dormir.", "§fGo to sleep."),
    "hb.jf.cut": ("§4§oUn corte.", "§4§oA cut."),

    # ------------------------------------------------------------ Slenderman
    "hb.sl.title": ("§f§lLAS OCHO PÁGINAS", "§f§lTHE EIGHT PAGES"),
    "hb.sl.goal": ("§7Encuentra las %s páginas en los árboles. No le mires.", "§7Find the %s pages on the trees. Don't look at him."),
    "hb.sl.count": ("§fPáginas: %s/%s", "§fPages: %s/%s"),
    "hb.sl.left": ("§7Quedan unos §f%s§7 minutos.", "§7About §f%s§7 minutes left."),
    "hb.sl.page.1": ("§8§o«SIEMPRE OBSERVA. SIN OJOS.»", "§8§o\"ALWAYS WATCHES. NO EYES.\""),
    "hb.sl.page.2": ("§8§o«DÉJAME EN PAZ.»", "§8§o\"LEAVE ME ALONE.\""),
    "hb.sl.page.3": ("§8§o«NO PUEDO CORRER.»", "§8§o\"CAN'T RUN.\""),
    "hb.sl.page.4": ("§8§o«NO MIRES... O TE ATRAPA.»", "§8§o\"DON'T LOOK... OR IT TAKES YOU.\""),
    "hb.sl.page.5": ("§8§o«SIGUE.»", "§8§o\"FOLLOWS.\""),
    "hb.sl.page.6": ("§8§o«NO NO NO NO NO NO»", "§8§o\"NO NO NO NO NO NO\""),
    "hb.sl.page.7": ("§8§o«NO PUEDES ESCONDERTE.»", "§8§o\"YOU CAN'T HIDE.\""),
    "hb.sl.page.8": ("§8§o«ESTÁ DETRÁS DE TI.»", "§8§o\"HE IS BEHIND YOU.\""),
    "hb.sl.sub": ("§8§kxxxx§r §fNo le mires §8§kxxxx", "§8§kxxxx§r §fDon't look at him §8§kxxxx"),
    "hb.sl.caught": ("§8Te ha alcanzado. Las páginas se las lleva el viento.", "§8He reached you. The wind takes the pages."),
    "hb.sl.time": ("§8Se acabó el tiempo. Las páginas se las lleva el viento.", "§8Time is up. The wind takes the pages."),
    "hb.sl.win": ("§f§l8/8", "§f§l8/8"),
    "hb.sl.win_sub": ("§7La niebla se levanta. Por ahora.", "§7The fog lifts. For now."),

    # ------------------------------------------------------------ structures
    "hb.ruin.cabin": ("Hay una cabaña abandonada", "There is an abandoned cabin"),
    "hb.ruin.kennel": ("Hay una perrera vacía", "There is an empty kennel"),
    "hb.ruin.crosses": ("Hay un campo de cruces de piedra", "There is a field of stone crosses"),
    "hb.ruin.temple": ("Hay un pequeño templo azul", "There is a small blue temple"),
    "hb.ruin.tower": ("Hay una antena de radio", "There is a radio mast"),
    "hb.ruin.camp": ("Hay un campamento abandonado", "There is an abandoned camp"),
    "hb.ruin.where": ("a unos %s bloques hacia el %s.", "about %s blocks to the %s."),
    "hb.dir.n": ("norte", "north"),
    "hb.dir.ne": ("noreste", "northeast"),
    "hb.dir.e": ("este", "east"),
    "hb.dir.se": ("sureste", "southeast"),
    "hb.dir.s": ("sur", "south"),
    "hb.dir.sw": ("suroeste", "southwest"),
    "hb.dir.w": ("oeste", "west"),
    "hb.dir.nw": ("noroeste", "northwest"),

    # ------------------------------------------------------------ intro
    "hb.intro.1": ("§7Este mundo era tuyo. Ya no lo es.", "§7This world was yours. Not any more."),
    "hb.intro.2": ("§7Alguien estuvo aquí antes que tú. Dejó notas.", "§7Someone was here before you. They left notes."),
    "hb.intro.3": ("§7Lee las notas. Busca las páginas. No los mires demasiado.", "§7Read the notes. Find the pages. Don't look at them for too long."),
    "hb.intro.skip": ("§8(Agáchate para saltar la introducción)", "§8(Sneak to skip the introduction)"),
    "hb.intro.msg.1": (
        "§7Tienes un §eDiario§7: úsalo para ver la historia, las criaturas y las notas, y para cambiar o desactivar lo que quieras.",
        "§7You have a §eJournal§7: use it to see the story, the creatures and the notes, and to change or switch off whatever you like.",
    ),
    "hb.intro.msg.2": (
        "§7Las notas están en cofres, en sitios que alguien construyó. El chat te dirá dónde.",
        "§7The notes are in chests, in places someone built. The chat will tell you where.",
    ),

    # ----------------------------------------------------- items and misc
    "hb.flash.bar": ("§eLinterna", "§eFlashlight"),
    "hb.flash.empty": ("§cLa linterna no tiene pilas.", "§cThe flashlight has no batteries."),
    "hb.flash.swap": ("§7Pila nueva.", "§7Fresh battery."),
    "hb.flash.dead": ("§cLa linterna parpadea... y se apaga.", "§cThe flashlight flickers... and dies."),
    "hb.ward.snuffed": ("§4Algo ha apagado tu Farol Protector.", "§4Something snuffed out your Ward Lantern."),
}

# The diary of whoever was here before you (items/notes.js).
NOTES = {
    1: (("Día 1", "Day 1"), (
        "Empecé este mundo para construir tranquilo. Semilla nueva, nadie más.\n\nEsta tarde salió en el chat: "
        "«Green Steve se ha unido a la partida». No hay nadie más. Escribí «¿hola?». Contestó: «Voy para allá».\n\n"
        "Lo vi en el borde del bosque. Era Steve. Pero verde. Entero. Y los ojos en blanco.\n\nCada vez que miraba "
        "a otro lado, estaba más cerca.",
        "I started this world to build in peace. New seed, nobody else.\n\nThis afternoon the chat said: \"Green "
        "Steve joined the game\". There is nobody else. I wrote \"hello?\". He answered: \"I am coming.\"\n\nI saw "
        "him at the edge of the forest. It was Steve. But green. All of him. And the eyes blank.\n\nEvery time I "
        "looked away, he was closer.",
    )),
    2: (("Día 2", "Day 2"), (
        "Por la noche llaman a la puerta. Tres golpes lentos. Luego otra vez.\n\nNo abrí. Por la mañana había marcas "
        "de dedos largos en la madera. Por fuera.\n\nLo llamo el que llama. No entra si no le abres. Pero sabe dónde "
        "duermo: anoche me desperté y estaba de pie junto a la cama.\n\nNo abras la puerta.",
        "At night someone knocks on the door. Three slow knocks. Then again.\n\nI didn't open it. In the morning "
        "there were marks of long fingers in the wood. On the outside.\n\nI call it the Knocker. It doesn't come in "
        "unless you open. But it knows where I sleep: last night I woke and it was standing beside the bed.\n\n"
        "Don't open the door.",
    )),
    3: (("La perrera", "The kennel"), (
        "Tenía un perro. Un husky. Una noche se sentó en la esquina oscura del cuarto y me miró.\n\nSonreía. No como "
        "sonríe un perro: con dientes de persona. Filas de ellos.\n\nNo lo mires mucho tiempo. Después de verlo solo "
        "podía pensar en una cosa.\n\nDIFUNDE LA PALABRA.\n\nSi estás leyendo esto, ya lo he hecho.",
        "I had a dog. A husky. One night it sat in the dark corner of the room and looked at me.\n\nIt was smiling. "
        "Not the way a dog smiles: with human teeth. Rows of them.\n\nDon't look at it for long. After I saw it I "
        "could only think of one thing.\n\nSPREAD THE WORD.\n\nIf you are reading this, I already have.",
    )),
    4: (("Las cruces", "The crosses"), (
        "En las cuevas hay cruces de piedra en el suelo. Y al fondo, siempre al fondo, hay alguien de pie.\n\nDe negro, "
        "con una máscara blanca y una lengua larga y roja colgando de la sonrisa. No se mueve.\n\nNo le apuntes con la "
        "luz. No te acerques. No se le puede herir.\n\nPuse estas cruces aquí arriba para acordarme. No vuelvas a bajar.",
        "In the caves there are crosses of stone on the floor. And at the back, always at the back, someone is "
        "standing.\n\nIn black, with a white mask and a long red tongue hanging out of the smile. It doesn't move.\n\n"
        "Don't shine your light on it. Don't go close. It can't be hurt.\n\nI put these crosses up here to remember. "
        "Don't go back down.",
    )),
    5: (("El templo azul", "The blue temple"), (
        "Oí mi nombre desde los árboles. Con mi voz.\n\nEn el chat salen frases mías que nunca escribí: «ven aquí», "
        "«estoy aquí», «ayuda».\n\nEs alto. Demasiado alto. Brazos y piernas largas y agujeros donde deberían estar "
        "los ojos. Lo llamo el Imitador.\n\nCorre más que tú, pero no sabe trepar. Sube tres bloques y se queda abajo, "
        "mirando hacia arriba.",
        "I heard my name from the trees. In my voice.\n\nLines of mine I never wrote come up in the chat: \"come "
        "here\", \"i'm over here\", \"help\".\n\nIt's tall. Far too tall. Long arms and legs and holes where the eyes "
        "should be. I call it the Mimicer.\n\nIt runs faster than you, but it can't climb. Build up three blocks and "
        "it stays at the bottom, looking up.",
    )),
    6: (("La antena", "The mast"), (
        "La radio capta una emisora que no existe. Una voz plana lee números. Entre los números, otra cosa.\n\nMonté "
        "esta antena para oírla mejor. Error.\n\nAhora lo veo a él: rojo, quemado, de cinco bloques de alto, con dos "
        "puntos rojos por ojos, de pie en el campo. Le llaman el Hervido. El Doctor Nowhere.\n\nNO LO MIRES. Si lo "
        "miras demasiado todo se vuelve rojo, y duele.",
        "The radio picks up a station that doesn't exist. A flat voice reads numbers. Between the numbers, "
        "something else.\n\nI built this mast to hear it better. Mistake.\n\nNow I see him: red, burnt, five blocks "
        "tall, two red points for eyes, standing in the field. They call him the Boiled One. Doctor Nowhere.\n\n"
        "DON'T LOOK AT HIM. Look too long and everything goes red, and it hurts.",
    )),
    7: (("El campamento", "The camp"), (
        "Ya no duermo en la cabaña. Él entra en las casas.\n\nApaga las luces una a una y susurra muy cerca: «Ve a "
        "dormir». Cara blanca, ojos que no se cierran, la sonrisa cortada en las mejillas. A Jeff sí se le puede "
        "herir.\n\nEsta noche hay niebla, y hay páginas clavadas en los árboles. Ocho. Dicen que si las encuentras "
        "todas, te deja en paz.\n\nVoy a intentarlo.",
        "I don't sleep in the cabin any more. He comes into houses.\n\nHe puts the lights out one by one and "
        "whispers very close: \"Go to sleep.\" White face, eyes that don't close, the smile cut into the cheeks. "
        "Jeff, at least, can be hurt.\n\nTonight there is fog, and there are pages pinned to the trees. Eight. They "
        "say if you find them all, he leaves you alone.\n\nI'm going to try.",
    )),
    8: (("La última página", "The last page"), (
        "Si estás leyendo esto, has encontrado las ocho. Yo no. Llegué a siete.\n\nAhora estoy en el bosque, entre "
        "los árboles, con los demás. Mirando. Ya no tengo cara.\n\nTe dejará descansar unas noches. Luego vuelve la "
        "niebla. Siempre vuelve.\n\n— el que estuvo antes",
        "If you are reading this, you found all eight. I didn't. I got to seven.\n\nNow I'm in the forest, among "
        "the trees, with the others. Watching. I don't have a face any more.\n\nHe'll let you rest for a few "
        "nights. Then the fog comes back. It always comes back.\n\n— the one who was here before",
    )),
}

for name, (es, en) in NAMES.items():
    S[f"entity.hb:{name}.name"] = (es, en)
    S[f"item.spawn_egg.entity.hb:{name}.name"] = (f"Generar: {es}", f"Spawn {en}")

for n, ((title_es, title_en), (text_es, text_en)) in NOTES.items():
    S[f"item.hb:note_{n}.name"] = (f"§fNota {n}: {title_es}", f"§fNote {n}: {title_en}")
    S[f"hb.note.{n}.title"] = (f"{n}. {title_es}", f"{n}. {title_en}")
    S[f"hb.note.{n}.text"] = (text_es, text_en)

LANG = dict(S)
