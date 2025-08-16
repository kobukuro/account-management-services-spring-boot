CREATE TABLE user_authentication
(
    id              UUID PRIMARY KEY,
    user_id         UUID                     NOT NULL,
    type            VARCHAR(20)              NOT NULL,
    email           VARCHAR(255)             NOT NULL,
    password        VARCHAR(255),
    enabled         BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (user_id) REFERENCES app_user (id)
);

ALTER TABLE user_authentication
    ADD CONSTRAINT uk_user_auth_type_email
        UNIQUE (type, email);