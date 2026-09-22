-- Для перевірки користувачем; на MySQL автором не виконувалось.
-- Передумова: V45 успішно застосована через Flyway.
-- Додати в src/main/resources/db/migration після перевірки на тестовій базі.
-- Один UPDATE налаштовує лише сім підтверджених PartNo.
-- Захист: усі чотири нові поля повинні бути NULL, InQuality має відповідати.
-- Якщо позицію вже налаштували або змінили InQuality, вона буде пропущена.
-- Успіх Flyway сам по собі НЕ гарантує, що налаштовано рівно сім позицій:
-- обов'язково виконати перевірку в кінці цього файла.
-- Ціни, залишки, ProfilWidth та InQuality не змінюються.
-- Version збільшується для узгодження з наявним JPA @Version.
-- Перед редагуванням цих матеріалів оновити відкриті форми.

UPDATE `parts` AS p
JOIN (
    SELECT 27580 AS part_no, 3 AS expected_quality,
           'PIECE' AS unit_code, 'QUANTITY' AS method_code
    UNION ALL SELECT 27760, 1, 'SQUARE_METER', 'AREA'
    UNION ALL SELECT 27780, 3, 'PIECE', 'QUANTITY'
    UNION ALL SELECT 28260, 2, 'METER', 'WIDTH_PLUS_ALLOWANCE'
    UNION ALL SELECT 28540, 2, 'METER', 'PERIMETER'
    UNION ALL SELECT 28900, 3, 'PIECE', 'QUANTITY'
    UNION ALL SELECT 29340, 2, 'METER', 'FRAME_PROFILE'
) AS cfg ON p.`PartNo` = cfg.part_no
SET p.`unit_type` = cfg.unit_code,
    p.`calculation_method` = cfg.method_code,
    p.`part_kind` = 'MATERIAL',
    p.`calculation_params` = CASE
        WHEN cfg.method_code = 'WIDTH_PLUS_ALLOWANCE'
            THEN JSON_OBJECT('allowanceMm', 100)
        ELSE JSON_OBJECT()
    END,
    p.`Version` = COALESCE(p.`Version`, 0) + 1
WHERE p.`InQuality` = cfg.expected_quality
  AND p.`unit_type` IS NULL
  AND p.`calculation_method` IS NULL
  AND p.`part_kind` IS NULL
  AND p.`calculation_params` IS NULL;

-- ПІСЛЯ ЗАСТОСУВАННЯ: виконати окремо, знявши коментарі.
-- SELECT PartNo, Description, unit_type, calculation_method,
--        part_kind, calculation_params, Version
-- FROM parts
-- WHERE PartNo IN (27580,27760,27780,28260,28540,28900,29340)
-- ORDER BY PartNo;
-- Очікування: сім рядків з усіма чотирма заповненими полями.
-- Для 28260: {"allowanceMm": 100}; для решти: {}.
--
-- SELECT COUNT(*) AS total_parts,
--        SUM(unit_type IS NOT NULL) AS configured_units,
--        SUM(calculation_method IS NOT NULL) AS configured_methods,
--        SUM(part_kind IS NOT NULL) AS configured_kinds,
--        SUM(calculation_params IS NOT NULL) AS configured_params
-- FROM parts;
-- За відсутності інших змін після V45 очікується: 1341,7,7,7,7.
--
-- Не включено: 5130,27970,29500 (площа залежить від конфігурації).
-- Не включено: 29720,33370 (відсутні в наданій актуальній вибірці).
-- QUANTITY задає алгоритм кількості, а не обов'язково одну деталь:
-- кількість підвісів на виріб визначатиме правило комплектації.
-- PERIMETER для термосклейки задає витрату, але не умову її додавання.
-- FRAME_PROFILE читає legacy ProfilWidth у метрах; 0.024 = 24 мм.
-- Порожній JSON {} означає, що алгоритм не потребує додаткових параметрів.
