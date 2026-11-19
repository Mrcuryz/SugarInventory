package com.Laibin.SugarInventory.agent.productimport;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.BusinessException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductImportUploadServiceTest {
    private final ProductImportAccessService access = mock(ProductImportAccessService.class);
    private final ProductImportDraftStore drafts = mock(ProductImportDraftStore.class);
    private final ProductImportUploadService uploads = new ProductImportUploadService(access,drafts);
    private final LoginUser user = mock(LoginUser.class);
    private MockMultipartFile file(String column, String name) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("产品"); sheet.createRow(0).createCell(0).setCellValue(column);
            sheet.createRow(1).createCell(0).setCellValue("黄冰糖（袋）");
            workbook.write(out);
            return new MockMultipartFile("file",name,"application/octet-stream",out.toByteArray());
        }
    }
    @Test void inspectingDoesNotCreateDraftAndUploadUsesHashAndSanitizedName() throws Exception {
        when(access.requireCurrent(user,"s")).thenReturn(7);
        var file = file("产品名称","C:\\fakepath\\产品.xlsx");
        assertEquals(1,uploads.inspect(user,"s",file).size());
        verifyNoInteractions(drafts);
        uploads.upload(user,"s",file,null,Map.of());
        verify(drafts).create(eq(7),eq("s"),eq("产品.xlsx"),matches("[0-9a-f]{64}"),argThat(rows ->
                rows.size()==1 && rows.get(0).rowNumber()==2 && "黄冰糖（袋）".equals(rows.get(0).values().get("productName"))));
        verify(access,times(3)).requireCurrent(user,"s");
    }
    @Test void revokedPermissionAfterParsingPreventsStorage() throws Exception {
        when(access.requireCurrent(user,"s")).thenReturn(7).thenThrow(new BusinessException(403,"权限已撤销"));
        var file=file("产品名称","产品.xlsx");
        assertEquals(403,assertThrows(BusinessException.class,()->uploads.upload(user,"s",file,null,Map.of())).getCode());
        verifyNoInteractions(drafts);
    }
    @Test void invalidHeaderReturnsActionableMessageAndDoesNotStore() throws Exception {
        var file=file("名称","产品.xlsx");
        var error=assertThrows(BusinessException.class,()->uploads.upload(user,"s",file,null,Map.of()));
        assertTrue(error.getMessage().contains("列对应关系"));
        verifyNoInteractions(drafts);
        uploads.upload(user,"s",file,null,Map.of(0,"productName"));
        verify(drafts).create(any(),eq("s"),eq("产品.xlsx"),anyString(),anyList());
    }
    @Test void invalidExtensionOrCorruptContentFailsWithoutInternalDetails() {
        for (String name : new String[]{"file.exe","file.xlsm","file.xlsx"}) {
            var file=new MockMultipartFile("file",name,"application/octet-stream","not excel".getBytes());
            var error=assertThrows(BusinessException.class,()->uploads.inspect(user,"s",file));
            assertEquals(400,error.getCode()); assertFalse(error.getMessage().contains("org.apache.poi"));
        }
        verifyNoInteractions(drafts);
    }
}
