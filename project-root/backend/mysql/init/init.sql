/*
 Navicat Premium Data Transfer

 Source Server         : MysqlLink
 Source Server Type    : MySQL
 Source Server Version : 80033 (8.0.33)
 Source Host           : localhost:3306
 Source Schema         : laibin

 Target Server Type    : MySQL
 Target Server Version : 80033 (8.0.33)
 File Encoding         : 65001

 Date: 09/04/2025 02:41:27
*/
CREATE DATABASE IF NOT EXISTS laibin;
USE laibin;

CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY 'ssmrcury1';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;

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
  UNIQUE INDEX `uk_product_date_version`(`product_id` ASC, `sample_date` ASC, `version` ASC) USING BTREE,
  INDEX `tested_by`(`tested_by` ASC) USING BTREE,
  INDEX `idx_sample_date`(`sample_date` ASC) USING BTREE,
  CONSTRAINT `assay_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `assay_ibfk_2` FOREIGN KEY (`tested_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 29 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of assay
-- ----------------------------

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
  `role_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '预设角色',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `employee_id`(`employee_id` ASC) USING BTREE,
  UNIQUE INDEX `mobile`(`mobile` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of employee_roster
-- ----------------------------
INSERT INTO `employee_roster` VALUES (1, 'EMP001', 'csc', '13511111111', NULL, NULL, '在职', 'ADMIN', '2025-02-21 02:34:22');
INSERT INTO `employee_roster` VALUES (5, 'EMP004', 'zmy', '13547721231', '', '', '在职', 'ADMIN', '2025-03-04 12:57:47');
INSERT INTO `employee_roster` VALUES (6, 'EMP000', 'i', '13557627356', NULL, NULL, '在职', 'ADMIN', '2025-03-11 15:27:35');

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
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `product_id`(`product_id` ASC) USING BTREE,
  INDEX `created_by`(`created_by` ASC) USING BTREE,
  INDEX `idx_entry_date`(`entry_date` ASC) USING BTREE,
  INDEX `in_stock_ibfk_2`(`warehouse_id` ASC) USING BTREE,
  INDEX `screen_mesh_id`(`screen_mesh_id` ASC) USING BTREE,
  CONSTRAINT `in_stock_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `in_stock_ibfk_2` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `in_stock_ibfk_3` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `in_stock_ibfk_4` FOREIGN KEY (`screen_mesh_id`) REFERENCES `screen_mesh` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 81 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of in_stock
