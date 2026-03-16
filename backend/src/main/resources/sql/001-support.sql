CREATE SCHEMA support;

CREATE TABLE support.users (
	id VARCHAR(36) NOT NULL PRIMARY KEY,
	username VARCHAR(255)
);

CREATE TABLE support.groups (
	id VARCHAR(36) NOT NULL PRIMARY KEY,
	name VARCHAR(255)
);

CREATE TABLE support.members (
	id VARCHAR(36) NOT NULL PRIMARY KEY,
	user_id VARCHAR(36),
	group_id VARCHAR(36),
	CONSTRAINT user_fk FOREIGN KEY(user_id) REFERENCES support.users(id) ON UPDATE CASCADE ON DELETE CASCADE,
	CONSTRAINT group_fk FOREIGN KEY(group_id) REFERENCES support.groups(id) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE support.scripts (
	filename VARCHAR(255) NOT NULL PRIMARY KEY,
	passed TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE FUNCTION support.merge_users(key VARCHAR, data VARCHAR) RETURNS VOID AS
$$
BEGIN
    LOOP
        UPDATE support.users SET username = data WHERE id = key;
        IF found THEN
            RETURN;
        END IF;
        BEGIN
            INSERT INTO support.users(id,username) VALUES (key, data);
            RETURN;
        EXCEPTION WHEN unique_violation THEN
        END;
    END LOOP;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION support.insert_users_members() RETURNS TRIGGER AS $$
    BEGIN
		IF (TG_OP = 'INSERT') THEN
            INSERT INTO support.members (id, user_id) VALUES (NEW.id, NEW.id);
            RETURN NEW;
        END IF;
        RETURN NULL;
    END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION support.insert_groups_members() RETURNS TRIGGER AS $$
    BEGIN
		IF (TG_OP = 'INSERT') THEN
            INSERT INTO support.members (id, group_id) VALUES (NEW.id, NEW.id);
            RETURN NEW;
        END IF;
        RETURN NULL;
    END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_trigger
AFTER INSERT ON support.users
    FOR EACH ROW EXECUTE PROCEDURE support.insert_users_members();

CREATE TRIGGER groups_trigger
AFTER INSERT ON support.groups
    FOR EACH ROW EXECUTE PROCEDURE support.insert_groups_members();