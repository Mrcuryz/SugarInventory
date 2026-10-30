package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayEntryExecutionTokenDTO;
import com.Laibin.SugarInventory.domain.vo.AssayEntryConfirmationVO;
import com.Laibin.SugarInventory.domain.vo.AssayEntryExecutionResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.function.Supplier;

/** Runs rejection audit only after the transactional service has rolled back. Never logs credentials. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssayEntryExecutionFacade {
    private final AssayEntryExecutionService execution;
    private final AssayEntryRejectionAuditService rejectionAudit;
    public AssayEntryConfirmationVO confirm(Integer owner, String session, String ref) {
        return run(owner, session, ref, () -> execution.confirm(owner, session, ref));
    }
    public AssayEntryExecutionResultVO execute(Integer owner, String session, String ref, AssayEntryExecutionTokenDTO request) {
        return run(owner, session, ref, () -> execution.execute(owner, session, ref, request));
    }
    private <T> T run(Integer owner, String session, String ref, Supplier<T> action) {
        try { return action.get(); }
        catch (RuntimeException exception) {
            int code = exception instanceof BusinessException business ? business.getCode() : 500;
            try { rejectionAudit.rejected(owner, session, ref, code); }
            catch (RuntimeException auditError) { log.error("Assay rejection audit unavailable; failureType={}", auditError.getClass().getSimpleName()); }
            throw exception;
        }
    }
}
