<template>
  <div class="operation-logs">
    <el-card class="search-card" style="max-width: 1500px">
      <div class="page-header">
        <div>
          <div class="page-title">{{ title }}</div>
          <div v-if="description" class="page-subtitle">{{ description }}</div>
        </div>
      </div>
      <el-form :model="searchForm" inline>
        <el-form-item v-if="hasSearchField('code')" label="托盘码">
          <el-input v-model="searchForm.code" clearable placeholder="请输入托盘码" style="width: 170px"/>
        </el-form-item>
        <el-form-item v-if="hasSearchField('taskType')" label="任务类型">
          <el-select v-model="searchForm.taskType" clearable placeholder="请选择" style="width: 160px">
            <el-option v-for="item in taskTypeOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="hasSearchField('bizScene')" label="业务场景">
          <el-select v-model="searchForm.bizScene" clearable placeholder="请选择" style="width: 180px">
            <el-option v-for="item in bizSceneOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="hasSearchField('status')" label="任务状态">
          <el-select v-model="searchForm.status" clearable placeholder="请选择" style="width: 140px">
            <el-option v-for="item in taskStatusOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="hasSearchField('productName')" label="产品名称">
          <el-input v-model="searchForm.productName" clearable placeholder="请输入产品名称" style="width: 170px"/>
        </el-form-item>
        <el-form-item v-if="hasSearchField('targetWarehouseName')" label="目标仓库">
          <el-input v-model="searchForm.targetWarehouseName" clearable placeholder="请输入目标仓库" style="width: 170px"/>
        </el-form-item>
        <el-form-item v-if="hasSearchField('productType')" label="产品类型">
          <el-select v-model="searchForm.productType" clearable placeholder="请选择" style="width: 140px">
            <el-option v-for="item in PRODUCT_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="hasSearchField('productStatus')" label="产品状态">
          <el-select v-model="searchForm.productStatus" clearable placeholder="请选择" style="width: 140px">
            <el-option v-for="item in PRODUCT_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item v-if="hasSearchField('productionDate')" label="生产日期">
          <el-date-picker
              v-model="searchForm.productionDateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" style="max-width: 1500px">
      <div v-if="topActions.length || batchActions.length || bizSceneTabs.length" class="table-toolbar">
        <div class="table-toolbar-main">
          <el-tabs v-if="bizSceneTabs.length" v-model="activeBizScene" @tab-change="handleBizSceneTabChange">
            <el-tab-pane v-for="tab in bizSceneTabs" :key="tab.value" :label="tab.label" :name="tab.value"/>
          </el-tabs>
          <div class="action-groups">
            <el-button v-if="hasTopAction('bindPallet')" type="primary" size="small" @click="goBindPallet">绑定托盘</el-button>
            <el-button v-if="hasTopAction('semiOutCreate')" type="primary" size="small" @click="openCommonDialog('semiOutCreate')">{{ getActionLabel('semiOutCreate', '创建普通出库任务') }}</el-button>
            <el-button v-if="hasTopAction('semiPrepareCreate')" type="primary" size="small" @click="openCommonDialog('semiPrepareCreate')">{{ getActionLabel('semiPrepareCreate', '创建转入备料池任务') }}</el-button>
            <el-button v-if="hasTopAction('finishOutCreate')" type="primary" size="small" @click="openCommonDialog('finishOutCreate')">创建成品出库任务</el-button>
            <el-button v-if="hasTopAction('transferCreate')" type="primary" size="small" @click="openCommonDialog('transferCreate')">创建调拨任务</el-button>
            <el-button v-if="hasBatchAction('confirmIn')" type="success" size="small" @click="openBatchConfirmInDialog">批量确认入库</el-button>
            <el-button v-if="hasBatchAction('semiOutConfirm')" type="success" size="small" @click="openBatchCodeDialog('semiOutConfirm')">{{ getActionLabel('semiOutConfirm', '批量确认普通出库') }}</el-button>
            <el-button v-if="hasBatchAction('semiPrepareConfirm')" type="success" size="small" @click="openBatchCodeDialog('semiPrepareConfirm')">{{ getActionLabel('semiPrepareConfirm', '批量确认转入备料池') }}</el-button>
            <el-button v-if="hasBatchAction('finishOutConfirm')" type="success" size="small" @click="openBatchCodeDialog('finishOutConfirm')">批量确认成品出库</el-button>
            <el-button v-if="hasBatchAction('transferConfirm')" type="success" size="small" @click="openBatchCodeDialog('transferConfirm')">批量确认调拨</el-button>
            <el-button v-if="hasBatchAction('cancel')" type="danger" size="small" @click="batchCancelTasks">{{ getActionLabel('cancel', '批量取消任务') }}</el-button>
          </div>
        </div>
      </div>
      <el-table :data="resultList" style="width: 100%" height="580" stripe v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45"/>
        <el-table-column prop="code" label="托盘码" width="130" fixed="left"/>
        <el-table-column v-if="hasColumn('taskType')" prop="taskType" label="任务类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getDictType(TASK_TYPE_MAP, row.taskType)">
              {{ getDictLabel(TASK_TYPE_MAP, row.taskType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="hasColumn('bizScene')" prop="bizScene" label="业务场景" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.bizScene" :type="getDictType(BIZ_SCENE_MAP, row.bizScene)">
              {{ getDictLabel(BIZ_SCENE_MAP, row.bizScene) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="taskStatus" label="任务状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getDictType(TASK_STATUS_MAP, row.taskStatus)">
              {{ getDictLabel(TASK_STATUS_MAP, row.taskStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="产品名称" min-width="150"/>
        <el-table-column prop="productType" label="产品类型" width="100"/>
        <el-table-column v-if="hasColumn('productStatus')" prop="productStatus" label="产品状态" width="100"/>
        <el-table-column prop="productionDate" label="生产日期" width="120"/>
        <el-table-column v-if="hasColumn('targetWarehouse')" prop="targetWarehouseName" label="目标仓库" width="130"/>
        <el-table-column v-if="hasColumn('targetWarehouse')" prop="targetSide" label="目标侧" width="80"/>
        <el-table-column v-if="hasColumn('semiItemCount')" prop="semiItemCount" label="半成品数" width="100"/>
        <el-table-column v-if="hasColumn('hasSemiItems')" label="已绑定半成品" width="120">
          <template #default="{ row }">
            <el-tag :type="row.semiItemCount > 0 ? 'success' : 'info'">
              {{ row.semiItemCount > 0 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="100"/>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="confirmedBy" label="确认人" width="100"/>
        <el-table-column prop="confirmedAt" label="确认时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.confirmedAt) }}</template>
        </el-table-column>
        <el-table-column v-if="rowActions.length" label="操作" :width="actionColumnWidth" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canBindSemi(row)" type="primary" size="small" @click="openSemiBindDialog(row)">绑定半成品</el-button>
            <el-button v-if="canConfirmIn(row)" type="success" size="small" @click="openConfirmInDialog(row)">确认入库</el-button>
            <el-button v-if="canConfirmSemiDirectOut(row)" type="success" size="small" @click="confirmRowCodes(row, 'semiOutConfirm')">确认普通出库</el-button>
            <el-button v-if="canConfirmSemiPrepare(row)" type="success" size="small" @click="confirmRowCodes(row, 'semiPrepareConfirm')">确认备料池</el-button>
            <el-button v-if="canConfirmFinishOut(row)" type="success" size="small" @click="confirmRowCodes(row, 'finishOutConfirm')">确认成品出库</el-button>
            <el-button v-if="canConfirmTransfer(row)" type="success" size="small" @click="confirmRowCodes(row, 'transferConfirm')">确认调拨</el-button>
            <el-button v-if="canCancel(row)" type="danger" size="small" @click="cancelRowTask(row)">取消任务</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="total"
            :current-page="currentPage"
            :page-size="pageSize"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog title="绑定半成品" v-model="semiBindDialogVisible" width="760px" :before-close="closeSemiBindDialog">
      <el-form :model="semiBindForm" label-width="110px">
        <el-form-item label="成品托盘码">
          <el-input v-model="semiBindForm.code" disabled/>
        </el-form-item>
        <el-table :data="semiBindForm.items" border>
          <el-table-column label="半成品托盘码" min-width="170">
            <template #default="{ row }">
              <el-input v-model="row.semiPalletCode" placeholder="请输入半成品托盘码" @blur="resolveSemiItemProduct(row)"/>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="120">
            <template #default="{ row }">
              <el-input-number
                  v-model="row.quantity"
                  :min="1"
                  :max="getSemiItemQuantityMax(row)"
                  :disabled="!canEditQuantity(row)"
                  controls-position="right"
                  style="width: 100%"
                  @change="normalizeSemiItemQuantity(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="100">
            <template #default="{ row }">
              <el-select v-model="row.unit" @change="normalizeSemiItemQuantity(row)">
                <el-option v-for="item in UNIT_OPTIONS" :key="item.value" :label="item.label" :value="item.value"/>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="每板上限" width="100">
            <template #default="{ row }">
              {{ getSemiItemPiecesLimit(row) || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="套用化验" width="110">
            <template #default="{ row }">
              <el-switch v-model="row.useAssay"/>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ $index }">
              <el-button type="danger" size="small" @click="removeSemiItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button class="table-add-btn" type="primary" plain @click="addSemiItem">新增半成品</el-button>
      </el-form>
      <template #footer>
        <el-button @click="closeSemiBindDialog">取消</el-button>
        <el-button type="primary" @click="submitSemiBind">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog title="确认入库" v-model="confirmInDialogVisible" width="560px" :before-close="closeConfirmInDialog">
      <el-form ref="confirmInFormRef" :model="confirmInForm" :rules="confirmInRules" label-width="110px">
        <el-form-item label="托盘码" prop="code">
          <el-input v-model="confirmInForm.code" disabled/>
        </el-form-item>
        <el-form-item label="入库仓库" prop="warehouseName">
          <el-select v-model="confirmInForm.warehouseName" filterable allow-create default-first-option placeholder="请选择或输入仓库" style="width: 100%">
            <el-option v-for="item in warehouseList" :key="item.id" :label="item.warehouseName" :value="item.warehouseName"/>
          </el-select>
        </el-form-item>
        <el-form-item label="入库日期">
          <el-date-picker v-model="confirmInForm.entryDate" value-format="YYYY-MM-DD" type="date" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="侧">
          <el-radio-group v-model="confirmInForm.side">
            <el-radio v-for="item in SIDE_OPTIONS" :key="item.value" :label="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="单位">
          <el-radio-group v-model="confirmInForm.unit" @change="handleConfirmInUnitChange">
            <el-radio v-for="item in UNIT_OPTIONS" :key="item.value" :label="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number
              v-model="confirmInForm.quantity"
              :min="1"
              :max="confirmInQuantityMax"
              :disabled="confirmInForm.unit === '0'"
              controls-position="right"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="confirmInForm.remark" type="textarea" :rows="2"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeConfirmInDialog">取消</el-button>
        <el-button type="primary" @click="submitConfirmIn">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog title="批量确认入库" v-model="batchConfirmInDialogVisible" width="1180px" :before-close="closeBatchConfirmInDialog">
      <el-table :data="batchConfirmInRows" border max-height="520">
        <el-table-column prop="code" label="托盘码" width="150" fixed="left"/>
        <el-table-column prop="productName" label="产品" min-width="150"/>
        <el-table-column label="入库仓库" min-width="170">
          <template #default="{ row }">
            <el-select v-model="row.warehouseName" filterable allow-create default-first-option placeholder="仓库" style="width: 100%">
              <el-option v-for="item in warehouseList" :key="item.id" :label="item.warehouseName" :value="item.warehouseName"/>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="入库日期" width="155">
          <template #default="{ row }">
            <el-date-picker v-model="row.entryDate" value-format="YYYY-MM-DD" type="date" style="width: 100%"/>
          </template>
        </el-table-column>
        <el-table-column label="侧" width="95">
          <template #default="{ row }">
            <el-select v-model="row.side">
              <el-option v-for="item in SIDE_OPTIONS" :key="item.value" :label="item.label" :value="item.value"/>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="95">
          <template #default="{ row }">
            <el-select v-model="row.unit" @change="normalizeQuantityByUnit(row)">
              <el-option v-for="item in UNIT_OPTIONS" :key="item.value" :label="item.label" :value="item.value"/>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="125">
          <template #default="{ row }">
            <el-input-number
                v-model="row.quantity"
                :min="1"
                :max="getQuantityMax(row)"
                :disabled="!canEditQuantity(row)"
                controls-position="right"
                style="width: 100%"
                @change="normalizeQuantityByUnit(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="每板上限" width="95">
          <template #default="{ row }">
            {{ getPiecesLimitByProductId(row.productId) || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.remark" placeholder="备注"/>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="closeBatchConfirmInDialog">取消</el-button>
        <el-button type="primary" @click="submitBatchConfirmIn">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog :title="batchCodeOperation?.title" v-model="batchCodeDialogVisible" width="720px" :before-close="closeBatchCodeDialog">
      <el-table :data="batchCodeRows" border max-height="360">
        <el-table-column prop="code" label="托盘码" width="160"/>
        <el-table-column prop="productName" label="产品" min-width="150"/>
        <el-table-column prop="bizScene" label="业务场景" width="150">
          <template #default="{ row }">
            {{ getDictLabel(BIZ_SCENE_MAP, row.bizScene) }}
          </template>
        </el-table-column>
        <el-table-column prop="taskStatus" label="任务状态" width="120">
          <template #default="{ row }">
            {{ getDictLabel(TASK_STATUS_MAP, row.taskStatus) }}
          </template>
        </el-table-column>
      </el-table>
      <el-form label-width="80px" class="batch-remark-form">
        <el-form-item label="备注">
          <el-input v-model="batchCodeRemark" type="textarea" :rows="2"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeBatchCodeDialog">取消</el-button>
        <el-button type="primary" @click="submitBatchCodeOperation">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog :title="commonOperation?.title" v-model="commonDialogVisible" width="720px" :before-close="closeCommonDialog">
      <template v-if="commonOperation?.mode === 'codes'">
        <el-form :model="commonForm" label-width="110px">
          <el-form-item label="托盘码" required>
            <el-input
                v-model="commonForm.codesText"
                type="textarea"
                :rows="6"
                placeholder="支持换行、逗号或空格分隔多个托盘码"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="commonForm.remark" type="textarea" :rows="2"/>
          </el-form-item>
        </el-form>
      </template>
      <template v-else>
        <el-table :data="transferForm.items" border>
          <el-table-column label="托盘码" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.code" placeholder="托盘码"/>
            </template>
          </el-table-column>
          <el-table-column label="目标仓库" min-width="150">
            <template #default="{ row }">
              <el-select v-model="row.targetWarehouseName" filterable allow-create default-first-option placeholder="仓库" style="width: 100%">
                <el-option v-for="item in warehouseList" :key="item.id" :label="item.warehouseName" :value="item.warehouseName"/>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="目标侧" width="100">
            <template #default="{ row }">
              <el-select v-model="row.targetSide">
                <el-option v-for="item in SIDE_OPTIONS" :key="item.value" :label="item.label" :value="item.value"/>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.remark" placeholder="备注"/>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ $index }">
              <el-button type="danger" size="small" @click="removeTransferItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button class="table-add-btn" type="primary" plain @click="addTransferItem">新增调拨托盘</el-button>
      </template>
      <template #footer>
        <el-button @click="closeCommonDialog">取消</el-button>
        <el-button type="primary" @click="submitCommonOperation">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {computed, onMounted, ref, watch} from 'vue'
import dayjs from 'dayjs'
import {ElMessage, ElMessageBox} from 'element-plus'
import {useRouter} from 'vue-router'
import {getProductList} from '@/api/product'
import {getWarehouse} from '@/api/warehouse'
import {formatDateTime} from '@/utils/dateTime'
import {
  bindSemiItemsToTask,
  cancelPalletTasks,
  confirmFinishOutTasks,
  confirmPalletInBatch,
  confirmSemiOutTasks,
  confirmSemiPrepareTasks,
  confirmTransferTasks,
  createFinishOutTasks,
  createSemiOutTasks,
  createSemiPrepareTasks,
  createTransferTasks,
  pagePalletTasks,
  parsePalletCode
} from '@/api/palletCode'
import {
  BIZ_SCENE_MAP,
  PRODUCT_STATUS_OPTIONS,
  PRODUCT_TYPE_OPTIONS,
  SIDE_OPTIONS,
  TASK_STATUS_MAP,
  TASK_TYPE_MAP,
  UNIT_OPTIONS,
  bizSceneOptions,
  getDictLabel,
  getDictType,
  taskStatusOptions,
  taskTypeOptions
} from '@/utils/palletCodeDict'

const props = defineProps({
  title: {
    type: String,
    default: '托盘任务中心'
  },
  description: {
    type: String,
    default: ''
  },
  defaultQuery: {
    type: Object,
    default: () => ({})
  },
  searchFields: {
    type: Array,
    default: () => ['code', 'taskType', 'bizScene', 'status', 'productName', 'productType', 'productStatus', 'productionDate']
  },
  columns: {
    type: Array,
    default: () => ['taskType', 'bizScene', 'productStatus', 'targetWarehouse', 'semiItemCount']
  },
  topActions: {
    type: Array,
    default: () => []
  },
  rowActions: {
    type: Array,
    default: () => []
  },
  batchActions: {
    type: Array,
    default: () => []
  },
  tabActionMap: {
    type: Object,
    default: () => ({})
  },
  bizSceneTabs: {
    type: Array,
    default: () => []
  }
})

const router = useRouter()
const activeBizScene = ref(props.bizSceneTabs[0]?.value || props.defaultQuery.bizScene || '')
const title = computed(() => props.title)
const description = computed(() => props.description)
const activeTabActionConfig = computed(() => props.tabActionMap[activeBizScene.value] || {})
const topActions = computed(() => activeTabActionConfig.value.topActions || props.topActions)
const batchActions = computed(() => activeTabActionConfig.value.batchActions || props.batchActions)
const rowActions = computed(() => props.rowActions)
const bizSceneTabs = computed(() => props.bizSceneTabs)
const searchForm = ref(defaultSearchForm())
const resultList = ref([])
const warehouseList = ref([])
const productList = ref([])
const productMap = computed(() => {
  const map = {}
  productList.value.forEach(item => {
    map[item.id] = item
  })
  return map
})
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const semiBindDialogVisible = ref(false)
const semiBindForm = ref({code: '', items: [defaultSemiItem()]})

const confirmInDialogVisible = ref(false)
const confirmInFormRef = ref(null)
const confirmInForm = ref(defaultConfirmInForm())
const confirmInProduct = ref(null)
const confirmInRules = {
  code: [{required: true, message: '缺少托盘码', trigger: 'blur'}],
  warehouseName: [{required: true, message: '请选择入库仓库', trigger: 'change'}]
}
const confirmInPiecesLimit = computed(() => {
  const value = Number(confirmInProduct.value?.piecesPerPallet)
  return Number.isFinite(value) && value > 0 ? value : null
})
const confirmInQuantityMax = computed(() => confirmInForm.value.unit === '0' ? 1 : (confirmInPiecesLimit.value || undefined))

const commonDialogVisible = ref(false)
const currentOperationKey = ref('')
const commonForm = ref({codesText: '', remark: ''})
const transferForm = ref({items: [defaultTransferItem()]})

const commonOperations = {
  semiOutCreate: {title: '创建半成品普通出库任务', mode: 'codes', api: createSemiOutTasks},
  semiOutConfirm: {title: '确认半成品普通出库', mode: 'codes', api: confirmSemiOutTasks},
  semiPrepareCreate: {title: '创建转入备料池任务', mode: 'codes', api: createSemiPrepareTasks},
  semiPrepareConfirm: {title: '确认转入备料池', mode: 'codes', api: confirmSemiPrepareTasks},
  finishOutCreate: {title: '创建成品出库任务', mode: 'codes', api: createFinishOutTasks},
  finishOutConfirm: {title: '确认成品出库', mode: 'codes', api: confirmFinishOutTasks},
  transferCreate: {title: '创建调拨任务', mode: 'transfer', api: createTransferTasks},
  transferConfirm: {title: '确认调拨', mode: 'codes', api: confirmTransferTasks}
}
const commonOperation = computed(() => commonOperations[currentOperationKey.value])
const actionColumnWidth = computed(() => props.rowActions.length > 2 ? 430 : 260)
const selectedRows = ref([])
const batchConfirmInDialogVisible = ref(false)
const batchConfirmInRows = ref([])
const batchCodeDialogVisible = ref(false)
const batchCodeOperationKey = ref('')
const batchCodeRows = ref([])
const batchCodeRemark = ref('')
const batchCodeOperation = computed(() => commonOperations[batchCodeOperationKey.value])

function defaultSearchForm() {
  return {
    code: '',
    taskType: props.defaultQuery.taskType || '',
    bizScene: props.defaultQuery.bizScene || '',
    status: '',
    productName: '',
    targetWarehouseName: '',
    productType: '',
    productStatus: props.defaultQuery.productStatus || '',
    productionDateRange: []
  }
}

function defaultSemiItem() {
  return {
    semiPalletCode: '',
    semiProductId: null,
    semiProductName: '',
    quantity: 1,
    unit: '0',
    useAssay: false
  }
}

function defaultConfirmInForm() {
  return {
    code: '',
    warehouseName: '',
    entryDate: dayjs().format('YYYY-MM-DD'),
    side: '左',
    quantity: 1,
    unit: '0',
    remark: ''
  }
}

function defaultTransferItem() {
  return {
    code: '',
    targetWarehouseName: '',
    targetSide: '左',
    remark: ''
  }
}

const buildQuery = () => {
  const params = {
    ...props.defaultQuery,
    pageNum: currentPage.value,
    pageSize: pageSize.value
  }
  Object.entries(searchForm.value).forEach(([key, value]) => {
    if (key !== 'productionDateRange' && value !== '' && value != null) {
      params[key] = value
    }
  })
  if (searchForm.value.productionDateRange?.length === 2) {
    params.productionDateStart = searchForm.value.productionDateRange[0]
    params.productionDateEnd = searchForm.value.productionDateRange[1]
  }
  if (activeBizScene.value) {
    params.bizScene = activeBizScene.value
  }
  return params
}

const handleSearch = async () => {
  loading.value = true
  try {
    const res = await pagePalletTasks(buildQuery())
    resultList.value = res.data?.records || []
    total.value = res.data?.total || 0
    selectedRows.value = []
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  searchForm.value = defaultSearchForm()
  activeBizScene.value = props.bizSceneTabs[0]?.value || props.defaultQuery.bizScene || ''
  currentPage.value = 1
  handleSearch()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  handleSearch()
}

const handleCurrentChange = (page) => {
  currentPage.value = page
  handleSearch()
}

const hasSearchField = (field) => props.searchFields.includes(field)
const hasColumn = (column) => props.columns.includes(column)
const hasTopAction = (action) => topActions.value.includes(action)
const hasRowAction = (action) => props.rowActions.includes(action)
const hasBatchAction = (action) => batchActions.value.includes(action)
const getActionLabel = (action, fallback) => activeTabActionConfig.value.labels?.[action] || fallback
const handleBizSceneTabChange = () => {
  currentPage.value = 1
  handleSearch()
}
const goBindPallet = () => {
  router.push('/pallet-code/list')
}

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

const getPiecesLimitByProductId = (productId) => {
  const product = productMap.value[productId]
  const value = Number(product?.piecesPerPallet ?? product?.pieces_per_pallet)
  return Number.isFinite(value) && value > 0 ? value : null
}

const canEditQuantity = (row) => row.unit === '1'

const getQuantityMax = (row) => {
  if (row.unit === '0') {
    return 1
  }
  return getPiecesLimitByProductId(row.productId) || undefined
}

const normalizeQuantityByUnit = (row) => {
  if (row.unit === '0') {
    row.quantity = 1
    return
  }
  if (!row.quantity || row.quantity < 1) {
    row.quantity = 1
  }
  const limit = getPiecesLimitByProductId(row.productId)
  if (limit && row.quantity > limit) {
    row.quantity = limit
  }
}

const validateQuantityRows = (rows, label = '任务') => {
  for (const row of rows) {
    if (row.unit === '0') {
      row.quantity = 1
      continue
    }
    const limit = getPiecesLimitByProductId(row.productId)
    if (!limit) {
      ElMessage.error(`${label} ${row.code || row.semiPalletCode} 未找到每板件数配置，不能按件提交`)
      return false
    }
    if (!row.quantity || row.quantity < 1) {
      ElMessage.error(`${label} ${row.code || row.semiPalletCode} 件数必须大于 0`)
      return false
    }
    if (row.quantity > limit) {
      ElMessage.error(`${label} ${row.code || row.semiPalletCode} 件数不能超过每板件数 ${limit}`)
      return false
    }
  }
  return true
}

const isPending = (row) => row.taskStatus === 'PENDING'
const canBindSemi = (row) => hasRowAction('bindSemi') && row.taskType === 'FINISH_IN' && isPending(row)
const canConfirmIn = (row) => hasRowAction('confirmIn') && ['SEMI_IN', 'FINISH_IN'].includes(row.taskType) && isPending(row)
const canCancel = (row) => hasRowAction('cancel') && isPending(row)
const canConfirmSemiDirectOut = (row) => hasRowAction('semiOutConfirm') && row.taskType === 'OUT' && row.bizScene === 'DIRECT_OUT' && isPending(row)
const canConfirmSemiPrepare = (row) => hasRowAction('semiPrepareConfirm') && row.taskType === 'OUT' && row.bizScene === 'PREPARE_CONSUMED' && isPending(row)
const canConfirmFinishOut = (row) => hasRowAction('finishOutConfirm') && row.taskType === 'OUT' && row.bizScene === 'FINISH_OUT' && isPending(row)
const canConfirmTransfer = (row) => hasRowAction('transferConfirm') && row.taskType === 'TRANSFER' && isPending(row)

const openSemiBindDialog = (row) => {
  semiBindForm.value = {
    code: row.code,
    items: row.semiItems?.length
        ? row.semiItems.map(item => ({
          semiPalletCode: item.semiPalletCode,
          semiProductId: item.semiProductId || null,
          semiProductName: item.semiProductName || '',
          quantity: item.quantity || 1,
          unit: item.unit || '0',
          useAssay: !!item.useAssay
        }))
        : [defaultSemiItem()]
  }
  semiBindDialogVisible.value = true
}

const closeSemiBindDialog = () => {
  semiBindDialogVisible.value = false
  semiBindForm.value = {code: '', items: [defaultSemiItem()]}
}

const addSemiItem = () => {
  semiBindForm.value.items.push(defaultSemiItem())
}

const removeSemiItem = (index) => {
  if (semiBindForm.value.items.length === 1) {
    ElMessage.warning('至少保留一条半成品明细')
    return
  }
  semiBindForm.value.items.splice(index, 1)
}

const findProductByParsedPallet = (info) => {
  if (info?.productId && productMap.value[info.productId]) {
    return productMap.value[info.productId]
  }
  return productList.value.find(item => {
    const sameName = item.productName === info?.productName
    const sameStatus = !info?.productStatus || item.status === info.productStatus
    return sameName && sameStatus
  }) || null
}

const resolveSemiItemProduct = async (row) => {
  if (!row.semiPalletCode || row.semiProductId) {
    normalizeSemiItemQuantity(row)
    return
  }
  try {
    const res = await parsePalletCode(row.semiPalletCode)
    const product = findProductByParsedPallet(res.data || {})
    if (product) {
      row.semiProductId = product.id
      row.semiProductName = product.productName
    }
  } catch {
    row.semiProductId = null
    row.semiProductName = ''
  }
  normalizeSemiItemQuantity(row)
}

const resolveSemiItems = async (items) => {
  for (const item of items) {
    await resolveSemiItemProduct(item)
  }
}

const getSemiItemPiecesLimit = (row) => getPiecesLimitByProductId(row.semiProductId)

const getSemiItemQuantityMax = (row) => {
  if (row.unit === '0') {
    return 1
  }
  return getSemiItemPiecesLimit(row) || undefined
}

const normalizeSemiItemQuantity = (row) => {
  if (row.unit === '0') {
    row.quantity = 1
    return
  }
  if (!row.quantity || row.quantity < 1) {
    row.quantity = 1
  }
  const limit = getSemiItemPiecesLimit(row)
  if (limit && row.quantity > limit) {
    row.quantity = limit
  }
}

const validateSemiBindItems = (items) => {
  for (const item of items) {
    if (item.unit === '0') {
      item.quantity = 1
      continue
    }
    const limit = getSemiItemPiecesLimit(item)
    if (!limit) {
      ElMessage.error(`半成品 ${item.semiPalletCode} 未找到每板件数配置，不能按件绑定`)
      return false
    }
    if (!item.quantity || item.quantity < 1) {
      ElMessage.error(`半成品 ${item.semiPalletCode} 件数必须大于 0`)
      return false
    }
    if (item.quantity > limit) {
      ElMessage.error(`半成品 ${item.semiPalletCode} 件数不能超过每板件数 ${limit}`)
      return false
    }
  }
  return true
}

const submitSemiBind = async () => {
  const items = semiBindForm.value.items.filter(item => item.semiPalletCode)
  if (!items.length) {
    ElMessage.warning('请填写半成品托盘码')
    return
  }
  await resolveSemiItems(items)
  if (!validateSemiBindItems(items)) {
    return
  }
  const payloadItems = items.map(item => ({
    semiPalletCode: item.semiPalletCode,
    quantity: item.quantity,
    unit: item.unit,
    useAssay: item.useAssay
  }))
  await bindSemiItemsToTask({code: semiBindForm.value.code, items: payloadItems})
  ElMessage.success('绑定成功')
  closeSemiBindDialog()
  await handleSearch()
}

const openConfirmInDialog = (row) => {
  confirmInProduct.value = productMap.value[row.productId] || null
  confirmInForm.value = {
    ...defaultConfirmInForm(),
    code: row.code,
    entryDate: row.productionDate || dayjs().format('YYYY-MM-DD'),
    side: row.targetSide || '左'
  }
  confirmInDialogVisible.value = true
}

const closeConfirmInDialog = () => {
  confirmInDialogVisible.value = false
  confirmInFormRef.value?.resetFields()
  confirmInForm.value = defaultConfirmInForm()
  confirmInProduct.value = null
}

const handleConfirmInUnitChange = (unit) => {
  if (unit === '0') {
    confirmInForm.value.quantity = 1
    return
  }
  if (confirmInPiecesLimit.value && confirmInForm.value.quantity > confirmInPiecesLimit.value) {
    confirmInForm.value.quantity = confirmInPiecesLimit.value
  }
}

const submitConfirmIn = async () => {
  await confirmInFormRef.value?.validate()
  if (confirmInForm.value.unit === '0') {
    confirmInForm.value.quantity = 1
  } else {
    if (!confirmInPiecesLimit.value) {
      ElMessage.error('未找到该产品的每板件数配置，不能按件确认入库')
      return
    }
    if (confirmInForm.value.quantity > confirmInPiecesLimit.value) {
      ElMessage.error(`件数不能超过该产品每板件数 ${confirmInPiecesLimit.value}`)
      return
    }
  }
  await confirmPalletInBatch({items: [confirmInForm.value]})
  ElMessage.success('确认入库成功')
  closeConfirmInDialog()
  await handleSearch()
}

const ensureSelectedRows = () => {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选任务')
    return false
  }
  return true
}

const ensureRowsMatch = (predicate, message) => {
  if (!ensureSelectedRows()) {
    return false
  }
  if (selectedRows.value.some(row => !predicate(row))) {
    ElMessage.warning(message)
    return false
  }
  return true
}

const openBatchConfirmInDialog = () => {
  if (!ensureRowsMatch(
      row => ['SEMI_IN', 'FINISH_IN'].includes(row.taskType) && isPending(row),
      '请选择待处理的入库任务'
  )) {
    return
  }
  batchConfirmInRows.value = selectedRows.value.map(row => ({
    code: row.code,
    productId: row.productId,
    productName: row.productName,
    warehouseName: row.targetWarehouseName || '',
    entryDate: row.productionDate || dayjs().format('YYYY-MM-DD'),
    side: row.targetSide || '左',
    quantity: 1,
    unit: '0',
    remark: ''
  }))
  batchConfirmInDialogVisible.value = true
}

const closeBatchConfirmInDialog = () => {
  batchConfirmInDialogVisible.value = false
  batchConfirmInRows.value = []
}

const submitBatchConfirmIn = async () => {
  if (!batchConfirmInRows.value.length) {
    ElMessage.warning('请先勾选入库任务')
    return
  }
  if (batchConfirmInRows.value.some(row => !row.warehouseName)) {
    ElMessage.warning('请填写所有入库仓库')
    return
  }
  if (!validateQuantityRows(batchConfirmInRows.value, '入库任务')) {
    return
  }
  const items = batchConfirmInRows.value.map(row => ({
    code: row.code,
    warehouseName: row.warehouseName,
    entryDate: row.entryDate,
    side: row.side,
    quantity: row.quantity,
    unit: row.unit,
    remark: row.remark
  }))
  await confirmPalletInBatch({items})
  ElMessage.success('批量确认入库成功')
  closeBatchConfirmInDialog()
  await handleSearch()
}

const validateBatchCodeRows = (key) => {
  const validators = {
    semiOutConfirm: row => row.taskType === 'OUT' && row.bizScene === 'DIRECT_OUT' && isPending(row),
    semiPrepareConfirm: row => row.taskType === 'OUT' && row.bizScene === 'PREPARE_CONSUMED' && isPending(row),
    finishOutConfirm: row => row.taskType === 'OUT' && row.bizScene === 'FINISH_OUT' && isPending(row),
    transferConfirm: row => row.taskType === 'TRANSFER' && isPending(row)
  }
  const messages = {
    semiOutConfirm: '请选择待处理的半成品普通出库任务',
    semiPrepareConfirm: '请选择待处理的转入备料池任务',
    finishOutConfirm: '请选择待处理的成品出库任务',
    transferConfirm: '请选择待处理的调拨任务'
  }
  const validator = validators[key]
  if (!validator) {
    ElMessage.warning('不支持的批量操作')
    return false
  }
  return ensureRowsMatch(validator, messages[key])
}

const openBatchCodeDialog = (key) => {
  if (!validateBatchCodeRows(key)) {
    return
  }
  batchCodeOperationKey.value = key
  batchCodeRows.value = [...selectedRows.value]
  batchCodeRemark.value = ''
  batchCodeDialogVisible.value = true
}

const closeBatchCodeDialog = () => {
  batchCodeDialogVisible.value = false
  batchCodeOperationKey.value = ''
  batchCodeRows.value = []
  batchCodeRemark.value = ''
}

const submitBatchCodeOperation = async () => {
  const operation = batchCodeOperation.value
  if (!operation || !batchCodeRows.value.length) {
    return
  }
  await operation.api({
    codes: batchCodeRows.value.map(row => row.code),
    remark: batchCodeRemark.value
  })
  ElMessage.success('批量提交成功')
  closeBatchCodeDialog()
  await handleSearch()
}

const batchCancelTasks = async () => {
  if (!ensureRowsMatch(row => isPending(row), '请选择待处理任务')) {
    return
  }
  const result = await ElMessageBox.prompt(
      `确认取消选中的 ${selectedRows.value.length} 个任务吗？`,
      '批量取消任务',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValue: '批量取消任务',
        inputPlaceholder: '请输入取消备注'
      }
  ).catch(() => null)
  if (!result) {
    return
  }
  await cancelPalletTasks({
    codes: selectedRows.value.map(row => row.code),
    remark: result.value || '批量取消任务'
  })
  ElMessage.success('批量取消成功')
  await handleSearch()
}

const openCommonDialog = (key, codes = []) => {
  currentOperationKey.value = key
  commonForm.value = {codesText: codes.join('\n'), remark: ''}
  transferForm.value = {items: [defaultTransferItem()]}
  commonDialogVisible.value = true
}

const closeCommonDialog = () => {
  commonDialogVisible.value = false
  currentOperationKey.value = ''
  commonForm.value = {codesText: '', remark: ''}
  transferForm.value = {items: [defaultTransferItem()]}
}

const addTransferItem = () => {
  transferForm.value.items.push(defaultTransferItem())
}

const removeTransferItem = (index) => {
  if (transferForm.value.items.length === 1) {
    ElMessage.warning('至少保留一条调拨明细')
    return
  }
  transferForm.value.items.splice(index, 1)
}

const parseCodes = (text) => {
  return text
      .split(/[\s,，]+/)
      .map(item => item.trim())
      .filter(Boolean)
}

const submitCommonOperation = async () => {
  const operation = commonOperation.value
  if (!operation) {
    return
  }
  let payload
  if (operation.mode === 'codes') {
    const codes = parseCodes(commonForm.value.codesText)
    if (!codes.length) {
      ElMessage.warning('请填写托盘码')
      return
    }
    payload = {codes, remark: commonForm.value.remark}
  } else {
    const items = transferForm.value.items.filter(item => item.code && item.targetWarehouseName)
    if (!items.length) {
      ElMessage.warning('请填写调拨托盘和目标仓库')
      return
    }
    payload = {items}
  }
  await operation.api(payload)
  ElMessage.success('提交成功')
  closeCommonDialog()
  await handleSearch()
}

const confirmRowCodes = async (row, key) => {
  openCommonDialog(key, [row.code])
}

const cancelRowTask = async (row) => {
  await ElMessageBox.confirm(`确认取消托盘 ${row.code} 的待处理任务吗？`, '温馨提示', {type: 'warning'})
  await cancelPalletTasks({codes: [row.code], remark: 'Web管理端取消任务'})
  ElMessage.success('取消成功')
  await handleSearch()
}

const loadWarehouses = async () => {
  const res = await getWarehouse({})
  warehouseList.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
}

const loadProducts = async () => {
  const res = await getProductList({})
  productList.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
}

watch(() => confirmInForm.value.unit, (unit) => {
  handleConfirmInUnitChange(unit)
})

onMounted(async () => {
  await Promise.all([loadWarehouses(), loadProducts()])
  await handleSearch()
})
</script>

<style scoped>
.operation-logs {
  padding: 0;
}

.search-card {
  margin-bottom: 20px;
  background: var(--app-panel);
}

.table-card {
  background: var(--app-panel);
}

.page-header {
  margin-bottom: 14px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.page-subtitle {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}

.el-form--inline .el-form-item {
  margin-right: 24px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.action-groups {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.action-card {
  background: linear-gradient(180deg, #ffffff 0%, #fbfcff 100%);
}

.action-title {
  margin-right: 10px;
  font-weight: 600;
}

.table-add-btn {
  margin-top: 12px;
}

.form-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}

:deep(.el-table) {
  --el-table-border-color: var(--app-border-soft);
}

:deep(.el-table__header th) {
  background-color: #f7f8fb;
  color: var(--app-text-secondary);
}

:deep(.el-table__body tr:hover > td) {
  background-color: var(--app-hover) !important;
}
</style>
