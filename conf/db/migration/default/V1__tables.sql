CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

CREATE OR REPLACE FUNCTION updated_modified_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE users
(
    username  TEXT NOT NULL,
    pass_hash TEXT NOT NULL,
    is_admin  BOOL NOT NULL,
    PRIMARY KEY (username)
);

CREATE TABLE woofs
(
    woof_id       BIGSERIAL PRIMARY KEY,
    url           TEXT                    NOT NULL,
    name          TEXT                    NOT NULL,
    fields        INT[]                   NOT NULL,
    col_names     TEXT[]                  NOT NULL,
    conversions   TEXT[]                  NOT NULL,
    latest_seq_no BIGINT    DEFAULT -1    NOT NULL,
    created_at    TIMESTAMP DEFAULT NOW() NOT NULL,
    updated_at    TIMESTAMP DEFAULT NOW() NOT NULL
);

CREATE TRIGGER update_timestamp
    BEFORE UPDATE ON woofs
    FOR EACH ROW
    EXECUTE PROCEDURE updated_modified_at();

CREATE TABLE metrics
(
    woof_id   BIGINT    NOT NULL REFERENCES woofs ON DELETE CASCADE,
    field     INT       NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    value     DECIMAL   NOT NULL,
    PRIMARY KEY (woof_id, field, timestamp)
);

SELECT create_hypertable('metrics', 'timestamp');
