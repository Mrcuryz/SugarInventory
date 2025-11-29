// src/api/autoInbound.js
import request from '@/utils/request'

// 1. 调用 LLM 自动解析
// AutoInboundParseRequest 假设字段：rawText, entryDate(yyyy-MM-dd), side('左'/'右')
export function parseAutoInbound(data) {
    return request.post('/auto-inbound/parse', data)
}

// 2. 根据 batchId 获取任务列表
export function getAutoInboundBatch(batchId) {
    return request.get(`/auto-inbound/${batchId}`)
}

// 3. 确认入库
// AutoInboundConfirmRequest: { operatorId, confirmedTaskIds, updatedTasks }
export function confirmAutoInbound(batchId, data) {
    return request.post(`/auto-inbound/${batchId}/confirm`, data)
}
