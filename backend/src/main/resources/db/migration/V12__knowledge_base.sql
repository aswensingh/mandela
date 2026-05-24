-- Phase 17: knowledge base + RAG.

-- 1. Our tenant-scoped document index. The actual chunked text + embeddings live in
--    vector_store (below); knowledge_documents is the per-file metadata row that the UI
--    lists and the user owns.
CREATE TABLE knowledge_documents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  title text NOT NULL,
  file_name text NOT NULL,
  status text NOT NULL DEFAULT 'PROCESSED'
    CHECK (status IN ('PROCESSING','PROCESSED','FAILED')),
  error_message text,
  chunk_count integer NOT NULL DEFAULT 0,
  created_by_user_id uuid NOT NULL REFERENCES users(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_knowledge_documents_tenant ON knowledge_documents(tenant_id);

-- 2. The Spring AI PgVectorStore schema. Column names + types match what
--    org.springframework.ai.vectorstore.pgvector.PgVectorStore expects so we can opt out
--    of its initialize-schema behaviour (Flyway owns DDL). Dimension 1536 matches both
--    OpenAI text-embedding-3-small and our MockEmbeddingModel.
CREATE EXTENSION IF NOT EXISTS hstore;

CREATE TABLE vector_store (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  content text,
  metadata json,
  embedding vector(1536)
);

-- HNSW index with cosine ops matches PgVectorStore's default distance type (COSINE_DISTANCE).
CREATE INDEX vector_store_embedding_idx
  ON vector_store USING HNSW (embedding vector_cosine_ops);
