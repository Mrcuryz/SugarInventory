import dayjs from 'dayjs'

export const formatDateTime = (value) => {
  if (!value) {
    return '-'
  }
  const normalized = typeof value === 'string' ? value.replace('T', ' ') : value
  const parsed = dayjs(normalized)
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : String(value).replace('T', ' ')
}
