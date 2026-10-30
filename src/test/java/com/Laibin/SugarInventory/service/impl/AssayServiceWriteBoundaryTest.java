package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.AssayGroup;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.service.AssayGroupService;
import com.Laibin.SugarInventory.service.AssayWriteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssayServiceWriteBoundaryTest {
    private final AssayServiceImpl service = new AssayServiceImpl();
    private final AssayWriteService writer = mock(AssayWriteService.class);
    private final AssayMapper mapper = mock(AssayMapper.class);
    private final AssayGroupService groups = mock(AssayGroupService.class);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "assayWriteService", writer);
        ReflectionTestUtils.setField(service, "assayMapper", mapper);
        ReflectionTestUtils.setField(service, "assayGroupService", groups);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void importsLockWholeBatchBeforeAppendingInInputOrder() {
        var a = input(9); var b = input(2);
        service.importAssays(List.of(a, b), 7);
        var order = inOrder(writer);
        order.verify(writer).lockProducts(List.of(9, 2));
        order.verify(writer).append(a, 7);
        order.verify(writer).append(b, 7);
        verifyNoInteractions(mapper);
    }

    @Test
    void emptyOrMixedInputCannotPretendToImport() {
        var group = input(2); group.setSelectType(2);
        assertThatThrownBy(() -> service.importAssays(List.of(), 7)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.importAssays(List.of(input(9), group), 7)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.importAssays(List.of(group, input(9)), 7)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(writer, mapper);
    }

    @Test
    void groupLocksSortedMembersAndDoesNotMutateOriginalDto() {
        var input = input(null); input.setSelectType(2); input.setRelatedId(10);
        var group = new AssayGroup(); group.setRelatedProducts("9,2,9");
        when(groups.getById(10)).thenReturn(group);
        service.importAssays(List.of(input), 7);
        var order = inOrder(writer);
        order.verify(writer).lockProducts(List.of(2, 9));
        order.verify(writer).append(argThat(dto -> dto.getProductId() == 2), eq(7));
        order.verify(writer).append(argThat(dto -> dto.getProductId() == 9), eq(7));
        assertThat(input.getProductId()).isNull();
    }

    @Test
    void emptyMalformedAndMissingGroupMembersFailBeforeAnyAppend() {
        var input = input(null); input.setSelectType(2); input.setRelatedId(10);
        var group = new AssayGroup(); when(groups.getById(10)).thenReturn(group);
        for (String members : List.of("", "2,", "not-an-id")) {
            group.setRelatedProducts(members);
            assertThatThrownBy(() -> service.importAssays(List.of(input), 7)).isInstanceOf(BusinessException.class);
        }
        group.setRelatedProducts("2,9");
        when(writer.lockProducts(any())).thenThrow(new BusinessException("产品不存在"));
        assertThatThrownBy(() -> service.importAssays(List.of(input), 7)).isInstanceOf(BusinessException.class);
        verify(writer, never()).append(any(), any());
    }

    @Test
    void updateAppendsOnceWithoutRewritingInsertedVersion() throws Exception {
        var input = input(2); var user = new User(); user.setId(7);
        var old = new Assay(); old.setId(10); old.setProductId(2); old.setSampleDate(input.getSampleDate());
        var saved = new Assay(); saved.setId(11); saved.setVersion(4);
        when(mapper.selectById(10)).thenReturn(old); when(writer.append(input, 7)).thenReturn(saved);
        assertThat(service.updateAssay(10, input, user).getVersion()).isEqualTo(4);
        var order = inOrder(writer, mapper);
        order.verify(writer).lockProducts(List.of(2)); order.verify(mapper).selectById(10);
        order.verify(writer).append(input, 7);
        verify(mapper, never()).updateById(any(Assay.class));
    }

    @Test
    void updateCannotUseAnUnrelatedPathRecord() {
        var input = input(2); var old = new Assay(); old.setProductId(9); old.setSampleDate(input.getSampleDate());
        when(mapper.selectById(10)).thenReturn(old);
        assertThatThrownBy(() -> service.updateAssay(10, input, new User())).isInstanceOf(BusinessException.class);
        verify(writer, never()).append(any(), any());
    }

    @Test
    void deletionUsesSameProductLockAndTransaction() throws Exception {
        var old = new Assay(); old.setProductId(2); when(mapper.selectById(10)).thenReturn(old);
        when(mapper.deleteById(10)).thenReturn(1); service.deleteAssay(10);
        var order = inOrder(writer, mapper);
        order.verify(mapper).selectById(10); order.verify(writer).lockProducts(List.of(2)); order.verify(mapper).deleteById(10);
        assertThat(AssayServiceImpl.class.getMethod("deleteAssay", Integer.class).isAnnotationPresent(Transactional.class)).isTrue();
    }

    private static AssaySubmitDTO input(Integer id) {
        var input = new AssaySubmitDTO(); input.setProductId(id); input.setSampleDate(LocalDate.of(2026, 9, 24)); return input;
    }
}
