package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.*;
import com.Laibin.SugarInventory.common.BusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssayEntryExecutionFacadeTest {
    @Test void rejectionIsAuditedAfterTransactionalFailureWithoutReplacingOriginalError() {
        var execution = mock(AssayEntryExecutionService.class); var audit = mock(AssayEntryRejectionAuditService.class);
        var facade = new AssayEntryExecutionFacade(execution, audit);
        var error = new BusinessException(1055, "状态已变化");
        when(execution.confirm(7, "s", "r")).thenThrow(error);
        assertThatThrownBy(() -> facade.confirm(7, "s", "r")).isSameAs(error);
        var order = inOrder(execution, audit); order.verify(execution).confirm(7, "s", "r"); order.verify(audit).rejected(7, "s", "r", 1055);
        doThrow(new IllegalStateException("audit unavailable")).when(audit).rejected(7, "s", "r", 1055);
        assertThatThrownBy(() -> facade.confirm(7, "s", "r")).isSameAs(error);
    }
}
