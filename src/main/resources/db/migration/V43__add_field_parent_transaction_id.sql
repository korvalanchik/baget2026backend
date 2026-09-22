-- 1️⃣ Додаємо колонку parent_transaction_id
ALTER TABLE customer_transactions
    ADD COLUMN parent_transaction_id BIGINT NULL COMMENT 'Parent transaction, e.g., PAYMENT for ALLOCATION';

-- 2️⃣ Додаємо зовнішній ключ на саму таблицю
ALTER TABLE customer_transactions
    ADD CONSTRAINT fk_customer_tx_parent
        FOREIGN KEY (parent_transaction_id)
            REFERENCES customer_transactions(id)
            ON DELETE SET NULL
            ON UPDATE CASCADE;

-- 3️⃣ Додаємо індекс для швидкого пошуку по parent_transaction_id
CREATE INDEX idx_customer_tx_parent
    ON customer_transactions (parent_transaction_id);