# 酒店系统 MVP 需求与技术方案

日期：2026-10-14。状态：用户授权实施后已实现并完成实际验收；最终 Java26 / Spring Boot4.0.8。验收覆盖及限制见 evidence/acceptance-results.md。

## 1. 目标与约束

用于 AI Coding 面试，交付可运行、可演示的酒店系统及完整过程材料。单酒店，住客预订端和前台管理端，前后端分离，Java 后端、SQLite 数据库，不接真实或模拟支付。

技术栈：Vue 3、TypeScript、Vite、Element Plus、Vue Router、Pinia；Java 26、Spring Boot 4.0、Spring JDBC、Spring Security、SQLite JDBC；npm 和 Maven Wrapper。实施前核对兼容性并固定具体依赖版本。单机单后端实例，不引入 Redis、消息队列、分布式锁。

用户约束：不执行单元测试。验证采用构建、接口验收、浏览器验收、并发请求及数据库核对。如需 Python 辅助工具，统一通过 uv 和 Python 3.12 运行。优先在合适边界使用子 Agent，服从工具实际并发上限，不超过用户要求的 6 个。

## 2. MVP 范围

住客：使用预置账号登录，按入住/离店日期查询房型、价格、余量，单订单预订一间房，查看自己的订单及详情，取消符合条件的订单。

前台：登录，按订单号、状态、入住日期分页查询全店订单，查看详情，选择房间办理入住，登记入住人基本信息，退房，取消符合条件的订单，手动标记未到店，查看房态及确认清洁完成。

演示：预置两个住客账号、一个前台账号，提供房型、房间和不同业务状态的订单；启动说明、显式数据重置方式、截图或录屏。初始化日期相对初始化当天生成；普通重启不覆盖已有数据。

不实现：多酒店、多间房订单、注册、找回密码、短信、第三方登录、真实或模拟支付、退款、改期、续住、换房、升级、提前入住、提前退房、钟点房、动态定价、优惠券、发票、身份证核验、复杂权限、财务报表、完整房型和房间配置后台。

## 3. 业务规则

### 3.1 日期与金额

酒店时区固定为 Asia/Shanghai，业务层统一获取酒店今天，不信任浏览器日期。入住日期不得早于今天，离店日期必须晚于入住日期，日期区间为 [入住日, 离店日)。例如 10 月 14 日入住、16 日离店，占用 14、15 两晚。

每个房型固定每晚价格，以整数分保存。金额由服务端计算，下单保存价格快照；人数必须在房型容量内。无有效库存记录的日期不可售，禁止默认为无限库存。演示库存覆盖区间在初始化配置和查询响应中明确提供，超出区间的请求拒绝。

### 3.2 库存

以房型和住宿日期维护总量及已占用量，整段可售数量等于各晚余量的最小值。总量来自固定房间数，不随清洁状态变化。

预订成功即已确认并占用每晚库存。已确认和已入住订单都占库存；入住不再次扣减；正常离店不回补过去库存。查询余量只供展示，提交时重新校验。

取消或标记未到店只释放今天及未来尚未释放的逐晚明细，每条明细最多释放一次。过去明细保留记账；完整住宿区间已经过去时，标记未到店可成功且释放量为零。明细 HELD 表示该晚占用未释放，不表示订单当前仍有效。

### 3.3 状态与权限

| 操作 | 状态变化 | 条件 |
|---|---|---|
| 预订 | 无 → CONFIRMED | 全部住宿日期库存足够 |
| 取消 | CONFIRMED → CANCELLED | 住客在入住日前；前台在入住日或之前 |
| 未到店 | CONFIRMED → NO_SHOW | 今天晚于入住日，由前台操作 |
| 入住 | CONFIRMED → CHECKED_IN | 今天等于入住日，分房校验通过 |
| 退房 | CHECKED_IN → COMPLETED | 今天不早于离店日 |

终态不逆转。入住日之后的未入住订单通过未到店流程处理。重复状态操作返回当前结果或明确状态冲突，不能再次产生库存和分房副作用。后端使用带原状态条件的更新并检查影响行数。

住客只能访问自己的订单；身份取自 Session，不接受请求参数指定所有者。前台可访问全店订单。所有接口执行后端鉴权，前端隐藏入口仅改善体验。用户只使用预置账号，密码保存摘要。

### 3.4 入住、退房与清洁

住客选择房型，入住时前台才选房号。候选房间必须匹配房型、已清洁、无未退房记录、在整个住宿区间无分配冲突。候选列表不是保证，提交时在同一写事务内再次校验。

房间是否被占用由未退房入住记录判断，清洁状态单独保存。入住同时创建入住记录、更新订单和操作事件。退房同时记录实际离店时间、完成订单、标记房间待清洁。清洁操作只允许对未被实际占用的房间执行。

没有干净房间时不得强制入住，订单保持已确认并提示等待清洁。住客超过计划离店日期仍未办理退房时，房间持续视为占用，阻止新入住并提示前台处理。MVP 不计算超时费用，不支持延住；该情形可能导致当日有预订而无法立即入住，界面必须明确提示。

入住人基本信息包括姓名和联系手机号，不做身份真实性核验；所有演示数据使用虚构信息。

## 4. 项目结构与分层

```text
hotel-system/
  frontend/        页面、路由、状态、请求封装
  backend/         Java API 与 SQLite 访问
  docs/            需求、技术方案、计划、验收用例
  evidence/        会话、Skill/Agent、验收结果、截图
  README.md        启动、账号、重置、演示步骤
```

前端一个工程划分住客和前台路由；后端一个服务，按认证、房型、预订、前台办理组织业务模块，各模块内部 Controller → Service → Repository。前端通过 /api 请求后端，开发环境用代理；SQLite 文件仅由后端访问，保存在独立数据目录。

