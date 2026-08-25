import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outputDir = "D:/Laibin/LaibinSugarInventory/outputs/quotation-20260813";
const outputFile = `${outputDir}/来宾智能仓储系统_功能价值与报价评估_20260813.xlsx`;
const asOf = "2026-08-13";
const CNY = '"¥"#,##0;[Red]("¥"#,##0);-';
const PCT = '0.0%';
const INT = '#,##0';
const COLORS = {
  navy: "#17324D", blue: "#2878B5", cyan: "#DDEBF7", pale: "#F3F7FA",
  green: "#2F855A", greenFill: "#E6F4EA", orange: "#C56A1A", orangeFill: "#FFF2CC",
  red: "#B42318", redFill: "#FDE9E7", gray: "#667085", line: "#D0D5DD", white: "#FFFFFF",
  input: "#FFF2CC", formula: "#E8F1FB", black: "#1F2937", purple: "#6941C6", purpleFill: "#F1EAFE"
};

const original = [
  ["O01","原系统","身份认证与RBAC","成熟","高","Web/微信登录，JWT无状态认证，角色/权限/员工花名册，细粒度业务权限与操作审计。","把不同岗位限定在可授权的业务范围，降低越权和账号管理风险。",24,1.00,"安全底座；所有库存、生产、质量功能均依赖。"],
  ["O02","原系统","产品/筛网/库位主数据","成熟","中高","产品、筛网、库位档案CRUD、状态维护、下拉与库位容量信息。","统一仓储对象口径，避免产品、规格、库位名称散落在纸表。",32,1.10,"主数据质量决定后续库存、扫码与报表可信度。"],
  ["O03","原系统","库存查询与仓库可视化","成熟","高","产品库存汇总、库位容量与明细、最近操作、平面图/库位状态查询。","让管理人员即时掌握数量、位置与容量，减少人工盘问和找货时间。",28,0.95,"直接支撑日常查库存、查库位和调度。"],
  ["O04","原系统","成品/半成品入库","成熟","高","传统入库、半成品普通/栈式入库、托盘任务确认、平面图单板入库。","把实物流入转换为可追溯库存与业务单据，是库存准确性的入口。",50,1.15,"交易核心；涉及库存写入、事务与状态校验。"],
  ["O05","原系统","出库、移库与调拨","成熟","高","传统/栈式出库、托盘出库、旧调拨及托盘级/平面图调拨任务。","规范发货和库内移动，形成数量扣减、位置变化和责任记录。",48,1.15,"交易核心；错误会直接影响账实一致。"],
  ["O06","原系统","托盘二维码生命周期","成熟","高","托盘码生成/解析、固定码池、产品绑定、作废/恢复、流转轮次与状态。","建立一托一码的追溯主线，实现扫码作业和单托定位。",42,1.15,"连接库存、任务、生产、打印和移动端。"],
  ["O07","原系统","微信小程序移动作业","中高","高","扫码、工作台、任务中心、查询中心、托盘详情与流转记录等移动页面。","把仓库作业入口带到现场，减少回电脑录入和二次抄写。",48,1.20,"现场采用率与扫码体验直接决定系统落地。"],
  ["O08","原系统","生产订单/煮糖批次/领料产出","中高","高","订单、煮糖批次来源、领料、产出、标签预分配、结束确认和入库进度。","贯通原料投入、在制过程、产出与成品入库，形成生产追溯。",60,1.20,"业务差异化最强，规则复杂、跨表事务多。"],
  ["O09","原系统","化验与质量标准","中高","高","化验导入/查询/维护、质量标准与产品关系、指标判定、异常与覆盖查询。","让质量结论有规则、有快照、可复核，支撑放行和异常分析。",50,1.15,"质量追溯核心；标准版本和判定语义重要。"],
  ["O10","原系统","智能报数与备料池","中","中高","自然语言报数解析、批次缓存/历史、半成品确认、生产报数与备料余额留档。","降低口语报数到结构化任务的录入成本，形成智能化入口。",36,1.20,"已有可用链路，但确定性解析和治理仍需持续强化。"],
  ["O11","原系统","Web管理端与运营页面","中高","高","Vue3/Element Plus 管理端，覆盖主数据、库存、任务、生产、质量、权限与助手入口。","提供管理、审核、查询、图表和配置的统一操作台。",48,1.05,"承载主要管理流程与后台协作。"],
  ["O12","原系统","标签打印助手与PDF标签","中高","中高","本机打印助手、打印机发现/配置、标签渲染/PDF、固定码打印启用。","打通系统二维码到现场标签介质，减少手工制码和错贴。",36,1.10,"硬件环境差异大，实施与售后成本高于普通页面。"],
  ["O13","原系统","审计、日志与OpenAPI治理","中高","中高","AOP操作日志、全局异常、DTO/VO、OpenAPI、数据库Schema启动校验。","提高可追责性、接口一致性和后续集成效率。",25,1.10,"属于不显眼但决定企业可维护性的工程资产。"],
  ["O14","原系统","部署、迁移、测试与交付工程","中高","高","Docker/Nginx、systemd/Windows任务、迁移脚本、环境模板、自动化测试与构建。","让代码能被重复部署、验收、回滚与接管，而不是只在开发机运行。",42,1.05,"当前工程基线强，但生产告警、演练与正式发布仍有缺口。"]
];

