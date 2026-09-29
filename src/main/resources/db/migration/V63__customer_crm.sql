-- CRM only: no stock or financial writes. Profile/follow-up belongs to a branch.
CREATE TABLE crm_customer_profiles (
 customer_id BIGINT NOT NULL,
 branch_no BIGINT NOT NULL,
 is_partner BOOLEAN NOT NULL DEFAULT FALSE,
 revision BIGINT NOT NULL DEFAULT 0,
 next_contact_date DATE NULL,
 PRIMARY KEY(customer_id,branch_no),
 FOREIGN KEY(customer_id) REFERENCES customer(CustNo),
 FOREIGN KEY(branch_no) REFERENCES branches(BranchNo)
);
CREATE TABLE crm_contacts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 request_key CHAR(36) NOT NULL UNIQUE,
 request_json JSON NOT NULL,
 customer_id BIGINT NOT NULL,
 branch_no BIGINT NOT NULL,
 outcome VARCHAR(32) NOT NULL,
 note VARCHAR(2000) NOT NULL,
 order_no BIGINT NULL,
 invoice_id BIGINT NULL,
 next_contact_date DATE NULL,
 actor VARCHAR(255) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_crm_history(customer_id,branch_no,id),
 FOREIGN KEY(customer_id) REFERENCES customer(CustNo),
 FOREIGN KEY(branch_no) REFERENCES branches(BranchNo),
 FOREIGN KEY(order_no) REFERENCES orders(OrderNo),
 FOREIGN KEY(invoice_id) REFERENCES invoices(id)
);
