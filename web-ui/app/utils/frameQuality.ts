/**
 * Whether a frame is worth keeping, decided on the phone (§9 step 4, workflow §2.5, BOYA-41).
 *
 * The whole value of doing this here is timing. §2.5: on a video walkthrough you find out hours later,
 * when the analysis comes back; on a photograph you find out now, while the customer is still standing
 * in the room and can simply take it again.
 *
 * Every threshold below is set low, and that is not timidity. §9 states the trade in one line —
 * "rejecting a good photo is far more costly than accepting a mediocre one — the user gets annoyed and
 * abandons" — so a frame is refused only when it is bad enough that the analysis would have to guess.
 * A mediocre frame is somebody's quote; a rejected good frame is often nobody's.
 */

/** Below this the image has no edges to speak of: out of focus, or a moving phone. */
const SHARP_ENOUGH = 12

/** Mean luma out of 255. A room lit by one lamp still clears this comfortably. */
const BRIGHT_ENOUGH = 18

/** Long edge. Under this the camera has given us less than the analysis can read a wall from. */
const BIG_ENOUGH = 480

/** §9: "After 3 rejections of the same frame, accept it and set low_quality_flag." */
const ARGUMENTS_BEFORE_GIVING_IN = 3

export type FrameMeasurements = {
  variance: number | null
  luminance: number | null
  width: number | null
  height: number | null
}

export type FrameFault = 'BLURRY' | 'DARK' | 'SMALL'

export type FrameVerdict = {
  accept: boolean
  reason: FrameFault | null
  lowQualityFlag: boolean
}

/**
 * @param attempt 1 for the first go at this frame, incrementing on each retake of the same one.
 */
export function assessFrame(frame: FrameMeasurements, attempt: number): FrameVerdict {
  const fault = faultIn(frame)
  if (fault === null) {
    return { accept: true, reason: null, lowQualityFlag: false }
  }
  if (attempt > ARGUMENTS_BEFORE_GIVING_IN) {
    // Three refusals is the point at which arguing costs more than the bad frame does. The backend is
    // told, so the operator sees why a finding from this photograph is thin (§2.5, §9).
    return { accept: true, reason: null, lowQualityFlag: true }
  }
  return { accept: false, reason: fault, lowQualityFlag: false }
}

/**
 * Darkness is named before blur, and the order was got wrong once.
 *
 * A dark frame has nothing in it to measure, so its variance collapses too and both faults fire at
 * once — which meant a photograph taken with the lights off was answered with "telefonu sabit tutun".
 * That is true and useless: the phone was steady, the room was dark, and the advice sends the customer
 * to do again exactly what they just did. Darkness is the cause, so darkness is what gets named.
 *
 * Found by photographing an unlit room in a browser, not by a test — every fixture until then was
 * either sharp and bright or flat and mid-grey.
 *
 * A measurement that is missing is not a fault: an old phone that cannot read its own canvas back has
 * still taken the photograph.
 */
function faultIn(frame: FrameMeasurements): FrameFault | null {
  if (frame.luminance !== null && frame.luminance < BRIGHT_ENOUGH) {
    return 'DARK'
  }
  if (frame.variance !== null && frame.variance < SHARP_ENOUGH) {
    return 'BLURRY'
  }
  const longest = Math.max(frame.width ?? 0, frame.height ?? 0)
  if (longest > 0 && longest < BIG_ENOUGH) {
    return 'SMALL'
  }
  return null
}

/** Rec. 601 luma. The green channel carries most of what an eye reads as brightness. */
export function toGrayscale(rgba: Uint8ClampedArray, width: number, height: number): Uint8ClampedArray {
  const gray = new Uint8ClampedArray(width * height)
  for (let i = 0; i < gray.length; i++) {
    gray[i] = 0.299 * rgba[i * 4]! + 0.587 * rgba[i * 4 + 1]! + 0.114 * rgba[i * 4 + 2]!
  }
  return gray
}

export function meanLuminance(gray: Uint8ClampedArray): number {
  if (gray.length === 0) {
    return 0
  }
  let total = 0
  for (let i = 0; i < gray.length; i++) {
    total += gray[i]!
  }
  return total / gray.length
}

/**
 * The variance of a Laplacian convolution — the standard sharpness measure, and the one §9 names.
 *
 * A focused image has edges, edges are where the second derivative is large, and a blurred one has
 * neither. Only interior pixels are convolved: the border has no neighbourhood, and inventing one
 * would put an edge at the frame's own boundary in every photograph.
 *
 * Unbounded above, which is why `Photo` clamps it to what `numeric(5,2)` holds rather than refusing
 * the sharpest frames anybody sends.
 */
export function laplacianVariance(gray: Uint8ClampedArray, width: number, height: number): number {
  if (width < 3 || height < 3) {
    return 0
  }

  let sum = 0
  let sumOfSquares = 0
  let count = 0
  for (let y = 1; y < height - 1; y++) {
    for (let x = 1; x < width - 1; x++) {
      const i = y * width + x
      const response = gray[i - width]! + gray[i - 1]! + gray[i + 1]! + gray[i + width]! - 4 * gray[i]!
      sum += response
      sumOfSquares += response * response
      count++
    }
  }

  if (count === 0) {
    return 0
  }
  const mean = sum / count
  return Math.max(0, sumOfSquares / count - mean * mean)
}
