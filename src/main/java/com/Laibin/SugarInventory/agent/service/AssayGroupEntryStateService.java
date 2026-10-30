package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.domain.dto.AssayGroupEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.mapper.AssayGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.List;

@Service @RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AssayGroupEntryStateService {
    private final AssayGroupMapper groups;
    private final AssayGroupEntryInputService inputs;
    private final AssayEntryInputValidator validator;
    private final AssayEntryStateService states;

    public GroupState lockAndRead(AssayGroupEntryPreviewDTO request) {
        inputs.validateTarget(request);
        var group = groups.selectForAssayEntryUpdate(request.getGroupId());
        var members = inputs.members(group);
        var values = members.stream().map(id -> validator.normalize(inputs.inputFor(request, id))).toList();
        var productStates = states.lockAndReadAll(values);
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("groupId", group.getId()); snapshot.put("groupName", group.getStandardName());
        snapshot.put("memberProductIds", members);
        snapshot.put("productStates", productStates.stream().map(AssayEntryStateService.State::snapshotJson).toList());
        return new GroupState(group.getId(), group.getStandardName(), values, productStates, states.json(snapshot));
    }

    public record GroupState(Integer groupId, String groupName, List<AssaySubmitDTO> inputs,
                             List<AssayEntryStateService.State> products, String snapshotJson) {
        public String digest() { return AssayEntryStateService.digest(snapshotJson); }
    }
}
