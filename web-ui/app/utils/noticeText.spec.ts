import { describe, expect, it } from 'vitest'

import { noticeBlocks } from './noticeText'

/**
 * The notice parser (§2.3, BOYA-39).
 *
 * Two things are worth a test here and neither is about Markdown. One is that the retention promise
 * survives the trip — §12's "30 gün" is the sentence the screen exists to make, and losing it to an
 * emphasis marker would leave a notice that says less than it was reviewed as saying. The other is that
 * markup in the text stays text, because this output is rendered without `v-html` on purpose.
 */
describe('noticeBlocks', () => {
  it('reads headings and paragraphs, and nothing else', () => {
    const blocks = noticeBlocks('## Başlık\n\nBir paragraf.\n\nİkinci paragraf.')

    expect(blocks).toEqual([
      { kind: 'heading', text: 'Başlık' },
      { kind: 'paragraph', text: 'Bir paragraf.' },
      { kind: 'paragraph', text: 'İkinci paragraf.' },
    ])
  })

  it('keeps the retention promise when it is emphasised', () => {
    const blocks = noticeBlocks('Talebiniz kapandıktan **30 gün** sonra silinir.')

    expect(blocks[0]!.text).toBe('Talebiniz kapandıktan 30 gün sonra silinir.')
  })

  it('joins a soft-wrapped paragraph back into one sentence', () => {
    const blocks = noticeBlocks('Fotoğraflarınız yalnızca teklif\nhazırlamak için kullanılır.')

    expect(blocks[0]!.text).toBe('Fotoğraflarınız yalnızca teklif hazırlamak için kullanılır.')
  })

  it('leaves markup as text: this output is rendered escaped, never as HTML', () => {
    const blocks = noticeBlocks('<script>alert(1)</script>')

    expect(blocks[0]).toEqual({ kind: 'paragraph', text: '<script>alert(1)</script>' })
  })

  it('ignores blank space rather than rendering empty blocks', () => {
    expect(noticeBlocks('\n\n   \n\n')).toEqual([])
  })
})
