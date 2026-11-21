-- 字典类型统一为 A 域 DictService 要求的“大写蛇形”命名。
-- B 的旧种子用小写类型；先保留小写行中的扩展键，迁到大写类型后删除小写行，避免 uk_dict 冲突。
SET NAMES utf8mb4;

-- 花色：ANIMAL_COLOR 已有 A 的 6 项，B 的额外花色补进去
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no, enabled)
SELECT 'ANIMAL_COLOR', s.dict_key, s.dict_label, s.sort_no + 20, s.enabled
FROM sys_dict s
WHERE BINARY s.dict_type = 'coat_color'
  AND NOT EXISTS (
      SELECT 1 FROM sys_dict x WHERE BINARY x.dict_type = 'ANIMAL_COLOR' AND x.dict_key = s.dict_key
  );
DELETE FROM sys_dict WHERE BINARY dict_type = 'coat_color';

-- 健康类型：补 HISTORY 等 A 未覆盖的键
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no, enabled)
SELECT 'HEALTH_TYPE', s.dict_key, s.dict_label, s.sort_no + 20, s.enabled
FROM sys_dict s
WHERE BINARY s.dict_type = 'health_type'
  AND NOT EXISTS (
      SELECT 1 FROM sys_dict x WHERE BINARY x.dict_type = 'HEALTH_TYPE' AND x.dict_key = s.dict_key
  );
DELETE FROM sys_dict WHERE BINARY dict_type = 'health_type';

-- 支出用途：A 用 EXPENSE_PURPOSE，B 的扩展键并入后删除小写行
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no, enabled)
SELECT 'EXPENSE_PURPOSE', s.dict_key, s.dict_label, s.sort_no + 20, s.enabled
FROM sys_dict s
WHERE BINARY s.dict_type = 'expense_category'
  AND NOT EXISTS (
      SELECT 1 FROM sys_dict x WHERE BINARY x.dict_type = 'EXPENSE_PURPOSE' AND x.dict_key = s.dict_key
  );
DELETE FROM sys_dict WHERE BINARY dict_type = 'expense_category';

-- A 已有等价大写行，直接删除 B 的小写重复（BINARY 防止大小写不敏感排序规则误删大写）
DELETE FROM sys_dict WHERE BINARY dict_type IN ('animal_category', 'gender', 'report_type', 'adoption_period');

-- 没有大写对应类型的小写行，原地改名
UPDATE sys_dict SET dict_type = 'POINT_AREA' WHERE BINARY dict_type = 'point_area';
UPDATE sys_dict SET dict_type = 'PROTECTION_LEVEL' WHERE BINARY dict_type = 'protection_level';
UPDATE sys_dict SET dict_type = 'OCCURRENCE_SCALE' WHERE BINARY dict_type = 'occurrence_scale';

-- 与 02 FR-R-01 的文案统一为“疑似遗弃”
UPDATE sys_dict SET dict_label = '疑似遗弃'
WHERE BINARY dict_type = 'REPORT_TYPE' AND dict_key = 'ABANDONED';
