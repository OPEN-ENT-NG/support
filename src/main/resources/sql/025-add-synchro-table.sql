CREATE TABLE support.synchro (
    bugtracker VARCHAR(32) PRIMARY KEY,
    last_synchro_epoch BIGINT
);

GRANT SELECT, INSERT, UPDATE ON TABLE support.synchro TO apps;