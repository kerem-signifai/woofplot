CREATE TABLE woofs (
    id VARCHAR NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    value DECIMAL NOT NULL,
    PRIMARY KEY(id, timestamp)
);

SELECT create_hypertable('woofs', 'timestamp');
