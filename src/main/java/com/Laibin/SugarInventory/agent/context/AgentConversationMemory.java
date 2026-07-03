package com.Laibin.SugarInventory.agent.context;

import com.Laibin.SugarInventory.agent.vo.AgentChoiceOptionVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentConversationMemory {
    private static final int MAX_MESSAGES = 8;
    private static final int MAX_OPTIONS = 12;

    private final Map<String, ConversationState> states = new ConcurrentHashMap<>();

    public void recordUserMessage(String agentSessionId, String message) {
        state(agentSessionId).addMessage("user", message);
    }

    public void recordAssistantMessage(String agentSessionId, String message) {
        state(agentSessionId).addMessage("assistant", message);
    }

    public void rememberProduct(String agentSessionId, Integer productId, String displayLabel, String query) {
        if (productId == null && isBlank(displayLabel) && isBlank(query)) {
            return;
        }
        state(agentSessionId).rememberProduct(productId, displayLabel, query);
    }

    public void rememberWarehouse(String agentSessionId, Integer warehouseId, String displayLabel, String query) {
        if (warehouseId == null && isBlank(displayLabel) && isBlank(query)) {
            return;
        }
        state(agentSessionId).rememberWarehouse(warehouseId, displayLabel, query);
    }

    public void rememberOptions(String agentSessionId, List<AgentChoiceOptionVO> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        state(agentSessionId).rememberOptions(options);
    }

    public SelectedOption resolveSelectedOption(String agentSessionId, Map<String, Object> selectedOption) {
        if (selectedOption == null || selectedOption.isEmpty()) {
            return null;
        }
        return state(agentSessionId).resolveSelectedOption(selectedOption);
    }

    public ConversationSnapshot snapshot(String agentSessionId) {
        return state(agentSessionId).snapshot();
    }

    public Map<String, Object> modelContext(String agentSessionId) {
        ConversationSnapshot snapshot = snapshot(agentSessionId);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("recentMessages", snapshot.recentMessages());
        context.put("lastProductQuery", snapshot.lastProductQuery());
        context.put("lastProductLabel", snapshot.lastProductLabel());
        context.put("lastWarehouseQuery", snapshot.lastWarehouseQuery());
        context.put("lastWarehouseLabel", snapshot.lastWarehouseLabel());
        context.put("pendingOptions", sanitizedOptions(snapshot.pendingOptions()));
        return context;
    }

    private List<Map<String, Object>> sanitizedOptions(List<Map<String, Object>> options) {
        List<Map<String, Object>> sanitized = new ArrayList<>();
        for (Map<String, Object> option : options) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("optionType", option.get("optionType"));
            item.put("displayLabel", option.get("displayLabel"));
            item.put("supported", option.get("supported"));
            sanitized.add(item);
        }
        return sanitized;
    }
    public void clear(String agentSessionId) {
        states.remove(agentSessionId);
    }

    private ConversationState state(String agentSessionId) {
        return states.computeIfAbsent(agentSessionId, ignored -> new ConversationState());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ConversationSnapshot(
            List<Map<String, String>> recentMessages,
            Integer lastProductId,
            String lastProductLabel,
            String lastProductQuery,
            Integer lastWarehouseId,
            String lastWarehouseLabel,
            String lastWarehouseQuery,
            List<Map<String, Object>> pendingOptions
    ) {
    }

    public record SelectedOption(
            boolean known,
            boolean supported,
            String optionType,
            String displayLabel,
            Integer productId,
            Integer warehouseId
    ) {
    }

    private static final class ConversationState {
        private final List<Map<String, String>> recentMessages = new ArrayList<>();
        private final List<Map<String, Object>> pendingOptions = new ArrayList<>();
        private Integer lastProductId;
        private String lastProductLabel;
        private String lastProductQuery;
        private Integer lastWarehouseId;
        private String lastWarehouseLabel;
        private String lastWarehouseQuery;

        synchronized void addMessage(String role, String message) {
            if (message == null || message.isBlank()) {
                return;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("role", role);
            item.put("content", message);
            recentMessages.add(item);
            while (recentMessages.size() > MAX_MESSAGES) {
                recentMessages.remove(0);
            }
        }

        synchronized void rememberProduct(Integer productId, String displayLabel, String query) {
            this.lastProductId = productId;
            this.lastProductLabel = firstNonBlank(displayLabel, query);
            this.lastProductQuery = firstNonBlank(query, displayLabel);
        }

        synchronized void rememberWarehouse(Integer warehouseId, String displayLabel, String query) {
            this.lastWarehouseId = warehouseId;
            this.lastWarehouseLabel = firstNonBlank(displayLabel, query);
            this.lastWarehouseQuery = firstNonBlank(query, displayLabel);
        }

        synchronized void rememberOptions(List<AgentChoiceOptionVO> options) {
            pendingOptions.clear();
            for (AgentChoiceOptionVO option : options) {
                if (pendingOptions.size() >= MAX_OPTIONS) {
                    break;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("optionType", option.getOptionType());
                item.put("displayLabel", option.getDisplayLabel());
                item.put("productId", option.getProductId());
                item.put("warehouseId", option.getWarehouseId());
                item.put("supported", option.getSupported());
                pendingOptions.add(item);
            }
        }

        synchronized SelectedOption resolveSelectedOption(Map<String, Object> selectedOption) {
            String optionType = stringValue(selectedOption.get("optionType"));
            Integer productId = positiveInt(selectedOption.get("productId"));
            Integer warehouseId = positiveInt(selectedOption.get("warehouseId"));
            String displayLabel = firstNonBlank(stringValue(selectedOption.get("rawDisplayLabel")), stringValue(selectedOption.get("displayLabel")));
            for (Map<String, Object> option : pendingOptions) {
                if (!matchesOption(option, optionType, productId, warehouseId, displayLabel)) {
                    continue;
                }
                Boolean supported = booleanValue(option.get("supported"));
                return new SelectedOption(
                        true,
                        supported == null || supported,
                        stringValue(option.get("optionType")),
                        safeDisplayLabel(firstNonBlank(stringValue(selectedOption.get("displayLabel")), stringValue(option.get("displayLabel")))),
                        positiveInt(option.get("productId")),
                        positiveInt(option.get("warehouseId"))
                );
            }
            return new SelectedOption(false, false, optionType, safeDisplayLabel(displayLabel), productId, warehouseId);
        }
        synchronized ConversationSnapshot snapshot() {
            return new ConversationSnapshot(
                    copyMessages(recentMessages),
                    lastProductId,
                    lastProductLabel,
                    lastProductQuery,
                    lastWarehouseId,
                    lastWarehouseLabel,
                    lastWarehouseQuery,
                    copyOptions(pendingOptions)
            );
        }

        private boolean matchesOption(Map<String, Object> option, String optionType, Integer productId, Integer warehouseId, String displayLabel) {
            String storedType = stringValue(option.get("optionType"));
            if (optionType != null && storedType != null && !optionType.equals(storedType)) {
                return false;
            }
            Integer storedProductId = positiveInt(option.get("productId"));
            if (productId != null) {
                return productId.equals(storedProductId);
            }
            Integer storedWarehouseId = positiveInt(option.get("warehouseId"));
            if (warehouseId != null) {
                return warehouseId.equals(storedWarehouseId);
            }
            String storedLabel = stringValue(option.get("displayLabel"));
            return displayLabel != null && displayLabel.equals(storedLabel);
        }

        private String safeDisplayLabel(String value) {
            if (value == null) {
                return null;
            }
            return value.replaceAll("\\s*[(（]#\\d+[)）]\\s*", " ").replaceAll("\\s+", " ").trim();
        }
        private String stringValue(Object value) {
            return value instanceof String text && !text.isBlank() ? text : null;
        }

        private Integer positiveInt(Object value) {
            if (value instanceof Number number && number.intValue() > 0) {
                return number.intValue();
            }
            if (value instanceof String text) {
                try {
                    int parsed = Integer.parseInt(text);
                    return parsed > 0 ? parsed : null;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }

        private Boolean booleanValue(Object value) {
            return value instanceof Boolean bool ? bool : null;
        }
        private List<Map<String, String>> copyMessages(List<Map<String, String>> source) {
            List<Map<String, String>> copy = new ArrayList<>();
            for (Map<String, String> item : source) {
                copy.add(new LinkedHashMap<>(item));
            }
            return copy;
        }

        private List<Map<String, Object>> copyOptions(List<Map<String, Object>> source) {
            List<Map<String, Object>> copy = new ArrayList<>();
            for (Map<String, Object> item : source) {
                copy.add(new LinkedHashMap<>(item));
            }
            return copy;
        }

        private String firstNonBlank(String first, String second) {
            if (first != null && !first.isBlank()) {
                return first;
            }
            return second == null || second.isBlank() ? null : second;
        }
    }
}



