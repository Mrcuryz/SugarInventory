<template>
  <div class="review-lite">
    <el-card class="filter-panel">
      <el-form :model="filters" inline>
        <el-form-item label="审查状态">
          <el-select v-model="filters.reviewStatus" clearable placeholder="全部" style="width: 150px">
            <el-option v-for="item in reviewStatusOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="回答状态">
          <el-select v-model="filters.answerStatus" clearable placeholder="全部" style="width: 170px">
            <el-option v-for="item in answerStatusOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="失败域">
          <el-select v-model="filters.failureDomain" clearable placeholder="全部" style="width: 150px">
            <el-option v-for="item in failureDomainOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="失败类型">
          <el-input v-model="filters.failureCategory" clearable placeholder="例如 INTENT_MISS" style="width: 190px" />
        </el-form-item>
        <el-form-item label="修复类型">
          <el-select v-model="filters.suggestedFixType" clearable placeholder="全部" style="width: 170px">
            <el-option v-for="item in suggestedFixOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="用例状态">
          <el-select v-model="filters.testCaseStatus" clearable placeholder="全部" style="width: 150px">
            <el-option v-for="item in testCaseStatusOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="filters.priorityOnly">仅看待处理问题</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-panel">
      <div class="table-head">
        <div>
          <div class="page-title">Review Lite</div>
          <div class="page-subtitle">只展示 Agent 审查安全摘要，用于筛选、标记和整理回归用例线索。</div>
        </div>
        <el-button :loading="loading" @click="loadReviews">刷新</el-button>
      </div>

      <el-table :data="reviews" border stripe v-loading="loading">
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="userQuestionSummary" label="用户问题摘要" min-width="220" show-overflow-tooltip />
        <el-table-column prop="assistantAnswerSummary" label="助手回答摘要" min-width="240" show-overflow-tooltip />
        <el-table-column prop="answerStatus" label="回答状态" width="145" />
        <el-table-column prop="confidenceLevel" label="置信度" width="95" />
        <el-table-column prop="failureDomain" label="失败域" width="115" />
        <el-table-column prop="failureCategory" label="失败类型" width="170" show-overflow-tooltip />
        <el-table-column prop="suggestedFixType" label="建议修复" width="145" />
        <el-table-column prop="intentType" label="Intent" width="120" />
        <el-table-column label="计划工具" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ joinList(row.plannedTools) }}</template>
        </el-table-column>
        <el-table-column label="实际工具" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ joinList(row.actualToolNames) }}</template>
        </el-table-column>
        <el-table-column prop="reviewStatus" label="审查状态" width="120" />
        <el-table-column prop="testCaseStatus" label="用例状态" width="120" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          :disabled="loading || total === 0"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="Review 详情" size="72%" destroy-on-close>
      <div v-if="detail" class="detail-body">
        <section class="detail-section">
          <div class="section-title">问题与回答</div>
          <div class="text-block">
            <span>用户原问题</span>
            <p>{{ detail.userQuestion || '-' }}</p>
          </div>
          <div class="text-block">
            <span>助手安全回答</span>
            <p>{{ detail.assistantAnswerTextSafe || '-' }}</p>
          </div>
        </section>

        <section class="detail-section">
          <div class="section-title">审查摘要</div>
          <div class="info-grid">
            <div><span>回答状态</span><strong>{{ displayValue(detail.answerStatus) }}</strong></div>
            <div><span>置信度</span><strong>{{ displayValue(detail.confidenceLevel) }}</strong></div>
            <div><span>失败域</span><strong>{{ displayValue(detail.failureDomain) }}</strong></div>
            <div><span>失败类型</span><strong>{{ displayValue(detail.failureCategory) }}</strong></div>
            <div><span>建议修复</span><strong>{{ displayValue(detail.suggestedFixType) }}</strong></div>
            <div><span>审查状态</span><strong>{{ displayValue(detail.reviewStatus) }}</strong></div>
            <div><span>用例状态</span><strong>{{ displayValue(detail.testCaseStatus) }}</strong></div>
          </div>
          <div class="trace-line">{{ detail.answerTraceSummary || '-' }}</div>
        </section>

        <section class="detail-section">
          <div class="section-title">Intent Router 摘要</div>
          <div class="info-grid">
            <div><span>intent_type</span><strong>{{ snapshotValue('intent_type') }}</strong></div>
            <div><span>intent_subtype</span><strong>{{ snapshotValue('intent_subtype') }}</strong></div>
            <div><span>business_domain</span><strong>{{ snapshotValue('business_domain') }}</strong></div>
            <div><span>next_action</span><strong>{{ snapshotValue('next_action') }}</strong></div>
            <div><span>support_status</span><strong>{{ snapshotValue('support_status') }}</strong></div>
            <div><span>missing_slots</span><strong>{{ joinList(detail.agentDecisionSnapshot?.missing_slots) }}</strong></div>
          </div>
          <div class="tool-row">
            <span>planned_tools</span>
            <strong>{{ joinList(detail.plannedTools) }}</strong>
          </div>
          <div class="tool-row">
            <span>actual_tools</span>
            <strong>{{ joinList(detail.actualToolNames) }}</strong>
          </div>
        </section>

        <section class="detail-section">
          <div class="section-title">证据摘要</div>
          <el-table :data="detail.evidenceSummary || []" border>
            <el-table-column prop="createdAt" label="时间" width="170" />
            <el-table-column prop="evidenceType" label="类型" width="150" />
            <el-table-column prop="evidenceSummary" label="安全摘要" min-width="320" />
          </el-table>
        </section>

        <section class="detail-section">
          <div class="section-title">用户反馈与备注</div>
          <div class="info-grid">
            <div><span>反馈类型</span><strong>{{ displayValue(detail.userFeedbackType) }}</strong></div>
            <div><span>期望意图</span><strong>{{ displayValue(detail.expectedIntentSummary) }}</strong></div>
          </div>
          <div class="text-block">
            <span>反馈内容</span>
            <p>{{ detail.userFeedbackNote || '-' }}</p>
          </div>
          <el-input
            v-model="adminNote"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="管理员备注"
          />
        </section>

        <div class="drawer-actions">
          <el-button @click="copyRegressionDraft">复制为回归用例草稿</el-button>
          <el-button @click="updateStatus('TRIAGED')">标记 TRIAGED</el-button>
          <el-button @click="updateStatus('TEST_NEEDED')">标记 TEST_NEEDED</el-button>
          <el-button type="success" @click="updateStatus('FIXED')">标记 FIXED</el-button>
          <el-button type="warning" @click="updateStatus('WONT_FIX')">标记 WONT_FIX</el-button>
          <el-button type="primary" @click="saveNote">保存备注</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAgentMessageReviewDetail,
  pageAgentMessageReviews,
  updateAgentMessageReviewStatus
} from '@/api/agent'

