import type { components } from '@decorating/api-client'

type PhotoRole = components['schemas']['UploadIntentRequest']['role']

/**
 * How large a frame is kept before it is re-encoded (§9 step 2, BOYA-41).
 *
 * Two edges, not one: 2048 px for the frames that describe a surface, 2560 px for a DETAIL close-up.
 * §2.6 is the reason for the difference — those are the shots taken because something is cracked or
 * stained, "muhtemelen en değerli kareler", and a hairline crack survives the resize or it does not
 * survive the analysis either.
 *
 * Downscaling only. A phone that hands us a small photograph has given us everything it has, and
 * stretching it would cost bytes on the customer's connection to add no detail at all.
 */
const LONG_EDGE: Record<PhotoRole, number> = {
  WALL_1: 2048,
  WALL_2: 2048,
  WALL_3: 2048,
  WALL_4: 2048,
  CEILING: 2048,
  DETAIL: 2560,
}

export type FrameSize = { width: number, height: number }

export function targetSize(width: number, height: number, role: PhotoRole): FrameSize {
  const edge = LONG_EDGE[role] ?? 2048
  const longest = Math.max(width, height)
  if (longest <= edge) {
    return { width, height }
  }

  const scale = edge / longest
  // The long edge is set exactly rather than scaled and rounded, so 4032×3024 lands on 2048 and not on
  // 2047 — a canvas one pixel short of the target is the kind of thing that shows up as a failing
  // assertion months later and means nothing.
  return width >= height
    ? { width: edge, height: atLeastOnePixel(height * scale) }
    : { width: atLeastOnePixel(width * scale), height: edge }
}

/** A canvas of zero pixels throws, and a very wide frame can round its short edge to nothing. */
function atLeastOnePixel(value: number): number {
  return Math.max(1, Math.round(value))
}
