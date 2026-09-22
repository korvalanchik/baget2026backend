CREATE TABLE finance_transactions (
                                      id BIGINT NOT NULL AUTO_INCREMENT,

                                      direction VARCHAR(10) NOT NULL COMMENT 'IN / OUT',
                                      category VARCHAR(30) NOT NULL COMMENT 'CUSTOMER_PAYMENT, MATERIALS, RENT, etc',

                                      amount DECIMAL(12,2) NOT NULL COMMENT 'Always positive number',

                                      created_at DATETIME(6) NOT NULL,

                                      user_id BIGINT NULL COMMENT 'Who created transaction',

                                      customer_transaction_id BIGINT NULL COMMENT 'Link to customer transaction if exists',

                                      reference VARCHAR(50) NULL,
                                      note TEXT NULL,

                                      PRIMARY KEY (id),

                                      KEY idx_finance_created_at (created_at),
                                      KEY idx_finance_category (category),
                                      KEY idx_finance_direction (direction),
                                      KEY idx_finance_user (user_id),
                                      KEY idx_finance_customer_tx (customer_transaction_id),

                                      CONSTRAINT fk_finance_user
                                          FOREIGN KEY (user_id)
                                              REFERENCES users(id)
                                              ON DELETE SET NULL

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
