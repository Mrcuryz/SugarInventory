<template>
  <div class="operation-logs">
    <el-card class="search-card" style="max-width: 1200px">
      <el-form :model="searchForm" inline>
        <el-form-item label="员工编号" style="width: 200px">
          <el-input v-model="searchForm.employeeId" clearable />
        </el-form-item>
        <el-form-item label="员工名称" style="width: 200px">
          <el-input v-model="searchForm.name" clearable />
        </el-form-item>
        <el-form-item label="员工手机" style="width: 300px">
          <el-input v-model="searchForm.mobile" clearable />
        </el-form-item>
        <br>
        <el-form-item label="部门" style="width: 200px">
          <el-input v-model="searchForm.department" clearable />
        </el-form-item>
        <el-form-item label="状态" style="width: 200px">
          <el-input v-model="searchForm.status" clearable />
        </el-form-item>
        <el-form-item label="角色" style="width: 300px">
          <el-input v-model="searchForm.roleCode" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="danger" @click="handleDelete">删除离职员工</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card class="table-card" style="max-width: 1200px">
      <el-table
          :data="resultList"
          style="width: 95%"
          heigth="300"
          stripe
          border
          v-loading="loading"
      >
        <el-table-column prop="employeeId" label="员工编号" width="150">
        </el-table-column>
        <el-table-column prop="name" label="员工名称" width="150">
        </el-table-column>
        <el-table-column prop="mobile" label="员工手机" width="150">
        </el-table-column>
        <el-table-column prop="department" label="部门" width="150">
        </el-table-column>
        <el-table-column prop="position" label="职位" width="150">
        </el-table-column>
        <el-table-column prop="status" label="状态" width="150">
        </el-table-column>
        <el-table-column prop="roleCode" label="角色" width="150">
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="dialogVisible = true;operationType='修改筛网';handleEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="total"
            :page-size="pageSize"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog
        :title=operationType
        v-model="dialogVisible"
        width="40%"
        :before-close="handleClose"
    >
      <el-form :model="submitForm" :rules="rule" label-width="auto">
        <el-form-item label="员工编号" prop="employeeId">
          <el-input v-model="submitForm.employeeId" clearable />
        </el-form-item>
        <el-form-item label="员工名称" prop="name">
          <el-input v-model="submitForm.name" clearable />
        </el-form-item>
        <el-form-item label="员工手机" prop="mobile">
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
            <el-option label="在职" value="在职"></el-option>
            <el-option label="离职" value="离职"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-input v-model="submitForm.roleCode" clearable />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="handleUpdate()">确定</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import {addMesh, deleteMesh, getMesh, updateMesh} from '@/api/mesh'
import { ElMessage, ElMessageBox} from 'element-plus'
import {getEmployeeList, updateEmployee} from "@/api/employee";
// 搜索表单
const searchForm = ref({
  status: '',
  roleCode: '',
  department: '',
  mobile: '',
  name: '',
  employeeId: ''
})
// 筛网列表
const resultList = ref([])
// 分页参数
const pageSize = ref(10)
const currentPage = ref(1)
const total = ref(0)
// 加载状态
const loading = ref(false)

// 处理搜索
const handleSearch = async () => {
  let params = {
    page: currentPage.value,
    size: pageSize.value
  }
  if (searchForm.value.status !== '') {
    params.status = searchForm.value.status
  }
  if (searchForm.value.roleCode !== '') {
    params.roleCode = searchForm.value.roleCode
  }
  if (searchForm.value.department !== '') {
    params.department = searchForm.value.department
  }
  if (searchForm.value.mobile !== '') {
    params.mobile = searchForm.value.mobile
  }
  if (searchForm.value.name !== '') {
    params.name = searchForm.value.name
  }
  if (searchForm.value.employeeId !== '') {
    params.employeeId = searchForm.value.employeeId
  }
  loading.value = true
  let res = await getEmployeeList(params)
  if (res.code === 200) {
    total.value = res.data.total
    resultList.value = res.data.records
    loading.value = false
  } else {
    ElMessage.error(res.msg)
  }
}
// 处理重置
const handleReset = () => {
  searchForm.value = {
    meshName: ''
  }
  handleSearch()
}
// 分页处理
const handleSizeChange = (size) => {
  pageSize.value = size
  handleSearch()
}
const handleCurrentChange = (page) => {
  currentPage.value = page
  handleSearch()
}

