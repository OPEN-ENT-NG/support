ALTER TABLE support.bug_tracker_attachments
DROP CONSTRAINT gridfs_or_document_notnull,
ADD CONSTRAINT gridfs_or_document_notnull CHECK (NOT (gridfs_id IS NULL AND document_id IS NULL));