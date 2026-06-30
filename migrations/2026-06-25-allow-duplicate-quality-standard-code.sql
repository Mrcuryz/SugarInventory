SET @idx_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'quality_standards'
      AND index_name = 'uk_quality_standard_code'
);

SET @drop_sql = IF(
    @idx_exists > 0,
    'ALTER TABLE quality_standards DROP INDEX uk_quality_standard_code',
    'SELECT 1'
);

PREPARE drop_quality_standard_code_index FROM @drop_sql;
EXECUTE drop_quality_standard_code_index;
DEALLOCATE PREPARE drop_quality_standard_code_index;
