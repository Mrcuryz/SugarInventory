package com.laibin.sugarinventory.dt50;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The server catalog is the single source of metric names, order and units. */
final class ProductionDailyReportData {
    static final String[] FIELD_KEYS = {
            "dailyActual", "convertedTons", "monthQuantity", "monthTons", "yearTons", "remark"
    };
    static final String[] FIELD_LABELS = {
            "当日实绩", "折成吨", "月累计（件）", "月累计（吨）", "年累计（吨）", "备注"
    };
    static final String PRODUCT_UNIT = "件";

    private ProductionDailyReportData() {
    }

    static Report parseReport(JSONObject object) throws JSONException {
        Report report = new Report();
        report.id = object.optLong("id", 0);
        report.date = string(object, "reportDate");
        report.preparedDate = string(object, "preparedDate");
        report.preparedBy = string(object, "preparedByName");
        report.status = string(object, "status");
        report.version = object.optInt("version", 0);
        report.updatedBy = string(object, "updatedByName");
        report.updatedAt = string(object, "updatedAt");
        report.headerDirty = object.optBoolean("_headerDirty", false);
        report.importPending = object.optBoolean("_importPending", false);
        JSONArray sections = object.optJSONArray("sections");
        if (sections != null) {
            for (int index = 0; index < sections.length(); index++) {
                JSONObject source = sections.getJSONObject(index);
                Section section = new Section();
                section.code = string(source, "departmentCode");
                section.name = string(source, "departmentName");
                section.status = string(source, "status");
                section.version = source.optInt("version", 0);
                section.updatedBy = string(source, "updatedByName");
                section.updatedAt = string(source, "updatedAt");
                section.dirty = source.optBoolean("_dirty", false);
                JSONArray groups = source.optJSONArray("groups");
                if (groups != null) {
                    for (int groupIndex = 0; groupIndex < groups.length(); groupIndex++) {
                        JSONObject groupSource = groups.getJSONObject(groupIndex);
                        Group group = new Group();
                        group.code = string(groupSource, "groupCode");
                        group.name = string(groupSource, "groupName");
                        group.productGroup = groupSource.optBoolean("productGroup", false);
                        JSONArray statuses = groupSource.optJSONArray("allowedProductStatuses");
                        if (statuses != null) {
                            for (int statusIndex = 0; statusIndex < statuses.length(); statusIndex++) {
                                group.allowedProductStatuses.add(statuses.optString(statusIndex));
                            }
                        }
                        readRows(groupSource.optJSONArray("metricValues"), false, group.metrics);
                        readRows(groupSource.optJSONArray("productLines"), true, group.products);
                        section.groups.add(group);
                    }
                }
                report.sections.add(section);
            }
        }
        return report;
    }

    private static void readRows(JSONArray sources, boolean product, List<Row> target) throws JSONException {
        if (sources == null) return;
        for (int index = 0; index < sources.length(); index++) {
            JSONObject source = sources.getJSONObject(index);
            Row row = new Row();
            row.product = product;
            row.id = source.has("id") && !source.isNull("id") ? source.optLong("id") : null;
            row.metricCode = string(source, "metricCode");
            row.name = string(source, product ? "productName" : "metricName");
            row.importedName = string(source, "importedProductName");
            row.importError = string(source, "importError");
            if (row.name.isEmpty() && !row.importedName.isEmpty()) row.name = row.importedName;
            row.unit = product ? PRODUCT_UNIT : string(source, "unit");
            row.position = string(source, "position");
            row.categoryCode = string(source, "categoryCode");
            row.productId = source.has("productId") && !source.isNull("productId")
                    ? source.optInt("productId") : null;
            row.productStatus = string(source, "productStatus");
            row.displayOrder = source.optInt("displayOrder", index + 1);
            for (int field = 0; field < FIELD_KEYS.length; field++) {
                row.values[field] = string(source, FIELD_KEYS[field]);
            }
            target.add(row);
        }
    }

