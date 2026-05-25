<template>
  <div class="role-management-page">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="角色关键字" style="width: 240px">
          <el-input v-model="searchForm.keyword" clearable placeholder="名称或编码" />
        </el-form-item>
        <el-form-item label="状态" style="width: 180px">
          <el-select v-model="searchForm.status" clearable placeholder="全部状态">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <div class="table-toolbar">
        <el-button v-if="canCreate" type="primary" @click="openCreateDialog">新增角色</el-button>
      </div>

      <el-table :data="roleList" stripe border v-loading="loading" style="width: 100%">
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="roleCode" label="角色编码" min-width="140" />
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联用户" min-width="260">
          <template #default="{ row }">
            <div v-if="row.userNames?.length" class="tag-wrap">
              <el-tag v-for="name in row.userNames" :key="name" size="small" type="info">{{ name }}</el-tag>
            </div>
            <span v-else class="empty-text">暂无</span>
          </template>
        </el-table-column>
        <el-table-column label="权限数" width="90">
          <template #default="{ row }">{{ row.permissionCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button v-if="canUpdate" type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button
                v-if="canAssignPermission"
                type="success"
                size="small"
                @click="openPermissionDrawer(row)"
              >分配权限</el-button>
              <el-button
                v-if="canUpdate"
                size="small"
                @click="toggleRoleStatus(row)"
              >{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
              <el-button v-if="canDelete" type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="currentPage"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520px"
      destroy-on-close
      :before-close="closeDialog"
    >
      <el-form ref="formRef" :model="submitForm" :rules="rules" label-width="88px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="submitForm.roleName" clearable />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="submitForm.roleCode" clearable :disabled="submitForm.roleCode === 'ADMIN'" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="submitForm.status">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input v-model="submitForm.description" type="textarea" :rows="4" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRole">确定</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="permissionDrawerVisible"
      title="分配权限"
      size="48%"
      destroy-on-close
    >
      <template #default>
        <div class="drawer-header">
          <div class="drawer-title">{{ activeRole?.roleName || '-' }}</div>
          <div class="drawer-subtitle">{{ activeRole?.roleCode || '-' }}</div>
        </div>
        <div class="permission-toolbar">
          <el-button size="small" @click="toggleAllPermissions">
            {{ allPermissionsSelected ? '清空' : '全选' }}
          </el-button>
        </div>
        <el-scrollbar height="calc(100vh - 220px)">
          <div class="permission-group-list">
            <div v-for="group in permissionGroups" :key="group.key" class="permission-group-card">
              <div class="permission-group-header">
                <div class="permission-group-title">{{ group.title }}</div>
                <el-button size="small" text type="primary" @click="toggleGroupPermissions(group)">
                  {{ isGroupFullySelected(group) ? '清空' : '全选' }}
                </el-button>
              </div>
              <el-checkbox-group v-model="selectedPermissionIds">
                <div class="permission-items">
                  <el-checkbox
                    v-for="item in group.items"
                    :key="item.id"
                    :label="item.id"
                  >
                    {{ item.label }}
                  </el-checkbox>
                </div>
              </el-checkbox-group>
            </div>
          </div>
        </el-scrollbar>
      </template>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="permissionDrawerVisible = false">取消</el-button>
          <el-button type="primary" @click="submitPermissions">保存权限</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createRole,
  deleteRole,
  getPermissionList,
  getRoleDetail,
  getRolePage,
  updateRole,
  updateRolePermissions,
  updateRoleStatus
} from '@/api/rbac'
import { useAuthStore } from '@/stores/auth'
import { buildPermissionGroups } from '@/utils/permissionCatalog'

const authStore = useAuthStore()
const canCreate = computed(() => authStore.hasPermission('rbac:role:create'))
const canUpdate = computed(() => authStore.hasPermission('rbac:role:update'))
const canDelete = computed(() => authStore.hasPermission('rbac:role:delete'))
const canAssignPermission = computed(() => authStore.hasPermission('rbac:role:assign_permission'))

const loading = ref(false)
const dialogVisible = ref(false)
const permissionDrawerVisible = ref(false)
const roleList = ref([])
const permissionList = ref([])
const activeRole = ref(null)
const selectedPermissionIds = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const formRef = ref(null)
const dialogTitle = ref('新增角色')

const searchForm = reactive({
  keyword: '',
  status: ''
})

const createEmptyRoleForm = () => ({
  id: null,
  roleName: '',
  roleCode: '',
  status: 'ENABLED',
  description: ''
})

const submitForm = ref(createEmptyRoleForm())

const rules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
}

const permissionGroups = computed(() => buildPermissionGroups(permissionList.value))
const allPermissionIds = computed(() => permissionGroups.value.flatMap(group => group.items.map(item => item.id)))
const allPermissionsSelected = computed(() => {
  const allIds = allPermissionIds.value
  if (!allIds.length) return false
  const selectedIds = new Set(selectedPermissionIds.value)
  return allIds.every(id => selectedIds.has(id))
})

const loadPermissions = async () => {
  const res = await getPermissionList()
  permissionList.value = res.data || []
}

