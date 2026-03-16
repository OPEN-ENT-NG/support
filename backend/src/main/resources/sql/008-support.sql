CREATE TABLE support.bug_tracker_attachments(
	id BIGINT PRIMARY KEY,
	issue_id BIGINT NOT NULL,
	gridfs_id VARCHAR(100) NOT NULL,
	name VARCHAR(255) NOT NULL,
	size INTEGER NOT NULL,
	created TIMESTAMP NOT NULL DEFAULT NOW(),
	CONSTRAINT issue_id_fk FOREIGN KEY(issue_id) REFERENCES support.bug_tracker_issues(id) ON UPDATE CASCADE
);