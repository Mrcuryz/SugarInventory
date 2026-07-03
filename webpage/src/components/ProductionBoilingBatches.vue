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
        <el-form-item label="每桶重量(kg)" required>
          <el-input-number v-model="form.kgPerBucket" :min="0.1" :step="0.1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="自动折算">
          <div class="calc-preview">{{ numberText(form.potCount) }}锅 × {{ numberText(form.bucketCount) }}桶 × {{ numberText(form.kgPerBucket) }}kg = {{ numberText(totalWeightPreview) }}kg</div>
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
      size="90%"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="detail-body">
        <div v-if="detail" class="summary-grid">
          <div v-for="item in summaryCards" :key="item.label" class="summary-item">
            <div class="summary-icon" :class="`summary-icon--${item.tone}`">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <div class="summary-content">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </div>

        <div class="detail-section usage-section">
          <div class="section-title">生产订单引用</div>
          <el-table :data="detail?.usages || []" border stripe size="small" max-height="188" empty-text="暂无引用记录">
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

        <div class="detail-section trace-section trace-card">
          <div class="trace-section-head">
            <div>
              <div class="section-title">去向追溯</div>
              <div class="section-subtitle">按真实订单、二维码、库位和领用关系实时组装，不维护独立追溯台账。</div>
            </div>
          </div>

          <el-tabs v-model="activeTraceTab" class="trace-tabs">
            <el-tab-pane label="物料流向图" name="graph">
              <div class="trace-tab-hint">按物流路径查看批次去向，支持放大、缩小、拖动画布及查看节点详情。</div>
              <div v-if="hasTraceFlow" class="trace-graph-panel">
                <div class="trace-toolbar">
                  <div class="trace-toolbar-stats">
                    <div class="trace-stat">
                      <span>流向节点</span>
                      <strong>{{ traceNodes.length }}</strong>
                    </div>
                    <div class="trace-stat">
                      <span>关联动作</span>
                      <strong>{{ traceEdges.length }}</strong>
                    </div>
                    <div class="trace-stat">
                      <span>下游生产订单</span>
                      <strong>{{ downstreamOrderCount }}</strong>
                    </div>
                    <div class="trace-stat">
                      <span>最终入库节点</span>
                      <strong>{{ finalWarehouseCount }}</strong>
                    </div>
                  </div>
                  <div class="trace-toolbar-actions">
                    <el-button-group>
                      <el-button :icon="ZoomOut" @click="zoomOut" />
                      <el-button class="zoom-value">{{ Math.round(traceZoom * 100) }}%</el-button>
                      <el-button :icon="ZoomIn" @click="zoomIn" />
                    </el-button-group>
                    <el-button :icon="Aim" @click="fitTraceCanvas">适应画布</el-button>
                    <el-button @click="showTraceLegend = !showTraceLegend">图例</el-button>
                    <el-button :icon="Refresh" :loading="detailLoading" @click="refreshTrace">刷新</el-button>
                  </div>
                </div>

                <div
                  ref="traceGraphScrollRef"
                  class="trace-graph-scroll"
                  :class="{ 'trace-graph-scroll--dragging': traceDragState.dragging }"
                  @mousedown="startTraceDrag"
                  @mousemove="moveTraceDrag"
                  @mouseup="stopTraceDrag"
                  @mouseleave="stopTraceDrag"
                >
                  <div
                    class="trace-zoom-surface"
                    :style="{ width: scaledTraceGraphWidth + 'px', height: scaledTraceGraphHeight + 'px' }"
                  >
                    <div
                    class="trace-flow-canvas"
                      :style="{
                        width: traceGraphWidth + 'px',
                        height: traceGraphHeight + 'px',
                        transform: `scale(${traceZoom})`
                      }"
                  >
                    <div
                      v-for="column in traceGraphColumns"
                        :key="column.key"
                      class="trace-stage"
                      :style="{ left: column.x + 'px', width: traceColumnStride + 'px' }"
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
                        <marker id="trace-arrow-aux" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
                          <path d="M0,0 L8,4 L0,8 Z" fill="#8fa2bf" />
                        </marker>
                      </defs>
                      <g
                        v-for="edge in auxiliaryTraceFlowEdges"
                        :key="edge.id"
                        class="trace-edge-layer trace-edge-layer--auxiliary"
                      >
                        <path :d="edge.path" class="trace-flow-path-hit" @click="openEdgeDetail(edge)" />
                        <path
                          :d="edge.path"
                          class="trace-flow-path trace-flow-path--auxiliary"
                          marker-end="url(#trace-arrow-aux)"
                          @click="openEdgeDetail(edge)"
                        />
                        <foreignObject
                          :x="edge.labelX"
                          :y="edge.labelY"
                          :width="edge.labelWidth"
                          height="40"
                          class="trace-edge-foreign"
                        >
                          <div class="trace-flow-edge-label" :title="edge.fullLabel" @click="openEdgeDetail(edge)">
                            <strong>{{ edge.shortAction }}</strong>
                          </div>
                        </foreignObject>
                      </g>
                      <g
                        v-for="edge in primaryTraceFlowEdges"
                        :key="edge.id"
                        class="trace-edge-layer trace-edge-layer--primary"
                      >
                        <path :d="edge.path" class="trace-flow-path-hit" @click="openEdgeDetail(edge)" />
                        <path
                          :d="edge.path"
                          class="trace-flow-path"
                          :class="{ 'trace-flow-path--dashed': edge.lineType === 'DASHED' }"
                          marker-end="url(#trace-arrow)"
                          @click="openEdgeDetail(edge)"
                        />
                        <foreignObject
                          :x="edge.labelX"
                          :y="edge.labelY"
                          :width="edge.labelWidth"
                          height="40"
                          class="trace-edge-foreign"
                        >
                          <div class="trace-flow-edge-label" :title="edge.fullLabel" @click="openEdgeDetail(edge)">
                            <strong>{{ edge.shortAction }}</strong>
                          </div>
                        </foreignObject>
                      </g>
                    </svg>

                    <div
                      v-for="node in tracePositionedNodes"
                      :key="node.id"
                      class="trace-flow-node"
                        :class="[`trace-flow-node--${node.type}`, `trace-flow-node--${node.tone}`]"
                      :style="{ left: node.x + 'px', top: node.y + 'px', width: traceNodeWidth + 'px', height: traceNodeHeight + 'px' }"
                      :title="nodeTooltip(node)"
                        @click="openNodeDetail(node)"
                    >
                      <div class="trace-flow-node-type">{{ nodeTypeText(node.type) }}</div>
                      <div class="trace-flow-node-title">{{ nodeTitle(node) }}</div>
                      <div v-if="node.quantityText" class="trace-flow-node-detail">{{ node.quantityText }}</div>
                      <div v-if="node.warehouseName && node.warehouseName !== nodeTitle(node)" class="trace-flow-node-detail">
                        {{ node.warehouseName }}
                      </div>
                      <span v-if="node.status" class="trace-flow-node-status" :class="statusToneClass(node.status)">{{ node.status }}</span>
                    </div>
                  </div>
                </div>
                </div>

                <div v-if="showTraceLegend" class="trace-legend">
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
              <div class="trace-tab-hint">按链路分组查看完整流转记录，默认展开前 2 条链路。</div>
              <div class="chain-filter-bar">
                <el-radio-group v-model="activeChainFilter" size="small">
                  <el-radio-button v-for="item in chainFilters" :key="item.value" :label="item.value">
                    {{ item.label }}（{{ chainFilterCount(item.value) }}）
                  </el-radio-button>
                </el-radio-group>
                <div class="chain-stat-strip">
                  <span>已形成链路：<strong>{{ completedChainCount }}</strong> 条</span>
                  <span>预占链路：<strong>{{ reservedChainCount }}</strong> 条</span>
                  <span>最终成品入库：<strong>{{ finalInboundRecordCount }}</strong> 笔</span>
                </div>
              </div>

              <div v-if="filteredTraceChains.length" class="chain-list">
                <el-collapse v-model="expandedChainNames">
                  <el-collapse-item v-for="chain in filteredTraceChains" :key="chain.id" :name="chain.id">
                    <template #title>
                      <div class="chain-title" :class="`chain-title--${chain.kind}`">
                        <span class="chain-mark"></span>
                        <div class="chain-main">
                          <div class="chain-name">
                            <strong>{{ chain.name }}</strong>
                            <el-tag size="small" :type="chainStatusType(chain.status)">{{ chain.status }}</el-tag>
                          </div>
                          <div class="chain-summary">
                            来源批次：{{ chain.sourceBatch }} · 链路总消耗：{{ chain.consumeText }} · 最终产品：{{ chain.finalProduct }} · 最终入库：{{ chain.finalWarehouse }}
                          </div>
                          <div class="chain-metrics">
                            共 {{ chain.actionCount }} 个流转动作 · {{ chain.detailCount }} 条明细记录 · 已聚合 {{ chain.aggregatedGroupCount }} 组
                          </div>
                        </div>
                      </div>
                    </template>
                    <div class="chain-timeline-groups">
                      <div class="chain-timeline-head">
                        <span>时间</span>
                        <span>流转动作</span>
                        <span>批次号/单号</span>
                        <span>产品名称/类型</span>
                        <span>数量</span>
                        <span>库位</span>
                        <span>关联对象/摘要</span>
                        <span>状态</span>
                        <span>明细</span>
                        <span></span>
                      </div>
                      <TraceTimelineGroup
                        v-for="group in chain.groups"
                        :key="group.id"
                        :group="group"
                      />
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </div>
              <div v-else class="empty-panel">
                <strong>暂无流转明细</strong>
                <span>当前筛选条件下没有可展示的链路记录。</span>
              </div>

              <div class="raw-table-toggle">
                <el-button link type="primary" @click="showRawTimeline = !showRawTimeline">
                  {{ showRawTimeline ? '收起原始明细表' : '查看原始明细表' }}
                </el-button>
              </div>
              <el-table
                v-if="showRawTimeline"
                :data="traceTimeline"
                border
                stripe
                max-height="360"
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

    <el-dialog v-model="traceDetailVisible" :title="traceDetailTitle" width="440px" append-to-body>
      <div v-if="selectedTraceDetail" class="trace-detail-dialog">
        <div v-for="item in selectedTraceDetailRows" :key="item.label" class="trace-detail-row">
          <span>{{ item.label }}</span>
          <strong>{{ item.value || '-' }}</strong>
        </div>
        <div v-if="selectedTraceActions.length" class="trace-detail-actions">
          <span>下一步动作</span>
          <div class="trace-detail-action-buttons">
            <el-button
              v-for="action in selectedTraceActions"
              :key="action.label"
              :type="action.type || 'primary'"
              @click="navigateTraceAction(action)"
            >
              {{ action.label }}
            </el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Aim, Box, Calendar, CircleCheck, Clock, Connection, Refresh, Tickets, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { cancelBoilingBatch, createBoilingBatch, getBoilingBatchDetail, getBoilingBatchTraceGraph, pageBoilingBatches, updateBoilingBatch } from '@/api/production'
