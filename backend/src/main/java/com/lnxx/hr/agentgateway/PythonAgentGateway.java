package com.lnxx.hr.agentgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnxx.hr.domain.HrRepository;
import com.lnxx.hr.security.AuthPrincipal;
import com.lnxx.hr.trace.AgentTrace;
import com.lnxx.hr.trace.TraceStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import com.lnxx.hr.trace.TraceNode;

@Component
@ConditionalOnProperty(name = "hr-agent.runtime", havingValue = "python")
public class PythonAgentGateway implements AgentGateway {
    private final RestClient client;
    private final String serviceToken;
    private final HrRepository repository;
    private final TraceStore traceStore;
    private final ObjectMapper objectMapper;

    public PythonAgentGateway(HrRepository repository, TraceStore traceStore, ObjectMapper objectMapper,
                              @Value("${hr-agent.agent.base-url}") String baseUrl,
                              @Value("${hr-agent.agent.service-token}") String serviceToken,
                              @Value("${hr-agent.agent.timeout-seconds:30}") long timeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.serviceToken = serviceToken;
        this.repository = repository;
        this.traceStore = traceStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentResponse chat(String requestId, String message, String requestedConversationId, String model, AuthPrincipal principal) {
        AgentRunRequest request = createRequest(requestId, message, requestedConversationId, model, principal);
        long started = System.nanoTime();
        AgentResponse response = client.post().uri("/internal/v1/agent/runs")
                .header("X-Agent-Service-Token", serviceToken)
                .body(request).retrieve().body(AgentResponse.class);
        if (response == null) throw new IllegalStateException("Python Agent returned an empty response");
        saveTrace(response, principal, started);
        saveDetailedTrace(fetchTrace(response.traceId()), principal, started);
        return response;
    }

    @Override
    public void stream(String requestId, String message, String requestedConversationId, String model, AuthPrincipal principal,
                       AgentStreamConsumer consumer) {
        AgentRunRequest request = createRequest(requestId, message, requestedConversationId, model, principal);
        long started = System.nanoTime();
        client.post().uri("/internal/v1/agent/runs/stream")
                .header("X-Agent-Service-Token", serviceToken)
                .body(request)
                .exchange((httpRequest, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("Python Agent stream returned " + response.getStatusCode());
                    }
                    consumeEvents(response.getBody(), consumer, principal, started);
                    return null;
                });
    }

