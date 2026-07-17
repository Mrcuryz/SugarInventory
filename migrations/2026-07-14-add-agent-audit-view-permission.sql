INSERT INTO `permission` (`perm_code`, `perm_name`, `description`)
SELECT 'agent:audit:view', 'AI 工具审计查看', '允许查看经过安全过滤的 AI 工具调用审计摘要'
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `perm_code` = 'agent:audit:view');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p ON p.`perm_code` = 'agent:audit:view'
WHERE r.`role_code` = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM `role_permission` rp
      WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