-- ----------------------------

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
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `warehouse_id`(`warehouse_id` ASC) USING BTREE,
  INDEX `product_id`(`product_id` ASC) USING BTREE,
  INDEX `entry_date`(`entry_date` ASC) USING BTREE,
  INDEX `inventory_ibfk_3`(`assay_id` ASC) USING BTREE,
  INDEX `idx_wh_product`(`warehouse_id` ASC, `product_id` ASC) USING BTREE,
  INDEX `inventory_ibfk_4`(`in_stock_id` ASC) USING BTREE,
  INDEX `fk_screen_mesh_inventory`(`screen_mesh_id` ASC) USING BTREE,
  CONSTRAINT `fk_screen_mesh_inventory` FOREIGN KEY (`screen_mesh_id`) REFERENCES `screen_mesh` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `inventory_ibfk_1` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `inventory_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `inventory_ibfk_3` FOREIGN KEY (`assay_id`) REFERENCES `assay` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `inventory_ibfk_4` FOREIGN KEY (`in_stock_id`) REFERENCES `in_stock` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 545 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of inventory
-- ----------------------------

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
) ENGINE = InnoDB AUTO_INCREMENT = 155 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

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

-- ----------------------------
-- Table structure for out_stock
-- ----------------------------
DROP TABLE IF EXISTS `out_stock`;
CREATE TABLE `out_stock`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '出库ID',
  `warehouse_id` int NOT NULL COMMENT '库位ID',
  `product_id` int NOT NULL COMMENT '产品ID',
  `quantity` int NOT NULL COMMENT '出库数量（板）',
  `in_date` date NOT NULL COMMENT '生产日期（入库日期）',
  `out_date` date NOT NULL COMMENT '出库时间',
  `operator_id` int NOT NULL COMMENT '操作员ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `total_weight` decimal(10, 2) NULL DEFAULT NULL COMMENT '总重量',
  `assay_id` int NULL DEFAULT NULL COMMENT '化验记录id\r\n',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_warehouse_product`(`warehouse_id` ASC, `product_id` ASC) USING BTREE,
  INDEX `operator_id`(`operator_id` ASC) USING BTREE,
  INDEX `idx_outstock_product`(`product_id` ASC, `in_date` ASC) USING BTREE,
  INDEX `idx_outstock`(`product_id` ASC, `warehouse_id` ASC) USING BTREE,
  INDEX `assay_id`(`assay_id` ASC) USING BTREE,
  CONSTRAINT `out_stock_ibfk_1` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `out_stock_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `out_stock_ibfk_3` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `out_stock_ibfk_4` FOREIGN KEY (`assay_id`) REFERENCES `assay` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '出库记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of out_stock
-- ----------------------------

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
  UNIQUE INDEX `permission_code`(`perm_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 23 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

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
  INDEX `created_by`(`created_by` ASC) USING BTREE,
  CONSTRAINT `product_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 83 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

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
INSERT INTO `product` VALUES (47, '黄中冰', '黄冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
INSERT INTO `product` VALUES (48, '黄碎冰白糖袋', '黄冰糖', '半成品', '袋', 40.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
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
INSERT INTO `product` VALUES (68, '黄中冰净', '黄冰糖', '成品', '箱', 15.00, 1, '2025-02-20 03:36:33', NULL, NULL, 1, 0);
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
  UNIQUE INDEX `product_type`(`product_type` ASC, `standard_name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of quality_standards
