from google import genai
from google.genai import types
import psycopg2
from dotenv import load_dotenv
import os
from chunker import load_and_chunk

load_dotenv()
client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))

# Bước A: chia nhỏ tài liệu (dùng lại hàm từ bước 3)
chunks = load_and_chunk("boversal_knowledge_base.txt")
print(f"Có {len(chunks)} chunks")

# Bước B: kết nối tới Aiven PostgreSQL
conn = psycopg2.connect(
    os.getenv("AIVEN_DB_URL"),
    sslmode="require"
)
cur = conn.cursor()

# Bước C: xoá dữ liệu cũ (nếu chạy lại nhiều lần, tránh bị trùng lặp)
cur.execute("DELETE FROM documents;")

# Bước D: tạo embedding cho từng chunk và lưu vào bảng
for c in chunks:
    result = client.models.embed_content(
        model="gemini-embedding-001",
        contents=c["text"],
        config=types.EmbedContentConfig(
            task_type="RETRIEVAL_DOCUMENT",
            output_dimensionality=768,
        ),
    )
    embedding = result.embeddings[0].values

    cur.execute(
        """
        INSERT INTO documents (content, source, chunk_id, embedding)
        VALUES (%s, %s, %s, %s)
        """,
        (c["text"], c["source"], c["chunk_id"], embedding)
    )
    print(f"Đã lưu chunk {c['chunk_id']}")

conn.commit()
cur.close()
conn.close()
print("Hoàn tất! Dữ liệu đã được lưu vào Aiven PostgreSQL.")