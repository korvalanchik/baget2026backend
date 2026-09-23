-- Check V54 is unused before deployment. Preserve prices, stock and existing configuration.
UPDATE parts SET unit_type='PIECE',calculation_method='QUANTITY',part_kind='MATERIAL',
 calculation_params=JSON_OBJECT(),Version=COALESCE(Version,0)+1
WHERE PartNo IN(29220,27130) AND InQuality=3
 AND unit_type IS NULL AND calculation_method IS NULL AND part_kind IS NULL AND calculation_params IS NULL;
-- Verify both rows exist and have PIECE / QUANTITY / MATERIAL / {}.
