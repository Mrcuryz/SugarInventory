const PROCESS_STAGE_LABELS = Object.freeze({
  understanding: '理解用户需求',
  routing: '判断业务领域并选择专家',
  expert_planning: '专家确认查询条件并决定工具',
  resolving_product: '确认产品范围',
  resolving_warehouse: '确认库位范围',
  inventory_overview: '查询库存总览',
  inventory_distribution: '查询库存分布',
  inventory_query: '查询库存数据',
  warehouse_query: '查询库位数据',
  assay_query: '查询化验与质量数据',
  pallet_query: '查询托盘与二维码数据',
  production_query: '调用生产数据工具',
  logistics_query: '查询任务与单据数据',
  master_data_query: '查询产品与主数据',
  administration_query: '查询员工与权限数据',
  audit_query: '查询审计数据',
  business_query: '调用业务查询工具',
  analyzing: '分析工具返回结果',
  clarification: '确认查询范围',
  continuing: '根据用户选择继续处理',
  answer: '整理最终回答',
  fallback: '切换到受控降级模式',
  error: '处理未完成'
})

export const processStageLabel = (stage, text = '') => (
  PROCESS_STAGE_LABELS[stage] || String(text || '').replace(/^正在/, '').replace(/[。…]+$/, '') || '处理请求'
)

export const appendProcessStep = (steps = [], payload = {}, fallbackStage = '') => {
  const stage = String(payload.stage || fallbackStage || '').trim()
  if (!stage) return Array.isArray(steps) ? steps : []
  const current = Array.isArray(steps) ? steps : []
  if (current.some(item => item.stage === stage)) return current
  return [
    ...current,
    {
      stage,
      label: processStageLabel(stage, payload.text || payload.message),
      text: String(payload.text || payload.message || '').trim()
    }
  ]
}