import { formatDateTime } from '@/utils/dateTime'
import { aggregateTraceTimeline, isAuxiliaryTraceEdge, routeTraceEdge } from '@/utils/productionTracePresentation.mjs'
import TraceTimelineGroup from '@/components/production/TraceTimelineGroup.vue'

const statusOptions = [
  { value: 'AVAILABLE', label: '可用', type: 'success' },
  { value: 'USED_UP', label: '已用完', type: 'warning' },
  { value: 'CANCELED', label: '已作废', type: 'info' }
]

const router = useRouter()
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
const traceZoom = ref(1)
const showTraceLegend = ref(true)
const activeChainFilter = ref('all')
const expandedChainNames = ref([])
const showRawTimeline = ref(false)
const traceDetailVisible = ref(false)
const traceDetailTitle = ref('详情')
const selectedTraceDetail = ref(null)
const selectedTraceDetailKind = ref('node')
const traceGraphScrollRef = ref(null)
const traceDragState = ref({
  dragging: false,
  startX: 0,
  startY: 0,
  scrollLeft: 0,
  scrollTop: 0
})

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
const totalWeightPreview = computed(() => Number(form.value.potCount || 0) * Number(form.value.bucketCount || 0) * Number(form.value.kgPerBucket || 0))
const traceNodeWidth = 160
const traceNodeHeight = 84
const traceColumnStride = 300
const traceTopPadding = 72
const traceLeftPadding = 32
const traceRowStride = 104

