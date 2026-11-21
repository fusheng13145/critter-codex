-- ============================================================
-- V5 成员 A 域：身份申请字段 + 角色/权限/字典/学籍名单/演示账号
-- 依据《数据库设计说明书》§3.11–§3.12、《功能分析说明书》§2 权限矩阵
-- 脚本可重复执行：ALTER 用 information_schema 判断，INSERT 用 IGNORE
-- 演示账号密码（BCrypt，明文仅供本地联调）：
--   学生     2023010173 / Qy@2026
--   动物管理员 2023010101 / Admin@2026
--   系统管理员 2023010102 / Admin@2026
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 1 身份申请表字段：申请与授予共用一张表 ----------
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

-- 历史数据：已授予的行按 GRANTED 回填，避免升级后角色丢失
UPDATE sys_user_role SET status = 'GRANTED'
WHERE granted_at IS NOT NULL AND (status = 'PENDING' OR status IS NULL);

-- ---------- 2 角色 ----------
INSERT IGNORE INTO sys_role (role_code, role_name, description) VALUES
('GUEST', '游客', '未登录访问，只读公开页'),
('STUDENT', '注册学生', '校内学生，可浏览、上报、申请认养'),
('ADOPTER', '认养人', '叠加身份，由认养审核通过后授予'),
('VOLUNTEER', '志愿者', '叠加身份，可报名志愿任务'),
('ANIMAL_ADMIN', '动物管理员', '档案、救助、资金复核等业务管理员'),
('SYSTEM_ADMIN', '系统管理员', '字典、权限、日志治理，不具备业务审核权');

-- ---------- 3 权限点（菜单 + 接口） ----------
INSERT IGNORE INTO sys_permission (perm_code, perm_name, type, path) VALUES
('menu:home', '工作台', 'MENU', '/'),
('menu:archive', '生物档案', 'MENU', '/animals'),
('menu:adopt', '认养专区', 'MENU', '/adopt'),
('menu:report', '救助上报', 'MENU', '/report'),
('menu:fund', '资金透明', 'MENU', '/fund'),
('menu:medical', '医疗协作', 'MENU', '/medical'),
('menu:volunteer', '志愿任务', 'MENU', '/tasks'),
('menu:content', '消息中心', 'MENU', '/messages'),
('menu:stats', '数据统计', 'MENU', '/stats'),
('menu:admin', '后台管理', 'MENU', '/admin'),
('menu:me', '个人中心', 'MENU', '/me'),
('animal:read', '读取档案', 'API', NULL),
('animal:write', '维护档案', 'API', NULL),
('report:write', '提交上报', 'API', NULL),
('report:accept', '受理上报', 'API', NULL),
('adoption:apply', '申请认养', 'API', NULL),
('adoption:audit', '审核认养', 'API', NULL),
('donation:write', '登记捐赠', 'API', NULL),
('donation:verify', '核对捐赠入账', 'API', NULL),
('expense:write', '登记支出', 'API', NULL),
('expense:review', '复核支出', 'API', NULL),
('tnr:write', '推进绝育任务', 'API', NULL),
('task:join', '报名志愿任务', 'API', NULL),
('task:publish', '发布志愿任务', 'API', NULL),
('dict:read', '读取字典', 'API', '/api/v1/dicts'),
('dict:write', '维护字典', 'API', '/api/v1/dicts'),
('user:manage', '用户治理', 'API', '/api/v1/users'),
('role:manage', '角色权限配置', 'API', '/api/v1/roles'),
('log:read', '检索操作日志', 'API', '/api/v1/logs'),
('log:read-self', '查看本人日志', 'API', '/api/v1/users/me/login-logs'),
('stats:read', '读取统计', 'API', '/api/v1/stats'),
('file:upload', '上传附件', 'API', '/api/v1/files'),
('file:audit', '审核附件', 'API', '/api/v1/files/{fileId}/audit'),
('profile:write', '维护个人资料', 'API', '/api/v1/users/me');

