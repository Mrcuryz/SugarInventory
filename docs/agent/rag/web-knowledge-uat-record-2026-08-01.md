# RAG-04 Web 知识引用真实浏览器 UAT 记录（2026-08-01）

状态：`10_PASS / COMPLETED / PARALLEL_RUNTIME_READY_18080_18091`
执行日期：2026-08-01～2026-08-02
执行范围：本地 Web、Java Agent Gateway、Python Agent Runtime
正式 corpus 状态：`VALIDATED_NOT_ACTIVE`

## 1. 验收环境

| 层级 | 地址 | 现场状态 |
| --- | --- | --- |
| Web Vite | `http://127.0.0.1:5173` | 可用，已加载本轮 HMR 代码 |
| Java | `http://127.0.0.1:8080` | 可用，管理员/非管理员门禁可验收 |
| Python Agent | `http://127.0.0.1:8091` | 可用；`rag=READY`、`toolGateway=UP`，已加载隔离候选 corpus |

浏览器使用本地现有真实数据库账号。ADMIN 使用现有管理员，非管理员使用此前建立的专用
`UAT_STAFF_20260727 / UAT现场员工` 测试账号；本记录不保存登录口令、JWT、服务密钥或数据库凭据。

现场从 `laibin-shadow-model.env` 读取变量名和存在性，只发现模型 API Key、数据库 URL、用户名和
密码；没有输出任何值。Java/Python 内部服务密钥、JWT 和实体引用密钥由系统加密随机源重新生成，
只保存在 `%LOCALAPPDATA%\Temp\laibin-rag04-uat-secrets.env`，ACL 仅允许当前 Windows 用户、
Codex 隔离账户和 SYSTEM 访问。隔离候选位于
`%LOCALAPPDATA%\Temp\laibin-rag04-uat-20260801-a1`，正式目录没有创建 `current.json`，没有切换
corpus，没有连接生产数据库，也没有执行仓储业务写操作。

## 2. 工程验证

| 验证 | 结果 |
| --- | --- |
| Python 全量测试 | `433 passed, 5 skipped` |
| Java 全量测试 | `304 passed`，0 failure、0 error、0 skipped |
| Web 全部现有 Node 测试 | `65 passed` |
| Web 生产构建 | 通过；仅有项目既有 Sass legacy API 和大 chunk 警告 |
| 真实浏览器 | 10 项全部通过；`NO_DATA`、受控降级及恢复普通语义检索均已现场复验 |

## 3. 十项场景结果

| 编号 | 场景 | 结果 | 现场证据 |
| --- | --- | --- | --- |
| UAT-01 | ADMIN 打开 AI 助手 | `PASS` | 页面有唯一“AI 助手”按钮；打开后显示“会话已连接”和安全欢迎语 |
| UAT-02 | 工艺知识命中 | `PASS` | “单晶黄冰糖的完整工艺流程是什么”显示“现行资料”、5 条来源、第 1 页/章节；DOM 检查无路径、JSON、内部 ID、校验异常，alert 数为 0 |
| UAT-03 | 企业资料知识命中 | `PASS` | “公司有哪些认证和销售网络”返回宣传册第 3、15、14、2 页等来源，认证和销售网络定位可读 |
| UAT-04 | 连续知识追问 | `PASS` | 同会话追问“那企业简介里还记录了什么？”继续显示企业宣传册来源和第 2/3 页定位，无内部上下文泄漏 |
| UAT-05 | 知识库内无答案 | `PASS` | 修复后 Python 中输入“白砂糖的企业认证资料是什么”，页面显示唯一“现行资料中未找到可核验内容”状态；来源数 0、alert 数 0，无路径、JSON 或内部 ID |
| UAT-06 | 关键词降级 | `PASS` | 工作区外测试替身受控令 query embedding 失败，输入“白砂糖生产工艺中金属检测 CCP2 的控制参数是什么”后显示唯一“关键词检索结果”状态和 5 条来源；撤销替身、恢复普通 Python 后正常“现行资料”再次命中 5 条来源 |
| UAT-07 | 知识库不可用 | `PASS` | 输入工艺问题后显示 `alert`：“知识库暂不可用；实时库存、库位、托盘和化验查询不受影响” |
| UAT-08 | 收起、再打开、刷新 | `PASS` | 收起再打开保留当前页面问题和状态；刷新后旧消息不伪恢复，可建立新的已连接会话 |
| UAT-09 | STAFF 页面入口 | `PASS` | `UAT现场员工` 登录后“AI 助手”按钮数量为 0，未挂载助手对话框 |
| UAT-10 | STAFF 直接请求 API | `PASS` | 伪造 `roleCode=ADMIN/userId=1` 请求仍返回统一业务码 `403`、`data=null`；活动 Agent 会话为 0 |

