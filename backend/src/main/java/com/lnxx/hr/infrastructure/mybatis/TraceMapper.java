package com.lnxx.hr.infrastructure.mybatis;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TraceMapper {
    @Insert("INSERT INTO agent_traces(trace_id, request_id, username, intent, runtime, model, status, tools, citations, nodes_json, retries, error_code, duration_ms, created_at) " +
            "VALUES(#{traceId}, #{requestId}, #{username}, #{intent}, #{runtime}, #{model}, #{status}, #{tools}, #{citations}, #{nodesJson}, #{retries}, #{errorCode}, #{durationMs}, #{createdAt})")
    void insert(@Param("traceId") String traceId, @Param("requestId") String requestId, @Param("username") String username,
                @Param("intent") String intent, @Param("tools") String tools,
                @Param("citations") String citations, @Param("durationMs") long durationMs,
                @Param("createdAt") LocalDateTime createdAt, @Param("runtime") String runtime,
                @Param("model") String model, @Param("status") String status,
                @Param("nodesJson") String nodesJson, @Param("retries") int retries,
                @Param("errorCode") String errorCode);

    @Delete("DELETE FROM agent_traces WHERE trace_id = #{traceId}")
    void delete(String traceId);

    @Select("SELECT trace_id, request_id, username, intent, runtime, model, status, tools, citations, nodes_json, retries, error_code, duration_ms, created_at " +
            "FROM agent_traces WHERE trace_id = #{traceId}")
    TraceRow find(String traceId);

    @Select("SELECT trace_id, request_id, username, intent, runtime, model, status, tools, citations, nodes_json, retries, error_code, duration_ms, created_at " +
            "FROM agent_traces WHERE username = #{username} ORDER BY created_at DESC LIMIT 20")
    List<TraceRow> recent(String username);

    record TraceRow(String traceId, String requestId, String username, String intent, String runtime,
                    String model, String status, String tools, String citations, String nodesJson,
                    int retries, String errorCode, long durationMs, LocalDateTime createdAt) {}
}
