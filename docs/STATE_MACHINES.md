# 核心状态与不变量

```mermaid
stateDiagram-v2
 [*] --> UNPAID_0: 创建并占用1库存/同事务入任务
 UNPAID_0 --> PAID_1: 本人模拟结算
 UNPAID_0 --> CANCELLED_5: 本人取消或SYSTEM超时
 PAID_1 --> SHIPPED_2: ADMIN交付
 PAID_1 --> REFUNDED_6: ADMIN退款
 SHIPPED_2 --> RENTING_3: 本人接收
 RENTING_3 --> RETURNED_4: 归还申请经ADMIN验收
```

归还申请0待审核、1通过、2驳回。申请/驳回不释放库存；通过同事务写申请、订单、一次RELEASE与事件。`inventory_held`由1变0，不能依靠重复调用stock+1实现“幂等”。

```mermaid
stateDiagram-v2
 [*] --> PENDING_0: ADMIN确认合法故障
 PENDING_0 --> PENDING_0: ADMIN派工/转派且version+1
 PENDING_0 --> IN_PROGRESS_1: 分配TECH开始
 IN_PROGRESS_1 --> WAIT_ACCEPTANCE_4: 分配TECH提交
 WAIT_ACCEPTANCE_4 --> COMPLETED_2: ADMIN独立验收
```

维修CANCELLED=3原码不变。提交完成保持维护状态；验收需检查其他活动工单、库存与上架状态，不能无条件恢复可租。维修状态转换不分配/释放库存。assigneeName是显示字段，只有真实assignee_id授权。

```mermaid
stateDiagram-v2
 [*] --> PENDING: 与创建订单同事务
 PENDING --> CLAIMED: 到期且SKIP LOCKED认领
 CLAIMED --> DONE: 业务结果及有效token回写
 CLAIMED --> PENDING: 有界退避重试
 CLAIMED --> CLAIMED: 租约过期由新token接管
 CLAIMED --> FAILED: 达到尝试上限
```

只有新订单入任务，既有历史订单不自动补建。UNPAID且仍持有库存才可超时取消；其他状态是NO_ACTION。SYSTEM不能登录，超时没有公开HTTP入口。真实进程崩溃、提交后回写失败、旧worker被栅栏拒绝均有MySQL测试，不依赖Mock数据库。
