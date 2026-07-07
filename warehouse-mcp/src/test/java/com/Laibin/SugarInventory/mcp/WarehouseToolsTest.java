package com.Laibin.SugarInventory.mcp;

import com.Laibin.SugarInventory.mcp.client.WarehouseApiClient;
import com.Laibin.SugarInventory.mcp.config.WarehouseApiProperties;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AmbiguityType;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayLookupMode;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayStatusRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryDistributionFilter;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryDistributionRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.MatchType;
import com.Laibin.SugarInventory.mcp.model.ToolModels.OptionType;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductScope;
import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletStatusRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolveProductsRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolveWarehousesRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolutionStatus;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseStatusRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseScope;
import com.Laibin.SugarInventory.mcp.service.WarehouseReadService;
import com.Laibin.SugarInventory.mcp.tool.WarehouseTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseToolsTest {
    private static final String FORBIDDEN_WRITE_PATHS = ".*(/api/in-stock/add|/api/out-stock/out|/api/out-stock/transferOut|/api/pallet-codes/tasks/confirm|/api/pallet-codes/invalid|/api/pallet-codes/invalid/restore|/api/pallet-codes/bind|/api/pallet-codes/fixed-product/bind|/api/pallet-codes/.*/confirm|/api/pallet-codes/.*/create|/api/auto-inbound/.*/confirm|/api/assay/import).*";

    private MockWebServer backend;
    private WarehouseTools tools;

    @BeforeEach
    void setUp() throws IOException {
        backend = new MockWebServer();
        backend.start();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        WarehouseApiClient apiClient = new WarehouseApiClient(
                new WarehouseApiProperties(backend.url("/").toString(), "test-token", Duration.ofMillis(200)),
                objectMapper
        );
        tools = new WarehouseTools(new WarehouseReadService(apiClient));
    }

    @AfterEach
    void tearDown() throws IOException {
        backend.shutdown();
    }

    @Test
    void resolvesProductUniqueExactMatch() {
        backend.enqueue(json(result("""
                [{"id":1,"productName":"白冰糖","productType":"白冰糖","status":"成品","piecesPerPallet":25}]
                """)));

        var response = tools.resolveProducts(new ResolveProductsRequest("白冰糖", null, null, 10));

        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.UNIQUE);
        assertThat(response.needsUserSelection()).isFalse();
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().getFirst().matchScore()).isEqualTo(100);
    }

    @Test
    void resolvesProductAmbiguousCandidates() {
        backend.enqueue(json(result("""
                [
                  {"id":1,"productName":"白冰糖一号","status":"成品"},
                  {"id":2,"productName":"白冰糖二号","status":"成品"}
                ]
                """)));

        var response = tools.resolveProducts(new ResolveProductsRequest("白冰糖", null, null, 10));

        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.AMBIGUOUS);
        assertThat(response.needsUserSelection()).isTrue();
        assertThat(response.candidates()).hasSize(2);
    }

    @Test
    void resolvesProductNotFound() {
        backend.enqueue(json(result("[]")));

        var response = tools.resolveProducts(new ResolveProductsRequest("不存在产品", null, null, 10));

        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.NOT_FOUND);
        assertThat(response.candidates()).isEmpty();
    }

    @Test
    void resolvesWarehouseUniqueExactMatch() {
        backend.enqueue(json(result("""
                [{"id":3,"warehouseName":"A-01","status":"正常","maxCapacity":10,"curCapacity":2}]
                """)));

        var response = tools.resolveWarehouses(new ResolveWarehousesRequest("A-01", false, 10));

        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.UNIQUE);
        assertThat(response.candidates().getFirst().warehouseId()).isEqualTo(3);
        assertThat(response.candidates().getFirst().freeCapacity()).isEqualTo(8);
    }

    @Test
    void resolvesWarehouseAmbiguousCandidates() {
        backend.enqueue(json(result("""
                [
                  {"id":3,"warehouseName":"A-01","status":"正常"},
                  {"id":4,"warehouseName":"A-02","status":"正常"}
                ]
                """)));

        var response = tools.resolveWarehouses(new ResolveWarehousesRequest("A", false, 10));

        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.AMBIGUOUS);
        assertThat(response.needsUserSelection()).isTrue();
    }

    @Test
    void resolvesWarehouseNaturalSlotName() throws InterruptedException {
        assertNaturalWarehouseName("2号库位", "2");
    }

    @Test
    void resolvesWarehouseChineseNumeralSlotName() throws InterruptedException {
        assertNaturalWarehouseName("二号库位", "2");
    }

    @Test
    void resolvesWarehouseTwoDigitSlotName() throws InterruptedException {
        assertNaturalWarehouseName("12号库位", "12");
    }

    @Test
    void resolvesWarehousePrefixSlotName() throws InterruptedException {
        assertNaturalWarehouseName("库位2", "2");
    }

    @Test
    void normalizedWarehouseNumberIsNotUsedAsWarehouseId() throws InterruptedException {
        backend.enqueue(json(result("""
                [{"id":9,"warehouseName":"2","status":"正常"}]
                """)));

        var response = tools.resolveWarehouses(new ResolveWarehousesRequest("2号库位", false, 10));

        assertThat(response.error()).isNull();
        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.UNIQUE);
        RecordedRequest request = backend.takeRequest(100, TimeUnit.MILLISECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).startsWith("/api/warehouse/query");
        assertThat(request.getPath()).doesNotStartWith("/api/warehouse/2");
    }

    @Test
    void resolvesProductAmbiguityWithHumanOptions() {
        backend.enqueue(json(result("""
                [
                  {"id":84,"productName":"黄冰糖（袋）","productType":"黄冰糖","status":"成品","weightPerPiece":25,"piecesPerPallet":40},
                  {"id":123,"productName":"黄冰糖（9.6箱装）","productType":"黄冰糖","status":"成品","weightPerPiece":9.6,"piecesPerPallet":100}
                ]
                """)));

        var response = tools.resolveProducts(new ResolveProductsRequest("黄冰糖", null, null, 10));

        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.AMBIGUOUS);
        assertThat(response.needsUserSelection()).isTrue();
        assertThat(response.ambiguityType()).isEqualTo(AmbiguityType.PRODUCT_SCOPE);
        assertThat(response.clarificationPrompt()).contains("黄冰糖");
        assertThat(response.options()).anySatisfy(option -> {
            assertThat(option.optionType()).isEqualTo(OptionType.PRODUCT_TYPE_GROUP);
            assertThat(option.supported()).isTrue();
        });
        assertThat(response.options()).anySatisfy(option -> {
            assertThat(option.optionType()).isEqualTo(OptionType.EXACT_PRODUCT_NAME_GROUP);
            assertThat(option.supported()).isTrue();
        });
        assertThat(response.options()).filteredOn(option -> option.optionType() == OptionType.SINGLE_PRODUCT)
                .hasSize(2)
                .allSatisfy(option -> assertThat(option.supported()).isTrue());
    }

    @Test
    void ambiguousProductQueryDoesNotAutoLoadInventoryOverview() throws InterruptedException {
        backend.enqueue(json(result("""
                [
                  {"id":84,"productName":"黄冰糖（袋）","productType":"黄冰糖","status":"成品"},
                  {"id":123,"productName":"黄冰糖（9.6箱装）","productType":"黄冰糖","status":"成品"}
                ]
                """)));

        var response = tools.getInventoryOverview(new InventoryOverviewRequest(null, "黄冰糖", null, 1, 10));

        assertThat(response.error().code()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.error().field()).isEqualTo("productQuery");
        assertThat(backend.getRequestCount()).isEqualTo(1);
        RecordedRequest request = backend.takeRequest(100, TimeUnit.MILLISECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).startsWith("/api/products/product");
        assertThat(request.getPath()).doesNotStartWith("/api/inventory/stock/page");
    }
    @Test
    void resolvesWarehouseNotFound() {
        backend.enqueue(json(result("[]")));

        var response = tools.resolveWarehouses(new ResolveWarehousesRequest("不存在库位", false, 10));

        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.NOT_FOUND);
    }

    @Test
    void mapsBackend401() {
        backend.enqueue(new MockResponse().setResponseCode(401).setBody("{}"));

        var response = tools.resolveProducts(new ResolveProductsRequest("白冰糖", null, null, 10));

        assertThat(response.error().code()).isEqualTo("UPSTREAM_UNAUTHORIZED");
        assertThat(response.error().upstreamStatus()).isEqualTo(401);
    }

    @Test
    void mapsBackend403() {
        backend.enqueue(new MockResponse().setResponseCode(403).setBody("{}"));

        var response = tools.resolveProducts(new ResolveProductsRequest("白冰糖", null, null, 10));

        assertThat(response.error().code()).isEqualTo("UPSTREAM_PERMISSION_DENIED");
        assertThat(response.error().upstreamStatus()).isEqualTo(403);
    }

    @Test
    void mapsBackendTimeout() {
        backend.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        var response = tools.resolveProducts(new ResolveProductsRequest("白冰糖", null, null, 10));

        assertThat(response.error().code()).isEqualTo("UPSTREAM_TIMEOUT");
        assertThat(response.error().retryable()).isTrue();
    }

    @Test
    void mapsBackend500() {
        backend.enqueue(new MockResponse().setResponseCode(500).setBody("{\"msg\":\"boom\"}"));

        var response = tools.resolveProducts(new ResolveProductsRequest("白冰糖", null, null, 10));

        assertThat(response.error().code()).isEqualTo("UPSTREAM_SERVER_ERROR");
        assertThat(response.error().upstreamStatus()).isEqualTo(500);
    }

    @Test
    void rejectsIllegalPagination() {
        var response = tools.getInventoryOverview(new InventoryOverviewRequest(null, null, null, 0, 101));

        assertThat(response.error().code()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.error().field()).isEqualTo("page");
        assertThat(backend.getRequestCount()).isZero();
    }

    @Test
    void rejectsBlankQueryString() {
        var response = tools.resolveProducts(new ResolveProductsRequest("   ", null, null, 10));

        assertThat(response.error().code()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.error().field()).isEqualTo("query");
        assertThat(backend.getRequestCount()).isZero();
    }

    @Test
    void rejectsIllegalLimit() {
        var response = tools.resolveProducts(new ResolveProductsRequest("白冰糖", null, null, 101));

        assertThat(response.error().code()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.error().field()).isEqualTo("limit");
        assertThat(backend.getRequestCount()).isZero();
    }

    @Test
    void exposesRawAndNormalizedInventoryQuantities() {
        backend.enqueue(json(result("""
                {"id":84,"productName":"黄冰糖（袋）","status":"成品","weightPerPiece":25,"piecesPerPallet":40}
                """)));
        backend.enqueue(json(result("""
                {"total":1,"records":[{"warehouseId":3,"warehouseName":"A-01","productId":84,"productName":"黄冰糖（袋）","totalQuantity":10,"totalPieces":70,"totalWeight":11750,"stockInfo":"11板30件"}]}
                """)));

        var response = tools.getInventoryOverview(new InventoryOverviewRequest(84, null, null, 1, 10));

        assertThat(response.error()).isNull();
        assertThat(response.summary().rawFullPallets()).isEqualTo(10);
        assertThat(response.summary().rawLoosePieces()).isEqualTo(70);
        assertThat(response.summary().normalizedPallets()).isEqualTo(11);
        assertThat(response.summary().normalizedLoosePieces()).isEqualTo(30);
        assertThat(response.summary().totalEquivalentPieces()).isEqualTo(470);
        assertThat(response.summary().displayStockInfo()).isEqualTo("11板30件");
        assertThat(response.summary().calculationNote()).contains("rawFullPallets=10", "rawLoosePieces=70", "piecesPerPallet=40", "11板30件");
        assertThat(response.records().getFirst().rawFullPallets()).isEqualTo(10);
        assertThat(response.records().getFirst().rawLoosePieces()).isEqualTo(70);
        assertThat(response.records().getFirst().normalizedPallets()).isEqualTo(11);
        assertThat(response.records().getFirst().normalizedLoosePieces()).isEqualTo(30);
        assertThat(response.records().getFirst().totalEquivalentPieces()).isEqualTo(470);
        assertThat(response.records().getFirst().displayStockInfo()).isEqualTo("11板30件");
        assertThat(response.records().getFirst().calculationNote()).contains("totalEquivalentPieces=470");
    }

    @Test
    void handlesBackendEmptyData() {
        backend.enqueue(json(result("""
                {"total":0,"records":[]}
                """)));

        var response = tools.getInventoryOverview(new InventoryOverviewRequest(null, null, null, 1, 10));

        assertThat(response.error()).isNull();
        assertThat(response.records()).isEmpty();
        assertThat(response.summary().totalRecords()).isZero();
    }

    @Test
    void getsWarehouseStatusSuccessPath() throws InterruptedException {
        backend.enqueue(json(result("""
                {"id":3,"warehouseName":"A-01","status":"正常","maxCapacity":10,"curCapacity":2,"maxRows":5}
                """)));
        backend.enqueue(json(result("""
                [{"warehouseId":3,"warehouseName":"A-01","status":"正常","curCapacity":2,"maxCapacity":10,"capacityPercentage":20,"maxRows":5,"currentPalletCount":2,"currentProductCount":1}]
                """)));
        backend.enqueue(json(result("""
                {"total":1,"records":[{"inventoryId":9,"productId":1,"productName":"白冰糖","warehouseName":"A-01","quantity":2,"pieces":50}]}
                """)));
        backend.enqueue(json(result("""
                [{"operationType":"IN","operationName":"入库","productName":"白冰糖"}]
                """)));

        var response = tools.getWarehouseStatus(new WarehouseStatusRequest(3, null, true, true, 1, 10, 5));

        assertThat(response.error()).isNull();
        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.UNIQUE);
        assertThat(response.warehouse().warehouseId()).isEqualTo(3);
        assertThat(response.capacity().currentPalletCount()).isEqualTo(2);
        assertThat(response.inventoryDetails()).hasSize(1);
        assertThat(response.recentOperations()).hasSize(1);
        assertNoForbiddenWriteRequests();
    }

    @Test
    void returnsAmbiguousWarehouseStatusCandidates() {
        backend.enqueue(json(result("""
                [
                  {"id":3,"warehouseName":"2号库位-A","status":"正常"},
                  {"id":4,"warehouseName":"2号库位-B","status":"正常"}
                ]
                """)));

        var response = tools.getWarehouseStatus(new WarehouseStatusRequest(null, "2号库位", false, false, 1, 10, 5));

        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.AMBIGUOUS);
        assertThat(response.needsUserSelection()).isTrue();
        assertThat(response.warehouseCandidates()).hasSize(2);
        assertThat(response.error().field()).isEqualTo("warehouseQuery");
    }

    @Test
    void returnsWarehouseStatusNotFound() {
        backend.enqueue(json(result("[]")));

        var response = tools.getWarehouseStatus(new WarehouseStatusRequest(null, "不存在库位", false, false, 1, 10, 5));

        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.NOT_FOUND);
        assertThat(response.error().message()).contains("did not match");
    }

    @Test
    void doesNotPassBackendMsgToAgent() {
        backend.enqueue(json("""
                {"code":500,"msg":"Authorization: Bearer SECRET select * from user at com.example.Service /var/app/db.yml"}
                """));

        var response = tools.resolveProducts(new ResolveProductsRequest("白冰糖", null, null, 10));

        assertThat(response.error().code()).isEqualTo("UPSTREAM_SERVER_ERROR");
        assertThat(response.error().message()).isEqualTo("Warehouse backend failed while serving the read request.");
        assertThat(response.error().message()).doesNotContain("SECRET", "select", "/var/app", "Authorization");
    }

    @Test
    void neverRequestsForbiddenWriteEndpoints() throws InterruptedException {
        backend.enqueue(json(result("""
                [{"id":1,"productName":"白冰糖","status":"成品"}]
                """)));
        backend.enqueue(json(result("""
                {"total":0,"records":[]}
                """)));
        backend.enqueue(json(result("""
                {"id":3,"warehouseName":"A-01","status":"正常"}
                """)));
        backend.enqueue(json(result("[]")));

        tools.resolveProducts(new ResolveProductsRequest("白冰糖", null, null, 10));
        tools.getInventoryOverview(new InventoryOverviewRequest(null, null, null, 1, 10));
        tools.getWarehouseStatus(new WarehouseStatusRequest(3, null, false, false, 1, 10, 5));

        assertNoForbiddenWriteRequests();
    }


    @Test
    void getsPalletStatusParseOnly() throws InterruptedException {
        backend.enqueue(json(result("""
                {"id":7,"code":"P20260613001","status":"NORMAL","productName":"黄冰糖（袋）"}
                """)));

        var response = tools.getPalletStatus(new PalletStatusRequest("P20260613001", false, false, false, null, 20));

        assertThat(response.error()).isNull();
        assertThat(response.partial()).isFalse();
        assertThat(response.palletInfo().path("code").asText()).isEqualTo("P20260613001");
        assertThat(backend.getRequestCount()).isEqualTo(1);
        RecordedRequest request = backend.takeRequest(100, TimeUnit.MILLISECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).startsWith("/api/pallet-codes/parse");
    }

    @Test
    void getsPalletStatusWithInventory() {
        backend.enqueue(json(result("""
                {"id":7,"code":"P1","status":"NORMAL"}
                """)));
        backend.enqueue(json(result("""
                {"warehouseName":"2","side":"A","rowNumber":1,"layer":2,"quantity":1,"unit":false}
                """)));

        var response = tools.getPalletStatus(new PalletStatusRequest("P1", true, false, false, null, 20));

        assertThat(response.error()).isNull();
        assertThat(response.inventory().path("warehouseName").asText()).isEqualTo("2");
        assertThat(response.partial()).isFalse();
    }

    @Test
    void getsPalletStatusWithAssay() {
        backend.enqueue(json(result("""
                {"id":7,"code":"P1","status":"NORMAL"}
                """)));
        backend.enqueue(json(result("""
                {"id":66,"resolveStatus":"FOUND","isQualified":true,"testerName":"qa"}
                """)));

        var response = tools.getPalletStatus(new PalletStatusRequest("P1", false, true, false, null, 20));

        assertThat(response.error()).isNull();
        assertThat(response.assay().path("id").asInt()).isEqualTo(66);
        assertThat(response.partial()).isFalse();
    }

    @Test
    void getsPalletStatusWithFlows() {
        backend.enqueue(json(result("""
                {"id":7,"code":"P1","status":"NORMAL"}
                """)));
        backend.enqueue(json(result("""
                {"total":1,"records":[{"cycleNo":2,"productName":"黄冰糖（袋）"}]}
                """)));
        backend.enqueue(json(result("""
                [{"cycleNo":2,"flowType":"IN","warehouseName":"2"}]
                """)));

        var response = tools.getPalletStatus(new PalletStatusRequest("P1", false, false, true, null, 20));

        assertThat(response.error()).isNull();
        assertThat(response.flowCyclePage().total()).isEqualTo(1);
        assertThat(response.flowCycles()).hasSize(1);
        assertThat(response.flows()).hasSize(1);
        assertThat(response.partial()).isFalse();
    }

    @Test
    void returnsPartialWhenOptionalPalletSubqueryFails() {
        backend.enqueue(json(result("""
                {"id":7,"code":"P1","status":"NORMAL"}
                """)));
        backend.enqueue(json("""
                {"code":404,"msg":"inventory missing internal detail"}
                """));

        var response = tools.getPalletStatus(new PalletStatusRequest("P1", true, false, false, null, 20));

        assertThat(response.error()).isNull();
        assertThat(response.partial()).isTrue();
        assertThat(response.warnings()).anyMatch(warning -> warning.contains("inventory"));
        assertThat(response.inventory()).isNull();
    }

    @Test
    void mapsPalletStatus401() {
        backend.enqueue(new MockResponse().setResponseCode(401).setBody("{}"));

        var response = tools.getPalletStatus(new PalletStatusRequest("P1", false, false, false, null, 20));

        assertThat(response.error().code()).isEqualTo("UPSTREAM_UNAUTHORIZED");
    }

    @Test
    void mapsPalletStatus403() {
        backend.enqueue(new MockResponse().setResponseCode(403).setBody("{}"));

        var response = tools.getPalletStatus(new PalletStatusRequest("P1", false, false, false, null, 20));

        assertThat(response.error().code()).isEqualTo("UPSTREAM_PERMISSION_DENIED");
    }

    @Test
    void mapsPalletStatus500() {
        backend.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));

        var response = tools.getPalletStatus(new PalletStatusRequest("P1", false, false, false, null, 20));

        assertThat(response.error().code()).isEqualTo("UPSTREAM_SERVER_ERROR");
    }

    @Test
    void mapsPalletStatusTimeout() {
        backend.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        var response = tools.getPalletStatus(new PalletStatusRequest("P1", false, false, false, null, 20));

        assertThat(response.error().code()).isEqualTo("UPSTREAM_TIMEOUT");
        assertThat(response.error().retryable()).isTrue();
    }

    @Test
    void getsAssayStatusById() {
        backend.enqueue(json(result("""
                {"id":99,"productId":84,"judgeResult":"QUALIFIED","failedMetrics":[],"appliedStandard":{"id":5,"name":"成品标准"}}
                """)));

        var response = tools.getAssayStatus(new AssayStatusRequest(99, null, null, null, true));

        assertThat(response.error()).isNull();
        assertThat(response.lookupMode()).isEqualTo(AssayLookupMode.ASSAY_ID);
        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.UNIQUE);
        assertThat(response.needsAssay()).isFalse();
        assertThat(response.judgeResult()).isEqualTo("QUALIFIED");
        assertThat(response.appliedStandard().path("name").asText()).isEqualTo("成品标准");
    }

    @Test
    void getsAssayStatusByProductDate() throws InterruptedException {
        backend.enqueue(json(result("""
                {"id":100,"productId":84,"judgeResult":"UNQUALIFIED","failedMetrics":[{"metric":"colorValue"}],"appliedStandard":{"id":5}}
                """)));

        var response = tools.getAssayStatus(new AssayStatusRequest(null, 84, "2026-06-13", null, true));

        assertThat(response.error()).isNull();
        assertThat(response.lookupMode()).isEqualTo(AssayLookupMode.PRODUCT_DATE);
        assertThat(response.failedMetrics()).hasSize(1);
        RecordedRequest request = backend.takeRequest(100, TimeUnit.MILLISECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).startsWith("/api/assay/by-product-date");
    }

    @Test
    void assayStatusProductQueryAmbiguousReturnsAmbiguous() {
        backend.enqueue(json(result("""
                [
                  {"id":84,"productName":"黄冰糖（袋）","productType":"黄冰糖","status":"成品"},
                  {"id":123,"productName":"黄冰糖（9.6箱装）","productType":"黄冰糖","status":"成品"}
                ]
                """)));

        var response = tools.getAssayStatus(new AssayStatusRequest(null, null, "2026-06-13", "黄冰糖", true));

        assertThat(response.error()).isNull();
        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.AMBIGUOUS);
        assertThat(response.needsUserSelection()).isTrue();
        assertThat(response.productResolution()).isNotNull();
        assertThat(backend.getRequestCount()).isEqualTo(1);
    }

    @Test
    void assayStatusNotFoundNeedsAssay() {
        backend.enqueue(json(result("null")));

        var response = tools.getAssayStatus(new AssayStatusRequest(null, 84, "2026-06-13", null, true));

        assertThat(response.error()).isNull();
        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.NOT_FOUND);
        assertThat(response.needsAssay()).isTrue();
        assertThat(response.warnings()).anyMatch(warning -> warning.contains("No assay"));
    }

    @Test
    void newReadToolsNeverRequestForbiddenWriteEndpoints() throws InterruptedException {
        backend.enqueue(json(result("""
                {"id":7,"code":"P1","status":"NORMAL"}
                """)));
        backend.enqueue(json(result("""
                {"warehouseName":"2"}
                """)));
        backend.enqueue(json(result("""
                {"id":66,"resolveStatus":"FOUND"}
                """)));
        backend.enqueue(json(result("""
                {"total":0,"records":[]}
                """)));
        backend.enqueue(json(result("""
                {"id":99,"judgeResult":"QUALIFIED"}
                """)));

        tools.getPalletStatus(new PalletStatusRequest("P1", true, true, true, null, 20));
        tools.getAssayStatus(new AssayStatusRequest(99, null, null, null, true));

        assertNoForbiddenWriteRequests();
    }

    @Test
    void sendsStaticTokenAndToolNameHeaders() throws InterruptedException {
        backend.enqueue(json(result("""
                [{"id":1,"productName":"白冰糖","status":"成品"}]
                """)));

        var response = tools.resolveProducts(new ResolveProductsRequest("白冰糖", null, null, 10));

        assertThat(response.error()).isNull();
        RecordedRequest request = backend.takeRequest(100, TimeUnit.MILLISECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token");
        assertThat(request.getHeader("X-Agent-Tool-Name")).isEqualTo("resolve_products");
        assertThat(request.getHeader("X-Agent-Session-Id")).isNull();
    }

    @Test
    void inventoryDistributionForwardsControlledMultiScopeFilters() throws InterruptedException {
        backend.enqueue(json(result("""
                {
                  "scopeLabel":"全部黄冰糖大类",
                  "productLabel":"全部黄冰糖大类",
                  "groupBy":"warehouse_product",
                  "totalStockText":"700件（跨规格）",
                  "totalEquivalentPieces":700,
                  "warehouseCount":3,
                  "productCount":2,
                  "palletCount":14,
                  "groups":[],
                  "notes":[]
                }
                """)));
        InventoryDistributionRequest request = new InventoryDistributionRequest(
                new ProductScope("PRODUCT_TYPE_GROUP", null, null, "黄冰糖"),
                new WarehouseScope("SINGLE_WAREHOUSE", 2),
                new InventoryDistributionFilter(
                        List.of("成品"), List.of("正常"), List.of("INSTOCK"), "PASS",
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7)),
                "warehouse_product",
                50
        );

        var response = tools.getInventoryDistribution(request);

        assertThat(response.error()).isNull();
        assertThat(response.groupBy()).isEqualTo("warehouse_product");
        RecordedRequest recorded = backend.takeRequest(100, TimeUnit.MILLISECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getPath()).isEqualTo("/api/inventory/distribution");
        String body = recorded.getBody().readUtf8();
        assertThat(body).contains("\"type\":\"PRODUCT_TYPE_GROUP\"");
        assertThat(body).contains("\"productType\":\"黄冰糖\"");
        assertThat(body).contains("\"warehouseId\":2");
        assertThat(body).contains("\"assayStatus\":\"PASS\"");
        assertThat(body).doesNotContain("sql", "http");
    }

    @Test
    void inventoryDistributionRejectsUnsupportedFilterBeforeBackendCall() {
        InventoryDistributionRequest request = new InventoryDistributionRequest(
                new ProductScope("ALL", null, null, null),
                new WarehouseScope("ALL", null),
                new InventoryDistributionFilter(List.of("已删除"), List.of(), List.of(), null, null, null),
                "warehouse",
                20
        );

        var response = tools.getInventoryDistribution(request);

        assertThat(response.error()).isNotNull();
        assertThat(response.error().code()).isEqualTo("INVALID_ARGUMENT");
        assertThat(backend.getRequestCount()).isZero();

        var missingScopeType = tools.getInventoryDistribution(new InventoryDistributionRequest(
                new ProductScope(null, null, null, null),
                new WarehouseScope("ALL", null),
                null,
                "warehouse",
                20
        ));
        assertThat(missingScopeType.error().code()).isEqualTo("INVALID_ARGUMENT");
        assertThat(backend.getRequestCount()).isZero();
    }

    @Test
    void delegatedTokenTakesPrecedenceAndSendsAgentSessionHeader() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        WarehouseApiClient delegatedClient = new WarehouseApiClient(
                new WarehouseApiProperties(backend.url("/").toString(), "static-token", "delegated-token", "agent-session-1", Duration.ofMillis(200)),
                objectMapper
        );
        WarehouseTools delegatedTools = new WarehouseTools(new WarehouseReadService(delegatedClient));
        backend.enqueue(json(result("""
                [{"id":3,"warehouseName":"A-01","status":"正常"}]
                """)));

        var response = delegatedTools.resolveWarehouses(new ResolveWarehousesRequest("A-01", false, 10));

        assertThat(response.error()).isNull();
        RecordedRequest request = backend.takeRequest(100, TimeUnit.MILLISECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer delegated-token");
        assertThat(request.getHeader("X-Agent-Session-Id")).isEqualTo("agent-session-1");
        assertThat(request.getHeader("X-Agent-Tool-Name")).isEqualTo("resolve_warehouses");
    }
    private void assertNaturalWarehouseName(String query, String warehouseName) throws InterruptedException {
        backend.enqueue(json(result("[{\"id\":2,\"warehouseName\":\"" + warehouseName + "\",\"status\":\"正常\"}]")));

        var response = tools.resolveWarehouses(new ResolveWarehousesRequest(query, false, 10));

        assertThat(response.error()).isNull();
        assertThat(response.resolutionStatus()).isEqualTo(ResolutionStatus.UNIQUE);
        assertThat(response.candidates().getFirst().warehouseName()).isEqualTo(warehouseName);
        assertThat(response.candidates().getFirst().matchType()).isEqualTo(MatchType.NORMALIZED_NAME);
        assertThat(response.candidates().getFirst().matchReason()).contains("归一化为 " + warehouseName);
        RecordedRequest request = backend.takeRequest(100, TimeUnit.MILLISECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).startsWith("/api/warehouse/query");
        assertThat(request.getPath()).doesNotStartWith("/api/warehouse/" + warehouseName);
    }
    private void assertNoForbiddenWriteRequests() throws InterruptedException {
        int count = backend.getRequestCount();
        for (int i = 0; i < count; i++) {
            RecordedRequest request = backend.takeRequest(100, TimeUnit.MILLISECONDS);
            assertThat(request).isNotNull();
            String requestLine = request.getMethod() + " " + request.getPath();
            assertThat(requestLine).doesNotMatch(FORBIDDEN_WRITE_PATHS);
            assertThat(requestLine).doesNotMatch("^(POST|PUT|PATCH|DELETE) /api/assay(/.*)?(\\?.*)?$");
            assertThat(requestLine).doesNotMatch("^(POST|PUT|PATCH|DELETE) /api/quality-standard.*$");
            assertThat(requestLine).doesNotMatch("^(POST|PUT|PATCH|DELETE) /api/quality-standards.*$");
        }
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body);
    }

    private static String result(String dataJson) {
        return "{\"code\":200,\"msg\":\"success\",\"data\":" + dataJson + "}";
    }
}

