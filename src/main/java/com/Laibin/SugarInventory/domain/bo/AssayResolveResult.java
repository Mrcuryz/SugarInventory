package com.Laibin.SugarInventory.domain.bo;

import com.Laibin.SugarInventory.domain.po.Assay;
import lombok.Data;

@Data
public class AssayResolveResult {
    private Assay assay;
    private String source;
    private String status;
    private String message;
    private boolean autoBound;
    private boolean multipleCandidates;
    private int candidateCount;

    public boolean hasAssay() {
        return assay != null;
    }

    public static AssayResolveResult found(Assay assay, String source) {
        AssayResolveResult result = new AssayResolveResult();
        result.setAssay(assay);
        result.setSource(source);
        result.setStatus(source);
        result.setMessage("已关联化验");
        result.setCandidateCount(1);
        return result;
    }

    public static AssayResolveResult none() {
        AssayResolveResult result = new AssayResolveResult();
        result.setSource("none");
        result.setStatus("none");
        result.setMessage("暂无关联化验");
        return result;
    }

    public static AssayResolveResult multiple(int candidateCount) {
        AssayResolveResult result = new AssayResolveResult();
        result.setSource("multiple_candidates");
        result.setStatus("multiple_candidates");
        result.setMessage("存在多条候选化验，需要人工确认");
        result.setMultipleCandidates(true);
        result.setCandidateCount(candidateCount);
        return result;
    }
}
