<template>
  <div class="boiling-page">
    <el-card class="search-card">
      <div class="page-head">
        <div>
          <div class="section-title">煮糖批次</div>
          <div class="section-subtitle">记录锅数、桶数和重量，供生产订单引用并预占余量。</div>
        </div>
        <el-button type="primary" @click="openCreate">新建煮糖批次</el-button>
      </div>
      <el-form :model="query" inline class="search-form">
        <el-form-item label="批次号">
          <el-input v-model="query.batchNo" clearable placeholder="输入批次号" style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="煮糖日期">
          <el-date-picker v-model="query.dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" style="width: 240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList(1)">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table :data="rows" border stripe v-loading="loading" empty-text="暂无煮糖批次">
        <el-table-column prop="batchNo" label="批次号" min-width="155" />
        <el-table-column prop="boilingDate" label="煮糖日期" width="115" />
        <el-table-column prop="teamName" label="班组" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.teamName || '暂无' }}</template>
        </el-table-column>
        <el-table-column label="糖类型" min-width="115" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sugarType || row.productName || '暂无' }}</template>
        </el-table-column>
        <el-table-column label="总量" min-width="150">
          <template #default="{ row }">{{ numberText(row.bucketCount) }}桶 / {{ numberText(row.totalWeightKg) }}kg</template>
        </el-table-column>
        <el-table-column label="占用/消耗" min-width="155">
          <template #default="{ row }">
            <div>预占：{{ numberText(row.reservedBucketCount) }}桶</div>
            <div class="muted">消耗：{{ numberText(row.consumedBucketCount) }}桶</div>
          </template>
        </el-table-column>
        <el-table-column label="剩余" min-width="145">
          <template #default="{ row }">{{ numberText(row.remainingBucketCount) }}桶 / {{ numberText(row.remainingWeightKg) }}kg</template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="left">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status !== 'CANCELED'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'AVAILABLE'" link type="danger" @click="cancelRow(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total" :current-page="page" :page-size="size" :page-sizes="[10, 20, 50]" @size-change="handleSize" @current-change="loadList" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑煮糖批次' : '新建煮糖批次'" width="660px" destroy-on-close>
      <el-form :model="form" label-width="120px" class="boiling-form">
        <el-form-item label="批次号" required>
          <el-input v-model="form.batchNo" maxlength="40" placeholder="例如：20260630-01" @input="batchNoTouched = true" />
        </el-form-item>
        <el-form-item label="煮糖日期" required>
          <el-date-picker v-model="form.boilingDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" @change="handleBoilingDateChange" />
        </el-form-item>
        <el-form-item label="班组/机组">
          <el-input v-model="form.teamName" placeholder="例如：机破1组、4.5.6.7组" />
        </el-form-item>
        <el-form-item label="糖类型">
          <el-select v-model="form.sugarType" clearable placeholder="选择糖类型" style="width: 100%">
            <el-option label="白冰糖" value="白冰糖" />
            <el-option label="黄冰糖" value="黄冰糖" />
          </el-select>
        </el-form-item>
        <el-form-item label="锅数">
          <el-input-number v-model="form.potCount" :min="1" :step="1" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="实际桶数" required>
          <el-input-number v-model="form.bucketCount" :min="1" :step="1" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="每桶重量kg" required>
          <el-input-number v-model="form.kgPerBucket" :min="0.1" :step="0.1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="自动折算">
          <div class="calc-preview">{{ numberText(form.bucketCount) }}桶 × {{ numberText(form.kgPerBucket) }}kg = {{ numberText(totalWeightPreview) }}kg</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitSave">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="detailVisible"
      title="煮糖批次详情"
      size="82%"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="detail-body">
        <div v-if="detail" class="summary-grid">
          <div class="summary-item">
            <span>批次号</span>
            <strong>{{ detail.batchNo }}</strong>
          </div>
          <div class="summary-item">
            <span>煮糖日期</span>
            <strong>{{ detail.boilingDate }}</strong>
          </div>
          <div class="summary-item">
            <span>总量</span>
            <strong>{{ numberText(detail.bucketCount) }}桶 / {{ numberText(detail.totalWeightKg) }}kg</strong>
          </div>
          <div class="summary-item">
            <span>可用剩余</span>
            <strong>{{ numberText(detail.remainingBucketCount) }}桶 / {{ numberText(detail.remainingWeightKg) }}kg</strong>
          </div>
          <div class="summary-item">
            <span>已预占</span>
            <strong>{{ numberText(detail.reservedBucketCount) }}桶</strong>
          </div>
          <div class="summary-item">
            <span>已消耗</span>
            <strong>{{ numberText(detail.consumedBucketCount) }}桶</strong>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">生产订单引用</div>
          <el-table :data="detail?.usages || []" border stripe empty-text="暂无引用记录">
            <el-table-column prop="orderNo" label="订单号" min-width="150" />
            <el-table-column label="引用数量" width="140">
              <template #default="{ row }">{{ usageText(row) }}</template>
            </el-table-column>
            <el-table-column label="折算" min-width="160">
              <template #default="{ row }">{{ numberText(row.bucketQuantity) }}桶 / {{ numberText(row.weightKg) }}kg</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="usageStatusType(row.status)">{{ usageStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="引用时间" width="165">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="detail-section trace-section">
          <div class="trace-section-head">
            <div>
              <div class="section-title">去向追溯</div>
              <div class="section-subtitle">按真实订单、二维码、库位和领用关系实时组装，不维护独立追溯台账。</div>
            </div>
            <el-button :loading="detailLoading" @click="refreshTrace">刷新</el-button>
          </div>

          <el-tabs v-model="activeTraceTab" class="trace-tabs">
            <el-tab-pane label="物料流向图" name="graph">
              <div v-if="hasTraceFlow" class="trace-graph-panel">
                <div class="trace-overview">
                  <div>
                    <span>流向节点</span>
                    <strong>{{ traceNodes.length }}</strong>
                  </div>
                  <div>
                    <span>关联动作</span>
                    <strong>{{ traceEdges.length }}</strong>
                  </div>
                  <div>
                    <span>下游生产订单</span>
                    <strong>{{ downstreamOrderCount }}</strong>
                  </div>
                  <div>
                    <span>最终入库节点</span>
                    <strong>{{ finalWarehouseCount }}</strong>
                  </div>
                </div>

                <div class="trace-graph-scroll">
                  <div
                    class="trace-flow-canvas"
                    :style="{ width: traceGraphWidth + 'px', height: traceGraphHeight + 'px' }"
                  >
                    <div
                      v-for="column in traceGraphColumns"
                      :key="column.depth"
                      class="trace-stage"
                      :style="{ left: column.x + 'px', width: traceNodeWidth + 'px' }"
                    >
                      <div class="trace-stage-title">{{ column.title }}</div>
                    </div>

                    <svg
                      class="trace-flow-lines"
                      :viewBox="'0 0 ' + traceGraphWidth + ' ' + traceGraphHeight"
                      preserveAspectRatio="none"
                      aria-hidden="true"
                    >
                      <defs>
                        <marker id="trace-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
                          <path d="M0,0 L8,4 L0,8 Z" fill="#6f93e8" />
                        </marker>
                      </defs>
                      <g v-for="edge in traceFlowEdges" :key="edge.id">
                        <path
                          :d="edge.path"
                          class="trace-flow-path"
                          marker-end="url(#trace-arrow)"
                        />
                        <foreignObject
                          :x="edge.labelX"
                          :y="edge.labelY"
                          :width="edge.labelWidth"
                          height="40"
                          class="trace-edge-foreign"
                        >
                          <div class="trace-flow-edge-label" :title="edge.fullLabel">
                            <strong>{{ edge.action }}</strong>
                            <span v-if="edge.quantityText">{{ edge.quantityText }}</span>
                          </div>
                        </foreignObject>
                      </g>
                    </svg>

                    <div
                      v-for="node in tracePositionedNodes"
                      :key="node.id"
                      class="trace-flow-node"
                      :class="	race-flow-node--"
                      :style="{ left: node.x + 'px', top: node.y + 'px' }"
                      :title="nodeTooltip(node)"
                    >
                      <div class="trace-flow-node-type">{{ nodeTypeText(node.type) }}</div>
                      <div class="trace-flow-node-title">{{ nodeTitle(node) }}</div>
                      <div v-if="node.quantityText" class="trace-flow-node-detail">{{ node.quantityText }}</div>
                      <div v-if="node.warehouseName && node.warehouseName !== nodeTitle(node)" class="trace-flow-node-detail">
                        {{ node.warehouseName }}
                      </div>
                      <span v-if="node.status" class="trace-flow-node-status">{{ node.status }}</span>
                    </div>
                  </div>
                </div>

                <div class="trace-legend">
                  <span v-for="item in traceLegend" :key="item.type">
                    <i :style="{ backgroundColor: item.color }"></i>{{ item.label }}
                  </span>
                </div>
              </div>
              <div v-else class="empty-panel">
                <strong>暂无后续流向</strong>
                <span>当前仅有煮糖批次信息，生产订单引用、产出或入库后会自动显示。</span>
              </div>
            </el-tab-pane>

            <el-tab-pane label="流转明细表" name="timeline">
              <div class="timeline-head">
                <span>共 {{ traceTimeline.length }} 条流转记录，按发生时间从早到晚排列。</span>
              </div>
              <el-table
                :data="traceTimeline"
                border
                stripe
                max-height="520"
                empty-text="暂无流转明细"
                class="trace-timeline-table"
              >
                <el-table-column label="时间" width="165">
                  <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
                </el-table-column>
                <el-table-column prop="actionType" label="动作类型" min-width="135" />
                <el-table-column prop="documentNo" label="单据号" min-width="155" show-overflow-tooltip />
                <el-table-column prop="productName" label="产品/物料" min-width="130" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.productName || '-' }}</template>
                </el-table-column>
                <el-table-column prop="quantityText" label="数量" min-width="130">
                  <template #default="{ row }">{{ row.quantityText || '-' }}</template>
                </el-table-column>
                <el-table-column label="库位" min-width="105">
                  <template #default="{ row }">{{ row.warehouseName || '-' }}</template>
                </el-table-column>
                <el-table-column label="关联对象" min-width="170" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.relatedObject || '-' }}</template>
                </el-table-column>
                <el-table-column label="状态" width="105">
                  <template #default="{ row }">
                    <el-tag size="small" :type="timelineStatusType(row.status)">{{ row.status || '-' }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelBoilingBatch, createBoilingBatch, getBoilingBatchDetail, getBoilingBatchTrace, pageBoilingBatches, updateBoilingBatch } from '@/api/production'
