CREATE TABLE inventory_balance (
                                   part_no BIGINT NOT NULL,

                                   quantity DECIMAL(12,3) NOT NULL,
                                   avg_cost DECIMAL(12,4) NOT NULL,

                                   updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                       ON UPDATE CURRENT_TIMESTAMP,

                                   PRIMARY KEY (part_no),

                                   CONSTRAINT fk_inv_balance_part
                                       FOREIGN KEY (part_no)
                                           REFERENCES parts (PartNo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
