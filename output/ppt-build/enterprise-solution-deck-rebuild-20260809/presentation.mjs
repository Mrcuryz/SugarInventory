import fs from 'node:fs/promises'
import path from 'node:path'
import { Presentation, PresentationFile } from '@oai/artifact-tool'

const ROOT = 'D:\\Laibin\\LaibinSugarInventory'
const BUILD_DIR = path.join(ROOT, 'output', 'ppt-build', 'enterprise-solution-deck-rebuild-20260809')
const SCREEN_DIR = path.join(ROOT, 'output', 'playwright', 'solution-deck-20260809')
const FINAL_PPTX = path.join(ROOT, 'output', '企业软件解决方案-数字仓储平台示例.pptx')
const RENDER_DIR = path.join(BUILD_DIR, 'rendered')

const W = 1280
const H = 720
const FONT = 'Microsoft YaHei'
const C = {
  navy: '#0B1F33',
  navy2: '#071521',
  ink: '#102133',
  blue: '#1473E6',
  blue2: '#3A8DFF',
  teal: '#0F9FA8',
  tealLight: '#E5F6F6',
  white: '#FFFFFF',
  bg: '#F5F7FA',
  bgBlue: '#EEF5FD',
  rule: '#D7DEE7',
  gray: '#667085',
  gray2: '#98A2B3',
  green: '#1B9A59',
  amber: '#D97706',
  red: '#C2413A',
}

const presentation = Presentation.create({ slideSize: { width: W, height: H } })

async function bytes(filePath) {
  return new Uint8Array(await fs.readFile(filePath))
}

function addText(slide, text, position, options = {}) {
  const shape = slide.shapes.add({
    geometry: options.geometry || 'textbox',
    name: options.name,
    position,
    fill: options.fill || 'none',
    line: options.line || { style: 'solid', fill: 'none', width: 0 },
    ...(options.borderRadius ? { borderRadius: options.borderRadius } : {}),
  })
  shape.text = text
  shape.text.style = {
    typeface: FONT,
    fontSize: options.fontSize || 22,
    bold: options.bold || false,
    color: options.color || C.ink,
    alignment: options.alignment || 'left',
    verticalAlignment: options.verticalAlignment || 'top',
    autoFit: options.autoFit || 'shrinkText',
    insets: options.insets || { top: 0, right: 0, bottom: 0, left: 0 },
  }
  return shape
}

function addBox(slide, position, options = {}) {
  return slide.shapes.add({
    geometry: options.geometry || 'rect',
    name: options.name,
    position,
    fill: options.fill || C.bg,
    line: options.line || {
      style: options.lineStyle || 'solid',
      fill: options.stroke || 'none',
      width: options.strokeWidth || 0,
    },
    ...(options.borderRadius ? { borderRadius: options.borderRadius } : {}),
    ...(options.shadow ? { shadow: options.shadow } : {}),
  })
}

function addLine(slide, left, top, width, height = 0, color = C.rule, weight = 1, dashed = false) {
  return slide.shapes.add({
    geometry: 'straightConnector1',
    position: { left, top, width, height },
    fill: 'none',
    line: { style: dashed ? 'dashed' : 'solid', fill: color, width: weight },
  })
}

function addArrow(slide, left, top, width, height = 0, color = C.blue, weight = 2) {
  return slide.shapes.add({
    geometry: 'straightConnector1',
    position: { left, top, width, height },
    fill: 'none',
    line: { style: 'solid', fill: color, width: weight },
    head: { type: 'arrow', width: 'sm', length: 'sm' },
  })
}

function addNode(slide, text, position, options = {}) {
  const geometry = options.geometry || 'roundRect'
  const node = addBox(slide, position, {
    fill: options.fill || C.white,
    stroke: options.stroke || C.rule,
    strokeWidth: options.strokeWidth || 1,
    geometry,
    ...(['rect', 'textbox', 'roundRect'].includes(geometry)
      ? { borderRadius: options.borderRadius ?? 8 }
      : {}),
  })
  addText(slide, text, position, {
    fontSize: options.fontSize || 22,
    bold: options.bold ?? true,
    color: options.color || C.ink,
    alignment: 'center',
    verticalAlignment: 'middle',
    insets: options.insets || { top: 6, right: 8, bottom: 6, left: 8 },
  })
  return node
}

async function addImage(slide, filePath, position, alt, options = {}) {
  if (options.frame !== false) {
    addBox(slide, {
      left: position.left - 2,
      top: position.top - 2,
      width: position.width + 4,
      height: position.height + 4,
    }, {
      fill: C.white,
      stroke: options.stroke || C.rule,
      strokeWidth: 1,
      geometry: 'roundRect',
      borderRadius: options.borderRadius || 10,
      shadow: options.shadow || 'shadow-sm',
    })
  }
  return slide.images.add({
    blob: await bytes(filePath),
    contentType: 'image/png',
    alt,
    fit: options.fit || 'cover',
    position,
    geometry: options.geometry || 'roundRect',
    borderRadius: options.borderRadius || 8,
    ...(options.crop ? { crop: options.crop } : {}),
  })
}

