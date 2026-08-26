from fastapi import APIRouter, Depends

from app.auth import get_current_user
from app.schemas import AiRequest, AiResponse
from app.services.ai_engine import run_inference

router = APIRouter()


@router.post("/api/ai/infer", response_model=AiResponse)
def infer(payload: AiRequest, user=Depends(get_current_user)):
    result = run_inference(payload.prompt, payload.context)
    return AiResponse(result=result)