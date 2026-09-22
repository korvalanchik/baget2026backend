alter table customer_transactions
    add column branch_id bigint not null comment 'Branch responsible for transaction';

UPDATE customer_transactions ct
    JOIN orders o ON ct.order_no = o.OrderNo
SET ct.branch_id = o.BranchNo
WHERE ct.order_no IS NOT NULL;

# alter table customer_transactions
#     add constraint fk_customer_tx_branch
#         foreign key (branch_id) references branches (BranchNo);
#
# create index idx_customer_tx_branch
#     on customer_transactions (branch_id);
