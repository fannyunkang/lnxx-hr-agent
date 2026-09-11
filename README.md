# 人力知识 Agent 平台

这是一个 Vue + Java 21 + Python Agent + MCP + Redis + MySQL + Milvus + PaddleOCR 的人力知识 Agent。默认完整演示链路由 Python `agent-service` 负责 Agent 编排，Java 负责公开 API、认证和业务数据；Spring AI 运行时仍可切换使用。

## 一键启动

准备 Docker Desktop，并配置聊天模型和 Embedding 模型：

```powershell
$env:CHAT_MODEL_BASE_URL="https://api.openai.com"
$env:CHAT_MODEL_API_KEY="your-key"
$env:CHAT_MODEL_NAME="gpt-4.1-mini"
$env:AGENT_MODELS="gpt-4.1-mini,deepseek-chat,qwen-plus"
$env:EMBEDDING_MODEL_BASE_URL="https://api.openai.com"
$env:EMBEDDING_MODEL_API_KEY="your-key"
$env:EMBEDDING_MODEL_NAME="text-embedding-3-small"
$env:EMBEDDING_DIMENSION="1536"
$env:MILVUS_INITIALIZE_SCHEMA="true"
docker compose up -d --build
```

也可以直接使用脚本入口：

```powershell
.\scripts\start-all.ps1
```

访问 `http://localhost:5173`。演示账号：员工 `employee / employee123`、员工 `employee2 / employee234`、HR `hr / hr123456`、管理员 `admin / admin123456`。首次构建 PaddleOCR 镜像会下载较大的运行依赖。

检查状态：

```powershell
docker compose ps
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8866/health
```

## 本地开发

先只启动依赖：

```powershell
docker compose up -d mysql redis etcd minio milvus ocr mcp-server
```

再启动 Java 和 Vue：

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:CHAT_MODEL_API_KEY="your-key"
$env:EMBEDDING_MODEL_API_KEY="your-key"
.\scripts\start-backend-python-agent.ps1

cd frontend
npm install
npm run dev
```

本地脚本使用 Python Agent 链路：先运行 `.\scripts\start-agent.ps1`，再运行 `.\scripts\start-backend-python-agent.ps1`。Docker Compose 已默认设置 `AGENT_RUNTIME=python`。如需验证 Java Spring AI 运行时，可显式设置 `AGENT_RUNTIME=spring-ai`。

如果想一键进入本地开发模式，可以运行：

```powershell
.\scripts\start-all.ps1 -Mode local
```

该脚本会用 Docker Compose 启动 MySQL、Redis、Milvus、OCR 和 MCP Server，再在后台启动 Python Agent、Spring Backend、Vue 前端；日志写入 `.run/logs`，PID 写入 `.run/pids`。停止本地后台进程：

```powershell
.\scripts\stop-all.ps1
```

Python Agent 现在负责显式 `model → tools → model` 循环、意图路由事件、Redis 会话窗口、节点级 Trace、重复调用检测、每轮/总工具限额和模型有限重试。内部 Trace 可通过 `GET /internal/v1/agent/traces/{traceId}` 查询，Prometheus 指标位于 Python 服务 `/metrics`。

## 核心接口

- `POST /api/agent/chat`、`POST /api/agent/chat/stream`、`DELETE /api/agent/conversations/{id}`
- `POST /api/knowledge/documents`：上传 PDF/MD/JSON 及权限元数据
- `GET /api/knowledge/jobs/{jobId}`：查询异步索引状态
- `POST /api/knowledge/documents/{id}/reindex`
- `DELETE /api/knowledge/documents/{id}`
- `/actuator/health`、`/actuator/metrics`、`/actuator/prometheus`

SSE 顺序统一为 `status → route → tool_start → tool_result → answer_delta → answer → done/error`。知识引用格式为 `[KB-{documentId}-{version}-{chunkNumber}]`。

## 测试

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.10"
.\.tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=$PWD\.tools\m2-repository" -pl backend,hr-mcp-server -am test

cd frontend
npm ci
npm run build
```

测试 profile 使用 H2，并关闭 Milvus 自动连接；完整集成环境使用 MySQL。更多细节见 [架构说明](docs/architecture.md) 和 [项目核验指南](docs/resume-project-verification.md)。

## 重要环境变量

`CHAT_MODEL_*`、`EMBEDDING_MODEL_*`、`RERANKER_*`、`MYSQL_*`、`REDIS_*`、`MILVUS_*`、`MCP_SERVER_*`、`OCR_SERVICE_*`、`AGENT_RUNTIME`。不要提交真实 API Key。
