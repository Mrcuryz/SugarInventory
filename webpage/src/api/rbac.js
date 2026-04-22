import request from '@/utils/request.js'

export const getRolePage = params => request.post('/rbac/roles/query', params)

export const getRoleOptions = () => request.get('/rbac/roles/options')

export const getRoleDetail = id => request.get(`/rbac/roles/${id}`)

export const createRole = params => request.post('/rbac/roles', params)

export const updateRole = (id, params) => request.put(`/rbac/roles/${id}`, params)

export const updateRolePermissions = (id, params) => request.put(`/rbac/roles/${id}/permissions`, params)

export const updateRoleStatus = (id, params) => request.put(`/rbac/roles/${id}/status`, params)

export const deleteRole = id => request.delete(`/rbac/roles/${id}`)

export const getPermissionList = () => request.get('/rbac/permissions')
