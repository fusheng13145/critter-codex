-- ============================================================
-- V1 初始化：37 张业务表 + seq_no + 3 个统计视图
-- 依据《数据库设计说明书》§2–§8；每表默认含 id / created_at / updated_at / created_by
-- 脚本可重复执行（IF NOT EXISTS），Flyway 只跑一次
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 权限与基础 ----------
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(16) NOT NULL,
    role_name VARCHAR(32) NOT NULL,
    description VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色';

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_no VARCHAR(20) NOT NULL COMMENT '学号',
    email VARCHAR(64) NOT NULL COMMENT '校园邮箱',
    password_hash VARCHAR(72) NOT NULL COMMENT 'BCrypt',
    nickname VARCHAR(32) NOT NULL,
    dept VARCHAR(64) NULL,
    phone VARCHAR(64) NULL COMMENT 'AES 加密存储',
    primary_role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    fail_count TINYINT NOT NULL DEFAULT 0,
    locked_until DATETIME NULL,
    applied_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_no (user_no),
    UNIQUE KEY uk_user_email (email),
    CONSTRAINT ck_user_status CHECK (status IN ('PENDING', 'ACTIVE', 'LOCKED', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '账号与身份';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(16) NOT NULL,
    granted_by BIGINT NULL,
    granted_at DATETIME NULL,
    expire_at DATETIME NULL COMMENT '临时授权到期',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_code),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '叠加身份';

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    perm_code VARCHAR(48) NOT NULL,
    perm_name VARCHAR(64) NOT NULL,
    type VARCHAR(8) NOT NULL,
    parent_id BIGINT NULL,
    path VARCHAR(128) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (perm_code),
    CONSTRAINT ck_perm_type CHECK (type IN ('MENU', 'API'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限点';

CREATE TABLE IF NOT EXISTS sys_dict (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dict_type VARCHAR(32) NOT NULL,
    dict_key VARCHAR(32) NOT NULL,
    dict_label VARCHAR(64) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict (dict_type, dict_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据字典';

CREATE TABLE IF NOT EXISTS sys_file (
    id BIGINT NOT NULL AUTO_INCREMENT,
    file_id VARCHAR(24) NOT NULL,
    biz_type VARCHAR(24) NULL,
    biz_id VARCHAR(32) NULL,
    name VARCHAR(128) NOT NULL,
    path VARCHAR(255) NOT NULL,
    mime VARCHAR(64) NULL,
    size BIGINT NULL,
    uploader_id BIGINT NULL,
    audit_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_id (file_id),
    KEY idx_file_biz (biz_type, biz_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '附件';

CREATE TABLE IF NOT EXISTS sys_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id BIGINT NOT NULL,
    actor_role VARCHAR(16) NULL,
    action VARCHAR(48) NOT NULL,
    biz_type VARCHAR(24) NULL,
    biz_id VARCHAR(32) NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    ip VARCHAR(45) NULL,
    result VARCHAR(16) NOT NULL,
    fail_code INT NULL,
    request_id VARCHAR(40) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_log_actor_time (actor_id, created_at),
    KEY idx_log_biz (biz_type, biz_id),
    CONSTRAINT ck_log_result CHECK (result IN ('SUCCESS', 'FAIL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志';

CREATE TABLE IF NOT EXISTS base_roster (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_no VARCHAR(20) NOT NULL,
    name VARCHAR(32) NOT NULL,
    dept VARCHAR(64) NULL,
    grade VARCHAR(8) NULL,
    imported_batch VARCHAR(32) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roster_user_no (user_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学籍名单';

CREATE TABLE IF NOT EXISTS base_point (
    id BIGINT NOT NULL AUTO_INCREMENT,
    point_name VARCHAR(64) NOT NULL,
    area VARCHAR(32) NULL,
    x DECIMAL(10, 2) NULL,
    y DECIMAL(10, 2) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    note VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '校区点位';

CREATE TABLE IF NOT EXISTS base_species (
    id BIGINT NOT NULL AUTO_INCREMENT,
    common_name VARCHAR(64) NOT NULL,
    scientific_name VARCHAR(128) NULL,
    category VARCHAR(16) NULL,
    protection_level VARCHAR(16) NULL,
    note VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '物种名录';

CREATE TABLE IF NOT EXISTS base_clinic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    clinic_name VARCHAR(64) NOT NULL,
    contact VARCHAR(32) NULL,
    phone VARCHAR(32) NULL,
    address VARCHAR(255) NULL,
    price_note VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '合作医院';

-- ---------- 档案域 ----------
CREATE TABLE IF NOT EXISTS animal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL COMMENT 'CAT-2026-031',
    category VARCHAR(8) NOT NULL,
    name VARCHAR(32) NULL,
    gender VARCHAR(8) NOT NULL,
    color VARCHAR(16) NOT NULL,
    estimated_age TINYINT NULL,
    features VARCHAR(255) NOT NULL,
    point_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ON_CAMPUS',
    neutered TINYINT(1) NOT NULL DEFAULT 0,
    neutered_at DATE NULL,
    adopted TINYINT(1) NOT NULL DEFAULT 0,
    cover_file_id VARCHAR(24) NULL,
    internal_note TEXT NULL,
    status_changed_at DATETIME NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_animal_code (code),
    KEY idx_animal_status_point (status, point_id),
    KEY idx_animal_updated (updated_at),
    CONSTRAINT fk_animal_point FOREIGN KEY (point_id) REFERENCES base_point (id),
    CONSTRAINT ck_animal_category CHECK (category IN ('CAT', 'DOG', 'OTHER')),
    CONSTRAINT ck_animal_gender CHECK (gender IN ('MALE', 'FEMALE', 'UNKNOWN')),
    CONSTRAINT ck_animal_age CHECK (estimated_age IS NULL OR (estimated_age BETWEEN 0 AND 25)),
    CONSTRAINT ck_animal_status CHECK (status IN ('ON_CAMPUS', 'TREATING', 'HOSPITALIZED', 'TO_RELEASE',
        'RELEASED', 'ADOPTED', 'MISSING', 'DECEASED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '个体档案';

CREATE TABLE IF NOT EXISTS animal_status_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    animal_id BIGINT NOT NULL,
    from_status VARCHAR(16) NOT NULL,
    to_status VARCHAR(16) NOT NULL,
    event VARCHAR(32) NOT NULL,
    reason VARCHAR(255) NULL,
    operator_id BIGINT NOT NULL,
    occurred_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_status_log_animal_time (animal_id, occurred_at),
    CONSTRAINT fk_status_log_animal FOREIGN KEY (animal_id) REFERENCES animal (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '状态变更日志';

CREATE TABLE IF NOT EXISTS animal_change_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    animal_id BIGINT NOT NULL,
    field VARCHAR(32) NOT NULL,
    before_value VARCHAR(255) NULL,
    after_value VARCHAR(255) NULL,
    reason VARCHAR(255) NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_change_log_animal (animal_id, created_at),
    CONSTRAINT fk_change_log_animal FOREIGN KEY (animal_id) REFERENCES animal (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字段级变更';

CREATE TABLE IF NOT EXISTS health_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    animal_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    recorded_at DATETIME NOT NULL,
    result VARCHAR(255) NULL,
    next_date DATE NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_health_animal_type (animal_id, type, recorded_at),
    CONSTRAINT fk_health_animal FOREIGN KEY (animal_id) REFERENCES animal (id),
    CONSTRAINT ck_health_type CHECK (type IN ('VACCINE', 'DEWORM', 'WEIGHT', 'HISTORY'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '健康与防疫';

CREATE TABLE IF NOT EXISTS animal_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_code VARCHAR(20) NOT NULL COMMENT 'GRP-2026-003',
    species_id BIGINT NOT NULL,
    features VARCHAR(255) NOT NULL,
    point_ids JSON NOT NULL,
    size_min SMALLINT NOT NULL,
    size_max SMALLINT NOT NULL,
    size_basis VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_seen_at DATETIME NULL,
    merged_from JSON NULL,
    split_from VARCHAR(20) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_code (group_code),
    KEY idx_group_status (status, last_seen_at),
    CONSTRAINT fk_group_species FOREIGN KEY (species_id) REFERENCES base_species (id),
    CONSTRAINT ck_group_status CHECK (status IN ('ACTIVE', 'UNCONFIRMED', 'GONE')),
    CONSTRAINT ck_group_size CHECK (size_min <= size_max)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '群体档案';

CREATE TABLE IF NOT EXISTS sighting (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NULL COMMENT '待归属时为空',
    point_id BIGINT NOT NULL,
    seen_at DATETIME NOT NULL,
    count_estimate SMALLINT NULL,
    behavior VARCHAR(64) NULL,
    file_ids JSON NULL,
    recorder_id BIGINT NOT NULL,
    assign_status VARCHAR(16) NOT NULL DEFAULT 'UNASSIGNED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_sighting_group_time (group_id, seen_at),
    KEY idx_sighting_unassigned (assign_status, created_at),
    CONSTRAINT fk_sighting_group FOREIGN KEY (group_id) REFERENCES animal_group (id),
    CONSTRAINT fk_sighting_point FOREIGN KEY (point_id) REFERENCES base_point (id),
    CONSTRAINT ck_sighting_assign CHECK (assign_status IN ('ASSIGNED', 'UNASSIGNED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '群体目击';

CREATE TABLE IF NOT EXISTS occurrence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    species_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    occurred_at DATETIME NOT NULL,
    scale VARCHAR(16) NULL,
    habitat VARCHAR(32) NULL,
    file_ids JSON NULL,
    recorder_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_occurrence_species_point (species_id, point_id, occurred_at),
    CONSTRAINT fk_occurrence_species FOREIGN KEY (species_id) REFERENCES base_species (id),
    CONSTRAINT fk_occurrence_point FOREIGN KEY (point_id) REFERENCES base_point (id),
    CONSTRAINT ck_occurrence_scale CHECK (scale IS NULL OR scale IN ('FEW', 'SMALL_HERD', 'LARGE_HERD'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '物种出现记录';

-- ---------- 认养域 ----------
CREATE TABLE IF NOT EXISTS adoption_plan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_name VARCHAR(32) NOT NULL,
    period VARCHAR(8) NOT NULL,
    amount INT NOT NULL,
    benefits VARCHAR(255) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_plan_period CHECK (period IN ('MONTH', 'QUARTER')),
    CONSTRAINT ck_plan_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '认养方案';

CREATE TABLE IF NOT EXISTS adoption (
    id BIGINT NOT NULL AUTO_INCREMENT,
    adoption_no VARCHAR(20) NOT NULL,
    user_id BIGINT NOT NULL,
    animal_id BIGINT NOT NULL,
    animal_code VARCHAR(20) NOT NULL,
    plan_id BIGINT NOT NULL,
    amount INT NOT NULL,
    period VARCHAR(8) NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    status VARCHAR(20) NOT NULL,
    active_flag TINYINT(1) GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
    auditor_id BIGINT NULL,
    audit_reason VARCHAR(255) NULL,
    contact VARCHAR(32) NOT NULL,
    promise_accepted_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_adoption_no (adoption_no),
    UNIQUE KEY uk_adoption_active (animal_id, active_flag),
    KEY idx_adoption_user (user_id, status),
    CONSTRAINT fk_adoption_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_adoption_animal FOREIGN KEY (animal_id) REFERENCES animal (id),
    CONSTRAINT fk_adoption_plan FOREIGN KEY (plan_id) REFERENCES adoption_plan (id),
    CONSTRAINT ck_adoption_amount CHECK (amount > 0),
    CONSTRAINT ck_adoption_period CHECK (period IN ('MONTH', 'QUARTER')),
    CONSTRAINT ck_adoption_status CHECK (status IN ('PENDING_AUDIT', 'PENDING_PAYMENT', 'ACTIVE',
        'EXPIRED', 'CLOSED', 'REJECTED', 'TERMINATED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '认养单与关系';

CREATE TABLE IF NOT EXISTS adoption_payment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_no VARCHAR(24) NOT NULL,
    adoption_id BIGINT NOT NULL,
    amount INT NOT NULL,
    paid_at DATETIME NOT NULL,
    verifier_id BIGINT NULL,
    verified_at DATETIME NULL,
    voucher_file_ids JSON NOT NULL,
    note VARCHAR(255) NULL,
    reversal_of BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_payment_adoption (adoption_id),
    KEY idx_payment_verified (verified_at),
    CONSTRAINT fk_payment_adoption FOREIGN KEY (adoption_id) REFERENCES adoption (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '认养缴费';

-- ---------- 资金域 ----------
CREATE TABLE IF NOT EXISTS project (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_no VARCHAR(20) NOT NULL,
    name VARCHAR(64) NOT NULL,
    purpose TEXT NOT NULL,
    target_type VARCHAR(8) NOT NULL,
    target_amount INT NOT NULL,
    raised_amount INT NOT NULL DEFAULT 0,
    spent_amount INT NOT NULL DEFAULT 0,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    auditor_id BIGINT NULL,
    audit_reason VARCHAR(255) NULL,
    cover_file_id VARCHAR(24) NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_no (project_no),
    KEY idx_project_status (status, end_date),
    CONSTRAINT ck_project_target_type CHECK (target_type IN ('ANIMAL', 'GROUP', 'MIXED')),
    CONSTRAINT ck_project_amount CHECK (target_amount > 0),
    CONSTRAINT ck_project_status CHECK (status IN ('DRAFT', 'PENDING_AUDIT', 'FUNDING', 'PENDING_SETTLE',
        'SETTLED', 'ENDED', 'REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '募捐项目';

CREATE TABLE IF NOT EXISTS project_budget (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    subject VARCHAR(32) NOT NULL,
    amount INT NOT NULL,
    note VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_budget_project (project_id),
    CONSTRAINT fk_budget_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT ck_budget_amount CHECK (amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '预算明细';

CREATE TABLE IF NOT EXISTS donation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    donation_no VARCHAR(24) NOT NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT NULL COMMENT '匿名时为空',
    donor_display VARCHAR(32) NOT NULL,
    amount INT NOT NULL,
    anonymous TINYINT(1) NOT NULL DEFAULT 0,
    message VARCHAR(100) NULL,
    voucher_file_ids JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING_VERIFY',
    verifier_id BIGINT NULL,
    verified_at DATETIME NULL,
    reversal_of BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_donation_no (donation_no),
    KEY idx_donation_project_status (project_id, status),
    KEY idx_donation_verified (verified_at),
    CONSTRAINT fk_donation_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT ck_donation_amount CHECK (amount > 0),
    CONSTRAINT ck_donation_status CHECK (status IN ('PENDING_VERIFY', 'VERIFIED', 'ABNORMAL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '捐赠记录';

CREATE TABLE IF NOT EXISTS expense (
    id BIGINT NOT NULL AUTO_INCREMENT,
    expense_no VARCHAR(24) NOT NULL,
    project_id BIGINT NOT NULL,
    animal_id BIGINT NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    amount INT NOT NULL,
    payee VARCHAR(64) NOT NULL,
    occurred_at DATETIME NOT NULL,
    handler_id BIGINT NOT NULL,
    voucher_file_ids JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    first_reviewer_id BIGINT NULL,
    second_reviewer_id BIGINT NULL,
    review_reason VARCHAR(255) NULL,
    reversal_of BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_expense_no (expense_no),
    KEY idx_expense_project_status (project_id, status),
    CONSTRAINT fk_expense_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_expense_animal FOREIGN KEY (animal_id) REFERENCES animal (id),
    CONSTRAINT ck_expense_amount CHECK (amount > 0),
    CONSTRAINT ck_expense_status CHECK (status IN ('PENDING_REVIEW', 'FIRST_APPROVED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_expense_not_self_review CHECK (handler_id <> first_reviewer_id
        AND handler_id <> second_reviewer_id),
    CONSTRAINT ck_expense_double_sign CHECK (amount <= 50000
        OR (first_reviewer_id IS NOT NULL AND second_reviewer_id IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '支出记录';

CREATE TABLE IF NOT EXISTS project_settlement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    report_text TEXT NOT NULL,
    total_raised INT NOT NULL,
    total_spent INT NOT NULL,
    balance_to VARCHAR(255) NOT NULL,
    attachments JSON NULL,
    published_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_project (project_id),
    CONSTRAINT fk_settlement_project FOREIGN KEY (project_id) REFERENCES project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '结项公示';

-- ---------- 救助域 ----------
CREATE TABLE IF NOT EXISTS report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    report_no VARCHAR(24) NOT NULL,
    type VARCHAR(16) NOT NULL,
    point_id BIGINT NOT NULL,
    description TEXT NOT NULL,
    file_ids JSON NULL,
    reporter_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    close_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_no (report_no),
    KEY idx_report_status (status, created_at),
    CONSTRAINT fk_report_point FOREIGN KEY (point_id) REFERENCES base_point (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生上报';

CREATE TABLE IF NOT EXISTS rescue_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(20) NOT NULL,
    report_id BIGINT NULL,
    animal_id BIGINT NULL,
    type VARCHAR(16) NOT NULL,
    acceptor_id BIGINT NULL,
    handler_id BIGINT NULL,
    deadline_at DATETIME NOT NULL,
    accepted_at DATETIME NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    dispose_note TEXT NULL,
    close_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_status_deadline (status, deadline_at),
    CONSTRAINT fk_order_report FOREIGN KEY (report_id) REFERENCES report (id),
    CONSTRAINT fk_order_animal FOREIGN KEY (animal_id) REFERENCES animal (id),
    CONSTRAINT ck_order_status CHECK (status IN ('PENDING', 'PROCESSING', 'TO_REVISIT', 'CLOSED', 'TIMEOUT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '救助工单';

CREATE TABLE IF NOT EXISTS visit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    visit_no VARCHAR(24) NOT NULL,
    animal_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    clinic_id BIGINT NOT NULL,
    visited_at DATETIME NOT NULL,
    complaint VARCHAR(255) NULL,
    diagnosis VARCHAR(255) NULL,
    treatment VARCHAR(255) NULL,
    cost INT NOT NULL DEFAULT 0,
    file_ids JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_visit_no (visit_no),
    KEY idx_visit_animal_time (animal_id, visited_at),
    CONSTRAINT fk_visit_animal FOREIGN KEY (animal_id) REFERENCES animal (id),
    CONSTRAINT fk_visit_order FOREIGN KEY (order_id) REFERENCES rescue_order (id),
    CONSTRAINT fk_visit_clinic FOREIGN KEY (clinic_id) REFERENCES base_clinic (id),
    CONSTRAINT ck_visit_cost CHECK (cost >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '就诊记录';

CREATE TABLE IF NOT EXISTS tnr_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tnr_no VARCHAR(24) NOT NULL,
    animal_id BIGINT NOT NULL,
    stage VARCHAR(16) NOT NULL,
    handler_id BIGINT NOT NULL,
    occurred_at DATETIME NOT NULL,
    file_ids JSON NULL,
    note VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tnr_no (tnr_no),
    KEY idx_tnr_animal_stage (animal_id, stage, occurred_at),
    CONSTRAINT fk_tnr_animal FOREIGN KEY (animal_id) REFERENCES animal (id),
    CONSTRAINT ck_tnr_stage CHECK (stage IN ('CATCH', 'TRANSPORT', 'SURGERY', 'RELEASE', 'OBSERVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '绝育任务';

-- ---------- 志愿与内容 ----------
CREATE TABLE IF NOT EXISTS volunteer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    available_slots VARCHAR(255) NULL,
    total_hours INT NOT NULL DEFAULT 0,
    audited_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_volunteer_user (user_id),
    CONSTRAINT fk_volunteer_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '志愿者身份';

CREATE TABLE IF NOT EXISTS task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_no VARCHAR(24) NOT NULL,
    type VARCHAR(16) NOT NULL,
    point_id BIGINT NULL,
    start_at DATETIME NOT NULL,
    quota INT NOT NULL,
    joined INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no),
    KEY idx_task_start (start_at, status),
    CONSTRAINT fk_task_point FOREIGN KEY (point_id) REFERENCES base_point (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '志愿任务';

CREATE TABLE IF NOT EXISTS task_signup (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    signed_at DATETIME NOT NULL,
    checkin_at DATETIME NULL,
    checkin_photo VARCHAR(255) NULL,
    hours DECIMAL(5, 1) NULL,
    absent TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_signup_task_user (task_id, user_id),
    CONSTRAINT fk_signup_task FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT fk_signup_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '报名与签到';

CREATE TABLE IF NOT EXISTS notice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notice_no VARCHAR(24) NOT NULL,
    category VARCHAR(16) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    author_id BIGINT NOT NULL,
    pinned TINYINT(1) NOT NULL DEFAULT 0,
    published_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notice_no (notice_no),
    KEY idx_notice_publish (published_at, pinned)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '公告';

CREATE TABLE IF NOT EXISTS message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    receiver_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    title VARCHAR(128) NOT NULL,
    biz_type VARCHAR(24) NULL,
    biz_id VARCHAR(32) NULL,
    read_flag TINYINT(1) NOT NULL DEFAULT 0,
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_message_receiver (receiver_id, read_flag, created_at),
    CONSTRAINT fk_message_receiver FOREIGN KEY (receiver_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内消息';

CREATE TABLE IF NOT EXISTS comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    biz_type VARCHAR(24) NOT NULL,
    biz_id VARCHAR(32) NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    reply_content VARCHAR(500) NULL,
    reply_visible TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_comment_biz (biz_type, biz_id, created_at),
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '留言';

CREATE TABLE IF NOT EXISTS follow (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    biz_type VARCHAR(24) NOT NULL,
    biz_id VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_follow (user_id, biz_type, biz_id),
    CONSTRAINT fk_follow_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '关注关系';

-- ---------- 发号器 ----------
CREATE TABLE IF NOT EXISTS seq_no (
    id BIGINT NOT NULL AUTO_INCREMENT,
    biz_type VARCHAR(24) NOT NULL COMMENT 'CAT/DOG/OTHER/ADOPTION/PROJECT/ORDER...',
    seq_year SMALLINT NOT NULL,
    current_no INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_seq (biz_type, seq_year)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '发号器：类型 + 年份';

-- ---------- 统计视图 ----------
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
