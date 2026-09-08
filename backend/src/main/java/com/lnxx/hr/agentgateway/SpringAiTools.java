package com.lnxx.hr.agentgateway;

import com.lnxx.hr.domain.HrRepository;
import com.lnxx.hr.rag.KnowledgeService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class SpringAiTools {
    private final HrRepository repository;
    private final KnowledgeService knowledge;

    public SpringAiTools(HrRepository repository, KnowledgeService knowledge) {
        this.repository = repository;
        this.knowledge = knowledge;
    }

    @Tool(description = "查询当前登录员工的档案。不得接受或查询其他员工编号。")
    public Object employeeProfile() {
        var user = TrustedUserContext.require();
        return repository.employee(user.employeeId()).orElse(null);
    }

    @Tool(description = "查询当前登录员工最近一个月的考勤汇总。")
    public Object attendanceSummary() {
        return repository.attendance(TrustedUserContext.require().employeeId());
    }

    @Tool(description = "查询当前登录员工的年假余额。")
    public Object leaveBalance() {
        return repository.leave(TrustedUserContext.require().employeeId());
    }

    @Tool(description = "查询当前登录员工的审批进度。")
    public Object approvalStatus() {
        return repository.approvals(TrustedUserContext.require().employeeId());
    }

    @Tool(description = "检索当前登录用户有权查看的人力制度，并返回可信引用编号。")
    public Object knowledgeSearch(String query) {
        var user = TrustedUserContext.require();
        String department = repository.employee(user.employeeId()).map(value -> value.department()).orElse("");
        return knowledge.search(query, user, department);
    }
}
