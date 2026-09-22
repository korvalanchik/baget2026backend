CREATE TABLE company_details (
                                 id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                 full_name VARCHAR(255),
                                 initials VARCHAR(255),
                                 edrpou VARCHAR(20),
                                 phone VARCHAR(50),
                                 bank_account VARCHAR(255),
                                 ipn VARCHAR(50),
                                 address VARCHAR(255),
                                 comment VARCHAR(255),
                                 work_title VARCHAR(255),
                                 default_recipient VARCHAR(255)
);
