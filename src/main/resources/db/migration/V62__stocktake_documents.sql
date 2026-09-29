-- Parts entity uses Double. Widen legacy FLOAT so counts up to 999999999 retain six decimal places.
-- Existing stock is preserved; no unknown balance is converted to zero.
ALTER TABLE parts MODIFY COLUMN OnHand DOUBLE NULL DEFAULT NULL;
CREATE TABLE stocktake_documents (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 request_key CHAR(36) NOT NULL UNIQUE,
 request_json JSON NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 document_date DATE NOT NULL,
 note VARCHAR(1000) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 posted_at TIMESTAMP(6) NULL,
 posted_by VARCHAR(255) NULL,
 post_key CHAR(36) NULL UNIQUE,
 post_request JSON NULL,
 posted_snapshot JSON NULL,
 CHECK(status IN ('DRAFT','POSTED','CANCELLED'))
);
CREATE TABLE stocktake_lines (
 document_id BIGINT NOT NULL,
 part_no BIGINT NOT NULL,
 description VARCHAR(1000) NOT NULL,
 unit VARCHAR(24) NOT NULL,
 baseline JSON NOT NULL,
 baseline_stock DECIMAL(24,6) NULL,
 counted_qty DECIMAL(24,6) NULL,
 note VARCHAR(1000) NOT NULL DEFAULT '',
 PRIMARY KEY(document_id,part_no),
 FOREIGN KEY(document_id) REFERENCES stocktake_documents(id),
 FOREIGN KEY(part_no) REFERENCES parts(PartNo),
 CHECK(counted_qty IS NULL OR counted_qty>=0)
);
CREATE TABLE stocktake_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 document_id BIGINT NOT NULL,
 revision BIGINT NOT NULL,
 action VARCHAR(24) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 snapshot JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(document_id) REFERENCES stocktake_documents(id)
);
CREATE TABLE stocktake_posted_lines (
 document_id BIGINT NOT NULL,
 part_no BIGINT NOT NULL,
 stock_operation_key CHAR(36) NOT NULL UNIQUE,
 PRIMARY KEY(document_id,part_no),
 FOREIGN KEY(document_id) REFERENCES stocktake_documents(id),
 FOREIGN KEY(part_no) REFERENCES parts(PartNo),
 FOREIGN KEY(stock_operation_key) REFERENCES material_stock_operations(request_key)
);
