ALTER TABLE support.tickets
ADD COLUMN event_count SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE support.tickets
ADD COLUMN locale character varying(36) DEFAULT 'fr';

CREATE TABLE support.tickets_histo (
	id bigserial PRIMARY KEY,
	ticket_id bigint,
	event   VARCHAR(255),
	event_date TIMESTAMP NOT NULL DEFAULT NOW(),
	status smallint,
	user_id character varying(36),
	CONSTRAINT ticket_fk FOREIGN KEY(ticket_id) REFERENCES support.tickets(id) ON UPDATE CASCADE ON DELETE CASCADE
);

alter table support.tickets_histo ADD
  event_type  SMALLINT;
