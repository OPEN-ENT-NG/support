CREATE TABLE support.attachments(
	gridfs_id VARCHAR(100) NOT NULL,
	ticket_id BIGINT NOT NULL,
	name VARCHAR(255) NOT NULL,
	created TIMESTAMP NOT NULL DEFAULT NOW(),
	size INTEGER NOT NULL,
	owner VARCHAR(36) NOT NULL,
	CONSTRAINT attachment_pk PRIMARY KEY (gridfs_id, ticket_id),
	CONSTRAINT ticket_fk FOREIGN KEY(ticket_id) REFERENCES support.tickets(id) ON UPDATE CASCADE ON DELETE CASCADE,
	CONSTRAINT attachment_owner_fk FOREIGN KEY(owner) REFERENCES support.users(id) ON UPDATE CASCADE
);