const agent = [
  ["A01","Agent","Agent会话与Web助手体验","中高","中高","流式SSE、会话/上下文、候选卡片、审核、取消/中断、历史报表入口。","把复杂业务查询变成自然语言交互，同时保留可检查的过程与结果卡片。",42,1.15,"是用户感知最强的智能层，但依赖后端事实与模型稳定性。"],
  ["A02","Agent","多专家路由与目标契约","中高","高","主路由、10个业务专家、58个GoalContract、受控配方、上下文状态与歧义追问。","把自由问答收敛到明确业务目标，减少模型越权、误路由和幻觉。",68,1.20,"Agent核心编排资产，非普通聊天框可替代。"],
  ["A03","Agent","54个MCP/业务工具与Java Gateway","中高","高","产品/库位解析、库存、托盘、生产、质量、物流、主数据、审计、报表和L2预览；Java/MCP/Python登记一致。","将现有WMS能力封装为结构化、可授权、可审计的业务工具。",54,1.25,"实现复用与安全边界，价值来自业务语义而非接口数量。"],
  ["A04","Agent","安全授权、白名单与审计门禁","中高","高","专家到工具最终授权、RBAC、Registry Hash握手、fail-closed、工具/API审计与安全格式化。","确保模型不能绕过系统权限、任意调用工具或暴露内部ID。",48,1.15,"工业业务智能助手能否上线的关键非功能价值。"],
  ["A05","Agent","库存/仓库/托盘智能查询","成熟","高","自然语言产品/库位解析、库存总览、库位状态、托盘状态/流转/任务与容量查询。","减少筛选页面和跨模块查找，让一线和管理人员快速得到可解释答案。",60,1.20,"Agent v1最稳定、最容易产生使用价值的能力组。"],
  ["A06","Agent","生产与物流追溯专家","中高","高","订单进度、批次来源、实际领料、标签完成度、在制物料、单据与任务追溯。","把跨订单、批次、托盘、库存的链路汇总为业务可读答案。",42,1.20,"跨实体聚合复杂，显著降低人工对账和追溯成本。"],
  ["A07","Agent","质量分析与异常查询","中高","高","化验记录、判定、异常指标、标准覆盖、缺化验/无标准、多候选等查询。","让质检和管理人员快速识别风险范围，并保留标准口径。",52,1.15,"数据语义严谨度要求高，不能靠模型自由计算。"],
  ["A08","Agent","登记式报表、快照与XLSX导出","中高","高","7类登记报表、跨期比较、不可变ReportRun、本人历史重开、XLSX、保留与清理审计。","将一次性对话结果沉淀为可复核、可归档、可分享的经营分析资产。",44,1.20,"连接问答与管理决策，具有持续复用价值。"],
  ["A09","Agent","库存历史、快照与守恒对账","工程完成/数据积累中","高","统一库存事件、上线基线、日终快照、每日守恒对账、发布门禁和独立调度。","为趋势、异常和经营分析建立可信时间序列，避免伪造历史。",42,1.20,"生产价值尚受0/7真实连续窗口阻断。"],
  ["A10","Agent","RAG知识库离线构建与检索","工程完成/资产缺失","中高","DOCX/PDF/OCR、清洗分块、嵌入索引、发布/回滚、知识专家、引用与评测。","回答流程制度、术语和操作知识，并用来源证据约束答案。",55,1.25,"正式语料artifact/model尚未外部交付，生产知识能力NO-GO。"],
  ["A11","Agent","L2任务预览与受控引导","中高","高","任务状态转换预览、成品入库精确预览、阻断项/警告、预览归档。","在不修改库存的前提下先验证输入与可执行性，降低误操作。",36,1.10,"是从只读助手走向可操作助手的安全过渡层。"],
  ["A12","Agent","首个L3成品入库控制面候选","隔离验收/默认关闭","高","确认/撤销、一次性token、幂等哈希、状态重检、事务、审计、故障注入；MCP execute未注册。","证明高风险写操作可被收敛到用户确认和领域事务内。",45,1.20,"不可按已生产开放计价；需独立业务/安全/运维评审。"],
  ["A13","Agent","评测、性能与可观测性工程","中高","高","固定语料、功能/权限/稳定性测试、耗时分段、运行诊断、部署排障文档。","持续证明回答正确、安全、可回归，并定位模型和工具瓶颈。",40,1.15,"功能80/80成功，但当前整体P95 46.562秒，性能门禁失败。"]
];

const gaps = [
  ["G01","原系统","生产部署与回滚演练","P0","完成正式环境部署、同版本包、密钥/Redis、备份恢复、回滚和故障注入演练。",18,1.10],
  ["G02","原系统","监控与告警接入","P1","接入目标告警平台，覆盖Java/Python/MCP、报表、库存历史任务和核心业务异常。",12,1.00],
  ["G03","原系统","正式角色与真人UAT","P1","确定仓管/质检/生产主管角色，组织真实业务问题验收和权限签字。",15,1.15],
  ["G04","原系统","前端性能与依赖治理","P2","拆分约1.27MB主chunk，处理Sass legacy警告并补充性能基线。",12,1.05],
  ["G05","Agent","正式RAG资产接收与发布","P0","接收可信原始材料、artifact和匹配模型，校验后完成切换、回滚和浏览器验收。",15,1.20],
  ["G06","Agent","Agent性能专项优化","P0","针对主路由/专家决策/结果分析做模型、Prompt、缓存和裁剪A/B，使P95进入目标。",28,1.25],
  ["G07","Agent","库存历史自然窗口积累与放行","P1","安装目标机计划任务，积累连续7天可信快照和守恒对账后通过门禁。",10,1.05],
  ["G08","Agent","报表权限与运行告警生产化","P1","完成最小权限、真人UAT、告警、保留策略与运维值守口径。",12,1.10],
  ["G09","Agent","L3成品入库生产审批与灰度","独立审批","保持默认关闭；经业务/安全/运维批准后小范围灰度、回滚与审计复核。",24,1.30],
  ["G10","Agent","正式验收语料与持续评测","P1","建立生产代表性问题集、准确率/拒答/权限/延迟门槛和版本回归流程。",15,1.10]
];

const sources = [
  ["项目事实","项目全量复审与发布就绪性结论",`${asOf}前最新复审：本地工程基线PASS、shadow浏览器UAT PASS、生产发布NO-GO；含测试数、P0/P1和性能结论。`,`docs/agent/project-holistic-reaudit-and-release-readiness-2026-08-12.md`],
  ["项目事实","系统能力地图","原系统模块、主业务链路、成熟度和第一版MCP边界。","docs/mcp-analysis/system-capability-map.md"],
  ["项目事实","MCP工具登记表","当前工具状态、风险等级、L1/L2/L3边界、报表和预览能力。","docs/mcp/mcp-tool-registry.md"],
  ["项目事实","AI助手产品目标","Agent目标、工具/专家/报表/RAG/安全边界与路线。","docs/agent/ai-assistant-product-goal.md"],
  ["项目事实","Agent v1最终验收","9专家、47个L1工具、测试与隔离E2E证据；正式生产仍NO-GO。","docs/agent/agent-v1-acceptance-report.md"],
  ["行业基准","2025中国软件行业基准数据发布解读","全行业开发生产率中值6.72人时/功能点；北京功能点单价1243.52元；需求/测试/实施占比提升。","https://bscea.org/html/news/2025/1031/3151.html"],
  ["造价标准","T/CCUA 060—2025 信息化项目造价评估","费用应区分定制开发、基础设施/资源租赁、集成实施、运维、安全服务等；可用经验、类比、类推和方程法。","https://www.ttbz.org.cn/standardDetail.html?id=w0c8bi87ymuzodinlyvyztnpisnijsg"],
  ["税务口径","国家税务总局公告2026年第4号","自然人代开发票的增值税征管及小规模纳税人符合条件时1%开票口径；实际税负需按主体所在地/资格核定。","https://fgk.chinatax.gov.cn/zcfgk/c100012/c5247426/content.html"]
];

