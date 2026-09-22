CREATE TABLE transaction_types (
                                   type_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                   code VARCHAR(20) NOT NULL UNIQUE, -- наприклад INVOICE, PAYMENT, REFUND
                                   description VARCHAR(100)          -- опис для користувача
);

CREATE TABLE transactions (
                              transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY, -- до кого належить транзакція
                              customer_id BIGINT NULL, -- транзакція може бути пов’язана з конкретним замовленням (або NULL)
                              order_no BIGINT NULL,
                              type_id BIGINT NOT NULL,  -- Payment, Refund, Deposit, Withdrawal, Transfer
                              transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              amount DECIMAL(10,2) NOT NULL,
                              reference VARCHAR(50), -- номер чеку, рахунку або внутрішній reference
                              status VARCHAR(20) DEFAULT 'Pending', -- Pending, Completed, Canceled
                              note TEXT,

                              CONSTRAINT fk_transaction_customer FOREIGN KEY (customer_id)
                                  REFERENCES customer(CustNo),

                              CONSTRAINT fk_transaction_order FOREIGN KEY (order_no)
                                  REFERENCES orders(OrderNo),

                              CONSTRAINT fk_transaction_type FOREIGN KEY (type_id)
                                  REFERENCES transaction_types(type_id)
);

INSERT INTO transaction_types (code, description) VALUES
                                                      ('INVOICE', 'Рахунок'),
                                                      ('PAYMENT', 'Оплата'),
                                                      ('REFUND', 'Повернення'),
                                                      ('ADJUSTMENT', 'Коригування'),
                                                      ('TRANSFER', 'Переказ'),
                                                      ('CHARGE', 'Нарахування'),
                                                      ('DISCOUNT', 'Знижка'),
                                                      ('CANCEL', 'Скасування'),
                                                      ('ADVANCE_PAYMENT', 'Аванс');