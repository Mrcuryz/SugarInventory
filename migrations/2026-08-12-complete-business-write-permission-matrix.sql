INSERT INTO `permission` (`perm_code`, `perm_name`, `description`)
VALUES
    ('inventory:inbound', '传统库存入库', '允许调用传统无二维码库存入库接口'),
    ('inventory:outbound', '传统库存出库', '允许调用传统无二维码库存出库接口'),
    ('inventory:transfer', '传统库存调拨', '允许调用传统无二维码库存调拨接口'),
    ('warehouse:create', '库位新增', '允许新增库位'),
    ('warehouse:update', '库位编辑', '允许编辑库位名称和容量配置'),
    ('warehouse:delete', '库位删除', '允许删除空库位'),
    ('warehouse:status', '库位状态维护', '允许切换库位维护状态'),
    ('screen_mesh:create', '筛网新增', '允许新增筛网'),
    ('screen_mesh:update', '筛网编辑', '允许编辑筛网'),
    ('screen_mesh:delete', '筛网删除', '允许删除筛网'),
    ('qrcode:invalidate', '二维码作废与恢复', '允许作废空闲二维码或恢复已作废二维码'),
    ('qrcode:flow_delete', '二维码历史流转删除', '允许删除满足保留期规则的历史流转记录')
ON DUPLICATE KEY UPDATE
    `perm_name` = VALUES(`perm_name`),
    `description` = VALUES(`description`);

-- 管理员承接全部新增权限；后续仍可通过角色管理页面显式调整自定义角色。
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p ON p.`perm_code` IN (
    'inventory:inbound',
    'inventory:outbound',
    'inventory:transfer',
    'warehouse:create',
    'warehouse:update',
    'warehouse:delete',
    'warehouse:status',
    'screen_mesh:create',
    'screen_mesh:update',
    'screen_mesh:delete',
    'qrcode:invalidate',
    'qrcode:flow_delete'
)
WHERE r.`role_code` = 'ADMIN';

-- 现场员工保留历史无二维码库存兼容作业；二维码托盘仍走 task:* 权限控制的任务状态机。
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p ON p.`perm_code` IN (
    'inventory:inbound',
    'inventory:outbound',
    'inventory:transfer'
)
WHERE r.`role_code` = 'STAFF';
