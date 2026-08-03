# RAG-01D 规范化与内容复核报告

状态：`PASSED_WITH_RETAINED_WARNINGS`

日期：2026-07-31
corpus version：`laibin-rag-2026-07-29-v1`

## 1. 结论

10 份甲方现行材料已完成一次性离线规范化和内容复核，正式语料状态为
`VALIDATED`。本阶段没有生成知识块、关键词索引、向量索引，也没有切换为运行时
`ACTIVE` 版本。

验收汇总：

| 项目 | 结果 |
| --- | ---: |
| 规范化文档 | 10/10 |
| 规范化页面 | 30 |
| PDF 已复核页面 | 16/16 |
| 有编号工艺步骤 | 108 |
| 结构化参数 | 32 |
| CCP/CPP 标记 | 14 |
| 表格设备字段 | 8 |
| 源文尾随空白页 | 5 |
| 阻断问题 | 0 |
| 已复核并保留的 warning | 8 |

正式 artifact：

```text
agent-service/build/rag-r01d-release-2026-07-31/
  releases/laibin-rag-2026-07-29-v1/
```

## 2. 冻结摘要

| 摘要 | SHA-256 |
| --- | --- |
| source inventory | `cefd802a6bbee06ee211c45b1638c57a7c5f03458a5db843ee0ec3649abfd574` |
| content review profile | `44da044e3669d1f8585be70f61708029421fb5a3e765c30c88aaafd8179e05ee` |
| normalized document manifest | `4fcf6769da4d725e778fc0cc2193dd25b2822f1c11a4931415847b4d45fd7b2f` |

复核配置位于：

```text
docs/agent/rag/profiles/laibin-rag-2026-07-29-v1.normalization-review.json
```

配置同时绑定 corpus、inventory、每份 source 和 extraction 的 SHA-256。任何一项变化均
必须重新复核并生成新配置，normalizer 会拒绝沿用旧结论。

## 3. 逐文档结论

| 文档 | 产品/主题 | 结论 | 保留质量项 |
| --- | --- | --- | --- |
| 单晶黄冰糖工艺流程 | 单晶黄冰糖 | 16 个编号步骤及已审核关系进入语料；参数与 CPP1/CCP2 保留 | `TITLE_CONFLICT`、`POSSIBLE_TYPO` |
| 白砂糖分装工艺流程 | 白砂糖 | 控制点表按工艺名称映射；电子秤、金属探测器和限值结构化 | `CONTROL_POINT_LABEL_CONFLICT` |
| 红糖分装工艺流程 | 红糖 | 控制点表按工艺名称映射；KD-990 与三类金属限值结构化 | 无 |
| 黑糖及风味糖制品工艺流程 | 黑糖、风味调味红糖制品、风味黑糖制品 | 编号步骤、原辅料分支及 CCP1/CCP2 保留 | 无 |
| 多晶体冰糖及风味冰糖工艺流程 | 白/黄多晶体冰糖、梨汁/菊花风味冰糖 | 15 个编号步骤、时间/温度参数和 CCP1 保留 | 无 |
| 单晶体冰糖及风味单晶体冰糖工艺流程 | 单晶体冰糖、风味单晶体冰糖 | 参数和 CCP1 保留；“压力温度：60-90℃”不代改 | `POSSIBLE_TYPO` |
| “来冰”企业宣传册 | 企业、产品、荣誉、销售、认证 | 16 页逐页归类并人工校正文；低置信证书缩略图不进入知识事实 | `OCR_LOW_CONFIDENCE`、`POSSIBLE_TYPO` |
| 赤砂糖分装工艺流程 | 赤砂糖 | 控制点表按工艺名称映射；KD-990 与三类金属限值结构化 | 无 |
| 冰红糖、黄糖、冰片糖、黑糖工艺流程 | 四类产品 | 9 个编号步骤和参数保留；“压力温度126～140°C”不代改 | `POSSIBLE_TYPO` |
| 糖分装工艺流程 | 未明确具体糖类 | 13 个编号步骤进入语料，不猜测产品范围 | `POSSIBLE_SCOPE_AMBIGUITY` |

## 4. PDF 内容取舍

- 封面装饰 OCR、仓库图片编号、产品页装饰字符和结束页 `THANKS` 不进入
  `normalizedText`，但仍保留在来源 OCR 或排除清单中。
- 企业地址和联系电话已按渲染页核对后进入规范化正文。
- 荣誉页只保留清楚可辨的奖项、榨季和产品类型，不采用 OCR 误识别的细小编号。
- 产品功效、适用人群、合作方、销售网络和工程计划均明确作为“宣传册陈述”，不转换为
  医疗建议、实时覆盖或当前合作承诺。
- 第 15 页只保留可确认的 ISO9001、ISO22000、HACCP、FSSC22000 体系名称及大段正文；
  低置信证书编号、范围和日期不作为知识事实。

## 5. 数据质量决定

- 未提供生效日期时字段保持 `null`，不推断、不告警、不阻塞。
- owner、reviewer、approval 和替代关系保持预留字段，不生成缺失告警。
- 所有文档仅允许 `ADMIN` 和 `SUPER_ADMIN`。
- 源文疑似错字和重复在 `sourceText` 中原样保留；人工确认后的文本只进入
  `normalizedText` 和复核说明。
- 白砂糖步骤 6 同时出现 `CCP3` 与 `CCP1`，两个标签均保留，等待业务方未来裁决。

## 6. 自动化验收

正式发布黄金测试覆盖：

- 10 份 `DocumentContract` 和 `CorpusManifest`/`QualityReport` 契约校验；
- 每份 `documentSha256` 重算；
- document manifest 使用 `documentId` 稳定排序后重算；
- 计数、管理员角色、空白页、白砂糖控制点/设备/限值；
- 宣传册联系电话、第 15 页低置信标记和证书编号排除。

执行方式：

```text
set LAIBIN_RAG_NORMALIZED_RELEASE=D:\Laibin\LaibinSugarInventory\agent-service\build\rag-r01d-release-2026-07-31\releases\laibin-rag-2026-07-29-v1
python -m pytest -q tests/rag/test_real_normalization_golden.py
```

本次结果：`1 passed`。

## 7. 后续边界

下一切片为 `RAG-02`：从当前 `VALIDATED` document 生成可引用知识块、关键词索引、
向量索引和固定检索评测。只有 RAG-02 质量门禁通过后，才能进入运行时集成；不得在
Agent 请求过程中重新解析、OCR 或规范化原始 Office/PDF 材料。
