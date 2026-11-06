-- G5: measured 100k-order filters/COUNT/sort. Existing semantics and indexes retained.
ALTER TABLE rental_order
 ADD KEY idx_order_owner_state(user_id,deleted,order_status,created_time,id),
 ADD KEY idx_order_global_time(deleted,created_time,id),
 ADD KEY idx_order_state_time(deleted,order_status,created_time,id);
ALTER TABLE maintenance_ticket
 ADD KEY idx_ticket_assignee_time(assignee_id,deleted,created_time,id);
