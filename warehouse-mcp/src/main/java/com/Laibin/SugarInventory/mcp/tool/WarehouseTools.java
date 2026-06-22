package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.client.WarehouseApiException;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductResolutionResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolveProductsRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolveWarehousesRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseResolutionResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseStatusRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseStatusResponse;
import com.Laibin.SugarInventory.mcp.service.ErrorMapper;
import com.Laibin.SugarInventory.mcp.service.WarehouseReadService;
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
            return readService.resolveProducts(request);
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
            return readService.resolveWarehouses(request);
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
            return readService.getInventoryOverview(request);
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
            return readService.getWarehouseStatus(request);
        } catch (WarehouseApiException e) {
            return WarehouseStatusResponse.error(ErrorMapper.upstream(e));
        } catch (RuntimeException e) {
            return WarehouseStatusResponse.error(ErrorMapper.unexpected());
        }
    }
}


