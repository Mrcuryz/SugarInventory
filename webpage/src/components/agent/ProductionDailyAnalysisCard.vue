<script setup>
import { computed, ref, watch, defineAsyncComponent } from 'vue'
import { dailyAnalysisSummary, dailyAnalysisSeries, dailyAnalysisNotes, dailyAnalysisValue, dailyAnalysisChart } from './dailyReportAnalysisPresentation.mjs'
const TrendChart = defineAsyncComponent(() => import('./RegisteredReportTrendChart.vue'))
const props = defineProps({ card: { type: Object, required: true } })
const summary = computed(() => dailyAnalysisSummary(props.card))
const series = computed(() => dailyAnalysisSeries(props.card))
const notes = computed(() => dailyAnalysisNotes(props.card))
const selectedIndex = ref(0)
const metric = ref('quantity')
const selected = computed(() => series.value[selectedIndex.value])
const chart = computed(() => dailyAnalysisChart(selected.value, metric.value))
watch(() => props.card, () => { selectedIndex.value = 0; metric.value = 'quantity' })
watch(selected, () => { metric.value = 'quantity' })
</script>

<template>
  <section class="daily-analysis" aria-label="生产日报分析">
    <div class="daily-analysis-scope">
      <strong>{{ summary.dateRangeLabel }}</strong>
      <span>{{ summary.departmentScopeLabel }} · {{ summary.productScopeLabel }}</span>
      <small>来源：生产日报（含已保存草稿）</small>
    </div>
    <p v-if="!series.length" class="daily-analysis-empty">所选范围没有可汇总的产量或能耗记录，不表示产量为 0。</p>
    <template v-else>
      <label class="daily-analysis-picker">统计项目
        <select v-model="selectedIndex" aria-label="选择日报统计项目">
          <option v-for="(row, index) in series" :key="index" :value="index">{{ row.departmentName }} · {{ row.label }}（{{ row.unit }}）</option>
        </select>
      </label>
      <div class="daily-analysis-totals">
        <div><span>已记录数量合计</span><strong>{{ dailyAnalysisValue(selected.quantity, selected.unit) }}</strong></div>
        <div v-if="selected.tonnageApplicable"><span>已记录折吨合计</span><strong>{{ dailyAnalysisValue(selected.convertedTons, '吨') }}</strong></div>
      </div>
      <p class="daily-analysis-coverage">{{ selected.coverageLabel }}</p>
      <div v-if="selected.tonnageApplicable" class="daily-analysis-tabs" role="group" aria-label="趋势指标">
        <button type="button" :aria-pressed="metric === 'quantity'" @click="metric = 'quantity'">数量</button>
        <button type="button" :aria-pressed="metric === 'convertedTons'" @click="metric = 'convertedTons'">折吨</button>
      </div>
      <TrendChart :card="card" :spec="chart" />
      <details class="daily-analysis-details">
        <summary>每日明细 · {{ selected.points?.length || 0 }} 天</summary>
        <div class="daily-analysis-table-scroll"><table>
          <thead><tr><th>日期</th><th>数量（{{ selected.unit }}）</th><th v-if="selected.tonnageApplicable">折吨</th></tr></thead>
          <tbody><tr v-for="point in selected.points" :key="point.businessDate">
            <td>{{ point.businessDate }}</td><td>{{ point.recordCount === 0 ? '无记录' : dailyAnalysisValue(point.quantity) }}</td>
            <td v-if="selected.tonnageApplicable">{{ point.recordCount === 0 ? '无记录' : dailyAnalysisValue(point.convertedTons) }}</td>
          </tr></tbody>
        </table></div>
      </details>
    </template>
    <p v-if="summary.truncated" class="daily-analysis-warning">当前显示前 10 个统计项目，请缩小车间或产品范围。导出范围与本卡片一致。</p>
    <details v-if="notes.length" class="daily-analysis-details"><summary>数据说明</summary><p v-for="note in notes" :key="note">{{ note }}</p></details>
  </section>
</template>

<style scoped>
.daily-analysis { padding: 12px; color: #334155; font-size: 12px; }
.daily-analysis-scope { display: flex; flex-direction: column; gap: 5px; margin-bottom: 14px; }
.daily-analysis-scope small, .daily-analysis-coverage { color: #64748b; line-height: 1.7; }
.daily-analysis-picker { display: flex; flex-direction: column; gap: 6px; }
.daily-analysis-picker select { width: 100%; min-width: 0; padding: 9px; border: 1px solid #dbe4f0; border-radius: 8px; background: white; color: #334155; }
.daily-analysis-totals { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 10px; margin-top: 12px; }
.daily-analysis-totals div { display: flex; flex-direction: column; gap: 8px; padding: 12px; border-radius: 8px; background: #f5f8fe; }
.daily-analysis-totals strong { font-size: 18px; color: #1e40af; overflow-wrap: anywhere; }
.daily-analysis-tabs { display: flex; gap: 6px; }
.daily-analysis-tabs button { padding: 5px 13px; border: 1px solid #dbe4f0; border-radius: 6px; background: white; cursor: pointer; }
.daily-analysis-tabs button[aria-pressed=true] { background: #eff6ff; border-color: #93c5fd; color: #1d4ed8; }
.daily-analysis-details { border-top: 1px solid #edf2f7; margin-top: 10px; padding-top: 10px; line-height: 1.7; }
.daily-analysis-details summary { cursor: pointer; color: #475569; }
.daily-analysis-table-scroll { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; margin-top: 8px; white-space: nowrap; font-size: 11px; }
th, td { padding: 7px 5px; border-bottom: 1px solid #edf2f7; text-align: right; }
th:first-child, td:first-child { text-align: left; }
.daily-analysis-warning { color: #a16207; line-height: 1.7; }
.daily-analysis-empty { padding: 15px 0; line-height: 1.8; }
</style>
