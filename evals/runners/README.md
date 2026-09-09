# Eval Runners

## Agent 行为评测

默认只运行不依赖真实模型 API 的 case，当前为 41 条离线回归用例：

```powershell
cd G:\ProjectinSummer\lnxx-hr-agent
.\scripts\run-agent-eval.ps1
```

指定单个测试集：

```powershell
.\scripts\run-agent-eval.ps1 -Dataset structured_hr
```

包含真实模型 case：

```powershell
.\scripts\run-agent-eval.ps1 -IncludeRealModels
```

启用 LLM-as-a-Judge：

```powershell
.\scripts\run-agent-eval.ps1 -Judge
```

默认评测集为 `evals/datasets/generated_700` 下的 700 条规模化用例；根目录 `evals/datasets/*.jsonl` 保留 53 条人工核心回归集，可用 `--dataset structured_hr.jsonl` 等参数单独筛选。报告会输出规则指标、各数据集通过率、`judge_pass_rate`、`overall_avg`、四维均分，以及 `faithfulness`、`context_precision`、`context_recall`、`traceability_accuracy` 等简历口径指标。

输出：

- `evals/reports/latest.json`
- `evals/reports/latest.md`
- `evals/reports/dashboard.html`
