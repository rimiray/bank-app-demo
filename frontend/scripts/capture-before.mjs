import { chromium } from 'playwright'
import { mkdirSync } from 'fs'
import { dirname, join } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const outDir = join(__dirname, 'design-review', 'before')
mkdirSync(outDir, { recursive: true })

const base = 'http://127.0.0.1:5173'

async function main() {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
  await page.goto(base, { waitUntil: 'networkidle' })

  // Wait for Google fonts stylesheet + document fonts
  await page.waitForTimeout(800)
  await page.evaluate(() => document.fonts.ready)

  const fontReport = await page.evaluate(async () => {
    await document.fonts.ready
    const loaded = [...document.fonts].map((f) => ({
      family: f.family,
      status: f.status,
      weight: f.weight,
      stretch: f.stretch,
      style: f.style,
    }))

    const pick = (sel) => {
      const el = document.querySelector(sel)
      if (!el) return null
      const cs = getComputedStyle(el)
      return {
        sel,
        text: (el.textContent || '').trim().slice(0, 40),
        fontFamily: cs.fontFamily,
        fontSize: cs.fontSize,
        fontWeight: cs.fontWeight,
        fontStretch: cs.fontStretch,
        letterSpacing: cs.letterSpacing,
        transform: cs.transform,
      }
    }

    return {
      loadedFamilies: [...new Set(loaded.map((f) => f.family))],
      loadedCount: loaded.length,
      samples: [
        pick('h1'),
        pick('header .font-display'),
        pick('body'),
        pick('.font-mono'),
        pick('.label'),
      ],
      checks: {
        hasSyne: document.fonts.check('700 32px "Syne"'),
        hasManrope: document.fonts.check('400 16px "Manrope"'),
        hasPlexMono: document.fonts.check('400 14px "IBM Plex Mono"'),
      },
      stylesheets: [...document.styleSheets]
        .map((s) => {
          try {
            return s.href
          } catch {
            return null
          }
        })
        .filter(Boolean),
    }
  })

  const tabs = [
    { id: 'cards', label: /My Cards|Cards/i, file: '01-cards.png' },
    { id: 'credit', label: /Credit/i, file: '02-credit.png' },
    { id: 'architecture', label: /Architecture/i, file: '03-architecture.png' },
  ]

  for (const tab of tabs) {
    const btn = page.locator('button').filter({ hasText: tab.label }).first()
    await btn.click()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: join(outDir, tab.file),
      fullPage: true,
    })
  }

  console.log(JSON.stringify(fontReport, null, 2))
  await browser.close()
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
