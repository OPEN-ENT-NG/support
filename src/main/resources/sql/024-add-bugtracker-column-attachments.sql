ALTER TABLE support.bug_tracker_attachments
ADD COLUMN bugtracker VARCHAR(32);

ALTER TABLE support.bug_tracker_attachments DROP CONSTRAINT issue_id_fk;
ALTER TABLE support.bug_tracker_issues DROP CONSTRAINT bug_tracker_issues_pkey;
ALTER TABLE support.bug_tracker_issues ADD CONSTRAINT bug_tracker_issues_pkey PRIMARY KEY (id, bugtracker);
ALTER TABLE support.bug_tracker_attachments ADD CONSTRAINT issue_id_fk FOREIGN KEY(issue_id, bugtracker) REFERENCES support.bug_tracker_issues(id, bugtracker) ON UPDATE CASCADE;

CREATE OR REPLACE FUNCTION  support.merge_attachment_bygridfs(id1 BIGINT,
                                                    issue_id1 BIGINT,
                                                    bugtracker1 VARCHAR,
                                                    gridfs_id1 VARCHAR,
                                                    name1 VARCHAR,
                                                    size1 INTEGER) RETURNS VOID AS
$$
BEGIN
    LOOP
        -- first try to update the key
        UPDATE support.bug_tracker_attachments
            SET issue_id = issue_id1,
                bugtracker = bugtracker1,
                gridfs_id = gridfs_id1,
                name = name1,
                size = size1
            WHERE id = id1 AND bugtracker = bugtracker1;
        IF found THEN
            RETURN;
        END IF;
        -- not there, so try to insert the key
        BEGIN
            INSERT INTO support.bug_tracker_attachments(id, issue_id, bugtracker, gridfs_id, name, size)
                VALUES(id1, issue_id1, bugtracker1, gridfs_id1, name1, size1);
            RETURN;
        EXCEPTION WHEN unique_violation THEN
            -- do nothing, and loop to try the UPDATE again
        END;
    END LOOP;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION  support.merge_attachment_bydoc(id1 BIGINT,
                                                    issue_id1 BIGINT,
                                                    bugtracker1 VARCHAR,
                                                    document_id1 VARCHAR,
                                                    name1 VARCHAR,
                                                    size1 INTEGER) RETURNS VOID AS
$$
BEGIN
    LOOP
        -- first try to update the key
        UPDATE support.bug_tracker_attachments
            SET issue_id = issue_id1,
                bugtracker = bugtracker1,
                document_id = document_id1,
                name = name1,
                size = size1
            WHERE id = id1 AND bugtracker = bugtracker1;
        IF found THEN
            RETURN;
        END IF;
        -- not there, so try to insert the key
        BEGIN
            INSERT INTO support.bug_tracker_attachments(id, issue_id, bugtracker, document_id, name, size)
                VALUES(id1, issue_id1, bugtracker1, document_id1, name1, size1);
            RETURN;
        EXCEPTION WHEN unique_violation THEN
            -- do nothing, and loop to try the UPDATE again
        END;
    END LOOP;
END;
$$
LANGUAGE plpgsql;
