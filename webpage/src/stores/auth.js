import { defineStore } from 'pinia'
import { getCurrentUserInfo } from '@/api/user'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    name: '',
    employeeId: '',
    roleCode: '',
    permissionCodes: [],
    loaded: false
  }),
  actions: {
    setUserInfo(payload = {}) {
      this.name = payload.name || ''
      this.employeeId = payload.employeeId || ''
      this.roleCode = payload.roleCode || ''
      this.permissionCodes = Array.isArray(payload.permissionCodes) ? [...new Set(payload.permissionCodes)] : []
      this.loaded = true
    },
    clearAuth() {
      this.name = ''
      this.employeeId = ''
      this.roleCode = ''
      this.permissionCodes = []
      this.loaded = false
    },
    async ensureLoaded(force = false) {
      if (this.loaded && !force) return this
      const res = await getCurrentUserInfo()
      this.setUserInfo(res.data || {})
      return this
    },
    hasPermission(code) {
      if (!code) return true
      return this.permissionCodes.includes(code)
    },
    hasAnyPermission(codes = []) {
      if (!Array.isArray(codes) || !codes.length) return true
      return codes.some(code => this.hasPermission(code))
    }
  },
  persist: true
})
