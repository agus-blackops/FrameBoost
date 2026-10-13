// Catálogo de componentes con precios REFERENCIALES en CLP (pesos chilenos).
// Los precios cambian seguido: edita los números de este archivo para
// ajustarlos a lo que veas en tiendas (PC Factory, SP Digital, Solotodo, etc.).
//
// Puntajes (no son benchmarks oficiales, son una escala relativa propia):
//   g = rendimiento en juegos (CPU), m = rendimiento multihilo (CPU)
//   s = rendimiento gráfico (GPU), w = consumo en watts
//   tier = calidad de placa/gabinete (1 básico, 2 medio, 3 alto)
(function (root) {
  const CATALOG = {
    updated: "2026-10 (aprox.)",

    cpus: [
      // AM4
      { id: "r5-5500",   name: "AMD Ryzen 5 5500",        brand: "AMD",   socket: "AM4", g: 58,  m: 52,  w: 65,  igpu: false, cooler: 0,     price: 65000,  minTier: 1 },
      { id: "r5-5600",   name: "AMD Ryzen 5 5600",        brand: "AMD",   socket: "AM4", g: 63,  m: 58,  w: 65,  igpu: false, cooler: 0,     price: 80000,  minTier: 1 },
      { id: "r5-5600g",  name: "AMD Ryzen 5 5600G",       brand: "AMD",   socket: "AM4", g: 55,  m: 56,  w: 65,  igpu: true,  cooler: 0,     price: 95000,  minTier: 1 },
      { id: "r7-5700x3d",name: "AMD Ryzen 7 5700X3D",     brand: "AMD",   socket: "AM4", g: 83,  m: 62,  w: 105, igpu: false, cooler: 22000, price: 190000, minTier: 2 },
      // AM5
      { id: "r5-7500f",  name: "AMD Ryzen 5 7500F",       brand: "AMD",   socket: "AM5", g: 76,  m: 68,  w: 65,  igpu: false, cooler: 0,     price: 125000, minTier: 1 },
      { id: "r5-8600g",  name: "AMD Ryzen 5 8600G",       brand: "AMD",   socket: "AM5", g: 70,  m: 66,  w: 65,  igpu: true,  cooler: 0,     price: 170000, minTier: 1 },
      { id: "r5-9600x",  name: "AMD Ryzen 5 9600X",       brand: "AMD",   socket: "AM5", g: 84,  m: 72,  w: 65,  igpu: true,  cooler: 0,     price: 190000, minTier: 1 },
      { id: "r7-7800x3d",name: "AMD Ryzen 7 7800X3D",     brand: "AMD",   socket: "AM5", g: 100, m: 76,  w: 120, igpu: true,  cooler: 25000, price: 330000, minTier: 2 },
      { id: "r7-9700x",  name: "AMD Ryzen 7 9700X",       brand: "AMD",   socket: "AM5", g: 88,  m: 88,  w: 65,  igpu: true,  cooler: 20000, price: 265000, minTier: 2 },
      { id: "r7-9800x3d",name: "AMD Ryzen 7 9800X3D",     brand: "AMD",   socket: "AM5", g: 112, m: 90,  w: 120, igpu: true,  cooler: 30000, price: 470000, minTier: 2 },
      { id: "r9-9900x",  name: "AMD Ryzen 9 9900X",       brand: "AMD",   socket: "AM5", g: 90,  m: 115, w: 120, igpu: true,  cooler: 30000, price: 380000, minTier: 2 },
      { id: "r9-9950x",  name: "AMD Ryzen 9 9950X",       brand: "AMD",   socket: "AM5", g: 92,  m: 140, w: 170, igpu: true,  cooler: 45000, price: 560000, minTier: 2 },
      // Intel LGA1700
      { id: "i3-12100f", name: "Intel Core i3-12100F",    brand: "Intel", socket: "LGA1700", g: 52, m: 42,  w: 60,  igpu: false, cooler: 0,     price: 65000,  minTier: 1 },
      { id: "i3-12100",  name: "Intel Core i3-12100",     brand: "Intel", socket: "LGA1700", g: 52, m: 42,  w: 60,  igpu: true,  cooler: 0,     price: 90000,  minTier: 1 },
      { id: "i5-12400f", name: "Intel Core i5-12400F",    brand: "Intel", socket: "LGA1700", g: 66, m: 58,  w: 65,  igpu: false, cooler: 0,     price: 100000, minTier: 1 },
      { id: "i5-12400",  name: "Intel Core i5-12400",     brand: "Intel", socket: "LGA1700", g: 66, m: 58,  w: 65,  igpu: true,  cooler: 0,     price: 130000, minTier: 1 },
      { id: "i5-13400f", name: "Intel Core i5-13400F",    brand: "Intel", socket: "LGA1700", g: 72, m: 70,  w: 65,  igpu: false, cooler: 0,     price: 150000, minTier: 1 },
      { id: "i5-14600kf",name: "Intel Core i5-14600KF",   brand: "Intel", socket: "LGA1700", g: 86, m: 100, w: 125, igpu: false, cooler: 25000, price: 235000, minTier: 2 },
      { id: "i7-14700kf",name: "Intel Core i7-14700KF",   brand: "Intel", socket: "LGA1700", g: 95, m: 125, w: 125, igpu: false, cooler: 35000, price: 330000, minTier: 2 },
    ],

    mobos: [
      { id: "a520",    name: "Placa A520 (AM4, DDR4)",        socket: "AM4",     ram: "DDR4", tier: 1, price: 55000 },
      { id: "b550",    name: "Placa B550 (AM4, DDR4)",        socket: "AM4",     ram: "DDR4", tier: 2, price: 85000 },
      { id: "x570",    name: "Placa X570 (AM4, DDR4)",        socket: "AM4",     ram: "DDR4", tier: 3, price: 130000 },
      { id: "a620",    name: "Placa A620 (AM5, DDR5)",        socket: "AM5",     ram: "DDR5", tier: 1, price: 85000 },
      { id: "b650",    name: "Placa B650 (AM5, DDR5)",        socket: "AM5",     ram: "DDR5", tier: 2, price: 125000 },
      { id: "x870",    name: "Placa X870 (AM5, DDR5)",        socket: "AM5",     ram: "DDR5", tier: 3, price: 240000 },
      { id: "h610",    name: "Placa H610 (LGA1700, DDR4)",    socket: "LGA1700", ram: "DDR4", tier: 1, price: 60000 },
      { id: "b660",    name: "Placa B660 (LGA1700, DDR4)",    socket: "LGA1700", ram: "DDR4", tier: 2, price: 90000 },
      { id: "b760d5",  name: "Placa B760 (LGA1700, DDR5)",    socket: "LGA1700", ram: "DDR5", tier: 2, price: 135000 },
      { id: "z790",    name: "Placa Z790 (LGA1700, DDR5)",    socket: "LGA1700", ram: "DDR5", tier: 3, price: 230000 },
    ],

    rams: [
      { id: "d4-8",  name: "RAM 8 GB DDR4",  type: "DDR4", gb: 8,  price: 18000 },
      { id: "d4-16", name: "RAM 16 GB DDR4", type: "DDR4", gb: 16, price: 32000 },
      { id: "d4-32", name: "RAM 32 GB DDR4", type: "DDR4", gb: 32, price: 62000 },
      { id: "d5-16", name: "RAM 16 GB DDR5", type: "DDR5", gb: 16, price: 55000 },
      { id: "d5-32", name: "RAM 32 GB DDR5", type: "DDR5", gb: 32, price: 105000 },
      { id: "d5-64", name: "RAM 64 GB DDR5", type: "DDR5", gb: 64, price: 215000 },
    ],

    ssds: [
      { id: "s500",  name: "SSD NVMe 500 GB", gb: 500,  price: 35000 },
      { id: "s1000", name: "SSD NVMe 1 TB",   gb: 1000, price: 58000 },
      { id: "s2000", name: "SSD NVMe 2 TB",   gb: 2000, price: 110000 },
      { id: "s4000", name: "SSD NVMe 4 TB",   gb: 4000, price: 240000 },
    ],

    gpus: [
      { id: "rx6500xt",  name: "Radeon RX 6500 XT 4 GB",   brand: "AMD",    s: 20,  w: 107, price: 95000 },
      { id: "rtx3050",   name: "GeForce RTX 3050 6 GB",    brand: "NVIDIA", s: 24,  w: 70,  price: 130000 },
      { id: "rx7600",    name: "Radeon RX 7600 8 GB",      brand: "AMD",    s: 38,  w: 165, price: 210000 },
      { id: "rtx5050",   name: "GeForce RTX 5050 8 GB",    brand: "NVIDIA", s: 40,  w: 130, price: 215000 },
      { id: "rtx5060",   name: "GeForce RTX 5060 8 GB",    brand: "NVIDIA", s: 48,  w: 145, price: 300000 },
      { id: "rx9060xt",  name: "Radeon RX 9060 XT 16 GB",  brand: "AMD",    s: 56,  w: 160, price: 340000 },
      { id: "rtx5060ti", name: "GeForce RTX 5060 Ti 16 GB",brand: "NVIDIA", s: 60,  w: 180, price: 450000 },
      { id: "rx9070",    name: "Radeon RX 9070 16 GB",     brand: "AMD",    s: 78,  w: 220, price: 620000 },
      { id: "rtx5070",   name: "GeForce RTX 5070 12 GB",   brand: "NVIDIA", s: 77,  w: 250, price: 680000 },
      { id: "rx9070xt",  name: "Radeon RX 9070 XT 16 GB",  brand: "AMD",    s: 86,  w: 304, price: 730000 },
      { id: "rtx5070ti", name: "GeForce RTX 5070 Ti 16 GB",brand: "NVIDIA", s: 96,  w: 300, price: 950000 },
      { id: "rtx5080",   name: "GeForce RTX 5080 16 GB",   brand: "NVIDIA", s: 125, w: 360, price: 1450000 },
      { id: "rtx5090",   name: "GeForce RTX 5090 32 GB",   brand: "NVIDIA", s: 180, w: 575, price: 3600000 },
    ],

    psus: [
      { id: "p450",  name: "Fuente 450 W 80+ Bronze",  w: 450,  price: 28000 },
      { id: "p550",  name: "Fuente 550 W 80+ Bronze",  w: 550,  price: 38000 },
      { id: "p650",  name: "Fuente 650 W 80+ Bronze",  w: 650,  price: 52000 },
      { id: "p750",  name: "Fuente 750 W 80+ Gold",    w: 750,  price: 70000 },
      { id: "p850",  name: "Fuente 850 W 80+ Gold",    w: 850,  price: 95000 },
      { id: "p1000", name: "Fuente 1000 W 80+ Gold",   w: 1000, price: 135000 },
      { id: "p1200", name: "Fuente 1200 W 80+ Platinum", w: 1200, price: 190000 },
    ],

    cases: [
      { id: "c1", name: "Gabinete básico ATX",                tier: 1, price: 30000 },
      { id: "c2", name: "Gabinete mid-tower con buen flujo de aire", tier: 2, price: 55000 },
      { id: "c3", name: "Gabinete premium vidrio templado",   tier: 3, price: 95000 },
    ],
  };

  if (typeof module !== "undefined" && module.exports) module.exports = CATALOG;
  else root.CATALOG = CATALOG;
})(typeof window !== "undefined" ? window : globalThis);
