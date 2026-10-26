# M4 本地验收记录

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](../development/current-state.md)及[全阶段审计](M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

2026-10-21 接续：本页的付费驱动命令及 oracle 是历史记录。R1 已关闭 `live-evaluate.py` 的执行、审批及 oracle 入口；`--audit-only` 仅有界读取并向 stdout 输出历史身份/费用检查，既不加载生成代码，也不修改账本或本页结果。缺失/损坏成本记 UNKNOWN，不释放新调用额度。源码过滤和最小环境不是不可信 Java 沙箱，重新启用须先完成隔离与计费设计和新的付费授权。

日期：2026-10-14。Windows 11、Java 21、公共 API 1.4。M4 本地开发与测试完成；远端 Windows/Linux CI 尚未运行，完整 V1 发布确认仍需该证据。

## 自动化与打包

执行 `.\mvnw.cmd -B spotless:apply clean verify`，BUILD SUCCESS；**156 项测试，0 失败、0 错误、0 跳过**。包含 M0～M3 的 130 项回归及 M4 新增 26 项，并通过编译警告检查、格式检查和 shaded CLI 打包。

[M4-results.json](M4-results.json) 保存测试类/用例、平台、命令、日志 SHA、源文件 SHA 和远端 CI 状态。代码尚未提交，测试版本以 SHA 标识；本机详细日志为 `.build-cache/M4-verify.log` 与模块 Surefire XML。

新增测试覆盖精确多文件补丁及部分提交、CLI 参数/配置/费用/损坏与未来版本、事实报告、当前轮完成条件、旧构建结果失效、文件并发变更、终端审批/取消、离线与模型 dry-run、惰性 doctor、插件替换、REPL、跨进程取消、真实 CLI 崩溃核实/冷恢复，以及 callId 秘密脱敏。

另行验证 `.\jharness.cmd --version` 返回 API 1.4；使用 DeepSeek 示例配置运行 doctor，Java/JAVA_HOME/Git/key 存在/模型配置/Wrapper 文件/插件装配通过，ready=true、exitCode=0。doctor 不发送模型请求，不证明远端认证成功。

## 真实模型验收

用户授权 DeepSeek / deepseek-flash / https://api.deepseek.com，环境变量 DEEPSEEK_API_KEY，累计费用上限 CNY 18.00。只发送隔离 Maven 示例、固定目标和工具结果，不发送整个 J-Harness 仓库。

固定配置：thinking disabled、contextTokens=65536、maxOutputTokens=4096、inputTokenCeiling=1000000、maxSteps=20、durationSeconds=600、requestTimeoutSeconds=90，无自动请求重试。按 2026-10-14 [官方价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/) 的峰时输入 cache-miss CNY 2/百万 token、输出 CNY 8/百万 token 计保守上界。逐请求保存 usage，未确认请求保留全部预留；价格和别名变化时须重新核对。

驱动：`scripts/live-evaluate.py --limit-cny 18.00 --repetitions 2`。只批准指定夹具文件编辑及完全匹配 Maven 命令；不批准任意 Shell 或其他文件。模型生成修改另经独立宿主 Java 反射检查。

最终构建 SHA：`ce17851b6183df7b6c422dbe939a0ed433a1e685d25281d52f5eae30f3e9ad07`。

| 最终构建任务 | 重复 | 请求数 | Maven 退出码 | 独立检查退出码 | 费用上界 CNY |
|---|---|---|---|---|---|
| calculator：探查、实现 multiply、补正数/负数/零测试 | 1 | 6 | 0 | 0 | 0.050076 |
| broken-project：先失败、修复语法、重新测试 | 1 | 9 | 1 → 0 | 0 | 0.070938 |
| calculator：同一任务、新夹具 | 2 | 7 | 0 | 0 | 0.059862 |
| broken-project：同一任务、新夹具 | 2 | 9 | 1 → 0 | 0 | 0.068912 |

四次 CLI 均 exitCode=0、COMPLETED、EXPLICIT_EVIDENCE_CRITERIA_PASSED；验证命令均为 CMD / `call mvnw.cmd -B -o test` / cwd `.`。独立检查确认 add(2,3)=5，功能任务另确认 multiply 返回类型和正数、负数、零行为。原始 broken-project 示例保持故意损坏，修复只发生在副本中。

此前构建四次也成功，费用上界 0.244800；最终四次为 0.249788；**全部八次累计上界 CNY 0.494588**。累计账本未重置；这些值不是服务商账单。[M4-live-results.json](M4-live-results.json) 保存八次模型配置、任务/JAR/源码/日志 SHA、usage、命令退出码、报告路径和独立 oracle。本机 `.build-cache/M4-live/ledger.json` 保留累计历史，隔离目录和原始日志不进入 Git。

这些固定任务的重复成功不能推导任意项目成功率。

## AC / NFR 证据索引

| 范围 | 本地证据 |
|---|---|
| AC-01/02/04 探查、功能、修复 | 两种 Maven 示例，最终构建各两次；真实工具、命令、SHA 和独立行为检查 |
| AC-03 请求重建 | M1 回归；M4 本地 HTTP 捕获与事件重建逐请求匹配 |
| AC-05/08 路径、并发保护 | M2 负面回归；补丁路径/版本预检，完成判定当前 SHA |
| AC-06/07 审批、超时、取消 | M3 回归；EOF/拒绝/等待取消、实际子进程与另一 CLI cancel |
| AC-09/10 崩溃、持久约束 | M2/M3 实盘与跨进程回归；真实 CLI 文件提交后结果追加前 halt，检查→核实→冷续跑，主体只进入一次；损坏/未来版本不自动修复 |
| AC-11/12/13 扩展、隔离、替换 | M0/M1/M3 回归；同一 CLI 装配路径替换 AgentLoopFactory |
| AC-14/15 计划、事实报告 | dry-run 无副作用主体；报告关联 call/event/版本/交付 SHA，模型假成功不成为事实 |

NFR-01/04/05/06/07/09/10/11/12/13 的相关契约随上述测试回归；NFR-02 增加实际启动器与惰性 doctor；NFR-03 保留 M2 的 1,000 条目实机性能证据，工作区回归通过；NFR-08 本机中文/空格路径与 Windows Shell 通过，远端 Linux 和其他实机尚未验证。具体上限与测试条件见 M0～M3 验收记录，不扩大原有结论。

进程清理继续明确 ProcessHandle 采样限制/partial，没有 Job Object 或不可信项目沙箱。完整存储迁移/分叉/查询、流式/PTY 和更多执行环境留给 M5～M7；[M4 契约](../development/M4-contracts.md) 记录行为与限制。
