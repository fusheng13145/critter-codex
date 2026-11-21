# 成员 B · 档案、救助与资金

> 本文只写 B 一个人要交付的东西，从《功能模块拆解与开发分工说明书》《功能分析说明书》《接口设计说明书》《数据库设计说明书》中抽出。分两部分：**基础模块**做完，三人合起来就能跑通一条完整演示链路；**后续模块**是基础达标之后再去加的体验项与治理项。

## 口径说明

- **基础模块**：一期、二期里构成主链路与资金闭环的部分，含"发现—建档—状态流转—捐赠入账—支出复核—结项—台账"。完成判据是"一条业务能从页面点进去，一路走到数据库和公示页"。
- **后续模块**：三期与增强项，主要是边界处理、批量工具、导出与数据治理。
- B 负责 D2 域全部 **52 个接口**：基础 32 个、后续 20 个。其中 `POST /donations`（学生捐赠登记）由 C 代写以复用表单组件，但表结构、入账校验与公开进度由 B 负责，B 必须验收。
- 三层数据模型（动物个体 / 群体 / 出现记录）物理分离，所有统计口径由 B 统一定义，A 只消费。

---

# 第一部分 · 基础模块（做完即可拿得出手）

## 1.1 基础范围与目标

| 覆盖模块 | 功能点 | 前端页面 | 主责数据表 |
|---|---|---|---|
| M02 生物档案（核心） | FR-A-01 建档、FR-A-02 编辑留痕、FR-A-03 检索、FR-A-04 状态流转、FR-A-05 健康记录、FR-A-09 群体档案、FR-A-12 物种出现记录 | P02 档案中心 | animal、animal_status_log、animal_change_log、health_record、animal_group、occurrence、base_point、base_species |
| M06 上报与工单 | FR-R-01 上报、FR-R-02 受理派单、FR-M-06 工单流转 | P06 上报、工单看板 | report、rescue_order |
| M04 募捐与资金 | FR-F-01 项目发起、FR-F-02 审核上架、FR-F-03 捐赠登记（C 代写）、FR-F-04 核对入账、FR-F-05 进度展示、FR-F-06 支出登记、FR-F-07 复核、FR-F-08 结项公示、FR-F-09 台账 | P04 资金 | project、project_budget、donation、expense、project_settlement |
| 数据口径 | FR-S-01/02 统计口径定义 | P09 统计（A 渲染） | 三个统计视图 |

**B 的基础目标**：一只动物从上报被发现、建档、状态变化，到资金挂靠，每一笔钱都能回答"谁捐的、花在哪只动物身上、谁复核的"，结项后公示页只读。

## 1.2 基础接口清单（32）

### 生物档案与基础数据（15）

| # | 方法 | 路径 | 用途 | 请求关键字段 | 响应关键字段 | 主要错误码 |
|---|---|---|---|---|---|---|
| 1 | GET | /animals | 档案筛选列表 | keyword、status、pointId、neutered、adopted、page/size | list、page | 30303 枚举非法 |
| 2 | POST | /animals | 建档并发号 | category、gender、color、features、pointId、photos | code、status | 30301 必填缺失、30411 编号重复 |
| 3 | GET | /animals/{code} | 档案详情 | code | 档案全字段（按角色裁剪） | 30410 不存在 |
| 4 | PUT | /animals/{code} | 编辑并留痕 | 变更字段 + reason(≥5 字) | 更新后的档案 | 30401 未填原因 |
| 5 | PUT | /animals/{code}/status | 状态迁移 | targetStatus、reason、relatedOrderNo | 新状态、allowed[] | 30402 非法迁移 |
| 6 | GET | /animals/{code}/history | 字段级变更历史 | code | before/after 列表 | 30410 |
| 7 | GET | /groups | 群体列表 | status、speciesId | list + page | — |
| 8 | POST | /groups | 群体建档 | speciesId、features、pointIds、sizeMin/Max、sizeBasis | groupCode | 30504 区间非法 |
| 9 | GET | /groups/{code} | 群体详情 | code | 规模区间、目击时间线 | 30410 |
| 10 | GET | /species | 物种名录 | keyword、category | list | — |
| 11 | GET | /occurrences | 出现记录列表 | speciesId、pointId、season | list | — |
| 12 | POST | /occurrences | 登记出现记录 | speciesId、pointId、occurredAt、scale | occurrenceId | 30303 scale 非法 |
| 13 | GET | /points | 点位字典（含平面图坐标） | — | points[] | — |
| 14 | GET | /animals/{code}/health | 健康与防疫记录 | code | 疫苗、驱虫、体重、病史 | 30410 |
| 15 | POST | /animals/{code}/health | 新增健康记录 | code、type、recordedAt、result、nextDate | healthRecordId | 30301 |