import { formatDateTime } from '@/utils/dateTime'

const statusOptions = [
  { value: 'AVAILABLE', label: '可用', type: 'success' },
  { value: 'USED_UP', label: '已用完', type: 'warning' },
  { value: 'CANCELED', label: '已作废', type: 'info' }
]

const query = ref({ batchNo: '', status: '', dateRange: [] })
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const trace = ref(null)
const activeTraceTab = ref('graph')
const form = ref({})
const batchNoTouched = ref(false)

const today = () => new Date().toISOString().slice(0, 10)
const defaultBatchNo = date => {
  const prefix = (date || today()).replaceAll('-', '') + '-'
  const maxSeq = rows.value
    .map(item => item.batchNo || '')
    .filter(no => no.startsWith(prefix))
    .map(no => Number(no.slice(prefix.length)))
    .filter(num => Number.isFinite(num))
    .reduce((max, num) => Math.max(max, num), 0)
  return `${prefix}${String(maxSeq + 1).padStart(2, '0')}`
}
const totalWeightPreview = computed(() => Number(form.value.bucketCount || 0) * Number(form.value.kgPerBucket || 0))
const traceNodeWidth = 176
const traceNodeHeight = 104
const traceColumnStride = 248
const traceTopPadding = 62

const traceNodes = computed(() => trace.value?.nodes || [])
const traceEdges = computed(() => trace.value?.edges || [])
const traceTimeline = computed(() => trace.value?.timeline || [])
const hasTraceFlow = computed(() => traceNodes.value.length > 1 && traceEdges.value.length > 0)
const downstreamOrderCount = computed(() => traceNodes.value.filter(item => item.type === 'DOWNSTREAM_PRODUCTION_ORDER').length)
const finalWarehouseCount = computed(() => traceNodes.value.filter(item => item.type === 'FINISHED_WAREHOUSE_LOCATION').length)

