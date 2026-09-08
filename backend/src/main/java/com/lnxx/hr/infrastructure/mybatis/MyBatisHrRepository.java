package com.lnxx.hr.infrastructure.mybatis;

import com.lnxx.hr.domain.ApprovalSummary;
import com.lnxx.hr.domain.AttendanceSummary;
import com.lnxx.hr.domain.EmployeeProfile;
import com.lnxx.hr.domain.HrRepository;
import com.lnxx.hr.domain.KnowledgeChunk;
import com.lnxx.hr.domain.LeaveBalance;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class MyBatisHrRepository implements HrRepository {
    private final HrDataMapper mapper;

    public MyBatisHrRepository(HrDataMapper mapper) { this.mapper = mapper; }

    @Override public Optional<EmployeeProfile> employee(String employeeId) {
        return mapper.findEmployee(employeeId);
    }

    @Override public List<EmployeeProfile> searchEmployees(String query, String department) {
        return mapper.searchEmployees(query == null ? "" : query.trim(), department == null ? "" : department.trim());
    }

    @Override public AttendanceSummary attendance(String employeeId) {
        AttendanceSummary result = mapper.findLatestAttendance(employeeId);
        if (result == null) throw new IllegalArgumentException("暂无考勤数据");
        return result;
    }

    @Override public LeaveBalance leave(String employeeId) {
        LeaveBalance result = mapper.findLeaveBalance(employeeId);
        if (result == null) throw new IllegalArgumentException("暂无假期数据");
        return result;
    }

    @Override public List<ApprovalSummary> approvals(String employeeId) {
        return mapper.findApprovals(employeeId);
    }

    @Override public List<KnowledgeChunk> knowledge() {
        return mapper.findKnowledge().stream().map(row -> new KnowledgeChunk(row.documentId(), row.version(), row.chunkNumber(),
                row.title(), row.content(), KnowledgeChunk.KnowledgeScope.valueOf(row.scope()),
                Set.copyOf(mapper.findKnowledgeDepartments(row.documentId())),
                Set.copyOf(mapper.findKnowledgeEmployees(row.documentId())))).toList();
    }

    @Override
    @Transactional
    public KnowledgeChunk addKnowledge(String documentId, String title, String content,
                                       KnowledgeChunk.KnowledgeScope scope, Set<String> departments) {
        int chunkNumber = mapper.nextChunkNumber(documentId);
        mapper.insertKnowledge(documentId, chunkNumber, title, content, scope.name());
        departments.stream().filter(department -> mapper.countKnowledgeDepartment(documentId, department) == 0)
                .forEach(department -> mapper.insertKnowledgeDepartment(documentId, department));
        return new KnowledgeChunk(documentId, chunkNumber, title, content, scope, Set.copyOf(departments));
    }
}
