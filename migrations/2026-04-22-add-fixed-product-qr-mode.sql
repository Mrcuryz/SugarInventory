ALTER TABLE `pallet_code`
    ADD COLUMN `fixed_product_id` int NULL DEFAULT NULL COMMENT '固定绑定的产品ID' AFTER `product_id`,
    ADD COLUMN `fixed_mode_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用固定产品二维码模式' AFTER `fixed_product_id`,
    ADD INDEX `idx_pallet_code_fixed_product` (`fixed_product_id`),
    ADD INDEX `idx_pallet_code_fixed_mode_status` (`fixed_mode_enabled`, `status`, `updated_at`),
    ADD CONSTRAINT `fk_pallet_code_fixed_product`
        FOREIGN KEY (`fixed_product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;
