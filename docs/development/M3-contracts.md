# M3 命令、审批、预算与运行控制契约

日期：2026-10-14。公共 API 1.3；JSONL 物理格式与事件 schemaVersion 仍为 1。新增 harness-process、harness-policy，Agent Loop 仍只依赖公共 API。

## 命令与同一执行环境

CommandRequest 包含 callId、mode、完整 argv、相对 cwd、timeout。LocalProcessRunner 复用 LocalWorkspace.commandDirectory 的路径验证；WorkspaceToolsPlugin 的三参数构造注入同一个 ProcessRunner，ExecutionEnvironment.files/processes 因而共享根目录。旧构造仍供无进程环境使用；ProcessToolsPlugin 注册服务与 execute_command，停止时撤销工具并关闭服务。

| mode | 行为 |
|---|---|
| ARGV（默认） | ProcessBuilder 原样传参数；空格、中文、& 等不变成链式命令。Windows .cmd/.bat 与常见 Shell 解释器拒绝隐式执行 |
| CMD | Windows 系统 cmd.exe /d /s /c；argv 必须是一项完整 Shell 文本。mvnw.cmd/mvn.cmd 必须显式走此模式 |
| POWERSHELL | Windows 系统 PowerShell -NoLogo -NoProfile -NonInteractive -Command；一项 Shell 文本 |
| SH | POSIX /bin/sh -c；Windows 拒绝，不隐式切到 WSL |

**决策：** Windows 默认原生执行，WSL 不自动选择。调用方负责 Shell 文本的转义与审核；库不把 arbitrary argv 拼成 Shell。Windows Job Object 本阶段不引入 JNI/JNA/本地二进制，采用 ProcessHandle 追踪并明确降级结果。未来需要更强进程归属保证时新增执行环境提供方。

ProcessLimits 是宿主配置：默认超时 2 分钟、最多 2 个同时执行的顶层命令、累计追踪进程身份上限 64 个、stdout/stderr 各保留 64 KiB；允许配置范围分别为 ≤1 小时、1～16、1～256、128 B～1 MiB。模型 timeoutMs 只能缩短宿主超时。并发满时 fail-fast；观察树越限时停止命令，**累计观察上限用于限制追踪内存并检测后终止，不是 OS 级禁止 fork 的配额，也不是仅统计同时存活进程**。argv ≤256 项/32,768 字符，stdin 立即 EOF，不支持交互式终端。

两个有界收集器同时消费 stdout/stderr，达到保留上限后继续消费并丢弃，避免管道阻塞。停止后只作有界尾部消费，等待收集线程结束并关闭管道；输出明确 truncated。编码可通过 ProcessLimits.outputEncoding 配置，默认 JDK native.encoding（本机 GBK），不假定 Windows 子进程全是 UTF-8。输出截断时额外丢弃已配置秘密最大长度的尾部及不完整行，避免只保存秘密前缀。

每个结果保留 exitCode、stdout、stderr、durationMs、timedOut、cancelled、truncated、cleanupStatus、observedClean、trackedPids、residualPids、limitations、完整 argv/mode/cwd 与 environment 摘要。未启动为 not_started；非零退出为 FAILED/NONZERO_EXIT，成功退出为 SUCCESS。输出收集异常不伪称成功。模型摘要只保留各流前 1,024 字符；配置 ArtifactStore 的 ProcessToolsPlugin 将长输出先保存，再给 stdoutArtifact/stderrArtifact 及 read_artifact 引用。审计 value 仍保留配置上限内的输出；截掉的原始字节不保留。

环境先 clear，再注入宿主授权 Map。buildEnvironment 仅选择 PATH、SystemRoot/WINDIR、JAVA_HOME、TEMP/TMP、HOME/USERPROFILE，不复制 API key、MAVEN_OPTS、JAVA_TOOL_OPTIONS、GIT_* 等任意变量。事件只记录变量名与 values=omitted。调用方应向命令服务、管线、SessionWriter、产物提供相同的秘密配置；不声称自动发现所有秘密。允许的宿主变量仍可能携带路径等信息，本地命令只适用于受信任项目。

## 清理与不确定副作用

从启动开始每约 10 ms 采样进程树，用 ProcessHandle 身份保留已观察子进程，避免仅按 PID 重用身份。命令结束、超时、取消、服务关闭均清理已追踪进程；反复请求强制终止并确认 isAlive，清理等待约 2 秒。超时包含命令启动等待；底层 ProcessBuilder/OS 调用及树枚举仍受宿主系统调度影响，不是硬实时中断。

- observedClean=true 表示追踪集合无存活进程、收集器已结束且枚举未失败。
- cleanupStatus=partial 即便 observedClean=true：快速脱离、短暂父进程生成的未观察子进程无法完整枚举，也不能保证终止。此时 residualPids=[] **仅代表已知集合无残留**。
- 已知残留、枚举失败或收集器未结束为 cleanupStatus=failed/UNKNOWN，并报告已知 residualPids。不得称整个任意 Shell 进程树已 clean；没有使用全系统 PID 广撒网终止。
- CANCELLED/TIMEOUT/进程数越限等停止结果保留 sideEffectUncertain=true；即使已观察进程退出，也不代表项目修改已回滚。Loop 在超时/未知结果后停止，在下轮模型请求前拒绝未核实结果。用户取消和总时间预算仍分别报告 CANCELLED/LIMIT_REACHED。正常非零退出可把编译错误送回模型修复，不自动重复执行停止的命令。

进程观察语义参考 [Oracle ProcessHandle](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ProcessHandle.html)，Job Object 的机制及 breakaway 边界参考 [Microsoft Job Objects](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects)。这里的降级选择是本项目决策，不把 Java 快照机制描述成 Job Object 等价保证。

