# M6 验收记录

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](../development/current-state.md)及[全阶段审计](M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

日期：2026-10-14。状态：M6 本地开发与集成验收完成；远端 Windows/Linux/macOS CI 未运行，不代表 V2 的全部后续产品化能力完成。

## 构建与证据

执行 `.\mvnw.cmd -B clean verify`，BUILD SUCCESS；**198 项测试，0 失败、0 错误、0 跳过**。包含 M0～M5 的 175 项回归及 M6 新增 23 项；Java 21、-Xlint:all -Werror、Spotless、模块与 shaded CLI 打包通过。

[M6-results.json](M6-results.json) 保存每个测试、平台/JDK/Maven、源码与配置 SHA、日志 SHA 及远端 CI 状态。原始日志 `.build-cache/M6-verify.log`，JUnit XML 在各模块 target/surefire-reports。代码未提交，用 sourceSha256 识别本次源码快照。

```powershell
.\mvnw.cmd -B spotless:apply
.\mvnw.cmd -B clean verify
.\scripts\collect-test-evidence.ps1 -Milestone M6
```

全量测试使用标准 Windows 临时目录，保留既有 junction、非 Git 目录与跨进程断言；未跳过或削弱测试。一次中间回归出现既有命令夹具的 Windows 临时目录释放竞争，后续全量 test 与最终 clean verify 都完整通过。

## 需求与验收结果

| 需求 | 测试证据 / 实际结果 |
|---|---|
| FR-14.1 / NFR-04、06 / AC-03、07 | M6StreamingTest 5 项：逐字节 SSE、跨 UTF-8/CRLF、多个工具调用与 arguments 分片、usage 尾块；缺 DONE/坏 JSON/重复 ID/截断 arguments/length 结束拒绝，不暴露最终工具，不重试；取消、整个 body 超时与字节上限通过；跨 delta 秘密值不进入展示 |
| FR-14.2、14.3 / FR-08.2、08.3 / AC-03、11、12 | M6CompactionTest 3 项：同一轮长工具历史压缩，完整组移除，保留系统、当前目标与最新结果；未决或孤立工具协议、超量保护内容拒绝。BEFORE_MODEL 插件保存 compaction 事实，最终 request.context 可重建，插件 stop 后贡献撤销 |
| FR-15.1、15.2、15.3 / AC-05、07、09 | M6TerminalTest 5 项：真实 PTY 输入/resize/cwd，后台 start/status/log/stop、超时、取消前不启动、路径穿越/非法页拒绝、关闭无观察残留。滚动日志明确旧 offset 已丢弃，单页与活跃任务配额有界；阻塞 stdin 取消在期限内返回 UNKNOWN 并停止进程 |
| FR-16.1 / FR-05.2 / AC-13 | LazyModelRouter 的未选路线不创建，重复选择复用实例，关闭只释放已创建适配器；stream/tools 能力与显式降级验证。CLI Route 的模型项不能改变策略或工具包 |
| FR-16.2、16.3 / NFR-10、06 | 真实 CLI 流式循环为每条请求保存 model.observed 元数据，关联 sourceEvent，报告分离模型/终端事实；不记录 prompt/response 正文。token 使用量按提供方返回保存，费用仍沿原预留上界事实，不虚构账单 |
| FR-17.1 / NFR-05、09 | M6ConfigurationTest 3 项：Profile/Preset/Model/Bundle 组合、顺序、去重和选择 SHA；路线冲突、Bundle 策略冲突、read-only 降级尝试、未知组合、内联 key、错误类型/阈值拒绝 |
| AC-03 / 长任务退出条件 | M6RuntimeIntegrationTest：CLI 单轮 8 次真实文件读取 + 最终文本，SSE 函数增量只执行一次；自动压缩发生。全部 9 条 model.requested 仅依赖其日志前缀重建，messages/tools 与实际 HTTP 内容在函数 arguments JSON 编码归一化后相等，模型参数一致 |
| AC-09 / 不重放 | 截断的工具增量不进入 approval/intent/body，文件不存在，恢复返回 6；未决模型请求保留。M5CliTest 新增冷终端 RUNNING 核实场景，恢复不启动/不杀旧 PID，显式 verify-terminal 后解除阻断 |
| AC-06、09 / 终端权限与恢复 | 真实 CLI 后台启动/停止分别有审批与 durable intent；只读策略在主体前 DENY，隐藏写工具即使猜名也不执行。关闭保存终态及尾日志产物，冷 ArtifactStore 可读并校验引用 |
| FR-15.1 / REPL 持久会话 | 同一 REPL 两轮用户任务共享同一个终端：第一轮创建，第二轮 status 仍为 RUNNING，再显式停止；日志有两次 turn.started，最终冷恢复 ready=true |
| AC-08～13 持续回归 | M2～M5 的文件版本保护、真实检查点故障、跨 JVM 锁、崩溃窗口、核实、迁移/未来版本、冷分叉与产物、插件替换/撤销全部通过 |

M6StreamingTest 5 项、M6TerminalTest 5 项、M6ConfigurationTest 3 项、M6CompactionTest 3 项、M6RuntimeIntegrationTest 6 项，以及 M5CliTest 新增 1 项，共 23 项。

## 打包 CLI 与原生库验收

使用 Java 21 直接运行 shaded JAR，`--version` 显示 API 1.6。独立 [M6-shaded-results.json](M6-shaded-results.json) 记录同一打包产物 SHA 与本地 SSE/PTY 夹具结果：通过，23 次本地模型替身请求，真实 pty=true，任务 EXITED，观察残留 PID 为空，尾部产物 SHA 校验成功。

夹具从打包 JAR 创建真实交互终端→resize→输入→分页等待实际 SHADED_PTY 标记→结束，最终冷产物可读。首次夹具在终端输出前结束宿主导致断言失败，改为按真实输出等待后通过；生产代码没有为夹具加入等待或假成功。CLI 及日志秘密哨兵未泄露。该检查不调用付费模型，不能冒充远端服务或真实 DeepSeek SSE 兼容性验收；M4 付费实测证据仍为历史记录。

## 边界与后续

- CI 配置已扩展到 Windows/Linux/macOS；仓库没有 Git remote，远端结果仍为未运行。本地证据仅证明 Windows。
- 终端归 CLI/REPL 宿主拥有，正常退出停止任务；不支持脱离宿主的常驻守护服务、冷启动接管旧进程或自动重启。
- 进程树仍为 ProcessHandle 采样保障，没有 Job Object/OS 沙箱，清理报告保持 partial 与残留证据。不能保证快速脱离且未观察到的后代归属。
- 长期输出是有界滚动尾日志，旧 offset 明确不可用；终态尾部保存产物，不声称无限完整日志留存。
- 默认压缩器是保守的内容摘录，不是外部模型语义摘要；超量保护内容拒绝继续。thinking/reasoning、图片不在本次支持范围。
- Profile 模型路线目前内置 OpenAI-compatible；公共 Router 支持替换其他 LlmClient，尚不提供其他供应商的专用协议适配器。
- M7 下一阶段为 MCP、Skills、Subagent、沙箱、Web/SDK 与执行环境扩展。

完整行为及配置示例见 [M6 契约](../development/M6-contracts.md)。