const traceNodes = computed(() => trace.value?.nodes || [])
const traceEdges = computed(() => trace.value?.edges || [])
const traceTimeline = computed(() => trace.value?.timeline || [])
const hasTraceFlow = computed(() => traceNodes.value.length > 1 && traceEdges.value.length > 0)
const downstreamOrderCount = computed(() => traceNodes.value.filter(item => item.type === 'DOWNSTREAM_PRODUCTION_ORDER').length)
const finalWarehouseCount = computed(() => traceNodes.value.filter(item => item.type === 'FINISHED_WAREHOUSE_LOCATION').length)

const summaryCards = computed(() => {
  const item = detail.value || {}
  return [
    { label: '批次号', value: item.batchNo || '-', icon: Tickets, tone: 'blue' },
    { label: '煮糖日期', value: item.boilingDate || '-', icon: Calendar, tone: 'cyan' },
    { label: '总量', value: `${numberText(item.bucketCount)}桶 / ${numberText(item.totalWeightKg)}kg`, icon: Box, tone: 'purple' },
    { label: '可用剩余', value: `${numberText(item.remainingBucketCount)}桶 / ${numberText(item.remainingWeightKg)}kg`, icon: Clock, tone: 'orange' },
    { label: '已预占', value: `${numberText(item.reservedBucketCount)}桶`, icon: Connection, tone: 'amber' },
    { label: '已消耗', value: `${numberText(item.consumedBucketCount)}桶`, icon: CircleCheck, tone: 'green' }
  ]
})

const traceLegend = [
  { type: 'BOILING_BATCH_ITEM', label: '来源批次', color: '#f2a23a' },
  { type: 'PRODUCTION_ORDER', label: '生产订单', color: '#5b8ff9' },
  { type: 'ACTUAL_OUTPUT', label: '实际产出', color: '#63b45c' },
  { type: 'PALLET_CODE_BATCH', label: '二维码批次', color: '#38a6a5' },
  { type: 'INBOUND_STORAGE', label: '入库（托盘码/库位）', color: '#7d6ae8' },
  { type: 'DOWNSTREAM_PRODUCTION_ORDER', label: '下游生产订单', color: '#b15ac7' },
  { type: 'FINISHED_OUTPUT', label: '成品产出/入库', color: '#df7398' }
]

const traceColumnDefs = [
  { key: 'source', title: '来源批次' },
  { key: 'semiOrder', title: '半成品生产订单' },
  { key: 'actualOutput', title: '实际产出' },
  { key: 'codeBatch', title: '二维码批次' },
  { key: 'palletLocation', title: '入库' },
  { key: 'downstreamOrder', title: '下游生产订单' },
  { key: 'finished', title: '成品产出/入库' }
]

const chainFilters = [
  { value: 'all', label: '全部' },
  { value: 'main', label: '主链路' },
  { value: 'support', label: '支链路' },
  { value: 'reserved', label: '预占链路' },
  { value: 'completed', label: '已完成' },
  { value: 'finished', label: '仅看成品流向' }
]

const traceNodeMap = computed(() => new Map(traceNodes.value.map(node => [node.id, node])))

const foldedInboundMap = computed(() => {
  const map = new Map()
  traceEdges.value.forEach(edge => {
    const source = traceNodeMap.value.get(edge.source)
    const target = traceNodeMap.value.get(edge.target)
    const action = edge.action || edge.label || ''
    if (source?.type === 'PALLET_CODE' && target?.type === 'WAREHOUSE_LOCATION' && action.includes('入库')) {
      map.set(target.id, {
        palletId: source.id,
        warehouseName: target.warehouseName || target.name || target.documentNo || '',
        warehouseNode: target,
        inboundEdge: edge
      })
    }
  })
  return map
})

const warehouseByPalletId = computed(() => {
  const map = new Map()
  foldedInboundMap.value.forEach(item => {
    map.set(item.palletId, item)
  })
  return map
})

const labelBatchByPalletId = computed(() => {
  const map = new Map()
  traceEdges.value.forEach(edge => {
    const source = traceNodeMap.value.get(edge.source)
    const target = traceNodeMap.value.get(edge.target)
    if (source?.type === 'PALLET_CODE_BATCH' && target?.type === 'PALLET_CODE') {
      map.set(target.id, source.id)
    }
  })
  return map
})

const normalizedTraceNodes = computed(() => traceNodes.value
  .filter(node => !foldedInboundMap.value.has(node.id))
  .map((node, index) => {
    const inbound = warehouseByPalletId.value.get(node.id)
    const displayType = inbound && node.type === 'PALLET_CODE' ? 'INBOUND_STORAGE' : node.type || 'UNKNOWN'
    const mergedNode = inbound ? {
      ...node,
      type: displayType,
      warehouseName: inbound.warehouseName || node.warehouseName || '',
      status: inbound.warehouseNode?.status || node.status || '',
      raw: {
        ...node,
        type: displayType,
        warehouseName: inbound.warehouseName || node.warehouseName || '',
        status: inbound.warehouseNode?.status || node.status || '',
        inboundEdge: inbound.inboundEdge,
        warehouseNode: inbound.warehouseNode
      }
    } : node
    return {
      id: mergedNode.id,
      type: displayType,
      name: mergedNode.name || '',
      documentNo: mergedNode.documentNo || '',
      productName: mergedNode.productName || '',
      warehouseName: mergedNode.warehouseName || '',
      title: nodeTitle(mergedNode),
      subtitle: mergedNode.documentNo || mergedNode.productName || mergedNode.warehouseName || '',
      quantityText: mergedNode.quantityText || '',
      status: mergedNode.status || '',
      columnKey: nodeColumnKey(displayType),
      rowIndex: Number.isFinite(Number(node.rowIndex)) ? Number(node.rowIndex) : index,
      tone: nodeTone(displayType),
      occurredAt: mergedNode.occurredAt || '',
      raw: mergedNode.raw || mergedNode
    }
  }))

