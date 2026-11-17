import assert from 'node:assert/strict'
import test from 'node:test'

import {
  formatOperationFieldValue,
  getOperationBusinessLabel,
  getOperationFieldLabel,
  isOperationFieldVisible
} from './operationLogPresentation.mjs'

test('生产日报操作业务使用中文名称', () => {
  assert.equal(getOperationBusinessLabel('production_daily_report'), '生产日报')
  assert.equal(getOperationBusinessLabel('production_daily_report_section'), '生产日报（车间数据）')
})

test('生产日报字段使用业务名称并隐藏技术版本', () => {
  assert.equal(getOperationFieldLabel('production_daily_report_section', 'metricValues'), '生产指标')
  assert.equal(getOperationFieldLabel('production_daily_report_section', 'productLines'), '产品产量')
  assert.equal(getOperationFieldLabel('production_daily_report_section', 'unknownField'), '其他信息')
  assert.equal(getOperationFieldLabel('product', 'unknownField'), undefined)
  assert.equal(isOperationFieldVisible('production_daily_report_section', 'version'), false)
})

test('生产指标不再展示字段名和原始 JSON', () => {
  const text = formatOperationFieldValue('production_daily_report_section', 'metricValues', [
    {
      metricCode: 'FACTORY_TOTAL_SUGAR_USAGE',
      dailyActual: 840,
      convertedTons: 33.6,
      monthQuantity: null,
      remark: ''
    }
  ])
  assert.equal(text, '总用糖量（折白糖）：当日实绩 840；折成吨 33.6')
  assert.doesNotMatch(text, /metricCode|dailyActual|FACTORY_TOTAL_SUGAR_USAGE/)
})

test('产品产量使用中文分类和可读数值', () => {
  const text = formatOperationFieldValue('production_daily_report_section', 'productLines', [
    {
      categoryCode: 'DIRECT_PRODUCT',
      productId: 12,
      unit: '件',
      dailyActual: 25,
      monthQuantity: 100
    }
  ])
  assert.equal(text, '直接成品 / 产品编号 12：当日实绩 25 件；月累计（件） 100')
  assert.doesNotMatch(text, /categoryCode|productId|dailyActual/)
})

test('Excel 导入内容显示车间级摘要', () => {
  const text = formatOperationFieldValue('production_daily_report_excel', 'sections', [
    {
      departmentCode: 'POLYCRYSTAL',
      metricValues: [{ metricCode: 'POLY_SUGAR_DISSOLVING', dailyActual: 10 }],
      productLines: [{ categoryCode: 'SEMI_PRODUCT', productId: 1, dailyActual: 5 }]
    }
  ])
  assert.equal(text, '多晶车间：1 项生产指标，1 行产品产量')
  assert.doesNotMatch(text, /departmentCode|metricValues|productLines/)
})
