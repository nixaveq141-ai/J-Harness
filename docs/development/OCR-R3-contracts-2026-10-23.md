# R3 公共 SPI 与协议补充契约

日期：2026-10-23。本文件规定采纳范围的行为和兼容边界，实施/验证状态另见 R3 修复记录；文字不代表所有第三方实现均已动态认证。插件运行在可信 JVM 内，不构成 OS 沙箱。

## 公共调用、资源与错误

- 除明确的 Optional、可选关联和默认值外，服务、参数、取消令牌及结果非空。替换实现须使用相同作用域、错误域与限额。构造器的编程空参可抛具名 NPE/IAE；外部 JSON 与配置入口用领域错误。HarnessException.code 必须非空、非空白，cause 仅供内部诊断，不能对外序列化。业务标识非空不意味着 JSON 对象键非空；空字符串/空白键是合法 JSON。
- AgentLoop.run 为单会话 owner 调用，token 非空；结果从持久事实投影读取。装配/恢复方负责拒绝未决 UNKNOWN。Factory 返回的 loop 借用 dependencies，不隐式接管所有共享依赖。PromptComposer 可缓存派生数据，但须从同会话有序投影重建请求；不能以平行聊天数组替代事实。请求需在外部发送前按预算及字节限制检查，不按集合实现类名免除快照。
- AgentTool 的名称、描述、输入/输出 schema 和副作用等级在注册生命周期中稳定且不可变；无参工具声明 object schema。声明缺失/非法不能补空后放行。validateArguments 是无 I/O、无资源分配的纯语义检查，位于审批/intent/body 前；hook 改参后重新检查。execute 的未分类异常可能发生在副作用之后，不能推断未执行。
- ApprovalService 的决策非空，仅适用于当前精确调用。取消、拒绝或服务失败时不进入主体；ASK 从不代表批准。PolicyEngine 可被父/子宿主共享，须无状态或线程安全。预览可展示 ASK，无须为只读预览先批准。
- ApiCostGuard.maximumCost 非空、非负，币种与 Budget 一致。默认 actualCost 仅适合纯函数守卫；有状态预留实现必须覆盖实际结算及 requestRejected 释放，不能把默认空回调解释成自动释放。兼容 AgentRuntimeServices 构造器的 unavailable 守卫在费用预算启用时发送前拒绝。零单价允许表达可信免费模型。
- Environment.files/processes 等能力引用应稳定、非空；不支持能力显式拒绝。terminal 可共享，借用调用者不能单独关闭环境拥有的实例。descriptor 是无秘密、版本化稳定的恢复绑定，不可任意因实例变化而漂移；unspecified 不授予权限。拥有资源的环境/LLM/scheduler 必须覆盖幂等 close 并报告不能确认的清理；无资源实现保留空默认。注入 ToolSchedulingPlugin 的 scheduler 和 PolicyPlugin 接管的 approval 不能再由其他 owner 无条件重复使用。
- LazyModelRouter 的 factory 在注册锁外执行，每条路由同时仅一个构造者；不得循环依赖其他 factory 或无限阻塞，部分构造失败由 factory 清理。关闭先撤销待构造的等待资格，迟到实例立即关闭；FACTORY_PENDING/关闭失败保持可诊断，不能当作所有构造线程已经终止。
- ModelRouter.select 对未知/空路由拒绝，禁止静默回退。返回客户端由 router 所有，使用方借用；RoutedLlmClient 接管整个 router，因此不得给多个独立 owner 共享同一 router。close 只关闭 router，不能再单独关闭 selected。观测与展示装饰器 select 保留装饰；低层 stream 由调用者 consume/finalResponse/close，Observed 的元数据结算仅在 complete/completeSingleAttempt 路径产生，直接低层 stream 不额外伪造 observed。
- ModelAttempt 仅明确的 ModelRejectedException 可供重试；singleSend=true 禁止适配器内部重试。ModelExecution.once 保留 complete(false) 的兼容语义，不承诺一次 wire 发送。流式不自动重试；传输 UNKNOWN、关闭或取消不能证明未发送。展示 Map 是已脱敏的有界临时快照，不能作为执行事实。

## 事件、作用域与事实

