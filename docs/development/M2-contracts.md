# M2 工作区、文件工具与实盘检查点契约

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](current-state.md)及[全阶段审计](../testing/M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

日期：2026-10-14。公共 API 版本：1.2；JSONL 物理格式及 SessionEvent schemaVersion 均为 1，两者不随 API 版本自动迁移。

## 工作区与执行世界

LocalWorkspace 实现 WorkspaceFiles/FileSystemView，工具只调用注入的文件服务。WorkspaceToolsPlugin 在当前作用域注册文件服务、local ExecutionEnvironment 和工具，停止时撤销贡献。文件根用 ExecPath(".") 表达，工具不获得宿主 Path。

路径接受相对路径和中文/空格文件名；拒绝 .. 分量、绝对/UNC/驱动器路径、ADS 冒号、控制字符、Windows 保留设备名和尾部点/空格。拒绝符号链接、junction 和不支持的特殊文件；路径各祖先在访问和提交前检查，目录真实路径必须与授权路径一致。显式访问 .git/.jharness 元数据目录拒绝，遍历不会进入这些目录、链接及默认排除目录。

边界保证针对受信任宿主上正常存在的路径。Java NIO 在 Windows 上不提供跨祖先解析和替换的完整安全事务；恶意进程在检查间隙替换祖先或目标的竞态不属于 OS 隔离保证。文件版本检查与原子替换也不是 OS 级 compare-and-swap。新增沙箱时继续使用同一执行环境契约验证边界。

## 文件读取、过滤与上限

| 能力 | 契约 |
|---|---|
| list_directory | depth 1～16，limit 1～5,000；返回相对路径和类型，超条目上限标记 truncated |
| read_file | 默认 200 行；每页最多 2,000 行/256 Ki 字符；返回行号、版本、nextLine、truncated。超长行/宽范围可能截断，不能把部分页面视作全文 |
| search_files | glob 支持 *、?、**、**/；固定最大深度 16、最多 5,000 条结果；上限不是全盘搜索 |
| grep | 字面模式，不执行用户 regex；有界扫描文本文件，返回行号/片段及 truncated；跳过二进制或非法 UTF-8 |
| 文本与版本 | 整文件读写上限 1 MiB，流式哈希/扫描上限 32 MiB；版本为 SHA-256，缺失文件版本为 MISSING |

常用 .gitignore 规则包括注释、路径/目录、glob 和顺序否定规则；不是完整 Git wildmatch 兼容实现。默认排除 .git/.jharness/target/node_modules。过滤用于遍历检索；授权目录内的显式 target 等文件读取可进行，元数据目录始终拒绝。

当前默认 UTF-8，非法编码和二进制不作为文本覆盖；常见 UTF-8 BOM、统一 CRLF 风格保留。混合换行文件的精确匹配可能需要显式提供对应文本，其他编码转换后续另行定义。

## 受保护修改与 Diff

- write_file 默认不覆盖已存在文件；覆盖须 overwrite=true 并提供 expectedVersion，或使用该文件服务先前读取的版本。未读取或版本变化明确冲突。
- edit_file 使用非空 oldText 与正 expectedOccurrences；匹配次数必须完全一致。编辑读取不更新早先的版本守护，从而不能把用户介入误当作新的编辑依据。
- 所有写入先经现有策略、审批、完整意图持久化，再进入文件服务。文件服务写同目录唯一临时文件并 force(true)，提交前再查路径、取消和版本。
- 已有文件必须 ATOMIC_MOVE + REPLACE_EXISTING；不支持时拒绝，不降级成可能损坏原文件的覆盖。新建使用同目录不覆盖的 move；必要父目录在受保护通路内创建。
- 失败或取消会同步结束并清理临时文件，没有后台文件任务继续写。操作超时/取消是协作式检查，无法硬中断正在等待的底层文件系统调用；主体进入后的不确定错误继续按 UNKNOWN 处理。提交后的校验失败明确 UNKNOWN_SIDE_EFFECT，须后续读取/版本核实。
- FileChange 包含路径、beforeVersion、afterVersion 和统一 Diff；最终工具事件保存版本证据。Diff 使用线性内存的整段替换 hunk，避免大文件 LCS 的平方内存；不追求最短差异。
- git_diff 在同一根目录执行固定的只读 Git diff，支持 working/staged，禁用 pager、external diff、textconv 和 fsmonitor；不执行模型提供的 Shell 字符串。不含 Git 的项目仍可使用文件级 Diff，git_diff 明确返回不可用。

**M2 快照决策：** 默认不自动创建 Git 提交/索引快照；采用版本守护、同目录替换和可追溯 Diff。可恢复快照仍是可选产品能力，不把这些机制宣称为完整备份。apply_patch 按 FR-07.6 的 V1 后半期安排在 M4，届时需定义多文件冲突与部分完成语义。

## 产物引用

超过 8 Ki 字符的文本/Diff 或结构化结果会保存为会话文本产物，模型得到预览、artifactRef、摘要校验及 read_artifact 指引。产物已 force 后才交付引用；作用域、UUID 与 SHA-256 验证阻止任意路径读取和篡改；重新创建服务后仍可读取。

当前单产物上限 4 MiB；读取按字符 offset/limit，工具单页上限 8 Ki 字符。产物存储应配置在工作区外或受保护的 .jharness 内。扫描/产物大小超限明确失败，不无限读入内存。内部日志/产物持久化属于系统记录，不表示只读工具可以修改项目文件。

## JSONL 物理契约

- JsonlSessionRepository 使用独立会话 .lock 的 FileChannel.tryLock，拥有权保持到 writer.close。冲突 fail-fast，两个 JVM 不能同时使用合规 writer 追加同一会话。
- 第一行是 format/version/sessionId header，后续是完整 JSON 事件行；单写者顺序写完一行并 force(true)，append 只有确认后才返回。force 或写入异常使 writer poisoned，后续追加/flush 拒绝，独立诊断通道记录 CHECKPOINT_FAILED。
- 半行从不被当作事件；默认 REJECT。REPAIR_TAIL 只有在取得独占写锁后截到最后有效记录并 force，且显式报告。中段损坏、重复 ID、跨会话事件及未来版本不作尾部修复。
- 高版本日志拒绝事件解析与 writer 打开；inspect 仅报告版本和只读状态。历史物理格式版本需显式迁移，不猜测转换；完整迁移工具属于 M5。
- 相同 JVM 的读取/追加通过同一文件 gate 保持快照完整。其他进程读到尚未完成的尾部时可以报告 TAIL_DAMAGED，应等待写入者完成后重读，不能抢占修复。
- 当前上限：单记录 8 MiB，日志 32 MiB/10,000 事件；单记录 100,000 JSON token，完整读取最多 250,000 token。写入前同时校验容量，避免已确认写入的数据无法在当前限制下读取。过量/过深记录在对象物化前拒绝。

存储使用本地文件系统提供的 force/锁/同目录操作语义，参考 [FileChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html) 与 [Files](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html)。测试覆盖真实进程崩溃，未模拟设备断电或不可靠网络文件系统；不扩张为硬件层保证。

## 未结算副作用与阶段边界

SessionProjection.uncertainCalls 从持久 tool.intent/tool.started 与缺失的 tool.completed 派生未结算副作用。即使只剩意图而不能证明主体进入，也按 UNKNOWN 保守处理；不生成虚构完成事实。DefaultAgentLoop 在新轮发送模型前拒绝继续，等待人工核实流程。

M2 已实现最小实盘检查点与只读诊断。完整恢复 UI、人工核实后的解锁、查询/分叉、日志轮转/迁移属于 M4/M5；通用 ProcessRunner、审批交互、预算与进程树清理属于 M3；真实模型对项目的端到端能力评测属于 M4。
