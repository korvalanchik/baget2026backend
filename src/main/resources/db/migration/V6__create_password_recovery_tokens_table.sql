CREATE TABLE password_recovery_tokens (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    token VARCHAR(255) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    expiry_date DATETIME NOT NULL,
                    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
