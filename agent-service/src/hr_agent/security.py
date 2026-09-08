import hmac

from fastapi import Header, HTTPException, status

from hr_agent.config import get_settings


async def require_service_token(
    x_agent_service_token: str = Header(default="", alias="X-Agent-Service-Token"),
) -> None:
    expected = get_settings().service_token
    if not hmac.compare_digest(x_agent_service_token, expected):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid service token")
