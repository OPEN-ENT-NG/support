ALTER TABLE support.attachments
    ADD COLUMN comment_id BIGINT DEFAULT NULL;

ALTER TABLE support.attachments
    ADD CONSTRAINT fk_attachments_comment_id
        FOREIGN KEY (comment_id) REFERENCES support.comments (id)
            ON UPDATE CASCADE ON DELETE SET NULL;