const allowedItemKeys = Object.freeze([
  'code',
  'warehouseName',
  'entryDate',
  'side',
  'quantity',
  'unit',
  'remark'
])

const normalizeItem = source => {
  if (!source || typeof source !== 'object' || Array.isArray(source)) return null
  const code = String(source.code || '').trim().toUpperCase()
  const warehouseName = String(source.warehouseName || '').trim()
  const quantity = Number(source.quantity ?? 1)
  const side = String(source.side || '左').trim()
  const unit = String(source.unit ?? '0').trim()
  if (!code || !warehouseName || !Number.isInteger(quantity) || quantity < 1) return null
  if (!['左', '右'].includes(side) || !['0', '1'].includes(unit)) return null

  const item = { code, warehouseName }
  const entryDate = String(source.entryDate || '').trim()
  if (entryDate) item.entryDate = entryDate
  item.side = side
  item.quantity = quantity
  item.unit = unit
  const remark = String(source.remark || '').trim()
  if (remark) item.remark = remark.slice(0, 255)
  return item
}

export const createFinishInboundExecutionPreviewRequest = ({ previewVersion = 1, items = [] } = {}) => {
  if (previewVersion !== 1 || !Array.isArray(items) || !items.length || items.length > 20) return null
  const safeItems = items.map(normalizeItem)
  if (safeItems.some(item => !item)) return null
  const payload = { previewVersion: 1, items: safeItems }
  return {
    payload,
    message: `请生成成品入库精确执行预览（只预览，不执行）。以下是我已经填写的表单：\n\n\`\`\`json\n${JSON.stringify(payload, null, 2)}\n\`\`\``
  }
}

export const finishInboundExecutionPreviewAllowedItemKeys = allowedItemKeys
