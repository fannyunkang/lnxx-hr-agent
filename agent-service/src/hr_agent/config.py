from functools import lru_cache

from pydantic import BaseModel
from pydantic_settings import BaseSettings, SettingsConfigDict


class ModelEndpoint(BaseModel):
    base_url: str
    api_key: str
    model: str


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="HR_AGENT_", env_file=".env", extra="ignore"
    )

    backend_base_url: str = "http://localhost:8080"
    service_token: str = "change-this-in-production"
    request_timeout_seconds: float = 5.0
    tool_transport: str = "mcp"
    mcp_base_url: str = "http://localhost:8090"
    mcp_endpoint: str = "/mcp"
    model_base_url: str = ""
    model_api_key: str = ""
    model_name: str = ""
    model_options: str = ""
    deepseek_base_url: str = "https://api.deepseek.com/v1"
    deepseek_api_key: str = ""
    qwen_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    qwen_api_key: str = ""
    doubao_base_url: str = "https://ark.cn-beijing.volces.com/api/v3"
    doubao_api_key: str = ""
    model_timeout_seconds: float = 20.0
    model_max_turns: int = 5
    conversation_max_messages: int = 20
    conversation_ttl_seconds: int = 86400
    redis_url: str = "redis://localhost:6379/0"
    redis_enabled: bool = True
    max_tools_per_turn: int = 5
    max_total_tool_calls: int = 5
    run_timeout_seconds: float = 30.0
    trace_ttl_seconds: int = 604800

    @property
    def model_configured(self) -> bool:
        return bool(
            self.model_base_url.strip()
            and self.model_api_key.strip()
            and self.model_name.strip()
        )

    @property
    def available_models(self) -> list[str]:
        configured = [item.strip() for item in self.model_options.split(",") if item.strip()]
        models = configured or [
            self.model_name.strip(),
            "deepseek-chat",
            "qwen-plus",
            "qwen-max",
            "doubao-pro-32k",
            "doubao-lite-32k",
            "demo-rule-agent",
        ]
        if self.model_name.strip() and self.model_name.strip() not in models:
            models.insert(0, self.model_name.strip())
        return list(dict.fromkeys(model for model in models if model))

    def resolve_model(self, requested_model: str | None) -> str:
        if not requested_model:
            return self.model_name
        if requested_model not in self.available_models:
            raise ValueError(f"Model {requested_model} is not enabled")
        return requested_model

    def endpoint_for(self, requested_model: str | None) -> ModelEndpoint | None:
        model = self.resolve_model(requested_model)
        if model == "demo-rule-agent":
            return None
        if model.startswith("qwen"):
            return ModelEndpoint(base_url=self.qwen_base_url, api_key=self.qwen_api_key, model=model)
        if model.startswith("doubao"):
            return ModelEndpoint(base_url=self.doubao_base_url, api_key=self.doubao_api_key, model=model)
        base_url = self.model_base_url or self.deepseek_base_url
        api_key = self.model_api_key or self.deepseek_api_key
        return ModelEndpoint(base_url=base_url, api_key=api_key, model=model)


@lru_cache
def get_settings() -> Settings:
    return Settings()
