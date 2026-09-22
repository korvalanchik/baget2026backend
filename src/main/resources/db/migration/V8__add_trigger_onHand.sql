CREATE TRIGGER update_on_hand_after_delete
    AFTER DELETE ON items
    FOR EACH ROW
BEGIN
    UPDATE parts
    SET onHand = onHand + OLD.qty
    WHERE partNo = OLD.partNo;
END;

CREATE TRIGGER update_on_hand_after_update
    AFTER UPDATE ON items
    FOR EACH ROW
BEGIN
    -- Спочатку повертаємо стару кількість в onHand
    UPDATE parts
    SET onHand = onHand + OLD.qty
    WHERE partNo = OLD.partNo;

    -- Потім знімаємо нову кількість з onHand
    UPDATE parts
    SET onHand = onHand - NEW.qty
    WHERE partNo = NEW.partNo;
END;

CREATE TRIGGER update_on_hand_after_insert
    AFTER INSERT ON items
    FOR EACH ROW
BEGIN
    UPDATE parts
    SET onHand = onHand - NEW.qty
    WHERE partNo = NEW.partNo;
END;
