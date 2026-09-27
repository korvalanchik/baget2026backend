-- Additive migration after V57. Confirmed consumption and financial entries are never reversed here.
CREATE TABLE product_reworks (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 product_id BIGINT NOT NULL,
 opened_revision BIGINT NOT NULL,
 charge_mode VARCHAR(24) NOT NULL,
 reason VARCHAR(2000) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 state VARCHAR(24) NOT NULL DEFAULT 'OPEN',
 source_confirmation JSON NOT NULL,
 baseline_total DECIMAL(24,2) NOT NULL,
 width_mm DECIMAL(12,3) NULL,
 height_mm DECIMAL(12,3) NULL,
 work_note VARCHAR(2000) NULL,
 saved BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 completed_at TIMESTAMP(6) NULL,
 completed_revision BIGINT NULL,
 final_confirmation JSON NULL,
 UNIQUE KEY uq_rework_revision(product_id,opened_revision),
 FOREIGN KEY(product_id) REFERENCES order_products(id),
 CHECK(charge_mode IN ('WORKSHOP','CUSTOMER')),
 CHECK(state IN ('OPEN','COMPLETED'))
);
ALTER TABLE order_products ADD COLUMN active_rework_id BIGINT NULL,
 ADD CONSTRAINT fk_active_rework FOREIGN KEY(active_rework_id) REFERENCES product_reworks(id);
CREATE TABLE product_rework_lines (
 rework_id BIGINT NOT NULL,
 line_no INT NOT NULL,
 source_item_no BIGINT NULL,
 part_no BIGINT NOT NULL,
 description VARCHAR(255) NOT NULL,
 unit VARCHAR(20) NOT NULL,
 material BOOLEAN NOT NULL,
 quantity DECIMAL(24,6) NOT NULL,
 unit_price DECIMAL(24,6) NOT NULL,
 unit_cost DECIMAL(24,6) NULL,
 width_mm DECIMAL(12,3) NULL,
 height_mm DECIMAL(12,3) NULL,
 PRIMARY KEY(rework_id,line_no),
 FOREIGN KEY(rework_id) REFERENCES product_reworks(id),
 FOREIGN KEY(part_no) REFERENCES parts(PartNo),
 CHECK(quantity > 0 AND unit_price >= 0)
);
ALTER TABLE items ADD COLUMN rework_id BIGINT NULL,
 ADD CONSTRAINT fk_item_rework FOREIGN KEY(rework_id) REFERENCES product_reworks(id);
