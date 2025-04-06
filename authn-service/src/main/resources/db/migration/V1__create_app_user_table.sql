CREATE SEQUENCE IF NOT EXISTS app_user_sequence START WITH 1 INCREMENT BY 1;

CREATE TABLE app_user
(
    id              BIGINT                      NOT NULL,
    first_name      VARCHAR(255),
    last_name       VARCHAR(255),
    email           VARCHAR(255)                NOT NULL,
    password        VARCHAR(255)                NOT NULL,
    enabled         BOOLEAN                     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id)
);

ALTER TABLE app_user
    ADD CONSTRAINT uc_app_user_email UNIQUE (email);

CREATE INDEX idx_app_user_email ON app_user(email);