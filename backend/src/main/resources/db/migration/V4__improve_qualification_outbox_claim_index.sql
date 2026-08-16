ALTER TABLE qualification_dispatch_outbox
    DROP INDEX idx_qualification_outbox_available,
    ADD INDEX idx_qualification_outbox_available (status, available_at, created_at);
