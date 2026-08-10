import fs from 'node:fs/promises';
import path from 'node:path';
import { Presentation, PresentationFile } from '@oai/artifact-tool';

const ROOT = 'D:\\Laibin\\LaibinSugarInventory';
const BUILD_DIR = path.join(ROOT, 'output', 'ppt-build', 'enterprise-solution-deck-20260809');
const SCREEN_DIR = path.join(ROOT, 'output', 'playwright', 'solution-deck-20260809');
const FINAL_PPTX = path.join(ROOT, 'output', '企业软件解决方案-数字仓储平台示例.pptx');

const COLORS = {
  canvas: '#FFFFFF',
  ink: '#0B1220',
  muted: '#667085',
  subtle: '#98A2B3',
  panel: '#F2F4F7',
  panelBlue: '#EAF2FF',
  rule: '#D0D5DD',
  accent: '#1769FF',
  accentLight: '#6DCBF4',
  green: '#12B76A',
  amber: '#F79009',
  red: '#F04438',
};

const FONT = 'Microsoft YaHei';
const SLIDE_W = 1280;
const SLIDE_H = 720;

const presentation = Presentation.create({ slideSize: { width: SLIDE_W, height: SLIDE_H } });

async function imageBytes(filePath) {
  return new Uint8Array(await fs.readFile(filePath));
}

function addText(slide, value, position, options = {}) {
  const {
    name,
    fontSize = 24,
    bold = false,
    color = COLORS.ink,
    alignment = 'left',
    verticalAlignment = 'top',
    fill = 'none',
    line = { style: 'solid', fill: 'none', width: 0 },
    geometry = 'textbox',
    autoFit = 'shrinkText',
    insets = { top: 0, right: 0, bottom: 0, left: 0 },
  } = options;
  const shape = slide.shapes.add({
    geometry,
    name,
    position,
    fill,
    line,
  });
  shape.text = value;
  shape.text.style = {
    fontSize,
    typeface: FONT,
    bold,
    color,
    alignment,
    verticalAlignment,
    autoFit,
    insets,
  };
  return shape;
}

function addBox(slide, position, options = {}) {
  return slide.shapes.add({
    geometry: options.geometry || 'rect',
    name: options.name,
    position,
    fill: options.fill || COLORS.panel,
    line: options.line || { style: 'solid', fill: options.stroke || 'none', width: options.strokeWidth || 0 },
    ...(options.borderRadius ? { borderRadius: options.borderRadius } : {}),
  });
}

function addLine(slide, left, top, width, height = 0, color = COLORS.rule, weight = 1) {
  return slide.shapes.add({
    geometry: 'straightConnector1',
    position: { left, top, width, height },
    fill: 'none',
    line: { style: 'solid', fill: color, width: weight },
  });
}

function addSlideTitle(slide, title, pageNumber) {
  addText(slide, title, { left: 42, top: 34, width: 1196, height: 68 }, {
    fontSize: 48,
    bold: true,
    name: `slide-${pageNumber}-title`,
    autoFit: 'shrinkText',
  });
  addLine(slide, 42, 682, 1128, 0, COLORS.rule, 1);
  addText(slide, String(pageNumber).padStart(2, '0'), { left: 1180, top: 668, width: 58, height: 24 }, {
    fontSize: 14,
    color: COLORS.subtle,
    alignment: 'right',
    verticalAlignment: 'middle',
  });
}

function addKicker(slide, textValue, left = 42, top = 32) {
  addText(slide, textValue, { left, top, width: 420, height: 28 }, {
    fontSize: 18,
    bold: true,
    color: COLORS.accent,
  });
}

function addBulletList(slide, items, left, top, width, options = {}) {
  const fontSize = options.fontSize || 23;
  const lineHeight = options.lineHeight || 52;
  items.forEach((item, index) => {
    const y = top + index * lineHeight;
    addBox(slide, { left, top: y + 12, width: 8, height: 8 }, { fill: options.bulletColor || COLORS.accent, geometry: 'ellipse' });
    addText(slide, item, { left: left + 22, top: y, width: width - 22, height: lineHeight - 2 }, {
      fontSize,
      color: options.color || COLORS.ink,
      bold: options.bold || false,
      verticalAlignment: 'top',
    });
  });
}

function addMetric(slide, value, label, note, left, top, width, accent = COLORS.accent) {
  addText(slide, value, { left, top, width, height: 96 }, {
    fontSize: 68,
    bold: true,
    color: accent,
    verticalAlignment: 'bottom',
  });
  addText(slide, label, { left, top: top + 103, width, height: 36 }, {
    fontSize: 27,
    bold: true,
  });
  if (note) {
    addText(slide, note, { left, top: top + 145, width, height: 56 }, {
      fontSize: 20,
      color: COLORS.muted,
    });
  }
}

