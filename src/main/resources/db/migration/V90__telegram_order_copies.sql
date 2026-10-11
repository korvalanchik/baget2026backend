CREATE TABLE telegram_copy_settings (
 id INT PRIMARY KEY, enabled BOOLEAN NOT NULL DEFAULT FALSE, revision BIGINT NOT NULL DEFAULT 0
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO telegram_copy_settings(id,enabled) VALUES(1,FALSE);
CREATE TABLE telegram_partner_recipients (
 partner_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 PRIMARY KEY(partner_id,user_id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE telegram_order_outbox (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no BIGINT NOT NULL,
 chat_id BIGINT NOT NULL, recipient VARCHAR(255) NOT NULL, part_no INT NOT NULL, body TEXT NOT NULL,
 state VARCHAR(20) NOT NULL DEFAULT 'PENDING', attempts INT NOT NULL DEFAULT 0,
 next_attempt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 last_error VARCHAR(200), message_id BIGINT,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), sent_at DATETIME(6),
 UNIQUE KEY uq_telegram_order_part(order_no,chat_id,part_no),
 KEY ix_telegram_due(state,next_attempt)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
