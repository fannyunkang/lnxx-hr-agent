# HR Agent 评测集说明

本目录保存 HR Agent 的行为评测集。测试集采用 JSONL：一行一个 case，当前共 53 条，方便追加、筛选和失败重跑。

## 目录

- `datasets/general.jsonl`：普通对话，不应调用业务工具。
- `datasets/structured_hr.jsonl`：员工本人结构化数据查询。
- `datasets/hr_privileged.jsonl`：HR/ADMIN 查询授权范围内的其他员工。
- `datasets/security.jsonl`：越权、角色伪造、提示词注入、敏感信息泄露测试。
- `datasets/rag.jsonl`：制度知识库/RAG 问答与引用测试。
- `datasets/memory.jsonl`：多轮上下文记忆、会话隔离测试。
- `datasets/multi_agent.jsonl`：Supervisor 多 Agent 调度、复合任务拆解与汇总回答测试。
- `datasets/sse_checkpoint.jsonl`：SSE 事件顺序、checkpoint 与 replay 测试。
- `runners/run_agent_eval.py`：自动执行评测并生成报告。
- `reports/latest.json` / `reports/latest.md`：最近一次评测报告。
- LLM-as-a-Judge：可选裁判模型评测层，基于 OpenAI-compatible `/chat/completions` 对回答质量打分。

## 通用字段

- `id`：稳定 case id。
- `category`：测试类别。
- `username` / `password`：登录账号。
- `model`：测试模型；稳定演示优先用 `demo-rule-agent`，真实模型对比用 `deepseek-chat`、`qwen-plus`、`doubao-pro-32k` 等。
- `question`：单轮问题。
- `conversation`：多轮问题列表，主要用于 memory 类。
- `expectedIntent`：期望意图。
- `expectedTools`：期望工具调用。
- `expectedAgents`：期望 Supervisor 调度的子 Agent 列表。
- `expectedAnswerContains`：最终答案应包含的片段。
- `forbiddenAnswerContains`：最终答案不允许包含的片段。
- `expectedAllowed`：权限预期。
- `expectedEvents`：SSE 应出现的事件序列。
- `expectedCheckpointStatus`：checkpoint 最终阶段。
- `requiresRealModel`：是否依赖真实 LLM API；默认 runner 会跳过这类 case。
- `judgeRubric`：可选，覆盖默认裁判标准；不配置时按 case 类别使用内置 rubric。
- `judge`：可选，设为 `false` 时该 case 不进入 LLM-as-a-Judge 评分。

## 主要指标

- `intent_accuracy`：意图命中率。
- `tool_accuracy`：工具选择命中率。
- `answer_hit_rate`：答案关键片段命中率。
- `unauthorized_block_rate`：越权 case 拦截率。
- `sensitive_leakage_rate`：敏感字段未泄露比例。
- `citation_precision`：引用是否来自本次召回结果。
- `event_order_accuracy`：SSE 事件顺序是否满足预期。
- `replay_success_rate`：`/runs/{runId}/events?after=N` 是否可回放。
- `checkpoint_availability`：`/runs/{runId}/checkpoint` 是否可查询。
- `supervisor_routing_accuracy`：主 Agent 是否调度了期望子 Agent。
- `multi_agent_task_coverage`：复合问题中的子任务是否被完整覆盖。
- `child_agent_tool_accuracy`：子 Agent 工具选择是否命中预期。
- `llm_judge_pass_rate`：裁判模型 `overall` 分数是否达到阈值，默认阈值为 `0.75`。
- `judge_overall_avg`：裁判模型对已评测 case 的平均总分。
- `judge_dimension_avg`：裁判模型在 `correctness`、`groundedness`、`permission_safety`、`usefulness` 四个维度的平均分。

## LLM-as-a-Judge

评测 runner 保留确定性规则指标，同时支持裁判模型从 `correctness`、`groundedness`、`permission_safety`、`usefulness` 四个维度输出 0-1 分、总分和一句评语。报告会汇总规则通过率、各数据集通过率、`judge_pass_rate`、`overall_avg` 和四维均分。裁判模型只读取测试问题、期望工具/意图、禁止泄露片段、实际回答、工具、引用等评测上下文，不影响 Agent 本身执行链路。

配置裁判模型：

```powershell
$env:JUDGE_MODEL_BASE_URL="https://api.openai.com/v1"
$env:JUDGE_MODEL_API_KEY="your-key"
$env:JUDGE_MODEL_NAME="gpt-4.1-mini"
$env:JUDGE_MODEL_THRESHOLD="0.75"
```

## 运行

```powershell
.\scripts\run-agent-eval.ps1
```

启用 LLM-as-a-Judge：

```powershell
.\scripts\run-agent-eval.ps1 -Judge
```

包含真实模型/RAG case：

```powershell
.\scripts\run-agent-eval.ps1 -IncludeRealModels
```

同时运行真实模型和裁判模型：

```powershell
.\scripts\run-agent-eval.ps1 -IncludeRealModels -Judge
```
