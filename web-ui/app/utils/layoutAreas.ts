/**
 * Which areas each layout implies, and how many of each.
 *
 * The same table `RoomListDeriver` holds on the server, for the same reason `districts.ts` mirrors the
 * district seed: the form has to offer the choice *before* it can ask the API anything. The server stays
 * authoritative — it derives the list that gets priced — and `layoutAreas.spec.ts` fails the build if the
 * two drift apart.
 *
 * Why counts and not just types: "3+1" is four rooms to the person typing and seven areas to the engine,
 * and selecting "Yatak odası" in a 3+1 prices two of them. A checkbox that hides that is a checkbox
 * nobody can answer honestly.
 */

export type LayoutCode = 'STUDIO' | 'ONE_PLUS_ONE' | 'TWO_PLUS_ONE' | 'THREE_PLUS_ONE'
  | 'FOUR_PLUS_ONE' | 'FIVE_PLUS_ONE'

export type RoomTypeCode = 'LIVING_ROOM' | 'MASTER_BEDROOM' | 'BEDROOM' | 'STUDY' | 'KITCHEN'
  | 'BATHROOM' | 'HALLWAY' | 'BALCONY'

export interface LayoutArea {
  type: RoomTypeCode
  /** How many of this kind the layout has — two bedrooms in a 3+1, three in a 4+1. */
  count: number
}

const L = 'LIVING_ROOM' as const
const M = 'MASTER_BEDROOM' as const
const B = 'BEDROOM' as const
const K = 'KITCHEN' as const
const W = 'BATHROOM' as const
const H = 'HALLWAY' as const

/** In capture order, which is the order the server derives them in. */
export const LAYOUT_AREAS: Record<LayoutCode, readonly LayoutArea[]> = {
  // A studio's one room is the living space, and it has no hallway.
  STUDIO: [{ type: L, count: 1 }, { type: K, count: 1 }, { type: W, count: 1 }],
  ONE_PLUS_ONE: [
    { type: L, count: 1 }, { type: M, count: 1 },
    { type: K, count: 1 }, { type: W, count: 1 }, { type: H, count: 1 },
  ],
  TWO_PLUS_ONE: [
    { type: L, count: 1 }, { type: M, count: 1 }, { type: B, count: 1 },
    { type: K, count: 1 }, { type: W, count: 1 }, { type: H, count: 1 },
  ],
  THREE_PLUS_ONE: [
    { type: L, count: 1 }, { type: M, count: 1 }, { type: B, count: 2 },
    { type: K, count: 1 }, { type: W, count: 1 }, { type: H, count: 1 },
  ],
  FOUR_PLUS_ONE: [
    { type: L, count: 1 }, { type: M, count: 1 }, { type: B, count: 3 },
    { type: K, count: 1 }, { type: W, count: 1 }, { type: H, count: 1 },
  ],
  FIVE_PLUS_ONE: [
    { type: L, count: 1 }, { type: M, count: 1 }, { type: B, count: 4 },
    { type: K, count: 1 }, { type: W, count: 1 }, { type: H, count: 1 },
  ],
}

export function areasFor(layout: LayoutCode): readonly LayoutArea[] {
  return LAYOUT_AREAS[layout]
}

/** How many rooms a set of chosen types actually adds up to in this layout. */
export function areaCount(layout: LayoutCode, selected: readonly string[]): number {
  return areasFor(layout)
    .filter(area => selected.includes(area.type))
    .reduce((total, area) => total + area.count, 0)
}

/** Every area, one per room — what "the whole home" means for this layout. */
export function totalAreaCount(layout: LayoutCode): number {
  return areasFor(layout).reduce((total, area) => total + area.count, 0)
}
