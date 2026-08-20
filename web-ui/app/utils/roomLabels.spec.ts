import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

/**
 * The room labels exist twice: `RoomLabels.java` writes them into `room.label` when the list is
 * derived (§2.1), and this locale file renders the ones the frontend names itself — the add-an-area
 * buttons at §2.2, for instance.
 *
 * Both are deliberate, and `districts.spec.ts` is the pattern for what to do about it: where a comment
 * would ask two things to stay in step, a test does the asking. A customer seeing "Salon" beside their
 * photographs and "Oturma odası" on the button is a customer wondering which one the quote is for.
 */
describe('room labels', () => {
  // Read, not imported: the i18n module precompiles locale JSON into message functions at build time,
  // so an import would hand this test an AST instead of the strings the operator reads.
  const tr = JSON.parse(readFileSync(
    new URL('../../i18n/locales/tr.json', import.meta.url), 'utf8',
  )) as { rooms: Record<string, string> }

  const java = readFileSync(
    new URL('../../../api/src/main/java/com/burakpadr/decorating/quoting/domain/service/RoomLabels.java',
      import.meta.url),
    'utf8',
  )

  const fromJava = new Map(
    [...java.matchAll(/case (\w+) -> "([^"]+)";/g)].map(([, type, label]) => [type, label]),
  )

  it('parses every room type out of the deriver', () => {
    expect(fromJava.size).toBe(8)
  })

  it.each([...Object.entries(tr.rooms)])('%s matches the label the API persists', (type, label) => {
    expect(fromJava.get(type)).toBe(label)
  })
})
