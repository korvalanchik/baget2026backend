-- Add dedicated cashier roles without changing existing role IDs or assignments.
INSERT INTO roles (name)
SELECT 'ROLE_CASHIER'
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'ROLE_CASHIER'
);

INSERT INTO roles (name)
SELECT 'ROLE_SENIOR_CASHIER'
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'ROLE_SENIOR_CASHIER'
);
