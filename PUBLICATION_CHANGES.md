# GitHub公开版本与G5验收候选的差异

唯一发布起点是G5候选ZIP，SHA256 `cadca95f6f6b0f20e93f71c20d5a9d6baf449c1d47c23ee8bfd270ff6c764e12`，1,171,547字节、286个Manifest条目+Manifest自身。首次公开提交 `d047f8369e72eeb803d06c14eb81af4a2a02fbca` 的287个Git blob与ZIP逐字节相同，父提交为空；不包含原G0—G5开发历史。

GitHub首次真实运行发现作业级env使用不受支持的runner.temp表达式，在开始测试前解析失败。后续只作发布/CI必要兼容调整：

- `.github/workflows/ci.yml`：改为允许的github.workspace路径，checkout不保留凭据；CI容器用专用脚本启动/清理，保留Java21、MySQL8.4.13、Node24、全部Maven/MySQL/Vue和可选浏览器检查；contents:read，不使用pull_request_target。
- `scripts/ci/service.py`：仅GitHub runner可运行。main使用新配置的CI专用Secret；Fork PR没有Secret时，为该次独立MySQL实例生成32随机字节口令，先mask、仅环境传递，不向Fork提供仓库Secret。数据库只绑定127.0.0.1，按run标签验证后清理自己容器。
- `scripts/ci/test_service.py`：验证缺Secret随机生成、口令不进入参数、本机拒绝和跨任务清理拒绝；另以真实本地子进程回归“首次镜像下载stderr混入容器ID”问题，共五项，不在本机启动Docker。
- `CiConfigG4Test`：适应显式容器启动方式，继续断言固定MySQL版本、loopback、随机口令/掩码、完整检查与无跳过；未修改库存、并发、角色、维修或任务Gold。
- README与公开验收说明按实际远程结果整理，旧G5报告仍是其当时的本地阶段记录。

业务生产代码、Flyway V1—V6及依赖锁文件未因发布改动。原ZIP保持不变；当前MANIFEST.sha256随公开提交重新生成，不能再将最终公开树称为原ZIP逐字节副本。精确新增/修改列表由首次提交到当前main的Git diff核验。

未部署公网应用、未添加AI、未修改其他项目、未推送原开发历史。源码Public不代表生产安全认证；Boot支持周期和两项Spring MVC条件性公告仍见安全审查。
