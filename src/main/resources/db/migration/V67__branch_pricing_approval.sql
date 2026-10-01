CREATE TABLE branch_commercial_settings (
 branch_no BIGINT PRIMARY KEY, mode VARCHAR(16) NOT NULL, partner_id BIGINT NULL,
 revision BIGINT NOT NULL DEFAULT 1,
 FOREIGN KEY(branch_no) REFERENCES branches(BranchNo), FOREIGN KEY(partner_id) REFERENCES customer(CustNo),
 CHECK(mode IN ('OWN','RESELLER')), CHECK((mode='OWN' AND partner_id IS NULL) OR (mode='RESELLER' AND partner_id IS NOT NULL))
);
CREATE TABLE branch_commercial_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, branch_no BIGINT NOT NULL, revision BIGINT NOT NULL,
 mode VARCHAR(16) NOT NULL, partner_id BIGINT NULL, actor VARCHAR(255) NOT NULL, note VARCHAR(1000) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), UNIQUE KEY uq_branch_commercial(branch_no,revision)
);
ALTER TABLE orders ADD COLUMN commerce_mode VARCHAR(16) NULL, ADD COLUMN commerce_partner_id BIGINT NULL;
ALTER TABLE items ADD COLUMN reseller_unit_price DECIMAL(24,6) NULL;
CREATE TABLE product_price_approval (
 product_id BIGINT PRIMARY KEY, baseline_retail DECIMAL(24,2) NOT NULL, baseline_reseller DECIMAL(24,2) NOT NULL,
 current_retail DECIMAL(24,2) NULL, reseller_total DECIMAL(24,2) NULL,
 state VARCHAR(20) NOT NULL DEFAULT 'UNMEASURED', revision BIGINT NOT NULL DEFAULT 0,
 calculation_hash CHAR(64) NULL, FOREIGN KEY(product_id) REFERENCES order_products(id)
);
CREATE TABLE product_price_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, revision BIGINT NOT NULL,
 action VARCHAR(20) NOT NULL, actor VARCHAR(255) NOT NULL, note VARCHAR(2000) NOT NULL,
 snapshot JSON NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(product_id) REFERENCES order_products(id), INDEX ix_price_events(product_id,id)
);
