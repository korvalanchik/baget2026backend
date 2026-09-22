-- Вставляємо користувача адміністратора
ALTER TABLE users AUTO_INCREMENT = 1;
INSERT INTO users (username, password, email)
VALUES ('admin', '$2a$12$Ij5Qnel4g7H6f0ILiWyKHeXZHKFOnoYblOnUc52Qqkgny8oVwBruW', '333@mppu.org.ua');

-- Припустимо, що id новоствореного користувача - 1
-- Вставляємо роль адміністратора (припустимо, що роль "ROLE_ADMIN" має id = 1)
INSERT INTO user_roles (user_id, role_id)
VALUES (1, 1);