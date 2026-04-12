<template>
  <div class="auto-inbound-page">
    <!-- 解析区 -->
    <el-card class="search-card" style="max-width: 1200px">
      <div class="card-title">自动入库解析</div>
      <el-dialog
          v-model="semiEditVisible"
          title="关联半成品"
          width="820px"
      >
        <el-form label-width="100px">
          <div
              v-for="(item, index) in semiEditRecords"
              :key="index"
              style="margin-bottom: 12px"
          >
            <el-row :gutter="16">
              <!-- 半成品名称 -->
              <el-col :span="11">
                <el-form-item label="半成品名称">
                  <el-select
                      v-model="item.semiProductId"
                      placeholder="选择半成品"
                      clearable
                      filterable
                      @change="onSemiProductChange(item)"
                  >
                    <el-option
                        v-for="semi in semiProductList"
                        :key="semi.productId"
                        :label="semi.productName"
                        :value="semi.productId"
                    />
                  </el-select>
                </el-form-item>
              </el-col>

              <!-- 库位 -->
              <el-col :span="10">
                <el-form-item label="库位">
                  <el-select
                      v-model="item.warehouseId"
                      placeholder="选择库位"
                      clearable
                      filterable
                      style="width:100%"
                  >
                    <el-option
                        v-for="w in warehouseList"
                        :key="w.warehouseId || w.id"
                        :label="w.warehouseName"
                        :value="w.warehouseId || w.id"
                    />
                  </el-select>
                </el-form-item>
              </el-col>

              <!-- 数量 + 单位（板/件） -->
            </el-row>

            <el-row :gutter="16">
              <!-- 生产日期 -->
              <el-col :span="11">
                <el-form-item label="生产日期">
                  <el-date-picker
                      v-model="item.productionDate"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="生产日期"
                      style="width:100%"
                  />
                </el-form-item>
              </el-col>

              <el-col :span="9">
                <el-form-item label="数量">
                  <div style="display:flex;gap:4px">
                    <el-input
                        v-model="item.quantity"
                        clearable
                        width:20px
                    />
                    <el-select
                        v-model="item.unit"
                        placeholder="单位"
                        style="width:110px"
                    >
                      <el-option label="板" value="0" />
                      <el-option label="件" value="1" />
                    </el-select>
                  </div>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <!-- 套用该半成品化验数据 -->
              <el-col :span="8">
                <el-form-item label=" ">
                  <el-switch
                      v-model="item.useAssay"
                      active-text="套用化验数据"
                      :active-value="true"
                      :inactive-value="false"
                      @change="handleSemiUseAssay(index)"
                  />
                </el-form-item>
              </el-col>

              <!-- 删除按钮 -->
              <el-col :span="6" style="display:flex;align-items:center;margin-left:auto;margin-right:auto;">
                <el-button
                    type="danger"
                    :icon="Delete"
                    circle
                    @click="removeSemiEditRecord(index)"
                />
              </el-col>
            </el-row>

            <el-divider v-if="index !== semiEditRecords.length - 1" />
          </div>

          <el-button
              type="primary"
              plain
              size="small"
              @click="addSemiEditRecord"
          >
            添加半成品
          </el-button>
        </el-form>

        <template #footer>
          <el-button @click="semiEditVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmSemiEdit">确定</el-button>
        </template>
      </el-dialog>
      <el-form :model="parseForm" label-width="90px" class="parse-form">
        <div class="parse-basic-panel">
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
        </div>

        <div class="parse-text-panel">
          <div class="parse-text-header">
            <div>
              <div class="parse-text-title">原始报数文本</div>
              <div class="parse-text-subtitle">直接粘贴报数内容，系统会按入库日期和产品类型解析成待入库任务。</div>
            </div>
          </div>
          <el-form-item label="报数文本" class="raw-text-item">
            <el-input
                v-model="parseForm.rawText"
                type="textarea"
                :rows="10"
                placeholder="直接粘贴报数文本"
          />
          </el-form-item>
          <div class="parse-actions">
            <el-button type="primary" :loading="loadingParse" @click="handleParse">
              解析报数
            </el-button>
            <el-button @click="handleResetParse">清空</el-button>
          </div>
        </div>
      </el-form>
    </el-card>

    <!-- 任务列表区 -->
    <el-card class="table-card" style="max-width: 1200px" v-if="taskList.length || batchId">
      <div class="table-header">
        <div class="left">
          <el-button
              v-if="batchId"
              type="primary"
              link
              size="small"
              :loading="loadingBatch"
              @click="handleReloadBatch"
              style="margin-left: 8px"
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
          :row-key="row => row.taskId"
          height="480"
      >

        <!--        <el-table-column type="expand">-->
