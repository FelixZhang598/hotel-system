# 后端演示种子与运行约定

首次初始化数据库时，以酒店 Asia/Shanghai 日期 D 生成数据。后续启动只检查已有用户，不覆盖业务数据；显式重置由根目录脚本管理。以下全部姓名、电话均为虚构演示数据。

## 版本与运行

Java 26；Spring Boot 4.0.8；SQLite JDBC 3.53.4.0；官方 Maven Wrapper 3.3.4（only-script），固定 Maven 3.9.16。

Spring 官方 [4.0 系统要求](https://docs.spring.io/spring-boot/4.0/system-requirements.html)明确兼容 Java 26。依赖已实际从 Maven Central 解析并构建。

```sh
cd backend
./mvnw -Dmaven.test.skip=true package
java --enable-native-access=ALL-UNNAMED -jar target/hotel-backend-1.0.0.jar
```

默认端口 8080，覆盖变量 `SERVER_PORT`。默认数据库 `../data/hotel.db`（相对进程当前目录），覆盖变量 `HOTEL_DB`。单实例、单数据库；所有业务写入在驱动 IMMEDIATE 事务中执行；每个连接设置 WAL、foreign_keys=ON、busy_timeout=3000。

应用保留一条只处于自动提交模式、从不执行业务事务的连接，直到关闭时释放。这样即使服务空闲且运行在非默认端口，重置脚本仍能用 `lsof` 检测数据库文件所有者，拒绝重置运行中的数据库。

仅 `acceptance` Spring profile 接受固定时钟：`HOTEL_FIXED_DATE=2026-10-14`（该日酒店零点）或 `HOTEL_FIXED_INSTANT=2026-10-14T15:59:59Z`（UTC 精确时间）；也可用对应 Spring 参数 `hotel.fixed-date`、`hotel.fixed-instant`。精确时间优先于日期。在默认运行 profile 中，这些值均被忽略。

## 用户

| ID | 用户名 | 角色 | 演示密码 |
|---|---|---|---|
| 1 | guest1 | GUEST | Demo12345! |
| 2 | guest2 | GUEST | Demo12345! |
| 3 | frontdesk | FRONTDESK | Demo12345! |

数据库只保存 BCrypt 密码摘要。Session Cookie 为 HttpOnly、SameSite=Lax；登录成功更新会话 ID。登录和每个 POST 都需要先读取 `/api/auth/csrf` 获取 token/headerName；登录及登出之后需要重新取 token。

## 房型与房间

| 房型 ID | 名称 | 容量 | 每晚分 | 房间 ID/房号 |
|---|---|---|---|---|
| 1 | 舒适大床房 | 2 | 32800 | 101–106 |
| 2 | 景观双床房 | 2 | 42800 | 201–206 |
| 3 | 家庭套房 | 4 | 68800 | 301–306 |

房间 ID 与房号数值相同；每房型共 6 间。103 初始 DIRTY；其他房间 CLEAN。101 和 201 已实际占用。库存覆盖 D-7 至 D+89 的住宿夜晚，每房型每日总量 6。最大离店日为 D+90，`/api/meta` 的 `availableFrom` 为酒店今天与库存最早日期中较晚者。

## 种子订单

订单号固定为 `QA-DEMO-0001` 等四位末尾 ID；种子幂等键为 `seed-1` 等内部初始化标记，演示下单应生成独立 UUID 键。

| ID | 所属住客 | 房型 | 入住→离店 | 初始状态 | 用途 |
|---|---|---|---|---|---|
| 1 | guest1 | 1 | D → D+2 | CONFIRMED | 今日分房入住 |
| 2 | guest1 | 1 | D-2 → D | CHECKED_IN | 房间 101，今日退房及清洁 |
| 3 | guest2 | 2 | D-1 → D+1 | CONFIRMED | 未到店；释放今天及未来夜晚 |
| 4 | guest2 | 2 | D-3 → D-1 | CHECKED_IN | 房间 201，逾期未退房，持续占用 |
| 5 | guest1 | 3 | D+3 → D+5 | CONFIRMED | 未来订单及取消 |
| 6 | guest2 | 1 | D-3 → D-2 | COMPLETED | 房间 103，已退房待清洁 |
| 7 | guest1 | 3 | D+4 → D+6 | CANCELLED | 已取消终态 |
| 8 | guest2 | 3 | D-4 → D-3 | NO_SHOW | 未到店终态；过去逐晚记录仍 HELD |
| 9 | guest2 | 2 | D-5 → D-4 | CONFIRMED | 全住宿区间已过去；未到店成功、释放量 0 |

所有逐晚库存 `reserved` 都等于同房型同日期 `HELD` 明细数量。COMPLETED 的历史夜晚保留占用记账；NO_SHOW 的过去 HELD 明细不代表订单仍有效。预订只扣房型库存，分房时才创建入住记录；退房不回补历史库存。

## 验证记录

构建明确使用 `-Dmaven.test.skip=true`；不创建或执行单元测试。后端实现者在独立 `.runtime/backend-smoke/hotel.db`、端口 18081 上用 curl Cookie 会话验证登录、CSRF、角色隔离及预订。完整接口、库存竞争和锁等待由根目录 `tools/acceptance/run.mjs` 对隔离数据库验证，结果见根目录验收证据。