async function addImage(slide, filePath, position, alt, options = {}) {
  if (options.backing !== false) {
    addBox(slide, position, {
      fill: options.backingFill || COLORS.panel,
      geometry: 'roundRect',
      stroke: COLORS.rule,
      strokeWidth: 1,
      borderRadius: 12,
    });
  }
  return slide.images.add({
    blob: await imageBytes(filePath),
    contentType: 'image/png',
    alt,
    fit: options.fit || 'cover',
    position,
    geometry: options.geometry || 'roundRect',
    borderRadius: options.borderRadius || 10,
    ...(options.crop ? { crop: options.crop } : {}),
  });
}

function addCaption(slide, caption, left, top, width, align = 'left') {
  addText(slide, caption, { left, top, width, height: 28 }, {
    fontSize: 17,
    color: COLORS.muted,
    alignment: align,
  });
}

function addNotes(slide, notes, sources) {
  const text = [
    notes,
    '',
    '[Sources]',
    ...sources.map((source) => `- ${source}`),
  ].join('\n');
  slide.speakerNotes.textFrame.setText(text);
  slide.speakerNotes.setVisible(true);
}

function newSlide() {
  const slide = presentation.slides.add();
  slide.background.fill = COLORS.canvas;
  return slide;
}

// Slide 01 — adapted from Codex Grid slide-01 cover hierarchy.
{
  const slide = newSlide();
  addKicker(slide, 'SOFTWARE SOLUTION / 2026', 42, 42);
  addText(slide, '企业级软件解决方案能力展示', { left: 42, top: 184, width: 1110, height: 170 }, {
    fontSize: 72,
    bold: true,
    verticalAlignment: 'bottom',
    autoFit: 'none',
    name: 'deck-title',
  });
  addText(slide, '以数字仓储平台为例', { left: 42, top: 370, width: 700, height: 58 }, {
    fontSize: 38,
    bold: true,
    color: COLORS.accent,
  });
  addLine(slide, 42, 462, 160, 0, COLORS.accent, 5);
  addText(slide, '前端 · 后端 · 数据库 · 部署 · 运维 · Agent', { left: 42, top: 498, width: 900, height: 52 }, {
    fontSize: 28,
    color: COLORS.muted,
  });
  addText(slide, '用真实系统证明：能做成，也能长期运行和演进。', { left: 42, top: 574, width: 900, height: 46 }, {
    fontSize: 22,
    color: COLORS.subtle,
  });
  addNotes(slide,
    '开场强调这是一份能力展示，不是单一产品推销。数字仓储只是样例，重点是可复制的软件工程方法。',
    [path.join(ROOT, 'README.md')]);
}

// Slide 02 — three-column composition based on Codex Grid slide-07 / slide-19.
{
  const slide = newSlide();
  addSlideTitle(slide, '企业需要的不是一个页面，而是一套可持续运行的业务系统', 2);
  addText(slide, '真实业务闭环、可靠工程底座和受控智能化，必须同时成立。', { left: 42, top: 118, width: 1100, height: 42 }, {
    fontSize: 24,
    color: COLORS.muted,
  });
  const columns = [
    { x: 42, n: '01', h: '业务闭环', b: '仓储、生产、质量、二维码与任务流围绕真实单据和状态推进。' },
    { x: 448, n: '02', h: '工程底座', b: '前后端分层、数据库迁移、鉴权审计与容器化交付形成长期基础。' },
    { x: 854, n: '03', h: '智能演进', b: 'Agent、MCP、RAG、报表与受控预览在明确安全边界内逐步开放。' },
  ];
  columns.forEach((column) => {
    addLine(slide, column.x, 218, 330, 0, COLORS.accent, 4);
    addText(slide, column.n, { left: column.x, top: 242, width: 100, height: 72 }, { fontSize: 48, bold: true, color: COLORS.accent });
    addText(slide, column.h, { left: column.x, top: 326, width: 330, height: 48 }, { fontSize: 31, bold: true });
    addText(slide, column.b, { left: column.x, top: 390, width: 330, height: 126 }, { fontSize: 23, color: COLORS.muted });
  });
  addBox(slide, { left: 42, top: 580, width: 1196, height: 62 }, { fill: COLORS.panelBlue });
  addText(slide, '核心判断：软件成熟度 = 业务正确性 × 工程可靠性 × 演进治理', { left: 66, top: 591, width: 1148, height: 40 }, {
    fontSize: 26,
    bold: true,
    color: COLORS.accent,
    verticalAlignment: 'middle',
  });
  addNotes(slide,
    '本页建立评价框架。后续每一页都会给出这个项目中的真实证据，而不是只列技术名词。',
    [path.join(ROOT, 'README.md'), path.join(ROOT, 'docs', 'agent', 'ai-assistant-product-goal.md')]);
}

