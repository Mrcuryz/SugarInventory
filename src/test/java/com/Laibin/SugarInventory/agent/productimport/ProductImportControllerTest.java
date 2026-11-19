package com.Laibin.SugarInventory.agent.productimport;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.controller.ProductImportController;
import com.Laibin.SugarInventory.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductImportControllerTest {
    private final ProductImportUploadService uploads = mock(ProductImportUploadService.class);
    private final ProductImportAccessService access = mock(ProductImportAccessService.class);
    private final ProductImportDraftStore drafts = mock(ProductImportDraftStore.class);
    private final ObjectMapper json = new ObjectMapper();
    private final ProductImportController controller = new ProductImportController(uploads,access,drafts,json);
    private final LoginUser user = mock(LoginUser.class);
    private final MockMultipartFile file = new MockMultipartFile("file",new byte[]{1});
    private final ProductImportDraftStore.Draft draft = new ProductImportDraftStore.Draft("ref",7,"s","产品.xlsx","digest",100,
            IntStream.range(0,80).mapToObj(i -> new ProductImportWorkbookReader.SourceRow("产品",i+2,Map.of("productName","产品"+i))).toList());

    @Test void disabledUnlessExplicitlyEnabled() {
        var runner = new ApplicationContextRunner().withUserConfiguration(ProductImportController.class)
                .withBean(ProductImportUploadService.class,()->uploads)
                .withBean(ProductImportAccessService.class,()->access)
                .withBean(ProductImportDraftStore.class,()->drafts)
                .withBean(ObjectMapper.class,()->json);
        runner.run(context -> assertTrue(context.getBeansOfType(ProductImportController.class).isEmpty()));
        runner.withPropertyValues("agent.product-import.upload-enabled=true").run(context ->
                assertNotNull(context.getBean(ProductImportController.class)));
    }

    @Test void parsesExplicitMappingAndReturnsReceiptWithoutOwnerIdsOrRawRows() throws Exception {
        when(uploads.upload(user,"s",file,"产品",Map.of(0,"productName"))).thenReturn(draft);
        var receipt=controller.upload(user,"s",file,"产品","{\"0\":\"productName\"}").getData();
        assertEquals(80,receipt.rowCount()); assertEquals("产品",receipt.sheet());
        JsonNodeAssertions.receipt(json.readTree(json.writeValueAsString(receipt)));
    }

    @Test void duplicateUnknownShapeAndTrailingMappingContentNeverReachUpload() {
        for (String mapping : List.of("[]","null","{\"0\":1}","{\"01\":\"productName\"}",
                "{\"-1\":\"productName\"}","{\"0\":\"productName\",\"0\":\"status\"}","{} {}"))
            assertEquals(400,assertThrows(BusinessException.class,()->controller.upload(user,"s",file,null,mapping)).getCode());
        verifyNoInteractions(uploads);
    }

    @Test void sourcePagesAreBoundedAndKeepPhysicalExcelRowNumber() {
        when(access.requireCurrent(user,"s")).thenReturn(7);
        when(drafts.get(7,"s","ref")).thenReturn(draft);
        var page=controller.read(user,"s","ref",2,50).getData();
        assertEquals(30,page.rows().size()); assertEquals(52,page.rows().get(0).rowNumber());
        assertTrue(controller.read(user,"s","ref",3,50).getData().rows().isEmpty());
        assertThrows(BusinessException.class,()->controller.read(user,"s","ref",Integer.MAX_VALUE,50));
        assertThrows(BusinessException.class,()->controller.read(user,"s","ref",1,51));
    }

    @Test void lostAccessPreventsReadAndDelete() {
        when(access.requireCurrent(user,"s")).thenThrow(new BusinessException(403,"无权限"));
        assertThrows(BusinessException.class,()->controller.read(user,"s","ref",1,50));
        assertThrows(BusinessException.class,()->controller.remove(user,"s","ref"));
        verifyNoInteractions(drafts);
    }

    private static class JsonNodeAssertions {
        static void receipt(com.fasterxml.jackson.databind.JsonNode value) {
            assertFalse(value.has("owner")); assertFalse(value.has("rows")); assertFalse(value.has("sessionId"));
        }
    }
}
