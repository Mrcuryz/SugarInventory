package com.Laibin.SugarInventory.agent.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class AgentMessageResponseVO {
    private String answer;
    private boolean needsUserSelection;
    private List<AgentChoiceOptionVO> options = new ArrayList<>();
    private List<AgentBusinessCardVO> cards = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private List<AgentToolCallSummaryVO> toolCalls = new ArrayList<>();
    private Map<String, Object> debug;
    private AgentSessionVO session;
}