### 上报与工单（7）

| # | 方法 | 路径 | 用途 | 请求关键字段 | 响应关键字段 | 主要错误码 |
|---|---|---|---|---|---|---|
| 16 | POST | /reports | 学生提交上报 | type、pointId、photos、description(≥10 字) | reportNo、deadline、duplicated | 30301、30503 野猪提示 |
| 17 | GET | /reports | 待办列表（含超时高亮） | status、type、from、to | list + page | — |
| 18 | POST | /reports/{id}/accept | 受理并转工单 | animalCode、handlerId、deadline | orderNo | 30402 |
| 19 | GET | /orders | 工单列表 | status、handlerId | list + page | — |
| 20 | GET | /orders/{no} | 工单详情与时间线 | orderNo | 状态时间线、处置说明 | 30410 |
| 21 | PUT | /orders/{no}/status | 工单状态推进 | targetStatus、disposeNote | 新状态 | 30602 非法 |

### 募捐、捐赠、支出与结项（10）

| # | 方法 | 路径 | 用途 | 请求关键字段 | 响应关键字段 | 主要错误码 |
|---|---|---|---|---|---|---|
| 22 | GET | /projects | 项目列表（含进度） | status、page | 已筹、目标、进度 | — |
| 23 | POST | /projects | 发起项目 | name、purpose、targetAmount、budget[]、起止日期 | projectNo、status=DRAFT | 40101 预算超目标 |
| 24 | GET | /projects/{no} | 项目详情 | projectNo | 预算、进度、捐赠流水（脱敏） | 30410 |
| 25 | PUT | /projects/{no}/audit | 审核上架 | decision、reason | status=FUNDING/REJECTED | 20003 |
| 26 | POST | /donations | 学生捐赠登记（C 代写，B 验收） | projectNo、amount、anonymous、voucherFileIds | donationNo、status=PENDING_VERIFY | 40102 金额非法 |
| 27 | PUT | /donations/{no}/confirm | 核对入账 | amount、paidAt、voucherFileIds | verifiedAt、项目进度 | 40106 未入账不得计进度 |
| 28 | POST | /expenses | 支出登记 | projectNo、animalCode、purpose、amount、voucherFileIds | expenseNo、status=PENDING_REVIEW | 40103 余额不足（返回差额） |
| 29 | GET | /expenses | 支出列表 | status、projectNo | list + page | — |
| 30 | PUT | /expenses/{no}/review | 支出复核 | decision、reason、secondReviewerId | status、项目余额 | 40104 自我复核 |
| 31 | PUT | /projects/{no}/settle | 提交结项并公示 | reportText、balanceTo、attachments | status=SETTLED | 40105 有未复核支出 |
| 32 | GET | /ledger | 资金台账汇总 | projectNo、from、to | 收入、支出、结余、逐笔流水 | 20002 |

字段与报文样例见《接口设计说明书》§7.9（支出登记）与 §7.10（复核）。

## 1.3 基础模块的硬规则

| 规则 | 落地位置 | 验收方式 |
|---|---|---|
| 编号"类别-年份-流水"连续不重号 | 序列表 + 行锁发号 | 连续建三只猫，编号不跳不重 |
| 状态只能按迁移表走 | `TransitionEngine` 查表 | 已离世改治疗中被拒，返回 allowed[] |
| 每次编辑必填原因、字段级留痕 | 服务层校验 + animal_change_log | 改一处体重可查到前后值 |
| 上报 24 小时未受理转 TIMEOUT | 工单 deadline_at + 每小时扫描 | 改时间模拟，待办高亮 |
| 三层数据分开统计 | 三个视图 | 不会把"三只黄鼠狼"算进在册猫狗 |
| 预算不超目标 | 项目提交时校验 | 3200/3000 被拒（40101） |
| 登记 ≠ 入账 | donation 两态分离 | 未入账时公开进度不变 |
| 支出必须挂项目与动物 | NOT NULL + 服务层 | 缺动物编号无法保存 |
| 余额不能为负 | 事务内 `SELECT ... FOR UPDATE` | 余额 300 支出 500 被拒并提示差额 200 |
| >500 元双人复核、不得自审 | 服务层 + CHECK 约束 | 同一账号复核自己登记的单返回 40104 |
| 资金只增不删 | 收回 DELETE 权限 + 冲正字段 | 删除流水返回 40107 |
| 结项前无未复核支出 | 状态机前置校验 | 有待复核单时返回 40105 |
| 公示只读 | 只暴露追加接口，不暴露 update | 已公示正文无法编辑 |

