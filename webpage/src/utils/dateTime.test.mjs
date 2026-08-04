import assert from 'node:assert/strict'
import test from 'node:test'

import { formatDateTime } from './dateTime.js'

test('formats backend ISO local date-time for user-facing cards', () => {
  assert.equal(formatDateTime('2026-08-04T06:26:23.9769421'), '2026-08-04 06:26:23')
})

test('keeps the shared empty-value placeholder', () => {
  assert.equal(formatDateTime(''), '-')
})
