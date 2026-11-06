# GitHub Public公开验收

日期2026-11-06。公开仓库：https://github.com/LeonaKTH/LeaseGuard 。源码可公开，不代表生产部署安全就绪；没有公网后端、真实支付/飞行控制或AI。

## 真实远程验证

在GitHub Ubuntu24.04 runner运行本仓库同一workflow的完整手动browser=true验收：[42146463516](https://github.com/LeonaKTH/LeaseGuard/actions/runs/42146463516)，源码 `19bd319948a0abb85a9c6c2238319d5e774e2b7d`。这是远程结果，不是G5本机26项浏览器断言的复用。

- Java21：92项（22单元+70真实MySQL），失败/错误/跳过均0；正式Flyway V1—V6，G2并发、G3双JVM崩溃恢复和G4角色/派工Gold仍执行。
- MySQL8.4.13：独立临时容器、loopback13308、四个新catalog、DML/迁移账号分离；退出时验证本任务容器身份并清理。
- Node24.13.0：npm ci、5项前端单元、typecheck、Vite build、npm audit通过，审计0；另5项CI安全边界测试通过。
- playwright-core1.64.1，Chromium 156.0.8078.4：4独立Context，26断言、13截图；订单/归还验收、真实派工/技师/管理员独立验收、Scheduled超时与审计三链通过，读取真实MySQL中间及最终Gold。
- 已扫描runner日志和全部Artifact成员，未发现已知真实密码、JWT、GitHub Token、私人Windows路径或原始Trace；环境口令字段确认mask。无跳过数据库测试；push事件的可选browser步骤跳过是预期，手动完整运行实际执行。

[精简远程结果](evidence/publication/runner-validation.json)、[浏览器断言](evidence/publication/runner-browser-result.json)、[最终数据库事实](evidence/publication/runner-db-final.json)。完整JUnit/截图在对应Actions Artifact，保留7天；原始Trace不上传。后续仅文档/Manifest提交也会触发main检查，最新结论请查看[Actions](https://github.com/LeonaKTH/LeaseGuard/actions)。

## 发布来源与必要修复

G5批准ZIP SHA256：`cadca95f6f6b0f20e93f71c20d5a9d6baf449c1d47c23ee8bfd270ff6c764e12`。首次根提交`d047f8369e72eeb803d06c14eb81af4a2a02fbca`的287个blob与ZIP完全一致，父提交为空。原开发历史不在本仓库。

首次工作流在测试前因runner.temp上下文失败；然后首次镜像拉取stderr混入容器ID、Linux Bash遇到CRLF分别暴露，失败运行保留。最小修复仅CI脚本/配置与CI合同测试，没有删除测试、改业务Gold或修改库存/任务/权限/迁移。详见[发布差异](../PUBLICATION_CHANGES.md)。当前Manifest对应当前公开树，不冒充未修改的G5 ZIP。

## Fork PR与安全范围

contents:read，checkout不保留凭据，普通pull_request，不使用pull_request_target。main使用新独立CI Secret；Fork PR没有该Secret时仅为其临时MySQL生成新随机256bit口令并mask，保留同一套完整测试。不向Fork提供仓库Secrets。本轮未额外创建真实外部Fork PR；随机分支与凭据/清理边界由本地及远程5项测试覆盖，不宣称做过外部PR实测。

匿名网页、原始文档/图片和禁用认证助手的clone独立核查；仅GitHub noreply邮箱，新历史根与原ZIP逐文件对应。README的页面图来自G5独立本机合成数据，远程CI证据单独标识，不混用。

Manifest按仓库文件字节记录。若在Windows希望直接按工作树核验，使用`git -c core.autocrlf=false clone https://github.com/LeonaKTH/LeaseGuard.git`，避免系统Git把混合换行自动转换；按Git blob核验不受检出换行影响。shell脚本由.gitattributes固定LF。

Boot3.5.16支持周期、两项Spring MVC条件性公告、Flyway/MySQL兼容提示、localStorage JWT、关闭的附件入口、生产TLS/限流/密钥治理和大分页限制，均按[安全审查](G5_SECURITY_REVIEW.md)与[性能报告](G5_PERFORMANCE.md)保留。部分v4 Actions在runner日志中仍提示Node20已弃用并由平台强制Node24，后续可独立升级验证，本轮没有为消警告扩大改造。
