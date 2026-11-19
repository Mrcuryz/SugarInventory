package com.Laibin.SugarInventory.agent.productimport;

import org.apache.poi.ss.usermodel.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Reads untrusted workbook values only. No model call, formula evaluation or business write. */
public final class ProductImportWorkbookReader {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_ROWS = 500;
    private static final int MAX_EXPANDED_BYTES = 24 * 1024 * 1024;
    private static final Map<String, String> HEADERS = Map.ofEntries(
            Map.entry("产品名称", "productName"), Map.entry("产品类型", "productType"),
            Map.entry("产品状态", "status"), Map.entry("包装方式", "packagingMethod"),
            Map.entry("单件重量(kg)", "weightPerPiece"), Map.entry("每板件数", "piecesPerPallet"),
            Map.entry("是否可堆叠", "canStack"), Map.entry("筛网名称", "screenMeshName"));

    public record SourceRow(String sheet, int rowNumber, Map<String, String> values) {
        public SourceRow { values = Map.copyOf(values); }
    }
    public static final class ValidationException extends IllegalArgumentException {
        public ValidationException(String message) { super(message); }
    }
    public record Column(int index, String title, String suggestedField) {}
    public record SheetInfo(String name, boolean hidden, List<Column> columns) {}

    public List<SheetInfo> inspect(byte[] content) throws IOException {
        validateContainer(content);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() > 32) throw new ValidationException("工作表过多，请拆分文件");
            List<SheetInfo> result = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                boolean hidden = workbook.isSheetHidden(i) || workbook.isSheetVeryHidden(i);
                List<Column> columns = new ArrayList<>();
                Row header = sheet.getRow(0);
                if (!hidden && header != null) {
                    if (header.getLastCellNum() > 32) throw new ValidationException("列数不能超过32列");
                    for (int c = 0; c < header.getLastCellNum(); c++) {
                        String title = text(header.getCell(c));
                        columns.add(new Column(c, title, HEADERS.get(Normalizer.normalize(title, Normalizer.Form.NFKC))));
                    }
                }
                result.add(new SheetInfo(sheet.getSheetName(), hidden, List.copyOf(columns)));
            }
            return List.copyOf(result);
        }
    }

    public List<SourceRow> read(byte[] content, String selectedSheet) throws IOException {
        return read(content, selectedSheet, Map.of());
    }

    /** Mapping keys are zero-based column indexes explicitly confirmed by the user. No silent column discard. */
    public List<SourceRow> read(byte[] content, String selectedSheet, Map<Integer, String> mapping) throws IOException {
        validateContainer(content);
        if (mapping == null || mapping.size() > 32 || mapping.entrySet().stream().anyMatch(e ->
                e.getKey() == null || e.getKey() < 0 || e.getKey() >= 32 || !HEADERS.containsValue(e.getValue())))
            throw new ValidationException("列对应关系无效，请重新选择");
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() > 32) throw new ValidationException("工作表过多，请拆分文件");
            Sheet sheet;
            if (selectedSheet == null || selectedSheet.isBlank()) {
                if (workbook.getNumberOfSheets() != 1)
                    throw new ValidationException("文件有多个工作表，请先选择要导入的工作表");
                sheet = workbook.getSheetAt(0);
            } else {
                sheet = workbook.getSheet(selectedSheet);
                if (sheet == null) throw new ValidationException("未找到所选工作表");
            }
            int index = workbook.getSheetIndex(sheet);
            if (workbook.isSheetHidden(index) || workbook.isSheetVeryHidden(index))
                throw new ValidationException("请使用可见工作表");
            if (sheet.getNumMergedRegions() > 0)
                throw new ValidationException("表格包含合并单元格，请拆分后再导入");
            if (sheet.getLastRowNum() > MAX_ROWS)
                throw new ValidationException("每次最多导入500行，请拆分文件");
            Row header = sheet.getRow(0);
            if (header == null || header.getLastCellNum() > 32)
                throw new ValidationException("请将列名放在第一行，列数不能超过32列");
            if (mapping.keySet().stream().anyMatch(c -> c >= header.getLastCellNum()))
                throw new ValidationException("列对应关系包含不存在的列");
            Map<Integer, String> columns = new LinkedHashMap<>();
            for (int col = 0; col < header.getLastCellNum(); col++) {
                String title = text(header.getCell(col));
                if (title.isEmpty() && !mapping.containsKey(col)) continue;
                String field = mapping.getOrDefault(col, HEADERS.get(Normalizer.normalize(title, Normalizer.Form.NFKC)));
                if (field == null) throw new ValidationException("未识别列名“" + title + "”，请先确认列对应关系");
                if (columns.containsValue(field)) throw new ValidationException("列名重复：" + title);
                columns.put(col, field);
            }
            if (!columns.containsValue("productName")) throw new ValidationException("缺少产品名称列");
            List<SourceRow> rows = new ArrayList<>();
            for (int n = 1; n <= sheet.getLastRowNum(); n++) {
                Row row = sheet.getRow(n);
                if (row == null) continue;
                if (row.getLastCellNum() > 32) throw new ValidationException("第" + (n+1) + "行列数过多");
                Map<String, String> values = new LinkedHashMap<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String value = text(row.getCell(c));
                    if (value.isEmpty()) continue; // Missing value is not an instruction to clear a field.
                    if (!columns.containsKey(c)) throw new ValidationException("第" + (n+1) + "行有未命名列的数据");
                    values.put(columns.get(c), value);
                }
                if (values.isEmpty()) continue;
                if (row.getZeroHeight()) throw new ValidationException("第" + (n+1) + "行被隐藏，请先确认该行是否参与导入");
                if (!values.containsKey("productName")) throw new ValidationException("第" + (n+1) + "行缺少产品名称");
                rows.add(new SourceRow(sheet.getSheetName(), n + 1, values));
            }
            if (rows.isEmpty()) throw new ValidationException("工作表中没有产品数据");
            return List.copyOf(rows);
        }
    }

    /** Bound decompression before POI allocates its object model; never change POI's global safety limits. */
    private void validateContainer(byte[] content) throws IOException {
        if (content == null || content.length == 0 || content.length > MAX_BYTES)
            throw new ValidationException("请选择不超过10MB的Excel文件");
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K') return; // OLE xls is checked by POI.
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            byte[] buffer = new byte[8192];
            int entries = 0, total = 0;
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 256) throw new ValidationException("文件结构过于复杂，请拆分后导入");
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.contains("vbaproject") || name.startsWith("xl/embeddings/") || name.startsWith("xl/externallinks/"))
                    throw new ValidationException("请移除宏、嵌入文件和外部引用后再导入");
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_EXPANDED_BYTES) throw new ValidationException("文件解压后过大，请拆分后导入");
                }
            }
        }
    }

    private String text(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.FORMULA)
            throw new ValidationException("表格含公式，请先粘贴为数值后导入");
        if (cell.getCellType() == CellType.ERROR)
            throw new ValidationException("表格含错误单元格，请修正后导入");
        String value = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> cell.getBooleanCellValue() ? "是" : "否";
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) throw new ValidationException("产品字段不能使用日期单元格");
                yield org.apache.poi.ss.util.NumberToTextConverter.toText(cell.getNumericCellValue());
            }
            default -> "";
        };
        value = value.strip();
        if (value.length() > 2000) throw new ValidationException("单元格文字过长");
        return value;
    }
}
