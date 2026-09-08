package com.lnxx.hr.agentgateway;

import com.lnxx.hr.security.AuthPrincipal;

import java.util.List;
import java.util.Map;

public interface AgentGateway {
    AgentResponse chat(String requestId, String message, String conversationId, String model, AuthPrincipal principal);
    void stream(String requestId, String message, String conversationId, String model, AuthPrincipal principal, AgentStreamConsumer consumer);
    void clearConversation(String conversationId, AuthPrincipal principal);
    default List<Map<String, Object>> runEvents(String runId, int after, AuthPrincipal principal) {
        return List.of();
    }
    default Map<String, Object> checkpoint(String runId, AuthPrincipal principal) {
        return Map.of("status", "UNSUPPORTED", "runId", runId);
    }
}
