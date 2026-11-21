#!/usr/bin/env python3
"""
青檐万灵册演示数据集生成器（可直接重复执行）。

设计目标：
- 200 个动物档案、12 个募捐项目、1200 笔捐赠、5 个动物群体；
- 同时补齐支出、结项公示、上报/工单、认养、健康、目击、出现记录、公告、消息与志愿任务；
- 所有金额单位为“分”，与后端接口契约一致；
- 生成 SQL 供 mysql CLI 执行，脚本本身不依赖第三方 Python 包。

用法：
    python generate_demo_data.py --output ../demo/qywl-demo.sql

注意：生成的 SQL 会清空业务表（保留账号、角色、字典、点位、物种、医院），
只应在演示库 / 联调库执行，不要对生产库执行。
"""

from __future__ import annotations

import argparse
import json
import random
from datetime import date, datetime, timedelta
from pathlib import Path

SEED = 20261121
RNG = random.Random(SEED)
NOW = datetime(2026, 11, 21, 20, 0, 0)
TODAY = NOW.date()
YEAR = 2026
DONATION_DAY = "20261121"


def q(value) -> str:
    """SQL string literal with MySQL escaping."""
    if value is None:
        return "NULL"
    text = str(value).replace("\\", "\\\\").replace("'", "''")
    return "'" + text + "'"


def dt(value: datetime | None) -> str:
    if value is None:
        return "NULL"
    return "'" + value.strftime("%Y-%m-%d %H:%M:%S") + "'"


def d(value: date | None) -> str:
    if value is None:
        return "NULL"
    return "'" + value.strftime("%Y-%m-%d") + "'"


def json_literal(value) -> str:
    return "'" + json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "'"


def insert_block(table: str, columns: list[str], rows: list[list], batch: int = 40) -> list[str]:
    if not rows:
        return []
    out = []
    for start in range(0, len(rows), batch):
        chunk = rows[start:start + batch]
        values = ",\n".join("(" + ", ".join(str(v) for v in row) + ")" for row in chunk)
        out.append(
            f"INSERT INTO {table} ({', '.join(columns)}) VALUES\n{values};"
        )
    return out


def at_between(start: datetime, end: datetime) -> datetime:
    if end <= start:
        return start
    seconds = int((end - start).total_seconds())
    return start + timedelta(seconds=RNG.randint(0, max(0, seconds)))


# ---------------------------------------------------------------------------
# 基础数据
# ---------------------------------------------------------------------------

CAT_NAMES = [
    "年糕", "煤球", "小满", "团子", "初一", "花卷", "豆包", "芝麻", "布丁", "可乐",
    "雪球", "栗子", "奶糖", "阿橘", "小灰", "黑桃", "三花", "麦芽", "汤圆", "米粒",
    "云朵", "桃酥", "小七", "橘子", "麻薯", "红豆", "绿豆", "小满", "阿白", "灰灰",
    "小鱼", "阿花", "咪咪", "大橘", "小狸", "点点", "圆圆", "小豹", "奶盖", "芋圆",
    "松饼", "小鹿", "露露", "安安", "静静", "小七", "阿宝", "元宝", "旺财", "小七",
]
DOG_NAMES = [
    "大黄", "阿福", "来福", "豆豆", "黑豆", "小黄", "旺仔", "花脸", "灰狼", "小柴",
    "阿灰", "球球", "多多", "小虎", "白牙", "阿黄", "毛毛", "石头", "闪电", "小满",
]
OTHER_NAMES = [
    "灰尾", "小刺", "栗尾", "阿鼬", "团毛", "小羽", "啾啾", "斑斑", "阿蟾", "小野",
]

FEATURES = [
    "左耳有缺口，亲人，常在投喂点附近活动",
    "右前爪有旧伤，怕人，喜欢躲在灌木丛里",
    "全黑短毛，戴红色项圈，性格温顺",
    "橘白相间，体型偏胖，喜欢趴在食堂后门",
    "三花短毛，右眼有轻微泪痕，已绝育",
    "狸花纹，尾巴末端有一小撮白毛",
    "白色长毛，背部有一块灰色斑，警惕性高",
    "黑白花色，鼻头有黑点，喜欢追人",
    "背部有旧伤疤，行动略慢，需持续观察",
    "胆小，夜间活动，常在宿舍区垃圾桶附近",
    "亲人可摸，会主动蹭腿，适合做校园宣传猫",
    "耳朵大，尾巴短，疑似幼猫时期营养不足",
]

COLORS = ["CALICO", "ORANGE_WHITE", "BLACK", "WHITE", "TABBY", "YELLOW"]
POINTS = list(range(1, 13))

PROJECTS = [
    ("东门草坪流浪猫绝育计划", 5_000_000, "FUNDING", date(2026, 10, 1), date(2027, 1, 31), "ANIMAL"),
    ("中心湖候鸟栖息地守护", 4_000_000, "FUNDING", date(2026, 9, 15), date(2027, 2, 28), "GROUP"),
    ("校园流浪犬医疗救助", 3_500_000, "FUNDING", date(2026, 10, 10), date(2027, 1, 15), "ANIMAL"),
    ("冬季猫屋保温改造", 2_800_000, "FUNDING", date(2026, 10, 20), date(2027, 1, 31), "MIXED"),
    ("外伤猫紧急手术基金", 4_500_000, "FUNDING", date(2026, 9, 1), date(2027, 3, 31), "ANIMAL"),
    ("校园 TNR 常态化行动", 6_000_000, "FUNDING", date(2026, 8, 15), date(2027, 6, 30), "MIXED"),
    ("幼猫奶粉与驱虫计划", 2_200_000, "FUNDING", date(2026, 11, 1), date(2027, 4, 30), "ANIMAL"),
    ("老校区围墙投喂点维护", 2_500_000, "PENDING_SETTLE", date(2026, 8, 1), date(2026, 11, 15), "MIXED"),
    ("2026 秋季绝育专项", 3_000_000, "SETTLED", date(2026, 7, 1), date(2026, 10, 31), "ANIMAL"),
    ("校园生物多样性观察站", 3_800_000, "ENDED", date(2026, 6, 1), date(2026, 10, 31), "GROUP"),
    ("2027 春季绝育储备金", 4_000_000, "DRAFT", date(2027, 1, 1), date(2027, 3, 31), "ANIMAL"),
    ("流浪动物冬季保暖物资", 3_000_000, "PENDING_AUDIT", date(2026, 11, 1), date(2027, 2, 28), "MIXED"),
]