const rule = {
  employeeId: [
    { required: true, message: '请输入员工编号', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入员工名称', trigger: 'blur' }
  ],
  mobile: [
    { required: true, message: '请输入员工手机', trigger: 'blur' }
  ],
  roleCode: [
    { required: true, message: '请输入角色', trigger: 'blur' }
  ]
}
const dialogVisible = ref(false)
const handleClose = () => {
  submitForm.value = {
  }
  dialogVisible.value = false
}
const operationType = ref('')
const submitForm = ref({
  id: '',
  employeeId: '',
  name: '',
  mobile: '',
  department: '',
  position: '',
  status: '',
  roleCode: ''
})

// // 新增
// const handleNew = async () => {
//   // 校验表单
//   if (submitForm.value.meshName === '') {
//     ElMessage.error('请输入筛网名称')
//     return
//   }
//   let res = await addMesh(submitForm.value)
//   if (res.code === 200) {
//     ElMessage.success('新增成功')
//     await handleSearch()
//     dialogVisible.value = false
//     submitForm.value = {
//       meshName: '',
//       description: ''
//     }
//   } else {
//     ElMessage.error(res.msg)
//   }
// }

// 删除
const handleDelete = async (row) => {
  await ElMessageBox.confirm(
      '你确认要删除吗?',
      '温馨提示',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning',
      }
  )
      .then(async () => {
        //调用接口
        let res = await deleteEmployee()
        if (res.code === 200) {
          ElMessage({
            type: 'success',
            message: '删除成功',
          })
          await handleSearch()
        } else {
          ElMessage.error(res.msg)
        }
      })
      .catch(() => {
        ElMessage({
          type: 'info',
          message: '用户取消了删除',
        })
      })
}

// 编辑
const handleEdit = (row) => {
  operationType.value = '修改员工信息'
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

const handleUpdate = async () => {
  // 校验表单
  if (submitForm.value.employeeId === '') {
    ElMessage.error('请输入员工编号')
    return
  }
  if (submitForm.value.name === '') {
    ElMessage.error('请输入员工名称')
    return
  }
  if (submitForm.value.mobile === '') {
    ElMessage.error('请输入员工手机')
    return
  }
  if (submitForm.value.roleCode === '') {
    ElMessage.error('请输入角色')
    return
  }
  let res = await updateEmployee(submitForm.value)
  if (res.code === 200) {
    ElMessage.success('修改成功')
    await handleSearch()
    dialogVisible.value = false
    submitForm.value = {
      id: '',
      employeeId: '',
      name: '',
      mobile: '',
      department: '',
      position: '',
      status: '',
      roleCode: ''
    }
  } else {
    ElMessage.error(res.msg)
  }
}

onMounted(() => {
  handleSearch()
})
</script>

<style scoped>
.operation-logs {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
  background: rgb(255, 255, 255);
}

.table-card {
  background: rgba(255, 255, 255, 0.8);
}

.el-form--inline .el-form-item {
  margin-right: 30px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

:deep(.el-table) {
  --el-table-border-color: #d3d3d3;
  --el-table-header-bg-color: #969696;
  --el-table-row-hover-bg-color: rgb(75, 75, 75);
}

:deep(.el-table__header th) {
  background-color: #fdfdfd;
  color: #525252;
}

:deep(.el-table__body tr:hover > td) {
  background-color: rgb(159, 234, 252) !important;
}
</style>