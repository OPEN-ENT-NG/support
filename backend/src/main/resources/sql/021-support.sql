ALTER TABLE support.tickets
ALTER COLUMN created SET DEFAULT timezone('UTC', NOW());

ALTER TABLE support.tickets
ALTER COLUMN modified SET DEFAULT timezone('UTC', NOW());

ALTER TABLE support.comments
ALTER COLUMN created SET DEFAULT timezone('UTC', NOW());

ALTER TABLE support.comments
ALTER COLUMN modified SET DEFAULT timezone('UTC', NOW());

ALTER TABLE support.attachments
ALTER COLUMN created SET DEFAULT timezone('UTC', NOW());

ALTER TABLE support.bug_tracker_issues
ALTER COLUMN created SET DEFAULT timezone('UTC', NOW());

ALTER TABLE support.bug_tracker_issues
ALTER COLUMN modified SET DEFAULT timezone('UTC', NOW());

ALTER TABLE support.bug_tracker_attachments
ALTER COLUMN created SET DEFAULT timezone('UTC', NOW());

ALTER TABLE support.tickets_histo
ALTER COLUMN event_date SET DEFAULT timezone('UTC', NOW());

UPDATE support.bug_tracker_attachments
SET created = created at time zone 'UTC';

UPDATE support.bug_tracker_issues
SET created = created at time zone 'UTC',
modified = modified at time zone 'UTC';

UPDATE support.comments
SET created = created at time zone 'UTC',
modified = modified at time zone 'UTC';

UPDATE support.tickets
SET created = created at time zone 'UTC',
modified = modified at time zone 'UTC',
escalation_date = escalation_date at time zone 'UTC',
issue_update_date = issue_update_date at time zone 'UTC';

UPDATE support.tickets_histo
SET event_date = event_date at time zone 'UTC';