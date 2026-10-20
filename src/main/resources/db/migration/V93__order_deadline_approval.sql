ALTER TABLE orders
    ADD COLUMN deadline_approval_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN deadline_approved_by_user_id BIGINT NULL,
    ADD COLUMN deadline_approved_at TIMESTAMP(6) NULL,
    ADD CONSTRAINT fk_orders_deadline_approver
        FOREIGN KEY (deadline_approved_by_user_id) REFERENCES users(id),
    ADD CONSTRAINT chk_orders_deadline_approval_status
        CHECK (deadline_approval_status IN ('NOT_REQUIRED', 'PENDING', 'APPROVED'));
