ALTER TABLE orders
    CHANGE COLUMN ShipToContact ReceivedDate DATETIME NULL COMMENT 'Дата оприходування до філії';