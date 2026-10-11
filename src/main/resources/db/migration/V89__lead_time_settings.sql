CREATE TABLE lead_time_settings (
 id INT PRIMARY KEY,
 min_days INT NOT NULL,
 normal_days INT NOT NULL,
 shortage_days INT NOT NULL,
 upcoming_days INT NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 updated_by VARCHAR(255) NULL,
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CHECK(id=1), CHECK(min_days>=1 AND min_days<=normal_days),
 CHECK(normal_days<=shortage_days AND shortage_days<=365),
 CHECK(upcoming_days>=1 AND upcoming_days<=30)
);
INSERT INTO lead_time_settings(id,min_days,normal_days,shortage_days,upcoming_days) VALUES(1,5,7,10,3);