// Slide 03 — image-led split adapted from Codex Grid slide-08.
{
  const slide = newSlide();
  addSlideTitle(slide, '一个项目，覆盖管理端、现场端与智能助手', 3);
  addText(slide, '同一业务语义，通过不同终端服务管理者、现场人员和自然语言用户。', { left: 42, top: 114, width: 1050, height: 40 }, {
    fontSize: 23,
    color: COLORS.muted,
  });
  await addImage(slide, path.join(SCREEN_DIR, '02-home-dashboard.png'), { left: 42, top: 166, width: 760, height: 428 }, '数字仓储 Web 管理端首页看板');
  addCaption(slide, 'Web 管理端：全局看板、复杂查询与管理操作', 42, 606, 760);
  await addImage(slide, path.join(ROOT, 'docs', '小程序上下文', '登录页原型.png'), { left: 848, top: 156, width: 216, height: 462 }, '数字仓储微信小程序现场作业端', { fit: 'contain' });
  addText(slide, '管理', { left: 1096, top: 228, width: 120, height: 42 }, { fontSize: 29, bold: true });
  addText(slide, 'Web 工作台', { left: 1096, top: 272, width: 142, height: 34 }, { fontSize: 20, color: COLORS.muted });
  addText(slide, '现场', { left: 1096, top: 360, width: 120, height: 42 }, { fontSize: 29, bold: true });
  addText(slide, '微信小程序', { left: 1096, top: 404, width: 142, height: 34 }, { fontSize: 20, color: COLORS.muted });
  addText(slide, '对话', { left: 1096, top: 492, width: 120, height: 42 }, { fontSize: 29, bold: true });
  addText(slide, 'AI 助手', { left: 1096, top: 536, width: 142, height: 34 }, { fontSize: 20, color: COLORS.muted });
  addNotes(slide,
    '强调多端不是简单复制页面：管理端处理全局视角，小程序服务现场扫码与任务，AI 助手承接自然语言入口。',
    [path.join(ROOT, 'webpage', 'README.md'), path.join(ROOT, 'LaibinSugarInventoryWxAPP', 'package.json'), path.join(ROOT, 'docs', '小程序上下文', '登录页原型.png')]);
}

// Slide 04 — screenshot + topic rail based on Codex Grid slide-15.
{
  const slide = newSlide();
  addSlideTitle(slide, '业务能力已贯穿仓储、生产、质量与治理', 4);
  await addImage(slide, path.join(SCREEN_DIR, '03-warehouse-map.png'), { left: 42, top: 132, width: 742, height: 418 }, '仓库平面图与库位状态看板');
  addCaption(slide, '真实运行界面：库位状态、筛选与空间定位', 42, 562, 742);
  const topics = [
    ['仓储运营', '库存汇总、库位平面、入库/出库/调拨'],
    ['生产协同', '生产订单、领料、产出贴码与来源追溯'],
    ['质量控制', '化验、标准、判定、异常与覆盖分析'],
    ['治理运营', '员工/RBAC、操作日志、报表与审计'],
  ];
  topics.forEach((topic, index) => {
    const y = 156 + index * 102;
    addText(slide, String(index + 1).padStart(2, '0'), { left: 824, top: y, width: 48, height: 32 }, { fontSize: 18, bold: true, color: COLORS.accent });
    addText(slide, topic[0], { left: 882, top: y - 4, width: 300, height: 38 }, { fontSize: 27, bold: true });
    addText(slide, topic[1], { left: 882, top: y + 39, width: 336, height: 46 }, { fontSize: 19, color: COLORS.muted });
  });
  addBox(slide, { left: 42, top: 614, width: 1196, height: 46 }, { fill: COLORS.panelBlue });
  addText(slide, '产品—批次—托盘—库位—任务，形成可查询、可追溯、可审计的关联事实。', { left: 62, top: 621, width: 1156, height: 32 }, { fontSize: 22, bold: true, color: COLORS.accent });
  addNotes(slide,
    '用业务域说明平台不是孤立模块集合。特别指出核心实体关系构成后续报表、追溯与 Agent 的事实基础。',
    [path.join(ROOT, 'README.md'), path.join(ROOT, 'docs', 'mcp-analysis', 'system-capability-map.md')]);
}

