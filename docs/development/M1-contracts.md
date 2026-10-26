# M1 请求、工具管线与 Agent Runtime 契约

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](current-state.md)及[全阶段审计](../testing/M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

日期：2026-10-13。公共 API 版本：**1.1**。当前固定配置：一个非流式 OpenAI-compatible chat/completions 提供方；产品模型名称由应用配置，不在 Loop 中硬编码。测试模型均为 Fake LLM 或本地 HTTP。本文是 M1 实施决策和测试契约，PRD v1.1 保留为原需求基线。

## 1. 事件与策略顺序决策

FR-10.1 与 FR-06.3 的「事件发布」顺序原文不一致。M1 固定采用更完整的 FR-06.3：

1. 查找当前作用域工具，校验原参数。
2. BEFORE_TOOL 扩展点可改写参数；重新校验，禁止改写工具名和 callId，副作用等级取注册工具的声明。
3. 追加并通知 tool.requested。
4. 经 policy.evaluate 扩展点组合 PolicyEngine，得到 ALLOW/ASK/DENY；ASK 发布 approval.requested 并调用 ApprovalService。
5. 追加 approval.decided（批准值、原因、决定者）。拒绝直接形成 DENIED，主体不进入。
6. 追加 tool.intent（完整参数及副作用等级）并确认检查点。副作用工具强制要求 DURABLE_FLUSH，即使测试 MemoryCheckpointPolicy 也不能绕过。
7. 追加 tool.started，调用 TOOL_EXECUTION 包装后的执行能力。
8. 校验并标准化结果；AFTER_TOOL 可处理输出，但不能改变 callId 或真实状态，成功值再次校验输出 Schema。
9. 脱敏并追加 tool.completed（状态、值、模型内容、展示、错误码、耗时、是否进入主体），由投影生成对应 callId 的 tool 消息。

策略与审批必须先于意图确认和主体；tool.requested 仅描述请求事实，不表示允许执行。意图检查点失败的诊断通过独立通知通道可见，不能宣称该诊断已写入不可写的 Session 介质。

执行终端还会检查：参数与批准意图完全一致、原取消信号有效、工具注册仍是同一实例、主体最多进入一次。包装器不能剥离取消、调用未批准参数、重复执行或未执行却伪造 SUCCESS。内部工具超时实现属于后续工具阶段；M1 支持明确 TIMEOUT 结果，副作用主体进入后的不确定错误形成 UNKNOWN。

## 2. 模型请求的事实来源

- 用户、系统及注入消息先成为 Session 事实；应用须使用 SanitizingSessionWriter 和同一 DataSanitizer。
- DefaultPromptComposer 从当前 SessionProjection 的消息与当前作用域工具声明组成请求，不维护权威聊天缓存。
- BEFORE_MODEL、能力降级、脱敏与 LlmClient.prepare 全部在记录最终请求之前完成。prepare 完成不代表已经发送或满足落盘要求。
- EventRequestJournal 追加 request.context，包含最终 messages/tools/parameters 和准备原因；随后 model.requested 引用该 context 的 eventId。
- 根据 CheckpointPolicy 确认检查点，再仅使用目标 model.requested 截止的日志重建请求，验证与准备结果相等后发送。
- 重建拒绝未来/缺失/不匹配步骤的 context，不读取目标事件之后的消息或配置。日志只支持当前事件 schemaVersion 1；公共 API 1.1 与事件格式版本分别管理。
- request.context 是最终模型可见投影的事件快照，记录扩展点/准备后的有效内容；后续投影在该快照上追加 assistant/tool 事实。历史请求不会被后来上下文变化覆盖。
- 参数中不包含凭据；适配器只在 HTTP Authorization 中使用配置密钥。脱敏前后不能维护两份不一致的「发送请求」与「日志请求」。

流式、摘要压缩、附件与磁盘格式迁移尚未实现，进入 M5/M6 后需要扩展上述回放测试。

## 3. Loop、状态与依赖

默认 Loop 只依赖 harness-api。M0 AgentLoopDependencies 保留；新增 AgentRuntimeServices 明确注入 scope、reader、projector、checkpoints、approvals、pipeline、request journal、sanitizer 和 completion evaluator。create 后每个 Loop 只运行一次，任务工具按模型顺序串行执行。

每轮写 turn.started；每步写 step.started、请求/响应/工具事实与 step.ended；结束写 turn.ended 的明确状态和原因。最大步数、时间及取消合成令牌贯穿模型与工具。错误结果通过工具消息反馈，可由模型在后续步骤修复；格式错误、模型错误、限额、取消等产生明确终止状态。

取消或出错时尚未执行的工具调用会形成显式未执行结果，使调用/结果保持配对。UNKNOWN 不会自动重试，后续 turn 也在发送模型前拒绝续跑，待人工核实能力后续实现。

纯文本终止只表示模型本次没有工具调用。应用必须提供 CompletionEvaluator 依据任务专属执行证据判定目标是否达到；缺乏证据返回 FAILED，不能把工具 SUCCESS 或模型说明自动等同于任务完成。M1 集成夹具使用记录中的 verified 值，M4 应接真实测试退出码及目标验证证据。

## 4. 工具 Schema 与扩展

工具仍采用 M0 的 JSON Map 输入/输出契约。校验器支持 object/array/string/number/integer/boolean/null、properties、required、布尔 additionalProperties、items、enum/const、数值与长度/条目边界、exact-one oneOf。未知关键词、错位约束、冲突边界及过深 Schema 明确拒绝，不静默忽略。description/title 仅作为说明。

Schema 嵌套上限 32；JSON 值快照上限 64，循环对象明确拒绝。数值 enum/const 按 JSON 数值语义比较。模型可见工具声明只包含 name、description、输入 parameters，不泄漏执行函数、输出展示或调度字段。

ToolsPlugin、LlmPlugin、AgentPlugin 分别注册 ToolPipeline、LlmClient、AgentLoopFactory。示例扩展插件只注册 BEFORE_TOOL 与 policy.evaluate 拦截器，完成参数改写和只读免审批；卸载后贡献撤销。作用域和持久性守护不属于可跳过的拦截器分支。

## 5. 模型适配器契约与边界

适配器参考 [DeepSeek 普通工具调用协议](https://api-docs.deepseek.com/guides/tool_calls/) 和 [错误码说明](https://api-docs.deepseek.com/quick_start/error_codes/)。支持范围由本项目本地契约测试验证，不承诺任意兼容提供方均可直接工作。

- baseUrl 仅接受 HTTPS 或 loopback HTTP，无 URL 凭据/查询/fragment，追加 /chat/completions；不跟随重定向。
- prepare 固定配置模型和 stream=false，验证消息/工具结果配对、支持参数和请求大小。complete 重复 prepare 必须保持幂等。
- 请求上限 1 MiB；响应采用有界 BodySubscriber，配置上限 128 B～16 MiB；有界 JSON 解析拒绝重复键与尾随内容。
- 整个响应体读取、可配置 429 退避和重试均在同一超时预算内，并轮询取消。发送前取消不发 HTTP；发送后取消只停止本地等待，服务端是否处理过不能保证。
- 默认重试 0；只在显式配置时重试完整收到的 429，最多 3 次。网络结果不明、5xx、取消与超时均不自动重发；重试为 100/200/400 ms 有限退避。
- wire tool arguments 为 JSON 字符串；内部 ToolInvocation 为不可变 JSON Map。检查重复 callId、未配对结果、非法/截断响应及不支持的内容格式。
- 当前明确不支持流式、图片、thinking/reasoning 内容。能力由配置声明且实现强制保持非流式/文本；不假设未知模型能力。
- 客户端错误仅暴露稳定错误码，不保存响应正文或包含密钥的底层异常。LlmPlugin 拥有客户端资源，停止时关闭 HTTP 客户端。

上下文上限检查采用字符量的保守估计，未实现精确 tokenizer 或费用预算。真实提供方效果、限流实践及协议特殊字段仍需在有授权配置的任务中补充实测。

## 6. 后续阶段约束

SessionWriter 新增 durable() 能力，默认 false；内存仓库、脱敏装饰器不会自行获得物理持久性。当前测试中的 durable=true writer 仅为故障/顺序模拟，没有真实文件或命令副作用。JSONL、跨进程拥有权、flush 实盘确认、损坏处理与 UNKNOWN 人工核实仍属于 M2～M5。

目录边界、受保护文件修改、Host Shell 高风险管理、OS 级取消/进程清理、产品审批交互、最终交付报告与 CLI 不在 M1 完成范围。新增这些能力时必须继续回归 AC-03、11～13 和当前失败/取消测试。
