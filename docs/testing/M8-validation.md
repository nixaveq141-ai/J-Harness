# M8 验收记录

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](../development/current-state.md)及[全阶段审计](M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

日期：2026-10-16。M8.1/M8.2/M8.3已完成各自范围的实现与验收；M8.4已完成所选范围收口。下方M8.1～M8.3为历史切片，当前全量及最终制品证据见M8.4。

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

最终Windows命令 `.\mvnw.cmd -B clean verify` 于2026-10-15通过：280项，0失败、0错误、0跳过，含格式、Java21 `-Xlint:all -Werror` 和最终CLI打包。新增11项：M8SchedulingTest八项、M8SchedulingConfigTest两项、M8FilterIntegrationTest新增dry-run一项；原有集成用例同时增加经典/生态CLI的并行配置和日志断言。结构化记录为 [M8.2-results.json](M8.2-results.json)，slice=M8.2，原始日志 `.build-cache/M8.2-verify.log`。M8.1的269项原样保存在独立JSON，M7历史结果不覆盖。

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

## M8.3 实际结果（2026-10-16）

最终 Windows `.\mvnw.cmd -B clean verify` 通过295项，0失败/错误/跳过，包含Spotless、Java21 `-Xlint:all -Werror`和最终CLI打包。新增15项：M8ResilienceTest十三项、M8ResilienceConfigTest两项。结构化证据 [M8.3-results.json](M8.3-results.json)，slice=M8.3；M8.2原280项证据原样归档 [M8.2-results.json](M8.2-results.json)。原始全量日志 `.build-cache/M8.3-verify.log`，专项 `.build-cache/M8.3-targeted.log`，历史核实兼容复验 `.build-cache/M8.3-compatibility.log`。

| 范围 | 实际验证 | 结果 |
|---|---|---|
| 默认与所有权 | 默认无新增重试；旧适配器显式429退避仍保留；插件对legacyRetries=3的适配器只发送所需2次，独占重试次数 | 通过 |
| 次数/时间/费用 | maxAttempts含首次，429次数耗尽；费用保留上界后阻止下一发送；总期限包含退避及合作发送，间隔有实际时间断言 | 通过 |
| 限流资源/取消 | latch固定一个执行中和一个等待者，第三调用立即拒绝；取消等待主体0次；退避取消不再发送；关闭打断间隔等待 | 通过 |
| UNKNOWN | 传输未知、超时、断流和伪造MODEL_RATE_LIMIT普通错误均只发送一次、无拒绝事实，SDK再次运行拒绝 | 通过 |
| 持久化/重建 | 请求checkpoint失败发送0次，拒绝事实flush失败不再重试；每个尝试拥有独立model.requested，可由对应日志前缀重建 | 通过 |
| 作用域/卸载/替换 | 注入替代ModelExecution服务，卸载后保留扩展引用不能执行；标准服务close拒绝后续请求并停止等待 | 通过 |
| 多尝试恢复 | model.rejected只结算精确requestEventId；旧请求核实不能清除新请求；历史无sourceEvent格式仅唯一未决时兼容 | 通过 |
| 配置/跨入口 | 非法字段/类型/范围、插件禁用、Profile整对象覆盖；经典/生态CLI真实HTTP的429→重试→读取→完成与脱敏 | 通过 |
| 全量回归 | M0～M8.2，包含审批、版本、取消、恢复、压缩、SSE、只读调度与进程边界 | 通过 |

首次全量发现历史model.verified仅带stepId的两处恢复用例被新精确关联规则阻断；保留原断言，补回仅唯一未决时的兼容并复验通过。失败日志保留 `.build-cache/M8.3-verify-attempt1.log`。主动Web夹具首次仍用旧请求序号定位工具结果，新增429使序号偏移；修正定位但保留顺序/wire/次数断言，失败日志 `.build-cache/M8.3-resilience-probe-attempt1.log`。夹具文件换行触发的格式失败亦保留 `.build-cache/M8.3-verify-format-attempt.log`；最终格式与全量均通过，不将失败尝试计作通过。

## M8.3 最终制品本地 / Docker

[M8.3-resilience-docker-results.json](M8.3-resilience-docker-results.json)：最终JAR启动真实CLI serve，经认证Web API→SDK在本地及实际Docker环境执行。每侧首先完整429拒绝一次、只重试一次，再进行并行模式读取、审批写入和完成；四次HTTP发送分别具备独立费用预留及prepared请求，与各自日志前缀重建的完整wire逐项相同。检查幂等提交、原序工具结果、拒绝/重试事件及环境关闭。本HTTP模型替身位于宿主，不把Docker network=none误称为禁止宿主模型网络。

[M8.3-filter-regression.json](M8.3-filter-regression.json)：同一最终JAR复验两组本地/实际Docker过滤、子视图、直接读取授权、metadata/越权保护及容器清理。旧过滤夹具内部milestone=M8.1为历史标识，新文件/JAR SHA说明M8.3回归。两报告均SUCCESS、cleanupVerified=true；JAR SHA `868e56d14b9af17eb3346bba0e1b3ee23ad77d4b20bac4c95a2bc0337d596d9a`。固定镜像同M8.2；引擎未启动的准备阶段不算验收成功，启动并核实Engine后执行。

```powershell
& "$env:JAVA_HOME/bin/java.exe" -cp 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar;harness-testkit/target/test-classes;harness-testkit/target/classes' io.jharness.testkit.M8ResilienceDockerProbe DOCKER IMAGE_AT_SHA256 '.build-cache/m8.3-resilience-probe' 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar' 'docs/testing/M8.3-resilience-docker-results.json'
& "$env:JAVA_HOME/bin/java.exe" -cp 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar;harness-testkit/target/test-classes;harness-testkit/target/classes' io.jharness.testkit.M8FilterDockerProbe DOCKER IMAGE_AT_SHA256 '.build-cache/m8.3-filter-probe' 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar' 'docs/testing/M8.3-filter-regression.json'
.\scripts\collect-test-evidence.ps1 -Milestone M8 -Slice M8.3 -VerificationLog '.build-cache/M8.3-verify.log'
```

API/SDK1.9，JSONL/schema仍1；旧SDK构造保留，子任务重试独立且默认关闭。仅非流式429安全拒绝进入标准重试，工具/5xx/UNKNOWN不重放，任意自定义Java适配器仍须遵守合作取消契约。配置见 [模型限流与重试](../examples/m8-model-resilience.md)。没有付费模型请求、新Linux全量、macOS或远端CI证据；实际容器链路不等于Linux295项全量。下一步仅M8.4整阶段收口，前端/Desktop未提前推进。

## M8.4 整阶段收口（2026-10-16）

M8专项35项首先通过，再运行Windows `.\mvnw.cmd -B clean verify` 和固定Java21 Linux容器内 `sh ./mvnw -B -Dmaven.repo.local=/tmp/m2 clean verify`。两侧各295项，均0失败/错误/跳过；相同完整测试名、259个Java/POM文件SHA一致。Windows记录为[M8-results.json](M8-results.json)（slice=M8.4），Linux独立记录为[M8-linux-results.json](M8-linux-results.json)。M8.3旧全量原样归档[M8.3-results.json](M8.3-results.json)，没有覆盖M7历史证据。

同一最终JAR已刷新七组本地/实际Docker夹具：environment、skills、subagent、web、filter、scheduling、resilience；结果均SUCCESS、cleanupVerified=true，SHA均为 `12a8c812fc90035d6d6a6e78da8f5de3af6d3dcea28d72d54af68a9ccf37207e`。version/help/doctor、固定上游19入口SHA及M7文件保留也已核实。Windows及Linux均包含格式、`-Xlint:all -Werror`、原生/进程/持久化/恢复、M8过滤/调度/重试测试，未削弱断言或跳过用例。

新增verify-m8-closure.py绑定两侧全量、日志、源码、七份实机与当前制品；正例及12组篡改证据负例通过。完整AC/NFR矩阵、逐报告链接、准备失败记录及平台/发布边界见[M8收口](M8-closure.md)，机器核验摘要见[M8-closure-results.json](M8-closure-results.json)。本轮无产品Java/POM改动，无付费模型请求；Linux容器全量不冒充远端CI或原生Linux桌面，macOS仍未验证。

M8按已约定范围完成。下一步M9发布准备与平台验证：无Git remote时先完成本地可做事项，不自行创建远端/推送/发布；前端M10、Desktop M11未提前推进。
