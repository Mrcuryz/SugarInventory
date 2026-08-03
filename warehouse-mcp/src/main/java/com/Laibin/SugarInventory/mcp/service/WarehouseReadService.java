package com.Laibin.SugarInventory.mcp.service;

import com.Laibin.SugarInventory.mcp.client.WarehouseApiClient;
import com.Laibin.SugarInventory.mcp.client.WarehouseApiException;
import com.Laibin.SugarInventory.mcp.model.ToolModels;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AmbiguityType;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayRecordsRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayRecordsResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayReportDetailRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayReportDetailResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayStandardCoverageRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayStandardCoverageResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayAbnormalitiesRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayAbnormalitiesResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayLookupMode;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayStatusRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayStatusResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductsWithoutRecentAssayRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductsWithoutRecentAssayResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewRecord;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewSummary;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryDistributionRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryDistributionResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryQualityRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryQualityResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.MatchType;
import com.Laibin.SugarInventory.mcp.model.ToolModels.OptionType;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PageInfo;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletStatusRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletStatusResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletLifecycleRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletLifecycleResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletFlowRecordsRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletFlowRecordsResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PrintedNotInboundCodesRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PrintedNotInboundCodesResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletAnomaliesRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletAnomaliesResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.QrBatchInboundCompletionRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.QrBatchInboundCompletionResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionEntityResolveRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionEntityResolutionResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionOrderProgressRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionOrderProgressResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.RegisteredReportRunRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.RegisteredReportRunResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionBoilingBatchListRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionBoilingBatchListResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionBoilingBatchTraceRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionBoilingBatchTraceResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionMaterialPickTraceRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionMaterialPickTraceResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionLabelCompletionRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionLabelCompletionResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionInProcessMaterialsRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionInProcessMaterialsResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionMaterialCandidatesRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionMaterialCandidatesResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletTasksRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletTasksResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.StockDocumentsRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.StockDocumentsResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AutoInboundBatchesRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AutoInboundBatchesResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AutoInboundBatchDetailRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AutoInboundBatchDetailResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseCapacityDistributionRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseCapacityDistributionResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseScope;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseRecentOperationsRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseRecentOperationsResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseMixedStorageFactsRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseMixedStorageFactsResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductCatalogRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductCatalogResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductDetailRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductDetailResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ScreenMeshCatalogRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ScreenMeshCatalogResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayGroupsCatalogResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.QualityStandardCatalogResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.QualityStandardDetailResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductStandardRelationsResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductQualityConfigurationResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.EmployeeRosterResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.RoleCatalogResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.RolePermissionSummaryResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AuditPageResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryLedgerResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PreparePoolBalanceResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.FixedProductQrPoolResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductCandidate;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductResolutionResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolveProductsRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolutionOption;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolveWarehousesRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolutionStatus;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseCandidate;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseCapacity;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseInventoryRecord;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseRecentOperation;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseResolutionResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseStatusRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseStatusResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class WarehouseReadService {
    private static final int DEFAULT_LIMIT = 10;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    private final WarehouseApiClient apiClient;

    public WarehouseReadService(WarehouseApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ProductResolutionResponse resolveProducts(ResolveProductsRequest request) {
        ToolModels.ToolError validation = validateResolveProductsRequest(request);
        if (validation != null) {
            return ProductResolutionResponse.error(validation);
        }
        int limit = defaultLimit(request.limit());
        List<ProductCandidate> candidates = new ArrayList<>();
        if (isPositiveInteger(request.query())) {
            JsonNode product = apiClient.getData("/api/products/" + request.query().trim());
            if (isObject(product)) {
                candidates.add(productCandidate(product, MatchType.EXACT_ID, 100));
            }
        }
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("name", request.query().trim());
        query.put("type", blankToNull(request.productType()));
        query.put("status", blankToNull(request.productStatus()));
        JsonNode data = apiClient.getData("/api/products/product", query);
        for (JsonNode item : asArray(data)) {
            ProductCandidate candidate = productCandidate(item, matchType(request.query(), text(item, "productName"), false),
                    matchScore(request.query(), text(item, "productName"), false));
            if (candidates.stream().noneMatch(existing -> sameId(existing.productId(), candidate.productId()))) {
                candidates.add(candidate);
            }
        }
        candidates = candidates.stream()
                .sorted(Comparator.comparing(ProductCandidate::matchScore).reversed()
                        .thenComparing(ProductCandidate::productId, Comparator.nullsLast(Integer::compareTo)))
                .limit(limit)
                .toList();
        return productResolution(request.query(), candidates);
    }

    public WarehouseResolutionResponse resolveWarehouses(ResolveWarehousesRequest request) {
        ToolModels.ToolError validation = validateResolveWarehousesRequest(request);
        if (validation != null) {
            return WarehouseResolutionResponse.error(validation);
        }
        int limit = defaultLimit(request.limit());
        WarehouseNameQuery warehouseQuery = normalizeWarehouseNameQuery(request.query());
        List<WarehouseCandidate> candidates = new ArrayList<>();
        if (!warehouseQuery.normalized() && isPositiveInteger(request.query())) {
            JsonNode warehouse = apiClient.getData("/api/warehouse/" + request.query().trim());
            if (isObject(warehouse)) {
                candidates.add(warehouseCandidate(warehouse, MatchType.EXACT_ID, 100, "用户输入为数字，按库位 ID 精确匹配。"));
            }
        }
        JsonNode data = apiClient.getData("/api/warehouse/query", Map.of("name", warehouseQuery.queryName()));
        for (JsonNode item : asArray(data)) {
            String warehouseName = text(item, "warehouseName");
            MatchType candidateMatchType = warehouseQuery.normalized() && normalize(warehouseQuery.queryName()).equals(normalize(warehouseName))
                    ? MatchType.NORMALIZED_NAME
                    : matchType(warehouseQuery.queryName(), warehouseName, false);
            int candidateMatchScore = matchScore(candidateMatchType);
            String matchReason = warehouseQuery.normalized()
                    ? warehouseQuery.matchReason()
                    : matchReason(candidateMatchType, request.query(), warehouseName);
            WarehouseCandidate candidate = warehouseCandidate(item, candidateMatchType, candidateMatchScore, matchReason);
            if (Boolean.TRUE.equals(request.onlyAvailable()) && !hasFreeCapacity(candidate)) {
                continue;
            }
            if (candidates.stream().noneMatch(existing -> sameId(existing.warehouseId(), candidate.warehouseId()))) {
                candidates.add(candidate);
            }
        }
        candidates = candidates.stream()
                .sorted(Comparator.comparing(WarehouseCandidate::matchScore).reversed()
                        .thenComparing(WarehouseCandidate::warehouseId, Comparator.nullsLast(Integer::compareTo)))
                .limit(limit)
                .toList();
        return warehouseResolution(candidates);
    }

    public InventoryOverviewResponse getInventoryOverview(InventoryOverviewRequest request) {
        ToolModels.ToolError validation = validateInventoryOverviewRequest(request);
        if (validation != null) {
            return InventoryOverviewResponse.error(validation);
        }
        int page = request == null || request.page() == null ? DEFAULT_PAGE : request.page();
        int size = request == null || request.size() == null ? DEFAULT_SIZE : request.size();

        ProductCandidate resolvedProduct = null;
        String productName = null;
        String productStatus = request == null ? null : blankToNull(request.productStatus());
        Integer productId = request == null ? null : request.productId();
        if (productId != null) {
            JsonNode product = apiClient.getData("/api/products/" + productId);
            if (!isObject(product)) {
                return InventoryOverviewResponse.error(ErrorMapper.invalid("productId", "Product id was not found."));
            }
            resolvedProduct = productCandidate(product, MatchType.EXACT_ID, 100);
            productName = resolvedProduct.productName();
            if (productStatus == null) {
                productStatus = resolvedProduct.productStatus();
            }
        } else if (request != null && request.productQuery() != null) {
            ProductResolutionResponse resolution = resolveProducts(new ResolveProductsRequest(request.productQuery(), null, productStatus, 10));
            if (resolution.error() != null) {
                return InventoryOverviewResponse.error(resolution.error());
            }
            if (resolution.resolutionStatus() != ResolutionStatus.UNIQUE) {
                return InventoryOverviewResponse.error(ErrorMapper.invalid("productQuery", "Product query did not resolve to a unique product."));
            }
            resolvedProduct = resolution.candidates().getFirst();
            productId = resolvedProduct.productId();
            productName = resolvedProduct.productName();
            if (productStatus == null) {
                productStatus = resolvedProduct.productStatus();
            }
        }

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("productStatus", productStatus);
        query.put("productName", productName);
        query.put("page", page);
        query.put("size", size);
        JsonNode data = apiClient.getData("/api/inventory/stock/page", query);
        long total = longValue(data, "total", 0L);
        List<InventoryOverviewRecord> records = new ArrayList<>();
        for (JsonNode item : asArray(data.path("records"))) {
            InventoryOverviewRecord record = inventoryRecord(item, resolvedProduct);
            if (productId == null || productId.equals(record.productId())) {
                records.add(record);
            }
        }
        return new InventoryOverviewResponse(
                resolvedProduct,
                new PageInfo(page, size, productId == null ? total : records.size()),
                summarize(records),
                records,
                null
        );
    }

    public InventoryDistributionResponse getInventoryDistribution(InventoryDistributionRequest request) {
        ToolModels.ToolError validation = validateInventoryDistributionRequest(request);
        if (validation != null) {
            return InventoryDistributionResponse.error(validation);
        }
        InventoryDistributionRequest normalized = new InventoryDistributionRequest(
                request.productScope(), request.warehouseScope(), request.statusFilter(), request.groupBy(),
                request.limit() == null ? 20 : request.limit());
        return apiClient.postData("/api/inventory/distribution", normalized, InventoryDistributionResponse.class);
    }

    public AssayRecordsResponse queryAssayRecords(AssayRecordsRequest request) {
        ToolModels.ToolError validation = validateAssayRecordsRequest(request);
        if (validation != null) {
            return AssayRecordsResponse.error(validation);
        }
        AssayRecordsRequest normalized = new AssayRecordsRequest(
                request.productScope(),
                request.dateRange(),
                request.judgeStatus() == null ? "ANY" : request.judgeStatus(),
                request.sortBy() == null ? "sampleDate" : request.sortBy(),
                request.sortDirection() == null ? "DESC" : request.sortDirection(),
                request.page() == null ? 1 : request.page(),
                request.size() == null ? 20 : request.size());
        return apiClient.postData("/api/assay/records/query", normalized, AssayRecordsResponse.class);
    }

    public AssayReportDetailResponse getAssayReportDetail(AssayReportDetailRequest request) {
        ToolModels.ToolError validation = validateAssayReportDetailRequest(request);
        if (validation != null) {
            return AssayReportDetailResponse.error(validation);
        }
        AssayReportDetailRequest normalized = new AssayReportDetailRequest(
                request.reportRef().trim(),
                request.includeMetrics() == null || request.includeMetrics(),
                request.includeStandardSnapshot() == null || request.includeStandardSnapshot());
        return apiClient.postData("/api/assay/report-detail/query", normalized, AssayReportDetailResponse.class);
    }

    public AssayAbnormalitiesResponse queryAssayAbnormalities(AssayAbnormalitiesRequest request) {
        ToolModels.ToolError validation = validateAssayAbnormalitiesRequest(request);
        if (validation != null) {
            return AssayAbnormalitiesResponse.error(validation);
        }
        AssayAbnormalitiesRequest normalized = new AssayAbnormalitiesRequest(
                request.productScope(),
                request.dateRange(),
                request.abnormalTypes() == null || request.abnormalTypes().isEmpty()
                        ? List.of("FAILED", "NO_STANDARD", "MULTIPLE_CANDIDATES")
                        : request.abnormalTypes(),
                request.groupBy() == null ? "product" : request.groupBy(),
                request.limit() == null ? 50 : request.limit());
        return apiClient.postData("/api/assay/abnormalities/query", normalized, AssayAbnormalitiesResponse.class);
    }

    public InventoryQualityResponse queryInventoryQuality(InventoryQualityRequest request) {
        ToolModels.ToolError validation = validateInventoryQualityRequest(request);
        if (validation != null) {
            return InventoryQualityResponse.error(validation);
        }
        InventoryQualityRequest normalized = new InventoryQualityRequest(
                request.productScope(), request.warehouseScope(), request.mode(), request.judgeStatus(),
                trimToNull(request.standardCode()), request.standardVersion(), request.metricCondition(),
                request.limit() == null ? 50 : request.limit());
        return apiClient.postData("/api/inventory/agent-read/quality/query", normalized, InventoryQualityResponse.class);
    }

    public ProductsWithoutRecentAssayResponse queryProductsWithoutRecentAssay(ProductsWithoutRecentAssayRequest request) {
        ToolModels.ToolError validation = validateProductsWithoutRecentAssayRequest(request);
        if (validation != null) {
            return ProductsWithoutRecentAssayResponse.error(validation);
        }
        ProductsWithoutRecentAssayRequest normalized = new ProductsWithoutRecentAssayRequest(
                request.productScope(),
                request.warehouseScope(),
                request.population() == null ? "CURRENT_INVENTORY" : request.population(),
                request.dateRange(),
                request.groupBy() == null ? "product" : request.groupBy(),
                request.limit() == null ? 50 : request.limit());
        return apiClient.postData("/api/assay/products-without-recent-assay/query", normalized,
                ProductsWithoutRecentAssayResponse.class);
    }

    public AssayStandardCoverageResponse queryAssayStandardCoverage(AssayStandardCoverageRequest request) {
        ToolModels.ToolError validation = validateAssayStandardCoverageRequest(request);
        if (validation != null) {
            return AssayStandardCoverageResponse.error(validation);
        }
        AssayStandardCoverageRequest normalized = new AssayStandardCoverageRequest(
                request.productScope(),
                request.dateRange(),
                request.coverageType() == null ? "PRODUCT_WITHOUT_STANDARD" : request.coverageType(),
                request.limit() == null ? 50 : request.limit());
        return apiClient.postData("/api/assay/standard-coverage/query", normalized,
                AssayStandardCoverageResponse.class);
    }

    public PalletLifecycleResponse queryQrCodeLifecycle(PalletLifecycleRequest request) {
        ToolModels.ToolError validation = validatePalletLifecycleRequest(request);
        if (validation != null) {
            return PalletLifecycleResponse.error(validation);
        }
        PalletLifecycleRequest normalized = new PalletLifecycleRequest(
                request.code().trim(),
                request.includeInventory() == null || request.includeInventory(),
                request.includeAssay() == null || request.includeAssay(),
                request.includeFlows() == null || request.includeFlows(),
                request.includePrintInfo() != null && request.includePrintInfo(),
                request.flowLimit() == null ? 50 : request.flowLimit());
        return apiClient.postData("/api/pallet-codes/lifecycle/query", normalized, PalletLifecycleResponse.class);
    }

    public PalletFlowRecordsResponse queryPalletFlowRecords(PalletFlowRecordsRequest request) {
        ToolModels.ToolError validation = validatePalletFlowRecordsRequest(request);
        if (validation != null) {
            return PalletFlowRecordsResponse.error(validation);
        }
        PalletFlowRecordsRequest normalized = new PalletFlowRecordsRequest(
                trimToNull(request.code()),
                request.productScope(),
                request.warehouseId(),
                request.dateRange(),
                request.eventTypes(),
                request.page() == null ? 1 : request.page(),
                request.size() == null ? 20 : request.size());
        return apiClient.postData("/api/pallet-codes/flow-records/query", normalized, PalletFlowRecordsResponse.class);
    }

    public PrintedNotInboundCodesResponse queryPrintedNotInboundCodes(PrintedNotInboundCodesRequest request) {
        ToolModels.ToolError validation = validatePrintedNotInboundCodesRequest(request);
        if (validation != null) {
            return PrintedNotInboundCodesResponse.error(validation);
        }
        PrintedNotInboundCodesRequest normalized = new PrintedNotInboundCodesRequest(
                request.productScope() == null ? new ToolModels.ProductScope("ALL", null, null, null) : request.productScope(),
                trimToNull(request.orderNo()),
                trimToNull(request.batchNo()),
                request.dateRange(),
                request.groupBy() == null ? "batch" : request.groupBy(),
                request.limit() == null ? 50 : request.limit());
        return apiClient.postData("/api/pallet-codes/printed-not-inbound/query", normalized,
                PrintedNotInboundCodesResponse.class);
    }

    public PalletAnomaliesResponse queryPalletAnomalies(PalletAnomaliesRequest request) {
        ToolModels.ToolError validation = validatePalletAnomaliesRequest(request);
        if (validation != null) {
            return PalletAnomaliesResponse.error(validation);
        }
        PalletAnomaliesRequest normalized = new PalletAnomaliesRequest(
                request.productScope() == null ? new ToolModels.ProductScope("ALL", null, null, null) : request.productScope(),
                request.warehouseId(),
                request.dateRange(),
                request.anomalyTypes(),
                request.limit() == null ? 50 : request.limit());
        return apiClient.postData("/api/pallet-codes/anomalies/query", normalized, PalletAnomaliesResponse.class);
    }

    public QrBatchInboundCompletionResponse queryQrBatchInboundCompletion(QrBatchInboundCompletionRequest request) {
        ToolModels.ToolError validation = validateQrBatchInboundCompletionRequest(request);
        if (validation != null) {
            return QrBatchInboundCompletionResponse.error(validation);
        }
        QrBatchInboundCompletionRequest normalized = new QrBatchInboundCompletionRequest(
                trimToNull(request.batchNo()),
                trimToNull(request.orderNo()),
                request.productId(),
                request.dateRange(),
                request.includeUnfinishedExamples() == null || request.includeUnfinishedExamples(),
                request.limit() == null ? 20 : request.limit());
        return apiClient.postData("/api/pallet-codes/batch-inbound-completion/query", normalized,
                QrBatchInboundCompletionResponse.class);
    }

    public ProductionEntityResolutionResponse resolveProductionEntities(ProductionEntityResolveRequest request) {
        if (request == null || request.entityType() == null || request.entityType().isBlank()
                || request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("entityType and query are required");
        }
        int limit = request.limit() == null ? 5 : Math.max(1, Math.min(request.limit(), 10));
        ProductionEntityResolveRequest normalized = new ProductionEntityResolveRequest(
                request.entityType().trim(), request.query().trim(), limit);
        return apiClient.postData("/api/production/agent-read/entities/resolve", normalized,
                ProductionEntityResolutionResponse.class);
    }

    public ProductionOrderProgressResponse queryProductionOrderProgress(ProductionOrderProgressRequest request) {
        if (request == null || request.orderRef() == null || request.orderRef().isBlank()) {
            throw new IllegalArgumentException("orderRef is required");
        }
        return apiClient.postData("/api/production/agent-read/orders/progress/query",
                new ProductionOrderProgressRequest(request.orderRef().trim()), ProductionOrderProgressResponse.class);
    }

    public RegisteredReportRunResponse runRegisteredReport(RegisteredReportRunRequest request) {
        if (request == null
                || request.reportDefinitionId() == null
                || request.reportDefinitionId().isBlank()
                || request.reportVersion() == null
                || request.startDate() == null
                || request.endDate() == null) {
            throw new IllegalArgumentException(
                    "reportDefinitionId, reportVersion, startDate and endDate are required");
        }
        RegisteredReportRunRequest normalized = new RegisteredReportRunRequest(
                request.reportDefinitionId().trim(),
                request.reportVersion(),
                request.startDate().trim(),
                request.endDate().trim(),
                trimToNull(request.productQuery()),
                trimToNull(request.metricKey()),
                trimToNull(request.taskType()),
                trimToNull(request.comparisonMode()),
                trimToNull(request.comparisonStartDate()),
                trimToNull(request.comparisonEndDate()));
        return apiClient.postData(
                "/api/analytics/agent-read/reports/run",
                normalized,
                RegisteredReportRunResponse.class);
    }

    public ProductionBoilingBatchListResponse queryBoilingBatches(ProductionBoilingBatchListRequest request) {
        ProductionBoilingBatchListRequest source = request == null
                ? new ProductionBoilingBatchListRequest(null, null, null, null, 10)
                : request;
        int limit = source.limit() == null ? 10 : Math.max(1, Math.min(source.limit(), 20));
        ProductionBoilingBatchListRequest normalized = new ProductionBoilingBatchListRequest(
                trimToNull(source.productQuery()),
                source.startDate(),
                source.endDate(),
                trimToNull(source.status()),
                limit);
        return apiClient.postData("/api/production/agent-read/boiling-batches/query", normalized,
                ProductionBoilingBatchListResponse.class);
    }

    public ProductionBoilingBatchTraceResponse queryBoilingBatchTrace(ProductionBoilingBatchTraceRequest request) {
        if (request == null || request.batchRef() == null || request.batchRef().isBlank()) {
            throw new IllegalArgumentException("batchRef is required");
        }
        return apiClient.postData("/api/production/agent-read/boiling-batches/trace/query",
                new ProductionBoilingBatchTraceRequest(request.batchRef().trim()),
                ProductionBoilingBatchTraceResponse.class);
    }

    public ProductionMaterialPickTraceResponse queryMaterialPickTrace(ProductionMaterialPickTraceRequest request) {
        if (request == null || request.orderRef() == null || request.orderRef().isBlank()) {
            throw new IllegalArgumentException("orderRef is required");
        }
        return apiClient.postData("/api/production/agent-read/orders/material-pick-trace/query",
                new ProductionMaterialPickTraceRequest(request.orderRef().trim()),
                ProductionMaterialPickTraceResponse.class);
    }

    public ProductionLabelCompletionResponse queryProductionLabelCompletion(ProductionLabelCompletionRequest request) {
        if (request == null || request.orderRef() == null || request.orderRef().isBlank()) {
            throw new IllegalArgumentException("orderRef is required");
        }
        return apiClient.postData("/api/production/agent-read/orders/label-completion/query",
                new ProductionLabelCompletionRequest(request.orderRef().trim()),
                ProductionLabelCompletionResponse.class);
    }

    public ProductionInProcessMaterialsResponse queryInProcessMaterials(ProductionInProcessMaterialsRequest request) {
        ProductionInProcessMaterialsRequest source = request == null
                ? new ProductionInProcessMaterialsRequest(null, null, null, null, 1, 20) : request;
        int page = source.page() == null ? 1 : source.page();
        int size = source.size() == null ? 20 : source.size();
        if (page < 1 || size < 1 || size > 50) {
            throw new IllegalArgumentException("page must be >= 1 and size must be between 1 and 50");
        }
        return apiClient.postData("/api/production/agent-read/materials/in-process/query",
                new ProductionInProcessMaterialsRequest(source.productName(), source.productType(),
                        source.productionDateStart(), source.productionDateEnd(), page, size),
                ProductionInProcessMaterialsResponse.class);
    }

    public ProductionMaterialCandidatesResponse queryMaterialCandidates(ProductionMaterialCandidatesRequest request) {
        if (request == null || request.orderRef() == null || request.orderRef().isBlank()) {
            throw new IllegalArgumentException("orderRef is required");
        }
        int page = request.page() == null ? 1 : request.page();
        int size = request.size() == null ? 20 : request.size();
        if (page < 1 || size < 1 || size > 50) throw new IllegalArgumentException("invalid pagination");
        return apiClient.postData("/api/production/agent-read/orders/material-candidates/query",
                new ProductionMaterialCandidatesRequest(request.orderRef().trim(), page, size),
                ProductionMaterialCandidatesResponse.class);
    }

    public PalletTasksResponse queryPalletTasks(PalletTasksRequest request) {
        PalletTasksRequest source = request == null
                ? new PalletTasksRequest(null, null, null, null, null, null, null, null, null, null, 1, 20)
                : request;
        int page = source.page() == null ? 1 : source.page();
        int size = source.size() == null ? 20 : source.size();
        if (page < 1 || size < 1 || size > 50) throw new IllegalArgumentException("invalid pagination");
        return apiClient.postData("/api/logistics/agent-read/pallet-tasks/query",
                new PalletTasksRequest(source.code(), source.taskType(), source.bizScene(), source.status(),
                        source.productName(), source.productType(), source.productStatus(), source.targetWarehouseName(),
                        source.productionDateStart(), source.productionDateEnd(), page, size), PalletTasksResponse.class);
    }

    public StockDocumentsResponse queryStockDocuments(StockDocumentsRequest request) {
        if (request == null || request.documentType() == null || request.documentType().isBlank()) {
            throw new IllegalArgumentException("documentType is required");
        }
        int page = request.page() == null ? 1 : request.page();
        int size = request.size() == null ? 20 : request.size();
        if (page < 1 || size < 1 || size > 50) throw new IllegalArgumentException("invalid pagination");
        return apiClient.postData("/api/logistics/agent-read/stock-documents/query",
                new StockDocumentsRequest(request.documentType(), request.productName(), request.warehouseName(),
                        request.operatorName(), request.startDate(), request.endDate(), page, size),
                StockDocumentsResponse.class);
    }

    public AutoInboundBatchesResponse queryAutoInboundBatches(AutoInboundBatchesRequest request) {
        AutoInboundBatchesRequest source = request == null ? new AutoInboundBatchesRequest(null, 20) : request;
        int limit = source.limit() == null ? 20 : source.limit();
        if (limit < 1 || limit > 20) throw new IllegalArgumentException("invalid limit");
        return apiClient.postData("/api/logistics/agent-read/auto-inbound/batches/query",
                new AutoInboundBatchesRequest(source.status(), limit), AutoInboundBatchesResponse.class);
    }

    public AutoInboundBatchDetailResponse getAutoInboundBatchDetail(AutoInboundBatchDetailRequest request) {
        if (request == null || request.batchRef() == null || request.batchRef().isBlank()
                || request.batchRef().length() > 100 || !request.batchRef().startsWith("aibr_")) {
            throw new IllegalArgumentException("valid batchRef is required");
        }
        return apiClient.postData("/api/logistics/agent-read/auto-inbound/batches/detail/query",
                request, AutoInboundBatchDetailResponse.class);
    }

    public WarehouseCapacityDistributionResponse queryWarehouseCapacityDistribution(WarehouseCapacityDistributionRequest request) {
        WarehouseCapacityDistributionRequest source = request == null
                ? new WarehouseCapacityDistributionRequest(new WarehouseScope("ALL", null), "ANY", false, 1, 20) : request;
        WarehouseScope scope = source.warehouseScope() == null ? new WarehouseScope("ALL", null) : source.warehouseScope();
        int page = source.page() == null ? 1 : source.page(); int size = source.size() == null ? 20 : source.size();
        if (page < 1 || size < 1 || size > 50) throw new IllegalArgumentException("invalid pagination");
        return apiClient.postData("/api/warehouse/agent-read/capacity-distribution/query",
                new WarehouseCapacityDistributionRequest(scope, source.occupancyBand() == null ? "ANY" : source.occupancyBand(),
                        Boolean.TRUE.equals(source.onlyAvailable()), page, size), WarehouseCapacityDistributionResponse.class);
    }

    public WarehouseRecentOperationsResponse queryWarehouseRecentOperations(WarehouseRecentOperationsRequest request) {
        WarehouseRecentOperationsRequest source = request == null
                ? new WarehouseRecentOperationsRequest(null, null, null, List.of(), 20) : request;
        int limit = source.limit() == null ? 20 : source.limit();
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("invalid limit");
        return apiClient.postData("/api/warehouse/agent-read/recent-operations/query",
                new WarehouseRecentOperationsRequest(source.warehouseId(),
                        normalizeAuditDateTime(source.from()), normalizeAuditDateTime(source.to()),
                        source.eventTypes() == null ? List.of() : source.eventTypes(), limit),
                WarehouseRecentOperationsResponse.class);
    }

    public WarehouseMixedStorageFactsResponse queryWarehouseMixedStorageFacts(WarehouseMixedStorageFactsRequest request) {
        WarehouseMixedStorageFactsRequest source = request == null
                ? new WarehouseMixedStorageFactsRequest(null, "ANY", 20) : request;
        int limit = source.limit() == null ? 20 : source.limit();
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("invalid limit");
        return apiClient.postData("/api/warehouse/agent-read/mixed-storage-facts/query",
                new WarehouseMixedStorageFactsRequest(source.warehouseId(), source.factType() == null ? "ANY" : source.factType(), limit),
                WarehouseMixedStorageFactsResponse.class);
    }

    public ProductCatalogResponse queryProductCatalog(ProductCatalogRequest request) {
        ProductCatalogRequest source = request == null ? new ProductCatalogRequest(null, null, null, null, null, 1, 20) : request;
        int page = source.page() == null ? 1 : source.page(); int size = source.size() == null ? 20 : source.size();
        if (page < 1 || size < 1 || size > 50) throw new IllegalArgumentException("invalid pagination");
        return apiClient.postData("/api/master-data/agent-read/products/query",
                new ProductCatalogRequest(source.productName(), source.productType(), source.productStatus(), source.packagingMethod(), source.screenMeshName(), page, size),
                ProductCatalogResponse.class);
    }
    public ProductDetailResponse getProductDetail(ProductDetailRequest request) {
        if (request == null || request.productName() == null || request.productName().isBlank()) throw new IllegalArgumentException("productName is required");
        return apiClient.postData("/api/master-data/agent-read/products/detail/query", request, ProductDetailResponse.class);
    }
    public ScreenMeshCatalogResponse queryScreenMeshCatalog(ScreenMeshCatalogRequest request) {
        ScreenMeshCatalogRequest source = request == null ? new ScreenMeshCatalogRequest(null, 1, 20) : request;
        int page = source.page() == null ? 1 : source.page(); int size = source.size() == null ? 20 : source.size();
        if (page < 1 || size < 1 || size > 50) throw new IllegalArgumentException("invalid pagination");
        return apiClient.postData("/api/master-data/agent-read/screen-meshes/query", new ScreenMeshCatalogRequest(source.meshName(), page, size), ScreenMeshCatalogResponse.class);
    }
    public AssayGroupsCatalogResponse queryAssayGroups(String groupName, Integer page, Integer size) {
        int p = page == null ? 1 : page, s = size == null ? 20 : size; if (p < 1 || s < 1 || s > 50) throw new IllegalArgumentException("invalid pagination");
        return apiClient.postData("/api/quality/agent-read/assay-groups/query", Map.of("groupName", groupName == null ? "" : groupName, "page", p, "size", s), AssayGroupsCatalogResponse.class);
    }
    public QualityStandardCatalogResponse queryQualityStandardCatalog(String productType, String standardName, String status, Integer page, Integer size) {
        int p = page == null ? 1 : page, s = size == null ? 20 : size; if (p < 1 || s < 1 || s > 50) throw new IllegalArgumentException("invalid pagination");
        Map<String, Object> body = new java.util.HashMap<>(); if (productType != null) body.put("productType", productType); if (standardName != null) body.put("standardName", standardName); if (status != null) body.put("status", status); body.put("page", p); body.put("size", s);
        return apiClient.postData("/api/quality/agent-read/standards/query", body, QualityStandardCatalogResponse.class);
    }
    public QualityStandardDetailResponse getQualityStandardDetail(String standardCode, Integer version) {
        if (standardCode == null || standardCode.isBlank() || version == null || version <= 0) throw new IllegalArgumentException("standardCode and version are required");
        return apiClient.postData("/api/quality/agent-read/standards/detail/query", Map.of("standardCode", standardCode, "version", version), QualityStandardDetailResponse.class);
    }
    public ProductStandardRelationsResponse queryProductStandardRelations(String productName) {
        if (productName == null || productName.isBlank()) throw new IllegalArgumentException("productName is required");
        return apiClient.postData("/api/quality/agent-read/product-standard-relations/query", Map.of("productName", productName), ProductStandardRelationsResponse.class);
    }
    public ProductQualityConfigurationResponse queryProductQualityConfiguration(Integer productId) {
        if (productId == null || productId <= 0) throw new IllegalArgumentException("invalid productId");
        return apiClient.postData(
                "/api/quality/agent-read/product-quality-configuration/query",
                Map.of("productId", productId),
                ProductQualityConfigurationResponse.class);
    }
    public EmployeeRosterResponse queryEmployeeRoster(String employeeId, String name, String department, String position, String status, String roleCode, Integer page, Integer size) {
        int p = page == null ? 1 : page, s = size == null ? 20 : size; if (p < 1 || s < 1 || s > 50) throw new IllegalArgumentException("invalid pagination");
        Map<String, Object> body = new java.util.HashMap<>();
        if (employeeId != null) body.put("employeeId", employeeId); if (name != null) body.put("name", name); if (department != null) body.put("department", department);
        if (position != null) body.put("position", position); if (status != null) body.put("status", status); if (roleCode != null) body.put("roleCode", roleCode);
        body.put("page", p); body.put("size", s);
        return apiClient.postData("/api/administration/agent-read/employees/query", body, EmployeeRosterResponse.class);
    }
    public RoleCatalogResponse queryRoles(String keyword, String status, Integer page, Integer size) {
        int p = page == null ? 1 : page, s = size == null ? 20 : size; if (p < 1 || s < 1 || s > 50) throw new IllegalArgumentException("invalid pagination");
        Map<String, Object> body = new java.util.HashMap<>(); if (keyword != null) body.put("keyword", keyword); if (status != null) body.put("status", status); body.put("page", p); body.put("size", s);
        return apiClient.postData("/api/administration/agent-read/roles/query", body, RoleCatalogResponse.class);
    }
    public RolePermissionSummaryResponse getRolePermissionSummary(String roleCodeOrName) {
        if (roleCodeOrName == null || roleCodeOrName.isBlank()) throw new IllegalArgumentException("roleCodeOrName is required");
        return apiClient.postData("/api/administration/agent-read/roles/permission-summary/query", Map.of("roleCodeOrName", roleCodeOrName), RolePermissionSummaryResponse.class);
    }
    public AuditPageResponse searchOperationLogs(String module, String operationType, String operator, String startTime, String endTime, Integer page, Integer size) {
        return auditPage("/api/audit/agent-read/operation-logs/query", module, operationType, operator, null, null, startTime, endTime, page, size);
    }
    public AuditPageResponse queryAgentToolAudit(String capability, String resultCode, String errorCode, String startTime, String endTime, Integer page, Integer size) {
        return auditPage("/api/audit/agent-read/agent-tool-audit/query", null, null, null, capability, resultCode, startTime, endTime, page, size, errorCode);
    }
    public AuditPageResponse queryAgentAnswerReviews(String reviewStatus, String answerStatus, String failureDomain, String failureCategory, String suggestedFixType, String testCaseStatus, Boolean priorityOnly, Integer page, Integer size) {
        int p = page == null ? 1 : page, s = size == null ? 20 : size; if (p < 1 || s < 1 || s > 50) throw new IllegalArgumentException("invalid pagination");
        Map<String, Object> body = new java.util.HashMap<>(); put(body, "reviewStatus", reviewStatus); put(body, "answerStatus", answerStatus); put(body, "failureDomain", failureDomain);
        put(body, "failureCategory", failureCategory); put(body, "suggestedFixType", suggestedFixType); put(body, "testCaseStatus", testCaseStatus); if (priorityOnly != null) body.put("priorityOnly", priorityOnly);
        body.put("page", p); body.put("size", s); return apiClient.postData("/api/audit/agent-read/agent-answer-reviews/query", body, AuditPageResponse.class);
    }
    private AuditPageResponse auditPage(String path, String module, String operationType, String operator, String capability, String resultCode, String startTime, String endTime, Integer page, Integer size, String... errorCode) {
        int p = page == null ? 1 : page, s = size == null ? 20 : size; if (p < 1 || s < 1 || s > 50) throw new IllegalArgumentException("invalid pagination");
        Map<String, Object> body = new java.util.HashMap<>(); put(body, "module", module); put(body, "operationType", operationType); put(body, "operator", operator);
        put(body, "capability", capability); put(body, "resultCode", resultCode); put(body, "startTime", normalizeAuditDateTime(startTime)); put(body, "endTime", normalizeAuditDateTime(endTime)); if (errorCode.length > 0) put(body, "errorCode", errorCode[0]);
        body.put("page", p); body.put("size", s); return apiClient.postData(path, body, AuditPageResponse.class);
    }
    private String normalizeAuditDateTime(String value) {
        if (value == null || value.isBlank()) return value;
        DateTimeFormatter localSeconds = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        try {
            return OffsetDateTime.parse(value)
                    .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                    .toLocalDateTime()
                    .format(localSeconds);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value).format(localSeconds);
            } catch (DateTimeParseException localIgnored) {
                return value;
            }
        }
    }
    private void put(Map<String, Object> target, String key, String value) { if (value != null && !value.isBlank()) target.put(key, value); }
    public InventoryLedgerResponse queryInventoryLedger(String productName, String warehouseName, String screenMeshName, String productStatus, String entryDateStart, String entryDateEnd, Integer page, Integer size) {
        int p = page == null ? 1 : page, s = size == null ? 20 : size; if (p < 1 || s < 1 || s > 50) throw new IllegalArgumentException("invalid pagination");
        Map<String, Object> body = new java.util.HashMap<>(); put(body, "productName", productName); put(body, "warehouseName", warehouseName); put(body, "screenMeshName", screenMeshName); put(body, "productStatus", productStatus); put(body, "entryDateStart", entryDateStart); put(body, "entryDateEnd", entryDateEnd); body.put("page", p); body.put("size", s);
        return apiClient.postData("/api/inventory/agent-read/ledger/query", body, InventoryLedgerResponse.class);
    }
    public PreparePoolBalanceResponse queryPreparePoolBalance(String productName, String productType, String screenMeshName, String productionDateStart, String productionDateEnd, Boolean positiveOnly, Integer page, Integer size) {
        int p = page == null ? 1 : page, s = size == null ? 20 : size; if (p < 1 || s < 1 || s > 50) throw new IllegalArgumentException("invalid pagination");
        if (Boolean.FALSE.equals(positiveOnly)) throw new IllegalArgumentException("only positiveOnly=true is supported");
        Map<String, Object> body = new java.util.HashMap<>(); put(body, "productName", productName); put(body, "productType", productType); put(body, "screenMeshName", screenMeshName); put(body, "productionDateStart", productionDateStart); put(body, "productionDateEnd", productionDateEnd); body.put("positiveOnly", true); body.put("page", p); body.put("size", s);
        return apiClient.postData("/api/inventory/agent-read/prepare-pool-balance/query", body, PreparePoolBalanceResponse.class);
    }
    public FixedProductQrPoolResponse queryFixedProductQrPool(String productName, List<String> codes, String status, Boolean freeOnly, Integer page, Integer size) {
        int p = page == null ? 1 : page, s = size == null ? 20 : size; if (p < 1 || s < 1 || s > 50) throw new IllegalArgumentException("invalid pagination");
        if (codes != null && codes.size() > 20) throw new IllegalArgumentException("codes exceeds limit");
        Map<String, Object> body = new java.util.HashMap<>(); put(body, "productName", productName); if (codes != null && !codes.isEmpty()) body.put("codes", codes); put(body, "status", status); if (freeOnly != null) body.put("freeOnly", freeOnly); body.put("page", p); body.put("size", s);
        return apiClient.postData("/api/pallet-codes/agent-read/fixed-product-pool/query", body, FixedProductQrPoolResponse.class);
    }

    public WarehouseStatusResponse getWarehouseStatus(WarehouseStatusRequest request) {
        ToolModels.ToolError validation = validateWarehouseStatusRequest(request);
        if (validation != null) {
            return WarehouseStatusResponse.error(validation);
        }
        int page = request.page() == null ? DEFAULT_PAGE : request.page();
        int size = request.size() == null ? DEFAULT_SIZE : request.size();
        int recentLimit = request.recentLimit() == null ? 10 : request.recentLimit();

        WarehouseResolution resolution = resolveWarehouseForStatus(request);
        if (resolution.status() == ResolutionStatus.AMBIGUOUS) {
            return WarehouseStatusResponse.resolutionFailure(ResolutionStatus.AMBIGUOUS, resolution.candidates(),
                    ErrorMapper.invalid("warehouseQuery", "Warehouse query is ambiguous. Select one warehouse candidate."));
        }
        if (resolution.status() == ResolutionStatus.NOT_FOUND) {
            return WarehouseStatusResponse.resolutionFailure(ResolutionStatus.NOT_FOUND, List.of(),
                    ErrorMapper.invalid("warehouseQuery", "Warehouse query did not match any warehouse."));
        }

        WarehouseCandidate warehouse = resolution.candidates().getFirst();
        WarehouseCapacity capacity = findCapacity(warehouse.warehouseId());
        List<WarehouseInventoryRecord> details = List.of();
        PageInfo inventoryPage = null;
        if (Boolean.TRUE.equals(request.includeInventoryDetails())) {
            JsonNode pageData = apiClient.postData("/api/inventory/qualified-inventory/" + warehouse.warehouseId() + "/page",
                    Map.of("page", page, "size", size));
            inventoryPage = new PageInfo(page, size, longValue(pageData, "total", 0L));
            details = new ArrayList<>();
            for (JsonNode item : asArray(pageData.path("records"))) {
                details.add(warehouseInventoryRecord(item));
            }
        }

        List<WarehouseRecentOperation> operations = List.of();
        if (Boolean.TRUE.equals(request.includeRecentOperations())) {
            JsonNode data = apiClient.getData("/api/inventory/warehouses/" + warehouse.warehouseId() + "/recent-operations",
                    Map.of("limit", recentLimit));
            operations = new ArrayList<>();
            for (JsonNode item : asArray(data)) {
                operations.add(recentOperation(item));
            }
        }
        return new WarehouseStatusResponse(ResolutionStatus.UNIQUE, false, List.of(warehouse), warehouse, capacity, inventoryPage, details, operations, null);
    }


    public PalletStatusResponse getPalletStatus(PalletStatusRequest request) {
        ToolModels.ToolError validation = validatePalletStatusRequest(request);
        if (validation != null) {
            return PalletStatusResponse.error(validation);
        }

        String code = request.code().trim();
        boolean includeInventory = request.includeInventory() == null || request.includeInventory();
        boolean includeAssay = request.includeAssay() == null || request.includeAssay();
        boolean includeFlows = request.includeFlows() == null || request.includeFlows();
        int flowLimit = request.flowLimit() == null ? 20 : request.flowLimit();
        String codePath = pathSegment(code);

        JsonNode palletInfo = apiClient.getData("/api/pallet-codes/parse", Map.of("code", code));
        JsonNode inventory = null;
        JsonNode assay = null;
        PageInfo flowCyclePage = null;
        List<JsonNode> flowCycles = new ArrayList<>();
        List<JsonNode> flows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (includeInventory) {
            try {
                inventory = apiClient.getData("/api/pallet-codes/" + codePath + "/inventory");
            } catch (WarehouseApiException e) {
                if (isFatalUpstream(e)) {
                    throw e;
                }
                warnings.add("inventory query failed with " + e.code() + ".");
            }
        }
        if (includeAssay) {
            try {
                assay = apiClient.getData("/api/pallet-codes/" + codePath + "/assay");
            } catch (WarehouseApiException e) {
                if (isFatalUpstream(e)) {
                    throw e;
                }
                warnings.add("assay query failed with " + e.code() + ".");
            }
        }
        if (includeFlows) {
            try {
                JsonNode cyclesPage = apiClient.getData("/api/pallet-codes/" + codePath + "/flows/cycles",
                        Map.of("pageNum", 1, "pageSize", flowLimit));
                for (JsonNode item : asArray(cyclesPage.path("records"))) {
                    flowCycles.add(item);
                }
                flowCyclePage = new PageInfo(1, flowLimit, longValue(cyclesPage, "total", flowCycles.size()));
                Integer selectedCycleNo = request.cycleNo();
                if (selectedCycleNo == null && !flowCycles.isEmpty()) {
                    selectedCycleNo = intValue(flowCycles.getFirst(), "cycleNo", null);
                }
                if (selectedCycleNo != null) {
                    JsonNode flowData = apiClient.getData("/api/pallet-codes/" + codePath + "/flows", Map.of("cycleNo", selectedCycleNo));
                    for (JsonNode item : asArray(flowData)) {
                        if (flows.size() >= flowLimit) {
                            break;
                        }
                        flows.add(item);
                    }
                }
            } catch (WarehouseApiException e) {
                if (isFatalUpstream(e)) {
                    throw e;
                }
                warnings.add("flows query failed with " + e.code() + ".");
            }
        }
        return new PalletStatusResponse(code, !warnings.isEmpty(), palletInfo, inventory, assay, flowCyclePage, flowCycles, flows, warnings, null);
    }

    public AssayStatusResponse getAssayStatus(AssayStatusRequest request) {
        ToolModels.ToolError validation = validateAssayStatusRequest(request);
        if (validation != null) {
            return AssayStatusResponse.error(validation);
        }

        boolean includeStandardDetails = request.includeStandardDetails() == null || request.includeStandardDetails();
        if (request.assayId() != null) {
            JsonNode assay = apiClient.getData("/api/assay/" + request.assayId());
            return assayStatusResponse(AssayLookupMode.ASSAY_ID, null, assay, includeStandardDetails, List.of());
        }

        LocalDate productionDate = LocalDate.parse(request.productionDate());
        Integer productId = request.productId();
        ProductResolutionResponse productResolution = null;
        AssayLookupMode lookupMode = AssayLookupMode.PRODUCT_DATE;
        if (productId == null) {
            productResolution = resolveProducts(new ResolveProductsRequest(request.productQuery(), null, null, 10));
            if (productResolution.error() != null) {
                return AssayStatusResponse.error(productResolution.error());
            }
            if (productResolution.resolutionStatus() != ResolutionStatus.UNIQUE) {
                return new AssayStatusResponse(
                        AssayLookupMode.PRODUCT_QUERY,
                        productResolution.resolutionStatus(),
                        productResolution.needsUserSelection(),
                        productResolution,
                        null,
                        null,
                        List.of(),
                        null,
                        productResolution.resolutionStatus() == ResolutionStatus.NOT_FOUND,
                        productResolution.resolutionStatus() == ResolutionStatus.NOT_FOUND
                                ? List.of("No product matched the productQuery; assay lookup was not attempted.")
                                : List.of("Product query is ambiguous; ask the user to choose a product before assay lookup."),
                        null
                );
            }
            productId = productResolution.candidates().getFirst().productId();
            lookupMode = AssayLookupMode.PRODUCT_QUERY;
        }

        JsonNode assay = apiClient.getData("/api/assay/by-product-date", Map.of("productId", productId, "productionDate", productionDate));
        if (assay == null || assay.isNull() || assay.isMissingNode() || (assay.isObject() && assay.isEmpty())) {
            return new AssayStatusResponse(
                    lookupMode,
                    ResolutionStatus.NOT_FOUND,
                    false,
                    productResolution,
                    null,
                    null,
                    List.of(),
                    null,
                    true,
                    List.of("No assay result exists for the requested product and production date."),
                    null
            );
        }
        return assayStatusResponse(lookupMode, productResolution, assay, includeStandardDetails, List.of());
    }
    private WarehouseResolution resolveWarehouseForStatus(WarehouseStatusRequest request) {
        if (request.warehouseId() != null) {
            JsonNode warehouseNode = apiClient.getData("/api/warehouse/" + request.warehouseId());
            if (!isObject(warehouseNode)) {
                return new WarehouseResolution(ResolutionStatus.NOT_FOUND, List.of());
            }
            return new WarehouseResolution(ResolutionStatus.UNIQUE, List.of(warehouseCandidate(warehouseNode, MatchType.EXACT_ID, 100)));
        }
        WarehouseResolutionResponse resolution = resolveWarehouses(new ResolveWarehousesRequest(request.warehouseQuery(), false, 10));
        return new WarehouseResolution(resolution.resolutionStatus(), resolution.candidates());
    }

    private ProductResolutionResponse productResolution(String query, List<ProductCandidate> candidates) {
        if (candidates.isEmpty()) {
            return new ProductResolutionResponse(ResolutionStatus.NOT_FOUND, false, null, null, List.of(), List.of(), null);
        }
        if (hasSingleCertainProduct(candidates)) {
            return new ProductResolutionResponse(ResolutionStatus.UNIQUE, false, null, null, List.of(), List.of(candidates.getFirst()), null);
        }
        return new ProductResolutionResponse(
                ResolutionStatus.AMBIGUOUS,
                true,
                ambiguityType(query, candidates),
                clarificationPrompt(query),
                productOptions(query, candidates),
                candidates,
                null
        );
    }

    private AmbiguityType ambiguityType(String query, List<ProductCandidate> candidates) {
        String normalizedQuery = normalize(query);
        boolean productTypeScope = candidates.stream()
                .map(ProductCandidate::productType)
                .anyMatch(productType -> normalize(productType).equals(normalizedQuery));
        return productTypeScope ? AmbiguityType.PRODUCT_SCOPE : AmbiguityType.MULTIPLE_SPECS;
    }

    private String clarificationPrompt(String query) {
        String trimmedQuery = query == null ? "" : query.trim();
        return "“" + trimmedQuery + "”可能指产品大类、产品名称组或某个具体规格，请选择查询范围。";
    }

    private List<ResolutionOption> productOptions(String query, List<ProductCandidate> candidates) {
        String normalizedQuery = normalize(query);
        List<ResolutionOption> options = new ArrayList<>();
        candidates.stream()
                .map(ProductCandidate::productType)
                .filter(productType -> productType != null && normalize(productType).equals(normalizedQuery))
                .distinct()
                .forEach(productType -> options.add(new ResolutionOption(
                        OptionType.PRODUCT_TYPE_GROUP,
                        "全部" + productType + "大类",
                        null,
                        null,
                        productType,
                        true,
                        "可用于库存分布分析；库存概览仍需选择具体产品规格。"
                )));
        boolean hasExactProductNameGroup = candidates.stream()
                .map(ProductCandidate::productName)
                .anyMatch(productName -> normalize(productName).equals(normalizedQuery) || alias(productName).equals(normalizedQuery));
        if (hasExactProductNameGroup) {
            options.add(new ResolutionOption(
                    OptionType.EXACT_PRODUCT_NAME_GROUP,
                    "产品名称为“" + query.trim() + "”的全部规格",
                    null,
                    query.trim(),
                    null,
                    true,
                    "可用于库存分布分析；库存概览仍需选择具体产品规格。"
            ));
        }
        candidates.forEach(candidate -> options.add(new ResolutionOption(
                OptionType.SINGLE_PRODUCT,
                singleProductLabel(candidate),
                candidate.productId(),
                candidate.productName(),
                candidate.productType(),
                true,
                "选择该具体 productId 后可继续查询库存概览。"
        )));
        return options;
    }

    private String singleProductLabel(ProductCandidate candidate) {
        StringBuilder label = new StringBuilder();
        label.append(candidate.productName() == null ? "未命名产品" : candidate.productName());
        if (candidate.weightPerPiece() != null) {
            label.append(" ").append(candidate.weightPerPiece()).append("kg/件");
        }
        if (candidate.piecesPerPallet() != null) {
            label.append(" ").append(candidate.piecesPerPallet()).append("件/板");
        }
        return label.toString();
    }
    private WarehouseResolutionResponse warehouseResolution(List<WarehouseCandidate> candidates) {
        if (candidates.isEmpty()) {
            return new WarehouseResolutionResponse(ResolutionStatus.NOT_FOUND, false, List.of(), null);
        }
        if (hasSingleCertainWarehouse(candidates)) {
            return new WarehouseResolutionResponse(ResolutionStatus.UNIQUE, false, List.of(candidates.getFirst()), null);
        }
        return new WarehouseResolutionResponse(ResolutionStatus.AMBIGUOUS, true, candidates, null);
    }

    private boolean hasSingleCertainProduct(List<ProductCandidate> candidates) {
        long highScore = candidates.stream().filter(candidate -> candidate.matchScore() >= 95).count();
        return candidates.size() == 1 || highScore == 1;
    }

    private boolean hasSingleCertainWarehouse(List<WarehouseCandidate> candidates) {
        long highScore = candidates.stream().filter(candidate -> candidate.matchScore() >= 95).count();
        return candidates.size() == 1 || highScore == 1;
    }

    private WarehouseCapacity findCapacity(Integer warehouseId) {
        JsonNode data = apiClient.getData("/api/inventory/warehouses");
        for (JsonNode item : asArray(data)) {
            if (Integer.valueOf(intValue(item, "warehouseId", -1)).equals(warehouseId)) {
                return new WarehouseCapacity(
                        intValue(item, "warehouseId", null),
                        text(item, "warehouseName"),
                        text(item, "status"),
                        decimalValue(item, "curCapacity"),
                        decimalValue(item, "maxCapacity"),
                        decimalValue(item, "capacityPercentage"),
                        intValue(item, "maxRows", null),
                        intValue(item, "currentPalletCount", null),
                        intValue(item, "currentProductCount", null),
                        localDate(item, "firstEntryDate")
                );
            }
        }
        return null;
    }

    private ToolModels.ToolError validateResolveProductsRequest(ResolveProductsRequest request) {
        ToolModels.ToolError error = validateRequiredString("query", request == null ? null : request.query(), 100);
        if (error != null) {
            return error;
        }
        error = validateOptionalString("productType", request.productType(), 50);
        if (error != null) {
            return error;
        }
        error = validateOptionalString("productStatus", request.productStatus(), 50);
        if (error != null) {
            return error;
        }
        return validateLimit(request.limit());
    }

    private ToolModels.ToolError validateResolveWarehousesRequest(ResolveWarehousesRequest request) {
        ToolModels.ToolError error = validateRequiredString("query", request == null ? null : request.query(), 100);
        if (error != null) {
            return error;
        }
        return validateLimit(request.limit());
    }

    private ToolModels.ToolError validateInventoryOverviewRequest(InventoryOverviewRequest request) {
        if (request == null) {
            return null;
        }
        ToolModels.ToolError error = validatePositiveId("productId", request.productId());
        if (error != null) {
            return error;
        }
        error = validateOptionalString("productQuery", request.productQuery(), 100);
        if (error != null) {
            return error;
        }
        error = validateOptionalString("productStatus", request.productStatus(), 50);
        if (error != null) {
            return error;
        }
        return validatePage(request.page() == null ? DEFAULT_PAGE : request.page(), request.size() == null ? DEFAULT_SIZE : request.size());
    }

    private ToolModels.ToolError validateInventoryDistributionRequest(InventoryDistributionRequest request) {
        if (request == null || request.productScope() == null) {
            return ErrorMapper.invalid("productScope", "productScope is required.");
        }
        String productType = request.productScope().type();
        if (productType == null
                || !Set.of("SINGLE_PRODUCT", "EXACT_PRODUCT_NAME_GROUP", "PRODUCT_TYPE_GROUP", "ALL").contains(productType)) {
            return ErrorMapper.invalid("productScope.type", "Unsupported product scope type.");
        }
        ToolModels.ToolError error;
        if ("SINGLE_PRODUCT".equals(productType)) {
            error = validatePositiveId("productScope.productId", request.productScope().productId());
            if (error != null || request.productScope().productId() == null) {
                return error == null ? ErrorMapper.invalid("productScope.productId", "productId is required.") : error;
            }
        } else if ("EXACT_PRODUCT_NAME_GROUP".equals(productType)) {
            error = validateRequiredString("productScope.productName", request.productScope().productName(), 100);
            if (error != null) return error;
        } else if ("PRODUCT_TYPE_GROUP".equals(productType)) {
            error = validateRequiredString("productScope.productType", request.productScope().productType(), 50);
            if (error != null) return error;
        }
        if (request.warehouseScope() == null || request.warehouseScope().type() == null
                || !Set.of("ALL", "SINGLE_WAREHOUSE").contains(request.warehouseScope().type())) {
            return ErrorMapper.invalid("warehouseScope.type", "Unsupported warehouse scope type.");
        }
        if ("SINGLE_WAREHOUSE".equals(request.warehouseScope().type())) {
            error = validatePositiveId("warehouseScope.warehouseId", request.warehouseScope().warehouseId());
            if (error != null || request.warehouseScope().warehouseId() == null) {
                return error == null ? ErrorMapper.invalid("warehouseScope.warehouseId", "warehouseId is required.") : error;
            }
        }
        if (request.groupBy() == null
                || !Set.of("warehouse", "product", "warehouse_product").contains(request.groupBy())) {
            return ErrorMapper.invalid("groupBy", "Unsupported distribution grouping.");
        }
        if (request.statusFilter() != null) {
            if (!allowedValues(request.statusFilter().productStatuses(), Set.of("半成品", "成品"))
                    || !allowedValues(request.statusFilter().warehouseStatuses(), Set.of("正常", "空置", "满仓", "维护", "临期预警"))
                    || !allowedValues(request.statusFilter().palletStatuses(), Set.of("FREE", "PENDING", "INSTOCK", "INVALID", "ORDER_RESERVED"))) {
                return ErrorMapper.invalid("statusFilter", "Status filter contains unsupported values.");
            }
            if (request.statusFilter().assayStatus() != null
                    && !Set.of("HAS_ASSAY", "MISSING_ASSAY", "PASS", "FAIL", "NO_STANDARD", "MULTIPLE_CANDIDATES")
                    .contains(request.statusFilter().assayStatus())) {
                return ErrorMapper.invalid("statusFilter.assayStatus", "Unsupported assay status.");
            }
            if (request.statusFilter().entryDateFrom() != null && request.statusFilter().entryDateTo() != null
                    && request.statusFilter().entryDateFrom().isAfter(request.statusFilter().entryDateTo())) {
                return ErrorMapper.invalid("statusFilter.entryDateFrom", "entryDateFrom must not be after entryDateTo.");
            }
        }
        return validateLimit(request.limit());
    }

    private ToolModels.ToolError validateInventoryQualityRequest(InventoryQualityRequest request) {
        if (request == null || request.productScope() == null || request.warehouseScope() == null) {
            return ErrorMapper.invalid("productScope", "productScope and warehouseScope are required.");
        }
        ToolModels.ToolError error = validateProductScope("productScope", request.productScope());
        if (error != null) return error;
        if (request.warehouseScope().type() == null
                || !Set.of("ALL", "SINGLE_WAREHOUSE").contains(request.warehouseScope().type())) {
            return ErrorMapper.invalid("warehouseScope.type", "Unsupported warehouse scope type.");
        }
        if ("SINGLE_WAREHOUSE".equals(request.warehouseScope().type())) {
            error = validatePositiveId("warehouseScope.warehouseId", request.warehouseScope().warehouseId());
            if (error != null || request.warehouseScope().warehouseId() == null) {
                return error == null ? ErrorMapper.invalid("warehouseScope.warehouseId", "warehouseId is required.") : error;
            }
        }
        if (!Set.of("JUDGE_STATUS", "STANDARD", "METRIC").contains(request.mode())) {
            return ErrorMapper.invalid("mode", "Unsupported inventory quality mode.");
        }
        if ("JUDGE_STATUS".equals(request.mode()) && !"FAIL".equals(request.judgeStatus())) {
            return ErrorMapper.invalid("judgeStatus", "Only explicit unqualified inventory is supported by this tool.");
        }
        if ("STANDARD".equals(request.mode())) {
            error = validateRequiredString("standardCode", request.standardCode(), 64);
            if (error != null) return error;
        }
        if ("METRIC".equals(request.mode())) {
            ToolModels.InventoryQualityMetricCondition condition = request.metricCondition();
            if (condition == null || !Set.of("color_value", "reducing_sugar", "dry_weight_loss",
                    "conductivity_ash", "sucrose", "insoluble_impurity", "ph").contains(condition.metricCode())) {
                return ErrorMapper.invalid("metricCondition.metricCode", "Unsupported assay metric.");
            }
            if (!Set.of("GT", "GTE", "LT", "LTE", "EQ", "BETWEEN").contains(condition.operator())) {
                return ErrorMapper.invalid("metricCondition.operator", "Unsupported metric operator.");
            }
            if ("BETWEEN".equals(condition.operator())) {
                if (condition.minValue() == null || condition.maxValue() == null
                        || condition.minValue().compareTo(condition.maxValue()) > 0) {
                    return ErrorMapper.invalid("metricCondition.minValue", "BETWEEN requires an ordered minValue and maxValue.");
                }
            } else if (condition.value() == null) {
                return ErrorMapper.invalid("metricCondition.value", "Metric comparison value is required.");
            }
        }
        return validateLimit(request.limit() == null ? 50 : request.limit());
    }

    private boolean allowedValues(List<String> values, Set<String> allowed) {
        return values == null || (values.size() <= 10
                && values.stream().allMatch(value -> value != null && allowed.contains(value)));
    }

    private ToolModels.ToolError validateAssayRecordsRequest(AssayRecordsRequest request) {
        if (request == null || request.productScope() == null) {
            return ErrorMapper.invalid("productScope", "productScope is required.");
        }
        ToolModels.ToolError error = validateProductScope("productScope", request.productScope());
        if (error != null) {
            return error;
        }
        if (request.dateRange() != null) {
            error = validateDateRange(request.dateRange());
            if (error != null) {
                return error;
            }
        }
        if (request.judgeStatus() != null
                && !Set.of("ANY", "PASS", "FAILED", "NO_STANDARD", "MULTIPLE_CANDIDATES").contains(request.judgeStatus())) {
            return ErrorMapper.invalid("judgeStatus", "Unsupported assay judge status.");
        }
        if (request.sortBy() != null && !Set.of("sampleDate", "createdAt").contains(request.sortBy())) {
            return ErrorMapper.invalid("sortBy", "Unsupported assay sort field.");
        }
        if (request.sortDirection() != null && !Set.of("ASC", "DESC").contains(request.sortDirection())) {
            return ErrorMapper.invalid("sortDirection", "Unsupported assay sort direction.");
        }
        return validatePage(request.page() == null ? DEFAULT_PAGE : request.page(), request.size() == null ? 20 : request.size());
    }

    private ToolModels.ToolError validateAssayReportDetailRequest(AssayReportDetailRequest request) {
        if (request == null || request.reportRef() == null || request.reportRef().isBlank()
                || request.reportRef().length() > 200) {
            return ErrorMapper.invalid("reportRef", "reportRef is required and must be 1..200 characters.");
        }
        return null;
    }

    private ToolModels.ToolError validateAssayAbnormalitiesRequest(AssayAbnormalitiesRequest request) {
        if (request == null || request.productScope() == null) {
            return ErrorMapper.invalid("productScope", "productScope is required.");
        }
        ToolModels.ToolError error = validateProductScope("productScope", request.productScope());
        if (error != null) {
            return error;
        }
        if (request.dateRange() != null) {
            error = validateDateRange(request.dateRange());
            if (error != null) {
                return error;
            }
        }
        if (request.abnormalTypes() != null
                && (!allowedValues(request.abnormalTypes(), Set.of("FAILED", "NO_STANDARD", "MULTIPLE_CANDIDATES"))
                || request.abnormalTypes().isEmpty())) {
            return ErrorMapper.invalid("abnormalTypes", "Unsupported assay abnormal type.");
        }
        if (request.groupBy() != null && !Set.of("product", "date", "abnormal_type", "metric").contains(request.groupBy())) {
            return ErrorMapper.invalid("groupBy", "Unsupported assay abnormality grouping.");
        }
        return validateLimit(request.limit() == null ? 50 : request.limit());
    }

    private ToolModels.ToolError validateProductsWithoutRecentAssayRequest(ProductsWithoutRecentAssayRequest request) {
        if (request == null || request.productScope() == null) {
            return ErrorMapper.invalid("productScope", "productScope is required.");
        }
        ToolModels.ToolError error = validateProductScope("productScope", request.productScope());
        if (error != null) {
            return error;
        }
        if (request.warehouseScope() == null || request.warehouseScope().type() == null
                || !Set.of("ALL", "SINGLE_WAREHOUSE").contains(request.warehouseScope().type())) {
            return ErrorMapper.invalid("warehouseScope.type", "Unsupported warehouse scope type.");
        }
        if ("SINGLE_WAREHOUSE".equals(request.warehouseScope().type())) {
            error = validatePositiveId("warehouseScope.warehouseId", request.warehouseScope().warehouseId());
            if (error != null || request.warehouseScope().warehouseId() == null) {
                return error == null ? ErrorMapper.invalid("warehouseScope.warehouseId", "warehouseId is required.") : error;
            }
        }
        if (request.population() != null && !"CURRENT_INVENTORY".equals(request.population())) {
            return ErrorMapper.invalid("population", "Unsupported population.");
        }
        if (request.dateRange() != null) {
            error = validateDateRange(request.dateRange());
            if (error != null) {
                return error;
            }
        }
        if (request.groupBy() != null && !Set.of("product", "warehouse", "product_warehouse").contains(request.groupBy())) {
            return ErrorMapper.invalid("groupBy", "Unsupported products without recent assay grouping.");
        }
        return validateLimit(request.limit() == null ? 50 : request.limit());
    }

    private ToolModels.ToolError validateAssayStandardCoverageRequest(AssayStandardCoverageRequest request) {
        if (request == null || request.productScope() == null) {
            return ErrorMapper.invalid("productScope", "productScope is required.");
        }
        ToolModels.ToolError error = validateProductScope("productScope", request.productScope());
        if (error != null) {
            return error;
        }
        if (request.dateRange() != null) {
            error = validateDateRange(request.dateRange());
            if (error != null) {
                return error;
            }
        }
        if (request.coverageType() != null
                && !Set.of("PRODUCT_WITHOUT_STANDARD", "ASSAY_WITHOUT_STANDARD", "UNUSED_STANDARD")
                .contains(request.coverageType())) {
            return ErrorMapper.invalid("coverageType", "Unsupported assay standard coverage type.");
        }
        return validateLimit(request.limit() == null ? 50 : request.limit());
    }

    private ToolModels.ToolError validatePalletLifecycleRequest(PalletLifecycleRequest request) {
        ToolModels.ToolError error = validateRequiredString("code", request == null ? null : request.code(), 100);
        if (error != null) return error;
        if (request.flowLimit() != null && (request.flowLimit() < 1 || request.flowLimit() > 100)) {
            return ErrorMapper.invalid("flowLimit", "flowLimit must be between 1 and 100.");
        }
        return null;
    }

    private ToolModels.ToolError validatePalletFlowRecordsRequest(PalletFlowRecordsRequest request) {
        if (request == null) return ErrorMapper.invalid("request", "request is required.");
        ToolModels.ToolError error = validateOptionalString("code", request.code(), 100);
        if (error != null) return error;
        if (request.productScope() != null) {
            error = validateProductScope("productScope", request.productScope());
            if (error != null) return error;
        }
        error = validatePositiveId("warehouseId", request.warehouseId());
        if (error != null) return error;
        if (request.dateRange() != null) {
            error = validateDateRange(request.dateRange());
            if (error != null) return error;
        }
        if (request.eventTypes() != null && !request.eventTypes().isEmpty()
                && !allowedValues(request.eventTypes(),
                Set.of("INBOUND", "OUTBOUND", "TRANSFER", "BIND", "ASSAY", "CANCEL", "LABEL"))) {
            return ErrorMapper.invalid("eventTypes", "Unsupported pallet flow event type.");
        }
        return validatePage(request.page() == null ? 1 : request.page(), request.size() == null ? 20 : request.size());
    }

    private ToolModels.ToolError validatePrintedNotInboundCodesRequest(PrintedNotInboundCodesRequest request) {
        if (request == null) return ErrorMapper.invalid("request", "request is required.");
        ToolModels.ToolError error = request.productScope() == null
                ? null : validateProductScope("productScope", request.productScope());
        if (error != null) return error;
        error = validateOptionalString("orderNo", request.orderNo(), 50);
        if (error != null) return error;
        error = validateOptionalString("batchNo", request.batchNo(), 80);
        if (error != null) return error;
        if (request.dateRange() != null) {
            error = validateDateRange(request.dateRange());
            if (error != null) return error;
        }
        if (request.groupBy() != null && !Set.of("batch", "order", "product").contains(request.groupBy())) {
            return ErrorMapper.invalid("groupBy", "Unsupported printed-code grouping.");
        }
        return validateLimit(request.limit() == null ? 50 : request.limit());
    }

    private ToolModels.ToolError validatePalletAnomaliesRequest(PalletAnomaliesRequest request) {
        if (request == null) return ErrorMapper.invalid("request", "request is required.");
        ToolModels.ToolError error = request.productScope() == null
                ? null : validateProductScope("productScope", request.productScope());
        if (error != null) return error;
        error = validatePositiveId("warehouseId", request.warehouseId());
        if (error != null) return error;
        if (request.dateRange() != null) {
            error = validateDateRange(request.dateRange());
            if (error != null) return error;
        }
        if (request.anomalyTypes() != null
                && (request.anomalyTypes().isEmpty() || !allowedValues(request.anomalyTypes(), Set.of(
                "VOID_CODE_SCANNED", "STATUS_INVENTORY_MISMATCH", "DUPLICATE_INBOUND",
                "OUTBOUND_WITHOUT_INBOUND", "PRODUCT_BINDING_MISMATCH")))) {
            return ErrorMapper.invalid("anomalyTypes", "Unsupported pallet anomaly type.");
        }
        return validateLimit(request.limit() == null ? 50 : request.limit());
    }

    private ToolModels.ToolError validateQrBatchInboundCompletionRequest(QrBatchInboundCompletionRequest request) {
        if (request == null) return ErrorMapper.invalid("request", "request is required.");
        ToolModels.ToolError error = validateOptionalString("batchNo", request.batchNo(), 80);
        if (error != null) return error;
        error = validateOptionalString("orderNo", request.orderNo(), 50);
        if (error != null) return error;
        error = validatePositiveId("productId", request.productId());
        if (error != null) return error;
        if (request.dateRange() != null) {
            error = validateDateRange(request.dateRange());
            if (error != null) return error;
        }
        if ((request.batchNo() == null || request.batchNo().isBlank())
                && (request.orderNo() == null || request.orderNo().isBlank())
                && request.productId() == null && request.dateRange() == null) {
            return ErrorMapper.invalid("batchNo", "batchNo, orderNo, productId, or dateRange is required.");
        }
        return validateLimit(request.limit() == null ? 20 : request.limit());
    }

    private ToolModels.ToolError validateProductScope(String prefix, ToolModels.ProductScope scope) {
        String productType = scope.type();
        if (productType == null
                || !Set.of("SINGLE_PRODUCT", "EXACT_PRODUCT_NAME_GROUP", "PRODUCT_TYPE_GROUP", "ALL").contains(productType)) {
            return ErrorMapper.invalid(prefix + ".type", "Unsupported product scope type.");
        }
        if ("SINGLE_PRODUCT".equals(productType)) {
            ToolModels.ToolError error = validatePositiveId(prefix + ".productId", scope.productId());
            if (error != null || scope.productId() == null) {
                return error == null ? ErrorMapper.invalid(prefix + ".productId", "productId is required.") : error;
            }
        } else if ("EXACT_PRODUCT_NAME_GROUP".equals(productType)) {
            ToolModels.ToolError error = validateRequiredString(prefix + ".productName", scope.productName(), 100);
            if (error != null) return error;
        } else if ("PRODUCT_TYPE_GROUP".equals(productType)) {
            ToolModels.ToolError error = validateRequiredString(prefix + ".productType", scope.productType(), 50);
            if (error != null) return error;
        }
        return null;
    }

    private ToolModels.ToolError validateDateRange(ToolModels.DateRange range) {
        if (range.type() == null || !Set.of("EXACT", "LAST_DAYS", "RANGE").contains(range.type())) {
            return ErrorMapper.invalid("dateRange.type", "Unsupported date range type.");
        }
        if ("EXACT".equals(range.type()) && range.date() == null) {
            return ErrorMapper.invalid("dateRange.date", "date is required for EXACT.");
        }
        if ("LAST_DAYS".equals(range.type())
                && (range.days() == null || range.days() < 1 || range.days() > 366)) {
            return ErrorMapper.invalid("dateRange.days", "days must be between 1 and 366.");
        }
        if ("RANGE".equals(range.type())) {
            if (range.from() == null || range.to() == null) {
                return ErrorMapper.invalid("dateRange.from", "from and to are required for RANGE.");
            }
            if (range.from().isAfter(range.to())) {
                return ErrorMapper.invalid("dateRange.from", "from must not be after to.");
            }
        }
        return null;
    }

    private ToolModels.ToolError validateWarehouseStatusRequest(WarehouseStatusRequest request) {
        if (request == null) {
            return ErrorMapper.invalid("warehouseId", "warehouseId or warehouseQuery is required.");
        }
        ToolModels.ToolError error = validatePositiveId("warehouseId", request.warehouseId());
        if (error != null) {
            return error;
        }
        error = validateOptionalString("warehouseQuery", request.warehouseQuery(), 100);
        if (error != null) {
            return error;
        }
        if (request.warehouseId() == null && request.warehouseQuery() == null) {
            return ErrorMapper.invalid("warehouseId", "warehouseId or warehouseQuery is required.");
        }
        error = validatePage(request.page() == null ? DEFAULT_PAGE : request.page(), request.size() == null ? DEFAULT_SIZE : request.size());
        if (error != null) {
            return error;
        }
        if (request.recentLimit() != null && (request.recentLimit() < 1 || request.recentLimit() > 100)) {
            return ErrorMapper.invalid("recentLimit", "recentLimit must be between 1 and 100.");
        }
        return null;
    }


    private ToolModels.ToolError validatePalletStatusRequest(PalletStatusRequest request) {
        ToolModels.ToolError error = validateRequiredString("code", request == null ? null : request.code(), 100);
        if (error != null) {
            return error;
        }
        if (request.cycleNo() != null && request.cycleNo() < 1) {
            return ErrorMapper.invalid("cycleNo", "cycleNo must be greater than or equal to 1.");
        }
        if (request.flowLimit() != null && (request.flowLimit() < 1 || request.flowLimit() > 100)) {
            return ErrorMapper.invalid("flowLimit", "flowLimit must be between 1 and 100.");
        }
        return null;
    }

    private ToolModels.ToolError validateAssayStatusRequest(AssayStatusRequest request) {
        if (request == null) {
            return ErrorMapper.invalid("assayId", "assayId or productId plus productionDate is required.");
        }
        ToolModels.ToolError error = validatePositiveId("assayId", request.assayId());
        if (error != null) {
            return error;
        }
        error = validatePositiveId("productId", request.productId());
        if (error != null) {
            return error;
        }
        error = validateOptionalString("productQuery", request.productQuery(), 100);
        if (error != null) {
            return error;
        }
        if (request.assayId() != null) {
            return null;
        }
        if (request.productId() == null && request.productQuery() == null) {
            return ErrorMapper.invalid("assayId", "assayId or productId/productQuery plus productionDate is required.");
        }
        error = validateRequiredString("productionDate", request.productionDate(), 10);
        if (error != null) {
            return error;
        }
        if (request.productionDate().length() != 10) {
            return ErrorMapper.invalid("productionDate", "productionDate must use yyyy-MM-dd format.");
        }
        try {
            LocalDate.parse(request.productionDate());
        } catch (DateTimeParseException e) {
            return ErrorMapper.invalid("productionDate", "productionDate must use yyyy-MM-dd format.");
        }
        return null;
    }
    private ToolModels.ToolError validateRequiredString(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return ErrorMapper.invalid(field, field + " must not be blank.");
        }
        if (value.length() > maxLength) {
            return ErrorMapper.invalid(field, field + " length must be at most " + maxLength + ".");
        }
        return null;
    }

    private ToolModels.ToolError validateOptionalString(String field, String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            return ErrorMapper.invalid(field, field + " must not be blank when provided.");
        }
        if (value.length() > maxLength) {
            return ErrorMapper.invalid(field, field + " length must be at most " + maxLength + ".");
        }
        return null;
    }

    private ToolModels.ToolError validatePositiveId(String field, Integer value) {
        if (value != null && value <= 0) {
            return ErrorMapper.invalid(field, field + " must be greater than 0.");
        }
        return null;
    }

    private ToolModels.ToolError validateLimit(Integer limit) {
        if (limit != null && (limit < 1 || limit > 100)) {
            return ErrorMapper.invalid("limit", "limit must be between 1 and 100.");
        }
        return null;
    }

    private ToolModels.ToolError validatePage(int page, int size) {
        if (page < 1) {
            return ErrorMapper.invalid("page", "page must be greater than or equal to 1.");
        }
        if (size < 1 || size > 100) {
            return ErrorMapper.invalid("size", "size must be between 1 and 100.");
        }
        return null;
    }

    private int defaultLimit(Integer limit) {
        return limit == null ? DEFAULT_LIMIT : limit;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ProductCandidate productCandidate(JsonNode item, MatchType matchType, int matchScore) {
        String productName = text(item, "productName");
        return new ProductCandidate(
                intValue(item, "id", intValue(item, "productId", null)),
                productName,
                text(item, "productType"),
                text(item, "status", text(item, "productStatus")),
                text(item, "packagingMethod"),
                decimalValue(item, "weightPerPiece"),
                intValue(item, "piecesPerPallet", null),
                booleanValue(item, "canStack"),
                intValue(item, "screenMeshId", null),
                matchType,
                matchScore,
                matchReason(matchType, null, productName)
        );
    }

    private WarehouseCandidate warehouseCandidate(JsonNode item, MatchType matchType, int matchScore) {
        return warehouseCandidate(item, matchType, matchScore, matchReason(matchType, null, text(item, "warehouseName")));
    }

    private WarehouseCandidate warehouseCandidate(JsonNode item, MatchType matchType, int matchScore, String matchReason) {
        Integer maxCapacity = intValue(item, "maxCapacity", null);
        Integer curCapacity = intValue(item, "curCapacity", null);
        Integer freeCapacity = maxCapacity == null || curCapacity == null ? null : Math.max(0, maxCapacity - curCapacity);
        return new WarehouseCandidate(
                intValue(item, "id", intValue(item, "warehouseId", null)),
                text(item, "warehouseName"),
                text(item, "status"),
                intValue(item, "maxRows", null),
                maxCapacity,
                curCapacity,
                freeCapacity,
                matchType,
                matchScore,
                matchReason
        );
    }

    private InventoryOverviewRecord inventoryRecord(JsonNode item, ProductCandidate product) {
        Integer rawFullPallets = intValue(item, "totalQuantity", 0);
        Integer rawLoosePieces = intValue(item, "totalPieces", 0);
        StockQuantity quantity = normalizeStockQuantity(rawFullPallets, rawLoosePieces, product);
        return new InventoryOverviewRecord(
                intValue(item, "warehouseId", null),
                text(item, "warehouseName"),
                intValue(item, "productId", null),
                text(item, "productName"),
                text(item, "productStatus"),
                localDate(item, "entryDate"),
                rawFullPallets,
                rawLoosePieces,
                quantity.normalizedPallets(),
                quantity.normalizedLoosePieces(),
                quantity.totalEquivalentPieces(),
                decimalValue(item, "totalWeight"),
                displayStockInfo(text(item, "stockInfo"), quantity),
                quantity.calculationNote(),
                intValue(item, "warehouseCount", null)
        );
    }

    private InventoryOverviewSummary summarize(List<InventoryOverviewRecord> records) {
        Set<Integer> warehouseIds = new HashSet<>();
        Set<Integer> productIds = new HashSet<>();
        int rawFullPallets = 0;
        int rawLoosePieces = 0;
        int totalEquivalentPieces = 0;
        boolean allRecordsHaveEquivalentPieces = true;
        BigDecimal weight = BigDecimal.ZERO;
        for (InventoryOverviewRecord record : records) {
            rawFullPallets += nullToZero(record.rawFullPallets());
            rawLoosePieces += nullToZero(record.rawLoosePieces());
            if (record.totalEquivalentPieces() == null) {
                allRecordsHaveEquivalentPieces = false;
            } else {
                totalEquivalentPieces += record.totalEquivalentPieces();
            }
            if (record.totalWeight() != null) {
                weight = weight.add(record.totalWeight());
            }
            if (record.warehouseId() != null) {
                warehouseIds.add(record.warehouseId());
            }
            if (record.productId() != null) {
                productIds.add(record.productId());
            }
        }
        Integer normalizedPallets = records.size() == 1 ? records.getFirst().normalizedPallets() : null;
        Integer normalizedLoosePieces = records.size() == 1 ? records.getFirst().normalizedLoosePieces() : null;
        String displayStockInfo = records.size() == 1 ? records.getFirst().displayStockInfo() : null;
        String calculationNote = records.size() == 1
                ? records.getFirst().calculationNote()
                : "summary.rawFullPallets/rawLoosePieces are sums of backend raw fields. normalizedPallets/normalizedLoosePieces are only populated for a single product row because different products may have different piecesPerPallet.";
        return new InventoryOverviewSummary(
                records.size(),
                rawFullPallets,
                rawLoosePieces,
                normalizedPallets,
                normalizedLoosePieces,
                allRecordsHaveEquivalentPieces ? totalEquivalentPieces : null,
                weight,
                displayStockInfo,
                calculationNote,
                warehouseIds.size(),
                productIds.size()
        );
    }

    private StockQuantity normalizeStockQuantity(Integer rawFullPallets, Integer rawLoosePieces, ProductCandidate product) {
        int fullPallets = nullToZero(rawFullPallets);
        int loosePieces = nullToZero(rawLoosePieces);
        Integer piecesPerPallet = product == null ? null : product.piecesPerPallet();
        if (piecesPerPallet == null || piecesPerPallet <= 0) {
            return new StockQuantity(
                    null,
                    null,
                    null,
                    "Backend returned rawFullPallets=" + fullPallets + " and rawLoosePieces=" + loosePieces + "; normalization requires a positive piecesPerPallet."
            );
        }
        int totalEquivalentPieces = fullPallets * piecesPerPallet + loosePieces;
        int normalizedPallets = totalEquivalentPieces / piecesPerPallet;
        int normalizedLoosePieces = totalEquivalentPieces % piecesPerPallet;
        return new StockQuantity(
                normalizedPallets,
                normalizedLoosePieces,
                totalEquivalentPieces,
                "Backend raw fields mean rawFullPallets=" + fullPallets + " full pallets and rawLoosePieces=" + loosePieces
                        + " loose pieces. With piecesPerPallet=" + piecesPerPallet + ", totalEquivalentPieces=" + totalEquivalentPieces
                        + ", displayed as " + normalizedPallets + "板" + normalizedLoosePieces + "件."
        );
    }

    private String displayStockInfo(String upstreamStockInfo, StockQuantity quantity) {
        if (upstreamStockInfo != null && !upstreamStockInfo.isBlank()) {
            return upstreamStockInfo;
        }
        if (quantity.normalizedPallets() == null || quantity.normalizedLoosePieces() == null) {
            return null;
        }
        return quantity.normalizedPallets() + "板" + quantity.normalizedLoosePieces() + "件";
    }

    private WarehouseInventoryRecord warehouseInventoryRecord(JsonNode item) {
        return new WarehouseInventoryRecord(
                intValue(item, "inventoryId", null),
                intValue(item, "productId", null),
                text(item, "productName"),
                text(item, "warehouseName"),
                localDate(item, "sampleDate"),
                text(item, "productType"),
                text(item, "productStatus"),
                text(item, "standardNames"),
                text(item, "meshName"),
                text(item, "side"),
                intValue(item, "rowNumber", null),
                intValue(item, "layer", null),
                intValue(item, "quantity", null),
                intValue(item, "pieces", null),
                text(item, "palletCode"),
                localDateTime(item, "createdAt")
        );
    }

    private WarehouseRecentOperation recentOperation(JsonNode item) {
        return new WarehouseRecentOperation(
                localDateTime(item, "operationTime"),
                text(item, "operationType"),
                text(item, "operationName"),
                text(item, "operatorName"),
                text(item, "palletCode"),
                text(item, "productName"),
                text(item, "fromWarehouseName"),
                text(item, "toWarehouseName"),
                text(item, "remark")
        );
    }


    private AssayStatusResponse assayStatusResponse(AssayLookupMode lookupMode, ProductResolutionResponse productResolution, JsonNode assay,
                                                    boolean includeStandardDetails, List<String> warnings) {
        String judgeResult = text(assay, "judgeResult");
        if (judgeResult == null && booleanValue(assay, "isQualified") != null) {
            judgeResult = Boolean.TRUE.equals(booleanValue(assay, "isQualified")) ? "QUALIFIED" : "UNQUALIFIED";
        }
        return new AssayStatusResponse(
                lookupMode,
                ResolutionStatus.UNIQUE,
                false,
                productResolution,
                assay,
                judgeResult,
                includeStandardDetails ? jsonList(assay.path("failedMetrics")) : List.of(),
                includeStandardDetails && assay.path("appliedStandard").isObject() ? assay.path("appliedStandard") : null,
                false,
                warnings,
                null
        );
    }

    private static List<JsonNode> jsonList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode item : node) {
            result.add(item);
        }
        return result;
    }

    private static boolean isFatalUpstream(WarehouseApiException exception) {
        return switch (exception.code()) {
            case "UPSTREAM_UNAUTHORIZED", "UPSTREAM_PERMISSION_DENIED", "UPSTREAM_TIMEOUT", "UPSTREAM_SERVER_ERROR" -> true;
            default -> false;
        };
    }

    private static String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
    private MatchType matchType(String query, String value, boolean code) {
        if (code && query.equals(value)) {
            return MatchType.EXACT_CODE;
        }
        if (query.equals(value)) {
            return MatchType.EXACT_NAME;
        }
        String normalizedQuery = normalize(query);
        String normalizedValue = normalize(value);
        if (normalizedQuery.equals(normalizedValue)) {
            return MatchType.NORMALIZED_NAME;
        }
        if (alias(value).equals(normalizedQuery)) {
            return MatchType.ALIAS;
        }
        if (normalizedValue.startsWith(normalizedQuery)) {
            return MatchType.PREFIX;
        }
        return MatchType.CONTAINS;
    }

    private int matchScore(String query, String value, boolean code) {
        return matchScore(matchType(query, value, code));
    }

    private int matchScore(MatchType matchType) {
        return switch (matchType) {
            case EXACT_ID, EXACT_CODE, EXACT_NAME -> 100;
            case NORMALIZED_NAME -> 95;
            case ALIAS -> 90;
            case PREFIX -> 80;
            case CONTAINS -> 60;
        };
    }

    private String matchReason(MatchType matchType, String query, String value) {
        return switch (matchType) {
            case EXACT_ID -> "按 ID 精确匹配。";
            case EXACT_CODE -> "按编码精确匹配。";
            case EXACT_NAME -> "按名称精确匹配。";
            case NORMALIZED_NAME -> "归一化名称后匹配。";
            case ALIAS -> "按去除规格括号后的别名匹配。";
            case PREFIX -> "按名称前缀匹配。";
            case CONTAINS -> "按名称包含关系匹配。";
        };
    }

    private WarehouseNameQuery normalizeWarehouseNameQuery(String query) {
        String original = query == null ? "" : query.trim();
        String naturalNumber = warehouseNaturalNumber(original);
        if (naturalNumber == null) {
            return new WarehouseNameQuery(original, false, null);
        }
        return new WarehouseNameQuery(
                naturalNumber,
                true,
                "将 " + original + " 归一化为 " + naturalNumber + " 后匹配库位名称。"
        );
    }

    private String warehouseNaturalNumber(String query) {
        String compact = Normalizer.normalize(query, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
        String token = null;
        if (compact.endsWith("#")) {
            token = compact.substring(0, compact.length() - 1);
        } else if (compact.startsWith("库位") && compact.length() > 2) {
            token = compact.substring(2);
        } else {
            String[] suffixes = {"号库位", "号库", "号位", "号"};
            for (String suffix : suffixes) {
                if (compact.endsWith(suffix) && compact.length() > suffix.length()) {
                    token = compact.substring(0, compact.length() - suffix.length());
                    break;
                }
            }
        }
        return parseNaturalNumber(token);
    }

    private String parseNaturalNumber(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (token.matches("[1-9]\\d{0,2}")) {
            return String.valueOf(Integer.parseInt(token));
        }
        Integer chineseNumber = parseChineseNumber(token);
        return chineseNumber == null ? null : String.valueOf(chineseNumber);
    }

    private Integer parseChineseNumber(String token) {
        if (token == null || token.isBlank() || !token.matches("[零〇一二两三四五六七八九十]+")) {
            return null;
        }
        if (token.equals("十")) {
            return 10;
        }
        int tenIndex = token.indexOf('十');
        if (tenIndex >= 0) {
            String tensPart = token.substring(0, tenIndex);
            String onesPart = token.substring(tenIndex + 1);
            int tens = tensPart.isEmpty() ? 1 : chineseDigit(tensPart);
            int ones = onesPart.isEmpty() ? 0 : chineseDigit(onesPart);
            if (tens < 0 || ones < 0) {
                return null;
            }
            return tens * 10 + ones;
        }
        return chineseDigit(token);
    }

    private int chineseDigit(String value) {
        return switch (value) {
            case "零", "〇" -> 0;
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            default -> -1;
        };
    }
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}（）()]+", "");
    }

    private static String alias(String value) {
        return normalize(value == null ? "" : value.replaceAll("[（(].*?[）)]", ""));
    }

    private static boolean hasFreeCapacity(WarehouseCandidate candidate) {
        return candidate.freeCapacity() == null || candidate.freeCapacity() > 0;
    }

    private static boolean isPositiveInteger(String value) {
        return value != null && value.trim().matches("[1-9]\\d{0,9}");
    }

    private static boolean sameId(Integer left, Integer right) {
        return left != null && left.equals(right);
    }

    private static boolean isObject(JsonNode node) {
        return node != null && node.isObject() && !node.isEmpty();
    }

    private static Iterable<JsonNode> asArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return node;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String text(JsonNode node, String field) {
        return text(node, field, null);
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? fallback : text;
    }

    private static Integer intValue(JsonNode node, String field, Integer fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !value.canConvertToInt()) {
            return fallback;
        }
        return value.asInt();
    }

    private static long longValue(JsonNode node, String field, long fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !value.canConvertToLong()) {
            return fallback;
        }
        return value.asLong();
    }

    private static Boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    private static BigDecimal decimalValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.decimalValue();
    }

    private static LocalDate localDate(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : LocalDate.parse(value.substring(0, Math.min(10, value.length())));
    }

    private static LocalDateTime localDateTime(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : LocalDateTime.parse(value);
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private record StockQuantity(Integer normalizedPallets, Integer normalizedLoosePieces, Integer totalEquivalentPieces, String calculationNote) {
    }

    private record WarehouseNameQuery(String queryName, boolean normalized, String matchReason) {
    }

    private record WarehouseResolution(ResolutionStatus status, List<WarehouseCandidate> candidates) {
    }
}
