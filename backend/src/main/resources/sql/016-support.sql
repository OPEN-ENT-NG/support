ALTER TABLE support.tickets
ALTER COLUMN locale TYPE character varying(100);

ALTER TABLE support.tickets
ADD COLUMN issue_update_date timestamp;
