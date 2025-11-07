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

 Date: 22/07/2025 08:44:12
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
