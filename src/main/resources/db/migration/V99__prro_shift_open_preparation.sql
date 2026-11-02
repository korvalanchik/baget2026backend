ALTER TABLE prro_operations
    ADD COLUMN signing_input MEDIUMBLOB NULL AFTER signed_document_hash,
    ADD COLUMN signing_input_hash CHAR(64) NULL AFTER signing_input,
    ADD COLUMN previous_document_hash CHAR(64) NULL AFTER signing_input_hash,
    ADD COLUMN dps_date_time BIGINT NULL AFTER previous_document_hash;
