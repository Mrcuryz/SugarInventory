package com.Laibin.SugarInventory.mcp.service;

import com.Laibin.SugarInventory.mcp.client.WarehouseApiClient;
import com.Laibin.SugarInventory.mcp.model.ToolModels;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewRecord;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewSummary;
import com.Laibin.SugarInventory.mcp.model.ToolModels.MatchType;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PageInfo;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductCandidate;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductResolutionResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolveProductsRequest;
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
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        return productResolution(candidates);
    }

    public WarehouseResolutionResponse resolveWarehouses(ResolveWarehousesRequest request) {
        ToolModels.ToolError validation = validateResolveWarehousesRequest(request);
        if (validation != null) {
            return WarehouseResolutionResponse.error(validation);
        }
        int limit = defaultLimit(request.limit());
        List<WarehouseCandidate> candidates = new ArrayList<>();
        if (isPositiveInteger(request.query())) {
            JsonNode warehouse = apiClient.getData("/api/warehouse/" + request.query().trim());
            if (isObject(warehouse)) {
                candidates.add(warehouseCandidate(warehouse, MatchType.EXACT_ID, 100));
            }
        }
        JsonNode data = apiClient.getData("/api/warehouse/query", Map.of("name", request.query().trim()));
        for (JsonNode item : asArray(data)) {
            WarehouseCandidate candidate = warehouseCandidate(item, matchType(request.query(), text(item, "warehouseName"), false),
                    matchScore(request.query(), text(item, "warehouseName"), false));
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

    private ProductResolutionResponse productResolution(List<ProductCandidate> candidates) {
        if (candidates.isEmpty()) {
            return new ProductResolutionResponse(ResolutionStatus.NOT_FOUND, false, List.of(), null);
        }
        if (hasSingleCertainProduct(candidates)) {
            return new ProductResolutionResponse(ResolutionStatus.UNIQUE, false, List.of(candidates.getFirst()), null);
        }
        return new ProductResolutionResponse(ResolutionStatus.AMBIGUOUS, true, candidates, null);
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

    private ProductCandidate productCandidate(JsonNode item, MatchType matchType, int matchScore) {
        return new ProductCandidate(
                intValue(item, "id", intValue(item, "productId", null)),
                text(item, "productName"),
                text(item, "productType"),
                text(item, "status", text(item, "productStatus")),
                text(item, "packagingMethod"),
                decimalValue(item, "weightPerPiece"),
                intValue(item, "piecesPerPallet", null),
                booleanValue(item, "canStack"),
                intValue(item, "screenMeshId", null),
                matchType,
                matchScore
        );
    }

    private WarehouseCandidate warehouseCandidate(JsonNode item, MatchType matchType, int matchScore) {
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
                matchScore
        );
    }

    private InventoryOverviewRecord inventoryRecord(JsonNode item, ProductCandidate product) {
        Integer totalQuantity = intValue(item, "totalQuantity", 0);
        Integer totalPieces = intValue(item, "totalPieces", null);
        if (totalPieces == null && product != null && product.piecesPerPallet() != null) {
            totalPieces = totalQuantity * product.piecesPerPallet();
        }
        return new InventoryOverviewRecord(
                intValue(item, "warehouseId", null),
                text(item, "warehouseName"),
                intValue(item, "productId", null),
                text(item, "productName"),
                text(item, "productStatus"),
                localDate(item, "entryDate"),
                totalQuantity,
                totalPieces,
                decimalValue(item, "totalWeight"),
                text(item, "stockInfo"),
                intValue(item, "warehouseCount", null)
        );
    }

    private InventoryOverviewSummary summarize(List<InventoryOverviewRecord> records) {
        Set<Integer> warehouseIds = new HashSet<>();
        Set<Integer> productIds = new HashSet<>();
        int boards = 0;
        int pieces = 0;
        BigDecimal weight = BigDecimal.ZERO;
        for (InventoryOverviewRecord record : records) {
            boards += nullToZero(record.totalQuantity());
            pieces += nullToZero(record.totalPieces());
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
        return new InventoryOverviewSummary(records.size(), boards, pieces, weight, warehouseIds.size(), productIds.size());
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
        return switch (matchType(query, value, code)) {
            case EXACT_ID, EXACT_CODE, EXACT_NAME -> 100;
            case NORMALIZED_NAME -> 95;
            case ALIAS -> 90;
            case PREFIX -> 80;
            case CONTAINS -> 60;
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

    private record WarehouseResolution(ResolutionStatus status, List<WarehouseCandidate> candidates) {
    }
}
