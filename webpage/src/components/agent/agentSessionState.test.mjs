import test from 'node:test'
import assert from 'node:assert/strict'
import { agentSessionExpiryMs, effectiveAgentSessionStatus, canSendAgentMessage } from './agentSessionState.mjs'

const expiry = Date.parse('2026-09-13T09:00:00+08:00')
const session = { agentSessionId: 'test-session', status: 'ACTIVE', expiresAt: '2026-09-13T09:00:00' }

test('business-local expiry is independent of browser timezone', () => {
  for (const value of ['2026-09-13T09:00:00', '2026-09-13 09:00:00', '2026-09-13T09:00:00+08:00', '2026-09-13T01:00:00Z']) {
    assert.equal(agentSessionExpiryMs(value), expiry)
  }
})
test('expiry boundary blocks sending even when server snapshot says ACTIVE', () => {
  assert.equal(effectiveAgentSessionStatus(session, expiry - 1), 'ACTIVE')
  assert.equal(effectiveAgentSessionStatus(session, expiry), 'EXPIRED')
  assert.equal(canSendAgentMessage(session, expiry + 1), false)
})
test('revoked and missing sessions cannot send', () => {
  assert.equal(effectiveAgentSessionStatus(null, expiry), '未启动')
  assert.equal(canSendAgentMessage({ ...session, status: 'REVOKED' }, expiry - 1), false)
  assert.equal(canSendAgentMessage({ status: 'ACTIVE', expiresAt: session.expiresAt }, expiry - 1), false)
})
test('missing or invalid expiry fails closed instead of extending session', () => {
  for (const expiresAt of [null, '', 'invalid']) {
    assert.equal(canSendAgentMessage({ ...session, expiresAt }, expiry - 1), false)
  }
})