const reviewStatusOptions = ['OPEN', 'TRIAGED', 'FIXED', 'WONT_FIX']
const answerStatusOptions = ['LOW_CONFIDENCE', 'NEEDS_REVIEW', 'FAILED']
const failureDomainOptions = ['PLANNER', 'SAFE_ADAPTER', 'CONTEXT', 'DATA', 'MODEL', 'UI', 'USER_INPUT', 'UNKNOWN']
const suggestedFixOptions = ['FIX_PLANNER', 'FIX_PROMPT', 'FIX_SAFE_ADAPTER', 'FIX_DATA_MODEL', 'FIX_UI_RENDER']
const testCaseStatusOptions = ['NONE', 'NEEDED', 'CREATED', 'PASSING']

const filters = reactive({
  reviewStatus: 'OPEN',
  answerStatus: '',
  failureDomain: '',
  failureCategory: '',
  suggestedFixType: '',
  testCaseStatus: '',
  priorityOnly: true
})
const reviews = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref(null)
const adminNote = ref('')

const snapshot = computed(() => detail.value?.agentDecisionSnapshot || {})

const loadReviews = async (retryWhenPageOverflow = true) => {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value,
      ...Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '' && value !== null && value !== undefined))
    }
    const res = await pageAgentMessageReviews(params)
    reviews.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
    const maxPage = Math.max(1, Math.ceil(total.value / size.value))
    if (retryWhenPageOverflow && page.value > maxPage) {
      page.value = maxPage
      await loadReviews(false)
    }
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  loadReviews()
}