-- ----------------------------
INSERT INTO `quality_standards` VALUES (1, '黄冰糖', '中粮优级', 210.00, 360.00, NULL, 0.60, NULL, 1.40, NULL, 0.13, 97.80, NULL, NULL, 60.00, 0.00, 14.00, '2025-03-12 23:57:13', '2025-03-13 00:03:54');
INSERT INTO `quality_standards` VALUES (2, '白冰糖', '一级', NULL, 150.00, NULL, 0.70, NULL, 1.40, NULL, 0.13, 97.10, NULL, NULL, 60.00, 0.00, 14.00, '2025-03-13 00:20:45', '2025-03-13 00:41:06');

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
  INDEX `role_code`(`role_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

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
  UNIQUE INDEX `uniq_role_permission`(`role_id` ASC, `permission_id` ASC) USING BTREE,
  INDEX `permission_id`(`permission_id` ASC) USING BTREE,
  CONSTRAINT `role_permission_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `role_permission_ibfk_2` FOREIGN KEY (`permission_id`) REFERENCES `permission` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 77 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

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
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

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
  `quantity` int NOT NULL COMMENT '数量（板）',
  `operation_date` date NOT NULL COMMENT '操作日期',
  `operator` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作员',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `total_weight` decimal(10, 2) NULL DEFAULT NULL COMMENT '总重量',
  `warehouse_id` int NOT NULL COMMENT '库位号',
  `assay_id` int NOT NULL COMMENT '化验记录id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `product_id`(`product_id` ASC) USING BTREE,
  INDEX `semi_product_record_FK`(`assay_id` ASC) USING BTREE,
  INDEX `semi_product_record_FK_1`(`warehouse_id` ASC) USING BTREE,
  CONSTRAINT `semi_product_record_FK` FOREIGN KEY (`assay_id`) REFERENCES `assay` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `semi_product_record_FK_1` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `semi_product_record_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 76 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of semi_product_record
-- ----------------------------

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
  UNIQUE INDEX `openid`(`openid` ASC) USING BTREE,
  UNIQUE INDEX `employee_id`(`employee_id` ASC) USING BTREE,
  INDEX `role_id`(`role_code` ASC) USING BTREE,
  INDEX `idx_employee`(`employee_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` VALUES (1, 'i', '', 'STAFF', '2025-02-20 03:36:10', '', 'MANUAL_BOUND', 'MANUAL', 'WECHAT');
INSERT INTO `user` VALUES (2, 'csc', 'ojupe7MtU_CE0tvWJ59_oCjN4D5s', 'ADMIN', '2025-03-04 22:09:06', 'EMP001', 'MANUAL_BOUND', 'MANUAL', 'WECHAT');
INSERT INTO `user` VALUES (13, 'i', 'ojupe7Lnq7Rgn9xEW7C4YlbQEpXo', 'ADMIN', '2025-03-11 17:29:30', 'EMP000', 'MANUAL_BOUND', 'MANUAL', 'WECHAT');
INSERT INTO `user` VALUES (14, 'zmy', 'ojupe7OLQPG2Wzssxg9J8cviwLqA', 'ADMIN', '2025-04-05 18:14:45', 'EMP004', 'MANUAL_BOUND', 'MANUAL', 'WECHAT');

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
) ENGINE = InnoDB AUTO_INCREMENT = 102 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of warehouse
-- ----------------------------
INSERT INTO `warehouse` VALUES (1, '空置', '2025-04-08 19:13:12', 20, 0, 10, '1');
INSERT INTO `warehouse` VALUES (2, '空置', '2025-04-08 19:13:12', 20, 0, 10, '2');
INSERT INTO `warehouse` VALUES (3, '空置', '2025-04-08 19:13:12', 20, 0, 10, '3');
INSERT INTO `warehouse` VALUES (4, '空置', '2025-04-08 19:13:12', 20, 0, 10, '4');
INSERT INTO `warehouse` VALUES (5, '空置', '2025-04-08 19:13:12', 20, 0, 10, '5');
INSERT INTO `warehouse` VALUES (6, '空置', '2025-04-08 19:13:12', 20, 0, 10, '6');
INSERT INTO `warehouse` VALUES (7, '空置', '2025-04-08 19:13:12', 20, 0, 10, '7');
INSERT INTO `warehouse` VALUES (8, '空置', '2025-04-08 19:13:12', 20, 0, 10, '8');
INSERT INTO `warehouse` VALUES (9, '空置', '2025-04-08 19:13:12', 20, 0, 10, '9');
INSERT INTO `warehouse` VALUES (10, '空置', '2025-04-08 19:13:12', 20, 0, 10, '10');
INSERT INTO `warehouse` VALUES (11, '空置', '2025-04-08 19:13:12', 20, 0, 10, '11');
INSERT INTO `warehouse` VALUES (12, '空置', '2025-04-08 19:13:12', 20, 0, 10, '12');
INSERT INTO `warehouse` VALUES (13, '空置', '2025-04-08 19:13:12', 20, 0, 10, '13');
INSERT INTO `warehouse` VALUES (14, '空置', '2025-04-08 19:13:12', 20, 0, 10, '14');
INSERT INTO `warehouse` VALUES (15, '空置', '2025-04-08 19:13:12', 20, 0, 10, '15');
INSERT INTO `warehouse` VALUES (16, '空置', '2025-04-08 19:13:12', 20, 0, 10, '16');
INSERT INTO `warehouse` VALUES (17, '空置', '2025-04-08 19:13:12', 20, 0, 10, '17');
INSERT INTO `warehouse` VALUES (18, '空置', '2025-04-08 19:13:12', 20, 0, 10, '18');
INSERT INTO `warehouse` VALUES (19, '空置', '2025-04-08 19:13:12', 20, 0, 10, '19');
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
INSERT INTO `warehouse` VALUES (31, '空置', '2025-04-08 19:13:12', 20, 0, 10, '31');
INSERT INTO `warehouse` VALUES (32, '空置', '2025-04-08 19:13:12', 20, 0, 10, '32');
INSERT INTO `warehouse` VALUES (33, '空置', '2025-04-08 19:13:12', 20, 0, 10, '33');
INSERT INTO `warehouse` VALUES (34, '空置', '2025-04-08 19:13:12', 20, 0, 10, '34');
INSERT INTO `warehouse` VALUES (35, '空置', '2025-04-08 19:13:12', 20, 0, 10, '35');
INSERT INTO `warehouse` VALUES (36, '空置', '2025-04-08 19:13:12', 20, 0, 10, '36');
INSERT INTO `warehouse` VALUES (37, '空置', '2025-04-08 19:13:12', 20, 0, 10, '37');
INSERT INTO `warehouse` VALUES (38, '空置', '2025-04-08 19:13:12', 20, 0, 10, '38');
INSERT INTO `warehouse` VALUES (39, '空置', '2025-04-08 19:13:12', 20, 0, 10, '39');
INSERT INTO `warehouse` VALUES (40, '空置', '2025-04-08 19:13:12', 20, 0, 10, '40');
INSERT INTO `warehouse` VALUES (41, '空置', '2025-04-08 19:13:12', 20, 0, 10, '41');
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
INSERT INTO `warehouse` VALUES (52, '空置', '2025-04-08 19:13:12', 20, 0, 10, '52');
INSERT INTO `warehouse` VALUES (53, '空置', '2025-04-08 19:13:12', 20, 0, 10, '53');
INSERT INTO `warehouse` VALUES (54, '空置', '2025-04-08 19:13:12', 20, 0, 10, '54');
INSERT INTO `warehouse` VALUES (55, '空置', '2025-04-08 19:13:12', 20, 0, 10, '55');
INSERT INTO `warehouse` VALUES (56, '空置', '2025-04-08 19:13:12', 20, 0, 10, '56');
INSERT INTO `warehouse` VALUES (57, '空置', '2025-04-08 19:13:12', 20, 0, 10, '57');
INSERT INTO `warehouse` VALUES (58, '空置', '2025-04-08 19:13:12', 20, 0, 10, '58');
INSERT INTO `warehouse` VALUES (59, '空置', '2025-04-08 19:13:12', 20, 0, 10, '59');
INSERT INTO `warehouse` VALUES (60, '空置', '2025-04-08 19:13:12', 20, 0, 10, '60');
INSERT INTO `warehouse` VALUES (61, '空置', '2025-04-08 19:13:12', 20, 0, 10, '61');
INSERT INTO `warehouse` VALUES (62, '空置', '2025-04-08 19:13:12', 20, 0, 10, '62');
INSERT INTO `warehouse` VALUES (63, '空置', '2025-04-08 19:13:12', 20, 0, 10, '63');
INSERT INTO `warehouse` VALUES (64, '空置', '2025-04-08 19:13:12', 20, 0, 10, '64');
INSERT INTO `warehouse` VALUES (65, '空置', '2025-04-08 19:13:12', 20, 0, 10, '65');
INSERT INTO `warehouse` VALUES (66, '空置', '2025-04-08 19:13:12', 20, 0, 10, '66');
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
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `v_warehouse_inventory_summary` AS select `w`.`id` AS `warehouse_id`,`w`.`warehouse_name` AS `warehouse_name`,`p`.`id` AS `product_id`,`p`.`product_name` AS `product_name`,`i`.`entry_date` AS `entry_date`,sum(`i`.`quantity`) AS `total_quantity`,min(`i`.`entry_date`) AS `first_entry_date` from ((`inventory` `i` join `product` `p` on((`i`.`product_id` = `p`.`id`))) join `warehouse` `w` on((`i`.`warehouse_id` = `w`.`id`))) group by `i`.`warehouse_id`,`p`.`id`,`p`.`product_name`,`i`.`entry_date`;

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
