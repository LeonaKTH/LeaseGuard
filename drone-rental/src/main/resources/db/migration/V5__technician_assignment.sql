-- Historical names are not identities. No backfill of assignees or business states.
ALTER TABLE `user` ADD CONSTRAINT ck_user_role CHECK(role IN(0,1,2));
ALTER TABLE maintenance_ticket
 ADD COLUMN assignee_id BIGINT NULL,
 ADD COLUMN assigned_at DATETIME NULL,
 ADD CONSTRAINT fk_ticket_assignee FOREIGN KEY(assignee_id) REFERENCES `user`(id),
 ADD KEY idx_ticket_assignee(assignee_id,status,created_time,id);
ALTER TABLE business_event ADD COLUMN details JSON NULL;
