import { ActionFormData } from "@minecraft/server-ui";
import { ID, contarItem, darItem, quitarItem } from "./util.js";

const PRODUCTOS = [
  { item: ID.superChampinon, nombre: "Súper Champiñón", precio: 10, icono: "textures/items/nc_super_champinon" },
  { item: ID.florFuego, nombre: "Flor de Fuego", precio: 25, icono: "textures/items/nc_flor_fuego" },
  { item: ID.estrella, nombre: "Estrella de Invencibilidad", precio: 40, icono: "textures/items/nc_estrella" },
  { item: ID.champinonVida, nombre: "Champiñón 1-UP", precio: 50, icono: "textures/items/nc_champinon_vida" },
  { item: ID.bloquePregunta, nombre: "Bloque ?", precio: 15, icono: "textures/blocks/nc_bloque_pregunta" },
  { item: ID.tuberia, nombre: "Tubería", precio: 8, icono: "textures/blocks/nc_tuberia_lado" },
];

/** @param {import("@minecraft/server").Player} jugador */
export function abrirTienda(jugador) {
  const monedas = contarItem(jugador, ID.moneda);
  const formulario = new ActionFormData()
    .title("§l§cTienda Oficial")
    .body(`Tienes §6${monedas} monedas§r.\n§7Precios sin descuento. Como siempre.`);
  for (const p of PRODUCTOS) formulario.button(`${p.nombre}\n§6${p.precio} monedas`, p.icono);
  formulario.button("Salir");

  formulario.show(jugador).then((respuesta) => {
    if (respuesta.canceled || respuesta.selection === undefined) return;
    const producto = PRODUCTOS[respuesta.selection];
    if (!producto) return;
    if (!quitarItem(jugador, ID.moneda, producto.precio)) {
      jugador.sendMessage(`§cNo te alcanza: cuesta §6${producto.precio} monedas§c. Nintendo no hace rebajas.`);
      jugador.playSound("note.bass");
      return;
    }
    darItem(jugador, producto.item, 1);
    jugador.sendMessage(`§a¡Compraste ${producto.nombre}! §7Gracias por tu compra.`);
    jugador.playSound("random.orb", { pitch: 1.2 });
  });
}
