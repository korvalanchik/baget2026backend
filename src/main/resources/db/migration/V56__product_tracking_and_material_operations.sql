ALTER TABLE order_products
 ADD COLUMN tracking_id CHAR(36) NULL,
 ADD COLUMN current_location VARCHAR(24) NOT NULL DEFAULT 'BRANCH',
 ADD COLUMN tracking_revision BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN pending_location VARCHAR(24) NULL,
 ADD COLUMN transfer_actor VARCHAR(255) NULL,
 ADD COLUMN warehouse_received_at TIMESTAMP(6) NULL;
UPDATE order_products SET tracking_id=UUID(),current_location=CASE WHEN production_state='WAITING' THEN 'BRANCH' ELSE 'WORKSHOP' END;
ALTER TABLE order_products MODIFY tracking_id CHAR(36) NOT NULL, ADD UNIQUE KEY uq_product_tracking(tracking_id);
CREATE TABLE product_transfers (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 product_id BIGINT NOT NULL,
 action VARCHAR(24) NOT NULL,
 from_location VARCHAR(24) NOT NULL,
 to_location VARCHAR(24) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 note VARCHAR(1000) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(product_id) REFERENCES order_products(id)
);
CREATE TABLE material_stock_operations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 request_key CHAR(36) NOT NULL UNIQUE,
 part_no BIGINT NOT NULL,
 operation VARCHAR(24) NOT NULL,
 quantity DECIMAL(24,6) NOT NULL,
 stock_before DECIMAL(24,6) NULL,
 stock_after DECIMAL(24,6) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 note VARCHAR(1000) NOT NULL,
 request_json JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(part_no) REFERENCES parts(PartNo)
);
CREATE TABLE material_purchase_requests (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 request_key CHAR(36) NOT NULL UNIQUE,
 part_no BIGINT NOT NULL,
 quantity DECIMAL(24,6) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
 revision BIGINT NOT NULL DEFAULT 0,
 actor VARCHAR(255) NOT NULL,
 note VARCHAR(1000) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(part_no) REFERENCES parts(PartNo)
);
CREATE TABLE material_request_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, request_id BIGINT NOT NULL,
 status VARCHAR(24) NOT NULL, actor VARCHAR(255) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(request_id) REFERENCES material_purchase_requests(id)
);
-- Adds a selectable role; no user's permissions are changed automatically.
INSERT INTO roles(name) SELECT 'ROLE_WAREHOUSE' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name='ROLE_WAREHOUSE');
