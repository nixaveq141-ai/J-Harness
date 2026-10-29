# 当前实现、验收与未完成项

更新：2026-10-29。活动状态以本节为准，各原始JSON和后文日期记录保留其历史版本。

最近一次完整三平台已验收版本为`1f7d521`，运行[40671352949](https://github.com/nixaveq141-ai/J-Harness/actions/runs/40671352949)。每平台718 Java/119套件、42客户端、813浏览器、三引擎R4各51、SDK66、原生7、示例2及候选安装门禁通过，三原ZIP和611输入已严格复核。见[完整CI](../testing/M10-gap-full-ci-2026-10-28.json)。历史验收不覆盖之后的新代码。

`4584d59`后续本地完整719/120、614输入回执及R4真实后端17×3响应窗口通过。历史M4真实生成代码在[隔离专项40683923370](https://github.com/nixaveq141-ai/J-Harness/actions/runs/40683923370)完成Docker编译和独立功能核验，错误实现被拒绝，两自有容器清理与只读输入均已核对，无新模型调用。见[专项原包审计](../testing/M10-gap-isolation-and-index-2026-10-28.json)。同一旧版本索引读取/锁专项Linux通过，Windows/macOS仍明确LIMITED。

目录原生专项`d1a5415`已在[40688290873](https://github.com/nixaveq141-ai/J-Harness/actions/runs/40688290873)三平台通过：每侧55项精确Java测试及索引发布/读取/锁、禁用能力的旧路径反例；Windows另核真实原地联接攻击和自有清理。三原ZIP、源码、日志和身份已独立审计，见[专项证据](../testing/M10-gap-native-focused-2026-10-28.json)。首轮失败保留，未降低断言或改用宽时限。`0170fce`本地729项/122套件也已归档，但不是后续源码的完整验收。

2026-10-29继续扩展事实JSONL目录能力：读取、writer/maintenance锁、备份、迁移、分叉和临时文件已接入；显式外部导出另绑定所选父目录，保持不覆盖。两项新增根替换/已有writer保留原文件回归及相关模块已通过。原身份全保留，当前精确契约732项/123套件；完整验证首轮在一万条目录扫描超时而拒绝；删除reader重复存在性检查后原扫描/并发断言及相关16项定点通过。完整重验、远端完整CI及Docker仍待。关闭目录时失败也会清理已打开文件通道。实体机器验收仍跳过。

三协议真实非流式/SSE只读往返已核对。原DeepSeek账本12次、不退款预留1.769472元人民币，2元授权及12次上限不变，禁止新付费调用。历史代码隔离评测不代表新任务或模型普遍能力认证。

M1～M10.5选定范围已交付，M10.5.9历史验收成立，M11未实施；本轮新增改动尚需独立证据。实体Windows10、独立WSL、额外CPU及实体Mac验收按用户要求跳过。历史macOS偶发原因保留未知，原生二进制精确构建来源保持限制；异地备份尚无用户指定目标。API/Java SDK1.14、独立stdio1.0、JSONL/schema1、Web目录4。仓库公开，验证分支不合并main、不发布Release。

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
| FR-07 | 文件与过滤 | 受保护写入/补丁/检索；目录能力新分支正在验证，完整wildmatch与删除/重命名补丁未承诺。 | [LocalWorkspace.java](../../harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java) |
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
| FR-27 | 环境/多入口 | 本地/Docker实现、CLI/JavaSDK/Web/stdio；七组真实Docker专项已通过，Desktop未实现另列。 | [HarnessSdk.java](../../harness-ecosystem/src/main/java/io/jharness/ecosystem/HarnessSdk.java) |

## 后续范围与安全边界

M11 Desktop尚未实现。M12候选包含图片/多模态、热重载/隔离类加载器、Goal/Schedule、Workflow、PTC、通用Web搜索/LSP和终端强化；每项独立立项，不能视为已完成阶段漏实现。MCP OAuth/常驻续传、隐式Skills监视、写入/递归通用子任务仍不支持。Teams、语音和控制其他桌面软件是非目标。

受信宿主与路径检查不是OS沙箱；ProcessHandle采样不等于Job Object；force/原子替换不保证所有平台父目录fsync或设备断电；UTF-8/补丁/gitignore支持子集及容量配额保持。UNKNOWN只读诊断和人工核实是安全策略，不是漏做在线自动重放。SQLite不是JSONL交付的前置条件。

## 本轮文档更新与版本绑定

本轮仅扫描和更新文档，不实施上述功能、运行新全量CI或调用付费模型。随包说明`docs/examples/m1059-distribution.md`更新了验收状态，因此当前仓库文档输入与验收构建存在一处差异；既有CI制品和清单继续绑定77e8855。后续重打包须生成新的候选清单和适用安装回执。本轮文档提交不冒充新的候选制品验收。

2026-10-28专项收尾：1f7d521的Windows生命周期40670936898共35项、macOS/WebKit开发流程40670970878共14项均通过。Windows原ZIP/611输入/精确XML/日志回执与Mac原ZIP/14身份/截图/轮次副本均复核，Mac旧异常原因仍未定。见[专项结果](../testing/M10-gap-focused-2026-10-28.json)。同版本完整三平台已申请一次运行，尚未验收。

2026-10-28补齐Responses真实SSE：原账本第11/12次均HTTP200、17/60帧及完整终止，实际CLI一次只读工具/COMPLETED/答案42和全部日志摘要独立核对。三协议真实SSE子项齐全；累计不退款预留1.769472元，已达12次上限，禁止新增付费请求。中继先有界读取再拒绝错误认证/路径，修正Windows未读请求体连接重置；9项离线过。见[Responses结果](../testing/M10-gap-responses-sse-2026-10-28.json)。隔离生成代码、目录能力、R4其余服务端窗口及混合负载仍待，CI40671352949仍运行，实体机器跳过。

2026-10-28混合读写样本：128MiB堆、单写者16轮共2048次真实append force、另1次header force，4读者同时完成64次完整前缀读取/投影，64次均观察到非空未完成历史，最终2048事实身份/正文/顺序由Python独立复核。样本总时长含屏障1.378秒，append p95为0.493ms，读取投影p95为54.600ms；堆池各自峰值之和93572104字节，是测量区间保守上界，不是精确同时峰值或RSS。见[混合负载结果](../testing/M10-gap-mixed-load-2026-10-28.json)。本机受控混合子项已补，更广系统负载/跨平台性能仍待；目录能力/R4其余服务端窗口/隔离生成代码继续，实体机器跳过。

2026-10-28目录能力首批：索引缓存发布在支持SecureDirectoryStream的提供者上改为已开目录相对创建/force/原子move/清理，并核对句柄绑定fileKey；根名被换后拒绝成功回执且不通过替换路径清理。受控探针在最后检查后替换根并放置同名诱导临时文件，要求外部哨兵不变、发布仅进入原目录；另编译禁用能力分支的对照，要求恰在越界发布断言失败。Windows本地完整session55项过，探针正常覆盖发布过并明确LIMITED；Linux/macOS真实能力分支仍待远端专项。仅发布子路径，缓存读取/锁、工作区、产物及Windows能力继续；GAP-01未关闭。见[目录能力结果](../testing/M10-gap-index-capability-2026-10-28.json)，依据[Java目录能力契约](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/SecureDirectoryStream.html)。

2026-10-28目录能力远端结果：250d8fe/40672988045三侧专项成功。Linux实际SDS分支的根名替换反例通过；受控禁用能力分支后确实在外部哨兵被改写处失败。macOS和Windows提供者均无SDS，正常发布通过但竞态记录LIMITED。三个原ZIP摘要、源码、日志和各5项既有回归已独立核对。该结论仅覆盖支持提供者的索引发布；读/锁、工作区/产物及其他平台能力保持开放。原完整CI40671352949截至本次Mac/Linux成功、Windows矩阵进行中，且只绑定旧1f7d521，不作为250d8fe全量验收。

2026-10-28集中收尾：1f7d521/40671352949三侧全部成功，原三ZIP及611输入、各718Java/119套件、813浏览器/三引擎R4/SDK/原生/候选严格审计通过，见[完整CI](../testing/M10-gap-full-ci-2026-10-28.json)，不覆盖后续源码。新R4真实后端17操作×3响应故障51窗口已专项通过；首轮统一verify遇新夹具取消早于模型主体入口，已增加真实入口屏障而保留UNKNOWN断言，相关原失败均保留，当前719/120完整重验仍待。见[真实窗口矩阵](../testing/M10-gap-r4-real-matrix-2026-10-28.json)。新增Docker-only历史模型代码评测驱动及四项离线边界负例通过；准备真实容器执行正确/错误实现及自有清理，无新付费调用、旧live-evaluate仍冻结。目录能力其余路径继续，实体机器跳过。

2026-10-28统一验证已通过：新增真实HTTP51窗口、索引读取及锁打开的能力接入后，完整verify719项/120套件零失败错误跳过，原718身份保留，全部构建输入前后相同；原XML/日志/回执/源码/JAR ef774480已归档。索引能力探针新增读取/锁各自的根替换及禁用能力对照，Windows兼容及session55过，Linux真实读锁分支待专项。隔离首次40677502238原ZIP摘要ad32707c已核对：正确历史代码因非root无法写默认/work临时盘而编译失败，隔离配置/宿主只读/清理实际通过；显式tmpfs mode1777后4离线项过，真实重验待，不降级成root。目录其余提供者及工作区/产物继续，实体机器跳过，无付费调用。
