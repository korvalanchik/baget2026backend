-- Review and confirm V48 is free in your actual Flyway history before installation.
-- No prices, inventory or legacy fields are changed. Existing metadata is preserved.
UPDATE parts AS p
JOIN (
    SELECT 5130 AS part_no, 1 AS quality, 'SQUARE_METER' AS unit_code,
           'AREA_WITH_MARGIN' AS method_code, 'MATERIAL' AS kind_code
    UNION ALL SELECT 5140, 2, 'METER', 'PERIMETER', 'WORK'
    UNION ALL SELECT 5150, 2, 'METER', 'PERIMETER', 'WORK'
    UNION ALL SELECT 27970, 1, 'SQUARE_METER', 'AREA', 'MATERIAL'
) cfg ON p.PartNo = cfg.part_no
SET p.unit_type = cfg.unit_code,
    p.calculation_method = cfg.method_code,
    p.part_kind = cfg.kind_code,
    p.calculation_params = CASE WHEN cfg.part_no = 5130
        THEN JSON_OBJECT('marginWidthMm', 10, 'marginHeightMm', 10)
        ELSE JSON_OBJECT() END,
    p.Version = COALESCE(p.Version, 0) + 1
WHERE p.InQuality = cfg.quality
  AND p.unit_type IS NULL AND p.calculation_method IS NULL
  AND p.part_kind IS NULL AND p.calculation_params IS NULL;

-- 33370 already configured by V47: PIECE / FIXED / WORK / {}.
-- Inspect all five rows after migration; success alone does not guarantee no rows were skipped.
-- SELECT PartNo, Description, unit_type, calculation_method, part_kind, calculation_params
-- FROM parts WHERE PartNo IN (5130,5140,5150,27970,33370) ORDER BY PartNo;
-- 28860, 28870 and 28880 are intentionally untouched and unused.