const handleReset = () => {
  Object.assign(filters, {
    reviewStatus: 'OPEN',
    answerStatus: '',
    failureDomain: '',
    failureCategory: '',
    suggestedFixType: '',
    testCaseStatus: '',
    priorityOnly: true
  })
  handleSearch()
}

const handleSizeChange = value => {
  size.value = value
  page.value = 1
  loadReviews()
}

const handleCurrentChange = value => {
  page.value = value
  loadReviews()
}

const openDetail = async row => {
  const res = await getAgentMessageReviewDetail(row.id)
  detail.value = res.data
  adminNote.value = res.data?.adminNote || ''
  detailVisible.value = true
}

const updateStatus = async status => {
  if (!detail.value?.id) return
  const res = await updateAgentMessageReviewStatus(detail.value.id, {
    reviewStatus: status,
    adminNote: adminNote.value
  })
  detail.value = res.data
  adminNote.value = res.data?.adminNote || ''
  ElMessage.success('状态已更新')
  loadReviews()
}

const saveNote = async () => {
  if (!detail.value?.id) return
  const res = await updateAgentMessageReviewStatus(detail.value.id, {
    adminNote: adminNote.value
  })
  detail.value = res.data
  adminNote.value = res.data?.adminNote || ''
  ElMessage.success('备注已保存')
  loadReviews()
}

const copyRegressionDraft = async () => {
  if (!detail.value) return
  const lines = [
    'Review Regression Draft',
    `question: ${detail.value.userQuestion || '-'}`,
    `expected_intent: ${snapshot.value.intent_type || '-'}`,
    `intent_subtype: ${snapshot.value.intent_subtype || '-'}`,
    `next_action: ${snapshot.value.next_action || '-'}`,
    `planned_tools: ${joinList(detail.value.plannedTools)}`,
    `actual_tools: ${joinList(detail.value.actualToolNames)}`,
    `failure: ${detail.value.failureDomain || '-'}/${detail.value.failureCategory || '-'}`,
    `suggested_fix_type: ${detail.value.suggestedFixType || '-'}`,
    `safe_answer_summary: ${detail.value.assistantAnswerSummary || '-'}`
  ]
  await navigator.clipboard.writeText(lines.join('\n'))
  ElMessage.success('已复制安全草稿')
}

const displayValue = value => {
  const text = String(value || '').trim()
  return text && text.toUpperCase() !== 'UNKNOWN' ? text : '-'
}
const joinList = value => {
  if (!Array.isArray(value)) return '-'
  const items = value.map(displayValue).filter(item => item !== '-')
  return items.length ? items.join(', ') : '-'
}
const snapshotValue = key => {
  const value = snapshot.value?.[key]
  if (Array.isArray(value)) return joinList(value)
  return displayValue(value)
}

onMounted(loadReviews)
</script>

<style scoped lang="scss">
.review-lite {
  display: grid;
  gap: 14px;
}

.filter-panel,
.table-panel {
  border-radius: 8px;
}

.table-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
}

.page-title,
.section-title {
  color: var(--app-text);
  font-weight: 700;
}

.page-title {
  font-size: 17px;
}

.page-subtitle {
  margin-top: 4px;
  color: var(--app-text-tertiary);
  font-size: 13px;
}

.pagination-wrapper {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  padding-top: 16px;
}

.detail-body {
  display: grid;
  gap: 16px;
  padding-bottom: 70px;
}

.detail-section {
  display: grid;
  gap: 10px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 8px;
}

.info-grid > div,
.tool-row {
  display: grid;
  gap: 4px;
  padding: 9px 10px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  background: #fbfdff;
}

.info-grid span,
.tool-row span,
.text-block span {
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.info-grid strong,
.tool-row strong {
  color: var(--app-text);
  font-weight: 650;
  overflow-wrap: anywhere;
}

.text-block {
  display: grid;
  gap: 5px;
}

.text-block p,
.trace-line {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  background: #fbfdff;
  color: var(--app-text);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.drawer-actions {
  position: sticky;
  bottom: 0;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 0 0;
  background: #ffffff;
}
</style>