const wb = Workbook.create();
const summary = wb.worksheets.add("报价总览");
const originalSheet = wb.worksheets.add("原系统报价");
const agentSheet = wb.worksheets.add("Agent报价");
const scenario = wb.worksheets.add("身份与方案对比");
const gapSheet = wb.worksheets.add("生产化缺口");
const assumptions = wb.worksheets.add("计价假设");
const sourceSheet = wb.worksheets.add("依据与证据");
const instructions = wb.worksheets.add("使用说明");
wb.comments.setSelf({displayName:"User"});

function title(sheet, text, subtitle, endCol) {
  sheet.getRange(`A1:${endCol}1`).merge();
  sheet.getRange("A1").values = [[text]];
  sheet.getRange("A1").format = {fill:COLORS.navy,font:{bold:true,color:COLORS.white,size:18},verticalAlignment:"center"};
  sheet.getRange(`A2:${endCol}2`).merge();
  sheet.getRange("A2").values = [[subtitle]];
  sheet.getRange("A2").format = {fill:COLORS.pale,font:{color:COLORS.gray,size:10,italic:true},wrapText:true,verticalAlignment:"center"};
  sheet.getRange("A1").format.rowHeight = 32;
  sheet.getRange("A2").format.rowHeight = 34;
  sheet.showGridLines = false;
}

function header(range) {
  range.format = {fill:COLORS.blue,font:{bold:true,color:COLORS.white},horizontalAlignment:"center",verticalAlignment:"center",wrapText:true,borders:{preset:"inside",style:"thin",color:"#A9C4D8"}};
  range.format.rowHeight = 34;
}

function setWidths(sheet, widths) {
  Object.entries(widths).forEach(([col,width]) => sheet.getRange(`${col}:${col}`).format.columnWidth = width);
}

function currency(range) { range.format.numberFormat = CNY; range.format.horizontalAlignment = "right"; }
function percent(range) { range.format.numberFormat = PCT; range.format.horizontalAlignment = "right"; }
function integer(range) { range.format.numberFormat = INT; range.format.horizontalAlignment = "right"; }
function bordered(range) { range.format.borders = {insideHorizontal:{style:"thin",color:COLORS.line},bottom:{style:"thin",color:COLORS.line}}; }

function buildDetail(sheet, rows, kind, prefix) {
  const end = 4 + rows.length;
  title(sheet, `${kind}功能价值与报价`, `按功能估算等价重置开发工作量；蓝色为公式，黄色为可调整计价假设。金额为建议签约价分摊，含税口径见“身份与方案对比”。`, "T");
  sheet.getRange("A4:T4").values = [["编号","系统部分","功能模块","当前成熟度","业务价值","当前如何实现","功能意义","基础人天","复杂度系数","加权人天","公司日费率","公司基准价","公司签约价分摊","公司价值占比","个人日费率","个人基准价","个人签约价分摊","个人价值占比","报价边界/风险","交付备注"]];
  header(sheet.getRange("A4:T4"));
  sheet.getRange(`A5:J${end}`).values = rows.map(r => [r[0],r[1],r[2],r[3],r[4],r[5],r[6],r[7],r[8],null]);
  sheet.getRange(`S5:T${end}`).values = rows.map(r => [r[9], kind === "原系统" ? "含设计、开发、联调、测试和基础文档；不含硬件/云资源/驻场差旅。个人签约价含3%开票/税费预留。" : "含业务工具、编排、安全、评测和集成；不含模型Token、知识语料采购和生产开放审批。个人签约价含3%开票/税费预留。"]);
  sheet.getRange(`J5`).formulas = [["=H5*I5"]]; sheet.getRange(`J5:J${end}`).fillDown();
  const isOrig = kind === "原系统";
  sheet.getRange(`K5:K${end}`).formulas = rows.map(()=>[[isOrig ? "='计价假设'!$B$5" : "='计价假设'!$B$7"]]).flat();
  sheet.getRange(`L5`).formulas = [["=ROUND(J5*K5,0)"]]; sheet.getRange(`L5:L${end}`).fillDown();
  sheet.getRange(`M5`).formulas = [["=ROUND(L5*'计价假设'!$B$9,0)"]]; sheet.getRange(`M5:M${end}`).fillDown();
  sheet.getRange(`N5`).formulas = [[`=M5/SUM($M$5:$M$${end})`]]; sheet.getRange(`N5:N${end}`).fillDown();
  sheet.getRange(`O5:O${end}`).formulas = rows.map(()=>[[isOrig ? "='计价假设'!$B$6" : "='计价假设'!$B$8"]]).flat();
  sheet.getRange(`P5`).formulas = [["=ROUND(J5*O5,0)"]]; sheet.getRange(`P5:P${end}`).fillDown();
  sheet.getRange(`Q5`).formulas = [["=ROUND(P5*'计价假设'!$B$10*(1+'计价假设'!$B$12),0)"]]; sheet.getRange(`Q5:Q${end}`).fillDown();
  sheet.getRange(`R5`).formulas = [[`=Q5/SUM($Q$5:$Q$${end})`]]; sheet.getRange(`R5:R${end}`).fillDown();
  sheet.getRange(`A5:T${end}`).format = {font:{size:9,color:COLORS.black},verticalAlignment:"top",wrapText:true};
  sheet.getRange(`A5:E${end}`).format.horizontalAlignment = "center";
  sheet.getRange(`H5:J${end}`).format.horizontalAlignment = "right";
  sheet.getRange(`K5:K${end}`).format.fill = COLORS.input; sheet.getRange(`O5:O${end}`).format.fill = COLORS.input;
  sheet.getRange(`J5:J${end}`).format.fill = COLORS.formula; sheet.getRange(`L5:N${end}`).format.fill = COLORS.formula; sheet.getRange(`P5:R${end}`).format.fill = COLORS.formula;
  integer(sheet.getRange(`H5:H${end}`)); sheet.getRange(`I5:J${end}`).format.numberFormat = "0.00";
  currency(sheet.getRange(`K5:M${end}`)); percent(sheet.getRange(`N5:N${end}`)); currency(sheet.getRange(`O5:Q${end}`)); percent(sheet.getRange(`R5:R${end}`));
  bordered(sheet.getRange(`A5:T${end}`));
  sheet.getRange(`A${end+1}:T${end+1}`).format = {fill:COLORS.navy,font:{bold:true,color:COLORS.white},borders:{preset:"doubleBottom",style:"medium",color:COLORS.navy}};
  sheet.getRange(`A${end+1}:G${end+1}`).merge(); sheet.getRange(`A${end+1}`).values = [[`${kind}合计`]];
  sheet.getRange(`H${end+1}`).formulas = [[`=SUM(H5:H${end})`]];
  sheet.getRange(`J${end+1}`).formulas = [[`=SUM(J5:J${end})`]];
  sheet.getRange(`L${end+1}`).formulas = [[`=SUM(L5:L${end})`]];
  sheet.getRange(`M${end+1}`).formulas = [[`=SUM(M5:M${end})`]];
  sheet.getRange(`N${end+1}`).formulas = [[`=SUM(N5:N${end})`]];
  sheet.getRange(`P${end+1}`).formulas = [[`=SUM(P5:P${end})`]];
  sheet.getRange(`Q${end+1}`).formulas = [[`=SUM(Q5:Q${end})`]];
  sheet.getRange(`R${end+1}`).formulas = [[`=SUM(R5:R${end})`]];
  integer(sheet.getRange(`H${end+1}:H${end+1}`)); sheet.getRange(`J${end+1}`).format.numberFormat="0.00";
  currency(sheet.getRange(`L${end+1}:M${end+1}`)); percent(sheet.getRange(`N${end+1}`)); currency(sheet.getRange(`P${end+1}:Q${end+1}`)); percent(sheet.getRange(`R${end+1}`));
  sheet.getRange(`A5:T${end+1}`).format.autofitRows();
  sheet.getRange(`A5:T${end}`).format.rowHeight = 60;
  setWidths(sheet,{A:10,B:10,C:25,D:17,E:13,F:50,G:43,H:10,I:10,J:11,K:13,L:15,M:16,N:12,O:13,P:15,Q:16,R:12,S:42,T:38});
  sheet.freezePanes.freezeRows(4); sheet.freezePanes.freezeColumns(3);
  const table = sheet.tables.add(`A4:T${end}`, true, `${prefix}FeatureTable`); table.style = "TableStyleMedium2"; table.showBandedRows = true;
  sheet.getRange(`D5:D${end}`).conditionalFormats.add("containsText",{text:"成熟",format:{fill:COLORS.greenFill,font:{color:COLORS.green,bold:true}}});
  sheet.getRange(`D5:D${end}`).conditionalFormats.add("containsText",{text:"缺失",format:{fill:COLORS.redFill,font:{color:COLORS.red,bold:true}}});
  return {end,totalRow:end+1};
}

