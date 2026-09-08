from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any

from hr_agent.graph.state import AgentState


@dataclass(frozen=True)
class IntentClassification:
    intent: str
    selected_tool: str
    access_scope: str = "SELF_OR_UNSPECIFIED"
    risk_level: str = "LOW"
    target_hints: dict[str, Any] = field(default_factory=dict)


EMPLOYEE_ID_PATTERN = re.compile(r"\bE\d{4,}\b", re.IGNORECASE)


def preclassify_intent(message: str) -> IntentClassification:
    normalized = message.lower()
    target_hints = _extract_target_hints(message)
    access_scope = _classify_access_scope(normalized, target_hints)
    risk_level = _risk_level_for(access_scope)

    if access_scope in {"EMPLOYEE_COLLECTION", "DEPARTMENT_COLLECTION", "ALL_EMPLOYEES"}:
        return IntentClassification(
            "HR_EMPLOYEE_SEARCH",
            "hr_search_employee",
            access_scope,
            risk_level,
            target_hints,
        )

    intent, tool = _classify_business_or_knowledge_intent(normalized)
    if intent != "GENERAL":
        return IntentClassification(intent, tool, access_scope, risk_level, target_hints)
    return IntentClassification("GENERAL", "", access_scope, risk_level, target_hints)


def classify_intent(message: str) -> tuple[str, str]:
    classification = preclassify_intent(message)
    return classification.intent, classification.selected_tool


def _extract_target_hints(message: str) -> dict[str, Any]:
    employee_ids = [match.group(0).upper() for match in EMPLOYEE_ID_PATTERN.finditer(message)]
    department_text = message
    for word in (
        "帮我",
        "请",
        "查询",
        "查找",
        "搜索",
        "列出",
        "所有",
        "全部",
        "员工列表",
        "员工名单",
        "人员名单",
        "员工",
        "人员",
        "同事",
        "名单",
        "列表",
    ):
        department_text = department_text.replace(word, "")
    departments = re.findall(r"[\u4e00-\u9fa5A-Za-z0-9]{1,12}部", department_text)
    return {
        "employeeIds": list(dict.fromkeys(employee_ids)),
        "departments": list(dict.fromkeys(departments)),
    }


def _classify_access_scope(normalized: str, target_hints: dict[str, Any]) -> str:
    employee_subject = any(
        keyword in normalized
        for keyword in ("员工", "人员", "同事", "employee", "staff")
    )
    collection_requested = any(
        keyword in normalized
        for keyword in ("搜索", "查找", "列表", "名单", "所有", "全部", "全员", "search", "list")
    )
    all_requested = any(keyword in normalized for keyword in ("所有", "全部", "全员", "all"))

    if target_hints["departments"] and employee_subject and collection_requested:
        return "DEPARTMENT_COLLECTION"
    if employee_subject and collection_requested:
        return "ALL_EMPLOYEES" if all_requested else "EMPLOYEE_COLLECTION"
    if target_hints["employeeIds"]:
        return "OTHER_EMPLOYEE"
    if any(keyword in normalized for keyword in ("这个员工", "那个员工", "这位同事", "他", "她")):
        return "OTHER_EMPLOYEE_UNRESOLVED"
    return "SELF_OR_UNSPECIFIED"


def _risk_level_for(access_scope: str) -> str:
    if access_scope in {"EMPLOYEE_COLLECTION", "DEPARTMENT_COLLECTION", "ALL_EMPLOYEES"}:
        return "HIGH"
    if access_scope in {"OTHER_EMPLOYEE", "OTHER_EMPLOYEE_UNRESOLVED"}:
        return "MEDIUM"
    return "LOW"


def _classify_business_or_knowledge_intent(normalized: str) -> tuple[str, str]:
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