const traceGraphLayout = computed(() => {
  const nodes = normalizedTraceNodes.value
  const nodeIds = new Set(nodes.map(item => item.id))
  const validEdges = traceEdges.value
    .map(edge => {
      const rawTarget = traceNodeMap.value.get(edge.target)
      const finishedInboundBatchSource = rawTarget?.type === 'FINISHED_WAREHOUSE_LOCATION'
        ? labelBatchByPalletId.value.get(edge.source)
        : null
      return {
        ...edge,
        originalSource: edge.source,
        originalTarget: edge.target,
        source: finishedInboundBatchSource || foldedInboundMap.value.get(edge.source)?.palletId || edge.source,
        target: foldedInboundMap.value.get(edge.target)?.palletId || edge.target
      }
    })
    .filter(edge => edge.source !== edge.target)
    .filter(edge => nodeIds.has(edge.source) && nodeIds.has(edge.target))
    .filter((edge, index, edges) => edges.findIndex(item => [
      item.source,
      item.target,
      item.action || item.label || '',
      item.quantityText || ''
    ].join('|') === [
      edge.source,
      edge.target,
      edge.action || edge.label || '',
      edge.quantityText || ''
    ].join('|')) === index)
    .map((edge, index) => normalizeTraceEdge(edge, index))
  const groups = new Map(traceColumnDefs.map(item => [item.key, []]))
  nodes.forEach(node => {
    if (!groups.has(node.columnKey)) groups.set(node.columnKey, [])
    groups.get(node.columnKey).push(node)
  })

  const columns = traceColumnDefs.map((definition, columnIndex) => {
    const items = [...(groups.get(definition.key) || [])]
    return {
      ...definition,
      x: traceLeftPadding + columnIndex * traceColumnStride,
      columnIndex,
      items: items.sort((left, right) => {
        const rowCompare = Number(left.rowIndex || 0) - Number(right.rowIndex || 0)
        const timeCompare = String(left.occurredAt || '').localeCompare(String(right.occurredAt || ''))
        return rowCompare || timeCompare || String(left.title || '').localeCompare(String(right.title || ''), 'zh-CN')
      })
    }
  })

  return { columns, edges: validEdges }
})

const traceGraphWidth = computed(() => Math.max(1480, traceLeftPadding * 2 + traceColumnDefs.length * traceColumnStride))
const traceColumnIndexMap = computed(() => {
  const map = new Map()
  traceGraphLayout.value.columns.forEach(column => {
    column.items.forEach(node => map.set(node.id, column.columnIndex))
  })
  return map
})
const auxiliaryEdgeCount = computed(() => traceGraphLayout.value.edges.filter(edge => {
  const sourceIndex = traceColumnIndexMap.value.get(edge.source)
  const targetIndex = traceColumnIndexMap.value.get(edge.target)
  if (sourceIndex === undefined || targetIndex === undefined) return false
  return targetIndex <= sourceIndex || targetIndex - sourceIndex > 1
}).length)
const traceGraphHeight = computed(() => {
  const maxRows = Math.max(1, ...traceGraphLayout.value.columns.map(item => item.items.length))
  const contentHeight = Math.max(440, traceTopPadding + maxRows * traceRowStride + 56)
  const auxiliaryChannelHeight = auxiliaryEdgeCount.value ? Math.min(96, 42 + auxiliaryEdgeCount.value * 12) : 0
  return contentHeight + auxiliaryChannelHeight
})
const scaledTraceGraphWidth = computed(() => traceGraphWidth.value * traceZoom.value)
const scaledTraceGraphHeight = computed(() => traceGraphHeight.value * traceZoom.value)
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
  WAREHOUSE_LOCATION: '入库',
  INBOUND_STORAGE: '入库',
  DOWNSTREAM_PRODUCTION_ORDER: '下游生产订单',
  FINISHED_OUTPUT: '成品产出',
  FINISHED_WAREHOUSE_LOCATION: '成品入库库位',
  AUXILIARY_SOURCE: '辅料来源',
  OTHER_SOURCE: '其他来源'
}[type] || '流转节点')
const nodeTitle = node => node.name || node.documentNo || node.productName || node.warehouseName || '-'

const traceGraphColumns = computed(() => traceGraphLayout.value.columns)

const tracePositionedNodes = computed(() => {
  return traceGraphColumns.value.flatMap(column => {
    return column.items.map((node, rowIndex) => ({
      ...node,
      x: column.x + (traceColumnStride - traceNodeWidth) / 2,
      columnIndex: column.columnIndex,
      y: traceTopPadding + rowIndex * traceRowStride
    }))
  })
})

const traceNodePositionMap = computed(() => new Map(tracePositionedNodes.value.map(node => [node.id, node])))

const traceFlowEdges = computed(() => {
  const auxiliaryChannelMap = new Map()
  return traceGraphLayout.value.edges.map(edge => {
    const source = traceNodePositionMap.value.get(edge.source)
    const target = traceNodePositionMap.value.get(edge.target)
    if (!source || !target) return null
    const auxiliary = isAuxiliaryTraceEdge(source, target)
    const auxiliaryGroupKey = auxiliary
      ? [edge.source, edge.action || edge.label || '', source.columnIndex, target.columnIndex].join('|')
      : ''
    if (auxiliary && !auxiliaryChannelMap.has(auxiliaryGroupKey)) {
      auxiliaryChannelMap.set(auxiliaryGroupKey, auxiliaryChannelMap.size)
    }
    const routed = routeTraceEdge({
      edge,
      source,
      target,
      nodeWidth: traceNodeWidth,
      nodeHeight: traceNodeHeight,
      canvasHeight: traceGraphHeight.value,
      channelIndex: auxiliary ? auxiliaryChannelMap.get(auxiliaryGroupKey) : 0
    })
    return {
      ...routed,
      sourceDisplayName: traceNodeDisplayName(source),
      targetDisplayName: traceNodeDisplayName(target),
      fullLabel: [edge.action, edge.quantityText].filter(Boolean).join(' ')
    }
  }).filter(Boolean)
})
const auxiliaryTraceFlowEdges = computed(() => traceFlowEdges.value.filter(edge => edge.auxiliary))
const primaryTraceFlowEdges = computed(() => traceFlowEdges.value.filter(edge => !edge.auxiliary))

const traceNodeDisplayName = node => {
  if (!node) return ''
  const title = nodeTitle(node)
  const details = [node.productName, node.quantityText, node.warehouseName]
    .filter(value => value && value !== title)
  return [title, ...details].filter(Boolean).join(' / ')
}

const nodeTooltip = node => [
  nodeTypeText(node.type),
  nodeTitle(node),
  node.productName,
  node.quantityText,
  node.warehouseName,
  node.status
].filter(Boolean).join(' · ')

