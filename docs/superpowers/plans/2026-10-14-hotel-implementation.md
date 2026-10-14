# 酒店系统 MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 交付可本地运行和演示的酒店系统，包含预订、查询、入住、退房、清洁，以及真实过程与验收材料。

**Architecture:** 前后端分离，单机单 Java 后端访问 SQLite。预订按房型逐晚占库存，入住才分房；所有业务写操作统一进入 IMMEDIATE 写事务。

**Tech Stack:** Vue 3、TypeScript、Vite、Element Plus、Vue Router、Pinia；Java 26、Spring Boot 4.0、Spring JDBC、Spring Security、SQLite JDBC；npm、Maven Wrapper。

**Spec:** [已确认需求与技术方案](../specs/2026-10-14-hotel-design.md)，执行前同时阅读 [验收用例](../../acceptance-cases.md)。

状态：已实施交付。实际文件以源码为准，部分相邻 UI 组件复用；验收见 evidence/acceptance-results.md，A29 异地浏览器时区模拟未执行。

## Global Constraints

- 单酒店，住客预订端和前台管理端，前后端分离，Java 后端、SQLite 数据库，不接真实或模拟支付。
- 酒店时区固定为 Asia/Shanghai，业务层统一获取酒店今天，不信任浏览器日期。
- 单机单后端实例，不引入 Redis、消息队列、分布式锁。
- 用户约束：不执行单元测试。验证采用构建、接口验收、浏览器验收、并发请求及数据库核对。
- 如需 Python 辅助工具，统一通过 uv 和 Python 3.12 运行。
- 原始 AI 会话不能用摘要替代，不伪造执行结果、截图或 Agent 使用记录。
- 优先在合适边界使用子 Agent，服从工具实际并发上限，不超过用户要求的 6 个。
- 用户已授权开始实施；执行下列任务，禁止执行单元测试。

## Review Focus

1. 浏览器日期与酒店日期不同、请求跨午夜：业务操作每次只获取一个酒店日期快照，所有日期判断使用该值；任务 1、3、7，补充 A29。
2. 预订成功但响应丢失：保留幂等键重试，返回原订单，不重复占库存；任务 3、5，补充 A30。
3. 入住与取消竞争同一订单：仅一个状态迁移成功，无入住记录与取消状态并存；任务 4、7，补充 A31。
4. 登录后 Session/CSRF token 更新：前端重新取 token，正常操作不被错误拒绝，旧 Session 不再有效；任务 2、5，补充 A32。
5. SQLite WAL 数据重置和重启：只在后端停止时重置演示数据库，普通启动不覆盖现有数据；任务 1、7，补充 A33。

## 文件职责和接口约定

以下路径相对项目根目录。Java 包根为 backend/src/main/java/com/example/hotel/，下文以 J/ 表示；资源根为 backend/src/main/resources/，下文以 R/ 表示。

