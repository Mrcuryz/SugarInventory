import test from 'node:test'
import assert from 'node:assert/strict'
import { reviewNextRows } from './productImportBatch.mjs'

function setup(numbers, done = new Map()) {
  const receipt = { reference: 'sheet-a', rowCount: numbers.length }, calls = [], pages = []
  const options = { receipt, limit: 5, has: n => done.has(n), active: () => true, shouldPause: () => false, progress: () => {},
    read: async (ref, page) => {
      pages.push(page)
      return { attachment: receipt, page, pageSize: 50, rows: numbers.slice((page - 1) * 50, page * 50).map(rowNumber => ({ rowNumber })) }
    },
    review: async n => { calls.push(n); done.set(n, 'unselected'); return true }
  }
  return { options, calls, pages, done }
}
test('读取分页后的物理行号，不覆盖已核对、已忽略或已勾选的行', async () => {
  const numbers = Array.from({ length: 58 }, (_, i) => i * 2 + 2)
  const prior = new Map(numbers.slice(0, 49).map(n => [n, 'chosen']))
  const { options, calls, pages } = setup(numbers, prior)
  assert.deepEqual(await reviewNextRows(options), { completed: 5, paused: false })
  assert.deepEqual(calls, numbers.slice(49, 54))
  assert.deepEqual(pages, [1, 2])
  assert.equal(prior.get(2), 'chosen')
})
test('暂停等待当前行返回，继续时从下一条未完成行开始', async () => {
  const { options, calls } = setup([3, 5, 8, 11])
  let pause = false
  options.shouldPause = () => pause
  const review = options.review
  options.review = async n => { if (n === 5) pause = true; return review(n) }
  assert.deepEqual(await reviewNextRows(options), { completed: 2, paused: true })
  pause = false
  await reviewNextRows(options)
  assert.deepEqual(calls, [3, 5, 8, 11])
})
test('当前行失败时不跳过，也不自动重试', async () => {
  const { options, calls } = setup([2, 3, 4])
  const review = options.review
  options.review = n => n !== 3 && review(n)
  await assert.rejects(reviewNextRows(options), /第 3 行/)
  assert.deepEqual(calls, [2])
  options.review = review
  await reviewNextRows(options)
  assert.deepEqual(calls, [2, 3, 4])
})
test('附件过期、会话切换或读取了其他页时不发起核对', async () => {
  for (const changed of ['session', 'sheet', 'missing']) {
    const { options, calls } = setup([2, 3])
    let active = true
    options.active = () => active
    const read = options.read
    options.read = async (...args) => {
      const data = await read(...args)
      if (changed === 'session') active = false
      if (changed === 'sheet') data.attachment = { ...data.attachment, reference: 'sheet-b' }
      if (changed === 'missing') data.rows.pop()
      return data
    }
    await assert.rejects(reviewNextRows(options))
    assert.deepEqual(calls, [])
  }
})
