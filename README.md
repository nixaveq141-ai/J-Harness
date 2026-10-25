# J-Harness

2026-10-25：[R4 本地修复增量](docs/development/OCR-R4-remediation-2026-10-25.md)补强扩展表单、旧响应隔离、路由展示、报告独立预算与截断前脱敏。原115条索引已保留；R4仍在进行中，最终本地证据见[验收记录](docs/testing/OCR-R4-remediation-2026-10-25-validation.md)，不替代M10.5.9/M11准入。

2026-10-24当前任务：独立推进[M10.5.9整阶段验收](docs/testing/M10.5.9-validation.md)，按原始契约执行当前版本跨平台、三浏览器及制品门禁。OCR R4～R6整体完成不作为本次前置；已获专用分支推送和Actions授权，实际结果归档后再更新完成状态。

2026-10-23：[R4 工作台加固进展](docs/development/OCR-R4-progress-2026-10-23.md)已交付一批异步状态、密钥草稿、回答核对与展示修复；原115条范围仍在逐项复核，M10.5.9未完成。专用验证分支推送和 GitHub Actions 已获授权并执行；当前Windows专项定位及完整同提交门禁仍待通过。

[R3 模型、工具、扩展与配置修复](docs/development/OCR-R3-remediation-2026-10-23.md)已完成304条采纳范围的本地实现、契约澄清与关联验证：640个Java用例、170项门禁、33项客户端及66项SDK检查通过；Windows/固定Docker专项与真实Linux目录别名反例通过。下一入口R4，M10.5.9和完整CI门禁保持。

2026-10-23：2457 条 OCR 意见的核实与阶段计划见[核实报告](docs/development/code-review-2026-10-20.md)和[修复计划](docs/requirements/OCR-remediation-plan-2026-10-20.md)。R0/R1 已有本地修复证据，[R2 持久化与恢复修复](docs/development/OCR-R2-remediation-2026-10-22.md)已完成采纳范围的本地实现与验证（107条逐项台账，603个Java用例）。WebKit问答运行时断言仍失败，完整浏览器矩阵未通过，留R6定位；M10.5.9及M11准入仍按实际验收证据判断。

后续开发已按用户要求插入 **M10.5工作台完善 → M11 Desktop → M12扩展**。M10.5包含九个切片，当前.1～.8已完成本地验收，.9整阶段验收实施中，覆盖多轮交互、会话容量/管理、错误恢复、模型协议、提问、展示、扩展、检索/SDK及整阶段验证；详细顺序和验收见[M10.5计划](docs/requirements/M10.5-workbench-improvement-plan.md)。M10.5.1已实现执行诊断、只读核实交接和权限选择，已完成本地验收，见[本片记录](docs/testing/M10.5.1-validation.md)。

M10.5.2已支持工作区注册/切换、会话命名/置顶/分叉/导出/归档/恢复和分页，解除累计32条后不能新建的问题；使用见[会话管理](docs/examples/m10.5.2-history.md)。

内容检索支持授权工作区/会话、归档、词语/短语与来源读取；外部集成见[TS/Python SDK](sdk/README.md)和[检索说明](docs/examples/m1058-search-sdk.md)。

扩展设置支持可视化条目、显式技能目录与 `/技能名` 候选；MCP 支持受控文本资源和简单 URI 模板。详见[M10.5.7说明](docs/examples/m1057-extensions.md)。

M10.5.3现可在已完成会话底部继续对话，忙碌时排队或发送下一轮补充，支持取消排队和受理核对；重启后显式开始，详见[多轮说明](docs/examples/m10.5.3-turns.md)。

Web工作台新增「设置」入口：可添加/修改/删除模型连接、API URL和API Key并选择默认连接；密钥按用户选择保存在Windows凭据管理器，重启后可用。「插件与扩展」提供Planning、上下文压缩以及MCP/Skills等配置入口。详见[使用说明](docs/examples/web-ui-settings.md)与[增量验收](docs/testing/M10-settings-validation.md)。

基于 Java 21 的可扩展 Agent Harness，需求基线为 [PRD v1.1](docs/requirements/JHarness-PRD-v1.1.md)。M0～M7 的本地实现范围和验收见各阶段记录，M7 最新计数与源码版本见 [收口记录](docs/testing/M7-closure.md)；CLI、持久化与恢复、SSE、上下文压缩、PTY/后台任务、多模型路线与命名配置组合已贯通。真实 DeepSeek 验收及费用证据见 [M4 验收记录](docs/testing/M4-validation.md)。M9源码的Windows/Linux/macOS远端CI历史证据见[M9验收](docs/testing/M9-validation.md)。M10新增Web工作台已完成本地验收，见[M10记录](docs/testing/M10-validation.md)。

