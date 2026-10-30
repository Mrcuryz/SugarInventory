package com.laibin.sugarinventory.dt50;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ApiClient {
    interface Callback<T> {
        void onSuccess(T value);
        void onError(String message, int httpStatus);
    }

    static final class LoginResult {
        final String token;
        final String name;
        final List<String> permissionCodes;

        LoginResult(String token, String name, List<String> permissionCodes) {
            this.token = token;
            this.name = name;
            this.permissionCodes = permissionCodes;
        }
    }

    static final class CurrentUser {
        final String name;
        final List<String> permissionCodes;

        CurrentUser(String name, List<String> permissionCodes) {
            this.name = name;
            this.permissionCodes = permissionCodes;
        }
    }

    private final String baseUrl;
    private final String token;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    ApiClient(String baseUrl, String token) {
        this.baseUrl = AppPreferences.normalizeServerUrl(baseUrl);
        this.token = token == null ? "" : token.trim();
    }

    void login(String name, String password, Callback<LoginResult> callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("password", password);
        } catch (JSONException exception) {
            callback.onError("登录参数无效", 0);
            return;
        }
        run(callback, () -> {
            JSONObject data = request("POST", "/api/auth/web-login", body);
            return new LoginResult(
                    data.getString("token"),
                    data.optString("name", name),
                    stringList(data.optJSONArray("permissionCodes")));
        });
    }

    void loadCurrentUser(Callback<CurrentUser> callback) {
        run(callback, () -> {
            JSONObject data = request("GET", "/api/user/info", null);
            return new CurrentUser(
                    data.optString("name", ""),
                    stringList(data.optJSONArray("permissionCodes")));
        });
    }

    void resolveWarehouse(String rawCode, Callback<WarehouseLocation> callback) {
        run(callback, () -> {
            String encoded = urlEncode(rawCode.trim());
            JSONObject data = request("GET", "/api/warehouse/location-code/resolve?code=" + encoded, null);
            return new WarehouseLocation(
                    data.getString("code"),
                    data.getInt("warehouseId"),
                    data.getString("warehouseName"),
                    data.optString("status", ""),
                    data.optInt("curCapacity", 0),
                    data.optInt("maxCapacity", 0));
        });
    }

    void resolveInboundTask(String rawCode, Callback<PalletTaskInfo> callback) {
        run(callback, () -> {
            String normalized = ScanCodeClassifier.normalizePalletInput(rawCode);
            String encoded = urlEncode(normalized);
            JSONObject pallet = request("GET", "/api/pallet-codes/parse?code=" + encoded, null);
            String canonicalCode = pallet.getString("code");
            if (!"PENDING".equalsIgnoreCase(pallet.optString("status"))) {
                throw new ApiFailure("二维码 " + canonicalCode + " 目前不能办理入库", 200);
            }

            JSONObject query = new JSONObject();
            query.put("code", canonicalCode);
            query.put("taskType", "IN");
            query.put("status", "PENDING");
            query.put("pageNum", 1);
            query.put("pageSize", 2);
            JSONObject page = request("POST", "/api/pallet-codes/tasks/list", query);
            JSONArray records = page.optJSONArray("records");
            if (records == null || records.length() == 0) {
                throw new ApiFailure("二维码 " + canonicalCode + " 没有可办理的入库记录", 200);
            }
            if (records.length() > 1) {
                throw new ApiFailure("二维码 " + canonicalCode + " 有多条待入库记录，请在管理端核对", 200);
            }
            JSONObject task = records.getJSONObject(0);
            Integer quantity = task.isNull("productionOutputQuantity")
                    ? null : task.getInt("productionOutputQuantity");
            return new PalletTaskInfo(
                    canonicalCode,
                    task.optString("productName", pallet.optString("productName", "未命名产品")),
                    task.optString("productionDate", "未记录"),
                    quantity,
                    task.optString("productionOutputUnit", "0"));
        });
    }

    void confirmInbound(String warehouseName, List<String> palletCodes, Callback<Integer> callback) {
        run(callback, () -> {
            JSONArray items = new JSONArray();
            for (String palletCode : palletCodes) {
                JSONObject item = new JSONObject();
                item.put("code", palletCode);
                item.put("warehouseName", warehouseName);
                items.put(item);
            }
            JSONObject body = new JSONObject();
            body.put("items", items);
            JSONArray results = requestArray("POST", "/api/pallet-codes/tasks/confirm", body);
            return results.length();
        });
    }

    void loadWarehouseOverview(Callback<List<WarehouseCapacityItem>> callback) {
        run(callback, () -> {
            JSONArray rows = requestArray("GET", "/api/inventory/warehouses", null);
            List<WarehouseCapacityItem> result = new ArrayList<>();
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.getJSONObject(index);
                result.add(new WarehouseCapacityItem(
                        row.optInt("warehouseId"),
                        jsonText(row, "warehouseName", "未命名库位"),
                        jsonText(row, "status", "未记录"),
                        row.optDouble("curCapacity", 0),
                        row.optDouble("maxCapacity", 0),
                        row.optDouble("capacityPercentage", 0),
                        row.optInt("currentPalletCount", 0),
                        row.optInt("currentProductCount", 0)));
            }
            return result;
        });
    }

    void loadWarehouseDetail(int warehouseId, Callback<WarehouseDetail> callback) {
        run(callback, () -> {
            JSONObject query = new JSONObject();
            query.put("warehouseId", warehouseId);
            query.put("page", 1);
            query.put("size", 100);
            JSONObject page = request("POST", "/api/inventory/summary", query);
            JSONArray records = page.optJSONArray("records");
            List<InventorySummaryItem> inventory = new ArrayList<>();
            if (records != null) {
                for (int index = 0; index < records.length(); index++) {
                    JSONObject row = records.getJSONObject(index);
                    inventory.add(new InventorySummaryItem(
                            jsonText(row, "productName", "未命名产品"),
                            row.optInt("totalQuantity", 0),
                            row.optInt("totalPieces", 0),
                            row.optDouble("totalWeight", 0),
                            jsonText(row, "firstEntryDate", jsonText(row, "entryDate", ""))));
                }
            }

            JSONArray operationRows = requestArray("GET",
                    "/api/inventory/warehouses/" + warehouseId + "/recent-operations?limit=20", null);
            List<WarehouseRecentOperation> operations = new ArrayList<>();
            for (int index = 0; index < operationRows.length(); index++) {
                JSONObject row = operationRows.getJSONObject(index);
                operations.add(new WarehouseRecentOperation(
                        jsonText(row, "operationTime", ""),
                        jsonText(row, "operationName", jsonText(row, "operationType", "操作")),
                        jsonText(row, "operatorName", "未记录"),
                        jsonText(row, "palletCode", ""),
                        jsonText(row, "productName", ""),
                        jsonText(row, "fromWarehouseName", ""),
                        jsonText(row, "toWarehouseName", ""),
                        jsonText(row, "remark", "")));
            }
            return new WarehouseDetail(inventory, operations);
        });
    }

    void searchProductStock(String productName, Callback<List<ProductStockItem>> callback) {
        run(callback, () -> {
            JSONObject query = new JSONObject();
            if (productName != null && !productName.trim().isEmpty()) {
                query.put("productName", productName.trim());
            }
            query.put("page", 1);
            query.put("size", 50);
            JSONObject data = request("POST", "/api/inventory/agent-read/ledger/query", query);
            JSONArray records = data.optJSONArray("records");
            List<ProductStockItem> result = new ArrayList<>();
            if (records != null) {
                for (int index = 0; index < records.length(); index++) {
                    JSONObject row = records.getJSONObject(index);
                    result.add(new ProductStockItem(
                            jsonText(row, "productName", "未命名产品"),
                            jsonText(row, "productStatus", "未记录"),
                            jsonText(row, "palletCode", "未记录二维码"),
                            jsonText(row, "warehouseName", "未记录库位"),
                            row.optInt("palletQuantity", 0),
                            row.optInt("pieces", 0),
                            jsonText(row, "entryDate", ""),
                            jsonText(row, "productionDate", "")));
                }
            }
            return result;
        });
    }

    void searchAssayRecords(String productName, Callback<List<AssayRecordItem>> callback) {
        run(callback, () -> {
            JSONObject productScope = new JSONObject();
            if (productName == null || productName.trim().isEmpty()) {
                productScope.put("type", "ALL");
            } else {
                productScope.put("type", "EXACT_PRODUCT_NAME_GROUP");
                productScope.put("productName", productName.trim());
            }
            JSONObject query = new JSONObject();
            query.put("productScope", productScope);
            query.put("judgeStatus", "ANY");
            query.put("sortBy", "sampleDate");
            query.put("sortDirection", "DESC");
            query.put("page", 1);
            query.put("size", 50);
            JSONObject data = request("POST", "/api/assay/records/query", query);
            JSONArray records = data.optJSONArray("records");
            List<AssayRecordItem> result = new ArrayList<>();
            if (records != null) {
                for (int index = 0; index < records.length(); index++) {
                    JSONObject row = records.getJSONObject(index);
                    result.add(new AssayRecordItem(
                            jsonText(row, "recordRef", ""),
                            jsonText(row, "productLabel", "未命名产品"),
                            jsonText(row, "sampleDate", ""),
                            jsonText(row, "judgeLabel", "未判定"),
                            jsonText(row, "failedMetricText", ""),
                            row.optInt("failedMetricCount", 0),
                            jsonText(row, "standardLabel", ""),
                            jsonText(row, "testerLabel", "")));
                }
            }
            return result;
        });
    }

    void searchFlowRecords(String palletCode, Integer warehouseId,
                           Callback<List<FlowRecordItem>> callback) {
        run(callback, () -> {
            JSONObject query = new JSONObject();
            if (palletCode != null && !palletCode.trim().isEmpty()) {
                query.put("code", ScanCodeClassifier.normalizePalletInput(palletCode));
            }
            if (warehouseId != null && warehouseId > 0) {
                query.put("warehouseId", warehouseId);
            }
            query.put("page", 1);
            query.put("size", 50);
            JSONObject data = request("POST", "/api/pallet-codes/flow-records/query", query);
            JSONArray records = data.optJSONArray("records");
            List<FlowRecordItem> result = new ArrayList<>();
            if (records != null) {
                for (int index = 0; index < records.length(); index++) {
                    JSONObject row = records.getJSONObject(index);
                    result.add(new FlowRecordItem(
                            jsonText(row, "time", ""),
                            jsonText(row, "eventLabel", "操作"),
                            jsonText(row, "codeLabel", ""),
                            jsonText(row, "productLabel", ""),
                            jsonText(row, "fromWarehouseLabel", ""),
                            jsonText(row, "toWarehouseLabel", ""),
                            jsonText(row, "operatorLabel", "")));
                }
            }
            return result;
        });
    }

    void resolveStoredPallet(String rawCode, Callback<StoredPalletInfo> callback) {
        run(callback, () -> {
            String normalized = ScanCodeClassifier.normalizePalletInput(rawCode);
            String encoded = urlEncode(normalized);
            JSONObject pallet = request("GET", "/api/pallet-codes/parse?code=" + encoded, null);
            String canonicalCode = pallet.getString("code");
            JSONObject inventory = request("GET", "/api/pallet-codes/" + urlEncode(canonicalCode)
                    + "/inventory", null);
            return new StoredPalletInfo(
                    canonicalCode,
                    jsonText(pallet, "productName", "未命名产品"),
                    jsonText(pallet, "productStatus", ""),
                    jsonText(pallet, "productionDate", "未记录"),
                    jsonText(inventory, "warehouseName", "未记录库位"),
                    inventory.optInt("quantity", 0),
                    inventory.optBoolean("unit", false));
        });
    }

    void createOutboundTasks(String productStatus, List<String> palletCodes, Callback<Void> callback) {
        run(callback, () -> {
            JSONObject body = codesBody(palletCodes);
            String path = "半成品".equals(productStatus)
                    ? "/api/pallet-codes/semi/out/create"
                    : "/api/pallet-codes/finish/out/create";
            requestVoid("POST", path, body);
            return null;
        });
    }

    void confirmOutboundTasks(String productStatus, List<String> palletCodes, Callback<Void> callback) {
        run(callback, () -> {
            JSONObject body = codesBody(palletCodes);
            String path = "半成品".equals(productStatus)
                    ? "/api/pallet-codes/semi/out/confirm"
                    : "/api/pallet-codes/finish/out/confirm";
            requestVoid("POST", path, body);
            return null;
        });
    }

    void createTransferTasks(String targetWarehouseName, List<String> palletCodes, Callback<Void> callback) {
        run(callback, () -> {
            JSONArray items = new JSONArray();
            for (String code : palletCodes) {
                JSONObject item = new JSONObject();
                item.put("code", code);
                item.put("targetWarehouseName", targetWarehouseName);
                items.put(item);
            }
            JSONObject body = new JSONObject();
            body.put("items", items);
            requestVoid("POST", "/api/pallet-codes/transfer/create", body);
            return null;
        });
    }

    void confirmTransferTasks(List<String> palletCodes, Callback<Void> callback) {
        run(callback, () -> {
            requestVoid("POST", "/api/pallet-codes/transfer/confirm", codesBody(palletCodes));
            return null;
        });
    }

    private JSONObject codesBody(List<String> palletCodes) throws JSONException {
        JSONObject body = new JSONObject();
        JSONArray codes = new JSONArray();
        for (String code : palletCodes) {
            codes.put(code);
        }
        body.put("codes", codes);
        return body;
    }

    private <T> void run(Callback<T> callback, ThrowingSupplier<T> supplier) {
        executor.execute(() -> {
            try {
                T result = supplier.get();
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (ApiFailure failure) {
                String message = MobileDisplay.userMessage(failure.getMessage());
                mainHandler.post(() -> callback.onError(message, failure.httpStatus));
            } catch (Exception exception) {
                String message = exception.getMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = "无法连接仓储系统，请检查网络或联系管理员";
                }
                String finalMessage = MobileDisplay.userMessage(message);
                mainHandler.post(() -> callback.onError(finalMessage, 0));
            }
        });
    }

    private JSONObject request(String method, String path, JSONObject body) throws IOException, JSONException, ApiFailure {
        Object data = requestData(method, path, body);
        if (!(data instanceof JSONObject)) {
            throw new ApiFailure("服务器返回的数据格式不正确", 200);
        }
        return (JSONObject) data;
    }

    private JSONArray requestArray(String method, String path, JSONObject body) throws IOException, JSONException, ApiFailure {
        Object data = requestData(method, path, body);
        if (!(data instanceof JSONArray)) {
            throw new ApiFailure("服务器返回的数据格式不正确", 200);
        }
        return (JSONArray) data;
    }

    private void requestVoid(String method, String path, JSONObject body) throws IOException, JSONException, ApiFailure {
        requestData(method, path, body);
    }

    private Object requestData(String method, String path, JSONObject body) throws IOException, JSONException, ApiFailure {
        if (baseUrl.trim().isEmpty()) {
            throw new ApiFailure("系统连接配置无效，请联系管理员", 0);
        }
        URI.create(baseUrl);
        HttpURLConnection connection = (HttpURLConnection) new java.net.URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        if (!token.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (body != null) {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String response = read(stream);
        connection.disconnect();
        if (response.trim().isEmpty()) {
            throw new ApiFailure("服务器未返回内容", status);
        }
        JSONObject envelope = new JSONObject(response);
        int businessCode = envelope.optInt("code", status);
        if (status < 200 || status >= 300 || businessCode != 200) {
            throw new ApiFailure(envelope.optString("msg", "请求失败"), status);
        }
        Object data = envelope.opt("data");
        return data == null ? JSONObject.NULL : data;
    }

    private String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }

    private String urlEncode(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static List<String> stringList(JSONArray values) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (int index = 0; index < values.length(); index++) {
            String value = values.optString(index, "").trim();
            if (!value.isEmpty() && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private static String jsonText(JSONObject object, String key, String fallback) {
        if (object == null || object.isNull(key)) {
            return fallback;
        }
        String value = object.optString(key, "").trim();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? fallback : value;
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class ApiFailure extends Exception {
        final int httpStatus;

        ApiFailure(String message, int httpStatus) {
            super(message);
            this.httpStatus = httpStatus;
        }
    }
}
