<template>
  <div class="operation-logs">
    <el-card class="search-card" style="max-width: 1200px">
      <el-form :model="searchForm" inline>
        <el-form-item label="库位名称">
          <el-input
              v-model="searchForm.name"
              placeholder="请输入库位名称"
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
          <el-button type="primary" @click="dialogVisible = true;operationType='新增库位'">新增</el-button>
        </div>
      </div>
      <el-table
          :data="warehouseList"
          style="width: 95%"
          heigth="300"
          stripe
          border
          v-loading="loading"
      >
        <el-table-column prop="warehouseName" label="库位名称" width="200" sortable/>
        <el-table-column prop="status" label="库位状态" width="200">
          <template #default="{ row }">
            <el-tag :type="row.status === '正常' ? 'success' : 'warning'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="maxCapacity" label="最大容量" width="150" sortable/>
        <el-table-column prop="curCapacity" label="当前容量" width="150" sortable/>
        <el-table-column prop="maxRows" label="最大行数" min-width="150"/>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button type="primary" size="small"
                       @click="dialogVisible = true;operationType='修改库位';editWarehouse(row)">编辑
            </el-button>
            <el-button type="danger" size="small" @click="deleteWarehouse(row)">删除</el-button>
            <el-button type="info" size="small"
                       @click="dialogVisible = true;operationType='修改状态';editWarehouse(row)">修改状态
            </el-button>
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
      <el-form :model="warehouseForm" :rules="rule" label-width="auto">
        <el-form-item label="库位名称" prop="warehouseName" v-if="operationType !== '修改状态'" required>
          <el-input v-model="warehouseForm.warehouseName" clearable/>
        </el-form-item>
        <el-form-item label="最大行数" prop="maxRows" v-if="operationType !== '修改状态'" required>
          <el-input v-model="warehouseForm.maxRows" clearable/>
        </el-form-item>
        <el-form-item v-if="operationType === '修改状态'" label="正常" prop="status" required>
          <el-switch
              v-model="warehouseForm.status"
              active-value="维护"
              inactive-value="取消维护"
              active-text="维护"
              clearable
          />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer" fixed="right">
        <el-button type="primary" @click="operationType === '新增库位' ? newWarehouse() : updateWarehouse()">确定
        </el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import {ref, onMounted} from 'vue'
import {getWarehouse, addWarehouse, removeWarehouse, changeWarehouse, changeWarehouseStatus} from '@/api/warehouse'
import {ElMessage, ElMessageBox} from 'element-plus'
// 搜索表单
const searchForm = ref({
  name: '',
})
// 库位列表
const warehouseList = ref([])

// 加载状态
const loading = ref(false)

// 处理搜索
const handleSearch = async () => {
  let params = {}
  if (searchForm.value.name) {
    params.name = searchForm.value.name
  }
  loading.value = true
  let res = await getWarehouse(params)
  if (res.code === 200) {
    warehouseList.value = res.data
    loading.value = false
  } else {
    ElMessage.error(res.msg)
  }
}
// 处理重置
const handleReset = () => {
  searchForm.value = {
    name: ''
  }
  handleSearch()
}

const rule = {
  warehouseName: [
    {required: true, message: '请输入库位名称', trigger: 'blur'}
  ],
  maxRows: [
    {required: true, message: '请输入最大行数', trigger: 'blur'},
    {type: 'string', message: '请输入整数', trigger: 'blur', pattern: /^\d+$/}
  ]
}
const dialogVisible = ref(false)
const handleClose = () => {
  warehouseForm.value = {
    warehouseName: '',
    maxRows: ''
  }
  dialogVisible.value = false
}
const operationType = ref('')
const warehouseForm = ref({
  warehouseName: '',
  maxRows: ''
})

// 新增库位
const newWarehouse = async () => {
  // 校验表单
  if (warehouseForm.value.warehouseName === '') {
    ElMessage.error('请输入库位名称')
    return
  }
  if (warehouseForm.value.maxRows === '') {
    ElMessage.error('请输入最大行数')
    return
  }
  let res = await addWarehouse(warehouseForm.value)
  if (res.code === 200) {
    ElMessage.success('新增成功')
    await handleSearch()
    dialogVisible.value = false
    warehouseForm.value = {
      warehouseName: '',
      maxRows: ''
    }
  } else {
    ElMessage.error(res.msg)
  }
}

// 删除库位
const deleteWarehouse = async (row) => {
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
        let res = await removeWarehouse(row.id)
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
const editWarehouse = (row) => {
  warehouseForm.value = {
    id: row.id,
    warehouseName: row.warehouseName,
    status: row.status,
    maxRows: row.maxRows
  }
  dialogVisible.value = true
}

const updateWarehouse = async () => {
  // 校验表单
  if (warehouseForm.value.warehouseName === '') {
    ElMessage.error('请输入库位名称')
    return
  }
  if (warehouseForm.value.maxRows === '') {
    ElMessage.error('请输入最大行数')
    return
  }
  if (warehouseForm.value.status === '') {
    ElMessage.error('请选择状态')
    return
  }
  let res
  if (operationType.value === '修改状态') {
    res = await changeWarehouseStatus(warehouseForm.value.id)
    if (res.code !== 200) {
      ElMessage.error(res.msg)
    }
  } else {
    res = await changeWarehouse(warehouseForm.value)
  }
  if (res.code === 200) {
    ElMessage.success('修改成功')
    await handleSearch()
    dialogVisible.value = false
    warehouseForm.value = {
      warehouseName: '',
      maxRows: ''
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
