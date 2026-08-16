CREATE TABLE users (
    id BINARY(16) NOT NULL,
    version BIGINT NOT NULL,
    email VARCHAR(254) NOT NULL,
    normalized_email VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL,
    last_login_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_normalized_email UNIQUE (normalized_email),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN'))
) ENGINE=InnoDB;