const traceChains = computed(() => buildTraceChains(traceTimeline.value))
const filteredTraceChains = computed(() => traceChains.value.filter(chain => {
  if (activeChainFilter.value === 'all') return true
  if (activeChainFilter.value === 'main') return chain.kind === 'main'
  if (activeChainFilter.value === 'support') return chain.kind === 'support'
  if (activeChainFilter.value === 'reserved') return chain.kind === 'reserved'
  if (activeChainFilter.value === 'completed') return chain.status === '已完成'
  if (activeChainFilter.value === 'finished') return chain.hasFinishedFlow
  return true
}))
const completedChainCount = computed(() => traceChains.value.filter(item => item.status === '已完成').length)
const reservedChainCount = computed(() => traceChains.value.filter(item => item.kind === 'reserved').length)
const finalInboundRecordCount = computed(() => aggregateTraceTimeline(
  traceTimeline.value.filter(item => item.actionType?.includes('成品入库'))
).length)
const selectedTraceDetailRows = computed(() => {
  const item = selectedTraceDetail.value || {}
  if (selectedTraceDetailKind.value === 'edge') {
    return [
      { label: '动作', value: item.action || item.shortAction },
      { label: '数量', value: item.quantityText },
      { label: '状态', value: item.status },
      { label: '来源节点', value: item.sourceDisplayName || item.source },
      { label: '目标节点', value: item.targetDisplayName || item.target }
    ]
  }
  return [
    { label: '类型', value: nodeTypeText(item.type) },
    { label: '单号/码', value: item.documentNo || item.name || item.title },
    { label: '产品', value: item.productName || item.subtitle },
    { label: '数量', value: item.quantityText },
    { label: '库位', value: item.warehouseName },
    { label: '状态', value: item.status },
    { label: '时间', value: formatDateTime(item.occurredAt) || item.occurredAt }
  ]
})
const selectedTraceActions = computed(() => {
  if (selectedTraceDetailKind.value !== 'node') return []
  return traceNodeActions(selectedTraceDetail.value || {})
})

const nodeColumnKey = type => ({
  BOILING_BATCH_ITEM: 'source',
  PRODUCTION_ORDER: 'semiOrder',
  ACTUAL_OUTPUT: 'actualOutput',
  PALLET_CODE_BATCH: 'codeBatch',
  PALLET_CODE: 'palletLocation',
  INBOUND_STORAGE: 'palletLocation',
  WAREHOUSE_LOCATION: 'palletLocation',
  AUXILIARY_SOURCE: 'downstreamOrder',
  OTHER_SOURCE: 'downstreamOrder',
  DOWNSTREAM_PRODUCTION_ORDER: 'downstreamOrder',
  FINISHED_OUTPUT: 'finished',
  FINISHED_WAREHOUSE_LOCATION: 'finished'
}[type] || 'finished')

const nodeTone = type => ({
  BOILING_BATCH_ITEM: 'source',
  PRODUCTION_ORDER: 'order',
  ACTUAL_OUTPUT: 'output',
  PALLET_CODE_BATCH: 'code',
  PALLET_CODE: 'inventory',
  INBOUND_STORAGE: 'inventory',
  WAREHOUSE_LOCATION: 'inventory',
  AUXILIARY_SOURCE: 'aux',
  OTHER_SOURCE: 'aux',
  DOWNSTREAM_PRODUCTION_ORDER: 'downstream',
  FINISHED_OUTPUT: 'finished',
  FINISHED_WAREHOUSE_LOCATION: 'finished'
}[type] || 'default')

const compactQuery = queryValue => Object.fromEntries(
  Object.entries(queryValue || {}).filter(([, value]) => value !== undefined && value !== null && value !== '')
)

const traceMeta = node => node?.meta || {}
const traceOrderId = node => traceMeta(node).orderId || node?.orderId
const traceNodeCode = node => node?.documentNo || node?.name || node?.title || ''
const traceOrderQuery = node => compactQuery({
  orderId: traceOrderId(node),
  orderNo: node?.documentNo || node?.name,
  productName: node?.productName
})

const traceNodeActions = node => {
  const actions = []
  const type = node?.type
  const orderId = traceOrderId(node)
  const code = traceNodeCode(node)

  if (type === 'PRODUCTION_ORDER' || type === 'DOWNSTREAM_PRODUCTION_ORDER') {
    if (type === 'DOWNSTREAM_PRODUCTION_ORDER' && orderId) {
      actions.push({
        label: '去半成品领用',
        type: 'default',
        to: { path: '/production/material-pick', query: traceOrderQuery(node) }
      })
    }
    if (orderId) {
      actions.push({
        label: '去产出贴码',
        type: 'primary',
        to: { path: '/production/output-bind', query: traceOrderQuery(node) }
      })
    }
    actions.push({
      label: '查看生产订单',
      type: 'default',
      to: { path: '/production/orders', query: compactQuery({ orderNo: node?.documentNo || node?.name }) }
    })
  }

  if (type === 'ACTUAL_OUTPUT' || type === 'FINISHED_OUTPUT') {
    const orderNo = node?.documentNo || ''
    if (orderNo) {
      actions.push({
        label: '查看生产订单',
        type: 'default',
        to: { path: '/production/orders', query: compactQuery({ orderNo }) }
      })
    }
  }

  if (type === 'INBOUND_STORAGE' || type === 'PALLET_CODE') {
    if (code) {
      actions.push({
        label: '查看二维码',
        type: 'default',
        to: { path: '/pallet-code/list', query: compactQuery({ code }) }
      })
    }
    actions.push({
      label: '查看入库任务',
      type: 'primary',
      to: {
        path: '/pallet-task/semi/in',
        query: compactQuery({ codes: code, productName: node?.productName })
      }
    })
  }

  if (type === 'FINISHED_WAREHOUSE_LOCATION') {
    actions.push({
      label: '查看成品入库任务',
      type: 'primary',
      to: {
        path: '/pallet-task/finish/in',
        query: compactQuery({ productName: node?.productName || node?.name })
      }
    })
  }

  return actions
}

const navigateTraceAction = action => {
  if (!action?.to) return
  traceDetailVisible.value = false
  router.push(action.to)
}

const shortEdgeAction = action => {
  const value = action || ''
  if (value.includes('引用')) return '引用'
  if (value.includes('产出')) return '产出'
  if (value.includes('入库')) return '入库'
  if (value.includes('领用')) return '领用'
  if (value.includes('消耗')) return '消耗'
  if (value.includes('生成') || value.includes('核销')) return '生成'
  return value || '流转'
}

const isPendingStatus = status => {
  const value = status || ''
  return value.includes('待') || value.includes('预占') || value.includes('RESERVED')
}

