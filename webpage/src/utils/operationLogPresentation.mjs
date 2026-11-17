const BUSINESS_LABELS = Object.freeze({
  production_daily_report: '生产日报',
  production_daily_report_section: '生产日报（车间数据）',
  production_daily_report_excel: '生产日报（Excel 导入）'
})

const PRODUCTION_DAILY_TABLES = new Set(Object.keys(BUSINESS_LABELS))

const PRODUCTION_DAILY_FIELD_LABELS = Object.freeze({
  reportDate: '日报日期',
  preparedDate: '制表日期',
  preparedByName: '制表人',
  departmentCode: '车间',
  status: '状态',
  metricValues: '生产指标',
  productLines: '产品产量',
  sections: '导入内容'
})

const SECTION_LABELS = Object.freeze({
  POLYCRYSTAL: '多晶车间',
  BROWN_SUGAR: '红糖车间',
  MONOCRYSTAL: '单晶车间',
  FACTORY_SUMMARY: '全厂汇总'
})

const CATEGORY_LABELS = Object.freeze({
  SEMI_PRODUCT: '半成品',
  DIRECT_PRODUCT: '直接成品',
  SECONDARY_PRODUCT: '装包或二次加工成品',
  PRODUCT: '产品'
})

const STATUS_LABELS = Object.freeze({
  DRAFT: '草稿',
  SUBMITTED: '已提交'
})

const METRIC_LABELS = Object.freeze({
  POLY_SUGAR_OIL: '出糖油',
  POLY_SUGAR_DISSOLVING: '化糖',
  POLY_WHITE_SUGAR_USAGE: '白糖用量',
  POLY_ROCK_SUGAR_REBOIL: '冰糖回煮',
  POLY_SUGAR_OIL_REBOIL: '糖油回煮',
  POLY_SYRUP_USAGE: '糖浆用量',
  POLY_ROCK_SUGAR_BOILING: '煮冰糖',
  POLY_SUGAR_RELEASE_BUCKETS: '放糖桶数',
  POLY_DEMOULDING: '脱模',
  POLY_SEMI_SUBTOTAL: '半成品小计',
  POLY_DIRECT_SUBTOTAL: '直接成品小计',
  POLY_SECONDARY_SUBTOTAL: '装包或二次加工成品小计',
  POLY_OUTPUT: '冰糖产量',
  POLY_STEAM_USAGE: '冰糖汽耗',
  POLY_STEAM_PER_TON: '吨冰糖耗汽',
  POLY_POWER_USAGE: '冰糖总电耗',
  POLY_POWER_PER_TON: '吨冰糖电耗',
  POLY_CRYSTALLIZATION_RATE: '冰糖结晶率',
  POLY_YIELD_RATE: '冰糖产率',
  POLY_RETURN_SAND_RATE: '返砂占比',
  BROWN_SUGAR_DISSOLVING: '化糖',
  BROWN_RAW_SUGAR_USAGE: '赤砂糖用量',
  BROWN_SYRUP_USAGE: '糖浆用量',
  BROWN_ROCK_SUGAR_DISSOLUTION: '冰糖回溶',
  BROWN_WHITE_SUGAR_USAGE: '白砂糖用量',
  BROWN_SUGAR_BOILING: '红糖煮糖',
  BROWN_SUGAR_PICKING: '拣糖',
  BROWN_PRODUCT_SUBTOTAL: '产品小计',
  BROWN_STEAM_USAGE: '红糖汽耗',
  BROWN_STEAM_PER_TON: '吨红糖耗汽',
  BROWN_POWER_USAGE: '红糖总电耗',
  BROWN_POWER_PER_TON: '吨红糖电耗',
  BROWN_YIELD_RATE: '红糖产率',
  MONO_SUGAR_DISSOLVING: '单晶化糖',
  MONO_WHITE_SUGAR_USAGE: '白糖用量',
  MONO_SYRUP_USAGE: '糖浆用量',
  MONO_TANKS_STARTED: '开煮罐数',
  MONO_SEED_USAGE: '投单晶种',
  MONO_SEED_NO4_USAGE: '投4号种',
  MONO_SEED_NO3_USAGE: '投3号种',
  MONO_SEED_NO2_USAGE: '投2号种',
  MONO_COFFEE_SEED_USAGE: '投咖啡糖种',
  MONO_OUTPUT_TANKS: '出糖罐数',
  MONO_SEED_OUTPUT: '种子产量',
  MONO_PRODUCT_SUBTOTAL: '产品小计',
  MONO_STEAM_USAGE: '单晶汽耗',
  MONO_STEAM_PER_TON: '吨单晶汽耗',
  MONO_POWER_USAGE: '单晶总电耗',
  MONO_POWER_PER_TON: '吨单晶电耗',
  MONO_YIELD_RATE: '单晶产率',
  MONO_CRYSTALLIZATION_RATE: '单晶结晶率',
  MONO_POLY_SEED_RATE: '多晶种子占比',
  MONO_COFFEE_SEED_RATE: '咖啡糖种子占比',
  FACTORY_TOTAL_SUGAR_USAGE: '总用糖量（折白糖）',
  FACTORY_TOTAL_OUTPUT: '总产量',
  FACTORY_MIXED_YIELD_RATE: '混合成品产率',
  FACTORY_TOTAL_STEAM: '总耗汽',
  FACTORY_TOTAL_POWER: '总耗电量',
  FACTORY_TOTAL_WATER: '总耗水量',
  FACTORY_STEAM_PER_TON: '吨混合成品汽耗',
  FACTORY_POWER_PER_TON: '吨混合成品电耗',
  FACTORY_WATER_PER_TON: '吨混合成品水耗'
})

