-- Preserve maintenance COMPLETED=2; newly allocate WAIT_ACCEPTANCE=4.
CREATE TABLE return_request (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 status TINYINT NOT NULL DEFAULT 0,
 submitted_at DATETIME NOT NULL,
 reviewed_at DATETIME NULL,
 reviewer_id BIGINT NULL,
 review_remark VARCHAR(500) NULL,
 UNIQUE KEY uk_return_order(order_id),
 CONSTRAINT fk_return_order FOREIGN KEY(order_id) REFERENCES rental_order(id),
 CONSTRAINT fk_return_user FOREIGN KEY(user_id) REFERENCES user(id),
 CONSTRAINT fk_return_reviewer FOREIGN KEY(reviewer_id) REFERENCES user(id),
 CONSTRAINT ck_return_status CHECK(status IN (0,1,2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE maintenance_ticket
 ADD COLUMN accepted_at DATETIME NULL,
 ADD COLUMN accepted_by BIGINT NULL,
 ADD COLUMN acceptance_remark VARCHAR(500) NULL,
 ADD CONSTRAINT fk_ticket_acceptor FOREIGN KEY(accepted_by) REFERENCES user(id);
