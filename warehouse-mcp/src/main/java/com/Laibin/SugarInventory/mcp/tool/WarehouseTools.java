package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.client.WarehouseApiException;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayStatusRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayStatusResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletStatusRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletStatusResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductResolutionResponse;
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
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class WarehouseTools {
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
}

