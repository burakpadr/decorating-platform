/**
 * The list of areas to photograph, while the customer is still editing it (workflow §2.2).
 *
 * §2.1 derives the list on the server and §2.2 shows it before anything is confirmed — with the number
 * of photographs and the time it takes, because "ortada bırakılan çekim, baştan söylenmiş uzun listeden
 * kötüdür". Between those two the customer adds a second bathroom or drops the balcony, and the count
 * on screen has to keep telling the truth without a round trip per press.
 *
 * So the naming and counting rules live here too. The frames per kind of area do not: they belong to
 * the price book version that priced the range (§5.3) and arrive with the estimate, because a copy in
 * this file would be free to drift from the version behind the figure the customer agreed to.
 *
 * `areaList.spec.ts` is what keeps the numbering rule in step with `RoomListDeriver.label`, and
 * `roomLabels.spec.ts` does the same for the names themselves.
 */

/** §2.4: a 3+1 is 28 frames and about eight minutes, which is seventeen seconds a frame. */
const SECONDS_PER_FRAME = 17

/**
 * Nobody photographs a home in under three minutes, whatever the arithmetic says: the walk between
 * rooms, the lights, the tripod-less pause. A promise of one minute is broken before the second room.
 */
const SHORTEST_HONEST_MINUTES = 3

export interface LabelledArea<T extends string = string> {
  type: T
  /** As the customer reads it, and as the API will label the same list back (§2.2). */
  label: string
}

/**
 * Names each area, numbering a kind only where there is a second of it.
 *
 * The rule `RoomListDeriver.label` applies: "Yatak odası 1" beside a single bedroom reads as though
 * part of the list went missing, and a bathroom that stays "Banyo" while its new neighbour is "Banyo 2"
 * is a list nobody can photograph against.
 */
export function labelAreas<T extends string>(
  types: readonly T[],
  name: (type: T) => string,
): LabelledArea<T>[] {
  const total = new Map<string, number>()
  types.forEach(type => total.set(type, (total.get(type) ?? 0) + 1))

  const seen = new Map<string, number>()
  return types.map((type) => {
    const ordinal = (seen.get(type) ?? 0) + 1
    seen.set(type, ordinal)
    return {
      type,
      label: (total.get(type) ?? 0) > 1 ? `${name(type)} ${ordinal}` : name(type),
    }
  })
}

/**
 * Adds an area beside the ones of its kind, or at the end if the list has none.
 *
 * Capture order is reading order (§2.4 works down the list), so "Banyo 1" and "Banyo 2" three rows
 * apart would be two lists rather than one.
 */
export function addArea<T extends string>(types: readonly T[], type: T): T[] {
  const last = types.lastIndexOf(type)
  if (last < 0) {
    return [...types, type]
  }
  return [...types.slice(0, last + 1), type, ...types.slice(last + 1)]
}

/** Removes the one that was pressed. By position, because a kind of area can be on the list twice. */
export function removeAreaAt<T extends string>(types: readonly T[], index: number): T[] {
  return types.filter((_, at) => at !== index)
}

/** How many frames a kind of area needs in the version that priced this request. */
export function framesFor(type: string, frames: Readonly<Record<string, number>>): number {
  return frames[type] ?? 0
}

/**
 * The total the screen promises.
 *
 * A kind of area the priced version does not know counts as nothing rather than as a guess — the screen
 * only offers what the version answered, so this can only happen if the contract has drifted, and a
 * guessed frame count would hide that behind a plausible number.
 */
export function photoTotal(
  types: readonly string[],
  frames: Readonly<Record<string, number>>,
): number {
  return types.reduce((total, type) => total + framesFor(type, frames), 0)
}

/** How long §2.4 budgets for that many frames, in whole minutes. */
export function captureMinutes(frames: number): number {
  return Math.max(SHORTEST_HONEST_MINUTES, Math.round(frames * SECONDS_PER_FRAME / 60))
}
