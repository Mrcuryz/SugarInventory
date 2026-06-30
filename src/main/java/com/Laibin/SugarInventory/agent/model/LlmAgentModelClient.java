package com.Laibin.SugarInventory.agent.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Primary
@ConditionalOnProperty(prefix = "agent.model", name = "mode", havingValue = "llm", matchIfMissing = true)
public class LlmAgentModelClient implements AgentModelClient {
    private static final Logger log = LoggerFactory.getLogger(LlmAgentModelClient.class);

    private final OpenAIClient openAIClient;
    private final ObjectMapper objectMapper;
    private final RuleBasedAgentModelClient fallback = new RuleBasedAgentModelClient();
    private final String model;
    private final long maxTokens;

    public LlmAgentModelClient(OpenAIClient openAIClient,
                               ObjectMapper objectMapper,
                               @Value("${agent.model.name:${openai.model:deepseek-v4-flash}}") String model,
                               @Value("${agent.model.max-tokens:800}") long maxTokens) {
        this.openAIClient = openAIClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public AgentPlan plan(String message, Map<String, Object> pageContext) {
        String text = message == null ? "" : message.trim();
        if (text.isBlank()) {
            return new AgentPlan();
        }
        try {
            String llmText = callModel(text, pageContext);
            return parsePlan(llmText, text, pageContext);
        } catch (Exception e) {
            log.warn("Agent model planning failed, falling back to rules: {}", e.getClass().getSimpleName());
            return fallback.plan(message, pageContext);
        }
    }

    private String callModel(String message, Map<String, Object> pageContext) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .addSystemMessage(systemPrompt())
                .addUserMessage(userPrompt(message, pageContext))
                .responseFormat(ResponseFormatJsonObject.builder()
                        .type(JsonValue.from("json_object"))
                        .build())
                .maxTokens(maxTokens)
                .temperature(0.0)
                .build();
        ChatCompletion response = openAIClient.chat().completions().create(params);
        return response.choices().stream()
                .findFirst()
                .flatMap(choice -> choice.message().content())
                .orElseThrow(() -> new IllegalStateException("Model returned empty content."));
    }

    private AgentPlan parsePlan(String content, String originalMessage, Map<String, Object> pageContext) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(extractJson(content));
        AgentPlan plan = new AgentPlan();
        plan.setIntent(parseIntent(root.path("intent").asText(null)));
        plan.setEntityQuery(blankToNull(root.path("entityQuery").asText(null)));
        plan.setPalletCode(blankToNull(root.path("palletCode").asText(null)));
        plan.setProductionDate(normalizeDate(root.path("productionDate").asText(null)));

        Map<String, Object> hints = new LinkedHashMap<>();
        String assistantReply = blankToNull(root.path("assistantReply").asText(null));
        if (assistantReply != null) {
            hints.put("assistantReply", assistantReply);
        }
        String reason = blankToNull(root.path("reason").asText(null));
        if (reason != null) {
            hints.put("reason", reason);
        }
        plan.setHints(hints);

