# R0 新审查意见核实

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](current-state.md)及[全阶段审计](../testing/M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

2026-10-21 实施接续：本页保留 2026-10-20 的核实快照与当时状态，后续修复、实际验证及采纳边界调整见 [复审采纳范围实施台账](OCR-R0-rereview-fixes-2026-10-21.md)。其中 006 的父级穿越实际由策略层先返回 `INSPECTION_DENIED`，其余四个现有夹具路径为 `PATH_DENIED`；实施按每条路径核精确错误码，不照搬统一码值。

会话：eed6a134-1d3b-4e89-adaa-a0e9b2ba1f19。日期：2026-10-20（Asia/Shanghai）。基线：1637fc01d759f0b4d58d0630f58892fba0ce6619。

**37 条意见已逐条核实：采纳 26，部分采纳 10，不作为缺陷采纳 1。** 这些是意见判断，不是已完成修复数；本轮不实施产品/门禁代码修复。

原审查选中 44 个文件。原始导出位于忽略缓存，已核对 session_id、complete、37 条及输入区间；[原文与建议代码](ocr-r0-rereview-2026-10-20/opinions.json)去除内部 thinking 后完整保存。逐条裁决及合并批次见 [verdicts.json](ocr-r0-rereview-2026-10-20/verdicts.json)，受控复核结果见 [probes.json](ocr-r0-rereview-2026-10-20/probes.json)。

## 主要结论

- 跨平台截图路径（035）为确定的发布验证阻断，归 P1；临时根路径别名（032）已在 Windows junction 下命中同一 KeyError，原生 macOS 未运行。
- 历史模块导入冻结（001/002/008）同根因，应合并修复；没有为复现而重写任何历史 JSON。
- XML 单次有界读取/编码（009/010/017）、manifest_name 路径（011）、ZIP 截断前内存分配（030）需补强。ZIP 原文的返回值描述不准确，风险方向成立。
- finalizer（036）改写已绑定输出，当前collector拒绝是正确行为，恢复工具应调整来源链，不能放松哈希检查。Firefox 本地可用性（037）缺本轮绑定；它不会直接让严格三引擎CI或M11通过。
- 进度/摘要（004/005/007）需改进，但不能改写静态审查快照、补造旧回执，或把明确的手工摘要当作已存在的发布门禁绕过。
- SDK固定两语言（012）不是当前功能错误；四条死导入及循环不变量属于低优先级清理。

本轮未运行真实浏览器、Java全量、远端CI或付费模型。没有将前次80项测试通过解释为这些新增场景已被覆盖，也不把原484条R0关联意见与这37条重复相加成完成率。M10.5.9仍LOCAL_PARTIAL_CI_PENDING，m11Eligible=false。

## 合并后的建议顺序

| 批次 | 优先级 | 关联意见 | 当前状态 |
| --- | --- | --- | --- |
| 先修跨平台集成阻断 | P1 | R0R-035 | 未修复 |
| 冻结导入路径并同步历史说明 | P1 | R0R-001、R0R-002、R0R-003、R0R-008 | 未修复 |
| 有界单次读取、解析与归档边界 | P1 | R0R-009、R0R-010、R0R-011、R0R-013、R0R-017、R0R-018、R0R-023、R0R-024、R0R-025、R0R-030 | 未修复 |
| 精确断言、矩阵入口与平台负例 | P1 | R0R-006、R0R-026、R0R-029、R0R-032 | 未修复 |
| 超时树清理、主次异常和本轮探测 | P1 | R0R-019、R0R-020、R0R-022、R0R-028、R0R-037 | 未修复 |
| 恢复动作的来源与产物身份 | P2 | R0R-027、R0R-036 | 未修复 |
| 独立实施进度与证据摘要 | P2 | R0R-004、R0R-005、R0R-007 | 未修复 |
| 保留原始失败与控制台诊断 | P2 | R0R-015、R0R-016 | 未修复 |
| 顺带清理死导入与重复读取 | P3 | R0R-014、R0R-021、R0R-031、R0R-033、R0R-034 | 未修复 |

各批次保持原R0工作包归属；先修确定跨平台失败与历史冻结，再补读取/解析、生命周期、身份链和独立回归。修复时必须先保留能命中原实现的负例，再验证修正，不删除断言、不跳平台、不重写历史结果。

## 逐条判断

### R0R-001 · 采纳 · 历史采集器导入绕过冻结

- 位置：scripts/collect-m1054-evidence.py:4；原严重度：high；核定优先级：P1。
- 判断：导入守卫确实被跳过。受控探针在第一次历史日志读取处截断，未允许任何历史写入。真正覆写还要求其余历史输入和断言全部满足，不能仅凭日志存在就声称本机已覆写。
- 建议范围：对已冻结的模块阻止导入执行，或把整个主体移入main使导入无副作用；补importlib/runpy负例。
- 取证：源代码核对及 python:scripts/collect-m1054-evidence.py

### R0R-002 · 采纳 · M9 verifier 的相同导入缺口

- 位置：docs/testing/tools/verify-m9-evidence.py:11；原严重度：medium；核定优先级：P1。
- 判断：M9主体、argv读取及最终写入仍位于模块级；main专用守卫不约束importlib。需要旧缓存和全部断言通过才会触及历史写入，未实际运行该旧主体。与001/008同根因。
- 建议范围：冻结导入路径，或安全封装主体；不为复核而真实重写历史JSON。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-003 · 采纳 · 冻结后的旧文档与提示未同步

- 位置：scripts/verify-m8-closure.py:8；原严重度：low；核定优先级：P3。
- 判断：M8-closure.md仍列出可执行收口命令及成功写JSON的描述，当前CLI却固定exit2；collect M10.5.9不能复核历史M8的295项。文档和提示确实应说明冻结边界。
- 建议范围：保留历史结果，标注命令为历史操作；区分历史verifier与新阶段collector，不把M10.5.9证据当M8重验。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-004 · 部分采纳 · 执行进度与派生Markdown缺少同步校验

- 位置：scripts/verify-ocr-review.mjs:81；原严重度：medium；核定优先级：P2。
- 判断：仅在内存修改progress.description后，只读验证仍成功且plan.md不变，过期生成物缺口成立。但将可变执行进度直接加入原始静态审查result.inputs，会重写应保留的原核实快照，不照搬该方案。
- 建议范围：单独校验执行进度schema和生成Markdown，使用独立实施产物哈希；保持原核实快照与动态进度分离。
- 取证：源代码核对及 node:staleProgress

### R0R-005 · 部分采纳 · 进度自由文本和结构状态未校验

- 位置：scripts/verify-ocr-review.mjs:131；原严重度：low；核定优先级：P2。
- 判断：换行可破坏派生文档结构，status与tasks的结构也缺验证，建议方向合理。进度文件是仓库内人工输入，不应夸大为外部恶意Markdown注入漏洞；只检查status非空也不足以保证状态合法。
- 建议范围：约束tasks对象、任务ID和status枚举，description限制类型/长度并归一化换行，再检查生成物。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-006 · 采纳 · 路径拒绝用例只核HTTP 400

- 位置：frontend/tests/content.mjs:109；原严重度：medium；核定优先级：P2。
- 判断：contentRoute把多种HarnessException及IllegalArgumentException映射为400，单独400无法证明五个测试命中路径权限拒绝。原意见与R0-03精确错误码目标一致。
- 建议范围：对各类拒绝同时核PATH_DENIED和HTTP状态，并让合法路径正例继续通过；不能只改登记标签。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-007 · 部分采纳 · 手工验收摘要的证据关联不足

- 位置：docs/testing/OCR-R0-2026-10-20-results.json:12；原严重度：medium；核定优先级：P2。
- 判断：gateTests/frontend缺少逐命令回执和日志关联，未来应补强；但该JSON明确声明手工摘要、不是release/runner receipt，未被门禁读取正是它不会授予PASS资格。java已有command/log/logSha256。不能据此认定当前发布门禁已被该摘要绕过。
- 建议范围：后续新运行输出逐命令结构化回执并链接；原手工摘要保持历史说明，不能补造旧runId/退出码，也不能简单接入发布门禁当可信来源。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-008 · 采纳 · 多份旧脚本的同根因导入缺口

- 位置：scripts/collect-m1058-evidence.py:4；原严重度：medium；核定优先级：P1。
- 判断：M10.5.8的受控导入同样进入历史日志读取而非exit2。其余模块级主体需同组覆盖；已有CLI-only测试不足。与001/002合并实施，不计三个独立事故。
- 建议范围：统一冻结导入或封装所有模块级主体，逐脚本补runpy/importlib负例及历史字节不变检查。
- 取证：源代码核对及 python:scripts/collect-m1058-evidence.py

### R0R-009 · 采纳 · XML大小检查晚于全量读取

- 位置：scripts/evidence_checks.py:48；原严重度：medium；核定优先级：P1。
- 判断：suite_snapshot先read_bytes再比较16MiB；受控对象证实全量读取发生后才抛Unsafe XML report，内存界限不成立。
- 建议范围：文件流只读limit+1再校验；对真实大文件/受控reader补读取上界断言。
- 取证：源代码核对及 python:xmlReadLimit

### R0R-010 · 部分采纳 · UTF-16绕过DTD字节扫描

- 位置：scripts/evidence_checks.py:49；原严重度：medium；核定优先级：P2。
- 判断：无害UTF-16实体样本被接受并展开，字节扫描缺口已复现。未验证UTF-32，也未执行膨胀攻击；现代解析器自身可能限制实体放大，不能从本探针推出任意无上限OOM。
- 建议范围：明确只支持预期UTF-8/ASCII兼容编码并在解析前拒绝DTD/ENTITY，或采用可靠禁DTD解析方案；同时保持单次有界快照。
- 取证：源代码核对及 python:utf16Dtd

### R0R-011 · 采纳 · manifest_name未经过路径验证

- 位置：scripts/evidence_io.py:135；原严重度：low；核定优先级：P2。
- 判断：隔离归档传入../escaped.json后，清单落在暂存目录之外而归档仍发布。当前调用方使用安全默认值，因此是helper接口缺口，不能声称已有外部可控调用被利用。
- 建议范围：在创建目录/写入之前验证manifest_name与普通归档名同一规则，并测绝对路径/穿越/重复目标。
- 取证：源代码核对及 python:manifestEscape

### R0R-012 · 不作为缺陷采纳 · 两种SDK语言的固定数量不是当前缺陷

- 位置：scripts/evidence_checks.py:28；原严重度：low；核定优先级：P3。
- 判断：产品支持范围、组件契约及总计58项均固定为TS/Python两种语言；当前len==2与精确集合双重约束没有错误拒绝合法当前证据。未来扩语言本就需要同步总数、契约、夹具等多个入口。
- 建议范围：可选去冗余重构，但不作为本轮功能缺陷或R0必修项；不能只改此一处就宣称支持新增语言。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-013 · 采纳 · 日志哈希全量读入内存

- 位置：scripts/evidence_run.py:90；原严重度：low；核定优先级：P2。
- 判断：run/validate都对不受输出尺寸上限约束的命令日志read_bytes，确实有随日志大小线性分配内存的问题。
- 建议范围：用流式文件摘要，并明确日志大小/磁盘预算及超限失败记录；不要只扩大内存。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-014 · 采纳 · evidence_run残留sys导入

- 位置：scripts/evidence_run.py:14；原严重度：low；核定优先级：P3。
- 判断：当前模块没有sys名称引用；建议属于可选代码清理，不是门禁绕过。
- 建议范围：移除死导入，随相关实质修复一起交付即可。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-015 · 采纳 · Windows失败尾日志可能覆盖原错

- 位置：.github/workflows/ci.yml:50；原严重度：medium；核定优先级：P2。
- 判断：wrapper可在创建verify.log前失败，pwsh的Get-Content在stop模式下会再报不存在。有效CI的正常路径不受影响，但异常诊断被二次错误干扰。
- 建议范围：先保存原退出码，仅在日志存在时输出尾部，最终按原退出码结束。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-016 · 采纳 · POSIX及前端失败诊断退化

- 位置：.github/workflows/ci.yml:54；原严重度：low；核定优先级：P3。
- 判断：wrapper只把命令输出写入文件，失败时作业控制台通常只见Python错误；artifact仍上传，所以是诊断可用性问题，不是假通过。
- 建议范围：受限输出已脱敏日志尾部，保持原退出码；兼顾无日志的启动失败。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-017 · 采纳 · release collector先进行无界XML解析

- 位置：scripts/collect-release-evidence.py:38；原严重度：medium；核定优先级：P1。
- 判断：ET.parse(report)确实先于suite_snapshot，受限读取还未执行就已解析；并且同文件读两次。不能照搬建议中的再次path.read_bytes，它仍存在第二次无界读取及版本漂移。
- 建议范围：让有界快照返回已验证raw/root/Java版本信息，后续直接复用同一份数据，不先ET.parse也不重新读取。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-018 · 采纳 · release日志入口缺路径边界

- 位置：scripts/collect-release-evidence.py:28；原严重度：medium；核定优先级：P2。
- 判断：ROOT / argv会接受绝对路径或..；缺参数也在撤销旧状态前IndexError。存在参数/边界不一致，但后续hash匹配仍是必需条件，未证明任意文件内容泄露。
- 建议范围：用argparse和bounded_path约束日志输入，明确缺参/启动失败时的状态行为。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-019 · 部分采纳 · 清理失败取代收据中的主异常类型

- 位置：scripts/smoke-candidate.py:296；原严重度：medium；核定优先级：P2。
- 判断：双重故障注入得到RuntimeError，原PrimaryFailure仍保留在__context__异常链，所以原文说根因完全丢失过强；但failureType只记录清理错误，结构化收据丢失主次关系成立。
- 建议范围：保留primary与secondary失败信息；仅无主异常时由清理异常决定失败类型，不把清理失败标为成功。
- 取证：源代码核对及 python:cleanupException

### R0R-020 · 采纳 · HTTP夹具异常Content-Length诊断

- 位置：scripts/smoke-candidate.py:148；原严重度：low；核定优先级：P3。
- 判断：缺头时int(None)会TypeError，非数字会ValueError；原文KeyError不精确，但缺少显式协议响应和诊断的方向合理。被测CLI目前生成正常请求头，不能称已发生主路径失败。
- 建议范围：明确处理缺失/非数/越界长度，返回有界400或413并记录协议失败；保留非法请求负例。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-021 · 采纳 · release collector残留json导入

- 位置：scripts/collect-release-evidence.py:68；原严重度：low；核定优先级：P3。
- 判断：json.dumps已移除，json导入未使用；仅为代码清理。
- 建议范围：随相关修复移除死导入，不计独立功能修复。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-022 · 采纳 · 矩阵超时没有清理整个进程树

- 位置：scripts/run-workbench-matrix.mjs:29；原严重度：medium；核定优先级：P1。
- 判断：当前kill仅针对直接node子进程；浏览器/Java后代不保证随父退出。这是R0记录已披露、尚未收口的资源问题，不是本轮首次发现。
- 建议范围：隔离进程组/作业所有权，超时和输出超限都按有界树清理并验证实际退出；不能仅改变signal或声称干净。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-023 · 部分采纳 · 有界读取的并发路径替换窗口

- 位置：scripts/workbench-evidence.mjs:35；原严重度：low；核定优先级：P2。
- 判断：逐段lstat与readFile之间不是原子操作，并发替换链接的缺口在设计上存在；未动态复现竞态，当前威胁范围是可信本地证据流程。O_NOFOLLOW仅保护末级文件，读取后realpath也不能完整消除检查后换回的竞态。
- 建议范围：明确并发/所有权假设；若要抵御主动替换，使用目录句柄/逐级不跟随等方案并补可控竞态测试，勿把简单补丁称为完整跨平台安全边界。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-024 · 部分采纳 · 源码快照错误复用非空证据规则

- 位置：scripts/workbench-evidence.mjs:43；原严重度：medium；核定优先级：P2。
- 判断：临时Git库内合法空frontend/public/.gitkeep确被拒绝。大文件界限可为有意资源契约，当前未发现实际超限源码，不能要求取消全部限制。
- 建议范围：源码读取允许空文件并单独定义资源上界/流式摘要；证据JSON/截图仍保留非空和大小规则。
- 取证：源代码核对及 node:emptySource

### R0R-025 · 采纳 · 契约加载绕开strictJson/schema

- 位置：scripts/workbench-evidence.mjs:26；原严重度：low；核定优先级：P2。
- 判断：复制helper后导入含重复browser键的契约成功，后值空数组覆盖前值。其他gate还会检查硬编码计数，所以不能据此直接断言全CI已空checks通过。
- 建议范围：契约也走strictJson并校验精确fixture集合、每组非空唯一case，作为防误编辑保护。
- 取证：源代码核对及 node:duplicateContract

### R0R-026 · 采纳 · validateMatrix入口缺独立回归覆盖

- 位置：scripts/test-workbench-evidence.mjs:7；原严重度：medium；核定优先级：P2。
- 判断：现有单测没有调用validateMatrix及其CLI。隔离合成完整矩阵通过，六种单字段/结构变异被拒绝，说明当前逻辑有效但既有测试确未锁定它，尤其缺完整矩阵正例。
- 建议范围：把完整正例、子集/重复/摘要/路径/runId负例及只读CLI覆盖纳入正式回归，不能只测试同一个record函数。
- 取证：源代码核对及 node:matrixProbe

### R0R-027 · 部分采纳 · 采集时间与最终化时间应明确区分

- 位置：scripts/finalize-workbench-matrix.mjs:6；原严重度：low；核定优先级：P3。
- 判断：finalizer的capturedAt确实为组装时刻；record原采集时间和runId仍保留，当前没有证据表明它声称重新执行测试，故不是已证实的伪造新运行。
- 建议范围：可用finalizedAt及record采集时间范围提高可读性；不要把矩阵组装时间直接等同每条测试时间。
- 取证：源代码核对及 node:finalizerOutput

### R0R-028 · 部分采纳 · POSIX超时退出竞态影响收尾

- 位置：scripts/evidence_run.py:86；原严重度：medium；核定优先级：P2。
- 判断：受控POSIX分支注入ProcessLookupError后只调用过一次wait，异常替代预期超时，控制流缺口成立。未跑真实POSIX进程竞态，也不能断言该情况下必然僵尸到解释器退出。
- 建议范围：忽略已消失的进程组但仍在finally等待/回收，区分实际清理失败；补竞态负例。
- 取证：源代码核对及 python:posixTimeoutRace

### R0R-029 · 采纳 · reparse测试没有击中声称分支

- 位置：scripts/test-evidence-io.py:37；原严重度：medium；核定优先级：P2。
- 判断：测试把Path.is_symlink固定为真，第一条判断就抛错，无法覆盖junction或st_file_attributes分支；注释所称Windows分支覆盖不成立。
- 建议范围：分别控制symlink/junction/reparse属性，验证删除对应保护后负例会失败，避免靠注释声称覆盖。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-030 · 部分采纳 · ZIP声明尺寸与真实内存开销不一致

- 位置：scripts/smoke-candidate.py:78；原严重度：medium；核定优先级：P1。
- 判断：原文archive.read一直返回全部解压数据不准确：本机Python返回值按声明尺寸截断。但伪造声明1字节、真实deflate为16MiB时，whole-read峰值约47.7MB，分块约71.7KB，截断前的分配风险已实测。
- 建议范围：采用有界分块读取并核实际消费/展开预算和完整性，ZIP/JAR两层都覆盖；不要依赖中央目录声明值作为内存上界。
- 取证：源代码核对及 python:zipDeclaredSize

### R0R-031 · 采纳 · smoke单测残留copy导入

- 位置：scripts/test-smoke-evidence.py:2；原严重度：low；核定优先级：P3。
- 判断：当前测试无copy名称引用；仅为代码清理。
- 建议范围：随相关测试修复移除即可，不计独立安全缺陷。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-032 · 采纳 · 临时根路径别名导致哈希测试KeyError

- 位置：scripts/test-m1059-gates.py:123；原严重度：medium；核定优先级：P1。
- 判断：Windows junction模拟别名根后，直接运行同一测试方法得到KeyError；聚合器以resolve根生成key，而测试拿未resolve路径索引。未运行原生macOS，结论适用于临时根含别名的条件。
- 建议范围：统一测试/报告路径规范，使用target.resolve()或在setup规范化directory；保留别名根负例，勿移除哈希断言。
- 取证：源代码核对及 node:linkedTemporaryRoot

### R0R-033 · 采纳 · 主collector残留json/ET导入

- 位置：scripts/collect-m1059-evidence.py:13；原严重度：low；核定优先级：P3。
- 判断：当前文件已改用共享JSON/XML helper，无json或ET名称使用；仅为代码清理。
- 建议范围：随相关collector修复清理死导入即可。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-034 · 采纳 · 循环内重复加载case契约

- 位置：scripts/collect-m1059-evidence.py:97；原严重度：low；核定优先级：P3。
- 判断：每个浏览器record重读同一契约多余。当前末尾源码/快照一致性仍会拒绝普通漂移，不能单凭重复读取断言已误认证。
- 建议范围：循环前读取一次并保留同一份快照，循环内复用，减少读放大和版本歧义。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现

### R0R-035 · 采纳 · POSIX截图路径与新门禁不兼容

- 位置：scripts/run-workbench-matrix.mjs:38；原严重度：critical；核定优先级：P1。
- 判断：search/settings/extensions均以Windows分隔符删除root；POSIX路径推演得到绝对路径，新runner前缀检查确定拒绝。该平台集成回归成立，原critical宜归P1发布验证阻断，不是P0安全/数据事故。
- 建议范围：优先修三个producer，统一生成relative(root,path)后转POSIX的相对路径；如兼容旧受控绝对路径，必须先核归属并拒绝穿越/链接，不能直接放宽门禁。
- 取证：源代码核对及 node:posixScreenshot

### R0R-036 · 采纳 · finalizer破坏browser命令输出绑定

- 位置：scripts/finalize-workbench-matrix.mjs:16；原严重度：medium；核定优先级：P2。
- 判断：合成完整矩阵经真实finalizer成功后，原输出SHA必然改变；外层receipt仍固定旧SHA，collector应且会拒绝。当前CI并不调用finalizer，影响的是手动恢复流程；并非永久不可修复，重新运行即可。
- 建议范围：改为只读检查或独立派生报告/独立动作收据；不能只把旧browser命令的output hash改成新值以伪装原命令产物。
- 取证：源代码核对及 node:finalizerOutput

### R0R-037 · 采纳 · Firefox可用性分支可混入旧探测

- 位置：scripts/collect-m1059-evidence.py:83；原严重度：medium；核定优先级：P2。
- 判断：probe-firefox输出没有runId，collector只核status/winerror，没有核当前exe哈希。缺口仅影响本地Windows缺Firefox的partial分支；严格CI要求三引擎，不能把它描述成直接授予M11资格。
- 建议范围：本轮实际重探测，绑定runId/命令退出、可执行文件身份及原始结果；旧availability不能补写身份后继续使用。
- 取证：源代码/契约/已有测试静态核对；未宣称动态复现
