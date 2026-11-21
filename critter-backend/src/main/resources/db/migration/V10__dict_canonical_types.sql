-- 字典规范类型归并：main 的 V6 只做大小写归一，旧种子的大写别名仍需折叠到 A 域约定的类型。
-- 保留已有键，补充 B 的扩展键，最后删除别名行。
SET NAMES utf8mb4;

-- 毛色：COAT_COLOR -> ANIMAL_COLOR
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no, enabled)
SELECT 'ANIMAL_COLOR', s.dict_key, s.dict_label, s.sort_no + 20, s.enabled
FROM sys_dict s
WHERE BINARY s.dict_type = 'COAT_COLOR'
  AND NOT EXISTS (
      SELECT 1 FROM sys_dict x
      WHERE BINARY x.dict_type = 'ANIMAL_COLOR' AND BINARY x.dict_key = BINARY s.dict_key
  );
DELETE FROM sys_dict WHERE BINARY dict_type = 'COAT_COLOR';

-- 支出用途：EXPENSE_CATEGORY -> EXPENSE_PURPOSE
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no, enabled)
SELECT 'EXPENSE_PURPOSE', s.dict_key, s.dict_label, s.sort_no + 20, s.enabled
FROM sys_dict s
WHERE BINARY s.dict_type = 'EXPENSE_CATEGORY'
  AND NOT EXISTS (
      SELECT 1 FROM sys_dict x
      WHERE BINARY x.dict_type = 'EXPENSE_PURPOSE' AND BINARY x.dict_key = BINARY s.dict_key
  );
DELETE FROM sys_dict WHERE BINARY dict_type = 'EXPENSE_CATEGORY';

-- 性别：GENDER 与 ANIMAL_GENDER 等价，直接删别名，保留 A 的规范行
DELETE FROM sys_dict WHERE BINARY dict_type = 'GENDER';