    @Override
    public void clearConversation(String conversationId, AuthPrincipal principal) {
        UserContext userContext = createUserContext(principal);
        client.post().uri("/internal/v1/agent/conversations/{conversationId}/clear", conversationId)
                .header("X-Agent-Service-Token", serviceToken)
                .body(new ConversationClearRequest(UUID.randomUUID().toString(), userContext))
                .retrieve().toBodilessEntity();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> runEvents(String runId, int after, AuthPrincipal principal) {
        return client.get().uri("/internal/v1/agent/runs/{runId}/events?after={after}", runId, after)
                .header("X-Agent-Service-Token", serviceToken)
                .retrieve().body(List.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> checkpoint(String runId, AuthPrincipal principal) {
        return client.get().uri("/internal/v1/agent/runs/{runId}/checkpoint", runId)
                .header("X-Agent-Service-Token", serviceToken)
                .retrieve().body(Map.class);
    }

    private AgentRunRequest createRequest(String requestedRequestId, String message, String requestedConversationId, String model, AuthPrincipal principal) {
        String requestId = requestedRequestId == null || requestedRequestId.isBlank()
                ? UUID.randomUUID().toString() : requestedRequestId;
        String conversationId = requestedConversationId == null || requestedConversationId.isBlank()
                ? UUID.randomUUID().toString() : requestedConversationId;
        return new AgentRunRequest(requestId, conversationId, message,
                createUserContext(principal), model);
    }

    private UserContext createUserContext(AuthPrincipal principal) {
        String department = repository.employee(principal.employeeId())
                .map(employee -> employee.department()).orElse("");
        return new UserContext(principal.username(), principal.employeeId(), principal.role().name(), department);
    }

    private void consumeEvents(java.io.InputStream body, AgentStreamConsumer consumer,
                               AuthPrincipal principal, long started) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String eventName = null;
            StringBuilder eventData = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    dispatchEvent(eventName, eventData, consumer, principal, started);
                    eventName = null;
                    eventData.setLength(0);
                } else if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (!eventData.isEmpty()) eventData.append('\n');
                    eventData.append(line.substring(5).trim());
                }
            }
            dispatchEvent(eventName, eventData, consumer, principal, started);
        }
    }

    private void dispatchEvent(String name, StringBuilder data, AgentStreamConsumer consumer,
                               AuthPrincipal principal, long started) throws IOException {
        if (name == null || data.isEmpty()) return;
        JsonNode json = objectMapper.readTree(data.toString());
        consumer.accept(new AgentStreamEvent(name, json));
        if ("answer".equals(name)) {
            saveTrace(objectMapper.treeToValue(json, AgentResponse.class), principal, started);
        } else if ("trace".equals(name)) {
            saveDetailedTrace(json, principal, started);
        }
    }

    private void saveTrace(AgentResponse response, AuthPrincipal principal, long started) {
        long durationMs = (System.nanoTime() - started) / 1_000_000;
        traceStore.save(new AgentTrace(response.traceId(), null, principal.username(), response.intent(),
                "python", response.model(), "COMPLETED", response.tools(), response.citations(),
                java.util.List.of(), 0, null, durationMs, Instant.now()));
    }

    private void saveDetailedTrace(JsonNode trace, AuthPrincipal principal, long started) {
        if (trace == null || trace.isMissingNode() || trace.isNull()) return;
        String traceId = text(trace, "trace_id");
        if (traceId.isBlank()) return;
        List<TraceNode> nodes = new ArrayList<>();
        long nodeDuration = 0;
        for (JsonNode node : trace.path("nodes")) {
            Instant startedAt = instant(text(node, "started_at"));
            Instant finishedAt = instant(text(node, "ended_at"));
            long durationMs = node.path("duration_ms").asLong(0);
            nodeDuration += durationMs;
            String summary = node.path("summary").isMissingNode() ? "{}" : node.path("summary").toString();
            nodes.add(new TraceNode(text(node, "name"), startedAt, finishedAt, durationMs,
                    summary, "", text(node, "status"), nullableText(node, "error_code")));
        }
        long totalDuration = nodeDuration > 0 ? nodeDuration : (System.nanoTime() - started) / 1_000_000;
        traceStore.save(new AgentTrace(traceId, text(trace, "request_id"), principal.username(),
                textOr(trace, "intent", "GENERAL"), textOr(trace, "runtime", "python"),
                text(trace, "model"), textOr(trace, "status", "COMPLETED"),
                stringList(trace.path("tools")), stringList(trace.path("citations")), nodes,
                trace.path("retries").asInt(0), nullableText(trace, "error_code"),
                totalDuration, instant(text(trace, "created_at"))));
    }

    private List<String> stringList(JsonNode values) {
        List<String> result = new ArrayList<>();
        if (values.isArray()) {
            values.forEach(value -> {
                if (value.isTextual()) result.add(value.asText());
            });
        }
        return result;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private String textOr(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value.isBlank() ? fallback : value;
    }

    private String nullableText(JsonNode node, String field) {
        String value = text(node, field);
        return value.isBlank() ? null : value;
    }

    private Instant instant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.now() : Instant.parse(value);
        } catch (RuntimeException exception) {
            return Instant.now();
        }
    }

    private JsonNode fetchTrace(String traceId) {
        try {
            return client.get().uri("/internal/v1/agent/traces/{traceId}", traceId)
                    .header("X-Agent-Service-Token", serviceToken)
                    .retrieve().body(JsonNode.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    record AgentRunRequest(String requestId, String conversationId, String message, UserContext userContext, String model) {}
    record ConversationClearRequest(String requestId, UserContext userContext) {}
    record UserContext(String username, String employeeId, String role, String department) {}
}