## 1.4 基础完成后的演示方式

1. 学生提交一条受伤上报 → 管理员受理转工单 → 工单推进到待回访。
2. 从工单关联动物建档 → 改一次状态 → 打开变更历史看前后值。
3. 发起"秋季绝育专项"，预算 3000 元；学生捐赠 200 元，公开进度仍是 0；管理员核对入账后变 200。
4. 登记一笔 520 元支出 → 第一人复核只记第一签 → 第二人复核才计入支出、余额下降。
5. 打开台账与结项公示页，结项后公示只读；统计页资金总览与台账合计一致。

## 1.5 基础验收清单

- [ ] 上报到工单、工单到档案、档案到资金，全链路点得通。
- [ ] 建档编号连续、非法状态迁移被拒、变更历史可查。
- [ ] 捐赠登记与入账在页面上分列显示。
- [ ] 超余额支出提示差额；超额支出必须两人复核；自我复核被拒。
- [ ] 结项后公示页不可编辑，只能追加说明。
- [ ] 台账合计 = 已入账捐赠 − 已复核支出，与统计页一致。

---

# 第二部分 · 后续可增加的模块

按优先级从上往下做。

| 优先级 | 功能点 / 事项 | 对应接口 | 价值 | 前置条件 |
|---|---|---|---|---|
| 中 | 上报详情与无效上报关闭 | GET /reports/{id}、PUT /reports/{id}/close | 清理噪声上报，效率统计更准 | 效率统计口径已定 |
| 中 | 重复档案合并 | POST /animals/{code}/merge | 治理重复建档 | 变更历史完备 |
| 中 | 二维码挂牌 | GET /animals/{code}/qrcode | 校园挂牌扫码看档案 | 公开页字段白名单已定 |
| 中 | 群体编辑与目击归属 | PUT /groups/{code}、POST /groups/{code}/sightings、GET /sightings、PUT /sightings/{id} | 群体数据真实可信 | 群体建档已用起来 |
| 中 | 群体合并与拆分 | POST /groups/merge、POST /groups/{code}/split | 群体动态维护 | 目击记录已积累 |
| 中 | 点位与物种维护 | POST /points、PUT /points/{id}、POST /species、PUT /species/{id} | 不再靠 SQL 改基础数据 | 字典页面已可用 |
| 中 | 结项公示详情 | GET /projects/{no}/settlement | 公示页独立访问、可分享 | 结项功能已上线 |
| 中 | 台账导出 | GET /ledger/export | 答辩材料可导出归档 | 台账数据正确 |
| 低 | 项目草稿编辑 | PUT /projects/{no} | 驳回后可改再提 | 审核流已跑顺 |
| 低 | 捐赠列表、异常标记与凭证 | GET /donations、PUT /donations/{no}/flag、GET /donations/{no}/certificate | 捐赠侧体验完整 | 凭证模板确定 |

以上 20 个接口加上基础 32 个，正好是 D2 的 52 个接口。附件审核 `PUT /files/{fileId}/audit` 属于 A 的 D1 域，B 只提供"公开照片审核通过才展示"的业务规则，不重复计入。

## 后续模块的实现提醒

- 合并档案时，副编号要保留为可检索别名，不能物理删记录；有生效认养关系的档案禁止合并（30403）。
- 点位移除前先查引用数量，被引用时只能停用不能删。
- 群体规模区间必须满足 `sizeMin ≤ sizeMax`，并写清计数依据（如"夜间计数 3 次取中位"）。
- 导出超过 5 万行改异步，立即返回 taskId。
- 所有资金更正用冲正记录（`reversal_of`），不允许出现 `DELETE FROM donation/expense`。

