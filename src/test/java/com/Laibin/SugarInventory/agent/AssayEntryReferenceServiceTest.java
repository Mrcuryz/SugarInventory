package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.security.AgentEntityRefCodec;
import com.Laibin.SugarInventory.agent.service.AssayEntryReferenceService;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.dto.AssayEntryReferencePreviewDTO;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class AssayEntryReferenceServiceTest {
    private final AssayEntryReferenceService service = new AssayEntryReferenceService(new AgentEntityRefCodec("test-only-reference-key-at-least-32-bytes", 600));
    private AssayEntryReferencePreviewDTO request(String ref) {
        var value = new AssayEntryReferencePreviewDTO(); value.setProductRef(ref);
        var input = new AssayEntryPreviewDTO(); input.setSampleDate(LocalDate.of(2026, 9, 24)); input.setColorValue(BigDecimal.ZERO);
        value.setValues(input); return value;
    }
    @Test void resolvesOnlyTheSignedProductForTheSameUser() {
        var input = service.resolve(7, request(service.encode(84, 7)));
        assertThat(input.getProductId()).isEqualTo(84); assertThat(input.getColorValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }
    @Test void rejectsAnotherUserTamperingAndClientProductIds() {
        String ref = service.encode(84, 7);
        assertThatThrownBy(() -> service.resolve(8, request(ref))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.resolve(7, request(ref.substring(0, ref.length()-5) + "xxxxx"))).isInstanceOf(BusinessException.class);
        var changed = request(ref); changed.getValues().setProductId(85);
        assertThatThrownBy(() -> service.resolve(7, changed)).isInstanceOf(BusinessException.class);
        var extra = request(ref); extra.reject("selectType", 2);
        assertThatThrownBy(() -> service.resolve(7, extra)).isInstanceOf(BusinessException.class);
    }
    @Test void groupReferencesAreOwnerAndTypeBoundAndDoNotCarryMembers() {
        var group = new com.Laibin.SugarInventory.domain.dto.AssayGroupEntryReferencePreviewDTO();
        group.setGroupRef(service.encodeGroup(9, 7)); group.setValues(request("unused").getValues());
        var resolved = service.resolveGroup(7, group);
        assertThat(resolved.getGroupId()).isEqualTo(9); assertThat(resolved.getValues().getProductId()).isNull();
        assertThatThrownBy(() -> service.resolveGroup(8, group)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.resolve(7, request(group.getGroupRef()))).isInstanceOf(BusinessException.class);
        group.setGroupRef(service.encode(9, 7));
        assertThatThrownBy(() -> service.resolveGroup(7, group)).isInstanceOf(BusinessException.class);
        group.setGroupRef(service.encodeGroup(9, 7)); group.reject("productIds", java.util.List.of(2, 3));
        assertThatThrownBy(() -> service.resolveGroup(7, group)).isInstanceOf(BusinessException.class);
    }
}
