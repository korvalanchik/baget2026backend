-- Product-level production. V54 is the preceding accessories migration.
ALTER TABLE orders ADD COLUMN workshop_mode BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE order_products
 ADD COLUMN production_state VARCHAR(24) NOT NULL DEFAULT 'WAITING',
 ADD COLUMN production_revision BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN measured_width_mm DECIMAL(12,3) NULL,
 ADD COLUMN measured_height_mm DECIMAL(12,3) NULL,
 ADD COLUMN workshop_note VARCHAR(2000) NULL,
 ADD COLUMN assigned_master VARCHAR(255) NULL,
 ADD COLUMN accepted_at TIMESTAMP(6) NULL,
 ADD COLUMN completed_at TIMESTAMP(6) NULL,
 ADD COLUMN production_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN confirmed_revision BIGINT NULL,
 ADD COLUMN confirmed_calculation JSON NULL;
-- Preserve already posted orders: migration never repeats inventory posting.
UPDATE order_products p JOIN orders o ON o.OrderNo=p.order_no
 SET p.production_state=CASE WHEN o.production_finalized THEN 'COMPLETED'
     WHEN o.StatusOrder IN (5,10) THEN 'STOPPED'
     WHEN o.StatusOrder IN (2,3) THEN 'IN_PROGRESS' ELSE 'WAITING' END,
     p.production_confirmed=o.production_finalized;
CREATE TABLE product_production_events (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_product_id BIGINT NOT NULL,
 revision BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 note VARCHAR(2000) NULL,
 snapshot JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_product_production_revision(order_product_id,revision),
 CONSTRAINT fk_product_production_event FOREIGN KEY(order_product_id) REFERENCES order_products(id)
);
