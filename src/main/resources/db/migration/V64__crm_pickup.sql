ALTER TABLE crm_contacts
 ADD COLUMN pickup_date DATE NULL,
 ADD COLUMN ready_snapshot JSON NULL;
CREATE TABLE crm_order_pickup (
 order_no BIGINT PRIMARY KEY,
 revision BIGINT NOT NULL DEFAULT 0,
 pickup_date DATE NULL,
 last_event_id BIGINT NULL,
 notified_event_id BIGINT NULL,
 FOREIGN KEY(order_no) REFERENCES orders(OrderNo),
 FOREIGN KEY(last_event_id) REFERENCES crm_contacts(id),
 FOREIGN KEY(notified_event_id) REFERENCES crm_contacts(id)
);