function addPageHeader(slide, section, title, subtitle, page, options = {}) {
  const dark = options.dark || false
  const ink = dark ? C.white : C.navy
  const muted = dark ? '#C9D5E3' : C.gray
  addText(slide, section.toUpperCase(), { left: 64, top: 24, width: 360, height: 24 }, {
    fontSize: 16,
    bold: true,
    color: dark ? '#76C8FF' : C.blue,
  })
  addText(slide, title, { left: 64, top: 50, width: 1120, height: 64 }, {
    fontSize: 48,
    bold: true,
    color: ink,
    autoFit: 'none',
  })
  if (subtitle) {
    addText(slide, subtitle, { left: 64, top: 118, width: 1130, height: 38 }, {
      fontSize: 23,
      color: muted,
      autoFit: 'shrinkText',
    })
  }
  addLine(slide, 64, 684, 1096, 0, dark ? '#29445F' : C.rule, 1)
  addText(slide, String(page).padStart(2, '0'), { left: 1170, top: 674, width: 46, height: 24 }, {
    fontSize: 15,
    color: dark ? '#87A0B8' : C.gray2,
    alignment: 'right',
  })
}

function addNotes(slide, notes, sources = []) {
  slide.speakerNotes.textFrame.setText([
    notes,
    '',
    '[Sources]',
    ...sources.map((source) => `- ${source}`),
  ].join('\n'))
  slide.speakerNotes.setVisible(true)
}

function newSlide(fill = C.white) {
  const slide = presentation.slides.add()
  slide.background.fill = fill
  return slide
}

// 01 — Cover
{
  const slide = newSlide(C.navy2)
  slide.images.add({
    blob: await bytes(path.join(BUILD_DIR, 'assets', 'warehouse-cover.png')),
    contentType: 'image/png',
    alt: '通用现代工业仓储场景，仅作封面氛围背景',
    fit: 'cover',
    position: { left: 0, top: 0, width: W, height: H },
  })
  addBox(slide, { left: 0, top: 0, width: 690, height: 720 }, { fill: C.navy2 })
  addBox(slide, { left: 64, top: 126, width: 6, height: 174 }, { fill: C.blue })
  addText(slide, '企业级软件解决方案\n能力展示', { left: 96, top: 122, width: 574, height: 174 }, {
    fontSize: 52,
    bold: true,
    color: C.white,
    autoFit: 'none',
  })
  addText(slide, '以数字仓储平台为例', { left: 96, top: 330, width: 500, height: 52 }, {
    fontSize: 34,
    bold: true,
    color: '#75C5FF',
  })
  addLine(slide, 96, 420, 110, 0, C.teal, 4)
  addText(slide, '业务建模｜系统研发｜数据治理｜部署运维｜AI Agent', { left: 96, top: 448, width: 574, height: 48 }, {
    fontSize: 20,
    color: '#D8E2EC',
  })
  addText(slide, 'REAL PROJECT  /  ENTERPRISE DELIVERY', { left: 96, top: 638, width: 520, height: 24 }, {
    fontSize: 15,
    bold: true,
    color: '#7E98B3',
  })
  addNotes(slide,
    '开场先说明：仓储平台是一个真实案例，用于展示团队从业务分析到软件交付和智能化演进的完整能力。封面背景为通用生成素材，不代表项目现场或客户案例。',
    [path.join(BUILD_DIR, 'assets', 'warehouse-cover.png'), path.join(BUILD_DIR, 'source-notes.txt')])
}

// 02 — Customer problems and change
{
  const slide = newSlide()
  addPageHeader(slide, 'BUSINESS CONTEXT', '企业软件先解决哪些问题', '先让数据一致、流程可控、责任可追，再考虑更多功能。', 2)

  addBox(slide, { left: 64, top: 188, width: 480, height: 418 }, { fill: '#F2F4F7' })
  addBox(slide, { left: 736, top: 188, width: 480, height: 418 }, { fill: C.bgBlue })
  addText(slide, '现场常见状态', { left: 88, top: 206, width: 420, height: 42 }, { fontSize: 28, bold: true, color: C.gray })
  addText(slide, '系统化处理', { left: 760, top: 206, width: 420, height: 42 }, { fontSize: 28, bold: true, color: C.blue })
  addText(slide, '→', { left: 580, top: 342, width: 120, height: 90 }, { fontSize: 70, bold: true, color: C.blue, alignment: 'center' })

  const before = [
    '库存、生产和质量数据分散',
    '入库、出库依赖人工协调',
    '出现差异后靠人翻表',
    '功能上线后难维护和扩展',
    'AI 停留在通用问答',
  ]
  const after = [
    '产品、批次、托盘和库位统一记录',
    '任务与状态驱动现场操作',
    '库存流水和操作日志保留责任链',
    '分层架构与迁移流程支持持续升级',
    '在权限边界内读取真实业务数据',
  ]
  before.forEach((item, index) => {
    const y = 268 + index * 64
    addText(slide, `${String(index + 1).padStart(2, '0')}  ${item}`, { left: 88, top: y, width: 420, height: 38 }, {
      fontSize: 21,
      color: C.ink,
      verticalAlignment: 'middle',
    })
    addLine(slide, 88, y + 45, 420, 0, '#DEE3EA', 1)
    addText(slide, after[index], { left: 760, top: y, width: 420, height: 38 }, {
      fontSize: 21,
      bold: index === 2,
      color: index === 2 ? C.navy : C.ink,
      verticalAlignment: 'middle',
    })
    addLine(slide, 760, y + 45, 420, 0, '#CFDDED', 1)
  })
  addText(slide, '仓储是案例；真正交付的是业务方法、软件工程和长期维护能力。', { left: 170, top: 626, width: 940, height: 38 }, {
    fontSize: 24,
    bold: true,
    color: C.navy,
    alignment: 'center',
  })
  addNotes(slide,
    '本页从客户视角建立问题背景。讲解时可以根据客户行业替换示例，但不要把问题描述成仓储行业独有。',
    [path.join(ROOT, 'README.md'), path.join(ROOT, 'docs', 'agent', 'ai-assistant-product-goal.md')])
}

