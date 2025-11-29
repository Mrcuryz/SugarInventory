<template>
  <div class="auto-inbound-page">
    <!-- 解析区 -->
    <el-card class="search-card" style="max-width: 1200px">
      <div class="card-title">自动入库解析</div>

      <el-form :model="parseForm" label-width="90px" class="parse-form">
        <el-form-item label="入库日期">
          <el-date-picker
              v-model="parseForm.entryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 180px"
          />
        </el-form-item>

        <el-form-item label="产品类型">
          <el-radio-group v-model="parseForm.parseType">
            <el-radio label="SEMI_PRODUCT">半成品</el-radio>
            <el-radio label="FINISHED_PRODUCT">成品</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="原始报数">
          <el-input
              v-model="parseForm.rawText"
              type="textarea"
              :rows="8"
              placeholder="直接粘贴报数文本"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loadingParse" @click="handleParse">
            解析报数
          </el-button>
          <el-button @click="handleResetParse">清空</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 任务列表区 -->
    <el-card class="table-card" style="max-width: 1200px" v-if="taskList.length || batchId">
      <div class="table-header">
        <div class="left">
          <span>当前批次：</span>
          <el-tag v-if="batchId" type="info" effect="plain">{{ batchId }}</el-tag>
          <el-button
              v-if="batchId"
              size="small"
              type="primary"
              link
              @click="handleReloadBatch"
          >
            重新加载
          </el-button>
        </div>
        <div class="right">
          <el-form :inline="true" :model="filterForm" class="filter-form">
            <el-form-item label="类型">
              <el-select v-model="filterForm.type" size="small" style="width: 120px">
                <el-option label="全部" value="ALL" />
                <el-option label="半成品" value="SEMI_PRODUCT" />
                <el-option label="成品" value="FINISHED_PRODUCT" />
              </el-select>
            </el-form-item>
            <el-form-item label="风险">
              <el-select v-model="filterForm.risk" size="small" style="width: 120px">
                <el-option label="全部" value="ALL" />
                <el-option label="高" value="RED" />
                <el-option label="中" value="YELLOW" />
                <el-option label="低" value="GREEN" />
              </el-select>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <el-table
          :data="filteredTasks"
          border
          stripe
          style="width: 100%"
          v-loading="loadingBatch"
          @selection-change="handleSelectionChange"
          height="480"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div v-if="row.type === 'FINISHED_PRODUCT' && row.suggestedSemiRecords && row.suggestedSemiRecords.length">
              <div class="semi-title">关联半成品记录</div>
              <el-table
                  :data="row.suggestedSemiRecords"
                  size="small"
                  border
                  style="width: 100%; margin-bottom: 8px"
              >
                <el-table-column prop="productName" label="半成品" min-width="100" />

                <el-table-column label="生产日期" width="140">
                  <template #default="{ row: semi }">
                    <el-date-picker
                        v-model="semi.productionDate"
                        type="date"
                        value-format="YYYY-MM-DD"
                        placeholder="日期"
                        size="small"
                        style="width: 120px"
                    />
                  </template>
                </el-table-column>

                <el-table-column label="库位" min-width="100">
                  <template #default="{ row: semi }">
                    <el-input
                        v-model="semi.warehouseName"
                        size="small"
                        placeholder="库位名称"
                        clearable
                    />
                  </template>
                </el-table-column>

                <el-table-column label="数量" width="300">
                  <template #default="{ row: semi }">
                    <el-input-number
                        v-model="semi.quantity"
                        :min="0"
                        size="small"
                        style="width: 90px"
                    />
                    <el-select
                        v-model="semi.unit"
                        size="small"
                        style="width: 70px; margin-left: 6px"
                    >
                      <el-option label="板" value="0" />
                      <el-option label="件" value="1" />
                    </el-select>
                  </template>
                </el-table-column>

                <el-table-column label="套用化验" width="110">
                  <template #default="{ row: semi }">
                    <el-switch
                        v-model="semi.useAssay"
                        :active-value="true"
                        :inactive-value="false"
                        size="small"
                    />
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div v-else class="no-semi-info">
              当前任务无关联半成品记录
            </div>
          </template>
        </el-table-column>

        <el-table-column type="selection" width="35" />

        <el-table-column prop="rawBlock" label="原始文本" min-width="200">
          <template #default="{ row }">
            <el-tooltip effect="dark" :content="row.rawBlock" placement="top">
              <span class="ellipsis-text">{{ row.rawBlock }}</span>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column label="日期" width="150">
          <template #default="{ row }">
            <el-date-picker
                v-model="row.entryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="日期"
                size="small"
                style="width: 120px"
            />
          </template>
        </el-table-column>

        <el-table-column label="产品" min-width="150">
          <template #default="{ row }">
            <el-cascader
                v-model="row._productId"
                :options="row.type === 'SEMI_PRODUCT' ? semiProductOptions : finishedProductOptions"
                :props="cascaderProps"
                clearable
                filterable
                size="small"
                controls-position="right"
                :show-all-levels="false"
                placeholder="选择产品"
                @change="(val) => handleProductChange(row, val)"
            />
          </template>
        </el-table-column>


        <el-table-column label="库位" min-width="100">
          <template #default="{ row }">
            <!-- 简单版：直接编辑库位名称，后端用名称匹配仓库 -->
            <el-input
                v-if="row.type === 'SEMI_PRODUCT'"
                v-model="row.semiWarehouseName"
                size="small"
                placeholder="输入库位"
                clearable
                style="width: 75px"
            />
            <el-input
                v-else
                v-model="row.warehouseName"
                size="small"
                placeholder="输入库位"
                clearable
                style="width: 75px"
            />
          </template>
        </el-table-column>

        <el-table-column label="板数" width="85">
          <template #default="{ row }">
            <el-input-number
                v-if="row.type === 'SEMI_PRODUCT'"
                v-model="row.semiBoardQuantity"
                :min="0"
                size="small"
                controls-position="right"
                style="width: 60px"
            />
            <el-input-number
                v-else
                v-model="row.finishedBoardQuantity"
                :min="0"
                size="small"
                controls-position="right"
                style="width: 60px"
            />
          </template>
        </el-table-column>

        <el-table-column label="件数" width="85">
          <template #default="{ row }">
            <el-input-number
                v-if="row.type === 'SEMI_PRODUCT'"
                v-model="row.semiPieceQuantity"
                :min="0"
                size="small"
                controls-position="right"
                style="width: 60px"
            />
            <el-input-number
                v-else
                v-model="row.finishedPieceQuantity"
                :min="0"
                size="small"
                controls-position="right"
                style="width: 60px"
            />
          </template>
        </el-table-column>

        <el-table-column prop="riskLevel" label="风险" width="60">
          <template #default="{ row }">
            <el-tag size="small" :type="riskLevelTagType(row.riskLevel)">
              {{ riskLevelLabel(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="riskReason" label="风险原因" min-width="120">
          <template #default="{ row }">
            <el-tooltip effect="dark" :content="row.riskReason" placement="top">
              <span class="ellipsis-text">{{ row.riskReason || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column prop="hasAssay" label="化验" width="60">
          <template #default="{ row }">
            <el-tag
                size="small"
                :type="row.hasAssay ? 'success' : 'danger'"
            >
              {{ row.hasAssay ? '有' : '无' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="canAutoStockIn" label="可入库" width="70">
          <template #default="{ row }">
            <el-tag
                size="small"
                :type="row.canAutoStockIn ? 'success' : 'info'"
            >
              {{ row.canAutoStockIn ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="remark" label="备注" min-width="180">
          <template #default="{ row }">
            <el-tooltip effect="dark" :content="row.remark" placement="top">
              <span class="ellipsis-text">{{ row.remark || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <div class="left">
          已选择 <b>{{ selectedTaskIds.length }}</b> 条任务
        </div>
        <div class="right">
          <el-button
              type="primary"
              :loading="loadingConfirm"
              :disabled="!selectedTaskIds.length"
              @click="handleConfirm"
          >
            确认入库
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card class="table-card" style="max-width: 1200px" v-if="taskList.length || batchId">
      <!-- 全局备注展示 -->
      <el-alert
          v-if="globalRemarks.length"
          type="info"
          show-icon
          class="global-remark-alert"
          title="备注"
      >
        <template #default>
          <div v-for="(r, idx) in globalRemarks" :key="idx">
            {{ idx + 1 }}. {{ r }}
          </div>
        </template>
      </el-alert>

      <div class="table-header">
        <!-- 原来的当前批次 / 筛选条件 -->
      </div>

      <!-- el-table ... -->
    </el-card>

  </div>
</template>

<script setup>
import { getSemiProduct, getStProduct } from '@/api/assay'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTokenStore } from '@/stores/token'
import { parseAutoInbound, getAutoInboundBatch, confirmAutoInbound } from '@/api/autoInbound'

// ---------- 工具：从 JWT 里解析出当前用户ID（sub） ----------
function decodeJwtSub(token) {
  if (!token) return null
  try {
    const parts = token.split('.')
    if (parts.length < 2) return null
    let payload = parts[1]
    // base64url -> base64
    payload = payload.replace(/-/g, '+').replace(/_/g, '/')
    while (payload.length % 4 !== 0) {
      payload += '='
    }
    const json = JSON.parse(atob(payload))
    return json.sub ? parseInt(json.sub, 10) : null
  } catch (e) {
    console.error('解析 JWT 失败', e)
    return null
  }
}

// ---------- 表单 & 状态 ----------
const today = new Date().toISOString().slice(0, 10)

const parseForm = ref({
  entryDate: today,
  parseType: 'SEMI_PRODUCT',
  rawText: ''
})

const batchId = ref('')
const taskList = ref([])
const globalRemarks = ref([])

const loadingParse = ref(false)
const loadingBatch = ref(false)
const loadingConfirm = ref(false)

const filterForm = ref({
  type: 'ALL',
  risk: 'ALL'
})

const selectedTaskIds = ref([])

// 当前用户 ID（操作员）
const tokenStore = useTokenStore()
const operatorId = ref(decodeJwtSub(tokenStore.token))

// ---------- 计算属性：过滤后的任务列表 ----------
const filteredTasks = computed(() => {
  return taskList.value.filter((t) => {
    if (filterForm.value.type !== 'ALL' && t.type !== filterForm.value.type) {
      return false
    }
    if (filterForm.value.risk !== 'ALL' && t.riskLevel !== filterForm.value.risk) {
      return false
    }
    return true
  })
})

// -------- 产品级联选择数据（复用手工入库页的结构） --------
const semiProductList = ref([])
const stProductList = ref([])

// 和手工入库页一致的 cascader props
const cascaderProps = {
  emitPath: false,
  label: 'label',
  value: 'value',
  children: 'children',
  expandTrigger: 'hover'
}

// 合并成品 + 半成品，构建三级结构：
// 第一级：成品 / 半成品
// 第二级：productType
// 第三级：具体产品（productId, productName）
const productOptions = computed(() => {
  const combined = [
    ...stProductList.value.map(p => ({ ...p, category: '成品' })),
    ...semiProductList.value.map(p => ({ ...p, category: '半成品' }))
  ]

  const categoryMap = {}

  combined.forEach(p => {
    const catKey = p.category
    if (!categoryMap[catKey]) {
      categoryMap[catKey] = {
        value: catKey,
        label: catKey,
        children: {}
      }
    }
    const catNode = categoryMap[catKey]

    const typeKey = p.productType || '未分类'
    if (!catNode.children[typeKey]) {
      catNode.children[typeKey] = {
        value: typeKey,
        label: typeKey,
        children: []
      }
    }
    const typeNode = catNode.children[typeKey]

    typeNode.children.push({
      value: p.productId,
      label: p.productName
    })
  })

  return Object.values(categoryMap).map(cat => ({
    ...cat,
    children: Object.values(cat.children)
  }))
})

// 针对半成品/成品任务做过滤
const semiProductOptions = computed(() =>
    productOptions.value.filter(o => o.label === '半成品')
)

const finishedProductOptions = computed(() =>
    productOptions.value.filter(o => o.label === '成品')
)

const loadProductOptions = async () => {
  try {
    const [semiRes, stRes] = await Promise.all([
      getSemiProduct(),
      getStProduct()
    ])
    if (semiRes.code === 200) {
      semiProductList.value = semiRes.data || []
    }
    if (stRes.code === 200) {
      stProductList.value = stRes.data || []
    }
  } catch (e) {
    console.error('加载产品列表失败', e)
  }
}

// ---------- 风险等级 -> Tag 类型 ----------
const riskLevelTagType = (level) => {
  if (level === 'RED') return 'danger'
  if (level === 'YELLOW') return 'warning'
  if (level === 'GREEN') return 'success'
  return 'info'
}

const riskLevelLabel = (level) => {
  if (level === 'RED') return '高'
  if (level === 'YELLOW') return '中'
  if (level === 'GREEN') return '低'
  return '-'
}

// 根据 productId 在级联数据中找到产品名
const findProductLabelById = (id) => {
  if (!id || !productOptions.value.length) return null

  const dfs = (nodes) => {
    for (const n of nodes) {
      if (n.value === id) return n.label
      if (n.children && n.children.length) {
        const r = dfs(n.children)
        if (r) return r
      }
    }
    return null
  }

  return dfs(productOptions.value)
}

// 级联选择变化时，同步写回任务对象里的 productId / semiProductId & 名称
const handleProductChange = (row, value) => {
  const label = findProductLabelById(value) || ''

  if (row.type === 'SEMI_PRODUCT') {
    row.semiProductId = value
    row.semiProductName = label
  } else {
    row.productId = value
    row.productName = label
  }
}

// ---------- 解析报数 ----------
const handleParse = async () => {
  if (!parseForm.value.rawText || !parseForm.value.rawText.trim()) {
    ElMessage.warning('请先粘贴原始报数文本')
    return
  }

  // 确保日期不为空
  if (!parseForm.value.entryDate) {
    parseForm.value.entryDate = today
  }

  const payload = {
    rawText: parseForm.value.rawText,
    entryDate: parseForm.value.entryDate,
    parseType: parseForm.value.parseType
  }

  loadingParse.value = true
  try {
    const res = await parseAutoInbound(payload)
    batchId.value = res.data.batchId
    taskList.value = res.data.tasks || []
    globalRemarks.value = res.data.globalRemarks || []

    taskList.value.forEach(t => {
      t._productId = t.type === 'SEMI_PRODUCT' ? t.semiProductId : t.productId
    })

    selectedTaskIds.value = []
    ElMessage.success('解析成功')
  } catch (e) {
    console.error(e)
  } finally {
    loadingParse.value = false
  }
}

// ---------- 从批次重新加载 ----------
const handleReloadBatch = async () => {
  if (!batchId.value) return
  loadingBatch.value = true
  try {
    const res = await getAutoInboundBatch(batchId.value)
    taskList.value = res.data.tasks || []
    selectedTaskIds.value = []
    ElMessage.success('已从服务器重新加载该批次')
  } catch (e) {
    console.error(e)
  } finally {
    loadingBatch.value = false
  }
}

// ---------- 清空解析表单 ----------
const handleResetParse = () => {
  parseForm.value = {
    entryDate: today,
    parseType: 'SEMI_PRODUCT',
    rawText: ''
  }
  batchId.value = ''
  taskList.value = []
  taskList.value.forEach(t => {
    t._productId = t.type === 'SEMI_PRODUCT' ? t.semiProductId : t.productId
  })
  selectedTaskIds.value = []
}

// ---------- 表格勾选 ----------
const handleSelectionChange = (rows) => {
  selectedTaskIds.value = rows.map((r) => r.taskId)
}

// ---------- 确认入库 ----------
const handleConfirm = async () => {
  if (!selectedTaskIds.value.length) {
    ElMessage.warning('请至少选择一条任务')
    return
  }

  if (!operatorId.value) {
    ElMessage.error('无法获取当前用户ID，请重新登录后再试')
    return
  }

  try {
    await ElMessageBox.confirm(
        `确认对选中的 ${selectedTaskIds.value.length} 条任务执行入库操作？`,
        '确认入库',
        {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )
  } catch {
    ElMessage.info('已取消入库操作')
    return
  }

  // 这里为了简单：把【被勾选的任务】作为 updatedTasks 发给后端
  const updatedTasks = taskList.value.filter((t) =>
      selectedTaskIds.value.includes(t.taskId)
  )

  const payload = {
    operatorId: operatorId.value,
    confirmedTaskIds: selectedTaskIds.value,
    updatedTasks
  }

  loadingConfirm.value = true
  try {
    await confirmAutoInbound(batchId.value, payload)
    ElMessage.success('入库成功')

    // 入库成功后，可以选择：
    // 1. 清空这批任务
    // 2. 或者重新拉取看看还有未入库的（目前后端是直接删 redis，这里就清空）
    batchId.value = ''
    taskList.value = []
    selectedTaskIds.value = []
  } catch (e) {
    console.error(e)
  } finally {
    loadingConfirm.value = false
  }
}

onMounted(() => {
  loadProductOptions()
  // 如果你打算支持“从其它页面跳转并带 batchId”的场景，可以在这里处理 route.query
})
</script>

<style scoped>
.auto-inbound-page {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
  background: #ffffff;
}

.table-card {
  background: rgba(255, 255, 255, 0.9);
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}

.parse-form {
  margin-top: 10px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.table-header .left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-form .el-form-item {
  margin-bottom: 0;
}

.ellipsis-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-footer {
  margin-top: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.global-remark-alert {
  margin-bottom: 10px;
}

</style>
