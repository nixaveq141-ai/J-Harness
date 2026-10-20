# OCR逐条核实：application

原文及建议代码见 opinions.json；这里只以稳定编号连接原意见，部分采纳以“判断”限定范围。所有修复均未实施。

## OCR-0049 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/AgentLoop.java:5；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：公共 API 契约未固定，边界行为全靠实现约定： 1) 空值语义未声明。`CancellationToken` 已有 `CancellationToken.NONE`，但接口未要求 `cancellation` 非空，实现方（如 `DefaultAgentLoop.run` 直接调用 `requestedCancellation.isCancelled()`）在传 null 时会直接 NPE。 2) 结果语义未声明。方法返回 void 且不声明受检异常，调用方无法从签名区分“正常完成”“被取消”“失败”以及“存在未结算的 UNKNOWN 副作用”，只能依赖实现抛出的非受检 `HarnessException` 约定；这类语义一旦被外部实现解释，后续补齐返回值即为破坏性 API 变更。 建议在接口 javadoc 中明确：`cancellation` 必须非空（取消由 `isCancelled()` 轮询，无需 NONE 时显式传 `CancellationToken.NONE`）；取消/失败/UNKNOWN 各自以何种异常或终止状态上报；以及 UNKNOWN 未人工核实前不得再次调用 `run`。
- 判断：AgentLoop只有run签名，补充取消令牌非空、结果从事件投影读取及UNKNOWN准入责任的文档；void/非受检异常本身不是缺陷，不改返回类型。
- 证据：harness-api/src/main/java/io/jharness/api/AgentLoop.java:5

## OCR-0053 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/AgentLoopFactory.java:4；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：`AgentLoopFactory` 是公共 SPI（插件通过 register 注册实现，见 M1-contracts），但 `create` 完全没有契约说明： 1) 未声明 `dependencies` 为 null 时必须快速失败。`AgentLoopDependencies` 的紧凑构造器只对内部组件做 `requireNonNull`（第 14-22 行），因此调用方传 `null` 整包时不会被该 record 拦截，不同实现可能延迟到内部 NPE 或构造出半初始化的 AgentLoop。 2) 未声明返回的 `AgentLoop` 归属与生命周期：`AgentLoop` 目前只有 `run`，未继承 `AutoCloseable`，依赖（session/eventBus/llm 等）由谁关闭、`create` 是否可重复调用、实现是否需线程安全均无约定，多实现替换时容易出现资源泄漏或重复执行。建议在接口 Javadoc 中写明 `@throws NullPointerException`/`IllegalArgumentException` 的触发条件、返回值非空、所有权与线程安全语义（实现侧应在方法入口用 `Objects.requireNonNull(dependencies, "dependencies")` 快速失败）。
- 判断：AgentLoopFactory只有create声明，可补充依赖非空、loop与依赖所有权和单会话线程模型；未发现实际泄漏调用链。
- 证据：harness-api/src/main/java/io/jharness/api/AgentLoopFactory.java:4

## OCR-0056 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/AgentTool.java:10；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`name()`/`description()`/`inputSchema()`/`outputSchema()` 只声明了返回类型，未约定非空与不可变，但调用方直接把它们当作可信校验依据使用：DefaultToolPipeline 在参数校验前后两次读取 `tool.inputSchema()`（DefaultToolPipeline.java:58、69），并用 `tool.outputSchema()` 校验模型可见的输出（:263），且不做防御性复制。若实现返回 null 会直接 NPE；若返回可变的共享 Map，工具（或插件/热更新线程）在两次校验之间修改 schema 会造成“同一批参数校验结果不一致”从而绕过输入约束。建议在接口层显式声明这些元数据必须返回非空、稳定的不可变快照（例如 Map.copyOf 的深拷贝），或由调用方在读取后立即复制并做 null 校验。
- 判断：AgentTool四个元数据方法没有快照/稳定性约定，补充不可变稳定schema契约；任意可信插件故意篡改不能视为已证实漏洞。
- 证据：harness-api/src/main/java/io/jharness/api/AgentTool.java:10

## OCR-0057 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/AgentTool.java:19；OCR原严重度：medium
- 计划：无
- 原意见：`requiresSerialExecution()` 默认返回 false 属于 fail-open 约定：Javadoc 已说明交互/有状态工具需要 owner 线程执行，但实现者只要忘记重写，DefaultToolPipeline 就会在 `effect == READ_ONLY && decision == ALLOW && !requiresSerialExecution()` 时把该工具标记为可并行（harness-tools/.../DefaultToolPipeline.java:129-132），BoundedReadOnlyToolScheduler 也只按 `sideEffectLevel() == READ_ONLY` 生成并行白名单（BoundedReadOnlyToolScheduler.java:37），于是 ask_user 这类持有草稿/在途状态的工具会被并发调度或跨线程执行，触发状态竞争。建议把默认值改为 true（安全侧）或在工具注册阶段强制显式声明该能力，而不是依赖实现者记得覆盖。
- 判断：M8契约要求宿主显式白名单且保证工具及拦截器线程安全，requiresSerialExecution是额外否决；ask_user已覆盖串行要求，改默认true会破坏既有并行能力。
- 证据：harness-api/src/main/java/io/jharness/api/AgentTool.java:19

## OCR-0058 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/AgentRuntimeServices.java:37；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：9 参便捷构造器会静默用 ApiCostGuard.unavailable() 顶替缺失的成本守卫，而该守卫直到首次调用 maximumCost() 时才抛出 COST_BUDGET_UNAVAILABLE（DefaultAgentLoop 中仅在配置了 maxApiCost 时才调用）。这会把“装配遗漏”从构造期错误推迟成会话运行中第一个计费请求的失败；对 harness-api 的外部嵌入方而言，编译期完全看不出使用的是降级实现。建议为该构造器补充 Javadoc 说明降级语义并标注 @Deprecated（或直接要求显式传入 ApiCostGuard），由调用方明确决定是否真的不启用费用守卫。
- 判断：兼容构造器确实使用unavailable且费用预算开启时在发送前拒绝；只采纳说明兼容降级语义，不强制废弃旧构造器。
- 证据：harness-api/src/main/java/io/jharness/api/AgentRuntimeServices.java:37

## OCR-0065 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ApiVersion.java:4；OCR原严重度：low
- 计划：R3-01 / P2
- 原意见：版本常量与仓库内已发布的协议说明存在漂移：README.md 第128行模块表仍写 `harness-api | API 1.13 ...`（该表后续未随 M10.5.8 更新）。由于 `PluginContainer.visit()` 用 `ApiVersion.CURRENT.equals(descriptor.apiVersion)` 做插件 API 精确匹配，这个字面量是唯一的兼容性判定源，任何文档/证据文件（docs/testing/*-results.json 中的 apiProtocol 字段）与实际值不一致都会让外部插件作者按错误版本号编译并被 `API_VERSION` 拒绝。建议同步更新 README 模块表，并在注释中标注“修改本值必须同步更新 README 的 API 版本说明与插件版本精确匹配约束”，避免下次升级时再次漂移。
- 判断：README第128行仍写API1.13而ApiVersion为1.14，需同步当前模块说明；历史验收文件不批量改版本。
- 证据：harness-api/src/main/java/io/jharness/api/ApiVersion.java:4；README.md:128

## OCR-0066 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/ApiVersion.java:3；OCR原严重度：low
- 计划：无
- 原意见：`CURRENT` 是 `static final String` 编译期常量，会被 javac 内联进所有引用点（如 CliApplication、StdioSdkServer、HarnessSdk、McpPlugin、PluginContainer 的版本比较）。只替换 harness-api jar 而不重新编译依赖模块时，这些引用点仍保留旧的 "1.x" 字面量，导致握手/客户端版本上报与实际 API 不一致且难以排查。若存在按 jar 单独升级或热替换的发布方式，建议改为非编译期常量，例如通过 `public static String current() { return "1.14"; }` 读取（或从资源文件/构建元数据加载），并对读侧做非空校验。
- 判断：编译期内联属Java既定行为，仓库采用同一多模块构建/CLI整体制品，无单独热替换harness-api发行契约；未证实现实版本故障。
- 证据：harness-api/src/main/java/io/jharness/api/ApiVersion.java:3

## OCR-0067 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ApiCostGuard.java:8；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：接口契约缺少对 maximumCost 返回值的约束（非 null、非负、与 currency() 一致）。调用方 DefaultAgentLoop:92-99 用 Objects.requireNonNull 取值、SharedFeeBudget:38-39 也是如此：宿主实现返回 null 时会在费用计算路径抛出裸 NPE（而非带回错误码的 HarnessException），返回负数时才被拦为 COST_BUDGET_UNAVAILABLE；如果实现返回的币种与 Budget.currency() 不一致，只在 DefaultAgentLoop 里检查，直接调用 actualCost 的路径没有校验。建议在 Javadoc 中显式声明“必须返回非 null、非负、且以 currency() 计价的请求费用上界”，并在此处提供 default 校验方法（或把 unavailable() 的失败语义写成统一入口），避免宿主实现静默返回 null/负值时产生不可控的失败形态。
- 判断：ApiCostGuard.maximumCost缺少非空/非负/币种契约，应补文档和宿主校验；现有Loop已发送前检查，不认定直接越预算。
- 证据：harness-api/src/main/java/io/jharness/api/ApiCostGuard.java:8

## OCR-0068 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ApiCostGuard.java:15；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：默认 actualCost 再次调用 maximumCost，会把“有状态”的守卫（maximumCost 带预留副作用）再走一遍：例如 SharedFeeBudget.maximumCost 每次调用都会累加 reserved 并设置 pending（SharedFeeBudget:37-49），对尚未 override actualCost 的装饰器/实现，这里会在结算阶段二次预留，或在 pending 未清空时抛 COST_RESERVATION_PENDING / 结算值大于 pending 而抛 COST_ACCOUNTING_BREACH。另外 maximumCost 对“重建后的 replay 请求”重新求值，可能得到与预留时不同的上界（例如 max_tokens/model 参数被改写会使 ConfiguredTokenCostGuard 直接抛 COST_BUDGET_UNAVAILABLE），无法保证“保留整笔预留”的语义。建议默认实现不重新调用 maximumCost，而是由调用方把已预留的上界传入（例如新增 default BigDecimal actualCost(request, response, reservedBound)），或在契约中明确 maximumCost 必须是纯函数且可对同一请求重复求值。
- 判断：默认actualCost确实重新调用maximumCost，需明确默认适用纯函数守卫、有状态守卫覆盖结算；SharedFeeBudget已有覆盖，不认定内置双预留。
- 证据：harness-api/src/main/java/io/jharness/api/ApiCostGuard.java:15

## OCR-0069 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ApiCostGuard.java:20；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：Javadoc 承诺“释放活跃的预留槽位”，但默认实现是空操作：有状态实现（如 SharedFeeBudget 通过包装器跟踪 pending）若忘记 override，则 DefaultAgentLoop:174-176 在 ModelRejectedException 后调用本方法时槽位不会释放，后续重试再次调用 maximumCost 将被判为 COST_RESERVATION_PENDING 而拒绝，表现为 429 重试被错误地 fail-closed。建议把该默认实现改为抛出 UnsupportedOperationException/明确失败，或在接口 Javadoc 中声明“有状态实现必须覆盖此方法”，并增加契约测试覆盖“取号→拒绝→重试”路径。
- 判断：requestRejected默认空操作适合无状态函数，实现有槽位必须覆盖应写明；改默认抛错会破坏合法无状态守卫。
- 证据：harness-api/src/main/java/io/jharness/api/ApiCostGuard.java:20

## OCR-0078 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/ApprovalService.java:7；OCR原严重度：high
- 计划：无
- 原意见：ApprovalDecision 未携带任何与本次审批请求绑定的标识（callId/approvalId），`actor` 也完全是调用方可自由填写的字符串。任何代码都可以直接 `new ApprovalDecision(true, "ok", "operator")` 构造出“已批准”的结果；只要某一层（按 callId 缓存审批结果、会话恢复重放、插件/子代理桥接、HTTP 或 SDK 适配器）复用或透传该记录，就可能把针对一次 ToolInvocation 的批准套用到另一次调用，或者伪造操作者身份，而 DefaultToolPipeline 会把这个 actor 原样写入 approval.decided 审计事件。建议在记录中增加调用绑定字段（callId/approvalId）并在受控位置校验，或在接口 Javadoc 中明确禁止缓存/跨调用复用决策、规定 actor 只能由可信适配器填写（如 web-operator/system），审计不得仅凭该记录判定审批真实发生。
- 判断：ApprovalService是可信宿主注入SPI，Java插件可构造记录不构成鉴权绕过；实际Web以promptId/callId绑定，不存在所述跨请求缓存路径证据。
- 证据：harness-api/src/main/java/io/jharness/api/ApprovalService.java:7

## OCR-0079 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ApprovalService.java:5；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：接口未定义 decide 的失败语义（抛出异常、返回 null 或超时是否等同于拒绝），fail-closed 只能靠各调用方自行实现。当前 DefaultToolPipeline 仅对 HarnessException 做兜底，其它 RuntimeException（例如下面 ApprovalDecision 校验失败抛出的 IllegalArgumentException）会直接向上抛出，导致已发出的 approval.requested 事件没有配对的 approval.decided 事件，审批审计链断裂；若后续新增调用方 catch 后继续执行，则等于异常情况下放行工具执行。建议在接口契约中显式声明“异常/中断/超时/空值一律视为拒绝、且决策不可跨调用复用”，并补充 Javadoc 说明。
- 判断：decide非空、异常/取消不执行、决策只对当前调用有效可补契约；DefaultToolPipeline外层catch(Exception)阻止主体，原文仅catch HarnessException不准确。
- 证据：harness-api/src/main/java/io/jharness/api/ApprovalService.java:5

## OCR-0080 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ArtifactStore.java:19；OCR原严重度：high
- 计划：R2-01 / P2
- 原意见：默认 `readText` 把“本次返回长度 < 262144”当作 EOF，但 `read(...)` 的契约（第 8 行）并未规定实现必须只在 EOF 时才返回短块。任何合法返回部分块（如底层流式读取、按字节上限切分、限流）的实现都会在此处被提前判定为结束，`readText` 静默返回不完整文本，而调用方（如 `WorkspaceContentInspection` 把该文本视为完整校验文本并做脱敏/分页/SHA 展示）无法区分截断与完整，属于数据完整性缺陷。应以“空块”作为唯一的 EOF 信号，短块继续累加。
- 判断：默认readText以短块判EOF，read尚未写明填满规则；内置LocalArtifactStore覆盖readText，采纳统一EOF契约及短块测试，非内置数据丢失结论。
- 证据：harness-api/src/main/java/io/jharness/api/ArtifactStore.java:19

## OCR-0081 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ArtifactStore.java:14；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：`read` 的 `offset/limit` 单位与 EOF 语义在接口中完全未声明，默认 `readText` 却隐式假定二者是解码后文本的 UTF-16 字符偏移，并按 `String.length()` 推进。当前 `LocalArtifactStore` 恰好按 `text.substring` 的字符偏移实现，但任何按字节偏移实现（`Artifact.bytes` 本身是字节数，实现者很自然会按字节分页）的存储都会导致多字节 UTF-8 内容被跳过/重复，或 offset 越过字符长度后被判为 EOF 而截断。请在接口 Javadoc 中明确 offset/limit 单位、EOF 返回空串、越界不得抛错，否则该默认方法不能安全复用于其他实现。
- 判断：readText按String.length推进，read接口未说明单位，应明确UTF16字符偏移、空块EOF及到末尾行为并做非ASCII分页契约测试。
- 证据：harness-api/src/main/java/io/jharness/api/ArtifactStore.java:14

## OCR-0082 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ArtifactStore.java:17；OCR原严重度：medium
- 计划：R2-01 / P3
- 原意见：这里存在两个问题：其一，上限 4 MiB 是硬编码且未在 `put` 契约中声明的，实现方若允许保存更大内容（或按字节上限保存 4 MiB 以上文本），默认 `readText` 会把合法产物当作超限拒绝；其二，判定用的是 `String.length()`（UTF-16 字符数）而 `put`/`Artifact.bytes` 的 4 MiB 是 UTF-8 字节数，两种度量不一致。此外，当内容恰好等于 4 MiB 时循环会再按 offset==length 读取一次，若实现把该 offset 视为非法而不是返回空串，则会令恰好 4 MiB 的合法产物读取失败。建议把上限抽成常量并在接口文档中说明其单位与来源，同时约定 offset 到达末尾时 `read` 返回空串。
- 判断：默认4194304限制按UTF16计数而Artifact.bytes为字节，需明确两者及inspection限额；不把默认宿主读取界限移除。
- 证据：harness-api/src/main/java/io/jharness/api/ArtifactStore.java:17

## OCR-0083 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ArtifactStore.java:10；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：注释声称返回“完整且已校验（verified）”的文本，但默认实现从不比对 `Artifact.sha256` 或 `bytes`，任何损坏/被篡改的存储内容都会被原样返回。仓库契约（M10.5.6：缺失/过期/损坏必须明确拒绝）要求校验 SHA，当前只有 `LocalArtifactStore` 的重写实现做了校验，而接口默认方法对第三方实现公开且不具备校验能力。建议要么删除“verified”措辞、明确该方法不做完整性校验，要么把校验责任写入接口契约（例如要求 `readText` 实现自行校验，或提供接收 `Artifact` 的校验入口）。
- 判断：默认实现只累加read结果而注释承诺verified，应明确provider完整性责任或删除无依据承诺；内置LocalArtifactStore校验独立保持。
- 证据：harness-api/src/main/java/io/jharness/api/ArtifactStore.java:10

## OCR-0084 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ChatRequest.java:11；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：构造器对 messages/tools/parameters 做无条件的全量深拷贝（Values.snapshot 递归复制每个 Map/List），但 messages/tools 既没有数量上限也没有单条消息大小上限校验；同时该 record 在一条请求链路上会被反复重新包装（如 StreamingLlmClient/OpenAiCompatibleLlmClient 的 prepare、DefaultAgentLoop 的 clean、ExtractiveContextCompactor 的 compact，均用已有的不可变 messages 再 new 一个 ChatRequest），导致同一份全量历史在一次推理中被深拷贝多次，长会话 + 大工具声明下 CPU/内存放大明显。建议增加已冻结集合的快速通道（例如判断入参是否已是 Collections.unmodifiableList 且逐元素为快照则直接复用），并在 API 边界对 messages/tools 规模做有界校验，避免无界数据放大。
- 判断：ChatRequest每次快照重拷贝确实存在，采纳规模度量和统一有界策略；按unmodifiable类名跳过深拷贝不安全，拒绝该优化方案。
- 证据：harness-api/src/main/java/io/jharness/api/ChatRequest.java:11

## OCR-0085 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ChatRequest.java:10；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：紧凑构造器未对入参做空值校验：`messages.stream()` 在 messages 为 null 时抛出的 NPE 没有任何上下文，而 `Values.snapshot(null)` 只是 `Objects.requireNonNull(value)`，抛出的也是无消息 NPE；若 list 中某个元素为 null，同样只能得到一个无定位信息的 NPE，且无法判断是 messages/tools/parameters 哪个字段或哪个下标出的问题。ChatRequest 是公共 API 信封（被 DefaultPromptComposer、EventRequestJournal.fromPayload、各 LlmClient 包装层直接构造），建议在构造器开头显式 requireNonNull 并带上字段名，让失败可诊断且尽早失败。
- 判断：messages.stream及snapshot的NPE缺字段名，只采纳具名非空校验作为诊断改进；空值已经被拒绝，并非输入校验绕过。
- 证据：harness-api/src/main/java/io/jharness/api/ChatRequest.java:10

## OCR-0086 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/Budget.java:30；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：currency 为 null 时并不会得到清晰的 IllegalArgumentException：`Set.of(...)` 是不可变集合，其 `contains(null)` 会直接抛 NullPointerException（`ImmutableCollections.SetN#contains` 内部 `Objects.requireNonNull(o)`）。即 `new Budget(1, Duration.ofSeconds(1), Optional.empty(), null)` 抛出的是 NPE 而非本类约定的参数校验异常，且该校验发生在 maxApiCost 非空校验之前会掩盖后者。建议先显式判空，或在 `Optional.ofNullable(currency).orElse("")` 之后再做白名单判断。
- 判断：Set.of币种contains(null)会NPE，补明确参数诊断即可；非法预算没有被放行。
- 证据：harness-api/src/main/java/io/jharness/api/Budget.java:30

## OCR-0087 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/Budget.java:13；OCR原严重度：medium
- 计划：无
- 原意见：构造函数明确允许 CNY（白名单 “USD”/“CNY”，生产配置 docs/examples/deepseek-config.json 及 CliConfig 构造的就是 CNY 预算），但 `maxApiCostUsd()` 对任何非 USD 预算直接抛 IllegalStateException。合法构造出的 CNY 预算在此访问器上成为运行时炸弹：共享的费用检查代码（如 SharedFeeBudget/DefaultAgentLoop 中若改用此别名）一旦调用即失败，且只能靠每个调用方自行判断 currency 规避。该别名同时与 record 自动生成的 `maxApiCost()` 语义重复，容易误用。建议直接使用 `maxApiCost()`（货币由 `currency()` 一并取用），或在此方法返回 Optional.empty()/改名并标注货币前提（如要求调用方断言 `currency().equals("USD")`）。
- 判断：maxApiCostUsd明确是USD兼容访问器，对CNY拒绝比返回空更安全；现行主链使用maxApiCost/currency，无真实合法CNY调用炸弹。
- 证据：harness-api/src/main/java/io/jharness/api/Budget.java:13

## OCR-0088 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/CancellationToken.java:10；OCR原严重度：medium
- 计划：R1-06 / P2
- 原意见：取消原因被硬编码成 CANCELLED，无法区分“用户取消 / 截止时间到期 / 包装器自行加的超时”。触发条件可复现：`SkillCatalog.scan` 给 token 叠加了 5 秒扫描 deadline（harness-ecosystem `SkillCatalog.java:77/85`），deadline 到期时经此默认方法抛出 code=CANCELLED；该异常在 CLI 侧被当作取消处理（`EcosystemCli.java:311` 映射退出码 3），而用户从未请求取消，属于把内部超时误报为用户取消（PRD 要求 CANCELLED 与 LIMIT_REACHED/TIMEOUT 分别报告）。同样，任何用 `() -> deadline 到期` 组合出的 token 都会失去真实原因。建议在该 API 上提供可携带 code/message 的重载（或让包装器在 catch 后重新映射），使超时路径能抛出 TIMEOUT/LIMIT_REACHED 而不是统一 CANCELLED。
- 判断：CancellationToken默认固定CANCELLED，组合扫描deadline容易混淆，采纳在SkillCatalog截止处映射超时；不必为所有token扩大API。
- 证据：harness-api/src/main/java/io/jharness/api/CancellationToken.java:10

## OCR-0089 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ChatResponse.java:14；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：空值校验不一致且不可诊断：`text` 有显式校验，但 `toolCalls` 为空时由 `List.copyOf` 抛出无参 NPE，`usage`/`providerState` 为空时由 `Values.snapshot` 内部的 `Objects.requireNonNull(value)` 抛出 message 为 `null` 的 NPE。调用方（如 `DefaultAgentLoop.clean(...)`、插件侧 `LlmClient` 实现）一旦漏传 usage/providerState，现场只能看到 `NullPointerException: null`，无法定位是哪个组件、哪个 provider 输出导致的，这对排障极不友好。建议在 compact constructor 中为每个引用组件补齐带名称的显式校验（`text` 不可用 `Values.required`，因为仅含工具调用的响应允许空文本）。
- 判断：ChatResponse的List.copyOf及snapshot已拒绝null，补字段名可改善定位；不存在接受空结果。
- 证据：harness-api/src/main/java/io/jharness/api/ChatResponse.java:14

## OCR-0090 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/CheckpointPolicy.java:4；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：该 SPI 的三个钩子没有任何契约说明，而它们处于副作用/恢复的关键路径上：1) 未定义三者的调用顺序以及跨步骤不变量（例如工具结果必须先落盘、再发下一次模型请求）；2) 未说明返回值只是“决策”而非持久化动作，调用方负责强制执行，且 `DURABLE_FLUSH` 必须成功后才能继续，否则实现方容易误以为返回枚举值即已刷盘（DefaultCheckpointPolicy 的注释已提示这一点，但接口本身没有）；3) 未说明 `MEMORY_ONLY` 不得用于会产生副作用的工具，也未说明 UNKNOWN/恢复中的会话不得在此触发重放。建议在接口级 Javadoc 中固化这些语义，避免第三方实现（该接口是公开扩展点）引入“未刷盘就执行副作用”或“重放 UNKNOWN 状态”的缺陷。
- 判断：CheckpointPolicy接口未承载M1持久顺序，补决策与实际flush分责和副作用durable要求；现有EventSupport执行强制检查。
- 证据：harness-api/src/main/java/io/jharness/api/CheckpointPolicy.java:4

## OCR-0091 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/CheckpointPolicy.java:6；OCR原严重度：low
- 计划：无
- 原意见：该钩子只收到 `ToolInvocation`，而 `ToolInvocation` 只有 callId/tool/arguments/sideEffectLevel，不包含 sessionId 或 `SessionState`。因此实现在顶层工具执行前无法做任何会话维度判定（按会话的环境变量白名单、插件作用域回滚、会话处于 UNKNOWN/失败态时拒绝继续执行等），只能退化为全局的副作用级别判断；相比另外两个钩子只能拿到会话状态也形成了签名不一致。建议补充 `SessionState`（或会话/作用域标识）参数，或至少在接口文档中明确“会话级约束不在此钩子执行、统一由调用方 Pipeline 负责”，以免扩展方在此处做出错误的越权放行假设。
- 判断：CheckpointPolicy只决定持久边界，scope/UNKNOWN准入由Loop和Pipeline负责；无需把授权职责塞进checkpoint签名。
- 证据：harness-api/src/main/java/io/jharness/api/CheckpointPolicy.java:6

## OCR-0094 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ConfigView.java:6；OCR原严重度：medium
- 计划：R1-04 / P3
- 原意见：类注释承诺“never render secrets into events”，但 record 自动生成的 toString()/equals()/hashCode() 会直接输出 values 中的原始值。任何把 ConfigView（或包含它的 PluginContext）拼进日志/异常消息/事件详情的地方，都会把 API key、token 等凭据原文写入事件与日志，属于可复现的敏感信息泄漏（触发条件：logger.info("{}", context.config()) 或把 config 作为异常参数）。建议显式重写 toString 做脱敏（或只输出键名/摘要），并明确 equals/hashCode 是否应包含敏感值。
- 判断：ConfigView自动toString会包含values，采纳安全摘要输出；equals/hashCode不输出内容，且未找到当前日志泄漏sink。
- 证据：harness-api/src/main/java/io/jharness/api/ConfigView.java:6

## OCR-0095 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/ConfigView.java:16；OCR原严重度：low
- 计划：无
- 原意见：Values.snapshot/freeze 允许 JSON null 值原样保留，因此 values 中“显式配置为 null”的键与“键不存在”都会让 get(key) 返回 Optional.empty()，调用方无法区分“未配置”和“配置了 null”，可能把非法/空配置静默当作缺省值处理。建议在构造时拒绝 null 值（与 required(key) 的校验保持一致），或补充 containsKey/带默认值语义的访问方式。
- 判断：ConfigView.get用Optional.ofNullable表达可选值，JSON null可代表缺省；无契约要求区分缺失和显式null，拒绝全局禁止JSON null。
- 证据：harness-api/src/main/java/io/jharness/api/ConfigView.java:16

## OCR-0096 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ContentInspection.java:7；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：契约层面缺陷（L7-L16）：1) 三个方法均返回 `Map<String, Object>`，无字段 schema、脱敏责任和不可变性约定，实现方容易返回包含主机绝对路径/敏感内容的可变 Map，并被多个线程共享后发布（调用方修改会污染共享状态）；2) `CancellationToken` 未声明非空，而 `WorkspaceContentInspection` 直接调用 `token.throwIfCancelled()`，传入 null 时在 L48 触发 NPE；3) 取消以 `HarnessException`（非受检）表达，但方法只声明 `IOException`，调用方按签名捕获异常时无法识别取消，恢复逻辑会误判。建议契约中明确结果字段/schema、返回不可变副本、token 非空（或允许 null 时用 NONE 兜底）以及取消的异常类型。
- 判断：ContentInspection返回Map且方法无字段/取消文档，补结构、限额、非空与脱敏职责；现有实现保护独立，不把IOException签名误作禁止RuntimeException。
- 证据：harness-api/src/main/java/io/jharness/api/ContentInspection.java:7

## OCR-0097 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ContentInspection.java:8；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：文件系统边界缺陷（L10）：`path` 是未加约束的 String，契约未要求以注册根为界做规范化（拒绝 `..`、绝对路径、Windows 盘符/UNC）及 symlink real-path 校验，也未要求过滤链接/二进制/根外元数据。触发条件：调用方传入 `../../etc` 或指向根外的符号链接，实现方若只做字符串过滤（如仅按段名屏蔽 `.git`）即可越界读取。建议在接口契约中明确“必须基于规范化后的真实路径校验位于注册根内，越界抛出可识别错误”。
- 判断：ContentInspection需把注册根、链接拒绝的既有M10.5.6契约链接到接口；未发现内置WorkspaceContentInspection越界。
- 证据：harness-api/src/main/java/io/jharness/api/ContentInspection.java:8

## OCR-0098 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ContentInspection.java:10；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：版本与范围校验缺陷（L12-L14）：`version` 为不透明 String，契约未规定未知/空/过期版本的语义，实现方可以静默返回当前内容，从而把 UNKNOWN 状态当作已确认版本重放；同时 `offset`/`line` 无契约级上界、负数校验和互斥约定（实现中 line>0 时要求 offset==0，但契约未声明），传入负 offset 或超 EOF 行的行为未定义，"bounded" 的保证在契约层未被强制。建议明确：空版本表示不做版本断言、非空版本不匹配必须显式冲突失败（不返回内容）、offset/line 的合法区间与互斥关系。
- 判断：file的version/offset/line组合确实缺少接口说明，采纳与现有实现一致的冲突/范围文档；只读返回当前内容不等于副作用重放。
- 证据：harness-api/src/main/java/io/jharness/api/ContentInspection.java:10

## OCR-0099 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/ContentInspection.java:14；OCR原严重度：high
- 计划：无
- 原意见：作用域校验缺陷（L15-L17）：`sessionId` 由调用方任意传入，契约未要求实现校验其属于当前插件/会话作用域。触发条件：绑定了会话 A 的插件调用 `artifact("B", ...)`，若实现方未做归属校验（现有实现仅在绑定 session 非空时比较，未绑定场景直接放行）即可跨会话读取其他会话的 artifact。建议契约要求对 sessionId 做归属校验，越权时显式拒绝，并明确 artifact id 的格式/大小上限。
- 判断：无绑定session的ContentInspection是宿主跨会话检查入口，绑定实例才严格比较session；把未绑定一律拒绝会删除已授权宿主功能。
- 证据：harness-api/src/main/java/io/jharness/api/ContentInspection.java:14

## OCR-0100 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ContextCompactor.java:9；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：`Compaction` 的紧凑构造器只校验了非空/防御性拷贝，但没有校验 `removedMessageIndices` 的**取值范围、去重与顺序**，也无法保证接口声明的“保留未完成的工具协议组”这一不变量：索引可以是负数、越界、重复或乱序；实现还可能在删除 `assistant(tool_calls)` 的同时保留对应 `tool` 结果（或反之）。后果：下游 `ContextCompactionPlugin` 会把这些索引写入 `context.compacted` 事件，任何按索引回查 `request.messages()` 的消费者会抛 `IndexOutOfBoundsException`；拆组则会让会话历史中出现孤立 tool 结果/缺失结果，触发协议错误或对 UNKNOWN 工具状态的重放。建议在构造器中显式校验（索引必须严格递增且落在 `[0, request.messages().size())`），并按 `tool_calls.id` ↔ `tool_call_id` 配对检查被删除集合不拆开任何未完成协议组，违反时抛 `IllegalArgumentException`。
- 判断：Compaction只复制indices，采纳在插件边界按原始request验证索引和保留未决工具组；返回request已删消息，不能按返回消息长度检验原索引。
- 证据：harness-api/src/main/java/io/jharness/api/ContextCompactor.java:9

## OCR-0101 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ContextCompactor.java:7；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`compact` 缺少可执行的契约：`request` 为 null、`maxCharacters` 为 0 或负数时行为未定义（多数实现会静默返回原请求或抛 `CONTEXT_LIMIT`，两者对调用方含义完全不同），也没有后置条件要求返回的 `request` 实际不超过 `maxCharacters`、且 `tools`/`parameters` 与原请求保持一致。由于 `ContextCompactionPlugin` 会把 `result.request()` 直接作为模型请求下发，一个违规实现可以静默超限（压缩失效、上下文溢出）或篡改/丢弃工具声明与参数（绕过工具白名单、改变 `max_tokens` 等策略），而调用方无从发现。建议用 javadoc 明确：`maxCharacters > 0`、`request != null`、返回非 null 且体积不大于 `maxCharacters`、`tools`/`parameters` 必须与入参一致，并在调用侧或返回处做相应断言。
- 判断：compact缺少maxCharacters正值及tools/parameters保留约定，可补契约与插件后置校验；可信替换实现任意恶意行为不是可隔离安全边界。
- 证据：harness-api/src/main/java/io/jharness/api/ContextCompactor.java:7

## OCR-0102 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/ContentSearchProvider.java:7；OCR原严重度：high
- 计划：无
- 原意见：契约层没有任何“sessionId 必须由实现再次按宿主机授权上下文校验”的约束，注释“Callers supply only an authorized session identifier”反而容易被第三方 provider 理解为可直接信任该字符串，从而以 sessionId 充当鉴权凭证，导致跨会话/跨工作区越权读取。建议在接口 Javadoc 中明确：sessionId 仅是作用域选择器，实现必须重新校验其属于当前宿主已授权范围，且必须校验其格式（禁止路径分隔符、`..`、Windows 保留名）后再用于落盘路径拼接（参考实现的 `file()` 有正则约束，但契约未要求）。
- 判断：接口明确由调用者只传授权session，provider是宿主绑定索引而非身份认证器；要求它凭一个sessionId重新鉴权在当前签名不可实现。
- 证据：harness-api/src/main/java/io/jharness/api/ContentSearchProvider.java:7

## OCR-0103 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ContentSearchProvider.java:8；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：Hit 的 snippet 直接来自会话正文，但契约未要求脱敏与长度上界。若某个实现把 sanitize 之前的 payload 或未脱敏字段写入 snippet，会把密钥、令牌等敏感信息通过搜索接口回传给调用方/UI；score 的取值语义（0 是否表示未匹配、上限多少）也完全未定义，客户端无法做稳定比较或展示。建议补 Javadoc：snippet 必须是已脱敏且长度有界的片段，eventId/type/timestamp 不可为 null，score 为 0 表示不匹配且范围有明确定义。
- 判断：Hit字段无snippet脱敏/边界和score定义，补M10.5.8既有结果契约；未证明当前TokenSessionSearch泄密。
- 证据：harness-api/src/main/java/io/jharness/api/ContentSearchProvider.java:8

## OCR-0104 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ContentSearchProvider.java:26；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：limit 只有 int 类型，没有下界/上界与超限行为（拒绝还是截断）的约定。TokenSessionSearch 内部限制为 1..4096，但该约束只存在于实现中，其他实现可接受 0、负数或 Integer.MAX_VALUE，造成无界扫描/超量内存占用，且各实现的分页语义不一致（有的在命中数超限时抛错而不是截断）。建议在契约中写明合法区间与超限处理方式（抛 IllegalArgumentException 或钳制），并在实现中统一执行。
- 判断：limit接口未写1..4096及超限行为，采纳文档保持实现一致；不得因为第三方可能不校验认定内置无界扫描。
- 证据：harness-api/src/main/java/io/jharness/api/ContentSearchProvider.java:26

## OCR-0105 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ContentSearchProvider.java:25；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：mode 以裸 String 暴露且未声明合法取值，跨实现无法保证一致性；实现侧 `Set.of("tokens","phrase").contains(mode)` 在 mode 为 null 时还会抛 NPE 而不是受检的 INVALID_SEARCH。建议改为枚举（或至少用 Javadoc 列出允许值并规定 null/非法值必须抛 IllegalArgumentException），同时说明 text 的长度/空白/词数上限由实现负责校验。
- 判断：mode允许tokens/phrase应写明，null诊断可统一；改枚举属破坏性变更，本计划不直接替换公共签名。
- 证据：harness-api/src/main/java/io/jharness/api/ContentSearchProvider.java:25

## OCR-0106 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ContentSearchProvider.java:17；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：Result 的紧凑构造器只做了 `List.copyOf(hits)`：hits 为 null 时抛出的是 NPE 而非可解释的参数错误，且未校验 hits 中是否含 null 元素、indexedEvents/reusedEvents 是否为负或 reusedEvents > indexedEvents。调用方无法区分“实现缺陷”和“空结果”，异常也不在 `throws IOException` 的契约内。建议显式校验并抛 IllegalArgumentException。
- 判断：List.copyOf已同时拒绝null列表和null元素，原文漏判；仅采纳索引计数非负/关系说明，不认定现行产生非法计数。
- 证据：harness-api/src/main/java/io/jharness/api/ContentSearchProvider.java:17

## OCR-0107 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ContentSearchProvider.java:16；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：indexedEvents/reusedEvents/rebuilt 的语义与不变量未定义：rebuilt=true 时 reusedEvents 是否必须为 0（参考实现在 rebuild=true 时仍可能统计出 >0 的 reused 前缀）、reusedEvents 是否一定 ≤ indexedEvents、结果是否保证按 score 降序并用稳定键破同分、分页是否稳定都未在契约中声明。消费者可能误把“reusedEvents>0”当作索引无需重建而重放旧索引状态，或依赖未承诺的排序。建议在记录 Javadoc 中写明这些不变量与排序约定。
- 判断：重建/复用计数和排序规则需说明，rebuilt不必隐含reused=0；指标读取不是自动重放入口。
- 证据：harness-api/src/main/java/io/jharness/api/ContentSearchProvider.java:16

## OCR-0108 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ContentSearchProvider.java:28；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：search 方法没有任何 Javadoc，取消与失败语义缺失：cancellation 触发时是抛未受检异常（CancellationException）还是返回部分 Result、此时磁盘上的半成品索引是否回滚/是否会在下次调用被重建，IOException 时是否保证不返回部分结果、临时文件是否清理，均未约定。参考实现会在收集命中之前写入索引，取消抛出后磁盘上可能已存在新索引，调用方若重试或回放需要明确语义。建议在方法级 Javadoc 中定义：取消必须抛出不可忽略的异常、不返回部分结果，索引是可丢弃的（IO 失败后可安全重建），以及 `@throws IOException` 的含义。
- 判断：search缺取消及可丢弃索引语义说明，补文档；索引先更新后取消可合法保留派生缓存，无需事务回滚事实。
- 证据：harness-api/src/main/java/io/jharness/api/ContentSearchProvider.java:28

## OCR-0109 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/EventBus.java:6；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：接口契约不完整：这里只约定了"非阻塞 + 观察者失败不影响发布结果"，但没有约定监听器内再次 publish 的行为。现有实现 DefaultEventBus 用 ThreadLocal 检测并抛 RECURSIVE_EVENT（recursive publish from a listener is forbidden），而该禁令没有出现在 API 契约里，其他实现可能选择同步重入（导致死锁/无限递归）或静默放行。建议把重入禁令、事件顺序（同一事件是否按注册快照派发）写入契约，避免插件在监听器里发布事件时行为随实现漂移。
- 判断：DefaultEventBus.rejectRecursive明确拒绝监听内发布，EventBus未说明，补禁止重入及观察快照契约。
- 证据：harness-api/src/main/java/io/jharness/api/EventBus.java:6

## OCR-0110 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/EventBus.java:9；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：notify 的 type 匹配语义未定义：现有实现把 "*" 当作通配符、其余按精确 type 匹配（DefaultEventBus.matches 中 `filter.equals("*") || filter.equals(event.type())`），但接口没有任何说明。触发条件：其他实现只做 equals 精确匹配（或支持前缀通配），同一段插件代码在替换实现后收不到/收到多余通知；同时未约定通知在哪个线程、是否异步执行，调用方无法判断能否在监听器里做阻塞操作。建议在接口上明确匹配规则（精确或 "*"）、派发线程与快照语义。
- 判断：实现matches仅精确类型或星号，notify异步单线程；接口应固定这些可依赖语义。
- 证据：harness-api/src/main/java/io/jharness/api/EventBus.java:9

## OCR-0111 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/EventBus.java:11；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：serial 的 timeout 契约不足以支撑实现一致性：未声明 timeout 必须为正（实现会对 null/0/负数抛 IllegalArgumentException）、超时后是否中断监听器线程、是否继续执行剩余监听器、以及超时/失败以何种异常返回给调用者（实现分别抛 SERIAL_TIMEOUT / SERIAL_FAILED）。另外 "cancels cooperatively" 没有告诉 SerialListener 如何观测取消（实际只能靠 Thread 中断标志），监听器作者无从编写正确的协作取消逻辑。这会导致超时/失败后必需步骤是否已执行处于不确定状态，进而引发重复执行或 UNKNOWN 状态处理错误。
- 判断：serial只写cooperative而未说明interrupt/超时后停止本轮及错误码，应补方法契约并与串行超时修复一致。
- 证据：harness-api/src/main/java/io/jharness/api/EventBus.java:11

## OCR-0112 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/EventBus.java:16；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：intercept 的 priority 语义未声明（方向、取值范围、与 invoke 链嵌套的关系），与 serial 的 "ascending priority" 不同，调用方无法判断数值小的是内层还是外层拦截器，不同实现/后续重构反转排序会静默改变拦截顺序。建议在接口上写明排序方向及同位次的稳定顺序。
- 判断：intercept无排序说明，实现priority升序外层且同优先级注册顺序，补明确契约。
- 证据：harness-api/src/main/java/io/jharness/api/EventBus.java:16

## OCR-0113 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/EventBus.java:18；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：invoke 缺少入参与异常契约：未声明 request 必须非空且可赋值给 point.type()、terminal 是否允许为 null（实现直接 Objects.requireNonNull，抛裸 NPE 而非 HarnessException）、拦截器返回 null 是否合法，以及拦截器抛出的异常是否被包装（实现包装为 INTERCEPT_FAILED）。触发条件：泛型 T 可能被推断为超类型/Object，类型不匹配被推迟到运行期 ClassCastException，调用方拿到的异常类型随实现而异，破坏了调用链上统一的错误处理。
- 判断：invoke实现已cast/requireNonNull并包装非Harness异常，接口补规则；泛型类型错误不构成当前越权。
- 证据：harness-api/src/main/java/io/jharness/api/EventBus.java:18

## OCR-0114 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/DataSanitizer.java:21；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：DataSanitizer chain wrappers do not validate intermediate results: if first.sanitize or first.streamingText returns null, downstream implementations NPE or silently pass null through, breaking the sanitization chain. Validate intermediate values or fail closed.
- 判断：DataSanitizer链未验证中间null，应明确sanitizer必须非空输出并在组合处fail closed；NPE本身未证明敏感内容被放行。
- 证据：harness-api/src/main/java/io/jharness/api/DataSanitizer.java:21

## OCR-0115 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ExecPath.java:6；OCR原严重度：high
- 计划：R2-01 / P2
- 原意见：ExecPath 的文档契约是“环境相对路径”，但构造函数只做了 null/blank 校验，未拒绝绝对路径（`/etc/passwd`）、Windows 盘符/UNC（`C:\\x`、`\\srv\share`）、`..` 穿越段、反斜杠分隔符、NUL 及其他 ISO 控制字符。当前边界安全完全依赖下游各实现重复校验：`LocalWorkspace.resolve` 会拒绝上述形态，`RemoteWorkspace.safe` 只做了部分校验，而任何新增的 WorkspaceFiles/ExecutionEnvironment 实现（或插件自实现的接口）都会直接接收未校验值并用 `root.resolve(value)` 等拼接，从而逃逸环境根目录；同时 `EnvironmentWorker.dispatch` 第 45 行直接把外部 JSON 的 `path` 构造成 ExecPath，说明该类型确实位于不可信输入入口。建议在 compact 构造器内一次性完成相对性、字符集与 `..` 校验，使非法值在构造期即被拒绝，触发条件为 `new ExecPath("../../etc/passwd")`、`new ExecPath("/tmp/x")`、`new ExecPath("a\u0000b")` 均能通过校验。
- 判断：ExecPath只非空，统一词法边界可减少Local/Remote重复校验；需保留root()/宿主路径既有用法，不能未经调用链审查直接收窄构造器。
- 证据：harness-api/src/main/java/io/jharness/api/ExecPath.java:6

## OCR-0116 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/ExecPath.java:4；OCR原严重度：medium
- 计划：无
- 原意见：record 自动生成的 equals/hashCode 直接比较原始字符串，未做规范化，因此 `ExecPath("a/b")`、`ExecPath("a/./b")`、`ExecPath("a//b")`、`ExecPath("a\\b")` 互相不相等，却可能指向同一文件。这会带来两个可复现问题：以 ExecPath 为键的缓存/去重会重复条目；任何基于相等性的允许/拒绝或版本比较判断都可能被等价路径绕过（例如 `a/./secret.txt` 绕过针对 `secret.txt` 的相等性拒绝）。建议在构造器中统一反斜杠并折叠空段与 `.` 段后再赋值，使类型本身具备路径恒等语义。
- 判断：ExecPath未承诺文件系统恒等语义，平台路径规范化由环境负责；构造器折叠和大小写等会误改环境标识，未发现按record.equals授权的实际绕过。
- 证据：harness-api/src/main/java/io/jharness/api/ExecPath.java:4

## OCR-0117 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/CommandRequest.java:10；OCR原严重度：medium
- 计划：R1-04 / P3
- 原意见：record 的自动 `toString()` 会原样拼接 `argv`、`cwd` 等字段。命令行参数中经常承载凭据（如 `--api-key`、`--token`、`psql` 的连接串），一旦该对象被日志、异常消息或审计事件字符串化（仓库内目前没有任何脱敏组件可兜底），敏感信息即明文外泄。建议显式重写 `toString()`（或提供脱敏视图），对 `argv` 中疑似密钥的参数只输出长度/摘要，而不是原值。
- 判断：CommandRequest默认toString确实包含argv，采纳不打印原始参数的摘要；原文称无脱敏组件不实，现有DataSanitizer存在。
- 证据：harness-api/src/main/java/io/jharness/api/CommandRequest.java:10

## OCR-0118 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/CommandRequest.java:22；OCR原严重度：high
- 计划：R3-01 / P3
- 原意见：`argv` 的不可变拷贝发生在参数校验之前，导致输入校验被绕过/失效： 1. `argv == null` 时 `List.copyOf` 直接抛 `NullPointerException`（无任何可读信息），而本类其余非法输入统一抛 `IllegalArgumentException`，调用方（如 EnvironmentWorker 由 JSON `request.get("argv")` 构造）无法用统一类型捕获； 2. `argv` 含 null 元素时 `List.copyOf` 已在第 22 行抛 NPE，第 25 行的 `s == null` 判断永远不可能命中，属于死代码，也说明“null 元素应被当作校验失败”的意图没有落地。 建议先对原始 `argv` 做 null/元素 null 校验（含阈值与长度校验），全部通过后再 `argv = List.copyOf(argv);` 固化，保证非法输入一律以 `IllegalArgumentException` 拒绝。
- 判断：List.copyOf在显式argv元素校验前拒绝null，属于错误诊断/死分支问题，输入并未绕过校验。
- 证据：harness-api/src/main/java/io/jharness/api/CommandRequest.java:22

## OCR-0119 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/CommandRequest.java:20；OCR原严重度：low
- 计划：R3-01 / P2
- 原意见：`callId` 只经由 `Values.required` 做了非空白校验，该 helper（Values.java:9-13）既无最大长度限制，也不拒绝控制字符（如 `\n`、`\0`）。而 `callId` 来自模型侧输入（EnvironmentWorker 的 `(String) request.get("callId")`、TerminalToolsPlugin 的 `call.callId()`），随后会被写进结果与事件流，超长或含换行的值会造成事件/日志放大与日志注入，与“bounded command”的契约不一致。建议在此处加上长度上限（如 128）并拒绝 ISO 控制字符。
- 判断：callId只required，无长度限额，可统一标识长度；JSON日志换行被转义，不采纳文本日志注入已证实结论。
- 证据：harness-api/src/main/java/io/jharness/api/CommandRequest.java:20

## OCR-0120 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/CommandRequest.java:34；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：所有规则失败共用同一句 `"Invalid bounded command"`，既无法定位是 argv 数量、NUL、长度、shell 模式基数还是 timeout 超界，也无法让上层做出差异化处理（例如把“argv 为空”归为参数错误、把“timeout 超 1 小时”截断重试）。建议在消息中带上具体违约项（如 `"argv must contain 1..256 entries"`），同时保持不含参数内容以避免泄露。
- 判断：CommandRequest把多种边界合为一个错误，采用不含实际argv的字段化消息可改善配置排查。
- 证据：harness-api/src/main/java/io/jharness/api/CommandRequest.java:34

## OCR-0125 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ExtensionPoint.java:7；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：这里 `requireNonNull` 没有传 message：传入 null type 时只会得到无上下文的 NPE（`Cannot invoke ... because "type" is null`），注册扩展点时无法定位是哪个扩展点/哪个插件出错。另外与上一行 `Values.required` 抛 `IllegalArgumentException` 不一致，调用方只捕获 `IllegalArgumentException` 做配置错误处理时会漏掉该 NPE。建议补充组件名并统一异常类型。
- 判断：ExtensionPoint非空已强制，仅具名NPE可改善；无须为了统一而将所有NPE改HarnessException。
- 证据：harness-api/src/main/java/io/jharness/api/ExtensionPoint.java:7

## OCR-0126 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/ExtensionPoint.java:6；OCR原严重度：medium
- 计划：无
- 原意见：`ExtensionPoint` 的 `name` 是 record 身份的一部分，而 `Values.required` 只做 null/blank 校验，不做任何规范化（不去首尾空白、不统一大小写）。因此 `new ExtensionPoint<>("model.execution ", X.class)` 与 `StandardExtensionPoints.MODEL_EXECUTION` 是两个不同的 key，`DefaultEventBus.invoke` 用 `e.point.equals(point)` 过滤拦截器时不会命中任何已注册拦截器，且 `intercept` 注册也不会报错——形成静默失效（同样的点用 `"Model.Execution"` 也会分裂成两个 key）。建议在此处 strip 并拒绝/规范化带首尾空白或大小写不一致的名称，保证同一点只有唯一 key。
- 判断：扩展点name按原样精确身份合法，大小写不同可代表不同点；自动strip/小写会引入碰撞，缺少真实误注册证据。
- 证据：harness-api/src/main/java/io/jharness/api/ExtensionPoint.java:6

## OCR-0127 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ExtensionPoint.java:4；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：`type` 使用 `Class<T>` 保存，泛型实参在构造时即被擦除，`ExtensionPoint<List<String>>` 与 `ExtensionPoint<List<Integer>>`（都用 `List.class`）在 `equals` 上完全相同，`DefaultEventBus` 以 `point.equals(point)` 作为匹配键时二者会互相串用，而 `invoke` 中的 `point.type().cast(request)` 只能校验到 raw `List`，无法阻止把 `List<Integer>` 请求投递给声明为 `List<String>` 的拦截器。建议要么在契约上显式限制扩展点 payload 必须是具体（非泛型）类型并在 Javadoc/校验中强制，要么引入 `java.lang.reflect.Type` 作为身份的一部分以避免擦除冲突。
- 判断：Class<T>只能验证运行时raw类型，应说明载荷使用具体类型；当前第一方扩展点非泛型，暂不引入Type身份。
- 证据：harness-api/src/main/java/io/jharness/api/ExtensionPoint.java:4

## OCR-0128 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ExecutionEnvironment.java:13；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：descriptor() 缺少“键集合/取值稳定”的契约，但它会被持久化进会话绑定：HarnessSdk 构造时 `new LinkedHashMap<>(environment.descriptor())` 后与历史 `sdk.created.binding` 做 equals 比较（HarnessSdk.java:140-166），不一致直接 `ENVIRONMENT_MISMATCH`。默认实现只给了 id/isolation，实现方在其上增删键（如本地实现额外的 kind/trustedProjectRequired、搜索过滤配置）就会改变绑定指纹；而默认值本身（"unspecified"）也没有版本约束，一旦默认实现调整，升级后旧会话将无法恢复。建议在接口文档中明确 descriptor() 的键集合必须向后兼容/稳定，并说明该值参与会话恢复校验。
- 判断：环境descriptor参与SDK恢复binding相等比较，接口需说明稳定版本化和升级兼容责任。
- 证据：harness-api/src/main/java/io/jharness/api/ExecutionEnvironment.java:13

## OCR-0129 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ExecutionEnvironment.java:14；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：两处问题： 1) `Map.of` 拒绝 null，而 `id()` 是抽象方法且接口未声明非空，实现返回 null 时此处直接抛 NPE，且发生在 EnvironmentPlugin.start / HarnessSdk 构造阶段，堆栈不指向真正的实现。 2) 默认 descriptor 只有 id/isolation，缺少终端能力键，但下游用 `"unsupported".equals(environment.descriptor().get("terminal"))` 判定是否支持终端（EnvironmentPlugin.java:40）。依赖默认 descriptor 且 terminal() 实际不可用的实现（仓库内已有抛 `UNSUPPORTED_TERMINAL` 的形态，WorkspaceToolsPlugin.java:88）会被误判为“支持终端”，进而 `new TerminalToolsPlugin(environment.terminal())` 的 `Objects.requireNonNull` 抛 NPE 或注册一个永远失败的服务。建议默认值显式给出终端能力（fail-closed），或要求实现必须覆盖 descriptor 才能被 EnvironmentPlugin 使用；同时对 id() 做非空校验。 3) 另外 `"isolation": "unspecified"` 作为未知隔离的默认值属于 fail-open：若后续有消费方按 isolation 做信任/策略判断，未覆盖 descriptor 的实现会被当成“未知即放行”；建议文档化要求消费方对 unspecified 按不可信处理，或不提供该默认键。
- 判断：默认descriptor未声明terminal支持，补明确能力默认值/查询约定；unspecified目前不是安全放行依据，拒绝假设性fail-open推断。
- 证据：harness-api/src/main/java/io/jharness/api/ExecutionEnvironment.java:14

## OCR-0130 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ExecutionEnvironment.java:11；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：terminal() 没有声明返回实例是否稳定、是否可空、以及由谁负责关闭。实际情况是调用方各自独立调用：EnvironmentPlugin 两次调用 terminal()（第 41、42 行）、WorkspaceToolsPlugin 构造时再取一次（WorkspaceToolsPlugin.java:39-40）、HarnessSdk 又单独调用（HarnessSdk.java:545）。若实现每次返回新会话（仓库内 ReadOnlyChildEnvironment.java:143-148 就是每次 new 一个无状态会话），各方将操作不同会话且丢弃的会话无人关闭；而共享实例又与 LocalExecutionEnvironment.close() 中关闭 terminal 的行为叠加，可能出现重复关闭/关闭后仍被工具使用。建议在接口上明确“返回同一受管实例，生命周期由环境 close() 统一释放”，对不支持终端的场景返回 Optional.empty() 或显式抛出统一错误，而不是允许返回 null。
- 判断：terminal多次取值需声明共享实例与所有权，ReadOnlyChild的无状态拒绝实现不实际泄漏进程；不直接改Optional签名。
- 证据：harness-api/src/main/java/io/jharness/api/ExecutionEnvironment.java:11

## OCR-0131 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ExecutionEnvironment.java:9；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：files()/processes()/terminal() 均未约定非空。EnvironmentPlugin 直接把 `environment.processes()` 注册为服务（EnvironmentPlugin.java:33），WorkspaceToolsPlugin 也直接 `environment.processes()`/`terminal()` 传入构造器（WorkspaceToolsPlugin.java:39-40），一旦实现因某项能力不可用返回 null，NPE 会延迟到远离根因的插件装配/工具调用处。建议在接口层声明非空，能力缺失时用明确的 UNSUPPORTED 异常或 Optional 表达。
- 判断：files/processes/terminal非空及不支持应显式失败可写文档；null当前装配拒绝，不是能力绕过。
- 证据：harness-api/src/main/java/io/jharness/api/ExecutionEnvironment.java:9

## OCR-0132 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ExecutionEnvironment.java:18；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：默认空实现的 close() 会掩盖资源泄漏：该接口实现普遍持有终端会话、子进程和文件句柄，而 EnvironmentPlugin 通过 `context.own(environment)`（EnvironmentPlugin.java:31）依赖 close() 做清理，实现方忘记覆盖时编译期无任何提示、运行期静默不释放。同时接口未约定 close() 必须幂等，而 LocalExecutionEnvironment.close() 会关闭共享的 terminal/processes，若这些实例还被其他插件持有并再次关闭，行为未定义。建议至少在接口文档中声明“实现必须释放其拥有的全部资源且 close() 幂等”，或考虑把 close() 设为抽象（配一个明确的空实现基类供桩使用）以强制实现方处理生命周期。
- 判断：环境拥有资源时必须覆盖close/幂等应文档化；空实现合法无需资源，保留兼容默认。
- 证据：harness-api/src/main/java/io/jharness/api/ExecutionEnvironment.java:18

## OCR-0133 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/Interceptor.java:4；OCR原严重度：low
- 计划：R3-01 / P2
- 原意见：该 SPI 未定义 `Next.proceed` 的调用契约：`DefaultEventBus.invoke` 中的链节点没有任何重入/一次性保护，拦截器若在一次 `intercept` 中调用 `next.proceed(...)` 多次，会将同一请求重复下发到下游拦截器与 terminal，导致下游副作用重复执行；若完全不调用，则会静默短路整条链。同理，`T` 同时承担请求与响应类型，但未说明 `proceed` 传入的请求是否允许被替换、返回 null 是否合法，实现方只能自行猜测。建议在此处补充契约说明（proceed 至多/恰好调用一次、参数与返回值非空、`intercept` 不得吞掉下游 `Exception` 而需原样传播以便调用方恢复）。
- 判断：Next.proceed当前无单次保护，应说明副作用入口不许多次推进；合法短路拦截器不能被要求恰好一次，也不强制所有异常原样传播。
- 证据：harness-api/src/main/java/io/jharness/api/Interceptor.java:4

## OCR-0134 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/HarnessException.java:6；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`code` is a machine-readable contract but is accepted without any validation: `null` or a blank string is stored and returned verbatim by `code()`. Callers rely on it with instance calls such as `ex.code().equals("CANCELLED")` (e.g. BoundedModelExecution, TerminalToolsPlugin, SubagentPlugin), which throw NPE when the code is null, and null/empty codes also erase the error signal in CLI/stdio JSON output (`StdioSdkServer` writes `ex.code()` directly, producing `"error": null`). Validate in both constructors, e.g. `this.code = Objects.requireNonNull(code, "code")` plus a blank check (`if (code.isBlank()) throw new IllegalArgumentException(...)`).
- 判断：HarnessException.code无校验而下游按code.equals路由，统一required可消除二次NPE。
- 证据：harness-api/src/main/java/io/jharness/api/HarnessException.java:6

## OCR-0135 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/HarnessException.java:9；OCR原严重度：low
- 计划：R1-04 / P3
- 原意见：The class javadoc states "messages must not contain secrets", but nothing enforces it: the raw message is passed straight to `super(message)` and the cause is retained, so any caller that interpolates upstream payload text (HTTP/LLM response bodies, config values, env values) into the message or wraps a provider exception will propagate credentials into logs or API responses wherever this exception is logged/serialized. Consider making the redaction responsibility explicit here (e.g. a factory `of(code, publicMessage, cause)` that sets the public message and keeps the raw detail as the cause, or a documented sanitizer applied on message construction) so the contract is mechanically enforceable rather than convention-only.
- 判断：异常消息的保密性依赖调用边界，采纳安全摘要规范/审计实际sink；异常构造器缺secret集合，不能在此可靠统一脱敏。
- 证据：harness-api/src/main/java/io/jharness/api/HarnessException.java:9

## OCR-0136 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelAttempt.java:3；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：该 SPI 契约未定义失败语义，导致“UNKNOWN 不得被重放”无法在接口层约束：调用方 BoundedModelExecution.execute 只对 ModelRejectedException 重试，其他异常直接上抛；而 harness-llm 的 OpenAiCompatibleLlmClient 在传输中断时会抛 MODEL_TRANSPORT_UNKNOWN（结果可能已在服务端生效）。由于本接口没有任何注释说明“非 ModelRejectedException 异常必须被视为终态、实现不得返回 null 或伪造响应”，新实现很容易把 UNKNOWN 结果包装成可重试异常或返回 null，从而被外层循环重放并重复计费/重复发送。同理也未声明 CancellationToken 必须在发起请求前、以及产生持久化副作用（journal/checkpoint/计费）前被检查。建议在类型注释中明确失败与取消契约。
- 判断：ModelAttempt接口无失败说明，明确仅ModelRejectedException可重试、传输UNKNOWN不得再次complete，并引用M8.3契约。
- 证据：harness-api/src/main/java/io/jharness/api/ModelAttempt.java:3

## OCR-0137 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelAttempt.java:6；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：参数语义与可空性未定义，容易被误读：在当前调用链中 {@code singleSend=true} 表示“只发送一次、不做客户端内部重试”（由 BoundedModelExecution 的退避循环负责重试，见 BoundedModelExecution.java:85），{@code ModelExecution.once()} 传 false（见 ModelExecution.java:9），实现里据此外层据以选择 completeSingleAttempt 或 complete（DefaultAgentLoop.java:152）。若实现把 true/false 理解反，会在外层已重试的同时再叠加内部重试，导致重复发送与 429 退避失效。此外 {@code cancellation} 在实现中被无条件解引用（attemptCancellation.throwIfCancelled()），接口却未声明其非空，调用方传入 null 时会在 lambda 内抛 NPE 而非 CANCELLED。建议补充参数说明并标注非空约束。
- 判断：singleSend布尔未说明，补true禁止适配器内部重试、false兼容旧行为及token非空，避免误把once当单wire发送。
- 证据：harness-api/src/main/java/io/jharness/api/ModelAttempt.java:6

## OCR-0138 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelDisplay.java:5；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：公共 API 的脱敏约束只写在注释里，`Map<String, Object>` 无法在类型层面阻止实现返回未脱敏文本或可执行工具事实；当前实现 ModelDisplayBuffer 依赖调用方自觉，而 harness-web（HarnessWebServer 直接把 snapshot 序列化下发）等调用方不会二次校验。建议在 Javadoc 中明确约定：所有值必须经 DataSanitizer 脱敏、禁止携带 tool call/凭证等可执行事实，并在 SDK 侧对返回键值做白名单校验，以免新增实现时契约漂移造成敏感信息泄漏。
- 判断：ModelDisplay已有展示非事实方向，补已脱敏/固定白名单结果字段；Map类型本身不能强制防恶意可信实现，不能据此声称已泄漏。
- 证据：harness-api/src/main/java/io/jharness/api/ModelDisplay.java:5

## OCR-0139 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelDisplay.java:7；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：接口未约定 snapshot() 的防御性拷贝/不可变性与线程安全语义：现有实现 ModelDisplayBuffer.snapshot() 在 synchronized 下返回 Map.of(...)（不可变防御性拷贝），但只要后续实现返回内部可变 Map，调用方（如 Web 轮询线程）就可能修改共享显示状态或触发 ConcurrentModificationException。同时未声明返回 Map 是否可为 null、键集合与值类型（如 truncated 必须为 boolean）是否固定，调用方只能靠 get("text") 后再强转。建议在方法上补充契约说明（返回不可变快照、禁止 null、固定键集合/类型、可在多线程下调用），或改用不可变记录类型替代 Map<String, Object>。
- 判断：snapshot实现同步返回Map.of快照，接口应明确非空、线程安全和字段/截断类型，供替换实现遵守。
- 证据：harness-api/src/main/java/io/jharness/api/ModelDisplay.java:7

## OCR-0140 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/HarnessPlugin.java:10；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`apiVersion()` 的默认实现恒返回 `ApiVersion.CURRENT`，使兼容性校验失去意义：`PluginContainer.visit`（harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java:128）用 `ApiVersion.CURRENT.equals(descriptor.apiVersion)` 做加载期校验，而仓库内所有生产插件均未覆写该方法（仅 PluginContainerTest 覆写），因此该校验对任何插件恒为 true。基于旧版 API 编译的插件缺少新方法时不会被加载期拒绝，而是在首次调用缺失方法时抛出 `NoSuchMethodError`/`AbstractMethodError`（属于 Error，`PluginContainer.start` 只捕获 RuntimeException，不会触发 cleanup 回滚，已加载的插件与上下文资源会泄漏）。建议去掉默认实现改为抽象方法，或在构建/打包阶段由注解处理器、插件清单写入编译期 API 版本，而不是在运行期用当前版本填充。
- 判断：HarnessPlugin.apiVersion默认运行时CURRENT不能可靠声明第三方编译版本，应明确构建描述符来源并补兼容测试；不因第一方同构建插件而立即移除兼容默认。
- 证据：harness-api/src/main/java/io/jharness/api/HarnessPlugin.java:10

## OCR-0141 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/HarnessPlugin.java:18；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`start`/`stop` 未声明任何失败与回滚语义，而运行时实际会以“部分启动”状态调用 `stop()`：`PluginContainer.start`（PluginContainer.java:88-92）先构造 `Running` 并入栈再调用 `plugin.start(context)`，启动抛异常后 `cleanup()`（PluginContainer.java:163）会对该未成功启动的插件执行 `stop()`；容器在 FAILED 后 `close()` 也会再次进入 `stop()`。若实现按“start 成功后 stop 才被调用”的直觉编写（例如在 stop 中直接释放 start 内才创建的线程池/文件句柄，或释放已被 start 自身 catch 回滚过的资源），会出现 NPE、重复释放或空指针掩盖原始异常（cleanup 中仅以 suppressed 追加）。建议在接口契约中明确：`start` 失败时必须自行回滚不可由 `PluginContext` 托管的副作用；`stop` 必须幂等、可在 `start` 未调用或未成功完成时安全调用；两者只允许抛出 RuntimeException，且 `stop` 不得抛出会中断后续插件清理的错误。
- 判断：PluginContainer先running.add再start，失败会调用stop，接口应说明部分启动及幂等清理；不能承诺捕获所有不可恢复VM错误。
- 证据：harness-api/src/main/java/io/jharness/api/HarnessPlugin.java:18

## OCR-0142 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/HarnessPlugin.java:6；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：`id()`/`version()` 未声明非空、非空白及全局唯一约束，但运行时把 `id` 直接用作插件注册表键和配置查表键（PluginContainer.java:58、63、87），并把 `version()` 当作依赖精确匹配的凭据（PluginContainer.java:139）。实现返回 null/空白时只会得到不指明来源的 `IllegalArgumentException("plugin id is required")`，重复 id 只报 `DUPLICATE_PLUGIN` 而不含冲突来源；若 id 含空白或与依赖声明中的 id 大小写/空白不一致，依赖解析会误报 `MISSING_DEPENDENCY`。建议在接口 Javadoc 中显式约定：`id()`、`version()` 非空、非空白，`id()` 在容器作用域内唯一且与 `PluginDependency.id()` 逐字符一致。
- 判断：容器对id/version执行required并精确依赖匹配，接口应记录非空、作用域唯一、逐字符匹配。
- 证据：harness-api/src/main/java/io/jharness/api/HarnessPlugin.java:6

## OCR-0143 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/FileSystemView.java:8；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：root() 虽然暴露了工作区根，但 readText/writeText 的契约没有要求 ExecPath 必须位于 root 之内；ExecPath 记录本身只校验非 null，允许 "/etc/passwd"、"../x"、含驱动器号或符号链接段的值。LocalWorkspace 在 resolve 中做了边界校验，但该接口是公开扩展点，其他实现（如 RemoteWorkspace 直接把 path 透传给 worker）没有强制约束，缺少校验的实现可能读取工作区外文件或越界写入。建议在接口契约中明确规定所有实现必须拒绝绝对路径、包含 ".." 的路径及指向 root 之外的符号链接，或把边界校验收敛到 API 层的共享校验工具。
- 判断：FileSystemView未写root边界，补各实现必须防逃逸的契约；不认定现有LocalWorkspace突破。
- 证据：harness-api/src/main/java/io/jharness/api/FileSystemView.java:8

## OCR-0144 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/FileSystemView.java:14；OCR原严重度：high
- 计划：R2-01 / P2
- 原意见：VersionGuard 只有 expectedVersion 字符串，缺少“无前置条件 / MISSING / UNKNOWN”的显式表示，导致各实现语义已经分叉：LocalWorkspace.writeText 对 null 直接 Objects.requireNonNull 抛 NPE，而 RemoteWorkspace.writeText 把 null 当作“回退到最近一次 observed 版本”的默认值（guard == null ? null : ...）。于是同一个 writeText 调用在本地和远端实现上产生的并发写结果不同，遗忘 guard 时可能在远端静默覆盖。另外 expectedVersion 为 "UNKNOWN"/"MISSING" 等哨兵时接口没有要求拒绝，存在把 UNKNOWN 版本当作可重放前置版本的风险。建议：明确禁止 null，提供 VersionGuard.NONE / MISSING 常量（或改为密封类型/Optional 表达），并在契约中要求拒绝 UNKNOWN 与非法哨兵值。
- 判断：VersionGuard及null语义未固定，统一本地/远端前置条件并补契约测试；UNKNOWN副作用与名为UNKNOWN版本字符串不是同一概念。
- 证据：harness-api/src/main/java/io/jharness/api/FileSystemView.java:14

## OCR-0145 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/FileSystemView.java:10；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：writeText 只声明 IOException 且只在成功时返回 FileChange，失败后的文件状态没有契约。实现 LocalWorkspace.write 在 rename 提交之后读取 afterVersion 失败时会抛 UNKNOWN_SIDE_EFFECT（提交可能已经发生），但接口没有让调用方区分“未提交 / 已提交 / UNKNOWN”的手段，按异常重试会造成重复写入；同时冲突/拒绝路径抛的是未声明的 HarnessException（RuntimeException），仅捕获 IOException 的调用方无法识别。建议显式定义失败语义（例如抛出异常即保证未提交，并用可携带 UNKNOWN 提交状态的结果类型表达不确定态），并说明 beforeVersion/afterVersion 在部分失败时是否可信。
- 判断：写后版本读取失败可能UNKNOWN，应声明异常码和禁止自动重试；不采纳所有异常保证未提交这一不可能通用保证。
- 证据：harness-api/src/main/java/io/jharness/api/FileSystemView.java:10

## OCR-0146 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/FileSystemView.java:12；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：versionOf 未定义缺失文件/不可读文件的返回约定（实现里用字符串 "MISSING" 当哨兵），也未声明返回值非空。调用方如 CliApplication、EvidenceCompletion 直接执行 versionOf(...).equals(...)，一旦某个实现（例如从 worker 响应取 version 的 RemoteWorkspace，字段缺失时会返回 null）返回 null 就会 NPE。建议在接口中定义 MISSING 常量与“不存在返回哨兵、其他 IO 错误抛异常、永不返回 null”的契约，避免字符串哨兵散落在各实现中。
- 判断：versionOf缺少MISSING/不可读/非空约定，补统一哨兵及跨实现契约测试。
- 证据：harness-api/src/main/java/io/jharness/api/FileSystemView.java:12

## OCR-0147 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/FileSystemView.java:16；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：FileChange.diff 携带写入前后的原始文件内容，接口既没有脱敏约定也没有长度上限；EnvironmentWorker.change 会把它原样放入跨进程 worker 响应，调用方若在事件、日志或工件中持久化前未经过 DataSanitizer，文件中的密钥/令牌就会泄漏到会话记录或遥测中。建议在接口层要求 diff 在离开实现前脱敏（或明确调用方必须先经 DataSanitizer 处理），并限制 diff 大小以避免无界数据进入协议与日志。
- 判断：FileChange.diff原始数据需要清晰脱敏归属及限额，采纳在持久化/对外展示边界保护；执行环境内部不能无条件改原始文件语义。
- 证据：harness-api/src/main/java/io/jharness/api/FileSystemView.java:16

## OCR-0148 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/EventSupport.java:32；OCR原严重度：high
- 计划：R1-04 / P1
- 原意见：广播的是未脱敏的原始事件：`SessionWriter` 往往是 `SanitizingSessionWriter`，它只在内部对副本调用 `sanitizer.sanitize(event.payload())` 后写入存储（见 harness-session/.../SanitizingSessionWriter.java:18-29），而这里 `event` 仍持有调用方传入的原始 payload。触发条件：调用方把含密钥/令牌/用户原文的 payload 交给 `EventSupport.append`（例如 `EventRequestJournal.record` 直接把 ChatRequest 的 messages 作为 request.context payload），随后 `bus.notify`/`bus.serial` 订阅者或插件收到的就是未脱敏内容，同时与 JSONL 中已脱敏的同 eventId 事件不一致，构成脱敏绕过与外泄通道。建议在 append 前用同一 DataSanitizer 统一脱敏，或让 writer 返回真正持久化后的事件并广播该事件。
- 判断：EventSupport先writer.append再bus.publish同一原对象；SanitizingSessionWriter只写脱敏副本，观察者仍收到原始payload，需让广播与持久事件一致。
- 证据：harness-api/src/main/java/io/jharness/api/EventSupport.java:32；harness-session/src/main/java/io/jharness/session/SanitizingSessionWriter.java:append

## OCR-0149 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/EventSupport.java:31；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：append 与 publish 之间没有原子性也没有异常隔离：`DefaultEventBus.publish` 会同步抛出 `BUS_CLOSED`（ensureOpen）或 `RECURSIVE_EVENT`（监听器内再次发布，rejectRecursive）。触发条件：会话关闭后或某个 notify/serial 回调里经由插件再次 append，此时 `writer.append(event)` 已经把事实写入日志，但异常继续向上抛出，调用方认为写入失败（例如 DefaultAgentLoop.checkpoint 的 catch 会再发 checkpoint.failed 并重试），而每次重试都会用新的 `UUID.randomUUID()` 生成新 eventId，导致日志中出现重复且无法去重的事实。建议显式处理 publish 失败（记录诊断但不让已持久化的事实被当成失败重放），并考虑在 append 前固定 eventId 以保证幂等。
- 判断：append成功后publish可BUS_CLOSED/RECURSIVE_EVENT抛错，需区分持久化成功和通知失败；不能简单吞所有serial/关键派发失败，更不能自动重试副作用。
- 证据：harness-api/src/main/java/io/jharness/api/EventSupport.java:31

## OCR-0150 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/EventSupport.java:40；OCR原严重度：medium
- 计划：R1-01 / P1
- 原意见：决策为 null（或未来新增的枚举值）时会走到 `writer.flush()`，从而绕过上面的 durable 校验，把“需要 DURABLE_FLUSH 的检查点”静默降级为普通 flush。触发条件：`Checkpoints.beforeTopLevelTool` 等实现返回 null 或新增枚举常量而未同步此处。建议显式 `Objects.requireNonNull(decision)` 并用 switch 覆盖三种决策，未知/空值直接拒绝，避免检查点语义被悄悄弱化。
- 判断：checkpoint(null)满足decision!=MEMORY_ONLY而只普通flush，绕过durable确认；应明确拒绝null决策并穷尽枚举。
- 证据：harness-api/src/main/java/io/jharness/api/EventSupport.java:40

## OCR-0151 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/EventSupport.java:27；OCR原严重度：low
- 计划：无
- 原意见：这里在 level 与关联字段不匹配时静默丢弃 turnId/stepId/callId，而不是拒绝：调用方（例如插件）给 SESSION 级事件传入 turnId 时关联信息被悄悄抹掉，追踪链断裂且无任何报错；这也让 `SessionEvent` 构造器中“Unexpected association fields”的校验在 EventSupport 路径上永远无法触发。建议对 level 不匹配但字段非空的入参直接抛出 IllegalArgumentException，暴露调用方错误。
- 判断：EventSupport按level裁剪可选关联字段是其便捷构造设计，多数调用统一传当前上下文；强制拒绝多余字段会破坏现有合法调用。
- 证据：harness-api/src/main/java/io/jharness/api/EventSupport.java:27

## OCR-0152 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelCapabilities.java:7；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：`parallelTools` 与 `tools` 之间缺少不变量校验：当前可以构造 `tools=false, parallelTools=true` 的矛盾能力描述（构造器只校验 `maxContextTokens`）。一旦下游按 `parallelTools` 开启并发工具调用，就会向声明不支持工具的模型发起 tool 调用，表现为协议错误或无效请求；这类配置错误在构造期是零成本可拦截的。建议在紧凑构造器中补充 `if (parallelTools && !tools) throw new IllegalArgumentException(...)`。
- 判断：parallelTools=true且tools=false可构造，应明确能力不变量并在配置边界拒绝矛盾；未证明现有发送绕过toolsSupported。
- 证据：harness-api/src/main/java/io/jharness/api/ModelCapabilities.java:7

## OCR-0153 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelCapabilities.java:10；OCR原严重度：low
- 计划：无
- 原意见：校验失败时异常信息只包含字段名，未携带实际取值，且抛出的是 `IllegalArgumentException` 而非项目统一的 `HarnessException(code, message)` 协议。若配置层（如 CliConfig 之外的调用方）以 0 作为 int 默认值构造该记录，最终会以无错误码的裸异常冒泡到 CLI，用户无法从输出定位是 `contextTokens` 配置非法还是其他问题。建议消息中包含非法值，或改抛带 `INVALID_CONFIG` 码的 `HarnessException`。
- 判断：ModelCapabilities构造期IllegalArgumentException是合理编程契约，字段名已定位错误；不要求所有API值对象统一使用业务异常。
- 证据：harness-api/src/main/java/io/jharness/api/ModelCapabilities.java:10

## OCR-0154 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelExecution.java:6；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：执行策略契约缺少关键语义约束：未说明实现是否可以多次调用 `attempt.complete(...)`（每次尝试都会重新 prepare、预留费用、写 checkpoint 并发起真实请求，见 DefaultAgentLoop 第 79-160 行）、未说明传入的 `cancellation` 由谁负责在派发前检查、未说明返回值是否允许为 null，也未说明失败语义（哪些异常算“可信的明确拒绝”从而允许重试，哪些属于传输失败结果 UNKNOWN 而禁止重放）。按 M8 契约，只有可信适配器明确抛出 `ModelRejectedException`(HTTP_429) 才具备重试资格，500/503 与未知传输错误都不得默认重试；当前 SPI 没有把这条写进契约，宿主自定义实现可能把 UNKNOWN 结果当作可重试并重放该步骤，造成重复计费/重复副作用。建议在接口和方法 Javadoc 中补充：attempt 至多完成一次、取消检查责任归属、返回值非空、仅显式 429 可重试、UNKNOWN 结果不得重放。
- 判断：ModelExecution需明确仅可信429重试、UNKNOWN不重放及令牌责任；attempt多次是标准重试本意，原文至多完成一次要求错误。
- 证据：harness-api/src/main/java/io/jharness/api/ModelExecution.java:6

## OCR-0155 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelExecution.java:9；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：内置 once() 在委派前既不校验参数也不检查取消：传入一个已取消的 `CancellationToken`（或 null）时会直接调用 `attempt.complete(...)`。DefaultAgentLoop 内的 attempt 恰好在开头做了 `throwIfCancelled()` 才未暴露问题，但 ModelAttempt 是 SPI 扩展点，宿主自带实现若省略该检查，就会在已取消后仍发起一次付费模型请求。建议在委派前调用 `cancellation.throwIfCancelled()` 并对两个参数做非空校验。另外，硬编码的 `false` 是 `ModelAttempt.complete(boolean singleSend, ...)` 的 singleSend 标志，取 false 会走 `LlmClient.complete` 的“沿用旧行为”路径（旧适配器内部仍可能对 429 退避重试），与 once() 名称暗示的“单次发送”不一致；保留该值以兼容旧适配器可以接受，但应在方法 Javadoc 中明确说明。`java.util.Objects` 需补充 import。
- 判断：once实际complete(false)保留旧适配器行为，采纳名称语义/取消前置说明及边界检查；DefaultAgentLoop已在回调入口取消检查。
- 证据：harness-api/src/main/java/io/jharness/api/ModelExecution.java:9

## OCR-0156 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/LlmClient.java:4；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：close() 的默认空实现使资源生命周期完全依赖实现方自觉：第三方适配器持有 HTTP 连接池、线程池或原生句柄却漏写 close 时，调用方在 try-with-resources 中不会收到任何异常或提示，连接会静默泄漏；同时该签名不声明受检异常，实现方也无法把释放失败上报给调用方。建议在 javadoc 中明确 close 必须释放传输层资源、要求幂等且可重复调用，并说明关闭后 complete/stream 的预期行为，避免适配器把“默认空实现”误当作无需释放资源的信号。
- 判断：LlmClient默认close可用于无资源替身，补拥有资源应释放和幂等责任；去除受检异常不阻止包装上报。
- 证据：harness-api/src/main/java/io/jharness/api/LlmClient.java:4

## OCR-0157 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/LlmClient.java:19；OCR原严重度：high
- 计划：R1-06 / P2
- 原意见：默认的 fail-closed 只能在真正调用时才暴露，缺少可供调用方提前判定的能力查询，触发条件：用户提供未覆写 completeSingleAttempt 的自定义/旧适配器并启用 ModelResilienceOptions.enabled=true。DefaultAgentLoop 会先写入 model.requested、checkpoint，再调用 completeSingleAttempt（DefaultAgentLoop:123-156），此处抛出 SINGLE_ATTEMPT_UNSUPPORTED 后循环仅把任务标为 FAILED，但该 model.requested 没有对应的 assistant.message/model.rejected/model.verified，SessionRecovery.pendingRequests 无法清空（SessionRecovery:25-45），后续重开该会话会被 HarnessSdk/Cli 以 UNKNOWN_SIDE_EFFECT 拒绝恢复——一次本应“发送前失败”的本地能力缺失被持久化成需要人工核实的未知副作用。建议在接口上提供可预先查询的契约（如 default boolean supportsSingleAttempt() { return false; }），调用方在写入 model.requested 之前校验，或明确要求在落盘请求前完成该检查。
- 判断：默认singleAttempt不支持时Loop在journal后才失败，可提前能力检查避免制造可避免未决；保持旧未知适配器fail closed。
- 证据：harness-api/src/main/java/io/jharness/api/LlmClient.java:19

## OCR-0158 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/LlmClient.java:8；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：默认实现静默丢弃观察者且返回 this，调用方无法区分“已接入显示流”与“该适配器不支持显示”。触发条件：HarnessSdk 构造时传入未覆写 withDisplay 的适配器（例如自定义 LlmClient、FakeLlm），HarnessSdk:146 的 model.withDisplay(display::accept) 表面成功，但 ModelDisplayBuffer 永远收不到快照并保持 IDLE，UI 侧会把“不支持”误判为“模型暂无输出”。建议在契约中明确该默认行为并让调用方显式探测，或在默认实现中拒绝而不是静默吞掉观察者（至少 requireNonNull(display) 并对不支持场景给出可判定信号）。
- 判断：withDisplay默认返回this意味着不支持展示，补能力文档；FakeLlm无流式显示是合法，不应默认拒绝所有调用。
- 证据：harness-api/src/main/java/io/jharness/api/LlmClient.java:8

## OCR-0159 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelRejectedException.java:7；OCR原严重度：medium
- 计划：无
- 原意见：文档声称该类是“可信适配器证据”（trusted adapter proof），但只要 `public` 无参构造可用，任何进程内组件（包括不可信的第三方插件、测试代码、任意业务代码）都能直接 `new ModelRejectedException()` 伪造“已完整收到非流式 429”这一事实。下游 `BoundedModelExecution`（第 88-109 行）与 `ObservedLlmClient`（第 72 行）正是仅凭异常类型给予额外重试资格，因此这是可被伪造的信任边界：伪造者可无成本地触发 `maxAttempts` 次真实模型调用，放大计费与上游限流压力（参见 M8 契约中“普通错误代码不能获得重试资格”的约束）。建议把构造入口收窄为携带可验证证据的工厂方法（如显式传入 `single=true`、`statusCode=429`、响应完整性标志），并将构造器改为包内可见，同时补充“仅适配器可构造”的契约说明，避免插件作用域内的伪造。
- 判断：Java进程内插件/适配器是可信宿主代码，public异常类型不是防恶意代码的安全边界；额外参数也可被伪造，不能解决声称漏洞。
- 证据：harness-api/src/main/java/io/jharness/api/ModelRejectedException.java:7

## OCR-0160 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelRejectedException.java:8；OCR原严重度：medium
- 计划：R1-06 / P3
- 原意见：异常携带的拒绝上下文被丢弃：消息被硬编码为 `"MODEL_RATE_LIMIT"`，既没有 `cause`，也没有 `Retry-After`、`request-id`、provider/model 等结构化字段。后果有两点：(1) 上游返回的 `Retry-After` 无法传递，`BoundedModelExecution` 只能使用固定的指数退避（100ms/200ms…，上限 10s），无法遵循服务端要求，容易在限流窗口内继续打满配额；(2) 重试耗尽后 `BoundedModelExecution` 第 90 行同样抛出 code 为 `MODEL_RATE_LIMIT` 的 `HarnessException`，与本异常在日志/审计中完全同码，无法区分“适配器证明的 429”与“重试次数耗尽”，排障与指标统计会失真；同时缺少 cause 也会切断原始 HTTP 异常的堆栈链。建议增加带 `retryAfter`/`requestId`/`cause` 的构造入口或结构化字段，并让审计/日志使用可区分的 reason。
- 判断：429结构上下文与retry-after支持可独立增强，采纳不含秘密的结构诊断；原契约明确固定退避，不把缺RetryAfter认定当前错误。
- 证据：harness-api/src/main/java/io/jharness/api/ModelRejectedException.java:8

## OCR-0161 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelRouter.java:9；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：接口未约定 select 的错误语义：route 为 null、未知路由、路由不可用（缺 key/网络不可达）时是抛异常、返回 null 还是回退到默认适配器均未定义。现有调用方（如 RoutedLlmClient 构造时直接保存 `router.select(route)` 的结果并在后续 complete/stream 中解引用）无法据此防御性处理，一旦实现返回 null 会在调用点抛 NPE 而非可识别的路由错误；实现若静默回退则会造成误路由到错误模型。建议在 Javadoc 中显式约定：非空返回、重复选择是否复用同一实例、未知/null 路由抛出的异常类型。
- 判断：ModelRouter.select缺非空/未知路由/禁止静默回退契约，补与现实现一致规则。
- 证据：harness-api/src/main/java/io/jharness/api/ModelRouter.java:9

## OCR-0162 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelRouter.java:7；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：生命周期契约缺失：close 之后 routes()/select() 是否仍可用、close 是否幂等、并发调用是否安全、close 是否会关闭已选出的 LlmClient 都没有说明。AutoCloseable 只保证 close 可被调用，调用方无法判断关闭后的选择是抛异常还是返回已释放的适配器，容易产生 use-after-close。同时 routes() 只返回裸 Set，未声明是否为不可变快照，也未保证其与 select() 的可见性一致（可能出现 routes() 中列出但 select() 拒绝的路由）。建议在接口层补充 Javadoc 约定这些语义，必要时用 `@throws` 明确关闭后调用的行为。
- 判断：ModelRouter无close/实例共享/routes快照责任说明，补生命周期契约和实现一致性检查。
- 证据：harness-api/src/main/java/io/jharness/api/ModelRouter.java:7

## OCR-0163 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/PluginContext.java:4；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：The public plugin contract leaves key failure/lifecycle semantics unspecified, and third-party plugins cannot verify them from this file: (1) `register` does not state whether a duplicate `(type, key)` replaces, throws, or silently wins, nor whether `type`/`key`/`implementation` may be null (the container implementation throws for scope mismatch and owns the returned handle, but that is not visible here); (2) `own` does not state that the handle is idempotent, that resources are closed in reverse registration order, whether close exceptions are aggregated, and what happens to a resource supplied after the context is closed. Since `Registration.close()` is declared without checked exceptions (see Registration.java) while `AutoCloseable.close()` throws `Exception`, plugins also cannot tell how a checked close failure surfaces. Documenting these guarantees (or referencing the container behavior) avoids plugins relying on accidental implementation details.
- 判断：PluginContext接口未写重复注册、own逆序/聚合和关闭后注册行为，而容器实际依赖这些语义，应补。
- 证据：harness-api/src/main/java/io/jharness/api/PluginContext.java:4

## OCR-0164 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/PlanPreviewer.java:7；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：返回值契约缺失：未声明 `Map` 可否为 null、是否可变/可共享。两个现有调用点均未做空值校验——`DryRunPipeline` 第 46-47 行与第 63 行直接执行 `sanitizer.sanitize(plan)`、`Json.text(plan)`，`CliApplication` 第 1267 行把返回值直接放进列表后由 `Json.text` 序列化。第三方实现（这是公开扩展点）一旦返回 null，就会在远离责任代码的调用方抛 NPE（`Json.text`/`DataSanitizer` 内部）。建议在 Javadoc 中显式要求“必须返回非 null、不可变 Map”，或由调用方 `Objects.requireNonNull` 兜底。另外该 Map 的 key 结构（`previewable`/`executed`/`reason`/`diff` 等）在接口层完全未定义，已导致调用方各自拼装不同结构（如 `DryRunPipeline` 的 DENY 分支带 `previewable`，`CliApplication` 的 DENY 分支没有），建议在接口 Javadoc 中固定 key 契约。
- 判断：PlanPreviewer的Map字段/非空约定缺失，可固定最小schema；不因任意第三方返回null认定现行故障。
- 证据：harness-api/src/main/java/io/jharness/api/PlanPreviewer.java:7

## OCR-0165 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/PlanPreviewer.java:6；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：接口未定义取消与失败语义，调用方的行为因此不可预期：(1) `CancellationToken` 只是作为参数传入，没有任何契约要求实现“在执行长耗时预览时必须轮询并抛出取消异常”，实现可以无限期执行，绕过调用方的超时/取消（`DryRunPipeline` 第 29、46 行依赖该 token 控制取消）；(2) 未说明失败时是抛异常还是返回 `previewable=false` 的降级结果——现实现 `LocalPlanPreviewer` 选择抛 `HarnessException`（FILE_EXISTS/EDIT_MATCH_CONFLICT 等），而 `CliApplication.offlinePlan` 的循环（第 1255-1268 行）没有 try/catch，单个 call 的预览冲突会直接中断整批计划输出，调用方无法区分“预览失败”与“预览为空”，也无法安全重试。建议在 Javadoc 中明确取消必须被响应、失败必须以异常还是降级结果表达、以及 preview 是否允许产生副作用（当前仅由实现类的类注释说明“只读”）。
- 判断：preview只读、合作取消和预期冲突结果缺少公共说明，采纳契约及CLI批次失败展示；不能承诺强杀任意Java实现。
- 证据：harness-api/src/main/java/io/jharness/api/PlanPreviewer.java:6

## OCR-0166 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/PluginDependency.java:6；OCR原严重度：low
- 计划：无
- 原意见：校验语义与文档声明存在差距：此处仅做“非空”校验，`"^1.0"`、`">=1"`、`"latest"` 等版本范围/标签字符串都会被原样接受并存入 record；插件 ID 也未做字符规整（`" base"` 与 `"base"` 是两个不同的依赖）。真正的“精确版本”约束只在 `PluginContainer.visit()` 中以 `target.version.equals(dependency.version())` 间接体现，构造阶段并不拒绝非法坐标，因此依赖声明错误的报错点会延后到容器启动阶段，且 ID 前导/尾随空白只会表现为 `MISSING_DEPENDENCY`。建议在构造器中显式校验 ID 形状（如 `[A-Za-z0-9._-]+`）并对 version 做 trim/格式校验，或在 Javadoc 中明确“范围字符串不在本类拒绝，而是由解析方按精确匹配失败”，避免调用方误以为传入即校验。
- 判断：插件版本是精确不透明字符串而非强制SemVer，范围字符串按普通字面量精确匹配必然失败，无需构造器拒绝所有非SemVer。
- 证据：harness-api/src/main/java/io/jharness/api/PluginDependency.java:6

## OCR-0167 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/PromptComposer.java:4；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`compose` 是每次模型请求的唯一组装入口，但接口没有定义任何契约：`projection` 为 null 时的行为、返回值是否必须完全由 `projection` 的当前事件派生（即同一投影必须产生等价请求、不得依赖实现内部缓存的“权威聊天状态”）、输出是否有界（消息条数/总字节/工具声明上限），以及敏感信息（凭据、token、环境变量值）的脱敏由实现负责还是由调用方负责。docs/development/M1-contracts.md 明确要求“每次模型请求可从事件重建”，缺少这些约定时，第三方实现很容易把未脱敏内容或无界历史直接塞进 `ChatRequest`，破坏重放一致性与“有界数据处理”要求。建议补齐 Javadoc，明确 null 传入策略、幂等/可重建性、有界输出与脱敏责任。
- 判断：PromptComposer应引用M1按投影可重建和脱敏边界；统一规模上限需兼顾预算/压缩，不盲目添加固定消息数。
- 证据：harness-api/src/main/java/io/jharness/api/PromptComposer.java:4

## OCR-0168 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/PromptComposer.java:3；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：接口未声明线程模型/状态约束。`compose` 会在每个模型步骤被调用，实现实例通常由 `AgentLoop` 注入并被同一会话（或跨会话）复用；若实现把跨调用的可变状态放在字段里（例如拼接式消息缓存、计数器），并发调用会出现竞态并破坏请求可重建性。建议在类型级 Javadoc 中明确“实现必须无状态，或在多个会话共享时保证线程安全；不得在 compose 内维护跨调用可变状态”，使 SPI 契约可验证。
- 判断：补同会话线程模型与可重建性约束；强制所有composer无状态过严，可安全缓存派生数据。
- 证据：harness-api/src/main/java/io/jharness/api/PromptComposer.java:3

## OCR-0169 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/PolicyExtensionPoints.java:5；OCR原严重度：high
- 计划：无
- 原意见：扩展点的载荷是整个 `PolicyEngine`，而 `EventBus.invoke` 会把拦截器链的返回值直接当作有效策略使用（`DefaultToolPipeline.java:76-84` 只做了 `requireNonNull`，没有校验返回对象的身份或重新施加必选守卫）。触发条件：插件或调用方在 `policy.evaluate` 上注册拦截器并返回一个恒返回 `ALLOW` 的自定义 `PolicyEngine`，随即执行任意工具调用——`DefaultPolicyEngine.forbiddenPath` 的绝对路径/`..`/`.env`/`.git` 边界检查与 `execute_command` 的精确 argv 模板白名单都会被整体绕过，这与本行注释“Scope and durable intent guards remain mandatory in the pipeline”的契约相矛盾。建议把扩展点载荷改为“调用+上下文 → Decision”这类结果级类型，由管线在调用扩展点之后仍强制复用原始引擎的必选守卫（至少在管线侧拒绝返回引擎身份被替换），并同步修正该契约注释。
- 判断：POLICY扩展点本来允许可信宿主替换策略，注释保证的是scope/durable intent仍由Pipeline强制；把DefaultPolicy所有规则称不可覆盖边界属于误读。
- 证据：harness-api/src/main/java/io/jharness/api/PolicyExtensionPoints.java:5

## OCR-0170 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/PreparedToolCall.java:7；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`run()` 缺少失败语义与非空契约。接口既未声明返回不可为 `null`，也未说明抛出运行时异常/取消/超时时调用方应如何结算：若在工作线程中直接抛出（如 `BoundedReadOnlyToolScheduler.launch` 的 catch 分支），owner 线程侧仍必须调用一次 `finish()` 才能释放准备阶段占用的资源，否则该 prepared call 永远未结算；实现方也可能把它当作“失败即结束”而跳过结算。同时未禁止在 worker 线程读取 owner 线程可变状态（如 `DefaultToolPipeline.Call.outcome/finished` 这类字段），并发下会产生可见性与顺序问题。建议在 SPI 上明确：不得返回 null；不得触碰 owner 线程可变状态；异常须由调用方在 owner 线程用非 SUCCESS 结果完成结算。
- 判断：PreparedToolCall.run缺worker/owner分责与异常后finish责任，补调度协议契约以免资源及结算遗漏。
- 证据：harness-api/src/main/java/io/jharness/api/PreparedToolCall.java:7

## OCR-0171 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/PreparedToolCall.java:9；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：`finish()` 缺少“恰好一次”与非空/UNKNOWN 的契约，这是整个调度协议的关键校验点：调用方在 worker 超时或返回 `null` 时（`BoundedReadOnlyToolScheduler.drain` 里就对 `task.result` 做了 null 兜底）会把任意 `ToolResult` 传入结算，实现方若不校验 `callId`/是否真正执行过，就可能把 UNKNOWN 结果当作已结算结果重放，甚至伪造 SUCCESS。建议在接口层写明：必须在 `run()` 返回或抛异常后恰好调用一次；`result` 不得为 null；UNKNOWN/`sideEffectUncertain` 不得被降级为已确认的成功，重复结算必须显式失败。
- 判断：finish应恰好一次/同call且UNKNOWN不可降级，但超时未合作worker仍运行时owner需结算UNKNOWN，不能要求必须等run结束后才finish。
- 证据：harness-api/src/main/java/io/jharness/api/PreparedToolCall.java:9

## OCR-0172 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/PreparedToolCall.java:4；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：接口缺少“已准备但永不执行”的放弃路径。`prepare()` 在 owner 线程已经完成了事件发布、策略/审批、checkpoint 等副作用（见 `DefaultToolPipeline.prepare`），但调度器在 prepare 之后若批处理已停止会直接 `break`（`BoundedReadOnlyToolScheduler` 第 51 行），此时既不会调用 `run()` 也不会调用 `finish()`，准备阶段占用的 owner 线程资源无法释放，也不会产生 `tool.completed` 事实，恢复/审计上出现“已准备未结算”的空洞。建议在 SPI 上提供显式的 abort/release 契约，要求实现方在调用方放弃时释放资源且不得重放工具主体。
- 判断：DefaultPipeline.prepare会先tool.requested，barrier在前序stopped时抛错被转preliminary；Scheduler随后if(run.stopped) break丢弃该prepared未finish，补已准备放弃结算契约/事件闭合；此分支尚未进入本工具body，不称已重放副作用或实际资源泄漏。
- 证据：harness-api/src/main/java/io/jharness/api/PreparedToolCall.java:4；harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:execute；harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java:Call.prepare

## OCR-0173 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/RequestJournal.java:5；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：类级 Javadoc 只声明『记录最终对模型可见的信封』，没有要求实现方在落盘前脱敏。`record` 的入参 `ChatRequest` 含 messages/tools/parameters，工具调用参数里常带 Authorization/API Key/环境变量值，若实现直接写入 `request.context` 事件（当前 `EventRequestJournal` 就是原样写入 payload），凭据会永久留在 session 日志中，并随 `reconstruct` 再次进入模型上下文。建议在接口契约中明确：禁止记录原始凭据，必须经统一脱敏（含 tools 描述、parameters 与环境变量占位化）后才能持久化，且脱敏失败应中止记录而不是降级记录。
- 判断：Journal应说明接收最终已脱敏信封、不得自行改变线上请求；现有SanitizingSessionWriter兜底，不认定直接永久明文落盘。
- 证据：harness-api/src/main/java/io/jharness/api/RequestJournal.java:5

## OCR-0174 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/RequestJournal.java:7；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`record` 的返回值和失败语义均未定义，而实现会先写 `request.context`、再写引用它的 `model.requested` 并返回后者的 eventId。契约缺失导致：1) 调用方无法判断返回 id 是否非空/唯一（能否作为后续 `reconstruct` 的键）；2) `writer` 与 `events` 之间无顺序与原子性约定，若第二次 append 失败，会残留一条无引用的 `request.context`，或在广播失败时出现事件已落盘但调用方未拿到 id，从而让重建结果与当时实际发出的请求分叉；3) 未声明入参（request/writer/events）为 null 时的行为。建议补充 `@return`（非空、指向已 flush 的 model.requested 事件）、`@throws HarnessException` 以及“记录失败不得留下可被重建的半成品”的约束。
- 判断：record返回requested事件ID及调用方checkpoint责任需明确；返回前已flush并非当前实现保证，不能凭建议伪装已有契约。
- 证据：harness-api/src/main/java/io/jharness/api/RequestJournal.java:7

## OCR-0175 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/RequestJournal.java:15；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`reconstruct` 完全没有方法级 Javadoc，`events` 与 `requestedEventId` 的前置条件、边界与失败语义都靠实现自行猜测，直接对应几条可复现的错误路径：1) 未要求 `events` 是单会话、按序、去重的前缀，调用方传入跨会话或乱序列表时可能拼出错误的 request；2) 未要求 `requestedEventId` 必须落在前缀内，越界 id 只能依赖实现抛错；3) 未禁止重放状态为 UNKNOWN 的步骤结果，违反“UNKNOWN 不得重放”的规则；4) 未声明 null 入参、返回是否可能为 null 以及应抛出的错误码（如 REQUEST_NOT_FOUND/REQUEST_CONTEXT_MISSING）。建议补上 `@throws`、前缀合法性要求（单 session、单调有序、eventId 唯一）、目标事件必须存在于 `events` 中且其步骤状态非 UNKNOWN，并明确返回非空。
- 判断：reconstruct需声明单会话有序前缀/找不到关联错误码；重建UNKNOWN请求用于核实合法，禁止重建本身会破坏诊断，禁止的是发送重放。
- 证据：harness-api/src/main/java/io/jharness/api/RequestJournal.java:15

## OCR-0176 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ProcessRunner.java:6；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：默认方法把「有界契约」在运行期静默退化为抛异常，而接口仍是单抽象方法（可作 lambda 目标，例如 ReadOnlyChildEnvironment#processes 中 `(argv, cwd, cancellation) -> { throw denied(); }`）。触发条件：任何只实现 argv 版本（含第三方/测试）的提供方编译、注册都能通过，但标准工具路径 ProcessToolsPlugin.CommandTool 调用的是 `execute(CommandRequest, ...)`，此时只会收到 HarnessException("UNSUPPORTED")；CommandTool 只捕获 IllegalArgumentException，异常会以未处理运行时异常逃出工具，而不是返回 ToolResult 失败/UNSUPPORTED 结果，也不会在注册期暴露能力缺失。修复建议：让有界方法成为唯一的抽象方法（argv 版本改为在实现侧适配），或在接口处以 Javadoc/能力查询显式声明该默认实现会失败，并要求调用方在注册时校验；若确为过渡期兼容，请标注 @Deprecated 并给出迁移路径。
- 判断：ProcessRunner旧lambda兼容默认有界入口拒绝，补能力/迁移说明；保留fail closed默认，不把未执行记为不安全放行。
- 证据：harness-api/src/main/java/io/jharness/api/ProcessRunner.java:6

## OCR-0177 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ProcessRunner.java:12；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：这是接口唯一的有界入口，但契约完全未约束入参边界：未要求 argv 非空、元素非 null/不含 NUL、参数个数与总长度上限（CommandRequest 中已有 256 项 / 32768 字符 / 超时 1 小时的界限），也未约定 ExecPath 必须是工作区内可解析的路径（绝对路径或逃逸路径必须拒绝）、CancellationToken 需在创建进程前检查。触发条件：实现方若不经过 CommandRequest 校验（直接 ProcessBuilder(argv) 并 chdir 到 workingDirectory），任意大小/空 argv 会直达 OS（E2BIG、难以诊断的失败），cwd 可越出工作区，取消请求可能在进程已启动后才生效而产生孤儿进程。建议在方法上补充强制的边界与拒绝语义（或声明必须委托给 CommandRequest 的校验），使各提供方行为一致。
- 判断：旧argv入口也应说明工作区/合作取消/有界责任，采纳契约；不将CommandRequest所有边界直接复制为破坏性接口变更。
- 证据：harness-api/src/main/java/io/jharness/api/ProcessRunner.java:12

## OCR-0178 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelResilienceOptions.java:41；OCR原严重度：medium
- 计划：R3-04 / P3
- 原意见：`Set.of(...).containsAll(m.keySet())` 会对 keySet 中的每个元素调用不可变集合的 `contains`，而 `Set.of(...).contains(null)` 会抛 NullPointerException。因此当外部配置（CLI/JSON 反序列化得到的 Map）含 null key（例如 `{"enabled":true, null:1}`）时，会抛出未受控的 NPE，而不是约定的 `HarnessException("INVALID_MODEL_RESILIENCE")`，破坏调用方按错误码分类处理的逻辑。建议先判 `raw instanceof Map`，再逐 key 做 `instanceof String` 校验。
- 判断：程序化Map可含null key导致containsAll NPE，可统一INVALID_MODEL_RESILIENCE；合法JSON对象不可能含null键，原文JSON例子无效。
- 证据：harness-api/src/main/java/io/jharness/api/ModelResilienceOptions.java:41

## OCR-0179 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelResilienceOptions.java:74；OCR原严重度：low
- 计划：R3-04 / P2
- 原意见：整数性校验使用 `n.doubleValue() != n.intValue()`，对 BigDecimal 这类十进制数并不可靠：`new BigDecimal("2.0000000000000000001")` 的 doubleValue 恰为 2.0，与 intValue()==2 相等，会被当作合法整数静默截断；同时 `intValue()` 对超出 int 范围的数值会回绕，判断依赖 double 比较才能侥幸拒绝。建议用 `double v = n.doubleValue();` 后判断 `v == Math.rint(v) && v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE`，并对 BigDecimal 用 `stripTrailingZeros().scale() <= 0` 精确校验。
- 判断：number用doubleValue比较intValue会接受高精度BigDecimal非整数，改精确整数/range检查；原建议仅double+rint仍不完整。
- 证据：harness-api/src/main/java/io/jharness/api/ModelResilienceOptions.java:74

## OCR-0180 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelResilienceOptions.java:13；OCR原严重度：medium
- 计划：无
- 原意见：`enabled == false` 时所有数值边界仍会被强制校验，而 `BoundedModelExecution.execute` 在 `!options.enabled()` 时会直接短路，完全不读取 maxAttempts/backoff/totalTimeout/minInterval/maxWaiters。结果是用户无法“关闭 + 保留历史遗留取值”的配置：例如 `{"enabled": false, "maxAttempts": 0}` 或 `{"enabled": false, "totalTimeoutMillis": 1}` 会抛 INVALID_MODEL_RESILIENCE，而不是按类注释所述“disabled preserves the adapter's legacy behavior”。建议在禁用时跳过数值边界校验（或将数值规范化为 disabled() 的取值）。
- 判断：禁用配置仍保持字段有效是合理严格校验，不意味改变adapter旧行为；允许无效历史值会在重新启用时潜伏错误。
- 证据：harness-api/src/main/java/io/jharness/api/ModelResilienceOptions.java:13

## OCR-0181 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/ModelResilienceOptions.java:11；OCR原严重度：low
- 计划：无
- 原意见：构造器逐字段校验，但缺少跨字段一致性校验：`minIntervalMillis >= totalTimeoutMillis`，或 `maxAttempts > 1` 而 `backoffMillis >= totalTimeoutMillis` 时，配置仍会被接受，但 `BoundedModelExecution` 中的退避/间隔等待必然先触发 `MODEL_RESILIENCE_TIMEOUT`，重试永远不会真正发生（例如 totalTimeoutMillis=50、backoffMillis=10000、maxAttempts=4）。建议在此处拒绝这类自相矛盾的组合，避免运行期才以超时形式暴露。
- 判断：总时限小于退避可合法表示先尝试一次、后续受预算限制，预算不保证maxAttempts必然全部执行；不应拒绝所有此类组合。
- 证据：harness-api/src/main/java/io/jharness/api/ModelResilienceOptions.java:11

## OCR-0182 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/SerialListener.java:5；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：该公共 SPI 方法声明了任意 `Exception`，但接口本身没有约定异常语义，导致实现方无法从代码判断失败影响面。实际的 `DefaultEventBus.publishSerial` 会把首个监听器异常包装成 `SERIAL_FAILED` 抛出并终止后续同优先级/低优先级监听器，超时则包装成 `SERIAL_TIMEOUT` 并依赖监听器响应中断；而 `notify` 的失败是隔离的。两者语义不同却没有在接口上说明，插件作者很容易误以为监听器失败不会影响其余监听器或发布方。建议在此处补充契约 Javadoc（串行/有序执行、首次异常即终止剩余分发并抛给发布方、超时协作中断、禁止重入 publish/publishSerial），若不需要如此宽泛的异常类型，可收敛为 `HarnessException` 或明确的受检异常以约束异常来源。
- 判断：SerialListener应说明首异常终止后续、超时interrupt及禁止重入，与DefaultEventBus实现一致。
- 证据：harness-api/src/main/java/io/jharness/api/SerialListener.java:5

## OCR-0183 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/PolicyEngine.java:4；OCR原严重度：high
- 计划：R1-05 / P2
- 原意见：该策略门禁 SPI 没有定义失败语义：返回类型没有 @NonNull 契约，Decision 也没有 UNKNOWN/ERROR 状态。触发条件：宿主/插件提供的 PolicyEngine 返回 null（例如把无法识别的外部策略结果映射为空），或实现抛出的异常被调用方的 catch-all 吞掉。两个真实调用点只在 `== DENY` 时拒绝：DryRunPipeline.java:37-46 和 CliApplication.java:1263-1267，null/未知决策会落入 preview/放行分支，而同一个 policy 在 WorkspaceContentInspection.java:50-56 却要求 `== ALLOW`。同一策略在不同入口产生不同安全结果，违背仓库“不确定即失败关闭”的约束。建议在接口注释中明确：evaluate 必须返回非空且实现无法判定时必须抛出；所有调用方必须把 null/异常/未知一律映射为 DENY。
- 判断：预览路径只DENY拒绝确会把null当可预览，应明确非空且预览null fail closed；dry-run不会执行工具，原文直接授权副作用推断过度。
- 证据：harness-api/src/main/java/io/jharness/api/PolicyEngine.java:4

## OCR-0184 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/PolicyEngine.java:3；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：接口未声明实现必须具备线程安全/无状态契约。触发条件：宿主注入的同一个 PolicyEngine 实例会被并发调用——SubagentPlugin.java:272-274 直接把 host.policy() 交给子任务，Web/SDK 的多任务共享宿主装配对象。若第三方实现在字段中缓存“当前调用/最近解析的策略结果”（这是最简单的实现方式），并发会话会互相覆盖，可能把本应 DENY 的调用判成 ALLOW。建议在接口层明确：实现必须无状态或线程安全，单次调用状态只能保存在局部变量中。
- 判断：PolicyEngine可能被父子宿主共享，补无状态或线程安全责任；未发现内置DefaultPolicyEngine可变共享状态竞争。
- 证据：harness-api/src/main/java/io/jharness/api/PolicyEngine.java:3

## OCR-0185 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/PolicyEngine.java:8；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：ASK 被定义成与 ALLOW/DENY 平级的返回值，但接口没有说明它的执行语义（必须取得显式人工批准才可执行，且缺少审批通道时按 DENY 处理）。触发条件：新调用方按 `decision != DENY` 或 `decision == ALLOW ? ... : ...` 的写法实现（DryRunPipeline.java:37-46 与 CliApplication.java:1265 已经是这种比较方式），或装配了自动批准的 ApprovalService 时，ASK 会直接变成可执行，从而绕过写操作/命令的人工审批。建议在枚举上注明 ASK 不代表授权，调用方在没有可用且真实的审批通道时必须按 DENY 处理。
- 判断：ASK不代表批准应写明；dry-run为ASK生成只读预览是合理，无需为了展示预览强制人工批准。
- 证据：harness-api/src/main/java/io/jharness/api/PolicyEngine.java:8

## OCR-0186 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ServiceRegistry.java:5；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：`ServiceRegistry` 缺少 `require`/`requireUnique` 的失败契约：缺失、重复、参数为 null 时抛什么异常/错误码未定义（默认实现依赖 `MISSING_SERVICE`/`AMBIGUOUS_SERVICE`），`find` 的 `Optional.empty()` 与实现值为 null 如何区分也未约定。尤其 `requireUnique` 返回非 Optional 的 `T`，零匹配必须抛缺失异常、多匹配必须抛歧义异常、禁止返回 null。建议在接口 Javadoc 中固化这些语义。
- 判断：ServiceRegistry缺MISSING_SERVICE/AMBIGUOUS_SERVICE及非空返回说明，应补与默认实现一致契约。
- 证据：harness-api/src/main/java/io/jharness/api/ServiceRegistry.java:5

## OCR-0187 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ServiceRegistry.java:6；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：`register` 的契约缺口会直接影响插件作用域正确性：1) `key` 的空值/空白与规范化（大小写、首尾空格）未定义，若实现 A 注册 `"k"`、实现 B 查询 `" k "`，会出现“注册成功但永远查不到”的不可达注册；2) 重复注册语义未定义（默认实现抛 `DUPLICATE_SERVICE`，但其他实现可能静默覆盖），若静默覆盖，后续注册的插件可替换先前插件的服务，造成跨插件服务劫持；3) `type`/`implementation`/`scope` 为 null 时的行为未定义（默认实现在 register 中校验，但 `find` 未校验）。建议在 Javadoc 中固化：key 非空且按原样逐字符匹配、type 与 implementation 非空、重复注册必须失败而不是覆盖。
- 判断：ServiceRegistry重复同scope/type/key必须拒绝、key精确匹配可补文档；不得自动归一化身份。
- 证据：harness-api/src/main/java/io/jharness/api/ServiceRegistry.java:6

## OCR-0188 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ServiceRegistry.java:8；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：查找的作用域隔离语义未在接口层约定。`PluginScope` 作为每次查找的入参，但接口没有规定解析是严格 scope-local 还是允许向 parent/global 回退；默认实现注释声称“exact scope, no implicit global or parent fallback”，但该约束未固化到 SPI。触发条件：替换实现采用回退解析时，某插件传入自身的 scope 却解析到其他作用域（甚至宿主全局）注册的服务，绕过 `PluginContainer` 的 scope 校验，形成跨插件服务泄漏。建议在接口 Javadoc 中明确 find/require/requireUnique 严格限定在给定 scope 内、不得隐式回退，并明确 key 的 null/空白处理。
- 判断：默认注册表严格scope精确查找无回退，接口应明确此既有关键约束。
- 证据：harness-api/src/main/java/io/jharness/api/ServiceRegistry.java:8

## OCR-0189 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionProjector.java:6；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：该公共 SPI 未声明任何重放安全/确定性契约。`SessionProjection.uncertainCalls()` 依赖 `toolHistory()` 中的 `tool.intent`/`tool.started`/`tool.completed` 推导未结算副作用，但本接口没有要求实现必须保留这些事件。触发条件：外部或替代实现只投影 `tool.completed`、按轮次截断历史，或把 `UNKNOWN` 映射为 completed/可继续状态时，未结算调用会从投影中消失，`DefaultAgentLoop` 在新轮发送模型前就无法拒绝继续，从而可能重放有副作用的调用。建议在接口 Javadoc 中明确：不得丢弃 `tool.intent`/`tool.started`，不得将 `UNKNOWN` 投影为终态或可重放状态，且相同事件序列必须返回确定等价的投影。
- 判断：SessionProjector应保留未决工具事实/确定性投影，补契约；不存在当前替换投影遗漏的实际证据。
- 证据：harness-api/src/main/java/io/jharness/api/SessionProjector.java:6

## OCR-0190 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionProjector.java:7；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：方法契约未规定入参非 null、事件顺序、快照语义与返回非 null。实际调用点 `DefaultAgentLoop` 直接传入 `runtime.reader().events()` 的可变列表；若实现保留该引用或按入参原顺序投影，后续追加/并发修改、乱序输入会使投影随调用时序变化，甚至触发 `ConcurrentModificationException` 或 NPE；实现返回 null 时调用方会直接解引用。此外接口仅接受完整事件列表，缺少 limit/fromSequence 一类有界输入契约，长会话会被全量物化并复制。建议在 Javadoc 中明确：`events` 不得为 null、按事件序列升序、实现必须复制为快照并返回非 null，同时约定调用方/实现的有界处理方式。
- 判断：SessionProjector可补有序单会话前缀/非空/独立快照责任；内置reader实际List.copyOf，原文称直接可变列表不实。
- 证据：harness-api/src/main/java/io/jharness/api/SessionProjector.java:7

## OCR-0191 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionState.java:5；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：校验方式不一致：sessionId 走 Values.required，对 null/空白抛 IllegalArgumentException("sessionId is required")；而 status 走 Objects.requireNonNull，对 null 抛无消息的 NullPointerException。SessionState 是公开 API 记录类型，调用方（如 DefaultAgentLoop 的 checkpoint 构造）无法用统一的异常类型/消息捕获构造失败，null 的 status 还会产生不带字段名的 NPE，定位困难。建议统一使用同一校验入口，例如 Objects.requireNonNull(status, "status")，或对 enum 也使用带字段名的 IllegalArgumentException。
- 判断：SessionState已拒绝null status，仅NPE缺字段名，采纳具名诊断，不称参数校验缺失。
- 证据：harness-api/src/main/java/io/jharness/api/SessionState.java:5

## OCR-0192 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionQuery.java:23；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：`apply(List<SessionEvent>)` 是公开 API（`SessionReader.query` 默认实现直接 `query.apply(events())`，`SessionCatalog` 也在跨会话循环中批量调用），但这里对入参和元素完全未做空值防护：`events == null` 会抛 NPE，`events` 中含 `null` 元素时 `e.timestamp()` 也会抛 NPE。`SessionEvent` 自身构造器虽保证 `timestamp`/`payload`/`type` 非空，但仓库反序列化路径（如 `repo.openReader(id).events()`）一旦返回含 null 的集合，就会把 NPE 冒泡成未分类的运行时异常，而不是稳定的 `HarnessException` 错误码。建议显式校验入参并把 NPE 转为带错误码的失败。
- 判断：SessionQuery.apply(null)与null元素会拒绝，补非空参数契约与字段诊断；正常SessionReader快照不含null。
- 证据：harness-api/src/main/java/io/jharness/api/SessionQuery.java:23

## OCR-0193 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionQuery.java:16；OCR原严重度：low
- 计划：无
- 原意见：校验只限制了 `offset < 0`，对 `offset` 没有上界（`limit` 有 1..1000 的界）。`apply()` 中 `skip(offset)` 会先完整遍历过滤结果再丢弃前 offset 条，调用方传入极大 offset（如 `Integer.MAX_VALUE`）时无法提前判定为空页，只能做全量无意义的扫描；对上层循环式分页（`SessionCatalog` 逐会话构造查询）是不可控的开销放大。建议为 `offset` 设定与数据规模匹配的上界（或业务约定的最大页深）并在构造器内校验，同时让错误消息体现 offset 约束。
- 判断：skip极大offset最多遍历现有有界历史而非按offset次数循环，给offset武断硬上限会阻断合法历史分页；未有性能证据。
- 证据：harness-api/src/main/java/io/jharness/api/SessionQuery.java:16

## OCR-0194 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionReader.java:9；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：默认实现直接解引用调用方传入的 `query`，未做任何非空校验：`reader.query(null)` 会抛出无任何诊断信息的 NullPointerException（且栈顶落在接口默认方法内，定位成本高）。同包 `SessionQuery` 构造器已统一用 `HarnessException("INVALID_QUERY", ...)` 做非法入参校验，此 API 边界应保持一致的失败语义，建议显式校验（需 `import java.util.Objects;`）。
- 判断：query(null)自然NPE已拒绝，可具名提示；无需将所有编程空参提升INVALID_QUERY业务异常。
- 证据：harness-api/src/main/java/io/jharness/api/SessionReader.java:9

## OCR-0195 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionReader.java:6；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：`events()` 返回裸 `List` 且未声明快照/不可变契约，而 `query()` 会把该列表原样交给 `SessionQuery.apply` 遍历。现有 `InMemorySessionRepository`/`JsonlSessionRepository` 均返回 `List.copyOf(...)` 快照，但接口本身没有约束：任何实现（如插件自定义的 `SessionReader` 或测试桩）返回内部实时列表时，调用方（如 `ModelDisplayBuffer` 直接遍历 `reader.events()`）在写入线程 append 期间可能抛 `ConcurrentModificationException` 或读到不一致的事件历史。建议在 Javadoc 中明确要求返回不可变快照，避免后续实现按可变视图实现。
- 判断：两内置events返回List.copyOf，接口补有序不可变快照与非空责任，以保持替换存储一致。
- 证据：harness-api/src/main/java/io/jharness/api/SessionReader.java:6

## OCR-0196 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionWriter.java:10；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`append` 的契约缺少三个直接决定重放安全的语义：(1) 返回的 id 是否等价于“该实现 `durable()` 所声明的保证已完成”，(2) 抛出什么异常表示写入失败、什么异常表示结果未知（例如 force 阶段 IO 失败后记录是否已部分落盘），(3) 失败后重试是否幂等。触发条件：调用方拿到返回值即 ack/广播事件（`EventSupport.append` 中即为 `writer.append(event); bus.publish(event);`），若某个 writer 的 append 只写缓冲区未落盘，进程崩溃后已广播事实丢失；反之失败后重试会导致同一 eventId 重复落盘。建议在 Javadoc 中明确：返回前必须完成本实现声明的存储保证，否则抛 HarnessException；异常后事件视为未确认，禁止调用方自动重放，重复 eventId 必须由实现拒绝。
- 判断：append类注释已有confirm guarantee，但返回id、poison/UNKNOWN和不自动重试需明确，写入异常不得被误认为确定未提交。
- 证据：harness-api/src/main/java/io/jharness/api/SessionWriter.java:10

## OCR-0197 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionWriter.java:6；OCR原严重度：medium
- 计划：无
- 原意见：`durable()` 以 `default false` 提供，使“确实不持久”与“物理存储写入方忘记覆写”不可区分。触发条件：新增的生产级 writer（文件/数据库实现）漏写 `durable()` 覆写，恢复与 checkpoint 逻辑会按内存语义处理，直到运行到 DURABILITY_REQUIRED 检查点才暴露，期间停机恢复会静默丢事件。建议将其改为抽象方法（强制实现方显式表态），或在 Javadoc 中明确规定“未覆写者仅允许在测试中使用”，并在工厂/装配处做启动期校验。
- 判断：durable默认false是刻意fail closed，副作用检查点会DURABILITY_REQUIRED拒绝，非静默放行；改抽象会破坏兼容包装器，现注释已限定memory TEST ONLY。
- 证据：harness-api/src/main/java/io/jharness/api/SessionWriter.java:6

## OCR-0198 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionWriter.java:12；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`flush()` 的保证未定义：是否使此前所有 append 达到 `durable()` 声明的物理保证、失败时抛什么异常、调用后 writer 是否仍可用，均无约定。触发条件：调用方把 `flush()` 当作 checkpoint（`EventSupport.checkpoint` 仅在 DURABLE_FLUSH 下调用 flush），若实现把 flush 实现为无操作或吞掉 IO 异常，checkpoint 会假成功，崩溃恢复时事件缺失。建议明确 flush 必须完成物理同步，失败抛 HarnessException 并置 writer 为不可继续写入状态。
- 判断：flush应说明按durable声明保证前缀、失败writer不可继续，采纳文档；内存TEST writer的flush不应被要求物理fsync。
- 证据：harness-api/src/main/java/io/jharness/api/SessionWriter.java:12

## OCR-0199 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionWriter.java:14；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：`close()` 被收窄为不声明受检异常，接口也未规定它是否隐含 flush、失败如何上报。触发条件：宿主按 try-with-resources 释放 writer 并依赖 close 落盘尾部事件，实现若不 flush 或吞掉 IOException（`JsonlSessionRepository.Writer.close()` 即只 `closeQuietly` 通道且不 flush），数据丢失将无任何信号传出。建议在契约中要求 close 等价于 flush 后释放资源，失败必须以 HarnessException 抛出而不是静默忽略。
- 判断：close错误/资源释放契约可补；JSONL append已每次force，close没额外flush不表示丢未刷尾，且无需增加受检throws。
- 证据：harness-api/src/main/java/io/jharness/api/SessionWriter.java:14

## OCR-0200 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionWriter.java:4；OCR原严重度：low
- 计划：R3-01 / P2
- 原意见：接口未声明并发与顺序契约：多个生产者共享同一 writer 时，是否保证 append 的全局顺序/事件序列号，是否允许并发调用 append，均无约定。触发条件：恢复重放按文件顺序或 eventId 重建状态时，若实现自行交错写入，同一步骤内的事件顺序可能与实际发生顺序不一致。建议明确 writer 是否线程安全、单写者要求，以及同一 writer 内 append 的持久化顺序即调用顺序。
- 判断：M8单writer owner顺序是关键契约，SessionWriter接口补串行调用/顺序及不允许worker随意写事实。
- 证据：harness-api/src/main/java/io/jharness/api/SessionWriter.java:4

## OCR-0201 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionRepository.java:6；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：接口未对 `sessionId` 约定格式与边界，各实现行为不一致：`InMemorySessionRepository.openWriter` 只做 `Values.required`（任意非空字符串都接受），而 `JsonlSessionRepository.file()` 强制 `[A-Za-z0-9][A-Za-z0-9_-]{0,127}` 并拒绝 `CON/NUL/COM1...` 等保留名。触发条件：调用方传入含 `.`、空格、超长或 Windows 保留名的 id，内存实现写入成功、JSONL 实现抛 `INVALID_SESSION_ID`；更关键的是，路径型实现的安全性完全依赖各自实现自行校验，接口层未把该约束作为契约，后续新实现（如其它存储后端）很容易漏掉校验而引入路径越界。建议在 API 层集中提供 `validateSessionId`（或在 javadoc 中明确 id 约束）并要求所有实现先调用。
- 判断：磁盘sessionId有portable正则/保留名而内存测试宽松，应公开生产存储要求并做跨实现合法子集契约；不把测试专用宽松当路径漏洞。
- 证据：harness-api/src/main/java/io/jharness/api/SessionRepository.java:6

## OCR-0202 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionRepository.java:8；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`openWriter`/`openReader` 的资源生命周期契约缺失：`SessionWriter` 虽继承 `AutoCloseable`，但接口未声明“所有权/租约仅在 `close()` 时释放，调用方必须使用 try-with-resources”；`SessionReader` 则完全没有可关闭语义。触发条件：调用方在异常路径忘记关闭 writer 时，`InMemorySessionRepository` 的 `store.writer` 永不清空，之后该 session 的所有 `openWriter` 都会持续抛 `WRITE_CONFLICT`（`JsonlSessionRepository` 的 `OWNERS` 同理）；若将来 reader 实现惰性持有文件句柄或锁，调用方也没有编译期信号去释放。建议在 javadoc 中明确关闭即释放租约、异常也需关闭，并让 `SessionReader` 按需声明 `AutoCloseable`（或明确约定它是只读快照、无需关闭）。
- 判断：writer拥有独占锁必须close可补生命周期文档，reader为完整快照无句柄无需AutoCloseable；不能要求不存在的reader资源关闭。
- 证据：harness-api/src/main/java/io/jharness/api/SessionRepository.java:8

## OCR-0203 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionRepository.java:12；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：`fork` 的契约未定义：返回值是新 `sessionId` 还是事件 id、`fromEventId` 不存在/已被压缩时的行为（两个实现都抛 `EVENT_NOT_FOUND`，但接口未声明，调用方无法依赖）、以及返回的 session 是否立即可读写。更严重的是没有要求 fork 点必须是可恢复边界：触发条件是使用某个“已发起但未完成”的工具调用事件 id 进行 fork，`JsonlSessionRepository.forkTo` 会把该事件及其之前的原始记录整体复制到子 session（第 255-267 行），只追加 `session.forked`，子 session 因此携带未结算的副作用；后续恢复/重放会再次执行该副作用，违反“UNKNOWN 状态不得被重放”的要求。建议在接口契约中要求实现拒绝 `SessionRecovery.inspect(...).ready() == false` 的 fork 点（或明确由调用方先校验），并补全返回值与错误语义。
- 判断：fork返回新session及EVENT_NOT_FOUND、子前缀UNKNOWN保持需说明；允许fork到未决点但禁止执行直到核实是设计，不能一律限制只在已结算边界分叉。
- 证据：harness-api/src/main/java/io/jharness/api/SessionRepository.java:12

## OCR-0204 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/StreamHandle.java:7；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：默认实现让 `finalResponse()` 变成事实上的“可选”方法：任何只实现 `consume(...)` 的 provider 都能通过编译，但流式聚合路径 `StreamingLlmClient.complete`（harness-llm/src/main/java/io/jharness/llm/StreamingLlmClient.java:65-71）在 `consume` 返回后是无条件调用 `finalResponse()` 的，因此这类实现会在已经向 display 输出过增量快照之后，整轮被 `UNSUPPORTED` 打断，且无法回滚已展示的内容。触发条件：任一未覆写 `finalResponse()` 的 `StreamHandle` 实现（第三方适配器、测试替身）走 `complete()` 聚合。另外 `UNSUPPORTED` 这个错误码同时被 `ProcessRunner`、`WorkspaceFiles` 用作通用能力缺失标记，调用方无法区分“该 provider 不支持最终响应”与“聚合阶段的其他失败”。建议把 `finalResponse()` 声明为抽象方法以在编译期强制实现，或增加 `supportsFinalResponse()` 能力位并在调用前判断；若必须保留默认实现，请使用专属错误码并写明调用顺序约束。
- 判断：finalResponse默认UNSUPPORTED是安全兼容边界，补能力要求/装配检查，不将空默认改成伪成功。
- 证据：harness-api/src/main/java/io/jharness/api/StreamHandle.java:7

## OCR-0205 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/StreamHandle.java:14；OCR原严重度：medium
- 计划：无
- 原意见：重声明 `close()` 去掉了 `AutoCloseable.close()` 的 `throws Exception`，实现方无法把释放失败向上传播，只能吞掉或包成未检查异常。触发条件：provider 在 `close()` 中关闭 HTTP 连接/SSE 订阅（例如 OpenAiCompatibleLlmClient 中的 `SseBody` 与 `pending` future），一旦关闭失败，调用方既拿不到错误也无法重试；同时接口没有规定 `consume` 回调抛异常时仍必须 `close()`、以及 `close()` 必须幂等，调用方若在异常路径上跳过关闭就会泄漏连接与线程。建议在 javadoc（或 `@implSpec`）中明确：`close()` 必须幂等、必须在 `consume`/`finalResponse` 失败路径上仍被调用，并说明释放失败的表现形式；若需要传递释放错误，可在接口层声明抛出 `HarnessException` 之类的未检查异常契约。
- 判断：close无受检异常不等于不能上报，Java实现可包装HarnessException；增加throws Exception会破坏所有调用方，不属合理必修。
- 证据：harness-api/src/main/java/io/jharness/api/StreamHandle.java:14

## OCR-0206 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/StreamHandle.java:12；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：`consume` 的契约缺少三个会影响正确性的约束，实现方与调用方只能靠猜测：1) `consumer` 为 null 时应立即抛 NPE，而不是在流中途回调处才失败（此时流已开始、资源已占用）；2) 回调收到的累计快照只是展示用，绝不能当作可执行/最终回复——必须在 javadoc 中明确“未完成的快照不得进入 assistant/tool 管线，也不得在异常后重放为成功结果”；3) `consume` 只能调用一次且抛出异常即表示流失败、此后 `finalResponse()` 不可再取。缺少 2)、3) 时，实现方可能在流中断后仍保留部分 `result`，调用方可能把截断的文本当成完整回复（特别是恢复/重试场景下重放 UNKNOWN 状态）。建议补充 `@throws`/`@implSpec` 说明这些顺序与失败语义。
- 判断：接口已有工具只能finalResponse执行说明，采纳consume单次/回调线程与非空契约补充；不重复宣称原有工具限制缺失。
- 证据：harness-api/src/main/java/io/jharness/api/StreamHandle.java:12

## OCR-0207 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionEvent.java:46；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：事件类型→等级的映射表遗漏了多个已在生产代码中使用的一等会话事实类型，例如 `user.message`（CliApplication:991、HarnessSdk:401）、`system.message`（CliApplication:959、HarnessSdk:189）、`context.injected`（CliApplication:980）和 `model.observed`（ObservedLlmClient:95）。这些类型都会落入本 `default` 分支得到 `expected == null`，因此 `level` 与关联字段完全不受约束：调用方可以把 `user.message` 构造成 TURN/STEP/TOOL 级并携带 turnId/stepId/callId，构造仍会成功。下游 EvidenceReport:41-44 用 `level != EventLevel.SESSION` 过滤并按 turnId 匹配，一旦此类事实被误标为 TURN/STEP，就会被从证据报告中丢弃或错配到某个 turn。建议把已知的第一方 SESSION 级类型显式列入映射，并对未登记类型默认拒绝（或至少强制 SESSION）而不是无条件放行。
- 判断：第一方SESSION类型未全部显式映射，采纳补齐第一方类型测试；拒绝默认禁止所有未知类型，插件自定义事件是设计能力。
- 证据：harness-api/src/main/java/io/jharness/api/SessionEvent.java:46

## OCR-0208 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionEvent.java:27；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`checkpoint.failed` 与 `session.created` 被归为同一分支且 `expected == null`，但只有 `session.created` 有显式等级校验（第 51 行）。结果是 `checkpoint.failed` 可以被构造为 TURN/STEP/TOOL 级：STEP 级要求 stepId、TOOL 级还要求 callId，均能通过校验。文档（JHarness-PRD-v1.1.md:704）与现有唯一生产者（DefaultAgentLoop:411、DefaultToolPipeline:142）都把它作为会话级落盘失败诊断事实，允许 TOOL 级并携带 callId 会绕过本类“按类型校验等级”的约束，日志中可能出现语义不一致的失败事实。建议对该类型显式校验允许的等级（至少 SESSION；若按文档支持可选 step，则限定为 SESSION/STEP），并对 TURN/TOOL 级直接拒绝。
- 判断：checkpoint.failed expected=null容许多个等级，需按既有生产者及PRD明确允许集合，不能未经核对一律改SESSION。
- 证据：harness-api/src/main/java/io/jharness/api/SessionEvent.java:27

## OCR-0209 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/SecretRedactor.java:52；OCR原严重度：high
- 计划：R1-04 / P1
- 原意见：敏感键名覆盖不足（第 7-15 行集合 + 第 52 行精确匹配）：这里只做小写后完全相等比较，导致常见别名漏脱敏，例如 `x-api-key`、`api-key`、`client_secret`、`cookie`/`set-cookie`、`token`、`credential`、`private_key`，以及驼峰写法 `accessToken`/`refreshToken`/`clientSecret`（小写后为 `accesstoken`/`refreshtoken`/`clientsecret`，均不在集合中）。触发条件：任何工具/MCP/模型回显返回的 JSON 对象使用上述键名时，其值会经 `clean(value)` 原样进入日志或发送数据，而只有已注册进 `secrets` 的字面量才会被替换。另外注意键名匹配只对 Map 生效，值里内嵌的 JSON 字符串（如 `{"authorization":"Bearer ..."}` 作为字符串值）绕过该规则。建议归一化键名（统一小写并去掉 `-`/`_`）后按后缀/精确集合匹配，并补齐别名。
- 判断：SecretRedactor仅固定7种小写精确键，api-key/accessToken/client_secret等未覆盖，统一归一化别名并补嵌套测试；不能盲目匹配所有token后缀损坏普通数据。
- 证据：harness-api/src/main/java/io/jharness/api/SecretRedactor.java:52

## OCR-0210 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SecretRedactor.java:48；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：`sanitize` 缺少 null 防御（第 46-55 行）：`data` 为 null 时直接 NPE；顶层调用传入含 null key 的 Map 时，第 52 行 `key.toLowerCase(...)` 会抛 NPE（嵌套 Map 会先被 `Values.object` 以 INVALID_JSON 拒绝，但公开入口 `sanitize` 本身没有校验）。`Values.snapshot` 本来能用 `required(key,"key")` 给出明确错误，但 NPE 先发生，且脱敏/日志路径崩溃会丢失整条记录。建议显式校验 data 与 key，并抛出与 Values 一致的异常。
- 判断：sanitize(null)/null键会NPE且已fail closed，只采纳具名诊断；不得在失败时吞掉异常写原文。
- 证据：harness-api/src/main/java/io/jharness/api/SecretRedactor.java:48

## OCR-0211 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SecretRedactor.java:50；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：键名本身没有经过脱敏（第 50-54 行）：只对 value 调用 `clean`/`text`，若凭据出现在 JSON 对象的键上（例如工具回显把 token 当作键 `{"<secret>": ...}`），或键被拼进日志说明，凭据会原样保留，违反“Scrub before logging AND sending”。建议对键也执行 `text(key)` 后再放入结果。
- 判断：sanitize只处理值且原键原样保留，已知secret作为key会泄漏；采纳键脱敏但必须检测碰撞，不可悄悄覆盖两个键。
- 证据：harness-api/src/main/java/io/jharness/api/SecretRedactor.java:50

## OCR-0212 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SecretRedactor.java:39；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：流式输出尾窗先脱敏后截断，会切裂占位符并永久丢失尾部（第 37-42 行）：先 `text(cumulative)` 把秘密替换成 10 字符的 `[REDACTED]`，再按原始秘密最大长度无条件回退 `hold` 个字符，截断点会落在占位符内部。例如注册秘密 `secret`（长度 6）时 `"hello secret"` 得到 `"hello [REDACTED]"`，返回 `"hello [RE"`；秘密越短越容易切裂。调用方（如 ModelDisplayBuffer.accept）每次用返回前缀覆盖显示、且没有最终 flush，所以流结束后界面会停在 `"hello [RE"`，尾部非秘密文本也一并丢失；当消息长度小于最长秘密长度时还会一直返回空串。建议先在原始文本上截出安全前缀，再做脱敏，保证占位符完整且只丢弃真正可能构成秘密前缀的尾部。
- 判断：streamingText按已脱敏长度再减secret最大长度可切裂占位符，采纳保守窗口改进；不得简单先切原文再脱敏，否则会暴露跨界secret前缀。
- 证据：harness-api/src/main/java/io/jharness/api/SecretRedactor.java:39

## OCR-0213 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SecretRedactor.java:38；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：流式路径存在二次复杂度（第 37-39 行）：每次 `streamingText` 都对完整累计文本重跑全部秘密的 `String.replace`，第 i 次回调处理 i 个字符，整条流累计 O(n²·k)（k 为秘密数）。`ModelDisplayBuffer` 只在展示侧截断到 65536 字符，传给本方法的仍是未截断的完整累计值，因此长输出/大工具结果会持续占用 CPU 并产生大量临时字符串。建议只对新增增量或“上次安全偏移之后的尾部窗口”做脱敏并缓存结果，而不是每帧全量重扫。
- 判断：每帧累计字符串全量replace确有反复扫描成本，采纳在显示上限/节流后有界处理与性能验证；不能引入跨会话可变共享脱敏缓存。
- 证据：harness-api/src/main/java/io/jharness/api/SecretRedactor.java:38

## OCR-0214 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/SecretRedactor.java:61；OCR原严重度：medium
- 计划：R3-01 / P1
- 原意见：递归脱敏缺少深度/环保护（第 58-62 行）：Map 分支通过 `Values.object` → `Values.freeze` 间接获得 64 层上限，但 List 分支没有任何限制。触发条件：外部工具结果或模型返回纯数组嵌套超过栈深度，或构造自引用 List（`list.add(list)`），`clean` 会无限递归并抛 `StackOverflowError`，直接击穿日志写入/展示路径（SOE 不是可恢复的业务异常）。建议给 `clean` 增加 depth 参数（如 64）或使用 IdentityHashMap 检测环，超限时抛 `HarnessException` 而不是让栈溢出。
- 判断：clean对纯List递归未计depth，自引用List可无限递归，Values.snapshot发生在clean之后不能保护；需先验证/统一递归深度节点限额。
- 证据：harness-api/src/main/java/io/jharness/api/SecretRedactor.java:61

## OCR-0215 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolExecution.java:5；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：这是对插件公开的扩展点（StandardExtensionPoints.TOOL_EXECUTION），但接口没有任何契约文档：既未声明 `invocation`/`cancellation` 必须非空、返回值必须非空，也未要求实现必须观察取消令牌。下游 `DefaultToolPipeline.run()` 用 `Objects.requireNonNull(wrapped.execute(...))` 兜底，实现返回 null 时只会被 `code()` 归类为 TOOL_FAILED，属于未定义错误路径而非明确失败；同时包装器若忽略取消令牌并返回 SUCCESS，管线在调用前后没有二次校验取消状态。建议在 Javadoc 中明确：参数与返回值为非空；实现应在开始及关键步骤调用 `cancellation.throwIfCancelled()`，取消时抛 `HarnessException("CANCELLED")`，不得在已取消后返回 SUCCESS。
- 判断：ToolExecution非空/取消责任可补；工具已经真实完成后返回SUCCESS不能因后到取消伪造失败，要求取消后绝不SUCCESS过严。
- 证据：harness-api/src/main/java/io/jharness/api/ToolExecution.java:5

## OCR-0216 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolExecution.java:6；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：`throws Exception` 未加约束，实现可抛出任意受检异常；而 `DefaultToolPipeline.code()` 只识别 `InterruptedException`（→CANCELLED）与 `HarnessException.code()`，其余异常统一降级为 `TOOL_FAILED`。因此实现若抛出 `IOException`/`TimeoutException` 等，TIMEOUT、PERMISSION_DENIED 等错误码与可重试语义会在此边界丢失，调用方也无法区分可重试与致命失败。建议在契约中约定实现应抛出带错误码的 `HarnessException`（或收窄异常类型），并在 Javadoc 说明未归类异常将被视为 TOOL_FAILED。
- 判断：未分类异常统一TOOL_FAILED符合保守分类，补实现使用HarnessException的说明，不自动把IOException认作可重试。
- 证据：harness-api/src/main/java/io/jharness/api/ToolExecution.java:6

## OCR-0217 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolPipeline.java:8；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：默认实现直接返回 empty，完全不会调用传入的 barrier，与上一行注释声明的「barrier must finish earlier reads before ASK/write」约定不一致。当前唯一的调用方 BoundedReadOnlyToolScheduler 在 `prepared.isEmpty()` 时自己补了一次 `run.drain()` 才 `pipeline.execute(...)`，所以现在没暴露问题；但接口契约本身没有写明「返回 empty 时调用方必须先执行 barrier 再 execute」。触发条件：任何新调用方（或未来的调度器实现）按接口注释理解为 prepare 已负责排序、在 empty 时直接 execute，此时一个只实现 execute 的旧插件若走到 ASK/写操作，就会与仍在运行的并行只读调用交叉，破坏跨步骤排序。建议在默认实现中先执行 barrier 再返回 empty（drain 本身可重入），或至少在 Javadoc 中明确「empty 表示调用方必须先完成 barrier」。
- 判断：prepare默认empty当前调度器会drain，现无排序漏；采纳明确empty调用方负责barrier，默认调用barrier可能改变旧兼容顺序。
- 证据：harness-api/src/main/java/io/jharness/api/ToolPipeline.java:8

## OCR-0218 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolPipeline.java:10；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：empty 同时承载了「旧实现未提供 prepare」这一唯一语义，但契约没有约束「返回 empty 前不得产生任何可观察副作用」。BoundedReadOnlyToolScheduler 在 empty 分支会再调用一次 `pipeline.execute(call, context)`，而自研实现在 prepare 中可能已经发布了 tool.requested 事件、写了 checkpoint 或弹出过 ASK 审批。触发条件：某个 override 在返回 empty 之前完成了一部分准备工作（发事件/审批/checkpoint），调度器回退到 execute 后会再次准备，导致事件与 checkpoint 重复、审批被重复请求，甚至对同一 callId 产生两次审批记录。建议在接口注释中明确 empty 必须表示「无副作用、未消费 barrier、可安全回退串行 execute」。
- 判断：prepare返回empty后调度器会重新execute，应明确empty必须无可观察准备副作用，避免兼容回退重复。
- 证据：harness-api/src/main/java/io/jharness/api/ToolPipeline.java:10

## OCR-0219 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolPipeline.java:7；OCR原严重度：low
- 计划：R3-01 / P2
- 原意见：barrier 的契约缺少异常、调用次数与失败语义：Runnable 抛出异常时（如 BoundedReadOnlyToolScheduler 的 barrier 会抛 SCHEDULER_STOPPED）override 应如何收尾、barrier 是否保证至多/恰好调用一次、调用后是否还能继续返回 prepared 调用，都没有定义；实现方若延后调用或永不调用 barrier，也无法从接口层被发现。建议在 Javadoc 中写明：barrier 至多调用一次、其异常必须原样向上抛出且不得再返回可并行的 prepared 调用。
- 判断：barrier失败必须停止准备/不得执行应写明；至多一次并非必要通用约束，允许安全幂等drain实现。
- 证据：harness-api/src/main/java/io/jharness/api/ToolPipeline.java:7

## OCR-0220 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/TerminalSession.java:0；OCR原严重度：high
- 计划：R3-01 / P3
- 原意见：stopAll() 默认实现直接委托 close()，存在两个可复现问题： 1) 递归风险：实现方最自然的写法是让 close() 调 stopAll()（如 LocalTerminalSession.java:210-215 正是 close→stopAll），若某实现只重写 close() 而沿用此默认 stopAll，则 stopAll→close→stopAll 无限递归直至 StackOverflowError，且发生在关闭路径上会导致资源无法释放。 2) 语义不一致：主实现 LocalTerminalSession.stopAll() 只停止任务、会话仍可用，而默认实现会永久关闭 provider（close 后 start 通常抛 TERMINAL_CLOSED），调用方无法安全地在中途清理终端任务。 建议把 stopAll 作为原语、让 close() 默认委托 stopAll（或至少显式声明 stopAll 具有终止语义并禁止 close→stopAll 反向委托）。
- 判断：stopAll默认close与内置只停任务语义不同，明确能力/终止后可用性；内置同时重写两个方法无现行无限递归。
- 证据：harness-api/src/main/java/io/jharness/api/TerminalSession.java:声明/现有代码

## OCR-0221 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/TerminalSession.java:40；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：states() 的默认实现返回空列表，而其他能力默认抛 UNSUPPORTED_TERMINAL，二者语义不一致：未实现状态上报的 provider 会被调用方误判为“没有任何存活会话”。CliApplication.java:1062-1067 用 states() 决定是否写入 terminal.snapshot、1151 用 states() 收集需要回收的任务 id，HarnessSdk.java:545 也直接上报 states()，因此这类 provider 的孤儿进程既不会被记录也不会被清理，且调用方完全无法区分“不支持”和“确实为空”。建议要么与其他默认一致抛 UNSUPPORTED_TERMINAL（调用方需按错误码降级），要么在接口层提供 capability 标识并在 javadoc 中明确空列表即“不支持状态查询”。
- 判断：states默认空需标注缺失状态能力，不能把无终端环境当已清理；改成总抛错需同步所有无状态provider。
- 证据：harness-api/src/main/java/io/jharness/api/TerminalSession.java:40

## OCR-0222 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/TerminalSession.java:19；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：status()/log()/states()/start() 返回未脱敏、无类型的 Map<String,Object>，而接口没有约定任何脱敏与有界性要求。这些返回值会被直接落盘（CliApplication.java:1062-1067 将 states() 写入 terminal.snapshot 会话事件），若 provider 把环境变量、token 或命令原文放进 task view，凭据会永久写入会话日志；log(offset,limit) 的 offset/limit 语义与上限、以及 start 被取消后不得遗留已启动的 PTY/后台进程，也均未在接口契约中声明。建议补充 javadoc：输出必须经过与 SecretRedactor 等价的敏感信息脱敏、log 必须校验 offset>=0 与 limit 上限（如 65536）、取消必须回收已创建进程，并说明各字段的含义与不可变性。
- 判断：TerminalSession输出Map/offset/limit/取消清理需文档契约；现持久化走SanitizingSessionWriter，非已证实日志泄漏。
- 证据：harness-api/src/main/java/io/jharness/api/TerminalSession.java:19

## OCR-0223 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolRegistry.java:12；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：register 未定义同名重复注册（同一 scope 内、以及不同 scope 间同名）的语义：是抛错、覆盖还是允许共存。当前唯一实现 ScopedToolRegistry 抛 DUPLICATE_TOOL，但接口允许其他实现静默覆盖——覆盖后原注册方持有的 Registration.close() 会在回滚时移除新注册者的工具（或反之），使插件作用域回滚结果不确定。建议在契约中明确“同名同 scope 必须失败且不得覆盖”以及“close() 必须幂等、只移除本次注册的实例”。
- 判断：ToolRegistry接口未写同scope同名拒绝与Registration只撤销本实例/幂等，补既有实现规则。
- 证据：harness-api/src/main/java/io/jharness/api/ToolRegistry.java:12

## OCR-0224 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolRegistry.java:14；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：接口契约未约定 resolve 必须“精确匹配给定 scope、不得回退到父/更宽 scope”。require() 直接以 resolve 的结果放行，因此任何第三方实现（该文件是 SPI）只要在未命中时回退到父 scope，就会把别的插件作用域注册的工具交给当前插件，突破插件作用域边界；这与 ScopedToolRegistry 的精确匹配行为形成隐式约定。建议在契约中显式规定：查询必须按 (name, scope) 精确匹配，跨 scope 解析一律返回 Optional.empty()。
- 判断：ToolRegistry.resolve应明确(name,scope)精确匹配，禁止隐式父scope回退，保持替换实现一致。
- 证据：harness-api/src/main/java/io/jharness/api/ToolRegistry.java:14

## OCR-0225 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolRegistry.java:7；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：默认方法 require() 未校验 name/scope 非空，导致“参数错误”被下游实现以不同方式表现： - 经 FilteredToolRegistry 时 allowed 为 Set.copyOf(...)（不可变集合），require(null, scope) → resolve(null, scope) → allowed.contains(null) 抛 NullPointerException； - 经 ScopedToolRegistry 时 Key(null, null) 能查到空结果，返回 HarnessException("UNKNOWN_TOOL", "Tool is unavailable: null")，把编程错误伪装成“工具不可用”。 调用方（如 DryRunPipeline 把工具名直接取自模型输出）无法区分 UNKNOWN_TOOL 是“模型幻觉的工具名”还是自身 bug，可能导致错误的重试/降级策略。建议在入口用 Objects.requireNonNull 做快速失败。
- 判断：require空name/scope在不同实现错误不同，应具名校验；均未因此找到并执行未授权工具。
- 证据：harness-api/src/main/java/io/jharness/api/ToolRegistry.java:7

## OCR-0226 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolRegistry.java:16；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：visibleTools 直接返回裸 List，但契约未声明是否为只读快照、是否可能返回 null。若某个实现返回内部可变视图（如 new ArrayList<>(tools.values()) 或原始集合），调用方增删元素会直接污染注册表状态；若实现返回 null，调用方普遍使用 stream()（DefaultPromptComposer、HarnessSdk）会立刻 NPE。建议在契约中规定返回不可变快照且永不为 null（必要时在接口边界做 List.copyOf 防御）。
- 判断：visibleTools补非空快照契约；原文把new ArrayList副本称会污染内部是错误，仅返回内部活动集合才有该风险。
- 证据：harness-api/src/main/java/io/jharness/api/ToolRegistry.java:16

## OCR-0227 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolInvocation.java:5；OCR原严重度：medium
- 计划：R1-04 / P3
- 原意见：This record does not override `toString()`, so the compiler-generated implementation renders the full `arguments` map. Any place that stringifies an invocation (debug/`log.info("{}", call)` statements, wrapped exception messages, IDE debugger, or SDK/plugin code that logs its parameter) will emit raw tool inputs, including credentials such as API keys, tokens or passwords. Redaction currently only happens at explicit `DataSanitizer`/`SECRET_ARGUMENT` call sites and is list/pattern based, so it cannot be relied upon for every secret passed to a tool. Suggest overriding `toString()` to expose only `callId`, `tool`, `sideEffectLevel` and a redacted/summary view of `arguments`.
- 判断：ToolInvocation默认toString含arguments，可用元数据摘要降低意外日志风险；未找到本次真实原始日志sink。
- 证据：harness-api/src/main/java/io/jharness/api/ToolInvocation.java:5

## OCR-0228 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolScheduler.java:5；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`close()` has a no-op default and no contract, which lets a scheduler silently stay live after its scope is torn down. `ToolSchedulingPlugin.start()` does `context.own(scheduler)` and only `close()` is invoked on scope rollback; a third-party scheduler that relies on this default neither releases workers/queues/streams nor starts rejecting work, so a captured `ToolScheduler` reference can still execute tools against an already-rolled-back scope (stale-scope tool execution, and a resource leak). The current guard against this exists only because `BoundedReadOnlyToolScheduler` happens to override `close()` and check a `closed` flag. Since the default must stay (it keeps `ToolScheduler` usable as a lambda), at minimum document the mandatory post-close behaviour so implementers know it is required: after `close()` the scheduler must reject every `execute(...)` with an error (e.g. `SCHEDULER_CLOSED`) and release held resources.
- 判断：有资源scheduler应close释放并拒绝旧引用，补文档；无资源lambda默认close合法，不能强制其关闭后状态而不改变函数接口设计。
- 证据：harness-api/src/main/java/io/jharness/api/ToolScheduler.java:5

## OCR-0229 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolScheduler.java:3；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：The "preserve pipeline and call identity" guarantee is only prose and omits the UNKNOWN/no-replay rule that the rest of the pipeline relies on. A scheduler implementation that re-dispatches a call whose outcome is unknown (or that dedups/reorders calls) can duplicate side effects, and nothing in this SPI's contract tells implementers to stop. Please state explicitly: the same `ToolPipeline` and the original `ToolPipelineContext` (scope/turn/step) must be passed through unchanged, results must be returned in the original call order without reordering/deduplication/replay, and the batch must stop on the first result for which `ToolBatchExecution.stops(result)` is true (UNKNOWN/CANCELLED/TIMEOUT/`sideEffectUncertain`/`CHECKPOINT_FAILED`).
- 判断：ToolScheduler应明确保留call/context/结果前缀顺序及stops禁止重放，与M8调度契约一致。
- 证据：harness-api/src/main/java/io/jharness/api/ToolScheduler.java:3

## OCR-0230 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionProjection.java:17；OCR原严重度：high
- 计划：R1-01 / P1
- 原意见：第 16-21 行的折叠对“已结算”的判定过宽：只要 `tool.completed` 的 `status` 不是 `UNKNOWN`、`value` 里也没有 `sideEffectUncertain`，就无条件 `pending.remove(callId)`，连 `status` 缺失（null）或为 `FAILED`/`TIMEOUT`/`CANCELLED` 的情况也算作“确认无副作用”。  可复现路径：非只读调用先写入 `tool.intent`（进入 pending），随后 `DefaultToolPipeline.finish` 写出 `tool.completed`，`bodyEntered=true`、`sideEffectLevel=SYSTEM_IMPACT`，但结果是工具自己返回的失败而非抛异常。例如 `McpPlugin.execute`（harness-ecosystem/.../McpPlugin.java:446-457）在远端返回 `isError:true` 时返回 `ToolStatus.FAILED`，`value` 中只有 `source/result`，没有 `sideEffectUncertain`；该远端工具可能已经部分执行。此时本方法把 callId 从 pending 移除，`DefaultAgentLoop`（harness-agent/.../DefaultAgentLoop.java:42）不再阻止下一轮，模型可对可能已生效的远端调用重放。注意 `TaskDiagnosis`（harness-web/.../TaskDiagnosis.java:69-79）对同一事件用 `bodyEntered` 判为 `unknown`，与本方法结论矛盾。  建议：仅在有肯定证据时清除（SUCCESS / DENIED / 显式 `bodyEntered=false`），把“已进入主体但非成功”的情况保持为待核实。
- 判断：uncertainCalls对非UNKNOWN且无标志一律remove；entered副作用工具返回FAILED可被清除，需统一Pipeline、Projection、Diagnosis对执行后失败的不确定规则。
- 证据：harness-api/src/main/java/io/jharness/api/SessionProjection.java:17；harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java:finish；harness-web/src/main/java/io/jharness/web/TaskDiagnosis.java:bodyEntered

## OCR-0231 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionProjection.java:14；OCR原严重度：medium
- 计划：无
- 原意见：只读判定两处不一致：第 13-14 行对 `tool.intent`/`tool.started` 用 `sideEffectLevel == READ_ONLY` 过滤，但第 16-21 行的 `tool.completed` 分支完全不看 `sideEffectLevel`。  可复现：宿主把某远端工具映射为 `SideEffectLevel.READ_ONLY`（见 docs/examples/m7-extensions.md 与 McpPlugin 的 effects 配置），该工具调用传输异常时 `McpPlugin` 返回 `ToolStatus.UNKNOWN`，于是这个只读调用被加入 `uncertainCalls()`；`DefaultAgentLoop` 在每轮前用 `uncertainCalls().isEmpty()` 做准入，整个会话被阻塞到操作者执行 `verify`，尽管只读调用不可能产生副作用。建议在同一分支使用 `payload` 中的 `sideEffectLevel` 做一致的过滤（或把两处判定收敛为一个辅助方法）。
- 判断：M8契约明确只读调用UNKNOWN/超时也阻止后续和新batch，不能假设未合作只读Java工具没有副作用；删除UNKNOWN阻塞会削弱既定门禁。
- 证据：harness-api/src/main/java/io/jharness/api/SessionProjection.java:14

## OCR-0232 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/SessionProjection.java:12；OCR原严重度：low
- 计划：R5-02 / P3
- 原意见：`uncertainCalls()` 每次调用都从 `toolHistory()` 起点完整重扫并重建集合，没有缓存或增量维护。该方法在每轮开始前（DefaultAgentLoop:42）、CLI `verify`/recover（CliApplication:727/806）、Web 诊断（TaskDiagnosis:98）都会触发；会话事件随时间线性增长时，每轮固定多一次 O(n) 全量折叠，长会话整体退化为 O(n²)，且返回列表也未做上界控制（Web 侧另在 64 处截断）。建议在投影构造/更新时一次性维护待核实集合，或至少在方法层缓存结果。
- 判断：uncertainCalls每次全扫toolHistory属事实，采纳测量长会话后按不可变投影缓存；目前无性能阈值失败证据，不盲目引入可变增量权威状态。
- 证据：harness-api/src/main/java/io/jharness/api/SessionProjection.java:12

## OCR-0233 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/UserQuestionService.java:7；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：接口注释承诺「回答绝不授权工具/计划/配置」，但 ask 的 ToolInvocation 与 ToolContext 完全由调用方构造后传入，签名本身不提供任何绑定关系。实现必须自行校验：call.tool() 为 ask_user、context.scope() 等于本服务的作用域、callId 对应已持久化的 tool.started 且参数一致，否则调用方可伪造一次「工具调用」或借用其它会话作用域发起提问，返回值也可能被上层当作已授权的工具结果。建议在接口契约中明确这些强制校验点及失败码，避免不同实现遗漏（当前 UserQuestionsPlugin 已做校验，但接口层面无任何约束）。
- 判断：问答服务应固定scope/call绑定和只收用户输入契约；现有UserQuestionsPlugin已有保护，不能把公开参数类型等同已越权。
- 证据：harness-api/src/main/java/io/jharness/api/UserQuestionService.java:7

## OCR-0234 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/UserQuestionService.java:13；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：respond 仅接受一个无类型 Map，签名未携带 questionId/requestId/版本或状态令牌，API 边界无法拒绝重复提交与陈旧提交，也无法阻止对 INTERRUPTED/UNKNOWN 等已终止状态的「重放」回答。幂等性当前完全靠实现内部约定（requestId + answerHash 比对、state 必须为 PENDING），一旦替换或新增实现就极易把 UNKNOWN 当成 PENDING 接受、重复写入回答。建议在契约中要求 submission 必须包含 questionId 与符合 [A-Za-z0-9_-]{1,64} 的 requestId，并声明：同一 requestId+同一内容返回原结果，requestId 不同或状态非 PENDING 一律拒绝。
- 判断：respond的requestId/answerHash/状态幂等现仅实现约定，应公开最小schema；不同requestId是否总拒绝应以现有合同为准。
- 证据：harness-api/src/main/java/io/jharness/api/UserQuestionService.java:13

## OCR-0235 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/UserQuestionService.java:9；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：list() 返回会话内全部提问条目，其中包含用户的 selected/text 回答内容，但接口未约定条数上界与脱敏要求：数据量随会话内提问累积（实现仅在超过 64 条时抛错），自由文本回答可能携带凭据、PII 等敏感信息；任何拿到该服务的调用方（HarnessSdk.questions() 对外暴露）都能批量读取全部回答。建议在接口层明确有界返回（条数上限）与返回前必须脱敏（或只返回摘要/不含原文）的要求。
- 判断：list返回回答原文用于工作台是既定功能，采纳脱敏/64项限额文档；不默认删除授权用户可见的原文。
- 证据：harness-api/src/main/java/io/jharness/api/UserQuestionService.java:9

## OCR-0236 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/UserQuestionService.java:11；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：draft/respond 以无 schema 的 Map 交换数据，接口未声明字段白名单、必填项、长度边界（如问题 2048、回答 4096、选项 2～8）以及失败语义（是否可能返回 null、抛出哪些错误码）。调用方只能依赖具体实现猜测，容易传入缺字段的 Map 并在内部 get() 拆箱时触发 NPE，或产生不同实现间的协议漂移。建议改为类型化请求/响应对象，或至少在 Javadoc 中固化字段集、边界值与错误码枚举。
- 判断：draft/respond缺无类型Map字段/长度/错误说明，应固定现有问答协议，避免立即破坏性改成新record。
- 证据：harness-api/src/main/java/io/jharness/api/UserQuestionService.java:11

## OCR-0237 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolBatchExecution.java:15；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：行 15：`e.code().equals(...)` 以错误码为接收者调用 equals，而 `ToolResult.ErrorInfo`（ToolResult.java:21）没有对 `code` 做非空校验，插件/自定义 pipeline 完全可以构造 `new ErrorInfo(null, ...)`。此时 `stops()` 会抛 NPE 而不是返回停止决策（stopping 判定发生在批量执行主路径上，一旦抛出会直接中断整个批量，比“继续执行”更危险）。触发条件：任意 ToolResult 的 error code 为 null（例如自定义 ToolPipeline 或 ToolResults.sanitized 中 code 被脱敏为 null）。建议常量在前做 null 安全比较。`result.value()` 由 ToolResult 构造器强制非空，此处不是问题；但 `result` 本身可能为 null（见行 39 评论）。
- 判断：ErrorInfo允许null code，stops使用e.code().equals会二次NPE，应在结果构造处约束并用常量equals。
- 证据：harness-api/src/main/java/io/jharness/api/ToolBatchExecution.java:15

## OCR-0238 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolBatchExecution.java:39；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：行 39-41：`pipeline.execute` 没有异常兜底，也没有 null 校验。触发条件：(1) 通过 `StandardExtensionPoints.TOOL_BATCH` / `ToolPipeline` 注入的自定义 pipeline 抛出 RuntimeException（接口未约定必须自行捕获，只有 DefaultToolPipeline 内部捕获），(2) pipeline 返回 null。后果：本次调用没有任何 ToolResult 落库，已收集的前序结果随异常一起被丢弃，批量在前序已完成、当前调用可能已产生副作用的情况下直接中断；若返回 null，则 `stops(null)` 抛 NPE，`results.add(null)` 再在 `List.copyOf` 处抛出语义不明的 NPE，恢复流程只能从零散事件推断状态。建议逐调用兜底为失败结果，让既有 `stops()` 语义决定是否中断，并保证“每个已调度调用恰好一条结果”的前缀契约。
- 判断：自定义pipeline空返回/异常协议需明确并在批次边界保留事实；不能一律合成确定FAILED后继续，可能已产生UNKNOWN副作用。
- 证据：harness-api/src/main/java/io/jharness/api/ToolBatchExecution.java:39

## OCR-0239 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolBatchExecution.java:22；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：行 22：取消检查只在每次迭代开头做一次，随后还要执行 `EventSupport.append`（session 写入 + 事件总线发布，属于 I/O），之后才进入 `pipeline.execute`。触发条件：取消信号在 append 期间到达（或被取消后工具自身不复查 token）；由于 `serial()` 是公开扩展点、契约上不要求自定义 ToolPipeline 二次检查取消（只有 DefaultToolPipeline 内部复查），此时会出现在已取消状态下仍然启动工具执行、甚至执行有副作用工具的情况。建议把 `throwIfCancelled()` 移到 `pipeline.execute` 之前（或前后各检查一次）。
- 判断：serial append后可再检查取消以缩小窗口，但DefaultToolPipeline已有发送前检查；合作取消无法保证完全无竞态，应文档化责任。
- 证据：harness-api/src/main/java/io/jharness/api/ToolBatchExecution.java:22

## OCR-0240 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolBatchExecution.java:41；OCR原严重度：low
- 计划：R1-01 / P2
- 原意见：行 41-43：`break` 后直接返回前缀结果，对“已调度但未执行”的剩余调用既不返回占位结果，也不写任何批量停止事件（对比 BoundedReadOnlyToolScheduler.abort 会写 `tool.batch.stopped`，含 reason/pending）。目前 DefaultAgentLoop 只能通过 `results.size() != toolCalls.size()` 推断，并在异常分支把未决调用补记为 CANCELLED/UNKNOWN；但会话日志无法还原“为何后续调用被跳过、还剩哪些调用”，对崩溃恢复与审计不利。建议在 break 前补发一条包含停因和剩余调用数的批量停止事件。
- 判断：serial的stops后直接break未写tool.batch.stopped，与M8审计要求不齐；补原因/剩余call前缀事实，不补造未执行完成结果。
- 证据：harness-api/src/main/java/io/jharness/api/ToolBatchExecution.java:41

## OCR-0241 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolResult.java:13；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：缺少 status 与 error 的一致性约束：构造函数只校验非空，因此可以构造出矛盾结果，且当前仓库里已有可复现路径：McpPlugin 第 446-457 行在 JSON-RPC 返回 isError 时构造 `ToolStatus.FAILED` 但传 `Optional.empty()`；DockerExecutionEnvironment 第 205-217/231-237 行在容器内命令超时/未知时构造 `UNKNOWN`/工作线程返回的 `FAILED` 也传 `Optional.empty()`（EnvironmentWorker 的载荷根本没带 error）。后果：ToolResults.event 生成的事件只有失败 status 没有 `error` 字段，失败原因在下游（UI 投影、审计、checkpoint 判定）彻底丢失；反向地，SUCCESS 携带 `ErrorInfo(code=CHECKPOINT_FAILED)` 时 ToolBatchExecution 第 11-15 行会把成功的批次当作中断处理。建议在紧凑构造器里强制不变式（SUCCESS 必须无 error，非 SUCCESS 必须有 error），并同步修复上述调用点。
- 判断：SUCCESS携带错误/非成功无诊断可造成消费差异，采纳明确状态与诊断schema；UNKNOWN可有结构value事实，不能简单所有非成功强制无差别error。
- 证据：harness-api/src/main/java/io/jharness/api/ToolResult.java:13

## OCR-0242 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolResult.java:21；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：ErrorInfo 没有任何校验，code/message 都可以是 null 或空白字符串，会击穿下游：ToolResults.event 第 24-29 行用 `Map.of("code", error.code(), "message", error.message())` 生成事件，`Map.of` 拒绝 null，一旦出现 null 就会在失败路径上抛 NPE（例如 ToolResults.sanitized 第 40-41 行从 DataSanitizer 输出中 `(String) e.get(...)` 取不到键时就会构造出 null 字段）；空白 code 还会让 ToolBatchExecution 的 `"CHECKPOINT_FAILED".equals(e.code())`、SkillsPlugin 的 `ex.code().equals(...)` 等按 code 路由的逻辑静默失配。建议在 ErrorInfo 的紧凑构造器中复用 Values.required 做非空+非空白校验。
- 判断：ErrorInfo无紧凑校验，ToolResults.event使用Map.of会在null失败信息上NPE，构造时required并保留安全错误信息。
- 证据：harness-api/src/main/java/io/jharness/api/ToolResult.java:21

## OCR-0243 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolResult.java:9；OCR原严重度：low
- 计划：R3-01 / P2
- 原意见：modelContent 只做非空校验，没有长度上限（value/display 至少还有 64 层深度限制），而这个字段是协议出口：所有插件/MCP/进程工具的输出都会原样进入该字段，随后被 ToolResults.event 整体快照、写入事件日志，并通过 EnvironmentWorker 跨进程传输。仓库内部分调用点（BoundedJson 的 4MB、preview()）自行限界，但第三方 AgentTool 直接构造时传入任意大字符串即可让该结果在内存/事件持久化中被整体复制，造成无界数据处理。建议在协议边界统一限制 modelContent 长度并附加截断标记（或在文档中明确要求调用方限界并校验）。
- 判断：ToolResult.modelContent无公共上限，可统一外部边界限额/产物溢出；静默截断结构化JSON会破坏协议，不能直接substring。
- 证据：harness-api/src/main/java/io/jharness/api/ToolResult.java:9

## OCR-0244 · 不采纳

- 位置：harness-api/src/main/java/io/jharness/api/WorkspaceFiles.java:18；OCR原严重度：medium
- 计划：无
- 原意见：默认 applyPatch 没有检查 cancellation：即使 token 已取消，也会以“能力不支持”返回，调用方可能把它当作可重试的能力缺失而非 CANCELLED。同时错误码使用泛化的 "UNSUPPORTED"（ProcessRunner/StreamHandle/WorkspaceToolsPlugin/CLI 都复用了同一个码），调用方按 code 分支时无法区分“补丁未执行”与其他不支持的能力，也无法判断该失败是否意味着零副作用。建议先 cancellation.throwIfCancelled()，并使用专属码（如 UNSUPPORTED_PATCH_PROVIDER），在契约上明确“该失败表示补丁完全未执行、无需做 UNKNOWN 恢复”。触发条件：传入已取消的 token 或按 code 分类失败的调用方。
- 判断：applyPatch默认立即UNSUPPORTED保证没有执行，无需为了取消优先级改变所有能力拒绝错误；UNSUPPORTED本来可由调用操作上下文区分。
- 证据：harness-api/src/main/java/io/jharness/api/WorkspaceFiles.java:18

## OCR-0245 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/WorkspaceFiles.java:35；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：write/edit 的 overwrite 与 expectedVersion 优先级、以及 expectedVersion 为 null 的语义在契约中缺失，而两个实现都把它解释为“回退到本地最后观察到的版本”（LocalWorkspace.write 中 expected==null 时仍抛 FILE_CONFLICT；RemoteWorkspace.write 用 observed.get(...) 兜底）。触发条件：对一个从未 read/versionOf 过的路径以 overwrite=true、expectedVersion=null 调用 write，或调用 edit（readTextForEdit 只读取不更新 observed，随后委托 write(null)），都会得到伪造的 FILE_CONFLICT；反之，第三方实现若认为 overwrite=true 可跳过版本校验，则会绕过并发保护形成 TOCTOU 写入。建议在接口上写明“expectedVersion 始终参与校验，overwrite 仅表示允许覆盖已存在文件”，并明确 null 的含义或禁止 null。
- 判断：overwrite只允许存在目标、不取消版本保护应写明；null有观察缓存兼容含义，未证实正常edit必然冲突，需跨provider契约测试。
- 证据：harness-api/src/main/java/io/jharness/api/WorkspaceFiles.java:35

## OCR-0246 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/WorkspaceFiles.java:52；OCR原严重度：medium
- 计划：R2-01 / P3
- 原意见：gitDiff 的 scope 是未加约束的自由字符串：LocalWorkspace 仅接受 working/staged，而 RemoteWorkspace 把它原样转发给 worker，其他实现则可能直接拼进 git 参数或 shell 命令。触发条件：实现方把 scope 直接交给外部命令时，形如 "working; rm -rf" 的输入会越出命令/工作区边界；同一调用在不同提供方也会表现出可接受/拒绝不一致。建议在契约层收敛为枚举或显式白名单，并在接口默认方法中做校验（非法值抛 INVALID_SCOPE）。
- 判断：gitDiff现有working/staged白名单应公开，插件假想拼shell不是内置注入；不必破坏性改枚举。
- 证据：harness-api/src/main/java/io/jharness/api/WorkspaceFiles.java:52

## OCR-0247 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/WorkspaceFiles.java:21；OCR原严重度：low
- 计划：R2-01 / P2
- 原意见：list/readPage/search/grep 的 depth、limit、startLine、endLine 都是裸 int，接口未约定取值范围与边界含义，实现间也不一致：LocalWorkspace 校验 depth 1..16、limit 1..5000、startLine>=1、endLine 含端且 endLine-startLine<2000，而 RemoteWorkspace 不做校验直接转发。触发条件：同一段调用代码在本地提供方得到 INVALID_PAGE/INVALID_LIMIT，在远程提供方却返回整页或无界结果；endLine<startLine 或 limit=0 时的行为完全由实现决定。此外这些方法返回未定义键的 Map（entries/nextLine/truncated 等），跨提供方缺少 schema 约定，调用方易出现空指针或解析失败。建议在接口上明确各参数的合法区间、边界是否含端、null 语义，并定义返回 Map 的必需键（或改为强类型返回）。
- 判断：WorkspaceFiles分页/深度/结果Map最小schema确实未写，补Local/Remote一致契约；远端worker仍会校验，原文远端无界结论过度。
- 证据：harness-api/src/main/java/io/jharness/api/WorkspaceFiles.java:21

## OCR-0248 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Arguments.java:63；OCR原严重度：high
- 计划：R3-04 / P1
- 原意见：值选项会无条件吞掉下一个 token，即使该 token 是另一个选项或 `--` 终止符。触发条件：`jharness run --workspace --run "task"` 会把 `--run` 当作 workspace 的取值，随后 `--run` 本身被跳过，既不会报 usage 错误，也不会被解析；类似 `--session --dry-run` 会静默把 dry-run 标志变成 session 名，导致运行模式被意外关闭（dry-run 失效会真实执行命令/写入文件）。建议在消费值之前校验下一个 token 不是 `--`、也不是已知选项名。
- 判断：Arguments消费值只检查数组长度，--workspace --dry-run会吞掉安全模式标志；应拒绝已知选项/终止符作为缺失值并提供明确字面量方案。
- 证据：harness-cli/src/main/java/io/jharness/cli/Arguments.java:63

## OCR-0249 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Arguments.java:56；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：任何不以 `--` 开头的 token（包括 `-h`、`-help`、`-workspace`、`-dry-run` 这类短横线选项/拼写错误）都会被当成位置参数收集，从而绕过 `FLAGS`/`VALUES` 白名单校验。触发条件：`jharness run -dry-run "task"` 或用户误输入单横线形式时，`-dry-run` 会作为任务文本/位置参数继续下发（例如被当作 subcommand 或 task 描述），而不是报 usage 错误，可能出现“以为开了 dry-run 实际没有”的静默行为差异。建议对以单个 `-` 开头的 token 直接 `fail()`。
- 判断：单横线非选项现当位置文本，采纳在明确CLI选项位置拒绝常见拼写错；任务正文合法可始于-，保留--终止语义，不全局禁止。
- 证据：harness-cli/src/main/java/io/jharness/cli/Arguments.java:56

## OCR-0250 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Arguments.java:46；OCR原严重度：medium
- 计划：R3-04 / P3
- 原意见：`options` 与 `positional` 都是包可见的可变集合，同包调用方可以直接对内部 List 增删（包括塞入空 List）。一旦某个 key 对应的列表为空，`get()` 中的 `getFirst()` 会抛 `NoSuchElementException`（未被转换为 `HarnessException`），破坏 CLI 统一错误码约束；同时 `all()` 返回的是内部活动列表，调用方 `addAll`/`clear` 会反向污染解析结果。建议对外只暴露不可变视图（或提供 `Set`/`Map` 的只读包装），并让 `get()` 在列表为空时走 `fail()` 而不是直接 `getFirst()`。
- 判断：all返回内部活动List，采纳只读快照/封装；当前包内调用未修改，非已证实getFirst空列表运行故障。
- 证据：harness-cli/src/main/java/io/jharness/cli/Arguments.java:46

## OCR-0251 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Arguments.java:74；OCR原严重度：medium
- 计划：无
- 原意见：`get()` 对可重复选项（`require-read`/`expect-file`/`bundle`）只返回第一个值，调用方若用 `get` 而非 `all` 会静默丢弃后续取值（例如只校验到部分必需读取文件/期望变更文件即判定完成，属于完成度校验绕过）。建议对可重复选项显式报错，或在文档/命名上强制可重复选项只能通过 `all()` 读取，例如：`if (REPEATABLE.contains(key) && options.get(key).size() > 1) fail();`。
- 判断：get/all分别读取单值和多值属常见解析器API；现require-read/expect-file/bundle调用使用all，未发现丢弃后续义务的真实调用。
- 证据：harness-cli/src/main/java/io/jharness/cli/Arguments.java:74

## OCR-0252 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/Values.java:40；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：环检测仅依赖 depth>64 的计数，既没有身份集合（IdentityHashMap）识别真正的循环引用，也没有总节点/条目上限，且 List/Map 每次出现都无条件深拷贝。对于共享子结构的 DAG（同一子 Map 被 k 个键引用并逐层嵌套），展开的路径数是 k^depth 级别：例如宽度为 2 的自引用 Map 需要遍历约 2^65 次才会触发深度保护（现有 SessionEventTest 只覆盖了单自引用，无法暴露该问题）。StdioSdkServer 会直接对 Values.object(Json.parse(line)) 处理外部 SDK 输入，几十字节即可构造出在保护触发前耗尽 CPU/内存的载荷，属于有界数据处理缺失。建议用身份集合在进入容器时登记、返回时移除以尽早发现真环，并对总节点数设置上限。
- 判断：freeze仅深度限额且重复展开共享DAG可放大，应加节点预算/身份检测；自引用会在首条深递归即抛，JSON文本不能表达Java引用环，原文几十字节JSON攻击不成立。
- 证据：harness-api/src/main/java/io/jharness/api/Values.java:40

## OCR-0253 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/Values.java:64；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：同一类“非法 JSON”在不同入口抛出的异常类型/错误码不一致：object() 对非 String key 抛 HarnessException("INVALID_JSON")，而 snapshot()/freeze() 对同样的 key（本行）以及不支持的值类型抛 IllegalArgumentException。上层 StdioSdkServer.run 只识别 HarnessException.code()，其余异常一律映射为 SDK_OPERATION_FAILED，于是经由 freeze 的校验失败会丢失 INVALID_JSON 语义，调用方按错误码分支处理时也会漏掉这些失败。建议 freeze 内部统一抛出 HarnessException("INVALID_JSON", ...)。
- 判断：object与freeze异常类别不同可改善公共JSON入口映射；内部快照的IllegalArgumentException合理，不强制所有方法改业务异常。
- 证据：harness-api/src/main/java/io/jharness/api/Values.java:64

## OCR-0254 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/Values.java:52；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：Double.NaN / Infinity（以及下面一行对应的 Float 非有限值）会穿过 isFinite 判断，最终落到第 69 行抛出 “Not a JSON value: java.lang.Double”。这把“非有限数值”误报成“不支持的 Java 类”，错误信息与真实原因不符，排查时会误导。建议显式判断非有限数值并给出明确原因（例如 "JSON number must be finite"）。
- 判断：NaN/Infinity最终报Not a JSON value:Double已拒绝但诊断不准，增加finite number明确错误即可。
- 证据：harness-api/src/main/java/io/jharness/api/Values.java:52

## OCR-0255 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/Values.java:10；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：required() 使用 isBlank()，使空字符串被判定为“缺失”。但本类的 snapshot()/freeze() 把它用于对象 key 校验（第 18、65 行），而空字符串键在 JSON 中是合法的，于是 snapshot(Map.of("", x)) 或 object(含 "" 键的外部载荷) 会抛出 “key is required”。这既把“缺失”与“非法空白”混为一谈，也拒绝了合法 JSON 对象。建议对象键校验只拒绝 null（或对空串给出明确的非法 key 信息），与用于必填字段的 required() 分离。
- 判断：snapshot用required拒绝空白对象key，比JSON标准更窄，外部MCP/SDK对象可能受影响；区分对象键和业务必填字段并补空key往返测试。
- 证据：harness-api/src/main/java/io/jharness/api/Values.java:10

## OCR-0256 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolSchedulingOptions.java:26；OCR原严重度：low
- 计划：R3-04 / P3
- 原意见：对 `map.keySet()` 调用 `Set.of(...).containsAll(...)` 时，JDK 不可变集合的 `contains(null)` 会抛 `NullPointerException`。`from(Object)` 是公开 API 且类型未受约束，若调用方传入含 null 键的 Map（例如程序化构造的 HashMap 或宽松解析的配置），这里会抛出 NPE 而不是 `INVALID_TOOL_SCHEDULING`，跳过统一的配置错误处理路径。建议先显式校验键类型/非空，或遍历 keySet 做 `instanceof String` 判断，再交给允许键集合。
- 判断：程序化Map的null键可使from报NPE，采纳INVALID_TOOL_SCHEDULING类型校验；合法JSON键不可能null。
- 证据：harness-api/src/main/java/io/jharness/api/ToolSchedulingOptions.java:26

## OCR-0257 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolSchedulingOptions.java:55；OCR原严重度：low
- 计划：R3-04 / P2
- 原意见：整数校验不严谨（约第54-57行）。用 `n.doubleValue() != n.intValue()` 判断整数会放过“double 恰好舍入为整数”的非整数 Number：例如 `new BigDecimal("2.0000000000000000001")` 的 `doubleValue()` 为 2.0、`intValue()` 为 2，于是被当成合法并发度 2 接受，与 `ModelResilienceOptions.number()` 中更严格的校验不一致；同时这里缺少 `Double.isFinite` 检查，且 `intValue()` 对超出 int 范围的 Number 会截断，可能掩盖越界输入。建议只接受 Byte/Short/Integer/Long（或对 BigDecimal 做精确比较）并显式校验有限性与 int 范围。
- 判断：调度number的double/int比较接受精确非整数BigDecimal，改精确整数与范围校验，和韧性配置统一。
- 证据：harness-api/src/main/java/io/jharness/api/ToolSchedulingOptions.java:55

## OCR-0258 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolSchedulingOptions.java:31；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：单键配置被静默丢弃（约第31-32行）。`mode` 缺省为 `serial`，于是任何只含一个键的 map 都满足 `map.size() <= 1` 并直接返回 `serial()`：例如 `{"maxConcurrency":8}`、`{"timeoutSeconds":5}`、`{"tools":["read_file"]}` 都会被解析成串行 + 空 tools + 30s 超时，调用者显式给出的并发上限、超时和只读工具白名单全部被无提示忽略。这既不是“默认串行”语义，也让宿主误以为白名单已生效（实际回退为空 allowlist），属于配置校验被绕过的安全/正确性缺陷。建议仅在 map 为空或仅含 `"mode":"serial"` 时才返回 `serial()`，其余带其他键的单键输入必须显式报错（或按实际值构造），不能静默返回默认值。
- 判断：mode缺省serial且map.size<=1直接默认，单timeoutSeconds/maxConcurrency/tools被丢弃，需拒绝冲突单键或保留显式值。
- 证据：harness-api/src/main/java/io/jharness/api/ToolSchedulingOptions.java:31

## OCR-0259 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolSchedulingOptions.java:61；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：`configuration()` 的输出无法被 `from()` 回读，配置协议不对称（约第60-69行 vs 第27行/第32-35行）：1) 输出键是 `timeoutMillis`，而 `from()` 的允许键集合只含 `timeoutSeconds`，任何 `from(configuration())` 都会抛 `INVALID_TOOL_SCHEDULING`；2) `maxConcurrency == 1` 时输出 `mode=serial` 同时携带 `maxConcurrency/tools/timeoutMillis`，`map.size() > 1`，`from()` 会把它当成 “Unknown or conflicting scheduling mode” 拒绝。该 Map 会写入 `sdk.turn.configured`、环境描述符与 CLI 日志（HarnessSdk/CliApplication/EcosystemCli），一旦这些已记录配置被复用作输入或用于校验就会被误判，且串行配置永远不可往返。建议统一超时键名（输出 `timeoutSeconds`，或让 `from()` 同时接受 `timeoutMillis`），并让 serial 形式与 `from()` 的可接受形状一致（例如 serial 只输出 `{"mode":"serial"}`，或 `from()` 接受完整的 serial 形式）。
- 判断：configuration输出timeoutMillis而from输入timeoutSeconds事实成立；前者用于描述符不是承诺可回读的配置，先区分schema文档，若提供导入再补转换。
- 证据：harness-api/src/main/java/io/jharness/api/ToolSchedulingOptions.java:61

## OCR-0260 · 采纳

- 位置：harness-api/src/test/java/io/jharness/api/SessionEventTest.java:31；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：这两条用例并非真正锁定 `SessionEvent` 的类型→级别映射：`tool.started` 期望级别本就是 TOOL，实际异常来自 `level == TOOL` 时缺失 `callId`；`turn.started` 期望级别本就是 TURN，实际异常来自多余/非法的 `stepId` 关联字段。即使映射表中的期望级别被误删/误改，用例仍会通过。建议为两类用例补上合法关联字段、只让级别错误触发 `expected != level`，并增加映射正确时构造成功的正向断言。
- 判断：两个负例同时关联字段非法，可在类型等级映射失效时照样失败；补仅错等级的单因子负例和正例。
- 证据：harness-api/src/test/java/io/jharness/api/SessionEventTest.java:31

## OCR-0261 · 采纳

- 位置：harness-api/src/test/java/io/jharness/api/SessionEventTest.java:14；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：用例名声称验证“nesting bound（64 层上限）”，但自引用 Map 既可能被循环检测拒绝、也可能被深度上限拒绝（Values.freeze 第 40 行），无法区分；且完全没有触达边界本身。建议增加 64 层嵌套（应通过）与 65 层嵌套（应抛出，且消息定位到深度上限）的对照用例，否则实现把深度上限改成 32 或改为循环检测时该用例仍会通过，无法保护声明的不变量。另外该用例还混入了 SecretRedactor 断言，失败时难以定位。
- 判断：自引用测试只能证明某种拒绝，不能锁定64层边界；补实际64/65嵌套对照。
- 证据：harness-api/src/test/java/io/jharness/api/SessionEventTest.java:14

## OCR-0262 · 部分采纳

- 位置：harness-api/src/test/java/io/jharness/api/SessionEventTest.java:15；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：脱敏覆盖过窄：SecretRedactor.sanitize 使用 `key.toLowerCase(Locale.ROOT)` 做大小写无关匹配（含嵌套 Map 递归），但这里只验证了全小写 `api_key` 与 List 中的一个精确哨兵值。建议补充：(1) 大写/混合大小写键如 `API_KEY`、`Authorization`、`Password` 应被替换；(2) 嵌套 Map 内的敏感键应被替换；(3) 只出现前缀/部分匹配的文本不应被误改或漏改；(4) 断言入参 map 与入参字符串未被就地修改（脱敏必须保持请求重放等价）。
- 判断：现只小写api_key与列表值，可补大小写/嵌套/未知键保留测试；不要求对任何未知秘密作全覆盖承诺。
- 证据：harness-api/src/test/java/io/jharness/api/SessionEventTest.java:15

## OCR-0263 · 采纳

- 位置：harness-api/src/test/java/io/jharness/api/SessionEventTest.java:54；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：只验证了顶层不可变，`payload()` 内部的嵌套 Map/List 是否真的不可变未被验证：Values.freeze 若在未来只对顶层做 unmodifiable 包装，本用例仍会通过。建议补上对嵌套结构的写操作断言，例如对 `event.payload().get("nested")` 及其 `list` 执行 put/add 并期望 UnsupportedOperationException，同时验证原始入参 map 本身保持不变。
- 判断：顶层不可变不足以证明深快照，应补嵌套Map/List的源修改与返回修改测试。
- 证据：harness-api/src/test/java/io/jharness/api/SessionEventTest.java:54

## OCR-0264 · 采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolResults.java:0；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：`Map.of` 不接受 null 值，而 `ToolResult.ErrorInfo` 只是普通 record，未校验 `code`/`message` 非空。触发条件：`failure(id, status, null)`（`failure()` 不校验 code，`ErrorInfo(null,null)` 会被构造成功）或上面 `sanitized()` 用 null 子键构造的错误信息。此时 `failure()` 不报错，NPE 延迟到记录 `tool.completed` 时才抛出；结合 `DefaultToolPipeline.finish()`/`BoundedReadOnlyToolScheduler.drain()` 的调用位置，会导致完成事件缺失、调用悬空，错误信息还会误导排查方向。建议在 `failure()` 入口用 `Values.required(code, "code")` 校验，并在此处显式处理 null 后再构造 Map。
- 判断：failure空code可构造ErrorInfo并在event Map.of处NPE，与0242同根，统一构造校验及负例。
- 证据：harness-api/src/main/java/io/jharness/api/ToolResults.java:声明/现有代码

## OCR-0265 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolResults.java:0；OCR原严重度：high
- 计划：R1-01 / P2
- 原意见：`ToolResults.sanitized()` 直接按 `Map`/`String` 强转重建结果，未校验自定义 `DataSanitizer` 的输出结构：`value`/`display` 被替换为非对象、`content` 被删除或改为非字符串、`error.code`/`message` 缺失或类型不符时，会得到 `INVALID_JSON`、`ClassCastException` 或无信息 NPE，且失败路径上的 `ErrorInfo` 可能被静默构造为 null 字段。该异常发生在 `DefaultToolPipeline.finish()`/`BoundedReadOnlyToolScheduler.drain()` 中，会中断完成事件写入。建议在重建前显式校验所有必需键及类型，给出受控失败或安全占位值。
- 判断：sanitized重建依赖sanitizer固定结构，采纳结构校验和明确失败；不得在自定义sanitizer失败时降级原文。
- 证据：harness-api/src/main/java/io/jharness/api/ToolResults.java:声明/现有代码

## OCR-0266 · 部分采纳

- 位置：harness-api/src/main/java/io/jharness/api/ToolResults.java:0；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：`event()` 返回的是未经脱敏的原始快照：`value`、`modelContent`、`display` 原样写入并对外公开，方法名与签名没有任何“raw/未脱敏”提示。本次扫描的现有调用点（`DefaultToolPipeline.finish` 先 `sanitized()`、`DryRunPipeline` 先对 plan 脱敏）均未发现实际泄漏，但该 public API 极易被新增的日志/会话持久化路径直接用于 `event(rawResult)`，从而把可能含凭据、路径、命令输出的工具结果落盘或外发，绕过 `SecretRedactor`。建议补充 javadoc 明确“只能传入已脱敏结果”，或收窄可见性/提供 `event(result, sanitizer)` 组合入口，从类型或 API 层面阻止原始事件被直接使用。
- 判断：ToolResults.event是原始格式化器，默认管线已先sanitized，采纳raw/责任文档；不能重复脱敏每个内部格式化器导致请求不一致。
- 证据：harness-api/src/main/java/io/jharness/api/ToolResults.java:声明/现有代码

## OCR-0267 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EvidenceCompletion.java:56；OCR原严重度：high
- 计划：R1-06 / P2
- 原意见：`--verify-argv` is parsed without any guard. `Json.parse` throws `HarnessException("INVALID_JSON")` for any non-JSON argv (e.g. `--verify-argv 'mvn test'`), and returns Java `null` for the literal `null`, which makes the `argv.equals(...)` comparison at line 65 throw an NPE. Since the loop only converts `RuntimeException` into a terminal FAILED run, a malformed criterion aborts the whole run as `INVALID_JSON`/`RUNTIME_FAILED` instead of producing a clear negative evidence decision. Validate the criterion once, require a JSON array of strings, and return a dedicated reason.
- 判断：verify-argv Json.parse可为null/非数组导致argv.equals NPE，应CLI参数期验证字符串数组并给CLI_USAGE/明确安全错误。
- 证据：harness-cli/src/main/java/io/jharness/cli/EvidenceCompletion.java:56

## OCR-0268 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EvidenceCompletion.java:72；OCR原严重度：medium
- 计划：R1-06 / P2
- 原意见：`getOrDefault` only protects against a missing key, not against an explicit `exitCode: null` (null value → NPE on `intValue()`) or a non-numeric value recorded by a legacy/foreign/worker event (→ `ClassCastException`). Both escape the evaluator and kill the run instead of yielding a negative decision. Use an `instanceof` check and treat anything non-numeric as a failed verification.
- 判断：exitCode直接(Number)且getOrDefault不保护显式null，损坏事件会报告崩溃；验证数值整数并返回未验证不冒称通过。
- 证据：harness-cli/src/main/java/io/jharness/cli/EvidenceCompletion.java:72

## OCR-0269 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EvidenceCompletion.java:62；OCR原严重度：medium
- 计划：R1-06 / P2
- 原意见：`lastWrite` is advanced for every event that merely declares `sideEffectLevel=WORKSPACE_WRITE`, regardless of its status or whether the tool body was entered. A write that was denied, failed, or never executed after a passing verification therefore has no file effect but still moves `lastWrite` past `verification`, so line 77 reports `VERIFICATION_MISSING_OR_STALE` even though the verification is still valid (false negative that makes evidence criteria unachievable in deny-heavy policies).
- 判断：lastWrite按级别连DENIED都更新，可能把未执行写尝试当验证过期；定义实际写入事实/不确定写保守阻断，不能仅SUCCESS忽略UNKNOWN。
- 证据：harness-cli/src/main/java/io/jharness/cli/EvidenceCompletion.java:62

## OCR-0270 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EvidenceCompletion.java:69；OCR原严重度：medium
- 计划：R1-06 / P2
- 原意见：`verification`/`passed` are overwritten by *any* matching command event, including one whose body never ran (`bodyEntered` false, e.g. denied or cancelled before launch with `exitCode -1`). Such an event appears after the successful verification and replaces its result, turning a valid verification into `VERIFICATION_FAILED`. Only a command that actually entered its body should be able to select/override the verification result.
- 判断：matching未进入主体的拒绝命令会覆盖此前成功验证，可明确最近实际执行与拒绝尝试各自语义；保守失败不构成错误成功，按需求定可用性。
- 证据：harness-cli/src/main/java/io/jharness/cli/EvidenceCompletion.java:69

## OCR-0271 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EvidenceCompletion.java:33；OCR原严重度：medium
- 计划：R1-06 / P2
- 原意见：Path comparison is asymmetric: only the CLI value goes through `normalize`, while the recorded payload path is compared verbatim. `normalize` (line 84-85) also does not canonicalize `..`, `.`, trailing separators or absolute forms, so `--require-read ./a/../a.txt`, `--require-read a.txt/` or an absolute path can never match recorded evidence, and `--verify-cwd .` does not match a recorded `cwd` of `./` (because `normalize("./") == ""`). Normalize both sides with one shared canonicalizer and reject absolute/`..` criteria up front with a distinct reason instead of silently emitting an evidence-missing decision.
- 判断：比较只normalize CLI端、事件路径原样，等价合法路径可能漏证据，统一规范化两端但先拒穿越/绝对形态，不为匹配放宽FS边界。
- 证据：harness-cli/src/main/java/io/jharness/cli/EvidenceCompletion.java:33

## OCR-0272 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EvidenceCompletion.java:46；OCR原严重度：medium
- 计划：R1-06 / P2
- 原意见：Unchecked cast on a persisted event payload: `afterVersion` may be absent, non-String (legacy/foreign/remote event) or the change may describe a deletion, in which case this throws `ClassCastException` or overwrites a previously valid version with `null`, which then surfaces as `CHANGE_EVIDENCE_MISSING` even though write evidence exists. Guard the type and distinguish "deleted/absent version" from "no evidence".
- 判断：afterVersion直接强转且可能null/非串，校验变更schema并明确删除语义，不让报告路径被ClassCastException中止。
- 证据：harness-cli/src/main/java/io/jharness/cli/EvidenceCompletion.java:46

## OCR-0273 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EvidenceCompletion.java:99；OCR原严重度：medium
- 计划：R1-06 / P2
- 原意见：`changes()` also trusts the payload shape: a non-`List` `changes` member triggers a `ClassCastException`, and `Values.object(...)` throws `HarnessException("INVALID_JSON")` for non-map members. In the `expect-file` loop only the `versionOf` call is wrapped in try/catch, so an exception raised here escapes the evaluator and fails the whole run instead of returning negative evidence. Validate with `instanceof List<?>` and catch/convert malformed payloads.
- 判断：changes直接List强转/Values.object，外部/损坏日志应按schema明确未验证并保留定位，不能跳过错误后声称义务通过。
- 证据：harness-cli/src/main/java/io/jharness/cli/EvidenceCompletion.java:99

## OCR-0274 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/ConfigurationSelection.java:21；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：profile/preset 里的 `preset`、`modelRoute` 直接强转为 String（本行，以及第 28-29 行的 `presetRoute`/`profileRoute`）。CliConfig 构造函数只校验 catalogs 的条目是对象、内部字段类型未校验，因此配置写成 `{"profiles":{"p":{"preset":123}}}` 或 `{"preset":{"modelRoute":{}}}` 时，这里会抛 ClassCastException 而不是受控错误，CliApplication 只能归类为 CLI_IO_OR_CONFIG_ERROR（退出码 5），丢失 INVALID_CONFIG / INVALID_CONFIG_SELECTION（退出码 64）的语义。建议统一用带类型校验的取值方法（例如 `text(Object)`：null 返回 null，非 String 抛 `error("INVALID_CONFIG")`）。
- 判断：ConfigurationSelection直接(String)转换profile/preset/modelRoute，畸形catalog会ClassCastException，需在选择前校验并统一配置错误；不改合法优先级。
- 证据：harness-cli/src/main/java/io/jharness/cli/ConfigurationSelection.java:21

## OCR-0275 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/ConfigurationSelection.java:84；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：只要选择了任意 bundle，就用聚合结果无条件覆盖已合并的 enabledTools/prompts。两类可复现问题：(a) 仅含 policy 的 bundle（例如只读 bundle）会把 base/profile 显式配置的 enabledTools、prompts 清空——空 enabledTools 在 CliApplication 中被当作精确白名单（FilteredToolRegistry 允许集为空），agent 将失去全部工具；(b) base 配置用 enabledTools 做的工具白名单会被任意 bundle 的工具并集整体替换，等于可以通过 `--bundle` 放宽基础配置的工具边界。建议在循环中记录各键是否被 bundle 显式声明，仅在声明时覆盖，并在 base 作为上限时与已合并白名单取交集。
- 判断：任意bundle都重写enabledTools/prompts为空并集，纯policy bundle会清空base工具，采纳只在显式声明时覆盖；CLI可信bundle并集本是产品组合语义，不能默认改交集。
- 证据：harness-cli/src/main/java/io/jharness/cli/ConfigurationSelection.java:84

## OCR-0276 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/ConfigurationSelection.java:75；OCR原严重度：low
- 计划：R3-04 / P2
- 原意见：`putIfAbsent` 对 null 值按“不存在”处理，配合 `prior != null` 判断会漏掉冲突：bundle 写成 `{"policy": null}` 时第一个 null 被写入且不报错，后续 bundle 的不同 policy（如 "default"）也会被静默覆盖，违反 “bundles must agree” 约束；该 null 最终还会写进 resolved 并在 CliConfig 里以 INVALID_CONFIG 失败（第 128 行 string("policy")）。建议合并前校验 bundle policy 只能是 default/read-only，并用 `bundleSettings.containsKey("policy")` 判断是否已存在。
- 判断：putIfAbsent与prior!=null漏显式null的policy冲突，合并前校验policy合法值并用containsKey判存在。
- 证据：harness-cli/src/main/java/io/jharness/cli/ConfigurationSelection.java:75

## OCR-0277 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/ConfigurationSelection.java:93；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：resolvedSha256 在把 models 目录写回 resolved 之前计算，随后第 96-97 行又向同一 map 写入 models 并作为 CliConfig.values 返回，摘要覆盖范围与实际生效配置不一致：两份仅在 models 目录上不同（例如同一路线名对应不同 baseUrl/apiKeyEnv）的配置会得到相同的 resolvedSha256。该值会被 TaskDiagnosis 作为 settingsRevision 传播用于链路复核，属于可复现的完整性问题。建议先写回 models 再计算摘要，或计算前显式剔除并保证返回值同样不含 models。
- 判断：resolvedSha256先算后models回填，需定义摘要到底覆盖已选配置还是完整catalog，并保持命名/返回一致；不直接认定未选模型目录必须改变当前配置摘要。
- 证据：harness-cli/src/main/java/io/jharness/cli/ConfigurationSelection.java:93

## OCR-0278 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/ConfigurationSelection.java:36；OCR原严重度：low
- 计划：R3-04 / P2
- 原意见：route 为保留名 "base" 时也会走 named() 查找，而 CliConfig 明确禁止 models 中出现 base（“base is the reserved default model route”），因此 `--model-route base` 或 profile/preset 写成 `modelRoute: "base"` 都会抛 CONFIG_SELECTION_NOT_FOUND；但第 91 行把未选择时的路线记为 "base"，CliApplication 的 LazyModelRouter 也注册了 base 工厂，说明 base 是合法选择，导致无法显式回退到默认路线。建议对 "base" 跳过模型目录查找。
- 判断：base是保留默认路由但resolve对非null一律named(models,route)，显式--model-route base找不到，应处理保留名并补回退测试。
- 证据：harness-cli/src/main/java/io/jharness/cli/ConfigurationSelection.java:36

## OCR-0279 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/DryRunPipeline.java:23；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：BEFORE_TOOL 拦截器在第 28 行参数校验、第 29 行取消检查、第 36-46 行策略判定之前就被调用，且没有任何回滚。触发条件：dry-run 时插件在 BEFORE_TOOL 中修改作用域/会话状态（插件作用域注册、临时文件、预算计数等），随后策略返回 DENY、拦截器改坏 callId/tool 触发第 26-27 行异常，或第 29 行判定已取消。此时调用从未真正执行，但拦截器副作用仍留在 scope/session 中，dry-run 可被用来持久改变插件状态。默认管线 DefaultToolPipeline.prepare（第 55-73 行）是先 throwIfCancelled + 校验 proposed.arguments() 再触发 hook。建议把取消检查与 proposed 参数校验前置，并对 DENY/取消路径回滚或跳过错副作用 hook。
- 判断：dry-run在取消/初始schema前调用BEFORE_TOOL，与默认管线顺序不齐，采纳前置校验；可信插件hook自发文件副作用无法由管线通用回滚。
- 证据：harness-cli/src/main/java/io/jharness/cli/DryRunPipeline.java:23

## OCR-0280 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/DryRunPipeline.java:28；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：此处缺少 DefaultToolPipeline.prepare 第 70-73 行的 SECRET_ARGUMENT fail-closed 校验。触发条件：模型把敏感值（如 token/密码）放进 write_file.content 等参数后执行 dry-run，previewer 会基于这些本应被拒绝的参数生成计划，plan 中 previewable=true，而同样的调用在真实执行时必然被 SECRET_ARGUMENT 拒绝。结果是预览结论与真实执行不一致，并把本应拒绝的参数送进预览/脱敏链路（脱敏只覆盖已配置的已知凭据）。建议在 schema 校验后复用相同的“sanitize 后是否变化”检查。
- 判断：DryRunPipeline缺默认管线sanitize后参数变更的SECRET_ARGUMENT检查，导致预览与执行判定不齐，应复用拒绝规则。
- 证据：harness-cli/src/main/java/io/jharness/cli/DryRunPipeline.java:28

## OCR-0281 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/DryRunPipeline.java:46；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：preview 的预期失败没有像默认管线那样转成工具级失败结果。LocalPlanPreviewer.preview 在目标已存在且 overwrite=false（FILE_EXISTS）、编辑匹配数不符（EDIT_MATCH_CONFLICT）、oldText 为空（INVALID_EDIT）、FILE_LIMIT、FILE_CONFLICT、PREVIEW_IO 时直接抛 HarnessException；本类没有 try/catch，异常会冒泡到 DefaultAgentLoop（第 322-367 行），使整个 turn 变为 FAILED，并把该 call 补记成 CANCELLED 的 tool.completed，同时不会写出 tool.preview，模型无法看到可纠正的失败原因。建议捕获 HarnessException，用 ToolResults.failure 或 previewable=false+reason 生成结果并正常发出 tool.preview / tool.completed，保持与真实执行一致的错误可恢复性。
- 判断：LocalPlanPreviewer预期冲突抛HarnessException会冒泡整轮，而无tool.preview反馈；将可纠正冲突映射失败预览并保留取消/UNKNOWN终止。
- 证据：harness-cli/src/main/java/io/jharness/cli/DryRunPipeline.java:46

## OCR-0282 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/DryRunPipeline.java:61；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：策略 DENY 与“仅预览”两种语义完全不同的结果都返回 ToolStatus.DENIED + 错误码 DRY_RUN_NOT_EXECUTED：policy DENY 时 PERMISSION_DENIED 只出现在 plan.reason，ToolResult 的 error 仍是 DRY_RUN_NOT_EXECUTED，审计/协议消费方必须解析 plan 才能区分“计划已生成待执行”和“权限被拒”；另外 policy 返回 ASK 时也没有记录“执行需审批”（默认管线会发 approval.decided，见 DefaultToolPipeline 第 110-121 行）。建议 DENY 分支使用 PERMISSION_DENIED 错误码或独立状态，并把 policy 决策/approvalRequired 显式写入 plan。
- 判断：DENY和仅预览同DRY_RUN_NOT_EXECUTED不易区分，采纳policyDecision/approvalRequired结构；ASK预览不需要实际审批。
- 证据：harness-cli/src/main/java/io/jharness/cli/DryRunPipeline.java:61

## OCR-0283 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/DryRunPipeline.java:66；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：同一份 plan 被重复落盘：tool.preview 事件（第 48-57 行）、ToolResult.value、modelContent=Json.text(plan)，以及这里的 ToolResults.event(result)（ToolResults.java 第 21-22 行同时写 value 和 content）都会各存一份；EventSessionProjection 还会把 content 作为下一轮 tool 消息再次送入模型。本类对 plan 大小没有任何上限（仅 LocalPlanPreviewer 内部把 diff 截断到 8KB），大会话/大 preview 时造成日志与上下文成倍放大。建议在写出前统一做大小/截断校验，并避免 value 与 content 双份冗余。
- 判断：plan在preview/value/content多份保存，采纳统一有界快照和测量；value/content分别供机器和模型使用，不能一律删除。
- 证据：harness-cli/src/main/java/io/jharness/cli/DryRunPipeline.java:66

## OCR-0284 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/DryRunPipeline.java:33；OCR原严重度：low
- 计划：R3-04 / P2
- 原意见：拦截器返回值未做非空校验。DefaultEventBus.invoke 对返回值只做 Class.cast，插件拦截器返回 null 时此处 policy 为 null，第 37 行 policy.evaluate(...) 直接 NPE；同理 preview.preview(...) 返回 null 时第 47 行 sanitizer.sanitize(plan) 或 ToolResult 构造也会 NPE，且异常码无法定位问题。默认管线在 DefaultToolPipeline 第 82-84 行用 Objects.requireNonNull 显式校验。建议对 policy 与 plan 加非空断言并给出明确错误码。
- 判断：DryRunPipeline对policy/plan空返回直接NPE，应在边界明确非空并给安全错误，保持未执行。
- 证据：harness-cli/src/main/java/io/jharness/cli/DryRunPipeline.java:33

## OCR-0285 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/CliConfig.java:100；OCR原严重度：medium
- 计划：R1-06 / P1
- 原意见：`contextTokens`、`maxOutputTokens`、`inputTokenCeiling` 三个值只在各自的方法里独立校验范围，构造期没有交叉约束，导致两类可复现问题： 1) `maxOutputTokens`（上限 32768）可以大于 `contextTokens`（下限仅 1024），此时 `parameters()` 会生成必然超出模型上下文窗口的 `max_tokens`，只会在运行时 `estimateContext` 才失败； 2) `inputTokenCeiling`（`costs()` 中校验）可以小于 `contextTokens`，`ConfiguredTokenCostGuard.maximumCost()` 会按这个偏小的输入上限预留预算，使 `COST_LIMIT` 检查在真实最坏成本下仍可通过，直到请求发出后 `actualCost()` 检测到 usage 超过 ceiling 才抛 `COST_ACCOUNTING_BREACH`——此时费用已经产生。 建议在构造期补充交叉校验：`maxOutputTokens <= contextTokens`，且 `inputTokenCeiling >= contextTokens`。
- 判断：ConfiguredTokenCostGuard.maximumCost直接按inputCeiling预留且只校验model/max_tokens，未按实际request验证输入上界；采纳发送前输入估算与费用上界一致、maxOutput<=context校验。无需一律inputCeiling>=contextTokens，可允许较低ceil但请求必须被限制在其内。
- 证据：harness-cli/src/main/java/io/jharness/cli/CliConfig.java:100；harness-agent/src/main/java/io/jharness/agent/ConfiguredTokenCostGuard.java:maximumCost；harness-agent/src/main/java/io/jharness/agent/DefaultAgentLoop.java:run

## OCR-0286 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/CliConfig.java:266；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：`costs()` 的可用性前置检查漏掉了 `inputTokenCeiling`：紧接着调用的 `integer("inputTokenCeiling", 0, 1, 1000000)` 兜底值为 0，小于允许下限 1，因此当配置了 `maxCost` 和两个单价、但漏配 `inputTokenCeiling` 时，抛出的是 `INVALID_CONFIG`（“Invalid config field inputTokenCeiling”），而不是这里声明的 `COST_BUDGET_UNAVAILABLE`，`WorkbenchSettings.validate()`/CLI 会得到错误的错误码。 另外 `inputPerMillion`/`outputPerMillion` 只在 `costs()` 中懒解析，构造期完全不校验：负值会一直通过配置校验，直到 `new ConfiguredTokenCostGuard(...)` 抛出未包装的 `IllegalArgumentException`（SDK 路径会退化成 `SDK_OPERATION_FAILED`）；价格为 0 时 `maximumCost`/`actualCost` 恒为 0，`maxCost` 预算永远不会触发，形成静默的预算绕过。建议把 `inputTokenCeiling` 纳入前置检查，并在构造期校验单价符号。
- 判断：costs漏inputTokenCeiling前置检查且单价懒验证，采纳构造期精确诊断/非负约束；0价格可表示可信免费模型，不应一律按预算绕过拒绝。
- 证据：harness-cli/src/main/java/io/jharness/cli/CliConfig.java:266

## OCR-0287 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/CliConfig.java:130；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：`compactContext`/`compactionCharacters` 与 `toolScheduling`、`modelResilience` 不同，没有校验对应插件是否启用：当 `enabledPlugins` 不含 `context-compaction` 但配置了 `compactContext: true` 时，构造期校验通过，而 `CliApplication` 注册的 `ContextCompactionPlugin` 会被插件容器按白名单禁用，上下文压缩静默失效、上下文增长无界，配置意图与运行行为不一致。建议补充与 model-resilience 相同的依赖校验。
- 判断：compactContext开启但context-compaction被禁用会意图落空，可与韧性/调度插件依赖一致校验；上下文仍有模型预算检查，不是实际无界发送。
- 证据：harness-cli/src/main/java/io/jharness/cli/CliConfig.java:130

## OCR-0288 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/CliConfig.java:137；OCR原严重度：low
- 计划：R3-01 / P2
- 原意见：`models`/`profiles`/`bundles`/`presets` 只校验顶层条目数不超过 64 和值类型为 Map，嵌套内容完全无界：每个条目的内层 map/list 数量、字符串长度都没有上限。通过 `--config` 加载时还有 `Json.read(path, 65536)` 的 64KB 限制兜底，但 `WorkbenchSettings`/SDK 等以程序化方式构造 `CliConfig` 的路径没有字节上限，超大嵌套 catalog 会直接进入快照并在后续选择逻辑中被遍历，存在无界数据处理风险。建议对每个 catalog 条目递归限制键数/集合大小/字符串长度，或统一按序列化字节数设上限。
- 判断：CliConfig的catalog程序化入口仅条数/Map形状，采纳统一字节/节点上限；CLI文件已有64KB，不能把所有路径说成无界。
- 证据：harness-cli/src/main/java/io/jharness/cli/CliConfig.java:137

## OCR-0289 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/CliConfig.java:295；OCR原严重度：medium
- 计划：R3-04 / P1
- 原意见：`load()` 在路径不存在时静默回落到空配置（即默认 `policy=default`、默认插件集合）。`Files.exists(..., NOFOLLOW_LINKS)` 在文件缺失、父目录无执行权限等 I/O 错误时也会返回 false，而调用方 `CliApplication` 对显式传入的 `--config` 路径不做事后存在性检查，因此一个拼写错误的 `--config /path/typo.json` 会被完全忽略，进程按默认策略继续运行（例如用户以为已配置 `read-only`，实际以可写策略执行），属于校验失败即放行。建议区分“默认配置路径可缺失”和“显式 `--config` 必须存在”两种情况，后者在缺失/不可读时抛出 `INVALID_CONFIG`。
- 判断：CliConfig.load对不存在文件返回空配置，显式--config也可静默默认策略；区分可缺默认文件和必须存在的显式配置。
- 证据：harness-cli/src/main/java/io/jharness/cli/CliConfig.java:295

## OCR-0290 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/CliConfig.java:108；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：`URI.create(...)` 与 `OpenAiCompatibleConfig` 构造器（校验 HTTPS/loopback、model、apiKey 等）抛出的都是 `IllegalArgumentException`，而本类其余校验统一抛 `HarnessException`。触发条件例如 `{"baseUrl":"http://example.com/v1"}`（非 loopback 的 http）或含非法字符的 URL：CLI 顶层只能给出通用错误，`WorkbenchSettings.read()`/SDK 调用方只捕获 `HarnessException` 时会把配置错误降级为 `SDK_OPERATION_FAILED`，丢失 `INVALID_CONFIG` 语义。`modelConfig()` 中同样的构造也需要一并处理，建议捕获 `IllegalArgumentException` 并转换为 `HarnessException("INVALID_CONFIG", ...)`。
- 判断：URI/OpenAiCompatibleConfig可抛IllegalArgumentException未映射INVALID_CONFIG，统一安全配置错误避免通用500/SDK_OPERATION_FAILED。
- 证据：harness-cli/src/main/java/io/jharness/cli/CliConfig.java:108

## OCR-0291 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Main.java:11；OCR原严重度：low
- 计划：无
- 原意见：`System.console()` 被调用了两次：null 判定与实际取 charset 不是同一次观测，在允许替换/注入 Console 的启动环境（如自定义 System 流、部分容器/测试宿主）下第二次调用可能返回 null 而导致 NPE；同时重复查找也不利于阅读。建议先把 console 缓存到局部变量再判断与取值。
- 判断：System.console通常缓存稳定，未给可支持的Console替换机制；局部变量是风格简化，不作为可复现空指针缺陷立项。
- 证据：harness-cli/src/main/java/io/jharness/cli/Main.java:11

## OCR-0292 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Main.java:0；OCR原严重度：medium
- 计划：无
- 原意见：入口把整个进程环境（`System.getenv()`，可能包含云厂商 AK/SK、CI token、代理口令等敏感变量）原样交给 CliApplication，并未在此边界做环境变量白名单过滤（已在 harness-cli 内检索，未见 allowlist 逻辑）。该 Map 会被长期保存在 CliApplication 中，并被下游用于凭据解析、子进程环境拼装（LocalProcessRunner 遍历 entrySet）等；一旦任何环节把它写入会话元数据、日志或透传给被调用的子进程/插件，就会造成凭据外泄。建议在入口按白名单（`JHARNESS_*`、配置中 apiKeyEnv/tokenEnv 声明的变量，以及 PATH/HOME/JAVA_HOME 等运行必需项）过滤后再注入。
- 判断：Main传入环境供可信配置显式选key，不等于对子进程全量继承；LocalProcessRunner有独立环境白名单，原文未查实便断言缺失。
- 证据：harness-cli/src/main/java/io/jharness/cli/Main.java:声明/现有代码

## OCR-0293 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Main.java:14；OCR原严重度：low
- 计划：无
- 原意见：`execute` 只捕获 Exception，Error 等其它 Throwable 会逃逸：此时不会走受控退出码，而是由 JVM 默认未捕获异常处理器把堆栈直接写到原始 `System.err`（绕过了上面按 charset 包装的 err，非 ASCII 消息可能乱码），进程以 1 退出，调用方无法区分“正常失败”与“崩溃”。建议在 main 中显式捕获 Throwable，写入 err 并按约定状态码退出。
- 判断：不应为不可恢复VM Error在main兜底Throwable伪装受控业务失败；现无敏感Error栈泄漏证据。
- 证据：harness-cli/src/main/java/io/jharness/cli/Main.java:14

## OCR-0294 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EcosystemConfig.java:105；OCR原严重度：high
- 计划：无
- 原意见：`tokenEnv` 只校验了变量名的语法，没有做任何白名单限制；随后 `plugins()` 会从调用方传入的 `environment` 中取值（CLI 侧是 `System.getenv()` 全量环境变量，见 Main/CliApplication），并作为 Bearer token 发送到配置里任意 https MCP 端点。因此 `tokenEnv: "AWS_SECRET_ACCESS_KEY"`、`GITHUB_TOKEN` 之类与 MCP 无关的宿主凭据都可以被指名为 MCP 凭据外发，缺少“环境变量白名单”这一层边界。建议在此处增加显式允许集合（例如仅允许 `JHARNESS_` 前缀，或由宿主显式声明的凭据变量名），并在 `plugins()` 取凭据前再次确认名字在集合内；未命中时抛 `INVALID_ECOSYSTEM_CONFIG` 而不是把任意宿主变量交给 HttpMcpTransport。
- 判断：MCP tokenEnv是用户显式可信宿主配置，指明现有凭据变量本身即授权用途；强制JHARNESS前缀会破坏现有合法集成。
- 证据：harness-cli/src/main/java/io/jharness/cli/EcosystemConfig.java:105

## OCR-0295 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EcosystemConfig.java:317；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：子代理完成判据存在两个可复现缺陷： 1) `requireRead` 为空（默认 `List.of()`）时整个表达式恒为 false，子代理永远无法通过完成判据，只能跑满 `maxSteps`/时长预算，且 `SubagentPlugin.Profile` 的默认实现本来就是“completion criteria required”，说明这是唯一的完成信号，配置一个不写 `requireRead` 的子代理等于配置了一个不会自行结束的代理； 2) 证据匹配硬编码为 `"read_file"`，而 `tools` 允许集合里还有 `read_artifact`、`grep`、`search_files`，用这些工具读到路径时无法满足 `requireRead`；再叠加 `validate()` 只校验 `tools` 属于只读集合、不校验 `requireRead` 非空时 `tools` 必须包含 `read_file`，可以合法配置出“永远不可能完成”的子代理（校验阶段不报错，运行期空转到预算耗尽）。另外 `validate()` 也没有拒绝空 `tools` 列表，`SubagentPlugin.Profile` 构造器会抛 `IllegalArgumentException` 而非 `INVALID_ECOSYSTEM_CONFIG`。建议：`requireRead` 为空时直接视为满足；按 `requireRead` 对应工具匹配；并在 `validate()` 做 `tools`/`requireRead` 交叉校验与空列表拒绝。
- 判断：requireRead可为空导致永远无法完成、非空却未包含read_file也矛盾，应配置期拒绝缺完成证据的组合；不能把空requireRead直接视成功，违背证据完成门禁。
- 证据：harness-cli/src/main/java/io/jharness/cli/EcosystemConfig.java:317

## OCR-0296 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EcosystemConfig.java:191；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：`number()` 只保证 ≥0，`scripts.timeoutSeconds: 0` 能通过校验并构造出 `Duration.ZERO`。下游 `SkillsPlugin.Script` 内部调用 `McpIo.bounded()` 会以 `IllegalArgumentException("MCP timeout must be between zero and 60 seconds")` 拒绝零时长，于是非法配置被当成宿主运行时异常抛出，而不是统一的 `INVALID_ECOSYSTEM_CONFIG`（该异常信息本身也是错的，实际拒绝的是零值）。同文件其他超时字段（MCP `timeoutSeconds`、子代理 `durationSeconds`、`approvalSeconds`）都显式做了 `< 1` 校验，此处应保持一致：`timeoutSeconds` 也必须 ≥1。
- 判断：scripts timeoutSeconds:number允许0而McpIo拒绝零，配置验证应提前统一1..60范围及错误码。
- 证据：harness-cli/src/main/java/io/jharness/cli/EcosystemConfig.java:191

## OCR-0297 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EcosystemConfig.java:109；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：校验器里多处直接强转/解析而未先做类型与语法校验，畸形配置会抛出非预期异常而非 `invalid()`：此处 `(String) v` + `SideEffectLevel.valueOf` 会抛 `ClassCastException`/`IllegalArgumentException`；`plugins()` 中同一段逻辑重复强转；`spec()` 中 `resources` 的 `(String) v`、`CommandRequest.Mode.valueOf`、`new ExecPath(...)` 同样如此；`webSources` 的 `URI.create((String) e.getValue())` 会抛 `IllegalArgumentException`；`skillRoots`/`workerJar` 的 `Path.of(...)` 会抛 `InvalidPathException`；`Values.object()` 抛的是 `INVALID_JSON`、`SkillsPlugin.validatePaths()` 抛的是 `SKILL_PATH_DENIED`、`SkillCatalog.checkName()` 抛的是 `SKILL_COMMAND_CONFLICT`。结果是同一类“配置写错”可能以五花八门的异常码冒出，配置错误被上报为宿主崩溃。建议先做 `instanceof String/Map` 与枚举合法值校验（或把转换包在 try/catch 中统一转成 `invalid()`）。
- 判断：effects/URI/Mode/Path等直接转换抛不同运行时异常，按字段严格类型/枚举校验并统一INVALID_ECOSYSTEM_CONFIG安全错误。
- 证据：harness-cli/src/main/java/io/jharness/cli/EcosystemConfig.java:109

## OCR-0298 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EcosystemConfig.java:258；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：stdio MCP 的 `cwd` 在 `validate()` 中完全没有约束（`keys()` 只允许该键存在），这里直接 `root.resolve(...)`：绝对路径会被 `resolve` 原样返回，`"../.."` 也会逃逸工作区，最终 `StdioMcpTransport` 会在工作区之外的目录启动外部进程，与该文件对 `skillRoots` 施加的“必须相对、已归一化、不得含 `..`”边界不一致。建议在 `validate()` 中按 project 根同样规则校验 `cwd`（相对、`normalize()` 后不变、不含 `..`），并在使用前再做一次归一化与包含性检查。
- 判断：MCP cwd直接root.resolve可绝对/父级，采纳明确宿主可信cwd边界和路径检查；MCP进程本是宿主信任执行，不宣称这是插件沙箱逃逸。
- 证据：harness-cli/src/main/java/io/jharness/cli/EcosystemConfig.java:258

## OCR-0299 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EcosystemConfig.java:394；OCR原严重度：medium
- 计划：R1-06 / P2
- 原意见：`ParameterizedModel` 只在 `prepare()` 里合并 CLI 参数（并强制 `stream=false`、移除 `stream_options`），而 `complete()`/`stream()` 把原始 request 原样透传给 delegate：任何不经 `prepare()` 的调用路径都会丢掉参数覆盖与非流式约束，与主模型行为不一致。更关键的是没有覆写 `completeSingleAttempt()`，因此当 `MODEL_EXECUTION` 以 `singleSend=true` 调用时（`BoundedModelExecution` 在 modelResilience 启用时即走 `attempt.complete(true, …)` → `llm.completeSingleAttempt(...)`），`LlmClient` 的默认实现会直接抛 `SINGLE_ATTEMPT_UNSUPPORTED`，子代理模型调用在启用模型韧性/单次发送契约时必然失败。建议在 `complete()`/`stream()`/`completeSingleAttempt()` 中都先合并同一份有界参数（`stream` 由调用场景决定）再委托，并同步覆写 `completeSingleAttempt()`。
- 判断：ParameterizedModel未透传completeSingleAttempt确实会走默认拒绝，补准备/单次适配契约；须先确认子SDK韧性配置独立默认关闭，不称所有子任务必然失败。
- 证据：harness-cli/src/main/java/io/jharness/cli/EcosystemConfig.java:394

## OCR-0300 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EcosystemConfig.java:204；OCR原严重度：low
- 计划：无
- 原意见：这个 3 参数 `environment(...)` 重载在仓库内没有任何调用点（唯一调用方 `EcosystemCli.open` 使用的是带 `WorkspaceSearchFilter` 的 4 参数版本，测试中也无引用），属于不会被执行的死代码。建议删除，避免两个入口后续漂移（例如默认过滤策略变更时只改一处）。
- 判断：包内便利重载无调用可作清理，但不是正确性缺陷，删除会增加与本次风险无关改动，暂不纳修复阶段。
- 证据：harness-cli/src/main/java/io/jharness/cli/EcosystemConfig.java:204

## OCR-0301 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Json.java:40；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：第 40 行：限流参数未做边界校验。当 limit == Integer.MAX_VALUE 时 `limit + 1` 溢出为负数，`readNBytes(int)` 直接抛 IllegalArgumentException（绕过 HarnessException 的错误码协议）；limit 为 0/负数同样抛 IllegalArgumentException；limit 为巨大正值时还会按 `limit+1` 一次性预分配导致 OOM。当前调用方虽都传字面量（32768/65536/1MB），但该方法是共享的读入口，应显式拒绝非法上限。
- 判断：Json.read共享limit+1确有溢出风险，当前固定限额安全，采纳参数范围校验及极值负例作为边界加固。
- 证据：harness-cli/src/main/java/io/jharness/cli/Json.java:40

## OCR-0302 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Json.java:37；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：第 37-39 行：`isRegularFile(...)` 与下方 `Files.newInputStream(...)` 构成 check-then-act（TOCTOU）。且 `NOFOLLOW_LINKS` 只对路径最后一级生效，父目录若是符号链接（例如 `--config`/plan 指向攻击者可写的共享目录中的软链）仍会被跟随，从而读到预期受限目录之外的文件；检查与打开之间的路径替换同理。触发条件：配置/计划路径位于他人可写目录且中间目录不可信。建议在打开后基于已解析的真实路径（如 `path.toRealPath()` 限定在允许根内）或 SecureDirectoryStream/打开句柄属性做校验，而不是仅依赖路径预检。
- 判断：Json.read预检最终NOFOLLOW后普通打开有TOCTOU，需复核不可信父路径/句柄策略；显式--config允许用户选择根外文件，不加任意工作区限制。
- 证据：harness-cli/src/main/java/io/jharness/cli/Json.java:37

## OCR-0303 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Json.java:43；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：第 43 行：`new String(bytes, UTF_8)` 采用替换式解码，非法/不完整的 UTF-8 字节序列会被静默替换为 U+FFFD 后继续解析，导致非法编码的配置被接受、字段值被静默篡改（写入后再读回内容与原始字节不一致）。建议使用 `onMalformedInput(REPORT)` 的严格解码器并映射为 HarnessException。
- 判断：new String(bytes,UTF8)替换非法编码后仍可解析配置，改严格Decoder并返回无原文安全错误。
- 证据：harness-cli/src/main/java/io/jharness/cli/Json.java:43

## OCR-0304 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Json.java:30；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：第 31-32 行：捕获的 IOException 被整体丢弃（`text()` 第 50-51 行同样如此），无法区分失败原因是语法错误、重复键、嵌套超限还是字符串超限，排障与错误码细化都失去依据。建议至少保留异常类型/位置信息（注意：Jackson 的解析异常消息可能带原始 token 片段，若输入可能含敏感值，应只保留异常类型/行列号等不含内容的字段，或映射为更细的错误码），`HarnessException` 已提供带 cause 的构造器。
- 判断：Json.parse丢弃IOException减少定位，可保留异常类别/位置；不得直接附Jackson原始message泄漏输入。
- 证据：harness-cli/src/main/java/io/jharness/cli/Json.java:30

## OCR-0305 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Json.java:71；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：第 71-76 行：临时文件以进程 umask 决定权限创建（POSIX 上通常为 0644）。随后 `ATOMIC_MOVE + REPLACE_EXISTING` 是用新 inode 替换目标，已有目标文件的 0600 等收紧权限会被静默放宽；当该路径用于落盘配置/报告等含本地路径或提示内容的文件时，属于权限回归。建议创建时显式设置 `rw-------`（POSIX 文件系统）并保留/校验目标原有属主与权限。
- 判断：Json.write新临时inode再替换可能丢失旧POSIX权限，采纳私有元数据权限保留；明确报告导出与秘密存储范围，不称所有配置含密钥。
- 证据：harness-cli/src/main/java/io/jharness/cli/Json.java:71

## OCR-0306 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Json.java:81；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：第 81-85 行：只对临时文件调用了 `channel.force(true)`，重命名后没有对父目录做 fsync。掉电/崩溃时该 rename 可能未持久化，目标仍是旧内容或旧版本（在部分文件系统上则是文件丢失），与“原子持久化”的语义不符，会破坏崩溃恢复后的状态一致性。建议在 move 成功后打开父目录（`FileChannel.open(path.getParent(), READ)`）并 `force(true)`（非 POSIX/不支持目录句柄的平台捕获异常降级）。
- 判断：rename后未父目录force可影响掉电耐久，采纳按持久承诺区分支持/不支持平台；不能catch后默默降级却仍宣称durable。
- 证据：harness-cli/src/main/java/io/jharness/cli/Json.java:81

## OCR-0307 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/CliApplication.java:1286；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：`unsettled()` 会对每个 tool_call 直接 `(String) id` 并加入集合：当会话日志被导入/损坏导致某个 `tool_calls` 条目缺少 `id`（或 `id` 非字符串）时，`(String) null` 会以 null 元素进入 `pending`，紧接着 `List.copyOf(pending)` 抛 NPE（非字符串则抛 ClassCastException）。该路径正是 `verify` / `resume` / `sessions recover` 用来判断“待核实调用”的关键逻辑，异常会让恢复流程以通用 CLI 错误退出（exit 5），无法输出可核实状态，也无法提示 `VERIFICATION_NOT_REQUIRED`/`CALL_NOT_FOUND` 之类的语义错误。建议跳过空/非字符串 id，或将其作为 `INVALID_SESSION_EVENT` 明确上报。
- 判断：unsettled直接强转id且List.copyOf拒绝null，应对损坏事件显式INVALID_SESSION_EVENT并阻塞；不能跳过非法id把不确定副作用当已清除。
- 证据：harness-cli/src/main/java/io/jharness/cli/CliApplication.java:1286

## OCR-0308 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/CliApplication.java:1508；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：`close()` 的 `finally` 中三个 close 是顺序调用的：若 `approvals.close()`（或 `container.close()`）抛异常，`process.close()` 与 `model.close()` 都会被跳过，导致 `LocalProcessRunner` 持有的子进程句柄/线程池和模型客户端连接泄漏；在 REPL 中会跨命令累计泄漏。请把每个 close 放进独立的 try/catch（或嵌套 try/finally）并聚合首个异常后抛出。
- 判断：Runtime.close finally中approvals/process/model顺序关闭，前者抛错会跳后者，应独立关闭聚合错误且不覆盖主失败。
- 证据：harness-cli/src/main/java/io/jharness/cli/CliApplication.java:1508

## OCR-0309 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/CliApplication.java:1035；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：关机钩子在 `task.get(6, TimeUnit.SECONDS)` 抛出 `TimeoutException`/`InterruptedException`/`ExecutionException` 时会直接进入 catch，`terminal.stopAll()` 被跳过。此时 JVM 正在退出、agent 任务仍未结束，终端子进程（长驻 shell/构建）无人回收，成为孤儿进程并在工作区继续运行；退出日志也无法说明这一事实。请把 `terminal.stopAll()` 放入 `finally`，保证无论等待是否超时都清理终端任务。
- 判断：关机hook task.get超时/异常直接跳过terminal.stopAll，需finally尽力回收并保留真实未知状态。
- 证据：harness-cli/src/main/java/io/jharness/cli/CliApplication.java:1035

## OCR-0310 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/CliApplication.java:1059；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：`run()` 内多处重复调用 `reader.events()`（本行、1069、1096、1103、1108 行等，再加前面 archival/empty/recovery 检查至少 8 次），而 `JsonlSessionRepository` 的 reader 每次 `events()` 都会重新读取并解析整个 JSONL（上限 32 MiB），复杂度为每次 O(n)。运行结束时日志已接近上限的场景下，一次 run 会重复解析近十遍，既拖慢收尾也增加不一致窗口（某些调用看到的是 append 之后的快照，另一些是之前的）。建议在关键节点取一次快照变量复用，仅在写入后显式刷新。`report` 命令（第 80-81 行）对同一 session 也 `openReader().events()` 了两次，同样应改为先取 `history` 再传入 `lastGoal()` 与 `EvidenceReport.build()`。
- 判断：运行收尾多次reader.events完整读JSONL，可在无新增写入节点缓存一致快照，写入后显式刷新。
- 证据：harness-cli/src/main/java/io/jharness/cli/CliApplication.java:1059

## OCR-0311 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/CliApplication.java:211；OCR原严重度：medium
- 计划：R1-04 / P1
- 原意见：环境变量敏感值收集只识别 `_API_KEY|_TOKEN|_SECRET|_PASSWORD` 四种后缀，像 `AWS_SECRET_ACCESS_KEY`、`AWS_ACCESS_KEY_ID`、`*_PRIVATE_KEY`、`*_ACCESS_KEY`、`*_CREDENTIALS`、`*_PASS` 这类常见凭据变量的值不会被注册到 `SecretRedactor`。一旦这些值出现在工具输出/命令回显中，就会原样写入 session JSONL 与报告（本次审查目标明确要求敏感信息脱敏）。建议扩展后缀集合（并注意 `AWS_SECRET_ACCESS_KEY` 结尾是 `_KEY`，需显式列出）。
- 判断：secrets仅按_API_KEY/_TOKEN/_SECRET/_PASSWORD后缀收集，漏AWS_SECRET_ACCESS_KEY等已知凭据名称；扩展明确别名并对齐所有装配入口。
- 证据：harness-cli/src/main/java/io/jharness/cli/CliApplication.java:211

## OCR-0312 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EcosystemCli.java:265；OCR原严重度：high
- 计划：R1-02 / P1
- 原意见：取消/资源释放顺序缺陷（第 265-273 行）。`CliApplication.interact(...)` 或 `task.get()` 抛异常（Ctrl-C 打断、stdin 读取失败、交互层内部异常）时，finally 只移除 shutdown hook，从不调用 `sdk.cancel()`/`task.cancel(...)`。由于 try-with-resources 的关闭顺序与声明顺序相反，`worker`（第 258 行声明）会先于 `sdk`/`control` 关闭，`ExecutorService.close()` 会等待正在运行的 `sdk.run(...)` 结束——取消信号没有发出，CLI 会一直挂到整个 run 预算耗尽，`CancellationToken` 也没被触发，无法做到"失败即中止"。建议在异常路径先 cancel 再抛出。
- 判断：EcosystemCli worker最后声明先close，interact异常未cancel可等待整轮预算，异常路径先发送取消再有界收尾。
- 证据：harness-cli/src/main/java/io/jharness/cli/EcosystemCli.java:265

## OCR-0313 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EcosystemCli.java:268；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：`task.get()` 把领域异常包进 `ExecutionException`（第 268 行）：一旦 `sdk.run()` 抛出 `HarnessException`（provider 4xx、预算/限额、工具异常等），包装异常会从 `run()` 直接向上抛，导致第 275 行之后的证据报告（第 278-281 行）与 `delivery.registered` 事件都不再写入；而 `CliApplication.execute` 顶层只识别 `HarnessException`，最终统一退化成 exit 5 + `CLI_IO_OR_CONFIG_ERROR`，丢失 `UNKNOWN_SIDE_EFFECT`/`TAIL_DAMAGED` 等 exit 6 语义以及原有错误码。中断场景同理（`InterruptedException` 未恢复中断标志）。建议解包 cause 后再抛。
- 判断：task.get的ExecutionException未解包导致顶层通用exit5，采纳保留领域code及中断标志；报告落盘需按原始状态真实生成，不能在任何异常后假装已结算。
- 证据：harness-cli/src/main/java/io/jharness/cli/EcosystemCli.java:268

## OCR-0314 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EcosystemCli.java:278；OCR原严重度：medium
- 计划：无
- 原意见：报告落盘路径未在本地校验 `id`（第 278-281 行）。`--session` 值直接 `resolve` 到 `.jharness/reports` 下，目前只因 `JsonlSessionRepository` 的 id 正则（openReader/openWriter 会先抛错）而被间接挡住；一旦该校验放宽、顺序调整，或出现新的 id 来源，`../`、绝对路径 id 会让报告写到工作区之外（同时 `workspace.relativize(path)` 会抛 `IllegalArgumentException`）。建议在此处按同一规则本地校验或用 normalize+startsWith 断言。
- 判断：报告id在更早openReader/openWriter的严格session正则已拒绝，当前路径不可达；不能只因假设未来移除校验重复报安全缺陷。
- 证据：harness-cli/src/main/java/io/jharness/cli/EcosystemCli.java:278

## OCR-0315 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EcosystemCli.java:76；OCR原严重度：medium
- 计划：R1-04 / P1
- 原意见：字面量密钥闸门失效（第 42-79 行）：`secrets` 只收集"匹配到的环境变量值"，`SecretRedactor` 仅做已知字面量替换 + 固定键名替换（不含 `apiKeyEnv`/`tokenEnv`/`baseUrl`/`prompts` 等键）。因此当配置里直接写了凭据（如 `baseUrl` 带 userinfo、prompts/模型目录里内联的 apiKey、`sk-`/`ghp_` 前缀字面量）而环境变量中不存在该值时，第 76 行的等式恒成立，`SECRET_CONFIG` 永不触发；随后 `open()` 的 `config.selected` 事件会把 `sanitizer.sanitize(config.values)` 原样写入会话 JSONL，报告也会带上该值。建议除环境值回填外，对配置做键名/URI userinfo/凭据前缀模式检测，或把这些字段限制为环境变量引用。
- 判断：仅已知环境字面量不能识别userinfo/内联未登记秘密，采纳禁止凭据URI与已知敏感配置键；不能用sk前缀猜测替代凭据引用契约。
- 证据：harness-cli/src/main/java/io/jharness/cli/EcosystemCli.java:76

## OCR-0316 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EcosystemCli.java:244；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：REPL 循环缺陷（第 240-247 行）：`TerminalInput.poll` 在 100ms 超时无输入时返回 null，而 prompt 在每次循环都打印，因此空闲状态下每 100ms 打印一次 `jharness> `（管道/重定向场景会刷出大量无用输出）。`CliApplication` 的同类循环用了内层 "等待直到有输入或 EOF" 的写法，此处缺失。另外 `last == 5 || last == 6`（配置/恢复类致命态）未 break，恢复屏障失败后 REPL 仍继续接受输入，后续每轮都会被 SDK 构造期的 UNKNOWN 检查拒绝，体验与语义都不一致；`:cancel` 在空闲时也会被当成 goal 送给模型。
- 判断：EcosystemCli每100ms poll空返回重新打印prompt确实刷屏，应单次提示等待输入；空闲:cancel等指令需明确本地处理。
- 证据：harness-cli/src/main/java/io/jharness/cli/EcosystemCli.java:244

## OCR-0317 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EcosystemCli.java:73；OCR原严重度：low
- 计划：无
- 原意见：收集到的秘密值没有最小长度/去重约束（第 73-75 行）：任何以 `_TOKEN`/`_SECRET`/`_PASSWORD` 结尾的环境变量（例如 CI 中的 `SOMETHING_TOKEN=1`、`X_PASSWORD=on`）都会进入 `secrets`，而 `SecretRedactor.text` 是全局子串 `replace`，会把会话日志、`config.selected.resolved` 与证据报告里所有出现的 `1`/`on` 替换成 `[REDACTED]`，造成审计记录与报告内容损坏（`streamingText` 的 hold 长度也会随之异常）。建议加最小长度/字符集过滤后再纳入字面量替换。
- 判断：已注册凭据即使很短也不应以最小长度过滤漏脱敏；可去重/限制配置，但不能因为值为1就在报告明文保留秘密。
- 证据：harness-cli/src/main/java/io/jharness/cli/EcosystemCli.java:73

## OCR-0318 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Metadata.java:19；OCR原严重度：high
- 计划：R2-01 / P3
- 原意见：路径逃逸（第19-22行）：`name` 未做任何约束，`root.resolve(name)` 会接受绝对路径（如 `/etc/cron.d`）或包含 `..` 的路径（如 `../../tmp/x`）；而 `check(path)` 只是把 `path.toRealPath()` 与 `path.toAbsolutePath().normalize()` 比较，两者在穿越后依然相等，因此校验必然通过，`directory()` 会创建并返回 `.jharness` 之外的目录。触发条件：任何把用户/插件/配置来源字符串传入 `directory()` 的调用（当前仓库调用点虽为常量，但该类是可复用的包内工厂，且 `name` 完全来自入参），例如 `metadata.directory("../../outside")` 会成功创建并返回工作区外目录。建议先校验 `name` 必须是单个本地路径段（拒绝绝对路径、`.`、`..`），再 resolve 并确认结果仍位于 root 之下（注意 `directory("")` 被 CliApplication:254 用于创建 `.jharness` 本身，需兼容空串）。
- 判断：Metadata.directory未约束name但现调用均常量且空串用于根，采纳内部路径段断言保留空串；非现行任意路径写入入口。
- 证据：harness-cli/src/main/java/io/jharness/cli/Metadata.java:19

## OCR-0319 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Metadata.java:16；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：TOCTOU 与符号链接祖先目录（第15-16行）：`check(root)` 与 `Files.createDirectories(root)` 之间存在竞态窗口，另一个进程可在二者之间把 `root` 替换为指向工作区外的符号链接，`createDirectories` 会跟随该链接在目标位置创建目录，事后第二次 `check(root)` 只能发现越界、无法回滚已创建的目录。另外若 `workspace` 自身包含符号链接祖先（未规范化构造 `Metadata` 时），`createDirectories` 会沿链接写到其真实位置，随后第17行的 `check` 才抛异常。建议在构造器中固定 `workspace = workspace.toRealPath()`，并对每一级组件使用 `Files.createDirectory`（NOFOLLOW 语义下逐级校验）或在创建失败/校验失败时清理已创建的目录。
- 判断：Metadata.directory先NOFOLLOW check再按路径createDirectories、后check，检查与创建非原子属事实；采纳受控目录替换回归/句柄或归属绑定加固，仅静态确认窗口，未声称真实主机已被越界。
- 证据：harness-cli/src/main/java/io/jharness/cli/Metadata.java:16；harness-cli/src/main/java/io/jharness/cli/Metadata.java:directory

## OCR-0320 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Metadata.java:27；OCR原严重度：medium
- 计划：无
- 原意见：未校验 config.json 本身（第26-28行）：`config()` 只检查 `.jharness` 目录，未用 NOFOLLOW 检查 `config.json`。若 `.jharness/config.json` 是指向工作区外文件（如 `~/.ssh/authorized_keys`、`~/.aws/credentials`）的符号链接，`CliConfig.load(configPath)` 会读取任意文件；配合 `configure()`（CliApplication:255）虽然 `Json.write` 会拒绝 symlink 最终路径，但其它以 `Files.write`/`Files.newOutputStream` 写入该路径的调用会跟随链接覆盖任意文件，构成文件系统边界突破。建议在返回前用 `Files.readAttributes(..., NOFOLLOW_LINKS)` 校验 `config.json` 为非符号链接的普通文件，并确认其父目录真实路径等于 `root.toRealPath()`。
- 判断：Metadata.config返回Path但实际CliConfig.load经Json.read先NOFOLLOW普通文件检查，所述跟随最终config symlink读取链不成立；假想未来Files.write调用不是现有缺陷。
- 证据：harness-cli/src/main/java/io/jharness/cli/Metadata.java:27

## OCR-0321 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Metadata.java:39；OCR原严重度：medium
- 计划：R2-01 / P3
- 原意见：死代码 + 规范化比较误判（第36-39行）：`BasicFileAttributes` 是在 `NOFOLLOW_LINKS` 下读取的，符号链接/FIFO/设备的 `isDirectory()` 恒为 false，因此 `attrs.isSymbolicLink()` 与 `attrs.isOther()` 因 `||` 短路永远不会被求值，是死分支（安全语义仍由第一个条件兜住，但代码意图与实际行为不一致）。同时 `toRealPath()` 会解析符号链接并做大小写规范化，而 `toAbsolutePath().normalize()` 不会：当 `workspace` 经符号链接（如 macOS 的 `/tmp` → `/private/tmp`）或大小写变体路径构造 `Metadata` 时，合法工作区会被误判为越界并抛 `METADATA_PATH_DENIED`。建议拆成显式校验：先判定 `isSymbolicLink() || isOther() || !isDirectory()`，再与统一规范化后的基准路径（构造器中缓存 `root` 的父目录真实路径）比较包含关系，而不是把路径与自身比较。
- 判断：check以realPath等于normalized路径有意拒绝链接/别名，采纳构造处规范化和平台别名契约说明；不是仅因条件冗余就安全失效。
- 证据：harness-cli/src/main/java/io/jharness/cli/Metadata.java:39

## OCR-0322 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/Metadata.java:31；OCR原严重度：low
- 计划：无
- 原意见：`workspace()` 可能返回 null 或相对路径（第31-32行）：构造器未校验 `workspace`，若传入 `Path.of("")`（CLI `--workspace` 缺省链路上可能出现空值/空串），`root` 为相对的 `.jharness`，`root.getParent()` 返回 null；若传入相对路径（如 `Path.of("ws")`），返回的是未解析的相对路径。调用方 `CliApplication:425`、`WorkbenchSettings:51`、`WorkbenchWorkspaces:56/63` 直接对其调用 `toString()`/`getFileName()`/`Files.getOwner()`，会分别导致 NPE 或把相对路径写入配置（后续解析到错误目录）。建议构造器中做 `workspace.toAbsolutePath().normalize()`（或 `toRealPath()`）并拒绝空路径，`workspace()` 返回非空绝对路径。
- 判断：实际CLI工作区入口已toRealPath/绝对化，传Path.of空是假想包内误用；未找到合法启动产生null workspace的链。
- 证据：harness-cli/src/main/java/io/jharness/cli/Metadata.java:31

## OCR-0323 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/TerminalInput.java:26；OCR原严重度：medium
- 计划：无
- 原意见：同一表达式里两次调用 System.console()：第 27 行判定非 null，第 29 行再次调用取 charset。System.console() 在重定向/包装场景下并非稳定返回同一实例，两行之间若返回值变为 null 会在字符集选择处抛 NPE（在 reader 线程中抛出未被捕获的 RuntimeException，直接杀死读取线程，且不会走到 finally 之外的任何上报）。此外该分支只在 `input == System.in` 时生效，若调用方传入包装后的 System.in（如 new BufferedInputStream(System.in)），Windows 控制台的本地字符集（非 UTF-8）输入会被按 UTF-8 解码成乱码。建议只取一次 Console 引用并判空，同时按流来源决定字符集。
- 判断：重复System.console不存在已支持动态替换证据，包装流UTF8是既定非控制台输入契约；不作为运行缺陷。
- 证据：harness-cli/src/main/java/io/jharness/cli/TerminalInput.java:26

## OCR-0324 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/TerminalInput.java:69；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：close() 只置位 closed 并调用 Thread.interrupt()，但 Thread.interrupt() 无法中断普通 InputStream.read() 的阻塞（System.in、管道、SocketInputStream 等均不响应中断）。触发条件：CLI 结束后 try-with-resources 关闭 TerminalInput 时，reader 线程正阻塞在 buffered.read()（第 33 行）上，此调用会一直挂住。后果：1) reader 作为 daemon 线程泄漏，且始终持有 stdin；2) 底层的 input 流从未被关闭（无 buffered.close()），资源生命周期不完整；3) close() 没有把 ended 置为 true，若调用方在 close 之后仍调用 eof()（第 57 行），会永远返回 false，配合 poll(timeout) 形成无限空转，例如 CliApplication#interact 中 `line == null && input.eof()` 的兜底拒绝分支将永远不触发。建议：在 close() 中同时 ended=true，并关闭底层流（对 System.in 之外的流至少 close 包装后的 reader），或改用可中断的读取方式（NIO channel / 可关闭的管道）。
- 判断：close仅interrupt不能中断普通stdin读取，采纳置ended和显式输入所有权、可关闭注入流清理；不能直接关闭共享System.in破坏REPL。
- 证据：harness-cli/src/main/java/io/jharness/cli/TerminalInput.java:69

## OCR-0325 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/TerminalInput.java:38；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：超长行直接 throw new IOException("Input bound")，随后被第 44 行的 catch 静默吞掉，finally 把 ended 置为 true：reader 线程彻底终止，此后所有标准输入被永久忽略（eof() 变真，REPL 退出 / 审批走默认拒绝）。触发条件：粘贴大段文本或恶意输入单行 >= 1 MiB（例如把文件内容无换行地重定向到 stdin）。这属于一次坏输入导致整个会话输入不可恢复的拒绝服务，而不是跳过该行或向调用方报错。另外此处按 Java char 计数而非字节：UTF-8 多字节字符下单行实际可占用至约 4 MiB 内存，与“1 MiB 上界”的意图不符。建议：丢弃该行并继续读取（或记录可观测的错误状态供 eof()/poll() 上报），并在超限时清空 line 缓冲。
- 判断：Input bound IOException被当EOF无诊断，采纳显式可观察输入超限状态；有界fail closed终止不必恢复同一坏输入流。
- 证据：harness-cli/src/main/java/io/jharness/cli/TerminalInput.java:38

## OCR-0326 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/TerminalInput.java:43；OCR原严重度：low
- 计划：R0-03 / P3
- 原意见：EOF 时残留的未换行行直接入队，没有像第 35 行那样去掉尾部 '\r'。触发条件：输入以 '\r' 结尾且文件/管道末尾没有 '\n'（Windows 剪贴板粘贴、部分管道写入），末行会变成 ":cancel\r" 或 "y\r" 之外的脏值；而主循环/审批分支用的是 `line.trim().equals(":cancel")` 之外的精确或 trim 比较，行为与正常换行结尾的输入不一致。建议与换行分支统一做 CR 剥离。
- 判断：EOF尾行未去CR与换行路径不齐，可统一处理并测试；主要命令trim已容忍，不能声称现行所有取消失效。
- 证据：harness-cli/src/main/java/io/jharness/cli/TerminalInput.java:43

## OCR-0327 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/TerminalInput.java:60；OCR原严重度：low
- 计划：无
- 原意见：takeCancel() 先用 peek() 判断队首再单独 poll()，这是典型的 check-then-act：队列操作的原子性被拆成两步。当前在 CliApplication#interact 中是单消费线程，尚不会出错；但一旦后续有第二个线程消费同一 TerminalInput（例如异步审批或 REPL 主循环与审批循环并行），另一消费者可能在 peek 与 poll 之间取走 ':cancel'，导致本方法返回 true（误判为已取消）却实际消费掉一条普通输入行，造成输入静默丢失或取消丢失。建议用单个 poll() 取值后判断、不是 ':cancel' 时再用 offer/addFirst 放回（或改用 take() 语义的原子方法），并在类注释中显式约束“仅允许单消费线程”。
- 判断：TerminalInput当前单消费线程，peek/poll跨消费者问题无真实调用；改为poll再放回反而可能重排，最多文档说明不立修复。
- 证据：harness-cli/src/main/java/io/jharness/cli/TerminalInput.java:60

## OCR-0328 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java:328；OCR原严重度：critical
- 计划：无
- 原意见：`secret_password_clear_sync` 的签名是 `(schema, collection, cancellable, error, ...attrs)`（与上面 `secret_password_store_sync` 一致，后者已传 "default"），但这里只传了 6 个参数并漏掉 `collection`，导致整体错位：`error` 落到 `cancellable` 位置、字符串字面量 `"target"` 被当作 `GError**`、`target` 变成第一个可变参数（后面跟 `null` 而非属性值）。删除失败时 libsecret 会向只读字符串内存写入 `GError*`，造成堆破坏/崩溃；同时 `check(error)` 永远看不到真正的错误，删除失败会被静默当成成功（违反『删除成功才可认为凭据已移除』的恢复语义）。建议与 store 分支保持一致补上 collection 参数。
- 判断：GNOME官方clear_sync签名是(schema,cancellable,error,...)，没有collection；现{schema,null,error,target,targetValue,null}参数顺序正确，OCR把store签名误套clear。
- 证据：harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java:328；https://gnome.pages.gitlab.gnome.org/libsecret/func.password_clear_sync.html:Declaration；harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java:328

## OCR-0329 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java:38；OCR原严重度：high
- 计划：R1-04 / P1
- 原意见：写路径只限制 1024 个**字符**（`value.length()`），而三条读路径都按原生**字节数** ≤1024 校验（Windows `credential.size`、Keychain `length.getValue()`、libsecret 的 strlen）。1024 个非 ASCII 字符经 UTF-8 编码约 2–4KB，写入会成功，但随后 read 会在长度校验处抛异常并以退出码 7 结束，调用方得到『Credential store operation failed』，而 vault 中已持久化了一份永远读不出来的凭据，形成写入后不可读的不确定状态。应在写入前按 `value.getBytes(UTF_8).length <= 1024` 校验（各平台 `bytes.length` 与读侧上限对齐）。
- 判断：原生入口限制value.length<=1024字符而读取按UTF8字节<=1024，非ASCII密钥可写后读失败，应写前按字节一致校验。
- 证据：harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java:38

## OCR-0330 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java:40；OCR原严重度：medium
- 计划：无
- 原意见：循环内 `result` 每轮被覆盖，最多 32 个操作但只返回最后一个的结果。`SystemCredentials.update()` 丢弃返回值时无影响，但任何包含多个 `read`/`probe` 的批量请求都会静默丢失前面的读取结果（例如一次读取多个 target 时把最后一个凭据当成全部结果返回，属于凭据张冠李戴）。建议限制 read/probe 只能单独成批，或返回与 operations 等长的结果数组。
- 判断：原生worker是内部批次协议，SystemCredentials.update批次只有write/delete且丢弃返回，read/probe单独调用；未存在混合多read被误认全部结果路径。
- 证据：harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java:40

## OCR-0331 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java:16；OCR原严重度：medium
- 计划：R1-04 / P3
- 原意见：stdin 读到的 `input` 是完整明文 JSON（含所有凭据值），构造 String 后没有清零，与 write 路径对 `bytes`/`Memory` 主动 `Arrays.fill`/`clear()` 的策略不一致；进程内存转储或 core dump 可完整还原明文。建议在 `Json.parse` 之后立即 `Arrays.fill(input, (byte) 0)`（整个 main 用 `finally` 包裹，确保异常退出路径也清零）。
- 判断：stdin byte[]可finally清零缩短额外明文驻留；String/JSON值仍不可擦除，不能把byte清零写成进程内存无秘密保证。
- 证据：harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java:16

## OCR-0332 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java:110；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：Windows 分支的 probe 在未做任何原生调用前直接返回 `available`，只代表 Advapi32 能加载；当凭据服务被组策略/服务异常禁用时仍报告可用，会让调用方误判可降级并继续写入。建议至少执行一次 `CredReadW(target)` 并区分 1168（不存在，视为可用）与其他错误（不可用）。
- 判断：Windows Native probe只返回available没有实际API探测，应给出能力含义并验证可用性；不存在条目应视可用，避免写入探针污染vault。
- 证据：harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java:110

## OCR-0333 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java:147；OCR原严重度：low
- 计划：R1-04 / P3
- 原意见：读路径只校验 size 范围，未校验 `blob` 指针非空；`size > 0` 而 `blob == null` 时会 NPE，被顶层 catch 转成退出码 7，把本可恢复的情况误报为『凭据存储不可用』。Keychain 分支同理（`length.getValue() == 0` 或未返回数据时 `data.getValue()` 可能为 null 后直接 `getByteArray`）。建议先判空指针并返回空串（与 `size == 0` 的处理保持一致）。
- 判断：原生blob/data非空与size关联可以防御检查，但不应把损坏原生响应称本可恢复；保持明确存储不可用fail closed。
- 证据：harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java:147

## OCR-0334 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/CredentialStoreProbe.java:37；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：超时归类依赖对 IOException 消息的精确等值匹配（`"Credential store timeout"`）。当前 SystemCredentials.run 直接抛出该消息所以能命中，但任何一层包装（例如以后在 read/write 外层加 try/catch 重新包装异常）、消息改写或本地化，都会让真实超时退化为 `PROBE_FAILED`，从而改变上层 CI/harness 对可重试失败的判定；同时 `supported()` 返回 false（平台不支持）也统一落到 `PROBE_FAILED`，无法与真正的凭据存储故障区分。建议让 SystemCredentials 暴露结构化错误码（或自定义异常子类），探针按类型码而非消息文本归类，至少用 `contains("timeout")` 之类的宽松匹配兜底。
- 判断：CredentialStoreProbe通过message精确匹配超时脆弱，可改结构错误码；当前消息匹配有效，不宣称现有超时全被误分类。
- 证据：harness-cli/src/test/java/io/jharness/cli/CredentialStoreProbe.java:37

## OCR-0335 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/CredentialStoreProbe.java:47；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：cleanup 失败只把 `succeeded` 置为 false，没有写入 `failureCode`，于是 try 块已成功、清理阶段失败时会输出 `"passed": false` 且 `"failureCode": ""`，下游按固定类别解析报告的脚本/看板拿不到失败分类（本次删除或清理时的超时也完全不会被识别为 `WORKER_TIMEOUT`）。另外只捕获 `Exception`，若 try 块抛出 `Error`（如 JNA 加载失败的 UnsatisfiedLinkError）同样会以空 failureCode 输出。建议在 cleanup 分支复用同一套归类逻辑并写入如 `CLEANUP_FAILED`（超时场景优先 `WORKER_TIMEOUT`）。
- 判断：cleanup失败只succeeded=false而failureCode空，补CLEANUP_FAILED及独立安全诊断，保留主失败/清理失败两者。
- 证据：harness-cli/src/test/java/io/jharness/cli/CredentialStoreProbe.java:47

## OCR-0336 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/CredentialStoreProbe.java:32；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：`deleteMissing` 校验只以“第二次删除没有抛异常”作为通过条件，缺少删除后再次读取为空的后置断言。若实现对不存在键的删除产生副作用（例如重新创建条目、写入空值、仅软删除），探针仍会判定通过，无法证明缺失键删除是幂等的。建议补一次读取断言。
- 判断：重复delete后无额外read可补幂等后置条件；不必为假想delete创建条目构建复杂场景。
- 证据：harness-cli/src/test/java/io/jharness/cli/CredentialStoreProbe.java:32

## OCR-0337 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/StdioSdkServer.java:132；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：句柄生命周期泄漏：`handles` 只在 session.open 里 put（148-149 行），全文件没有任何 remove/close 路径，method 列表里也没有 session.close。因此一个长驻 stdio 客户端累计打开 32 次会话（包括对同一 sessionId 的重复打开、早已结束/归档的会话）之后，后续所有 session.open 都会永久返回 HANDLE_LIMIT，只能重启进程才能恢复。建议增加释放路径（session.close 或会话终态/归档后回收句柄），至少允许回收 DRAFT 与终态句柄。
- 判断：session.open每次put新handle且上限32，无close方法永久占满，增加显式释放且运行中句柄不误回收。
- 证据：harness-cli/src/main/java/io/jharness/cli/StdioSdkServer.java:132

## OCR-0338 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/StdioSdkServer.java:254；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：`approved` 完全未校验：`keys()` 只校验键存在，客户端发送 `"approved": null` 时 `Map.of(...)` 会直接抛 NPE，被 run() 捕获后降级成 SDK_OPERATION_FAILED（而不是 INVALID_PARAMS）；发送字符串/对象则被原样转发给宿主，只能靠宿主 400 兜底，并返回 INVALID_APPROVAL 之类的模糊错误码。建议显式校验 `p.get("approved") instanceof Boolean`，否则抛 INVALID_PARAMS，再放进 body。
- 判断：approved未检查Boolean，null入Map.of及错类型依赖宿主，提前INVALID_PARAMS并断言无HTTP副作用。
- 证据：harness-cli/src/main/java/io/jharness/cli/StdioSdkServer.java:254

## OCR-0339 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/StdioSdkServer.java:317；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：先解析响应体再判断状态码：4xx/5xx 若返回空体或非 JSON（代理/中间件错误页、204 语义等），会先抛 INVALID_JSON，真实的 HTTP 状态与宿主错误码全部丢失，客户端只能看到 INVALID_JSON/SDK_OPERATION_FAILED。另外 `statusCode() < 400` 包含 3xx，而 HttpClient 配置了 followRedirects=NEVER，重定向响应体会被当作成功结果返回。建议先分流状态码（3xx 也视为失败），仅在 body 非空且为 JSON 对象时解析，并在错误映射中保留状态信息。
- 判断：先parse再看HTTP状态掩盖空/非JSON错误，采纳状态分流和安全错误；只连接loopback宿主，无任意代理可信响应假设。
- 证据：harness-cli/src/main/java/io/jharness/cli/StdioSdkServer.java:317

## OCR-0340 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/StdioSdkServer.java:295；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：原始 params 直接透传：text 未校验是否 String/长度上限，limit/offset 未做范围约束，mode/view/rebuild/cursor 未做类型与枚举校验，最大 64KB 的任意嵌套 JSON 结构都会进入宿主检索服务，绕过了本适配器「严格 INVALID_PARAMS + 有界数据」的契约；一旦宿主因类型不符抛非 HarnessException，只会退化成 SDK_OPERATION_FAILED。建议像 events.list 一样逐字段校验（text 长度上限、limit 范围、`rebuild instanceof Boolean`、view ∈ {all,active,archived}、cursor 用 identifier 并限制长度），只组装宿主契约要求的字段。
- 判断：search部分字段整包透传可统一严格类型/限额映射，宿主仍会校验，不认定绕过宿主有界数据。
- 证据：harness-cli/src/main/java/io/jharness/cli/StdioSdkServer.java:295

## OCR-0341 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/StdioSdkServer.java:239；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：question.answer 同样整包透传：text/selected/skip/requestId 均未做类型与长度校验（skip 可为任意 JSON、text 可为对象或接近 64KB 的字符串），而 turnId/callId 也没走 `field(...)` 边界。宿主收到畸形类型时只能返回 400/500 的模糊错误码，无法定位为参数问题。建议对 text 用 `field(p,"text",16384)`、requestId 用 identifier、selected 校验为字符串数组、skip 校验为 Boolean，并显式组装字段而不是 `new LinkedHashMap<>(p)`。
- 判断：question.answer应固定text/selected/skip/requestId schema，在SDK边界早拒绝；宿主问答仍有幂等/边界校验。
- 证据：harness-cli/src/main/java/io/jharness/cli/StdioSdkServer.java:239

## OCR-0342 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/StdioSdkServer.java:57；OCR原严重度：low
- 计划：R3-04 / P2
- 原意见：`id` 在信封校验之后才提取：当请求带有合法 id、但多了一个字段（或缺 params、jsonrpc 不是 "2.0"）被拒时，响应里的 `id` 恒为 null，客户端无法把错误关联到具体请求；另外 `identifier` 只接受 `[A-Za-z0-9_-]+` 字符串，JSON-RPC 2.0 允许的数字 id 会落到同一个无 id 的失败分支。建议在信封校验前先尽力取出 id（字符串或数字），再用它构造 INVALID_REQUEST 响应。
- 判断：合法id在信封拒绝前未保留导致关联丢失应修复；本SDK明确1.0字符串id约定，不未经协议升级支持所有JSONRPC数字id。
- 证据：harness-cli/src/main/java/io/jharness/cli/StdioSdkServer.java:57

## OCR-0343 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/StdioSdkServer.java:306；OCR原严重度：low
- 计划：无
- 原意见：宿主 bearer 凭据被无条件附加到 `address.resolve(path)`，构造函数未校验 address 必须是 http(s) 且为 loopback。当前唯一调用方传入的是本机 server.address()，影响有限，但一旦地址来自配置/外部输入，凭据就会被发往任意源（`Redirect.NEVER` 只防重定向，不防目标地址本身）。建议在构造函数中断言 scheme 为 http/https 且主机为回环地址。
- 判断：StdioSdkServer是包内实现且唯一构造地址来自本机server.address，未经配置外部任意源路径，声称凭据外发当前不可达。
- 证据：harness-cli/src/main/java/io/jharness/cli/StdioSdkServer.java:306

## OCR-0344 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/CliContractsTest.java:136；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：该测试用 actor="user" 的 tool.verified 消除不确定调用后断言 uncertainCalls 为空，但从未验证“非操作员核实”被拒绝，也未验证不同 callId 的核实不会清除本调用，因此无法支撑测试名中的 “RequiresOperatorVerification” 与 “clears only that call”。对照 harness-api/.../SessionProjection.java 第 23 行，`tool.verified` 是无条件 `pending.remove(callId)`，并不校验 payload 中的 actor/outcome；也就是说模型或系统伪造一条 tool.verified 即可清除 UNKNOWN（UNKNOWN 不得被重放的约束在测试中完全未覆盖）。建议补充：1) actor 为 "model"/缺失或 outcome 非人工核实的事件，断言 uncertainCalls 仍包含 "c"；2) 对 callId="other" 的 tool.verified，断言 "c" 仍不确定；3) 仅有 READ_ONLY intent 时 UNKNOWN 完成也必须保持不确定。
- 判断：补不同callId不会清除本call；actor只是可信日志审计值不是认证凭证，不能凭模型构造Java事件声称越权，也不改READ_ONLY UNKNOWN阻塞合同。
- 证据：harness-cli/src/test/java/io/jharness/cli/CliContractsTest.java:136

## OCR-0345 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/CliContractsTest.java:102；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：此测试只断言 `facts.changes` 与 `facts.commands` 为空，未检查 EvidenceReport 中真正承载“测试证据”的字段 `unexecutedValidation`（EvidenceReport.java 第 124-129 行在无命令时会生成 “No command execution is recorded; model prose is not test evidence”），因此测试名声称的 “never become ... test evidence” 并未被验证：如果实现把模型叙述写入 unexecutedValidation 或其它事实字段，本测试仍会通过。建议追加断言 `report.get("unexecutedValidation")` 含 “model prose is not test evidence”，并确认 modelExplanation 未进入 recordedFacts。
- 判断：模型叙述不作为事实已有changes/commands空，补unexecutedValidation与modelExplanation分区明确证明不冒充测试证据。
- 证据：harness-cli/src/test/java/io/jharness/cli/CliContractsTest.java:102

## OCR-0346 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/CliContractsTest.java:182；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：该用例把“干跑隔离”与“无法预览”混在一起：期望退出码 4 且输出 “无法预览” 说明整个计划被拒绝（含 write_file 与 CMD 命令），因此代码路径根本没有走到逐条安全预览，无法证明“dry-run 不产生副作用、不需要 API key”；同时 `err` 仅用于断言消息，若 4 来自计划解析失败也会误通过。隔离断言也只检查 `root/new.txt`，若路径解析错误把文件写到进程 cwd 或 `& start cmd` 真的启动了子进程，测试都发现不了。建议：1) 增加一个可预览的计划，断言退出码为 0 且未触发鉴权错误；2) 断言 stdout/stderr 中不含鉴权失败信息；3) 对 `Files.list(root)` 与进程表/子进程副作用做断言，或至少同时校验 cwd 下未被写入。
- 判断：不可预览负例应保留，另加可预览成功且无API key/文件副作用正例，避免只经预检拒绝却称覆盖执行隔离。
- 证据：harness-cli/src/test/java/io/jharness/cli/CliContractsTest.java:182

## OCR-0347 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/CliContractsTest.java:212；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：该测试丢弃了 stdout/stderr（`sink` 未保留内容），只断言退出码 5 与 `.jharness/config.json` 不存在。退出码 5 是通用配置错误，模型字段不合法、参数解析失败等其它原因同样会返回 5；同时若 CLI 把已知密钥回显到 stderr 或以其它文件名（临时文件、备份、其他目录配置）落盘，本测试仍通过，与“敏感信息脱敏”要求不符。建议分别捕获 out/err，断言错误码为 SECRET_CONFIG 类信息、`sink` 内容不包含 "fixture-private-value"，并断言 `.jharness` 目录下无任何新增/残留文件。
- 判断：SECRET_CONFIG负例不保留sink，补明确code、stdout/stderr无哨兵和本fixture元数据无新凭据文件。
- 证据：harness-cli/src/test/java/io/jharness/cli/CliContractsTest.java:212

## OCR-0348 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/CliContractsTest.java:255；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：未来格式与损坏尾部两种情况都用同一个 `sink`/`out` 且都期望退出码 6，无法区分“版本不兼容”和“文件损坏”，也无法证明 inspect 没有改写文件：`assertTrue(out.toString().contains("\"writable\":false"))` 只看 CLI 自述，未校验 `future.jsonl` 内容未变；末尾的 `assertEquals(damaged, ...)` 只检查主文件，若 CLI 生成 `tail.jsonl.bak`、`.tmp`、截断后重写或创建 sidecar 修复文件，测试仍会通过。建议：1) 两次调用使用独立输出流并断言不同的错误码/错误类型（FUTURE_VERSION vs JSON_DAMAGED）；2) 调用前后记录 `Files.readAllBytes(future.jsonl)` 与目录列表并逐一对比，确保只读且无新增文件。
- 判断：future与damaged不同错误应独立输出核验，补未来文件及目录前后字节/条目不变；不要修改旧证据来迎合新错误码。
- 证据：harness-cli/src/test/java/io/jharness/cli/CliContractsTest.java:255

## OCR-0349 · 不采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/CliContractsTest.java:189；OCR原严重度：low
- 计划：无
- 原意见：同一个 `output` 同时作为 out 与 err，且同一个空 `input` 被四次 `execute` 复用。若 `cancel` 路径会读取/确认 stdin（或任何命令消费输入），后续调用只能看到 EOF，测试结果依赖于调用顺序；输出混流也无法判断错误信息来自哪次调用，退出码断言可能在错误原因下误通过。建议每次调用创建独立的 `ByteArrayInputStream`/`ByteArrayOutputStream`，分别捕获并断言 out/err。
- 判断：空ByteArrayInputStream重复读取永远EOF且这些维护命令不应需要输入，复用有意检验无需交互；混流仅影响诊断不构成当前错误通过。
- 证据：harness-cli/src/test/java/io/jharness/cli/CliContractsTest.java:189

## OCR-0350 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:15；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：Windows 分支完全跳过探测就直接返回 true：只要 os.name 以 Windows 开头就认定凭据库可用，即使 java.class.path 中缺少 NativeCredentials、JNA/jnidispatch 无法加载或 Advapi32 调用失败。此时 description() 会谎报 "Windows Credential Manager"，invoke() 也不会抛出 CREDENTIAL_STORE_UNAVAILABLE，而是在首次真实操作时以通用的 "Credential store operation failed" 失败，与非 Windows 平台的错误契约不一致。建议 Windows 同样走 probe 路径（NativeCredentials 的 WindowsVault 对 probe 直接返回 available），或在首次操作失败后回填 available=false 并映射为 CREDENTIAL_STORE_UNAVAILABLE。
- 判断：SystemCredentials.supported在Windows直接true，不能发现worker/JNA加载失败，Windows也应做无秘密只读probe并映射可用性。
- 证据：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:15

## OCR-0351 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:30；OCR原严重度：medium
- 计划：无
- 原意见：可用性缓存一旦写入 false 就永久生效：probe 期间的任何 IOException/RuntimeException（7 秒超时、D-Bus 临时不可用、keychain 被锁定、子 JVM 启动抖动）都会把 available 固化为 false，此后整个进程生命周期内 read/write/delete/update 一律抛 CREDENTIAL_STORE_UNAVAILABLE，只能重启进程恢复，不满足可恢复性要求。另外该 probe 最长阻塞 7 秒且运行在 synchronized 方法内，期间所有并发的凭据调用都会排队。建议区分“平台确实不支持”与“临时失败”：仅对确定的不可用结果做永久缓存，对超时/IO 失败保留按时间窗退避重试的通道，并把探测过程移出锁区间。
- 判断：M10.5.4-contracts第17行明确supported缓存和服务恢复需重启是当前边界，不能把已声明恢复方式当遗漏；增加热恢复刷新属新功能，本轮不强加。
- 证据：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:30；docs/development/M10.5.4-contracts.md:17

## OCR-0352 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:91；OCR原严重度：medium
- 计划：R1-04 / P1
- 原意见：update 固定把所有 write 排在所有 delete 之前，且整批不做冲突校验：当同一 target 同时出现在 writes 与 deletes（例如凭据轮换/重命名场景）时，刚写入的凭据会被随后的 delete 静默删除，调用方却收到成功。同时整批并非原子操作——NativeCredentials 逐条执行，某一条校验或原生调用失败即 exit 7，前面已执行的操作已经落盘，父进程却只看到通用异常，无法判断哪些生效（WorkbenchSettings.save 在写设置文件之前调用本方法，部分生效会留下无引用的凭据）。建议拒绝 writes 与 deletes 的交集，或明确 delete→write 的执行顺序，并让失败路径能给出可判定的生效范围以便补偿。
- 判断：update未拒绝write/delete同target且批次非原子，采纳冲突校验和部分/未知生效状态；简单改delete→write会先删旧秘密不可接受。
- 证据：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:91

## OCR-0353 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:100；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：这里既不限制也不分批操作数量，而 NativeCredentials.main 在 operations.size() > 32 时会在打开 vault 之前直接 exit 7：任何超过 32 条的批量（例如一次 save 中删除大量已被移除模型的 credentialRef，或同时写入+删除累积超过 32 条）都会被整批拒绝，父进程只能报 "Credential store operation failed"，这类清理将永远无法成功。建议在此按 32 条分片顺序调用，并在某个分片失败时停止并暴露已完成的分片，避免大批量操作成为不可达路径。
- 判断：SystemCredentials不检查操作数而worker>32拒绝，采纳上层有界校验和可诊断限制；分片会扩大部分提交，须与补偿策略一起设计。
- 证据：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:100

## OCR-0354 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:127；OCR原严重度：medium
- 计划：R1-04 / P1
- 原意见：超时后直接抛 IOException，随后在 finally 中 destroyForcibly() 杀掉子进程：子进程可能已经在原生库侧完成写入（secret_password_store_sync/CredWriteW 已返回、结果尚未输出时被杀），调用方却收到确定的失败，形成 UNKNOWN 结果——凭据已写入但 WorkbenchSettings 不会保存其引用，留下无法回收的孤儿秘密；重试也无法保证与原语义一致。建议对写操作在超时/中断后执行补偿删除，或至少返回可区分的“结果未知”错误码，由上层按未知状态处理而不是当作失败。
- 判断：写超时可已生效但父仅IOException，需明确UNKNOWN及对账；盲目补偿删除可能删除已被其他操作更新的凭据，不采纳自动删除建议。
- 证据：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:127

## OCR-0355 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:131；OCR原严重度：medium
- 计划：无
- 原意见：父进程先 waitFor 子进程退出、之后才读取 stdout：只要子进程输出超过管道缓冲区（Linux/macOS 通常约 64KB），子进程就会阻塞在写 stdout 上无法退出，父进程在 7 秒后判定超时；2048 字节的上限也因此只能在进程退出后才生效（当前仅因 NativeCredentials 把单条结果限制在 1024 字节才没有触发）。建议在等待期间用独立线程或有界读取并发排空 stdout（读满 2049 字节即停止并标记超限，超过上限时主动 destroyForcibly），并在 finally 中关闭 process.getInputStream()，避免依赖子进程当前的长度上限。
- 判断：worker单结果先限制<=1024字节再Base64，stdout最大约1368字节且stderr丢弃，当前不可能填满常见管道；未来扩输出再并发排空。
- 证据：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:131

## OCR-0356 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:129；OCR原严重度：low
- 计划：R1-04 / P2
- 原意见：子进程的所有失败路径（参数校验失败、原生库/服务不可用、批处理中途失败）都以 exit 7 返回，且 stderr 被 DISCARD，父进程统一映射为同一句 "Credential store operation failed"，调用方无法区分“确定未生效”“部分生效”和“环境不可用”，不利于决定重试还是补偿删除。建议至少区分超时、存储不可用、请求非法与未知结果几类错误码/消息（注意不要回传可能含秘密的原生错误文本）。
- 判断：所有worker失败统一exit7且stderr丢弃，需安全结构化区分无效/不可用/部分或未知结果，不回显原生秘密文本。
- 证据：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:129

## OCR-0357 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:55；OCR原严重度：low
- 计划：无
- 原意见：辅助 JVM 直接用 System.getProperty("java.class.path") 作为类路径：以模块方式（--module-path/-m）启动时该属性为空，经自定义 ClassLoader/嵌套 JAR（如 Spring Boot 风格打包）启动时这些条目在子 JVM 中不可解析，NativeCredentials 会 ClassNotFound 退出 7，表现为所有凭据操作都以通用错误失败，而 Windows 分支还会因 supported() 直接返回 true 而进一步掩盖问题。建议通过 NativeCredentials.class 的 CodeSource 定位实际代码位置，或在类路径缺失时给出明确的不支持诊断。
- 判断：当前正式入口是classpath/可执行fatJAR，未承诺modulepath或嵌套SpringBoot；假设新打包形式不可作为当前功能回归。
- 证据：harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java:55

## OCR-0358 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/EvidenceCompletionTest.java:37；OCR原严重度：medium
- 计划：R1-06 / P2
- 原意见：该用例只有负向断言，缺少正向对照：整个仓库中 `--require-read` 仅在 EvidenceCompletionTest 出现，而这里从未验证“本轮成功 read_file 应返回 achieved()=true”。因此若 EvidenceCompletion 恒返回 false（例如 latestTurnId 解析回归、require-read 循环被误删、normalize 逻辑失效），本用例依旧通过；另外用例只断言 achieved()，若 latestTurnId 为空导致提前返回 GOAL_UNVERIFIED 也会“假通过”，并未真正验证 READ_EVIDENCE_MISSING 分支。建议在同一测试中追加当前轮成功读取事件并断言 achieved() 为 true，并校验 reason 为 READ_EVIDENCE_MISSING 而非 GOAL_UNVERIFIED。
- 判断：require-read负例需加同轮成功读取的正例与具体reason，原文全仓只此使用不实（M7CLI集成也使用），采纳本测试正负对照。
- 证据：harness-cli/src/test/java/io/jharness/cli/EvidenceCompletionTest.java:37

## OCR-0359 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/EvidenceCompletionTest.java:27；OCR原严重度：medium
- 计划：R1-06 / P2
- 原意见：证据事件只覆盖了 status=SUCCESS，没有任何 status=UNKNOWN 或缺失 status 的负向用例。规则明确 UNKNOWN 不得被当作有效证据重放，而 `success()` 的判定（"SUCCESS".equals(status)）一旦被放宽（例如改为 !"FAILED".equals(status) 或忽略缺失 status），现有测试无法发现。建议补充 status=UNKNOWN 的 read_file / WORKSPACE_WRITE 事件，断言 require-read 返回 READ_EVIDENCE_MISSING、expect-file 返回 CHANGE_EVIDENCE_MISSING。
- 判断：补UNKNOWN/缺status read/write不能作为完成证据，锁定仅SUCCESS判据。
- 证据：harness-cli/src/test/java/io/jharness/cli/EvidenceCompletionTest.java:27

## OCR-0360 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/EvidenceCompletionTest.java:48；OCR原严重度：medium
- 计划：R1-06 / P2
- 原意见：验收命令语义只测了“先失败、后成功”，没有覆盖“先成功、后失败”的最新匹配命令场景，也没有覆盖 status=SUCCESS 但 exitCode!=0 的情况。实现的规定是最后一次匹配命令必须 exitCode=0（EvidenceCompletion 第 65-79 行），若实现退化为“最后一次写入之后存在任意成功匹配”或忽略 exitCode，当前用例仍会通过。建议追加 [start, success, fail(same argv/cwd)] 的断言，并校验 reason 为 VERIFICATION_FAILED。
- 判断：补最后同命令失败覆盖此前成功、SUCCESS且非零exit拒绝，验证最新命令/实际写之后的证据。
- 证据：harness-cli/src/test/java/io/jharness/cli/EvidenceCompletionTest.java:48

## OCR-0361 · 不采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/EvidenceCompletionTest.java:153；OCR原严重度：low
- 计划：无
- 原意见：所有夹具事件都使用 schemaVersion=1 与 `Instant.now()` 构造，时间戳可能相同且不保证单调递增；用例的正确性完全依赖 List 的传入顺序（EventSessionProjection 与 EvidenceCompletion 都未按 timestamp 排序）。如果后续实现改为按时间戳/序号排序，这些用例会因时间戳相等而顺序不确定，可能变成偶发失败或无法再覆盖本意的排序语义。建议按序构造递增时间戳（或引入可递增的固定时间基准），使夹具与真实事件流的排序特征一致。
- 判断：schemaVersion=1不是序列号，事件物理列表顺序才是重建权威，要求timestamp严格递增/按其排序会改变正确契约。
- 证据：harness-cli/src/test/java/io/jharness/cli/EvidenceCompletionTest.java:153

## OCR-0362 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M8FilterConfigTest.java:46；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：该断言是恒真断言：`ConfigurationSelection.resolve` 每次都会向 selection 写入 `resolvedSha256`，所以它无法验证哈希确实由“最终校验后的 filter + 规范化 selection”推导。触发条件：若 resolve 仍用预设/未替换的 searchFilter 计算哈希，或 selection 丢失 profile/preset 信息，本测试依然通过。建议断言哈希为 64 位小写十六进制（production 使用 SHA-256 + HexFormat），并断言 `selection` 的 profile/preset/modelRoute 分别为 "inspect"/"p"/"base"，以证明优先级确实生效。
- 判断：仅containsKey不验证resolvedSha256内容/作用范围，补稳定/变化敏感性而非只正则64hex即可；记录profile/preset真实优先级。
- 证据：harness-cli/src/test/java/io/jharness/cli/M8FilterConfigTest.java:46

## OCR-0363 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M8FilterConfigTest.java:41；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：替换语义只覆盖了“合法 filter 被整体替换”，未覆盖 profile/preset 提供非法 filter 时 resolve 必须抛 `INVALID_SEARCH_FILTER`（这正是用例名中的 “validated object”）。触发条件：若 resolve 直接把 settings 里的 searchFilter 透传给下游而跳过校验，本测试仍通过。建议补充 `resolve(sourceWithProfileBadFilter, args)` 的 assertThrows，并断言异常在装配前抛出。
- 判断：合法整体替换不证明非法覆盖对象仍受校验，增加profile/preset非法filter并断言INVALID_SEARCH_FILTER。
- 证据：harness-cli/src/test/java/io/jharness/cli/M8FilterConfigTest.java:41

## OCR-0364 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M8FilterConfigTest.java:45；OCR原严重度：low
- 计划：R3-04 / P3
- 原意见：不可变性只检查了顶层 `searchFilter.rules()`。resolve 内部是 `new LinkedHashMap<>(source.values)` 浅拷贝后 `putAll(settings(preset))`，若嵌套的 presets/profiles Map 被就地修改（例如修改 preset 的 settings.searchFilter），此断言不会发现。建议追加对 source 的 presets/profiles 嵌套内容的断言，并断言 `result.searchFilter().rules()` 不可变（调用 add 应抛 UnsupportedOperationException，production 使用 List.copyOf）。
- 判断：源catalog不可变快照已有CliConfig保证，可补可变输入及嵌套未改写，避免浅层断言替代深隔离。
- 证据：harness-cli/src/test/java/io/jharness/cli/M8FilterConfigTest.java:45

## OCR-0365 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M8FilterConfigTest.java:50；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：方法名声称“校验先于模型/环境装配”，但测试只调用了 `new CliConfig(...)`，从未触发模型或环境装配，也未断言失败路径无副作用；即使把 searchFilter 校验挪到模型/环境装配之后，本测试仍会通过。建议重命名为 `badFilterIsRejectedAtConfigConstruction`，或注入可观测的装配入口并断言其未被调用，否则该命名会给出虚假的回归保障。
- 判断：仅构造CliConfig证明构造期拒绝，可重命名精准说明；不必为简单配置负例引入全套模型环境装配。
- 证据：harness-cli/src/test/java/io/jharness/cli/M8FilterConfigTest.java:50

## OCR-0366 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M8FilterConfigTest.java:58；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：遍历/边界校验只覆盖了 `../secret` 和未知键两种输入，不足以证明 `INVALID_SEARCH_FILTER` 的覆盖面。建议参数化补充：绝对路径（如 `/etc/passwd`）、`a/./b`、`a//b`、仅 `!` 的规则、空串/纯空白与 `#` 注释（应被过滤而非报错）、非字符串 rules 元素、非布尔 `useDefaultExcludes`/`useGitignore`、长度 >256 或条数 >128。尤其绝对路径需要确认：`WorkspaceSearchFilter.checkedRules` 目前只拒绝 `//` 前缀和单独的 `/`，`/etc/passwd` 会被接受，请用用例明确这是预期行为还是路径边界漏洞。
- 判断：补filter边界/类型/不支持语法合理；/etc/passwd是gitignore根锚定相对规则，不能作为绝对FS路径要求拒绝，注释/空规则按合同过滤。
- 证据：harness-cli/src/test/java/io/jharness/cli/M8FilterConfigTest.java:58

## OCR-0367 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M8ResilienceConfigTest.java:20；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：Only the invalid side of each numeric bound is exercised (maxAttempts 5/0, backoffMillis -1, totalTimeoutMillis 49, minIntervalMillis 60001, maxWaiters 33). The implementation in ModelResilienceOptions bounds these with `<1 / >4`, `<0 / >10000`, `<50 / >300000`, `<0 / >60000`, `<0 / >32`, so an off-by-one regression (e.g. changing `maxAttempts > 4` to `>= 4` or `totalTimeoutMillis < 50` to `<= 50`) would not be caught by this test. Add a positive-boundary assertion set that must be accepted (maxAttempts 1 and 4, backoffMillis 0 and 10000, totalTimeoutMillis 50 and 300000, minIntervalMillis 0 and 60000, maxWaiters 0 and 32) and verify the values survive construction.
- 判断：韧性只测越界负例，补各合法端点及保留实际值，锁定包含边界不被误收窄。
- 证据：harness-cli/src/test/java/io/jharness/cli/M8ResilienceConfigTest.java:20

## OCR-0368 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M8ResilienceConfigTest.java:30；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：This assertion only checks the exception type, not the rejection reason. CliConfig throws HarnessException from several independent paths (INVALID_CONFIG for unknown keys / invalid enabledPlugins, INVALID_MODEL_RESILIENCE for the plugin gate), so if the value `"registry"` ever stops being a valid plugin name (see CliConfig.PLUGINS) the test would still pass while no longer proving the resilience/plugin ordering rule. Assert the code explicitly, and add the complementary positive case where enabledPlugins contains `model-resilience` so an inverted or over-strict gate is also detected.
- 判断：插件禁用仅异常类型可无关提前失败，锁定INVALID_MODEL_RESILIENCE并加启用插件正例。
- 证据：harness-cli/src/test/java/io/jharness/cli/M8ResilienceConfigTest.java:30

## OCR-0369 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M8ResilienceConfigTest.java:59；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：The whole-object replacement is only proven for `maxAttempts`, because it is the only resilience field given a non-default value in the base config. `backoffMillis` (100), `totalTimeoutMillis` (30000), `minIntervalMillis` (0) and `maxWaiters` (0) are left at their defaults in the base object, so an implementation that merges the base object with the profile object (a partial-merge regression) would still satisfy these assertions. Give the base config distinct non-default values for every field and assert all of them are reset to the defaults (1/100/30000/0/0) after the safe profile is selected.
- 判断：base非默认仅maxAttempts使其它字段merge与replace不可区分，给全部可配置字段不同非默认后只覆盖一项检验整体替换。
- 证据：harness-cli/src/test/java/io/jharness/cli/M8ResilienceConfigTest.java:59

## OCR-0370 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M9DistributionDoctorTest.java:43；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：该断言隐含依赖宿主机的 git：`CliApplication.doctor` 中 `checks.put("git", ...)` 使用 `LocalProcessRunner.buildEnvironment()`，而该方法读取的是 `System.getenv("PATH")`（环境变量白名单是宿主进程环境），并非本测试注入的 `Map`（注入的 PATH 仅为空的临时目录）。而 `ready` 的计算要求 `git` 为 true 才返回 0。因此在不安装 git 的构建机/容器中，本用例必然失败（返回 5），与它要验证的“打包运行时不需要 Maven”这一关注点无关，属于机器相关的脆弱断言；同时也说明该测试并没有真正覆盖环境变量白名单（git 探测绕过了注入环境）。建议显式处理 git：读取并断言 `checks` 中的 maven 相关字段，或在 git 不可用时跳过/参数化，而不是无条件断言总体 `ready`。另外失败消息拼接了完整的 stderr+stdout，一旦将来 doctor 在输出中回显解析后的配置或环境，凭据会直接进入 CI 日志，建议只拼接有限长度且脱敏的摘要。
- 判断：doctor总体ready确依赖git，测试应明确构建环境前置且聚焦Maven非必需；不能以git缺失skip正式发行门禁。
- 证据：harness-cli/src/test/java/io/jharness/cli/M9DistributionDoctorTest.java:43

## OCR-0371 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M9DistributionDoctorTest.java:47；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：脱敏校验不完整：只检查了 stdout 中是否出现该字面值，`errors`（stderr）从未被断言，仅出现在失败消息里。若 doctor 或其他代码路径把 key 写到 stderr、或输出的不是完整 key 而是经过拼接/截断的片段（例如解析后的 config 回显、`apiKey` 字段），该断言仍会通过。建议同时断言 `errors.toString(UTF_8)` 不含该值，并在解析 JSON 后按字段（如 `apiKey`/`secrets`/`environment` 等）断言不存在敏感值，而不是仅做整串包含判断。
- 判断：doctor已检查stdout哨兵，补stderr与结构化不含敏感字段，不在失败消息打印秘密。
- 证据：harness-cli/src/test/java/io/jharness/cli/M9DistributionDoctorTest.java:47

## OCR-0372 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M9DistributionDoctorTest.java:46；OCR原严重度：low
- 计划：R3-04 / P2
- 原意见：用 `json.contains(...)` 做子串匹配来验证输出协议，无法确认字段层次、类型与字段名：例如嵌套在其他对象里的同名键、数值/字符串形式的 `true`（"true"）都可能造成误判，且实际输出 schema（`ready`、`checks.java21`、`checks.apiKeyPresent`、`checks.maven*`、`assemblyProbe` 等）完全没有被校验。建议用项目已有的 `Json.parse` 解析后按类型断言具体字段。此外该断言把“baseUrl 指向 127.0.0.1:1（不可达）时 ready 仍为 true”固化为契约，但 `doctor` 目前并不做连通性探测；若将来加入端点可达性检查，此用例会以难以定位的方式失败，建议在断言旁注释明确 `ready` 的语义仅为“前置条件满足，未发起 API 探测”。
- 判断：子串true不能保证schema，改解析checks布尔；doctor明确只本地无网络，不能将未来端点探测当修复方向。
- 证据：harness-cli/src/test/java/io/jharness/cli/M9DistributionDoctorTest.java:46

## OCR-0373 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:426；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：`URI.create` 对非法 `baseUrl` 抛出未受检的 `IllegalArgumentException`，而 `save()` 路径上 Web 层只捕获 `HarnessException`/`IOException`，因此畸形 URL 会变成 500/连接中断，而不是约定的 `INVALID_SETTINGS`。此外 `validate` 里紧接着的 `new CliConfig(configuration(value, model))` 也会对 ecosystem 中的 mcp url / webSources 执行 `URI.create`，同一路径同样会逃逸为未受检异常。建议在这里捕获 `IllegalArgumentException` 并转换为 `error("INVALID_SETTINGS")`。
- 判断：WorkbenchSettings.validate URI.create未包装，与Web错误映射不齐；捕获合法字段解析错误返回INVALID_SETTINGS且不回显URL秘密。
- 证据：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:426

## OCR-0374 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:453；OCR原严重度：high
- 计划：无
- 原意见：环境变量白名单缺失：`apiKeyEnv` 只校验了变量名格式（validate 中 `[A-Za-z_][A-Za-z0-9_]*`），这里直接把主机环境变量的值当作发往 `model.baseUrl` 的 API key。保存时把 `apiKeyEnv` 指向 `AWS_SECRET_ACCESS_KEY`、`GITHUB_TOKEN` 等任意主机变量，并配合把 `baseUrl` 设为外部地址，即可把与模型无关的凭据作为 Authorization 发给第三方（且这些变量名未必命中构造器里的 `_API_KEY|_TOKEN|_SECRET|_PASSWORD` 脱敏规则，也不会被脱敏）。同时 `runtime()` 还把整份 host environment 复制给运行时。建议在 validate/save 中把 `apiKeyEnv` 限定为 `JHARNESS_*` 或启动时显式声明的允许集合，并进行前缀校验，拒绝任意主机变量名。
- 判断：settings是受保护本机宿主配置入口，用户显式apiKeyEnv引用任意合法凭据名是产品能力；强制JHARNESS前缀会破坏DeepSeek等示例。
- 证据：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:453

## OCR-0375 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:107；OCR原严重度：medium
- 计划：无
- 原意见：加载路径与写入路径的文件系统防御不一致：这里只用 `Files.exists(..., NOFOLLOW_LINKS)` 判断存在性（对符号链接本身仍返回 true），随后 `Json.read` 会跟随符号链接读取目标文件；而 `save()` 明确拒绝链接/非普通文件。攻击者可在 `.jharness/settings/workbench.json` 放置指向外部文件的符号链接，重启后其内容被当作可信设置（含 `baseUrl`/`apiKeyEnv`）载入，绕过写入时的 `CONFIG_PATH_DENIED` 防御；FIFO/设备文件还可能导致读取阻塞。建议读取前用 `Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)` 校验，并对 `file.getParent().toRealPath()` 做目录归属校验，与 save 保持一致。
- 判断：load虽先exists，但随后Json.read已isRegularFile(NOFOLLOW_LINKS)拒绝最终链接/FIFO，原文声称Json.read跟随最终链接不实；父级竞态另单独核查。
- 证据：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:107

## OCR-0376 · 不采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:427；OCR原严重度：medium
- 计划：无
- 原意见：允许 `http` 明文 `baseUrl`：API key 会以请求头发往该地址，可被网络中间人窃取。建议仅允许 `https`，如需本地调试可仿照 `EcosystemConfig.validate` 对 mcp/url 的策略，把 `http` 限制到 localhost/127.0.0.1/[::1]。
- 判断：后续CliConfig/OpenAiCompatibleConfig已要求HTTPS或loopback HTTP，不能只看前一条startsWith校验就认定任意明文端点通过。
- 证据：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:427

## OCR-0377 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:225；OCR原严重度：high
- 计划：R1-04 / P1
- 原意见：跨步骤操作顺序错误：凭据在设置文件成功落盘之前就写入 vault 和 `credentialCache`。此后的 `metadata.directory("settings")`、`CONFIG_PATH_DENIED` 检查、`createTempFile`、写入与 `ATOMIC_MOVE` 任一步失败（磁盘满、权限、路径被替换为链接等）时，`state` 不会更新，但新 `ref` 对应的密钥已永久留在 vault 与内存 cache 中：既没有回滚，也没有待清理记录——后续 save 的 `obsolete` 是从 `state` 派生的，永远看不到这个未被引用的 ref。建议把这些写入移到文件原子替换成功之后，或在失败分支回删 vault 条目并登记待清理 ref。
- 判断：vault/cache在设置文件原子提交前更新，失败可能留下新引用孤儿，需事务/清理账本；把vault写简单后移会让文件引用未写入秘密，不能照搬。
- 证据：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:225

## OCR-0378 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:260；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：`credentialCleanupPending` 只被置为 true，文件中没有任何复位点：一旦某次过时凭据删除失败，即使之后清理成功，`read()` 也会永久返回 `credentialCleanupPending: true`，且没有重试清理的入口，状态不可恢复。建议在清理成功后置回 false（或在 `read()`/`save()` 前重试清理并据结果更新该标志）。
- 判断：credentialCleanupPending仅置true无恢复/重试账本，需保留待清理目标并在成功后准确复位。
- 证据：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:260

## OCR-0379 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:212；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：客户端省略 `selected` 或 `extensions` 时，`Map.of` 会因 null 值抛出 `NullPointerException`（而不是 `INVALID_SETTINGS`），Web 层未捕获 NPE，会返回 500。建议先做类型/非空校验再构造 candidate。
- 判断：candidate Map.of直接取selected/extensions，可null造成NPE，先必填/类型验证再构建并返回INVALID_SETTINGS。
- 证据：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:212

## OCR-0380 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:198；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：`secrets` 无界增长：每次 save 都无条件 `add`（不查重），且即使随后 `validate`/`sanitize`/落盘失败也已加入；`streamingText()` 与 `sanitize()` 每次调用都用 `List.copyOf(secrets)` 全量复制并重建 `SecretRedactor`，`key()` 里的 `secrets.contains` 也是 O(n)。重复保存同一密钥或反复失败重试会让内存和每 token/每响应开销持续增长。建议改用去重的 `LinkedHashSet`/`HashSet`，只在保存成功后登记，并为脱敏列表维护不可变快照（或设上限）。
- 判断：secrets每save追加且重复重建Redactor，应去重/缓存不可变版本；失败输入也可能已进入错误/原生结果，不应机械只在成功后登记而漏脱敏。
- 证据：harness-cli/src/main/java/io/jharness/cli/WorkbenchSettings.java:198

## OCR-0381 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/WorkbenchWorkspaces.java:105；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：注册流程的副作用无法回滚，且探针会向目标工作区写入一条永久存在的“幽灵会话”。触发条件：`validate()` 已经通过 `new Metadata(real).directory("sessions")` 在目标目录创建 `.jharness/sessions`；`factory.create(root)` 与这里的 `openWriter("web-workspace-owner", EXCLUSIVE)` 会真正创建 `<root>/.jharness/sessions/web-workspace-owner.jsonl`（含 jsonl header）和 `.lock` 文件。之后若 `sanitizer.sanitize(state).equals(state)` 抛 `SECRET_CONFIG`、序列化超过 32768 字节抛 `WORKSPACE_LIMIT`、或临时文件写入/`ATOMIC_MOVE` 失败，第 105-109 行创建的会话文件、锁文件、`.jharness` 目录都不会被清理，注册失败但工作区已被污染；即使注册成功，该 jsonl 也会被 `JsonlSessionRepository.list()` 列出，出现在会话列表/历史中。建议：不要用真实会话探针（改用 sessions 目录下的临时文件创建+删除来验证可写），或在失败分支中删除本次创建的 `web-workspace-owner.jsonl`/`.lock` 并回滚 `.jharness` 目录。
- 判断：workspace owner探针实际创建固定session文件，采纳明确探针命名/历史过滤及失败副作用清理；不能为回滚删除用户已有同名会话。
- 证据：harness-cli/src/main/java/io/jharness/cli/WorkbenchWorkspaces.java:105

## OCR-0382 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/WorkbenchWorkspaces.java:82；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：注册表条目只校验了 key 集合与 `id`/`name` 的类型，`path` 直接强转 String。若 `workspaces.json` 中 `path` 为数字/对象/数组，这里抛出 `ClassCastException`（未受检异常，绕过了 `INVALID_WORKSPACE_REGISTRY` 的启动错误处理）；若 list 元素不是对象，`Values.object` 抛 `INVALID_JSON`；若文件内容非法，`Json.read` 抛 `INVALID_JSON`；若 `path` 含 NUL 等非法字符，`validate` 中 `Path.of` 抛 `InvalidPathException`。建议在强转前做 `instanceof String` 判断并统一抛 `INVALID_WORKSPACE_REGISTRY`，同时对 `Path.of` 的 `InvalidPathException` 做捕获转换。
- 判断：加载registry path直接(String)及Path.of未包装，损坏文件应统一INVALID_WORKSPACE_REGISTRY并保持只读。
- 证据：harness-cli/src/main/java/io/jharness/cli/WorkbenchWorkspaces.java:82

## OCR-0383 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/WorkbenchWorkspaces.java:164；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：TOCTOU 校验缺口：逐级 `NOFOLLOW` 检查只覆盖了循环内的 `current`，循环结束后 `Files.getOwner(path)` 与 `path.toRealPath()` 默认跟随符号链接，且拿到的 `real` 没有再与规范化绝对路径做一致性校验。若攻击者在循环结束与这里之间把某个目录（或最终目录）换成指向别处的符号链接/重解析点，owner 校验会落在新目标上，`real` 也会被解析成被禁止目录（例如 `.ssh`、`.jharness` 下或其它用户目录）而不会再次经过 deny-list 与规范化检查，随后 `new Metadata(real).directory("sessions")` 会在该真实目录中创建 `.jharness`。建议在取到 `real` 后强制 `real.equals(path.toAbsolutePath().normalize())`，否则抛 `WORKSPACE_PATH_DENIED`；`RootCheck.verify()` 中同样需要该校验。
- 判断：逐级check后getOwner/toRealPath再Metadata创建，最后真实路径未重核deny规则存在分离窗口；先绑定同一根身份再使用并做受控竞态验证，不以简单real.equals声称彻底消除所有race。
- 证据：harness-cli/src/main/java/io/jharness/cli/WorkbenchWorkspaces.java:164；harness-cli/src/main/java/io/jharness/cli/WorkbenchWorkspaces.java:validate

## OCR-0384 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/WorkbenchWorkspaces.java:88；OCR原严重度：low
- 计划：R2-01 / P3
- 原意见：`profiles()` 未持有对象锁就复制可变的 `LinkedHashMap`，而 `register()` 在同步块中执行 `profiles.put(...)`。若 `profiles()` 与一次注册并发执行，`Map.copyOf` 遍历时可能抛 `ConcurrentModificationException` 或得到撕裂的快照（缺少 happens-before，也看不到最新写入）。当前调用点（`EcosystemCli` 构造 web server 时）在启动阶段，属于潜在缺陷，但该类对外暴露了同步写入、非同步读取的接口，一旦后续在请求线程中调用即会触发。建议改为 `synchronized Map<String, HarnessWebServer.Profile> profiles()`。
- 判断：profiles无同步而register同步，当前仅启动读取，采纳若公开并发使用则返回同步快照；无现行并发CME证据。
- 证据：harness-cli/src/main/java/io/jharness/cli/WorkbenchWorkspaces.java:88

## OCR-0385 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/WorkbenchWorkspaces.java:83；OCR原严重度：low
- 计划：R2-01 / P2
- 原意见：启动恢复性缺陷：构造函数会逐条调用 `validate()`，其中 `Files.readAttributes`/`Files.getOwner` 对已注册根目录执行。只要任意一个已注册工作区被删除、未挂载或暂时不可访问，就会抛 `IOException` 并中止整个构造函数，导致 `serve` 完全无法启动，而且本类未提供任何 unregister 接口，运维只能在进程外手工编辑 `workspaces.json` 才能恢复。建议对“根不存在/不可达”与“存在但校验失败（链接、owner 不符）”区别处理：前者跳过并记录（保留注册表原样，便于挂载恢复），后者仍按安全策略拒绝启动。
- 判断：任一已注册根消失可中断serve构造，采纳不可用根可诊断/移除恢复途径；不能默默跳过存在但归属/链接违规的根。
- 证据：harness-cli/src/main/java/io/jharness/cli/WorkbenchWorkspaces.java:83

## OCR-0386 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/WorkbenchWorkspaces.java:63；OCR原严重度：low
- 计划：R2-01 / P3
- 原意见：边界条件 NPE：当初始工作区是文件系统根（例如以 root 用户在 `/` 下启动 `serve`）时，`metadata.workspace().getFileName()` 返回 `null`，这里直接 `.toString()` 会抛 NPE，掩盖了 `validate()` 本应返回的 `WORKSPACE_PATH_DENIED`（根路径 `getParent()==null`）。建议对 `getFileName()==null` 做兜底（使用 `workspace.toString()` 作为名称），或在生成默认条目之前先校验工作区路径。
- 判断：根路径getFileName为null会先NPE，采纳在默认名称前调用根拒绝校验；不因此允许系统根作为工作区。
- 证据：harness-cli/src/main/java/io/jharness/cli/WorkbenchWorkspaces.java:63

## OCR-0387 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M6ConfigurationTest.java:61；OCR原严重度：low
- 计划：R3-04 / P2
- 原意见：resolvedSha256 仅断言存在性：值为空串、固定常量或完全不随配置变化时本用例依旧通过，无法保证选择记录可复现。另外 ConfigurationSelection 计算哈希时已经移除了 models 目录（models 在哈希计算之后才被放回 resolved），只修改 models 定义时哈希不变，当前断言也无法发现这类完整性缺口。建议断言其为 64 位十六进制、相同输入稳定、以及不同 settings/model 定义产生不同哈希。
- 判断：resolvedSha256只存在不足以证明配置指纹，补相同选中结果稳定/不同有效设置变化；未选模型catalog变化是否影响hash按0277定义。
- 证据：harness-cli/src/test/java/io/jharness/cli/M6ConfigurationTest.java:61

## OCR-0388 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M6ConfigurationTest.java:104；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：98-122 行以及 127-170 行的 assertThrows 只断言异常类型，没有校验 `HarnessException#code()`。这些用例分别期望 CONFIG_CONFLICT（bundle 策略冲突）、CONFIG_SELECTION_NOT_FOUND（profile missing）、INVALID_CONFIG（内联 apiKey）等不同失败原因，任何无关校验提前抛错都会让用例「假通过」，也无法防御错误码回归。建议统一写成 `assertEquals("CONFIG_CONFLICT", assertThrows(...).code())` 的形式。
- 判断：多个负例仅异常类型，补各CONFIG_CONFLICT/NOT_FOUND/INVALID_CONFIG以避免其它规则提前失败。
- 证据：harness-cli/src/test/java/io/jharness/cli/M6ConfigurationTest.java:104

## OCR-0389 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M6ConfigurationTest.java:163；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：该用例（147-169 行）疑似想验证「profile settings=read-only 与 model route 的 policy=default 冲突」，但实际并未走到冲突判定：`ConfigurationSelection.resolve` 在应用 model 之前会先做 model 键白名单校验（models 条目只允许 baseUrl/model/apiKeyEnv/...，不含 policy），因此 `"policy", "default"` 会先抛出 INVALID_CONFIG。结果是即便删除 ConfigurationSelection 第 81-82 行的 CONFIG_CONFLICT 判定，本断言依然通过——用例无法覆盖其声称的冲突逻辑。请把 policy 放回 bundle/profile settings 中构造真实冲突，或改为显式断言 INVALID_CONFIG 并去掉无关的 settings。
- 判断：model条目policy不在白名单，会先INVALID_CONFIG，构造真实bundle/profile策略冲突另断CONFIG_CONFLICT。
- 证据：harness-cli/src/test/java/io/jharness/cli/M6ConfigurationTest.java:163

## OCR-0390 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M6ConfigurationTest.java:120；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：内联密钥用例只断言抛出任意 HarnessException：既未断言 code 为 INVALID_CONFIG，也未断言异常消息中不包含测试注入的密钥值（"inline"）。按「敏感信息不得回显」的要求，应校验 `ex.getMessage()` 不包含该值，否则将来有人把 values 拼进错误消息时本测试仍然通过，无法发现密钥泄漏。同时建议断言 key 未被写入 selection/values。
- 判断：内联key负例补明确INVALID_CONFIG且安全异常无哨兵，检查本例未提交/输出不含原值。
- 证据：harness-cli/src/test/java/io/jharness/cli/M6ConfigurationTest.java:120

## OCR-0391 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M6ConfigurationTest.java:39；OCR原严重度：medium
- 计划：R3-04 / P3
- 原意见：apiKeyEnv 的环境变量名白名单没有任何覆盖：这里只用了合法名字 FAST_KEY。CliConfig 第 122 行仅做 `[A-Za-z_][A-Za-z0-9_]*` 校验，因此任何已存在的进程环境变量（例如 PATH、HOME、代理/云凭据变量）都能被 apiKeyEnv 指向，并通过 `key()` 读取后发送到 baseUrl。建议补充反例（"1KEY"、"KEY-NAME"、"A B"、"PATH" 等）锁定允许的环境变量集合；若产品确实只依赖标识符格式，也应至少有用例说明该边界，避免秘钥外泄面缺少回归防护。
- 判断：补非法环境变量标识符合理；PATH等语法合法由可信配置显式引用，不加未经产品要求的JHARNESS专用白名单。
- 证据：harness-cli/src/test/java/io/jharness/cli/M6ConfigurationTest.java:39

## OCR-0392 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M6ConfigurationTest.java:188；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：176-189 行固化了「顶层 streaming=true 被静默忽略」的行为：capabilities().streaming() 只取 streamSupported，parameters() 还要求 streaming 与 streamSupported 同时为 true。本用例既不断言报错、又断言参数中没有 stream，等于把缺少校验当成预期，掩盖了配置键语义不一致（用户显式开启 streaming 却不生效、无任何提示）。若这是设计意图请补充说明性断言/注释，否则应断言配置校验失败（如 streaming 需要 streamSupported）。
- 判断：streaming意图受provider streamSupported能力门禁，禁止静默发不支持流合理；补降级说明/展示提示，不直接要求所有旧配置失败。
- 证据：harness-cli/src/test/java/io/jharness/cli/M6ConfigurationTest.java:188

## OCR-0393 · 不采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M6ConfigurationTest.java:174；OCR原严重度：low
- 计划：无
- 原意见：contextTokens 的单位是 token，compactionCharacters 的单位是字符，但 CliConfig 第 130 行把 compactionCharacters 的上界取为 contextTokens，因此本行实际固化的是「字符阈值不得超过 token 数」。这会把按 1 token≈4 字符换算的合法阈值（默认 65536 token 却只能配到 65536 字符）判为非法，等于把单位混用当作不变量。建议确认语义后按字符窗口（例如独立的字符上限或 contextTokens 的换算值）断言，并在用例中写明依据；若确为有意保守约束，也应加注释说明。
- 判断：字符数<=token数是保守限额并非错误单位换算，不能假定所有模型1token≈4字符；当前合同刻意有限制，无阈值正确性反例。
- 证据：harness-cli/src/test/java/io/jharness/cli/M6ConfigurationTest.java:174

## OCR-0394 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M8SchedulingConfigTest.java:20；OCR原严重度：low
- 计划：R3-04 / P2
- 原意见：该校验项无法单独证明“未知调度字段被拒绝”：该 Map 同时缺少 mode（实现中 mode 缺省会回退为 serial），因此只要实现改为“必须显式提供 mode”，即使未知字段校验被删除，这个用例仍会抛 INVALID_TOOL_SCHEDULING 而继续通过。建议改用只触发未知字段规则的输入，例如 `Map.of("mode", "readonly-parallel", "unknown", true)`，使失败原因唯一。
- 判断：未知字段负例可带合法明确mode避免缺mode改变规则掩盖回归，使拒绝唯一因unknown键。
- 证据：harness-cli/src/test/java/io/jharness/cli/M8SchedulingConfigTest.java:20

## OCR-0395 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M8SchedulingConfigTest.java:16；OCR原严重度：low
- 计划：R3-04 / P2
- 原意见：边界覆盖不完整：这里只验证了超上限值（maxConcurrency=9、timeoutSeconds=61）和非整数，没有覆盖允许的最大值（8 与 60）、下界（maxConcurrency=0/负数、timeoutSeconds=0）以及 timeout 的最小毫秒限制（ToolSchedulingOptions 中为 50ms）。若校验出现 off-by-one（例如把 `> 8` 写成 `>= 8`、把 `seconds < 1` 写成 `seconds < 0`），当前用例仍会全部通过。建议补充一组“边界内应被接受”的断言，以及 0/负数应被拒绝的断言。
- 判断：补并发8/timeout60成功及0/负拒绝，区分from秒单位与构造器50ms下界。
- 证据：harness-cli/src/test/java/io/jharness/cli/M8SchedulingConfigTest.java:16

## OCR-0396 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M8SchedulingConfigTest.java:45；OCR原严重度：medium
- 计划：R3-04 / P3
- 原意见：用例名声称“default remains serial”，但只断言了 maxConcurrency()==1。默认值若被改成 readonly-parallel 且并发为 1，或默认 tools 不再为空、默认 timeout 发生变化，该断言依然通过，无法捕捉默认串行语义的回归。建议直接断言整个调度配置等于 ToolSchedulingOptions.serial()（或同时断言 tools 为空、timeout 为 30s）。
- 判断：默认只并发1不能锁定工具allowlist/timeout，断言等于serial配置即可，避免镜像全部实现字段测试。
- 证据：harness-cli/src/test/java/io/jharness/cli/M8SchedulingConfigTest.java:45

## OCR-0397 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchCredentialCleanup.java:13；OCR原严重度：medium
- 计划：R1-04 / P3
- 原意见：入口失败路径不明确：`args[0]` 未做长度校验（缺少参数时只会抛 ArrayIndexOutOfBoundsException），并且 `Path.of(".build-cache").toRealPath()` 依赖进程实际工作目录（CWD）下已存在该目录，从别的目录启动会抛 NoSuchFileException。这两种情况与“无需清理”无法区分，调用方容易误判清理已执行。建议显式校验参数个数并对基准目录缺失给出可辨识的异常信息。
- 判断：清理辅助入口提前校验args/root提升诊断且必须坚持.build-cache限制；缺路径不等同无秘密，不能打印CLEAN。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchCredentialCleanup.java:13

## OCR-0398 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchCredentialCleanup.java:17；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：文件系统边界只校验了 root 本身：`Json.read` 的 NOFOLLOW_LINKS 只作用于最后一段路径，中间目录 `.jharness` 或 `.jharness/settings` 若是符号链接，仍可让读取落到 fixture 目录之外（root.resolve(...) 不会解析中间符号链接）。虽然读取大小有界，但读取非 fixture 配置再据此删除凭据会放大影响面。建议在读取前对父目录做 toRealPath 校验其仍位于 root 之内。
- 判断：清理工具会读ref后真删vault，父目录链接需验证归属再读取，不能仅root限制；在受控fixture路径构造逃逸反例。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchCredentialCleanup.java:17

## OCR-0399 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchCredentialCleanup.java:23；OCR原严重度：high
- 计划：R1-04 / P2
- 原意见：`config.get("models")` 未做空值与类型校验就强转并遍历：配置缺少 `models` 字段时强转结果为 null，foreach 抛 NPE；`models` 不是数组时抛 ClassCastException；元素不是 JSON 对象时 `Values.object(raw)` 抛 HarnessException INVALID_JSON。这些异常都发生在 `vault.update` 删除凭据之前，清理流程直接中断，合成凭据会残留且最后一行 CLEAN 不会输出，排查困难。建议显式校验类型并在缺失时给出明确提示。
- 判断：清理models直接强转可中断，校验schema并明确失败、保留清理未完成状态，不能缺字段就当无凭据。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchCredentialCleanup.java:23

## OCR-0400 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchCredentialCleanup.java:24；OCR原严重度：medium
- 计划：R1-04 / P3
- 原意见：凭据引用匹配过窄且无“已清理”证据：只识别 64 位小写十六进制的 `credentialRef`（当前生成端 `Json.hash` 确实是该格式），但只要格式变化、出现大写，或凭据未挂在 `models` 段落下（例如其他段落也引用凭据），`targets` 就会为空，`vault.update(Map.of(), List.of())` 实际不执行任何删除，随后循环不执行，仍然打印 FIXTURE_CREDENTIALS_CLEAN，形成“假阳性清理成功”。建议在 `targets.isEmpty()` 时区分“配置中确实没有需要清理的凭据”和“未识别到凭据”，至少断言匹配数量与配置中的引用数量一致（例如正则改为大小写不敏感并对未匹配的 credentialRef 直接报错）。
- 判断：当前credentialRef固定小写64hex是正确合同，未知ref应显式失败而非放宽正则盲删；无匹配可报告零目标但不能伪造已删除数。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchCredentialCleanup.java:24

## OCR-0401 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/StdioSdkServerTest.java:57；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：L57：第 6 条 `shutdown` 响应只被 `rows.length` 计数覆盖，从未断言其内容。触发条件：若 `StdioSdkServer.dispatch` 的 `shutdown` 分支回归为抛出 `INVALID_PARAMS`/`METHOD_NOT_FOUND`，或忘记把 `closing` 置为 true，此处仍会输出第 6 行、EOF 后退出码仍为 0，测试依旧通过（测试名中的 “ExecutionBarriers” 实际未验证）。建议断言 `rows[5]` 的 `id` 为 `end`、`result.closing` 为 true，并在 shutdown 之后再追加一条请求、断言不会产生第 7 行，以证明关闭后不再继续解释输入。
- 判断：shutdown只计行数未检查closing/后续请求不处理，补result和关闭后额外请求无响应验证。
- 证据：harness-cli/src/test/java/io/jharness/cli/StdioSdkServerTest.java:57

## OCR-0402 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/StdioSdkServerTest.java:31；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：L31-36：该 helper 直接对 `error`/`data` 做 `Values.object(...)` 并强转 String；当协议回归导致缺少 `error` 或 `data`、或 `error` 不是对象时，`Values.object` 会抛出 `HarnessException("INVALID_JSON")`（见 Values.java:22-24），测试将以一个与真实缺陷无关的 INVALID_JSON 异常崩溃，而不是给出可读的断言失败；同时它只取 `data.code`，既不校验 `jsonrpc=="2.0"`，也不校验错误响应的 `id` 是否与请求 id 一致，因此 id 回显、错误码映射（-32601/-32600 vs -32000）等协议回归均不可见。建议先断言结构类型、再取 code，并补充 jsonrpc 与 id 一致性断言。
- 判断：error helper的缺字段会异常使测试失败，采纳类型/层次断言提升消息，不称会假绿。
- 证据：harness-cli/src/test/java/io/jharness/cli/StdioSdkServerTest.java:31

## OCR-0403 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/StdioSdkServerTest.java:67；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：L67：只覆盖了 65537 字节的超限帧，没有覆盖“恰好 65536 字节被接受”的边界。`StdioSdkServer.frame` 的判定是 `buffer.size() >= 65536`（见 StdioSdkServer.java:331），因此合法上限是 65536 字节；若把阈值误改为 65535，65537 的用例仍会得到 `RPC_REQUEST_LIMIT` 而通过，边界回归不会被发现。建议补充 65536 字节（带换行）的用例，断言退出码 0 且错误码为 `INVALID_JSON`（而非 `RPC_REQUEST_LIMIT`），并校验 initialize 返回的 `maxRequestBytes` 与该边界一致。
- 判断：帧超限仅65537负例，应分别验证payload65536及换行不计/计规则，按frame实际字节边界构造精确正例。
- 证据：harness-cli/src/test/java/io/jharness/cli/StdioSdkServerTest.java:67

## OCR-0404 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/StdioSdkServerTest.java:69；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：L62-69：三个用例都先 `.trim()` 再解析，且只检查首个错误码。`trim()` 会丢弃响应帧的换行/空白，因而无法验证响应是以 `\n` 结尾的合法帧（服务端 StdioSdkServer.java:76 每次写入 `encoded + "\n"`）；同时若回归导致帧错误后还额外输出被解释的响应或重复错误，`error(...)` 只解析首行，测试仍会通过，这与测试名“close without interpreting bytes”不符。建议去掉 `trim()`，断言输出恰好是 1 个换行结尾的帧、且不含此前已读入的合法请求的解释结果（例如使用“合法请求行 + 截断帧”的输入，断言只有 1 条响应）。
- 判断：trim后只首error不能证明单帧/终止，断言换行、行数以及坏帧后附请求无多余响应。
- 证据：harness-cli/src/test/java/io/jharness/cli/StdioSdkServerTest.java:69

## OCR-0405 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/StdioSdkServerTest.java:86；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：L73-87：测试名声称“重复字段拒绝而不是取最后一个值”，但只覆盖了顶层 `id`，且断言过弱：未断言被拒绝请求的响应 `id` 为 null（而不是 `one`/`two`），未断言输出行数，也未验证拒绝后服务端状态未被污染。触发条件：若去重检测失效并选中后一个 `id`，该 `initialize` 会成功，随后 `ok` 请求将得到 `ALREADY_INITIALIZED`，但此处只校验 `id`，无法暴露状态污染。另外 `params` 内部的重复键完全未覆盖。建议断言 `rows.length==2`、首响应 `id` 为 null、次响应为成功的 initialize（`result.protocolVersion == "1.0"`），并新增一个 `params` 内重复键的用例。
- 判断：重复顶层id已有INVALID_JSON可证明拒绝，补嵌套params重复键及初始化状态未改变，不按所有错误都要求id=null泛化。
- 证据：harness-cli/src/test/java/io/jharness/cli/StdioSdkServerTest.java:86

## OCR-0411 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchWorkspacesTest.java:65；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：这里用 `assertThrows(Exception.class, ...)` 过宽：`NullPointerException`、`IOException`（例如 `readAttributes` 因路径不存在而抛出的异常）等与校验逻辑无关的异常同样会让断言通过，路径校验回归（相对路径/`..`/`.ssh`/UNC 被意外放行）可能被掩盖。而且 `target.resolve("..")` 只是构造出未归一化的路径（normalize 后等于 temp），并未真正覆盖越界情形。这 5 个用例都应由 validate() 以 `WORKSPACE_PATH_DENIED` 拒绝，建议断言 HarnessException 并校验 code。
- 判断：边界负例assertThrows(Exception)过宽且路径目标可能不存在，构造实际存在哨兵并锁定WORKSPACE_PATH_DENIED。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchWorkspacesTest.java:65

## OCR-0412 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchWorkspacesTest.java:69；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：只断言异常类型，没有校验 code。`RootCheck.verify()` 会先逐级做 NOFOLLOW/toRealPath 归一化检查，当 `@TempDir` 位于符号链接前缀（如 macOS 的 `/var` -> `/private/var`）或路径无法 canonicalize 时，会先抛 `WORKSPACE_PATH_DENIED`，测试照样“通过”，但根本没有走到 `WORKSPACE_OWNER_MISMATCH` 分支，owner 校验回归将无法被发现。建议传入 `target.toRealPath()` 并断言 code 为 `WORKSPACE_OWNER_MISMATCH`。
- 判断：owner mismatch必须canonical根排除先触发PATH_DENIED，断精确OWNER_MISMATCH及未写入。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchWorkspacesTest.java:69

## OCR-0413 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchWorkspacesTest.java:83；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`readAllBytes()` 没有超时，若 `mklink` 因参数解析异常而等待输入/挂起，测试会无限阻塞而不是失败；同时输出被丢弃，`assertEquals(0, ...)` 失败时没有任何诊断信息。另外 `cmd.exe /c` 会重新解析命令行，`@TempDir` 路径含空格（Windows 用户目录很常见）时 link/target 可能被拆成多个参数导致建链失败。建议使用带超时的 `waitFor` 并把进程输出拼进失败信息。
- 判断：mklink先无界readAllBytes，补有界并发排空/finally回收；ProcessBuilder的cmd quoting需实际带空格路径验证不能凭概括认定拆参。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchWorkspacesTest.java:83

## OCR-0414 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchWorkspacesTest.java:105；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：只断言 HarnessException 无法证明失败原因是“工作区被其他宿主独占”：在 `@TempDir` 位于符号链接前缀（macOS `/var` -> `/private/var`）时 validate() 会先抛 `WORKSPACE_PATH_DENIED`，测试仍然“通过”却完全没有验证探针写入；传入未 canonicalize 的 `target.toString()` 也与上面用 `target.toRealPath()` 建仓库不一致。建议传 `target.toRealPath()` 并断言 code 为 `WRITE_CONFLICT`，这样才能锁定锁冲突这一失败原因。
- 判断：占用根负例需规范根并锁定SESSION_IN_USE/对应实际归属错误码，不让路径别名先拒绝假通过。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchWorkspacesTest.java:105

## OCR-0415 · 不采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchWorkspacesTest.java:90；OCR原严重度：low
- 计划：无
- 原意见：finally 中直接 `Files.delete(link)`：一旦删除失败（Windows junction/reparse point 的清理、或被删除对象状态异常），抛出的异常会覆盖 try 中真正的断言结果/预期异常，导致失败原因错位。建议使用 `deleteIfExists` 并吞掉清理阶段的 IOException，避免影响主断言。
- 判断：清理异常不能直接吞掉再宣称通过；保留主断言并附suppressed、报清理失败才符合验收真实性，deleteIfExists也不处理权限失败。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchWorkspacesTest.java:90

## OCR-0416 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchWorkspacesTest.java:38；OCR原严重度：low
- 计划：R2-01 / P2
- 原意见：这里传入未 canonicalize 的 `b.toString()`，与 helper 中刻意使用 `initial.toRealPath()` 不一致。validate() 要求逐级 `toRealPath()` 等于 `normalize()`；在 `@TempDir` 位于符号链接前缀（如 macOS `/var` -> `/private/var`）时，这次注册会以 `WORKSPACE_PATH_DENIED` 失败，用例在该平台上必然失败（同文件第三条用例则会因异常类型断言而“假通过”）。建议统一使用 `b.toRealPath().toString()`。
- 判断：测试已有初始toRealPath但注册b用未canonical路径，补规范夹具防macOS别名假失败，不放宽产品路径规则。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchWorkspacesTest.java:38

## OCR-0427 · 不采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchSettingsTest.java:141；OCR原严重度：high
- 计划：无
- 原意见：吊销校验使用的是新建 manager，绕过了进程内凭据缓存：前面的 `reopened.capture("default")` 已把 ref→key 写入 `WorkbenchSettings.credentialCache`（WorkbenchSettings.java:455-457），而该缓存在密钥被外部删除后不会失效。触发条件：先 capture 过一次（缓存已填充），再从凭据库删除该密钥；此时只有 `manager(vault)` 会抛 API_KEY_MISSING，而存活的 manager 及基于其 snapshot 的 `runtime()`/`prepareSelection()` 仍会继续返回已删除的密钥。该测试因新建 manager 而无法暴露这种吊销后重放。建议清空 vault 后先对同一个 `reopened` 断言 `runtime(chosen)` 抛 API_KEY_MISSING，再断言新 manager 失败。
- 判断：M10.5.4-contracts第17行明确服务恢复或外部改凭据需重启；测试用新manager模拟重启核实外部删除符合约定。缓存命中不是已承诺即时吊销绕过，不纳新即时吊销功能。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchSettingsTest.java:141；docs/development/M10.5.4-contracts.md:17

## OCR-0428 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchSettingsTest.java:319；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：这两个断言在本测试中恒为真：测试从不含任何持久化文件的全新 manager 开始，非法 save 之前从未成功写过磁盘或内存状态，因此 `manager.read()` 与 `Files.exists(...)` 无论 save 是否先落盘都会通过（`save()` 先在 WorkbenchSettings.java:225 写 vault、再原子替换设置文件，写入顺序一旦回退，本测试抓不到）。触发条件：若实现改成先写 settings 再写凭据，非法/失败的保存在这里不会被发现。应先成功持久化一份合法设置并记录 `workbench.json` 字节，再断言非法保存后文件字节不变且新 `manager(vault)` 读到的状态一致（可复用本文件 unavailableNativeVault 用例的 before/after 字节比对）。另外 `assertThrows(RuntimeException.class, ...)` 过宽：NPE/ClassCastException 也能通过，应断言 `HarnessException` 的具体 code（INVALID_SETTINGS / COST_BUDGET_UNAVAILABLE），并确认异常消息不含传入的 synthetic 密钥（敏感信息脱敏校验）。
- 判断：全新状态负保存不能验证保留旧设置，补先成功后无效save并比较内存/磁盘/旧凭据；原现无文件断言仍非恒真因为错误保存可能创建。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchSettingsTest.java:319

## OCR-0429 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchSettingsTest.java:329；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：失败注入发生在既无设置文件也无任何旧密钥的初始状态，且 `assertEquals(before, manager.read())` 只比较同一 manager 的内存 state（save 抛异常前 state 未被修改，恒成立），因此无法验证两点：(1) 凭据写失败时磁盘上的设置不得被创建/修改——应重新构造 manager(vault) 读取或直接比对 workbench.json 字节；(2) 轮换既有 key 时写失败必须保留旧 key——当前 fail 场景里没有旧 key。建议先成功保存 first key，再置 `vault.fail = true` 轮换 second key，断言凭据库仍含 first、`runtime()` 仍能取到 first，且 old key 未被删除。
- 判断：vault失败只比同manager内存不足，先旧密钥成功持久再失败轮换并重开比文件与旧key仍可用。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchSettingsTest.java:329

## OCR-0430 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchSettingsTest.java:409；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：该测试直接在宿主机真实凭据库中写入并删除真实条目：在支持 Keychain/Secret Service 的开发机或 CI 上会产生系统级凭据，若进程在 `write` 与 `finally` 的 `delete` 之间被超时/崩溃中断（或 delete 本身失败），条目会永久残留；而在无凭据服务的环境 `supported()==false` 时直接 return，适配器读写逻辑完全未执行、测试却仍通过（虚假绿灯、且结果依赖执行环境）。建议用 `Assumptions.assumeTrue(vault.supported())` 显式标记跳过，并把写入/删除包在 try/finally 中保证清理；核心读写一致性应由可注入的假实现覆盖。
- 判断：真实系统vault测试需标明不支持未执行并保留清理失败证据/唯一fixture命名；不能擅自以Assumption skip替代三平台强制原生验收。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchSettingsTest.java:409

## OCR-0431 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/WorkbenchSettingsTest.java:90；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：拒绝用例集全部使用 `kind=project`，`kind=user` 分支完全没有覆盖：按 EcosystemConfig.validate 的约束，user 根必须为显式本机绝对路径，需要分别验证“相对路径被拒绝”“本机绝对路径可保存并通过 skills() 读取”，以及文档要求的 UNC/网络路径拒绝；否则这条文件系统边界回归时没有测试兜底。另外建议把 `skills/../../outside` 这类规范化后仍试图逃逸的路径加入用例（当前实现依赖 `normalize().equals()` 拦截，缺少回归防线）。
- 判断：skill roots只project负例，补user显式绝对本机根成功/相对及UNC拒绝，保持M10.5.7边界。
- 证据：harness-cli/src/test/java/io/jharness/cli/WorkbenchSettingsTest.java:90

## OCR-0534 · 部分采纳

- 位置：harness-events/src/main/java/io/jharness/events/EventFailure.java:3；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：该公开 record 缺少任何构造校验：`code`、`eventId`、`type`、`cause` 均可为 null/空白并被静默接受。项目内同类数据载体均已用 `Values.required(...)` 校验（如 `SessionEvent` 的 `eventId`、`DefaultEventBus` 的 `type`），而本类型是诊断/失败协议的边界对象，外部插件也可直接构造。一旦 `code` 或 `type` 为空，下游按 `failure.code()` / `failure.type()` 做分类、去重或重放过滤时会退化为空键匹配而误路由，而不是在构造处失败。建议加紧凑构造器做校验（`DefaultEventBus` 传入的 `code` 是常量、`eventId`/`type` 来自已校验的 `SessionEvent`，不会破坏现有调用）。
- 判断：EventFailure公开record无非空校验，采纳诊断字段约束；现生产者从已校验事件及常量填充，不认定当前空键误路由。
- 证据：harness-events/src/main/java/io/jharness/events/EventFailure.java:3

## OCR-0535 · 部分采纳

- 位置：harness-events/src/main/java/io/jharness/events/EventFailure.java:3；OCR原严重度：medium
- 计划：R1-04 / P3
- 原意见：record 自动生成的 `toString()` 会把 `cause.toString()`（即 `异常类名: message`）原样拼入输出。监听器抛出的异常 message 很可能携带请求载荷片段、环境变量值、令牌等敏感内容；默认诊断实现（`DefaultEventBus` 只打印 `code/eventId/type`）虽未泄露，但任何消费者只要 `log(failure)` 或把 failure 放进带 `{}` 占位符的日志里就会整体泄露，与“敏感信息必须脱敏”的要求冲突。建议显式重写 `toString()`，只保留 `code`、`eventId`、`type` 与 `cause.getClass().getName()`，不输出异常 message。另外生成的 `equals/hashCode` 对 `Throwable` 是按 reference 比较的，结构相同的两条失败互不相等；若后续把 `EventFailure` 作为去重集合的键（按 eventId/code 去重）会静默失效，建议显式按业务键定义相等语义并在注释中说明。
- 判断：默认toString包含cause.message可用安全摘要替代；默认diagnostics仅code/id/type无泄漏，equals按Throwable引用合法无需擅改去重语义。
- 证据：harness-events/src/main/java/io/jharness/events/EventFailure.java:3

## OCR-0553 · 采纳

- 位置：harness-events/src/main/java/io/jharness/events/DefaultEventBus.java:171；OCR原严重度：high
- 计划：R1-02 / P1
- 原意见：超时只做 `task.cancel(true)` 即抛异常，而 `serialLock` 会在异常抛出时立即释放。若监听器不响应中断（例如阻塞 IO、synchronized 等待），它会在锁释放后继续执行，与下一次 `publishSerial` 的监听器并发运行，破坏“串行有序”契约并引入状态竞争；同时该监听器的后续副作用也不再受事件总线控制。建议：超时后先把该 serialWorker 标记为“污染/不可再用”，并在释放 `serialLock` 前等待任务真正结束（或拒绝后续串行派发直到任务终止），避免顺序被破坏。
- 判断：serialWorker为每任务虚拟线程，超时cancel后立即释放serialLock，未合作旧任务可与下一派发并发；污染后拒绝新serial并有界确认退出。
- 证据：harness-events/src/main/java/io/jharness/events/DefaultEventBus.java:171

## OCR-0554 · 采纳

- 位置：harness-events/src/main/java/io/jharness/events/DefaultEventBus.java:236；OCR原严重度：high
- 计划：R1-02 / P2
- 原意见：`awaitNotifications` 未与 `close()` 互斥（`close` 只同步 `this`，本方法未同步）：在 `ensureOpen()` 通过后、`submit` 之前如果 `close()` 执行 `shutdownNow()`，这里会抛出未检查的 `RejectedExecutionException`，既不是 `HarnessException` 也没有任何诊断，调用方按协议无法处理。另外 `timeout` 未做 null/非正校验（与 `serial` 的校验不一致），null 会 NPE，0/负值会立刻变成 `NOTIFY_BARRIER_FAILED`，错误语义被误导。建议捕获 `RejectedExecutionException` 转为 `BUS_CLOSED`，并像 `serial` 一样校验 timeout。
- 判断：awaitNotifications的ensureOpen与submit未同close锁，竞态会RejectedExecutionException；统一BUS_CLOSED并校验正timeout。
- 证据：harness-events/src/main/java/io/jharness/events/DefaultEventBus.java:236

## OCR-0555 · 采纳

- 位置：harness-events/src/main/java/io/jharness/events/DefaultEventBus.java:157；OCR原严重度：high
- 计划：R1-02 / P2
- 原意见：`publishSerial` 没有与 `close()` 互斥（`close()` 只同步 `this`，本方法同步的是 `serialLock`）：外层 `ensureOpen()` 与锁内 `ensureOpen()` 通过后，另一个线程执行 `close()` 并 `serialWorker.shutdownNow()`，此处的 `submit` 会抛未检查的 `RejectedExecutionException`，已被接受的事件被静默丢弃且异常类型不符合总线协议。建议在生命周期锁（与 `close` 相同的监视器或独立的 lifecycle lock）下提交任务，并捕获 `RejectedExecutionException` 转成 `HarnessException("BUS_CLOSED", ...)`。
- 判断：publishSerial持serialLock但close持this，submit可在shutdownNow后抛未映射RejectedExecutionException，应协调提交生命周期。
- 证据：harness-events/src/main/java/io/jharness/events/DefaultEventBus.java:157

## OCR-0556 · 部分采纳

- 位置：harness-events/src/main/java/io/jharness/events/DefaultEventBus.java:259；OCR原严重度：medium
- 计划：R1-02 / P2
- 原意见：`close()` 直接 `shutdownNow()`：1) 已成功 `publish` 但仍在通知队列中的事件被直接丢弃（`shutdownNow` 的返回值未处理），既不回调也不产生 `EventFailure` 诊断，属于静默丢失，违反“观察不丢事件/失败必须可观测”的预期；2) 正在执行的通知监听器被中断，通常会把 `InterruptedException` 记成 `NOTIFY_FAILED`，产生误导性诊断；3) 完全不 `awaitTermination`，`close()` 返回后监听器仍可能在运行，资源生命周期不确定。建议 `shutdown()` 后处理/上报被丢弃的队列任务，并 `awaitTermination` 一个有界时长，超时再 `shutdownNow`。
- 判断：close shutdownNow不等待，可能遗留不合作回调，采纳有界退出和残留诊断；撤销注册后跳过排队通知是既有语义，不承诺关闭后所有观察必达。
- 证据：harness-events/src/main/java/io/jharness/events/DefaultEventBus.java:259

## OCR-0557 · 采纳

- 位置：harness-events/src/main/java/io/jharness/events/DefaultEventBus.java:17；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：`newSingleThreadExecutor()` 使用无界 LinkedBlockingQueue，且 `publish` 是 fire-and-forget、没有任何背压/上限。一旦某个通知监听器阻塞（或生产速度持续高于消费速度），队列会无界增长直至 OOM，且 `awaitNotifications` 也会一直超时失败。建议改为有界队列（`ThreadPoolExecutor` + `ArrayBlockingQueue`）并在饱和时按策略丢弃/上报诊断，或提供可配置的队列上限。
- 判断：newSingleThreadExecutor使用无界队列，publish对慢监听无限排队，改有界队列及不破坏已持久事实的饱和诊断/策略。
- 证据：harness-events/src/main/java/io/jharness/events/DefaultEventBus.java:17

## OCR-0558 · 部分采纳

- 位置：harness-events/src/main/java/io/jharness/events/DefaultEventBus.java:163；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：这里的显式 `CompletionException` 是双重包装：任务本身是 `Runnable`，`FutureTask` 已经会把监听器异常放进 `ExecutionException`，于是 `ex.getCause()` 拿到的是 `CompletionException` 而不是监听器原始异常。结果是监听器抛出的 `HarnessException`（例如 `RECURSIVE_EVENT`）异常码被埋两层，调用方无法按协议识别原始失败原因。建议删除该 try/catch，让 `FutureTask` 直接包装，并在 `ExecutionException` 分支中通过 `ex.getCause()`（必要时再剥一层 `CompletionException`）保留原始异常。
- 判断：ExecutionException.cause为显式CompletionException会增加包装，可解包保留原cause；SERIAL_FAILED对外分类是既定契约不必改成内部异常码。
- 证据：harness-events/src/main/java/io/jharness/events/DefaultEventBus.java:163

## OCR-0563 · 采纳

- 位置：harness-events/src/test/java/io/jharness/events/DefaultEventBusTest.java:12；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：测试类未声明任何 @Timeout，仓库也未配置 junit.jupiter.execution.timeout。触发条件：第 36、61 行的 publish 一旦回归为同步执行，主线程会卡在 publish 上，而用于释放监听器的 release.countDown()（第 39、65 行）位于 publish 之后永远执行不到，测试会无限挂起而不是失败；第 5、6 个用例的 invoke 同样没有超时保护。建议在类或各测试方法上添加 @Timeout，并给可能阻塞的调用设置有界等待。
- 判断：通知测试先publish再release，若publish回归阻塞会无限挂起，需独立有界@Timeout/看门狗。
- 证据：harness-events/src/test/java/io/jharness/events/DefaultEventBusTest.java:12

## OCR-0564 · 部分采纳

- 位置：harness-events/src/test/java/io/jharness/events/DefaultEventBusTest.java:42；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：两处断言都直接对异步错误列表调用 `errors.getFirst()`，既未先校验错误数量，也未校验类型。触发条件：监听器抛出 Error（DefaultEventBus 只 catch Exception）或实现回归导致错误未被上报时，测试抛 NoSuchElementException 而不是真实的断言失败；错误以其它包装形式出现时 `(HarnessException) errors.getFirst().cause()` 抛 ClassCastException，同样掩盖真实回归。建议先断言错误数量，再用 assertInstanceOf 校验 cause 类型后断言 code。
- 判断：getFirst/强转在缺诊断时本来也会失败，只采纳数量/类型断言提升定位；不称其产生假通过。
- 证据：harness-events/src/test/java/io/jharness/events/DefaultEventBusTest.java:42

## OCR-0565 · 采纳

- 位置：harness-events/src/test/java/io/jharness/events/DefaultEventBusTest.java:108；OCR原严重度：medium
- 计划：R1-02 / P2
- 原意见：第 108-111 行只断言抛出 SERIAL_TIMEOUT，未证明监听器确实已开始执行并在超时后被中断/终止。触发条件：CI 负载较高时 50ms 内 worker 可能尚未启动，cancel(true) 会直接阻止任务运行，测试仍然通过；若实现超时后不调用 cancel 或监听线程持续阻塞，泄漏的线程也不会被发现。建议在监听器入口 countDown 一个 started 闩锁，超时抛出后用 started.await(...) 证明其已运行，再用退出闩锁/中断标志证明其在超时后被终止，并把 50ms 调整为稳定的量级。
- 判断：50ms超时未确认listener开始/结束，可能只取消未运行任务，补started/exited闩锁验证真实中断及不合作后拒绝。
- 证据：harness-events/src/test/java/io/jharness/events/DefaultEventBusTest.java:108

## OCR-0566 · 采纳

- 位置：harness-events/src/test/java/io/jharness/events/DefaultEventBusTest.java:186；OCR原严重度：medium
- 计划：R1-02 / P2
- 原意见：第 186-188 行仅断言 SERIAL_FAILED，任何序列监听器异常都会得到同一结果，无法证明失败原因是递归发布被拒绝。触发条件：即使 RECURSIVE_EVENT 保护失效，递归调用最终也可能以 StackOverflowError 结束，Future.get 仍会把它包装为 SERIAL_FAILED，该用例照样通过。建议沿 cause 链断言存在 code 为 RECURSIVE_EVENT 的 HarnessException（当前实现的链为 HarnessException(SERIAL_FAILED) -> CompletionException -> HarnessException(RECURSIVE_EVENT)）。
- 判断：仅SERIAL_FAILED不能区分递归禁令还是栈溢出，检查cause链RECURSIVE_EVENT并设置整体超时。
- 证据：harness-events/src/test/java/io/jharness/events/DefaultEventBusTest.java:186

## OCR-0618 · 部分采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/DefaultServiceRegistry.java:38；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：查询路径与 register 的入参校验不对称：register 校了 type/scope 非空、key 非空白，但 find/require 没有任何校验。type 为 null 时 `type.cast(...)` 直接抛 NPE；key 为 null/空白或 scope 为 null 时不报校验错误，而是按“未命中”静默返回 empty（require 随后抛 MISSING_SERVICE），把调用方传参错误伪装成服务缺失，排查方向完全错误。建议在 find（以及 require/requireUnique）入口做同样的校验。
- 判断：注册与查询空参诊断不对称，采纳具名校验；null查不到并未使跨scope服务被放行。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/DefaultServiceRegistry.java:38

## OCR-0619 · 部分采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/DefaultServiceRegistry.java:53；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：requireUnique 同样缺少 null 校验，且后果更隐蔽：type 为 null 时冒泡过滤器条件恒为 false，found 为空后执行 `"Missing service: " + type.getName()` 时再次触发 NPE，真正的问题（非法入参）被异常信息构造失败掩盖；scope 为 null 时则静默返回 MISSING_SERVICE，与 register 对同类输入抛 IllegalArgumentException 的行为不一致。建议方法入口先做 Objects.requireNonNull(type)/requireNonNull(scope)。
- 判断：requireUnique(null)构造缺服务消息又type.getName NPE，应入口校验type/scope，属诊断加固。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/DefaultServiceRegistry.java:53

## OCR-0620 · 不采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/DefaultServiceRegistry.java:8；OCR原严重度：medium
- 计划：无
- 原意见：作用域生命周期风险：注册表只保存实现对象而不保存 Registration 句柄，也没有按 scope 批量注销的入口。任一调用方（宿主/第三方插件）注册后漏调 close（句柄丢失、启动流程异常提前返回等），entry 就会永久驻留——Key 强引用 PluginScope 的 sessionId/agentId，Value 强引用插件实现，导致插件卸载无法回滚、会话结束后仍能解析到陈旧服务并持续泄漏内存。建议按 scope 记录 registration 句柄并支持作用域级 teardown（或至少在容器卸载时统一回收）。
- 判断：PluginContext.register自动own句柄、Context.close逆序撤销，现容器已有scope回滚；直接调用底层registry的宿主必须拥有Registration，不能凭假设漏close认定现行泄漏。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/DefaultServiceRegistry.java:8

## OCR-0621 · 部分采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/ServiceRequirement.java:7；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：构造器只校验了 type，未校验 key。`DefaultServiceRegistry.register` 通过 `Values.required(key, "key")` 拒绝 null/空白 key，而本 record 允许 `new ServiceRequirement(type, "")` / `"  "`：此时 `PluginContainer.start()` 会走 `registry.require(type, "", scope)` 分支，既不是唯一解析（就绕过了 AMBIGUOUS_SERVICE 启动歧义检测），也永远匹配不到任何已注册服务，只会在启动末期抛出语义模糊的 MISSING_SERVICE。应在构造期拒绝空白 key，使非法输入在解析前失败。
- 判断：ServiceRequirement key=null有unique合法语义，空白key应早拒绝以免启动末期MISSING_SERVICE；不改变null表达唯一查询。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/ServiceRequirement.java:7

## OCR-0622 · 部分采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/ScopedToolRegistry.java:11；OCR原严重度：high
- 计划：R1-02 / P2
- 原意见：作用域校验缺口：register 直接信任调用方传入的任意 PluginScope 作为可见性键，不校验该 scope 是否属于调用者。插件通过 context.services().requireUnique(ToolRegistry.class, ...) 拿到的是这个原始注册表（services()/events() 在 PluginContainer 中都有 verify(requested) 作用域校验，而 ToolRegistry 没有等价包装），且 PluginScope 是公开 record，因此插件可以构造 PluginScope("其它 sessionId", "其它 agentId") 把工具注入到别的会话作用域，之后该会话的 resolve/visibleTools 会把它当作本会话的合法工具返回。触发条件：多会话宿主机上插件调用 registry.register(tool, new PluginScope(otherSessionId, otherAgentId))。建议为传给插件的 ToolRegistry 增加与 scopedServices 相同的 scope 校验包装，或在注册入口显式接收并校验调用者 scope，避免跨会话工具注入。
- 判断：服务注册表返回原始ToolRegistry允许跨scope参数，可加绑定视图防误用；Java插件可信不构成恶意代码隔离沙箱，必须核对同容器/多宿主共享registry再定安全优先级。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/ScopedToolRegistry.java:11

## OCR-0623 · 部分采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/ScopedToolRegistry.java:14；OCR原严重度：medium
- 计划：R1-02 / P2
- 原意见：持锁执行插件代码：register 是 synchronized 方法，此处调用的 tool.name() 由插件实现，可能在锁内做 I/O、阻塞或再次回调注册表/其他容器锁；期间所有线程的 register/resolve/visibleTools/close 都被阻塞。若插件 name() 获取另一把锁，而该锁的持有线程正在调用本注册表，则会形成死锁。建议在进入 synchronized 之前完成 tool.name() 取值与 Values.required 校验，锁内只做 containsKey/put。
- 判断：ScopedToolRegistry.register synchronized内调用tool.name，可能阻塞其它查找，可先快照元数据再锁；未证实当前第一方name死锁。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/ScopedToolRegistry.java:14

## OCR-0624 · 部分采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/ScopedToolRegistry.java:35；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：校验策略不一致导致错误被掩盖：register 对 tool/scope 做空值快速失败，但 resolve（以及第 39-41 行的 visibleTools）不校验 name/scope。resolve("x", null) 或 resolve(null, scope) 会静默返回 empty，随后 ToolRegistry.require 抛出 UNKNOWN_TOOL，把"调用方传入空 scope"这一编程错误伪装成"工具不存在"；visibleTools(null) 更会静默返回空列表，使 agent 误以为当前作用域没有任何工具。建议与 register 保持一致：Objects.requireNonNull(scope)、Values.required(name, "tool name")。
- 判断：resolve/visibleTools空scope与register不对称，补一致入参诊断；空结果本身不暴露工具。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/ScopedToolRegistry.java:35

## OCR-0625 · 不采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/FilteredToolRegistry.java:24；OCR原严重度：medium
- 计划：无
- 原意见：注册路径完全绕过了白名单，与本类注释「Un disabled tool cannot be resolved」形成不对称契约，可复现的问题有两点： 1) 未列入 `allowed` 的工具通过本注册表 `register` 仍会成功并被写入底层 `ScopedToolRegistry`（生命周期延续到 Registration.close 或作用域销毁），但同一注册表上的 `resolve`/`require` 永远返回 `empty`、`visibleTools` 也不可见；插件在注册成功后无法再通过注入的同一个 `ToolRegistry` 服务解析到自己的工具，而 `validate()` 只检查 `allowed` 反方向，不会报任何错误，配置错误（拼写、别名不一致）被静默吞掉。 2) 白名单在注册侧没有闭环：任何直接持有底层 `ScopedToolRegistry`（或未来把 delegate 暴露出去/多层包装）的消费者依旧能解析并执行这个「被禁用」的工具。 建议明确注册侧策略：要么对 `tool.name()` 不在 `allowed` 的注册给出显式结果（拒绝并抛 `HarnessException`，或至少记录），要么在类注释/接口中显式声明「注册不受白名单约束，且底层 delegate 不得外泄」，避免出现注册成功却半可用的静默状态。
- 判断：FilteredToolRegistry控制解析/可见性而允许所有插件完成注册，是按enabledTools禁用工具的正常装配方式；注册拒绝会使禁用一项工具导致整个插件启动失败。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/FilteredToolRegistry.java:24

## OCR-0626 · 部分采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/FilteredToolRegistry.java:16；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：`validate` 校验的是 `delegate.resolve(name, scope)`，而模型实际可见的暴露路径是 `visibleTools(scope)` 再按 `AgentTool.name()` 过滤（第 34-38 行），两条路径并不等价：当 delegate 支持别名（解析名 ≠ `tool.name()`）、工具注册后 `name()` 发生变化，或本注册表外面还套了一层过滤装饰器时，配置里的名字可以通过 `validate`（甚至校验通过后仍返回 empty 被误判为缺失），但模型永远看不到该工具；反过来也可能出现「可见但按该名字不可解析」。触发条件只需替换/包装 delegate 实现即可复现（本类是 public 且接受任意 `ToolRegistry`）。建议按实际暴露路径校验，例如用 `delegate.visibleTools(scope)` 的 `name()` 集合判断 `allowed` 是否被完全覆盖： ```java var visible = delegate.visibleTools(scope).stream().map(AgentTool::name).collect(Collectors.toSet()); for (String name : allowed)     if (!visible.contains(name))         throw new HarnessException("CONFIG_TOOL_UNAVAILABLE", "Configured tool is not registered: " + name); ```
- 判断：任意delegate若允许别名/可变tool.name可导致validate与visible不同，采纳稳定工具元数据契约；现ScopedToolRegistry不支持别名，未证实现行故障。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/FilteredToolRegistry.java:16

## OCR-0627 · 不采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/FilteredToolRegistry.java:7；OCR原严重度：low
- 计划：无
- 原意见：`validate` 不属于 `ToolRegistry` 接口，只能由调用方用 `instanceof FilteredToolRegistry` 显式调用（CliApplication / HarnessSdk 各一处）。这意味着任何新增的装配路径只要忘记这一步，错误配置就会退化为「模型拿到空或不完整的工具集」而不是启动期失败，且本类内部没有任何兜底。建议把校验纳入强制路径（例如在首次 `resolve`/`visibleTools` 时做一次惰性校验并缓存），或把它提升为接口方法/装饰器契约，避免校验依赖调用方自觉。
- 判断：两个真实装配入口均显式validate，首次resolve自动校验可能在其它插件尚未注册时错误失败；未来新增入口忘记不是当前缺陷。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/FilteredToolRegistry.java:7

## OCR-0628 · 采纳

- 位置：harness-policy/src/main/java/io/jharness/policy/InteractiveApprovalService.java:0；OCR原严重度：high
- 计划：R1-05 / P1
- 原意见：超时/取消/关闭的撤销不是原子的：这里只会在进入 20ms 阻塞 `get` 之前检查一次，若人工决策恰好在超时或取消发生之后、`get` 返回之前落地，`get` 会直接返回该批准，循环不再执行顶部的 `throwIfCancelled()`/`timeout` 检查，于是已撤销的提示仍然生效（返回 approved）。触发条件：`timeout` 到期瞬间或 `context.cancellation()` 触发瞬间 UI 提交通过。建议在 `get` 成功返回批准后，再次校验取消状态、`closed` 与已用时长（或把“已决策”与“已撤销”统一到一个原子状态机）后再返回批准。
- 判断：decide在20ms get成功后直接return，没有再次判断截止/取消/closed，可能返回已撤销批准；统一单次原子决策/撤销状态。
- 证据：harness-policy/src/main/java/io/jharness/policy/InteractiveApprovalService.java:声明/现有代码

## OCR-0629 · 采纳

- 位置：harness-policy/src/main/java/io/jharness/policy/InteractiveApprovalService.java:33；OCR原严重度：high
- 计划：R1-05 / P1
- 原意见：`answer` 是“先查再写”的非原子操作，而 `decide` 只在 `finally` 中移除条目：当 `decide` 已因超时/关闭/取消决定拒绝（返回值已生成）但尚未执行 `pending.remove` 时，并发的 `answer` 仍能 `complete` 该 future 并向 UI 返回 `true`，导致 UI 认为批准成功、实际工具调用已被拒绝，状态不一致。另外此处未校验 `closed`，且 `promptId` 为 null 时 `ConcurrentHashMap.get(null)` 会抛 NPE。建议改为原子仲裁（如 `pending.remove(promptId)` 成功后才 `complete`，并在 `decide` 决定拒绝时先原子移除条目再返回），同时显式拒绝 null 的 `promptId` 与已关闭状态。
- 判断：answer只get后future.complete，decide拒绝已决定但finally移除前仍可返回true，UI和执行不一致；同一状态机仲裁并明确空id拒绝。
- 证据：harness-policy/src/main/java/io/jharness/policy/InteractiveApprovalService.java:33

## OCR-0630 · 部分采纳

- 位置：harness-policy/src/main/java/io/jharness/policy/InteractiveApprovalService.java:82；OCR原严重度：medium
- 计划：R1-05 / P2
- 原意见：`close()` 只置位标志，既没有完成/拒绝 `pending` 中的 future，也没有清空 `pending`：在关闭瞬间等待中的审批线程最多要等一个 20ms 轮询周期才被释放，期间 `prompts()` 仍会把已撤销的提示暴露给 UI，`answer` 也仍可被接受（见 `answer` 未校验 `closed`）。建议关闭时幂等地遍历 `pending`，对每个 future 以拒绝原因完成并清空映射表。
- 判断：close仅置flag，pending在后续poll清理，采纳即时撤销/关闭后answer拒绝；有界20ms响应并非无限等待泄漏。
- 证据：harness-policy/src/main/java/io/jharness/policy/InteractiveApprovalService.java:82

## OCR-0631 · 采纳

- 位置：harness-policy/src/main/java/io/jharness/policy/InteractiveApprovalService.java:52；OCR原严重度：medium
- 计划：R1-05 / P2
- 原意见：影响级别描述按工具名精确匹配 `execute_command`：仓库内其它同样执行任意命令的 SYSTEM_IMPACT 工具（如 `terminal_start`、`terminal_write`，以及 MCP/插件注册的同类工具）会落到兜底的 `": workspace mutation"`，甚至带有网络访问能力的 SYSTEM_IMPACT 工具也被描述为“工作区变更”，从而低估风险、误导审批人。建议基于 `sideEffectLevel`（SYSTEM_IMPACT 统一提示任意命令/网络/进程风险）或工具元数据生成描述，而不是依赖硬编码的单个工具名；如需按名字识别，至少使用大小写不敏感的名字集合/前缀判断。
- 判断：impact只对execute_command写任意命令警告，terminal_start等SYSTEM_IMPACT降为workspace mutation，按注册副作用级别明确风险。
- 证据：harness-policy/src/main/java/io/jharness/policy/InteractiveApprovalService.java:52

## OCR-0634 · 部分采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java:100；OCR原严重度：high
- 计划：R1-02 / P1
- 原意见：`start()` 只捕获 `RuntimeException`：插件 `start()` 抛出的 `Error`（OOM、LinkageError，或 `visit()` 深递归导致的 StackOverflowError）会直接穿透，`state` 永久停留在 `STARTING`，`cleanup()` 不会执行，已启动插件的 `stop()`/`Context.close()` 全部不会发生，只能寄希望于调用方事后手动 `stop()/close()`。建议让 `Error` 也走同一条回滚路径。
- 判断：start只catch RuntimeException，LinkageError可跳过清理，应对可清理Error尽力回滚并原样再抛；不宣称OOM下必然可靠恢复。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java:100

## OCR-0635 · 采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java:102；OCR原严重度：high
- 计划：R1-02 / P1
- 原意见：生命周期事件发布没有异常隔离：`events()` 暴露的就是容器内部的 `DefaultEventBus`，一旦它已被外部 `close()`（或发布本身失败），`publish` 会抛 `BUS_CLOSED`。在 `start()` 的 catch 块中抛出会顶替原始异常并跳过下一行 `cleanup()`；在 `cleanup()`/`stop()` 循环中抛出会中断剩余插件的 `stop()`、跳过 `running.clear()`、累积失败也不会返回，导致状态为 FAILED/STOPPED 但已启动插件全部泄漏。另外无论 `plugin.stop()`/`context.close()` 是否成功都会发出 `plugin.stopped`，观察者会误判资源已释放。建议对两处 `lifecycle(...)` 发布做 try/catch 隔离，并只在 teardown 成功时发 `plugin.stopped`（失败时发带错误码的失败事实）。
- 判断：start catch里的lifecycle和cleanup循环lifecycle可BUS_CLOSED抛错，跳过剩余资源及盖原错，必须隔离观察失败并诚实记录清理状态。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java:102

## OCR-0636 · 部分采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java:188；OCR原严重度：medium
- 计划：R1-02 / P2
- 原意见：cleanup 失败时仍把状态无条件置为 `STOPPED`：`state()` 的消费者会认为停止成功、资源已释放，而实际存在插件 stop 或 context close 失败；并且由于开头 `if (state == State.STOPPED) return;`，再次 `stop()` 不会重试，失败被永久吞掉。建议清理失败时保留 FAILED（或引入 STOP_FAILED 状态），只有清理全部成功才迁移到 STOPPED。
- 判断：cleanup失败后仍STOPPED且running.clear无法再收尾，应明确FAILED/STOP_FAILED与残留资源证据；不能自动重试非幂等stop回调。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java:188

## OCR-0637 · 部分采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java:95；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：服务需求校验排在所有 `plugin.start()` 之后，与类注释“Dependency validation precedes all plugin side effects”不符：缺失/歧义服务（MISSING_SERVICE / AMBIGUOUS_SERVICE）只有在全部插件已经启动、可能完成不可补偿副作用（写文件、发起网络请求、注册外部资源）之后才被判定，回滚只能尽力 `stop()`。建议在启动前基于插件声明做可满足性预检（至少校验 requirement 的提供方已启用），或在文档中明确该失败发生在副作用之后并保证 stop 能补偿。
- 判断：类注释dependency实际指插件拓扑/版本，服务requirements只能启动后验证；补分责说明，不能静态预知运行时注册服务。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java:95

## OCR-0638 · 部分采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java:69；OCR原严重度：medium
- 计划：R1-02 / P2
- 原意见：这里把容器内部、可关闭的 `DefaultEventBus` 直接暴露给调用方：任何持有者执行 `container.events().close()` 后，容器后续所有 `lifecycle()` 发布都会抛 `BUS_CLOSED`，从而连带打断 `start()` 的失败上报与 `cleanup()` 的回滚（见上文两处未受保护的 publish）；此处也未检查容器是否已关闭。建议返回不暴露 `close()` 的 `EventBus` 视图，或在生命周期发布处统一做容错处理。
- 判断：events直接返回可close总线放大0635问题，优先令cleanup不受观察者故障阻断，是否隐藏close属于API设计加固。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java:69

## OCR-0639 · 部分采纳

- 位置：harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java:317；OCR原严重度：medium
- 计划：R1-02 / P2
- 原意见：在持有 Context 监视器期间执行插件提供的资源关闭回调（`owned.get(i).close()` 最终调用插件 `AutoCloseable.close()`）；同时 `OwnedBus.notify/serial/intercept` 是“先持 Context 锁再进 `DefaultEventBus` 的 synchronized 方法”。若某个关闭回调阻塞（例如内部调用 `publishSerial` 等待监听器，或由其它线程回调用 context 的 register/publish），该插件的所有事件与服务调用都会被长时间甚至永久阻塞，并与共享 bus 的锁顺序形成反转风险。建议锁内只置 `closed` 并快照 owned 列表，出锁后再逐个 close。
- 判断：Context.close在监视器内调用owned.close外部回调可能锁反转，采纳锁内封存、锁外关闭并保留逆序/幂等；需并发回归验证窗口。
- 证据：harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java:317

## OCR-0640 · 部分采纳

- 位置：harness-policy/src/main/java/io/jharness/policy/PolicyPlugin.java:28；OCR原严重度：medium
- 计划：R1-02 / P3
- 原意见：生命周期接管顺序反了：`Context.register` 内部会执行 `own(registry.register(...))`（见 PluginContainer.Context.register 第 265-268 行），把注册句柄作为 owned 资源；紧接着对同一个 `approvals` 再 `own`。`Context.close()` 是按 owned 列表**逆序**关闭的（PluginContainer 第 321-327 行），因此实际顺序是「先 approvals.close()，再撤销 ApprovalService 注册」。即：服务在被注销前就已经被关闭，此窗口内其他线程/后续插件若 `require(ApprovalService.class, "default")` 会拿到已关闭实例（InteractiveApprovalService 之后所有 decide 都返回 "Approval interface closed" 并 DENY）。建议先 `own` 再 `register`（使逆序关闭时先注销后关闭），或对外部注入的依赖不做接管。
- 判断：先register后own意味着先关闭对象后撤销注册，采纳注销先于资源关闭的顺序；当前审批close返回DENY，属安全关闭而非放行。
- 证据：harness-policy/src/main/java/io/jharness/policy/PolicyPlugin.java:28

## OCR-0641 · 部分采纳

- 位置：harness-policy/src/main/java/io/jharness/policy/PolicyPlugin.java:29；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：服务生命周期归属策略不一致：`ApprovalService` 按运行期类型条件接管并随 `Context.close()` 关闭，而 `PolicyEngine` 注册后从未纳入 owned 列表，容器关闭只会撤销注册、不会关闭实现（若其实现了 AutoCloseable 则资源泄漏，且「按运行期类型判断是否接管」使生命周期行为随实现类型变化）。同时强制接管调用方传入的依赖会与外部所有者形成重复关闭：CLI 中同一个 `InteractiveApprovalService` 既传给 `AgentRuntimeServices` 又传给 `PolicyPlugin`，`CliApplication.close()` 里还会再 close 一次，而 `ApprovalService` 接口并未约定 close 幂等（一旦抛异常，插件上下文关闭会失败，外层 finally 里 `approvals.close()` 失败还会跳过 `process.close()`/`model.close()`）。建议由构造函数显式声明是否接管所有权（默认不接管外部注入的依赖），并对两个服务采用一致的归属策略。
- 判断：外部注入ApprovalService条件接管而PolicyEngine不接管，应明确所有权和幂等；现InteractiveApprovalService重复close安全，不认定已泄漏。
- 证据：harness-policy/src/main/java/io/jharness/policy/PolicyPlugin.java:29

## OCR-0649 · 采纳

- 位置：harness-policy/src/test/java/io/jharness/policy/PolicyApprovalTest.java:92；OCR原严重度：medium
- 计划：R1-05 / P2
- 原意见：该断言无法区分“用户拒绝已生效”和“decide 因超时返回 false”：服务超时与 get 的等待时长同为 2 秒，CI 负载高时 decide 会先超时并返回 approved=false，使测试在审批链路实际失效时仍然通过。建议放宽等待时间并断言返回原因与操作者，确认走的是用户决定分支。
- 判断：用户拒绝测试仅approved=false且与服务相同2秒期限，可能超时假通过，断言actor/reason并用闩锁控制用户决定。
- 证据：harness-policy/src/test/java/io/jharness/policy/PolicyApprovalTest.java:92

## OCR-0650 · 部分采纳

- 位置：harness-policy/src/test/java/io/jharness/policy/PolicyApprovalTest.java:89；OCR原严重度：low
- 计划：R1-05 / P3
- 原意见：对 `git reset --hard` 断言 impact 含 "network" 与命令语义不符：该文本只是 execute_command 的通用告警（含 "use network"），并未表达本地破坏性操作。该断言既不能验证影响分类的正确性，又会随提示文案调整而误报。建议断言提示包含实际命令/副作用等级等与分类相关的信息。
- 判断：网络为任意命令通用风险不错误，采纳副作用等级/实际目标断言减少文案耦合；不声称git reset会网络。
- 证据：harness-policy/src/test/java/io/jharness/policy/PolicyApprovalTest.java:89

## OCR-0651 · 采纳

- 位置：harness-policy/src/test/java/io/jharness/policy/PolicyApprovalTest.java:115；OCR原严重度：low
- 计划：R1-05 / P2
- 原意见：取消/超时路径的断言不足：超时用例仅断言 `approved()==false`，无法区分正常超时、线程中断（CANCELLED）还是内部异常导致的 false，也检查不到超时后 prompt 是否被撤销；取消路径同样只通过 `answer` 返回 false 间接推断 prompt 已撤销，未显式验证 `prompts()` 为空，若 answer 语义变化则提示泄漏无法被发现。建议断言 reason 为 "Approval timed out"、actor 为 "system"，并在取消/超时后补 `assertTrue(approvals.prompts().isEmpty())`；30ms 在慢 CI 上偏紧，可适当放宽。
- 判断：超时/取消仅false或answer=false不足以确认原因和提示撤销，补精确reason/actor及prompts空快照。
- 证据：harness-policy/src/test/java/io/jharness/policy/PolicyApprovalTest.java:115

## OCR-0652 · 采纳

- 位置：harness-policy/src/test/java/io/jharness/policy/PolicyApprovalTest.java:134；OCR原严重度：medium
- 计划：R1-05 / P2
- 原意见：awaitPrompt 多次独立调用 prompts() 存在 TOCTOU：断言非空之后、getFirst() 之前，decide 可能已因超时/取消移除该 prompt，导致 NoSuchElementException。且这里 2 秒 deadline 与两个用例的服务超时完全相同，慢机器上正好在到期点移除 prompt，该竞态可稳定复现。建议只取一次快照并基于快照断言/返回。
- 判断：awaitPrompt多次prompts快照可在超时移除间getFirst空，取单次快照并有界等待。
- 证据：harness-policy/src/test/java/io/jharness/policy/PolicyApprovalTest.java:134

## OCR-0653 · 采纳

- 位置：harness-policy/src/test/java/io/jharness/policy/PolicyApprovalTest.java:70；OCR原严重度：low
- 计划：R1-05 / P2
- 原意见：文件系统边界仅覆盖 .env.production 这一种后缀命中；绝对路径（如 /etc/passwd）、.. 穿越、.git/.jharness 目录以及反斜杠/盘符路径均未覆盖，而这些正是 forbiddenPath 的关键分支，回归时容易漏检。建议补充对应的 DENY 用例。
- 判断：仅.env.production不足以锁定forbiddenPath的绝对/穿越/.git/.jharness等分支，补表驱动合法与拒绝对照。
- 证据：harness-policy/src/test/java/io/jharness/policy/PolicyApprovalTest.java:70

## OCR-0654 · 采纳

- 位置：harness-policy/src/test/java/io/jharness/policy/PolicyApprovalTest.java:91；OCR原严重度：low
- 计划：R1-05 / P2
- 原意见：用例名为 single user decision，但这里只验证了串行重复 answer 返回 false，没有覆盖两个线程并发 answer 时恰好一个成功（CompletableFuture.complete 的原子性）的场景，而这正是 UI 重复点击/多客户端并发审批的风险点。建议用 CyclicBarrier 配合虚拟线程并发调用 answer，断言只有一次返回 true。
- 判断：串行重复answer不能锁定并发只一次，补两个应答与撤销同时竞争的屏障测试。
- 证据：harness-policy/src/test/java/io/jharness/policy/PolicyApprovalTest.java:91

## OCR-0655 · 部分采纳

- 位置：harness-policy/src/main/java/io/jharness/policy/DefaultPolicyEngine.java:45；OCR原严重度：high
- 计划：R1-05 / P1
- 原意见：路径校验只覆盖 path/cwd 中的一个值（第 45-47 行），存在三个绕过面：(1) path 与 cwd 同时出现时只校验 path，cwd 完全不检查；(2) getOrDefault 在键存在但值为 null 时返回 null，instanceof 为假直接跳过校验；(3) 只处理 String，List/Map 形状的路径参数不检查。可复现触发：grep 工具 sideEffectLevel 为 READ_ONLY，调用 {"pattern":"=","paths":[".env"]} 时这里取不到 path/cwd，按默认 "." 判定安全，第 52 行直接 ALLOW；而 LocalWorkspace.PROTECTED 仅含 .git/.jharness，.env 会被正常打开并逐行返回匹配内容，造成凭据泄露（对照 read_file {"path":".env"} 会被本方法 DENY）。建议对 path、cwd 以及 paths/files/file/target 等所有路径型参数（含数组元素）逐项执行 forbiddenPath。
- 判断：evaluate只path或cwd，grep paths数组可绕过.env词法禁读，需按工具路径schema逐项检查；不要把任意名为target的URL当文件路径普遍拒绝。
- 证据：harness-policy/src/main/java/io/jharness/policy/DefaultPolicyEngine.java:45

## OCR-0656 · 部分采纳

- 位置：harness-policy/src/main/java/io/jharness/policy/DefaultPolicyEngine.java:60；OCR原严重度：medium
- 计划：R1-05 / P2
- 原意见：第 60-67 行是纯词法分段比较，不带平台语义：Windows 会忽略路径段末尾的点和空格，`.git.`、`.git `、`.. ` 都不等于 ".."/".git"/".env" 从而被放行；百分号编码形式 `..%2f` 也不会被识别；符号链接 link→.git/.env 无法在词法层发现（校验与使用之间存在 TOCTOU）。内置 workspace 工具在 LocalWorkspace.resolve 中补了 trim/PROTECTED/toRealPath 校验，但插件、MCP 或自定义 Tool 只经过本策略层时上述保护不生效，与本类“拒绝危险路径”的契约不符。建议先做规范化（去尾部点/空格、大小写折叠、解码后再分段）再比较，并在真正打开路径时用 toRealPath/NormalizedPath 做同源校验。
- 判断：词法策略和实际FS边界应明确分层、Windows尾点空格可规范化拒绝；百分号编码在普通文件路径无解码语义，不能盲目URL decode扩大路径解释。
- 证据：harness-policy/src/main/java/io/jharness/policy/DefaultPolicyEngine.java:60

## OCR-0657 · 不采纳

- 位置：harness-policy/src/main/java/io/jharness/policy/DefaultPolicyEngine.java:58；OCR原严重度：low
- 计划：无
- 原意见：`path.contains(":")` 会把所有含冒号的相对路径一律 DENY：Linux/macOS 上合法文件名（如 `logs/12:08:00.log`）会被拒绝；某些工具把 URL/URN 之类非文件路径放在 path 参数（如 `repo://a/b`）也会被无条件拒绝，属于误拦截导致的功能故障。建议只针对 Windows 盘符和 NTFS 备用数据流判断。
- 判断：项目采用跨平台严格安全相对路径子集，冒号也可表示Windows驱动器/ADS；Linux特殊文件名和插件URL不是既定内置path支持范围。
- 证据：harness-policy/src/main/java/io/jharness/policy/DefaultPolicyEngine.java:58

## OCR-0658 · 部分采纳

- 位置：harness-policy/src/main/java/io/jharness/policy/DefaultPolicyEngine.java:10；OCR原严重度：low
- 计划：R3-04 / P3
- 原意见：TrustedCommand 构造期只校验 cwd 非空白，argv 直接 List.copyOf：允许 cwd 为 "/"、"../x"、".env"、".git" 或空 argv。由于 evaluate 第 47 行会先拒绝这类路径，此类配置会静默永不匹配（配置错误无法在启动期暴露）；而一旦出现第 45-47 行的 cwd 未被校验情形，未校验的 cwd 就会直接进入 matches 比较。建议在紧凑构造器中拒绝危险 cwd 并校验 argv 非空。
- 判断：TrustedCommand允许永不匹配的危险cwd/空argv，采纳配置期按已有命令边界拒绝；evaluate已有路径拒绝，未证实越权命令执行。
- 证据：harness-policy/src/main/java/io/jharness/policy/DefaultPolicyEngine.java:10

## OCR-0659 · 采纳

- 位置：harness-plugin/src/test/java/io/jharness/plugin/PluginContainerTest.java:46；OCR原严重度：medium
- 计划：R1-02 / P2
- 原意见：L46 仅断言顶层 suppressed 数量为 1，无法验证“两个清理失败都被保留”。实际链路是：`start()` 的 catch 把 `cleanup()` 返回的 `CLEANUP_FAILED` 作为唯一 suppressed，而 `cleanup()` 内部把 `stop failed` 作为 `CLEANUP_FAILED` 的 cause、把资源关闭产生的 `RESOURCE_CLOSE` 作为其自身的 suppressed（PluginContainer#cleanup/#accumulate）。如果实现只保留 stop 失败而丢弃资源关闭失败（或反之），该断言仍为 1 并通过，与用例名 “KeepsCleanupFailures” 不符。建议断言 `failure.getSuppressed()[0]` 的 code 为 `CLEANUP_FAILED`、其 `getCause()` 为 stop 的 `IllegalStateException`，并继续断言其 `getSuppressed()` 中包含由 resource failure 产生的 `RESOURCE_CLOSE`。
- 判断：只顶层suppressed数量1不能确认stop和owned close两个错误都保留，检查聚合cause/suppressed链具体类型和安全码。
- 证据：harness-plugin/src/test/java/io/jharness/plugin/PluginContainerTest.java:46

## OCR-0660 · 采纳

- 位置：harness-plugin/src/test/java/io/jharness/plugin/PluginContainerTest.java:312；OCR原严重度：medium
- 计划：R1-02 / P2
- 原意见：L312-L314 的事件隔离断言等待对象与投递对象不一致，存在“恒真/假通过”：“parentHits” 的监听器是通过父插件上下文注册在 `one` 自己的 DefaultEventBus 上，而这里只向 `two` 的 bus 发布、只在 `two` 的 bus 上等待。每个 PluginContainer 在构造时各自 `new DefaultEventBus()`（PluginContainer 构造函数），因此即使 scope 隔离实现完全失效，该事件也不会到达父监听器，`assertEquals(0, parentHits.get())` 永远通过，无法防止回归；反之若以后事件改为共享/转发，仅等待 `two` 的 barrier 也不能保证 `one` 的异步回调已执行，断言可能先于回调执行而假通过。建议保存 child 插件上下文并通过其 `events()` 发布一个 sessionId 不匹配的事件断言 `SCOPE_DENIED`，或至少在断言前同时调用 `one.events().awaitNotifications(...)`。
- 判断：父监听器在one总线而只向two发布，不能验证同总线scope包装，改从子context发布错scope并断言SCOPE_DENIED。
- 证据：harness-plugin/src/test/java/io/jharness/plugin/PluginContainerTest.java:312

## OCR-0661 · 采纳

- 位置：harness-plugin/src/test/java/io/jharness/plugin/PluginContainerTest.java:333；OCR原严重度：low
- 计划：R1-02 / P2
- 原意见：L332-L335 的“配置不可变”断言实际验证的是入参 `Map.of(...)`（L344）自身的不可变性：即使 `ConfigView` 没有做防御性快照（缺少 `Values.snapshot`），`values()` 返回的仍是 `Map.of` 创建的不可变 Map，`clear()` 同样抛 `UnsupportedOperationException`，该用例无法发现实现缺陷。建议改用可变的 `HashMap` 构造 `ConfigView`，构造后修改源 Map 并断言 `c.config().get("model")` 仍返回原值，再校验 `values()` 不可变。另外此用例只为一个插件提供配置，未覆盖方法名中的 “plugin-specific”，可增加第二个插件断言其拿到的是只读空配置而不是该插件的配置。
- 判断：ConfigView入参Map.of本就不可变，clear失败不能证明防御快照；改可变源并验证源修改隔离及插件配置分离。
- 证据：harness-plugin/src/test/java/io/jharness/plugin/PluginContainerTest.java:333

## OCR-0796 · 不采纳

- 位置：harness-testkit/src/main/java/io/jharness/testkit/CheckpointRecorder.java:21；OCR原严重度：high
- 计划：无
- 原意见：边界标记先于 delegate 决策写入（第21-22、27-28、33-34行）：`boundaries.add(...)` 在 `delegate.beforeXxx(...)` 返回之前执行。若 delegate 抛出异常（例如持久化失败被包装为 HarnessException）或被替换为拒绝/跳过语义的实现，recorder 仍会留下 `model:/tool:/step:` 标记，导致基于 `boundaries()` 的调用顺序断言出现“模型请求/工具确实发生过”的假阳性。建议先调用 delegate，成功返回后再记录标记（异常路径则不记录），使标记与实际执行的步骤一致。
- 判断：CheckpointRecorder记录的是进入策略钩子的边界调用，不承诺delegate成功或副作用发生；先记录再委派保留失败现场是合理。
- 证据：harness-testkit/src/main/java/io/jharness/testkit/CheckpointRecorder.java:21

## OCR-0797 · 不采纳

- 位置：harness-testkit/src/main/java/io/jharness/testkit/CheckpointRecorder.java:9；OCR原严重度：medium
- 计划：无
- 原意见：`boundaries` 只增不减，既无容量上限也无 reset/clear 入口：同一个 recorder 在长时间会话或跨多个测试复用时会无限增长（长会话下内存无界），并且 `boundaries()` 始终返回全量历史，断言只能看到累积结果，无法在用例之间隔离。建议提供 `reset()`（或构造时传入上限并在超限时丢弃/失败），并在 Javadoc 中说明复用语义。
- 判断：testkit recorder为每测试拥有的全历史观察器，有界测试场景无需运行时截断丢失证据；未发现长期跨会话生产使用。
- 证据：harness-testkit/src/main/java/io/jharness/testkit/CheckpointRecorder.java:9

## OCR-0798 · 部分采纳

- 位置：harness-testkit/src/main/java/io/jharness/testkit/CheckpointRecorder.java:20；OCR原严重度：medium
- 计划：R1-02 / P3
- 原意见：三个 hook 均在 `synchronized` 方法体内调用外部 delegate（第20-22、26-28、32-34行）：delegate 是任意外部实现，其执行期间一直持有 recorder 的对象监视器。若 delegate 阻塞或较慢，所有 policy 调用（包括 `boundaries()`）会被串行化；若 delegate 内部再持有其它锁，而另一线程持该锁后回调本 recorder，则存在锁顺序反转导致的死锁风险。建议只在锁内完成标记写入，delegate 调用移到锁外（或明确文档化为不支持并发调用）。
- 判断：synchronized钩子中调用delegate可能阻塞快照读取，采纳明确串行测试替身约定；未有真实锁反转复现，不升为产品修复。
- 证据：harness-testkit/src/main/java/io/jharness/testkit/CheckpointRecorder.java:20

## OCR-0805 · 不采纳

- 位置：harness-testkit/src/main/java/io/jharness/testkit/EventRecorder.java:20；OCR原严重度：low
- 计划：无
- 原意见：`types()` 直接对流式读取字段 `events`（实时的 CopyOnWriteArrayList），而 `events()` 返回的是 `List.copyOf` 生成的快照。通知是由 `DefaultEventBus` 的单线程 worker 异步投递的（见 `DefaultEventBus.publish`），因此同一时刻内 `events()` 与 `types()` 可能观察到不同的长度与顺序：例如先 `events()` 取到 N 条，随后又投递一条，`types()` 便返回 N+1 条，导致断言 / 索引对应关系错乱（测试表现为偶发不稳定）。应让 `types()` 复用同一快照语义，即基于 `events()` 生成，保证两个访问器的一致性。
- 判断：CopyOnWriteArrayList.stream本身捕获不可变数组快照，两次独立观察可见不同时间点合法；没有承诺events/types跨调用原子一致。
- 证据：harness-testkit/src/main/java/io/jharness/testkit/EventRecorder.java:20

## OCR-0806 · 采纳

- 位置：harness-testkit/src/main/java/io/jharness/testkit/TemporaryProject.java:12；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：构造器在第 12 行就已创建临时目录，但随后第 13-28 行的 pom.xml / 源码写入（以及 getParent() 目录创建）若抛出 IOException，构造器直接失败，`root` 字段虽已赋值却无法通过 `close()` 回收，导致临时目录残留（在 CI / 长跑测试进程中会持续累积磁盘垃圾）。建议在构造器内对创建后的所有步骤做 try/catch，失败时尽力删除已创建的目录再抛出原始异常，例如：  ```java Path created = Files.createTempDirectory("jharness-测试 project-").toRealPath(); try {     root = created; // 需改为非 final 或使用局部变量 + 赋值前 try     ... 写入 pom/source ... } catch (IOException | RuntimeException e) {     deleteQuietly(created);     throw e; } ``` 触发条件：磁盘满、TMPDIR 权限异常、并发清理等原因导致 writeString/createDirectories 失败。
- 判断：TemporaryProject构造createTempDirectory后写文件失败无可获得close句柄，失败分支应清理已创建且确认归属的临时根。
- 证据：harness-testkit/src/main/java/io/jharness/testkit/TemporaryProject.java:12

## OCR-0807 · 部分采纳

- 位置：harness-testkit/src/main/java/io/jharness/testkit/TemporaryProject.java:36；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：清理过程是「一遇异常即整体中止」：`SimpleFileVisitor` 未覆写 `visitFileFailed`，默认实现会直接 rethrow IOException；`Files.delete(...)` 任一失败（如 Windows 上 Maven 构建产物被句柄占用、只读属性文件）也会立即中断整棵树的遍历，剩余文件与根目录全部残留，`root` 目录本身也不会被尽力删除。这与「best-effort 清理、泄漏最小化」的预期不符。建议记录第一个异常后继续遍历（返回 CONTINUE），最后再抛出/汇总，并对根本目录做兜底删除（注意仍保持 NOFOLLOW_LINKS 语义）。触发条件：任一条目删除失败或遍历被拒绝访问。
- 判断：文件删除失败可聚合后继续安全遍历减少残留，但保留首错/路径并禁止跟随链接；测试失败保留证据不是全部自动递归删除。
- 证据：harness-testkit/src/main/java/io/jharness/testkit/TemporaryProject.java:36

## OCR-0808 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/ecosystem/M7ChildEnvironmentTest.java:18；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：取消回调被写成常量 `() -> false`，因此 `ReadOnlyChildEnvironment` 中“取消后旧视图拒绝新读取”（`cancelled.getAsBoolean()` 分支）这条路径在本测试中永远不会被执行。由于关闭与取消复用同一 `CANCELLED` 错误语义，关闭场景的断言无法覆盖“父任务取消后子视图失效”这一 M7 契约点：即使实现中删除/绕过 `cancelled` 判断，本测试仍会通过。建议用可变的 `AtomicBoolean` 提供取消状态，并在 close 断言之前先验证 `cancelled=true` 时读取被拒绝（且错误码为 `CANCELLED`）。
- 判断：M7ChildEnvironment取消谓词恒false，仅close不能覆盖父取消撤销，补可控token及每个读取入口取消后拒绝。
- 证据：harness-testkit/src/test/java/io/jharness/ecosystem/M7ChildEnvironmentTest.java:18

## OCR-0809 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/ecosystem/M7ChildEnvironmentTest.java:22；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：这里只用 `HarnessException` 类型做断言，没有校验错误码。`ReadOnlyChildEnvironment` 对写操作返回 `PERMISSION_DENIED`、对关闭/取消后的读取返回 `CANCELLED`，二者都是同一个异常类型，因此若实现把写操作改成抛 `CANCELLED`（或把关闭后的读取改成抛 `PERMISSION_DENIED`），语义已错但本测试依然通过。建议对关键路径断言 `.code()`，把“只读拒绝”和“视图已失效”区分开。
- 判断：只断言HarnessException无法区分PERMISSION_DENIED与CANCELLED，补精确code与父环境仍可用证据。
- 证据：harness-testkit/src/test/java/io/jharness/ecosystem/M7ChildEnvironmentTest.java:22

## OCR-0810 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/ecosystem/M7ChildEnvironmentTest.java:31；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：关闭后只验证了 `readText` 与 `versionOf` 两个方法，而该匿名 `WorkspaceFiles` 还有 `readPage`、`list`、`search`、`grep` 等读方法，均依赖同一个 `check()`。如果后续新增读方法时漏掉 `check()`，或 `check()` 被移到部分方法之外，本测试无法发现“保留视图部分失效”的回归。建议至少再覆盖一个目录级读操作（如 `list`/`search`），确保关闭后所有读入口统一拒绝。
- 判断：close后只readText/versionOf留其它入口覆盖空隙，补代表性list/search并参数化统一check；无需为每简单委托重复实现镜像测试。
- 证据：harness-testkit/src/test/java/io/jharness/ecosystem/M7ChildEnvironmentTest.java:31

## OCR-0811 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/ecosystem/M7ChildEnvironmentTest.java:32；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：子环境关闭后只验证了父环境的 `files()` 仍可读，没有验证父环境的 `terminal()`/`processes()` 未被连带关闭，也没有覆盖“父环境先于子环境关闭”的顺序。M7 契约要求子环境只借用父资源、关闭视图不影响父容器/终端；若 `close()` 误关父终端，当前断言仍会通过。建议补充父环境进程/终端仍可用（或可控关闭）以及父先关闭时子视图拒绝新读取的用例。
- 判断：父资源不得被子close连带关闭是合理契约测试，补受控process/terminal桩计数，避免真实任意进程测试膨胀。
- 证据：harness-testkit/src/test/java/io/jharness/ecosystem/M7ChildEnvironmentTest.java:32

## OCR-0812 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/CrashWriteProbe.java:14；OCR原严重度：low
- 计划：无
- 原意见：args[0]/args[1] 未做长度校验，直接下标访问；当调用方漏传参数（例如 M2WorkspaceIntegrationTest 外的直接运行、脚本拼装参数失败）时会抛出 ArrayIndexOutOfBoundsException，退出码不是约定的 17，父测试只会看到“崩溃场景不符合预期”，容易被误解为仓库/工作区恢复逻辑缺陷。建议先校验参数个数并给出明确诊断信息。
- 判断：CrashWriteProbe是父测试精确构造argv的受控子进程，缺参数应非17退出使父测试失败；不构成产品错误或证据假通过。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/CrashWriteProbe.java:14

## OCR-0813 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/CrashWriteProbe.java:56；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：`scope` 是用字面量 `new PluginScope("s", "probe")` 构造的，`PluginScope` 构造器只做非空校验（harness-api/.../PluginScope.java:5-8），不会改写 sessionId，因此 `scope.sessionId().equals("s")` 恒为 true，该判断是永真分支，halt(17) 实际是无条件执行。这既是死代码，也会误导读者以为存在“条件崩溃”能力；同时该 scope 除了这句判断外从未参与工具管线/作用域校验，探针并没有真正覆盖插件作用域回滚路径。建议直接调用 halt(17)，或真正用 scope 走一次受作用域约束的调用后再崩溃。
- 判断：scope.sessionId恒s条件只是触发halt，简化为显式故障注入可改善可读性；不能删除用于崩溃场景的halt本身。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/CrashWriteProbe.java:56

## OCR-0814 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/CrashWriteProbe.java:49；OCR原严重度：low
- 计划：无
- 原意见：LocalWorkspace.write 的返回值（FileChange/版本）被丢弃，探针在 halt(17) 之前不校验副作用是否真的落盘；同时这里使用 overwrite=false + expectedVersion=null，隐含“工作区必须干净”的前置条件——目标文件已存在时 write 会抛 FILE_EXISTS（LocalWorkspace.write 第 409-414 行），探针会以异常退出而不是约定的 17。建议显式断言写入结果/版本，使“效果已提交但结果未结算”的崩溃点得到自证，而不是完全依赖父测试的事后检查。
- 判断：父测试创建干净fixture并核验退出17/实际effect.txt，write抛错会使探针非17退出失败，不能把前置条件当缺陷。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/CrashWriteProbe.java:49

## OCR-0815 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/CrashWriteProbe.java:37；OCR原严重度：low
- 计划：无
- 原意见：只有 `intent` 之后显式调用了 flush()，`started` 追加后没有 flush 就 halt(17)。当前 JsonlSessionRepository 的实现中 append 内部已执行 barrier.force(channel)（第 656-657 行），所以两者都持久化，flush 是冗余的；但这让“崩溃点前哪些事件已落盘”依赖 append 的实现细节，语义不对称也使测试意图变得含混。建议要么去掉冗余 flush 并在注释中说明“append 自身保证持久化”，要么在 `started` 后也显式 flush，保持崩溃状态可复现且与注释一致。
- 判断：SessionWriter契约append必须确认存储保证或throw，当前JSONL每append强制force；多一次flush不影响崩溃事实，非实际可见性缺失。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/CrashWriteProbe.java:37

## OCR-0820 · 部分采纳

- 位置：harness-testkit/src/main/java/io/jharness/testkit/FakeLlm.java:41；OCR原严重度：low
- 计划：R6-01 / P3
- 原意见：参数校验顺序问题：`Objects.requireNonNull(request)` 位于取消检查与脚本耗尽检查之后。当调用方传入 null 请求且脚本已耗尽时，会先抛出 `FAKE_EXHAUSTED`（或被取消时抛 `CANCELLED`），从而掩盖真正的调用方误用，测试中难以定位根因。建议在方法入口处先校验 request 非空，再做取消/耗尽判断，且保证校验失败时不消耗脚本响应。
- 判断：FakeLlm先取消/耗尽再request非空属于确定错误优先级，可具名校验优化但不改变请求计数；不是漏执行保护。
- 证据：harness-testkit/src/main/java/io/jharness/testkit/FakeLlm.java:41

## OCR-0821 · 部分采纳

- 位置：harness-testkit/src/main/java/io/jharness/testkit/FakeLlm.java:45；OCR原严重度：medium
- 计划：R1-06 / P2
- 原意见：FakeLlm 未覆盖 `LlmClient.completeSingleAttempt`，而 DefaultAgentLoop 在启用 modelResilience 插件时（singleSend=true）走 `core.llm().completeSingleAttempt(...)`（DefaultAgentLoop.java:152-156）；默认实现会直接抛出 `SINGLE_ATTEMPT_UNSUPPORTED`（LlmClient.java:19-23）。结果是使用该测试替身的用例无法覆盖标准单次发送/重试路径（M8ResilienceTest 只能手写匿名 LlmClient）。建议覆盖该方法并委托给 complete，或在类注释中明确该替身不支持单次发送契约。
- 判断：FakeLlm未覆写completeSingleAttempt导致不能用于韧性正例，可加明确单次替身能力；其默认拒绝正可用于不支持适配器测试，不认为生产必错。
- 证据：harness-testkit/src/main/java/io/jharness/testkit/FakeLlm.java:45

## OCR-0822 · 不采纳

- 位置：harness-testkit/src/main/java/io/jharness/testkit/FakeLlm.java:47；OCR原严重度：low
- 计划：无
- 原意见：stream 方法忽略了 cancellation 参数：即使调用方已取消，这里仍抛出 `UNSUPPORTED` 而非 `CANCELLED`，与 complete 先执行 `throwIfCancelled()` 的语义不一致，依赖取消优先级的测试会得到错误结论。建议先检查取消状态。
- 判断：FakeLlm不支持stream，直接UNSUPPORTED没有发送/执行，取消与能力拒绝优先级未承诺，无需为替身人为扩大行为。
- 证据：harness-testkit/src/main/java/io/jharness/testkit/FakeLlm.java:47

## OCR-0823 · 部分采纳

- 位置：harness-testkit/src/main/java/io/jharness/testkit/FakeLlm.java:37；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：synchronized 只保证互斥，不保证并发调用者获得脚本响应的顺序与调用发起顺序一致：哪个线程先拿到锁，就由它消费 `removeFirst()` 的响应。若测试在多线程场景下断言“第 N 个请求对应第 N 条脚本响应”，会随机失败。建议在类 Javadoc 中明确该替身仅适用于串行调用，或提供按请求显式匹配响应的 API，避免使用方误以为并发下也是确定性的。
- 判断：FakeLlm并发消费按实际锁顺序而非调用发起时间，补仅串行脚本适用说明；Java同步从未承诺公平，非实现竞态。
- 证据：harness-testkit/src/main/java/io/jharness/testkit/FakeLlm.java:37

## OCR-0824 · 采纳

- 位置：harness-testkit/src/main/java/io/jharness/testkit/FakeLlm.java:22；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：类注释中的 “captured immutable requests” 未说明记录范围：只有通过取消检查且脚本未耗尽的请求才会被加入 requests()，被取消或触发 FAKE_EXHAUSTED 的调用不会留下任何痕迹（TestkitTest.java:25-29 也按此断言）。排查“harness 实际尝试了什么请求”时容易误判，建议在 requests() 的 Javadoc 中写明这一语义。
- 判断：requests仅捕获通过取消且有脚本响应的调用，补明确尝试/受理范围，避免当作所有发送尝试审计。
- 证据：harness-testkit/src/main/java/io/jharness/testkit/FakeLlm.java:22

## OCR-0835 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M0ServiceReplacementTest.java:104；OCR原严重度：medium
- 计划：R1-02 / P2
- 原意见：Scope isolation is not exercised anywhere in this contract test. Both registries key tools by `PluginScope`, but every lookup/registration here uses the single constant `SCOPE`, so a replacement `ToolRegistry` that ignores the scope argument entirely (returning `echo` for any scope) would still pass `visibleTools(SCOPE).size() == 1`, `resolve("echo", SCOPE)` and the post-stop cleanup assertions. Since plugin-scope isolation/rollback is a required contract (other plugins must not see this tool), add negative checks with a different `PluginScope` (e.g. same sessionId, different agentId) for `visibleTools`/`resolve`, and register the same tool name under two scopes to prove per-scope registration does not collide.
- 判断：替换服务测试只有单scope，补第二scope查询/同名回滚契约有价值；现有其它scope测试存在，不称全仓无隔离覆盖。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M0ServiceReplacementTest.java:104

## OCR-0836 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M0ServiceReplacementTest.java:115；OCR原严重度：low
- 计划：R1-02 / P2
- 原意见：Post-stop cleanup is only verified for `AgentLoopFactory`. `LlmClient`, `ToolRegistry` and `SessionRepository` were registered by the plugin in the same `start()` (the latter two are also owned/handled by the container), so a leak in `PluginContainer`/`Context.close()` for those entries would go undetected while the test still passes. Assert removal for every registered service type to make the rollback contract meaningful.
- 判断：stop后只loop factory验证，补该插件其它已注册服务撤销，直接守护替换实现完整回滚。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M0ServiceReplacementTest.java:115

## OCR-0837 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M0ServiceReplacementTest.java:105；OCR原严重度：low
- 计划：R1-02 / P3
- 原意见：This assertion cannot fail for the reason it documents: the only writer of `toolExecutions` is `AgentTool.execute` (below), and no code path in this test ever calls `execute` — the fake `AgentLoop` only invokes `llm.complete(...)` and the scripted `ChatResponse` carries an empty tool-call list. Consequently `toolExecutions.get()` is always 0, so a duplicated/rogue tool execution introduced by a replacement registry or loop would not be detected here. Either drop the vacuous check or script a response containing a `ToolInvocation` and drive it through a loop that dispatches tools, so the counter can actually observe a side effect.
- 判断：fake loop不执行tool，toolExecutions=0只能证明元数据枚举不进主体；应明确测试意图，不把该负断言单独称恒真缺陷。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M0ServiceReplacementTest.java:105

## OCR-0838 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M0ServiceReplacementTest.java:133；OCR原严重度：medium
- 计划：R1-02 / P2
- 原意见：The prompt/projection contract is never actually verified: this fake composer ignores its argument and always returns a constant request, and the only assertion on LLM interaction is `llm.requests().size() == 1`. A replacement `AgentLoopFactory` that never calls `promptComposer()`, or that composes from a wrong/stale projection (e.g. `EventSessionProjection` built before `session.created` was appended), would still pass. Capture the projection passed to the composer (assert it contains the appended `session.created` event) and/or assert the resulting `ChatRequest.messages()` content, so the composer path is part of the shared contract.
- 判断：固定composer无法验证真实projection派生请求，补可观察projection输入/请求相等断言；M0可替换服务与M1重建不必强行合为同测试。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M0ServiceReplacementTest.java:133

## OCR-0839 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056WebContentTest.java:28；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：`request()` 在 body 为 null 时无条件执行 `b.GET()`，完全忽略传入的 `method`。当前用例恰好只用 GET+null body，所以还没暴露；但一旦后续用例写 `request(host, "DELETE"/"POST", ..., null, true)`，实际发出的是 GET，负向状态断言（如 405/401）可能因为请求方法被静默改写而“碰巧通过”，掩盖真实缺陷。建议显式使用方法参数。
- 判断：request helper空body一律GET确实忽略method，当前仅GET调用；使用builder.method保持未来负例真实方法。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056WebContentTest.java:28

## OCR-0840 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056WebContentTest.java:38；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`get()` 未校验响应状态码就把 body 反序列化为 Map。当服务端返回 401/404/500 的错误 JSON 时，调用方得到的是一个缺少字段的 Map：`create()` 里 `get(...).get("phase")` 恒为 null，会一直轮询到 8 秒超时后抛出无信息量的 `AssertionError("not finished")`，而不是立即暴露真实的服务端错误（例如鉴权回归、路由 404）。建议先断言 200 并把 status/body 放进失败信息。
- 判断：get helper不验HTTP状态导致错误体在phase轮询伪装超时，先断言成功及有类型phase并带安全诊断。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056WebContentTest.java:38

## OCR-0841 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056WebContentTest.java:61；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`create()` 的轮询只把 FINISHED 当作结束条件，对终态失败（如 UNKNOWN/FAILED/CANCELLED）不做任何处理：任务已经失败时仍会空转满 8 秒，随后仅抛出 `"not finished"`，把真正的 `status`/`error` 藏起来，排查成本高（而且 8 秒与 host 的 8 秒运行超时相同，负载高时容易不稳定）。建议识别终态失败立即失败，并在异常信息中带上 phase/status/error。
- 判断：create只等FINISHED，终态错误可立即报具体phase/status；host phase FINISHED通常容纳失败status，按实际schema识别不捏造phase。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056WebContentTest.java:61

## OCR-0842 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056WebContentTest.java:78；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：“无 OAuth 产品 API 鉴权”的断言只覆盖了 `/file`：本文件后续使用的 `/events`、`/content/{eventId}`、`/artifacts/{artifactId}` 都没有未带 token 的 401 断言。若这些路由的鉴权被移除，本测试套件不会发现。建议为每个暴露的路由补一条未认证请求并断言 401（且断言响应不包含内容正文/事件数据），才能覆盖整个 web 表面。
- 判断：内容/事件/产物路由可各有未认证负例，采纳防敏感内容返回的表驱动鉴权测试；不能只因本文件缺断言认定全项目鉴权未覆盖。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056WebContentTest.java:78

## OCR-0843 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056WebContentTest.java:188；OCR原严重度：low
- 计划：R2-01 / P2
- 原意见：测试直接按 `LocalArtifactStore` 的内部落盘布局写 `read-artifacts/<taskId>/<artifactId>.txt`，并把宿主服务同时跑着；这既把测试耦合到实现细节（目录/后缀改动后测试会以无关的 404/失败形式崩溃），也绕过了任何写入边界的模拟。另外这里只断言 400，无法区分是完整性校验失败（ARTIFACT_CORRUPT）还是别的 400（如 INVALID_QUERY）。建议通过受支持的测试夹具制造损坏，或在写入前后显式断言文件存在并校验响应中的错误码为 ARTIFACT_CORRUPT。
- 判断：直接篡改落盘产物正用于校验磁盘完整性，保留故障方式但锁定ARTIFACT_CORRUPT而非任意400；不必新增专用产品篡改API。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056WebContentTest.java:188

## OCR-0844 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1052HistoryTest.java:452；OCR原严重度：medium
- 计划：R6-01 / P2
- 原意见：此处（以及本方法下方对 `HarnessWebServer.verifyTask(catalog, "two", ...)` 的断言）只校验异常类型，不校验 `code()`。只要构造或校验因任何无关原因抛出 HarnessException（例如未来出现新的版本/迁移/IO 错误），测试都会通过，从而掩盖“workspace 绑定属于另一个 catalog 版本时不得被接管/不得放行”这一核心约束被破坏的问题。建议像 `futureCatalogVersionIsRejectedWithoutChangingItsBytes` 那样断言具体的错误码。
- 判断：catalog归属/绑定负例仅HarnessException，增加预期code及无任务执行断言防错误校验假通过。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1052HistoryTest.java:452

## OCR-0845 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1052HistoryTest.java:579；OCR原严重度：medium
- 计划：R6-01 / P2
- 原意见：迁移失败的断言同样只判断异常类型。注入的 force 失败只是众多可能失败路径之一：如果构造过程因其他原因（例如 catalog 已被别的句柄占用、版本判定错误）失败，测试仍会通过，而“迁移失败必须拒绝启动且不执行任务”并未真正被验证。建议断言期望的 HarnessException `code()`（迁移失败对应的具体错误码），再配合 `opens.get()==0`。
- 判断：迁移force注入测试仅HarnessException，可先确认注入命中再断言迁移失败code/opens=0。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1052HistoryTest.java:579

## OCR-0846 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1052HistoryTest.java:21；OCR原严重度：low
- 计划：R6-01 / P3
- 原意见：私有工具方法 `sha(String)` 在整个文件中只有声明、没有任何调用点（已全局检索确认），属于死代码；同时导出校验处又用 `HexFormat`+`MessageDigest` 内联重写了一遍同样的逻辑。建议删除该方法，或在导出摘要校验处复用它，避免两份实现及未使用代码。
- 判断：sha helper未用可删除/复用属低优先清理，不作为安全或功能问题。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1052HistoryTest.java:21

## OCR-0847 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1052HistoryTest.java:244；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：分页遍历使用无上限的 `do/while`：本轮 `cursor` 来自 `page.get("next")`，一旦服务端分页回归（例如 `next` 永远非空、或返回的是未更新的同一个游标），该循环不会终止，测试会挂死而不是失败；另外 `(String) page.get("next")` 在服务端返回 null 时会抛 NPE 而不是给出可读断言。建议限定最大翻页次数（如 `pages < 10`）并对 `next` 做空值/空串处理后再继续。
- 判断：分页do/while没有明确页数上限，可加硬上限防空页但next不推进；现seen.add会抓重复非空页，不把所有重复游标都说成无限挂起。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1052HistoryTest.java:244

## OCR-0848 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1052HistoryTest.java:280；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：这里直接把 `oldPage.get("next")` 拼进 URL 并断言 400，但从未断言该游标非空。服务端在无下一页时返回 `next=""`（HarnessWebServer.taskPage 第 540 行），若分页/limit 回归成“一次返回全部 65 条”，URL 会变成 `after=`；若 `next` 为 null 则变成 `after=null`。两种情况都会被服务端判为 UNKNOWN_CURSOR 返回 400，从而让这条“过期游标必须被拒绝”的断言在分页本身已损坏时误判为通过。建议先 `assertNotNull`/`assertFalse(next.isEmpty())`，再断言 400 及错误码。
- 判断：旧游标未先断言非空，after=空/字符串null的400也能误通过失效规则，先证明实际存在下一页再变更快照。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1052HistoryTest.java:280

## OCR-0849 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1052HistoryTest.java:593；OCR原严重度：low
- 计划：R6-01 / P2
- 原意见：迁移失败后只校验了备份文件内容，没有校验线上 `old.jsonl` 本身未被就地改写。若实现先原地重写 `old.jsonl` 再失败（备份仍等于迁移前内容），此测试依然会通过，后续再次启动甚至可能读到半迁移状态。建议在 `fail.set(false)` 之前额外断言 `Files.readAllBytes(root.resolve("catalog/old.jsonl"))` 与 `original` 相等。
- 判断：迁移失败仅比较备份，需同时确认线上old.jsonl原始字节不变，避免先改活跃文件后失败。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1052HistoryTest.java:593

## OCR-0850 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1052HistoryTest.java:305；OCR原严重度：low
- 计划：无
- 原意见：`assertEquals(expected, actual)` 的参数顺序写反了：第一个参数是本地计算的摘要（actual），第二个才是响应里的 `sha256`（expected）。断言结果不受影响，但失败信息会把两者颠倒，误导排查；同时此处可直接复用已声明却未使用的 `sha()` 辅助方法。建议改为 `assertEquals(sha(contentString), exported.get("sha256"))`。
- 判断：源码assertEquals第一个为本地按实际content计算的期望SHA，第二个导出响应actual，本来顺序正确；OCR将expected/actual反说。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1052HistoryTest.java:305

## OCR-0851 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1053TurnsTest.java:170；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：req() 每次调用都新建一个 HttpClient（Java 21 中每个实例都会拉起自己的 selector/线程池资源），而 settled() 以 15ms 间隔轮询最长 10s，单条用例可能产生数百个 HttpClient 且从不 close，容易造成线程/文件描述符泄漏并拖慢轮询型用例；同模块 M1052HistoryTest 等用的是复用的 static HttpClient。同时 send() 没有设置任何超时，服务端不响应时（例如 close/cancel 语义回归）本方法会无限阻塞，而 busyQueue 用例里 `a.get()`/`b.get()` 也因此没有上界，CI 表现为挂死而不是失败。建议复用类级 client 并给请求加 timeout。
- 判断：每req new HttpClient且send无超时，轮询累积资源/回归可挂住，复用受管client并给请求与整体截止。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1053TurnsTest.java:170

## OCR-0852 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1053TurnsTest.java:29；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：rejectSelection 由测试线程写入（lostOriginalCredentialPausesQueueBeforeExecutionAndCanBeRepaired 中置 true），却由服务端线程在 settings.prepareSelection 回调里读取，而这里声明为非 volatile，存在可见性竞态：工作线程可能一直读到缓存的 false，使注入的 API_KEY_MISSING 不生效，测试变成偶发通过/偶发失败。frozenFixture 只在测试线程的 host() 构建阶段读取，可以保持普通字段，但 rejectSelection 必须标为 volatile。
- 判断：rejectSelection测试线程写、请求线程读的普通boolean无happens-before，改原子/volatile或显式同步闩锁。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1053TurnsTest.java:29

## OCR-0853 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1053TurnsTest.java:552；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：故障注入以“第 3 次 force 调用”为触发点（配合 remaining.set(3)），没有锚定到 turn.started 这个开始检查点。只要准入写入（web.turn.accepted）、catalog flush 或其它并发写多/少一次 force，异常就会落在准入写入或流水线上其它位置：此时提交可能根本不存在或状态为 PAUSED 而非 UNKNOWN，failedStartCheckpointRecoversOriginalTurnAsUnknownWithoutReplay 后续的 UNKNOWN/no-replay 断言就失去意义（要么 503 断言失败，要么验证了错误的对象）。建议把注入与具体事件绑定（例如 accepted 写入后置位标志，只在随后的下一次 force 抛错），并显式断言“accepted 已落盘、turn.started 未落盘”。
- 判断：第3次force注入依赖写入序号，采纳事件锚定/命中计数与错误码核对；固定序号本身可测顺序，不无条件移除。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1053TurnsTest.java:552

## OCR-0854 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1053TurnsTest.java:590；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：这里用无参 SecretRedactor（secrets 为空）调用 verifyTask，而服务端实际配置的是 new SecretRedactor(List.of("synthetic-secret"))。verifyTask 会经由 SanitizingSessionWriter/DurableTaskCatalog 落盘事件，空配置下任何残留的 synthetic-secret 都会原样写入，等于用“未配置脱敏”的 sanitizer 去验证离线维护路径，无法覆盖真实脱敏行为；上面两处 SessionLifecycle.setArchived 也传了无参 redactor。建议抽取一个已配置的 redactor（含 synthetic-secret）复用于所有离线维护调用。
- 判断：verifyTask空redactor与实际host秘密集不齐，采纳同一sanitizer/额外秘密事件确保离线维护脱敏；空配置本身也可作为合法接口测试。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1053TurnsTest.java:590

## OCR-0855 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1053TurnsTest.java:598；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：泄密检查只遍历 root/catalog 下的 *.jsonl，而本次请求文本包含 synthetic-secret：该内容还可能落入 root/sessions（session 事件/请求日志）、root/artifacts 以及 workspace 文件，验证与导出产物也可能带出。只扫 catalog 无法证明“敏感信息未泄漏”，建议对整个 @TempDir 树（或至少 sessions/artifacts/workspace）逐文件扫描，避免脱敏回归在其它存储上漏检。
- 判断：本例只扫catalog遗漏sessions/artifacts，扩到本fixture明确生成的持久输出和stdout，避免读取无关用户文件。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1053TurnsTest.java:598

## OCR-0856 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1055QuestionsTest.java:97；OCR原严重度：medium
- 计划：R1-05 / P2
- 原意见：第 92-98 行：伪造的 `write_file` 调用被声明为 `SideEffectLevel.READ_ONLY`，而 `WorkspaceToolsPlugin.FileTool.sideEffectLevel()` 对 `write_file` 返回的是 `WORKSPACE_WRITE`。触发条件：当前第 180/187 行的 "文件未生成 + tool.completed=DENIED" 断言之所以成立，是因为 `DefaultToolPipeline.prepare` 会用解析出的工具效果重写 ToolInvocation（effect 以工具定义为准），而 `DefaultPolicyEngine.evaluate` 对非 READ_ONLY 调用返回 ASK，再由 `ApprovalService.rejectAll()` 拒绝。也就是说，这条用例实际验证的是"策略层拒绝写入"，而不是"回答不能授权写入"。一旦管线改为信任调用方声明的 effect（或测试换用直接调用 policy 的路径），该写入会被当作只读放行，用例就会在"答案授权写"的场景下依然通过。建议把 fake 调用标为 `WORKSPACE_WRITE`，让拒绝确实来自策略/审批路径。
- 判断：伪write_file声明READ_ONLY可能在等级校验前拒绝，补真实WORKSPACE_WRITE测试隔离回答不授权写入；保留伪等级负例单独验证。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1055QuestionsTest.java:97

## OCR-0857 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1055QuestionsTest.java:233；OCR原严重度：medium
- 计划：无
- 原意见：第 232-236 行：用例在持有 `sdk.questions()` 监视器的情况下 `Thread.sleep(1100)`，而题目超时只有 1 秒（`spec("single", 1)`）。触发条件：由于 `UserQuestionsPlugin.list()/respond()` 都是 `synchronized` 方法，sleep 期间 owner 线程会被阻塞在监视器外，所谓"延迟结算"完全由测试持锁制造，而不是由运行时真实时序产生；一旦实现把 TIMED_OUT 落盘挪到锁外、或把结算顺序改成先判 deadline 再检查 answer，本用例就会变红甚至偶发失败。另外固定 1100ms sleep + 1s 题目超时 + 建 SDK 的开销，在慢速 CI 上可能超过第 237 行 `running.get(3, TimeUnit.SECONDS)` 而抛 TimeoutException，掩盖真实断言。建议用 `expiresAt`（第 406 行附近的 projection 已暴露该字段）计算剩余时间并在锁外等待，或用可控时钟/闩锁替代固定 sleep。
- 判断：方法明确测试answer在deadline前提交、owner晚结算，持同一monitor延迟owner是确定性调度手段；不能因故障/竞争由测试制造而认定无效。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1055QuestionsTest.java:233

## OCR-0858 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1055QuestionsTest.java:377；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 372-381 行：800ms 总预算与 `pending()` 内部 4s 轮询组合，没有断言问题卡片确实在预算耗尽前注册成功。触发条件：`DefaultAgentLoop.run` 的 deadline 从 turn 开始（含 turn.started/step.started/首次模型调用）即开始计时，若建 SDK/首次请求较慢、预算先于 `question.requested` 落盘而耗尽，`pending(sdk)` 会以通用的 "No pending question" 失败，或者 `list()` 为空导致第 380/381 行 `getFirst()` 抛 `NoSuchElementException`，报错信息完全掩盖真实的 CANCELLED/LIMIT_REACHED 原因。建议先有界轮询断言存在 question 行（失败时打印 `snapshot().events()`），再断言其最终状态，并适当放宽 800ms。
- 判断：800ms总预算与pending4秒可能慢CI先过期，采纳确认question.requested已进入并用可控时钟/闩锁；不单纯放宽产品预算掩盖问题。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1055QuestionsTest.java:377

## OCR-0859 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1055QuestionsTest.java:260；OCR原严重度：low
- 计划：R1-04 / P2
- 原意见：第 254-261、278-279 行：脱敏断言只覆盖了 `list().toString()`、事件快照和模型请求，遗漏了同样会回到调用方/落盘的路径——`draft()` 的返回值 `saved`（第 254 行，`saved.get("text")` 从未断言）以及 `JsonlSessionRepository` 实际写入的 sessions/*.jsonl 原文；另外第 258/266/276 行的 `assertThrows` 只取了 `.code()`，丢弃了异常消息，无法发现错误信息回显明文答案。触发条件：若某次改动只对 `list()` 视图做脱敏、而 draft 返回值或持久化 payload 仍保留原文，本用例依然通过，敏感答案会经草稿接口或磁盘泄漏。建议补充对 `saved.toString()`、落盘 jsonl 文件内容以及异常 `getMessage()` 的 `contains(SECRET)` 否定断言。
- 判断：draft返回saved与原始JSONL未核验，补已知秘密在所有本测试实际输出通道的不存在断言。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1055QuestionsTest.java:260

## OCR-0860 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056DisplayTest.java:103；OCR原严重度：high
- 计划：R0-03 / P1
- 原意见：资源关闭顺序存在死锁风险：try-with-resources 按声明逆序关闭，`executor` 会先于 `sdk` 关闭，而 `Executors.newSingleThreadExecutor()` 的 `close()` 在 `shutdown()` 后会无限期 `awaitTermination`。此时 run 线程正阻塞在 `Provider.consume` 的 `while (!release.await(20ms)) token.throwIfCancelled()` 上；只要在 `provider.release.countDown()` 之前有任何断言失败（例如 `frames.await` 超时的 assertTrue、live 相关断言），release 永远不会被 countDown，SDK 也还没轮到 `close()`/`cancel()`，测试线程就会永久挂死（仓库中没有 junit-platform.properties 配置默认超时，surefire 也未设超时）。第 149-152、167-170、183-188 行是同样的写法。建议将 `executor` 声明在 `sdk` 之前（使 sdk 先关闭并触发 cancel），或在 finally 中无条件执行 `provider.release.countDown()`，并给用例加 `@Timeout`。
- 判断：display test executor先于sdk关闭，断言失败时Provider等待release、cancel尚未触发可能挂死；在executor.close前finally释放/取消且有整体上限。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056DisplayTest.java:103

## OCR-0861 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056DisplayTest.java:110；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：这条脱敏断言实际上永远不会失败，无法验证任何脱敏行为。`ModelDisplayBuffer.accept` 在 text 非空、距上次更新 <50ms 且新增字符 <16384 时会直接丢弃新帧；Provider 的两帧是同一线程内连续同步发出的，因此第二帧（唯一包含完整 SECRET 的帧）必然被节流丢弃，live.text 只会保留第一帧（`"Visible prefix "+64 个 '-'+SECRET.substring(0,8)`，再被 `SecretRedactor.streamingText` 扣掉末尾 33 字符）。也就是说 `live.toString().contains(SECRET)` 恒为 false，即使完全去掉脱敏也会通过。同时 Provider 特意在首帧发送了 SECRET 的 8 字符前缀，测试却没有任何断言。建议改成单帧发送完整 SECRET，并断言 `!live.get("text").toString().contains(SECRET.substring(0, 8))`（或直接断言精确的期望文本），使前缀泄露/脱敏回归可被捕获。
- 判断：第二帧可能被50ms节流丢弃，需控制帧间隔或时钟确保含完整secret的帧被观察，不能只检查首帧；原文必然丢弃受调度影响不能绝对化。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056DisplayTest.java:110

## OCR-0862 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056DisplayTest.java:155；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：方法名声称 "KeepsPartialDisplay"，但本用例只断言了状态为 INTERRUPTED 和 stream 调用次数，从未检查 `sdk.display().snapshot().get("text")`。若实现把失败流的部分输出清空，或保留了未脱敏的文本，本测试仍会通过。建议补充断言：部分显示文本非空（例如 startsWith("Visible prefix")），且不包含 SECRET 及其前缀。
- 判断：Interrupted只state/calls断言不足，补部分text非空/已脱敏并确认最终消息未追加。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056DisplayTest.java:155

## OCR-0863 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056DisplayTest.java:141；OCR原严重度：low
- 计划：R1-04 / P2
- 原意见：最终产物只校验了非 null，无法证明“只持久化最终、完整且已脱敏的答案”。上面对 assistant.message 只统计了条数，没有校验其内容就是 finalResponse 的最终文本（而不是首帧的部分输出），`content()` 结果中的文本/工件也未检查是否包含敏感信息。建议断言唯一 assistant.message 的内容等于期望的最终文本（其中 SECRET 已替换为占位符），并校验 `sdk.content()` 的文本输出不含 SECRET 或其前缀。
- 判断：final sdk.content仅非null，补唯一assistant.message等于完整脱敏finalResponse及产物引用内容。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056DisplayTest.java:141

## OCR-0864 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:127；OCR原严重度：low
- 计划：R6-01 / P2
- 原意见：这里只断言抛出 HarnessException，没有校验具体错误码。若 write 因路径、策略或其它原因失败，用例仍会通过，无法证明“预览不会写入 model 的 observed 版本缓存”。建议断言 `code()` 为 FILE_CONFLICT。
- 判断：预览不写observed测试仅异常类型，应锁定FILE_CONFLICT并确认原文件无变化。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:127

## OCR-0865 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:240；OCR原严重度：low
- 计划：R6-01 / P2
- 原意见：240-246 行只校验异常类型：`file(...,-1,...)` 期望 INVALID_RANGE（offset 为负），`file(...,1,2,...)` 期望“同时给 offset 和 line”的 INVALID_RANGE，`page(..., stale hash)` 期望 CONTENT_CHANGED。任何无关异常（策略拒绝、文件缺失等）都会让这些边界断言通过，建议统一断言 `.code()`。
- 判断：负offset/offset+line/过期hash均仅异常类型，分别锁定INVALID_RANGE/CONTENT_CHANGED避免无关失败蒙混。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:240

## OCR-0866 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:265；OCR原严重度：low
- 计划：R6-01 / P2
- 原意见：第 265 行应断言 `code()` 为 CANCELLED（取消需先于策略/根检查生效），第 275 行应断言 WORKSPACE_OWNER_MISMATCH（模拟根检查替换工作区）。当前只断言 HarnessException，无法区分是取消/根检查失败还是路径、策略等其它错误，因而没有验证“取消与根检查先于 inspect”的顺序。
- 判断：取消与替换根保护均只HarnessException，补CANCELLED/OWNER_MISMATCH及read未进入的计数。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:265

## OCR-0867 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:68；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：第 68-71 行的输入同时包含 other 和 SECRET，但这里只断言 other 被剔除。若 SecretRedactor(SECRET) 在链式 streamingText 中回归泄漏（SECRET 不在尾部保留窗口内），该用例仍会通过，无法验证敏感信息脱敏。建议先保存结果变量，再同时断言不含 other 和不含 SECRET。
- 判断：链式streamingText仅断言other，结果可能仍包含另一个SECRET，应保存结果同时检查两个哨兵。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:68

## OCR-0868 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:93；OCR原严重度：medium
- 计划：无
- 原意见：SECRET 从 16370 开始、长 30，而页大小 16384：第 1 页只含前 14 字符，第 2 页只含后 16 字符，因此 `first/next.toString().contains(SECRET)` 恒为 false；且当前只读取第 1 页。建议用 nextOffset 读取两页，并分别断言不含 SECRET 的对应片段（或校验拼接后 clean 且脱敏先于分页）。
- 判断：源码已经按nextOffset取第二页，并assertEquals(clean.substring(0,32768),第一页+第二页)，直接证明脱敏先分页；原文称只第一页及恒真缺陷忽略相邻强断言。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:93；harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:91

## OCR-0869 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:215；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：`service` 是第 5 参构造的无会话实例（session 为空），且 `second` 作用域从未创建，所以这里实际抛出的是 ARTIFACT_NOT_FOUND 而不是跨会话拒绝；任意 HarnessException 都能让断言通过，未验证作用域完整性。应像第 38-56 行那样使用 scoped 实例并断言 `code()` 为 ARTIFACT_DENIED，或至少在创建第二个作用域后断言具体错误码。
- 判断：未绑定service请求不存在second仅会ARTIFACT_NOT_FOUND，需独立绑定实例和真实第二会话产物断言ARTIFACT_DENIED。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:215

## OCR-0870 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:149；OCR原严重度：high
- 计划：R2-01 / P2
- 原意见：列表中的 `.git/config`、`.jharness/config`、`.aws/secret`、`../outside.txt` 都没有在磁盘上创建，`assertThrows` 只要求任意 HarnessException。尤其 `.aws` 不在 LocalWorkspace 的 PROTECTED 集合中：若 pathAllowed 对 `.aws` 的拦截回归，读取只会因文件不存在抛 FILE_NOT_FOUND，用例仍会通过；`../outside.txt` 在 resolve 的 `..` 检查回归时同样会以文件缺失通过。建议先创建这些目标文件，并断言 `code()` 为 PATH_DENIED，确保是边界拒绝而非“文件不存在”。
- 判断：禁读路径文件不存在会无关not found假通过，创建可读取哨兵并断言具体边界码/正文不返回。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:149

## OCR-0871 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:183；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：Windows 分支先 `readAllBytes()` 再 `waitFor()`，两者都没有超时：一旦 cmd.exe/mklink 挂起，CI 会无限阻塞；进程异常退出时也没有 destroyForcibly 兜底。建议使用 `waitFor(timeout)` 并在超时后强制销毁，再读取输出。另外第 197-199 行只断言任意 HarnessException，建议断言 `code()` 为 PATH_DENIED，以证明是链接边界拒绝而不是文件不存在等其它读取失败。
- 判断：Windows mklink先readAllBytes再无界waitFor，卡住时无上限/强制回收，应有界排空及finally销毁。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1056ContentTest.java:183

## OCR-0872 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M105DiagnosisTest.java:232；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：This "cancellation race" assertion is vacuous: the input contains only a single SUCCESS tool.completed event and no host-cancellation event at all, so `issues` is empty by construction. A regression where a host cancellation/turn.cancelled overrides or corrupts an already-completed result would still pass this test. Include the competing cancellation event (e.g. a `turn.cancelled`/cancellation-requested event for the same callId) alongside the SUCCESS result and assert the projection keeps the recorded outcome.
- 判断：所谓取消竞争仅一个SUCCESS completed无cancel事实，补真实cancel先后事件序列断言已完成结果保持。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M105DiagnosisTest.java:232

## OCR-0873 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M105DiagnosisTest.java:208；OCR原严重度：medium
- 计划：R4-03 / P3
- 原意见：Bounded-history verification is too weak: only the first retained callId (`call68`) is asserted, so an implementation that keeps 12 issues in the wrong order (e.g. newest-first, or arbitrary subset that happens to contain call68) would still pass. Also `issue(events)` runs `TaskDiagnosis.inspect` a second time instead of reusing `result`, so the assertion does not necessarily validate the same projection object. Assert the full retained callId list equals call68..call79 in order, derived from `result`.
- 判断：只断言第一个保留call不足以锁定12条顺序/集合，补完整有序id列表并验证截断计数。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M105DiagnosisTest.java:208

## OCR-0874 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M105DiagnosisTest.java:32；OCR原严重度：medium
- 计划：无
- 原意见：The fixture hardcodes `seq=1` and `stepId="step"` for every synthesized event. Multi-event scenarios built on this helper (timeout uncertainty, unresolved recovery associations) can therefore never detect defects that depend on monotonic event ordering or on which step a call/tool-call belongs to — all events appear at the same position in the same step. Parameterize seq/stepId (and give the `tool` helper a way to advance them) so ordering-sensitive diagnosis logic is actually exercised.
- 判断：SessionEvent.schemaVersion=1不是事件序号，固定1是正确格式；日志顺序由列表/JSONL物理顺序而非必须单调timestamp，原文混淆字段语义。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M105DiagnosisTest.java:32

## OCR-0875 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M105DiagnosisTest.java:22；OCR原严重度：low
- 计划：R1-04 / P2
- 原意见：Redaction coverage is limited to the flattened `code` field and the rendered `toString()`. Other projected fields sourced from the same payload (operation, turnId/stepId/callId, approvalReason, arguments-derived mode/timeoutMs) plus the `pending` lists are not checked, so a sanitizer regression that leaks the secret through any of those paths would go undetected. Add assertions that the whole diagnosis payload contains no secret substring (e.g. walk the map or assert on each string field).
- 判断：整个diagnosis.toString不含哨兵已覆盖所有输出字段中该完整secret，采纳新增不同字段哨兵提升路径触达；不称已有整对象检查仅code。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M105DiagnosisTest.java:22

## OCR-0876 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M105DiagnosisTest.java:170；OCR原严重度：low
- 计划：R4-03 / P2
- 原意见：The recovery associations are only asserted as flat lists, so a defect that links the unsettled tool call to the wrong step/request (e.g. attaching `unsettled` to `pending-step` instead of the assistant message's `other-step`) would still satisfy these assertions. Consider asserting the step/turn association carried by the recovery projection in addition to the ID lists.
- 判断：平面列表不能保证错误step关联，补unsettled call到确切step/request结构断言，不自动将诊断显示当执行授权。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M105DiagnosisTest.java:170

## OCR-0877 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M3CommandFixture.java:9；OCR原严重度：low
- 计划：R6-01 / P3
- 原意见：`args[0]` 未做长度/空值校验：当测试脚手架漏传 mode 参数（例如 argv 拼接回归、模式串被过滤）时会抛出 ArrayIndexOutOfBoundsException，子进程以非 0 退出并打印一段 JVM 堆栈，而不是给出“参数缺失”的明确诊断；同时任何非 "block" 的取值（"Block"、空串、拼写错误）都会静默走 success 分支，命令立即退出 0，可能让依赖“阻塞命令”的用例以超时/状态断言以外的形式失败或掩盖脚手架回归。建议显式校验参数个数并对未知模式报错退出（例如 stderr 提示 + 非 0 退出码）。
- 判断：夹具args[0]和未知mode成功可给明确usage，降低脚手架误配置排查；父测试受控argv且非预期退出已有断言。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M3CommandFixture.java:9

## OCR-0878 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M3CommandFixture.java:10；OCR原严重度：medium
- 计划：无
- 原意见：标记文件使用相对路径 `started`，其落盘位置完全取决于子进程继承的工作目录（即命令的 cwd 参数解析结果）：若脚手架把 cwd 传到非预期的目录（或被回滚/规范化前的路径），标记会写到工作区之外而用例只会表现为“等不到 started”的模糊超时；另外默认的 CREATE/TRUNCATE_EXISTING 语义会覆盖已存在的同名文件，同一目录中上一次运行或并行运行遗留的 stale `started` 会被静默复用，导致“进程已启动”的假阳性。建议改为由参数传入绝对标记路径，并使用 CREATE_NEW 使冲突显式失败，同时在退出前删除标记。
- 判断：started相对cwd是验证工作目录行为的可观测标记，各测试独立temp根，改绝对路径反而不再测cwd；未见并行共享stale标记。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M3CommandFixture.java:10

## OCR-0879 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M3CommandFixture.java:11；OCR原严重度：low
- 计划：无
- 原意见：阻塞分支硬编码 30s 且在睡眠结束后直接以退出码 0 正常结束：一方面该 fixture 的用途是“长时间运行/被超时或取消终止”的进程，若某个用例的 timeout 大于 30s，进程会自行成功退出，超时/未完成类断言可能假通过；另一方面被中断（InterruptedException）或被杀掉时 `started` 标记不会清理，与上一条的 stale 标记问题叠加。建议让阻塞时长可由参数指定（缺省值也应小于被测超时），并在 finally/关闭钩子中清理标记文件。
- 判断：30s阻塞是远超被测短超时的受控夹具，建议缺省小于被测超时方向错误；正常结束可被具体TIMEOUT/CANCELLED断言区分。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M3CommandFixture.java:11

## OCR-0880 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M10BrowserHost.java:244；OCR原严重度：low
- 计划：R6-01 / P3
- 原意见：main 未校验参数个数：缺少 args[1]/args[2] 时会在启动后写入地址/证据之前抛出 ArrayIndexOutOfBoundsException，测试侧只能看到无提示的进程崩溃，难以定位。建议显式校验 `args.length >= 3` 并给出用法；写文件前也应创建目标文件的父目录，避免因目录不存在导致 NoSuchFileException。
- 判断：浏览器host入口可提前校验3参数/输出目录提升错误定位，属测试工具UX，不是生产副作用缺陷。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M10BrowserHost.java:244

## OCR-0881 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M10BrowserHost.java:231；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：`readAllBytes()` 在 `waitFor()` 之前执行：只要 git 子进程不主动关闭输出流（锁等待、凭据/hook 提示、或输出超过管道缓冲区且无人消费等），当前线程就会一直阻塞，下面 10 秒超时永远不会生效；并且 waitFor 返回 false 时也没有 `destroy()` 子进程，会遗留孤儿进程与未关闭的流。建议先把 stdout/stderr 重定向到临时文件，再带超时 waitFor，超时则 destroyForcibly 并抛错，最后读取文件内容。
- 判断：git helper先readAllBytes使后10秒waitFor不能限读取，改有界输出排空及finally回收。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M10BrowserHost.java:231

## OCR-0882 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M10BrowserHost.java:158；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：取消夹具的 complete 实现只有在 cancellation 被取消时才退出循环，因此紧随其后的 `cancellation.throwIfCancelled()` 必然抛出，`fake.complete(...)` 永远不可达（死代码）；如果浏览器测试从未触发取消，该调用会无限期占用任务线程。同时 catch 中重新设置中断标记后立即再次 `Thread.sleep(30)`，sleep 会立刻再抛 InterruptedException，形成无休眠的忙循环，CPU 被打满。建议：循环条件同时检查中断标记，中断时直接 break，退出循环后仅在确实被取消时才抛出。
- 判断：cancel fixture循环只有token终止且中断后可能忙转，采纳整体截止/interrupt退出；fake.complete不可达是取消故障模式预期，不应强行发后续请求。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M10BrowserHost.java:158

## OCR-0883 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M10BrowserHost.java:67；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：shell 场景无条件使用 `cmd.exe`，在 Linux/macOS 的 CI 上工作区里不存在该可执行文件，execute_command 会直接启动失败，shell 夹具场景无法验证预期行为。同一分支已经对 java 可执行文件按 `os.name` 做了平台判断，shell 部分应同样按平台选择 `/bin/sh -c` 或 `cmd.exe /c`。
- 判断：shell case确实硬编码cmd.exe而其余java按平台选择，新三平台浏览器门禁需OS对应Shell但保留命令审批含义。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M10BrowserHost.java:67

## OCR-0884 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M10BrowserHost.java:55；OCR原严重度：medium
- 计划：无
- 原意见：`opened` 在 unknown 分支抛出异常之前就已经自增，因此 evidence 中的 `opened` 会把失败的 open 尝试也计入。浏览器侧若断言 opened 与实际成功会话数一致，会得到偏大的值并掩盖打开失败。建议把自增放到成功构造 HarnessSdk 之后，或在 unknown 分支抛错前不计数。
- 判断：opened计数用于统计factory构造尝试及UNKNOWN禁止重试，失败前递增是有意观测；改成功后计数会使重新尝试UNKNOWN无法被检测。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M10BrowserHost.java:55

## OCR-0885 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1057ExtensionsTest.java:59；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 58-63 行：`pid.txt` 无条件读取。若子进程在写出 pid 前就退出（例如 classpath/工作目录问题），这里抛的是 NoSuchFileException，真正的失败原因和被 trace.jsonl 记录的初始化报文都被掩盖；而且 3 秒轮询后只做 `assertFalse`，没有 `destroyForcibly()` 兜底，一旦 transport 关闭卡住就会断言失败并把子进程遗留在机器上，污染后续用例。建议先断言 pid 文件存在（附 trace 内容），超时后强制销毁并在断言消息中带上 pid。
- 判断：pid缺失应附初始化trace明确失败，确认归属后超时尽力销毁子进程，避免仅assertFalse后残留；不按未校验任意PID杀进程。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1057ExtensionsTest.java:59

## OCR-0886 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1057ExtensionsTest.java:13；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：第 13 行：`@TempDir` 提供的路径没有规范化，而 `SkillCatalog.scan` 的 `verify()` 要求 `current.toRealPath().equals(current)`（SkillCatalog.java:150）。在 macOS 上 `java.io.tmpdir` 通常是 `/var/folders/...`，真实路径是 `/private/var/folders/...`，于是 `explicitProjectAndUserRootsFrontmatterAndFrozenLoading`（第 312-325 行）会以 SKILL_PATH_DENIED 失败；更隐蔽的是 `linkedSkillRootsCannotExposeSiblingFiles`（第 86-92 行）和 `rootTraversalMetadataLimitsAndCancelledScansAreRejected`（第 366-380 行）的负向断言会因“任何 root 都被拒绝”而空过，符号链接/越界检测实际上未被验证。建议先把 base 规范化为真实路径再使用。
- 判断：SkillCatalog严格real路径，@TempDir在macOS可能别名，采纳fixture显式toRealPath；需真实CI确认，不修改产品链接拒绝。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1057ExtensionsTest.java:13

## OCR-0887 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1057ExtensionsTest.java:235；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：第 235 行：该用例把带 userinfo 的敏感 URI `https://user:secret@host/path` 交给工具，却只断言 `status() != SUCCESS`，没有断言返回的 error/message/`toString()`（以及 `fixture.history()`）中不含 `secret`。如果 URI 校验失败路径把原始输入回显到错误码细节或消息里，凭据会直接泄漏进 ToolResult 与历史记录，而这类脱敏回归在本仓库恰恰是无法被发现的。建议对该 URI 单独断言错误码，并断言错误文本与 history 都不含 `secret`。
- 判断：敏感userinfo负例仅status非SUCCESS，补工具结果/持久事件无已知secret，避免异常回显泄漏。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1057ExtensionsTest.java:235

## OCR-0888 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1057ExtensionsTest.java:283；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 266-286 行：循环最后一次赋值把 `peer.mode` 留在 `"disconnect"`，因此后面的“取消”分支即使实现完全忽略 cancellation token，也会因 peer 抛 MCP_CLOSED 而得到非 SUCCESS，`assertNotEquals(SUCCESS, ...)` 无法证明取消生效；并且 `() -> true` 在派发前就取消，方法名里的 in-flight cancellation 从未被执行。另外 disconnect 分支只断言了 `FAILED` 而未断言 error code，无法区分“请求未发出”和“已发出但响应丢失”这两种语义。建议将 mode 复位为 "normal"，断言 `ToolStatus.CANCELLED`（含 error code），并新增一个在 exchange 阻塞时才触发取消的 Peer 以覆盖在途取消。
- 判断：peer仍disconnect且预取消会使忽略cancel也失败，切换正常阻塞peer、确认已进入后取消并断言CANCELLED/不重放。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1057ExtensionsTest.java:283

## OCR-0889 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1057ExtensionsTest.java:298；OCR原严重度：medium
- 计划：R1-02 / P2
- 原意见：第 298-300 行：用例名是 *RollbackRegistrations*，但 `McpPlugin.start` 只有在 `resources/list` 与 `resources/templates/list` 都成功后才注册工具（McpPlugin.java:107-125），而 duplicate 与 cursor 两种模式都在 `discoverResources` 内部就失败，失败前注册表里没有任何条目，`visibleTools(...).isEmpty()` 恒为真，工具/资源/模板注册的回滚根本没被覆盖，实际只验证了 transport 释放（`peer.closed`）。建议增加一个在资源发现成功后再失败的 peer（例如模板成功后在后续能力/页阶段失败），并断言回滚后 mcp 前缀工具、资源与模板注册均为零。
- 判断：discovery失败前无注册，空visible仅证明启动前失败无残留，另加部分注册后失败验证回滚；不取消现discovery负例。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1057ExtensionsTest.java:298

## OCR-0890 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1057ExtensionsTest.java:93；OCR原严重度：medium
- 计划：无
- 原意见：第 71-95 行：链接创建与清理缺少平台兜底。Windows 上 `mklink /J` 在受限环境（无权限、非 NTFS、网络盘）会返回非 0，`assertEquals(0, process.waitFor(), output)` 会让用例直接失败，而不是跳过；非 Windows 平台若文件系统不支持符号链接同样抛 IOException。更关键的是 `finally { Files.delete(link); }` 对 junction 并不总是安全（部分 JDK 会跟随联接删除目标内容），且删除异常会掩盖主断言异常、`outside/secret/SKILL.md` 残留还会影响复用同一 `base` 的后续用例。建议用 `Assumptions.assumeTrue` 跳过不支持的环境，并用 `Files.deleteIfExists` 加 try/catch 保留原始异常。
- 判断：用户/阶段要求严格Windows/Linux/macOS验证不能因链接能力失败改skip；junction清理需安全但删除链接本身不是递归跟随，原文未证实误删目标。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1057ExtensionsTest.java:93

## OCR-0891 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M10WebTest.java:46；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：用例名为 Sanitized，但这里只断言 host token 不在响应中；第 28 行创建的 goal 就是敏感串 "fixture-secret-do-not-display"（M10BrowserHost 也把它配置进 SecretRedactor），却没有任何断言验证该串在 diagnosis 响应里被脱敏。若 diagnosis 通过 task summary 或事件回显了 goal 文本而未经过脱敏，本用例仍会通过。建议补充对该敏感串的缺失断言。
- 判断：diagnosis只token无goal哨兵检查，应同时断言fixture-secret-do-not-display脱敏且确有受测内容路径。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M10WebTest.java:46

## OCR-0892 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M10WebTest.java:145；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：settings 载荷使用空 apiKeys，且桩实现的 read() 只返回 {"revision":"fixture"}，save() 只累加计数而不校验入参。因此即使 GET/POST /v1/settings 回显真实 API key、或服务端丢弃了 models/selected/extensions 字段，本用例也检测不出来（与用例名 UnknownBinding 的意图不符的覆盖缺口）。建议用非空占位 key（例如 "fixture-api-key-do-not-display"），断言 GET/POST 响应体不含该值，并在 save() 中校验 input 字段被完整透传。
- 判断：HTTP路由桩不覆盖真实settings秘密生命周期，可补有哨兵返回与字段透传；真实WorkbenchSettingsTest已有独立覆盖，不把此路由用例当端到端。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M10WebTest.java:145

## OCR-0893 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M10WebTest.java:178；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：M10WebTest 的轮询未校验 HTTP 状态码且使用非空安全比较：GET 返回错误体（无 phase）时 `get("phase").equals(...)` 抛 NPE，`Set.of(...).contains(null)` 也抛 NPE；循环超时后还没有终态断言就继续后续断言。建议先断言状态码 200，用 `"FINISHED".equals(...)` 等空安全比较，并在循环后显式断言已到达终态。
- 判断：phase缺失会NPE且轮询退出无最终状态断言，统一HTTP状态/schema/有界终态检查并保留安全响应摘要。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M10WebTest.java:178

## OCR-0894 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M10WebTest.java:267；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：路径穿越用例并未真正打到服务器：request() 用 host.server.address().resolve(path)（第 202 行）构造请求 URI，而 URI.resolve 会按 RFC 2396 删除 "."/".." 段，因此列表里的 "/../pom.xml" 实际被发送为 "/pom.xml"（"/.agent"、"/?token=bad" 不受影响），这条 404 断言与服务器的路径穿越防护无关，回归时无法发现穿越缺陷。建议用不归一化的方式构造 URI（例如 URI.create(host.server.address() + path)），并用 code_search 确认服务端确实返回 404/400 而非被客户端改写。
- 判断：URI.resolve会规范/../路径，该一项不测服务器原始穿越，使用保留原始路径URL；相邻已有百分号编码400负例，非完全无覆盖。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M10WebTest.java:267

## OCR-0895 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M10WebTest.java:327；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：第二页只断言“与第一页不同”。若游标没有正确推进（next 仍是最后一页的 eventId，第二次请求返回空列表），assertNotEquals 仍会通过；若 next 已为空串（HarnessWebServer 第 1052/1060 行在无更多结果时返回 ""），第二次请求会以空游标重新从头搜索，失败原因与分页无关。建议断言第二页 events.size()==1、两页 eventId 互不相同（并校验 more/next 的推进与末页 next 为空），才能真正验证有界分页语义。
- 判断：第二页仅notEquals允许空错误页，补HTTP200、具体事件数/不交叠、more/next末页终止。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M10WebTest.java:327

## OCR-0896 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M10WebTest.java:305；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：用例名声称 report/search/submission 查询是 ReadOnly，但这里之后只断言了响应内容，没有回读任务状态来证明这些 GET/POST 查询没有改变任务（对比第一个用例在 405 之后重新校验 phase 的做法），也没有断言对 report 子资源使用写方法会被拒绝。建议在查询结束后重新 GET /v1/tasks/{id} 断言 phase 仍为终态、并在本用例内补一条 POST /v1/tasks/{id}/report 或 search 之外的写请求得到 405 的断言。
- 判断：查询只断内容可补前后任务/日志事实一致和写方法405，但已有其它只读合同测试，限聚合增加关键断言。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M10WebTest.java:305

## OCR-0897 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M4CliCrashProbe.java:38；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：崩溃触发条件把 callId 硬编码为 "crash"，且只在 SUCCESS 时触发，命中失败时既不输出任何诊断也不做兜底（直接走 next.proceed 继续执行完整 run）。一旦 M4CliIntegrationTest 里的脚本模型 call id 改名，或该 call 因审批/策略/中断而变成 FAILED/DENIED/UNKNOWN，探针就会平静地跑完整个会话并写入 tool.completed，父测试只能看到“退出码不是 23 + 日志里出现 tool.completed”，很难区分是崩溃窗口失效还是触发条件没命中。建议 callId 从系统属性/参数注入，并在 halt 前向 stderr 打印一行可核验的诊断（含 callId、status、tool），同时在未命中时也打印说明，便于定位。
- 判断：crash callId/status锚定若未命中会正常结束，父退出23断言已失败，采纳明确注入命中诊断；并非假通过。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M4CliCrashProbe.java:38

## OCR-0898 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M4CliCrashProbe.java:59；OCR原严重度：low
- 计划：无
- 原意见：Runtime.halt 不会执行 shutdown hook，也不会 flush 这里的包装 PrintStream，而 System.err 又未按 UTF-8 包装。CliApplication 在 run 流程中会用 out.print 流式输出（如 CliApplication:1189 的增量文本，无换行），这些内容会滞留在 PrintStream 缓冲里，halt 后被丢弃，导致 crash.log 丢失崩溃前的现场（这正是该探针要留证的输出）。建议 halt 之前显式 flush System.out/System.err，并让 stderr 也使用同一个 UTF-8 PrintStream，避免平台默认编码造成乱码或丢失。
- 判断：PrintStream并无独立字符缓冲承诺，System.out为既有流；halt刻意模拟硬崩溃丢失非耐久stdout，核心证据是持久JSONL，不应强制flush假装崩溃耐久。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M4CliCrashProbe.java:59

## OCR-0899 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M2WorkspaceIntegrationTest.java:216；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：第 206-217 行（以及第 325-327 行 git() 辅助方法）子进程处理有两个可复现问题：1) 死锁风险：redirectErrorStream(true) 后先 waitFor(10s) 再读输出，当子进程输出超过管道缓冲区（Linux 约 64KB）时子进程写满管道被阻塞而永不退出，waitFor 必然超时，测试以与真实缺陷无关的原因失败（JVM 启动告警栈、git 大量输出都可能触发）；2) 资源泄漏：waitFor 返回 false 或 exitValue 断言失败时没有 destroyForcibly()，泄漏的探测 JVM 继续持有 store 的 EXCLUSIVE .lock 与 root 目录，导致本次测试后续 openWriter(EXCLUSIVE) 抛异常并掩盖首个失败原因。建议把输出重定向到临时文件（或并发 drain），并用 try/finally 在超时后强制终止子进程。
- 判断：waitFor超时后assert失败无destroy可能遗留子进程，采纳finally回收/有界排空；现probe输出有界，不直接认定已发生管道死锁。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M2WorkspaceIntegrationTest.java:216

## OCR-0900 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M2WorkspaceIntegrationTest.java:105；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：第 105 行的脱敏断言是空断言（恒真），无法证明任何脱敏能力：1) ../secret.txt 在路径边界处即被 DENIED（PATH_DENIED），outside-secret 内容根本没有被读取，事件里本来就不可能出现它，即使 SecretRedactor 完全失效该断言也永远通过；2) 断言对象是 f.events()（内存 reader 视图）的 toString()，没有覆盖真正落盘的 s.jsonl、tool display/error payload 以及 diagnostics 通道，而这些正是泄漏最易发生的地方；3) 方法名声称 rejectedOutsideReadIsAnEvent，但没有任何断言证明 DENIED 被写成了事件。建议断言已落盘文件中不含该内容与绝对路径，并断言存在一条 callId=outside 的拒绝事件。
- 判断：outside-secret未被读取，断言只能证明边界不泄漏，新增真正可达工具结果哨兵验证redactor；保留原边界负例。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M2WorkspaceIntegrationTest.java:105

## OCR-0901 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M2WorkspaceIntegrationTest.java:173；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 173-189 行的失败注入断言过宽，无法证明“intent 先落盘、物理写入未发生”这一被审查的性质：1) assertThrows(HarnessException.class) 接受任意 HarnessException（例如工具未注册、参数校验失败、CONTEXT_CLOSED），注入点未命中也可能因其它异常而通过，应断言 error.code() 为 CHECKPOINT_FAILED；2) diagnostics.contains(CHECKPOINT_FAILED) 只证明有某个 force 失败，不证明失败发生在 tool.intent 的 force 上；第 131-136 行的注入钩子靠 getLast() 的字符串包含关系判断事件类型，与 Jackson 紧凑序列化格式（键名、无空格）硬耦合，序列化或事件追加批次一旦变化，注入点会静默漂移到别的事件，测试语义随之失真。建议在钩子中记录每次 force 时读到的事件类型，并断言注入确实命中 tool.intent。
- 判断：force注入只异常类型/诊断不足以锁定目标intent，增加CHECKPOINT_FAILED、注入命中与body未进入/文件未变化。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M2WorkspaceIntegrationTest.java:173

## OCR-0902 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M2WorkspaceIntegrationTest.java:256；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：第 256-258 行并没有证明方法名宣称的“不得自动重放”：1) 唯一的效果校验是 effect.txt 内容等于 committed；若循环真的重放了 crash-call，第二次写入内容完全相同，该断言依旧通过，无法区分“写了一次”和“写了两次”，应使用非幂等证据（探测进程写入可区分内容/追加计数），或断言事件流中没有新增 tool.intent / tool.completed；2) reader.events().getLast() 依赖“最后一条事件恰好是 UNKNOWN 记录”，任何尾部诊断/状态事件都会让断言以无关原因失败，建议按 reason 或类型过滤定位 UNKNOWN 事件。
- 判断：已有llm.requests空与UNKNOWN_SIDE_EFFECT不能被否认，采纳再比恢复前后tool.intent/started数量防直接工具重放。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M2WorkspaceIntegrationTest.java:256

## OCR-0903 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M2WorkspaceIntegrationTest.java:274；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：第 274-283 行的溢出/回读校验过弱且与方法名不符：1) 未校验 artifactRef 是否存在再强转，缺失时抛 NPE、类型变化时抛 ClassCastException，失败信息无法定位；2) 方法名含 AfterReopen，但从未真正“重开”会话仓储（没有新建 JsonlSessionRepository 重读会话），只是 new 了一个 LocalArtifactStore 直读磁盘，无法证明 artifact 引用在会话重开后仍可解析；3) readBack.value().toString().contains(large.txt) 只要工具有任何字段提到文件名即通过，没有校验回读内容确实是 spilled diff（截断标记、预期 diff 行），read_artifact 返回空页或错误页也可能通过。
- 判断：重新LocalArtifactStore不等于重开session，补重建repository解析artifactRef与分页完整内容/哈希；原引用存在可用类型断言改善定位。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M2WorkspaceIntegrationTest.java:274

## OCR-0904 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M2WorkspaceIntegrationTest.java:476；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：第 481-485 行 Fixture.close() 缺少异常保护：container.close() 抛异常时（PluginContainer.close() 会重抛 cleanup 失败，如 CLEANUP_FAILED），writer 与 bus 都不会被关闭；writer 持有 EXCLUSIVE .lock 并留在 OWNERS 静态表中，导致同 JVM 内后续测试无法再以 EXCLUSIVE 打开同一路径。另外构造函数中 container.start() 失败时，已打开的 writer/bus 也不会被关闭（try-with-resources 尚未接管），应在构造失败时显式回滚，并把关闭逻辑放进 try/finally。
- 判断：Fixture.close顺序container/writer/bus无独立finally，早失败跳过锁/线程释放，使用异常聚合关闭全部受管资源。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M2WorkspaceIntegrationTest.java:476

## OCR-0905 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M3RuntimeIntegrationTest.java:271；OCR原严重度：high
- 计划：R0-03 / P1
- 原意见：如果 awaitFile 超时（内部 assertTrue 失败）或 next 的 ProcessBuilder.start() 抛 IOException，内层 try/finally 不会进入，于是既不会 cancel 控制端，也不会取消 future。而 try-with-resources 中 executor 声明在最后、最先关闭，ExecutorService.close() 会无限期等待仍阻塞在 fixture("block") 上的虚拟线程，测试进程将挂死而不是给出失败。建议在方法级 finally 里先 control.cancel()/future.cancel(true)，再关闭 executor（或显式带超时地 shutdown/awaitTermination）。
- 判断：awaitFile或ProcessBuilder.start在保护finally前失败，executor先close会等未取消任务，前移取消/释放保护并整体有界。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M3RuntimeIntegrationTest.java:271

## OCR-0906 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M3RuntimeIntegrationTest.java:109；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：该 finally 位于 try-with-resources 之后，资源（含 executor）已按逆序先行关闭，因此它起不到“兜底取消”的作用：一旦前面某个断言失败，executor.close() 会先阻塞，直到 InteractiveApprovalService 的 3s 审批超时（approvals.close() 此时还没执行）才返回，而 cancel 标志尚未置位。此外 future.get(2s) 与被测的 3s 审批超时窗口过近，CI 负载高时容易先超时失败。建议把 cancel/future.cancel 放进资源关闭之前的 finally，并放宽等待预算。
- 判断：外层finally cancel晚于executor.close，失败可能等待审批3秒，采纳内层finally先取消；原文无限挂起在此3秒有界场景过度。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M3RuntimeIntegrationTest.java:109

## OCR-0907 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M3RuntimeIntegrationTest.java:57；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：这里只断言“抛出了任意 HarnessException”，无法证明被测行为：DefaultToolPipeline.Call.prepare 的 catch(Exception) 会吞掉 tool.intent 追加失败并把它变成 FAILED 结果，真正从 execute() 逃逸的 HarnessException 是随后 finish() 写 tool.completed 时对已 poison 的 writer 抛出的 WRITER_POISONED。也就是说该断言既不能证明 intent 的 fsync 失败被当作失败上报，也会被任何无关的 HarnessException（schema 校验、WRITE_CONFLICT 等）满足。建议断言具体 code（CHECKPOINT_FAILED / WRITER_POISONED）、断言注入标志为 true，并断言没有 tool.completed 事件。
- 判断：writer poison后的完成追加可能掩盖最初intent失败，补命中标志与正确失败链/无body，而不是只改异常预期到新的下游码。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M3RuntimeIntegrationTest.java:57

## OCR-0908 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M3RuntimeIntegrationTest.java:608；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：Fixture 持有 LocalProcessRunner（它在 close() 里会 terminate 所有 active 进程），但 close() 没有关闭它。当断言在命令执行中途失败（例如外部取消/超时用例）时，残留的 fixture 子进程不会被清理，会污染后续测试与进程数配额。建议在关闭 writer/bus 之前补上 process.close()。
- 判断：Fixture持有LocalProcessRunner但close未关闭，应并列关process/writer/bus以回收失败测试留下命令。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M3RuntimeIntegrationTest.java:608

## OCR-0909 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M3RuntimeIntegrationTest.java:47；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：在 fsync 屏障里读取“正在被写入”的活动会话文件并解析最后一行，风险有三点： 1) 任何读取异常（Windows 上对该文件的共享/锁定冲突、读取时机落在写入缓冲之外的异常等）都会被 JsonlSessionRepository.Writer.append 的 catch(IOException) 统一转成 CHECKPOINT_FAILED，测试无法区分“注入的强制失败”和“读取本身失败”，出现假阳性； 2) 该屏障在 openWriter 写 header 以及后续 checkpoint/flush 时同样会被调用，“最后一行是 tool.intent” 这个条件可能在错误时机重复触发； 3) 若文件此刻为空，getLast() 抛 NoSuchElementException（不是 IOException），不会被包装成 CHECKPOINT_FAILED。 建议改为一次性内存标志（只在检测到 tool.intent 之后的那次 force 抛错），并断言注入确实发生过，不要读取活动 JSONL。
- 判断：force回调读取活跃JSONL失败也可成为CHECKPOINT_FAILED，采纳独立注入命中标志/错误源断言；不把用于检查持久边界的读取一律删掉。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M3RuntimeIntegrationTest.java:47

## OCR-0910 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M3RuntimeIntegrationTest.java:168；OCR原严重度：medium
- 计划：R1-05 / P2
- 原意见：approval.decided 的断言没有按 callId 过滤，而 events() 是同一 session 的累积事件。循环第二轮（reset 调用）即使自身的拒绝决策从未被记录，也会因为第一轮 delete 调用留下的 approved=false 事件而通过，属于跨迭代假阳性。应加上 e.callId().equals(call.callId()) 过滤。
- 判断：循环审批拒绝只anyMatch approved=false未按callId，第二轮可借第一轮事件通过，补精确当前call事件关联。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M3RuntimeIntegrationTest.java:168

## OCR-0911 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M3RuntimeIntegrationTest.java:340；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：Path.of("..") 依赖测试进程的 CWD 恰好是 harness-testkit 模块目录：surefire 默认如此，但在 IDE、或在仓库根用 -f/聚合方式运行时 ".." 会指向仓库外，随后命令失败与产品缺陷无关。同一分支还硬编码 "Apache Maven 3.9.9"、要求本地已离线准备 wrapper 分发并依赖宿主输出编码。建议从 System.getProperty("basedir")/类路径推导仓库根，校验 mvnw/mvnw.cmd 存在，包装器缺失或版本不符时用 assumeTrue 跳过而不是失败。
- 判断：Path.of(..)依赖Surefire CWD，应从basedir确定repo并显式检查固定Wrapper缓存；Maven3.9.9是固定发行契约不能随意移除版本断言。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M3RuntimeIntegrationTest.java:340

## OCR-0912 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1ToolPipelineTest.java:33；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：第 17 行测试名声称验证「intent 先落盘、再进入写操作 body」，但这里的模拟 writer 只是在 flush 时置一个布尔位，既没有记录 flush 之前已 append 了哪些事件，也没有统计 flush 次数。`DefaultToolPipeline` 是先写 `tool.intent`（第 133 行）再 checkpoint/flush（第 138 行），而 body 内的 `f.types()` 直接读内存仓库，不依赖 flush；因此即使实现改成「先 flush 再 append intent」，第 44-45 行断言依旧全部通过，测试无法发现落盘顺序被破坏。建议在模拟 writer 中记录 append 的事件类型集合与 flush 次数，在 flush() 内断言 `tool.intent` 已 append，并断言 flush 恰好发生一次且早于 body。
- 判断：flush只置布尔不能证明intent已在该flush前写入，应让wrapper记录刷盘事件前缀并断言主体前durable边界。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1ToolPipelineTest.java:33

## OCR-0913 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1ToolPipelineTest.java:58；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：这里把 TIMEOUT 失败映射为 UNKNOWN 后就结束，没有验证 UNKNOWN 之后不得重放：没有用同一个 callId 再次执行并断言被拒绝（如 REPEATED_SETTLEMENT / 已结算），没有断言 `tool.intent` 不会被二次写入，也没有断言会话中最后一条 `tool.completed` 的状态同样是 UNKNOWN（若事件流里记成 SUCCESS，恢复端可能把已产生副作用的调用当成未执行而重放）。`DefaultToolPipeline` 第 231 行表明 UNKNOWN 只在 `entered && effect != READ_ONLY` 时产生，属于副作用已发生但结果未知的关键状态，建议补充重放/二次执行断言。
- 判断：补UNKNOWN持久事件及后续Loop拒绝，不能在Pipeline同callId直接第二次执行要求全局去重，因为prepared单次与跨run准入职责不同。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1ToolPipelineTest.java:58

## OCR-0914 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1ToolPipelineTest.java:59；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：M1ToolPipelineTest 的“错误脱敏”用例实际未覆盖脱敏：sentinel 放入异常 message 会被 DefaultToolPipeline.failure 丢弃，测试名承诺脱敏却未注入敏感串或断言 payload。建议让工具返回值/事件 payload 含敏感串，并断言 result.value、result.error 及事件 payload 均不含 sentinel。
- 判断：异常message被failure代码丢弃，private-sentinel不存在不足以测脱敏；另注入返回value/display哨兵覆盖真实输出。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1ToolPipelineTest.java:59

## OCR-0915 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1ToolPipelineTest.java:114；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：call()/tool() 把工具名硬编码为 `echo`，导致无法构造“工具完全未注册”的调用；`invalidParametersMissingToolsAndWrongScopeNeverEnterBody` 中名为 missing-tool 的场景实际只测到 scope 不匹配。建议给 call()/tool() 增加 toolName 参数，并至少保留一个使用从未注册名字的用例。
- 判断：现把echo只注册在other scope同时测未知和隔离，补独立未注册tool名与scope正确场景区分规则；已有UNKNOWN_TOOL断言非完全缺测。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1ToolPipelineTest.java:114

## OCR-0916 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1ToolPipelineTest.java:209；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：这里丢弃了 `f.run(...)` 的返回值，只断言参数被改写、审批次数为 0。若流水线在 body 之后的后置校验（输出 schema 校验、AFTER_TOOL 状态一致性、结算）失败并返回 INVALID_TOOL_OUTPUT / RESULT_STATUS_CHANGED 等错误，参数断言依然成立，测试仍然通过，因此无法证明「只读绕过」这条路径最终成功。建议接收结果并断言 `ToolStatus.SUCCESS`，同时断言 `f.types()` 中不含 `approval.requested`。
- 判断：参数改写/审批次数断言未验最终结果，AFTER_TOOL或schema失败仍可过，补SUCCESS及落盘completed一致。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1ToolPipelineTest.java:209

## OCR-0917 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1ToolPipelineTest.java:281；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：flush 失败用例只断言了返回码和 body 未进入，没有断言事件流的正确性：应当断言没有 `tool.started`（否则会话里会留下「已开始」的假象），并且记录了 `checkpoint.failed`；同时应验证此前已 append 但未 flush 的 `tool.intent` 在后续重试/恢复时不会导致副作用被重放。当前断言在「先发 started 再 checkpoint」这类排序回归下仍会通过。
- 判断：已有CHECKPOINT_FAILED和body未进入，可补无tool.started/真实checkpoint.failed，不把未执行intent自动视为可安全重试。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1ToolPipelineTest.java:281

## OCR-0918 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M5RecoveryIntegrationTest.java:87；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：该断言无法证明“显式期望版本已过期”被拒绝，存在假阳性：`resume` 是新的 CLI 进程，`LocalWorkspace.observed` 只是实例字段（见 LocalWorkspace 第 40 行），恢复进程里对 note.txt 的已观测版本为空；而 `LocalWorkspace.write()`（第 412-417 行）在 `expectedVersion == null` 时同样抛 `FILE_CONFLICT`。也就是说，即使实现把 `expectedVersion` 参数丢弃/忽略，这里依旧会得到 FAILED + 文件未变，测试照样通过。 建议让恢复轮先在 note.txt 上执行一次 read_file（记录到当前版本），再用旧 `version` 写入：只有真正比对显式期望版本时才会冲突（若参数被忽略则写入成功、文件被覆盖），同时断言完整错误码 `FILE_CONFLICT`，并顺带断言该失败调用未被标记为 UNKNOWN/uncertainCalls（UNKNOWN 不得被重放）。
- 判断：恢复新实例无observed可造成FILE_CONFLICT，补同内容新版本成功对照/显式旧版本错误以隔离版本条件；保留恢复阻塞门禁。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M5RecoveryIntegrationTest.java:87

## OCR-0919 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M5RecoveryIntegrationTest.java:88；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：两个问题会让“请求日志可重建”校验失效： 1) `EventRequestJournal.reconstruct` 的契约是“从前缀重放”（见 EventRequestJournal 第 6 行注释，M4 测试也用 `events.subList(0, i + 1)`）。这里传入完整的 `childEvents`，即使实现把 `request.context` 记录到 `model.requested` 之后（顺序契约被破坏），也能被后续事件“补齐”而通过断言，无法复现顺序缺陷。 2) 断言只有 `assertNotNull`：若 childEvents 中根本不存在 `model.requested`（fork 丢失历史或恢复未完成就退出），for 循环体一次都不执行，测试空转通过；重建出来的内容也从未与真正发送的报文比对。 建议改为按索引取前缀重建、断言重建数量非零，并将重建结果与 `server.requests` 中对应请求的 messages 对比。
- 判断：reconstruct拿完整childEvents可借后续context掩盖倒序，逐model.requested只用截至该事件前缀并比完整wire信封。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M5RecoveryIntegrationTest.java:88

## OCR-0920 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M5RecoveryIntegrationTest.java:93；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：历史继承的强度不足且强耦合脚本序号：`get(2)` 硬编码为恢复轮首个请求，一旦实现多一次重试/总结请求，这里会变成 IndexOutOfBounds 或对着错误报文比较；而“消息条数更大”只说明恢复请求多了一些消息（哪怕只是新用户消息），并不能证明子会话确实继承了父会话的原始请求历史。 建议按父轮请求数推导恢复轮首个请求下标，并把该请求的 messages 前缀与父会话最后一个请求的 messages 做逐条相等比较（子会话历史必须是父历史的超集前缀）。
- 判断：消息数量变大不能证明父历史内容继承，按已捕获父请求数定位子首请求并比较相关历史role/content；不能要求父最后请求必然是完整子前缀（可能追加结果）。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M5RecoveryIntegrationTest.java:93

## OCR-0921 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1AgentLoopTest.java:499；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：该测试名声称验证“replay contract”，但这里的断言只在遇到 `model.requested` 事件时才执行；如果 `EventRequestJournal.record` 回归为不再写入 `model.requested`（或写入的事件类型被改名），循环体一次都不会执行，测试依然通过，只留下 `assertEquals(2, requests.get())` 覆盖 HTTP 调用次数，请求日志/重放链路的回归会被完全掩盖。建议先断言 `model.requested` 事件数与 HTTP 请求数一致（=2），再进行逐条重建比较。
- 判断：遍历model.requested可空过，先断言事件数等于实际请求并每条前缀重建完整相等。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1AgentLoopTest.java:499

## OCR-0922 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1AgentLoopTest.java:562；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：`AlternateStorage` 的 `SessionWriter` 包装器只透传了 `append/flush/close`，没有透传 `durable()`，因此该方法始终返回接口默认值 `false`，即使被包装的存储本身是持久化的。当前 Fixture 默认使用 `MemoryCheckpointPolicy`，所以不影响这些用例；但一旦有人把 `Fixture.checkpoints` 换成 `DefaultCheckpointPolicy` 来验证“durability gate（`DURABILITY_REQUIRED`）在备用存储上成立”，这里会静默地把持久化存储报告为非持久化，测试得到一个假阳性结论。建议补上 `durable()` 的透传。
- 判断：AlternateStorage包装未透传durable默认false，现Fixture内存检查点合法，补显式测试替身能力说明/透传以复用于耐久测试。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1AgentLoopTest.java:562

## OCR-0923 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1AgentLoopTest.java:480；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：`HttpServer` 与虚拟线程 `ExecutorService` 在进入 `try (…)` 之前就已创建，`try` 资源里只有 client 和 Fixture。如果 `createContext`/`start()` 或 `new OpenAiCompatibleLlmClient(...)` 抛异常，`finally` 块不会执行（因为 try/finally 也在其内部），监听端口不会被释放、`workers` 线程池也不会 shutdown，可能导致同 JVM 内后续用例端口/线程泄漏。建议把 server 的创建、`start()` 与 client 的构造都纳入 `try`，或在外部再包一层 `try/finally` 保证 `server.stop(0)` 与 `workers.shutdownNow()` 一定执行。
- 判断：HttpServer/worker在资源try前建立，后续构造失败会跳清理，应从获取第一资源起进入finally并聚合释放。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1AgentLoopTest.java:480

## OCR-0924 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1AgentLoopTest.java:518；OCR原严重度：low
- 计划：R6-01 / P3
- 原意见：`assertReplay` 在 `captured` 不足时会先抛出 `IndexOutOfBoundsException` 而不是带语义的断言失败（事件中 `model.requested` 多于实际发送请求时即触发），同类的直接下标访问还有 `llm.requests().get(2)`、`get(1)`、`getFirst()`。这样失败信息只剩越界堆栈，无法定位是“请求未被发送/记录”还是“消息数量不符”。建议在循环前先断言 `captured.size()` 等于 `model.requested` 事件数，循环内再对下标做守卫。
- 判断：captured下标越界本来也失败但诊断弱，先断言请求数再对应逐条相等，合并0921不单列重构。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1AgentLoopTest.java:518

## OCR-0925 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M1AgentLoopTest.java:169；OCR原严重度：low
- 计划：R1-01 / P2
- 原意见：重复 call-id 的用例是唯一一个只断言失败原因、不断言终态 `TaskStatus` 的终止类用例（同一方法后半段的 `FAKE_EXHAUSTED` 分支就断言了 `FAILED`）。如果状态机回归成其它终态而仍写入 `DUPLICATE_CALL_ID` 原因，此用例不会发现。建议补上 `assertEquals(TaskStatus.FAILED, f.status());`。
- 判断：重复call-id用例断reason但未TaskStatus，补FAILED与无后续执行/模型请求确保真实终止。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M1AgentLoopTest.java:169

## OCR-0926 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M6CompactionTest.java:76；OCR原严重度：medium
- 计划：R6-01 / P2
- 原意见：两处 `assertThrows(HarnessException.class, ...)` 只校验异常类型，未校验 `HarnessException.code()`。`ExtractiveContextCompactor` 在“未解析 tool_call”和“受保护上下文超预算”两条路径上都会抛出 `code=CONTEXT_LIMIT`，但若解析/服务查找链路抛出其他 Harness 异常（如 MISSING_SERVICE、INVALID_EVENT），本用例仍会通过，无法定位拒绝原因，也无法防止拒绝路径回归。建议捕获异常并断言 `code()` 为 `CONTEXT_LIMIT`（第二个“system 3000 字符”用例同样处理）。
- 判断：compactor两个负例只HarnessException，应断CONTEXT_LIMIT并核对未完成协议组未被拆分。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M6CompactionTest.java:76

## OCR-0927 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M6CompactionTest.java:54；OCR原严重度：low
- 计划：R1-06 / P2
- 原意见：这里用 `messages().toString().length()` 作为压缩预算的代理断言，但 `ExtractiveContextCompactor.size()` 的计费口径是 `messages.toString().length() + tools.toString().length()`。当前 fixture 的 tools 为空，二者恰好相等；一旦用例补充 tools（如 `List.of(Map.of("type","function",...))`），即使压缩结果超出 2000 字符预算，该断言仍可能通过，形成假阳性。建议按生产口径计算同样的尺寸后再断言。
- 判断：预算应含tools声明，补非空tools和总计费口径；tools空toString也有[]两字符，不能称两者精确相等。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M6CompactionTest.java:54

## OCR-0928 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M6CompactionTest.java:123；OCR原严重度：low
- 计划：R1-06 / P2
- 原意见：第三个用例只断言第一条事件类型是 `context.compacted`，但没有断言这次调用确实发生了“压缩”。若插件在 BEFORE_MODEL 中只追加事件、却把原始请求透传（`result == original`），后续 `journal.reconstruct`、`EventSessionProjection` 与 `assertEquals(result.messages(), ...)` 都会一致地通过，测试无法发现压缩未生效。建议补充断言，例如 `!result.messages().equals(original.messages())`，并校验事件 payload 中 `removedMessageIndices` 非空且与 `result` 的移除量一致。
- 判断：只有compacted事件且重建等结果不证明真正压缩，补原request尺寸超限、结果缩减/受保护组保持及义务索引一致。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M6CompactionTest.java:123

## OCR-0929 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:177；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：第 177-189 行只断言退出码为 5。CliApplication.execute 的兜底分支对任意非 CLI_USAGE/INVALID_* 异常都返回 5，因此配置解析失败、工作区错误、serve 启动失败等都会让该断言通过，无法证明是 HarnessWebServer 的 token 强度校验拒绝了请求。另外 `Map.of()` 只覆盖了“token 缺失”；测试名声称验证“强 token”，但长度不足/字符集非法的弱 token 场景从未执行，正则被放宽到接受短串也不会被发现。建议断言输出中包含具体的拒绝原因，并补充一个弱 token 用例。
- 判断：serve拒绝token只exit5未明确原因，补缺失/弱token安全码；当前浏览器自动同源入口合同需区分显式CLI Bearer模式，不恢复废弃手输token流程。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:177

## OCR-0930 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:191；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：两个命令都没有传 `--session eco`：dry-run 分支会使用 `s-<uuid>` 作为默认会话 ID，serve 也不会写这个文件。因此即使 dry-run/serve 错误地启动了模型或 MCP 子进程并写入了会话，这条断言仍然为真，无法验证测试名所声称的“CannotStartModelsOrProcesses”。建议改为断言 `.jharness/sessions` 下不存在任何新增会话文件，并在配置中挂一个会写 marker 的 MCP peer 后断言该 marker 未生成。
- 判断：检查固定eco.jsonl但命令未传该id可漏新随机session，补前后session清单/模型和MCP启动计数均未增。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:191

## OCR-0931 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:128；OCR原严重度：high
- 计划：R1-04 / P2
- 原意见：M7McpServer 启动时会向 stderr 输出包含 `mcp-secret-token` 的警示文本（见 M7McpServer 第 18 行），该 fixture 的语义是“必须永不进入事件”。本测试启动并调用该 peer 后只断言 marker，从未断言该敏感串不出现在父/子会话事件、`server.requests`（模型请求）以及证据报告中；StdioMcpTransport 一旦回归为持久化 stderr，此测试不会失败。建议补充对事件、请求和报告内容的脱敏断言。
- 判断：MCP stderr哨兵未在本CLI路径核验，补实际父子日志/报告与线上请求无完整已知秘密。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:128

## OCR-0932 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:149；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：只统计报告文件数量（json + md），没有读取报告内容：无法验证 `--require-read input.txt` 的跨步骤必需操作记录、交付哈希与脱敏。配合第 128 行的问题，环境变量中的 API key 或 MCP stderr 中的敏感串一旦写入报告，测试仍会通过。建议读取报告 JSON，断言义务满足且敏感值已被脱敏。
- 判断：仅报告2个文件数不足以证明义务满足/引用哈希和脱敏，读取JSON/MD并核验证据及哨兵。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:149

## OCR-0933 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:144；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`EventRequestJournal.reconstruct` 要么返回非空 ChatRequest，要么抛出 HarnessException（实现见 EventRequestJournal 第 50-74 行），因此这里的 `assertNotNull` 恒为真，除“没有抛异常”外没有验证任何内容：既未比对重建出的 messages/tools 与 `server.requests` 中实际发送的请求，也未验证 UNKNOWN 事实不会被重放、重建内容已脱敏。建议断言重建结果与记录请求一致，并断言序列化事件/请求中不含 API key 值。
- 判断：assertNotNull只证明可构造，不证明等价wire，补前缀重建完整请求相等；UNKNOWN重建可用于核实不需禁止读取。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:144

## OCR-0934 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:109；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：这里同时依赖两个隐含顺序假设：精确的 7 次请求，以及索引 1 一定是子代理请求。ScriptedServer 的响应是按全局 FIFO 队列匹配而非按请求内容匹配（M4CliIntegrationTest.ScriptedServer.responses），一旦工具并行调度（tool-scheduling）、模型重试或子代理重发导致顺序变化，`get(1)` 会指向父请求，后续 childTools 断言就会在错误对象上通过或失败。建议按 tools/messages 内容定位子代理请求，并把固定计数改成对关键请求的精确断言。
- 判断：当前串行脚本顺序是受控fixture可依赖，采纳按模型/子来源定位请求增强可读性；未来并行启用不是当前不确定性。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:109

## OCR-0935 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:119；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：对序列化 JSON 做子串匹配无法真正验证子代理工具隔离：工具 description 中出现 `write_file` 等子串就会误判；`assertFalse(childTools.contains("mcp_peer"))` 也会被其他 `mcp_peer__*` 名称影响；若子代理新增/改名为其他写能力工具（apply_patch、shell 等）或新增 MCP 工具，这里都不会发现。建议解析 tools 数组并对 `name` 集合做精确断言（等于只读白名单）。
- 判断：工具声明序列化子串可被描述文字误触，解析实际function.name并与子任务精确白名单集合比较。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:119

## OCR-0936 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:22；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：SKILL.md 中写入了提示注入文本，但测试仅断言工具声明包含 `skill_demo__load`，从未断言注入内容以“不可信”方式传给模型，也没有断言加载 skill 后父代理/子代理的工具集合、审批与策略没有扩张。skill 越权（例如注入后获得写工具或提升权限）的回归不会被这个测试发现。建议在 `skill_demo__load` 调用前后比较工具声明，并断言注入文本只作为不可信内容出现在请求中。
- 判断：SKILL不可信文本可补只以内容进入且工具权限不扩张的前后断言；不能用模型是否听从注入当确定安全测试。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7CliEcosystemTest.java:22

## OCR-0937 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:450；OCR原严重度：medium
- 计划：R0-03 / P1
- 原意见：try-with-resources 的关闭顺序是反序：`executor` 最后声明却最先关闭，`PipedInputStream/PipedOutputStream` 反而在其之后才关闭。触发条件：断言失败、`await()` 超时或 `executing.get(10, SECONDS)` 抛 TimeoutException 时，CLI 虚拟线程仍阻塞在 stdin 读取上，`ExecutorService.close()` 会无限等待该任务结束，而管道要等 close 返回后才被关闭 —— 测试不是快速失败而是挂死，同时 `terminal_start` 拉起的 sleep/ping 子进程与实时终端也得不到回收。建议把 executor 声明在管道之前（或在 finally 中先 `feed.close()` 再 `executor.shutdownNow()`），并在超时分支显式 `executing.cancel(true)` 后清理终端。
- 判断：REPL executor先close、管道后close，失败时stdin读取可能永等，finally先关拥有的输入管道/取消任务再有界关执行器。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:450

## OCR-0938 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:202；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：线上请求与 journal 事件只按位置 `index++` 配对，但循环后仅断言 `assertEquals(9, index)`（第 218 行），从未断言 `server.requests.size() == index`。触发条件：运行时若因重试/重发多发出一次请求，第 10 个请求会被静默忽略，快照与协议回归仍然“通过”；反之线上请求少于 model.requested 事件时 `get()` 抛出的只是无任何上下文的 IndexOutOfBoundsException，无法定位是哪一轮请求缺失。建议在循环后补 `assertEquals(index, server.requests.size())`，并在取值前断言存在性以给出可复现的失败信息。
- 判断：循环只断index9而未wire总数，额外真实发送可遗漏，补两边计数相等及无下标漏项。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:202

## OCR-0939 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:207；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：期望值/实际值顺序写反（expected 应为 `wire.get("model")`），失败时打印的 diff 会指向错误一侧；同时该循环只校验 messages/tools/stream/model，`thinking`、temperature 等其余参数即使从 reconstruct 结果中丢失也不会被发现（M4 测试里是有 thinking 断言的）。建议纠正参数顺序，并把 `reconstructed.parameters()` 与线上 JSON 整体比较，至少补齐 thinking 断言。
- 判断：expected来自重建、actual来自wire顺序可合理，不必强行反转；采纳完整parameters比较覆盖thinking等而非仅model/stream。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:207

## OCR-0940 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:74；OCR原严重度：low
- 计划：无
- 原意见：在序列化 JSON 的中点切分 tool_calls.arguments：`args` 长度为奇数时会把转义序列（如 `\"`）或代理对从中间切开。当前调用点的参数都是 ASCII 路径所以碰巧安全，但该 helper 一旦用于含中文/日文内容（例如 write_file 的 content）或需要转义的字符串，两个分片就会拼出非法 JSON，导致客户端解析失败，产生与被测逻辑无关的失败。建议按结构边界切分（例如在恰好位于 `,` 之后的位置），并断言两段拼接后仍能 `JSON.readValue`。
- 判断：helper先把arguments JSON字符串切片再用delta的JSON序列化分别转义，两片在接收端拼回原串；切在反斜杠内是流式协议必须支持情景，不会天然拼非法JSON。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:74

## OCR-0941 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:295；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：这里假定最后一条消息必然是 content 为非空 String 的 tool 结果：若协议形状变化（最后一条是带 tool_calls、content 为 null 的 assistant 消息，或 content 变成结构化片段），`(String)` 强转会抛 ClassCastException、`Pattern.matcher(null)` 会抛 NullPointerException。更糟的是异常发生在 HttpServer 工作线程里，测试线程收不到，只会在客户端表现为读超时/重试，失败原因被完全掩盖。建议先断言 `"tool".equals(Values.object(messages.getLast()).get("role"))`，再用 `instanceof String` 校验 content 并把原始内容带进断言消息。
- 判断：handler依赖最后tool消息形状，缺字段应记录明确fixture failure并回受控失败，避免隐式NPE变传输错误；合法协议下cast是受控。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:295

## OCR-0942 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:440；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：在 HttpServer handler 内直接抛异常不会让测试失败：JDK 默认 handler 只会吞掉异常并关闭连接，客户端看到的是被截断的 SSE，会走重试/超时路径，最终表现为难以定位的挂起或误报。建议把失败原因记录到共享字段（如 `List<Throwable> failures`）并在测试结束时断言为空，或返回一条显式错误 SSE 让客户端侧失败。另外 `[0-9a-f-]{27}` 过于宽松，很容从 toolText 中误取到其他 UUID；建议用完整 36 位的严格正则 `[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}`，并锚定 “id=” 或 “pty=” 之类的上下文。
- 判断：handler异常不会自动断言至主测试，可集中失败通道并终局断空；不记录包含原toolText秘密的异常文本。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:440

## OCR-0943 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:497；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：测试名声称 REPL 退出时会关闭实时终端（AndClosesIt），但这里只校验 journal 可恢复，没有任何断言证明退出路径真的 terminate/close 了终端。若退出清理被遗漏，`sleep 30`/`ping -n 30` 的子进程会残留在运行环境里而测试仍然通过。建议补断言：退出后最后一个 `terminal.snapshot` 中 state 为 STOPPED/CLOSED、`alive` 为 false、`residualPids` 为空（可直接复用 `approvedBackgroundToolsPersistFinalStateAndColdTailArtifact` 的断言方式）。
- 判断：repl终端退出只ready不能证明stop，补已知PID退出/最后终端状态且无重新启动记录。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:497

## OCR-0944 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:367；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：“在进程创建之前就拒绝”这一结论只靠“没有 tool.intent/tool.started/terminal.snapshot 事件”反证：如果实现实际先 spawn 进程再 kill，只要不落这些事件，测试依然通过，正好漏掉了该方法要守护的回归风险。建议改用可观测副作用证明：把 argv 换成一个一旦被执行就会写标记文件的命令，然后断言该标记文件不存在，并断言没有产出 pty/pid 相关的 artifact 或 terminal 会话记录。
- 判断：无intent事件不能独立证明未spawn，补受控进程启动计数或唯一标记文件；不运行真实危险命令。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M6RuntimeIntegrationTest.java:367

## OCR-0945 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M4CliIntegrationTest.java:580；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：脱敏覆盖不完整（约 580–584 行）：这里只检查了 stdout 与 session JSONL。触发条件：敏感 call id 被写入 stderr、`.jharness/reports` 下的 JSON/Markdown 交付报告（`recordedFacts` 会包含 callId）或其它落盘日志时，测试仍会通过。建议对该用例产生的 stderr、`delivery.registered` 指向的所有报告文件以及 workspace 下可读日志统一断言不包含该夹具密钥；第一个用例产生的交付报告也应同样检查。
- 判断：敏感callId只stdout/JSONL，补stderr及delivery报告JSON/MD实际输出通道不含已知秘密。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M4CliIntegrationTest.java:580

## OCR-0946 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M4CliIntegrationTest.java:420；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：竞态条件（约 416–426 行）：这里只等到 `started` 文件出现，就立刻从异步收集线程写入的 `lines` 中查找 `SESSION` 行；收集线程可能尚未把该行追加到列表，`orElseThrow()` 会抛出 `NoSuchElementException`，导致取消用例偶发失败，也可能在 runId 尚未取得时继续。触发条件：CI 高负载或子进程 stdout 输出较慢。建议在固定截止时间内轮询 `lines`，直到出现以 `SESSION ` 开头的行，超时后用包含当前 `lines` 的断言失败信息报告。
- 判断：started文件与异步SESSION stdout收集无同步，先轮询有界获取runId行再取消，避免orElseThrow竞态。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M4CliIntegrationTest.java:420

## OCR-0947 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M4CliIntegrationTest.java:64；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：重放断言过弱（约 58–66 行）：用例名声称 `EverySentRequestReplays`，但这里只比较 `model`、硬编码的 `thinking` 和 messages 数量，没有比较消息的 role/content/tool_calls 或 `tools`、temperature 等参数。触发条件：事件日志重建时丢失或篡改消息内容但保持数量不变（例如把 user 消息替换成 system 消息），测试仍会通过。建议将 `replay.messages()` 与 `wire.get("messages")` 按 role/content/tool_calls 规范化后逐项比较，并比较完整请求参数；`thinking` 也应来自 replay 而不是常量。
- 判断：EverySentRequestReplays只model/thinking/消息数不能锁定消息正文，比较完整messages/tools/参数的前缀重建wire等价。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M4CliIntegrationTest.java:64

## OCR-0948 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M4CliIntegrationTest.java:160；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：dry-run 负向断言不完整（约 157–164 行）：这里只断言没有 `tool.intent`/`tool.started` 且目标文件不存在，没有断言 dry-run 不产生 `delivery.registered` 或生成的报告状态不是 `COMPLETED`。触发条件：dry-run 错误地把预览登记为已交付/已完成时，本测试仍通过。建议读取 `delivery.registered` 指向的报告并断言其状态明确表示预览/未执行（或按契约断言该事件不存在），同时断言没有命令/变更被标记为已执行。
- 判断：干跑可生成事实报告但不得COMPLETED冒称执行，检查报告明确预览/未执行；不能一律要求没有delivery.registered（交付预览报告合法）。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M4CliIntegrationTest.java:160

## OCR-0949 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M4CliIntegrationTest.java:594；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：该取消场景把 `y\n:cancel\n` 一次性预置到 stdin。`interact` 线程在批准后约 10ms 就会执行 `takeCancel()` 并取消运行，此时 `execute_command` 子进程可能尚未启动；测试只断言退出码 3，不检查 `started` 标记，因此即使取消逻辑无法中断运行中的命令也会通过。触发条件：机器较快或调度较早时取消在工具启动前生效。建议改为可控输入流：先写入 `y\n`，轮询到 `started` 文件出现后再写入 `:cancel\n`；或至少断言 `root.resolve("started")` 存在并验证命令进程被终止。
- 判断：预置y及cancel可在进程开始前取消，只exit3不测在途终止，改可控管道等started再cancel并确认PID结束。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M4CliIntegrationTest.java:594

## OCR-0950 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M4CliIntegrationTest.java:256；OCR原严重度：low
- 计划：R6-01 / P3
- 原意见：用例名声称验证“每轮不会新建另一个输入读取器”，但断言只统计 `turn.ended` 数量和 HTTP 请求数；没有对输入读取器/线程创建次数做可观测断言。触发条件：REPL 每轮新建 `TerminalInput`/stdin 读取线程但轮次与请求数不变时，测试仍通过。建议注入可计数的输入流/读取器工厂，或断言仅存在一个 `cli-stdin` 读取线程在复用。
- 判断：两轮测试未计输入读取器，若保留方法强命名可加可观察工厂/线程计数；主要same-session和轮次断言不因此无效。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M4CliIntegrationTest.java:256

## OCR-0951 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7EcosystemFixture.java:28；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：构造函数中先打开会话写入器（`openWriter("s", EXCLUSIVE)` 会占用 `JsonlSessionRepository.OWNERS` 进程级独占锁并持有 FileChannel），随后才构造 `PluginContainer` 并组装参数列表。若后续任一环节抛异常（例如 `extension` 为 null 时 `plugins.stream().map(HarnessPlugin::id)` 直接 NPE；或扩展插件 id 与 `fixture-registry` 重复导致 `DUPLICATE_PLUGIN`；或插件 `id()/version()` 返回空值导致 `Values.required` 抛错），构造失败后调用方拿不到 fixture 实例，`close()` 永远不会执行，写入器的独占文件锁与 `events` 事件总线都泄漏。泄漏的锁存在静态 `OWNERS` 集合中，同一 base 目录的后续 fixture 会持续报 `WRITE_CONFLICT`，形成跨用例的级联失败。建议把 `writer`/`container` 的创建放到 try/catch 中，失败时显式关闭已创建资源；或先完成 `PluginContainer` 构造，再打开写入器。
- 判断：fixture先取得writer再构造容器，后续失败无close入口，构造失败释放writer/已建资源并保留主错误。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7EcosystemFixture.java:28

## OCR-0952 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7EcosystemFixture.java:112；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`events.close()` 与 `writer.close()` 顺序写在同一个 `finally` 块里：一旦 `writer.close()`（底层 `FileChannel`/锁释放）抛异常，`events.close()` 就不会执行，`DefaultEventBus` 的通知/串行执行器线程和监听器不会被关闭（总线是独立于 `PluginContainer` 内部总线的实例，容器关闭并不会顺带关闭它）。同时该异常会覆盖 `container.close()` 抛出的 `CLEANUP_FAILED`，丢失原始失败原因。建议改成嵌套 try/finally 或 try-with-resources，保证两个资源都被关闭且异常被抑制保留。
- 判断：writer.close与events.close同finally串行，writer抛错跳独立总线，分别关闭并聚合suppressed。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7EcosystemFixture.java:112

## OCR-0953 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7McpServer.java:29；OCR原严重度：high
- 计划：R6-02 / P3
- 原意见：JSON-RPC 通知处理不完整（第 33-37 行）：只有 notifications/initialized 被跳过，其余不带 id 的通知（如 notifications/tools/list_changed、notifications/cancelled、客户端对 ping 之外的推送）会继续往下走到 Map.of("jsonrpc","2.0","id",id,...) —— Map.of 拒绝 null 值，直接抛 NPE 终止 fixture 进程，客户端只看到 stdout EOF，而不是协议规定的“只发请求、不回响应”。建议在读取 id 后统一判定 id == null 即视为通知并 continue。
- 判断：测试peer只实现受测initialized通知，新增通用无id通知应忽略/计数而不回复；无需将故障peer改成完整生产MCP服务器。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7McpServer.java:29

## OCR-0954 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7McpServer.java:81；OCR原严重度：high
- 计划：R6-02 / P3
- 原意见：JSON-RPC methods resources/read and tools/call do not guard missing params or arguments. Null params or missing uri/arguments/text cause NPE in Map.of or member access, crashing the fixture instead of returning -32602 or an isError result. Validate nullable params first and send a JSON-RPC error.
- 判断：受控peer缺params会异常关闭，补负场景的明确协议错误仅在准备测该行为时，别把整个fixture升成产品服务。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7McpServer.java:81

## OCR-0955 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7McpServer.java:25；OCR原严重度：medium
- 计划：无
- 原意见：trace 记录顺序与脱敏问题（第 25-27 行）：解析在落盘之前，`JSON.readValue(line, Map.class)` 遇到非法 JSON 或非对象顶层（数组/数字/字符串）会抛异常，整个 while 循环终止——这一行既没有进入 trace，也没有按 JSON-RPC 规范回 -32700 parse error，夹具在“畸形输入”场景下静默退出。另外 trace 原样追加请求行，本项目刻意在 content 中植入的 mcp-secret-token 以及被测端可能传入的凭据/token 都会明文落到测试产物中，缺少脱敏。建议先（必要时截断/脱敏后）落盘再解析，并对解析失败 continue + 回 error 帧。
- 判断：原始trace是本机忽略缓存中的故障证据且输入为synthetic哨兵，先解析再记录是既定受控正向协议；不能要求任何坏帧都生产式回复/把原始证据擦掉。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7McpServer.java:25

## OCR-0956 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7McpServer.java:16；OCR原严重度：medium
- 计划：无
- 原意见：pid 记录路径固定为 trace 的同级 pid.txt（第 16-17 行）：trace 文件名允许每个 fixture 不同，但 pid 文件名恒为 pid.txt，同一目录下并行运行多个 M7McpServer（例如多组 mode/trace 的并发用例）会互相覆盖，导致上层按 pid.txt 关联/清理进程时对错进程，进而出现杀错进程或残留进程。建议由 trace 文件名派生唯一 pid 文件，例如 trace.resolveSibling(trace.getFileName() + ".pid")。
- 判断：每peer独立temp目录，pid.txt同级固定名用于父测试关联，未发现同目录并发多peer；假想复用不足以要求协议更名。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7McpServer.java:16

## OCR-0957 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7McpServer.java:204；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：stdio 输出的编码与分行不符合 newline-delimited JSON 约定（第 204-206 行）：输入用 UTF-8 解码，输出却走 System.out 的 JVM 默认字符集 + println 的平台行分隔符。在默认编码非 UTF-8 的环境（如 Windows GBK）中非 ASCII 内容会被替换/乱码，Windows 上还会输出 \r\n，破坏按 \n 切帧的被测端解析。此外 PrintStream 会吞掉底层 IO 异常，方法签名上的 `throws Exception` 实际不会触发，stdout 被关闭时表现为静默丢帧而不是可诊断的失败。建议用显式 UTF-8 的 PrintStream 并以 '\n' 结尾，必要时检查 checkError() 抛 IOException。
- 判断：System.out默认编码可在显式非UTF8启动下非ASCII失真，采纳UTF8输出；CRLF按readLine是合法跨平台换行，不是必然帧违规。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7McpServer.java:204

## OCR-0958 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7EnvironmentTest.java:45；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：这里只验证了“不确定读”（op=read）分支。DockerExecutionEnvironment 对 write/edit/patch 的不确定结果是另一条安全关键路径：必须先 close() 移除容器、再抛 ENVIRONMENT_OPERATION_UNKNOWN（DockerExecutionEnvironment 第 111-115 行），且该命令绝不能被重放。全仓库测试中没有任何一处断言 ENVIRONMENT_OPERATION_UNKNOWN（检索测试目录无命中），因此一旦该分支退化成“直接重试”或“不关闭容器”，本文件不会失败。建议复用同一 transport 桩，让 childView.write(...) 收到 uncertain 回复，断言 code 为 ENVIRONMENT_OPERATION_UNKNOWN、rm 恰好执行一次、并且之后任何 files() 调用抛 ENVIRONMENT_CLOSED。
- 判断：FakeTransport仅不确定read不能守护write/edit/patch未知后关闭且不重发，补副作用操作故障注入和请求计数。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7EnvironmentTest.java:45

## OCR-0959 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7EnvironmentTest.java:96；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：“无观察版本不得覆盖”这一核心保护没有校验错误码，只断言抛出任意 HarnessException：即使实现改成 PATH_DENIED、ENVIRONMENT_IO 或其它无关异常，用例依旧通过。本文件第二个用例（第 78-85 行）已对同类场景断言 FILE_CONFLICT，此处应保持一致，并同时断言磁盘内容未被改写。
- 判断：无观察版本写入只异常类型，可断FILE_CONFLICT和原字节保持。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7EnvironmentTest.java:96

## OCR-0960 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7EnvironmentTest.java:105；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：路径逃逸用例断言过宽：只要抛出任意 HarnessException 即通过，无法区分是边界校验生效还是 ExecPath 构造/文件不存在等无关失败；并且全部输入都是字符串形态，缺少最关键的真实逃逸场景——工作区内存在指向 root 之外的符号链接（readText 应被拒绝，而不是读到宿主文件）。建议对每个路径断言 PATH_DENIED，并在 @TempDir 中实际创建指向 root 外文件的 symlink 后断言读取被拒绝。
- 判断：路径负例补具体PATH_DENIED与真实链接逃逸哨兵，远端worker/local分别适配；不在不支持链接平台静默skip强制门禁。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7EnvironmentTest.java:105

## OCR-0961 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7EnvironmentTest.java:133；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：容器加固断言遗漏了若干已实现的安全属性：未断言 --user=65534:65534（非 root 强制，回归成 root 运行不会被发现）、未校验 worker jar 绑定挂载是否带 readonly、也未校验环境变量白名单（宿主凭据/变量不得注入容器）。建议至少补充 --user=65534:65534，并断言 --mount 中 “/runtime/harness.jar” 的段包含 readonly。
- 判断：容器加固断言缺non-root与worker只读挂载，补精确--user和对应mount，并检查环境仅允许运行所需值。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7EnvironmentTest.java:133

## OCR-0962 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7EnvironmentTest.java:157；OCR原严重度：low
- 计划：R0-03 / P3
- 原意见：assertEquals(3, commands.size()) 与实现内部的传输调用次数（run + exec + rm）强耦合：实现一旦增加一次探测/检查调用，用例就会在没有任何回归的情况下失败；反过来它也表达不出“清理恰好一次且针对本环境”的语义。建议改为断言 rm 命令的条数为 1 且参数包含 environment.id()。另外相邻两行 assertThrows 同样只校验异常类型，未校验 ENVIRONMENT_CLOSED / UNSUPPORTED_TERMINAL 错误码，错误码互换的回归无法被发现。
- 判断：固定run/exec/rm次数可继续作为无隐藏调用保障，另加精确owner rm唯一/幂等与错误码，不只删除次数断言。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7EnvironmentTest.java:157

## OCR-0963 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7EnvironmentTest.java:166；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：该用例是 NativeCommandTransport 的唯一覆盖，但依赖宿主 java -version 并断言退出码为 0：在受限 CI（禁止 fork、使用精简运行时）中容易失败，而且它完全没有覆盖超时/取消返回 started=true、uncertain=true 的分支，无法防止“超时被误报为成功/确定性结果”这一协议的回归。建议补充一个超时用例（如 java -version 也不足以触发，可用长耗时命令或极短 timeout），断言 uncertain() 为 true。
- 判断：补NativeCommandTransport超时/取消started/uncertain有价值，真实java-version用于烟测合法；不能因受限机器不能fork就skip发布验收。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7EnvironmentTest.java:166

## OCR-0964 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7EnvironmentTest.java:72；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：用例名声称验证 “independent workers”，但整个用例只创建了一个 RemoteWorkspace（remote() 返回的单个实例），实际验证的仅是同一 workspace 跨调用的 observed 版本记忆。版本状态是否相互独立（例如 isolatedView() 或另一个 RemoteWorkspace 未读过文件时不得复用前者的版本，否则可被用来绕过版本保护）完全未被断言。建议要么改名，要么额外创建独立视图并断言其不带版本保护时写入被拒绝。
- 判断：同remote跨调用版本缓存不证明isolatedView独立，补两视图观察/父写版本冲突或精确重命名。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7EnvironmentTest.java:72

## OCR-0965 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SdkTest.java:81；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 81-88 行：这里直接用 model.requests().get(count++) 按下标取值，但没有先断言持久化日志中 model.requested 事件数等于 FakeLlm 实际收到的请求数。一旦日志中多出一条 model.requested（例如重试/失败请求也写入了事件），测试会以 IndexOutOfBounds 失败，而不是给出“日志多写/请求被重放”的可诊断信息；这正是该用例要防的重复请求场景。建议先统计并断言事件数量与 model.requests().size() 一致，再逐条按下标比较。
- 判断：先断FakeLlm请求数与journal requested数再逐前缀完整比对，避免额外发送漏测并改善下标失败。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SdkTest.java:81

## OCR-0966 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SdkTest.java:128；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 128-129 行：assertThrows 只校验异常类型，未校验异常码。若取消检查被移除，sdk.run("again") 会继续执行并因 FakeLlm 脚本耗尽抛出 FAKE_EXHAUSTED（同样是 HarnessException），测试仍会通过，无法真正证明“取消后拒绝执行”。建议断言 code 为 CANCELLED。
- 判断：取消后run只异常类型可因脚本耗尽假通过，明确CANCELLED并断请求计数不增。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SdkTest.java:128

## OCR-0967 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SdkTest.java:216；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：第 216-227 行：手工构造的 model.requested 事件与真实 journal schema 不一致。EventRequestJournal.record 总会先写 request.context，并在 model.requested 的 payload 中写入 contextEventId，恢复/重放路径依赖这对事件；这里 payload 为空，测试仅因 SessionRecovery 按事件类型判定 pending 而通过。这样既没有覆盖真实 pending 请求的恢复路径，也可能在恢复逻辑收紧 payload 校验后失真。建议通过 EventRequestJournal/writer 生成成对的 request.context + model.requested，或至少补上 contextEventId，并断言 SessionRecovery.inspect(...).pendingRequests() 的实际内容。
- 判断：空model.requested用于制造恢复未决事实合法，不需要完整有效request才能被阻塞；可补真实journal格式场景，但不移除畸形未决保守拒绝测试。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SdkTest.java:216

## OCR-0968 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SdkTest.java:21；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：第 21-35 行：open() 在 HarnessSdk 构造失败时不会关闭已创建的 LocalExecutionEnvironment/模型。HarnessSdk 明确“构造成功后才接管 environment”，其构造异常路径只关闭 assembly 和 writer。当前多个用例（如 ENVIRONMENT_MISMATCH、UNKNOWN_SIDE_EFFECT 分支）都会走到这里，导致 LocalProcessRunner/LocalTerminalSession 泄漏（虽未启动子进程，仍属于资源生命周期问题，Windows 上还可能影响临时目录清理）。建议先单独构造 environment，并在 new HarnessSdk 的 catch 中关闭后再抛出。
- 判断：helper构造HarnessSdk失败而环境所有权未转移，需finally释放新建LocalExecutionEnvironment/模型，避免构造负例泄漏。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SdkTest.java:21

## OCR-0969 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SdkTest.java:199；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：第 199-203 行：重复的 verifyEnvironment 只断言抛出 HarnessException，没有校验 ENVIRONMENT_NOT_UNCERTAIN。若该调用因其他原因（例如写锁冲突或事件未落盘）失败，幂等性断言仍会通过，无法证明“已被显式验证后不能重复验证”。建议断言异常 code。
- 判断：重复verifyEnvironment只异常类型，断ENVIRONMENT_NOT_UNCERTAIN与未追加重复核实事实。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SdkTest.java:199

## OCR-0970 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:17；OCR原严重度：medium
- 计划：R6-02 / P3
- 原意见：`args[2]` 在任何参数个数校验之前就被使用，后面还用到 `args[0]`/`args[1]`/`args[3]`/`args[4]`。参数缺失时抛出的是 `ArrayIndexOutOfBoundsException`，没有任何用法提示，且探针会先创建目录/容器再失败。另外：`Path.of(args[4])` 未校验父目录可写，属调用方可控的任意写路径；`Path.of(args[3])` 可能不存在或为目录。建议在 main 开头校验 `args.length == 5`、校验 worker jar 为可读普通文件、并确保输出文件父目录存在后再继续。
- 判断：探针args和输出父目录预检可提前明确失败；显式输出路径本来由授权脚本选择，不叫任意路径写漏洞。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:17

## OCR-0971 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:48；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：Docker 分支用 SH，本地分支却固定用 `CommandRequest.Mode.CMD` 且命令体是 Windows CMD 语法（`type`、`&`、`cd`）。`LocalProcessRunner.nativeArgv` 在非 Windows 宿主上对 `CMD` 会直接抛 `UNSUPPORTED_SHELL`（"Native CMD/PowerShell require Windows"），因此在 Linux/macOS 上该探针必然失败。应依据宿主 OS 选择模式（非 Windows 用 `SH` + `/bin/sh` 语法），或统一改用 SH 以便跨平台复现。
- 判断：M7DockerProbe本地分支固定CMD使新Linux/macOS执行不适用，采纳按平台脚本；历史仅Windows宿主Docker证据不因此作废。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:48

## OCR-0972 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:54；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：这条断言实际只能证明最后一个子命令成功：Docker 分支以 `;` 串联，退出码是 `pwd` 的；本地 CMD 分支用 `&` 无条件串联，退出码是 `cd`（恒为 0）的。因此 `printf/echo > shell.txt` 或 `cat/type input.txt` 失败时仍会得到 `SUCCESS`，探针不会暴露问题。建议用 `&&` 串联（或 `set -e`）让任一子命令失败即整体失败。
- 判断：串联命令最后exit可能掩盖cat失败，采纳&&及期望stdout；已有shell.txt内容验证不能被忽略，非所有子命令都无验证。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:54

## OCR-0973 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:117；OCR原严重度：medium
- 计划：无
- 原意见：该 fixture 把会修改工作区的 `edit_file` 标为 `READ_ONLY`，下一条 `execute_command` 也标为 `READ_ONLY`。`DefaultToolPipeline` 会用工具声明的级别覆盖模型给出的级别（effect = tool.sideEffectLevel()），因此这里的取值实际是误导性的：探针既没有覆盖 `WORKSPACE_WRITE`/`SYSTEM_IMPACT` 路径，也不会触发 `DefaultPolicyEngine` 的 ASK 分支与注入的审批回调（审批分支被静默跳过）。建议改为 `WORKSPACE_WRITE` 与 `SYSTEM_IMPACT`，并断言确实产生了 `approval.requested` 事件。
- 判断：ToolInvocation级别为模型提议、Pipeline必须以注册工具真实级别覆盖，故READ_ONLY伪声明恰可验证安全来源，不能据此说没进入写/命令路径。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:117

## OCR-0974 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:74；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：隔离断言过弱：`CapDrop.toString().contains("ALL")` 与下一行的 `SecurityOpt.toString().contains("no-new-privileges")` 只做字符串包含判断，任何包含该子串的数组（例如 `["ALL","SYS_ADMIN"]`，或 `no-new-privileges:false`）都会通过，无法证明真正丢弃了全部能力/禁止提权。建议按数组精确比较（大小 + 元素值），`SecurityOpt` 还应断言 `:true` 后缀。
- 判断：cap/security字段字符串contains过宽，补结构化ALL唯一与no-new-privileges true；不能单靠presence证明运行时加固。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:74

## OCR-0975 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:89；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：`! touch /forbidden` 在 `touch` 不存在、被 PATH/权限屏蔽等任何失败场景下都会返回 0，因此即使根文件系统并非只读（只是 touch 因其他原因失败）该检查也会“通过”。应先真正尝试创建，再对结果做存在性判断，例如 `touch /forbidden 2>/dev/null; test ! -e /forbidden`。同样地，`test ! -S /var/run/docker.sock` 不存在的 sock 与“被隔离”不可区分，建议额外确认 docker CLI 不可用。
- 判断：!touch失败可能因工具缺失，先确认touch存在并核实只读挂载/创建errno；原建议再test不存在也不能区分缺touch，不照搬。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:89

## OCR-0976 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:230；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：清理校验不严谨：`docker ps --filter name=<owner>` 是子串/正则匹配而非精确名称匹配，任何名称包含 owner 的残留容器会让断言误报失败，而空 stdout 也无法区分“容器已删除”和“过滤条件/ps 行为异常”。另外这里没有校验 `!remaining.uncertain()`（同一方法中 inspect 却校验了），超时/不确定的 ps 只返回空输出时会被当成清理成功。建议使用 `name=^/<owner>$`（或 `docker inspect -f '{{.State.Status}}' <owner>` 期望非 0 退出），并同时断言 `!remaining.uncertain()`。
- 判断：Docker name过滤未锚定可误命中，优先精确owner label并检查uncertain/exit；空stdout仍需有成功ps。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:230

## OCR-0977 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:30；OCR原严重度：low
- 计划：无
- 原意见：容器在进入 try-with-resources 之前就创建，且 `String owner = container.id();` 位于构造与 try 之间：一旦该语句（或后续任何 try 前的代码）抛异常，已启动的容器不会被 `rm --force`，只能靠人工清理。建议把容器获取与 `id()` 一并放进资源声明或 `try { ... } finally { container.close(); }` 结构，确保构造成功后任何路径都能释放。
- 判断：构造后仅取id纯访问器再进入try，无外部调用/可抛路径证据，泛化任何语句可OOM不是实质资源泄漏。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:30

## OCR-0978 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:70；OCR原严重度：low
- 计划：R6-02 / P3
- 原意见：`docker inspect` 退出码为 0 并不保证 stdout 是至少含一个元素的 JSON 数组；空输出/`null` 时 `readTree(...)` 得到 `MissingNode`，`get(0)` 返回 `null`，随后 `actual.get("HostConfig")`、`host.get("NetworkMode")` 会抛 NPE，掩盖真实原因。建议先断言 `actual` 是数组且 `size() > 0`，并对 `HostConfig`/`Config` 做非空校验后再断言隔离项。
- 判断：inspect返回合法数组需断schema再取index0，改善错误定位；字段缺失导致NPE本来也失败不会假通过。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:70

## OCR-0979 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:20；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：dockerRoot 由宿主进程以默认权限（通常 0755）创建，而容器以 `--user=65534:65534` 运行并把该目录 bind mount 到 /workspace（见 DockerExecutionEnvironment 的 `--user`/`--mount` 参数）。在原生 Linux 宿主上，nobody(65534) 对宿主用户拥有的目录/文件没有写权限，因而 `files.edit(...)`、`printf ... > shell.txt`、SDK 的 `edit_file` 都会因 EACCES 失败，探针在非 root 的 Linux 环境下不可复现。建议显式为 dockerRoot 授予容器 uid 可写权限（如 0777 或 chown 65534），或明确要求以 root/兼容 bind-mount 语义的环境运行。
- 判断：bind挂载宿主权限与uid65534在Linux不同，采纳fixture定向准备可写目录并验证non-root，不chmod真实工作区或削弱容器user限制。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java:20

## OCR-0980 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7ClosureTest.java:96；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：第 96-99 行：Driver 自行 new DefaultPolicyEngine() 并自带 ApprovalService 决策，绕开了 HarnessSdk 在 open() 中注入的 policy 与 ApprovalService.rejectAll()（SDK 侧由 PolicyPlugin 注册 ApprovalService）。结果是：本套件关于“显式审批/拒绝”的断言只验证了 DefaultToolPipeline 如何使用调用方传入的 context，一旦 HarnessSdk 构造 ToolPipelineContext 时传错/漏传 approvals 或 policy，这些用例完全发现不了，且 SDK 级审批服务从未被执行。建议把可控的 ApprovalService 安装到 SDK（PluginContext 已注册该服务），从 context.services() 取得后再执行工具；若必须按调用传参，请至少追加一条直接走 SDK 自带 context 的用例。另外 ToolInvocation 中写死的 SideEffectLevel.READ_ONLY 实际不生效（DefaultToolPipeline.prepare 会用 tool.sideEffectLevel() 覆盖），保留该值会误导读者以为审批/串行语义按 READ_ONLY 处理。
- 判断：Driver自建policy/approval测管线而非SDK装配，可保留分层测试，另补SDK真实注入服务解析与单次审批证明。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7ClosureTest.java:96

## OCR-0981 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7ClosureTest.java:226；OCR原严重度：medium
- 计划：R1-06 / P2
- 原意见：第 224-230 行：该会话（guard）从未成功创建 id 为 one 的 todo（创建 todo 的是上一个 planning 会话），因此此处“非 SUCCESS”很可能仅因为 todo 不存在，而不是因为“伪造 eventId 作为证据被拒绝”；随后 assertTrue(todos.isEmpty()) 也随之恒成立。这让“必须用真实事件作为证据”的校验实际上没有被覆盖。建议先 todo_update(PENDING, revision 0) 并断言 SUCCESS/状态为 PENDING，再用 invented-event 尝试 DONE，断言失败且 todo 仍为 PENDING。
- 判断：guard会话未建one todo，DONE失败可能NOT_FOUND，先建PENDING并断成功后用伪event核验拒绝原因。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7ClosureTest.java:226

## OCR-0982 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7ClosureTest.java:214；OCR原严重度：medium
- 计划：R1-06 / P2
- 原意见：第 209、214-215 行：这三处 setup 调用的返回值都未断言。若第 214 行的 APPROVED 转换本身失败（例如被策略/证据校验拒绝），plan 仍为 DRAFT，第 216 行的 DRAFT 断言就会在“撤销审批”逻辑完全失效时也通过。建议对 setup 断言 SUCCESS（APPROVED 之后先断言 plan_view 返回 APPROVED），再做 revision 2 的 plan_update 并断言回落到 DRAFT，使被测行为与断言一一对应。
- 判断：APPROVED setup返回未查，随后DRAFT可能原本未审批，补每setup成功及中间APPROVED再修改revision回DRAFT。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7ClosureTest.java:214

## OCR-0983 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7ClosureTest.java:357；OCR原严重度：medium
- 计划：R6-01 / P2
- 原意见：第 357-367 行：断言 RuntimeException 过宽。HarnessException 本身就是 RuntimeException，因此 NPE、ClassCastException、IllegalArgumentException（例如 Values.object 在配置值不是 Map 时、URI.create 解析失败时抛出）同样会让测试通过，无法证明“未固定/未知能力被信任配置校验拒绝”。建议改为 assertThrows(HarnessException.class, …) 并校验错误码（如 INVALID_ECOSYSTEM_CONFIG）；第 368-383 行的 mcp 用例同理。
- 判断：RuntimeException过宽可无关NPE通过，应各负例断实际信任配置码，不将所有异常统一猜成一个code。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7ClosureTest.java:357

## OCR-0984 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7ClosureTest.java:464；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：第 467-476 行：redirect/large/unconfigured 三个负例只断言“不是 SUCCESS”，任何 FAILED（连接异常、审批失败、超时等）都会通过，无法定位失败原因；且 calls 只统计 /page，redirect 请求即使根本没发出、或插件错误地跟随了重定向到非 /page 地址，该计数断言也无法区分。建议断言 ToolStatus.FAILED 及具体错误码（重定向被拒、响应超限、未知 source），并为 /redirect 增加独立计数器以证明请求确实发出后被拒绝。
- 判断：redirect/large/unconfigured仅非SUCCESS，补各安全错误码、明确受测endpoint计数及未跟随目标。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7ClosureTest.java:464

## OCR-0985 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7ClosureTest.java:428；OCR原严重度：low
- 计划：R0-03 / P3
- 原意见：第 431-434 行：客户端在读到上限后会主动断开连接，这里一次性写出 1MB+1 字节很可能抛 IOException（Broken pipe）且未被捕获，异常逃逸到 HttpServer 的工作线程，可能在 CI 日志中产生噪声甚至干扰负例判定。建议捕获并忽略 IOException（或在写循环中检测中断后静默退出），保证 /large 场景的失败只由客户端侧大小限制产生。
- 判断：超限负例服务写大体被客户端提前断开是正常，捕获预期broken pipe只减噪；不能吞所有handler失败导致隐藏夹具错误。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7ClosureTest.java:428

## OCR-0986 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:142；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：请求计数只统计了事件侧出现的 `model.requested` 次数，从未与实际模型调用次数 `model.requests().size()` 比对：若某次模型调用没有产生 `model.requested` 事件（例如重试/回放路径漏记日志），`requests` 仍为 4，校验通过，从���让“日志未记录真实调用”这类重放/丢日志缺陷逃逸。另外 `model.requests().get(requests++)` 在事件数多于实际请求时会抛 `IndexOutOfBoundsException` 而不是给出有意义的断言失败信息。建议双向闭合：断言 `model.requests().size() == requests == 4`，并对下标越界做显式判断。
- 判断：skills Docker journal计数4未验FakeLlm总请求数，补等长和逐前缀wire等价，避免额外调用遗漏。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:142

## OCR-0987 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:18；OCR原严重度：low
- 计划：R6-02 / P3
- 原意见：`args` 未做长度校验即取 `args[2]/args[3]/args[4]`，参数缺失时只会抛 `ArrayIndexOutOfBoundsException`，无法得知用法；且输出文件 `args[4]` 的父目录未创建，即便整轮 docker 探针耗时成功，最后一步 `writeValue` 仍可能因 `NoSuchFileException` 丢失全部证据。建议入口处校验 `args.length == 5` 并打印用法，写出前 `Files.createDirectories(output.toAbsolutePath().getParent())`。
- 判断：入口5参数/父目录预检有益，失败报告应保留工作根；不把受控args缺失算生产代码缺陷。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:18

## OCR-0988 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:48；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：本地执行环境固定使用 `Mode.CMD`，但 `LocalProcessRunner.nativeArgv` 在非 Windows 主机上对 CMD/SH 之外的模式直接抛 `UNSUPPORTED_SHELL`（"Native CMD/PowerShell require Windows"）。该探针绑定`docker` 即意味着可在 Linux 上运行，此时本地这一轮在 `skill_demo__run_check` 阶段必然失败，探针得出“parity 失败”的假阳/假阴结论。另外本地脚本用 POSIX 重定向语法 `>`，与其声明模式也不一致。建议按宿主 OS 选择模式（docker 侧始终 SH，因为镜像是 Linux），与本仓库 `M6TerminalTest` 的 `windows ? CMD : SH` 约定保持一致。
- 判断：本地skill脚本固定CMD，仅支持Windows当前运行，新多平台探针应显式选择平台脚本，保持命令审批/marker证据不弱化。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:48

## OCR-0989 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:161；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：清理校验用 `name=` 做未锚定的子串匹配，既可能匹配到同名前缀的无关容器而误报失败，也绕开了创建时已打上的精确归属标识 `io.jharness.owner=<name>`；同时只看 `exitCode()==0 && stdout 为空`，未校验 `remaining.uncertain()`（虽然当前 NativeCommandTransport 的超时分支返回 -1，但协议上约定了 uncertain 标志，校验应显式覆盖）。建议改用 label 过滤（`label=io.jharness.owner=" + owner`）或 `name=^<owner>$` 锚定，并断言 `!remaining.uncertain()`。
- 判断：name子串过滤改精确owner label并显式uncertain=false，保证验证的是本探针资源。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:161

## OCR-0990 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:23；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：docker 工作区由宿主进程以当前用户身份创建（默认目录权限 0755），而容器以 `--user=65534:65534` 运行（见 DockerExecutionEnvironment 构造）。bind mount 保留宿主 uid/权限，因此在 Linux CI 上 uid 65534 无法在 `dockerRoot/skills/demo` 下创建 `marker.txt`，脚本写入会因 Permission denied 失败，completion 证据永远为 false，探针在正常环境下也会失败。建议在创建完 docker 工作区后对 dockerRoot 递归放开该 uid 的写权限（POSIX 下设置 0777 或 chown 65534），或在 docker 侧改用与宿主相同的 uid/gid。
- 判断：Linux nobody写host-owned skills目录可能权限失败，fixture需明确准备容器可写子目录；不能为通过改root执行。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:23

## OCR-0991 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:124；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：探针只断言最终状态为 COMPLETED，但 COMPLETED 只代表 completion 评估器读到 marker 文件；它没有校验任何 `tool.completed` 事件的 `status`。若 `skill_demo__run_check` 返回 UNKNOWN（`DockerExecutionEnvironment` 在 uncertain 时会 close 容器并返回 UNKNOWN，副作用可能已发生），marker 文件已存在即可让评估器返回 true 并最终 COMPLETED，于是 UNKNOWN 状态被当作成功证据接受并“重放”进探针结论。建议额外断言：每个 `tool.completed` 的 `status` 均为 SUCCESS、没有 UNKNOWN/FAILED，且该脚本工具恰好完成一次。
- 判断：COMPLETED应同时核实关键脚本SUCCESS与无未知调用，采纳工具事件/标记关联；不能假设已有Loop UNKNOWN门禁失效。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:124

## OCR-0992 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:179；OCR原严重度：medium
- 计划：R6-02 / P3
- 原意见：`Files.readAllBytes` 把 worker jar 整体读入堆内存求摘要，jar 体积不可控时（几百 MB 以上或膨胀的 artifact）会直接 OOM；用户规则明确要求有界数据处理。建议先 `Files.size` 校验上限，再用 `DigestInputStream`/`MessageDigest.update(byte[], int, int)` 流式分块计算，同时复用已解析的 jar 路径避免重复 `Path.of`。
- 判断：jar哈希readAllBytes可用流式摘要固定内存，统一探针工具并实际绑定同一制品。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:179

## OCR-0993 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:98；OCR原严重度：low
- 计划：R6-02 / P2
- 原意见：审批器对任何调用都无条件返回 true，且探针从不校验审批是否真的被请求过；同时 `reply(...)` 把所有 `ToolInvocation` 声明成 `READ_ONLY`。`DefaultToolPipeline` 会用工具真实的 `sideEffectLevel()`（脚本工具是 SYSTEM_IMPACT）覆盖声明值，所以当前不会漏审批，但探针因此无法发现“SYSTEM_IMPACT 脚本未触发 ASK/审批”的回归，也无法发现审批被绕过。建议把 fixture 的声明级别改为与工具一致，并用计数器断言脚本调用恰好触发一次审批请求。
- 判断：自动批准器不计调用漏ASK回归，补审批计数及exact call；模型伪READ_ONLY不应当修成可信权限来源。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsDockerProbe.java:98

## OCR-0994 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7McpTest.java:414；OCR原严重度：low
- 计划：R6-02 / P3
- 原意见：契约桩的 `exchange` 只识别 notifications/initialized，其余通知（如 notifications/cancelled）会落入 else 分支，最终在 `Map.of("jsonrpc","2.0","id", message.get("id"), ...)` 处因 id 为 null 抛 NPE；同时所有未知方法都被当作 tools/list 并 `pages.incrementAndGet()`，一旦客户端发出额外请求，`assertEquals(2, pages.get())` 会给出与被测行为无关的失败。建议先对 method 判空、显式对 notifications/* 与未知方法返回空响应，仅对 tools/list 计数。
- 判断：通知和未知method回到tools/list可错注入，按明确method匹配且无id通知不造响应；限所测MCP功能避免实现全协议。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7McpTest.java:414

## OCR-0995 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7McpTest.java:356；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：取消测试存在竞态且无法证明“取消生效”：(1) `server.entered.await(...)` 的返回值被忽略，即使请求根本没进入 peer，2s 超时后也会执行 `cancelled.set(true)`；(2) 该虚拟线程未被 join/保存，回调异常或延迟都不可见；(3) 断言 `elapsed < 3s` 与 `HttpMcpTransport` 的 3s 超时重合，而 hang 模式服务端只 sleep 2000ms 就返回不完整响应，因此“完全没取消”的调用同样满足该断言，测试可能在取消未生效时通过。建议：仅当 latch 成功计数后才置位取消、join 线程，并把阈值收紧到远小于 2000ms（如 < 1s），或在服务端记录连接是否被中断来断言。
- 判断：entered.await返回未检查且服务2秒退出与断言3秒/timeout3秒重合，可忽略cancel仍通过；确认进入、延长peer挂起并等待取消完成码/线程退出。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7McpTest.java:356

## OCR-0996 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7McpTest.java:525；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：`getFirst("Accept")` 在请求缺失 Accept 头时返回 null，此处直接 `.contains(...)` 会抛 NPE；同时该异常被下方 `catch (RuntimeException ignored) { invalidHeader.set(true); }` 吞掉，导致任何运行时缺陷（JSON 解析失败、`(String) request.get("method")` 的 ClassCastException、响应已提交后的写入异常等）都被伪装成“协议头违规”，后续 `assertFalse(server.invalidHeader.get())` 只会给出误导性结论且原始异常信息完全丢失。建议做空值判断，并只把预期的协议异常映射为 invalidHeader，其余异常记录后抛出（或至少保存异常信息）。
- 判断：header null NPE被catch RuntimeException混成invalidHeader，补显式缺头及独立handler故障通道；失败标志已存在不会静默继续成功。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7McpTest.java:525

## OCR-0997 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7McpTest.java:478；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`awaitExit` 无任何前置校验：`Files.readString(pidFile)` 在 peer 未写出 pid.txt（例如 `unsupportedSchemaRollsBackWholeScopeAndClosesPeer` 中 start 提前失败）时抛 NoSuchFileException/FileNotFoundException，测试以“文件不存在”而非“子进程泄漏”报错，掩盖真实原因；`Long.parseLong` 未 trim，pid 文件一旦带换行就抛 NumberFormatException；另外 3s 窗口内 PID 被系统复用会得到误判。建议先断言文件存在、trim 后解析，并在最终断言上附带 pid 与消息。
- 判断：pid缺失需明确初始化失败，PID格式/归属先验后等待与有界回收；不对解析失败目标调用kill。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7McpTest.java:478

## OCR-0998 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7McpTest.java:214；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：该用例只断言“不是 SUCCESS”，没有锁定失败原因。流水线在 checkpoint 之前还有工具解析（UNKNOWN_TOOL）、schema 校验（SCHEMA_MISMATCH）、策略/审批（PERMISSION_DENIED）以及 `EventSupport.checkpoint` 的 DURABILITY_REQUIRED 等多处失败点，任何一处失败都会同时满足 `!= SUCCESS`、marker 不存在、trace 中无 tools/call 三个断言，于是“intent 后的 flush 失败绝不触发外部调用”这一核心语义其实没有被验证。建议断言 `result.error().orElseThrow().code()` 等于 CHECKPOINT_FAILED，并断言 journal 中已落盘 tool.intent 事件。
- 判断：!=SUCCESS/marker不存在/无tools-call也可未知工具先拒绝，补CHECKPOINT_FAILED与force命中确保durable边界真正被测。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7McpTest.java:214

## OCR-0999 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7McpTest.java:335；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：该用例构造了带鉴权/协议版本/Session-Id/Accept 校验的 peer，却从未断言 `server.invalidHeader` 为 false，因此 drop/expired 场景下请求头或会话头回归（例如漏发 MCP-Protocol-Version、复用错误 Session-Id）不会被发现，测试会以 UNKNOWN 通过。建议与 HTTP 正常路径用例一致，在结束时断言 `assertFalse(server.invalidHeader.get())`。
- 判断：drop/expired应同正常路径断invalidHeader=false，否则协议头回归也会UNKNOWN而误通过断线语义。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7McpTest.java:335

## OCR-1000 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java:18；OCR原严重度：low
- 计划：R6-02 / P3
- 原意见：`args[0..4]` 被直接解引用，但 `main` 未校验 `args.length`。少传参数时探针会以 `ArrayIndexOutOfBoundsException` 失败（`args[2]` 处即抛出），既不打印用法也不产出任何证据 JSON，在 CI 里很难区分「参数配置错误」与「Docker 探测失败」。建议入口处先校验长度并输出 usage。
- 判断：subagent probe入口args[0..4]可校验用法/目标父目录，属于受控验收工具诊断，不提升安全严重度。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java:18

## OCR-1001 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java:224；OCR原严重度：medium
- 计划：R6-02 / P3
- 原意见：`Files.readAllBytes(Path.of(args[3]))` 会把整个 worker jar 一次性读入堆内存。jar 体积不受探针控制（构建产物可能上百 MB），在低内存 CI/容器中可能 OOM，属于无界数据处理；同时哈希值在读取失败时只会抛出难以定位的 OOM/IOException，缺失「jar 是否与 Docker 实际挂载的一致」的证据。建议改为流式摘要。
- 判断：worker jar一次性读哈希可统一流式摘要并保留size/SHA与实际挂载身份。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java:224

## OCR-1002 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java:20；OCR原严重度：low
- 计划：无
- 原意见：探针在宿主上创建了 `root` 目录树（两个 workspace、sessions JSONL、artifacts，Docker 场景还会有 worker 产物），但全程没有清理，`root` 也没有注册为临时目录。反复执行探针（尤其失败重跑）会持续累积磁盘占用；若容器异常退出，挂载目录还会残留。建议在 `finally` 中删除 `root` 树并保留失败时的路径信息。
- 判断：探针根是调用方专用.build-cache证据留存，阶段合同要求失败日志原件保留，不能默认递归删除所有root；容器/进程资源回收另有明确要求。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java:20

## OCR-1003 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java:192；OCR原严重度：low
- 计划：R6-02 / P3
- 原意见：证据里写的是字面量 4/2，而不是 `reconstruct()` 的实际返回值，`independentVersionGuards`/`parentEditVerified`/`originAndArtifactVerified` 同样是硬编码 `true`。一旦上面的 `require` 判定逻辑被放宽或重构，JSON 证据仍会输出成功值，与事实脱节。建议把 `reconstruct()` 的返回值保存为局部变量后再写入结果（`require` 也复用同一份度量）。
- 判断：硬编码4/2前面确有reconstruct==4/2 require，非无证据成功；可复用实际返回值减少未来漂移，不称现有证据伪造。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java:192

## OCR-1004 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java:151；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：这里对 `subagent.started` 的 payload 做无校验的 String 强转并直接传入 `openReader`：若事件字段缺失或类型变更，会得到 `NullPointerException`/`ClassCastException` 而不是带标签的断言失败。另外 `SessionRecovery.inspect(...).ready()` 只校验日志尾部完整性，并不能证明「UNKNOWN/不确定调用没有被重放」——与注释里 “settled logs” 的语义不符。建议先断言 `childId` 非空，并补充 `new EventSessionProjection(childEvents).uncertainCalls().isEmpty()` 之类的校验。
- 判断：childId强转可加类型断言；SessionRecovery.ready只尾完整并非settled，另查pendingRequests/uncertainCalls保证报告措辞有依据。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java:151

## OCR-1005 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java:173；OCR原严重度：low
- 计划：R3-04 / P2
- 原意见：这里只验证了子会话注册表的工具数量为 1，但没有验证委派结束后父侧插件作用域是否回滚：若 `DelegationTool` 或子侧只读工具残留在父注册表中，本探针不会失败。建议利用已记录的 `parent.requests()` 比较委派前后父请求的工具清单（例如 `parent.requests().getLast().tools()` 应等于首次请求的 `tools()`），从而覆盖「插件作用域回滚/子工具不泄漏」这一关注点。
- 判断：子工具数1不证明父注册表恢复，补委派前后父工具集合相等及原权利保持，不引入额外模型调用。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java:173

## OCR-1006 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceDockerProbe.java:39；OCR原严重度：medium
- 计划：R6-02 / P3
- 原意见：`Files.readAllBytes(jar)` 会把整个 JAR 一次性读入堆内存后再做 SHA-256，对 fat JAR / 大产物存在 OOM 与 GC 压力风险（阈值建议：>100MB 即可触发）。应改为流式摘要，内存占用恒定。
- 判断：韧性probe读整jar求SHA可统一流式，不依赖任意100MB阈值推断必OOM。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceDockerProbe.java:39

## OCR-1007 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceDockerProbe.java:35；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：`result=SUCCESS` 与 `cleanupVerified=true` 都是无条件的常量：本文件从未核对两个 case 结果里返回的 `status` 字段，也从未验证证据目录/临时目录或本地 case 的环境清理（M7 仅对 container 分支校验容器已删除，local 分支无任何清理校验）。报告因此可能宣称“清理已验证”而实际未验证，属于证据可信度缺陷。建议从 case 结果推导 result，并在显式校验后（例如 base 目录按策略删除或断言 docker ps 为空）再写 cleanupVerified；`actualDocker` 同理应由 docker case 的实际执行结果推导。
- 判断：子run已通过require才返回SUCCESS有依据，但cleanupVerified覆盖范围应明示仅受管资源、派生实际检查结果；专用证据目录保留不等于清理失败。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceDockerProbe.java:35

## OCR-1008 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceDockerProbe.java:45；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：报告写入没有创建父目录、也没有原子性保证：`args[4]` 的父目录不存在时直接抛 NoSuchFileException（此时两个 case 已执行完、证据全部丢失）；写入中途失败会留下截断的 JSON，下游解析会把“部分证据”误当成完整证据。建议先 createDirectories，再写临时文件后 ATOMIC_MOVE 重命名。
- 判断：先校验输出目录并原子落盘可避免完整执行后报告写失败/截断；解析失败不能当完整证据，保留失败报告状态。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceDockerProbe.java:45

## OCR-1009 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceDockerProbe.java:25；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：报告只会在两个 case 全部成功时写出：`M7WebDockerProbe.run` 内部用 `require(...)` 抛 IllegalStateException 表示失败，任一断言失败都会让 main 直接抛出，导致 acceptance 失败时完全没有证据文件（连哪些 case 通过、失败原因都丢失）。建议用 try/catch 记录失败 case 的异常摘要，并始终写出报告（result=FAILED）。
- 判断：仅全成功时出JSON使失败摘要缺失，可始终写result=FAILED及已完成case，不覆盖原始日志，不冒称未执行case通过。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceDockerProbe.java:25

## OCR-1010 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceDockerProbe.java:19；OCR原严重度：low
- 计划：R6-02 / P2
- 原意见：JAR 摘要是在两个 case 执行完毕之后才计算的，因此记录下来的 hash 并不能证明“被执行的产物就是这个 hash”（执行期间产物被替换/重打包不会被发现，即 TOCTOU）。建议在运行 case 之前先计算 hash，必要时运行后再算一次并比对，同时把路径解析失败（jar 不存在/非普通文件）给出明确的参数错误信息。
- 判断：执行后才算jarSHA不能确认执行期间同制品，前后哈希/路径/尺寸核对并要求一致，绑定新验收证据。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceDockerProbe.java:19

## OCR-1011 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingDockerProbe.java:41；OCR原严重度：medium
- 计划：R6-02 / P3
- 原意见：`Files.readAllBytes(jar)` 会把整个打包 JAR（含依赖可达成百 MB）一次性读入堆内存再计算摘要，属于无界读取，容器/CI 内存受限时可能 OOM。建议用 `DigestInputStream` 流式读取固定缓冲区计算 SHA-256。
- 判断：调度probe同readAllBytes jar问题，统一流式摘要固定缓冲。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingDockerProbe.java:41

## OCR-1012 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingDockerProbe.java:29；OCR原严重度：high
- 计划：R6-02 / P3
- 原意见：结论字段全部是硬编码，未从两个子探针的返回值推导。`M7WebDockerProbe.run` 返回的证据 Map 只包含 `status`/`environmentClosed`/`hostExitCode` 等键，本身没有记录环境类型（无 `kind`/`container` 字段），因此 `actualDocker: true` 无法被证明；同样 `cleanupVerified: true` 与 `result: "SUCCESS"` 也没有任何校验支撑（Docker 侧仅校验容器移除，且 local 侧工作目录从未做清理确认）。一旦子探针逻辑降级/跳过 Docker 或清理未真正执行，本报告仍会输出干净的 SUCCESS，形成可复现的验收误报。建议在写报告前校验 cases 中的 `status`、`environmentClosed`、`hostExitCode`，并由子探针显式返回环境类型与清理核验结果后再填充这些字段，否则写入 FAILED/UNVERIFIED。
- 判断：actualDocker由显式container=true调用、run内已真实docker cleanup判定，不必每个返回再复制全部字段；采纳报告中显式记录环境身份/验证依据，拒绝无证据常量推断。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingDockerProbe.java:29

## OCR-1013 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingDockerProbe.java:42；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：证据文件直接写目标路径，非原子：序列化中途异常或进程被杀会留下被截断的 JSON，下游验收脚本可能把它读成不完整证据；另外 `args[4]` 的父目录不存在时 `writeValue` 会在整个探针跑完后才失败，白跑一轮且无证据。建议先校验父目录（必要时创建），写入同目录临时文件后 `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`。
- 判断：JSON报告直接写可截断，采纳预检父目录/同目录原子替换，不把不可解析文件视成功。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingDockerProbe.java:42

## OCR-1014 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingDockerProbe.java:20；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：两次子探针串行调用没有 try/finally 兜底清理：`base` 目录（`scheduling-<uuid>`，内含 local、docker 两侧的工作区与 host-config.json）从不删除，且 Docker 阶段一旦抛异常，local 阶段残留的目录/进程/容器也无统一收尾，异常路径下还会直接跳过报告写出，导致证据缺失且残留物跨运行累积。建议用 try/finally 包裹两个 run 调用，在 finally 中删除 `base`（保留需上报的证据文件）并显式记录清理结果，再决定 `cleanupVerified`。
- 判断：单case异常应写失败摘要及确认受管进程容器释放；base为证据目录不默认删，不让清理覆盖主失败。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingDockerProbe.java:20

## OCR-1015 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:468；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：用 `captured.get(reconstructed++)` 做下标访问且无边界检查：若 `model.requested` 事件数多于实际抓取到的请求（例如某次请求未到达 fixture、或截断导致漏抓），这里抛 IndexOutOfBoundsException，掩盖真正的“重建请求数量不匹配”断言信息。建议先判断 `reconstructed < captured.size()` 再用可读消息 require。
- 判断：子探针比captured索引前确认事件/实际发送数量，改善缺帧定位并防额外wire未核验。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:468

## OCR-1016 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:337；OCR原严重度：high
- 计划：无
- 原意见：同一个 45s `deadline` 被复用于审批轮询和完成轮询。慢速审批（例如审批接口/主机响应 20s+）几乎耗尽 deadline 后，完成轮询的 `while` 直接不执行，`state` 仍为 `Map.of()`，随后 `require("Task end: " + state)` 以空状态失败——即使任务稍后确实 FINISHED。触发条件：审批延迟接近 45s。建议完成阶段使用独立 deadline。
- 判断：45秒共用deadline合理代表整场景总预算，审批耗时占预算是既定有界行为；独立重置会偷偷翻倍放宽门禁，不无条件采纳。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:337

## OCR-1017 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:275；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：读取线程在队列满 128 行后直接 `break`，随后关闭子进程 stdout 流。此时子进程若继续写 stdout（`redirectErrorStream(true)` 的日志量很容易超过 128 行），写满管道缓冲区后会阻塞，导致后续 `process.getOutputStream().write(":quit\n")` 与 `process.waitFor(10s)` 超时失败，finally 只能强杀进程。触发条件：任务日志 >128 行。建议持续排空管道（有界队列 offer 失败时丢弃最旧行或直接丢弃该行但继续读取）。
- 判断：lines队列128满即break关闭stdout会让host输出受阻/丢失启动诊断，持续排空并用有界环形摘要/超限失败。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:275

## OCR-1018 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:280；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：`redirectErrorStream(true)` 会把子进程 stderr 合并进同一流，因此首行不一定是 `WEB ...`；任何 JVM 警告/日志前置输出都会被当作启动失败（`startup != null && startup.startsWith("WEB ")` 为 false），且此时 `startup` 变量本身不再保留，诊断信息只显示错误那行。另外 `startup.split(" ")[1]` 对恰好等于 `"WEB "` 的行会抛 ArrayIndexOutOfBoundsException 而非清晰断言。建议在超时内持续取行，直到找到以 `WEB ` 开头的行并校验存在后续 token。
- 判断：stderr合并首行必须WEB可因JVM警告假失败，按有界deadline搜索唯一合法启动行并保留前置诊断，不能无限跳过真实错误。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:280

## OCR-1019 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:83；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：固定读取 1 MiB 后立即解析：超限请求被静默截断（JSON 必然解析失败），且剩余字节未排空。解析异常会从 handler 抛出，HttpServer 直接关闭连接而不返回响应，客户端表现为连接错误而非“超限”这一明确违反，同时 keep-alive 连接被破坏。触发条件：重建请求体 >1 MiB（工具列表/长上下文增长时）。建议显式拒绝超限并保证读取完整。
- 判断：HTTP fixture readNBytes1MB后直接parse超限会连接异常，显式检测+1字节并回受控错误，保持请求前缀等价验证。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:83

## OCR-1020 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:364；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：脱敏校验只覆盖 `/events?limit=100` 的响应体：1) 通过 `lines` 队列捕获的 CLI stdout/stderr 完全未扫描，token 若被打印到控制台不会被发现；2) 其余接口响应（`/v1/tasks`、`/v1/tasks/{id}`、审批列表、error 消息）未检查；3) `limit=100` 可能导致分页截断，末尾事件中的泄漏逃过检测。建议至少同时断言捕获到的进程输出与任务详情响应中不含 TOKEN，并在事件总量超过 limit 时使用分页/更大 limit。
- 判断：Web Docker仅首100事件检查token，补全部分页、详情/错误与进程输出已知秘密不泄漏。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:364

## OCR-1021 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:490；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：cleanup 断言直接使用未校验的 `environmentId` 拼接 docker label：若该字段为空/缺失（payload 结构变化或关闭失败），filter 变为 `io.jharness.owner=null`，`docker ps` 返回空结果，`readAllBytes().length == 0` 成立，于是“容器已删除”被误判为通过。触发条件：`environment.closed` 事件缺少 environmentId。建议先断言 id 非空（例如匹配 `[A-Za-z0-9_.:-]+`）再做查询。
- 判断：environment.closed id缺失拼label=null可能ps空而假清理，应断合法owner/id并精确匹配已创建环境。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:490

## OCR-1022 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:326；OCR原严重度：low
- 计划：R6-02 / P3
- 原意见：`Map.of(...)` 不接受 null 值：若审批响应中缺少 `callId`（或 `approvalId` 为 null 导致路径拼接出 `.../approvals/null`），这里抛 NullPointerException，而不是给出“审批字段缺失”的明确失败原因。触发条件：审批列表返回结构不含 callId。建议先 assert 字段非空再构造请求体。
- 判断：审批callId等缺字段Map.of会NPE，先schema断言提升排查，仍不应在字段缺失时造批准请求。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java:326

## OCR-1023 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsTest.java:248；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：`model.requests().get(sent++)` 没有上界保护：一旦实际产生比 FakeLlm 应答更多的 `model.requested` 事件（例如 SDK 增加重试/修复请求），这里会抛 `IndexOutOfBoundsException` 而不是可读的断言失败，且后面的 `assertEquals(4, sent)` 永远不会执行，排查成本高。建议先做上界断言再取值。
- 判断：请求索引先数量/上界断言并终局与实际数量相等，合并统一重建helper，不仅改善异常文案。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsTest.java:248

## OCR-1024 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsTest.java:174；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：越界资源用例只用 `assertThrows(HarnessException.class, fixture::start)` 断言“启动抛异常”，完全没有校验错误码，因此这条安全边界测试存在假阳性风险：两套规则这里的错误码本来不同——`../outside`/`.git/config`/`/absolute` 走 `RemoteWorkspace.safe` 抛 `PATH_DENIED`，`.env`/`.env.local` 走 `SkillsPlugin.safe` 抛 `SKILL_PATH_DENIED`。若路径逃逸校验回归（例如不再拒绝 `..`），`SkillsPlugin.start` 读到不存在的文件会抛 IOException 并被包装为 `SKILL_IO`，同样是 HarnessException，且同样不会注册任何工具，本用例依旧全绿，无法发现越界保护失效。建议按用例断言精确错误码。
- 判断：越界skill资源启动只异常类型，按path与.env规则分别断正确拒绝码并确认peer/注册已回滚。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsTest.java:174

## OCR-1025 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsTest.java:106；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：停止容器后对旧句柄只断言 `HarnessException` 类型，任何 HarnessException（如 `CONTEXT_CLOSED`、`SKILL_IO`）都能让断言通过，无法证明拒绝真的来自插件停用（`SkillsPlugin.check` 的 `active` 标志 / 注册表吊销）。同时这里绕过注册表直接调用 `AgentTool.execute`，而真实调用链（`fixture.tools.require` / `fixture.execute`）在 stop 之后的拒绝行为没有被覆盖，断言的其实是实现细节而非对外生命周期契约。建议断言错误码为 `SCOPE_DENIED`，并补一条经注册表/管道调用被拒绝的断言。
- 判断：旧句柄吊销应验具体SKILL_DISABLED或scope码和新查找不可见，保留直接调用旧引用测试，因为其正检验生命周期。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsTest.java:106

## OCR-1026 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsTest.java:183；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：只覆盖了 65537 字节的越界值，未覆盖精确边界。`SkillsPlugin.read` 的判定是 `bytes.length > 65536`，即 65536 字节应当被接受；当前边界条件（恰好等于上限）没有任何用例保护，若实现被改成 `>=` 本用例仍会通过。建议补充“恰好 65536 字节时仍能正常注册”的断言（错误码已断言为 `SKILL_FILE_LIMIT` 这点没问题）。
- 判断：65537只超界负例，补恰65536允许与实际读取一致，避免>=边界收窄。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7SkillsTest.java:183

## OCR-1027 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7WebTest.java:325；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：第二页请求没有断言状态码：若服务端因游标/查询问题返回 400 错误体（响应中没有 `next` 字段），`second.get("next")` 就是 null，`assertNotEquals(page.get("next"), null)` 依然成立，断言变成空转，无法发现“大响应下分页游标丢失/错误”的缺陷（这正是该用例要覆盖的点）。建议先断言 200 并确认 events 非空，再比较 next。
- 判断：第二页未HTTP200且next不同可错误JSON假通过，先响应/schema/非空事件再核游标末尾及总量。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7WebTest.java:325

## OCR-1028 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7WebTest.java:896；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：这里的 `assertThrows` 并不能验证“仅有 model.verified 审计仍不足以完成离线验证”这一不变量：外层 try-with-resources 已持有 `sessions` 的 EXCLUSIVE 写锁，`HarnessWebServer.verifyTask` 内部会调用 `sessions.openWriter(taskId, WriteOwnership.EXCLUSIVE)`（HarnessWebServer.java:2549），而 `JsonlSessionRepository.openWriter` 对同一 lockPath 会因 `OWNERS.add` 失败抛 `WRITE_CONFLICT`。也就是说异常来源是“锁冲突”而不是“底层审计校验失败”；即使实现错误地接受只有 model.verified 的状态，该断言依旧会通过，随后锁释放后的成功调用又掩盖了真实行为。建议在追加 model.verified 后先关闭 writer，再在“无锁占用”前提下断言失败，从而真正校验审计/恢复状态判定。
- 判断：model.verified负例在持EXCLUSIVE writer时调用verifyTask，会先锁冲突而非语义拒绝；释放writer后单独断预期未核实环境原因。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7WebTest.java:896

## OCR-1029 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7WebTest.java:182；OCR原严重度：low
- 计划：R0-03 / P3
- 原意见：`Set.of("FINISHED", "UNKNOWN").contains(v.get("phase"))` 在响应体缺少 `phase` 字段（降级/异常响应、字段名变更）时会把 null 传给不可变集合，JDK 的 `ImmutableCollections.contains(null)` 抛 NullPointerException，await 会以 NPE 失败并掩盖真正的状态问题（本用例正是用来验证 UNKNOWN 恢复，字段缺失是高概率场景）。建议先做类型/空值判断。
- 判断：Set.of.contains(null)在缺phase报NPE，可先schema断言并给安全响应；异常本来使测试失败，非假绿。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7WebTest.java:182

## OCR-1030 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7WebTest.java:627；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：方法名声明要验证“ErrorDetailsAreNotExposed”，但方法体只断言了 UNKNOWN 状态、SessionRecovery 未就绪与 409 阻塞，没有任何检查错误详情（provider 原始异常文本、堆栈、内部路径、凭据）是否泄漏到 HTTP 响应或会话事件中。建议像 `catalogCheckpointFailureCannotConstructSdkOrRetryAdmission` 那样，断言 task/events 响应体与会话 payload 不包含敏感细节（例如 TOKEN 或注入的异常消息），否则这条安全断言实际缺失。
- 判断：命名ErrorDetailsAreNotExposed但无哨兵检查，注入安全固定provider错误并核HTTP/JSONL无详细原文和秘密。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7WebTest.java:627

## OCR-1031 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M7WebTest.java:697；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：`waitFor` 返回 false（git 卡住/钩子阻塞）时 assertTrue 直接失败，但子进程从未被销毁，其 stdin/stdout 管道会残留，进而在 @TempDir 清理或后续用例中造成不稳定。建议在超时分支显式 `process.destroyForcibly()`。
- 判断：git.waitFor5秒超时assert失败不destroy，finally在确认拥有的子进程上回收/关流，保留超时失败。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M7WebTest.java:697

## OCR-1032 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/SdkTransportPeer.java:19；OCR原严重度：high
- 计划：无
- 原意见：`ids` 正则只匹配字符串型 id，且不带转义处理；只要出现一条不含字符串 id 的输入行（合法的 JSON-RPC 通知 `{"jsonrpc":"2.0","method":"..."}`、数值型 id `"id":1`、或者一条被截断的帧），这里直接 `return`，整个对端进程静默退出，后续排队的所有请求都收不到响应，测试侧将表现为超时/EOF，而不是可诊断的协议错误。触发条件：客户端在任意请求后发送通知或使用非字符串 id。建议对无 id 行 `continue`（通知按规范不应回复），仅在 stdin EOF 时退出。
- 判断：SdkTransportPeer专门测试本SDK字符串identifier的有限协议及坏帧故障，数字/含转义id不在该SDK合法集合；无需变完整JSONRPC服务。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/SdkTransportPeer.java:19

## OCR-1033 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/SdkTransportPeer.java:51；OCR原严重度：critical
- 计划：无
- 原意见：第 51-52 行把真实的 `JHARNESS_API_KEY` 环境变量值直接打印到 stderr，随后被测试进程的 stderr 采集逻辑读入，可能落入测试日志/构建产物（报告/CI 输出），违反敏感信息脱敏要求；同时该分支输出随运行环境变化，若密钥未设置会打印 `null`，使测试结果不可复现。触发条件：任何包含 `"stderr"` 的请求行，且测试进程继承了真实密钥环境变量。建议改为固定的占位/伪值，仅用于验证 stderr 读取通道。
- 判断：唯一验收入口sdk/tests/run.mjs显式将JHARNESS_API_KEY覆盖为synthetic-sdk-UUID，TS/Python发送stderr用来检查诊断不进入API；非真实继承凭据泄漏。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/SdkTransportPeer.java:51；sdk/tests/run.mjs:9；sdk/tests/run.mjs:35；sdk/tests/client.ts:59；sdk/tests/client.py:111

## OCR-1034 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/SdkTransportPeer.java:65；OCR原严重度：high
- 计划：无
- 原意见：请求 id 被直接拼进手工构造的 JSON，未做任何转义。若客户端请求 id 含 `"`、`\` 或控制字符（例如 `a"b`、`a\\b`、`\u0000`），输出将是非法的 JSON，或被解析成额外字段，导致 framing/背压测试把“对端返回坏帧”误判成“客户端解析能力”问题；同理第 39-43 行的 `duplicate` 分支也把 id 原样插入两次。建议集中一个转义函数（至少处理 `"`、`\` 及 <0x20 的控制字符），在 `reply` 与 duplicate 分支统一使用。
- 判断：SDK字符串id受identifier规则限制字母数字下划线连字符，peer由SDK生成，所述引号/反斜杠合法id不可达；无需扩展测试peer协议范围。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/SdkTransportPeer.java:65

## OCR-1035 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/SdkTransportPeer.java:23；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：`slow` 分支启动的虚拟线程没有被收集或 join，而主循环在三条路径上会直接退出：stdin EOF、`shutdown` 分支的 `return`、以及上面无 id 行的 `return`。这些情况下主线程返回后 JVM 退出，sleep 中的虚拟线程及其 `reply` 会被丢弃，客户端等待不到迟到的响应，背压/超时测试随机失败。另外 `slow` 计数在收到请求时即自增（而非响应发出后），`stats` 里的 `slowCalls` 可能把尚未完成的调用算进去，断言时序敏感。建议用列表/`ExecutorService` 跟踪线程，在退出前统一 join，并把计数改为在响应发送后自增（或明确语义）。
- 判断：迟到响应的虚拟线程可在主进程EOF时被丢弃，需使背压/超时用例显式等待并确认late reply路径；slowCalls表尝试非完成可文档化。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/SdkTransportPeer.java:23

## OCR-1036 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/SdkTransportPeer.java:22；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：故障模式用整行 `contains` 判定，而整行同时包含 id 与业务 payload，因此任一处出现这些单词都会误触发：例如 id 为 `slow-1` / `stats-42` 的普通请求会走 slow 分支，payload 里出现 `"initialize"` 的 stats 请求会返回初始化结果，`hold`/`duplicate`/`oversized` 同理。这会让测试用例因 id 命名而行为错乱，且失败难以定位。建议对输入做最小 JSON 解析，仅根据 `method` 字段（或明确约定的 `fault` 字段）选择故障模式，id 只用于回包。
- 判断：整行contains可被id/payload模式词误触，按method解析更稳健；当前SDK生成id和用例输入受控，未证明生产客户端故障。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/SdkTransportPeer.java:22

## OCR-1037 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8FilterIntegrationTest.java:91；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：对 config.selected 的校验过松：用 payload().toString().contains("hidden.txt") 判断，只要任意无关字段（如 selection、resolved 中的其它文本）出现该字符串即通过，既无法确认 filter 是结构化落盘的，也无法发现 filter 结构回归。更关键的是 classic 路径把 filter 记为 payload["searchFilter"]（CliApplication 第 903 行），而 ecosystem 路径只把整个配置埋在 payload["resolved"] 里（EcosystemCli 第 148 行），这里恰好掩盖了两条路径记录结构的差异。建议先断言 config.selected 至少存在一条，再从结构化位置取出 searchFilter 与 WorkspaceSearchFilter.configuration() 做相等比较（兼容 resolved 嵌套），并顺便断言 toolScheduling 也被记录。
- 判断：config.selected用toString含hidden.txt不足以证明filter结构，分别解析classic/eco实际schema并比完整配置和来源。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8FilterIntegrationTest.java:91

## OCR-1038 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8FilterIntegrationTest.java:179；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：这里的 allMatch 在 tool.scheduled 流为空时恒为 true，是一条可空过的断言：只要某些变化导致 dry-run 不再记录调度决策（或 write 调用根本没进入调度），用例依然通过。而且它只检查「所有事件的 mode 都不是 readonly-parallel」，没有定位到 write 调用本身。建议先断言存在调度事件，再断言全部为 serial，并显式断言 callId=write 的事件以 serial 调度。
- 判断：tool.scheduled allMatch可空过，应先计有事件并定位write call为serial且无body执行。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8FilterIntegrationTest.java:179

## OCR-1039 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8FilterIntegrationTest.java:76；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：用例通过 apiKeyEnv/FIXTURE_API_KEY 注入了真实凭据值到模型请求路径，并整体读取了会话事件，但从未断言事件流中不出现该凭据（如 model.requested、错误信息或 config.selected 中泄漏）。一旦 SecretRedactor 在某一类事件上回归失效，本用例仍会通过。建议在读取 events 后断言所有事件序列化文本中不包含 "fixture-key"，并校验含敏感字段的事件确实已脱敏。
- 判断：本场景fixture-key应在原始会话/请求/报告检查，确保config.selected和工具结果实际触达秘密路径。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8FilterIntegrationTest.java:76

## OCR-1040 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8FilterDockerProbe.java:110；OCR原严重度：medium
- 计划：R6-02 / P3
- 原意见：成功报告与“实际 Docker 执行/镜像身份”没有绑定：`actualDocker=true`、`result=SUCCESS`、`cleanupVerified=true` 都是常量，`image` 只记录 `args[1]` 的 tag（第 111 行），没有任何 `docker version`、镜像 digest 或删除校验的退出码/stderr 证据。触发条件：`args[0]` 指向一个按预期输出 JSON 的桩程序，镜像被重新 tag 成同名，都能产出看似可信的“实际 Docker 验收”证据。建议在写报告前执行 `docker version --format` 与 `docker image inspect --format {{.Id}}` 取真实版本与 digest，写入报告，并由删除校验的实际结果推导 `cleanupVerified`（而不是硬编码）。
- 判断：actualDocker来源显式DockerExecutionEnvironment，历史外部docker二进制本为可信工具；采纳镜像ID/digest/daemon记录提高可复现性，不宣称替换恶意docker桩是产品漏洞。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8FilterDockerProbe.java:110

## OCR-1041 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8FilterDockerProbe.java:104；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：清理校验把“inspect 非 0 退出”直接当作“容器已删除”（第 104 行）。但 `docker inspect` 只有“对象不存在”时才返回 1；二进制不可执行、daemon 不可达、权限不足、`docker.id()` 返回空串/非法 id、上下文失效等都会返回非 0（或 -1），此时 probe 依然会打印 SUCCESS 并写入 `cleanupVerified=true`，而容器实际仍存活——正是最容易泄漏容器的场景被判为通过。触发条件：daemon 拒绝连接/超时被 NativeCommandTransport 转成非 0 且 uncertain=false，或 `docker.id()` 未真正拿到容器 id。建议先断言 owner 非空白，再要求 `exitCode()==1` 且 stderr 明确包含 "No such object/container"；其它退出码与 stderr 内容一律视为未验证并失败（或用 `docker ps -a --filter id=<owner>` 断言结果为空）。
- 判断：源码已经拒绝uncertain，原文遗漏该检查；inspect确定非零仍可能daemon错误而非不存在，改成功ps按精确owner空集验证。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8FilterDockerProbe.java:104

## OCR-1042 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8FilterDockerProbe.java:97；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：容器删除确认被放在两个配置全部通过断言之后（第 97-106 行），不在 finally 中。触发条件：`inspect(local/docker, filter)` 抛异常、第 69-70 行 `Filter worlds differ`、第 86-87 行路径期望不符，或 try-with-resources 里 `docker.close()` 自身失败——任何一种都会在到达 owners 循环之前中断，容器残留在宿主机上且没有任何清理证据，报告里也不会留下失败原因。建议把每个配置的 owner 删除校验放进该配置 try 的 finally（或整体 try/finally），保证异常路径也执行删除确认并把结果写入证据文件。
- 判断：容器close已try-with-resources，断言失败不会跳过close本身；采纳finally保留残留检测/失败报告，不称完全无人回收。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8FilterDockerProbe.java:97

## OCR-1043 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8FilterDockerProbe.java:168；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：越界测试没有真正构造越界目标：`../outside.txt` 从未在 base 目录外创建，因此这里只证明了“不存在的路径”被拒绝，不能证明“存在的外部文件”被边界拒绝；若实现先做存在性检查再做边界检查，会抛出别的错误码，反而让第 173 行把 probe 判为失败而非验证失败。同时 `root/escape.txt -> ../outside.txt` 这类符号链接逃逸完全未覆盖，而报告仍硬编码 `metadataAndTraversalDenied=true`。建议在 base 下真实写入 outside.txt（并创建指向它的符号链接），分别断言读取已存在的外部目标与符号链接均返回 `PATH_DENIED`。
- 判断：../不存在会在路径边界先拒绝本已有效，可加真实外部文件/链接及精确错误加强；报告不能把该一项泛化所有边界都验证。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8FilterDockerProbe.java:168

## OCR-1044 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8FilterDockerProbe.java:166；OCR原严重度：low
- 计划：无
- 原意见：`cache/drop.txt` 在配置 0 中被 search/list/grep 全部排除，但这里断言它仍可被 `readText` 读取。这与 `WorkspaceSearchFilter` 类注释“never an access-control grant”一致，属于预期语义；但断言被命名为 `explicitReadRetained` 并作为 SUCCESS 证据输出，容易被下游误读为“被过滤文件即机密边界”。建议在报告与断言消息中显式声明“search filter 不是访问控制边界”，若产品上要求被过滤文件不可读，则应改为断言 `PATH_DENIED`。
- 判断：现断言消息直接写Filter changed read authorization，报告explicitReadRetained准确表达合同；无需为假想下游误读把合法读取当缺陷。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8FilterDockerProbe.java:166

## OCR-1045 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/web/WorkbenchContentSearchTest.java:96；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：分页验证过弱：第二页只用 assertNotEquals 断言“和第一页不同”，没有校验第二页实际命中（按实现排序应为时间戳倒序的 s2）、total/more/next 与快照的一致性，也没有断言翻页最终会终止。若实现返回空列表、跳过某条命中或重复返回同一事件，本断言仍会通过。建议断言 next.matches 的 sessionId/eventId 具体值、more 与 next 字段，并循环翻页直到 next 为空且累计命中数等于 total。
- 判断：Search第二页notEquals不能证明实际匹配/无遗漏，断具体session/event与总量、more、next终止和快照稳定。
- 证据：harness-testkit/src/test/java/io/jharness/web/WorkbenchContentSearchTest.java:96

## OCR-1046 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/web/WorkbenchContentSearchTest.java:40；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：query 辅助方法保留了 rebuild 参数，但所有调用点都传 false，重建索引路径完全没有被覆盖；同时实现中“携带 cursor 且 rebuild=true 应拒绝（SEARCH_CURSOR_EXPIRED）”的协议约束也未被验证。若重建分支把未完成/UNKNOWN 状态的内容当作命中返回，本测试无法发现。建议补充首次查询 rebuild=true 时断言 rebuiltSessions>0，以及 cursor+rebuild=true 返回 SEARCH_CURSOR_EXPIRED 的用例。
- 判断：所有rebuild=false漏显式重建/cursor冲突，补正负例；UNKNOWN会话正文允许授权只读搜索，不能把返回未决内容等同工具重放。
- 证据：harness-testkit/src/test/java/io/jharness/web/WorkbenchContentSearchTest.java:40

## OCR-1047 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/web/WorkbenchContentSearchTest.java:48；OCR原严重度：high
- 计划：R1-04 / P2
- 原意见：注入了 SecretRedactor，但写入的事件内容中没有任何敏感串，测试也从未断言 snippet 已被脱敏。这样一旦搜索结果/片段泄露密钥（如 sk-xxx、Authorization 头），本测试仍然通过，属于安全回归的覆盖缺口。建议写入包含密钥形态字符串的事件，并断言返回 hits 的 snippet 中不包含原始密钥。
- 判断：provider空Redactor及无秘密事件未触达snippet脱敏，注入已配置哨兵并核所有搜索片段无原始值。
- 证据：harness-testkit/src/test/java/io/jharness/web/WorkbenchContentSearchTest.java:48

## OCR-1048 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/web/WorkbenchContentSearchTest.java:171；OCR原严重度：low
- 计划：R5-02 / P2
- 原意见：只构造了 257 条 scope 验证超限，未验证恰好 256 条应被接受（实现边界是 scope.size() > 256），边界差一的缺陷无法被发现。另外该用例的 provider 为空，虽然验证了 SCOPE_LIMIT 优先于 SEARCH_UNAVAILABLE，但没有验证 provider 可用时 256 条能正常返回。建议补 256 条成功、257 条失败的对照用例。
- 判断：257只负例不锁定256包含边界，配有效provider补256成功/257 SCOPE_LIMIT。
- 证据：harness-testkit/src/test/java/io/jharness/web/WorkbenchContentSearchTest.java:171

## OCR-1049 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/web/WorkbenchContentSearchTest.java:98；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：cursor 失效只覆盖了 text 变化和 archived 变化两种场景，profile、view、limit、sessionId 变化以及 scope 中会话增删均未覆盖。cursor 是服务端全局快照 id，跨 profile 复用 cursor 属于潜在的越权读取路径（换工作区拿到别的范围的结果），建议用同一 cursor 切换 profile（如 "other"）或改变 limit/view 再查询，断言返回 SEARCH_CURSOR_EXPIRED。
- 判断：cursor变化只text/archive，补profile/view/limit/session范围切换失效和跨scope不复用快照。
- 证据：harness-testkit/src/test/java/io/jharness/web/WorkbenchContentSearchTest.java:98

## OCR-1050 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingTest.java:613；OCR原严重度：low
- 计划：R6-01 / P3
- 原意见：`registry` 声明后从未被读取（同一个测试里 `M7EcosystemFixture` / `ToolSchedulingPlugin` 已经完成服务注册，replacement 调度器也无需手工注册），属于死代码，且容易让读者误以为需要把 replacement 写入该 registry。建议删除该变量；如果确实想验证「replacement 被注册进服务表」，则应改为直接断言该 registry，而不是声明后弃用。
- 判断：unused registry可清理减少误导，但没有功能/安全影响，不单独占高优修复阶段。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingTest.java:613

## OCR-1051 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingTest.java:258；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：该断言在 `actualOrder` 缺少 "b" 时会虚假通过：`indexOf("b")` 返回 -1，而 `indexOf("a")` 为 0，`-1 < 0` 成立，从而掩盖「b 根本没有执行」的并发缺陷。建议先断言两个元素都已被记录，再比较先后顺序。
- 判断：indexOf b=-1可能让相对顺序通过，补actualOrder含全部主体执行id；相邻completed全id只证明结算，不证明body记录齐全。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingTest.java:258

## OCR-1052 · 不采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingTest.java:265；OCR原严重度：low
- 计划：无
- 原意见：该断言把「所有 tool.* 事件必须在调用者线程写入」这一实现细节固化为测试契约。一旦日志写入改由 worker 线程完成（即使事件顺序仍然正确），测试会误报失败；反之，它也不能证明排序正确性（排序已由后面 tool.completed 的 callId 序列断言覆盖）。建议改为断言事件顺序/内容，而不是线程身份。
- 判断：M8.2明确owner单writer写tool.completed是契约，journalThreads测试正防worker写事实，非可随意删除的实现细节。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingTest.java:265；docs/development/M8-contracts.md:M8.2

## OCR-1053 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingTest.java:488；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：这里只断言抛出通用 `HarnessException`，任何原因（会话已关闭、预算/步数超限、环境错误等）都能让它通过，无法真正验证「UNKNOWN 状态不得被重放」的协议。`DefaultAgentLoop` 在 `project().uncertainCalls()` 非空时抛出 code 为 `UNKNOWN_SIDE_EFFECT` 的异常（harness-agent/.../DefaultAgentLoop.java:42-44），建议断言具体错误码。
- 判断：UNKNOWN重开只异常类型可无关失败，断UNKNOWN_SIDE_EFFECT且模型/工具发送数不增。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingTest.java:488

## OCR-1054 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingTest.java:484；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：这里用 60ms 调度超时 + 释放后固定 `Thread.sleep(50)` 来观察「迟到结算不得二次落盘」。在负载较高的 CI 上，60ms 可能在 worker 真正进入 body 之前就到期，而 50ms 也可能不足以让迟到写入完成，导致偶发失败或漏检。建议使用更长的超时（数百毫秒）并在释放前用 latch 确认 body 已进入，然后用带上限的轮询等待条件成立，而不是固定睡眠。
- 判断：60ms超时和固定50ms晚结算观察不确定，确认body已入/退出闩锁并有界等待无重复completed，保持产品限额不弱化。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingTest.java:484

## OCR-1055 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingTest.java:665；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：`container.stop()` 之后的拒绝只用通用 `HarnessException` 校验，无法区分是「调度器已关闭」还是其他原因（上面对 `wrong` 作用域的断言同样只校验异常类型）。建议分别断言具体错误码（如关闭后的 SCHEDULER_CLOSED 与作用域不匹配的错误码），以确认测试真的覆盖了调度器吊销与作用域隔离两端，而不是被无关异常蒙混通过。
- 判断：stop后与wrong scope分别断SCHEDULER_CLOSED/作用域错误并确认主体未入，不能只泛HarnessException。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingTest.java:665

## OCR-1056 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/TestkitTest.java:60；OCR原严重度：low
- 计划：R6-01 / P2
- 原意见：撤销订阅后仅断言事件数量为 2，属于弱断言：若实现错误地在撤销时丢掉已有事件 `two` 同时又捕获了 `three`，数量仍为 2，测试会假绿；也无法证明撤销只影响后续事件而不破坏既有快照。触发条件是 EventRecorder 撤销逻辑误删/替换历史事件。建议改为断言精确内容 `List.of(one, two)`。
- 判断：撤销订阅后只计数2无法发现历史被替换，断完整one/two及之前快照保持。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/TestkitTest.java:60

## OCR-1057 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/TestkitTest.java:105；OCR原严重度：medium
- 计划：R6-01 / P2
- 原意见：测试名声称 `CleansOnlyItsOwnedDirectory`，但用例只在正常关闭后断言 `root` 不存在，从未在 `root` 之外放置哨兵文件/目录。若 `TemporaryProject.close()` 的边界校验（`startsWith(root)`）或符号链接处理退化为误删外部路径，本测试仍然通过。建议在 `root` 的父目录（或在 `root` 内创建一个指向外部哨兵的符号链接，Windows 不支持时退化为同级哨兵）创建独立文件，关闭后断言该哨兵仍存在且 `root` 已删除，以实际覆盖“只删除自有目录/不跟随符号链接”的边界。
- 判断：owned directory清理可加外部哨兵/根内链接不跟随验证，严格限制自己临时目录并保留失败证据。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/TestkitTest.java:105

## OCR-1058 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/TestkitTest.java:145；OCR原严重度：low
- 计划：R6-01 / P2
- 原意见：隔离性只做了否定断言：若 `ScopedToolRegistry` 完全忽略子作用域注册、对任意 `child` 都返回空，本测试依旧全部通过，无法证明同名工具可以按作用域独立注册与回滚。建议在 `child` 作用域注册同名 `read` 工具，断言 `child` 可见、`parent` 不可见，并在 `old` 仍有效时断言 `visibleTools(parent)` 恰好包含该工具；随后分别关闭两个注册并确认只移除各自作用域。
- 判断：仅子scope查空不能证明两边可注册同名并独立回滚，补子工具正例及分别关闭只移除本scope。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/TestkitTest.java:145

## OCR-2359 · 不采纳

- 位置：harness-cli/pom.xml:4；OCR原严重度：low
- 计划：无
- 原意见：JNA 版本 5.14.0 在子模块中硬编码，父 POM 的 dependencyManagement 只管理了 junit-jupiter。JNA 是本模块唯一直接使用的原生访问库，版本一旦与将来父 POM 管理版本（或其他模块声明的 JNA 版本）不一致，Maven 会按最近优先路径选中不同版本，可能出现 native 库加载失败/符号不匹配，而这类问题只在运行期（加载 native 库时）才暴露。建议把 JNA 版本提到父 POM 的 dependencyManagement（或 ${jna.version} 属性）统一管理，子模块只声明 groupId/artifactId。
- 判断：JNA当前仅CLI直接依赖固定5.14.0，未存在父管理或其它声明冲突；提版本到父是未来维护选择，不是现行依赖错误。
- 证据：harness-cli/pom.xml:4

## OCR-2360 · 不采纳

- 位置：harness-cli/pom.xml:24；OCR原严重度：medium
- 计划：无
- 原意见：shade 配置只注册了 ManifestResourceTransformer，没有 ServicesResourceTransformer/AppendingTransformer。shade 对重复资源默认是“先到先得”，因此当多个被打包依赖（JNA 或后续引入的 harness-llm/harness-ecosystem 等）各自带有 META-INF/services/* 时，除第一个以外的服务注册项会被静默丢弃；一旦插件/工具发现、日志实现或序列化扩展改为 ServiceLoader 注册，打出的 fat jar 在运行时会直接出现 “No provider found” 之类的运行时故障，而 mvn test（未打包、类路径为独立 jar）无法复现。触发条件：任意一个被打包模块或第三方依赖提供 META-INF/services 文件。建议补上 ServicesResourceTransformer（并保留对 Manifest 的配置）。
- 判断：当前fatJAR实际服务条目仅Jackson ObjectCodec/JsonFactory和SLF4J provider，未指出任何当前同服务多provider被遮蔽；项目也无ServiceLoader插件发现承诺。未来增加依赖后需要merge时再改，不以可能性加构建变更。
- 证据：harness-cli/pom.xml:24；harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar:jar tf（已有制品，非新构建）

## OCR-2361 · 不采纳

- 位置：harness-cli/pom.xml:0；OCR原严重度：medium
- 计划：无
- 原意见：filter 只剔除了签名类文件，未处理各依赖 jar 中同名的 META-INF/NOTICE、META-INF/LICENSE、META-INF/DEPENDENCIES 等元数据。shade 对同名条目采用先到先得，最终 jar 中每个路径只保留一份，结果可能是某个第三方组件的 NOTICE/许可证声明被丢弃（合规风险），也可能是本模块精心放入 META-INF/jharness 的许可文件与依赖声明并存造成混淆。触发条件：任意两个被打包依赖同时包含 META-INF/NOTICE 或 META-INF/LICENSE（Apache 系依赖几乎必然满足）。建议显式排除这些重复条目并由本模块统一提供汇总的 LICENSE/NOTICE，或使用 ApacheLicenseResourceTransformer/ApacheNoticeResourceTransformer 做合并。
- 判断：项目已将第三方逐组件法律材料放META-INF/jharness/third-party并有M9制品验证，不能仅因普通META-INF/LICENSE合并先到先得认定法律材料丢失；需具体丢失文件证据。
- 证据：harness-cli/pom.xml:声明/现有代码

## OCR-2362 · 部分采纳

- 位置：harness-cli/pom.xml:19；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：这里把仓库根下的整个 ../third-party 目录原样打进 fat jar，既没有 includes/excludes 过滤，也没有对目录是否存在的显式控制。触发条件下会出问题：(1) 该目录新增源码包、tar/zip 压缩包、测试夹具或任何体积较大的二进制文件时，会直接膨胀 CLI 产物（资源拷贝阶段还会遍历整棵树，构建时间随之上升）；(2) 若其中混入不允许再分发的文件，会被一并发布；(3) 目录内容随开发机状态变化，破坏已由 project.build.outputTimestamp 建立的产物可复现性。建议只 include 需要的文件名/子目录（例如 *.txt、特定 notice 文件），必要时显式声明资源目录不存在时的行为。
- 判断：third-party整目录资源无includes，采纳限制为已审计法律文件/清单并验证包内清单与输入SHA；当前目录本就是法律材料，无现有任意二进制分发证据。
- 证据：harness-cli/pom.xml:19

## OCR-2363 · 不采纳

- 位置：harness-cli/pom.xml:0；OCR原严重度：low
- 计划：无
- 原意见：createDependencyReducedPom=false 使得模块产出的 POM 仍保留全部 internal 模块依赖，而实际安装/部署的主 artifact 已被 shade 替换为包含这些类的 fat jar。若 harness-cli 被其他项目作为依赖引入（或未来拆出可复用的 CLI API），消费者会同时拿到 fat jar 内的类与 POM 声明的传递依赖，形成重复类/版本冲突，并且该类问题在构建期不会报错。建议在明确 CLI 仅作为可执行产物发布时补充 maven-install/deploy 的说明，或改为 <createDependencyReducedPom>true</createDependencyReducedPom> 并配置 dependencyReducedPomLocation。
- 判断：harness-cli当前发行是可执行fatJAR非供外部Java引用的库，createDependencyReducedPom=false保留多模块构建依赖合理；未来依赖用途无当前重复类故障。
- 证据：harness-cli/pom.xml:声明/现有代码

## OCR-2368 · 部分采纳

- 位置：harness-testkit/pom.xml:7；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：依赖作用域过宽：`harness-testkit` 的 `src/main/java`（CheckpointRecorder/EventRecorder/FailingSessionWriter/FakeLlm/TemporaryProject）只 import `io.jharness.api`，第 8–19 行的 web/cli/process/policy/workspace/tools/agent/llm/events/session/plugin/ecosystem 实际只被 `src/test/java` 使用。以 compile 作用域声明后，任何把 testkit 引入测试（甚至误引入生产）的模块都会被传递引入 CLI、Web 服务器、进程执行、插件等全部实现模块，造成类路径污染、传递依赖收敛/版本冲突和测试类初始化开销。建议仅 `harness-api` 保留 compile，其余改为 `test`（testkit 自身测试仍可编译）或 `<optional>true</optional>`。
- 判断：testkit main只需api，其余大量集成依赖可设test缩小发布闭包；需核对下游实际依赖testkit transitive用途后迁移，非生产循环漏洞。
- 证据：harness-testkit/pom.xml:7

## OCR-2427 · 采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EvidenceReport.java:83；OCR原严重度：high
- 计划：R4-03 / P2
- 原意见：EvidenceReport 对 tool.completed payload 中的显式 null 缺乏防护，会导致报告生成失败：第 83-91 行在 status 缺失或为 null 而进入非 SUCCESS 分支后，Map.of("status", null, ...) 必然抛 NPE；当 error 键存在且值为 null 时，getOrDefault("error", Map.of()) 也返回 null。第 78-79 行 Values.object(e.payload().getOrDefault("value", Map.of())) / display 在键存在且值为 null 时收到 null，Values.object(null) 会抛 INVALID_JSON HarnessException（Values.snapshot/freeze 允许 null，历史或外部日志可构造）。任一情况都会使 EvidenceReport.build 失败、report 命令无法生成报告。建议统一做 null/类型校验：status/error 用可变 Map 或 requireNonNullElse 兜底，value/display 先判空再 Values.object，并在遇到损坏 payload 时降级而非中断整份报告。
- 判断：tool.completed非SUCCESS时Map.of(status,error)遇显式null必NPE，应验证事件schema并报告无效/未验证，不默认成功或吞掉不确定事实。
- 证据：harness-cli/src/main/java/io/jharness/cli/EvidenceReport.java:83

## OCR-2428 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EvidenceReport.java:75；OCR原严重度：high
- 计划：R4-03 / P1
- 原意见：该 `WORKSPACE_WRITE` 变更收集分支没有校验 `status == SUCCESS`，会把未确认的写入当作“已交付变更”写进 `recordedFacts.changes`。触发条件：`apply_patch` 部分成功时 `LocalWorkspace.applyPatch` 返回 `partial=true`，`WorkspaceToolsPlugin` 以 `ToolStatus.UNKNOWN` 结算但 value 中仍带 `changes`，于是 UNKNOWN 状态下的写入被无标记地记录（违反“UNKNOWN 状态不得被重放/当作已确认事实”），同时上面第 64-73 行的 display 分支与本分支可能对同一变更各追加一次，造成重复证据。另外 `getOrDefault("changes", ...)` 在 key 存在且值为 null 时返回 null，变更会被静默丢弃。建议增加 SUCCESS 门控、用 `requireNonNullElse` 取值并按 eventId+path 去重。
- 判断：批量changes分支无SUCCESS门槛，UNKNOWN部分patch仍进入changes；reference已有toolStatus，不是完全无标识，但应明确已确认/部分未知分区避免误称完成。
- 证据：harness-cli/src/main/java/io/jharness/cli/EvidenceReport.java:75

## OCR-2429 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EvidenceReport.java:47；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：turn.ended 分支多处用 String.valueOf(e.payload().get(...)) 或 getOrDefault 读取字段，无法防护显式 null：status/reason 缺失或为 null 时会把字面量 "null" 写入报告并进入 unresolvedProblems；currency 与 reservedApiCost/reservedApiCostUsd 同样会在 key 存在且值为 null 时输出 cost.upperBound="null"。Values.snapshot/freeze 允许 null，历史或外部日志可触发，读者会把格式错误日志误读为有效状态/费用上界。建议统一改用 Objects.toString(value, 默认值)（status 默认 UNKNOWN/INCOMPLETE、reason 默认空或 UNKNOWN、currency 默认 USD、cost 默认 "0"），并对 status 做非字符串校验、对 cost 做数值格式校验。
- 判断：String.valueOf(null)写字面量null确实误导状态/费用，采用UNKNOWN/未验证和类型校验；缺费用不能默认0伪造零成本。
- 证据：harness-cli/src/main/java/io/jharness/cli/EvidenceReport.java:47

## OCR-2430 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EvidenceReport.java:59；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：此处把完整 `tool.preview` payload 无上限地复制进报告，第 34-38 行的 `models`/`terminals` 同样整段 payload 直拷，均无条数与单字段长度上限；`DryRunPipeline` 的预览内容、终端输出可让报告 JSON/Markdown 体积和内存无界增长（违反有界数据处理要求）。建议为这三个列表设置条数上限，并对 payload 中的大字段（预览文本、任务输出等）复用 `commandSummary` 的截断逻辑。
- 判断：预览/模型/终端事实列表完整复制可能接近32MiB日志上限、报告无自己输出预算，采纳有界引用/分页；不得静默丢证据却仍称完整报告。
- 证据：harness-cli/src/main/java/io/jharness/cli/EvidenceReport.java:59

## OCR-2431 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EvidenceReport.java:36；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：终端快照没有按 run/turn 作用域过滤：`terminal.snapshot` 是 SESSION 级事件（无 turnId），这里扫描整个会话日志并让最后一条胜出。触发条件：同一会话中本次运行未写新快照（CLI 仅在 `terminal.states()` 非空时追加；`HarnessSdk.close()` 中 `container.close()` 抛异常也会跳过写入），上一次运行遗留的 RUNNING/UNKNOWN 任务会被当作本次交付证据重放，违反“UNKNOWN 状态不得被重放”。建议只接受最新 turn 开始（或本次 run 开始）之后写入的快照，并在 `turn.started` 处重置。
- 判断：terminal.snapshot选整会话最后一条，需说明这是历史最后已知状态及来源/时刻，或仅当前turn新快照；显示历史UNKNOWN不等于执行重放，原文违规则过度。
- 证据：harness-cli/src/main/java/io/jharness/cli/EvidenceReport.java:36

## OCR-2432 · 部分采纳

- 位置：harness-cli/src/main/java/io/jharness/cli/EvidenceReport.java:115；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：截断发生在最终 `sanitizer.sanitize` 之前，会导致部分敏感信息残留：`SecretRedactor` 只按完整密钥做字符串替换，若密钥正好跨越 16384 边界（`commandSummary` 的 2048 边界同理），截断后留下的密钥前缀不会再被匹配，随报告落盘并交付。此外 `substring(0, 16384)` 可能切断代理对产生无效字符。建议先脱敏再截断，或在截断处保留“已知密钥最大长度”的安全窗口（参考 `SecretRedactor.streamingText` 的做法）。
- 判断：公共build接收原始事件时先截断后sanitize可残留跨界已知secret前缀，改先脱敏再Unicode安全截断；正常JSONL已SanitizingSessionWriter清理，不能称所有现报告已泄密。
- 证据：harness-cli/src/main/java/io/jharness/cli/EvidenceReport.java:115

## OCR-2433 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceTest.java:547；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：这段 `while (true)` 只有 `token.throwIfCancelled()` 抛出异常才能退出。一旦回归导致 bounded 令牌未在 `totalTimeoutMillis` 后取消（正是本用例要守护的行为），测试线程会无限自旋空转、CPU 100%，而本类没有任何 `@Timeout`，CI 会彻底挂死而不是给出断言失败。触发条件：`BoundedModelExecution` 的 deadline 计算或 `CancellationToken` 传播被改坏。建议改为有界循环，超时后显式 `fail(...)`。
- 判断：while true只依赖被测token取消可在回归时永久自旋，独立deadline/整体Timeout令测试有界失败。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceTest.java:547

## OCR-2434 · 采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceTest.java:609；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：脱敏断言只覆盖了「反序列化后的 SessionEvent 列表」这一条通路：本方法已捕获的 CLI `output`（stdout/stderr）、会话原始 JSONL 字节以及 artifact 目录内容都未被检查。触发条件：SanitizingSessionWriter / ArtifactStore / CLI 任一层漏掉 apiKey 脱敏，或某 payload 字段在事件模型中被丢弃/未映射，该断言仍然通过，无法证明「敏感信息不外泄」。建议至少补充对 `output.toString(UTF_8)` 与会话目录原始文件（遍历 `.jharness/sessions` 下所有文件读取文本）的断言；注意只断言「不含」即可，不要打印凭据值本身。
- 判断：M8韧性已捕获CLI输出但只检查events.toString，补stdout/stderr、原始JSONL及本场景实际产物无固定秘密。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceTest.java:609

## OCR-2435 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceTest.java:470；OCR原严重度：medium
- 计划：R6-01 / P2
- 原意见：该断言只校验「抛出了某个 HarnessException」，未校验错误码。容器 `stop()` 之后任何失败原因（writer 已关闭、scope 已释放、session 已 sealed 等）都会抛出 HarnessException 并让用例通过，因此无法证明「插件回滚后保留的 ModelExecution 句柄确实被吊销」。另外方法名声称覆盖 “ReplacementService（替换服务）”，但用例既未注册替换插件、也未在 stop 后重新 start 验证替换行为，名称与实际覆盖范围不一致。建议断言具体吊销错误码（`code(() -> execution.execute(...))` 并 assertEquals 实测码），并补齐/重命名用例。
- 判断：旧ModelExecution句柄吊销负例锁定CONTEXT_CLOSED等实测码；容器one-shot不能stop后再start，原文补重启建议违反容器合同。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceTest.java:470

## OCR-2436 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceTest.java:126；OCR原严重度：low
- 计划：R0-03 / P3
- 原意见：故障注入标志在委派 `writer.append(event)` **之前**就被置位。若这次 append 自身抛异常（磁盘写失败、payload 无法序列化等），`failFlush` 会残留为 true，从而把下一次无关的 flush（例如 turn 结束或 close 时的 flush）打崩，导致 `reason()`/`wire.size()` 断言定位到错误的故障点，掩盖真实问题。建议在 append 成功返回后再置位。
- 判断：标志在delegate.append前设置可能在append另失败时污染后续flush，改成功append后标记并独立命中断言；现内置append错误本来终止，不夸大为已错误通过。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceTest.java:126

## OCR-2437 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceTest.java:58；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：HTTP fixture 完全依赖 `wire` 的全局计数决定返回 429 / read 工具调用 / done，而不是按请求内容（模型名、attempt 序号、stepId 等）关联。触发条件：出现任何额外或更少的请求（适配器 legacy 重试、policy 多试一次、同一 Server 被并行复用、加入新的探测请求）时，注入点整体错位，测试会在「错误的请求收到正确的响应」的情况下仍然通过；后续 `server.wire.get(sent)` 的位置对应断言同样失效。建议在 handler 中解析请求体特征来选择响应/状态码。
- 判断：HTTP脚本按wire次数是标准受控顺序fixture，额外发送应以等长/完整请求断言捕获；可按request区分测试场景但无需一律重写所有脚本。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceTest.java:58

## OCR-2438 · 部分采纳

- 位置：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceTest.java:61；OCR原严重度：low
- 计划：R6-01 / P3
- 原意见：M8 的 fixture 报文直接构造自 `M4CliIntegrationTest` 的静态辅助方法，形成测试类之间的隐式耦合：M4 中 tool_calls 报文结构（字段名、形状）一旦调整，M8 会在与 M8 无关的位置失败，并且两个类的可见性/执行顺序也会影响结果。建议把该 tool-call 报文提取为 testkit 内的独立 fixture 工具类（如 `TestPayloads`），避免跨用例类依赖。
- 判断：跨测试类static纯报文helper可提取稳定fixture工具减少耦合；静态纯方法不依赖JUnit执行顺序，原文执行顺序影响错误。
- 证据：harness-testkit/src/test/java/io/jharness/testkit/M8ResilienceTest.java:61

## OCR-2444 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M5CliTest.java:27；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：`new PrintStream(out)` 使用平台默认字符集编码，而下一行用 UTF-8 解码字节，二者不一致。在默认字符集非 UTF-8 的 JVM（Windows GBK/CP1252 等）上，含中文的产物内容会被替换字符破坏，导致第 74 行 `assertTrue(output.contains("历史产物"))` 假失败。应显式指定 UTF-8 构造 PrintStream。
- 判断：PrintStream默认charset与UTF8解码应显式对齐；Java21默认UTF8已降低平台差异，不声称所有Windows默认GBK。
- 证据：harness-cli/src/test/java/io/jharness/cli/M5CliTest.java:27

## OCR-2445 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M5CliTest.java:78；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第二次 export 只断言退出码 5，既没有回读 `export.jsonl` 校验既有文件在失败覆盖后未被截断/改写，也没有确认导出内容仍包含 fork 出来的事件。`JsonlSessionRepository.export` 依赖 `forcedFile`（CREATE_NEW）来保证“目标已存在即失败”，一旦实现退化为“先截断再检查”或先写后报错，本测试仍会通过；而退出码 5 同时是 TAIL_DAMAGED/SESSION_EXPORT_FAILED 的通用码，单看数字也无法证明失败原因就是“文件已存在”。建议在前后比较文件字节并断言内容完整。
- 判断：已有文件export失败只exit5不足证明不覆盖，保存原始字节并比调用后完全相等及错误码。
- 证据：harness-cli/src/test/java/io/jharness/cli/M5CliTest.java:78

## OCR-2446 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M5CliTest.java:121；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：`verify` 标记 NOT_APPLIED 后 `recover` 返回 0 只说明状态收敛，测试没有断言“日志中没有新增可执行事件”。如果恢复逻辑把 UNKNOWN 的 tool.intent 重新展开为新的 `tool.intent`/`tool.started`/`tool.requested`，这里依然会通过——这正是“UNKNOWN 不得被重放”的核心保障。建议在 recover 之后按类型统计（`tool.started`==0、`tool.intent` 仍为 1、`tool.verified`==1），或对比 recover 前后新增事件类型的白名单。
- 判断：显式核实后recover成功需断新增只有核实/恢复诊断，不出现新tool.intent/started或模型请求，直接防UNKNOWN重放。
- 证据：harness-cli/src/test/java/io/jharness/cli/M5CliTest.java:121

## OCR-2447 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M5CliTest.java:110；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`verify` 只有正向用例，缺少负向覆盖：对不存在/已收敛的 call 执行 `verify` 必须被拒绝（当前实现抛 VERIFICATION_NOT_REQUIRED）且不得写日志。没有该断言时，`verify` 若接受任意 call id 就能静默清除 UNKNOWN 而不被发现。建议补充负向用例并断言该调用后日志字节保持不变。
- 判断：补不存在/已收敛call verify拒绝VERIFICATION_NOT_REQUIRED且日志不变，防任意核实静默清UNKNOWN。
- 证据：harness-cli/src/test/java/io/jharness/cli/M5CliTest.java:110

## OCR-2448 · 部分采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M5CliTest.java:322；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：测试名为 `neverRestarted`，但只断言不存在 `tool.intent`/`tool.started`；冷终端重启在协议中由 `terminal.started` / 新的 `terminal.snapshot` 表达，因此即使实现真的重启了终端，这些断言也不会失败（形同空断言）。应额外断言：`terminal.verified` 恰好 1 条且 resolution 为 checked_without_restart、`terminal.snapshot` 仍为 1 条、不存在 `terminal.started`/`terminal.restarted`，并补充 `--terminal-id` 未知时被拒绝的负向断言。
- 判断：冷终端不重启应核terminal.verified及snapshot/PID状态而非仅tool事件，补准确现schema事件名不虚构terminal.restarted要求。
- 证据：harness-cli/src/test/java/io/jharness/cli/M5CliTest.java:322

## OCR-2449 · 采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M5CliTest.java:213；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`repair-tail` 只断言输出里出现 `backup-` 并随后能 `recover`，未验证两点关键性质：(1) 备份文件确实包含被截断的损坏尾部字节；(2) 修复后日志与损坏前的合法内容完全一致（合法前缀未被丢弃或改写）。缺少这两点，“整段清空后重建”的实现也能通过。建议保存截断前的合法字符串，修复后 `assertEquals(valid, Files.readString(log))` 并检查备份文件内容/数量。
- 判断：repair-tail须验证备份含原损坏尾与合法前缀逐字节保留，输出backup名称与recover0不足以证明无数据丢失。
- 证据：harness-cli/src/test/java/io/jharness/cli/M5CliTest.java:213

## OCR-2450 · 不采纳

- 位置：harness-cli/src/test/java/io/jharness/cli/M5CliTest.java:226；OCR原严重度：medium
- 计划：无
- 原意见：文件系统边界只覆盖了 `fork --session ../s`。`sessions export --output` 在 CliApplication 中直接把 `Path.of(args.get("output",""))` 交给 `repo.export` → `forcedFile`，没有任何工作区/路径边界校验，可以把快照写到工作区之外的任意绝对或相对路径；artifact id、`verify`/`verify-request`/`artifact` 的 `--session`、`--session-workspace` 也未覆盖穿越输入。建议补充用例断言这些输入被拒绝（64/5）且日志与目标路径均未被写入。
- 判断：sessions export --output是用户显式选择文件导出目的地，允许根外绝对路径属授权功能，不能当工作区执行工具逃逸；session/artifact标识已有各自存储边界校验。
- 证据：harness-cli/src/test/java/io/jharness/cli/M5CliTest.java:226