// Assumptions must exist before formulas are evaluated.
title(assumptions,"计价假设与可调参数",`评估基准日：${asOf}。黄色单元格为报价人可调整输入；调整后总览、功能分摊和方案对比会自动更新。`,"H");
assumptions.getRange("A4:D4").values = [["参数","取值","单位/格式","说明"]]; header(assumptions.getRange("A4:D4"));
const assumptionRows = [
 ["公司-原系统日费率",2300,"元/加权人天","包含组织管理、测试/交付、发票与质保责任的综合费率。"],
 ["个人-原系统日费率",1350,"元/加权人天","个人高级全栈/业务开发综合费率，连续性和组织保障较弱。"],
 ["公司-Agent日费率",3000,"元/加权人天","复合AI/MCP/后端/数据语义/评测与安全工程费率。"],
 ["个人-Agent日费率",1750,"元/加权人天","个人复合Agent工程费率，实施并发和备份能力有限。"],
 ["公司整体签约折扣",0.92,"百分比","按原系统+Agent整体签约的商业折扣。"],
 ["个人整体签约折扣",0.90,"百分比","个人整体承接的包干折扣。"],
 ["公司增值税示例",0.06,"百分比","仅为一般纳税人现代服务常见示例；实际以报价主体税务资格为准。"],
 ["个人开票附加预留",0.03,"百分比","预留增值税及附加/代开差异，不代表最终个人所得税。"],
 ["公司质保期",12,"月","建议含12个月缺陷质保，不含新增需求。"],
 ["个人质保期",3,"月","建议含3个月缺陷质保，延长需加价。"],
 ["公司首年运维比例",0.12,"百分比/年","建议按软件签约价的12%/年；云资源、模型、驻场另计。"],
 ["个人首年运维比例",0.08,"百分比/年","建议按软件签约价的8%/年；响应窗口和工时需封顶。"],
 ["报价有效期",30,"天","正式报价建议保留30天有效期。"],
 ["预付款比例",0.30,"百分比","建议30%预付款、40%里程碑、20%UAT、10%质保尾款。"]
];
assumptions.getRange(`A5:D${4+assumptionRows.length}`).values=assumptionRows;
assumptions.getRange(`B5:B${4+assumptionRows.length}`).format.fill=COLORS.input;
currency(assumptions.getRange("B5:B8")); percent(assumptions.getRange("B9:B12")); integer(assumptions.getRange("B13:B14")); percent(assumptions.getRange("B15:B16")); integer(assumptions.getRange("B17")); percent(assumptions.getRange("B18"));
assumptions.getRange("A20:H20").values=[["计价方法","口径","为何这样算","不等于什么","风险处理","建议用途","可比性","备注"]]; header(assumptions.getRange("A20:H20"));
assumptions.getRange("A21:H24").values=[
 ["等价重置开发法","基础人天×复杂度系数×主体日费率","代码已有大量工程证据，按可复现开发工作量比单纯按代码行更合理。","不是历史实际投入审计，也不是知识产权交易估值。","复杂业务、安全、硬件/现场、Agent复合工程提高系数。","新建预算、源码价值说明、对外商务锚点。","可与行业人月/功能点基准互相校验。","含需求、设计、开发、测试、联调与基础文档。"],
 ["当前状态折算","成熟度和生产门禁在功能表与缺口表中单列","避免把已写代码误当成已全面商用。","不直接按完成百分比粗暴打折。","未生产放行能力不得按可用SLA承诺。","接手、尾款、收尾预算和风险谈判。","需结合甲方已有材料、数据和环境。","本表重置价与生产化预算应分别看。"],
 ["公司报价","含组织连续性、项目管理、正式合同/发票、质保与SLA成本","客户购买的不只是编码工时，还包括交付责任。","税率不是利润率。","人员冗余、售后、合规和回款风险形成溢价。","B2B、招采、长期运维、生产上线。","通常高于个人报价。","税率需以实际主体资格确认。"],
 ["个人报价","精简管理成本，主要由核心开发者直接交付","适合预算敏感、沟通链路短、需求边界清晰的项目。","不代表可以无限期免费维护。","单点人力、并发、发票、驻场和长期SLA能力较弱。","原型、阶段开发、技术顾问、小范围迭代。","合同应明确工时上限和响应窗口。","大额项目需咨询会计/税务。"]
];
assumptions.getRange("A21:H24").format={wrapText:true,verticalAlignment:"top",font:{size:9}}; bordered(assumptions.getRange("A21:H24"));
setWidths(assumptions,{A:25,B:16,C:18,D:54,E:40,F:43,G:40,H:38}); assumptions.getRange("A5:H24").format.autofitRows(); assumptions.freezePanes.freezeRows(4);
wb.comments.addThread({cell:assumptions.getRange("B9")},"此折扣用于整体签约示例。拆分采购、需求不明确或甲方流程复杂时，应降低折扣或取消折扣。");
wb.comments.addThread({cell:assumptions.getRange("B11")},"6%仅作公司一般纳税人现代服务含税报价演示。最终以实际开票项目、纳税人资格和合同约定为准。");
wb.comments.addThread({cell:assumptions.getRange("B12")},"3%是个人对外报价时的综合预留，并不等同于确定税率；自然人代开、个税及附加可能因地区、次数和所得性质不同而变化。");

