import psycopg2
from dotenv import load_dotenv
import os

load_dotenv()

try:
    db_url = os.getenv("AIVEN_DB_URL")
    if not db_url:
        print("❌ AIVEN_DB_URL không được set trong .env")
        exit(1)
    
    conn = psycopg2.connect(db_url)
    cur = conn.cursor()

    # Bật extension pgvector cho database này
    cur.execute("CREATE EXTENSION IF NOT EXISTS vector;")

    # Tạo bảng lưu chunk + embedding
    # Giữ kích thước đồng bộ với output_dimensionality trong embedder.py.
    cur.execute("""
        CREATE TABLE IF NOT EXISTS documents (
            id SERIAL PRIMARY KEY,
            content TEXT NOT NULL,
            source TEXT,
            chunk_id INTEGER,
            embedding VECTOR(768)
        );
    """)

    conn.commit()
    cur.close()
    conn.close()
    print("✅ Đã tạo extension và bảng documents thành công.")
    
except psycopg2.OperationalError as e:
    print(f"❌ Không thể kết nối tới database Aiven")
    print(f"   Lỗi: {str(e)}")
    print(f"\n💡 Lưu ý: Dev container có thể không có kết nối internet")
    print(f"   Nếu chạy local, tạo database PostgreSQL và cập nhật AIVEN_DB_URL trong .env")
    exit(1)
except Exception as e:
    print(f"❌ Lỗi: {str(e)}")
    exit(1)