const VALUE_LABELS = Object.freeze({
  dailyActual: '当日实绩',
  convertedTons: '折成吨',
  monthQuantity: '月累计（件）',
  monthTons: '月累计（吨）',
  yearTons: '年累计（吨）',
  remark: '备注'
})

function hasValue(value) {
  return value !== null && value !== undefined && value !== ''
}

function valueParts(item, unit) {
  return Object.entries(VALUE_LABELS)
    .filter(([key]) => hasValue(item?.[key]))
    .map(([key, label]) => {
      const suffix = key === 'dailyActual' && unit ? ` ${unit}` : ''
      return `${label} ${item[key]}${suffix}`
    })
}

function formatMetricValues(value) {
  if (!Array.isArray(value) || !value.length) return '未填写指标数据'
  const rows = value
    .map(item => {
      const parts = valueParts(item)
      if (!parts.length) return null
      const label = METRIC_LABELS[item?.metricCode] || '其他生产指标'
      return `${label}：${parts.join('；')}`
    })
    .filter(Boolean)
  return rows.length ? rows.join('\n') : '未填写指标数据'
}

function formatProductLines(value) {
  if (!Array.isArray(value) || !value.length) return '未填写产品产量'
  const rows = value
    .map(item => {
      const parts = valueParts(item, item?.unit)
      if (!parts.length) return null
      const category = CATEGORY_LABELS[item?.categoryCode] || '产品'
      const product = item?.productName || (item?.productId ? `产品编号 ${item.productId}` : '未选择产品')
      return `${category} / ${product}：${parts.join('；')}`
    })
    .filter(Boolean)
  return rows.length ? rows.join('\n') : '未填写产品产量'
}

function formatImportSections(value) {
  if (!Array.isArray(value) || !value.length) return '未导入车间数据'
  return value
    .map(section => {
      const sectionName = SECTION_LABELS[section?.departmentCode] || '其他车间'
      const metricCount = Array.isArray(section?.metricValues)
        ? section.metricValues.filter(item => valueParts(item).length).length
        : 0
      const productCount = Array.isArray(section?.productLines)
        ? section.productLines.filter(item => valueParts(item, item?.unit).length).length
        : 0
      return `${sectionName}：${metricCount} 项生产指标，${productCount} 行产品产量`
    })
    .join('\n')
}

export function getOperationBusinessLabel(tableName) {
  return BUSINESS_LABELS[tableName] || tableName || '-'
}

export function getOperationFieldLabel(tableName, fieldName) {
  if (!PRODUCTION_DAILY_TABLES.has(tableName)) return undefined
  return PRODUCTION_DAILY_FIELD_LABELS[fieldName] || '其他信息'
}

export function isOperationFieldVisible(tableName, fieldName) {
  if (!PRODUCTION_DAILY_TABLES.has(tableName)) return true
  return fieldName !== 'version'
}

export function formatOperationFieldValue(tableName, fieldName, value) {
  if (!PRODUCTION_DAILY_TABLES.has(tableName)) return undefined
  if (fieldName === 'metricValues') return formatMetricValues(value)
  if (fieldName === 'productLines') return formatProductLines(value)
  if (fieldName === 'sections') return formatImportSections(value)
  if (fieldName === 'departmentCode') return SECTION_LABELS[value] || value
  if (fieldName === 'status') return STATUS_LABELS[value] || value
  return undefined
}
