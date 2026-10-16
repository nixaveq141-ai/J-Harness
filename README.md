# J-Harness

基于 Java 21 的可扩展 Agent Harness，需求基线为 [PRD v1.1](docs/requirements/JHarness-PRD-v1.1.md)。M0～M7 的本地实现范围和验收见各阶段记录，M7 最新计数与源码版本见 [收口记录](docs/testing/M7-closure.md)；CLI、持久化与恢复、SSE、上下文压缩、PTY/后台任务、多模型路线与命名配置组合已贯通。真实 DeepSeek 验收及费用证据见 [M4 验收记录](docs/testing/M4-validation.md)。Windows/Linux/macOS 远端 CI 已配置，尚未运行。

## 构建与启动

需要 JDK 21 并设置 JAVA_HOME。Wrapper 固定 Maven 3.9.9；首次构建下载依赖，自动化测试无需模型 key。

```powershell
.\mvnw.cmd -B verify
.\jharness.cmd --version
.\jharness.cmd --config docs/examples/deepseek-config.json doctor
```

Linux 构建为 `sh ./mvnw -B verify`，启动为 `sh ./jharness --help`；Windows 11 本机与 Linux Java 21 容器分别保留验收证据。verify 包含 JUnit、编译警告检查（-Xlint:all -Werror）、Spotless 和模块打包。修改 Java 后运行 `.\mvnw.cmd spotless:apply`。可直接运行 `java -jar harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar --help`。

## 模型配置与交互

[DeepSeek 配置示例](docs/examples/deepseek-config.json) 使用 deepseek-flash、https://api.deepseek.com、thinking disabled、环境变量 DEEPSEEK_API_KEY 和单轮 CNY 2.50 预算。请在宿主环境中设置 key，不写入配置或项目。示例单价需按服务商当前价格维护；预算记录是保守上界，不是账单。doctor 只检查环境和惰性装配，不发送付费请求。

```powershell
.\jharness.cmd --config docs/examples/deepseek-config.json run "探查 pom.xml，并说明项目的构建方式" --require-read pom.xml
.\jharness.cmd --config docs/examples/deepseek-config.json
```

无子命令进入 REPL；:quit 退出，:cancel 取消当前运行。审批显示完整参数、目标和影响，输入 y 才批准；其他回答、EOF 或超时拒绝。也可另开终端使用 `jharness cancel --run UUID --workspace PATH`。

模型会收到准备后的系统提示、用户目标、获准读取的上下文、历史消息、工具结果和声明。命令环境仅注入授权变量，不传递模型 key。静态插件和本地命令用于受信任项目；CLI 默认本地运行，没有 OS 沙箱；M7 可通过 CLI ecosystem.environment 或嵌入式 Java SDK 显式选择 Docker。

## 验收、计划与恢复

run 的 --require-read PATH 校验本轮读取；--expect-file PATH 校验真实变化和当前文件 SHA。--verify-argv 接收 JSON 数组，配合 --verify-mode ARGV/CMD/POWERSHELL/SH 与 --verify-cwd（默认 .）校验修改之后最后一次成功命令。没有明确验收条件时，模型自述完成仍返回未验证。Windows Maven Wrapper 要显式使用 CMD；例如验收数组为 `["call mvnw.cmd -B -o test"]`。

--dry-run 不执行计划中的写入或命令；任意 Shell 标注无法预览。--dry-run --plan JSON 可离线预览明确工具调用，无需 API key。完整参数格式见 --help 与 [M4 契约](docs/development/M4-contracts.md)。

会话、产物、报告和控制状态保存在工作区 .jharness（Git 默认忽略）。报告分离事件事实、模型说明、未执行验证和未解决问题；交付事件保存报告 SHA 和源事件引用。

```powershell
.\jharness.cmd sessions list
.\jharness.cmd sessions inspect --session SESSION_ID
.\jharness.cmd report --session SESSION_ID
.\jharness.cmd diff
.\jharness.cmd verify --session SESSION_ID --call CALL_ID --outcome APPLIED --reason "已核对文件 SHA 和实际结果"
```

未确认副作用和未结算模型请求阻止自动续跑。verify 记录人工核实事实，不重放工具。未来版本与损坏日志默认只读诊断，不自动截断或迁移。M5 提供显式维护与恢复入口：

