# LeaseGuard · 租赁履约与维修责任闭环

LeaseGuard让归还、维修交付和超时取消有明确的责任与数据库事实：用户提交归还不会立即释放库存，技师提交完成不会直接恢复设备，后台任务重试不会重复归还库存。

基于[springmeng/Drone-Rental-System](https://github.com/springmeng/Drone-Rental-System)固定源码二次开发。保留上游基础业务与布局，个人增量聚焦事务一致性、权限/派工、可靠任务、核心全栈和可复现验证；[上游归属](UPSTREAM.md)与[变更边界](CHANGES_FROM_UPSTREAM.md)可核验。

## 三种身份完成真实业务

- **USER**：资质、型号租赁、模拟结算、接收、申请归还、故障与本人维修进度。
- **ADMIN**：交付、归还审核、故障确认、真实技师派工、独立维修验收、任务与审计。
- **TECHNICIAN**：仅本人分配工单，开始维修、提交待验收、查看最终验收结果。

库存按型号计数，每单占用一单位；不提供逐台资产管理、真实支付或无人机飞行控制。资质/账号/数据均为合成演示，未实施AI。

## 工程取舍

Java21、Spring Boot3.5.16、Spring Security、MyBatis-Plus、MySQL8.4、Flyway V1—V6；Vue3、Element Plus与核心TypeScript。模块化单体，不需要Redis、MQ、Docker或微服务。

- 设备优先行锁、状态/version CAS，库存持有标志和唯一释放流水。
- 持久化Idempotency-Key、参数摘要、同事务命令回执/事件，未知网络结果同键恢复。
- 有界2线程超时任务、SKIP LOCKED短认领、租约栅栏及真实JVM崩溃恢复。
- 数据库当前角色权威、本人/派工SQL范围、管理员与技师职责分离。
- 分页后批量投影/范围JOIN、测量驱动V6索引；没有用缓存掩盖事务或权限。

## 证据而非功能堆砌

G5本机92项Java（22单元/70真实MySQL）、原270轮G2竞争及G3/G4各60轮、91项HTTP、5项前端单元/类型/build通过。10万合成订单下订单/维修列表仍6条SQL，30组分页结果哈希相同。完成并发1/10/30各3轮前后对照及任务开关实验，共24轮真实HTTP测量；数据、环境、失败和剩余瓶颈全部保留。

这些是单机、明确场景的实验，不是生产TPS。硬件i7-14650HX/24逻辑核心/约16GB、MySQL128MiB缓冲池、Hikari10；完整指标及深OFFSET约0.7秒的限制见[性能报告](docs/G5_PERFORMANCE.md)。独立候选构建/浏览器验证结果见[发布准备](docs/G5_RELEASE_READINESS.md)，不把远程CI配置当成已运行。

## 从这里开始

- [环境、初始化和启动](docs/RUN.md)：环境变量与正式Flyway，无作者私有DPAPI依赖。
- [自动验收与实验复现](docs/TESTING.md)：全新实例、分离账户、真实MySQL与可选Chrome三链。
- [架构](docs/ARCHITECTURE.md) · [状态机](docs/STATE_MACHINES.md) · [ER图](docs/ER.md)
- [角色/API](docs/API.md) · [安全与许可](docs/G5_SECURITY_REVIEW.md)

源码发布候选不包含真实口令、私有环境、datadir、依赖缓存、构建产物或原始Trace。没有固定演示密码；普通用户注册、首个管理员一次性bootstrap，使用操作者独立安全变量。

## 当前边界

Boot3.5.16已结束OSS维护；兼容安全补丁已核验，但Maven仍有2项Spring MVC条件性公告，不能声称0漏洞或生产安全就绪。上传/下载因无私有附件模型默认关闭，Swagger本地可见，JWT localStorage及生产TLS/限流/密钥管理仍需评估。Flyway/MySQL兼容提示、大前端chunk、旧非核心JS页面也没有隐藏。

本轮仅本地工程和公开源码候选准备。没有推送GitHub、运行远程Actions、部署公网或实施LangChain4j。
