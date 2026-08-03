export const KNOWLEDGE_CARD_TYPE = 'knowledge_evidence'

const KNOWLEDGE_FIELD_LABELS = Object.freeze({
  content: '内容',
  source: '来源'
})

const DISPLAY_LIMITS = Object.freeze({
  title: 240,
  content: 4000,
  source: 800
})

const FORBIDDEN_DISPLAY_PATTERNS = Object.freeze([
  /(?:^|[\s(（])(?:[a-z]:[\\/]|\\\\[^\s\\]+[\\/])/i,
  /(?:^|[\s(（])\/(?:users|home|root|etc|var|tmp|opt|mnt|srv)(?:\/|\b)/i,
  /\b(?:file|jar|classpath):/i,
  /\b(?:document|chunk|evidence|observation)[-_ ]?id\b/i,
  /\b(?:bearer\s+|access[-_ ]?token|refresh[-_ ]?token|password|secret|jdbc:)/i
])

const looksLikeRawJson = (text) => {
  if (!((text.startsWith('{') && text.endsWith('}')) || (text.startsWith('[') && text.endsWith(']')))) {
    return false
  }
  try {
    const value = JSON.parse(text)
    return value !== null && typeof value === 'object'
  } catch {
    return false
  }
}

export const safeKnowledgeDisplayText = (value, maxLength = DISPLAY_LIMITS.content) => {
  if (typeof value !== 'string') return ''
  const text = value.trim()
  if (!text || text.length > maxLength || looksLikeRawJson(text)) return ''
  if (FORBIDDEN_DISPLAY_PATTERNS.some(pattern => pattern.test(text))) return ''
  return text
}

export const isKnowledgeCard = card => card?.cardType === KNOWLEDGE_CARD_TYPE

const knowledgeFieldValue = (card, label, maxLength) => {
  if (!isKnowledgeCard(card) || !Array.isArray(card.fields)) return ''
  const field = card.fields.find(item => item?.label === label)
  return safeKnowledgeDisplayText(field?.value, maxLength)
}

export const knowledgeEvidence = (card) => {
  if (!isKnowledgeCard(card)) return null
  const title = safeKnowledgeDisplayText(card.title, DISPLAY_LIMITS.title) || '现行资料引用'
  const content = knowledgeFieldValue(card, KNOWLEDGE_FIELD_LABELS.content, DISPLAY_LIMITS.content)
  const source = knowledgeFieldValue(card, KNOWLEDGE_FIELD_LABELS.source, DISPLAY_LIMITS.source)
  if (!content || !source) return null

  const [documentPart, ...locationParts] = source.split('，').map(part => part.trim()).filter(Boolean)
  return Object.freeze({
    title,
    content,
    source,
    documentTitle: documentPart || source,
    locationLabel: locationParts.join('，')
  })
}

export const isSafeVisibleAgentCard = card => !isKnowledgeCard(card) || Boolean(knowledgeEvidence(card))

export const knowledgeMessageStatus = (item) => {
  if (item?.role !== 'assistant') return null
  const answer = typeof item.content === 'string' ? item.content.trim() : ''
  const knowledgeCards = (Array.isArray(item.cards) ? item.cards : [])
    .filter(card => Boolean(knowledgeEvidence(card)))

  if (knowledgeCards.length) {
    if (answer.includes('本次查询使用了可核验的关键词证据；语义检索暂时降级。')) {
      return Object.freeze({
        code: 'DEGRADED',
        tone: 'warning',
        label: '关键词检索结果',
        detail: '语义检索暂时不可用，以下来源仍可核验。'
      })
    }
    return Object.freeze({
      code: 'SUCCEEDED',
      tone: 'info',
      label: '现行资料',
      detail: '回答依据当前已校验的静态材料。'
    })
  }

  if (answer.startsWith('现行知识材料中没有检索到与“')) {
    return Object.freeze({
      code: 'NO_DATA',
      tone: 'neutral',
      label: '现行资料中未找到可核验内容',
      detail: '这不代表相关事实一定不存在。'
    })
  }

  if (answer === '知识库当前不可用。' || answer.startsWith('知识库当前不可用，请稍后重试。')) {
    return Object.freeze({
      code: 'UNAVAILABLE',
      tone: 'danger',
      label: '知识库暂不可用',
      detail: '实时库存、库位、托盘和化验查询不受影响。'
    })
  }

  return null
}
