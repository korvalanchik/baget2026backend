CREATE TABLE cost_update_settings (
 id INT PRIMARY KEY, rate DECIMAL(7,2) NOT NULL, revision BIGINT NOT NULL DEFAULT 0
);
INSERT INTO cost_update_settings(id,rate) VALUES(1,0);
CREATE TABLE cost_update_rates (
 part_no BIGINT PRIMARY KEY, rate DECIMAL(7,2) NOT NULL
);
CREATE TABLE cost_update_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 request_key VARCHAR(36) NOT NULL UNIQUE,
 action VARCHAR(24) NOT NULL,
 actor VARCHAR(100) NOT NULL,
 request_json JSON NOT NULL,
 snapshot JSON NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
