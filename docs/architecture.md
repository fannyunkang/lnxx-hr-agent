# 架构说明

```text
Vue ── REST/SSE ──> Java Backend（认证/API/业务数据）
                         ├─ Python agent-service（Supervisor 多 Agent 编排）
                         │    ├─ Supervisor 规划与结果汇总
                         │    ├─ agent_plugins.json 挂载 HR 子 Agent 插件
                         │    ├─ Profile/Attendance/Leave/Approval/PolicyRag/HrSearch 子 Agent
                         │    ├─ Redis 会话、运行事件、checkpoint 与节点 Trace
                         │    ├─ 子 Agent model → tools → model 状态循环
                         │    └─ 重试、循环保护与 Prometheus
                         ├─ MySQL/Flyway Trace 与知识元数据
                         ├─ Milvus + MySQL 关键词混合检索
                         ├─ PaddleOCR 文档解析
                         └─ MCP Client ──> 独立 hr-mcp-server ──> MySQL

可选：`AGENT_RUNTIME=spring-ai` 时由 Java Spring AI 运行时直接编排。
```

## 安全边界

- 浏览器只提交问题和会话 ID，员工编号、角色、部门从 Java 认证主体取得。
- 本地 Spring AI Tools 使用线程绑定的可信身份，不接受模型提供的用户身份字段。
- 知识权限在召回前过滤，支持 `PUBLIC`、`DEPARTMENT`、`PERSONAL`、`HR_ONLY`。
- 模型输入、工具结果、回答和 Trace 摘要均经过身份证、手机号、邮箱脱敏。
- 知识文本属于不可信上下文，不能改变系统规则、身份或工具权限。

## 插件化编排

Python Agent 参考 DeepSeek Harness “模型、工具、会话、循环、调度均可插件化”的设计思想，但不依赖 Harness Runtime。项目把模型客户端、MCP 工具注册、子 Agent 清单、运行存储和评测器拆成独立模块；`agent_plugins.json` 声明每个子 Agent 的意图、关键词和工具白名单，Supervisor 启动时加载清单并按问题选择一个或多个子 Agent 并发执行。新增 HR 场景时，只需要补充工具 schema 和子 Agent 插件配置，再纳入 JSONL/LLM-as-a-Judge 评测。

## 检索与入库

上传 PDF、扫描 PDF、Markdown 或 JSON 后异步解析。PDFBox 文本不足时调用 PaddleOCR；切片以约 500 tokens、80 tokens 重叠为默认值。Milvus 与关键词各召回 30 条，RRF 合并后返回 5 条；Milvus 不可用时保留关键词检索。新版本全部索引完成后才激活，删除先软删除再清理向量。

引用格式固定为 `[KB-{documentId}-{version}-{chunkNumber}]`。
