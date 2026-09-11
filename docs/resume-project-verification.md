# 人力知识 Agent 项目功能核验指南

本指南用于按当前仓库的真实能力进行演示和面试核验。它只对应简历中的“人力知识 Agent 平台”，不用于证明其他项目经历。

## 演示准备

按照项目根目录 `README.md` 启动 Python Agent、Java Backend、Java MCP Server、Redis、MySQL、Milvus 和 Vue 前端，访问 `http://localhost:5173`。默认演示链路为 Python Agent + MCP 工具链，需要先配置 OpenAI 兼容模型；`demo-rule-agent` 可用于稳定离线评测。

## 员工问答

使用 `employee / employee123` 登录并依次提问：

| 问题 | 预期意图 | 预期工具或结果 |
|---|---|---|
| 查询我的员工档案 | `EMPLOYEE_PROFILE` | 模型选择 `employee_profile`，MCP 返回员工编号 E1001 |
| 我还有多少年假？ | `LEAVE_BALANCE` | 模型选择 `leave_balance`，MCP 返回剩余 7.0 天 |
| 查询我的考勤 | `ATTENDANCE` | 模型选择 `attendance_summary`，MCP 返回 2026-09 汇总 |
| 我的审批进度 | `APPROVAL_STATUS` | 模型选择 `approval_status`，MCP 返回两个审批记录 |
| 研发部可以弹性上班吗？ | `KNOWLEDGE_SEARCH` | 返回 `[KB-RD-001-1]` |

每条回答下方应显示意图、工具、引用、当前模型名和 Trace ID。

## 权限验证

1. 员工打开知识库，应看到公共制度和研发部制度，但看不到 `HR-001`。
2. 员工界面不提供新增知识按钮，直接调用新增接口也应返回 403。
3. 使用 `hr / hr123456` 或 `admin / admin123456` 登录，应能看到 HR_ONLY 知识并新增知识。
4. 新增部门知识后，只有对应部门成员可以查看和检索。

这组操作验证的是角色、部门和个人级知识权限。

## SSE 与 Trace

在浏览器开发者工具中查看 `/api/agent/chat/stream`，正常工具请求应出现：

```text
status -> route -> supervisor_plan -> agent_start -> status -> tool_start -> tool_result -> agent_result -> synthesis_delta -> answer_delta -> answer -> trace -> done
```

完成问答后打开“执行追踪”，核对意图、模型名、工具、引用、总耗时和 Trace ID。Python Agent 内部 Trace 包含 `load_memory`、`route`、`supervisor_plan`、`delegate_*`、`model`、`tool`、`supervisor_synthesis` 等节点，并记录子 Agent、模型轮次、工具耗时、节点状态和异常码；Java Backend 会保存 Python 侧传回的 Trace 节点摘要。

## 多 Agent 验证

复合问题会由 Supervisor 同时调度多个子 Agent。例如：

| 问题 | 预期子 Agent | 预期工具 |
|---|---|---|
| 我还剩多少年假？顺便查一下我的审批进度。 | `LeaveAgent`、`ApprovalAgent` | `hr_get_leave_balance`、`hr_get_approval_status` |
| 帮我查询员工档案和当月考勤汇总。 | `ProfileAgent`、`AttendanceAgent` | `hr_get_employee_profile`、`hr_get_attendance_summary` |
| 研发部弹性上班制度是什么？我还有多少年假？ | `PolicyRagAgent`、`LeaveAgent` | `knowledge_search`、`hr_get_leave_balance` |

子 Agent 采用类 DeepSeek Harness 的插件化清单管理，`agent-service/src/hr_agent/graph/agent_plugins.json` 中声明了 6 个 Agent 的意图、关键词和工具白名单。核验时可以修改或新增清单项，再观察 `supervisor_plan` 事件和 Trace 中的 `delegate_*` 节点是否同步变化。

## 局限验证

- 停止模型服务后真实模型请求返回 `MODEL_NOT_CONFIGURED` 或模型连接错误；`demo-rule-agent` 只用于演示和离线评测。
- 停止 Java MCP Server 后工具调用返回 `MCP_CLIENT_ERROR`，说明当前 MCP 链路不再 fallback 到 Java 内部工具接口。
- 停止 Redis 后会话、run events、checkpoint 和 trace 会使用进程内存兜底；恢复 Redis 后可验证 Redis 优先存储。
- 停止 Milvus 或未配置 Embedding 时，知识检索保留 MySQL 关键词召回；Milvus 可用时走向量召回 + 关键词召回 + RRF 融合。

## 评测验证

默认评测使用 `evals/datasets/generated_700` 下的 700 条规模化 JSONL 用例，同时保留 53 条人工核心回归集用于快速筛选；700 条用例由人工业务骨架、HR 问法模板和确定性变体生成，覆盖普通对话、结构化 HR 查询、HR 权限、安全越权、多轮记忆、SSE/checkpoint、多 Agent 复合任务和真实模型/RAG 抽样等 8 类场景。规则指标校验意图、工具、子 Agent 路由、答案片段、引用、事件顺序、checkpoint 回放、敏感泄露、`faithfulness`、`context_precision`、`context_recall` 和 `traceability_accuracy`；历史核心回归报告保持全通过，可通过 `--dataset structured_hr.jsonl` 等参数单独回归 53 条人工集。

启用 LLM-as-a-Judge 时，先配置 `JUDGE_MODEL_BASE_URL`、`JUDGE_MODEL_API_KEY`、`JUDGE_MODEL_NAME`，再运行：

```powershell
.\scripts\run-agent-eval.ps1 -Judge
```

真实 LLM/RAG 场景可运行：

```powershell
.\scripts\run-agent-eval.ps1 -IncludeRealModels -Judge
```

裁判模型按 `correctness`、`groundedness`、`permission_safety`、`usefulness` 四个维度输出 0-1 分，默认 `overall >= 0.75` 判为通过；报告会额外生成 `LLM Judge Pass Rate`、`LLM Judge Overall Avg`、四维均分、`overallAccuracy` 综合准确率、失败诊断和 `evals/reports/dashboard.html` 可视化看板。`overallAccuracy` 使用所有 `*_accuracy` 检查的 micro-average，即 `通过的准确性检查数 / 全部准确性检查数`，不是多个百分比的简单平均。简历中的“系统忠实度 71% 提升到 85%，上下文精确、召回 > 90%”对应 `faithfulness`、`context_precision`、`context_recall` 与 `judge.dimensionAvg.groundedness` 等报告字段。

## 与简历一致的表述

建议技术栈：

> Java、Spring Boot、Spring MVC、Spring Security、MyBatis、Flyway、H2/MySQL、SSE、Python、FastAPI、Pydantic、OpenAI 兼容模型工具调用、权限感知关键词检索、结构化工具调用

可以描述 Python Agent、MCP Client/Server、Redis 会话与运行事件、Milvus 向量召回、RRF 融合、多模型配置、Trace 节点记录、敏感字段脱敏、类 Harness 插件化 Agent 清单和 LLM-as-a-Judge 评测框架。不要把当前自定义 HMAC token 写成标准 JWT，也不要把 `demo-rule-agent` 写成真实大模型能力；未实际运行裁判模型前，不要写具体裁判分数。
