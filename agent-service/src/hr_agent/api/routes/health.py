from typing import Annotated

from fastapi import APIRouter, Depends, status
from fastapi.responses import JSONResponse

from hr_agent.config import Settings, get_settings

router = APIRouter(tags=["system"])
SettingsDependency = Annotated[Settings, Depends(get_settings)]


@router.get("/health")
async def health() -> dict[str, str]:
    return {"status": "UP", "service": "hr-agent-python"}


@router.get("/health/ready")
async def readiness(settings: SettingsDependency) -> JSONResponse:
    if not settings.model_configured:
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={
                "status": "DOWN",
                "code": "MODEL_NOT_CONFIGURED",
                "message": "请配置模型服务地址、API Key 和模型名",
            },
        )
    return JSONResponse(
        content={"status": "UP", "service": "hr-agent-python", "model": settings.model_name}
    )