| 路径 | 职责 |
|---|---|
| backend/pom.xml、backend/mvnw、backend/.mvn/wrapper/ | 固定依赖和 Java 26 构建 |
| J/HotelApplication.java、R/application.yml | 后端入口、端口及数据目录配置 |
| J/common/HotelClock.java、ApiExceptionHandler.java、ApiError.java、PageResult.java | 酒店日期、统一错误、分页 |
| J/config/DatabaseConfig.java、J/database/WriteTransactions.java | SQLite 连接参数和唯一写事务入口 |
| J/database/DatabaseInitializer.java、DemoSeeder.java、R/db/schema.sql | 建表与幂等演示数据初始化 |
| J/auth/SecurityConfig.java、AuthController.java、UserRepository.java、CurrentUser.java | Session、CSRF、角色、身份查询 |
| J/catalog/RoomTypeController.java、RoomTypeService.java、RoomTypeRepository.java、RoomTypeView.java | 房型价格和逐晚余量 |
| J/order/OrderController.java、OrderService.java、OrderRepository.java、InventoryRepository.java、OrderContracts.java | 预订、查询、取消、库存明细 |
| J/frontdesk/FrontdeskController.java、StayService.java、RoomRepository.java、StayRepository.java、StayContracts.java | 分房、入住、退房、未到店、清洁 |
| frontend/src/api/{http,auth,catalog,orders,frontdesk}.ts、types.ts | API 请求与共享前端类型 |
| frontend/src/router/index.ts、stores/auth.ts、layouts/{GuestLayout,FrontdeskLayout}.vue | 路由、身份和双端布局 |
| frontend/src/views/{LoginView,RoomSearchView,MyOrdersView,OrderDetailView}.vue | 登录和住客页面 |
| frontend/src/views/frontdesk/{OrdersView,OrderDetailView,RoomsView}.vue | 前台订单、办理弹窗、房态 |
| tools/acceptance/{run,session,fixtures,assertions}.mjs | 针对运行中服务的接口与并发验收，不是单元测试 |
| docs/api-contract.md、docs/demo-guide.md、docs/implementation-notes.md | 接口契约、演示脚本、实现思路 |
| scripts/{start-demo,stop-demo,reset-demo}.sh | 启停及显式重置本项目演示实例 |
| evidence/acceptance-results.md、evidence/screenshots/、evidence/conversations/ | 实际结果、产品截图、真实会话导出 |

所有 DTO 精确字段先在任务 1 的 docs/api-contract.md 固定；后续前后端均按该契约实现，不分别命名。共同约定如下：

- ID 为 long/number，金额为 long/number 的整数分，服务端校验溢出；页面将分格式化为人民币。
- OrderContracts 定义 CreateOrderRequest(roomTypeId, checkIn, checkOut, guestCount)、OrderView、OrderFilter、OrderStatus；StayContracts 定义 CheckInRequest(roomId, guestName, guestPhone)、RoomView。
- OrderView 包含 id、orderNo、roomTypeId、roomTypeName、checkIn、checkOut、guestCount、nightlyPriceCents、totalCents、status、createdAt，详情另带入住记录和事件；住客数据不暴露密码、幂等摘要等内部字段。
- PageResult<T> 为 items、page、size、total；page 从 1 开始，size 默认 20、最大 100；按 created_at DESC、id DESC 排序。
- GET /api/auth/csrf 返回 token、headerName；登录使用表单 username/password；登录成功及退出后重新获取 CSRF token。所有业务请求体使用 JSON。
- 创建订单首次返回 201，幂等重放返回 200；状态操作成功返回 200 和最新 OrderView。重复状态操作统一返回 409，库存不变；清洁操作重复请求返回当前房态。
- 错误体为 code、message、requestId。权限/参数/状态错误与 HTTP 状态遵循正式方案。
- 默认 demo 种子库存覆盖初始化当天 D-7 到 D+89 的住宿夜晚；普通查询不动态补库存。接口返回可售日期边界，离店日最多到最后库存日期次日。此为演示配置，不改变业务规则。

## 任务 1：可启动后端、数据库与契约

**Files:** 创建 backend 构建/入口/配置、J/common/、J/database/、J/config/DatabaseConfig.java、R/db/schema.sql、docs/api-contract.md、tools/acceptance/fixtures.mjs 和 assertions.mjs，职责见上表。

**Interfaces:** 产出 HotelClock.today(): LocalDate、HotelClock.now(): Instant；WriteTransactions.execute(Supplier<T>): T；PageResult<T>、ApiError。所有 Repository 使用同一个受 Spring 管理的数据源，写事务入口以驱动 IMMEDIATE 模式结合 TransactionTemplate 管理连接，禁止手动嵌套 BEGIN。

