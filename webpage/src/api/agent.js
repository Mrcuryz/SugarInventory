import request from '@/utils/request'

export const createAgentSession = (data = {}) => request.post('/agent/sessions', data)

export const getCurrentAgentSessions = () => request.get('/agent/sessions/current')

export const sendAgentMessage = (agentSessionId, data) => request.post(`/agent/sessions/${agentSessionId}/messages`, data)

export const revokeAgentSession = (agentSessionId, data = {}) => request.delete(`/agent/sessions/${agentSessionId}`, { data })
