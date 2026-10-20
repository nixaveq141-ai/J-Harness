# Delivery 补充 80 条核实

2026-10-20。分配自 delivery 代理的明确 80 个 ID，全部逐条读取原意见及对应源码。结论为 accepted 24、partial 32、rejected 24，无整条 unverified。该组仅写本说明与 `verdicts-delivery-extra.json`；总控现已将 80 条按 ID 合并到主 delivery ledger，补充文件保留作一致性复核。没有修改产品源码或历史证据。

## 优先并入阶段计划

### 第一阶段：付费评估辅助入口与候选门禁

- **paid-evaluation-isolation / P1**：2170、2174、2175。`live-evaluate.py` 的独立 oracle JVM 继承真实 `DEEPSEEK_API_KEY` 环境并加载模型生成类，stdout 又原样进入 ledger；模型输出目录在可信 oracle 的 classpath 前；`allowed()` 的代码子串黑名单与实际 edit 字段也不能提供隔离。先阻断不安全辅助入口或在不发起付费请求的本地反例中加固：最小环境、不可信编译代码独立隔离、可信驱动不可遮蔽、返回值与脱敏输出验证。验收必须包含读取环境、同名 oracle、Unicode/额外字段等反例；不重新执行真实付费评估。
- **paid-evaluation-accounting / P1**：2171、2177 部分采纳。缺失/空/损坏日志不得将未知已用成本默认为 0 并永久记 recorded；异常/取消仍保留本次有界脱敏输出和保守成本。600 秒是总预算，不能直接把请求 90 秒相加推导“690 秒合法”，不采用单纯延长到 690 秒的建议。
- **python-assert-gates / P1/P2**：2183、2204。候选冒烟和启动器测试均无拒绝 `-O` 的入口，优化后 assert 被剥离仍写 PASS。统一显式 require 或入口拒绝优化，负例必须证明 `-O` 不产 PASS。
- **candidate-manifest-boundary / P1**：2184。manifest.files 未复用 ZIP 名称的边界校验且未覆盖全部安装文件。验证条目规范化/边界/完整集合，覆盖将执行的启动器；用越界名称、缺启动器、未列文件等纯本地候选反例验证。

### 第二阶段：现行取证与资源生命周期

- **native-compat-receipt / P2**：2191 及 2190、2192、2194 的合理部分。固定 windows-compat.json 只成功末尾写，失败会遗留上一轮 PASS。增加本次身份/开始态和失败收据、原子发布、工具链/历史对象诊断；JVM stderr 必须有界脱敏。不要仅以 mtime 证明来源，也不把本地 `git show` 说成远端凭据请求。
- **native-compat-cleanup / P2**：2193。专用 credential target 在 Python 生成并传入；JVM 超时/崩溃后外层仍能仅清理本次目标并报告结果，不能删其它凭据。
- **native-receipt-consistency / P2**：2062，2044 仅部分采纳。producer 本已验证 unavailable/cleanup=false，workflow 也确实构造 DBus 缺失/锁定环境；合并门禁重复核对相应收据一致性，拒绝矛盾字段，不能说三个场景只是换参数伪造。
- **evidence-java-case-counts / P2**：2038、2073 部分采纳。当前共享 collector 的 XML 声明计数与实际 testcase 数/必需集合要一致；不同 milestone 使用各自门禁。明确拒绝“每个 module 至少一份 suite/len(suites)==len(modules)”建议，项目有多 suite 及 testkit 集中测试，强加会错误拒绝正确 reactor。
- **frontend-sbom-graph / P2**：2116、2117、2118。同 name/version 多安装路径不能重复 bom-ref；只将实际安装的 optional/peer 边纳入；根 optional 也要连根并验证可达。当前六个生产依赖没有触发这些输入，修复以最小合法 lock 夹具验证，不编造现有发布失败。
- **frontend-sbom-format / P2/P3**：2119 及 2115 合理部分。ID/表达式/name 正确分类；缺 SHA512/resolved/version 应明确拒绝不支持的来源，不能为支持 file/git 而静默跳过完整性。
- **ci-gate-negative-tests / P2**：2148、2149。把负例绑定具体失败原因，并补 paid/dirty、Java 计数、UI/commit/用例集合不一致等真实 M11 准入不变量。保持一个字段一反例与正例。
- **launcher-timeout-cleanup / P2**：2205。启动就绪超时后有界关闭自有 PowerShell/Java 进程树并保留原诊断；不能仅 wait 超时后遗留锁/端口。

