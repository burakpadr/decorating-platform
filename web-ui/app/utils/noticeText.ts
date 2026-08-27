/**
 * The consent notice, turned into blocks a template can render (§2.3, BOYA-39).
 *
 * The notice is Markdown because it is written and reviewed as prose — legal counsel redrafts it
 * (§16, BOYA-4) and a reviewer should not have to read HTML. It is rendered through this rather than
 * through `v-html` for the obvious reason: the text arrives over the wire, and a screen that will put
 * arbitrary markup on the page is one compromised response away from being a phishing page on our own
 * domain.
 *
 * So the parser is deliberately small. It understands the two things the notice uses — a level-two
 * heading and a paragraph — and treats everything else as text, including any HTML in it, which the
 * template then escapes as the string it is. Bold is dropped rather than rendered: `**30 gün**` is
 * emphasis in the source, and losing it costs nothing next to the risk of growing an inline parser.
 */
export type NoticeBlock = { kind: 'heading' | 'paragraph', text: string }

export function noticeBlocks(body: string): NoticeBlock[] {
  return body
    .split(/\n\s*\n/)
    .map(block => block.trim())
    .filter(block => block.length > 0)
    .map((block) => {
      const heading = /^#{1,6}\s+(.*)$/s.exec(block)
      const text = plain(heading ? heading[1]! : block)
      return { kind: heading ? 'heading' : 'paragraph', text } as NoticeBlock
    })
    .filter(block => block.text.length > 0)
}

/** Emphasis markers off, soft-wrapped lines joined back into one paragraph. */
function plain(text: string): string {
  return text
    .replace(/\*\*(.+?)\*\*/gs, '$1')
    .replace(/(^|\s)\*(\S(?:.*?\S)?)\*(?=\s|$)/gs, '$1$2')
    .split('\n')
    .map(line => line.trim())
    .join(' ')
    .trim()
}