-- ---------- 4 字典 ----------
INSERT IGNORE INTO sys_dict (dict_type, dict_key, dict_label, sort_no, enabled) VALUES
('ANIMAL_CATEGORY', 'CAT', '猫', 1, 1),
('ANIMAL_CATEGORY', 'DOG', '犬', 2, 1),
('ANIMAL_CATEGORY', 'OTHER', '其他', 3, 1),
('ANIMAL_COLOR', 'CALICO', '三花', 1, 1),
('ANIMAL_COLOR', 'ORANGE_WHITE', '橘白', 2, 1),
('ANIMAL_COLOR', 'BLACK', '全黑', 3, 1),
('ANIMAL_COLOR', 'WHITE', '纯白', 4, 1),
('ANIMAL_COLOR', 'TABBY', '狸花', 5, 1),
('ANIMAL_COLOR', 'YELLOW', '黄色短毛', 6, 1),
('ANIMAL_GENDER', 'MALE', '雄性', 1, 1),
('ANIMAL_GENDER', 'FEMALE', '雌性', 2, 1),
('ANIMAL_GENDER', 'UNKNOWN', '未知', 3, 1),
('ANIMAL_STATUS', 'ON_CAMPUS', '在校园活动', 1, 1),
('ANIMAL_STATUS', 'TREATING', '治疗中', 2, 1),
('ANIMAL_STATUS', 'HOSPITALIZED', '住院观察', 3, 1),
('ANIMAL_STATUS', 'TO_RELEASE', '待放归', 4, 1),
('ANIMAL_STATUS', 'RELEASED', '已放归', 5, 1),
('ANIMAL_STATUS', 'ADOPTED', '已领养', 6, 1),
('ANIMAL_STATUS', 'MISSING', '失踪', 7, 1),
('ANIMAL_STATUS', 'DECEASED', '已离世', 8, 1),
('REPORT_TYPE', 'INJURED', '受伤', 1, 1),
('REPORT_TYPE', 'NEW_ARRIVAL', '新出现', 2, 1),
('REPORT_TYPE', 'ABANDONED', '疑似遗弃', 3, 1),
('REPORT_TYPE', 'DISTURB', '扰民', 4, 1),
('REPORT_TYPE', 'DEAD', '死亡', 5, 1),
('HEALTH_TYPE', 'VACCINE', '疫苗', 1, 1),
('HEALTH_TYPE', 'DEWORM', '驱虫', 2, 1),
('HEALTH_TYPE', 'WEIGHT', '体重', 3, 1),
('HEALTH_TYPE', 'HISTORY', '病史', 4, 1),
('EXPENSE_PURPOSE', 'NEUTER', '绝育手术', 1, 1),
('EXPENSE_PURPOSE', 'VACCINE', '疫苗', 2, 1),
('EXPENSE_PURPOSE', 'DEWORM', '驱虫', 3, 1),
('EXPENSE_PURPOSE', 'MEDICINE', '药品', 4, 1),
('EXPENSE_PURPOSE', 'SHELTER', '猫屋维护', 5, 1),
('EXPENSE_PURPOSE', 'TRANSPORT', '送医交通', 6, 1),
('ADOPTION_PERIOD', 'MONTH', '月度守护', 1, 1),
('ADOPTION_PERIOD', 'QUARTER', '季度陪伴', 2, 1),
('TNR_STAGE', 'CATCH', '抓捕', 1, 1),
('TNR_STAGE', 'TRANSPORT', '送医', 2, 1),
('TNR_STAGE', 'SURGERY', '手术', 3, 1),
('TNR_STAGE', 'RELEASE', '放归', 4, 1),
('TNR_STAGE', 'OBSERVE', '观察', 5, 1),
('PROJECT_STATUS', 'DRAFT', '草稿', 1, 1),
('PROJECT_STATUS', 'PENDING_AUDIT', '待审核', 2, 1),
('PROJECT_STATUS', 'FUNDING', '募捐中', 3, 1),
('PROJECT_STATUS', 'PENDING_SETTLE', '待结项', 4, 1),
('PROJECT_STATUS', 'SETTLED', '已结项', 5, 1),
('PROJECT_STATUS', 'ENDED', '已结束', 6, 1),
('PROJECT_STATUS', 'REJECTED', '已驳回', 7, 1),
('ADOPTION_STATUS', 'PENDING_AUDIT', '待审核', 1, 1),
('ADOPTION_STATUS', 'PENDING_PAYMENT', '待缴费', 2, 1),
('ADOPTION_STATUS', 'ACTIVE', '生效中', 3, 1),
('ADOPTION_STATUS', 'EXPIRED', '已到期', 4, 1),
('ADOPTION_STATUS', 'CLOSED', '已关闭', 5, 1),
('ADOPTION_STATUS', 'REJECTED', '已驳回', 6, 1),
('ADOPTION_STATUS', 'TERMINATED', '已终止', 7, 1),
('ORDER_STATUS', 'PENDING', '待受理', 1, 1),
('ORDER_STATUS', 'PROCESSING', '处置中', 2, 1),
('ORDER_STATUS', 'TO_REVISIT', '待回访', 3, 1),
('ORDER_STATUS', 'CLOSED', '已结案', 4, 1),
('ORDER_STATUS', 'TIMEOUT', '已超时', 5, 1),
('APPLY_ROLE', 'ADOPTER', '认养人', 1, 1),
('APPLY_ROLE', 'VOLUNTEER', '志愿者', 2, 1);

