# M8 收口与验收边界

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](../development/current-state.md)及[全阶段审计](M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

2026-10-21 历史工具冻结说明：`scripts/verify-m8-closure.py` 的历史实现保留供阅读，CLI、导入及 runpy 均在读取证据前拒绝执行。上文运行命令仅记录当时验收方式，不是当前可运行入口。需要复现时使用验收记录中的原始版本及隔离副本；保留原始 M8 JSON 字节。当前 M10.5.9 收集器只验证新阶段，不能作为 M8 收口证明。

日期：2026-10-16。状态：M8已按所选范围完成本地验收与收口。M8是新增实施计划中的核心补齐阶段，所选范围为FR-07.3检索过滤、FR-25.1/25.3只读调度及FR-25.2标准模型限流/重试，不代表全部PRD功能或发布DoD完成。

## 范围与验收矩阵

| 范围 / 需求 | 正反例与事实来源 | 最终回归 |
|---|---|---|
| FR-07.3、AC-05 | M8SearchFilterTest、M8FilterConfigTest、M8FilterIntegrationTest：规则优先级/否定/资源边界、元数据与链接保护、版本及环境绑定、直接读取与检索过滤区别 | M8.4-filter-results.json；Windows/Linux全量 |
| FR-25.1/25.3 | M8SchedulingTest、M8SchedulingConfigTest：latch证明真实主体重叠/并发上限、原序聚合、注册只读+白名单+ALLOW、写/ASK屏障、默认串行与dry-run | M8.4-scheduling-results.json；M8.4-resilience-results.json |
| FR-25.2/25.3 | M8ResilienceTest、M8ResilienceConfigTest：默认关闭、唯一重试所有者、完整非流式429、预算/次数/间隔/等待者/期限、取消与卸载 | M8.4-resilience-results.json；Windows/Linux全量 |
| AC-03、NFR-10/11 | EventRequestJournalTest、M1AgentLoopTest、M8专项：每次prepared envelope先落盘，逐尝试前缀重建；最终HTTP报文逐项相同 | environment每侧4次，web/scheduling每侧3次，resilience每侧4次 |
| AC-06/09、NFR-04/12 | M8调度/重试及M3/M5/M7恢复：取消与不合作线程、UNKNOWN禁止重放，精确请求核实，历史唯一未决兼容 | Windows/Linux全量；subagent、web、resilience实机 |
| AC-07/08/15 | M3/M4/M8：审批/拒绝/dry-run、checkpoint失败主体0次、拒绝事实flush失败不再发送、版本与原子覆盖 | environment、skills、web、scheduling、resilience实机 |
| AC-10/11、NFR-05/12 | JsonlSessionRepositoryTest、M5SessionTest、M6StreamingTest、M8专项：单写锁/损坏拒绝/有界资源/脱敏/完整流校验后执行 | Windows/Linux全量；七组最终制品回归 |
| AC-12/13、NFR-13 | 插件作用域/生命周期及M8替换测试：可替换服务，停止后撤销，旧扩展引用拒绝；Loop仅依赖公共API | Windows/Linux全量 |
| AC-16 | 相同最终CLI JAR，文件/命令位于同一环境，CLI/SDK/Web、受控Skills和只读子任务沿用审批/日志/恢复 | 七组本地/真实Docker回归，JAR SHA与清理一致 |

完整切片契约及测试矩阵见 [M8-contracts](../development/M8-contracts.md) / [M8-validation](M8-validation.md)。实机调度事件证明装配和顺序；主体真实并发由latch用例证明，不把这些结果换算为性能提升。

## 证据与复验

最终Windows命令 `.\mvnw.cmd -B clean verify`；Linux为固定Java21镜像中的 `sh ./mvnw -B -Dmaven.repo.local=/tmp/m2 clean verify`。源码快照、平台、测试计数和日志各自独立记录于M8-results.json（slice=M8.4）及M8-linux-results.json。Linux是Docker Desktop Linux Engine中的构建环境，不是原生Linux桌面、WSL认证或远端CI。两侧要求相同Java/POM文件集及SHA、相同完整测试名、零失败/错误/跳过。

七份M8.4主动报告均使用最终Windows打包JAR：environment、skills、subagent、web、filter、scheduling、resilience。旧夹具内部M7/M8.1/M8.2/M8.3标识保留，新M8.4文件名与当前JAR SHA定义本轮回归；不覆盖历史JSON。生产JRE镜像固定摘要，pull=never/network=none。模型HTTP替身及MCP/网页传输在宿主，容器禁网不覆盖宿主网络。Linux构建容器为安装Git/准备依赖使用网络，与生产沙箱分开。

```powershell
.\scripts\collect-test-evidence.ps1 -Milestone M8 -Slice M8.4 -VerificationLog '.build-cache/M8.4-verify.log'
python scripts/verify-m8-closure.py
```

校验器需要Python3.11+，仅使用标准库（本机Python3.14）。收口校验器读取当前Windows/Linux证据、日志及源码，检查完整测试清单、逐文件哈希、七报告SUCCESS/真实Docker/清理、固定镜像、相同最终JAR SHA和报告不早于JAR，成功才写入 [M8-closure-results.json](M8-closure-results.json)。单独复用历史的295项或旧制品报告不能通过。该校验不代替运行测试与检查真实资源；输入均为可信宿主采集事实。

## 最终实际结果

Windows与Linux容器各295项测试，均0失败/错误/跳过，完整测试名一致；259个Java/POM哈希逐项相同。Windows日志 `.build-cache/M8.4-verify.log`；Linux快照和独立日志位置见M8-linux-results.json。API/SDK1.9，JSONL/schema仍1；本轮没有产品Java/POM改动，新增的是收口校验及证据/文档。

七份主动报告均SUCCESS、cleanupVerified=true，绑定最终JAR SHA `12a8c812fc90035d6d6a6e78da8f5de3af6d3dcea28d72d54af68a9ccf37207e`：

- [环境与SDK](M8.4-environment-results.json)、[Skills](M8.4-skills-results.json)、[只读子任务](M8.4-subagent-results.json)、[Web默认配置](M8.4-web-results.json)。
- [检索过滤](M8.4-filter-results.json)、[只读调度](M8.4-scheduling-results.json)、[模型限流与重试](M8.4-resilience-results.json)。

[CLI冒烟](M8.4-smoke-results.json)确认version/help正常、真实项目目录doctor就绪且不发送API请求；不含Wrapper的空目录正确报告未就绪，未将其当作产品失败或成功环境。[静态审计](M8.4-audit-results.json)确认9份M7验收文件的Git内容保持、固定上游19个入口SHA未变；仅复核已有固定快照，不新增上游全仓审查或兼容认证。

[校验器正反例](M8.4-verifier-results.json)确认当前证据通过，12组分别篡改的旧切片、失败/跳过测试、缺失suite/实机报告、源码不一致、旧JAR、未知清理、伪Docker、错误镜像、旧时间和错误日志SHA均被拒绝。这12组为脚本验收，不计入295项JUnit。校验输入与失败目录保留在.build-cache。

Linux首次准备停在非必需universe索引下载，终止本轮自有容器后保留原目录；改为main仓库、20秒HTTP超时/一次重试重新准备Git，完整原测试通过。缓存只读挂载并复制入容器，不携带宿主settings.xml/凭据；嵌套离线Wrapper夹具预备HOME分发及仓库映射。生产所有权标签及本轮Linux构建标签最终查询均无残留。

M8.1/M8.2/M8.3历史全量分别保存在M8.1-results.json、M8.2-results.json、M8.3-results.json；当前M8-results.json仅标识M8.4。M7证据与PRD原文保持。

## 保留限制与后续

- 过滤是明确gitignore子集，只读根.gitignore，不支持完整wildmatch/嵌套规则；检索隐藏不构成秘密隔离，显式read/Shell仍按原授权。
- 工具默认串行；宿主白名单、注册READ_ONLY、有效ALLOW同时成立才可并行。可信Java工具及拦截器须线程安全，不能强杀不合作线程；未知结果仍需人工核实。
- 标准重试默认关闭，只接受完整非流式429安全拒绝。工具、5xx、超时、传输未知和断流不重试。旧适配器显式429退避保持兼容；启用插件时使用单次发送入口，不叠加次数。429费用保留保守上界，不是账单；限流实例之间不共享服务商全局配额。
- 本地进程清理仍为ProcessHandle采样partial，无Windows Job Object保证；Docker不支持PTY，失败不回退宿主。Web仍为loopback/Bearer单操作者API，无前端，浏览器Origin策略不放宽。
- 没有付费模型请求、在线供应商兼容认证或公网发布。远端CI/macOS/Windows10/独立WSL验收仍未完成；M8收口不能代替PRD13第8条发布门禁。
- 下一阶段为M9发布准备与平台验证；没有Git remote时先完成本地可做部分，不自行创建远端/推送/发布。M10前端、M11 Desktop及M12候选不提前宣称实现。
