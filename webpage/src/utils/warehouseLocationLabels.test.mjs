import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildWarehouseLocationLabels,
  capacityText,
  selectedWarehouseIds
} from './warehouseLocationLabels.mjs'

test('selectedWarehouseIds keeps stable unique positive IDs', () => {
  assert.deepEqual(selectedWarehouseIds([
    {id: 3},
    {warehouseId: '8'},
    {id: 3},
    {id: 0},
    null
  ]), [3, 8])
})

test('buildWarehouseLocationLabels rejects incomplete labels', () => {
  assert.deepEqual(buildWarehouseLocationLabels([
    {code: ' LSI|WAREHOUSE|1|3 ', warehouseName: ' 3号库位 '},
    {code: '', warehouseName: '4号库位'}
  ]), [{code: 'LSI|WAREHOUSE|1|3', title: '3号库位'}])
})

test('capacityText handles configured and open capacity', () => {
  assert.equal(capacityText({curCapacity: 6, maxCapacity: 20}), '6 / 20 板')
  assert.equal(capacityText({curCapacity: 2, maxCapacity: null}), '2 板')
})
