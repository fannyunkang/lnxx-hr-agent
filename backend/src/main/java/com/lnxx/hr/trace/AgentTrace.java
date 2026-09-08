package com.lnxx.hr.trace;

import java.time.Instant;
import java.util.List;

public record AgentTrace(String traceId, String requestId, String username, String intent, String runtime,
                         String model, String status, List<String> tools, List<String> citations,
                         List<TraceNode> nodes, int retries, String errorCode,
                         long durationMs, Instant createdAt) {
    public AgentTrace(String traceId, String username, String intent, List<String> tools,
                      List<String> citations, long durationMs, Instant createdAt) {
        this(traceId, null, username, intent, "python", null, "COMPLETED", tools, citations,
                List.of(), 0, null, durationMs, createdAt);
    }
}
