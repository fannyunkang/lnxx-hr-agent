# Java 与 Python 内部接口

机器可读契约位于 `contracts/backend-agent.openapi.yaml`。

## 调用方向

- Java → Python：`POST /internal/v1/agent/runs`，执行一次 Agent 请求。
- Java → Python：`POST /internal/v1/agent/runs/stream`，返回 SSE 事件。
- Python → Java：`POST /internal/v1/tools/{toolName}`，执行结构化 HR 工具。

所有内部请求必须携带 `X-Agent-Service-Token`。Java 使用 `AGENT_SERVICE_TOKEN`，Python 使用 `HR_AGENT_SERVICE_TOKEN`，两个值必须一致。当前本地开发使用共享密钥；生产环境应改为更严格的服务认证。

Python 不接受浏览器用户 Token。Java 完成用户鉴权后生成 `UserContext`，Python 调用工具时原样传回 Java，最终数据访问边界仍由 Java 控制。

## 当前工具

- `employee_profile`
- `attendance_summary`
- `leave_balance`
- `approval_status`
- `knowledge_search`：权限过滤后的关键词检索，最多返回三条结果

这些接口是固定结构化工具，不是 MCP Server。Python Agent 通过 OpenAI 兼容 Function Calling 调用它们：模型只负责选择工具，参数由模型按 schema 生成。

