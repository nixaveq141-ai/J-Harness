# M7 执行环境、SDK 与生态入口契约

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](current-state.md)及[全阶段审计](../testing/M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

日期：2026-10-15。阶段状态：按已记录范围完成本地验收与收口。实现包含：执行环境/SDK、MCP/Skills、只读 Subagent、CLI/Web、Todo/Plan、搜索归档和离线核实。验收状态见 M7-closure.md；延期与部分支持见 upstream-capabilities.md。公共 API / SDK 协议为 1.7；JSONL 格式和事件 schemaVersion 保持 1。

## 执行环境

`harness-ecosystem` 提供 `LocalExecutionEnvironment`、`DockerExecutionEnvironment`、`EnvironmentPlugin` 和 `HarnessSdk`。文件、命令及 cwd 统一从传入的 ExecutionEnvironment 获得，工具路径始终为相对 ExecPath。CLI 默认本地，ecosystem.environment 可显式选择 Docker，SDK 宿主也可直接装配。

Docker 只接受预先安装、带 sha256 摘要的镜像，运行时 `--pull=never`。宿主明确传入 Docker 可执行文件、工作区与包含 EnvironmentWorker 的打包 JAR。容器采用无外网、只读根目录、非 root UID/GID 65534、移除全部 capabilities、no-new-privileges、512 MiB 内存、1 CPU、64 PID、64 MiB 临时目录；只挂载授权工作区和只读工作程序，不挂载 Docker socket 或宿主 home。

授权工作区整体可写，Shell 可以访问其中的所有内容；Java 文件工具的元数据路径保护不能约束 Shell 读取该挂载内的秘密文件。调用者应使用专用、不含秘密的任务目录。这是 Docker 容器边界，不宣称对宿主管理员、内核漏洞或 Docker Engine 的完全隔离。本地环境描述明确为 isolation=none。

文件与命令请求通过有界 JSON stdin 传入容器中的 Java 工作程序。Shell 文本不会作为 Windows Docker argv 传递，保留嵌套引号。工作程序采用既有 LocalWorkspace / LocalProcessRunner，因此保留路径、版本保护、显式 Shell 模式、输出上限及超时语义。控制传输输入/标准输出上限 4 MiB、stderr 上限 64 KiB，并有取消/截止时间。

文件读取返回与内容一致的版本，跨工作程序调用保留观察版本。未读的现有文件不能覆盖；外部变更拒绝编辑/覆盖。补丁后保守保留旧缓存，后续需重新读取发生变化的文件或给出明确版本，不能据此允许无依据覆盖。

命令或文件变更的控制传输不确定时，不重试，移除本实例独占、随机命名的容器，并返回 UNKNOWN / ENVIRONMENT_OPERATION_UNKNOWN。只读文件 RPC 中断返回 ENVIRONMENT_READ_INTERRUPTED，不回收父容器；工作程序的读取仍受既有扫描上限和期限约束，真正的容器所有者负责最终回收。清理结果不确定则抛出 CONTAINER_CLEANUP_UNKNOWN。只清理本实例名称，不能按历史 PID 或宽泛容器筛选杀进程。容器 PTY/后台终端暂不支持：不向模型注册这些工具，服务接口返回 UNSUPPORTED_TERMINAL，禁止回退宿主终端。

## SDK 所有权与入口

`HarnessSdk` 由宿主提供 SessionRepository、ArtifactStore、ExecutionEnvironment、LlmClient、DataSanitizer、PolicyEngine、ApprovalService、Budget、CompletionEvaluator、ApiCostGuard 及可信插件列表。启动即占有跨进程 EXCLUSIVE 写锁；不接管非 SDK 创建的旧日志。workspaceBinding 由可信宿主提供，必须稳定、唯一对应实际任务目录；重开时校验绑定及环境描述（容器随机实例 id 除外）。改变环境类型或镜像需新会话，不伪造历史环境。

`run(goal, token)` 同步且串行；`cancel()` 可并发调用，一旦取消不隐式复位。`snapshot()` 返回版本、会话、状态及事件事实。模型自述不作为完成判据，宿主必须提供真实证据 CompletionEvaluator。SDK 复用 PluginContainer、DefaultAgentLoop、DefaultToolPipeline、DefaultPolicyEngine 与 durable intent，模型声明 READ_ONLY 不能降低注册工具的副作用等级。宿主可加入可信插件；每会话独立 registry 与精确 scope，停止会撤销贡献。

运行前对冷日志做 SessionRecovery 检查，UNKNOWN、未决模型/工具或终端均阻止继续。SDK 不会重发历史请求。全部写入经过 SanitizingSessionWriter；模型及环境由 SDK 在关闭时释放，存储和产物对象归宿主所有。环境初始化、镜像安装及 MCP 连接配置属于可信宿主配置，不能成为模型传入的任意宿主操作。

关闭失败追加 environment.closed(status=UNKNOWN) 并使恢复阻止；成功关闭记录 SUCCESS。`HarnessSdk.verifyEnvironment` 在独占写锁下追加有理由的 environment.verified 操作者事实，必须先在外部实际核实清理，方法本身不执行、重试或清理容器，不将任务改为完成。

## 当前退出范围

AC-16 的文件/命令/cwd、SDK 同管线场景及最终打包 CLI/Web/Docker 联合链路已有真实 Docker 证据。MCP 使用真实 stdio 与本地 HTTP/SSE；Skills 和只读子任务另有本地/容器对照。CLI 生态装配、Todo/Plan、搜索归档、固定网页来源和离线核实均已有专项，Windows/Linux 各 260 项回归通过。模型为 FakeLlm 或本机 HTTP 替身，没有付费请求；不表示在线 MCP 服务商联调。子任务写入/命令、容器 PTY、远端 CI/macOS 等未实现或未验证边界仍保留，范围决定及发布门禁见 M7-closure.md。

## MCP 基础接入（FR-19.1、19.2）

参考 MCP [2025-11-25 传输](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports)、[生命周期](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle) 与 [Tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools) 的固定版本实现，不自动跟随协议升级。

`McpPlugin(serverId, transportFactory, effects)` 是宿主提供的可信配置。只协商 2025-11-25，声明空客户端能力，初始化后通知 initialized，再做有界分页发现。服务端 instructions 不成为系统提示；sampling、roots、elicitation 与异步 tasks 均未开放。插件只用一次，按精确 scope 注册/撤销；失败回滚关闭连接，不能在另一个 Agent 重用同一个插件实例。

`StdioMcpTransport` 是明确选择的宿主进程，UTF-8 换行 JSON-RPC、忽略 stderr 内容、最小进程环境，不继承模型 key；stderr 生命周期总量超过 8 MiB 关闭连接。子进程及最多 64 个已观察到的后代被停止，属于采样清理，不宣称进程沙箱。不能把宿主 stdio 进程当成 Docker 内进程。

`HttpMcpTransport` 使用固定 HTTPS 端点，明文仅允许 localhost/127.0.0.1/::1；拒绝 URL 内凭据和 fragment，禁止重定向。Bearer token 由宿主 Supplier（通常读独立环境变量）提供，连接不会主动把 token 写入声明、事件或日志；宿主须将 MCP token 纳入同一 DataSanitizer 的已知秘密，覆盖服务端回显。POST 接收 JSON 与 SSE，保存会话头及协议版本头；正常关闭尝试 DELETE（允许服务器返回 405）。服务端请求仅回答 ping 或方法不支持，不执行额外宿主能力。

有意限制：不实现 OAuth 授权发现、旧 HTTP+SSE、GET 常驻流、SSE 续传/重连、流断开后的轮询、自动会话重建或调用重放。404/过期、截断、超时、取消、错误关联 ID、无效结果及观察到工具变更通知时保留 UNKNOWN，关闭连接并停止安全恢复。宿主核实后显式创建新连接，不自动重发 tools/call。关闭连接或发送 DELETE 不证明远程副作用被撤回。

远程声明经既有 JsonSchemaValidator 的明确子集检查，不支持的关键字（包括 $ref / $schema）使装配失败，禁止静默忽略。入参和声明的 structuredContent 出参均校验。工具发现最多 8 页、128 个工具，总量 4 MiB；单次传输总量 4 MiB、128 消息、JSON 100000 token/64 层、单 RPC 60 秒上限。一次结果最多 128 内容块；非文本媒体作为未信任 JSON 呈现，不宣称模型图像/音频能力。

工具名为 mcp_SERVER__TOOL；含点名称改为可读名称加 SHA256 前 12 位，检查别名碰撞。serverId 最多 16 位，远程工具名最多 40 位且限字母/数字/下划线/短横线/点。源信息包含宿主 serverId、原始工具名和传输种类；描述与返回内容标记未信任。默认副作用为 SYSTEM_IMPACT，服务端 readOnlyHint 不能降低权限。只有宿主 effects 表可明确降低；未知覆盖名称装配失败。注册工具仍经正常 Schema→策略/审批→durable intent→执行，检查点失败不发送调用。服务端 JSON-RPC 错误或 isError 结果记录失败，不等同事务回滚。

MCP 是显式外部能力：Docker 的 network=none 限制容器命令，不限制宿主选择的 HTTP MCP 或宿主 stdio。连接的服务端必须由宿主可信配置选择，其权限和运行位置应单独审查；不能宣称容器隔离自动约束外部 MCP。

## Skills 受控文件规范（FR-19.3）

`SkillsPlugin.Spec` 由宿主指定 id、选定执行环境内的相对目录、资源别名和脚本别名。该目录必须有 SKILL.md；Markdown/YAML 只作为未信任内容，不从其中生成资源授权、进程环境、工具或脚本入口。SDK 使用 extensions 列表接入，无自动扫描安装或 CLI 配置入口。

所有加载通过同一 ExecutionEnvironment.files，拒绝越界、链接、保留元数据及 .env/.env.*。每个文件最多 64 KiB UTF-8、32 个资源和 16 个脚本、总量 512 KiB；启动时保存一致版本和 SHA256。manifest/resource 返回固定快照，不伪装为当前文件；要刷新必须新建插件。加载结果记录来源和未信任标记，不自动注入系统角色。

可见工具为 skill_ID__load、skill_ID__resource（仅枚举允许别名）和 skill_ID__run_SCRIPT。脚本仅允许宿主明确指定的 CMD/POWERSHELL/SH 模式和超时；不存在任意 argv、额外参数、环境变量或技能内容自行开放入口。Script 工具始终 SYSTEM_IMPACT，完整审批参数包括路径、cwd、模式、revision；全部为固定 const。宿主审核时可按路径读取正文并核对 revision，摘要本身不能代替内容审查。

执行前重新检查源文件版本；变更则失败且不启动进程。执行的是已加载的固定文本快照，因此版本检查后的文件替换不会改变被批准内容；命令仍由同一环境 ProcessRunner 执行并沿用超时、取消、输出上限和 UNKNOWN 语义。所有脚本调用必须经过既有策略、审批和 durable intent，不能通过 load/resource 执行。停止后撤销贡献，持有旧工具引用也不能继续执行。

较长 SDK 集成夹具使用明确声明 32768 上下文的 FakeLlm；默认 8192 及生产上下文保护保持不变。技能内容与返回元数据占用上下文，大技能在不足容量时明确 CONTEXT_LIMIT，不能绕过该限制。

装配示例见 [m7-extensions.md](../examples/m7-extensions.md)。

## 只读 Subagent 委派（FR-20）

本切片采用“父 Agent 单写者、子 Agent 只读探查”的冲突策略。共享的是同一执行环境的文件位置；子任务没有文件变更、命令、终端、git_diff、MCP、Skills 或递归委派能力。没有跨 Agent 合并修改、工作流或 Teams。不能将其描述为可并行修改代码的通用子 Agent。

`SubagentPlugin` 通过 SDK extensions 装配，宿主指定最多 16 个 Profile（提示词、工具白名单、独立 Budget、每次新建的 LlmClient 工厂、ApiCostGuard、CompletionEvaluator）。可选工具仅 list_directory/read_file/search_files/grep/read_artifact。没有子任务验收条件时默认未验证；模型自述和一次无关读取不能代替具体任务条件。父任务仍由独立的父 CompletionEvaluator 判定，不因子任务完成而自动完成。

`delegate_task(profile, goal)` 为 SYSTEM_IMPACT，先走父 Schema/作用域、父策略/人工审批与 durable intent，之后强制落盘 subagent.started，才启动子模型/会话。没有绕过管线的公开 launch/submit 方法。模型不能指定工作区、服务、key、模型工厂、权限、预算或随意新增 Profile。结果同步等待；现有串行 Loop 不自动把一组工具调用变成并发。

每个委派创建独立 child-* Session、agent-* PluginScope、过滤后的注册表、事件总线和写拥有权。只传显式子目标与宿主 Profile 提示词，不复制父消息、监听器或插件贡献。子任务仍受父当前有效 PolicyEngine（包括父策略扩展点）的约束，并再加只读白名单限制；模型钩子等其他父监听器不向子任务传播。禁止重用父模型或同一个子模型对象，避免上下文、预算和关闭所有权串用。

`WorkspaceFiles.isolatedView()` 是兼容性默认能力；未实现的提供方明确返回 UNSUPPORTED_WORKSPACE_VIEW，不回退其他环境。LocalWorkspace 与 RemoteWorkspace 为同一文件位置建立独立观察版本缓存。子读取不能替代父读取/版本守护；父编辑仍需自己的读取或明确 expectedVersion。子环境只借用父资源，关闭的是视图与子模型，不关闭父容器/终端；取消或关闭后旧视图拒绝新读取。根目录替换为链接/junction 时，原视图和新视图均拒绝重新绑定外部目录。目录扫描保留逐段检查，并在扫描前后验证根边界，以维持原两秒性能标准；Java 检查仍不宣称消除了全部路径竞态或成为 OS 沙箱。

每个父插件最多 1～4 个活跃任务、最多 32 个保留任务；超额拒绝、不排无界队列。宿主若在同一父 turn 经既有工具管线并行调用，可并发只读探查；不得把独立的受信宿主调试调用与新 SDK run 交错。`SubagentService` 仅提供本父作用域的 tasks/status/cancel，停止后旧服务和工具引用失效。父是唯一写入方，子任务之间不会修改同一文件；父变更与读取竞态沿用文件版本校验。

子 Profile 的步数和时长不得超过父配置，最大 64 步/5 分钟；父取消/截止时间与子时限组合传播。步数是每个 Agent 的局部限制，不宣称全树共享步数计数；总委派次数另有上限。每个父 turn 的费用保守预留由父模型和全部子模型共享；父有费用上限时子必须同币种、提供不高于父的费用上限与可靠价格界限。未知结果不退还预留；未传输但已预留的请求也可保守占用额度，不能称为实际账单。sdk.turn.configured 记录预算配置，子配置及来源另在分配事件中保存。父新 turn 仅在恢复检查通过后重置本 turn 费用池。

子任务先关闭 SDK/写者，随后保存父作用域的脱敏结果 Artifact，并记录 subagent.completed；报告包含父/子标识、父调用 ID、预算、状态、子日志末事件、成功工具事件引用、未信任摘要与产物 SHA。subagent.origin 反向引用父分配事件，父子各自的 model.requested 均可从各自日志前缀重建。子摘要仅作为未信任 tool 结果，不作为系统指令。

取消先传播 token，给子任务最多两秒收尾；对不合作提供方尝试中断和关闭模型，但不承诺能杀死 JVM 线程或撤回远程请求。仍未停止、关闭失败、未决模型请求/副作用或结果保存失败均保留 UNKNOWN，禁止重放。并发名额直到真实 worker 结束才释放。父冷恢复将未结束/UNKNOWN 委派列为 subagent:TASK_ID；不能仅因父工具日志完整就继续。

`SubagentPlugin.verify(repository, parentId, taskId, reason, sanitizer)` 只记录操作者的外部核实事实：父写锁必须可获，已存在的子写锁也必须可获，子日志必须先完成既有请求/副作用核实，并校验已调用模型的来源关联。之后追加 subagent.verified；需要时为父未知委派追加 tool.verified(outcome=PARTIAL)，不改写旧 SUCCESS、不重放、不启动子任务。没有子日志的启动前失败不会由该操作创建子会话。该方法不证明任意宿主客户端线程已终止，操作者仍需核实真实资源。

Subagent 仍由可信 SDK extensions 装配；Web 宿主也可在自己的 SDK 工厂中选择插件，客户端不能创建 Profile 或改变插件配置。CLI 通过 ecosystem.subagents 装配受信 Profile；前端界面未实现。

## 本机 Web 后端（FR-27.4）

独立 `harness-web` 模块提供嵌入式 `HarnessWebServer`，使用 JDK HttpServer，固定绑定 127.0.0.1，支持宿主指定端口或随机端口。它是单操作者的本机 API，不是公网部署、多租户或前端页面。需要访问远端、TLS、浏览器跨域或多用户授权时另行实现，不能直接把该服务暴露到外网。

每个请求（含列表、事件和审批）都需要 Bearer。宿主从环境变量或秘密管理器取得 32～256 位字母/数字/下划线/短横线 token，使用常量时间摘要比较；无认证 401，Host 必须精确匹配 address() 返回的本机地址，所有 Origin 请求拒绝。无 CORS、cookie 或 URL token。响应 no-store/nosniff，JSON 使用 CSP；连接用后关闭，避免被拒正文影响后续请求。响应不含原始异常、请求 URL 或凭据。

宿主传入最多 16 个固定 Profile，指定 workspaceBinding、SessionRepository 和 SdkFactory。工厂必须使用传入的 sessionId、ApprovalService、DataSanitizer 创建 HarnessSdk，使用对应仓库和同一绑定；模型/环境由 SDK 拥有，创建失败时工厂清理尚未移交资源。传入 sanitizer 已组合宿主脱敏与 Web token 脱敏，应同时用于 SDK 和 ArtifactStore。工厂是可信宿主代码，服务无法约束恶意工厂；请求不能提供目录、镜像、命令、模型 key、策略、预算或任意插件。工作区绑定必须唯一对应真实目录，不能用别名规避同目录占用。

任务入口 POST /v1/tasks 仅接受 profile、goal（1～16384 字符）和 requestId（1～64 位字母/数字/下划线/短横线）。返回 202 和随机 web-* taskId。每个任务仅运行一次，SDK 负责模型/工具的同一审批与 durable intent；任务工厂不在 HTTP 线程运行。最多 1～4 个占用工作区、最多 32 个保留任务，不排队；同一 workspaceBinding 的不同 Profile 也不能同时运行。超额 429、工作区冲突 409。已完成任务仍计入保留任务上限，不能自动删除幂等记录腾位置。

独立目录中的 Web catalog 使用 EXCLUSIVE 写锁与真实 durable writer。web.task.accepted（任务、Profile、绑定、请求 ID/目标 SHA256）成功 append+flush 后才启动工厂；失败返回 503、停止本实例后续提交，不尝试重放。相同 requestId/原始目标/Profile 返回原任务，不再次调用工厂；内容改变 409。正常执行和 SDK 关闭后追加 web.task.finished。服务重启读取原 catalog，既有任务只能查询或命中幂等记录，不自动重开 SDK；没有已确认结束的任务显示 UNKNOWN，并占用原绑定，等待宿主外部核实。catalog 标识和目录需要稳定保存，换 catalog 是建立新的幂等域，不得用来绕过未知操作；提供下文所述 CLI 离线核实；没有 HTTP 在线核实/恢复/归档接口。

任务 phase 是入口/资源状态（STARTING/RUNNING/CLOSING/FINISHED/UNKNOWN），status 是最后 SDK 结果；只有 phase=FINISHED 才表示运行与关闭均结束。SDK 日志存在未决模型请求、UNKNOWN 工具/环境/子任务等时 phase=UNKNOWN，不因为 taskStatus 已结束就释放绑定。取消先传播 token，不隐式重置。关闭停止 HTTP、取消所有任务，最多两秒等待后中断；任意不合作工厂/模型仍可能存活，catalog 保留未确认记录，不能宣称已停止或资源回收成功。单实例绑定占用不是跨不同 catalog/进程的全局目录锁。

| 方法与路径 | 语义 |
|---|---|
| GET /v1/tasks | 当前 catalog 的有限任务列表 |
| GET /v1/tasks/TASK | phase/status、取消与恢复事实，不含凭据/目标正文 |
| POST /v1/tasks/TASK/cancel | JSON {}，请求取消，不承诺撤回远程操作 |
| GET /v1/tasks/TASK/events?after=EVENT&limit=N | 稳定落盘事件游标；1～100 条，未知游标拒绝 |
| GET 同上，Accept: text/event-stream | 有界 SSE 订阅；id 为原事件 ID，支持 Last-Event-ID，不能同时提供 after |
| GET /v1/tasks/TASK/approvals | 仅当前待决项，含随机 approvalId、callId、父/子作用域及精确工具参数 |
| POST /v1/tasks/TASK/approvals/APPROVAL | approved、reason、callId；必须匹配当前任务和调用，过期/重复/取消/错关联均 409 |
| GET /v1/tasks/TASK/diff | 最近已记录 git_diff 工具结果与源 eventId；缺少记录 404，不另行执行 Git |

审批最多等待 60 秒、每任务最多 8 项，超时/关闭/取消拒绝；HTTP 返回 accepted 只说明决策被接收，实际工具结果以日志为准。审批仍由既有管线记录 approval.requested/decided，经过取消复查、tool.intent 和检查点后执行；不能凭 HTTP 决策直接调用工具。人工 actor 固定为 web-operator，该共享 token 不区分多位用户。

请求必须是 application/json、显式 Content-Length，不接收 chunked；正文最多 64 KiB、JSON 24 层/8192 token，拒绝重复键、尾随数据和额外字段。每请求从进入处理器起最多 10 秒；HTTP 工作线程 8、等待队列 16。最多 2 个 SSE，同时保留其他线程用于审批；每次订阅最多 5 秒/100 条/512 KiB，然后关闭，客户端可显式按事件 ID 续读。断开只停止传输，不取消或重复运行任务。队列满可能直接断开连接，不能将断开解读为提交未发生。

JSON 总响应最多 512 KiB；单条事件 payload 脱敏后超过 64 KiB 时保留事件身份/关联和 omitted/SHA256，游标仍可推进。完整日志留在宿主仓库，不把有限 Web 页当作完整审计导出或请求重建输入；重建仍用原持久日志。事件页底层沿用现有最多 32 MiB/10000 条的会话读取实现，不宣称数据库级增量读取或无限历史订阅。Diff 是有来源的历史工具快照，不是实时工作区 Diff；大型 Diff 同样可能省略。

装配与调用示例见 [m7-web.md](../examples/m7-web.md)。CLI serve 启动同一后端，装配见 m7-cli.md；没有页面或认证用户管理。Web 专项使用真实本机 HTTP/SSE、真实文件/Git 和 FakeLlm，联合打包 CLI/Docker 证据见 M7-web-docker-results.json，未联调在线 MCP 服务商。

## CLI 与可信生态配置

配置含 ecosystem 时 run/resume/repl/serve 通过同一个 HarnessSdk 装配。environment、mcp、skills、subagents、planning、webSources、web 均由宿主配置，严格拒绝未知字段和越界数量；地址、命令、凭据环境变量引用不能由模型提供。CLI 默认串行，config.selected 记录最终组合及串行原因。现有模型路线/提示词/参数/工具白名单、只读策略、上下文压缩、验收条件和取消沿用；没有任意插件类加载配置。CLI 依赖 harness-web，并将其打入 shaded JAR。

SdkConfiguration 提供可信提示词、精确工具白名单和模型参数快照，旧 SDK 构造和 SdkOptions 五参数调用保持兼容。已知 key/token 由环境获得并统一脱敏，发现配置中出现已知秘密字面值时拒绝。AGENTS.md 从选定执行环境读取并以不可信项目说明注入，不能开放权限。生态入口不支持交互 dry-run；离线 --dry-run --plan 仍在装配前预览，不启动模型/环境。

子 SDK 仅借用父 ApprovalService 的 decide 能力，关闭子任务不得关闭父审批宿主。CLI 对已回答 approvalId 去重，避免后续输入被旧提示消费。父子分别使用模型实例；子模型保留父参数和费用上界但强制非流式，费用仍由父 turn 的共享池保护。CLI 证据报告及 delivery.registered 保留源事件和 SHA。

serve 固定单个 Profile default 和当前工作区/配置，端口/tokenEnv/catalogId/审批时限/保留任务上限为宿主设置；:quit、EOF、宿主退出均进入关闭流程。没有公网服务或页面。工作区占用限于同一个 Web catalog，不是跨 catalog 全局互斥。命令及完整配置示例见 [m7-cli.md](../examples/m7-cli.md)。

## Todo 与 Plan（FR-23）

PlanningPlugin 通过当前作用域 SessionReader/Writer 重建状态。todo_list、plan_view 是只读；todo_update、plan_update、plan_transition 都为 SYSTEM_IMPACT，经统一策略/审批/intent 后 append+flush。失败或 UNKNOWN 不隐式重放，停止后旧工具失效。

Todo 是逐项清单：每项稳定 ID、title、revision，状态 PENDING/IN_PROGRESS/DONE/REMOVED；最多保留 128 项，移除项仍保留事实，title 最多 1024 字符。修改需匹配当前 revision，DONE 必须引用一个实际成功的非计划工具事件。可重新更新状态，清单不自动执行步骤。

Plan 是单会话当前结构化计划：最多 32 步、每步最多 1024 字符及单调 revision。未创建时 NONE；更新成为 DRAFT 并清除旧批准/完成引用，DRAFT→APPROVED→EXECUTING→COMPLETED，非终态可 ABANDONED。完成需每步一个不同的成功非计划工具事件引用；事件引用证明有实际工具事实，不自动证明每步语义正确。偏离时先更新成新 DRAFT，重新批准。批准计划不能授予文件/命令权限。todo.changed 与 plan.changed 是各自的持久事实，不混为同一状态机。

Goal 与 Schedule 本阶段明确延期：没有长期 Goal 状态、模型工具、定时仓库或后台调度生命周期；turn 目标不冒充 Goal，时限不冒充 Schedule。将来实现需独立状态机/事件/无人审批策略，不能自动批准高风险工具。

## 会话检索、比较与逻辑归档（FR-24）

SessionSearchIndex 是可删除的派生缓存：单会话完整 payload JSON 按大小写不敏感子串查找，text 1～256、limit 1～100、offset 非负；继承源日志 32MiB/10000 事件上限。缓存最多 96MiB，保存事件 ID/类型/时间、payload SHA 与全文，封装 SHA；校验现有前缀后增量追加，损坏/版本不匹配/显式 rebuild 时重建。严格 JSON、独占 .search.lock、原子替换与 force，不修改事实 JSONL。不是 FTS5、分词、模糊搜索或跨会话排名。snippet 最多 512 字符。

sessions compare 对照事件公共前缀、状态与计数，不做语义合并。archive 在独占且已结算的日志追加 session.archived，保留所有日志/产物，不自动删除；classic CLI 与 SDK 均拒绝归档后续跑。可从归档事件之前的检查点分叉为新任务。Catalog query 支持 SDK workspaceBinding 并展示 archived；list 不隐式隐藏归档。

单写者同时使用 JVM 内路径占用表与 OS 文件锁。先拒绝同 JVM writer/maintenance 竞争，再打开锁通道；防止 POSIX 上关闭失败竞争者通道释放原进程锁。关闭资源后才释放占用表，失败装配也释放；搜索索引采用同样的进程内竞争保护。原跨 JVM 冲突断言及新增维护竞争断言在 Windows/Linux 分别验证。

## 可信固定来源网页（FR-26）

WebFetchPlugin 只接受最多 16 个宿主别名→固定 URL，HTTPS 或明确 loopback HTTP，不接受凭据/fragment，不跟随跳转。web_fetch 只选 alias，始终 SYSTEM_IMPACT，默认 ASK；拒绝时没有网络访问。请求总时限 10 秒、正文 1MiB、仅 text/* 或 application/json；结果带来源 URL、内容 SHA 和不可信说明。关闭/取消关闭当前输入并取消 future，单读取线程/无排队，不承诺能终止任意底层不合作实现。

请求从宿主发出，不受 Docker network=none 约束；固定来源选择是显式网络授权，网页内容不能创建来源、配置或权限。不提供通用搜索和 LSP；传输不确定返回 UNKNOWN，不自动重发。

## 离线资源核实与范围决定

sessions verify-environment / verify-subagent 调用既有人工事实核实 API。sessions verify-web 对稳定 catalog 获得独占写锁，校验 task/Profile/绑定、SDK 写锁和 SessionRecovery；先处理未决模型/工具/环境/子任务，不能用 Web 核实掩盖未决事实。仅对未结束或 UNKNOWN 且尚未核实的任务追加 web.task.verified，操作者必须先停止旧宿主/工厂并检查实际资源。无 SDK 日志时只接受外部核实声明，日志不能证明任意工厂线程已经停止。

重启投影把已核实 UNKNOWN 标为 phase=VERIFIED，保留原 status 和 EXTERNALLY_VERIFIED_NO_REPLAY，释放绑定供新任务使用。旧 requestId 仍指向原任务，不运行第二次；不改写 COMPLETED、不杀历史 PID、不启动环境、不重放。HTTP 在线核实/恢复/自动归档未实现。

Workflow、PTC、Goal/Schedule、LSP、自动只读工具并行与通用重试/限流插件、Desktop UI 等决定及理由逐项见 [上游能力对照](../requirements/upstream-capabilities.md)。默认串行与显式有界只读子任务并发是当前调度能力，不能称为自动并行工具调度。当前本地 SDK/Web 生命周期不是桌面 UI；图片输入未实现。M7 收口是这一明确范围的交付，不是上游全产品复刻。



M10.5.3 SDK增量（2026-10-17）：兼容原run(goal,cancellation)，新增携带requestHash/kind的公共重载，先记录sdk.input.applied与userEventId再开始新Loop；不直接改变运行轮次，日志前缀仍可重建。归档判断采用最新archived/restored事实，使已恢复会话可续轮。实际多轮/恢复验收见M10.5.3-contracts.md及对应记录。


R1 定点补验修订（2026-10-22）：Web 审批 POST 与 owner 的到期判断在共同服务端监视器内单次结算。截止前已接受的决定允许 owner 在截止后读取；截止后到达的 POST 不得报告 accepted。取消及关闭仍阻止执行。测试覆盖真实 HTTP 在同一监视器阻塞后跨截止释放，不把客户端请求已发出当作服务端已经接受。子任务核实在 subagent.verified 已落盘、tool.verified 未落盘时允许重入仅补后者，仍要求双 writer 空闲与子日志已结算，不重放工具。

2026-10-22 R2 增补：当前持久化、SDK 自包含输入/初始化、问答暂存与最终持久解析、容量及平台边界以 [R2 补充契约](OCR-R2-contracts-2026-10-22.md) 为准；旧 `userEventId` 分离输入仍兼容读取，新输入正文与关联同一事实发布。最终实测见 [R2 修复记录](OCR-R2-remediation-2026-10-22.md)，不将本条契约说明算作验收通过。
