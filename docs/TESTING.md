# 测试与实验复现

Maven Surefire运行22项单元，Failsafe运行70项真实MySQL集成，共92项。原G4 90项有效保留，新加2项公开文件边界测试；迁移期待追加V6，维修身份/库存/状态Gold不放宽。没有H2或Mock代替InnoDB，也没有缺数据库时的默认跳过。

覆盖：状态文案/角色/DTO、JWT/BCrypt、唯一/FK/CHECK、同型号设备锁、状态CAS、幂等重放/冲突、释放一次、真死锁/锁超时、失响应、派工冲突和本人技师范围、独立验收、任务短认领/栅栏/有限重试、两个真实JVM崩溃恢复、实际SQL数量与字段/排序，以及上传下载默认关闭。

完整verify中的G2九类竞争各30轮×20真实InnoDB事务=5400任务，G3支付/取消对超时各30轮，G4同版本派工/派工对开始各30轮。它们是正确性实验，不是吞吐测试。直接修改低层Mapper绕过业务协议不在保证内。

新环境先按RUN设置四个全新catalog及账号，可用`scripts/portable/verify.py`自动建立安全测试环境和正式迁移。测试变量还包括DEV_MIGRATION_PASSWORD、FRESH_PASSWORD、G4_FRESH_PASSWORD及EVIDENCE_DIR/TEST_SHA。原历史SQL payload模式不变；公开包显式generated模式使用同样100行合成 fixture 的SQL排序Oracle和字段/范围约束，不借缺历史数据跳过测试。

前端：5项Node单元（操作键复用/清除/指纹、Long安全边界及金额），vue-tsc严格检查核心TS，Vite生产构建。旧未使用JS页不声称全仓TS覆盖。浏览器复用playwright-core/已有Chrome，四个隔离Context，只允许已确认15174/18084 origin；稳定role/label操作，页面刷新、提交禁用、提交已成功但响应丢失后同Key重试、真实409后刷新。

三条新浏览器链：A新建/结算/交付/接收/归还/验收；B确认故障/真实派工/分配技师开始与提交/ADMIN独立验收/本人用户进度；C真实Scheduled取消及ADMIN任务/系统审计页面。动作必须UI点击，准备夹具/读取Gold可以由安全工具执行。数据库最终核对held、订单/申请/维修状态、库存流水、事件/命令、任务及真实验收人时间。

G5性能为独立perf库/读取夹具和400笔真实Service任务队列，不并行运行这些JUnit重负载。每轮数据、失败尝试、原始延迟摘要与限制见G5_PERFORMANCE。CI配置含Java21、Node24、MySQL真实服务，数据库缺失应失败；可选受控浏览器任务。未推送，远程GitHub Actions尚未执行，不能把本地YAML/语法检查叫远程绿灯。
