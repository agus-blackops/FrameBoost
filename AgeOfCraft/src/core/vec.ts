export interface Vec3 {
  readonly x: number;
  readonly y: number;
  readonly z: number;
}

export function distanceSq(a: Vec3, b: Vec3): number {
  const dx = a.x - b.x;
  const dy = a.y - b.y;
  const dz = a.z - b.z;
  return dx * dx + dy * dy + dz * dz;
}

export function distance(a: Vec3, b: Vec3): number {
  return Math.sqrt(distanceSq(a, b));
}

export function horizontalDistance(a: Vec3, b: Vec3): number {
  const dx = a.x - b.x;
  const dz = a.z - b.z;
  return Math.sqrt(dx * dx + dz * dz);
}

export function formatVec(v: Vec3): string {
  return `${Math.floor(v.x)}, ${Math.floor(v.y)}, ${Math.floor(v.z)}`;
}

/** Centre of the block containing `v`, on its floor. */
export function blockCentre(v: Vec3): Vec3 {
  return { x: Math.floor(v.x) + 0.5, y: Math.floor(v.y), z: Math.floor(v.z) + 0.5 };
}
