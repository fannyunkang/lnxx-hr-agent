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

完整评测集共 53 条，报告会输出规则指标、各数据集通过率、`judge_pass_rate`、`overall_avg` 和四维均分。

输出：

- `evals/reports/latest.json`
- `evals/reports/latest.md`