## 构建与启动

需要 JDK 21 并设置 JAVA_HOME。Wrapper 固定 Maven 3.9.9；首次构建下载依赖，自动化测试无需模型 key。

```powershell
.\mvnw.cmd -B verify
.\jharness.cmd --version
.\jharness.cmd --config docs/examples/deepseek-config.json doctor
```

Linux 构建为 `sh ./mvnw -B verify`，启动为 `sh ./jharness --help`；Windows 11 本机与 Linux Java 21 容器分别保留验收证据。verify 包含 JUnit、编译警告检查（-Xlint:all -Werror）、Spotless 和模块打包。修改 Java 后运行 `.\mvnw.cmd spotless:apply`。可直接运行 `java -jar harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar --help`。

## 浏览器任务工作台（M10）

Windows快捷入口：在项目目录双击 [`web-ui.cmd`](web-ui.cmd)。脚本自动启动本机服务、打开默认浏览器，页面自动进入工作台，无需输入本机连接凭据，也不再修改剪贴板。保持启动窗口打开，输入`:quit`停止。优先使用工作区已有`.jharness/config.json`，否则使用DeepSeek示例配置；模型Key沿用所选配置的环境变量，不写入启动脚本。详见[入口说明](docs/examples/web-ui-launcher.md)。

视觉和基础交互沿用DeepSeek Harness，名称与业务接入属于J-Harness；参考目录只读。支持连接、任务创建/列表/详情、事件时间线与有限SSE、审批/拒绝/取消、记录型Diff、事实报告和脱敏会话查询。完整说明见[前端说明](frontend/README.md)和[M10契约](docs/development/M10-contracts.md)。

源码构建需先安装Node24/npm并构建前端，再执行Java打包；运行打包后的JAR不需要Node：

```powershell
cd frontend
npm ci
npm run build
cd ..
.\mvnw.cmd -B clean verify
.\jharness.cmd --config docs/examples/m7-config.json serve
```

手动serve前设置JHARNESS_WEB_TOKEN，浏览器打开输出的127.0.0.1地址后自动进入工作台。模型密钥可在连接后的设置页保存到系统凭据库，也可继续使用配置指定的环境变量。浏览器自动取得仅在内存中使用的本机会话，刷新自动重连，不把凭据放进URL或Web Storage。页面不会因刷新/断线重提任务；UNKNOWN需按现有CLI流程离线核实。本机serve需保持宿主运行，EOF或退出关闭。当前浏览器证据不代表已完成新版本的远端三平台验证。

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

CLI 的 ecosystem 配置和 serve 启动入口见 [配置与命令](docs/examples/m7-cli.md) / [配置示例](docs/examples/m7-config.json)。Web 后端固定 loopback、Bearer、持久幂等任务、取消/审批、事件分页/SSE 与有来源的历史 Diff；M10新增同源浏览器工作台；公网或多租户仍不支持。SDK 宿主示例见 [扩展装配](docs/examples/m7-extensions.md) 与 [Web 装配](docs/examples/m7-web.md)。

Docker 采用固定镜像摘要、非 root、只读根、禁外网、资源配额，文件/命令在同一环境；只挂载获准工作区和只读 worker。工作区整体可写，应使用不含秘密的专用目录；容器 PTY 不支持，失败不回退宿主。MCP/网页传输在宿主运行，其网络权限必须单独审核。

新增 sessions search/compare/archive 及环境/子任务/Web 离线核实。搜索是单会话 payload 全文子串索引，支持增量与损坏重建；归档保留日志/产物并禁止续跑。核实只追加外部事实，不重放或伪造成功。Todo 与 Plan 分别持久化，修改须审批，Plan 批准不能代替实际工具授权。

四组最终打包 JAR 的本地/真实 Docker 证据、全量测试和固定上游源码核验见 [M7 验收](docs/testing/M7-validation.md) / [收口与发布门禁](docs/testing/M7-closure.md)。Workflow、PTC、Goal/Schedule、搜索提供方/LSP、自动工具并行、Desktop UI 等已明确延期或部分支持，逐项决定见 [上游能力对照](docs/requirements/upstream-capabilities.md)。阶段收口不表示上游全产品复刻。M9已补齐选定runner的真实三平台CI；平台边界见[M9矩阵](docs/testing/M9-platform-matrix.md)。
## 模块

