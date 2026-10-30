export const dailyReportRecords = card => {
  const fields = Array.isArray(card?.fields) ? card.fields : []
  return {
    summary: fields.find(field => field.kind === 'daily_records_summary') || {},
    reports: fields.filter(field => field.kind === 'daily_records_header'),
    rows: fields.filter(field => field.kind === 'daily_records_row'),
    overview: fields.filter(field => field.kind === 'daily_records_overview'),
    notes: fields.filter(field => field.kind === 'daily_records_note').map(field => field.value)
  }
}

export const overviewValueLabel = (item, requestedDays, tons = false) => {
  const present = Number(tons ? item.tonnageRows : item.quantityRows)
  const partial = present < Number(item.recordCount) || Number(item.observedDays) < Number(requestedDays)
  return `${partial ? '已记录部分' : '已记录'}${tons ? '折吨' : '数量'}`
}

export const reportedValue = (value, unit = '') => {
  if (value === null || value === undefined || value === '') return '未填报'
  const number = Number(value)
  const displayUnit = ({ 'Kw.h': '千瓦时', M3: '立方米' })[unit] || unit
  return Number.isFinite(number) ? `${number}${displayUnit ? ` ${displayUnit}` : ''}` : '未填报'
}

export const cumulativeDisplay = row => row.itemKindLabel === '产品'
  ? { monthLabel: '月累计吨数', yearLabel: '年累计吨数', unit: '吨' }
  : { monthLabel: '月累计值', yearLabel: '年累计值', unit: '' }
