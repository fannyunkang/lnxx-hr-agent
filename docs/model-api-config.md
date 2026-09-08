# 真实大模型 API 配置说明

当前项目支持两条模型调用链路：

1. `AGENT_RUNTIME=python`：Java Backend 负责认证和业务 API，Python `agent-service` 负责 Agent 编排、工具调用和 LLM 对话。
2. `AGENT_RUNTIME=spring-ai`：Java Backend 直接通过 Spring AI 调用模型。

默认推荐先使用 Python Agent 链路，因为它已经包含多模型切换、MCP Client、工具编排、Trace、SSE 和 checkpoint。

## Python Agent 模型配置

复制模板：

```powershell
Copy-Item .\scripts\model-api.example.ps1 .\scripts\model-api.local.ps1
```

编辑 `scripts/model-api.local.ps1`，填入真实 API Key，然后在同一个终端执行：

```powershell
. .\scripts\model-api.local.ps1
.\scripts\start-agent.ps1
.\scripts\start-backend-python-agent.ps1
```

前端下拉框来自 Java Backend 的：

```text
GET /api/agent/models
```

对应环境变量：

```powershell
$env:AGENT_MODELS="deepseek-chat,qwen-plus,qwen-max,doubao-pro-32k,doubao-lite-32k,gpt-4o-mini,demo-rule-agent"
$env:HR_AGENT_MODEL_OPTIONS=$env:AGENT_MODELS
```

## Provider 映射

Python Agent 根据模型名前缀选择 endpoint：

| 模型名 | 使用配置 |
|---|---|
| `deepseek-*` 或其他普通模型 | `HR_AGENT_DEEPSEEK_*`，或 fallback 到 `HR_AGENT_MODEL_*` |
| `qwen-*` | `HR_AGENT_QWEN_*` |
| `doubao-*` | `HR_AGENT_DOUBAO_*` |
| `demo-rule-agent` | 本地规则模型，不调用外部 API |

## Java Spring AI 模型配置

当你想测试 Java Spring AI 链路：

```powershell
$env:AGENT_RUNTIME="spring-ai"
$env:CHAT_MODEL_BASE_URL="https://api.deepseek.com"
$env:CHAT_MODEL_API_KEY="replace-with-your-chat-key"
$env:CHAT_MODEL_NAME="deepseek-chat"
.\scripts\start-backend-python-agent.ps1
```

Embedding 单独配置：

```powershell
$env:EMBEDDING_MODEL_BASE_URL="https://api.openai.com"
$env:EMBEDDING_MODEL_API_KEY="replace-with-your-embedding-key"
$env:EMBEDDING_MODEL_NAME="text-embedding-3-small"
$env:EMBEDDING_DIMENSION="1536"
```

## 验证真实模型

默认评测会跳过真实模型 case。要验证真实 LLM：

```powershell
.\scripts\run-agent-eval.ps1 -IncludeRealModels
```

如果没有配置对应 provider 的 API Key，真实模型 case 会失败并提示 `MODEL_NOT_CONFIGURED` 或鉴权错误。

## 安全提醒

- 不要把真实 API Key 提交到仓库。
- 如果 Key 已经出现在终端截图、日志或聊天记录里，建议立即到供应商控制台轮换。
- 本项目模板只放占位符；真实值建议放在本地 `scripts/model-api.local.ps1`、系统环境变量或密钥管理服务中。