// Summary formulas refer to detail sheets; create detail content first.
const od = buildDetail(originalSheet, original, "原系统", "Original");
const ad = buildDetail(agentSheet, agent, "Agent", "Agent");

title(summary,"来宾智能仓储系统｜功能价值与报价评估",`评估基准日：${asOf}｜口径：基于当前代码与验收证据的“等价重置开发价值”+生产化缺口预算；人民币，含税/开票口径按主体区分。`,"L");
summary.getRange("A4:D4").values=[["核心结论","公司主体（含税）","个人开发者（含3%预留）","说明"]]; header(summary.getRange("A4:D4"));
summary.getRange("A5:A8").values=[["原系统建议签约价"],["Agent建议签约价"],["软件整体建议签约价"],["生产化缺口另行预算"]];
summary.getRange("B5").formulas=[[`='原系统报价'!M${od.totalRow}`]]; summary.getRange("B6").formulas=[[`='Agent报价'!M${ad.totalRow}`]]; summary.getRange("B7").formulas=[["=SUM(B5:B6)"]]; summary.getRange("B8").formulas=[["='生产化缺口'!K16"]];
summary.getRange("C5").formulas=[[`='原系统报价'!Q${od.totalRow}`]]; summary.getRange("C6").formulas=[[`='Agent报价'!Q${ad.totalRow}`]]; summary.getRange("C7").formulas=[["=SUM(C5:C6)"]]; summary.getRange("C8").formulas=[["='生产化缺口'!L16"]];
summary.getRange("D5:D8").values=[
 ["原WMS/生产/质量/小程序/打印/部署与测试的整体重置交付价。"],
 ["Agent Runtime、Gateway/MCP、专家工具、报表、RAG、预览、安全与评测的整体重置交付价。"],
 ["建议对外锚点；整体采购折扣已通过计价假设自动计算。"],
 ["不包含在软件重置价内，只有确需生产化时才投入；详见缺口表。"]
];
summary.getRange("A5:D8").format={wrapText:true,verticalAlignment:"center",font:{size:10}}; summary.getRange("A5:A8").format.font={bold:true,color:COLORS.navy};
summary.getRange("B5:C8").format.fill=COLORS.formula; currency(summary.getRange("B5:C8")); bordered(summary.getRange("A5:D8")); summary.getRange("A7:D7").format={fill:COLORS.greenFill,font:{bold:true,color:COLORS.green},borders:{preset:"outside",style:"medium",color:COLORS.green},wrapText:true};

summary.getRange("A10:F10").values=[["系统部分","公司签约价（含税）","个人签约价（含预留）","公司基准价","个人裸价基准","定位"]]; header(summary.getRange("A10:F10"));
summary.getRange("A11:A13").values=[["原系统"],["Agent"],["总计"]];
summary.getRange("B11").formulas=[[`='原系统报价'!M${od.totalRow}`]]; summary.getRange("B12").formulas=[[`='Agent报价'!M${ad.totalRow}`]]; summary.getRange("B13").formulas=[["=SUM(B11:B12)"]];
summary.getRange("C11").formulas=[[`='原系统报价'!Q${od.totalRow}`]]; summary.getRange("C12").formulas=[[`='Agent报价'!Q${ad.totalRow}`]]; summary.getRange("C13").formulas=[["=SUM(C11:C12)"]];
summary.getRange("D11").formulas=[[`='原系统报价'!L${od.totalRow}`]]; summary.getRange("D12").formulas=[[`='Agent报价'!L${ad.totalRow}`]]; summary.getRange("D13").formulas=[["=SUM(D11:D12)"]];
summary.getRange("E11").formulas=[[`='原系统报价'!P${od.totalRow}`]]; summary.getRange("E12").formulas=[[`='Agent报价'!P${ad.totalRow}`]]; summary.getRange("E13").formulas=[["=SUM(E11:E12)"]];
summary.getRange("F11:F13").values=[["业务交易与现场数字化底座"],["自然语言入口、智能聚合与安全自动化层"],["完整系统等价重置开发价值"]];
currency(summary.getRange("B11:E13")); summary.getRange("A11:F13").format={wrapText:true,verticalAlignment:"center"}; bordered(summary.getRange("A11:F13")); summary.getRange("A13:F13").format={fill:COLORS.navy,font:{bold:true,color:COLORS.white},borders:{preset:"doubleBottom",style:"medium",color:COLORS.navy}};

