from langchain_text_splitters import RecursiveCharacterTextSplitter

def load_and_chunk(filepath, chunk_size=300, chunk_overlap=50):
    with open(filepath, "r", encoding="utf-8") as f:
        text = f.read()

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=chunk_size,
        chunk_overlap=chunk_overlap,
        separators=["\n\n", "\n", ". ", " ", ""] 
    )
    raw_chunks = splitter.split_text(text)

    # Gắn metadata đơn giản: biết chunk này từ file nào
    chunks = [
        {"text": chunk, "source": filepath, "chunk_id": i}
        for i, chunk in enumerate(raw_chunks)
    ]
    return chunks

if __name__ == "__main__":
    chunks = load_and_chunk("data.txt")
    for c in chunks:
        print(f"--- Chunk {c['chunk_id']} (nguồn: {c['source']}) ---")
        print(c["text"])
        print()