- [ ] 核对本机 JDK、Node、npm 和官方依赖兼容性，固定 Maven 版本及依赖；提交前端 lockfile 时再固定前端依赖，不擅自升级已确认的 Spring Boot 主版本。
- [ ] 在 docs/api-contract.md 写清全部请求/响应字段、错误、分页、CSRF 流程及两个前端入口；此文件完成后冻结共享接口。
- [ ] 创建后端及 8 张业务表，为 orders 补齐 created_at/updated_at，增加角色/状态/金额/库存 CHECK、外键、唯一索引；不创建业务单元测试。
- [ ] 实现连接配置：WAL、每连接 foreign_keys=ON、busy_timeout=3000ms；配置驱动 IMMEDIATE 写事务并确保失败整体回滚。锁超时映射 DATABASE_BUSY/503。
- [ ] 实现统一酒店时钟，每个业务事务只取一次 today；实现种子数据，包含今日入住、今日离店、未到店、待清洁和逾期未退房场景，保证明细与库存一致。
- [ ] 编写隔离验收数据工具：使用 Node 内置 SQLite 能力或项目固定的兼容驱动，限定数据库必须位于临时验收目录；不修改正在演示的数据库。执行前检查运行时能力。
- [ ] 在 backend 运行 `./mvnw -Dmaven.test.skip=true package`，预期 BUILD SUCCESS；用独立连接实测写锁等待和回滚，不以配置文件存在代替验证。覆盖 A22、A23、A26、A29 的基础条件。

## 任务 2：登录、Session 与权限

**Files:** 创建 J/auth/ 下上述文件；补全统一错误处理；创建 tools/acceptance/session.mjs 和 run.mjs 的 auth 分组。

**Interfaces:** 产出 CurrentUser(id, username, role)；GET /api/auth/me 返回本人；GET /api/auth/csrf 提供 token；业务服务接受 CurrentUser actor，不能相信 body 中的 userId。

- [ ] 实现用户名密码认证及密码摘要校验，配置 Session 登录成功时的会话保护、退出失效和 HttpOnly Cookie。
- [ ] 实现 JSON 登录/权限错误响应，保护住客与前台路径；提供 CSRF token 获取接口，登录失败不会返回 HTML 页面。
- [ ] 实现验收会话客户端，分别保存不同用户 Cookie 与 CSRF token，登录后刷新 token；token/headerName 从接口读取，不写死。
- [ ] 运行 `node tools/acceptance/run.mjs --group auth`，覆盖 A01、A24、A32；预期无会话请求 401、错误角色 403、缺失 token 的写操作拒绝，合法登录后的写请求不因旧 token 失败。

## 任务 3：房型查询、预订、本人订单与取消

**Files:** 创建 J/catalog/、J/order/ 下上述文件；扩展 run.mjs 的 booking 分组。

**Interfaces:** 产出 RoomTypeService.search(LocalDate checkIn, LocalDate checkOut): List<RoomTypeView>；OrderService.create(CurrentUser actor, String key, CreateOrderRequest request): OrderView；list(CurrentUser actor, OrderFilter filter): PageResult<OrderView>；detail(CurrentUser actor, long orderId): OrderView；cancel(CurrentUser actor, long orderId): OrderView。HTTP 层另根据幂等命中区分 201/200。

- [ ] 实现日期、人数、金额及库存区间校验；拒绝空幂等键及超出约定长度的参数，在契约中固定这些输入限制。
- [ ] 实现房型余量查询，检查每晚都有库存行，返回区间最小余量、固定每晚价格和可售日期边界。
- [ ] 在统一写事务内校验幂等键和规范化请求摘要，逐晚条件扣库存；任何失败回滚订单、库存、明细和事件。
- [ ] 实现订单查询分页、详情和所有者限制；前台查询复用服务的角色判断，不能给住客提供全店结果。
- [ ] 实现取消：校验身份、酒店日期及原状态，只释放今天及未来 HELD 明细，在同一事务内变更订单和事件。
- [ ] 运行 `node tools/acceptance/run.mjs --group booking`，覆盖 A02–A10、A23、A25、A29、A30；对 A06–A08 使用独立会话/并发请求，核对数据库总量及逐晚记录；预期无超卖、半单或重复占用。

## 任务 4：前台分房、入住、退房、未到店与清洁

