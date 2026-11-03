-- A TEST source is a real sale receipt used only for controlled fiscalization checks.
-- It remains a CHK document and is included in the shift Z-report when fiscalized.
ALTER TABLE prro_operations
    DROP CHECK chk_prro_operation_source_type;

ALTER TABLE prro_operations
    ADD CONSTRAINT chk_prro_operation_source_type
        CHECK (source_type IN ('PAYMENT', 'TEST', 'SHIFT_OPEN', 'SHIFT_CLOSE', 'SERVICE'));
