const fields = card => (Array.isArray(card?.fields) ? card.fields : [])
const byKind = (card, kind) => fields(card).find(item => item?.kind === kind) || null
const count = value => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0
}
const text = value => String(value ?? '').trim()

export const isTodayOperationsOverviewCard = card => (
  card?.cardType === 'today_operations_overview_report'
)

export const todayOperationsOverviewSummary = card => (
  byKind(card, 'today_operations_overview_summary')
)

export const todayOperationsOverviewSections = card => {
  const production = byKind(card, 'today_operations_production')
  const quality = byKind(card, 'today_operations_quality')
  const flow = byKind(card, 'today_operations_flow')
  const inventory = byKind(card, 'today_operations_inventory')
  const tasks = byKind(card, 'today_operations_tasks')
  return [
    production && {
      key: 'production',
      label: production.label,
      value: production.value,
      details: [
        `产出记录 ${count(production.outputRecordCount)} 条`,
        `生产订单 ${count(production.productionOrderCount)} 个`,
        `折算件数 ${count(production.totalPieces)} 件`
      ]
    },
    quality && {
      key: 'quality',
      label: quality.label,
      value: quality.value,
      details: [
        `已判定 ${count(quality.judgedRecordCount)} 条`,
        `无适用标准 ${count(quality.noStandardCount)} 条`,
        `多标准候选 ${count(quality.multipleCandidatesCount)} 条`
      ]
    },
    flow && {
      key: 'flow',
      label: flow.label,
      value: flow.value,
      details: [
        `领用记录 ${count(flow.materialInputRecordCount)} 条`,
        `稳定产出记录 ${count(flow.stableOutputRecordCount)} 条`,
        '两条序列采用各自业务日期，不直接相除'
      ]
    },
    inventory && {
      key: 'inventory',
      label: inventory.label,
      value: inventory.value,
      details: [
        `产品 ${count(inventory.productCount)} 种`,
        `库位 ${count(inventory.warehouseCount)} 个`,
        `托盘 ${count(inventory.palletCount)} 个`,
        text(inventory.totalWeightText) ? `总重量 ${text(inventory.totalWeightText)}` : ''
      ].filter(Boolean)
    },
    tasks && {
      key: 'tasks',
      label: tasks.label,
      value: tasks.value,
      details: [
        `今日完成 ${count(tasks.completedTaskCount)} 条`,
        `今日进行中 ${count(tasks.inProgressTaskCount)} 条`,
        `今日取消 ${count(tasks.canceledTaskCount)} 条`
      ]
    }
  ].filter(Boolean)
}

export const todayOperationsOverviewNotes = card => fields(card).filter(
  item => item?.kind === 'today_operations_quality_note'
)

export const todayOperationsOverviewScopeNote = card => (
  byKind(card, 'today_operations_scope_note')
)
