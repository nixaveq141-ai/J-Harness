# M8 验收记录

日期：2026-10-15。M8.1/M8.2已完成本地实现和验收；M8整阶段进行中。下方M8.1记录是历史切片，最新证据见M8.2。

| 范围 | 验收内容 | 状态 |
|---|---|---|
| FR-07.3优先级 | 默认/.gitignore/用户层、有序覆盖、父目录剪枝、配置关闭 | 通过 |
| 功能一致性 | list/search/显式grep一致，read_file授权保持 | 通过 |
| AC-05 | !规则不开放metadata/外部路径/链接，中文空格路径 | 通过 |
| 资源边界 | 规则数量/长度/总量、扫描期限、非法规则诊断 | 通过 |
| 装配 | 经典CLI、命名配置、SDK环境、子视图、worker握手/拒绝降级 | 通过 |
| AC-16 | 最终JAR本地/真实Docker使用同一规则与返回路径 | 通过 |
| 回归 | 1000条目性能、版本保护、M0～M7全量测试 | 通过 |

M8.1验收时M8.2/M8.3未开始；远端CI/macOS仍无新增证据。本轮使用本地服务/夹具，不调用付费模型。

## M8.1 实际结果

最终 Windows 命令 `.\mvnw.cmd -B clean verify`：BUILD SUCCESS，269项测试，0失败、0错误、0跳过，包含格式检查、-Xlint:all -Werror与最终CLI打包。结构化证据为 [M8.1-results.json](M8.1-results.json)，slice=M8.1（从当时M8-results.json原样归档）；原始日志 .build-cache/M8-verify.log。M7的260项和历史JSON保留，不用旧结果代表当前源码。

新增9项：M8SearchFilterTest四项（优先级、锚定/目录/中文空格、配置资源边界、metadata/外部路径/真实junction拒绝），M8FilterConfigTest两项（命名配置整对象覆盖与启动前校验），M8FilterIntegrationTest三项（经典/生态CLI实际HTTP模型替身与真实文件一致、真实worker进程确认及旧worker拒绝清理、SDK非默认配置冻结）。原LocalWorkspaceTest的1000条目性能断言及全部历史测试保持。

专项首先发现关闭默认排除会让遍历进入metadata目录，已修复为独立不可覆盖的保护，并保留原负面断言；另补齐经典CLI config.selected的实际过滤快照。首次全量在既有进程用例中遭遇Windows临时目录占用，测试主体断言通过但JUnit清理失败；日志/报告保存在 .build-cache/M8-verify-attempt1.log 和 M8-process-attempt1.xml。未改动进程实现、未跳过或削弱断言，复验全量通过。编译中的夹具调用签名/类型错误也先修正再计通过。

## M8.1 真实 Docker 对照

M8FilterDockerProbe使用最终打包JAR，分别在本地和实际Docker执行两种配置：默认与.gitignore再叠加用户否定/排除；关闭前两层再使用根锚定规则。核对list/search/显式grep相同、中文空格路径、子视图一致、过滤不改变直接读取授权、metadata与穿越拒绝、非默认descriptor及容器清理。结果为 [M8-filter-docker-results.json](M8-filter-docker-results.json)。

另外使用既有M7DockerProbe对同一最终JAR进行默认环境回归，验证原文件版本、编辑/Shell/cwd、本地/容器SDK、每侧4个模型请求重建及容器清理；结果独立保存于 [M8-environment-regression.json](M8-environment-regression.json)。其夹具内部milestone=M7是旧夹具标识，文件及JAR SHA表明这是M8.1最终制品的回归，不覆盖或替代M7历史证据。

两份主动实机结果均SUCCESS、cleanupVerified=true、JAR SHA一致；固定镜像及证据目录在JSON内。夹具调用如下，DOCKER和IMAGE须由宿主明确准备：

```powershell
& "$env:JAVA_HOME/bin/java.exe" -cp 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar;harness-testkit/target/test-classes;harness-testkit/target/classes' io.jharness.testkit.M8FilterDockerProbe DOCKER IMAGE_AT_SHA256 '.build-cache/m8-filter-probe' 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar' 'docs/testing/M8-filter-docker-results.json'
.\scripts\collect-test-evidence.ps1 -Milestone M8 -Slice M8.1
```

本轮没有付费模型请求，没有重新跑Linux全量、macOS或远端CI；容器实际过滤对照不等于全量Linux269项。API/SDK仍1.7。规则语法是明确子集，非完整Git wildmatch，不支持嵌套.gitignore；具体配置见 ../examples/m8-search-filter.md。

M8.1结束时下一步为M8.2：先定义可替换只读工具调度的并发、顺序聚合、写屏障、取消/失败和持久单写者契约，再实施。当前默认串行不变，M8.3限流/重试未开始。

## M8.2 实际结果与验收矩阵

最终Windows命令 `.\mvnw.cmd -B clean verify` 于2026-10-15通过：280项，0失败、0错误、0跳过，含格式、Java21 `-Xlint:all -Werror` 和最终CLI打包。新增11项：M8SchedulingTest八项、M8SchedulingConfigTest两项、M8FilterIntegrationTest新增dry-run一项；原有集成用例同时增加经典/生态CLI的并行配置和日志断言。结构化记录为 [M8-results.json](M8-results.json)，slice=M8.2，原始日志 `.build-cache/M8.2-verify.log`。M8.1的269项原样保存在独立JSON，M7历史结果不覆盖。