summary.getRange("A16:F16").values=[["项目状态判断","判定","证据摘要","报价影响","客户应理解","建议"]]; header(summary.getRange("A16:F16"));
summary.getRange("A17:F21").values=[
 ["原系统工程成熟度","中高","43个Controller候选文件、复杂业务服务与多端代码；最近复审Java 438项测试通过。","可按完整定制系统重置价报价。","成熟不等于已完成甲方生产发布。","源码交付与生产上线合同分开写。"],
 ["Agent v1只读能力","中高","已验收47个L1工具；最新登记扩展到54个无业务写入工具、10专家、58个GoalContract。","可作为独立Agent产品包报价。","价值在业务聚合、安全与语义，不是普通大模型聊天壳。","按业务域分期验收。"],
 ["工程质量","较强","复审记录：Java 438、Python 485、MCP 72、Web 91；shadow浏览器UAT通过。","降低接手不确定性，支撑工程溢价。","历史文档是证据，正式交易仍应冻结提交并复跑。","签约前做一次版本基线验收。"],
 ["生产就绪性","NO-GO","RAG正式资产缺失、Agent性能P95超目标、库存历史0/7、正式角色/告警/演练未完成。","不能按全面生产SLA成品溢价。","生产化缺口不应被藏在软件总价中。","采用“软件价+生产化预算+外部运行费”。"],
 ["L3写操作","默认关闭","首个成品入库候选已做隔离门禁；MCP execute未注册，需独立审批。","不得宣传为Agent已可自主写库存。","开放写操作需要业务、安全、运维共同负责。","单独里程碑、灰度和回滚条款。"]
];
summary.getRange("A17:F21").format={wrapText:true,verticalAlignment:"top",font:{size:9}}; bordered(summary.getRange("A17:F21")); summary.getRange("B17:B21").format.font={bold:true};
summary.getRange("B20:B21").format={fill:COLORS.redFill,font:{bold:true,color:COLORS.red}};
summary.getRange("A24:F24").values=[["推荐商务口径","适用情形","公司建议","个人建议","合同关键点","备注"]]; header(summary.getRange("A24:F24"));
summary.getRange("A25:F28").values=[
 ["整套源码重置开发价","解释项目已形成的完整软件资产价值","原系统+Agent整体签约价","个人整体承接价","冻结范围、源代码/文档/测试交付、第三方组件清单","本表主报价口径"],
 ["只购买原系统","不含Agent/RAG/智能报表","原系统签约价","个人原系统签约价","明确Agent相关页面/API是否剥离","避免客户以为含智能助手"],
 ["只购买Agent增量","客户已有同版本WMS底座","Agent签约价","个人Agent签约价","依赖现有API、数据质量、模型和环境","WMS变化会产生集成变更"],
 ["生产上线与长期运维","需要正式SLA、告警、演练、驻场或7×24","优先公司主体","个人需限制SLA和工时","云/模型/短信/打印硬件/差旅另计","生产责任不建议隐含在开发价内"]
];
summary.getRange("A25:F28").format={wrapText:true,verticalAlignment:"top",font:{size:9}}; bordered(summary.getRange("A25:F28"));
summary.getRange("A30:F30").values=[["商务价格带","公司主体","个人开发者","用途","适用前提","建议话术"]]; header(summary.getRange("A30:F30"));
summary.getRange("A31:A34").values=[["建议首报（可谈）"],["目标签约价"],["审慎底价"],["若全部生产化缺口均采购"]];
summary.getRange("B31").formulas=[["=D13"]]; summary.getRange("C31").formulas=[["=ROUND(E13*(1+'计价假设'!B12),0)"]];
summary.getRange("B32").formulas=[["=B7"]]; summary.getRange("C32").formulas=[["=C7"]];
summary.getRange("B33").formulas=[["=ROUND(B32*92%,0)"]]; summary.getRange("C33").formulas=[["=ROUND(C32*92%,0)"]];
summary.getRange("B34").formulas=[["=B7+B8"]]; summary.getRange("C34").formulas=[["=C7+C8"]];
summary.getRange("D31:F34").values=[
 ["首次正式报价，保留谈判空间","需求边界未扩张，付款条件正常","“按完整范围首报；整体采购可按里程碑和付款条件申请折扣。”"],
 ["本模型认为合理的成交目标","范围冻结、按推荐付款节点执行","“该价格已体现整套采购折扣，生产化与外部运行费单列。”"],
 ["不建议轻易突破的底线","无驻场/7×24、无免费新增、回款风险低","“若继续降价，需要同步缩范围、缩质保或调整付款条件。”"],
 ["规划参考，不是必须一次性采购","十项缺口全部实施且目标环境条件满足","“建议按P0/P1拆阶段，不必把所有缺口一次性打包。”"]
 ];
currency(summary.getRange("B31:C34")); summary.getRange("B31:C34").format.fill=COLORS.formula; summary.getRange("A31:F34").format={wrapText:true,verticalAlignment:"top",font:{size:9}}; bordered(summary.getRange("A31:F34")); summary.getRange("A32:F32").format={fill:COLORS.greenFill,font:{bold:true,color:COLORS.green},borders:{preset:"outside",style:"medium",color:COLORS.green},wrapText:true};
setWidths(summary,{A:27,B:22,C:22,D:52,E:43,F:43,G:3,H:3,I:3,J:3,K:3,L:3}); summary.getRange("A4:F28").format.autofitRows(); summary.freezePanes.freezeRows(2);

// Native comparison chart.
const chart = summary.charts.add("bar", summary.getRange("A10:C12"));
chart.title = "公司与个人建议签约价（按系统部分）"; chart.hasLegend = true; chart.xAxis = {axisType:"textAxis"}; chart.yAxis = {numberFormatCode:"¥#,##0"}; chart.setPosition("H4","L16");

