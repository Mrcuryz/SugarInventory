# RAG Office 与本地 OCR 环境方案

状态：`IMPLEMENTED_AND_VALIDATED`

日期：2026-07-31

## 1. 目标与边界

本方案用于解除两项离线语料门禁：

1. 使用可无人值守、可重复的渲染器完成 9 份 DOCX 的逐页视觉核验。
2. 在材料不离开本机的前提下完成 16 页 PDF 的简体中文 OCR、区域坐标和置信度提取。

Office、LibreOffice、OCR 模型和 PDF 渲染组件只属于离线构建环境，不进入 Agent
运行时发布包。文档解析、OCR、规范化、切分和文档向量化仍然只在材料版本更新时执行一次。

## 2. 本机盘点结论

| 项目 | 结果 | 处理 |
| --- | --- | --- |
| 操作系统 | Windows 11 64 位 | 满足 LibreOffice 与本地 OCR 要求 |
| 内存 | 15.7 GB | 满足当前 16 页一次性 OCR |
| GPU | RTX 3060 Laptop，6 GB 显存 | 本期不依赖；保留后续 GPU 加速 |
| Microsoft Office | Home and Student 2019，Word 已安装 | 不作为无人值守渲染依赖 |
| LibreOffice | 26.2.5.2，已安装 | 作为 DOCX 无头渲染器 |
| OCR 引擎 | RapidOCR 3.9.2 / ONNX Runtime 1.23.2，已安装 | 隔离环境、CPU、本地推理 |
| Python | 3.12.2 64 位 | RapidOCR 与 ONNX Runtime 有可用 wheel |
| Poppler | 已有受控 `pdftoppm.exe` | 继续用于 PDF 250 DPI 页面渲染 |

Word 2019 保留用于人工打开材料，但不再使用 Word COM 作为构建门禁。此前 COM 调用已在当前
非交互桌面会话返回 `0x80070520`，不具备无人值守可重复性。

## 3. 决策

### 3.1 DOCX 渲染

- 安装 `TheDocumentFoundation.LibreOffice` Windows x86-64 稳定版。
- 调用技能包的 `render_docx.py`，由脚本为每次运行创建独立 LibreOffice profile 和可写
  临时目录。
- 不依赖 `soffice.exe` 已加入系统 `PATH`；优先解析标准安装路径并在验证日志中记录实际路径
  与版本。
- 9 份 DOCX 必须全部渲染为逐页 PNG，并人工检查全部页面；仅命令成功不等于通过视觉门禁。

### 3.2 本地 OCR

选择：

- `rapidocr==3.9.2`
- `onnxruntime==1.23.2`
- CPU execution provider
- RapidOCR 默认 PP-OCRv6 中文检测与识别小模型

选择理由：

1. 输入图像、识别文本和置信度全程留在本机，不需要 endpoint、API key 或外部材料上传。
2. RapidOCR wheel 预置默认模型，可在安装完成后离线运行。
3. 输出原生包含文本行、多边形坐标和置信度，可直接映射现有 `OcrProvider` 契约。
4. 当前任务只有 16 页且属于一次性构建，CPU 的稳定性和可复现性优先于 GPU 吞吐量。
5. 不引入 CUDA、cuDNN、TensorRT 与显卡驱动的组合约束；RTX 3060 仍可作为后续加速扩展。

本期不选择：

- Word COM：当前会话不可用，且不适合作为服务化/无人值守渲染门禁。
- Tesseract：可作为故障回退，但对当前中文宣传册、复杂背景和区域结构的默认效果不如
  PP-OCR 系列。
- 外部 HTTP OCR：当前没有甲方批准的服务地址、数据出境边界、凭据和费用信息。
- PaddleOCR 完整 PaddlePaddle 后端：功能更广，但当前只需要通用中文 OCR；本期避免额外
  框架、显存和版本耦合。

## 4. 安装与隔离

### 4.1 LibreOffice

首选安装命令：

```text
winget install --id TheDocumentFoundation.LibreOffice --exact --silent
```

安装后记录：

