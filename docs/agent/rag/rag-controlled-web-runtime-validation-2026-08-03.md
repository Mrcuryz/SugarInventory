# RAG 当前提交隔离 Java/Web 全链路验证记录

状态：`PASSED / ISOLATED_JAVA_28080_WEB_5174_VERIFIED / CURRENT_DEPLOYMENT_NOT_SWITCHED`
执行日期：2026-08-03 18:24～18:57（`Asia/Shanghai`）
验证提交：`4150e6dd66578bfdd71e97e64625d1c81f02e21f`
正式 corpus：`laibin-rag-2026-07-29-v1`

## 1. 验证目的与结论

本轮从精确 Git 提交导出干净 Java/Web 源码，在不停止、不重配现有 8080、8091、5173 的前提下，
建立以下隔离链路：

```text
Browser 5174 -> Java 28080 -> Python Agent 28091 -> formal RAG v1
```

管理员静态知识、数值证据、无证据、静态/实时混合路由和普通员工双层门禁均通过。当前提交与正式
v1 artifact 已具备隔离 Web 全链路可用证据，但本轮没有切换现有 Web/Java/8091 流量，也没有准备
可长期分发的真实部署 env。因此结论不是“当前部署已上线”，而是“同一提交的受控隔离链路已通过”。

## 2. 环境与安全边界

| 层级 | 地址或构建 | 结果 |
| --- | --- | --- |
| 干净源码 | 从 `4150e6d` 导出的 `pom.xml`、`src/`、`webpage/`，945 个文件 | 通过 |
| Java JAR | `SugarInventory-1.0-SNAPSHOT.jar`，143,028,375 字节 | 构建通过 |
| JAR SHA-256 | `35834569812d8adcd700edf09d483a05cabf766eb9859f1bf4350a20b8169b0b` | 已记录 |
| Java | `127.0.0.1:28080`，验收 PID 43200 | 验收后已停止 |
| Web Vite | `127.0.0.1:5174`，验收 PID 18056 | 验收后已停止 |
| Python Agent | `127.0.0.1:28091`，PID 28764 | 保持运行 |
| 既有服务 | 8080、8091、5173 | 未停止、未重配，验收后仍监听 |

数据库变量只从甲方给定的本地环境文件注入，启动前确认 URL 指向本机；值没有输出或写入 Git。
Java 的 JWT、实体引用密钥和内部工具密钥均在进程内随机生成；Python 服务密钥从受保护的临时文件
读取，没有写入文档。Python 继续使用 test、memory、deterministic、mock tool gateway，不调用外部
模型或真实仓储工具。

本轮只允许本地登录、Agent 会话和审阅/审计记录，不执行库存、托盘、库位、盘点、出入库等仓储
业务写操作。可配置的每日快照 cron 被改到验收窗口之外；固定 03:00 清理任务在 18:24～18:57
窗口内没有触发。Java/Web 在验收后停止，避免跨越后续定时任务窗口。

## 3. 构建与启动结果

- 全仓 `git archive` 首次由 Windows `tar` 解压时在部分中文路径失败；未修改仓库，随后只导出运行
  所需的 `pom.xml`、`src/`、`webpage/` 到新目录，成功得到 945 个文件；
- 干净副本执行 `mvn -q -DskipTests package` 成功；本轮复用此前对同一提交完成的 Java 316、
  Python 454、RAG 119、Web 67 项全量回归结果，不把跳过测试的打包命令记为回归测试；
- Java 首次启动使用了 Spring 不接受的 7 段 cron，应用在上下文创建阶段退出，28080 没有监听；
  改为有效 6 段表达式并显式使用 JDK 21 后启动成功，`/v3/api-docs` 返回 200；
- 首次 API 会话冒烟提交了未登记的自定义 scope，未创建会话；改用与 Web 相同的空请求体后返回
  `ACTIVE`，scope 为 `mcp:warehouse:read`；
- Vite 5174 启动包装命令因 Windows 后台重定向没有正常返回结果，但直接核验确认 PID 18056 正在
  监听，首页 HTTP 200，代理目标为 28080；
- 浏览器网络记录显示登录、Agent session、SSE message 和 message review 均经 5174 返回 HTTP 200。

## 4. 真实浏览器场景

