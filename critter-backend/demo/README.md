# 青檐万灵册演示数据集

本目录提供可重复生成的演示数据，用于答辩、UI 走查和联调。数据全部由
`scripts/demo/generate_demo_data.py` 以固定随机种子生成，金额单位为“分”，
与后端接口契约一致。

## 数据构成

| 数据 | 数量 | 说明 |
|---|---:|---|
| 动物档案 | 200 | 170 猫、20 犬、10 其他；覆盖在校园、治疗、住院、待放归、放归、失踪、死亡、已领养 |
| 动物群体 | 5 | 松鼠、黄鼬、刺猬、白头鹎、珠颈斑鸠；含目击记录 50 条 |
| 物种出现记录 | 30 | 覆盖 8 个物种、12 个校园点位 |
| 募捐项目 | 12 | 7 个 FUNDING、1 个 PENDING_SETTLE、1 个 SETTLED、1 个 ENDED、1 个 DRAFT、1 个 PENDING_AUDIT |
| 项目预算 | 36 | 每个项目 3 条预算，合计不超过目标金额 |
| 捐赠 | 1200 | 900 VERIFIED、250 PENDING_VERIFY、50 ABNORMAL |
| 支出 | 45 | 27 APPROVED、9 FIRST_APPROVED、5 PENDING_REVIEW、4 REJECTED |
| 上报 / 工单 | 60 / 60 | 覆盖 PENDING、PROCESSING、TO_REVISIT、CLOSED、TIMEOUT |
| 认养 | 20 | 12 ACTIVE、4 EXPIRED、2 PENDING_PAYMENT、1 CLOSED、1 REJECTED |
| 健康记录 | 160 | 体重、疫苗、驱虫、病史 |
| 结项公示 | 1 | `PRJ-2026-009` 已 SETTLED，可匿名查看公示 |
| 公告 / 消息 | 10 / 30 | 用于首页、公告与消息中心走查 |
| 志愿任务 / 报名 | 8 / 20 | 用于志愿页面走查 |

资金恒等式：

```text
project.raised_amount = SUM(VERIFIED donation.amount)
project.spent_amount  = SUM(APPROVED expense.amount)
```

导入脚本会自动回填这两列，并在结束时检查 `fund_mismatch=0`。

## 一键重置

在 `critter-backend` 目录执行：

```powershell
# 联调库（默认）
.\scripts\demo\reset-demo.ps1 -Database critter_codex_dev -Force

# 主库（答辩演示）
.\scripts\demo\reset-demo.ps1 -Database critter_codex -Force
```

脚本会清空业务表，保留账号、角色、字典、点位、物种和医院；只应对演示库执行。
默认数据库账号 `root/1234`，端口 `127.0.0.1:3306`，可用参数覆盖。

只重新生成 SQL、不导入：

```powershell
python .\scripts\demo\generate_demo_data.py --output .\demo\qywl-demo.sql
```

## 演示账号

| 角色 | 学号 | 密码 | 用途 |
|---|---|---|---|
| 学生 | `2023010173` | `Qy@2026` | 上报、我的上报 |
| 动物管理员 A | `2023010101` | `Admin@2026` | 状态迁移、支出登记、结项 |
| 动物管理员 B | `2023010111` | `Admin@2026` | 大额支出第一签 |
| 动物管理员 C | `2023010112` | `Admin@2026` | 大额支出第二签 |
| 系统管理员 | `2023010102` | `Admin@2026` | 用户、字典、审计 |

> B/C 是为了演示 >500 元支出的双人复核而新增的演示账号，不改变既有账号。

## 已有数据库的 Flyway 历史修复

如果数据库是从早期分支升级过来的，可能残留已被 `V11` 取代的 `V2` 记录，
或残留旧分支的 `V14__expense_double_sign_stage`。Flyway `repair` 的语义是把
这些“本地已不存在的迁移”标记为 `DELETE`。本机演示库可执行：

```sql
UPDATE flyway_schema_history SET type = 'DELETE' WHERE version = '2';
-- 仅当历史里仍存在旧分支的 V14 expense 时：
UPDATE flyway_schema_history SET type = 'DELETE' WHERE version = '14';
```

全新数据库直接执行 `flyway migrate` 即可，不需要这段修复。

## 已知边界

- 动物封面为空，前端显示占位图；这是为了避免演示数据依赖外部图片资源。
- 捐赠中 1200 笔含 250 笔待核对、50 笔异常，资金汇总只统计 900 笔 VERIFIED。
- 项目 8/9 的已筹金额高于目标金额，用于展示“超额达标”与结项公示；项目 10 为到期未达标结束。
- 演示数据只覆盖业务表；系统日志、文件表不导入。