// 03 — Overall solution architecture
{
  const slide = newSlide()
  addPageHeader(slide, 'SOLUTION OVERVIEW', '多端入口连接同一套业务', '管理人员、现场人员和自然语言用户共享同一套身份、规则与数据。', 3)

  // Connection rails first.
  addLine(slide, 194, 244, 892, 0, '#9CB9D8', 2)
  addArrow(slide, 214, 282, 0, 54, '#9CB9D8', 2)
  addArrow(slide, 640, 282, 0, 54, '#9CB9D8', 2)
  addArrow(slide, 1066, 282, 0, 54, '#9CB9D8', 2)
  addArrow(slide, 640, 398, 0, 48, C.blue, 2)
  addArrow(slide, 640, 512, 0, 42, C.teal, 2)

  addText(slide, '管理人员', { left: 116, top: 184, width: 196, height: 42 }, { fontSize: 26, bold: true, alignment: 'center' })
  addText(slide, '现场人员', { left: 542, top: 184, width: 196, height: 42 }, { fontSize: 26, bold: true, alignment: 'center' })
  addText(slide, '业务用户', { left: 968, top: 184, width: 196, height: 42 }, { fontSize: 26, bold: true, alignment: 'center' })

  addNode(slide, 'Web 管理端', { left: 116, top: 336, width: 196, height: 62 }, { fill: C.navy, stroke: C.navy, color: C.white })
  addNode(slide, '微信小程序', { left: 542, top: 336, width: 196, height: 62 }, { fill: C.blue, stroke: C.blue, color: C.white })
  addNode(slide, 'AI 助手', { left: 968, top: 336, width: 196, height: 62 }, { fill: C.teal, stroke: C.teal, color: C.white })

  addBox(slide, { left: 116, top: 446, width: 1048, height: 66 }, { fill: '#E9F1F9', stroke: '#BFD1E3', strokeWidth: 1 })
  addText(slide, '统一身份与业务 API', { left: 116, top: 446, width: 1048, height: 66 }, {
    fontSize: 26,
    bold: true,
    color: C.navy,
    alignment: 'center',
    verticalAlignment: 'middle',
  })

  const domains = ['仓储运营', '生产协同', '质量管理', '权限与审计']
  domains.forEach((domain, index) => {
    const x = 116 + index * 262
    addText(slide, domain, { left: x, top: 554, width: 238, height: 44 }, {
      fontSize: 24,
      bold: true,
      color: index === 3 ? C.teal : C.ink,
      alignment: 'center',
    })
    if (index < 3) addLine(slide, x + 250, 552, 0, 48, C.rule, 1)
  })
  addText(slide, 'MySQL 业务事实  ·  Redis 状态  ·  报表与只读 RAG 制品', { left: 180, top: 620, width: 920, height: 32 }, {
    fontSize: 21,
    color: C.gray,
    alignment: 'center',
  })
  addNotes(slide,
    '强调多端入口共享同一套后端规则和数据，不是分别建设三套系统。AI 助手当前以查询、报表和预览为主。',
    [path.join(ROOT, 'webpage', 'src'), path.join(ROOT, 'src', 'main', 'java'), path.join(ROOT, 'docs', 'agent', 'ai-assistant-product-goal.md')])
}

// 04 — Real business trace flow
{
  const slide = newSlide()
  addPageHeader(slide, 'REAL BUSINESS FLOW', '一条链路贯通生产与库存', '煮糖批次详情按订单、二维码和库位关系实时组装，无需维护独立追溯台账。', 4)
  await addImage(slide, path.join(SCREEN_DIR, '11-boiling-batch-trace.png'), {
    left: 80, top: 172, width: 1120, height: 488,
  }, '煮糖批次去向追溯真实系统界面', {
    crop: { left: 0, top: 0.10, right: 0, bottom: 0.15 },
    borderRadius: 8,
  })
  addNotes(slide,
    '这是项目真实运行界面。讲解链路：来源批次 → 半成品生产订单 → 实际产出 → 二维码批次 → 入库；系统还可继续关联库位、领用和下游生产。截图仅执行查询，没有修改库存。',
    [path.join(SCREEN_DIR, '11-boiling-batch-trace.png'), path.join(ROOT, 'webpage', 'src', 'components', 'ProductionBoilingBatches.vue')])
}

// 05 — Real system UI
{
  const slide = newSlide()
  addPageHeader(slide, 'PRODUCT EVIDENCE', '真实系统已经覆盖核心场景', '库位、生产和质量管理由同一套业务数据驱动。', 5)

  await addImage(slide, path.join(SCREEN_DIR, '03-warehouse-map.png'), { left: 64, top: 184, width: 760, height: 452 }, '仓库平面与库位状态真实界面')
  await addImage(slide, path.join(SCREEN_DIR, '05-production-orders.png'), { left: 852, top: 184, width: 364, height: 210 }, '生产订单真实界面')
  await addImage(slide, path.join(SCREEN_DIR, '06-quality-assay.png'), { left: 852, top: 426, width: 364, height: 210 }, '化验管理真实界面')

  addBox(slide, { left: 82, top: 202, width: 142, height: 32 }, { fill: C.navy })
  addText(slide, '库位管理', { left: 82, top: 202, width: 142, height: 32 }, { fontSize: 18, bold: true, color: C.white, alignment: 'center', verticalAlignment: 'middle' })
  addBox(slide, { left: 870, top: 202, width: 132, height: 32 }, { fill: C.blue })
  addText(slide, '生产订单', { left: 870, top: 202, width: 132, height: 32 }, { fontSize: 18, bold: true, color: C.white, alignment: 'center', verticalAlignment: 'middle' })
  addBox(slide, { left: 870, top: 444, width: 132, height: 32 }, { fill: C.teal })
  addText(slide, '质量管理', { left: 870, top: 444, width: 132, height: 32 }, { fontSize: 18, bold: true, color: C.white, alignment: 'center', verticalAlignment: 'middle' })

  addNotes(slide,
    '这一页只展示真实项目截图。主截图用于说明库位空间与库存状态，右侧两张补充生产订单和质量记录。',
    [path.join(SCREEN_DIR, '03-warehouse-map.png'), path.join(SCREEN_DIR, '05-production-orders.png'), path.join(SCREEN_DIR, '06-quality-assay.png')])
}

