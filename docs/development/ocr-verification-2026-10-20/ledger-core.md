# OCR逐条核实：core

原文及建议代码见 opinions.json；这里只以稳定编号连接原意见，部分采纳以“判断”限定范围。所有修复均未实施。

## OCR-0050 · 部分采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/DefaultPromptComposer.java:32；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：Map.of 在任一 value 为 null 时会直接抛 NullPointerException（外层与内层 Map.of 均如此）。AgentTool 是公开 SPI，接口本身未约束 description()/inputSchema() 非空；一旦某个插件（尤其是动态注册的 MCP/第三方工具）返回 null description 或 null schema，compose() 就会在每一轮抛 NPE，导致整条 agent 循环以无法定位到具体工具的错误失败。建议在组装声明时做防御性处理（description 回退为空串、schema 回退为 type=object 的空 schema），或在注册/声明阶段用明确的错误码校验并带上工具名。
- 判断：Map.of确实拒绝第三方工具的null声明；应在注册/声明边界报带工具名的契约错误，不能静默补空schema改变工具输入语义。现有内建声明不构成已发生故障。
- 证据：harness-agent/src/main/java/io/jharness/agent/DefaultPromptComposer.java:32

## OCR-0051 · 部分采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/DefaultPromptComposer.java:25；OCR原严重度：low
- 计划：R5-03 / P3
- 原意见：Map.of 生成的不可变 Map 的迭代顺序由 JVM 每次启动随机生成的 SALT 决定，因此序列化后的 tool 声明键顺序在进程重启后会变化。主流 provider 的 prompt cache 对请求前缀（含 tools 声明）做哈希，键顺序漂移会降低缓存命中率，也会让基于序列化文本的回归/黄金测试在不同 JVM 运行间抖动。建议改用有序结构（如 LinkedHashMap）保证声明顺序稳定。
- 判断：声明使用Map.of，键序不保证；接受稳定序列化以便证据比较，provider缓存受影响未有测量证据，不按缓存故障立项。
- 证据：harness-agent/src/main/java/io/jharness/agent/DefaultPromptComposer.java:25

## OCR-0052 · 不采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/DefaultPromptComposer.java:16；OCR原严重度：low
- 计划：无
- 原意见：parameters 在构造时已被 Values.snapshot 深拷贝为不可变结构，而 compose() 每轮都会把它交给 ChatRequest，其紧凑构造器又会调用 Values.snapshot 再次完整深拷贝一遍；对包含大参数的配置来说，这是每个模型步骤都重复付出的无谓开销。可考虑在构造时仅做校验、由 ChatRequest 统一快照，或让 compose 复用已冻结的实例。
- 判断：两层Values.snapshot用于构造边界不可变性；没有大配置性能测量，直接移除快照可能失去隔离，不列修复。
- 证据：harness-agent/src/main/java/io/jharness/agent/DefaultPromptComposer.java:16

## OCR-0054 · 部分采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ConfiguredTokenCostGuard.java:78；OCR原严重度：high
- 计划：R2-02 / P2
- 原意见：`actualCost` 里的强制转换没有做任何校验：`request.parameters().get("max_tokens")` 缺失时返回 null，`(Number) null` 之后调用 `intValue()` 会抛 NPE；若该值是 String 等非 Number（`Values.freeze` 允许 String 值）则抛 ClassCastException。这两条路径都会绕过本类统一的 `HarnessException("COST_ACCOUNTING_BREACH")` 失败语义，把结算阶段的受控校验变成未捕获运行时异常（M3 契约要求最终请求必须含匹配模型与 max_tokens）。此外 `actualCost` 也不像 `maximumCost` 那样校验 `model` 与 `n == 1`，宿主直接调用或传入与预留时不同的请求时，会用本 guard 的单价为不匹配的请求计费，且 max_tokens 可超过构造时的 `outputCeiling`。建议抽出统一的参数校验/解析逻辑并在两个方法中复用。
- 判断：actualCost强转未经校验确实存在；标准Loop先maximumCost再对同一prepared请求actualCost，不能推导正常路径绕过预算。补公共SPI输入校验及一致错误码，不宣称当前预算绕过。
- 证据：harness-agent/src/main/java/io/jharness/agent/ConfiguredTokenCostGuard.java:78

## OCR-0055 · 不采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ConfiguredTokenCostGuard.java:38；OCR原严重度：low
- 计划：无
- 原意见：`Set.of(...).contains(currency)` 在 `currency` 为 null 时会抛 NullPointerException（JDK 不可变集合的 contains 会对入参调用 hashCode），而不是带明确信息的 IllegalArgumentException；宿主配置漏填 currency 时只能看到 NPE，难以定位是成本守卫的配置错误。建议改为 null 安全的白名单比较。
- 判断：currency为null确实在白名单contains拒绝，属于非法宿主配置的异常样式，未证明安全或正确性影响。
- 证据：harness-agent/src/main/java/io/jharness/agent/ConfiguredTokenCostGuard.java:38

## OCR-0059 · 采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java:67；OCR原严重度：high
- 计划：R5-03 / P1
- 原意见：每次 BEFORE_MODEL 都会调用 `reader.events()`，而 JSONL 实现的 `SessionReader.events()` 会重新解析整个会话文件（`JsonlSessionRepository.openReader` → `parse`，且持有 `gate(log)` 锁），没有任何窗口或分页限制。触发条件：长会话 + 开启压缩且消息被移除时，每次模型调用前都会产生一次 O(会话总行数) 的磁盘 IO/JSON 解析。更严重的是 `sourceEvents` 把全量命中的 eventId 写进 payload 并落盘到同一份 JSONL，下一次压缩读取和写入的体积随会话长度线性增长，形成近似 O(n²) 的日志膨胀（单条事件最终可能达到 MB 级），并可能在 `SessionQuery` 的 limit=1000 语义之外继续放大。建议只采纳有界来源：例如按 `SessionQuery(..., offset, limit)` 取最近窗口，或记录上次压缩之后的增量事件，并对 payload 长度设上限。
- 判断：压缩时reader.events全读并把所有命中ID写进sourceEvents，长会话来源列表无界，需来源窗口/索引和写出上限，保留溯源与持久化前置。
- 证据：harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java:67

## OCR-0060 · 部分采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java:76；OCR原严重度：medium
- 计划：R5-03 / P2
- 原意见：`sourceEvents` 的语义与实际被移除的消息不对应：这里收集的是该会话历史上所有 `user.message`/`assistant.message`/`tool.completed`/`request.context` 事件，既包含本次仍保留在请求中的消息，也包含多轮之前（甚至此前多次压缩）的事件。触发条件：同一会话发生第二次及以后压缩，或本轮回合并未移除某些消息。结果会让恢复/审计时把“未移除消息”的来源事件当成被压缩证据，也把已被压缩掉的旧事件重复计入（并且这些旧事件的 `request.context` 里可能含上一轮完整请求快照的引用）。建议由 `removedMessageIndices` 对应的消息来源（或本次压缩的起止 eventId/序号区间）精确推导，而不是按事件类型全量过滤。
- 判断：sourceEvents收集全会话而非移除消息，关联粒度过宽；应明确来源集合语义并建立移除消息到事件的映射，不能据此认定恢复重放错误。
- 证据：harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java:76

## OCR-0061 · 不采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java:56；OCR原严重度：medium
- 计划：无
- 原意见：记录的 `strategy` 取的是构造时字段 `compactor` 的类名，但真正执行压缩的是 `context.services().requireUnique(ContextCompactor.class, context.scope())` 解析出的实例。按 M6 契约“可提供其他 compactor 实现”，一旦宿主/其他插件以更高优先级覆盖该服务的注册，实际压缩由一个实现完成、日志却标注另一个实现，恢复与审计会得到错误的策略归因。建议把 `requireUnique` 的结果保存到局部变量，并用该实例调用 `compact(...)` 及其 `getClass().getSimpleName()`；字段 `compactor` 仅用于注册。
- 判断：requireUnique不支持同作用域按更高优先级覆盖；注册第二实现会歧义，不会实际由另一实现执行却记原类名。可替换须按装配契约替换原服务。
- 证据：harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java:56

## OCR-0062 · 部分采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java:62；OCR原严重度：medium
- 计划：R5-03 / P2
- 原意见：`beforeCharacters`/`afterCharacters` 用 `messages().toString().length()` 计算，存在两个问题：(1) 与压缩器使用的度量不一致——`ExtractiveContextCompactor.size()` 还包含 `tools().toString().length()`，因此即使配置了 `compactionCharacters`，日志里的 before/after 也可能低于阈值却发生了压缩，恢复/诊断会误判；(2) 每次压缩都额外把整份消息列表序列化成临时字符串（O(总内容) 的 CPU 与内存分配），而压缩本身已经遍历过一次消息。建议与压缩器统一度量，并直接把 `request.tools()` 计入，或改为累加消息内容长度，避免重复整体序列化。
- 判断：日志字符数只算messages，压缩判定同时算tools；接受统一测量并避免全量toString临时分配，现有字段不承担恢复权威。
- 证据：harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java:62

## OCR-0063 · 部分采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java:60；OCR原严重度：medium
- 计划：R1-04 / P1
- 原意见：`summary` 由压缩器生成，默认实现 `ExtractiveContextCompactor` 会把每条被移除消息前 60 个字符（含 user/assistant/tool 结果内容）拼进摘要，这里原样写入会话事件。本插件没有注入 `DataSanitizer`，脱敏完全依赖宿主给 `writer` 套上 `SanitizingSessionWriter`；直接嵌入本插件的宿主（或忘记包装 writer 的路径）会把消息中的凭据/PII 持久化到 JSONL；此外 `EventSupport.append` 还会把未脱敏的 payload 原样 publish 到事件总线。触发条件：消息内容包含敏感值且宿主 writer 未被包装。建议构造时接收 `DataSanitizer` 并对 `summary` 做一次脱敏（或至少在契约中明确要求 writer 必须已脱敏），再写入事件。
- 判断：插件直接EventSupport写摘要，writer包装不保证事件总线payload也脱敏。核对宿主sanitize/总线订阅链后在统一事件边界解决；不把自定义无脱敏宿主假设当默认持久泄露。
- 证据：harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java:60

## OCR-0064 · 不采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java:45；OCR原严重度：medium
- 计划：无
- 原意见：压缩成功后的“溯源记录”不是模型调用的必要前提，但这里把 `reader.events()` 读取、`EventSupport.append`、`writer.flush()` 全部放在 `next.proceed(result.request())` 之前且未做任何兜底。触发条件：会话文件已存在尾部损坏（`openReader` 抛 `TAIL_DAMAGED`）、并发读取锁异常或 flush 写入失败时，异常会直接从拦截器抛出，导致已经压缩完成、本可继续的模型请求整体失败，且没有降级或诊断标记。建议将溯源采集/日志写入限制为 best-effort（捕获异常后记录降级诊断并继续 `next.proceed`），或明确区分“压缩失败”与“溯源记录失败”两种终止语义。
- 判断：M6契约明确context.compacted必须落盘flush后才请求；吞异常继续会违反durability，禁止采用best-effort建议。
- 证据：harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java:45

## OCR-0070 · 采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ExtractiveContextCompactor.java:58；OCR原严重度：low
- 计划：R5-03 / P2
- 原意见：每移除一个候选组都会重建整份 retained 列表并调用 size(compacted)，而 size() 会对全部 messages 做 toString()（含所有工具输出）再取长度，整体复杂度约为 O(组数 × 消息数 × 序列化长度)，且产生大量临时字符串；消息与工具输出较大时压缩本身会成为内存/CPU 热点。建议在扫描阶段预先计算每条消息/每个组的字符数，用累加差值判断是否达标，只在成功时构建一次最终 ChatRequest。
- 判断：每删除一组即重建ChatRequest并对完整messages/tools做toString，长工具历史有重复遍历分配；用累计大小和一次构造，保持协议组不拆分。
- 证据：harness-agent/src/main/java/io/jharness/agent/ExtractiveContextCompactor.java:58

## OCR-0071 · 采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ExtractiveContextCompactor.java:42；OCR原严重度：medium
- 计划：R5-03 / P1
- 原意见：这里只保护“字面最后一条消息所属的组”，而不是“最后一个协议组”。当请求以 user/system/assistant 文本消息结尾时（典型：新回合第一条 user 指令，其前面是上一轮的最后一次 assistant+tool 组），该协议组位于 groups.size()-2，会被整体加入 removed，最新的工具执行证据只剩 60 字符摘录。这与 docs/development/M6-contracts.md“保留系统说明、最近用户目标及最后一个协议组”的约定不一致，属于跨步骤证据丢失。触发条件：size(request) > maxCharacters 且最后一条消息不是 assistant(tool_calls)+tool 组。建议先扫描出最后一个含 tool_calls 的组下标 lastProtocol，在本循环中 `if (g == lastProtocol) continue;`。
- 判断：代码仅保护groups最后一组；尾部普通user时最近assistant/tool组仍可被删除，违反M6最后协议组保护。补尾部user和文本assistant回归。
- 证据：harness-agent/src/main/java/io/jharness/agent/ExtractiveContextCompactor.java:42

## OCR-0072 · 部分采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ExtractiveContextCompactor.java:44；OCR原严重度：medium
- 计划：R5-03 / P2
- 原意见：上一轮压缩插入的摘要消息（role=user，内容以 "Earlier context extract" 开头）既不含 latestUser 也不是 system，因此下一次压缩时会被当作普通历史组删除；而摘录只取每条消息 content 的前 60 个字符（extract 上限 512），旧摘要只剩开头 60 字符，累积的早期上下文在一次再次压缩后不可恢复。触发条件：同一会话内发生两次以上压缩（长回合压缩后继续调用模型再次超限，或跨回合超限）。建议为摘要消息打可识别标记（专用 role/键）并永久跳过，或把旧摘要整体并入新摘要而不是按 60 字符摘录。
- 判断：再次压缩会截取旧摘要前60字符，确有递归信息损失；提取式摘要本来允许有损，不宜永久保护无界旧摘要，需明确有界递归压缩策略。
- 证据：harness-agent/src/main/java/io/jharness/agent/ExtractiveContextCompactor.java:44

## OCR-0073 · 部分采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ExtractiveContextCompactor.java:60；OCR原严重度：medium
- 计划：R5-03 / P2
- 原意见：被移除的 assistant/tool 原文摘录被包装成 role="user" 的消息，并插在真正的用户指令之前：一是把不可信的工具输出/模型输出提升到“用户指令”权限层级，其中 60 字符足以放入 "ignore previous instructions" 之类的注入文本；二是该摘要还会被 ContextCompactionPlugin 原样写入 context.compacted 事件并持久化。建议改用 role="system"（或 assistant）的显式“上下文摘录”段落，保持真实 user 消息在前，并保留清晰的边界标记。
- 判断：工具摘录作为user消息有来源层级混淆；原文建议system反而提高权限不可采纳。保留角色/不可信引用边界并测试，不宣称能消除模型注入。
- 证据：harness-agent/src/main/java/io/jharness/agent/ExtractiveContextCompactor.java:60

## OCR-0074 · 采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ExtractiveContextCompactor.java:49；OCR原严重度：medium
- 计划：R5-03 / P2
- 原意见：摘录上限被硬编码为 512 字符，与 maxCharacters 无关，导致预算较小时永远无法收敛：例如 compactionCharacters=1024（CLI 允许的最小值），若受保护内容（system + 最新 user + 最后的组）约 600 字符，加上最长约 512+ 的摘录必然 > 1024，最终抛 CONTEXT_LIMIT，而实际上只要缩短摘录就能满足预算。建议把上限改为随预算推导，如 min(512, max(0, maxCharacters - 受保护内容长度))，并在无法容纳时明确区分“受保护内容超量”与“摘录超量”。
- 判断：固定512摘录可占满1024预算使原本可容纳的受保护消息失败；需按受保护内容和工具声明剩余空间裁剪，保留真正超限拒绝。
- 证据：harness-agent/src/main/java/io/jharness/agent/ExtractiveContextCompactor.java:49

## OCR-0075 · 部分采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ModelResiliencePlugin.java:34；OCR原严重度：high
- 计划：R1-02 / P1
- 原意见：审计回调没有做失败隔离（第34-48行）。该 lambda 在 `BoundedModelExecution.execute` 的模型调用线程上同步执行，而 `context.services()` 在插件上下文关闭后会抛 `CONTEXT_CLOSED`（PluginContainer.Context.ensureOpen），`requireUnique` 在作用域内 SessionWriter 缺失/多注册时抛 `MISSING_SERVICE`/`AMBIGUOUS_SERVICE`，`writer.append` 还可能抛 I/O 异常。触发条件：`container.stop()`/startup rollback 与在途模型调用并发（cleanup 先 `plugin.stop()` 再 `context.close()`，已在等待/重试中的调用仍在跑），或 SessionWriter 解析失败。后果不只是丢事件：`BoundedModelExecution` 第81-83行在发送前、第93-101行在收到 429 后调用 `audit.accept`，抛出会把一次正常的 429 重试变成 `CONTEXT_CLOSED` 失败；第119行 `audit.accept("model.execution.stopped", Map.of("reason", ex.code()))` 位于 catch 块内，审计抛出会直接覆盖原始异常码（如 `MODEL_RATE_LIMIT`/`CANCELLED`），破坏重试与 UNKNOWN 分类。建议把审计事件做成 best-effort：在 lambda 内捕获 `RuntimeException` 后忽略。
- 判断：audit失败可覆盖catch中的原异常，确需保留首因和已发送/未发送事实；不能把发送前或重试必需审计一律吞掉继续请求，M8要求先落盘。
- 证据：harness-agent/src/main/java/io/jharness/agent/ModelResiliencePlugin.java:34

## OCR-0076 · 不采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ModelResiliencePlugin.java:58；OCR原严重度：medium
- 计划：无
- 原意见：解析方式与注册 key 不一致（第51行注册 `"default"`，这里却用只按类型匹配的 `requireUnique`）。`requireUnique` 会在同作用域存在第二个 `ModelExecution` 注册时抛 `AMBIGUOUS_SERVICE`（DefaultServiceRegistry:59-62），而用其它 key 注册“替换实现”（文档 M8 声明宿主可提供替换实现）不会触发 `DUPLICATE_SERVICE`，因此会静默演变成每次模型调用都失败的运行期错误，而不是在装配期暴露。建议改为 `require(ModelExecution.class, "default", context.scope())`，与注册 key 对齐；若确实要求唯一，应在 `start()` 时（装配期）校验并快速失败。
- 判断：requireUnique是作用域唯一服务契约，key不同并不意味着替换默认实现；改require(default)会静默忽略歧义。若需装配期诊断另作为优化，非运行语义缺陷。
- 证据：harness-agent/src/main/java/io/jharness/agent/ModelResiliencePlugin.java:58

## OCR-0077 · 不采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/ModelResiliencePlugin.java:30；OCR原严重度：medium
- 计划：无
- 原意见：ModelResiliencePlugin lifecycle is not idempotent: start() can overwrite/re-register execution and repeatedly own services, while stop() is empty and leaves closed instances referenced. Double start or stop/start can leave a closed BoundedModelExecution registered and every model call fails MODEL_RESILIENCE_CLOSED. Add a started guard, reset/close state in stop, and make start/stop idempotent.
- 判断：PluginContainer仅NEW时start并负责own撤销和close，不支持把关闭实例再启动；建议要求所有插件可重启缺乏契约依据。
- 证据：harness-agent/src/main/java/io/jharness/agent/ModelResiliencePlugin.java:30

## OCR-0092 · 部分采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/BoundedModelExecution.java:44；OCR原严重度：medium
- 计划：R1-02 / P2
- 原意见：该 audit 回调在持有 `synchronized (this)` 监视器时被调用，而本文件其他所有 audit 调用点都在锁外，位置不一致。`ModelResiliencePlugin` 注入的回调会做作用域服务查找与 session 事件追加（I/O），在锁内执行会阻塞所有排队者、处于 interval/退避等待中的活跃线程以及 `close()`，并形成"本对象监视器 → 会话写锁"的加锁顺序，存在长时间停顿甚至锁顺序反转的风险。建议锁内只做计数与判定（`waiters++`），把 `audit.accept` 移到 `synchronized` 块之外再进入等待循环。
- 判断：等待审计在synchronized内做服务查找/I/O会延长监视器持有，接受缩小锁区间并保持等待计数原子；死锁尚无反向锁链证据。
- 证据：harness-agent/src/main/java/io/jharness/agent/BoundedModelExecution.java:44

## OCR-0093 · 不采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/BoundedModelExecution.java:112；OCR原严重度：low
- 计划：无
- 原意见：`ex.code()` 未做空值保护：`HarnessException` 构造函数不校验 code，若适配器抛出 code 为 null 的 HarnessException，这里会抛二次 NPE，掩盖原始失败；同时第 119 行 `Map.of("reason", ex.code())` 也不接受 null 值，会跳过 `model.execution.stopped` 停止审计，破坏事件序列。建议使用常量侧的 `equals`，并对 reason 做空值兜底（如 `ex.code() == null ? "UNKNOWN" : ex.code()`）。
- 判断：仅违规自定义HarnessException(null)触发，现有模型适配器均给出码；与基础异常code非空契约统一，不单列重试缺陷。
- 证据：harness-agent/src/main/java/io/jharness/agent/BoundedModelExecution.java:112

## OCR-0121 · 采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/DefaultAgentLoop.java:337；OCR原严重度：low
- 计划：R5-02 / P2
- 原意见：失败路径性能问题：每个未决调用都在循环内重新执行 `runtime.reader().events()` 两次（338-343、345-350 行），即读两遍全量日志；未决调用 m 个、日志 n 条时为 O(n·m) 的全量扫描，在长会话 + 并行 batch 大批量调用超时/取消时（每轮还会再读一次 38-39 行）开销明显。建议在循环外取一次事件快照，并一次性按 callId 建立 type 索引。
- 判断：异常结算每个未决call两次reader.events读取，确为重复全日志扫描；一次快照建立call索引即可，需保留UNKNOWN结算语义。
- 证据：harness-agent/src/main/java/io/jharness/agent/DefaultAgentLoop.java:337

## OCR-0122 · 采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/DefaultAgentLoop.java:324；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：错误码分类顺序有缺陷：只要整轮耗时超过 `maxDuration`（104 行计算，含 `maxDuration` 为 0/负值的情况），任何异常都会被改写成 `LIMIT_REACHED/TIME_LIMIT`，即使真实原因是 `UNKNOWN_SIDE_EFFECT`、`COST_ACCOUNTING_BREACH`、`INVALID_SCHEDULER_RESULT`、`DURABILITY_REQUIRED` 等。后果：`turn.ended` 的 status/reason 与真实失败不一致（CLI 退出码与诊断结论错误），并且 375 行 step.ended 的 `error` 也用被覆盖后的 `reason`，原始 `code` 在 journal 中完全丢失。建议只在“确实是超时/取消”时才降级为 TIME_LIMIT，其余情况保留原始 code；可另加字段记录 `timedOut`。
- 判断：catch先按耗时改TIME_LIMIT会覆盖真实UNKNOWN/持久化失败码；需保留原始error及超时事实，测试超过期限后未知副作用仍不可重放。
- 证据：harness-agent/src/main/java/io/jharness/agent/DefaultAgentLoop.java:324

## OCR-0123 · 部分采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/DefaultAgentLoop.java:409；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：`checkpoint.failed` 只通过 `core.events().publish(...)` 广播到 EventBus，没有走 `core.session()`/`EventSupport.append` 写入 journal，因此该证据不会持久化，`diagnose`/恢复流程（读 journal）看不到这条 checkpoint 失败记录，与文件内其他事件一律 append 的做法不一致。另外 publish 若抛异常，会取代 `throw ex`，把原始的 checkpoint / durability 异常吞掉，导致外层 catch 的 `code` 变成无关的 RUNTIME_FAILED。建议改为 append SESSION 级事件后再抛出，并确保 publish 失败不影响原始异常。
- 判断：checkpoint.failed只publish且publish异常会覆盖首因；写盘失败时不能强制依赖同一损坏writer成功追加。保留原异常，持久诊断仅在可安全写入时补充。
- 证据：harness-agent/src/main/java/io/jharness/agent/DefaultAgentLoop.java:409

## OCR-0124 · 不采纳

- 位置：harness-agent/src/main/java/io/jharness/agent/DefaultAgentLoop.java:123；OCR原严重度：medium
- 计划：无
- 原意见：恢复残留问题：`record()`（123-131 行）之后、真正发送（150-160 行）之前还夹着 `checkpoint`、取消检查、`reconstruct`、`REQUEST_REPLAY_MISMATCH` 这些**本地**失败点。这些路径抛出时 journal 里已有一条 model.requested 但没有 assistant.message/model.verified，`SessionRecovery.pendingRequestEvents` 只会被 assistant.message、`model.rejected/HTTP_429`、`model.verified` 清除，因此 `recoveryReady()`（CliApplication 1148-1156 行要求 pendingRequests 为空）会一直为 false：一次纯本地失败（例如 checkpoint 写盘失败）会把会话永久卡住，必须人工 verify 一个从未发出的请求。对比 89-107 行的货币/费用校验都刻意放在 `record` 之前，可见这里是遗漏。建议在发送前的本地失败路径补写可被 SessionRecovery 识别为“已终结”的事件（如复用 model.rejected 并让 recovery 对本地失败也清除 pending），或把不变的 checkpoint 前移到 record 之前。
- 判断：model.requested后checkpoint/重建失败保留未决是M5/M8保守恢复契约；不能在持久化失败后伪造已拒绝或将checkpoint前移绕过请求事实落盘。可改善人工诊断，不自动清除。
- 证据：harness-agent/src/main/java/io/jharness/agent/DefaultAgentLoop.java:123

## OCR-0406 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/CommandTransport.java:15；OCR原严重度：high
- 计划：无
- 原意见：紧凑构造器直接对 stdout/stderr 调用 clone()，没有 null 校验。任一实现（尤其是测试替身或未来的 transport）返回 null 时，会在构造阶段抛出无消息的 NullPointerException，把契约违约伪装成 NPE，且栈顶指向被调用方而非违约方。建议显式把 null 视为契约违约（带消息）或归一化为空数组。
- 判断：Reply以clone拒绝null是构造边界非空防御；只是异常消息不足，不能据此列high缺陷或把非法null静默转换成功输出。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/CommandTransport.java:15

## OCR-0407 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/CommandTransport.java:14；OCR原严重度：high
- 计划：无
- 原意见：Reply 没有强制状态不变量，允许自相矛盾的组合：uncertain=true 却携带确定的 exitCode、started=false 却 uncertain=true。DockerExecutionEnvironment 只依赖 `exitCode() != 0 || uncertain()`（第 79/203/288 行）做判定，只要某个实现把 UNKNOWN 结果和确定的 exitCode 一起返回（例如 exitCode=0 或某个真实退出码），下游就会把未决执行当成已确认结果并重放，违反「UNKNOWN 状态不得被重放」。建议在紧凑构造器里强制不变量：uncertain 时 exitCode 必须为 -1；!started 时 uncertain 必须为 false（现有 NativeCommandTransport 的返回值均满足此约束）。
- 判断：DockerExecutionEnvironment对uncertain单独或OR拒绝，不会因exitCode=0忽略UNKNOWN；意见与源码判定相反。exitCode与uncertain可并存观察事实，不能强制抹去。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/CommandTransport.java:14

## OCR-0408 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/CommandTransport.java:13；OCR原严重度：medium
- 计划：无
- 原意见：record 自动生成的 equals/hashCode 对 byte[] 组件使用引用比较（Objects.equals 对数组退化为 ==），因此 stdout/stderr 内容完全相同的两个 Reply 并不相等；toString 也只会输出 `[B@hash`，排障时看不到内容。若上层用 Reply 做断言、去重或缓存键，会出现静默误判。建议显式覆写 equals/hashCode 使用 Arrays.equals/Arrays.hashCode，并明确输出内容是否纳入身份比较。
- 判断：Reply没有按数组内容作为身份的业务调用点，byte[]引用equals非现有去重缺陷；toString直接输出可能泄密，不接受为排障打印全文。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/CommandTransport.java:13

## OCR-0409 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/CommandTransport.java:20；OCR原严重度：medium
- 计划：R5-01 / P2
- 原意见：访问器每次调用都全量 clone 数组，而 NativeCommandTransport 的 stdout 上限可达 BoundedJson.LIMIT 字节；同一请求内多次读取（日志记录 + JSON 解析 + 错误上报）会产生多次全量拷贝和额外 GC 压力。更重要的是 Reply 自身对输出大小没有任何约束，完全依赖具体 transport 自觉，任一实现（测试替身、未来新增 transport）返回超大数组会直接占用堆内存。建议在紧凑构造器对长度设上限/截断，并在 javadoc 中明确访问器的复制语义（或返回不可变视图）。
- 判断：clone维护不可变边界不应删除；接受公共transport输出上限与实现契约，未证明现有有界NativeCommandTransport泄漏或GC故障。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/CommandTransport.java:20

## OCR-0410 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/CommandTransport.java:7；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：接口契约没有定义超时/取消的返回语义。NativeCommandTransport 在进程已启动后超时会返回 (-1, 空, 空, started=true, uncertain=true)，但这只是实现细节：接口既未要求「已启动后超时/取消必须上报 started=true 且 uncertain=true」，也未说明超时应该是抛异常还是返回值，更未约定 CancellationToken 为 null 时的行为。实现者（测试替身、插件侧 transport）很可能返回 started=false 的取消结果，导致已经产生的副作用被判定为「未执行」而被重放。建议把该不变量写入方法级 javadoc，作为接口契约的一部分。当前 javadoc「fake results are contract evidence only」也没有表达任何可校验的契约。
- 判断：CommandTransport只有一句注释，缺started/uncertain与超时取消关系；应明确实施契约并加替身违约回归，保持未知不重放。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/CommandTransport.java:7

## OCR-0417 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ContentPages.java:13；OCR原严重度：medium
- 计划：无
- 原意见：`clean`/`version` are dereferenced without null checks (`clean.length()` here, `version.isEmpty()` on line 20). A null value (e.g. a caller/HTTP query layer that maps a valueless `version` parameter to null, or a sanitizer that returns null text) escapes the structured `CONTENT_LIMIT`/`INVALID_RANGE` error protocol and surfaces as a raw NPE, which the web/MCP layer maps to an unhandled 500 instead of a coded 400. Add explicit coded checks before use.
- 判断：ContentPages内部三条调用链传入完整脱敏字符串和空串/规范version；null属于未支持SPI输入，意见假设的HTTP valueless映射未证实。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ContentPages.java:13

## OCR-0418 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ContentPages.java:19；OCR原严重度：medium
- 计划：R5-01 / P2
- 原意见：Every page request re-encodes the whole sanitized content to UTF-8 and re-digests it with SHA-256, even when the caller passes no `version` (the common paging case, e.g. `page(clean, offset, "")`). With the 8 MiB char bound this is up to ~32 MiB of transient allocation and an O(n) hash per request; a client paging through the maximum content makes total work O(n²), and repeated requests with client-controlled `offset` become a cheap CPU/allocation amplification vector. Compute the digest once upstream at sanitization time (or cache it keyed by content identity/version) and pass it in, so paging only slices.
- 判断：每页sha(clean)确实全量编码散列；接受单次有界内容快照复用版本，不能仅按请求客户端offset无限缓存或放弃版本一致性验证。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ContentPages.java:19

## OCR-0419 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ContentPages.java:34；OCR原严重度：medium
- 计划：R5-01 / P2
- 原意见：`clean.substring(0, offset)` copies the whole prefix (up to 8 MiB of chars / ~16 MiB) on every page call just to count newlines — the same per-request allocation amplification as the rehash, and O(n²) across a full paging walk. Count the newlines over the prefix without materialising a copy (the loop below keeps O(offset) time but zero extra allocation), or have callers supply the line number.
- 判断：startLine用substring(0,offset).chars导致复制前缀，可无分配遍历或有界行索引，保持偏移语义。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ContentPages.java:34

## OCR-0420 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ContentPages.java:23；OCR原严重度：low
- 计划：无
- 原意见：`end > offset` is unreachable-false in this guard: it is only evaluated when `end < clean.length()`, which forces `end == offset + 16384 > offset`, and `offset >= 0`. The condition is dead and misleadingly suggests `end` could collapse onto `offset` (an empty page with `more=true`, i.e. no forward progress). Remove it (and if that invariant is meant to be protected, assert it explicitly).
- 判断：end>offset虽在当前16384规则下冗余，属于可读性防御条件，无正确性影响。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ContentPages.java:23

## OCR-0421 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ContentPages.java:31；OCR原严重度：low
- 计划：R5-01 / P2
- 原意见：`totalCharacters` is `String.length()`, i.e. UTF-16 code units, while `offset`/`nextOffset` use the same units — but the field name advertises characters. A client computing offsets in code points (e.g. Python length, or any non-UTF-16 binding of this JSON page protocol) will land inside a surrogate pair on the high-surrogate side, which is *not* rejected by the check on line 17 and yields truncated/duplicated text while `nextOffset` silently advances by code units. Either rename to `totalCodeUnits`/document the unit explicitly, or validate/normalise the offset against code points.
- 判断：偏移统一UTF16但JSON字段未直接说明单位，接受跨Python/JS接口文档及非BMP回归；源码拒绝落在low surrogate，意见声称高代理起始截断不成立。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ContentPages.java:31

## OCR-0422 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ContentPages.java:33；OCR原严重度：low
- 计划：无
- 原意见：`sanitized` is hard-coded to `true` and `page()` performs no sanitization — the whole class contract rests on a by-convention `clean` argument name. Today's three call sites (`WorkspaceContentInspection.file`/`artifact`, `HarnessWebServer` `content`) all sanitize before calling, but this is a public method of a shared ecosystem module, so a future caller forwarding raw text would emit falsely-attested pages (including secrets that straddle a page boundary, which the class comment claims is covered). Enforce the precondition instead of asserting the outcome: take an explicit `sanitized`/already-clean token, or assert on a marker produced by the sanitizer.
- 判断：方法注释明确完整脱敏先于切片且三处调用均sanitize；对未来误用的推测不足以增加标志位接口，标志本身也不能证明脱敏。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ContentPages.java:33

## OCR-0423 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/BoundedJson.java:29；OCR原严重度：high
- 计划：R5-01 / P1
- 原意见：第 29-30 行：写入侧没有任何约束，且大小检查发生在完全物化之后。`writeValueAsBytes` 会先把整个值序列化成一个字节数组（内部还会经 UTF-8 编码缓冲），随后才比较 `LIMIT`，因此一个 100MB 的工具输出或一个深层/自引用的 Map 会先在 `encode()` 中耗尽堆内存或抛 `StackOverflowError`（`Error` 不会进入第 32 行的 `catch (IOException)`），宿主/Worker 进程直接崩溃，而不是得到 `PAYLOAD_LIMIT`。触发条件：`HarnessSdk`/`EnvironmentWorker`/`HttpMcpTransport` 把未预先限长的工具结果或对端数据传入 `encode`。建议为工厂配置 `StreamWriteConstraints.builder().maxNestingDepth(64)...`，并通过带上限的 `OutputStream` 用 `createGenerator` 流式写出，在超限时立即中止而非事后判断。
- 判断：encode先writeValueAsBytes后检查4MB，写出路径缺少有界流与对称约束；应限制序列化过程占用，深度循环保护沿用Values边界，不断言所有巨大图均可达。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/BoundedJson.java:29

## OCR-0424 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/BoundedJson.java:41；OCR原严重度：high
- 计划：R5-01 / P1
- 原意见：第 40-42 行：读写两侧的限额不对称。`decode` 会拒绝超过 10 万个 token 或嵌套深度大于 64 的文档，而 `encode` 只校验 4MB 字节长度（MAPPER 只配置了读约束，没有 `StreamWriteConstraints`），`text`/`encode(null)` 也走同一路径。结果是本进程自己产出的合法载荷会在对端解析时失败：例如 `EnvironmentWorker` 第 38 行用 `encode` 写出一个含约 12 万个小整数（远小于 4MB）或 65 层嵌套的响应，`DockerExecutionEnvironment` 第 220 行再 `decode` 时就会抛 `PAYLOAD_NODE_LIMIT`/`INVALID_JSON`；MCP 传输同理。另外 `encode(null)` 会成功产出 `null`，而 `decode(null 字节)` 经 `Values.object` 必然报 `INVALID_JSON`，无法往返。建议在写入侧施加与读取侧一致的深度/节点数上限（或在入口统一拒绝 null），保证 encode 的输出一定能被 decode 接受。
- 判断：encode只限字节而decode还限100000token/64层，自产小字节多节点报文可能对端拒绝；统一对象协议限额。encode是通用Object方法而decode约定对象，null不可往返不必强求。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/BoundedJson.java:41

## OCR-0425 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/BoundedJson.java:46；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：第 44-48 行：所有 `IOException` 都被折叠成 `INVALID_JSON`，没有区分限额违规与语法错误。第 21-23 行配置的 `maxNestingDepth(64)`、`maxStringLength(LIMIT)` 以及 Jackson 默认的数字/名称长度约束在超限时抛出的 `StreamConstraintsException`/`StreamReadException` 都会走到这里，客户端得到的是 `INVALID_JSON`，而字节超限得到的是 `PAYLOAD_LIMIT`、token 超限得到的是 `PAYLOAD_NODE_LIMIT`——同类“过大”错误出现三种互不一致的错误码，调用方无法据此判断是可重试的载荷过大还是数据损坏；同时异常 cause 被丢弃，也无法定位具体字段。建议按 `StreamConstraintsException`（或消息中的 depth/string/token 超限特征）映射到 `PAYLOAD_LIMIT`/`PAYLOAD_NODE_LIMIT`，并用 `new HarnessException(code, code, ex)` 保留原因。（同样的吞异常写法在 `encode` 第 32-33 行也存在，会把写出侧深度超限报成 `INVALID_JSON`。）
- 判断：StreamConstraintsException合并INVALID_JSON确实降低诊断粒度；可区分限额并保留安全cause，不能把解析错误建议为自动可重试。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/BoundedJson.java:46

## OCR-0426 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/BoundedJson.java:40；OCR原严重度：low
- 计划：无
- 原意见：第 39 行：每个报文都被解析两次——先用预扫描统计 token，再用 `readValue` 重新完整反序列化。对最大 4MB 的载荷这意味着近乎翻倍的 CPU 与临时对象分配，而 Worker/MCP 的每个请求和响应都要经过该路径。可在同一个 `JsonParser` 上边计数边绑定（如用计数包装的 `JsonParser` 传入 `MAPPER.readValue(parser, typeRef)`），在单次解析内完成节点数校验。
- 判断：预扫描是绑定前节点上限门禁；在没有等效计数parser实现与性能证据前不能简单合并，4MB已有上限，不独立立项。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/BoundedJson.java:40

## OCR-0432 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentPlugin.java:32；OCR原严重度：medium
- 计划：无
- 原意见：嵌套插件 WorkspaceToolsPlugin/TerminalToolsPlugin 是直接 new 后 start 的，其注册项虽然都通过本插件的 PluginContext 登记（context.close 时能回滚），但两个子插件自身的生命周期钩子从未被调用：本类 stop() 为空，也没有持有它们的引用。当前两个子插件的 stop() 恰好是空实现所以没有立即泄漏，但一旦它们将来在 stop() 里加入清理（例如终端/工作区状态落盘、子进程回收），这些清理会被静默跳过，且外层无法补偿。另外直接 new 也绕过了 PluginContainer 对 id/apiVersion/依赖的校验。建议保存子插件实例并在 stop() 中反向调用其 stop()，或让子插件实现 AutoCloseable 后交给 context.own(...) 托管。
- 判断：两子插件stop当前为空，注册资源由同一context.own正确回滚；假设未来stop有资源不构成当前泄漏。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentPlugin.java:32

## OCR-0433 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentPlugin.java:38；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：environment.processes() 被求值了两次（第 32 行注册 ProcessRunner 服务，这里再传给 CommandTool；第 31 行的 WorkspaceToolsPlugin 构造器内部还会再调用一次）。ExecutionEnvironment 的实现并不保证该方法返回同一实例——DockerExecutionEnvironment.processes() 每次调用都会 new 一个匿名 ProcessRunner。当前 Docker 实现是无状态的委托所以暂时等价，但一旦某个 provider 的 runner 持有连接/会话状态或延迟初始化，注册到容器里的 ProcessRunner 与 execute_command 实际使用的 runner 就会是两个不同对象，出现“服务解析拿到的进程管理器与工具执行的不是同一个”的不一致。建议在 start 开头用局部变量捕获 var processes = environment.processes();，注册与 CommandTool 都复用该实例。
- 判断：多次environment.processes调用可给可替换环境不同runner；捕获单实例利于一致装配，当前Docker无状态委托不构成实际不同管理器故障。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentPlugin.java:38

## OCR-0434 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentPlugin.java:40；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：终端能力判定依赖 descriptor() 的“缺省即支持”隐式约定，且未做空值/异常防护，存在三个可触发问题：1) ExecutionEnvironment.descriptor() 的默认实现只返回 id/isolation（不含 terminal 键），任何未覆写 descriptor() 的自定义环境下这里都会判为“支持”，从而注册 terminal_* 工具，而它的 TerminalSession 全部走接口默认实现抛 UNSUPPORTED_TERMINAL，导致工具清单里出现必然失败的工具；2) 若环境以“terminal() 抛 UNSUPPORTED_TERMINAL”的方式表达不支持（WorkspaceToolsPlugin 内部的匿名环境正是这么做的），即使 descriptor 已声明 unsupported，第 40 行仍会调用 terminal() 并把整个插件/容器启动打挂，与该分支“容忍不支持终端”的意图相反；3) descriptor() 返回 null 时直接 NPE。建议显式建模能力（descriptor 增加 terminal=supported，或先判断是否为 unsupported 再决定是否调用 terminal()），并对 terminal() 的调用做空值与 UNSUPPORTED_TERMINAL 的容错；不支持时不要注册一个可被解析的 no-op TerminalSession，以免调用方误以为终端可用。
- 判断：terminal=unsupported分支仍调用terminal，公开provider若用抛异常表达不支持可启动失败；需统一descriptor与无终端契约。不接受忽略所有异常或默认注册虚假可用终端。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentPlugin.java:40

## OCR-0435 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentPlugin.java:30；OCR原严重度：low
- 计划：无
- 原意见：start 没有幂等/重入保护：每次调用都会再次 context.own(environment)（关闭时对同一环境 close 两次）、再次以 "default" 注册 ProcessRunner/TerminalSession 并重复注册 execute_command/terminal_* 工具，第二次注册会抛 DUPLICATE_SERVICE 并导致容器 FAILED。PluginContainer 只在 state==NEW 时调用 start，因此通过容器使用不会触发；但插件实例是可复用的公开类型（同一实例被加入两个容器、或宿主直接调用 start 都是可能的），失败后遗留的已注册项只能靠容器清理。建议用 AtomicBoolean/状态位在 start 入口做一次性保护，重复调用时抛出明确错误而不是走到中途因重复注册失败。
- 判断：意见自己确认容器仅NEW调用start；跨容器复用有资源插件实例不在保证内，不能以此要求插件重复start幂等。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentPlugin.java:30

## OCR-0436 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/LocalExecutionEnvironment.java:28；OCR原严重度：low
- 计划：无
- 原意见：构造参数 root / secrets 未做非空校验（同一个构造函数里 searchFilter 用了 Objects.requireNonNull）。触发条件：EcosystemConfig.environment(...) 传入的 root 或 secrets 为 null 时，root 会在 LocalWorkspace 构造函数的 root.toRealPath() 处抛 NPE，secrets 会在 LocalProcessRunner -> SecretRedactor 构造函数里抛 NPE；此时 files 已创建，异常信息也不带参数名，调用方难以定位是哪个入参非法。建议在委托构造前显式校验（secrets 用 List.copyOf 顺带做快照）。
- 判断：root/secrets非法null在下游构造边界拒绝，没有资源句柄被打开的证据；补参数名属于低价值风格调整，不入修复计划。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/LocalExecutionEnvironment.java:28

## OCR-0437 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/LocalExecutionEnvironment.java:78；OCR原严重度：low
- 计划：R1-02 / P2
- 原意见：close() 只用 try/finally 串联两个关闭动作：若 terminal.close() 抛出异常、随后 processes.close() 也抛出异常，finally 中的异常会覆盖并吞掉前一个异常，调用方无法知道终端清理失败的原始原因（terminal.close() 内部会调用可替换的 closeObserver，存在抛出可能）。另外 close() 自身没有幂等/并发保护，完全依赖被委托对象的内部状态。建议捕获首个异常并用 addSuppressed 保留后续失败。
- 判断：LocalExecutionEnvironment.close的finally会覆盖terminal首个异常；合并suppressed并记录各资源清理结果，不能因末个异常丢失首因。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/LocalExecutionEnvironment.java:78

## OCR-0438 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/LocalExecutionEnvironment.java:77；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：环境关闭后没有失效状态：close() 之后 processes 会返回 PROCESS_SERVICE_CLOSED、terminal 会抛 TERMINAL_CLOSED，但 files() 返回的 LocalWorkspace 本身没有 closed 状态（不实现 AutoCloseable），仍可正常 readText/write/edit/applyPatch。触发条件：插件作用域销毁（PluginScope 关闭 environment）后仍有工具/子任务持有 files() 引用并发起写操作，导致“已关闭”的环境继续修改工作区，与 DockerExecutionEnvironment 的 ensureOpen()、ReadOnlyChildEnvironment 的 check() 行为不一致。建议增加 closed 标志并在 files() 返回前校验，或让 LocalWorkspace 支持关闭/失效。
- 判断：LocalWorkspace独立对象关闭环境后仍可写，保留引用的公开files边界未撤销；需定义关闭后文件能力与在途操作边界，单在files()加检查无法撤销已取得引用。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/LocalExecutionEnvironment.java:77

## OCR-0439 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/LocalExecutionEnvironment.java:33；OCR原严重度：low
- 计划：无
- 原意见：LocalProcessRunner.buildEnvironment() 在这里被调用两次，分别给进程运行器和终端会话生成两个相互独立的环境变量白名单快照。当前 System.getenv 在同一 JVM 内稳定，两次结果相同，但白名单/默认值逻辑一旦调整（或增加注入参数），进程与终端就会看到不同的环境，且主机环境被重复读取。建议只取一次快照后同时传给两者，保证两条执行通道的环境完全一致。
- 判断：System.getenv在JVM内稳定，两次白名单快照一致；意见未给出实际不同环境触发，属于消除重复调用偏好。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/LocalExecutionEnvironment.java:33

## OCR-0440 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentWorker.java:44；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`op` 被裸强转为 String 后直接用于 switch：请求缺少 "op" 或值不是字符串时会抛 NPE/ClassCastException；`path` 同样裸强转（为 null 时 ExecPath 内部的 Values.required 抛 IllegalArgumentException）。同理，grep 的 paths/literal、write 的 content、patch 的 patch、search 的 glob 也都没有存在性与类型校验，而 number() 对非 Number 值抛 ClassCastException、对 Double 会静默截断。这些问题在 main 的 catch 里全部退化成 WORKER_IO，调用方无法区分“请求非法”和真正的 IO 故障。建议先校验 op 属于已知操作集合、必需字段存在且类型正确，失败抛 HarnessException("INVALID_REQUEST", ...)。
- 判断：dispatch对op及字段裸强转、number.intValue截断，非法请求变WORKER_IO；需严格类型/整数/范围校验与固定协议错误。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentWorker.java:44

## OCR-0441 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentWorker.java:50；OCR原严重度：medium
- 计划：R2-01 / P1
- 原意见：返回的 version 不是与 text 来自同一次读取：versionOf 在读前、读后各做一次全文件哈希。若文件在两次哈希之间被改写后又改回原内容（或 readText 期间被部分改写），返回的 text 与 version 就会自相矛盾；而 RemoteWorkspace.readText 会把该 version 记入 observed，作为后续 write 的默认 expectedVersion，从而漏检并发修改、可能覆盖他人写入（丢失更新）。建议从同一次读取的字节直接算出版本（或提供一次读取同时返回 text+version 的原子接口），避免二次快照与额外 I/O。
- 判断：两次version夹read不能证明读出字节与hash原子一致，ABA可返回不匹配文本；接受同一次读取派生版本，不宣称能解决不合作外部写者的所有丢更新问题。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentWorker.java:50

## OCR-0442 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentWorker.java:31；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：除 HarnessException 外的所有异常（NPE、ClassCastException、CommandRequest 构造校验抛出的 IllegalArgumentException 等）都被压成 WORKER_IO，且 HarnessException 自带的 message/cause 也被丢弃，stderr 没有任何日志。宿主侧只能拿到通用错误码，无法定位是请求非法还是环境故障。建议分开处理：catch HarnessException 原样回传 code（可带简短 message），catch IllegalArgumentException 映射为 INVALID_REQUEST，其余异常至少先写一条 stderr 日志再返回 WORKER_IO。
- 判断：main把非法参数合并WORKER_IO不利诊断；应分类有界错误，不能原样输出未脱敏message/cause或stderr全文。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentWorker.java:31

## OCR-0443 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentWorker.java:38；OCR原严重度：medium
- 计划：R5-01 / P1
- 原意见：序列化与写回在 try 之外：BoundedJson.encode 在响应超过 4 MiB 时会抛 HarnessException("PAYLOAD_LIMIT")（例如 search/list 大结果或 diff），System.out.write 在宿主侧 docker exec 超时/断开（EPIPE）时会抛 IOException。两种情况下 main 都直接抛出、进程非 0 退出，不会输出任何结构化响应，宿主只能得到 ENVIRONMENT_IO/无法区分错误类型。建议把编码与写出也纳入受控逻辑，失败时仍输出 {"error": "PAYLOAD_LIMIT"|"WORKER_IO"} 形式的有界响应。
- 判断：响应encode在try之外，超限导致无结构响应；可在编码前后返回有界错误。EPIPE已断流不能保证再输出错误帧，应保持结果不确定而非重试。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentWorker.java:38

## OCR-0444 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentWorker.java:131；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：command 分支对 callId/mode/argv/timeoutMs 全部裸强转：mode 通过 CommandRequest.Mode.valueOf 解析，未知值抛 IllegalArgumentException；缺 timeoutMs 抛 NPE；timeoutMs 若为小数（Jackson 解成 Double）会被 longValue() 静默截断，例如 0.5 变为 0 触发 "Invalid bounded command"。这些参数错误最终都只会表现为 WORKER_IO。建议在构造 CommandRequest 之前校验字段存在、类型为正整数且在该 mode 下 argv 形态合法，并把非法输入映射为 INVALID_REQUEST。
- 判断：command模式与timeoutMs裸转换使小数截断，归并worker参数严格校验并在副作用进入前拒绝。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/EnvironmentWorker.java:131

## OCR-0445 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpIo.java:35；OCR原严重度：medium
- 计划：无
- 原意见：第 35 行的 deadline 计算位于线程启动之后、try/finally 之外。若 timeout 为 null（NPE）或 Duration 过大导致 toNanos() 抛 ArithmeticException，此处异常会直接逃逸：虚拟线程已在第 25-34 行启动、result 无人等待、abort 与 thread.interrupt() 都不会执行，被遗弃的 action 会继续占用共享的 input/output 流（stdio 进程或 HTTP 连接），后续 exchange 与之并发读写导致帧错乱。建议在启动线程前完成 timeout 校验与 deadline 计算（或把整个方法体包进统一的 try/finally 以保证线程被回收）。当前调用方虽经 bounded() 校验，但 run 作为通用帮助方法不应依赖此约定。
- 判断：McpIo为包内帮助类，现有两transport构造均McpIo.bounded限制60秒非空；不存在给定超大/空duration入口，不独立立项。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpIo.java:35

## OCR-0446 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpIo.java:54；OCR原严重度：high
- 计划：R1-02 / P1
- 原意见：abort.run() 抛出的运行时异常会覆盖 try 块中正在抛出的 HarnessException（MCP_OUTCOME_UNKNOWN 或 MCP_TRANSPORT_FAILED），调用方收到与协议无关的 RuntimeException，UNKNOWN 语义丢失，上层无法判断该操作是否已产生副作用。此外 thread.interrupt() 之后未 join，忽略中断的 worker 仍会长期泄漏并持续读取共享流。建议吞掉/抑制 abort 的异常以保留原始失败码，并在中断后做有界等待。
- 判断：finally abort异常能覆盖MCP_OUTCOME_UNKNOWN，且只有interrupt无有界等待；需保留首因并明确工作线程退出/清理未知，不能改为确定未执行。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpIo.java:54

## OCR-0447 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpIo.java:73；OCR原严重度：high
- 计划：R3-03 / P2
- 原意见：Map.of 不接受 null 值。BoundedJson.decode + Values.freeze 允许 JSON null，因此对端只要发送 {"jsonrpc":"2.0","id":null,"method":"roots/list"} 这样的帧，StdioMcpTransport:73-74 / HttpMcpTransport:136-137 就会走到 rejection()，request.get("id") 返回 null 触发 NPE，最终被包装成 MCP_TRANSPORT_FAILED 并 abort 传输，而不是返回 JSON-RPC 错误响应（JSON-RPC 2.0 允许 id 为 null）。请用允许 null 的 Map（LinkedHashMap）构造应答。
- 判断：rejection把对端id=null塞进Map.of触发NPE，需明确支持/拒绝null id的结构化协议路径，不能把合法拒绝响应变传输崩溃。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpIo.java:73

## OCR-0448 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpIo.java:69；OCR原严重度：medium
- 计划：R3-03 / P1
- 原意见：响应校验过弱：只要没有 method 且 id 相等就认定为响应，既不要求存在 result 或 error，也没有用 containsKey 判断 id 是否真的存在。结果是 {"jsonrpc":"2.0","id":x} 这种畸形帧会被 exchange 当成正常响应提前返回，帧循环随即结束、后续合法帧留在流中，下一次 exchange 将从错位的帧开始读取；同时当期望 id 为 null 时，任何缺少 id 的帧（例如通知）都会被 Objects.equals(null, null) 误判为响应。建议用 containsKey("id") 精确匹配，并要求 result 与 error 恰有其一。
- 判断：response只检jsonrpc/method/id，没有要求result/error互斥，畸形帧可提前闭合；需完整envelope验证和帧同步回归。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpIo.java:69

## OCR-0449 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpIo.java:38；OCR原严重度：medium
- 计划：R3-03 / P2
- 原意见：取消/超时检查排在取结果之前。若 action 已成功完成（result.isDone() 为 true），但此刻 token 被取消或 deadline 刚过，这里会先抛 MCP_OUTCOME_UNKNOWN，把一个已知成功的执行结果报告成未知；按“UNKNOWN 不得被重放”的要求，上层只可能放弃或重试，重试会对有副作用的工具调用产生重复执行。建议先消费已完成的 result，再判断取消与超时。
- 判断：run在消费已完成future前检查取消可能过度UNKNOWN，接受确定结果与deadline规则；UNKNOWN本身阻止重放，意见声称必然重复执行不成立。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpIo.java:38

## OCR-0450 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpIo.java:31；OCR原严重度：low
- 计划：R1-02 / P2
- 原意见：worker 只捕获 Exception。若 action 抛出 Error（如 4MB 载荷解析导致的 StackOverflowError / OutOfMemoryError），result 永远不会完成，主线程会空转轮询直到最长 60 秒超时，然后报 MCP_OUTCOME_UNKNOWN 并 abort 传输，真实失败原因被完全丢弃。建议捕获 Throwable 以完成 future。
- 判断：Error不会complete future，造成等到deadline才关闭；可在任务finally确保完成/退出事实，不能把OOME当普通可恢复错误吞掉继续服务。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpIo.java:31

## OCR-0451 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpTransport.java:6；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：接口只用注释要求“必须有界”，但 exchange 没有把每次调用的超时/截止时间暴露给调用方，也未约定取消时在途 IO 的语义和异常结果。McpPlugin 在 initialize/discovery 阶段传入 CancellationToken.NONE，若实现未按注释实现有界 IO，插件启动会无限阻塞；工具调用超时/取消后调用方也无法区分“请求未发送”和“可能已执行”，只能按 UNKNOWN 处理。建议像 CommandTransport 一样显式接收 Duration，并规定取消必须中断在途 IO、超时/取消使用固定错误码且绝不允许重试。
- 判断：McpTransport只有有界注释，需明确每实例timeout及取消UNKNOWN契约；内建stdio/http已有60秒构造限额，无需强制破坏接口新增每调用Duration。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpTransport.java:6

## OCR-0452 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpTransport.java:10；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：message 与返回值的所有权/可变性未定义。当前调用方（McpPlugin.rpc）传入的是 Map.of 生成的不可变 Map，实现若就地补字段会抛 UnsupportedOperationException；返回值也未要求非 null 或由调用方独占，若实现复用同一个可变 Map，多次/并发 exchange 会互相污染。建议在契约中明确 request 只读且实现不得保留，response 必须非 null 且归调用方所有（或返回不可变副本）。
- 判断：公开McpTransport未明确request/response所有权和非空，补不可变输入/快照输出契约及替身验证。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpTransport.java:10

## OCR-0453 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpTransport.java:8；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：kind() 会直接进入 ToolResult 元数据（McpPlugin 使用 Map.of("transport", transport.kind())），但这里未约定非 null、稳定且不含敏感信息。实现返回 null 会在 Map.of 处触发 NPE；返回动态值会破坏持久化/审计元数据的一致性；若返回端点或凭据还会造成泄露。建议声明 @return 为非 null 的稳定标识，并禁止包含凭据、端点等敏感信息。
- 判断：kind进入元数据，应要求稳定非空无秘密标识；实际两个内建kind均常量，第三方恶意泄密尚无证据。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpTransport.java:8

## OCR-0454 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpTransport.java:13；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：close() 的生命周期语义未定义：是否幂等、关闭后 exchange 的行为、部分初始化失败时是否释放资源均未说明。McpPlugin 会通过 context.own(transport) 和工具失败路径多次调用 close()；实现若不幂等，可能重复杀进程/关闭流或抛异常，造成资源泄漏或掩盖原始错误。建议明确 close 必须幂等、失败报告方式、关闭后 exchange 必须失败（如 MCP_CLOSED），并保证释放所有底层资源。
- 判断：传输可能由作用域own及异常路径重复关闭，明确close幂等/关闭后exchange拒绝及清理失败语义，配内建正反例。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpTransport.java:13

## OCR-0455 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java:117；OCR原严重度：high
- 计划：R1-03 / P1
- 原意见：Non-zero docker exec exits are mapped to retryable ENVIRONMENT_IO, but the worker may have already applied side effects before failing to write its response or being killed. Treat write/edit/patch and command paths consistently with the uncertain branch: close the container and return an UNKNOWN/sideEffectUncertain result rather than a retryable IO error.
- 判断：worker可能写入后编码/退出非0，Docker路径只抛ENVIRONMENT_IO丢失明确不确定信号；核对管线started兜底后统一标识，不能称所有ENVIRONMENT_IO都自动重试。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java:117

## OCR-0456 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java:226；OCR原严重度：medium
- 计划：R1-03 / P1
- 原意见：（第226-230行）worker 标记 sideEffectUncertain=true 时只关闭容器，却没有把 status 改为 UNKNOWN，仍按 worker 报告的 TIMEOUT/CANCELLED/FAILED 甚至 SUCCESS 返回；下游若只依据 status 判断会把它当成已确认结果（本仓库的契约是 sideEffectUncertain ⇒ UNKNOWN）。另外 value 中的 "containerCleanup=removed" 只有在 close() 成功返回后才可信。触发条件：worker 返回 value.sideEffectUncertain=true 而 status != UNKNOWN。建议在该分支强制 status = ToolStatus.UNKNOWN。
- 判断：sideEffectUncertain=true仍保留原status，虽Loop另查该标志而不重放，公开ProcessRunner消费者可能只看status；应统一UNKNOWN同时保留原始退出事实。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java:226

## OCR-0457 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java:203；OCR原严重度：high
- 计划：R1-03 / P1
- 原意见：（第203-204行）不确定路径先调用 close() 再返回 UNKNOWN 结果：close() 在 docker rm 失败或超时时会抛 CONTAINER_CLEANUP_UNKNOWN（第289行），从而把带 sideEffectUncertain=true 的 UNKNOWN ToolResult 换成异常，丢失“副作用不确定”信号；第114行（工作区写操作）和第228行同理，会把 ENVIRONMENT_OPERATION_UNKNOWN 替换成清理错误。构造器第130-134行已用 addSuppressed 处理同类情况，此处缺失。触发条件：exec 结果不确定且容器清理同时失败。建议对 close() 做 try/catch（addSuppressed 或把 cleanupStatus 记为 unknown），仍返回 UNKNOWN/sideEffectUncertain 结果。
- 判断：不确定分支先close，清理失败会覆盖ENVIRONMENT_OPERATION_UNKNOWN/UNKNOWN结果；应分别保留操作未知与清理未知，禁止虚报removed。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java:203

## OCR-0458 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java:287；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：（第287-289行）在确认 docker rm 成功之前就把 closed 置为 true：rm 返回非零或 uncertain 时对象仍标记为已关闭，后续 close() 直接 no-op，无法重试清理，容器（仍以读写方式挂载了工作区）会一直泄漏到人工处理，且没有任何代码路径能再次触发 rm。触发条件：docker daemon 短暂不可用/超时导致 rm 失败。建议仅在 rm 确认成功（exitCode==0 && !uncertain）后置 closed，或单独记录待清理状态以允许重试。
- 判断：close在rm未确认成功时closed=true，后续close不再尝试；需区分能力失效与清理待核实，允许明确清理操作而不恢复执行。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java:287

## OCR-0459 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java:39；OCR原严重度：high
- 计划：无
- 原意见：（第39、65行）workspace.toRealPath() 会解析符号链接，随后把解析后的目录以读写 bind mount 挂进容器，但没有校验它仍位于允许的工作区根之下：若 workspace 是指向 /、$HOME 或其他宿主目录的符号链接（或路径中含符号链接组件），容器内的 worker 与命令会以 65534 身份获得该目录的读写能力，突破文件系统边界；第40-43行只校验了目录类型与逗号注入。触发条件：调用方传入符号链接形式的 workspace（该类是 public API，测试/其他调用方也会直接构造）。建议在解析后校验规范化路径位于显式配置的允许根之下，或直接拒绝符号链接形式的 workspace。
- 判断：workspace是可信宿主显式授权根，此构造器不存在另一个允许根可比较；toRealPath选定根不是模型输入越界。不得凭宿主选择HOME推导突破已授权边界。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java:39

## OCR-0460 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java:80；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：（第81-86行）启动失败后的清理忽略了 rm 的结果：docker run 超时/非零但容器已创建时调用 rm，却未检查 Reply；若 rm 非零或 uncertain，容器会继续运行（工作区仍被挂载），而构造器抛异常后没有任何对象能再调用 close() 清理。另外 transport.call 若直接抛异常，会掩盖 CONTAINER_START_FAILED/DOCKER_UNAVAILABLE 语义。触发条件：docker run 已创建容器但返回不确定，随后 rm 失败。建议检查 rm 的 exitCode/uncertain 并合并进错误信息，或用 try/catch 附加 suppressed。
- 判断：启动失败补偿rm未检查Reply，可能遗留容器而只报启动失败；应带容器身份及清理未知事实，保留原异常及补偿异常。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java:80

## OCR-0461 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java:140；OCR原严重度：medium
- 计划：R1-03 / P1
- 原意见：（第140-141行）ensureOpen() 与实际 docker exec 之间是 check-then-act：closed 只在 rm 完成后才置位，close() 可能在检查通过之后、exec 发出前或执行中删除容器，正在执行的 write/命令被中断后以非零退出返回，从而走第117/219行被当作 ENVIRONMENT_IO（可重试），造成非幂等操作被重放。触发条件：工具执行与 close()/不确定清理并发。建议用读写锁或活跃操作计数让 close() 等待在途操作，或让在途操作在关闭过程中一律按 UNKNOWN 处理。
- 判断：exec与close并发确能产生中断/非0，需一并审计活跃操作与关闭状态；根问题与455/457合并，不接受close后自动重发。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java:140

## OCR-0462 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ModelDisplayBuffer.java:45；OCR原严重度：high
- 计划：R4-03 / P2
- 原意见：第 42-45 行的节流直接 `return` 会丢弃本帧累积文本，而之后不一定还有 `accept` 调用。`StreamingLlmClient` 的最后一帧是 `display.accept(redactor.text(response.text()))`（补充被 `hold` 尾窗扣下的内容），紧接着的 `model.observed`/`assistant.message` 不再触发 `accept`。触发条件：最后一段与上一次被采纳的更新间隔 <50ms 且长度增量 <16384（绝大多数流式会话都满足）——结果是 `snapshot()` 永久停留在旧的累积前缀上，最终答复（尤其是被尾窗扣住的那段尾部）在展示中永远缺失，直到下一次 `reset()`。建议把被节流的值缓存为 pending，并在 `snapshot()` 中补一次应用，或提供结束时强制冲刷的入口。
- 判断：节流return可丢临时流末尾，持久assistant.message仍完整且UI应切换最终消息；接受末帧flush/快照pending，不能称最终答复永久丢失。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ModelDisplayBuffer.java:45

## OCR-0463 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ModelDisplayBuffer.java:48；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：第 42 行的增量判断 `value.length() - inputLength < 16384` 只有在输入是“单调累积快照”时才成立，但本类是被 `model.withDisplay(display::accept)`（HarnessSdk.java:146）直接绑定的，而 `LlmClient.withDisplay` 的契约（LlmClient.java:7-10）并未要求累积、也不要求单调。若适配器按增量（delta）回调，`text` 会被覆盖成最后一段增量而不是完整累积文本（第 55 行是整体替换而非拼接），且增量长度差永远 <16384，16KB 这一分支成为死条件；若回调长度回退，`text` 反而会被更短的值覆盖。建议在 `accept` 中明确累积语义（长度回退/非累积时拒绝覆盖或改为追加），并把 `inputLength` 的更新与判空、单调校验放在一起。
- 判断：ModelDisplayBuffer明确累积快照而LlmClient公开display约定不充分；应统一累积语义和回退规则，不能自动拼接不明delta造成重复。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ModelDisplayBuffer.java:48

## OCR-0464 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ModelDisplayBuffer.java:33；OCR原严重度：medium
- 计划：无
- 原意见：`sourceId/turnId/stepId` 只在一次流式周期的首个 `accept` 捕获一次，之后再没有与最新 `model.requested` 比对。触发条件：重试/多次 `complete`（模型重试插件对同一 step 重新 prepare 时不一定再走 BEFORE_MODEL 的 `reset()`），或插件在上一次请求结束后读取 `snapshot()`——此时 `text` 已是新请求的内容，但 `snapshot()` 报出的 `sourceEvent/turnId/stepId` 仍是旧请求，且第 61-74 行的状态推导以旧事件位置为起点，会把新请求的 PERSISTED/INTERRUPTED 判定挂到错误的源事件上。建议在每次 `accept`（或检测到日志中最新 `model.requested` 变化时）重新绑定源事件，并在源事件变化时清空 `text/truncated`。
- 判断：重试每次attempt仍执行prepare并经过BEFORE_MODEL reset，意见前提未成立；对失败请求关闭后的snapshot按sourceEvent保留原事实是预期。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ModelDisplayBuffer.java:33

## OCR-0465 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ModelDisplayBuffer.java:49；OCR原严重度：medium
- 计划：R4-03 / P1
- 原意见：此处运行在模型流式回调链上（HarnessSdk.java:146 `model.withDisplay(display::accept)` → StreamingLlmClient → 网络帧回调）。`sanitizer` 是插件/宿主注入的实现，`Sanitizer/streamingText` 若抛出异常会沿回调上抛，把“展示失败”升级成模型请求/流式传输失败；第 34-37 行的 `reader.events()` 同样会抛 SESSION_READ_FAILED/TAIL_DAMAGED。另外 `value` 为 null 时第 43 行 `value.length()` 直接 NPE。建议对脱敏与读取做异常隔离并降级（如清空 `text`），保证展示通道永远不会中断模型调用。
- 判断：临时展示callback reader/sanitizer异常可终止模型流；允许失败时清空展示并留诊断，禁止展示未脱敏原文，需保留模型调用真实未知事实。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ModelDisplayBuffer.java:49

## OCR-0466 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ModelDisplayBuffer.java:59；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：`snapshot()` 每次调用都执行 `reader.events()`，而 JsonlSessionRepository.openReader 的实现是每次都重新解析整个 JSONL 文件（JsonlSessionRepository.java:172-186）；UI 轮询 + 长会话时这里是 O(n) 反复全量解析。更关键的是该 I/O 在持有 `this` 监视器期间执行，会阻塞流式线程进入 `accept()`（同一个锁），并且 `events()` 抛出的 SESSION_READ_FAILED/TAIL_DAMAGED 会让 `snapshot()` 整体失败、无法返回任何状态。建议缓存/增量读取事件进度（例如记录 sourceId 在日志中的索引，只扫描新增部分），并把可能抛错的读取包在 try/catch 中降级为上一次已知状态。
- 判断：snapshot持监视器全量reader.events轮询，与accept争锁；增量索引及锁外读取降低长会话阻塞，不能读失败时伪造PERSISTED。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ModelDisplayBuffer.java:59

## OCR-0467 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ModelDisplayBuffer.java:65；OCR原严重度：medium
- 计划：无
- 原意见：终态判定完全依赖事件插入顺序，且各分支优先级不一致：`assistant.message` 无条件覆盖（包括已判定为 INTERRUPTED 的状态），而第 69-72 行的 `turn.ended` 只在仍为 STREAMING 时降级，`model.observed` 的非 SUCCESS 也没有任何状态保护。触发条件：同一 sourceEvent 先出现非 SUCCESS 的 `model.observed`（或先由 `turn.ended` 判定中断）之后又落盘了该 step 的 `assistant.message`（重试后成功、部分落盘）——最终状态会被改写成 PERSISTED，从而掩盖真实的中断事实；反之若事件顺序被重放/补齐，也会得到相反结论。建议为 IDLE/STREAMING/PERSISTED/INTERRUPTED 定义显式优先级（例如 PERSISTED 与 INTERRUPTED 互斥且以事实事件优先），而不是按遍历顺序互相覆盖。
- 判断：成功assistant.message是持久响应事实，后续优先显示PERSISTED并不掩盖已知成功；给出的失败后同源成功路径未按reset链成立。真实多请求归属另以sourceEvent测试。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ModelDisplayBuffer.java:65

## OCR-0468 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/NativeCommandTransport.java:45；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：第 45 行 `sent.get()` 在子进程提前退出/关闭 stdin 时会抛 ExecutionException（broken pipe），随后被第 50 行 catch 捕获并丢弃已经拿到的 exitCode 与完整 stdout/stderr，返回 `started=true, uncertain=true`。触发条件：payload 非空且子进程未读完 stdin 就退出（如 docker exec 中 worker 先报错退出、命令本身不读 stdin）。后果是该次实际已确定完成的命令被上报为 UNKNOWN，调用方（DockerExecutionEnvironment 111/203 行）会关闭环境并拒绝重放，属于结果状态误判。建议把 stdin 回执失败与执行结果解耦：仅当 out/err 也失败或进程仍存活时才视为不确定，否则照常返回 exitCode 与输出。
- 判断：sent.get失败丢弃已收集退出码/输出，接受保留观测数据及原因；stdin未完整发送是否可证明副作用结果须按协议判断，不能一律认定确定成功。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/NativeCommandTransport.java:45

## OCR-0469 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/NativeCommandTransport.java:0；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：第 47-51 行把 IOException/ExecutionException 全部折叠成 `started = process != null, uncertain = process != null`，与超时/取消使用完全相同的状态位，且不区分「stdout 超过上限」「stdin broken pipe」「reader 线程异常」这些可确定的失败原因，异常对象也被直接吞掉（无日志、无 cause 传递）。调用方无法区分真正的 UNKNOWN 与确定性失败，会把确定性失败当不确定处理（关环境、拒绝重放），排障时也拿不到原因。建议按异常来源分类：进程未启动→(started=false, uncertain=false)；已启动但无法确定副作用→uncertain=true；输出越界等确定性错误给出明确错误码，并至少保留 cause 用于诊断。
- 判断：IOException来源被抹平确实不利诊断；错误类型与副作用确定性是两维，输出越界不证明执行未发生，必须保留uncertain。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/NativeCommandTransport.java:0

## OCR-0470 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/NativeCommandTransport.java:38；OCR原严重度：medium
- 计划：R5-01 / P1
- 原意见：第 38 行轮询条件只看 isDone，不检查 future 是否异常完成。当 `read()`（第 73-74 行）因输出超过上限抛 IOException 时 future 已异常完成，但循环会继续等子进程自然结束；此时子进程若还在写 stdout，由于管道已关闭/不再读取会被阻塞，进程不会退出，只能一直空转到第 39 行的 deadline，等于把「输出越界」变成一次完整超时等待，最终仍只得到 uncertain。建议在循环内检测 `out.isCompletedExceptionally() || err.isCompletedExceptionally()` 时立即 destroyForcibly 并快速失败（并保留「bound exceeded」这一确定性错误语义，而不是混入 uncertain）。
- 判断：输出future异常后仍等进程至deadline，需提早停止和有界收尾；输出越界后有副作用状态仍须UNKNOWN，不能按建议改确定失败可重试。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/NativeCommandTransport.java:38

## OCR-0471 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/NativeCommandTransport.java:37；OCR原严重度：medium
- 计划：R5-01 / P2
- 原意见：第 37 行的 deadline 在 `builder.start()`（第 22 行）、环境变量构建和 3 个虚拟线程创建之后才计算，因此进程启动阶段（fork/exec 慢、容器 runtime 拉起慢或阻塞）完全不受请求的 timeout 约束，调用方声明的超时会被突破。另：`Duration.toNanos()` 对超大 Duration 会抛 ArithmeticException，这是未被第 47/50 行捕获的 RuntimeException，虽然 finally 会杀进程，但调用方会收到运行时异常而非 Reply。建议在方法入口先计算 deadline（或对启动使用剩余时间），并在进入 try 前做 safe 的 duration 校验。
- 判断：deadline在builder.start之后计算，启动耗时未纳入预算；提前校验Duration/计时并核验启动限制。不能只移动时间戳宣称已可中断操作系统阻塞start。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/NativeCommandTransport.java:37

## OCR-0472 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/NativeCommandTransport.java:40；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：第 40-41 行在超时/取消时只调用 destroyForcibly（异步信号，不等待进程终止）就立即 return，finally（第 52-61 行）也只关闭流而不 waitFor，reader/writer 虚拟线程也未 join。此时本方法已返回、子进程仍在运行，调用方基于 `uncertain` 立刻发起的补偿/清理命令（如 DockerExecutionEnvironment 80-85 行的 `docker rm --force`）会与尚未退出的进程产生竞态；频繁超时还会累积未回收的子进程。建议 destroyForcibly 后用剩余时间/固定小超时 waitFor，并在返回前 join 读线程（或至少保证流关闭后线程已退出）。
- 判断：destroyForcibly后无wait/join，返回时不能证明退出；应有界等待和资源残留事实，不声称强杀采样等于OS级隔离。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/NativeCommandTransport.java:40

## OCR-0473 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/NativeCommandTransport.java:43；OCR原严重度：low
- 计划：无
- 原意见：第 43 行用 10ms sleep 轮询，每条控制命令都会引入最多 10ms 的固定尾延迟（Docker 环境下每个 op 一次），长命令期间还持续唤醒；同时 sleep 被中断时会被第 47 行捕获并以 uncertain 返回。建议改用 `child.waitFor(remaining, TimeUnit.MILLISECONDS)` 配合 futures 的完成回调，既避免空转也天然把等待限定在剩余超时内。
- 判断：10ms轮询是合作取消的有界等待策略，无性能测量证明故障；改waitFor仍需取消轮询，不为固定尾延迟独立立项。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/NativeCommandTransport.java:43

## OCR-0474 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:141；OCR原严重度：medium
- 计划：R2-02 / P1
- 原意见：只剔除了顶层 `id`，但 `descriptor()` 可能递归内嵌环境描述：`ReadOnlyChildEnvironment.descriptor()` 返回 `"parent": parent.descriptor()`（其中包含 Docker 的随机容器实例 id）。而 `docs/development/M7-contracts.md` 明确要求“重开时校验绑定及环境描述（容器随机实例 id 除外）”。触发条件：以 read-only-child（其 parent 为 Docker 环境）创建会话后，用新的容器实例重开同一会话，`binding` 中的 parent.id 变化 → line 165 必然抛 `ENVIRONMENT_MISMATCH`，合法会话无法重开。建议递归剔除/规范化所有层级中随机实例 id（或对 descriptor 做显式的稳定字段投影，而不是整表 equals）。
- 判断：binding仅移除顶层id，ReadOnlyChildEnvironment嵌套parent.id仍带Docker随机实例，重开合法相同绑定会误拒绝；稳定字段投影需递归明确。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:141

## OCR-0475 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:166；OCR原严重度：medium
- 计划：R2-02 / P1
- 原意见：`binding` 写入走的是 `SanitizingSessionWriter`（`sanitizer.sanitize(event.payload())` 且 `SecretRedactor` 会按 key 名（secret/password/apikey…）与包含 secret 的字符串做替换），而这里用未脱敏的原始 `environment.descriptor()` + `workspaceBinding` 重新计算后做严格 `equals`。触发条件：宿主 secret 列表中的值出现在 workspaceBinding/descriptor 值中，或宿主自定义 ExecutionEnvironment 的 descriptor 含 `secret`/`password` 之类键名时，落盘值被改写为 `[REDACTED]`，重开会话将永久报 `ENVIRONMENT_MISMATCH`（且无绕过路径）。建议比较前对同一 `binding` 先应用相同 sanitizer，或仅对稳定的白名单字段做比较。
- 判断：创建通过SanitizingSessionWriter保存binding，重开却拿原始binding比较，秘密与绑定字段重合时误拒绝；用稳定安全指纹/相同规范比较，不以脱敏合并值冒充身份。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:166

## OCR-0476 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:165；OCR原严重度：low
- 计划：R2-02 / P2
- 原意见：`sdk.created` 中写入了 `protocolVersion`，但重开时只补 `agentId` 并比较 binding，从未校验 `created.get().payload().get("protocolVersion")` 与 `PROTOCOL_VERSION`。触发条件：用 1.14 之前的 SDK 创建、之后升级 SDK 打开同一会话时，协议不兼容也被静默接受（与 writer 对 `schemaVersion` 的 `FUTURE_VERSION_READ_ONLY` 校验策略不一致）。建议显式比较协议版本，不匹配时给出明确错误码。
- 判断：重开未校验sdk协议支持范围，应拒绝未来/不兼容版本并允许明确兼容旧版；不能所有不等于1.14都拒绝破坏承诺兼容。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:165

## OCR-0477 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:168；OCR原严重度：high
- 计划：R2-02 / P1
- 原意见：初始化事件（`session.created`/`sdk.created`/`subagent.origin`/`system.message`）在插件装配、`assembly.start()`、工具清单上限校验与 `filtered.validate(scope)` 之前就被 `append`，而 `JsonlSessionRepository.Writer.append` 每条都立即 `barrier.force`，即逐条持久化。存在的缺陷：1) 进程在 `session.created` 与 `sdk.created` 之间崩溃，重开时 `events` 非空而 `created` 为空 → 永久落入 line 161 的 `SDK_SESSION_BINDING_REQUIRED`，该会话没有任何恢复路径；2) 装配/清单校验失败（如 TOOL_INVENTORY_LIMIT）后重试会跳过创建，却重复追加 `subagent.origin` 与 `system.message`，日志持续增长且没有任何“初始化已完成”标记可区分半初始化会话（`extensions.ready` 仅在末尾写入）。建议先完成装配与校验再持久化初始化事件，或写入显式的初始化开始/完成标记并在重开时校验。
- 判断：session.created与sdk.created分次持久化存在半初始化窗口，重开拒绝但非副作用重放；需显式初始化状态及安全诊断/恢复。不能把必须记录的事实全部推迟到有副作用插件装配后。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:168

## OCR-0478 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:374；OCR原严重度：medium
- 计划：R2-02 / P2
- 原意见：`sdk.turn.configured` 在 `input` 提交关联校验（line 405-420）之前就落盘，而 append 是逐条 force 的持久写入。触发条件：宿主传入非法 `input`（如 requestHash 格式错误）时，durable 日志中留下一条“已配置但从未执行”的 turn 事件；宿主反复以非法参数调用 run 会无界增长日志，最终触发 `JsonlSessionRepository` 的 10000 事件 / 32MB `SESSION_LIMIT`，使会话再也无法写入（但轮次并未真正执行）。建议先完成全部校验再写入该事件。
- 判断：input验证晚于sdk.turn.configured写入，无效提交污染日志且占限额；纯参数验证应在任何新轮持久化前完成。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:374

## OCR-0479 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:407；OCR原严重度：medium
- 计划：R2-02 / P1
- 原意见：`sdk.input.applied` 在它引用的 `user.message`（line 421 之后才 append）之前持久化。触发条件：两次 append 之间进程崩溃，或后续 `writer.append(userMessage)` 抛出（如 SESSION_LIMIT/CHECKPOINT_FAILED）时，日志中留下指向不存在事件的悬空 `userEventId`，恢复/审计无法解析该关联；另外该事件的 `"boundary":"next-turn"` 与其引用的“本轮”用户消息语义冲突，读取方按 boundary 重放会错位一轮。建议先 append `userMessage`，再写入引用它的 `sdk.input.applied`，并修正 boundary 标签。
- 判断：sdk.input.applied先于其引用user.message写入，崩溃可悬空；需定义可恢复原子边界/校验顺序，不能简单换序后忽略另一方向丢响应幂等窗口。next-turn标签需对照Web消费契约。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:407

## OCR-0480 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:371；OCR原严重度：medium
- 计划：R2-02 / P1
- 原意见：`fees.beginTurn()` 位于 goal 与 input 校验之前，而 `SharedFeeBudget.beginTurn()` 会执行 `reserved = ZERO; epoch++`。触发条件：一次被拒绝的 run（goal 为空/超长、或 input 非法）也会重置共享费用预算的预留额并推进纪元；此时若仍有在途模型调用（共享同一 `fees` 的委派子代理，或延迟返回的响应）持有 `pending/pendingEpoch`，其 `actualCost` 会因 `pendingEpoch != epoch` 抛 `COST_ACCOUNTING_BREACH`，且已预留但未结算的成本被从 `reserved` 中抹掉，导致超预算放行。建议在 goal/input 全部校验通过后再调用 `beginTurn()`。
- 判断：beginTurn在goal/input校验前重置费用，非法run会推进epoch；先校验可修复。SDK run串行且恢复门禁先检查，未证明非法并发可绕过费用，应加子任务延迟结算回归再定影响。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:371

## OCR-0481 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:517；OCR原严重度：high
- 计划：R1-02 / P1
- 原意见：同一个 try 同时覆盖 `container.close()` 与 `environment.closed`(SUCCESS) 的写入。触发条件：容器已成功关闭但成功事件写入失败（如 SESSION_LIMIT/WRITER_POISONED）时，catch 会再追加一条 `UNKNOWN`，把“已关闭”误报为未知状态；若这次 UNKNOWN 追加也失败，则原始异常被第二个异常替换而丢失，环境关闭状态完全没有记录。另外 `closed` 在失败后仍为 true（第 524 行 CAS 之后不再复位），`close()` 无法重试，已部分释放的容器/环境资源（如 Docker 容器）可能泄漏。建议拆分两段 try：先 `container.close()` 并保留其异常，再单独尝试记录状态；失败时复位 `closed` 或提供重试入口。
- 判断：container.close与SUCCESS事件写入共用catch，落盘故障被误写成UNKNOWN且第二异常覆盖首因；应拆分资源事实/记录事实，关闭后不能简单复位允许任务再执行。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:517

## OCR-0482 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:404；OCR原严重度：low
- 计划：R2-02 / P2
- 原意见：`input` 的值可为 null：`input.get("requestHash")` 为 null 时 `.matches(...)` 直接 NPE，`input.get("kind")` 为 null 时 `Set.of("message","supplement").contains(null)` 在不可变集合实现中同样 NPE（`Map.of` 亦不接受 null）。宿主本应得到 `INVALID_SUBMISSION`，实际却是未包装的 NullPointerException。建议做显式空值判断。
- 判断：input键齐但requestHash/kind=null可NPE，需在任何持久化前规范校验并给INVALID_SUBMISSION。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:404

## OCR-0483 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:491；OCR原严重度：low
- 计划：R2-02 / P2
- 原意见：`verifyEnvironment` 先以 EXCLUSIVE 方式 `openWriter`（`JsonlSessionRepository.openWriter` 对不存在的日志会 CREATE 并写入 header），再读取事件做校验，且未检查 `SessionLifecycle.archived(events)`。触发条件：1) sessionId 拼写错误时会凭空创建空会话日志与 .lock 文件（污染 catalog，且要等 try-with-resources 关闭后才释放锁）；2) 对已归档（只读）会话调用时会向其追加 `environment.verified`，破坏“归档只读”的约束。建议先用 reader 校验会话存在、未归档且确实含该 uncertain 调用，再以 EXCLUSIVE 打开写入。
- 判断：verifyEnvironment先openWriter会创建不存在日志，且缺归档门禁；先只读存在性检查并在取得独占后再次确认，不能只做一次TOCTOU前检。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java:491

## OCR-0484 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkConfiguration.java:35；OCR原严重度：high
- 计划：无
- 原意见：`tools` 是唯一未做非空校验的组件：`prompt`/`scheduling`/`resilience` 都 `requireNonNull`，而这里仅在非空时拷贝。下游 `HarnessSdk` 构造函数把 `options.tools() == null` 解释为“不过滤”，直接使用未加 `FilteredToolRegistry` 的 `ScopedToolRegistry`（HarnessSdk.java:128-131）。因此当宿主从可选配置/反序列化结果构造 `SdkConfiguration(prompt, null, params)` 时，会静默放行全部工具，与类注释“exact tool allowlist”的语义相反，属于 fail-open 的权限缺陷。建议在紧凑构造器中要求 `tools` 非空（如需“不限制”语义，应使用显式常量如 `Set.of("*")` 或专用构造器，而不是 null）。
- 判断：tools=null是root兼容构造显式不过滤语义，仍沿policy/approval，不等于开放未审批副作用；改变为空集破坏现有SDK默认能力。可补说明非权限绕过。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkConfiguration.java:35

## OCR-0485 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkConfiguration.java:7；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：record 未覆写 `toString`，自动生成的实现会把 `prompt`（可信根代理提示词，可能含内部策略/指令）以及 `parameters` 的全部键值（模型参数，常见如 apiKey/baseUrl/token 等凭据）拼入字符串。该类是公共 SDK 入口，宿主或第三方框架一旦打印配置对象、把它放进异常消息或写入日志，就会泄漏敏感值，绕过 `DataSanitizer` 的统一脱敏。建议覆写 `toString` 仅输出 prompt 长度、工具数量与参数键名（或整体占位），不输出具体值。
- 判断：record默认toString包含prompt/parameters，接受公共配置诊断安全表示；当前调用点未证明直接日志泄露，secret参数不应作为支持配置推广。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkConfiguration.java:7

## OCR-0486 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkConfiguration.java:35；OCR原严重度：low
- 计划：R5-01 / P2
- 原意见：仅对 `prompt` 做了 32768 的上限校验，`tools` 与 `parameters` 完全无界：`Set.copyOf` 与 `Values.snapshot` 会全量复制集合/映射（`Values` 只限制嵌套深度 64，不限制元素数量或字符串长度）。当宿主配置来源于外部文件/服务端下发时，超大工具集或超多键参与深拷贝会造成明显内存与 CPU 消耗（构造期即 O(n) 复制，且配置对象被长期持有）。建议对工具数量、参数键数量及单个字符串值长度设置与 prompt 一致的显式上限。
- 判断：宿主parameters/tools快照只限深度不限制大小，接受配置读取/工具声明入口统一数量与字节上限；可信root配置不按远程DoS定级。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkConfiguration.java:35

## OCR-0487 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HttpMcpTransport.java:0；OCR原严重度：medium
- 计划：R3-03 / P2
- 原意见：第 137 行：服务端可控的 incoming 直接传入 McpIo.rejection。触发条件：服务端下发 {"jsonrpc":"2.0","method":"x","id":null}，第 136 行的 containsKey("id") 为 true，而 rejection 内部用 Map.of 放入 null 值会抛 NullPointerException；该 NPE 被 McpIo.run 包装成 MCP_TRANSPORT_FAILED 并触发 abort，导致整条传输永久断开，而不是可诊断的协议错误。建议在调用前校验 id 必须是 String 或 Number，否则抛 MCP_PROTOCOL_ERROR。
- 判断：与447同一null-id Map.of问题；归并修复但保留独立原意见编号，不照搬String/Number拒绝null而不处理协议兼容。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HttpMcpTransport.java:0

## OCR-0488 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HttpMcpTransport.java:0；OCR原严重度：high
- 计划：无
- 原意见：第 136-140 行：SSE 中收到服务端请求时递归调用 post()，递归深度完全没有上限。恶意或异常服务端只要在“对上一个服务端请求的响应 POST”返回的 SSE 里再下发一个请求，就能让本客户端无限递归（每层都会重新获得 4MiB + 128 帧的独立预算），可在一次 exchange 内放大出任意多次 POST；递归足够深时抛出 StackOverflowError，而 McpIo.run 只捕获 Exception，result 永不完成，只能等 deadline 超时后 abort，此时已声明 UNKNOWN 的工作线程仍在继续发请求。另外嵌套 POST 是同步的，会阻塞外层 SSE 读取，服务端若先写外层流再处理该 POST 会互相等待到超时。建议增加嵌套深度参数并限制（例如 depth>0 直接 MCP_PROTOCOL_ERROR），同时让字节/帧预算在同一次 exchange 内共享，并在嵌套前检查 disconnected。
- 判断：post(rejection)的rejection没有method，McpIo.expectsResponse=false，要求202空body后直接return，不会进入SSE解析再次递归；所谓无限递归调用链不成立。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HttpMcpTransport.java:0

## OCR-0489 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HttpMcpTransport.java:0；OCR原严重度：high
- 计划：R1-02 / P1
- 原意见：第 140 行（配合第 117、151 行）：active 是单槽，且外层流在内层 post() 执行期间无登记——内层 finally 会先把 active 置 null，内层完成后才在这里重新登记。触发条件：调用方超时/取消触发 abort() 恰好落在内层 finally 与 active.set(input) 之间，abort 看到的 active 为 null（只做 shutdownNow），随后本行又把已被 abort 的流重新登记，之后再无 abort 会关闭它；并且读循环（第 117 行）不再检查 disconnected，外层会继续读取、甚至继续执行第 139 行的 POST。这既造成流泄漏，也违反“UNKNOWN 不得重放”：exchange 已向调用方返回 MCP_OUTCOME_UNKNOWN 后仍可能向服务端发送带副作用的请求。第 85 行 client.send 返回前 active 同样为空，存在同一窗口。建议用集合登记所有活动流并在 abort 时全部关闭，同时在本行之后及循环每轮检查 disconnected，为 true 时抛 MCP_DISCONNECTED。
- 判断：内层拒绝响应POST会临时覆盖active并finally清空，存在外层流登记窗口；应全活动流登记并检查disconnected。它是协议拒绝响应，不能称模型副作用工具被重放。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HttpMcpTransport.java:0

## OCR-0490 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HttpMcpTransport.java:0；OCR原严重度：high
- 计划：无
- 原意见：第 99 行：session 在仅确认 HTTP 200 后、任何内容校验之前就被提交。触发条件：initialize 响应 Content-Type 非法、JSON/SSE 解析失败或响应 id 不匹配（第 108-135 行）时异常向上抛出，调用方认为初始化失败，但对象内已保存该 MCP-Session-Id；后续 exchange（包括重新发送的 initialize，因为第 62 行会无条件附加该头）都会携带这个未经确认的会话 ID，服务端可能当成会话已恢复。建议先完成 Content-Type、报文解析与 id 匹配校验，全部成功后再赋值 session，失败路径显式把 session 复位为 null。
- 判断：初始化解析错误经McpIo.run异常finally执行abort，disconnected永久拒绝后续exchange；不会带坏session重新initialize。延后赋值可优化但非现有恢复错误。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HttpMcpTransport.java:0

## OCR-0491 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HttpMcpTransport.java:0；OCR原严重度：high
- 计划：R1-02 / P1
- 原意见：第 168 行（与第 50 行）：close() 与 exchange() 共用同一把对象锁，而 exchange 在整个 McpIo.run 期间持锁。触发条件：服务端对某个请求卡住时另一线程调用 close()，close 必须先等满 timeout（最长 60 秒）才能执行，期间既不能通过 abort() 关闭活动流，也不能发送 DELETE；同时第 170 行的 closed 标志在拿到锁前不会生效，新的 exchange 只会一起排队而不是立即返回 MCP_CLOSED。建议把关闭状态改为 AtomicBoolean/独立锁，close() 先无锁置位并 abort 活动流，再在尽量短的临界区内完成 DELETE 与清理。
- 判断：exchange和close同监视器，close无法立即abort在途最长60秒IO；应先原子撤销/abort再有界清理，测试关闭对阻塞exchange响应。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HttpMcpTransport.java:0

## OCR-0492 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HttpMcpTransport.java:0；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：第 28-36 行：loopback 判定只比较 URI 的文本 host。触发条件：'localhost' 的解析结果由 hosts 文件/DNS/系统解析器决定（可被改写或 DNS 重绑定），而第 69 行会向该地址以明文发送 Bearer 凭据；一旦 localhost 被映射到非本机地址，凭据与全部 MCP 报文即外泄，而这里放行的正是 http。建议对 http 额外解析目标地址并校验 InetAddress.isLoopbackAddress()（或仅允许 IP 字面量回环地址，或干脆只允许 https），不要把 'localhost' 文本等同于回环。
- 判断：http localhost只按文本放行，需要明确回环解析策略；仅增加一次DNS检查仍有解析竞态。可信宿主hosts被篡改属环境控制问题，不按已证凭据外泄处理。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HttpMcpTransport.java:0

## OCR-0493 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/HttpMcpTransport.java:0；OCR原严重度：medium
- 计划：无
- 原意见：第 179 行附近（close 内的 DELETE 分支）：close() 会因远端行为抛出未检查异常——DELETE 返回非 200/204/405 抛 MCP_CLOSE_FAILED，DELETE 超时由 McpIo.run 抛 MCP_OUTCOME_UNKNOWN，bearerToken 校验失败（第 65-68 行）也会从 request() 抛出。trigger：服务端不可达/凭据在关闭时已失效。McpTransport.close() 是 AutoCloseable 清理入口，从这里抛出的异常会传播给 try-with-resources 调用方，可能掩盖原始失败或让清理流程中断。建议 DELETE 的非预期状态仅视为清理失败并忽略，随后照常 abort。
- 判断：close最终finally始终abort，失败必须报告清理未知不能吞掉远端非预期状态；try-with-resources原异常有suppressed机制，意见声称必然覆盖不成立。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/HttpMcpTransport.java:0

## OCR-0494 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkHost.java:16；OCR原严重度：medium
- 计划：R2-02 / P2
- 原意见：SdkHost 没有紧凑构造器，因此所有协作对象都不做非空/一致性校验。已确认唯一构造点 HarnessSdk(HarnessSdk.java:272) 中 repository、sanitizer、approvals 等参数在 HarnessSdk 构造函数里并未 requireNonNull（仅校验了 environment/model/policy/budget/completion），一旦传入 null，SdkHost 会静默持有一个不完整的装配结果；消费方 SubagentPlugin 在 start() 里直接解引用 host.budget()（第 96-108 行）、event() 里 host.writer()（第 124 行），错误要等到插件启动或首次写事件时才以 NPE 形式暴露，且部分 NPE 发生在会话已创建、已写入 sdk.created 事件之后，属于跨步骤顺序上的晚失败。另外 scope 与 reader/writer/repository 之间也没有任何绑定约束，若将来用别的 scope 组装，插件会以本 scope 身份读写其它会话，破坏插件作用域隔离与回滚边界。建议在此处加紧凑构造器做非空与 scope 一致性校验，让装配错误在构造期（插件 start 之前）立即失败。
- 判断：SdkHost公开record没有边界校验，可补协作服务非空与scope绑定诊断；当前唯一构造来自SDK，不能把未来手工伪造scope认定现有越权。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkHost.java:16

## OCR-0495 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/PlanningPlugin.java:180；OCR原严重度：high
- 计划：R2-04 / P2
- 原意见：`plan_update`（第 177-183 行）只校验 revision，便把 state 无条件写成 DRAFT、把 evidence 清空，完全绕过 `plan_transition` 的状态机： 1) 计划处于 EXECUTING 时可直接改写 steps 并回退到 DRAFT，随后再次 APPROVED/EXECUTING，实际执行内容与已批准/已完成记录不一致； 2) COMPLETED 计划被回退成 DRAFT 后可再次被批准并完成，完成事实与其 evidence 轨迹被丢弃； 3) ABANDONED 从“终态”变成可恢复状态。 触发条件：先 `plan_view` 取 revision，再在任意状态调用 `plan_update(revision=n, steps=[...])`。建议仅允许 NONE/DRAFT 状态下编辑计划，其余状态必须走显式转换。
- 判断：plan_update可在EXECUTING/终态回DRAFT并清证据，接受显式编辑/新版本状态规则；工具审批仍独立，不能称重新批准计划自动授权执行。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/PlanningPlugin.java:180

## OCR-0496 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/PlanningPlugin.java:63；OCR原严重度：high
- 计划：R2-04 / P2
- 原意见：`evidence()`（第 63-79 行）只校验：ids 数量等于 required、当前列表内去重、每个 id 对应一条 SUCCESS 的 `tool.completed` 非 todo_/plan_ 工具事件；但没有记录“该事件已被消费”，也没有把证据绑定到 todo id / plan revision。因此同一批 eventId 可被反复重放： 1) 多个 todo 的 DONE 可共用同一个 tool.completed； 2) todo 的 DONE 与 plan 的 COMPLETED 可共用同一事件； 3) `plan_update` 会把 plan 的 evidence 清空（第 182 行），于是重置后的计划可再次用同一批历史事件完成。 这使得“完成”可以被伪造/重放，等于证据校验失效。触发条件：`plan_transition(state=COMPLETED, evidence=[<任意历史成功事件 id>])` 或 `todo_update(state=DONE, evidence=[同一 id])`。建议把已消费的 eventId 持久化（例如在 todo.changed/plan.changed 中累计 usedEvidence 并在 evidence() 中先做交集拒绝），或至少要求证据事件发生在本次计划/待办创建之后且未被任何历史 payload 引用。
- 判断：evidence只检查成功事件存在，未验证与具体步骤相关/发生于当前修订之后；接受关联语义。一个事件可合理支持多个待办，禁止一律一次消费，计划完成不代表外部目标真实达成。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/PlanningPlugin.java:63

## OCR-0497 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/PlanningPlugin.java:167；OCR原严重度：medium
- 计划：R2-04 / P2
- 原意见：容量上限把 REMOVED 墓碑也计算在内：`todos()` 收集所有历史 `todo.changed`（含 state=REMOVED），`all.size() >= 128` 用全部条目判断。一旦会话累计创建过 128 个不同 id，即使它们全部被 REMOVED，也无法再创建第 129 个新 todo（该 `all.size()` 永不下降），只能复用旧 id。这使 128 变成“会话生命周期内不同 id 数”的硬上限，而非“活跃待办数”上限。建议只统计非 REMOVED 条目，或在达到上限时允许回收已 REMOVED 的最旧 id。
- 判断：128计数包含REMOVED，确为生命周期ID上限；需先明确上限语义，若改活跃容量必须另限墓碑/历史投影，不能简单不计REMOVED后形成无界存储。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/PlanningPlugin.java:167

## OCR-0498 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/PlanningPlugin.java:106；OCR原严重度：medium
- 计划：R2-04 / P2
- 原意见：revision 入参上限 10000 会被 `JsonSchemaValidator` 强制（maximum），而 `execute` 每次成功更新都把 revision+1 且无封顶回落（第 174、200 行）。同一 todo/plan 累计成功更新 10001 次后，持久化的 revision 变为 10001，之后任何 `todo_update`/`plan_transition` 传入的 revision 都无法同时满足 schema 的 maximum=10000 与 `revision()` 的等值校验，从而永久锁死该 todo/plan（plan 无法另起一个，因为 plan 是单实例）。触发条件：对同一 id 连续成功更新超过 10000 次（自动化循环/长会话即可达到）。建议在写入时对上界做明确处理（超限时报错并允许重置，或提高上限并在取模/回绕时保持单调性）。
- 判断：revision schema最大10000而成功写+1能达10001，此后不能匹配输入；需明确终止/新修订策略并验证边界，不回绕复用旧审批。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/PlanningPlugin.java:106

## OCR-0499 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/PlanningPlugin.java:32；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：`stop()`（第 43-45 行）既不获取 `this` 锁也不等待在途调用：`execute` 的 synchronized 块只在入口检查 `active`，已进入块内的调用会在 stop 之后继续 `save()`（append+flush）；若宿主随后为另一个会话重新 `start()`（`scope/reader/writer` 不是 final/volatile，且 `start()` 同样未同步），在途调用可能用新的 scope 与 writer 落盘，把事件写进另一个会话。触发条件：工具调用执行期间插件被停止/重启。建议 `start()`/`stop()` 同样 `synchronized (this)`，并在 `save()` 内再次校验 `active` 与 scope，stop 时等待在途调用退出后再清理资源。
- 判断：execute锁内只检查一次active而stop无锁，停止后在途可能继续save；需明确在途撤销与关闭等待。跨会话重启同插件不是已支持路径，不据此认定现有串会话。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/PlanningPlugin.java:32

## OCR-0500 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/PlanningPlugin.java:38；OCR原严重度：low
- 计划：R5-02 / P2
- 原意见：`todos()` 与 `plan()` 每次调用都全量重放会话事件日志，而 `evidence()` 又对每个 evidence id 各做一次全量 `stream().noneMatch(...)` 扫描（最多 32 次）。由于 `todo_update`/`plan_transition` 每次都会调用这些方法，长会话下单个工具调用的开销随事件总数线性增长（证据校验最坏为 32×N）。建议一次性遍历并把 `eventId → tool.completed 概要` 建成索引复用，或按事件类型缓存投影。
- 判断：todos/plan重放全日志，evidence每ID再扫描列表；一次投影/索引并保持唯一写者与恢复可重建，归并长会话读取计划。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/PlanningPlugin.java:38

## OCR-0501 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkOptions.java:46；OCR原严重度：medium
- 计划：无
- 原意见：tools 为 null 时既不校验也不归一化，而 null 与空集在下游含义完全不同：HarnessSdk 第 128-131 行把 null 解释为“不施加 allowlist”，直接使用 ScopedToolRegistry（开放全部工具），空集则会构造 FilteredToolRegistry 导致全部拒绝。SdkOptions.root()（第 50 行）与 SdkConfiguration.tools 为 null 时都会走到这条“放开工具白名单”的路径，一旦配置缺失或上游拼装时漏传，就会静默地按最小限制之外的方式运行，属于失败不闭合的风险；同时 Set.copyOf 遇到含 null 元素的集合会抛 NPE。建议明确并收口 null 语义（例如归一化为空集或引入显式的“无限制”标记），并在注释中写明区分，避免调用方误用 null 获得全量工具权限。
- 判断：SdkOptions.root明确null=不过滤，与484一致是兼容默认值，空集合=无工具有意区分，不能将null改空集当安全修复。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkOptions.java:46

## OCR-0502 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkOptions.java:42；OCR原严重度：medium
- 计划：无
- 原意见：紧凑构造器只校验了 scheduling，未校验 resilience（第 13-14 行声明的记录组件）。虽然当前唯一走规范构造器的 HarnessSdk 第 98-106 行传入的 configuration.resilience() 已被 SdkConfiguration 第 33 行 requireNonNull 保护，但本文件 6 参/5 参便捷构造器都显式补了 ModelResilienceOptions.disabled() 默认值，说明该组件约定非空；一旦有调用方传 null，resilience 会流入 HarnessSdk 第 127、306 行并最终进入 ModelResiliencePlugin，触发 NPE 或出现“空值=未禁用/已禁用”不一致的行为。建议在紧凑构造器中与非空默认值保持一致地校验（或显式归一化为 disabled()）。
- 判断：SdkOptions包内且唯一完整调用已被SdkConfiguration.requireNonNull保护，便捷构造提供disabled；无可达null路径，不单列假想公共输入缺陷。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkOptions.java:42

## OCR-0503 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkOptions.java:44；OCR原严重度：low
- 计划：无
- 原意见：origin/parameters 未做 null 处理就交给 Values.snapshot，而 Values.snapshot 第 16 行是无消息的 Objects.requireNonNull(value)，因此任一路径传入 null 时会在构造期抛出没有字段名的 NPE（HarnessSdk 第 181 行 options.origin().isEmpty()、第 125 行 parameters 也依赖二者非空）。当前调用方虽都传 Map.of()/已规范化参数，但与本类对 tools 允许 null 的处理不对称，契约脆弱。建议显式归一化或带字段名校验。
- 判断：Values.snapshot已在构造期拒绝非法null，改变异常消息不构成功能修复。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkOptions.java:44

## OCR-0504 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:27；OCR原严重度：critical
- 计划：R2-01 / P2
- 原意见：路径规范化缺失，`safe()` 只做反斜杠替换并逐段精确匹配，存在绕过与键别名风险（第 26-38 行）： - `./.git/config`、`.git./config`、`.git /config`（Windows 会裁剪尾随点/空格）、`.GIT` 的 8.3 短名等均可绕过 `.git`/`.jharness` 拒绝规则后落到受保护目录； - `a//b`、`a/./b`、`a/b` 指向同一文件却产生 3 个不同的 `observed` 键，使 expectedVersion 守卫与实际文件版本错配（见第 64/128/147 行），并可被用来让守卫误判为“无版本”； - 空路径、`./`、空段未拒绝，会原样转发给 worker。 建议在分段校验前先规范化并拒绝空段、`.` 段及尾随点/空格段（大小写不敏感的 worker 还需对键做统一大小写折叠或直接拒绝混合大小写别名）。
- 判断：safe逐段已拒绝.git，./.git/config并不能绕过；真实a/./b与a/b缓存键别名成立。Windows尾点等最终由LocalWorkspace边界拒绝，不能按宿主Windows推导Linux容器保护失效。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:27

## OCR-0505 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:64；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：`ConcurrentHashMap` 不允许 null 值：当 worker 的 read 响应缺少 `version` 字段（或返回 JSON null）时，`(String) value.get("version")` 为 null，此行直接抛 NullPointerException 而非有界的协议错误。第 131、150 行同样问题——`change(value).afterVersion()` 为 null（如写入无变更、删除语义）时 `observed.put` 会 NPE。建议改成 `instanceof` 守卫/显式判空后再写入，并对缺失版本报 `PROTOCOL_ERROR`。
- 判断：异常worker缺version会在ConcurrentHashMap.put NPE；接受返回schema验证与固定协议错误。内建workerchange正常返回非null版本，未证明正常无变更会null。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:64

## OCR-0506 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:157；OCR原严重度：high
- 计划：R2-01 / P2
- 原意见：`applyPatch` 只把 `observed` 快照合并进请求，却从未用 patch 结果回写 `observed`。触发条件：先 patch 修改若干文件，随后对同一路径调用 `write/edit` 且 `expected == null`（第 128/147 行会回退到缓存），此时发送的是 patch 前的旧版本，worker 严格校验时必然报版本冲突，若 worker 不校验则可能用旧基线覆盖 patch 结果。建议解析 patch 响应中每个文件的 `afterVersion` 并更新 `observed`（同时清理/更新受影响路径的别名键）。
- 判断：applyPatch仅发送observed合并值不回写结果版本，后续隐式guard沿用旧版本造成伪冲突；解析变更并更新/失效相应缓存。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:157

## OCR-0507 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:87；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：并发下版本缓存会倒退：`readPage`/`readText`（第 64 行）在远端返回后无条件 `put`，若期间另一线程的 `write/edit` 已把更新的 `afterVersion` 写入同一键，较旧的读版本会覆盖新版本；同样 `write/edit` 在远端调用后直接 `put(afterVersion)`，两个并发写完成后最终缓存值可能是先完成者的版本。结果是后续 `expected == null` 的写操作使用过期版本，出现伪冲突或在 worker 未强制校验时静默覆盖。建议仅在“当前缓存值仍是本次请求读取到的期望版本”时更新（compare-and-set，如先记录读前版本再用 `replace(key, before, after)`），或改为不缓存、每次由 `versionOf` 显式获取。
- 判断：并行read结果后put可覆盖较新缓存，造成伪冲突；worker仍版本校验，静默覆盖推测不成立。需缓存CAS/串行化或显式版本调用方案。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:87

## OCR-0508 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:48；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：协议校验过弱： - 仅当 `response.get("error")` 是 String 才视为失败，若 worker 返回 `{"error": {...}}`、`{"error": 500}` 或 `error: null` 会被当成成功继续走 `Values.object`，最终抛 `INVALID_JSON` 或把错误响应当数据； - `response` 为 null 时此处 NPE； - 响应缺少 `value`（或 value 为 null）时 `Values.object` 抛 `INVALID_JSON`，掩盖真实失败原因并可能把错误信息带进上层。 建议：判空 `response`；用 `containsKey("error")` 判定失败并把非 String 错误映射为固定 `PROTOCOL_ERROR`（避免把任意内容拼进错误信息导致敏感信息外泄）；确认存在 `value` 键后再解析。
- 判断：RemoteWorkspace响应只以String error判失败，缺value/畸形error转为通用异常；对整个envelope严格验证并保留不确定副作用语义。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:48

## OCR-0509 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:169；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`change()` 对 worker 返回字段全部强制转型：`path` 缺失或非 String 时得到 null 传给 `ExecPath`，`beforeVersion/afterVersion/diff` 非 String 时 ClassCastException，属于可被恶意/异常 worker 触发的未捕获异常而非有界错误。此外返回的 `path` 未经 `safe()` 校验，worker 可回传绝对路径、`..`、`.git/...` 等值，污染调用方持有的 `ExecPath`（后续再进入 `call()` 才会以 `PATH_DENIED` 失败）。建议先校验 `path` 为 String 非空并通过 `safe()` 归一化，其余字段用 `instanceof` 校验。
- 判断：change裸转worker字段且未对返回path做safe校验；应校验路径与请求相符、版本/diff类型及限额，再建FileChange。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:169

## OCR-0510 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:128；OCR原严重度：medium
- 计划：无
- 原意见：`expected == null` 时回退到 `observed.get(safe(path))`，若该路径从未被读取过（例如新建文件、或 `isolatedView()` 新建的实例没有任何缓存，见第 15 行），这里会向 worker 发送 `expectedVersion: null`。若 worker 把 null 解释为“无守卫”，`overwrite=true` 的写入就会无条件覆盖并发修改者的内容，乐观并发保护形同虚设；`edit`（第 147 行）同样如此。建议：无已知版本且 `overwrite` 为真时要么显式拒绝并要求调用方用 `VersionGuard`/`versionOf()` 提供版本，要么发送明确的“必须不存在”标记，而不是静默发送 null。
- 判断：worker调用LocalWorkspace.write/edit，现有工作区对已存在文件缺观察/期望版本拒绝；意见假设worker忽略guard不成立，新建文件无旧版本是正常语义。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:128

## OCR-0511 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:19；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：`observed` 是无界缓存：每次 read/readPage/write/edit 都会新增键，且因 `safe()` 不做规范化，同一文件的多种写法会各自产生条目；长生命周期会话（尤其 `isolatedView()` 之外共享的实例）遍历/搜索大量临时文件后内存持续增长且永不释放。建议按会话作用域化或设置容量上限（如按访问顺序淘汰的 LRU，例如 4096 条），并在规范化键后再写入。
- 判断：observed无数量限制且路径别名会新增键；设规范键和有界策略，淘汰后不得退化为无guard覆盖。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:19

## OCR-0512 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:158；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：`versions` 为 null 时 `putAll` 抛 NullPointerException（`Map.of("patch", patch, ...)` 在 `patch` 为 null 时也会 NPE），变成未分类的运行时异常而非有界错误。另外把整个 `observed` 作为 versions 下发会泄露与 patch 无关路径的版本信息，并可能让 worker 因未知路径而拒绝整个 patch。建议显式判空、只下发 patch 涉及的路径版本。
- 判断：patch合并整个observed造成多余版本负载，接受只传受影响规范路径并保留限制；null为非法宿主参数，版本元数据在同环境内传输并非已证泄密。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:158

## OCR-0513 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:86；OCR原严重度：low
- 计划：无
- 原意见：`readPage` 未做边界校验：`start < 0`、`end < start` 以及 `end - start` 过大（未设上限）都会原样转发给 worker；同样 `list/search/grep` 的 `depth/limit` 也未校验。若 worker 信任这些参数，负偏移可能被解释为“从末尾偏移”，超大区间会一次性返回无界数据（有界数据处理失效）。建议在宿主侧校验 `start >= 0 && end >= start`，并把 `end - start` 与 `depth/limit` 收敛到协议约定的上限后再下发。
- 判断：内建worker委托LocalWorkspace已有readPage/list/search/grep范围限制，不能用假设worker完全信任输入认定无界返回；协议入口校验另归440。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:86

## OCR-0514 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:112；OCR原严重度：low
- 计划：无
- 原意见：`text`/`paths` 为 null 时此处直接 NPE（`Map.of` 也不接受 null 值），`paths` 中含 null 元素时 `RemoteWorkspace::safe` 也会 NPE；`search`（第 99 行）的 `glob` 为 null 同理。这些都属于调用方参数错误却以未分类运行时异常抛出，建议在入口处判空（或用 `Objects.requireNonNull` 给出明确消息）。
- 判断：非法null在Map.of/stream构造边界拒绝，当前调用者经工具schema校验；仅提升错误消息不独立立项。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/RemoteWorkspace.java:112

## OCR-0515 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ReadOnlyChildEnvironment.java:152；OCR原严重度：medium
- 计划：R2-02 / P1
- 原意见：descriptor() 原样内嵌 parent.descriptor()。该描述符会被 HarnessSdk 用于构造并持久化 sdk.created 的 binding（HarnessSdk.java:140-166），因此父环境的 id、镜像、searchFilter 等配置会写入子会话事件，造成不必要的信息暴露；更重要的是 DockerExecutionEnvironment.id() 返回 "jharness-" + UUID 的随机值（DockerExecutionEnvironment.java:12,145-147），父 id 内嵌会让子环境的 binding 不可复现，一旦用同一子会话重建 SDK 就会触发 ENVIRONMENT_MISMATCH。建议只暴露稳定且非敏感的父字段（如 kind/isolation），不要整段嵌入父描述符。
- 判断：嵌套parent随机id与474重复，需稳定投影；宿主选定image/filter写入子会话不是未经授权秘密外泄。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ReadOnlyChildEnvironment.java:152

## OCR-0516 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ReadOnlyChildEnvironment.java:87；OCR原严重度：medium
- 计划：无
- 原意见：产生式缺陷：writeText 直接抛 PERMISSION_DENIED，未先调用 check()（write/edit/applyPatch 同样）。可复现路径：child.close() 或 cancelled=true 后调用 retained.files().writeText(...)，得到 PERMISSION_DENIED 而不是 CANCELLED；而同类的 readText/versionOf/readPage/list/search/grep/gitDiff 都先 check()。这会让错误码语义不一致：DefaultToolPipeline.failure() 把 PERMISSION_DENIED 映射为 DENIED、CANCELLED 映射为 CANCELLED（harness-tools/.../DefaultToolPipeline.java:226-229），SubagentPlugin 也以 CANCELLED 判定子任务状态（SubagentPlugin.java:314），取消中的子任务因此被记为拒绝/失败而非取消。建议所有 mutating 方法先 check() 再 denied()。
- 判断：只读子环境所有变更始终PERMISSION_DENIED，取消后仍拒绝是安全且合理优先级；不存在写入放行，Subagent读取错误按父取消另结算，不因错误码偏好立项。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ReadOnlyChildEnvironment.java:87

## OCR-0517 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ReadOnlyChildEnvironment.java:135；OCR原严重度：medium
- 计划：R3-01 / P3
- 原意见：processes() 的 lambda 只实现了 execute(List, ExecPath, CancellationToken)，没有覆盖 ProcessRunner.execute(CommandRequest, CancellationToken)。ProcessToolsPlugin.CommandTool 实际调用的是 CommandRequest 重载（harness-process/.../ProcessToolsPlugin.java:122），会落到接口默认实现抛出 UNSUPPORTED（ProcessRunner.java:6-10），而不是本类承诺的 PERMISSION_DENIED；该路径也完全绕过 check()，取消/关闭后仍返回 UNSUPPORTED。建议改用匿名类同时覆盖两个重载，并在每个入口先 check()。
- 判断：子环境CommandRequest重载落入接口UNSUPPORTED而非PERMISSION_DENIED，接受明确一致的只读能力错误；不会执行命令，非越权漏洞。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ReadOnlyChildEnvironment.java:135

## OCR-0518 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/ReadOnlyChildEnvironment.java:50；OCR原严重度：low
- 计划：R1-02 / P1
- 原意见：check() 只在入口采样 cancelled/closed，随后把调用方 token 原样传给 base；若调用方传入 CancellationToken.NONE 或不携带本子环境状态的 token，子环境在长耗时的 readPage/list/search/grep 期间被 cancel()/close() 也不会中止底层操作，与“取消或关闭后旧视图不再继续读取”的约定存在缺口（M7-contracts.md:75）。建议转发组合 token：() -> token.isCancelled() || closed.get() || cancelled.getAsBoolean()，list/search/grep 同样处理。
- 判断：child入口仅采样关闭/取消，长读取转发原token，需组合子关闭/父取消/调用token并保留有界退出。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/ReadOnlyChildEnvironment.java:50

## OCR-0519 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpPlugin.java:462；OCR原严重度：high
- 计划：R3-03 / P1
- 原意见：第458-476行的兜底 catch 把所有非 MCP_RPC_ERROR 的 RuntimeException 一律判为 UNKNOWN，并永久设置 failed=true、关闭 transport，这与实际语义不符，且有明确可复现的误判路径： 1) 取消：McpIo.run 第23行 token.throwIfCancelled() 在请求尚未发出时就抛出 MCP_OUTCOME_UNKNOWN，此时结果完全确定（未执行），却仍返回 UNKNOWN 并杀掉整条连接； 2) 已收到完整响应后的本地校验失败：第421/428行 MCP_INVALID_RESULT；第430-434行 outputSchema 校验——当服务器声明了 outputSchema 但结果里没有 structuredContent 时，validate({"type":"object"}, null) 必然抛错；第455行 BoundedJson.text(result) 也可能抛 PAYLOAD_LIMIT。这些都属于调用已完成、响应已收到的情形； 3) 后果被放大：DefaultAgentLoop 第286-289行遇到 UNKNOWN 会立即以 UNKNOWN_SIDE_EFFECT 中止整轮对话并要求人工核验；同时由于 failed=true，本会话内该 MCP 的所有工具（包括 READ_ONLY 的 read_resource/列表工具）此后只会返回 SCOPE_DENIED，服务器无法恢复。 建议：只把传输层确实无法判定的失败（MCP_OUTCOME_UNKNOWN / MCP_TRANSPORT_FAILED / MCP_DISCONNECTED）以及非 READ_ONLY 工具升级为 UNKNOWN；协议/结构/schema/序列化等本地校验失败与 READ_ONLY 工具一律返回 FAILED（或沿用 pipeline 的 CANCELLED），且不要因此关闭共享 transport。
- 判断：本地校验与真实发送不确定混合导致过度UNKNOWN，需按发送阶段/副作用/完整响应分类；outputSchema失败不证明服务端未执行，禁止一律FAILED并保持连接重用。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpPlugin.java:462

## OCR-0520 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpPlugin.java:461；OCR原严重度：high
- 计划：R3-03 / P1
- 原意见：第459-461行把 MCP_RPC_ERROR 无条件映射为确定的 FAILED。触发条件：服务器对 tools/call 返回 JSON-RPC error（例如 -32603 internal error），但这并不能证明服务端没有执行副作用（可能执行后再返回错误）。返回 FAILED 后 DefaultAgentLoop 不会中止轮次，模型可再次调用该工具，从而重放一个副作用状态未知的操作，违反“UNKNOWN 不得被重放”。本类默认副作用级别是 SYSTEM_IMPACT（第402行），而 DefaultToolPipeline 第231行对“已进入方法体且非 READ_ONLY”的异常是升级为 UNKNOWN 的，这里的行为与之相反。建议：当 sideEffectLevel() != READ_ONLY 时按 UNKNOWN（sideEffectUncertain=true）返回，避免下游重试。
- 判断：非只读tools/call的RPC error不能普遍证明副作用未发生；需定义已拒绝/执行失败/未知类别并用执行后报错回归。对明确受理前错误可保留确定失败。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpPlugin.java:461

## OCR-0521 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpPlugin.java:83；OCR原严重度：medium
- 计划：R3-03 / P2
- 原意见：第83行 `String name = (String) tool.get("name");` 和第99行 `cursor = (String) result.get("nextCursor");` 对服务器完全可控的字段做了无校验强转。触发条件：tools/list 返回 `"name": 123` 或 `"nextCursor": 0` / 对象时，会在第83/99/101行抛 ClassCastException，绕过本类统一的有界错误路径（MCP_INVALID_TOOLS / MCP_DISCOVERY_LIMIT），start 以裸 RuntimeException 失败，容器只能记录一条无上下文的启动失败；这与 discoverResources 第146-163行的 instanceof 校验风格也不一致。建议改为 `!(tool.get("name") instanceof String name)` 与 `!(result.get("nextCursor") instanceof String value)` 后再判断长度/集合。
- 判断：tools/list name/nextCursor裸转可被对端畸形字段触发CCE；归并MCP发现schema和有界诊断。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpPlugin.java:83

## OCR-0522 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpPlugin.java:415；OCR原严重度：medium
- 计划：R5-01 / P1
- 原意见：tools/call 的返回缺少应用层大小上限：这里只限制 content 条数（128），对 result 整体、单个 text 块、structuredContent 均无长度检查；而 ResourceTool 对 resources/read 的结果有 256KB 上限（MCP_RESOURCE_LIMIT）。目前仅靠传输实现兜底（HttpMcpTransport 第109/117-119行与 McpIo.line 按 BoundedJson.LIMIT 截断为 4MB），但 McpTransport 是宿主可替换的公开扩展点，其“实现方必须有界”只是接口约定；本插件自身不应依赖该约定。触发条件：服务端返回接近 4MB 的 content（含 image/audio/resource 的 base64 blob），第445-455行会把同一份内容同时放进 value 和 text 两处（约 2 倍常驻内存），并整体注入模型上下文。建议在构造 ToolResult 前对 result 施加与资源一致的字节上限，超限时返回确定的失败而不是继续放行。
- 判断：content仅限条数，结构化内容/文本总量缺插件层限额；接受应用结果上限与上下文界限。已执行后超限不能一律确定FAILED允许重放。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpPlugin.java:415

## OCR-0523 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpPlugin.java:435；OCR原严重度：low
- 计划：R3-03 / P2
- 原意见：RemoteTool 返回的 source 结构化元数据缺少 ResourceTool 中使用的 `"untrusted", true` 标记（对照第288-295行），下游若按结构化字段做污点/展示分级，只能看到 type=mcp，无法识别这是不受信任内容；目前仅靠第452行的文本前缀提示。建议保持两个适配器的 source 字段一致，避免后续按结构字段消费时出现安全分级缺口。
- 判断：RemoteTool source缺untrusted而ResourceTool有；接受结构元数据一致和渲染约定，目前文本前缀已标不可信，非已证提权。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/McpPlugin.java:435

## OCR-0524 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentService.java:7；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：第 7 行：`tasks()` 的契约没有分页/上限/过滤参数，也没有 Javadoc 声明结果规模上界。触发条件：SDK 宿主（`context.services().requireUnique(SubagentService.class, ...)`）在任务保留量较大或第三方实现直接返回全量集合时，会把全部任务元数据一次性物化到内存并跨进程返回，违背有界数据处理要求；由于接口无 `limit/offset` 或候选过滤，调用方也无法自行收敛。建议在签名中显式引入分页/上限（如 `tasks(int limit)` 或 `tasks(TaskQuery)`），或至少用 Javadoc 固定最大返回条数与截断语义。
- 判断：tasks接口上限契约未写清，但内建SubagentPlugin最多32总任务，不存在内建无界集合；补上限及截断/未知语义，是否分页不强制破接口。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentService.java:7

## OCR-0525 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentService.java:9；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：第 9 行：`status(String)` 未约定 taskId 为 null/空串/未知/已越界时的行为，也未约定返回值中 `state` 的取值域（例如 `UNKNOWN` 与终态如何区分）。触发条件：调用方把未校验的用户输入或已回收的 taskId 传入，实现侧若以 `Map.get(null)` 处理，在 `ConcurrentHashMap` 上会直接抛 NPE，在 `HashMap` 上则退化为“未知任务”；同时调用方拿到无 schema 的自由 `Map` 后无法判断 `UNKNOWN` 是否可重放，可能把不确定状态当作可继续/可重放状态上报。建议在接口上固定非空校验、未知 id 的失败方式，并用类型化的状态枚举/记录替代自由 Map。
- 判断：status自由Map缺状态/id错误契约，接受公共接口说明/返回验证；不能据第三方任意返回推导内建UNKNOWN重放。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentService.java:9

## OCR-0526 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentService.java:11；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：第 11 行：`cancel` 返回 `void`，且没有结果/状态参数，无法区分“未知任务”“已处于终态”“已被拒绝（越权或宿主已关闭）”三种结果；同时未声明是同步终止还是仅投递取消请求。触发条件：父作用域在收到 `cancel` 正常返回后立即释放子任务占用的槽位、临时文件或 Artifact 引用，而子任务仍在运行（`cancelRequested` 已置位但未 finished），造成资源生命周期竞态与状态误报；另外仅凭 taskId 即可发起取消，缺少父作用域/主体校验点，接口层无法保证不会取消其他父作用域的任务。建议返回取消结果对象（含 `accepted/state/reason`），并在契约中明确作用域绑定与“返回不等于已终止”的语义。
- 判断：cancel应明确只投递取消、不证明终止及绑定父作用域；内建Service.check已作用域隔离，不存在仅taskId即可取消别父任务的实证。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentService.java:11

## OCR-0527 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SharedFeeBudget.java:17；OCR原严重度：medium
- 计划：无
- 原意见：切换轮次时无任何前置校验就清零 `reserved` 并自增 `epoch`，但没有确认各 guard 是否仍有未结算的预留（`pending`）。触发条件：某轮在 `maximumCost` 预留成功后异常/取消退出（例如 DefaultAgentLoop 在 maximumCost 之后、actualCost/requestRejected 之前的 throwIfCancelled、REQUEST_REPLAY_MISMATCH、checkpoint 失败，或模型调用抛非 429 异常），此时 `pending` 仍挂起且 `reserved` 已被抬高；下一次 `run()` 进入 `beginTurn()` 直接把 `reserved` 归零，这笔已预留（很可能已被供应商计费）的金额既不再计入共享上限，也无人释放，等同于放宽本轮的保守上限。若该请求的结算在 `beginTurn()` 之后才回到 `actualCost`，还会被判定为 `COST_ACCOUNTING_BREACH`（而不是可识别的过期结算）。建议在重置前检查是否仍有未结算预留（例如维护未结算计数/引用），存在时拒绝开启新轮或先按上界结算，而不是静默丢弃。
- 判断：预算是每父轮额度，run先SessionRecovery.ready门禁，未知模型请求阻止下一轮；已核实结束后新轮重置符合契约，不是跨轮总费用绕过。非法goal提前重置另以480处理。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SharedFeeBudget.java:17

## OCR-0528 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SharedFeeBudget.java:53；OCR原严重度：high
- 计划：无
- 原意见：`requestRejected` 在锁内直接调用 `delegate.requestRejected(request)`，只有成功返回后才把 `pending` 置空，缺少 try/finally。触发条件：delegate（宿主可插拔的 ApiCostGuard，例如自带限额校验的实现）在 429 拒绝回调中抛异常时，`pending` 不会被清空且 `reserved` 保持被抬高状态，DefaultAgentLoop 的 429 重试再次调用 `maximumCost` 就会一直得到 `COST_RESERVATION_PENDING`，导致本轮后续所有模型调用失败（槽位只能等下个轮次 `beginTurn` 才恢复）。另外把外部 delegate 调用放在 `SharedFeeBudget` 锁内也会把宿主代码的执行时长/锁顺序风险引入共享锁。建议改为 try/finally 中清空 `pending`（`reserved` 仍按注释保留上界），并尽量让 delegate 调用移出共享锁。
- 判断：delegate.requestRejected抛异常时Loop整次尝试失败且保留保守pending，不能finally清空假装正常继续；锁外回调风险可在成本契约重构考量，意见的自动无限重试前提不成立。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SharedFeeBudget.java:53

## OCR-0529 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SharedFeeBudget.java:65；OCR原严重度：high
- 计划：R2-02 / P2
- 原意见：先调用 `delegate.actualCost(...)` 再校验预留状态，顺序有误：当 `pending` 为空或 `pendingEpoch != epoch`（例如 `beginTurn` 已切换轮次）时，delegate 已经完成了实际计费计算/记账，随后本方法才抛 `COST_ACCOUNTING_BREACH`，`reserved` 既不会扣掉预留也不会累加真实费用，真实花费在共享上限中永久丢失，可被用来突破 `maxApiCost`；同时真实合法的结算被误报成“记账越界”。另外 `actual.compareTo(pending) > 0` 分支抛出后同样不会清空 `pending`，本轮该 guard 的槽位被永久占用。建议先在同一把锁内校验并取出（或作废）预留额度，再调用 delegate，并在所有终止路径上释放 `pending`、把实际费用写回 `reserved`（超出预留时也应按实际上界计入而不是直接丢弃）。
- 判断：可替换delegate在校验pending前调用确会产生副作用顺序问题；接口应规定纯计算/校验顺序并保留费用未知。不得无条件清pending或以新轮绕过未结算事实。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SharedFeeBudget.java:65

## OCR-0530 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/StdioMcpTransport.java:23；OCR原严重度：medium
- 计划：无
- 原意见：命令长度校验可被绕过且不防 null： 1) `mapToInt(String::length).sum()` 是 int 累加，256 个超长参数（每个接近 2^31 字符）相加会溢出为负数或小正数，`> 32768` 判断失效，超长命令会进入 `ProcessBuilder`，造成巨量内存占用/参数注入面扩大； 2) `command` 本身为 null 时 `command.isEmpty()` 直接 NPE，元素为 null 时 `String::length` NPE，都不是预期的 `IllegalArgumentException`。 建议改用 long 累加并显式判空。
- 判断：需先分配数GB参数才能触发int溢出，命令来自可信宿主且OS另拒绝；没有可达实际边界绕过，null也在构造拒绝，不列安全修复。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/StdioMcpTransport.java:23

## OCR-0531 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/StdioMcpTransport.java:30；OCR原严重度：medium
- 计划：R1-02 / P2
- 原意见：构造失败路径会泄漏子进程：`builder.start()` 成功后，只要后续 `new BufferedInputStream(...)`（内存不足等）、`process.getOutputStream()` 或 `Thread.ofVirtual().start(...)`（OOM / 线程创建被拒）抛出，构造函数即以异常退出，但已启动的子进程没有任何引用，不会被 destroyForcibly，成为孤儿常驻进程（且此时 stdin/stdout 管道无人读取，可能进一步阻塞）。请把 start 之后的初始化包在 try/catch 中，失败时先 `process.destroyForcibly()` 再重抛。
- 判断：进程启动后构造初始化失败缺异常清理，接受启动资源接管与失败补偿；仅OOM路径不能断言常态泄漏，不能捕获Error后继续运行。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/StdioMcpTransport.java:30

## OCR-0532 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/StdioMcpTransport.java:73；OCR原严重度：medium
- 计划：R3-03 / P2
- 原意见：对服务端下发的请求回 error 时未防 null id：这里只判断 `containsKey("id")`，若不可信服务端发送 `{"jsonrpc":"2.0","id":null,"method":"..."}`（Jackson 反序列化会保留显式 null 值），`McpIo.rejection()` 内部用 `Map.of(..., "id", request.get("id"), ...)` 构造，`Map.of` 拒绝 null 值而抛 NPE，最终被包装成 MCP_TRANSPORT_FAILED 并关闭连接，属于可被对端触发的协议级崩溃。应按值判空（id 为 null 的报文按通知处理，不回包）。
- 判断：null id问题与447/487重复；但id=null不等于通知，禁止按建议直接无应答，需要明确合法null或结构化协议错误。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/StdioMcpTransport.java:73

## OCR-0533 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/StdioMcpTransport.java:94；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：清理顺序与截断都会遗留子孙进程： 1) 先枚举/杀子孙、父进程仍存活，`descendants()` 是惰性快照，父进程在遍历期间新 fork 的进程不会被覆盖，结束后这些新进程仍存活；应先 `process.destroyForcibly()` 阻断再创建，再遍历子孙（此时下方第 96 行的 `process.destroyForcibly()` 变为冗余）。 2) `.limit(64)` 静默丢弃第 65 个以后的子孙，超过 64 个子进程时其余全部泄漏；既然是尽力而为的清理，直接遍历全部子孙更安全。 3) 另外 `close()` 未调用 `waitFor()`，返回时进程可能尚未终止、管道资源尚未回收，调用方若依赖“close 后进程已死”并不成立。
- 判断：stdio清理只采样64子孙且未wait，接受有界收尾和partial残留证据；先杀父可能丢失后代枚举，遍历全部无界也不可采用，不承诺Job Object保证。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/StdioMcpTransport.java:94

## OCR-0536 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/WorkspaceContentInspection.java:92；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：强转 `(String)` 且只捕获 `HarnessException`：`Values.object(v)` 对非 Map 条目会抛 HarnessException（被过滤，符合预期），但只要提供方返回的条目缺少 `path` 键，`get("path")` 就返回 null，`pathAllowed(null)` 在 `path.length()` 处抛 NPE；若 `path` 是非 String（如数字/列表），强转直接抛 ClassCastException。两种情况都不会被 `catch (HarnessException)` 拦住，导致整个 `list()` 以未捕获运行时异常失败，而不是仅过滤该条目。`WorkspaceFiles.list()` 的条目结构在 API 接口中并未文档化，远程/第三方实现即可触发。建议先用 `instanceof String` 做类型收窄并对 null 返回 false。
- 判断：list过滤条目对path裸转并仅catch HarnessException，第三方缺path/非String能抛NPE/CCE；按schema过滤/报协议错误。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/WorkspaceContentInspection.java:92

## OCR-0537 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/WorkspaceContentInspection.java:61；OCR原严重度：low
- 计划：R3-01 / P2
- 原意见：`pathAllowed` 目前只是段名黑名单，作为 ContentInspection 的“文件系统边界”校验并不完整：不拒绝 `..` 段、绝对路径/盘符/UNC、ISO 控制字符，也不对 `.git.`、`.git ` 这类尾部点/空格做归一化（`split("/")` 还会产生空段），且 `path` 为 null 时先在 `path.length()` 处 NPE 而不是抛 PATH_DENIED。当前之所以安全，完全依赖下游 `LocalWorkspace.resolve`/`RemoteWorkspace.safe` 的兜底（本文件又被用于过滤 `list()` 的条目输出，见上面已脱敏后的路径）；一旦 `WorkspaceFiles` 换成校验较弱的实现，越界读取/元数据泄漏就会发生。建议在本层同样做归一化与拒绝，形成纵深防御，并把每轮循环都重新构造的 `Set.of(...)` 提升为静态常量。
- 判断：pathAllowed只是展示过滤非最终文件能力边界，内建LocalWorkspace/RemoteWorkspace已拒越界；接受列表路径类型和规范校验，不称当前任意文件读取。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/WorkspaceContentInspection.java:61

## OCR-0538 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/WebFetchPlugin.java:32；OCR原严重度：high
- 计划：无
- 原意见：构造函数只按 scheme 放行：任何 https 主机与端口都能通过（例如 https://127.0.0.1/、https://169.254.169.254/、解析到内网/link-local 的内部域名），HTTP 分支也只限制 host、不限制端口。webSources 直接来自项目配置（EcosystemConfig 里 URI.create 后原样传入），一旦工作区配置不可信或写错，模型即可通过 web_fetch 访问内网管理端口/云元数据端点（SSRF），且不跟随跳转并不能阻止这种直连。建议：显式限定允许的 host（含端口）白名单，并在发起请求前解析目标地址、拒绝 loopback/私有/link-local/保留地址（注意 DNS 重绑定），而不是只做 scheme 判断。
- 判断：webSources是可信宿主明确固定来源配置，允许内网/本地替身是现有范围；模型只选别名，不能变任意URL。新增禁私网会破坏已授权来源，不按不可信workspace假设立项。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/WebFetchPlugin.java:32

## OCR-0539 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/WebFetchPlugin.java:177；OCR原严重度：medium
- 计划：R1-04 / P1
- 原意见：返回给模型的 url 是 uri.toString()，而构造函数只拒绝 userInfo 和 fragment，没有拒绝 query。因此形如 https://api.example.com/data?token=... 的预签名 URL / API key 会原样进入工具结果（并落到会话记录/日志中），违反敏感信息脱敏要求。建议在构造函数中拒绝带 query 的源，或输出时只返回别名（或剥离 query 后的 host+path）。
- 判断：url直接输出含query，已知secret可被统一脱敏但未知预签名参数存在泄露面；输出来源别名/安全URL元数据并明确query秘密策略，不能一律禁止所有query破坏合法固定来源。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/WebFetchPlugin.java:177

## OCR-0540 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/WebFetchPlugin.java:153；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：thenAccept 只在回调执行那一刻观察到 closed/已取消才关闭 body，与 await 的失败路径存在竞态：若响应在回调判定之后才完成，或 pending 恰好在最后一次 get(20ms) 超时之后完成，await 会因取消/PLUGIN_CLOSED/WEB_FETCH_TIMEOUT 抛出，而 finally 里 pending.isDone() 为 true 不会 cancel，active 又从未 set，响应 body 流永远不会被关闭，泄漏连接与缓冲区。建议在 finally 中无条件回收已完成的响应：用 boolean handedOff 标记流是否已交给读取路径，对 pending.getNow(null) 拿到的 response.body() 在未移交时 close()，再决定是否 cancel。
- 判断：await取消/超时与pending完成之间有未移交response body窗口，finally只cancel未done future；需handedOff所有权和迟到body必关回归。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/WebFetchPlugin.java:153

## OCR-0541 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/WebFetchPlugin.java:186；OCR原严重度：medium
- 计划：R3-03 / P2
- 原意见：Content-Type 只用于类型白名单，charset 参数被丢弃，正文一律按 UTF-8 解码。当响应为 text/html; charset=Shift_JIS、text/plain; charset=ISO-8859-1 等时，content 会变成 U+FFFD 乱码（而 sha256 仍基于原始字节），模型拿到的是失真内容且无法察觉。建议解析 charset 参数并按声明解码，缺失或非法时回退 UTF-8（application/json 按 RFC 8259 固定 UTF-8）。
- 判断：文本固定UTF8与声明charset不一致时会损坏正文；应明确支持UTF8并拒绝其他charset或有界解码支持，不默认容错替换伪造文本事实。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/WebFetchPlugin.java:186

## OCR-0542 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/WebFetchPlugin.java:138；OCR原严重度：low
- 计划：无
- 原意见：source 参数在运行时未校验（inputSchema 仅用于提示词，仓库内没有 schema 校验）。模型漏传 source 时 arguments.get 返回 null，Map.copyOf 生成的不可变 Map 对 get(null) 会抛 NPE；传入不存在或非字符串别名时 uri 为 null，HttpRequest.newBuilder(null) 同样抛 NPE——两者都被下面的 catch (Exception) 折叠成 WEB_FETCH_FAILED，掩盖了真正的参数错误且不易排查。建议先显式校验 arguments 含字符串 source 且能在 sources 中命中，否则抛出 INVALID_ARGUMENT/INVALID_SOURCE。
- 判断：仓库DefaultToolPipeline实际调用JsonSchemaValidator，inputSchema不只是提示词；source枚举已在管线校验，意见“没有schema校验”与实现相反。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/WebFetchPlugin.java:138

## OCR-0543 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/LazyModelRouter.java:30；OCR原严重度：high
- 计划：R3-02 / P2
- 原意见：用户提供的 factory 在 `synchronized` + `computeIfAbsent` 内执行（第 29 行）： 1) 若 factory 需要读取凭据、做网络探测或加载配置，所有其他 route 的 `select()` 以及 `close()` 都会被阻塞；factory 永久阻塞时，关闭流程也无法推进，属于持锁执行外部代码。 2) 若 factory 重入本路由器（例如为另一个 route 调用 `select()`，或调用 `close()`），会在 `LinkedHashMap.computeIfAbsent` 的映射函数执行期间修改同一 map，JDK 会抛 `ConcurrentModificationException`（或导致缓存状态损坏），而不是返回正常的 `HarnessException`。 建议：先在锁内完成 `closed`/缓存命中判断与 route 合法性检查，释放锁后再调用 `factory.get()`，最后在锁内用 `putIfAbsent` 提交（若竞争失败则关闭自己创建的实例）；或在文档中显式禁止 factory 重入并改用可重入安全的缓存实现。
- 判断：factory在同步锁和computeIfAbsent执行，可重入修改map抛CME/阻塞关闭；明确factory契约并缩短锁范围，保证竞态失败实例关闭。
- 证据：harness-llm/src/main/java/io/jharness/llm/LazyModelRouter.java:30

## OCR-0544 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/LazyModelRouter.java:35；OCR原严重度：high
- 计划：R1-02 / P2
- 原意见：关闭失败会永久丢失 client 引用（第 35、44 行）：即使某个 `LlmClient.close()` 抛出 `RuntimeException`，`closed` 已被置为 true 且 `clients.clear()` 仍会执行，因此这些关闭失败的 client 再也不可能被重试关闭，其连接/线程/句柄会泄漏，路由器处于半关闭状态。另外第 40 行只捕获 `RuntimeException`，若某个 client 抛出 `Error`，后续 client 完全不会被关闭，同样泄漏。 建议：只在成功关闭后从缓存中移除该 client，失败者保留；并把提前返回条件改为“已关闭且缓存为空”，使再次调用 `close()` 能重试失败项；同时确保单个 client 的异常不影响其余 client 的关闭。
- 判断：router清空关闭失败client引用会失去再次核实资源状态，接受清理失败明细与残留管理；不能假设所有close失败都可安全重试或Error可吞。
- 证据：harness-llm/src/main/java/io/jharness/llm/LazyModelRouter.java:35

## OCR-0545 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/LazyModelRouter.java:27；OCR原严重度：medium
- 计划：R3-02 / P2
- 原意见：`select(null)` 不会进入第 27 行的 `factory == null` 分支，而是由不可变 Map（`Map.copyOf` 结果）的 `get(null)` 直接抛出 `NullPointerException`，绕过声明的 `MODEL_ROUTE_NOT_FOUND` 错误码。route 常来自配置（如 `modelRoute`）或外部输入，null/空串属于可预期输入，会让调用方的错误处理失效。建议在方法入口显式校验 `route` 为 null/空白并统一抛出 `HarnessException("MODEL_ROUTE_NOT_FOUND", ...)`。
- 判断：select(null)触发Map.copyOf.get NPE而非MODEL_ROUTE_NOT_FOUND，公共入口可统一参数拒绝；内建Web配置已预校验，不按外部可达服务崩溃定级。
- 证据：harness-llm/src/main/java/io/jharness/llm/LazyModelRouter.java:27

## OCR-0546 · 不采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/LazyModelRouter.java:14；OCR原严重度：low
- 计划：无
- 原意见：构造参数为 null 时，`factories.isEmpty()` 直接抛出不带上下文的 `NullPointerException`，与预期的 `IllegalArgumentException("Invalid routes")` 不一致；同时 `Map.copyOf` 对 null key/value 抛出的 NPE 也没有任何提示，配置错误时难以定位。建议先做 `Objects.requireNonNull(factories, "factories")` 或显式遍历校验 key/value 非空后再做数量校验。
- 判断：构造factories非法null/含null键值已快速拒绝，异常消息偏好不列功能修复。
- 证据：harness-llm/src/main/java/io/jharness/llm/LazyModelRouter.java:14

## OCR-0547 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SkillsPlugin.java:317；OCR原严重度：high
- 计划：R3-03 / P1
- 原意见：脚本快照的加载上限与 CommandRequest 的契约不一致，会把“根本没执行的命令”上报成副作用不确定。read()（第 96 行）允许脚本内容最大 65536 字节且允许空/纯空白内容，但 CommandRequest 会拒绝 argv 总字符数 > 32768（CommandRequest:28）或首元素 isBlank()（CommandRequest:29）并抛 IllegalArgumentException。由于 ScriptTool 的 sideEffectLevel 是 SYSTEM_IMPACT，异常发生在 DefaultToolPipeline 已置 entered=true 之后，code(ex)=TOOL_FAILED 会被强制改写成 ToolStatus.UNKNOWN（DefaultToolPipeline:231），DefaultAgentLoop 随即抛 UNKNOWN_SIDE_EFFECT 并要求人工核验、禁止自动重试（DefaultAgentLoop:286-289）。触发条件：宿主配置的脚本文件内容在 32769~65536 字符之间或为空白（如只有换行），start() 完全成功、审批也能通过，但会话在第一次调用时直接以 UNKNOWN_SIDE_EFFECT 终止。建议在加载阶段就按“≤32768 字符且非空白”校验脚本并在注册前以有界错误码失败，或在 execute() 构造 CommandRequest 之前显式校验并返回 ToolResults.failure(FAILED, ...)。
- 判断：脚本加载64KB而CommandRequest字符上限32768/非空，执行体内才拒导致UNKNOWN；加载注册前统一脚本可执行限额及空白校验。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SkillsPlugin.java:317

## OCR-0548 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SkillsPlugin.java:96；OCR原严重度：medium
- 计划：R3-03 / P2
- 原意见：有界数据处理顺序不当：readText() 已经先把整份文件解码成 String 并再复制一份 byte[]（第 91-95 行），之后才判断 64 KiB 上限；而 512 KiB 的总量上限要到 122-131 行、即 32 个资源 + 16 个脚本全部读入并长期保存在 resources/scripts 之后才检查。工作区单文件读取允许 1 MiB（本地）/4 MiB（远程），因此一个超限的 skill 目录在被拒绝前可产生数十 MB 的瞬时与常驻内存占用（远程环境下每文件还有多次往返，且 read() 还额外做两次 versionOf）。建议先用 versionOf + 大小/元数据判界再读取，或边读边累加总量、超限立即中止，而不是全部读完后才判定。
- 判断：单文件先全文读取后64KiB、总体全部加载后512KiB检查确有瞬时放大；边读累计/有界读取，metadata先检仍需读时限额防竞态。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SkillsPlugin.java:96

## OCR-0549 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SkillsPlugin.java:110；OCR原严重度：medium
- 计划：无
- 原意见：started 在任何加载/注册动作之前就被 CAS 置为 true，而失败路径没有任何回滚，导致插件不可恢复：一旦 read() 抛 FILE_CONFLICT/SKILL_IO（例如启动瞬间宿主同步修改了 SKILL.md 或资源文件）、或 registry.register 抛错，该实例之后任何 start() 都只会得到 PLUGIN_ALREADY_USED；同时 resources/scripts 中已写入的部分快照与 manifest 不会被清理（stop() 仅把 active 置为 false）。由于 Spec 构造器并不调用 validatePaths，路径/入口错误只能在 start() 内暴露，使这个“一次性”问题在运维修正配置后仍无法重试。建议只在全部读取与注册成功后再置位 started（或失败时清理 resources/scripts/manifest 并复位 started），并把路径校验前移到构造函数。
- 判断：SkillsPlugin明确一次性started门禁，PluginContainer失败后回滚且不支持同实例重启；修改配置重新建实例即可，不能复位导致旧引用复活。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SkillsPlugin.java:110

## OCR-0550 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SkillCatalog.java:166；OCR原严重度：high
- 计划：R3-03 / P2
- 原意见：当 frontmatter 的值恰好只有一个引号字符（例如文件内容为 `---\nname: "\n---\n` 或 `description: '`）时，`startsWith` 和 `endsWith` 会同时为真，`substring(1, 0)` 抛 `StringIndexOutOfBoundsException`，而不是协议约定的有界错误 `SKILL_FRONTMATTER_INVALID`。该异常不是 `IOException`/`HarnessException`，`scan` 调用方（如 EcosystemConfig 构建插件配置时）不会把它转换成 `SKILL_SCAN_FAILED`，因此根目录里一个畸形或被篡改的 SKILL.md 可直接让宿主配置加载崩溃（不可重试、无有界错误码）。建议改为仅当长度≥2 时才剥离引号：
- 判断：frontmatter单引号字符同时starts/ends使substring(1,0)抛越界；长度>=2与配对校验，畸形目录应给受控诊断。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SkillCatalog.java:166

## OCR-0551 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SkillCatalog.java:112；OCR原严重度：medium
- 计划：R3-03 / P1
- 原意见：`directory` 取自 `Files.list` 的文件名，未经路径分量校验就拼进 `ExecPath`。在 POSIX 上目录名可以合法包含反斜杠（如目录 `a\b`）：此前的 `verify(child)`、`manifest` 属性/大小检查针对的是 `root/a\b/SKILL.md`，而 `LocalWorkspace.resolve` 会把 `\` 归一化为 `/`，实际读取的是 `root/a/b/SKILL.md`。于是 (1) 若 `root/a/b/SKILL.md` 存在，会把从未被 `verify` 校验过的另一个目录内容作为该技能快照返回，`entry.directory`/`sha256`/名称与实际内容不一致（文件系统边界与快照完整性被混淆）；(2) 若不存在，则抛 `FILE_NOT_FOUND` 导致整个根目录扫描失败。含 `:` 或控制字符的目录名也会在 resolve 阶段触发 `PATH_DENIED`，使单个目录名就能让所有技能不可用。建议在 discovery 阶段先按路径分量规则过滤/拒绝这些名字：
- 判断：POSIX含反斜杠目录经LocalWorkspace转分隔后读取另一文件，前检属性与实际内容路径不一；发现时拒别名/控制字符并核对同对象。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SkillCatalog.java:112

## OCR-0552 · 不采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ModelProtocol.java:23；OCR原严重度：low
- 计划：无
- 原意见：Diagnostics gap in the fail-closed path (focus area 3): `fromId` matches IDs with exact, case-sensitive `equals`, but the thrown `INVALID_CONFIG` message carries no detail. Any small config variation (e.g. `protocol: Responses`, `protocol: "responses "`, or an env var with a trailing space) is rejected — which is correct fail-closed behavior — yet the operator gets only "Unsupported model protocol" with no indication of which value was wrong or which values are legal, making the misconfiguration effectively undebuggable. Suggest listing the accepted IDs (the enum `id`s are non-secret config identifiers, so echoing the input is acceptable, but listing the valid set is enough and avoids echoing arbitrary config text). Note `null` is already handled safely by `value.id.equals(id)` and also fails closed — no default fallback exists, which is the desired behavior.
- 判断：严格protocol ID拒绝是有意fail-closed，增加合法值提示可随配置诊断改进，不单列已运行缺陷。
- 证据：harness-llm/src/main/java/io/jharness/llm/ModelProtocol.java:23

## OCR-0559 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ObservedLlmClient.java:94；OCR原严重度：high
- 计划：R1-02 / P1
- 原意见：观测写入放在 finally 中，`SessionWriter.append` 的契约是“必须确认存储保证否则抛异常”。因此当模型调用已成功返回、或 delegate 已抛出原始异常时，若 append（或 `reader.events()`/`sanitizer.sanitize`）抛 RuntimeException，finally 中的异常会覆盖原始返回值/原始异常，导致调用方看到的是观测失败而非真实结果，错误归因错误且丢失原始堆栈。建议对观测步骤单独 try/catch：观测失败时不要替换主结果，最多把观测异常以 suppressed 形式附加到正在传播的异常上，或先构造好观测事件再在主路径之外安全落盘。
- 判断：Observed finally append/reader/sanitizer失败覆盖delegate原异常成立；需保存双重事实和首因。成功但观测落盘失败不能假装全部持久完成，禁止一律忽略。
- 证据：harness-llm/src/main/java/io/jharness/llm/ObservedLlmClient.java:94

## OCR-0560 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ObservedLlmClient.java:34；OCR原严重度：medium
- 计划：R3-02 / P1
- 原意见：`select()` 直接返回底层 router 选出的原始 client（以及 base 路由下的 `delegate`），返回的实例不再是 ObservedLlmClient。任何通过 `ModelRouter.select(...)`/RoutedLlmClient 选择路由后发起的请求都会绕过本类的 `model.observed` 元数据记录与脱敏，与类注释声明的“观测所有模型请求”不一致，且路由模型（非 base）下完全无观测事实。应对选中的 client 重新包装为新的 ObservedLlmClient（base 分支返回 `this`），保持 writer/reader/sanitizer/session 不变。
- 判断：select返回未观测delegate会绕过wrapper，接受装饰器在路由选择后保留；需防重复包装/重复observed并核对标准SDK路由调用链。
- 证据：harness-llm/src/main/java/io/jharness/llm/ObservedLlmClient.java:34

## OCR-0561 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ObservedLlmClient.java:83；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：每次模型调用结束都在 finally 中 `reader.events()` 读取整份事件日志，再用 `reduce((a, b) -> b)` 取最后一条 `model.requested`：1) 会话日志随轮次线性增长，整体退化为 O(n²) 的全量扫描与列表拷贝，长会话下观测本身会成为主要开销；2) 该归因没有与本次请求建立关联，只要日志在本次请求期间出现别的 `model.requested`（并发/嵌套请求，或 prepare 之后请求未真正写入日志），就会把别的请求的 eventId 记到本条 model.observed 上，破坏“sourceEvent 精确指向本次请求”的事实性。建议把 EventRequestJournal 生成的 contextEventId/request eventId 作为参数传入（prepare 阶段可获得），或至少按 step/turn 限定并做有界反向查询。
- 判断：每完成请求全读日志取最新requested，接受请求ID显式关联/增量读取；标准Loop单请求、子会话独立，未证明当前并发串归属，不据假想并发确认。
- 证据：harness-llm/src/main/java/io/jharness/llm/ObservedLlmClient.java:83

## OCR-0562 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ObservedLlmClient.java:100；OCR原严重度：medium
- 计划：R3-02 / P2
- 原意见：`stream()` 直接透传 `delegate.stream(...)`，完全绕过本类的观测逻辑。`LlmClient` 是公开接口且 `StreamHandle` 路径可被直接调用（不使用 complete 的封装），这些流式请求不会产生 `model.observed`（elapsedMs/outcome/usage/sourceEvent 全部缺失），与 M6 契约“每条模型请求保存 model.observed 元数据”不符。建议在 stream 路径外包一层：在拿到 StreamHandle 前记录开始时间，在 finalResponse 成功后按同样逻辑写入观测，并在失败/取消时记录对应 outcome。
- 判断：Observed.stream透传不记录观测，需声明低层stream所有权与支持语义或包装终态；标准Loop通过complete已有observed，不宣称当前全部流无记录。
- 证据：harness-llm/src/main/java/io/jharness/llm/ObservedLlmClient.java:100

## OCR-0567 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:100；OCR原严重度：critical
- 计划：R2-04 / P1
- 原意见：取消判定先于「已受理答案」判定，会丢弃已确认的答案（第 100-103 行，配套 126 行）。触发条件：respond() 在 t1 设置 wait.answer 并已向客户端返回 SUBMITTED，随后 stop()/取消在 t2>t1 发生；ask 线程在 t3>t2 醒来先判断 `!active || isCancelled()`，把 resolution 覆盖为 CANCELLED，并落盘不含 requestId 的 question.resolved（若 fingerprint 已设置还会写入无对应 requestId 的 answerHash）。结果是持久事实与客户端已确认的 SUBMITTED 矛盾：respond() 因 durable 行无 requestId、pending 已被 finally 移除而只返回 QUESTION_CLOSED，recorded()（第 236 行）同样返回 QUESTION_CLOSED，答案既不能幂等重放也不可恢复。应在同一个 synchronized 块内先判 `wait.answer != null`，再判取消/超时（取消分支也在锁内读取），保证已受理的答案优先于 CANCELLED 落盘。
- 判断：ask先取消再看已提交answer，SUBMITTED与取消竞态可丢requestId/幂等关联；需明确暂存受理与最终持久解析优先级，保留取消真实事实。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:100

## OCR-0568 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:288；OCR原严重度：high
- 计划：R2-04 / P1
- 原意见：应答成功早于持久化，存在「客户端已确认但答案丢失」的窗口（第 287-292 行与 126-135 行）。respond() 只设置 wait.answer/wait.fingerprint 并立即返回 SUBMITTED，真正的 question.resolved 由 ask 线程随后的 append+flush 落盘。触发条件：writer.append/flush 抛错（磁盘满、作用域关闭、TAIL_DAMAGED、进程在落盘前崩溃），finally 仍会 remove(id)；此时客户端已拿到成功应答，但事件流里既无 question.resolved，也没有可用于幂等重试的 requestId/answerHash，respond() 与 recorded() 都只返回 QUESTION_CLOSED，答案永久丢失。建议在 respond() 内（持有写者）完成 question.resolved 的追加与 flush 后再返回，或返回非提交态（如 ACCEPTED）并要求客户端 GET 确认，或在 append 失败时保留可重试的 pending 状态。
- 判断：respond只返回内存SUBMITTED，崩溃前未落盘有丢答案窗口；协议应要求GET最终核对并清晰呈现未持久，不可直接让HTTP线程写共享journal违反单写者。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:288

## OCR-0569 · 不采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:159；OCR原严重度：medium
- 计划：无
- 原意见：list() 的 pending 叠加层对投影结果未判空即解引用（第 160 行）。`rows.get(entry.getKey())` 在 pending 中存在某 questionId 而 `reader.events()` 投影里没有对应 question.requested 时返回 null（读取视图滞后/快照、日志被截断、写入未立即可见、reader 与 writer 非同一存储视图等），随后 `.get("state")` 抛 NPE，HTTP 列表接口变 500 而不是返回 CLOSED/INTERRUPTED。建议先取局部变量并判空；第 93 行先落盘再入 pending 只能缩小而不能消除该窗口。
- 判断：ask先save+flush再pending.put，内建reader同日志强一致，缺requested假设依赖不合规滞后/截断存储；可在投影畸形统一校验，不单列当前HTTP随机500。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:159

## OCR-0570 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:234；OCR原严重度：medium
- 计划：R2-04 / P2
- 原意见：recorded() 未校验 requestId 的类型/格式就进入 validateAnswer()（第 212-236 行，配合 314-322 行）。这里只校验了 keySet，`requestId` 可为 null 或非 String；validateAnswer 内 `Map.of("requestId", input.get("requestId"), ...)` 拒绝 null 直接抛 NPE，HTTP 校验入口返回 500 而不是协议错误 INVALID_ANSWER。同时 respond() 用 `[A-Za-z0-9_-]{1,64}` 校验 requestId，而 recorded() 没有同等约束，两条入口契约不一致。建议在调用 validateAnswer 前先做 `String requestId = string(input, "requestId", 64)` 与同样的正则校验，并让 validateAnswer 收到非空 requestId。
- 判断：recorded keySet相同但requestId缺类型/正则，validateAnswer Map.of可NPE；统一应答入口纯校验，在访问/写入之前失败。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:234

## OCR-0571 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:372；OCR原严重度：medium
- 计划：R2-04 / P2
- 原意见：project() 对重复/冲突事件静默 last-wins，且不校验 payload 形状（第 362-375 行）。同一 questionId 的重复 `question.requested` 或两个内容不同的 `question.resolved` 会被 `rows.put` 直接覆盖，恢复/回放时以最后一条为准，冲突不会被发现（这也让 64 条上限失去约束作用：伪造的重复事件不增加 rows.size()）。同时 payload 未校验 questionId 必填字符串、options/mode 结构，伪造或截断的 JSONL 会产生 null 键行，并让 validateAnswer 中 `(List<?>) question.get("options")` 抛 ClassCastException/NPE，而不是 fail-closed 地报 INVALID_QUESTION。建议：已存在的 questionId 再次出现 resolved（或状态已是终态）时抛冲突；校验 questionId 为非空字符串、options 为列表。
- 判断：project对冲突相同questionId last-wins且缺payload形状校验，接受历史事实冲突拒绝及版本兼容；“伪造日志无限提问”不是现有合法写入路径。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:372

## OCR-0572 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:252；OCR原严重度：low
- 计划：R2-04 / P2
- 原意见：respond() 用 string() 校验 questionId/requestId，而 string() 固定抛 INVALID_QUESTION（第 285 行、第 397-402 行）。应答路径上的畸形输入（超长/空白 questionId、非字符串 requestId）返回 INVALID_QUESTION，而紧随其后的正则校验与其余分支返回 INVALID_ANSWER，客户端无法用同一错误码区分“问题定义非法”和“答案非法”，属于协议一致性缺陷。建议让 string() 接收错误码参数，或在 respond/recorded 里先按 INVALID_ANSWER 校验。
- 判断：respond的string返回INVALID_QUESTION而余下INVALID_ANSWER不一致；合并应答校验错误码，不单独重构全部异常。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:252

## OCR-0573 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:254；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：respond() 一次调用会对会话日志做 3 次全量读取/投影（第 273-292 行）：`list()` 内部 `project(reader.events())`、随后的 `reader.events()` 去重扫描、以及结尾再次 `list()`；每次 events() 对 JSONL 实现都是整文件重新解析（上限 10,000 事件/32 MiB），且 list() 是在 `synchronized` 插件锁内完成的，会把 ask 线程的应答/超时判定与 draft/respond 一起阻塞在解析上。建议一次取投影结果并复用（`var rows = list(); ... row = rows.stream()...`、去重检查复用同一次 events 快照），并把解析移出插件锁。
- 判断：respond持插件锁内list/reader/list三次全读，接受单次快照复用及缩小锁，保持回答幂等和单写者。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:254

## OCR-0574 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:385；OCR原严重度：low
- 计划：R1-04 / P1
- 原意见：answerHash 是无盐 SHA-256，直接把原始答案（含 text）的可校验指纹持久化到 question.resolved（第 352-361 行，写入点第 126 行）。落盘正文经过 sanitizer 脱敏，但指纹基于原始输入：当用户把口令/短敏感串填进 text 时，攻击者拿到 JSONL 后可借助其余公开字段（questionId/sessionId/turnId/callId/selected/skip）离线字典爆破还原被脱敏的内容。建议改用会话密钥的 HMAC（或仅对非敏感字段计算指纹），使持久化后的哈希不可离线穷举。
- 判断：answerHash为原答案无盐摘要，低熵敏感文字可离线猜测；需设计持久幂等与安全摘要方案。不能仅随机会话密钥不保存就破坏重启核对，也不能只删text失去冲突识别。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:385

## OCR-0575 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:305；OCR原严重度：low
- 计划：R2-04 / P2
- 原意见：validateAnswer() 假定 question.get("options") 一定是元素为 Map 的 List：非 List 时强制转换抛 ClassCastException，元素非 Map 时 Values.object 抛 INVALID_JSON HarnessException，id 缺失时 allowed 中混入 null——这些都不是预期的协议错误码。触发条件：respond()/recorded() 的 question 来自 project() 对事件 payload 的复制（第 362-368 行未校验 payload 形状），磁盘上被篡改或缺字段的历史事件即可触发。建议在此处显式校验 options 形状并统一抛 INVALID_ANSWER / QUESTION_ASSOCIATION_MISMATCH。
- 判断：读取历史options裸转可被畸形事件触发，归并571的投影schema及安全失败；不把任意篡改本地日志认定授权漏洞。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:305

## OCR-0576 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:40；OCR原严重度：low
- 计划：R1-02 / P1
- 原意见：stop() 只翻转 active，未清理 pending，也未唤醒等待线程：条目要等对应 ask() 线程进入 finally（第 130-134 行）才被移除，而该线程此刻正以 CANCELLED 分支继续调用 save("question.resolved", ...)（第 127 行），插件作用域已关闭时写者可能已不可用，异常会掩盖 CANCELLED 语义并跳过唯一一次落盘机会。此外 list() 未检查 active，stop() 之后仍会把内存中的 PENDING/SUBMITTED 状态叠加返回给调用方，与「作用域已关闭」不一致。建议 stop() 中清空 pending，并让 list() 在非 active 时只返回投影结果（或直接 QUESTION_SCOPE_CLOSED）。
- 判断：stop只active=false，ask退出后仍写resolved可能与writer关闭竞争；需撤销后有界等待单写者结算，不能直接清pending丢已接受答案。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/UserQuestionsPlugin.java:40

## OCR-0577 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleConfig.java:48；OCR原严重度：medium
- 计划：R3-02 / P3
- 原意见：scheme 与回环主机名都用大小写敏感的 `equals` 比较，但 URI 方案名按 RFC 3986 是大小写不敏感的，且 `java.net.URI#getScheme/getHost` 会原样保留用户输入的大小写。触发条件：配置 `baseUrl = URI.create("HTTPS://api.example.com/v1")` 或 `http://LOCALHOST:8080`，`getScheme()` 返回 `HTTPS`、`getHost()` 返回 `LOCALHOST`，本应合法的 HTTPS 地址 / 本地回环地址会被直接拒绝并抛出误导性的 “Use HTTPS or loopback HTTP…” 异常。建议对 scheme 和 `localhost` 使用 `equalsIgnoreCase`（IP 字面量可用精确比较）。
- 判断：URI scheme/hostname按标准不区分大小写而构造精确比较，合法大写配置误拒；规范化后验证，无网络或凭据回退。
- 证据：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleConfig.java:48

## OCR-0578 · 采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleConfig.java:65；OCR原严重度：medium
- 计划：R3-02 / P2
- 原意见：API key 只拒绝 CR/LF，其余控制字符与空白（如 `\u0000`、`\u000b`、首尾空格、制表符）仍能通过构造期校验。这些 key 之后会被直接拼进 `Authorization: Bearer …` / `x-api-key`（见 `OpenAiCompatibleLlmClient` 第 434-435 行），`java.net.http.HttpClient` 会在发请求时才因非法 header 值抛 `IllegalArgumentException`，错误点被推迟到运行期且与配置无关；首尾空格则会造成服务端鉴权失败（凭据格式在传输中被静默改变），且这种失败难以定位。建议在此处按可打印 ASCII 白名单一次性校验，让畸形 key 在配置阶段即被拒绝。
- 判断：key仅拒CR/LF未拒其余非法header字符，可能日志已记requested后发请求才异常；配置阶段校验可打印范围与长度，错误不回显密钥。
- 证据：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleConfig.java:65

## OCR-0579 · 不采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleConfig.java:81；OCR原严重度：low
- 计划：无
- 原意见：构造时无条件把 `images` 和 `parallelTools` 覆盖为 false，静默丢弃调用方通过 `ModelCapabilities` 声明的能力：`capabilities()` 返回的对象与入参不一致，而调用方（如 `SubagentPlugin`、`EcosystemConfig` 直接转发的 `capabilities()`）无从得知这一降级。当前 `harness-llm` 内确实没有 images / parallel_tool_calls 的实现，fail-closed 本身正确，但这种“静默改写”在后续补上能力时会长期给出错误信号。建议显式拒绝或至少加注释说明这是传输层不具备的能力，而不是让调用方误以为配置生效。
- 判断：适配器明确不支持images/parallelTools，降级false是保守能力事实，现有调用capabilities能看到结果；不能为未来能力扩展假设修改当前契约。
- 证据：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleConfig.java:81

## OCR-0580 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/RoutedLlmClient.java:11；OCR原严重度：low
- 计划：R3-02 / P2
- 原意见：缺少对 `router`/`selected` 的空值校验。`ModelRouter` 是公共接口，接口契约并未保证 `select()` 一定返回非 null 实例；一旦注入的 router 对未知路线返回 null，构造会成功，而 NPE 会延迟到第一次 `capabilities()/prepare()/complete()` 时才暴露，且异常信息无法定位到路线名。当前 `LazyModelRouter.select` 会抛 `MODEL_ROUTE_NOT_FOUND`，因此这是对自定义 router 的健壮性缺口；建议在构造阶段用 `Objects.requireNonNull` 或带 route 的 `HarnessException` 立即失败。
- 判断：router.select非空契约未明确，Routed构造缺即时校验；在统一SPI边界快速失败，不以自定义违约推导默认模型失败。
- 证据：harness-llm/src/main/java/io/jharness/llm/RoutedLlmClient.java:11

## OCR-0581 · 采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/RoutedLlmClient.java:22；OCR原严重度：medium
- 计划：R3-02 / P2
- 原意见：`select()` 直接把 router 内部的共享缓存实例暴露给调用方，没有声明所有权。`LazyModelRouter.select` 使用 `computeIfAbsent` 返回同一实例（测试中也断言 `assertSame(router.select("a"), router.select("a"))`），而 `LlmClient` 继承 `AutoCloseable`，调用方按常规生命周期对拿到的客户端调用 `close()` 后，会把 router 缓存中的实例以及本对象冻结的 `selected` 一并关闭；之后本对象再调用 `complete()` 会失败，router 关闭时还会二次 close。建议明确 `select()` 返回的实例由 router 独占管理（文档/接口约定），或返回不负责关闭底层资源的视图，避免调用方误关闭。
- 判断：LazyModelRouter共享client并由router关闭，select没有借用所有权说明；明确borrowed视图/close规则，防一包装器关闭其它任务共享资源。
- 证据：harness-llm/src/main/java/io/jharness/llm/RoutedLlmClient.java:22

## OCR-0582 · 采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/RoutedLlmClient.java:26；OCR原严重度：medium
- 计划：R4-03 / P1
- 原意见：`RoutedLlmClient` 未覆写 `LlmClient.withDisplay(...)`，会退化为接口默认实现 `return this`，从而静默丢弃观察者。当宿主通过 `HarnessSdk`（其中执行 `model.withDisplay(display::accept)`）拿到本包装器，或 `router` 工厂返回实现了累积观察的适配器（如 `StreamingLlmClient`）时，显示回调永远到不了被选中的适配器，流式预览/观察流会丢失且无任何告警。应在包装器中转发该方法，并保留冻结的路由包装（可增加一个接收已装饰 `selected` 的私有构造器）。
- 判断：RoutedLlmClient未转withDisplay，SDK展示观察被接口默认吞掉；冻结route后转发显示装饰并验证实际路由流式展示。
- 证据：harness-llm/src/main/java/io/jharness/llm/RoutedLlmClient.java:26

## OCR-0583 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/RoutedLlmClient.java:52；OCR原严重度：low
- 计划：R3-02 / P2
- 原意见：`close()` 只关闭 router，隐含假设“router 一定拥有并会关闭它返回的所有适配器”，但该所有权并未写入 `ModelRouter` 的接口契约。若外部注入的 router 每次 `select()` 返回新建且不被自身跟踪的客户端，`selected` 的资源（连接/线程）会泄漏；反之，若同一个 `ModelRouter` 被多个 `RoutedLlmClient` 共享，任一包装器关闭都会把其他包装器正在使用的适配器一起关闭。建议在类/接口层显式约定所有权，或在关闭时对 `selected` 做受控释放并保证幂等。
- 判断：wrapper.close关闭整个router需明确独占/共享所有权；不能同时再关selected造成双重释放，合并581。
- 证据：harness-llm/src/main/java/io/jharness/llm/RoutedLlmClient.java:52

## OCR-0584 · 不采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/StreamingLlmClient.java:18；OCR原严重度：medium
- 计划：无
- 原意见：构造函数把调用方传入的可变 `secrets` 传给 `SecretRedactor`，却只把不可变副本存进 `this.secrets`，两处状态可能分叉：若调用方在构造后增删该列表，`redactor` 会跟随变化而第 20 行算出的 `hold` 不变，新加入的超长密钥不再被保留窗口覆盖，流式显示会打印该密钥的前 `len-hold` 个字符（敏感信息泄漏）。此外 `List.copyOf` 遇到 null 元素会直接抛 NPE，与第 20 行 `filter(Objects::nonNull)` 的容错意图矛盾（该过滤分支实际不可达）。建议统一基于副本构建：`this.secrets = secrets.stream().filter(s -> s != null && !s.isEmpty()).toList(); redactor = new SecretRedactor(this.secrets); hold = this.secrets.stream().mapToInt(String::length).max().orElse(0);`
- 判断：SecretRedactor构造通过stream.toList建立自己的不可变列表，不保留传入可变列表；hold已同时计算，后续修改原列表不能改变redactor。null元素List.copyOf拒绝属显式非空边界。
- 证据：harness-llm/src/main/java/io/jharness/llm/StreamingLlmClient.java:18

## OCR-0585 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/StreamingLlmClient.java:42；OCR原严重度：medium
- 计划：R3-02 / P1
- 原意见：`select` 返回的是未包装的 delegate / router 选中的 client，调用方（例如 LlmPlugin 把本类注册成 ModelRouter 后按路由取模型）拿到该 client 再执行请求时，既不会调用 `display` 也不会经过本包装器的 `SecretRedactor`：路由切换后“实时显示 + 全量密钥脱敏”静默失效，只剩 transport 层对 apiKey 的脱敏，其余密钥若被调用方展示/记录即泄漏。建议对选中的 client 重新包装，例如 `return new StreamingLlmClient(router.select(route), display, secrets);`，`base` 分支同样处理。
- 判断：Streaming.select返回原client确实绕过展示包装；保留装饰器路由语义并验证脱敏，不能假设任意直接router调用自动承担所有host sanitizer。
- 证据：harness-llm/src/main/java/io/jharness/llm/StreamingLlmClient.java:42

## OCR-0586 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/StreamingLlmClient.java:85；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：`stream` 直接透传 delegate 的 StreamHandle，完全绕过本类的 `redactor` 与 `display`：任何通过 `LlmClient.stream()` 取流的调用方收到的都是仅做了 transport 层 apiKey 脱敏的原始帧，这与类注释“Streaming transport exposed as complete”（只通过 complete 暴露流式）相反，其余密钥会在调用方展示/落盘时泄漏。建议要么抛 `UNSUPPORTED_STREAM` 强制走 `complete()`，要么返回包装后的 handle（逐帧 `redactor.text(...)` 并透传 `close`）。
- 判断：stream直接透传为低层原始接口，需明确公开脱敏契约或安全包装；标准complete会经redactor，当前低层frame泄露到日志未证实。
- 证据：harness-llm/src/main/java/io/jharness/llm/StreamingLlmClient.java:85

## OCR-0587 · 采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/StreamingLlmClient.java:69；OCR原严重度：low
- 计划：R4-03 / P2
- 原意见：截断点 `safe.length() - hold` 直接做 `substring`，未处理代理对：当切点恰好落在一个 emoji/增补平面字符的高代理项之后时，会输出孤立高代理项（非法 UTF-16），控制台/UI 显示为乱码或替换字符。同仓库 `SecretRedactor.streamingText` 已内含 `Character.isHighSurrogate` 修正，建议直接复用 `redactor.streamingText(frame.text())`，同时避免这里的 hold 计算与 redactor 内部窗口出现两套逻辑。
- 判断：safe.substring(length-hold)未防切中高代理，能给展示孤立UTF16；复用streamingText统一尾窗/非BMP边界。
- 证据：harness-llm/src/main/java/io/jharness/llm/StreamingLlmClient.java:69

## OCR-0588 · 采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/StreamingLlmClient.java:68；OCR原严重度：medium
- 计划：R4-03 / P1
- 原意见：`display.accept(...)` 在 transport 的 SSE 回调线程内同步执行且未做异常隔离：`OpenAiCompatibleLlmClient.SseBody.onNext` 会捕获消费回调抛出的任何异常，cancel 订阅并以 `MODEL_BAD_RESPONSE` 结束整个请求。因此宿主展示观察者（UI 快照渲染、写终端失败等）抛一次异常就会让本次模型调用失败，与“display 只是展示、不是执行事实”的约定相悖。建议把 `display.accept` 包在 try/catch 中（至少不向外抛出），保证展示失败不影响请求结果。
- 判断：display.accept未隔离异常，可让SSE订阅结束为MODEL_BAD_RESPONSE；仅展示降级应清空并诊断，不能影响主执行或输出未脱敏文本。
- 证据：harness-llm/src/main/java/io/jharness/llm/StreamingLlmClient.java:68

## OCR-0589 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/StreamingLlmClient.java:71；OCR原严重度：low
- 计划：R4-03 / P2
- 原意见：`finalResponse()` 的返回值未判空：自定义/插件 StreamHandle 若在未完整收流时返回 null（内置实现会抛 `MODEL_STREAM_INCOMPLETE`，但接口默认实现与第三方实现无此保证），第 72 行 `response.text()` 会抛裸 NPE，绕过 `HarnessException` 错误码体系，上层只能得到未知失败而无法区分“流不完整”。建议显式判空并抛出带错误码的异常（如 `MODEL_STREAM_INCOMPLETE`）。另外这一次 `display.accept` 会重放整段最终文本，对 append 型观察者会重复输出，建议在类注释中明确快照语义或只补发上次已发快照之后的增量。
- 判断：finalResponse自定义null缺明确完整性错误，补SPI契约检查；累积最终全文回调是既有快照语义，不能改为delta破坏消费者。
- 证据：harness-llm/src/main/java/io/jharness/llm/StreamingLlmClient.java:71

## OCR-0590 · 采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ProtocolStream.java:65；OCR原严重度：critical
- 计划：R3-02 / P1
- 原意见：`response.completed` 只校验 `ended` 中出现过的索引，未要求 `output.size() == ended.size()`。若服务端在终止事件的 `output` 中额外追加未经 `output_item.added/done` 校验的条目（例如额外的 function_call），这些条目会通过校验并随 `finish()` 返回；随后 `ProtocolCodec.decode` 会把它转换成可执行的工具调用（SYSTEM_IMPACT），从而绕过逐事件流式校验。触发条件：恶意或异常上游在 `completed.response.output` 尾部追加条目。建议在循环前断言数量一致。
- 判断：completed仅验证ended已有索引不检查output总数，多余function_call可逃过逐项流校验；必须总数/索引集合一致后才释放终态。
- 证据：harness-llm/src/main/java/io/jharness/llm/ProtocolStream.java:65

## OCR-0591 · 采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ProtocolStream.java:166；OCR原严重度：high
- 计划：R3-02 / P2
- 原意见：每个 Anthropic 文本 delta 都执行 `string(block, "text") + fragment`，即复制此前已累积的全部文本；n 个 delta 的总拷贝量为 O(n²)。结合较宽松的 `maxResponseBytes`，长文本流会带来明显的 CPU/耗时放大（可由上游控制 delta 切分粒度触发）。建议为当前打开的块维护一个 `StringBuilder` 累积文本，在 `content_block_stop` 时一次性写回 block。
- 判断：Anthropic每delta字符串拼接累计内容，极细分片放大复制；改有界StringBuilder并保持终态内容与预览一致。
- 证据：harness-llm/src/main/java/io/jharness/llm/ProtocolStream.java:166

## OCR-0592 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ProtocolStream.java:46；OCR原严重度：medium
- 计划：R3-02 / P2
- 原意见：`sequence_number` 缺失时直接跳过单调递增校验。Responses 协议的每个事件本应携带该字段；允许缺失意味着重复的 `response.output_text.delta` / `response.function_call_arguments.delta` 等事件会被再次处理并污染 `preview` 与累计状态（无法区分重放与合法的重复 token）。建议把该字段设为必需，缺失即判定 `MODEL_BAD_RESPONSE`。
- 判断：缺sequence_number当前跳过单调校验，应按明确支持Responses协议要求补必填/兼容策略回归；不声称重复文本delta必然恶意重放，需区分内容重复与事件重复。
- 证据：harness-llm/src/main/java/io/jharness/llm/ProtocolStream.java:46

## OCR-0593 · 不采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ProtocolStream.java:33；OCR原严重度：medium
- 计划：无
- 原意见：类注释声明“executable calls are released only after a valid terminal event and EOF”，但 `finish()` 只检查 `terminal`，没有任何 EOF/closed 状态；在终态帧之后、传输层 EOF 之前，任何调用方都可以拿到 `result` 并对外暴露可执行调用，契约与实现不一致。当前唯一调用点在 `SseBody.onComplete`（已校验残留缓冲），暂未触发，但属于可被后续调用方误用的释放语义缺陷。建议增加 `eof()`/`close()` 标志，或仅允许在 EOF 回调中释放结果。
- 判断：ProtocolStream包内唯一finish调用在SseBody.onComplete，已处EOF边界；注释是跨层不变量，非需要再加eof标志的当前释放漏洞。
- 证据：harness-llm/src/main/java/io/jharness/llm/ProtocolStream.java:33

## OCR-0594 · 采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/OpenAiCompatibleLlmClientTest.java:138；OCR原严重度：medium
- 计划：R0-03 / P1
- 原意见：这些「不得重放」用例的客户端都是用 `server.client(..., 0)`（rateLimitRetries=0）构造的，此时任何状态码或解析失败都只会发一次请求，`assertEquals(1, server.requests.size())` 恒为真，无法证明 500/503 这类 UNKNOWN 错误与畸形/重复 key/不完整响应不会被重放（无法区分「不重试」与「根本没启用重试」）。建议改用 `retries=1`（或 2）后仍断言只有 1 次请求，并保留一个 429 + retries=1 的对照用例，才能真正覆盖「UNKNOWN/解析失败不得重放」的约束。
- 判断：retries=0的单次断言无法证明启用重试仍拒5xx/畸形重放；用启用重试负例及429正例对照。
- 证据：harness-llm/src/test/java/io/jharness/llm/OpenAiCompatibleLlmClientTest.java:138

## OCR-0595 · 部分采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/OpenAiCompatibleLlmClientTest.java:161；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：脱敏断言只覆盖了 `toString()`，且在 `Map.of("api_key", KEY)` 这类分支里直接丢弃了异常对象（`assertThrows` 的返回值未被使用），没有任何针对 `getMessage()` 与 cause 链的断言。服务端返回的响应体就是 sentinel KEY 原文，是现成的探针；若实现把上游响应体/URL/参数值放进 message 或 cause（日志同样会输出），现有用例仍会通过。建议接住异常并断言其 message/toString/cause 均不含 KEY，同时断言错误码未被误用。
- 判断：测试只查toString不覆盖cause链，接受秘密sentinel错误全链回归；未确认生产实际泄密，归为验证缺口。
- 证据：harness-llm/src/test/java/io/jharness/llm/OpenAiCompatibleLlmClientTest.java:161

## OCR-0596 · 采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/OpenAiCompatibleLlmClientTest.java:100；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：往返断言过弱：只验证序列化后的 `arguments` 是 String，既没有把字符串解析回 Map 比对内容，也没有验证 tool 消息与 assistant tool_calls 的 `tool_call_id` 关联。若 `stringify` 把参数写成 `"{}"`、`"null"` 或丢失 id 关联，本用例仍然会通过。建议解析回对象并断言 `Map.of("path", "pom.xml")`，并断言 wire 中 tool 消息的 `tool_call_id` 为 `c1`。
- 判断：wire仅断言arguments是String无法防参数丢失，补解析内容及callId关联，校验实际协议不复制实现。
- 证据：harness-llm/src/test/java/io/jharness/llm/OpenAiCompatibleLlmClientTest.java:100

## OCR-0597 · 不采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/OpenAiCompatibleLlmClientTest.java:377；OCR原严重度：low
- 计划：无
- 原意见：夹具通过字符串拼接生成 JSON 且完全不做转义：一旦有人传入含双引号、反斜杠或换行的文本（例如把模型的报错原文塞进来），产生的是非法 JSON，失败会表现为“客户端解析错误”而不是夹具错误，误导排查方向。建议与 `toolResponse` 保持一致，改用 `JSON.writeValueAsString` 构造。
- 判断：text helper调用均受控常量，未出现含引号输入导致错误；可在新增Unicode/转义协议回归顺带改善，不独立修复fixture想象路径。
- 证据：harness-llm/src/test/java/io/jharness/llm/OpenAiCompatibleLlmClientTest.java:377

## OCR-0598 · 部分采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/OpenAiCompatibleLlmClientTest.java:427；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：`server.stop(0)` 之后立即 `workers.shutdownNow()`，既没有等待 `workers` 终止，也忽略了 `shutdownNow` 的返回值。仍在运行的 handler（例如超时用例里 `Thread.sleep(500)` 后写响应的 handler、取消用例里阻塞在 `release.await` 的 handler）可能在测试结束甚至下一个用例运行时继续向已关闭的 exchange 写数据，造成偶发异常与用例间串扰。建议 `shutdownNow()` 后 `awaitTermination(1, TimeUnit.SECONDS)`，未终止时显式失败或至少记录。
- 判断：Server.close shutdownNow无await，接受有界线程退出检查防测试串扰；不能以未观察残留就宣称当前每轮泄漏。
- 证据：harness-llm/src/test/java/io/jharness/llm/OpenAiCompatibleLlmClientTest.java:427

## OCR-0599 · 部分采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/OpenAiCompatibleLlmClientTest.java:234；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：`finally` 位于 try-with-resources 之外：资源按反序先关闭（`executor`/`client`/`server`），此时服务端 handler 可能仍阻塞在 `release.await`，`stop(0)` 与仍在执行的 handler 产生竞争；另外 body 内已经 `release.countDown()`，finally 中的重复释放让释放时机变得不清晰。建议在断言结束处（关闭资源之前）明确释放，并断言被阻塞的 body 读取已解除、无残留 worker；同时注意 `ExecutorService.close()` 会等待任务终止，若被测客户端未响应取消，用例会长时间挂起而不是快速失败。
- 判断：阻塞handler取消回归需确保关闭前释放latch及有界退出；重复countDown是幂等保护不应仅为清晰删finally兜底。
- 证据：harness-llm/src/test/java/io/jharness/llm/OpenAiCompatibleLlmClientTest.java:234

## OCR-0600 · 采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentPlugin.java:232；OCR原严重度：high
- 计划：R1-03 / P1
- 原意见：中断标志先于父会话日志写入被恢复，会永久毒化父会话写入器。`Thread.currentThread().interrupt()` 之后紧接着调用 `cancel(task)` 和 `finish(...)`，两者都会经 `event()` 落到 `host.writer().append()`,而生产实现 `JsonlSessionRepository.Writer.append` 使用 `FileChannel.write`（第 553 行 `channel.write(buffer)`）。按 `InterruptibleChannel` 规范，线程中断状态已置位时调用 `write` 会立即关闭通道并抛 `ClosedByInterruptException`，`append` 捕获 IOException 后把 writer 置为 `poisoned` 并抛 `CHECKPOINT_FAILED`。触发条件：父步骤被中断（超时/取消/宿主关闭）时正好有一次委托在执行——此后父会话所有写入都失败，会话不可继续、恢复链也被破坏。建议先完成取消与落盘，最后再恢复中断标志（或用 `finally` 兜底），并保证最终写入发生在一个未置位中断标志的线程上。
- 判断：父等待InterruptedException先恢复中断再cancel/finish写FileChannel，会ClosedByInterrupt poison父writer；先安全结算后finally恢复中断，配真实JSONL回归。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentPlugin.java:232

## OCR-0601 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentPlugin.java:482；OCR原严重度：high
- 计划：R1-03 / P1
- 原意见：`abort()` 用 interrupt 去打断 worker，但 worker 被中断后剩下的职责恰恰是向**父**会话的共享 `host.writer()` 写 `subagent.completed`（`runChild` 的 finally → `finish`）。父写入器是 FileChannel 支撑的可中断通道（`JsonlSessionRepository` 第 553 行），中断标志残留时 `channel.write` 会抛 `ClosedByInterruptException` 并关闭通道，`append` 随即把父 writer 置为 `poisoned`（CHECKPOINT_FAILED）。触发条件：子任务在 2 秒内没有停下（CHILD_DID_NOT_STOP）进入 `abort`。结果是父会话日志从此不可写——为了中止一个卡住的子任务反而让父会话失去持久化能力。建议不要在 worker 上置位中断标志（改用取消令牌 + 有超时的 `sdk.close()`），或在 worker 写父日志前用 `Thread.interrupted()` 清除标志。
- 判断：abort中断worker而finish可能写父journal，有关闭channel风险；需统一父单写者调度，不能无条件清掉中断伪造已退出或改为无限等待。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentPlugin.java:482

## OCR-0602 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentPlugin.java:433；OCR原严重度：medium
- 计划：R1-03 / P1
- 原意见：取消的必需操作顺序被日志写入挡住：先写 `subagent.cancel_requested`，再调用 `task.sdk.cancel()`。父写入器在达到 `SESSION_LIMIT`/`LOG_LIMIT`、`WRITER_POISONED` 或通道被关闭（见上面中断问题）时会抛异常，异常在 `task.sdk.cancel()` 之前抛出，导致程序化的取消（`SubagentService.cancel`、`shutdown`、父令牌取消）根本没有传到子任务，子任务继续占用并发名额与模型。建议把 `task.sdk.cancel()` 放进 try 之前/finally 中保证一定执行，审计事件失败只降级为 UNKNOWN 而不是阻断取消。
- 判断：cancel_requested持久写失败可能阻断sdk.cancel，但task.cancelled已CAS置位也被子token观察；仍需finally确保所有取消通道及清理，不宣称子一定继续不取消。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentPlugin.java:433

## OCR-0603 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentPlugin.java:644；OCR原严重度：medium
- 计划：R1-03 / P1
- 原意见：`shutdown()` 对仍在运行的子任务零等待就直接写 `UNKNOWN/PARENT_CLOSED`：子任务通过 `!active` 令牌本可以很快就以 `CANCELLED` 收敛（`delegate` 路径自己也等 2 秒），但这里立刻用 CAS 占住 `finished`，worker 之后真实的 `CANCELLED`/`COMPLETED` 结果被 `finish` 的 CAS 丢弃。后果是父会话日志永久留下未结算的 `subagent:<id>`，`SessionRecovery` 保持父会话不 ready，必须人工 `subagent verify` 才能继续恢复——把一次确定性取消升级成了人工干预。另外这里没有任何异常隔离：`pending.forEach(this::cancel)` 或 `finish` 任一抛出（`SESSION_LIMIT`、`WRITER_POISONED`、`SUBAGENT_RESULT_UNCERTAIN`）都会中断循环，剩余子任务既不取消也不 abort，异常还会冒出 rollback hook 影响后续清理。建议按任务逐个 try/catch，并给 worker 一个有限的收敛窗口后再判定 UNKNOWN。
- 判断：shutdown无等待先finish UNKNOWN且逐任务异常可中断后续清理，接受有界收敛与逐个异常收集；未观察停止时UNKNOWN是正确保守状态，不能强记CANCELLED。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentPlugin.java:644

## OCR-0604 · 部分采纳

- 位置：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentPlugin.java:576；OCR原严重度：medium
- 计划：R1-03 / P1
- 原意见：`verify()` 的两个落盘步骤顺序不可重入/不可幂等：先写 `subagent.verified`，之后才写 `tool.verified`。若 `subagent.verified` 成功而随后的 `tool.verified` 失败（`SESSION_LIMIT`/`LOG_LIMIT`/进程退出），父调用仍是 uncertain，但子任务已从 `SessionRecovery.uncertainCalls()` 中移除——再次执行 `subagent verify` 会在入口因 `SUBAGENT_NOT_UNCERTAIN` 失败，`sessions verify` 又只会结算父调用、不会补 `subagent.verified`，该会话就被卡死在“既不 ready 也无法用文档化命令修复”的状态。建议把 `tool.verified` 的补写放在 `subagent.verified` 之前（这样中断后重跑入口前置条件仍成立、已结算的调用自动跳过），或让入口对“已写 `subagent.verified` 但父调用未结算”的任务继续放行。
- 判断：verify先subagent.verified后tool.verified，中断后前置可能拒重复核实；需双事实可重入结算回归。通用sessions verify仍可结算父call，不称永久无修复路径。
- 证据：harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentPlugin.java:576

## OCR-0605 · 采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/MultiProtocolTest.java:396；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：用例名承诺覆盖 "pre-cancelled"，但方法体只验证了超限响应（limit=128）与 401 鉴权两条失败路径，从未传入一个在请求开始前就已取消的 CancellationToken。若客户端在 prepare/建连阶段忽略取消状态（仍发出 HTTP 请求），本用例无法发现。建议补充： ```java var preCancelled = new java.util.concurrent.atomic.AtomicBoolean(true); try (var f = new Fixture(protocol, JSON.writeValueAsString(response(protocol, false)), false, 200, 65536)) {     assertEquals("CANCELLED", assertThrows(HarnessException.class,             () -> f.client.complete(request(false), preCancelled::get)).code());     assertNull(f.path.get(), "pre-cancelled request must not reach the wire"); } ```
- 判断：名称PreCancelled但方法只覆盖超限/401，补预取消不触wire的实际计数断言。
- 证据：harness-llm/src/test/java/io/jharness/llm/MultiProtocolTest.java:396

## OCR-0606 · 部分采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/MultiProtocolTest.java:421；OCR原严重度：medium
- 计划：R0-03 / P1
- 原意见：`HttpServer.create(...)` 未调用 `setExecutor`，按 JDK 语义默认使用 start() 创建的单个调度线程同步执行 handler；而本 handler 会阻塞在 `release.await(5, SECONDS)` 上。因此即使客户端在 CANCELLED 之后真的重放了请求，第二个请求的 handler 也无法被调度，`assertEquals(1, calls.get())` 断言时计数仍为 1（重放请求要到 finally 里 release 之后才会被计数），"绝不重放" 的断言形同虚设。建议给 server 配置多线程 executor，或在断言前先 `release.countDown()` 并留出短暂窗口再检查 calls，同时保证 finally 中不再重复阻塞。
- 判断：取消fixture单线程handler阻塞时计数不能看到排队第二请求；多线程server或释放后核验总数，不能靠短sleep宣称绝不重放。
- 证据：harness-llm/src/test/java/io/jharness/llm/MultiProtocolTest.java:421

## OCR-0607 · 部分采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/MultiProtocolTest.java:310；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：Fixture 的资源清理不是异常安全的：`server.start()` 在构造 `OpenAiCompatibleConfig`/`OpenAiCompatibleLlmClient` 之前执行，客户端构造抛 IllegalArgumentException 时构造函数异常退出，`try (var f = new Fixture(...))` 还没拿到对象，`close()` 不会被调用；`close()` 内 `client.close()` 与 `server.stop(0)` 之间也没有 try/finally，前者抛异常时 server 不会被停止。两种情况都会把监听 127.0.0.1 的 socket 与调度线程泄漏到整个测试 JVM（每个 @EnumSource 用例各泄漏一个）。建议用 try/catch 包裹客户端构造，失败时先 `server.stop(0)` 再重抛，并让 `server.stop(0)` 位于 finally 中。
- 判断：Fixture先start后client构造且close无finally，构造/close异常可漏server；接受异常安全清理，当前受控构造均合法不宣称每用例都泄漏。
- 证据：harness-llm/src/test/java/io/jharness/llm/MultiProtocolTest.java:310

## OCR-0608 · 采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/MultiProtocolTest.java:352；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：脱敏覆盖不足：这里只断言序列化请求体不含内部字段 `protocol`，没有任何断言保证 API key 不会出现在请求体、日志或异常消息中。KEY 是测试用假值，可安全用于断言。建议补充 `assertFalse(JSON.writeValueAsString(f.wire.get()).contains(KEY));`，并在 401/超限用例中同样断言异常消息不含 KEY（`assertFalse(ex.getMessage().contains(KEY))`），否则 "凭据不落盘/不落日志" 的回归无法被发现。
- 判断：多协议wire只查protocol字段，补KEY不入正文/错误cause等路径，归并脱敏协议验证缺口。
- 证据：harness-llm/src/test/java/io/jharness/llm/MultiProtocolTest.java:352

## OCR-0609 · 采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/MultiProtocolTest.java:147；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：SSE 夹具只输出 `data:` 行，而真实的 Anthropic/Responses 流会先发送 `event: <type>`（如 `event: response.completed`、`event: content_block_delta`）。客户端对 event 名与 payload 内 `type` 的交叉校验路径因此完全未被覆盖：即使该校验实现写错，整套用例仍会通过。建议对含 `type` 字段的 payload 额外输出一行 `event: " + value.get("type")`，并保留至少一个 event 名与 type 不一致的负向用例。
- 判断：event helper只data不覆盖event/type交叉验证；加匹配与冲突帧以及EOF完整性回归。
- 证据：harness-llm/src/test/java/io/jharness/llm/MultiProtocolTest.java:147

## OCR-0610 · 采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/MultiProtocolTest.java:509；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：畸形工具参数的两个分支只断言抛出 `HarnessException`，未校验错误码；传输超时、连接失败、解析器其它失败等无关路径同样会抛 HarnessException，使用例在 "放行非法参数" 的回归下仍然通过（也会掩盖 `raw.replace(...)` 未命中、响应体其实合法的情况）。建议同时断言 `ex.code()` 与相邻用例保持一致的写法规格，并断言替换确实生效（如 `assertTrue(raw.contains("[]"))`）。
- 判断：畸形参数仅assertThrows宽泛异常，补替换生效和精确MODEL_BAD_RESPONSE/参数校验码，防无关传输错误假通过。
- 证据：harness-llm/src/test/java/io/jharness/llm/MultiProtocolTest.java:509

## OCR-0611 · 采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java:105；OCR原严重度：high
- 计划：R3-02 / P2
- 原意见：Anthropic 的 `stop_sequences` 必须是字符串数组，但 Chat 的 `stop` 常见形态是单个字符串（仓库测试 OpenAiCompatibleLlmClientTest 就是 `"stop", KEY` 这种 String）。这里直接重命名会把 `"stop":"END"` 原样变成 `"stop_sequences":"END"`，Anthropic 会返回 400 invalid_request_error。触发条件：protocol=ANTHROPIC 且 parameters.stop 为 String。建议对 String 包装为单元素 List，并对 List 元素逐项校验为 String。
- 判断：Anthropic stop直接move成stop_sequences，String输入未归一化列表；在协议转换前校验并转换兼容参数。
- 证据：harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java:105

## OCR-0612 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java:189；OCR原严重度：medium
- 计划：R3-02 / P1
- 原意见：这里只校验响应里的 `model` 非空后就把值丢弃，而 `provider_state` 一律用配置的 `model` 打标（第 261 行）。如果兼容端点/代理实际返回了另一模型的响应，其 encrypted reasoning 会被标记为配置模型；下一次 encode 的 `model.equals(state.get("model"))` 校验必然通过，从而把外部模型的推理状态回放给提供商，provider-state 的模型作用域被绕过。建议把响应声明的 model 纳入绑定（不一致时拒绝或丢弃状态），而不是只做非空校验。
- 判断：响应model只非空不用于provider_state绑定，配置别名与实际版本可能不同；需明确允许的模型身份策略并拒跨模型状态，不能简单等号拒所有合法provider别名。
- 证据：harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java:189

## OCR-0613 · 采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java:286；OCR原严重度：medium
- 计划：R3-02 / P1
- 原意见：summary 项只校验了 `type` 与 `text`，但这里把 provider 返回的原始对象整体放进 `provider_state`。这样 provider 附加的任意字段会绕过白名单被持久化，并在后续请求中随 reasoning 一起回放。触发条件：响应 `reasoning.summary` 的项带有额外字段。建议只保留 `type`/`text` 重建对象。
- 判断：summary校验type/text后仍保留原Map额外字段，provider_state白名单不完整；只重建受支持字段并验证保存/回放一致。
- 证据：harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java:286

## OCR-0614 · 不采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java:49；OCR原严重度：medium
- 计划：无
- 原意见：provider_state 用 chat 消息下标 `i` 去索引 `request.messages()`，但 codec 没有校验两个列表长度/顺序一致。一旦调用方对消息做过滤、合并或重排（或直接以不同列表调用 codec），轻则抛出未包装的 IndexOutOfBoundsException（`encode(ChatRequest)` 只捕获 JsonProcessingException，会直接穿透），重则把别的消息的 reasoning 回放给模型。当前 OpenAiCompatibleLlmClient 逐条一一映射所以暂时成立，但这是隐式不变量。建议显式校验下标与 role 对齐，或把 state 与 wire 消息成对传入。
- 判断：ProtocolCodec包内由OpenAiCompatibleLlmClient按同序一一映射消息，原意见也承认当前不变量成立；不存在独立公开encode传不同列表入口。
- 证据：harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java:49

## OCR-0615 · 不采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java:16；OCR原严重度：low
- 计划：无
- 原意见：3 参构造把 endpoint 置为空串，使 endpoint 绑定检查形同虚设：两个都用该构造创建、实际指向不同 base URL 的 codec 之间，provider_state 的 `endpoint.equals(...)` 校验都会通过。当前生产代码使用 4 参构造，但测试/未来调用方复用 3 参构造时会丢失端点作用域。建议不提供空 endpoint 的默认构造，或在回放时拒绝空 endpoint 的状态。
- 判断：3参codec为包内测试便利构造，生产用4参绑定endpoint；测试中的空endpoint不构成跨真实服务回放漏洞。
- 证据：harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java:16

## OCR-0616 · 不采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java:111；OCR原严重度：low
- 计划：无
- 原意见：Anthropic 只接受 user/assistant 角色，但这里除 system/tool 外的任何角色（如 developer、function）都会原样写入 `messages`，最终被 Anthropic 以 400 拒绝。当前 OpenAiCompatibleLlmClient.encode 会先把角色限制为 system/user/assistant/tool，生产路径暂不可触发；建议 codec 自身也校验 role，保证协议转换独立正确。
- 判断：生产入口已限制角色且codec包内，重复角色验证可选但给定developer/function路径不可达。
- 证据：harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java:111

## OCR-0617 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java:168；OCR原严重度：low
- 计划：R3-02 / P2
- 原意见：Chat 工具定义允许省略 `parameters`（OpenAI 视为无参数函数），而 Anthropic 的 `input_schema` 是必填。这里对缺失/null 的 parameters 直接 `obj(...)` 会抛 `MODEL_BAD_RESPONSE`（还是响应侧错误码），导致合法请求被拒。触发条件：tools 中存在没有 parameters 的函数定义。建议缺省时生成空 schema，仅对非 Map 的非法值报错。
- 判断：转换要求parameters对象，应在公共声明契约明确无参工具使用{}，若支持省略则统一归一化；当前DefaultPromptComposer内建工具均有schema，不按正常全局故障定级。
- 证据：harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java:168

## OCR-0632 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/CancelMain.java:10；OCR原严重度：medium
- 计划：R1-03 / P2
- 原意见：参数解析未做空白校验：`args.length != 4` 只校验数量与选项名，`--state-dir ""`、`--run ""` 这类空串仍会通过校验进入 cancel。`Path.of("")` 会得到空路径，`directory.resolve(runId + ".run")` 于是退化为相对当前工作目录查找状态文件（可能误命中 CWD 下任意 `<uuid>.run` 并向其中的 port 发起取消请求），与“必须使用专用状态目录”的契约冲突；空 runId 虽会被 cancel 的 UUID 校验拦成 INVALID_STATE(5)，但与用法错误的退出码 5 无法区分。建议在此处显式拒绝空白参数。
- 判断：CancelMain空白state-dir变cwd，应先拒空参数给用法退出码；cancel仍严格runId/状态/token，不能凭cwd读同名文件认定任意取消。
- 证据：harness-process/src/main/java/io/jharness/process/CancelMain.java:10

## OCR-0633 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/CancelMain.java:14；OCR原严重度：high
- 计划：R1-03 / P2
- 原意见：入口未捕获非受检异常，破坏退出码契约：`Path.of(args[1])` 在路径含 NUL/非法字符时抛 `InvalidPathException`（其父类为 IllegalArgumentException，但在本方法内未被捕获），`LocalRunControl.cancel` 中 `Files.exists`/`Files.size` 等亦可能抛 `SecurityException`（SecurityManager 场景）。这些异常会跳过 `print` 与 `System.exit(result.exitCode())`，以未捕获堆栈和 JVM 默认退出码 1 结束；而 CancelResult 仅定义了 0/2/3/4/5，自动化脚本会把 1 当作未知结果。建议在调用处兜底，将参数非法映射为 5、其余运行时异常映射为 4，保证始终输出结果并以约定码退出。
- 判断：Path.of非法NUL可未捕获退出1破约定码，入口应映射INVALID_STATE且不打印敏感堆栈；SecurityManager在JDK21不是支持部署前提。
- 证据：harness-process/src/main/java/io/jharness/process/CancelMain.java:14

## OCR-0642 · 采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleLlmClient.java:493；OCR原严重度：high
- 计划：R3-02 / P1
- 原意见：SSE 取消存在竞态，取消可能丢失并造成无界读取（行 466-468、486-489、296-297、290-296）。`subscription` 既非 volatile 也无 `cancelled` 标志： 1) 触发条件：`StreamHandle.close()`（行 291-296）在 `sendAsync` 的 body handler 尚未执行 `onSubscribe` 之前被调用（例如 `consume()` 中 25ms 轮询检测到 `closed`/超时后走 `finally { if (result == null) close(); }`，行 296-297），此时 `subscriber == null`、`subscription == null`，`close()` 的取消被吞掉；随后 `onSubscribe` 仍 `value.request(1)`，`onNext`（行 506 起）完全不检查已取消状态，于是 handle 已关闭仍持续下载并逐帧调用调用方 `consumer`，直到服务端关闭或响应超过 `maxResponseBytes`。 2) 同一竞态还使 `close()` 与 `consume()` 的 `synchronized` 检查（行 279-283）之后、`sendAsync`（行 288）之前存在窗口：关闭后请求仍会发出。 建议：`subscription` 改 volatile 并增加 `volatile boolean cancelled`；`cancel()` 先置标志再取消；`onSubscribe` 若已取消则 `value.cancel()` 直接返回；`onNext` 入口检查已取消则停止处理；`consume()` 在 `sendAsync` 前再检查一次 `closed`。
- 判断：SseBody subscription非volatile且cancel无前置取消标志，晚onSubscribe可能继续request；需原子关闭/订阅竞态与关闭前发送边界验证。
- 证据：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleLlmClient.java:493

## OCR-0643 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleLlmClient.java:239；OCR原严重度：medium
- 计划：R3-02 / P2
- 原意见：流式路径把所有非 200 一律映射为 `MODEL_HTTP_ERROR`，与非流式（行 176-195 的 401/402/429/400/422/5xx 精确映射）不一致，且 429 不参与 `config.rateLimitRetries` 退避。触发条件：流式请求收到 401（鉴权失效）、402（余额不足）或 429（限流）时，调用方拿到的是通用 HTTP 错误码，无法据此做凭据刷新/降级/重试决策；且 `complete()` 在 `stream==true` 时（行 122-126）直接转 `stream()`，`rateLimitRetries` 对流式请求完全失效。建议至少按状态码 switch 抛出与非流式相同的错误码（`info.statusCode()` 在 body handler 中可用），并在 429 且仍有重试预算时于消费 body 前重发。
- 判断：流非200统一MODEL_HTTP_ERROR降低401/402/429诊断，接受一致错误映射；M8明确仅非流429重试，不纳入自动流重试建议。
- 证据：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleLlmClient.java:239

## OCR-0644 · 采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleLlmClient.java:374；OCR原严重度：medium
- 计划：R3-02 / P1
- 原意见：非流式解析缺少 `finish_reason` 与 `tool_calls` 的一致性校验，与流式路径（行 660-662 的 `calls.isEmpty() && !"stop".equals(finish) || !calls.isEmpty() && !"tool_calls".equals(finish)`）不一致。触发条件：provider 返回 `finish_reason=tool_calls` 但 `message.tool_calls` 缺失/为空时，只要 content 非空就会被当作正常最终回答返回（本应 `MODEL_INCOMPLETE`）；反之 `finish_reason=stop` 却携带 tool_calls 时，调用方仍会执行这些工具调用。畸形/被篡改的响应因此绕过流式路径的严格校验进入下游工具处理。建议在 `calls` 组装完成后补上与流式一致的判定。
- 判断：非流允许stop携tool_calls及tool_calls空调用，与流严格终态不同；统一finish/calls一致校验，畸形响应不得进入工具。
- 证据：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleLlmClient.java:374

## OCR-0645 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleLlmClient.java:390；OCR原严重度：medium
- 计划：R1-04 / P1
- 原意见：tool call 的 `id` 与 `name` 未经脱敏就构造 `ToolInvocation`（行 388-401），而 content、arguments、usage、provider_state 都经过 `redactor`。触发条件：provider（或响应被中间层改写、模型回显训练/提示中的字符串）在 call id 或工具名中返回包含 `config.apiKey` 的文本时，`ChatResponse.toolCalls()` 会原样把密钥带到日志、UI 事件与工具解析链路，绕过本类的脱敏边界（`prepare()` 只校验请求方向，无法补救响应方向）。建议对 `id`/`name` 执行 `redactor.text(...)` 后再构造；脱敏后匹配不到已注册工具时应 fail closed（未知工具/拒绝执行），而不是把原值透传。
- 判断：适配器响应id/name未redact，标准Loop再clean但公开client仍暴露；统一敏感标识拒绝或安全标识，不能无声重写callId造成关联碰撞。
- 证据：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleLlmClient.java:390

## OCR-0646 · 不采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleLlmClient.java:399；OCR原严重度：medium
- 计划：无
- 原意见：`requiredText(function, "arguments")` 要求非空字符串，会把合法 tool call 判为 `MODEL_BAD_RESPONSE`。触发条件：无参数工具的流式增量通常从未下发 `arguments`，`onComplete` 组装时用 `parts.getOrDefault("arguments", new StringBuilder())` 兜底为空串（行 637-650），随后经 `json.writeValueAsBytes` 回灌到本方法即抛错——即“零参数工具 + 流式”必然失败；Process-elsewhere 的 `ProtocolCodec` 对空 arguments 是归一化为 `"{}"` 的（例如 Anthropic input_json_delta 的累计），前后不一致。建议 arguments 缺失/空串时按 `{}` 处理，再做 JSON 解析与脱敏。
- 判断：合法Chat无参函数应返回arguments为{}字符串，缺失/空串并非合法完整工具参数；严格拒不完整流符合契约，不接受自动补{}执行模型未给出的调用。
- 证据：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleLlmClient.java:399

## OCR-0647 · 部分采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleLlmClient.java:547；OCR原严重度：low
- 计划：R4-03 / P2
- 原意见：流式预览用 `redactor.text(...)` + 固定长度硬截断重复实现了 `SecretRedactor.streamingText`（该方法已针对尾窗保留做了 `Character.isHighSurrogate` 回退），但漏掉了代理项处理，且 Responses/Anthropic 分支（行 579-583）同样如此。触发条件：截断点正好落在 UTF-16 代理对中间时（中文之外的 emoji/增补平面字符紧邻末尾），返回的预览串以孤立高位代理结尾，属于非法 UTF-16，写入 Session 事件/JSON 或经 UTF-8 编码时会被替换成 U+FFFD 甚至抛错；同时密钥长度大于实际持有窗口时，还会永久吞掉末尾的合法可见字符。建议两处统一改用 `redactor.streamingText(...)`，与 DataSanitizer 契约保持一致。
- 判断：两协议预览尾窗substring未防代理对，接受统一streamingText；固定hold保护秘密，不是尾部永久丢失（最终响应仍完整）。
- 证据：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleLlmClient.java:547

## OCR-0648 · 采纳

- 位置：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleLlmClient.java:166；OCR原严重度：low
- 计划：R3-02 / P1
- 原意见：`pending.get(...)` 在 future 被 `cancel(true)` 后会抛 JDK 的 `CancellationException`（RuntimeException 子类），这里直接原样重抛，`stream()` 的 255-275 行也没有对应捕获。触发条件：流式 handle 的 `close()` 从另一线程取消 pending（行 290-295），或 `HttpClient.shutdownNow()` 关闭客户端后仍在使用本实例，调用方将收到 `java.util.concurrent.CancellationException` 而非本类统一的 `HarnessException("CANCELLED")`，破坏调用方基于错误码的取消/恢复判定。建议显式 `catch (CancellationException ex)` 并映射为 `CANCELLED`（两处 `get` 轮询都要覆盖）。
- 判断：跨线程close取消future可抛裸CancellationException，统一有阶段信息CANCELLED/UNKNOWN，确保恢复不得据取消认定未发送。
- 证据：harness-llm/src/main/java/io/jharness/llm/OpenAiCompatibleLlmClient.java:166

## OCR-0662 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/ControlledAgentRun.java:27；OCR原严重度：high
- 计划：R1-03 / P2
- 原意见：The one-run invariant is enforced with a plain, non-volatile `boolean` and a check-then-set sequence (lines 11, 27-29). Two threads calling `run()` concurrently can both observe `started == false` and both start an agent loop on the same control endpoint, and because the field is not volatile other threads may never observe the write. `ControlledAgentRun` is public in `harness-process` and is handed to an executor in `CliApplication`, so this cannot be assumed single-threaded. Use an atomic guard (and `final`) so the check-and-set is a single atomic operation.
- 判断：公开ControlledAgentRun.started普通布尔，若并发run可双进入；默认CLI一次提交不构成已发生竞态。使用CAS并测试重复/关闭后run拒绝。
- 证据：harness-process/src/main/java/io/jharness/process/ControlledAgentRun.java:27

## OCR-0663 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/ControlledAgentRun.java:53；OCR原严重度：high
- 计划：R1-03 / P1
- 原意见：`close()` 与终止事件的顺序及失败处理都有问题：`close()` 在 `run.ended` 追加之前执行，而 `LocalRunControl.close()` 会立即把状态文件改写为 `status=EXITED` 并清空 token——进程在两步之间死亡或 `close()` 抛异常时，恢复会看到已 EXITED 但事件日志仍止于 `run.started`；`run.ended` 也是唯一的终止记录却从未获得 `DURABLE_FLUSH`，崩溃即丢失，且只记录 `cancelled` 无法区分失败与成功。此外 `finally` 中 `close()` 抛出的 `HarnessException("RUN_STATE_IO", ...)` 会替换原始失败（并因此跳过 `run.ended` 追加），捕获到的 `IOException` 也从未作为 cause 挂上。建议先追加并持久化 flush 终止事件（含 outcome/错误码）再标记 EXITED，失败时用 addSuppressed/rethrow 保留原始异常，并把 IOException 作为 cause。
- 判断：close状态EXITED先于run.ended，close异常覆盖主故障；需分离端点停止与运行结果事实并保留首因，失败时不能延迟必需取消/资源释放。
- 证据：harness-process/src/main/java/io/jharness/process/ControlledAgentRun.java:53

## OCR-0664 · 采纳

- 位置：harness-process/src/main/java/io/jharness/process/ControlledAgentRun.java:69；OCR原严重度：medium
- 计划：R1-03 / P2
- 原意见：`run()` itself calls `close()` (line 52) and the class also exposes a public `close()`, but no closed state is tracked. A caller that closes the endpoint first (e.g. an early `try`-with-resources exit or an explicit `close()`) can still call `run()` afterwards: the loop starts against a control whose state file is already EXITED and whose token has been cleared, `control.cancellation()` is already true, and the class still appends `run.started`/`run.ended` for that run. That produces audit/state inconsistency (events for a run the control already reports as exited) and the one-run guard does not cover it. Track a closed flag and reject `run()` when the control is already closed; keep `close()` idempotent.
- 判断：close无本对象closed门禁，之后run仍追加开始/结束虽控制已退出；统一生命周期并发/幂等入口拒绝。
- 证据：harness-process/src/main/java/io/jharness/process/ControlledAgentRun.java:69

## OCR-0665 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/ProcessLimits.java:15；OCR原严重度：medium
- 计划：R3-02 / P2
- 原意见：第20行直接用 `Charset.forName(System.getProperty("native.encoding", "UTF-8"))` 解析宿主编码存在可复现的失败路径：`System.getProperty` 的默认值只在属性为 null 时生效，若宿主以 `-Dnative.encoding=`（空串）或 `-Dnative.encoding=xxx`（JDK 不支持的名字）启动，`Charset.forName` 会抛出 `IllegalCharsetNameException`/`UnsupportedCharsetException`，异常发生在委托构造器求值阶段，消息仅为 `""` 或非法名，没有说明这是 ProcessLimits 的编码配置问题；JDK 9+ 的模块化/受限启动器也确实可能下发空值。触发条件：任一次构造 `ProcessLimits`（包括 `defaults()` 与 LocalProcessRunner 的调用链）时该属性被覆盖为空或非法值，进程执行整体不可用。建议显式判空/trim 并对非法编码回退到 `StandardCharsets.UTF_8`（或抛出带字段说明的 IllegalArgumentException）。
- 判断：native.encoding非法可使ProcessLimits构造失败，接受显式配置诊断；不可静默UTF8回退后把真实native输出解码错误当成功。
- 证据：harness-process/src/main/java/io/jharness/process/ProcessLimits.java:15

## OCR-0666 · 不采纳

- 位置：harness-process/src/main/java/io/jharness/process/ProcessLimits.java:24；OCR原严重度：low
- 计划：无
- 原意见：异常契约不一致：第23行对 `outputEncoding` 用 `requireNonNull` 抛 `NullPointerException`，而第25-34行其余非法取值统一抛 `IllegalArgumentException`。调用方（例如 CliApplication 构造不可信配置、LocalProcessRunner 及环境组件的默认配置路径）如果按文档只捕获 `IllegalArgumentException` 来降级/报配置错误，传 null 编码时会直接逃逸为 NPE 崩溃。建议把 `outputEncoding == null` 合并进同一个 IAE 分支；同时 `"Invalid process limits"` 未指出是哪个字段越界，排障时无法定位（例如 outputBytes=100 与 maxConcurrent=32 报同样的消息）。
- 判断：null outputEncoding非法构造边界已拒绝，NPE/IAE异常类型偏好不构成当前配置路径漏校验，尚无只捕IAE后错误继续的可达证据。
- 证据：harness-process/src/main/java/io/jharness/process/ProcessLimits.java:24

## OCR-0667 · 不采纳

- 位置：harness-process/src/main/java/io/jharness/process/ProcessLimits.java:38；OCR原严重度：low
- 计划：无
- 原意见：`defaults()` 的 `outputEncoding` 隐式取自当前 JVM 的 `native.encoding`，因此同一份代码在不同宿主/JVM/CI 上得到的 limits 不相等（record 的 equals/hashCode 含 Charset），解码结果也不同：在 `native.encoding=GBK` 的 Windows 上 `defaults()` 会把输出按 GBK 解码，而在 `native.encoding=UTF-8` 的 Linux CI 上按 UTF-8 解码。`defaults()` 已被生产路径（LocalExecutionEnvironment、EnvironmentWorker、CliApplication:1385）与测试（LocalProcessRunnerTest:194、M3RuntimeIntegrationTest:325 用 `ProcessLimits.defaults().timeout()`）共用，一旦宿主进程输出固定为 UTF-8（例如脚本里 `chcp 65001` 或 Java 子进程），断言/解析会随环境产生乱码且难以复现。建议让默认编码的决定点显式化（例如 `defaults()` 提供显式 Charset 重载，或在进程创建处按输出实际编码配置），避免配置对象携带隐式宿主状态。
- 判断：native默认编码是明确本机子进程兼容选择，跨平台不同是预期；已有显式Charset构造器可指定UTF8，不能为record equals稳定强改默认。
- 证据：harness-process/src/main/java/io/jharness/process/ProcessLimits.java:38

## OCR-0668 · 采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalRunControl.java:158；OCR原严重度：medium
- 计划：R1-03 / P2
- 原意见：discover() 用宽松正则 `[a-f0-9-]{36}\.run` 提取 runId（36 个连字符的 `----…----.run`、36 位十六进制乱码都能通过），而 cancel() 用严格 UUID 正则校验同一输入并返回 INVALID_STATE。调用方从 discover() 取到的 id 可能永远无法被取消，形成协议不一致。此外该过滤只匹配文件名，同名目录也会被当作 run 返回。建议 discover() 复用 cancel() 的 UUID 正则，并加 `Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)` 过滤。
- 判断：discover宽松36字符模式且不检查regular file，与cancel严格UUID不一致；统一ID及NOFOLLOW文件类型，保留有界列表。
- 证据：harness-process/src/main/java/io/jharness/process/LocalRunControl.java:158

## OCR-0669 · 采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalRunControl.java:84；OCR原严重度：high
- 计划：R1-03 / P1
- 原意见：监听侧只有单个虚拟线程串行处理连接，且 `setSoTimeout(1000)` 是“每次 read 调用”的超时而非整条消息的总时限：本机任意进程可先发 2 字节长度前缀 0xFFFF，再每 0.9s 发 1 字节，最坏把唯一的监听线程占用约 65535×0.9s。此时 backlog=8 中排队的合法 cancel 连接虽然能建成（connect 成功），但 cancel() 侧 1s 读超时会返回 UNREACHABLE——服务其实仍在运行，只是被饿死；异常又被空 catch 静默吞掉，没有任何日志/审计。建议：每个连接交给有界 Executor 处理（限制并发数），或给单条消息设置绝对截止时间（记录开始时间 + 循环校验 deadline），并对拒绝/超时做可观测记录。
- 判断：串行readUTF只有每次read超时，慢字节可长期占唯一取消监听；限制精确帧长度/绝对deadline及有界并发，防合法取消饥饿。
- 证据：harness-process/src/main/java/io/jharness/process/LocalRunControl.java:84

## OCR-0670 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalRunControl.java:89；OCR原严重度：medium
- 计划：R1-03 / P2
- 原意见：`cancelled.set(true)` 在回写 "REQUESTED" 之前执行。若此后写响应失败（对端提前关闭、RST、写出异常），cancel() 侧会落到 IOException 分支返回 UNREACHABLE，但取消实际上已经生效。调用方可能因此重试，或误判“未取消”而执行破坏性收尾，与“UNKNOWN 状态不得被重放”的原则相冲突。建议在 cancel() 中把“消息已成功写出但读响应失败”映射为独立的、可重试但不可当作未生效的结果（例如新增 UNCERTAIN/DELIVERED），或在协议层用请求 id 做幂等确认，避免将可能已生效的取消报成 UNREACHABLE。
- 判断：发送取消后读响应失败确不证明没生效，UNREACHABLE需明确未知交付语义/GET核对；取消本身幂等，不是工具副作用重放，不禁止安全重复取消。
- 证据：harness-process/src/main/java/io/jharness/process/LocalRunControl.java:89

## OCR-0671 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalRunControl.java:218；OCR原严重度：medium
- 计划：R1-03 / P2
- 原意见：关闭路径不具备失败恢复能力：前面 `closed` 已 CAS 为 true，`server.close()` 抛 IOException 或此处 writeState 失败都会直接向调用方抛出，且无法重试；进程被 kill/崩溃时更是如此。结果是 .run 文件永久停留在 `status=RUNNING` 且仍带合法 token，后续 cancel() 只会一直返回 UNREACHABLE，discover() 也继续把死 run 当作存活 run 暴露，永远不会落到 ALREADY_EXITED。另外文件里写入了 `pid=`，但 cancel() 完全没读取它做存活判断（死代码式字段）。建议：close() 用 try/finally 保证落盘 EXITED（失败时至少删除 .run 或写成无 token 的 EXITED），并在 cancel() 用 pid + ProcessHandle 存活检查把陈旧 RUNNING 归一为 ALREADY_EXITED（陈旧态），而不是无限 UNREACHABLE。
- 判断：close CAS后写EXITED失败不能再次完成状态落盘，可增加残留状态诊断/安全重试；不能仅PID存活推断原run退出（PID可复用），更不能删状态掩盖未知。
- 证据：harness-process/src/main/java/io/jharness/process/LocalRunControl.java:218

## OCR-0672 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalRunControl.java:170；OCR原严重度：medium
- 计划：R1-03 / P1
- 原意见：cancel()/discover() 是静态方法，接收任意 directory，既没有复用构造器里的 toRealPath 规范化与符号链接校验，这里的路径拼接也存在“检查-使用”窗口（Files.exists/isSymbolicLink/size 检查完才 newInputStream）。父目录或文件本身在窗口内被替换为符号链接，就能把 token 的读取重定向到状态目录之外。另一个更实际的风险：读取 token 后直接发给文件里记录的 127.0.0.1:port——若该 run 已崩溃而端口被本机其他进程复用，合法 token 会被发送给非预期的本地监听者。建议先对 directory 做 toRealPath 并断言状态文件仍位于规范目录内，再以 NOFOLLOW 语义（SecureDirectoryStream.newByteChannel 或先打开 FileChannel 校验属性）一次性读取，并核对 host/pid/进程存活后再发送 token。
- 判断：cancel已newInputStream NOFOLLOW拒文件链接，意见说完全无NOFOLLOW不实；父目录别名及陈旧端口身份仍需规范路径/进程startInstant核实，不能仅PID证明身份。
- 证据：harness-process/src/main/java/io/jharness/process/LocalRunControl.java:170

## OCR-0673 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalRunControl.java:113；OCR原严重度：medium
- 计划：R1-03 / P1
- 原意见：临时文件用默认 umask 权限创建，随后才 ownerOnly(0600)；目录也先由 Files.createDirectories 以默认权限（常见 0755）创建，之后才 chmod 0700。POSIX 权限只在 open() 时校验，因此本机其他用户可在“先宽后紧”的窗口内 open() 该文件/目录获取长期有效的 fd，等 token 写入后继续读出凭据并伪造 CANCEL 请求，敏感信息由此泄露。建议创建时即通过 PosixFilePermissions.asFileAttribute(...) 原子设定目录 0700、文件 0600（createDirectories(path, attrs) / createFile(tmp, attrs)），避免任何宽权限窗口。
- 判断：POSIX目录先默认创建再chmod有宽权限窗口，临时文件位于已ownerOnly目录，不能简单断言其它用户均可读取；采用创建时最小权限并验证继承/父根。
- 证据：harness-process/src/main/java/io/jharness/process/LocalRunControl.java:113

## OCR-0674 · 采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalPlanPreviewerTest.java:20；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：敏感信息脱敏路径完全没有被覆盖：虽然注入了 `SecretRedactor`，但两个用例的 `content`/`newText` 都是普通文本，也没有任何针对预览输出的脱敏断言。若 `LocalPlanPreviewer` 的 `sanitizer.sanitize(...)` 未覆盖 `diff`/`path`，凭据会原样出现在预览结果中而该测试依然通过。建议新增用例：写入或编辑内容包含形如 `password=...`、`api_key=...` 的敏感串，断言返回的 `diff` 中不含明文且包含脱敏占位符。
- 判断：预览测试无秘密sentinel，补diff/path及模型预览全对象脱敏回归，使用已知secret而非假设任意password文本自动识别。
- 证据：harness-process/src/test/java/io/jharness/process/LocalPlanPreviewerTest.java:20

## OCR-0675 · 采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalPlanPreviewerTest.java:65；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：shell 预览的"未执行"仅通过返回字段断言，缺少对工作区的实际校验：若预览器误执行了命令，`a.txt` 会被改写为 `changed`，本测试仍会通过。建议在 shell 预览之后补一行 `assertEquals("before\n", Files.readString(root.resolve("a.txt")));`，同时也可断言 `new.txt` 仍不存在，以证明预览全程无副作用。
- 判断：shell预览只看executed字段，缺实际文件未改断言；加入副作用marker防“报告未执行但已执行”。
- 证据：harness-process/src/test/java/io/jharness/process/LocalPlanPreviewerTest.java:65

## OCR-0676 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalPlanPreviewerTest.java:76；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：文件系统边界覆盖过窄：只验证了 `../x` 这一种相对路径越界。绝对路径（如 `Path.of("/tmp/x")`）、workspace 内指向外部目录的符号链接、以及 `..%2f` 之类编码变体都未覆盖，路径校验回归时测试不会失败。建议补充这几类输入，均断言抛出 `HarnessException` 且 `code()` 为 `PATH_DENIED`。
- 判断：绝对路径/链接边界可补直接preview入口回归，%2f是文件名而非URL编码，不要求对普通路径额外URL解码造成错误越界解释。
- 证据：harness-process/src/test/java/io/jharness/process/LocalPlanPreviewerTest.java:76

## OCR-0677 · 采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalPlanPreviewerTest.java:39；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：diff 断言过于宽松：只检查包含 `+after`，即使实现丢失了 `-before` 删除行、上下文行，或该子串只是偶然出现，测试仍会通过，无法保证 diff 结构正确。建议同时断言包含 `-before`，或直接与 `UnifiedDiff.between("a.txt", "before\n", "after\n")` 的期望输出做整体比较，并顺带断言 `truncated` 为 false。
- 判断：diff只+after不足验证删除行/上下文，补手写预期结构与截断；避免仅调用同一UnifiedDiff生成期望自我验证。
- 证据：harness-process/src/test/java/io/jharness/process/LocalPlanPreviewerTest.java:39

## OCR-0678 · 采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalPlanPreviewerTest.java:82；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：取消路径完全未被验证：所有 `preview(...)` 调用都传 `CancellationToken.NONE`，而实现中入口和生成 diff 前各有一次 `throwIfCancelled()`。若这两处检查被回归删除，测试不会发现。建议新增用例：用 `() -> true` 构造已取消的 token 调用 preview，断言抛出 `HarnessException` 且 `code()` 为 `CANCELLED`，并且工作区未被修改。
- 判断：preview全用NONE无法证明取消检查，补预取消和处理中取消且文件不变。
- 证据：harness-process/src/test/java/io/jharness/process/LocalPlanPreviewerTest.java:82

## OCR-0679 · 不采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalPlanPreviewerTest.java:64；OCR原严重度：low
- 计划：无
- 原意见：这里直接对 `reason` 调用 `toString()`，字段缺失时会抛 NPE 而不是给出清晰的断言失败；同时断言耦合了中文文案片段，文案调整会造成测试误报。建议先断言 `reason` 键存在（或断言结构化的原因/状态码），再校验内容。
- 判断：缺reason使NPE本身仍令测试失败，不会假通过；中文原因文案断言的风格不独立立项，可新增结构码时顺带更新。
- 证据：harness-process/src/test/java/io/jharness/process/LocalPlanPreviewerTest.java:64

## OCR-0680 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalPlanPreviewer.java:34；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：参数解析缺少类型/存在性校验：`path` 直接强转 `(String)`（null 则经 ExecPath→Values.required 抛 IllegalArgumentException），`newText` 缺失/显式 null 时 `replacement` 为 null 使 `before.replace(old, null)` 抛 NPE，`getOrDefault("expectedOccurrences", 1)` 在键存在但值为 null 时返回 null、`.intValue()` 抛 NPE、值为非 Number 时强转抛 ClassCastException。`--plan` 离线路径（CliApplication#offlinePlan 直接构造 ToolInvocation）与模型输入都不经过 inputSchema 校验，这些 RuntimeException 会绕过 HarnessException 统一错误码，使整份计划预览中断而不是返回可诊断的错误。建议对 path/oldText/newText 先做 `instanceof String` 校验、对 expectedOccurrences 先取 Object 再判类型，非法值抛 INVALID_ARGUMENT/INVALID_EDIT。
- 判断：离线plan直接preview存在未经过schema的字段裸转，接受严格校验；模型管线实际校验schema，意见称全部模型不校验不成立。
- 证据：harness-process/src/main/java/io/jharness/process/LocalPlanPreviewer.java:34

## OCR-0681 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalPlanPreviewer.java:62；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：版本守护可被静默绕过：传入非 String 的 `expectedVersion`（如数字、对象、null）时 `instanceof` 为 false，冲突检查被完全跳过，预览会输出“可预览”。由于离线计划路径未做 schema 校验，`"expectedVersion": 123` 即可绕过守护。应在键存在但类型非 String 时直接拒绝，而不是忽略。
- 判断：expectedVersion非String被忽略导致预览可信度下降，接受键存在即类型校验；preview不执行，不能称已绕过实际写入guard。
- 证据：harness-process/src/main/java/io/jharness/process/LocalPlanPreviewer.java:62

## OCR-0682 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalPlanPreviewer.java:36；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：1 MB 限制只在 `after` 上、且在读取/替换/diff 之后才生效：这里先整文件读取（before 没有任何本类的大小上限，完全依赖 FileSystemView 实现），随后第 51-59 行做全量 occurrence 扫描与全量 `replace`，第 67 行对完整内容生成完整 diff，最后才截断到 8 KB。`cancellation` 只在入口和第 66 行检查，上述耗时/耗内存阶段无法中断。若 FileSystemView 为远程或自定义实现而未做读取上限，大文件会拖垮预览流程。建议先基于大小/版本元数据拒绝超限文件，并在扫描循环里定期 `throwIfCancelled()`。
- 判断：preview整读/replace/diff后截断，接受统一有界读取与取消点；内建LocalWorkspace已1MiB，不能以任意违约FileSystemView推断默认无限大。
- 证据：harness-process/src/main/java/io/jharness/process/LocalPlanPreviewer.java:36

## OCR-0683 · 采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalPlanPreviewer.java:60；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：两点问题：(1) 这里用 UTF-16 char 数比较 1 MiB，而 LocalWorkspace 的写入限制按 UTF-8 字节数计算（`WRITE_LIMIT`，1 MiB），多字节内容会出现“预览判定可写、实际写入报 FILE_LIMIT”的不一致；(2) `after == null`（write_file 的 content 缺失/null）被归为 FILE_LIMIT，错误码掩盖了参数错误。建议按 UTF-8 字节数校验，并对 null 抛 INVALID_ARGUMENT。
- 判断：预览after按UTF16 char限而写按UTF8 bytes限，中文可预览通过实际拒；统一字节限与参数错误分类。
- 证据：harness-process/src/main/java/io/jharness/process/LocalPlanPreviewer.java:60

## OCR-0684 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalPlanPreviewer.java:65；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：版本快照不是原子的：第 34 行 `versionOf` 与第 35 行 `readText` 是两次独立调用，读取到的内容与最初记录的 `version` 可能不属于同一快照；若 versionOf 不是内容散列（例如远程视图用 mtime/size），这里的二次校验只能发现“当前版本≠旧版本”，无法发现 ABA（A→B→A）——此时 diff 内容来自 B，却回报 beforeVersion=A，预览结论与执行时版本守护可能不一致。建议使用返回“内容+版本”的单次守护读取（FileSystemView 可扩展为 readText 返回 FileChange 形式），或在同一临界区内完成读取与校验。
- 判断：version/read/version的ABA使预览内容/hash不一致，归并同次读取版本快照；不能宣称锁能约束不合作外部写者。
- 证据：harness-process/src/main/java/io/jharness/process/LocalPlanPreviewer.java:65

## OCR-0685 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalPlanPreviewer.java:87；OCR原严重度：low
- 计划：R1-02 / P2
- 原意见：捕获 IOException 后丢弃了原始异常（既未作为 cause 传递，也未记录），文件权限/编码/远程读取失败时只剩固定的 "Preview read failed"，无法定位真实原因。HarnessException 已提供 cause 构造器，建议 `new HarnessException("PREVIEW_IO", "Preview read failed", ex)`。
- 判断：Preview IO丢cause可改善安全诊断，cause可能含路径/凭据须经统一脱敏，不直接原样回HTTP。
- 证据：harness-process/src/main/java/io/jharness/process/LocalPlanPreviewer.java:87

## OCR-0686 · 采纳

- 位置：harness-process/src/main/java/io/jharness/process/ProcessToolsPlugin.java:155；OCR原严重度：high
- 计划：R1-02 / P1
- 原意见：artifact 落盘发生在命令已经执行完成之后，且未做任何异常兜底：`LocalArtifactStore.put` 在内容超过 4 MiB 时抛 `HarnessException("ARTIFACT_LIMIT")`，sessionId 不合规时抛 `INVALID_SESSION_ID`，写盘失败时抛 `IOException`。这些异常会从 `CommandTool.execute` 逃逸到 `DefaultToolPipeline.run/failure`，由于 `entered && sideEffectLevel == SYSTEM_IMPACT`，最终状态被改写成 `ToolStatus.UNKNOWN`，已完成命令的 exitCode/stdout/stderr/residualPids/sideEffectUncertain 证据全部丢失，上层只能看到一个不可重放的 UNKNOWN。触发条件：runner 的 `outputBytes` 允许的输出超过 4 MiB（或 artifact 目录不可写/被删除），命令本身却已真实产生副作用。建议在 `artifacts.put` 外层捕获 `IOException | HarnessException`，退化为保留内联截断预览并标注 artifact 失败原因，确保返回的仍是 runner 的原始 ToolResult（状态与证据不丢）。
- 判断：命令已完成后artifact失败使管线只余UNKNOWN并丢原退出/残留事实；保留原ToolResult与独立artifact失败，不伪造制品成功。
- 证据：harness-process/src/main/java/io/jharness/process/ProcessToolsPlugin.java:155

## OCR-0687 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/ProcessToolsPlugin.java:119；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：参数解析完全没有防御：`args.get("argv")` 为 null 时强转 `List<?>` 抛 NPE，元素不是 String 时 `String.class::cast` 抛 ClassCastException，空 argv 会让 runner 内 `argv.getFirst()` 抛 NoSuchElementException，`mode`/`cwd` 为 null 或 `timeoutMs` 非 Number 时分别在 `Mode.valueOf(null)`、`ExecPath(null)`、`(Number)` 强转处抛 NPE/ClassCastException。`catch (IllegalArgumentException)` 只能兜住非法枚举值，其余 RuntimeException 会逃出工具，被管线记成 TOOL_FAILED/UNKNOWN，而不是结构化的 INVALID_COMMAND（该工具是 public 的 AgentTool，直接调用或替换实现时不经过 JSON Schema 校验）。建议先显式校验 argv 为非空 List<String>、mode/cwd 为 String、timeoutMs 为 Number 且落在 [1, 3600000]，不满足时直接返回 `ToolResults.failure(..., "INVALID_COMMAND")`，并把 catch 扩到 RuntimeException 兜底。
- 判断：公开CommandTool直接调用字段强转无结构校验，但正常管线已有schema；与终端等入口统一参数失败阶段，禁止catch所有RuntimeException把已执行失败误报未执行。
- 证据：harness-process/src/main/java/io/jharness/process/ProcessToolsPlugin.java:119

## OCR-0688 · 不采纳

- 位置：harness-process/src/main/java/io/jharness/process/ProcessToolsPlugin.java:95；OCR原严重度：medium
- 计划：无
- 原意见：Shell 模式下 schema 与实际执行语义不一致：`LocalProcessRunner.nativeArgv` 对 CMD/POWERSHELL/SH 只取 `request.argv().getFirst()` 作为整条命令串，其余 argv 元素被静默丢弃，而这里对四种 mode 统一声明 `minItems 1 / maxItems 256`。触发条件：模型在 CMD/SH 模式下按数组拆分传参（如 ["echo", "hello"]），人类批准后只有 "echo" 被执行，剩余参数静默消失，属于“已批准意图 ≠ 实际执行”的隐蔽偏差。建议在 schema/description 中限定 Shell 模式 argv 必须为单元素，或在 `execute` 中对非 ARGV 模式的多元素 argv 直接拒绝。
- 判断：CommandRequest构造明确mode非ARGV且argv.size!=1拒绝，ProcessToolsPlugin捕IAE返回INVALID_COMMAND；不存在只执行第一个而静默丢其它已批准参数。
- 证据：harness-process/src/main/java/io/jharness/process/ProcessToolsPlugin.java:95

## OCR-0689 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/ProcessToolsPlugin.java:177；OCR原严重度：medium
- 计划：R5-01 / P2
- 原意见：模型可见内容用 `Map.toString()` 拼装（`{exitCode=0, stdout=..., ...}`），既不是合法 JSON，也没有转义：命令输出本身含换行、`=`、`}` 时，stdout 会与其它键值在文本层混在一起，模型/下游解析容易误读字段边界。另外此白名单丢掉了 runner value 中的 `reason` 与 `limitations`，非成功场景下模型无法区分 TIMEOUT/PROCESS_LIMIT/OUTPUT_IO（UNKNOWN 时 error 只剩 CLEANUP_INCOMPLETE）。建议改用稳定的 JSON 序列化输出 modelContent，并把 `reason` 一并暴露。
- 判断：model.toString非JSON且缺reason/limitations降低结构可读，接受统一有界结构化模型摘要，保留原结果事实及未信任输出边界。
- 证据：harness-process/src/main/java/io/jharness/process/ProcessToolsPlugin.java:177

## OCR-0690 · 不采纳

- 位置：harness-process/src/main/java/io/jharness/process/ProcessToolsPlugin.java:181；OCR原严重度：low
- 计划：无
- 原意见：该 catch 丢弃了 cause 与消息，把所有 IllegalArgumentException 统一映射成无信息的 INVALID_COMMAND，其中既包含模型传错枚举值，也可能包含 ExecPath 对非法 cwd 的拒绝（本应体现为路径越界/参数非法两类不同信号），排障时无法区分。建议至少记录/回传 `ex.getMessage()` 或按参数名区分错误码（如 INVALID_MODE / INVALID_CWD / INVALID_TIMEOUT）。
- 判断：固定INVALID_COMMAND避免把原始命令/路径参数漏入异常；需诊断时安全字段分类即可，不采纳原样回传ex.message。
- 证据：harness-process/src/main/java/io/jharness/process/ProcessToolsPlugin.java:181

## OCR-0691 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalRunControlTest.java:79；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 79-82 行存在非确定性且可能泄露取消凭据：`run` 关闭后服务端 socket 已释放，而这里把包含真实 `token` 和已释放临时端口的原始快照写回，再期望 `UNREACHABLE`。该 ephemeral 端口可能被本机其它进程重新绑定——若新监听者直接断开或不应答，测试仍得到 `UNREACHABLE`（假通过），若它回包则返回 `INVALID_STATE` 导致随机失败；更严重的是此时会把真实取消凭据发给无关的本机监听者。建议构造不含真实 token 的状态（例如用新随机 64 位 hex 值），并显式断言目标端口未监听（或让实现可注入 socket 工厂）后再断言 `UNREACHABLE`，不要依赖“端口不会被复用”。
- 判断：复写已退出原状态到释放端口有复用竞态，接受受控监听/随机失效token夹具；其token仅测试随机值，不是产品真实账号秘密。
- 证据：harness-process/src/test/java/io/jharness/process/LocalRunControlTest.java:79

## OCR-0692 · 不采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalRunControlTest.java:89；OCR原严重度：medium
- 计划：无
- 原意见：第 89 行的替换结果未做校验：如果状态格式或版本字段写法变化（如增加空格、版本升级），`replace("version=1", "version=2")` 会变成 no-op，后续 `cancel` 将走“连接已关闭端口”的 `UNREACHABLE` 分支，测试失败原因与“版本非法”完全无关，难以定位。建议先断言替换确实发生（`assertNotEquals(original, tampered)` / `assertTrue(tampered.contains("version=2"))`），或解析 `Properties` 后 `setProperty("version", "2")` 再写回。
- 判断：替换no-op时最终期望INVALID_STATE但实际UNREACHABLE会失败，不是假通过；预检可改善报错不单列功能修复。
- 证据：harness-process/src/test/java/io/jharness/process/LocalRunControlTest.java:89

## OCR-0693 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalRunControlTest.java:41；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 41-44 行先 `waitFor` 再读取 stdout，且完全没有处理 stderr（未 `redirectErrorStream`，也未排空）。若子 JVM 向 stderr 输出较多内容（启动告警、异常栈等）超过管道缓冲区，子进程会阻塞在写管道上导致 `waitFor` 超时，测试结果依赖机器负载；超时后第 50 行只调用 `destroyForcibly()` 而不 `waitFor`，子进程可能残留到测试结束（在 Windows 上还可能阻塞 @TempDir 清理或持有状态目录句柄）。建议改为 `.redirectErrorStream(true).start()`（或并发泵出 stderr）并在失败信息中附带输出，`finally` 中先 `destroyForcibly()` 再 `waitFor(...)`。此外 `new String(bytes)` 使用平台默认字符集，而状态与客户端输出均按 UTF-8 编码，应显式传入 `StandardCharsets.UTF_8` 以避免非 UTF-8 默认字符集环境下解码错误。
- 判断：子JVM先wait后读且无stderr泵、强杀未wait，接受有界双流排空及退出确认；REQUESTED是ASCII，默认charset在此不是实际乱码根因。
- 证据：harness-process/src/test/java/io/jharness/process/LocalRunControlTest.java:41

## OCR-0694 · 采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalProcessRunnerTest.java:162；OCR原严重度：high
- 计划：R0-03 / P1
- 原意见：无 deadline 的忙等会导致测试挂死而不是失败：`started` 只在 LocalProcessRunner.execute 的轮询循环内被调用（进程成功启动后才进入该循环），一旦 fixture 启动失败、路径被拒绝或 execute 在启动前抛异常/提前返回，该 lambda 永不执行，`while (!started.get())` 将无限循环，整个测试套件卡死而不会给出失败原因（mvn/gradle 超时才退出）。随后的固定 `Thread.sleep(200)` 同样使 PROCESS_LIMIT 断言依赖机器快慢。建议改为带超时的等待并在超时后 fail。
- 判断：while(!started)无deadline，若fixture启动前失败会永久等待；等待future/信号必须有界并保留失败原因。
- 证据：harness-process/src/test/java/io/jharness/process/LocalProcessRunnerTest.java:162

## OCR-0695 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalProcessRunnerTest.java:204；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：这条断言恒为真，无法发现取值泄漏：LocalProcessRunner 固定把 `environment` 写成 `Map.of("names", ..., "values", "omitted")`，values 永远是字面量 "omitted"，因此 environment.toString() 永远不可能包含 PATH 的真实值，断言对“环境变量值是否泄漏”没有任何覆盖。另外 `assertFalse(result.toString().contains("very-private-value"))` 依赖 ToolResult record 的默认 toString 间接覆盖 value/modelContent，一旦改成自定义 toString 就失去保护。建议显式断言 values 为 "omitted"，并对 stdout/stderr/modelContent 单独断言不包含机密值。
- 判断：toString脱敏断言可被record实现更改弱化，补各输出字段直接sentinel检查；现有环境字段检验不是“恒真”漏洞，泄漏回归会改变输出。
- 证据：harness-process/src/test/java/io/jharness/process/LocalProcessRunnerTest.java:204

## OCR-0696 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalProcessRunnerTest.java:66；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：1000ms 的请求期限包含 fixture 的 JVM 启动时间（ProcessProbe 通过 `java -cp ...` 启动），在负载较高的 CI 上很可能在写满 512 字节之前就超时，导致 `assertEquals(true, truncated)` 偶发失败（超时一定会成立，但截断不一定）。建议把期限放大到能稳定覆盖 JVM 启动（如 3~5s），或先用 `stdin`/`echo` 探测 fixture 可启动后再测截断。
- 判断：1000ms同时验证JVM冷启动/截断/超时易受负载影响，拆准备和实际短期限用例，不能只放大产品默认超时掩盖行为。
- 证据：harness-process/src/test/java/io/jharness/process/LocalProcessRunnerTest.java:66

## OCR-0697 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalProcessRunnerTest.java:290；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：这段清理逻辑有两类可靠性问题：1）3 秒窗口结束后直接 `return`，若 fixture 子进程确实晚于窗口才注册（注释里承认存在这种竞态），该进程既不会被清理也不会被发现，会长时间存活并污染后续测试（例如占用并发槽位、让进程树断言看到不属于自己的子进程）；2）轮询里的 `Files.readAllLines` 可能抛 IOException、`Long.parseLong(identity.get(0))` 可能抛 NumberFormatException，而这段代码运行在 `finally` 中，异常会覆盖测试真正的断言失败原因。建议超时后 fail（或至少在窗口内持续重试并给出可见告警），把读取异常视为“尚未写完”继续重试，并对 pid 解析做防御。
- 判断：finally读pid身份不完整会覆盖主失败，接受受控注册握手与残留明细；未写pid也可能确未启动，不可一律认定泄漏或无界等待。
- 证据：harness-process/src/test/java/io/jharness/process/LocalProcessRunnerTest.java:290

## OCR-0698 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalProcessRunnerTest.java:119；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：runner 未放入 try-with-resources：`execute` 调用位于 try 之外，一旦它抛异常（fixture 启动失败、线程被中断等），finally 不会执行，`runner.close()` 被跳过，泄漏虚拟线程与仍在运行的 `block` 进程，影响后续测试。另外 finally 里的 `handle.get().onExit().get(5, TimeUnit.SECONDS)` 超时会抛 TimeoutException 并从 finally 冒出，掩盖真正的断言失败。建议把 runner 纳入 try-with-resources（或把 execute 移入 try），并让 onExit 等待的失败不替换主异常。
- 判断：runner.execute在try外，异常不执行清理；try-with-resources及抑制次级清理异常，退出未确认须显式失败。
- 证据：harness-process/src/test/java/io/jharness/process/LocalProcessRunnerTest.java:119

## OCR-0699 · 不采纳

- 位置：harness-process/src/test/java/io/jharness/process/LocalProcessRunnerTest.java:109；OCR原严重度：low
- 计划：无
- 原意见：该断言绑定的是产品内部的人类可读英文文案：LocalProcessRunner 无条件拼出同一句 limitations（含 “No Job Object”），文案任何措辞调整都会让测试失败，而断言通过也不能说明平台真实能力（Windows 上同样是这句话，语义并不准确）。建议改为断言结构化字段（cleanupStatus / trackedPids / residualPids / observedClean）来表达“清理范围诚实”的意图，文案只做包含关键字的弱校验或干脆不校验。
- 判断：同时已断言trackedPids/cleanupStatus，No Job Object文案明确防夸大保证，不能仅因文案耦合删除诚实限制的回归。
- 证据：harness-process/src/test/java/io/jharness/process/LocalProcessRunnerTest.java:109

## OCR-0700 · 采纳

- 位置：harness-process/src/main/java/io/jharness/process/TerminalToolsPlugin.java:74；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：start 的 schema 只约束 argv 为 1..256 个字符串，但 CommandRequest 的构造校验要求非 ARGV 模式（CMD/POWERSHELL/SH）时 argv 必须恰好 1 个元素，且 sum(argv 长度) <= 32768、不得含 '\0'、首元素不得为空白。因此形如 {"mode":"SH","argv":["bash","-lc","id"]} 或超长 argv / 含 \0 的 argv 都能通过 schema 校验（prepare 阶段 validate 通过），却在 execute 中由 CommandRequest 抛出 IllegalArgumentException（CommandRequest.java:25-34），造成“schema 合法但执行必然失败”的校验缺口。建议用 oneOf 表达 mode 与 argv 形状的联合约束，并给 items 增加 maxLength，使非法参数在 prepare 阶段就被 SCHEMA_MISMATCH 拒绝。
- 判断：terminal_start schema允许CommandRequest拒绝的shell多argv/NUL/总长，需在主体之前完成语义校验并与参数schema一致；现有schema子集未必支持oneOf，不能直接照搬。
- 证据：harness-process/src/main/java/io/jharness/process/TerminalToolsPlugin.java:74

## OCR-0701 · 采纳

- 位置：harness-process/src/main/java/io/jharness/process/TerminalToolsPlugin.java:198；OCR原严重度：high
- 计划：R3-04 / P1
- 原意见：execute 只捕获 HarnessException：CommandRequest.Mode.valueOf、new CommandRequest(...)（多元素 argv + 非 ARGV 模式、argv 总长 > 32768、含 '\0'、首元素空白）、new ExecPath(cwd) 等抛出的是 IllegalArgumentException；第 131 行的 (String) args.get("id")、(List<?>) args.get("argv")、String.class::cast 在绕过 schema 的直接调用下还会抛 ClassCastException/NPE。这些异常会逃出 execute，被 DefaultToolPipeline 统一折算成 TOOL_FAILED，且因为 body 已进入（entered=true）而 SYSTEM_IMPACT 工具会被强制改写为 ToolStatus.UNKNOWN（DefaultToolPipeline.java:231），把一个纯参数校验失败标记成“副作用不确定”，既无法定位原因也会阻止安全重试。参照同模块 ProcessToolsPlugin 的做法，应把 IllegalArgumentException（及类型转换异常）映射为结构化的 INVALID_ARGUMENT 失败。
- 判断：终端execute未捕CommandRequest的IAE，纯参数拒绝被管线记UNKNOWN；前置构造/语义验证且保证未进入副作用时确定失败。
- 证据：harness-process/src/main/java/io/jharness/process/TerminalToolsPlugin.java:198

## OCR-0702 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/TerminalToolsPlugin.java:195；OCR原严重度：medium
- 计划：R5-01 / P2
- 原意见：modelContent 直接用 value.toString()、display 直接用 value，把 terminal_log 返回的 text（单次最多 65536 字符）原样复制到 value/content/display 三处。一次 log 调用就会在 session 事件、模型上下文和 CLI 输出中各写入数十 KB，重复轮询 log 会快速膨胀会话与上下文；而同模块 ProcessToolsPlugin 对 stdout/stderr 只给 1024 字符预览并把全文落到 ArtifactStore（ProcessToolsPlugin.java:148-172）。建议此处对 modelContent（以及必要时 display）做长度截断的预览，长文本通过 artifact 引用返回。
- 判断：terminal_log最多65536仍在value/modelContent/display重复，接受模型预览/制品引用与总量限额；当前有界文本不是无限泄漏。
- 证据：harness-process/src/main/java/io/jharness/process/TerminalToolsPlugin.java:195

## OCR-0703 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/ProcessProbe.java:49；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：tree 模式（行 42-53）启动子进程后既没有 waitFor 也没有在异常/中断路径上销毁它：若 `Files.writeString` 抛错（非法路径、目录不存在、无写权限）或 `Thread.sleep` 被中断，子进程会继续存活最长 30s 且父进程已丢失句柄，只能依赖外部 runner 的进程树回收（LocalProcessRunnerTest 中就是靠 cancel 顺序成功才断言子进程已死）。建议用 try/finally 包住 pid 文件写入与 sleep，并在 finally 中 `child.destroyForcibly()`（必要时 `child.waitFor(超时, TimeUnit)`），保证任何退出路径都不残留子进程与 pid 文件。
- 判断：ProcessProbe tree启动后异常可留子进程；需有异常路径清理与身份记录，但正常父死后保留孩子是进程树测试目的，不应让fixture自行清理掩盖runner失败。
- 证据：harness-process/src/test/java/io/jharness/process/ProcessProbe.java:49

## OCR-0704 · 不采纳

- 位置：harness-process/src/test/java/io/jharness/process/ProcessProbe.java:37；OCR原严重度：medium
- 计划：无
- 原意见：flood 模式（行 36-39）是无终止条件的忙循环，且 PrintStream 会吞掉 IOException：一旦消费者关闭管道（例如测试读取端关闭但进程未被杀），`println` 不再阻塞而是立即失败返回，循环将以 100% CPU 空转直到超时被外部杀死，期间每轮还各新建两个 128 字符 String，造成无意义的分配压力。建议在循环里检测 `System.out.checkError()` / `System.err.checkError()` 以及中断标志并退出。
- 判断：flood故意持续双流压力用于验证超时/强杀，改关闭管道即自行退出可能削弱清理用例；无当前失控证据，不单独修复测试设计。
- 证据：harness-process/src/test/java/io/jharness/process/ProcessProbe.java:37

## OCR-0705 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/ProcessProbe.java:87；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：unicodeEcho 的编译器定位（行 107-113）不可靠：`System.getenv("SystemRoot")` 在部分受限环境/裁剪镜像中可能为 null，`Path.of(null, ...)` 会直接抛 NPE 而不是可诊断的错误；同时路径硬编码为 `Microsoft.NET\Framework64\v4.0.30319\csc.exe`，32 位 Windows 只有 `Framework` 目录，非默认 Framework 安装位置或未安装 .NET Framework 的机器会直接 IOException。建议先显式校验 SystemRoot 是否为空并给出明确异常，再在 `Framework`/`Framework64` 两个目录中探测 csc.exe（或使用 Roslyn/dotnet 构建），找不到时抛出包含已探测路径的异常。
- 判断：Windows Unicode接收器依赖Framework64，当前支持平台AMD64已验收；补清晰前置条件诊断，不扩张至未支持32位/无.NET平台且不加skip。
- 证据：harness-process/src/test/java/io/jharness/process/ProcessProbe.java:87

## OCR-0706 · 采纳

- 位置：harness-process/src/test/java/io/jharness/process/ProcessProbe.java:100；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：编译子进程的输出流从未被消费也从未关闭（行 114-129）：`redirectErrorStream(true)` 把编译器 stdout/stderr 合并到 getInputStream()，但没有读取，一旦编译器输出超过管道缓冲区（警告/错误较多时会发生）子进程会阻塞在该写入上，`waitFor(20s)` 超时后抛出统一的 "Unicode receiver compilation failed"，真正的编译诊断信息全部丢失；编译失败/成功后 `compile` 的流也没有 close，在 Windows 上会持有句柄影响临时目录清理。建议把输出重定向到 root 下的日志文件，或读取流并在 IOException 消息中携带输出内容，并在 finally 中关闭 `compile.getInputStream()` 等资源。
- 判断：编译器合并输出后未消费，可能管道满超时且丢诊断；重定向有界日志并总是关闭/等待编译进程。
- 证据：harness-process/src/test/java/io/jharness/process/ProcessProbe.java:100

## OCR-0707 · 不采纳

- 位置：harness-process/src/test/java/io/jharness/process/ProcessProbe.java:15；OCR原严重度：low
- 计划：无
- 原意见：入口缺少参数长度校验：`args[0]`（行 15）以及 echo 分支的 `args[1]`/`args[2]`（行 17、19）、tree 分支的 `args[1]`（行 49、51）都是直接下标访问，模式参数缺失或多传/少传时会抛 AIOOBE 或 NumberFormatException，堆栈里看不出是哪种调用形态出错，排查成本高。建议在 switch 前校验 args.length 并在各分支校验所需参数个数，抛出带模式名与期望参数个数的 IllegalArgumentException（default 分支也应带上非法的模式值）。
- 判断：ProcessProbe只由固定测试参数启动，非法参数直接失败合理；增加参数提示非当前产品缺陷。
- 证据：harness-process/src/test/java/io/jharness/process/ProcessProbe.java:15

## OCR-0708 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/RunStatePermissionsTest.java:23；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：该断言只校验了异常类型，未校验失败原因。`LocalRunControl` 构造函数在 `ownerOnly()` 中同样会抛 `IOException("Owner-only state permissions unsupported")`（例如在不支持 POSIX/ACL 的文件系统上，见 LocalRunControl:144），因此即使“专用目录校验”逻辑失效，本断言也会通过，形成假阳性。另外这里只回读了 `user.txt`，无法证明目录未被部分改造（例如实现失败前已创建 `*.tmp` 文件）。建议匹配异常消息并断言目录内容未被改动。
- 判断：普通目录拒绝只assert IOException可能原因不对，补错误码/消息及目录未变；保留平台权限不支持也失败的严格门禁。
- 证据：harness-process/src/test/java/io/jharness/process/RunStatePermissionsTest.java:23

## OCR-0709 · 不采纳

- 位置：harness-process/src/test/java/io/jharness/process/RunStatePermissionsTest.java:49；OCR原严重度：medium
- 计划：无
- 原意见：受限容器/未开启符号链接权限的 CI 上，`Files.createSymbolicLink` 会抛 `UnsupportedOperationException`/`AccessDeniedException`，测试直接报错而不是跳过，导致这条安全断言在部分环境不可复现；Windows 分支同理依赖外部 `cmd.exe mklink`（第 40 行的 assertTrue 会硬失败）。建议用 JUnit `Assumptions.assumeTrue/abort` 在这些环境降级为跳过。
- 判断：三平台安全验收明确0跳过，不支持链接/权限应环境失败显式记录，不能按建议Assumptions跳过制造通过。
- 证据：harness-process/src/test/java/io/jharness/process/RunStatePermissionsTest.java:49

## OCR-0710 · 不采纳

- 位置：harness-process/src/test/java/io/jharness/process/RunStatePermissionsTest.java:53；OCR原严重度：medium
- 计划：无
- 原意见：若第 49 行 `createSymbolicLink`（或 Windows 分支的 mklink）失败，`linked` 并不存在，finally 中的 `Files.delete(linked)` 会抛 `NoSuchFileException`，把原始的断言失败/真实根因（链接不支持、权限不足）覆盖掉，排查时看不到真正原因。应使用 `deleteIfExists`。
- 判断：链接创建在try/finally之前，若createSymbolicLink/mklink失败不会进入该finally，声称delete覆盖创建异常不成立。
- 证据：harness-process/src/test/java/io/jharness/process/RunStatePermissionsTest.java:53

## OCR-0711 · 不采纳

- 位置：harness-process/src/test/java/io/jharness/process/RunStatePermissionsTest.java:63；OCR原严重度：high
- 计划：无
- 原意见：`Files.getFileAttributeView(..., AclFileAttributeView.class)` 在不支持 ACL 的文件系统上返回 `null`（FAT、部分网络挂载），此处直接解引用会抛 NPE，而不是给出有意义的失败/跳过信息。此外 `getAcl()` 在 Windows 上会包含从父目录继承的 ACE（SYSTEM/Administrators 等），即使实现正确写入 owner-only DACL，该 `allMatch` 也可能判定失败；应过滤 `AclEntryFlag.INHERITED` 或 `acl.getAcl()` 中不继承的 ACE，并断言文件属主为当前用户。
- 判断：无ACL文件系统非支持安全能力应失败；建议过滤继承ACE会放松owner-only门禁，且JDK AclEntryFlag没有建议中的INHERITED标志，不采纳。
- 证据：harness-process/src/test/java/io/jharness/process/RunStatePermissionsTest.java:63

## OCR-0712 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/RunStatePermissionsTest.java:60；OCR原严重度：medium
- 计划：R1-03 / P1
- 原意见：这里只校验了状态文件的最终权限，未校验状态目录 `run.stateFile().getParent()` 的权限（实现期望 `rwx------`）和文件属主；也就是说凭据目录仍可能对其它用户可列举、可删除/替换状态文件，测试无法发现。另外实现是 `createFile` 后再 chmod（LocalRunControl:113-114），存在短暂按 umask 默认权限存在的窗口，当前断言只观察终态，看不到这段 TOCTOU 暴露。建议补断言目录权限与属主。
- 判断：当前测试只查文件终态权限，补目录/owner及创建最小权限回归；临时文件已在受限目录，暴露影响须结合673路径核实。
- 证据：harness-process/src/test/java/io/jharness/process/RunStatePermissionsTest.java:60

## OCR-0713 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java:53；OCR原严重度：high
- 计划：R2-04 / P2
- 原意见：任务条目只增不减：`tasks` 在本类中只有 put/get/size/values（第 19、79、88、186、194 行），第 53 行的上限统计的是历史全量条目，而不是活跃任务。触发条件：同一会话内累计执行 32 次命令（包括早已 EXITED/STOPPED 的），第 33 次起 `start()` 永远返回 TERMINAL_LIMIT，即使当前没有任何进程在跑；同时每个已结束的 Task 仍持有最多 256K 的 StringBuilder、observed 句柄表和 artifact 引用，形成内存/生命周期泄漏。建议在任务终结（EXITED 或 stop 后 persist 完成）时从 `tasks` 移除，或第 53 行改为只统计 `state.equals("RUNNING")` 的条目。
- 判断：tasks.size32计历史条目，长期会话无法新开终端，需明确历史/活跃双限额与终态有界回收；不能直接移除丢日志/制品或只计活跃使历史无界。
- 证据：harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java:53

## OCR-0714 · 采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java:307；OCR原严重度：high
- 计划：R1-03 / P1
- 原意见：状态写入存在 check-then-act 竞争：watcher 在第 307-308 行以非原子的「读 state 再赋值 EXITED」结束任务，而 `stop()`（第 338-341 行）是在 `synchronized (this)` 内检查并设置 STOPPED/TIMED_OUT/PROCESS_LIMIT。触发条件：进程刚好退出时另一线程（用户 stop、write 超时/取消、stopAll、watcher 自身超时）正在 stop，watcher 会把已写入的 STOPPED/TIMED_OUT 覆盖成 EXITED，导致停止原因丢失，并重复执行 drain()/cleanup()/persist()（cleanup 会对进程树再次 destroyForcibly 并最多自旋等待 2s），工件与状态可能不一致。建议把判断与赋值放进同一把锁。
- 判断：watcher无锁读RUNNING再写EXITED，与synchronized stop终态竞争；统一原子状态转换及单次drain/cleanup/persist，保留停止原因。
- 证据：harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java:307

## OCR-0715 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java:264；OCR原严重度：medium
- 计划：R1-03 / P1
- 原意见：轮转的长度记账混用了两种长度：`remove` 按脱敏前的 `output.length()` 计算（第 264-265 行），随后整段缓冲被脱敏（每个密钥被替换为 "[REDACTED]"，SecretRedactor 第 32 行）再按原始长度删除（第 272-273 行）。密钥被替换后 clean 变短，`remove = Math.min(remove, output.length())` 会把缓冲区删得比 256K+hold 更多（极端情况下被清空），既突破了设计上的保留量，也让 `dropped` 的增量与实际丢弃内容不对应；更严重的是 `log()` 的 offset 语义基于「log 时重新脱敏后的文本」，而 `dropped` 是基于轮转时脱敏后的删除量，同一条输出先以「部分密钥」、后以「完整密钥」出现时文本长度会变化，之前返回的 nextOffset 随之错位，分页会出现跳过或重复文本。建议统一以脱敏后的实际删除长度累加 `dropped`，并在轮转时保留尾部 hold 个字符。
- 判断：轮转先按原长度算remove再redact导致保留量/offset尺度变化；需要稳定脱敏输出流与绝对游标契约，不能只改dropped计数而留下跨chunk秘密边界。
- 证据：harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java:264

## OCR-0716 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java:168；OCR原严重度：high
- 计划：R1-03 / P1
- 原意见：`write` 的 UNKNOWN 语义不完整：第 162-163 行在输入线程写入失败（IOException/RuntimeException，例如流已被 stop 关闭）时只抛 TERMINAL_INPUT_UNKNOWN，既不停止任务也不改变 state，进程仍是 RUNNING。触发条件：写入过程中进程被外部杀死或流被并发 close，调用方按 UNKNOWN 语义重试 `write`，就会把可能已部分写入的文本再写一遍，违反「UNKNOWN 不得重放」的约束。建议与超时/取消分支保持一致，至少 `task.stop(...)` 或把任务标记为不可重放后再返回。
- 判断：write ExecutionException只报TERMINAL_INPUT_UNKNOWN而任务未失效，需禁止未核实同任务后续写并停止/诊断；标准Loop遇UNKNOWN不会自动重试，意见的自动重放说法不成立。
- 证据：harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java:168

## OCR-0717 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java:368；OCR原严重度：medium
- 计划：R1-04 / P1
- 原意见：持久化时没有保留尾部 hold：`persist()` 直接对整段缓冲做 `redactor.text(...)`，而 `stop()` 是先 `cleanup()` 强杀进程再 `drain()`。子进程在写出密钥一半时被 SIGKILL，缓冲区尾部残留的是不完整密钥串，SecretRedactor 只替换完整密钥，这半截明文会被写入 artifact；同样 `log()` 第 104-105 行只在 `process.isAlive()` 时裁掉尾部 hold，进程退出后残片也会通过分页输出。建议持久化/输出前统一按 streamingText 语义裁掉尾部 hold（或屏蔽以任意密钥前缀结尾的尾部），再脱敏落盘。
- 判断：结束后persist/log不再保留尾窗，非完整秘密前缀可能输出；明确部分秘密政策并用受控半密钥终止回归。不能永久截掉所有正常终态尾部，需可识别前缀策略。
- 证据：harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java:368

## OCR-0718 · 采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java:43；OCR原严重度：medium
- 计划：R1-04 / P1
- 原意见：环境变量在此处被原样复制并在第 73-75 行 clear 后整体注入，本类没有像 LocalProcessRunner（第 43-46 行）那样做键名校验或白名单。当前 CLI 调用方用的是 `LocalProcessRunner.buildEnvironment()`，但构造函数是公开 API：任何调用方传入的主机变量（含凭据类变量）都会被子进程直接继承；且值中含 '\0' 或非法键名时，`ProcessBuilder.start()` 会抛 IllegalArgumentException，而 `start()` 只捕获 IOException | LinkageError（第 82 行），未包装成 HarnessException 直接穿透到调用方。建议在构造/start 中复用同样的白名单与校验，并把 IllegalArgumentException 纳入 catch。
- 判断：LocalTerminalSession公开构造对Map.copyOf环境全注入，无LocalProcessRunner的键白名单；统一环境验证和实际快照对象，保持宿主白名单能力。
- 证据：harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java:43

## OCR-0719 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java:79；OCR原严重度：medium
- 计划：R1-03 / P2
- 原意见：任务 publicación 早于初始化：Task 在第 79 行放入 `tasks`，第 80 行才由 `begin()` 创建 reader/watcher；而 `status()`/`states()`/`stop()` 都不在 session 锁内。窗口期内另一线程可读到 `reader == null` 的任务，`view()` 会把 `outputDrained` 报成 true（reader == null），`stop()` 还可能在 begin() 之前 destroy 进程并关闭输入流，随后 begin() 才启动读线程去读已关闭的流。建议先 `begin()` 再 put，或把 reader/watcher 的创建移入 Task 构造函数，保证发布即完整初始化。
- 判断：task put早于begin，读接口可见未初始化reader；需完整发布或INITIALIZING态，不能简单先begin后put使快速watcher持久事实漏注册。
- 证据：harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java:79

## OCR-0720 · 采纳

- 位置：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:88；OCR原严重度：medium
- 计划：R0-03 / P1
- 原意见：脱敏断言可能空转，且依赖固定 sleep。`log()` 在任务仍存活时会把尾部 `hold` 个字符截掉、并做 redact；这里只断言 `indexOf("terminal-secret") < 0`，当日志还没被 reader 线程采到（150 ms 在慢 CI 上完全可能不够，或进程输出被尾部截断清空）时 `text` 为空串，indexOf 返回 -1，断言“通过”，实际上没有验证任何脱敏行为。另外命令本身（`printf terminal-secret; sleep 10` / `echo terminal-secret & ...`）也含密文，但断言没有区分“命令回显”与“程序输出”，也没有在 `stop()` 之后再校验一次（redact 是异步/在 log 时计算）。建议轮询一个非敏感标记（例如在命令里加 `echo MARK`）直到日志非空，再断言密文不出现。
- 判断：秘密断言可因日志空串真空通过，先等待公开marker再检秘密及stop后产物/日志，去固定sleep依赖。
- 证据：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:88

## OCR-0721 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:96；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`cleanupStatus` 在 `LocalTerminalSession.Task.view()` 中是硬编码的常量 `"partial"`（无任何分支），因此该断言永远不会失败，也无法发现清理逻辑回归；同时它与上一行 `residualPids` 必须为空的期望在语义上互相矛盾（一边说清理干净、一边说清理只完成了一部分）。建议改为断言可观测的清理结果：`waitForExit` 后 `status(id)` 的 `residualPids` 为空、`alive` 为 false、`outputDrained` 为 true、`state` 为 `STOPPED`。
- 判断：应增加alive/outputDrained/state实测断言；partial表示采样保证非完整而非“发现残留”，与residual为空不矛盾，不删诚实限制门禁。
- 证据：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:96

## OCR-0722 · 采纳

- 位置：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:113；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：该用例没有用 try-with-resources / finally 关闭会话，`terminal.close()` 写在所有断言之后。只要前面任一断言（例如 138-140 行的分页/尺寸断言，或 125 行的 `../outside` 拒绝断言）失败，`close()` 就不会执行，会话不会被关闭、`closeObserver` 不会被验证，后台任务只能靠自身超时回收——测试失败时还会叠加“会话未关闭”的假象，掩盖真正的失败原因。建议改成 try-with-resources，并在“取消/目录穿越必须不产生任务”两处失败启动后断言 `terminal.states()` 为空，「NeverStartOrLeakTasks」这个用例名所声称的泄漏校验目前完全没有断言支撑。
- 判断：M6TerminalTest会话在断言后close，提前失败漏清理；try/finally及失败启动后任务为空，保留原断言。
- 证据：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:113

## OCR-0723 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:165；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：配额断言依赖未被建立的清理时序。`stop()` 内部先 `destroyForcibly` 再最多等 2 s，超时后会带着仍存活的进程返回；而 `start()` 的配额是 `alive 进程数 >= 4`（`LocalTerminalSession:53-55`）。这里 stop 后既没有 `waitForExit` 也没有断言 state，紧接着连开 4 个 `block` 任务：若 flood 进程在 stop 返回时仍未死亡，第 4 次 `start` 就会抛 `TERMINAL_LIMIT`，用例在 172-181 行的循环里以未包装的 HarnessException 失败，而不是在 182 行的断言处失败，报错信息完全误导。建议 stop 后先 `waitForExit(terminal, id)` 确认进程已退出，并断言每次 block 启动成功（或最终 `states()` 中存活数为 4）再验证第 5 次被限流。
- 判断：stop后未确认flood退出即启动配额用例，新增退出/实际活跃数握手防时序误归因；残留时必须失败不能静默重试过门禁。
- 证据：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:165

## OCR-0724 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:171；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：该断言是恒真式：`log()` 已保证 `limit` 在 1..65536，且 `end = min(clean.length(), start + limit)`，所以返回文本长度必然 ≤ 4096，断言无法发现分页/offset 计算的任何缺陷。建议改为校验分页契约：`text` 非空、返回的 `offset == first`、`nextOffset == first + text.length()`、`rotated == true`。
- 判断：长度上限断言是有效契约但覆盖不足，补offset/nextOffset/rotation/非空，不因实现现保证就称恒真无用删除。
- 证据：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:171

## OCR-0725 · 采纳

- 位置：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:211；OCR原严重度：high
- 计划：R0-03 / P1
- 原意见：这里存在竞态，用例可能失败在错误的分支上。`write()` 入口先 `cancellation.throwIfCancelled()`（抛 `CANCELLED`），再起虚拟线程写管道并轮询取消（抛 `TERMINAL_INPUT_UNKNOWN`）。若执行器调度慢，100 ms 内 worker 还没进入第一次 `write`，取消标志已置位，于是第一处检查就抛 `CANCELLED`，224 行的 `assertEquals("TERMINAL_INPUT_UNKNOWN", ...)` 失败；反之若管道能吞下更多数据、64 次写都成功，`pending.get` 正常返回，`assertThrows` 又因未抛异常（或抛 `TimeoutException`，它不是 `ExecutionException`）而失败。固定 `Thread.sleep(100)` 无法证明“写已阻塞”。建议用可观测信号确认阻塞后再取消：写线程先循环 `write`，主线程轮询一次探测性 `write` 直到收到 `TERMINAL_INPUT_BUSY`（说明 inputSlot 已被占用、写入确实在途），再置取消标志。
- 判断：固定100ms不能保证write已进入阻塞，可能入口CANCELLED；以inputSlot忙/进度握手触发取消，保留在途UNKNOWN断言。
- 证据：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:211

## OCR-0726 · 采纳

- 位置：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:70；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：工作目录校验只比较了临时目录的最后一段名字，任何末级目录同名的 cwd 都能通过，无法证明进程真的落在 workspace 根目录下；同时如果改成直接比较 `root.toString()`，macOS 的 `/var` → `/private/var` 符号链接解析和 Windows 盘符大小写会让断言变成环境相关。建议用 `root.toRealPath()` 得到规范路径再比较（Windows 上按大小写不敏感处理），或在命令里输出 `pwd -P`/`cd` 后与规范路径严格比对。
- 判断：cwd只比较末级目录不足证明选对根，使用canonical路径或根内marker输出，处理平台别名但不放宽路径边界。
- 证据：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:70

## OCR-0727 · 部分采纳

- 位置：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:40；OCR原严重度：low
- 计划：R0-03 / P3
- 原意见：`waitForExit` 只断言 `alive == false`，没有失败信息；超时（6 s）后断言失败时既看不到 `state`/`cleanupStatus`/`residualPids`，也看不到最后的日志片段，对 PTY 输入未生效、close 后任务未被 stop 等生命周期缺陷几乎无法定位。建议在断言里带上完整 `status(id)`（或至少 `state` 与 `residualPids`），并顺带校验关闭/停止后的预期 `state`。
- 判断：waitForExit失败只alive信息不足，附脱敏状态/残留及总deadline，是否状态STOPPED按具体用例不一刀切。
- 证据：harness-process/src/test/java/io/jharness/process/M6TerminalTest.java:40

## OCR-0728 · 采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalProcessRunner.java:42；OCR原严重度：medium
- 计划：R1-04 / P1
- 原意见：第 42-46 行：先对调用方传入的 `environment` 做 `Map.copyOf` 快照，然后却遍历**原始** `environment` 做白名单校验，校验对象与真正生效的对象不是同一份数据。若调用方传入的是可变 Map 并在构造期间被其他线程修改，`this.environment` 里可能留下从未被校验过的键/值（如含 `\0` 的值或非法变量名），从而绕过环境变量白名单边界。请改为遍历并校验 `this.environment`（即快照本身），或先校验原始 Map 再拷贝。
- 判断：runner保存快照却验证原environment，构造并发变动可使实际快照未验；校验this.environment并加受控可变Map回归。
- 证据：harness-process/src/main/java/io/jharness/process/LocalProcessRunner.java:42

## OCR-0729 · 采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalProcessRunner.java:114；OCR原严重度：high
- 计划：R1-03 / P1
- 原意见：第 111-116 行存在 close/start 竞态：`closed` 检查（第 93、109 行）与 `builder.start()`/`active.add()` 不是原子操作。`close()`（第 378-383 行）只是置位 `closed` 后遍历当时的 `active`，若另一线程恰好在 `close()` 遍历结束之后、`active.add(process)` 之前完成 `start()`，该进程不会被 `close()` 看到，`close()` 会在命令即将/已经开始执行后返回，造成“关闭后仍有命令启动并产生副作用”。同理 `close()` 也不会等待正在执行的命令结束。建议让 `execute` 与 `close()` 共用同一把锁（关闭时置位 + 快照 `active`，启动时检查置位 + start + 注册原子完成），或在 `active.add()` 之后再次检查 `closed` 并立即清理。
- 判断：runner close快照active与start/add非原子，关闭后可漏进程；统一启动接管及关闭撤销，启动后复核并有界清理。
- 证据：harness-process/src/main/java/io/jharness/process/LocalProcessRunner.java:114

## OCR-0730 · 部分采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalProcessRunner.java:215；OCR原严重度：high
- 计划：R1-03 / P1
- 原意见：第 159-169 行与第 215-216 行：`join(outThread)/join(errThread)` 只等待 1000ms，超时后返回 false 但线程可能仍在运行；随后 `closeQuietly` 关闭流后**没有再次 join**，主线程就在第 215-216 行调用 `Collector.text()` 读取 `ByteArrayOutputStream`。触发条件：子进程被终止后仍有继承 stdout/stderr 的脱离孙进程存活、或关闭流无法唤醒阻塞中的 `read`，此时 collector 仍并发写 `bytes`，`truncated/ioFailed` 也还没有稳定。后果：输出内容可能被截断或错乱；IO 失败标志可能在状态计算之后才被置位，使实际 OUTPUT_IO 被判成 EXIT/SUCCESS；更重要的是此时 `truncated` 可能仍为 false，`text()` 不会执行 `secretTail` 尾部裁剪，末尾半个密钥会绕过脱敏直接进入结果；同时阻塞在原生 read 上的虚拟线程会长期泄漏。建议在关闭流后循环 join 至线程真正结束（带总体上限），若仍未结束则把 `truncated/ioFailed` 强制置真并丢弃未确认的尾部文本，确保读取发生在 writer 退出之后。
- 判断：读线程join超时后close无再次join，输出/标志未稳定就结算；需安全Collector快照、关闭后有界等待及尾部策略。未停止读者不得报告完整SUCCESS。
- 证据：harness-process/src/main/java/io/jharness/process/LocalProcessRunner.java:215

## OCR-0731 · 采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalProcessRunner.java:182；OCR原严重度：high
- 计划：R1-03 / P1
- 原意见：第 143-146 行把 `tree.exceeded` 当作 PROCESS_LIMIT 中断条件，但第 182-183 行的 `observedClean` 没有考虑 `exceeded`。触发条件：进程树超过 `maxTreeProcesses`（尤其在第 159 行最终 `cleanup()` 的 `sample()` 中才超限），超限后的后代不会被加入 `handles`，因此 `residual()` 仍为空、`observedClean` 为 true。后果：未被跟踪的后代可能继续存活，而结果却报告 observedClean=true；若此时 `reason` 已因正常 EXIT 且 exit==0 落到 SUCCESS 分支，则会在存在存活进程的情况下返回 SUCCESS，违反“清理未确认不得视为成功”的约定。建议让 `exceeded` 参与 `observedClean` 判定（并阻止 SUCCESS）。
- 判断：tree.exceeded未参与observedClean，末次cleanup才超配额可将未跟踪后代排除而报干净；超限必须禁止已完整清理声明并保留UNKNOWN。
- 证据：harness-process/src/main/java/io/jharness/process/LocalProcessRunner.java:182

## OCR-0732 · 不采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalProcessRunner.java:313；OCR原严重度：high
- 计划：无
- 原意见：第 306-320 行的 shell 解释器黑名单不完整，可绕过“Shell 必须显式使用 SH/CMD/POWERSHELL 模式”的策略。触发条件：ARGV 模式下直接执行 `bash.exe`/`sh.exe`（Windows 上 Git Bash、WSL）、POSIX 上的 `dash`、`zsh`、`fish`、`ksh`、`csh`、`tcsh`、`busybox sh`，或使用 `env bash -c ...`、`nohup bash -c ...` 之类的包装器，均可直接获得任意 shell 执行能力而绕过审批。建议先规范化 basename（去目录并去掉大小写不敏感的 `.exe` 后缀），再对解释器集合做完整匹配，并对 `env` 等包装器解包后继续校验。
- 判断：ARGV所有命令仍SYSTEM_IMPACT审批；Shell黑名单防常见误用非沙箱，python/node/env也能执行任意程序，穷举解释器不构成安全边界且无法完备，不称绕过审批。
- 证据：harness-process/src/main/java/io/jharness/process/LocalProcessRunner.java:313

## OCR-0733 · 采纳

- 位置：harness-process/src/main/java/io/jharness/process/LocalProcessRunner.java:491；OCR原严重度：low
- 计划：R1-03 / P2
- 原意见：第 485-489 行：`cleanup()` 吞掉 `InterruptedException` 后既没有恢复中断状态，也没有把中断传递出去，而它运行在 `execute` 的 finally 中。触发条件：上层在等待清理（最长 2 秒）期间中断执行线程（例如取消/关闭超时）。后果：中断信号被静默丢弃，`interrupted` 不会被置真，finally 末尾不会恢复中断位，调用方的取消语义丢失。建议用局部布尔量记录中断，在清理循环结束后统一 `Thread.currentThread().interrupt()`（或通过返回值交给 `execute` 合并到 `interrupted`）。
- 判断：cleanup catch InterruptedException无恢复标志，清理期间中断可丢；收尾后恢复或通过结果明确传递，避免带中断写journal毒化。
- 证据：harness-process/src/main/java/io/jharness/process/LocalProcessRunner.java:491

## OCR-0734 · 不采纳

- 位置：harness-session/src/main/java/io/jharness/session/MemoryCheckpointPolicy.java:6；OCR原严重度：medium
- 计划：无
- 原意见：安全边界问题：仅在注释中声明“仅限测试”，但该实现位于 harness-session 的 main 源集且为 public final，生产代码或第三方插件都能直接实例化并通过插件上下文注册为 CheckpointPolicy。一旦被接入生产装配，beforeEachModelRequest/atStepBoundary 将返回 MEMORY_ONLY，模型请求前与步骤边界的 DURABLE_FLUSH 检查点被跳过，进程崩溃/重启后事件可能未落盘，导致会话恢复缺少边界、已执行副作用被重放。当前 main 源集中未见该类的使用点（仅 harness-session/harness-testkit 测试引用），但可见性本身没有防护。建议将其移入 test 源集或 harness-testkit 的测试支撑模块；若必须保留在 main，至少改为包内可见，并在生产装配/插件注册处显式拒绝该实现。
- 判断：MemoryCheckpointPolicy明确仅内存测试策略，可信宿主可替换服务本是SPI，不承诺抵御恶意Java插件；生产durability门禁另检查writer，不能以public可实例化认定已绕过。
- 证据：harness-session/src/main/java/io/jharness/session/MemoryCheckpointPolicy.java:6

## OCR-0735 · 不采纳

- 位置：harness-session/src/main/java/io/jharness/session/MemoryCheckpointPolicy.java:13；OCR原严重度：medium
- 计划：无
- 原意见：该钩子完全忽略 call.sideEffectLevel()：即使调用是写文件、执行命令等有副作用的工具，也返回 MEMORY_ONLY。对照 DefaultCheckpointPolicy，非 READ_ONLY 工具应返回 DURABLE_FLUSH；若本策略被误接入生产，副作用工具将在没有持久化检查点的情况下执行，崩溃后本地副作用无法回滚且无法依据日志恢复，违反了“副作用工具强制 DURABLE_FLUSH”的约定。建议增加防御：对非 READ_ONLY 调用抛出不支持的操作异常（或返回 DURABLE_FLUSH），避免误用时静默降级为纯内存检查点。
- 判断：工具管线对非只读强制DURABLE_FLUSH和writer能力，内存policy不能静默放行真实副作用；修改仅测试策略无运行漏洞依据。
- 证据：harness-session/src/main/java/io/jharness/session/MemoryCheckpointPolicy.java:13

## OCR-0736 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:99；OCR原严重度：high
- 计划：R2-01 / P2
- 原意见：L99-101：`copyScope` 在任何作用域校验之前就执行 `root.resolve(source)`，存在三个问题：1) `Path.resolve` 对绝对路径会直接返回该绝对路径（如 `"/etc"`），相对路径 `../..` 也会逃逸 root，因此 `Files.exists` 成为仓库外的存在性探测/信息泄露，且随后 `directory(source,false)` 抛 INVALID_SESSION_ID 与静默 return 的差异可被用来判定任意路径是否存在；2) `source == null` 时 `root.resolve(null)` 直接 NPE；3) target 同样未经预校验（虽然后续 `directory(target,true)` 会拦截，但顺序上应统一）。触发条件：CLI/生态 API 把外部输入作为 source/target 传入（见 CliApplication#fork、EcosystemCli、M10BrowserHost）。建议先把 `directory()` 里的 session 正则抽取为 `validateScope()`，在解析任何路径之前对 source/target 校验，然后再判断存在性。
- 判断：copyScope先root.resolve/exists再validate scope有存在性探测差异，接受输入校验前置；标准fork生成/验证session ID，不称已证公共HTTP任意路径探测。
- 证据：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:99

## OCR-0737 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:102；OCR原严重度：high
- 计划：R2-01 / P1
- 原意见：L102-103：fork 的产物发布不是原子操作。target 目录与其中的文件都是就地创建，循环中任何一次失败（`ARTIFACT_LIMIT`、`ARTIFACT_CORRUPT`、IO 异常、非法 *.txt 条目）都会留下一个只有部分产物的目标作用域；由于 `CREATE_NEW` 对已存在文件必然失败，重试同一 fork 也无法补齐（除非人工清理）。而 `JsonlSessionRepository.fork` 的回滚只覆盖会话文件，孤儿产物目录会残留。触发条件：源作用域中任一产物被篡改/超限，或复制过程中磁盘错误。建议先写入同父目录下的临时目录，全部校验成功后用 `Files.move(..., ATOMIC_MOVE)` 发布；或在 try/catch 中删除本次创建的文件并在目标目录由本方法新建时删除该目录。
- 判断：copyScope就地写target，后续失败留部分制品且无补偿；用临时作用域完整校验后原子发布，并与会话fork事务协调。
- 证据：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:102

## OCR-0738 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:107；OCR原严重度：high
- 计划：R2-01 / P2
- 原意见：L107-109：`newDirectoryStream(sourcePath, "*.txt")` 只按名字匹配，不限制条目类型；`Files.size(file)` 会跟随符号链接。触发条件：源作用域目录被投放名为 `x.txt` 的符号链接或子目录——`Files.size` 会读取仓库外目标的元数据（大小侧信道，若链接悬空还会抛 IOException），而随后的 `read()` 因 `isRegularFile(..., NOFOLLOW)` 失败抛 ARTIFACT_NOT_FOUND，导致整个 fork 被一个外部链接以未受控错误码中断。建议在统计大小前显式校验 `Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)`，非普通文件统一按 ARTIFACT_CORRUPT/ARTIFACT_DENIED 处理。
- 判断：计数Files.size跟随不可信条目链接，后续read NOFOLLOW已拒内容读取；前置类型检查统一错误，不夸大为已读外部正文。
- 证据：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:107

## OCR-0739 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:32；OCR原严重度：medium
- 计划：R2-01 / P1
- 原意见：L31-32：作用域目录的符号链接校验是典型的 check-then-act（TOCTOU）。触发条件：在校验之后、`Files.createDirectories(path)` 之前，另一个进程把 `root/<session>` 换成指向外部目录的符号链接；当该路径原本不存在时，L27 的存在性检查整段跳过，`createDirectories` 也能成功穿过链接，之后 `put`/`copyScope` 的写入就会落到 root 之外。建议创建后重新校验（`path.toRealPath().equals(path.toAbsolutePath().normalize())` 且 `!Files.isSymbolicLink(path)`），并用 `Files.createDirectory(..., NOFOLLOW)` 或先 mkdir 再验证的流程替代不校验的 `createDirectories`；`create=false` 分支返回的路径在后续打开文件前同样应做一次复核。
- 判断：新建scope前检与mkdir/打开存在父目录替换窗口，需绑定目录句柄/原子创建及NOFOLLOW访问；反复toRealPath只能缩窗不能证明完全消除外部并发替换。
- 证据：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:32

## OCR-0740 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:40；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：L39-40：对 `DataSanitizer` 的契约没有任何防御。触发条件：1) `content == null` 时 `Map.of` 直接 NPE；2) 传入的是不可变 `Map.of`，任何就地修改入参的 sanitizer 实现（接口未禁止）会抛出 UnsupportedOperationException；3) sanitizer 返回 null、删除/改名 `"content"` 键或返回非 String 时，`.get()`/强制转换/`getBytes` 抛 NPE 或 ClassCastException，绕过了本类统一的 HarnessException 错误码。建议先 `Objects.requireNonNull(content)`，传可变 LinkedHashMap，并校验 `sanitize` 结果与 `"content"` 值类型后再使用。
- 判断：sanitizer返回形状未校验可引发NPE/CCE，接受统一DataSanitizer契约与输出验证；不允许sanitizer就地修改已不可变输入，不能为违规实现改成可变map。
- 证据：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:40

## OCR-0741 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:42；OCR原严重度：medium
- 计划：R5-01 / P1
- 原意见：L41-42：4MB 上限是在整段不可信输入完成脱敏并 `getBytes` 之后才检查的。触发条件：调用方传入 GB 级字符串——先经过 sanitizer 的 `String.replace` 全量处理，再额外分配一份 UTF-8 字节数组（至少 2 倍瞬时内存，中文约 3 倍），才被拒绝，与管理要求中的“有界数据处理”相悖。由于 UTF-8 字节数不可能少于字符数，可先用字符数做上界快速拒绝，再脱敏/编码。
- 判断：put先sanitize整串再getBytes后4MB检查，需输入预界/有界编码及脱敏扩张上限，避免大工具结果峰值放大。
- 证据：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:42

## OCR-0742 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:67；OCR原严重度：medium
- 计划：R5-01 / P2
- 原意见：L66-67：分页 `read(offset, limit)` 完全退化为整读——每次最多 256KB 的页请求都会通过 `readText` 把整个产物（最多 4MB）读入内存、做一次 SHA-256，再解码成 String 后 `substring`。触发条件：前端/主机按页遍历一个接近上限的产物，总成本退化为 O(n²) 的 IO 与哈希计算，且每次页请求都产生 4MB 级临时分配。建议按字节窗口读取（先校验文件大小和摘要，再 `FileChannel.read(buffer, offset)`），或至少缓存已验证的摘要/内容，避免每页重复全量校验与解码。
- 判断：read每页整读4MB hash，接受同一内容版本安全快照复用；直接按字节offset会破现有UTF16分页及完整脱敏，需要保持协议单位和防篡改校验。
- 证据：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:67

## OCR-0743 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:111；OCR原严重度：low
- 计划：R2-01 / P2
- 原意见：L111-112：这里的 `read(source, id, 0, 1)` 只是为校验，却通过 `readText` 把文件完整读入并计算 SHA-256；紧接着 L113-116 又把同一文件完整读入并再算一次哈希，等于每个产物 2 倍 IO + 2 倍摘要，且第一次的校验结果被直接丢弃。触发条件：fork 时源作用域产物较多/较大。建议去掉这次 `read`，在上面的 `Files.isRegularFile(..., NOFOLLOW)` 校验后用一次读取完成摘要校验（或让校验方法返回字节数组供后续写入复用）。
- 判断：copyScope先read整文件校验又第二遍读hash，合并一次NOFOLLOW有界读取验证后复制，保持内容摘要正确。
- 证据：harness-session/src/main/java/io/jharness/session/LocalArtifactStore.java:111

## OCR-0744 · 不采纳

- 位置：harness-session/src/main/java/io/jharness/session/EventSessionProjection.java:83；OCR原严重度：high
- 计划：无
- 原意见：终止态不粘滞（第 75-89 行设置、第 41-56 行/第 46-48 行又覆盖）。`turn.ended` 把 `current` 校验并置为 COMPLETED/FAILED/CANCELLED/LIMIT_REACHED 之后，投影仍继续消费后续事件：之后任一 `step.started`、`turn.started`、`tool.started`、`approval.decided` 都会把 `current` 改回 RUNNING/EXECUTING_TOOL，甚至再次进入 `turn.ended` 又能改写状态。触发条件：日志被并发追加/拼接、fork 前缀截断、或崩溃恢复后追加了 turn 结束后的事件。后果：`SessionRecovery` 直接使用 `projection.taskStatus()`（SessionRecovery.java:98），一个已 COMPLETED/FAILED 的会话会被判定为 RUNNING 而继续执行，等价于把已结束的 turn 重放。建议在投影中记录是否已进入终止态，终止态之后出现任何非 `session.*`/`plugin.*` 事件即抛 `INVALID_EVENT`（或至少忽略并不再改动 `current`）。
- 判断：会话明确支持多轮，turn.ended后合法turn.started必须回RUNNING；终态全会话粘滞会破坏M10.5.3。投影不执行工具，也不等于结束轮被重放。
- 证据：harness-session/src/main/java/io/jharness/session/EventSessionProjection.java:83

## OCR-0745 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/EventSessionProjection.java:46；OCR原严重度：high
- 计划：R2-03 / P2
- 原意见：审批状态缺少配对校验，UNKNOWN/乱序可被静默接受。`approval.requested` 只改状态、不入历史（第 45 行），`approval.decided` 无条件把状态置为 RUNNING（第 46-48 行），既不校验存在同一 `callId` 的未决 `approval.requested`，也不校验当前是否处于 WAITING_APPROVAL。触发条件：重复的 `approval.decided`（幂等重放）、针对另一个 call 的决策、丢失 request 事件的前缀（fork 截断）或伪造/乱序日志。后果：投影把从未请求过审批的工具调用当成已批准，状态从 CANCELLED/COMPLETED/EXECUTING_TOOL 被改回 RUNNING，恢复流程据此继续执行高风险工具。建议用 `Map<String, SessionEvent> pendingApprovals` 记录 request，decided 时校验 callId 与当前状态并消费掉该 pending，缺失或重复则抛 `INVALID_EVENT`。
- 判断：approval projection未配对可使畸形日志状态不准，接受事实关联校验；真正工具批准由ApprovalService/pipeline精确判定，不会因projection RUNNING自动执行风险工具。
- 证据：harness-session/src/main/java/io/jharness/session/EventSessionProjection.java:46

## OCR-0746 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/EventSessionProjection.java:58；OCR原严重度：high
- 计划：R2-03 / P2
- 原意见：`tool.completed` 无论 status 是否为 UNKNOWN 都被无条件重放成 `role=tool` 消息。触发条件：工具执行后状态为 UNKNOWN（例如 CliApplication 对账时写入 `tool.completed` + `ToolStatus.UNKNOWN`，或工具结果带 `sideEffectUncertain=true`）。此时下游模型会看到一条“确定失败/确定结果”的工具输出，而 `SessionProjection.uncertainCalls()`  meanwhile 仍把它标记为不确定；若随后 `tool.verified`，第 65-73 行只追加一条 user 消息，先前那条 UNKNOWN 的 tool 结果仍留在 `messages()` 中，二者互相矛盾。建议读取 `payload.get("status")`：UNKNOWN 或 `value.sideEffectUncertain` 为真时不要生成确定性的 tool 消息（或生成显式“执行结果未知，勿重放”的占位内容），并在收到 `tool.verified` 时替换对应 callId 的旧 tool 消息。另外，`content` 缺失或非 String 时静默跳过会留下一条无人应答的 tool_call，建议补一条占位 tool 消息以避免下游协议报错。
- 判断：UNKNOWN工具消息仍需保持assistant/tool协议配对，不能删消息造成孤立调用；明确未知文本和后续人工核实关联，恢复门禁已阻止直接重放。
- 证据：harness-session/src/main/java/io/jharness/session/EventSessionProjection.java:58

## OCR-0747 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/EventSessionProjection.java:74；OCR原严重度：medium
- 计划：R1-04 / P2
- 原意见：`tool.verified` 的整个 payload 被原样 `toString()` 拼进 user 消息送入模型上下文，存在敏感信息外泄与快照不确定性两个问题：(1) payload 是扩展事件，可能携带 tool 参数、路径、token、子会话/产物引用等任意字段（参见 SubagentPlugin、CliApplication 写入的 `reason`/`childSessionId`/`sourceEvent`），未做白名单或脱敏即进入请求体；(2) `Map.toString()` 的键序取决于 payload 底层 Map 的迭代顺序，用 `Map.of(...)` 构造的 payload 键序在不同 JVM 运行间并不保证一致，附带 `toString` 也非 JSON，破坏了本类“确定性快照”的承诺。建议只挑选并显式脱敏固定字段（如 outcome/reason），并用 JSON 编码输出；或在投影层直接不带 payload 原文。
- 判断：tool.verified payload.toString非稳定结构，接受白名单安全结构输出；宿主writer/Loop clean已经脱敏，不能说所有核实payload必然泄密。
- 证据：harness-session/src/main/java/io/jharness/session/EventSessionProjection.java:74

## OCR-0748 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/EventSessionProjection.java:31；OCR原严重度：medium
- 计划：R2-03 / P2
- 原意见：`context.injected` 的 payload 被无条件当成一条消息加入，但不校验消息契约（role/content）。触发条件：插件或 CLI 写入不满足 chat message 形状的 `context.injected`，例如测试中 M5SessionTest.java:233 的 `Map.of("artifactId", ref.id())`。此时 `messages()` 会返回一条没有 `role` 的伪消息，并被 CliApplication 作为历史上下文重放到下一次 ChatRequest，导致下游模型收到非法消息（或在严格 provider 上直接 400）。建议校验 `role`/`content` 为 String，否则忽略该事件或把 artifact 引用解析为显式注入内容，并在格式非法时抛 `INVALID_EVENT`。
- 判断：context.injected无role payload会直接进入messages导致下一请求非法；明确注入消息与artifact引用事件schema并在边界拒绝畸形。
- 证据：harness-session/src/main/java/io/jharness/session/EventSessionProjection.java:31

## OCR-0749 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/InMemorySessionRepository.java:53；OCR原严重度：high
- 计划：R2-03 / P2
- 原意见：fork 不是原子提交：`openWriter(target, ...)` 会通过 `stores.computeIfAbsent` 先把目标会话注册进 `stores`，随后才逐个 `append` 复制事件和补写 `session.forked`。只要复制过程中任一 append 失败（例如将来加入 SESSION_LIMIT/序列化校验，或发生 OOM），try-with-resources 只会 `close` writer，目标 Store 仍留在 `stores` 中，成为一个缺 `session.forked` 标记、事件不完整的“半成品会话”，之后 `list()`/`openReader()` 能读到它，别的调用方还能对它 `openWriter` 继续写，导致恢复/批量处理拿到不一致的历史。建议在失败时移除目标 Store（成功后仅保留），或先在局部 Store 中构建完毕再一次性发布到 `stores`。
- 判断：内存fork目标逐步公布，无原子发布策略，可在并发读取时见中间态；原意见主要靠将来限额/OOM，接受与文件fork一致发布契约而非OOM后可继续保证。
- 证据：harness-session/src/main/java/io/jharness/session/InMemorySessionRepository.java:53

## OCR-0750 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/InMemorySessionRepository.java:43；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：fork 是 `synchronized` 方法，整个“扫描事件 + 逐条复制 + 追加 forked 事件”的过程都持有仓库级监视器，期间其它会话的 `openWriter`/`openReader`/`list` 全部被阻塞；历史越长（仓库本身也没有事件数上限）阻塞时间越长，表现为跨会话的全局停顿。建议只在 `stores` 查找/注册目标与源 Store 时用仓库锁，实际复制在源 Store 的锁内完成。
- 判断：内存fork持仓库全局锁复制会阻塞其它会话，当前未限事件量加重；统一限额后缩锁，性能需要受控多会话数据验证。
- 证据：harness-session/src/main/java/io/jharness/session/InMemorySessionRepository.java:43

## OCR-0751 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/InMemorySessionRepository.java:107；OCR原严重度：medium
- 计划：R2-03 / P2
- 原意见：schemaVersion 校验与错误码同 JsonlSessionRepository 不一致：文件实现用 `event.schemaVersion() != 1` 并抛 `FUTURE_VERSION_READ_ONLY`（JsonlSessionRepository:637），这里用 `> 1` + `FUTURE_VERSION`。两个实现被同一接口/同一批用例替换使用（如 M5SessionTest 同时跑两种实现），上层按 code 分支时会在换存储后行为不同（InMemorySessionTest:111 也固化了 `FUTURE_VERSION`）。建议统一为 `!= 1` + `FUTURE_VERSION_READ_ONLY` 并同步用例。
- 判断：内存未来schema抛FUTURE_VERSION而文件FUTURE_VERSION_READ_ONLY不一致；统一存储能力/错误契约，schema=0已在SessionEvent拒绝，不能以!=1推导当前0被接受。
- 证据：harness-session/src/main/java/io/jharness/session/InMemorySessionRepository.java:107

## OCR-0752 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/InMemorySessionRepository.java:109；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：append 缺少每会话事件数上限：文件实现有 `eventIds.size() >= 10000 -> SESSION_LIMIT`（JsonlSessionRepository:639）与 LOG_LIMIT 约束，这里可以无限增长，既没有“有界数据处理”保护，也让同一份逻辑在换存储时行为分叉（InMemory 能通过、Jsonl 抛 SESSION_LIMIT；fork 复制出的子会话同样不受限）。建议在 append 中增加等价的 `SESSION_LIMIT` 检查。
- 判断：内存仓库事件无总上限，与项目有界要求不符；定义可配置但确定上限和文件实现兼容回归。
- 证据：harness-session/src/main/java/io/jharness/session/InMemorySessionRepository.java:109

## OCR-0753 · 不采纳

- 位置：harness-session/src/main/java/io/jharness/session/InMemorySessionRepository.java:109；OCR原严重度：low
- 计划：无
- 原意见：`ids.add` 既做判重又立即改状态，且先于 `events.add` 执行：一旦 `store.events.add` 抛异常（如扩容 OOM），ID 索引已记录该 eventId 而 events 中没有对应事件，重试同一事件会被误判为 DUPLICATE_EVENT，索引与事件列表失配（fork 按 eventId 查找也会受影响）。建议先 `ids.contains` 判重，成功追加事件后再 `ids.add`，使两个集合的更新顺序对失败安全。
- 判断：只以ArrayList扩容OOM说明两集合事务问题；调换顺序又可在HashSet.add OOM时反向失配，不能为OOM保证提出无效顺序修复。
- 证据：harness-session/src/main/java/io/jharness/session/InMemorySessionRepository.java:109

## OCR-0754 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/EventRequestJournal.java:51；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：prefix 会把传入的每个事件都复制进 LinkedHashMap，但后面唯一的读取是 prefix.get(contextEventId)，只可能命中 type=request.context 的事件。DefaultAgentLoop 每次模型尝试都用 runtime.reader().events() 传入全量历史（非有界前缀），长会话下每次 reconstruct 都会在调用方列表之外再分配一份 O(n) 的 Map，内存开销随事件总数线性增长且会随重试次数放大。建议只索引 request.context 类型的事件（其余事件仅做 session/schema/重复校验），或直接要求调用方传入有界前缀。
- 判断：reconstruct索引所有事件只为context查找，多次请求放大；一次线性校验同时只存必要context及已见ID，保留重复/跨会话门禁。
- 证据：harness-session/src/main/java/io/jharness/session/EventRequestJournal.java:51

## OCR-0755 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/EventRequestJournal.java:36；OCR原严重度：medium
- 计划：R2-03 / P1
- 原意见：request.context 与 model.requested 是两条独立 append，中间没有 flush、事务或补偿。触发条件：第一条 append 成功后第二条 EventSupport.append 抛异常（磁盘满/配额、writer 已关闭、writer 校验 SESSION_MISMATCH、EventBus.publish 抛错等），此时持久化日志会留下一个孤立的 request.context，而没有对应的 model.requested。由于 EventSessionProjection 把任何 request.context 当作最终可见信封并清空消息列表，恢复/投影会显示一个“已准备但无请求记录”的信封，无法区分模型调用是否真的发生。建议在首次 append 前完成 writer 可用性校验，并在第二次 append 失败时写入补偿事实（如 request.rejected/request.failed）或至少显式失败并阻止后续恢复把该上下文当作有效请求。
- 判断：request.context可能先写后requested失败留孤立信封，需投影只采用已关联的请求上下文并可诊断；不得在writer失败后强写补偿或把checkpoint移走绕过先落盘。
- 证据：harness-session/src/main/java/io/jharness/session/EventRequestJournal.java:36

## OCR-0756 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/EventRequestJournal.java:84；OCR原严重度：low
- 计划：R2-03 / P2
- 原意见：fromPayload 只校验 messages/tools 为 List，未校验 parameters：缺失或类型错误时由 Values.object 抛出 INVALID_JSON（与重建路径期望的 INVALID_EVENT 错误码不一致），而 messages/tools 为“缺失”和“类型错误”共用同一条 "Missing request messages or tools" 文案，损坏事件难以定位。建议显式校验 parameters 为 JSON 对象、统一错误码，并对 messages/tools 的元素类型给出明确诊断（当前由 Values.object 抛 INVALID_JSON）。
- 判断：fromPayload只检查messages/tools，parameters/元素畸形泄漏INVALID_JSON而非事件错误；统一事件schema受控拒绝和字段诊断。
- 证据：harness-session/src/main/java/io/jharness/session/EventRequestJournal.java:84

## OCR-0757 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/EventRequestJournal.java:56；OCR原严重度：low
- 计划：R2-03 / P3
- 原意见：三种不同失败原因（跨会话 sessionId、schemaVersion != 1、重复 eventId）被合并进同一条 INVALID_EVENT 且文案固定为 "Request prefix must be a single valid session"：当真实原因是重复 ID 或未来 schema 版本时会给出误导性提示，排查困难；同仓库 EventSessionProjection 已分别使用 SESSION_MISMATCH / FUTURE_VERSION / DUPLICATE_EVENT。另外把有副作用的 putIfAbsent 放进 || 短路条件里，也使“校验”和“建索引”耦合在一起。建议拆成独立检查并使用各自错误码。
- 判断：INVALID_EVENT目前安全拒绝但原因不精确，分schema/重复/跨会话诊断可采纳，不能因短路表达式风格单列重构。
- 证据：harness-session/src/main/java/io/jharness/session/EventRequestJournal.java:56

## OCR-0758 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/SanitizingSessionWriter.java:26；OCR原严重度：medium
- 计划：R1-04 / P1
- 原意见：脱敏覆盖范围不完整且未声明豁免字段。`append` 只对 `payload()` 调用 `sanitize`，而 `eventId/sessionId/turnId/stepId/callId` 原样透传，但类注释宣称本装饰器覆盖 "inputs and extension facts"，读代码的下游很容易把它当成写侧唯一的安全闸门。触发条件：插件或扩展在构造事件时把自由文本/外部标识写进 `stepId`、`callId`（这两个都不是 `SessionEvent`/仓储强制校验的关联键，只有 `sessionId` 必须与仓储一致、`eventId` 是去重键），该值会进入 JSONL/内存仓储并通过 Web 订阅原样下发，绕开脱敏。建议至少在其中一处（构造函数或 `append` 的 javadoc）显式列出被豁免的字段与原因，明确 "不可把外部自由文本放进这些标识字段" 的契约；若要对标识字段也脱敏，需保证同一 key 在 `assistant.message` 与 `approval.decided` 等不同事件间得到相同的脱敏结果，否则会破坏 `EventSessionProjection` 的 callId 关联。
- 判断：SanitizingSessionWriter只清payload，callId等自由标识可含已知secret；统一标识约束/安全映射并保证关联稳定，禁止逐事件随机脱敏破坏恢复。
- 证据：harness-session/src/main/java/io/jharness/session/SanitizingSessionWriter.java:26

## OCR-0759 · 不采纳

- 位置：harness-session/src/main/java/io/jharness/session/SanitizingSessionWriter.java:18；OCR原严重度：low
- 计划：无
- 原意见：此处按位置逐字段重建 `SessionEvent`，等价于在本文件复制了一份 record 的组件列表；`SessionEvent` 每新增一个组件都必须同步改动这里（虽然能靠编译错误兜底，但任何"漏写"的临时修复都会静默丢字段），而且每次 `append` 都会重复执行 record 构造器里的全部校验。建议在 `SessionEvent` 上提供 `withPayload(Object payload)`（复用其余组件）并在此调用，去掉这份重复的结构定义。
- 判断：按record重建有编译器检查新增字段，withPayload属于API便利重构，尚无丢字段实证不纳修复。
- 证据：harness-session/src/main/java/io/jharness/session/SanitizingSessionWriter.java:18

## OCR-0760 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionCatalog.java:74；OCR原严重度：high
- 计划：R2-03 / P2
- 原意见：`archived` 只判断是否存在 `session.archived` 事件，忽略了 `session.restored`。触发条件：对同一会话先 `sessions archive` 再取消归档（SessionLifecycle.setArchived 会追加 `session.restored`，且 SessionLifecycle.archived() 明确把状态翻回 false），此后 `sessions query` 永远报告 `archived: true`，与 `sessions list`/归档语义不一致。此处重复实现了 SessionLifecycle.archived(events) 的顺序状态机，直接复用即可修复。
- 判断：SessionCatalog archived=anyMatch，归档恢复后仍true；复用SessionLifecycle顺序状态机并覆盖archive→restore→query。
- 证据：harness-session/src/main/java/io/jharness/session/SessionCatalog.java:74

## OCR-0761 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionCatalog.java:77；OCR原严重度：medium
- 计划：R2-03 / P2
- 原意见：只捕获 `HarnessException`，与类注释“errors are returned per session”不符。触发条件：日志被篡改/损坏且 payload 类型不符时，下游会抛出非 HarnessException 的 RuntimeException——例如 SessionRecovery 第 53、82 行把 payload 里的 `id`/`taskId` 直接 `(String)` 强转，遇到数字类型即抛 ClassCastException；HarnessException 是 final class 且继承 RuntimeException，此处的 catch 无法覆盖。结果是整个 catalog 扫描中断，一次坏会话让所有会话查询失败。建议捕获 RuntimeException 并归一化为该会话的 diagnostic 行（HarnessException 取 code()，其余用固定 code）。
- 判断：Catalog仅catch HarnessException，损坏会话CCE等可中断全目录；逐会话隔离非致命解析失败，保留原诊断且不跳过当通过。
- 证据：harness-session/src/main/java/io/jharness/session/SessionCatalog.java:77

## OCR-0762 · 不采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionCatalog.java:21；OCR原严重度：medium
- 计划：无
- 原意见：这里构造的 SessionQuery 结果被直接丢弃，实际只依赖其构造函数副作用做 offset/limit 校验；扫描上界完全依赖这一行（配合第 87 行的 `result.size() == limit` 等值判断）。一旦这行“看似无用”的表达式在重构/静态清理中被删除，`limit <= 0` 时第 87 行永远不成立，循环退化为全量扫描并返回无界结果（10,000 个会话上限下仍会返回全量）。建议显式校验边界，或把对象承接为局部变量复用于事件过滤，并用 `>=` 做上界判断。
- 判断：new SessionQuery实际执行参数校验，不是无用表达式；假设未来删掉代码不能证明当前无界返回。
- 证据：harness-session/src/main/java/io/jharness/session/SessionCatalog.java:21

## OCR-0763 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionCatalog.java:26；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：每个会话先反序列化全部事件，随后又被多次全量遍历：`SessionRecovery.inspect` 内部对 events 至少做 5 次循环（pendingRequestEvents、calls、terminalStates、subagent、environment），workspace/model 过滤各一次 stream，`archived` 又一次 stream；而最便宜的 workspace/model 过滤反而排在最贵的 `SessionRecovery.inspect` 之后。当 list() 达到 10,000 个会话上限时，单次 query 的遍历成本被成倍放大，且第 59 行还在循环内为每个会话重复 new SessionQuery。建议：先用一次循环同时算出 workspace/model/archived 命中标记，再按需调用 recovery（仅当需要 status 或输出时），并把事件过滤用的 SessionQuery 提到循环外复用。
- 判断：Catalog全日志/多遍扫描且恢复在便宜过滤前，接受有界元数据投影/增量索引；必须保持源事实权威且不复用陈旧状态。
- 证据：harness-session/src/main/java/io/jharness/session/SessionCatalog.java:26

## OCR-0764 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionLifecycle.java:32；OCR原严重度：medium
- 计划：R2-03 / P2
- 原意见：新增的 `session.restored` 只有在 `SessionLifecycle.archived()` 中生效，其它状态推导仍只认 `session.archived`：`SessionCatalog.query()` 用 `anyMatch(type == "session.archived")` 计算 archived（SessionCatalog 第 75-76 行），CLI `sessions list` 走该路径。触发条件：先 `setArchived(id, true)` 再 `setArchived(id, false)`，此后 Web/SDK 认为未归档，但目录查询仍返回 `archived=true`，出现可复现的状态不一致。建议把 archived 推导统一收敛到本类（并为 archive→restore→list 增加回归用例），保证所有消费者对 `session.restored` 采用同一语义。
- 判断：与760同根因，保留独立意见编号合并统一归档投影修复。
- 证据：harness-session/src/main/java/io/jharness/session/SessionLifecycle.java:32

## OCR-0765 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionLifecycle.java:21；OCR原严重度：high
- 计划：R2-03 / P2
- 原意见：独占 writer 先于存在性校验被打开：`JsonlSessionRepository.openWriter()` 用 `CREATE` 打开/创建 `<id>.jsonl`，并在文件为空时写入 format header（JsonlSessionRepository 第 122-142 行），同时创建 `<id>.lock`。因此对**不存在**的 id 调用 `setArchived` 不会抛 `SESSION_NOT_FOUND`，而是先落地一个只有 header 的空会话文件，随后在空历史上 `SessionRecovery.ready()` 为 true，最终把 `session.archived` 追加进一个从未创建过的会话（幽灵会话；恢复时 Web/SDK 都会把它当作真实归档会话）。同理，`archived(history) == archived` 的 no-op 路径也会无谓创建文件与锁。建议先用 `openReader` 校验会话存在并判断当前状态（不存在即失败、状态相同即返回），再打开独占 writer 并在其内部重读快照做二次校验，以保留 read-under-lock 的原子性。
- 判断：setArchived先openWriter创建缺失会话/锁，需先存在性只读核验并取锁后二次确认；不能只前检而引入竞态。
- 证据：harness-session/src/main/java/io/jharness/session/SessionLifecycle.java:21

## OCR-0766 · 不采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionLifecycle.java:28；OCR原严重度：medium
- 计划：无
- 原意见：幂等短路位于 UNKNOWN 校验之后：当会话已处于目标状态、但历史中存在未结算事实（`uncertainCalls`/`unsettledCalls`/`pendingRequests`/`pendingTerminals` 任一非空，例如崩溃后遗留的 `model.requested` 或 `terminal.snapshot` RUNNING）时，重复调用 `setArchived(id, true)`（归档重试、Web 重复 PATCH、恢复后再次恢复）会抛 `UNKNOWN_SIDE_EFFECT` 而不是幂等返回，把“已归档/已恢复”状态误报成失败。建议把 `archived(history) == archived` 的判断提前到 `SessionRecovery.inspect(...)` 之前。
- 判断：UNKNOWN前置门禁适用于可见性变化/确认，重复请求仍需反映当前未决事实；移动no-op到检查前会隐藏未知，不因幂等偏好绕过安全诊断。
- 证据：harness-session/src/main/java/io/jharness/session/SessionLifecycle.java:28

## OCR-0767 · 不采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionLifecycle.java:34；OCR原严重度：medium
- 计划：无
- 原意见：审计主体硬编码为 `"operator"`，而方法自身不校验任何调用者身份：CLI 的 archive 写的是 `actor=user`（CliApplication 第 488-489 行），Web 端 `POST /v1/tasks/{id}/metadata` 在该产品中也没有鉴权，任何能访问该端口的调用方都会被记录成 `operator`，审计归属不可区分且可伪造。建议把 actor 作为显式参数由已认证的调用上下文注入（或至少记录真实调用来源与 requestId），不要硬编码。
- 判断：产品Web有loopback同源会话/业务鉴权，意见称任何端口访问无鉴权与HarnessWebServer相反；单操作者actor=operator是支持范围，不扩展多用户身份。
- 证据：harness-session/src/main/java/io/jharness/session/SessionLifecycle.java:34

## OCR-0768 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionRecovery.java:73；OCR原严重度：medium
- 计划：R2-03 / P1
- 原意见：`terminalStates` 的所有写入都是无条件 `put()`，最后写入者胜出，未为显式人工核实事实设置优先级。一旦 `terminal.verified`（操作者确认“未重启已完成核实”，payload 无 state 字段，此处固定写 VERIFIED）之后又出现 `terminal.snapshot` 或带 `pty` 的 `tool.completed`（例如运行时/重启时按当前进程视图记录的旧状态），VERIFIED 会被 RUNNING/UNKNOWN 覆盖，`pendingTerminals` 重新出现已完成核实的终端；反向地，陈旧快照也可能把 UNKNOWN 覆盖为终态而吞掉未决终端信号。建议让 VERIFIED 成为粘性状态（已核实的 id 不再被 snapshot/tool.completed 覆盖），并按明确的优先级而不是事件顺序计算待核实终端。
- 判断：verified后陈旧snapshot可覆盖状态需要明确终端世代/顺序验证；不能永久粘VERIFIED掩盖后来真实新活动或新未知，测试覆盖两方向。
- 证据：harness-session/src/main/java/io/jharness/session/SessionRecovery.java:73

## OCR-0769 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionRecovery.java:29；OCR原严重度：high
- 计划：R2-03 / P1
- 原意见：`assistant.message` 会按 stepId 一次性清除该步骤内**所有**未决 `model.requested`，与本方法对 `model.rejected`（精确 requestEventId）和 `model.verified`（sourceEvent 或仅唯一未决时才接受 stepId）的“尝试级”语义不一致。复现：同一步骤 attempt1 发出请求后客户端异常（未产生 model.rejected/assistant.message），重试 attempt2 成功并记录 assistant.message，此时 attempt1 也被判定已结算——`isRequestPending(events, attempt1)` 返回 false，`inspect` 不再报告 pendingRequests，可达模型请求的未知副作用被静默隐藏，与“UNKNOWN 不得被重放”的恢复约束冲突（且 M8 用例 rejectedFactOnlySettlesItsExactAttempt 明确要求只结算精确尝试）。建议沿用 legacy model.verified 的规则：仅当该步骤内恰有一个未决请求时才按 stepId 清除，否则必须经 verify-request 的 sourceEvent 精确核实。
- 判断：assistant.message按step清所有请求不够尝试精确，畸形多pending历史应拒歧义；默认M8只重试已落盘HTTP429拒绝，给定未知后重试不是标准合法路径。
- 证据：harness-session/src/main/java/io/jharness/session/SessionRecovery.java:29

## OCR-0770 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionRecovery.java:53；OCR原严重度：high
- 计划：R2-03 / P2
- 原意见：恢复审计对 payload 中的 id 直接强转：tool_calls[i].id、subagent.started.taskId、environmentId 缺失或非 String 时会加入 null 或抛 ClassCastException，导致 inspect/recover 整体失败（或生成 `subagent:null`/`environment:null` 伪标识）。建议统一用 `instanceof String` 校验，非法条目跳过或报明确校验错误。
- 判断：恢复对tool_call/task等字段裸String转型可畸形日志CCE；必须固定schema错误fail-closed，禁止跳过未知事实当ready。
- 证据：harness-session/src/main/java/io/jharness/session/SessionRecovery.java:53

## OCR-0771 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionRecovery.java:13；OCR原严重度：medium
- 计划：R2-03 / P2
- 原意见：`ready()` 只看四个列表，完全忽略 record 中的 `status`。会话若以 `approval.requested` 结束（无对应 `approval.decided`），投影状态为 WAITING_APPROVAL，但没有任何待核实项进入这四个列表，于是 `ready()` 仍返回 true；而该方法是 `CliApplication resume`、`SessionLifecycle.setArchived`、`HarnessSdk` 的唯一放行条件，导致未决审批被静默丢弃（resume 直接开启新一轮，审批事实悬空）。建议把未决审批这类事实纳入审计（或在 ready() 中排除 WAITING_APPROVAL），使恢复/归档前能显式暴露并处理未决审批。
- 判断：正常AgentLoop先assistant.tool_calls，审批中call被unsettledCalls门禁覆盖，不是默认恢复放行；但公共pipeline可独立产生tool.requested/approval.requested，需明确独立会话恢复契约并补中断审批诊断/废弃规则，不能据status永久禁用合法新轮或恢复旧批准。
- 证据：harness-session/src/main/java/io/jharness/session/SessionRecovery.java:13

## OCR-0772 · 部分采纳

- 位置：harness-session/src/test/java/io/jharness/session/EventRequestJournalTest.java:12；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：L11-37 的 EventBus 假实现把 publish/publishSerial/intercept 全部静默丢弃。EventRequestJournal.record 经 EventSupport.append 依次执行 writer.append 与 bus.publish（EventSupport:31-32），因此本测试无法发现事件漏发或重复发布：例如 model.requested 被发布两次会让同一权威请求事实被重放两次，contextEventId 指向错误事件也不会被发现。建议在假实现中收集已发布事件，断言每次 record 恰好发布 request.context 与 model.requested 各一条，且 model.requested 的 contextEventId 等于刚发布的 context.eventId、turnId/stepId 一致。
- 判断：EventBus stub不检查publish缺口成立，加record两事实精确关联和单次发布；重复通知不等于执行重放，不按高危产品缺陷表述。
- 证据：harness-session/src/test/java/io/jharness/session/EventRequestJournalTest.java:12

## OCR-0773 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/EventRequestJournalTest.java:131；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：L129-130 只断言消息条数与最后一条，无法发现“重复一条、丢失一条”同时发生的情况：例如丢掉 assistant.message 又追加一条重复的 context 消息，size 仍为 4 且末条仍是 tool 消息，测试依然通过，与测试名声称的“WithoutAuthorityDuplication”不符。建议直接断言完整的期望消息列表（含 role/content/tool_call_id 全部字段）。
- 判断：size+末条不能识别中间消息替换，补完整预期协议消息与顺序，保留确定性比较。
- 证据：harness-session/src/test/java/io/jharness/session/EventRequestJournalTest.java:131

## OCR-0774 · 部分采纳

- 位置：harness-session/src/test/java/io/jharness/session/EventRequestJournalTest.java:117；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：L120-133：该用例把 tool.completed 的 payload.status（DENIED）完全忽略并断言其内容被直接重放为 role=tool 消息；同时 assistant 消息未声明 tool_calls，却出现 tool_call_id=c 的 tool 消息，形成孤立的工具结果，真实 provider 会以 400 拒绝，且投影未校验 turn/step/callId 归属，跨步骤的 tool.completed 也会被拼进当前请求。建议补充：UNKNOWN/失败状态不得重放为模型输入、跨 step/turn 或 callId 不匹配的 tool.completed 不进入消息，并在 assistant 消息中带上匹配的 tool_calls 后再断言重放结果。
- 判断：fixture assistant无tool_calls而有tool结果不够真实，补关联负例；DENIED/FAILED结果应反馈模型而非一律禁止作为上下文，UNKNOWN另受恢复门禁。
- 证据：harness-session/src/test/java/io/jharness/session/EventRequestJournalTest.java:117

## OCR-0775 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/EventRequestJournalTest.java:159；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：用 toString().contains("raw") 判断脱敏过于间接且脆弱：任何无关字段出现 "raw" 子串都会误报。更关键的是本用例只覆盖顶层 content 与 api_key，没有走 journal.record 去覆盖 ChatRequest 中嵌套 messages/tools/parameters 的脱敏（SecretRedactor 会递归清洗嵌套 Map/List），嵌套层级脱敏退化时该用例仍会通过。建议直接断言 payload().get("api_key") 为 "[REDACTED]"，并追加一条含嵌套 secret-sentinel 的 request.context 事件，断言嵌套值同样被替换。
- 判断：补nested request.context的messages/tools/parameters秘密sentinel，直接字段断言与全局无秘密互补。
- 证据：harness-session/src/test/java/io/jharness/session/EventRequestJournalTest.java:159

## OCR-0776 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/EventRequestJournalTest.java:155；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：这里只用一个 durable()=false 的 InMemorySessionRepository 验证包装后仍为 false，无法发现 SanitizingSessionWriter 把 durable() 硬编码为 false 的缺陷——真实持久化 writer 会因此丢失 fsync 语义而没有任何测试报警。建议增加一个 durable() 返回 true 的 delegate 桩，断言包装后 durable() 仍为 true，并校验 flush() 确实被委托。
- 判断：只测durable=false无法发现包装器硬编码false；加true委托及flush调用与失败传播回归。
- 证据：harness-session/src/test/java/io/jharness/session/EventRequestJournalTest.java:155

## OCR-0777 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/InMemorySessionTest.java:75；OCR原严重度：high
- 计划：R0-03 / P1
- 原意见：并发用例可能在 CI 上无限挂起而不是失败（约第 54-77 行）。finally 中的 start.countDown() 是在 try-with-resources 收尾之后才执行的：try 块内的任何断言失败（例如第 68 行 ready.await 超时、第 70/71 行 get 超时）都会先触发 ExecutorService.close()，而 close() 会等待所有任务终止；此时两个 worker 仍阻塞在 start.await()（第 58 行）上，永远无法被释放，进程既不报错也不退出。另外 winner.close() 不在 finally 中，任一断言失败都会泄漏已获得的 writer。建议不要把 latch 的释放放在 try-with-resources 之后：显式声明 ExecutorService，在 finally 中先 countDown() 再 shutdownNow()/close()，并为整个用例加 @Timeout 兜底。
- 判断：latch finally在ExecutorService.close之后，ready超时会关executor等worker而worker等latch死锁；先释放再有界关闭并finally关闭winner。
- 证据：harness-session/src/test/java/io/jharness/session/InMemorySessionTest.java:75

## OCR-0778 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/InMemorySessionTest.java:142；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：这几条断言是自比较：两个投影由同一个 events 列表构造，比较结果几乎恒真，只能证明“两次构造结果一致”，无法发现投影映射错误。例如把 user.message 写成 assistant 角色、assistant.message 顺序错乱、tool.requested 与 tool.completed 未按调用配对、approval 被丢弃（但集合大小仍为 1）时，本用例仍会通过（只校验了 size 和 taskStatus）。建议对 one.messages() 断言完整的期望内容（role/顺序/content）、对 one.toolHistory() 断言事件类型序列并与 callId 对应，再单独保留 two 的确定性比较。
- 判断：两次同实现投影等值只证明确定性，补手写完整messages/toolHistory/approval映射预期。
- 证据：harness-session/src/test/java/io/jharness/session/InMemorySessionTest.java:142

## OCR-0779 · 不采纳

- 位置：harness-session/src/test/java/io/jharness/session/InMemorySessionTest.java:70；OCR原严重度：low
- 计划：无
- 原意见：2 秒的硬编码超时对共享/负载较高的 CI 机器偏紧，容易产生偶发失败（第 68 行的 ready.await 同样是 2 秒）。虚拟线程本身启动很快，但 synchronzied 竞争、GC 或容器 CPU 限流都可能使等待超过 2 秒。建议放宽到 5-10 秒，或改用 @Timeout 统一控制用例上限，避免把超时抖动误报为并发缺陷。
- 判断：未有2秒真实负载失败证据，单纯延长到10秒不证明正确；先修777无限等待并加入确定握手，保留有限deadline。
- 证据：harness-session/src/test/java/io/jharness/session/InMemorySessionTest.java:70

## OCR-0780 · 部分采纳

- 位置：harness-session/src/test/java/io/jharness/session/WriterProbe.java:15；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第17行的成功信号在 try-with-resources 块内部、SessionWriter.close() 释放独占锁（JsonlSessionRepository.Writer 关闭时释放 FileLock 与 OWNERS）之前就打印了，导致两个可复现问题：(1) 若 close() 抛出 HarnessException（释放/force 失败），进程会先输出 APPENDED，再在 catch 中输出错误码并以 23 退出，父进程看到自相矛盾的“成功+失败”输出；(2) 任何以 stdout 作为同步点（读到 APPENDED 即认为锁已释放）的调用方会在锁仍被持有时访问会话，形成竞态和偶发失败。建议把成功输出移到 try 块结束（即资源已关闭）之后。
- 判断：WriterProbe在资源关闭前打印APPENDED可能与close失败同时出现；成功信号应在close确认后，当前父测试还核exit码，不能称单输出就假通过。
- 证据：harness-session/src/test/java/io/jharness/session/WriterProbe.java:15

## OCR-0781 · 不采纳

- 位置：harness-session/src/test/java/io/jharness/session/WriterProbe.java:12；OCR原严重度：medium
- 计划：无
- 原意见：args[0]/args[1] 未做长度与空串校验即直接下标访问。父进程一旦漏传参数（如后续重构成 3 个参数或启动失败），这里抛出 ArrayIndexOutOfBoundsException 而非约定的错误码+退出码 23，父进程只能拿到默认退出码 1 和一段堆栈，测试会以无法定位的方式失败。建议在入口处显式校验并输出可解析的失败信号。
- 判断：测试子程序受控参数，漏参异常会让测试失败，不是产品退出码承诺；不以参数帮助信息单列修复。
- 证据：harness-session/src/test/java/io/jharness/session/WriterProbe.java:12

## OCR-0782 · 不采纳

- 位置：harness-session/src/test/java/io/jharness/session/WriterProbe.java:16；OCR原严重度：medium
- 计划：无
- 原意见：这个 catch 把三个阶段（openWriter 抢锁、append 持久化、close 释放锁）的 HarnessException 混同处理，只输出 ex.code() 且统一 exit 23，父进程无法区分“锁冲突（预期失败）”与“追加/释放失败（真实缺陷）”，只能靠字符串包含判断；同时 Path.of 的 InvalidPathException、NPE、Error 等非 HarnessException 完全不被捕获，会以默认退出码 1 逃逸，并因 redirectErrorStream(true) 把堆栈混入 stdout 协议流。建议输出带阶段前缀的信号，并补充非 HarnessException 的兜底分支映射到独立退出码。
- 判断：父进程会检查具体错误码/退出23，统一HarnessException catch仍保留code可区分；给定“无法区分”不成立，Error不宜兜底伪装正常探针结果。
- 证据：harness-session/src/test/java/io/jharness/session/WriterProbe.java:16

## OCR-0783 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionSearchIndex.java:118；OCR原严重度：high
- 计划：R5-02 / P2
- 原意见：会话事件被截断/回滚时（events 数量少于缓存 documents 且前缀仍匹配），上方的 `while (documents.size() > reused) documents.removeLast()` 只在内存中裁剪，而这里因 `reused == events.size()` 且 `cached` 非空会跳过重写分支，导致已从 JSONL 删除的事件的完整 payload 文本长期残留在 `.search.json` 中（派生索引未随事实源回收，属于数据生命周期缺陷）；同时返回的 `"rebuilt", cached.isEmpty() || rebuild` 也报告 false，客户端会误以为索引未变化。建议在裁剪前记录 truncated 标志并纳入重写条件。
- 判断：events变短但prefix匹配时内存裁剪未触发写回，磁盘保留被删payload；缩短/修复源必须更新缓存且rebuilt语义一致。
- 证据：harness-session/src/main/java/io/jharness/session/SessionSearchIndex.java:118

## OCR-0784 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionSearchIndex.java:102；OCR原严重度：high
- 计划：R5-02 / P1
- 原意见：96MB 的 LIMIT 只在所有事件都已反序列化为完整字符串、documents 全量驻留内存、并且 `JSON.writeValueAsBytes(data)` 被重复序列化多次之后才在 `if (bytes.length > LIMIT)` 处检查。攻击者/超大会话可让堆峰值达到索引大小的数倍（events + documents + text + 序列化副本），在抛出 SEARCH_INDEX_LIMIT 之前就 OOM，与“有界数据处理”要求不符。建议在循环内按 content 长度累计字节数，一旦超过 LIMIT 立即抛出，并对从磁盘读入的 cached documents 做同样的上限估算。
- 判断：索引全量payload转文本/重复serialize后才96MB限，峰值远超缓存限；有界增量累计和流式原子写，缓存读同样先限。
- 证据：harness-session/src/main/java/io/jharness/session/SessionSearchIndex.java:102

## OCR-0785 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionSearchIndex.java:92；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：SessionSearchIndex 缓存行在缓存校验 try/catch 之外做 Values.object 转换和字段强转：索引文件损坏、缺字段或类型错误时会抛 HarnessException/NPE/ClassCastException 直接导致搜索失败，而不是按注释“损坏即重建”。建议把 cached 行转换与逐字段 `instanceof String` 校验放入同一 try，失败回退全量重建。
- 判断：缓存documents行Values.object/字段转换在损坏重建catch之外，畸形派生缓存使搜索失败；完整schema/hash验证后可丢弃重建，绝不改事实日志。
- 证据：harness-session/src/main/java/io/jharness/session/SessionSearchIndex.java:92

## OCR-0786 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionSearchIndex.java:151；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：`found` 是小写副本中的下标，却被用于截取原始 `content`（`start = ... - 80` 以及 `substring(start, start + 512)`）。当 case folding 改变字符串长度时（如 'İ'(U+0130) → "i̇" 两个 char，或代理对边界），偏移会错位，snippet 可能不含匹配词、甚至被 512 边界切断代理对产生非法 UTF-16 字符串。建议在原始字符串上做大小写不敏感匹配（如 regionMatches(true, ...)）或在折叠后按长度差回映射偏移，并保证截断点不切断代理对。
- 判断：lowercase下标用于原串snippet在İ长度扩张错位，512边界也可断代理；保持原始索引映射/Unicode安全窗口回归。
- 证据：harness-session/src/main/java/io/jharness/session/SessionSearchIndex.java:151

## OCR-0787 · 不采纳

- 位置：harness-session/src/main/java/io/jharness/session/SessionSearchIndex.java:35；OCR原严重度：low
- 计划：无
- 原意见：`toRealPath()` 会解析路径中任意一层符号链接，而 `toAbsolutePath().normalize()` 不会，因此只要任一祖先目录是链接（例如 macOS 的 /var、/tmp，或用户 home 下的软链），合法的搜索目录也会被拒绝并抛 “must not be a link”，在 @TempDir 等测试环境/跨平台场景下直接不可用；错误信息也与实际判定不一致（判定的是“路径中不含链接”）。建议仅检查目录自身是否为符号链接（Files.isSymbolicLink / root 与 directory 的 real path 比较），或明确记录该严格限制。
- 判断：严格祖先无链接是文件根契约，macOS测试通过toRealPath规范已修；不接受仅检查末级从而放松边界，说明支持路径即可。
- 证据：harness-session/src/main/java/io/jharness/session/SessionSearchIndex.java:35

## OCR-0788 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:284；OCR原严重度：high
- 计划：R2-01 / P1
- 原意见：prepareArtifacts.accept(target) 在第 284 行执行，但后续的 encode/LOG_LIMIT 检查（286-291）和 publish()（292，内部 parse 还要求事件数 < 10000，见 465 行）都可能失败，且失败没有任何补偿/回滚，宿主已写入的 artifacts 成为孤儿。可复现触发：源会话正好 10000 个事件时 parse 允许打开，prefix 加上新增的 session.forked 记录变成 10001 条 → publish→parse 抛 SESSION_LIMIT，fork 必然失败而 artifacts 已落盘；源日志接近 32MB 导致 buffer 超限时同样触发。建议先完成全部编码与限额校验（并为 forked 记录预留 1 条事件额度），再调用 prepareArtifacts，并在 publish 失败时通过补偿回调清理产物。
- 判断：fork先prepareArtifacts后编码/事件数门禁，10000+forked可失败留孤儿；先完整预算校验并协调临时产物/日志原子发布与补偿。
- 证据：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:284

## OCR-0789 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:470；OCR原严重度：medium
- 计划：R2-03 / P2
- 原意见：parse 只拒绝了 schemaVersion > 1，未校验下限（0 或负数在这里被放行）。这类记录会被第 473 行 SessionEvent 构造器的 IllegalArgumentException（schemaVersion < 1）抛出，再被第 498 行当作“尾部损坏”吞掉：若它是最后一条，REPAIR_TAIL 会静默截断它；若后面还有事件，则变成 MID_LOG_DAMAGED，整份日志不可读。这与 Writer.append（637 行要求恰好为 1）以及 323 行注释 “event schema must already be v1” 不一致，也无法区分“需要迁移的旧 schema”和“损坏记录”。建议显式 `if (schema != 1)` 并给出独立错误码，同时把该码加入 490-495 行的不可修复白名单，避免未知 schema 的事件被当成可截断的损坏尾部而静默丢弃。
- 判断：schema<1被SessionEvent构造拒绝后按损坏处理，并未真正放行；可区分不支持schema与尾损，不能把负版本视作合法历史需迁移或允许悄悄丢事实。
- 证据：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:470

## OCR-0790 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:246；OCR原严重度：critical
- 计划：R2-01 / P1
- 原意见：forkTo() 只用 file(target, …)（246 行）做了路径/类型校验，既没有检查目标会话是否已存在，也没有为 target 申请 .lock、没有加入 OWNERS、没有 synchronized(gate(target))；随后 publish() 在第 407 行用 Files.move(temp, file(id,".jsonl"), ATOMIC_MOVE) 直接作用在目标路径上（未传 REPLACE_EXISTING，是否覆盖依赖实现，Linux 上是静默替换）。触发条件与后果：1) 对已存在的 target 调用 forkTo → 既有会话日志被整体替换、无任何报错，历史事件丢失；2) 此时若 target 正被 openWriter 持有（其 fd 指向旧 inode），后续 append 全部写进已被 rename 掉的旧文件，append 仍返回成功但数据不可见；3) 两个并发 forkTo 指向同一 target 互不排斥，与 243 行注释宣称的 “host-owned idempotent admission” 不符。建议：forkTo 先通过 maintenance(target) 取得目标锁并把 target 纳入 OWNERS，显式拒绝已存在的 target，publish 使用 REPLACE_EXISTING 并置于 synchronized(gate(target)) 内。
- 判断：forkTo只锁source不锁target、不拒已存在目标，ATOMIC_MOVE目标存在行为可平台替换；需目标独占与不覆盖发布，不能照建议加REPLACE_EXISTING覆盖旧事实。
- 证据：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:246

## OCR-0791 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:477；OCR原严重度：high
- 计划：R2-03 / P2
- 原意见：Instant.parse 抛出的 DateTimeParseException 继承自 DateTimeException/RuntimeException，而不是 IllegalArgumentException，因此第 498 行的 catch (IllegalArgumentException) 抓不到它。触发条件：日志尾行是语法合法但时间戳非法的记录（手工编辑、或被截断后仍能解析为字符串的行，如 "timestamp":"2026-13-45T00:00:00Z"）→ 异常直接穿透 parse()，openReader 抛出未归一化的运行时异常而不是 TAIL_DAMAGED/SESSION_READ_FAILED，TailPolicy.REPAIR_TAIL（145-150）与 repairTail() 也都会在同一位置再次抛出，文件永久不可读、不可修复。建议在 498 行并列捕获 DateTimeException（或统一 catch RuntimeException 并配合 490-495 的白名单码）后按尾部损坏/MID_LOG_DAMAGED 语义处理。
- 判断：Instant.parse的DateTimeParseException不是IAE，parse未捕获会绕过尾损分类；明确时间字段错误及尾/中段规则，不让未知版本被截断。
- 证据：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:477

## OCR-0792 · 不采纳

- 位置：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:537；OCR原严重度：medium
- 计划：无
- 原意见：聚合节点预算 250000 与 10000 事件 / 32MB 的会话上限严重不匹配：单条事件记录（10 个字段 + payload）约 20-50 个 token，正常会话在约 5000-7000 条事件时就会触发 RECORD_LIMIT；而 RECORD_LIMIT 在 490-495 行被列为不可修复码、parse 对尾行也会直接抛出，于是完全合法的会话变成永久不可读且 repairTail() 无法修复；Writer.append 复用同一 nodes 累计值（653-654 行），达到 250k 后也会持续拒绝写入，但错误码是 RECORD_LIMIT 而非 SESSION_LIMIT。另外 654 行对 encode() 已完整统计过 token 的记录再解析一遍，属重复开销。建议按 LOG_LIMIT/事件上限重新标定聚合预算（或只保留单记录上限，整会话改用字节数/事件数限额），并让尾部的 RECORD_LIMIT 可修复。
- 判断：节点250000是与32MB/10000条并行的额外上限，writer相同累计门禁阻止写出自己不能读的合法前缀；先达节点上限不等于合法会话永久不可读。禁止将RECORD_LIMIT尾部静默修复删事实。
- 证据：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:537

## OCR-0793 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:97；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：GATES 是静态 Map 且条目从不移除（Writer.close() 只清理 OWNERS），而 openReader()/inspect() 对任意合法 id 都会调用 gate(...) 建项——173/192 行在校验文件是否存在之前就取 gate。触发条件：一个长期存活的 repository 被反复用于探测/读取不同（不存在或已删除）的会话 id（例如 inspect/list 轮询、错误 id），GATES 会无界增长并一直持有 Path 对象，形成内存泄漏。建议按引用计数在最后一个使用者释放时移除条目（Writer.close()、读取完成、maintenance 关闭），或改用弱引用/有界缓存。
- 判断：静态GATES对任意合法id computeIfAbsent不释放，未存在会话探测也累积；采用固定条带或正确引用计数，不能简单LRU淘汰活跃锁导致双写。
- 证据：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:97

## OCR-0794 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:396；OCR原严重度：medium
- 计划：R6-04 / P2
- 原意见：这里以及第 140-141 行（创建新会话日志）、404-407 行（fork 的 ATOMIC_MOVE 重命名）、migrate/repairTail 的重命名路径，都只对文件通道执行 force(true)，从不 fsync 父目录 root。触发条件：新建会话文件/备份文件或 ATOMIC_MOVE 重命名后立刻断电、崩溃，目录项可能尚未落盘，导致已向调用方返回成功的会话文件或重命名结果丢失/指向旧内容——与类注释声明的 “force(true)” 持久化语义不符。建议在 create/rename/delete 之后对 root 目录的 FileChannel 调用 force(true)（POSIX 上 fsync 目录），或显式在文档中降低该持久化承诺。
- 判断：文件force未包含父目录落盘，不可宣称断电目录项保证；明确平台持久化层级，支持时目录fsync并实机验证，不能无条件FileChannel开目录声称Windows等价保证。
- 证据：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:396

## OCR-0795 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:147；OCR原严重度：low
- 计划：R1-02 / P2
- 原意见：第 149 行直接调用 diagnostics.accept(...)，与别处统一的 diagnose()（会吞掉消费者异常）不一致。触发条件：注入的 diagnostics 消费者本身抛异常时，异常发生在 channel.truncate()/force() 之后，会被第 162 行 catch (RuntimeException) 捕获，关闭通道并移除 OWNERS，调用方收到的是会话打开了却失败的结局（且错误码不是 TAIL_* 语义），而文件其实已经被截断修复。建议改用 diagnose("TAIL_REPAIRED")，保持诊断通道异常不影响主流程。
- 判断：尾修复后直接diagnostics.accept可抛异常覆盖已完成操作，使用隔离diagnose并保留实际修复事实。
- 证据：harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java:147

## OCR-0799 · 部分采纳

- 位置：harness-session/src/test/java/io/jharness/session/JsonlSessionRepositoryTest.java:31；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：该断言过弱，无法锁住“累计节点容量”这一被测契约。当前实现累计预算为 250000 个节点，而本用例每条记录约 20012 个节点（10000 个 payload 键 + 10000 个值 + 外层对象/字段名），因此实际边界约为 12 条；但 `accepted > 0 && accepted < 20` 在容量退化为“只能写 1 条记录”时依然通过，节点累计统计失效的回归不会被发现。建议把断言与预算挂钩并给出下界（例如 `assertTrue(accepted >= 10 && accepted < 20)`），或按预算推算期望边界；同时补一条对照用例（小 payload 下连续 20 次 append 全部成功），以证明第 20 次之前的失败确实来自累计容量而非单条记录上限或其它错误码。
- 判断：累计节点测试accepted>0<20下界过宽，可加小payload对照/门槛公式及精确码；不硬编码实现内部12条当接口永久保证。
- 证据：harness-session/src/test/java/io/jharness/session/JsonlSessionRepositoryTest.java:31

## OCR-0800 · 部分采纳

- 位置：harness-session/src/test/java/io/jharness/session/JsonlSessionRepositoryTest.java:230；OCR原严重度：high
- 计划：R0-03 / P1
- 原意见：这里只校验了独立诊断通道，没有校验失败后的落盘状态，从而掩盖了一个可复现的“失败事件被提交”风险：`Writer.append` 是先 `write(channel, bytes)` 再 `barrier.force(channel)`，force 抛 IOException 时记录字节已经写入 `s.jsonl`，而 `Writer.close()` 只关闭 channel/lock，不做截断或回滚。于是一次抛出 CHECKPOINT_FAILED 的 append，其事件在重新打开 session 时仍会被 `openReader("s").events()` 读出并被当作已提交事实（调用方认为失败、恢复时却看到成功），属于 UNKNOWN 状态被重放。建议在本用例中补断言：失败并关闭后重新读取 `s.jsonl`，明确期望该事件不可见（若产品语义允许可见，则需显式断言并说明），否则这一契约的回归不会被任何测试捕获。
- 判断：force失败后字节仍可读是持久性未知而非自动重放证据；补故障后重开实际可见性和禁止发送断言，不强制truncate假装操作未发生。
- 证据：harness-session/src/test/java/io/jharness/session/JsonlSessionRepositoryTest.java:230

## OCR-0801 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/JsonlSessionRepositoryTest.java:259；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：测试名声称 RejectedWithoutAppending，但断言只检查了异常码与 `list()`；`list()` 仅按 `*.jsonl` 文件名枚举，无法发现文件内容被追加，因此“DUPLICATE_EVENT/SESSION_MISMATCH 校验先于写入”的回归不会被捕获。建议在两次失败 append 之前保存 `Files.readAllBytes(root.resolve("s.jsonl"))`，失败后断言字节完全一致（或断言 `openReader("s").events()` 仍只有 1 条）。
- 判断：拒绝重复/跨会话append仅查异常/list不能锁定字节未变；补前后bytes及event列表，验证校验在写前。
- 证据：harness-session/src/test/java/io/jharness/session/JsonlSessionRepositoryTest.java:259

## OCR-0802 · 部分采纳

- 位置：harness-session/src/test/java/io/jharness/session/JsonlSessionRepositoryTest.java:252；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：这里只断言了异常码，没有验证文件系统边界：对 `../outside` 应显式断言 `root` 之外没有产生任何文件（例如 `Files.notExists(root.getParent().resolve("outside.jsonl"))`），并断言无效 id 未在 `root` 内留下 `s.lock` 之外的残留条目——否则路径拼接/规范化逻辑回归（例如改用 `root.resolve(id)` 后再校验）不会被发现。另外 `C:escape` 与 `NUL` 在当前实现里由通用正则拒绝、可跨平台工作，但建议再补一个 POSIX 风格穿越用例，避免平台差异掩盖边界校验缺陷。
- 判断：越界拒绝需同时核工作区外marker未变/无新文件与临时残留；已有../outside是POSIX式穿越，不必重复为同类输入堆测试。
- 证据：harness-session/src/test/java/io/jharness/session/JsonlSessionRepositoryTest.java:252

## OCR-0803 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/JsonlSessionRepositoryTest.java:294；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：篡改内容 `"tampered"`（8 字节）与被替换的 `"[REDACTED] full content"`（23 字节）长度不同，因此仅比较长度/文件大小的伪校验也能通过本用例，无法证明 ARTIFACT_CORRUPT 来自摘要校验。建议先 `store.read` 取回原文，再用等长内容（例如 23 字节）覆盖后断言 ARTIFACT_CORRUPT；同时补一条 scope 前缀相同的隔离断言（如 `s` 与 `s2`），因为当前 `read("other", ...)` 用的是完全不同的 scope，前缀匹配误判无法被捕获。
- 判断：篡改异长不能独立证明SHA校验，使用等字节长度替换及相似scope s/s2隔离回归。
- 证据：harness-session/src/test/java/io/jharness/session/JsonlSessionRepositoryTest.java:294

## OCR-0804 · 部分采纳

- 位置：harness-session/src/test/java/io/jharness/session/JsonlSessionRepositoryTest.java:104；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：子进程缺少失败兜底与清理：`waitFor(10, TimeUnit.SECONDS)` 返回 false 时断言失败但子 JVM 仍在运行，且不会 `destroy()`；该子进程可能继续持有 `s.lock`/文件句柄，导致后续断言或 Windows 下的 @TempDir 清理失败（`afterMaintenance` 与 `second` 同样问题）。此外 stdout 流未关闭，且直接透传 `java.class.path`（Surefire 引导 jar 场景下子 JVM 可能找不到 `WriterProbe`），`assertEquals(23, process.exitValue())` 也没有附带子进程输出，失败时难以定位。建议在 `probe()` 中统一处理超时 `destroyForcibly()` 并用 try-with-resources 关闭输入流，同时把子进程输出作为退出码断言的失败消息。
- 判断：WriterProbe等待超时未finally杀进程可能持锁，统一有界双流/退出清理；Surefire类路径问题需实际证据，不随意替换已成功跨进程夹具。
- 证据：harness-session/src/test/java/io/jharness/session/JsonlSessionRepositoryTest.java:104

## OCR-0816 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/TokenSessionSearch.java:76；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：`synchronized` on the whole `search` serializes every session search on the provider instance, even though the shared resources are already guarded per session ID: `OWNERS.add(lock)` is an atomic per-path claim and the `FileChannel.tryLock` guards the index file. A single large session (re-reading and re-sanitizing all events, up to 8 MB of index encoding) therefore blocks all other sessions' searches in the same JVM, so concurrent per-session queries (e.g. several web requests or a profile-wide parallel sweep) degrade to sequential execution. The method has no instance-level mutable state to protect (`cached`/`documents` are locals, `JSON` is thread-safe), so the monitor should be dropped (or replaced by per-ID striping) and the file lock kept as the only serialization point.
- 判断：TokenSessionSearch整体同步跨会话串行，内建文件锁按id已有隔离；缩锁/有界并发且验证sanitize线程安全，不直接删锁假设所有注入provider安全。
- 证据：harness-session/src/main/java/io/jharness/session/TokenSessionSearch.java:76

## OCR-0817 · 采纳

- 位置：harness-session/src/main/java/io/jharness/session/TokenSessionSearch.java:203；OCR原严重度：low
- 计划：R5-02 / P2
- 原意见：The match offset is computed on the lower-cased shadow string but applied to the original `content`. `toLowerCase(Locale.ROOT)` is not length preserving (e.g. U+0130 `İ` becomes the two chars `i` + U+0307, and case mappings can also change surrogate composition), so `found`/`phrase` offsets from `lower` do not map to positions in `content`. For long documents the resulting snippet is shifted and can miss the actual match entirely (`start` may land after it), and slicing by raw char index can cut a surrogate pair, emitting an unpaired surrogate in the returned snippet. Compute the snippet from a length-preserving view of the content (e.g. case-insensitive scan over `content` itself) or map the lower-case offset back to code-point boundaries before `substring`.
- 判断：lowercase位置用于原文snippet同786，统一Unicode位置映射与代理边界回归。
- 证据：harness-session/src/main/java/io/jharness/session/TokenSessionSearch.java:203

## OCR-0818 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/TokenSessionSearch.java:87；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：Invalid input can escape as `NullPointerException` instead of the intended `HarnessException(INVALID_SEARCH)`. `Set.of(...)` is an immutable collection whose `contains`/`probe` calls `Objects.requireNonNull(o)`, so `search(id, "x", null, ...)` throws NPE from this line, bypassing the protocol error mapping. The same applies to `cancellation.throwIfCancelled()` a few lines below when a caller passes a null token. Validate `mode` and `cancellation` explicitly (or compare with `"tokens".equals(mode)` / `"phrase".equals(mode)`) so bad client input always maps to INVALID_SEARCH.
- 判断：search null mode触Set.contains NPE，公共ContentSearch可结构化拒绝；Web入口已字符串校验，null token属宿主违规，不当远程崩溃。
- 证据：harness-session/src/main/java/io/jharness/session/TokenSessionSearch.java:87

## OCR-0819 · 部分采纳

- 位置：harness-session/src/main/java/io/jharness/session/TokenSessionSearch.java:68；OCR原严重度：medium
- 计划：R5-02 / P1
- 原意见：Path validation and use are not atomic: `file()` checks `root` is still a real directory and that the target is not a non-regular file, but every later operation (`Files.createTempFile(root, ...)`, `Files.move(..., index, ...)`, `Files.newInputStream(index, ...)`) re-resolves the path from a String. If any ancestor of `root` is renamed and replaced by a symlink between the check and the use, the temp file and the index are created/moved into a directory outside the intended root (the file name `<id>.content.json` is predictable), and reads can be redirected to an arbitrary regular file. `NOFOLLOW_LINKS` only rejects a symlink at the final component, not a swapped ancestor or a hardlink. Verify `root.toRealPath().equals(root)` immediately before each create/move/open, or perform the operations relative to an already-open directory handle (`SecureDirectoryStream`).
- 判断：父目录替换窗口存在于路径式缓存读写；使用绑定目录能力/原子临时文件及NOFOLLOW，重新realpath只缩窗不彻底消竞态，派生缓存不可成为事实写入通道。
- 证据：harness-session/src/main/java/io/jharness/session/TokenSessionSearch.java:68

## OCR-0825 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/TokenSessionSearchTest.java:137；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：遍历用例只断言抛出 HarnessException，未校验错误码。如果实现对 "../outside" 抛出 CANCELLED、INVALID_SEARCH 等其它 HarnessException，测试依然通过，无法证明是路径边界校验（INVALID_SESSION_ID / SEARCH_PATH_DENIED）生效。建议断言具体 code。
- 判断：traversal只异常类型，补精确路径拒绝码及无外部副作用，避免无关取消假通过。
- 证据：harness-session/src/test/java/io/jharness/session/TokenSessionSearchTest.java:137

## OCR-0826 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/TokenSessionSearchTest.java:110；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：测试方法名声称验证 model request 被排除，但这里只追加了 user.message 和 config.selected，从未追加仓库中真实存在的 model.requested 事件（见 SessionRecovery/EventRequestJournal）。TokenSessionSearch.TYPES 白名单当前恰好不含 model.requested，因此若后续有人把它加入白名单，本测试仍会通过，无法发现模型请求载荷被索引进索引的回归。建议追加一个 model.requested 事件（payload 含可检索词），并断言该词不可检索且 indexedEvents 仍为 1。
- 判断：测试名排除model.requested但未写该事件，补独特敏感检索词并断言不索引；保持正文白名单范围。
- 证据：harness-session/src/test/java/io/jharness/session/TokenSessionSearchTest.java:110

## OCR-0827 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/TokenSessionSearchTest.java:86；OCR原严重度：medium
- 计划：R0-03 / P1
- 原意见：方法名包含 LeakNewlyKnownSecrets，但 setup 中构造 SecretRedactor 时就已传入 synthetic-search-key，索引也是在该 secret 已“已知”的情况下建立的；SecretRedactor 是不可变对象，没有新增 secret 的接口。因此测试只验证了“建索引时已知 secret 会被脱敏”，并未覆盖“索引先以明文建立、secret 之后才被知悉”的泄漏场景。建议先以空的 SecretRedactor 建索引，再用包含该 secret 的新 TokenSessionSearch 查询，断言旧缓存明文不会被命中且索引被重写为 [REDACTED]。
- 判断：LeakNewlyKnownSecrets用例secret从起始已知，缺旧缓存明文到新secret配置重建的覆盖；两实例/同缓存测试无命中且安全重写。
- 证据：harness-session/src/test/java/io/jharness/session/TokenSessionSearchTest.java:86

## OCR-0828 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/TokenSessionSearchTest.java:128；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：同一 JVM 内对同文件再次加锁会抛 OverlappingFileLockException，而不是走 TokenSessionSearch 第 103 行 tryLock()==null 的跨进程忙路径；实现只是把 OLE 映射为 SEARCH_BUSY。因此本用例只覆盖了 OLE 分支，真正的跨进程锁竞争（owned == null）分支删掉后测试仍会通过；assertTrue(lock.isValid()) 也只是断言测试自己持有的锁。另外用例在释放外部锁后没有再次查询，无法验证 SEARCH_BUSY 路径上锁/OWNERS 资源被正确释放。建议补充跨 JVM/独立进程的竞争用例，并在释放锁后断言查询恢复成功。
- 判断：同JVM锁只覆盖OverlappingFileLockException，补跨进程tryLock=null及释放后成功，证明OWNERS/文件锁回收。
- 证据：harness-session/src/test/java/io/jharness/session/TokenSessionSearchTest.java:128

## OCR-0829 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/M5SessionTest.java:183；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：三个断言只校验 `HarnessException` 类型，不校验错误码；且 `fork("s", "absent")` 用的 eventId 在日志中根本不存在。当前实现里 `forkTo` 先 `parse` 再查事件（所以损坏会先抛 FUTURE_VERSION_READ_ONLY / MID_LOG_DAMAGED），但一旦顺序调整或损坏被包装成 EVENT_NOT_FOUND / SESSION_WRITE_FAILED，本用例依然通过，无法证明「未来版本与中段损坏被拒绝且无副作用」。建议改为 `code(...)` 断言具体错误码，并用真实存在的 eventId 调 fork；另建议补充断言 root 下未遗留 `.fork-*` / `.maintenance-*` 临时文件（`publish`/`maintain` 都用随机临时名，`repo.list()` 只匹配 `*.jsonl`，看不到泄漏）。
- 判断：坏日志fork使用不存在event且仅异常类型，改真实event和精确未来/中损码并检查临时文件无新残留。
- 证据：harness-session/src/test/java/io/jharness/session/M5SessionTest.java:183

## OCR-0830 · 部分采纳

- 位置：harness-session/src/test/java/io/jharness/session/M5SessionTest.java:115；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：冲突路径只断言了错误码，没有验证 export 失败后目标位置无残留。`export` 先取 `maintenance` 锁再写目标，一旦实现改为先 `CREATE_NEW` 目标（或先写部分字节再发现冲突），「不覆盖、失败不产生半成品」的契约就被破坏而本用例仍然通过。建议补 `assertFalse(Files.exists(root.resolve("out")))`；同时在 writer 关闭后用 `new JsonlSessionRepository(root)` 重新打开独占 writer 并做一次 `repairTail`，用跨实例路径证明异常分支里 `OWNERS` 与 OS 文件锁都被释放（当前只复用同一实例，`owns` 状态被去掉但 `FileLock` 泄漏也只在同 JVM 重开时才暴露）。
- 判断：export冲突后需核无目标半成品和锁可重取；同JVM新实例仍受OS锁，跨进程对照更完整，不声称原复用实例看不到FileLock泄漏。
- 证据：harness-session/src/test/java/io/jharness/session/M5SessionTest.java:115

## OCR-0831 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/M5SessionTest.java:265；OCR原严重度：high
- 计划：R0-03 / P1
- 原意见：被拒绝的 fork 只用 `repo.list()`（仅匹配 `*.jsonl`）确认没有新会话日志，完全没有检查 artifacts 目录。`LocalArtifactStore.copyScope` 在完整性校验之前就调用 `directory(target, true)` 建目录，父作用域被篡改时会在 artifacts 下留下一个空的子作用域目录（资源/边界泄漏），而本断言无法发现；同理，失败路径也不校验 sessions 目录下的 `.fork-*` 残留。建议在 fork 前快照 `root.resolve("artifacts")` 的子目录集合，断言失败后只剩 `s` 与第一次成功 fork 的 child 作用域，并断言失败后无法通过任何新 child id 读到产物。
- 判断：fork失败只repo.list看不到孤儿artifacts，补两目录前后快照及未公布target不可读，绑定737/788实际缺陷。
- 证据：harness-session/src/test/java/io/jharness/session/M5SessionTest.java:265

## OCR-0832 · 部分采纳

- 位置：harness-session/src/test/java/io/jharness/session/M5SessionTest.java:292；OCR原严重度：high
- 计划：R0-03 / P1
- 原意见：该用例只检查 `ready()/uncertainCalls()/pendingRequests()` 这些「状态快照」，从未触发任何恢复/重放入口，因此「UNKNOWN 状态不得被重放」这条强制安全约束在本套件中没有任何断言覆盖。若下游把 WORKSPACE_WRITE 的 `tool.intent`（call）在恢复时重新执行，本用例仍全绿。建议补断言：对 fork 出的子会话走恢复执行路径，断言 call 仍处于未验证且未产生新的 `tool.started`/`tool.completed` 事实、也未调用执行器（可用带副作用计数的执行器桩）。
- 判断：本M5用例只状态不调恢复执行，补marker/调用计数验证未知不执行；其它M4/M9已有端到端，不因此称全仓无禁止重放测试。
- 证据：harness-session/src/test/java/io/jharness/session/M5SessionTest.java:292

## OCR-0833 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/M5SessionTest.java:324；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：用例名声称 ReportsDamage，但只用总数 3 证明损坏会话「被计入」，没有任何关于 `diagnostic`/`ready` 字段的断言。`SessionCatalog.query` 的 catch 分支构造的是 `{session, diagnostic, ready}` 行；如果实现把诊断字段丢掉、改成一个看似正常的行（status/eventCount 填充为默认值），总数 3 依然成立，静默吞掉损坏不会被发现。建议对 session="broken" 的那条记录断言 `diagnostic` 非空且等于具体错误码、`ready()==false`，并断言 workspace/model 过滤时损坏会话不会伪造出匹配项（目前只靠空结果间接覆盖）。
- 判断：damage测试只size=3不能证明diagnostic/ready=false，补broken行固定错误及过滤不伪造匹配。
- 证据：harness-session/src/test/java/io/jharness/session/M5SessionTest.java:324

## OCR-0834 · 采纳

- 位置：harness-session/src/test/java/io/jharness/session/M5SessionTest.java:372；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：方法名承诺 stable pagination，但会话里只有一个匹配事件，`offset=1` 为空这一断言无法发现越界分页、跨页重复/漏行问题（`SessionQuery` 的 offset/limit 在查询里是「先匹配计数再切片」，off-by-one 最容易出现在这里）。建议写入 3 条 `tool.requested`，断言 offset=0/limit=2 与 offset=2/limit=2 拼接后恰好等于全量且无重复，并覆盖 offset 等于总数、limit 大于剩余数量的边界。
- 判断：单匹配分页不能覆盖跨页漏重，补多条完整拼接与边界，验证稳定顺序。
- 证据：harness-session/src/test/java/io/jharness/session/M5SessionTest.java:372

## OCR-1059 · 采纳

- 位置：harness-tools/src/test/java/io/jharness/tools/JsonSchemaValidatorTest.java:78；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：这三处 `assertThrows(HarnessException.class, ...)`（本行、line 79-88 的重叠 `oneOf(number, integer)` 用例、line 90-92 的 `type: boolean` + `"true"` 用例）只校验异常类型，未校验 `code()`。触发条件：`JsonSchemaValidator.checkValue` 若把实例级失败误报为 `INVALID_SCHEMA`（例如 `checkSchema` 递归深度/关键字白名单在 `validate` 里被重复触发，或 `invalid("Invalid schema type")` 走到 line 177），这些用例仍会通过。本文件其余断言都精确比对 `"INVALID_SCHEMA"` / `"SCHEMA_MISMATCH"`，因此这里的不一致会掩盖错误分类回归；尤其重叠 `oneOf` 用例的语义就是“两个分支都匹配 → SCHEMA_MISMATCH”，必须校验该码。建议统一改为 `assertEquals("SCHEMA_MISMATCH", assertThrows(...).code())`。
- 判断：enum/oneOf/boolean负例只宽泛异常，补SCHEMA_MISMATCH与INVALID_SCHEMA区分，防声明错误伪装值不匹配。
- 证据：harness-tools/src/test/java/io/jharness/tools/JsonSchemaValidatorTest.java:78

## OCR-1060 · 采纳

- 位置：harness-tools/src/test/java/io/jharness/tools/JsonSchemaValidatorTest.java:57；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：line 30-51 的 schema 声明了 `maximum: 3`、`maxItems: 2` 和 `minLength: 1`，但 line 55-60 的非法实例只覆盖了下界与类型：`count=0` 打 `minimum`、`count=1.5` 打整数判定、空数组打 `minItems`、`"long-name"` 打 `maxLength`、多余属性打 `additionalProperties`、缺键打 `required`。触发条件：`JsonSchemaValidator.bounds`（line 185-186）的上界比较写错（如 `>` 写成 `<`）或 `maxItems`/`minLength` 被忽略时，本用例集全部通过，无法发现回归。建议补充 `count=4`、`names` 含 3 个元素、`names` 含空串三类实例，分别锁住 `maximum`、`maxItems`、`minLength`。
- 判断：maximum/maxItems/minLength声明未有反例，补恰好边界/越界对照而非镜像实现测试。
- 证据：harness-tools/src/test/java/io/jharness/tools/JsonSchemaValidatorTest.java:57

## OCR-1061 · 采纳

- 位置：harness-tools/src/test/java/io/jharness/tools/JsonSchemaValidatorTest.java:52；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：合法实例用的是 `"甲"`（BMP 字符，UTF-16 长度与码点数均为 1），而 `JsonSchemaValidator.checkValue` line 158 用 `text.codePointCount(0, text.length())` 计算长度。触发条件：若实现退化为 `text.length()`（或 `maxLength` 以 UTF-16 单元计），本用例与 `"long-name"` 用例仍全部通过，星平面字符的长度语义无任何覆盖。建议加入代理对字符并配合 `maxLength: 4`：3 个 emoji（3 码点/6 单元）应通过、5 个 emoji 应失败，才能区分两种计数方式。
- 判断：BMP字符串无法区分codepoint/UTF16，补3与5 emoji的maxLength回归。
- 证据：harness-tools/src/test/java/io/jharness/tools/JsonSchemaValidatorTest.java:52

## OCR-1062 · 采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java:185；OCR原严重度：high
- 计划：R1-01 / P1
- 原意见：并发窗口：守卫在 L175 检查 `finished.get()`，但真正的进入标记要到 L185 才 CAS `entered`，L189 才执行工具体；而 `finish()` 在 L237 独立 CAS `finished` 且不等待 `entered`。触发路径是真实存在的：`BoundedReadOnlyToolScheduler.Run.drain()`（L215-247）在工作线程未完成时会用 `SCHEDULER_WORKER_UNKNOWN` 提前调用 `prepared.finish(...)`，此时 worker 仍可能停在 L175 与 L185 之间，于是工具体在“已结算”之后仍然被执行——结算事件里 `bodyEntered=false`/UNKNOWN，但副作用已经发生，且调用方已按 UNKNOWN 处理，可能触发重放。建议把 `finished` 复检放到 `entered` CAS 之后、`tool.execute` 之前（或统一用单个状态机 CAS：PREPARED→ENTERED / PREPARED→SETTLED，二者互斥）。
- 判断：run先finished检查后entered CAS，finish可中间结算后主体继续进入；需单一原子状态机防结算后进入，并保持owner结算/UNKNOWN不重放。
- 证据：harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java:185

## OCR-1063 · 部分采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java:198；OCR原严重度：high
- 计划：R1-01 / P1
- 原意见：L191-201 的防伪造检查只看 `entered`，无法区分“工具真的执行出的结果”和“包装器自造的结果”：(1) 恶意/有缺陷的 TOOL_EXECUTION 扩展可以先调用 guard（`entered` 变为 true、真实副作用已发生），再返回自己构造的 SUCCESS 结果，只要能通过 L197/L202 的输出 schema 就会被当成真实成功；(2) 反过来，扩展可以调用 guard 后吞掉工具异常并返回 FAILED/CANCELLED，于是本应因“副作用不确定”而记为 UNKNOWN 的非只读调用被降级为 FAILED，绕过 L231 的 UNKNOWN 判定，进而被安全重放。触发条件是任何注册了 TOOL_EXECUTION 扩展点的插件。建议在 guard 内记录工具真实返回值，并在包装器返回后要求二者一致（仅允许把状态降级为 UNKNOWN），而不是只判断 `entered`。
- 判断：扩展在真实进入后可改状态掩盖未知，需要明确包装可修改范围和保守不确定性合并；可信Java插件不是恶意沙箱，不禁止合法结果拦截/降级或要求结果恒等。
- 证据：harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java:198

## OCR-1064 · 部分采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java:242；OCR原严重度：high
- 计划：R1-01 / P1
- 原意见：结算只对 SUCCESS 做一致性校验：任何非 SUCCESS 的 raw（`FAILED`/`CANCELLED`/`UNKNOWN`/`DENIED`）都会被无条件接受，而且 L237 的 CAS 之前没有检查 `dispatched`，因此一个 `entered == true` 且 `effect != READ_ONLY` 的调用，其 `failure()` 产生的 UNKNOWN（L231）可以被调度方替换成 FAILED/CANCELLED 后结算，`BoundedReadOnlyToolScheduler.execute` 中 L79-81 的 `UNKNOWN_SIDE_EFFECT` 拦截随之失效，调用会被当作可安全重放。同理，未调用 `run()` 直接 `finish(FAILED)` 也能把“从未执行”悄悄记成失败。建议在 `entered && effect != READ_ONLY` 时只接受 UNKNOWN，或在 `raw` 与 `outcome` 不一致时统一抛错。
- 判断：finish仅防伪SUCCESS，未保护已知UNKNOWN被调度器降级；保留未知粘滞与真实outcome，不可按建议entered非只读就全UNKNOWN（正常成功也entered）。
- 证据：harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java:242

## OCR-1065 · 部分采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java:237；OCR原严重度：medium
- 计划：R1-01 / P1
- 原意见：结算标记先于校验/审计落地：`finished` 在 L237 就被置位，而 L239 的 callId 校验、L243 的脱敏、L251 的 `tool.completed` 发布都在其后。触发条件：raw 的 callId 不匹配（RESULT_CALL_MISMATCH）、脱敏器抛异常（`ToolResults.sanitized` 内部 `(String) payload.get("content")` 强制转换、`Values.object(payload.get("value"))` 对被打码成字符串的 value 都会抛）或事件总线写入失败（如会话已关闭）。后果是该调用被永久关闭，既不返回结果也没有 `tool.completed` 事实写入日志，恢复/审计时该次调用凭空消失，且无法再结算一次。建议先完成 callId 校验，再 CAS 结算；事件发布失败时至少补写一条降级的完成/失败事件。
- 判断：finished CAS早于纯校验，错误callId/sanitize失败会占结算且无完成事实；前置纯校验、清晰SETTLING/SETTLED，审计失败保留未决。不能writer失败时强补假完成。
- 证据：harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java:237

## OCR-1066 · 部分采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java:245；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：输出 schema 校验发生在脱敏之前：`validate()`（L197/L210，实现见 L255-266）校验的是工具/后处理器的原始值，而 L243 的 `ToolResults.sanitized` 会用脱敏后的 `value`/`content`/`display` 重新构造 ToolResult。触发条件：脱敏器把嵌套对象替换为字符串（如 "***"/"REDACTED"）或删除字段，使被返回的 SUCCESS 结果不再满足 `tool.outputSchema()`。后果是返回给上层/写入事件的 SUCCESS 值违约，下游按 schema 取值时可能 NPE/ClassCast，而且日志里的值与被校验的值不一致。建议对脱敏后的结果再校验一次，或在脱敏导致形状变化时明确降级状态而不是静默返回 SUCCESS。
- 判断：脱敏可改变输出形状，需定义可见schema/红删状态并防SUCCESS违约；不能为了schema恢复秘密原值，保留操作已发生的真实事实。
- 证据：harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java:245

## OCR-1067 · 不采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java:29；OCR原严重度：low
- 计划：无
- 原意见：`prepare` 永远返回 `Optional.of(state)`——即使准备阶段抛异常也只是把失败折叠进 `preliminary`。因此 L20 的 `orElseThrow()` 是不可达的死代码，`BoundedReadOnlyToolScheduler.execute` L52 的 `prepared.isEmpty()` 分支在该实现下也永远不会命中，调用方对“无法准备”的兜底逻辑形同虚设。建议要么返回 `Optional.empty()` 表示准备失败，要么把返回类型改成非 Optional 并同步调用方。
- 判断：公共ToolPipeline Optional支持其它不可准备实现；默认总of包含preliminary失败用于结算，不是可删除的跨实现死代码。
- 证据：harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java:29

## OCR-1068 · 不采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/ToolSchedulingPlugin.java:42；OCR原严重度：medium
- 计划：无
- 原意见：拦截器内每次批量执行都用 `requireUnique(ToolScheduler.class, scope)` 重新解析服务，而不是使用本插件已持有并注册的 `scheduler` 字段。触发条件：宿主按文档（m8-tool-scheduling.md 把 `ToolScheduler` 服务列为可替换入口）在本插件启用的同一 scope 里再注册一个 key 不同的 `ToolScheduler`（例如自定义调度器 key 为 "custom"），此时同一 scope 内存在两个实现，`requireUnique` 会抛 `AMBIGUOUS_SERVICE`，导致每次 `TOOL_BATCH` 全部失败，而插件手里的 `scheduler` 本可直接使用；同理，宿主若用 "default" key 预注册自定义调度器，则 `start()` 中 `context.register` 会因 `DUPLICATE_SERVICE` 使整个容器启动失败。由于此处只需要本插件自己持有的实例，直接调用字段即可消除该失败面。
- 判断：按scope重新requireUnique是撤销后旧引用防复活的明确机制，不能直接scheduler字段绕过；多key注册不是替换的正确方式，歧义拒绝符合契约。
- 证据：harness-tools/src/main/java/io/jharness/tools/ToolSchedulingPlugin.java:42

## OCR-1069 · 采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/ToolSchedulingPlugin.java:30；OCR原严重度：low
- 计划：R3-01 / P2
- 原意见：`context.own(scheduler)` 会把宿主通过 `ToolSchedulingPlugin(ToolScheduler)` 传入的实例纳入容器所有权，容器 stop 时由 `Context.close()` 调用其 `close()`；但类注释只写了 “hosts can supply a replacement scheduler”，未声明所有权转移。触发条件：宿主把一个跨容器共享/长生命周期的调度器实例传给多个 `HarnessSdk`/子智能体容器，任一容器停止就会关闭该共享实例，其它容器后续批次将得到 `SCHEDULER_CLOSED`。建议在类注释中明确“传入的调度器所有权转移给插件，stop 时会被关闭”，或提供不接管所有权的构造方式。
- 判断：传入scheduler会context.own移交关闭所有权，需明确不可跨容器共享及借用方案，防意外关其它任务。
- 证据：harness-tools/src/main/java/io/jharness/tools/ToolSchedulingPlugin.java:30

## OCR-1070 · 部分采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/JsonSchemaValidator.java:189；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：decimal() 同时用于 schema 约束和待校验值/const/enum 比较。值可能来自程序化调用或允许非数值的 Jackson 解析，例如 Double.NaN / POSITIVE_INFINITY：new BigDecimal(n.toString()) 抛 NumberFormatException 后这里抛的是 INVALID_SCHEMA，于是 validate(Map.of("type","number"), Double.NaN) 被报告为“schema 非法”而不是 SCHEMA_MISMATCH（第 162、200 行都会走到这里）。更糟的是第 114-118 行的 oneOf 分支只吞 SCHEMA_MISMATCH，一个 NaN 会让整个分支求值以 INVALID_SCHEMA 中止。建议拆分 schemaDecimal()（非法约束 → INVALID_SCHEMA）与 valueDecimal()（非有限值 → mismatch()），第 162、200 行改用 valueDecimal()。
- 判断：程序直接validate NaN被decimal归INVALID_SCHEMA，需值与schema错误区分；JSON/Values边界已拒非有限数，不能当模型JSON可达漏洞。
- 证据：harness-tools/src/main/java/io/jharness/tools/JsonSchemaValidator.java:189

## OCR-1071 · 部分采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/JsonSchemaValidator.java:198；OCR原严重度：high
- 计划：R3-01 / P2
- 原意见：sameJson 递归完全没有深度上限或环检测，而 checkValue 的 depth 限制（第 109 行）不会作用于这里，checkSchema 也不校验 const/enum 载荷内容。只要 const/enum 里存在自引用的 List/Map（或调用方构造的极深 value），validate 就会无限递归直至 StackOverflowError；StackOverflowError 不属于 HarnessException，会绕过管线的错误分类处理。建议增加 depth 参数（例如与 checkValue 一致的 64）并使用 IdentityHashMap 做环检测，超限/成环时按 mismatch() 失败。
- 判断：sameJson递归无自身深度/环检，公共直接Java调用可构造极深const/value；在schema/value统一JSON快照边界处理，正常工具Values已限深64。
- 证据：harness-tools/src/main/java/io/jharness/tools/JsonSchemaValidator.java:198

## OCR-1072 · 采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/JsonSchemaValidator.java:52；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：子 schema 未经 instanceof 检查就交给 Values.object，坏 schema 会抛出 INVALID_JSON（非 JSON 类型时甚至是 IllegalArgumentException），而不是本类约定的 INVALID_SCHEMA。例如 {"type":"object","properties":{"x":"oops"}}、{"type":"array","items":true} 以及第 58、64 行的 items/oneOf 都会以 INVALID_JSON 失败；McpPlugin、DefaultToolPipeline 等只按 INVALID_SCHEMA 判定“声明非法”，fail-closed 的错误码契约被破坏。建议先判断 instanceof Map，否则统一调用 invalid("Invalid nested schema")。
- 判断：嵌套非法schema经Values.object报INVALID_JSON非INVALID_SCHEMA；统一声明错误且保留失败前无执行。
- 证据：harness-tools/src/main/java/io/jharness/tools/JsonSchemaValidator.java:52

## OCR-1073 · 部分采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/JsonSchemaValidator.java:75；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：checkSchema 只校验 enum 是非空列表，完全没有校验 enum 元素以及 const 的 JSON 类型和嵌套深度（const 甚至完全没有检查）。任意 Java 对象、可变对象或环状结构都能通过 schema 预校验，随后在第 122/125 行进入无界的 sameJson：可能栈溢出，或通过 Objects.equals 得到与 JSON 语义不一致的匹配结果。建议在此对 enum 的每个元素和 const 递归校验为合法 JSON 标量/容器并限制深度，否则 invalid。
- 判断：const/enum仅列表形状检查，接受声明JSON域/深度限制；合并1071而不重复一个栈溢出根因多立项。
- 证据：harness-tools/src/main/java/io/jharness/tools/JsonSchemaValidator.java:75

## OCR-1074 · 部分采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/JsonSchemaValidator.java:131；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：value 虽已通过 instanceof Map<?,?>，但 Values.object 在遇到非 String 键或非 JSON 类型值时会抛 INVALID_JSON / IllegalArgumentException，把“值不符合 schema”错误地报告成 schema 错误，且 IllegalArgumentException 不在 HarnessException 体系内，可能逃逸上层错误处理。建议先检查所有键均为 String（否则 mismatch()），或把这两类异常统一转换为 SCHEMA_MISMATCH；第 134、141、153 行的 Values.object 调用同理。
- 判断：公共validate非String键/非JSON值报INVALID_JSON或IAE，需实例错误归SCHEMA_MISMATCH；正常管线Values已有JSON化，区分SPI健壮性。
- 证据：harness-tools/src/main/java/io/jharness/tools/JsonSchemaValidator.java:131

## OCR-1075 · 部分采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/JsonSchemaValidator.java:40；OCR原严重度：low
- 计划：R3-01 / P2
- 原意见：schema 为 null 时 schema.keySet() 直接抛 NPE。validate()/checkSchema() 是 public 入口，DefaultToolPipeline 直接传入 tool.inputSchema()；一旦某个 AgentTool 实现返回 null，就会抛出不可识别的 NPE，而不是本类承诺的失败封闭错误。建议在入口或此处加 null 判断并 invalid("Schema is required")。
- 判断：null工具schema会NPE，统一注册/声明校验并带工具名，不能用空schema默认放开未知输入。
- 证据：harness-tools/src/main/java/io/jharness/tools/JsonSchemaValidator.java:40

## OCR-1076 · 部分采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:175；OCR原严重度：high
- 计划：R1-01 / P1
- 原意见：第 175-178 行把 `sideEffectUncertain` 的判断写成了 `UNKNOWN || (status != CANCELLED && uncertain)`（`&&` 优先级高于 `||`）。触发条件：执行包装器/工具返回 `status=CANCELLED` 且 `value.sideEffectUncertain=true`（例如取消发生在已产生部分副作用之后）。此时 stopReason 被置为 "CANCELLED"，而 `execute()` 第 82-84 行的 `if (run.stopped) throw ...` 在最终的 sideEffectUncertain 扫描（第 85-88 行）之前执行，导致不确定副作用被降级成普通取消上报，调用方可能据此重放该调用并造成重复副作用。建议无条件优先判定 sideEffectUncertain，并在 execute() 中把不确定副作用检查放到 stopped 分支之前。
- 判断：CANCELLED且sideEffectUncertain优先级落CANCELLED，先stopped再扫uncertain会影响错误码；应未知优先，同时现有journal保留标志/Loop复核，不宣称必然自动重放。
- 证据：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:175

## OCR-1077 · 部分采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:228；OCR原严重度：high
- 计划：R1-01 / P1
- 原意见：第 221 行 grace 到期后 `break`，此处的 task 仍可能在 `prepared.run()` 内执行，但第 246 行仍然调用 `task.prepared.finish(result)` 并 `pending.clear()`。触发条件：worker 运行超过 `timeout + 2s` 宽限期。后果：(1) `run()` 与 `finish()` 在两条线程上并发执行，违反 PreparedToolCall 的 owner-thread 结算契约（`DefaultToolPipeline.Call.finish()` 会置 `finished` 并发布 `tool.completed`，而工具体可能仍在写数据，形成已完成事件与实际执行不一致）；(2) worker 最终返回的真实结果被丢弃且无人结算，恢复阶段只能看到一个被标记完成的调用。建议在 worker 确认终止前不要调用 `finish()`，或改为由 worker 侧完成结算/显式记录未结算状态。
- 判断：宽限后用UNKNOWN owner finish在worker仍run是有意有界收尾，不应无限等或让worker写journal；需解决run/finish状态竞争并显式body仍活，保证后续batch拒绝直至退出。
- 证据：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:228

## OCR-1078 · 采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:248；OCR原严重度：high
- 计划：R1-01 / P1
- 原意见：`pending` 只在整个循环结束后才清空，缺少异常安全。`DefaultToolPipeline.Call.finish()` 用 CAS 保护并在二次结算时抛出 `REPEATED_SETTLEMENT`。触发条件：`drain()` 中某个 `finish()`（或 `accept()` 内的 EventSupport 写入）抛异常；`execute()` 第 93 行的 catch 再次调用 `drain()`，于是已结算的任务被重复处理，立刻抛出 `REPEATED_SETTLEMENT`，其余尚未结算的 pending 调用（包括可能是 UNKNOWN 的调用）永远不会被 `finish()` 记录，工具事实与结果列表丢失。建议每结算完一个 task 就立即从集合中移除（或在进入 drain 时先快照并 clear），保证每个 PreparedToolCall 只被结算一次。
- 判断：drain部分finish后异常pending未移除，catch再次drain触REPEATED_SETTLEMENT阻断剩余结算；逐项标记并只结算一次，保留首因。
- 证据：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:248

## OCR-1079 · 采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:218；OCR原严重度：high
- 计划：R1-01 / P1
- 原意见：`Run.context` 的取消信号是 `外部取消 || run.cancel || closed`（第 148 行），而 worker 在结果触发停止时会执行 `cancel.set(true)`（第 206 行），`abort()` 自身也会置位。因此内部停止会被这里的 `context.cancellation().isCancelled()` 误判为外部取消，在结果被 accept 之前就 `abort("CANCELLED")` 并固定 stopReason。触发条件：某个并行调用返回 TIMEOUT / CHECKPOINT_FAILED / sideEffectUncertain 并让 worker 置位 cancel，随后 drain 等待其它任务时命中该分支；最终批次以 "CANCELLED" 上报，`accept()` 里的 TIMEOUT/CHECKPOINT_FAILED 分支因 stopReason 非空而被忽略。`close()` 造成的停止同样会被上报为 CANCELLED 而非 SCHEDULER_CLOSED。建议把“外部取消”和“内部停止”拆成两个标志，只对原始 `context.cancellation()` 做 CANCELLED 判定。
- 判断：Run.context取消混合内部cancel/closed，drain把内部TIMEOUT误归CANCELLED；保留原取消源与最高影响原因，测试多项竞态。
- 证据：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:218

## OCR-1080 · 部分采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:267；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：`close()` 只中断执行遍历时快照到的 workers，与 `execute()` 的 `closed` 检查（第 30 行）之间没有互斥：`execute()` 通过检查后仍可在第 211-212 行 `workers.add(...)` / `start()`，或在第 56/74 行直接执行工具体。触发条件：`close()` 与 `execute()` 并发。该 worker 永远不会收到中断，可能在 close 之后继续执行工具体，作用域关闭/回滚语义被破坏。建议在 `workers.add` 之后重新检查 `closed` 并拒绝启动（或对 close/launch 使用同一把锁保证注册与中断互斥）。
- 判断：close与worker注册/start存在窗口，需关闭/注册原子协议并保证晚启动立即退出；有合作token仍不能证明不会进入主体。
- 证据：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:267

## OCR-1081 · 采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:51；OCR原严重度：medium
- 计划：R1-01 / P1
- 原意见：此处（以及第 66 行 `run.drain(); if (run.stopped) break;` 之后）直接 `break`，已 prepare 的 PreparedToolCall 既不执行也不 `finish()`，被静默丢弃。触发条件：`prepare()` 内部的 barrier 触发 `run.drain()` 发现停止（DefaultToolPipeline 会把 barrier 抛出的 SCHEDULER_STOPPED 吞掉并返回 preliminary 失败结果），或 prepare 返回后 drain 才发生停止。后果：该调用已产生 `tool.requested`（某些路径还有 `tool.started`、checkpoint 刷新），却永远不会产生 `tool.completed`，`results` 中也缺少该调用，恢复/审计时会看到悬空的进行中事实。建议在 stopped 时也对该 prepared 调用执行 `accept(prepared.get().finish(preliminary))` 后再退出。
- 判断：prepare触barrier发现stopped后直接break，已prepare请求未finish留下悬挂；对已受理preliminary做单次确定结算，主体不进入。
- 证据：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:51

## OCR-1082 · 采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:90；OCR原严重度：medium
- 计划：R1-02 / P1
- 原意见：`run.abort()` 位于内层 try 之外，而 `abort()` 会通过 `EventSupport.append`（SessionWriter + EventBus）写事件。触发条件：进入该 catch 的原因本身就是会话写入/事件总线故障（`drain()` 中 `accept`/`finish` 抛出的正是这类异常），此时 `abort()` 再次抛异常会直接替换原始异常，真正的根因丢失，且不会执行到 `addSuppressed`。建议把 `abort()` 也放进 try 并把其异常加入 suppressed，保证始终抛出原始异常。
- 判断：异常catch内run.abort在try之外，重复审计失败覆盖首因且不drain；收集suppressed并尽可能结算，写失败不能伪造完整事实。
- 证据：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:90

## OCR-1083 · 采纳

- 位置：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:207；OCR原严重度：medium
- 计划：R1-01 / P2
- 原意见：worker 先从 `workers` 中移除自己之前就 `countDown()` 了。`drain()` 只要看到 `done` 为 0 就返回，`execute()` 的 finally 也会立即 `busy.set(false)`；触发条件：同一 scoped scheduler 在上一次 `execute()` 返回后紧接着再次调用 `execute()`，此时第 22 行 `if (!workers.isEmpty())` 可能仍看到刚结束的线程，从而错误地抛出 `SCHEDULER_BUSY`（间歇性失败）。建议先 `workers.remove(...)` 再 `countDown()`，让 latch 归零时“worker 已注销”这一不变量成立。
- 判断：done.countDown早于workers.remove，下一批可误报SCHEDULER_BUSY；注销先于完成信号，测试紧邻批次无人工sleep。
- 证据：harness-tools/src/main/java/io/jharness/tools/BoundedReadOnlyToolScheduler.java:207

## OCR-1084 · 不采纳

- 位置：harness-web/src/main/java/io/jharness/web/WebJson.java:32；OCR原严重度：medium
- 计划：无
- 原意见：同一份字节被完整解析两次：token 预扫描一遍，随后 `MAPPER.readValue` 再反序列化一遍，CPU 与临时对象分配翻倍（HTTP 入口已把 body 限到 64 KiB，readValue 本身也受 maxNestingDepth/maxStringLength 约束，预扫描收益有限）。另外 `nodes` 统计的是 token 数（含 START_OBJECT/END_OBJECT/START_ARRAY/END_ARRAY 与字段名 token），8192 的门槛实际只允许约 4096 个字段/元素的合法对象通过；超过时抛出的 IOException 会被上层统一映射成 400 INVALID_JSON，与“文档非法”不可区分，接口契约上不可预期。建议只统计值节点（或在单次解析中按容器深度累加），并按真实需要设定阈值；若目标是限制总量，使用 StreamReadConstraints（如 maxDocumentLength）替代重复解析。
- 判断：8192统计token是明确额外资源门禁，预扫描保护绑定前节点数；不能以64KB已经限制为由删除节点限或只统计值，性能优化需等效有界parser证据。
- 证据：harness-web/src/main/java/io/jharness/web/WebJson.java:32

## OCR-1085 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/WebJson.java:25；OCR原严重度：medium
- 计划：R5-01 / P1
- 原意见：写侧与读侧约束不对称，且 encode 自身不校验输出长度：(1) decode 限制 maxNestingDepth(24)/maxStringLength(65536)，而 encode 仍使用 Jackson 默认写出上限（嵌套 1000 层），因此可能出现“encode 写得出来、decode 读不回去”的往返失败——例如 WorkspaceLease.save 把 pending 再包一层 {"version":1,"pending":...}，原本 24 层的请求体值在状态文件里会变成 25 层，重启时 decode 直接抛 IOException 导致租约状态不可读；(2) RESPONSE_LIMIT 只由调用方在 encode 之后比较字节长度，大对象会先被完整序列化到堆内存再被判定超限，峰值分配不受 512 KiB 约束（对 diff、事件 payload 等大结构存在 OOM 风险）。建议通过 StreamWriteConstraints 让写侧套用与读侧相同的嵌套/字符串上限，并在 encode 内用带长度上限的 OutputStream（超过 RESPONSE_LIMIT 立即失败）代替 writeValueAsBytes。
- 判断：WebJson写出后才由调用者限bytes，需有界输出流和状态文件读写对称schema；WorkspaceLease pending实际只有有限字段，24层请求嵌套租约例子未证实。
- 证据：harness-web/src/main/java/io/jharness/web/WebJson.java:25

## OCR-1086 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/TurnQueue.java:57；OCR原严重度：high
- 计划：R2-04 / P2
- 原意见：配额只增不减：`inputs` 中没有任何条目被移除（全仓库检索 `inputs.remove` 无结果），而这里用 `inputs.size() >= 256` 作为准入上限。一个任务累计提交 256 次后，即使全部提交都已 FINISHED/CANCELLED/VERIFIED，后续每次提交都会抛 `IllegalArgumentException`（HarnessWebServer.enqueue 的同类检查会返回 429 QUEUE_LIMIT），任务被永久锁死。触发条件：同一任务生命周期内提交超过 256 次。建议改为仅对未终结（非终结态）条目计数，或在状态转为终结态后从 `inputs` 中淘汰并另建 hashes 幂等索引以保持重复请求检出。
- 判断：256是总提交历史上限，长会话确会拒第257项；需区分8待执行与总历史策略/归档分页及幂等保留，不能只计pending导致无界hash记录。
- 证据：harness-web/src/main/java/io/jharness/web/TurnQueue.java:57

## OCR-1087 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/TurnQueue.java:21；OCR原严重度：high
- 计划：R2-03 / P2
- 原意见：校验顺序缺少 null 判断：`kind` 为 null 时 `Set.of(...).contains(null)` 会抛 NullPointerException（不可变集合拒绝 null），`content` 为 null 时 `content.length()` 同样 NPE。这两个异常都不是这里想表达的 `IllegalArgumentException`，并且 apply() 在 catalog 回放路径（HarnessWebServer.parseTask）中被调用，异常会中断整个历史解析/启动，而不是只拒绝单条畸形事实。建议显式做 null 校验并把长度校验放在最前。
- 判断：Input非法kind/content null会NPE，接受catalog回放严格schema与错误隔离；标准HTTP已检，不能默认把内建受理当随机坏记录。
- 证据：harness-web/src/main/java/io/jharness/web/TurnQueue.java:21

## OCR-1088 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/TurnQueue.java:39；OCR原严重度：high
- 计划：R2-03 / P2
- 原意见：`Map.of` 拒绝 null 值，但这里的三个字段都可能被写成 null：`apply` 中 `input.status = (String) p.get("status")`（web.turn.finished 缺 status 键时返回 null），`p.getOrDefault("turnId", "")` 在键存在但值为 null 时返回 null，`selection.getOrDefault("modelRoute", "")` 在 selection 显式带 `"modelRoute": null` 时返回 null（Values 允许 null 值）。任何一种都会让 `view()` 抛 NPE，进而使 `page()` / `inputView()` 直接 500，即一条脏事件可让整个 turns 列表接口不可用。建议在赋值点或渲染点做空值归一（如 `Objects.toString(x, "")`）。
- 判断：finished status/turnId/modelRoute显式null可进入view Map.of NPE；在回放赋值校验而非静默归空掩盖损坏。
- 证据：harness-web/src/main/java/io/jharness/web/TurnQueue.java:39

## OCR-1089 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/TurnQueue.java:73；OCR原严重度：medium
- 计划：R2-04 / P2
- 原意见：`web.turn.started` 把状态改成 UNKNOWN 却保留 `content`，而 UNKNOWN 不在 PENDING 中，因此该文本不会再被任何终结路径清除（只有 finished/cancelled/verified 会清空）。这与类注释“executable text is retained only for pending inputs”矛盾：一旦轮到该轮次执行但结果不可确认（UNKNOWN 需要外部核验），用户输入会无限期驻留内存，并继续通过 `preview` 暴露给无鉴权的 turns 接口。`startInput` 已在启动前把 content 复制到局部变量，此处可以直接清空。
- 判断：started置UNKNOWN仍保留content违注释的pending-only，应清执行文本并保留摘要/哈希关联；turns有业务鉴权，不采纳“无鉴权泄露”主张。
- 证据：harness-web/src/main/java/io/jharness/web/TurnQueue.java:73

## OCR-1090 · 不采纳

- 位置：harness-web/src/main/java/io/jharness/web/TurnQueue.java:102；OCR原严重度：high
- 计划：无
- 原意见：`verified()` 把 UNKNOWN 直接提升为 VERIFIED 并清空 `content`，把“结果未知、需外部核验”与“已核验成功”混为一谈：UNKNOWN 轮次可能实际已经产生过副作用，这里既丢失了唯一可用于对账的原始文本，也让任务再也无法区分该轮次是否被重放/核验（parseTask 之后 task.phase 变成 VERIFIED、error 为 EXTERNALLY_VERIFIED_NO_REPLAY）。建议对 UNKNOWN 保持独立标记（例如保留 content 并记录 status="UNRESOLVED"），只把 RUNNING 的核验结果落为 VERIFIED。
- 判断：verified是操作者明确外部核实并禁止重放，不等于成功，error已有EXTERNALLY_VERIFIED_NO_REPLAY；按建议保持UNRESOLVED会破恢复语义，原正文在session/journal仍保留。
- 证据：harness-web/src/main/java/io/jharness/web/TurnQueue.java:102

## OCR-1091 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/TurnQueue.java:54；OCR原严重度：medium
- 计划：R2-03 / P2
- 原意见：未做类型校验的强制转换：catalog 中的 payload 若 `requestHash` 不是字符串（例如被篡改为数字/对象），这里会抛 ClassCastException，而不是本类其余路径约定的 `IllegalArgumentException`；异常在 catalog 回放期间传播会直接使 worker 启动/历史加载失败。建议用 `instanceof String` 校验后再使用，并对非字符串值给出明确的 IAE。
- 判断：requestHash裸转畸形catalog可CCE，统一严格字段/关联/状态验证并按任务报告损坏。
- 证据：harness-web/src/main/java/io/jharness/web/TurnQueue.java:54

## OCR-1092 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/UiResources.java:19；OCR原严重度：medium
- 计划：R6-01 / P2
- 原意见：清单被静默截断：`readNBytes(16384)` 只读取前 16 KiB，且 `readNBytes` 不会告知是否还有剩余字节，随后 `.lines()` 生成的最后一行若被截断还会被正则过滤掉。由于 `package-ui.mjs` 用 `files.sort()` 排序后写入清单，`assets/*` 排在 `index.html` 之前，一旦构建产物较多导致清单超过 16 KiB，被丢弃的恰恰是排在后部的 `index.html`/`ui-license.txt`：此时 `FILES` 非空，`FILES.isEmpty()` 的 503 提示分支不会命中，访问 `/` 直接得到无说明的 404；而落在截断点之后的 `assets/*.js|css` 也会 404，导致页面加载失败。建议整份读取并对超限显式失败（同时可在 `package-ui.mjs` 侧校验清单大小），而不是静默丢行。
- 判断：ui-files仅readNBytes16384未探测超限，清单截断会缺后部入口；读取LIMIT+1且超限明确拒发布/诊断，构建端同步限额。
- 证据：harness-web/src/main/java/io/jharness/web/UiResources.java:19

## OCR-1093 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/UiResources.java:33；OCR原严重度：low
- 计划：R6-01 / P2
- 原意见：边界条件缺陷：`getPath()` 可能返回空字符串（例如 absolute-form 请求目标 `GET http://host:port HTTP/1.1`，Host 头与监听地址一致即可通过 `handle()` 的 Host 与 `trustedBrowser` 校验，且 `getRawPath()` 既不含 `%` 也不以 `/v1/` 开头）。此时 `path.equals("/")` 为 false，`"".substring(1)` 抛出 `StringIndexOutOfBoundsException`，该非受检异常会被 `handle()` 的 `catch (Exception)` 兜底成 500 `WEB_OPERATION_FAILED`，而不是预期的 404。建议先判空再取子串。
- 判断：UiResources.serve对空path substring可抛500，先规范空请求目标/拒绝非斜杠；实际JDK HttpServer绝对form空path需原始HTTP回归确认。
- 证据：harness-web/src/main/java/io/jharness/web/UiResources.java:33

## OCR-1094 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/WorkbenchContentSearch.java:94；OCR原严重度：high
- 计划：R5-02 / P1
- 原意见：当请求未指定 profile 时，`candidates` 中任意 profile 的行都会被放行，而 `profiles.get(row.get("profile"))` 对已不在 `allowed` 映射中的 profile（例如磁盘目录 `diskCatalog.scan` 恢复出的历史任务、配置调整后被移除的 profile）返回 null，随后 `selected.search()` 直接 NPE，最终被上层兜底成 500 `WEB_OPERATION_FAILED`，而不是预期的 `SEARCH_UNAVAILABLE` 或跳过该行。触发条件：catalog 中存在 profile 名不在当前 profiles 映射里的 session，且请求体 profile 为空。建议在取到 selected 后先判空（以 profiles 作为授权白名单，不在白名单的行直接跳过），再进行可用性判断。
- 判断：历史row profile不在当前profiles时selected=null，需授权过滤/受控拒绝；不能把移除profile旧任务继续视可搜索授权，补重启配置删除场景。
- 证据：harness-web/src/main/java/io/jharness/web/WorkbenchContentSearch.java:94

## OCR-1095 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/WorkbenchContentSearch.java:119；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：`Map.copyOf` 拒绝 null 值，而 `ContentSearchProvider.Hit` 的 `eventId/type/timestamp/turnId/snippet` 都是无约束的 String 字段（接口层没有非空校验）。任一 provider 返回 null 字段时，这里会抛 NPE，使整个搜索失败并返回 500，而不是给出受控错误或跳过该命中。建议在放入快照前对可空字段做归一化（例如 `Objects.requireNonNullElse(hit.turnId(), "")`），或显式校验后抛出受控的 `SEARCH_RESULT_INVALID`。
- 判断：provider Hit缺非空验证可Map.copyOf NPE，统一返回schema/范围；未知turnId可规范空值但eventId等必填必须拒绝。
- 证据：harness-web/src/main/java/io/jharness/web/WorkbenchContentSearch.java:119

## OCR-1096 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/WorkbenchContentSearch.java:126；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：`Instant.parse((String) row.get("timestamp"))` 完全信任 provider 输出：只要某个 provider 给出非 ISO-8601 的 timestamp 或 null，排序阶段就会抛 DateTimeParseException/NPE，整个请求变成 500，而且是扫描完全部 session 之后才失败。建议在插入命中时就把时间戳解析为 `Instant`（并对 null/非法值走受控错误），排序时对已解析值比较，同时避免在每次比较中重复解析（最多 4096 条命中，约 O(n log n) 次解析）。
- 判断：provider timestamp到排序才Instant.parse反复解析且异常500；采集命中时校验/解析一次并给固定provider结果错误。
- 证据：harness-web/src/main/java/io/jharness/web/WorkbenchContentSearch.java:126

## OCR-1097 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/WorkbenchContentSearch.java:98；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：`list()`（如 `JsonlSessionRepository.list()`）会遍历 root 目录下的全部 `*.jsonl` 并排序，单次调用成本随 session 总数线性增长；这里在按行循环（最多 256 行）中对同一 profile 反复调用，最多产生 256 次目录扫描，且整个扫描还持有着实例锁，直接放大请求延迟。建议按 profile 缓存已解析的 session 集合（循环外构建 `Map<String, Set<String>>`）再逐行判定。
- 判断：同profile每row调用sessions.list导致重复最多256次目录扫描；每次查询按profile单次有界membership快照，保留授权核对。
- 证据：harness-web/src/main/java/io/jharness/web/WorkbenchContentSearch.java:98

## OCR-1098 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/WorkbenchContentSearch.java:20；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：整个 `search` 方法都被 `synchronized` 保护，但临界区内包含对最多 256 个 session、每个 4096 命中的外部 provider 扇出调用；5 秒截止 token 只在 session 之间被检查，单个慢 provider 就能长时间占锁，导致该 host 上其他搜索请求（包括翻页请求）全部串行阻塞。建议仅对 `snapshots` 的读写（查找/插入/淘汰/续页校验）加锁，把扫描与排序放到锁外执行，或用 `ConcurrentHashMap` + 独立锁控制并发。
- 判断：整个search锁内外部provider扇出，慢provider阻塞所有请求；缩快照锁并限制总并发，不能仅ConcurrentHashMap后允许无界并行。
- 证据：harness-web/src/main/java/io/jharness/web/WorkbenchContentSearch.java:20

## OCR-1099 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/DurableTaskCatalog.java:240；OCR原严重度：high
- 计划：R2-03 / P2
- 原意见：append 未校验 taskId：非 accepted 事件会把 null 直接传给 byId，而 byId 第一步 `recent.containsKey(id)` 之后立即执行 `id.matches(...)`，必然抛 NullPointerException（而不是协议错误 INVALID_WEB_CATALOG）。accepted 分支更隐蔽：缺少 taskId 时 byHash 正常通过，随后 `recent.put(null, hash)` 以 null 作为缓存 key，之后任何 byId(null) 都会命中这条历史。触发条件：任何未经服务端 generateIds 路径构造（或损坏/被篡改）的 web.task.* 事件。建议在入口处显式校验 taskId 非空且符合命名规则。
- 判断：catalog append缺taskId输入校验，accepted可recent null键；标准生成ID安全但回放/迁移需严格模式，写前拒畸形。
- 证据：harness-web/src/main/java/io/jharness/web/DurableTaskCatalog.java:240

## OCR-1100 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/DurableTaskCatalog.java:19；OCR原严重度：high
- 计划：R2-03 / P1
- 原意见：共享可变状态缺少同步：append 是 synchronized，但 remember/scan/byHash/byId 都会修改 recent（LinkedHashMap 的 remove/put，含 32 条淘汰逻辑）和 count，而这些方法不带任何锁。HarnessWebServer 用 ThreadPoolExecutor 处理请求（多个 handler 线程会调 byId/byHash/scan），同时任务 worker 线程调用 append，因此会出现「一个线程 put、另一个线程 remove/迭代」的并发写：LinkedHashMap 结构可能损坏、条目丢失或错误命中，导致 taskId→hash 解析到错误历史、count 配额判断失真。建议所有对 recent/count 的读写都在同一把锁（或同步容器/Caffeine 有界缓存 + AtomicInteger）下进行。
- 判断：recent/count内部方法未统一锁，需核HarnessWebServer外层调用锁后明确封装线程安全；直接并发使用当前类确可改LinkedHashMap，不以此断言已串错任务数据。
- 证据：harness-web/src/main/java/io/jharness/web/DurableTaskCatalog.java:19

## OCR-1101 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/DurableTaskCatalog.java:66；OCR原严重度：high
- 计划：R2-03 / P2
- 原意见：迁移续跑的判定依赖 sanitizer 的跨进程确定性：已落盘的记录是上次进程用「当时的 sanitizer」（内含每进程随机 browserToken 的 SecretRedactor 链，见 HarnessWebServer 第 134/180 行）处理后的结果，这里又对原始 legacy 事件重新 sanitize 后做 equals 比较。只要 sanitizer 的配置/密钥集合在两次进程间不同（或实现非幂等/非确定），比较即失败，抛 MIGRATION_CONFLICT，而构造器没有任何清理或跳过路径 —— 该会话此后每次启动都失败，任务历史永久不可用。建议续跑只比较与密钥无关的身份字段（eventId/type/顺序），或为每条记录写入迁移进度标记，或直接比对未脱敏的原始事件。
- 判断：迁移续跑重sanitize后equals依赖秘密配置稳定，非幂等/新秘密可MIGRATION_CONFLICT；用身份/原始安全指纹和迁移进度，禁止按建议比较未脱敏正文输出或忽略内容冲突。
- 证据：harness-web/src/main/java/io/jharness/web/DurableTaskCatalog.java:66

## OCR-1102 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/DurableTaskCatalog.java:61；OCR原严重度：medium
- 计划：R2-03 / P1
- 原意见：迁移只取首个事件的 requestHash 且未做唯一性/一致性校验：(1) 同一 taskId 组内未被要求所有事件 requestHash 相同；(2) 未校验不同 taskId 组之间 requestHash 不重复（旧版本 taskId 是随机 UUID，requestId 复用可能产生两个 taskId 共用一个 hash）。若两组命中同一 recordId，第二个组会追加进第一个组的记录，形成包含两个 web.task.accepted 的历史；remember 只校验首事件，无法发现，随后 byHash(hash) 会把 A 任务的 finished/verified 事件当作 B 的请求历史返回（跨任务数据串读）。建议迁移前做全局 hash 唯一性校验，并校验组内 requestHash/taskId 一致，冲突时抛 MIGRATION_CONFLICT。
- 判断：迁移只首hash/首accepted验证不足，应全组ID/hash一致及全局唯一；当前遇existing不一致会MIGRATION_CONFLICT，原说必然拼成串任务需回归证明。
- 证据：harness-web/src/main/java/io/jharness/web/DurableTaskCatalog.java:61

## OCR-1103 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/DurableTaskCatalog.java:255；OCR原严重度：medium
- 计划：R2-03 / P2
- 原意见：持久化前没有事件类型/状态白名单：迁移分支只接受 web.task.accepted / web.task.finished / web.task.verified（第 59-62 行），但运行期 append 只要事件不是 accepted 且能按 taskId 找到历史，就会把任意类型（未知/future 类型）持久化，随后被 scan/byId/UI 重放；也未校验 finished 事件的状态字段，UNKNOWN 状态会被落盘并在重启后被当作事实重放（与「UNKNOWN 状态不得被重放」的要求冲突）。建议在写入前复用同一白名单，并显式拒绝 UNKNOWN/未知状态，否则抛 INVALID_WEB_CATALOG。
- 判断：运行append类型/状态需白名单验证，但M10.5新metadata/turn事件合法，不能复用旧迁移三类型白名单；UNKNOWN是必须持久保留的事实，不能拒写掩盖它。
- 证据：harness-web/src/main/java/io/jharness/web/DurableTaskCatalog.java:255

## OCR-1104 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/DurableTaskCatalog.java:218；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：byId 的兜底路径是「扫描并完整解析目录下最多 10000 个 JSONL 记录」，而 append 对每个非 accepted 事件都会调用 byId（缓存 32 条之外的 taskId 全部落到这条路径），因此每次追加一个 finished/verified 事件都会触发一次 O(任务数 × 单任务事件数) 的全量解析，写路径被严重放大；请求线程调 byId 查不存在/非规范 id 时同样全量扫描。建议维护持久化的 taskId→hash 索引，或对 taskId 先做格式校验/派生 hash 再定位文件，避免全量扫描。
- 判断：新taskId含hash已有直接byHash路径，不会每非accepted全扫；仅legacy UUID/无效ID兜底扫描，接受legacy索引/格式快速拒绝的有界优化。
- 证据：harness-web/src/main/java/io/jharness/web/DurableTaskCatalog.java:218

## OCR-1105 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/DurableTaskCatalog.java:179；OCR原严重度：medium
- 计划：R2-03 / P2
- 原意见：配额口径不一致形成永久性拒绝服务：append 用 count（只统计非空历史）拦截创建，scan 却用「目录内文件数」拦截读取。openWriter 会先写出仅含 header 的记录文件，若在 append/flush 之前崩溃或磁盘写失败，就会留下不计数却占用 seen 的空记录；这类残留累积到 10001 个后，scan 每次都抛 WEB_HISTORY_QUOTA，构造器启动失败、所有按 id/hash 的查询也随之不可用。建议统一配额口径（按非空历史计数），并在 scan 中跳过/清理空或损坏的记录。
- 判断：空record计文件数与count非空不一致可能耗配额，需统一保留/修复策略并可诊断；不能跳过损坏记录当不存在或无授权删除运行数据。
- 证据：harness-web/src/main/java/io/jharness/web/DurableTaskCatalog.java:179

## OCR-1106 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/WorkspaceLease.java:24；OCR原严重度：high
- 计划：R2-03 / P1
- 原意见：文件存在性/大小校验与真正的读取是分离的（TOCTOU）：`Files.size` 和 `Files.readAllBytes` 都会跟随符号链接，且 `readAllBytes` 不受 64 KiB 限制。攻击者（或并发进程）可在 `check()` 返回后把 `web-workspace-owner.json` 换成指向工作区外的大文件，从而越过工作区边界并让读取量失去上限（内存压力/OOM）。建议用 `FileChannel.open(file, READ, NOFOLLOW_LINKS)` 打开后基于该句柄的 `size()` 判定，并只读取 `min(size, 65536+1)` 字节，读取前用 `Files.readAttributes(..., NOFOLLOW_LINKS)` 校验 `isRegularFile` 且 `fileKey` 在读取前后一致。
- 判断：lease前size后readAllBytes可被并发放大，且read跟随终组件；改NOFOLLOW句柄有界读取并保留父目录保护，文件key复查不能替代句柄能力。
- 证据：harness-web/src/main/java/io/jharness/web/WorkspaceLease.java:24

## OCR-1107 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/WorkspaceLease.java:44；OCR原严重度：high
- 计划：R2-03 / P1
- 原意见：`accept()` 把调用方提供的 map 原样持久化，未校验任何必需字段：`clear()` 要求 `pending.get("taskId")` 与传入 task 相等，如果接受的准入里没有 `taskId`（或非 String/null），`clear()` 永远返回 ENVIRONMENT_MISMATCH，而 `pending` 非空又让后续 `accept()` 一律抛 WORKSPACE_BUSY —— 工作区会被永久卡死，只能人工改磁盘文件。另外 `next.put("catalogKey", catalogId)` 会静默覆盖调用方已带的不同 catalogKey（可能掩盖跨目录准入），应改为冲突即拒绝。建议在 `accept` 中强制校验 `taskId` 为非空字符串、`catalogId` 非空，并对已存在的 catalogKey 做不相等则拒绝的检查。
- 判断：accept未验证taskId/catalog身份即可持久化，需强schema和重读校验；当前调用生成字段不漏taskId，不能称正常准入必永久锁死，允许受控诊断但不手改运行文件。
- 证据：harness-web/src/main/java/io/jharness/web/WorkspaceLease.java:44

## OCR-1108 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/WorkspaceLease.java:77；OCR原严重度：medium
- 计划：R6-04 / P1
- 原意见：原子 rename 后没有对目录本身做 fsync（`FileChannel.open(directory, READ)` + `force(true)`），因此新目录项可能只停留在页缓存。该文件被注释声明为“预写指针”，若进程/机器在 rename 返回后崩溃，磁盘上可能仍是旧的（例如空的）pending，而相应的会话/任务副作用已经发生，恢复时工作区会被判为空闲并可被再次准入，破坏“先写指针后产生副作用”的顺序保证。建议 move 成功后对 `directory` 执行目录 fsync。
- 判断：指针rename仅force文件缺目录项持久性保证，需文档明确断电范围及支持平台fsync策略；进程崩溃通常不同于断电，不混为必然失指针。
- 证据：harness-web/src/main/java/io/jharness/web/WorkspaceLease.java:77

## OCR-1109 · 不采纳

- 位置：harness-web/src/main/java/io/jharness/web/WorkspaceLease.java:88；OCR原严重度：medium
- 计划：无
- 原意见：`close()` 只释放 owner，没有把对象标记为已关闭（`pending` 仍可读写）。关闭后再调用 `accept()`/`clear()` 时，`save()` 里的 `owner.flush()` 作用在已关闭的 writer 上（可能抛异常或静默失败），随后仍然会写 `web-workspace-owner.json` —— 此时排他锁已被释放，写入没有任何互斥保护，会与其他进程/实例的准入产生竞态并覆盖对方的指针。建议增加 `closed` 标志，在 `accept`/`clear`/`save` 入口拒绝已关闭的 lease。
- 判断：close已完成后再次save第一步owner.flush，固定Jsonl writer.valid抛WRITER_CLOSED，不会继续写文件。此结论仅针对原意见的顺序调用；flush后到move之间并发close不由此证明安全，归入生命周期锁审计。
- 证据：harness-web/src/main/java/io/jharness/web/WorkspaceLease.java:88

## OCR-1110 · 不采纳

- 位置：harness-web/src/main/java/io/jharness/web/WorkspaceLease.java:51；OCR原严重度：low
- 计划：无
- 原意见：`task`/`catalogId` 为 null 时这里会 NPE（例如调用方传入未校验的参数），而 NPE 会被上层当作 WEB_VERIFICATION_FAILED/SUBMISSION_NOT_CONFIRMED 之类的通用错误吞掉，掩盖真实原因；同时间接类型不匹配只能得到 ENVIRONMENT_MISMATCH，无法区分“参数缺失”与“环境不一致”。建议参数判空并使用 `Objects.equals` 比较。
- 判断：WorkspaceLease包内clear由宿主确定task/catalog字符串调用，null属于违约，Objects.equals反而可能两个null匹配损坏pending；统一accept schema以1107解决。
- 证据：harness-web/src/main/java/io/jharness/web/WorkspaceLease.java:51

## OCR-1111 · 不采纳

- 位置：harness-web/src/main/java/io/jharness/web/WorkspaceLease.java:37；OCR原严重度：low
- 计划：无
- 原意见：`pending()` 直接返回内部 map 引用。当前之所以未出问题，是因为构造与 `save()` 恰好都经 `Values.snapshot()/object()` 包装成不可变 map；这是隐式约束而非接口保证，一旦未来新增可变赋值路径（或调用方尝试就地修改，实际会抛 UnsupportedOperationException 而不是被显式拒绝），就会出现内存与磁盘状态不同步、绕过 WORKSPACE_BUSY/ENVIRONMENT_MISMATCH 校验的问题。建议返回 `Values.snapshot(pending)` 或保持字段类型为不可变的防御性拷贝。
- 判断：所有pending赋值已经Values.snapshot/object或Map.of不可变，返回引用不会被改；假想未来可变赋值不构成当前缺陷，重复快照徒增开销。
- 证据：harness-web/src/main/java/io/jharness/web/WorkspaceLease.java:37

## OCR-1112 · 部分采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/UnifiedDiff.java:10；OCR原严重度：high
- 计划：R3-05 / P2
- 原意见：CRLF 归一化与原文比较不一致（第 10、29-31 行）。`before.equals(after)` 比较的是原始文本，而 `lines()` 会把 `\r\n` 替换为 `\n`，因此仅行尾风格不同的内容（例如 `before="a\r\n"`、`after="a\n"`）会被判定为不相等，进而输出一个“删除 a / 新增 a”的伪全量补丁，下游会误报文件变更。更严重的是，LocalWorkspace.lineStyle 会保留 CRLF 文件内容，生成的 `-` 行已被剥掉 `\r`，把该补丁应用到真实 CRLF 文件时删除行无法匹配，patch/git apply 会直接失败。另外仅含孤立 `\r`（旧 Mac 换行）的文本不会被识别为行分隔符，`\r` 会被嵌入 hunk 行内容中。建议统一按同一规则比较（或先做行结束符规范化后再判等），并保留原始行尾，或明确只支持 LF 并在此处显式拒绝/转换。
- 判断：CRLF-only变化会全替换同文字，作为字节行尾变化不是假文件改动；应明确展示型diff的行尾语义/可应用范围并测试，不能归一化判相等丢真实变更。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/UnifiedDiff.java:10

## OCR-1113 · 部分采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/UnifiedDiff.java:12；OCR原严重度：high
- 计划：R3-05 / P2
- 原意见：path 未经任何转义/校验就拼进 `---`/`+++` 头部。工作区路径来自用户/模型可控的文件名，在 POSIX 下可以包含换行、`\t`、控制字符；一旦 path 含 `\n`，就能关闭当前头部并注入任意 diff 行（例如伪造另一个文件的 hunk），使预览/下游应用产生非预期的文件改动，同时也会破坏标准 diff 解析（含空格/tab 的路径同样会被截断）。建议对 path 做校验（拒绝换行/控制字符）或按 git 规则加引号转义（如 `"a/\t..."`）。
- 判断：公开UnifiedDiff无path转义，内建LocalWorkspace拒控制字符但空格/tab等头语义需明确；安全引用路径/格式边界，不宣称模型已能注入跨文件写入。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/UnifiedDiff.java:12

## OCR-1114 · 不采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/UnifiedDiff.java:14；OCR原严重度：medium
- 计划：无
- 原意见：新建/删除文件时头部仍使用 `a/path`、`b/path`，只把起始行号写成 0。按统一 diff 协议，源/目标为空时应使用 `/dev/null`（如 `--- /dev/null` + `+++ b/path`），否则 `git apply` 等工具不会识别为“创建/删除文件”，而在目标不存在（新建）或期望被移除（删除）时因找不到 `a/path` 而报错，或把删除操作变成“清空文件”。建议当 `before.isEmpty()` 时左侧写 `/dev/null`，`after.isEmpty()` 时右侧写 `/dev/null`。
- 判断：before空串不等于文件不存在，after空串不等于删除；仅凭空文本改/dev/null会把清空已有文件误作删除，文件工具明确不支持删除。需未来明确change kind另立项。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/UnifiedDiff.java:14

## OCR-1115 · 不采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/UnifiedDiff.java:9；OCR原严重度：low
- 计划：无
- 原意见：公开入口未做 null 校验：`before.equals(after)` 在 before 为 null 时抛 NPE，`lines()` 内的 `text.isEmpty()/replace/split` 在 after 为 null 时抛 NPE，且异常信息无法定位到是哪个参数、哪个文件。建议在入口用 `Objects.requireNonNull(before, "before")` / `Objects.requireNonNull(after, "after")`（或显式抛出带 path 上下文的领域异常）快速失败。
- 判断：非法null已快速NPE拒绝，增加参数名属API消息偏好，无当前调用错误。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/UnifiedDiff.java:9

## OCR-1116 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/TaskDiagnosis.java:69；OCR原严重度：high
- 计划：R4-03 / P1
- 原意见：TaskDiagnosis prioritizes cleanupStatus=not_started and bodyEntered=false over sideEffectsMayHaveOccurred or sideEffectUncertain. This can report effects=not_started or commandStarted=no even when evidence says side effects may have occurred. Check side-effect uncertainty first and use conservative values.
- 判断：effects先not_started再uncertain，冲突证据可被诊断断言未执行；未知优先并保留矛盾标记，诊断不授予恢复执行权限。
- 证据：harness-web/src/main/java/io/jharness/web/TaskDiagnosis.java:69

## OCR-1117 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/TaskDiagnosis.java:36；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：`requested` 与 `decisions` 为每个 `tool.requested` / `approval.decided` 事件保存完整的已脱敏 payload，但后续只用到 `arguments.mode/timeoutMs` 和 `reason`，且 `sanitizer.sanitize(e.payload())` 在失败过滤之前已对全部事件执行。会话记录上限为 10000 条、单条可达 8MB，长会话下会造成明显的内存放大与重复脱敏开销，不符合该类的 bounded 契约。建议只保留需要的字段，或仅对失败事件的 callId 再做一次有界查找。
- 判断：全事件sanitize并保留整requested/decisions payload仅用少字段有重复开销；字段投影/受限索引，日志总32MB不能按每事件8MB乘10000夸大实际输入。
- 证据：harness-web/src/main/java/io/jharness/web/TaskDiagnosis.java:36

## OCR-1118 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/TaskDiagnosis.java:47；OCR原严重度：medium
- 计划：R1-04 / P1
- 原意见：`eventId` 直接写入输出且未经 `safeString`/`string` 处理，而其余字符串字段都经过脱敏并截断到 256 字符。`eventId` 来自磁盘 JSONL（解析时仅校验非空，长度可达记录上限），可能携带敏感内容并放大诊断响应体；配置快照中的 `c.put("eventId", e.eventId())` 同样存在该问题。建议统一走 `safeString(e.eventId(), sanitizer)`。
- 判断：diagnosis eventId裸写未像其它字段safeString，有自由ID的秘密/长串面；引用ID安全显示与原始定位分离，不能截断后作实际事件主键。
- 证据：harness-web/src/main/java/io/jharness/web/TaskDiagnosis.java:47

## OCR-1119 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/TaskDiagnosis.java:139；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：`category()` 在 status/code 为空或无法识别时一律返回 `FAILED`。对于缺失 `status` 的畸形/旧版记录、未来新增状态名，或未携带 `UNKNOWN_SIDE_EFFECT` 的副作用不确定记录，这会断言一个确定的失败分类，掩盖本应保留的 UNKNOWN 语义，与“UNKNOWN 不得被当作确定事实/重放”的契约相悖。建议在证据不足时返回 `UNKNOWN`。
- 判断：category默认FAILED在信息缺失时过度确定，可增UNKNOWN/UNCLASSIFIED且按uncertain优先；该分类不是恢复状态，不据此断言工具会被重放。
- 证据：harness-web/src/main/java/io/jharness/web/TaskDiagnosis.java:139

## OCR-1120 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/TaskDiagnosis.java:92；OCR原严重度：low
- 计划：R4-03 / P2
- 原意见：这里的 `mode` 取自 payload 的 value（已脱敏但未截断），绕过 `string()` 的 256 字符上限；并且即使该值为空字符串也会覆盖前面从 `arguments` 复制的 `mode`，导致记录信息丢失。建议使用 `string(mode)` 并在非空时才覆盖。
- 判断：value.mode覆盖未截断且空值可抹原args.mode，统一安全截断/非空优先规则并保留来源。
- 证据：harness-web/src/main/java/io/jharness/web/TaskDiagnosis.java:92

## OCR-1121 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/TaskDiagnosis.java:115；OCR原严重度：low
- 计划：R4-03 / P3
- 原意见：`turnIds` 先通过 `new ArrayList<>(turns)` 复制全部 turnId，再由 `limited` 截断到 64，复制阶段没有上界；长会话（最多 10000 条事件）下会产生不必要的内存峰值。建议在流上先 limit 再收集，保持有界处理。
- 判断：turns先全ArrayList后limited64，可先limit减少中间分配；输入已10000事件限不称无界OOM，归并诊断有界投影。
- 证据：harness-web/src/main/java/io/jharness/web/TaskDiagnosis.java:115

## OCR-1122 · 部分采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/WorkspaceSearchFilter.java:19；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：NPE 风险：`Set.of(...)` 返回不可变集合，其 `contains(null)` 在 JDK9+ 会抛 NPE，而 `containsAll` 会逐元素调用 `contains`。因此传入一个允许 null key 的原始 Map（如 `LinkedHashMap`/`HashMap`，可能来自 CLI/SDK 手工构造的配置）时，`from` 会抛未捕获的 NullPointerException，而不是返回 `INVALID_SEARCH_FILTER`，破坏错误协议。建议改为对每个 key 先做类型判断，或使用 `new HashSet<>(Set.of(...))` 这类允许 null 探测的集合。
- 判断：直接Java raw Map null key可containsAll NPE；JSON入口字段名非null，统一public filter配置错误，不按远程JSON越界漏洞。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/WorkspaceSearchFilter.java:19

## OCR-1123 · 部分采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/WorkspaceSearchFilter.java:58；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：校验过严且抛错方式会导致仓库级不可用：`checkedRules` 同时被用于校验根 `.gitignore` 内容（LocalWorkspace#ignoreRules），而这里把 `[`/`]`、`\`、`:` 等字符整体判为非法。真实仓库的 `.gitignore` 常见 `[Bb]in/`、`*.[oa]`、`\#literal` 等写法，一旦出现就会抛 `INVALID_SEARCH_FILTER`，使该工作区的 list/search/grep 全部失败，属于可复现的兼容性缺陷。同理，`rows.size()>128`、总长 16384、单条 256 的上限以及先做长度校验再跳过 `#` 注释行的顺序，会让超过 128 条规则或一行超长注释的 `.gitignore` 直接导致检索不可用。建议对 `.gitignore` 来源采用宽松策略（跳过不支持的条目/注释行而非抛异常），或为宿主规则与仓库规则使用不同的校验入口。
- 判断：gitignore子集/128条限是M8明确范围，unsupported拒绝是fail-closed不是意外全功能不兼容；可在配置预检给出具体不支持行与指导，不能静默跳过导致本应排除文件被检索。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/WorkspaceSearchFilter.java:58

## OCR-1124 · 采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/WorkspaceToolsPlugin.java:401；OCR原严重度：high
- 计划：R2-01 / P1
- 原意见：`compact()` 在 write_file/edit_file/apply_patch 已提交落盘之后才执行，内部会调用 `artifacts.put`（第 417/427 行）。该调用可能因磁盘 IO 失败、`ARTIFACT_LIMIT`（LocalArtifactStore 上限 4 MiB，多文件补丁的 `changes[].diff` 很容易超限）或其他 RuntimeException 抛出，异常从 execute 抛出后，DefaultToolPipeline 会把 WORKSPACE_WRITE 工具的异常统一转成 UNKNOWN（需要人工核验），调用方看到的却是“写操作结果不确定”，而实际上修改已经成功提交。建议把产物持久化降级为尽力而为：捕获失败后返回已提交结果 + 截断标记/错误码，保持 SUCCESS。
- 判断：写入成功后compact制品失败丢原FileChange并变UNKNOWN；保留已提交变更/版本、独立制品失败及截断提示，不把持久事实已缺失一律标SUCCESS。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/WorkspaceToolsPlugin.java:401

## OCR-1125 · 采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/WorkspaceToolsPlugin.java:353；OCR原严重度：medium
- 计划：R5-01 / P1
- 原意见：partial 补丁分支在第 353 行提前 return，绕过了第 401 行的 `compact(...)`：`changes[].diff` 每个最多 8192 字符（LocalWorkspace.applyPatch 第 138 行只截到 8192），多文件时 `result`（作为 value）和 `result.toString()`（作为 summary）都可能远超 8192 的输出上限，也不会产生 `artifactRef` 供 read_artifact 回读，与其它路径的有界输出约定不一致。建议先对 partial 结果执行 compact 再返回 UNKNOWN（`partial`/`error`/`pendingPaths` 字段会被保留）。
- 判断：partial patch提前return绕compact，最多64份diff和toString可放大模型结果；在保留UNKNOWN/changes/pending事实下做有界摘要/制品降级。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/WorkspaceToolsPlugin.java:353

## OCR-1126 · 部分采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/WorkspaceToolsPlugin.java:334；OCR原严重度：medium
- 计划：R3-01 / P2
- 原意见：这里在判断 `partial` 之前就把 `result.get("changes")` 强转为 `List<?>`，且 `Map.of` 不接受 null 值。`WorkspaceFiles` 是公共 SPI（applyPatch 有默认实现），自定义实现若返回缺少 `changes` 字段的 Map、或 version 字段为 null，会在补丁已经写入部分文件之后抛出 NPE/ClassCastException；异常被 pipeline 转成 UNKNOWN 后，`pendingPaths`/`sideEffectUncertain` 等用于恢复的字段全部丢失。建议先做 `instanceof List<?>` 校验并对 null 值容错，结构不符时跳过 display 而不是让异常逃逸。
- 判断：公开WorkspaceFiles畸形changes导致已写后展示NPE丢原partial，返回schema先验/有界解析保留操作未知事实；不能缺数据仍声称成功。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/WorkspaceToolsPlugin.java:334

## OCR-1127 · 采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/WorkspaceToolsPlugin.java:263；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：默认 grep 路径会丢弃 `files.search` 返回的 `truncated` 标记：LocalWorkspace.search 最多返回 limit（此处硬编码 1000）个文件并把是否截断放在 `truncated` 中，这里忽略该标记，导致大仓库下 grep 实际只覆盖前 1000 个文件，却向模型呈现为完整结果（静默漏报匹配）。另外 `((List<?>) ...get("paths"))` 未做 null 校验，若实现返回缺少 paths 字段的 Map 会抛 NPE，且不在下方受控错误码集合内。建议读取 truncated 并在结果中回传 `searchTruncated`/提示，或直接以有界错误失败；同时对 paths 做 `instanceof List<?>` 校验。
- 判断：默认grep search前1000路径丢truncated，模型误以全覆盖；回传搜索范围截断及实际检查数，paths畸形明确协议错误。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/WorkspaceToolsPlugin.java:263

## OCR-1128 · 部分采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/WorkspaceToolsPlugin.java:44；OCR原严重度：low
- 计划：R3-01 / P3
- 原意见：`requireWorkspace(environment)` 在空值检查之前就解引用 `environment.files()`，环境为 null 时抛出的是无信息 NPE，且第 41 行 `Objects.requireNonNull(environment)` 对 null 情况永远不可达（死分支）。此外 `environment.processes()`/`terminal()` 在构造阶段被无条件调用，文件型环境即使不需要进程能力也会被迫提供。建议在 `requireWorkspace` 内先 `Objects.requireNonNull`，并按需惰性获取进程/终端能力。
- 判断：requireWorkspace在nonnull之前解引用确有多余后置校验，核心是文件能力装配不应强制拿无关terminal；null错误消息不独立立项。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/WorkspaceToolsPlugin.java:44

## OCR-1129 · 部分采纳

- 位置：harness-workspace/src/test/java/io/jharness/workspace/UnifiedPatchTest.java:44；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：路径逃逸用例的断言不完整（第 37-49 行）。`b/../outside.txt` 经 `UnifiedPatch.header()` 去掉 `b/` 前缀后变成 `../outside.txt`，会被 `LocalWorkspace.resolve()` 的显式 `..` 规则直接以 PATH_DENIED 拒绝——而该路径 `Path.normalize()` 后其实是 root 内的 `outside.txt`，因此本用例只证明了“含 .. 的路径被拒”，并没有验证真正的越界目标未被写入：测试只检查了 `a.txt` 未被改动，却没有断言 `root.resolve("outside.txt")`/`root.resolve("b.txt")` 以及 `root.getParent().resolve("outside.txt")` 不存在。触发条件：若将来 `resolve()` 在拒绝前发生了任何写/建目录（或越界路径被写到 root 外的父目录），本用例仍会通过。建议补充对拒绝目标文件不存在性的断言，并另加一条规范化后真正落在 root 之外的路径（如 `../../outside.txt`，必要时用符号链接）来验证边界与“preflight 未产生任何写”。
- 判断：补丁拒绝应验证所有目标/外部marker未变；去a/b前缀后的../outside实际可逃root，意见称normalize后仍root内计算错误，不采纳该论证。
- 证据：harness-workspace/src/test/java/io/jharness/workspace/UnifiedPatchTest.java:44

## OCR-1130 · 部分采纳

- 位置：harness-workspace/src/test/java/io/jharness/workspace/UnifiedPatchTest.java:74；OCR原严重度：medium
- 计划：R0-03 / P1
- 原意见：部分提交用例的断言与 UNKNOWN 语义不一致，且未覆盖真正的不确定分支（第 67-78 行）。注入的 hook 在 `write()` 的 `Files.move` 之前抛出 IOException（见 LocalWorkspace 第 443-448 行），此时失败点是确定的：`b.txt` 必然不存在、临时文件也会被 finally 清理。因此这里同时断言 `sideEffectUncertain == true` 与 `!Files.exists(b.txt)`，把一个“确定未提交”的场景固化成“不确定”协议，反而没有验证真正需要恢复的路径——move 成功后再抛出（`UNKNOWN_SIDE_EFFECT`）时 `pendingPaths` 仍会包含已落盘的 `b.txt`，存在重放风险。另外第 72-76 行未断言 `result.get("error")`（应为 PATCH_IO），也未断言 `root` 下无 `.jharness-write-*.tmp` 残留（资源生命周期）。触发条件：一旦 hook 位置调整或实现改为提交后校验失败，这些断言会因为错误的理由失败/通过。建议拆分用例：一个断言 pre-commit 失败时 error 码与临时文件清理，另一个专门覆盖提交后失败的不确定结果（`sideEffectUncertain` 与 pendingPaths 的重放语义）。
- 判断：多文件已有首个成功即整体partial/uncertain合理，失败文件未提交不消除整体副作用；新增提交后异常与临时清理回归，不把现UNKNOWN降确定未执行。
- 证据：harness-workspace/src/test/java/io/jharness/workspace/UnifiedPatchTest.java:74

## OCR-1131 · 采纳

- 位置：harness-workspace/src/test/java/io/jharness/workspace/UnifiedPatchTest.java:86；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：三个负向用例只断言异常类型 `HarnessException.class`，没有断言错误码（第 86-106 行），因此解析/分类错误也能通过：例如删除用例的 `+++ /dev/null` 补丁体仍是 `-two/+new`（oldCount=2 但只删了 1 行），即使删除能力被错误地“支持”或被误判为 INVALID_PATCH，测试依旧绿；上下文不匹配被误报成 PATH_DENIED/INVALID_PATCH 也不会被发现。建议分别断言 `.code()` 等于 UNSUPPORTED_PATCH（`/dev/null` 目标）、PATCH_CONFLICT（上下文不匹配）、UNSUPPORTED_PATCH（`\ No newline at end of file`），并把删除用例改成真正的全删补丁（`@@ -1,2 +0,0 @@` 且仅含 `-` 行），才能证明删除是被明确拒绝而不是因补丁格式错误被拒。
- 判断：负例只assertThrows且删除体仍含替换，需合法全删补丁触UNSUPPORTED_PATCH、冲突码及无新变化断言。
- 证据：harness-workspace/src/test/java/io/jharness/workspace/UnifiedPatchTest.java:86

## OCR-1132 · 采纳

- 位置：harness-workspace/src/test/java/io/jharness/workspace/UnifiedPatchTest.java:55；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：FILE_CONFLICT 用例只依赖 `readText` 在 `observed` 中留下的隐式版本（第 36、55 行始终传 `Map.of()`），显式 `expectedVersions` 契约完全没有被测到：即使实现完全忽略 `expectedVersions` 参数、只用内部 observed 缓存，本用例仍会通过。触发条件：调用方（如 WorkspaceToolsPlugin/EnvironmentWorker）按显式版本做并发保护的路径不会被该测试覆盖。建议先记录 `versionOf(new ExecPath("a.txt"))` 的旧版本，再以 `Map.of("a.txt", oldVersion)` 显式传入陈旧版本并断言 FILE_CONFLICT，同时补一个显式版本与磁盘一致时成功的用例。
- 判断：版本冲突只用隐式observed，补显式expectedVersions旧/新版本对照，证明公开参数真正生效。
- 证据：harness-workspace/src/test/java/io/jharness/workspace/UnifiedPatchTest.java:55

## OCR-1133 · 部分采纳

- 位置：harness-workspace/src/test/java/io/jharness/workspace/LocalWorkspaceTest.java:67；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：Test cleanup deletes a Windows junction or symlink directly in finally. If delete fails because the handle is busy or the entry is gone, the IOException masks the real assertion failure. Use deleteIfExists and suppress or record cleanup exceptions.
- 判断：finally直接Files.delete可能盖主断言，使用安全有界清理及suppressed；不要吞残留当测试通过。
- 证据：harness-workspace/src/test/java/io/jharness/workspace/LocalWorkspaceTest.java:67

## OCR-1134 · 不采纳

- 位置：harness-workspace/src/test/java/io/jharness/workspace/LocalWorkspaceTest.java:428；OCR原严重度：medium
- 计划：无
- 原意见：第 414-429 行用 System.nanoTime 对单次 list 计时并硬性要求 ≤2000ms：第 0 次迭代包含 JIT 编译、类加载与文件系统元数据缓存冷启动，构建机 IO 抖动也可能让同一逻辑偶发超时（触发条件：首次运行或 CI 负载高），失败与实现正确性无关。建议先做一次不计时的预热迭代，再取 3 次的最小值/中位数并放宽阈值，或将该性能断言打上 @Tag 移出默认单测，仅保留计数断言。
- 判断：2秒目录列表是明确NFR验收，不能移出默认测试/只取最小值掩盖慢路径；若需基准分离另说明，当前无证据可放宽门禁。
- 证据：harness-workspace/src/test/java/io/jharness/workspace/LocalWorkspaceTest.java:428

## OCR-1135 · 部分采纳

- 位置：harness-workspace/src/test/java/io/jharness/workspace/LocalWorkspaceTest.java:0；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 439-444 行的 GIT_DIFF_UNAVAILABLE 断言隐含两个环境前提：1) 机器上必须安装 git，否则 `ProcessBuilder.start()` 抛出的普通 IOException（而非 HarnessException）会直接让测试失败；2) @TempDir 不能位于任何 Git 工作树内，若 CI 把 java.io.tmpdir 指到仓库内，`git -C root diff` 会成功返回空串且 exit=0，同样不会抛 GIT_DIFF_UNAVAILABLE。触发条件：无 git 的构建机或 TMPDIR 位于仓库内。建议先用 Assumptions 或 `git rev-parse --is-inside-work-tree` 确认“git 可用且非仓库”，再断言错误码。
- 判断：git可用且夹具非仓库是实际前置条件，应受控隔离并明确失败诊断；不加Assumptions跳过严格平台门禁，不依赖任意tmpdir不在repo。
- 证据：harness-workspace/src/test/java/io/jharness/workspace/LocalWorkspaceTest.java:0

## OCR-1136 · 采纳

- 位置：harness-workspace/src/test/java/io/jharness/workspace/LocalWorkspaceTest.java:318；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 299-324 行：与 aRaceBeforeCommitIsDetectedAndTemporaryFileIsRemoved 不同，CANCELLED 与注入 IOException 两条失败路径只校验了原文件内容，没有校验临时文件是否被清理。触发条件：commit 前的 cleanup 路径回归（finally 未执行或 deleteIfExists 失败）时，`.jharness-write-*.tmp` 会残留并且测试仍然通过，资源生命周期泄漏无法被发现。建议在这两处写失败后 `Files.list(root)` 断言目录中只剩 `a`。
- 判断：CANCELLED/IO失败只查原文未查临时残留，补目录快照和安全版本不变，验证资源清理。
- 证据：harness-workspace/src/test/java/io/jharness/workspace/LocalWorkspaceTest.java:318

## OCR-1137 · 采纳

- 位置：harness-workspace/src/test/java/io/jharness/workspace/LocalWorkspaceTest.java:351；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：第 345-357 行只覆盖了 limit+1：实现用 `bytes.length > WRITE_LIMIT` 判定，恰好 1 MiB 的写入应成功但未被验证，因此判界从 `>` 误改为 `>=` 的 off-by-one 不会被发现；同时纯 ASCII 'x' 的字符数等于字节数，无法暴露按字符而非按 UTF-8 字节计量的缺陷。建议补充“恰好 1 MiB 成功 / 1 MiB+1 失败（并用多字节字符构造，如 512K 个中文）”的用例；另外 1 MiB 以上已有文件的 readText->FILE_LIMIT 路径也未被覆盖。
- 判断：只limit+1 ASCII缺恰好1MiB和多字节计量，补UTF8边界及超大已有文件读取拒绝。
- 证据：harness-workspace/src/test/java/io/jharness/workspace/LocalWorkspaceTest.java:351

## OCR-1138 · 部分采纳

- 位置：harness-workspace/src/test/java/io/jharness/workspace/LocalWorkspaceTest.java:146；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：第 147-159 行（以及第 36-48 行的重复代码）直接用 ProcessBuilder 启动 cmd，但异常路径没有 destroyForcibly 和关闭 stdout/stderr。触发条件：mklink 卡住导致 `waitFor(5, TimeUnit.SECONDS)` 返回 false（断言失败）时，cmd 子进程继续运行并占用目录句柄，随后的 junction 重建或 @TempDir 递归清理会失败；成功路径下 `getInputStream()` 也未关闭。建议 try/finally 中 destroyForcibly 并关闭流，同时把两段完全重复的 junction 创建逻辑抽成公共 helper。
- 判断：mklink子进程等待超时后缺销毁/流关闭，抽有界helper保留退出码/实际链接身份断言，不能绕过权限测试。
- 证据：harness-workspace/src/test/java/io/jharness/workspace/LocalWorkspaceTest.java:146

## OCR-1139 · 部分采纳

- 位置：harness-workspace/src/test/java/io/jharness/workspace/M8SearchFilterTest.java:189；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：Windows 分支的 `mklink` 子进程缺少生命周期兜底：当 `waitFor(5s)` 超时返回 false 时会直接抛断言失败，既不会 `destroyForcibly()` 也没有关闭 `getInputStream()`，进程可能继续存活；随后 `finally` 在子进程仍可能操作 `bridge` 的情况下删除 junction 与外部目录，可能留下孤儿进程、竞态删除失败，或让 `@TempDir`/临时目录清理报错（Windows 上偶发）。另外 `process` 的输出流在成功路径上也从未关闭。建议用 try/finally 包裹，超时或异常时强制结束进程并关闭输出流。
- 判断：M8 Windows mklink同1138资源接管缺口，合并独立原意见编号和跨用例清理。
- 证据：harness-workspace/src/test/java/io/jharness/workspace/M8SearchFilterTest.java:189

## OCR-1140 · 采纳

- 位置：harness-workspace/src/test/java/io/jharness/workspace/M8SearchFilterTest.java:147；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：校验边界覆盖不完整：这里只验证了超限输入（129 条规则、257 字符单条规则、100×200=20000 超过 16384），没有验证“恰好等于上限”的接受路径（128 条、256 字符、总长度恰为 16384），若 `checkedRules` 把 `>` 误写成 `>=` 造成 off-by-one，本用例仍会全绿。同时方法名声称的 “WithoutTruncation” 也没有断言合法规则未被截断（例如构造 128 条不同规则后回读 `configuration()` 或验证末条规则仍生效）。建议补充边界值与不截断断言。
- 判断：仅129/257/超总长缺等于界限接受，补末规则确生效且configuration不截断。
- 证据：harness-workspace/src/test/java/io/jharness/workspace/M8SearchFilterTest.java:147

## OCR-1141 · 部分采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/UnifiedPatch.java:36；OCR原严重度：high
- 计划：R3-05 / P1
- 原意见：hunk 体是按行前缀截取的，而不是按 @@ 头声明的 oldCount+newCount 精确消费，导致文件边界判定可被内容行污染：删除一条内容以 "-- " 开头的行会产生原始行 `--- xxx`，被当成新文件头；若紧随其后的新增行内容以 "++ " 开头（原始行 `+++ xxx`），两者会被解析成第二个 FilePatch。可构造 `--- a/a.txt` / `+++ b/a.txt` / `@@ -1 +1 @@` / ` one` / `--- foo` / `+++ foo` / `@@ -1 +1 @@` / `-x` / `+y`，解析结果除 a.txt 外还多出一个未在文件头声明的目标 foo，从而把改动注入到补丁未声明的路径；不满足该形态时则整份合法补丁报 INVALID_PATCH。建议：读到 @@ 头后按 oldCount+newCount 精确消费 hunk 体行（行数不足/多余即失败），只在文件级边界判断 `--- ` / `diff --git `。
- 判断：parse按---判文件边界，内容删除行以--开头会被误拆；应按hunk计数消费避免合法内容歧义。示例额外foo在头部明示，不据此认定隐藏越权注入，最终各路径仍审批/guard。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/UnifiedPatch.java:36

## OCR-1142 · 不采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/UnifiedPatch.java:39；OCR原严重度：medium
- 计划：无
- 原意见：这里只丢弃整个补丁末尾的单个空行，其余空行会被原样收进上一文件的 hunks：文件段之间或 hunk 之间的空行分隔符会进入 hunks，随后 apply 的 `line.isEmpty()` 直接抛 INVALID_PATCH；补丁文本以 `...\n\n` 结尾时倒数第二个空串同样被并入而报 INVALID_PATCH，错误码还会误导为补丁语法非法。建议在收集 hunk 体时显式跳过（或一致地拒绝）空行，并处理任意数量的结尾空行。
- 判断：exact unified subset可严格拒多余空白，空内容hunk行必须前缀，随意跳空行会改变补丁语义；不为扩展格式容忍度标缺陷。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/UnifiedPatch.java:39

## OCR-1143 · 部分采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/UnifiedPatch.java:55；OCR原严重度：medium
- 计划：R3-05 / P2
- 原意见：header() 无条件剥离 `a/` 或 `b/` 前缀，会把非 git 风格的补丁指向错误文件：工作区中真实存在 `a/foo`（或 `b/foo`）时，补丁 `--- a/foo` / `+++ a/foo` 会被解析为 path=`foo`；调用方按派生路径取版本并用 observed 兜底后可能校验通过，从而静默修改根目录下的同名文件而不是 `a/foo`。建议仅当新旧两侧都带同种前缀、且剥离后仍一致时才剥离（或要求调用方显式指定 strip 层数）；同时 `--- ` / `+++ ` 后为空时 header() 会返回空路径，parse 也应显式拒绝空 path。
- 判断：a/b前缀剥离无显式strip策略，可将无git真实a/foo误映射foo；明确只支持git前缀并校验头配对或显式strip，不接受模糊自动猜。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/UnifiedPatch.java:55

## OCR-1144 · 部分采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/UnifiedPatch.java:22；OCR原严重度：medium
- 计划：R3-05 / P2
- 原意见：跳过列表漏掉了 git 生成新增文件时必带的元数据行。真实 `git diff` 的 create 输出是 `diff --git a/x b/x` / `new file mode 100644` / `index 000..000` / `--- /dev/null` / `+++ b/x`，这里的 `new file mode 100644`（以及 `deleted file mode`、`old mode`/`new mode`）既不匹配 `--- ` 也不在跳过集合中，会落入下面的 `fail("INVALID_PATCH")`，导致注释中宣称支持的 create 在真实 git 补丁上完全不可用。建议把 mode 行一并纳入忽略列表（rename/similarity/binary 保持显式拒绝）。
- 判断：支持create但标准git新增含new file mode会拒，接受受支持模式元数据解析/明确诊断；不能忽略执行位变更再宣称完整支持git补丁。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/UnifiedPatch.java:22

## OCR-1145 · 不采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/UnifiedPatch.java:67；OCR原严重度：medium
- 计划：无
- 原意见：apply() 全程没有使用 FilePatch.create，导致 create 语义在本层没有被强制：对非空 before 应用 create 补丁（`@@ -0,0 +1,N @@`，全部为 `+` 行）时 start=0、removed=0=oldCount、added=N=newCount，所有冲突检查都通过，最终把新内容前置拼接在旧内容之前并当作成功返回，属于静默内容破坏。当前唯一调用方靠 version==MISSING 的校验兜住该场景，但该不变量应由补丁应用层保证。建议在构造 old 之后增加 `if (patch.create() && !old.isEmpty()) fail("PATCH_CONFLICT");`。
- 判断：UnifiedPatch包内唯一调用LocalWorkspace已严格create==MISSING保证before空；意见自己确认兜底，不是公开可绕过路径。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/UnifiedPatch.java:67

## OCR-1146 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:278；OCR原严重度：high
- 计划：R2-03 / P2
- 原意见：非 Jsonl 的 restore 分支只识别 accepted/verified/finished，末尾以 `Not a Web catalog` 结束；但 `/v1/tasks/{id}/metadata`（约 884 行）只检查 `settings != null` 与 `diskCatalog`? 实际上是无条件 `catalog.append(web.task.changed)`。触发条件：`catalog.durable()` 为真但 `diskCatalog == null`（非 Jsonl catalog 路径）时，用户改名/置顶/归档一次 → 重启 → 构造函数调用 restore → 直接抛 `Not a Web catalog`，服务无法启动。另外该分支 finished 时也没有恢复 `createdAt`（分页排序/游标错乱）与 `cancelRequested`。建议在 restore 中处理 `web.task.changed`（name/pinned/archived/revision）并从 finished 事件恢复 createdAt、cancelRequested；或在 metadata 写入处拒绝 `diskCatalog == null` 的情况。
- 判断：非Jsonl持久catalog restore不识别新metadata/turn事实，有替换存储兼容缺口；补统一catalog版本/能力约束，默认Jsonl路径不受此legacy分支影响。
- 证据：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:278

## OCR-1147 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:1964；OCR原严重度：high
- 计划：R1-05 / P1
- 原意见：`sdk.display().snapshot()` 在 try/finally 之外、且位于全局 `synchronized (this)` 内，未做任何异常保护。触发条件：display 快照抛 RuntimeException（SDK 已关闭/后端异常）→ 整个 finally 立即退出，后续 `sdk.close()`、`web.task.finished` 落盘、`occupied.remove(binding)`、`advance/pauseInputs`、`notifyAll()` 全部被跳过 → 任务永久停留在 CLOSING、workspace 永久 BUSY（后续提交全部 409 WORKSPACE_BUSY）、lease 指针不清理。另外该调用在持锁期间执行可能阻塞所有请求。建议对 snapshot 做 try/catch 并回退到旧值，最好移出同步块，且让 finally 中“关闭并落盘”不再依赖 sdk 状态。
- 判断：worker finally在cleanup try之前做display.snapshot，异常跳过sdk.close/catalog/占用结算；将展示失败隔离于必需清理且finally保证通知，保留未知。
- 证据：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:1964

## OCR-1148 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:2050；OCR原严重度：high
- 计划：R1-05 / P1
- 原意见：`pauseInputs(task)` 内部 `persistInput` 在 append/flush 失败时会 `broken = true` 并抛 WebFault，而此处没有任何 catch（相邻的 `advance(task)` 有 `catch (WebFault)`）。触发条件：暂停排队轮次时 catalog 写入失败（磁盘满/写入器已关闭）→ finally 异常退出，`notifyAll()` 被跳过、worker 线程以未捕获 WebFault 死亡、队列状态只更新了一半。建议与 advance 一样包一层 `try { pauseInputs(task); } catch (WebFault ignored) {}`，并保证 `notifyAll()` 独立执行。
- 判断：pauseInputs持久失败可跳notifyAll/剩余队列处理，需独立finally通知并记录broken/未决；不能简单空catch忽略丢失事实。
- 证据：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:2050

## OCR-1149 · 不采纳

- 位置：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:592；OCR原严重度：high
- 计划：无
- 原意见：`/v1/browser-session` 无需任何凭据即可换取 `browserToken`，而 `authorized()` 又把该 token 当 Bearer 接受于所有 /v1/ 接口。这里的唯一校验是 `Origin` 与 `Sec-Fetch-Site` 两个请求头，任何本机进程都能伪造。触发条件：本机任意进程发送 `Origin: http://127.0.0.1:<port>` + `Sec-Fetch-Site: same-origin` 的 POST → 拿到 token → 可提交任务、审批工具调用、读取/保存含 provider API Key 的 settings。多用户主机上即等价于绕过 bearer 鉴权；且该 token 无过期/轮换。建议签发时要求已有 bearer token 或一次性短 TTL nonce，并绑定同站 cookie/签名，至少加入过期与轮换。
- 判断：用户明确取消手输token并授权同源自动进入；产品单操作者loopback信任本机进程，Origin防网页跨站非本机恶意进程隔离。重新要求bearer/nonce违已批准产品边界，不扩展多用户模型。
- 证据：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:592

## OCR-1150 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:706；OCR原严重度：medium
- 计划：R1-05 / P1
- 原意见：`body(...)`（读 socket、解析 JSON）在持有全局 `synchronized (this)` 时执行，`settings.save` 也在锁内。触发条件：客户端发送合法 Content-Length 后缓慢/不发送 body → 请求在 10s/40s 超时前一直占住服务端唯一监视器 → 所有 submit、审批、metadata、以及 worker 的完成清理全部阻塞。workspaces POST 在锁内做 `body(...)` + `workspaces.register`（文件系统 I/O），turn POST 的 `body(...)` 也由 route 的 synchronized 包裹。建议先读体再进锁，只在状态变更处同步。
- 判断：部分route持全局锁body read/settings.save/注册IO，慢合法body可阻塞取消与worker清理；解析/有界I/O移锁外，状态变更锁内复检revision/占用。
- 证据：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:706

## OCR-1151 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:734；OCR原严重度：medium
- 计划：R1-05 / P1
- 原意见：`profiles` 是非并发 `LinkedHashMap`，`/v1/info` 在无锁下 `keySet().stream()` 遍历，而 `POST /v1/workspaces` 在持锁时 `profiles.put(...)`。触发条件：UI 并发轮询 /v1/info 同时新增工作区 → `ConcurrentModificationException` 或返回不一致列表。同一类问题还出现在 /v1/models：`settings == null` 检查后无锁读取非 volatile 的 `settings` 字段，可能与 setter 竞争而读到 null（NPE）或过期值。建议在锁内生成不可变快照，或改用 volatile/AtomicReference 发布快照。
- 判断：profiles LinkedHashMap写有锁但info遍历无锁可能CME，使用不可变快照；settings setter是否运行期并发需按构造装配约定，不凭非volatile认定已NPE。
- 证据：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:734

## OCR-1152 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:531；OCR原严重度：medium
- 计划：R1-05 / P1
- 原意见：`taskPage` 由 `synchronized (this)` 包裹，却在此处对整个 catalog 做 `diskCatalog.scan` 并对每条历史 `parseTask`，而 `parseTask` 末尾的 `reconcileArchival` 又会通过 `events(task)` 读取完整会话文件、`summary` 再读一次。触发条件：历史较长时请求 GET /v1/tasks?limit=32 → 单次分页即 O(全部历史) 的文件 I/O，期间 submit/审批/cancel/close 全部阻塞。`followup()`（每次 submit 与 enqueue 都全量 scan+parse）、`/v1/search` 的 scope 扫描、`forkTask` 的 `forkTo` 拷贝、`/v1/skills` 的扫描同样在锁内执行。建议锁内只取边界快照，扫描与文件复制放到锁外，并缓存已归档状态避免在列表路径读会话文件。
- 判断：锁内全catalog scan+会话reconcile/summary阻塞其它操作；有界元数据快照/派生索引与短锁，保持冷历史不计32热对象及实时授权核实。
- 证据：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:531

## OCR-1153 · 不采纳

- 位置：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:2206；OCR原严重度：medium
- 计划：无
- 原意见：分页/流式预算用 `String.length()`（UTF-16 单元）去比较字节上限 `RESPONSE_LIMIT`，会严重低估 UTF-8 体积（CJK 1 字符=3 字节，`\uXXXX` 转义=6 字节）。触发条件：事件 payload 含较长中文 → 此处判定“可放入”，随后 `respond()` 以 `bytes.length > 524288` 判定超限并改写为 413 RESPONSE_LIMIT，/search、/report、SSE 帧同样受益于同一错误假设。建议用 `WebJson.encode(...).getBytes(UTF_8).length` 或已编码字节数组长度作为预算。
- 判断：当前eventPage源码WebJson.encode(...).length即byte[]长度，SSE frame也.getBytes后length；OCR把字节数组误当String，中文低估前提不成立。
- 证据：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:2206

## OCR-1154 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:914；OCR原严重度：low
- 计划：R5-02 / P3
- 原意见：同一“会话历史是否可用”的检查被连续执行两次，第二次永远不会产生新的结果，只是多读一次会话文件。建议删除重复的一处。
- 判断：相邻两次同一reader.events.isEmpty检查确重复全读，去重并在已取得独占后的实际快照使用，不丢并发安全。
- 证据：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:914

## OCR-1155 · 不采纳

- 位置：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:329；OCR原严重度：low
- 计划：无
- 原意见：`task.turns.inputs.get(p.get("requestHash"))` 未判空：当 catalog 中存在 `web.turn.started` 而没有对应 `web.turn.accepted`（中断/部分写入）时返回 null，直接 NPE。触发条件：重启或磁盘扫描一个含孤立 started 事实的 catalog → 构造函数 `diskCatalog.scan` 内抛裸 NPE，而不是可诊断的“catalog 损坏”错误（`attachLease` 对同一映射做了判空，此处不一致）。建议判空并标记 UNKNOWN/RECOVERY_REQUIRED。
- 判断：parse先task.turns.apply，孤立started已在apply input==null时抛IAE，不会执行后续selection解引用；回放schema可整体增强但给定NPE链不成立。
- 证据：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:329

## OCR-1156 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:1151；OCR原严重度：low
- 计划：R1-05 / P1
- 原意见：审批超时与审批应答存在竞态：worker 侧在 `approvalTimeout` 到期后返回拒绝并在 finally 里移除条目，而在此之前一刻到达的 POST 仍会 `complete(...)` 并返回 200 accepted。触发条件：操作员恰好在上限时刻点击审批 → 前端以为已批准，工具调用实际被拒绝。建议让“超时”和“接受”互斥（例如超时后先移除条目、complete 前再次校验是否仍在表中；已超时返回 409/410）。
- 判断：POST只看decision未完成且complete结果未核对，worker超时移除前窗口可回accepted但实际拒绝；使用共同原子决策/截止时间并持久精确关联。
- 证据：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:1151

## OCR-1157 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:2248；OCR原严重度：low
- 计划：R4-03 / P2
- 原意见：SSE 因字节预算截断时直接 `return`，既不发送 `more` 标志也不发送终止事件/注释。触发条件：单帧超限或累计超 RESPONSE_LIMIT → 客户端无法区分“被截断”和“事件已结束”，与 `eventPage` 返回的 `more` 语义不一致。建议在关闭前发送明确的终止事件（如 `event: truncated` 或 `: more` 注释），并考虑发送心跳注释避免中间层超时。
- 判断：SSE预算return缺明确more终止提示，客户端应GET after对账而非推断流EOF全部结束；增加有界尾帧/续读说明，不因此自动重连重发工具。
- 证据：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:2248

## OCR-1158 · 采纳

- 位置：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:511；OCR原严重度：low
- 计划：R4-03 / P2
- 原意见：分页排序把 `createdAt` 当字符串比较（`thenComparing(t -> t.createdAt, ...)` 与 `after()` 同源）。ISO-8601 小数位精度不同时字符串序与时间序不一致（`…:00Z` 会排在 `…:00.123Z` 之后）。触发条件：混有整秒时间戳或恢复后默认时间戳的任务 → 分页顺序/游标错位，甚至同一页重复或漏项。建议内部保存/比较 `Instant`（或 epochNanos），仅在输出时序列化为字符串。
- 判断：Instant字符串变精度时字典序与时间序不同，创建整秒/小数混排可破游标；统一Instant排序及cursor边界，保留stable id次序。
- 证据：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:511

## OCR-1159 · 部分采纳

- 位置：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:2600；OCR原严重度：low
- 计划：R1-05 / P1
- 原意见：关闭流程不等待在途请求/worker 结束：`http.stop(0)` 无宽限地中断在途响应，`requests/deadlines.shutdownNow()` 后没有 awaitTermination，worker join 只等 2s 就 `catalog.close()` 与 `leases.close()`，而 worker 可能仍在 `sdk.close()` / `SessionRecovery.inspect(events(task))` 中读写会话文件。触发条件：有任务运行或慢请求时调用 close() → 可能向已关闭的 catalog 追加事件（并把 `broken` 置位）、lease 检查点丢失。另外 metadata 路由未检查 `closed/broken`，关闭过程中仍可写入 catalog。建议 metadata 先检查 closed，使用 `http.stop(1)` 宽限，并在关闭 catalog/leases 前 awaitTermination。
- 判断：close2秒后关catalog/lease但worker可能仍清理，需明确退场/未知记录与owner释放顺序；仅http.stop(1)不足证明退出，不能无限await不合作线程。
- 证据：harness-web/src/main/java/io/jharness/web/HarnessWebServer.java:2600

## OCR-1160 · 部分采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:153；OCR原严重度：high
- 计划：R2-01 / P1
- 原意见：LocalWorkspace.applyPatch adds the failing patch to pendingPaths using skip(changes.size()). If write fails after commit with UNKNOWN_SIDE_EFFECT, the already-applied patch is returned as pending and may be replayed. Distinguish pre-commit failures from post-commit uncertain paths and exclude committed paths from pendingPaths.
- 判断：pendingPaths包含提交后UNKNOWN的失败路径，需区分未尝试/已知提交/需核实，禁止把pending解释为可重放；未知提交不能武断从列表删除当已知成功。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:153

## OCR-1161 · 部分采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:380；OCR原严重度：high
- 计划：R5-01 / P2
- 原意见：LocalWorkspace.readPage derives nextLine only from newline count. When the last line has no newline, line does not advance and nextLine repeats the same line; when startLine is beyond EOF, nextLine can be less than startLine. Track EOF explicitly and ensure nextLine advances or return a no-more-page sentinel.
- 判断：无末尾换行nextLine可重复，超EOF可能小于请求，需明确more/EOF并单调/哨兵协议；此readPage单位是行不同ContentPages UTF16，不能混改。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:380

## OCR-1162 · 采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:362；OCR原严重度：medium
- 计划：R2-01 / P2
- 原意见：readPage performs the final version check outside synchronized and only puts observed inside the lock. A concurrent write can commit between check and put, so the old version overwrites observed and later writes see a stale guard. Keep version comparison and observed update in one synchronized block.
- 判断：readPage最终version check与observed.put不在同锁，读缓存可倒退；同快照派生版本与CAS/锁内更新并发回归。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:362

## OCR-1163 · 部分采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:815；OCR原严重度：medium
- 计划：R1-04 / P1
- 原意见：gitDiff only removes GIT_* environment variables but inherits PATH, HOME, XDG_CONFIG_HOME, and global or system git config. This can change the git binary or output such as color, prefixes, or renames. Use an explicit minimal environment, isolate git config, and pass --no-color.
- 判断：gitDiff继承HOME/PATH/global config可影响外部命令/helper/输出，需可信git定位及最小env/config禁外部diff；保留必要平台变量，不能仅--no-color当全隔离。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:815

## OCR-1164 · 采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:818；OCR原严重度：medium
- 计划：R1-03 / P1
- 原意见：gitDiff reads stdout and stderr with readNBytes up to a limit and then stops draining the pipes. If git writes more, it blocks on the full pipe and waitFor never returns; the probe reports TIMEOUT instead of OUTPUT_LIMIT. Continue draining after exceeding the limit or destroy the process and return OUTPUT_LIMIT.
- 判断：git stdout/stderr限读后停止排空可能写端阻塞到deadline，需超限快速终止/继续有界丢弃排空并精确限额结果，进程总时限仍保留。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:818

## OCR-1165 · 采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:451；OCR原严重度：medium
- 计划：R2-01 / P1
- 原意见：Atomic replace via temporary file and Files.move loses the target file metadata. POSIX mode such as the executable bit, ACLs, and xattrs are not preserved because the temp file has default permissions. Copy target permissions and attributes to the temporary file before the move or restore them afterward.
- 判断：原子replace用默认temp权限会丢执行位/ACL等，写现有脚本可损坏可执行性；创建前复制可支持安全属性、明确不能保留则拒或报告，保留原子提交与边界保护。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:451

## OCR-1166 · 部分采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:694；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：Directory walk continues after the result limit is reached because the visitor still returns true. Large workspaces can exhaust the timeout and discard found results instead of returning truncated=true. Stop or prune the walk once the limit is reached.
- 判断：search达到limit仍遍历可超时丢结果，应limit+1确认truncated后终止并保留有效结果；不能假定visitor false表示全局停止需核walk契约。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:694

## OCR-1167 · 部分采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:718；OCR原严重度：medium
- 计划：R5-02 / P2
- 原意见：grep or read scanning breaks only the inner loop when matches reach the limit; the outer loop still opens and scans all remaining files. This can exhaust the timeout and discard collected matches. Break the outer loop or check the limit at the top of the loop and return truncated results.
- 判断：grep命中limit后仍扫其它路径，需外循环停止并准确标truncated，不为确认无更多命中无限遍历。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:718

## OCR-1168 · 不采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:172；OCR原严重度：low
- 计划：无
- 原意见：LocalWorkspace.deny always throws, so the following return root is unreachable dead code. If deny ever changes, invalid paths would resolve to the workspace root. Remove the return or replace deny with a thrown HarnessException.
- 判断：deny永抛而return满足Java编译控制流，未导致invalid path返回root；假设未来deny不抛不构成当前bug。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:172

## OCR-1169 · 采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:475；OCR原严重度：low
- 计划：R1-02 / P1
- 原意见：LocalWorkspace write cleanup calls Files.deleteIfExists in finally without suppressing IOExceptions. A cleanup failure can replace the original HarnessException such as FILE_CONFLICT or UNKNOWN_SIDE_EFFECT, losing the error code. Catch and addSuppressed cleanup failures, only throwing if no primary exception is propagating.
- 判断：write finally delete临时失败可覆盖FILE_CONFLICT/UNKNOWN_SIDE_EFFECT，保留主异常+suppressed及残留路径，不把清理失败当未写。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:475

## OCR-1170 · 采纳

- 位置：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:351；OCR原严重度：medium
- 计划：R5-01 / P2
- 原意见：页面恰好结束于文件最后一个换行时（endLine 为末行、文件以 \n 结尾），++line 变为 endLine+1 便置 truncated=true，但此后已无内容；调用方会多发一次空页请求，把“已读完”误判为“被截断”。建议仅在确实在 endLine 之后仍读到字符时才置 truncated（例如循环结束前用尾随换行后的实际剩余内容判断）。
- 判断：恰末尾newline时先++line即truncated=true，实际无下一内容；窥读/EOF状态明确，覆盖尾换行/无尾换行/超EOF。
- 证据：harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:351

## OCR-2354 · 部分采纳

- 位置：harness-llm/pom.xml:7；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：jackson-databind 的版本在这里直接硬编码，未交由父 POM 的 dependencyManagement 统一管理。同一字面量 2.21.7 还分别出现在 harness-session/pom.xml 与 harness-ecosystem/pom.xml 中，升级或修复漏洞时必须同步修改三处，任一处遗漏都会导致同一 reactor 内多个 Jackson 版本共存，进而在 ObjectMapper 共享（如 harness-web 侧的 Jackson 传递依赖）时出现序列化/反序列化行为不一致或方法链接错误。建议在根 POM 中新增 jackson.version 属性并放入 dependencyManagement，本模块只声明 groupId/artifactId 而不声明 version。
- 判断：三子模块Jackson当前均2.21.7无版本漂移，统一根属性/management可降低将来漏升级风险；不宣称当前NoSuchMethodError或漏洞。
- 证据：harness-llm/pom.xml:7

## OCR-2358 · 部分采纳

- 位置：harness-ecosystem/pom.xml:61；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：jackson-databind 直接在子模块中硬编码版本 2.21.7（harness-llm、harness-session 也各自重复声明同一版本），而父 POM 的 dependencyManagement 只管理了 junit-jupiter，未对 Jackson 串做统一管理。触发条件：后续任一模块单独升级 Jackson（或父 POM 引入 jackson-bom/其他库传递引入更高版本的 jackson-core/jackson-annotations）时，各模块及其下游扁平化 classpath 上会出现 databind/core/annotations 版本不一致，典型表现为 NoSuchMethodError/NoClassDefFoundError 或反序列化行为差异；同时该硬编码版本绕过了依赖管理，无法通过父 POM 一键统一或做 CVE 升级。另外该版本号需确认已在中央仓发布，否则依赖无法解析导致构建失败。建议在父 POM 的 dependencyManagement 中导入 com.fasterxml.jackson:jackson-bom 并移除本处的显式版本，使 databind/core/annotations 三者保持同一版本。
- 判断：与2354同源重复固定版本，接受集中约束及依赖收敛门禁；M9已有2.21.7实际构建/扫描证据，不重查发布存在性制造待办。
- 证据：harness-ecosystem/pom.xml:61

## OCR-2364 · 部分采纳

- 位置：harness-session/pom.xml:7；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：第三方依赖版本硬编码在模块 POM 中，未纳入父 POM 的 dependencyManagement：同一份 jackson-databind 版本 2.21.7 已在 harness-session、harness-llm(第7行)、harness-ecosystem(第61-65行) 三处重复声明。后果是升级/修复 Jackson 安全漏洞时必须逐模块手动同步，任一模块漏改就会在同一 classpath 上出现版本漂移（Maven 最近者优先），难以保证全仓版本收敛；同时该依赖为 compile 作用域，会以固定版本传递给所有下游消费者，放大冲突面。建议在根 pom.xml 的 <dependencyManagement> 中统一定义（如 <jackson.version>2.21.7</jackson.version>），本模块只保留 groupId/artifactId 不写 version。
- 判断：第三处Jackson重复归同修复组；compile依赖本身正常，不因可传递即缺陷。
- 证据：harness-session/pom.xml:7

## OCR-2365 · 部分采纳

- 位置：harness-process/pom.xml:6；OCR原严重度：medium
- 计划：R6-01 / P2
- 原意见：pty4j 版本硬编码在叶子模块（第 6 行），而父 POM 的 `dependencyManagement` 只管了 junit，其余第三方版本也散落在各模块（如 harness-ecosystem 的 jackson）。一旦其他模块后续也需要 pty4j，极易出现版本分叉。同时该依赖加载本地 native 库：0.13.12 需要确认目标 OS/架构（Linux/macOS/Windows 的 x86_64 与 aarch64，以及 `setUseWinConPty` 依赖的 ConPTY）的 native 产物是否随 jar 打包；已看到 `LocalTerminalSession` 捕获 `LinkageError` 降级为 `PTY_UNAVAILABLE`，但打包层面缺失 native 会表现为所有 PTY 会话统一不可用而非可诊断错误。建议把版本移到父 POM 的 `dependencyManagement` 统一管理，并补充目标平台矩阵说明/构建期校验（例如在 CI 中对部署架构做一次 PTY 冒烟测试）。
- 判断：pty4j仅一处版本不存在当前重复冲突；保留原生架构实机矩阵与缺支持诊断，三平台当前验收范围按历史证据，不新承诺所有CPU。
- 证据：harness-process/pom.xml:6

## OCR-2366 · 部分采纳

- 位置：harness-process/pom.xml:7；OCR原严重度：high
- 计划：R6-01 / P2
- 原意见：库模块中声明 `slf4j-nop` 且为 `runtime` scope（第 7 行）会带来两个可复现问题： 1) `runtime` 依赖是可传递的，任何依赖 harness-process 的应用（harness-cli / harness-ecosystem / 外部宿主）都会被迫在 classpath 上带上 no-op provider。SLF4J 2.x 检测到多个 provider 时只会非确定性地挑选其中一个，一旦选中 nop，宿主自身的日志后端（logback/log4j2）将整体失效，导致 PTY/进程异常等诊断信息被静默吞掉。 2) 本模块源码中没有任何 `org.slf4j` 引用（已全量检索），该绑定实际只作用于 pty4j 传递进来的 `slf4j-api`。而 pty4j 0.13.12 若传递的是 1.7.x 的 `slf4j-api`，2.0.13 的 provider 不会被 1.7 API 识别（1.7 依赖 `StaticLoggerBinder`），绑定实际不生效且仍打印告警，等于既没消除告警又污染了消费方。 建议：库模块不要声明日志绑定，交给宿主决定；若仅为单测/自测消除告警，改为 `test` scope（并显式对齐 slf4j-api 的大版本）。
- 判断：库模块runtime传递slf4j-nop可抢外部宿主日志provider，绑定应移可执行宿主/测试层；pty4j实际API版本需锁闭包核实，不能据“若1.7”认定当前不兼容。
- 证据：harness-process/pom.xml:7

## OCR-2367 · 部分采纳

- 位置：harness-web/pom.xml:11；OCR原严重度：medium
- 计划：R6-01 / P1
- 原意见：`${project.basedir}/../frontend/dist` is copied wholesale into `ui/` with no existence/freshness validation and no `includes`: Maven silently skips a missing resource directory, so a build without a prior `npm ci && npm run build` (or a `-pl harness-web` / source-archive build) still succeeds with no `ui/` in the artifact and `UiResources` degrades to 503, while a stale dist (not removed by `mvn clean`) ships old hashed assets and debug output. Add explicit `<includes>` (`index.html`, `ui-files.txt`, `ui-license.txt`, `assets/**`), fail the build when they are absent (e.g. enforcer `requireFilesExist`) or generate the dist via frontend-maven-plugin, and pin `maven-resources-plugin` in the parent pluginManagement so the copying behavior is reproducible.
- 判断：Maven缺dist仍可打包且clean不删旧dist，README明确前置npm构建/根503；需要候选发布门禁校验前端输入/包内hash，不必强制所有纯后端构建依赖Node。
- 证据：harness-web/pom.xml:11

## OCR-2439 · 采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/M6StreamingTest.java:190；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 191 行的 body 只有 `data: {bad}\n\n`，缺少 `[DONE]` 终止符：即使实现完全忽略/丢弃非法 SSE 事件，流结束时也会因 `!done` 抛 MODEL_STREAM_INCOMPLETE，该用例仍然通过，无法证明非法 JSON 被拒绝（第 4 个 focus 点所述问题成立）。另外第 213-216 行循环内只断言异常类型，五个用例产生的 MODEL_STREAM_INCOMPLETE / MODEL_INCOMPLETE / DUPLICATE_CALL_ID / MODEL_BAD_RESPONSE 无法区分，任何一个校验分支失效都可能被其他分支掩盖。建议该用例改为 `"data: {bad}\n\ndata: [DONE]\n\n"`，并给每个 body 断言具体的 error code（例如 duplicate id 用例断言 DUPLICATE_CALL_ID、非法参数用例断言 MODEL_BAD_RESPONSE）。
- 判断：非法JSON SSE缺DONE可被流不完整兜底，补非法帧后DONE与精确错误码，防校验分支被其它失败遮盖。
- 证据：harness-llm/src/test/java/io/jharness/llm/M6StreamingTest.java:190

## OCR-2440 · 部分采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/M6StreamingTest.java:258；OCR原严重度：medium
- 计划：R0-03 / P1
- 原意见：脱敏断言只匹配完整密钥 `secret-stream-key`（或恰好包含 `secret-stream-` 的字符串），更短的跨 delta 片段（如 `secret-str`）泄露不会被发现。且该话题的两个增量分别只有 21 和 30 个字符，都小于 SseBody 裁剪的 apiKey 长度(17) 与 StreamingLlmClient 的 hold(17)，经两层裁剪后所有中间 display 值实际都是空串（""、""），本例并没有真正验证“密钥跨 delta 时预览被暂扣”的路径，display 为空时 noneMatch 也恒为真。建议在密钥前加入 >34 个字符的普通文本使中间帧非空，并断言 display 非空且任何片段都不包含 KEY 的任一长度≥4 的前缀（或拼接后不含任何密钥片段）。
- 判断：中间display可能全空使秘密noneMatch真空通过，补长公开前缀marker及非空中帧/跨delta秘密回归；任意>=4前缀禁止会误杀正常文本，按既定秘密策略测试。
- 证据：harness-llm/src/test/java/io/jharness/llm/M6StreamingTest.java:258

## OCR-2441 · 部分采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/M6StreamingTest.java:151；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`wire` 只在 handler 线程的 `synchronized (wire)` 中写入（第 46-50 行），此处读取没有加锁：ArrayList 非线程安全，且 server handler 线程与测试线程之间不存在针对 `wire` 的 happens-before（HttpClient 的 future 完成只与客户端内部线程同步）。触发条件为用例并发/机器负载较高时，这里可能读到过期快照；若后续有人把 `wire` 改成清空/重建逻辑还会出现 NoSuchElementException。建议读取时同样加锁，或改用 CopyOnWriteArrayList / 在 handler 完成后通过 latch 发布不可变快照。
- 判断：wire写同步ArrayList读无统一锁/快照，使用线程安全收集或latch发布；网络响应先后不替代共享Java对象同步，不能用假设未来clear当当前NoSuchElement故障。
- 证据：harness-llm/src/test/java/io/jharness/llm/M6StreamingTest.java:151

## OCR-2442 · 部分采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/M6StreamingTest.java:85；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：handler 在写完响应后仍会 `Thread.sleep(delay)`（取消/超时用例中为 500ms），而 `server.stop(0)` 不会等待或中断仍在执行的 exchange handler，上一用例的 handler 可能在本用例的 server 已停止后继续写已关闭的 socket；同时第 64-68 行把包括非取消场景写失败在内的所有 IOException 静默吞掉，导致取消/超时用例可能因看不到真实传输错误而“偶然通过”，handler 线程也可能在测试结束后继续存活。建议为 HttpServer 显式配置只含 daemon 线程的 Executor，close 时 stop 后 awaitTermination（或中断 handler），并且只对确实由客户端取消的连接忽略 IOException，其余记录或让测试失败。
- 判断：stream fixture handler写后sleep且stop0无等待，可遗留线程，接受显式executor有界回收/预期断连分类；daemon不是资源清理证明。
- 证据：harness-llm/src/test/java/io/jharness/llm/M6StreamingTest.java:85

## OCR-2443 · 采纳

- 位置：harness-llm/src/test/java/io/jharness/llm/M6StreamingTest.java:145；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：`allMatch` 在 frames 为空时恒为 true：若实现回归为不再产出任何 preview 帧，该断言无法发现“预览丢失”。建议先断言 frames 非空，再校验每个帧的 toolCalls 为空（必要时再断言至少一个帧的 text 符合预期），避免空集合导致的假通过。
- 判断：frames.allMatch空集可通过，必须先非空并核实际文本/工具调用仅终态释放，防流预览回归。
- 证据：harness-llm/src/test/java/io/jharness/llm/M6StreamingTest.java:145