const normalizeTraceEdge = (edge, index) => ({
  ...edge,
  id: edge.id || `edge-${index}`,
  source: edge.source,
  target: edge.target,
  action: edge.action || edge.label || '流转',
  shortAction: shortEdgeAction(edge.action || edge.label),
  quantityText: edge.quantityText || '',
  status: edge.status || '',
  lineType: isPendingStatus(edge.status) ? 'DASHED' : 'SOLID'
})

const statusToneClass = status => {
  const value = status || ''
  if (value.includes('异常') || value.includes('超用') || value.includes('失败')) return 'trace-flow-node-status--danger'
  if (value.includes('预占') || value.includes('待')) return 'trace-flow-node-status--warning'
  if (value.includes('关闭') || value.includes('作废') || value.includes('回收')) return 'trace-flow-node-status--info'
  return 'trace-flow-node-status--success'
}

const zoomIn = () => {
  traceZoom.value = Math.min(1.6, Number((traceZoom.value + 0.1).toFixed(2)))
}

const zoomOut = () => {
  traceZoom.value = Math.max(0.7, Number((traceZoom.value - 0.1).toFixed(2)))
}

const fitTraceCanvas = () => {
  traceZoom.value = 0.85
}

const startTraceDrag = event => {
  const target = event.target
  if (event.button !== 0 || target?.closest?.('.trace-flow-node, .trace-flow-edge-label, .el-button')) return
  const element = traceGraphScrollRef.value
  if (!element) return
  traceDragState.value = {
    dragging: true,
    startX: event.clientX,
    startY: event.clientY,
    scrollLeft: element.scrollLeft,
    scrollTop: element.scrollTop
  }
}

const moveTraceDrag = event => {
  if (!traceDragState.value.dragging) return
  const element = traceGraphScrollRef.value
  if (!element) return
  event.preventDefault()
  element.scrollLeft = traceDragState.value.scrollLeft - (event.clientX - traceDragState.value.startX)
  element.scrollTop = traceDragState.value.scrollTop - (event.clientY - traceDragState.value.startY)
}

const stopTraceDrag = () => {
  traceDragState.value.dragging = false
}

const openNodeDetail = node => {
  selectedTraceDetailKind.value = 'node'
  selectedTraceDetail.value = node.raw || node
  traceDetailTitle.value = '节点详情'
  traceDetailVisible.value = true
}

const openEdgeDetail = edge => {
  selectedTraceDetailKind.value = 'edge'
  selectedTraceDetail.value = edge
  traceDetailTitle.value = '动作详情'
  traceDetailVisible.value = true
}

const recordKey = record => [
  record.occurredAt || '',
  record.actionType || '',
  record.documentNo || '',
  record.productName || '',
  record.quantityText || '',
  record.warehouseName || '',
  record.relatedObject || '',
  record.status || ''
].join('|')

const uniqueRecords = records => {
  const map = new Map()
  records.filter(Boolean).forEach(record => {
    const key = record.key || recordKey(record)
    if (!map.has(key)) {
      map.set(key, { ...record, key })
    }
  })
  return [...map.values()].sort((left, right) => String(left.occurredAt || '').localeCompare(String(right.occurredAt || '')))
}

const chainStatusType = status => status === '已完成' ? 'success' : status === '预占' ? 'warning' : 'info'
const isAuxiliaryRecord = record => record.actionType?.includes('辅料') || record.actionType?.includes('其他来源')
const isConsumeRecord = record => record.actionType?.includes('半成品领用') || record.actionType?.includes('消耗')

const groupBy = (items, keyGetter) => {
  const groups = new Map()
  items.forEach((item, index) => {
    const key = keyGetter(item, index) || `group-${index}`
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(item)
  })
  return groups
}

const buildTraceChains = records => {
  const sorted = uniqueRecords(records || [])
  if (!sorted.length) return []

  const sourceRows = sorted.filter(item => item.actionType?.includes('引用煮糖批次'))
  const semiRows = sorted.filter(item => item.actionType?.includes('半成品产出') || item.actionType?.includes('半成品入库'))
  const consumeRows = sorted.filter(isConsumeRecord)
  const auxiliaryRows = sorted.filter(isAuxiliaryRecord)
  const chains = []

  Array.from(groupBy(consumeRows, item => item.documentNo || item.relatedObject).entries()).forEach(([downstreamOrderNo, rows], index) => {
    const finishedRows = sorted.filter(item =>
      item.documentNo === downstreamOrderNo
      || item.relatedObject === downstreamOrderNo
      || (item.actionType?.includes('成品') && item.documentNo === downstreamOrderNo)
    )
    const auxiliaryForOrder = auxiliaryRows.filter(item => item.documentNo === downstreamOrderNo || item.relatedObject === downstreamOrderNo)
    const chainRecords = uniqueRecords([...sourceRows, ...semiRows, ...rows, ...auxiliaryForOrder, ...finishedRows])
    chains.push(createTraceChain(chainRecords, index === 0 ? 'main' : 'support', index))
  })

  if (!chains.length) {
    sourceRows.forEach((sourceRow, index) => {
      const kind = sourceRow.status?.includes('预占') ? 'reserved' : index === 0 ? 'main' : 'support'
      chains.push(createTraceChain(uniqueRecords([sourceRow, ...semiRows.filter(item => item.documentNo === sourceRow.documentNo || item.relatedObject === sourceRow.documentNo)]), kind, index))
    })
  }

  if (!chains.length) {
    chains.push(createTraceChain(sorted, 'main', 0))
  }

  return chains
}

const createTraceChain = (records, kind, index) => {
  const finalInbound = [...records].reverse().find(item => item.actionType?.includes('成品入库') || item.actionType?.includes('入库'))
  const finalProduct = [...records].reverse().find(item => item.actionType?.includes('成品产出') || item.actionType?.includes('成品入库'))
  const consumeRecords = records.filter(isConsumeRecord)
  const consume = consumeRecords[0] || records.find(item => item.actionType?.includes('引用煮糖批次'))
  const consumeText = [...new Set(consumeRecords.map(item => item.quantityText).filter(Boolean))].join('；') || consume?.quantityText || '-'
  const reserved = records.some(item => item.status?.includes('预占'))
  const completed = records.some(item => item.actionType?.includes('成品入库')) || records.some(item => item.status?.includes('完成'))
  const actualKind = reserved && !completed ? 'reserved' : kind
  const prefix = actualKind === 'reserved' ? '预占链路' : actualKind === 'support' ? '支链路' : '主链路'
  const suffix = String.fromCharCode(65 + index)
  const groups = aggregateTraceTimeline(records)
  return {
    id: `chain-${actualKind}-${index}`,
    name: `${prefix} ${suffix}`,
    kind: actualKind,
    sourceBatch: detail.value?.batchNo || '-',
    consumeText,
    finalWarehouse: finalInbound?.warehouseName || '-',
    finalProduct: finalProduct ? [finalProduct.productName, finalProduct.quantityText].filter(Boolean).join(' ') : '-',
    status: completed ? '已完成' : actualKind === 'reserved' ? '预占' : '流转中',
    hasFinishedFlow: records.some(item => item.actionType?.includes('成品')),
    groups,
    actionCount: new Set(records.map(item => item.actionType).filter(Boolean)).size,
    detailCount: records.length,
    aggregatedGroupCount: groups.filter(group => group.count > 1).length,
    records
  }
}

