// Load with playwright-cli run-code --filename before opening the dialog.
// Mock transport only: this does not validate Excel parsing, Redis, authentication, or the real model.
async page => {
  let uploads = 0
  const receipt = { reference: 'aa000000-0000-4000-8000-000000000001', fileName: '验收产品.xlsx',
    sheet: '产品汇总', rowCount: 56, sha256: 'fixture-only', expiresAt: Date.now() + 30 * 60000 }
  const params = new URL(page.url()).searchParams
  let drafts = params.has('seed') || params.has('summary') ? [receipt] : []
  await page.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url())
    if (!url.pathname.startsWith('/api/')) return route.continue() // Vite also serves /src/api/*.js modules.
    const ok = data => route.fulfill({ json: { code: 200, msg: 'success', data } })
    if (!url.pathname.includes('/product-import/')) return route.abort()
    if (url.pathname.endsWith('/inspect')) return ok([
      { name: '产品汇总', hidden: false, columns: [{ index: 0, title: '名称', suggestedField: null },
        { index: 1, title: '单件重量（kg）', suggestedField: 'weightPerPiece' },
        { index: 2, title: '是否可堆叠', suggestedField: 'canStack' }] },
      { name: '内部备注', hidden: true, columns: [] }
    ])
    if (url.pathname.endsWith('/drafts') && request.method() === 'GET') return ok(drafts)
    if (url.pathname.endsWith('/drafts') && request.method() === 'POST') {
      if (++uploads > 1) throw new Error('Unexpected duplicate upload')
      const body = request.postData() || ''
      if (!body.includes('productName') || !body.includes('weightPerPiece')) throw new Error('Explicit mapping missing')
      receipt.expiresAt = Date.now() + 30 * 60000
      drafts = [receipt]
      return ok(receipt)
    }
    if (url.pathname.endsWith('/' + receipt.reference)) {
      if (request.method() === 'DELETE') { drafts = []; return ok(true) }
      const current = Number(url.searchParams.get('page') || 1)
      const start = (current - 1) * 50
      return ok({ attachment: receipt, page: current, pageSize: 50,
        rows: Array.from({ length: Math.max(0, Math.min(50, 56 - start)) }, (_, index) => ({
          rowNumber: start + index + 2,
          values: { productName: index === 0 ? '黄冰糖（袋）' : `白冰糖（箱）${start + index}`,
            weightPerPiece: index === 0 ? '0' : '15', canStack: '否' }
        })) })
    }
    throw new Error('Unexpected endpoint: ' + request.method() + ' ' + url.pathname)
  })
}
