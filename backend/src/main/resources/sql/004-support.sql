ALTER TABLE support.comments
ALTER COLUMN ticket_id DROP DEFAULT;
DROP SEQUENCE IF EXISTS support.comments_ticket_id_seq;