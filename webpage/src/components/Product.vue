<template>
  <div class="operation-logs">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="产品名称">
          <el-input v-model="searchForm.name" clearable placeholder="请输入产品名称" style="width: 180px" />
        </el-form-item>
        <el-form-item label="产品类型">
          <el-select v-model="searchForm.type" clearable placeholder="全部产品类型" style="width: 180px">
            <el-option v-for="item in productTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部产品状态" style="width: 180px">
            <el-option v-for="item in productStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
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
        <div>
          <div class="section-title">产品管理</div>
          <div class="section-subtitle">维护产品基础资料，并为产品配置正式化验标准关系。</div>
        </div>
        <div class="table-toolbar-left">
          <el-button type="primary" @click="openCreate">新增产品</el-button>
          <el-button @click="exportExcel">导出 Excel</el-button>
        </div>
      </div>

      <el-table :data="productList" stripe border v-loading="loading">
        <el-table-column prop="productName" label="产品名称" min-width="180" />
        <el-table-column prop="productType" label="产品类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.productType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="产品状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === '成品' ? 'success' : 'warning'">{{ row.status || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="默认标准" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.defaultStandardName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="packagingMethod" label="包装方式" width="130" />
        <el-table-column prop="weightPerPiece" label="每件重量(kg)" width="130" />
        <el-table-column prop="piecesPerPallet" label="每板件数" width="110" />
        <el-table-column label="筛网" min-width="140">
          <template #default="{ row }">{{ meshMap[row.screenMeshId] || '-' }}</template>
        </el-table-column>
        <el-table-column label="可堆叠" width="100">
          <template #default="{ row }">
            <el-tag :type="row.canStack ? 'success' : 'info'">{{ row.canStack ? '可堆叠' : '不可堆叠' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openRelationDrawer(row)">标准关联</el-button>
            <el-button type="primary" link @click="editProduct(row)">编辑</el-button>
            <el-button type="danger" link @click="deleteProduct(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="operationType" width="680px" destroy-on-close>
      <el-form :model="productForm" label-width="110px">
        <div class="form-grid">
          <el-form-item label="产品名称" required>
            <el-input v-model="productForm.productName" clearable />
          </el-form-item>
          <el-form-item label="产品类型" required>
            <el-select v-model="productForm.productType">
              <el-option v-for="item in productTypes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="产品状态" required>
            <el-select v-model="productForm.status">
              <el-option v-for="item in productStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="包装方式">
            <el-input v-model="productForm.packagingMethod" clearable />
          </el-form-item>
          <el-form-item label="每件重量" required>
            <el-input v-model="productForm.weightPerPiece" clearable />
          </el-form-item>
          <el-form-item label="每板件数" required>
            <el-input v-model="productForm.piecesPerPallet" clearable />
          </el-form-item>
          <el-form-item label="筛网" required>
            <el-select v-model="productForm.screenMeshId" clearable placeholder="请选择筛网">
              <el-option v-for="item in meshList" :key="item.id" :label="item.meshName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="可堆叠">
            <el-switch v-model="productForm.canStack" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="operationType === '新增产品' ? newProduct() : updateProduct()">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="relationDrawerVisible" size="760px" destroy-on-close>
      <template #header>
        <div>
          <div class="section-title">产品标准关联</div>
          <div class="section-subtitle">{{ activeProduct?.productName || '-' }} / {{ activeProduct?.productType || '-' }}</div>
        </div>
      </template>

      <div class="relation-panel" v-loading="relationLoading">
        <div class="relation-summary" v-if="activeProduct">
          <div class="summary-item"><span>产品状态</span><strong>{{ activeProduct.status || '-' }}</strong></div>
          <div class="summary-item"><span>默认筛网</span><strong>{{ meshMap[activeProduct.screenMeshId] || '-' }}</strong></div>
          <div class="summary-item"><span>当前关联数</span><strong>{{ relationList.length }}</strong></div>
        </div>

        <el-card class="relation-form-card">
          <div class="form-card-head">
            <div>
              <div class="section-title">新增关联</div>
              <div class="section-subtitle">给当前产品绑定可用标准，并设置默认标准与优先级。</div>
            </div>
            <el-button type="primary" @click="submitRelation">绑定标准</el-button>
          </div>
          <div class="relation-form-grid">
            <el-select v-model="relationForm.qualityStandardId" filterable placeholder="选择化验标准">
              <el-option
                v-for="item in standardOptions"
                :key="item.id"
                :label="formatStandardOption(item)"
                :value="item.id"
              />
            </el-select>
            <el-input-number v-model="relationForm.priority" :min="1" :controls="false" placeholder="优先级" />
            <el-select v-model="relationForm.enabled" placeholder="状态">
              <el-option :value="true" label="启用" />
              <el-option :value="false" label="停用" />
            </el-select>
            <el-switch v-model="relationForm.isDefault" active-text="设为默认" />
          </div>
          <el-input v-model="relationForm.remark" placeholder="备注，例如：现场默认执行标准" />
        </el-card>

        <el-table :data="relationList" border>
          <el-table-column label="默认" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.isDefault" type="success">默认</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="standardName" label="标准名称" min-width="160" />
          <el-table-column prop="standardCode" label="标准编号" min-width="120" />
          <el-table-column prop="standardVersion" label="标准版本" width="90" />
          <el-table-column prop="priority" label="优先级" width="90" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="setDefaultRelation(row)" :disabled="row.isDefault">设为默认</el-button>
              <el-button type="danger" link @click="removeRelation(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import { addProduct, changeProduct, getProductList, removeProduct } from '@/api/product'
import { getMesh } from '@/api/mesh'
import { getStandard } from '@/api/standard'
import {
  bindProductStandardRelation,
  deleteProductStandardRelation,
  listProductStandardRelations,
  setDefaultProductStandardRelation
} from '@/api/productQualityStandard'

const searchForm = ref({ name: '', type: '', status: '' })
const productList = ref([])
const productTypes = [
  { value: '白冰糖', label: '白冰糖' },
  { value: '黄冰糖', label: '黄冰糖' }
]
const productStatusOptions = [
  { value: '半成品', label: '半成品' },
  { value: '成品', label: '成品' }
]
const meshList = ref([])
const meshMap = ref({})
const loading = ref(false)
const dialogVisible = ref(false)
const operationType = ref('新增产品')
const productForm = ref(createProductForm())
const relationDrawerVisible = ref(false)
const relationLoading = ref(false)
const activeProduct = ref(null)
const relationList = ref([])
const standardOptions = ref([])
const relationForm = ref(createRelationForm())

function createProductForm() {
  return {
    productName: '',
    productType: '',
    status: '',
    packagingMethod: '',
    weightPerPiece: '',
    piecesPerPallet: '',
    screenMeshId: null,
    canStack: false
  }
}

function createRelationForm() {
  return {
    qualityStandardId: undefined,
    priority: 1,
    enabled: true,
    isDefault: false,
    remark: ''
  }
}

function formatStandardOption(item) {
  const parts = [item.standardName, `v${item.version || 1}`]
  if (item.standardLevel) parts.push(item.standardLevel)
  return parts.join(' / ')
}

async function loadMeshList() {
  const res = await getMesh()
  if (res.code === 200) {
    meshList.value = res.data || []
    meshMap.value = meshList.value.reduce((result, item) => {
      result[item.id] = item.meshName
      return result
    }, {})
  }
}

async function handleSearch() {
  loading.value = true
  try {
    const res = await getProductList({
      name: searchForm.value.name || undefined,
      type: searchForm.value.type || undefined,
      status: searchForm.value.status || undefined
    })
    if (res.code === 200) {
      productList.value = res.data || []
    }
  } finally {
    loading.value = false
  }
}

function handleReset() {
  searchForm.value = { name: '', type: '', status: '' }
  handleSearch()
}

function openCreate() {
  operationType.value = '新增产品'
  productForm.value = createProductForm()
  dialogVisible.value = true
}

function editProduct(row) {
  operationType.value = '编辑产品'
  productForm.value = {
    productId: row.id,
    productName: row.productName,
    productType: row.productType,
    status: row.status,
    packagingMethod: row.packagingMethod,
    weightPerPiece: row.weightPerPiece,
    piecesPerPallet: row.piecesPerPallet,
    screenMeshId: row.screenMeshId ?? null,
    canStack: !!row.canStack
  }
  dialogVisible.value = true
}

function validateProductForm() {
  if (!productForm.value.productName || !productForm.value.productType || !productForm.value.status) {
    ElMessage.warning('请完整填写产品基础信息')
    return false
  }
  return true
}

async function newProduct() {
  if (!validateProductForm()) return
  const res = await addProduct(productForm.value)
  if (res.code === 200) {
    ElMessage.success('产品已新增')
    dialogVisible.value = false
    productForm.value = createProductForm()
    await handleSearch()
  }
}

async function updateProduct() {
  if (!validateProductForm()) return
  const res = await changeProduct(productForm.value)
  if (res.code === 200) {
    ElMessage.success('产品已更新')
    dialogVisible.value = false
    productForm.value = createProductForm()
    await handleSearch()
  }
}

async function deleteProduct(row) {
  await ElMessageBox.confirm(`确认删除产品“${row.productName}”吗？`, '删除产品', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning'
  })
  const res = await removeProduct(row.id)
  if (res.code === 200) {
    ElMessage.success('产品已删除')
    await handleSearch()
  }
}

async function exportExcel() {
  const res = await getProductList({
    name: searchForm.value.name || undefined,
    type: searchForm.value.type || undefined,
    status: searchForm.value.status || undefined
  })
  if (res.code !== 200) return
  const rows = (res.data || []).map(item => ({
    产品名称: item.productName,
    产品类型: item.productType,
    产品状态: item.status,
    默认标准: item.defaultStandardName || '',
    包装方式: item.packagingMethod,
    每件重量kg: item.weightPerPiece,
    每板件数: item.piecesPerPallet,
    筛网: meshMap.value[item.screenMeshId] || '',
    可堆叠: item.canStack ? '是' : '否'
  }))
  const worksheet = XLSX.utils.json_to_sheet(rows)
  const workbook = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(workbook, worksheet, '产品列表')
  XLSX.writeFile(workbook, `产品列表_${new Date().toISOString().slice(0, 10)}.xlsx`)
  ElMessage.success('导出成功')
}

async function openRelationDrawer(row) {
  activeProduct.value = row
  relationDrawerVisible.value = true
  relationForm.value = createRelationForm()
  await Promise.all([loadRelationList(row.id), loadStandardOptions(row.productType)])
}

async function loadRelationList(productId) {
  relationLoading.value = true
  try {
    const res = await listProductStandardRelations(productId)
    relationList.value = res.data || []
  } finally {
    relationLoading.value = false
  }
}

async function loadStandardOptions(productType) {
  let res = await getStandard({
    productType: productType || undefined,
    status: 'ENABLED'
  })
  standardOptions.value = res.data || []
  if (standardOptions.value.length) {
    return
  }

  res = await getStandard({ status: 'ENABLED' })
  standardOptions.value = res.data || []
  if (standardOptions.value.length) {
    return
  }

  res = await getStandard({})
  standardOptions.value = res.data || []
}

async function submitRelation() {
  if (!activeProduct.value || !relationForm.value.qualityStandardId) {
    ElMessage.warning('请选择要绑定的标准')
    return
  }
  await bindProductStandardRelation({
    productId: activeProduct.value.id,
    qualityStandardId: relationForm.value.qualityStandardId,
    priority: relationForm.value.priority,
    enabled: relationForm.value.enabled,
    isDefault: relationForm.value.isDefault,
    remark: relationForm.value.remark || undefined
  })
  ElMessage.success('标准关联已保存')
  relationForm.value = createRelationForm()
  await loadRelationList(activeProduct.value.id)
}

async function setDefaultRelation(row) {
  await setDefaultProductStandardRelation(row.id)
  ElMessage.success('默认标准已更新')
  await loadRelationList(activeProduct.value.id)
}

async function removeRelation(row) {
  await ElMessageBox.confirm(`确认删除标准“${row.standardName}”的关联关系吗？`, '删除关联', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await deleteProductStandardRelation(row.id)
  ElMessage.success('关联已删除')
  await loadRelationList(activeProduct.value.id)
}

onMounted(async () => {
  await loadMeshList()
  await handleSearch()
})
</script>

<style scoped>
.operation-logs {
  padding: 20px;
}

.search-card,
.table-card,
.relation-form-card {
  background: var(--app-panel);
}

.search-card {
  margin-bottom: 20px;
}

.table-toolbar,
.form-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.table-toolbar-left {
  display: flex;
  gap: 8px;
}

.section-title {
  color: var(--app-text);
  font-size: 16px;
  font-weight: 700;
}

.section-subtitle {
  margin-top: 4px;
  color: var(--app-text-tertiary);
  font-size: 13px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
}

.relation-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.relation-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.summary-item {
  padding: 14px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #fbfcff;
}

.summary-item span {
  display: block;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.summary-item strong {
  display: block;
  margin-top: 6px;
  color: var(--app-text);
  font-size: 14px;
}

.relation-form-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

:deep(.el-input),
:deep(.el-select),
:deep(.el-input-number) {
  width: 100%;
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
