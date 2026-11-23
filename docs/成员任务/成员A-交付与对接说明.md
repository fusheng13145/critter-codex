# 成员 A 交付说明 · 身份、后台与数据看板

> 本文给 B、C 看：A 交了什么、怎么起、前端怎么接、哪些地方与文档有偏差。
> 契约基线：`docs/md/03 接口设计说明书.md` v1.0-frozen；数据基线：`docs/md/04 数据库设计说明书.md`。
> 代码分支：`unifyed`（已快进合并 `origin/main`，当前含 B 的 T1.2/T1.4/T1.5；迁移到 V7）。

## 1 怎么跑起来

| 项 | 值 |
|---|---|
| 后端 | `critter-backend`，Spring Boot 3.5.16 + MyBatis-Plus 3.5.18.1，端口 **8080** |
| 数据库 | MySQL `127.0.0.1:3306/critter_codex`，`root/123456`（`application.yaml` 已改；换机器只改这一处） |
| 缓存 | 本机无 Redis：`critter.cache.mode: memory`（进程内）。装了 Redis 改成 `redis` 即切换，业务代码不用动 |
| 前端 | `critter-frontend`，Vue 3 + Vite + Pinia + Element Plus，`npm run dev` → **5173**，`/api` 代理到 8080 |
| 附件目录 | `critter-backend/data/files/yyyy/MM/`（已在 `.gitignore` 内，不要提交） |

启动顺序：MySQL → 后端（Flyway 自动建表/补数据）→ 前端。后端起来后先访问 `GET /api/v1/ping` 确认 `cache` 是 `memory` 还是 `redis`。

```bash
# 后端
cd critter-backend && mvn spring-boot:run
# 前端
cd critter-frontend && npm install && npm run dev
```

演示账号（`V2__seed_identity.sql` / `V3__stats_views_and_demo.sql` 已播种，密码为 BCrypt 哈希）：

| 角色 | 学号 | 密码 |
|---|---|---|
| 注册学生 | `2023019001` | `Qy@2026` |
| 动物管理员 | `2023010101` | `Admin@2026` |
| 系统管理员 | `2023010102` | `Admin@2026` |
| 认养人（叠加） | `2023010103` | `Student@2026` |
| 志愿者（叠加） | `2023010104` | `Student@2026` |
| 已禁用（验证 30104） | `2023010105` | `Student@2026` |

> 库里另有 B 早期种子账号（`9000000001` / `9000000002`），密码以 B 的说明为准，A 未改动。

## 2 后端交付清单

### 2.1 基础 17 个接口（全部实现并逐条联调）

| # | 方法 | 路径 | 权限 |
|---|---|---|---|
| 1 | POST | `/auth/register` | 公开 |
| 2 | POST | `/auth/register/verify` | 公开 |
| 3 | POST | `/auth/login` | 公开 |
| 4 | POST | `/auth/refresh` | 公开（凭 Cookie） |
| 5 | POST | `/auth/logout` | 登录 |
| 6 | GET | `/users/me` | 登录 |
| 7 | PUT | `/users/me` | 登录 |
| 8 | PUT | `/users/me/password` | 登录 |
| 9 | POST | `/users/me/applications` | 登录 |
| 10 | GET | `/roles` | 系统管理员 |
| 11 | GET | `/dicts?type=A,B` | 登录 |
| 12 | POST | `/files` | 登录 |
| 13 | GET | `/files/{fileId}` | 登录 |
| 14 | GET | `/logs` | 系统管理员 |
| 15 | GET | `/stats/biology` | 登录 |
| 16 | GET | `/stats/fund` | 管理员（动物/系统） |
| 17 | GET | `/stats/efficiency` | 管理员（动物/系统） |

### 2.2 额外先做的接口（基础验收会用到，取自后续清单）

| 方法 | 路径 | 用途 | 为什么现在做 |
|---|---|---|---|
| GET | `/users/me/applications` | 我的身份申请 | 个人中心要把"待审/已通过"列出来 |
| GET | `/users/me/login-logs` | 本人登录日志 | FR-U-05 验收项 |
| GET | `/users` | 用户列表（分页/筛选） | 后台用户页与日志按人检索 |
| PUT | `/users/{id}/status` | 禁用/启用 | 禁用须同步作废令牌，属基础治理 |
| GET | `/users/applications` | 待审申请列表 | 申请-审批闭环 |
| PUT | `/users/applications/{id}` | 审批通过/驳回 | 同上 |
| GET | `/dicts/manage` | 含停用项的字典 | 后台字典页 |
| POST | `/dicts`、PUT | `/dicts/{id}` | 新增/改名/停用 | 1.4 演示第 3 步"后台改字典"要求 |
| PUT | `/files/{fileId}/audit` | 审核公开照片 | 公开页"先审后发" |
| GET | `/files/{fileId}/content` | 文件字节流 | `<img src>` 需要二进制，元信息接口返回 JSON |
| GET | `/api/v1/ping` | 健康检查 | 前端判断后端是否在线 |