// Scenario comparison.
title(scenario,"公司主体 vs 个人开发者｜报价与责任边界",`同一套软件的功能价值相同，报价差异来自税务/合同、组织连续性、交付管理、质保、SLA和风险承担方式。`,"K");
scenario.getRange("A4:K4").values=[["对比项","公司主体","个人开发者","对客户的影响","公司建议写法","个人建议写法","可量化参数","公司数值","个人数值","选择建议","风险提示"]]; header(scenario.getRange("A4:K4"));
const compareRows=[
 ["软件整体签约价","较高，包含组织和责任溢价","较低，核心人员直接交付","预算与责任保障不同","含税总价+明确发票类型","价税口径单列，代开/个税按实际","建议签约价","='报价总览'!B7","='报价总览'!C7","大额B2B优先公司","不要只比较裸编码单价"],
 ["合同与采购","标准B2B合同、对公付款、供应商准入更顺畅","可签个人技术服务/承揽合同，部分甲方准入受限","影响立项、付款、验收和审计","约定里程碑/验收/SLA/保密/IP","范围和工时上限必须更清楚","合同承载","强","中","正式采购优先公司","甲方模板可能要求法人主体"],
 ["发票与税务","可按主体资格开票；本表6%仅示例","自然人代开或个体户开票，税负因资格/地区而异","影响甲方进项、个人实收和报价有效性","报价写“含税，税率以实际为准”","写“含/不含税及代开承担方”","开票预留","='计价假设'!B11","='计价假设'!B12","先确认客户开票要求","需咨询会计/税务，勿把示例当定论"],
 ["项目管理与多人协作","可配置PM、前后端、测试、运维/备份人员","通常由1名核心开发统筹，协作依赖个人网络","影响并行交付与突发替补","纳入项目管理和例会机制","限定沟通窗口与并行任务数","交付连续性","高","低-中","跨多端/生产上线优先公司","单点离岗会影响周期"],
 ["质保","建议12个月缺陷质保","建议3个月缺陷质保","影响缺陷修复成本和客户安心度","12个月缺陷修复，新增需求另计","3个月缺陷修复，超期按工时包","质保月数","='计价假设'!B13","='计价假设'!B14","长期运行优先公司","需定义缺陷与需求变更"],
 ["SLA与响应","可约定工作日/紧急等级、备份人和升级机制","建议仅承诺工作日窗口，不承诺7×24","影响生产故障恢复","分P0/P1/P2和响应/恢复目标","限定支持时段和月度工时","SLA等级","可定制","有限","生产核心系统优先公司","恢复时间依赖甲方环境与第三方"],
 ["知识产权与源码交接","可进行公司级著作权/资产交付和长期托管","可完整交源码，但个人持续承接能力需约定","影响后续审计、融资、招采和接管","列交付清单、第三方许可和IP归属","列仓库、账号、密钥移交与协作边界","IP交接","规范","可规范","两者都必须书面约定","开源组件不等于全部可再授权"],
 ["首年运维建议","签约价12%/年起，可扩展到驻场/7×24","签约价8%/年起，需封顶工时","影响长期成本可预期性","独立运维合同和SLA","月度工时包/按次支持","首年运维","='报价总览'!B7*'计价假设'!B15","='报价总览'!C7*'计价假设'!B16","稳定生产优先公司","云、模型、短信、硬件与差旅另计"],
 ["适合场景","正式生产、招采、跨部门、长期运维","预算敏感、阶段迭代、原型/顾问、小范围部署","决定是否值得为组织保障付费","公司承担更完整交付责任","个人保持清晰边界和快速沟通","推荐","生产项目","阶段项目","按责任而非只按价格选择","个人不宜低价承诺公司级无限责任"]
];
scenario.getRange(`A5:K${4+compareRows.length}`).values=compareRows.map(r=>r.map((v,i)=> (i===7||i===8)&&typeof v==="string"&&v.startsWith("=")?null:v));
for(let i=0;i<compareRows.length;i++){const row=5+i; for(const col of [7,8]){const v=compareRows[i][col];if(typeof v==="string"&&v.startsWith("="))scenario.getRange(`${String.fromCharCode(65+col)}${row}`).formulas=[[v]];}}
currency(scenario.getRange("H5:I5")); percent(scenario.getRange("H7:I7")); integer(scenario.getRange("H9:I9")); currency(scenario.getRange("H12:I12"));
scenario.getRange(`A5:K${4+compareRows.length}`).format={wrapText:true,verticalAlignment:"top",font:{size:9}}; bordered(scenario.getRange(`A5:K${4+compareRows.length}`)); scenario.getRange("H5:I13").format.fill=COLORS.formula;
setWidths(scenario,{A:22,B:37,C:37,D:36,E:38,F:38,G:18,H:17,I:17,J:30,K:36}); scenario.getRange("A5:K13").format.rowHeight=58; scenario.freezePanes.freezeRows(4); scenario.freezePanes.freezeColumns(1);

// Gaps.
title(gapSheet,"生产化缺口与追加预算",`这些工作不应被误认为当前已全面生产就绪；建议作为独立里程碑或先决条件。价格为规划级预算，需在目标环境和责任范围确认后锁定。`,"M");
gapSheet.getRange("A4:M4").values=[["编号","系统部分","缺口项","优先级","当前缺口/完成标准","基础人天","复杂度","加权人天","公司日费率","个人日费率","公司预算","个人预算","是否计入主报价"]]; header(gapSheet.getRange("A4:M4"));
gapSheet.getRange("A5:G14").values=gaps.map(r=>r.slice(0,7));
gapSheet.getRange("H5").formulas=[["=F5*G5"]]; gapSheet.getRange("H5:H14").fillDown();
gapSheet.getRange("I5:I14").formulas=gaps.map(r=>[r[1]==="原系统"?"='计价假设'!$B$5":"='计价假设'!$B$7"]);
gapSheet.getRange("J5:J14").formulas=gaps.map(r=>[r[1]==="原系统"?"='计价假设'!$B$6":"='计价假设'!$B$8"]);
gapSheet.getRange("K5").formulas=[["=ROUND(H5*I5,0)"]]; gapSheet.getRange("K5:K14").fillDown(); gapSheet.getRange("L5").formulas=[["=ROUND(H5*J5*(1+'计价假设'!$B$12),0)"]]; gapSheet.getRange("L5:L14").fillDown();
gapSheet.getRange("M5:M14").values=gaps.map(()=>["否，单独预算"]);
gapSheet.getRange("A5:M14").format={wrapText:true,verticalAlignment:"top",font:{size:9}}; bordered(gapSheet.getRange("A5:M14")); gapSheet.getRange("H5:L14").format.fill=COLORS.formula; integer(gapSheet.getRange("F5:F14")); gapSheet.getRange("G5:H14").format.numberFormat="0.00"; currency(gapSheet.getRange("I5:L14"));
gapSheet.getRange("A16:G16").merge(); gapSheet.getRange("A16").values=[["生产化缺口合计（规划级）"]]; gapSheet.getRange("H16").formulas=[["=SUM(H5:H14)"]]; gapSheet.getRange("K16").formulas=[["=SUM(K5:K14)"]]; gapSheet.getRange("L16").formulas=[["=SUM(L5:L14)"]]; gapSheet.getRange("A16:M16").format={fill:COLORS.navy,font:{bold:true,color:COLORS.white},borders:{preset:"doubleBottom",style:"medium",color:COLORS.navy}}; currency(gapSheet.getRange("K16:L16")); gapSheet.getRange("H16").format.numberFormat="0.00";
gapSheet.getRange("A18:M19").merge(); gapSheet.getRange("A18").values=[["重要：G09 L3成品入库生产审批与灰度只有在甲方业务、安全、运维责任人批准后才可实施；本报价不授权Agent写库存，也不包含出库、调拨、半成品入库等其他L3/L4能力。"]]; gapSheet.getRange("A18").format={fill:COLORS.redFill,font:{bold:true,color:COLORS.red},wrapText:true,verticalAlignment:"center"};
setWidths(gapSheet,{A:9,B:11,C:29,D:14,E:55,F:11,G:10,H:11,I:14,J:14,K:15,L:15,M:17}); gapSheet.getRange("A5:M14").format.rowHeight=54; gapSheet.freezePanes.freezeRows(4);
gapSheet.getRange("D5:D14").conditionalFormats.add("containsText",{text:"P0",format:{fill:COLORS.redFill,font:{bold:true,color:COLORS.red}}});

