import assert from 'node:assert/strict'
import test from 'node:test'

import {
  KNOWLEDGE_CARD_TYPE,
  isKnowledgeCard,
  isSafeVisibleAgentCard,
  knowledgeEvidence,
  knowledgeMessageStatus,
  safeKnowledgeDisplayText
} from './knowledgeCardPresentation.mjs'

const knowledgeCard = (overrides = {}) => ({
  cardType: KNOWLEDGE_CARD_TYPE,
  title: '压榨车间工艺流程',
  fields: [
    { label: '内容', value: '甘蔗经过撕解后进入压榨环节。' },
    { label: '来源', value: '压榨车间工艺流程，第 1 页，压榨步骤' }
  ],
  ...overrides
})

test('extracts a readable knowledge citation without exposing extra fields', () => {
  const card = knowledgeCard({
    fields: [
      { label: '内容', value: '甘蔗经过撕解后进入压榨环节。' },
      { label: '来源', value: '压榨车间工艺流程，第 1 页，压榨步骤' },
      { label: 'score', value: '0.98' },
      { label: 'documentId', value: 'doc-private' }
    ]
  })

  assert.equal(isKnowledgeCard(card), true)
  assert.deepEqual(knowledgeEvidence(card), {
    title: '压榨车间工艺流程',
    content: '甘蔗经过撕解后进入压榨环节。',
    source: '压榨车间工艺流程，第 1 页，压榨步骤',
    documentTitle: '压榨车间工艺流程',
    locationLabel: '第 1 页，压榨步骤'
  })
})

test('rejects paths, raw JSON, internal identifiers and credentials', () => {
  for (const unsafeValue of [
    'D:\\private\\corpus\\document.json',
    '\\\\server\\share\\document.docx',
    '/home/agent/corpus/chunks.jsonl',
    'file:///D:/private/document.docx',
    '{"documentId":"doc-private"}',
    'documentId=doc-private',
    'Authorization: Bearer secret-value',
    'jdbc:mysql://127.0.0.1/private'
  ]) {
    assert.equal(safeKnowledgeDisplayText(unsafeValue), '')
  }
})

test('does not fall back to rendering an incomplete or unsafe knowledge card', () => {
  const unsafe = knowledgeCard({
    fields: [
      { label: '内容', value: '安全正文' },
      { label: '来源', value: 'D:\\private\\source.docx' }
    ]
  })
  const incomplete = knowledgeCard({
    fields: [{ label: '内容', value: '只有正文，没有可核验来源。' }]
  })

  assert.equal(knowledgeEvidence(unsafe), null)
  assert.equal(knowledgeEvidence(incomplete), null)
  assert.equal(isSafeVisibleAgentCard(unsafe), false)
  assert.equal(isSafeVisibleAgentCard({ cardType: 'inventory_summary' }), true)
})

test('classifies successful and degraded knowledge messages from frozen semantics', () => {
  const succeeded = knowledgeMessageStatus({
    role: 'assistant',
    content: '根据现行知识材料，找到以下相关内容：',
    cards: [knowledgeCard()]
  })
  const degraded = knowledgeMessageStatus({
    role: 'assistant',
    content: '根据现行知识材料，找到以下相关内容：\n本次查询使用了可核验的关键词证据；语义检索暂时降级。',
    cards: [knowledgeCard()]
  })

  assert.equal(succeeded.code, 'SUCCEEDED')
  assert.equal(succeeded.tone, 'info')
  assert.equal(degraded.code, 'DEGRADED')
  assert.match(degraded.detail, /来源仍可核验/)
})

test('keeps no evidence and unavailable states semantically separate', () => {
  const noData = knowledgeMessageStatus({
    role: 'assistant',
    content: '现行知识材料中没有检索到与“火星仓库”直接相关的内容。这只表示本次知识库范围内没有证据，不代表相关事实一定不存在。',
    cards: []
  })
  const unavailable = knowledgeMessageStatus({
    role: 'assistant',
    content: '知识库当前不可用，请稍后重试。实时库存、库位、托盘和化验查询不受影响。',
    cards: []
  })

  assert.equal(noData.code, 'NO_DATA')
  assert.equal(noData.tone, 'neutral')
  assert.equal(unavailable.code, 'UNAVAILABLE')
  assert.equal(unavailable.tone, 'danger')
})

test('recognizes the allowlisted concise SSE unavailable message', () => {
  const unavailable = knowledgeMessageStatus({
    role: 'assistant',
    content: '知识库当前不可用。',
    cards: []
  })

  assert.equal(unavailable.code, 'UNAVAILABLE')
  assert.equal(unavailable.detail, '实时库存、库位、托盘和化验查询不受影响。')
})

test('does not classify ordinary assistant or user messages as knowledge states', () => {
  assert.equal(knowledgeMessageStatus({
    role: 'assistant',
    content: '当前库存共有 10 个产品。',
    cards: []
  }), null)
  assert.equal(knowledgeMessageStatus({
    role: 'user',
    content: '知识库当前不可用，请稍后重试。',
    cards: []
  }), null)
})
