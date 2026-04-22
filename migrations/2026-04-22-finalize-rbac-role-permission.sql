ALTER TABLE `role`
    MODIFY COLUMN `role_code` varchar(50) NOT NULL COMMENT '角色代码',
    ADD COLUMN `status` varchar(20) NOT NULL DEFAULT 'ENABLED' COMMENT '角色状态：ENABLED/DISABLED' AFTER `description`,
    ADD COLUMN `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `created_at`;

ALTER TABLE `role`
    DROP INDEX `role_code`,
    ADD UNIQUE INDEX `uk_role_code` (`role_code`);

INSERT INTO `role` (`role_name`, `role_code`, `description`, `status`)
VALUES
    ('管理员', 'ADMIN', '系统管理员，拥有全部权限', 'ENABLED'),
    ('化验员', 'QC', '化验与质量相关业务角色', 'ENABLED'),
    ('现场员工', 'STAFF', '现场作业与查询角色', 'ENABLED')
ON DUPLICATE KEY UPDATE
    `role_name` = VALUES(`role_name`),
    `description` = VALUES(`description`),
    `status` = VALUES(`status`);

INSERT INTO `permission` (`perm_code`, `perm_name`, `description`)
VALUES
    ('system:access', '系统访问', '允许登录 Web 管理端'),
    ('dashboard:view', '首页查看', '允许查看首页工作台'),
    ('product:view', '产品管理查看', '允许查看产品管理页面'),
    ('inventory:view', '库存汇总查看', '允许查看库存汇总与库存查询页面'),
    ('warehouse:view', '库位管理查看', '允许查看库位管理页面'),
    ('warehouse_map:view', '仓库平面图查看', '允许查看仓库平面图'),
    ('qrcode:view', '二维码管理查看', '允许查看二维码管理页面'),
    ('qrcode:generate', '二维码生成', '允许生成二维码'),
    ('qrcode:print', '二维码打印', '允许打印二维码'),
    ('qrcode:bind_fixed_product', '固定产品绑定', '允许为二维码绑定固定产品'),
    ('qrcode:activate', '二维码启用', '允许打印并启用固定产品二维码'),
    ('qrcode:pool_view', '固定产品二维码池查看', '允许查看固定产品二维码池'),
    ('task:view', '任务中心查看', '允许查看任务中心'),
    ('task:create', '任务创建', '允许创建任务'),
    ('task:confirm', '任务确认', '允许确认任务'),
    ('task:cancel', '任务取消', '允许取消任务'),
    ('screen_mesh:view', '筛网管理查看', '允许查看筛网管理页面'),
    ('assay:view', '化验管理查看', '允许查看化验管理页面'),
    ('assay:create', '化验新增', '允许新增化验记录'),
    ('assay:update', '化验编辑', '允许编辑化验记录'),
    ('assay:delete', '化验删除', '允许删除化验记录'),
    ('assay:query', '化验查询', '允许查询化验记录'),
    ('assay:copy', '化验复制', '允许复制化验记录'),
    ('assay_group:view', '批量化验组查看', '允许查看批量化验组页面'),
    ('assay_group:create', '批量化验组新增', '允许新增批量化验组'),
    ('assay_group:update', '批量化验组编辑', '允许编辑批量化验组'),
    ('assay_group:delete', '批量化验组删除', '允许删除批量化验组'),
    ('quality_standard:view', '化验标准查看', '允许查看化验标准管理页面'),
    ('quality_standard:create', '化验标准新增', '允许新增化验标准'),
    ('quality_standard:update', '化验标准编辑', '允许编辑化验标准'),
    ('quality_standard:delete', '化验标准删除', '允许删除化验标准'),
    ('quality_standard:bind_product', '化验标准绑定产品', '允许维护产品与化验标准关联'),
    ('document:view', '单据中心查看', '允许查看单据中心'),
    ('record:view', '记录查看', '允许查看操作日志与业务记录'),
    ('record:export', '记录导出', '允许导出记录'),
    ('employee:view', '员工管理查看', '允许查看员工管理页面'),
    ('employee:create', '员工新增', '允许新增员工'),
    ('employee:update', '员工编辑', '允许编辑员工'),
    ('employee:delete', '员工删除', '允许删除员工'),
    ('rbac:user:view', '用户管理查看', '允许查看用户管理页面'),
    ('rbac:user:update_role', '用户分配角色', '允许给用户分配角色'),
    ('rbac:user:enable_disable', '用户启停', '允许启用或停用用户'),
    ('rbac:user:reset_password', '用户重置凭证', '允许重置用户登录凭证'),
    ('rbac:role:view', '角色管理查看', '允许查看角色管理页面'),
    ('rbac:role:create', '角色新增', '允许新增角色'),
    ('rbac:role:update', '角色编辑', '允许编辑角色'),
    ('rbac:role:delete', '角色删除', '允许删除角色'),
    ('rbac:role:assign_permission', '角色分配权限', '允许给角色分配权限'),
    ('user:create', '员工导入/新增', '兼容旧接口：员工导入与新增'),
    ('user:update', '员工编辑', '兼容旧接口：员工编辑'),
    ('user:delete', '员工删除', '兼容旧接口：员工删除'),
    ('quality:test', '化验操作', '兼容旧接口：化验录入与修改'),
    ('record:query', '库存与记录查询', '兼容旧接口：库存与记录查询'),
    ('log:view', '操作日志查看', '兼容旧接口：查看操作日志'),
    ('product:create', '产品新增', '兼容旧接口：产品/筛网新增'),
    ('product:update', '产品编辑', '兼容旧接口：产品/筛网编辑'),
    ('product:delete', '产品删除', '兼容旧接口：产品/筛网删除')
ON DUPLICATE KEY UPDATE
    `perm_name` = VALUES(`perm_name`),
    `description` = VALUES(`description`);

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p
WHERE r.`role_code` = 'ADMIN';

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p
WHERE r.`role_code` = 'QC'
  AND p.`perm_code` IN (
      'system:access',
      'dashboard:view',
      'product:view',
      'inventory:view',
      'warehouse:view',
      'warehouse_map:view',
      'qrcode:view',
      'qrcode:pool_view',
      'task:view',
      'task:create',
      'task:confirm',
      'task:cancel',
      'screen_mesh:view',
      'assay:view',
      'assay:create',
      'assay:update',
      'assay:delete',
      'assay:query',
      'assay:copy',
      'assay_group:view',
      'assay_group:create',
      'assay_group:update',
      'quality_standard:view',
      'record:view',
      'record:query',
      'quality:test'
  );

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p
WHERE r.`role_code` = 'STAFF'
  AND p.`perm_code` IN (
      'dashboard:view',
      'inventory:view',
      'warehouse:view',
      'qrcode:view',
      'task:view',
      'task:create',
      'task:confirm',
      'task:cancel',
      'assay:query',
      'record:query'
  );
