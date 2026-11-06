-- No historical order backfill: only new CREATE_ORDER commands enqueue tasks.
ALTER TABLE `user`
 ADD COLUMN principal_kind VARCHAR(8) NOT NULL DEFAULT 'USER',
 ADD CONSTRAINT ck_principal_kind CHECK(principal_kind IN('USER','SYSTEM')),
 ADD CONSTRAINT ck_system_non_login CHECK(principal_kind<>'SYSTEM' OR
   (role=0 AND status=0 AND deleted=0 AND password='!SYSTEM_NON_LOGIN!'));
INSERT INTO `user`(username,password,nickname,role,status,principal_kind)
 VALUES ('__leaseguard_timeout__','!SYSTEM_NON_LOGIN!','订单超时系统主体',0,0,'SYSTEM');
CREATE TABLE job_task (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 business_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 order_id BIGINT NOT NULL,
 type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
 due_at DATETIME(6) NOT NULL,
 available_at DATETIME(6) NOT NULL,
 lease_until DATETIME(6) NULL,
 claim_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 attempts INT NOT NULL DEFAULT 0,
 max_attempts INT NOT NULL,
 last_error VARCHAR(80) NULL,
 result VARCHAR(32) NULL,
 created_command_id BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 completed_at DATETIME(6) NULL,
 UNIQUE KEY uk_job_business(business_key),
 UNIQUE KEY uk_job_order_type(order_id,type),
 KEY idx_job_available(status,available_at,id),
 KEY idx_job_lease(status,lease_until,id),
 CONSTRAINT fk_job_order FOREIGN KEY(order_id) REFERENCES rental_order(id),
 CONSTRAINT fk_job_command FOREIGN KEY(created_command_id) REFERENCES command_dedup(id),
 CONSTRAINT ck_job_type CHECK(type='ORDER_TIMEOUT'),
 CONSTRAINT ck_job_status CHECK(status IN('PENDING','CLAIMED','DONE','FAILED')),
 CONSTRAINT ck_job_attempts CHECK(attempts>=0 AND max_attempts BETWEEN 1 AND 10),
 CONSTRAINT ck_job_claim CHECK(status<>'CLAIMED' OR (claim_token IS NOT NULL AND lease_until IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
