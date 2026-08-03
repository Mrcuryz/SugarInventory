export const AGENT_ADMIN_ROLES = Object.freeze(['ADMIN', 'SUPER_ADMIN'])

export const isAgentAdminRole = roleCode =>
  AGENT_ADMIN_ROLES.includes(String(roleCode || '').trim().toUpperCase())
