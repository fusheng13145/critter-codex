-- ============================================================
-- V2 参考数据：字典 + 物种名录 + 合作医院 + 校区点位
-- 依据：03 §10、02 FR-SYS-01、04 §4；业务方未给正式清单，
--       本脚本按文档假设给最小可用集合，均标注“示例/待核”。
-- 幂等：全部 INSERT ... WHERE NOT EXISTS，可重复执行不产生重复行。
-- 演示用动物/用户数据不放这里，见 db/seed/seed_demo.sql（后续）。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 1 字典 ----------
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no, enabled)
SELECT d.dict_type, d.dict_key, d.dict_label, d.sort_no, d.enabled
FROM (
    SELECT 'animal_category' AS dict_type, 'CAT' AS dict_key, '猫' AS dict_label, 1 AS sort_no, 1 AS enabled
    UNION ALL SELECT 'animal_category', 'DOG', '犬', 2, 1
    UNION ALL SELECT 'animal_category', 'OTHER', '其他', 3, 1

    UNION ALL SELECT 'gender', 'MALE', '公', 1, 1
    UNION ALL SELECT 'gender', 'FEMALE', '母', 2, 1
    UNION ALL SELECT 'gender', 'UNKNOWN', '未知', 3, 1

    UNION ALL SELECT 'coat_color', 'TABBY', '狸花', 1, 1
    UNION ALL SELECT 'coat_color', 'ORANGE_WHITE', '橘白', 2, 1
    UNION ALL SELECT 'coat_color', 'ORANGE', '橘猫', 3, 1
    UNION ALL SELECT 'coat_color', 'BLACK_WHITE', '黑白', 4, 1
    UNION ALL SELECT 'coat_color', 'CALICO', '三花', 5, 1
    UNION ALL SELECT 'coat_color', 'TORTIE', '玳瑁', 6, 1
    UNION ALL SELECT 'coat_color', 'BLACK', '纯黑', 7, 1
    UNION ALL SELECT 'coat_color', 'WHITE', '纯白', 8, 1
    UNION ALL SELECT 'coat_color', 'OTHER', '其他', 9, 1

    UNION ALL SELECT 'point_area', 'TEACHING', '教学区', 1, 1
    UNION ALL SELECT 'point_area', 'DORM', '宿舍区', 2, 1
    UNION ALL SELECT 'point_area', 'SPORT', '运动区', 3, 1
    UNION ALL SELECT 'point_area', 'CANTEEN', '食堂区', 4, 1
    UNION ALL SELECT 'point_area', 'GREEN', '绿化带', 5, 1
    UNION ALL SELECT 'point_area', 'OUTER', '校园外围', 6, 1

    UNION ALL SELECT 'protection_level', 'NATIONAL_1', '国家一级', 1, 1
    UNION ALL SELECT 'protection_level', 'NATIONAL_2', '国家二级', 2, 1
    UNION ALL SELECT 'protection_level', 'THREE_HAS', '三有动物', 3, 1
    UNION ALL SELECT 'protection_level', 'PROVINCIAL', '省级保护', 4, 1
    UNION ALL SELECT 'protection_level', 'NOT_LISTED', '未列入', 5, 1
    UNION ALL SELECT 'protection_level', 'TO_VERIFY', '待核', 6, 1

    UNION ALL SELECT 'report_type', 'INJURED', '受伤', 1, 1
    UNION ALL SELECT 'report_type', 'NEW_ARRIVAL', '新出现', 2, 1
    UNION ALL SELECT 'report_type', 'ABANDONED', '被遗弃', 3, 1
    UNION ALL SELECT 'report_type', 'DISTURB', '扰民', 4, 1
    UNION ALL SELECT 'report_type', 'DEAD', '死亡', 5, 1

    UNION ALL SELECT 'expense_category', 'MEDICAL', '医疗费', 1, 1
    UNION ALL SELECT 'expense_category', 'TNR', '绝育手术', 2, 1
    UNION ALL SELECT 'expense_category', 'VACCINE', '疫苗驱虫', 3, 1
    UNION ALL SELECT 'expense_category', 'FOOD', '粮食与用品', 4, 1
    UNION ALL SELECT 'expense_category', 'TRANSPORT', '交通与运输', 5, 1
    UNION ALL SELECT 'expense_category', 'PROMOTION', '宣传物料', 6, 1
    UNION ALL SELECT 'expense_category', 'OTHER', '其他', 7, 1

    UNION ALL SELECT 'health_type', 'VACCINE', '疫苗', 1, 1
    UNION ALL SELECT 'health_type', 'DEWORM', '驱虫', 2, 1
    UNION ALL SELECT 'health_type', 'WEIGHT', '体重', 3, 1
    UNION ALL SELECT 'health_type', 'HISTORY', '病史', 4, 1

    UNION ALL SELECT 'occurrence_scale', 'FEW', '零星', 1, 1
    UNION ALL SELECT 'occurrence_scale', 'SMALL_HERD', '小群', 2, 1
    UNION ALL SELECT 'occurrence_scale', 'LARGE_HERD', '大群', 3, 1

    UNION ALL SELECT 'adoption_period', 'MONTH', '月度', 1, 1
    UNION ALL SELECT 'adoption_period', 'QUARTER', '季度', 2, 1
) AS d
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict x WHERE x.dict_type = d.dict_type AND x.dict_key = d.dict_key
);

