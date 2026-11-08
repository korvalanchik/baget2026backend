-- The DPS endpoint is selected once per Baget deployment. A PRRO device
-- stores its real fiscal identity and branch binding, not a separate API mode.
ALTER TABLE prro_devices
    DROP CHECK chk_prro_devices_environment;

ALTER TABLE prro_devices
    DROP COLUMN environment;
