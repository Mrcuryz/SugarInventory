package com.Laibin.SugarInventory.agent.productimport;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.security.AgentEntityRefCodec;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.mapper.ProductImportCatalogMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductImportCandidatesTest {
    final ProductImportAccessService access = mock(ProductImportAccessService.class);
    final ProductImportDraftStore store = mock(ProductImportDraftStore.class);
    final ProductImportCatalogMapper catalog = mock(ProductImportCatalogMapper.class);
    final AgentEntityRefCodec refs = new AgentEntityRefCodec("local-test-only-012345678901234567890123", 600);
    final ProductImportCandidates service = new ProductImportCandidates(access, store, catalog, refs);
    final LoginUser user = mock(LoginUser.class);

    private void prepare() {
        when(access.requireCurrent(user, "s")).thenReturn(7);
        when(store.get(7, "s", "a")).thenReturn(new ProductImportDraftStore.Draft("a", 7, "s", "产品.xlsx", "sha", 999,
                List.of(new ProductImportWorkbookReader.SourceRow("目录", 3, Map.of("productName", "黄冰糖（袋）")),
                        new ProductImportWorkbookReader.SourceRow("目录", 9, Map.of("productName", "黄冰糖(袋)")))));
    }

    private ProductImportCatalogMapper.Entry entry(int id, String name) {
        var e = new ProductImportCatalogMapper.Entry();
        e.setId(id); e.setProductName(name); e.setHasInventory(true);
        return e;
    }

    @Test void exactBeforeShortlistAndSignedForPhysicalRow() {
        prepare();
        var entries = new ArrayList<ProductImportCatalogMapper.Entry>();
        for (int i=1; i<=15; i++) entries.add(entry(i, "产品"+i));
        entries.add(entry(16, "黄冰糖(袋)"));
        when(catalog.readForMatching()).thenReturn(entries);
        var result = service.read(user, "s", "a", 3);
        assertEquals(List.of(3,9), result.duplicateRows());
        assertEquals(1, result.exactCount());
        assertFalse(result.allCandidatesShown());
        assertEquals(10, result.candidates().size());
        var first = result.candidates().get(0);
        assertTrue(first.hasInventory());
        assertEquals("黄冰糖(袋)", first.values().get("productName"));
        assertEquals(16L, refs.decodeAndValidate(first.ref(),"PRODUCT_IMPORT_TARGET",7,"product-import:s:a:3").entityId());
        assertThrows(BusinessException.class, () -> refs.decodeAndValidate(first.ref(),"PRODUCT_IMPORT_TARGET",7,"product-import:s:a:9"));
    }

    @Test void invalidRowsAndUnauthorizedRequestsCannotQueryCatalog() {
        prepare();
        assertThrows(BusinessException.class, () -> service.read(user,"s","a",2));
        when(access.requireCurrent(user,"other")).thenThrow(new BusinessException(403,"禁止访问"));
        assertThrows(BusinessException.class, () -> service.read(user,"other","a",3));
        verifyNoInteractions(catalog);
        verify(store, times(1)).get(anyInt(),anyString(),anyString());
    }

    @Test void catalogOverflowAndUnknownInventoryNeverBecomeAnEmptyMatch() {
        prepare();
        var e = entry(1, "黄冰糖"); e.setHasInventory(null);
        when(catalog.readForMatching()).thenReturn(List.of(e));
        assertThrows(BusinessException.class, () -> service.read(user,"s","a",3));
        when(catalog.readForMatching()).thenReturn(Collections.nCopies(501,entry(2,"白冰糖")));
        assertThrows(BusinessException.class, () -> service.read(user,"s","a",3));
    }
}
