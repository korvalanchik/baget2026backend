alter table customer_transactions
    add constraint fk_customer_tx_branch
        foreign key (branch_id) references branches (BranchNo);

create index idx_customer_tx_branch
    on customer_transactions (branch_id);