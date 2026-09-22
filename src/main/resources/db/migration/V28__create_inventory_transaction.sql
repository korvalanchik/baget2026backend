CREATE TABLE inventory_transactions (
                                        id BIGINT NOT NULL AUTO_INCREMENT,

                                        part_no BIGINT NOT NULL,
                                        order_no BIGINT NULL,
                                        transaction_type_id BIGINT NOT NULL,

                                        quantity DECIMAL(12,3) NOT NULL,
                                        unit_cost DECIMAL(12,4) NOT NULL,
                                        total_cost DECIMAL(14,4) NOT NULL,

                                        transaction_date DATETIME NOT NULL,
                                        reference VARCHAR(255),
                                        note VARCHAR(255),

                                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                        PRIMARY KEY (id),

                                        CONSTRAINT fk_inv_tx_part
                                            FOREIGN KEY (part_no)
                                                REFERENCES parts (PartNo),

                                        CONSTRAINT fk_inv_tx_order
                                            FOREIGN KEY (order_no)
                                                REFERENCES orders (OrderNo),

                                        CONSTRAINT fk_inv_tx_type
                                            FOREIGN KEY (transaction_type_id)
                                                REFERENCES inventory_transaction_types (id),

                                        KEY idx_inv_tx_part (part_no),
                                        KEY idx_inv_tx_order (order_no),
                                        KEY idx_inv_tx_type (transaction_type_id),
                                        KEY idx_inv_tx_date (transaction_date)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;