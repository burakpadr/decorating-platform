import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { LAYOUT_AREAS, type LayoutCode } from './layoutAreas'

/**
 * The layout table exists twice: `RoomListDeriver` derives the list that gets priced, and
 * `layoutAreas.ts` offers the choice before any request can be made. `districts.spec.ts` is the pattern
 * for what to do about that — where a comment would ask two things to stay in step, a test asks.
 *
 * A drift here is not cosmetic: the form would offer a bedroom the engine never prices, or hide one it
 * does, and the operator would be comparing a figure against a job they did not describe.
 */
describe('layout areas', () => {
  const java = readFileSync(
    new URL('../../../api/src/main/java/com/burakpadr/decorating/quoting/domain/service/RoomListDeriver.java',
      import.meta.url),
    'utf8',
  )

  /** `areas.put(Layout.X, List.of(RoomType.A, RoomType.B, …));` → flat list of room types, in order. */
  const fromJava = new Map<string, string[]>(
    [...java.matchAll(/areas\.put\(Layout\.(\w+), List\.of\(([\s\S]*?)\)\);/g)]
      .map(match => [
        match[1]!,
        [...match[2]!.matchAll(/RoomType\.(\w+)/g)].map(room => room[1]!),
      ]),
  )

  it('finds every layout the deriver knows', () => {
    expect([...fromJava.keys()].sort()).toEqual(Object.keys(LAYOUT_AREAS).sort())
  })

  it.each(Object.keys(LAYOUT_AREAS) as LayoutCode[])(
    '%s matches the areas the server derives, in order and in number', layout => {
      const expanded = LAYOUT_AREAS[layout]
        .flatMap(area => Array.from({ length: area.count }, () => area.type))

      expect(expanded).toEqual(fromJava.get(layout))
    })

  it('is the 3+1 the workflow document describes: seven areas, three of them bedrooms', () => {
    const expanded = LAYOUT_AREAS.THREE_PLUS_ONE
      .flatMap(area => Array.from({ length: area.count }, () => area.type))

    expect(expanded).toHaveLength(7)
    expect(expanded.filter(type => type.endsWith('BEDROOM'))).toHaveLength(3)
  })
})
