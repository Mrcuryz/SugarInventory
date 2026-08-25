-- 仓管只获得受控成品入库执行权限，不授予通用 task:confirm。
-- task:confirm 同时覆盖出库、调拨等写操作，不能作为仓管入库权限的替代品。
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p ON p.`perm_code` = 'agent:finish-inbound:execute'
WHERE r.`role_code` = 'WAREHOUSE_MANAGER';

UPDATE `role`
SET `description` = '仓储查询、受控报表与受控成品入库执行角色；不含出库、调拨、二维码维护或通用任务确认权限'
WHERE `role_code` = 'WAREHOUSE_MANAGER';
