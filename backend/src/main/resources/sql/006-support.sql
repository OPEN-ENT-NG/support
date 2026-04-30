ALTER TABLE support.tickets 
ADD COLUMN escalation_status SMALLINT NOT NULL DEFAULT 1,
ADD COLUMN escalation_date TIMESTAMP;

CREATE TABLE support.bug_tracker_issues(
	id BIGINT PRIMARY KEY,
	ticket_id BIGINT NOT NULL,
	content JSON,
	created TIMESTAMP NOT NULL DEFAULT NOW(),
	modified TIMESTAMP NOT NULL DEFAULT NOW(),
	owner VARCHAR(36) NOT NULL,
	CONSTRAINT ticket_id_fk FOREIGN KEY(ticket_id) REFERENCES support.tickets(id) ON UPDATE CASCADE,
	CONSTRAINT owner_fk FOREIGN KEY(owner) REFERENCES support.users(id) ON UPDATE CASCADE	
);