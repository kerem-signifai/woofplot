CREATE TABLE users
(
    username  TEXT NOT NULL,
    pass_hash TEXT NOT NULL,
    is_admin  BOOL NOT NULL,
    PRIMARY KEY (username)
);