## 策略与审批

DefaultPolicyEngine 对相对 path/cwd 的越权与 .git/.jharness/.env 系列秘密路径 DENY；只读工具 ALLOW，写工具及一般命令 ASK。第一版唯一默认自动批准的命令模板是完整 ARGV `git --no-optional-locks -c core.fsmonitor=false status --short`、cwd="."；模板精确匹配参数序列、mode、cwd，任何后缀、Shell 组合或额外字段不自动批准。Maven 会执行项目定义的代码，默认 ASK。部署可显式注入其他完整 TrustedCommand 模板，自行承担信任范围。

InteractiveApprovalService 将不可变完整调用、目标路径、潜在影响与一次性 promptId 暴露给 UI；UI 用 answer 提交决定、原因和决定者。等待每 20 ms 检查取消，最多配置 10 分钟；超时、关闭默认拒绝，已回答/撤销 ID 不能再次批准。ApprovalService.rejectAll 仍是无交互入口的默认实现。M4 对接终端 UI，不在库内部另开无法取消的 stdin 读取线程。

tool.requested → 策略 → approval.requested（完整参数/影响等级）→ approval.decided → tool.intent → DURABLE_FLUSH → tool.started → 主体 → tool.completed。审批等待取消也有拒绝/撤销事件；拒绝不创建意图、不进主体。执行包装器仍不能变更批准参数/身份或重复进入主体。损坏存储写不出最终结果时走独立诊断，不虚构完成日志。

## 预算

步数与单次运行总时间预算沿用 M1，时间取消传播到审批、模型和进程服务。新增 Budget.maxApiCostUsd 可选上限和 ApiCostGuard：对最终 prepared 请求先取得宿主配置的 USD 请求上界，再检查剩余额度；超额在发送前 LIMIT_REACHED/COST_LIMIT。

budget.reserved 持久记录 requestBound/reserved（十进制字符串）；模型请求前检查点同时提交此记录。即使请求失败或网络结果不明，预留额度也不返还；turn.ended.reservedApiCostUsd 是**保守预留量，不是服务商账单**。预算按单次运行/turn，不跨运行自动复用。

ConfiguredTokenCostGuard 要求明确模型、已验证输入 token 上界、输出上界与每 token 价格；最终请求必须包含匹配模型和 max_tokens，n 必须为 1。配置输入上界应覆盖该模型全部可计费输入容量，输出限制与价格应适用于实际服务商及当前计费规则，不适用于未经核验的隐藏推理费用/额外计费项。库不查询或猜测价格；默认 accounting unavailable，启用费用上限却缺少可靠上界时，在发送前 COST_BUDGET_UNAVAILABLE。此保证取决于宿主上界及价格配置真实有效，不宣称能约束服务商独立账单。

## runId 与本机取消

每次 LocalRunControl 生成 UUID runId，专用状态目录内保存 `<runId>.run`：version、runId、pid、host=127.0.0.1、临时 port、RUNNING/EXITED、随机 256-bit token。状态 force 后同目录原子替换；Windows 仅 owner ACL，POSIX 700 目录/600 文件，权限不支持时拒绝启动。含其他文件的普通目录不会被改造成状态目录；.jharness 已 Git 忽略。

控制服务仅监听 127.0.0.1，带 token 的 CANCEL 请求返回 REQUESTED；不接受客户端 PID 杀进程。ControlledAgentRun 持有端点并把 token 合并进 Loop，通过 run.started/run.ended 记录 runId；凭据不入事件。关闭后状态保留 EXITED、清空 token。正常生命周期应使用 try-with-resources；进程被硬杀的 RUNNING 状态在下次控制请求中表现为不可达，不自动杀同号 PID。

| CancelResult | 退出码 | 语义 |
|---|---|---|
| REQUESTED | 0 | 已认证并接收取消；不是清理完成确认，应查看运行的终态及 cleanupStatus |
| NOT_FOUND | 2 | 状态目录不存在该 runId |
| ALREADY_EXITED | 3 | 记录已退出，幂等无动作 |
| UNREACHABLE | 4 | 本机端点不可达/状态读取 I/O 失败，可能是崩溃后的过期记录 |
| INVALID_STATE | 5 | 标识、版本、端口、凭据格式或认证不合法 |

discover 最多列举 1,000 个状态 ID，包括已退出记录；保留/轮转由后续存储/CLI 管理。CancelMain 提供跨 JVM 可执行入口，Windows 构建后使用 `scripts/jharness-cancel.ps1 -RunId <id> -StateDirectory <专用目录>`。完整 `jharness cancel --run` 命令解析及默认全局状态目录在 M4 接入，本阶段已实现并测试其底层发现/控制/返回码通路。

## dry-run

LocalPlanPreviewer 仅读取明确 write_file/edit_file 目标，返回 path、beforeVersion、Diff 估计、truncated、executed=false。版本与匹配数冲突拒绝；预览期间版本变化拒绝。预览不替代真正执行时的版本守护、策略、审批和检查点。UTF-8 BOM/换行保留规则仍由真实文件服务提交，Diff 是文本估计；长 Diff 最多 8,192 字符并标记截断。

所有命令和未支持工具返回 previewable=false、executed=false、明确“无法预览”，包括 Shell 重定向与子进程调用，绝不把无法推断解释为“无变更”。M4 将此服务接入 --dry-run 和计划展示。


R1 定点补验修订（2026-10-22）：交互审批的及时性以共同 pending 监视器内接受回答的时刻为准。已在截止前接受的回答，不因等待者延迟读取跨过截止而变为超时；到期与回答只结算一次。取消和关闭在等待者采用结果前仍优先，均撤销待决提示。该规则不授予额外工具权限。
