import test from 'node:test'
import assert from 'node:assert/strict'
import {isJsonParseErrorMessage, resolveResponseMessage} from './requestError.mjs'

test('空业务错误消息使用安全提示且不会调用 null.includes', () => {
  assert.equal(resolveResponseMessage({code: 500, msg: null}), '服务异常，请稍后重试')
  assert.equal(isJsonParseErrorMessage(resolveResponseMessage({code: 500, msg: null})), false)
})

test('保留后端明确返回的业务错误消息', () => {
  assert.equal(resolveResponseMessage({code: 403, msg: '权限不足'}), '权限不足')
})

test('JSON 参数解析错误仍可识别为输入错误', () => {
  assert.equal(isJsonParseErrorMessage('JSON parse error: invalid value'), true)
})

test('HTTP 纯文本错误和调用方 fallback 均可稳定处理', () => {
  assert.equal(resolveResponseMessage('服务暂时不可用'), '服务暂时不可用')
  assert.equal(resolveResponseMessage(null, '请求失败：503'), '请求失败：503')
})
