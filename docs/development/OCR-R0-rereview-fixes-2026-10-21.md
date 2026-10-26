# R0 复审采纳范围实施台账

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](current-state.md)及[全阶段审计](../testing/M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

日期：2026-10-21（Asia/Shanghai）。原复审会话：`eed6a134-1d3b-4e89-adaa-a0e9b2ba1f19`。

原 [逐条核实报告](OCR-R0-rereview-2026-10-20.md) 的 37 条意见中，26 条采纳、10 条部分采纳、1 条不作为当前缺陷采纳。本台账独立记录后续实施；不修改原核实判断、探针、静态快照或历史验收 JSON。下表 36 条的 **Implemented（本地验证）** 表示采纳范围已有代码并通过下述本地验证；不代表 36 个原始建议被原样应用，也不代表全部产品阶段或 R0 的跨阶段回归任务收口。R0R-012 保持不改。

## 逐条实施

| 意见 | 状态 | 实施范围与可核对证据 |
| --- | --- | --- |
| R0R-001 | Implemented（本地验证） | 15 个历史入口在业务读写前同时阻断 CLI、importlib 和 runpy；CLI exit 2，非主模块导入抛明确冻结异常。`test-history-evidence.py` 逐入口验证，审计钩子阻止历史读写。 |
| R0R-002 | Implemented（本地验证） | M9 verifier 同步冻结导入与 runpy 路径；与 001 共用测试，不重复计独立事故。 |
| R0R-003 | Implemented（本地验证） | M8/M9 及 M10～M10.5.8 历史验收说明标记旧命令已冻结；提示区分历史核验器和当前采集器，不能用当前 410 项重证历史 M8 的 295 项。历史结果字节保留。 |
| R0R-004 | Implemented（本地验证） | 新 `ocr-progress.mjs` 校验动态进度并单独绑定输入及派生 Markdown 哈希；只读核验拒绝过期 Markdown/清单。原静态审查输入哈希保持独立。`test-ocr-progress.mjs` 有只读变异负例。 |
| R0R-005 | Implemented（本地验证） | 约束 tasks 对象、已知任务 ID、状态枚举及非空/长度受限 description；渲染时把换行归一为空格。用于防误编辑与结构漂移，不宣称仓库人工输入构成外部攻击面。 |
| R0R-006 | Implemented（本地验证） | content 五类路径拒绝同时核 HTTP 400 与每条路径的精确拒绝码。真实 HTTP 探针确认父级穿越先由策略层返回 `INSPECTION_DENIED`，另外四个现有夹具路径返回 `PATH_DENIED`（绝对路径含受保护 `.build-cache`，先由 metadata 检查拒绝）；合法文件 200 且正文正确。逐条互换拒绝码或改成无关 400 的负例均失败，未修改产品策略以迎合原建议。 |
| R0R-007 | Implemented（本地验证） | 新 `run-r0-validation.py` 为未来新运行生成同 runId、命令/工作目录、退出码、日志/结果哈希及源码快照回执；Node 终态事件及 Python 用例身份按固定契约核验。旧手工摘要不补 runId/退出码、不升格为发布证据。新基线已实际运行，回执与精确 case 记录见下文。 |
| R0R-008 | Implemented（本地验证） | 历史 M10.5.8 等同根因入口与 001 统一冻结；逐个覆盖导入，不只测直接 CLI，未执行旧采集主体。 |
| R0R-009 | Implemented（本地验证） | `xml_document_snapshot` 从文件流至多读 XML 上限 + 1 字节再解析；大小错误发生在有界读取后。新负例禁止 `Path.read_bytes`，并断言受控 reader 只收到 limit+1。 |
| R0R-010 | Implemented（本地验证） | XML 明确支持 UTF-8/ASCII 兼容编码；解析前拒绝 NUL、其他编码声明及 DTD/ENTITY。UTF-16/UTF-32 无害实体样本均拒绝；不从原 UTF-16 探针推断任意无限实体膨胀攻击。 |
| R0R-011 | Implemented（本地验证） | `publish_archive` 在创建目录前对 manifest_name 与普通成员使用同一平铺文件名规则；拒绝绝对路径、穿越、Windows 设备名和目标重名。隔离负例验证根目录无逃逸文件。 |
| R0R-012 | 不改 | 当前 SDK 仅 TS/Python，固定两语言及精确集合符合现行 58 项契约；本轮不声称新增语言支持。 |
| R0R-013 | Implemented（本地验证） | 命令日志与输出制品使用流式 SHA-256；默认日志预算 128 MiB，运行期间每 250ms 监测，超限保留 INCOMPLETE。真实快速超限及仍在运行的超限子进程均有负例。这是监测预算，间隔内可超出，不是 OS 硬磁盘配额。 |
| R0R-014 | Implemented（本地验证） | 移除 `evidence_run.py` 未使用的 sys 导入，随实质修复交付，不单计门禁能力。 |
| R0R-015 | Implemented（本地验证） | Windows workflow 先保存原退出码，只在日志存在时读取最后 100 行；读尾失败也不覆盖原退出码。使用抽取的实际 PowerShell 片段演练有日志/无日志故障。 |
| R0R-016 | Implemented（本地验证） | 前端 Bash/POSIX wrapper 失败后有条件输出最后 100 行，再以原退出码结束；未产生文件的启动失败也保留诊断。实际片段控制演练不等同真实 Linux/macOS/远端 CI。 |
| R0R-017 | Implemented（本地验证） | release collector 移除先行 `ET.parse(report)`；`suite_details` 一次返回计数/用例/原始字节哈希及同份 XML root，Java 版本直接复用该 root。主 POM 同样有界解析；410 个合成用例正例确认每份 suite 只打开一次。 |
| R0R-018 | Implemented（本地验证） | release collector 使用 argparse 与仓库内 bounded_path；缺参、绝对路径及 `..` 在旧 PASS 先被撤销为 INCOMPLETE 后失败。历史 milestone 不再作为当前采集目标。 |
| R0R-019 | Implemented（本地验证） | smoke 的取消子进程、HTTP server 及最后回执发布保留 primary 原异常；secondaryFailures 分别记录清理操作/异常类型，并给原异常加 note。若文件系统拒绝最终写入，次级元数据无法承诺落盘，但原异常 note 保留，初始旧 PASS 已撤销。 |
| R0R-020 | Implemented（本地验证） | HTTP 夹具显式拒绝缺失/重复/非数字/越界长度、transfer encoding、损坏 JSON、短 body 及无可用回复；有界响应与失败事实进入测试记录，不再靠隐式 int 异常诊断。 |
| R0R-021 | Implemented（本地验证） | 移除 release collector 未使用的 json 导入；不单计功能修复。 |
| R0R-022 | Implemented（本地验证） | 矩阵 fixture 在 POSIX 使用独立进程组，Windows 用 taskkill `/T /F`；超时/输出超限都走有界树清理并记录失败。真实已运行孙进程的 marker/PID 验证为 Windows 本机观察；不宣称覆盖主动脱离父子关系/逃逸进程，真实 POSIX 仍待运行。 |
| R0R-023 | Implemented（本地验证） | Node 读取使用单文件描述符、前后 fstat/lstat 身份/大小/时间及最终 realpath 检查，并测试描述符取得后的受控改写。Python/Node 均明确可信本地、稳定且独占目录的假设；末级 NOFOLLOW 与后验检查不被描述为抵御主动祖先目录交换的跨平台沙箱。 |
| R0R-024 | Implemented（本地验证） | 源码摘要与非空证据读取分开；源码允许合法空 `.gitkeep`，流式摘要维持 64 MiB 单文件/512 MiB 总量预算。空文件及 2 MiB 文件正例、单文件/总预算负例均保留。 |
| R0R-025 | Implemented（本地验证） | 浏览器 case 契约走 strictJson，要求恰好 11 fixtures、每组非空唯一 case ID。重复键及错误集合通过复制 helper 的真实模块导入负例拒绝。 |
| R0R-026 | Implemented（本地验证） | 正式测试覆盖完整 11-fixture 矩阵、子集/重复/摘要/路径/runId 变异及只读 CLI。另拒绝命令 overflow 事实，不能靠同步 record SHA 把失败命令变成合法冻结记录。 |
| R0R-027 | Implemented（本地验证） | finalizer 保留原 capturedAt，只输出独立 finalizedAt；不把组装/核验时间解释为重新运行测试。 |
| R0R-028 | Implemented（本地验证） | `terminate_and_reap` 忽略已消失进程组的 ProcessLookupError，但 finally 仍等待直接子进程；实际清理错误进入 cleanupFailures，保留超时主因。修前隔离旧代码命中原异常替代路径；新 POSIX 控制分支通过，不冒充真实 POSIX 竞态运行。 |
| R0R-029 | Implemented（本地验证） | 单独控制 symlink=false/junction=true，以及两者=false/reparse attribute=true，分别命中保护分支；旧 symlink 用例注释更正，不再声称它覆盖 Windows reparse。 |
| R0R-030 | Implemented（本地验证） | ZIP/JAR 两层从原始压缩跨度每 64 KiB 分块，DEFLATE 每次设置实际输出上限；核实际长度、共享 512 MiB 展开预算、EOF/CRC 和声明一致性。仅支持当前 builder 的 STORED/DEFLATE，显式拒绝 ZIP64/其他方法。16 MiB 内容伪报 1 字节及未使用 JAR 成员损坏负例拒绝；不再把原缺陷误述为 ZipExtFile 返回未截断全部数据。 |
| R0R-031 | Implemented（本地验证） | 移除 smoke 主脚本未使用的 copy/sys/time 导入；测试模块中的 copy.deepcopy 用于保存独立回执状态，不单计安全修复。 |
| R0R-032 | Implemented（本地验证） | 报告哈希测试按 `target.resolve()` 索引规范路径；保留实际 Windows junction 别名根复验，不移除精确原始字节哈希断言。原生 macOS 未运行。 |
| R0R-033 | Implemented（本地验证） | 移除主 collector 的 json/ET 死导入，沿用共享有界读取 helper。 |
| R0R-034 | Implemented（本地验证） | 浏览器 case 契约在 record 循环前读取一次，循环内复用同份数据；快照仍进入 archiveInputs 与结束一致性核验。 |
| R0R-035 | Implemented（本地验证） | search/settings/extensions 截图 producer 统一 `relative(root,path)` 后转 POSIX 分隔符；执行实际 shot 函数的 VM 正例模拟 POSIX。未放宽门禁接受任意绝对路径；真实 POSIX 浏览器尚待验收。 |
| R0R-036 | Implemented（本地验证） | finalizer 改为纯只读 validateMatrix 与 stdout 结果；成功/失败路径都保留已被 browser 命令绑定的矩阵原字节，不重签旧回执或修改其输出哈希。 |
| R0R-037 | Implemented（本地验证） | Windows platform runner 在 prepare 前实际执行 probe，产生独立 firefox-probe 命令/退出码/输出回执；probe 记录本轮 runId、脚本/可执行文件 SHA、命令与原始启动事实、前后可执行文件未变。collector 核同轮回执及当前 executable 路径/哈希；旧 availability 不补写身份。14001 只支持本地 partial，不授予严格三引擎 CI 或 M11。 |

## 组内验证观察

以下为各组在本轮代码上已经报告的本地观察；最终统一运行可能继续修改用例及数量，因此本节不充当最终回执，也不把独立组次数相加成产品验收项数。

| 组 | 已执行检查 | 组内观察与边界 |
| --- | --- | --- |
| Python 读取/证据链 | bundled Python：`test-r0-python-rereview.py` 19、`test-evidence-run.py` 11、`test-evidence-io.py` 14 | 44 个测试方法通过；真实简单子进程验证日志超限，POSIX killpg/消失竞态及 Firefox 14001 使用受控注入。TEMP/TMP 指向仓库忽略缓存。 |
| 浏览器门禁/矩阵 | `node --test scripts/test-workbench-evidence.mjs` 25；bundled Python `scripts/test-m1059-gates.py` 12 | 37 项通过；包括完整合成矩阵、真实 Node CLI/finalizer、Windows 实际孙进程、junction。POSIX 截图通过实际 producer 函数的 VM 演练，未运行真实 POSIX 浏览器。 |
| 历史入口 | bundled Python `scripts/test-history-evidence.py` 3 | 覆盖 15 入口 CLI/importlib/runpy，历史 JSON 全集前后哈希一致；子进程审计钩子禁止业务读写，不运行历史采集主体。 |
| 候选/夹具 | bundled Python `scripts/test-smoke-evidence.py` 26 | 全部通过，无 skip；真实现存候选 ZIP 62 文件仅做解析兼容检查，不当作新安装/CLI smoke。 |
| 动态进度 | `scripts/test-ocr-progress.mjs` | schema、派生文档/独立清单只读变异测试已实现；最终命令与计数由统一验证填写。 |
| 新回执采集器 | bundled Python `scripts/test-r0-validation.py` 8 | 8 个独立测试方法通过，已移除继承导致的重复执行；实际固定 case 契约及新回执运行由统一验证填写。 |
| Workflow 故障诊断 | 从实际 YAML 抽取 PowerShell/Git Bash 片段 | 6 次有日志/无日志控制演练保留退出码 41/42/43/44，日志仅尾 100 行；非远端 CI。 |

初始负例曾在修改前命中 XML 无界读取、UTF-16 声明扫描缺口、manifest_name 越界、整段日志读取、旧 POSIX ProcessLookupError、路径别名 KeyError、错误 400 断言、空源码、契约重复键、POSIX 截图及 finalizer 改写绑定输出。每条采纳范围的测试证据按其表内场景解释，未声称全部 36 条均动态复现过原始生产事故。日志超限和 ZIP 预算属于资源契约；没有靠增大内存、跳过测试或降低确切用例集合使其通过。

## 最终统一验证

新证据：[本地验证结果](../testing/OCR-R0-rereview-fixes-2026-10-21-results.json)。固定 case 身份契约为 `scripts/contracts/r0-validation-cases.json`，独立于执行计数；其中 130 项门禁回归、33 项客户端，Java 沿用 410 case / 64 suite 精确契约，全部零失败/错误/跳过。11 组命令均有同 runId、真实退出码 0、工作目录、开始/结束时间、日志和结果 SHA、前后相同的源码快照；结果文件保存实际终态 case。原始命令回执及日志留在忽略的本地缓存，摘要的路径/哈希用于核对本机记录，不是远端可信证明。

正式命令为 `python scripts/run-r0-validation.py --run-id r0-rereview-20261021-final2 --output .build-cache/R0/r0-rereview-20261021-final2 --with-product`，随后 `python scripts/run-r0-validation.py --verify .build-cache/R0/r0-rereview-20261021-final2/results.json` 独立核对全部 11 组通过。Java 实际执行 `mvnw.cmd -B -o test`，临时目录使用仓库外的 Windows Temp。首轮新 runner 的 Node reporter 绝对路径被 Windows ESM 拒绝，失败回执保留在 `r0-rereview-20261021-final`；修为 `file:` URI并加入含空格路径回归后，在全新目录完整重跑，不覆盖旧失败。

真实浏览器选定 `search/settings/extensions/content` 四组：Chrome 与 WebKit 分别 28+26+35+45=134，共 268 项通过；8 份冻结记录逐一再次核 source/JAR/log/screenshot/runId。选定矩阵 runId 为 `r0-rereview-browser-20261021-final`，没有把四组子集作为完整 11-fixture 或三引擎矩阵。006 的首轮失败促使进一步核实实际策略层错误，最终五类路径与合法正文均实际验证通过。前轮 WebKit content 异常在本次选定回归中未再现，保留原失败事实，不扩张为完整矩阵通过。

相邻发布工具 5、SDK 清单 4、秘密模式 2 项通过；全仓源文件秘密模式扫描 0 发现，差异检查通过。Workflow 的 6 次 PowerShell/Git Bash 故障演练为单独控制验证，并非远端 CI。

本次 36 条采纳范围已实施并完成本地验证，012 保持不改。原静态审查及历史验收 JSON 不改写；动态进度与 Markdown 用独立实施清单同步核验。M10.5.9 仍为 LOCAL_PARTIAL_CI_PENDING、m11Eligible=false；完整同运行发布/安装 smoke、真实三平台三引擎 CI 未运行，Firefox 启动失败的负例使用受控注入。R0-03 原 300 条跨阶段回归随 R1～R6 交付，不把本轮 36 项当原 484 项全部关闭。下一入口仍为 R1 及完整 CI 的授权与实测。

本轮仅本地版本管理；用户原有 `.agent` 扫描快照和 `.opencodereview/background-R0.md` 保留且不纳入本次提交，无推送、发布或付费模型调用。
