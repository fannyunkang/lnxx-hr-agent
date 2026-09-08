package com.lnxx.hr.agentgateway;

import java.util.List;

public record AgentResponse(String requestId, String answer, String intent, List<String> tools,
                            List<String> citations, String traceId, String conversationId,
                            String model) {
}
