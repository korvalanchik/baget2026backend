ALTER TABLE orders
    CHANGE COLUMN ShipToAddr1 ClientReceivedDate DATETIME NULL COMMENT 'Дата отримання клієнтом';