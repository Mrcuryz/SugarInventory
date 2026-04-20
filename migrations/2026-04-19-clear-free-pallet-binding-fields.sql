-- Clear stale binding fields on FREE pallet codes.
-- FREE means the physical pallet is not bound to any current product cycle.

UPDATE `pallet_code`
SET `product_id` = NULL,
    `product_status` = NULL,
    `production_date` = NULL,
    `screen_mesh_id` = NULL,
    `assay_id` = NULL
WHERE `status` = 'FREE'
  AND (
      `product_id` IS NOT NULL
      OR `product_status` IS NOT NULL
      OR `production_date` IS NOT NULL
      OR `screen_mesh_id` IS NOT NULL
      OR `assay_id` IS NOT NULL
  );
