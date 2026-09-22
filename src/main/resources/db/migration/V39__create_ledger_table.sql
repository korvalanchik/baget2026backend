CREATE TABLE ledger_entries (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                branch_id BIGINT NOT NULL,

                                direction VARCHAR(10) NOT NULL,
                                category VARCHAR(40) NOT NULL,

                                amount DECIMAL(19,4) NOT NULL,

                                created_at DATETIME(6) NOT NULL,

                                created_by BIGINT,

                                customer_id BIGINT NULL,
                                order_id BIGINT NULL,
                                invoice_id BIGINT NULL,
                                supplier_id BIGINT NULL,

                                reference VARCHAR(100),
                                note TEXT,

                                INDEX idx_ledger_branch_date (branch_id, created_at),
                                INDEX idx_ledger_customer (customer_id),
                                INDEX idx_ledger_order (order_id),
                                INDEX idx_ledger_invoice (invoice_id),
                                INDEX idx_ledger_category (category),
                                INDEX idx_ledger_created_at (created_at)
);
