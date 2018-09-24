CREATE OR REPLACE FUNCTION  support.merge_attachment_bygridfs(id1 BIGINT, 
													issue_id1 BIGINT,
													gridfs_id1 VARCHAR,
													name1 VARCHAR,
													size1 INTEGER) RETURNS VOID AS
$$
BEGIN
    LOOP
        -- first try to update the key
        UPDATE support.bug_tracker_attachments 
        	SET issue_id = issue_id1,
        		gridfs_id = gridfs_id1,
        		name = name1,
        		size = size1
        	WHERE id = id1;
        IF found THEN
            RETURN;
        END IF;
        -- not there, so try to insert the key
        BEGIN
            INSERT INTO support.bug_tracker_attachments(id, issue_id, gridfs_id, name, size) 
            	VALUES(id1, issue_id1, gridfs_id1, name1, size1);
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
													document_id1 VARCHAR,
													name1 VARCHAR,
													size1 INTEGER) RETURNS VOID AS
$$
BEGIN
    LOOP
        -- first try to update the key
        UPDATE support.bug_tracker_attachments 
        	SET issue_id = issue_id1,
        		document_id = document_id1,
        		name = name1,
        		size = size1
        	WHERE id = id1;
        IF found THEN
            RETURN;
        END IF;
        -- not there, so try to insert the key
        BEGIN
            INSERT INTO support.bug_tracker_attachments(id, issue_id, document_id, name, size) 
            	VALUES(id1, issue_id1, document_id1, name1, size1);
            RETURN;
        EXCEPTION WHEN unique_violation THEN
            -- do nothing, and loop to try the UPDATE again
        END;
    END LOOP;
END;
$$
LANGUAGE plpgsql;