const traceLegend = [
  { type: 'BOILING_BATCH_ITEM', label: '来源批次', color: '#f2a23a' },
  { type: 'PRODUCTION_ORDER', label: '半成品订单', color: '#5b8ff9' },
  { type: 'ACTUAL_OUTPUT', label: '半成品产出', color: '#63b45c' },
  { type: 'PALLET_CODE_BATCH', label: '订单码批次', color: '#38a6a5' },
  { type: 'PALLET_CODE', label: '二维码', color: '#55b7bd' },
  { type: 'WAREHOUSE_LOCATION', label: '半成品库位', color: '#7c98b6' },
  { type: 'DOWNSTREAM_PRODUCTION_ORDER', label: '成品订单', color: '#8a63d2' },
  { type: 'FINISHED_OUTPUT', label: '成品产出', color: '#df7398' },
  { type: 'FINISHED_WAREHOUSE_LOCATION', label: '成品库位', color: '#cf79a8' }
]

const traceGraphLayout = computed(() => {
  const nodes = traceNodes.value
  const nodeIds = new Set(nodes.map(item => item.id))
  const validEdges = traceEdges.value.filter(edge => nodeIds.has(edge.source) && nodeIds.has(edge.target))
  const depths = new Map(nodes.map(item => [item.id, 0]))

  for (let round = 0; round < nodes.length; round += 1) {
    let changed = false
    validEdges.forEach(edge => {
      const nextDepth = Math.min(nodes.length - 1, (depths.get(edge.source) || 0) + 1)
      if (nextDepth > (depths.get(edge.target) || 0)) {
        depths.set(edge.target, nextDepth)
        changed = true
      }
    })
    if (!changed) break
  }

  const groups = new Map()
  nodes.forEach(node => {
    const depth = depths.get(node.id) || 0
    if (!groups.has(depth)) groups.set(depth, [])
    groups.get(depth).push(node)
  })

  const columns = [...groups.entries()]
    .sort(([left], [right]) => left - right)
    .map(([depth, items]) => ({
      depth,
      items: items.sort((left, right) => {
        const timeCompare = String(left.occurredAt || '').localeCompare(String(right.occurredAt || ''))
        return timeCompare || String(left.name || '').localeCompare(String(right.name || ''), 'zh-CN')
      })
    }))

  return { columns, edges: validEdges }
})