PROJECT_PURPOSE = [
    "为东门草坪及周边流浪猫提供绝育、疫苗与术后观察费用，减少无序繁殖。",
    "维护中心湖东岸的鸟类饮水点与栖息草丛，支持校园生物多样性观察。",
    "用于校园流浪犬的体检、外伤处理与必要送医，保障学生与动物安全。",
    "对三个投喂点的猫屋进行保温改造，采购保温垫、防水布与冬季猫粮。",
    "为外伤、骨折、感染等需要紧急手术的猫提供手术与住院费用。",
    "支持校园 TNR 常态化：抓捕、送医、绝育、放归与术后回访。",
    "为幼猫采购奶粉、幼猫粮、驱虫药与基础医疗物资。",
    "老校区围墙投喂点的日常维护、清洁与冬季补给。",
    "2026 秋季集中绝育专项，已按计划完成并公示结项。",
    "建设校园生物多样性观察站，记录鸟类、松鼠与两栖类出现情况。",
    "为 2027 春季绝育行动预留启动资金（草稿，尚未提交审核）。",
    "为流浪动物冬季保暖采购物资（待审核，尚未公开）。",
]

REPORTS_DESC = [
    "在教学楼后门发现一只橘猫，右前爪受伤，走路一瘸一拐，靠近后会躲。",
    "中心湖东岸有一只鸟翅膀下垂，疑似撞到玻璃，请管理员来看看。",
    "南区宿舍垃圾桶旁有一只小黑猫，眼睛分泌物很多，精神不太好。",
    "老校区围墙边发现一只狗，脖子上有旧项圈，见人会低吼。",
    "实验楼后草丛里有一只刺猬，缩成一团，附近有狗叫声。",
    "食堂后门有一只三花猫，左耳有缺口，已绝育标记，想确认档案。",
    "快递站旁有幼猫叫声，疑似和母猫走散，未见母猫。",
    "体育馆西侧有一只猫躺在路边，呼吸急促，需要紧急送医。",
    "家属区小花园发现一只黄鼬，夜间翻垃圾桶，学生有点害怕。",
    "北门绿化带发现一只鸽子，脚上缠着线，无法正常站立。",
]

ORDER_NOTES = [
    "已到现场，动物躲避较强，放置诱捕笼观察。",
    "已送合作医院，完成基础检查，等待进一步处置。",
    "现场确认无明显外伤，已留下饮水和少量猫粮。",
    "已完成抓捕并送医，术后在猫屋观察。",
    "回访发现伤口恢复良好，已拆除敷料。",
    "现场未找到目标动物，已告知上报人继续观察。",
]

NOTICE_TITLES = [
    "东门草坪投喂点本周完成清洁与消毒",
    "2026 秋季绝育专项结项公示",
    "校园流浪猫冬季猫屋改造进度",
    "如何正确上报一只受伤的校园动物",
    "中心湖候鸟观察记录（十月）",
    "关于校园投喂时段与地点的说明",
    "志愿者周末猫屋清洁招募",
    "校园生物多样性观察站开放日",
    "老校区围墙投喂点临时调整通知",
    "感谢 1200 份校园善意：捐赠明细已更新",
]


# ---------------------------------------------------------------------------
# 构造数据
# ---------------------------------------------------------------------------

