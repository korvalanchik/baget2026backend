-- REVIEW BEFORE APPLYING. Not executed by the author.
-- Deploy the updated ConsumptionCalculator before applying this migration.
-- PERIMETER now accepts an optional positive railWidthMm parameter.
-- Confirm V47 is unused in your actual Flyway history.
-- Only five confirmed positions are changed; no schema/legacy price/stock changes.
-- Existing calculation settings are never overwritten.
UPDATE `parts` AS p
JOIN (
    SELECT 28020 AS part_no, 46 AS rail_width_mm, 2 AS quality,
           'METER' AS unit_code, 'PERIMETER' AS method_code, 'MATERIAL' AS kind_code
    UNION ALL SELECT 28060, 28, 2, 'METER', 'PERIMETER', 'MATERIAL'
    UNION ALL SELECT 28210, 56, 2, 'METER', 'PERIMETER', 'MATERIAL'
    UNION ALL SELECT 29720, 46, 2, 'METER', 'PERIMETER', 'MATERIAL'
    UNION ALL SELECT 33370, NULL, 3, 'PIECE', 'FIXED', 'WORK'
) AS cfg ON p.`PartNo` = cfg.part_no
SET p.`unit_type` = cfg.unit_code,
    p.`calculation_method` = cfg.method_code,
    p.`part_kind` = cfg.kind_code,
    p.`calculation_params` = CASE
        WHEN cfg.rail_width_mm IS NULL THEN JSON_OBJECT()
        ELSE JSON_OBJECT('railWidthMm', cfg.rail_width_mm)
    END,
    p.`Version` = COALESCE(p.`Version`, 0) + 1
WHERE p.`InQuality` = cfg.quality
  AND p.`unit_type` IS NULL
  AND p.`calculation_method` IS NULL
  AND p.`part_kind` IS NULL
  AND p.`calculation_params` IS NULL;

-- Run these checks separately after Flyway applies the migration:
-- SELECT PartNo, Description, unit_type, calculation_method, part_kind, calculation_params
-- FROM parts WHERE PartNo IN (28020,28060,28210,29720,33370) ORDER BY PartNo;
-- Expected: five configured rows; rail widths 46,28,56,46 and {} for work.
-- SELECT COUNT(*), SUM(unit_type IS NOT NULL), SUM(calculation_method IS NOT NULL),
-- SUM(part_kind IS NOT NULL), SUM(calculation_params IS NOT NULL) FROM parts;
-- If there were no other changes since V46: 1341,12,12,12,12.
-- A successful migration can skip changed/missing rows: always inspect results.
-- Rail 28030 is identified as the crossbar material but not configured here.
-- It needs length supplied by the composition rule, not PERIMETER or FRAME_PROFILE.
-- Its prices and legacy data remain unchanged. No automatic stock writes.
