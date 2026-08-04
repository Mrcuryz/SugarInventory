package com.Laibin.SugarInventory.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    public enum AmbiguityType {
        PRODUCT_SCOPE,
        MULTIPLE_SPECS
    }

    public enum OptionType {
        PRODUCT_TYPE_GROUP,
        EXACT_PRODUCT_NAME_GROUP,
        SINGLE_PRODUCT
    }

    public enum AssayLookupMode {
        ASSAY_ID,
        PRODUCT_DATE,
        PRODUCT_QUERY
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
    public record ProductScope(
            @JsonProperty(value = "type", required = true) String type,
            @Min(1) Integer productId,
            @Size(min = 1, max = 100) String productName,
            @Size(min = 1, max = 50) String productType
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record WarehouseScope(
            @JsonProperty(value = "type", required = true) String type,
            @Min(1) Integer warehouseId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record InventoryDistributionFilter(
            List<String> productStatuses,
            List<String> warehouseStatuses,
            List<String> palletStatuses,
            String assayStatus,
            LocalDate entryDateFrom,
            LocalDate entryDateTo
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record InventoryDistributionRequest(
            @JsonProperty(value = "productScope", required = true) ProductScope productScope,
            @JsonProperty(value = "warehouseScope", required = true) WarehouseScope warehouseScope,
            InventoryDistributionFilter statusFilter,
            @JsonProperty(value = "groupBy", required = true) String groupBy,
            @Min(1) @Max(100) Integer limit
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record InventoryQualityMetricCondition(
            @JsonProperty(value = "metricCode", required = true) String metricCode,
            @JsonProperty(value = "operator", required = true) String operator,
            BigDecimal value,
            BigDecimal minValue,
            BigDecimal maxValue
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record InventoryQualityRequest(
            @JsonProperty(value = "productScope", required = true) ProductScope productScope,
            @JsonProperty(value = "warehouseScope", required = true) WarehouseScope warehouseScope,
            @JsonProperty(value = "mode", required = true) String mode,
            String judgeStatus,
            @Size(min = 1, max = 64) String standardCode,
            @Min(1) Integer standardVersion,
            InventoryQualityMetricCondition metricCondition,
            @Min(1) @Max(100) Integer limit
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record DateRange(
            @JsonProperty(value = "type", required = true) String type,
            LocalDate date,
            @Min(1) @Max(366) Integer days,
            LocalDate from,
            LocalDate to
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AssayRecordsRequest(
            @JsonProperty(value = "productScope", required = true) ProductScope productScope,
            DateRange dateRange,
            String judgeStatus,
            String sortBy,
            String sortDirection,
            @Min(1) Integer page,
            @Min(1) @Max(100) Integer size
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AssayReportDetailRequest(
            @JsonProperty(value = "reportRef", required = true)
            @NotBlank
            @Size(min = 1, max = 200)
            String reportRef,
            Boolean includeMetrics,
            Boolean includeStandardSnapshot
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AssayAbnormalitiesRequest(
            @JsonProperty(value = "productScope", required = true) ProductScope productScope,
            DateRange dateRange,
            List<String> abnormalTypes,
            String groupBy,
            @Min(1) @Max(100) Integer limit
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ProductsWithoutRecentAssayRequest(
            @JsonProperty(value = "productScope", required = true) ProductScope productScope,
            @JsonProperty(value = "warehouseScope", required = true) WarehouseScope warehouseScope,
            String population,
            DateRange dateRange,
            String groupBy,
            @Min(1) @Max(100) Integer limit
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AssayStandardCoverageRequest(
            @JsonProperty(value = "productScope", required = true) ProductScope productScope,
            DateRange dateRange,
            String coverageType,
            @Min(1) @Max(100) Integer limit
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

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PalletStatusRequest(
            @JsonProperty(value = "code", required = true)
            @JsonPropertyDescription("Pallet code. Length 1..100.")
            @NotBlank
            @Size(min = 1, max = 100)
            String code,
            @JsonPropertyDescription("Include current inventory position. Defaults to true.")
            Boolean includeInventory,
            @JsonPropertyDescription("Include resolved assay information. Defaults to true.")
            Boolean includeAssay,
            @JsonPropertyDescription("Include flow cycles and flow details. Defaults to true.")
            Boolean includeFlows,
            @JsonPropertyDescription("Optional flow cycle number. Minimum 1.")
            @Min(1)
            Integer cycleNo,
            @JsonPropertyDescription("Maximum flow cycles/details to return. Range 1..100. Defaults to 20.")
            @Min(1)
            @Max(100)
            Integer flowLimit
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PalletLifecycleRequest(
            @JsonProperty(value = "code", required = true)
            @NotBlank @Size(min = 1, max = 100) String code,
            Boolean includeInventory,
            Boolean includeAssay,
            Boolean includeFlows,
            Boolean includePrintInfo,
            @Min(1) @Max(100) Integer flowLimit
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PalletFlowRecordsRequest(
            String code,
            ProductScope productScope,
            @Min(1) Integer warehouseId,
            DateRange dateRange,
            List<String> eventTypes,
            @Min(1) Integer page,
            @Min(1) @Max(100) Integer size
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PrintedNotInboundCodesRequest(
            ProductScope productScope,
            @Size(min = 1, max = 50) String orderNo,
            @Size(min = 1, max = 80) String batchNo,
            DateRange dateRange,
            String groupBy,
            @Min(1) @Max(100) Integer limit
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PalletAnomaliesRequest(
            ProductScope productScope,
            @Min(1) Integer warehouseId,
            DateRange dateRange,
            List<String> anomalyTypes,
            @Min(1) @Max(100) Integer limit
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record QrBatchInboundCompletionRequest(
            String batchNo,
            String orderNo,
            @Min(1) Integer productId,
            DateRange dateRange,
            Boolean includeUnfinishedExamples,
            @Min(1) @Max(100) Integer limit
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AssayStatusRequest(
            @JsonPropertyDescription("Preferred assay id. When supplied, product fields are ignored.")
            @Min(1)
            Integer assayId,
            @JsonPropertyDescription("Product id used with productionDate.")
            @Min(1)
            Integer productId,
            @JsonPropertyDescription("Production date in ISO format yyyy-MM-dd. Required with productId or productQuery.")
            @Size(min = 10, max = 10)
            String productionDate,
            @JsonPropertyDescription("Product query used only when productId is absent. Length 1..100.")
            @Size(min = 1, max = 100)
            String productQuery,
            @JsonPropertyDescription("Include applied standard and failed metric details. Defaults to true.")
            Boolean includeStandardDetails
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
            Integer matchScore,
            String matchReason
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
            Integer matchScore,
            String matchReason
    ) {
    }

    public record ResolutionOption(
            OptionType optionType,
            String displayLabel,
            Integer productId,
            String productName,
            String productType,
            boolean supported,
            String matchReason
    ) {
    }

    public record ProductResolutionResponse(
            ResolutionStatus resolutionStatus,
            boolean needsUserSelection,
            AmbiguityType ambiguityType,
            String clarificationPrompt,
            List<ResolutionOption> options,
            List<ProductCandidate> candidates,
            ToolError error
    ) {
        public static ProductResolutionResponse error(ToolError error) {
            return new ProductResolutionResponse(ResolutionStatus.NOT_FOUND, false, null, null, List.of(), List.of(), error);
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
            int rawFullPallets,
            int rawLoosePieces,
            Integer normalizedPallets,
            Integer normalizedLoosePieces,
            Integer totalEquivalentPieces,
            BigDecimal totalWeight,
            String displayStockInfo,
            String calculationNote,
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
            Integer rawFullPallets,
            Integer rawLoosePieces,
            Integer normalizedPallets,
            Integer normalizedLoosePieces,
            Integer totalEquivalentPieces,
            BigDecimal totalWeight,
            String displayStockInfo,
            String calculationNote,
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

    public record InventoryDistributionGroup(
            String groupLabel,
            String warehouseLabel,
            String canonicalProductName,
            String productLabel,
            long rawFullPallets,
            long rawLoosePieces,
            Long normalizedPallets,
            Long normalizedLoosePieces,
            long totalEquivalentPieces,
            String stockText,
            String totalWeightText,
            long palletCount,
            long warehouseCount,
            long productCount,
            String percentageText,
            LocalDate latestInboundTime,
            List<String> riskLabels,
            String calculationNote
    ) {
    }

    public record InventoryDistributionResponse(
            String scopeLabel,
            String productLabel,
            String groupBy,
            long rawFullPallets,
            long rawLoosePieces,
            Long normalizedPallets,
            Long normalizedLoosePieces,
            long totalEquivalentPieces,
            String totalStockText,
            String totalWeightText,
            long warehouseCount,
            long productCount,
            long palletCount,
            String calculationNote,
            List<InventoryDistributionGroup> groups,
            List<String> notes,
            ToolError error
    ) {
        public static InventoryDistributionResponse error(ToolError error) {
            return new InventoryDistributionResponse(null, null, null, 0, 0, null, null, 0, null, null,
                    0, 0, 0, null, List.of(), List.of(), error);
        }
    }

    public record InventoryQualityRecord(
            String productLabel,
            LocalDate productionDate,
            String warehouseLabel,
            String stockText,
            String totalWeightText,
            long palletCount,
            String judgeStatus,
            String judgeLabel,
            String standardLabel,
            String failedMetricText,
            String metricLabel,
            String metricValueText,
            String reportRef
    ) {
    }

    public record InventoryQualityResponse(
            String queryType,
            String queryLabel,
            long totalGroups,
            long totalEquivalentPieces,
            String totalWeightText,
            boolean truncated,
            List<InventoryQualityRecord> records,
            List<String> notes,
            ToolError error
    ) {
        public static InventoryQualityResponse error(ToolError error) {
            return new InventoryQualityResponse(null, null, 0, 0, null, false, List.of(), List.of(), error);
        }
    }

    public record AssayRecord(
            String recordRef,
            String recordLabel,
            String productLabel,
            LocalDate sampleDate,
            LocalDateTime createdAt,
            String judgeStatus,
            String judgeLabel,
            String failedMetricText,
            Integer failedMetricCount,
            String standardLabel,
            String testerLabel,
            String actionHint
    ) {
    }

    public record AssayRecordsResponse(
            String scopeLabel,
            String dateRangeLabel,
            long total,
            long passCount,
            long failedCount,
            long noStandardCount,
            long multipleCandidatesCount,
            LocalDate latestSampleDate,
            String summaryText,
            int page,
            int size,
            List<AssayRecord> records,
            List<String> notes,
            ToolError error
    ) {
        public static AssayRecordsResponse error(ToolError error) {
            return new AssayRecordsResponse(null, null, 0, 0, 0, 0, 0, null, null,
                    1, 20, List.of(), List.of(), error);
        }
    }

    public record AssayReportMetric(
            String metricCode,
            String metricName,
            String actualValueText,
            String standardRangeText,
            String resultLabel,
            String reason
    ) {
    }

    public record AssayReportDetailResponse(
            String reportRef,
            String reportLabel,
            String productLabel,
            LocalDate sampleDate,
            LocalDateTime createdAt,
            String judgeStatus,
            String judgeLabel,
            String judgeMessage,
            String standardLabel,
            List<AssayReportMetric> metrics,
            List<String> matchedStandards,
            List<String> riskLabels,
            List<String> notes,
            String summaryText,
            ToolError error
    ) {
        public static AssayReportDetailResponse error(ToolError error) {
            return new AssayReportDetailResponse(null, null, null, null, null, null, null, null,
                    null, List.of(), List.of(), List.of(), List.of(), null, error);
        }
    }

    public record AssayAbnormalityGroup(
            String groupLabel,
            long total,
            long failedCount,
            long noStandardCount,
            long multipleCandidatesCount,
            LocalDate latestSampleDate,
            List<String> riskLabels
    ) {
    }

    public record AssayAbnormalitiesResponse(
            String scopeLabel,
            String dateRangeLabel,
            String groupBy,
            long total,
            long failedCount,
            long noStandardCount,
            long multipleCandidatesCount,
            LocalDate latestSampleDate,
            String summaryText,
            List<AssayAbnormalityGroup> groups,
            List<String> riskLabels,
            List<String> notes,
            ToolError error
    ) {
        public static AssayAbnormalitiesResponse error(ToolError error) {
            return new AssayAbnormalitiesResponse(null, null, null, 0, 0, 0, 0, null, null,
                    List.of(), List.of(), List.of(), error);
        }
    }

    public record ProductWithoutRecentAssayGroup(
            String groupLabel,
            String productLabel,
            String warehouseLabel,
            long rawFullPallets,
            long rawLoosePieces,
            Long normalizedPallets,
            Long normalizedLoosePieces,
            long totalEquivalentPieces,
            String stockText,
            String totalWeightText,
            long inventoryRecordCount,
            long palletCount,
            long warehouseCount,
            long productCount,
            LocalDate latestInboundTime,
            List<String> warehouseLabels,
            List<String> riskLabels,
            String nextActionLabel,
            String calculationNote
    ) {
    }

    public record ProductsWithoutRecentAssayResponse(
            String scopeLabel,
            String warehouseScopeLabel,
            String dateRangeLabel,
            String population,
            String groupBy,
            long totalGroups,
            long rawFullPallets,
            long rawLoosePieces,
            Long normalizedPallets,
            Long normalizedLoosePieces,
            long totalEquivalentPieces,
            String totalStockText,
            String totalWeightText,
            long inventoryRecordCount,
            long palletCount,
            long warehouseCount,
            long productCount,
            String summaryText,
            List<ProductWithoutRecentAssayGroup> groups,
            List<String> riskLabels,
            List<String> notes,
            ToolError error
    ) {
        public static ProductsWithoutRecentAssayResponse error(ToolError error) {
            return new ProductsWithoutRecentAssayResponse(null, null, null, null, null,
                    0, 0, 0, null, null, 0, null, null, 0, 0, 0, 0,
                    null, List.of(), List.of(), List.of(), error);
        }
    }

    public record AssayStandardCoverageGroup(
            String groupLabel,
            String productLabel,
            String coverageLabel,
            String affectedStockText,
            long rawFullPallets,
            long rawLoosePieces,
            Long normalizedPallets,
            Long normalizedLoosePieces,
            long totalEquivalentPieces,
            String totalWeightText,
            long inventoryRecordCount,
            long palletCount,
            long warehouseCount,
            LocalDate latestInboundTime,
            List<String> warehouseLabels,
            List<String> riskLabels
    ) {
    }

    public record AssayStandardCoverageResponse(
            String scopeLabel,
            String dateRangeLabel,
            String coverageType,
            long totalGroups,
            long rawFullPallets,
            long rawLoosePieces,
            Long normalizedPallets,
            Long normalizedLoosePieces,
            long totalEquivalentPieces,
            String totalStockText,
            String totalWeightText,
            long inventoryRecordCount,
            long palletCount,
            long warehouseCount,
            long productCount,
            String summaryText,
            List<AssayStandardCoverageGroup> groups,
            List<String> riskLabels,
            List<String> notes,
            ToolError error
    ) {
        public static AssayStandardCoverageResponse error(ToolError error) {
            return new AssayStandardCoverageResponse(null, null, null,
                    0, 0, 0, null, null, 0, null, null, 0, 0, 0, 0,
                    null, List.of(), List.of(), List.of(), error);
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

    public record PalletStatusResponse(
            String code,
            boolean partial,
            JsonNode palletInfo,
            JsonNode inventory,
            JsonNode assay,
            PageInfo flowCyclePage,
            List<JsonNode> flowCycles,
            List<JsonNode> flows,
            List<String> warnings,
            ToolError error
    ) {
        public static PalletStatusResponse error(ToolError error) {
            return new PalletStatusResponse(null, false, null, null, null, null, List.of(), List.of(), List.of(), error);
        }
    }

    public record PalletLifecycleEvent(
            LocalDateTime time,
            String eventType,
            String eventLabel,
            String codeLabel,
            String productLabel,
            String fromWarehouseLabel,
            String toWarehouseLabel,
            String operatorLabel,
            Integer cycleNo
    ) {
    }

    public record PalletPrintInfo(
            String batchLabel,
            String orderLabel,
            String printStatusLabel,
            String labelStatusLabel,
            LocalDateTime printedAt,
            LocalDateTime usedAt,
            LocalDateTime recycledAt
    ) {
    }

    public record PalletLifecycleResponse(
            String codeLabel,
            String currentStatusLabel,
            String productLabel,
            String warehouseLabel,
            String quantityText,
            LocalDate productionDate,
            String assaySummary,
            PalletPrintInfo printInfo,
            List<PalletLifecycleEvent> timeline,
            List<String> riskLabels,
            List<String> notes,
            ToolError error
    ) {
        public static PalletLifecycleResponse error(ToolError error) {
            return new PalletLifecycleResponse(null, null, null, null, null, null, null, null, List.of(), List.of(), List.of(), error);
        }
    }

    public record PalletFlowRecord(
            LocalDateTime time,
            String eventType,
            String eventLabel,
            String codeLabel,
            String productLabel,
            String fromWarehouseLabel,
            String toWarehouseLabel,
            String operatorLabel,
            Integer cycleNo
    ) {
    }

    public record PalletFlowRecordsResponse(
            String scopeLabel,
            String dateRangeLabel,
            long total,
            String summaryText,
            List<PalletFlowRecord> records,
            List<String> notes,
            ToolError error
    ) {
        public static PalletFlowRecordsResponse error(ToolError error) {
            return new PalletFlowRecordsResponse(null, null, 0, null, List.of(), List.of(), error);
        }
    }

    public record PrintedNotInboundGroup(
            String groupLabel,
            long printedCount,
            long inboundCount,
            long notInboundCount,
            String completionRateText,
            List<String> examples,
            List<String> riskLabels
    ) {
    }

    public record PrintedNotInboundCodesResponse(
            String scopeLabel,
            String dateRangeLabel,
            String groupBy,
            long printedCount,
            long inboundCount,
            long notInboundCount,
            String completionRateText,
            String summaryText,
            List<PrintedNotInboundGroup> groups,
            List<String> notes,
            ToolError error
    ) {
        public static PrintedNotInboundCodesResponse error(ToolError error) {
            return new PrintedNotInboundCodesResponse(null, null, null, 0, 0, 0, null, null, List.of(), List.of(), error);
        }
    }

    public record PalletAnomalyGroup(
            String anomalyType,
            String groupLabel,
            long count,
            List<String> examples,
            List<String> riskLabels
    ) {
    }

    public record PalletAnomaliesResponse(
            String scopeLabel,
            String dateRangeLabel,
            long total,
            String summaryText,
            List<PalletAnomalyGroup> groups,
            List<String> notes,
            ToolError error
    ) {
        public static PalletAnomaliesResponse error(ToolError error) {
            return new PalletAnomaliesResponse(null, null, 0, null, List.of(), List.of(), error);
        }
    }

    public record QrBatchInboundCompletionResponse(
            String batchLabel,
            String orderLabel,
            long printedCount,
            long inboundCount,
            long notInboundCount,
            String completionRateText,
            List<String> unfinishedExamples,
            String summaryText,
            List<String> notes,
            ToolError error
    ) {
        public static QrBatchInboundCompletionResponse error(ToolError error) {
            return new QrBatchInboundCompletionResponse(null, null, 0, 0, 0, null, List.of(), null, List.of(), error);
        }
    }

    public record ProductionEntityResolveRequest(String entityType, String query, Integer limit) {
    }

    public record ProductionEntityCandidate(
            String entityRef,
            String entityType,
            String displayCode,
            String status,
            LocalDate businessDate,
            String summary
    ) {
    }

    public record ProductionEntityResolutionResponse(
            String resolutionStatus,
            boolean needsUserSelection,
            String entityType,
            String query,
            List<ProductionEntityCandidate> candidates,
            List<String> limitations,
            ToolError error
    ) {
        public static ProductionEntityResolutionResponse error(ToolError error) {
            return new ProductionEntityResolutionResponse("NO_MATCH", false, null, null, List.of(), List.of(), error);
        }
    }

    public record RegisteredReportRunRequest(
            String reportDefinitionId,
            Integer reportVersion,
            String startDate,
            String endDate,
            String productQuery,
            String metricKey,
            String taskType,
            String comparisonMode,
            String comparisonStartDate,
            String comparisonEndDate
    ) {
    }

    public record RegisteredReportRunResponse(
            String dataScope,
            String reportRunId,
            String reportDefinitionId,
            Integer reportVersion,
            String reportName,
            String metricDefinitionVersion,
            String startDate,
            String endDate,
            String dateRangeLabel,
            String dataAsOf,
            String latestRecordAt,
            JsonNode filtersApplied,
            JsonNode metrics,
            List<JsonNode> dailySeries,
            List<JsonNode> productBreakdowns,
            JsonNode qualityMetrics,
            String seriesGranularity,
            List<JsonNode> qualitySeries,
            List<JsonNode> qualityProductBreakdowns,
            List<JsonNode> standardBreakdowns,
            JsonNode metricTrendSummary,
            List<JsonNode> metricSeries,
            List<JsonNode> metricProductBreakdowns,
            List<JsonNode> metricStandardBreakdowns,
            JsonNode productionFlowMetrics,
            List<JsonNode> productionFlowDailySeries,
            List<JsonNode> productionFlowOrderBreakdowns,
            JsonNode palletTaskCycleMetrics,
            List<JsonNode> palletTaskCycleDailySeries,
            List<JsonNode> palletTaskCycleTypeBreakdowns,
            List<JsonNode> palletTaskPendingItems,
            JsonNode inventoryTrendMetrics,
            List<JsonNode> inventoryTrendDailySeries,
            List<JsonNode> inventoryTrendProductBreakdowns,
            JsonNode operationsOverview,
            JsonNode comparison,
            JsonNode dataQuality,
            List<String> limitations,
            ToolError error
    ) {
        public static RegisteredReportRunResponse error(ToolError error) {
            return new RegisteredReportRunResponse(
                    null, null, null, null, null, null, null, null, null, null,
                    null, null, null,
                    List.of(), List.of(),
                    null, null,
                    List.of(), List.of(), List.of(),
                    null,
                    List.of(), List.of(), List.of(),
                    null,
                    List.of(), List.of(),
                    null,
                    List.of(), List.of(), List.of(),
                    null, List.of(), List.of(), null,
                    null, null,
                    List.of(), error);
        }
    }

    public record ProductionBoilingBatchListRequest(
            String productQuery,
            String startDate,
            String endDate,
            String status,
            Integer limit
    ) {
    }

    public record ProductionBoilingBatchListResponse(
            String dataScope,
            String scopeLabel,
            String dateRangeLabel,
            long total,
            List<ProductionEntityCandidate> candidates,
            List<String> limitations,
            ToolError error
    ) {
        public static ProductionBoilingBatchListResponse error(ToolError error) {
            return new ProductionBoilingBatchListResponse(null, null, null, 0, List.of(), List.of(), error);
        }
    }

    public record ProductionOrderProgressRequest(String orderRef) {
    }

    public record ProductionOrderProgressResponse(
            String dataScope,
            String orderRef,
            String orderNo,
            String orderType,
            String status,
            LocalDate productionDate,
            String teamName,
            String plannedMaterialText,
            String plannedOutputText,
            int materialRecordCount,
            int outputRecordCount,
            int requiredQrCount,
            int boundQrCount,
            int inboundQrCount,
            int labelBatchCount,
            int reservedLabelCount,
            int usedLabelCount,
            int recycledLabelCount,
            List<JsonNode> boilingSources,
            List<JsonNode> outputs,
            LocalDateTime updatedAt,
            LocalDateTime completedAt,
            List<String> limitations,
            ToolError error
    ) {
        public static ProductionOrderProgressResponse error(ToolError error) {
            return new ProductionOrderProgressResponse(null, null, null, null, null, null, null, null, null,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), null, null, List.of(), error);
        }
    }

    public record ProductionBoilingBatchTraceRequest(String batchRef) {
    }

    public record ProductionBoilingBatchTraceResponse(
            String dataScope,
            String batchRef,
            String batchNo,
            LocalDate boilingDate,
            String sugarType,
            String productName,
            String status,
            BigDecimal totalWeightKg,
            BigDecimal reservedWeightKg,
            BigDecimal consumedWeightKg,
            BigDecimal remainingWeightKg,
            int usageCount,
            int nodeCount,
            int edgeCount,
            List<JsonNode> usages,
            List<JsonNode> nodes,
            List<JsonNode> edges,
            List<JsonNode> timeline,
            List<String> limitations,
            ToolError error
    ) {
        public static ProductionBoilingBatchTraceResponse error(ToolError error) {
            return new ProductionBoilingBatchTraceResponse(null, null, null, null, null, null, null,
                    null, null, null, null, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of(), error);
        }
    }

    public record ProductionMaterialPickTraceRequest(String orderRef) {
    }

    public record ProductionMaterialPickTraceResponse(
            String dataScope,
            String orderRef,
            String orderNo,
            String orderStatus,
            int materialRecordCount,
            List<JsonNode> records,
            List<String> limitations,
            ToolError error
    ) {
        public static ProductionMaterialPickTraceResponse error(ToolError error) {
            return new ProductionMaterialPickTraceResponse(null, null, null, null, 0, List.of(), List.of(), error);
        }
    }

    public record ProductionLabelCompletionRequest(String orderRef) {
    }

    public record ProductionLabelCompletionResponse(
            String dataScope,
            String orderRef,
            String orderNo,
            String orderStatus,
            int labelBatchCount,
            int reservedLabelCount,
            int usedLabelCount,
            int recycledLabelCount,
            int requiredQrCount,
            int boundQrCount,
            int inboundQrCount,
            int notBoundQrCount,
            int notInboundQrCount,
            List<JsonNode> batches,
            List<String> limitations,
            ToolError error
    ) {
        public static ProductionLabelCompletionResponse error(ToolError error) {
            return new ProductionLabelCompletionResponse(null, null, null, null,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), error);
        }
    }

    public record ProductionInProcessMaterialsRequest(
            String productName,
            String productType,
            String productionDateStart,
            String productionDateEnd,
            Integer page,
            Integer size
    ) {
    }

    public record ProductionInProcessMaterialsResponse(
            String dataScope,
            long total,
            int page,
            int size,
            List<JsonNode> records,
            List<String> limitations,
            ToolError error
    ) {
        public static ProductionInProcessMaterialsResponse error(ToolError error) {
            return new ProductionInProcessMaterialsResponse(null, 0, 0, 0, List.of(), List.of(), error);
        }
    }

    public record ProductionMaterialCandidatesRequest(String orderRef, Integer page, Integer size) {
    }

    public record ProductionMaterialCandidatesResponse(
            String dataScope, String orderRef, long total, int page, int size,
            List<JsonNode> records, List<String> limitations, ToolError error
    ) {
        public static ProductionMaterialCandidatesResponse error(ToolError error) {
            return new ProductionMaterialCandidatesResponse(null, null, 0, 0, 0, List.of(), List.of(), error);
        }
    }

    public record PalletTasksRequest(
            String code, String taskType, String bizScene, String status,
            String productName, String productType, String productStatus, String targetWarehouseName,
            String productionDateStart, String productionDateEnd, Integer page, Integer size
    ) {
    }

    public record PalletTasksResponse(
            String dataScope, long total, int page, int size,
            List<JsonNode> records, List<String> limitations, ToolError error
    ) {
        public static PalletTasksResponse error(ToolError error) {
            return new PalletTasksResponse(null, 0, 0, 0, List.of(), List.of(), error);
        }
    }

    public record TaskTransitionPreviewRequest(
            Integer previewVersion, String transition, List<String> palletCodes
    ) {
    }

    public record TaskTransitionPreviewResponse(
            String dataScope, int previewVersion, String previewStatus, String previewRef,
            String stateDigest, String previewedAt, String expiresAt,
            String transition, String transitionLabel, boolean canOpenBusinessDialog,
            int requestedTaskCount, int eligibleTaskCount, List<JsonNode> tasks,
            List<String> requiredUserInputs, List<String> blockingIssues,
            List<String> warnings, List<String> limitations, ToolError error
    ) {
        public static TaskTransitionPreviewResponse error(ToolError error) {
            return new TaskTransitionPreviewResponse(
                    null, 0, null, null, null, null, null, null, null,
                    false, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of(), error);
        }
    }

    public record StockDocumentsRequest(
            String documentType, String productName, String warehouseName, String operatorName,
            String startDate, String endDate, Integer page, Integer size
    ) {
    }

    public record StockDocumentsResponse(
            String dataScope, String documentType, long total, int page, int size,
            List<JsonNode> records, List<String> limitations, ToolError error
    ) {
        public static StockDocumentsResponse error(ToolError error) {
            return new StockDocumentsResponse(null, null, 0, 0, 0, List.of(), List.of(), error);
        }
    }

    public record AutoInboundBatchesRequest(String status, Integer limit) {
    }

    public record AutoInboundBatchesResponse(
            String dataScope, int count, List<JsonNode> records, List<String> limitations, ToolError error
    ) {
        public static AutoInboundBatchesResponse error(ToolError error) {
            return new AutoInboundBatchesResponse(null, 0, List.of(), List.of(), error);
        }
    }

    public record AutoInboundBatchDetailRequest(String batchRef) {
    }

    public record AutoInboundBatchDetailResponse(
            String dataScope, String batchRef, int taskCount, List<JsonNode> tasks,
            List<String> globalRemarks, List<String> limitations, ToolError error
    ) {
        public static AutoInboundBatchDetailResponse error(ToolError error) {
            return new AutoInboundBatchDetailResponse(null, null, 0, List.of(), List.of(), List.of(), error);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record WarehouseCapacityDistributionRequest(
            WarehouseScope warehouseScope, String occupancyBand, Boolean onlyAvailable,
            @Min(1) Integer page, @Min(1) @Max(50) Integer size
    ) {
    }

    public record WarehouseCapacityDistributionResponse(
            String dataScope, long total, int page, int size, JsonNode summary,
            List<JsonNode> records, Map<String, String> occupancyBandDefinition,
            List<String> limitations, ToolError error
    ) {
        public static WarehouseCapacityDistributionResponse error(ToolError error) {
            return new WarehouseCapacityDistributionResponse(null, 0, 0, 0, null, List.of(), Map.of(), List.of(), error);
        }
    }

    public record WarehouseRecentOperationsRequest(
            Integer warehouseId, String from, String to, List<String> eventTypes, Integer limit
    ) {
    }

    public record WarehouseRecentOperationsResponse(
            String dataScope, int count, List<JsonNode> records, List<String> limitations, ToolError error
    ) {
        public static WarehouseRecentOperationsResponse error(ToolError error) {
            return new WarehouseRecentOperationsResponse(null, 0, List.of(), List.of(), error);
        }
    }

    public record WarehouseMixedStorageFactsRequest(Integer warehouseId, String factType, Integer limit) {
    }

    public record WarehouseMixedStorageFactsResponse(
            String dataScope, int count, List<JsonNode> records, List<String> limitations, ToolError error
    ) {
        public static WarehouseMixedStorageFactsResponse error(ToolError error) {
            return new WarehouseMixedStorageFactsResponse(null, 0, List.of(), List.of(), error);
        }
    }

    public record ProductCatalogRequest(
            String productName, String productType, String productStatus, String packagingMethod,
            String screenMeshName, Integer page, Integer size
    ) { }
    public record ProductCatalogResponse(
            String dataScope, long total, int page, int size, List<JsonNode> records, List<String> limitations, ToolError error
    ) {
        public static ProductCatalogResponse error(ToolError error) { return new ProductCatalogResponse(null, 0, 0, 0, List.of(), List.of(), error); }
    }
    public record ProductDetailRequest(String productName) { }
    public record ProductDetailResponse(
            String dataScope, String productName, String productType, String productStatus, String packagingMethod,
            BigDecimal weightPerPiece, Integer piecesPerPallet, Boolean canStack, String screenMeshName,
            String conversionSummary, List<String> limitations, ToolError error
    ) {
        public static ProductDetailResponse error(ToolError error) { return new ProductDetailResponse(null, null, null, null, null, null, null, null, null, null, List.of(), error); }
    }
    public record ScreenMeshCatalogRequest(String meshName, Integer page, Integer size) { }
    public record ScreenMeshCatalogResponse(
            String dataScope, long total, int page, int size, List<JsonNode> records, List<String> limitations, ToolError error
    ) {
        public static ScreenMeshCatalogResponse error(ToolError error) { return new ScreenMeshCatalogResponse(null, 0, 0, 0, List.of(), List.of(), error); }
    }
    public record AssayGroupsCatalogResponse(String dataScope, long total, int page, int size, List<JsonNode> records, List<String> limitations, ToolError error) {
        public static AssayGroupsCatalogResponse error(ToolError e) { return new AssayGroupsCatalogResponse(null, 0, 0, 0, List.of(), List.of(), e); }
    }
    public record QualityStandardCatalogResponse(String dataScope, long total, int page, int size, List<JsonNode> records, List<String> limitations, ToolError error) {
        public static QualityStandardCatalogResponse error(ToolError e) { return new QualityStandardCatalogResponse(null, 0, 0, 0, List.of(), List.of(), e); }
    }
    public record QualityStandardDetailResponse(String dataScope, String standardCode, String standardName, String productType,
            String standardLevel, Integer version, String status, String remark, List<JsonNode> metrics,
            List<String> relatedProductNames, List<String> limitations, ToolError error) {
        public static QualityStandardDetailResponse error(ToolError e) { return new QualityStandardDetailResponse(null, null, null, null, null, null, null, null, List.of(), List.of(), List.of(), e); }
    }
    public record ProductStandardRelationsResponse(String dataScope, String productName, int count, List<JsonNode> records, List<String> limitations, ToolError error) {
        public static ProductStandardRelationsResponse error(ToolError e) { return new ProductStandardRelationsResponse(null, null, 0, List.of(), List.of(), e); }
    }
    public record ProductQualityConfigurationResponse(
            String dataScope,
            String productName,
            String productType,
            String productStatus,
            String packagingMethod,
            BigDecimal weightPerPiece,
            Integer piecesPerPallet,
            int standardCount,
            List<JsonNode> standards,
            int assayGroupCount,
            List<JsonNode> assayGroups,
            List<String> limitations,
            ToolError error
    ) {
        public static ProductQualityConfigurationResponse error(ToolError e) {
            return new ProductQualityConfigurationResponse(
                    null, null, null, null, null, null, null,
                    0, List.of(), 0, List.of(), List.of(), e);
        }
    }
    public record EmployeeRosterResponse(String dataScope, long total, int page, int size, List<JsonNode> records, List<String> limitations, ToolError error) {
        public static EmployeeRosterResponse error(ToolError e) { return new EmployeeRosterResponse(null, 0, 0, 0, List.of(), List.of(), e); }
    }
    public record RoleCatalogResponse(String dataScope, long total, int page, int size, List<JsonNode> records, List<String> limitations, ToolError error) {
        public static RoleCatalogResponse error(ToolError e) { return new RoleCatalogResponse(null, 0, 0, 0, List.of(), List.of(), e); }
    }
    public record RolePermissionSummaryResponse(String dataScope, String roleName, String roleCode, String status,
            Integer activeEmployeeCount, Integer permissionCount, List<JsonNode> permissions, List<String> limitations, ToolError error) {
        public static RolePermissionSummaryResponse error(ToolError e) { return new RolePermissionSummaryResponse(null, null, null, null, null, null, List.of(), List.of(), e); }
    }
    public record AuditPageResponse(String dataScope, long total, int page, int size, List<JsonNode> records, List<String> limitations, ToolError error) {
        public static AuditPageResponse error(ToolError e) { return new AuditPageResponse(null, 0, 0, 0, List.of(), List.of(), e); }
    }
    public record InventoryLedgerResponse(String dataScope, long total, int page, int size, String inventoryAsOf, List<JsonNode> records, List<String> limitations, ToolError error) {
        public static InventoryLedgerResponse error(ToolError e) { return new InventoryLedgerResponse(null, 0, 0, 0, null, List.of(), List.of(), e); }
    }
    public record PreparePoolBalanceResponse(String dataScope, long total, int page, int size, String balanceAsOf, List<JsonNode> records, List<String> limitations, ToolError error) {
        public static PreparePoolBalanceResponse error(ToolError e) { return new PreparePoolBalanceResponse(null, 0, 0, 0, null, List.of(), List.of(), e); }
    }
    public record FixedProductQrPoolResponse(String dataScope, long total, int page, int size, String poolAsOf, List<JsonNode> records, List<String> limitations, ToolError error) {
        public static FixedProductQrPoolResponse error(ToolError e) { return new FixedProductQrPoolResponse(null, 0, 0, 0, null, List.of(), List.of(), e); }
    }

    public record AssayStatusResponse(
            AssayLookupMode lookupMode,
            ResolutionStatus resolutionStatus,
            boolean needsUserSelection,
            ProductResolutionResponse productResolution,
            JsonNode assay,
            String judgeResult,
            List<JsonNode> failedMetrics,
            JsonNode appliedStandard,
            boolean needsAssay,
            List<String> warnings,
            ToolError error
    ) {
        public static AssayStatusResponse error(ToolError error) {
            return new AssayStatusResponse(null, ResolutionStatus.NOT_FOUND, false, null, null, null, List.of(), null, false, List.of(), error);
        }
    }
}
