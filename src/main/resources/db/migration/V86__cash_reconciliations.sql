-- Reconciliation records observations only; it never posts ledger entries.
CREATE TABLE cash_reconciliations (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 account_id BIGINT NOT NULL,
 business_day DATE NOT NULL,
 last_ledger_id BIGINT NOT NULL,
 expected_amount DECIMAL(19,4) NOT NULL,
 actual_amount DECIMAL(19,4) NOT NULL,
 difference DECIMAL(19,4) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 note VARCHAR(1000) NOT NULL,
 snapshot JSON NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY (account_id) REFERENCES cash_accounts(id),
 INDEX idx_cash_reconciliations_account(account_id,id),
 CHECK(actual_amount>=0)
);
