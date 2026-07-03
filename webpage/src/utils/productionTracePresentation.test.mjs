import assert from 'node:assert/strict'
import test from 'node:test'

import { aggregateTraceTimeline, isAuxiliaryTraceEdge, routeTraceEdge } from './productionTracePresentation.mjs'

test('aggregates same-time semi inbound records and sums board and piece quantities', () => {
  const groups = aggregateTraceTimeline([
    {
      occurredAt: '2026-06-30T15:29:02',
      actionType: '半成品入库',
      documentNo: 'BT000YGI',
      productName: '黄中冰',
      quantityText: '1板',
      warehouseName: '20号库',
      relatedObject: 'PO202606300002',
      status: '已入库'
    },
    {
      occurredAt: '2026-06-30T15:29:02',
      actionType: '半成品入库',
      documentNo: 'BT000YTV',
      productName: '黄中冰',
      quantityText: '16件',
      warehouseName: '20号库',
      relatedObject: 'PO202606300002',
      status: '已入库'
    }
  ])

  assert.equal(groups.length, 1)
  assert.equal(groups[0].count, 2)
  assert.equal(groups[0].documentNo, 'PO202606300002')
  assert.equal(groups[0].quantitySummary, '黄中冰 1板 + 16件')
  assert.equal(groups[0].warehouseSummary, '20号库')
  assert.equal(groups[0].relatedSummary, '2个二维码')
})

test('keeps different operation contexts in separate timeline groups', () => {
  const groups = aggregateTraceTimeline([
    { occurredAt: '2026-06-30T15:32:29', actionType: '半成品领用/消耗', documentNo: 'PO-A', quantityText: '1板' },
    { occurredAt: '2026-06-30T15:32:29', actionType: '半成品领用/消耗', documentNo: 'PO-B', quantityText: '1板' }
  ])
  assert.equal(groups.length, 2)
})

test('routes backward edges through the auxiliary channel', () => {
  const source = { x: 900, y: 80, columnIndex: 6 }
  const target = { x: 420, y: 180, columnIndex: 3 }
  assert.equal(isAuxiliaryTraceEdge(source, target), true)
  const route = routeTraceEdge({
    edge: { id: 'edge-1' },
    source,
    target,
    nodeWidth: 160,
    nodeHeight: 84,
    canvasHeight: 520
  })
  assert.equal(route.auxiliary, true)
  assert.equal(route.lineType, 'AUXILIARY')
  assert.match(route.path, /L/)
  assert.match(route.path, /L 1086 122/)
  assert.match(route.path, /L 396 222/)
})

