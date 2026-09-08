package com.lnxx.hr.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface HrRepository {
    Optional<EmployeeProfile> employee(String employeeId);
    List<EmployeeProfile> searchEmployees(String query, String department);
    AttendanceSummary attendance(String employeeId);
    LeaveBalance leave(String employeeId);
    List<ApprovalSummary> approvals(String employeeId);
    List<KnowledgeChunk> knowledge();
    KnowledgeChunk addKnowledge(String documentId, String title, String content,
                                KnowledgeChunk.KnowledgeScope scope, Set<String> departments);
}
