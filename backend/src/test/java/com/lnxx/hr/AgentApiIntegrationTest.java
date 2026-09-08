package com.lnxx.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnxx.hr.agentgateway.AgentGateway;
import com.lnxx.hr.agentgateway.AgentResponse;
import com.lnxx.hr.agentgateway.AgentStreamEvent;
import com.lnxx.hr.security.AuthPrincipal;
import com.lnxx.hr.trace.AgentTrace;
import com.lnxx.hr.trace.TraceStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AgentApiIntegrationTest.AgentGatewayTestConfiguration.class)
class AgentApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test void loginAndQueryLeaveBalance() throws Exception {
        String loginBody = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"username\":\"employee\",\"password\":\"employee123\"}"
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode login = objectMapper.readTree(loginBody);

        mvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + login.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"message\":\"我还有多少年假？\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("LEAVE_BALANCE"))
                .andExpect(jsonPath("$.conversationId").isNotEmpty())
                .andExpect(jsonPath("$.model").value("deterministic-fallback"))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("剩余 7.0 天")));
    }

    @Test void protectedEndpointRejectsAnonymousRequest() throws Exception {
        mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"message\":\"查询我的考勤\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void approvalToolWritesQueryableTrace() throws Exception {
        String token = tokenFor("employee", "employee123");
        mvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"message\":\"我的审批进度\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("APPROVAL_STATUS"))
                .andExpect(jsonPath("$.tools[0]").value("approval_status"));

        mvc.perform(get("/api/traces").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].intent", org.hamcrest.Matchers.hasItem("APPROVAL_STATUS")));
    }

    @Test void authenticatedSseRequestCompletesAcrossAsyncDispatch() throws Exception {
        String token = tokenFor("employee", "employee123");
        MvcResult pending = mvc.perform(post("/api/agent/chat/stream")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"message\":\"我的审批进度\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:answer")));
    }

    @Test void onlyHrCanCreateKnowledge() throws Exception {
        String request = "{\"documentId\":\"TEST-POLICY-001\",\"title\":\"测试制度\",\"content\":\"测试知识内容\",\"scope\":\"PUBLIC\",\"departments\":[]}";
        mvc.perform(post("/api/knowledge").header("Authorization", "Bearer " + tokenFor("employee", "employee123"))
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(request.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/knowledge").header("Authorization", "Bearer " + tokenFor("hr", "hr123456"))
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(request.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("TEST-POLICY-001"));
    }

    @Test void clientCanReuseAndClearConversation() throws Exception {
        String token = tokenFor("employee", "employee123");
        String conversationId = "conversation-001";
        mvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"message\":\"我的档案\",\"conversationId\":\"" + conversationId + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversationId));

        mvc.perform(delete("/api/agent/conversations/{id}", conversationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test void internalToolsRequireServiceTokenAndReturnStructuredData() throws Exception {
        String body = """
                {"requestId":"request-001","userContext":{"username":"employee","employeeId":"E1001",
                "role":"EMPLOYEE","department":"研发部"},"arguments":{}}
                """;
        mvc.perform(post("/internal/v1/tools/leave_balance")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/internal/v1/tools/leave_balance")
                        .header("X-Agent-Service-Token", "change-this-in-production")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.annualRemaining").value(7.0));
    }

    private String tokenFor(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AgentGatewayTestConfiguration {
        @Bean
        @Primary
        AgentGateway testAgentGateway(TraceStore traceStore, ObjectMapper objectMapper) {
            return new AgentGateway() {
                @Override
                public AgentResponse chat(String requestId, String message, String conversationId, String model, AuthPrincipal principal) {
                    String resolvedConversationId = conversationId == null || conversationId.isBlank()
                            ? UUID.randomUUID().toString() : conversationId;
                    String intent;
                    String answer;
                    List<String> tools;
                    if (message.contains("年假")) {
                        intent = "LEAVE_BALANCE";
                        answer = "你的年假总额为 10.0 天，已使用 3.0 天，剩余 7.0 天。";
                        tools = List.of("leave_balance");
                    } else if (message.contains("审批")) {
                        intent = "APPROVAL_STATUS";
                        answer = "你最近的审批：9月年假申请（AP-20260901）：审批中";
                        tools = List.of("approval_status");
                    } else if (message.contains("档案")) {
                        intent = "EMPLOYEE_PROFILE";
                        answer = "你的员工编号是 E1001，姓名张伟，所在部门为研发部。";
                        tools = List.of("employee_profile");
                    } else {
                        intent = "GENERAL";
                        answer = "我可以帮你查询人力资源信息。";
                        tools = List.of();
                    }
                    AgentResponse response = new AgentResponse(
                            requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId,
                            answer, intent, tools, List.of(),
                            UUID.randomUUID().toString(), resolvedConversationId,
                            "deterministic-fallback");
                    traceStore.save(new AgentTrace(response.traceId(), principal.username(), intent,
                            tools, response.citations(), 0, Instant.now()));
                    return response;
                }

                @Override
                public void stream(String requestId, String message, String conversationId, String model, AuthPrincipal principal,
                                   com.lnxx.hr.agentgateway.AgentStreamConsumer consumer) {
                    AgentResponse response = chat(requestId, message, conversationId, model, principal);
                    try {
                        consumer.accept(new AgentStreamEvent("answer", objectMapper.valueToTree(response)));
                        consumer.accept(new AgentStreamEvent("done",
                                objectMapper.valueToTree(java.util.Map.of("status", "COMPLETED"))));
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException("Unable to emit test event", exception);
                    }
                }

                @Override
                public void clearConversation(String conversationId, AuthPrincipal principal) {
                    // The Java API contract is the subject of this test; Python owns checkpoint deletion.
                }
            };
        }
    }
}
