import request from '@/utils/request.js'

export const pageRegisteredReportRuns = (params = {}) => (
  request.get('/analytics/agent-read/reports', { params })
)

export const getRegisteredReportRun = reportRunId => (
  request.get(`/analytics/agent-read/reports/${encodeURIComponent(reportRunId)}`)
)

export const exportRegisteredReportRunXlsx = reportRunId => (
  request.get(
    `/analytics/agent-read/reports/${encodeURIComponent(reportRunId)}/export.xlsx`,
    { responseType: 'blob' }
  )
)
