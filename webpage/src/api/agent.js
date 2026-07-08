import request from '@/utils/request'
import { useTokenStore } from '@/stores/token'

export const createAgentSession = (data = {}) => request.post('/agent/sessions', data)

export const getCurrentAgentSessions = () => request.get('/agent/sessions/current')

export const sendAgentMessage = (agentSessionId, data) => request.post(`/agent/sessions/${agentSessionId}/messages`, data)

export const cancelAgentMessage = (agentSessionId, messageId) =>
  request.post(`/agent/sessions/${agentSessionId}/messages/${messageId}/cancel`)

export const recordAgentMessageReview = (agentSessionId, data) =>
  request.post(`/agent/sessions/${agentSessionId}/message-reviews`, data)

export const submitAgentMessageReviewFeedback = (agentSessionId, messageId, data) =>
  request.post(`/agent/sessions/${agentSessionId}/message-reviews/${messageId}/feedback`, data)

export const pageAgentMessageReviews = (params = {}) => request.get('/agent/reviews', { params })

export const getAgentMessageReviewDetail = id => request.get(`/agent/reviews/${id}`)

export const updateAgentMessageReviewStatus = (id, data) => request.patch(`/agent/reviews/${id}/status`, data)

export const streamAgentMessage = async (agentSessionId, data, onEvent, options = {}) => {
  await streamAgentSse(`/api/agent/sessions/${agentSessionId}/messages/stream`, data, onEvent, options)
}

export const streamAgentInterruptResume = async (agentSessionId, interruptId, data, onEvent, options = {}) => {
  await streamAgentSse(
    `/api/agent/sessions/${agentSessionId}/interrupts/${interruptId}/resume/stream`,
    data,
    onEvent,
    options
  )
}

const streamAgentSse = async (url, data, onEvent, options = {}) => {
  const tokenStore = useTokenStore()
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(tokenStore.token ? { Authorization: `Bearer ${tokenStore.token}` } : {})
    },
    body: JSON.stringify(data),
    signal: options.signal
  })
  if (!response.ok || !response.body) {
    throw new Error(`Agent stream request failed: ${response.status}`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    buffer = consumeSseBuffer(buffer, onEvent)
  }
  buffer += decoder.decode()
  consumeSseBuffer(buffer, onEvent, true)
}

export const revokeAgentSession = (agentSessionId, data = {}) => request.delete(`/agent/sessions/${agentSessionId}`, { data })

export const consumeSseBuffer = (buffer, onEvent, flush = false) => {
  const delimiter = /\r?\n\r?\n/
  let rest = buffer
  while (true) {
    const match = delimiter.exec(rest)
    if (!match) break
    const block = rest.slice(0, match.index)
    rest = rest.slice(match.index + match[0].length)
    dispatchSseBlock(block, onEvent)
  }
  if (flush && rest.trim()) {
    dispatchSseBlock(rest, onEvent)
    return ''
  }
  return rest
}

const dispatchSseBlock = (block, onEvent) => {
  const data = block
    .split(/\r?\n/)
    .filter(line => line.startsWith('data:'))
    .map(line => line.slice(5).trim())
    .join('\n')
  if (!data) return
  onEvent(JSON.parse(data))
}
