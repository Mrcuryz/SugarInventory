package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.agent.security.AgentEntityRefCodec;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.dto.AssayEntryReferencePreviewDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class AssayEntryReferenceService {
    private static final String TYPE = "ASSAY_ENTRY_PRODUCT";
    private static final String SCOPE = "assay-entry-human-preview";
    private final AgentEntityRefCodec codec;
    public String encode(Integer productId, Integer owner) { return codec.encode(TYPE, productId, owner, SCOPE); }
    public String encodeGroup(Integer groupId, Integer owner) { return codec.encode("ASSAY_ENTRY_GROUP", groupId, owner, SCOPE); }
    public com.Laibin.SugarInventory.domain.dto.AssayGroupEntryPreviewDTO resolveGroup(Integer owner,
            com.Laibin.SugarInventory.domain.dto.AssayGroupEntryReferencePreviewDTO request) {
        if (request == null || request.isUnsupportedFields() || request.getValues() == null
                || request.getValues().getProductId() != null || !request.getValues().getUnsupportedFields().isEmpty())
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_INPUT_INVALID);
        var ref = codec.decodeAndValidate(request.getGroupRef(), "ASSAY_ENTRY_GROUP", owner, SCOPE);
        if (ref.entityId() > Integer.MAX_VALUE) throw new BusinessException(ErrorCode.ASSAY_ENTRY_INPUT_INVALID);
        var input = new com.Laibin.SugarInventory.domain.dto.AssayGroupEntryPreviewDTO();
        input.setGroupId((int) ref.entityId()); input.setValues(request.getValues());
        return input;
    }
    public AssayEntryPreviewDTO resolve(Integer owner, AssayEntryReferencePreviewDTO request) {
        if (request == null || request.isUnsupportedFields() || request.getValues() == null || request.getValues().getProductId() != null) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_INPUT_INVALID);
        }
        var ref = codec.decodeAndValidate(request.getProductRef(), TYPE, owner, SCOPE);
        if (ref.entityId() > Integer.MAX_VALUE) throw new BusinessException(ErrorCode.ASSAY_ENTRY_INPUT_INVALID);
        request.getValues().setProductId((int) ref.entityId());
        return request.getValues();
    }
}
