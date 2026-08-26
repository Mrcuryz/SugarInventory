package com.Laibin.SugarInventory.production.service.impl;

import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyMetricValueVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyProductLineVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportGroupVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportSectionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportVO;
import com.Laibin.SugarInventory.production.service.ProductionDailyReportCatalog;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ProductionDailyReportXlsxExporter {
    private static final String TEMPLATE_PATH = "templates/production-daily-report-template.xlsx";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final List<String> NOTES = List.of(
            "1. 统计产率不含翻包及二次加工成品；",
            "2. 以上数据是根据每天生产情况汇总，不涉及最终销售的产品；",
            "3. 糖油桶的在制品不在统计内；",
            "4. 调拨到柳冰的糖油不在统计内。"
    );

    public byte[] export(ProductionDailyReportVO report) {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
        try (InputStream input = resource.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.getNumberOfSheets() == 0
                    ? workbook.createSheet("生产日报")
                    : workbook.getSheetAt(0);
            workbook.setSheetName(workbook.getSheetIndex(sheet), "生产日报");
            resetSheet(sheet);
            Styles styles = new Styles(workbook);
            configureColumns(sheet);
            writeTitleAndHeader(sheet, styles, report);

            ProductionDailyReportSectionVO poly = section(report, "POLYCRYSTAL");
            ProductionDailyReportSectionVO brown = section(report, "BROWN_SUGAR");
            ProductionDailyReportSectionVO mono = section(report, "MONOCRYSTAL");
            ProductionDailyReportSectionVO factory = section(report, "FACTORY_SUMMARY");

            int leftRow = writeRegionHeader(sheet, styles, 2, 0);
            leftRow = writeSection(sheet, styles, leftRow, 0, poly);
            leftRow = writeSection(sheet, styles, leftRow, 0, factory);

            int rightRow = writeRegionHeader(sheet, styles, 2, 10);
            rightRow = writeSection(sheet, styles, rightRow, 10, brown);
            rightRow = writeSection(sheet, styles, rightRow, 10, mono);
            rightRow = writeNotes(sheet, styles, rightRow + 1, 10);

            configurePrint(workbook, sheet, Math.max(leftRow, rightRow) - 1);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成生产日报Excel失败", exception);
        }
    }

    private void resetSheet(XSSFSheet sheet) {
        while (sheet.getNumMergedRegions() > 0) {
            sheet.removeMergedRegion(0);
        }
        for (int rowIndex = sheet.getLastRowNum(); rowIndex >= 0; rowIndex--) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                sheet.removeRow(row);
            }
        }
        sheet.setDisplayGridlines(false);
        sheet.setFitToPage(true);
    }

    private void configureColumns(XSSFSheet sheet) {
        int[] widths = {12, 17, 23, 11, 13, 13, 15, 13, 13, 18};
        for (int region = 0; region < 2; region++) {
            for (int index = 0; index < widths.length; index++) {
                sheet.setColumnWidth(region * 10 + index, widths[index] * 256);
            }
        }
    }

    private void writeTitleAndHeader(XSSFSheet sheet, Styles styles, ProductionDailyReportVO report) {
        Row titleRow = row(sheet, 0, 34);
        mergedText(sheet, titleRow, 0, 19,
                "来冰公司" + report.getReportDate().format(DateTimeFormatter.BASIC_ISO_DATE) + "生产日报表",
                styles.title);

        Row metaRow = row(sheet, 1, 24);
        mergedText(sheet, metaRow, 12, 15, "制表人：" + text(report.getPreparedByName()), styles.footer);
        mergedText(sheet, metaRow, 16, 19, "制表日期：" +
                (report.getPreparedDate() == null ? "" : DATE_FORMAT.format(report.getPreparedDate())), styles.footer);
    }

    private int writeRegionHeader(XSSFSheet sheet, Styles styles, int rowIndex, int startColumn) {
        String[] headers = {"车间", "项目分区", "项目", "单位", "当日实绩", "折成吨",
                "月累计(件)", "月累计(吨)", "年累计(吨)", "备注"};
        Row row = row(sheet, rowIndex, 25);
        for (int index = 0; index < headers.length; index++) {
            cell(row, startColumn + index, styles.header).setCellValue(headers[index]);
        }
        return rowIndex + 1;
    }

    private int writeSection(XSSFSheet sheet, Styles styles, int rowIndex, int startColumn,
                             ProductionDailyReportSectionVO section) {
        int sectionStart = rowIndex;
        for (ProductionDailyReportGroupVO group : section.getGroups()) {
            int groupStart = rowIndex;
            List<ProductionDailyMetricValueVO> before = group.getMetricValues().stream()
                    .filter(metric -> ProductionDailyReportCatalog.POSITION_BEFORE_PRODUCT.equals(metric.getPosition()))
                    .toList();
            List<ProductionDailyMetricValueVO> normal = group.getMetricValues().stream()
                    .filter(metric -> ProductionDailyReportCatalog.POSITION_NORMAL.equals(metric.getPosition()))
                    .toList();
            List<ProductionDailyMetricValueVO> after = group.getMetricValues().stream()
                    .filter(metric -> ProductionDailyReportCatalog.POSITION_AFTER_PRODUCT.equals(metric.getPosition()))
                    .toList();

            for (ProductionDailyMetricValueVO metric : before) {
                writeMetricRow(sheet, styles, rowIndex++, startColumn, metric);
            }
            if (group.isProductGroup()) {
                if (group.getProductLines().isEmpty()) {
                    writeEmptyProductRow(sheet, styles, rowIndex++, startColumn);
                } else {
                    for (ProductionDailyProductLineVO product : group.getProductLines()) {
                        writeProductRow(sheet, styles, rowIndex++, startColumn, product);
                    }
                }
            }
            for (ProductionDailyMetricValueVO metric : normal) {
                writeMetricRow(sheet, styles, rowIndex++, startColumn, metric);
            }
            for (ProductionDailyMetricValueVO metric : after) {
                writeMetricRow(sheet, styles, rowIndex++, startColumn, metric);
            }
            if (rowIndex == groupStart) {
                writeEmptyProductRow(sheet, styles, rowIndex++, startColumn);
            }
            mergeAndBorder(sheet, groupStart, rowIndex - 1, startColumn + 1);
            Cell groupCell = sheet.getRow(groupStart).getCell(startColumn + 1);
            groupCell.setCellValue(group.getGroupName());
            groupCell.setCellStyle(styles.group);
        }
        mergeAndBorder(sheet, sectionStart, rowIndex - 1, startColumn);
        Cell sectionCell = sheet.getRow(sectionStart).getCell(startColumn);
        sectionCell.setCellValue(section.getDepartmentName());
        sectionCell.setCellStyle(styles.section);
        return rowIndex;
    }

    private void writeMetricRow(XSSFSheet sheet, Styles styles, int rowIndex, int startColumn,
                                ProductionDailyMetricValueVO metric) {
        Row row = dataRow(sheet, styles, rowIndex, startColumn);
        row.getCell(startColumn + 2).setCellValue(metric.getMetricName());
        row.getCell(startColumn + 3).setCellValue(text(metric.getUnit()));
        writeNumber(row.getCell(startColumn + 4), metric.getDailyActual());
        writeNumber(row.getCell(startColumn + 5), metric.getConvertedTons());
        writeNumber(row.getCell(startColumn + 6), metric.getMonthQuantity());
        writeNumber(row.getCell(startColumn + 7), metric.getMonthTons());
        writeNumber(row.getCell(startColumn + 8), metric.getYearTons());
        writeText(row.getCell(startColumn + 9), metric.getRemark());
    }

    private void writeProductRow(XSSFSheet sheet, Styles styles, int rowIndex, int startColumn,
                                 ProductionDailyProductLineVO product) {
        Row row = dataRow(sheet, styles, rowIndex, startColumn);
        writeText(row.getCell(startColumn + 2), product.getProductName());
        writeText(row.getCell(startColumn + 3), ProductionDailyReportCatalog.PRODUCT_UNIT);
        writeNumber(row.getCell(startColumn + 4), product.getDailyActual());
        writeNumber(row.getCell(startColumn + 5), product.getConvertedTons());
        writeNumber(row.getCell(startColumn + 6), product.getMonthQuantity());
        writeNumber(row.getCell(startColumn + 7), product.getMonthTons());
        writeNumber(row.getCell(startColumn + 8), product.getYearTons());
        writeText(row.getCell(startColumn + 9), product.getRemark());
    }

    private void writeEmptyProductRow(XSSFSheet sheet, Styles styles, int rowIndex, int startColumn) {
        dataRow(sheet, styles, rowIndex, startColumn);
    }

    private Row dataRow(XSSFSheet sheet, Styles styles, int rowIndex, int startColumn) {
        Row row = row(sheet, rowIndex, 23);
        for (int column = 0; column < 10; column++) {
            CellStyle style = column >= 4 && column <= 8 ? styles.number
                    : column == 9 ? styles.remark : styles.body;
            cell(row, startColumn + column, style).setBlank();
        }
        return row;
    }

    private int writeNotes(XSSFSheet sheet, Styles styles, int rowIndex, int startColumn) {
        for (String note : NOTES) {
            Row row = row(sheet, rowIndex, 22);
            mergedText(sheet, row, startColumn, startColumn + 9, note, styles.note);
            rowIndex++;
        }
        return rowIndex;
    }

    private void configurePrint(XSSFWorkbook workbook, XSSFSheet sheet, int footerRow) {
        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setLandscape(true);
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        printSetup.setFitWidth((short) 1);
        printSetup.setFitHeight((short) 0);
        sheet.setAutobreaks(true);
        sheet.setHorizontallyCenter(true);
        sheet.setMargin(XSSFSheet.LeftMargin, 0.25);
        sheet.setMargin(XSSFSheet.RightMargin, 0.25);
        sheet.setMargin(XSSFSheet.TopMargin, 0.35);
        sheet.setMargin(XSSFSheet.BottomMargin, 0.35);
        workbook.setPrintArea(workbook.getSheetIndex(sheet), 0, 19, 0, footerRow);
    }

    private ProductionDailyReportSectionVO section(ProductionDailyReportVO report, String code) {
        return report.getSections().stream()
                .filter(section -> code.equals(section.getDepartmentCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少生产日报分区：" + code));
    }

    private void mergedText(XSSFSheet sheet, Row row, int startColumn, int endColumn,
                            String value, CellStyle style) {
        for (int column = startColumn; column <= endColumn; column++) {
            cell(row, column, style);
        }
        if (endColumn > startColumn) {
            CellRangeAddress range = new CellRangeAddress(row.getRowNum(), row.getRowNum(), startColumn, endColumn);
            sheet.addMergedRegion(range);
            applyMergedBorders(range, sheet);
        }
        row.getCell(startColumn).setCellValue(value);
    }

    private void mergeAndBorder(XSSFSheet sheet, int startRow, int endRow, int column) {
        if (endRow <= startRow) {
            return;
        }
        CellRangeAddress range = new CellRangeAddress(startRow, endRow, column, column);
        sheet.addMergedRegion(range);
        applyMergedBorders(range, sheet);
    }

    private void applyMergedBorders(CellRangeAddress range, XSSFSheet sheet) {
        RegionUtil.setBorderTop(BorderStyle.THIN, range, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THIN, range, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THIN, range, sheet);
        RegionUtil.setBorderRight(BorderStyle.THIN, range, sheet);
    }

    private Row row(XSSFSheet sheet, int rowIndex, int heightPoints) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        row.setHeightInPoints(Math.max(row.getHeightInPoints(), heightPoints));
        return row;
    }

    private Cell cell(Row row, int column, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        return cell;
    }

    private void writeNumber(Cell cell, BigDecimal value) {
        if (value == null) {
            cell.setBlank();
        } else {
            cell.setCellValue(value.doubleValue());
        }
    }

    private void writeText(Cell cell, String value) {
        if (value == null || value.isBlank()) {
            cell.setBlank();
        } else {
            cell.setCellValue(value);
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private static final class Styles {
        private final CellStyle title;
        private final CellStyle header;
        private final CellStyle section;
        private final CellStyle group;
        private final CellStyle body;
        private final CellStyle number;
        private final CellStyle remark;
        private final CellStyle note;
        private final CellStyle footer;

        private Styles(XSSFWorkbook workbook) {
            Font titleFont = font(workbook, 18, true);
            Font headerFont = font(workbook, 10, true);
            Font bodyFont = font(workbook, 10, false);

            title = base(workbook, titleFont, HorizontalAlignment.CENTER, true);
            title.setVerticalAlignment(VerticalAlignment.CENTER);
            whiteFill(title);

            header = bordered(workbook, headerFont, HorizontalAlignment.CENTER);
            header.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            section = bordered(workbook, headerFont, HorizontalAlignment.CENTER);
            section.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            section.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            section.setWrapText(true);
            group = bordered(workbook, bodyFont, HorizontalAlignment.CENTER);
            group.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            group.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            group.setWrapText(true);
            body = bordered(workbook, bodyFont, HorizontalAlignment.LEFT);
            whiteFill(body);
            number = bordered(workbook, bodyFont, HorizontalAlignment.RIGHT);
            whiteFill(number);
            number.setDataFormat(workbook.createDataFormat().getFormat("0.######"));
            remark = bordered(workbook, bodyFont, HorizontalAlignment.LEFT);
            whiteFill(remark);
            remark.setWrapText(true);
            note = bordered(workbook, bodyFont, HorizontalAlignment.LEFT);
            whiteFill(note);
            note.setWrapText(true);
            footer = bordered(workbook, bodyFont, HorizontalAlignment.LEFT);
            whiteFill(footer);
        }

        private static Font font(XSSFWorkbook workbook, int size, boolean bold) {
            Font font = workbook.createFont();
            font.setFontName("Microsoft YaHei");
            font.setFontHeightInPoints((short) size);
            font.setBold(bold);
            font.setColor(IndexedColors.BLACK.getIndex());
            return font;
        }

        private static void whiteFill(CellStyle style) {
            style.setFillForegroundColor(IndexedColors.WHITE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        private static CellStyle bordered(XSSFWorkbook workbook, Font font, HorizontalAlignment alignment) {
            CellStyle style = base(workbook, font, alignment, true);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }

        private static CellStyle base(XSSFWorkbook workbook, Font font,
                                      HorizontalAlignment alignment, boolean wrap) {
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setAlignment(alignment);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(wrap);
            return style;
        }
    }
}
