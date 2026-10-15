-- Add courier as a distinct role without changing existing role IDs or permissions.
INSERT INTO roles (name)
SELECT 'ROLE_COURIER'
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'ROLE_COURIER'
);
