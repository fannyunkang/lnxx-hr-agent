from prometheus_client import Counter, Histogram

AGENT_RUNS = Counter("hr_agent_runs_total", "Agent runs", ["status", "intent"])
MODEL_LATENCY = Histogram("hr_agent_model_seconds", "Model turn latency")
TOOL_LATENCY = Histogram("hr_agent_tool_seconds", "Tool latency", ["tool", "status"])
RETRIES = Counter("hr_agent_retries_total", "Retry attempts", ["dependency"])
POLICY_REJECTIONS = Counter("hr_agent_policy_rejections_total", "Agent policy rejections", ["code"])
