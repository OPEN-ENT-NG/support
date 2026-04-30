ALTER TABLE support.bug_tracker_attachments
ALTER COLUMN gridfs_id DROP NOT NULL,
ADD COLUMN document_id VARCHAR(100),
ADD CONSTRAINT gridfs_or_document_notnull CHECK ((gridfs_id IS NOT NULL AND document_id IS NULL) OR (gridfs_id IS NULL AND document_id IS NOT NULL));