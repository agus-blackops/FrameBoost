// NintendoCraft: ¿qué pasaría si Nintendo comprara Minecraft?
// Parodia no oficial. No afiliada con Nintendo ni con Mojang.
import { system } from "@minecraft/server";
import { iniciarBloques, registrarComponentesDeBloques } from "./bloques.js";
import { iniciarDemandas } from "./demandas.js";
import { iniciarPoderes } from "./poderes.js";
import { iniciarTienda } from "./tienda.js";
import { iniciarVidas } from "./vidas.js";

system.beforeEvents.startup.subscribe((ev) => {
  registrarComponentesDeBloques(ev);
});

// Suscribirse a eventos e iniciar temporizadores está permitido desde el
// arranque; el acceso al mundo ocurre después, dentro de cada callback.
iniciarVidas();
iniciarPoderes();
iniciarBloques();
iniciarDemandas();
iniciarTienda();
