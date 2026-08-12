# 标签打印助手暴露面收口

日期：2026-08-12

范围：主业务应用组件扫描、独立标签打印助手 HTTP 边界、Web 配对、批量输入上限、Agent/MCP 边界

状态：代码、全量自动化验证、真实浏览器验收和临时环境清理均已完成

## 1. 审查结论

历史设计正确要求打印助手只监听 `127.0.0.1:9527`，但真实代码仍有三类风险：

1. `SugarInventoryApplication` 只排除了 `PrinterAssistantApplication` 启动类，没有排除其 Controller、Service 和配置。主业务服务因此实际暴露 7 条无 `/api` 前缀的打印助手路径。
2. `PrinterAssistantCorsConfig` 的全路径、任意来源、任意方法配置也进入主服务，扩大了整个业务 API 的跨域暴露面。
3. 独立助手没有认证。任何能从浏览器访问回环地址的网页都可枚举本机打印机、查看本地状态、修改默认打印机或提交任意标签；标签数量也没有上限。

该问题与 Agent 是否登记打印工具无关：MCP 文档虽已标记本机打印为 `FORBIDDEN`，主应用组件扫描和浏览器本地服务仍必须由代码边界独立保证。

## 2. 主服务隔离

主应用的组件扫描使用包级正则排除：

```text
com.Laibin.SugarInventory.printerassistant..*
```

因此主服务不再实例化打印助手 Controller、Service、桌面配置或 CORS 配置。`docs/openapi.json` 必须以真实运行实例重导，并验证下列路径全部不存在：

- `/health`
- `/printers`
- `/status`
- `/config`
- `/config/default-printer`
- `/print`
- `/print/test`

独立 `PrinterAssistantApplication` 仍只扫描 `printerassistant` 包，功能不会因主应用隔离而丢失。

## 3. 独立助手认证

- 首次启动使用 `SecureRandom` 生成 32 字节随机密钥，以 URL-safe Base64 保存到当前 Windows 用户目录。
- 也可通过 `printer-assistant.access-key` / `PRINTER_ASSISTANT_ACCESS_KEY` 在进程级覆盖；覆盖值少于 24 个字符时启动失败。
- `/health` 只返回静态在线状态，无打印机、配置、日志路径或打印记录。
- 其余接口必须携带 `X-Laibin-Printer-Key`，比较使用常量时间字节比较；错误时统一返回 401，不返回期望密钥或本机信息。
- HTTP API 永不返回连接密钥；桌面 GUI 提供本机复制按钮。
- CORS 仅允许 `GET`、`POST`、`OPTIONS` 及必需请求头。跨域可达性不再等于调用权限，随机密钥是实际认证边界。

连接密钥保护的是“网页到当前 Windows 用户本机打印服务”的本地能力，不替代主系统 JWT，也不能被服务端 Agent、Python Runtime 或 MCP Server 使用。

## 4. Web 配对

Web 设置弹窗先调用公开健康检查，再要求用户从桌面助手复制连接密钥。密钥只写入当前标签页所属浏览器会话的 `sessionStorage`：

- 不进入 Pinia 持久化；
- 不写入后端；
- 不进入 URL、日志或操作审计；
- 会话结束后自动失效，需要重新配对。

未配对、密钥错误或助手不可用时，既有业务仍回退到 PDF 导出，不绕过业务接口执行打印或启用。

## 5. 资源与输入边界

单个打印请求限制如下：

| 项目 | 上限 |
| --- | ---: |
| 标签数量 | 100 |
| 标签数 × copies | 200 页 |
| copies | 20 |
| template | 64 字符 |
| printerName | 255 字符 |
| code | 128 字符 |
| title | 100 字符 |

这些上限在进入图片渲染和 Windows 打印队列前由 Bean Validation 拒绝，防止浏览器请求无限放大内存和打印队列。

## 6. Agent/MCP 边界

本机打印继续保持 `FORBIDDEN`：

- 不加入 Tool Capability Registry；
- 不加入 Java/Python 专家白名单；
- 不加入 MCP Server；
- 不通过任意 HTTP 代理间接暴露；
- 服务端“已打印未入库”等工具只查询数据库业务事实，不表示模型能控制打印机。

## 7. 自动化验证

- `PrinterAssistantAccessKeyServiceTest`：随机密钥持久化、重启稳定、错误密钥拒绝和最小长度门禁。
- `PrinterAssistantAccessInterceptorTest`：缺失密钥返回 401、正确密钥通过、预检请求可完成。
- `PrintJobRequestValidationTest`：正常任务通过，超标签数、超总页数和超长编码失败。
- `MainApplicationPrinterIsolationTest`：主应用保持打印助手整包排除规则。
- `PrinterAssistantApplicationContextTest`：独立助手真实 Servlet 容器可创建 Controller、认证拦截器与密钥服务。
- 前端 `printerAssistantCredential.test.mjs`：密钥只进入指定会话存储，短密钥拒绝且可清除。
- 原打印渲染、配置和执行单元测试继续通过；前端生产构建通过。

最终结果：后端 113 个测试套件、424 个测试，0 failure / 0 error / 0 skipped；前端相关 Node 测试 4/4；前端生产构建通过。

## 8. 真实浏览器验收

使用桌面影子环境启动主业务服务、独立打印助手和 Web，所有测试均从真实浏览器发起。验收使用进程级一次性连接密钥，没有读取或改写本机正式助手密钥，也没有调用测试打印或合法打印任务。

验收结果：

1. 打印设置页先识别到本机助手，未配对时显示连接密钥提示，测试打印和保存默认打印机按钮禁用。
2. 浏览器直接访问 `/health` 返回 200、静态在线消息和空数据；不带密钥访问 `/printers` 返回 401、空数据，未泄露打印机信息。
3. 在设置弹窗输入一次性密钥后显示“已安全连接”，受保护 `/status` 返回运行状态；原始响应不包含连接密钥。
4. 密钥仅存在 `sessionStorage`，不进入 `localStorage`；清除会话密钥并刷新后，页面重新进入未配对状态且保护按钮再次禁用。
5. 携带正确密钥提交 101 个标签时返回 400“单次最多打印 100 个标签”；之后 `lastPrintStatus` 仍为空，证明请求未进入渲染或 Windows 打印队列。
6. 主服务 `/v3/api-docs` 共有 207 条路径，7 条本机打印路径全部不存在；从 Web 跨域直接读取主服务文档被浏览器阻止，证明打印助手的全局宽松 CORS 不再污染主应用。
7. OpenAPI 快照与运行实例 207 条路径完全一致，Agent 上游注册表 60 条路径全部存在。
8. 负向 401/400/CORS 场景会按浏览器规范记录失败资源；关闭负向测试页后重新打开正常业务页与未配对设置弹窗，控制台为 0 error。

验收结束后关闭浏览器，清除会话密钥，并清理临时账号、角色、助手用户目录与全部临时服务；影子库没有新增打印、二维码、任务或库存数据。