- EventBus 监听精确类型或 `*`，禁止回调内递归 publish。serial 首异常停止后续；超时合作 interrupt，不能强杀任意 Java 代码。notify 是有界异步观察。拦截器按 priority 升序、同值按注册顺序形成链；合法短路可不 proceed，副作用入口不得多次 proceed。扩展点只检查 raw Class 类型，泛型载荷不能据此获得深结构保证。
- ServiceRegistry/ToolRegistry 精确匹配 scope/type/key/name，无父级或全局回退；同 scope 同身份注册拒绝。Registration 仅撤销自身且幂等；visibleTools 为独立列表快照。ServiceRequirement.key=null 表示唯一查询，空白键不合法。requireUnique 缺失/多值分别 MISSING_SERVICE/AMBIGUOUS_SERVICE。
- PluginContext 关闭后不能新增注册；own 逆序清理、聚合失败。插件部分 start 后也会 stop，stop 要幂等并处理未完全初始化。依赖拓扑/API 版本先核验，运行时服务 requirement 在启动后核验；不能静态预知插件将注册什么。第三方插件须从构建描述符声明编译 API，默认 CURRENT 只为同构建兼容。
- checkpoint.failed 允许 SESSION/TURN/STEP/TOOL 四级，均须满足该级关联字段；user.message 兼容历史 TURN 与当前 SESSION，拒绝 STEP/TOOL。其他第一方类型按固定等级校验，未知插件事件仍可扩展。
- SessionReader.events 是有序、非空、不可变的单会话快照，无长期开启句柄。Projector 确定性地保留未决工具/模型事实及 UNKNOWN。SessionWriter 为单 owner 顺序调用；append 返回事实 ID，异常可能已提交，失败 writer 不可继续或自动重试。flush 按 durable 声明保证前缀，内存测试 writer 不冒充 fsync。CheckpointPolicy 只决定等级，实际耐久屏障由调用方执行；副作用 intent 未 durable 时不得进入主体。
- RequestJournal 接收最终已脱敏 prepared envelope，不改变 wire 请求。record 返回 requested ID，调用方仍负责 checkpoint。reconstruct 使用有序同会话日志前缀及匹配 ID；UNKNOWN 的重建可用于核实，禁止的是发送重放。fork 复制新 session 的指定前缀，保留原未决事实，不赋予执行资格；writer 持独占锁，必须 close。
- PreparedToolCall.run 可在 worker 计算，finish 由 owner 对同 call 恰好一次结算；超时未合作 worker 可先结算 UNKNOWN，不能等任意线程结束才释放 owner。prepare=empty 必须无可观察准备副作用，调用方先 drain 屏障再 execute；barrier 失败不得继续。批次结果保留输入顺序与已执行前缀，UNKNOWN/取消/超时/检查点失败停止，不能合成确定 FAILED 后继续。
- 工具结果原始 diff/modelContent 不在内部无条件截断为不合法 JSON。持久化、模型请求及对外展示边界负责脱敏、字节限额和产物溢出。ToolResults.event 是原始格式化器，调用方必须先 sanitize。DataSanitizer 输入为不可变 JSON；输出非空、JSON-compatible，链中失败必须停止，不能回退原文。

## 文件、检索、预览与交互

- 写后版本/结果读取失败可能 UNKNOWN，不承诺“所有异常都未提交”，不得自动重试。目录、链接、注册根与当前文件内容检查沿用 M8/R2 及 M10.5.6。ContentInspection 的 version 是当前文件版本，offset/line 为有界分页/定位参数；冲突必须重新核实，不能用记录 Diff 冒充当前内容。
- ContentSearchProvider 的 mode 是 tokens/phrase，limit 为 1..4096；调用者传授权 session 和合作取消。Hit 的 eventId/type/timestamp 必填，turnId 可规范为空串；snippet 已脱敏，最多400 UTF-16单元，score 非负，由具体索引定义。Result 的 indexed/reused 非负且 reused<=indexed；rebuilt 不隐含 reused=0。排序由具体索引固定，索引为可丢弃派生数据，取消不要求回滚已更新缓存。
- PlanPreviewer 返回有界非空 Map，至少 previewable/executed，成功支持时含 path/beforeVersion/diff/truncated/advisory；executed 必须 false。预览不调用模型、进程或文件写入。路径/字段/类型/expectedVersion/UTF-8 字节限额与真实写入一致；预期文件冲突作为可纠正的失败预览，取消/UNKNOWN 保持终止。预览结果不能代替执行时审批和版本守卫。
- TerminalSession 的 start/status/log/write/resize/stop 都有 owner 绑定。log 使用非负字节 offset、有界 limit；states 是有界快照，默认空只表示缺状态能力，不能认证清理完成。stopAll 默认 close，具体 provider 应说明终止后能否复用；不合作资源的清理失败保留 UNKNOWN。
- UserQuestionService 绑定 scope/turn/call/question，只有明确用户输入可 respond。list 最多64条且统一脱敏；draft 不等于提交，requestId/answerHash 用于最终 ANSWERED/SKIPPED 对账。SUBMITTED 是暂存，字段、长度、历史及取消规则沿用 R2 和 M10.5.5，不批准工具或计划。SubagentService 最多32个生命周期任务，状态含稳定任务/父身份；cancel 只投递取消，不证明子任务已终止。
- ContextCompactor 的 maxCharacters 为正，返回 tools/parameters 保持，removedMessageIndices 引用原始 request、无重复、范围合法；不能按返回消息长度解释。保留 system、最新用户和未决协议组；压缩记录先于最终请求日志，不以摘要证明工作完成。