const traceGraphWidth = computed(() => Math.max(980, 40 + traceGraphLayout.value.columns.length * traceColumnStride))
const traceGraphHeight = computed(() => {
  const maxRows = Math.max(1, ...traceGraphLayout.value.columns.map(item => item.items.length))
  return Math.max(420, traceTopPadding + maxRows * (traceNodeHeight + 24) + 24)
})
const numberText = value => {
  const num = Number(value || 0)
  return Number.isInteger(num) ? String(num) : num.toFixed(3).replace(/0+$/, '').replace(/\.$/, '')
}
const statusText = status => statusOptions.find(item => item.value === status)?.label || '未知状态'
const statusType = status => statusOptions.find(item => item.value === status)?.type || ''
const usageStatusText = status => ({ RESERVED: '已预占', CONSUMED: '已消耗', RELEASED: '已释放', CANCELED: '已取消' }[status] || '未知状态')
const usageStatusType = status => ({ RESERVED: 'warning', CONSUMED: 'success', RELEASED: 'info', CANCELED: 'info' }[status] || 'info')
const timelineStatusType = status => status?.includes('完成') || status?.includes('入库') || status?.includes('消耗') || status?.includes('领用') ? 'success' : status?.includes('待') || status?.includes('预占') ? 'warning' : 'info'
const usageText = row => row.usageUnit === 'KG' ? numberText(row.usageQuantity) + 'kg' : numberText(row.usageQuantity) + '桶'
const nodeTypeText = type => ({
  BOILING_BATCH_ITEM: '煮糖批次明细',
  PRODUCTION_ORDER: '半成品生产订单',
  ACTUAL_OUTPUT: '实际产出',
  PALLET_CODE_BATCH: '二维码批次',
  PALLET_CODE: '托盘码',
  WAREHOUSE_LOCATION: '半成品入库库位',
  DOWNSTREAM_PRODUCTION_ORDER: '下游生产订单',
  FINISHED_OUTPUT: '成品产出',
  FINISHED_WAREHOUSE_LOCATION: '成品入库库位'
}[type] || '流转节点')
const nodeTitle = node => node.name || node.documentNo || node.productName || node.warehouseName || '-'
const stageTitle = items => {
  const labels = [...new Set(items.map(item => nodeTypeText(item.type)))]
  return labels.join(' / ')
}