// 06 — Frontend/backend engineering
{
  const slide = newSlide()
  addPageHeader(slide, 'ENGINEERING', '227 条接口背后的分层工程', '前端交互、业务规则、权限事务和数据访问分别承担清晰责任。', 6)

  addText(slide, '227', { left: 64, top: 176, width: 260, height: 92 }, { fontSize: 78, bold: true, color: C.blue })
  addText(slide, '运行时 HTTP 映射', { left: 68, top: 266, width: 360, height: 36 }, { fontSize: 24, bold: true, color: C.navy })
  addText(slide, '来自本地运行时 OpenAPI 统计', { left: 68, top: 304, width: 360, height: 30 }, { fontSize: 20, color: C.gray })

  const layers = [
    ['Web 管理端', 'Vue 3'],
    ['Controller', 'DTO / VO'],
    ['Service', '业务规则 / 事务'],
    ['Mapper', 'MyBatis-Plus'],
    ['数据层', 'MySQL / Redis'],
  ]
  layers.forEach((layer, index) => {
    const y = 350 + index * 57
    if (index < layers.length - 1) addArrow(slide, 268, y + 43, 0, 18, '#8DB1D6', 2)
    addBox(slide, { left: 68, top: y, width: 400, height: 46 }, {
      fill: index === 0 ? C.navy : (index === 4 ? C.tealLight : '#F1F5F9'),
      stroke: index === 0 ? C.navy : C.rule,
      strokeWidth: 1,
    })
    addText(slide, layer[0], { left: 86, top: y, width: 170, height: 46 }, {
      fontSize: 21,
      bold: true,
      color: index === 0 ? C.white : C.ink,
      verticalAlignment: 'middle',
    })
    addText(slide, layer[1], { left: 246, top: y, width: 200, height: 46 }, {
      fontSize: 19,
      color: index === 0 ? '#D9E6F2' : C.gray,
      alignment: 'right',
      verticalAlignment: 'middle',
    })
  })

  await addImage(slide, path.join(SCREEN_DIR, '10-api-documentation.png'), { left: 548, top: 184, width: 668, height: 390 }, 'Knife4j OpenAPI 运行时接口统计页面', {
    crop: { left: 0.09, top: 0.02, right: 0, bottom: 0.02 },
  })
  addText(slide, 'Vue 3  ·  Java 21  ·  Spring Boot 3  ·  Spring Security  ·  MyBatis-Plus  ·  Docker', { left: 548, top: 596, width: 668, height: 42 }, {
    fontSize: 20,
    bold: true,
    color: C.navy,
    alignment: 'center',
  })
  addNotes(slide,
    '227 为本地运行时 Knife4j 页面统计的 HTTP 映射数量。技术栈只作为工程证明，重点说明分层责任：Controller 接收 DTO/VO，Service 承担业务规则与事务，Mapper 负责持久化。',
    [path.join(SCREEN_DIR, '10-api-documentation.png'), path.join(ROOT, 'pom.xml'), path.join(ROOT, 'webpage', 'package.json'), path.join(ROOT, 'src', 'main', 'java')])
}

// 07 — Data trace model
{
  const slide = newSlide()
  addPageHeader(slide, 'DATA GOVERNANCE', '数据模型记录每一次变化', '产品、批次、托盘、库位和任务构成可查询、可追溯的业务事实。', 7)

  const labels = ['产品', '批次', '托盘', '库位', '任务', '库存流水', '操作日志']
  const xs = [64, 226, 388, 550, 712, 874, 1036]
  for (let index = 0; index < labels.length - 1; index += 1) {
    addArrow(slide, xs[index] + 126, 294, 36, 0, index >= 4 ? C.teal : C.blue, 2)
  }
  labels.forEach((label, index) => {
    addNode(slide, label, { left: xs[index], top: 258, width: 126, height: 72 }, {
      fill: index < 4 ? C.white : (index < 5 ? C.tealLight : C.navy),
      stroke: index < 4 ? '#95B8DE' : (index < 5 ? C.teal : C.navy),
      color: index < 5 ? C.ink : C.white,
      fontSize: index > 4 ? 20 : 22,
    })
  })
  addText(slide, '主数据', { left: 64, top: 222, width: 612, height: 28 }, { fontSize: 20, bold: true, color: C.blue, alignment: 'center' })
  addText(slide, '业务动作与历史事实', { left: 712, top: 222, width: 450, height: 28 }, { fontSize: 20, bold: true, color: C.teal, alignment: 'center' })

  addLine(slide, 64, 382, 1152, 0, C.rule, 1)
  addText(slide, '56', { left: 64, top: 410, width: 150, height: 86 }, { fontSize: 68, bold: true, color: C.blue })
  addText(slide, '个数据对象', { left: 202, top: 430, width: 180, height: 34 }, { fontSize: 25, bold: true })
  addText(slide, '54 张基础表 + 2 个视图', { left: 202, top: 468, width: 250, height: 30 }, { fontSize: 20, color: C.gray })

  addText(slide, '35', { left: 536, top: 410, width: 150, height: 86 }, { fontSize: 68, bold: true, color: C.teal })
  addText(slide, '个迁移脚本', { left: 674, top: 430, width: 180, height: 34 }, { fontSize: 25, bold: true })
  addText(slide, '按版本库口径统计', { left: 674, top: 468, width: 220, height: 30 }, { fontSize: 20, color: C.gray })

  addText(slide, '每一次库存变化都能找到来源、操作人、时间和状态。', { left: 64, top: 548, width: 1152, height: 56 }, {
    fontSize: 29,
    bold: true,
    color: C.navy,
    alignment: 'center',
    verticalAlignment: 'middle',
  })
  addNotes(slide,
    '数据对象口径来自本地开发库 information_schema：54 张 BASE TABLE、2 个 VIEW。35 个迁移脚本按版本库文件统计，不包含当前未纳入版本的工作区文件。',
    [path.join(BUILD_DIR, 'source-notes.txt'), path.join(ROOT, 'migrations'), path.join(ROOT, 'laibin.sql')])
}