### 2.3 硬规则落地位置

| 规则 | 代码位置 | 验证方式（已实测） |
|---|---|---|
| 学号 10 位 + 校内邮箱双校验 | `AuthService.register` | 格式错 → 30301 字段错误；名单外 → 30202；外校邮箱 → 30301 |
| 连续 5 次密码错锁定 10 分钟 | `AuthService.recordFailure`（账号 + IP 双计数） | 第 5 次 30101 且提示上限，第 6 次 30103 + `lockSeconds` |
| 密码 BCrypt 加盐 | `PasswordHasher` | 库中 `password_hash` 为 `$2a$10$...` |
| 双令牌 2h / 7d | `JwtService` + `AuthController` 写 httpOnly Cookie | 刷新令牌在 Cookie，访问令牌在内存 |
| 改密后旧令牌失效 | `AuthService.changePassword` 写 `auth:min-iat:{uid}`，拦截器比对 `iat` | 改密后旧 accessToken → 401 / 30102 |
| 注销拉黑 | `AuthService.logout` 写 `auth:black:{jti}` | 注销后同令牌 → 401 / 30102 |
| 手机号 AES 加密 + 脱敏 | `AesCipher`（AES/GCM，随机 IV） | 列表接口只回 `phoneMasked`；`/users/me` 本人可见全量 |
| 系统管理员无业务审核权 | `MenuMatrix` 常量：`SYS_ADMIN` 的接口集合不含 `adoption:audit`/`expense:review` | `GET /roles` 的矩阵可直接看到 |
| 两层鉴权 | 接口层 `AuthInterceptor` + `@RequireRoles`；服务层动作白名单在 `MenuMatrix` | 学生查日志 → 403 / 20003 |

### 2.4 统计口径

- 生物：`v_animal_overview`（排除软删与已离世）+ `animal.adopted` 计认养覆盖率；群体与出现记录分别计数，**不与个体相加**。
- 资金：`v_project_fund`；接口内自检 `raised − spent = balance`，不成立直接 50001（数据库设计 §7 的硬规则）。
- 效率：上报/工单按 `created_at` 落在区间内计数，平均受理时长取 `accepted_at − created_at` 分钟均值。
- 日期区间：`from`/`to` 为 `yyyy-MM-dd`，`to` 当天计入。

### 2.5 本期新增的 5 个接口（第二部分）

| # | 方法 | 路径 | 权限 | 关键规则 |
|---|---|---|---|---|
| 1 | POST | `/auth/password/code` | 公开 | 验证码 5 分钟有效，走 `KeyValueStore` 键 `pwd:code:{学号}`；禁用账号不发码（30104），只能走申诉 |
| 2 | PUT | `/auth/password` | 公开 | 凭码重置；验证码错 30105 且计数与登录共用（账号 + IP），5 次锁 10 分钟 30103；成功写 min-iat 让旧令牌失效 |
| 3 | POST | `/users/me/appeals` | 公开 | 账号不存在 30410、非禁用态 30402、已有待处理申诉幂等返回原记录；落 `sys_appeal` |
| 4 | PUT | `/roles/{id}/permissions` | 系统 | `preview=true` 只回差异不落库；系统管理员必须保留 `menu:admin` + `role:manage`，否则 30301；SYS_ADMIN / SYSTEM_ADMIN 双码同步 |
| 5 | GET | `/stats/export` | 管理员 | 行数 ≤ 阈值同步回 xlsx；超过则回 `{taskId, pollUrl}`，轮询 `GET /tasks/export/{taskId}`，下载走 `GET /stats/export/{taskId}/download` |

配套：