**Files:** 创建 J/frontdesk/ 下上述文件；扩展 run.mjs 的 frontdesk 分组。

**Interfaces:** 消费任务 3 的 OrderView、OrderFilter、取消服务；产出 StayService.availableRooms(CurrentUser actor, long orderId): List<RoomView>、checkIn(CurrentUser actor, long orderId, CheckInRequest request): OrderView、checkOut(CurrentUser actor, long orderId): OrderView、noShow(CurrentUser actor, long orderId): OrderView、clean(CurrentUser actor, long roomId): RoomView。

- [ ] 实现前台订单筛选与详情，候选房间排除房型不符、待清洁、未退房及住宿区间冲突。
- [ ] 在 IMMEDIATE 事务内重新校验订单、日期和房间，写入住记录并更新订单、事件；清晰映射房间唯一约束冲突。
- [ ] 实现退房：禁止提前退房，关闭入住记录，完成订单并置房间待清洁，不回补历史库存。
- [ ] 实现未到店，复用库存明细释放逻辑；允许住宿区间全过去且释放量为零，不允许对已入住订单操作。
- [ ] 实现房态查询及清洁，实际占用优先于计划离店日期；占用中拒绝清洁完成请求。
- [ ] 运行 `node tools/acceptance/run.mjs --group frontdesk`，覆盖 A11–A21、A31；同时入住同房和入住/取消竞争均只允许一种有效结果，数据库全量一致性断言通过。

## 任务 5：住客端与共享前端基础

**Files:** 创建 frontend/package.json、package-lock.json、index.html、vite.config.ts、tsconfig.json、src/main.ts、App.vue，以及上表中的 api、types、router、store、layouts 和住客 views。

**Interfaces:** 消费 docs/api-contract.md；产出 api/http.ts 的 request<T>(path: string, options?: RequestInit): Promise<T>；业务 API 模块仅传参和返回契约类型。前台任务复用该请求层和登录 store。

- [ ] 创建 Vue 工程和 Element Plus 基础布局，配置 /api 代理和包含 vue-tsc 的 build 脚本，固定 package-lock.json。
- [ ] 实现 Session 登录、身份恢复、路由权限和 CSRF 刷新；401 返回登录页面，业务错误保留表单输入。
- [ ] 实现日期查询、房型列表、人数及价格确认；提交时保存幂等键和请求快照，网络结果未知时允许原请求重试，编辑参数意味着新请求。
- [ ] 实现本人订单分页、详情、可取消按钮及成功后刷新；按钮规则以酒店日期为准，后端仍作最终判断。
- [ ] 运行 `npm --prefix frontend run build`，预期类型检查及构建退出码 0；浏览器验证 A01–A04、A09、A10、A25、A30、A32，确认窄屏表单不溢出、加载及错误提示可见。

## 任务 6：前台管理端

**Files:** 创建 frontend/src/views/frontdesk/ 下三个页面，补全 frontend/src/api/frontdesk.ts 和前台路由；不改共享契约。

**Interfaces:** 消费任务 4 API 和任务 5 request/auth store；在订单详情中使用入住、取消、未到店、退房弹窗，按状态显示操作。

- [ ] 实现全店订单筛选、分页和详情；过滤状态与日期由 URL 查询参数或页面状态明确保留。
- [ ] 实现选房入住弹窗，登记姓名手机号；提交冲突后刷新候选房间，不把旧列表作为成功保证。
- [ ] 实现退房、取消、未到店操作及结果刷新，展示等待清洁与逾期未退房提示。
- [ ] 实现房态页，分开展示占用与清洁状态；只有未占用房间可确认清洁。
- [ ] 运行 `npm --prefix frontend run build`；浏览器覆盖 A13–A21、A25，验证直接打开前台 URL 的住客仍被拒绝；前后台刷新后的订单状态一致。

## 任务 7：联调、交付与真实证据

**Files:** 创建 scripts/ 下启停重置脚本、README.md、docs/demo-guide.md、docs/implementation-notes.md、evidence/acceptance-results.md；更新 evidence/process-log.md；实际生成截图与可获取的原始会话文件。

