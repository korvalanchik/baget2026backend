-- Physical stock remains Parts.OnHand. Legacy OnOrder / requests are not supplier receipts.
CREATE TABLE material_stock_policy (
 part_no BIGINT PRIMARY KEY,
 minimum_qty DECIMAL(24,6) NOT NULL DEFAULT 0,
 target_qty DECIMAL(24,6) NOT NULL DEFAULT 0,
 revision BIGINT NOT NULL DEFAULT 0,
 actor VARCHAR(255) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(part_no) REFERENCES parts(PartNo),
 CHECK(minimum_qty>=0 AND target_qty>=minimum_qty)
);
CREATE TABLE supplier_orders (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 request_key CHAR(36) NOT NULL UNIQUE,
 request_json JSON NOT NULL,
 vendor_no BIGINT NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
 revision BIGINT NOT NULL DEFAULT 0,
 expected_date DATE NULL,
 note VARCHAR(1000) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(vendor_no) REFERENCES vendors(VendorNo),
 CHECK(status IN ('DRAFT','ORDERED','PARTIAL','RECEIVED','CANCELLED','CLOSED'))
);
CREATE TABLE supplier_order_lines (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 part_no BIGINT NOT NULL,
 description VARCHAR(1000) NOT NULL,
 unit VARCHAR(24) NOT NULL,
 ordered_qty DECIMAL(24,6) NOT NULL,
 received_qty DECIMAL(24,6) NOT NULL DEFAULT 0,
 UNIQUE KEY uq_supplier_order_part(order_id,part_no),
 FOREIGN KEY(order_id) REFERENCES supplier_orders(id),
 FOREIGN KEY(part_no) REFERENCES parts(PartNo),
 CHECK(ordered_qty>0 AND received_qty>=0 AND received_qty<=ordered_qty)
);
CREATE TABLE supplier_receipts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 request_key CHAR(36) NOT NULL UNIQUE,
 request_json JSON NOT NULL,
 document_no VARCHAR(120) NOT NULL,
 received_date DATE NOT NULL,
 note VARCHAR(1000) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(order_id) REFERENCES supplier_orders(id)
);
CREATE TABLE supplier_receipt_lines (
 receipt_id BIGINT NOT NULL,
 order_line_id BIGINT NOT NULL,
 quantity DECIMAL(24,6) NOT NULL,
 stock_operation_key CHAR(36) NOT NULL UNIQUE,
 PRIMARY KEY(receipt_id,order_line_id),
 FOREIGN KEY(receipt_id) REFERENCES supplier_receipts(id),
 FOREIGN KEY(order_line_id) REFERENCES supplier_order_lines(id),
 FOREIGN KEY(stock_operation_key) REFERENCES material_stock_operations(request_key),
 CHECK(quantity>0)
);
CREATE TABLE procurement_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NULL,
 part_no BIGINT NULL,
 action VARCHAR(32) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 snapshot JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(order_id) REFERENCES supplier_orders(id),
 FOREIGN KEY(part_no) REFERENCES parts(PartNo)
);