    static List<ProductOption> parseProductOptions(JSONArray array) throws JSONException {
        List<ProductOption> result = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject source = array.getJSONObject(index);
            ProductOption option = new ProductOption();
            option.id = source.getInt("id");
            option.name = string(source, "productName");
            option.status = string(source, "productStatus");
            option.type = string(source, "productType");
            result.add(option);
        }
        return result;
    }

    static List<History> parseHistory(JSONObject page) throws JSONException {
        List<History> result = new ArrayList<>();
        JSONArray records = page.optJSONArray("records");
        if (records == null) return result;
        for (int index = 0; index < records.length(); index++) {
            JSONObject source = records.getJSONObject(index);
            History history = new History();
            history.date = string(source, "reportDate");
            history.status = string(source, "status");
            history.preparedBy = string(source, "preparedByName");
            history.updatedAt = string(source, "updatedAt");
            result.add(history);
        }
        return result;
    }

    static String string(JSONObject source, String key) {
        if (source == null || source.isNull(key)) return "";
        return source.optString(key, "");
    }

    static String validateNumber(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            BigDecimal number = new BigDecimal(value.trim());
            long integerDigits = (long) number.precision() - number.scale();
            if (number.scale() > 6 || integerDigits > 12) {
                return "整数最多 12 位，小数点后最多 6 位";
            }
            return null;
        } catch (NumberFormatException exception) {
            return "请输入有效数字";
        }
    }

    static String validateSection(Section section) {
        for (Group group : section.groups) {
            for (Row row : group.allRows()) {
                if (row.product && row.hasContent() && row.productId == null) {
                    return group.name + " · " + (row.name.isEmpty() ? "产品行" : row.name) + "：请选择产品";
                }
                for (int field = 0; field < FIELD_KEYS.length; field++) {
                    String value = row.values[field];
                    if (field == 5) {
                        if (value != null && value.length() > 500) return row.name + "：备注不能超过 500 字";
                    } else {
                        String error = validateNumber(value);
                        if (error != null) return row.name + " · " + FIELD_LABELS[field] + "：" + error;
                    }
                }
            }
        }
        return null;
    }

    private static Object numberOrNull(String value) {
        return value == null || value.trim().isEmpty() ? JSONObject.NULL : new BigDecimal(value.trim());
    }

    private static Object textOrNull(String value) {
        return value == null || value.trim().isEmpty() ? JSONObject.NULL : value.trim();
    }

    private static JSONObject rowPayload(Row row) throws JSONException {
        JSONObject object = new JSONObject();
        if (row.product) {
            object.put("id", row.id == null ? JSONObject.NULL : row.id);
            object.put("categoryCode", row.categoryCode);
            object.put("productId", row.productId == null ? JSONObject.NULL : row.productId);
            object.put("unit", PRODUCT_UNIT);
            object.put("displayOrder", row.displayOrder);
        } else {
            object.put("metricCode", row.metricCode);
        }
        for (int field = 0; field < 5; field++) object.put(FIELD_KEYS[field], numberOrNull(row.values[field]));
        object.put("remark", textOrNull(row.values[5]));
        return object;
    }

    static JSONObject sectionPayload(Section section) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("version", section.version);
        JSONArray metrics = new JSONArray();
        JSONArray products = new JSONArray();
        for (Group group : section.groups) {
            for (Row metric : group.metrics) metrics.put(rowPayload(metric));
            int order = 1;
            for (Row product : group.products) {
                if (!product.hasContent()) continue;
                product.categoryCode = group.code;
                product.displayOrder = order++;
                products.put(rowPayload(product));
            }
        }
        body.put("metricValues", metrics);
        body.put("productLines", products);
        return body;
    }

    static JSONObject headerPayload(Report report) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("version", report.version);
        body.put("preparedDate", textOrNull(report.preparedDate));
        body.put("preparedByName", textOrNull(report.preparedBy));
        return body;
    }

    static JSONObject importConfirmPayload(Report report) throws JSONException {
        JSONObject body = headerPayload(report);
        body.put("reportDate", report.date);
        JSONArray sections = new JSONArray();
        for (Section section : report.sections) {
            JSONObject sectionBody = sectionPayload(section);
            sectionBody.put("departmentCode", section.code);
            sections.put(sectionBody);
        }
        body.put("sections", sections);
        return body;
    }

    static JSONObject localSnapshot(Report report) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", report.id);
        object.put("reportDate", report.date);
        object.put("preparedDate", report.preparedDate);
        object.put("preparedByName", report.preparedBy);
        object.put("status", report.status);
        object.put("version", report.version);
        object.put("updatedByName", report.updatedBy);
        object.put("updatedAt", report.updatedAt);
        object.put("_headerDirty", report.headerDirty);
        object.put("_importPending", report.importPending);
        JSONArray sections = new JSONArray();
        for (Section section : report.sections) {
            JSONObject sectionObject = new JSONObject();
            sectionObject.put("departmentCode", section.code);
            sectionObject.put("departmentName", section.name);
            sectionObject.put("status", section.status);
            sectionObject.put("version", section.version);
            sectionObject.put("updatedByName", section.updatedBy);
            sectionObject.put("updatedAt", section.updatedAt);
            sectionObject.put("_dirty", section.dirty);
            JSONArray groups = new JSONArray();
            for (Group group : section.groups) {
                JSONObject groupObject = new JSONObject();
                groupObject.put("groupCode", group.code);
                groupObject.put("groupName", group.name);
                groupObject.put("productGroup", group.productGroup);
                groupObject.put("allowedProductStatuses", new JSONArray(group.allowedProductStatuses));
                JSONArray metrics = new JSONArray();
                JSONArray products = new JSONArray();
                for (Row row : group.metrics) metrics.put(localRow(row));
                for (Row row : group.products) products.put(localRow(row));
                groupObject.put("metricValues", metrics);
                groupObject.put("productLines", products);
                groups.put(groupObject);
            }
            sectionObject.put("groups", groups);
            sections.put(sectionObject);
        }
        object.put("sections", sections);
        return object;
    }

    static Report mergeUnsaved(Report server, Report local) {
        if (local == null || !local.dirty()) return server;
        if (local.headerDirty) {
            server.headerConflict = local.version != server.version;
            server.version = local.version;
            server.preparedDate = local.preparedDate;
            server.preparedBy = local.preparedBy;
            server.headerDirty = true;
        }
        for (Section localSection : local.sections) {
            if (!localSection.dirty) continue;
            Section serverSection = server.section(localSection.code);
            if (serverSection == null) continue;
            serverSection.conflict = localSection.version != serverSection.version;
            serverSection.version = localSection.version;
            serverSection.groups.clear();
            serverSection.groups.addAll(localSection.groups);
            serverSection.dirty = true;
        }
        return server;
    }

    private static JSONObject localRow(Row row) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", row.id == null ? JSONObject.NULL : row.id);
        object.put("metricCode", row.metricCode);
        object.put("metricName", row.name);
        object.put("productName", row.name);
        object.put("importedProductName", row.importedName);
        object.put("importError", row.importError);
        object.put("unit", row.unit);
        object.put("position", row.position);
        object.put("categoryCode", row.categoryCode);
        object.put("productId", row.productId == null ? JSONObject.NULL : row.productId);
        object.put("productStatus", row.productStatus);
        object.put("displayOrder", row.displayOrder);
        for (int field = 0; field < FIELD_KEYS.length; field++) object.put(FIELD_KEYS[field], row.values[field]);
        return object;
    }

    static final class Report {
        long id;
        String date = "";
        String preparedDate = "";
        String preparedBy = "";
        String status = "DRAFT";
        int version;
        String updatedBy = "";
        String updatedAt = "";
        boolean headerDirty;
        boolean headerConflict;
        boolean importPending;
        final List<Section> sections = new ArrayList<>();

        boolean dirty() {
            if (headerDirty) return true;
            for (Section section : sections) if (section.dirty) return true;
            return false;
        }

        Section section(String code) {
            for (Section section : sections) if (section.code.equals(code)) return section;
            return null;
        }
    }

    static final class Section {
        String code = "";
        String name = "";
        String status = "DRAFT";
        int version;
        String updatedBy = "";
        String updatedAt = "";
        boolean dirty;
        boolean conflict;
        final List<Group> groups = new ArrayList<>();

        Group group(String code) {
            for (Group group : groups) if (group.code.equals(code)) return group;
            return null;
        }

        int missingCount() {
            int count = 0;
            for (Group group : groups) count += group.missingCount();
            return count;
        }
    }

    static final class Group {
        String code = "";
        String name = "";
        boolean productGroup;
        final List<String> allowedProductStatuses = new ArrayList<>();
        final List<Row> metrics = new ArrayList<>();
        final List<Row> products = new ArrayList<>();

        List<Row> allRows() {
            if (!productGroup) return new ArrayList<>(metrics);
            List<Row> rows = new ArrayList<>();
            for (Row row : metrics) if ("BEFORE_PRODUCT".equals(row.position)) rows.add(row);
            rows.addAll(products);
            for (Row row : metrics) if (!"BEFORE_PRODUCT".equals(row.position)) rows.add(row);
            return rows;
        }

        int missingCount() {
            int count = 0;
            for (Row row : allRows()) if (row.empty()) count++;
            return count;
        }

        void moveProduct(Row row, int offset) {
            int from = products.indexOf(row);
            int to = from + offset;
            if (from >= 0 && to >= 0 && to < products.size()) Collections.swap(products, from, to);
        }
    }

    static final class Row {
        boolean product;
        Long id;
        String metricCode = "";
        String name = "";
        String importedName = "";
        String importError = "";
        String unit = "";
        String position = "";
        String categoryCode = "";
        Integer productId;
        String productStatus = "";
        int displayOrder;
        final String[] values = {"", "", "", "", "", ""};

        boolean empty() {
            for (String value : values) if (value != null && !value.trim().isEmpty()) return false;
            return true;
        }

        boolean hasContent() {
            return productId != null || !importedName.trim().isEmpty()
                    || !importError.trim().isEmpty() || !empty();
        }

        boolean otherFieldsFilled(int activeField) {
            for (int index = 0; index < values.length; index++) {
                if (index != activeField && values[index] != null && !values[index].trim().isEmpty()) return true;
            }
            return false;
        }
    }

    static final class ProductOption {
        int id;
        String name = "";
        String status = "";
        String type = "";
    }

    static final class History {
        String date = "";
        String status = "";
        String preparedBy = "";
        String updatedAt = "";
    }
}