<!--          <template #default="{ row }">-->
<!--            <el-button type="primary" link @click="openSemiEditDialog(row)">-->
<!--              {{ (row.semiRecords && row.semiRecords.length) ? '编辑' : '添加' }}-->
<!--            </el-button>-->
<!--            <div v-if="row.type === 'FINISHED_PRODUCT' && row.suggestedSemiRecords && row.suggestedSemiRecords.length">-->
<!--              <div class="semi-title">关联半成品记录</div>-->
<!--              <el-table-->
<!--                  :data="row.suggestedSemiRecords"-->
<!--                  size="small"-->
<!--                  border-->
<!--                  style="width: 100%; margin-bottom: 8px"-->
<!--              >-->
<!--                <el-table-column prop="productName" label="半成品" min-width="100" />-->
<!--                <el-dialog-->
<!--                    v-model="semiEditVisible"-->
<!--                    title="关联半成品"-->
<!--                    width="820px"-->
<!--                >-->
<!--                  <el-form>-->
<!--                    <div v-for="(item, index) in semiEditRecords" :key="index" style="margin-bottom: 12px">-->
<!--                      <el-row :gutter="16">-->
<!--                        &lt;!&ndash; 半成品名称 &ndash;&gt;-->
<!--                        <el-col :span="8">-->
<!--                          <el-form-item :label="'半成品名称'" :prop="`semiEditRecords.${index}.semiProductId`">-->
<!--                            <el-select-->
<!--                                v-model="item.semiProductId"-->
<!--                                placeholder="选择半成品"-->
<!--                                clearable-->
<!--                                filterable-->
<!--                                @change="onSemiProductChange(item)"-->
<!--                            >-->
<!--                              <el-option-->
<!--                                  v-for="semi in semiProductList"-->
<!--                                  :key="semi.productId"-->
<!--                                  :label="semi.productName"-->
<!--                                  :value="semi.productId"-->
<!--                              />-->
<!--                            </el-select>-->
<!--                          </el-form-item>-->
<!--                        </el-col>-->

<!--                        &lt;!&ndash; 库位 &ndash;&gt;-->
<!--                        <el-col :span="6">-->
<!--                          <el-form-item :label="'库位'" :prop="`semiEditRecords.${index}.warehouseId`">-->
<!--                            <el-select-->
<!--                                v-model="item.warehouseId"-->
<!--                                placeholder="选择库位"-->
<!--                                clearable-->
<!--                                filterable-->
<!--                                style="width: 100%"-->
<!--                            >-->
<!--                              <el-option-->
<!--                                  v-for="w in warehouseList"-->
<!--                                  :key="w.warehouseId || w.id"-->
<!--                                  :label="w.warehouseName"-->
<!--                                  :value="w.warehouseId || w.id"-->
<!--                              />-->
<!--                            </el-select>-->
<!--                          </el-form-item>-->
<!--                        </el-col>-->

<!--                        &lt;!&ndash; 数量 + 单位（板/件） &ndash;&gt;-->
<!--                        <el-col :span="6">-->
<!--                          <el-form-item :label="'数量'" :prop="`semiEditRecords.${index}.quantity`">-->
<!--                            <div style="display: flex; gap: 4px">-->
<!--                              <el-input-->
<!--                                  v-model="item.quantity"-->
<!--                                  clearable-->
<!--                                  style="flex: 1"-->
<!--                              />-->
<!--                              <el-select-->
<!--                                  v-model="item.unit"-->
<!--                                  placeholder="单位"-->
<!--                                  style="width: 80px"-->
<!--                              >-->
<!--                                <el-option label="板" value="0" />-->
<!--                                <el-option label="件" value="1" />-->
<!--                              </el-select>-->
<!--                            </div>-->
<!--                          </el-form-item>-->
<!--                        </el-col>-->

<!--                        &lt;!&ndash; 删除按钮 &ndash;&gt;-->
<!--                        <el-col :span="4" style="display:flex;align-items:center">-->
<!--                          <el-button-->
<!--                              type="danger"-->
<!--                              :icon="Delete"-->
<!--                              circle-->
<!--                              @click="removeSemiEditRecord(index)"-->
<!--                          />-->
<!--                        </el-col>-->
<!--                      </el-row>-->

