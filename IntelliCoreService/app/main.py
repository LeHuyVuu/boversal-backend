from datetime import datetime, timezone
from fastapi import FastAPI
from fastapi.openapi.utils import get_openapi
from app.api.routes.detect_routes import router as detect_router
from app.api.routes.forecast_routes import router as forecast_router
from app.api.routes.inference_routes import router as inference_router
from app.api.routes.rag_routes import router as rag_router
from app.api.routes.vision_routes import router as vision_router

app = FastAPI(title="Intelligen AI Service", version="1.0.0")


def custom_openapi():
    if app.openapi_schema:
        return app.openapi_schema

    app.openapi_schema = get_openapi(
        title=app.title,
        version=app.version,
        routes=app.routes,
    )
    app.openapi_schema["openapi"] = "3.0.3"
    return app.openapi_schema


app.openapi = custom_openapi

app.include_router(detect_router)
app.include_router(forecast_router)
app.include_router(inference_router)
app.include_router(rag_router)
app.include_router(vision_router)


@app.get("/health")
def health():
    return {
        "status": "healthy",
        "service": "Intelligen",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }


@app.get("/api/gateway-test")
def gateway_test():
    return {
        "status": "ok",
        "service": "Intelligen",
        "message": "Request reached",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
