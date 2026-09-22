CREATE TABLE inventory_transaction_types (
                                             id BIGINT NOT NULL AUTO_INCREMENT,
                                             code VARCHAR(30) NOT NULL,
                                             name VARCHAR(100) NOT NULL,
                                             description VARCHAR(255),

                                             PRIMARY KEY (id),
                                             UNIQUE KEY uk_inventory_tx_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


INSERT INTO inventory_transaction_types (code, name, description) VALUES
                                                                      ('INBOUND', 'Прихід товару', 'Закупка або виробництво'),
                                                                      ('OUTBOUND', 'Списання товару', 'Списання під замовлення'),
                                                                      ('ADJUSTMENT', 'Коригування', 'Інвентаризація'),
                                                                      ('WRITE_OFF', 'Списання браку', 'Брак або пошкодження');