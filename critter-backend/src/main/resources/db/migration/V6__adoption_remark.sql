-- ============================================================
-- V6 认养申请备注
-- 接口 03 §7.6 定义了可选 remark，但 V1 的 adoption 表未落该字段；
-- 用幂等 ALTER 补齐，兼容已执行 V1 的库。
-- ============================================================
SET NAMES utf8mb4;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE adoption ADD COLUMN remark VARCHAR(255) NULL COMMENT ''申请备注''',
    'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'adoption' AND column_name = 'remark');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
