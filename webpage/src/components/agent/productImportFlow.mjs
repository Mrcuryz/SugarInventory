export const PRODUCT_FIELDS = Object.freeze([
  { value: 'productName', label: '产品名称' }, { value: 'productType', label: '产品类型' },
  { value: 'status', label: '产品状态' }, { value: 'packagingMethod', label: '包装方式' },
  { value: 'weightPerPiece', label: '单件重量（kg）' }, { value: 'piecesPerPallet', label: '每板件数' },
  { value: 'canStack', label: '是否可堆叠' }, { value: 'screenMeshName', label: '筛网名称' }
])
const fieldKeys = new Set(PRODUCT_FIELDS.map(field => field.value))
export const canImportProducts = (codes = []) => codes.includes('product:view') &&
  (codes.includes('product:create') || codes.includes('product:update'))
export const importBusy = phase => ['inspecting', 'uploading', 'loading', 'removing', 'refreshing'].includes(phase)
export const fileError = file => !file || !/\.(xls|xlsx)$/i.test(file.name || '') ? '请选择xls或xlsx格式的Excel文件' :
  !file.size || file.size > 10 * 1024 * 1024 ? '请选择不超过10MB的Excel文件' : ''
export const mappingError = (sheet, mapping) => {
  if (!sheet || sheet.hidden) return '请选择要读取的工作表'
  const assigned = []
  for (const column of sheet.columns || []) {
    const key = mapping[column.index]
    if (!column.title && !key) continue // Empty header is not silently discarded; server rejects any data below it.
    if (!fieldKeys.has(key)) return `请选择“${column.title || `第${column.index + 1}列`}”对应的内容`
    if (assigned.includes(key)) return '同一种产品信息不能对应多列，请重新选择'
    assigned.push(key)
  }
  return assigned.includes('productName') ? '' : '请指定产品名称对应的列'
}
const message = (error, fallback) => error?.msg || error?.response?.data?.msg || fallback
const initial = () => ({ phase: 'idle', file: null, sheets: [], sheet: '', mapping: {}, attachments: [],
  selectedRef: '', source: null, error: '', recoveryRequired: false })

/** No product writes or model calls. Epoch guards prevent another session receiving a stale response. */
export function createProductImportFlow(api, changed = () => {}) {
  let state = initial(), epoch = 0, disposed = false
  const publish = patch => { state = { ...state, ...patch }; if (!disposed) changed(state) }
  const live = generation => !disposed && generation === epoch
  const available = () => !disposed && !importBusy(state.phase)
  const activeSheet = () => state.sheets.find(sheet => sheet.name === state.sheet)
  const chooseSheet = name => {
    if (!available()) return
    const sheet = state.sheets.find(item => item.name === name && !item.hidden)
    publish({ sheet: sheet?.name || '', mapping: Object.fromEntries((sheet?.columns || [])
      .filter(column => fieldKeys.has(column.suggestedField)).map(column => [column.index, column.suggestedField])), error: '' })
  }
  const refresh = async () => {
    if (!available()) return false
    const generation = epoch
    publish({ phase: 'refreshing', error: '' })
    try {
      const attachments = await api.list()
      if (!live(generation)) return false
      const exists = attachments.some(item => item.reference === state.selectedRef)
      publish({ attachments, phase: 'idle', recoveryRequired: false,
        ...(state.recoveryRequired ? { file: null, sheets: [], sheet: '', mapping: {} } : {}),
        ...(exists ? {} : { selectedRef: '', source: null }) })
      return true
    } catch (error) { if (live(generation)) publish({ phase: 'idle', error: message(error, '暂时无法读取表格列表，请稍后刷新') }); return false }
  }
  const read = async (reference, page = 1) => {
    if (!available()) return false
    const generation = epoch
    publish({ phase: 'loading', selectedRef: reference, source: null, error: '' })
    try {
      const source = await api.read(reference, page)
      if (!live(generation)) return false
      publish({ source, phase: 'idle' })
      return true
    } catch (error) {
      if (live(generation)) publish({ phase: 'idle', error: message(error, '暂时无法读取表格内容，请重试') })
      return false
    }
  }
  const inspect = async file => {
    if (!available() || state.recoveryRequired) return false
    const error = fileError(file)
    if (error) { publish({ file: null, sheets: [], sheet: '', mapping: {}, error }); return false }
    const generation = epoch
    publish({ phase: 'inspecting', file, sheets: [], sheet: '', mapping: {}, error: '', source: null, selectedRef: '' })
    try {
      const sheets = await api.inspect(file)
      if (!live(generation)) return false
      publish({ sheets, phase: 'idle' })
      if (sheets.length === 1 && !sheets[0].hidden) chooseSheet(sheets[0].name)
      if (!sheets.some(sheet => !sheet.hidden)) publish({ error: '文件中没有可读取的工作表' })
      return true
    } catch (error) { if (live(generation)) publish({ phase: 'idle', error: message(error, '暂时无法读取文件，请确认格式后重试') }); return false }
  }
  const upload = async () => {
    if (!available() || state.recoveryRequired) return false
    const error = fileError(state.file) || mappingError(activeSheet(), state.mapping)
    if (error) { publish({ error }); return false }
    const generation = epoch
    publish({ phase: 'uploading', error: '' })
    let receipt
    try {
      receipt = await api.upload(state.file, state.sheet, { ...state.mapping })
    } catch (error) {
      if (live(generation)) {
        // A timeout may occur after the server stored the file. Do not automatically create it again.
        const definiteRejection = Number.isFinite(error?.code) && error.code >= 400 && error.code < 500
        publish({ phase: 'idle', recoveryRequired: !definiteRejection, error: message(error,
          '上传结果未确认，请先刷新表格列表，检查是否已上传；不要重复上传') })
      }
      return false
    }
    if (!live(generation)) return false
    publish({ phase: 'idle', file: null, sheets: [], sheet: '', mapping: {},
      attachments: [receipt, ...state.attachments.filter(item => item.reference !== receipt.reference)] })
    // Reading failure does not discard the successful receipt or trigger another upload.
    return read(receipt.reference)
  }
  const remove = async reference => {
    if (!available()) return false
    const generation = epoch
    publish({ phase: 'removing', error: '' })
    try {
      await api.remove(reference)
      if (!live(generation)) return false
      publish({ phase: 'idle', attachments: state.attachments.filter(item => item.reference !== reference),
        ...(state.selectedRef === reference ? { selectedRef: '', source: null } : {}) })
      return true
    } catch (error) { if (live(generation)) publish({ phase: 'idle', error: message(error, '移除结果未确认，请刷新列表核实') }); return false }
  }
  return { refresh, inspect, chooseSheet, upload, read, remove,
    setMapping: (index, value) => { if (available()) publish({ mapping: { ...state.mapping, [index]: value }, error: '' }) },
    clearFile: () => { if (available()) publish({ file: null, sheets: [], sheet: '', mapping: {}, error: '' }) },
    dispose: () => { disposed = true; epoch++; state = initial() },
    state: () => state }
}