- 登录菜单与 `/users/me` 的 `menus` 改为**读 `sys_role_permission`**（DB 优先，某角色一条记录都没有时回退 `MenuMatrix` 常量）。改完权限后该角色下次登录立即生效。
- 新增 `GET /tasks/export/{taskId}`（异步任务查询），路径按 03 §8 的约定预留。
- 修掉一个既有缺陷：登录失败计数此前用 `store.get(ip 键)` 读值，而内存实现自增后 `get` 返回空，**导致 IP 维度从未真正锁定**；改为取 `increment` 返回值，账号与 IP 双维度都生效。

### 2.6 本期迁移脚本

| 版本 | 内容 | 注意 |
|---|---|---|
| V5 | 原 B 的 `V2__seed_reference_data.sql` 改名，**内容一行未改** | 与本方 `V2__seed_identity.sql` 重号会让 Flyway 拒绝启动 |
| V6 | `dict_type` 统一大写 + 按 (dict_type, label) 去重 | 兼容 `DictService` 只接受大写类型 |
| V7 | 新建 `sys_role_permission`（对齐本机既有结构）+ `sys_appeal`；补 `report:read` 权限点；按当前矩阵回填角色权限（先清空该表再播） | 该表是派生配置，回填后行为与改前完全一致 |

## 3 前端交付与接入方式

### 3.1 已实现页面

| 页面组 | 路由 | 说明 |
|---|---|---|
| P00 登录注册 | `/login` | 登录 / 注册 / 验证码三步；注册含学号与邮箱前端预校验；新增"忘记密码"（发码→重置）与"账号被禁用？申诉"两个入口 |
| P01 首页 | `/` | 指标卡、近半年新增、待办（管理员）、身份与菜单（学生）、后台动态（系统管理员） |
| P09 统计 | `/stats` | 生物/资金/效率三块 + 项目明细 + 区间筛选；"导出报表"接真实接口，>5 万行自动转异步并轮询 |
| P10 后台 | `/admin` | 四个页签：操作日志 / 用户与申请 / 数据字典 / 角色权限（角色页可在线改：勾选 → 预览差异 → 确认保存） |
| P11 个人中心 | `/me` | 资料、改密、身份申请、我的申请、**账号申诉**、登录日志 |

**B/C 的页面已留占位路由**（`/animals` `/adopt` `/report` `/fund` `/medical` `/tasks` `/messages`），占位页列出该页契约接口与共享资产，接上自己的视图即可。侧栏菜单、面包屑、权限判断全部由后端 `menus` 驱动，**不要在前端写死角色判断**。

### 3.2 共享资产（直接用，不要另起一套）

| 资产 | 路径 | 用法 |
|---|---|---|
| 设计令牌与组件样式 | `src/styles/theme.css` | `.card` `.metric` `.tag` `.btn` `.table-wrap` `.progress` `.list` 等，颜色只用 CSS 变量 |
| 请求层 | `src/api/http.ts` | `get/post/put/del` 已解包 `data`；失败抛 `BizError(code,message,data)`；401 自动静默刷新一次 |
| 会话 | `src/stores/auth.ts` | `useAuthStore()`：`user` `menus` `isAdmin` `hasMenu()`；刷新页面自动恢复会话 |
| 格式化 | `src/utils/format.ts` | `money(分)` `dateTime` `statusLabel` `roleLabel` |
| 组件 | `src/components/` | `PageHero` `PanelCard` `MetricCard` `BarChart` |
| 字典 | `src/api/dict.ts` | `fetchDicts(['ANIMAL_COLOR','POINT'])` |
| 附件 | `src/api/file.ts` | `uploadFile(file, 'ANIMAL_PUBLIC')` → `fileId` + `contentUrl` |

新增一个页面的标准动作（4 步）：

1. `src/views/` 下建视图组件；
2. `src/router/index.ts` 把对应占位路由的 `component` 换掉（`meta.menu` 保持不变）；
3. `src/api/` 下加接口函数，走 `http.ts`，不要自己 new axios；
4. 菜单若需新增，改后端 `MenuMatrix.MENUS` 与角色菜单集合，前端自动出现。

## 4 与文档的偏差（都已同步说明，不是漏做）

