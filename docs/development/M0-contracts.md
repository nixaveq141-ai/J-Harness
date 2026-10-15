# M0 服务与事件契约

历史契约版本：M0 为 1.0，当前 M1 公共契约已更新为 1.1，详见 [M1 契约说明](M1-contracts.md)。本文件保留 M0 的阶段边界记录，接口源码位于 `harness-api`。

## 插件与注册

- `HarnessPlugin` 的 id 在候选列表内唯一；版本依赖采用精确字符串匹配，API 版本必须匹配当前版本。不支持版本范围时明确拒绝，不能猜测兼容。
- 配置输入为候选插件列表、enabled 集合、按插件 id 索引的 `ConfigView` 和必需服务清单。配置文件解析尚未实现。
- 依赖在任何插件启动前完成检查：缺失/禁用依赖、循环、版本冲突均导致启动失败。相同优先关系按候选列表和依赖声明顺序稳定解析。
- 服务身份是 `(接口 Class, key, PluginScope)`，同身份重复注册明确拒绝；要求唯一服务时多个 key 产生 `AMBIGUOUS_SERVICE`，必需服务检查在容器进入 RUNNING 前完成。
- 容器单次启动，不支持重启/热重载。启动失败调用已进入启动的插件（含失败插件）的 stop，并关闭已拥有资源；其他插件尚未执行的 start 不发生。
- 停止采用依赖逆序；每个插件先 stop，再逆序关闭注册句柄和资源。一个 stop/close 失败不阻断其他资源清理，异常以 `CLEANUP_FAILED` 及 suppressed cause 汇总。
- `Registration.close()` 幂等；旧句柄不能撤销后来同 key 的新注册。`PluginContext` 关闭后拒绝新贡献；资源通过 `own()` 登记，工具注册句柄也必须登记。
- `PluginContext.services()` 限定当前作用域；容器外应用装配层可使用共享 ServiceRegistry 查找明确作用域。插件不是安全沙箱，主动持有宿主对象的恶意插件不在此隔离保证内。

## 事件

| 类型 | 返回/执行语义 | 顺序 | 失败与撤销 |
|---|---|---|---|
| notify | 发布只入队；单独 daemon worker 通知观察者 | 发布入队顺序、注册顺序 | Exception 进入独立诊断，不改变发布方结果；撤销后未进入的回调跳过 |
| serial | 发布方等待监听器完成 | 优先级升序、同级注册顺序 | 首次异常/超时终止当前分发并明确抛出；超时请求中断，监听器须配合 |
| intercept | 同步包装请求、执行能力或结果 | 优先级升序、同级注册顺序；返回逆序展开 | 可直接返回短路；异常传播；被撤销拦截器跳过 |

`StandardExtensionPoints` 定义模型请求前/响应后、工具调用前、工具执行包装、工具结果后五个标准扩展点。工具执行包装的值是 `ToolExecution` 能力，允许插件返回包装器；审批与检查点仍由 M1 工具管线负责，不能由包装器绕过。

监听器中再次 publish/publishSerial 会明确拒绝，避免递归分发和串行等待死锁。通知不会替用户保存 Session；持久事实必须先经 SessionWriter，后广播。`awaitNotifications()` 是测试屏障，只保证调用前已入队通知完成。

每个容器独占事件总线，因此 Agent 间监听器及拦截贡献互不可见。默认诊断只输出 code、eventId、type，不输出 payload 或异常正文。调用方可以提供独立诊断 sink。通知队列的负载配额将在 NFR-04 工具/长任务实现时补充。

## Session 与检查点

- `SessionEvent` 不可变且 payload 深拷贝为 JSON 值；拒绝非 JSON 对象、非有限浮点数。session、turn、step、tool 层级按类型校验所需 ID，禁止用不相干的字段代替层级。
- 已知 Session 事件类型只能用其指定层级；扩展事件显式指定层级。schemaVersion 从 1 开始；内存仓库拒绝未来版本、跨会话事件和重复 eventId。
- 同一内存仓库同一 Session 同时只有一个 writer；并发打开采用 fail-fast。写入和 reader 快照通过同一存储锁同步。关闭 writer 释放拥有权；已关闭 writer 不得追加，旧 close 不得释放新 writer。
- 内存仓库 append/flush 只保证进程内可见性。`SessionWriter` 的物理保证由存储提供方定义，M0 不声明 NFR-12 或 AC-10 的真实持久化分支已通过。
- 基础投影由事件快照重新 fold，派生消息、工具事件历史、审批与任务状态；没有第二份权威消息数组。模型请求准备、历史完整重建、结果投影进模型消息在 M1 实现。
- 默认检查点策略在模型请求前/步骤边界要求 DURABLE_FLUSH；只读工具要求 FLUSH，其他工具要求 DURABLE_FLUSH。MEMORY_ONLY 仅为测试策略。策略给出决策，并不自行持久化或许可执行。
- list 提供内存 Session id 列表；fork 明确返回 UNSUPPORTED，V2 再实现。

## 类型定型与后续边界

PRD 草案中的 EventId/SessionId 在 M0 用字符串表示；工具参数、Schema、结构化值和展示记录以深不可变 JSON Map 表示。`ToolResult` 为不可变 record，保留独立 status、结构化 value、modelContent、display 和 error。工具 Schema 校验属于 M1。文件版本与变化通过 `FileSystemView` 嵌套 record 表达，执行路径保持 `ExecPath`，不暴露宿主 Path。

`AgentLoopFactory` 接收明确的 `AgentLoopDependencies`；M0 用测试实现证明替换能力，不实现产品 Loop。模型、工具注册表、SessionRepository、CheckpointPolicy、ExecutionEnvironment、PolicyEngine 均可通过同一服务注册契约装配，具体提供方按里程碑落地。

`LlmClient.prepare()` 返回 PreparedRequest，但该类型自身不证明已经落盘或发送；M1 必须通过管线履行请求准备、检查点、预算及取消的顺序。Fake LLM 只支持非流式，不支持的 stream 明确返回 UNSUPPORTED。
