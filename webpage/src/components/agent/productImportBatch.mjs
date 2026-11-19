// Receipts describe immutable source rows, not necessarily contiguous Excel row numbers.
export async function reviewNextRows({ receipt, limit, read, has, review, active, shouldPause, progress }) {
  const { reference, rowCount } = receipt
  if (!reference || !Number.isInteger(rowCount) || rowCount < 1 || rowCount > 500 ||
      !Number.isInteger(limit) || limit < 1 || limit > 500) throw new Error('表格信息有变，请刷新后重试。')
  const seen = new Set()
  let completed = 0
  const check = () => { if (!active()) throw new Error('本次会话或表格已失效，请重新打开表格。') }
  for (let page = 1; page <= Math.ceil(rowCount / 50); page++) {
    check()
    if (shouldPause()) return { completed, paused: true }
    const data = await read(reference, page)
    check()
    if (data?.attachment?.reference !== reference || data.attachment.rowCount !== rowCount ||
        data.page !== page || data.pageSize !== 50 || !Array.isArray(data.rows) ||
        data.rows.length !== Math.min(50, rowCount - (page - 1) * 50)) throw new Error('读取的表格页不完整，请刷新后继续。')
    for (const { rowNumber } of data.rows) {
      check()
      if (shouldPause()) return { completed, paused: true }
      if (!Number.isInteger(rowNumber) || rowNumber < 2 || rowNumber > 501 || seen.has(rowNumber)) {
        throw new Error('表格行号不一致，请刷新后重试。')
      }
      seen.add(rowNumber)
      if (has(rowNumber)) continue
      progress({ completed, rowNumber })
      if (!await review(rowNumber)) throw new Error(`第 ${rowNumber} 行未取得核对结果。已完成的行仍保留，可继续重试。`)
      check()
      completed++
      if (completed === limit) return { completed, paused: false }
    }
  }
  return { completed, paused: false }
}
