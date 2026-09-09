# HR Agent 评测集说明

本目录保存 HR Agent 的行为评测集。测试集采用 JSONL：一行一个 case。当前包含两套数据：

- `datasets/*.jsonl`：53 条人工核心回归集，用于快速验证关键链路。
- `datasets/generated_700/*.jsonl`：700 条规模化评测集，默认 runner 会优先加载这一套，用于对齐简历中的 LLM-as-a-Judge、忠实度、上下文精确/召回和可追溯评测口径。

## 目录

- `datasets/general.jsonl`：普通对话，不应调用业务工具。
- `datasets/structured_hr.jsonl`：员工本人结构化数据查询。
- `datasets/hr_privileged.jsonl`：HR/ADMIN 查询授权范围内的其他员工。
- `datasets/security.jsonl`：越权、角色伪造、提示词注入、敏感信息泄露测试。
- `datasets/rag.jsonl`：制度知识库/RAG 问答与引用测试。
- `datasets/memory.jsonl`：多轮上下文记忆、会话隔离测试。
- `datasets/multi_agent.jsonl`：Supervisor 多 Agent 调度、复合任务拆解与汇总回答测试。
- `datasets/sse_checkpoint.jsonl`：SSE 事件顺序、checkpoint 与 replay 测试。
- `datasets/generated_700/`：由 `generators/build_700_case_suite.py` 生成的 700 条完整评测集；配额为普通对话 70、本人 HR 查询 140、HR 授权查询 105、权限越权 140、RAG 105、多轮记忆 70、多 Agent 50、SSE/checkpoint 20。
- `generators/build_700_case_suite.py`：基于人工业务骨架、真实 HR 问法模板和确定性变体生成规模化 JSONL。
- `runners/run_agent_eval.py`：自动执行评测并生成报告。
- `reports/latest.json` / `reports/latest.md` / `reports/dashboard.html`：最近一次评测报告和可视化指标看板。
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
- `faithfulness`：答案是否忠实于工具结果或知识库引用，不编造 HR 数据或制度条款。
- `context_precision`：答案、工具和引用是否只包含当前问题需要的上下文，不混入无关工具、跨会话内容或禁止泄露字段。
- `context_recall`：答案是否覆盖当前问题要求的关键字段、工具和子 Agent 任务。
- `event_order_accuracy`：SSE 事件顺序是否满足预期。
- `replay_success_rate`：`/runs/{runId}/events?after=N` 是否可回放。
- `checkpoint_availability`：`/runs/{runId}/checkpoint` 是否可查询。
- `traceability_accuracy`：评测结果是否具备 route、answer、trace、done 等可追溯链路信息。
- `supervisor_routing_accuracy`：主 Agent 是否调度了期望子 Agent。
- `multi_agent_task_coverage`：复合问题中的子任务是否被完整覆盖。
- `child_agent_tool_accuracy`：子 Agent 工具选择是否命中预期。
- `llm_judge_pass_rate`：裁判模型 `overall` 分数是否达到阈值，默认阈值为 `0.75`。
- `judge_overall_avg`：裁判模型对已评测 case 的平均总分。
- `judge_dimension_avg`：裁判模型在 `correctness`、`groundedness`、`permission_safety`、`usefulness` 四个维度的平均分。
- `overallAccuracy`：综合准确率，按所有 `*_accuracy` 类检查做 micro-average，公式为 `通过的准确性检查数 / 全部准确性检查数`，覆盖意图、工具、权限、引用、子 Agent、会话隔离和 Trace 完整性。

## LLM-as-a-Judge

评测 runner 保留确定性规则指标，同时支持裁判模型从 `correctness`、`groundedness`、`permission_safety`、`usefulness` 四个维度输出 0-1 分、总分和一句评语。报告会汇总规则通过率、各数据集通过率、`judge_pass_rate`、`overall_avg` 和四维均分。裁判模型只读取测试问题、期望工具/意图、禁止泄露片段、实际回答、工具、引用、Trace ID 和关键 SSE 事件等评测上下文，不影响 Agent 本身执行链路。

`faithfulness`、`context_precision`、`context_recall` 同时由规则指标和 Judge 质量分支支撑：规则层负责可定位的硬约束，Judge 层负责自然语言答案是否忠实、完整、有用的软评分。简历中的“系统忠实度 71% 提升到 85%，上下文精确、召回 > 90%”对应 `reports/latest.json` 中的 `faithfulness`、`context_precision`、`context_recall` 和 `judge.dimensionAvg.groundedness` 等字段。

## 可视化与综合准确率

每次 runner 写出报告时会同步生成 `reports/dashboard.html`，包含核心分数卡、指标条形图、按数据集/类别通过率、Judge 四维均分和失败诊断表。

综合准确率不是把 `intent_accuracy`、`tool_accuracy` 等百分比做简单平均，而是对所有准确性检查做 micro-average。例如 700 条 case 里一共产生 2,100 个准确性检查，通过 1,995 个，则：

```text
overallAccuracy = 1995 / 2100 = 95.00%
```

这种方式能让多轮、多 Agent、权限和引用类检查都按真实检查次数计入最终准确率。

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

默认存在 `datasets/generated_700/` 时执行 700 条规模化评测；如需只跑 53 条人工核心回归集，可显式指定数据集文件，例如：

```powershell
python evals\runners\run_agent_eval.py --dataset structured_hr.jsonl --dataset security.jsonl
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
