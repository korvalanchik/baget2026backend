-- 1. Drop foreign key from items
ALTER TABLE items DROP FOREIGN KEY items_ibfk_1;

-- 2. Alter orders.OrderNo to remove AUTO_INCREMENT
ALTER TABLE orders MODIFY COLUMN OrderNo BIGINT NOT NULL;

-- 3. Re-add the foreign key constraint
ALTER TABLE items
    ADD CONSTRAINT items_ibfk_1
        FOREIGN KEY (OrderNo) REFERENCES orders(OrderNo) ON DELETE CASCADE;
