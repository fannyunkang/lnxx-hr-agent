package com.lnxx.hr.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class HrReadOnlyTools {
    private final JdbcTemplate jdbc;

    public HrReadOnlyTools(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Tool(description = "Read the authenticated employee profile. employeeId must come from trusted server context.")
    public Map<String, Object> employeeProfile(@ToolParam(description = "Trusted employee id") String employeeId) {
        return jdbc.queryForMap("SELECT employee_id, name, department, position, email_masked, phone_masked FROM employees WHERE employee_id = ?", employeeId);
    }

    @Tool(description = "Read the latest attendance summary for the authenticated employee.")
    public Map<String, Object> attendanceSummary(@ToolParam(description = "Trusted employee id") String employeeId) {
        return jdbc.queryForMap("SELECT * FROM attendance_summaries WHERE employee_id = ? ORDER BY attendance_month DESC LIMIT 1", employeeId);
    }

    @Tool(description = "Read annual leave balance for the authenticated employee.")
    public Map<String, Object> leaveBalance(@ToolParam(description = "Trusted employee id") String employeeId) {
        return jdbc.queryForMap("SELECT * FROM leave_balances WHERE employee_id = ?", employeeId);
    }

    @Tool(description = "Read recent approvals for the authenticated employee.")
    public List<Map<String, Object>> approvalStatus(@ToolParam(description = "Trusted employee id") String employeeId) {
        return jdbc.queryForList("SELECT * FROM approvals WHERE employee_id = ? ORDER BY updated_at DESC LIMIT 10", employeeId);
    }

    @Tool(description = "HR/ADMIN search employees by id, name, position or department.")
    public List<Map<String, Object>> hrSearchEmployee(
            @ToolParam(description = "Search query") String query,
            @ToolParam(description = "Optional department filter") String department,
            @ToolParam(description = "Trusted role") String role) {
        requireHr(role);
        String term = "%" + (query == null ? "" : query.trim()) + "%";
        String dept = department == null ? "" : department.trim();
        return jdbc.queryForList("""
                SELECT employee_id, name, department, position, email_masked, phone_masked
                FROM employees
                WHERE (? = '' OR employee_id LIKE ? OR name LIKE ? OR position LIKE ?)
                  AND (? = '' OR department = ?)
                ORDER BY employee_id
                LIMIT 20
                """, query == null ? "" : query.trim(), term, term, term, dept, dept);
    }

    @Tool(description = "Read employee profile. EMPLOYEE can only read self; HR/ADMIN can read target employee.")
    public Map<String, Object> hrGetEmployeeProfile(
            @ToolParam(description = "Trusted employee id") String employeeId,
            @ToolParam(description = "Optional target employee id") String targetEmployeeId,
            @ToolParam(description = "Trusted role") String role) {
        String target = resolveTarget(employeeId, targetEmployeeId, role);
        return employeeProfile(target);
    }

    @Tool(description = "Read employee attendance. EMPLOYEE can only read self; HR/ADMIN can read target employee.")
    public Map<String, Object> hrGetAttendanceSummary(
            @ToolParam(description = "Trusted employee id") String employeeId,
            @ToolParam(description = "Optional target employee id") String targetEmployeeId,
            @ToolParam(description = "Trusted role") String role) {
        String target = resolveTarget(employeeId, targetEmployeeId, role);
        return attendanceSummary(target);
    }

    @Tool(description = "Read employee leave balance. EMPLOYEE can only read self; HR/ADMIN can read target employee.")
    public Map<String, Object> hrGetLeaveBalance(
            @ToolParam(description = "Trusted employee id") String employeeId,
            @ToolParam(description = "Optional target employee id") String targetEmployeeId,
            @ToolParam(description = "Trusted role") String role) {
        String target = resolveTarget(employeeId, targetEmployeeId, role);
        return leaveBalance(target);
    }

    @Tool(description = "Read employee approvals. EMPLOYEE can only read self; HR/ADMIN can read target employee.")
    public List<Map<String, Object>> hrGetApprovalStatus(
            @ToolParam(description = "Trusted employee id") String employeeId,
            @ToolParam(description = "Optional target employee id") String targetEmployeeId,
            @ToolParam(description = "Trusted role") String role) {
        String target = resolveTarget(employeeId, targetEmployeeId, role);
        return approvalStatus(target);
    }

    @Tool(description = "Search permission-filtered HR knowledge. Identity fields must come from trusted server context.")
    public List<Map<String, Object>> knowledgeSearch(
            @ToolParam(description = "Search query") String query,
            @ToolParam(description = "Trusted employee id") String employeeId,
            @ToolParam(description = "Trusted department") String department,
            @ToolParam(description = "Trusted role") String role) {
        String term = "%" + query.trim() + "%";
        return jdbc.queryForList("""
                SELECT DISTINCT k.document_id, k.version, k.chunk_number, k.title, k.content, k.scope
                FROM knowledge_chunks k
                LEFT JOIN knowledge_departments d ON d.document_id = k.document_id
                LEFT JOIN knowledge_personal_access p ON p.document_id = k.document_id
                WHERE k.active = TRUE AND (k.title LIKE ? OR k.content LIKE ?)
                  AND (k.scope = 'PUBLIC'
                    OR (k.scope = 'DEPARTMENT' AND d.department = ?)
                    OR (k.scope = 'PERSONAL' AND p.employee_id = ?)
                    OR (k.scope = 'HR_ONLY' AND ? IN ('HR', 'ADMIN')))
                LIMIT 30
                """, term, term, department, employeeId, role);
    }

    private static String resolveTarget(String employeeId, String targetEmployeeId, String role) {
        String target = targetEmployeeId == null || targetEmployeeId.isBlank() ? employeeId : targetEmployeeId.trim();
        if (!target.equals(employeeId)) {
            requireHr(role);
        }
        return target;
    }

    private static void requireHr(String role) {
        if (!"HR".equals(role) && !"ADMIN".equals(role)) {
            throw new IllegalArgumentException("Current user cannot query other employees");
        }
    }
}
