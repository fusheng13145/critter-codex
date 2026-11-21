# 成员 A 交付说明 · 身份、后台与数据看板

> 本文给 B、C 看：A 交了什么、怎么起、前端怎么接、哪些地方与文档有偏差。
> 契约基线：`docs/md/03 接口设计说明书.md` v1.0-frozen；数据基线：`docs/md/04 数据库设计说明书.md`。
> 代码分支：`unifyed`（从 `main` 拉出，已快进到 `origin/main` 的 `19e3215`）。

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

## 3 前端交付与接入方式

### 3.1 已实现页面

| 页面组 | 路由 | 说明 |
|---|---|---|
| P00 登录注册 | `/login` | 登录 / 注册 / 验证码三步；注册含学号与邮箱前端预校验 |
| P01 首页 | `/` | 指标卡、近半年新增、待办（管理员）、身份与菜单（学生）、后台动态（系统管理员） |
| P09 统计 | `/stats` | 生物/资金/效率三块 + 项目明细 + 区间筛选 |
| P10 后台 | `/admin` | 四个页签：操作日志 / 用户与申请 / 数据字典 / 角色权限 |
| P11 个人中心 | `/me` | 资料、改密、身份申请、我的申请、登录日志 |

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
| 角色权限在线配置 | `PUT /roles/{id}/permissions` | 本期只读矩阵（矩阵在 `MenuMatrix` 常量） | 库中暂无角色-权限关联表；待基础稳定后再建表迁移 |

## 5 需要 B、C 注意的三件事

1. **本机库与 `V1__init.sql` 不一致**：本机库是历史库，Flyway 以 baseline 接管（`flyway_schema_history` 只有 baseline 与 V2—V4），`V1__init.sql` 从未真正执行，因此 `v_animal_overview` 等三个视图原本不存在。A 在 `V3` 里用 `CREATE OR REPLACE` 按 V1 的定义补齐，未改口径。**建议 B 把本机库与 V1 对齐（或重建库），否则新克隆的库与本机库字段会有差异**（如 `sys_user_role.created_by` 本机没有）。
2. **`adoption` 表尚未建**：`GET /stats/efficiency` 的 `pendingAdoptions` 在缺表时降级为 0（`StatsService.safePendingAdoptions`），C 建表后自动生效。
3. **A 不写 B/C 的表**：除 `V2/V3` 的字典、角色、学籍、账号种子外，A 的代码对 `animal`、`project`、`adoption`、`report`、`rescue_order` 等只读。统计接口不缓存，B/C 改数据后刷新即变。

## 6 测试与验证

- 后端：`mvn test` → **81 例全绿**（原有 63 + A 新增 18：JWT 4、AES 4、内存缓存 4、菜单矩阵 5、其他）。
- 接口：curl 逐条走过注册→验证→登录→刷新→注销、改密失效、锁定、越权 403、字段错误 30301、字典增改即时生效、附件上传/审核/匿名访问控制、三个统计接口。
- 前端：`npm run build`（`vue-tsc` 类型检查 + 打包）通过；headless 浏览器走查登录、首页（管理员/学生两种菜单）、统计、个人中心、后台四个页签，无 console 报错；学生访问 `/stats` `/admin` 被守卫拦回首页。
