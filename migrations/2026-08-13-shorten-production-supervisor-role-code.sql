-- user.role_code 与 employee_roster.role_code 仍为 varchar(20)，而 role.role_code 已为 varchar(50)。
-- 使用可被现有用户字段保存的预置代码，避免扩大历史字段并引入额外兼容风险。
UPDATE `role`
SET `role_code` = 'PROD_SUPERVISOR'
WHERE `role_code` = 'PRODUCTION_SUPERVISOR'
  AND NOT EXISTS (
      SELECT 1
      FROM (SELECT `role_code` FROM `role`) existing_role
      WHERE existing_role.`role_code` = 'PROD_SUPERVISOR'
  );
