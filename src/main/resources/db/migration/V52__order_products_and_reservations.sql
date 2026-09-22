-- Apply only if V52 is unused. No legacy orders or unknown stock are backfilled.
CREATE TABLE order_products (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_no BIGINT NOT NULL,
 client_product_id VARCHAR(64) NOT NULL,
 revision BIGINT NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 configuration JSON NOT NULL,
 calculation JSON NOT NULL,
 total DECIMAL(24,2) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_product_order (id,order_no),
 UNIQUE KEY uq_product_revision (order_no,revision,client_product_id),
 CONSTRAINT fk_product_order FOREIGN KEY(order_no) REFERENCES orders(OrderNo)
);
ALTER TABLE orders
 ADD COLUMN calculation_revision BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN calculation_request_key VARCHAR(36) NULL,
 ADD COLUMN calculation_request_hash CHAR(64) NULL,
 ADD UNIQUE KEY uq_calculation_request (calculation_request_key);
ALTER TABLE items
 ADD COLUMN order_product_id BIGINT NULL,
 ADD COLUMN planned_consumption DECIMAL(24,6) NULL,
 ADD COLUMN calculated_amount DECIMAL(24,2) NULL,
 ADD COLUMN calculation_role VARCHAR(32) NULL,
 ADD COLUMN calculation_unit VARCHAR(20) NULL,
 ADD CONSTRAINT fk_item_product_order FOREIGN KEY(order_product_id,OrderNo)
 REFERENCES order_products(id,order_no);
CREATE TABLE material_reservations (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_product_id BIGINT NOT NULL,
 part_no BIGINT NOT NULL,
 demand DECIMAL(24,6) NOT NULL,
 reserved DECIMAL(24,6) NOT NULL DEFAULT 0,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_reservation_material (order_product_id,part_no),
 KEY ix_reservation_allocation (part_no,active,id),
 CONSTRAINT fk_reservation_product FOREIGN KEY(order_product_id) REFERENCES order_products(id),
 CONSTRAINT fk_reservation_part FOREIGN KEY(part_no) REFERENCES parts(PartNo),
 CHECK(demand >= 0 AND reserved >= 0 AND reserved <= demand)
);
CREATE TABLE reservation_events (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 reservation_id BIGINT NOT NULL,
 reserved_before DECIMAL(24,6) NOT NULL,
 reserved_after DECIMAL(24,6) NOT NULL,
 reason VARCHAR(32) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_event_reservation FOREIGN KEY(reservation_id) REFERENCES material_reservations(id)
);
-- Physical stock is no longer modified by inserting/updating/deleting Items.
DROP TRIGGER IF EXISTS update_on_hand_after_insert;
DROP TRIGGER IF EXISTS update_on_hand_after_update;
DROP TRIGGER IF EXISTS update_on_hand_after_delete;
-- Number allocation now uses one row lock in Java for both legacy and new clients.
DROP TRIGGER IF EXISTS update_temp_order_counter;
UPDATE temp_order_counter SET last_temp_order_no = GREATEST(last_temp_order_no,
 COALESCE((SELECT MAX(OrderNo) FROM orders),0)) WHERE id=1;
