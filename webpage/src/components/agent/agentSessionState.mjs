// Java returns business-local LocalDateTime values in Asia/Shanghai.
export const agentSessionExpiryMs = (value) => {
  if (typeof value !== 'string' || !value.trim()) return NaN
  const normalized = value.trim().replace(' ', 'T')
  return Date.parse(/(?:Z|[+-]\d{2}:?\d{2})$/i.test(normalized) ? normalized : `${normalized}+08:00`)
}

export const effectiveAgentSessionStatus = (session, now = Date.now()) => {
  if (!session?.agentSessionId) return '未启动'
  if (session.status !== 'ACTIVE') return session.status || 'EXPIRED'
  const expiry = agentSessionExpiryMs(session.expiresAt)
  return Number.isFinite(expiry) && now < expiry ? 'ACTIVE' : 'EXPIRED'
}

export const canSendAgentMessage = (session, now = Date.now()) =>
  effectiveAgentSessionStatus(session, now) === 'ACTIVE'
