INSERT INTO `permission` (`perm_code`, `perm_name`, `description`)
SELECT 'agent:review:view', 'AI 回答复核查看', '允许查看经过安全过滤的 AI 回答复核状态摘要'
WHERE NOT EXISTS (
    SELECT 1
    FROM `permission`
    WHERE `perm_code` = 'agent:review:view'
);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p ON p.`perm_code` = 'agent:review:view'
WHERE r.`role_code` = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM `role_permission` rp
      WHERE rp.`role_id` = r.`id`
        AND rp.`permission_id` = p.`id`
  );
