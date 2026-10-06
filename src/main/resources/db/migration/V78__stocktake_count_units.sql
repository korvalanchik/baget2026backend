CREATE TABLE material_count_units (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 part_no BIGINT NOT NULL,
 label VARCHAR(100) NOT NULL,
 base_unit VARCHAR(30) NOT NULL,
 factor DECIMAL(18,6) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 actor VARCHAR(255) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX ix_material_count_units(part_no,active)
);
ALTER TABLE stocktake_lines ADD COLUMN count_breakdown JSON NULL;
