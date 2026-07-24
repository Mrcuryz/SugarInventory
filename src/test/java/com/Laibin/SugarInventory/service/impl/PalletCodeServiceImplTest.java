package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.mapper.PalletCodeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PalletCodeServiceImplTest {

    @Test
    void parseAndFindDistinguishesInvalidCodeFromMissingPallet() {
        PalletCodeServiceImpl service = new PalletCodeServiceImpl();

        assertThatThrownBy(() -> service.parseAndFind("BT999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_PALLET_CODE.getCode());

        PalletCodeMapper mapper = mock(PalletCodeMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        assertThatThrownBy(() -> service.parseAndFind("BTZZZZZZ"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PALLET_CODE_NOT_FOUND.getCode());
    }
}
