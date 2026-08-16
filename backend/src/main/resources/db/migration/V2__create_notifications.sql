CREATE TABLE notifications (
    id BINARY(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    type VARCHAR(40) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(500) NOT NULL,
    lead_id BINARY(16),
    read_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_notifications_unread_created (read_at, created_at),
    INDEX idx_notifications_created (created_at),
    INDEX idx_notifications_lead_created (lead_id, created_at),
    CONSTRAINT fk_notifications_lead FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE SET NULL,
    CONSTRAINT chk_notifications_type CHECK (type IN ('NEW_LEAD', 'LEAD_QUALIFIED', 'HIGH_PRIORITY_LEAD', 'AUTOMATION_FAILED')),
    CONSTRAINT chk_notifications_severity CHECK (severity IN ('INFO', 'SUCCESS', 'WARNING', 'ERROR'))
);
