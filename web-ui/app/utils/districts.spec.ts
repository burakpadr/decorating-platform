import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { DISTRICTS, findDistrictBySlug } from './districts'

/*
 * This file exists because DISTRICTS duplicates the service_district seed. The duplication is
 * deliberate — the 39 SEO pages are prerendered when the API is unreachable — but a comment asking
 * two lists to stay in step is a comment that gets ignored. A slug here with no matching row
 * prerenders a page whose form cannot submit, and nothing else would notice.
 *
 * The seed stopped being a migration in BOYA-72: the application is open source and the shipped
 * migrations carry the schema only, so a fresh install starts with no districts and enters its own
 * through setup. What is read here is the test fixture the backend suite prices against — still the
 * same 39 rows, and still the only other place they are written down. When setup owns the district
 * list (BOYA-71) this comparison moves with it; until then the fixture is the list to hold against.
 */
const SEED = fileURLToPath(
  new URL('../../../api/src/test/resources/db/fixture/V900__seed_price_book.sql', import.meta.url),
)

function seededDistricts(): { code: string; name: string }[] {
  const sql = readFileSync(SEED, 'utf8')
  const block = sql.slice(sql.indexOf('INSERT INTO service_district'))
  return [...block.matchAll(/\(\s*'([A-Z_]+)',\s*'([^']+)'\s*\)/g)]
    .map((match) => ({ code: match[1]!, name: match[2]! }))
}

describe('DISTRICTS', () => {
  it('covers exactly the districts seeded into service_district', () => {
    const seeded = seededDistricts()

    expect(seeded).toHaveLength(39)
    expect(DISTRICTS.map((d) => d.code).sort()).toEqual(seeded.map((d) => d.code).sort())
  })

  it('uses the same Turkish display names as the seed', () => {
    const seededNames = new Map(seededDistricts().map((d) => [d.code, d.name]))

    for (const district of DISTRICTS) {
      expect(district.name, `display name for ${district.code}`).toBe(seededNames.get(district.code))
    }
  })

  it('has URL-safe slugs — the SEO routes are the whole point', () => {
    for (const district of DISTRICTS) {
      expect(district.slug, `slug for ${district.code}`).toMatch(/^[a-z0-9-]+$/)
    }
  })

  it('has no duplicate code or slug', () => {
    expect(new Set(DISTRICTS.map((d) => d.code)).size).toBe(DISTRICTS.length)
    expect(new Set(DISTRICTS.map((d) => d.slug)).size).toBe(DISTRICTS.length)
  })

  it('resolves a slug, and returns undefined rather than guessing', () => {
    expect(findDistrictBySlug('kadikoy')?.code).toBe('KADIKOY')
    expect(findDistrictBySlug('atlantis')).toBeUndefined()
  })
})
