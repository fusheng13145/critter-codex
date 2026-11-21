-- ============================================================
-- V6 字典类型大小写归一
--
-- 背景：V5（原 B 的参考数据种子）用 animal_category / coat_color / gender
--       等小写 dict_type 写入字典，而 DictService 只接受大写类型
--       （^[A-Z][A-Z0-9_]{1,31}$，查询前统一 toUpperCase），
--       导致 /dicts?type=ANIMAL_CATEGORY 查不到这批数据、下拉为空。
-- 契约：API 侧字典类型一律大写，不改前端、不改 DictService。
-- 处理：1) 先删掉"转大写后会与既有大写行撞 uk_dict(dict_type,dict_key)"的小写行
--          （sys_dict 用 utf8mb4_0900_ai_ci，等值比较本身忽略大小写，
--            必须先清再改，否则 UPDATE 会报 Duplicate entry）；
--       2) 全部 dict_type 转大写；
--       3) 同 (dict_type, dict_label) 只保留 id 较小的一条（A 的原始行优先），
--          避免同一显示名在下拉里出现两次。
-- 幂等：重复执行不会产生新行（第 1、3 步均为删除，第 2 步转大写后条件不再命中）。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 1 清理转大写后会撞唯一键的小写行 ----------
DELETE lower_row
FROM sys_dict lower_row
JOIN sys_dict upper_row
  ON BINARY upper_row.dict_type = BINARY UPPER(lower_row.dict_type)
 AND BINARY upper_row.dict_key = BINARY lower_row.dict_key
 AND upper_row.id < lower_row.id
WHERE BINARY lower_row.dict_type <> BINARY UPPER(lower_row.dict_type);

-- ---------- 2 类型统一大写 ----------
UPDATE sys_dict
SET dict_type = UPPER(dict_type)
WHERE BINARY dict_type <> BINARY UPPER(dict_type);

-- ---------- 3 同类型同显示名去重，保留 id 较小者 ----------
DELETE dup
FROM sys_dict dup
JOIN sys_dict keep
  ON keep.dict_type = dup.dict_type
 AND keep.dict_label = dup.dict_label
 AND keep.id < dup.id;
