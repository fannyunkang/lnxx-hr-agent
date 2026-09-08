# Python Supervisor 多 Agent 状态图

当前版本使用 Supervisor Agent 进行任务规划和子 Agent 调度；每个子 Agent 使用 OpenAI 兼容模型进行显式工具调用，不调用 LangGraph。

```text
START
  └─ 加载 Redis 会话窗口
       │
       ▼
  Supervisor 规划子任务
       │
       ├─ ProfileAgent     员工档案
       ├─ AttendanceAgent  考勤汇总
       ├─ LeaveAgent       年假余额
       ├─ ApprovalAgent    审批进度
       ├─ PolicyRagAgent   制度知识问答
       └─ HrSearchAgent    HR 员工搜索
       │
       ▼
  子 Agent 并行执行 model -> tools -> model
       │
       ├─ 模型返回 tool_calls ──> 校验参数与工具配额
       │                              │
       │                              ▼
       │                        MCP Client 调用 Java MCP Server
       │                              │
       │                              ▼
       │                        把 tool 结果追加进消息
       │                              │
       └──────────────────────────────┘  (继续循环，最多 model_max_turns 轮)
       │
       ▼
  Supervisor 汇总子 Agent 结果 -> 最终回答 -> END
```

## 核心机制

- Supervisor 将复合问题拆解到 6 个子 Agent：`ProfileAgent`、`AttendanceAgent`、`LeaveAgent`、`ApprovalAgent`、`PolicyRagAgent`、`HrSearchAgent`。
- 每个子 Agent 只接收自己的工具白名单，降低工具误选和越权面。
- 子 Agent 每轮把「系统提示 + 历史对话 + 当前消息 + 工具结果」完整发给模型。
- 模型用 `tool_choice: auto` 在子 Agent 工具范围内自行决定是否调用工具、调用哪个工具。
- 工具结果以 OpenAI `tool` 角色消息回填，模型据此生成最终回答。
- MCP Client 只向 MCP Server 发送服务端注入的可信身份字段，不接受模型伪造的员工编号、角色或部门。
- 会话历史、运行事件、checkpoint 和 Trace 使用 Redis 优先存储，Redis 不可用时保留进程内存兜底。
- Trace 记录 `load_memory`、`route`、`supervisor_plan`、`delegate_*`、`model`、`tool`、`supervisor_synthesis` 节点，包含子 Agent、模型轮次、工具名称、耗时、状态和异常码，并在 Java Backend 落库摘要。
- 最多循环 `HR_AGENT_MODEL_MAX_TURNS` 轮，超出则返回 `MAX_TOOL_TURNS_EXCEEDED`。

## 事件流（SSE）

```text
status -> route -> supervisor_plan -> agent_start -> status -> tool_start -> tool_result -> agent_result -> synthesis_delta -> answer_delta -> answer -> trace -> done
```

工具调用失败、模型未配置等错误时追加 `error` 事件并以 `done(status=FAILED)` 结束。
