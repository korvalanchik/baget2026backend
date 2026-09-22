ALTER TABLE invoices
    ADD COLUMN payer_id BIGINT NULL AFTER customer_id;

ALTER TABLE invoices
    ADD CONSTRAINT fk_invoice_payer
        FOREIGN KEY (payer_id) REFERENCES customer (CustNo);

UPDATE invoices
SET payer_id = customer_id
WHERE payer_id IS NULL;


ALTER TABLE ledger_entries
    ADD COLUMN payer_id BIGINT NULL AFTER customer_id;

ALTER TABLE ledger_entries
    ADD CONSTRAINT fk_ledger_payer
        FOREIGN KEY (payer_id) REFERENCES customer (CustNo);

UPDATE ledger_entries
SET payer_id = customer_id
WHERE payer_id IS NULL AND direction = 'IN';