INSERT INTO `permission` (`perm_code`, `perm_name`, `description`)
VALUES
  ('agent:review:view', 'AI Review 查看', '允许查看 AI 助手 Review Lite 审查列表和安全详情'),
  ('agent:review:update', 'AI Review 标记', '允许标记 AI 助手 Review 状态和保存管理员备注')
ON DUPLICATE KEY UPDATE
  `perm_name` = VALUES(`perm_name`),
  `description` = VALUES(`description`);

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p ON p.`perm_code` IN ('agent:review:view', 'agent:review:update')
WHERE r.`role_code` IN ('ADMIN', 'SUPER_ADMIN');