// 08 — Deployment architecture
{
  const slide = newSlide()
  addPageHeader(slide, 'DEPLOYMENT', '标准架构支持持续交付', '容器化组件、环境配置和健康检查让系统可以部署、替换和排障。', 8)

  // Static connector rails first.
  addArrow(slide, 640, 218, 0, 42, C.blue, 2)
  addArrow(slide, 640, 318, 0, 48, C.blue, 2)
  addLine(slide, 230, 366, 820, 0, '#9FB8CF', 2)
  addArrow(slide, 230, 366, 0, 46, '#9FB8CF', 2)
  addArrow(slide, 640, 366, 0, 46, '#9FB8CF', 2)
  addArrow(slide, 1050, 366, 0, 46, '#9FB8CF', 2)
  addText(slide, '↙', { left: 800, top: 462, width: 72, height: 52 }, { fontSize: 40, bold: true, color: C.teal, alignment: 'center' })
  addArrow(slide, 640, 514, 0, 36, C.teal, 2)
  addLine(slide, 330, 550, 620, 0, '#95C9CB', 2)
  addArrow(slide, 330, 550, 0, 44, '#95C9CB', 2)
  addArrow(slide, 640, 550, 0, 44, '#95C9CB', 2)
  addArrow(slide, 950, 550, 0, 44, '#95C9CB', 2)

  addNode(slide, '企业用户', { left: 535, top: 176, width: 210, height: 50 }, { fill: C.navy, stroke: C.navy, color: C.white, fontSize: 22 })
  addNode(slide, 'Nginx 1.27  /  TLS  /  路由', { left: 430, top: 260, width: 420, height: 58 }, { fill: C.bgBlue, stroke: '#9DBCDD', color: C.navy, fontSize: 22 })

  addNode(slide, 'Web\nVue 3', { left: 130, top: 412, width: 200, height: 62 }, { fill: C.white, stroke: '#A9C3DE', fontSize: 20 })
  addNode(slide, 'Java Backend\nSpring Boot 3', { left: 540, top: 412, width: 200, height: 62 }, { fill: C.navy, stroke: C.navy, color: C.white, fontSize: 20 })
  addNode(slide, 'Agent Runtime\nPython', { left: 950, top: 412, width: 200, height: 62 }, { fill: C.teal, stroke: C.teal, color: C.white, fontSize: 20 })
  addNode(slide, 'MCP 受控工具', { left: 540, top: 500, width: 200, height: 50 }, { fill: C.tealLight, stroke: C.teal, color: C.navy, fontSize: 20 })

  addNode(slide, 'MySQL 8', { left: 230, top: 594, width: 200, height: 48 }, { fill: C.white, stroke: '#95C9CB', fontSize: 20 })
  addNode(slide, 'Redis 7', { left: 540, top: 594, width: 200, height: 48 }, { fill: C.white, stroke: '#95C9CB', fontSize: 20 })
  addNode(slide, '只读 RAG 制品', { left: 850, top: 594, width: 200, height: 48 }, { fill: C.white, stroke: '#95C9CB', fontSize: 20 })

  addText(slide, 'Docker Compose  ·  环境变量注入  ·  健康检查  ·  独立日志  ·  只读挂载', { left: 160, top: 650, width: 960, height: 28 }, {
    fontSize: 19,
    bold: true,
    color: C.gray,
    alignment: 'center',
  })
  addNotes(slide,
    '部署架构来自项目 Docker Compose：Nginx、Java 后端、Python Agent、Redis 和外部 MySQL；RAG 运行时制品采用只读挂载。TLS、环境变量、健康检查和日志作为交付约束说明。',
    [path.join(ROOT, 'docker', 'docker-compose.yml'), path.join(ROOT, 'deploy', 'simple', 'docker-compose.yml'), path.join(ROOT, 'docs', 'agent', 'agent-production-deployment.md')])
}

