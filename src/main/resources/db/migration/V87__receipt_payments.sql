-- Cash payment observations are separate from stock receipt cost snapshots.
CREATE TABLE receipt_payments (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 receipt_id BIGINT NOT NULL,
 cash_account_id BIGINT NOT NULL,
 component VARCHAR(24) NOT NULL,
 kind VARCHAR(16) NOT NULL,
 amount DECIMAL(19,2) NOT NULL,
 payee VARCHAR(255) NOT NULL,
 document_ref VARCHAR(120) NOT NULL,
 note VARCHAR(1000) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 reversal_of BIGINT NULL UNIQUE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(receipt_id) REFERENCES priced_material_receipts(id),
 FOREIGN KEY(cash_account_id) REFERENCES cash_accounts(id),
 FOREIGN KEY(reversal_of) REFERENCES receipt_payments(id),
 INDEX idx_receipt_payments_receipt(receipt_id,id),
 CHECK(amount>0),
 CHECK(component IN ('MATERIALS','DELIVERY','PROCUREMENT','DIRECT')),
 CHECK(kind IN ('PAYMENT','REVERSAL')),
 CHECK((kind='PAYMENT' AND reversal_of IS NULL) OR (kind='REVERSAL' AND reversal_of IS NOT NULL))
);
ALTER TABLE ledger_entries ADD COLUMN receipt_payment_id BIGINT NULL,
 ADD UNIQUE KEY uq_ledger_receipt_payment(receipt_payment_id),
 ADD CONSTRAINT fk_ledger_receipt_payment FOREIGN KEY(receipt_payment_id) REFERENCES receipt_payments(id);
