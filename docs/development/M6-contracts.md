# M6 长任务、模型与配置组合契约

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](current-state.md)及[全阶段审计](../testing/M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

日期：2026-10-14。公共 API 1.6，JSONL 格式与事件 schema 保持 1。Agent Loop 的执行逻辑保持不变：流式适配由 LlmClient 装饰器承接，压缩由 BEFORE_MODEL 插件承接，终端工具经过既有策略/审批/意图检查点管线。

## SSE 与工具增量

配置 `streaming=true` 表示请求使用流式；所选路线还须显式声明 `streamSupported=true`。不支持时 CLI 显示 MODEL_CAPABILITY_FALLBACK，改用非流式。toolsSupported=false 时去掉工具声明与 tool_choice，Loop 继续拒绝不支持能力的工具响应。thinking 仍仅支持 disabled，图片/reasoning 不新增支持。

StreamHandle.consume 的回调是累计文本展示快照，不包含可执行的工具调用；finalResponse 仅在 consume 成功之后可取，返回一个完整、校验过的 ChatResponse。StreamingLlmClient.complete 将这个最终响应交给现有 Loop；未完成增量不进入 assistant/tool 管线。

HTTP 响应限额覆盖全部 SSE 字节（CLI 默认 2 MiB），逐行严格 UTF-8 解码，支持 CRLF、注释心跳、分片 UTF-8、多 data 行和独立 usage 尾块。仅允许一个 index=0 的 choice；函数调用按 index 组装，最多 64 个，最终索引连续、ID 唯一、名称非空、arguments 为严格 JSON object。必须有有效 finish_reason 和 [DONE]；EOF/不完整 arguments/重复 ID/超量/意外 reasoning 拒绝。流式发送无自动重试，取消和超时覆盖整个 body；等待轮询为 25 ms。

展示保留已知秘密的尾部窗口后再输出，防止跨 delta 的 API key 或环境秘密值泄露；最终文本及 usage 同样脱敏。最终模型响应仍作为一条 assistant.message 落盘，部分展示不构成成功或工具执行事实。断流后的 model.requested 保留为未决请求，沿用 M5 人工核实，不自动重发。

## 上下文压缩

新增可替换 ContextCompactor 服务与 context-compaction 插件。默认不开启自动压缩；`compactContext=true` 后，以 `compactionCharacters` 为触发/目标上限（默认 contextTokens），单位是保守字符估计，和当前 Loop 的 context 检查保持一致，不宣称精确 tokenizer。

ExtractiveContextCompactor 保留系统说明、最近用户目标及最后一个协议组；其他完成的 assistant/tool 组可以整体移除。同一轮长工具历史可压缩，不要求先结束用户任务。多工具调用的全部结果必须齐全，不能拆组；有未解决或孤立的工具协议则拒绝压缩。摘要为有限的角色标注内容摘录，明确说明“部分引用，不作为完成证据”；不是调用外部模型生成的语义摘要。受保护内容/工具声明本身超量时返回 CONTEXT_LIMIT。

每次真正压缩先保存 context.compacted：策略名、移除的原消息索引、摘要、前后字符数和源事件 ID，并 flush。其后仍由 EventRequestJournal 保存最终 prepared request 的 request.context/model.requested。请求前缀独立重建，投影只采用最终请求事实；摘要不替代磁盘执行证据。插件停止撤销拦截器与服务，可提供其他 compactor 实现。

## PTY 与后台任务

新增 TerminalSession 的 start/status/log/write/resize/stop/states/stopAll 契约；未实现的提供方明确 UNSUPPORTED_TERMINAL。LocalTerminalSession 使用 [pty4j 0.13.12](https://central.sonatype.com/artifact/org.jetbrains.pty4j/pty4j/0.13.12)，Windows 请求 ConPTY，上游可退回 WinPty；POSIX 使用上游本地 PTY。[上游 API](https://github.com/JetBrains/pty4j/blob/master/src/com/pty4j/PtyProcessBuilder.java)

终端 start 的 argv、显式 Shell mode、cwd 和 timeout 与普通命令一致，共用 LocalWorkspace.commandDirectory 和 nativeArgv 边界；不隐式使用 WSL，不将模型 key 传给命令环境。`pty=true` 创建真实 PTY，`pty=false` 创建合并 stdout/stderr 的后台进程。终端 input 是 UTF-8 原始输入；Windows 交互输入通常用 `\r`，POSIX 用 `\n`。

模型工具为 terminal_start/status/log/write/resize/stop。start/write/resize/stop 为 SYSTEM_IMPACT，经过审批与 durable intent；status/log 为只读。Bundle 隐藏的工具不能通过猜名字调用。dry-run 沿既有预览管线，不进入终端主体；只读策略拒绝所有终端变更。

任务归调用 CLI/REPL 拥有，跨多步与 REPL 多轮持久；CLI 正常退出关闭全部任务，不作为脱离宿主的常驻守护服务。最多 4 个活跃任务、32 个保留任务，每任务最长 1 小时；树观察上限 128。日志保留 256 KiB 字符尾窗口（另保留秘密尾），分页最大 65,536 字符。输出过长会滚动丢弃旧内容，旧 offset 明确 TERMINAL_LOG_ROTATED；不声称保留完整输出。

stdin 由单写槽及独立虚拟线程写入，最多等待 5 秒；等待期间可取消，不持有阻塞停止流程的锁。输入前取消不写入；输入途中取消/超时/I/O 不确定会停止任务并返回 TERMINAL_INPUT_UNKNOWN，不允许盲目重发。

停止、超时及正常退出会清理观察到的进程，清理等待有界；状态包括 RUNNING/EXITED/STOPPED/TIMED_OUT/PROCESS_LIMIT/UNKNOWN，保留残留 PID、outputFailed/outputDrained 与 sampled observation。仍没有 Windows Job Object 或 OS 沙箱；快速脱离且未被采样的后代无法保证归属，cleanupStatus 如实为 partial。原生 PTY 加载失败明确 PTY_UNAVAILABLE，不伪造成普通管道 PTY。

结束任务的尾部日志保存为当前会话产物，视图记录 SHA/ID。run 记录 terminal.snapshot；宿主正常关闭再保存终态，冷查询和 read_artifact 可读。取消/时限到达停止本宿主任务，shutdown hook 也请求停止；强制结束 JVM 不保证 hook 执行。冷日志中的 RUNNING/UNKNOWN 任务阻止 resume，不自动重建终端或仅凭旧 PID 杀进程。人工核实命令：

```powershell
.\jharness.cmd sessions verify-terminal --session SESSION_ID --terminal-id TASK_ID --reason '已核对进程与工作区实际状态'
```

terminal.verified 仅记录操作者事实，不启动、重试或杀进程。分叉继承这些恢复约束；正常结束产物沿用 M5 的复制与冷读取规则。

## 模型路由与元数据

ModelRouter/LazyModelRouter 提供惰性多适配器注册与选择；任务路线冻结，未选适配器不创建、不要求 key。CLI 自带路线均为 OpenAI-compatible，公共工厂可注入其他 LlmClient；不宣称内置其他供应商协议。所选路线能力决定工具/流式降级，配置价格与预算仍由当前模型的 CostGuard 核验。

每次模型请求产生 model.observed，记录 sourceEvent、elapsedMs、requestCount、model、stream、outcome，以及提供方返回的 prompt/completion/total token 数。不记录提示词或响应正文。报告 recordedFacts.models 展示本轮请求元数据，recordedFacts.terminals 展示最新任务快照；成本仍是原 budget/turn 事实的保守上界，不是账单。工具错误继续保留具体统一状态和错误码。

## Profile / Bundle / Preset

[配置示例](../examples/m6-config.json) 使用保留域名占位，需要替换 URL、模型名称和实际能力声明后运行；不要填入 API key。支持：

```powershell
.\jharness.cmd --config docs/examples/m6-config.json --profile inspect doctor
.\jharness.cmd --config CONFIG_JSON --profile coding run '完成任务'
.\jharness.cmd --config CONFIG_JSON --profile coding --model-route text-only run '解释已有文本'
.\jharness.cmd --config CONFIG_JSON --preset long-task --bundle files --bundle editing run '完成任务'
```

models 是命名模型/URL/key 环境引用/能力/价格配置；不允许在模型项里修改策略或工具包。profiles 包含 modelRoute、preset、bundles、settings；presets 包含 modelRoute/settings；bundles 包含 enabledTools/prompts/可选 policy。base 为默认路线保留名。

解析顺序是基础字段→Preset settings→Profile settings→所选模型字段→Bundle 组合；CLI 选择覆盖默认选择。Profile 与 Preset 的路线不同且没有显式 --model-route 时拒绝。多个 Bundle 的工具并集去重、提示词按声明顺序追加；策略冲突拒绝，Bundle 不能将已选择的 read-only 降为 default。Bundle 不是自动批准命令的机制。未知组合、未知字段、非法类型和未注册工具明确拒绝；每个目录最多 64 个命名项，配置文件总体限额仍 64 KiB。

每轮保存 config.selected：Profile/Preset/路线/Bundle 选择、解析后配置 SHA、模型、策略及 streaming 意图；创建会话后的首轮因此可追溯初始组合，续跑也记录新选择。key 只从环境获取，不进入配置、选择事件或遥测。默认 V1/V2 无额外 named 选择时维持原配置行为。

## 平台与范围

CI 配置扩展为 Windows/Linux/macOS，但本机证据仅证明 Windows；远端未运行。没有 Git remote，不能从本地声称远端流水线通过。PTY 本地动态库由上游提供；M6 不新增流式 reasoning、图片、独立常驻终端服务、OS 沙箱或语义摘要模型。相关未来增强需保留当前可重建/不重放/先落盘约束。