// Sources.
title(sourceSheet,"依据、证据与外部参考",`本报价优先以当前代码和最近验收文档为事实；外部行业资料只用于校验计价方法和市场合理性。网址以纯文本保留，便于审计。`,"E");
sourceSheet.getRange("A4:E4").values=[["类型","名称","支持的判断","文件/URL","备注"]]; header(sourceSheet.getRange("A4:E4"));
sourceSheet.getRange(`A5:D${4+sources.length}`).values=sources; sourceSheet.getRange(`E5:E${4+sources.length}`).values=sources.map((s,i)=>[i<5?"项目内证据；交易前应以冻结Git提交复跑。":"外部公开参考；不替代正式造价鉴定或税务意见。"]);
sourceSheet.getRange(`A5:E${4+sources.length}`).format={wrapText:true,verticalAlignment:"top",font:{size:9}}; bordered(sourceSheet.getRange(`A5:E${4+sources.length}`)); setWidths(sourceSheet,{A:15,B:40,C:66,D:84,E:42}); sourceSheet.getRange(`A5:E${4+sources.length}`).format.rowHeight=54; sourceSheet.freezePanes.freezeRows(4);

// Instructions.
title(instructions,"报价表使用说明",`此文件可直接用于内部预算或作为对外报价底稿；正式发客户前请填写公司/个人信息、客户名称、项目范围和报价有效期。`,"F");
instructions.getRange("A4:F4").values=[["步骤","怎么做","看哪个工作表","输出","必须确认","常见误区"]]; header(instructions.getRange("A4:F4"));
instructions.getRange("A5:F11").values=[
 [1,"先在“计价假设”黄色格调整公司/个人费率、折扣、税务预留、质保和运维比例。","计价假设","自动更新所有报价","实际主体纳税资格、客户发票要求","把税率直接当利润或折扣"],
 [2,"选择对外身份。正式生产、招采、长期SLA优先公司；阶段迭代可选个人。","身份与方案对比","确定报价责任边界","合同主体、付款方式、IP和保密","个人低价承诺公司级责任"],
 [3,"确认客户购买范围：原系统、Agent增量，还是整套。","报价总览/两张功能报价","形成主报价","同版本WMS依赖、是否含小程序/打印/部署","把Agent报价当作仅接一个模型API"],
 [4,"逐功能讲价值和当前实现，必要时删除明确不在客户范围的行，并同步调整人天。","原系统报价/Agent报价","形成客户版功能清单","删减是否破坏公共底座或跨模块依赖","按按钮数量机械砍价"],
 [5,"把生产化缺口作为独立阶段，不与重置开发价混在一起。","生产化缺口","形成上线预算和前置条件","目标环境、告警、真实UAT、RAG资产、性能目标","把NO-GO表述成已商用"],
 [6,"把云资源、模型Token、短信/企微、打印机/耗材、证书、差旅、驻场列为第三方或实报实销。","本页/合同附件","避免亏损和责任争议","用量、供应商、付款方、涨价机制","开发总价无限包运行费"],
 [7,"签约前冻结Git提交并复跑测试/构建/浏览器验收，形成交付基线。","依据与证据","验收附件","数据库/测试账号/密钥由甲方受控提供","只引用旧报告不复验"]
];
instructions.getRange("A5:F11").format={wrapText:true,verticalAlignment:"top",font:{size:9}}; bordered(instructions.getRange("A5:F11")); integer(instructions.getRange("A5:A11")); setWidths(instructions,{A:9,B:62,C:27,D:31,E:46,F:42}); instructions.getRange("A5:F11").format.rowHeight=56;
instructions.getRange("A14:F14").merge(); instructions.getRange("A14").values=[["正式报价建议附加条款"]]; instructions.getRange("A14").format={fill:COLORS.navy,font:{bold:true,color:COLORS.white}};
instructions.getRange("A15:F21").merge(true); instructions.getRange("A15:A21").values=[
 ["1. 报价为当前已识别范围的定制软件交付价；新增需求、数据清洗、第三方接口变更按变更单计价。"],
 ["2. 生产环境云资源、数据库、Redis、域名/证书、模型调用、OCR/嵌入模型、短信/企微、硬件及耗材不含在软件价。"],
 ["3. Agent回答受模型、数据质量、权限和工具可用性影响；以结构化工具事实和登记报表为准，不承诺模型永不出错。"],
 ["4. 任何库存写操作必须保持预览、用户确认、幂等、状态重检、事务、审计与灰度；未批准前默认关闭。"],
 ["5. 缺陷修复与新增需求定义应写入合同；超出质保期或SLA范围按运维包/工时另计。"],
 ["6. 付款建议：30%预付款、40%里程碑、20%UAT、10%质保尾款；也可按客户资信调整。"],
 ["7. 本表是商务估算，不替代第三方软件造价鉴定、审计报告、法律意见或税务意见。"]
];
instructions.getRange("A15:F21").format={wrapText:true,verticalAlignment:"center",font:{size:10,color:COLORS.black},fill:COLORS.pale}; instructions.getRange("A15:F21").format.rowHeight=34;

// Final common formatting and a few formula controls.
for (const s of [summary,originalSheet,agentSheet,scenario,gapSheet,assumptions,sourceSheet,instructions]) {
  s.getUsedRange().format.font.name = "Microsoft YaHei";
}

await fs.mkdir(outputDir,{recursive:true});
const checks = {};
for (const [name,range] of [["summary","报价总览!A1:L34"],["original",`原系统报价!A1:T${od.totalRow}`],["agent",`Agent报价!A1:T${ad.totalRow}`],["scenario","身份与方案对比!A1:K13"],["gaps","生产化缺口!A1:M19"],["assumptions","计价假设!A1:H24"],["sources",`依据与证据!A1:E${4+sources.length}`],["instructions","使用说明!A1:F21"]]) {
  const [sheetName,a1] = range.split("!");
  const blob = await wb.render({sheetName,range:a1,scale:1,format:"png"});
  await fs.writeFile(`${outputDir}/preview_${name}.png`,new Uint8Array(await blob.arrayBuffer()));
}
checks.summary = (await wb.inspect({kind:"table",range:"报价总览!A4:F13",include:"values,formulas",tableMaxRows:15,tableMaxCols:8})).ndjson;
checks.original = (await wb.inspect({kind:"table",range:`原系统报价!H4:R${od.totalRow}`,include:"values,formulas",tableMaxRows:20,tableMaxCols:12})).ndjson;
checks.agent = (await wb.inspect({kind:"table",range:`Agent报价!H4:R${ad.totalRow}`,include:"values,formulas",tableMaxRows:20,tableMaxCols:12})).ndjson;
checks.errors = (await wb.inspect({kind:"match",searchTerm:"#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",options:{useRegex:true,maxResults:300},summary:"final formula error scan"})).ndjson;
await fs.writeFile(`${outputDir}/verification.txt`,Object.entries(checks).map(([k,v])=>`[${k}]\n${v}`).join("\n\n"),"utf8");
const out = await SpreadsheetFile.exportXlsx(wb);
await out.save(outputFile);
console.log(JSON.stringify({outputFile,originalRows:original.length,agentRows:agent.length,gapRows:gaps.length,checks},null,2));
