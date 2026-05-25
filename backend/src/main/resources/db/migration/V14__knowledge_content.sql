-- Phase-17 follow-up: persist the raw uploaded file so tenant admins can download
-- the original document back. Previously we only stored the Tika-extracted text in
-- vector_store; the source PDF/DOCX/TXT was thrown away after embedding.
--
-- All three columns are nullable so docs uploaded before this migration survive
-- (their download button will be disabled in the UI).
ALTER TABLE knowledge_documents
  ADD COLUMN content bytea,
  ADD COLUMN content_type text,
  ADD COLUMN content_size bigint;
