import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { addArea, captureMinutes, labelAreas, photoTotal, removeAreaAt } from './areaList'

/**
 * The list the customer edits at workflow §2.2.
 *
 * The screen has to name and count areas *before* the server has seen the edited list, because §2.2's
 * whole point is setting the expectation up front: "ortada bırakılan çekim, baştan söylenmiş uzun
 * listeden kötüdür". So the numbering rule exists on both sides, and the last test here is what asks
 * them to stay in step — `roomLabels.spec.ts` does the same for the names themselves.
 */
const NAMES: Record<string, string> = {
  LIVING_ROOM: 'Salon',
  MASTER_BEDROOM: 'Ebeveyn yatak odası',
  BEDROOM: 'Yatak odası',
  STUDY: 'Çalışma odası',
  KITCHEN: 'Mutfak',
  BATHROOM: 'Banyo',
  HALLWAY: 'Koridor',
  BALCONY: 'Balkon',
}

const name = (type: string) => NAMES[type] ?? type

/** A 3+1, whole home: what §2.1 derives and §2.2 shows. */
const THREE_PLUS_ONE = [
  'LIVING_ROOM', 'MASTER_BEDROOM', 'BEDROOM', 'BEDROOM', 'KITCHEN', 'BATHROOM', 'HALLWAY',
] as const

/** The seeded frames per kind of area, as the estimate answers them (V2, §5.3). */
const FRAMES = {
  LIVING_ROOM: 5,
  MASTER_BEDROOM: 5,
  BEDROOM: 5,
  STUDY: 5,
  KITCHEN: 3,
  BATHROOM: 2,
  HALLWAY: 3,
  BALCONY: 2,
}

describe('labelling the areas', () => {
  it('numbers a kind of area only when there are two of it', () => {
    const labels = labelAreas(THREE_PLUS_ONE, name).map(area => area.label)

    // "Yatak odası 1" beside a single bedroom reads as though part of the list went missing.
    expect(labels).toEqual([
      'Salon', 'Ebeveyn yatak odası', 'Yatak odası 1', 'Yatak odası 2',
      'Mutfak', 'Banyo', 'Koridor',
    ])
  })

  it('renumbers the ones already on the list when a second arrives', () => {
    const labels = labelAreas(addArea(THREE_PLUS_ONE, 'BATHROOM'), name).map(area => area.label)

    // The bathroom was "Banyo" a moment ago. Leaving it unnumbered while its neighbour is "Banyo 2"
    // is a list the customer cannot photograph against.
    expect(labels).toContain('Banyo 1')
    expect(labels).toContain('Banyo 2')
    expect(labels).not.toContain('Banyo')
  })
})

describe('editing the list', () => {
  it('puts a second bathroom beside the first, not at the end', () => {
    const added = addArea(THREE_PLUS_ONE, 'BATHROOM')

    // Capture order is reading order: "Banyo 1" and "Banyo 2" three rows apart is two lists.
    expect(added).toEqual([
      'LIVING_ROOM', 'MASTER_BEDROOM', 'BEDROOM', 'BEDROOM', 'KITCHEN',
      'BATHROOM', 'BATHROOM', 'HALLWAY',
    ])
  })

  it('appends a kind of area the list does not have yet', () => {
    expect(addArea(THREE_PLUS_ONE, 'BALCONY').at(-1)).toBe('BALCONY')
  })

  it('removes exactly the one that was pressed, not every one of its kind', () => {
    const withoutSecondBedroom = removeAreaAt(THREE_PLUS_ONE, 3)

    expect(withoutSecondBedroom).toEqual([
      'LIVING_ROOM', 'MASTER_BEDROOM', 'BEDROOM', 'KITCHEN', 'BATHROOM', 'HALLWAY',
    ])
  })

  it('leaves the list alone when the index is not on it', () => {
    expect(removeAreaAt(THREE_PLUS_ONE, 9)).toEqual([...THREE_PLUS_ONE])
  })
})

describe('what the screen promises', () => {
  it('counts the frames the priced version asks for, area by area', () => {
    // §2.4's own arithmetic: 5 + 5 + 5 + 5 + 3 + 2 + 3.
    expect(photoTotal(THREE_PLUS_ONE, FRAMES)).toBe(28)
  })

  it('counts an added area at its own rate, not at the average', () => {
    // A bathroom is two frames because most of its wall is tile and cupboard (§2.4), and a study is
    // five. A screen that added four either way would be wrong twice.
    expect(photoTotal(addArea(THREE_PLUS_ONE, 'BATHROOM'), FRAMES)).toBe(30)
    expect(photoTotal(addArea(THREE_PLUS_ONE, 'STUDY'), FRAMES)).toBe(33)
  })

  it('counts nothing for a kind of area the priced version does not know', () => {
    // Contract drift rather than a customer choice — the screen offers only what the map contains.
    expect(photoTotal(['LIVING_ROOM', 'WINE_CELLAR'], FRAMES)).toBe(5)
  })

  it('turns the frame count into the minutes §2.4 budgets for it', () => {
    // §2.4: "3+1 ev için ~8 dk, 28 fotoğraf". Everything else is that same rate.
    expect(captureMinutes(28)).toBe(8)
    expect(captureMinutes(33)).toBe(9)
  })

  it('never promises less than three minutes, however short the list', () => {
    // A studio is ten frames. "3 dakika" is honest; "1 dakika" is a promise the walk between rooms
    // breaks on its own.
    expect(captureMinutes(10)).toBe(3)
    expect(captureMinutes(2)).toBe(3)
  })
})

describe('the numbering rule the API also applies', () => {
  const java = readFileSync(
    new URL('../../../api/src/main/java/com/burakpadr/decorating/quoting/domain/service/RoomListDeriver.java',
      import.meta.url),
    'utf8',
  )

  it('still numbers only where there is something to tell apart', () => {
    // The server labels the list it is sent (§2.2 lets the customer change it), and the customer has
    // already read these labels on this screen. Two rules would be two answers to "what is this room
    // called" — and that name is read again on the capture screen and in the quote.
    expect(java).toContain('total.get(type) > 1')
    expect(java).toContain('RoomLabels.of(type) + " " + ordinal')
  })
})
