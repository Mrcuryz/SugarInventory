package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.productimport.ProductImportWorkbookReader;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class ProductImportWorkbookReaderTest {
    private final ProductImportWorkbookReader reader = new ProductImportWorkbookReader();
    private byte[] bytes(Workbook w) throws Exception {
        try (w; ByteArrayOutputStream out = new ByteArrayOutputStream()) { w.write(out); return out.toByteArray(); }
    }
    private Sheet sheet(Workbook w) {
        Sheet s = w.createSheet("产品");
        Row header = s.createRow(0);
        header.createCell(0).setCellValue("产品名称");
        header.createCell(1).setCellValue("单件重量（kg）");
        header.createCell(2).setCellValue("是否可堆叠");
        s.createRow(1).createCell(0).setCellValue("黄冰糖（袋）");
        return s;
    }
    @Test void readsXlsAndXlsxAndPreservesMissingAndFalse() throws Exception {
        for (Workbook w : new Workbook[]{new XSSFWorkbook(),new HSSFWorkbook()}) {
            Sheet s = sheet(w); s.getRow(1).createCell(2).setCellValue(false);
            var rows = reader.read(bytes(w),null);
            assertEquals(2, rows.get(0).rowNumber());
            assertEquals("否",rows.get(0).values().get("canStack"));
            assertFalse(rows.get(0).values().containsKey("weightPerPiece"));
        }
    }
    @Test void numericFormattingDoesNotRoundTheSource() throws Exception {
        Workbook w=new XSSFWorkbook(); Sheet s=sheet(w); Cell c=s.getRow(1).createCell(1);
        c.setCellValue(14.5); CellStyle style=w.createCellStyle(); style.setDataFormat((short)1); c.setCellStyle(style);
        assertEquals("14.5",reader.read(bytes(w),null).get(0).values().get("weightPerPiece"));
    }
    @Test void formulasAreNotEvaluatedOrTrustedFromCache() throws Exception {
        Workbook w=new XSSFWorkbook(); Sheet s=sheet(w); s.getRow(1).createCell(1).setCellFormula("1+1");
        byte[] data=bytes(w); assertThrows(IllegalArgumentException.class,()->reader.read(data,null));
    }
    @Test void multipleSheetsRequireExplicitSelection() throws Exception {
        Workbook w=new XSSFWorkbook();sheet(w);w.createSheet("其他");byte[] data=bytes(w);
        assertThrows(IllegalArgumentException.class,()->reader.read(data,null));
        assertEquals(1,reader.read(data,"产品").size());
    }
    @Test void unknownAndDuplicateColumnsAreNotSilentlyIgnored() throws Exception {
        for(String title:new String[]{"价格","产品名称"}) {
            Workbook w=new XSSFWorkbook();Sheet s=sheet(w);s.getRow(0).createCell(3).setCellValue(title);
            byte[] data=bytes(w);assertThrows(IllegalArgumentException.class,()->reader.read(data,null));
        }
    }
    @Test void hiddenAndUnlabelledDataRequireReview() throws Exception {
        for(boolean hidden:new boolean[]{true,false}) {
            Workbook w=new XSSFWorkbook();Sheet s=sheet(w);
            if(hidden)s.getRow(1).setZeroHeight(true);else s.getRow(1).createCell(3).setCellValue("不能丢弃");
            byte[] data=bytes(w);assertThrows(IllegalArgumentException.class,()->reader.read(data,null));
        }
    }
    @Test void oversizeAndEmptyFilesRejected() {
        assertThrows(IllegalArgumentException.class,()->reader.read(new byte[0],null));
        assertThrows(IllegalArgumentException.class,()->reader.read(new byte[ProductImportWorkbookReader.MAX_BYTES+1],null));
    }
    @Test void inspectReturnsUnknownHeadersAndHiddenSheetWithoutImportingRows() throws Exception {
        Workbook w = new XSSFWorkbook(); Sheet s = sheet(w);
        s.getRow(0).getCell(1).setCellValue("重量");
        w.createSheet("内部"); w.setSheetHidden(1, true);
        var info = reader.inspect(bytes(w));
        assertEquals("productName", info.get(0).columns().get(0).suggestedField());
        assertNull(info.get(0).columns().get(1).suggestedField());
        assertTrue(info.get(1).hidden());
        assertTrue(info.get(1).columns().isEmpty());
    }
    @Test void explicitMappingAllowsUnknownHeadersButNotDiscardOrDuplicateTargets() throws Exception {
        Workbook w = new XSSFWorkbook(); Sheet s = sheet(w);
        s.getRow(0).getCell(1).setCellValue("净重"); s.getRow(1).createCell(1).setCellValue(15);
        byte[] data = bytes(w);
        assertEquals("15", reader.read(data, null, Map.of(1,"weightPerPiece")).get(0).values().get("weightPerPiece"));
        for (Map<Integer,String> mapping : java.util.List.of(Map.of(1,"productName"), Map.of(1,"ignore"),
                Map.of(1,"productId"), Map.of(20,"weightPerPiece"), Map.of(-1,"weightPerPiece")))
            assertThrows(IllegalArgumentException.class, () -> reader.read(data, null, mapping));
    }
    @Test void refusesExpandedZipBeforePoiCreatesWorkbook() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("large.xml"));
            byte[] chunk = new byte[1024 * 1024];
            for (int i = 0; i < 25; i++) zip.write(chunk);
            zip.closeEntry();
        }
        assertThrows(ProductImportWorkbookReader.ValidationException.class, () -> reader.inspect(out.toByteArray()));
    }
    @Test void rejectsEmbeddedFilesAndExternalLinks() throws Exception {
        for (String name : new String[]{"xl/vbaProject.bin", "xl/embeddings/a.bin", "xl/externalLinks/externalLink1.xml"}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(1); zip.closeEntry();
            }
            assertThrows(ProductImportWorkbookReader.ValidationException.class, () -> reader.inspect(out.toByteArray()));
        }
    }
}
