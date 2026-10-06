ALTER TABLE parts ADD COLUMN inventory_unit_cost DECIMAL(24,6) NULL,
 ADD COLUMN inventory_cost_unit VARCHAR(24) NULL;
CREATE TABLE priced_material_receipts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 request_key CHAR(36) NOT NULL UNIQUE,
 request_json JSON NOT NULL,
 vendor_no BIGINT NOT NULL,
 document_no VARCHAR(120) NOT NULL,
 document_date DATE NOT NULL,
 supplier_order_id BIGINT NULL,
 supplier_receipt_id BIGINT NULL,
 snapshot JSON NOT NULL,
 actor VARCHAR(255) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_priced_vendor_document(vendor_no,document_no,document_date)
);
CREATE TABLE priced_material_receipt_lines (
 receipt_id BIGINT NOT NULL,
 part_no BIGINT NOT NULL,
 stock_operation_key CHAR(36) NOT NULL UNIQUE,
 PRIMARY KEY(receipt_id,part_no),
 FOREIGN KEY(receipt_id) REFERENCES priced_material_receipts(id),
 FOREIGN KEY(stock_operation_key) REFERENCES material_stock_operations(request_key)
);
