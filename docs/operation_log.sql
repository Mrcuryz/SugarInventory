/*
 Navicat Premium Data Transfer

 Source Server         : laibin（服务器）
 Source Server Type    : MySQL
 Source Server Version : 80041 (8.0.41-0ubuntu0.22.04.1)
 Source Host           : 101.33.236.7:3306
 Source Schema         : laibin

 Target Server Type    : MySQL
 Target Server Version : 80041 (8.0.41-0ubuntu0.22.04.1)
 File Encoding         : 65001

 Date: 24/04/2026 09:54:21
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
) ENGINE = InnoDB AUTO_INCREMENT = 1494 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of operation_log
-- ----------------------------
INSERT INTO `operation_log` VALUES (1486, '化验数据', 'INSERT', '陈思聪', '2026-04-23 14:09:28', '\"{\\\"sampleDate\\\":\\\"2026-04-23\\\",\\\"colorValue\\\":1,\\\"reducingSugar\\\":1,\\\"dryWeight\\\":1,\\\"conductivityAsh\\\":1,\\\"sucrose\\\":1,\\\"insolubleImpurity\\\":1,\\\"phValue\\\":1,\\\"productName\\\":\\\"黄冰糖（袋）\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (1487, '化验数据', 'DELETE', '陈思聪', '2026-04-23 14:10:10', '\"{}\"', '\"{\\\"id\\\":1683,\\\"sampleDate\\\":\\\"2026-04-23\\\",\\\"colorValue\\\":1.00,\\\"reducingSugar\\\":1.00,\\\"dryWeight\\\":1.00,\\\"conductivityAsh\\\":1.00,\\\"sucrose\\\":1.00,\\\"insolubleImpurity\\\":1.00,\\\"phValue\\\":1.00,\\\"qualifiedStandards\\\":\\\"[\\\\\\\"无\\\\\\\"]\\\",\\\"appliedStandardId\\\":null,\\\"appliedStandardName\\\":null,\\\"appliedStandardVersion\\\":null,\\\"judgeResult\\\":\\\"NO_STANDARD\\\",\\\"failedMetricCount\\\":0,\\\"failedMetricsJson\\\":\\\"[]\\\",\\\"standardSnapshotJson\\\":null,\\\"judgeMessage\\\":\\\"产品未配置启用中的化验标准\\\",\\\"isQualified\\\":\\\"无标准\\\",\\\"version\\\":1,\\\"productName\\\":\\\"黄冰糖（袋）\\\"}\"');
INSERT INTO `operation_log` VALUES (1488, '化验数据', 'INSERT', '唐秋香', '2026-04-23 16:36:02', '\"{\\\"productId\\\":null,\\\"sampleDate\\\":\\\"2026-04-21\\\",\\\"colorValue\\\":268,\\\"reducingSugar\\\":0.15,\\\"dryWeight\\\":0.97,\\\"conductivityAsh\\\":0.03,\\\"sucrose\\\":98.7,\\\"insolubleImpurity\\\":4,\\\"phValue\\\":7.52}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (1489, '化验数据', 'INSERT', '唐秋香', '2026-04-23 16:37:12', '\"{\\\"sampleDate\\\":\\\"2026-04-20\\\",\\\"colorValue\\\":null,\\\"reducingSugar\\\":null,\\\"dryWeight\\\":null,\\\"conductivityAsh\\\":null,\\\"sucrose\\\":null,\\\"insolubleImpurity\\\":null,\\\"phValue\\\":null,\\\"productName\\\":\\\"黄中冰\\\"}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (1490, '化验数据', 'DELETE', '唐秋香', '2026-04-23 16:37:27', '\"{}\"', '\"{\\\"id\\\":1693,\\\"sampleDate\\\":\\\"2026-04-20\\\",\\\"colorValue\\\":null,\\\"reducingSugar\\\":null,\\\"dryWeight\\\":null,\\\"conductivityAsh\\\":null,\\\"sucrose\\\":null,\\\"insolubleImpurity\\\":null,\\\"phValue\\\":null,\\\"qualifiedStandards\\\":\\\"[\\\\\\\"无\\\\\\\"]\\\",\\\"appliedStandardId\\\":null,\\\"appliedStandardName\\\":null,\\\"appliedStandardVersion\\\":null,\\\"judgeResult\\\":\\\"NO_STANDARD\\\",\\\"failedMetricCount\\\":0,\\\"failedMetricsJson\\\":\\\"[]\\\",\\\"standardSnapshotJson\\\":null,\\\"judgeMessage\\\":\\\"产品未配置启用中的化验标准\\\",\\\"isQualified\\\":\\\"无标准\\\",\\\"version\\\":1,\\\"productName\\\":\\\"黄中冰\\\"}\"');
INSERT INTO `operation_log` VALUES (1491, '化验数据', 'INSERT', '唐秋香', '2026-04-23 16:38:14', '\"{\\\"productId\\\":null,\\\"sampleDate\\\":\\\"2026-04-20\\\",\\\"colorValue\\\":242,\\\"reducingSugar\\\":0.19,\\\"dryWeight\\\":1.01,\\\"conductivityAsh\\\":0.03,\\\"sucrose\\\":98.9,\\\"insolubleImpurity\\\":5,\\\"phValue\\\":8.03}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (1492, '化验数据', 'INSERT', '唐秋香', '2026-04-23 16:39:24', '\"{\\\"productId\\\":null,\\\"sampleDate\\\":\\\"2026-04-18\\\",\\\"colorValue\\\":378,\\\"reducingSugar\\\":0.29,\\\"dryWeight\\\":0.96,\\\"conductivityAsh\\\":0.03,\\\"sucrose\\\":98.8,\\\"insolubleImpurity\\\":4,\\\"phValue\\\":7.95}\"', '\"{}\"');
INSERT INTO `operation_log` VALUES (1493, '化验数据', 'INSERT', '唐秋香', '2026-04-23 16:40:54', '\"{\\\"productId\\\":null,\\\"sampleDate\\\":\\\"2026-04-17\\\",\\\"colorValue\\\":290,\\\"reducingSugar\\\":0.25,\\\"dryWeight\\\":1.03,\\\"conductivityAsh\\\":0.03,\\\"sucrose\\\":98.8,\\\"insolubleImpurity\\\":4,\\\"phValue\\\":7.83}\"', '\"{}\"');

SET FOREIGN_KEY_CHECKS = 1;
