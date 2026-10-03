-- Append-only corrections of reference money owned by reseller companies.
-- Enterprise ledger_entries and customer_transactions are not involved.
CREATE TABLE partner_payment_adjustments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 request_key CHAR(36) NOT NULL UNIQUE,
 partner_id BIGINT NOT NULL,
 customer_id BIGINT NOT NULL,
 branch_no BIGINT NOT NULL,
 order_no BIGINT NULL,
 operation VARCHAR(16) NOT NULL,
 source_payment_id BIGINT NULL UNIQUE,
 cash_delta DECIMAL(18,2) NOT NULL,
 advance_delta DECIMAL(18,2) NOT NULL,
 paid_delta DECIMAL(18,2) NOT NULL,
 actor VARCHAR(255) NOT NULL,
 note VARCHAR(2000) NOT NULL,
 payload JSON NOT NULL,
 result JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(partner_id) REFERENCES customer(CustNo),
 FOREIGN KEY(customer_id) REFERENCES customer(CustNo),
 FOREIGN KEY(branch_no) REFERENCES branches(BranchNo),
 FOREIGN KEY(order_no) REFERENCES orders(OrderNo),
 FOREIGN KEY(source_payment_id) REFERENCES partner_customer_payments(id),
 INDEX partner_adjustment_history(partner_id,customer_id,id),
 INDEX partner_adjustment_order(partner_id,customer_id,order_no),
 CONSTRAINT partner_adjustment_reason CHECK(CHAR_LENGTH(TRIM(note))>0),
 CONSTRAINT partner_adjustment_balance CHECK(cash_delta=advance_delta+paid_delta),
 CONSTRAINT partner_adjustment_kind CHECK(
  (operation='REVERSAL' AND source_payment_id IS NOT NULL AND cash_delta<=0 AND
   ((order_no IS NULL AND paid_delta=0 AND advance_delta<0) OR (order_no IS NOT NULL AND paid_delta<0 AND advance_delta>=0)))
  OR (operation='TRANSFER' AND source_payment_id IS NULL AND order_no IS NOT NULL AND cash_delta=0 AND advance_delta>0 AND paid_delta=-advance_delta)
 )
);

CREATE VIEW partner_customer_movements AS
 SELECT id,'PAYMENT' AS entry_type,request_key,partner_id,customer_id,branch_no,order_no,operation,
        CAST(NULL AS SIGNED) AS source_payment_id,amount AS cash_delta,
        CASE WHEN operation='ADVANCE' THEN amount ELSE -allocation_amount END AS advance_delta,
        CASE WHEN operation='PAYMENT' THEN amount+allocation_amount ELSE 0 END AS paid_delta,
        actor,note,created_at
 FROM partner_customer_payments
 UNION ALL
 SELECT id,'ADJUSTMENT' AS entry_type,request_key,partner_id,customer_id,branch_no,order_no,operation,
        source_payment_id,cash_delta,advance_delta,paid_delta,actor,note,created_at
 FROM partner_payment_adjustments;
