<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent, AriaComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { registeredReportTrendChart } from './registeredReportTrendChart.mjs'

echarts.use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, AriaComponent, CanvasRenderer])

const props = defineProps({
  card: {
    type: Object,
    required: true
  }
})

const chartElement = ref(null)
const chartSpec = computed(() => registeredReportTrendChart(props.card))
let chartInstance = null
let resizeObserver = null

const valueText = (value, unit) => {
  if (value === null || value === undefined || value === '') return '暂无数据'
  return `${value}${unit ? ` ${unit}` : ''}`
}

const renderChart = async () => {
  await nextTick()
  if (!chartElement.value || !chartSpec.value) return
  if (chartElement.value.clientWidth < 1 || chartElement.value.clientHeight < 1) return
  if (!chartInstance) chartInstance = echarts.init(chartElement.value)
  const spec = chartSpec.value
  chartInstance.setOption({
    animationDuration: 280,
    color: spec.series.map(item => item.color),
    aria: { enabled: true, description: spec.ariaLabel },
    grid: { left: 12, right: spec.axes.length > 1 ? 18 : 12, top: 44, bottom: 30, containLabel: true },
    legend: {
      top: 4,
      left: 0,
      itemWidth: 14,
      itemHeight: 8,
      textStyle: { color: '#64748b', fontSize: 11 }
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      formatter: params => {
        const rows = Array.isArray(params) ? params : [params]
        const heading = rows[0]?.axisValueLabel || ''
        return [heading, ...rows.map(item => {
          const series = spec.series[item.seriesIndex]
          return `${item.marker}${item.seriesName}：${valueText(item.value, series?.unit)}`
        })].join('<br/>')
      }
    },
    xAxis: {
      type: 'category',
      boundaryGap: spec.series.some(item => item.type === 'bar'),
      data: spec.categories,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#64748b', fontSize: 10, hideOverlap: true }
    },
    yAxis: spec.axes.map((axis, index) => ({
      type: 'value',
      position: index === 1 ? 'right' : 'left',
      min: axis.min,
      max: axis.max,
      name: axis.unit || '',
      nameTextStyle: { color: '#94a3b8', fontSize: 10 },
      axisLabel: { color: '#64748b', fontSize: 10 },
      splitLine: { lineStyle: { color: index === 0 ? '#eef2f7' : 'transparent' } }
    })),
    series: spec.series.map(item => ({
      name: item.name,
      type: item.type,
      yAxisIndex: item.axisIndex,
      data: item.data,
      smooth: false,
      connectNulls: false,
      symbol: 'circle',
      symbolSize: 6,
      showSymbol: spec.categories.length <= 14,
      barMaxWidth: 22,
      lineStyle: { width: 2 },
      emphasis: { focus: 'series' }
    }))
  }, true)
}

watch(chartSpec, () => {
  if (!chartSpec.value && chartInstance) {
    chartInstance.dispose()
    chartInstance = null
    return
  }
  renderChart()
}, { deep: true })

onMounted(() => {
  renderChart()
  if (typeof ResizeObserver !== 'undefined' && chartElement.value) {
    resizeObserver = new ResizeObserver(() => {
      if (chartInstance) chartInstance.resize()
      else renderChart()
    })
    resizeObserver.observe(chartElement.value)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<template>
  <section v-if="chartSpec" class="registered-report-chart" :aria-label="chartSpec.ariaLabel">
    <h4>{{ chartSpec.title }}</h4>
    <div ref="chartElement" class="registered-report-chart-canvas" role="img" :aria-label="chartSpec.ariaLabel"></div>
    <p>图表仅呈现报表中的登记指标；具体数值和数据限制见下方明细。</p>
  </section>
</template>

<style scoped>
.registered-report-chart {
  box-sizing: border-box;
  min-width: 0;
  width: 100%;
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
}

.registered-report-chart h4 {
  margin: 0;
  color: #1e293b;
  font-size: 14px;
}

.registered-report-chart-canvas {
  min-width: 0;
  max-width: 100%;
  width: 100%;
  height: 220px;
  overflow: hidden;
}

.registered-report-chart p {
  margin: 4px 0 0;
  color: #94a3b8;
  font-size: 11px;
  line-height: 1.5;
}

@media (max-width: 640px) {
  .registered-report-chart {
    padding: 10px 8px;
  }

  .registered-report-chart-canvas {
    height: 200px;
  }
}
</style>
