# M7 验收记录

日期：2026-10-15。执行环境/SDK、MCP/Skills、只读 Subagent、CLI/Web、Todo/Plan、查询归档与离线恢复核实的明确范围验收，见 [收口记录](M7-closure.md)。

## 自动化回归

M7EnvironmentTest 验证独立工作程序的版本依据、未读覆盖拒绝、路径拒绝、容器启动限制/独占名称、未知执行后的移除和原生控制程序启动。M7SdkTest 验证读工具闭环、独占写锁、持久请求重建、环境绑定、审批拒绝、取消、未决模型请求及清理失败人工核实。最新全量命令为 `.\mvnw.cmd -B clean verify`；Windows 全量 260 项通过，0 失败/错误/跳过，源码 SHA 见 M7-results.json。结果见 M7-results.json；BUILD SUCCESS 表示该源码快照通过回归，不表示 M7 全部能力完成。

## 真实容器验收

M7DockerProbe 是主动运行的实机夹具，不在默认 verify 中偷偷连接 Docker。使用 Docker Desktop 的 Linux Engine，以及官方 eclipse-temurin:21-jre 的固定摘要。镜像摘要、打包 JAR SHA、实机结果见 M7-docker-results.json。

实测两种环境执行同一读→版本编辑→Shell 写入任务，验证相对 cwd 的文件视图一致。SDK 再完成读→编辑→带引号 Shell→证据完成，共每环境 4 个可从日志前缀重建的模型请求和 3 个工具意图；编辑与 Shell 通过固定夹具审批。模型为本地 FakeLlm，不是在线模型验收。

docker inspect 校验 network=none、只读根、全部 cap 移除、no-new-privileges、非 root、512 MiB 和 64 PID；实际命令校验 Docker socket/host 挂载不存在及根目录写入失败。最终 docker ps 确认本次容器已移除。Windows 的 Shell 嵌套引号回归已纳入 SDK 实测。

运行方式（仓库根；需先拉取官方镜像并锁定摘要，DOCKER 为实际 docker.exe 路径）：

```powershell
& "$env:JAVA_HOME/bin/java.exe" -cp 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar;harness-testkit/target/test-classes;harness-testkit/target/classes' io.jharness.testkit.M7DockerProbe DOCKER IMAGE_AT_SHA256 '.build-cache/m7-environment-probe' 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar' 'docs/testing/M7-docker-results.json'
```

夹具每次创建独立的 .build-cache 子目录、会话和自有容器，不修改用户工程业务文件。不把 fake transport 单测当作实机隔离证据。仍待：写入型子 Agent、Web Docker 联合实机验收、CLI 环境/MCP/Skills/Subagent/Web 配置、完整 upstream-capabilities 决策及跨平台 CI。详细边界见 ../development/M7-contracts.md。

重复实机验收曾因复用旧会话的固定 FakeLlm callId 被 DUPLICATE_CALL_ID 拒绝。夹具已改为每次独立目录；生产拒绝规则保持，最终新目录验收通过。最终证据对应最新 JAR SHA，不沿用较早打包结果。

## 2026-10-15 MCP / Skills 专项

M7McpTest 的 11 个测试使用真实 Java stdio 子进程及本机 HttpServer，验证初始化、工具发现、ping、JSON/SSE、Bearer/会话/版本头、正常 DELETE、审批拒绝、宿主只读覆盖、错误 ID、断连/404/取消、工具清单变更、非法出参及节点上限。明确检查工具不重放、事件脱敏、独占 scope 撤销、实际 peer 进程退出、intent flush 失败时不发送 tools/call。分页/点号别名与重复游标用独立传输契约夹具验证，不冒充真实外部服务。

M7SkillsTest 的 5 个测试验证恶意 Markdown 不增加授权、资源白名单与固定快照、脚本审批、精确参数、源版本变更拒绝、路径/秘密文件/大小限制、卸载后旧工具引用失效。SDK 通过真实脚本生成文件并核对每个 prepared 请求可由事件前缀重建。较长夹具明确使用 32K FakeLlm，不扩大生产上下文容量或取消保护。

M7SkillsDockerProbe 使用打包 JAR，在本地和真实 Linux 容器分别加载 SKILL.md/资源，再审批执行脚本，核对标记文件及每环境 4 个请求的重建。独立证据为 M7-skills-docker-results.json，包含实际镜像摘要、产物 SHA 和最终容器清理。调用参数与 M7DockerProbe 相同，只替换主类和结果文件名。

Docker Desktop 起初未运行，已启动现有应用；首次 CONTAINER_START_FAILED 未算通过。正式结果只使用 Engine 就绪后的成功运行。全程没有访问在线 MCP 服务商或发送付费模型请求。远程服务商兼容性、OAuth、SSE 续传等未实现范围见 M7-contracts.md，不将本机服务测试表述为完整 MCP 兼容认证。

## 2026-10-15 Subagent 专项

M7SubagentTest 的 14 项测试验证父子独立上下文/工具/提示钩子、父策略扩展约束、父执行实际编辑、模型拒绝复用、审批拒绝和分配落盘失败时不构造子模型、共享费用限制、真正并发的两个只读子任务、并发与保留任务配额、父取消和子独立截止时间、不合作模型 UNKNOWN、子写者释放/未决请求审计后人工核实。子任务没有验收条件时保持未验证，不能仅凭模型说明完成；父子所有请求分别重建。M7ChildEnvironmentTest 另验证关闭子视图后旧引用读取/版本查询拒绝、写入及命令拒绝，而父环境继续可用。

