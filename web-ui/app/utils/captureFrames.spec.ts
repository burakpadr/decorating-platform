import { describe, expect, it } from 'vitest'

import { frameHintKey, frameLabelKey, nextOutstanding } from './captureFrames'

/**
 * What to ask for next, and what to call it (§2.4, BOYA-42).
 *
 * §2.4's table asks different things of different rooms, and the difference is not cosmetic: a kitchen
 * wants "2 karşıt köşe" because most of its walls are tile and cupboard, and a bathroom wants "1 genel"
 * for the same reason. Calling both of those "1. duvar" would be asking for a photograph of a kitchen
 * unit and then pricing the wall behind it.
 */

const area = (type: string, frames: { role: string, taken: boolean }[]) => ({
  id: type, type, label: type, sortOrder: 0, frames, complete: frames.every(f => f.taken),
})

describe('frameLabelKey', () => {
  it('numbers the walls of a room that has four of them', () => {
    expect(frameLabelKey('LIVING_ROOM', 'WALL_2')).toBe('photoRoles.WALL_2')
  })

  it('asks a kitchen for opposite corners, which is what §2.4 asks of it', () => {
    expect(frameLabelKey('KITCHEN', 'WALL_1')).toBe('capture.frames.corner1')
    expect(frameLabelKey('KITCHEN', 'WALL_2')).toBe('capture.frames.corner2')
  })

  it('asks a hallway for the same, for the same reason', () => {
    expect(frameLabelKey('HALLWAY', 'WALL_2')).toBe('capture.frames.corner2')
  })

  it('asks a bathroom for one general view rather than a numbered wall', () => {
    expect(frameLabelKey('BATHROOM', 'WALL_1')).toBe('capture.frames.general')
  })

  it('calls a ceiling a ceiling in every room', () => {
    for (const type of ['LIVING_ROOM', 'KITCHEN', 'BATHROOM', 'HALLWAY']) {
      expect(frameLabelKey(type, 'CEILING')).toBe('photoRoles.CEILING')
    }
  })
})

describe('frameHintKey', () => {
  it('tells a wall and a ceiling apart, because the advice differs', () => {
    expect(frameHintKey('LIVING_ROOM', 'WALL_1')).toBe('capture.hints.wall')
    expect(frameHintKey('LIVING_ROOM', 'CEILING')).toBe('capture.hints.ceiling')
  })

  it('has its own advice for the rooms §2.4 treats differently', () => {
    expect(frameHintKey('KITCHEN', 'WALL_1')).toBe('capture.hints.corner')
    expect(frameHintKey('BATHROOM', 'WALL_1')).toBe('capture.hints.general')
  })
})

describe('nextOutstanding', () => {
  it('is the first frame nobody has taken, in the order the areas were agreed', () => {
    const state = {
      areas: [
        area('LIVING_ROOM', [{ role: 'WALL_1', taken: true }, { role: 'WALL_2', taken: false }]),
        area('BATHROOM', [{ role: 'WALL_1', taken: false }]),
      ],
    }

    expect(nextOutstanding(state)).toEqual({ areaIndex: 0, frameIndex: 1 })
  })

  it('moves on to the next area once one is finished', () => {
    const state = {
      areas: [
        area('LIVING_ROOM', [{ role: 'WALL_1', taken: true }]),
        area('BATHROOM', [{ role: 'WALL_1', taken: false }]),
      ],
    }

    expect(nextOutstanding(state)).toEqual({ areaIndex: 1, frameIndex: 0 })
  })

  it('does not skip a gap left by a retake in an earlier area', () => {
    // Deleting a frame to retake it reopens it, and §2.4 walks the list rather than a cursor — a
    // capture that marched forward only would leave that hole and then refuse to submit.
    const state = {
      areas: [
        area('LIVING_ROOM', [{ role: 'WALL_1', taken: false }, { role: 'WALL_2', taken: true }]),
        area('BATHROOM', [{ role: 'WALL_1', taken: true }]),
      ],
    }

    expect(nextOutstanding(state)).toEqual({ areaIndex: 0, frameIndex: 0 })
  })

  it('is null when every frame is in', () => {
    const state = { areas: [area('BATHROOM', [{ role: 'WALL_1', taken: true }])] }

    expect(nextOutstanding(state)).toBeNull()
  })

  it('is null when there is nothing to photograph at all', () => {
    expect(nextOutstanding({ areas: [] })).toBeNull()
  })
})
