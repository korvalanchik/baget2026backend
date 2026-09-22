ALTER TABLE `items`
    ADD COLUMN `ResellerPrice` decimal NULL DEFAULT NULL AFTER `SellPrice`;