| 项 | 文档 | 实现 | 原因 |
|---|---|---|---|
| 附件访问 | `url` = `/api/v1/files/{fileId}` | 元信息仍是该地址，二进制在 `/files/{fileId}/content` | 一个地址不能同时返回 JSON 与图片；`<img src>` 需要后者 |
| 刷新令牌 | 响应体含 `refreshToken`，正文又说存 httpOnly Cookie | 两者都给（`critter.security.expose-refresh-token` 可关），Cookie 带 `HttpOnly` | 联调工具需要直接取；生产可只走 Cookie |
| 待验证账号登录 | 未定义码 | 30104 + "账号尚未激活，请先完成邮箱验证码校验" | 30104 语义最接近，避免新增码 |
| 邮箱域名不符 | 未定义码 | 30301 + 字段错误 `email` | 属表单校验，走字段级提示更合适 |
| 密码强度 | 只写"加盐哈希" | 8—32 位且同时含字母与数字 | 教学项目的最低可辩护强度，前端同步提示 |
| 系统管理员码 | §10 为 `SYS_ADMIN`，代码 `Role` 枚举为 `SYSTEM_ADMIN` | 两码等价（`MenuMatrix.isSystemAdmin`、`@RequireRoles` 同时列） | 库里两套种子都在用，不能只认一个 |
| 统计认养数 | 只写"走 B 的视图" | 取 `animal.adopted`，不查 `adoption` 表 | C 域 `adoption` 表尚未建，直接查会让统计整体 500；该字段与列表同源 |
| 角色权限在线配置 | `PUT /roles/{id}/permissions` | 已实现：V7 建 `sys_role_permission`，矩阵以库为准（无记录回退常量），改完即时生效 | 预览差异为同一接口的 `preview=true`，不额外加路径 |
| 统计导出响应 | `GET /stats/export` 未写同步返回形态 | 阈值内直接回 xlsx 二进制（不走统一信封），超阈值回 `{taskId, pollUrl}`；另加 `GET /stats/export/{taskId}/download` | 二进制无法塞进 `{code,data}`；下载地址需要一个实际接口 |
| 目击记录鉴权 | `/sightings` 标注"登录" | 暂并入"可选鉴权"（WebConfig.OPTIONAL_AUTH）：有令牌解析身份，无令牌放行 | B 的控制器仍用 `X-User-Id` 占位且用例匿名调用；待 B 切到令牌后从该表移除 |
| 字典类型命名 | 未规定命名 | 大小写已由 V6 归一到大写；但 B 的 `gender`/`coat_color`/`expense_category` 与 A 的 `ANIMAL_GENDER`/`ANIMAL_COLOR`/`EXPENSE_PURPOSE` 是**不同名**，会并存为两组 | 只做大小写归一不改语义映射，避免改坏 B 已用的 key；建议后续统一 |
| 演示账号密码 | 无 | 种子密码 `Qy@2026` 只有 7 位，不满足 8 位强度；重置密码接口会拒绝它 | 演示"找回密码"请重设为 8 位以上（如 `Qy@2026x`），或直接改库还原 |

## 5 需要 B、C 注意的几件事

1. **Flyway 历史需要 repair（重要）**：B 的 `V2__seed_reference_data.sql` 已改名为 `V5`（两条 V2 会让 Flyway 拒绝启动）。**B 本地库执行过旧 V2 的话，会看到 `Validate failed: Detected resolved migration not applied / description mismatch`**，两种处理：① 重建库（推荐，脚本已能一次跑完 V1→V7）；② 手工执行 `DELETE FROM flyway_schema_history WHERE version='2' AND script='V2__seed_reference_data.sql';` 后重启（相当于 `flyway repair`），再让 V5 执行一次。
2. **本机库与 `V1__init.sql` 不一致**：本机是历史库，Flyway 以 baseline 接管，`V1__init.sql` 从未执行，缺 20 张表（`base_species`/`base_clinic`/`sighting`/`health_record`/`adoption` 等）。A 已在本机按 V1 的 `CREATE TABLE IF NOT EXISTS` 补齐（并把 `base_point.id`、`adoption_plan.id` 从 INT 对齐到 BIGINT，否则外键类型冲突），现 43 张表。**新克隆的库直接跑 Flyway 即可，不要再手工建表**；`sys_user_role.created_by` 这类字段差异仍然存在，要彻底统一得和 B 一起重构 V1。
3. **V7 会重置 `sys_role_permission`**：该表是派生配置（可从 `MenuMatrix` 重建），V7 先清空再按当前矩阵回填；如果 B/C 本地改过这张表，升级后会被覆盖。
4. **`adoption` 表**：本机已随 V1 补齐，`GET /stats/efficiency` 的 `pendingAdoptions` 改为读真实数据；缺表环境仍降级为 0（`StatsService.safePendingAdoptions`）。
5. **A 不写 B/C 的表**：除 `V2/V3` 的字典、角色、学籍、账号种子外，A 的代码对 `animal`、`project`、`adoption`、`report`、`rescue_order` 等只读。统计接口不缓存，B/C 改数据后刷新即变。