## 支持的协议子集

- JSON 快照最大深度64、总节点100000，重复共享 DAG 按展开节点计数；环在限额内拒绝。实例非 JSON/非有限数/环归 SCHEMA_MISMATCH；声明的嵌套形状、const/enum 非 JSON、缺 schema 归 INVALID_SCHEMA。对象空键允许，业务身份仍 required。整数配置使用精确十进制整数及 int 范围，不用浮点近似比较。
- Responses 流必须先 created，output_item added/done 集合与 completed.output 总量和索引一致。支持两种整流格式：全部事件都有递增 sequence_number，或旧替身/兼容服务整流都省略；不允许混合省略或逆序。Anthropic 只支持已声明文本/tool_use，stop 字符串规范为 stop_sequences 列表；reasoning/provider_state 只保存白名单字段。configured model+endpoint+protocol 是状态绑定；响应 model 可为 provider 的版本别名，不用于静默切换配置路由。
- API key 配置只接受1..8192个可打印非空白 ASCII字符，错误不回显内容。URI scheme/host 按标准大小写不敏感；仍只 HTTPS 或 loopback HTTP，不允许 URL 凭据/query/fragment。localhost 依赖可信宿主解析/hosts，单次 DNS 检查不构成无竞态网络沙箱。
- MCP JSON-RPC response 明确 id，result/error 恰好一个，error 的 code 整数/message 字符串。带 null id 的请求仍须结构化响应，不能当通知。transport 每实例超时为(0,60]秒，输入不可变/输出有界快照，kind 稳定无秘密，close 幂等。非只读 tools/call 的 RPC error 不普遍证明未执行，保持 UNKNOWN/禁重放；只读明确 RPC error 可确定失败。所有外部内容标 untrusted，不能授权工具。
- 技能文件每份64KiB、总量512KiB，脚本同时满足 CommandRequest：非空白、无NUL、总字符<=32768、显式 shell mode/有界 timeout；加载时拒绝。frontmatter 引号须成对且至少两个字符；发现目录拒反斜杠别名和控制字符。网页只接受 UTF-8（可省略 charset），其他声明及非法 UTF-8 拒绝；固定来源由可信宿主授权。
- 配置选中摘要 resolvedSha256 覆盖选中后有效配置，不含未选模型 catalog；未选 catalog 变化不会改变当前配置摘要。base 为保留默认路由。bundle 仅在显式声明时替换 enabledTools/prompts，policy 必须合法且多包一致。默认配置可缺，显式 --config 必须存在。CLI 选项值不能吞后续 --flag，`--name=--literal` 提供字面量，`--` 后均作正文。
- stdio SDK 1.0 保留字符串 id；错误信封尽量保留已合法解析的 id。session.close 只释放桥接句柄，不取消/删除宿主持久任务；session.open 丢响应的草稿句柄可重启桥接释放，不自动重放输入。客户端不淘汰已知过期 id：最多4096历史（既有至多32在途可增加少量尾项），达到上限拒新调用并要求对账/重开；收到已知迟到响应后释放墓碑，真正未知/重复 id 仍 fail closed。close 只处理自有 child，阶段超时后升级 kill；不能确认则报告 PROCESS_CLEANUP_UNKNOWN。
- 精确补丁仅 create/update，无 fuzz、rename、binary、delete、执行位变更。按 hunk 计数消费正文；无 git 标记且同路径 a/foo 保留真实目录。diff --git 使用配对 a/b 前缀；无标记时只剥离精确对应的 a/foo→b/foo。git new file mode 只支持100644。补丁传输的 CRLF 归一化后解析；现有文件沿用 LocalWorkspace 的 CRLF/BOM 保留逻辑，新建默认 LF；无末尾换行与其 marker 明确拒绝，使用 read/edit 保持精确字节，不声称完整 git patch 兼容。展示型 UnifiedDiff 记录行尾差异，但不保证其全部格式可直接 apply。

FakeLlm 只适合串行脚本；并发按实际锁获取顺序消费，无调用发起时间公平保证。requests 仅包含通过取消且有脚本响应的受理调用，不能用来审计所有发送尝试。内存/AlternateStorage 测试替身不认证生产磁盘耐久。