// Slide 05 — dual image evidence layout.
{
  const slide = newSlide();
  addSlideTitle(slide, '前端不仅展示数据，也承载复杂业务操作', 5);
  await addImage(slide, path.join(SCREEN_DIR, '05-production-orders.png'), { left: 42, top: 126, width: 596, height: 336 }, '生产订单管理界面');
  await addImage(slide, path.join(SCREEN_DIR, '06-quality-assay.png'), { left: 662, top: 126, width: 576, height: 336 }, '化验管理界面');
  addCaption(slide, '生产订单：领料、产出、贴码与进度', 42, 474, 596);
  addCaption(slide, '质量管理：筛选、状态、标准与记录', 662, 474, 576);
  addLine(slide, 42, 524, 1196, 0, COLORS.rule, 1);
  addText(slide, '技术基础', { left: 42, top: 550, width: 150, height: 38 }, { fontSize: 25, bold: true, color: COLORS.accent });
  addText(slide, 'Vue 3 · Vite · Element Plus · Pinia · ECharts · Vue Router · i18n', { left: 198, top: 548, width: 1040, height: 42 }, { fontSize: 25, bold: true });
  addBulletList(slide, [
    '动态路由、权限菜单、多页签、复杂表格与业务弹窗',
    '响应式布局、扫码/打印、统一错误处理和展示安全适配',
  ], 42, 602, 1196, { fontSize: 21, lineHeight: 34 });
  addNotes(slide,
    '把前端能力解释为业务交付能力：复杂状态、流程联动、权限可见性、反馈和打印，而不是只说使用了 Vue。',
    [path.join(ROOT, 'webpage', 'package.json'), path.join(ROOT, 'webpage', 'src', 'router', 'index.js')]);
}

// Slide 06 — metric + evidence frame adapted from Codex Grid slide-20.
{
  const slide = newSlide();
  addSlideTitle(slide, '前后端通过清晰分层承载 227 个 HTTP 接口', 6);
  addMetric(slide, '36', 'Controller', '按业务域划分入口', 42, 142, 138);
  addMetric(slide, '227', 'HTTP API', '运行时文档可检索', 202, 142, 150);
  addMetric(slide, '41', 'Vue 组件', '页面与业务组件', 374, 142, 130);
  addBulletList(slide, [
    'Controller 仅接收/返回 DTO、VO',
    'Service 承担业务规则与事务边界',
    'Mapper / PO 对接 MySQL 持久化',
    '统一 Result、分页、校验与异常处理',
  ], 42, 390, 450, { fontSize: 21, lineHeight: 48 });
  await addImage(slide, path.join(SCREEN_DIR, '10-api-documentation.png'), { left: 530, top: 130, width: 708, height: 468 }, 'Knife4j OpenAPI 在线接口文档');
  addCaption(slide, 'Knife4j / OpenAPI：接口分组、方法统计与在线检索', 530, 612, 708);
  addNotes(slide,
    '接口数量来自代码扫描与本地运行时 Knife4j 页面交叉验证：PUT 12、GET 65、DELETE 13、POST 136、PATCH 1，共 227。',
    [path.join(ROOT, 'pom.xml'), path.join(ROOT, 'docs', 'openapi.json'), path.join(ROOT, 'AGENTS.md')]);
}

// Slide 07 — data evidence table inspired by Codex Grid slide-14.
{
  const slide = newSlide();
  addSlideTitle(slide, '数据模型把实时库存、追溯与审计统一到同一事实基础', 7);
  addMetric(slide, '56', '数据表', '本地开发库实测', 42, 134, 150);
  addMetric(slide, '35', '迁移脚本', '按日期可追溯演进', 230, 134, 160);
  addText(slide, '数据分层', { left: 438, top: 142, width: 170, height: 40 }, { fontSize: 27, bold: true, color: COLORS.accent });
  const rows = [
    ['主数据', '产品、库位、筛网、质量标准、员工与角色'],
    ['业务记录', '库存、出入库、托盘任务、生产订单、化验'],
    ['历史事实', '库存流水、日终快照、守恒对账、流转时间线'],
    ['审计资产', '操作日志、Agent 会话/调用、报表快照与导出审计'],
  ];
  rows.forEach((row, index) => {
    const y = 202 + index * 90;
    addBox(slide, { left: 438, top: y, width: 800, height: 72 }, { fill: index % 2 === 0 ? COLORS.panel : '#FAFAFA' });
    addText(slide, row[0], { left: 462, top: y + 17, width: 140, height: 38 }, { fontSize: 24, bold: true });
    addText(slide, row[1], { left: 624, top: y + 17, width: 590, height: 38 }, { fontSize: 22, color: COLORS.muted });
  });
  addLine(slide, 42, 552, 1196, 0, COLORS.rule, 1);
  addBulletList(slide, [
    '事务、行锁与唯一约束保护关键库存动作',
    '迁移脚本让结构变更可审查、可部署、可回退',
    '快照、哈希与不可变报表确保结果可复核',
  ], 42, 582, 1196, { fontSize: 21, lineHeight: 31 });
  addNotes(slide,
    '56 是本地开发数据库 information_schema 的实际表数；35 是 migrations 目录 SQL 文件数。重点解释数据事实层如何支撑追溯、报表与 Agent。',
    [path.join(ROOT, 'laibin.sql'), path.join(ROOT, 'migrations'), path.join(ROOT, 'docs', 'agent', 'inventory-trend-data-foundation-implementation-2026-07-29.md')]);
}