// 09 — Security chain
{
  const slide = newSlide()
  addPageHeader(slide, 'SECURITY & AUDIT', '每次请求都有安全链路', '身份、角色、业务规则和工具权限在后端逐层校验。', 9)

  const labels = ['用户登录', 'JWT', 'Spring\nSecurity', 'RBAC', '业务校验', 'Agent / Tool\n权限', '审计日志']
  const xs = [64, 226, 388, 550, 712, 874, 1036]
  labels.forEach((label, index) => {
    if (index < labels.length - 1) addArrow(slide, xs[index] + 126, 290, 36, 0, index >= 4 ? C.teal : C.blue, 2)
    addNode(slide, label, { left: xs[index], top: 248, width: 126, height: 84 }, {
      fill: index === 0 ? C.navy : (index === 6 ? C.teal : C.white),
      stroke: index === 0 ? C.navy : (index === 6 ? C.teal : '#9DBCDD'),
      color: (index === 0 || index === 6) ? C.white : C.ink,
      fontSize: label.includes('\n') ? 18 : 20,
    })
  })

  addText(slide, '权限由后端判断；Agent 调用与普通 API 走同一套规则。', { left: 100, top: 380, width: 1080, height: 62 }, {
    fontSize: 32,
    bold: true,
    color: C.navy,
    alignment: 'center',
    verticalAlignment: 'middle',
  })

  addBox(slide, { left: 64, top: 486, width: 1152, height: 110 }, { fill: C.bg, stroke: C.rule, strokeWidth: 1 })
  const rules = [
    ['后端最终授权', '登录身份不能替代业务权限'],
    ['高风险操作确认', '预览、确认和幂等控制分开'],
    ['关键动作审计', '操作日志与 Agent 调用可追溯'],
  ]
  rules.forEach((rule, index) => {
    const x = 88 + index * 376
    addText(slide, rule[0], { left: x, top: 504, width: 320, height: 32 }, { fontSize: 23, bold: true, color: index === 2 ? C.teal : C.navy })
    addText(slide, rule[1], { left: x, top: 542, width: 320, height: 34 }, { fontSize: 20, color: C.gray })
    if (index < 2) addLine(slide, x + 340, 504, 0, 72, C.rule, 1)
  })
  addNotes(slide,
    '安全链路覆盖 JWT、Spring Security、RBAC、业务参数校验和 AOP 操作日志。Agent 不直接获得数据库权限，也不能绕过后端事务和权限判断。',
    [path.join(ROOT, 'src', 'main', 'java', 'com', 'Laibin', 'SugarInventory', 'SpringSecurity'), path.join(ROOT, 'src', 'main', 'java', 'com', 'Laibin', 'SugarInventory', 'aspect', 'OperationLogAspect.java'), path.join(ROOT, 'docs', 'mcp', 'mcp-auth-design.md')])
}

// 10 — Agent in business
{
  const slide = newSlide(C.navy2)
  addPageHeader(slide, 'AGENT IN BUSINESS', '自然语言直接查询业务数据', '用户不必在多个页面之间查找和拼接信息。', 10, { dark: true })

  addText(slide, '“今天哪个仓库库存最高？”', { left: 64, top: 192, width: 500, height: 54 }, { fontSize: 29, bold: true, color: C.white })
  addLine(slide, 64, 256, 470, 0, '#29445F', 1)
  addText(slide, '“最近 7 天库存变化怎么样？”', { left: 64, top: 280, width: 500, height: 54 }, { fontSize: 29, bold: true, color: C.white })
  addLine(slide, 64, 344, 470, 0, '#29445F', 1)
  addText(slide, '“哪些批次质量异常？”', { left: 64, top: 368, width: 500, height: 54 }, { fontSize: 29, bold: true, color: C.white })

  addText(slide, '问题', { left: 70, top: 468, width: 90, height: 38 }, { fontSize: 22, bold: true, color: '#7DD3FC', alignment: 'center' })
  addText(slide, '→', { left: 162, top: 466, width: 50, height: 38 }, { fontSize: 26, bold: true, color: '#7DD3FC', alignment: 'center' })
  addText(slide, '受控工具', { left: 210, top: 468, width: 118, height: 38 }, { fontSize: 22, bold: true, color: '#7DD3FC', alignment: 'center' })
  addText(slide, '→', { left: 330, top: 466, width: 50, height: 38 }, { fontSize: 26, bold: true, color: '#7DD3FC', alignment: 'center' })
  addText(slide, '业务数据', { left: 378, top: 468, width: 118, height: 38 }, { fontSize: 22, bold: true, color: '#7DD3FC', alignment: 'center' })
  addText(slide, '→', { left: 498, top: 466, width: 50, height: 38 }, { fontSize: 26, bold: true, color: '#7DD3FC', alignment: 'center' })
  addText(slide, '结果', { left: 548, top: 468, width: 70, height: 38 }, { fontSize: 22, bold: true, color: '#7DD3FC', alignment: 'center' })
  addText(slide, '当前重点：只读查询、报表和业务预览', { left: 64, top: 552, width: 500, height: 50 }, { fontSize: 23, bold: true, color: '#55D8C7' })

  await addImage(slide, path.join(SCREEN_DIR, '09-ai-assistant.png'), { left: 650, top: 168, width: 566, height: 480 }, '项目 AI 助手真实运行界面', {
    crop: { left: 0.37, top: 0.02, right: 0, bottom: 0.02 },
    borderRadius: 10,
    stroke: '#29445F',
  })
  addNotes(slide,
    '用三个业务问题说明 Agent 的价值：理解自然语言、调用受控工具、读取真实业务数据并返回可解释结果。当前重点是只读查询、注册报表和部分业务预览，不描述为全自动写入。',
    [path.join(SCREEN_DIR, '09-ai-assistant.png'), path.join(ROOT, 'docs', 'agent', 'ai-assistant-product-goal.md'), path.join(ROOT, 'docs', 'mcp', 'mcp-tool-registry.md')])
}

