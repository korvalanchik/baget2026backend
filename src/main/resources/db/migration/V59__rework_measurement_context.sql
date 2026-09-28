-- Remember explicit calculation choices for newly selected rework articles.
-- Existing quantities, measurements and stock movements are unchanged.
ALTER TABLE product_rework_lines
 ADD COLUMN measurement_basis VARCHAR(16) NOT NULL DEFAULT 'OUTER',
 ADD COLUMN calculation_units INT NOT NULL DEFAULT 1,
 ADD CONSTRAINT ck_rework_measurement_basis CHECK(measurement_basis IN ('ARTWORK','OUTER')),
 ADD CONSTRAINT ck_rework_calculation_units CHECK(calculation_units BETWEEN 1 AND 10000);