const traceGraphColumns = computed(() => traceGraphLayout.value.columns.map((column, columnIndex) => ({
  ...column,
  x: 20 + columnIndex * traceColumnStride,
  title: stageTitle(column.items)
})))

const tracePositionedNodes = computed(() => {
  const height = traceGraphHeight.value
  return traceGraphColumns.value.flatMap(column => {
    const availableHeight = height - traceTopPadding - 16
    const rowSpace = availableHeight / column.items.length
    return column.items.map((node, rowIndex) => ({
      ...node,
      x: column.x,
      y: traceTopPadding + rowSpace * (rowIndex + 0.5) - traceNodeHeight / 2
    }))
  })
})

const traceNodePositionMap = computed(() => new Map(tracePositionedNodes.value.map(node => [node.id, node])))

const traceFlowEdges = computed(() => traceGraphLayout.value.edges.map(edge => {
  const source = traceNodePositionMap.value.get(edge.source)
  const target = traceNodePositionMap.value.get(edge.target)
  if (!source || !target) return null

  const sourceX = source.x + traceNodeWidth
  const sourceY = source.y + traceNodeHeight / 2
  const targetX = target.x
  const targetY = target.y + traceNodeHeight / 2
  const controlX = sourceX + Math.max(28, (targetX - sourceX) / 2)
  const labelWidth = Math.min(86, Math.max(52, targetX - sourceX - 12))
  const labelCenterX = sourceX + (targetX - sourceX) * 0.5
  const labelX = labelCenterX - labelWidth / 2
  const labelY = sourceY * 0.35 + targetY * 0.65 - 20

  return {
    ...edge,
    path: 'M ' + sourceX + ' ' + sourceY + ' C ' + controlX + ' ' + sourceY + ', ' + controlX + ' ' + targetY + ', ' + (targetX - 4) + ' ' + targetY,
    labelX,
    labelY,
    labelWidth,
    fullLabel: [edge.action, edge.quantityText].filter(Boolean).join(' ')
  }
}).filter(Boolean))

const nodeTooltip = node => [
  nodeTypeText(node.type),
  nodeTitle(node),
  node.productName,
  node.quantityText,
  node.warehouseName,
  node.status
].filter(Boolean).join(' · ')

const refreshTrace = async () => {
  if (!detail.value?.id) return
  detailLoading.value = true
  try {
    const traceRes = await getBoilingBatchTrace(detail.value.id)
    trace.value = traceRes.data
  } finally {
    detailLoading.value = false
  }
}
const buildQuery = () => {
  const [startDate, endDate] = query.value.dateRange || []
  return {
    batchNo: query.value.batchNo || undefined,
    status: query.value.status || undefined,
    startDate,
    endDate,
    page: page.value,
    size: size.value
  }
}

