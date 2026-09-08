package com.lnxx.hr.interfaceapi;

import com.lnxx.hr.agentgateway.AgentGateway;
import com.lnxx.hr.agentgateway.AgentResponse;
import com.lnxx.hr.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentGateway agentGateway;
    private final List<ModelOption> models;

    public AgentController(AgentGateway agentGateway,
                           @Value("${hr-agent.agent.models:${CHAT_MODEL_NAME:deepseek-chat}}") String models,
                           @Value("${CHAT_MODEL_NAME:deepseek-chat}") String activeModel) {
        this.agentGateway = agentGateway;
        this.models = Arrays.stream(models.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .map(value -> new ModelOption(value, value, value.equals(activeModel)))
                .toList();
    }

    @GetMapping("/models")
    List<ModelOption> models() {
        return models;
    }

    @PostMapping("/chat")
    AgentResponse chat(@Valid @RequestBody ChatRequest request,
                       @AuthenticationPrincipal AuthPrincipal principal) {
        return agentGateway.chat(request.requestId(), request.message(), request.conversationId(), request.model(), principal);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@Valid @RequestBody ChatRequest request,
                      @AuthenticationPrincipal AuthPrincipal principal) {
        SseEmitter emitter = new SseEmitter(30_000L);
        CompletableFuture.runAsync(() -> {
            try {
                agentGateway.stream(request.requestId(), request.message(), request.conversationId(), request.model(), principal,
                        event -> emitter.send(SseEmitter.event().name(event.name()).data(event.data())));
                emitter.complete();
            } catch (RuntimeException exception) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("code", "AGENT_SERVICE_UNAVAILABLE",
                                    "message", "Agent 服务暂时不可用", "retryable", true)));
                    emitter.complete();
                } catch (IOException ignored) {
                    emitter.completeWithError(exception);
                }
            }
        });
        return emitter;
    }

    @DeleteMapping("/conversations/{conversationId}")
    void clearConversation(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String conversationId,
                           @AuthenticationPrincipal AuthPrincipal principal) {
        agentGateway.clearConversation(conversationId, principal);
    }

    @GetMapping("/runs/{runId}/events")
    List<Map<String, Object>> runEvents(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String runId,
                                        @RequestParam(defaultValue = "0") int after,
                                        @AuthenticationPrincipal AuthPrincipal principal) {
        return agentGateway.runEvents(runId, after, principal);
    }

    @GetMapping("/runs/{runId}/checkpoint")
    Map<String, Object> checkpoint(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String runId,
                                   @AuthenticationPrincipal AuthPrincipal principal) {
        return agentGateway.checkpoint(runId, principal);
    }

    public record ChatRequest(@NotBlank @Size(max = 500) String message,
                              @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestId,
                              @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String conversationId,
                              @Size(max = 80) String model) {}
    public record ModelOption(String id, String label, boolean active) {}
}
