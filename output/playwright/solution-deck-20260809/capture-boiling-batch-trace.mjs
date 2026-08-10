import { createRequire } from 'node:module'
import fs from 'node:fs/promises'
import path from 'node:path'

const require = createRequire(import.meta.url)
const { chromium } = require('../../../webpage/node_modules/playwright')

const outputDir = path.resolve('output/playwright/solution-deck-20260809')
const baseUrl = 'http://127.0.0.1:5173'
const userHex = process.env.DEMO_USER_HEX
const password = process.env.DEMO_PASSWORD

if (!userHex || !password) {
  throw new Error('DEMO_USER_HEX and DEMO_PASSWORD are required')
}

const userName = Buffer.from(userHex, 'hex').toString('utf8')
await fs.mkdir(outputDir, { recursive: true })

const browser = await chromium.launch({
  headless: true,
  channel: 'chrome',
  args: ['--disable-gpu'],
})

const context = await browser.newContext({
  viewport: { width: 1720, height: 980 },
  deviceScaleFactor: 1,
  locale: 'zh-CN',
  timezoneId: 'Asia/Shanghai',
  colorScheme: 'light',
})

const page = await context.newPage()
const consoleMessages = []
page.on('console', (message) => {
  if (['warning', 'error'].includes(message.type())) {
    consoleMessages.push(`${message.type()}: ${message.text()}`)
  }
})

await page.goto(`${baseUrl}/login`, { waitUntil: 'domcontentloaded' })
await page.getByPlaceholder('请输入用户名').fill(userName)
await page.getByPlaceholder('请输入密码').fill(password)
await page.getByRole('button', { name: '立即登录' }).click()
await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 20000 })

await page.goto(`${baseUrl}/production/boiling-batches`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(3500)

const detailButtons = page.getByRole('button', { name: '详情', exact: true })
const detailCount = await detailButtons.count()
let bestIndex = -1
let bestNodeCount = -1

for (let index = 0; index < Math.min(detailCount, 10); index += 1) {
  await detailButtons.nth(index).click()
  const drawer = page.getByRole('dialog', { name: '煮糖批次详情' })
  await drawer.waitFor({ state: 'visible', timeout: 15000 })
  await page.waitForTimeout(2600)

  const nodeCount = await drawer.locator('.trace-flow-node').count()
  if (nodeCount > bestNodeCount) {
    bestNodeCount = nodeCount
    bestIndex = index
  }

  await drawer.locator('.el-drawer__close-btn').click()
  await drawer.waitFor({ state: 'hidden', timeout: 10000 })
  if (nodeCount >= 8) break
}

if (bestIndex < 0) {
  throw new Error('No boiling batch detail row was available')
}

await detailButtons.nth(bestIndex).click()
const drawer = page.getByRole('dialog', { name: '煮糖批次详情' })
await drawer.waitFor({ state: 'visible', timeout: 15000 })
await page.waitForTimeout(3000)

const traceCard = drawer.locator('.trace-card')
await traceCard.scrollIntoViewIfNeeded()
await page.waitForTimeout(800)
await traceCard.screenshot({
  path: path.join(outputDir, '11-boiling-batch-trace.png'),
  animations: 'disabled',
})

await fs.writeFile(
  path.join(outputDir, 'boiling-batch-trace-report.json'),
  JSON.stringify({ detailCount, selectedIndex: bestIndex, traceNodeCount: bestNodeCount, consoleMessages }, null, 2),
  'utf8',
)

await browser.close()
