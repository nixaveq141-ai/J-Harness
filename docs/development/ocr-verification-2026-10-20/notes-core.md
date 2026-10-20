# 核心模块核实说明

基线 `337c150dca2bbfe193f8586dd79623797fbd8724`；500 条原意见逐条静态核实，完整结论在 `verdicts-core.json`。`partial` 的理由限定采纳范围；其余原文、严重度和建议代码均不能自动视为确认。没有实施产品修复，没有运行全量/三平台验收。

## 优先顺序与验收设计

1. **R1 生命周期和真实事实**：tool-settlement、audit-failure、cleanup-evidence、scope-revocation、subagent-writer、docker-unknown、terminal-lifecycle、process-output、approval-race、web-cleanup、web-locking。先为并发/失败路径建立可控制的 latch/故障注入，验证同一次调用至多进入一次、至多结算一次；迟到执行、关闭未退出和清理失败都不得降为确定未发生。真实 JSONL 写者验证中断不会毒化父通道；保留首因和 suppressed，不允许吞持久化异常继续模型请求。Web 展示快照失败仍必须执行资源关闭及占用结算，无法结算则明确 UNKNOWN。
2. **R2 事实存储与恢复**：fork-target-lock、artifact-lifecycle、sdk-init、sdk-binding、workspace-version、workspace-lease、session-fact-schema、web-fact-schema、archive-consistency、catalog-migration、catalog-concurrency、question-durability、planning-evidence、queue-history、terminal-capacity。以真实目录、两个进程/两个实例、半写/断电语义分别验证。forkTo 不能覆盖存在/正在写的 target；多文件/多事件失败不得留下可执行半成品。UNKNOWN 只能显式外部核实，不能变成功或自动重放。新状态/版本需要迁移策略；总历史上限和活跃上限分离时，墓碑与幂等索引仍需有界。
3. **R3 协议与边界**：mcp-protocol、model-stream-validation、model-routing、model-config、cost-contract、skills-validation、tool-schema、spi-validation、transport-contract、transport-bounds、preview-input、process-environment、run-control、patch-format、workspace-metadata。协议恶意帧/缺字段/超节点/未知后 RPC error、流取消晚订阅、终态多余工具均为负例；不发外部付费请求。脚本加载限额先于注册；写原文件保留安全权限。使用真实离线 Git/本机 HTTP 替身和最终 JAR 验证，不把替身成功冒充真实服务互操作认证。
4. **R4 展示与交互**：display-stream、diagnosis-facts、web-stream-page。临时展示失败不改变主请求，末帧/代理对/取消状态清晰；诊断未知优先于 not_started。分页顺序统一时间比较，SSE EOF 不是全事件已读完，应按稳定游标 GET 核对。模型路由装饰器必须保留实际展示与观测包装。
5. **R5 有界处理与检索**：session-read-bounds、search-index、tool-result-bounds、content-paging、compaction-bounds、compaction-protocol、search-filter、terminal-log、web-encoding、stable-wire。以最大合法规模与一条越界对照证明读取/编码/缓存峰值有界；避免只做事后 byte[] 长度检查。保留最近用户目标和最后一个工具协议组，压缩不把工具引用提升为 system 指令。Unicode casefold/snippet、UTF16 内容游标、行号游标分别测试；缓存可重建，事实 JSONL 不因缓存坏而修改。
6. **跨阶段安全验证**：sanitize-boundaries、test-no-replay、test-session-facts、test-preview、test-terminal、test-workspace、test-tool-schema、test-fixture-cleanup 必须跟对应产品修复一起交付，而非留到最后统一补。非空帧/marker/实际文件、启用重试的反例、跨进程锁是关键独立证据。
7. **R6 发布与收口**：dependency-convergence、ui-packaging、durability-scope。依赖集中约束只是可维护性提升，当前同版本不算已冲突；前端制品输入与包内清单应在候选门禁校验，纯后端构建不必强制 Node。明确 force 文件与父目录 fsync 的支持平台和保证差异，不能把进程退出与掉电保证混为一谈。

## 代表性拒绝结论

- OCR-0064 / 0075：不能把请求前溯源/审计写盘改为可忽略失败。
- OCR-0407：Docker 已显式判断 `uncertain`，exitCode=0 不会覆盖未知。
- OCR-0488：`post(rejection)` 无 method，走 202 空体分支返回，不会递归解析 SSE。
- OCR-0584：SecretRedactor 已复制列表，修改调用者列表不会改变其内部秘密集合。
- OCR-0688：CommandRequest 已拒 Shell 多元素 argv，不会静默只执行首元素。
- OCR-0709 / 0711：不能通过跳过平台权限测试或过滤 ACL 放松安全门禁。
- OCR-0744：会话支持多轮，不能把一个 turn 终态强制为会话永久终态。
- OCR-0792：累计节点、事件数、字节数是并行资源限额，不要求三个限额同时达到。
- OCR-1109：lease save 先 owner.flush，关闭后实际 writer 会拒绝，不能继续无锁保存。
- OCR-1114：空文本不等于不存在/删除，不能无条件改为 `/dev/null`。
- OCR-1149：同源自动进入是用户明确批准的单操作者本机边界，不擅自恢复手输 token。
- OCR-1153：实际 encode 返回 byte[]，length 已是字节数，并非 UTF16 字符数。

所有阶段仍待实施；静态核实不等于已复现全部场景，原 OCR 严重度不等于最终优先级。
