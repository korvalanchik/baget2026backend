-- One configured PRRO device per branch for the first release.
-- Multiple NULL branch_no values remain allowed for unassigned draft devices.
ALTER TABLE prro_devices
    ADD CONSTRAINT uq_prro_devices_branch_no UNIQUE (branch_no);

CREATE TABLE prro_device_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id BIGINT NULL,
    actor VARCHAR(150) NOT NULL,
    action VARCHAR(32) NOT NULL,
    snapshot JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_prro_device_audit_device_created (device_id, created_at),
    INDEX idx_prro_device_audit_created (created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