```powershell
.\jharness.cmd sessions query --session SESSION_ID --type tool.completed --status FAILED --limit 100
.\jharness.cmd sessions query --model MODEL --status FAILED
.\jharness.cmd sessions export --session SESSION_ID --output NEW_JSONL
.\jharness.cmd sessions fork --session SESSION_ID --event EVENT_ID
.\jharness.cmd sessions recover --session SESSION_ID
.\jharness.cmd resume --session SESSION_ID "继续目标"
.\jharness.cmd sessions migrate --session SESSION_ID
.\jharness.cmd sessions repair-tail --session SESSION_ID
.\jharness.cmd sessions verify-request --session SESSION_ID --event REQUEST_EVENT_ID --reason "已核对服务商；放弃缺失响应"
.\jharness.cmd sessions artifact --session SESSION_ID --artifact ARTIFACT_ID --offset 0 --limit 65536
```

查询按时间、工作区、模型、工具与状态过滤；无索引，逐个有界扫描冷日志。分叉独立写入、保留请求引用，复制校验后的冷产物。维护先备份再原子替换，中段损坏与未来版本拒绝修复。resume 检查通过后开启新轮次，未决工具与模型请求必须先核实；不自动重发。迁移仅支持明确的格式 0 导入规则。导出只包含事件日志，迁移主机时需一并保留产物和报告。完整行为见 [M5 契约](docs/development/M5-contracts.md) 与 [验收](docs/testing/M5-validation.md)。

run 返回码：0 声明的证据条件通过，2 失败/未验证，3 取消，4 预算/dry-run，5 配置/I/O，6 人工核实/只读，64 参数错误。cancel 独立返回码见 --help。

## M6 长任务与配置组合

通过配置 `streaming=true` 请求流式输出；路线需声明 `streamSupported=true`，否则显示降级并使用非流式。工具调用仅在整个 SSE 响应校验完成后进入执行管线；断流、取消或不完整 arguments 不执行部分工具。

`compactContext=true` 启用可替换的 BEFORE_MODEL 压缩插件，按 `compactionCharacters` 保守字符上限压缩完整历史组，记录移除范围、摘要与源事件，保持请求重建。默认摘要为内容摘录，未决工具与超量保护内容明确拒绝压缩。

新增 terminal_start/status/log/write/resize/stop 工具，支持真实 PTY 与后台任务；终端变更仍需审批与 durable intent。任务跨 REPL 多轮保持，退出时停止并保存终态/尾日志产物；冷恢复不接管或重启旧进程。未知终端用 `sessions verify-terminal --session ID --terminal-id TASK_ID --reason "实际核实依据"` 记录人工核实。

Profile/Preset/Bundle 选择与解析后 SHA 保存为 config.selected；模型路线惰性创建，按能力降级。例子：

```powershell
.\jharness.cmd --config CONFIG_JSON --profile coding run "完成任务"
.\jharness.cmd --config CONFIG_JSON --profile coding --model-route text-only run "解释文本"
```

[组合配置示例](docs/examples/m6-config.json) 使用占位域名，需替换真实 URL、模型与能力声明；key 仍只从环境获取。模型请求延迟、token 和结果元数据保存在 model.observed，报告分离这些事实与模型说明。详见 [M6 契约](docs/development/M6-contracts.md) / [验收](docs/testing/M6-validation.md)。

## M7 生态、执行环境与 Web/SDK

harness-ecosystem 提供本地/Docker 环境、版本化 SDK、MCP stdio/HTTP JSON+SSE、受控 Skills、只读子任务、Todo/Plan 和固定来源网页抓取。所有工具复用统一策略、审批、执行前落盘与恢复检查。子任务独立会话/预算/取消/费用池，父 Agent 是唯一工作区写者；默认串行，不自动并行模型工具调用。

CLI 的 ecosystem 配置和 serve 启动入口见 [配置与命令](docs/examples/m7-cli.md) / [配置示例](docs/examples/m7-config.json)。Web 后端固定 loopback、Bearer、持久幂等任务、取消/审批、事件分页/SSE 与有来源的历史 Diff；没有前端页面、公网或多租户支持。SDK 宿主示例见 [扩展装配](docs/examples/m7-extensions.md) 与 [Web 装配](docs/examples/m7-web.md)。

Docker 采用固定镜像摘要、非 root、只读根、禁外网、资源配额，文件/命令在同一环境；只挂载获准工作区和只读 worker。工作区整体可写，应使用不含秘密的专用目录；容器 PTY 不支持，失败不回退宿主。MCP/网页传输在宿主运行，其网络权限必须单独审核。

