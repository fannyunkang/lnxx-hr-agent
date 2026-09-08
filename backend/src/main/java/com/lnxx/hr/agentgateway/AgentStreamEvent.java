package com.lnxx.hr.agentgateway;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentStreamEvent(String name, JsonNode data) {
}
