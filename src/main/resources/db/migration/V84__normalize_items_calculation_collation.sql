ALTER TABLE items
    MODIFY COLUMN calculation_unit
        VARCHAR(20)
            CHARACTER SET utf8mb4
                COLLATE utf8mb4_unicode_ci
        DEFAULT NULL,
    MODIFY COLUMN calculation_role
        VARCHAR(32)
            CHARACTER SET utf8mb4
                COLLATE utf8mb4_unicode_ci
        DEFAULT NULL;