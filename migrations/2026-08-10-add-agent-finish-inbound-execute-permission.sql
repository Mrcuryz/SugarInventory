INSERT INTO `permission` (`perm_code`, `perm_name`, `description`)
SELECT
    'agent:finish-inbound:execute',
    'AI 成品入库执行',
    '允许在受控 AI 助手界面确认并执行已经完成精确预览的成品入库'
WHERE NOT EXISTS (
    SELECT 1
    FROM `permission`
    WHERE `perm_code` = 'agent:finish-inbound:execute'
);

-- 安全默认值：本迁移只登记权限，不自动分配给任何角色。
-- 具体角色必须在隔离 UAT 和职责评审后显式授权。
