package com.lnxx.hr.infrastructure.mybatis;

import com.lnxx.hr.domain.ApprovalSummary;
import com.lnxx.hr.domain.AttendanceSummary;
import com.lnxx.hr.domain.EmployeeProfile;
import com.lnxx.hr.domain.LeaveBalance;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface HrDataMapper {
    @Select("SELECT employee_id, name, department, position, email_masked, phone_masked " +
            "FROM employees WHERE employee_id = #{employeeId}")
    Optional<EmployeeProfile> findEmployee(String employeeId);

    @Select("""
            SELECT employee_id, name, department, position, email_masked, phone_masked
            FROM employees
            WHERE (#{query} = '' OR employee_id LIKE CONCAT('%', #{query}, '%')
                   OR name LIKE CONCAT('%', #{query}, '%') OR position LIKE CONCAT('%', #{query}, '%'))
              AND (#{department} = '' OR department = #{department})
            ORDER BY employee_id
            LIMIT 20
            """)
    List<EmployeeProfile> searchEmployees(@Param("query") String query, @Param("department") String department);

    @Select("SELECT employee_id, attendance_month, work_days, attended_days, late_count, absent_count " +
            "FROM attendance_summaries WHERE employee_id = #{employeeId} ORDER BY attendance_month DESC LIMIT 1")
    AttendanceSummary findLatestAttendance(String employeeId);

    @Select("SELECT employee_id, annual_total, annual_used, annual_remaining " +
            "FROM leave_balances WHERE employee_id = #{employeeId}")
    LeaveBalance findLeaveBalance(String employeeId);

    @Select("SELECT approval_id, employee_id, approval_type, title, status, updated_at " +
            "FROM approvals WHERE employee_id = #{employeeId} ORDER BY updated_at DESC LIMIT 10")
    List<ApprovalSummary> findApprovals(String employeeId);

    @Select("SELECT document_id, version, chunk_number, title, content, scope FROM knowledge_chunks WHERE active = TRUE ORDER BY id")
    List<KnowledgeRow> findKnowledge();

    @Select("SELECT department FROM knowledge_departments WHERE document_id = #{documentId}")
    List<String> findKnowledgeDepartments(String documentId);

    @Select("SELECT employee_id FROM knowledge_personal_access WHERE document_id = #{documentId}")
    List<String> findKnowledgeEmployees(String documentId);

    @Select("SELECT COALESCE(MAX(chunk_number), 0) + 1 FROM knowledge_chunks WHERE document_id = #{documentId}")
    int nextChunkNumber(String documentId);

    @Insert("INSERT INTO knowledge_chunks(document_id, chunk_number, title, content, scope) " +
            "VALUES(#{documentId}, #{chunkNumber}, #{title}, #{content}, #{scope})")
    void insertKnowledge(@Param("documentId") String documentId, @Param("chunkNumber") int chunkNumber,
                         @Param("title") String title, @Param("content") String content,
                         @Param("scope") String scope);

    @Insert("INSERT INTO knowledge_departments(document_id, department) VALUES(#{documentId}, #{department})")
    void insertKnowledgeDepartment(@Param("documentId") String documentId,
                                   @Param("department") String department);

    @Insert("INSERT INTO knowledge_personal_access(document_id, employee_id) VALUES(#{documentId}, #{employeeId})")
    void insertKnowledgeEmployee(@Param("documentId") String documentId,
                                 @Param("employeeId") String employeeId);

    @Select("SELECT COUNT(*) FROM knowledge_departments WHERE document_id = #{documentId} AND department = #{department}")
    int countKnowledgeDepartment(@Param("documentId") String documentId,
                                 @Param("department") String department);

    record KnowledgeRow(String documentId, int version, int chunkNumber, String title, String content, String scope) {}
}
