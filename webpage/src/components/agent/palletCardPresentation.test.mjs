import assert from 'node:assert/strict'
import test from 'node:test'

import {
  isPalletCard,
  isPalletFlowCard,
  isPalletStatusCard,
  palletEventRoute,
  palletFlowEvents,
  palletRisks,
  palletStatusSummary,
  palletStatusTone,
  palletSummaryEntries
} from './palletCardPresentation.mjs'

test('recognizes controlled pallet cards', () => {
  assert.equal(isPalletCard({ cardType: 'pallet_status' }), true)
  assert.equal(isPalletCard({ cardType: 'pallet_flow_history' }), true)
  assert.equal(isPalletStatusCard({ cardType: 'pallet_status' }), true)
  assert.equal(isPalletFlowCard({ cardType: 'pallet_flow_history' }), true)
  assert.equal(isPalletCard({ cardType: 'pallet_tasks' }), false)
})

test('extracts only allowlisted pallet field kinds', () => {
  const card = {
    fields: [
      { kind: 'pallet_status_summary', codeLabel: 'BT000YGI', currentStatusLabel: '在库' },
      { kind: 'pallet_flow_event', eventLabel: '入库' },
      { kind: 'pallet_risk', value: '存在风险' },
      { kind: 'internal_ref', value: 'must-not-render' }
    ]
  }
  assert.equal(palletStatusSummary(card).codeLabel, 'BT000YGI')
  assert.equal(palletFlowEvents(card).length, 1)
  assert.equal(palletRisks(card).length, 1)
})

test('formats status, summary and route without inventing values', () => {
  assert.equal(palletStatusTone('在库'), 'success')
  assert.equal(palletStatusTone('待入库'), 'pending')
  assert.equal(palletStatusTone('已作废'), 'danger')
  assert.deepEqual(
    palletSummaryEntries({ productLabel: '黄中冰', warehouseLabel: '20号库位', quantityText: '' }),
    [['产品', '黄中冰'], ['当前位置', '20号库位']]
  )
  assert.equal(palletEventRoute({ fromWarehouseLabel: '1号库位', toWarehouseLabel: '20号库位' }), '1号库位 → 20号库位')
})
