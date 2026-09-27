CREATE TABLE product_invoice_claims (
 product_id BIGINT PRIMARY KEY, invoice_id BIGINT NOT NULL,
 FOREIGN KEY(product_id) REFERENCES order_products(id), FOREIGN KEY(invoice_id) REFERENCES invoices(id)
);
CREATE TABLE invoice_products (
 invoice_id BIGINT NOT NULL, product_id BIGINT NOT NULL, order_no BIGINT NOT NULL,
 confirmed_revision BIGINT NOT NULL, snapshot JSON NOT NULL,
 original_amount DECIMAL(24,2) NOT NULL, billed_amount DECIMAL(24,2) NOT NULL,
 source_invoice_id BIGINT NULL,
 PRIMARY KEY(invoice_id,product_id),
 FOREIGN KEY(invoice_id) REFERENCES invoices(id), FOREIGN KEY(product_id) REFERENCES order_products(id),
 FOREIGN KEY(order_no) REFERENCES orders(OrderNo), FOREIGN KEY(source_invoice_id) REFERENCES invoices(id)
);
CREATE TABLE billing_requests (
 request_key CHAR(36) PRIMARY KEY, actor VARCHAR(255) NOT NULL, payload JSON NOT NULL,
 invoice_id BIGINT NULL, FOREIGN KEY(invoice_id) REFERENCES invoices(id)
);
CREATE TABLE invoice_number_lock (id INT PRIMARY KEY, day_key CHAR(8) NULL, last_no BIGINT NOT NULL DEFAULT 0);
INSERT INTO invoice_number_lock(id) VALUES(1);
