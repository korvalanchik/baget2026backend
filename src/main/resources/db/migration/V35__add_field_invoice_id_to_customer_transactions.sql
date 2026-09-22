ALTER TABLE customer_transactions
    ADD COLUMN invoice_id BIGINT NULL;

ALTER TABLE customer_transactions
    ADD CONSTRAINT fk_customer_transactions_invoice
        FOREIGN KEY (invoice_id)
            REFERENCES invoices(id)
            ON DELETE SET NULL;

CREATE INDEX idx_customer_transactions_invoice
    ON customer_transactions (invoice_id);

CREATE INDEX idx_customer_transactions_customer
    ON customer_transactions (customer_id);
