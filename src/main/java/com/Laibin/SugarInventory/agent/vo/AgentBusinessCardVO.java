package com.Laibin.SugarInventory.agent.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class AgentBusinessCardVO {
    private String cardType;
    private String title;
    private String prompt;
    private List<AgentChoiceOptionVO> options = new ArrayList<>();
    private List<Map<String, String>> fields = new ArrayList<>();
}