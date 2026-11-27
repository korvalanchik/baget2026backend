CREATE TABLE preliminary_order_invoices (
    order_no BIGINT NOT NULL,
    pdf LONGBLOB NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (order_no),
    CONSTRAINT fk_preliminary_order_invoice_order
        FOREIGN KEY (order_no) REFERENCES orders (OrderNo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
