import json

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.auth import get_current_user
from app.modules.rag.service import generate_answer_stream, retrieve_context

router = APIRouter(prefix="/api/ai/rag", tags=["rag"])


class ChatRequest(BaseModel):
	question: str = Field(min_length=1, max_length=4000)


class ChatSource(BaseModel):
	content: str
	source: str | None = None
	similarity: float


@router.post("/chat")
def chat(payload: ChatRequest, user=Depends(get_current_user)):
	context_rows = retrieve_context(payload.question)

	def events():
		sources = [
			ChatSource(
				content=row[0],
				source=row[1],
				similarity=float(row[2]),
			).model_dump()
			for row in context_rows
		]
		yield f"data: {json.dumps({'type': 'sources', 'sources': sources}, ensure_ascii=False)}\n\n"
		for text in generate_answer_stream(payload.question, context_rows):
			yield f"data: {json.dumps({'type': 'token', 'text': text}, ensure_ascii=False)}\n\n"
		yield "data: {\"type\": \"done\"}\n\n"

	return StreamingResponse(events(), media_type="text/event-stream")