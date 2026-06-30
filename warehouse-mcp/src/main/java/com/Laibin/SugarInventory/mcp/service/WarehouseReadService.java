package com.Laibin.SugarInventory.mcp.service;

import com.Laibin.SugarInventory.mcp.client.WarehouseApiClient;
import com.Laibin.SugarInventory.mcp.client.WarehouseApiException;
import com.Laibin.SugarInventory.mcp.model.ToolModels;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AmbiguityType;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayLookupMode;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayStatusRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayStatusResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewRecord;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewSummary;
import com.Laibin.SugarInventory.mcp.model.ToolModels.MatchType;
import com.Laibin.SugarInventory.mcp.model.ToolModels.OptionType;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PageInfo;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletStatusRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletStatusResponse;
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
                        false,
                        "当前库存概览尚不支持按产品大类聚合查询，请让用户选择具体产品规格。"
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
                    false,
                    "当前库存概览尚不支持按产品名称组聚合查询，请让用户选择具体产品规格。"
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
        if (candidate.productId() != null) {
            label.append(" (#").append(candidate.productId()).append(")");
        }
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
