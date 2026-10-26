# M3 验收记录

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](../development/current-state.md)及[全阶段审计](M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

日期：2026-10-14。本阶段按 PRD v1.1 与 NFR/AC 顺序表实施命令、策略、审批、预算、取消和进程清理；使用真实文件系统/进程/本机 socket、Fake LLM，未访问付费模型。

验证命令：`.\mvnw.cmd -B spotless:apply clean verify`。2026-10-14 16:09:32 +08:00 完成，耗时 37.179 秒，BUILD SUCCESS；130 项测试、0 失败、0 错误、0 跳过，其中 M3 新增 26 项。编译执行 `-Xlint:all -Werror`，verify 执行格式检查及全部模块测试。最终运行结果、各测试名、环境、日志 SHA-256 与源码/构建哈希由 [M3-results.json](M3-results.json) 记录；原始日志位于 `.build-cache/M3-verify.log`（Git 忽略）。

使用 PowerShell 7 的证据采集器 `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/collect-test-evidence.ps1 -Milestone M3 -VerificationCommand '.\mvnw.cmd -B spotless:apply clean verify'` 导出结果。打包后的 `scripts/jharness-cancel.ps1` 另通过 Windows PowerShell 验证：未知 UUID 返回 NOT_FOUND/退出码 2；跨 JVM 有效取消与退出/不可达行为由 JUnit 实际验证。

## 本阶段可核验的证据

| 要求 | 实现与测试 |
|---|---|
| AC-06 / NFR-05 审批 | 删除/强制 reset 被拒绝，目标文件保留，未创建 tool.intent/tool.started；完整参数和拒绝决定实际 JSONL 保存。默认精确模板不接受后缀、Shell 链或额外 env 字段 |
| AC-07 / NFR-04、08 清理 | 真实 JVM 双流持续输出、阻塞、真实子进程树；超时、取消、服务关闭释放顶层准入槽。已观察 PID 确认退出，但结果仍 partial；拒绝终止故障返回 failed/UNKNOWN 与实际存活 PID，测试最后显式终止并等待退出 |
| FR-09 / NFR-06 命令与环境 | 中文/空格/操作符作为 argv 参数原样到达；stdin EOF；非零退出结构化 FAILED；环境 clear 后只注入授权项，值不入摘要，配置秘密输出脱敏；Windows batch 显式 CMD |
| FR-11.4 / NFR-08 取消控制 | 单独 JVM 调用 CancelMain，经 runId 状态文件和认证端点取消真实 Agent 命令；终态 CANCELLED、已观察清理证据、无后续模型调用。无凭据不取消；不存在、已退出、不可达、格式错误有独立返回码；文件 owner-only，普通用户文件目录不被重设 |
| FR-04.4 预算 | 时间预算中止真实阻塞命令并报告 LIMIT_REACHED/TIME_LIMIT；费用保守预留超额时在第二次发送前停止；缺少 accounting 在首次发送前拒绝；明确模型与 max_tokens 才接受配置价格计算 |
| AC-09 / NFR-12 命令检查点 | 真实 JSONL force 在命令意图记录后失败，writer poisoned、最终写入失败如实抛出；进程未启动，无 tool.started；延续 M1 包装器不能变更批准参数/重复执行保证 |
| 未知副作用 | 命令超时记录 sideEffectUncertain，当前轮停止，下轮仍在发送模型前拒绝；审批等待取消有撤销事件，未进入主体 |
| AC-14 计划预览 | write/edit 给目标路径、版本和 Diff，源文件不变/新文件不存在；Shell 重定向/子进程计划明确“无法预览”；越界和旧版本拒绝 |
| 构建错误修复 | 实际固定 Maven Wrapper 离线调用临时 Java 项目：编译失败 → 模型收到 NONZERO_EXIT → 工具修改 Java 文件 → 再编译退出 0 → 生成 Broken.class → 完成。stdout 产物重新打开后可读编译错误；非零退出没有被误判成功 |

M0～M2 全量回归同时执行，包括跨 JVM 写锁、实际崩溃后副作用不重放、工作区 junction 越界与模型请求重建。

## 边界与后续

Windows 本地测试不代表 Linux/macOS 已实际验证；现有 Windows/Linux GitHub Actions 已包含新模块，远端执行待上传后确认。状态文件使用原子替换及当前用户权限，未模拟设备断电。

本阶段不宣称任意 Shell 完整进程树 clean；ProcessHandle 只覆盖已观察集合，未采用 Job Object/沙箱。进程树上限是检测式上限，无法阻止尚未观察到的 fork。清理保证与限制必须随报告一起交付。

费用上限依赖宿主提供真实有效的价格与请求上界，预留额不是实际账单；未进行付费计费核验。dry-run 的文件 Diff 是估计，不替代执行时版本检查；未知 Shell 副作用明确不可预览。

M4 接入正式 CLI/run/REPL/doctor、终端审批、`jharness cancel --run`、--dry-run、报告及最小恢复核实入口；M4 仍需 AC-01/02/04 的真实模型与产品入口验证，本次 Fake LLM 修复闭环不能替代这些验收。