-- ---------- 2 物种名录（校园常见种；保护级别未核实的一律“待核”） ----------
INSERT INTO base_species (common_name, scientific_name, category, protection_level, note)
SELECT s.common_name, s.scientific_name, s.category, s.protection_level, s.note
FROM (
    SELECT '家猫' AS common_name, 'Felis catus' AS scientific_name, '哺乳纲' AS category, '未列入' AS protection_level, '校园流浪猫主体' AS note
    UNION ALL SELECT '家犬', 'Canis lupus familiaris', '哺乳纲', '未列入', '校园流浪犬'
    UNION ALL SELECT '赤腹松鼠', 'Callosciurus erythraeus', '哺乳纲', '待核', '树上活动，常见于绿化带'
    UNION ALL SELECT '黄鼬', 'Mustela sibirica', '哺乳纲', '待核', '夜行，偶见'
    UNION ALL SELECT '刺猬', 'Erinaceus amurensis', '哺乳纲', '待核', '草丛活动'
    UNION ALL SELECT '野猪', 'Sus scrofa', '哺乳纲', '待核', '入校需拦截并上报（BR-18）'
    UNION ALL SELECT '白头鹎', 'Pycnonotus sinensis', '鸟纲', '待核', '校园常见鸣禽'
    UNION ALL SELECT '乌鸫', 'Turdus mandarinus', '鸟纲', '待核', '地面觅食'
    UNION ALL SELECT '珠颈斑鸠', 'Spilopelia chinensis', '鸟纲', '待核', '草坪常见'
    UNION ALL SELECT '中华蟾蜍', 'Bufo gargarizans', '两栖纲', '待核', '水边活动'
) AS s
WHERE NOT EXISTS (
    SELECT 1 FROM base_species b WHERE b.common_name = s.common_name
);

-- ---------- 3 合作医院（全部为示例，不得当作真实报价对外） ----------
INSERT INTO base_clinic (clinic_name, contact, phone, address, price_note)
SELECT c.clinic_name, c.contact, c.phone, c.address, c.price_note
FROM (
    SELECT '校园北门合作宠物医院（示例）' AS clinic_name, '王医生' AS contact, '0000-0000001' AS phone,
           '北门路 1 号（示例地址）' AS address, '绝育 300 元、疫苗 60 元/针（示例价，待社团确认）' AS price_note
    UNION ALL SELECT '市动物疫病预防控制中心（示例）', '李医生', '0000-0000002', '城南大道 8 号（示例地址）',
           '绝育 200 元、狂犬疫苗 50 元/针（示例价，待社团确认）'
    UNION ALL SELECT '城南流浪动物绝育诊所（示例）', '张医生', '0000-0000003', '城南路 22 号（示例地址）',
           '公猫绝育 150 元、母猫绝育 300 元（示例价，待社团确认）'
) AS c
WHERE NOT EXISTS (
    SELECT 1 FROM base_clinic b WHERE b.clinic_name = c.clinic_name
);

-- ---------- 4 校区点位（坐标是 0–100 的相对值，待平面图底图校准） ----------
INSERT INTO base_point (point_name, area, x, y, enabled, note)
SELECT p.point_name, p.area, p.x, p.y, 1, p.note
FROM (
    SELECT '东门草坪' AS point_name, '绿化带' AS area, 12.00 AS x, 18.00 AS y, '坐标待底图校准' AS note
    UNION ALL SELECT '图书馆北侧', '教学区', 30.00, 32.00, '坐标待底图校准'
    UNION ALL SELECT '一号教学楼', '教学区', 35.00, 40.00, '坐标待底图校准'
    UNION ALL SELECT '学生一食堂', '食堂区', 52.00, 55.00, '坐标待底图校准'
    UNION ALL SELECT '南区宿舍', '宿舍区', 60.00, 75.00, '坐标待底图校准'
    UNION ALL SELECT '体育馆西侧', '运动区', 70.00, 45.00, '坐标待底图校准'
    UNION ALL SELECT '实验楼后', '教学区', 45.00, 28.00, '坐标待底图校准'
    UNION ALL SELECT '中心湖东岸', '绿化带', 58.00, 38.00, '坐标待底图校准'
    UNION ALL SELECT '北门绿化带', '校园外围', 20.00, 8.00, '坐标待底图校准'
    UNION ALL SELECT '老校区围墙', '校园外围', 8.00, 60.00, '坐标待底图校准'
    UNION ALL SELECT '快递站旁', '宿舍区', 65.00, 68.00, '坐标待底图校准'
    UNION ALL SELECT '家属区小花园', '校园外围', 78.00, 20.00, '坐标待底图校准'
) AS p
WHERE NOT EXISTS (
    SELECT 1 FROM base_point b WHERE b.point_name = p.point_name
);
