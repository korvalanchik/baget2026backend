CREATE TABLE customer_transactions (
                                       id BIGINT NOT NULL AUTO_INCREMENT,

                                       customer_id BIGINT NOT NULL COMMENT 'Customer (owner of balance)',
                                       order_no BIGINT NULL COMMENT 'Optional order reference',

                                       type VARCHAR(20) NOT NULL COMMENT 'INVOICE, PAYMENT, ADVANCE, REFUND, ADJUSTMENT',

    /**
     * + amount  => increases customer debt
     * - amount  => decreases customer debt
     */
                                       amount DECIMAL(12,2) NOT NULL,

                                       created_at DATETIME(6) NOT NULL,

                                       reference VARCHAR(50) NULL,
                                       note TEXT NULL,

                                       active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Soft delete / cancel flag',

                                       PRIMARY KEY (id),

                                       KEY idx_customer_tx_customer (customer_id),
                                       KEY idx_customer_tx_order (order_no),
                                       KEY idx_customer_tx_created (created_at),
                                       KEY idx_customer_tx_type (type),
                                       KEY idx_customer_tx_active (active),

                                       CONSTRAINT fk_customer_tx_customer
                                           FOREIGN KEY (customer_id)
                                               REFERENCES customer(custNo)
                                               ON DELETE RESTRICT,

                                       CONSTRAINT fk_customer_tx_order
                                           FOREIGN KEY (order_no)
                                               REFERENCES orders(orderNo)
                                               ON DELETE SET NULL

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