const loadRolePage = async () => {
  loading.value = true
  try {
    const res = await getRolePage({
      page: currentPage.value,
      size: pageSize.value,
      keyword: searchForm.keyword || undefined,
      status: searchForm.status || undefined
    })
    roleList.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const handleSearch = async () => {
  currentPage.value = 1
  await loadRolePage()
}

const handleReset = async () => {
  searchForm.keyword = ''
  searchForm.status = ''
  currentPage.value = 1
  await loadRolePage()
}

const handleSizeChange = size => {
  pageSize.value = size
  loadRolePage()
}

const handleCurrentChange = page => {
  currentPage.value = page
  loadRolePage()
}

const closeDialog = () => {
  submitForm.value = createEmptyRoleForm()
  dialogVisible.value = false
}

const refreshAuthSilently = async () => {
  await authStore.ensureLoaded(true).catch(() => {})
}

const openCreateDialog = () => {
  dialogTitle.value = '新增角色'
  submitForm.value = createEmptyRoleForm()
  dialogVisible.value = true
}

const openEditDialog = row => {
  dialogTitle.value = '编辑角色'
  submitForm.value = {
    id: row.id,
    roleName: row.roleName,
    roleCode: row.roleCode,
    status: row.status || 'ENABLED',
    description: row.description || ''
  }
  dialogVisible.value = true
}

const submitRole = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (submitForm.value.id) {
    await updateRole(submitForm.value.id, submitForm.value)
    ElMessage.success('角色已更新')
  } else {
    const { id, ...payload } = submitForm.value
    await createRole(payload)
    ElMessage.success('角色已新增')
  }
  await refreshAuthSilently()
  dialogVisible.value = false
  submitForm.value = createEmptyRoleForm()
  await loadRolePage()
}

const openPermissionDrawer = async row => {
  const res = await getRoleDetail(row.id)
  activeRole.value = res.data
  selectedPermissionIds.value = [...(res.data.permissionIds || [])]
  permissionDrawerVisible.value = true
}

const submitPermissions = async () => {
  if (!activeRole.value) return
  await updateRolePermissions(activeRole.value.id, {
    roleId: activeRole.value.id,
    permissionIds: selectedPermissionIds.value
  })
  ElMessage.success('权限已保存')
  await refreshAuthSilently()
  permissionDrawerVisible.value = false
  await loadRolePage()
}

const toggleAllPermissions = () => {
  if (allPermissionsSelected.value) {
    selectedPermissionIds.value = []
    return
  }
  selectedPermissionIds.value = [...new Set(allPermissionIds.value)]
}

const isGroupFullySelected = group => {
  if (!group?.items?.length) return false
  const selectedIds = new Set(selectedPermissionIds.value)
  return group.items.every(item => selectedIds.has(item.id))
}

const toggleGroupPermissions = group => {
  if (!group?.items?.length) return
  const groupIds = group.items.map(item => item.id)
  const selectedIds = new Set(selectedPermissionIds.value)
  if (isGroupFullySelected(group)) {
    groupIds.forEach(id => selectedIds.delete(id))
  } else {
    groupIds.forEach(id => selectedIds.add(id))
  }
  selectedPermissionIds.value = [...selectedIds]
}

const toggleRoleStatus = async row => {
  const nextStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const actionText = nextStatus === 'ENABLED' ? '启用' : '停用'
  await ElMessageBox.confirm(`确认${actionText}角色“${row.roleName}”吗？`, `${actionText}角色`, {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  })
  await updateRoleStatus(row.id, { status: nextStatus })
  ElMessage.success(nextStatus === 'ENABLED' ? '角色已启用' : '角色已停用')
  await refreshAuthSilently()
  await loadRolePage()
}

const handleDelete = async row => {
  await ElMessageBox.confirm(`确认删除角色“${row.roleName}”吗？`, '删除角色', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  })
  await deleteRole(row.id)
  ElMessage.success('删除成功')
  await refreshAuthSilently()
  await loadRolePage()
}

onMounted(async () => {
  await loadPermissions()
  await loadRolePage()
})
</script>

<style scoped>
.role-management-page {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
  background: var(--app-panel);
}

.table-card {
  background: var(--app-panel);
}

.table-toolbar {
  margin-bottom: 16px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.action-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.empty-text {
  color: var(--app-text-secondary);
}

.drawer-header {
  margin-bottom: 16px;
}

.drawer-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--app-text);
}

.drawer-subtitle {
  margin-top: 4px;
  color: var(--app-text-secondary);
}

.permission-toolbar {
  margin-bottom: 12px;
  display: flex;
  justify-content: flex-start;
}

.permission-group-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.permission-group-card {
  border: 1px solid var(--app-border-soft);
  border-radius: 8px;
  padding: 16px;
  background: #fff;
}

.permission-group-header {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.permission-group-title {
  font-weight: 600;
  color: var(--app-text);
}

.permission-items {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px 12px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

:deep(.el-table) {
  --el-table-border-color: var(--app-border-soft);
  --el-table-header-bg-color: #f7f8fb;
  --el-table-row-hover-bg-color: var(--app-hover);
}

:deep(.el-table__header th) {
  background-color: #f7f8fb !important;
  color: var(--app-text-secondary);
}
</style>
