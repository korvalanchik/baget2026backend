-- Future customer handovers only. Disabled until an administrator sets the rate and enables accrual.
CREATE TABLE reward_settings (
 id INT PRIMARY KEY, enabled BOOLEAN NOT NULL DEFAULT FALSE, rate DECIMAL(7,4) NOT NULL DEFAULT 0,
 revision BIGINT NOT NULL DEFAULT 0, CHECK(rate>=0 AND rate<=100)
);
INSERT INTO reward_settings(id) VALUES(1);
CREATE TABLE reward_rates (
 user_id BIGINT PRIMARY KEY, rate DECIMAL(7,4) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id), CHECK(rate>=0 AND rate<=100)
);
CREATE TABLE reward_accounts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,branch_no BIGINT NOT NULL,revision BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uq_reward_account(user_id,branch_no),FOREIGN KEY(user_id) REFERENCES users(id),FOREIGN KEY(branch_no) REFERENCES branches(BranchNo)
);
CREATE TABLE reward_issues (
 product_id BIGINT PRIMARY KEY,order_no BIGINT NOT NULL,branch_no BIGINT NOT NULL,author_id BIGINT NULL,
 base_amount DECIMAL(24,2) NULL,rate DECIMAL(7,4) NOT NULL,personal_rate BOOLEAN NOT NULL,
 state VARCHAR(16) NOT NULL,reason VARCHAR(500) NULL,actor VARCHAR(255) NOT NULL,
 issued_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(product_id) REFERENCES order_products(id), CHECK(state IN ('POSTED','REVIEW'))
);
CREATE TABLE reward_entries (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,account_id BIGINT NOT NULL,kind VARCHAR(16) NOT NULL,
 product_id BIGINT NULL,source_id BIGINT NULL,amount DECIMAL(24,2) NOT NULL,
 method VARCHAR(16) NULL,note VARCHAR(1000) NOT NULL,actor VARCHAR(255) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(account_id) REFERENCES reward_accounts(id),FOREIGN KEY(product_id) REFERENCES reward_issues(product_id),
 FOREIGN KEY(source_id) REFERENCES reward_entries(id),INDEX reward_account_history(account_id,id),
 CHECK(kind IN ('ACCRUAL','PAYOUT','CORRECTION','REVERSAL')),CHECK(kind<>'PAYOUT' OR amount<0)
);
CREATE TABLE reward_requests (
 request_key CHAR(36) PRIMARY KEY,actor VARCHAR(255) NOT NULL,operation VARCHAR(24) NOT NULL,
 payload JSON NOT NULL,result JSON NOT NULL,created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
