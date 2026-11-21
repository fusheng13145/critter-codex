-- ============================================================
-- V3 统计视图补齐 + 成员 A 演示学生账号
--
-- 为什么需要这个脚本：本机库是历史遗留库，Flyway 以 baseline 方式接管（history 里只有
-- << Flyway Baseline >> 与 V2），V1__init.sql 从未真正执行，因此 V1 里的三个统计视图
-- 在本机不存在；而全新克隆的库由 V1 建视图。这里用 CREATE OR REPLACE 补齐，
-- 两种环境下定义完全一致，重复执行无副作用。
--
-- 视图归属 D2（成员 B 的档案与资金域），此处只做"缺失补齐"，定义照抄 V1__init.sql，
-- 不改变任何口径；等 B 把本机库与 V1 对齐后可删除本段。
-- ============================================================
SET NAMES utf8mb4;

-- 身份申请表字段必须先于下方演示角色插入；V5 会幂等重复检查，兼容既有库。
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE sys_user_role ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT ''PENDING'' COMMENT ''PENDING/GRANTED/REJECTED''',
    'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user_role' AND column_name = 'status');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE sys_user_role ADD COLUMN apply_reason VARCHAR(255) NULL COMMENT ''申请理由/审批意见''',
    'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user_role' AND column_name = 'apply_reason');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE sys_user_role ADD COLUMN available_slots VARCHAR(255) NULL COMMENT ''可参与时段（志愿者）''',
    'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user_role' AND column_name = 'available_slots');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE OR REPLACE VIEW v_animal_overview AS
SELECT status,
       COUNT(*) AS animal_count,
       SUM(neutered) AS neutered_count
FROM animal
WHERE deleted = 0
GROUP BY status;

CREATE OR REPLACE VIEW v_group_overview AS
SELECT status,
       COUNT(*) AS group_count,
       SUM(size_min) AS size_min_total,
       SUM(size_max) AS size_max_total
FROM animal_group
GROUP BY status;

CREATE OR REPLACE VIEW v_project_fund AS
SELECT p.id AS project_id,
       p.project_no,
       COALESCE(d.raised, 0) AS raised_amount,
       COALESCE(e.spent, 0) AS spent_amount,
       COALESCE(d.raised, 0) - COALESCE(e.spent, 0) AS balance
FROM project p
LEFT JOIN (SELECT project_id, SUM(amount) AS raised FROM donation WHERE status = 'VERIFIED' GROUP BY project_id) d
    ON d.project_id = p.id
LEFT JOIN (SELECT project_id, SUM(amount) AS spent FROM expense WHERE status = 'APPROVED' GROUP BY project_id) e
    ON e.project_id = p.id;

-- ---------- 成员 A 专用演示学生（避免与既有种子账号的学号冲突） ----------
INSERT IGNORE INTO base_roster (user_no, name, dept, grade, imported_batch) VALUES
('2023019001', '青檐一号', '计算机学院', '2023', '2026-秋-A演示');

INSERT IGNORE INTO sys_user (user_no, email, password_hash, nickname, dept, primary_role, status) VALUES
('2023019001', '2023019001@stu.just.edu.cn',
 '$2a$10$IXr87Xl5uU.csdfvzSG7UubVMKso5OyDYt5lMkHGLjyvTpaImasDC',
 '青檐一号', '计算机学院', 'STUDENT', 'ACTIVE');

INSERT IGNORE INTO sys_user_role (user_id, role_code, status, granted_at, granted_by)
SELECT id, 'STUDENT', 'GRANTED', NOW(), id FROM sys_user WHERE user_no = '2023019001';
