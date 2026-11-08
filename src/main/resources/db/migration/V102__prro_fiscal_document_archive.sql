CREATE TABLE prro_fiscal_documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operation_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    branch_no BIGINT NOT NULL,
    branch_name VARCHAR(120) NOT NULL,
    fiscal_number VARCHAR(32) NOT NULL,
    device_name VARCHAR(120) NOT NULL,
    endpoint_mode VARCHAR(16) NOT NULL,
    fiscal_document_id VARCHAR(100) NOT NULL,
    document_type VARCHAR(24) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_reference VARCHAR(128) NULL,
    local_number INT NOT NULL,
    response_code INT NOT NULL,
    dps_date_time BIGINT NULL,
    data_sign LONGBLOB NULL,
    id_sign LONGBLOB NULL,
    data_sign_sha256 CHAR(64) NULL,
    id_sign_sha256 CHAR(64) NULL,
    data_sign_length BIGINT UNSIGNED NOT NULL DEFAULT 0,
    id_sign_length BIGINT UNSIGNED NOT NULL DEFAULT 0,
    archive_status VARCHAR(24) NOT NULL,
    archived_by VARCHAR(150) NOT NULL,
    archived_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_prro_fiscal_document_operation UNIQUE (operation_id),
    CONSTRAINT uq_prro_fiscal_document_device_id UNIQUE (device_id, fiscal_document_id),
    CONSTRAINT fk_prro_fiscal_document_operation
        FOREIGN KEY (operation_id) REFERENCES prro_operations(id),
    CONSTRAINT fk_prro_fiscal_document_device
        FOREIGN KEY (device_id) REFERENCES prro_devices(id),
    CONSTRAINT chk_prro_fiscal_document_endpoint
        CHECK (endpoint_mode IN ('TEST', 'PRODUCTION')),
    CONSTRAINT chk_prro_fiscal_document_type
        CHECK (document_type IN ('CHK', 'ZREPORT', 'SERVICECHK')),
    CONSTRAINT chk_prro_fiscal_document_archive_status
        CHECK (archive_status IN ('CAPTURED', 'MISSING_DATA'))
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE INDEX idx_prro_fiscal_documents_branch_time
    ON prro_fiscal_documents (branch_no, archived_at, id);
CREATE INDEX idx_prro_fiscal_documents_fiscal_id
    ON prro_fiscal_documents (fiscal_document_id);
CREATE INDEX idx_prro_fiscal_documents_source
    ON prro_fiscal_documents (source_type, source_reference);
