import pytest
from pydantic import ValidationError

from hr_agent.api.schemas import AgentRunRequest


def test_request_accepts_camel_case_and_rejects_unknown_fields():
    request = AgentRunRequest.model_validate(
        {
            "requestId": "request-001",
            "conversationId": "conversation-001",
            "message": "我的年假",
            "userContext": {
                "username": "employee",
                "employeeId": "E1001",
                "role": "EMPLOYEE",
                "department": "研发部",
            },
        }
    )
    assert request.user_context.employee_id == "E1001"
    assert request.model_dump(by_alias=True)["conversationId"] == "conversation-001"

    with pytest.raises(ValidationError):
        AgentRunRequest.model_validate(
            {
                "requestId": "request-001",
                "conversationId": "conversation-001",
                "message": "问题",
                "userContext": {
                    "username": "employee",
                    "employeeId": "E1001",
                    "role": "EMPLOYEE",
                    "department": "研发部",
                },
                "unexpected": True,
            }
        )