        if (plan.getIntent() == AgentIntent.UNSUPPORTED && assistantReply == null) {
            return fallback.plan(originalMessage, pageContext);
        }
        if (requiresEntity(plan.getIntent()) && plan.getEntityQuery() == null) {
            AgentPlan fallbackPlan = fallback.plan(originalMessage, pageContext);
            if (fallbackPlan.getIntent() == plan.getIntent() && fallbackPlan.getEntityQuery() != null) {
                plan.setEntityQuery(fallbackPlan.getEntityQuery());
            }
        }
        return plan;
    }

    private AgentIntent parseIntent(String value) {
        if (value == null || value.isBlank()) {
            return AgentIntent.UNSUPPORTED;
        }
        try {
            return AgentIntent.valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            return AgentIntent.UNSUPPORTED;
        }
    }

    private boolean requiresEntity(AgentIntent intent) {
        return intent == AgentIntent.INVENTORY_OVERVIEW
                || intent == AgentIntent.WAREHOUSE_STATUS
                || intent == AgentIntent.ASSAY_STATUS;
    }

    private String extractJson(String text) {
        if (text == null) {
            return "{}";
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String normalizeDate(String value) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        if ("TODAY".equalsIgnoreCase(text) || "今天".equals(text)) {
            return LocalDate.now().toString();
        }
        return text;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() || "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private String userPrompt(String message, Map<String, Object> pageContext) {
        String context = "{}";
        if (pageContext != null && !pageContext.isEmpty()) {
            try {
                context = objectMapper.writeValueAsString(pageContext);
            } catch (JsonProcessingException ignored) {
                context = "{}";
            }
        }
        return "用户消息：" + message + "\n页面上下文：" + context + "\n今天日期：" + LocalDate.now();
    }

    private String systemPrompt() {
        return """
                你是智能仓储系统的 Agent 规划器，只负责把用户自然语言转换成受控 JSON 计划。
                你不能回答业务数据，不能编造产品ID、库位ID、库存、化验结果，也不能设计或调用任何写操作。

                当前只允许以下只读意图：
                - INVENTORY_OVERVIEW：查询产品库存。entityQuery 必须是产品自然语言名称，例如 黄冰糖、黄冰糖（袋）。
                - WAREHOUSE_STATUS：查询库位状态。entityQuery 必须是库位自然语言名称，例如 2号库位、库位2。
                - PALLET_STATUS：查询托盘状态。palletCode 必须是托盘码。
                - ASSAY_STATUS：查询化验状态。entityQuery 是产品名称，productionDate 是 yyyy-MM-dd；用户说今天时输出 TODAY。
                - UNSUPPORTED：问候、自我介绍、能力咨询或当前不支持的问题。

                上下文规则：
                - 页面上下文里可能包含 conversationContext，包括最近消息、lastProductLabel、lastWarehouseLabel 和 pendingOptions。
                - 用户说“这些”“它”“这个”“刚才那个”“这些糖”时，应结合 conversationContext 解析指代，不要要求用户重复。
                - 用户追问“这些糖主要存放在哪个库位/哪些库位/位置”时，意图应为 INVENTORY_OVERVIEW，entityQuery 使用 lastProductLabel；不要把它误判成查询某个库位状态。
                - 用户从候选卡片中选择了具体产品或库位时，优先使用选择项的 displayLabel 作为 entityQuery，仍不要输出 ID。

                输出必须是严格 JSON 对象，不要 Markdown，不要解释文字。字段固定为：
                {
                  "intent": "INVENTORY_OVERVIEW | WAREHOUSE_STATUS | PALLET_STATUS | ASSAY_STATUS | UNSUPPORTED",
                  "entityQuery": "string or null",
                  "palletCode": "string or null",
                  "productionDate": "yyyy-MM-dd | TODAY | null",
                  "assistantReply": "仅当 intent=UNSUPPORTED 且是问候/自我介绍/能力咨询时填写，否则 null",
                  "reason": "简短规划原因"
                }

                抽取规则：
                - “帮我查一下当前黄冰糖的库存情况” => INVENTORY_OVERVIEW, entityQuery=黄冰糖。
                - “帮我查一下当前黄冰糖（袋）的库存情况” => INVENTORY_OVERVIEW, entityQuery=黄冰糖（袋）。
                - “查黄冰糖（袋）库存” => INVENTORY_OVERVIEW, entityQuery=黄冰糖（袋）。
                - “2号库位现在是什么情况” => WAREHOUSE_STATUS, entityQuery=2号库位。
                - “黄冰糖（袋）今天有没有化验” => ASSAY_STATUS, entityQuery=黄冰糖（袋）, productionDate=TODAY。
                - “你是谁” => UNSUPPORTED, assistantReply=我是智能仓储 AI 助手，可以帮你查询库存、库位、托盘和化验状态。

                安全边界：
                - 入库、出库、调拨、确认、作废、恢复、导入、更新、删除、质量标准修改都输出 UNSUPPORTED。
                - 不要输出 productId 或 warehouseId；后续 resolver 会解析，歧义时必须由用户选择。
                """;
    }
}


