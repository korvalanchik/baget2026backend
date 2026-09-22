-- ПРОПОЗИЦІЯ ДЛЯ ПЕРЕВІРКИ. Не виконувалась на базі.
-- В архіві Baget_2024 остання міграція V44. Перед додаванням файла
-- перевірте фактичну історію Flyway і вільний номер міграції.
-- Не запускайте цей SQL вручну, а потім повторно через Flyway.
-- Спершу резервна копія та перевірка на окремій тестовій базі.
-- Не запускайте застосунок із цим файлом та робочим datasource:
-- у поточному application.properties Flyway увімкнений.
--
-- ПЕРЕВІРКИ ДО МІГРАЦІЇ (виконати окремо, знявши --):
-- SELECT DATABASE() AS current_database, VERSION() AS mysql_version;
-- SHOW CREATE TABLE parts;
-- SELECT installed_rank, version, description, success
-- FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
-- FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'parts';
-- SELECT InQuality, COUNT(*) AS part_count
-- FROM parts GROUP BY InQuality ORDER BY InQuality;
-- SELECT PartNo, Description, ProfilWidth, InQuality
-- FROM parts WHERE PartNo IN
-- (29720,29500,29340,28900,28260,27970,27780,27580,28540,27760,5130,33370);
--
-- Якщо будь-яке нове поле вже існує, STOP: з'ясувати історію змін.
-- Якщо V45 вже зайнята, вибрати наступний вільний номер ДО застосування.
--
-- ЄДИНА ЗМІНА: чотири нові поля без модифікації старих даних.
-- NULL означає, що матеріал ще не налаштований для нового калькулятора.
-- Новий сервіс повинен відхиляти неналаштовані позиції, а не здогадуватися
-- про алгоритм. Старий сервіс продовжує використовувати старі поля.
-- ALGORITHM=INSTANT явно забороняє непомітний перехід на копіювання таблиці.
-- Якщо сервер/таблиця не підтримує його, STOP: не прибирати автоматично.
-- ALTER TABLE все одно потребує metadata lock; плануйте вікно змін.
ALTER TABLE `parts`
    ADD COLUMN `unit_type` VARCHAR(20) NULL DEFAULT NULL
        COMMENT 'METER / SQUARE_METER / PIECE; NULL = not configured',
    ADD COLUMN `calculation_method` VARCHAR(40) NULL DEFAULT NULL
        COMMENT 'Consumption algorithm code; NULL = not configured',
    ADD COLUMN `part_kind` VARCHAR(16) NULL DEFAULT NULL
        COMMENT 'MATERIAL / WORK; NULL = not configured',
    ADD COLUMN `calculation_params` JSON NULL
        COMMENT 'Typed algorithm parameters; dimensions in mm',
    ALGORITHM=INSTANT;

-- ПЕРЕВІРКИ ПІСЛЯ МІГРАЦІЇ (виконати окремо, знявши --):
-- SHOW CREATE TABLE parts;
-- SELECT COUNT(*) AS total_parts,
--        SUM(unit_type IS NOT NULL) AS configured_units,
--        SUM(calculation_method IS NOT NULL) AS configured_methods,
--        SUM(part_kind IS NOT NULL) AS configured_kinds,
--        SUM(calculation_params IS NOT NULL) AS configured_params
-- FROM parts;
-- Очікування одразу після цієї міграції: всі configured_* = 0.
-- Також перевірити старі API списку/створення/редагування матеріалів
-- і розрахунок старого замовлення на тестовій базі.
--
-- КОНТРАКТ ДЛЯ НАСТУПНОГО КРОКУ (не backfill):
-- UnitType: METER, SQUARE_METER, PIECE.
-- PartKind: MATERIAL, WORK.
-- CalculationMethod: AREA, AREA_WITH_MARGIN, PERIMETER, FRAME_PROFILE,
--                    WIDTH_PLUS_ALLOWANCE, QUANTITY, FIXED.
-- Сумісність алгоритму, одиниці та JSON перевірятиметься Java-сервісом.
-- Для чисел/розрахунків Java використовуватиме BigDecimal.
--
-- ProfilWidth НЕ конвертувати і НЕ перейменовувати: legacy-значення у метрах.
-- PartsService.fetchProfileParts вже виконує Math.round(ProfilWidth * 1000).
-- Точний порядок округлення зафіксуємо тестом перед FRAME_PROFILE.
-- InQuality=2 НЕ означає автоматично FRAME_PROFILE:
-- підрамник і термосклейка -> PERIMETER, шнур -> WIDTH_PLUS_ALLOWANCE.
-- Припуски полотна 80/100 мм залежать від конфігурації виробу, а не лише PartNo.
-- Параметри правил не приймати як довільну формулу з frontend.
--
-- Не додаємо зв'язки в Items і не змінюємо облік складу на цьому етапі.
-- Правила комплектації будуть Java-кодом, прив'язки допоміжних PartNo
-- визначимо окремо після перевірки актуальних матеріалів.
--
-- Якщо нову функцію вирішено відкласти: залишити nullable-поля,
-- користуватися старим кодом. Не видаляти застосовану міграцію/її історію.
-- Це НЕ транзакційний rollback DDL; SQL для видалення полів навмисно відсутній.