**Interfaces:** run.mjs 接受 --group auth|booking|frontdesk|all 和 --base-url，使用隔离实例；任何失败退出非零。启停脚本仅管理项目自己的进程，重置脚本拒绝对运行中的后端执行。

- [ ] 编写 README 和启停方式：安装后通过 `bash scripts/start-demo.sh` 启动双服务；通过 `bash scripts/stop-demo.sh` 停止；端口占用明确报错，不停止其他项目进程。
- [ ] 实现重置脚本，停止状态下仅重建配置的演示数据库，正确处理该库 WAL/SHM 文件，不影响其他目录；普通启动只幂等初始化。
- [ ] 在 backend 执行 `./mvnw -Dmaven.test.skip=true package`，执行 `npm --prefix frontend ci` 和 `npm --prefix frontend run build`；记录版本、退出结果，不执行单元测试。
- [ ] 在隔离数据实例执行 `node tools/acceptance/run.mjs --group all`，覆盖接口、锁等待与并发；核对 8 张表的业务不变量。SQLite 连接验收是运行时集成验证，不新增单元测试。
- [ ] 在浏览器完成全部页面验收，覆盖 A26–A28、A33；完整记录 A01–A33 的通过/失败/未执行，不以构建成功代替业务通过。
- [ ] 演示住客下单、查询、前台入住，再切换预置今日离店订单展示退房清洁；截图保存到 evidence/screenshots/，包含预订、订单、分房、退房/房态画面。
- [ ] 编写实现思路与亮点，解释库存原子性、幂等、状态约束、房型与房间分离以及 SQLite 单写者限制，不宣称生产级高并发。
- [ ] 获取实际会话导出或真实分享链接，按来源保存并更新材料清单；无法获取时明确标为缺失，不能宣称全部交付完成。更新实际使用 Skill/Agent、版本、执行证据。
- [ ] 复核无演示密码之外的秘密、无临时验收数据库混入提交；按 README 在新数据目录复现启动，确认代码和交付材料完整。

## 依赖与 Agent 分工

依赖顺序：任务 1 → 任务 2 → 任务 3 → 任务 4；任务 5 在任务 2 和接口契约完成后可与任务 3/4 并行；任务 6 在任务 5 的共享基础完成后开发，并在任务 4 完成后联调；任务 7 等所有功能完成。

沿用用户“尽量使用 subagent 并行处理”的偏好：一个后端实施 Agent、一个前端实施 Agent；主 Agent 负责契约、集成和验收。按需使用第三个子 Agent 做独立审查。当前工具最多同时 4 个 Agent（含主 Agent），因此最多 3 个子 Agent。数据库、共享类型和锁文件按文件所有权串行修改，不让两个 Agent 同时改同一文件。每个完成任务安排独立审查，修复后再推进依赖任务；最终做整体审查。

项目已建立独立 Git 仓库与 feat/hotel-mvp 分支，保存文档与实现。是否需要工作树在执行时按相关技能判断，不能把整个 IdeaProjects 初始化成仓库。

## 自查结果与交接

本计划已由主 Agent 对照正式方案自行检查，不将自查分派给子 Agent：

- 需求覆盖：登录/权限→2；房型/预订/查询/取消→3、5；分房/退房/清洁/未到店→4、6；运行与材料→1、7。
- 验证覆盖：既有 A01–A28 均有任务归属，新增审查边界 A29–A33 均有负责任务。
- 接口一致：身份统一 CurrentUser；日期 checkIn/checkOut；金额分；状态枚举沿用正式方案；前后端共享契约优先完成。
- 执行限制：全部构建跳过单元测试；已按用户约束完成实施与运行时验证；完成标记表示交付任务完成，不表示每个可选验收变体都已运行。
- 交接：用户审阅本计划后进入实施，沿用上述子 Agent 分工偏好，不再次询问已确定的技术栈和执行方式。
