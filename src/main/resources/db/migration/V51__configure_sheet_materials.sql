-- Step 11: confirmed glass/acrylic, PVC and mat sheet materials, in square metres.
-- This V51 is a proposal; V50 is the last migration in the supplied original archive.
-- Check flyway_schema_history before applying. Do not replace an already applied V51.
-- Configure only previously unconfigured rows; preserve existing metadata and prices.
UPDATE parts
SET unit_type = 'SQUARE_METER', calculation_method = 'AREA', part_kind = 'MATERIAL',
    calculation_params = JSON_OBJECT(), Version = COALESCE(Version, 0) + 1
WHERE PartNo IN (12670, 27470, 27510, 27540, 27520, 5160, 28010, 29500)
  AND InQuality = 1
  AND unit_type IS NULL AND calculation_method IS NULL
  AND part_kind IS NULL AND calculation_params IS NULL;

-- Verify after migration: all eight rows should be SQUARE_METER / AREA / MATERIAL / {}.
-- SELECT PartNo, unit_type, calculation_method, part_kind, calculation_params
-- FROM parts WHERE PartNo IN (12670, 27470, 27510, 27540, 27520, 5160, 28010, 29500);
-- Mirror 27760 and DVP 4460 were configured earlier and are deliberately excluded.
-- Obsolete glass 5230 and 12680 are deliberately excluded.
-- Sheet counts and visible mat borders belong to product composition, not Parts metadata.