## 与其他两人的接口边界

| 对象 | B 提供 | B 消费 |
|---|---|---|
| A | 统计视图与三个统计接口的数据、档案与资金查询接口 | 字典、附件上传、令牌与角色判断、日志写入入口 |
| C | 动物状态查询与状态流转接口、项目进度与余额查询 | 认养状态、就诊与 TNR 记录、消息通道（进展反馈） |

B 不直接改认养表、就诊表与消息表；需要数据时调用 C 的接口。资金与档案是 B 唯一可写的数据域。

## 后续增强接口实现记录（2026-11-21）

| 能力 | 接口 | 实现口径 |
|---|---|---|
| 重复档案合并 | `POST /animals/{code}/merge` | 主编号在路径，请求 `sourceCodes`（≤20）+ `reason`（≥5 字）；副档不逻辑删除、不物理删除，写 `merged_into_id/merged_at`；副编号写 `animal_alias`，详情/搜索/二维码解析到主档；健康记录与变更历史迁主档；存在 `PENDING_PAYMENT`/`ACTIVE` 认养时返回 30403 |
| 档案二维码 | `GET /animals/{code}/qrcode` | `image/png`，300×300，UTF-8，纠错 M，margin 1；内容为 `critter.qrcode.animal-url-prefix` + 编号，默认 `http://localhost:5173/animals/` |
| 目击归属修正 | `PUT /sightings/{id}` | `pending=true` 清空为 `UNASSIGNED`；否则按 `groupId`/`groupCode` 置 `ASSIGNED` 并刷新群体 `last_seen_at`；已消失/已合并群体返回 30505 |
| 群体合并 | `POST /groups/merge` | 仅同物种、主副群体非 GONE；主群体合并点位、规模区间、最近目击时间与 `mergedFrom`；副群体置 `GONE` + `mergedIntoCode`；目击迁主群体 |
| 群体拆分 | `POST /groups/{code}/split` | 新群体由服务端发号；请求显式给新群体/源群体规模区间与计数依据；可迁移指定目击记录 |
| 点位维护 | `POST/PUT /points` | 坐标 0–100；名称唯一；`enabled=false` 停用不删除 |
| 物种维护 | `POST/PUT /species` | 俗名唯一；支持学名、类别、保护级别、备注 |
| 台账导出 | `GET /ledger/export`、`GET /ledger/export/{taskId}`、`GET .../download` | 复用 `LedgerService` 口径，xlsx 含汇总与逐笔流水；≤5 万行同步，超阈值异步返回 taskId |
| 凭证异常 | `PUT /donations/{no}/flag` | 仅 `PENDING_VERIFY`/`ABNORMAL` 可标记；`VERIFIED` 拒绝并提示先冲正；写 `abnormal_reason`、可替换凭证、通知实名捐赠人、审计 `DONATION_FLAG` |
| 捐赠证书 | `GET /donations/{no}/certificate` | 仅 `VERIFIED`；实名本人或 `ANIMAL_ADMIN`/`SYS_ADMIN` 可下载；PDFBox 横版 A4，含捐赠人、项目、金额、单号、证书编号、入账日期 |
| 公示追加说明 | `POST /projects/{projectNo}/settlement/notes` | 仅 `ANIMAL_ADMIN`；只能追加，不能改公示正文；`ProjectSettlementView` 末尾返回 `appendNotes` |
| 野猪拦截 | `POST /reports` | 可选 `speciesId`；俗名含“野猪”或学名 `Sus scrofa` 返回 `30503 WILDLIFE_FORBIDDEN`，不落上报、不生成工单 |
| 疫苗提醒 | `HealthReminderJob` | 每天扫描 `VACCINE` 且 `next_date ∈ [今天, 今天+3]` 的记录，按 `operatorId` 投递 `HEALTH_REMINDER`，`biz_id=健康记录 id` 幂等去重 |

数据库变更集中在 `V19__p1_merge_alias_and_extensions.sql`：`animal` 加合并字段、新建 `animal_alias`、`animal_group.merged_into_code`、`report.species_id`、`donation.abnormal_reason`、`project_settlement_note`。