def build_sql() -> str:
    lines: list[str] = []
    add = lines.append

    add("-- 青檐万灵册演示数据集（生成器：scripts/demo/generate_demo_data.py）")
    add("-- 目标：200 档案 / 12 项目 / 1200 捐赠 / 5 群体；仅用于演示库。")
    add("SET NAMES utf8mb4;")
    add("SET FOREIGN_KEY_CHECKS = 0;")

    # 清空业务表，保留 sys_user / 角色 / 字典 / 点位 / 物种 / 医院。
    for table in [
        "adoption_payment", "adoption", "adoption_plan",
        "expense", "donation", "project_settlement", "project_budget", "project",
        "visit", "rescue_order", "report",
        "tnr_task", "health_record", "animal_status_log", "animal_change_log",
        "sighting", "occurrence", "animal_group", "animal",
        "notice", "message", "task_signup", "task", "volunteer", "comment", "follow",
        "seq_no", "sys_log",
    ]:
        add(f"DELETE FROM {table};")

    # 额外两名动物管理员，用于演示双人复核；不改变既有演示账号。
    add("""
INSERT INTO sys_user (user_no, email, password_hash, nickname, dept, primary_role, status)
SELECT '2023010111', '2023010111@stu.just.edu.cn',
       '$2a$10$xR/.OuYZpuECSESwC2.jt.4gZVTFxE2h8/G5j3Q8CXu6na0VqXVXe',
       '周棠', '计算机学院', 'ANIMAL_ADMIN', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE user_no = '2023010111');

INSERT INTO sys_user (user_no, email, password_hash, nickname, dept, primary_role, status)
SELECT '2023010112', '2023010112@stu.just.edu.cn',
       '$2a$10$xR/.OuYZpuECSESwC2.jt.4gZVTFxE2h8/G5j3Q8CXu6na0VqXVXe',
       '许砚', '生物技术学院', 'ANIMAL_ADMIN', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE user_no = '2023010112');

INSERT INTO base_roster (user_no, name, dept, grade, imported_batch)
SELECT '2023010111', '周棠', '计算机学院', '2023', '2026-秋-演示'
WHERE NOT EXISTS (SELECT 1 FROM base_roster WHERE user_no = '2023010111');
INSERT INTO base_roster (user_no, name, dept, grade, imported_batch)
SELECT '2023010112', '许砚', '生物技术学院', '2023', '2026-秋-演示'
WHERE NOT EXISTS (SELECT 1 FROM base_roster WHERE user_no = '2023010112');

INSERT INTO sys_user_role (user_id, role_code, status, granted_at, granted_by)
SELECT u.id, 'ANIMAL_ADMIN', 'GRANTED', NOW(), u.id
FROM sys_user u
WHERE u.user_no IN ('2023010101', '2023010111', '2023010112')
  AND NOT EXISTS (SELECT 1 FROM sys_user_role r WHERE r.user_id = u.id AND r.role_code = 'ANIMAL_ADMIN');
""".strip())

    add("SET @admin_a := (SELECT id FROM sys_user WHERE user_no = '2023010101' LIMIT 1);")
    add("SET @admin_b := (SELECT id FROM sys_user WHERE user_no = '2023010111' LIMIT 1);")
    add("SET @admin_c := (SELECT id FROM sys_user WHERE user_no = '2023010112' LIMIT 1);")
    add("SET @sys_admin := (SELECT id FROM sys_user WHERE user_no = '2023010102' LIMIT 1);")
    add("SET @stu_a := (SELECT id FROM sys_user WHERE user_no = '2023010173' LIMIT 1);")
    add("SET @stu_b := (SELECT id FROM sys_user WHERE user_no = '2023010103' LIMIT 1);")
    add("SET @stu_c := (SELECT id FROM sys_user WHERE user_no = '2023010104' LIMIT 1);")
    add("SET @stu_d := (SELECT id FROM sys_user WHERE user_no = '2023019001' LIMIT 1);")

    # ---------- 认养方案 ----------
    plan_rows = [
        [1, q("月度守护"), q("MONTH"), 3000, q("每月收到一次猫屋近况与照片"), 1],
        [2, q("月度陪伴"), q("MONTH"), 5000, q("每月照片 + 季度猫屋探访"), 1],
        [3, q("季度守护"), q("QUARTER"), 9000, q("季度探访 + 专属电子证书"), 1],
        [4, q("季度陪伴"), q("QUARTER"), 15000, q("季度探访 + 年度感谢名单"), 1],
    ]
    lines.extend(insert_block(
        "adoption_plan",
        ["id", "plan_name", "period", "amount", "benefits", "enabled"],
        plan_rows,
    ))

    # ---------- 动物档案 ----------
    animal_rows = []
    animal_code_by_id = {}
    animal_ids_by_status = {}
    neutered_ids = []
    for animal_id in range(1, 201):
        if animal_id <= 170:
            category = "CAT"
            local_no = animal_id
            name = CAT_NAMES[(animal_id - 1) % len(CAT_NAMES)]
        elif animal_id <= 190:
            category = "DOG"
            local_no = animal_id - 170
            name = DOG_NAMES[(local_no - 1) % len(DOG_NAMES)]
        else:
            category = "OTHER"
            local_no = animal_id - 190
            name = OTHER_NAMES[(local_no - 1) % len(OTHER_NAMES)]

        code = f"{category}-{YEAR}-{local_no:03d}"
        animal_code_by_id[animal_id] = code

        if animal_id <= 140:
            status = "ON_CAMPUS"
        elif animal_id <= 155:
            status = "TREATING"
        elif animal_id <= 163:
            status = "HOSPITALIZED"
        elif animal_id <= 168:
            status = "TO_RELEASE"
        elif animal_id <= 173:
            status = "RELEASED"
        elif animal_id <= 177:
            status = "MISSING"
        elif animal_id <= 180:
            status = "DECEASED"
        elif animal_id <= 192:
            status = "ADOPTED"
        else:
            status = "ON_CAMPUS"

        animal_ids_by_status.setdefault(status, []).append(animal_id)
        if animal_id <= 120 or 164 <= animal_id <= 173:
            neutered_ids.append(animal_id)

        created = at_between(datetime(2026, 8, 1), datetime(2026, 11, 10))
        changed = at_between(created, datetime(2026, 11, 20))
        gender = "MALE" if animal_id % 2 == 0 else "FEMALE"
        if animal_id % 17 == 0:
            gender = "UNKNOWN"
        age = 1 + (animal_id * 7) % 12
        color = COLORS[animal_id % len(COLORS)]
        if category == "DOG":
            color = ["YELLOW", "BLACK", "WHITE", "TABBY"][animal_id % 4]
        if category == "OTHER":
            color = ["BLACK", "TABBY", "YELLOW"][animal_id % 3]
        features = FEATURES[animal_id % len(FEATURES)] + f"；档案编号 {code}，由校园志愿者持续观察。"
        point_id = POINTS[(animal_id - 1) % len(POINTS)]
        internal = None
        if animal_id % 6 == 0:
            internal = "投喂点观察：近两周精神状态稳定，建议继续记录体重。"
        if animal_id % 13 == 0:
            internal = "曾有学生投诉夜间叫声，已调整投喂时间。"

        animal_rows.append([
            animal_id, q(code), q(category), q(name), q(gender), q(color), age,
            q(features), point_id, q(status),
            1 if animal_id in neutered_ids else 0,
            d(date(2026, 9, 1) + timedelta(days=(animal_id * 3) % 60)) if animal_id in neutered_ids else "NULL",
            1 if status == "ADOPTED" else 0,
            "NULL", q(internal) if internal else "NULL", dt(changed),
            0, 0, dt(created), dt(changed), "@admin_a",
        ])

    lines.extend(insert_block(
        "animal",
        ["id", "code", "category", "name", "gender", "color", "estimated_age", "features",
         "point_id", "status", "neutered", "neutered_at", "adopted", "cover_file_id",
         "internal_note", "status_changed_at", "deleted", "version", "created_at",
         "updated_at", "created_by"],
        animal_rows,
    ))

    # ---------- 健康记录 ----------
    health_rows = []
    health_id = 1
    health_types = ["WEIGHT", "VACCINE", "DEWORM", "HISTORY"]
    for animal_id in range(1, 121):
        record_count = 1 if animal_id <= 80 else 2
        for n in range(record_count):
            typ = health_types[(animal_id + n) % len(health_types)]
            recorded = at_between(datetime(2026, 9, 1), datetime(2026, 11, 18))
            if typ == "WEIGHT":
                result = f"{2.2 + (animal_id % 30) / 10:.1f} kg"
                next_date = "NULL"
            elif typ == "VACCINE":
                result = "完成猫三联疫苗，未见异常"
                next_date = d(recorded.date() + timedelta(days=365))
            elif typ == "DEWORM":
                result = "完成体内外驱虫，体重稳定"
                next_date = d(recorded.date() + timedelta(days=90))
            else:
                result = "既往病史：结膜炎，已治愈；无传染病记录"
                next_date = "NULL"
            health_rows.append([
                health_id, animal_id, q(typ), dt(recorded), q(result), next_date,
                "@admin_a", dt(recorded), dt(recorded), "@admin_a",
            ])
            health_id += 1

    lines.extend(insert_block(
        "health_record",
        ["id", "animal_id", "type", "recorded_at", "result", "next_date", "operator_id",
         "created_at", "updated_at", "created_by"],
        health_rows,
    ))

    # ---------- 状态变更日志 ----------
    status_rows = []
    status_id = 1
    for animal_id in range(141, 193):
        status = next(s for s, ids in animal_ids_by_status.items() if animal_id in ids)
        event = {
            "TREATING": "REPORT_ACCEPTED",
            "HOSPITALIZED": "SEND_TO_HOSPITAL",
            "TO_RELEASE": "SURGERY_DONE",
            "RELEASED": "RELEASE",
            "MISSING": "NO_SIGHTING_30D",
            "DECEASED": "CONFIRM_DEATH",
            "ADOPTED": "ADOPTION_COMPLETED",
        }.get(status, "REPORT_ACCEPTED")
        from_status = "TREATING" if status == "TO_RELEASE" else "ON_CAMPUS"
        occurred = at_between(datetime(2026, 9, 10), datetime(2026, 11, 20))
        reason = {
            "TREATING": "学生上报受伤，管理员现场确认后送医观察。",
            "HOSPITALIZED": "已完成基础检查，留院观察一晚。",
            "TO_RELEASE": "手术恢复稳定，进入放归前观察。",
            "RELEASED": "按计划放归原活动区域，继续投喂观察。",
            "MISSING": "连续三十日无目击记录，按规则标记失踪。",
            "DECEASED": "确认死亡，已记录并通知上报人。",
            "ADOPTED": "领养手续办结，完成交接。",
        }[status]
        status_rows.append([
            status_id, animal_id, q(from_status), q(status), q(event), q(reason),
            "@admin_a", dt(occurred), dt(occurred), dt(occurred), "@admin_a",
        ])
        status_id += 1

    lines.extend(insert_block(
        "animal_status_log",
        ["id", "animal_id", "from_status", "to_status", "event", "reason", "operator_id",
         "occurred_at", "created_at", "updated_at", "created_by"],
        status_rows,
    ))

    # ---------- 5 个群体 ----------
    group_rows = [
        [1, q("GRP-2026-001"), 3, q("赤腹松鼠群体，尾毛蓬松，常在图书馆北侧树上活动"), json_literal([1, 2]), 8, 14, q("同时目击计数"), q("ACTIVE"), dt(datetime(2026, 11, 18, 9, 30)), "NULL", "NULL", dt(datetime(2026, 9, 1)), dt(datetime(2026, 11, 18)), "@admin_a"],
        [2, q("GRP-2026-002"), 4, q("黄鼬家庭，夜间沿实验楼与老围墙活动"), json_literal([7, 10]), 2, 4, q("夜间红外相机"), q("ACTIVE"), dt(datetime(2026, 11, 16, 22, 10)), "NULL", "NULL", dt(datetime(2026, 8, 20)), dt(datetime(2026, 11, 16)), "@admin_a"],
        [3, q("GRP-2026-003"), 5, q("刺猬小群，草丛与落叶堆活动，疑似三只成体"), json_literal([8, 12]), 3, 5, q("翻查落叶堆"), q("ACTIVE"), dt(datetime(2026, 11, 12, 20, 40)), "NULL", "NULL", dt(datetime(2026, 9, 10)), dt(datetime(2026, 11, 12)), "@admin_a"],
        [4, q("GRP-2026-004"), 7, q("白头鹎鸟群，清晨在绿化带取食"), json_literal([1, 9]), 18, 30, q("同步听声计数"), q("ACTIVE"), dt(datetime(2026, 11, 20, 7, 20)), "NULL", "NULL", dt(datetime(2026, 7, 15)), dt(datetime(2026, 11, 20)), "@admin_a"],
        [5, q("GRP-2026-005"), 9, q("珠颈斑鸠群体，食堂与中心湖之间往返"), json_literal([4, 8]), 10, 16, q("路线样线计数"), q("UNCONFIRMED"), dt(datetime(2026, 10, 28, 16, 5)), "NULL", "NULL", dt(datetime(2026, 6, 1)), dt(datetime(2026, 10, 28)), "@admin_a"],
    ]
    lines.extend(insert_block(
        "animal_group",
        ["id", "group_code", "species_id", "features", "point_ids", "size_min", "size_max",
         "size_basis", "status", "last_seen_at", "merged_from", "split_from",
         "created_at", "updated_at", "created_by"],
        group_rows,
    ))

    # ---------- 目击记录 ----------
    sighting_rows = []
    for sighting_id in range(1, 51):
        group_id = 1 + (sighting_id - 1) % 4
        point_id = [1, 2, 4, 7, 8, 9, 10, 12][(sighting_id - 1) % 8]
        seen = at_between(datetime(2026, 9, 1), datetime(2026, 11, 20))
        count = 3 + (sighting_id * 5) % 18
        behavior = ["树上取食", "地面觅食", "快速穿过草坪", "鸣叫", "翻找落叶", "沿围墙移动"][sighting_id % 6]
        recorder = [2, 4, 5, 7][sighting_id % 4]
        sighting_rows.append([
            sighting_id, group_id, point_id, dt(seen), count, q(behavior), json_literal([]),
            recorder, q("ASSIGNED"), dt(seen), dt(seen), "@admin_a",
        ])

    lines.extend(insert_block(
        "sighting",
        ["id", "group_id", "point_id", "seen_at", "count_estimate", "behavior", "file_ids",
         "recorder_id", "assign_status", "created_at", "updated_at", "created_by"],
        sighting_rows,
    ))

    # ---------- 物种出现记录 ----------
    occurrence_rows = []
    for occurrence_id in range(1, 31):
        species_id = 3 + (occurrence_id - 1) % 8
        point_id = 1 + (occurrence_id * 3) % 12
        occurred = at_between(datetime(2026, 8, 20), datetime(2026, 11, 20))
        scale = ["FEW", "SMALL_HERD", "LARGE_HERD"][occurrence_id % 3]
        habitat = ["林下", "水边", "草坪", "落叶堆", "灌木丛"][occurrence_id % 5]
        occurrence_rows.append([
            occurrence_id, species_id, point_id, dt(occurred), q(scale), q(habitat),
            json_literal([]), 4 if occurrence_id % 3 else 5, dt(occurred), dt(occurred), "@admin_a",
        ])

    lines.extend(insert_block(
        "occurrence",
        ["id", "species_id", "point_id", "occurred_at", "scale", "habitat", "file_ids",
         "recorder_id", "created_at", "updated_at", "created_by"],
        occurrence_rows,
    ))

    # ---------- 项目 ----------
    project_rows = []
    budget_rows = []
    for i, (name, target, status, start, end, target_type) in enumerate(PROJECTS, start=1):
        project_no = f"PRJ-{YEAR}-{i:03d}"
        auditor = "@admin_a" if status in ("FUNDING", "PENDING_SETTLE", "SETTLED", "ENDED") else "NULL"
        audit_reason = "项目资料完整，预算与目标匹配。" if auditor != "NULL" else "NULL"
        project_rows.append([
            i, q(project_no), q(name), q(PROJECT_PURPOSE[i - 1]), q(target_type), target,
            0, 0, d(start), d(end), q(status), auditor, q(audit_reason) if audit_reason != "NULL" else "NULL",
            "NULL", 0, dt(datetime(2026, 8, 1, 10, 0)), dt(datetime(2026, 11, 15, 16, 0)), "@admin_a",
        ])
        # 3 条预算，合计不超过目标
        parts = [int(target * 0.4), int(target * 0.3), int(target * 0.2)]
        subjects = ["绝育与手术", "疫苗与药品", "猫屋、运输与物料"]
        for j, (subject, amount) in enumerate(zip(subjects, parts), start=1):
            budget_rows.append([
                (i - 1) * 3 + j, i, q(subject), amount,
                q("按实际票据结算，超出需另行说明。"), dt(datetime(2026, 8, 1, 10, 0)),
                dt(datetime(2026, 8, 1, 10, 0)), "@admin_a",
            ])

    lines.extend(insert_block(
        "project",
        ["id", "project_no", "name", "purpose", "target_type", "target_amount",
         "raised_amount", "spent_amount", "start_date", "end_date", "status", "auditor_id",
         "audit_reason", "cover_file_id", "deleted", "created_at", "updated_at", "created_by"],
        project_rows,
    ))
    lines.extend(insert_block(
        "project_budget",
        ["id", "project_id", "subject", "amount", "note", "created_at", "updated_at", "created_by"],
        budget_rows,
    ))

    # ---------- 1200 笔捐赠 ----------
    donation_rows = []
    raised_by_project = {i: 0 for i in range(1, 13)}
    donation_no = 1
    for project_id in range(1, 11):
        target = PROJECTS[project_id - 1][1]
        start = PROJECTS[project_id - 1][3]
        deadline = datetime.combine(PROJECTS[project_id - 1][4], datetime.min.time())
        for index in range(120):
            if project_id in (8, 9):
                amount = RNG.randint(35000, 60000)
            elif project_id == 10:
                amount = RNG.randint(10000, 25000)
            else:
                amount = RNG.randint(12000, 35000)

            if index < 90:
                status = "VERIFIED"
            elif index < 115:
                status = "PENDING_VERIFY"
            else:
                status = "ABNORMAL"

            created = at_between(datetime.combine(start, datetime.min.time()), deadline)
            verified = created + timedelta(hours=RNG.randint(1, 36)) if status == "VERIFIED" else None
            if status == "VERIFIED":
                raised_by_project[project_id] += amount

            anonymous = 1 if index % 5 == 0 else 0
            user_id = "NULL" if anonymous else ["@stu_a", "@stu_b", "@stu_c", "@stu_d"][index % 4]
            donor = "匿名同学" if anonymous else ["青檐一号", "陈芋", "苏晚", "青檐志愿者"][index % 4]
            message = ["希望小猫们平安过冬。", "感谢志愿者的坚持。", "一点点心意，支持 TNR。", "愿校园更温柔。", ""][index % 5]
            donation_rows.append([
                donation_no, q(f"DN{DONATION_DAY}{donation_no:04d}"), project_id,
                user_id, q(donor), amount, anonymous, q(message) if message else "NULL",
                json_literal([]), q(status), "@admin_a" if status == "VERIFIED" else "NULL",
                dt(verified) if verified else "NULL", "NULL",
                dt(created), dt(verified or created), "@stu_a" if user_id != "NULL" else "NULL",
            ])
            donation_no += 1

    lines.extend(insert_block(
        "donation",
        ["id", "donation_no", "project_id", "user_id", "donor_display", "amount", "anonymous",
         "message", "voucher_file_ids", "status", "verifier_id", "verified_at", "reversal_of",
         "created_at", "updated_at", "created_by"],
        donation_rows,
    ))

    # ---------- 45 笔支出 ----------
    expense_rows = []
    expense_id = 1
    approved_by_project = {i: 0 for i in range(1, 13)}
    purposes = ["绝育手术", "疫苗", "驱虫", "药品", "猫屋维护", "送医交通"]
    payees = ["校园北门合作宠物医院（示例）", "市动物疫病预防控制中心（示例）", "城南流浪动物绝育诊所（示例）", "校园猫屋物料采购（示例）"]
    for project_id in range(1, 10):
        base_date = PROJECTS[project_id - 1][3]
        for slot in range(5):
            amount = [30000, 60000, 80000, 70000, 90000][slot]
            status = "APPROVED"
            first = "@admin_b"
            second = "@admin_c" if amount > 50000 else "NULL"
            reason = "NULL"
            if slot == 3:
                status = "FIRST_APPROVED"
                second = "NULL"
            elif slot == 4:
                if project_id <= 5:
                    status = "PENDING_REVIEW"
                    first = "NULL"
                    second = "NULL"
                else:
                    status = "REJECTED"
                    second = "NULL"
                    reason = q("票据金额与用途不符，退回补充说明。")
            if status == "APPROVED":
                approved_by_project[project_id] += amount

            animal_id = 1 + (project_id * 7 + slot * 11) % 180
            occurred = at_between(datetime.combine(base_date, datetime.min.time()), datetime(2026, 11, 18))
            expense_rows.append([
                expense_id, q(f"EX{DONATION_DAY}{expense_id:04d}"), project_id, animal_id,
                q(purposes[(project_id + slot) % len(purposes)]), amount,
                q(payees[(project_id + slot) % len(payees)]), dt(occurred), "@admin_a",
                json_literal([]), q(status), first, second, reason,
                "NULL", dt(occurred), dt(occurred), "@admin_a",
            ])
            expense_id += 1

    lines.extend(insert_block(
        "expense",
        ["id", "expense_no", "project_id", "animal_id", "purpose", "amount", "payee",
         "occurred_at", "handler_id", "voucher_file_ids", "status", "first_reviewer_id",
         "second_reviewer_id", "review_reason", "reversal_of", "created_at", "updated_at",
         "created_by"],
        expense_rows,
    ))

    # ---------- 回填项目金额并写结项公示 ----------
    add("""
UPDATE project p
SET raised_amount = COALESCE((SELECT SUM(d.amount) FROM donation d
                              WHERE d.project_id = p.id AND d.status = 'VERIFIED'), 0),
    spent_amount = COALESCE((SELECT SUM(e.amount) FROM expense e
                             WHERE e.project_id = p.id AND e.status = 'APPROVED'), 0);
""".strip())

    settlement_text = (
        "2026 秋季绝育专项已按计划完成：累计完成绝育 18 只、疫苗 31 只、"
        "外伤处置 6 只；所有支出均经两名管理员复核，票据已归档。"
        "结余资金转入校园救助基金，用于冬季猫屋维护与幼猫医疗。"
    )
    settlement_rows = [[
        1, 9, q(settlement_text), raised_by_project[9], approved_by_project[9],
        q("结余资金转入校园救助基金，专用于冬季猫屋维护与幼猫医疗，不挪作他用。"),
        json_literal([]), dt(datetime(2026, 11, 1, 10, 0)), dt(datetime(2026, 11, 1, 10, 0)),
        dt(datetime(2026, 11, 1, 10, 0)), "@admin_a",
    ]]
    lines.extend(insert_block(
        "project_settlement",
        ["id", "project_id", "report_text", "total_raised", "total_spent", "balance_to",
         "attachments", "published_at", "created_at", "updated_at", "created_by"],
        settlement_rows,
    ))

    # ---------- 认养 ----------
    adoption_rows = []
    payment_rows = []
    active_animals = list(range(181, 193))
    for idx, animal_id in enumerate(active_animals, start=1):
        plan_id = 1 + (idx - 1) % 4
        status = "ACTIVE"
        start = date(2026, 10, 1)
        end = date(2027, 10, 1)
        certificate = f"CERT-2026-{idx:04d}"
        pay_deadline = "NULL"
        termination = "NULL"
        audit_reason = "资料完整，已签署领养承诺。"
        adoption_rows.append([
            idx, q(f"AD{DONATION_DAY}{idx:03d}"), "@stu_b" if idx % 2 else "@stu_c", animal_id,
            q(animal_code_by_id[animal_id]), plan_id, [3000, 5000, 9000, 15000][plan_id - 1],
            q("MONTH" if plan_id <= 2 else "QUARTER"), d(start), d(end), q(status),
            "@admin_a", q(audit_reason), q(certificate) if certificate else "NULL",
            pay_deadline, termination, q("微信 qy-adopt-01"), dt(datetime(2026, 9, 28, 10, 0)),
            dt(datetime(2026, 9, 28, 10, 0)), dt(datetime(2026, 11, 1, 10, 0)), "@admin_a",
        ])

    for idx in range(13, 17):
        animal_id = 180 + idx
        plan_id = 1 + (idx - 13) % 4
        adoption_rows.append([
            idx, q(f"AD{DONATION_DAY}{idx:03d}"), "@stu_b" if idx % 2 else "@stu_c", animal_id,
            q(animal_code_by_id[animal_id]), plan_id, [3000, 5000, 9000, 15000][plan_id - 1],
            q("MONTH" if plan_id <= 2 else "QUARTER"), d(date(2026, 1, 1)), d(date(2026, 6, 30)),
            q("EXPIRED"), "@admin_a", q("领养期已结束，动物回到校园活动。"), "NULL", "NULL", "NULL",
            q("微信 qy-adopt-02"), dt(datetime(2025, 12, 20, 10, 0)),
            dt(datetime(2025, 12, 20, 10, 0)), dt(datetime(2026, 6, 30, 10, 0)), "@admin_a",
        ])

    for idx in range(17, 19):
        animal_id = 180 + idx
        adoption_rows.append([
            idx, q(f"AD{DONATION_DAY}{idx:03d}"), "@stu_c", animal_id,
            q(animal_code_by_id[animal_id]), 2, 5000, q("MONTH"),
            d(date(2026, 11, 1)), d(date(2027, 5, 1)), q("PENDING_PAYMENT"), "@admin_a",
            q("审核通过，等待缴费。"), "NULL", dt(datetime(2026, 11, 25, 23, 59)),
            "NULL", q("微信 qy-adopt-03"), dt(datetime(2026, 10, 28, 10, 0)),
            dt(datetime(2026, 10, 28, 10, 0)), dt(datetime(2026, 10, 29, 10, 0)), "@admin_a",
        ])

    adoption_rows.append([
        19, q(f"AD{DONATION_DAY}019"), "@stu_b", 199,
        q(animal_code_by_id[199]), 1, 3000, q("MONTH"), d(date(2026, 3, 1)), d(date(2026, 4, 1)),
        q("CLOSED"), "@admin_a", q("领养人因搬迁申请终止。"), "NULL", "NULL",
        q("领养人因搬迁申请终止，动物回到校园。"), q("微信 qy-adopt-04"),
        dt(datetime(2026, 2, 20, 10, 0)), dt(datetime(2026, 2, 20, 10, 0)),
        dt(datetime(2026, 4, 2, 10, 0)), "@admin_a",
    ])
    adoption_rows.append([
        20, q(f"AD{DONATION_DAY}020"), "@stu_c", 200,
        q(animal_code_by_id[200]), 3, 9000, q("QUARTER"), d(date(2026, 10, 1)), d(date(2027, 1, 1)),
        q("REJECTED"), "@admin_a", q("居住条件不符合，暂缓领养。"), "NULL", "NULL", "NULL",
        q("微信 qy-adopt-05"), dt(datetime(2026, 9, 25, 10, 0)),
        dt(datetime(2026, 9, 25, 10, 0)), dt(datetime(2026, 9, 26, 10, 0)), "@admin_a",
    ])

    lines.extend(insert_block(
        "adoption",
        ["id", "adoption_no", "user_id", "animal_id", "animal_code", "plan_id", "amount",
         "period", "start_date", "end_date", "status", "auditor_id", "audit_reason",
         "certificate_no", "pay_deadline", "termination_reason", "contact", "promise_accepted_at",
         "created_at", "updated_at", "created_by"],
        adoption_rows,
    ))

    for idx in range(1, 17):
        plan_id = 1 + (idx - 1) % 4
        amount = [3000, 5000, 9000, 15000][plan_id - 1]
        paid = datetime(2026, 10, 1, 9, 0) if idx <= 12 else datetime(2025, 12, 20, 9, 0)
        payment_rows.append([
            idx, q(f"AP{DONATION_DAY}{idx:03d}"), idx, amount, dt(paid), "@admin_a",
            dt(paid + timedelta(hours=2)), json_literal([]), q("认养缴费已核对。"), "NULL",
            dt(paid), dt(paid), "@admin_a",
        ])

    lines.extend(insert_block(
        "adoption_payment",
        ["id", "payment_no", "adoption_id", "amount", "paid_at", "verifier_id",
         "verified_at", "voucher_file_ids", "note", "reversal_of", "created_at",
         "updated_at", "created_by"],
        payment_rows,
    ))

    # ---------- 60 条上报 + 60 条工单 ----------
    report_rows = []
    order_rows = []
    for i in range(1, 61):
        report_type = ["INJURED", "NEW_ARRIVAL", "ABANDONED", "DISTURB", "DEAD"][i % 5]
        point_id = 1 + (i - 1) % 12
        reporter = ["@stu_a", "@stu_b", "@stu_c", "@stu_d"][i % 4]
        animal_id = 1 + (i * 7) % 180
        created = at_between(datetime(2026, 10, 1), datetime(2026, 11, 20))
        description = REPORTS_DESC[(i - 1) % len(REPORTS_DESC)]
        if i <= 10:
            report_status = "PENDING"
            order_status = "TIMEOUT"
            deadline = created + timedelta(hours=24)
            accepted = None
            close_reason = None
        elif i <= 25:
            report_status = "PENDING"
            order_status = "PENDING"
            deadline = NOW + timedelta(hours=4 + i)
            accepted = None
            close_reason = None
        elif i <= 35:
            report_status = "PROCESSING"
            order_status = "PROCESSING"
            deadline = created + timedelta(hours=24)
            accepted = created + timedelta(hours=2)
            close_reason = None
        elif i <= 40:
            report_status = "PROCESSING"
            order_status = "TO_REVISIT"
            deadline = created + timedelta(hours=24)
            accepted = created + timedelta(hours=1)
            close_reason = None
        else:
            report_status = "CLOSED"
            order_status = "CLOSED"
            deadline = created + timedelta(hours=24)
            accepted = created + timedelta(hours=1)
            close_reason = "已完成处置并回访，上报人确认。"

        report_rows.append([
            i, q(f"RP{DONATION_DAY}{i:03d}"), q(report_type), point_id, q(description),
            json_literal([]), reporter, animal_id, q(report_status),
            q(close_reason) if close_reason else "NULL", q("微信/手机：13800000000"),
            dt(created), dt(created), reporter,
        ])
        order_rows.append([
            i, q(f"RO{DONATION_DAY}{i:03d}"), i, animal_id, q(report_type),
            "@admin_a" if accepted else "NULL", "@admin_a" if accepted else "NULL",
            dt(deadline), dt(accepted) if accepted else "NULL", q(order_status),
            q(ORDER_NOTES[i % len(ORDER_NOTES)]) if accepted else "NULL",
            q(close_reason) if close_reason else "NULL",
            dt(created), dt(created), reporter,
        ])

    lines.extend(insert_block(
        "report",
        ["id", "report_no", "type", "point_id", "description", "file_ids", "reporter_id",
         "animal_id", "status", "close_reason", "contact", "created_at", "updated_at",
         "created_by"],
        report_rows,
    ))
    lines.extend(insert_block(
        "rescue_order",
        ["id", "order_no", "report_id", "animal_id", "type", "acceptor_id", "handler_id",
         "deadline_at", "accepted_at", "status", "dispose_note", "close_reason",
         "created_at", "updated_at", "created_by"],
        order_rows,
    ))

    # ---------- 就诊与 TNR ----------
    visit_rows = []
    for i in range(1, 11):
        animal_id = 156 + ((i - 1) % 8)
        visited = at_between(datetime(2026, 10, 1), datetime(2026, 11, 15))
        visit_rows.append([
            i, q(f"VS{DONATION_DAY}{i:03d}"), animal_id, "NULL", 1 + (i - 1) % 3, dt(visited),
            q("精神沉郁 / 右前爪外伤"), q("软组织挫伤，未见骨折"),
            q("清创、包扎，留院观察 24 小时"), 12000 + i * 700, json_literal([]),
            dt(visited), dt(visited), "@admin_a",
        ])

    lines.extend(insert_block(
        "visit",
        ["id", "visit_no", "animal_id", "order_id", "clinic_id", "visited_at",
         "complaint", "diagnosis", "treatment", "cost", "file_ids", "created_at",
         "updated_at", "created_by"],
        visit_rows,
    ))

    tnr_rows = []
    for i in range(1, 21):
        animal_id = neutered_ids[i - 1]
        occurred = at_between(datetime(2026, 9, 1), datetime(2026, 11, 10))
        stage = ["CATCH", "TRANSPORT", "SURGERY", "OBSERVE", "RELEASE"][i % 5]
        tnr_rows.append([
            i, q(f"TNR{DONATION_DAY}{i:03d}"), animal_id, q(stage), "@admin_a",
            dt(occurred), json_literal([]), q("TNR 流程记录，术后恢复良好。"),
            dt(occurred), dt(occurred), "@admin_a",
        ])

    lines.extend(insert_block(
        "tnr_task",
        ["id", "tnr_no", "animal_id", "stage", "handler_id", "occurred_at",
         "file_ids", "note", "created_at", "updated_at", "created_by"],
        tnr_rows,
    ))

    # ---------- 公告 ----------
    notice_rows = []
    for i in range(1, 11):
        category = ["PROGRESS", "PUBLICITY", "SCIENCE"][i % 3]
        published = datetime(2026, 9, 20, 10, 0) + timedelta(days=i * 3)
        notice_rows.append([
            i, q(f"NT-2026-{i:03d}"), q(category), q(NOTICE_TITLES[i - 1]),
            q(f"{NOTICE_TITLES[i - 1]}。本条目为演示数据，用于展示公告列表、置顶与详情页排版。"),
            "@sys_admin", 1 if i <= 2 else 0, dt(published), dt(published), dt(published), "@sys_admin",
        ])

    lines.extend(insert_block(
        "notice",
        ["id", "notice_no", "category", "title", "content", "author_id", "pinned",
         "published_at", "created_at", "updated_at", "created_by"],
        notice_rows,
    ))

    # ---------- 消息 ----------
    message_rows = []
    for i in range(1, 31):
        receiver = ["@stu_a", "@stu_b", "@stu_c", "@admin_a"][i % 4]
        typ = ["ORDER_PROGRESS", "DONATION_PROGRESS", "ADOPTION_EXPIRY", "APPROVAL_RESULT"][i % 4]
        title = {
            "ORDER_PROGRESS": "你上报的救助工单有新进展",
            "DONATION_PROGRESS": "募捐项目进度更新",
            "ADOPTION_EXPIRY": "认养计划即将到期",
            "APPROVAL_RESULT": "审核结果通知",
        }[typ]
        created = datetime(2026, 10, 15, 9, 0) + timedelta(hours=i * 7)
        message_rows.append([
            i, receiver, q(typ), q(title), q("PROJECT" if typ == "DONATION_PROGRESS" else "RESCUE_ORDER"),
            q(f"PRJ-{YEAR}-001" if typ == "DONATION_PROGRESS" else f"RO{DONATION_DAY}{i:03d}"),
            1 if i % 3 == 0 else 0, dt(created) if i % 3 == 0 else "NULL",
            dt(created), dt(created), "NULL",
        ])

    lines.extend(insert_block(
        "message",
        ["id", "receiver_id", "type", "title", "biz_type", "biz_id", "read_flag",
         "read_at", "created_at", "updated_at", "created_by"],
        message_rows,
    ))

    # ---------- 志愿任务 ----------
    add("""
INSERT INTO volunteer (user_id, status, available_slots, total_hours, audited_by, created_at, updated_at, created_by)
SELECT @stu_c, 'ACTIVE', '周三下午 / 周六上午', 18, @admin_a, NOW(), NOW(), @admin_a
WHERE NOT EXISTS (SELECT 1 FROM volunteer WHERE user_id = @stu_c);
""".strip())

    task_rows = []
    for i in range(1, 9):
        start = datetime(2026, 11, 22 + i, 9, 0)
        task_rows.append([
            i, q(f"TK-2026-{i:03d}"), q(["FEED", "TNR", "TRANSPORT", "CLEAN"][i % 4]),
            1 + (i - 1) % 12, dt(start), 4 + i, i % 3, q("OPEN" if i <= 6 else "CLOSED"),
            dt(datetime(2026, 11, 10, 9, 0)), dt(datetime(2026, 11, 10, 9, 0)), "@admin_a",
        ])

    lines.extend(insert_block(
        "task",
        ["id", "task_no", "type", "point_id", "start_at", "quota", "joined", "status",
         "created_at", "updated_at", "created_by"],
        task_rows,
    ))

    signup_rows = []
    signup_plan = []
    for task_id in range(1, 9):
        signup_plan.append((task_id, "@stu_a"))
        signup_plan.append((task_id, "@stu_b"))
    for task_id in range(1, 5):
        signup_plan.append((task_id, "@stu_c"))
    for i, (task_id, user_id) in enumerate(signup_plan, start=1):
        signed = datetime(2026, 11, 1, 9, 0) + timedelta(hours=i * 5)
        signup_rows.append([
            i, task_id, user_id, dt(signed), "NULL", "NULL", "NULL", 0,
            dt(signed), dt(signed), "NULL",
        ])

    lines.extend(insert_block(
        "task_signup",
        ["id", "task_id", "user_id", "signed_at", "checkin_at", "checkin_photo", "hours",
         "absent", "created_at", "updated_at", "created_by"],
        signup_rows,
    ))

    # ---------- 发号器对齐 ----------
    add("""
INSERT INTO seq_no (biz_type, seq_year, current_no) VALUES
('ANIMAL_CAT', 2026, 170),
('ANIMAL_DOG', 2026, 20),
('ANIMAL_OTHER', 2026, 10),
('GROUP', 2026, 5),
('PROJECT_2026', 2026, 12),
('DONATION_20261121', 2026, 1200),
('EXPENSE_20261121', 2026, 45),
('REPORT_20261121', 2026, 60),
('ORDER_20261121', 2026, 60),
('ADOPTION', 2026, 20)
ON DUPLICATE KEY UPDATE current_no = GREATEST(current_no, VALUES(current_no));
""".strip())

    add("SET FOREIGN_KEY_CHECKS = 1;")
    add("SELECT 'qywl-demo seeded: 200 animals / 12 projects / 1200 donations / 5 groups' AS result;")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default=str(Path(__file__).resolve().parents[2] / "demo" / "qywl-demo.sql"))
    args = parser.parse_args()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(build_sql(), encoding="utf-8")
    print(f"generated {output} ({output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
