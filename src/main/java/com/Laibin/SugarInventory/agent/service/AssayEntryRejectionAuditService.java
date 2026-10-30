package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.mapper.AgentAssayEntryAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssayEntryRejectionAuditService {
    private final AgentAssayEntryAuditMapper audit;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(Integer owner, String session, String ref, int code) {
        String safeSession = session != null && session.length() <= 64 ? session : "INVALID";
        String safeRef = ref != null && ref.matches("aep1_[A-Za-z0-9_-]{43}") ? ref : null;
        if (audit.append(owner, safeSession, safeRef, "EXECUTION_REJECTED", code, null) != 1) {
            throw new IllegalStateException("记录化验拒绝审计失败");
        }
    }
}
