package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

public final class QualityCatalogAgentQueries {
    private QualityCatalogAgentQueries() { }
    @Data public static class AssayGroups { private String groupName; private Integer page = 1; private Integer size = 20; }
    @Data public static class Standards { private String productType; private String standardName; private String status; private Integer page = 1; private Integer size = 20; }
    @Data public static class StandardDetail { private String standardCode; private Integer version; }
    @Data public static class ProductRelations { private String productName; }
}
