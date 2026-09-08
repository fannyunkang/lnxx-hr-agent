# HR Agent Eval Report

- Generated At: `2026-09-05T13:03:49.891391+00:00`
- Total: 32
- Executed: 23
- Skipped: 9
- Passed: 23
- Failed: 0
- Pass Rate: 100.00%

## Metrics

| Metric | Score |
|---|---:|
| answer_hit_rate | 100.00% |
| event_order_accuracy | 100.00% |
| http_ok | 100.00% |
| intent_accuracy | 100.00% |
| sensitive_leakage_rate | 100.00% |
| sse_answer | 100.00% |
| tool_accuracy | 100.00% |
| tool_result_status | 100.00% |
| turn_1_answer_hit_rate | 100.00% |
| turn_1_http_ok | 100.00% |
| turn_1_intent_accuracy | 100.00% |
| turn_1_sse_answer | 100.00% |
| turn_1_tool_accuracy | 100.00% |
| turn_2_answer_hit_rate | 100.00% |
| turn_2_http_ok | 100.00% |
| turn_2_intent_accuracy | 100.00% |
| turn_2_sse_answer | 100.00% |
| turn_2_tool_accuracy | 100.00% |

## By Dataset

| Dataset | Total | Passed | Failed | Pass Rate |
|---|---:|---:|---:|---:|
| general.jsonl | 3 | 3 | 0 | 100.00% |
| hr_privileged.jsonl | 5 | 5 | 0 | 100.00% |
| memory.jsonl | 4 | 4 | 0 | 100.00% |
| security.jsonl | 4 | 4 | 0 | 100.00% |
| sse_checkpoint.jsonl | 3 | 3 | 0 | 100.00% |
| structured_hr.jsonl | 4 | 4 | 0 | 100.00% |

## Failures

No failures.

## Skipped

- `general_004` from `general.jsonl`: requiresRealModel
- `rag_leave_policy_001` from `rag.jsonl`: requiresRealModel
- `rag_attendance_policy_001` from `rag.jsonl`: requiresRealModel
- `rag_department_policy_001` from `rag.jsonl`: requiresRealModel
- `rag_hr_only_denied_001` from `rag.jsonl`: requiresRealModel
- `rag_hr_only_allowed_001` from `rag.jsonl`: requiresRealModel
- `security_prompt_001` from `security.jsonl`: requiresRealModel
- `sse_real_model_001` from `sse_checkpoint.jsonl`: requiresRealModel
- `structured_leave_002` from `structured_hr.jsonl`: requiresRealModel