// 11 — Agent boundary
{
  const slide = newSlide()
  addPageHeader(slide, 'AGENT GOVERNANCE', '模型理解，系统负责执行', '模型可以思考，但不能绕过系统规则。', 11)

  const labels = ['用户', '主 Agent', '领域 Agent', 'MCP Tool', '后端服务', '数据库']
  const xs = [64, 254, 444, 634, 824, 1014]
  labels.forEach((label, index) => {
    if (index < labels.length - 1) addArrow(slide, xs[index] + 150, 294, 40, 0, index >= 2 ? C.teal : C.blue, 2)
    addNode(slide, label, { left: xs[index], top: 254, width: 150, height: 76 }, {
      fill: index === 1 ? C.navy : (index === 3 ? C.tealLight : (index === 4 ? C.bgBlue : C.white)),
      stroke: index === 1 ? C.navy : (index === 3 ? C.teal : '#A9C3DE'),
      color: index === 1 ? C.white : C.ink,
      fontSize: 21,
    })
  })

  addBox(slide, { left: 64, top: 392, width: 552, height: 128 }, { fill: C.navy })
  addText(slide, '模型负责', { left: 92, top: 414, width: 200, height: 36 }, { fontSize: 24, bold: true, color: '#80CAFF' })
  addText(slide, '理解  ·  分析  ·  规划', { left: 92, top: 460, width: 470, height: 38 }, { fontSize: 28, bold: true, color: C.white })

  addBox(slide, { left: 664, top: 392, width: 552, height: 128 }, { fill: C.tealLight, stroke: '#A9D9D9', strokeWidth: 1 })
  addText(slide, '系统负责', { left: 692, top: 414, width: 200, height: 36 }, { fontSize: 24, bold: true, color: C.teal })
  addText(slide, '权限  ·  参数  ·  事务  ·  审计  ·  执行', { left: 692, top: 460, width: 490, height: 38 }, { fontSize: 25, bold: true, color: C.navy })

  addBox(slide, { left: 64, top: 558, width: 1152, height: 68 }, { fill: '#F7F8FA', stroke: C.rule, strokeWidth: 1 })
  addText(slide, '当前重点：L0 / L1 与部分 L2', { left: 90, top: 578, width: 430, height: 30 }, { fontSize: 21, bold: true, color: C.green })
  addText(slide, 'L3 / L4 写操作需完成权限、确认、幂等、事务和审计验收后再开放', { left: 500, top: 578, width: 680, height: 30 }, { fontSize: 20, bold: true, color: C.amber, alignment: 'right' })
  addNotes(slide,
    '主 Agent 负责理解目标和编排，领域 Agent 只看到最小上下文与工具，MCP Tool 使用参数 Schema 和白名单，后端做最终权限、事务与审计。L3/L4 不描述为已上线。',
    [path.join(ROOT, 'docs', 'agent', 'modular-agent-architecture.md'), path.join(ROOT, 'docs', 'mcp', 'mcp-tool-registry.md'), path.join(ROOT, 'docs', 'agent', 'ai-assistant-product-goal.md')])
}

// 12 — Quality and maintainability
{
  const slide = newSlide()
  addPageHeader(slide, 'DELIVERY QUALITY', '长期维护依赖工程闭环', '开发完成只是起点；测试、部署、观察、升级和回滚要一起设计。', 12)

  const labels = ['开发', '测试', '部署', '监控', '定位', '升级', '回滚']
  const xs = [74, 244, 414, 584, 754, 924, 1094]
  labels.forEach((label, index) => {
    if (index < labels.length - 1) addArrow(slide, xs[index] + 82, 286, 88, 0, index >= 3 ? C.teal : C.blue, 2)
  })
  addLine(slide, 1135, 332, 0, 70, '#93B6D8', 2)
  addLine(slide, 115, 402, 1020, 0, '#93B6D8', 2)
  addLine(slide, 115, 332, 0, 70, '#93B6D8', 2)
  addText(slide, '↑', { left: 95, top: 318, width: 40, height: 34 }, { fontSize: 28, bold: true, color: '#93B6D8', alignment: 'center' })

  labels.forEach((label, index) => {
    addNode(slide, label, { left: xs[index], top: 245, width: 82, height: 82 }, {
      geometry: 'ellipse',
      fill: index === 1 ? C.blue : (index === 3 ? C.teal : C.white),
      stroke: index === 1 ? C.blue : (index === 3 ? C.teal : '#9DBCDD'),
      color: (index === 1 || index === 3) ? C.white : C.ink,
      borderRadius: 0,
      fontSize: 21,
    })
  })
  addText(slide, '问题与版本经验返回下一轮开发', { left: 374, top: 416, width: 532, height: 32 }, { fontSize: 20, bold: true, color: C.gray, alignment: 'center' })

  addLine(slide, 64, 474, 1152, 0, C.rule, 1)
  addText(slide, '145', { left: 64, top: 500, width: 190, height: 86 }, { fontSize: 70, bold: true, color: C.blue })
  addText(slide, '个版本库测试源文件', { left: 224, top: 514, width: 310, height: 38 }, { fontSize: 27, bold: true, color: C.navy })
  addText(slide, 'Java  ·  MCP  ·  Python  ·  Web  ·  E2E', { left: 224, top: 558, width: 400, height: 30 }, { fontSize: 20, color: C.gray })

  addText(slide, '同时保留', { left: 698, top: 514, width: 160, height: 34 }, { fontSize: 23, bold: true, color: C.teal })
  addText(slide, '数据库迁移  ·  操作日志  ·  Agent 审计  ·  发布与回滚资料', { left: 698, top: 556, width: 500, height: 42 }, { fontSize: 20, bold: true, color: C.ink })
  addText(slide, '注：测试文件数量用于说明质量资产规模，不代表当前所有测试全部通过。', { left: 64, top: 630, width: 1152, height: 30 }, { fontSize: 20, color: C.gray, alignment: 'center' })
  addNotes(slide,
    '145 为版本库内测试源文件盘点数量：Java、MCP、Python、Web 单元测试和 E2E。明确提醒客户：数量不等于当前全部通过。当前工作区正在开发的未纳入版本文件不计入。',
    [path.join(BUILD_DIR, 'source-notes.txt'), path.join(ROOT, 'src', 'test'), path.join(ROOT, 'warehouse-mcp', 'src', 'test'), path.join(ROOT, 'agent-service', 'tests'), path.join(ROOT, 'webpage')])
}

