-- ============================================================
-- V21 P2 收口：领养回访、志愿排班/缺勤、认养人权益、疫苗用药计划
-- 依赖 V1 的 animal/adoption/volunteer/task/notice 表，不改变既有列语义。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 领养（动物离校）与三次回访 ----------
CREATE TABLE IF NOT EXISTS animal_handover (
    id BIGINT NOT NULL AUTO_INCREMENT,
    handover_no VARCHAR(24) NOT NULL,
    animal_id BIGINT NOT NULL,
    animal_code VARCHAR(20) NOT NULL,
    adopter_name VARCHAR(32) NOT NULL,
    contact VARCHAR(32) NOT NULL,
    guardian_consent TINYINT(1) NOT NULL DEFAULT 0,
    residence_note VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    adopted_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_handover_no (handover_no),
    KEY idx_handover_animal (animal_id, status),
    CONSTRAINT fk_handover_animal FOREIGN KEY (animal_id) REFERENCES animal (id),
    CONSTRAINT ck_handover_status CHECK (status IN ('ACTIVE', 'RETURNED', 'CLOSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '领养交接单';

CREATE TABLE IF NOT EXISTS handover_followup (
    id BIGINT NOT NULL AUTO_INCREMENT,
    handover_id BIGINT NOT NULL,
    seq_no TINYINT NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    result VARCHAR(16) NULL,
    note VARCHAR(255) NULL,
    completed_at DATETIME NULL,
    operator_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_followup_handover_seq (handover_id, seq_no),
    KEY idx_followup_due (status, due_date),
    CONSTRAINT fk_followup_handover FOREIGN KEY (handover_id) REFERENCES animal_handover (id),
    CONSTRAINT ck_followup_seq CHECK (seq_no BETWEEN 1 AND 3),
    CONSTRAINT ck_followup_status CHECK (status IN ('PENDING', 'DONE', 'MISSED')),
    CONSTRAINT ck_followup_result CHECK (result IS NULL OR result IN ('NORMAL', 'ABNORMAL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '领养回访计划与记录';

-- ---------- 志愿排班、缺勤与申诉 ----------
ALTER TABLE volunteer
    ADD COLUMN absence_streak INT NOT NULL DEFAULT 0 COMMENT '连续缺勤次数',
    ADD COLUMN suspended_at DATETIME NULL COMMENT '暂停接单时间';

CREATE TABLE IF NOT EXISTS volunteer_shift (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shift_no VARCHAR(24) NOT NULL,
    title VARCHAR(64) NOT NULL,
    point_id BIGINT NULL,
    shift_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    capacity INT NOT NULL,
    booked INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shift_no (shift_no),
    KEY idx_shift_week (shift_date, status),
    CONSTRAINT fk_shift_point FOREIGN KEY (point_id) REFERENCES base_point (id),
    CONSTRAINT ck_shift_time CHECK (end_time > start_time),
    CONSTRAINT ck_shift_capacity CHECK (capacity BETWEEN 1 AND 100),
    CONSTRAINT ck_shift_status CHECK (status IN ('OPEN', 'CLOSED', 'CANCELED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '周排班班次';

CREATE TABLE IF NOT EXISTS volunteer_shift_signup (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shift_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    signed_at DATETIME NOT NULL,
    checkin_at DATETIME NULL,
    absent TINYINT(1) NOT NULL DEFAULT 0,
    hours DECIMAL(4, 1) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shift_signup_user (shift_id, user_id),
    KEY idx_shift_signup_absent (user_id, absent),
    CONSTRAINT fk_shift_signup_shift FOREIGN KEY (shift_id) REFERENCES volunteer_shift (id),
    CONSTRAINT fk_shift_signup_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '排班报名与出勤';

CREATE TABLE IF NOT EXISTS volunteer_appeal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reviewer_id BIGINT NULL,
    review_reason VARCHAR(255) NULL,
    decided_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_appeal_user (user_id, status),
    CONSTRAINT fk_appeal_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_appeal_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '暂停接单申诉';

-- ---------- 认养人专属权益 ----------
CREATE TABLE IF NOT EXISTS adoption_post (
    id BIGINT NOT NULL AUTO_INCREMENT,
    adoption_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_post_adoption (adoption_id, created_at),
    CONSTRAINT fk_post_adoption FOREIGN KEY (adoption_id) REFERENCES adoption (id),
    CONSTRAINT fk_post_author FOREIGN KEY (author_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '认养人专属动态';

CREATE TABLE IF NOT EXISTS adoption_visit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    adoption_id BIGINT NOT NULL,
    visit_date DATE NOT NULL,
    time_slot VARCHAR(16) NOT NULL,
    note VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reviewer_id BIGINT NULL,
    review_reason VARCHAR(255) NULL,
    decided_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_visit_adoption (adoption_id, visit_date, status),
    KEY idx_visit_pending (status, visit_date),
    CONSTRAINT fk_visit_adoption FOREIGN KEY (adoption_id) REFERENCES adoption (id),
    CONSTRAINT ck_visit_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '认养人探视预约';

-- ---------- 疫苗与用药计划 ----------
CREATE TABLE IF NOT EXISTS care_plan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_no VARCHAR(24) NOT NULL,
    animal_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    item_name VARCHAR(64) NOT NULL,
    dosage VARCHAR(64) NULL,
    plan_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    source_visit_id BIGINT NULL,
    result VARCHAR(255) NULL,
    completed_at DATETIME NULL,
    operator_id BIGINT NULL,
    reminded_at DATE NULL,
    note VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_care_plan_no (plan_no),
    KEY idx_care_plan_animal (animal_id, status, plan_date),
    KEY idx_care_plan_due (status, plan_date),
    CONSTRAINT fk_care_plan_animal FOREIGN KEY (animal_id) REFERENCES animal (id),
    CONSTRAINT fk_care_plan_visit FOREIGN KEY (source_visit_id) REFERENCES visit (id),
    CONSTRAINT ck_care_plan_type CHECK (type IN ('VACCINE', 'MEDICATION')),
    CONSTRAINT ck_care_plan_status CHECK (status IN ('PLANNED', 'DONE', 'CANCELED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '疫苗与用药计划';
