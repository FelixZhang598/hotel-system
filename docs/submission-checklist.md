# 面试提交材料索引

| 要求 | 实际交付 |
|---|---|
| 完整代码 | backend/、frontend/、scripts/、tools/；README 启动说明 |
| AI 原始会话 | evidence/conversations/original-conversation.md 和 original-messages.jsonl，附来源 SHA256 和导出时间 |
| 实际使用 Skill / Agent | evidence/process-log.md，独立审查报告位于 evidence/reviews/ 和 evidence/final-review.md |
| 需求方案 | docs/superpowers/specs/2026-10-14-hotel-design.md 的目标、范围、业务规则 |
| 技术方案 | 同一方案的架构、数据模型、事务、接口；docs/api-contract.md |
| 实施计划 | docs/superpowers/plans/2026-10-14-hotel-implementation.md |
| 测试用例 | docs/acceptance-cases.md 共33条；未执行单元测试 |
| 实际验收结果 | evidence/acceptance-results.md 与 evidence/*.json |
| 产品截图或录屏 | evidence/screenshots/ 共8张实际产品截图；未制作录屏 |
| 实现思路和亮点 | docs/implementation-notes.md |
| 演示脚本 | docs/demo-guide.md、docs/backend-seeds.md |

原始会话导出范围为导出时已持久化的用户/助手可见消息，逐条原文保留，不是人工重写；不包含系统/开发者指令、内部推理或工具原始日志。过程摘要另列，不能替代原文。未上传至任何外部平台。

最终技术栈为用户指定的 Java26，相应选用官方兼容的 Spring Boot4.0.8；原始对话保留最初 Java21/Boot3 的讨论，正式方案反映最终选择。

使用现有工具链首次安装依赖需要网络；源代码交付不包含 node_modules、Maven 下载缓存或运行数据库。原始会话截止时间以 provenance.json 为准。