// Slide 08 — native deployment architecture diagram. Connectors are authored before nodes.
{
  const slide = newSlide();
  addSlideTitle(slide, '部署形态可从本地联调平滑过渡到容器化交付', 8);
  addText(slide, 'Compose 编排 Nginx、Java 后端、Python Agent 与 Redis；MySQL 作为外部持久化数据源。', { left: 42, top: 112, width: 1160, height: 40 }, { fontSize: 23, color: COLORS.muted });
  addLine(slide, 300, 340, 120, 0, COLORS.accent, 3);
  addLine(slide, 846, 340, 120, 0, COLORS.accent, 3);
  addLine(slide, 632, 486, 0, 74, COLORS.rule, 2);
  addBox(slide, { left: 54, top: 238, width: 246, height: 202 }, { fill: COLORS.panelBlue, geometry: 'roundRect', stroke: COLORS.rule, strokeWidth: 1, borderRadius: 12 });
  addText(slide, '访问层', { left: 78, top: 260, width: 200, height: 42 }, { fontSize: 29, bold: true, color: COLORS.accent });
  addText(slide, 'Web 管理端\n微信小程序\nAI 助手入口', { left: 78, top: 320, width: 190, height: 102 }, { fontSize: 23, color: COLORS.ink, verticalAlignment: 'middle' });
  addBox(slide, { left: 420, top: 186, width: 426, height: 300 }, { fill: COLORS.panel, geometry: 'roundRect', stroke: COLORS.rule, strokeWidth: 1, borderRadius: 12 });
  addText(slide, '应用服务层', { left: 446, top: 210, width: 250, height: 42 }, { fontSize: 29, bold: true });
  const appLines = [
    'Nginx 1.27｜TLS、静态资源、反向代理',
    'Java 21｜Spring Boot 业务与权限中心',
    'Python｜模块化 Agent Runtime',
    'MCP JAR｜受控工具与后端访问',
  ];
  appLines.forEach((line, index) => {
    const y = 282 + index * 49;
    addLine(slide, 446, y + 35, 372, 0, COLORS.rule, 1);
    addText(slide, line, { left: 446, top: y, width: 372, height: 34 }, { fontSize: 20, bold: index === 1 });
  });
  addBox(slide, { left: 966, top: 226, width: 252, height: 228 }, { fill: '#FAFAFA', geometry: 'roundRect', stroke: COLORS.rule, strokeWidth: 1, borderRadius: 12 });
  addText(slide, '数据与状态层', { left: 990, top: 250, width: 210, height: 42 }, { fontSize: 29, bold: true });
  addText(slide, 'MySQL 8\nRedis 7\n只读 RAG 版本制品', { left: 990, top: 318, width: 210, height: 112 }, { fontSize: 23, color: COLORS.ink });
  addBox(slide, { left: 420, top: 560, width: 796, height: 78 }, { fill: COLORS.panelBlue });
  addText(slide, '交付策略', { left: 444, top: 579, width: 130, height: 38 }, { fontSize: 25, bold: true, color: COLORS.accent });
  addText(slide, '环境变量注入密钥 · 健康检查 · 只读挂载 · 独立日志 · 可替换运行组件', { left: 580, top: 579, width: 612, height: 38 }, { fontSize: 22, bold: true });
  addNotes(slide,
    '说明部署组件和责任边界。生产密钥由平台注入，不进入镜像、仓库或普通日志；RAG 运行时使用只读版本制品。',
    [path.join(ROOT, 'deploy', 'simple', 'docker-compose.yml'), path.join(ROOT, 'docs', 'agent', 'agent-production-deployment.md')]);
}