| 范围 | 实际验证 | 结果 |
|---|---|---|
| FR-25.1/25.3 并发与聚合 | latch强制两个主体重叠，四次调用峰值为2，后调用先完成，完成日志和模型消息仍为原序 | 通过 |
| 资格与写屏障 | 宿主白名单+注册READ_ONLY+有效ALLOW；模型声明不能替代；写/未列名/ASK前等待已有读取结束，DENY主体0次 | 通过 |
| 失败与检查点 | 已知只读失败汇总，下一请求可重建；注入flush失败后主体/后续写入0次 | 通过 |
| 取消/超时/UNKNOWN | 合作线程取消退出，未启动写入0次；不合作线程60ms超时+共享2秒退出期限后UNKNOWN，无后续请求/自动重放/重复结算 | 通过 |
| 单写者与AC-03 | 全部默认管线tool事件由调用线程追加，每个model.requested仅用事件前缀重建；实机逐次核对HTTP wire | 通过 |
| 可替换与AC-12/13 | 作用域内替换ToolScheduler，停止后旧扩展引用失效；关闭调度器拒绝执行；伪造SUCCESS结算拒绝 | 通过 |
| 默认/配置/dry-run | 默认峰值1；Profile覆盖；非法类型/边界/禁用插件拒绝；启用并行的dry-run仍串行预览且写入0次 | 通过 |
| 跨入口与AC-16 | 经典/生态CLI专项；最终CLI→Web→SDK本地/真实Docker两次读调用按并行模式调度，再经审批写入 | 通过 |
| 回归 | M0～M8.1全量，过滤/版本/进程/取消/审批/恢复/元数据保护；最终JAR实际过滤回归 | 通过 |

本轮修复取消任务携带sideEffectUncertain时被错误改为失败状态的回归：保留CANCELLED任务状态与原退出码，同时保留部分副作用待核实的事实。首次失败日志保留在 `.build-cache/M8.2-verify-attempt1.log`；修复后M3/M4专项及最终全量通过。

另定位既有Windows进程夹具临时目录占用：配额触发清理期间，子JVM可在最后一次ProcessHandle采样之后启动，父进程尚未记录PID即退出。只修改测试夹具，让孩子自行登记PID+startInstant，测试finally按精确身份清理，且若该存活PID属于runner已追踪集合仍断言失败；原PROCESS_LIMIT和residualPids断言保留。未修改生产进程清理能力，仍明确partial、采样/未观察后代及无Job Object限制，不宣称完整OS进程树保证。相关失败/诊断保存在 `.build-cache/M8.2-verify-attempt2.log`、`M8.2-process-diagnostic.log`，修复后LocalProcessRunnerTest九项及全量通过。

## M8.2 最终制品本地 / Docker

[M8.2-scheduling-docker-results.json](M8.2-scheduling-docker-results.json)：M8SchedulingDockerProbe使用最终打包JAR启动真实CLI serve，在本地与实际Docker环境分别通过认证Web API→SDK执行两次读取、审批后写入；核对两个并行调度事件、原序完成/模型结果、每侧三次prepared请求与实际HTTP报文相同、幂等提交与环境关闭。工具主体实际重叠由上方latch测试证明；本实机记录证明装配和环境一致性，不将其宣传为性能加速。

[M8.2-filter-regression.json](M8.2-filter-regression.json)：既有M8FilterDockerProbe针对同一最终JAR复验两组本地/容器过滤规则、子视图、直接读取与权限保护、容器清理。夹具内部milestone=M8.1为历史标识，新文件与JAR SHA明确这是M8.2制品回归。两份报告均SUCCESS、cleanupVerified=true；固定JRE镜像同M8.1，JAR SHA均为 `220301b240af2e33a18f8109947db8236456981960bb9b2ddc598bd579637406`。

```powershell
& "$env:JAVA_HOME/bin/java.exe" -cp 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar;harness-testkit/target/test-classes;harness-testkit/target/classes' io.jharness.testkit.M8SchedulingDockerProbe DOCKER IMAGE_AT_SHA256 '.build-cache/m8-scheduling-probe' 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar' 'docs/testing/M8.2-scheduling-docker-results.json'
& "$env:JAVA_HOME/bin/java.exe" -cp 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar;harness-testkit/target/test-classes;harness-testkit/target/classes' io.jharness.testkit.M8FilterDockerProbe DOCKER IMAGE_AT_SHA256 '.build-cache/m8-filter-probe' 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar' 'docs/testing/M8.2-filter-regression.json'
.\scripts\collect-test-evidence.ps1 -Milestone M8 -Slice M8.2 -VerificationLog '.build-cache/M8.2-verify.log'
```

API/SDK升级1.8，JSONL/schema仍1，旧构造/旧管线兼容串行。使用FakeLlm及本机HTTP替身，无付费请求；没有新增Linux全量、macOS、远端CI、前端或Desktop证据。宿主启用的工具及执行/结果拦截器须线程安全并符合只读契约；框架不能隔离任意Java插件或强杀不合作线程。默认串行不设置并行读取期限，SDK可设置50ms～60秒，CLI为整数1～60秒。下一步M8.3限流/重试插件，其后M8.4收口；M8整阶段未完成。
