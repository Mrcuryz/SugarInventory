const { chromium, expect } = require('@playwright/test')

const baseUrl = process.env.P3_SMOKE_BASE_URL || 'http://127.0.0.1:5174'

const success = (data) => ({
  code: 200,
  msg: 'success',
  data
})

async function main() {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
  const browserMessages = []
  page.on('console', message => browserMessages.push(`${message.type()}: ${message.text()}`))
  page.on('pageerror', error => browserMessages.push(`pageerror: ${error.message}`))

  await page.addInitScript(() => {
    const auth = {
      name: '陈思聪',
      employeeId: 'CS001',
      roleCode: 'ADMIN',
      permissionCodes: ['dashboard:view', 'inventory:view'],
      loaded: true
    }
    window.localStorage.setItem('token', JSON.stringify({ token: 'p3-visual-token' }))
    window.localStorage.setItem('pinia-token', JSON.stringify({ token: 'p3-visual-token' }))
    window.localStorage.setItem('auth', JSON.stringify(auth))
    window.localStorage.setItem('pinia-auth', JSON.stringify(auth))
  })

  await page.route('**/*', route => {
    const pathname = new URL(route.request().url()).pathname
    if (!pathname.startsWith('/api/')) {
      return route.continue()
    }
    return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(success({ records: [], total: 0 }))
    })
  })

  await page.route('**/api/user/info', route => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(success({
      name: '陈思聪',
      employeeId: 'CS001',
      roleCode: 'ADMIN',
      permissionCodes: [
        'dashboard:view',
        'inventory:view',
        'warehouse:view',
        'warehouse_map:view',
        'product:view',
        'assay:view',
        'log:view',
        'rbac:user:view'
      ]
    }))
  }))

  await page.route('**/api/agent/sessions', route => {
    if (route.request().method() !== 'POST') {
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(success([]))
      })
    }
    return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(success({
        agentSessionId: 'visual-session-1',
        userId: 7,
        name: '陈思聪',
        roleCode: 'ADMIN',
        permissionCodes: ['inventory:view'],
        scopes: ['mcp:warehouse:read'],
        status: 'ACTIVE',
        expiresAt: '2026-07-07T16:39:36',
        modelDisplayName: 'deepseek v4 flash',
        mcpServerName: 'smart_warehouse',
        mcpTransport: 'STDIO'
      }))
    })
  })

  await page.goto(`${baseUrl}/home`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  if (await page.locator('.agent-button').count() === 0) {
    await page.screenshot({ path: 'output/playwright/p3-before-agent-button.png', fullPage: true })
    const bodyText = await page.locator('body').innerText().catch(() => '')
    throw new Error(`Agent button was not rendered. url=${page.url()} body=${bodyText.slice(0, 500)} logs=${browserMessages.slice(-10).join(' | ')}`)
  }
  await page.locator('.agent-button').click()
  await page.waitForTimeout(700)

  const identity = page.getByLabel('AI 助手会话身份')
  await expect(identity).toBeVisible()
  await expect(identity.getByText('智能仓储助手')).toBeVisible()
  await expect(identity.getByText('deepseek v4 flash')).toBeVisible()
  await expect(page.getByText('陈思聪').first()).toBeVisible()
  await expect(page.getByText('管理员').first()).toBeVisible()
  await expect(page.locator('.model-badge', { hasText: 'deepseek v4 flash' })).toBeVisible()

  const layout = await page.evaluate(() => {
    const drawer = document.querySelector('.agent-assistant-drawer')
    const identityStrip = document.querySelector('.identity-strip')
    const targets = drawer ? [drawer, identityStrip, ...drawer.querySelectorAll('button, textarea, input')] : []
    const overflowing = targets.filter(Boolean).filter((element) => {
      const rect = element.getBoundingClientRect()
      return rect.left < -1 || rect.right > window.innerWidth + 1 || element.scrollWidth > element.clientWidth + 1
    })
    return {
      bodyOverflow: document.documentElement.scrollWidth > window.innerWidth || document.body.scrollWidth > window.innerWidth,
      overflowingCount: overflowing.length,
      overflowing: overflowing.map(element => {
        const rect = element.getBoundingClientRect()
        return {
          tag: element.tagName,
          className: element.className,
          text: element.textContent.trim().slice(0, 80),
          left: Math.round(rect.left),
          right: Math.round(rect.right),
          clientWidth: element.clientWidth,
          scrollWidth: element.scrollWidth
        }
      })
    }
  })

  if (layout.bodyOverflow || layout.overflowingCount > 0) {
    throw new Error(`Drawer overflow detected: ${JSON.stringify(layout)}`)
  }

  await page.screenshot({ path: 'output/playwright/p3-identity-mobile.png', fullPage: true })
  await browser.close()
}

main().catch(error => {
  console.error(error)
  process.exit(1)
})
