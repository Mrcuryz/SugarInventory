package com.Laibin.SugarInventory.agent.model;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "agent.model", name = "mode", havingValue = "rule")
public class RuleBasedAgentModelClient implements AgentModelClient {
    private static final Pattern ISO_DATE = Pattern.compile("(20\\d{2}-\\d{1,2}-\\d{1,2})");
    private static final Pattern PALLET_CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{5,}");

    @Override
    public AgentPlan plan(String message, Map<String, Object> pageContext) {
        String text = message == null ? "" : message.trim();
        AgentPlan plan = new AgentPlan();
        if (text.isBlank()) {
            return plan;
        }
        if (containsAny(text, "托盘", "托盘码", "二维码")) {
            plan.setIntent(AgentIntent.PALLET_STATUS);
            plan.setPalletCode(extractPalletCode(text));
            return plan;
        }
        if (containsAny(text, "化验", "合格", "不合格")) {
            plan.setIntent(AgentIntent.ASSAY_STATUS);
            plan.setEntityQuery(cleanEntityQuery(text));
            plan.setProductionDate(extractDate(text));
            return plan;
        }
        if (containsAny(text, "库位", "号库", "号位", "仓位")) {
            plan.setIntent(AgentIntent.WAREHOUSE_STATUS);
            plan.setEntityQuery(cleanEntityQuery(text));
            return plan;
        }
        if (containsAny(text, "库存", "还有多少", "多少货", "余量")) {
            plan.setIntent(AgentIntent.INVENTORY_OVERVIEW);
            plan.setEntityQuery(cleanEntityQuery(text));
            return plan;
        }
        plan.setIntent(AgentIntent.UNSUPPORTED);
        return plan;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String extractPalletCode(String text) {
        Matcher matcher = PALLET_CODE.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return text.replaceAll("(查|查询|一下|托盘码|托盘|状态|现在|当前位置|流转|化验|信息|[，。,.?？])", "").trim();
    }

    private String extractDate(String text) {
        Matcher matcher = ISO_DATE.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        if (text.contains("今天")) {
            return LocalDate.now().toString();
        }
        return null;
    }

    private String cleanEntityQuery(String text) {
        String cleaned = text
                .replaceAll("今天", "")
                .replaceAll("(帮我|请帮我|麻烦|查|查询|一下|现在|当前|目前|有没有|是否|合不合格|合格|不合格|库存|库位状态|状态|化验|还有多少|多少|情况|的|[，。,.?？])", "")
                .trim();
        return cleaned.isBlank() ? text.trim() : cleaned;
    }
}


