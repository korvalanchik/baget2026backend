-- Expense metadata; the monetary amount is held only in ledger_entries.
CREATE TABLE cash_expenses (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 cash_account_id BIGINT NOT NULL,
 business_day DATE NOT NULL,
 category VARCHAR(24) NOT NULL,
 kind VARCHAR(16) NOT NULL,
 payee VARCHAR(255) NOT NULL,
 document_ref VARCHAR(120) NOT NULL,
 note VARCHAR(1000) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 reversal_of BIGINT NULL UNIQUE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(cash_account_id) REFERENCES cash_accounts(id),
 FOREIGN KEY(reversal_of) REFERENCES cash_expenses(id),
 INDEX idx_cash_expenses_account_day(cash_account_id,business_day,id),
 CHECK(category IN ('RENT','UTILITIES','REPAIR','HOUSEHOLD','OTHER')),
 CHECK(kind IN ('EXPENSE','REVERSAL')),
 CHECK((kind='EXPENSE' AND reversal_of IS NULL) OR (kind='REVERSAL' AND reversal_of IS NOT NULL))
);
ALTER TABLE ledger_entries ADD COLUMN cash_expense_id BIGINT NULL,
 ADD UNIQUE KEY uq_ledger_cash_expense(cash_expense_id),
 ADD CONSTRAINT fk_ledger_cash_expense FOREIGN KEY(cash_expense_id) REFERENCES cash_expenses(id);
