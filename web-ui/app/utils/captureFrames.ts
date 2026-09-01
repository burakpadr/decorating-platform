/**
 * Naming and ordering the frames of a capture (§2.4, BOYA-42).
 *
 * §2.4's table asks different things of different rooms and says why: a kitchen and a hallway want two
 * opposite corners, a bathroom one general view, because in those rooms most of the wall is tile and
 * cupboard and there is little paintable surface to photograph. So the same {@code WALL_1} means
 * "the first wall" in a living room and "one of two corners" in a kitchen, and the screen has to say
 * which — asking for "1. duvar" in a kitchen gets a photograph of a cupboard.
 */

const CORNER_ROOMS = new Set(['KITCHEN', 'HALLWAY'])
const SINGLE_VIEW_ROOMS = new Set(['BATHROOM'])

export type CaptureFrameLike = { role: string, taken: boolean }
export type CaptureAreaLike = { frames: CaptureFrameLike[] }
export type CaptureStateLike = { areas: CaptureAreaLike[] }

export function frameLabelKey(type: string, role: string): string {
  if (role === 'CEILING' || role === 'DETAIL') {
    return `photoRoles.${role}`
  }
  if (SINGLE_VIEW_ROOMS.has(type)) {
    return 'capture.frames.general'
  }
  if (CORNER_ROOMS.has(type)) {
    return `capture.frames.corner${role.replace('WALL_', '')}`
  }
  return `photoRoles.${role}`
}

export function frameHintKey(type: string, role: string): string {
  if (role === 'CEILING') {
    return 'capture.hints.ceiling'
  }
  if (role === 'DETAIL') {
    return 'capture.hints.detail'
  }
  if (SINGLE_VIEW_ROOMS.has(type)) {
    return 'capture.hints.general'
  }
  if (CORNER_ROOMS.has(type)) {
    return 'capture.hints.corner'
  }
  return 'capture.hints.wall'
}

export type FramePosition = { areaIndex: number, frameIndex: number }

/**
 * The first frame that has not arrived, walking the areas in the order they were agreed.
 *
 * <p>A search rather than a cursor, deliberately. Retaking a frame deletes it and reopens it, and that
 * can happen behind where a cursor had got to — a capture that only ever moved forward would leave the
 * hole, tell the customer they were finished, and then be refused by §3's submit guard for a reason
 * nothing on the screen had mentioned.
 */
export function nextOutstanding(state: CaptureStateLike): FramePosition | null {
  for (let areaIndex = 0; areaIndex < state.areas.length; areaIndex++) {
    const frames = state.areas[areaIndex]!.frames
    for (let frameIndex = 0; frameIndex < frames.length; frameIndex++) {
      if (!frames[frameIndex]!.taken) {
        return { areaIndex, frameIndex }
      }
    }
  }
  return null
}
