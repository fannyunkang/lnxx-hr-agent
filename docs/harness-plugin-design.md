# 类 DeepSeek Harness 插件化设计

DeepSeek Harness 的核心思路是 “Everything is a plugin”：模型、工具、技能、会话、存储、循环和调度都可以作为插件挂载到运行时。当前项目没有直接引入 Harness Runtime，而是把这个设计思想落到人力知识 Agent 的工程结构里，让 HR 场景能力可以按模块替换、扩展和评测。

## 对应设计

| Harness 思路 | 本项目落地 | 关键文件 |
|---|---|---|
| 模型插件 | `OpenAI-compatible` 模型客户端统一封装 DeepSeek、Qwen、Doubao 和本地 demo-rule-agent，运行时按配置切换 | `agent-service/src/hr_agent/model_client.py` |
| 工具插件 | 工具统一注册为 schema，Python 只拿工具定义和执行结果，真实业务调用只经过 Java MCP Server | `agent-service/src/hr_agent/tools/registry.py`、`agent-service/src/hr_agent/tools/mcp_client.py` |
| Agent 插件 | 通过 `agent_plugins.json` 声明子 Agent 的 intent、说明、关键词和工具白名单，Supervisor 启动时加载 | `agent-service/src/hr_agent/graph/agent_plugins.json`、`agent-service/src/hr_agent/graph/multi_agent.py` |
| 调度插件 | Supervisor 根据用户问题和意图选择一个或多个子 Agent，并发执行后统一汇总 | `agent-service/src/hr_agent/graph/builder.py` |
| 会话/运行存储插件 | 会话、SSE 事件、checkpoint 和 Trace 使用 Redis 优先、内存兜底；Trace 可同步保存到 Java 后端 | `agent-service/src/hr_agent/conversation.py`、`agent-service/src/hr_agent/run_store.py`、`agent-service/src/hr_agent/trace.py` |
| 评测插件 | JSONL runner + LLM-as-a-Judge 可插拔评测层，规则指标和裁判模型指标分开输出 | `evals/runners/run_agent_eval.py` |

## 子 Agent 插件清单

当前清单挂载 6 个 HR 子 Agent：

- `ProfileAgent`：员工档案、岗位、部门查询
- `AttendanceAgent`：考勤、迟到、缺勤查询
- `LeaveAgent`：年假余额和已使用假期查询
- `ApprovalAgent`：审批进度和待办状态查询
- `PolicyRagAgent`：制度知识库 RAG 问答
- `HrSearchAgent`：HR/ADMIN 员工搜索

每个子 Agent 只暴露自己的工具白名单。模型即使生成了其他工具调用，也会被 `CHILD_AGENT_TOOL_SCOPE_VIOLATION` 拦截。

## 面试表达

这个设计可以强调为：不是把所有工具堆给一个 Agent，而是把模型、工具、子 Agent、存储和评测都拆成可替换模块；新增 HR 场景时优先新增插件清单和工具 schema，Supervisor 自动纳入调度、Trace 和评测。
