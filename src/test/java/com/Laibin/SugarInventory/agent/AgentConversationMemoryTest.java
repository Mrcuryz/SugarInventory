package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.context.AgentConversationMemory;
import com.Laibin.SugarInventory.agent.vo.AgentChoiceOptionVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentConversationMemoryTest {

    @Test
    void resolvesRememberedInternalIdFromSafeDisplayLabel() {
        AgentConversationMemory memory = new AgentConversationMemory();
        AgentChoiceOptionVO option = new AgentChoiceOptionVO();
        option.setOptionType("SINGLE_PRODUCT");
        option.setDisplayLabel("黄冰糖（袋） 25.0kg/件 40件/板");
        option.setProductId(84);
        option.setSupported(true);
        memory.rememberOptions("agt_001", List.of(option));

        AgentConversationMemory.SelectedOption selected = memory.resolveSelectedOption("agt_001", Map.of(
                "optionType", "SINGLE_PRODUCT",
                "displayLabel", "黄冰糖（袋） 25.0kg/件 40件/板",
                "rawDisplayLabel", "黄冰糖（袋） 25.0kg/件 40件/板"));

        assertThat(selected.known()).isTrue();
        assertThat(selected.productId()).isEqualTo(84);
        assertThat(selected.warehouseId()).isNull();
    }
}
