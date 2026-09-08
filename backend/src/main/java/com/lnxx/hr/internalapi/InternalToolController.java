package com.lnxx.hr.internalapi;

import com.lnxx.hr.domain.HrRepository;
import com.lnxx.hr.rag.KnowledgeService;
import com.lnxx.hr.security.AuthPrincipal;
import com.lnxx.hr.security.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/tools")
public class InternalToolController {
    private final AgentServiceTokenVerifier tokenVerifier;
    private final HrRepository repository;
    private final KnowledgeService knowledgeService;

    public InternalToolController(AgentServiceTokenVerifier tokenVerifier, HrRepository repository,
                                  KnowledgeService knowledgeService) {
        this.tokenVerifier = tokenVerifier;
        this.repository = repository;
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/{toolName}")
    ToolResponse execute(@PathVariable String toolName,
                         @RequestHeader(value = "X-Agent-Service-Token", required = false) String serviceToken,
                         @Valid @RequestBody ToolRequest request) {
        tokenVerifier.verify(serviceToken);
        UserContext user = request.userContext();
        Object data;
        try {
            data = switch (toolName) {
                case "employee_profile" -> repository.employee(user.employeeId()).orElse(null);
                case "attendance_summary" -> repository.attendance(user.employeeId());
                case "leave_balance" -> repository.leave(user.employeeId());
                case "approval_status" -> repository.approvals(user.employeeId());
                case "hr_search_employee" -> searchEmployees(request);
                case "hr_get_employee_profile" -> repository.employee(resolveTargetEmployeeId(request)).orElse(null);
                case "hr_get_attendance_summary" -> repository.attendance(resolveTargetEmployeeId(request));
                case "hr_get_leave_balance" -> repository.leave(resolveTargetEmployeeId(request));
                case "hr_get_approval_status" -> repository.approvals(resolveTargetEmployeeId(request));
                case "knowledge_search" -> searchKnowledge(request);
                default -> null;
            };
        } catch (IllegalArgumentException exception) {
            return new ToolResponse(toolName, false, null,
                    Map.of("code", "TOOL_ACCESS_DENIED", "message", exception.getMessage()));
        }
        if (data == null) {
            return new ToolResponse(toolName, false, null,
                    Map.of("code", "TOOL_NOT_FOUND", "message", "工具不存在或没有可用数据"));
        }
        return new ToolResponse(toolName, true, data, null);
    }

    private Object searchEmployees(ToolRequest request) {
        UserContext user = request.userContext();
        requireHr(user);
        String query = String.valueOf(request.arguments().getOrDefault("query", ""));
        String department = String.valueOf(request.arguments().getOrDefault("department", ""));
        return repository.searchEmployees(query, department);
    }

    private String resolveTargetEmployeeId(ToolRequest request) {
        UserContext user = request.userContext();
        String target = String.valueOf(request.arguments().getOrDefault("targetEmployeeId", "")).trim();
        if (target.isBlank()) {
            return user.employeeId();
        }
        if (!target.equals(user.employeeId())) {
            requireHr(user);
        }
        return target;
    }

    private void requireHr(UserContext user) {
        if (user.role() != UserRole.HR && user.role() != UserRole.ADMIN) {
            throw new IllegalArgumentException("当前账号无权查询其他员工信息");
        }
    }

    private List<Map<String, Object>> searchKnowledge(ToolRequest request) {
        UserContext user = request.userContext();
        String query = String.valueOf(request.arguments().getOrDefault("query", ""));
        AuthPrincipal principal = new AuthPrincipal(user.username(), user.employeeId(), user.role());
        return knowledgeService.search(query, principal, user.department()).stream()
                .map(chunk -> Map.<String, Object>of(
                        "documentId", chunk.documentId(),
                        "chunkNumber", chunk.chunkNumber(),
                        "title", chunk.title(),
                        "content", chunk.content(),
                        "citation", chunk.citation()))
                .toList();
    }

    public record ToolRequest(@NotBlank String requestId, @NotNull @Valid UserContext userContext,
                              @NotNull Map<String, Object> arguments) {}
    public record UserContext(@NotBlank String username, @NotBlank String employeeId,
                              @NotNull UserRole role, @NotNull String department) {}
    public record ToolResponse(String tool, boolean success, Object data, Map<String, Object> error) {}
}
