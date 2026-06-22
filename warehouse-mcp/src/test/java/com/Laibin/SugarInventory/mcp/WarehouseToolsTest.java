package com.Laibin.SugarInventory.mcp;

import com.Laibin.SugarInventory.mcp.client.WarehouseApiClient;
import com.Laibin.SugarInventory.mcp.config.WarehouseApiProperties;
import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryOverviewRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolveProductsRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolveWarehousesRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ResolutionStatus;
import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseStatusRequest;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseToolsTest {
    private static final String FORBIDDEN_WRITE_PATHS = "^(/api/in-stock/add|/api/out-stock/out|/api/out-stock/transferOut|/api/pallet-codes/tasks/confirm|/api/auto-inbound/.*/confirm)$";

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
    void convertsInventorySummaryPiecesFromPallets() {
        backend.enqueue(json(result("""
                {"id":1,"productName":"白冰糖","status":"成品","piecesPerPallet":25}
                """)));
        backend.enqueue(json(result("""
                {"total":1,"records":[{"warehouseId":3,"warehouseName":"A-01","productId":1,"productName":"白冰糖","totalQuantity":2,"totalWeight":50.5}]}
                """)));

        var response = tools.getInventoryOverview(new InventoryOverviewRequest(1, null, null, 1, 10));

        assertThat(response.error()).isNull();
        assertThat(response.summary().totalBoards()).isEqualTo(2);
        assertThat(response.summary().totalPieces()).isEqualTo(50);
        assertThat(response.records().getFirst().totalPieces()).isEqualTo(50);
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

    private void assertNoForbiddenWriteRequests() throws InterruptedException {
        int count = backend.getRequestCount();
        for (int i = 0; i < count; i++) {
            RecordedRequest request = backend.takeRequest(100, TimeUnit.MILLISECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).doesNotMatch(FORBIDDEN_WRITE_PATHS);
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


