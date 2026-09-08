from __future__ import annotations

from hr_agent.graph.state import AgentState


def classify_intent(message: str) -> tuple[str, str]:
    normalized = message.lower()
    search_requested = any(keyword in normalized for keyword in ("搜索", "查找", "列表", "名单", "所有", "search", "list"))
    employee_requested = any(keyword in normalized for keyword in ("员工", "人员", "同事", "employee", "staff"))
    if search_requested and employee_requested:
        return "HR_EMPLOYEE_SEARCH", "hr_search_employee"

    routes = (
        (
            "EMPLOYEE_PROFILE",
            "employee_profile",
            ("档案", "员工信息", "个人信息", "岗位", "部门", "profile", "employee profile"),
        ),
        (
            "ATTENDANCE",
            "attendance_summary",
            ("考勤", "迟到", "缺勤", "出勤", "attendance"),
        ),
        (
            "LEAVE_BALANCE",
            "leave_balance",
            ("年假", "假期余额", "剩余假", "多少假", "用了多少", "还有多少", "leave balance", "annual leave"),
        ),
        (
            "APPROVAL_STATUS",
            "approval_status",
            ("审批", "申请进度", "待办", "这个员工", "approval", "request status"),
        ),
        (
            "KNOWLEDGE_SEARCH",
            "knowledge_search",
            ("制度", "规定", "流程", "规范", "怎么申请", "上班时间", "弹性", "policy", "knowledge"),
        ),
    )
    for intent, tool, keywords in routes:
        if any(keyword in normalized for keyword in keywords):
            return intent, tool
    return "GENERAL", ""


def route_after_classification(state: AgentState) -> str:
    if state["intent"] == "GENERAL":
        return "general"
    if state["intent"] == "KNOWLEDGE_SEARCH":
        return "knowledge"
    return "business"
