INSERT INTO `permission` (`perm_code`, `perm_name`, `description`)
VALUES
    ('agent:use', 'AI 助手使用', '允许进入 AI 助手并在当前账号既有权限范围内执行受控查询与分析')
ON DUPLICATE KEY UPDATE
    `perm_name` = VALUES(`perm_name`),
    `description` = VALUES(`description`);

INSERT INTO `role` (`role_name`, `role_code`, `description`, `status`)
VALUES
    ('仓管', 'WAREHOUSE_MANAGER', '仓储只读查询、受控报表与 AI 助手角色；不含库存、任务或二维码写入权限', 'ENABLED'),
    ('生产主管', 'PRODUCTION_SUPERVISOR', '生产只读查询、受控报表与 AI 助手角色；不含生产或库存写入权限', 'ENABLED')
ON DUPLICATE KEY UPDATE
    `role_name` = VALUES(`role_name`),
    `description` = VALUES(`description`),
    `status` = VALUES(`status`);

-- 管理员保持兼容；质检在既有 QC 业务权限范围内获得助手入口。
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p ON p.`perm_code` = 'agent:use'
WHERE r.`role_code` IN ('ADMIN', 'QC');

-- 仓管角色只包含已由 Controller / 报表定义验证的查询权限，不授予 task:* 写入、
-- inventory:* 写入、二维码维护、主数据维护、审查、审计或 L3 execute 权限。
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p ON p.`perm_code` IN (
    'system:access',
    'dashboard:view',
    'agent:use',
    'product:view',
    'inventory:view',
    'warehouse:view',
    'warehouse_map:view',
    'qrcode:view',
    'qrcode:pool_view',
    'task:view',
    'screen_mesh:view',
    'assay:view',
    'assay:query',
    'quality_standard:view',
    'document:view',
    'record:view',
    'record:query'
)
WHERE r.`role_code` = 'WAREHOUSE_MANAGER';

-- 生产主管可读取生产全链路和“今日运营概览”依赖的库存、化验与任务事实；
-- 不授予生产创建、编辑、作废、领用、产出、标签或任何库存写入权限。
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p ON p.`perm_code` IN (
    'system:access',
    'dashboard:view',
    'agent:use',
    'product:view',
    'inventory:view',
    'warehouse:view',
    'qrcode:view',
    'task:view',
    'assay:view',
    'assay:query',
    'quality_standard:view',
    'document:view',
    'record:view',
    'record:query',
    'production:boiling:view',
    'production:order:view',
    'production:material:view',
    'production:output:view'
)
WHERE r.`role_code` = 'PRODUCTION_SUPERVISOR';
