# 当前实现、验收与未完成项

更新：2026-10-26。本文是开发与使用状态的统一入口；阶段原始验收JSON和取证材料各自保留版本。

后续补缺已开始：[补齐进展](gap-remediation-2026-10-26.md)。GAP-03三组分页断言与GAP-08逐行规则诊断已补齐，本地专项28项通过。DeepSeek三协议只读真实闭环6次成功，人民币2元授权下累计预留上界0.884736元；生成代码执行继续冻结。其余缺口仍待处理。以下77e8855是既有三平台验收基线，新改动尚未重新完成整体验收。

已验收运行时代码：`77e8855edaab0f7e543c9709c95513e3a0a2c4bb`，完整CI [40284862454](https://github.com/nixaveq141-ai/J-Harness/actions/runs/40284862454)，原包已独立审计。M1～M10.5选定范围已交付，M10.5.9完成、M11准入通过但未实施；PRD全部子项、R4/R5专项意见没有因此全部关闭。API/Java SDK1.14，独立stdio1.0，JSONL/schema1，Web目录4。

每平台：Java702/119套件、客户端42、三浏览器33组813、SDK66、原生凭据7、示例2及适用发布/安装门禁通过。源码与UI摘要跨平台一致；各平台JAR分别绑定到其回执和候选包。仓库公开，默认main未合并验证分支，Release未发布。开源可见性与发布版本是独立状态。

扫描结果见[全阶段缺口审计](../testing/M1-M10.5-gap-audit-2026-10-26.md)。R4主要缺逐项故障窗口证据；R5保留2条有限实施、11条后续。当前Docker主动实测、Windows10/独立WSL等额外平台、生产长会话性能和当前真实厂商评测未闭合。历史macOS模型按钮一次超时未定位，后续定点及本次完整验收通过。

## M1至今逐阶段索引

| 阶段 | 已交付选定范围 | 契约 / 验收 |
|---|---|---|
| M1 | 工具管线、模型、事实投影 | [契约](M1-contracts.md) / [验收](../testing/M1-validation.md) |
| M2 | 文件、产物、JSONL检查点 | [契约](M2-contracts.md) / [验收](../testing/M2-validation.md) |
| M3 | 命令、审批、预算、取消 | [契约](M3-contracts.md) / [验收](../testing/M3-validation.md) |
| M4 | CLI闭环、真实模型历史评测 | [契约](M4-contracts.md) / [验收](../testing/M4-validation.md) |
| M5 | 恢复、迁移、查询、分叉 | [契约](M5-contracts.md) / [验收](../testing/M5-validation.md) |
| M6 | 流式、压缩、PTY、配置组合 | [契约](M6-contracts.md) / [验收](../testing/M6-validation.md) |
| M7 | 环境、MCP、Skills、只读子任务、Web/SDK | [契约](M7-contracts.md) / [验收](../testing/M7-validation.md) |
| M8 | 过滤、只读调度、模型限流重试及收口 | [契约](M8-contracts.md) / [验收](../testing/M8-validation.md) |
| M9 | 供应链、三平台候选和安装验证 | [契约](M9-contracts.md) / [验收](../testing/M9-validation.md) |
| M10 | 工作台、设置、自动入口 | [契约](M10-contracts.md) / [验收](../testing/M10-validation.md) |
| M10.5.1 | 诊断与离线核实 | [契约](M10.5.1-contracts.md) / [验收](../testing/M10.5.1-validation.md) |
| M10.5.2 | 会话与工作区生命周期 | [契约](M10.5.2-contracts.md) / [验收](../testing/M10.5.2-validation.md) |
| M10.5.3 | 多轮队列 | [契约](M10.5.3-contracts.md) / [验收](../testing/M10.5.3-validation.md) |
| M10.5.4 | 三协议与逐轮模型快照 | [契约](M10.5.4-contracts.md) / [验收](../testing/M10.5.4-validation.md) |
| M10.5.5 | 结构化提问 | [契约](M10.5.5-contracts.md) / [验收](../testing/M10.5.5-validation.md) |
| M10.5.6 | 内容/文件/产物展示 | [契约](M10.5.6-contracts.md) / [验收](../testing/M10.5.6-validation.md) |
| M10.5.7 | 扩展表单与资源 | [契约](M10.5.7-contracts.md) / [验收](../testing/M10.5.7-validation.md) |
| M10.5.8 | 正文检索与外部SDK | [契约](M10.5.8-contracts.md) / [验收](../testing/M10.5.8-validation.md) |
| M10.5.9 | 最终三平台三浏览器验收 | [契约](M10.5.9-contracts.md) / [验收](../testing/M10.5.9-validation.md) |

## PRD 27组当前状态

“选定范围已交付”包含明确的子集边界，不表示所有未来能力或任意运行条件都受保证。

| FR | 能力 | 状态与边界 | 实现定位 |
|---|---|---|---|
| FR-01 | 插件生命周期 | 静态装载、作用域、替换与撤销；热重载/类加载器留M12。 | [PluginContainer.java](../../harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java) |
| FR-02 | 事件与扩展点 | 事件、串行/通知/拦截链已实现；不强杀不合作Java线程。 | [DefaultEventBus.java](../../harness-events/src/main/java/io/jharness/events/DefaultEventBus.java) |
| FR-03 | 事实日志与检查点 | JSONL单写者、执行前落盘、投影及UNKNOWN；保留断电/目录fsync边界。 | [JsonlSessionRepository.java](../../harness-session/src/main/java/io/jharness/session/JsonlSessionRepository.java) |
| FR-04 | Agent Loop | 多轮、预算、取消、工具闭环、证据完成；不是长期Goal。 | [DefaultAgentLoop.java](../../harness-agent/src/main/java/io/jharness/agent/DefaultAgentLoop.java) |
| FR-05 | 模型协议 | Chat Completions、Responses、Anthropic文本/工具与SSE；真实厂商当前版本认证另列。 | [ProtocolCodec.java](../../harness-llm/src/main/java/io/jharness/llm/ProtocolCodec.java) |
| FR-06 | 工具管线 | 注册/Schema子集、策略、审批、持久意图和来源；不是任意Schema方言。 | [DefaultToolPipeline.java](../../harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java) |
| FR-07 | 文件与过滤 | 受保护写入/补丁/检索；父目录替换竞态、完整wildmatch与删除/重命名补丁未承诺。 | [LocalWorkspace.java](../../harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java) |
| FR-08 | 上下文与产物 | 有界压缩/产物/回读；图片未实现，来源映射与安全快照后续见缺口表。 | [ContextCompactionPlugin.java](../../harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java) |
| FR-09 | 进程 | 受控命令/取消/双流；ProcessHandle采样不是Job Object/完整归属保证。 | [LocalProcessRunner.java](../../harness-process/src/main/java/io/jharness/process/LocalProcessRunner.java) |
| FR-10 | 策略与审批 | ALLOW/ASK/DENY、精确审批、只读预览；任意Shell不能静态预览。 | [DefaultPolicyEngine.java](../../harness-policy/src/main/java/io/jharness/policy/DefaultPolicyEngine.java) |
| FR-11 | CLI | run/REPL/doctor/恢复/报告/serve/生态配置；Desktop独立。 | [CliApplication.java](../../harness-cli/src/main/java/io/jharness/cli/CliApplication.java) |
| FR-12 | 交付报告 | 事实/模型叙述、文件SHA与来源分开；有界报告不是完整日志副本。 | [CliApplication.java](../../harness-cli/src/main/java/io/jharness/cli/CliApplication.java) |
| FR-13 | 存储恢复 | JSONL查询/分叉/导出/迁移/人工核实；SQLite可选，未知格式拒绝。 | [SessionRecovery.java](../../harness-session/src/main/java/io/jharness/session/SessionRecovery.java) |
| FR-14 | 流式与压缩 | 受校验流式、有界摘要、请求重建；重复/合成消息来源不可逆时保留未映射。 | [ContextCompactionPlugin.java](../../harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java) |
| FR-15 | 终端 | 宿主内PTY/后台任务/尾日志；无冷接管守护、Job Object或Docker PTY。 | [LocalTerminalSession.java](../../harness-process/src/main/java/io/jharness/process/LocalTerminalSession.java) |
| FR-16 | 多模型与观测 | 可信模型选择、协议快照、usage及保守预算；不保证提供商缓存命中。 | [LazyModelRouter.java](../../harness-llm/src/main/java/io/jharness/llm/LazyModelRouter.java) |
| FR-17 | 配置组合 | 模型/Profile/Bundle/Preset及Web设置；任务正文不能改可信配置。 | [SdkConfiguration.java](../../harness-ecosystem/src/main/java/io/jharness/ecosystem/SdkConfiguration.java) |
| FR-18 | 热重载 | 静态插件已实现，热重载/类加载器迁移与回滚仍为M12候选。 | [PluginContainer.java](../../harness-plugin/src/main/java/io/jharness/plugin/PluginContainer.java) |
| FR-19 | MCP/Skills | CLI/SDK/Web受控装配、资源与单变量模板/显式技能根；OAuth/常驻续传/隐式监视未支持。 | [McpPlugin.java](../../harness-ecosystem/src/main/java/io/jharness/ecosystem/McpPlugin.java) |
| FR-20 | 子Agent | 只读委派、独立来源/预算/作用域、父单写者；写入型/递归通用子任务未实现。 | [SubagentPlugin.java](../../harness-ecosystem/src/main/java/io/jharness/ecosystem/SubagentPlugin.java) |
| FR-21 | Workflow | 节点编排、扇出/汇总/失败恢复未实现；Plan不是Workflow。 | [PlanningPlugin.java](../../harness-ecosystem/src/main/java/io/jharness/ecosystem/PlanningPlugin.java) |
| FR-22 | PTC | run_code与代码内逐工具审计运行时未实现；Shell不是PTC。 | [DefaultToolPipeline.java](../../harness-tools/src/main/java/io/jharness/tools/DefaultToolPipeline.java) |
| FR-23 | 任务类型 | Todo/Plan已实现；Goal/Schedule生命周期与后台调度未实现。 | [PlanningPlugin.java](../../harness-ecosystem/src/main/java/io/jharness/ecosystem/PlanningPlugin.java) |
| FR-24 | 会话与正文检索 | 注册工作区、生命周期、跨会话词语/短语、排序/分页/来源、TS/Python SDK；无自动GC。 | [WorkbenchContentSearch.java](../../harness-web/src/main/java/io/jharness/web/WorkbenchContentSearch.java) |
| FR-25 | 工具调度/横切 | 显式有界只读并行/写屏障、默认关闭的模型限流/完整非流式429重试；工具UNKNOWN不重放。 | [ModelResiliencePlugin.java](../../harness-agent/src/main/java/io/jharness/agent/ModelResiliencePlugin.java) |
| FR-26 | 网页/LSP | 固定可信来源web_fetch已实现；通用搜索提供方和LSP延期。 | [WebFetchPlugin.java](../../harness-ecosystem/src/main/java/io/jharness/ecosystem/WebFetchPlugin.java) |
| FR-27 | 环境/多入口 | 本地/Docker实现、CLI/JavaSDK/Web/stdio；当前Docker实测缺口与Desktop未实现分开记录。 | [HarnessSdk.java](../../harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java) |

## 后续范围与安全边界

M11 Desktop尚未实现。M12候选包含图片/多模态、热重载/隔离类加载器、Goal/Schedule、Workflow、PTC、通用Web搜索/LSP和终端强化；每项独立立项，不能视为已完成阶段漏实现。MCP OAuth/常驻续传、隐式Skills监视、写入/递归通用子任务仍不支持。Teams、语音和控制其他桌面软件是非目标。

受信宿主与路径检查不是OS沙箱；ProcessHandle采样不等于Job Object；force/原子替换不保证所有平台父目录fsync或设备断电；UTF-8/补丁/gitignore支持子集及容量配额保持。UNKNOWN只读诊断和人工核实是安全策略，不是漏做在线自动重放。SQLite不是JSONL交付的前置条件。

## 本轮文档更新与版本绑定

本轮仅扫描和更新文档，不实施上述功能、运行新全量CI或调用付费模型。随包说明`docs/examples/m1059-distribution.md`更新了验收状态，因此当前仓库文档输入与验收构建存在一处差异；既有CI制品和清单继续绑定77e8855。后续重打包须生成新的候选清单和适用安装回执。本轮文档提交不冒充新的候选制品验收。
