import test from 'node:test'
import assert from 'node:assert/strict'
import { appendProcessStep, processStageLabel } from './agentProcessTrace.mjs'

test('maps controlled Agent stages to user-readable execution steps', () => {
  assert.equal(processStageLabel('routing'), '判断业务领域并选择专家')
  assert.equal(processStageLabel('expert_planning'), '专家确认查询条件并决定工具')
  assert.equal(processStageLabel('production_query'), '调用生产数据工具')
  assert.equal(processStageLabel('analyzing'), '分析工具返回结果')
})

test('keeps process history ordered and removes duplicate heartbeat stages', () => {
  let steps = appendProcessStep([], { stage: 'understanding', text: '正在理解你的问题。' })
  steps = appendProcessStep(steps, { stage: 'routing', text: '正在判断由哪个业务模块处理。' })
  steps = appendProcessStep(steps, { stage: 'routing', text: '正在判断由哪个业务模块处理。' })
  steps = appendProcessStep(steps, { stage: 'production_query', text: '正在查询生产与领料数据。' })

  assert.deepEqual(steps.map(item => item.stage), [
    'understanding',
    'routing',
    'production_query'
  ])
})

