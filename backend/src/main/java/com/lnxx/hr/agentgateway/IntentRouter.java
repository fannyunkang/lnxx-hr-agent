package com.lnxx.hr.agentgateway;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class IntentRouter {
    private final Map<AgentIntent, List<String>> keywords = new LinkedHashMap<>();

    public IntentRouter() {
        keywords.put(AgentIntent.EMPLOYEE_PROFILE, List.of("档案", "员工信息", "职位", "部门", "profile"));
        keywords.put(AgentIntent.ATTENDANCE, List.of("考勤", "迟到", "出勤", "打卡"));
        keywords.put(AgentIntent.LEAVE_BALANCE, List.of("年假", "假期", "余额", "休假"));
        keywords.put(AgentIntent.APPROVAL_STATUS, List.of("审批", "进度", "申请状态"));
        keywords.put(AgentIntent.KNOWLEDGE_SEARCH, List.of("制度", "规定", "政策", "流程", "怎么申请", "上班"));
    }

    public Route route(String message) {
        AgentIntent best = AgentIntent.GENERAL;
        int bestScore = 0;
        boolean tie = false;
        for (var entry : keywords.entrySet()) {
            int score = (int) entry.getValue().stream().filter(message::contains).count();
            if (score > bestScore) {
                best = entry.getKey(); bestScore = score; tie = false;
            } else if (score > 0 && score == bestScore) {
                tie = true;
            }
        }
        if (tie) return new Route(AgentIntent.CLARIFY, 0.45);
        return new Route(best, bestScore == 0 ? 0.7 : Math.min(0.99, 0.7 + bestScore * 0.1));
    }

    public record Route(AgentIntent intent, double confidence) {}
}
