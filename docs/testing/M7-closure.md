# M7 收口与验收边界

日期：2026-10-15。M7 已按下表范围完成本地验收与收口，未实现项仍明确登记为部分支持/延期。本记录按 PRD 7.1 的范围决定规则收口 Java 实现，不表示复制上游全部产品能力。具体支持程度见 [固定提交对照](../requirements/upstream-capabilities.md)；接口和限制见 [M7 契约](../development/M7-contracts.md)。

| 范围 / 需求 | 实现与验收证据 |
|---|---|
| FR-19 / AC-05、09、12 | MCP stdio、HTTP JSON/SSE 与 Skills 文件/资源/固定脚本；M7McpTest、M7SkillsTest；M7-skills-docker-results.json |
| FR-20 / AC-06、12、13 | 独立只读子任务、父单写者、预算/取消/费用池、来源与 UNKNOWN；M7SubagentTest、M7ChildEnvironmentTest；M7-subagent-docker-results.json |
| FR-23 Todo/Plan | 分别事件化状态、revision、审批与真实工具事件引用；M7ClosureTest；Goal/Schedule 明确延期 |
| FR-24 / AC-09、10 | 查询/分叉/导出；新增单会话全文子串索引、增量/损坏重建、比较与逻辑归档；M7ClosureTest、M5SessionTest、M5CliTest |
| FR-26 | 固定来源网页获取、逐次审批、URL/SHA/不可信标记、上限/重定向拒绝；M7ClosureTest；搜索提供方/LSP 延期 |
| FR-27 / AC-16、05～07、09～13、15 | 本地/Docker、SDK、CLI 统一生态装配与 Web 启动；M7SdkTest、M7EnvironmentTest、M7CliEcosystemTest、M7WebTest；M7-docker-results.json、M7-web-docker-results.json |
| FR-21、22、25、27.5 / 高级项 | Workflow、PTC、自动只读工具并行、通用重试/限流插件、Desktop UI 的取舍和限制单列在能力对照；没有实现的部分不记为通过 |

Windows 全量命令为 `.\mvnw.cmd -B clean verify`，包含格式检查、-Xlint:all -Werror、JUnit 和打包。精确测试计数、源码 SHA 与日志 SHA 见 [M7-results.json](M7-results.json)。Linux 全量使用独立 Java 21 容器与相同 Java/POM 快照，平台、镜像、测试报告和结果单独保存在 M7-linux-results.json；不将容器内 Linux 结果称作远端 CI 或原生 Linux 桌面结果。

四组主动实机夹具均使用最终 shaded CLI JAR，核对每份结果中的 JAR SHA：执行环境/SDK、Skills、只读 Subagent、打包 CLI serve→Web→SDK→Docker 联合链路。最后一组另对照本地环境，检查先审批后真实写入、相同 requestId 不重复运行、3 个实际 HTTP 模型请求均由日志前缀重建、环境关闭和宿主退出。全部模型为 FakeLlm 或本机 HTTP 替身，未发送付费请求。

新增 9 个 JUnit 用例：M7ClosureTest 6 个、M7CliEcosystemTest 2 个、M7WebTest 增加 1 个。专项发现并修复子任务关闭父审批服务，以及已完成审批误消费后续输入两项问题；保留了“子任务结束后父任务再次审批 MCP”的实际组合断言。离线 Web 核实测试验证活动 catalog、占用 SDK 写锁、未决模型请求均拒绝；核实后保留旧状态且不重放。

上游官方固定提交归档已实际获取，19 个能力入口的路径/SHA/行数见 M7-upstream-review.json。只核对入口与关键声明，没有运行上游测试或完成全仓安全审计，也没有声称 Git checkout/rev-parse 成功。

发布门禁另行保留：没有 Git remote，Windows/Linux/macOS 远端 CI 未触发，macOS 尚无实机证据。原生 Windows 进程树清理仍为采样保障，不承诺 Job Object；Docker 生产运行网络禁用，Linux 构建容器为安装 Git/获取构建依赖使用网络，两者不能混称。在线 MCP 服务商、生产模型和公网部署没有在本轮验收。

后续工作可选择前端产品化或发布准备。前端尚未实现；若启动，应先基于现有认证、任务、审批、事件和历史 Diff API 确定交互范围。发布前需设置远端、运行平台 CI 并人工核对平台差异。延期能力进入独立需求阶段，不能把 Todo/Plan 或委派改称 Workflow、Goal 或 Schedule。

最终实际结果：Windows 与 Linux 各 260 项测试，均 0 失败、0 错误、0 跳过；236 个 Java/POM SHA 完全一致。四份实机 JSON 均 SUCCESS、cleanupVerified=true，并绑定同一最终 JAR SHA `4f784f489e8d107c8822495169acbfb22372f611bfa83d4887bf5809f7bb8475`。Docker 所有权标签和 Linux 验收标签最终查询均无残留容器。Windows JDK 版本、Linux JDK/Git 版本及完整测试名分别记录于对应 JSON。

Linux 最后一轮为实际离线 Wrapper 构建夹具补齐 HOME 中的缓存分发和仓库映射；没有修改该用例的构建、修复、再次编译成功断言。源码、API、脚本示例和文档通过证据哈希关联，不以旧切片的 251 项或旧 JAR 代替最终结果。
