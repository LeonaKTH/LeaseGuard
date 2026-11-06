-- Additive G2 migration. V1/V2 are immutable; migration preflight checks existing rows.
ALTER TABLE drone ADD CONSTRAINT ck_drone_stock_nonnegative CHECK(stock>=0);
ALTER TABLE rental_order
 ADD COLUMN version INT NOT NULL DEFAULT 0,
 ADD COLUMN inventory_held TINYINT NOT NULL DEFAULT 1,
 ADD CONSTRAINT ck_order_state CHECK(order_status IN(0,1,2,3,4,5,6)),
 ADD CONSTRAINT ck_inventory_held CHECK(inventory_held IN(0,1)),
 ADD CONSTRAINT fk_order_user FOREIGN KEY(user_id) REFERENCES user(id),
 ADD CONSTRAINT fk_order_drone FOREIGN KEY(drone_id) REFERENCES drone(id);
-- Existing terminal G1 orders have already released their allocation. No business status is changed.
UPDATE rental_order SET inventory_held=0, updated_time=updated_time WHERE order_status IN(4,5,6);
ALTER TABLE return_request ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE fault_report ADD COLUMN version INT NOT NULL DEFAULT 0,
 ADD CONSTRAINT fk_fault_order FOREIGN KEY(order_id) REFERENCES rental_order(id),
 ADD CONSTRAINT fk_fault_drone FOREIGN KEY(drone_id) REFERENCES drone(id);
ALTER TABLE maintenance_ticket ADD COLUMN version INT NOT NULL DEFAULT 0,
 ADD UNIQUE KEY uk_ticket_fault(fault_report_id),
 ADD KEY idx_ticket_drone_status(drone_id,status,id),
 ADD CONSTRAINT fk_ticket_fault FOREIGN KEY(fault_report_id) REFERENCES fault_report(id),
 ADD CONSTRAINT fk_ticket_drone FOREIGN KEY(drone_id) REFERENCES drone(id),
 ADD CONSTRAINT ck_ticket_state CHECK(status IN(0,1,2,3,4));
ALTER TABLE payment ADD UNIQUE KEY uk_payment_order(order_id),
 ADD CONSTRAINT fk_payment_order FOREIGN KEY(order_id) REFERENCES rental_order(id);
CREATE TABLE command_dedup (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 actor_id BIGINT NOT NULL,
 operation VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 resource_id BIGINT NULL,
 response_json JSON NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_command_key(actor_id,operation,request_key),
 CONSTRAINT fk_command_actor FOREIGN KEY(actor_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE business_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 aggregate_type VARCHAR(32) NOT NULL,
 aggregate_id BIGINT NOT NULL,
 command_id BIGINT NOT NULL,
 seq_no INT NOT NULL,
 event_type VARCHAR(48) NOT NULL,
 from_status INT NULL,
 to_status INT NULL,
 actor_id BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_command_event(command_id,seq_no),
 KEY idx_event_resource(aggregate_type,aggregate_id,id),
 CONSTRAINT fk_event_command FOREIGN KEY(command_id) REFERENCES command_dedup(id),
 CONSTRAINT fk_event_actor FOREIGN KEY(actor_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE drone_stock_log
 ADD COLUMN command_id BIGINT NULL,
 ADD COLUMN event_type VARCHAR(24) NULL,
 ADD COLUMN inventory_effect VARCHAR(12) NULL,
 ADD UNIQUE KEY uk_stock_command(command_id,event_type),
 ADD UNIQUE KEY uk_stock_order_effect(related_order_id,inventory_effect),
 ADD CONSTRAINT fk_stock_command FOREIGN KEY(command_id) REFERENCES command_dedup(id),
 ADD CONSTRAINT fk_stock_order FOREIGN KEY(related_order_id) REFERENCES rental_order(id);
UPDATE drone_stock_log SET inventory_effect=CASE change_type WHEN 2 THEN 'ALLOCATE' WHEN 3 THEN 'RELEASE' ELSE NULL END
 WHERE related_order_id IS NOT NULL AND change_type IN(2,3);
