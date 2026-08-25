import test from 'node:test'
import assert from 'node:assert/strict'
import { AGENT_ADMIN_ROLES, AGENT_USE_PERMISSION, canUseAgent, isAgentAdminRole } from './agentAccess.mjs'

test('allows only Agent administrator roles', () => {
  assert.deepEqual(AGENT_ADMIN_ROLES, ['ADMIN', 'SUPER_ADMIN'])
  assert.equal(isAgentAdminRole('ADMIN'), true)
  assert.equal(isAgentAdminRole('SUPER_ADMIN'), true)
  assert.equal(isAgentAdminRole(' super_admin '), true)
})

test('allows AI assistant access by explicit permission without granting administrator role', () => {
  assert.equal(AGENT_USE_PERMISSION, 'agent:use')
  assert.equal(canUseAgent('QC', ['agent:use', 'assay:view']), true)
  assert.equal(canUseAgent('WAREHOUSE_MANAGER', ['agent:use']), true)
  assert.equal(canUseAgent('PROD_SUPERVISOR', []), false)
  assert.equal(canUseAgent('ADMIN', []), true)
})

test('rejects non-administrator and missing roles', () => {
  for (const roleCode of ['STAFF', 'QC', 'WAREHOUSE', '', null, undefined]) {
    assert.equal(isAgentAdminRole(roleCode), false)
  }
})