const loadList = async (nextPage = page.value) => {
  page.value = nextPage
  loading.value = true
  try {
    const res = await pageBoilingBatches(buildQuery())
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.value = { batchNo: '', status: '', dateRange: [] }
  loadList(1)
}

const handleSize = nextSize => {
  size.value = nextSize
  loadList(1)
}

const emptyForm = () => {
  const boilingDate = today()
  return {
    batchNo: defaultBatchNo(boilingDate),
    boilingDate,
    teamName: '',
    sugarType: '',
    potCount: 1,
    bucketCount: 180,
    kgPerBucket: 11,
    remark: ''
  }
}

const openCreate = () => {
  editingId.value = null
  form.value = emptyForm()
  batchNoTouched.value = false
  dialogVisible.value = true
}

const openEdit = row => {
  editingId.value = row.id
  form.value = {
    batchNo: row.batchNo || '',
    boilingDate: row.boilingDate,
    teamName: row.teamName || '',
    sugarType: row.sugarType || '',
    potCount: Number(row.potCount || 1),
    bucketCount: Number(row.bucketCount || 180),
    kgPerBucket: Number(row.kgPerBucket || 11),
    remark: row.remark || ''
  }
  batchNoTouched.value = true
  dialogVisible.value = true
}

const handleBoilingDateChange = () => {
  if (!editingId.value && !batchNoTouched.value) {
    form.value.batchNo = defaultBatchNo(form.value.boilingDate)
  }
}

const submitSave = async () => {
  if (!form.value.batchNo) {
    ElMessage.warning('请填写批次号')
    return
  }
  if (!form.value.boilingDate) {
    ElMessage.warning('请选择煮糖日期')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateBoilingBatch(editingId.value, form.value)
    } else {
      await createBoilingBatch(form.value)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadList(page.value)
  } finally {
    saving.value = false
  }
}

const cancelRow = async row => {
  await ElMessageBox.confirm(`确认作废煮糖批次“${row.batchNo}”吗？`, '作废煮糖批次', { type: 'warning' })
  await cancelBoilingBatch(row.id)
  ElMessage.success('已作废')
  await loadList(page.value)
}

const openDetail = async row => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const [detailRes, traceRes] = await Promise.all([getBoilingBatchDetail(row.id), getBoilingBatchTrace(row.id)])
    detail.value = detailRes.data
    trace.value = traceRes.data
    activeTraceTab.value = 'graph'
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => loadList(1))

</script>