本系统业务异常使用统一 HTTP 响应封装，因此 UAT-10 的 HTTP 状态为 200、业务码为 403。验收以业务码、
空 `data` 和没有创建 session 三个信号共同判定，不把 HTTP 200 误判为授权成功。

## 4. 现场缺陷

### RAG04-WEB-01 — 简短 SSE 不可用文案没有状态条

首次现场输入：

```text
压榨车间的工艺流程有哪些步骤？
```

现场旧会话通过 SSE `error.message` 显示“知识库当前不可用。”，首版前端只识别完整回答
“知识库当前不可用，请稍后重试。……”，因此没有专用状态条。

修复：

- 只把上述简短安全文案和完整安全前缀显式加入白名单；
- 不使用包含“不可用”等模糊关键词匹配；
- 新增自动化测试；
- HMR 后在同一真实消息上复验，DOM 出现 `alert`、标题“知识库暂不可用”和实时业务边界。

结论：`FIXED_AND_RETESTED`。

### RAG04-RUNTIME-01 — 自然问法未进入知识目标

首次输入“多晶体白冰糖自然结晶需要多久？”时，旧确定性路由没有把“自然结晶/多久”识别为工艺
知识，返回了通用澄清。修复内容：

- 为公司成立问法、自然结晶及结晶时间/温度/参数问法增加有界分类；
- 从“产品 + 工艺/自然结晶/企业认证”句式中只提取一个显式产品名称，由知识服务按冻结产品族精确
  校验，不生成 ID、不猜产品；
- Python 集成测试覆盖自然问法、公司成立问法和“白砂糖的企业认证资料是什么”；
- 真实 release golden 覆盖显式产品范围下的 `NO_DATA`。

状态：`FIXED / AUTOMATION_PASSED / LIVE_RETESTED`。

### RAG04-WEB-02 — 长知识回答导致审阅摘要校验错误泄漏

首次真实工艺命中返回 5 条证据后，后台审阅请求把完整回答同时写入 1000 字摘要字段，Java DTO
校验失败；旧全局异常处理又把 Controller 方法、字段名和 rejected value 直接返回，页面显示了
技术 alert。

修复内容：

- Web 按 Java DTO 契约分别将安全回答限制为 10000 UTF-16 单元、摘要限制为 1000，且不截断在
  未配对高代理项上；
- Java 对参数校验固定返回“请求参数校验失败”，其他非业务异常固定返回“服务异常，请稍后重试”，
  详细异常只写服务端日志；
- 刷新后的真实页面复验同一长回答：5 条来源、`alertCount=0`，无 Controller/字段/路径/内部 ID；
- Web 与 Java 定向测试和三端全量回归均通过。

状态：`FIXED / AUTOMATION_PASSED / JAVA_RESTARTED / LIVE_NORMAL_FLOW_RETESTED`。

## 5. 阻断解除与恢复记录

### RAG04-ENV-02 — `RESOLVED`

2026-08-02 用户停止原 8080/8091 进程后完成以下操作：

1. 重新执行 `mvn -q package -DskipTests`，修复后的 Java JAR 打包成功；
2. Java 首次启动暴露本地 UAT 环境缺少必填 `WECHAT_APP_SECRET`，随后仅为本地进程设置
   `uat-disabled` 占位值，不修改配置文件、不调用微信接口；
3. 修复后的 Java/Python 启动成功，健康状态为 `UP`、`toolGateway=UP`、`stateStore=UP`、
   `planningMode=DETERMINISTIC`、`rag=READY`；
