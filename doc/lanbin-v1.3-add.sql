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

 Date: 09/09/2025 16:33:38
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for assay_group
-- ----------------------------
DROP TABLE IF EXISTS `assay_group`;
CREATE TABLE `assay_group`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `standard_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标准名称',
  `related_products` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '产品',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `created_by` int NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` int NULL DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 26 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '化验标准（分组）' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