新增 sessions search/compare/archive 及环境/子任务/Web 离线核实。搜索是单会话 payload 全文子串索引，支持增量与损坏重建；归档保留日志/产物并禁止续跑。核实只追加外部事实，不重放或伪造成功。Todo 与 Plan 分别持久化，修改须审批，Plan 批准不能代替实际工具授权。

四组最终打包 JAR 的本地/真实 Docker 证据、全量测试和固定上游源码核验见 [M7 验收](docs/testing/M7-validation.md) / [收口与发布门禁](docs/testing/M7-closure.md)。Workflow、PTC、Goal/Schedule、搜索提供方/LSP、自动工具并行、Desktop UI 等已明确延期或部分支持，逐项决定见 [上游能力对照](docs/requirements/upstream-capabilities.md)。阶段收口不表示上游全产品复刻。远端 CI/macOS 实机仍待发布前补齐。
## 模块

| 模块 | 职责 |
|---|---|
| harness-api | API 1.9 服务、事件、会话、模型、工作区、策略和执行环境契约 |
| harness-events | 异步通知、串行事件、拦截链与诊断 |
| harness-plugin | 精确作用域注册、静态装配、依赖与生命周期 |
| harness-session | 内存/JSONL、单写者、查询、分叉、迁移、恢复、请求重建、检查点与产物 |
| harness-workspace | 路径边界、文件工具、版本保护、原子替换、Diff 与精确 apply_patch |
| harness-process | 显式 argv/Shell、有界双流、PTY/后台任务、滚动日志、清理证据与运行控制 |
| harness-policy | 精确命令模板、默认 ASK/DENY、可取消审批 |
| harness-tools | Schema、作用域、策略、审批、检查点和执行管线 |
| harness-agent | 串行 Loop、事件派生请求、压缩插件、预算、取消与完成判定 |
| harness-llm | JSON/SSE OpenAI-compatible、增量组装、惰性路由与请求元数据 |
| harness-ecosystem | 执行环境、Docker 工作程序、嵌入式 SDK、MCP/Skills 与只读 Subagent 插件 |
| harness-web | 本机认证 API、持久任务提交、取消、工具审批、事件分页/SSE 与历史 Diff |
| harness-cli | CLI、REPL、doctor、交互审批、恢复核实和报告 |
| harness-testkit | Fake LLM、故障注入、跨进程与跨模块验收 |

Loop 只依赖公共 API；CLI 通过 AgentLoopFactory 服务选择实现。插件以 register/events/own 注册服务、扩展和资源，关闭与失败回滚逆序撤销，贡献按精确 PluginScope 隔离。

## 范围与限制

- 文件只接受工作区相对路径；拒绝穿越、链接/junction、Windows 设备/ADS 和 .git/.jharness。已存在文件原子替换失败不降级，覆盖必须有版本依据。
- UTF-8 文件保留常见 BOM/CRLF，整文件上限 1 MiB；分页、扫描和产物有界。apply_patch 只支持精确创建/更新；多文件部分提交不伪称事务。
- JSONL 每条完整记录 force(true)，跨进程单写者，容量 32 MiB/10,000 事件。坏尾行默认拒绝，未来版本不可覆盖。
- 默认策略只自动批准完整只读 Git status 模板；其他命令和写入 ASK。Windows .cmd/.bat 必须显式 Shell。
- ProcessHandle 采样清理保留 partial 和观察范围；快速脱离的未观察进程无法保证清理，没有 Windows Job Object。命令输出/进程数/等待有界，PTY 已通过本机交互验证；任务不脱离 CLI/REPL 宿主存活。
- 串行事件请求超时中断，但 Java 不能强制停止不响应中断的插件。热重载、隔离类加载器延期；本地没有 OS 沙箱，Docker 边界见 M7 契约。
- 适配器明确禁用 thinking；支持有界 SSE，不支持 reasoning、图片。网络结果不明及 5xx 不自动重发；实时验收不配置重试。
- 自动化测试使用 Fake LLM/本地 HTTP；付费真实验收另行执行，固定隔离夹具和预算，不进入默认构建。

## M8.1 检索过滤

