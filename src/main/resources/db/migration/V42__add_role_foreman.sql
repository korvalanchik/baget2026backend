SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO `roles` (`id`, `name`)
VALUES
    (6, 'ROLE_FOREMAN');  -- production foreman
COMMIT;