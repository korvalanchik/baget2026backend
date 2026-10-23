-- Public identifier of the signing key selected for the active PRRO shift.
-- This is a key/certificate identifier, never private key material.
ALTER TABLE prro_devices
    ADD COLUMN active_signer_key_id VARCHAR(128) NULL AFTER active_shift_key;

-- Persist the signer identity used to prepare each fiscal document.
-- Nullable for rows created before this migration; new operations require it in the service layer.
ALTER TABLE prro_operations
    ADD COLUMN signer_key_id VARCHAR(128) NULL AFTER local_number;