## 5. 数据模型

| 表 | 主要字段与约束 |
|---|---|
| users | id、username UNIQUE、password_hash、role |
| room_types | id、name、description、capacity、nightly_price_cents |
| rooms | id、room_number UNIQUE、room_type_id、cleaning_status |
| inventory_days | room_type_id、stay_date、total、reserved；组合唯一；0 ≤ reserved ≤ total |
| orders | id、order_no UNIQUE、user_id、room_type_id、check_in_date、check_out_date、guest_count、price_snapshot_cents、total_cents、status、idempotency_key、request_hash；用户与幂等键组合唯一 |
| order_nights | order_id、stay_date、allocation_status；订单与日期组合唯一 |
| check_in_records | id、order_id UNIQUE、room_id、guest_name、guest_phone、checked_in_at、checked_out_at |
| order_events | id、order_id、operator_id、from_status、to_status、reason、created_at |

使用外键保护关系，并为每个连接启用外键校验。在入住记录中设置针对 checked_out_at IS NULL 的房间唯一索引，避免一个房间出现两条未退房记录。入住区间冲突检查与写入在统一写事务内完成。

索引覆盖用户订单查询、状态/入住日筛选、房型日期库存和房间入住记录。订单列表排序需稳定，以创建时间和 ID 排序。住宿日期规范存储为 ISO 日期字符串；事件时间统一保存 UTC 时间并按酒店时区展示。

## 6. 事务与幂等

SQLite 同时只有一个写事务。预订、取消、未到店、入住、退房、清洁均走统一写事务入口，在读取决策数据前获取写事务。使用 SQLite JDBC 驱动支持的 IMMEDIATE 模式，验证实际生效；不假设默认 @Transactional 已满足要求，不在已开始的事务中重复 BEGIN。

启用 WAL，配置有限锁等待。锁等待超时返回可重试的业务错误，整个失败事务必须回滚。WAL 改善读写并行，不能实现多个同时写事务。事务内部不执行外部调用。

预订顺序：查用户与幂等键 → 校验参数及价格 → 逐晚条件增加占用 → 创建订单、明细、事件 → 提交。任一环节失败全部回滚。

同幂等键同规范化参数返回原订单（包括订单当前状态）；同键不同参数返回冲突。请求摘要包含房型、日期、人数等影响预订的字段。网络超时后前端重试使用原键；新的预订意图生成新键。

取消/未到店在一个事务内校验原状态，仅释放符合日期及 HELD 条件的明细，根据实际释放明细递减库存，更新订单并写事件。入住/退房同样保证记录、状态与事件原子更新。

## 7. 接口与页面

| 页面/操作 | API |
|---|---|
| 登录/退出/本人 | POST /api/auth/login、POST /api/auth/logout、GET /api/auth/me |
| 房型与余量 | GET /api/room-types?checkIn=YYYY-MM-DD&checkOut=YYYY-MM-DD |
| 预订 | POST /api/orders，携带 Idempotency-Key |
| 我的订单/详情 | GET /api/orders、GET /api/orders/{id} |
| 住客取消 | POST /api/orders/{id}/cancel |
| 前台订单/详情 | GET /api/frontdesk/orders、GET /api/frontdesk/orders/{id} |
| 候选房间/入住 | GET /api/frontdesk/orders/{id}/available-rooms、POST /api/frontdesk/orders/{id}/check-in |
| 退房 | POST /api/frontdesk/orders/{id}/check-out |
| 前台取消/未到店 | POST /api/frontdesk/orders/{id}/cancel、POST /api/frontdesk/orders/{id}/no-show |
| 房态/清洁 | GET /api/frontdesk/rooms、POST /api/frontdesk/rooms/{id}/clean |

Session Cookie 使用 HttpOnly，修改操作保留 CSRF 校验；在技术实现中约定前端取得和提交 CSRF token 的方式。前台房态页展示可入住、待清洁、已占用，并突出逾期未退房情况。

统一错误结构包含 code、message 和 requestId。主要错误码：INVENTORY_INSUFFICIENT、ORDER_STATE_CONFLICT、ROOM_UNAVAILABLE、IDEMPOTENCY_CONFLICT、DATABASE_BUSY。无权限和不存在的他人订单不暴露敏感细节。无效参数返回 400，未登录 401，角色无权限 403，资源不存在 404，业务冲突 409，数据库暂时繁忙 503。

## 8. 验收与交付

验收用例见 ../../acceptance-cases.md。记录执行环境、操作步骤、实际结果和证据，未运行的用例一律标记未执行。不执行单元测试，不把构建成功表述为业务验收通过。

演示主线：住客预订与查询 → 前台分房入住；由于不允许提前退房，退房与清洁使用预置的今日离店订单继续演示，不修改生产业务日期规则。

最终材料：完整代码、实际 AI 原始会话、实际 Skill/Agent 记录、需求方案、技术方案、测试用例及执行结果、产品截图或录屏、实现思路及技术亮点。人工摘要不能冒充原始会话；无法导出的内容须如实记录，不重构或伪造原文。

## 9. 官方参考

- SQLite 事务：https://www.sqlite.org/lang_transaction.html
- SQLite WAL：https://www.sqlite.org/wal.html
- SQLite 外键：https://www.sqlite.org/foreignkeys.html

## 10. 本次文档审查说明

已对照用户确认的聊天方案检查范围、状态、库存、分房、权限与交付项。明确了两个演示边界：入住日之后采用未到店流程；五分钟演示中的退房使用今日离店种子订单，避免违反禁止提前退房规则。正式文档审阅完成后再编写实施计划。
