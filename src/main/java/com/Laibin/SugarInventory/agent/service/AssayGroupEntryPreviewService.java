package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.domain.dto.AssayGroupEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.vo.AssayGroupEntryPreviewVO;
import com.Laibin.SugarInventory.mapper.AssayGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AssayGroupEntryPreviewService {
    private final AssayGroupMapper groups;
    private final AssayGroupEntryInputService inputs;
    private final AssayEntryPreviewService single;

    public AssayGroupEntryPreviewVO preview(AssayGroupEntryPreviewDTO request) {
        inputs.validateTarget(request);
        var group = groups.selectById(request.getGroupId());
        var members = inputs.members(group);
        var products = members.stream().map(id -> single.preview(inputs.inputFor(request, id))).toList();
        return new AssayGroupEntryPreviewVO(true, false, group.getId(), group.getStandardName(),
                request.getValues().getSampleDate(), products.size(), products,
                List.of("同一组检测值将用于以上全部产品，各产品分别按自身标准判定并新增版本。",
                        "本次仅为完整预览，尚未保存任何化验记录。"));
    }
}
