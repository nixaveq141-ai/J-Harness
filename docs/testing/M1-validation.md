# M1 开发与测试验收记录

日期：2026-10-13（Asia/Shanghai）。状态：**M1 本地开发与测试完成**。M0 的 32 项测试仍通过；当前总计 **76 项测试，0 失败、0 错误、0 跳过**。

## 实际验证与证据

Windows 11、Oracle JDK 21.0.12.1、Maven Wrapper 3.9.9。最终执行：

```powershell
.\mvnw.cmd -B spotless:apply clean verify
```

结果：BUILD SUCCESS；8 个模块编译、打包通过；`-Xlint:all -Werror` 与 Spotless 格式检查通过。模型测试仅调用 Fake LLM 和 127.0.0.1 本地 HTTP 服务，没有外部模型调用或真实 API key。

结构化证据：[M1-results.json](M1-results.json)，包含命令、环境、测试名称、计数及源码/构建配置的 SHA-256。源码尚未提交，按该清单识别测试版本。原始日志在 `.build-cache/M1-verify.log`，原始 JUnit XML 在各模块 `target/surefire-reports/`；后续 clean 会删除旧报告。

导出本阶段证据：

```powershell
.\scripts\collect-test-evidence.ps1 -Milestone M1 -VerificationCommand '.\mvnw.cmd -B spotless:apply clean verify'
```

导出脚本从根 POM 发现模块，拒绝源码或报告晚于验证日志的快照。M0 历史 JSON 保留，不用 M1 结果覆盖。

## 里程碑退出条件

| M1 要求 | 实现与自动化证据 | 结论 |
|---|---|---|
| 从 Session 派生请求 | DefaultPromptComposer、EventSessionProjection、EventRequestJournal | 通过 |
| 任意 model.requested 可从此前日志重建 | EventRequestJournalTest；M1AgentLoopTest 对 Fake LLM 捕获的每次请求逐前缀比较 | AC-03 的 M1 场景通过 |
| 多步 Agent Loop | read_file → edit_file → execute_command 模拟任务；同响应多工具顺序、callId、错误反馈与修复 | 通过；未执行真实文件/命令 |
| 工具执行管线 | M1ToolPipelineTest：正常顺序、Schema、作用域、ASK 拒绝、参数改写、撤销、检查点、执行包装与结果真实性 | 通过 |
| 可替换模型与 Session 存储 | Fake LLM 与本地 HTTP 提供方运行同一 Loop；替代内存 SessionRepository 提供方无需修改 Loop | 通过 |
| 扩展点插件 | M1AgentLoopTest 的插件组合：改写工具参数 + 只读免审批；停止后注册与拦截贡献撤销 | AC-11 的 M1 任务级场景通过 |
| 状态与终止 | 步数/时间限额、准备阶段取消、执行中取消、模型错误、重复 ID、目标证据缺失、UNKNOWN 停止及阻止下一轮 | 通过 |
| 模型适配器与能力发现 | OpenAiCompatibleLlmClientTest：协议、JSON 参数转换、认证、429 有限重试、5xx 不重试、取消、响应体超时、大小限制、重定向拒绝与不支持能力 | 本地协议通过；真实提供方待实测 |
| 可观察与隐私 | 请求/结果关联 ID、意图/审批/耗时、目标状态；消息/参数/结果脱敏，密钥哨兵、JSON 深度及未知 Schema 拒绝 | M1 覆盖范围通过 |

## 测试计数

| 模块 | 测试数 |
|---|---:|
| harness-api | 3 |
| harness-events | 7 |
| harness-session | 10 |
| harness-plugin | 10 |
| harness-tools | 4 |
| harness-llm | 9 |
| harness-testkit | 33 |
| 合计 | 76 |

harness-agent 的 15 项任务集成测试放在 testkit，避免产品模块反向依赖测试设施。harness-testkit 的 33 项另包括工具管线 11 项、M0 替换 2 项、测试设施 5 项。

## NFR / AC 状态

- **NFR-11 / AC-03：** M1 请求快照、事件投影及逐前缀重建通过；压缩/流式/迁移场景在后续阶段回归。
- **NFR-13 / AC-13：** 现有 Loop/工具注册表替换回归，加上模型和内存存储替换、插件装配通过；真实 CLI 配置加载与磁盘存储替换后续验收。
- **AC-11、AC-12：** 任务级拦截生效、插件撤销、撤销工具在审批后再次校验，以及 M0 作用域隔离回归通过；M3 真实策略、M7 多 Agent 产品路径继续复验。
- **NFR-01：** 模型成功叙述不能绕过目标证据；失败结果可反馈并修复，UNKNOWN 明确终止。真实项目目标与最终报告仍在 M4 验收。
- **NFR-04、06、07、09、10：** 本阶段的模型输入/输出界限、协议错误、取消、脱敏、错误码、测试与事件证据通过；文件大输出、进程与报告边界尚未验收。
- **NFR-12 / AC-09、10：** 只有顺序守护、内存拒绝副作用与模拟 flush 故障证据；真实 JSONL、跨进程写拥有权、损坏/未来版本及崩溃窗口未验收。

Windows/Linux GitHub Actions 配置继续适用新增模块，远端 CI 尚未运行；Linux 未实测。没有真实付费模型的效果、兼容性与稳定性证据，不将本地协议通过等同于这些证据。

## M2 接续范围

1. 实现工作区与执行环境的文件服务，先验证路径穿越、绝对路径、符号链接及 Windows junction 拦截（AC-05）。
2. 实现读取、搜索、分页、受保护写入、Diff、版本守护与原子写，同步验证 AC-08 和 NFR-03/04。
3. 接入最小 JSONL 实盘检查点；在任何真实文件写工具启用前验证持久化失败不进入主体，并开展 AC-09/10 的 V1 前置场景。
4. 将 M1 模拟工具逐步换成真实文件工具，继续逐请求回放、审批与插件替换回归；命令执行仍留给 M3。
