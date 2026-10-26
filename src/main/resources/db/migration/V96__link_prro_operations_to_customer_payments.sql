CREATE TABLE prro_payment_operation_links (
    payment_request_key CHAR(36) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL PRIMARY KEY,
    operation_id BIGINT NOT NULL,
    branch_no BIGINT NOT NULL,
    cash_account_id BIGINT NOT NULL,
    created_by VARCHAR(150) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_prro_payment_link_operation UNIQUE (operation_id),
    CONSTRAINT fk_prro_payment_link_request
        FOREIGN KEY (payment_request_key) REFERENCES customer_payment_requests(request_key),
    CONSTRAINT fk_prro_payment_link_operation
        FOREIGN KEY (operation_id) REFERENCES prro_operations(id),
    CONSTRAINT fk_prro_payment_link_branch
        FOREIGN KEY (branch_no) REFERENCES branches(BranchNo)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
