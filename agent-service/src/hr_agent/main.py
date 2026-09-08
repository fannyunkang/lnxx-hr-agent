from fastapi import FastAPI
from fastapi.responses import Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from hr_agent.api.routes import agent, health

app = FastAPI(
    title="LNXX HR Agent Service",
    version="0.1.0",
    docs_url="/docs",
    openapi_url="/openapi.json",
)
app.include_router(health.router)
app.include_router(agent.router)


@app.get("/metrics", include_in_schema=False)
async def metrics() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
