CREATE TABLE branches (
    BranchNo BIGINT AUTO_INCREMENT PRIMARY KEY,
    BranchName VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO branches (BranchName) VALUES
    ('Ювілейний'),
    ('Шрек'),
    ('Намив'),
    ('Кодак Бог'),
    ('Очаків'),
    ('Соборна'),
    ('Парапет');

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_branches
        FOREIGN KEY (BranchNo) REFERENCES branches(BranchNo);
