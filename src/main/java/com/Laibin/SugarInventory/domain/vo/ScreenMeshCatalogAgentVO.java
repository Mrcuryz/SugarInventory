package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;
import java.util.List;

@Value @Builder
public class ScreenMeshCatalogAgentVO {
    String dataScope; long total; int page; int size; List<Row> records; List<String> limitations;
    @Value @Builder
    public static class Row { String meshName; String description; }
}
