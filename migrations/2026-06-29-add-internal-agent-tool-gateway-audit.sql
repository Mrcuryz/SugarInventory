-- Adds a stable tool call correlation field for the Java Internal Agent Tool Gateway.
-- The migration is idempotent for existing M1.2 audit tables.

SET @schema_name = DATABASE();

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE agent_tool_audit_log ADD COLUMN tool_call_id varchar(100) DEFAULT NULL AFTER tool_name',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'agent_tool_audit_log'
    AND column_name = 'tool_call_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'CREATE INDEX idx_agent_tool_audit_call_id ON agent_tool_audit_log (tool_call_id)',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'agent_tool_audit_log'
    AND index_name = 'idx_agent_tool_audit_call_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
