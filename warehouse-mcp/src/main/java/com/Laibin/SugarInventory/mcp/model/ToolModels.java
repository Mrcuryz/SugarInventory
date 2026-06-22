package com.Laibin.SugarInventory.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ToolModels {
    private ToolModels() {
    }

    public enum ResolutionStatus {
        UNIQUE,
        AMBIGUOUS,
        NOT_FOUND
    }

    public enum MatchType {
        EXACT_ID,
        EXACT_CODE,
        EXACT_NAME,
        NORMALIZED_NAME,
        ALIAS,
        PREFIX,
        CONTAINS
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ResolveProductsRequest(
            @JsonProperty(value = "query", required = true)
            @JsonPropertyDescription("Product id, name, normalized name, or alias fragment. Length 1..100.")
            @NotBlank
            @Size(min = 1, max = 100)
            String query,
            @JsonPropertyDescription("Optional product type filter, for example white or yellow crystal sugar. Length 1..50.")
            @Size(min = 1, max = 50)
            String productType,
            @JsonPropertyDescription("Optional product status filter, for example semi-finished or finished. Length 1..50.")
            @Size(min = 1, max = 50)
            String productStatus,
            @JsonPropertyDescription("Maximum candidates to return. Range 1..100.")
            @Min(1)
            @Max(100)
            Integer limit
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ResolveWarehousesRequest(
            @JsonProperty(value = "query", required = true)
            @JsonPropertyDescription("Warehouse id, name, normalized name, or alias fragment. Length 1..100.")
            @NotBlank
            @Size(min = 1, max = 100)
            String query,
            @JsonPropertyDescription("When true, filter out warehouses without free capacity when capacity fields are present.")
            Boolean onlyAvailable,
            @JsonPropertyDescription("Maximum candidates to return. Range 1..100.")
            @Min(1)
            @Max(100)
            Integer limit
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record InventoryOverviewRequest(
            @JsonPropertyDescription("Preferred unique product id. When supplied, productQuery is ignored.")
            @Min(1)
            Integer productId,
            @JsonPropertyDescription("Product query used only when productId is absent. Length 1..100.")
            @Size(min = 1, max = 100)
            String productQuery,
            @JsonPropertyDescription("Optional product status filter. Length 1..50.")
            @Size(min = 1, max = 50)
            String productStatus,
            @JsonPropertyDescription("Page number. Minimum 1.")
            @Min(1)
            Integer page,
            @JsonPropertyDescription("Page size. Range 1..100.")
            @Min(1)
            @Max(100)
            Integer size
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record WarehouseStatusRequest(
            @JsonPropertyDescription("Preferred unique warehouse id. When supplied, warehouseQuery is ignored.")
            @Min(1)
            Integer warehouseId,
            @JsonPropertyDescription("Warehouse query used only when warehouseId is absent. Length 1..100.")
            @Size(min = 1, max = 100)
            String warehouseQuery,
            @JsonPropertyDescription("Include paged inventory details for this warehouse.")
            Boolean includeInventoryDetails,
            @JsonPropertyDescription("Include recent warehouse operation records.")
            Boolean includeRecentOperations,
            @JsonPropertyDescription("Page number for inventory details. Minimum 1.")
            @Min(1)
            Integer page,
            @JsonPropertyDescription("Page size for inventory details. Range 1..100.")
            @Min(1)
            @Max(100)
            Integer size,
            @JsonPropertyDescription("Recent operation limit. Range 1..100.")
            @Min(1)
            @Max(100)
            Integer recentLimit
    ) {
    }

    public record ToolError(
            String code,
            String message,
            String severity,
            String field,
            boolean retryable,
            List<String> suggestedActions,
            Integer upstreamStatus
    ) {
    }

    public record ProductCandidate(
            Integer productId,
            String productName,
            String productType,
            String productStatus,
            String packagingMethod,
            BigDecimal weightPerPiece,
            Integer piecesPerPallet,
            Boolean canStack,
            Integer screenMeshId,
            MatchType matchType,
            Integer matchScore
    ) {
    }

    public record WarehouseCandidate(
            Integer warehouseId,
            String warehouseName,
            String status,
            Integer maxRows,
            Integer maxCapacity,
            Integer curCapacity,
            Integer freeCapacity,
            MatchType matchType,
            Integer matchScore
    ) {
    }

    public record ProductResolutionResponse(
            ResolutionStatus resolutionStatus,
            boolean needsUserSelection,
            List<ProductCandidate> candidates,
            ToolError error
    ) {
        public static ProductResolutionResponse error(ToolError error) {
            return new ProductResolutionResponse(ResolutionStatus.NOT_FOUND, false, List.of(), error);
        }
    }

    public record WarehouseResolutionResponse(
            ResolutionStatus resolutionStatus,
            boolean needsUserSelection,
            List<WarehouseCandidate> candidates,
            ToolError error
    ) {
        public static WarehouseResolutionResponse error(ToolError error) {
            return new WarehouseResolutionResponse(ResolutionStatus.NOT_FOUND, false, List.of(), error);
        }
    }

    public record PageInfo(
            int page,
            int size,
            long total
    ) {
    }

    public record InventoryOverviewSummary(
            int totalRecords,
            int totalBoards,
            int totalPieces,
            BigDecimal totalWeight,
            int warehouseCount,
            int productCount
    ) {
    }

    public record InventoryOverviewRecord(
            Integer warehouseId,
            String warehouseName,
            Integer productId,
            String productName,
            String productStatus,
            LocalDate entryDate,
            Integer totalQuantity,
            Integer totalPieces,
            BigDecimal totalWeight,
            String stockInfo,
            Integer warehouseCount
    ) {
    }

    public record InventoryOverviewResponse(
            ProductCandidate resolvedProduct,
            PageInfo page,
            InventoryOverviewSummary summary,
            List<InventoryOverviewRecord> records,
            ToolError error
    ) {
        public static InventoryOverviewResponse error(ToolError error) {
            return new InventoryOverviewResponse(null, null, null, List.of(), error);
        }
    }

    public record WarehouseCapacity(
            Integer warehouseId,
            String warehouseName,
            String status,
            BigDecimal curCapacity,
            BigDecimal maxCapacity,
            BigDecimal capacityPercentage,
            Integer maxRows,
            Integer currentPalletCount,
            Integer currentProductCount,
            LocalDate firstEntryDate
    ) {
    }

    public record WarehouseInventoryRecord(
            Integer inventoryId,
            Integer productId,
            String productName,
            String warehouseName,
            LocalDate sampleDate,
            String productType,
            String productStatus,
            String standardNames,
            String meshName,
            String side,
            Integer rowNumber,
            Integer layer,
            Integer quantity,
            Integer pieces,
            String palletCode,
            LocalDateTime createdAt
    ) {
    }

    public record WarehouseRecentOperation(
            LocalDateTime operationTime,
            String operationType,
            String operationName,
            String operatorName,
            String palletCode,
            String productName,
            String fromWarehouseName,
            String toWarehouseName,
            String remark
    ) {
    }

    public record WarehouseStatusResponse(
            ResolutionStatus resolutionStatus,
            boolean needsUserSelection,
            List<WarehouseCandidate> warehouseCandidates,
            WarehouseCandidate warehouse,
            WarehouseCapacity capacity,
            PageInfo inventoryPage,
            List<WarehouseInventoryRecord> inventoryDetails,
            List<WarehouseRecentOperation> recentOperations,
            ToolError error
    ) {
        public static WarehouseStatusResponse error(ToolError error) {
            return new WarehouseStatusResponse(ResolutionStatus.NOT_FOUND, false, List.of(), null, null, null, List.of(), List.of(), error);
        }

        public static WarehouseStatusResponse resolutionFailure(ResolutionStatus status, List<WarehouseCandidate> candidates, ToolError error) {
            return new WarehouseStatusResponse(status, status == ResolutionStatus.AMBIGUOUS, candidates, null, null, null, List.of(), List.of(), error);
        }
    }
}