<!--                      <el-row :gutter="16">-->
<!--                        &lt;!&ndash; 生产日期 &ndash;&gt;-->
<!--                        <el-col :span="8">-->
<!--                          <el-form-item :label="'生产日期'" :prop="`semiEditRecords.${index}.productionDate`">-->
<!--                            <el-date-picker-->
<!--                                v-model="item.productionDate"-->
<!--                                type="date"-->
<!--                                placeholder="生产日期"-->
<!--                                value-format="YYYY-MM-DD"-->
<!--                                style="width: 100%"-->
<!--                            />-->
<!--                          </el-form-item>-->
<!--                        </el-col>-->

<!--                        &lt;!&ndash; 套用化验数据开关，可选 &ndash;&gt;-->
<!--                        <el-col :span="8">-->
<!--                          <el-form-item label=" " >-->
<!--                            <el-switch-->
<!--                                v-model="item.useAssay"-->
<!--                                active-text="套用该半成品化验数据"-->
<!--                                :active-value="true"-->
<!--                                :inactive-value="false"-->
<!--                                @change="handleSemiUseAssay(index)"-->
<!--                            />-->
<!--                          </el-form-item>-->
<!--                        </el-col>-->
<!--                      </el-row>-->

<!--                      <el-divider v-if="index !== semiEditRecords.length - 1" />-->
<!--                    </div>-->

<!--                    <el-button-->
<!--                        type="primary"-->
<!--                        plain-->
<!--                        size="small"-->
<!--                        @click="addSemiEditRecord"-->
<!--                    >-->
<!--                      添加半成品-->
<!--                    </el-button>-->
<!--                  </el-form>-->

<!--                  <template #footer>-->
<!--                    <el-button @click="semiEditVisible = false">取消</el-button>-->
<!--                    <el-button type="primary" @click="confirmSemiEdit">确定</el-button>-->
<!--                  </template>-->
<!--                </el-dialog>-->

<!--                <el-table-column label="生产日期" width="140">-->
<!--                  <template #default="{ row: semi }">-->
<!--                    <el-date-picker-->
<!--                        v-model="semi.productionDate"-->
<!--                        type="date"-->
<!--                        value-format="YYYY-MM-DD"-->
<!--                        placeholder="日期"-->
<!--                        size="small"-->
<!--                        style="width: 120px"-->
<!--                    />-->
<!--                  </template>-->
<!--                </el-table-column>-->

<!--                <el-table-column label="库位" min-width="100">-->
<!--                  <template #default="{ row: semi }">-->
<!--                    <el-input-->
<!--                        v-model="semi.warehouseName"-->
<!--                        size="small"-->
<!--                        placeholder="库位名称"-->
<!--                        clearable-->
<!--                    />-->
<!--                  </template>-->
<!--                </el-table-column>-->

<!--                <el-table-column label="数量" width="300">-->
<!--                  <template #default="{ row: semi }">-->
<!--                    <el-input-number-->
<!--                        v-model="semi.quantity"-->
<!--                        :min="0"-->
<!--                        size="small"-->
<!--                        style="width: 90px"-->
<!--                    />-->
<!--                    <el-select-->
<!--                        v-model="semi.unit"-->
<!--                        size="small"-->
<!--                        style="width: 70px; margin-left: 6px"-->
<!--                    >-->
<!--                      <el-option label="板" value="0" />-->
<!--                      <el-option label="件" value="1" />-->
<!--                    </el-select>-->
<!--                  </template>-->
<!--                </el-table-column>-->

