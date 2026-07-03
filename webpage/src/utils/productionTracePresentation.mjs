const text = value => String(value || '').trim()

const unique = values => [...new Set(values.map(text).filter(Boolean))]

const occurredAtSecond = value => text(value).replace('T', ' ').slice(0, 19)

const isInboundAction = action => action.includes('入库')

const isMaterialPickAction = action => action.includes('半成品领用') || action.includes('消耗')

const groupContext = record => {
  const action = text(record.actionType)
  if (isInboundAction(action)) {
    return [record.relatedObject, record.warehouseName].map(text).filter(Boolean).join('|')
  }
  if (isMaterialPickAction(action)) {
    return text(record.documentNo || record.relatedObject)
  }
  return text(record.documentNo || record.relatedObject || record.productName)
}

const parseQuantity = value => {
  const quantity = text(value)
  const boardMatch = quantity.match(/(\d+(?:\.\d+)?)板/)
  const pieceMatch = quantity.match(/(\d+(?:\.\d+)?)件/)
  if (!boardMatch && !pieceMatch) return null
  return {
    boards: Number(boardMatch?.[1] || 0),
    pieces: Number(pieceMatch?.[1] || 0)
  }
}

const compactNumber = value => Number.isInteger(value) ? String(value) : String(Number(value.toFixed(3)))

const quantityText = ({ boards, pieces }) => {
  const result = []
  if (boards) result.push(`${compactNumber(boards)}板`)
  if (pieces) result.push(`${compactNumber(pieces)}件`)
  return result.join(' + ')
}

const summarizeProducts = records => {
  const byProduct = new Map()
  records.forEach(record => {
    const product = text(record.productName) || '未标注物料'
    if (!byProduct.has(product)) byProduct.set(product, { boards: 0, pieces: 0, fallback: [] })
    const summary = byProduct.get(product)
    const parsed = parseQuantity(record.quantityText)
    if (parsed) {
      summary.boards += parsed.boards
      summary.pieces += parsed.pieces
    } else if (text(record.quantityText)) {
      summary.fallback.push(text(record.quantityText))
    }
  })

  return [...byProduct.entries()].map(([product, summary]) => {
    const parsedText = quantityText(summary)
    const fallbackText = unique(summary.fallback).join(' + ')
    return [product, parsedText || fallbackText].filter(Boolean).join(' ')
  }).join('；')
}

const primaryDocument = records => {
  const action = text(records[0]?.actionType)
  if (isInboundAction(action)) {
    return unique(records.map(record => record.relatedObject))[0]
      || unique(records.map(record => record.documentNo))[0]
      || '-'
  }
  return unique(records.map(record => record.documentNo))[0]
    || unique(records.map(record => record.relatedObject))[0]
    || '-'
}

const relatedSummary = records => {
  const action = text(records[0]?.actionType)
  if (isInboundAction(action) && records.length > 1) return `${records.length}个二维码`
  const related = unique(records.map(record => record.relatedObject))
  return related.length > 1 ? `${related.length}个关联对象` : related[0] || '-'
}

const operationSummary = records => {
  const action = text(records[0]?.actionType)
  const warehouses = unique(records.map(record => record.warehouseName))
  if (isInboundAction(action)) {
    return `${records.length}条记录${warehouses.length === 1 ? `，入库到${warehouses[0]}` : ''}`
  }
  if (isMaterialPickAction(action)) return `领用${records.length}条半成品记录`
  return records.length > 1 ? `${records.length}条记录` : action
}

export const aggregateTraceTimeline = records => {
  const groups = new Map()
  ;(records || []).filter(Boolean).forEach((record, index) => {
    const action = text(record.actionType) || '流转'
    const key = [
      occurredAtSecond(record.occurredAt),
      action,
      groupContext(record)
    ].join('|') || `record-${index}`
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(record)
  })

  return [...groups.entries()].map(([key, children], index) => {
    const statuses = unique(children.map(record => record.status))
    const warehouses = unique(children.map(record => record.warehouseName))
    const products = unique(children.map(record => record.productName))
    return {
      id: `timeline-group-${index}-${key}`,
      key,
      occurredAt: children[0]?.occurredAt,
      actionType: text(children[0]?.actionType) || '流转',
      documentNo: primaryDocument(children),
      productSummary: products.join('、') || '-',
      quantitySummary: summarizeProducts(children) || '-',
      warehouseSummary: warehouses.join('、') || '-',
      relatedSummary: relatedSummary(children),
      status: statuses.length > 1 ? '多状态' : statuses[0] || '-',
      summary: operationSummary(children),
      count: children.length,
      children
    }
  }).sort((left, right) => text(left.occurredAt).localeCompare(text(right.occurredAt)))
}

export const isAuxiliaryTraceEdge = (source, target) => {
  if (!source || !target) return false
  return target.columnIndex <= source.columnIndex || target.columnIndex - source.columnIndex > 1
}

export const routeTraceEdge = ({ edge, source, target, nodeWidth, nodeHeight, canvasHeight, channelIndex = 0 }) => {
  const auxiliary = isAuxiliaryTraceEdge(source, target)
  if (auxiliary) {
    const sourceX = source.x + nodeWidth
    const sourceY = source.y + nodeHeight / 2
    const targetX = target.x
    const targetY = target.y + nodeHeight / 2
    const sourceChannelX = sourceX + 26
    const targetChannelX = targetX - 24
    const channelY = canvasHeight - 24 - channelIndex * 18
    const labelWidth = 88
    return {
      ...edge,
      auxiliary: true,
      lineType: 'AUXILIARY',
      path: `M ${sourceX} ${sourceY} L ${sourceChannelX} ${sourceY} L ${sourceChannelX} ${channelY} L ${targetChannelX} ${channelY} L ${targetChannelX} ${targetY} L ${targetX} ${targetY}`,
      labelX: (sourceChannelX + targetChannelX) / 2 - labelWidth / 2,
      labelY: channelY - 13,
      labelWidth
    }
  }

  const sourceX = source.x + nodeWidth
  const sourceY = source.y + nodeHeight / 2
  const targetX = target.x
  const targetY = target.y + nodeHeight / 2
  const controlX = sourceX + Math.max(22, (targetX - sourceX) / 2)
  const labelWidth = Math.min(58, Math.max(44, targetX - sourceX - 8))
  return {
    ...edge,
    auxiliary: false,
    path: `M ${sourceX} ${sourceY} C ${controlX} ${sourceY}, ${controlX} ${targetY}, ${targetX - 4} ${targetY}`,
    labelX: (sourceX + targetX) / 2 - labelWidth / 2,
    labelY: (sourceY + targetY) / 2 - 20,
    labelWidth
  }
}



