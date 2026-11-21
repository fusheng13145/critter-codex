-- ============================================================
-- V7 角色权限关联表 + 账号申诉表
--
-- 背景：
--   1) PUT /roles/{id}/permissions 要在线改权限，原先矩阵只写在
--      MenuMatrix 常量里，库里没有承载表；
--   2) POST /users/me/appeals 是公开的禁用账号申诉入口，需要一个落库处
--      （04 数据库设计说明书未定义此表，按最小可用原则新增）。
--
-- 说明：
--   * sys_role_permission 在本机历史库里已由早期脚本建过（68 行 ad-hoc 数据，
--     无迁移来源），这里用 IF NOT EXISTS 对齐同一结构，再把内容重置为
--     MenuMatrix 当前生效的矩阵——保证"改库不改行为"。
--   * 角色码两套等价：SYS_ADMIN（文档）与 SYSTEM_ADMIN（历史种子），两行都写，
--     接口层改动时也会同步这一对。
--   * 菜单权限码沿用 sys_permission 既有口径：menu:home / menu:archive / ...
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 1 角色-权限关联表 ----------
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id INT NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(16) NOT NULL,
    perm_code VARCHAR(48) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_code, perm_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色权限分配';

-- ---------- 2 账号申诉表 ----------
CREATE TABLE IF NOT EXISTS sys_appeal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_no VARCHAR(20) NOT NULL COMMENT '申诉人学号/工号',
    reason VARCHAR(500) NOT NULL COMMENT '申诉理由',
    contact VARCHAR(64) NULL COMMENT '联系方式，便于管理员回访',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
    ip VARCHAR(64) NULL COMMENT '提交来源 IP，防刷与追溯',
    handled_by BIGINT NULL,
    handled_at DATETIME NULL,
    handle_note VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_appeal_user (user_no, status),
    KEY idx_appeal_status (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '账号禁用申诉';

-- ---------- 3 补齐权限点 ----------
-- MenuMatrix 里动物管理员有 report:read（接单前要看上报详情），
-- 但 V2 的权限点种子漏了这一条；不补的话在线配置回传现有矩阵会被判"未知接口权限"。
INSERT IGNORE INTO sys_permission (perm_code, perm_name, type, path) VALUES
('report:read', '读取上报', 'API', '/api/v1/reports');

-- ---------- 4 按 MenuMatrix 当前矩阵回填角色权限 ----------
-- 关联表是派生配置（可从代码常量重建），直接重置后重播，避免与历史 ad-hoc 行混在一起。
DELETE FROM sys_role_permission;

INSERT INTO sys_role_permission (role_code, perm_code) VALUES
-- 菜单：注册学生
('STUDENT','menu:home'),('STUDENT','menu:archive'),('STUDENT','menu:adopt'),('STUDENT','menu:report'),
('STUDENT','menu:fund'),('STUDENT','menu:content'),('STUDENT','menu:me'),
-- 菜单：认养人
('ADOPTER','menu:home'),('ADOPTER','menu:archive'),('ADOPTER','menu:adopt'),('ADOPTER','menu:report'),
('ADOPTER','menu:fund'),('ADOPTER','menu:content'),('ADOPTER','menu:me'),
-- 菜单：志愿者
('VOLUNTEER','menu:home'),('VOLUNTEER','menu:archive'),('VOLUNTEER','menu:adopt'),('VOLUNTEER','menu:report'),
('VOLUNTEER','menu:fund'),('VOLUNTEER','menu:volunteer'),('VOLUNTEER','menu:content'),('VOLUNTEER','menu:me'),
-- 菜单：动物管理员
('ANIMAL_ADMIN','menu:home'),('ANIMAL_ADMIN','menu:archive'),('ANIMAL_ADMIN','menu:adopt'),
('ANIMAL_ADMIN','menu:report'),('ANIMAL_ADMIN','menu:fund'),('ANIMAL_ADMIN','menu:medical'),
('ANIMAL_ADMIN','menu:volunteer'),('ANIMAL_ADMIN','menu:content'),('ANIMAL_ADMIN','menu:stats'),
('ANIMAL_ADMIN','menu:me'),
-- 菜单：系统管理员（两套角色码同权）
('SYS_ADMIN','menu:home'),('SYS_ADMIN','menu:archive'),('SYS_ADMIN','menu:fund'),('SYS_ADMIN','menu:content'),
('SYS_ADMIN','menu:stats'),('SYS_ADMIN','menu:admin'),('SYS_ADMIN','menu:me'),
('SYSTEM_ADMIN','menu:home'),('SYSTEM_ADMIN','menu:archive'),('SYSTEM_ADMIN','menu:fund'),
('SYSTEM_ADMIN','menu:content'),('SYSTEM_ADMIN','menu:stats'),('SYSTEM_ADMIN','menu:admin'),
('SYSTEM_ADMIN','menu:me'),
-- 接口：注册学生
('STUDENT','animal:read'),('STUDENT','report:write'),('STUDENT','adoption:apply'),
('STUDENT','donation:write'),('STUDENT','file:upload'),('STUDENT','profile:write'),
-- 接口：认养人
('ADOPTER','animal:read'),('ADOPTER','report:write'),('ADOPTER','adoption:apply'),
('ADOPTER','donation:write'),('ADOPTER','file:upload'),('ADOPTER','profile:write'),
-- 接口：志愿者（多一个 task:join）
('VOLUNTEER','animal:read'),('VOLUNTEER','report:write'),('VOLUNTEER','adoption:apply'),
('VOLUNTEER','donation:write'),('VOLUNTEER','file:upload'),('VOLUNTEER','profile:write'),
('VOLUNTEER','task:join'),
-- 接口：动物管理员（含业务审核权）
('ANIMAL_ADMIN','animal:read'),('ANIMAL_ADMIN','animal:write'),('ANIMAL_ADMIN','report:read'),
('ANIMAL_ADMIN','report:accept'),('ANIMAL_ADMIN','adoption:audit'),('ANIMAL_ADMIN','donation:verify'),
('ANIMAL_ADMIN','expense:write'),('ANIMAL_ADMIN','expense:review'),('ANIMAL_ADMIN','tnr:write'),
('ANIMAL_ADMIN','task:publish'),('ANIMAL_ADMIN','file:upload'),('ANIMAL_ADMIN','file:audit'),
('ANIMAL_ADMIN','dict:read'),('ANIMAL_ADMIN','log:read-self'),('ANIMAL_ADMIN','stats:read'),
-- 接口：系统管理员（不含 adoption:audit / expense:review，硬约束）
('SYS_ADMIN','animal:read'),('SYS_ADMIN','dict:read'),('SYS_ADMIN','dict:write'),('SYS_ADMIN','user:manage'),
('SYS_ADMIN','role:manage'),('SYS_ADMIN','log:read'),('SYS_ADMIN','stats:read'),('SYS_ADMIN','file:upload'),
('SYSTEM_ADMIN','animal:read'),('SYSTEM_ADMIN','dict:read'),('SYSTEM_ADMIN','dict:write'),
('SYSTEM_ADMIN','user:manage'),('SYSTEM_ADMIN','role:manage'),('SYSTEM_ADMIN','log:read'),
('SYSTEM_ADMIN','stats:read'),('SYSTEM_ADMIN','file:upload');