| 场景 | 结果 | 可见证据 |
| --- | --- | --- |
| ADMIN 登录与入口 | `PASS` | 登录角色为 `ADMIN`；页面存在唯一“AI 助手”，打开后显示“会话已连接” |
| 静态知识成功 | `PASS` | “白砂糖生产工艺中，金属检测的限值是多少？”显示“现行资料”和 5 张来源卡片；回答没有把静态资料描述成实时事实 |
| 数值证据 | `PASS` | “白砂糖生产工艺中，金属检测的限值是多少？”的可见 DOM 同时包含 `Φ1.5mm`、`Φ2.0mm`、`Φ2.5mm`，首两张证据定位到白砂糖分装步骤 6 金属控制 |
| 无证据 | `PASS` | “白砂糖的企业认证资料是什么？”显示“现行资料中未找到可核验内容”，并明确“不代表相关事实一定不存在”，无来源卡片 |
| 静态/实时混合 | `PASS` | “白砂糖生产工艺是什么，当前库存还有多少？”要求拆成工艺和当前库存两个问题，没有执行组合查询 |
| STAFF 页面门禁 | `PASS` | `UAT现场员工` 登录后页面无“AI 助手”按钮，也未挂载助手对话框 |
| STAFF 后端门禁 | `PASS` | 直接创建 Agent session 的 HTTP 状态为 200、统一业务码为 403、`data=null`；没有获得会话 |
| SUPER_ADMIN | `PASS_WITH_ENV_LIMITATION` | 本地只读花名册查询 `SUPER_ADMIN` 账号数为 0，未伪造 Web 账号；同日 28091 受控接口已用可信上下文验证该角色的 `NO_DATA` 路径 |

项目业务异常使用统一 HTTP 响应封装，所以 STAFF 的门禁以业务码 403、空 data 和未获得会话共同
判断，不把外层 HTTP 200 当作授权成功。

## 5. 受控输入纠偏

本轮先输入“来宾白砂糖生产工艺中的筛网孔径有哪些？”。确定性参数构建器把“来宾白砂糖”作为
显式产品标签，而正式语料只声明“白砂糖”，知识服务按精确产品范围返回 `INVALID_QUERY`。这符合
“不得猜测产品”的设计，不将“来宾”擅自删去或映射为白砂糖。

去掉未登记产品前缀后检索成功，但“筛网孔径”并不是三项金属检测阈值的准确语义。因此数值场景
改用“金属检测的限值是多少”，随后可见 DOM 命中全部三项冻结数值。上述两次纠偏均保留为现场
事实，不把错误输入产生的结果包装成系统缺陷或通过证据。

## 6. 浏览器、日志和凭据检查

- Playwright 页面标题、登录、对话框、状态条、来源卡片和 STAFF 菜单均通过真实 Chromium 检查；
- 浏览器控制台错误 0；唯一 warning 是既有 `vue-i18n` legacy API deprecation；
- Vite stderr 只有既有 Dart Sass legacy API 和 Node `util._extend` deprecation warning；
- 浏览器验收窗口内 Java WARN/ERROR 0；
- 扫描 Java/Web/Python 日志及 Playwright 文本证据共 24 个文件，临时服务密钥、数据库口令和 Web
  测试口令原文命中文件 0，Authorization/Bearer/service-key/password 等通用凭据模式命中文件 0；
- 28080、5174 只监听 `127.0.0.1`；完成后对应进程已按 PID 和命令行身份核验后停止；
- 28091 继续监听，当前 8080、8091、5173 也保持监听。

浏览器证据位于 Git 忽略的：

```text
output/playwright/rag-uat-2026-08-03/
```

其中保存 4 张 PNG 以及 Playwright DOM/console 记录。三张主要截图 SHA-256：

- `admin-rag-no-data.png`：`fcc83712773304651760e0d022ae96d5a0fe7ad0ca5390eceaa3f914bd1e68de`；
- `admin-rag-mixed-query.png`：`4a96326679a68039bf1596c95223df229cbee6ee5627c07f67dcb0438ffc49da`；
- `staff-agent-entry-hidden.png`：`e1683930f38c47a4b25c65062bec25328c33c5fabd4f7386b280a1e152f98bad`。

数值证据以同目录 Playwright DOM 快照中三个字符串各 3 处可见命中为主；对话框截图因滚动位置只
覆盖回答末段，不将其单独作为三项数值的充分证据。

## 7. 当前门禁与下一步

已经关闭：

- 确定提交重建 Java/Web；
- Java 28080 到 Python 28091 的服务密钥认证链路；
- ADMIN 真实浏览器静态知识成功、数值、无证据和混合问题；
- STAFF 页面和 Java API 双层门禁；
- 浏览器、日志和临时进程安全检查。

仍未关闭：

- 将随机密钥和真实部署变量放入可长期受控分发的部署 env；
- 在变更窗口将现有 Java/Web/Python 部署指向已经验证的配置；
- 切换后重新核对当前服务的 health、corpus version、ADMIN/STAFF 冒烟和回滚准备；
- 若甲方需要 SUPER_ADMIN 的 Web 现场验收，先由管理员建立真实测试账号，不在 UAT 中伪造身份。

因此当前权威状态为：

```text
SOURCE_INTEGRATED
ENGINEERING_REGRESSION_PASSED
V1_ARTIFACT_VALIDATED
ISOLATED_RUNTIME_28091_VERIFIED
ISOLATED_WEB_5174_VERIFIED
CURRENT_DEPLOYMENT_NOT_SWITCHED
```
