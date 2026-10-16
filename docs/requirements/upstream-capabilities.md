# 上游能力对照与 M7 范围决定

更新：2026-10-15。固定提交为 [c291e7961a515f6d7af9304e7fd1d257929aef26](https://github.com/deepseek-ai/deepseek-harness/commit/c291e7961a515f6d7af9304e7fd1d257929aef26)，不宣称为当前最新版本。

本轮实际下载官方 codeload 固定提交归档，SHA256 为 `e268fe8bd0049cd77da25fd051cb06d71896d2b28ec1270bd5890e54fa70d42a`。Git 传输失败，未获得可核实 HEAD 的 checkout；归档取证不冒充 git rev-parse。已检查 19 个能力入口及相关声明，路径/SHA/行数见 [源码核验清单](../testing/M7-upstream-review.json)。这不是上游测试运行、全仓安全审计或 Cordis 格式/二进制兼容认证。

以下是当前范围决定，替代此前切片中的“待核验/待决定”状态。路径均相对固定提交的 packages/；支持是 Java 语义实现，不表示上游全部配置和传输格式兼容。

| 能力 / FR | 上游固定源码入口及关键差异 | 最终决定 / Java 验收 |
|---|---|---|
| 插件、作用域、生命周期 / FR-01、02、18 | core/tools、core/session 的服务/生命周期契约；Java 使用静态插件容器和精确作用域 | 支持基础，热重载/隔离类加载器延期；PluginContainerTest、M0ServiceReplacementTest、M7SubagentTest |
| Session、恢复、投影 / FR-03、08、13 | core/session 与 session/session-persistence-jsonl：追加事实、投影、不可变 generation 存储；Java 为独占单写者 JSONL、明确迁移及派生缓存 | 支持 Java 存储契约；JsonlSessionRepositoryTest、M5SessionTest；不声称上游 generation 格式兼容 |
| PTC / FR-22 | core/tools 有 native/ptc/both 与嵌套调用并发屏障；code-runtime/code-runtime-worker-thread 明确 worker 本身不是安全边界 | 延期；不注册 run_code。需要逐工具审批/intent、隔离运行时和嵌套取消证据后另立阶段，不能用 execute_command 代称 PTC |
| Workflow / FR-21 | workflow/workflow 是独立的预定义编排服务，区别于动态委派 | 延期；需要节点持久状态、扇出汇总和部分失败恢复。现有 Subagent/Plan 不冒充 Workflow；范围核验 |
| Goal / FR-23.2 | goal/tool-goal 为长期目标创建/更新，与 turn 分离并涉及外部人工授权 | 延期；无 Goal 状态仓库或模型工具；user goal 字符串仅是当前 turn 输入，不是长期 Goal 生命周期 |
| Todo / FR-23.3 | todo/tool-todo 为整表覆盖及进行中配置；Java 采用逐项 ID/revision 变更及证据引用 | 支持步骤清单：PENDING/IN_PROGRESS/DONE/REMOVED，todo.changed 持久化、list/update 工具；M7ClosureTest |
| Plan / FR-23.4 | plan/plan-mode 为 agent planmode/退出审查；Java 为结构化步骤与显式批准/执行/结束状态 | 支持 Java 计划：DRAFT/APPROVED/EXECUTING/COMPLETED/ABANDONED，plan.changed 持久化、view/update/transition；M7ClosureTest。不授予工具权限或自动执行步骤 |
| Schedule / FR-23.5 | schedule/schedule 为持久一次/固定频率提醒，独立后台生命周期 | 延期；无定时仓库、后台触发服务或工具，不以 Web 任务/命令 timeout 替代。后续必须定义无人审批，默认不自动批准高风险操作 |
| Subagent / FR-20 | subagent/tool-subagent 可配置前后台 provider；Java 为限定只读 Profile 与父单写者 | 支持基础委派，部分对齐：独立会话/预算/取消/费用/来源/并发配额；M7SubagentTest、M7-subagent-docker-results.json。写入型/递归/后台通用提供方延期 |
| Teams / FR-20.4 | 上游实验协作 seam | 排除于本阶段主线；不将编码开发工具中的 agent 等同产品 Teams |
| MCP / FR-19 | mcp/mcp-client 有服务器命名空间、生命周期和重连配置；Java 为有界同步 stdio/HTTP JSON+SSE 子集 | 支持基础连接/发现/Schema/审批/来源；M7McpTest、M7CliEcosystemTest。不支持 OAuth、自动重连/重放、GET 常驻流/续传等，断连 UNKNOWN |
| Skills / FR-19 | skill/skill-filesystem 有多来源目录、YAML 自动发现和文件观察；Java 是可信宿主白名单与固定文件快照 | 支持受控加载/资源/脚本；M7SkillsTest、M7-skills-docker-results.json。无自动目录发现/监视或 Markdown 自行授权 |
| 网页与搜索 / FR-26 | web/tool-web 可配置 web_search/fetch provider；Java 只按宿主固定来源别名抓取 | 部分支持：web_fetch 逐次审批、不跳转、来源 URL/SHA/不可信标记/1MiB/10秒上限；M7ClosureTest。通用搜索提供方延期，不能接受模型任意 URL |
| LSP / FR-26.3 | 本轮 19 个入口不含 LSP，未逐项源码核验其实现 | 延期；未注册 LSP 工具，不声称已完成其上游源码审查；后续需要环境内路径、进程与诊断生命周期 |
| 附件/图片 / FR-05.2、08.6 | attachment/attachment-local 提供持久文件/图片策略；Java 已有文本 Artifact 冷读取 | 部分支持文本产物；M2/M5 产物测试。内置 OpenAI-compatible 不传输图片，图片输入延期，不能以文本引用称作图像模型支持 |
| 分叉、查询、全文搜索 / FR-13、24 | session-query/session-query-sqlite 使用 FTS5；Java 采用可丢弃子串索引而非 SQLite | 支持有界单会话 payload 全文子串索引/增量/损坏重建、查询/分叉/比较/导出/逻辑归档；M5SessionTest、M5CliTest、M7ClosureTest。不含分词/模糊排名/自动删除 |
| spill、投影与缓存 / FR-08.4、13.4 | spill/spill-policy 为大输出产物与有界预览；core/session 投影 | 支持 Java 有界产物回读/持久投影；M2/M5 产物与投影测试，索引不成为事实来源 |
| 配置组合 / FR-17 | preset/agent-presets 是共享装配；Java 为命名模型/Profile/Bundle/Preset 与 SDK 配置 | 支持 Java 组合与选择事件，不是复制上游配置文件；M6ConfigurationTest、M7CliEcosystemTest |
| 交付/展示 / FR-12 | fs/tool-present 的声明交付事实 | 支持事实报告/文件SHA/来源事件；M4CliIntegrationTest、M7CliEcosystemTest。Web Diff 为已记录 git_diff，不是实时自动执行 Git |
| 调度与横切扩展 / FR-25 | core/tools 有并发调用与顺序屏障；Java 默认串行、可替换 AgentLoopFactory/拦截链 | 部分支持：M8.2已交付宿主显式启用的有界只读工具并行、可替换调度、原序聚合及写/ASK屏障；M8SchedulingTest和M8.2-scheduling-docker-results.json。默认串行、子任务并发、压缩/超时/费用保护保持；M8.3已交付默认关闭的标准模型限流/非流式429重试插件，逐尝试落盘及预算，Windows295项及最终本地/Docker跨入口验收通过；工具重试未启用，绝不自动重试未知副作用或未决模型请求 |
| 沙箱、SDK、多入口 / FR-27 | sdk/server 是 stdio JSON-RPC 宿主；Java 提供嵌入式版本化 API 与本机 HTTP Web 后端 | 支持本地/Docker/SDK/CLI/Web；M7EnvironmentTest、M7SdkTest、M7WebTest、M7CliEcosystemTest、四组 Docker JSON。协议不是上游 JSON-RPC 兼容层，容器 PTY 不支持；Desktop UI 延期，本地宿主关闭/取消生命周期已有实现 |
| 语音、浏览器与桌面操作 / PRD1.4 | 非产品复刻范围，不在本轮 19 个入口核验范围 | 排除；无此工具，不以本机 Web API 代称桌面操作 |

延期项本阶段均不暴露模型工具、不启动后台资源、不伪造状态；未来引入时须单独定义状态机、持久化、权限与验收。上述部分支持和延期是 PRD 7.1 明确范围记录，不能将 M7 收口理解为全部 FR 子项均已实现。

M7历史本地验收见 [M7收口](../testing/M7-closure.md)，最新M8所选范围及发布门禁见 [M8收口](../testing/M8-closure.md)。远端 CI/macOS 实机、线上服务商、生产模型调用均未因本表而自动变成通过。

## M8.1实施更新（2026-10-15）

FR-07.3新增宿主searchFilter，默认排除→根.gitignore→用户有序规则，统一list/search/grep；metadata/链接/越权保护不可被否定，直接读取仍遵守原授权。支持语法、上限、CLI/SDK/子视图与worker确认见 [M8契约](../development/M8-contracts.md)，Windows269项及实际Docker结果见 [M8验收](../testing/M8-validation.md)。完整Git wildmatch和嵌套.gitignore仍不支持。本条是Java实现进展，不新增上游源码审查声明；FR-25仍待M8.2/M8.3。

## M8.2实施更新（2026-10-15）

FR-25.1/25.3按宿主显式配置交付只读调度，公共API/SDK1.8；准备和结算由调用线程保持单写者，工具主体有界并行，结果原序，写/审批屏障及取消/超时/UNKNOWN均有正反例。Windows280项及最终制品本地/真实Docker的CLI/Web/SDK链路通过，见 [M8验收](../testing/M8-validation.md)。框架不隔离任意Java插件，不强杀不合作线程；通用限流/重试仍待M8.3，FR-25整体仍是部分支持。本条只更新Java实现进展，不新增上游源码审查声明。

## M8.3实施更新（2026-10-16）

FR-25.2/25.3通过MODEL_EXECUTION拦截链与可替换ModelExecution服务交付标准模型限流/重试，API/SDK1.9；默认关闭，唯一重试所有者、次数/间隔/等待者/总期限/费用均有界，只有完整非流式429拒绝可重试。UNKNOWN、超时、SSE断流、工具及5xx不重放。Windows295项及最终JAR本地/实际Docker的CLI/Web/SDK重试与过滤回归通过，见 [M8验收](../testing/M8-validation.md)。原压缩/整轮超时/费用保护保持；全阶段待M8.4收口。此为Java实现进展，不新增上游源码审查或全平台/发布声明。

## M8.4收口更新（2026-10-16）

FR-07.3、FR-25已按M8.1～M8.3定义的Java支持子集完成阶段收口。Windows/Linux容器各295项，259个Java/POM哈希一致；七组最终JAR本地/实际Docker回归通过，详见[M8收口](../testing/M8-closure.md)。原表的部分支持/延期边界保持，工具重试、完整gitignore、任意Java隔离/强杀、前端/Desktop及发布CI不因此变为已实现。固定上游19个入口SHA与M7历史文件已复核保留，没有新的上游测试或全仓审查声明。下一步M9，本地阶段收口不等于PRD全部功能或发布完成。
