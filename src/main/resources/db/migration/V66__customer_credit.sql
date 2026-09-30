CREATE TABLE customer_credit_policy (
 customer_id BIGINT NOT NULL, branch_no BIGINT NOT NULL,
 allow_unpaid BOOLEAN NOT NULL DEFAULT FALSE, revision BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(customer_id,branch_no),
 FOREIGN KEY(customer_id) REFERENCES customer(CustNo), FOREIGN KEY(branch_no) REFERENCES branches(BranchNo)
);
CREATE TABLE invoice_payment_terms (
 invoice_id BIGINT PRIMARY KEY, due_date DATE NULL, revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(invoice_id) REFERENCES invoices(id)
);
CREATE TABLE customer_credit_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, request_key CHAR(36) NOT NULL UNIQUE,
 customer_id BIGINT NOT NULL, branch_no BIGINT NOT NULL, invoice_id BIGINT NULL,
 action VARCHAR(32) NOT NULL, actor VARCHAR(255) NOT NULL, payload JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX ix_credit_history(customer_id,branch_no,id),
 FOREIGN KEY(customer_id) REFERENCES customer(CustNo), FOREIGN KEY(branch_no) REFERENCES branches(BranchNo),
 FOREIGN KEY(invoice_id) REFERENCES invoices(id)
);
CREATE TABLE product_credit_issues (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, invoice_id BIGINT NOT NULL,
 customer_id BIGINT NOT NULL, branch_no BIGINT NOT NULL, tracking_revision BIGINT NOT NULL,
 action VARCHAR(16) NOT NULL, debt DECIMAL(24,2) NOT NULL, due_date DATE NOT NULL,
 policy_revision BIGINT NOT NULL, terms_revision BIGINT NOT NULL, actor VARCHAR(255) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_credit_issue(product_id,tracking_revision),
 INDEX ix_credit_issue_customer(customer_id,branch_no,id),
 FOREIGN KEY(product_id) REFERENCES order_products(id), FOREIGN KEY(invoice_id) REFERENCES invoices(id)
);
