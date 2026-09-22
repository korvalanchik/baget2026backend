-- ----------------------------
-- Records of roles
-- the higher the access level number, the fewer the rights
-- ----------------------------
SET NAMES UTF8MB4;
START TRANSACTION;
INSERT INTO `roles` (`id`, `name`)
VALUES
    (1, 'ROLE_ADMIN'),
    (2, 'ROLE_COUNTER'),  -- accountant
    (3, 'ROLE_USER'),  -- buyer
    (4, 'ROLE_SELLER'),  -- ordinary seller
    (5, 'ROLE_LEVEL2');  -- privileged seller
COMMIT;