LocalWorkspaceTest 新增根目录被替换成外部 junction/link 后旧/新视图拒绝重新绑定；目录一千项性能保持原两秒断言。Subagent 用例另验证独立缓存不能允许父方无依据覆盖、已有变更使旧子版本失效。M7EnvironmentTest 用传输故障契约验证借用视图的只读 RPC 中断不能回收父容器；该契约不冒充实机故障注入。

M7SubagentDockerProbe 用最终 JAR 做本地与真实 Docker 对照：子读取、父独立读取和修改、父 4 个/子 2 个请求重建、来源反向关联和产物读取；并核对子读取不能授权父方无版本覆盖，最终回收唯一自有容器。单独结果 M7-subagent-docker-results.json。调用参数与 M7DockerProbe 一致，替换主类/输出文件。模型均为 FakeLlm，没有付费请求，也没有启动用于开发本项目的编码子 Agent。

该 Subagent 切片只读委派与“父单写者”策略，不覆盖子 Agent 修改/命令、递归子任务或 Teams；未知远程调用和任意不合作线程不能宣称已清理。本机 Web 后端见下方后续切片。

## 2026-10-15 本机 Web 后端专项

M7WebTest 的 13 项测试运行真实本机 HTTP/SSE，而非直接调用 handler。模型使用 FakeLlm、工作区使用临时真实文件与 Git，未发送付费请求。覆盖：

- Bearer 缺失/错误、恶意 Host、Origin、弱 token、独占 catalog、重复键/额外字段/超大正文/未知 Profile 拒绝；都不构造 SDK。
- HTTP 创建→待审批→精确 callId 审批→实际写入→SDK 关闭；检查原 JSONL 可重建两个模型请求，事件 cursor 分页和缺失 Diff/游标边界。
- 相同提交在运行中和服务重启后返回原任务，不构造第二个模型；改变目标冲突。多个 Profile 绑定同一工作区时拒绝并发，保留任务上限不删除幂等记录。
- 审批拒绝、超时、取消和迟到决策不会创建目标文件；拒绝/超时没有 tool.intent。catalog flush 故障先于 SDK 工厂，且阻止后续准入。
- 不合作工厂下 close 有界返回；重启加载 UNKNOWN，不重放、不释放原占用。未决模型请求即使 SDK 返回终态也保持 UNKNOWN。
- 已知 Web token 在目标、模型输出和 HTTP 事件中脱敏；大事件 payload 省略但保留游标。Diff 接口返回真实 git_diff 工具结果与同一个源事件 ID。
- 同时两个 SSE 订阅仍可审批并完成任务，第三个订阅 429；事件含原 ID，Last-Event-ID 正确续读，双重游标拒绝。

首次专项发现拒绝超大正文后的连接复用问题，已通过响应 Connection: close 修复，原断言保留并通过复验。后续已完成 CLI serve 与 Web+Docker 联合实机验收，详见下节；没有页面、多用户/TLS。最新完整构建和精确源码 SHA 见 M7-results.json。

## 最终组合与收口专项

M7ClosureTest 的 6 项覆盖 Todo/Plan 的独立持久状态、修订冲突、批准撤销、成功工具证据；全文索引的增量/损坏重建/不改原日志；CLI 查询比较归档、归档后拒绝执行；严格生态配置；网页批准/拒绝/固定来源/跳转/大小边界。M7CliEcosystemTest 的 2 项使用真实本机 HTTP 模型替身与 stdio MCP，验证父子 7 个请求、Skills、Todo/Plan 声明、AGENTS 注入、报告来源及“子关闭后父仍可审批”，另验证无副作用 dry-run 和弱/缺失 Web token 拒绝。

M7WebTest 新增离线人工核实：活动 catalog/SDK 写锁占用/未决模型请求均拒绝，先核实 SDK 再追加 web.task.verified；重启显示 VERIFIED，保留旧 status，不重放原任务。旧请求 ID 不再次调用模型。该方法不证明任意工厂线程已经停止，必须先进行外部资源检查。

M7WebDockerProbe 通过最终 shaded CLI 的 serve 子进程，实际 HTTP 创建任务、查询审批、决策后写入，再检查幂等、事件脱敏、关闭和容器清理。本地与 Docker 每侧 3 个真实 HTTP 请求从 prepared 日志前缀重建，并按 OpenAI wire 编码核对；不是用 FakeLlm 直接替代 HTTP 编码验证。结果 M7-web-docker-results.json。运行参数与其他三组 Probe 相同：DOCKER、IMAGE_AT_SHA256、BASE_DIR、WORKER_JAR、OUTPUT_JSON。

固定提交源码核验结果见 M7-upstream-review.json，范围决定见 ../requirements/upstream-capabilities.md。Windows 全量证据 M7-results.json；Linux 独立 Java 21 构建证据 M7-linux-results.json；它们不代替尚未触发的远端 CI/macOS 实机验收。

Linux 首轮实际发现同 JVM 失败锁竞争关闭通道导致 POSIX 跨进程锁释放；已加 JVM 占用表，并保持原第二 JVM 冲突断言，补充维护锁竞争后的第二 JVM 断言。后续发现清空命令环境丢失 UTF-8 locale，已只加入 LANG/LC_ALL/LC_CTYPE 白名单及 POSIX 默认 LANG；取消夹具改为有限多次写入填满实际管道，保留 INPUT_UNKNOWN 和 4 秒期限断言。Windows 一次回归另遇临时目录删除失败，保留原断言，最终结果以复验记录为准。首轮失败不计入通过证据。

最终 Linux 全量同为 260 项通过、0 失败/错误/跳过；236 个 Java/POM 哈希与 Windows 一致。四份 Docker 实机证据已刷新相同最终 JAR SHA，所有本次容器已清理。参见 M7-closure.md 和 M7-linux-results.json。

