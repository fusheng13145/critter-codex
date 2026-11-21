-- ============================================================
-- V4 字典去重
--
-- 背景：本机库在 V2 之前已由成员 B 播过一批字典（id 12—51），
-- V2 用不同 key 播了同义花色（三花/橘白/狸花），导致下拉出现重复选项。
-- 处理：同类型 + 同显示名时保留 id 更小的那条（B 的原始数据），删除后播的重复项。
-- 只作用于 V2 之后新增的花色行（id >= 359），不会碰到既有数据。
-- ============================================================
SET NAMES utf8mb4;

DELETE dup FROM sys_dict dup
JOIN sys_dict keep
  ON keep.dict_type = dup.dict_type
 AND keep.dict_label = dup.dict_label
 AND keep.id < dup.id
WHERE dup.dict_type = 'ANIMAL_COLOR'
  AND dup.id >= 359;
