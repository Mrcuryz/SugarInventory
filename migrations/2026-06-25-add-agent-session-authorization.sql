CREATE TABLE IF NOT EXISTS `agent_session` (
  `id` varchar(64) NOT NULL,
  `user_id` int NOT NULL,
  `client_type` varchar(40) DEFAULT NULL,
  `scopes` varchar(500) NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_ip` varchar(80) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `mcp_server_name` varchar(100) DEFAULT NULL,
  `mcp_transport` varchar(40) DEFAULT NULL,
  `issued_at` datetime NOT NULL,
  `expires_at` datetime NOT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `revoked_by` int DEFAULT NULL,
  `revoked_reason` varchar(200) DEFAULT NULL,
  `last_used_at` datetime DEFAULT NULL,
  `last_error_code` varchar(80) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_agent_session_user_status` (`user_id`, `status`),
  KEY `idx_agent_session_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `agent_api_audit_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `agent_session_id` varchar(64) NOT NULL,
  `user_id` int DEFAULT NULL,
  `tool_name` varchar(100) DEFAULT NULL,
  `http_method` varchar(12) DEFAULT NULL,
  `request_path` varchar(500) DEFAULT NULL,
  `response_status` int DEFAULT NULL,
  `result_code` varchar(40) DEFAULT NULL,
  `error_code` varchar(80) DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_agent_api_audit_session_time` (`agent_session_id`, `created_at`),
  KEY `idx_agent_api_audit_user_time` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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



