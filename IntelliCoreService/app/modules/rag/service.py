from google import genai
from google.genai import types
import psycopg2
from dotenv import load_dotenv
import os
from collections.abc import Iterator

# Tải cấu hình biến môi trường
load_dotenv()
client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))

def retrieve_context(question, top_k=3):
    # Bước 1: Tạo embedding với mô hình gemini-embedding-001 hiện hành
    result = client.models.embed_content(
        model="gemini-embedding-001",
        contents=question,
        config=types.EmbedContentConfig(
            task_type="RETRIEVAL_QUERY",
            output_dimensionality=768,
        ),
    )
    query_embedding = result.embeddings[0].values

    # CHUYỂN ĐỔI: Ép kiểu mảng số thực về định dạng chuỗi '[0.1, 0.2, ...]' để pgvector hiểu
    query_embedding_str = str(query_embedding)

    # Bước 2: Tìm các đoạn văn bản (chunks) gần nghĩa nhất trong database Aiven
    conn = psycopg2.connect(os.getenv("AIVEN_DB_URL"), sslmode="require")
    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT content, source, 1 - (embedding <=> %s::vector) AS similarity
                FROM documents
                ORDER BY embedding <=> %s::vector
                LIMIT %s
                """,
                (query_embedding_str, query_embedding_str, top_k)
            )
            return cur.fetchall()
    finally:
        conn.close()

def generate_answer(question, context_rows):
    # Ghép các đoạn tài liệu tìm được thành 1 khối ngữ cảnh duy nhất
    # index 0: content, index 1: source
    context_text = "\n\n".join([f"[Nguồn: {r[1]}]\n{r[0]}" for r in context_rows])

    prompt = f"""Bạn là trợ lý AI trả lời câu hỏi dựa trên tài liệu được cung cấp bên dưới.
Chỉ trả lời dựa trên thông tin trong tài liệu. Nếu không tìm thấy thông tin liên quan, hãy nói rõ là không có thông tin.

TÀI LIỆU THAM KHẢO:
{context_text}

CÂU HỎI: {question}

TRẢ LỜI:"""

    response = client.models.generate_content(
        model="gemini-3.6-flash",
        contents=prompt,
    )
    return response.text or "Không tìm thấy câu trả lời phù hợp trong tài liệu."


def generate_answer_stream(question, context_rows) -> Iterator[str]:
    context_text = "\n\n".join([f"[Nguồn: {r[1]}]\n{r[0]}" for r in context_rows])
    prompt = f"""Bạn là trợ lý AI trả lời câu hỏi dựa trên tài liệu được cung cấp bên dưới.
Chỉ trả lời dựa trên thông tin trong tài liệu. Nếu không tìm thấy thông tin liên quan, hãy nói rõ là không có thông tin.

TÀI LIỆU THAM KHẢO:
{context_text}

CÂU HỎI: {question}

TRẢ LỜI:"""

    for chunk in client.models.generate_content_stream(
        model="gemini-3.6-flash",
        contents=prompt,
    ):
        if chunk.text:
            yield chunk.text

if __name__ == "__main__":
    print("Chatbot RAG cho hệ thống Boversal. Gõ 'exit' để thoát.\n")
    while True:
        question = input("Bạn hỏi: ")
        if question.lower() == "exit":
            break

        try:
            context_rows = retrieve_context(question)
            answer = generate_answer(question, context_rows)

            print(f"\nTrả lời: {answer}\n")
            print("--- Nguồn tham khảo dùng để trả lời ---")
            for r in context_rows:
                # r[2]: similarity, r[0]: content văn bản
                print(f"(độ liên quan: {r[2]:.2f}) {r[0][:80]}...")
            print()
        except Exception as e:
            print(f"\n[Lỗi] Có lỗi xảy ra trong quá trình xử lý: {e}\n")