const chainFilterCount = filter => {
  if (filter === 'all') return traceChains.value.length
  if (filter === 'main') return traceChains.value.filter(item => item.kind === 'main').length
  if (filter === 'support') return traceChains.value.filter(item => item.kind === 'support').length
  if (filter === 'reserved') return traceChains.value.filter(item => item.kind === 'reserved').length
  if (filter === 'completed') return traceChains.value.filter(item => item.status === '已完成').length
  if (filter === 'finished') return traceChains.value.filter(item => item.hasFinishedFlow).length
  return 0
}

const resetExpandedChains = () => {
  expandedChainNames.value = traceChains.value.slice(0, 2).map(item => item.id)
}

const normalizeTraceGraph = value => ({
  ...(value || {}),
  nodes: Array.isArray(value?.nodes) ? value.nodes : [],
  edges: Array.isArray(value?.edges) ? value.edges : [],
  timeline: Array.isArray(value?.timeline) ? value.timeline : []
})

const refreshTrace = async () => {
  if (!detail.value?.id) return
  detailLoading.value = true
  try {
    const traceRes = await getBoilingBatchTraceGraph(detail.value.id)
    trace.value = normalizeTraceGraph(traceRes.data)
    resetExpandedChains()
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
    const [detailRes, traceRes] = await Promise.all([getBoilingBatchDetail(row.id), getBoilingBatchTraceGraph(row.id)])
    detail.value = detailRes.data
    trace.value = normalizeTraceGraph(traceRes.data)
    activeTraceTab.value = 'graph'
    activeChainFilter.value = 'all'
    traceZoom.value = 1
    showRawTimeline.value = false
    resetExpandedChains()
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
  z-index: 3;
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

.detail-body {
  gap: 18px;
  padding: 4px 20px 24px;
  background: #f6f8fb;
}
.summary-grid {
  gap: 14px;
}
.summary-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 74px;
  border: 1px solid #e5ebf3;
  border-radius: 8px;
  padding: 14px 16px;
  background: #fff;
  box-shadow: 0 8px 20px rgba(26, 45, 75, 0.04);
}
.summary-icon {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  border-radius: 50%;
  font-size: 17px;
}
.summary-icon--blue { color: #2563eb; background: #eaf1ff; }
.summary-icon--cyan { color: #078698; background: #e8f7fa; }
.summary-icon--purple { color: #6b55d7; background: #f0edff; }
.summary-icon--orange,
.summary-icon--amber { color: #c56a16; background: #fff4e3; }
.summary-icon--green { color: #209653; background: #eaf8ef; }
.summary-content {
  min-width: 0;
}
.summary-content span {
  display: block;
  color: #7b8797;
  font-size: 12px;
  line-height: 18px;
}
.summary-content strong {
  display: block;
  color: #18253a;
  font-size: 17px;
  line-height: 24px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.usage-section {
  padding: 14px 16px 16px;
  border: 1px solid #e6ebf2;
  border-radius: 8px;
  background: #fff;
}
.usage-section :deep(.el-table__cell) {
  padding-top: 6px;
  padding-bottom: 6px;
}
.trace-card {
  padding: 16px 18px 18px;
  border: 1px solid #e1e7f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(26, 45, 75, 0.05);
}
.trace-tab-hint {
  margin-bottom: 12px;
  color: #758299;
  font-size: 13px;
}
.trace-graph-panel {
  border-color: #dde6f1;
  border-radius: 8px;
}
.trace-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  border-bottom: 1px solid #e6edf6;
  background: #f8fbff;
}
.trace-toolbar-stats,
.trace-toolbar-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.trace-stat {
  min-width: 112px;
  padding: 8px 12px;
  border: 1px solid #e4ebf4;
  border-radius: 6px;
  background: #fff;
}
.trace-stat span {
  color: #7a8798;
  font-size: 12px;
}
.trace-stat strong {
  margin-left: 8px;
  color: #21314b;
  font-size: 18px;
}
.zoom-value {
  min-width: 64px;
  pointer-events: none;
}
.trace-graph-scroll {
  min-height: 460px;
  max-height: 62vh;
  cursor: grab;
  user-select: none;
  background:
    radial-gradient(circle, rgba(132, 151, 176, 0.1) 1px, transparent 1.5px),
    linear-gradient(#f7f9fc 1px, transparent 1px),
    linear-gradient(90deg, #f7f9fc 1px, transparent 1px);
  background-size: 18px 18px, 36px 36px, 36px 36px;
}
.trace-graph-scroll--dragging {
  cursor: grabbing;
}
.trace-zoom-surface {
  position: relative;
}
.trace-flow-canvas {
  min-height: 440px;
  transform-origin: left top;
}
.trace-stage {
  box-sizing: border-box;
  border-left: 0;
  border-right: 1px solid rgba(212, 222, 235, 0.88);
  background: rgba(255, 255, 255, 0.28);
}
.trace-stage:first-of-type {
  border-left: 1px solid rgba(212, 222, 235, 0.88);
}
.trace-stage-title {
  box-sizing: border-box;
  width: 100%;
  height: 48px;
  padding: 0 8px;
}
.trace-flow-lines {
  z-index: 1;
  pointer-events: auto;
}
.trace-flow-path {
  stroke-width: 1.7;
  cursor: pointer;
}
.trace-flow-path-hit {
  fill: none;
  stroke: transparent;
  stroke-width: 14;
  cursor: pointer;
}
.trace-flow-path--dashed {
  stroke: #a9b6c8;
  stroke-dasharray: 6 6;
  opacity: 0.74;
}
.trace-edge-layer--auxiliary {
  opacity: 0.95;
}
.trace-flow-path--auxiliary {
  stroke: #8fa2bf;
  stroke-width: 1.7;
  stroke-dasharray: 7 5;
}
.trace-edge-layer--auxiliary .trace-flow-edge-label {
  border-color: #dfe6ef;
  background: rgba(247, 249, 252, 0.96);
  color: #7b889b;
  box-shadow: none;
}
.trace-edge-foreign {
  pointer-events: auto;
}
.trace-flow-edge-label {
  min-height: 22px;
  padding: 2px 6px;
  border-radius: 999px;
  font-size: 9px;
  line-height: 14px;
  cursor: pointer;
}
.trace-flow-node {
  height: 84px;
  gap: 3px;
  padding: 8px 10px;
  border-radius: 8px;
  box-shadow: 0 6px 16px rgba(31, 45, 61, 0.08);
  cursor: pointer;
  transition: box-shadow 0.16s ease, transform 0.16s ease;
}
.trace-flow-node:hover {
  box-shadow: 0 10px 24px rgba(31, 45, 61, 0.14);
  transform: translateY(-1px);
}
.trace-flow-node-title {
  font-size: 12px;
  line-height: 16px;
  white-space: normal;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.trace-flow-node-type,
.trace-flow-node-detail {
  font-size: 9px;
  line-height: 13px;
}
.trace-flow-node-status {
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 9px;
  line-height: 14px;
}
.trace-flow-node-status--success { background: #e9f8ef; color: #23884e; }
.trace-flow-node-status--warning { background: #fff5e6; color: #b86913; }
.trace-flow-node-status--info { background: #eef2f6; color: #66758b; }
.trace-flow-node-status--danger { background: #fff0f0; color: #c43d3d; }
.trace-flow-node--source { border-color: #f2a23a; background: #fff9ef; }
.trace-flow-node--order { border-color: #5b8ff9; background: #f4f7ff; }
.trace-flow-node--output { border-color: #63b45c; background: #f4fbf2; }
.trace-flow-node--code { border-color: #38a6a5; background: #f1fafa; }
.trace-flow-node--inventory { border-color: #7d6ae8; background: #f7f5ff; }
.trace-flow-node--downstream { border-color: #b15ac7; background: #fbf4ff; }
.trace-flow-node--finished { border-color: #df7398; background: #fff5f8; }
.trace-flow-node--aux { border-color: #7c9de8; background: #f4f7ff; }
.chain-filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.chain-stat-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: #66758b;
  font-size: 12px;
}
.chain-stat-strip strong {
  color: #1f2f46;
}
.chain-list :deep(.el-collapse) {
  border-top: 0;
  border-bottom: 0;
}
.chain-list :deep(.el-collapse-item) {
  margin-bottom: 10px;
  border: 1px solid #e3eaf3;
  border-radius: 8px;
  overflow: hidden;
}
.chain-list :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 74px;
  padding: 0 12px 0 0;
  border-bottom: 1px solid #edf1f6;
}
.chain-list :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
}
.chain-title {
  width: 100%;
  display: flex;
  align-items: stretch;
  min-width: 0;
}
.chain-mark {
  width: 4px;
  flex: none;
  background: #3f7ee8;
}
.chain-title--support .chain-mark { background: #4f9ed7; }
.chain-title--reserved .chain-mark { background: #e99a2d; }
.chain-main {
  min-width: 0;
  padding: 12px 14px;
}
.chain-name {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 5px;
}
.chain-name strong {
  color: #1f2f46;
  font-size: 14px;
}
.chain-summary {
  color: #68778b;
  font-size: 12px;
  line-height: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chain-metrics {
  margin-top: 3px;
  color: #8a96a7;
  font-size: 11px;
  line-height: 16px;
}
.chain-timeline-groups {
  padding: 10px 14px 14px;
  overflow-x: auto;
}
.chain-timeline-head {
  min-width: 1160px;
  display: grid;
  grid-template-columns: 132px 126px minmax(140px, 1.1fr) minmax(120px, 1fr) minmax(150px, 1.2fr) 92px minmax(132px, 1fr) 88px 78px 24px;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  padding: 0 10px 0 28px;
  border-bottom: 1px solid #e1e8f2;
  border-radius: 4px 4px 0 0;
  background: #f1f5fb;
  color: #637188;
  font-size: 12px;
  font-weight: 600;
}
.chain-record {
  position: relative;
  display: grid;
  grid-template-columns: 132px 126px minmax(120px, 1.1fr) minmax(110px, 1fr) 100px 86px minmax(120px, 1fr) 88px;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 8px 10px 8px 22px;
  border-bottom: 1px solid #edf1f6;
  color: #34445c;
  font-size: 12px;
}
.chain-record:last-child {
  border-bottom: 0;
}
.chain-record::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 0;
  bottom: 0;
  width: 1px;
  background: #d9e2ee;
}
.chain-dot {
  position: absolute;
  left: 3px;
  top: 17px;
  width: 9px;
  height: 9px;
  border: 2px solid #5c8ee8;
  border-radius: 50%;
  background: #fff;
  z-index: 1;
}
.chain-record--aux {
  border-radius: 6px;
  background: #f5f7ff;
}
.chain-action {
  font-weight: 600;
  color: #263a57;
}
.chain-time,
.chain-warehouse,
.chain-related {
  color: #6f7e92;
}
.chain-doc,
.chain-product,
.chain-qty,
.chain-related {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.raw-table-toggle {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
.trace-detail-dialog {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.trace-detail-row {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  padding-bottom: 8px;
  border-bottom: 1px solid #edf1f6;
}
.trace-detail-row:last-child {
  border-bottom: 0;
}
.trace-detail-row span {
  color: #7a8798;
}
.trace-detail-row strong {
  min-width: 0;
  color: #263a57;
  font-weight: 600;
  word-break: break-all;
}
.trace-detail-actions {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  padding-top: 4px;
}
.trace-detail-actions > span {
  color: #7a8798;
}
.trace-detail-action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.trace-detail-action-buttons .el-button + .el-button {
  margin-left: 0;
}
@media (max-width: 1100px) {
  .trace-toolbar,
  .chain-filter-bar {
    align-items: flex-start;
    flex-direction: column;
  }
  .chain-record {
    grid-template-columns: 112px 112px minmax(110px, 1fr) minmax(100px, 1fr);
  }
}
</style>
