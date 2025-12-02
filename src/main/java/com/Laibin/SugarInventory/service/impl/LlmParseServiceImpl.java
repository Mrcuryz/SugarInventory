package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AutoInboundParseRequest;
import com.Laibin.SugarInventory.domain.enumObject.AutoInboundType;
import com.Laibin.SugarInventory.domain.po.LlmParseResult;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.service.LlmParseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlmParseServiceImpl implements LlmParseService {

    private final OpenAIClient openAIClient;
    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;
    private final WarehouseMapper warehouseMapper;
    private static final Logger log = LoggerFactory.getLogger(LlmParseServiceImpl.class);

    @Override
    public LlmParseResult parseReport(AutoInboundParseRequest request) {
        String productsJson = buildProductsJson(request);
        String warehousesJson = buildWarehousesJson();

        String systemMessage = buildSystemPrompt();
        String userMessage = buildUserPrompt(request, productsJson, warehousesJson);

        String llmText = callOpenAi(systemMessage, userMessage);
        log.info("LLM raw output: {}", llmText);
        try {
            String jsonText = extractJsonBlock(llmText);
            log.info("LLM json to parse: {}", jsonText);
            return objectMapper.readValue(jsonText, LlmParseResult.class);
        } catch (Exception e) {
            throw new RuntimeException("LLM JSON 解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从 LLM 输出中提取第一个 { ... } JSON 块。
     * 允许前后有说明文字、代码块标记等。
     */
    private String extractJsonBlock(String text) {
        if (text == null) return "";

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return text.substring(start, end + 1).trim();
        }

        // 找不到大括号，就退而求其次
        return text.trim();
    }

    private String buildProductsJson(AutoInboundParseRequest request) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        List<Product> products = productMapper.selectList(wrapper);

        try {
            List<Map<String, Object>> productSummary = products.stream()
                    .map(p -> {
                        // 规格：例如 "40kg袋"（40kg + 包装方式）
                        String spec = "";
                        if (p.getWeightPerPiece() != null) {
                            spec += p.getWeightPerPiece()
                                    .stripTrailingZeros()
                                    .toPlainString() + "kg";
                        }
                        if (p.getPackagingMethod() != null) {
                            spec += p.getPackagingMethod();  // “袋/箱/罐…”
                        }

                        Map<String, Object> map = new java.util.HashMap<>();
                        map.put("id", p.getId());
                        map.put("product_name", p.getProductName());
                        map.put("product_type", p.getProductType());
                        map.put("status", p.getStatus());
                        map.put("spec", spec);                  // 给 LLM 用的规格文本
                        map.put("weight_per_piece", p.getWeightPerPiece()); // 数值规格也保留，方便模型对齐 40kg
                        return map;
                    })
                    .toList();

            return objectMapper.writeValueAsString(productSummary);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化产品列表失败", e);
        }
    }

    private String buildWarehousesJson() {
        List<Warehouse> warehouses = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>()
        );

        try {
            List<Map<String, Object>> warehouseSummary = warehouses.stream()
                    .map(w -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", w.getId());
                        map.put("warehouse_name", w.getWarehouseName());
                        return map;
                    })
                    .toList();

            return objectMapper.writeValueAsString(warehouseSummary);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化库位列表失败", e);
        }
    }


    private String buildSystemPrompt() {
        return """
               你是一个“冰糖仓储报数解析器”。
       
               **目标：**
               把手工报数文本解析成结构化的入库记录，用于自动或半自动入库。
       
               **输入说明：**
               - 文本是中文，包含班组、日期、计时/计件说明、成品/半成品报数、备注等。
               - 有两种解析模式：
                 - SEMI_PRODUCT：机破/直破等半成品报数。
                 - FINISHED_PRODUCT：翻包组成品报数。
               - 报数中经常出现：
                 - 规格前缀：如“40kg深黄小颗粒”里的“40kg”是单件重量规格；
                 - 数量：形如“8板+21件”“3板十10件”“4件”等，“+”“十”都视为加号；
                 - 库位：如“6号烘房”“1号库位”“3号库位”，也可能缺失；
                 - 一些说明词：如“直破”“机破2组报数”“（14.15.16.17组）”等，这些应该进入备注，而不是产品名。
       
       
               **可选产品列表 productCatalog（必须从中选择 product_id）：**
               {PRODUCTS_JSON}
       
               **可选库位列表 warehouseCatalog（必须从中选择 warehouse_id）：**
               {WAREHOUSES_JSON}
       
               **解析要求：**
       
               1. 先按语义，把原始文本分成若干条“入库项”，每条对应一种产品的一次报数。对于每种产品，首先应区分是成品或半成品。
                  用户提示中会提供一行：`解析类型：{parseType}`，其中 parseType ∈ {SEMI_PRODUCT, FINISHED_PRODUCT, MIXED}。
                  你必须根据 parseType 决定哪些内容可以作为顶层入库项 items，哪些只能作为成品的用料 sources。
                  当 parseType = "SEMI_PRODUCT" 时，仅根据 productCatalog 中的半成品生成items，包含半成品信息，sources 为空；
                  当 parseType = "FINISHED_PRODUCT" 时，每一条入库项必须为成品，关联的半成品写入 sources；如果能判断属于某个成品，则必须挂在该成品 item 的 `sources` 中
                  详细的规则见下面的解析要求。
                  - 班组、人员、计时等信息不单独生成入库项，可以合并到备注里。
               2. 对每一条入库项，抽取：
                  - `type`：只能是 `"SEMI_PRODUCT_IN"` 或 `"FINISHED_PRODUCT_IN"` 或 `"OTHER"`。
                  - `raw_block`：这一条对应的原始多行文本，原样拷贝。
                  - 数量：
                    - `quantity.pallets`：板数，整数，缺失则 0。
                    - `quantity.pieces`：件数，整数，缺失则 0。
                  - 产品：
                    - 从文本中抽取 `product_name_raw`，例如：“40kg深黄小颗粒”、“40kg纯白碎冰”等；
                    - 确保已全部了解 productCatalog 中的 `product_name`、`product_type`、`weight_per_piece` 等信息，
                      从中选择**最匹配的一条产品**，输出：
                      - `product_id`：来自 productCatalog.id；
                      - `product_name`：来自 productCatalog.product_name，必须完全一致。
                    - 如果确实找不到合适的产品，`product_id` 设为 null，`product_name` 用空字符串。
                    - 如果有多个产品匹配，选择最匹配的那条，其他候选产品写入当前 item 的 `reason` 字段中，标明原因。
                  - 库位：
                    - 从文本中抽取库位原文 `location`，例如“6号烘房”“1号库位”；
                    - 在 warehouseCatalog 中选择最匹配的一条，输出：
                      - `warehouse_id`：来自 warehouseCatalog.id；
                      - `warehouse_name`：来自 warehouseCatalog.warehouse_name，必须完全一致。
                    - 如果没有提到库位，`warehouse_id` 设为 null，`warehouse_name` 用空字符串。
                  - 日期：
                    - 如果这一条中没有单独的日期，使用整体入库日期 `entryDate`（由用户传入），否则必须使用所在行的日期，
                      比如“2025年11月28日\\n机破正中11月26号18板(6号库)”，则实际日期为2025-11-26。
                    - 输出到 `production_date` 字段，例如 `"2025-11-25"`。
                  - 半成品关联：
                    - 当 `type` 为 `"FINISHED_PRODUCT_IN"` 且 parseType = "FINISHED_PRODUCT" 时，
                      - 当前 item 一定是成品；
                      - 当前成品后面出现的半成品用量信息，必须解析为当前 item 的 `sources`。
                    - 一种成品可能对应多个半成品，可以生成多个 sources 条目。
                      - 在成品的原始文本块之后，遇到所有“带数量的半成品/用料描述”，都解析为 `sources`，直到遇到下一条成品描述或文本结束
                    - 文本可能包含所使用的半成品信息，如"用50kg白砂糖57件",解析后填入 `sources`,包含：
                      -   产品类型 `sourceType` 即SEMI_PRODUCT_IN;
                      -   品名原文 `productNameRaw`;
                      -   产品 ID `semiProductId`；（必须从 productCatalog 中选择）
                      -   产品名称 `productName`；（必须从 productCatalog 中选择）
                      -   生产日期 `productionDate`（如 2025-10-18），缺失则 null;
                      -   板数 / 件数 `boardCount` / `pieceCount`;
                      -   批号 / 其他附属信息 `batchNo`;
                      -   备注 `remark`；
                  - 原因解释：
                    - 当本条入库项存在不确定、无法解析或多种可能时，
                      把**相关原文片段**和解释文字写入 `reason` 字段（字符串或简单数组均可），
                      如：
                      - “未能根据‘黄小颗粒’在产品列表中唯一定位产品”、
                      - “生产日期‘10月’无法解析确切日”。
                  - 备注：
                    - 把对入库有帮助但不适合放入结构字段的信息，汇总到 `remark`，
                      如“直破”“机破2组报数（14.15.16.17组）”等。
       
               3. 输出格式必须是 **严格的 JSON**，不能使用 Markdown 代码块，不要出现 ```json。
                  - 半成品解析示例：
                    ```json
                    {
                      "items": [
                        {
                          "index": 1,
                          "type": "SEMI_PRODUCT_IN",
                          "raw_block": "直破40kg深黄小颗粒8板+21件（1号库位）",
                          "product_name_raw": "40kg深黄小颗粒",
                          "product_id": 201,
                          "product_name": "深黄小颗粒40kg（袋）",
                          "quantity": { "pallets": 8, "pieces": 21 },
                          "warehouse_id": 1,
                          "warehouse_name": "1",
                          "location": "6号烘房",
                          "production_date": "2025-11-25",
                          "sources": ,
                          "reason" : ...,
                          "remark": "直破；机破2组报数（14.15.16.17组）",
                        }
                        // ...
                      ],
                      "globalRemarks": ["..."]  // 只用于全局性文字备注：
                                                // —— 班组、人员、日期、计时/计件规则说明等。
                                                // 禁止把任何包含具体产品 + 数量（板 / 件等）的语句放入 globalRemarks，
                                                // 这类语句必须解析为 items 或 items[i].sources。
                    }
                    ```
                  - 成品解析示例：
                    ```json
                  {
                     "items": [
                       {
                         "index": 1,
                         "type": "FINISHED_PRODUCT_IN",
                         "raw_block": "翻刘化美25Kg袋黄小颗粒 200件（柳冰）\\n合格证印2025.11.25\\n直破黄小颗粒\\n10月5号2板\\n11月16号3板十20件",
                         "product_name_raw": "25Kg袋黄小颗粒",
                         "product_id": 109,
                         "product_name": "黄小颗粒（袋）",
                         "quantity": { "pallets": 0, "pieces": 200 },
                         "warehouse_id": null,
                         "warehouse_name": "",
                         "location": "",
                         "production_date": "2025-11-25",
                         "sources": [
                           {
                             "sourceType": "SEMI_PRODUCT_IN",
                             "productNameRaw": "直破黄小颗粒",
                             "semiProductId":  50,
                             "productName": "黄小颗粒",
                             "productionDate": "2025-10-05",
                             "boardCount": 2,
                             "pieceCount": 0,
                             "batchNo": null,
                             "remark": "直破黄小颗粒；10月5号2板"
                           },
                           {
                             "sourceType": "SEMI_PRODUCT_IN",
                             "productNameRaw": "直破黄小颗粒",
                             "semiProductId":  50,
                             "productName": "黄小颗粒",
                             "productionDate": "2025-11-16",
                             "boardCount": 3,
                             "pieceCount": 20,
                             "batchNo": null,
                             "remark": "直破黄小颗粒；11月16号3板十20件"
                           }
                         ],
                         "reason": [],
                         "remark": "翻包组报数；合格证印2025.11.25"
                       }
                     ],
                     "globalRemarks": [
                       "翻包组报数，11月25日（新.秀.财.妹.龙.花.兰）",
                       "计时信息：每人计时6.5个小肘"
                     ]
                   }
                    ```
               请严格按照上面的字段名输出，不要添加额外的顶层字段。
               """;
    }

    private String buildUserPrompt(AutoInboundParseRequest req, String productsJson, String warehousesJson) {
        return """
        入库日期：%s
        解析类型：%s  （SEMI_PRODUCT / FINISHED_PRODUCT / MIXED）

        【产品列表（productCatalog）】
        %s

        【库位列表（warehouseCatalog）】
        %s

        【报数原文】
        %s

        请根据系统说明，输出 JSON 结果。
        """.formatted(
                req.getEntryDate(),
                req.getParseType(),
                productsJson,
                warehousesJson,
                req.getRawText()
        );
    }

    private String callOpenAi(String systemMessage, String userMessage) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                // 直连 OpenAI 用官方模型名
                .model("gpt-4o-mini")
                // system prompt 单独放在 instructions 字段
                .instructions(systemMessage)
                // 用户真实输入
                .input(ResponseCreateParams.Input.ofText(userMessage))
                .temperature(0.1)
                .build();

        try {
            Response resp = openAIClient.responses().create(params);
            return extractTextFromResponse(resp);
        } catch (Exception e) {
            // 这里抛 BusinessException，前端 msg 至少能看到具体原因
            log.error("调用 OpenAI 失败: {}", e.getMessage(), e);
            throw new BusinessException("调用大模型失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * 把 Response 里的所有 text 段拼接起来
     */
    private String extractTextFromResponse(Response resp) {
        // 直接把 Response 映射为 JsonNode，而不是先转字符串再 parse
        JsonNode root = objectMapper.valueToTree(resp);

        StringBuilder sb = new StringBuilder();
        JsonNode outputArray = root.path("output");
        if (outputArray.isArray()) {
            for (JsonNode outItem : outputArray) {
                JsonNode contentArray = outItem.path("content");
                if (contentArray.isArray()) {
                    for (JsonNode contentItem : contentArray) {
                        JsonNode textNode = contentItem.get("text");
                        if (textNode != null && !textNode.isNull()) {
                            sb.append(textNode.asText());
                        }
                    }
                }
            }
        }
        return sb.toString();
    }
}

