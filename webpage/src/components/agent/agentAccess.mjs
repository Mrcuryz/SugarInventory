export const AGENT_ADMIN_ROLES = Object.freeze(['ADMIN', 'SUPER_ADMIN'])
export const AGENT_USE_PERMISSION = 'agent:use'

export const isAgentAdminRole = roleCode =>
  AGENT_ADMIN_ROLES.includes(String(roleCode || '').trim().toUpperCase())

export const canUseAgent = (roleCode, permissionCodes = []) =>
  isAgentAdminRole(roleCode) || (permissionCodes || []).includes(AGENT_USE_PERMISSION)
