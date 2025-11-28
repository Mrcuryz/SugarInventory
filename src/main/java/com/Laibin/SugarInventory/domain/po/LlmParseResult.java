package com.Laibin.SugarInventory.domain.po;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class LlmParseResult {
    private List<ParsedInboundItem> items;
    @JsonProperty("globalRemarks")
    private List<String> globalRemarks; // 全局备注，如班组、人员、计时信息
}