## 6 测试与验证

- 后端：`mvn test` → **159 例全绿**（合并 B 的 54 例后基线 135；本期新增 24：找回密码 7、申诉 4、角色权限 10、导出 3）。
- 接口：curl 逐条走过注册→验证→登录→刷新→注销、改密失效、锁定、越权 403、字段错误 30301、字典增改即时生效、附件上传/审核/匿名访问控制、三个统计接口。
- 本期接口 curl 实测（正常 + 两条失败分支）：找回密码（30105/30410/30301、5 次锁定 30103、重置后新密码可登录且旧令牌失效）、申诉（30402/30410/幂等）、角色权限（30301 自锁拦截、预览不落库、落库后登录菜单即时变化、学生越权 20003）、导出（200 + `application/vnd...sheet` + 6390 字节 + 四张 sheet；学生 20003）。
- 前端：`npm run build`（`vue-tsc` 类型检查 + 打包）通过；headless（Edge + puppeteer-core）走查"忘记密码→重置→申诉→系统管理员登录→后台角色页预览差异→统计页导出→个人中心申诉卡片"，控制台无报错；导出请求实测 200 且 MIME 正确（无头模式下 blob 不落磁盘，属浏览器行为，非前端缺陷）。

## 7 结论审查整改（2026-11-23）

针对《结论.md》里 A 域的三处落项逐条收口，`mvn test` 全量 **514 例全绿**（本期新增 36），`npm run build` 通过。

| 审查落项 | 整改内容 | 涉及文件 |
|---|---|---|
| FR-SYS-03 部分完成：`sys_user_role.expire_at` 未消费，临时授权 24 小时自动失效未实现 | ① 审批接口 `PUT /users/applications/{id}` 新增可选 `expireHours`（1—720，超限 30301），通过即写 `expire_at`；② 新增 `RoleExpiryService` + `job/identity/RoleExpiryJob`（每小时第 40 分，Asia/Shanghai）把到期行回收为 `EXPIRED` 并写 `ROLE_EXPIRE` 批量审计；③ `rolesOf`/菜单计算读侧本就过滤过期行（保持不变），到期后下次登录/刷新即失去角色。前端：审批弹窗加"授权时长"（永久/24h/7 天/30 天），待审列表与个人中心"我的申请"展示到期时间 | `UserService` `RoleExpiryService` `RoleExpiryJob` `UserDtos` `UserController` `UserPanel.vue` `ProfileView.vue` |
| A 的 1 个约定端点无前端入口：`PUT /files/{fileId}/audit` | 新增辅助端点 `GET /api/v1/files`（按 `auditStatus`/`bizType` 筛选 + 分页，ANIMAL_ADMIN/SYS_ADMIN/SYSTEM_ADMIN，非法枚举 30303）；后台新增第五个页签"附件审核"：缩略图预览、通过/驳回、待审池默认 PENDING。至此 A 的约定端点全部有前端入口 | `FileService` `FileController` `FileDtos` `FileAuditPanel.vue` `AdminView.vue` `api/file.ts` |
| A 域主链路缺自动化测试（登录、注册、用户、字典、附件、日志） | 新增 6 个测试类 36 例：`AuthFlowTest`（登录成功/锁定计数/账号+IP 双维度/禁用/待验证/注册四分支）、`UserServiceTest`（申请重复校验、审批含 expireHours、超限 30301、重复审批 30407、禁用原因+min-iat）、`RoleExpiryServiceTest`、`DictServiceTest`、`FileServiceTest`（上传双校验/审核流转/列表筛选）、`LogServiceTest` | `src/test/java/com/just/critter/service/{auth,user,support}` |

复核确认（无需再动）：结论里 P01"公告轮播/置顶公告"已由 C 在 `HomeView` 接 `/notices/highlights`；P11"我的捐赠/电子凭证"已由前轮接入 `GET /donations/mine` 与 `GET /donations/{no}/certificate`（ProfileView"我的捐赠"卡片，PDF blob 下载）。P02/P04 的其余缺口（档案合并、二维码、台账导出、捐赠异常标记等）属 B 的页面组，未动。