<!--                <el-table-column label="套用化验" width="110">-->
<!--                  <template #default="{ row: semi }">-->
<!--                    <el-switch-->
<!--                        v-model="semi.useAssay"-->
<!--                        :active-value="true"-->
<!--                        :inactive-value="false"-->
<!--                        size="small"-->
<!--                    />-->
<!--                  </template>-->
<!--                </el-table-column>-->
<!--              </el-table>-->
<!--            </div>-->
<!--            <div v-else class="no-semi-info">-->
<!--              当前任务无关联半成品记录-->
<!--            </div>-->
<!--          </template>-->
<!--        </el-table-column>-->

        <el-table-column type="selection" width="35" />

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

        <el-table-column label="板数" width="100">
          <template #default="{ row }">
            <el-input-number
                v-if="row.type === 'SEMI_PRODUCT'"
                v-model="row.semiBoardQuantity"
                :min="0"
                size="small"
                controls-position="right"
                style="width: 75px"
            />
            <el-input-number
                v-else
                v-model="row.finishedBoardQuantity"
                :min="0"
                size="small"
                controls-position="right"
                style="width: 75px"
            />
          </template>
        </el-table-column>

        <el-table-column label="件数" width="100">
          <template #default="{ row }">
            <el-input-number
                v-if="row.type === 'SEMI_PRODUCT'"
                v-model="row.semiPieceQuantity"
                :min="0"
                size="small"
                controls-position="right"
                style="width: 75px"
            />
            <el-input-number
                v-else
                v-model="row.finishedPieceQuantity"
                :min="0"
                size="small"
                controls-position="right"
                style="width: 75px"
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

        <el-table-column prop="riskReason" label="风险原因" min-width="100">
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
        <el-table-column label="关联半成品" width="100">
          <template #default="{ row }">
            <!-- 只有成品任务才需要关联半成品 -->
            <el-button
                v-if="row.type === 'FINISHED_PRODUCT'"
                type="primary"
                link
                size="small"
                @click.stop="openSemiEditDialog(row)"
            >
              {{ (row.semiRecords && row.semiRecords.length) ? '编辑' : '编辑' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" width="85">
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
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTokenStore } from '@/stores/token'
import { confirmAutoInbound, getAutoInboundBatch, parseAutoInbound } from '@/api/autoInbound'
import { getWarehouse } from '@/api/warehouse'
import { Delete } from '@element-plus/icons-vue'

// ---------- 工具：从 JWT 里解析出当前用户ID（sub） ----------
function decodeJwtSub (token) {
  if (!token) return null
  try {
    const parts = token.split('.')
    if (parts.length < 2) return null
    let payload = parts[1]
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

// ---------- 关联半成品弹窗 ----------
const semiEditVisible = ref(false)
const semiEditRecords = ref([])  // 弹窗里的临时列表
const semiEditTask = ref(null)   // 当前正在编辑的任务

const warehouseList = ref([])

const loadWarehouseList = async () => {
  try {
    const res = await getWarehouse({ page: 1, size: 500 })
    const records = Array.isArray(res.data) ? res.data : (res.data.records || [])
    warehouseList.value = records
  } catch (e) {
    console.error('获取库位列表失败', e)
  }
}

const findWarehouseNameById = (id) => {
  if (!id) return ''
  const w = warehouseList.value.find(
      w => w.warehouseId === id || w.id === id
  )
  return w ? w.warehouseName : ''
}

// 打开弹窗：从 task.semiRecords / suggestedSemiRecords 里拷贝一份到 semiEditRecords
const openSemiEditDialog = (task) => {
  semiEditTask.value = task
  let records = []

  if (Array.isArray(task.semiRecords) && task.semiRecords.length) {
    records = task.semiRecords.map(r => ({
      semiProductId: r.semiProductId || null,
      productName: r.productName || '',
      warehouseId: r.warehouseId || null,
      warehouseName: findWarehouseNameById(r.warehouseId),
      quantity: r.quantity ?? '',
      unit: String(r.unit ?? '1'),
      productionDate: r.productionDate || '',
      useAssay: !!r.useAssay
    }))
  } else if (task.suggestedSemiRecords && task.suggestedSemiRecords.length) {
    records = task.suggestedSemiRecords.map(s => ({
      semiProductId: s.semiProductId || null,
      productName: s.productName || '',
      warehouseId: s.warehouseId || null,
      warehouseName: findWarehouseNameById(s.warehouseId),
      quantity: s.quantity ?? '',
      unit: String(s.unit ?? '1'),
      productionDate: s.productionDate || '',
      useAssay: !!s.useAssay
    }))
  }

  if (!records.length) {
    records.push({
      semiProductId: null,
      productName: '',
      warehouseId: null,
      warehouseName: '',
      quantity: '',
      unit: '0',
      productionDate: '',
      useAssay: false
    })
  }

  semiEditRecords.value = JSON.parse(JSON.stringify(records))
  semiEditVisible.value = true
}

const addSemiEditRecord = () => {
  semiEditRecords.value.push({
    semiProductId: null,
    productName: '',
    warehouseId: null,
    warehouseName: '',
    quantity: '',
    unit: '0',
    productionDate: '',
    useAssay: false
  })
}

const removeSemiEditRecord = (index) => {
  semiEditRecords.value.splice(index, 1)
}

const onSemiProductChange = (item) => {
  const found = semiProductList.value.find(p => p.productId === item.semiProductId)
  if (found) {
    item.productName = found.productName
  }
}

// 只能选一个“套用化验”
const handleSemiUseAssay = (index) => {
  semiEditRecords.value.forEach((r, i) => {
    if (i !== index) r.useAssay = false
  })
}

// 弹窗“确定”：把编辑结果写回对应 task 的 semiRecords，并让 expand 立刻刷新
const confirmSemiEdit = () => {
  if (!semiEditTask.value) return

  for (const r of semiEditRecords.value) {
    if (!r.semiProductId) {
      ElMessage.error('请完整填写半成品名称')
      return
    }
    if (!r.warehouseId) {
      ElMessage.error('请选择库位')
      return
    }
  }

  const newRecords = semiEditRecords.value.map(r => ({
    semiProductId: r.semiProductId,
    productName: r.productName,
    productionDate: r.productionDate || null,
    warehouseId: r.warehouseId,
    quantity: r.quantity ? Number(r.quantity) : null,
    unit: r.unit,
    useAssay: !!r.useAssay
  }))

  console.log('newRecords:', newRecords)

  const taskId = semiEditTask.value.taskId
  console.log('taskId:', taskId)
  const idx = taskList.value.findIndex(t => t.taskId === taskId)
  console.log('idx:', idx)
  if (idx !== -1) {
    const old = taskList.value[idx]
    taskList.value[idx] = { ...old, semiRecords: newRecords }
    console.log('taskList[idx]:', taskList.value[idx])
    semiEditTask.value = taskList.value[idx]
    console.log('semiEditTask:', semiEditTask.value)
  }

  semiEditVisible.value = false
  ElMessage.success('关联半成品已更新')
}

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

// -------- 产品级联选择数据 --------
const semiProductList = ref([])
const stProductList = ref([])

const cascaderProps = {
  emitPath: false,
  label: 'label',
  value: 'value',
  children: 'children',
  expandTrigger: 'hover'
}

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

// 根据 productId 在级联数据中找到产品名（只是为了回填 row.productName）
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
    globalRemarks.value = res.data.globalRemarks || []

    const rawTasks = res.data.tasks || []

    // ⭐ 一次性构造：_productId + 初始 semiRecords
    taskList.value = rawTasks.map(t => {
      const _productId = t.type === 'SEMI_PRODUCT' ? t.semiProductId : t.productId
      return {
        ...t,
        _productId,
        semiRecords: Array.isArray(t.semiRecords) && t.semiRecords.length
            ? t.semiRecords
            : (t.suggestedSemiRecords || [])
      }
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
    const rawTasks = res.data.tasks || []

    console.log('res.data:', res.data)
    console.log('rawTasks:', rawTasks)

    taskList.value = rawTasks.map(t => {
      const _productId = t.type === 'SEMI_PRODUCT' ? t.semiProductId : t.productId
      console.log('_productId:', _productId)
      return {
        ...t,
        _productId,
        semiRecords: Array.isArray(t.semiRecords) && t.semiRecords.length
            ? t.semiRecords
            : (t.suggestedSemiRecords || []),
      }
    })

    console.log('taskList:', taskList.value)

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

  const updatedTasks = taskList.value.filter((t) =>
      selectedTaskIds.value.includes(t.taskId)
  )
  console.log('updatedTasks:', updatedTasks)

  const payload = {
    operatorId: operatorId.value,
    confirmedTaskIds: selectedTaskIds.value,
    updatedTasks
  }
  console.log('payload:', payload)
  loadingConfirm.value = true
  try {
    await confirmAutoInbound(batchId.value, payload)
    ElMessage.success('入库成功')
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
  loadWarehouseList()
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
  display: block !important;
  margin-top: 10px;
}

.parse-form :deep(.el-form-item:last-child) {
  margin-left: 0 !important;
  padding-left: 0;
}

.parse-basic-panel {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 24px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--app-border-soft);
}

.parse-text-panel {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #fbfcff;
}

.parse-text-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.parse-text-title {
  color: var(--app-text);
  font-size: 15px;
  font-weight: 650;
}

.parse-text-subtitle {
  margin-top: 4px;
  color: var(--app-text-tertiary);
  font-size: 13px;
}

.raw-text-item {
  display: block;
}

.raw-text-item :deep(.el-form-item__label) {
  float: none;
  display: block;
  margin-bottom: 8px;
  text-align: left;
}

.raw-text-item :deep(.el-form-item__content) {
  margin-left: 0 !important;
}

.raw-text-item :deep(.el-textarea__inner) {
  min-height: 220px;
  line-height: 1.6;
}

.parse-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
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
