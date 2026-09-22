CREATE TABLE temp_order_counter (
    id INT PRIMARY KEY,
    last_temp_order_no BIGINT NOT NULL
);

-- Вставка початкового значення
INSERT INTO temp_order_counter (id, last_temp_order_no) VALUES (1, 100);

DELIMITER $$

CREATE TRIGGER update_temp_order_counter
    BEFORE UPDATE ON temp_order_counter
    FOR EACH ROW
BEGIN
    -- Інкрементуємо значення лише поля last_temp_order_no
    SET NEW.last_temp_order_no = OLD.last_temp_order_no + 1;
END$$

DELIMITER ;
