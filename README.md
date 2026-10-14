# 栖岸酒店 · 酒店预订与前台管理

单酒店、前后端分离的 AI Coding 面试项目：预订、订单查询、分房入住、退房、清洁。无需支付服务、Redis 或外部数据库。

## 技术栈

- Java **26** + Spring Boot **4.0.8** + Spring JDBC + Spring Security + SQLite。
- Vue 3 + TypeScript + Vite + Element Plus + Pinia + Vue Router。
- Maven Wrapper 固定构建版本；npm lockfile 固定前端依赖。

Java 26 是用户最终指定版本。Spring Boot 从方案最初的 3 调整到 4.0，以满足 [官方 Java 26 兼容范围](https://docs.spring.io/spring-boot/4.0/system-requirements.html)。

## 启动

需要 Java 26、Node.js 24 或更新版本、npm，以及首次安装依赖时的网络。项目的实际验收环境和结果见 evidence/acceptance-results.md。

在项目根目录执行：

```bash
bash scripts/start-demo.sh
```

脚本构建后端（跳过单元测试）、安装缺失的前端依赖、构建前端并启动双服务。打开 http://127.0.0.1:5173 。后端地址 http://127.0.0.1:8080 ，接口根路径 /api。

| 角色 | 用户名 | 密码 |
|---|---|---|
| 住客 A | guest1 | Demo12345! |
| 住客 B | guest2 | Demo12345! |
| 前台 | frontdesk | Demo12345! |

以上仅为本地演示账号，演示资料使用虚构信息。

```bash
bash scripts/stop-demo.sh
bash scripts/reset-demo.sh
bash scripts/start-demo.sh
```

重置会删除本项目演示数据库及其 WAL/SHM 文件，必须先停止服务；普通重启不删除数据。数据库位于 data/hotel.db，日志位于 .runtime/，均不提交 Git。预置订单相对初始化当天生成，跨日演示前可显式重置。

## 独立开发

```bash
# 在 backend 目录
./mvnw -Dmaven.test.skip=true package
java --enable-native-access=ALL-UNNAMED -jar target/hotel-backend-1.0.0.jar

# 在 frontend 目录，另一个终端
npm ci
npm run dev -- --host 127.0.0.1
```

后端环境变量 SERVER_PORT 和 HOTEL_DB 可改变端口及数据库路径；前端 API_TARGET 指定代理目标。不要在两个实例间共享演示数据库。仅在 acceptance profile 中允许 HOTEL_FIXED_DATE 注入验收日期，正常模式使用上海时区系统日期。

## 验证

遵循用户约束：**不执行单元测试**。接口脚本访问真实运行的后端，并检查独立 SQLite 验收数据库。

```bash
# 独立验收实例：在 backend 目录启动，先准备 ../.runtime/acceptance 目录
HOTEL_DB="$PWD/../.runtime/acceptance/hotel.db" SERVER_PORT=18080 \
  java --enable-native-access=ALL-UNNAMED -jar target/hotel-backend-1.0.0.jar --spring.profiles.active=acceptance

# 项目根目录另一个终端执行；验收数据库应为新初始化数据，脚本会改变其中的订单
HOTEL_ACCEPTANCE_DB="$PWD/.runtime/acceptance/hotel.db" \
  node tools/acceptance/run.mjs --group all --base-url http://127.0.0.1:18080
```

结果输出 .runtime/acceptance/results.json。重复验收须先停止验收实例，再重建独立验收数据库，不能直接重复消耗同一份种子数据。不要将接口验收通过等同于所有浏览器用例通过，逐项执行记录见交付材料。

## 材料导航

- [完整提交材料索引](docs/submission-checklist.md)
- [需求与技术方案](docs/superpowers/specs/2026-10-14-hotel-design.md)
- [实施计划](docs/superpowers/plans/2026-10-14-hotel-implementation.md)
- [API 契约](docs/api-contract.md)
- [验收用例](docs/acceptance-cases.md)
- [演示步骤](docs/demo-guide.md)
- [实现思路与技术亮点](docs/implementation-notes.md)
- [实际 Skill/Agent 记录](evidence/process-log.md)
- [验收结果](evidence/acceptance-results.md)
- [产品截图](evidence/screenshots/)
- [原始可见会话](evidence/conversations/original-conversation.md)

## 范围边界

单订单单间房、房型固定价格、不支持支付、改期、续住、提前入住/退房和换房。SQLite 是单写者数据库，本项目展示正确性与可复现业务闭环，不宣称分布式或生产级高并发。