// Slide 09 — native security chain. Lines are authored before nodes.
{
  const slide = newSlide();
  addSlideTitle(slide, '安全不是附加项，而是贯穿身份、权限与审计', 9);
  addText(slide, '从一次登录到一次 Agent 工具调用，每个环节都有独立校验责任。', { left: 42, top: 116, width: 1000, height: 40 }, { fontSize: 23, color: COLORS.muted });
  addLine(slide, 274, 328, 46, 0, COLORS.accent, 3);
  addLine(slide, 574, 328, 46, 0, COLORS.accent, 3);
  addLine(slide, 874, 328, 46, 0, COLORS.accent, 3);
  const stages = [
    { x: 42, title: '身份可信', body: 'JWT Bearer\nAgent 委派身份\n会话撤销', color: COLORS.panelBlue },
    { x: 320, title: '权限可证', body: 'Spring Security\n@PreAuthorize\nRBAC 权限矩阵', color: COLORS.panel },
    { x: 620, title: '业务安全', body: '参数校验\n事务与锁\n预览/确认门禁', color: COLORS.panel },
    { x: 920, title: '全程审计', body: 'AOP 操作日志\n工具调用审计\n不可变报表快照', color: COLORS.panelBlue },
  ];
  stages.forEach((stage, index) => {
    addBox(slide, { left: stage.x, top: 206, width: index === 0 ? 232 : 254, height: 244 }, { fill: stage.color, geometry: 'roundRect', stroke: COLORS.rule, strokeWidth: 1, borderRadius: 12 });
    addText(slide, String(index + 1).padStart(2, '0'), { left: stage.x + 22, top: 230, width: 54, height: 32 }, { fontSize: 17, bold: true, color: COLORS.accent });
    addText(slide, stage.title, { left: stage.x + 22, top: 278, width: 205, height: 44 }, { fontSize: 29, bold: true });
    addText(slide, stage.body, { left: stage.x + 22, top: 342, width: 206, height: 90 }, { fontSize: 22, color: COLORS.muted });
  });
  addBox(slide, { left: 42, top: 520, width: 1196, height: 108 }, { fill: '#0B1220' });
  addText(slide, '三条红线', { left: 68, top: 542, width: 150, height: 38 }, { fontSize: 25, bold: true, color: '#FFFFFF' });
  addText(slide, '不开放任意 SQL / HTTP 代理　·　Agent 不猜业务 ID　·　普通用户不见 token、raw JSON 与内部堆栈', { left: 222, top: 542, width: 980, height: 58 }, { fontSize: 23, bold: true, color: '#FFFFFF', verticalAlignment: 'middle' });
  addNotes(slide,
    '强调 Agent 不替代后端权限判断。即使模型判断正确，也必须通过 Runtime、MCP 和 Spring Security 的确定性校验。',
    [path.join(ROOT, 'src', 'main', 'java', 'com', 'Laibin', 'SugarInventory', 'SpringSecurity', 'SecurityConfig.java'), path.join(ROOT, 'docs', 'mcp', 'agent-session-authorization.md'), path.join(ROOT, 'docs', 'agent', 'ai-assistant-product-goal.md')]);
}

// Slide 10 — screenshot-led Agent experience.
{
  const slide = newSlide();
  addSlideTitle(slide, 'Agent 已成为业务入口，而不是聊天外挂', 10);
  await addImage(slide, path.join(SCREEN_DIR, '09-ai-assistant.png'), { left: 42, top: 126, width: 838, height: 472 }, '数字仓储 AI 助手界面');
  addCaption(slide, '真实运行界面：会话连接、建议问题、业务范围与自然语言输入', 42, 610, 838);
  addText(slide, '用户看到', { left: 920, top: 154, width: 260, height: 42 }, { fontSize: 29, bold: true, color: COLORS.accent });
  addBulletList(slide, [
    '自然语言回答',
    '业务卡片与候选项',
    '进度、追问与建议',
    '人话错误解释',
  ], 920, 218, 300, { fontSize: 22, lineHeight: 48 });
  addText(slide, '系统保障', { left: 920, top: 430, width: 260, height: 42 }, { fontSize: 29, bold: true });
  addBulletList(slide, [
    '上下文记忆与专家路由',
    '安全事实适配与权限审计',
    '只读查询、报表和预览',
  ], 920, 492, 300, { fontSize: 21, lineHeight: 45, bulletColor: COLORS.green });
  addNotes(slide,
    '用户体验刻意隐藏 toolName、内部 ID 和 raw JSON。普通用户感知到的是业务进度与结果，管理员才可看脱敏调试摘要。',
    [path.join(ROOT, 'webpage', 'src', 'components', 'AgentAssistant.vue'), path.join(ROOT, 'docs', 'agent', 'ai-assistant-product-goal.md')]);
}