-- ---------- 5 学籍名单（演示：计算机学院 2023 级） ----------
INSERT IGNORE INTO base_roster (user_no, name, dept, grade, imported_batch) VALUES
('2023010173', '青檐一号', '计算机学院', '2023', '2026-秋-演示'),
('2023010101', '林照野', '计算机学院', '2023', '2026-秋-演示'),
('2023010102', '沈听澜', '计算机学院', '2023', '2026-秋-演示'),
('2023010103', '陈芋', '生物技术学院', '2023', '2026-秋-演示'),
('2023010104', '苏晚', '环境工程学院', '2023', '2026-秋-演示'),
('2023010105', '周予', '外国语学院', '2023', '2026-秋-演示'),
('2023010106', '何桨', '计算机学院', '2023', '2026-秋-演示'),
('2023010107', '许知野', '船舶与海洋工程学院', '2023', '2026-秋-演示'),
('2023010108', '顾青', '生物技术学院', '2023', '2026-秋-演示'),
('2023010109', '汤圆', '材料科学与工程学院', '2023', '2026-秋-演示');

-- ---------- 6 演示账号 ----------
INSERT IGNORE INTO sys_user (user_no, email, password_hash, nickname, dept, primary_role, status) VALUES
('2023010173', '2023010173@stu.just.edu.cn', '$2a$10$IXr87Xl5uU.csdfvzSG7UubVMKso5OyDYt5lMkHGLjyvTpaImasDC', '青檐一号', '计算机学院', 'STUDENT', 'ACTIVE'),
('2023010101', '2023010101@stu.just.edu.cn', '$2a$10$xR/.OuYZpuECSESwC2.jt.4gZVTFxE2h8/G5j3Q8CXu6na0VqXVXe', '林照野', '计算机学院', 'ANIMAL_ADMIN', 'ACTIVE'),
('2023010102', '2023010102@stu.just.edu.cn', '$2a$10$xR/.OuYZpuECSESwC2.jt.4gZVTFxE2h8/G5j3Q8CXu6na0VqXVXe', '沈听澜', '计算机学院', 'SYSTEM_ADMIN', 'ACTIVE'),
('2023010103', '2023010103@stu.just.edu.cn', '$2a$10$9JoEv74yOqqNEn9vEA7PPOVXEwuiVH.tQ5NaypcMHuBV6GKeyi8zm', '陈芋', '生物技术学院', 'STUDENT', 'ACTIVE'),
('2023010104', '2023010104@stu.just.edu.cn', '$2a$10$9JoEv74yOqqNEn9vEA7PPOVXEwuiVH.tQ5NaypcMHuBV6GKeyi8zm', '苏晚', '环境工程学院', 'STUDENT', 'ACTIVE'),
('2023010105', '2023010105@stu.just.edu.cn', '$2a$10$9JoEv74yOqqNEn9vEA7PPOVXEwuiVH.tQ5NaypcMHuBV6GKeyi8zm', '周予', '外国语学院', 'STUDENT', 'DISABLED');

-- 主角色学生补 STUDENT 叠加行；另给陈芋 ADOPTER、苏晚 VOLUNTEER
INSERT IGNORE INTO sys_user_role (user_id, role_code, status, granted_at, granted_by)
SELECT id, 'STUDENT', 'GRANTED', NOW(), id FROM sys_user WHERE primary_role = 'STUDENT';

INSERT IGNORE INTO sys_user_role (user_id, role_code, status, granted_at, granted_by)
SELECT id, 'ADOPTER', 'GRANTED', NOW(), 1 FROM sys_user WHERE user_no = '2023010103';

INSERT IGNORE INTO sys_user_role (user_id, role_code, status, granted_at, granted_by, available_slots)
SELECT id, 'VOLUNTEER', 'GRANTED', NOW(), 1, '周三下午 / 周六上午' FROM sys_user WHERE user_no = '2023010104';
