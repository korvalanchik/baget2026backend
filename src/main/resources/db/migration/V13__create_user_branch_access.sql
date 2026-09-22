CREATE TABLE user_branch_access (
    user_id BIGINT NOT NULL,
    branch_no BIGINT NOT NULL,
    PRIMARY KEY (user_id, branch_no),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_no) REFERENCES branches(BranchNo)  ON DELETE CASCADE
);

INSERT INTO user_branch_access (user_id, branch_no)
    VALUES (5, 1),
           (15, 1),
           (2, 7),
           (3,1);
