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
        <el-table-column prop="meshName" label="筛网名称" width="180"/>
        <el-table-column prop="description" label="描述" min-width="220"/>
        <el-table-column label="关联产品" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <div v-if="getRelatedProducts(row.id).length" class="related-products">
              <el-tag
                  v-for="product in getRelatedProducts(row.id)"
                  :key="product.id"
                  size="small"
                  type="primary"
              >
                {{ product.productName }}
              </el-tag>
            </div>
            <el-text v-else type="info">未关联</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="200" sortable/>
        <el-table-column prop="updatedAt" label="更新时间" width="200" sortable/>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small"
                       @click="dialogVisible = true;operationType='修改筛网';handleEdit(row)">编辑
            </el-button>
            <el-button type="success" size="small" @click="openAssociateDialog(row)">新增关联产品</el-button>
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

    <el-dialog
        v-model="associateDialogVisible"
        :title="`新增关联产品 - ${associateMesh?.meshName || ''}`"
        width="520px"
        destroy-on-close
    >
      <el-form label-width="96px">
        <el-form-item label="当前筛网">
          <el-input :model-value="associateMesh?.meshName || ''" disabled/>
        </el-form-item>
        <el-form-item label="关联产品">
          <el-select
              v-model="associateProductIds"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              placeholder="请选择需要绑定到该筛网的产品"
              style="width: 100%"
          >
            <el-option
                v-for="product in associateProductOptions"
                :key="product.id"
                :label="product.label"
                :value="product.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="associateDialogVisible = false">取消</el-button>
        <el-button
            type="primary"
            :loading="associateSubmitting"
            @click="handleAssociateProducts"
        >
          确定绑定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {computed, ref, onMounted} from 'vue'
import {addMesh, deleteMesh, getMesh, updateMesh} from '@/api/mesh'
import {changeProduct, getProductList} from '@/api/product'
import {ElMessage, ElMessageBox} from 'element-plus'
// 搜索表单
const searchForm = ref({
  meshName: '',
})
// 筛网列表
const resultList = ref([])
const productList = ref([])

// 加载状态
const loading = ref(false)

// 处理搜索
const handleSearch = async () => {
  let params = {}
  if (searchForm.value.meshName) {
    params.meshName = searchForm.value.meshName
  }
  loading.value = true
  try {
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
    } else {
      ElMessage.error(res.msg)
    }
  } finally {
    loading.value = false
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

const loadProducts = async () => {
  const res = await getProductList()
  if (res.code === 200) {
    productList.value = res.data || []
  } else {
    ElMessage.error(res.msg || '获取产品列表失败')
  }
}

const getRelatedProducts = (meshId) => {
  return productList.value.filter(product => product.screenMeshId === meshId)
}

const associateDialogVisible = ref(false)
const associateMesh = ref(null)
const associateProductIds = ref([])
const associateSubmitting = ref(false)

const associateProductOptions = computed(() => {
  const currentMeshId = associateMesh.value?.id
  return productList.value
      .filter(product => product.screenMeshId !== currentMeshId)
      .map(product => ({
        ...product,
        label: `${product.productName}（${product.status || '未知状态'}${product.screenMeshId ? '，已绑定其他筛网' : '，未绑定筛网'}）`
      }))
})

const openAssociateDialog = (row) => {
  associateMesh.value = row
  associateProductIds.value = []
  associateDialogVisible.value = true
}

const handleAssociateProducts = async () => {
  if (!associateMesh.value?.id) {
    ElMessage.error('请先选择筛网')
    return
  }
  if (associateProductIds.value.length === 0) {
    ElMessage.error('请至少选择一个产品')
    return
  }
  associateSubmitting.value = true
  try {
    const results = await Promise.all(associateProductIds.value.map(productId => changeProduct({
      productId,
      screenMeshId: associateMesh.value.id
    })))
    const failed = results.find(res => res.code !== 200)
    if (failed) {
      throw new Error(failed.msg || '关联产品失败')
    }
    ElMessage.success('关联产品成功')
    associateDialogVisible.value = false
    await loadProducts()
  } catch (error) {
    ElMessage.error(error?.message || '关联产品失败')
  } finally {
    associateSubmitting.value = false
  }
}

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

onMounted(async () => {
  await Promise.all([
    handleSearch(),
    loadProducts()
  ])
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

.related-products {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
