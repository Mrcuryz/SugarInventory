const { chromium, expect } = require('@playwright/test')

const baseUrl = process.env.P3_SMOKE_BASE_URL || 'http://127.0.0.1:5174'

const success = (data) => ({
  code: 200,
  msg: 'success',
  data
})

const taskRecord = ({ index, type, status = '待处理', code, productionDate = '2026-05-22', weight = '1000.0 kg' }) => ({
  kind: 'pallet_task',
  label: `${index}. ${type}任务`,
  taskTypeLabel: type,
  taskStatusLabel: status,
  productLabel: '黄冰糖（袋）',
  productStatusLabel: '成品',
  businessSceneLabel: type === '成品出库' ? '成品出库' : '成品入库',
  palletCode: code,
  productionDate,
  totalWeightText: weight
})

const updatedTaskCard = {
  cardType: 'pallet_tasks',
  title: '任务状态（已更新）· 待处理 8 条，已确认 2 条',
  fields: [
    taskRecord({ index: 1, type: '成品入库', status: '已确认', code: 'BT001ADS' }),
    taskRecord({ index: 2, type: '成品入库', status: '已确认', code: 'BT001AQS' }),
    taskRecord({ index: 3, type: '成品入库', code: 'BT001B3J' }),
    taskRecord({ index: 4, type: '成品入库', code: 'BT001BGW' }),
    taskRecord({ index: 5, type: '成品入库', code: 'BT0019N1' }),
    taskRecord({ index: 6, type: '成品入库', code: 'BT001A0F', weight: '375.0 kg' }),
    taskRecord({ index: 7, type: '成品出库', code: 'BT00135D', productionDate: '2026-05-07' }),
    taskRecord({ index: 8, type: '成品出库', code: 'BT0012SZ', productionDate: '2026-05-07' }),
    taskRecord({ index: 9, type: '成品入库', code: 'BT0013IQ', productionDate: '2026-05-07' }),
    taskRecord({ index: 10, type: '成品入库', code: 'BT0013V3', productionDate: '2026-05-07', weight: '375.0 kg' })
  ]
}

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
      `data: ${JSON.stringify({ eventId: 'smoke-2', messageId: 'msg_smoke', agentSessionId: 'visual-session-1', type: 'card', sequence: 2, payload: updatedTaskCard })}`,
      '',
      'data: {"eventId":"smoke-3","messageId":"msg_smoke","agentSessionId":"visual-session-1","type":"text_delta","sequence":3,"payload":{"text":"已收到测试消息。"}}',
      '',
      'data: {"eventId":"smoke-4","messageId":"msg_smoke","agentSessionId":"visual-session-1","type":"message_end","sequence":4,"payload":{"finishReason":"completed"}}',
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
  const taskCard = page.locator('.task-card')
  await expect(taskCard).toContainText('任务状态')
  await expect(taskCard).toContainText('已更新')
  await expect(taskCard).toContainText('共 10 条')
  await expect(taskCard).toContainText('待处理 8')
  await expect(taskCard).toContainText('已确认 2')
  await expect(taskCard.getByRole('button', { name: '展开成品入库' })).toBeVisible()
  await expect(taskCard.getByRole('button', { name: '展开成品出库' })).toBeVisible()
  await expect(taskCard.getByText('全选可处理项（6）')).toBeVisible()
  await expect(taskCard.getByText('全选可处理项（2）')).toBeVisible()
  await expect(taskCard.locator('.task-row:visible')).toHaveCount(0)

  await taskCard.getByRole('button', { name: '展开成品入库' }).click()
  await expect(taskCard.locator('.task-row:visible')).toHaveCount(8)
  const firstPendingTask = taskCard.locator('.task-row').filter({ hasText: '3. 成品入库任务' })
  await firstPendingTask.click()
  await expect(firstPendingTask).toHaveClass(/is-selected/)
  await expect(taskCard.getByRole('button', { name: '处理所选 1 条' })).toBeVisible()
  await taskCard.getByText('全选可处理项（6）').click()
  await expect(taskCard).toContainText('已选择 6 / 6 条')
  await expect(taskCard.getByRole('button', { name: '处理所选 6 条' })).toBeVisible()
  await expect(taskCard.locator('.task-row.is-completed')).toHaveCount(2)
  await expect(page.getByRole('button', { name: '收起', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '结束会话' })).toBeVisible()

  const layout = await page.evaluate(() => {
    const drawer = document.querySelector('.agent-assistant-drawer')
    const header = document.querySelector('.assistant-header')
    const messageWrap = drawer?.querySelector('.message-list .el-scrollbar__wrap')
    const targets = drawer
      ? [drawer, header, messageWrap, ...drawer.querySelectorAll('.message-row, .message-stack, .message-bubble, .business-card, .task-group, .task-row, button, textarea, input')]
      : []
    const overflowing = targets.filter(Boolean).filter((element) => {
      const rect = element.getBoundingClientRect()
      return rect.left < -1 || rect.right > window.innerWidth + 1 || element.scrollWidth > element.clientWidth + 1
    })
    return {
      bodyOverflow: document.documentElement.scrollWidth > window.innerWidth || document.body.scrollWidth > window.innerWidth,
      messageHorizontalOverflow: Boolean(messageWrap && messageWrap.scrollWidth > messageWrap.clientWidth + 1),
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

  if (layout.bodyOverflow || layout.messageHorizontalOverflow || layout.overflowingCount > 0) {
    throw new Error(`Drawer overflow detected: ${JSON.stringify(layout)}`)
  }

  await page.screenshot({ path: 'output/playwright/p3-identity-mobile.png', fullPage: true })

  await page.setViewportSize({ width: 900, height: 720 })
  await expect.poll(() => page.locator('.agent-assistant-drawer')
    .evaluate(element => Math.round(element.getBoundingClientRect().width))).toBe(680)
  await page.screenshot({ path: 'output/playwright/p3-task-card-desktop.png', fullPage: true })
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
