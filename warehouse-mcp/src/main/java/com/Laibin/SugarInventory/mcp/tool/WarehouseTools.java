package com.Laibin.SugarInventory.mcp.tool;

import java.time.LocalDate;
import java.util.List;

import com.Laibin.SugarInventory.mcp.client.WarehouseApiException;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayRecordsRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayRecordsResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayReportDetailRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayReportDetailResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayStandardCoverageRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayStandardCoverageResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayAbnormalitiesRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayAbnormalitiesResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayStatusRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayStatusResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductsWithoutRecentAssayRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductsWithoutRecentAssayResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryDistributionRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryDistributionResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryQualityRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryQualityResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryDistributionFilter;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductScope;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseScope;
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
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductResolutionResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionEntityResolveRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionEntityResolutionResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionOrderProgressRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductionOrderProgressResponse;
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
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolveProductsRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolveWarehousesRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseResolutionResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseStatusRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseStatusResponse;
import com.Laibin.SugarInventory.mcp.service.ErrorMapper;
import com.Laibin.SugarInventory.mcp.service.WarehouseReadService;
import com.Laibin.SugarInventory.mcp.security.WarehouseToolCallContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class WarehouseTools {
    private static final Logger log = LoggerFactory.getLogger(WarehouseTools.class);

    private final WarehouseReadService readService;

    public WarehouseTools(WarehouseReadService readService) {
        this.readService = readService;
    }

    @Tool(name = "resolve_products", description = "Resolve a natural-language product query to unique or ambiguous product candidates without modifying warehouse data.")
    public ProductResolutionResponse resolveProducts(
            @ToolParam(description = "Product id, name, normalized name, or alias fragment. Length 1..100.") @Schema(minLength = 1, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(min = 1, max = 100) String query,
            @ToolParam(description = "Optional product type filter. Length 1..50.", required = false) @Schema(minLength = 1, maxLength = 50) @Size(min = 1, max = 50) String productType,
            @ToolParam(description = "Optional product status filter. Length 1..50.", required = false) @Schema(minLength = 1, maxLength = 50) @Size(min = 1, max = 50) String productStatus,
            @ToolParam(description = "Maximum candidates to return. Range 1..100.", required = false) @Schema(minimum = "1", maximum = "100") @Min(1) @Max(100) Integer limit) {
        return resolveProducts(new ResolveProductsRequest(query, productType, productStatus, limit));
    }

    public ProductResolutionResponse resolveProducts(ResolveProductsRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("resolve_products", () -> readService.resolveProducts(request));
        } catch (WarehouseApiException e) {
            return ProductResolutionResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            return ProductResolutionResponse.error(ErrorMapper.unexpected());
        }
    }

    @Tool(name = "resolve_warehouses", description = "Resolve a natural-language warehouse query to unique or ambiguous warehouse candidates without modifying warehouse data.")
    public WarehouseResolutionResponse resolveWarehouses(
            @ToolParam(description = "Warehouse id, name, normalized name, or alias fragment. Length 1..100.") @Schema(minLength = 1, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(min = 1, max = 100) String query,
            @ToolParam(description = "When true, filter out warehouses without free capacity when capacity fields are present.", required = false) Boolean onlyAvailable,
            @ToolParam(description = "Maximum candidates to return. Range 1..100.", required = false) @Schema(minimum = "1", maximum = "100") @Min(1) @Max(100) Integer limit) {
        return resolveWarehouses(new ResolveWarehousesRequest(query, onlyAvailable, limit));
    }

    public WarehouseResolutionResponse resolveWarehouses(ResolveWarehousesRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("resolve_warehouses", () -> readService.resolveWarehouses(request));
        } catch (WarehouseApiException e) {
            return WarehouseResolutionResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            return WarehouseResolutionResponse.error(ErrorMapper.unexpected());
        }
    }

    @Tool(name = "get_inventory_overview", description = "Read paged inventory overview and totals, preferring a unique productId when available.")
    public InventoryOverviewResponse getInventoryOverview(
            @ToolParam(description = "Preferred unique product id. When supplied, productQuery is ignored.", required = false) @Schema(minimum = "1") @Min(1) Integer productId,
            @ToolParam(description = "Product query used only when productId is absent. Length 1..100.", required = false) @Schema(minLength = 1, maxLength = 100) @Size(min = 1, max = 100) String productQuery,
            @ToolParam(description = "Optional product status filter. Length 1..50.", required = false) @Schema(minLength = 1, maxLength = 50) @Size(min = 1, max = 50) String productStatus,
            @ToolParam(description = "Page number. Minimum 1.", required = false) @Schema(minimum = "1") @Min(1) Integer page,
            @ToolParam(description = "Page size. Range 1..100.", required = false) @Schema(minimum = "1", maximum = "100") @Min(1) @Max(100) Integer size) {
        return getInventoryOverview(new InventoryOverviewRequest(productId, productQuery, productStatus, page, size));
    }

    public InventoryOverviewResponse getInventoryOverview(InventoryOverviewRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("get_inventory_overview", () -> readService.getInventoryOverview(request));
        } catch (WarehouseApiException e) {
            return InventoryOverviewResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            return InventoryOverviewResponse.error(ErrorMapper.unexpected());
        }
    }

    @Tool(name = "get_inventory_distribution", description = "Read filtered current inventory distribution for a controlled product and warehouse scope without modifying warehouse data.")
    public InventoryDistributionResponse getInventoryDistribution(
            @ToolParam(description = "Controlled product scope: one product, exact product-name group, product-type group, or all products.") ProductScope productScope,
            @ToolParam(description = "Warehouse scope: all warehouses or one resolver-confirmed warehouse.") WarehouseScope warehouseScope,
            @ToolParam(description = "Optional product, warehouse, pallet, assay, and entry-date filters.", required = false) InventoryDistributionFilter statusFilter,
            @ToolParam(description = "Grouping dimension: warehouse, product, or warehouse_product.") String groupBy,
            @ToolParam(description = "Maximum result groups. Range 1..100; defaults to 20.", required = false)
            @Schema(minimum = "1", maximum = "100") @Min(1) @Max(100) Integer limit) {
        return getInventoryDistribution(new InventoryDistributionRequest(productScope, warehouseScope, statusFilter, groupBy, limit));
    }

    public InventoryDistributionResponse getInventoryDistribution(InventoryDistributionRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("get_inventory_distribution",
                    () -> readService.getInventoryDistribution(request));
        } catch (WarehouseApiException e) {
            return InventoryDistributionResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("get_inventory_distribution failed; productScope={}, warehouseScope={}, groupBy={}, errorType={}, message={}",
                    request == null || request.productScope() == null ? null : request.productScope().type(),
                    request == null || request.warehouseScope() == null ? null : request.warehouseScope().type(),
                    request == null ? null : request.groupBy(),
                    e.getClass().getSimpleName(),
                    safeLogValue(e.getMessage()));
            return InventoryDistributionResponse.error(ErrorMapper.unexpected());
        }
    }

    public AssayRecordsResponse queryAssayRecords(AssayRecordsRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_assay_records",
                    () -> readService.queryAssayRecords(request));
        } catch (WarehouseApiException e) {
            return AssayRecordsResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_assay_records failed; productScope={}, judgeStatus={}, errorType={}, message={}",
                    request == null || request.productScope() == null ? null : request.productScope().type(),
                    request == null ? null : request.judgeStatus(),
                    e.getClass().getSimpleName(),
                    safeLogValue(e.getMessage()));
            return AssayRecordsResponse.error(ErrorMapper.unexpected());
        }
    }

    public AssayReportDetailResponse getAssayReportDetail(AssayReportDetailRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("get_assay_report_detail",
                    () -> readService.getAssayReportDetail(request));
        } catch (WarehouseApiException e) {
            return AssayReportDetailResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("get_assay_report_detail failed; reportRefPresent={}, errorType={}, message={}",
                    request != null && request.reportRef() != null && !request.reportRef().isBlank(),
                    e.getClass().getSimpleName(),
                    safeLogValue(e.getMessage()));
            return AssayReportDetailResponse.error(ErrorMapper.unexpected());
        }
    }

    public AssayAbnormalitiesResponse queryAssayAbnormalities(AssayAbnormalitiesRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_assay_abnormalities",
                    () -> readService.queryAssayAbnormalities(request));
        } catch (WarehouseApiException e) {
            return AssayAbnormalitiesResponse.error(ErrorMapper.upstream(e));
        } catch (Exception e) {
            log.warn("query_assay_abnormalities failed; productScope={}, groupBy={}, errorType={}, message={}",
                    request == null ? null : request.productScope(),
                    request == null ? null : request.groupBy(),
                    e.getClass().getSimpleName(),
                    e.getMessage());
            return AssayAbnormalitiesResponse.error(ErrorMapper.unexpected());
        }
    }

    public InventoryQualityResponse queryInventoryQuality(String toolName, InventoryQualityRequest request) {
        try {
            return WarehouseToolCallContext.withToolName(toolName, () -> readService.queryInventoryQuality(request));
        } catch (WarehouseApiException e) {
            return InventoryQualityResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("{} failed; mode={}, errorType={}, message={}", toolName,
                    request == null ? null : request.mode(), e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return InventoryQualityResponse.error(ErrorMapper.unexpected());
        }
    }

    public ProductsWithoutRecentAssayResponse queryProductsWithoutRecentAssay(ProductsWithoutRecentAssayRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_products_without_recent_assay",
                    () -> readService.queryProductsWithoutRecentAssay(request));
        } catch (WarehouseApiException e) {
            return ProductsWithoutRecentAssayResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_products_without_recent_assay failed; productScope={}, warehouseScope={}, groupBy={}, errorType={}, message={}",
                    request == null ? null : request.productScope(),
                    request == null ? null : request.warehouseScope(),
                    request == null ? null : request.groupBy(),
                    e.getClass().getSimpleName(),
                    safeLogValue(e.getMessage()));
            return ProductsWithoutRecentAssayResponse.error(ErrorMapper.unexpected());
        }
    }

    public AssayStandardCoverageResponse queryAssayStandardCoverage(AssayStandardCoverageRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_assay_standard_coverage",
                    () -> readService.queryAssayStandardCoverage(request));
        } catch (WarehouseApiException e) {
            return AssayStandardCoverageResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_assay_standard_coverage failed; productScope={}, coverageType={}, errorType={}, message={}",
                    request == null ? null : request.productScope(),
                    request == null ? null : request.coverageType(),
                    e.getClass().getSimpleName(),
                    safeLogValue(e.getMessage()));
            return AssayStandardCoverageResponse.error(ErrorMapper.unexpected());
        }
    }

    public PalletLifecycleResponse queryQrCodeLifecycle(PalletLifecycleRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_qr_code_lifecycle", () -> readService.queryQrCodeLifecycle(request));
        } catch (WarehouseApiException e) {
            return PalletLifecycleResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_qr_code_lifecycle failed; errorType={}, message={}", e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return PalletLifecycleResponse.error(ErrorMapper.unexpected());
        }
    }

    public PrintedNotInboundCodesResponse queryPrintedNotInboundCodes(PrintedNotInboundCodesRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_printed_not_inbound_codes", () -> readService.queryPrintedNotInboundCodes(request));
        } catch (WarehouseApiException e) {
            return PrintedNotInboundCodesResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_printed_not_inbound_codes failed; errorType={}, message={}", e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return PrintedNotInboundCodesResponse.error(ErrorMapper.unexpected());
        }
    }

    public PalletAnomaliesResponse queryPalletAnomalies(PalletAnomaliesRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_pallet_anomalies", () -> readService.queryPalletAnomalies(request));
        } catch (WarehouseApiException e) {
            return PalletAnomaliesResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_pallet_anomalies failed; errorType={}, message={}", e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return PalletAnomaliesResponse.error(ErrorMapper.unexpected());
        }
    }

    public PalletFlowRecordsResponse queryPalletFlowRecords(PalletFlowRecordsRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_pallet_flow_records", () -> readService.queryPalletFlowRecords(request));
        } catch (WarehouseApiException e) {
            return PalletFlowRecordsResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_pallet_flow_records failed; errorType={}, message={}", e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return PalletFlowRecordsResponse.error(ErrorMapper.unexpected());
        }
    }

    public QrBatchInboundCompletionResponse queryQrBatchInboundCompletion(QrBatchInboundCompletionRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_qr_batch_inbound_completion", () -> readService.queryQrBatchInboundCompletion(request));
        } catch (WarehouseApiException e) {
            return QrBatchInboundCompletionResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_qr_batch_inbound_completion failed; errorType={}, message={}", e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return QrBatchInboundCompletionResponse.error(ErrorMapper.unexpected());
        }
    }

    public ProductionEntityResolutionResponse resolveProductionEntities(String entityType, String query, Integer limit) {
        try {
            return WarehouseToolCallContext.withToolName("resolve_production_entities",
                    () -> readService.resolveProductionEntities(new ProductionEntityResolveRequest(entityType, query, limit)));
        } catch (WarehouseApiException e) {
            return ProductionEntityResolutionResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("resolve_production_entities failed; errorType={}, message={}",
                    e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return ProductionEntityResolutionResponse.error(ErrorMapper.unexpected());
        }
    }

    public ProductionOrderProgressResponse queryProductionOrderProgress(String orderRef) {
        try {
            return WarehouseToolCallContext.withToolName("query_production_order_progress",
                    () -> readService.queryProductionOrderProgress(new ProductionOrderProgressRequest(orderRef)));
        } catch (WarehouseApiException e) {
            return ProductionOrderProgressResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_production_order_progress failed; errorType={}, message={}",
                    e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return ProductionOrderProgressResponse.error(ErrorMapper.unexpected());
        }
    }

    public ProductionBoilingBatchListResponse queryBoilingBatches(String productQuery, LocalDate startDate,
                                                                   LocalDate endDate, String status, Integer limit) {
        try {
            return WarehouseToolCallContext.withToolName("query_boiling_batches",
                    () -> readService.queryBoilingBatches(
                            new ProductionBoilingBatchListRequest(
                                    productQuery,
                                    startDate == null ? null : startDate.toString(),
                                    endDate == null ? null : endDate.toString(),
                                    status,
                                    limit)));
        } catch (WarehouseApiException e) {
            return ProductionBoilingBatchListResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_boiling_batches failed; errorType={}, message={}",
                    e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return ProductionBoilingBatchListResponse.error(ErrorMapper.unexpected());
        }
    }

    public ProductionBoilingBatchTraceResponse queryBoilingBatchTrace(String batchRef) {
        try {
            return WarehouseToolCallContext.withToolName("query_boiling_batch_trace",
                    () -> readService.queryBoilingBatchTrace(new ProductionBoilingBatchTraceRequest(batchRef)));
        } catch (WarehouseApiException e) {
            return ProductionBoilingBatchTraceResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_boiling_batch_trace failed; errorType={}, message={}",
                    e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return ProductionBoilingBatchTraceResponse.error(ErrorMapper.unexpected());
        }
    }

    public ProductionMaterialPickTraceResponse queryMaterialPickTrace(String orderRef) {
        try {
            return WarehouseToolCallContext.withToolName("query_material_pick_trace",
                    () -> readService.queryMaterialPickTrace(new ProductionMaterialPickTraceRequest(orderRef)));
        } catch (WarehouseApiException e) {
            return ProductionMaterialPickTraceResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_material_pick_trace failed; errorType={}, message={}",
                    e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return ProductionMaterialPickTraceResponse.error(ErrorMapper.unexpected());
        }
    }

    public ProductionLabelCompletionResponse queryProductionLabelCompletion(String orderRef) {
        try {
            return WarehouseToolCallContext.withToolName("query_production_label_completion",
                    () -> readService.queryProductionLabelCompletion(new ProductionLabelCompletionRequest(orderRef)));
        } catch (WarehouseApiException e) {
            return ProductionLabelCompletionResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_production_label_completion failed; errorType={}, message={}",
                    e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return ProductionLabelCompletionResponse.error(ErrorMapper.unexpected());
        }
    }

    public ProductionInProcessMaterialsResponse queryInProcessMaterials(String productName, String productType,
                                                                         String productionDateStart, String productionDateEnd,
                                                                         Integer page, Integer size) {
        try {
            return WarehouseToolCallContext.withToolName("query_in_process_materials",
                    () -> readService.queryInProcessMaterials(new ProductionInProcessMaterialsRequest(
                            productName, productType, productionDateStart, productionDateEnd, page, size)));
        } catch (WarehouseApiException e) {
            return ProductionInProcessMaterialsResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_in_process_materials failed; errorType={}, message={}",
                    e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return ProductionInProcessMaterialsResponse.error(ErrorMapper.unexpected());
        }
    }

    public ProductionMaterialCandidatesResponse queryMaterialCandidates(String orderRef, Integer page, Integer size) {
        try {
            return WarehouseToolCallContext.withToolName("query_material_candidates",
                    () -> readService.queryMaterialCandidates(new ProductionMaterialCandidatesRequest(orderRef, page, size)));
        } catch (WarehouseApiException e) {
            return ProductionMaterialCandidatesResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_material_candidates failed; errorType={}, message={}",
                    e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return ProductionMaterialCandidatesResponse.error(ErrorMapper.unexpected());
        }
    }

    public PalletTasksResponse queryPalletTasks(PalletTasksRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_pallet_tasks", () -> readService.queryPalletTasks(request));
        } catch (WarehouseApiException e) {
            return PalletTasksResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_pallet_tasks failed; errorType={}, message={}", e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return PalletTasksResponse.error(ErrorMapper.unexpected());
        }
    }

    public StockDocumentsResponse queryStockDocuments(StockDocumentsRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_stock_documents", () -> readService.queryStockDocuments(request));
        } catch (WarehouseApiException e) {
            return StockDocumentsResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_stock_documents failed; errorType={}, message={}", e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return StockDocumentsResponse.error(ErrorMapper.unexpected());
        }
    }

    public AutoInboundBatchesResponse queryAutoInboundBatches(AutoInboundBatchesRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_auto_inbound_batches", () -> readService.queryAutoInboundBatches(request));
        } catch (WarehouseApiException e) {
            return AutoInboundBatchesResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_auto_inbound_batches failed; errorType={}, message={}", e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return AutoInboundBatchesResponse.error(ErrorMapper.unexpected());
        }
    }

    public AutoInboundBatchDetailResponse getAutoInboundBatchDetail(AutoInboundBatchDetailRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("get_auto_inbound_batch_detail", () -> readService.getAutoInboundBatchDetail(request));
        } catch (WarehouseApiException e) {
            return AutoInboundBatchDetailResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("get_auto_inbound_batch_detail failed; errorType={}, message={}", e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return AutoInboundBatchDetailResponse.error(ErrorMapper.unexpected());
        }
    }

    public WarehouseCapacityDistributionResponse queryWarehouseCapacityDistribution(WarehouseCapacityDistributionRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_warehouse_capacity_distribution",
                    () -> readService.queryWarehouseCapacityDistribution(request));
        } catch (WarehouseApiException e) {
            return WarehouseCapacityDistributionResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_warehouse_capacity_distribution failed; errorType={}, message={}",
                    e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return WarehouseCapacityDistributionResponse.error(ErrorMapper.unexpected());
        }
    }

    public WarehouseRecentOperationsResponse queryWarehouseRecentOperations(WarehouseRecentOperationsRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_warehouse_recent_operations",
                    () -> readService.queryWarehouseRecentOperations(request));
        } catch (WarehouseApiException e) {
            return WarehouseRecentOperationsResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_warehouse_recent_operations failed; errorType={}, message={}",
                    e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return WarehouseRecentOperationsResponse.error(ErrorMapper.unexpected());
        }
    }

    public WarehouseMixedStorageFactsResponse queryWarehouseMixedStorageFacts(WarehouseMixedStorageFactsRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("query_warehouse_mixed_storage_facts",
                    () -> readService.queryWarehouseMixedStorageFacts(request));
        } catch (WarehouseApiException e) {
            return WarehouseMixedStorageFactsResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            log.warn("query_warehouse_mixed_storage_facts failed; errorType={}, message={}",
                    e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return WarehouseMixedStorageFactsResponse.error(ErrorMapper.unexpected());
        }
    }

    public ProductCatalogResponse queryProductCatalog(String productName, String productType, String productStatus,
                                                       String packagingMethod, String screenMeshName, Integer page, Integer size) {
        try { return WarehouseToolCallContext.withToolName("query_product_catalog", () -> readService.queryProductCatalog(
                new ProductCatalogRequest(productName, productType, productStatus, packagingMethod, screenMeshName, page, size)));
        } catch (WarehouseApiException e) { return ProductCatalogResponse.error(ErrorMapper.upstream(e)); }
        catch (RuntimeException e) { return ProductCatalogResponse.error(ErrorMapper.unexpected()); }
    }
    public ProductDetailResponse getProductDetail(String productName) {
        try { return WarehouseToolCallContext.withToolName("get_product_detail", () -> readService.getProductDetail(new ProductDetailRequest(productName))); }
        catch (WarehouseApiException e) { return ProductDetailResponse.error(ErrorMapper.upstream(e)); }
        catch (RuntimeException e) { return ProductDetailResponse.error(ErrorMapper.unexpected()); }
    }
    public ScreenMeshCatalogResponse queryScreenMeshCatalog(String meshName, Integer page, Integer size) {
        try { return WarehouseToolCallContext.withToolName("query_screen_mesh_catalog", () -> readService.queryScreenMeshCatalog(new ScreenMeshCatalogRequest(meshName, page, size))); }
        catch (WarehouseApiException e) { return ScreenMeshCatalogResponse.error(ErrorMapper.upstream(e)); }
        catch (RuntimeException e) { return ScreenMeshCatalogResponse.error(ErrorMapper.unexpected()); }
    }
    public AssayGroupsCatalogResponse queryAssayGroups(String groupName, Integer page, Integer size) {
        try { return WarehouseToolCallContext.withToolName("query_assay_groups", () -> readService.queryAssayGroups(groupName, page, size)); }
        catch (WarehouseApiException e) { return AssayGroupsCatalogResponse.error(ErrorMapper.upstream(e)); } catch (RuntimeException e) { return AssayGroupsCatalogResponse.error(ErrorMapper.unexpected()); }
    }
    public QualityStandardCatalogResponse queryQualityStandardCatalog(String productType, String standardName, String status, Integer page, Integer size) {
        try { return WarehouseToolCallContext.withToolName("query_quality_standard_catalog", () -> readService.queryQualityStandardCatalog(productType, standardName, status, page, size)); }
        catch (WarehouseApiException e) { return QualityStandardCatalogResponse.error(ErrorMapper.upstream(e)); } catch (RuntimeException e) { return QualityStandardCatalogResponse.error(ErrorMapper.unexpected()); }
    }
    public QualityStandardDetailResponse getQualityStandardDetail(String standardCode, Integer version) {
        try { return WarehouseToolCallContext.withToolName("get_quality_standard_detail", () -> readService.getQualityStandardDetail(standardCode, version)); }
        catch (WarehouseApiException e) { return QualityStandardDetailResponse.error(ErrorMapper.upstream(e)); } catch (RuntimeException e) { return QualityStandardDetailResponse.error(ErrorMapper.unexpected()); }
    }
    public ProductStandardRelationsResponse queryProductStandardRelations(String productName) {
        try { return WarehouseToolCallContext.withToolName("query_product_standard_relations", () -> readService.queryProductStandardRelations(productName)); }
        catch (WarehouseApiException e) { return ProductStandardRelationsResponse.error(ErrorMapper.upstream(e)); } catch (RuntimeException e) { return ProductStandardRelationsResponse.error(ErrorMapper.unexpected()); }
    }
    public ProductQualityConfigurationResponse queryProductQualityConfiguration(Integer productId) {
        try {
            return WarehouseToolCallContext.withToolName(
                    "query_product_quality_configuration",
                    () -> readService.queryProductQualityConfiguration(productId));
        } catch (WarehouseApiException e) {
            return ProductQualityConfigurationResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            return ProductQualityConfigurationResponse.error(ErrorMapper.unexpected());
        }
    }
    public EmployeeRosterResponse queryEmployeeRoster(String employeeId, String name, String department, String position, String status, String roleCode, Integer page, Integer size) {
        try { return WarehouseToolCallContext.withToolName("query_employee_roster", () -> readService.queryEmployeeRoster(employeeId, name, department, position, status, roleCode, page, size)); }
        catch (WarehouseApiException e) { return EmployeeRosterResponse.error(ErrorMapper.upstream(e)); } catch (RuntimeException e) { return EmployeeRosterResponse.error(ErrorMapper.unexpected()); }
    }
    public RoleCatalogResponse queryRoles(String keyword, String status, Integer page, Integer size) {
        try { return WarehouseToolCallContext.withToolName("query_roles", () -> readService.queryRoles(keyword, status, page, size)); }
        catch (WarehouseApiException e) { return RoleCatalogResponse.error(ErrorMapper.upstream(e)); } catch (RuntimeException e) { return RoleCatalogResponse.error(ErrorMapper.unexpected()); }
    }
    public RolePermissionSummaryResponse getRolePermissionSummary(String roleCodeOrName) {
        try { return WarehouseToolCallContext.withToolName("get_role_permission_summary", () -> readService.getRolePermissionSummary(roleCodeOrName)); }
        catch (WarehouseApiException e) { return RolePermissionSummaryResponse.error(ErrorMapper.upstream(e)); } catch (RuntimeException e) { return RolePermissionSummaryResponse.error(ErrorMapper.unexpected()); }
    }
    public AuditPageResponse searchOperationLogs(String module, String operationType, String operator, String startTime, String endTime, Integer page, Integer size) {
        try { return WarehouseToolCallContext.withToolName("search_operation_logs", () -> readService.searchOperationLogs(module, operationType, operator, startTime, endTime, page, size)); }
        catch (WarehouseApiException e) { return AuditPageResponse.error(ErrorMapper.upstream(e)); } catch (RuntimeException e) { return AuditPageResponse.error(ErrorMapper.unexpected()); }
    }
    public AuditPageResponse queryAgentToolAudit(String capability, String resultCode, String errorCode, String startTime, String endTime, Integer page, Integer size) {
        try { return WarehouseToolCallContext.withToolName("query_agent_tool_audit", () -> readService.queryAgentToolAudit(capability, resultCode, errorCode, startTime, endTime, page, size)); }
        catch (WarehouseApiException e) { return AuditPageResponse.error(ErrorMapper.upstream(e)); } catch (RuntimeException e) { return AuditPageResponse.error(ErrorMapper.unexpected()); }
    }
    public AuditPageResponse queryAgentAnswerReviews(String reviewStatus, String answerStatus, String failureDomain, String failureCategory, String suggestedFixType, String testCaseStatus, Boolean priorityOnly, Integer page, Integer size) {
        try { return WarehouseToolCallContext.withToolName("query_agent_answer_reviews", () -> readService.queryAgentAnswerReviews(reviewStatus, answerStatus, failureDomain, failureCategory, suggestedFixType, testCaseStatus, priorityOnly, page, size)); }
        catch (WarehouseApiException e) { return AuditPageResponse.error(ErrorMapper.upstream(e)); } catch (RuntimeException e) { return AuditPageResponse.error(ErrorMapper.unexpected()); }
    }
    public InventoryLedgerResponse queryInventoryLedger(String productName, String warehouseName, String screenMeshName, String productStatus, String entryDateStart, String entryDateEnd, Integer page, Integer size) {
        try { return WarehouseToolCallContext.withToolName("query_inventory_ledger", () -> readService.queryInventoryLedger(productName, warehouseName, screenMeshName, productStatus, entryDateStart, entryDateEnd, page, size)); }
        catch (WarehouseApiException e) { return InventoryLedgerResponse.error(ErrorMapper.upstream(e)); } catch (RuntimeException e) { return InventoryLedgerResponse.error(ErrorMapper.unexpected()); }
    }
    public PreparePoolBalanceResponse queryPreparePoolBalance(String productName, String productType, String screenMeshName, String productionDateStart, String productionDateEnd, Boolean positiveOnly, Integer page, Integer size) {
        try { return WarehouseToolCallContext.withToolName("query_prepare_pool_balance", () -> readService.queryPreparePoolBalance(productName, productType, screenMeshName, productionDateStart, productionDateEnd, positiveOnly, page, size)); }
        catch (WarehouseApiException e) { return PreparePoolBalanceResponse.error(ErrorMapper.upstream(e)); } catch (RuntimeException e) { return PreparePoolBalanceResponse.error(ErrorMapper.unexpected()); }
    }
    public FixedProductQrPoolResponse queryFixedProductQrPool(String productName, List<String> codes, String status, Boolean freeOnly, Integer page, Integer size) {
        try { return WarehouseToolCallContext.withToolName("query_fixed_product_qr_pool", () -> readService.queryFixedProductQrPool(productName, codes, status, freeOnly, page, size)); }
        catch (WarehouseApiException e) { return FixedProductQrPoolResponse.error(ErrorMapper.upstream(e)); } catch (RuntimeException e) { return FixedProductQrPoolResponse.error(ErrorMapper.unexpected()); }
    }

    @Tool(name = "get_warehouse_status", description = "Read warehouse capacity, inventory details, and recent operations, preferring a unique warehouseId when available.")
    public WarehouseStatusResponse getWarehouseStatus(
            @ToolParam(description = "Preferred unique warehouse id. When supplied, warehouseQuery is ignored.", required = false) @Schema(minimum = "1") @Min(1) Integer warehouseId,
            @ToolParam(description = "Warehouse query used only when warehouseId is absent. Length 1..100.", required = false) @Schema(minLength = 1, maxLength = 100) @Size(min = 1, max = 100) String warehouseQuery,
            @ToolParam(description = "Include paged inventory details for this warehouse.", required = false) Boolean includeInventoryDetails,
            @ToolParam(description = "Include recent warehouse operation records.", required = false) Boolean includeRecentOperations,
            @ToolParam(description = "Page number for inventory details. Minimum 1.", required = false) @Schema(minimum = "1") @Min(1) Integer page,
            @ToolParam(description = "Page size for inventory details. Range 1..100.", required = false) @Schema(minimum = "1", maximum = "100") @Min(1) @Max(100) Integer size,
            @ToolParam(description = "Recent operation limit. Range 1..100.", required = false) @Schema(minimum = "1", maximum = "100") @Min(1) @Max(100) Integer recentLimit) {
        return getWarehouseStatus(new WarehouseStatusRequest(warehouseId, warehouseQuery, includeInventoryDetails, includeRecentOperations, page, size, recentLimit));
    }

    public WarehouseStatusResponse getWarehouseStatus(WarehouseStatusRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("get_warehouse_status", () -> readService.getWarehouseStatus(request));
        } catch (WarehouseApiException e) {
            return WarehouseStatusResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            return WarehouseStatusResponse.error(ErrorMapper.unexpected());
        }
    }
    @Tool(name = "get_pallet_status", description = "Read pallet code status, current inventory position, assay information, flow cycles, and flow details without modifying warehouse data.")
    public PalletStatusResponse getPalletStatus(
            @ToolParam(description = "Pallet code. Length 1..100.") @Schema(minLength = 1, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(min = 1, max = 100) String code,
            @ToolParam(description = "Include current inventory position. Defaults to true.", required = false) Boolean includeInventory,
            @ToolParam(description = "Include resolved assay information. Defaults to true.", required = false) Boolean includeAssay,
            @ToolParam(description = "Include flow cycles and flow details. Defaults to true.", required = false) Boolean includeFlows,
            @ToolParam(description = "Optional flow cycle number. Minimum 1.", required = false) @Schema(minimum = "1") @Min(1) Integer cycleNo,
            @ToolParam(description = "Maximum flow cycles/details to return. Range 1..100.", required = false) @Schema(minimum = "1", maximum = "100") @Min(1) @Max(100) Integer flowLimit) {
        return getPalletStatus(new PalletStatusRequest(code, includeInventory, includeAssay, includeFlows, cycleNo, flowLimit));
    }

    public PalletStatusResponse getPalletStatus(PalletStatusRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("get_pallet_status", () -> readService.getPalletStatus(request));
        } catch (WarehouseApiException e) {
            return PalletStatusResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            return PalletStatusResponse.error(ErrorMapper.unexpected());
        }
    }

    @Tool(name = "get_assay_status", description = "Read an assay by id, or by product and production date, including judge result, failed metrics, and applied standard details.")
    public AssayStatusResponse getAssayStatus(
            @ToolParam(description = "Preferred assay id. When supplied, product fields are ignored.", required = false) @Schema(minimum = "1") @Min(1) Integer assayId,
            @ToolParam(description = "Product id used with productionDate.", required = false) @Schema(minimum = "1") @Min(1) Integer productId,
            @ToolParam(description = "Production date in ISO format yyyy-MM-dd. Required with productId or productQuery.", required = false) @Schema(minLength = 10, maxLength = 10) @Size(min = 10, max = 10) String productionDate,
            @ToolParam(description = "Product query used only when productId is absent. Length 1..100.", required = false) @Schema(minLength = 1, maxLength = 100) @Size(min = 1, max = 100) String productQuery,
            @ToolParam(description = "Include applied standard and failed metric details. Defaults to true.", required = false) Boolean includeStandardDetails) {
        return getAssayStatus(new AssayStatusRequest(assayId, productId, productionDate, productQuery, includeStandardDetails));
    }

    public AssayStatusResponse getAssayStatus(AssayStatusRequest request) {
        try {
            return WarehouseToolCallContext.withToolName("get_assay_status", () -> readService.getAssayStatus(request));
        } catch (WarehouseApiException e) {
            return AssayStatusResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            return AssayStatusResponse.error(ErrorMapper.unexpected());
        }
    }

    private static String safeLogValue(String value) {
        if (value == null) {
            return null;
        }
        String safe = value
                .replaceAll("(?i)authorization\\s*[:=]\\s*bearer\\s+[^\\s,;]+", "Authorization: <redacted>")
                .replaceAll("(?i)bearer\\s+[^\\s,;]+", "Bearer <redacted>")
                .replaceAll("(?i)(delegation[_-]?token|refresh[_-]?token|token|api[_-]?key|password|secret)\\s*[:=]\\s*[^\\s,;]+", "$1=<redacted>")
                .replaceAll("(?m)^\\s*at\\s+.+$", "<stack redacted>")
                .replaceAll("[\\r\\n\\t]+", " ");
        return safe.length() <= 200 ? safe : safe.substring(0, 200);
    }
}

