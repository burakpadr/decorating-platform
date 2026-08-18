/**
 * Districts, for routing and SEO copy only.
 *
 * The authoritative list lives in `service_district` — that is where `active` and
 * `district_factor` come from, and the quote form must read it from `GET /api/districts`. This
 * array exists because the 39 SEO pages are prerendered at build time, when the API is not
 * reachable. Keep the slugs in step with the migration; a slug here that has no matching row will
 * prerender a page whose form cannot submit.
 */
export interface District {
  /** Matches `service_district.district_code`. */
  code: string
  /** Matches `service_district.display_name`. */
  name: string
  /** URL segment: `/{slug}-boya-badana-fiyatlari`. */
  slug: string
}

export const DISTRICTS: readonly District[] = [
  { code: 'ADALAR', name: 'Adalar', slug: 'adalar' },
  { code: 'ARNAVUTKOY', name: 'Arnavutköy', slug: 'arnavutkoy' },
  { code: 'ATASEHIR', name: 'Ataşehir', slug: 'atasehir' },
  { code: 'AVCILAR', name: 'Avcılar', slug: 'avcilar' },
  { code: 'BAGCILAR', name: 'Bağcılar', slug: 'bagcilar' },
  { code: 'BAHCELIEVLER', name: 'Bahçelievler', slug: 'bahcelievler' },
  { code: 'BAKIRKOY', name: 'Bakırköy', slug: 'bakirkoy' },
  { code: 'BASAKSEHIR', name: 'Başakşehir', slug: 'basaksehir' },
  { code: 'BAYRAMPASA', name: 'Bayrampaşa', slug: 'bayrampasa' },
  { code: 'BESIKTAS', name: 'Beşiktaş', slug: 'besiktas' },
  { code: 'BEYKOZ', name: 'Beykoz', slug: 'beykoz' },
  { code: 'BEYLIKDUZU', name: 'Beylikdüzü', slug: 'beylikduzu' },
  { code: 'BEYOGLU', name: 'Beyoğlu', slug: 'beyoglu' },
  { code: 'BUYUKCEKMECE', name: 'Büyükçekmece', slug: 'buyukcekmece' },
  { code: 'CATALCA', name: 'Çatalca', slug: 'catalca' },
  { code: 'CEKMEKOY', name: 'Çekmeköy', slug: 'cekmekoy' },
  { code: 'ESENLER', name: 'Esenler', slug: 'esenler' },
  { code: 'ESENYURT', name: 'Esenyurt', slug: 'esenyurt' },
  { code: 'EYUPSULTAN', name: 'Eyüpsultan', slug: 'eyupsultan' },
  { code: 'FATIH', name: 'Fatih', slug: 'fatih' },
  { code: 'GAZIOSMANPASA', name: 'Gaziosmanpaşa', slug: 'gaziosmanpasa' },
  { code: 'GUNGOREN', name: 'Güngören', slug: 'gungoren' },
  { code: 'KADIKOY', name: 'Kadıköy', slug: 'kadikoy' },
  { code: 'KAGITHANE', name: 'Kağıthane', slug: 'kagithane' },
  { code: 'KARTAL', name: 'Kartal', slug: 'kartal' },
  { code: 'KUCUKCEKMECE', name: 'Küçükçekmece', slug: 'kucukcekmece' },
  { code: 'MALTEPE', name: 'Maltepe', slug: 'maltepe' },
  { code: 'PENDIK', name: 'Pendik', slug: 'pendik' },
  { code: 'SANCAKTEPE', name: 'Sancaktepe', slug: 'sancaktepe' },
  { code: 'SARIYER', name: 'Sarıyer', slug: 'sariyer' },
  { code: 'SILIVRI', name: 'Silivri', slug: 'silivri' },
  { code: 'SULTANBEYLI', name: 'Sultanbeyli', slug: 'sultanbeyli' },
  { code: 'SULTANGAZI', name: 'Sultangazi', slug: 'sultangazi' },
  { code: 'SILE', name: 'Şile', slug: 'sile' },
  { code: 'SISLI', name: 'Şişli', slug: 'sisli' },
  { code: 'TUZLA', name: 'Tuzla', slug: 'tuzla' },
  { code: 'UMRANIYE', name: 'Ümraniye', slug: 'umraniye' },
  { code: 'USKUDAR', name: 'Üsküdar', slug: 'uskudar' },
  { code: 'ZEYTINBURNU', name: 'Zeytinburnu', slug: 'zeytinburnu' },
]

export function findDistrictBySlug(slug: string): District | undefined {
  return DISTRICTS.find((d) => d.slug === slug)
}
