-- Idempotent patch for existing M1.2 Agent Tool audit tables.
-- Some local databases already had agent_tool_audit_log created before
-- request_summary/response_summary/upstream_path were added to code.

CREATE TABLE IF NOT EXISTS `agent_tool_audit_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `agent_session_id` varchar(64) NOT NULL,
  `user_id` int DEFAULT NULL,
  `tool_name` varchar(100) NOT NULL,
  `upstream_path` varchar(500) DEFAULT NULL,
  `arguments_summary` varchar(1000) DEFAULT NULL,
  `request_summary` varchar(1000) DEFAULT NULL,
  `response_summary` varchar(1000) DEFAULT NULL,
  `result_code` varchar(40) DEFAULT NULL,
  `error_code` varchar(80) DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_agent_tool_audit_session_time` (`agent_session_id`, `created_at`),
  KEY `idx_agent_tool_audit_tool_time` (`tool_name`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @schema_name = DATABASE();

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `agent_tool_audit_log` ADD COLUMN `upstream_path` varchar(500) DEFAULT NULL AFTER `tool_name`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'agent_tool_audit_log'
    AND column_name = 'upstream_path'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `agent_tool_audit_log` ADD COLUMN `request_summary` varchar(1000) DEFAULT NULL AFTER `arguments_summary`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'agent_tool_audit_log'
    AND column_name = 'request_summary'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `agent_tool_audit_log` ADD COLUMN `response_summary` varchar(1000) DEFAULT NULL AFTER `request_summary`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'agent_tool_audit_log'
    AND column_name = 'response_summary'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
