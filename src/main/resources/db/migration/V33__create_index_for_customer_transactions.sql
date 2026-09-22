CREATE UNIQUE INDEX ux_invoice_per_order
    ON customer_transactions(order_no, type, active);