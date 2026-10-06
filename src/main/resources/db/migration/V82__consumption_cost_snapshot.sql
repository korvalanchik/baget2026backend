ALTER TABLE items ADD COLUMN inventory_unit_cost DECIMAL(24,6) NULL;
ALTER TABLE inventory_movements
 ADD COLUMN inventory_unit_cost DECIMAL(24,6) NULL,
 ADD COLUMN inventory_cost_unit VARCHAR(24) NULL,
 ADD COLUMN inventory_cost_captured_at TIMESTAMP(6) NULL;
-- Historical costs are deliberately not reconstructed from current parts prices.
