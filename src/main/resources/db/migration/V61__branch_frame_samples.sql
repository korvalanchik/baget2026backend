-- Samples are presence/status records, never physical metres or inventory movements.
CREATE TABLE branch_frame_samples (
 part_no BIGINT NOT NULL,
 branch_no BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL,
 revision BIGINT NOT NULL,
 actor VARCHAR(255) NOT NULL,
 note VARCHAR(1000) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(part_no,branch_no),
 FOREIGN KEY(part_no) REFERENCES parts(PartNo),
 FOREIGN KEY(branch_no) REFERENCES branches(BranchNo),
 CHECK(status IN ('ACTIVE','PAUSED','REMOVED'))
);
CREATE TABLE frame_purchase_policy (
 part_no BIGINT PRIMARY KEY,
 status VARCHAR(20) NOT NULL,
 revision BIGINT NOT NULL,
 actor VARCHAR(255) NOT NULL,
 note VARCHAR(1000) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(part_no) REFERENCES parts(PartNo),
 CHECK(status IN ('ALLOWED','DISCONTINUED','BLOCKED'))
);
CREATE TABLE frame_assortment_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 part_no BIGINT NOT NULL,
 branch_no BIGINT NULL,
 previous_status VARCHAR(20) NULL,
 status VARCHAR(20) NOT NULL,
 revision BIGINT NOT NULL,
 actor VARCHAR(255) NOT NULL,
 note VARCHAR(1000) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(part_no) REFERENCES parts(PartNo),
 FOREIGN KEY(branch_no) REFERENCES branches(BranchNo)
);
-- Existing samples cannot be inferred from stock or past orders. Enter actual branch sets in the UI.
