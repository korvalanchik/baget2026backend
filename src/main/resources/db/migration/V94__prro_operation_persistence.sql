CREATE TABLE prro_devices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    branch_no INT NULL,
    rro_fn VARCHAR(32) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    environment VARCHAR(16) NOT NULL DEFAULT 'TEST',
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    active_shift_key VARCHAR(64) NULL,
    next_local_number INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_prro_devices_rro_fn UNIQUE (rro_fn),
    CONSTRAINT chk_prro_devices_environment CHECK (environment IN ('TEST', 'PRODUCTION')),
    CONSTRAINT chk_prro_devices_local_number CHECK (next_local_number BETWEEN 1 AND 2147483646)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE prro_operations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id BIGINT NOT NULL,
    shift_key VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_reference VARCHAR(128) NULL,
    document_type VARCHAR(24) NOT NULL,
    local_number INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PREPARED',
    signed_document_hash CHAR(64) NULL,
    fiscal_document_id VARCHAR(100) NULL,
    response_code INT NULL,
    response_message VARCHAR(500) NULL,
    created_by VARCHAR(150) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_prro_operations_device FOREIGN KEY (device_id) REFERENCES prro_devices(id),
    CONSTRAINT uq_prro_operation_idempotency UNIQUE (device_id, idempotency_key),
    CONSTRAINT uq_prro_operation_local_number UNIQUE (device_id, shift_key, local_number),
    CONSTRAINT chk_prro_operation_source_type CHECK (source_type IN ('PAYMENT', 'SHIFT_OPEN', 'SHIFT_CLOSE', 'SERVICE')),
    CONSTRAINT chk_prro_operation_document_type CHECK (document_type IN ('CHK', 'ZREPORT', 'SERVICECHK')),
    CONSTRAINT chk_prro_operation_status CHECK (status IN ('PREPARED', 'SIGNED', 'SENDING', 'FISCALIZED', 'REJECTED', 'UNKNOWN_RESULT')),
    CONSTRAINT chk_prro_operation_local_number CHECK (local_number BETWEEN 0 AND 2147483646)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE INDEX idx_prro_operations_status_created ON prro_operations(status, created_at);
CREATE INDEX idx_prro_operations_source ON prro_operations(source_type, source_reference);
