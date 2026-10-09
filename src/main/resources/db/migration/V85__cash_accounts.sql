-- Historical customer entries remain unassigned. Opening balances are entered explicitly.
CREATE TABLE cash_control (id INT PRIMARY KEY);
INSERT INTO cash_control VALUES(1);
CREATE TABLE cash_accounts (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 code VARCHAR(80) NOT NULL UNIQUE,
 name VARCHAR(150) NOT NULL,
 kind VARCHAR(20) NOT NULL,
 branch_no BIGINT NOT NULL,
 initialized BOOLEAN NOT NULL DEFAULT FALSE,
 opened_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CHECK (kind IN ('BRANCH_CASH','CENTRAL_CASH','BANK','TRANSIT'))
);
CREATE TABLE cash_transfers (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 source_id BIGINT NOT NULL,
 destination_id BIGINT NOT NULL,
 transit_id BIGINT NOT NULL,
 amount DECIMAL(19,4) NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'SENT',
 sent_by VARCHAR(255) NOT NULL,
 received_by VARCHAR(255) NULL,
 note VARCHAR(1000) NOT NULL,
 received_note VARCHAR(1000) NULL,
 sent_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 received_at DATETIME(6) NULL,
 FOREIGN KEY (source_id) REFERENCES cash_accounts(id),
 FOREIGN KEY (destination_id) REFERENCES cash_accounts(id),
 FOREIGN KEY (transit_id) REFERENCES cash_accounts(id),
 CHECK (amount>0), CHECK(state IN ('SENT','RECEIVED'))
);
CREATE TABLE cash_requests (
 request_key VARCHAR(36) PRIMARY KEY,
 actor VARCHAR(255) NOT NULL,
 operation VARCHAR(30) NOT NULL,
 payload JSON NOT NULL,
 result JSON NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
ALTER TABLE ledger_entries ADD COLUMN cash_account_id BIGINT NULL,
 ADD COLUMN cash_transfer_id BIGINT NULL,
 ADD COLUMN reward_entry_id BIGINT NULL,
 ADD CONSTRAINT fk_ledger_cash_account FOREIGN KEY(cash_account_id) REFERENCES cash_accounts(id),
 ADD CONSTRAINT fk_ledger_cash_transfer FOREIGN KEY(cash_transfer_id) REFERENCES cash_transfers(id),
 ADD UNIQUE KEY uq_ledger_reward_cash(reward_entry_id),
 ADD INDEX idx_ledger_cash_date(cash_account_id,created_at,id);

CREATE TABLE cash_transfer_notes (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 transfer_id BIGINT NOT NULL,
 observed_amount DECIMAL(19,4) NOT NULL,
 note VARCHAR(1000) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY (transfer_id) REFERENCES cash_transfers(id)
);