// 13 — Replicability
{
  const slide = newSlide()
  addPageHeader(slide, 'REPLICABILITY', '方法可以复制到更多场景', '以一个高价值流程为起点，验证业务方法和工程体系，再按优先级扩展。', 13)

  // Spokes first.
  addLine(slide, 640, 220, 0, 96, '#A4BED8', 2)
  addLine(slide, 342, 312, 176, 40, '#A4BED8', 2)
  addLine(slide, 762, 312, 176, 40, '#A4BED8', 2)
  addLine(slide, 400, 422, 140, 74, '#8FC7CA', 2)
  addLine(slide, 740, 422, 140, 74, '#8FC7CA', 2)

  addBox(slide, { left: 500, top: 316, width: 280, height: 124 }, { fill: C.navy, geometry: 'roundRect', borderRadius: 12 })
  addText(slide, '企业软件\n交付能力', { left: 500, top: 316, width: 280, height: 124 }, { fontSize: 32, bold: true, color: C.white, alignment: 'center', verticalAlignment: 'middle' })

  addNode(slide, '业务建模', { left: 550, top: 176, width: 180, height: 58 }, { fill: C.bgBlue, stroke: '#9DBCDD', color: C.navy })
  addNode(slide, '软件研发', { left: 190, top: 276, width: 180, height: 58 }, { fill: C.white, stroke: '#9DBCDD', color: C.navy })
  addNode(slide, '数据治理', { left: 910, top: 276, width: 180, height: 58 }, { fill: C.white, stroke: '#9DBCDD', color: C.navy })
  addNode(slide, '部署运维', { left: 310, top: 474, width: 180, height: 58 }, { fill: C.tealLight, stroke: '#8FC7CA', color: C.navy })
  addNode(slide, 'AI Agent', { left: 790, top: 474, width: 180, height: 58 }, { fill: C.tealLight, stroke: '#8FC7CA', color: C.navy })

  addText(slide, '制造', { left: 74, top: 198, width: 100, height: 34 }, { fontSize: 24, bold: true, color: C.gray })
  addText(slide, '仓储', { left: 74, top: 252, width: 100, height: 34 }, { fontSize: 24, bold: true, color: C.gray })
  addText(slide, '供应链', { left: 74, top: 306, width: 120, height: 34 }, { fontSize: 24, bold: true, color: C.gray })
  addText(slide, '质量管理', { left: 1090, top: 198, width: 130, height: 34 }, { fontSize: 24, bold: true, color: C.gray, alignment: 'right' })
  addText(slide, '设备管理', { left: 1090, top: 252, width: 130, height: 34 }, { fontSize: 24, bold: true, color: C.gray, alignment: 'right' })
  addText(slide, '运营分析', { left: 1090, top: 306, width: 130, height: 34 }, { fontSize: 24, bold: true, color: C.gray, alignment: 'right' })

  addBox(slide, { left: 180, top: 582, width: 920, height: 58 }, { fill: C.bgBlue })
  addText(slide, '从一个高价值业务流程开始，先做试点，再逐步扩大。', { left: 180, top: 582, width: 920, height: 58 }, {
    fontSize: 27,
    bold: true,
    color: C.navy,
    alignment: 'center',
    verticalAlignment: 'middle',
  })
  addNotes(slide,
    '结束时回到核心定位：数字仓储只是证明样例，业务建模、软件研发、数据治理、部署运维和 Agent 治理方法可以复制。建议从一个有明确边界和验收标准的高价值流程开始。',
    [path.join(ROOT, 'README.md'), path.join(ROOT, 'docs', 'mcp-analysis', 'system-capability-map.md')])
}

await fs.mkdir(RENDER_DIR, { recursive: true })
for (const [index, slide] of presentation.slides.items.entries()) {
  const png = await presentation.export({ slide, format: 'png', scale: 1 })
  await fs.writeFile(path.join(RENDER_DIR, `slide-${String(index + 1).padStart(2, '0')}.png`), new Uint8Array(await png.arrayBuffer()))
  const layout = await slide.export({ format: 'layout' })
  await fs.writeFile(path.join(RENDER_DIR, `slide-${String(index + 1).padStart(2, '0')}.layout.json`), await layout.text(), 'utf8')
}

const montage = await presentation.export({ format: 'webp', montage: true, scale: 1 })
await fs.writeFile(path.join(BUILD_DIR, 'deck-montage.webp'), new Uint8Array(await montage.arrayBuffer()))

const inspection = await presentation.inspect({
  kind: 'slide,textbox,shape,image,notes',
  maxChars: 120000,
})
await fs.writeFile(path.join(BUILD_DIR, 'presentation-inspect.ndjson'), inspection.ndjson, 'utf8')

const pptx = await PresentationFile.exportPptx(presentation)
await pptx.save(FINAL_PPTX)

console.log(FINAL_PPTX)
