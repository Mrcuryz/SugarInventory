<template>
  <div class="user-management-page">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="工号" style="width: 200px">
          <el-input v-model="searchForm.employeeId" clearable />
        </el-form-item>
        <el-form-item label="姓名" style="width: 180px">
          <el-input v-model="searchForm.name" clearable />
        </el-form-item>
        <el-form-item label="手机号" style="width: 220px">
          <el-input v-model="searchForm.mobile" clearable />
        </el-form-item>
        <el-form-item label="部门" style="width: 180px">
          <el-input v-model="searchForm.department" clearable />
        </el-form-item>
        <el-form-item label="状态" style="width: 160px">
          <el-select v-model="searchForm.status" clearable placeholder="全部状态">
            <el-option label="在职" value="在职" />
            <el-option label="离职" value="离职" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" style="width: 220px">
          <el-select v-model="searchForm.roleCode" clearable placeholder="全部角色">
            <el-option
              v-for="item in roleOptions"
              :key="item.roleCode"
              :label="item.roleName"
              :value="item.roleCode"
            />
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
        <div class="table-toolbar-left">
          <el-upload
            v-if="canCreate"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :http-request="customRequest"
            accept=".xlsx,.xls"
          >
            <el-button :loading="uploadLoading">
              {{ uploadLoading ? '上传中...' : '导入Excel' }}
            </el-button>
          </el-upload>
          <el-button v-if="canCreate" type="primary" @click="handleAdd">新增用户</el-button>
          <el-button v-if="canDelete" type="danger" @click="handleDelete">清理离职员工</el-button>
        </div>
      </div>

      <el-table
        :data="resultList"
        style="width: 100%"
        stripe
        border
        v-loading="loading"
      >
        <el-table-column prop="employeeId" label="工号" min-width="140" />
        <el-table-column prop="name" label="姓名" min-width="120" />
        <el-table-column prop="mobile" label="手机号" min-width="140" />
        <el-table-column prop="department" label="部门" min-width="140" />
        <el-table-column prop="position" label="职位" min-width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '在职' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="roleCode" label="当前角色" min-width="140">
          <template #default="{ row }">
            {{ roleNameMap[row.roleCode] || row.roleCode || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canUpdate" type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
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
      :title="operationType"
      width="560px"
      destroy-on-close
      :before-close="handleClose"
    >
      <el-form ref="formRef" :model="submitForm" :rules="rules" label-width="88px">
        <el-form-item label="工号" prop="employeeId">
          <el-input v-model="submitForm.employeeId" clearable />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="submitForm.name" clearable />
        </el-form-item>
        <el-form-item label="手机号" prop="mobile">
          <el-input v-model="submitForm.mobile" clearable />
        </el-form-item>
        <el-form-item label="部门" prop="department">
          <el-input v-model="submitForm.department" clearable />
        </el-form-item>
        <el-form-item label="职位" prop="position">
          <el-input v-model="submitForm.position" clearable />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="submitForm.status">
            <el-option label="在职" value="在职" />
            <el-option label="离职" value="离职" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-model="submitForm.roleCode" clearable placeholder="请选择角色">
            <el-option
              v-for="item in roleOptions"
              :key="item.roleCode"
              :label="item.roleName"
              :value="item.roleCode"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addEmployee, deleteEmployee, getEmployeeList, updateEmployee, uploadFile } from '@/api/employee'
import { getRoleOptions } from '@/api/rbac'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const canCreate = computed(() => authStore.hasPermission('user:create'))
const canUpdate = computed(() => authStore.hasPermission('user:update'))
const canDelete = computed(() => authStore.hasPermission('user:delete'))

const roleOptions = ref([])
const roleNameMap = computed(() => roleOptions.value.reduce((result, item) => {
  result[item.roleCode] = item.roleName
  return result
}, {}))

const uploadLoading = ref(false)
const loading = ref(false)
const dialogVisible = ref(false)
const operationType = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const resultList = ref([])
const formRef = ref(null)

const searchForm = reactive({
  employeeId: '',
  name: '',
  mobile: '',
  department: '',
  status: '',
  roleCode: ''
})

const createEmptySubmitForm = () => ({
  id: '',
  employeeId: '',
  name: '',
  mobile: '',
  department: '',
  position: '',
  status: '在职',
  roleCode: ''
})

const submitForm = ref(createEmptySubmitForm())

const rules = {
  employeeId: [{ required: true, message: '请输入员工编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入员工姓名', trigger: 'blur' }],
  mobile: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const EXCEL_MIME_TYPES = [
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
]

const beforeUpload = file => {
  if (!EXCEL_MIME_TYPES.includes(file.type)) {
    ElMessage.error('仅支持 .xls 和 .xlsx 文件')
    return false
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 20MB')
    return false
  }
  return true
}

const customRequest = async ({ file }) => {
  try {
    uploadLoading.value = true
    const formData = new FormData()
    formData.append('file', file)
    await uploadFile(formData)
    ElMessage.success('导入成功')
    await handleSearch()
  } finally {
    uploadLoading.value = false
  }
}

const loadRoleOptions = async () => {
  const res = await getRoleOptions()
  roleOptions.value = res.data || []
}

const handleSearch = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      employeeId: searchForm.employeeId || undefined,
      name: searchForm.name || undefined,
      mobile: searchForm.mobile || undefined,
      department: searchForm.department || undefined,
      status: searchForm.status || undefined,
      roleCode: searchForm.roleCode || undefined
    }
    const res = await getEmployeeList(params)
    total.value = res.data.total || 0
    resultList.value = res.data.records || []
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  Object.assign(searchForm, {
    employeeId: '',
    name: '',
    mobile: '',
    department: '',
    status: '',
    roleCode: ''
  })
  currentPage.value = 1
  handleSearch()
}

const handleSizeChange = size => {
  pageSize.value = size
  handleSearch()
}

const handleCurrentChange = page => {
  currentPage.value = page
  handleSearch()
}

const handleClose = () => {
  submitForm.value = createEmptySubmitForm()
  dialogVisible.value = false
}

const handleAdd = () => {
  operationType.value = '新增用户'
  submitForm.value = createEmptySubmitForm()
  dialogVisible.value = true
}

const handleEdit = row => {
  operationType.value = '编辑用户'
  submitForm.value = {
    id: row.id,
    employeeId: row.employeeId,
    name: row.name,
    mobile: row.mobile,
    department: row.department,
    position: row.position,
    status: row.status,
    roleCode: row.roleCode
  }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (operationType.value === '新增用户') {
    await addEmployee(submitForm.value)
    ElMessage.success('新增成功')
  } else {
    await updateEmployee(submitForm.value)
    ElMessage.success('更新成功')
  }
  dialogVisible.value = false
  submitForm.value = createEmptySubmitForm()
  await handleSearch()
}

const handleDelete = async () => {
  await ElMessageBox.confirm('确认清理全部离职员工记录吗？', '清理离职员工', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  })
  await deleteEmployee()
  ElMessage.success('清理成功')
  await handleSearch()
}

onMounted(async () => {
  await loadRoleOptions()
  await handleSearch()
})
</script>

<style scoped>
.user-management-page {
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
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.table-toolbar-left {
  display: flex;
  gap: 12px;
  align-items: center;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
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

:deep(.el-table__body tr:hover > td) {
  background-color: var(--app-hover) !important;
}
</style>
