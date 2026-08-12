-- Corrective migration for a schema change that historically existed only under docs/.
-- Every statement is idempotent because some installations may have applied that
-- historical SQL manually before the versioned migration ledger was introduced.

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE pallet_task_semi_item ADD COLUMN prepare_balance_id BIGINT NULL COMMENT ''备料池余额ID'' AFTER semi_pallet_code_id',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pallet_task_semi_item'
      AND COLUMN_NAME = 'prepare_balance_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE pallet_task_semi_item ADD COLUMN board_count INT NULL COMMENT ''登记板数'' AFTER unit',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pallet_task_semi_item'
      AND COLUMN_NAME = 'board_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE pallet_task_semi_item ADD COLUMN piece_count INT NULL COMMENT ''登记件数'' AFTER board_count',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pallet_task_semi_item'
      AND COLUMN_NAME = 'piece_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE pallet_task_semi_item ADD COLUMN total_pieces INT NULL COMMENT ''折算总件数'' AFTER piece_count',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pallet_task_semi_item'
      AND COLUMN_NAME = 'total_pieces'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 1 AND MAX(IS_NULLABLE) = 'NO',
        'ALTER TABLE pallet_task_semi_item MODIFY COLUMN semi_pallet_code_id INT NULL COMMENT ''半成品托盘码ID''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pallet_task_semi_item'
      AND COLUMN_NAME = 'semi_pallet_code_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
