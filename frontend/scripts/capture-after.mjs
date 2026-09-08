import { chromium } from 'playwright'
import { mkdirSync } from 'fs'
import { dirname, join } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const outDir = join(__dirname, '..', 'design-review', 'after')
mkdirSync(outDir, { recursive: true })
const base = process.env.CAPTURE_BASE || 'http://127.0.0.1:5174'

const mockCards = [
  {
    id: 'tab-a',
    cardNumberMasked: '**** **** **** 1111',
    status: 'ACTIVE',
    balance: 120.0,
    creditLimit: 5000,
    loanPrincipal: 0,
    activeDebt: 0,
    currency: 'EUR',
  },
  {
    id: 'tab-b',
    cardNumberMasked: '**** **** **** 2222',
    status: 'ACTIVE',
    balance: 5340.5,
    creditLimit: 12000,
    loanPrincipal: 2000,
    activeDebt: 150.25,
    currency: 'EUR',
  },
]

async function main() {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })

  await page.route('**/api/v1/cards', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockCards),
      })
      return
    }
    await route.continue()
  })

  await page.goto(base, { waitUntil: 'networkidle' })
  await page.evaluate(() => document.fonts.ready)
  await page.waitForTimeout(500)

  const report = await page.evaluate(async () => {
    await document.fonts.ready
    const families = [...new Set([...document.fonts].map((f) => f.family))]
    const h1 = document.querySelector('h1')
    const cs = h1 ? getComputedStyle(h1) : null
    const figures = [...document.querySelectorAll('.figure')].slice(0, 6).map((el) => {
      const s = getComputedStyle(el)
      return {
        text: (el.textContent || '').trim().slice(0, 32),
        fontFamily: s.fontFamily,
        fontFeatureSettings: s.fontFeatureSettings,
        fontVariantNumeric: s.fontVariantNumeric,
      }
    })
    const upperCss = [...document.querySelectorAll('*')].filter((el) => {
      const t = getComputedStyle(el).textTransform
      return t === 'uppercase'
    }).length
    return {
      families,
      checks: {
        plexSans: document.fonts.check('400 16px "IBM Plex Sans"'),
        plexMono: document.fonts.check('400 14px "IBM Plex Mono"'),
        syne: document.fonts.check('700 32px "Syne"'),
        manrope: document.fonts.check('400 16px "Manrope"'),
      },
      h1: cs && {
        fontFamily: cs.fontFamily,
        fontWeight: cs.fontWeight,
        fontSize: cs.fontSize,
        letterSpacing: cs.letterSpacing,
      },
      figures,
      uppercaseNodes: upperCss,
    }
  })

  const tabs = [
    { label: /My Cards/i, file: '01-cards.png' },
    { label: /Credit/i, file: '02-credit.png' },
    { label: /Architecture/i, file: '03-architecture.png' },
  ]

  for (const tab of tabs) {
    await page.locator('button').filter({ hasText: tab.label }).first().click()
    await page.waitForTimeout(400)
    await page.screenshot({ path: join(outDir, tab.file), fullPage: true })
  }

  // Tabular figures close-up: cards tab, crop to card grid
  await page.locator('button').filter({ hasText: /My Cards/i }).first().click()
  await page.waitForTimeout(300)
  const grid = page.locator('section .grid').first()
  await grid.screenshot({ path: join(outDir, '04-tabular-figures.png') })

  console.log(JSON.stringify(report, null, 2))
  await browser.close()
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
