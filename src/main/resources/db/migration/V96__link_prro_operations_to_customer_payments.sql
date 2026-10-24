CREATE TABLE prro_payment_operation_links (
    customer_transaction_id BIGINT NOT NULL PRIMARY KEY,
    operation_id BIGINT NOT NULL,
    branch_no BIGINT NOT NULL,
    cash_account_id BIGINT NOT NULL,
    created_by VARCHAR(150) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_prro_payment_link_operation UNIQUE (operation_id),
    CONSTRAINT fk_prro_payment_link_transaction
        FOREIGN KEY (customer_transaction_id) REFERENCES customer_transactions(id),
    CONSTRAINT fk_prro_payment_link_operation
        FOREIGN KEY (operation_id) REFERENCES prro_operations(id),
    CONSTRAINT fk_prro_payment_link_branch
        FOREIGN KEY (branch_no) REFERENCES branches(BranchNo)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