| 模块 | 职责 |
|---|---|
| harness-api | API 1.14 服务、事件、会话、模型、工作区、策略和执行环境契约 |
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

上述为M8历史快照。M9发布验证已收口；M10 Web工作台已完成本地验收，Desktop留待M11。

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

## M9 发布验证（已完成约定范围）

[同一提交的三平台CI](https://github.com/nixaveq141-ai/J-Harness/actions/runs/38858529628)已通过：Windows Server2025 AMD64、Ubuntu24.04.5 x86_64、macOS26.6.2 arm64各305项，0失败/错误/跳过；完整测试名及260个Java/POM SHA一致。Windows本机同源码305项及重复JAR归档也通过。

项目采用Apache-2.0，第三方条款见[法律材料](third-party/README.md)。每侧18依赖OSV查询、5项离线门禁和实际ZIP安装/审批/取消/显式恢复/报告通过；原始归档、候选及法律文件哈希已交叉核对。候选版本仍为0.1.0-SNAPSHOT，正式Release未创建。

用法见[分发说明](docs/examples/m9-distribution.md)；详细结果、前轮失败修复及扫描/原生来源边界见[M9验收](docs/testing/M9-validation.md)、[平台矩阵](docs/testing/M9-platform-matrix.md)和[收口记录](docs/testing/M9-closure.md)。Windows10、独立WSL及其他CPU未新增认证。M10前端已完成本地验收，M11 Desktop尚未实施；M9结果不代表新源码已通过三平台CI。

M10本地验收：Windows11/Java21全量307项零失败/错误/跳过；客户端8项和真实Chrome28项通过。依赖审计、原始来源、最终JAR与UI哈希、截图证据见[M10验收](docs/testing/M10-validation.md)。新三平台CI、Firefox/Safari及Desktop未因此认证。

M10.5.2收口（2026-10-17）：工作区注册/选择、会话管理及32对象热集与持久历史分离已完成本地验收；Java331、客户端13、Chrome历史19/设置24/诊断13/工作台28、入口8项通过，276份Java/POM及最终同一JAR/UI证据见[M10.5.2记录](docs/testing/M10.5.2-validation.md)。下一步M10.5.3，.3～.9及M11门禁尚未闭合；未推送、未运行远端CI或付费模型。

M10.5.3收口（2026-10-17）：同会话多轮、独立受理/核对、有界队列/取消及下一轮补充已完成本地验收。Java337、客户端16、Chrome多轮15/历史19/设置24/诊断13/工作台28、入口8项通过；278份Java/POM及最终同一JAR/UI见[M10.5.3记录](docs/testing/M10.5.3-validation.md)。重启暂停队列，UNKNOWN及原离线核实限制保持；下一步M10.5.4，.4～.9及M11门禁未闭合，无付费请求、推送或远端CI。

M10.5.4原生凭据增量已实现Windows Credential Manager、macOS Keychain、Linux Secret Service/libsecret路径；Windows/Linux/macOS原生持久化已通过[三平台CI](https://github.com/nixaveq141-ai/J-Harness/actions/runs/39014489141)，Windows旧/新凭据兼容及本机Java338/Chrome设置26通过；这组CI只覆盖原生凭据专项。Linux需要已解锁的默认凭据集合和用户D-Bus会话；服务缺失/锁定可使用环境变量，不回退明文文件。该次CI只证明原生凭据路径；M10.5.4模型选择/逐轮快照/多协议已于2026-10-18完成本地验收，见[当前验收](docs/testing/M10.5.4-validation.md)。

M10.5.4于2026-10-18完成本地验收（Java358、客户端19、Chrome123、启动器8、Windows原生7）：输入区可选择模型连接，下一条消息与当前轮模型分别显示；每条消息在受理时冻结协议/能力/预算。设置新增每连接能力、Responses推理参数与仅本地的配置检查；文本/工具支持Chat Completions、Responses、Anthropic Messages及SSE。API/SDK1.10、JSONL1、Web目录备份升级4；使用方法及支持边界见[模型连接说明](docs/examples/m1054-models.md)，最终验收状态见[M10.5.4记录](docs/testing/M10.5.4-validation.md)。

M10.5.5主动提问已完成本地验收：模型可通过ask_user提出单选、多选或文字问题，Web卡片支持明确回答/跳过、草稿刷新恢复与丢响应核对。推荐项不自动提交，回答不授权工具；重启后未完成等待显示中断，不自动续问。Java371、客户端22、Chrome149、启动器8、Windows原生7通过；API/SDK1.11、JSONL1/Web目录4。见[提问说明](docs/examples/m1055-questions.md)及[验收记录](docs/testing/M10.5.5-validation.md)。该条为M10.5.5收口时快照，最新入口见下方。


M10.5.6已完成本地验收：对话/安全Markdown与代码复制、工具/变更卡、有界临时流式、实际用量、文件树与行号预览、产物/大事件分页下载及记录Diff与当前文件区分。Java388、客户端27、Chrome194、入口8及Windows原生7通过；API/SDK1.12。见[使用说明](docs/examples/m1056-content.md)与[验收记录](docs/testing/M10.5.6-validation.md)。下一入口M10.5.7，整阶段及M11/M12尚未完成。

2026-10-18 M10.5.7已完成本地验收：MCP/Skills/只读子任务条目表单与高级JSON互通、显式项目/用户技能根有界扫描/frontmatter/快照查看及slash候选、MCP文本资源/单变量模板发现读取、持久实际工具清单已交付。脚本/子任务仍独立审批，缺MCP凭据受理前拒绝，连接装配异常保留UNKNOWN且禁止修改/重试。最终Java399、客户端29、Chrome229（扩展35+既有194）、启动器8、Windows原生7通过；297份Java/POM及最终同一JAR/UI见M10.5.7-results.json和validation.md。API/SDK1.13、JSONL1/Web目录4；原生worker未改，历史三平台凭据证据独立保留。无付费请求、推送或参考项目修改；保留原审查草稿。下一入口M10.5.8，整阶段及M11/M12未完成。

扩展配置与边界见[使用说明](docs/examples/m1057-extensions.md)、[契约](docs/development/M10.5.7-contracts.md)和[验收记录](docs/testing/M10.5.7-validation.md)。

2026-10-18 M10.5.8已完成本地验收：授权跨会话/工作区正文检索、词语/短语/相关性/归档/稳定分页及来源定位、有界可替换派生索引、独立stdio JSON-RPC1.0与TS/Python客户端和示例已交付。超时/断线先对账，问题回答不批准工具，UNKNOWN不重放。最终Java410、客户端31、Chrome257（检索28+既有229）、SDK58、文档示例2、启动器8、Windows原生7通过；305份Java/POM与最终同一JAR/UI见M10.5.8-results.json及validation.md。API/SDK1.14、JSONL1/Web目录4；原生worker未改，旧三平台凭据证据独立保留。无付费请求、推送或参考项目修改；保留原审查草稿。下一入口M10.5.9，整阶段及M11/M12未完成。

M10.5.9当前本地验证、三平台CI准备和保留门禁见[验收状态](docs/testing/M10.5.9-validation.md)。本轮专用验证分支推送/CI已获授权并执行11轮完整运行；六轮Windows专项完成剩余浏览器定位，第十一轮Linux暴露MCP清理根Process回收缺口。修复后的三平台进程专项各31项、本地649项全量已通过；正在复验Windows Chromium回执采集单组，随后完整同提交验收，完整门禁闭合前不进入M11。R4～R6另按OCR范围继续，不作为本次整批前置。

2026-10-19 M10.5.9实施中、本机部分验收和CI准备完成：Java410/客户端33、Chrome155与WebKit26.5各271项共542、SDK58、示例2、启动器8及Windows原生7通过，候选真实安装/审批/取消/显式恢复5项通过；Maven18依赖OSV、前端6运行依赖SBOM/npm审计0发现、SDK零外部依赖源码清单及秘密模式门禁已核对。跨引擎夹具保持断言并修正异步等待，受控断网/停机的只读诊断单独留痕。Firefox原包14001，隔离清单副本仅版本探针成功，实际无头启动仍失败；不计通过。证据M10.5.9-results.json/validation.md为LOCAL_PARTIAL_CI_PENDING、m11Eligible=false；三平台/三引擎严格CI工作流已准备但未授权推送/未运行，不能收口或进入M11。下一步取得对应推送/CI授权后在当前版本真实Windows/Linux/macOS运行并解决失败。保留原审查草稿，参考目录未改，无付费模型请求。
