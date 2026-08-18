import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { DISTRICTS, findDistrictBySlug } from './districts'

/*
 * This file exists because DISTRICTS duplicates the service_district seed. The duplication is
 * deliberate — the 39 SEO pages are prerendered when the API is unreachable — but a comment asking
 * two lists to stay in step is a comment that gets ignored. A slug here with no matching row
 * prerenders a page whose form cannot submit, and nothing else would notice.
 */
const MIGRATION = fileURLToPath(
  new URL('../../../api/src/main/resources/db/migration/V2__seed_price_book.sql', import.meta.url),
)

function seededDistricts(): { code: string; name: string }[] {
  const sql = readFileSync(MIGRATION, 'utf8')
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
