CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

CREATE TABLE woofs
(
    url     TEXT NOT NULL,
    name    TEXT NOT NULL,
    pattern TEXT NOT NULL,
    labels  TEXT[] NOT NULL,
    conversions  TEXT[] NOT NULL,
    latest_seq_no BIGINT  NOT NULL,
    PRIMARY KEY (url)
);

CREATE TABLE metrics
(
    source TEXT    NOT NULL,
    woof   TEXT    NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    value  DECIMAL NOT NULL,
    PRIMARY KEY (source, timestamp),
    FOREIGN KEY (woof) REFERENCES woofs (url) ON DELETE CASCADE
);

SELECT create_hypertable('metrics', 'timestamp');