<style scoped>
.boiling-page {
  padding: 0;
}
.search-card {
  margin-bottom: 16px;
}
.page-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.section-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--app-text);
}
.section-subtitle,
.muted {
  color: var(--app-text-tertiary);
  font-size: 13px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.calc-preview,
.empty-panel {
  color: var(--app-text-secondary);
  font-size: 13px;
}
.boiling-form :deep(.el-input-number .el-input__inner) {
  text-align: center;
}
.boiling-form :deep(.el-input-number__decrease),
.boiling-form :deep(.el-input-number__increase) {
  width: 46px;
  font-size: 17px;
  font-weight: 600;
}
.boiling-form :deep(.el-input-number__decrease) {
  border-radius: 4px 0 0 4px;
}
.boiling-form :deep(.el-input-number__increase) {
  border-radius: 0 4px 4px 0;
}
.detail-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.summary-grid > div {
  border: 1px solid var(--app-border);
  border-radius: 8px;
  padding: 12px;
  background: var(--app-bg-soft);
}
.summary-grid span {
  display: block;
  color: var(--app-text-tertiary);
  font-size: 12px;
  margin-bottom: 6px;
}
.detail-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.trace-section {
  min-width: 0;
}
.trace-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.trace-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
.trace-tabs :deep(.el-tabs__content) {
  overflow: visible;
}
.trace-graph-panel {
  min-width: 0;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #fff;
  overflow: hidden;
}
.trace-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(130px, 1fr));
  border-bottom: 1px solid #e8edf4;
  background: #f8faff;
}
.trace-overview > div {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 8px;
  min-height: 58px;
  padding: 12px 16px;
  border-right: 1px solid #e8edf4;
}
.trace-overview > div:last-child {
  border-right: 0;
}
.trace-overview span {
  color: #7a8799;
  font-size: 12px;
}
.trace-overview strong {
  color: #21314b;
  font-size: 20px;
}
.trace-graph-scroll {
  overflow: auto;
  min-height: 460px;
  background:
    linear-gradient(#f3f6fb 1px, transparent 1px),
    linear-gradient(90deg, #f3f6fb 1px, transparent 1px);
  background-size: 24px 24px;
}
.trace-flow-canvas {
  position: relative;
  min-height: 420px;
}
.trace-stage {
  position: absolute;
  top: 0;
  bottom: 0;
  border-left: 1px solid rgba(222, 228, 237, 0.82);
}
.trace-stage:last-of-type {
  border-right: 1px solid rgba(222, 228, 237, 0.82);
}
.trace-stage-title {
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 8px;
  border-bottom: 1px solid #e5eaf2;
  background: rgba(248, 250, 253, 0.95);
  color: #66758b;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
}
.trace-flow-lines {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}
.trace-flow-path {
  fill: none;
  stroke: #6f93e8;
  stroke-width: 1.5;
  opacity: 0.9;
}
.trace-edge-foreign {
  overflow: visible;
}
.trace-flow-edge-label {
  width: 100%;
  min-height: 30px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3px 5px;
  border: 1px solid #e2e8f2;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.96);
  color: #607088;
  font-size: 10px;
  line-height: 1.25;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  box-shadow: 0 2px 6px rgba(31, 45, 61, 0.06);
}
.trace-flow-edge-label strong,
.trace-flow-edge-label span {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}
.trace-flow-edge-label strong {
  color: #405473;
  font-weight: 600;
}
.trace-flow-node {
  position: absolute;
  z-index: 2;
  width: 176px;
  height: 104px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #aab5c4;
  border-radius: 7px;
  background: #fff;
  box-shadow: 0 4px 12px rgba(31, 45, 61, 0.08);
  overflow: hidden;
}
.trace-flow-node-type {
  color: #7a8799;
  font-size: 10px;
  line-height: 15px;
}
.trace-flow-node-title {
  color: #22334d;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.trace-flow-node-detail {
  color: #53647b;
  font-size: 10px;
  line-height: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.trace-flow-node-status {
  width: fit-content;
  margin-top: auto;
  padding: 1px 6px;
  border-radius: 3px;
  background: #edf8ef;
  color: #2d9150;
  font-size: 10px;
  line-height: 16px;
}
.trace-flow-node--BOILING_BATCH_ITEM {
  border-color: #f2a23a;
  background: #fff9ef;
}
.trace-flow-node--PRODUCTION_ORDER {
  border-color: #5b8ff9;
  background: #f4f7ff;
}
.trace-flow-node--ACTUAL_OUTPUT {
  border-color: #63b45c;
  background: #f4fbf2;
}
.trace-flow-node--PALLET_CODE_BATCH {
  border-color: #38a6a5;
  background: #f1fafa;
}
.trace-flow-node--PALLET_CODE {
  border-color: #55b7bd;
  background: #f1fbfc;
}
.trace-flow-node--WAREHOUSE_LOCATION {
  border-color: #7c98b6;
  background: #f4f7fa;
}
.trace-flow-node--DOWNSTREAM_PRODUCTION_ORDER {
  border-color: #8a63d2;
  background: #f8f5ff;
}
.trace-flow-node--FINISHED_OUTPUT {
  border-color: #df7398;
  background: #fff5f8;
}
.trace-flow-node--FINISHED_WAREHOUSE_LOCATION {
  border-color: #cf79a8;
  background: #fff4f9;
}
.trace-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
  padding: 12px 16px;
  border-top: 1px solid #e8edf4;
  color: #64748b;
  font-size: 12px;
}
.trace-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.trace-legend i {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}
.timeline-head {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
  color: var(--app-text-tertiary);
  font-size: 12px;
}
.trace-timeline-table :deep(.el-table__cell) {
  padding-top: 9px;
  padding-bottom: 9px;
}
.empty-panel {
  min-height: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px dashed #d8dee9;
  border-radius: 6px;
  background: #fafbfc;
  text-align: center;
}
.empty-panel strong {
  color: #34445c;
  font-size: 15px;
}
.empty-panel span {
  color: #8793a5;
  font-size: 12px;
}
@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .trace-overview {
    grid-template-columns: repeat(2, minmax(130px, 1fr));
  }
  .trace-overview > div:nth-child(2) {
    border-right: 0;
  }
  .trace-overview > div:nth-child(-n + 2) {
    border-bottom: 1px solid #e8edf4;
  }
}
</style>
