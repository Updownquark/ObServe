
DROP SCHEMA IF EXISTS observe;

CREATE SCHEMA observe;

CREATE TABLE observe.Entity_Source(
	id BIGINT NOT NULL,
	source_key VARCHAR(64) NOT NULL,
	name VARCHAR(128) NOT NULL,

	PRIMARY KEY(id)
);

CREATE TABLE observe.Entity_Type(
	id BIGINT NOT NULL,
	name VARCHAR(256) NOT NULL,

	PRIMARY KEY(id)
);

CREATE TABLE observe.Entity_Field(
	entity_type BIGINT NOT NULL,
	id INTEGER NOT NULL,
	name VARCHAR(256) NOT NULL,

	PRIMARY KEY(entity_type, id),
	FOREIGN KEY(entity_type) REFERENCES observe.Entity_Type(id)
);

CREATE TABLE observe.Update(
	id BIGINT NOT NULL AUTO_INCREMENT,
	entity_type BIGINT NOT NULL,
	entity BIGINT NOT NULL,
	change_type SMALLINT NOT NULL,
	field INTEGER NULL,
	change_time TIMESTAMP NOT NULL,

	PRIMARY KEY(id)
);

CREATE INDEX observe.Update_By_Time ON observe.Update(change_time);
