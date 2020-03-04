CREATE TABLE source
(
    id        TEXT NOT NULL,
    name      TEXT NOT NULL,
    pattern   TEXT NOT NULL,
    datatypes TEXT[] NOT NULL,
    url       TEXT NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE woof
(
    id        TEXT        NOT NULL,
    source    TEXT        NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    value     DECIMAL     NOT NULL,
    PRIMARY KEY (id, timestamp),
    FOREIGN KEY (source) REFERENCES source (id) ON DELETE CASCADE
);

SELECT create_hypertable('woof', 'timestamp');
