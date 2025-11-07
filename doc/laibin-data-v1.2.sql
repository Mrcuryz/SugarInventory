/*
 Navicat Premium Data Transfer

 Source Server         : 39.98.119.105_3306
 Source Server Type    : MySQL
 Source Server Version : 80027
 Source Host           : 39.98.119.105:3306
 Source Schema         : laibin

 Target Server Type    : MySQL
 Target Server Version : 80027
 File Encoding         : 65001

 Date: 22/07/2025 08:43:51
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for assay
-- ----------------------------
DROP TABLE IF EXISTS `assay`;
CREATE TABLE `assay`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '检测ID',
  `product_id` int NOT NULL COMMENT '产品ID',
  `sample_date` date NOT NULL COMMENT '抽样日期',
  `color_value` decimal(5, 2) NULL DEFAULT NULL COMMENT '色值',
  `reducing_sugar` decimal(5, 2) NULL DEFAULT NULL COMMENT '还原糖分',
  `dry_weight` decimal(10, 2) NULL DEFAULT NULL COMMENT '干燥失重',
  `conductivity_ash` decimal(10, 2) NULL DEFAULT NULL COMMENT '电导灰分',
  `sucrose` decimal(10, 2) NULL DEFAULT NULL COMMENT '蔗糖分',
  `insoluble_impurity` decimal(10, 2) NULL DEFAULT NULL COMMENT '不溶于水杂质',
  `ph_value` decimal(5, 2) NULL DEFAULT NULL COMMENT 'pH值',
  `tested_by` int NOT NULL COMMENT '检测员ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `version` int NOT NULL DEFAULT 1 COMMENT '版本',
  `qualified_standards` json NOT NULL COMMENT '达标的标准名称数组（如[\"中粮优级\",\"国家一级\"]）',
  `is_qualified` enum('合格','不合格') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '是否合格',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_product_date_version`(`product_id`, `sample_date`, `version`) USING BTREE,
  INDEX `tested_by`(`tested_by`) USING BTREE,
  INDEX `idx_sample_date`(`sample_date`) USING BTREE,
  CONSTRAINT `assay_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `assay_ibfk_2` FOREIGN KEY (`tested_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 342 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of assay
-- ----------------------------
INSERT INTO `assay` VALUES (319, 47, '2025-07-03', 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2, '2025-07-03 14:00:48', 1, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (320, 48, '2025-07-03', 10.00, 10.00, 10.00, 10.00, 10.00, 10.00, 10.00, 2, '2025-07-03 22:42:22', 1, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (321, 66, '2025-07-04', 10.00, 10.00, 10.00, 10.00, 10.00, 10.00, 10.00, 2, '2025-07-04 13:47:38', 1, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (322, 66, '2025-07-04', 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2, '2025-07-04 21:43:55', 2, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (323, 66, '2025-07-04', 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2, '2025-07-07 12:10:27', 3, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (324, 66, '2025-07-04', 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 2, '2025-07-09 17:35:26', 4, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (325, 66, '2025-07-04', 10.00, 10.00, 10.00, 10.00, 10.00, 10.00, 10.00, 2, '2025-07-09 20:10:51', 5, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (326, 47, '2025-07-09', 9.00, 9.00, 9.00, 9.00, 9.00, 9.00, 9.00, 2, '2025-07-09 20:15:23', 1, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (327, 47, '2025-07-10', 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 2, '2025-07-10 16:04:27', 1, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (328, 66, '2025-07-10', 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 2, '2025-07-10 16:12:53', 1, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (330, 68, '2025-07-10', 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 2, '2025-07-10 16:19:49', 1, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (331, 68, '2025-07-10', 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 2, '2025-07-10 16:20:39', 2, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (334, 68, '2025-07-10', 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 2, '2025-07-10 16:24:59', 3, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (335, 68, '2025-07-10', 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 37, '2025-07-10 16:29:05', 4, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (336, 68, '2025-07-10', 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 13, '2025-07-10 16:32:06', 5, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (338, 66, '2025-07-10', 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 2, '2025-07-10 16:38:06', 2, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (339, 68, '2025-07-10', 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 11.00, 2, '2025-07-11 18:26:29', 6, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (340, 47, '2025-07-16', 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 37, '2025-07-16 16:17:30', 1, '[\"黄冰糖\"]', '合格');
INSERT INTO `assay` VALUES (341, 69, '2025-07-16', 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 37, '2025-07-16 16:19:39', 1, '[\"黄冰糖\"]', '合格');

-- ----------------------------
-- Table structure for employee_roster
-- ----------------------------
DROP TABLE IF EXISTS `employee_roster`;
CREATE TABLE `employee_roster`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工号',
  `name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '姓名',
  `mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '手机号',
  `department` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '所属部门',
  `position` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '职位',
  `status` enum('在职','离职') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '在职',
  `role_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'STAFF' COMMENT '预设角色',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `employee_id`(`employee_id`) USING BTREE,
  UNIQUE INDEX `mobile`(`mobile`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 158 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of employee_roster
-- ----------------------------
INSERT INTO `employee_roster` VALUES (1, 'EMP001', 'csc', '13511111111', NULL, NULL, '在职', 'ADMIN', '2025-02-21 02:34:22');
INSERT INTO `employee_roster` VALUES (5, 'EMP004', 'zmy', '13547721231', '', '', '在职', 'ADMIN', '2025-03-04 12:57:47');
INSERT INTO `employee_roster` VALUES (6, 'EMP000', 'i', '13557627356', NULL, NULL, '在职', 'ADMIN', '2025-03-11 15:27:35');
INSERT INTO `employee_roster` VALUES (157, '005', '肖柳航', '13558328255', '1', '1', '在职', 'ADMIN', '2025-07-10 16:27:04');

-- ----------------------------
-- Table structure for in_stock
-- ----------------------------
DROP TABLE IF EXISTS `in_stock`;
CREATE TABLE `in_stock`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '入库ID',
  `product_id` int NOT NULL COMMENT '产品ID',
  `warehouse_id` int NOT NULL COMMENT '仓库ID',
  `quantity` int NOT NULL COMMENT '入库数量（板）',
  `entry_date` date NOT NULL COMMENT '入库日期',
  `assay_id` int NOT NULL COMMENT '关联化验数据ID',
  `total_weight` decimal(10, 2) NOT NULL COMMENT '总重量',
  `screen_mesh_id` int NOT NULL COMMENT '筛网规格ID',
  `created_by` int NOT NULL COMMENT '操作员ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `semi_product_records` json NULL COMMENT '多批次半成品',
  `unit` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '出库单位：0板1件',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `product_id`(`product_id`) USING BTREE,
  INDEX `created_by`(`created_by`) USING BTREE,
  INDEX `idx_entry_date`(`entry_date`) USING BTREE,
  INDEX `in_stock_ibfk_2`(`warehouse_id`) USING BTREE,
  INDEX `screen_mesh_id`(`screen_mesh_id`) USING BTREE,
  INDEX `assay_id`(`assay_id`) USING BTREE,
  CONSTRAINT `in_stock_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `in_stock_ibfk_2` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `in_stock_ibfk_3` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `in_stock_ibfk_4` FOREIGN KEY (`screen_mesh_id`) REFERENCES `screen_mesh` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `in_stock_ibfk_5` FOREIGN KEY (`assay_id`) REFERENCES `assay` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 135 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of in_stock
-- ----------------------------
INSERT INTO `in_stock` VALUES (114, 66, 31, 5, '2025-07-04', 324, 125.00, 1, 2, '2025-07-09 17:35:26', '[{\"unit\": \"0\", \"quantity\": 5, \"useAssay\": false, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 1, \"semiProductId\": 47, \"productionDate\": \"2025-07-03\"}, {\"unit\": \"1\", \"quantity\": 5, \"useAssay\": true, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 1, \"semiProductId\": 47, \"productionDate\": \"2025-07-03\"}]', '0');
INSERT INTO `in_stock` VALUES (115, 66, 5, 10, '2025-07-04', 324, 250.00, 1, 2, '2025-07-09 20:06:44', '[{\"unit\": \"0\", \"quantity\": 10, \"useAssay\": false, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 10, \"semiProductId\": 47, \"productionDate\": \"2025-07-03\"}]', '0');
INSERT INTO `in_stock` VALUES (116, 66, 1, 3, '2025-07-04', 325, 75.00, 1, 2, '2025-07-09 20:10:51', '[{\"unit\": \"1\", \"quantity\": 10, \"useAssay\": false, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 1, \"semiProductId\": 47, \"productionDate\": \"2025-07-03\"}, {\"unit\": \"0\", \"quantity\": 2, \"useAssay\": true, \"productName\": \"黄碎冰白糖袋(40.00kg/袋)\", \"warehouseId\": 1, \"semiProductId\": 48, \"productionDate\": \"2025-07-03\"}]', '0');
INSERT INTO `in_stock` VALUES (117, 66, 9, 10, '2025-07-10', 328, 250.00, 1, 2, '2025-07-10 16:12:53', '[{\"unit\": \"0\", \"quantity\": 10, \"useAssay\": true, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 2, \"semiProductId\": 47, \"productionDate\": \"2025-07-10\"}]', '0');
INSERT INTO `in_stock` VALUES (118, 68, 10, 10, '2025-07-10', 330, 150.00, 1, 2, '2025-07-10 16:19:49', '[{\"unit\": \"1\", \"quantity\": 20, \"useAssay\": true, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 7, \"semiProductId\": 47, \"productionDate\": \"2025-07-10\"}]', '1');
INSERT INTO `in_stock` VALUES (119, 68, 11, 10, '2025-07-10', 331, 150.00, 1, 2, '2025-07-10 16:20:39', '[{\"unit\": \"1\", \"quantity\": 10, \"useAssay\": true, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 6, \"semiProductId\": 47, \"productionDate\": \"2025-07-10\"}]', '1');
INSERT INTO `in_stock` VALUES (120, 68, 65, 20, '2025-07-10', 334, 300.00, 1, 2, '2025-07-10 16:24:59', '[{\"unit\": \"1\", \"quantity\": 50, \"useAssay\": true, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 64, \"semiProductId\": 47, \"productionDate\": \"2025-07-10\"}]', '1');
INSERT INTO `in_stock` VALUES (121, 68, 15, 10, '2025-07-10', 335, 150.00, 1, 37, '2025-07-10 16:29:05', '[{\"unit\": \"0\", \"quantity\": 20, \"useAssay\": true, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 13, \"semiProductId\": 47, \"productionDate\": \"2025-07-10\"}]', '1');
INSERT INTO `in_stock` VALUES (122, 68, 66, 20, '2025-07-10', 336, 300.00, 1, 13, '2025-07-10 16:32:06', '[{\"unit\": \"1\", \"quantity\": 50, \"useAssay\": true, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 64, \"semiProductId\": 47, \"productionDate\": \"2025-07-10\"}]', '1');
INSERT INTO `in_stock` VALUES (123, 66, 62, 20, '2025-07-10', 338, 500.00, 1, 2, '2025-07-10 16:38:06', '[{\"unit\": \"0\", \"quantity\": 20, \"useAssay\": true, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 63, \"semiProductId\": 47, \"productionDate\": \"2025-07-10\"}]', '0');
INSERT INTO `in_stock` VALUES (124, 66, 9, 5, '2025-07-04', 325, 125.00, 1, 37, '2025-07-10 16:45:12', NULL, '0');
INSERT INTO `in_stock` VALUES (125, 66, 57, 10, '2025-07-10', 338, 250.00, 1, 2, '2025-07-10 18:17:28', '[{\"unit\": \"0\", \"quantity\": 5, \"useAssay\": false, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 1, \"semiProductId\": 47, \"productionDate\": \"2025-07-03\"}]', '0');
INSERT INTO `in_stock` VALUES (126, 66, 60, 10, '2025-07-10', 338, 250.00, 1, 2, '2025-07-11 16:13:57', '[{\"unit\": \"0\", \"quantity\": 10, \"useAssay\": false, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 1, \"semiProductId\": 47, \"productionDate\": \"2025-07-03\"}]', '0');
INSERT INTO `in_stock` VALUES (127, 66, 60, 10, '2025-07-10', 338, 250.00, 1, 2, '2025-07-11 16:14:43', '[{\"unit\": \"0\", \"quantity\": 12, \"useAssay\": false, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 1, \"semiProductId\": 47, \"productionDate\": \"2025-07-03\"}]', '1');
INSERT INTO `in_stock` VALUES (128, 68, 59, 20, '2025-07-10', 339, 300.00, 1, 2, '2025-07-11 18:26:29', '[{\"unit\": \"1\", \"quantity\": 25, \"useAssay\": true, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 12, \"semiProductId\": 47, \"productionDate\": \"2025-07-10\"}]', '1');
INSERT INTO `in_stock` VALUES (129, 66, 55, 5, '2025-07-10', 338, 125.00, 1, 2, '2025-07-11 19:52:31', '[{\"unit\": \"0\", \"quantity\": 2, \"useAssay\": false, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 12, \"semiProductId\": 47, \"productionDate\": \"2025-07-10\"}]', '0');
INSERT INTO `in_stock` VALUES (130, 66, 53, 10, '2025-07-10', 338, 250.00, 1, 2, '2025-07-15 21:30:57', '[{\"unit\": \"0\", \"quantity\": 8, \"useAssay\": false, \"productName\": \"黄碎冰白糖袋(40.00kg/袋)\", \"warehouseId\": 1, \"semiProductId\": 48, \"productionDate\": \"2025-07-03\"}]', '0');
INSERT INTO `in_stock` VALUES (131, 66, 53, 1, '2025-07-10', 338, 25.00, 1, 2, '2025-07-15 21:32:22', '[{\"unit\": \"1\", \"quantity\": 4, \"useAssay\": false, \"productName\": \"黄碎冰白糖袋(40.00kg/袋)\", \"warehouseId\": 2, \"semiProductId\": 48, \"productionDate\": \"2025-07-03\"}]', '1');
INSERT INTO `in_stock` VALUES (132, 66, 53, 1, '2025-07-10', 338, 25.00, 1, 2, '2025-07-15 21:33:05', '[{\"unit\": \"0\", \"quantity\": 1, \"useAssay\": false, \"productName\": \"黄碎冰白糖袋(40.00kg/袋)\", \"warehouseId\": 2, \"semiProductId\": 48, \"productionDate\": \"2025-07-03\"}]', '0');
INSERT INTO `in_stock` VALUES (133, 66, 53, 2, '2025-07-10', 338, 50.00, 2, 2, '2025-07-15 21:34:01', '[{\"unit\": \"1\", \"quantity\": 5, \"useAssay\": false, \"productName\": \"黄碎冰白糖袋(40.00kg/袋)\", \"warehouseId\": 2, \"semiProductId\": 48, \"productionDate\": \"2025-07-03\"}]', '0');
INSERT INTO `in_stock` VALUES (134, 69, 52, 20, '2025-07-16', 341, 800.00, 1, 37, '2025-07-16 16:19:39', '[{\"unit\": \"1\", \"quantity\": 150, \"useAssay\": true, \"productName\": \"黄中冰(40.00kg/袋)\", \"warehouseId\": 18, \"semiProductId\": 47, \"productionDate\": \"2025-07-16\"}]', '1');

-- ----------------------------
-- Table structure for in_stock_item
-- ----------------------------
DROP TABLE IF EXISTS `in_stock_item`;
CREATE TABLE `in_stock_item`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '半成品',
  `in_stock_id` int NULL DEFAULT NULL COMMENT '入库id',
  `product_name` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '产品名字',
  `warehouse_id` int NULL DEFAULT NULL COMMENT '库位',
  `semi_product_id` int NULL DEFAULT NULL COMMENT '产品id',
  `production_date` date NULL DEFAULT NULL COMMENT '生成日期',
  `use_assay` tinyint(1) NULL DEFAULT NULL COMMENT '是否套用化验数据',
  `quantity` int NULL DEFAULT NULL COMMENT '数量：数量',
  `unit` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '单位：0板，1件',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 32 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '入库成品的半成品明细' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of in_stock_item
-- ----------------------------
INSERT INTO `in_stock_item` VALUES (10, 114, '黄中冰(40.00kg/袋)', 1, 47, '2025-07-03', 0, 5, '0');
INSERT INTO `in_stock_item` VALUES (11, 114, '黄中冰(40.00kg/袋)', 1, 47, '2025-07-03', 1, 5, '1');
INSERT INTO `in_stock_item` VALUES (12, 115, '黄中冰(40.00kg/袋)', 10, 47, '2025-07-03', 0, 10, '0');
INSERT INTO `in_stock_item` VALUES (13, 116, '黄中冰(40.00kg/袋)', 1, 47, '2025-07-03', 0, 10, '1');
INSERT INTO `in_stock_item` VALUES (14, 116, '黄碎冰白糖袋(40.00kg/袋)', 1, 48, '2025-07-03', 1, 2, '0');
INSERT INTO `in_stock_item` VALUES (15, 117, '黄中冰(40.00kg/袋)', 2, 47, '2025-07-10', 1, 10, '0');
INSERT INTO `in_stock_item` VALUES (16, 118, '黄中冰(40.00kg/袋)', 7, 47, '2025-07-10', 1, 20, '1');
INSERT INTO `in_stock_item` VALUES (17, 119, '黄中冰(40.00kg/袋)', 6, 47, '2025-07-10', 1, 10, '1');
INSERT INTO `in_stock_item` VALUES (18, 120, '黄中冰(40.00kg/袋)', 64, 47, '2025-07-10', 1, 50, '1');
INSERT INTO `in_stock_item` VALUES (19, 121, '黄中冰(40.00kg/袋)', 13, 47, '2025-07-10', 1, 20, '0');
INSERT INTO `in_stock_item` VALUES (20, 122, '黄中冰(40.00kg/袋)', 64, 47, '2025-07-10', 1, 50, '1');
INSERT INTO `in_stock_item` VALUES (21, 123, '黄中冰(40.00kg/袋)', 63, 47, '2025-07-10', 1, 20, '0');
INSERT INTO `in_stock_item` VALUES (22, 125, '黄中冰(40.00kg/袋)', 1, 47, '2025-07-03', 0, 5, '0');
INSERT INTO `in_stock_item` VALUES (23, 126, '黄中冰(40.00kg/袋)', 1, 47, '2025-07-03', 0, 10, '0');
INSERT INTO `in_stock_item` VALUES (24, 127, '黄中冰(40.00kg/袋)', 1, 47, '2025-07-03', 0, 12, '0');
INSERT INTO `in_stock_item` VALUES (25, 128, '黄中冰(40.00kg/袋)', 12, 47, '2025-07-10', 1, 25, '1');
INSERT INTO `in_stock_item` VALUES (26, 129, '黄中冰(40.00kg/袋)', 12, 47, '2025-07-10', 0, 2, '0');
INSERT INTO `in_stock_item` VALUES (27, 130, '黄碎冰白糖袋(40.00kg/袋)', 1, 48, '2025-07-03', 0, 8, '0');
INSERT INTO `in_stock_item` VALUES (28, 131, '黄碎冰白糖袋(40.00kg/袋)', 2, 48, '2025-07-03', 0, 4, '1');
INSERT INTO `in_stock_item` VALUES (29, 132, '黄碎冰白糖袋(40.00kg/袋)', 2, 48, '2025-07-03', 0, 1, '0');
INSERT INTO `in_stock_item` VALUES (30, 133, '黄碎冰白糖袋(40.00kg/袋)', 2, 48, '2025-07-03', 0, 5, '1');
INSERT INTO `in_stock_item` VALUES (31, 134, '黄中冰(40.00kg/袋)', 18, 47, '2025-07-16', 1, 150, '1');

-- ----------------------------
-- Table structure for inventory
-- ----------------------------
DROP TABLE IF EXISTS `inventory`;
CREATE TABLE `inventory`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `warehouse_id` int NOT NULL COMMENT '库位ID',
  `product_id` int NOT NULL COMMENT '产品ID',
  `entry_date` date NOT NULL COMMENT '入库日期（生产日期）',
  `side` enum('左','右') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '存放列（左/右）',
  `row_number` int NOT NULL COMMENT '排号（从后往前）',
  `layer` int NOT NULL DEFAULT 1 COMMENT '层数（默认1，仅特殊产品适用）',
  `quantity` int NOT NULL COMMENT '板数（即多少板）',
  `assay_id` int NOT NULL COMMENT '关联的化验数据ID',
  `product_status` enum('半成品','成品') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '成品' COMMENT '产品状态（半成品或成品）',
  `in_stock_id` int NULL DEFAULT NULL COMMENT '关联的成品入库记录',
  `screen_mesh_id` int NULL DEFAULT NULL COMMENT '筛网规格ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `semi_record_id` json NULL,
  `pieces` int NULL DEFAULT 0 COMMENT '件数',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `warehouse_id`(`warehouse_id`) USING BTREE,
  INDEX `product_id`(`product_id`) USING BTREE,
  INDEX `entry_date`(`entry_date`) USING BTREE,
  INDEX `inventory_ibfk_3`(`assay_id`) USING BTREE,
  INDEX `idx_wh_product`(`warehouse_id`, `product_id`) USING BTREE,
  INDEX `inventory_ibfk_4`(`in_stock_id`) USING BTREE,
  INDEX `fk_screen_mesh_inventory`(`screen_mesh_id`) USING BTREE,
  CONSTRAINT `fk_screen_mesh_inventory` FOREIGN KEY (`screen_mesh_id`) REFERENCES `screen_mesh` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `inventory_ibfk_1` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `inventory_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `inventory_ibfk_3` FOREIGN KEY (`assay_id`) REFERENCES `assay` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `inventory_ibfk_4` FOREIGN KEY (`in_stock_id`) REFERENCES `in_stock` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 403 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of inventory
-- ----------------------------
INSERT INTO `inventory` VALUES (13, 31, 66, '2025-07-04', '左', 1, 1, 1, 324, '成品', 114, 1, '2025-07-09 17:35:27', NULL, 0);
INSERT INTO `inventory` VALUES (14, 31, 66, '2025-07-04', '左', 2, 1, 1, 324, '成品', 114, 1, '2025-07-09 17:35:27', NULL, 0);
INSERT INTO `inventory` VALUES (15, 31, 66, '2025-07-04', '左', 3, 1, 1, 324, '成品', 114, 1, '2025-07-09 17:35:27', NULL, 0);
INSERT INTO `inventory` VALUES (16, 31, 66, '2025-07-04', '左', 4, 1, 1, 324, '成品', 114, 1, '2025-07-09 17:35:27', NULL, 0);
INSERT INTO `inventory` VALUES (17, 31, 66, '2025-07-04', '左', 5, 1, 1, 324, '成品', 114, 1, '2025-07-09 17:35:27', NULL, 0);
INSERT INTO `inventory` VALUES (18, 2, 47, '2025-07-03', '右', 1, 1, 1, 319, '半成品', NULL, 1, '2025-07-09 18:07:14', NULL, 0);
INSERT INTO `inventory` VALUES (19, 2, 47, '2025-07-03', '右', 2, 1, 1, 319, '半成品', NULL, 1, '2025-07-09 18:07:15', NULL, 0);
INSERT INTO `inventory` VALUES (20, 2, 47, '2025-07-03', '右', 3, 1, 1, 319, '半成品', NULL, 1, '2025-07-09 18:07:15', NULL, 0);
INSERT INTO `inventory` VALUES (21, 2, 47, '2025-07-03', '右', 4, 1, 1, 319, '半成品', NULL, 1, '2025-07-09 18:07:15', NULL, 0);
INSERT INTO `inventory` VALUES (22, 2, 47, '2025-07-03', '右', 5, 1, 1, 319, '半成品', NULL, 1, '2025-07-09 18:07:15', NULL, 0);
INSERT INTO `inventory` VALUES (23, 2, 47, '2025-07-03', '右', 6, 1, 1, 319, '半成品', NULL, 1, '2025-07-09 18:07:15', NULL, 0);
INSERT INTO `inventory` VALUES (24, 2, 47, '2025-07-03', '右', 7, 1, 1, 319, '半成品', NULL, 1, '2025-07-09 18:07:15', NULL, 0);
INSERT INTO `inventory` VALUES (25, 2, 47, '2025-07-03', '右', 8, 1, 1, 319, '半成品', NULL, 1, '2025-07-09 18:07:15', NULL, 0);
INSERT INTO `inventory` VALUES (26, 2, 47, '2025-07-03', '右', 9, 1, 1, 319, '半成品', NULL, 1, '2025-07-09 18:07:15', NULL, 0);
INSERT INTO `inventory` VALUES (27, 2, 47, '2025-07-03', '右', 10, 1, 1, 319, '半成品', NULL, 1, '2025-07-09 18:07:15', NULL, 0);
INSERT INTO `inventory` VALUES (74, 2, 48, '2025-07-03', '左', 1, 1, 1, 320, '半成品', NULL, 1, '2025-07-09 19:49:22', NULL, 5);
INSERT INTO `inventory` VALUES (75, 3, 48, '2025-07-03', '右', 1, 1, 1, 320, '半成品', NULL, 1, '2025-07-09 19:50:03', NULL, 10);
INSERT INTO `inventory` VALUES (77, 5, 66, '2025-07-04', '左', 1, 1, 1, 324, '成品', 115, 1, '2025-07-09 20:06:44', NULL, 0);
INSERT INTO `inventory` VALUES (78, 5, 66, '2025-07-04', '左', 2, 1, 1, 324, '成品', 115, 1, '2025-07-09 20:06:45', NULL, 0);
INSERT INTO `inventory` VALUES (79, 5, 66, '2025-07-04', '左', 3, 1, 1, 324, '成品', 115, 1, '2025-07-09 20:06:45', NULL, 0);
INSERT INTO `inventory` VALUES (80, 5, 66, '2025-07-04', '左', 4, 1, 1, 324, '成品', 115, 1, '2025-07-09 20:06:45', NULL, 0);
INSERT INTO `inventory` VALUES (81, 5, 66, '2025-07-04', '左', 5, 1, 1, 324, '成品', 115, 1, '2025-07-09 20:06:45', NULL, 0);
INSERT INTO `inventory` VALUES (87, 1, 66, '2025-07-04', '左', 1, 2, 1, 325, '成品', 116, 1, '2025-07-09 20:10:52', NULL, 0);
INSERT INTO `inventory` VALUES (88, 1, 66, '2025-07-04', '左', 2, 2, 1, 325, '成品', 116, 1, '2025-07-09 20:10:52', NULL, 0);
INSERT INTO `inventory` VALUES (89, 1, 66, '2025-07-04', '左', 3, 2, 1, 325, '成品', 116, 1, '2025-07-09 20:10:52', NULL, 0);
INSERT INTO `inventory` VALUES (90, 1, 66, '2025-07-04', '左', 4, 2, 1, 325, '成品', 116, 1, '2025-07-09 20:10:52', NULL, 0);
INSERT INTO `inventory` VALUES (91, 1, 66, '2025-07-04', '左', 5, 2, 1, 325, '成品', 116, 1, '2025-07-09 20:10:52', NULL, 0);
INSERT INTO `inventory` VALUES (92, 1, 66, '2025-07-04', '左', 6, 2, 1, 325, '成品', 116, 1, '2025-07-09 20:10:52', NULL, 0);
INSERT INTO `inventory` VALUES (93, 1, 66, '2025-07-04', '左', 7, 2, 1, 325, '成品', 116, 1, '2025-07-09 20:10:52', NULL, 0);
INSERT INTO `inventory` VALUES (94, 1, 66, '2025-07-04', '左', 8, 2, 1, 325, '成品', 116, 1, '2025-07-09 20:10:52', NULL, 0);
INSERT INTO `inventory` VALUES (95, 1, 66, '2025-07-04', '左', 9, 2, 1, 325, '成品', 116, 1, '2025-07-09 20:10:52', NULL, 0);
INSERT INTO `inventory` VALUES (96, 1, 66, '2025-07-04', '左', 10, 2, 1, 325, '成品', 116, 1, '2025-07-09 20:10:52', NULL, 0);
INSERT INTO `inventory` VALUES (97, 4, 47, '2025-07-09', '右', 1, 1, 1, 326, '半成品', NULL, 1, '2025-07-09 20:16:10', NULL, 10);
INSERT INTO `inventory` VALUES (99, 4, 47, '2025-07-03', '右', 3, 1, 1, 319, '半成品', NULL, 1, '2025-07-09 20:16:46', NULL, 10);
INSERT INTO `inventory` VALUES (100, 4, 47, '2025-07-09', '左', 2, 1, 1, 326, '半成品', NULL, 1, '2025-07-09 20:17:32', NULL, 10);
INSERT INTO `inventory` VALUES (101, 8, 47, '2025-07-10', '左', 1, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:57', NULL, 0);
INSERT INTO `inventory` VALUES (102, 8, 47, '2025-07-10', '左', 2, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:57', NULL, 0);
INSERT INTO `inventory` VALUES (103, 8, 47, '2025-07-10', '左', 3, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:58', NULL, 0);
INSERT INTO `inventory` VALUES (104, 8, 47, '2025-07-10', '左', 4, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:58', NULL, 0);
INSERT INTO `inventory` VALUES (105, 8, 47, '2025-07-10', '左', 5, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:58', NULL, 0);
INSERT INTO `inventory` VALUES (106, 8, 47, '2025-07-10', '左', 6, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:58', NULL, 0);
INSERT INTO `inventory` VALUES (107, 8, 47, '2025-07-10', '左', 7, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:58', NULL, 0);
INSERT INTO `inventory` VALUES (108, 8, 47, '2025-07-10', '左', 8, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:58', NULL, 0);
INSERT INTO `inventory` VALUES (109, 8, 47, '2025-07-10', '左', 9, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:58', NULL, 0);
INSERT INTO `inventory` VALUES (110, 8, 47, '2025-07-10', '左', 10, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:58', NULL, 0);
INSERT INTO `inventory` VALUES (111, 8, 47, '2025-07-10', '右', 1, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:58', NULL, 0);
INSERT INTO `inventory` VALUES (112, 8, 47, '2025-07-10', '右', 2, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:59', NULL, 0);
INSERT INTO `inventory` VALUES (113, 8, 47, '2025-07-10', '右', 3, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:59', NULL, 0);
INSERT INTO `inventory` VALUES (114, 8, 47, '2025-07-10', '右', 4, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:59', NULL, 0);
INSERT INTO `inventory` VALUES (115, 8, 47, '2025-07-10', '右', 5, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:59', NULL, 0);
INSERT INTO `inventory` VALUES (116, 8, 47, '2025-07-10', '右', 6, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:59', NULL, 0);
INSERT INTO `inventory` VALUES (117, 8, 47, '2025-07-10', '右', 7, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:04:59', NULL, 0);
INSERT INTO `inventory` VALUES (118, 8, 47, '2025-07-10', '右', 8, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:05:00', NULL, 0);
INSERT INTO `inventory` VALUES (119, 8, 47, '2025-07-10', '右', 9, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:05:00', NULL, 0);
INSERT INTO `inventory` VALUES (120, 8, 47, '2025-07-10', '右', 10, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:05:00', NULL, 0);
INSERT INTO `inventory` VALUES (121, 9, 66, '2025-07-10', '左', 1, 1, 1, 328, '成品', 117, 1, '2025-07-10 16:12:53', NULL, 0);
INSERT INTO `inventory` VALUES (122, 9, 66, '2025-07-10', '左', 2, 1, 1, 328, '成品', 117, 1, '2025-07-10 16:12:53', NULL, 0);
INSERT INTO `inventory` VALUES (123, 9, 66, '2025-07-10', '左', 3, 1, 1, 328, '成品', 117, 1, '2025-07-10 16:12:54', NULL, 0);
INSERT INTO `inventory` VALUES (124, 9, 66, '2025-07-10', '左', 4, 1, 1, 328, '成品', 117, 1, '2025-07-10 16:12:54', NULL, 0);
INSERT INTO `inventory` VALUES (125, 9, 66, '2025-07-10', '左', 5, 1, 1, 328, '成品', 117, 1, '2025-07-10 16:12:54', NULL, 0);
INSERT INTO `inventory` VALUES (126, 9, 66, '2025-07-10', '左', 6, 1, 1, 328, '成品', 117, 1, '2025-07-10 16:12:54', NULL, 0);
INSERT INTO `inventory` VALUES (127, 9, 66, '2025-07-10', '左', 7, 1, 1, 328, '成品', 117, 1, '2025-07-10 16:12:54', NULL, 0);
INSERT INTO `inventory` VALUES (128, 9, 66, '2025-07-10', '左', 8, 1, 1, 328, '成品', 117, 1, '2025-07-10 16:12:54', NULL, 0);
INSERT INTO `inventory` VALUES (129, 9, 66, '2025-07-10', '左', 9, 1, 1, 328, '成品', 117, 1, '2025-07-10 16:12:54', NULL, 0);
INSERT INTO `inventory` VALUES (130, 9, 66, '2025-07-10', '左', 10, 1, 1, 328, '成品', 117, 1, '2025-07-10 16:12:54', NULL, 0);
INSERT INTO `inventory` VALUES (131, 7, 47, '2025-07-10', '左', 1, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:18:18', NULL, 20);
INSERT INTO `inventory` VALUES (132, 6, 47, '2025-07-10', '左', 1, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:19:13', NULL, 10);
INSERT INTO `inventory` VALUES (133, 10, 68, '2025-07-10', '左', 1, 1, 1, 330, '成品', 118, 1, '2025-07-10 16:19:49', NULL, 1);
INSERT INTO `inventory` VALUES (134, 10, 68, '2025-07-10', '左', 2, 1, 1, 330, '成品', 118, 1, '2025-07-10 16:19:49', NULL, 1);
INSERT INTO `inventory` VALUES (135, 10, 68, '2025-07-10', '左', 3, 1, 1, 330, '成品', 118, 1, '2025-07-10 16:19:49', NULL, 1);
INSERT INTO `inventory` VALUES (136, 10, 68, '2025-07-10', '左', 4, 1, 1, 330, '成品', 118, 1, '2025-07-10 16:19:49', NULL, 1);
INSERT INTO `inventory` VALUES (137, 10, 68, '2025-07-10', '左', 5, 1, 1, 330, '成品', 118, 1, '2025-07-10 16:19:50', NULL, 1);
INSERT INTO `inventory` VALUES (138, 10, 68, '2025-07-10', '左', 6, 1, 1, 330, '成品', 118, 1, '2025-07-10 16:19:50', NULL, 1);
INSERT INTO `inventory` VALUES (139, 10, 68, '2025-07-10', '左', 7, 1, 1, 330, '成品', 118, 1, '2025-07-10 16:19:50', NULL, 1);
INSERT INTO `inventory` VALUES (140, 10, 68, '2025-07-10', '左', 8, 1, 1, 330, '成品', 118, 1, '2025-07-10 16:19:50', NULL, 1);
INSERT INTO `inventory` VALUES (141, 10, 68, '2025-07-10', '左', 9, 1, 1, 330, '成品', 118, 1, '2025-07-10 16:19:50', NULL, 1);
INSERT INTO `inventory` VALUES (142, 10, 68, '2025-07-10', '左', 10, 1, 1, 330, '成品', 118, 1, '2025-07-10 16:19:50', NULL, 1);
INSERT INTO `inventory` VALUES (143, 11, 68, '2025-07-10', '左', 1, 1, 1, 331, '成品', 119, 1, '2025-07-10 16:20:40', NULL, 1);
INSERT INTO `inventory` VALUES (144, 11, 68, '2025-07-10', '左', 2, 1, 1, 331, '成品', 119, 1, '2025-07-10 16:20:40', NULL, 1);
INSERT INTO `inventory` VALUES (145, 11, 68, '2025-07-10', '左', 3, 1, 1, 331, '成品', 119, 1, '2025-07-10 16:20:40', NULL, 1);
INSERT INTO `inventory` VALUES (146, 11, 68, '2025-07-10', '左', 4, 1, 1, 331, '成品', 119, 1, '2025-07-10 16:20:40', NULL, 1);
INSERT INTO `inventory` VALUES (147, 11, 68, '2025-07-10', '左', 5, 1, 1, 331, '成品', 119, 1, '2025-07-10 16:20:40', NULL, 1);
INSERT INTO `inventory` VALUES (148, 11, 68, '2025-07-10', '左', 6, 1, 1, 331, '成品', 119, 1, '2025-07-10 16:20:40', NULL, 1);
INSERT INTO `inventory` VALUES (149, 11, 68, '2025-07-10', '左', 7, 1, 1, 331, '成品', 119, 1, '2025-07-10 16:20:40', NULL, 1);
INSERT INTO `inventory` VALUES (150, 11, 68, '2025-07-10', '左', 8, 1, 1, 331, '成品', 119, 1, '2025-07-10 16:20:40', NULL, 1);
INSERT INTO `inventory` VALUES (151, 11, 68, '2025-07-10', '左', 9, 1, 1, 331, '成品', 119, 1, '2025-07-10 16:20:40', NULL, 1);
INSERT INTO `inventory` VALUES (152, 11, 68, '2025-07-10', '左', 10, 1, 1, 331, '成品', 119, 1, '2025-07-10 16:20:40', NULL, 1);
INSERT INTO `inventory` VALUES (156, 65, 68, '2025-07-10', '左', 1, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:00', NULL, 1);
INSERT INTO `inventory` VALUES (157, 65, 68, '2025-07-10', '左', 2, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:00', NULL, 1);
INSERT INTO `inventory` VALUES (158, 65, 68, '2025-07-10', '左', 3, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:00', NULL, 1);
INSERT INTO `inventory` VALUES (159, 65, 68, '2025-07-10', '左', 4, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:00', NULL, 1);
INSERT INTO `inventory` VALUES (160, 65, 68, '2025-07-10', '左', 5, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:00', NULL, 1);
INSERT INTO `inventory` VALUES (161, 65, 68, '2025-07-10', '左', 6, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:00', NULL, 1);
INSERT INTO `inventory` VALUES (162, 65, 68, '2025-07-10', '左', 7, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:00', NULL, 1);
INSERT INTO `inventory` VALUES (163, 65, 68, '2025-07-10', '左', 8, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:00', NULL, 1);
INSERT INTO `inventory` VALUES (164, 65, 68, '2025-07-10', '左', 9, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:00', NULL, 1);
INSERT INTO `inventory` VALUES (165, 65, 68, '2025-07-10', '左', 10, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:00', NULL, 1);
INSERT INTO `inventory` VALUES (166, 65, 68, '2025-07-10', '右', 1, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:01', NULL, 1);
INSERT INTO `inventory` VALUES (167, 65, 68, '2025-07-10', '右', 2, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:01', NULL, 1);
INSERT INTO `inventory` VALUES (168, 65, 68, '2025-07-10', '右', 3, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:01', NULL, 1);
INSERT INTO `inventory` VALUES (169, 65, 68, '2025-07-10', '右', 4, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:01', NULL, 1);
INSERT INTO `inventory` VALUES (170, 65, 68, '2025-07-10', '右', 5, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:01', NULL, 1);
INSERT INTO `inventory` VALUES (171, 65, 68, '2025-07-10', '右', 6, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:01', NULL, 1);
INSERT INTO `inventory` VALUES (172, 65, 68, '2025-07-10', '右', 7, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:01', NULL, 1);
INSERT INTO `inventory` VALUES (173, 65, 68, '2025-07-10', '右', 8, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:01', NULL, 1);
INSERT INTO `inventory` VALUES (174, 65, 68, '2025-07-10', '右', 9, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:01', NULL, 1);
INSERT INTO `inventory` VALUES (175, 65, 68, '2025-07-10', '右', 10, 1, 1, 334, '成品', 120, 1, '2025-07-10 16:25:01', NULL, 1);
INSERT INTO `inventory` VALUES (196, 15, 68, '2025-07-10', '左', 1, 1, 1, 335, '成品', 121, 1, '2025-07-10 16:29:05', NULL, 1);
INSERT INTO `inventory` VALUES (197, 15, 68, '2025-07-10', '左', 2, 1, 1, 335, '成品', 121, 1, '2025-07-10 16:29:05', NULL, 1);
INSERT INTO `inventory` VALUES (198, 15, 68, '2025-07-10', '左', 3, 1, 1, 335, '成品', 121, 1, '2025-07-10 16:29:06', NULL, 1);
INSERT INTO `inventory` VALUES (199, 15, 68, '2025-07-10', '左', 4, 1, 1, 335, '成品', 121, 1, '2025-07-10 16:29:06', NULL, 1);
INSERT INTO `inventory` VALUES (200, 15, 68, '2025-07-10', '左', 5, 1, 1, 335, '成品', 121, 1, '2025-07-10 16:29:06', NULL, 1);
INSERT INTO `inventory` VALUES (201, 15, 68, '2025-07-10', '左', 6, 1, 1, 335, '成品', 121, 1, '2025-07-10 16:29:06', NULL, 1);
INSERT INTO `inventory` VALUES (202, 15, 68, '2025-07-10', '左', 7, 1, 1, 335, '成品', 121, 1, '2025-07-10 16:29:06', NULL, 1);
INSERT INTO `inventory` VALUES (203, 15, 68, '2025-07-10', '左', 8, 1, 1, 335, '成品', 121, 1, '2025-07-10 16:29:06', NULL, 1);
INSERT INTO `inventory` VALUES (204, 15, 68, '2025-07-10', '左', 9, 1, 1, 335, '成品', 121, 1, '2025-07-10 16:29:06', NULL, 1);
INSERT INTO `inventory` VALUES (205, 15, 68, '2025-07-10', '左', 10, 1, 1, 335, '成品', 121, 1, '2025-07-10 16:29:06', NULL, 1);
INSERT INTO `inventory` VALUES (206, 66, 68, '2025-07-10', '左', 1, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:06', NULL, 1);
INSERT INTO `inventory` VALUES (207, 66, 68, '2025-07-10', '左', 2, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:06', NULL, 1);
INSERT INTO `inventory` VALUES (208, 66, 68, '2025-07-10', '左', 3, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:07', NULL, 1);
INSERT INTO `inventory` VALUES (209, 66, 68, '2025-07-10', '左', 4, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:07', NULL, 1);
INSERT INTO `inventory` VALUES (210, 66, 68, '2025-07-10', '左', 5, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:07', NULL, 1);
INSERT INTO `inventory` VALUES (211, 66, 68, '2025-07-10', '左', 6, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:07', NULL, 1);
INSERT INTO `inventory` VALUES (212, 66, 68, '2025-07-10', '左', 7, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:07', NULL, 1);
INSERT INTO `inventory` VALUES (213, 66, 68, '2025-07-10', '左', 8, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:07', NULL, 1);
INSERT INTO `inventory` VALUES (214, 66, 68, '2025-07-10', '左', 9, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:07', NULL, 1);
INSERT INTO `inventory` VALUES (215, 66, 68, '2025-07-10', '左', 10, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:07', NULL, 1);
INSERT INTO `inventory` VALUES (216, 66, 68, '2025-07-10', '右', 1, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:07', NULL, 1);
INSERT INTO `inventory` VALUES (217, 66, 68, '2025-07-10', '右', 2, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:07', NULL, 1);
INSERT INTO `inventory` VALUES (218, 66, 68, '2025-07-10', '右', 3, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:07', NULL, 1);
INSERT INTO `inventory` VALUES (219, 66, 68, '2025-07-10', '右', 4, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:08', NULL, 1);
INSERT INTO `inventory` VALUES (220, 66, 68, '2025-07-10', '右', 5, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:08', NULL, 1);
INSERT INTO `inventory` VALUES (221, 66, 68, '2025-07-10', '右', 6, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:08', NULL, 1);
INSERT INTO `inventory` VALUES (222, 66, 68, '2025-07-10', '右', 7, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:08', NULL, 1);
INSERT INTO `inventory` VALUES (223, 66, 68, '2025-07-10', '右', 8, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:08', NULL, 1);
INSERT INTO `inventory` VALUES (224, 66, 68, '2025-07-10', '右', 9, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:08', NULL, 1);
INSERT INTO `inventory` VALUES (225, 66, 68, '2025-07-10', '右', 10, 1, 1, 336, '成品', 122, 1, '2025-07-10 16:32:08', NULL, 1);
INSERT INTO `inventory` VALUES (226, 63, 47, '2025-07-10', '左', 1, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:06', NULL, 0);
INSERT INTO `inventory` VALUES (227, 63, 47, '2025-07-10', '左', 2, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:06', NULL, 0);
INSERT INTO `inventory` VALUES (228, 63, 47, '2025-07-10', '左', 3, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:06', NULL, 0);
INSERT INTO `inventory` VALUES (229, 63, 47, '2025-07-10', '左', 4, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:06', NULL, 0);
INSERT INTO `inventory` VALUES (230, 63, 47, '2025-07-10', '左', 5, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:06', NULL, 0);
INSERT INTO `inventory` VALUES (231, 63, 47, '2025-07-10', '左', 6, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:06', NULL, 0);
INSERT INTO `inventory` VALUES (232, 63, 47, '2025-07-10', '左', 7, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:06', NULL, 0);
INSERT INTO `inventory` VALUES (233, 63, 47, '2025-07-10', '左', 8, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:06', NULL, 0);
INSERT INTO `inventory` VALUES (234, 63, 47, '2025-07-10', '左', 9, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:06', NULL, 0);
INSERT INTO `inventory` VALUES (235, 63, 47, '2025-07-10', '左', 10, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:06', NULL, 0);
INSERT INTO `inventory` VALUES (236, 63, 47, '2025-07-10', '右', 1, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:06', NULL, 0);
INSERT INTO `inventory` VALUES (237, 63, 47, '2025-07-10', '右', 2, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:06', NULL, 0);
INSERT INTO `inventory` VALUES (238, 63, 47, '2025-07-10', '右', 3, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:06', NULL, 0);
INSERT INTO `inventory` VALUES (239, 63, 47, '2025-07-10', '右', 4, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:07', NULL, 0);
INSERT INTO `inventory` VALUES (240, 63, 47, '2025-07-10', '右', 5, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:07', NULL, 0);
INSERT INTO `inventory` VALUES (241, 63, 47, '2025-07-10', '右', 6, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:07', NULL, 0);
INSERT INTO `inventory` VALUES (242, 63, 47, '2025-07-10', '右', 7, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:07', NULL, 0);
INSERT INTO `inventory` VALUES (243, 63, 47, '2025-07-10', '右', 8, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:07', NULL, 0);
INSERT INTO `inventory` VALUES (244, 63, 47, '2025-07-10', '右', 9, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:07', NULL, 0);
INSERT INTO `inventory` VALUES (245, 63, 47, '2025-07-10', '右', 10, 1, 1, 327, '半成品', NULL, 1, '2025-07-10 16:37:07', NULL, 0);
INSERT INTO `inventory` VALUES (246, 62, 66, '2025-07-10', '左', 1, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:06', NULL, 0);
INSERT INTO `inventory` VALUES (247, 62, 66, '2025-07-10', '左', 2, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:06', NULL, 0);
INSERT INTO `inventory` VALUES (248, 62, 66, '2025-07-10', '左', 3, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:06', NULL, 0);
INSERT INTO `inventory` VALUES (249, 62, 66, '2025-07-10', '左', 4, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:06', NULL, 0);
INSERT INTO `inventory` VALUES (250, 62, 66, '2025-07-10', '左', 5, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:06', NULL, 0);
INSERT INTO `inventory` VALUES (251, 62, 66, '2025-07-10', '左', 6, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:06', NULL, 0);
INSERT INTO `inventory` VALUES (252, 62, 66, '2025-07-10', '左', 7, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:06', NULL, 0);
INSERT INTO `inventory` VALUES (253, 62, 66, '2025-07-10', '左', 8, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:07', NULL, 0);
INSERT INTO `inventory` VALUES (254, 62, 66, '2025-07-10', '左', 9, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:07', NULL, 0);
INSERT INTO `inventory` VALUES (255, 62, 66, '2025-07-10', '左', 10, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:07', NULL, 0);
INSERT INTO `inventory` VALUES (256, 62, 66, '2025-07-10', '右', 1, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:07', NULL, 0);
INSERT INTO `inventory` VALUES (257, 62, 66, '2025-07-10', '右', 2, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:07', NULL, 0);
INSERT INTO `inventory` VALUES (258, 62, 66, '2025-07-10', '右', 3, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:07', NULL, 0);
INSERT INTO `inventory` VALUES (259, 62, 66, '2025-07-10', '右', 4, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:07', NULL, 0);
INSERT INTO `inventory` VALUES (260, 62, 66, '2025-07-10', '右', 5, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:07', NULL, 0);
INSERT INTO `inventory` VALUES (261, 62, 66, '2025-07-10', '右', 6, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:07', NULL, 0);
INSERT INTO `inventory` VALUES (262, 62, 66, '2025-07-10', '右', 7, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:07', NULL, 0);
INSERT INTO `inventory` VALUES (263, 62, 66, '2025-07-10', '右', 8, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:07', NULL, 0);
INSERT INTO `inventory` VALUES (264, 62, 66, '2025-07-10', '右', 9, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:07', NULL, 0);
INSERT INTO `inventory` VALUES (265, 62, 66, '2025-07-10', '右', 10, 1, 1, 338, '成品', 123, 1, '2025-07-10 16:38:07', NULL, 0);
INSERT INTO `inventory` VALUES (266, 9, 66, '2025-07-04', '右', 1, 1, 1, 325, '成品', 124, 1, '2025-07-10 16:45:13', NULL, 0);
INSERT INTO `inventory` VALUES (267, 9, 66, '2025-07-04', '右', 2, 1, 1, 325, '成品', 124, 1, '2025-07-10 16:45:13', NULL, 0);
INSERT INTO `inventory` VALUES (268, 9, 66, '2025-07-04', '右', 3, 1, 1, 325, '成品', 124, 1, '2025-07-10 16:45:13', NULL, 0);
INSERT INTO `inventory` VALUES (269, 9, 66, '2025-07-04', '右', 4, 1, 1, 325, '成品', 124, 1, '2025-07-10 16:45:13', NULL, 0);
INSERT INTO `inventory` VALUES (270, 9, 66, '2025-07-04', '右', 5, 1, 1, 325, '成品', 124, 1, '2025-07-10 16:45:13', NULL, 0);
INSERT INTO `inventory` VALUES (271, 57, 66, '2025-07-10', '左', 1, 1, 1, 338, '成品', 125, 1, '2025-07-10 18:17:28', NULL, 0);
INSERT INTO `inventory` VALUES (272, 57, 66, '2025-07-10', '左', 2, 1, 1, 338, '成品', 125, 1, '2025-07-10 18:17:28', NULL, 0);
INSERT INTO `inventory` VALUES (273, 57, 66, '2025-07-10', '左', 3, 1, 1, 338, '成品', 125, 1, '2025-07-10 18:17:28', NULL, 0);
INSERT INTO `inventory` VALUES (274, 57, 66, '2025-07-10', '左', 4, 1, 1, 338, '成品', 125, 1, '2025-07-10 18:17:28', NULL, 0);
INSERT INTO `inventory` VALUES (275, 57, 66, '2025-07-10', '左', 5, 1, 1, 338, '成品', 125, 1, '2025-07-10 18:17:28', NULL, 0);
INSERT INTO `inventory` VALUES (276, 57, 66, '2025-07-10', '左', 6, 1, 1, 338, '成品', 125, 1, '2025-07-10 18:17:28', NULL, 0);
INSERT INTO `inventory` VALUES (277, 57, 66, '2025-07-10', '左', 7, 1, 1, 338, '成品', 125, 1, '2025-07-10 18:17:28', NULL, 0);
INSERT INTO `inventory` VALUES (278, 57, 66, '2025-07-10', '左', 8, 1, 1, 338, '成品', 125, 1, '2025-07-10 18:17:29', NULL, 0);
INSERT INTO `inventory` VALUES (279, 57, 66, '2025-07-10', '左', 9, 1, 1, 338, '成品', 125, 1, '2025-07-10 18:17:29', NULL, 0);
INSERT INTO `inventory` VALUES (280, 57, 66, '2025-07-10', '左', 10, 1, 1, 338, '成品', 125, 1, '2025-07-10 18:17:29', NULL, 0);
INSERT INTO `inventory` VALUES (281, 60, 66, '2025-07-10', '左', 1, 1, 1, 338, '成品', 126, 1, '2025-07-11 16:13:58', NULL, 0);
INSERT INTO `inventory` VALUES (282, 60, 66, '2025-07-10', '左', 2, 1, 1, 338, '成品', 126, 1, '2025-07-11 16:13:58', NULL, 0);
INSERT INTO `inventory` VALUES (283, 60, 66, '2025-07-10', '左', 3, 1, 1, 338, '成品', 126, 1, '2025-07-11 16:13:58', NULL, 0);
INSERT INTO `inventory` VALUES (284, 60, 66, '2025-07-10', '左', 4, 1, 1, 338, '成品', 126, 1, '2025-07-11 16:13:58', NULL, 0);
INSERT INTO `inventory` VALUES (285, 60, 66, '2025-07-10', '左', 5, 1, 1, 338, '成品', 126, 1, '2025-07-11 16:13:58', NULL, 0);
INSERT INTO `inventory` VALUES (286, 60, 66, '2025-07-10', '左', 6, 1, 1, 338, '成品', 126, 1, '2025-07-11 16:13:58', NULL, 0);
INSERT INTO `inventory` VALUES (287, 60, 66, '2025-07-10', '左', 7, 1, 1, 338, '成品', 126, 1, '2025-07-11 16:13:58', NULL, 0);
INSERT INTO `inventory` VALUES (288, 60, 66, '2025-07-10', '左', 8, 1, 1, 338, '成品', 126, 1, '2025-07-11 16:13:58', NULL, 0);
INSERT INTO `inventory` VALUES (289, 60, 66, '2025-07-10', '左', 9, 1, 1, 338, '成品', 126, 1, '2025-07-11 16:13:58', NULL, 0);
INSERT INTO `inventory` VALUES (290, 60, 66, '2025-07-10', '左', 10, 1, 1, 338, '成品', 126, 1, '2025-07-11 16:13:58', NULL, 0);
INSERT INTO `inventory` VALUES (291, 60, 66, '2025-07-10', '右', 1, 1, 1, 338, '成品', 127, 1, '2025-07-11 16:14:43', NULL, 1);
INSERT INTO `inventory` VALUES (292, 60, 66, '2025-07-10', '右', 2, 1, 1, 338, '成品', 127, 1, '2025-07-11 16:14:43', NULL, 1);
INSERT INTO `inventory` VALUES (293, 60, 66, '2025-07-10', '右', 3, 1, 1, 338, '成品', 127, 1, '2025-07-11 16:14:43', NULL, 1);
INSERT INTO `inventory` VALUES (294, 60, 66, '2025-07-10', '右', 4, 1, 1, 338, '成品', 127, 1, '2025-07-11 16:14:43', NULL, 1);
INSERT INTO `inventory` VALUES (295, 60, 66, '2025-07-10', '右', 5, 1, 1, 338, '成品', 127, 1, '2025-07-11 16:14:43', NULL, 1);
INSERT INTO `inventory` VALUES (296, 60, 66, '2025-07-10', '右', 6, 1, 1, 338, '成品', 127, 1, '2025-07-11 16:14:44', NULL, 1);
INSERT INTO `inventory` VALUES (297, 60, 66, '2025-07-10', '右', 7, 1, 1, 338, '成品', 127, 1, '2025-07-11 16:14:44', NULL, 1);
INSERT INTO `inventory` VALUES (298, 60, 66, '2025-07-10', '右', 8, 1, 1, 338, '成品', 127, 1, '2025-07-11 16:14:44', NULL, 1);
INSERT INTO `inventory` VALUES (299, 60, 66, '2025-07-10', '右', 9, 1, 1, 338, '成品', 127, 1, '2025-07-11 16:14:44', NULL, 1);
INSERT INTO `inventory` VALUES (300, 60, 66, '2025-07-10', '右', 10, 1, 1, 338, '成品', 127, 1, '2025-07-11 16:14:44', NULL, 1);
INSERT INTO `inventory` VALUES (301, 41, 47, '2025-07-10', '左', 1, 1, 1, 327, '半成品', NULL, 1, '2025-07-11 17:01:47', NULL, 20);
INSERT INTO `inventory` VALUES (302, 41, 47, '2025-07-10', '左', 2, 1, 1, 327, '半成品', NULL, 1, '2025-07-11 17:01:47', NULL, 20);
INSERT INTO `inventory` VALUES (303, 41, 47, '2025-07-10', '左', 3, 1, 1, 327, '半成品', NULL, 1, '2025-07-11 17:01:48', NULL, 10);
INSERT INTO `inventory` VALUES (306, 12, 47, '2025-07-10', '左', 3, 1, 1, 327, '半成品', NULL, 1, '2025-07-11 18:24:42', NULL, 0);
INSERT INTO `inventory` VALUES (307, 12, 47, '2025-07-10', '左', 4, 1, 1, 327, '半成品', NULL, 1, '2025-07-11 18:24:42', NULL, 0);
INSERT INTO `inventory` VALUES (308, 12, 47, '2025-07-10', '左', 5, 1, 1, 327, '半成品', NULL, 1, '2025-07-11 18:24:42', NULL, 0);
INSERT INTO `inventory` VALUES (309, 59, 68, '2025-07-10', '左', 1, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:29', NULL, 1);
INSERT INTO `inventory` VALUES (310, 59, 68, '2025-07-10', '左', 2, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:30', NULL, 1);
INSERT INTO `inventory` VALUES (311, 59, 68, '2025-07-10', '左', 3, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:30', NULL, 1);
INSERT INTO `inventory` VALUES (312, 59, 68, '2025-07-10', '左', 4, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:30', NULL, 1);
INSERT INTO `inventory` VALUES (313, 59, 68, '2025-07-10', '左', 5, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:30', NULL, 1);
INSERT INTO `inventory` VALUES (314, 59, 68, '2025-07-10', '左', 6, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:30', NULL, 1);
INSERT INTO `inventory` VALUES (315, 59, 68, '2025-07-10', '左', 7, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:30', NULL, 1);
INSERT INTO `inventory` VALUES (316, 59, 68, '2025-07-10', '左', 8, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:30', NULL, 1);
INSERT INTO `inventory` VALUES (317, 59, 68, '2025-07-10', '左', 9, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:30', NULL, 1);
INSERT INTO `inventory` VALUES (318, 59, 68, '2025-07-10', '左', 10, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:30', NULL, 1);
INSERT INTO `inventory` VALUES (319, 59, 68, '2025-07-10', '右', 1, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:30', NULL, 1);
INSERT INTO `inventory` VALUES (320, 59, 68, '2025-07-10', '右', 2, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:30', NULL, 1);
INSERT INTO `inventory` VALUES (321, 59, 68, '2025-07-10', '右', 3, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:30', NULL, 1);
INSERT INTO `inventory` VALUES (322, 59, 68, '2025-07-10', '右', 4, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:30', NULL, 1);
INSERT INTO `inventory` VALUES (323, 59, 68, '2025-07-10', '右', 5, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:30', NULL, 1);
INSERT INTO `inventory` VALUES (324, 59, 68, '2025-07-10', '右', 6, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:31', NULL, 1);
INSERT INTO `inventory` VALUES (325, 59, 68, '2025-07-10', '右', 7, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:31', NULL, 1);
INSERT INTO `inventory` VALUES (326, 59, 68, '2025-07-10', '右', 8, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:31', NULL, 1);
INSERT INTO `inventory` VALUES (327, 59, 68, '2025-07-10', '右', 9, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:31', NULL, 1);
INSERT INTO `inventory` VALUES (328, 59, 68, '2025-07-10', '右', 10, 1, 1, 339, '成品', 128, 1, '2025-07-11 18:26:31', NULL, 1);
INSERT INTO `inventory` VALUES (329, 55, 66, '2025-07-10', '右', 1, 1, 1, 338, '成品', 129, 1, '2025-07-11 19:52:32', NULL, 0);
INSERT INTO `inventory` VALUES (330, 55, 66, '2025-07-10', '右', 2, 1, 1, 338, '成品', 129, 1, '2025-07-11 19:52:32', NULL, 0);
INSERT INTO `inventory` VALUES (331, 55, 66, '2025-07-10', '右', 3, 1, 1, 338, '成品', 129, 1, '2025-07-11 19:52:32', NULL, 0);
INSERT INTO `inventory` VALUES (332, 55, 66, '2025-07-10', '右', 4, 1, 1, 338, '成品', 129, 1, '2025-07-11 19:52:32', NULL, 0);
INSERT INTO `inventory` VALUES (333, 55, 66, '2025-07-10', '右', 5, 1, 1, 338, '成品', 129, 1, '2025-07-11 19:52:32', NULL, 0);
INSERT INTO `inventory` VALUES (334, 58, 47, '2025-07-03', '左', 1, 1, 1, 319, '半成品', NULL, 1, '2025-07-15 20:33:09', NULL, 0);
INSERT INTO `inventory` VALUES (335, 53, 66, '2025-07-10', '左', 1, 1, 1, 338, '成品', 130, 1, '2025-07-15 21:30:57', NULL, 0);
INSERT INTO `inventory` VALUES (336, 53, 66, '2025-07-10', '左', 2, 1, 1, 338, '成品', 130, 1, '2025-07-15 21:30:57', NULL, 0);
INSERT INTO `inventory` VALUES (337, 53, 66, '2025-07-10', '左', 3, 1, 1, 338, '成品', 130, 1, '2025-07-15 21:30:57', NULL, 0);
INSERT INTO `inventory` VALUES (338, 53, 66, '2025-07-10', '左', 4, 1, 1, 338, '成品', 130, 1, '2025-07-15 21:30:58', NULL, 0);
INSERT INTO `inventory` VALUES (339, 53, 66, '2025-07-10', '左', 5, 1, 1, 338, '成品', 130, 1, '2025-07-15 21:30:58', NULL, 0);
INSERT INTO `inventory` VALUES (340, 53, 66, '2025-07-10', '左', 6, 1, 1, 338, '成品', 130, 1, '2025-07-15 21:30:58', NULL, 0);
INSERT INTO `inventory` VALUES (341, 53, 66, '2025-07-10', '左', 7, 1, 1, 338, '成品', 130, 1, '2025-07-15 21:30:58', NULL, 0);
INSERT INTO `inventory` VALUES (342, 53, 66, '2025-07-10', '左', 8, 1, 1, 338, '成品', 130, 1, '2025-07-15 21:30:58', NULL, 0);
INSERT INTO `inventory` VALUES (343, 53, 66, '2025-07-10', '左', 9, 1, 1, 338, '成品', 130, 1, '2025-07-15 21:30:58', NULL, 0);
INSERT INTO `inventory` VALUES (344, 53, 66, '2025-07-10', '左', 10, 1, 1, 338, '成品', 130, 1, '2025-07-15 21:30:58', NULL, 0);
INSERT INTO `inventory` VALUES (345, 53, 66, '2025-07-10', '右', 1, 1, 1, 338, '成品', 131, 1, '2025-07-15 21:32:22', NULL, 1);
INSERT INTO `inventory` VALUES (346, 53, 66, '2025-07-10', '右', 2, 1, 1, 338, '成品', 132, 1, '2025-07-15 21:33:06', NULL, 0);
INSERT INTO `inventory` VALUES (347, 53, 66, '2025-07-10', '右', 3, 1, 1, 338, '成品', 133, 2, '2025-07-15 21:34:01', NULL, 0);
INSERT INTO `inventory` VALUES (348, 53, 66, '2025-07-10', '右', 4, 1, 1, 338, '成品', 133, 2, '2025-07-15 21:34:01', NULL, 0);
INSERT INTO `inventory` VALUES (356, 18, 47, '2025-07-16', '左', 8, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:17:50', NULL, 10);
INSERT INTO `inventory` VALUES (357, 18, 47, '2025-07-16', '左', 9, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:17:50', NULL, 20);
INSERT INTO `inventory` VALUES (358, 18, 47, '2025-07-16', '左', 10, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:17:50', NULL, 20);
INSERT INTO `inventory` VALUES (359, 18, 47, '2025-07-16', '右', 1, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:17:50', NULL, 20);
INSERT INTO `inventory` VALUES (360, 18, 47, '2025-07-16', '右', 2, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:17:50', NULL, 20);
INSERT INTO `inventory` VALUES (361, 18, 47, '2025-07-16', '右', 3, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:17:50', NULL, 20);
INSERT INTO `inventory` VALUES (362, 18, 47, '2025-07-16', '右', 4, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:17:50', NULL, 20);
INSERT INTO `inventory` VALUES (363, 18, 47, '2025-07-16', '右', 5, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:17:50', NULL, 20);
INSERT INTO `inventory` VALUES (364, 18, 47, '2025-07-16', '右', 6, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:17:50', NULL, 20);
INSERT INTO `inventory` VALUES (365, 18, 47, '2025-07-16', '右', 7, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:17:50', NULL, 20);
INSERT INTO `inventory` VALUES (366, 18, 47, '2025-07-16', '右', 8, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:17:50', NULL, 20);
INSERT INTO `inventory` VALUES (367, 18, 47, '2025-07-16', '右', 9, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:17:50', NULL, 20);
INSERT INTO `inventory` VALUES (368, 18, 47, '2025-07-16', '右', 10, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:17:50', NULL, 20);
INSERT INTO `inventory` VALUES (369, 19, 47, '2025-07-16', '左', 1, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:18:45', NULL, 20);
INSERT INTO `inventory` VALUES (370, 19, 47, '2025-07-16', '左', 2, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:18:45', NULL, 20);
INSERT INTO `inventory` VALUES (371, 19, 47, '2025-07-16', '左', 3, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:18:45', NULL, 20);
INSERT INTO `inventory` VALUES (372, 19, 47, '2025-07-16', '左', 4, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:18:45', NULL, 20);
INSERT INTO `inventory` VALUES (373, 19, 47, '2025-07-16', '左', 5, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:18:45', NULL, 20);
INSERT INTO `inventory` VALUES (374, 19, 47, '2025-07-16', '左', 6, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:18:45', NULL, 20);
INSERT INTO `inventory` VALUES (375, 19, 47, '2025-07-16', '左', 7, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:18:45', NULL, 20);
INSERT INTO `inventory` VALUES (376, 19, 47, '2025-07-16', '左', 8, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:18:45', NULL, 20);
INSERT INTO `inventory` VALUES (377, 19, 47, '2025-07-16', '左', 9, 1, 1, 340, '半成品', NULL, 1, '2025-07-16 16:18:46', NULL, 5);
INSERT INTO `inventory` VALUES (378, 52, 69, '2025-07-16', '左', 1, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:39', NULL, 1);
INSERT INTO `inventory` VALUES (379, 52, 69, '2025-07-16', '左', 2, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:39', NULL, 1);
INSERT INTO `inventory` VALUES (380, 52, 69, '2025-07-16', '左', 3, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:39', NULL, 1);
INSERT INTO `inventory` VALUES (381, 52, 69, '2025-07-16', '左', 4, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:39', NULL, 1);
INSERT INTO `inventory` VALUES (382, 52, 69, '2025-07-16', '左', 5, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:39', NULL, 1);
INSERT INTO `inventory` VALUES (383, 52, 69, '2025-07-16', '左', 6, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (384, 52, 69, '2025-07-16', '左', 7, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (385, 52, 69, '2025-07-16', '左', 8, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (386, 52, 69, '2025-07-16', '左', 9, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (387, 52, 69, '2025-07-16', '左', 10, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (388, 52, 69, '2025-07-16', '右', 1, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (389, 52, 69, '2025-07-16', '右', 2, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (390, 52, 69, '2025-07-16', '右', 3, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (391, 52, 69, '2025-07-16', '右', 4, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (392, 52, 69, '2025-07-16', '右', 5, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (393, 52, 69, '2025-07-16', '右', 6, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (394, 52, 69, '2025-07-16', '右', 7, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (395, 52, 69, '2025-07-16', '右', 8, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (396, 52, 69, '2025-07-16', '右', 9, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (397, 52, 69, '2025-07-16', '右', 10, 1, 1, 341, '成品', 134, 1, '2025-07-16 16:19:40', NULL, 1);
INSERT INTO `inventory` VALUES (398, 64, 47, '2025-07-03', '左', 1, 1, 1, 319, '半成品', NULL, 1, '2025-07-16 18:17:59', NULL, 20);
INSERT INTO `inventory` VALUES (399, 64, 47, '2025-07-03', '左', 2, 1, 1, 319, '半成品', NULL, 1, '2025-07-16 18:17:59', NULL, 20);
INSERT INTO `inventory` VALUES (400, 64, 47, '2025-07-09', '左', 3, 1, 1, 326, '半成品', NULL, 1, '2025-07-16 18:18:00', NULL, 10);

-- ----------------------------
-- Table structure for operation_log
-- ----------------------------
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `table_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作对象表名',
  `operation_type` enum('INSERT','UPDATE','DELETE') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作类型',
  `operator` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作人',
  `operation_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `changed_fields` json NULL COMMENT '受影响的字段及其新值（插入或更新后的数据）',
  `old_data` json NULL COMMENT '对于更新或删除操作，记录修改或删除前的数据',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 797 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of operation_log
-- ----------------------------
INSERT INTO `operation_log` VALUES (58, '筛网', 'INSERT', 'csc', '2025-03-08 13:54:27', '\"{\\\"id\\\":10,\\\"meshName\\\":\\\"string\\\",\\\"description\\\":\\\"string\\\",\\\"createdAt\\\":[2025,3,8,13,54,26,622063500],\\\"createdBy\\\":2,\\\"updatedAt\\\":null,\\\"updatedBy\\\":null}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (59, '产品', 'INSERT', 'csc', '2025-03-08 14:27:01', '\"{\\\"productName\\\":\\\"测试\\\",\\\"type\\\":\\\"白冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packaging\\\":\\\"箱\\\",\\\"weightPerPiece\\\":40,\\\"piecesPerPallet\\\":100,\\\"canStack\\\":true,\\\"id\\\":null}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (60, '产品', 'UPDATE', 'csc', '2025-03-08 14:28:31', '\"{\\\"piecesPerPallet\\\":25,\\\"productName\\\":\\\"测试111\\\",\\\"weightPerPiece\\\":30.00}\"', '\"{\\\"id\\\":79,\\\"productName\\\":\\\"测试\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":null,\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":100,\\\"canStack\\\":true,\\\"createdBy\\\":2,\\\"createdAt\\\":[2025,3,8,14,27,1],\\\"updatedBy\\\":null,\\\"updatedAt\\\":null}\"');
INSERT INTO `operation_log` VALUES (61, '产品', 'UPDATE', 'csc', '2025-03-08 14:29:19', '\"{\\\"packagingMethod\\\":\\\"箱\\\",\\\"productName\\\":\\\"测试121\\\"}\"', '\"{\\\"id\\\":79,\\\"productName\\\":\\\"测试111\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":null,\\\"weightPerPiece\\\":30.00,\\\"piecesPerPallet\\\":25,\\\"canStack\\\":true,\\\"createdBy\\\":2,\\\"createdAt\\\":[2025,3,8,14,27,1],\\\"updatedBy\\\":2,\\\"updatedAt\\\":[2025,3,8,14,28,31]}\"');
INSERT INTO `operation_log` VALUES (66, '产品', 'UPDATE', 'csc', '2025-03-08 14:37:41', '\"{\\\"canStack\\\":false}\"', '\"{\\\"id\\\":79,\\\"productName\\\":\\\"测试121\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"箱\\\",\\\"weightPerPiece\\\":30.00,\\\"piecesPerPallet\\\":25,\\\"canStack\\\":true,\\\"createdBy\\\":2,\\\"createdAt\\\":[2025,3,8,14,27,1],\\\"updatedBy\\\":2,\\\"updatedAt\\\":[2025,3,8,14,34,41]}\"');
INSERT INTO `operation_log` VALUES (67, '产品', 'UPDATE', 'csc', '2025-03-08 14:37:56', '\"{\\\"canStack\\\":true}\"', '\"{\\\"id\\\":79,\\\"productName\\\":\\\"测试121\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"箱\\\",\\\"weightPerPiece\\\":30.00,\\\"piecesPerPallet\\\":25,\\\"canStack\\\":false,\\\"createdBy\\\":2,\\\"createdAt\\\":[2025,3,8,14,27,1],\\\"updatedBy\\\":2,\\\"updatedAt\\\":[2025,3,8,14,37,41]}\"');
INSERT INTO `operation_log` VALUES (68, '化验数据', 'INSERT', 'csc', '2025-03-08 14:49:34', '\"{\\\"productId\\\":60,\\\"sampleDate\\\":[2025,3,8],\\\"colorValue\\\":125,\\\"reducingSugar\\\":35.4,\\\"phValue\\\":6.3}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (69, '产品', 'INSERT', 'csc', '2025-03-08 15:37:37', '\"{\\\"productName\\\":\\\"测试2\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"箱\\\",\\\"weightPerPiece\\\":40,\\\"piecesPerPallet\\\":100,\\\"canStack\\\":true,\\\"id\\\":null}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (70, '产品', 'INSERT', 'csc', '2025-03-08 15:46:52', '\"{\\\"productName\\\":\\\"测试3\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"status\\\":\\\"成品\\\",\\\"packagingMethod\\\":\\\"箱\\\",\\\"weightPerPiece\\\":40,\\\"piecesPerPallet\\\":100,\\\"canStack\\\":true,\\\"id\\\":null}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (71, '化验数据', 'INSERT', 'csc', '2025-03-08 15:47:50', '\"{\\\"productId\\\":61,\\\"sampleDate\\\":[2025,3,8],\\\"colorValue\\\":125,\\\"reducingSugar\\\":35.4,\\\"phValue\\\":6.3}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (72, '化验数据', 'INSERT', 'csc', '2025-03-08 15:51:19', '\"{\\\"productId\\\":70,\\\"sampleDate\\\":[2025,3,8],\\\"colorValue\\\":125,\\\"reducingSugar\\\":35.4,\\\"phValue\\\":6.3}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (73, '化验数据', 'INSERT', 'csc', '2025-03-08 15:52:56', '\"{\\\"productId\\\":70,\\\"sampleDate\\\":[2025,3,9],\\\"colorValue\\\":125,\\\"reducingSugar\\\":35.4,\\\"phValue\\\":6.3}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (74, '化验数据', 'INSERT', 'csc', '2025-03-08 15:54:24', '\"{\\\"productId\\\":70,\\\"sampleDate\\\":[2025,3,10],\\\"colorValue\\\":125,\\\"reducingSugar\\\":35.5,\\\"phValue\\\":6.3}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (75, '化验数据', 'INSERT', 'csc', '2025-03-08 15:57:47', '\"{\\\"sampleDate\\\":[2025,3,11],\\\"colorValue\\\":125,\\\"phValue\\\":6.3,\\\"reducingSugar\\\":35.5,\\\"productName\\\":\\\"黄浮冰\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (76, '产品', 'DELETE', 'csc', '2025-03-09 20:01:08', '\"{}\"', '\"{\\\"createdAt\\\":[2025,3,8,15,46,51],\\\"packagingMethod\\\":\\\"箱\\\",\\\"updatedBy\\\":null,\\\"canStack\\\":true,\\\"createdBy\\\":2,\\\"piecesPerPallet\\\":100,\\\"id\\\":81,\\\"productName\\\":\\\"测试3\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"weightPerPiece\\\":40.00,\\\"status\\\":\\\"成品\\\",\\\"updatedAt\\\":null}\"');
INSERT INTO `operation_log` VALUES (77, '产品', 'UPDATE', 'csc', '2025-03-09 20:56:16', '\"{\\\"canStack\\\":false}\"', '\"{\\\"createdAt\\\":[2025,3,8,14,27,1],\\\"packagingMethod\\\":\\\"箱\\\",\\\"updatedBy\\\":2,\\\"canStack\\\":true,\\\"createdBy\\\":2,\\\"piecesPerPallet\\\":25,\\\"id\\\":79,\\\"productName\\\":\\\"测试121\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"weightPerPiece\\\":30.00,\\\"status\\\":\\\"半成品\\\",\\\"updatedAt\\\":[2025,3,8,14,37,56]}\"');
INSERT INTO `operation_log` VALUES (78, '产品', 'UPDATE', 'csc', '2025-03-09 20:56:47', '\"{\\\"piecesPerPallet\\\":30,\\\"packagingMethod\\\":\\\"箱\\\",\\\"productName\\\":\\\"测试产品www\\\",\\\"productType\\\":\\\"白冰糖\\\"}\"', '\"{\\\"createdAt\\\":[2025,3,4,14,29,56],\\\"packagingMethod\\\":\\\"袋\\\",\\\"updatedBy\\\":2,\\\"canStack\\\":false,\\\"createdBy\\\":1,\\\"piecesPerPallet\\\":1,\\\"id\\\":72,\\\"productName\\\":\\\"测试产品2\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"weightPerPiece\\\":40.00,\\\"status\\\":\\\"成品\\\",\\\"updatedAt\\\":[2025,3,7,14,27,21]}\"');
INSERT INTO `operation_log` VALUES (79, '产品', 'UPDATE', 'csc', '2025-03-09 21:03:23', '\"{}\"', '\"{\\\"createdAt\\\":[2025,2,20,3,36,33],\\\"packagingMethod\\\":\\\"袋\\\",\\\"updatedBy\\\":1,\\\"canStack\\\":false,\\\"createdBy\\\":1,\\\"piecesPerPallet\\\":1,\\\"id\\\":36,\\\"productName\\\":\\\"正中冰\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"weightPerPiece\\\":40.00,\\\"status\\\":\\\"半成品\\\",\\\"updatedAt\\\":[2025,2,22,3,33,12]}\"');
INSERT INTO `operation_log` VALUES (80, '产品', 'UPDATE', 'csc', '2025-03-09 21:03:35', '\"{\\\"productName\\\":\\\"正中冰1\\\"}\"', '\"{\\\"createdAt\\\":[2025,2,20,3,36,33],\\\"packagingMethod\\\":\\\"袋\\\",\\\"updatedBy\\\":2,\\\"canStack\\\":false,\\\"createdBy\\\":1,\\\"piecesPerPallet\\\":1,\\\"id\\\":36,\\\"productName\\\":\\\"正中冰\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"weightPerPiece\\\":40.00,\\\"status\\\":\\\"半成品\\\",\\\"updatedAt\\\":[2025,3,9,21,3,23]}\"');
INSERT INTO `operation_log` VALUES (81, '产品', 'UPDATE', 'csc', '2025-03-09 21:03:41', '\"{\\\"productName\\\":\\\"正中冰\\\"}\"', '\"{\\\"createdAt\\\":[2025,2,20,3,36,33],\\\"packagingMethod\\\":\\\"袋\\\",\\\"updatedBy\\\":2,\\\"canStack\\\":false,\\\"createdBy\\\":1,\\\"piecesPerPallet\\\":1,\\\"id\\\":36,\\\"productName\\\":\\\"正中冰1\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"weightPerPiece\\\":40.00,\\\"status\\\":\\\"半成品\\\",\\\"updatedAt\\\":[2025,3,9,21,3,35]}\"');
INSERT INTO `operation_log` VALUES (82, '产品', 'UPDATE', 'csc', '2025-03-09 21:04:14', '\"{\\\"productName\\\":\\\"正中冰1\\\"}\"', '\"{\\\"createdAt\\\":[2025,2,20,3,36,33],\\\"packagingMethod\\\":\\\"袋\\\",\\\"updatedBy\\\":2,\\\"canStack\\\":false,\\\"createdBy\\\":1,\\\"piecesPerPallet\\\":1,\\\"id\\\":36,\\\"productName\\\":\\\"正中冰\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"weightPerPiece\\\":40.00,\\\"status\\\":\\\"半成品\\\",\\\"updatedAt\\\":[2025,3,9,21,3,41]}\"');
INSERT INTO `operation_log` VALUES (83, '产品', 'UPDATE', 'csc', '2025-03-09 21:04:33', '\"{\\\"productName\\\":\\\"正中冰\\\"}\"', '\"{\\\"createdAt\\\":[2025,2,20,3,36,33],\\\"packagingMethod\\\":\\\"袋\\\",\\\"updatedBy\\\":2,\\\"canStack\\\":false,\\\"createdBy\\\":1,\\\"piecesPerPallet\\\":1,\\\"id\\\":36,\\\"productName\\\":\\\"正中冰1\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"weightPerPiece\\\":40.00,\\\"status\\\":\\\"半成品\\\",\\\"updatedAt\\\":[2025,3,9,21,4,14]}\"');
INSERT INTO `operation_log` VALUES (84, '产品', 'DELETE', 'csc', '2025-03-09 21:05:12', '\"{}\"', '\"{\\\"createdAt\\\":[2025,3,8,15,37,36],\\\"packagingMethod\\\":\\\"箱\\\",\\\"updatedBy\\\":null,\\\"canStack\\\":true,\\\"createdBy\\\":2,\\\"piecesPerPallet\\\":100,\\\"id\\\":80,\\\"productName\\\":\\\"测试2\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"weightPerPiece\\\":40.00,\\\"status\\\":\\\"半成品\\\",\\\"updatedAt\\\":null}\"');
INSERT INTO `operation_log` VALUES (85, '产品', 'UPDATE', 'csc', '2025-03-09 21:09:35', '\"{\\\"productName\\\":\\\"正中冰1\\\"}\"', '\"{\\\"createdAt\\\":[2025,2,20,3,36,33],\\\"packagingMethod\\\":\\\"袋\\\",\\\"updatedBy\\\":2,\\\"canStack\\\":false,\\\"createdBy\\\":1,\\\"piecesPerPallet\\\":1,\\\"id\\\":36,\\\"productName\\\":\\\"正中冰\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"weightPerPiece\\\":40.00,\\\"status\\\":\\\"半成品\\\",\\\"updatedAt\\\":[2025,3,9,21,4,33]}\"');
INSERT INTO `operation_log` VALUES (86, '产品', 'UPDATE', 'csc', '2025-03-09 21:10:22', '\"{\\\"productName\\\":\\\"正中冰\\\"}\"', '\"{\\\"createdAt\\\":[2025,2,20,3,36,33],\\\"packagingMethod\\\":\\\"袋\\\",\\\"updatedBy\\\":2,\\\"canStack\\\":false,\\\"createdBy\\\":1,\\\"piecesPerPallet\\\":1,\\\"id\\\":36,\\\"productName\\\":\\\"正中冰1\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"weightPerPiece\\\":40.00,\\\"status\\\":\\\"半成品\\\",\\\"updatedAt\\\":[2025,3,9,21,9,35]}\"');
INSERT INTO `operation_log` VALUES (87, '产品', 'INSERT', 'csc', '2025-03-09 21:49:06', '\"{\\\"packagingMethod\\\":\\\"袋\\\",\\\"canStack\\\":false,\\\"piecesPerPallet\\\":25,\\\"id\\\":null,\\\"productName\\\":\\\"WWWWWW\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"weightPerPiece\\\":40,\\\"status\\\":\\\"半成品\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (88, '产品', 'DELETE', 'csc', '2025-03-10 15:06:27', '\"{}\"', '\"{\\\"createdAt\\\":[2025,3,9,21,49,6],\\\"packagingMethod\\\":\\\"袋\\\",\\\"updatedBy\\\":null,\\\"canStack\\\":false,\\\"createdBy\\\":2,\\\"piecesPerPallet\\\":25,\\\"id\\\":82,\\\"productName\\\":\\\"WWWWWW\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"weightPerPiece\\\":40.00,\\\"status\\\":\\\"半成品\\\",\\\"updatedAt\\\":null}\"');
INSERT INTO `operation_log` VALUES (89, '产品', 'DELETE', 'csc', '2025-03-10 15:06:30', '\"{}\"', '\"{\\\"createdAt\\\":[2025,3,8,14,27,1],\\\"packagingMethod\\\":\\\"箱\\\",\\\"updatedBy\\\":2,\\\"canStack\\\":false,\\\"createdBy\\\":2,\\\"piecesPerPallet\\\":25,\\\"id\\\":79,\\\"productName\\\":\\\"测试121\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"weightPerPiece\\\":30.00,\\\"status\\\":\\\"半成品\\\",\\\"updatedAt\\\":[2025,3,9,20,56,16]}\"');
INSERT INTO `operation_log` VALUES (90, '库位', 'UPDATE', 'i', '2025-03-11 16:22:46', '\"{}\"', '\"{\\\"curCapacity\\\":9,\\\"createdAt\\\":[2025,2,24,22,23,8],\\\"maxRows\\\":25,\\\"warehouseId\\\":\\\"101\\\",\\\"maxCapacity\\\":50,\\\"id\\\":101,\\\"warehouseName\\\":\\\"SS\\\",\\\"status\\\":\\\"正常\\\"}\"');
INSERT INTO `operation_log` VALUES (91, '库位', 'UPDATE', 'i', '2025-03-11 16:27:16', '\"{}\"', '\"{\\\"curCapacity\\\":9,\\\"createdAt\\\":[2025,2,24,22,23,8],\\\"maxRows\\\":25,\\\"warehouseId\\\":\\\"101\\\",\\\"maxCapacity\\\":50,\\\"id\\\":101,\\\"warehouseName\\\":\\\"SS\\\",\\\"status\\\":\\\"维护\\\"}\"');
INSERT INTO `operation_log` VALUES (92, '库位', 'UPDATE', 'i', '2025-03-11 16:45:52', '\"{}\"', '\"{\\\"curCapacity\\\":0,\\\"createdAt\\\":[2025,3,7,21,42,18],\\\"maxRows\\\":25,\\\"warehouseId\\\":\\\"102\\\",\\\"maxCapacity\\\":50,\\\"id\\\":102,\\\"warehouseName\\\":\\\"102\\\",\\\"status\\\":\\\"空置\\\"}\"');
INSERT INTO `operation_log` VALUES (93, '库位', 'INSERT', 'i', '2025-03-11 18:07:38', '\"{}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (94, '库位', 'UPDATE', 'i', '2025-03-11 18:22:49', '\"{}\"', '\"{\\\"curCapacity\\\":0,\\\"createdAt\\\":[2025,3,7,21,42,18],\\\"maxRows\\\":25,\\\"warehouseId\\\":\\\"102\\\",\\\"maxCapacity\\\":50,\\\"id\\\":102,\\\"warehouseName\\\":\\\"102\\\",\\\"status\\\":\\\"维护\\\"}\"');
INSERT INTO `operation_log` VALUES (95, '库位', 'UPDATE', 'i', '2025-03-11 18:28:21', '\"{}\"', '\"{\\\"curCapacity\\\":9,\\\"createdAt\\\":[2025,2,24,22,23,8],\\\"maxRows\\\":10,\\\"warehouseId\\\":\\\"101\\\",\\\"maxCapacity\\\":20,\\\"id\\\":101,\\\"warehouseName\\\":\\\"S2WQA\\\",\\\"status\\\":\\\"正常\\\"}\"');
INSERT INTO `operation_log` VALUES (96, '库位', 'UPDATE', 'i', '2025-03-11 18:28:25', '\"{}\"', '\"{\\\"curCapacity\\\":9,\\\"createdAt\\\":[2025,2,24,22,23,8],\\\"maxRows\\\":10,\\\"warehouseId\\\":\\\"101\\\",\\\"maxCapacity\\\":20,\\\"id\\\":101,\\\"warehouseName\\\":\\\"S2WQA\\\",\\\"status\\\":\\\"维护\\\"}\"');
INSERT INTO `operation_log` VALUES (97, '库位', 'UPDATE', 'i', '2025-03-11 18:38:33', '\"{}\"', '\"{\\\"curCapacity\\\":9,\\\"createdAt\\\":[2025,2,24,22,23,8],\\\"maxRows\\\":10,\\\"warehouseId\\\":\\\"101\\\",\\\"maxCapacity\\\":20,\\\"id\\\":101,\\\"warehouseName\\\":\\\"S2WQA\\\",\\\"status\\\":\\\"正常\\\"}\"');
INSERT INTO `operation_log` VALUES (98, '库位', 'UPDATE', 'i', '2025-03-11 18:38:35', '\"{}\"', '\"{\\\"curCapacity\\\":9,\\\"createdAt\\\":[2025,2,24,22,23,8],\\\"maxRows\\\":10,\\\"warehouseId\\\":\\\"101\\\",\\\"maxCapacity\\\":20,\\\"id\\\":101,\\\"warehouseName\\\":\\\"S2WQA\\\",\\\"status\\\":\\\"维护\\\"}\"');
INSERT INTO `operation_log` VALUES (99, '库位', 'DELETE', 'i', '2025-03-11 18:38:50', '\"{}\"', '\"{\\\"curCapacity\\\":0,\\\"createdAt\\\":[2025,3,11,18,7,38],\\\"maxRows\\\":10,\\\"warehouseId\\\":\\\"103\\\",\\\"maxCapacity\\\":20,\\\"id\\\":103,\\\"warehouseName\\\":\\\"AAA\\\",\\\"status\\\":\\\"空置\\\"}\"');
INSERT INTO `operation_log` VALUES (100, '筛网', 'DELETE', 'i', '2025-03-11 20:02:25', '\"{}\"', '\"{\\\"createdAt\\\":[2025,3,8,13,54,26],\\\"updatedBy\\\":null,\\\"createdBy\\\":2,\\\"description\\\":\\\"string\\\",\\\"meshName\\\":\\\"string\\\",\\\"id\\\":10,\\\"updatedAt\\\":null}\"');
INSERT INTO `operation_log` VALUES (101, '筛网', 'UPDATE', 'i', '2025-03-11 20:37:37', '\"{\\\"description\\\":\\\"SSSSS\\\"}\"', '\"{\\\"createdAt\\\":[2025,2,28,15,35,53],\\\"updatedBy\\\":null,\\\"createdBy\\\":1,\\\"description\\\":null,\\\"meshName\\\":\\\"Test\\\",\\\"id\\\":1,\\\"updatedAt\\\":[2025,3,4,14,20,50]}\"');
INSERT INTO `operation_log` VALUES (102, '筛网', 'UPDATE', 'i', '2025-03-11 20:38:04', '\"{\\\"description\\\":\\\"科比专用筛网：过滤牢大碎片111\\\"}\"', '\"{\\\"createdAt\\\":[2025,3,4,18,8,13],\\\"updatedBy\\\":null,\\\"createdBy\\\":1,\\\"description\\\":null,\\\"meshName\\\":\\\"kobe111\\\",\\\"id\\\":9,\\\"updatedAt\\\":null}\"');
INSERT INTO `operation_log` VALUES (103, '筛网', 'UPDATE', 'i', '2025-03-11 20:43:24', '\"{\\\"description\\\":\\\"科比专用筛网：过滤牢大碎片，收集小布丁\\\"}\"', '\"{\\\"createdAt\\\":[2025,3,4,18,8,13],\\\"updatedBy\\\":13,\\\"createdBy\\\":1,\\\"description\\\":\\\"科比专用筛网：过滤牢大碎片111\\\",\\\"meshName\\\":\\\"kobe111\\\",\\\"id\\\":9,\\\"updatedAt\\\":[2025,3,11,20,38,3]}\"');
INSERT INTO `operation_log` VALUES (104, '筛网', 'UPDATE', 'i', '2025-03-11 20:43:49', '\"{\\\"meshName\\\":\\\"tes369\\\"}\"', '\"{\\\"createdAt\\\":[2025,3,4,14,43,51],\\\"updatedBy\\\":1,\\\"createdBy\\\":1,\\\"description\\\":null,\\\"meshName\\\":\\\"test333\\\",\\\"id\\\":3,\\\"updatedAt\\\":[2025,3,4,14,45,9]}\"');
INSERT INTO `operation_log` VALUES (105, '筛网', 'INSERT', 'i', '2025-03-11 20:44:24', '\"{\\\"description\\\":\\\"\\\",\\\"meshName\\\":\\\"理塘王指定滤网\\\",\\\"id\\\":0}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (106, '筛网', 'DELETE', 'i', '2025-03-11 20:44:50', '\"{}\"', '\"{\\\"createdAt\\\":[2025,3,4,14,49,53],\\\"updatedBy\\\":null,\\\"createdBy\\\":1,\\\"description\\\":null,\\\"meshName\\\":\\\"test4\\\",\\\"id\\\":4,\\\"updatedAt\\\":null}\"');
INSERT INTO `operation_log` VALUES (107, '化验数据', 'INSERT', 'csc', '2025-03-13 00:10:08', '\"{\\\"phValue\\\":6.8,\\\"insolubleImpurity\\\":35.0,\\\"reducingSugar\\\":0.55,\\\"sampleDate\\\":[2025,3,10],\\\"colorValue\\\":250.0,\\\"dryWeight\\\":1.05,\\\"sucrose\\\":98.0,\\\"productName\\\":\\\"黄中冰净\\\",\\\"conductivityAsh\\\":0.12}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (108, '检验标准', 'INSERT', 'csc', '2025-03-13 00:20:45', '\"{\\\"phMin\\\":0,\\\"standardName\\\":\\\"一级\\\",\\\"dryWeightMin\\\":null,\\\"colorMax\\\":150,\\\"conductivityAshMin\\\":null,\\\"colorMin\\\":null,\\\"dryWeightMax\\\":1.40,\\\"insolubleImpurityMax\\\":60,\\\"sucroseMin\\\":97.8,\\\"conductivityAshMax\\\":0.13,\\\"reducingSugarMin\\\":null,\\\"phMax\\\":14,\\\"sucroseMax\\\":null,\\\"reducingSugarMax\\\":0.7,\\\"id\\\":null,\\\"insolubleImpurityMin\\\":null,\\\"productType\\\":\\\"白冰糖\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (109, '检验标准', 'UPDATE', 'csc', '2025-03-13 00:33:12', '\"{\\\"phMin\\\":0,\\\"standardName\\\":null,\\\"colorMax\\\":150,\\\"phMax\\\":14,\\\"reducingSugarMax\\\":0.7,\\\"insolubleImpurityMax\\\":60,\\\"productType\\\":null,\\\"sucroseMin\\\":97.8}\"', '\"{\\\"phMin\\\":0.00,\\\"standardName\\\":\\\"一级\\\",\\\"dryWeightMin\\\":null,\\\"colorMax\\\":150.00,\\\"conductivityAshMin\\\":null,\\\"colorMin\\\":null,\\\"dryWeightMax\\\":1.40,\\\"insolubleImpurityMax\\\":60.00,\\\"sucroseMin\\\":97.80,\\\"conductivityAshMax\\\":0.13,\\\"reducingSugarMin\\\":null,\\\"phMax\\\":14.00,\\\"sucroseMax\\\":null,\\\"reducingSugarMax\\\":0.70,\\\"id\\\":2,\\\"insolubleImpurityMin\\\":null,\\\"productType\\\":\\\"白冰糖\\\"}\"');
INSERT INTO `operation_log` VALUES (120, '化验数据', 'INSERT', 'csc', '2025-03-13 14:48:05', '\"{\\\"phValue\\\":6.8,\\\"insolubleImpurity\\\":35.0,\\\"reducingSugar\\\":0.55,\\\"sampleDate\\\":[2025,3,13],\\\"colorValue\\\":250.0,\\\"dryWeight\\\":1.05,\\\"sucrose\\\":98.0,\\\"productName\\\":\\\"中冰\\\",\\\"conductivityAsh\\\":0.12}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (125, '检验标准', 'UPDATE', 'csc', '2025-03-13 15:57:30', '\"{\\\"colorMax\\\":150,\\\"insolubleImpurityMax\\\":60,\\\"phMax\\\":14,\\\"phMin\\\":0,\\\"reducingSugarMax\\\":0.7,\\\"sucroseMin\\\":97.1}\"', '\"{\\\"colorMax\\\":150.00,\\\"colorMin\\\":null,\\\"conductivityAshMax\\\":0.13,\\\"conductivityAshMin\\\":null,\\\"dryWeightMax\\\":1.40,\\\"dryWeightMin\\\":null,\\\"id\\\":2,\\\"insolubleImpurityMax\\\":60.00,\\\"insolubleImpurityMin\\\":null,\\\"phMax\\\":14.00,\\\"phMin\\\":0.00,\\\"productType\\\":\\\"白冰糖\\\",\\\"reducingSugarMax\\\":0.70,\\\"reducingSugarMin\\\":null,\\\"standardName\\\":\\\"一级\\\",\\\"sucroseMax\\\":null,\\\"sucroseMin\\\":97.10}\"');
INSERT INTO `operation_log` VALUES (126, '检验标准', 'UPDATE', 'csc', '2025-03-13 16:00:30', '\"{\\\"colorMax\\\":150,\\\"insolubleImpurityMax\\\":60,\\\"phMax\\\":14,\\\"phMin\\\":0,\\\"reducingSugarMax\\\":0.7,\\\"sucroseMin\\\":97.1}\"', '\"{\\\"colorMax\\\":150.00,\\\"colorMin\\\":null,\\\"conductivityAshMax\\\":0.13,\\\"conductivityAshMin\\\":null,\\\"dryWeightMax\\\":1.40,\\\"dryWeightMin\\\":null,\\\"id\\\":2,\\\"insolubleImpurityMax\\\":60.00,\\\"insolubleImpurityMin\\\":null,\\\"phMax\\\":14.00,\\\"phMin\\\":0.00,\\\"productType\\\":\\\"白冰糖\\\",\\\"reducingSugarMax\\\":0.70,\\\"reducingSugarMin\\\":null,\\\"standardName\\\":\\\"一级\\\",\\\"sucroseMax\\\":null,\\\"sucroseMin\\\":97.10}\"');
INSERT INTO `operation_log` VALUES (127, '化验数据', 'INSERT', 'i', '2025-03-14 22:15:22', '\"{}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (128, '化验数据', 'INSERT', 'i', '2025-03-14 22:17:17', '\"{\\\"colorValue\\\":1,\\\"conductivityAsh\\\":1,\\\"dryWeight\\\":1,\\\"insolubleImpurity\\\":1,\\\"phValue\\\":1,\\\"reducingSugar\\\":1,\\\"sampleDate\\\":[2025,3,14],\\\"sucrose\\\":null,\\\"productName\\\":\\\"多晶冰糖净\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (129, '化验数据', 'INSERT', 'i', '2025-03-14 23:00:06', '\"{}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (130, '化验数据', 'INSERT', 'i', '2025-03-14 23:01:29', '\"{\\\"colorValue\\\":5,\\\"conductivityAsh\\\":4,\\\"dryWeight\\\":3,\\\"insolubleImpurity\\\":6,\\\"phValue\\\":7,\\\"reducingSugar\\\":2,\\\"sampleDate\\\":[2025,3,14],\\\"sucrose\\\":null,\\\"productName\\\":\\\"中冰\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (131, '化验数据', 'INSERT', 'i', '2025-03-14 23:08:59', '\"{\\\"colorValue\\\":5,\\\"conductivityAsh\\\":4,\\\"dryWeight\\\":3,\\\"insolubleImpurity\\\":6,\\\"phValue\\\":7,\\\"reducingSugar\\\":2,\\\"sampleDate\\\":[2025,3,14],\\\"sucrose\\\":null,\\\"productName\\\":\\\"中冰\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (132, '化验数据', 'INSERT', 'i', '2025-03-14 23:09:24', '\"{}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (133, '化验数据', 'INSERT', 'i', '2025-03-16 16:54:01', '\"{\\\"colorValue\\\":250,\\\"conductivityAsh\\\":0.12,\\\"dryWeight\\\":1.32,\\\"insolubleImpurity\\\":56.0,\\\"phValue\\\":6.5,\\\"reducingSugar\\\":0.56,\\\"sampleDate\\\":[2025,3,16],\\\"sucrose\\\":98.1,\\\"productName\\\":\\\"黄冰糖小粒净\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (134, '化验数据', 'INSERT', 'i', '2025-03-16 19:30:51', '\"{}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (135, '化验数据', 'INSERT', 'i', '2025-03-16 19:31:43', '\"{}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (136, '化验数据', 'INSERT', 'i', '2025-03-17 17:20:55', '\"{\\\"colorValue\\\":110,\\\"conductivityAsh\\\":0.09,\\\"dryWeight\\\":1.33,\\\"insolubleImpurity\\\":56,\\\"phValue\\\":6.7,\\\"reducingSugar\\\":0.69,\\\"sampleDate\\\":[2025,3,17],\\\"sucrose\\\":97.9,\\\"productName\\\":\\\"正中冰\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (137, '库位', 'INSERT', 'i', '2025-03-17 23:55:08', '\"{\\\"id\\\":0,\\\"maxRows\\\":10,\\\"warehouseName\\\":\\\"1\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (138, '库位', 'INSERT', 'i', '2025-03-17 23:55:18', '\"{\\\"id\\\":0,\\\"maxRows\\\":10,\\\"warehouseName\\\":\\\"2\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (139, '化验数据', 'INSERT', 'i', '2025-03-18 00:07:31', '\"{\\\"colorValue\\\":2,\\\"conductivityAsh\\\":2,\\\"dryWeight\\\":2,\\\"insolubleImpurity\\\":2,\\\"phValue\\\":2,\\\"reducingSugar\\\":2,\\\"sampleDate\\\":[2025,3,18],\\\"sucrose\\\":2,\\\"productName\\\":\\\"中冰\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (140, '库位', 'INSERT', 'i', '2025-03-18 00:38:14', '\"{\\\"id\\\":0,\\\"maxRows\\\":10,\\\"warehouseName\\\":\\\"3\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (141, '产品', 'UPDATE', 'i', '2025-03-18 00:38:37', '\"{\\\"productName\\\":\\\"测试产品kobe\\\"}\"', '\"{\\\"canStack\\\":false,\\\"createdAt\\\":[2025,3,4,14,29,56],\\\"createdBy\\\":1,\\\"id\\\":72,\\\"packagingMethod\\\":\\\"箱\\\",\\\"piecesPerPallet\\\":30,\\\"productName\\\":\\\"测试产品www\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"status\\\":\\\"成品\\\",\\\"updatedAt\\\":[2025,3,9,20,56,47],\\\"updatedBy\\\":2,\\\"weightPerPiece\\\":40.00}\"');
INSERT INTO `operation_log` VALUES (142, '化验数据', 'INSERT', 'i', '2025-03-19 01:11:12', '\"{\\\"colorValue\\\":2,\\\"conductivityAsh\\\":2,\\\"dryWeight\\\":2,\\\"insolubleImpurity\\\":2,\\\"phValue\\\":2,\\\"reducingSugar\\\":2,\\\"sampleDate\\\":[2025,3,19],\\\"sucrose\\\":2,\\\"productName\\\":\\\"中冰\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (143, '库位', 'UPDATE', 'i', '2025-03-21 15:48:40', '\"{}\"', '\"{\\\"createdAt\\\":[2025,3,18,0,38,14],\\\"curCapacity\\\":0,\\\"id\\\":106,\\\"maxCapacity\\\":20,\\\"maxRows\\\":10,\\\"status\\\":\\\"空置\\\",\\\"warehouseId\\\":\\\"106\\\",\\\"warehouseName\\\":\\\"3\\\"}\"');
INSERT INTO `operation_log` VALUES (144, '库位', 'UPDATE', 'i', '2025-03-21 15:48:44', '\"{}\"', '\"{\\\"createdAt\\\":[2025,3,18,0,38,14],\\\"curCapacity\\\":0,\\\"id\\\":106,\\\"maxCapacity\\\":20,\\\"maxRows\\\":10,\\\"status\\\":\\\"维护\\\",\\\"warehouseId\\\":\\\"106\\\",\\\"warehouseName\\\":\\\"3\\\"}\"');
INSERT INTO `operation_log` VALUES (145, '化验数据', 'INSERT', 'csc', '2025-03-21 20:03:21', '\"{\\\"colorValue\\\":250.0,\\\"conductivityAsh\\\":0.12,\\\"dryWeight\\\":1.05,\\\"insolubleImpurity\\\":35.0,\\\"phValue\\\":6.8,\\\"reducingSugar\\\":0.55,\\\"sampleDate\\\":[2025,3,19],\\\"sucrose\\\":98.0,\\\"productName\\\":\\\"中冰\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (146, '化验数据', 'INSERT', 'csc', '2025-03-21 20:13:42', '\"{\\\"colorValue\\\":250.0,\\\"conductivityAsh\\\":0.12,\\\"dryWeight\\\":1.05,\\\"insolubleImpurity\\\":35.0,\\\"phValue\\\":6.8,\\\"reducingSugar\\\":0.55,\\\"sampleDate\\\":[2025,3,19],\\\"sucrose\\\":98.0,\\\"productName\\\":\\\"中冰\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (147, '筛网', 'DELETE', 'i', '2025-03-26 17:39:27', '\"{}\"', '\"{\\\"createdAt\\\":[2025,3,4,18,8,13],\\\"createdBy\\\":1,\\\"description\\\":\\\"科比专用筛网：过滤牢大碎片，收集小布丁\\\",\\\"id\\\":9,\\\"meshName\\\":\\\"kobe111\\\",\\\"updatedAt\\\":[2025,3,11,20,43,24],\\\"updatedBy\\\":13}\"');
INSERT INTO `operation_log` VALUES (148, '产品', 'UPDATE', 'i', '2025-04-04 03:43:53', '\"{}\"', '\"{\\\"canStack\\\":false,\\\"createdAt\\\":[2025,2,20,3,36,33],\\\"createdBy\\\":1,\\\"id\\\":36,\\\"packagingMethod\\\":\\\"袋\\\",\\\"piecesPerPallet\\\":1,\\\"productName\\\":\\\"正中冰\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"updatedAt\\\":[2025,3,9,21,10,22],\\\"updatedBy\\\":2,\\\"weightPerPiece\\\":40.00}\"');
INSERT INTO `operation_log` VALUES (149, '产品', 'UPDATE', 'i', '2025-04-04 03:45:43', '\"{}\"', '\"{\\\"canStack\\\":false,\\\"createdAt\\\":[2025,2,20,3,36,33],\\\"createdBy\\\":1,\\\"id\\\":36,\\\"packagingMethod\\\":\\\"袋\\\",\\\"piecesPerPallet\\\":1,\\\"productName\\\":\\\"正中冰\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"updatedAt\\\":[2025,4,4,3,43,53],\\\"updatedBy\\\":13,\\\"weightPerPiece\\\":40.00}\"');
INSERT INTO `operation_log` VALUES (150, '库位', 'DELETE', 'i', '2025-04-04 04:39:15', '\"{}\"', '\"{\\\"createdAt\\\":[2025,3,18,0,38,14],\\\"curCapacity\\\":0,\\\"id\\\":106,\\\"maxCapacity\\\":20,\\\"maxRows\\\":10,\\\"status\\\":\\\"空置\\\",\\\"warehouseId\\\":\\\"106\\\",\\\"warehouseName\\\":\\\"3\\\"}\"');
INSERT INTO `operation_log` VALUES (151, '库位', 'UPDATE', 'i', '2025-04-04 15:03:07', '\"{}\"', '\"{\\\"createdAt\\\":[2025,3,7,21,42,18],\\\"curCapacity\\\":0,\\\"id\\\":102,\\\"maxCapacity\\\":20,\\\"maxRows\\\":10,\\\"status\\\":\\\"空置\\\",\\\"warehouseId\\\":\\\"102\\\",\\\"warehouseName\\\":\\\"102\\\"}\"');
INSERT INTO `operation_log` VALUES (152, '库位', 'UPDATE', 'i', '2025-04-04 15:03:19', '\"{}\"', '\"{\\\"createdAt\\\":[2025,3,7,21,42,18],\\\"curCapacity\\\":0,\\\"id\\\":102,\\\"maxCapacity\\\":20,\\\"maxRows\\\":10,\\\"status\\\":\\\"维护\\\",\\\"warehouseId\\\":\\\"102\\\",\\\"warehouseName\\\":\\\"102\\\"}\"');
INSERT INTO `operation_log` VALUES (153, '化验数据', 'INSERT', 'zmy', '2025-04-05 20:37:41', '\"{\\\"colorValue\\\":200,\\\"conductivityAsh\\\":null,\\\"dryWeight\\\":null,\\\"insolubleImpurity\\\":null,\\\"phValue\\\":null,\\\"reducingSugar\\\":null,\\\"sampleDate\\\":[2025,4,5],\\\"sucrose\\\":null,\\\"productName\\\":\\\"中冰\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (154, '化验数据', 'INSERT', 'i', '2025-04-05 22:45:27', '\"{}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (695, '检验标准', 'INSERT', 'csc', '2025-07-03 14:00:22', '\"{\\\"productType\\\":\\\"黄冰糖\\\",\\\"standardName\\\":\\\"黄冰糖\\\",\\\"colorMin\\\":1,\\\"colorMax\\\":100,\\\"reducingSugarMin\\\":1,\\\"reducingSugarMax\\\":100,\\\"dryWeightMin\\\":1,\\\"dryWeightMax\\\":100,\\\"conductivityAshMin\\\":1,\\\"conductivityAshMax\\\":100,\\\"sucroseMin\\\":1,\\\"sucroseMax\\\":100,\\\"insolubleImpurityMax\\\":100,\\\"insolubleImpurityMin\\\":1,\\\"phMin\\\":1,\\\"phMax\\\":100}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (696, '化验数据', 'INSERT', 'csc', '2025-07-03 14:00:48', '\"{\\\"sampleDate\\\":\\\"2025-07-03\\\",\\\"colorValue\\\":2,\\\"reducingSugar\\\":2,\\\"dryWeight\\\":2,\\\"conductivityAsh\\\":2,\\\"sucrose\\\":2,\\\"insolubleImpurity\\\":2,\\\"phValue\\\":2,\\\"productName\\\":\\\"黄中冰\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (697, '产品', 'UPDATE', 'csc', '2025-07-03 17:33:36', '\"{\\\"piecesPerPallet\\\":10}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":1,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (698, '产品', 'UPDATE', 'csc', '2025-07-03 17:34:11', '\"{\\\"piecesPerPallet\\\":20}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":10,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (699, '产品', 'UPDATE', 'csc', '2025-07-03 17:34:32', '\"{\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":1,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (700, '化验数据', 'INSERT', 'csc', '2025-07-03 22:42:22', '\"{\\\"sampleDate\\\":\\\"2025-07-03\\\",\\\"colorValue\\\":10,\\\"reducingSugar\\\":10,\\\"dryWeight\\\":10,\\\"conductivityAsh\\\":10,\\\"sucrose\\\":10,\\\"insolubleImpurity\\\":10,\\\"phValue\\\":10,\\\"productName\\\":\\\"黄碎冰白糖袋\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (701, '产品', 'DELETE', 'csc', '2025-07-04 11:49:15', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (702, '产品', 'DELETE', 'csc', '2025-07-04 11:50:40', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (703, '产品', 'DELETE', 'csc', '2025-07-04 11:51:27', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (704, '产品', 'DELETE', 'csc', '2025-07-04 11:51:43', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (705, '产品', 'DELETE', 'csc', '2025-07-04 11:52:17', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (706, '产品', 'DELETE', 'csc', '2025-07-04 11:53:53', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (707, '产品', 'DELETE', 'csc', '2025-07-04 11:55:49', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (708, '产品', 'DELETE', 'csc', '2025-07-04 11:59:11', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (709, '产品', 'DELETE', 'csc', '2025-07-04 12:02:39', '\"{}\"', '\"{\\\"id\\\":46,\\\"productName\\\":\\\"糖罐子\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":null,\\\"weightPerPiece\\\":6.14,\\\"piecesPerPallet\\\":1,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (710, '产品', 'DELETE', 'csc', '2025-07-04 12:02:49', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (711, '产品', 'DELETE', 'csc', '2025-07-04 12:03:13', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (712, '产品', 'DELETE', 'csc', '2025-07-04 12:04:48', '\"{}\"', '\"{\\\"id\\\":40,\\\"productName\\\":\\\"纯白浮冰\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":1,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (713, '产品', 'DELETE', 'csc', '2025-07-04 12:04:54', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (714, '产品', 'DELETE', 'csc', '2025-07-04 12:04:58', '\"{}\"', '\"{\\\"id\\\":43,\\\"productName\\\":\\\"正翻砂\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":1,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (715, '产品', 'DELETE', 'csc', '2025-07-04 12:08:10', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (716, '产品', 'DELETE', 'csc', '2025-07-04 12:08:21', '\"{}\"', '\"{\\\"id\\\":45,\\\"productName\\\":\\\"纯白中冰\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":null,\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":1,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (717, '产品', 'DELETE', 'csc', '2025-07-04 12:08:24', '\"{}\"', '\"{\\\"id\\\":46,\\\"productName\\\":\\\"糖罐子\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":null,\\\"weightPerPiece\\\":6.14,\\\"piecesPerPallet\\\":1,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (718, '产品', 'DELETE', 'csc', '2025-07-04 12:08:27', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (719, '产品', 'DELETE', 'csc', '2025-07-04 12:08:30', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (720, '产品', 'DELETE', 'csc', '2025-07-04 12:08:42', '\"{}\"', '\"{\\\"id\\\":42,\\\"productName\\\":\\\"纯白小颗粒\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":1,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (721, '产品', 'DELETE', 'csc', '2025-07-04 12:08:53', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (722, '产品', 'DELETE', 'csc', '2025-07-04 13:43:58', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (723, '化验数据', 'INSERT', 'csc', '2025-07-04 13:47:38', '\"{\\\"sampleDate\\\":\\\"2025-07-04\\\",\\\"colorValue\\\":10,\\\"reducingSugar\\\":10,\\\"dryWeight\\\":10,\\\"conductivityAsh\\\":10,\\\"sucrose\\\":10,\\\"insolubleImpurity\\\":10,\\\"phValue\\\":10,\\\"productName\\\":\\\"黄冰糖小粒\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (724, '产品', 'DELETE', 'csc', '2025-07-04 13:48:09', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (725, '产品', 'DELETE', 'csc', '2025-07-04 21:12:27', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (726, '产品', 'DELETE', 'csc', '2025-07-04 21:45:04', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (727, '产品', 'DELETE', 'csc', '2025-07-04 21:45:26', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (728, '产品', 'DELETE', 'csc', '2025-07-04 21:54:26', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (729, '产品', 'DELETE', 'csc', '2025-07-04 21:54:44', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (730, '产品', 'DELETE', 'csc', '2025-07-04 21:57:46', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (731, '产品', 'DELETE', 'csc', '2025-07-04 22:00:54', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (732, '产品', 'DELETE', 'csc', '2025-07-04 22:03:20', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (733, '产品', 'DELETE', 'csc', '2025-07-04 22:04:31', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (734, '产品', 'DELETE', 'csc', '2025-07-06 20:56:30', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (735, '产品', 'DELETE', 'csc', '2025-07-06 20:56:48', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (736, '产品', 'DELETE', 'csc', '2025-07-06 21:05:43', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (737, '产品', 'DELETE', 'csc', '2025-07-06 21:29:17', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (738, '产品', 'DELETE', 'csc', '2025-07-06 21:37:13', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (739, '产品', 'DELETE', 'csc', '2025-07-06 22:02:36', '\"{}\"', '\"{\\\"id\\\":40,\\\"productName\\\":\\\"纯白浮冰\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":1,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (740, '产品', 'DELETE', 'csc', '2025-07-06 22:02:42', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (741, '产品', 'DELETE', 'csc', '2025-07-07 12:07:13', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (742, '产品', 'DELETE', 'csc', '2025-07-07 12:07:33', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (743, '产品', 'DELETE', 'csc', '2025-07-08 13:44:43', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (744, '产品', 'DELETE', 'csc', '2025-07-08 13:48:37', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (745, '产品', 'DELETE', 'csc', '2025-07-08 13:48:42', '\"{}\"', '\"{\\\"id\\\":36,\\\"productName\\\":\\\"正中冰\\\",\\\"productType\\\":\\\"白冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":1,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (746, '产品', 'DELETE', 'csc', '2025-07-08 13:50:35', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (747, '产品', 'DELETE', 'csc', '2025-07-08 13:59:12', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (748, '产品', 'DELETE', 'csc', '2025-07-08 13:59:57', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (749, '产品', 'DELETE', 'csc', '2025-07-08 14:00:11', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (750, '产品', 'DELETE', 'csc', '2025-07-08 16:27:28', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (751, '产品', 'DELETE', 'csc', '2025-07-08 16:29:41', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (752, '产品', 'DELETE', 'csc', '2025-07-08 16:36:27', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (753, '产品', 'DELETE', 'csc', '2025-07-08 16:39:07', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (754, '产品', 'DELETE', 'csc', '2025-07-08 16:41:25', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (755, '产品', 'DELETE', 'csc', '2025-07-08 16:43:31', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (756, '产品', 'DELETE', 'csc', '2025-07-08 16:47:18', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (757, '产品', 'DELETE', 'csc', '2025-07-08 17:45:53', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (758, '产品', 'DELETE', 'csc', '2025-07-09 17:19:37', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (759, '产品', 'DELETE', 'csc', '2025-07-09 17:19:50', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (760, '产品', 'DELETE', 'csc', '2025-07-09 17:22:55', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (761, '产品', 'DELETE', 'csc', '2025-07-09 17:25:37', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (762, '产品', 'DELETE', 'csc', '2025-07-09 17:26:42', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (763, '产品', 'DELETE', 'csc', '2025-07-09 17:26:53', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (764, '产品', 'DELETE', 'csc', '2025-07-09 17:26:57', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (765, '产品', 'DELETE', 'csc', '2025-07-09 19:54:01', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (766, '产品', 'DELETE', 'csc', '2025-07-09 19:54:51', '\"{}\"', '\"{\\\"id\\\":49,\\\"productName\\\":\\\"黄浮冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":1,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (767, '产品', 'DELETE', 'csc', '2025-07-09 19:55:00', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (768, '产品', 'DELETE', 'csc', '2025-07-09 20:04:55', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (769, '产品', 'DELETE', 'csc', '2025-07-09 20:10:10', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (770, '产品', 'DELETE', 'csc', '2025-07-09 20:10:30', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (771, '化验数据', 'INSERT', 'csc', '2025-07-09 20:15:23', '\"{\\\"sampleDate\\\":\\\"2025-07-09\\\",\\\"colorValue\\\":9,\\\"reducingSugar\\\":9,\\\"dryWeight\\\":9,\\\"conductivityAsh\\\":9,\\\"sucrose\\\":9,\\\"insolubleImpurity\\\":9,\\\"phValue\\\":9,\\\"productName\\\":\\\"黄中冰\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (772, '化验数据', 'INSERT', 'csc', '2025-07-10 16:04:27', '\"{\\\"sampleDate\\\":\\\"2025-07-10\\\",\\\"colorValue\\\":11,\\\"reducingSugar\\\":11,\\\"dryWeight\\\":11,\\\"conductivityAsh\\\":11,\\\"sucrose\\\":11,\\\"insolubleImpurity\\\":11,\\\"phValue\\\":11,\\\"productName\\\":\\\"黄中冰\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (773, '产品', 'DELETE', 'csc', '2025-07-10 16:05:26', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (774, '产品', 'DELETE', 'csc', '2025-07-10 16:12:42', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (775, '产品', 'DELETE', 'csc', '2025-07-10 16:14:21', '\"{}\"', '\"{\\\"id\\\":50,\\\"productName\\\":\\\"黄小颗粒\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":1,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (776, '产品', 'DELETE', 'csc', '2025-07-10 16:14:30', '\"{}\"', '\"{\\\"id\\\":52,\\\"productName\\\":\\\"黄中颗粒\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":null,\\\"weightPerPiece\\\":30.00,\\\"piecesPerPallet\\\":1,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (777, '产品', 'DELETE', 'csc', '2025-07-10 16:14:38', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (778, '产品', 'DELETE', 'csc', '2025-07-10 16:19:37', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (779, '产品', 'DELETE', 'csc', '2025-07-10 16:20:30', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (780, '产品', 'DELETE', 'csc', '2025-07-10 16:24:44', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (781, '产品', 'DELETE', '肖柳航', '2025-07-10 16:28:47', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (782, '产品', 'DELETE', 'i', '2025-07-10 16:31:48', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (783, '产品', 'DELETE', 'csc', '2025-07-10 16:37:53', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (784, '产品', 'DELETE', 'csc', '2025-07-10 18:17:13', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (785, '产品', 'DELETE', 'csc', '2025-07-11 16:13:37', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (786, '产品', 'DELETE', 'csc', '2025-07-11 16:14:30', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (787, '产品', 'DELETE', 'csc', '2025-07-11 18:26:00', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (788, '产品', 'DELETE', 'csc', '2025-07-11 19:52:17', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (789, '产品', 'UPDATE', 'csc', '2025-07-15 20:55:22', '\"{\\\"piecesPerPallet\\\":10}\"', '\"{\\\"id\\\":68,\\\"productName\\\":\\\"黄中冰净\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"成品\\\",\\\"packagingMethod\\\":\\\"箱\\\",\\\"weightPerPiece\\\":15.00,\\\"piecesPerPallet\\\":1,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (790, '产品', 'DELETE', 'csc', '2025-07-15 21:30:06', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (791, '产品', 'DELETE', 'csc', '2025-07-15 21:32:03', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (792, '产品', 'DELETE', 'csc', '2025-07-15 21:32:54', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (793, '产品', 'DELETE', 'csc', '2025-07-15 21:33:38', '\"{}\"', '\"{\\\"id\\\":48,\\\"productName\\\":\\\"黄碎冰白糖袋\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":true}\"');
INSERT INTO `operation_log` VALUES (794, '化验数据', 'INSERT', '肖柳航', '2025-07-16 16:17:30', '\"{\\\"sampleDate\\\":\\\"2025-07-16\\\",\\\"colorValue\\\":1,\\\"reducingSugar\\\":1,\\\"dryWeight\\\":1,\\\"conductivityAsh\\\":1,\\\"sucrose\\\":1,\\\"insolubleImpurity\\\":1,\\\"phValue\\\":1,\\\"productName\\\":\\\"黄中冰\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (795, '产品', 'DELETE', '肖柳航', '2025-07-16 16:19:24', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');
INSERT INTO `operation_log` VALUES (796, '产品', 'DELETE', '肖柳航', '2025-07-16 18:02:57', '\"{}\"', '\"{\\\"id\\\":47,\\\"productName\\\":\\\"黄中冰\\\",\\\"productType\\\":\\\"黄冰糖\\\",\\\"status\\\":\\\"半成品\\\",\\\"packagingMethod\\\":\\\"袋\\\",\\\"weightPerPiece\\\":40.00,\\\"piecesPerPallet\\\":20,\\\"canStack\\\":false}\"');

-- ----------------------------
-- Table structure for out_stock
-- ----------------------------
DROP TABLE IF EXISTS `out_stock`;
CREATE TABLE `out_stock`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '出库ID',
  `warehouse_id` int NOT NULL COMMENT '库位ID',
  `product_id` int NOT NULL COMMENT '产品ID',
  `quantity` int NOT NULL COMMENT '出库数量（板）',
  `pieces` int NULL DEFAULT NULL COMMENT '出库散件数量',
  `in_date` date NOT NULL COMMENT '生产日期（入库日期）',
  `out_date` date NOT NULL COMMENT '出库时间',
  `operator_id` int NOT NULL COMMENT '操作员ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `total_weight` decimal(10, 2) NULL DEFAULT NULL COMMENT '总重量',
  `assay_id` int NULL DEFAULT NULL COMMENT '化验记录id\r\n',
  `unit` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '出库单位：0整版出库，1散件出库',
  `out_type` int NULL DEFAULT NULL COMMENT '出库类型：0整版优先，1散件优先',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_warehouse_product`(`warehouse_id`, `product_id`) USING BTREE,
  INDEX `operator_id`(`operator_id`) USING BTREE,
  INDEX `idx_outstock_product`(`product_id`, `in_date`) USING BTREE,
  INDEX `idx_outstock`(`product_id`, `warehouse_id`) USING BTREE,
  INDEX `assay_id`(`assay_id`) USING BTREE,
  CONSTRAINT `out_stock_ibfk_1` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `out_stock_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `out_stock_ibfk_3` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `out_stock_ibfk_4` FOREIGN KEY (`assay_id`) REFERENCES `assay` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 32 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '出库记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of out_stock
-- ----------------------------
INSERT INTO `out_stock` VALUES (6, 1, 47, 4, 20, '2025-07-03', '2025-07-09', 2, '2025-07-09 17:35:26', 3200.00, 319, '0', 1);
INSERT INTO `out_stock` VALUES (7, 1, 47, 0, 5, '2025-07-03', '2025-07-09', 2, '2025-07-09 17:35:26', 200.00, 319, '1', 1);
INSERT INTO `out_stock` VALUES (8, 1, 47, 1, 5, '2025-07-03', '2025-07-09', 2, '2025-07-09 17:40:46', 200.00, 319, '1', 0);
INSERT INTO `out_stock` VALUES (9, 1, 47, 2, 0, '2025-07-03', '2025-07-09', 2, '2025-07-09 17:55:15', 1600.00, 319, '0', 0);
INSERT INTO `out_stock` VALUES (10, 1, 47, 0, 20, '2025-07-03', '2025-07-09', 2, '2025-07-09 18:02:09', 800.00, 319, '1', 1);
INSERT INTO `out_stock` VALUES (11, 1, 48, 9, 20, '2025-07-03', '2025-07-09', 2, '2025-07-09 19:48:19', 7200.00, 320, '0', 1);
INSERT INTO `out_stock` VALUES (12, 1, 48, 0, 10, '2025-07-03', '2025-07-09', 2, '2025-07-09 19:49:16', 400.00, 320, '1', 1);
INSERT INTO `out_stock` VALUES (13, 1, 48, 0, 10, '2025-07-03', '2025-07-09', 2, '2025-07-09 19:50:03', 400.00, 320, '1', 1);
INSERT INTO `out_stock` VALUES (14, 1, 47, 0, 10, '2025-07-03', '2025-07-09', 2, '2025-07-09 19:51:31', 400.00, 319, '1', 1);
INSERT INTO `out_stock` VALUES (15, 1, 47, 0, 20, '2025-07-03', '2025-07-09', 2, '2025-07-09 19:52:19', 800.00, 319, '1', 1);
INSERT INTO `out_stock` VALUES (16, 1, 47, 0, 10, '2025-07-03', '2025-07-09', 2, '2025-07-09 20:10:50', 400.00, 319, '1', 1);
INSERT INTO `out_stock` VALUES (17, 1, 48, 2, 0, '2025-07-03', '2025-07-09', 2, '2025-07-09 20:10:51', 1600.00, 320, '0', 1);
INSERT INTO `out_stock` VALUES (18, 13, 47, 20, 0, '2025-07-10', '2025-07-10', 37, '2025-07-10 16:29:05', 16000.00, 327, '0', 1);
INSERT INTO `out_stock` VALUES (19, 64, 47, 0, 50, '2025-07-10', '2025-07-10', 13, '2025-07-10 16:32:05', 2000.00, 327, '1', 1);
INSERT INTO `out_stock` VALUES (21, 5, 66, 5, 0, '2025-07-04', '2025-07-10', 37, '2025-07-10 16:45:12', 125.00, 325, '0', 0);
INSERT INTO `out_stock` VALUES (22, 1, 47, 5, 0, '2025-07-03', '2025-07-10', 2, '2025-07-10 18:17:27', 4000.00, 319, '0', 1);
INSERT INTO `out_stock` VALUES (23, 1, 47, 12, 0, '2025-07-03', '2025-07-11', 2, '2025-07-11 16:14:42', 9600.00, 319, '0', 1);
INSERT INTO `out_stock` VALUES (24, 12, 47, 2, 0, '2025-07-10', '2025-07-11', 2, '2025-07-11 19:52:31', 1600.00, 327, '0', 1);
INSERT INTO `out_stock` VALUES (25, 1, 48, 8, 0, '2025-07-03', '2025-07-15', 2, '2025-07-15 21:30:56', 6400.00, 320, '0', 1);
INSERT INTO `out_stock` VALUES (26, 2, 48, 0, 5, '2025-07-03', '2025-07-15', 2, '2025-07-15 21:34:00', 200.00, 320, '1', 1);
INSERT INTO `out_stock` VALUES (27, 18, 47, 0, 150, '2025-07-16', '2025-07-16', 37, '2025-07-16 16:19:38', 6000.00, 340, '1', 1);
INSERT INTO `out_stock` VALUES (28, 4, 47, 0, 40, '2025-07-03', '2025-07-16', 37, '2025-07-16 18:17:59', 1600.00, 319, '1', 0);
INSERT INTO `out_stock` VALUES (29, 4, 47, 0, 10, '2025-07-09', '2025-07-16', 37, '2025-07-16 18:17:59', 400.00, 326, '1', 0);

-- ----------------------------
-- Table structure for permission
-- ----------------------------
DROP TABLE IF EXISTS `permission`;
CREATE TABLE `permission`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `perm_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '权限代码',
  `perm_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '权限名称',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权限描述',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `permission_code`(`perm_code`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 23 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of permission
-- ----------------------------
INSERT INTO `permission` VALUES (1, 'system:access', '系统访问', '基础系统访问权限', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (2, 'log:view', '查看日志', '查看系统操作日志', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (3, 'user:create', '创建用户', '创建新用户账号', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (4, 'user:update', '修改用户', '修改用户信息', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (5, 'user:delete', '删除用户', '删除用户账号', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (6, 'user:role', '分配角色', '为用户分配角色', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (7, 'warehouse:create', '新建仓库', '创建新仓库记录', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (8, 'warehouse:update', '修改仓库', '编辑仓库信息', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (9, 'warehouse:delete', '删除仓库', '删除仓库记录', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (10, 'product:create', '新增产品', '创建新产品', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (11, 'product:update', '修改产品', '修改产品详细信息', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (12, 'product:delete', '删除产品', '删除产品', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (13, 'product:query', '产品查询', '查询产品详细信息', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (14, 'quality:test', '质量检测', '提交质量检测数据', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (15, 'quality:approve', '质量审批', '审批产品质量状态', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (16, 'report:generate', '生成报表', '生成各类统计报表', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (17, 'report:export', '导出数据', '导出报表数据文件', '2025-02-22 02:33:40');
INSERT INTO `permission` VALUES (18, 'record:create', '产品入库', '新增产品入库记录', '2025-02-22 05:18:12');
INSERT INTO `permission` VALUES (19, 'record:update', '修改记录', '修改产品库存信息', '2025-02-22 05:18:12');
INSERT INTO `permission` VALUES (20, 'record:delete', '删除记录', '删除产品库存记录', '2025-02-22 05:18:12');
INSERT INTO `permission` VALUES (21, 'record:query', '库存查询', '查询库存记录详情', '2025-02-22 05:18:12');
INSERT INTO `permission` VALUES (22, 'stock:update', '入库修改', '修改入库记录信息', '2025-02-25 20:43:47');

-- ----------------------------
-- Table structure for product
-- ----------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '产品ID',
  `product_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '产品名称',
  `product_type` enum('黄冰糖','白冰糖') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '产品种类',
  `status` enum('半成品','成品') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '产品状态',
  `packaging_method` enum('箱','袋','罐') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '包装方式',
  `weight_per_piece` decimal(10, 2) NOT NULL COMMENT '单件重量（kg）',
  `created_by` int NULL DEFAULT NULL COMMENT '创建者',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` int NULL DEFAULT NULL COMMENT '修改者',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `pieces_per_pallet` int NOT NULL DEFAULT 25 COMMENT '一板产品数量',
  `can_stack` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否可堆积',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `created_by`(`created_by`) USING BTREE,
  CONSTRAINT `product_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 126 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of product
-- ----------------------------
INSERT INTO `product` VALUES (36, '正中冰', '白冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', 13, '2025-04-04 03:45:43', 1, 0);
INSERT INTO `product` VALUES (37, '浮冰', '白冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (38, '正碎冰', '白冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (39, '纯白碎冰', '白冰糖', '半成品', '袋', 25.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (40, '纯白浮冰', '白冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (41, '正中小颗粒', '白冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (42, '纯白小颗粒', '白冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (43, '正翻砂', '白冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (44, '正中颗粒', '白冰糖', '半成品', NULL, 25.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (45, '纯白中冰', '白冰糖', '半成品', NULL, 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (46, '糖罐子', '白冰糖', '半成品', NULL, 6.14, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (47, '黄中冰', '黄冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', 2, '2025-07-03 17:34:10', 20, 0);
INSERT INTO `product` VALUES (48, '黄碎冰白糖袋', '黄冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', 2, '2025-07-03 17:34:32', 20, 1);
INSERT INTO `product` VALUES (49, '黄浮冰', '黄冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (50, '黄小颗粒', '黄冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (51, '黄翻砂', '黄冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (52, '黄中颗粒', '黄冰糖', '半成品', NULL, 30.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (53, '黄碎冰', '黄冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (54, '深黄中冰', '黄冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (55, '浅色黄中冰', '黄冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (56, '深黄翻砂', '黄冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (57, '中冰', '白冰糖', '成品', '箱', 14.50, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (58, '中冰', '白冰糖', '成品', '袋', 15.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (59, '中冰', '白冰糖', '成品', '箱', 10.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (60, '碎冰', '白冰糖', '成品', '袋', 15.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (61, '多晶冰糖净', '白冰糖', '成品', '箱', 15.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (62, '怡宝成品', '白冰糖', '成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (63, '浮冰', '白冰糖', '成品', '袋', 15.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (64, '多晶冰糖', '白冰糖', '成品', '袋', 25.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (65, '罐装糖', '白冰糖', '成品', NULL, 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (66, '黄冰糖小粒', '黄冰糖', '成品', '袋', 25.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (67, '黄冰糖净', '黄冰糖', '成品', '箱', 15.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (68, '黄中冰净', '黄冰糖', '成品', '箱', 15.00, 1, '2025-02-20 03:36:33', 2, '2025-07-15 20:55:22', 10, 0);
INSERT INTO `product` VALUES (69, '黄冰糖小粒净', '黄冰糖', '成品', '箱', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (70, '黄浮冰', '黄冰糖', '成品', '袋', 15.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (72, '测试产品kobe', '白冰糖', '成品', '箱', 40.00, 1, '2025-03-04 14:29:56', 13, '2025-03-18 00:38:37', 30, 0);

-- ----------------------------
-- Table structure for quality_standards
-- ----------------------------
DROP TABLE IF EXISTS `quality_standards`;
CREATE TABLE `quality_standards`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `product_type` enum('黄冰糖','白冰糖') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `standard_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标准名称（如中粮优级）',
  `color_min` decimal(10, 2) NULL DEFAULT NULL COMMENT '色值下限',
  `color_max` decimal(10, 2) NULL DEFAULT NULL COMMENT '色值上限',
  `reducing_sugar_min` decimal(10, 2) NULL DEFAULT NULL COMMENT '还原糖分下限',
  `reducing_sugar_max` decimal(10, 2) NULL DEFAULT NULL COMMENT '还原糖分上限',
  `dry_weight_min` decimal(10, 2) NULL DEFAULT NULL COMMENT '干燥失重下限',
  `dry_weight_max` decimal(10, 2) NULL DEFAULT NULL COMMENT '干燥失重上限',
  `conductivity_ash_min` decimal(10, 2) NULL DEFAULT NULL COMMENT '电导灰分下限',
  `conductivity_ash_max` decimal(10, 2) NULL DEFAULT NULL COMMENT '电导灰分上限',
  `sucrose_min` decimal(10, 2) NULL DEFAULT NULL COMMENT '蔗糖分下限',
  `sucrose_max` decimal(10, 2) NULL DEFAULT NULL COMMENT '蔗糖分上限',
  `insoluble_impurity_min` decimal(10, 2) NULL DEFAULT NULL COMMENT '不溶于水杂质下限',
  `insoluble_impurity_max` decimal(10, 2) NULL DEFAULT NULL COMMENT '不溶于水杂质上限',
  `ph_min` decimal(10, 2) NULL DEFAULT NULL COMMENT 'pH下限',
  `ph_max` decimal(10, 2) NULL DEFAULT NULL COMMENT 'pH上限',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `product_type`(`product_type`, `standard_name`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of quality_standards
-- ----------------------------
INSERT INTO `quality_standards` VALUES (7, '黄冰糖', '黄冰糖', 1.00, 100.00, 1.00, 100.00, 1.00, 100.00, 1.00, 100.00, 1.00, 100.00, 1.00, 100.00, 1.00, 100.00, '2025-07-03 14:00:22', '2025-07-03 14:00:22');

-- ----------------------------
-- Table structure for role
-- ----------------------------
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色名称',
  `role_code` enum('ADMIN','QC','STAFF') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色代码',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '角色描述',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `role_code`(`role_code`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of role
-- ----------------------------
INSERT INTO `role` VALUES (1, '老板', 'ADMIN', '拥有全部权限以及能够授予或修改所有用户的权限。', '2025-02-20 03:35:41');
INSERT INTO `role` VALUES (2, '管理员', 'QC', '拥有除管理用户外的全部权限', '2025-02-21 05:46:35');
INSERT INTO `role` VALUES (3, '普通员工', 'STAFF', '仅可录入、查看或有限次修改与自身相关的记录', '2025-02-21 05:46:35');

-- ----------------------------
-- Table structure for role_permission
-- ----------------------------
DROP TABLE IF EXISTS `role_permission`;
CREATE TABLE `role_permission`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `role_id` int NOT NULL COMMENT '角色ID',
  `permission_id` int NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniq_role_permission`(`role_id`, `permission_id`) USING BTREE,
  INDEX `permission_id`(`permission_id`) USING BTREE,
  CONSTRAINT `role_permission_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `role_permission_ibfk_2` FOREIGN KEY (`permission_id`) REFERENCES `permission` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 78 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of role_permission
-- ----------------------------
INSERT INTO `role_permission` VALUES (24, 1, 1);
INSERT INTO `role_permission` VALUES (2, 1, 2);
INSERT INTO `role_permission` VALUES (25, 1, 3);
INSERT INTO `role_permission` VALUES (28, 1, 4);
INSERT INTO `role_permission` VALUES (26, 1, 5);
INSERT INTO `role_permission` VALUES (27, 1, 6);
INSERT INTO `role_permission` VALUES (30, 1, 7);
INSERT INTO `role_permission` VALUES (34, 1, 8);
INSERT INTO `role_permission` VALUES (32, 1, 9);
INSERT INTO `role_permission` VALUES (5, 1, 10);
INSERT INTO `role_permission` VALUES (12, 1, 11);
INSERT INTO `role_permission` VALUES (7, 1, 12);
INSERT INTO `role_permission` VALUES (10, 1, 13);
INSERT INTO `role_permission` VALUES (17, 1, 14);
INSERT INTO `role_permission` VALUES (14, 1, 15);
INSERT INTO `role_permission` VALUES (21, 1, 16);
INSERT INTO `role_permission` VALUES (19, 1, 17);
INSERT INTO `role_permission` VALUES (65, 1, 18);
INSERT INTO `role_permission` VALUES (66, 1, 19);
INSERT INTO `role_permission` VALUES (67, 1, 20);
INSERT INTO `role_permission` VALUES (68, 1, 21);
INSERT INTO `role_permission` VALUES (75, 1, 22);
INSERT INTO `role_permission` VALUES (23, 2, 1);
INSERT INTO `role_permission` VALUES (1, 2, 2);
INSERT INTO `role_permission` VALUES (29, 2, 7);
INSERT INTO `role_permission` VALUES (33, 2, 8);
INSERT INTO `role_permission` VALUES (31, 2, 9);
INSERT INTO `role_permission` VALUES (4, 2, 10);
INSERT INTO `role_permission` VALUES (11, 2, 11);
INSERT INTO `role_permission` VALUES (6, 2, 12);
INSERT INTO `role_permission` VALUES (9, 2, 13);
INSERT INTO `role_permission` VALUES (16, 2, 14);
INSERT INTO `role_permission` VALUES (13, 2, 15);
INSERT INTO `role_permission` VALUES (20, 2, 16);
INSERT INTO `role_permission` VALUES (18, 2, 17);
INSERT INTO `role_permission` VALUES (69, 2, 18);
INSERT INTO `role_permission` VALUES (70, 2, 19);
INSERT INTO `role_permission` VALUES (71, 2, 20);
INSERT INTO `role_permission` VALUES (72, 2, 21);
INSERT INTO `role_permission` VALUES (76, 2, 22);
INSERT INTO `role_permission` VALUES (22, 3, 1);
INSERT INTO `role_permission` VALUES (8, 3, 13);
INSERT INTO `role_permission` VALUES (74, 3, 19);
INSERT INTO `role_permission` VALUES (15, 3, 21);

-- ----------------------------
-- Table structure for screen_mesh
-- ----------------------------
DROP TABLE IF EXISTS `screen_mesh`;
CREATE TABLE `screen_mesh`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `mesh_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '筛网类型',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '筛网描述',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` int NOT NULL,
  `updated_at` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` int NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 23 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of screen_mesh
-- ----------------------------
INSERT INTO `screen_mesh` VALUES (1, 'Test', 'SSSSS', '2025-02-28 15:35:53', 1, '2025-03-11 20:37:37', 13);
INSERT INTO `screen_mesh` VALUES (2, 'TEST2', NULL, '2025-02-28 17:04:28', 1, NULL, NULL);
INSERT INTO `screen_mesh` VALUES (3, 'tes369', NULL, '2025-03-04 14:43:51', 1, '2025-03-11 20:43:49', 13);

-- ----------------------------
-- Table structure for semi_product_record
-- ----------------------------
DROP TABLE IF EXISTS `semi_product_record`;
CREATE TABLE `semi_product_record`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `product_id` int NOT NULL COMMENT '产品ID',
  `quantity` int NOT NULL COMMENT '数量',
  `operation_date` date NOT NULL COMMENT '操作日期',
  `operator` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作员',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `total_weight` decimal(10, 2) NULL DEFAULT NULL COMMENT '总重量',
  `warehouse_id` int NOT NULL COMMENT '库位号',
  `assay_id` int NOT NULL COMMENT '化验记录id',
  `screen_mesh_id` int NOT NULL COMMENT '筛网id',
  `unit` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '单位（0板，1件）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `product_id`(`product_id`) USING BTREE,
  INDEX `semi_product_record_FK`(`assay_id`) USING BTREE,
  INDEX `semi_product_record_FK_1`(`warehouse_id`) USING BTREE,
  CONSTRAINT `semi_product_record_FK` FOREIGN KEY (`assay_id`) REFERENCES `assay` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `semi_product_record_FK_1` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `semi_product_record_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 37 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of semi_product_record
-- ----------------------------
INSERT INTO `semi_product_record` VALUES (1, 47, 10, '2025-07-03', 'csc', '2025-07-09 17:17:00', 8000.00, 1, 319, 1, '0');
INSERT INTO `semi_product_record` VALUES (2, 48, 10, '2025-07-03', 'csc', '2025-07-09 17:17:50', 400.00, 1, 320, 1, '1');
INSERT INTO `semi_product_record` VALUES (3, 47, 10, '2025-07-03', 'csc', '2025-07-09 17:34:55', 400.00, 1, 319, 1, '1');
INSERT INTO `semi_product_record` VALUES (4, 47, 10, '2025-07-03', 'csc', '2025-07-09 18:07:15', 8000.00, 2, 319, 1, '0');
INSERT INTO `semi_product_record` VALUES (5, 47, 2, '2025-07-03', 'csc', '2025-07-09 18:08:00', 1600.00, 1, 319, 1, '0');
INSERT INTO `semi_product_record` VALUES (6, 47, 2, '2025-07-03', 'csc', '2025-07-09 18:19:44', 1600.00, 1, 319, 1, '0');
INSERT INTO `semi_product_record` VALUES (7, 47, 3, '2025-07-03', 'csc', '2025-07-09 19:13:51', 2400.00, 1, 319, 1, '0');
INSERT INTO `semi_product_record` VALUES (8, 47, 3, '2025-07-03', 'csc', '2025-07-09 19:14:25', 2400.00, 1, 319, 1, '0');
INSERT INTO `semi_product_record` VALUES (9, 47, 10, '2025-07-03', 'csc', '2025-07-09 19:18:48', 8000.00, 1, 319, 1, '0');
INSERT INTO `semi_product_record` VALUES (10, 48, 1, '2025-07-03', 'csc', '2025-07-09 19:22:25', 800.00, 1, 320, 1, '0');
INSERT INTO `semi_product_record` VALUES (11, 48, 10, '2025-07-03', 'csc', '2025-07-09 19:23:11', 400.00, 1, 320, 1, '1');
INSERT INTO `semi_product_record` VALUES (12, 48, 50, '2025-07-03', 'csc', '2025-07-09 19:23:38', 2000.00, 1, 320, 1, '1');
INSERT INTO `semi_product_record` VALUES (13, 48, 50, '2025-07-03', 'csc', '2025-07-09 19:44:09', 2000.00, 1, 320, 1, '1');
INSERT INTO `semi_product_record` VALUES (14, 48, 50, '2025-07-03', 'csc', '2025-07-09 19:47:00', 40000.00, 1, 320, 1, '0');
INSERT INTO `semi_product_record` VALUES (15, 48, 10, '2025-07-03', '2', '2025-07-09 19:49:22', 400.00, 2, 320, 1, '1');
INSERT INTO `semi_product_record` VALUES (16, 48, 10, '2025-07-03', '2', '2025-07-09 19:50:04', 400.00, 3, 320, 1, '1');
INSERT INTO `semi_product_record` VALUES (17, 47, 20, '2025-07-03', '2', '2025-07-09 19:52:20', 800.00, 4, 319, 1, '1');
INSERT INTO `semi_product_record` VALUES (18, 47, 10, '2025-07-09', 'csc', '2025-07-09 20:16:11', 400.00, 4, 326, 1, '1');
INSERT INTO `semi_product_record` VALUES (19, 47, 30, '2025-07-03', 'csc', '2025-07-09 20:16:47', 1200.00, 4, 319, 1, '1');
INSERT INTO `semi_product_record` VALUES (20, 47, 20, '2025-07-09', 'csc', '2025-07-09 20:17:32', 800.00, 4, 326, 1, '1');
INSERT INTO `semi_product_record` VALUES (21, 47, 11, '2025-07-10', 'csc', '2025-07-10 16:04:58', 8800.00, 8, 327, 1, '0');
INSERT INTO `semi_product_record` VALUES (22, 47, 11, '2025-07-10', 'csc', '2025-07-10 16:05:00', 8800.00, 8, 327, 1, '0');
INSERT INTO `semi_product_record` VALUES (23, 47, 20, '2025-07-10', 'csc', '2025-07-10 16:18:18', 800.00, 7, 327, 1, '1');
INSERT INTO `semi_product_record` VALUES (24, 47, 10, '2025-07-10', 'csc', '2025-07-10 16:19:13', 400.00, 6, 327, 1, '1');
INSERT INTO `semi_product_record` VALUES (25, 47, 50, '2025-07-10', 'csc', '2025-07-10 16:23:16', 2000.00, 64, 327, 1, '1');
INSERT INTO `semi_product_record` VALUES (26, 47, 20, '2025-07-10', 'csc', '2025-07-10 16:28:36', 16000.00, 13, 327, 1, '0');
INSERT INTO `semi_product_record` VALUES (27, 47, 20, '2025-07-10', 'csc', '2025-07-10 16:37:07', 16000.00, 63, 327, 1, '0');
INSERT INTO `semi_product_record` VALUES (28, 47, 50, '2025-07-10', 'csc', '2025-07-11 17:01:48', 2000.00, 41, 327, 1, '1');
INSERT INTO `semi_product_record` VALUES (29, 47, 5, '2025-07-10', 'csc', '2025-07-11 18:24:43', 4000.00, 12, 327, 1, '0');
INSERT INTO `semi_product_record` VALUES (30, 47, 1, '2025-07-03', 'csc', '2025-07-15 20:33:09', 800.00, 58, 319, 1, '0');
INSERT INTO `semi_product_record` VALUES (31, 47, 400, '2025-07-16', '肖柳航', '2025-07-16 16:17:51', 16000.00, 18, 340, 1, '1');
INSERT INTO `semi_product_record` VALUES (32, 47, 165, '2025-07-16', '肖柳航', '2025-07-16 16:18:46', 6600.00, 19, 340, 1, '1');
INSERT INTO `semi_product_record` VALUES (33, 47, 40, '2025-07-03', '37', '2025-07-16 18:17:59', 1600.00, 64, 319, 1, '1');
INSERT INTO `semi_product_record` VALUES (34, 47, 10, '2025-07-09', '37', '2025-07-16 18:18:00', 400.00, 64, 326, 1, '1');

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '姓名',
  `openid` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '微信OpenID',
  `role_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联角色代码',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `employee_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联工号',
  `bind_status` enum('UNBOUND','WECHAT_BOUND','MANUAL_BOUND') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'UNBOUND',
  `bind_method` enum('WECHAT','MANUAL') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `login_type` enum('WECHAT','WEB') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'WECHAT' COMMENT '登录方式',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `openid`(`openid`) USING BTREE,
  UNIQUE INDEX `employee_id`(`employee_id`) USING BTREE,
  INDEX `role_id`(`role_code`) USING BTREE,
  INDEX `idx_employee`(`employee_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 38 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` VALUES (1, 'i', '', 'STAFF', '2025-02-20 03:36:10', '', 'MANUAL_BOUND', 'MANUAL', 'WECHAT');
INSERT INTO `user` VALUES (2, 'csc', 'ojupe7EqBl_piZh3YcAUD6fxnyBk', 'ADMIN', '2025-03-04 22:09:06', 'EMP001', 'MANUAL_BOUND', 'MANUAL', 'WECHAT');
INSERT INTO `user` VALUES (13, 'i', 'ojupe7Lnq7Rgn9xEW7C4YlbQEpXo', 'ADMIN', '2025-03-11 17:29:30', 'EMP000', 'MANUAL_BOUND', 'MANUAL', 'WECHAT');
INSERT INTO `user` VALUES (14, 'zmy', 'ojupe7OLQPG2Wzssxg9J8cviwLqA', 'ADMIN', '2025-04-05 18:14:45', 'EMP004', 'MANUAL_BOUND', 'MANUAL', 'WECHAT');
INSERT INTO `user` VALUES (37, '肖柳航', 'ojupe7MtU_CE0tvWJ59_oCjN4D5s', 'ADMIN', '2025-07-10 16:27:23', '005', 'MANUAL_BOUND', 'MANUAL', 'WECHAT');

-- ----------------------------
-- Table structure for warehouse
-- ----------------------------
DROP TABLE IF EXISTS `warehouse`;
CREATE TABLE `warehouse`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '仓库ID',
  `status` enum('正常','空置','满仓','维护','临期预警') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '空置' COMMENT '仓库状态',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `max_capacity` int NOT NULL DEFAULT 20 COMMENT '最大容量',
  `cur_capacity` int NOT NULL DEFAULT 0 COMMENT '当前存量',
  `max_rows` int NOT NULL DEFAULT 10 COMMENT '最大存放排数',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '库位名',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1010 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of warehouse
-- ----------------------------
INSERT INTO `warehouse` VALUES (1, '临期预警', '2025-04-08 19:13:12', 40, 3, 10, '1');
INSERT INTO `warehouse` VALUES (2, '临期预警', '2025-04-08 19:13:12', 40, 11, 10, '2');
INSERT INTO `warehouse` VALUES (3, '正常', '2025-04-08 19:13:12', 40, 1, 10, '3');
INSERT INTO `warehouse` VALUES (4, '临期预警', '2025-04-08 19:13:12', 20, 3, 10, '4');
INSERT INTO `warehouse` VALUES (5, '正常', '2025-04-08 19:13:12', 20, 5, 10, '5');
INSERT INTO `warehouse` VALUES (6, '正常', '2025-04-08 19:13:12', 20, 1, 10, '6');
INSERT INTO `warehouse` VALUES (7, '正常', '2025-04-08 19:13:12', 20, 1, 10, '7');
INSERT INTO `warehouse` VALUES (8, '满仓', '2025-04-08 19:13:12', 20, 20, 10, '8');
INSERT INTO `warehouse` VALUES (9, '正常', '2025-04-08 19:13:12', 20, 15, 10, '9');
INSERT INTO `warehouse` VALUES (10, '正常', '2025-04-08 19:13:12', 20, 10, 10, '10');
INSERT INTO `warehouse` VALUES (11, '正常', '2025-04-08 19:13:12', 20, 10, 10, '11');
INSERT INTO `warehouse` VALUES (12, '正常', '2025-04-08 19:13:12', 20, 3, 10, '12');
INSERT INTO `warehouse` VALUES (13, '空置', '2025-04-08 19:13:12', 20, 0, 10, '13');
INSERT INTO `warehouse` VALUES (14, '空置', '2025-04-08 19:13:12', 20, 0, 10, '14');
INSERT INTO `warehouse` VALUES (15, '正常', '2025-04-08 19:13:12', 20, 10, 10, '15');
INSERT INTO `warehouse` VALUES (16, '空置', '2025-04-08 19:13:12', 20, 0, 10, '16');
INSERT INTO `warehouse` VALUES (17, '空置', '2025-04-08 19:13:12', 20, 0, 10, '17');
INSERT INTO `warehouse` VALUES (18, '正常', '2025-04-08 19:13:12', 20, 13, 10, '18');
INSERT INTO `warehouse` VALUES (19, '正常', '2025-04-08 19:13:12', 20, 9, 10, '19');
INSERT INTO `warehouse` VALUES (20, '空置', '2025-04-08 19:13:12', 20, 0, 10, '20');
INSERT INTO `warehouse` VALUES (21, '空置', '2025-04-08 19:13:12', 20, 0, 10, '21');
INSERT INTO `warehouse` VALUES (22, '空置', '2025-04-08 19:13:12', 20, 0, 10, '22');
INSERT INTO `warehouse` VALUES (23, '空置', '2025-04-08 19:13:12', 20, 0, 10, '23');
INSERT INTO `warehouse` VALUES (24, '空置', '2025-04-08 19:13:12', 20, 0, 10, '24');
INSERT INTO `warehouse` VALUES (25, '空置', '2025-04-08 19:13:12', 20, 0, 10, '25');
INSERT INTO `warehouse` VALUES (26, '空置', '2025-04-08 19:13:12', 20, 0, 10, '26');
INSERT INTO `warehouse` VALUES (27, '空置', '2025-04-08 19:13:12', 20, 0, 10, '27');
INSERT INTO `warehouse` VALUES (28, '空置', '2025-04-08 19:13:12', 20, 0, 10, '28');
INSERT INTO `warehouse` VALUES (29, '空置', '2025-04-08 19:13:12', 20, 0, 10, '29');
INSERT INTO `warehouse` VALUES (30, '空置', '2025-04-08 19:13:12', 20, 0, 10, '30');
INSERT INTO `warehouse` VALUES (31, '正常', '2025-04-08 19:13:12', 40, 5, 10, '31');
INSERT INTO `warehouse` VALUES (32, '空置', '2025-04-08 19:13:12', 20, 0, 10, '32');
INSERT INTO `warehouse` VALUES (33, '空置', '2025-04-08 19:13:12', 20, 0, 10, '33');
INSERT INTO `warehouse` VALUES (34, '空置', '2025-04-08 19:13:12', 20, 0, 10, '34');
INSERT INTO `warehouse` VALUES (35, '空置', '2025-04-08 19:13:12', 20, 0, 10, '35');
INSERT INTO `warehouse` VALUES (36, '空置', '2025-04-08 19:13:12', 20, 0, 10, '36');
INSERT INTO `warehouse` VALUES (37, '空置', '2025-04-08 19:13:12', 20, 0, 10, '37');
INSERT INTO `warehouse` VALUES (38, '空置', '2025-04-08 19:13:12', 20, 0, 10, '38');
INSERT INTO `warehouse` VALUES (39, '空置', '2025-04-08 19:13:12', 20, 0, 10, '39');
INSERT INTO `warehouse` VALUES (40, '空置', '2025-04-08 19:13:12', 20, 0, 10, '40');
INSERT INTO `warehouse` VALUES (41, '正常', '2025-04-08 19:13:12', 20, 3, 10, '41');
INSERT INTO `warehouse` VALUES (42, '空置', '2025-04-08 19:13:12', 20, 0, 10, '42');
INSERT INTO `warehouse` VALUES (43, '空置', '2025-04-08 19:13:12', 20, 0, 10, '43');
INSERT INTO `warehouse` VALUES (44, '空置', '2025-04-08 19:13:12', 20, 0, 10, '44');
INSERT INTO `warehouse` VALUES (45, '空置', '2025-04-08 19:13:12', 20, 0, 10, '45');
INSERT INTO `warehouse` VALUES (46, '空置', '2025-04-08 19:13:12', 20, 0, 10, '46');
INSERT INTO `warehouse` VALUES (47, '空置', '2025-04-08 19:13:12', 20, 0, 10, '47');
INSERT INTO `warehouse` VALUES (48, '空置', '2025-04-08 19:13:12', 20, 0, 10, '48');
INSERT INTO `warehouse` VALUES (49, '空置', '2025-04-08 19:13:12', 20, 0, 10, '49');
INSERT INTO `warehouse` VALUES (50, '空置', '2025-04-08 19:13:12', 20, 0, 10, '50');
INSERT INTO `warehouse` VALUES (51, '空置', '2025-04-08 19:13:12', 20, 0, 10, '51');
INSERT INTO `warehouse` VALUES (52, '满仓', '2025-04-08 19:13:12', 20, 20, 10, '52');
INSERT INTO `warehouse` VALUES (53, '正常', '2025-04-08 19:13:12', 20, 14, 10, '53');
INSERT INTO `warehouse` VALUES (54, '空置', '2025-04-08 19:13:12', 20, 0, 10, '54');
INSERT INTO `warehouse` VALUES (55, '正常', '2025-04-08 19:13:12', 20, 5, 10, '55');
INSERT INTO `warehouse` VALUES (56, '空置', '2025-04-08 19:13:12', 20, 0, 10, '56');
INSERT INTO `warehouse` VALUES (57, '正常', '2025-04-08 19:13:12', 20, 10, 10, '57');
INSERT INTO `warehouse` VALUES (58, '临期预警', '2025-04-08 19:13:12', 20, 1, 10, '58');
INSERT INTO `warehouse` VALUES (59, '满仓', '2025-04-08 19:13:12', 20, 20, 10, '59');
INSERT INTO `warehouse` VALUES (60, '满仓', '2025-04-08 19:13:12', 20, 20, 10, '60');
INSERT INTO `warehouse` VALUES (61, '空置', '2025-04-08 19:13:12', 20, 0, 10, '61');
INSERT INTO `warehouse` VALUES (62, '满仓', '2025-04-08 19:13:12', 20, 20, 10, '62');
INSERT INTO `warehouse` VALUES (63, '满仓', '2025-04-08 19:13:12', 20, 20, 10, '63');
INSERT INTO `warehouse` VALUES (64, '临期预警', '2025-04-08 19:13:12', 20, 3, 10, '64');
INSERT INTO `warehouse` VALUES (65, '满仓', '2025-04-08 19:13:12', 20, 20, 10, '65');
INSERT INTO `warehouse` VALUES (66, '满仓', '2025-04-08 19:13:12', 20, 20, 10, '66');
INSERT INTO `warehouse` VALUES (67, '空置', '2025-04-08 19:13:12', 20, 0, 10, '67');

-- ----------------------------
-- View structure for v_warehouse_capacity
-- ----------------------------
DROP VIEW IF EXISTS `v_warehouse_capacity`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `v_warehouse_capacity` AS select `w`.`id` AS `warehouse_id`,`w`.`warehouse_name` AS `warehouse_name`,`w`.`status` AS `status`,`w`.`cur_capacity` AS `cur_capacity`,`w`.`max_capacity` AS `max_capacity`,(`w`.`cur_capacity` / `w`.`max_capacity`) AS `capacity_percentage`,min(`i`.`entry_date`) AS `first_entry_date` from (`warehouse` `w` join `inventory` `i` on((`i`.`warehouse_id` = `w`.`id`))) group by `w`.`id`;

-- ----------------------------
-- View structure for v_warehouse_inventory_summary
-- ----------------------------
DROP VIEW IF EXISTS `v_warehouse_inventory_summary`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `v_warehouse_inventory_summary` AS select `w`.`id` AS `warehouse_id`,`w`.`warehouse_name` AS `warehouse_name`,`p`.`id` AS `product_id`,`p`.`product_name` AS `product_name`,`i`.`entry_date` AS `entry_date`,sum(if((`i`.`pieces` > 0),0,`i`.`quantity`)) AS `total_quantity`,sum(`i`.`pieces`) AS `total_pieces`,min(`i`.`entry_date`) AS `first_entry_date` from ((`inventory` `i` join `product` `p` on((`i`.`product_id` = `p`.`id`))) join `warehouse` `w` on((`i`.`warehouse_id` = `w`.`id`))) group by `i`.`warehouse_id`,`p`.`id`,`p`.`product_name`,`i`.`entry_date`;

-- ----------------------------
-- Triggers structure for table warehouse
-- ----------------------------
DROP TRIGGER IF EXISTS `trg_before_update_warehouse_status`;
delimiter ;;
CREATE TRIGGER `trg_before_update_warehouse_status` BEFORE UPDATE ON `warehouse` FOR EACH ROW BEGIN
    -- 所有变量声明必须在 BEGIN 后面第一部分
    DECLARE capacity_percentage DECIMAL(5,2);
    DECLARE earliest_date DATE;
    
    IF NEW.status = '维护' THEN
        -- 如果状态已被设置为“维护”，不修改状态，直接保留原状态
        SET NEW.status = NEW.status;
    ELSEIF NEW.cur_capacity IS NULL OR NEW.cur_capacity = 0 THEN
        SET NEW.status = '空置';
    ELSE
        SET capacity_percentage = (NEW.cur_capacity / NEW.max_capacity) * 100;
        IF capacity_percentage >= 95 THEN
            SET NEW.status = '满仓';
        ELSE
            -- 查询该仓库中所有库存记录中最早的入库日期
            SELECT MIN(entry_date) INTO earliest_date
            FROM inventory
            WHERE warehouse_id = NEW.id;
            
            IF earliest_date IS NOT NULL AND DATEDIFF(CURDATE(), earliest_date) >= 7 THEN
                SET NEW.status = '临期预警';
            ELSE
                SET NEW.status = '正常';
            END IF;
        END IF;
    END IF;
END
;;
delimiter ;

SET FOREIGN_KEY_CHECKS = 1;
