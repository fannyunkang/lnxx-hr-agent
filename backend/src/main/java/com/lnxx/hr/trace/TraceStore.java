package com.lnxx.hr.trace;

import com.lnxx.hr.infrastructure.mybatis.TraceMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class TraceStore {
    private static final String SEPARATOR = "\u001f";
    private final TraceMapper mapper;
    private final ObjectMapper json;

    public TraceStore(TraceMapper mapper, ObjectMapper json) { this.mapper = mapper; this.json = json; }

    public void save(AgentTrace trace) {
        mapper.delete(trace.traceId());
        mapper.insert(trace.traceId(), trace.requestId(), trace.username(), trace.intent(), String.join(SEPARATOR, trace.tools()),
                String.join(SEPARATOR, trace.citations()), trace.durationMs(),
                LocalDateTime.ofInstant(trace.createdAt(), ZoneOffset.UTC), trace.runtime(), trace.model(),
                trace.status(), writeNodes(trace.nodes()), trace.retries(), trace.errorCode());
    }

    public Optional<AgentTrace> find(String traceId) {
        return Optional.ofNullable(mapper.find(traceId)).map(this::toDomain);
    }

    public List<AgentTrace> recentFor(String username) {
        return mapper.recent(username).stream().map(this::toDomain).toList();
    }

    private AgentTrace toDomain(TraceMapper.TraceRow row) {
        return new AgentTrace(row.traceId(), row.requestId(), row.username(), row.intent(), row.runtime(),
                row.model(), row.status(), split(row.tools()), split(row.citations()), readNodes(row.nodesJson()),
                row.retries(), row.errorCode(), row.durationMs(), row.createdAt().toInstant(ZoneOffset.UTC));
    }

    private List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(SEPARATOR, -1));
    }

    private String writeNodes(List<TraceNode> nodes) {
        try { return json.writeValueAsString(nodes); } catch (Exception exception) { return "[]"; }
    }

    private List<TraceNode> readNodes(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return json.readValue(value, new TypeReference<>() {}); } catch (Exception exception) { return List.of(); }
    }
}
