# 项目约束

- 后端 Java26、Spring Boot4.0、Spring JDBC、SQLite；前端 Vue3/TypeScript，前后端分离。
- 不执行单元测试。后端构建使用 `./mvnw -Dmaven.test.skip=true package`；验证使用真实 API、浏览器和隔离数据库。
- 若需要 Python，使用 uv 与 Python3.12；禁止默认直接调用 python/pip/pytest。
- 优先按文件所有权分配子 Agent，不超过工具实际容量或用户要求的6个。共享接口先同步再修改。
- 询问或诊断任务只给方案，不自行修改；明确实施/修复指令下完成授权工作。
- 不重置运行中的数据库；验收数据库限 `.runtime/acceptance/`。
- 需求以 docs/superpowers/specs/2026-10-14-hotel-design.md 为准；接口以 docs/api-contract.md 为准。
