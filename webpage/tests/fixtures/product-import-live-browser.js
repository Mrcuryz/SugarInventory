// Run with playwright-cli from an authenticated, fresh local UAT home page, without mocks.
async page => {
  if (new URL(page.url()).origin !== 'http://127.0.0.1:4817') throw Error('仅允许在隔离验收页面运行')
  const cap = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/product-import/capabilities'))
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  const config = (await (await cap).json()).data
  if (config.matchingEnabled !== true || config.executionEnabled !== false) throw Error('必须仅开放匹配、禁止执行')
  await page.getByRole('button', { name: '产品表格', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '产品表格' })
  const [chooser] = await Promise.all([page.waitForEvent('filechooser'),
    dialog.getByRole('button', { name: /^选择Excel文件/ }).click()])
  await chooser.setFiles('outputs/product-import-uat-20261119/产品核对验收.xlsx')
  const upload = dialog.getByRole('button', { name: '上传并核对' })
  await upload.waitFor()
  await dialog.screenshot({ path: 'output/playwright/product-live-01-mapping.png' })
  await upload.click()
  await dialog.getByText('共 6 行 · 未核对 6 行 · 已选且无冲突 0 项').waitFor()
  const section = dialog.locator('.review-summary')
  await section.scrollIntoViewIfNeeded()
  await dialog.screenshot({ path: 'output/playwright/product-live-02-sheet.png' })
  await section.getByRole('button', { name: '核对全部剩余 6 行' }).click()
  await section.getByRole('status').filter({ hasText: '正在核对第 2 行' }).waitFor()
  await section.getByRole('button', { name: '暂停核对' }).click()
  await section.getByText('已暂停，核对了 1 行。').waitFor({ timeout: 180000 })
  await section.getByText('共 6 行 · 未核对 5 行 · 已选且无冲突 0 项').waitFor()
  await section.screenshot({ path: 'output/playwright/product-live-03-paused.png' })
  return '已通过：真实登录会话、只读能力、映射、上传、第一行完成后暂停。'
}