// Slide 11 — Agent/MCP flow. Connectors are authored before nodes.
{
  const slide = newSlide();
  addSlideTitle(slide, 'Agent 的能力边界由模型之外的系统来保证', 11);
  addText(slide, '模型负责理解与分析；Runtime、MCP 与后端负责授权、参数、事实和完成判定。', { left: 42, top: 116, width: 1160, height: 40 }, { fontSize: 23, color: COLORS.muted });
  const nodeXs = [42, 282, 522, 762, 1002];
  for (let i = 0; i < nodeXs.length - 1; i += 1) addLine(slide, nodeXs[i] + 196, 284, 44, 0, COLORS.accent, 3);
  const nodes = [
    ['用户目标', '自然语言与上下文'],
    ['主 Agent', '理解目标、追问、委派'],
    ['最小权限专家', '只见本域上下文与工具'],
    ['MCP Tool', 'Schema、白名单、受控参数'],
    ['后端事实', '权限、事务、审计、数据'],
  ];
  nodes.forEach((node, index) => {
    addBox(slide, { left: nodeXs[index], top: 214, width: 196, height: 140 }, { fill: index === 4 ? COLORS.panelBlue : COLORS.panel, geometry: 'roundRect', stroke: COLORS.rule, strokeWidth: 1, borderRadius: 10 });
    addText(slide, node[0], { left: nodeXs[index] + 16, top: 236, width: 164, height: 38 }, { fontSize: 25, bold: true, alignment: 'center' });
    addText(slide, node[1], { left: nodeXs[index] + 14, top: 288, width: 168, height: 48 }, { fontSize: 17, color: COLORS.muted, alignment: 'center' });
  });
  addBox(slide, { left: 42, top: 402, width: 1196, height: 74 }, { fill: '#FAFAFA', stroke: COLORS.rule, strokeWidth: 1 });
  addText(slide, 'Runtime 安全校验', { left: 66, top: 420, width: 220, height: 36 }, { fontSize: 24, bold: true, color: COLORS.accent });
  addText(slide, '工具白名单 · 参数 Schema · 受控实体引用 · 调用次数 · 用户权限 · CompletionEvaluator', { left: 292, top: 420, width: 920, height: 36 }, { fontSize: 22, bold: true });
  const risks = [
    { x: 42, label: 'L0 / L1', body: '帮助与实时只读查询', fill: '#ECFDF3', color: COLORS.green },
    { x: 440, label: 'L2', body: '预览、校验与登记报表', fill: COLORS.panelBlue, color: COLORS.accent },
    { x: 838, label: 'L3 / L4', body: '未闭合门禁前不开放', fill: '#FFF4ED', color: COLORS.amber },
  ];
  risks.forEach((risk) => {
    addBox(slide, { left: risk.x, top: 520, width: 360, height: 108 }, { fill: risk.fill });
    addText(slide, risk.label, { left: risk.x + 20, top: 538, width: 105, height: 36 }, { fontSize: 25, bold: true, color: risk.color });
    addText(slide, risk.body, { left: risk.x + 20, top: 579, width: 320, height: 32 }, { fontSize: 21, bold: true });
  });
  addNotes(slide,
    '当前第一版只允许 L0、L1 和部分 L2。未来写操作必须经过 preview、用户确认、executionToken、idempotencyKey、执行前重检和审计。',
    [path.join(ROOT, 'docs', 'mcp', 'mcp-tool-registry.md'), path.join(ROOT, 'agent-service', 'README.md'), path.join(ROOT, 'warehouse-mcp', 'README.md')]);
}

// Slide 12 — large metric + maintenance rail based on Codex Grid slide-19.
{
  const slide = newSlide();
  addSlideTitle(slide, '可测试、可观测、可回滚，才称得上可维护', 12);
  addText(slide, '145', { left: 42, top: 164, width: 290, height: 150 }, { fontSize: 100, bold: true, color: COLORS.accent, verticalAlignment: 'bottom' });
  addText(slide, '测试源文件', { left: 42, top: 326, width: 290, height: 48 }, { fontSize: 31, bold: true });
  addText(slide, 'Java / MCP / Python / Web / E2E', { left: 42, top: 385, width: 320, height: 72 }, { fontSize: 22, color: COLORS.muted });
  const maintainability = [
    ['自动化验证', '单元、集成、权限矩阵、浏览器旅程与发布门禁'],
    ['可观测性', '操作日志、Agent 调用审计、错误分类与耗时记录'],
    ['可恢复性', '版本化迁移、不可变报表、RAG 发布切换与回滚设计'],
    ['运维资料', '部署说明、故障排查、验收记录与运行手册'],
  ];
  maintainability.forEach((item, index) => {
    const y = 150 + index * 104;
    addText(slide, item[0], { left: 426, top: y, width: 184, height: 38 }, { fontSize: 26, bold: true, color: index === 0 ? COLORS.accent : COLORS.ink });
    addText(slide, item[1], { left: 630, top: y, width: 580, height: 56 }, { fontSize: 22, color: COLORS.muted });
    if (index < maintainability.length - 1) addLine(slide, 426, y + 77, 812, 0, COLORS.rule, 1);
  });
  addBox(slide, { left: 42, top: 584, width: 1196, height: 58 }, { fill: COLORS.panelBlue });
  addText(slide, '质量资产覆盖代码、数据、权限、交互和运行现场，而不是只做接口自测。', { left: 66, top: 596, width: 1148, height: 34 }, { fontSize: 24, bold: true, color: COLORS.accent });
  addNotes(slide,
    '145 是测试源文件清单统计，不代表当前工作区版本的实时通过率。对外表达重点是质量资产覆盖范围与可追溯的验收资料。',
    [path.join(ROOT, 'src', 'test'), path.join(ROOT, 'warehouse-mcp', 'src', 'test'), path.join(ROOT, 'agent-service', 'tests'), path.join(ROOT, 'webpage', 'src'), path.join(ROOT, 'docs', 'agent', 'agent-production-troubleshooting.md')]);
}

