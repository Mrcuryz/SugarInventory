package com.Laibin.SugarInventory.production.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyMetricValueDTO;
import com.Laibin.SugarInventory.production.service.ProductionDailyReportCatalog;
import com.Laibin.SugarInventory.production.service.ProductionDailyReportCatalog.GroupDefinition;
import com.Laibin.SugarInventory.production.service.ProductionDailyReportCatalog.MetricDefinition;
import com.Laibin.SugarInventory.production.service.ProductionDailyReportCatalog.SectionDefinition;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProductionDailyReportXlsxImporter {
    private static final int MAX_ROWS = 500;
    private static final int MAX_COLUMNS = 50;
    private static final int[] REGION_START_COLUMNS = {0, 10};
    private static final Pattern REPORT_DATE_PATTERN = Pattern.compile("来冰公司(\\d{8})生产日报表");
    private static final Pattern PREPARED_BY_PATTERN = Pattern.compile(
            "制表人\\s*[:：]\\s*(.*?)(?=\\s*制表日期\\s*[:：]|$)");
    private static final Pattern PREPARED_DATE_PATTERN = Pattern.compile(
            "制表日期\\s*[:：]\\s*([0-9]{4}[-/.]?[0-9]{2}[-/.]?[0-9]{2})");
    private static final Map<String, String> METRIC_ALIASES = Map.of(
            normalize("冰糖结昌率"), normalize("冰糖结晶率")
    );

    public ParsedReport parse(InputStream inputStream) {
        if (inputStream == null) {
            throw invalid("请选择Excel文件");
        }
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw invalid("Excel中没有工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            validateDimensions(sheet);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            LocalDate reportDate = parseReportDate(sheet, formatter, evaluator);
            HeaderData header = parseHeader(sheet, formatter, evaluator);
            List<ParsedSection> sections = parseSections(sheet, formatter, evaluator);
            return new ParsedReport(reportDate, header.preparedDate(), header.preparedByName(), sections);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalid("无法读取Excel，请确认文件未损坏且格式正确");
        }
    }

    private void validateDimensions(Sheet sheet) {
        if (sheet.getLastRowNum() + 1 > MAX_ROWS) {
            throw invalid("Excel行数不能超过" + MAX_ROWS + "行");
        }
        for (Row row : sheet) {
            if (row.getLastCellNum() > MAX_COLUMNS) {
                throw invalid("Excel列数不能超过" + MAX_COLUMNS + "列");
            }
        }
    }

    private LocalDate parseReportDate(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int maxRow = Math.min(sheet.getLastRowNum(), 4);
        for (int rowIndex = 0; rowIndex <= maxRow; rowIndex++) {
            for (int column = 0; column < 20; column++) {
                String normalized = normalize(cellText(sheet, rowIndex, column, formatter, evaluator));
                Matcher matcher = REPORT_DATE_PATTERN.matcher(normalized);
                if (matcher.find()) {
                    return parseDate(matcher.group(1), "日报日期");
                }
            }
        }
        throw invalid("未找到“来冰公司yyyyMMdd生产日报表”标题");
    }

    private HeaderData parseHeader(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        String preparedBy = null;
        LocalDate preparedDate = null;
        int maxRow = Math.min(sheet.getLastRowNum(), 5);
        for (int rowIndex = 0; rowIndex <= maxRow; rowIndex++) {
            for (int column = 0; column < 20; column++) {
                String text = cellText(sheet, rowIndex, column, formatter, evaluator).trim();
                if (text.isEmpty()) {
                    continue;
                }
                Matcher byMatcher = PREPARED_BY_PATTERN.matcher(text);
                if (preparedBy == null && byMatcher.find()) {
                    preparedBy = blankToNull(byMatcher.group(1));
                }
                Matcher dateMatcher = PREPARED_DATE_PATTERN.matcher(text);
                if (preparedDate == null && dateMatcher.find()) {
                    preparedDate = parseDate(dateMatcher.group(1), "制表日期");
                }
            }
        }
        return new HeaderData(preparedDate, preparedBy);
    }

    private List<ParsedSection> parseSections(Sheet sheet, DataFormatter formatter,
                                               FormulaEvaluator evaluator) {
        Map<String, SectionAccumulator> accumulators = new LinkedHashMap<>();
        for (SectionDefinition definition : ProductionDailyReportCatalog.sections()) {
            accumulators.put(definition.code(), new SectionAccumulator(definition));
        }

        for (int startColumn : REGION_START_COLUMNS) {
            boolean headerSeen = false;
            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                String sectionText = mergedCellText(sheet, rowIndex, startColumn, formatter, evaluator);
                if ("车间".equals(normalize(sectionText))) {
                    headerSeen = true;
                    continue;
                }
                if (!headerSeen) {
                    continue;
                }
                SectionDefinition section = findSection(sectionText);
                if (section == null) {
                    continue;
                }
                SectionAccumulator accumulator = accumulators.get(section.code());
                accumulator.seen = true;

                String groupText = mergedCellText(sheet, rowIndex, startColumn + 1, formatter, evaluator);
                String itemText = cellText(sheet, rowIndex, startColumn + 2, formatter, evaluator).trim();
                GroupDefinition group;
                if ("FACTORY_SUMMARY".equals(section.code()) && itemText.isEmpty()) {
                    itemText = groupText.trim();
                    group = section.groups().get(0);
                } else {
                    group = findGroup(section, groupText);
                }
                if (itemText.isEmpty()) {
                    continue;
                }

                MetricDefinition metric = findMetric(group, itemText);
                if (metric == null && "小计".equals(normalizeMetricName(itemText))) {
                    metric = findUniqueMetric(section, itemText);
                }
                if (metric != null) {
                    if (!accumulator.metricCodes.add(metric.code())) {
                        throw invalid("第" + (rowIndex + 1) + "行固定指标重复：" + itemText);
                    }
                    accumulator.metrics.add(readMetric(sheet, rowIndex, startColumn, metric, evaluator));
                    continue;
                }

                if (group == null || !group.productGroup()) {
                    throw invalid("第" + (rowIndex + 1) + "行项目无法识别：" + itemText);
                }
                int displayOrder = accumulator.nextProductOrder.merge(group.code(), 1, Integer::sum);
                accumulator.products.add(readProduct(
                        sheet, rowIndex, startColumn, group.code(), itemText, displayOrder, evaluator));
            }
        }

        List<ParsedSection> result = new ArrayList<>();
        for (SectionAccumulator accumulator : accumulators.values()) {
            validateSectionComplete(accumulator);
            result.add(new ParsedSection(
                    accumulator.definition.code(),
                    List.copyOf(accumulator.metrics),
                    List.copyOf(accumulator.products)));
        }
        return result;
    }

    private void validateSectionComplete(SectionAccumulator accumulator) {
        if (!accumulator.seen) {
            throw invalid("Excel缺少“" + accumulator.definition.name() + "”区域");
        }
        List<String> missing = accumulator.definition.groups().stream()
                .flatMap(group -> group.metrics().stream())
                .filter(metric -> !accumulator.metricCodes.contains(metric.code()))
                .map(MetricDefinition::name)
                .toList();
        if (!missing.isEmpty()) {
            String names = String.join("、", missing.stream().limit(5).toList());
            throw invalid(accumulator.definition.name() + "缺少固定指标：" + names
                    + (missing.size() > 5 ? "等" + missing.size() + "项" : ""));
        }
    }

    private ProductionDailyMetricValueDTO readMetric(Sheet sheet, int rowIndex, int startColumn,
                                                      MetricDefinition metric, FormulaEvaluator evaluator) {
        ProductionDailyMetricValueDTO value = new ProductionDailyMetricValueDTO();
        value.setMetricCode(metric.code());
        value.setDailyActual(decimal(sheet, rowIndex, startColumn + 4, evaluator));
        value.setConvertedTons(decimal(sheet, rowIndex, startColumn + 5, evaluator));
        value.setMonthQuantity(decimal(sheet, rowIndex, startColumn + 6, evaluator));
        value.setMonthTons(decimal(sheet, rowIndex, startColumn + 7, evaluator));
        value.setYearTons(decimal(sheet, rowIndex, startColumn + 8, evaluator));
        value.setRemark(blankToNull(rawText(sheet, rowIndex, startColumn + 9, evaluator)));
        return value;
    }

    private ParsedProductLine readProduct(Sheet sheet, int rowIndex, int startColumn,
                                          String categoryCode, String productName, int displayOrder,
                                          FormulaEvaluator evaluator) {
        return new ParsedProductLine(
                categoryCode,
                productName.trim(),
                decimal(sheet, rowIndex, startColumn + 4, evaluator),
                decimal(sheet, rowIndex, startColumn + 5, evaluator),
                decimal(sheet, rowIndex, startColumn + 6, evaluator),
                decimal(sheet, rowIndex, startColumn + 7, evaluator),
                decimal(sheet, rowIndex, startColumn + 8, evaluator),
                blankToNull(rawText(sheet, rowIndex, startColumn + 9, evaluator)),
                displayOrder);
    }

    private BigDecimal decimal(Sheet sheet, int rowIndex, int column, FormulaEvaluator evaluator) {
        Row row = sheet.getRow(rowIndex);
        Cell cell = row == null ? null : row.getCell(column);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
        }
        if (cell.getCellType() == CellType.FORMULA) {
            CellValue evaluated = evaluator.evaluate(cell);
            if (evaluated == null || evaluated.getCellType() == CellType.BLANK) {
                return null;
            }
            if (evaluated.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(evaluated.getNumberValue()).stripTrailingZeros();
            }
            if (evaluated.getCellType() == CellType.STRING) {
                return parseDecimal(evaluated.getStringValue(), rowIndex, column);
            }
            throw invalid(cellAddress(rowIndex, column) + "不是有效数字");
        }
        if (cell.getCellType() == CellType.STRING) {
            return parseDecimal(cell.getStringCellValue(), rowIndex, column);
        }
        throw invalid(cellAddress(rowIndex, column) + "不是有效数字");
    }

    private BigDecimal parseDecimal(String value, int rowIndex, int column) {
        String normalized = value == null ? "" : value.trim().replace(",", "");
        if (normalized.isEmpty() || "-".equals(normalized) || "—".equals(normalized)) {
            return null;
        }
        if (normalized.endsWith("%")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        try {
            return new BigDecimal(normalized).stripTrailingZeros();
        } catch (NumberFormatException exception) {
            throw invalid(cellAddress(rowIndex, column) + "不是有效数字：" + value);
        }
    }

    private String rawText(Sheet sheet, int rowIndex, int column, FormulaEvaluator evaluator) {
        Row row = sheet.getRow(rowIndex);
        Cell cell = row == null ? null : row.getCell(column);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA) {
            CellValue evaluated = evaluator.evaluate(cell);
            if (evaluated == null) {
                return "";
            }
            return switch (evaluated.getCellType()) {
                case STRING -> evaluated.getStringValue();
                case NUMERIC -> BigDecimal.valueOf(evaluated.getNumberValue()).stripTrailingZeros().toPlainString();
                case BOOLEAN -> Boolean.toString(evaluated.getBooleanValue());
                default -> "";
            };
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private SectionDefinition findSection(String name) {
        String normalized = normalize(name);
        return ProductionDailyReportCatalog.sections().stream()
                .filter(section -> normalize(section.name()).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private GroupDefinition findGroup(SectionDefinition section, String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return null;
        }
        return section.groups().stream()
                .filter(group -> normalize(group.name()).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private MetricDefinition findMetric(GroupDefinition group, String name) {
        if (group == null) {
            return null;
        }
        String normalized = normalizeMetricName(name);
        return group.metrics().stream()
                .filter(metric -> normalize(metric.name()).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private MetricDefinition findUniqueMetric(SectionDefinition section, String name) {
        String normalized = normalizeMetricName(name);
        List<MetricDefinition> matches = section.groups().stream()
                .flatMap(group -> group.metrics().stream())
                .filter(metric -> normalize(metric.name()).equals(normalized))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private String normalizeMetricName(String name) {
        String normalized = normalize(name);
        return METRIC_ALIASES.getOrDefault(normalized, normalized);
    }

    private String mergedCellText(Sheet sheet, int rowIndex, int column,
                                  DataFormatter formatter, FormulaEvaluator evaluator) {
        for (CellRangeAddress range : sheet.getMergedRegions()) {
            if (range.isInRange(rowIndex, column)) {
                return cellText(sheet, range.getFirstRow(), range.getFirstColumn(), formatter, evaluator);
            }
        }
        return cellText(sheet, rowIndex, column, formatter, evaluator);
    }

    private String cellText(Sheet sheet, int rowIndex, int column,
                            DataFormatter formatter, FormulaEvaluator evaluator) {
        Row row = sheet.getRow(rowIndex);
        Cell cell = row == null ? null : row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator);
    }

    private LocalDate parseDate(String value, String label) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() != 8) {
            throw invalid(label + "格式不正确");
        }
        try {
            return LocalDate.parse(digits, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException exception) {
            throw invalid(label + "格式不正确");
        }
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "")
                .trim();
    }

    private String cellAddress(int rowIndex, int column) {
        int value = column + 1;
        StringBuilder letters = new StringBuilder();
        while (value > 0) {
            int remainder = (value - 1) % 26;
            letters.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return letters + Integer.toString(rowIndex + 1);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException invalid(String detail) {
        return new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_IMPORT_INVALID.getCode(), detail);
    }

    public record ParsedReport(LocalDate reportDate, LocalDate preparedDate, String preparedByName,
                               List<ParsedSection> sections) {
    }

    public record ParsedSection(String departmentCode, List<ProductionDailyMetricValueDTO> metricValues,
                                List<ParsedProductLine> productLines) {
    }

    public record ParsedProductLine(String categoryCode, String productName,
                                    BigDecimal dailyActual, BigDecimal convertedTons,
                                    BigDecimal monthQuantity, BigDecimal monthTons,
                                    BigDecimal yearTons, String remark, Integer displayOrder) {
    }

    private record HeaderData(LocalDate preparedDate, String preparedByName) {
    }

    private static final class SectionAccumulator {
        private final SectionDefinition definition;
        private final List<ProductionDailyMetricValueDTO> metrics = new ArrayList<>();
        private final List<ParsedProductLine> products = new ArrayList<>();
        private final Set<String> metricCodes = new HashSet<>();
        private final Map<String, Integer> nextProductOrder = new HashMap<>();
        private boolean seen;

        private SectionAccumulator(SectionDefinition definition) {
            this.definition = definition;
        }
    }
}
