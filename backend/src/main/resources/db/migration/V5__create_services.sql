CREATE TABLE services (
    id BINARY(16) PRIMARY KEY,
    version BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    description VARCHAR(1000) NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_services_normalized_name UNIQUE (normalized_name),
    INDEX idx_services_active_name (active, normalized_name),
    INDEX idx_services_created_at (created_at)
);

ALTER TABLE leads
    ADD COLUMN service_id BINARY(16) NULL AFTER requested_service,
    ADD CONSTRAINT fk_leads_service FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE SET NULL,
    ADD INDEX idx_leads_service_id (service_id);
