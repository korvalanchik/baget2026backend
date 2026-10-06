CREATE TABLE opening_material_cost_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 request_key VARCHAR(36) NOT NULL UNIQUE,
 part_no BIGINT NOT NULL,
 actor VARCHAR(100) NOT NULL,
 request_json JSON NOT NULL,
 snapshot JSON NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX opening_cost_part(part_no,id)
);
