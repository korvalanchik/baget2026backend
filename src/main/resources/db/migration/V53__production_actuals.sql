-- Together with V52, installed directly over step 12. Financial ledger is unchanged.
ALTER TABLE orders ADD COLUMN production_finalized BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN production_total DECIMAL(24,2) NULL;
ALTER TABLE order_products ADD COLUMN actual_total DECIMAL(24,2) NULL;
ALTER TABLE items ADD COLUMN actual_consumption DECIMAL(24,6) NULL,
 ADD COLUMN actual_width_mm DECIMAL(12,3) NULL,
 ADD COLUMN actual_height_mm DECIMAL(12,3) NULL,
 ADD COLUMN actual_amount DECIMAL(24,2) NULL,
 ADD COLUMN billing_unit_price DECIMAL(24,6) NULL,
 ADD COLUMN stock_material BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE inventory_movements (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_no BIGINT NOT NULL,
 item_no BIGINT NOT NULL,
 part_no BIGINT NOT NULL,
 quantity_delta DECIMAL(24,6) NOT NULL,
 stock_before DECIMAL(24,6) NOT NULL,
 stock_after DECIMAL(24,6) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_production_consumption(order_no,item_no),
 FOREIGN KEY(order_no,item_no) REFERENCES items(OrderNo,ItemNo),
 FOREIGN KEY(part_no) REFERENCES parts(PartNo)
);
CREATE TABLE production_events (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_no BIGINT NOT NULL,
 revision BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 note VARCHAR(500) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(order_no) REFERENCES orders(OrderNo)
);
