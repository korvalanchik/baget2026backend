ALTER TABLE prro_operations
    DROP CHECK chk_prro_operation_status;

ALTER TABLE prro_operations
    ADD CONSTRAINT chk_prro_operation_status
        CHECK (status IN (
            'PREPARED',
            'SIGNED',
            'SENDING',
            'FISCALIZED',
            'REJECTED',
            'UNKNOWN_RESULT',
            'CANCELLED'
        ));