### 第三阶段：夹具断言准确性

- **fixture-question-boundaries / P2**：1941、1942。写入越权检查读错文件，应直接断言 must-not-write.txt 未产生；skip 场景核请求体 skip/selected 与持久结果。
- **fixture-keyboard / P2**：1943。未 disabled 不等于能键盘聚焦，使用实际 focus/Tab 与 activeElement。
- **fixture-controlled-shutdown / P2**：1944。在预期 kill 前登记精确 origin，避免把已知停机只读噪音误当未预期异常；不扩大异常忽略规则。
- **fixture-search-xss / P2**：1958。带事件处理器的已存正文命中、明确等待结果、DOM/字面文本与未执行同时检查；仅 fill script 标签加检测全局变量不足。
- **fixture-search-redaction / P2**：1953、1954 部分采纳。统一扫描实际读过的分页/范围响应，并把 Web token 也纳入模型请求敏感集合。
- **fixture-search-rebuild / P2**：1959 部分采纳。除了 rebuiltSessions，还检查重建后实际 boost 命中/数量；缓存损坏注入是有意反例，不取消它。
- **fixture-pagination-bounds / P2**：1963。events helper 的 do/while 缺游标前进/页数边界，外层 until 无法打断内部无限 await；每页检查状态、结构、前进并设上限。
- **fixture-turn-output-redaction / P2**：1965。start 清空 output，最后一次扫描不含此前宿主或维护命令；各次进程分别扫描或聚合到独立有界安全缓冲。
- **paid-evaluation-evidence / P2**：2172。audit-only 不覆盖旧 oracle 结果；先核快照摘要，不一致保留 STALE 或追加新审计身份。
- **paid-evaluation-goal-evidence / P2**：2176。calculator goal 要求先读既有测试，require-read 也应包括该测试；broken-project 分支维持原范围。

其它 partial 的 P3 诊断、socket 超时、时间戳 offset、一次读取快照和原子发布等建议按 JSON repairGroup 合并进现行基础设施，不逐个追溯修已结束阶段的 collector。

## 必须保留的收窄/误报

- 2173 的模型流式 stdout 冒充 APPROVAL 利用前提不成立：该 live-evaluate config 没启用 streaming，CliApplication 仅 `streaming==true` 才将模型增量原文打印到 stdout。未来若打开流式再做结构化审批关联；不能以此声称当前已绕过。
- 1950 是 `synthetic-sdk-UUID`，刻意写 large.txt 做脱敏反例，不是真实 API key 泄漏；忽略缓存保留用于取证。
- 1964 不应因模型请求上限 80 秒就允许关闭宿主等 80 秒，宿主本身有有界关闭约束。
- 1967/1968 的 slow-current/slow-exit 在现行场景各只创建一次，只有 slow-current 释放一次，不能凭假想重复 goal/重复 send 推断当前泄漏或错误通过。
- 1969 的 CLI verify-request 会拒绝已结算事件，且最后一次 held 请求有停机/socket 前提；1971 的 reload 在核对按钮清空输入之前，原意见把前后顺序倒置。
- 旧阶段收据允许不同 fixture 有不同字段；2069 不能强制 launcher 等无截图收据也有非空 screenshots/sourceSha256。缺文件异常非零不等于漏门禁。
- 2203 的外部 CLI 参数来自操作者明确指定，不能与报告内不可信派生路径混为一谈，强制所有下载证据/输出在 ROOT 会破坏合法离线审计。
- 2197 的 BufferedReader.read(n) 不是一般TCP分片就返回短读；真正问题是无 socket 超时。仅设置 HTTPServer.timeout 也不等于给连接读取加超时。

## 验证说明

只做源码/契约/调用链静态核实及 JSON 覆盖/枚举/ID/路径检查。尝试用系统 `py -3 -B` 运行内存 SBOM 反例时 launcher 报 `No installed Python found`，该反例没有实际执行，不计任何产品/脚本测试通过；上述 SBOM 结论来自生成器逐安装路径/边构造的源码证据。未运行付费请求、真实凭据写入或产品构建。
