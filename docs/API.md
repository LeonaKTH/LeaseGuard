# 当前角色、HTTP与OpenAPI契约

USER0 / ADMIN1 / TECHNICIAN2。SYSTEM为禁用的系统主体，不能登录。管理员以外不可创建/分配技师。普通注册仍固定USER。

|能力|USER|TECHNICIAN|ADMIN|
|---|---|---|---|
|订单/故障/归还|本人合法资源|不开放用户入口|原有管理权限/本人规则|
|故障审核|403|403|允许|
|维修进度|本人故障关联进度|本人被分配工单|全部含历史未分配|
|派工|403|403|PENDING+version+启用技师|
|开始/提交维修|403|本人被分配单|403|
|独立验收|403|403|WAIT_ACCEPTANCE|
|订单审计|本人|403|全部|
|工单审计|本人进度|本人被分配单|全部|
|任务列表|403|403|只读|

通用维修编辑不可修改状态/派工；技师不可变库存、验收、自派工、读他人工单。管理员/技师详情不可见资源用404，路由角色不足用403；JWT角色与数据库冲突时以后者为准。

|路径（前缀/api）|方法|请求与响应|权限|
|---|---|---|---|
|/workbench/tickets|GET|page=1,pageSize=20,status可选；Page<TicketView>|ADMIN/TECH范围SQL|
|/workbench/tickets/{id}|GET|十进制字符串id；TicketView|ADMIN/被分配TECH|
|/workbench/tickets/{id}/start|POST|无body；200空data；幂等Key|被分配TECH|
|/workbench/tickets/{id}/complete|POST|actualCost十进制字符串、progressNote等既有DTO；200|被分配TECH|
|/admin/maintenance/ticket/{id}/assignment|POST|{"assigneeId":"123","version":0}；200；幂等Key|ADMIN|
|/admin/technicians|GET|最多100个启用技师目录，字符串id/username/nickname|ADMIN|
|/admin/technicians|POST|username4—20字母数字下划线，password12—64字符且UTF8≤72字节；200返回安全目录项|ADMIN|
|/admin/maintenance/ticket/{id}/accept|POST|{"remark":"..."}；200；沿用G2|ADMIN|
|/audit/orders/{id}|GET|page,pageSize；Page<Event>|本人USER/ADMIN|
|/audit/tickets/{id}|GET|page,pageSize；Page<Event>|本人USER/被分配TECH/ADMIN|
|/admin/tasks|GET|page,pageSize,status=PENDING/CLAIMED/DONE/FAILED可选|ADMIN只读|

所有分页size=1..100。TicketView中的id/droneId/faultReportId/assigneeId及actualCost均字符串（可空字段为null）；audit/task ID同样为字符串。旧API结构不整体改写；前端将安全整数ID转字符串，不接受超精度数值。库存/状态编码不变。任务重试URL不存在，实际404，不提供虚假的管理按钮。

状态描述修复：订单0待结算、1待交付、2待接收、3租赁中、4已归还、5已取消、6已退款。维修0待维修、1维修中、4待验收、2已验收完成、3已取消。

实际返回200为Result envelope；无新增201创建契约。400非法参数/金额/技师目标，401未认证或失效JWT，403角色不足，404不可见资源，409状态/version/幂等参数冲突。503是原G2有限锁重试耗尽的忙碌语义，已通过真实设备X锁与HTTP验证；没有新增429限流。

OpenAPI在`http://127.0.0.1:18084/api/v3/api-docs`，Swagger沿用原SwaggerConfig合并bearerAuth，避免重复Bean。AccessG4IT读取实际文档核对路径和字符串ID schema；快照见[evidence/reproduce/openapi.json](evidence/reproduce/openapi.json)。新技师密码为writeOnly，响应无密码。

兼容变化：旧ADMIN开始/提交维修现在403；必须先ADMIN派工，再由分配TECH调用新工作台路径。旧业务测试只替换此操作主体，状态/库存/审计断言保留。普通USER维修进度沿用`/fault/{id}/maintenance`，TECH不可借此读取普通用户数据。


G5安全收口：/common/upload与/uploads/**默认关闭，匿名401、USER/TECH/ADMIN均403；未启用的上传接口不出现在当前OpenAPI。V6只增加索引，不改变核心状态、角色或金额契约。
