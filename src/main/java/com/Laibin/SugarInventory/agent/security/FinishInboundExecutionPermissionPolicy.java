package com.Laibin.SugarInventory.agent.security;

import com.Laibin.SugarInventory.common.BusinessException;

import java.util.Set;

/**
 * Keeps the controlled finished-goods inbound permission separate from the
 * generic task confirmation permission, which also covers outbound and transfer.
 */
public final class FinishInboundExecutionPermissionPolicy {
    public static final String TASK_VIEW = "task:view";
    public static final String TASK_CONFIRM = "task:confirm";
    public static final String CONTROLLED_EXECUTE = "agent:finish-inbound:execute";
    public static final String MANUAL_PREVIEW_SNAPSHOT = "task:confirm,task:view";
    public static final String CONTROLLED_EXECUTION_SNAPSHOT =
            "agent:finish-inbound:execute,task:view";
    public static final Set<String> CONTROLLED_EXECUTION_PERMISSIONS = Set.of(
            CONTROLLED_EXECUTE, TASK_VIEW);

    private FinishInboundExecutionPermissionPolicy() {
    }

    public static String selectPreviewSnapshot(Set<String> currentAuthorities) {
        Set<String> authorities = normalize(currentAuthorities);
        if (!authorities.contains(TASK_VIEW)) {
            throw deniedPreview();
        }
        if (authorities.contains(CONTROLLED_EXECUTE)) {
            return CONTROLLED_EXECUTION_SNAPSHOT;
        }
        if (authorities.contains(TASK_CONFIRM)) {
            return MANUAL_PREVIEW_SNAPSHOT;
        }
        throw deniedPreview();
    }

    public static void requireStoredPreviewSnapshot(
            String requiredPermissions, Set<String> currentAuthorities) {
        Set<String> authorities = normalize(currentAuthorities);
        if (MANUAL_PREVIEW_SNAPSHOT.equals(requiredPermissions)) {
            requireAll(authorities, Set.of(TASK_VIEW, TASK_CONFIRM), deniedPreview());
            return;
        }
        if (CONTROLLED_EXECUTION_SNAPSHOT.equals(requiredPermissions)) {
            requireAll(authorities, CONTROLLED_EXECUTION_PERMISSIONS, deniedPreview());
            return;
        }
        throw new BusinessException(409, "成品入库执行预览权限快照校验失败，请重新预览");
    }

    public static void requireControlledExecution(Set<String> currentAuthorities) {
        requireAll(normalize(currentAuthorities), CONTROLLED_EXECUTION_PERMISSIONS,
                new BusinessException(403, "当前用户没有执行成品入库的权限"));
    }

    private static Set<String> normalize(Set<String> currentAuthorities) {
        return currentAuthorities == null ? Set.of() : currentAuthorities;
    }

    private static BusinessException deniedPreview() {
        return new BusinessException(403, "当前用户没有查看或生成成品入库执行预览的权限");
    }

    private static void requireAll(
            Set<String> authorities, Set<String> required, BusinessException denied) {
        if (!authorities.containsAll(required)) {
            throw denied;
        }
    }
}
