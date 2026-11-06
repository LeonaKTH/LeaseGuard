# 可移植构建、演示与验证

需要JDK21、Maven3.9、MySQL Community8.4、Node24/npm。Python3用于自动复现/性能工具，应用本身不依赖Python。无需Docker、Redis、MQ、模型或付费API。只在127.0.0.1使用MySQL13308、后端18084/api、Vue15174。

## 独立自动验收

准备一个全新、仅本项目使用的MySQL实例，明确它的data/auto.cnf中的server-uuid，并为root设置独立强口令。不能指向已有业务实例。脚本会核验UUID，拒绝已有LeaseGuard库，不会DROP、TRUNCATE、clean或重置任何库。

在解压后的候选根目录运行（Windows例；路径换成自己已安装的工具）：

```powershell
$env:JAVA_HOME='<你的JDK21目录>'
# 不写入口令字面量；脚本会安全提示输入新实例root口令，也可由当前进程安全注入。
python -X utf8 scripts/portable/verify.py --expected-uuid '<新实例UUID>' --mysql '<MySQL>/bin/mysql.exe' --maven '<Maven>/bin/mvn.cmd' --node '<Node>/node.exe' --npm '<Node>/npm.cmd' --repo '<可选的本机Maven缓存>'
```

脚本创建dev/test/g2_fresh/g4_fresh四库与分离DML/迁移账号，口令为当前进程新随机值；V1起点用于验证原数据迁移，g4_fresh保持空库供完整迁移测试。随后Maven verify、干净npm ci、前端单元/类型/构建。缺MySQL/口令必须失败，不能加skip参数伪装成功。不依赖作者DPAPI、Git历史或私有路径；可省略--repo使用自己的正常Maven缓存/官方Central。

若已有受控Chrome，增加`--browser --chrome '<Chrome可执行文件>'`。脚本创建新合成夹具、启动生产JAR和Vue，用隔离Context执行三条真实链并直接读MySQL Gold，最后关闭自己启动的前后端及子进程。不会下载本机浏览器。测试源码中显式生成模式只用于无历史payload的全新环境，仍核验SQL行ID、字段、范围和SQL数量；本机旧夹具模式仍严格比对已认可payload。

验证证据在docs/evidence/reproduce，私有运行日志/原始Trace在.local，不得打包公开。脚本为一次性新环境验收，进程结束后不会导出随机业务账号口令；交互演示请用下面自己持有变量的方案，不能清空验证库来重跑。

## 正常交互演示

另用全新演示实例或从开始就自行安全保存所选账号口令。管理员在该实例内创建一个空dev库及相应账号：

```sql
CREATE DATABASE leaseguard_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'leaseguard_dev'@'localhost' IDENTIFIED BY '<自己生成的随机运行口令>';
GRANT SELECT,INSERT,UPDATE,DELETE ON leaseguard_dev.* TO 'leaseguard_dev'@'localhost';
CREATE USER 'leaseguard_migrate_dev'@'localhost' IDENTIFIED BY '<不同的随机迁移口令>';
GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON leaseguard_dev.* TO 'leaseguard_migrate_dev'@'localhost';
```

这些仅为新库/账号操作，遇到已存在应停止核查，不能追加DROP。表结构只由Flyway V1—V6建立；禁止执行上游init.sql。

按根目录`.env.example`在当前进程注入真实环境变量；文件仅模板，Spring不自动读取它，不能把占位符当口令。JWT至少32随机字节。不要修改系统级PATH/JAVA_HOME或提交.env。MySQL运行DML和迁移DDL账号必须分离。

```powershell
cd drone-rental
mvn -B -ntp package -DskipTests
# 第一次创建管理员时，另提供自己选择的BOOTSTRAP_USER/PASSWORD：
java -jar target/drone-rental-1.0.0.jar --spring.profiles.active=local-bootstrap
```

首次profile只建立BCrypt管理员，存在同名会拒绝覆盖。创建后重启，去掉profile及BOOTSTRAP变量；正常运行使用同一套自己保存的数据库/JWT变量。没有固定演示管理员密码。普通用户通过/register注册；提交合成资质后由管理员审核，之后租赁/交付/接收/归还。管理员在维修页建立真实TECHNICIAN、派工，技师/login后进入/technician。

```powershell
# 另一个终端，候选根目录
npm --prefix drone-rental/drone-rental-web ci --ignore-scripts
npm --prefix drone-rental/drone-rental-web run dev
```

JOBS_ENABLED默认false；需要实际超时调度时仅为当前后端进程设true，默认新订单1800秒超时。它不会给历史订单补任务。任务页只读，FAILED需先排查，不直接重置状态。结算仅模拟，没有真实支付、飞行控制或AI。

## 性能重现

另建全新leaseguard_perf及其DML/迁移账号，不能用dev/test。为该进程配置perf URL/账号/JWT变量。显式性能启动器只允许127.0.0.1:13308/leaseguard_perf，使用正式迁移；不在生产JAR中。

```powershell
cd drone-rental
mvn -B -ntp test-compile dependency:build-classpath -Dmdep.outputFile=target/perf-classpath.txt -DskipTests
cd ..
python -X utf8 scripts/perf/start.py
# 另一个终端使用相同perf进程变量；只允许全新未生成数据的perf库：
python -X utf8 scripts/perf/seed.py
python -X utf8 scripts/perf/query.py reproduce
# 把上面启动器的Java PID与自己专用MySQL的真实监听PID注入资源采样变量：
$env:LEASEGUARD_PERF_JAVA_PID='<PID>'
$env:LEASEGUARD_PERF_MYSQL_PID='<PID>'
python -X utf8 scripts/perf/load.py reproduce
```

原生mysql客户端由MYSQL_CLIENT指定；root仅在需要全局只读死锁计数时通过LEASEGUARD_ROOT_PASSWORD临时注入。HTTP负载为真实已认证读取，不测BCrypt登录或写TPS。默认并发1/10/30，各预热30秒、3轮60秒；数据与指标全部标为合成。资源采样适配Windows（本轮实测平台）；Linux构建逻辑有跨平台路径修复，但本轮没有Linux性能/远程CI实测。

索引基准原始源版本和依赖见G5_PERFORMANCE，当前候选含V6和安全补丁，不应假装仍是G4。可用报告中的完整SQL/IGNORE新索引消融复核计划；不得改已应用迁移或删除原库去追求相同数字。批量夹具是读取数据，不代替真实Service事务验证。

停止：应用终端Ctrl+C；性能启动器持有自己的Popen生命周期并关闭子进程。MySQL按自己创建实例的UUID核验后关闭，不能按进程名批量杀其他Java/Node/MySQL。Windows重新打包前停运行中的JAR，npm ci前停专用Vite。

Boot3.5.16不再有持续免费OSS维护；Flyway11.7.2仍警告MySQL8.4超出其已测试到8.1范围。本地通过不是厂商认证或公开生产部署许可；详见安全审查。


独立浏览器验收使用已构建的Vite preview及显式本地API代理，避免首次dev依赖预构建/HMR打断登录。交互开发仍可npm run dev；验收先build，再执行`node node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 15174 --strictPort`。preview不是生产公网服务器。
