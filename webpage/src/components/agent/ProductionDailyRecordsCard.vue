<script setup>
import { computed } from 'vue'
import { dailyReportRecords, reportedValue, cumulativeDisplay, overviewValueLabel } from './dailyReportRecordsPresentation.mjs'
const props = defineProps({ card: { type: Object, required: true } })
const data = computed(() => dailyReportRecords(props.card))
</script>

<template>
  <section class="daily-records">
    <p>{{ data.summary.sourceLabel }} · {{ data.summary.scopeLabel }}</p>
    <p v-if="data.summary.isEmpty">该日期范围没有已保存的生产日报。</p>
    <section v-if="data.overview.length" aria-label="生产概况" class="overview">
      <h4>生产概况</h4>
      <p class="overview-scope">{{ data.summary.dateRangeLabel }} · 按完整查询范围统计</p>
      <article v-for="(item, index) in data.overview" :key="index">
        <header><strong>{{ item.label }}</strong><span>{{ item.departmentName }}</span></header>
        <dl>
          <div><dt>{{ overviewValueLabel(item, data.summary.requestedDays) }}</dt><dd class="overview-number">{{ reportedValue(item.quantity, item.unit) }}</dd></div>
          <div v-if="item.convertedTons != null"><dt>{{ overviewValueLabel(item, data.summary.requestedDays, true) }}</dt><dd>{{ reportedValue(item.convertedTons, '吨') }}</dd></div>
        </dl>
        <p class="coverage">{{ item.coverageLabel }}</p>
      </article>
      <p v-if="data.summary.scopeLabel === '全部车间及全厂汇总' && !data.overview.some(item => item.summaryType === '全厂产量')">全厂总产量尚未记录，以下分类数量不代替全厂总量。</p>
      <p v-if="data.summary.overviewTruncated">概况仅展示前10组，请按车间或名称缩小范围。</p>
    </section>
    <details :open="!data.overview.length" class="detail-records">
    <summary>查看日报明细 · 本页 {{ data.rows.length }} 条</summary>
    <div class="report-dates">
      <span v-for="report in data.reports" :key="report.reportDate">{{ report.reportDate }} · 整份日报：{{ report.statusLabel }}</span>
    </div>
    <p v-if="!data.summary.isEmpty && !data.rows.length">本页没有匹配明细，可调整名称、车间或页码。</p>
    <article v-for="(row, index) in data.rows" :key="index">
      <header><strong>{{ row.itemName }}</strong><span>车间{{ row.sectionStatusLabel }}</span></header>
      <p>{{ row.reportDate }} · {{ row.departmentName }} · {{ row.groupName }}</p>
      <dl>
        <div><dt>{{ row.itemKindLabel === '产品' ? '当日数量' : row.itemName }}</dt><dd>{{ reportedValue(row.dailyActual, row.unit) }}</dd></div>
        <div v-if="row.itemKindLabel === '产品' || row.convertedTons != null"><dt>当日折吨</dt><dd>{{ reportedValue(row.convertedTons, '吨') }}</dd></div>
      </dl>
      <details v-if="[row.monthQuantity, row.monthTons, row.yearTons].some(value => value != null)">
        <summary>查看累计值</summary>
        <dl>
          <div><dt>月累计数量</dt><dd>{{ reportedValue(row.monthQuantity, row.unit) }}</dd></div>
          <div><dt>{{ cumulativeDisplay(row).monthLabel }}</dt><dd>{{ reportedValue(row.monthTons, cumulativeDisplay(row).unit) }}</dd></div>
          <div><dt>{{ cumulativeDisplay(row).yearLabel }}</dt><dd>{{ reportedValue(row.yearTons, cumulativeDisplay(row).unit) }}</dd></div>
        </dl>
      </details>
    </article>
    <p>共 {{ data.summary.total }} 条匹配明细，本页 {{ data.rows.length }} 条，第 {{ data.summary.page }} 页。<span v-if="data.summary.hasMore">可继续询问“下一页”。</span></p>
    </details>
    <details><summary>数据口径</summary><p v-for="note in data.notes" :key="note">{{ note }}</p></details>
  </section>
</template>

<style scoped>
.daily-records { padding: 12px; max-height: 560px; overflow: auto; font-size: 13px; line-height: 1.6; color: #536176; }
.daily-records p { margin: 6px 0; }
.report-dates { display: flex; flex-wrap: wrap; gap: 6px; }
.report-dates span { padding: 2px 8px; background: #f0f5ff; border-radius: 6px; }
article { margin: 10px 0; padding: 12px; border: 1px solid #e3eaf5; border-radius: 10px; background: #f8fafc; }
header { display: flex; gap: 12px; justify-content: space-between; color: #243247; }
header span { flex-shrink: 0; color: #687890; }
dl { margin: 8px 0; }
dl div { display: flex; justify-content: space-between; gap: 12px; }
dd { margin: 0; color: #243247; }
summary { cursor: pointer; color: #386bce; }
.overview h4 { margin: 12px 0 2px; color: #243247; font-size: 16px; }
.overview article { background: #f4f8ff; }
.overview-number { font-size: 19px; font-weight: 650; color: #2457b7; }
.coverage, .overview-scope { color: #6e7f96; font-size: 12px; }
.detail-records { margin-top: 12px; }
</style>
