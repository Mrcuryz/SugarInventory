<template>
  <div class="operation-logs">
    <el-card class="search-card" style="max-width: 1200px">
      <el-form :model="searchForm" inline>
        <el-form-item label="筛网名称">
          <el-input
              v-model="searchForm.meshName"
              placeholder="请输入筛网名称"
              clearable
              style="width: 150px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" style="max-width: 1200px">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <el-button type="primary" @click="dialogVisible = true;operationType='新增筛网'">新增</el-button>
        </div>
      </div>
      <el-table
          :data="resultList"
          style="width: 95%"
          heigth="300"
          stripe
          border
          v-loading="loading"
      >
        <el-table-column prop="meshName" label="筛网名称" width="200"/>
        <el-table-column prop="description" label="描述" width="352"/>
        <el-table-column prop="createdAt" label="创建时间" width="200" sortable/>
        <el-table-column prop="updatedAt" label="更新时间" width="200" sortable/>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="primary" size="small"
                       @click="dialogVisible = true;operationType='修改筛网';handleEdit(row)">编辑
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
        :title=operationType
        v-model="dialogVisible"
        width="40%"
        :before-close="handleClose"
    >
      <el-form :model="submitForm" :rules="rule" label-width="auto">
        <el-form-item label="筛网名称" prop="meshName" required>
          <el-input v-model="submitForm.meshName" clearable/>
        </el-form-item>
        <el-form-item label="筛网描述" prop="description">
          <el-input v-model="submitForm.description" clearable/>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="operationType === '新增筛网' ? handleNew() : handleUpdate()">确定</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import {ref, onMounted} from 'vue'
import {addMesh, deleteMesh, getMesh, updateMesh} from '@/api/mesh'
import {ElMessage, ElMessageBox} from 'element-plus'
// 搜索表单
const searchForm = ref({
  meshName: '',
})
// 筛网列表
const resultList = ref([])

// 加载状态
const loading = ref(false)

// 处理搜索
const handleSearch = async () => {
  let params = {}
  if (searchForm.value.meshName) {
    params.meshName = searchForm.value.meshName
  }
  loading.value = true
  let res = await getMesh(params)
  if (res.code === 200) {
    resultList.value = res.data
    resultList.value.forEach(item => {
      if (item.createdAt) {
        item.createdAt = item.createdAt.replace('T', ' ').replace('Z', ' ')
      }
      if (item.updatedAt) {
        item.updatedAt = item.updatedAt.replace('T', ' ').replace('Z', ' ')
      }
    })
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

const rule = {
  meshName: [
    {required: true, message: '请输入筛网名称', trigger: 'blur'}
  ]
}
const dialogVisible = ref(false)
const handleClose = () => {
  submitForm.value = {
    meshName: '',
    description: ''
  }
  dialogVisible.value = false
}
const operationType = ref('')
const submitForm = ref({
  meshName: '',
  description: ''
})

// 新增筛网
const handleNew = async () => {
  // 校验表单
  if (submitForm.value.meshName === '') {
    ElMessage.error('请输入筛网名称')
    return
  }
  let res = await addMesh(submitForm.value)
  if (res.code === 200) {
    ElMessage.success('新增成功')
    await handleSearch()
    dialogVisible.value = false
    submitForm.value = {
      meshName: '',
      description: ''
    }
  } else {
    ElMessage.error(res.msg)
  }
}

// 删除筛网
const handleDelete = async (row) => {
  await ElMessageBox.confirm(
      '你确认要删除这条信息吗?',
      '温馨提示',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning',
      }
  )
      .then(async () => {
        //调用接口
        let res = await deleteMesh(row.id)
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

// 编辑库位
const handleEdit = (row) => {
  operationType.value = '修改库位'
  submitForm.value = {
    id: row.id,
    meshName: row.meshName,
    description: row.description
  }
  dialogVisible.value = true
}

const handleUpdate = async () => {
  // 校验表单
  if (submitForm.value.meshName === '') {
    ElMessage.error('请输入筛网名称')
    return
  }
  let res = await updateMesh(submitForm.value)
  if (res.code === 200) {
    ElMessage.success('修改成功')
    await handleSearch()
    dialogVisible.value = false
    submitForm.value = {
      meshName: '',
      description: ''
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
  background: var(--app-panel);
}

.table-card {
  background: var(--app-panel);
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
  --el-table-border-color: var(--app-border-soft);
  --el-table-header-bg-color: #f7f8fb;
  --el-table-row-hover-bg-color: var(--app-hover);
}

:deep(.el-table__header th) {
  background-color: #f7f8fb;
  color: var(--app-text-secondary);
}

:deep(.el-table__body tr:hover > td) {
  background-color: var(--app-hover) !important;
}
</style>