顶层 `searchFilter` 配置统一用于经典 CLI、生态入口/serve 和本地/Docker 环境：默认排除→根 `.gitignore`→用户有序规则，可分别关闭前两层。支持目录/glob/否定规则，`.git`、`.jharness` 和路径/链接保护不受覆盖；显式 grep 同样过滤，直接读取仍按原授权处理。过滤不是秘密隔离。具体语法、上限、SDK 构造与示例见 [检索过滤配置](docs/examples/m8-search-filter.md)。M8.1 已通过 Windows 269 项全量回归与真实 Docker 过滤对照/默认环境回归，见 [M8 验收](docs/testing/M8-validation.md)。M8.2 只读调度见下节，M8.3 模型限流/重试见下节，M8.4 已完成所选范围收口。

## M8.2 只读工具调度

顶层 `toolScheduling` 默认串行，显式 `readonly-parallel` 可启用宿主白名单内、注册 READ_ONLY 且策略直接 ALLOW 的工具并行。并发上限 1～8，结果按原调用顺序聚合，写入、命令和 ASK 审批先等待前面读取结算。取消/超时触发中断，仍未退出的工作线程记录 UNKNOWN；不重放未知结果。经典 CLI、SDK、生态入口/serve 共用调度插件，dry-run 与旧管线保持串行。配置、扩展及线程安全要求见 [只读调度](docs/examples/m8-tool-scheduling.md)，实际验证见 [M8 验收](docs/testing/M8-validation.md)。API/SDK 协议为 1.9，JSONL/schema 仍为 1；旧构造保留串行默认。

## M8.3 模型限流与重试

顶层 `modelResilience` 默认关闭；启用后在同一作用域内限制请求间隔和等待者数量，仅对明确收到的非流式429作有限退避。每次尝试独立准备、预留费用、落盘及校验请求；UNKNOWN、超时、断流及工具调用不重放。标准插件唯一拥有重试次数，避免与适配器内层重试叠加，取消和卸载可停止等待。配置、SDK兼容构造及限制见 [模型限流与重试](docs/examples/m8-model-resilience.md)；M8.4已完成所选范围收口，详见下节。

## M8 收口

M8.1～M8.4所选范围已完成：Windows与Linux容器各295项通过，零失败/错误/跳过，259个Java/POM哈希一致；七组最终JAR本地/真实Docker回归及清理通过。收口校验器绑定测试、源码、日志和制品，拒绝旧/不完整证据。验收矩阵、机器摘要与保留限制见 [M8收口](docs/testing/M8-closure.md)。API/SDK1.9，JSONL/schema仍1。

下一阶段为M9发布准备与平台验证。远端CI/macOS及正式发布门禁尚未闭合；本轮没有付费模型请求，Web前端和Desktop仍未实现。

## 开发记录

- [后续推进计划：M8～M12](docs/requirements/JHarness-next-plan-v1.0.md) / [PRD 需求状态审计](docs/testing/requirements-status-2026-10-15.md)
- [M8 契约](docs/development/M8-contracts.md) / [验收](docs/testing/M8-validation.md)
- [NFR / AC 开发与测试顺序](docs/requirements/JHarness-NFR-AC-sequence-v1.1.md)
- [M0 契约](docs/development/M0-contracts.md) / [验收](docs/testing/M0-validation.md)
- [M1 契约](docs/development/M1-contracts.md) / [验收](docs/testing/M1-validation.md)
- [M2 契约](docs/development/M2-contracts.md) / [验收](docs/testing/M2-validation.md)
- [M3 契约](docs/development/M3-contracts.md) / [验收](docs/testing/M3-validation.md)
- [M4 契约](docs/development/M4-contracts.md) / [验收](docs/testing/M4-validation.md)
- [M5 契约](docs/development/M5-contracts.md) / [验收](docs/testing/M5-validation.md)
- [M6 契约](docs/development/M6-contracts.md) / [验收](docs/testing/M6-validation.md)
- [上游能力对照](docs/requirements/upstream-capabilities.md)

## M9 发布准备（进行中）

已开始对三平台CI、启动器、第三方许可、SBOM/安全扫描及候选分发进行核实。用户提供的目标仓库为nixaveq141-ai/J-Harness，仓库已成功认证并连接origin、fetch main（私有仓库）；用户选择Apache-2.0。按最新指示本轮仅连接，本地发布准备暂停。M9尚未验收、正式发布未完成，进展与平台边界见[M9验证](docs/testing/M9-validation.md)。
