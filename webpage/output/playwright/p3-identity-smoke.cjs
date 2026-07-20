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
  let createSessionCount = 0
  let revokeSessionCount = 0
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
    createSessionCount += 1
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
        mcpServerName: 'smart_warehouse',
        mcpTransport: 'STDIO'
      }))
    })
  })

  await page.route('**/api/agent/sessions/visual-session-1', route => {
    if (route.request().method() === 'DELETE') {
      revokeSessionCount += 1
    }
    return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(success({}))
    })
  })

  await page.route('**/api/agent/sessions/visual-session-1/messages/stream', route => route.fulfill({
    contentType: 'text/event-stream',
    body: [
      'data: {"eventId":"smoke-1","messageId":"msg_smoke","agentSessionId":"visual-session-1","type":"message_start","sequence":1,"payload":{"role":"assistant"}}',
      '',
      'data: {"eventId":"smoke-2","messageId":"msg_smoke","agentSessionId":"visual-session-1","type":"text_delta","sequence":2,"payload":{"text":"已收到测试消息。"}}',
      '',
      'data: {"eventId":"smoke-3","messageId":"msg_smoke","agentSessionId":"visual-session-1","type":"message_end","sequence":3,"payload":{"finishReason":"completed"}}',
      '',
      ''
    ].join('\n')
  }))

  await page.goto(`${baseUrl}/home`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  if (await page.locator('.agent-button').count() === 0) {
    await page.screenshot({ path: 'output/playwright/p3-before-agent-button.png', fullPage: true })
    const bodyText = await page.locator('body').innerText().catch(() => '')
    throw new Error(`Agent button was not rendered. url=${page.url()} body=${bodyText.slice(0, 500)} logs=${browserMessages.slice(-10).join(' | ')}`)
  }
  await page.locator('.agent-button').click()
  await page.waitForTimeout(700)

  await expect(page.getByLabel('AI 助手会话身份')).toHaveCount(0)
  await expect(page.locator('.assistant-header')).toContainText('会话已连接')
  await expect(page.locator('.assistant-header')).not.toContainText('模型运行中')
  await expect(page.locator('.assistant-header')).not.toContainText('调试')
  await expect(page.getByText('陈思聪').first()).toBeVisible()
  await expect(page.locator('.model-badge')).toHaveCount(0)

  const textarea = page.locator('.composer textarea')
  const emptyComposerHeight = await textarea.evaluate(element => element.clientHeight)
  await textarea.fill('第一行')
  await expect.poll(() => textarea.evaluate(element => element.clientHeight)).toBe(emptyComposerHeight)
  const singleLineHeight = await textarea.evaluate(element => element.clientHeight)
  await textarea.fill('第一行\n第二行\n第三行')
  await expect.poll(() => textarea.evaluate(element => element.clientHeight)).toBeGreaterThan(singleLineHeight)
  const cappedComposerHeight = await textarea.evaluate(element => element.clientHeight)
  await textarea.fill('第一行\n第二行\n第三行\n第四行\n第五行\n第六行')
  const overflowMetrics = await textarea.evaluate(element => ({
    clientHeight: element.clientHeight,
    scrollHeight: element.scrollHeight,
    overflowY: getComputedStyle(element).overflowY
  }))
  if (
    overflowMetrics.clientHeight !== cappedComposerHeight ||
    overflowMetrics.scrollHeight <= overflowMetrics.clientHeight ||
    !['auto', 'scroll'].includes(overflowMetrics.overflowY)
  ) {
    throw new Error(`Composer should cap its height and scroll internally: ${JSON.stringify(overflowMetrics)}`)
  }

  await textarea.fill('第一行')
  await textarea.press('Shift+Enter')
  await textarea.type('第二行')
  await expect(textarea).toHaveValue('第一行\n第二行')
  await textarea.press('Enter')
  await expect(page.locator('.message-row.user').filter({ hasText: '第一行' })).toBeVisible()
  await expect(page.getByText('已收到测试消息。')).toBeVisible()
  await expect(page.getByRole('button', { name: '收起' })).toBeVisible()
  await expect(page.getByRole('button', { name: '结束会话' })).toBeVisible()

  const layout = await page.evaluate(() => {
    const drawer = document.querySelector('.agent-assistant-drawer')
    const header = document.querySelector('.assistant-header')
    const targets = drawer ? [drawer, header, ...drawer.querySelectorAll('button, textarea, input')] : []
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

  await page.setViewportSize({ width: 900, height: 720 })
  await page.mouse.click(24, 120)
  await expect(page.locator('.agent-assistant-drawer')).toBeHidden()
  if (revokeSessionCount !== 0) {
    throw new Error(`Backdrop suspend should not revoke session, got revokeSessionCount=${revokeSessionCount}`)
  }
  await page.locator('.agent-button').click()
  await expect(page.locator('.agent-assistant-drawer')).toBeVisible()
  await expect(page.getByText('已收到测试消息。')).toBeVisible()
  if (createSessionCount !== 1) {
    throw new Error(`Reopening a suspended assistant should reuse session, got createSessionCount=${createSessionCount}`)
  }

  await browser.close()
}

main().catch(error => {
  console.error(error)
  process.exit(1)
})
