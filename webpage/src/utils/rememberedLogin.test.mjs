import test from 'node:test'
import assert from 'node:assert/strict'
import {sanitizeRememberedLogin} from './rememberedLogin.mjs'

test('记住登录只保留用户名并清除历史明文密码', () => {
  assert.deepEqual(
    sanitizeRememberedLogin({name: ' admin ', password: 'plain-text-secret', rememberMe: true}),
    {name: 'admin'}
  )
})

test('无有效用户名时不持久化任何登录字段', () => {
  assert.deepEqual(sanitizeRememberedLogin({password: 'plain-text-secret'}), {})
  assert.deepEqual(sanitizeRememberedLogin(null), {})
})