4. UAT-05 在真实页面得到 `NO_DATA`，无来源和内部字段泄漏；
5. UAT-06 使用 `C:\tmp\laibin-rag04-degraded\sitecustomize.py` 临时替身受控令 query embedding
   失败，可靠关键词证据返回 `DEGRADED` 和 5 条来源；
6. 故障 Python 停止后删除临时替身，普通 Python 恢复；再次查询单晶黄冰糖工艺得到“现行资料”
   和 5 条来源；
7. 正式目录仍没有 `current.json`，corpus 未标记 `ACTIVE`。

服务密钥始终从受保护临时 env 注入，没有从其他进程内存、浏览器存储或日志提取，也没有关闭认证。

### 验收后运行时交接

完成上述 10 项 UAT 并恢复普通 Python 后，本轮 Java/Python 进程退出；00:16 又出现一组由其他
工作流启动的 Java/Python 监听进程（8080 PID 38292、8091 PID 29500），启动时间晚于本轮进程，
其 Python 服务密钥与本轮受保护配置不一致。本轮没有读取其进程内存或尝试提取密钥。

停止这组其他工作流进程可能中断并行任务，审批门禁要求用户在获知风险后明确授权。因此本项只影响
最终本地运行时交接，不改变此前已经采集的 10 项 UAT 结果，也不改变 corpus
`VALIDATED_NOT_ACTIVE` 状态。

用户随后明确要求不停止现有进程，改用其他端口重启。本轮因此保留原 8080/8091，不读取其密钥，
并启动隔离并行实例：

- Java：`http://127.0.0.1:18080`，PID 32512；为避免其他工作流重新打包 `target` 时影响延迟类加载，
  从 SHA-256 为 `B105A707603B6A41C7A12A74819398B55C6DB02B6EDAD2DF925D7B1E680D1C16` 的只读临时
  JAR 快照启动；
- Python：`http://127.0.0.1:18091`，监听 PID 39364；
- Java 配置 `AGENT_PYTHON_BASE_URL=http://127.0.0.1:18091`；
- Python 配置 `JAVA_TOOL_GATEWAY_BASE_URL=http://127.0.0.1:18080`；
- 最终健康检查为 `UP`、`toolGateway=UP`、`stateStore=UP`、`planningMode=DETERMINISTIC`、
  `rag=READY`，corpus version 为 `laibin-rag-2026-07-29-v1`；
- Java 内部网关探针通过服务密钥认证后返回预期 `AGENT_SESSION_NOT_FOUND`，证明请求到达会话
  门禁且未执行业务查询；Python 调用同样返回该预期错误，临时内存会话随后删除；
- 后台日志仅位于 `%LOCALAPPDATA%\Temp`，未写入仓库。

现有 Web/Vite 仍按原配置访问 8080；18080/18091 是不抢占现有工作流的隔离运行实例。若后续需要
Web 切换至该实例，应在独立部署/代理切换窗口处理，不属于本次并行启动。

并行 Java 的第一次启动曾直接引用工作区 `target` JAR；其他工作流在进程运行期间于 00:51 重新
写入该 JAR，导致 Logback 延迟类加载失败并退出。最终实例改用工作区外只读快照，完整性哈希与复制
时源 JAR 一致，后续构建不再影响该运行进程。

## 6. 当前结论

- Web 知识引用和成功、降级、无证据、不可用四种状态已完成代码、自动化和真实浏览器验证；
- ADMIN 正常工艺、企业资料、连续追问、无证据、降级、不可用、会话行为及 STAFF 双层门禁均通过；
- 10 项场景为 `10 PASS`；
- 自然问法、显式产品范围、真实 `NO_DATA`、审阅字段边界和异常安全修复已通过自动化并加载到当前本地进程；
- RAG-04 当前为 `COMPLETED / UAT_10_OF_10_PASSED`；
- 最终本地运行时已通过不抢占方式交接到并行 18080/18091；原 8080/8091 保持不动；
- RAG-05 不得开始正式激活，corpus 继续保持 `VALIDATED_NOT_ACTIVE`。
