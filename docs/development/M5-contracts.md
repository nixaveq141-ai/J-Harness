# M5 持久化、查询、迁移与恢复契约

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](current-state.md)及[全阶段审计](../testing/M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

日期：2026-10-14。公共 API 1.5；JSONL 物理格式与事件 schemaVersion 保持 1。延续 M2～M4 的 force(true)、单写者、脱敏与先落盘约束；JSONL 是当前正式磁盘实现，SQLite 后置。

## 存储与替换

SessionReader 新增默认 query(SessionQuery)，内存与磁盘实现共享精确事件过滤、包含端点的时间范围和分页语义。单页 1～1,000 条，offset 非负；日志仍限制 32 MiB、10,000 事件、8 MiB 单记录及累计节点预算。目录列表限制 10,000 会话。

SessionCatalog 只依赖 SessionRepository；不建索引，每次扫描有界冷日志，逐个反序列化全部事件后释放，不要求驻留所有会话。会话结果按 ID 排序，支持 workspace/model/时间/tool/最终任务状态组合过滤；时间与 tool 必须在同一个事件上匹配。无过滤时损坏和未来版本以每会话诊断行返回；有过滤时不可解释的日志不能匹配。全文搜索与持久索引属于后续阶段，此处不宣称 FR-24 的全部产品化能力完成。

## CLI 查询与导出

```powershell
.\jharness.cmd sessions list
.\jharness.cmd sessions query --session-workspace 'D:\project' --model fixture --status FAILED
.\jharness.cmd sessions query --session SESSION_ID --type tool.completed --status FAILED --offset 0 --limit 100
.\jharness.cmd sessions query --session SESSION_ID --tool read_file --after 2026-10-14T00:00:00Z --before 2026-10-15T00:00:00Z
.\jharness.cmd sessions export --session SESSION_ID --output NEW_JSONL
.\jharness.cmd sessions artifact --session SESSION_ID --artifact ARTIFACT_ID --offset 0 --limit 65536
```

query 的会话级 model/workspace 过滤与事件级 type 过滤不可混用；未知参数与非法页/时间返回 64。状态在会话查询中表示投影最终状态，在事件查询中匹配事件 payload.status。tool 匹配 payload.tool，例如 tool.requested；不能推断没有该字段的事件属于某工具。查询、导出、核实、迁移均不要求模型 key。

export 保存原始 JSONL 头与所有事件，不改写 ID，不覆盖已有目标，持有源会话写锁以得到稳定快照。导出是事件日志，不打包产物和报告；移动到另一台机器时需一并保留 .jharness/artifacts 与报告目录。失败返回 5，I/O 失败后应检查输出文件是否完整再保留。冷产物读取验证会话日志、作用域、内容 SHA 与分页边界；没有跨会话回退。

## 分叉

```powershell
.\jharness.cmd sessions fork --session SESSION_ID --event EVENT_ID
```

复制指定事件及之前的完整前缀，保留 eventId/turnId/stepId/callId 和 payload，将 sessionId 改为新 UUID 会话，并追加 session.forked，记录 parentSessionId/fromEventId。事件 ID 的唯一性在会话内成立；继承 ID 使 contextEventId、sourceEvents 等引用保持可重建。父子日志独立，后续写拥有权分别获取，父写者活跃时拒绝分叉。源工作区归属不变，不复制工作区文件；已有 delivery 路径仍指向原交付文件。

CLI 在新日志原子发布前复制父会话全部已校验文本产物，保留产物 ID/SHA，新会话冷读取无需父产物回退。产物复制限制 1,000 个、合计 32 MiB；不重写历史模型消息中的引用。分叉前缀中的 UNKNOWN、未完成调用和未决模型请求一并继承，不能通过分叉绕过人工核实。日志先暂存、校验，再原子发布；失败不留下可见的半个会话。产物复制或日志发布失败可能留下未引用的 UUID 产物目录，保留供人工清理。

## 显式迁移与修复

```powershell
.\jharness.cmd sessions migrate --session SESSION_ID
.\jharness.cmd sessions repair-tail --session SESSION_ID
```

迁移只支持显式兼容导入格式：jharness-jsonl header.version=0，其 sessionId、字段和事件 schema 均与格式 1 相同，仅升级头版本；不是任意历史格式猜测，也不声称存在已发布的 v0 数据。格式 1 校验成功返回 ALREADY_CURRENT。其他旧版拒绝；未来格式或未来事件 schema 拒写，返回 6。

维护操作取得与普通写者相同的跨进程独占 .lock。候选日志先强制落盘、完整校验；原始字节保存到唯一 SESSION_ID.backup-UUID.bak 并 force(true)，然后原子替换日志。任何备份或替换失败保留源日志，临时日志清理；不降级为非原子覆盖。备份不自动删除，失败时可能留下未完成备份，应以成功命令返回的备份名为准。

repair-tail 仅丢弃无换行的最终记录或唯一非法末行，保存完整原始备份；有效前缀与 UNKNOWN 不变。中段损坏、重复 ID、会话不匹配、容量限制和未来版本均拒绝，不扩大为“自动修好”。健康日志返回 ALREADY_VALID。inspect 与普通读取不自动修复；中段损坏和头损坏返回 6 并提供诊断码。

## 恢复与人工核实

```powershell
.\jharness.cmd sessions recover --session SESSION_ID
.\jharness.cmd resume --session SESSION_ID '继续目标'
.\jharness.cmd verify --session SESSION_ID --call CALL_ID --outcome NOT_APPLIED --reason '已核对磁盘事实'
.\jharness.cmd sessions verify-request --session SESSION_ID --event MODEL_REQUEST_EVENT_ID --reason '已核对服务商；放弃缺失响应'
```

recover 只读加载、校验、投影，返回 uncertainCalls、unsettledCalls、pendingRequests 与 ready。缺失工具结果、UNKNOWN 或 sideEffectUncertain 要求人工核实。模型请求必须已有 assistant.message 或显式 model.verified；budget.settled 不能代替响应已落盘的证据。仅收到 assistant 工具调用但未进入工具请求的窗口，也可引用原 assistant 事件完成核实。

verify 追加 UNKNOWN 对账和 tool.verified 操作者事实，不把人工核实伪造成原工具 SUCCESS。verify-request 只能核实仍未决的已记录请求，追加 model.verified，记录请求来源、操作者、非空理由及 abandoned_without_replay；不重发、不伪造响应、不退回原费用预留，费用仍以原事实/未知上界为准。重复核实拒绝。

resume 审计通过后，使用指定新目标（省略时使用最近用户目标）开启新 turn，保留历史消息，经既有模型与工具管线执行。它不继续历史步骤，也不重放工具。run --session 与 REPL 使用相同恢复阻断检查；检查先于模型 key 校验和任何模型请求。实际文件写入仍需当前版本依据、审批与 durable intent，恢复不会把旧读取版本当成新授权。

## 保留与边界

默认保留所有日志、备份、产物和报告，不自动归档、删除或 GC。M5 完成阶段要求中的查询/导出/分叉/迁移/恢复；FR-24 的全文搜索、比较、归档界面与可配置保留策略仍属后续产品化范围。本地 Windows 测试证据不能代表远端 CI 或其他平台已通过。

2026-10-22 R2 增补：当前持久化、SDK 自包含输入/初始化、问答暂存与最终持久解析、容量及平台边界以 [R2 补充契约](OCR-R2-contracts-2026-10-22.md) 为准；旧 `userEventId` 分离输入仍兼容读取，新输入正文与关联同一事实发布。最终实测见 [R2 修复记录](OCR-R2-remediation-2026-10-22.md)，不将本条契约说明算作验收通过。
