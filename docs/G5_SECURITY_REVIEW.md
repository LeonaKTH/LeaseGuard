# G5安全、依赖和许可审查

本轮做了源码/配置、真实HTTP、全部本地Git可达对象（含Trace归档内部）、npm及Maven依赖检查。源码候选可以评审，不等于生产安全认证。

## 凭据与历史

全历史扫描的独立blob数量随封存提交增长，以git-history-audit.json为准；12个ZIP及3117个ZIP成员同时读取扫描，已知真实本机运行/数据库/性能/独立候选root凭据与JWT匹配0。私人路径匹配在sanitize_traces.py及G5安全辅助脚本的脱敏/扫描规则字面量，并非泄漏凭据；这些作者辅助脚本不进入公开候选。原始上游历史仍含旧示例配置/未知来源图片，因此不推送开发历史，单独建立无.git的公开快照。

扫描不回显匹配内容。JUnit中有不可登录sentinel及隔离测试账号口令，不是公共演示默认账号；不公开测试数据库。公开初始化每次新随机变量，无固定管理员密码。DPAPI、真实.env、datadir、依赖缓存、target/dist、原始Trace及未经审核日志一律不入包。候选还需独立文件/秘密/链接/Manifest复核，结果见发布准备报告。

## npm与Maven

本轮官方npm audit仍为0，锁文件未做force升级。Maven实际解析111个依赖节点，经官方OSV batch按ecosystem/name/version查询，完整返回无分页遗漏；官方缓存POM及父POM许可全部111项可解析。不是基于README猜测，也不是完整可利用性或法律认证。

初始6节点匹配22个公告：Jackson core/databind、Tomcat embed、Log4j API、Commons Lang、Spring MVC。官方Central确认同线补丁存在后，保持Boot3.5.16，更新jackson-bom2.21.7、Tomcat10.1.61、Log4j2.25.5、Commons Lang3.18.0；重新解析和全量测试。最终只有Spring MVC6.2.19的2项条件性匹配未消除：

|公告|OSV摘要|审查结论|
|---|---|---|
|[GHSA-j9f9-w8pj-32f8](https://osv.dev/vulnerability/GHSA-j9f9-w8pj-32f8)|Spring Framework Server Sent Event stream corruption while rendering fragments|源码未发现启用相关SSE片段渲染或XsltView配置；不把“未发现触发面”冒充已修复。生产前需受支持框架迁移/正式评估。|
|[GHSA-pc63-qcmh-9cmg](https://osv.dev/vulnerability/GHSA-pc63-qcmh-9cmg)|Spring Framework Improper Path Limitation in XsltView|源码未发现启用相关SSE片段渲染或XsltView配置；不把“未发现触发面”冒充已修复。生产前需受支持框架迁移/正式评估。|

这些是依赖版本实际匹配，触发配置/可达性未证明。不能写“后端0漏洞”。Boot3.5.16已结束OSS维护；不为清警告强行升级Boot4。Flyway11.7.2仍提示MySQL8.4超出已测试8.1，空库/增量/恢复实测通过不等于官方认证。

来源：[OSV批查询接口](https://google.github.io/osv.dev/post-v1-querybatch/)、[Apache Tomcat安全公告](https://tomcat.apache.org/security-10.html)、[Spring安全公告](https://spring.io/security/)。详细版本范围/aliases保留原始OSV数据；Log4j使用SLF4J桥接、本项目无容器FORM/DIGEST/TLS/SSE/XSLT业务配置等条件不等于第三方库全局安全。

## 应用边界

- Spring Security每请求读取数据库角色/状态；SYSTEM禁用且不能登录。本人/被分配范围由Service及SQL保证；不信任JWT role。
- 上传原实现缺少文件类型与所有权模型，下载无法证明仅本人附件：G5不扩展文件系统，默认deny /common/upload与/uploads/**（未认证401，三个角色均403），隐藏未启用上传OpenAPI。核心合成资质/租赁流程不依赖上传；没有宣称实现私有附件授权。
- Swagger仍在本地可访问，展示API元数据；生产应禁用或隔离。没有暴露诊断写SQL、后台任务强制重置或超时取消HTTP入口。
- JWT仍存localStorage，有XSS风险；logout主要为客户端清除，改密/重置通过credentialStamp使旧token失效，不宣称全token撤销服务。生产还需TLS、强密钥托管、速率/请求体限制、日志审计等。
- 没有真实支付、付费API、飞行控制或AI模块。模拟结算和合成资质必须在展示中明确。

## 素材与归属

上游Apache-2.0 LICENSE原样保留，增加NOTICE、UPSTREAM和增量清单。旧README逐字节保存在开发归档；公开候选不带原宣传图片/二维码/未知版权素材。核心页面截图来自新合成账号；前端旧外部占位URL改成本项目自制SVG，首页去掉虚构营销数字。

Maven/npm声明的第三方库不作为二进制随源码包分发，许可来源列表另附；不能把Apache-2.0主许可证自动当成所有依赖/图片的许可证。上游订单/设备/资质/模拟支付/维修骨架及布局归上游；G1—G5的事务/权限/可靠任务/界面与验证等增量单独声明。

## 生产发布阻塞

框架支持周期及2个Spring MVC条件性公告、完整上传所有权模型未实现、localStorage/TLS/限流/生产密钥治理、长稳态与容量验证、独立Linux及远程CI尚未实测。源代码求职展示候选与可上公网的生产服务是不同结论；本轮只准备前者，不推送、不部署。

[历史扫描摘要](evidence/g5/git-history-audit.json) · [最终依赖查询](evidence/g5/maven-osv-licenses.json) · [解析许可](evidence/g5/maven-licenses-resolved.json) · [npm结果](evidence/g5/npm-audit.json)。
