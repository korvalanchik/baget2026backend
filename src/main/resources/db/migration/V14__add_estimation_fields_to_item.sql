ALTER TABLE `items`
    ADD COLUMN `EstimationWidth` FLOAT NULL DEFAULT NULL AFTER `ProfilWidth`,
    ADD COLUMN `EstimationHeight` FLOAT NULL DEFAULT NULL AFTER `EstimationWidth`;