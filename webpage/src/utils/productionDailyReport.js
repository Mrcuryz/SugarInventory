export const PRODUCTION_PRODUCT_UNIT = '件'

export const productionDailyDateState = ({ date, selectedDate, today, status }) => {
  if (date === selectedDate) return 'selected'
  if (date > today) return 'future'
  if (status === 'SUBMITTED') return 'submitted'
  if (status === 'DRAFT') return 'draft'
  return 'unfilled'
}

export const productionDailyCalendarRange = value => {
  const anchor = value instanceof Date ? value : parseLocalDate(value)
  const start = new Date(anchor.getFullYear(), anchor.getMonth(), 1, 12, 0, 0)
  const end = new Date(anchor.getFullYear(), anchor.getMonth() + 1, 0, 12, 0, 0)
  start.setDate(start.getDate() - 14)
  end.setDate(end.getDate() + 14)
  return {
    startDate: formatLocalDate(start),
    endDate: formatLocalDate(end)
  }
}

export const reorderProductLines = (rows, fromIndex, toIndex) => {
  if (!Array.isArray(rows)
    || fromIndex === toIndex
    || fromIndex < 0
    || toIndex < 0
    || fromIndex >= rows.length
    || toIndex >= rows.length) {
    return false
  }

  const [row] = rows.splice(fromIndex, 1)
  rows.splice(toIndex, 0, row)
  rows.forEach((item, index) => {
    item.displayOrder = index + 1
  })
  return true
}

const parseLocalDate = value => {
  const [year, month, day] = String(value).split('-').map(Number)
  return new Date(year, month - 1, day || 1, 12, 0, 0)
}

const formatLocalDate = date => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
