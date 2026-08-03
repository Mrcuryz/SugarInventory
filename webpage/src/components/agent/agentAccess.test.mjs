import test from 'node:test'
import assert from 'node:assert/strict'
import { AGENT_ADMIN_ROLES, isAgentAdminRole } from './agentAccess.mjs'

test('allows only Agent administrator roles', () => {
  assert.deepEqual(AGENT_ADMIN_ROLES, ['ADMIN', 'SUPER_ADMIN'])
  assert.equal(isAgentAdminRole('ADMIN'), true)
  assert.equal(isAgentAdminRole('SUPER_ADMIN'), true)
  assert.equal(isAgentAdminRole(' super_admin '), true)
})

test('rejects non-administrator and missing roles', () => {
  for (const roleCode of ['STAFF', 'QC', 'WAREHOUSE', '', null, undefined]) {
    assert.equal(isAgentAdminRole(roleCode), false)
  }
})
