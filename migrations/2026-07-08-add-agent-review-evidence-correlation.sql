-- Adds safe correlation fields for Agent Answer Review evidence enrichment.
-- Idempotent for local databases that already contain test audit/review data.

SET @schema_name = DATABASE();

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE agent_tool_audit_log ADD COLUMN message_id varchar(100) DEFAULT NULL AFTER tool_call_id',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'agent_tool_audit_log'
    AND column_name = 'message_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'CREATE INDEX idx_agent_tool_audit_message ON agent_tool_audit_log (agent_session_id, message_id, created_at)',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'agent_tool_audit_log'
    AND index_name = 'idx_agent_tool_audit_message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE agent_message_review ADD COLUMN answer_trace_summary varchar(1000) DEFAULT NULL AFTER assistant_answer_summary',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'agent_message_review'
    AND column_name = 'answer_trace_summary'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE agent_message_review ADD COLUMN agent_decision_snapshot text NULL AFTER answer_trace_summary',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'agent_message_review'
    AND column_name = 'agent_decision_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
