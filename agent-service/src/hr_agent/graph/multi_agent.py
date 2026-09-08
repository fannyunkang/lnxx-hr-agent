from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class ChildAgentSpec:
    name: str
    intent: str
    description: str
    tool_names: tuple[str, ...]
    keywords: tuple[str, ...]


PLUGIN_MANIFEST = Path(__file__).with_name("agent_plugins.json")

DEFAULT_CHILD_AGENT_PLUGINS = {
    "plugins": [
        {
            "name": "ProfileAgent",
            "intent": "EMPLOYEE_PROFILE",
            "description": "负责员工档案、岗位、部门和联系方式查询。",
            "tools": ["employee_profile", "hr_get_employee_profile"],
            "keywords": ["档案", "员工信息", "个人信息", "岗位", "部门", "profile"],
        },
        {
            "name": "AttendanceAgent",
            "intent": "ATTENDANCE",
            "description": "负责考勤、出勤、迟到和缺勤查询。",
            "tools": ["attendance_summary", "hr_get_attendance_summary"],
            "keywords": ["考勤", "迟到", "缺勤", "出勤", "attendance"],
        },
        {
            "name": "LeaveAgent",
            "intent": "LEAVE_BALANCE",
            "description": "负责年假、假期余额和已使用假期查询。",
            "tools": ["leave_balance", "hr_get_leave_balance"],
            "keywords": ["年假", "假期余额", "剩余假", "多少假", "用了多少", "还有多少", "leave"],
        },
        {
            "name": "ApprovalAgent",
            "intent": "APPROVAL_STATUS",
            "description": "负责审批、申请进度和待办状态查询。",
            "tools": ["approval_status", "hr_get_approval_status"],
            "keywords": ["审批", "申请进度", "待办", "approval"],
        },
        {
            "name": "PolicyRagAgent",
            "intent": "KNOWLEDGE_SEARCH",
            "description": "负责公司制度、流程规范和知识库 RAG 问答。",
            "tools": ["knowledge_search"],
            "keywords": ["制度", "规定", "流程", "规范", "怎么申请", "上班时间", "弹性", "policy", "knowledge"],
        },
        {
            "name": "HrSearchAgent",
            "intent": "HR_EMPLOYEE_SEARCH",
            "description": "负责 HR/ADMIN 员工搜索与名单查询。",
            "tools": ["hr_search_employee"],
            "keywords": ["搜索员工", "查找员工", "员工列表", "员工名单", "所有员工", "search employee", "staff list"],
        },
    ]
}


def load_child_agent_plugins(manifest: Path = PLUGIN_MANIFEST) -> dict[str, ChildAgentSpec]:
    payload = json.loads(manifest.read_text(encoding="utf-8")) if manifest.exists() else DEFAULT_CHILD_AGENT_PLUGINS
    agents: dict[str, ChildAgentSpec] = {}
    for item in payload.get("plugins", []):
        name = str(item["name"])
        agents[name] = ChildAgentSpec(
            name=name,
            intent=str(item["intent"]),
            description=str(item["description"]),
            tool_names=tuple(str(tool) for tool in item.get("tools", [])),
            keywords=tuple(str(keyword).lower() for keyword in item.get("keywords", [])),
        )
    return agents


def plan_child_agents(
    message: str,
    routed_intent: str,
    child_agents: dict[str, ChildAgentSpec] | None = None,
) -> list[ChildAgentSpec]:
    agents = child_agents or load_child_agent_plugins()
    intent_to_agent = {spec.intent: spec.name for spec in agents.values()}
    normalized = message.lower()
    selected: list[str] = []

    for agent in agents.values():
        if any(keyword in normalized for keyword in agent.keywords):
            selected.append(agent.name)

    if not selected and routed_intent in intent_to_agent:
        selected.append(intent_to_agent[routed_intent])

    return [agents[name] for name in dict.fromkeys(selected) if name in agents]
