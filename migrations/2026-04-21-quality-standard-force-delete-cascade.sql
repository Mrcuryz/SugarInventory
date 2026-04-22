-- Allow force delete of quality standards by cascading product-standard relations.

ALTER TABLE `product_quality_standard_relation`
    DROP FOREIGN KEY `fk_product_quality_standard_standard`;

ALTER TABLE `product_quality_standard_relation`
    ADD CONSTRAINT `fk_product_quality_standard_standard`
        FOREIGN KEY (`quality_standard_id`) REFERENCES `quality_standards` (`id`)
        ON DELETE CASCADE ON UPDATE RESTRICT;
