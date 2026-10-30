package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.service.AssayEntryReferenceService;
import com.Laibin.SugarInventory.agent.service.AssayGroupEntryPreviewService;
import com.Laibin.SugarInventory.controller.AssayGroupEntryPreviewController;
import com.Laibin.SugarInventory.domain.dto.AssayGroupEntryReferencePreviewDTO;
import com.Laibin.SugarInventory.domain.vo.AssayGroupEntryPreviewVO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssayGroupEntryReferencePreviewTest {
    @Test void referencePreviewBindsOwnerAndEchoesReferenceWithoutEnablingConfirmation() {
        var service = mock(AssayGroupEntryPreviewService.class);
        var refs = mock(AssayEntryReferenceService.class);
        var user = mock(LoginUser.class, RETURNS_DEEP_STUBS);
        when(user.getUser().getId()).thenReturn(12);
        var input = new AssayGroupEntryReferencePreviewDTO();
        input.setGroupRef("aer_selected.signature");
        var resolved = AssayGroupEntryPreviewTest.request();
        when(refs.resolveGroup(12, input)).thenReturn(resolved);
        when(service.preview(resolved)).thenReturn(new AssayGroupEntryPreviewVO(true, false,
                8, "冰糖组", resolved.getValues().getSampleDate(), 2, List.of(), List.of()));
        var data = new AssayGroupEntryPreviewController(service, refs).previewFromReference(user, input).getData();
        assertThat(data.groupRef()).isEqualTo(input.getGroupRef());
        assertThat(data.previewOnly()).isTrue();
        assertThat(data.confirmationAvailable()).isFalse();
        verify(refs).resolveGroup(12, input);
        verify(service).preview(resolved);
        verifyNoMoreInteractions(refs, service);
    }

    @Test void invalidReferenceNeverInvokesBusinessPreview() {
        var service = mock(AssayGroupEntryPreviewService.class);
        var refs = mock(AssayEntryReferenceService.class);
        var user = mock(LoginUser.class, RETURNS_DEEP_STUBS);
        when(user.getUser().getId()).thenReturn(12);
        var input = new AssayGroupEntryReferencePreviewDTO();
        when(refs.resolveGroup(12, input)).thenThrow(new com.Laibin.SugarInventory.common.BusinessException(403, "引用无效"));
        assertThatThrownBy(() -> new AssayGroupEntryPreviewController(service, refs).previewFromReference(user, input))
                .isInstanceOf(com.Laibin.SugarInventory.common.BusinessException.class);
        verifyNoInteractions(service);
    }
}
