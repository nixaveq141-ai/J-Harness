# M4 CLI、诊断、审批与报告契约

日期：2026-10-14。公共 API 1.4，JSONL 物理格式和事件 schemaVersion 仍为 1。harness-cli 负责静态装配与入口；Agent Loop 继续只依赖公共 API，并通过 AgentLoopFactory 服务解析实现。

## 入口与配置

构建后的 shaded JAR 可直接由 Java 21 运行；根目录提供 jharness.cmd 和 jharness 启动器。支持 run、REPL、doctor、config set、diff、report、sessions list/inspect、verify、cancel。参数严格校验，不提供自动批准全部请求的开关。重复 --require-read 与 --expect-file 合并为验收条件。

JSON 配置严格拒绝未知字段、重复键、越界值和内联 API key。apiKeyEnv 指向宿主环境变量；已知秘密值不能通过 config set 写进其他字段。模型适配器、管线、日志、产物和进程输出共享脱敏规则，包括模型返回的 callId。CLI 管道使用 UTF-8，真实控制台使用控制台字符集。

doctor 检查 Java/JAVA_HOME、Git 实际可执行性、Maven Wrapper 文件或全局 Maven 可执行文件、模型与预算配置、环境变量是否存在，以及惰性插件装配。它不请求模型，不下载 Maven，也不证明远端认证成功或 Maven 项目可构建。

## 交互、取消与恢复

审批呈现完整参数、目标、影响和一次性 promptId；仅明确 y 同意，EOF、关闭、超时均拒绝。:cancel 可中断审批或运行，其他排队输入保留给下一次交互。stdin 由一个有界队列和守护读取线程管理；阻塞的 System.in 读取不会阻止 JVM 退出。

每次 run 使用持久 JSONL、专用状态目录、认证 loopback 取消端点。shutdown hook 请求取消并有界等待。进程清理仍遵循 M3 的 ProcessHandle 采样限制：观察集合退出不等于 OS 级进程归属保证，没有 Windows Job Object；报告明确 partial/残留证据。

副作用 intent/start 未获得确定结果，或结果标记 sideEffectUncertain，均阻止自动续跑。未结算模型请求预算也阻止续跑。verify 要求人工填写 callId、APPLIED/NOT_APPLIED/PARTIAL 与依据；缺失结果只补 UNKNOWN 对账记录，再追加 tool.verified 操作者事实，不执行原工具。实际 CLI 子进程在写入之后、结果落盘之前强制崩溃的测试覆盖冷启动检查、核实和续跑，原副作用主体只进入一次。

未来版本、损坏尾行通过 inspect 只读诊断，返回 6；CLI 不自动截断、迁移或修复日志。完整迁移、查询和分叉留给 M5。

## 验收与报告

完成判定使用当前 turn.started ID，不能复用上一轮工具证据。--require-read 要求成功读取对应路径；--expect-file 要求本轮成功写入、前后版本不同且当前磁盘 SHA 与结果版本一致。--verify-argv 指定完整命令数组，--verify-mode 指定显式执行模式，--verify-cwd 默认 .；最后一次匹配命令必须 exitCode=0，位于最后一次修改之后，且没有更晚进入的其他命令。没有明确验收条件时模型自述不能触发成功。

报告从事件重建，区分 recordedFacts、modelExplanation、unexecutedValidation 和 unresolvedProblems。失败后修复的历史结果保留为 issues，但不伪称仍未解决。文件事实记录路径、前后 SHA、Diff/产物引用，命令事实记录完整 argv、cwd、退出码、输出和清理证据，并关联 callId/eventId。无变化写入不计为文件修改。

JSON 与 Markdown 写入 .jharness/reports，原子替换并 force；随后 delivery.registered 记录路径、SHA 和来源事件。报告是交付事件之前的快照，避免自包含哈希循环。模型说明始终不作为事实证据。

| run 返回码 | 含义 |
|---|---|
| 0 | 声明的证据条件通过 |
| 2 | 失败或未验证 |
| 3 | 已取消 |
| 4 | 达到预算或 dry-run 计划 |
| 5 | 配置、装配或 I/O 错误 |
| 6 | 需要人工核实或只读诊断 |
| 64 | 参数错误 |

cancel 独立返回码：0 已请求、2 不存在、3 已退出、4 不可达、5 无效请求。

## 预算、计划和补丁

预算支持 USD/CNY。每次发送前按可信输入 token 上界与输出 cap 预留；可靠 usage 返回后按配置的保守单价结算。缺失 usage 保留全部预留，超过已验证 token cap 明确失败；未确认请求仍占用预算。既有 ApiCostGuard 默认不退款语义保留。这些值是费用上界，不能替代服务商账单。

DeepSeek 示例明确 thinking=disabled；不实现 reasoning/流式/多模态。价格是外部配置，使用者需按当前服务商资料维护。实时验收驱动保存跨调用累计账本，包含中断记录，累计不超过用户授权的 18 元。

--dry-run 的写入与命令仅产生计划，不进入 intent/started/工具主体；只读工具可实际执行。任意 Shell 明确标注无法预览。离线 --plan JSON 无需模型/key；在创建会话与产物目录前处理。

例如 plan.json 内容为 `{"calls":[{"tool":"write_file","arguments":{"path":"new.txt","content":"preview"}}]}`，执行 `jharness run "检查计划" --dry-run --plan plan.json`。该命令返回 4，不创建 new.txt。

apply_patch 支持有界、精确上下文 unified diff 的创建和更新，最多 64 文件/1 MiB 补丁；拒绝删除、重命名、二进制、模糊匹配和不支持的无末尾换行格式。所有路径、规范化重复目标、版本、上下文与总快照容量先验证；每文件原子提交。多文件不是事务，后续提交失败返回已知 changes/pendingPaths 与 sideEffectUncertain，停止并要求核实。

## 验收边界

Windows 11/Java 21 本地回归与两个受控 Maven 项目的真实模型重复验收已保存证据。GitHub Actions 的 Windows/Linux 工作流已配置但远端未运行；Linux/macOS 实机、任意项目成功率和不可信项目沙箱不属于这份本地通过结论。V1 发布仍需补齐远端 CI 证据。
