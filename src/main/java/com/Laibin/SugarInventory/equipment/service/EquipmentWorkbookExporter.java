package com.Laibin.SugarInventory.equipment.service;

import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentAssetVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentRepairVO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public final class EquipmentWorkbookExporter {
    private EquipmentWorkbookExporter() {
    }

    public static byte[] assets(List<EquipmentAssetVO> rows) {
        String[] headers = {"设备编号", "设备子编号", "设备单位", "设备类别", "设备名称", "设备型号",
                "额定功率(kW)", "额定电压(V)", "额定电流(A)", "额定转速(r/min)", "价格(元)", "安装费(元)",
                "安装位置", "厂家编号", "生产厂家", "出厂编号", "生产日期", "进厂日期", "启用日期", "技改类别", "主设备", "说明"};
        return workbook("设备台账", headers, rows.size(), (sheet, index) -> {
            EquipmentAssetVO item = rows.get(index);
            Object[] values = {item.getEquipmentCode(), item.getEquipmentSubNo(), item.getUnitName(), item.getCategoryName(),
                    item.getEquipmentName(), item.getModel(), item.getRatedPowerKw(), item.getRatedVoltageV(),
                    item.getRatedCurrentA(), item.getRatedSpeedRpm(), item.getPrice(), item.getInstallationCost(),
                    item.getInstallationLocation(), item.getManufacturerCode(), item.getManufacturerName(), item.getFactorySerialNo(),
                    item.getProductionDate(), item.getArrivalDate(), item.getCommissioningDate(), item.getRenovationTypeName(),
                    item.getParentEquipmentCode(), item.getRemark()};
            writeRow(sheet.createRow(index + 1), values);
        });
    }

    public static byte[] repairs(List<EquipmentRepairVO> rows) {
        String[] headers = {"设备编号", "设备名称", "设备单位", "设备类别", "修理日期", "修理类型", "修理人", "验收人", "修理内容"};
        return workbook("修理记录", headers, rows.size(), (sheet, index) -> {
            EquipmentRepairVO item = rows.get(index);
            Object[] values = {item.getEquipmentCode(), item.getEquipmentName(), item.getUnitName(), item.getCategoryName(),
                    item.getRepairDate(), item.getRepairTypeName(), item.getRepairPerson(), item.getAcceptancePerson(), item.getRepairContent()};
            writeRow(sheet.createRow(index + 1), values);
        });
    }

    private static byte[] workbook(String sheetName, String[] headers, int size, RowWriter writer) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            writeRow(sheet.createRow(0), headers);
            for (int i = 0; i < size; i++) writer.write(sheet, i);
            for (int i = 0; i < headers.length; i++) sheet.setColumnWidth(i, Math.min(40, Math.max(12, headers[i].length() + 4)) * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("设备数据导出失败", e);
        }
    }

    private static void writeRow(Row row, Object[] values) {
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            Object value = values[i];
            cell.setCellValue(value == null ? "" : String.valueOf(value));
        }
    }

    @FunctionalInterface
    private interface RowWriter {
        void write(Sheet sheet, int index);
    }
}
