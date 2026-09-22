CREATE TABLE invoices (
                          id BIGINT PRIMARY KEY AUTO_INCREMENT,

                          invoice_no BIGINT NOT NULL UNIQUE,

                          customer_id BIGINT NOT NULL,

                          type VARCHAR(20) NOT NULL,
                          status VARCHAR(20) NOT NULL,

                          total_amount DECIMAL(15,2) NOT NULL,

                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          note TEXT,

                          CONSTRAINT fk_invoice_customer
                              FOREIGN KEY (customer_id) REFERENCES customer(CustNo)
);

CREATE TABLE invoice_orders (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                invoice_id BIGINT NOT NULL,
                                order_no BIGINT NOT NULL,
                                amount DECIMAL(15,2) NOT NULL,

                                CONSTRAINT fk_io_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id),
                                CONSTRAINT fk_io_order FOREIGN KEY (order_no) REFERENCES orders(orderNo),
                                UNIQUE (invoice_id, order_no)
);
