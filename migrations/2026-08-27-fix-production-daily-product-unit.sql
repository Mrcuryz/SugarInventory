UPDATE `production_daily_product_line`
SET `unit` = '件'
WHERE `unit` IS NULL OR `unit` <> '件';