// Slide 13 — timeline adapted from Codex Grid slide-17.
{
  const slide = newSlide();
  addSlideTitle(slide, '成熟度来自明确的准入门槛，而不是功能数量', 13);
  addText(slide, '每个高风险能力都必须先证明权限、确认、幂等、事务与审计闭环。', { left: 42, top: 116, width: 1100, height: 40 }, { fontSize: 23, color: COLORS.muted });
  addLine(slide, 76, 336, 1088, 0, COLORS.ink, 2);
  const milestones = [
    { x: 76, label: '现在', title: '安全查询与预览', body: 'L0/L1 只读工具\nL2 预览与登记报表\n业务卡片和历史重开' },
    { x: 468, label: '下一步', title: '执行安全层', body: 'executionToken\n幂等键与内容绑定\nHITL、过期与重检' },
    { x: 860, label: '未来', title: '有限写入与优化', body: '逐项开放 L3\n群聊只做跳转确认\n预测与仿真先做建议' },
  ];
  milestones.forEach((m, index) => {
    addBox(slide, { left: m.x - 7, top: 329, width: 14, height: 14 }, { fill: index === 0 ? COLORS.accent : COLORS.ink, geometry: 'ellipse' });
    addText(slide, m.label, { left: m.x, top: 282, width: 160, height: 32 }, { fontSize: 20, bold: true, color: index === 0 ? COLORS.accent : COLORS.ink });
    addText(slide, m.title, { left: m.x, top: 382, width: 300, height: 44 }, { fontSize: 29, bold: true });
    addText(slide, m.body, { left: m.x, top: 444, width: 310, height: 112 }, { fontSize: 22, color: COLORS.muted });
  });
  addBox(slide, { left: 42, top: 590, width: 1196, height: 54 }, { fill: '#0B1220' });
  addText(slide, '原则：不把设计条目当作已实现能力；高风险功能必须逐项验收后开放。', { left: 66, top: 600, width: 1148, height: 34 }, { fontSize: 23, bold: true, color: '#FFFFFF' });
  addNotes(slide,
    '路线图的价值在于展示治理成熟度：宁可明确阻断，也不让模型越过业务安全边界。第一版只开放 L0、L1 和部分 L2。',
    [path.join(ROOT, 'docs', 'mcp', 'mcp-tool-registry.md'), path.join(ROOT, 'docs', 'agent', 'ai-assistant-product-goal.md')]);
}

// Slide 14 — closing hierarchy adapted from Codex Grid slide-26.
{
  const slide = newSlide();
  addKicker(slide, 'NEXT STEP', 42, 42);
  addText(slide, '把一个项目的方法论，\n复制到更多企业场景', { left: 42, top: 154, width: 1080, height: 190 }, {
    fontSize: 66,
    bold: true,
    verticalAlignment: 'bottom',
    autoFit: 'none',
  });
  addText(slide, '业务建模 × 工程实现 × 可运维交付 × 受控智能化', { left: 42, top: 392, width: 1100, height: 54 }, {
    fontSize: 32,
    bold: true,
    color: COLORS.accent,
  });
  addText(slide, '可迁移到制造、仓储、质量、供应链、设备与运营分析等场景。', { left: 42, top: 470, width: 1000, height: 48 }, {
    fontSize: 24,
    color: COLORS.muted,
  });
  addLine(slide, 42, 552, 180, 0, COLORS.accent, 5);
  addText(slide, '建议下一步：选择一个高价值流程，完成现状诊断 → 方案蓝图 → 试点验证 → 分阶段上线', { left: 42, top: 584, width: 1160, height: 52 }, {
    fontSize: 25,
    bold: true,
  });
  addNotes(slide,
    '收束到客户行动：不是一次性购买功能，而是选择高价值流程，用同一套方法完成诊断、设计、验证和分阶段落地。',
    [path.join(ROOT, 'README.md'), path.join(ROOT, 'docs', 'agent', 'ai-assistant-product-goal.md')]);
}

await fs.mkdir(path.dirname(FINAL_PPTX), { recursive: true });
const pptx = await PresentationFile.exportPptx(presentation);
await pptx.save(FINAL_PPTX);

const inspect = await presentation.inspect({
  kind: 'slide,textbox,shape,image',
  maxChars: 12000,
});
await fs.writeFile(path.join(BUILD_DIR, 'presentation-inspect.ndjson'), inspect.ndjson, 'utf8');
console.log(FINAL_PPTX);
