-- Request log only. Financial truth remains ledger_entries.
CREATE TABLE customer_payment_requests (
 request_key CHAR(36) PRIMARY KEY,
 customer_id BIGINT NOT NULL,
 branch_no BIGINT NOT NULL,
 actor VARCHAR(255) NOT NULL,
 payload JSON NOT NULL,
 result JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(customer_id) REFERENCES customer(CustNo),
 FOREIGN KEY(branch_no) REFERENCES branches(BranchNo)
);
