import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'

const require = createRequire(new URL('../rbac-web/package.json', import.meta.url))
const { chromium } = require('playwright')
const browser = await chromium.launch()

try {
  for (const stem of process.argv.slice(2)) {
    const svg = await readFile(`${stem}.svg`, 'utf8')
    const page = await browser.newPage({ deviceScaleFactor: 1 })
    await page.setContent(`<!doctype html><meta charset="utf-8"><style>html,body{margin:0}svg{display:block}</style>${svg}`)
    const size = await page.locator('svg').evaluate((root) => ({
      width: root.width.baseVal.value,
      height: root.height.baseVal.value,
    }))
    await page.setViewportSize(size)
    await page.evaluate(() => document.fonts.ready)
    await page.screenshot({ path: `${stem}.png` })
    await page.close()
  }
} finally {
  await browser.close()
}
