-- Review first. Confirm V50 is unused before moving into db/migration.
-- DVP area for a mirror: ceil(widthMm) * ceil(heightMm) / 1000000 square metres.
UPDATE parts
SET unit_type = 'SQUARE_METER',
    calculation_method = 'AREA',
    part_kind = 'MATERIAL',
    calculation_params = JSON_OBJECT(),
    Version = COALESCE(Version, 0) + 1
WHERE PartNo = 4460 AND InQuality = 1
  AND unit_type IS NULL AND calculation_method IS NULL
  AND part_kind IS NULL AND calculation_params IS NULL;

-- SELECT PartNo, Description, unit_type, calculation_method, part_kind, calculation_params
-- FROM parts WHERE PartNo = 4460;
-- Expected SQUARE_METER / AREA / MATERIAL / {}.
-- Existing metadata is never overwritten; inspect for skipped rows after Flyway.
-- No changes to prices, stock, ProfilWidth, InQuality or previous migrations.
