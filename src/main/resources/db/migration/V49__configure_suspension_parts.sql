-- Review before applying; confirm V49 is unused in the actual Flyway history.
-- Existing V46 already configured 27580 and cord 28260.
UPDATE parts
SET unit_type = 'PIECE',
    calculation_method = 'QUANTITY',
    part_kind = 'MATERIAL',
    calculation_params = JSON_OBJECT(),
    Version = COALESCE(Version, 0) + 1
WHERE PartNo IN (14450, 14460, 23080, 29650)
  AND InQuality = 3
  AND unit_type IS NULL AND calculation_method IS NULL
  AND part_kind IS NULL AND calculation_params IS NULL;

-- Inspect after Flyway: migration success can mean some rows were skipped.
-- SELECT PartNo, Description, unit_type, calculation_method, part_kind, calculation_params
-- FROM parts WHERE PartNo IN (27580,14450,14460,23080,29650,28260) ORDER BY PartNo;
-- First five: PIECE / QUANTITY / MATERIAL / {}.
-- 28260: METER / WIDTH_PLUS_ALLOWANCE / MATERIAL / {"allowanceMm":100}.
-- DVP 4460 is context only, not priced by suspension-preview; no changes to its metadata.
-- No price, stock, ProfilWidth or InQuality changes. No updates to legacy hangers 02/03.