- `soffice.exe` 绝对路径；
- `soffice --version`；
- `winget list` 中的安装版本；
- 一个真实 DOCX 的 headless smoke test。

本机实施时 `winget` 下载完成后 MSI 事务长时间无进展，因此终止挂起事务并使用 winget
缓存中的官方 MSI 完成安装。MSI 返回 `3010`，表示安装成功且建议重启；当前不需要为了
离线构建立即重启。最终验证结果：

```text
C:\Program Files\LibreOffice\program\soffice.com --version
LibreOffice 26.2.5.2 cd7284b4cbbfeb507e630c1aac019f4157393acb
```

Windows 无头自动化应调用控制台入口 `soffice.com`，不要直接依赖 GUI 入口
`soffice.exe`。技能包渲染脚本还需要 Poppler 的 `pdfinfo.exe`、`pdftoppm.exe` 所在目录
进入当前进程 `PATH`；缺少该目录时 LibreOffice 可以生成 PDF，但不能生成逐页 PNG。

### 4.2 OCR 虚拟环境

创建不继承系统 site-packages 的独立环境：

```text
python -m venv agent-service\build\rag-build-venv
agent-service\build\rag-build-venv\Scripts\python.exe -m pip install -e "agent-service[rag-build]"
```

`rag-build` optional extra 固定 RapidOCR 和 ONNX Runtime 的兼容范围。该环境位于 Git 忽略的
构建目录；普通 Agent runtime 不安装 OCR 依赖。

## 5. Provider 接入

现有 `HttpJsonOcrProvider` 保留。新增本地 provider 后，通过显式配置二选一：

```text
RAG_OCR_ENABLED=true
RAG_OCR_PROVIDER=local-rapidocr
RAG_OCR_MODEL=rapidocr-3.9.2-ppocrv6-small-ch-onnx-cpu
```

本地 provider：

- 只接收内存中的 PNG bytes，不接收或记录材料绝对路径；
- 将 RapidOCR 的每个文本行转换为 `regions`；
- 将像素坐标归一化为现有 `[0,1]` 页面坐标；
- 页面置信度按非空文本行置信度加权计算；
- 初始化、推理或结果格式异常时使用稳定错误码，不猜测文本；
- 调用日志只记录 provider、模型、耗时、输入/输出字节数和成功/失败，不记录图片或 OCR 文本。

当 `RAG_OCR_PROVIDER=http-json` 时，继续使用现有 HTTPS endpoint、host allowlist、API key
和禁止重定向规则。本地 provider 不读取这些 HTTP 配置。

## 6. 验收门槛

### 6.1 LibreOffice

- `soffice --version` 成功。
- `render_docx.py` 可将真实 DOCX 渲染为非空 PDF 和逐页 PNG。
- 9 份 DOCX 的全部页面均已目检，无缺字、裁切、重叠、表格错位和异常字体替换。
- 视觉复核记录绑定输入文件 hash、页面 PNG hash 和页数。

### 6.2 OCR

- 在断开 provider 网络配置的情况下初始化并识别本地 fixture。
- 真实宣传册 16/16 页均生成非空 OCR 文本或明确失败记录。
- 每个非空区域包含文本、置信度和边界框；边界框均在 `[0,1]` 范围内。
- 同一页面重复运行的文本与区域顺序一致。
- 低于 `0.85` 的文本行保留 `OCR_LOW_CONFIDENCE`，不得自动提升为可信事实。
- 重要企业事实、数字、名称和流程结论仍需逐页人工对照。

### 6.3 回归与文档

- 本地 provider 单元测试、PDF parser 定向测试和 RAG 全量测试通过。
- 普通 Agent runtime 在未安装 `rag-build` extra 时仍可导入和运行。
- 更新运行手册、实现计划、开发日志和真实构建 manifest。

## 7. 回退与扩展

- LibreOffice 安装失败时，不退回 Word COM；记录安装错误并使用官方 MSI 做第二安装路径。
- RapidOCR 3.9.2 出现已确认的兼容问题时，允许回退到 3.9.1，但必须更新依赖、manifest、
  测试证据和本文件。
