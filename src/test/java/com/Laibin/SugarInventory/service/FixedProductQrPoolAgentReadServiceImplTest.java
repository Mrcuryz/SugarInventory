package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.FixedProductQrPoolAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.FixedProductQrPoolVO;
import com.Laibin.SugarInventory.service.impl.FixedProductQrPoolAgentReadServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FixedProductQrPoolAgentReadServiceImplTest {
    @Test
    void omitsInternalIdsAndDoesNotClaimPrintingOrActivation() {
        PalletCodeService pallets = mock(PalletCodeService.class); FixedProductQrPoolVO row = new FixedProductQrPoolVO();
        row.setId(99); row.setFixedProductId(77); row.setCode("QR001"); row.setFixedProductName("单晶冰糖"); row.setStatus("FREE"); row.setAllowPrint(true);
        when(pallets.pageFixedProductPool(any())).thenReturn(new PageResult<>(1L, List.of(row)));
        var service = new FixedProductQrPoolAgentReadServiceImpl(pallets);
        var result = service.queryFixedProductQrPool(new FixedProductQrPoolAgentQueryDTO());
        assertThat(result.getRecords()).singleElement().satisfies(item -> assertThat(item.getCode()).isEqualTo("QR001"));
        assertThat(result.toString()).doesNotContain("99", "77");
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不表示标签已打印"));
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不执行绑定"));
    }
}
