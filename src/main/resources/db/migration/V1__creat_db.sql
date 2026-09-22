SET NAMES UTF8MB4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for Buh_account
-- ----------------------------
-- DROP TABLE IF EXISTS `Buh_account`;
CREATE TABLE `Buh_account`  (
                                `accountNo` int NOT NULL AUTO_INCREMENT,
                                `account` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                PRIMARY KEY (`accountNo`) USING BTREE
) ENGINE = MyISAM AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for Buh
-- ----------------------------
-- DROP TABLE IF EXISTS `Buh`;
CREATE TABLE `Buh`  (
                        `count` int NOT NULL AUTO_INCREMENT,
                        `date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `account` int NOT NULL,
                        `category` int NOT NULL,
                        `total` int NOT NULL,
                        `currency` int NOT NULL,
                        `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                        `transfer` int NULL DEFAULT NULL,
                        PRIMARY KEY (`count`) USING BTREE,
                        INDEX `count`(`count`) USING BTREE
) ENGINE = MyISAM AUTO_INCREMENT = 7623 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for Buh_category
-- ----------------------------
-- DROP TABLE IF EXISTS `Buh_category`;
CREATE TABLE `Buh_category`  (
                                 `categoryNo` int NOT NULL AUTO_INCREMENT,
                                 `category` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                 PRIMARY KEY (`categoryNo`) USING BTREE,
                                 INDEX `categoryNo`(`categoryNo`) USING BTREE
) ENGINE = MyISAM AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for Buh_currency
-- ----------------------------
-- DROP TABLE IF EXISTS `Buh_currency`;
CREATE TABLE `Buh_currency`  (
                                 `currencyNo` int NOT NULL,
                                 `currency` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                 UNIQUE INDEX `currencyNo`(`currencyNo`) USING BTREE
) ENGINE = MyISAM AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for TradeLevel
-- ----------------------------
-- DROP TABLE IF EXISTS `TradeLevel`;
CREATE TABLE `TradeLevel`  (
                               `NoLevel` int NOT NULL DEFAULT 0,
                               `Level` varchar(22) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                               PRIMARY KEY (`NoLevel`) USING BTREE
) ENGINE = MyISAM AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for customer
-- ----------------------------
-- DROP TABLE IF EXISTS `customer`;
CREATE TABLE `customer`  (
                             `CustNo` bigint NOT NULL AUTO_INCREMENT,
                             `Company` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '',
                             `Addr1` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '',
                             `Comment` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '',
                             `City` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '',
                             `State` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '',
                             `Zip` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '',
                             `Country` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '',
                             `Phone` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '',
                             `Mobile` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '',
                             `TaxRate` float NULL DEFAULT 0,
                             `Contact` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '',
                             `LastInvoiceDate` datetime NULL DEFAULT NULL,
                             `PriceLevel` float NULL DEFAULT 1,
                             PRIMARY KEY (`CustNo`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for employee
-- ----------------------------
-- DROP TABLE IF EXISTS `employee`;
CREATE TABLE `employee`  (
                             `EmpNo` int NOT NULL AUTO_INCREMENT,
                             `LastName` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                             `FirstName` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                             `PhoneExt` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                             `HireDate` datetime NULL DEFAULT NULL,
                             `Salary` float NULL DEFAULT NULL,
                             PRIMARY KEY (`EmpNo`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for items
-- ----------------------------
-- DROP TABLE IF EXISTS `items`;
CREATE TABLE `items`  (
                          `OrderNo` bigint NOT NULL,
                          `ItemNo` bigint NOT NULL,
                          `PartNo` bigint NULL DEFAULT NULL,
                          `ProfilWidth` float NULL DEFAULT NULL,
                          `Width` float NULL DEFAULT NULL,
                          `Height` float NULL DEFAULT NULL,
                          `Qty` float NULL DEFAULT NULL,
                          `Quantity` float NULL DEFAULT NULL,
                          `SellPrice` decimal NULL DEFAULT NULL,
                          `Discount` float NULL DEFAULT NULL,
                          `OnHand` float NULL DEFAULT NULL,
                          `Cost` decimal NULL DEFAULT NULL,
                          PRIMARY KEY (`OrderNo`, `ItemNo`) USING BTREE,
                          FOREIGN KEY (`OrderNo`) REFERENCES `orders`(`OrderNo`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for orders
-- ----------------------------
-- DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders`  (
                           `OrderNo` bigint NOT NULL AUTO_INCREMENT,
                           `CustNo` bigint NULL DEFAULT NULL,
                           `FactNo` bigint NULL DEFAULT 0,
                           `SaleDate` datetime NULL DEFAULT NULL,
                           `ShipDate` datetime NULL DEFAULT NULL,
                           `EmpNo` bigint NULL DEFAULT NULL,
                           `ShipToContact` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                           `ShipToAddr1` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                           `ShipToAddr2` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                           `ShipToCity` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                           `ShipToState` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                           `ShipToZip` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                           `ShipToCountry` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                           `ShipToPhone` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                           `ShipVIA` varchar(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                           `PO` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                           `Terms` varchar(6) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                           `PaymentMethod` varchar(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                           `ItemsTotal` float NULL DEFAULT NULL,
                           `TaxRate` float NULL DEFAULT NULL,
                           `Freight` float NULL DEFAULT NULL,
                           `AmountPaid` float NULL DEFAULT NULL,
                           `AmountDueN` float NULL DEFAULT NULL,
                           `Income` float NULL DEFAULT NULL,
                           `PriceLevel` int NULL DEFAULT NULL,
                           `StatusOrder` int NULL DEFAULT 3,
                           `RahFacNo` bigint NULL DEFAULT NULL,
                           PRIMARY KEY (`OrderNo`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for parts
-- ----------------------------
-- DROP TABLE IF EXISTS `parts`;
CREATE TABLE `parts` (
                         `PartNo` bigint NOT NULL AUTO_INCREMENT,
                         `VendorNo` bigint NULL DEFAULT NULL,
                         `Description` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                         `ProfilWidth` float NULL DEFAULT NULL,
                         `InQuality` float NULL DEFAULT NULL,
                         `OnHand` float NULL DEFAULT NULL,
                         `OnOrder` float NULL DEFAULT NULL,
                         `Cost` float NULL DEFAULT NULL,
                         `ListPrice` float NULL DEFAULT NULL,
                         `ListPrice_1` float NULL DEFAULT NULL,
                         `ListPrice_2` float NULL DEFAULT NULL,
                         `NoPercent` int NULL DEFAULT NULL,
                         `ListPrice_3` float NULL DEFAULT NULL,
                         `Version` bigint DEFAULT 0,
                         PRIMARY KEY (`partNo`),
                         CONSTRAINT `fk_vendor` FOREIGN KEY (`VendorNo`) REFERENCES `vendors`(`VendorNo`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=Dynamic;

-- ----------------------------
-- Table structure for qualility
-- ----------------------------
-- DROP TABLE IF EXISTS `qualility`;
CREATE TABLE `qualility`  (
                              `KeyType` bigint NOT NULL DEFAULT 0,
                              `DescriptQuolity` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                              PRIMARY KEY (`KeyType`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for rahfact
-- ----------------------------
-- DROP TABLE IF EXISTS `rahfact`;
CREATE TABLE `rahfact`  (
                            `NumRahFact` bigint NOT NULL,
                            `NumOrders` bigint NULL DEFAULT NULL,
                            `SumOrders` float NULL DEFAULT NULL,
                            PRIMARY KEY (`NumRahFact`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = cp866 COLLATE = cp866_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for status
-- ----------------------------
-- DROP TABLE IF EXISTS `status`;
CREATE TABLE `status`  (
                           `StatusNo` int NOT NULL,
                           `StatusName` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                           PRIMARY KEY (`StatusNo`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for vendors
-- ----------------------------
-- DROP TABLE IF EXISTS `vendors`;
CREATE TABLE `vendors`  (
                            `VendorNo` bigint NOT NULL AUTO_INCREMENT,
                            `VendorName` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                            `Address1` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                            `Address2` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                            `City` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                            `State` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                            `Zip` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                            `Country` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                            `Phone` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                            `FAX` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                            `Preferred` int NULL DEFAULT NULL,
                            PRIMARY KEY (`VendorNo`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Procedure structure for Select Kodaks
-- ----------------------------
DROP PROCEDURE IF EXISTS `Select Kodaks`;
delimiter ;;
CREATE PROCEDURE `Select Kodaks`()
SELECT CustNo, OrderNo FROM orders
WHERE CustNo=10890
;;
delimiter ;

SET FOREIGN_KEY_CHECKS = 1;
