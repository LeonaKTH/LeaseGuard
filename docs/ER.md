# 主要表与约束

```mermaid
erDiagram
 USER ||--o{ RENTAL_ORDER : "FK user_id"
 DRONE ||--o{ RENTAL_ORDER : "FK drone_id"
 RENTAL_ORDER ||--o| RETURN_REQUEST : "FK + unique order_id"
 RENTAL_ORDER ||--o| PAYMENT : "FK + unique order_id"
 RENTAL_ORDER ||--o{ FAULT_REPORT : "FK order_id"
 DRONE ||--o{ FAULT_REPORT : "FK drone_id"
 FAULT_REPORT ||--o| MAINTENANCE_TICKET : "FK + unique fault_report_id"
 DRONE ||--o{ MAINTENANCE_TICKET : "FK drone_id"
 USER ||--o{ MAINTENANCE_TICKET : "FK assignee_id / accepted_by"
 USER ||--o{ COMMAND_DEDUP : "FK actor_id"
 COMMAND_DEDUP ||--o{ BUSINESS_EVENT : "FK command_id"
 USER ||--o{ BUSINESS_EVENT : "FK actor_id"
 COMMAND_DEDUP ||--o{ DRONE_STOCK_LOG : "FK command_id"
 RENTAL_ORDER ||--o{ DRONE_STOCK_LOG : "FK related_order_id"
 RENTAL_ORDER ||--o| JOB_TASK : "FK + unique order/type"
 COMMAND_DEDUP ||--o{ JOB_TASK : "FK created_command_id"
 RENTAL_ORDER {
  bigint id PK
  bigint user_id FK
  bigint drone_id FK
  tinyint order_status
  tinyint inventory_held
  int version
  decimal total_amount
 }
 MAINTENANCE_TICKET {
  bigint id PK
  bigint fault_report_id FK
  bigint assignee_id FK
  int status
  int version
  datetime assigned_at
  datetime accepted_at
 }
 COMMAND_DEDUP {
  bigint id PK
  bigint actor_id FK
  string operation
  string request_key
  string request_hash
  json response_json
 }
 JOB_TASK {
  bigint id PK
  bigint order_id FK
  string business_key UK
  string status
  string claim_token
  datetime lease_until
  int attempts
 }
```

图中FK均对应正式迁移；未列出的旧字段/表不意味着已补全所有外键。资质、空域、评论、信用记录等上游表保留，不把逻辑关联宣传成数据库外键。

- `command_dedup`唯一(actor_id,operation,request_key)，请求参数不同不能复用Key。
- 库存流水唯一(related_order_id,inventory_effect)，确保每单分配/释放各至多一次；无关联单的管理调整不冒充订单释放。
- `business_event`唯一(command_id,seq_no)，事件与命令/库存同事务。
- CHECK限制非负库存、held∈{0,1}、合法状态/角色及不可登录SYSTEM。
- V6 owner/state/global-time/assignee-time组合索引支持实测分页/COUNT；COUNT与深OFFSET仍有扫描成本，不声称复杂查询已全部最优。
