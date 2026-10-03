-- Immutable ownership of reseller invoices; historical invoices remain unchanged.
CREATE TABLE invoice_commercial_owner (
 invoice_id BIGINT NOT NULL PRIMARY KEY,
 partner_id BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_commercial_invoice FOREIGN KEY(invoice_id) REFERENCES invoices(id),
 CONSTRAINT fk_commercial_partner FOREIGN KEY(partner_id) REFERENCES customer(CustNo)
);