- CPU 性能不足时，可新增 `onnxruntime-gpu` 或 DirectML provider；不得在同一构建中静默
  切换 execution provider，实际 provider 必须进入 manifest。
- 甲方后续批准外部 OCR 时，可继续使用 `http-json` provider，不改变 PDF parser 契约。

## 8. 官方依据

- LibreOffice Windows 系统要求：<https://www.libreoffice.org/system-requirements/>
- LibreOffice 官方下载：<https://www.libreoffice.org/download/download-libreoffice/>
- RapidOCR 官方仓库：<https://github.com/RapidAI/RapidOCR>
- RapidOCR 模型列表：<https://rapidai.github.io/RapidOCRDocs/latest/model_list/>
- RapidOCR 参数说明：<https://rapidai.github.io/RapidOCRDocs/latest/install_usage/rapidocr/parameters/>
- RapidOCR PyPI：<https://pypi.org/project/rapidocr/>

## 9. 2026-07-31 实施与验收结果

### 9.1 安装与隔离

- LibreOffice：`26.2.5.2`，控制台入口为标准安装目录下的 `soffice.com`。
- 隔离环境：`agent-service/build/rag-build-venv`，不继承系统 site-packages。
- RapidOCR：`3.9.2`。
- ONNX Runtime：`1.23.2`，实际可用执行后端为 `CPUExecutionProvider`；
  不启用 CUDA，也不依赖外部 OCR endpoint。
- 默认模型：`rapidocr-3.9.2-ppocrv6-small-ch-onnx-cpu`，模型随安装包缓存在本地。

### 9.2 DOCX 真实材料验证

- 9/9 DOCX 成功渲染，共 14 页。
- 14/14 页完成目检，无裁切、重叠、缺字形或表格布局破损。
- 5 份文档具有尾部空白第 2 页，已识别并保留，未擅自删除或修改原文件。
- 新的可审计渲染与已目检渲染逐页 SHA-256 比对：14/14 一致、差异 0。
- 每份 extraction 已保存 renderer/version、页数、像素尺寸、空白标记和页面 SHA-256；
  `visualValidationPendingCount` 从 9 降为 0。
- `单晶黄冰糖工艺流程图24.12.docx` 页内标题为“咖啡调糖生产工艺流程”，继续保留
  `TITLE_CONFLICT`，不由环境配置阶段修正文义。
- DOCX extraction manifest SHA-256：
  `db9643abff1803b76e520cd9a3df81b96a8013f2a6ccdb92b017b556a5837857`。

### 9.3 PDF 本地 OCR 验证

- 16/16 页渲染成功、OCR 状态均为 `SUCCEEDED`，OCR 待处理页数为 0。
- 16/16 页视觉复核为 `SUCCEEDED`，视觉待处理页数为 0。
- 全部非空区域均带原文、置信度和 `[0,1]` 归一化坐标。
- 第 15 页平均置信度约 `0.818`，保留 `OCR_LOW_CONFIDENCE`；人工检查确认低置信区域
  集中在证书缩略图，不能自动升级为可信事实。
- 同一真实页面跨独立进程重复识别的文本结果一致。
- 脱敏调用日志记录 provider 为 `local-rapidocr`、模型为
  `rapidocr-3.9.2-ppocrv6-small-ch-onnx-cpu`，不记录 OCR 正文或本机路径。
- PDF extraction SHA-256：
  `af605269166418bcc4fef13a0caa582a9edcbf4598e27b48a77d0a312e11ec2e`。
- PDF extraction manifest SHA-256：
  `d0665370d34977f03eb9fa7c3bad3dc2bc79f507d810426214be3d1acc8f2717`。

### 9.4 自动化验证

- DOCX 复核与本地 OCR 定向测试：`18 passed`。
- RAG 全量测试：`47 passed, 3 skipped`。
- Agent Service 全量回归：`363 passed, 3 skipped`。
- `compileall` 通过。

环境与 provider 配置已经完成。后续仍需在规范化阶段处理流程关系、标题冲突、OCR
低置信区域和重要宣传事实对照；这些内容级门禁不属于 Office/OCR 环境故障。
