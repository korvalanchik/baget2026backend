ALTER TABLE ledger_entries
    ADD COLUMN customer_tx_id BIGINT,
    ADD CONSTRAINT fk_ledger_customer_tx
        FOREIGN KEY (customer_tx_id) REFERENCES customer_transactions(id)
        ON DELETE SET NULL,
    ADD INDEX idx_ledger_customer_tx (customer_tx_id);
