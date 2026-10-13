CREATE TABLE backup_settings (
 id INT PRIMARY KEY,
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 frequency VARCHAR(10) NOT NULL DEFAULT 'DAILY',
 weekday INT NOT NULL DEFAULT 1,
 local_time CHAR(5) NOT NULL DEFAULT '03:00',
 keep_count INT NOT NULL DEFAULT 14,
 revision BIGINT NOT NULL DEFAULT 0,
 schedule_from DATETIME(6) NOT NULL,
 installation_id CHAR(36) NOT NULL,
 checked_at DATETIME(6) NULL,
 storage_status VARCHAR(20) NOT NULL DEFAULT 'NOT_CONFIGURED',
 storage_message VARCHAR(250) NULL,
 folder_id VARCHAR(150) NULL,
 updated_by VARCHAR(150) NULL,
 CONSTRAINT backup_frequency CHECK (frequency IN ('DAILY','WEEKLY')),
 CONSTRAINT backup_weekday CHECK (weekday BETWEEN 1 AND 7),
 CONSTRAINT backup_keep CHECK (keep_count BETWEEN 2 AND 365)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
INSERT INTO backup_settings(id,schedule_from,installation_id) VALUES(1,UTC_TIMESTAMP(6),UUID());
CREATE TABLE backup_runs (
 id CHAR(36) PRIMARY KEY,
 request_key VARCHAR(100) NOT NULL UNIQUE,
 kind VARCHAR(12) NOT NULL,
 actor VARCHAR(150) NOT NULL,
 state VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
 created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
 started_at DATETIME(6) NULL,
 finished_at DATETIME(6) NULL,
 file_id VARCHAR(150) NULL,
 file_name VARCHAR(200) NULL,
 bytes BIGINT NULL,
 sha256 CHAR(64) NULL,
 md5 CHAR(32) NULL,
 message VARCHAR(250) NULL,
 INDEX backup_queue(state,created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
