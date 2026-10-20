# OCR逐条核实：docs

原文及建议代码见 opinions.json；这里只以稳定编号连接原意见，部分采纳以“判断”限定范围。所有修复均未实施。

## OCR-1172 · 不采纳

- 位置：docs/development/M10-frontend-reuse-source.json:4；OCR原严重度：medium
- 计划：无
- 原意见：第4行把本机绝对路径（含 Windows 盘符、`Documents` 目录与项目夹名）写入仓库内的快照文件。该文件会随仓库分发，等同于泄露工作站目录布局，并使文档在其他机器上不可解析。建议用占位符（如 `<redacted-local-checkout>`）替代具体路径，只在本地/未跟踪笔记中保留实际路径；溯源信息用 `headCommit` 即可，无需真实目录名。
- 判断：本地核对 headCommit="d743267388641bc76f17c45ce8b4c231aed1d32c"。本机路径/Windows分隔符是历史运行平台记录，实际collector已有replace归一化，当前矩阵已输出POSIX路径。没有现行消费者失败证据；路径不含密钥，不把目录布局视作凭据泄漏，也不追溯改写历史取证记录。
- 证据：docs/development/M10-frontend-reuse-source.json:4；docs/testing/M10.5.9-validation.md:3；docs/development/M10-frontend-reuse-source.json:5

## OCR-1173 · 不采纳

- 位置：docs/development/M10-frontend-reuse-source.json:7；OCR原严重度：medium
- 计划：无
- 原意见：第7行声明 `clientPackageManifestCount: 63`，但 `sourceFilesSha256` 只收录了 `apps/web/package.json` 一个清单，无法从本文件复现或校验 63 这个数字的来源（M10-frontend-reuse-review.md 第16行也只做了同样断言）。一旦上游快照变化，读者无从判断该计数对应哪个包集合。建议同时记录统计口径与命令（如 `packages/client/*/package.json` 的 glob）以及清单文件的名称/哈希列表；若清单过多，可另存为独立的 `clientManifests` 清单并在本文件以哈希引用。
- 判断：本地核对 sourceFilesSha256=对象(25键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/development/M10-frontend-reuse-source.json:7；docs/development/M10-frontend-reuse-review.md:16；docs/development/M10-frontend-reuse-source.json:8

## OCR-1174 · 不采纳

- 位置：docs/development/M10-frontend-reuse-source.json:35；OCR原严重度：medium
- 计划：无
- 原意见：`jHarnessApiFile`/`jHarnessApiSha256`（第35-36行）指向本仓库的 Java 源码，但本文件只有一个 `headCommit`，且它明确是上游 deepseek-harness 的提交。因此该 Java 文件的哈希没有绑定任何 revision：读者既无法确认它对应哪个 J-Harness 提交，也无法发现它在后续提交中被修改（哈希会静默过期）。同时，这里缺少仓库归属字段，容易被误解析成 `sourceDirectory` 下的相对路径（该目录下不存在 `harness-web/`）。建议补充 `jHarnessHeadCommit`（或注明“路径相对于本仓库根、哈希对应 <commit>”）以固定校验基准。
- 判断：本地核对 jHarnessApiFile="harness-web/src/main/java/io/jharness/web/HarnessWebServer.java"；jHarnessApiSha256="90f11ad32696d3c637035ca1eb5b5705ef549ce113b378bc0d3f3500df40c6a9"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/development/M10-frontend-reuse-source.json:35；docs/development/M10-frontend-reuse-source.json:5；docs/development/M10-frontend-reuse-source.json:36

## OCR-1175 · 不采纳

- 位置：docs/development/M10-frontend-reuse-source.json:6；OCR原严重度：low
- 计划：无
- 原意见：`trackedWorktreeCleanAtCapture`（第6行）以及第37-39行的 `browserRendered`/`upstreamBuildOrTestsRun`/`m10ImplementationStarted` 均为自述断言，文件没有记录采集时间戳、采集命令或工作树校验输出，下游审查者无法复现该快照状态（例如无法证明哈希对应的是一个 clean 的 `headCommit`，也无法排除采集后又改动了文件）。建议补充 `captureTimestamp`、`captureCommand`（如 `git status --porcelain` / `git rev-parse HEAD` 的输出引用）或指向日志的字段，使这些布尔断言可被审计。
- 判断：本地核对 trackedWorktreeCleanAtCapture=true；browserRendered=false。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/development/M10-frontend-reuse-source.json:6；docs/development/M10-frontend-reuse-source.json:2；docs/development/M10-frontend-reuse-source.json:37

## OCR-1176 · 部分采纳

- 位置：docs/examples/m6-config.json:2；OCR原严重度：medium
- 计划：R6-03 / P3
- 原意见：顶层 baseUrl/model/apiKeyEnv（第2-4行）与 models.primary（第7-9行）是同一份路由信息的重复定义，但两者在运行期走的是不同分支：profile/preset 的 modelRoute 解析到 primary 时使用 models.primary 的字段（ConfigurationSelection.resolve 用 putAll 覆盖），而未选中任何 route 或显式选择 `base` 时用的是顶层字段（CliApplication 中 factories.put("base", () -> new OpenAiCompatibleLlmClient(modelConfig))）。因此只要有人只改了一处（例如只改顶层 model 或只改 primary 的 baseUrl），`--model-route base` 与 `primary` 就会静默指向不同的 endpoint/model/key，且配置校验不会报错（两层都合法），示例被复制到生产后极易出现“以为改的是同一条路线”的错配。建议去掉顶层重复字段，在 models 中显式定义 `base` 路线并让所有 profile 的 modelRoute 指向它，或至少在文档中明确这两处必须同步。
- 判断：本地核对 models.primary=对象(5键)。base和primary是不同可选路由，重复字段有维护误改风险，采纳文档明确base/primary互不联动。不可直接按建议删除顶层配置，需先保持基础路由配置契约。
- 证据：docs/examples/m6-config.json:2；harness-cli/src/main/java/io/jharness/cli/ConfigurationSelection.java；docs/examples/m6-config.json:7

## OCR-1177 · 采纳

- 位置：docs/examples/m8-filter-config.json:8；OCR原严重度：medium
- 计划：R6-03 / P2
- 原意见：示例规则的语义与其想表达的意图不一致：`!target/` 与 `cache/` 都没有内部斜杠，按 `LocalWorkspace.ignored()` 的匹配逻辑会被解释为“匹配任意层级同名目录”，而 `target/*` 含斜杠所以是“相对工作区根锚定”。因此对 `module/target/foo.txt` 这类嵌套路径：`target` 段被默认排除后又被 `!target/` 重新包含，而 `target/*` 只锚定根目录无法再次排除其内容，最终该文件会被 list/search/grep 发现——即示例会把层级内所有构建产物都暴露到检索结果里，而不是文档 `m8-search-filter.md` 第 15 行描述的“只重新包含 target 目录、排除其内容、仅保留 target/report.txt”。另外该 md 的代码片段只列了 target 相关三条规则，与本文件的 `cache/`、`generated/**` 不同步，复制粘贴时会得到两种不同语义。建议给需要锚定根目录的规则加前导 `/`，并在 md 中同步说明。
- 判断：本地核对 "rules": ["cache/", "generated/**", "!target/", "target/*", "!target/report.txt"]   } }。规则匹配实现先去前导/再按anchored或含/决定匹配整个前缀；!target/匹配任意层target，target/*却仅匹配根前缀。示例描述只保留根target/report.txt时确会重新暴露嵌套target内容。应锚定示例规则并补嵌套反例。
- 证据：docs/examples/m8-filter-config.json:8；harness-workspace/src/main/java/io/jharness/workspace/LocalWorkspace.java:628

## OCR-1178 · 不采纳

- 位置：docs/testing/M10-auto-settings-results.json:93；OCR原严重度：medium
- 计划：无
- 原意见：截图证据路径指向 `.build-cache/`（已被 .gitignore 第 8 行忽略），因此本报告提交后引用的 PNG 并不在仓库中：任何 clone/CI 环境都无法重算 sha256，缓存被重新生成或清理后也无法发现漂移。另外路径使用 Windows 反斜杠，仅在 Windows 下可解析。触发条件：在非 Windows 或干净检出环境中执行校验脚本重算这些 sha256 即失败/无法验证。建议把截图落到受版本控制的证据目录并使用 POSIX 分隔符，或直接内联证据摘要。
- 判断：本地核对 "path": ".build-cache\\M10-settings-plugins.png",       "sha256": "ab4919adc268ce3172de6af298f241413105cdd1aebbdc1bd2dff277de。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10-auto-settings-results.json:93；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1179 · 不采纳

- 位置：docs/testing/M10-auto-settings-results.json:2；OCR原严重度：low
- 计划：无
- 原意见：可复现性元数据不足：本文件只有 jarSha256，没有对应的源码提交/构建标识（其他 M10 证据里使用 sourceSha256/upstreamSha256 绑定输入），因此事后无法把这份安全校验结果（未鉴权拒绝、跨源变更拒绝、密钥不落盘等）归属到具体的源码 revision；一旦源码变更，jarSha256 与实际版本是否一致无从判断。此外 `browser` 仅记录内核版本号，缺少产品名（Chromium 同版本可能来自 Chrome/Edge 等多个浏览器），无法确定测试载体。建议补充 commit/buildId、被测源码摘要与浏览器产品名。
- 判断：本地核对 browser="155.0.8059.82"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10-auto-settings-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10-auto-settings-results.json:4

## OCR-1180 · 不采纳

- 位置：docs/testing/M10-auto-launcher-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：第 4-12 行的 `checks` 仅为描述性字符串数组，既没有逐项 `status`/`passed`，也没有 skipped 或失败项列表。顶层 `status: PASS` 是聚合值，因此消费者（以及人工复审）无法判断某个场景究竟是“执行并通过”“未执行/被跳过”还是“部分失败后仍写 PASS”，一旦有场景未运行或被 8 行文档描述冒名顶替，报告仍然显示全绿。触发条件：任一条 check 未实际执行或断言失败但脚本仍写出聚合 PASS 时，本文件无法区分，属于可复现的校验证据缺失。建议将 checks 改为带名称与结果的对象数组（与同目录使用布尔映射的 results 文件口径保持一致），或补充 `failed`/`skipped` 字段。
- 判断：本地核对 checks=数组(8项)；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10-auto-launcher-results.json:4；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10-auto-launcher-results.json:2

## OCR-1181 · 不采纳

- 位置：docs/testing/M10-auto-launcher-results.json:14；OCR原严重度：medium
- 计划：无
- 原意见：第 14-16 行的三项安全断言（关闭浏览器/剪贴板副作用、`paidRequests: 0`、模型端点不可达）只有结论文本，没有任何可追溯证据：没有日志路径、执行命令、退出码、端点探测记录或对应 check 标识，也没有说明结论对应的代码/配置版本。触发条件：只要有人改动启动器配置或测试脚本再复用本报告，这些“无浏览器副作用”“零付费请求”“端点不可达”的结论就无法被复核，安全声明退化为不可验证的口头承诺。建议为每项断言增加 evidence 字段（日志文件、探测命令与返回值、关联的 sourceSha256 版本）。另外，此处称生产环境“never copies a credential”，与同目录既有报告记录的“production defaults ... copy token”口径不一致，需要注明适用版本，否则无法判断哪份结论有效。
- 判断：本地核对 capturedAt="2026-10-17T00:36:18.601426+00:00"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10-auto-launcher-results.json:14；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1182 · 部分采纳

- 位置：docs/examples/deepseek-config.json:8；OCR原严重度：medium
- 计划：R6-03 / P2
- 原意见：触发条件与影响：成本预留按「inputTokenCeiling × 输入单价 + max_tokens × 输出单价」逐请求计算（ConfiguredTokenCostGuard.maximumCost），而 DefaultAgentLoop 在每次请求前要求 `reservedCost + bound ≤ maxCost`（超过即抛 COST_LIMIT）。本文件中 1,000,000×2e-6 + 4,096×8e-6 = 2.032768 CNY，单次请求预留就占 maxCost=2.50 CNY 的 81.3%。因此当累计实际费用超过 2.50−2.032768≈0.467232 CNY 后（约 3 个接近 contextTokens=65536 的步骤），即使任务预算还余约 2.03 CNY，下一步也会被 COST_LIMIT 拒绝，maxSteps=20 无法用满；若响应缺失 usage 则直接按 2.03 CNY 计费，第 2 步即被拒绝。另外 contextTokens 已把单请求输入限制在 65,536，而校验上限是它的 15 倍，实际用量在 65,537–1,000,000 之间时 actualCost 不会报 COST_ACCOUNTING_BREACH，预留与校验都失去与真实上下文窗口的一致性。建议把 inputTokenCeiling 收敛到 contextTokens（65536）量级，使单次预留约 0.1638 CNY，与 2.50 CNY 预算及 20 步的配置相称。
- 判断：本地核对 "inputTokenCeiling": 1000000,   "currency": "CNY",   "maxCost": "2.50",。按示例数字单次上界确为2.032768 CNY，远高于65536上下文对应的预留。采纳说明预算的保守预留语义并评估合理示例上界；不能承诺20步一定用满，也不据此判断真实服务商上下文/价格。
- 证据：docs/examples/deepseek-config.json:8；docs/examples/deepseek-config.json:6

## OCR-1183 · 不采纳

- 位置：docs/testing/M10-first-attempt.json:6；OCR原严重度：low
- 计划：无
- 原意见：`reportSha256` / `logSha256` / `recheckLogSha256` 三个哈希只给出了摘要值，却没有指明它们分别对应哪个产物文件（路径、文件名、生成批次），仓库内也不存在对应文件（检索无 first-attempt 产物）。同目录 `docs/testing/M10-settings-first-attempt.json` 采用了 `files: {路径: sha256}` 的写法，可据此校验。当前写法导致证据不可独立复核：任何人都无法确定该 sha256 校验的是哪份报告/日志，也无法判断是否被替换或与第 13 行的后续全量验证出自同一构建。建议改为显式的 路径→sha256 映射（保留失败尝试的原始产物于 .build-cache 并在修复提交中一并保留）。
- 判断：本地核对 reportSha256="c84b93c236b527d4bf262d732b476481994cad90833cf464aa636b341044d755"；logSha256="12d18e0f2e1a4965f82e589a80de18ff3e3c50a8f4bb10d727243de3c14ee9a5"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10-first-attempt.json:6；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M10-first-attempt.json:7

## OCR-1184 · 不采纳

- 位置：docs/testing/M10-first-attempt.json:10；OCR原严重度：low
- 计划：无
- 原意见：`subsequentFullVerify` 只记录了 tests/failures/errors/skipped 四个计数，缺少时间戳、提交/构建标识、验证命令与报告校验和（对比同文件第 6–9 行给出 sha256 的做法）。因此无法确认这次 0 失败的运行与失败尝试处于同一代码版本、是否真的按原样重跑，也无法排除「用更晚的通过结果掩盖未修复缺陷」——即第 4 行 Windows 目录仍被占用的资源生命周期问题是否已解决无从判定。建议补充 commit/sha、时间戳、执行命令与报告 sha256，并显式说明失败测试的修复点。
- 判断：本地核对 subsequentFullVerify=对象(4键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10-first-attempt.json:10；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1185 · 不采纳

- 位置：docs/testing/M10-evidence-audit-results.json:2；OCR原严重度：high
- 计划：无
- 原意见：第 2 行顶层 `status` 直接硬编码为 `PASS`，但文件内没有任何 runId、源码 commit SHA、执行时间戳、工具版本等绑定字段，无法将结论定位到具体代码版本与一次具体执行；同时第 39-42 行的负例缺少 `exitCode`，`status` 也未反映该结构缺失。下游消费者只读 `status` 时会把“证据不完整的通过”当作“已验证通过”。建议补充 `sourceRevision`、`runTimestamp`、`schemaVersion`，并让校验器基于负例字段完整性推导 `status`，而不是在证据文件中写死 PASS。
- 判断：本地核对 status="PASS"；negativeCases[0].exitCode=1。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10-evidence-audit-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10-evidence-audit-results.json:7

## OCR-1186 · 采纳

- 位置：docs/testing/M10-evidence-audit-results.json:40；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：该负例只有 `case`/`rejected`，缺少其余 7 个负例都有的 `exitCode`，schema 不一致。仅凭 `rejected: true` 无法断言拒绝来自预期的非零退出码：若校验进程因异常以 0 退出、或崩溃/超时后由外层脚本补写 true，也会得到同样记录。建议补齐 `exitCode`（期望 1），并增加期望的失败签名（错误码或关键错误消息），同时在 schema 中把 `exitCode` 设为 required，避免字段被静默忽略。
- 判断：本地核对 negativeCases[0].case="failed browser run"；negativeCases[0].rejected=true。negativeCases最后一项确只有case/rejected，前七项有exitCode。采纳新负例结果统一记录命令、实际退出码及预期拒绝签名；不可补造该历史执行的退出码。
- 证据：docs/testing/M10-evidence-audit-results.json:40；docs/testing/M10-evidence-audit-results.json:39；docs/testing/M10-evidence-audit-results.json:5；docs/testing/M10-evidence-audit-results.json:6

## OCR-1187 · 不采纳

- 位置：docs/testing/M10-evidence-audit-results.json:45；OCR原严重度：high
- 计划：无
- 原意见：第 45-46 行计数不一致：初始校验 25 个源文件哈希，复制后只校验了 21 个原件哈希，差额 4 个文件在复制后缺失哈希校验。这与第 44 行 `sourceRepositoryUnchanged: true` 的结论存在矛盾——未被复验的文件可能被复制流程改写而无法发现。建议：要么让两个计数一致，要么显式列出豁免/跳过的文件路径及原因，并附上基准哈希清单，否则该证据不足以证明源仓库未被修改。
- 判断：本地核对 status="PASS"。25是前置只读阅读文件集合，21是原样移植文件集合，两者范围不同；文档明确25前置、21移植，差4并不意味着漏验改写。
- 证据：docs/testing/M10-evidence-audit-results.json:45；.agent:M10完成与接续；docs/development/M10-frontend-reuse-source.json:8

## OCR-1188 · 不采纳

- 位置：docs/testing/M10-evidence-audit-results.json:47；OCR原严重度：medium
- 计划：无
- 原意见：两个 SHA-256 字段缺少 artifact 绑定信息（如 `collectorPath`/`bomGeneratorPath`、产物大小、版本），摘要无法定位到具体产物；也没有任何声明表明生成器会校验摘要的 64 位十六进制格式与长度，因此截断或被替换的摘要仍可随 `PASS` 一起被接受。建议为每个摘要配套记录产物路径与字节数，并在证据生成/校验脚本中强制 64-hex 校验后再写入该文件。
- 判断：本地核对 status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10-evidence-audit-results.json:47；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1189 · 不采纳

- 位置：docs/testing/M10-evidence-audit-results.json:5；OCR原严重度：medium
- 计划：无
- 原意见：负例记录仅有标签、布尔值和退出码，缺少复现命令、注入的输入工件引用、日志/stderr 路径以及期望失败标识，`rejected` 结论无法被独立复现与审计。建议为每个负例增加 `command`、`inputArtifact`（或 fixture 哈希）、`logRef`、`expectedFailure` 字段，使审计者能按记录重放并验证拒绝原因，而不是只相信一个布尔值。
- 判断：本地核对 negativeCases[0].rejected=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10-evidence-audit-results.json:5；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10-evidence-audit-results.json:6

## OCR-1190 · 不采纳

- 位置：docs/testing/M10-browser-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：第 2 行：顶层 `status` 只有 `PASS` 一个聚合结论，且缺少“期望的检查总数/已计划检查清单/跳过项”字段。触发条件：`frontend/tests/browser.mjs` 中某个检查因异常提前退出、被注释或改名后不再执行时，本文件仍会生成 `status: "PASS"` 且 `checks` 只包含实际跑过的条目，任何下游校验都会误判为全绿；同时 `checks[]` 只有 `name`/`passed`，没有稳定 id 与失败详情，改名即静默丢失覆盖率、失败时也无法定位原因。建议增加 `schemaVersion`、`expectedChecks`（或 `requestedChecks` 列表）、`failedChecks`/`skippedChecks` 计数，并要求 `checks.length === expectedChecks` 才允许 PASS。
- 判断：本地核对 status="PASS"；checks=数组(28项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10-browser-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10-browser-results.json:7

## OCR-1191 · 不采纳

- 位置：docs/testing/M10-browser-results.json:127；OCR原严重度：high
- 计划：无
- 原意见：第 127-169 行：`sourceSha256` 只覆盖 frontend 与 harness-web（2 个 main 类）、harness-testkit（2 个测试类），但本文件第 21、25、49、69、77、93 行等检查项声明的被测对象是 “actual Java API / actual tool pipeline / actual Java runtime”。仓库根 `pom.xml` 共有 14 个模块（harness-api、harness-events、harness-session、harness-plugin、harness-tools、harness-workspace、harness-process、harness-policy、harness-agent、harness-llm、harness-ecosystem、harness-web、harness-cli、harness-testkit），其中 harness-tools / harness-policy / harness-session 等实现工具流水线与取消/审批逻辑的模块完全没有哈希。触发条件：这些模块在生成证据后被修改，或证据被复用到另一个提交，消费方无法判断“实际 Java 运行时”到底是哪个版本，结论不可复现。建议至少记录 git commit SHA，并补齐与工具流水线、会话、策略相关模块的源码哈希。
- 判断：本地核对 sourceSha256=对象(41键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10-browser-results.json:127；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1192 · 不采纳

- 位置：docs/testing/M10-browser-results.json:177；OCR原严重度：medium
- 计划：无
- 原意见：第 177-203 行：截图路径使用 Windows 反斜杠分隔符（`.build-cache\\M10-*.png`），且 `.build-cache/` 已在仓库 `.gitignore` 第 8 行被忽略。触发条件：在 Linux/macOS 或另一台机器、另一份 checkout 上按该路径取证据时，路径不存在且分隔符不兼容，`sha256` 无法回溯校验，screenshots 段落退化为不可验证数据。建议改用 POSIX 相对路径，并把产物归档到被版本管理的目录（或 CI artifact）并在文件中记录归档位置。
- 判断：本地核对 screenshots[0].sha256="5da602f4faf968893239b988035ea7c46e47b08778636eea4ea1422111451f12"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10-browser-results.json:177；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29；docs/testing/M10-browser-results.json:178

## OCR-1193 · 不采纳

- 位置：docs/testing/M10-browser-results.json:209；OCR原严重度：medium
- 计划：无
- 原意见：第 209 行：`jarSha256` 只有哈希值，没有 jar 文件名/构建坐标，也没有被测 UI 产物（`frontend/dist` 打包后被 `UiResources` 提供的那份 bundle）的哈希。触发条件：`harness-web` 提供的静态资源来自一次过期的 `frontend/dist`（源码哈希已更新但未重新构建），浏览器实际加载的代码与 `sourceSha256` 不一致，本文件仍会 PASS，且无法用 `frontend/scripts/package-ui.mjs` 复现同一产物。建议补充 jar 名称/版本与 `frontend/dist` 产物哈希（至少 index.html 及主 JS/CSS 的哈希）。
- 判断：本地核对 jarSha256="a540f149a16e746ca73a935c0476a1d67cd2007f208df82b898b599d5177f2e4"；sourceSha256=对象(41键)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10-browser-results.json:209；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10-browser-results.json:127

## OCR-1194 · 不采纳

- 位置：docs/testing/M10-browser-results.json:123；OCR原严重度：low
- 计划：无
- 原意见：第 121-126 行与第 170-173 行：`runtime.opened`、`requests.taskSubmissions`（均为 9）与 `requests.total`（126）语义重叠且无来源说明。触发条件：若这些计数由测试脚本自行累加而非取自服务端请求日志，则重复 POST 重放、重复建任务这类缺陷（正是第 25、77 行检查项要防的场景）会同时抬高计数并被写成“预期值”，从而掩盖真实问题；同时 “opened” 到底指打开任务数还是打开的连接数无法判定，后续核对会误读。建议明确命名（如 `taskOpenCount` / `taskPostCount`）并注明计数取自服务端请求日志。
- 判断：本地核对 runtime=对象(4键)；requests=对象(2键)。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10-browser-results.json:123；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10-browser-results.json:121；docs/testing/M10-browser-results.json:170

## OCR-1195 · 不采纳

- 位置：docs/testing/M10-auto-workbench-results.json:178；OCR原严重度：high
- 计划：无
- 原意见：`M10-connection.png` 与 `M10-welcome.png` 记录为完全相同的 sha256（520c58…03cf），说明“连接前”与“连接成功（欢迎）”两个不同 UI 状态实际只截到了同一帧，或者第二次截图根本没写入新内容。结果是依赖这两帧的检查（第 9 行的「credentials rejected while browser enters automatically」以及连接态相关断言）在产物中没有独立的视觉证据，PASS 无法被复核。建议在截图后校验相邻帧哈希不得相同（哈希相同即判定证据失败），或把 welcome 截图延后到连接态可见并断言 `.connection-state` 文本变化后再截。
- 判断：本地核对 capturedAt="2026-10-17T00:36:31.206Z"；status="PASS"。相同内容会有相同摘要，不能反推未执行或复制。浏览器自动同源进入使connection/welcome可已是同一状态；夹具日志仅打印确定性的PASS名称，不同引擎/复跑可逐字相同。应核对执行入口和制品来源，不能要求截图/日志哈希强制互异。
- 证据：docs/testing/M10-auto-workbench-results.json:178；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1196 · 不采纳

- 位置：docs/testing/M10-auto-workbench-results.json:121；OCR原严重度：high
- 计划：无
- 原意见：`paidRequests` 恒为 0：该字段由 M10BrowserHost 直接写成字面量 0（不是对真实出站模型流量的计数），因此对应检查「keyless runtime reports zero paid requests」永远为真、不可能失败，本字段不构成“零付费请求”的证据。同时 `modelRequests` 为 14 说明确实发生了 14 次模型请求，但产物未记录这些请求的目标 endpoint/baseURL 与是否本地桩，无法排除其中夹带付费调用。建议在 evidence 中落盘实际观测到的模型 endpoint 集合与每条请求的目标，并让 paidRequests 由“目标 endpoint 是否在白名单内”推导，而不是常量。
- 判断：本地核对 runtime.paidRequests=0；runtime.modelRequests=14。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10-auto-workbench-results.json:121；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10-auto-workbench-results.json:122；docs/testing/M10-auto-workbench-results.json:125

## OCR-1197 · 不采纳

- 位置：docs/testing/M10-auto-workbench-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：顶层 `status` 是生成器里硬编码的字符串字面量（frontend/tests/browser.mjs 第 227 行 `status: 'PASS'`），且 `checks` 每项只有 `{name, passed:true}`，不带断言 id、实际观测值或失败输出；任何检查项被跳过/被注释掉（例如某个依赖特定环境的断言被条件跳过）后，产物依旧会是 PASS 且看不出缺口。建议整体状态由 `checks` 派生（如 `status = checks.every(c=>c.passed) ? 'PASS' : 'FAIL'`），并把每项检查的实际观测值或跳过原因写进产物，使 PASS 可被复核。
- 判断：本地核对 status="PASS"；checks=数组(28项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10-auto-workbench-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10-auto-workbench-results.json:7

## OCR-1198 · 不采纳

- 位置：docs/testing/M10-auto-workbench-results.json:206；OCR原严重度：low
- 计划：无
- 原意见：`screenshots[].path` 指向 `.build-cache`（已被 .gitignore 第 8 行忽略）且使用 Windows 反斜杠分隔符：提交到仓库的证据引用的是未被版本化的构建产物，且相对路径既不是相对本文件（docs/testing/）也不是 URL 形式，任何下游复核者都无法据哈希重新校验这些截图。建议把关键截图随产物一并归档到受版本控制的位置（或把截图内容内联/以 base64 摘要+可复现脚本路径记录），并统一使用仓库根相对的正斜杠路径。
- 判断：本地核对 screenshots=数组(8项)。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10-auto-workbench-results.json:206；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29；docs/testing/M10-auto-workbench-results.json:177

## OCR-1199 · 不采纳

- 位置：docs/testing/M10-gap-review-results.json:6；OCR原严重度：low
- 计划：无
- 原意见：该字段记录的是开发者本机的 Windows 绝对路径（含用户名与磁盘布局），提交到仓库的审计报告里泄露了工作站环境上下文，与“敏感信息脱敏”要求冲突；下游复核者也无法使用该路径。建议改为仓库相对标识（如 `deepseek-harness@<commit>`）或占位符。
- 判断：本地核对 status="REVIEW_COMPLETE"。本机路径/Windows分隔符是历史运行平台记录，实际collector已有replace归一化，当前矩阵已输出POSIX路径。没有现行消费者失败证据；路径不含密钥，不把目录布局视作凭据泄漏，也不追溯改写历史取证记录。
- 证据：docs/testing/M10-gap-review-results.json:6；docs/development/M10-frontend-reuse-source.json:4；docs/testing/M10.5.9-validation.md:3

## OCR-1200 · 不采纳

- 位置：docs/testing/M10-gap-review-results.json:46；OCR原严重度：medium
- 计划：无
- 原意见：自引用完整性缺陷：`reportSha256` 被内嵌在它所声称摘要的文件中，若按常规对整文件重算 SHA-256，结果永远不可能等于该值（写入哈希会改变文件内容）。文档未说明计算口径（是否剔除本字段、键排序/换行规范化等），因此该值无法被下游复核者验证，起不到防篡改作用。建议注明精确的计算规则（并把计算脚本纳入仓库），或改用外部校验清单/签名（如 `.sha256` 旁路文件），避免自引用。
- 判断：本地核对 reportSha256="731979dbb7f5624a823162aa557ce55a4d78e1b353124afdf67f4f820ca2f80f"。reportSha256位于差距JSON但字段并未声明是该JSON自身；同名开发Markdown为评审报告。不能仅凭字段名推导自引用不可能；可读性可改善但原缺陷未成立。
- 证据：docs/testing/M10-gap-review-results.json:46；docs/development/M10-upstream-gap-review-2026-10-17.md

## OCR-1201 · 不采纳

- 位置：docs/testing/M10-gap-review-results.json:8；OCR原严重度：medium
- 计划：无
- 原意见：覆盖度与声明不一致：这里声称监控 1954 个参考仓库文件，但 `referenceSourcesSha256` 只列出 19 个摘要，文件中也未说明这 19 个的选取规则、以及其余 1935 个文件是如何被判定“未变更”的。下游无法判断该 1954 的口径（清单来源/工具），也无法复现结论。建议补充被监控清单的生成方式与清单文件自身的摘要。
- 判断：本地核对 referenceSourcesSha256=对象(19键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10-gap-review-results.json:8；docs/testing/M10-gap-review-results.json:11；docs/development/M10-upstream-gap-review-2026-10-17.md:42；docs/testing/M10-gap-review-results.json:12

## OCR-1202 · 不采纳

- 位置：docs/testing/M10-gap-review-results.json:10；OCR原严重度：medium
- 计划：无
- 原意见：该布尔断言与 `referenceMonitoredFilesUnchanged`、`referenceBaselineFileSha256` 都是自述式结论：未记录采集命令、工具与版本、执行时间，也未说明 `referenceBaselineFileSha256` 对应的基线文件身份（文件名/来源），因此审计最核心的“参考仓库全程未被改动”结论既不可复现也不可证伪。建议补充可重放的命令与基线文件标识。
- 判断：本地核对 referenceMonitoredFilesUnchanged=true；referenceBaselineFileSha256="4d03220395e0689b579cf4481275c9dcc1a4c786cb511613d5bdef2559dcaeff"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10-gap-review-results.json:10；docs/testing/M10-gap-review-results.json:8；docs/testing/M10-gap-review-results.json:11；docs/development/M10-upstream-gap-review-2026-10-17.md:42；docs/testing/M10-gap-review-results.json:9

## OCR-1203 · 不采纳

- 位置：docs/testing/M10-gap-review-results.json:44；OCR原严重度：medium
- 计划：无
- 原意见：证据链混用：`jHarnessSourcesSha256`（自述为源码摘要集合）里包含了上一轮生成的报告工件 `docs/testing/M10-auto-results.json`，把生成产物当成“源码”纳入同一摘要集合。后果是：该 JSON 一旦被重新生成就会导致整组“源码摘要”失效，而它本身也不能证明产品源码状态。建议把报告/工件类文件的摘要移入单独字段，与真正被审阅的源码摘要分开。
- 判断：本地核对 jHarnessSourcesSha256=对象(11键)。该字段记录本仓库审阅输入，报告本身也是差距分析输入；把JSON证据纳入摘要不会让其它文件的哈希失效，非产品正确性缺陷。
- 证据：docs/testing/M10-gap-review-results.json:44；docs/testing/M10-gap-review-results.json:34；docs/testing/M10-gap-review-results.json:33

## OCR-1204 · 不采纳

- 位置：docs/testing/M10-gap-review-results.json:4；OCR原严重度：low
- 计划：无
- 原意见：状态声明与自述限制相互矛盾：`status` 标记为 REVIEW_COMPLETE，但 `limitations` 明确说明未做安装/构建/运行时/测试，文件中也没有任何 findings/结论字段。读者会把该状态理解为“差距评审已完成且结论有效”，与实际情况不符。建议改为更精确的状态（如 `STATIC_REVIEW_COMPLETE`）并补充评审结论条目，使状态与限制一致。
- 判断：本地核对 status="REVIEW_COMPLETE"；limitations=数组(3项)。kind明确为static-source-comparison，limitations明确未安装/构建/测试；静态审查完成与未运行软件不矛盾，结论在配套Markdown中。
- 证据：docs/testing/M10-gap-review-results.json:4；docs/testing/M10-gap-review-results.json:3；docs/development/M10-upstream-gap-review-2026-10-17.md:3；docs/testing/M10-gap-review-results.json:52

## OCR-1205 · 不采纳

- 位置：docs/testing/M10-npm-audit.json:14；OCR原严重度：medium
- 计划：无
- 原意见：计数无法自洽：npm audit 的 `total` 恒等于 `prod + dev`（optional 只是子集，不额外计入），此处 7 + 151 = 158 ≠ 157。若按 docs/testing/M10-validation.md 第21行/第32行声明的“生产闭包6包”（package-lock 中非 dev 包 = 6），则 `dev: 151` + 6 = 157 正好等于 `total`，说明第14行的 `prod` 值被错记为 7（或多算了根项目）。该字段是审计覆盖面的直接证据，错误的 prod 计数会让“生产依赖全部纳入审计”的结论失真，也会让任何按包数比对的校验器误判。请以真实 `npm audit --json` 输出为准重新采集，不要手工填写计数。
- 判断：本地核对 metadata.vulnerabilities.total=0；metadata.dependencies.prod=7。这是原字节保留的npm audit report v2，prod/dev/optional是npm分类计数，报告未约定prod+dev=total。无真实npm输出反证前不能手改prod=6；生产闭包SBOM计数与audit包含根等统计不可直接混算。
- 证据：docs/testing/M10-npm-audit.json:14；docs/testing/M10-npm-audit.json:2；scripts/collect-m10-evidence.py；docs/testing/M10-npm-audit.json:11

## OCR-1206 · 不采纳

- 位置：docs/testing/M10-npm-audit.json:4；OCR原严重度：medium
- 计划：无
- 原意见：该审计快照没有任何溯源字段（生成时间、npm 版本、对应的 package-lock sha256、commit），而 scripts/collect-m10-evidence.py 只是原字节拷贝 .build-cache/M10-npm-audit.json。因此本文件脱离 M10-results.json 后无法判断它对应哪个依赖图；若后续 package-lock.json 变更而此文件未重新生成，一个“全零/空 vulnerabilities”的历史快照仍会被当作干净的依赖树证据通过。建议在采集时于 metadata 中补充 `capturedAt`、`npmVersion`、`lockfileSha256`（或在同目录附 provenance 记录），并在证据校验中将 lockfile 摘要与本文件绑定。
- 判断：本地核对 "metadata": {     "vulnerabilities": {       "info": 0,。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10-npm-audit.json:4；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1207 · 不采纳

- 位置：docs/testing/M10-settings-launcher-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：第 2 行的顶层 `"status": "PASS"` 与第 4-13 行的 `checks` 数组之间没有可追溯的对应关系：`checks` 里 8 项全是散文式描述，没有单项 verdict、无时间戳、无失败细节。触发条件：任意一项检查被跳过、超时或只跑了一半（例如第 8 行 `web-ui.cmd starts and stops the real packaged UI` 未真正拉起进程），记录仍会产出整体 PASS，评审者无法区分“真通过”和“未执行”。建议把 `checks` 改为对象数组，每项至少含 `id`/`description`/`result`/`durationMs`/`evidenceRef`，并让 `status` 由各项聚合得出（任一非 PASS 则整体非 PASS）。
- 判断：本地核对 checks=数组(8项)；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10-settings-launcher-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10-settings-launcher-results.json:4

## OCR-1208 · 不采纳

- 位置：docs/testing/M10-settings-launcher-results.json:14；OCR原严重度：high
- 计划：无
- 原意见：第 6 行声称 `duplicate launch fails without ... exposing token`，第 14 行声称浏览器/剪贴板副作用在自动化下关闭，但整个记录没有任何脱敏断言或产物摘要（如日志 sha256、`token=<redacted>` 的匹配计数）。触发条件：launcher 在错误路径/重复启动路径把明文 token 写进 stdout、日志或异常栈——该记录仍会 PASS，因为无任何字段可判定输出中是否出现过 token 值。建议增加 `redaction` 块，记录被扫描的产物清单、每份产物的 sha256、敏感模式命中数（应为 0），并且不得把 token 值本身写入本文件。
- 判断：本地核对 capturedAt="2026-10-16T15:22:59.211266+00:00"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10-settings-launcher-results.json:14；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1209 · 不采纳

- 位置：docs/testing/M10-settings-launcher-results.json:11；OCR原严重度：medium
- 计划：无
- 原意见：第 11 行仅以一句话记录了“token 不能覆盖受保护环境变量”，缺少可复现要素：受保护变量名单、设置优先级的期望顺序、以及负向用例的输入/输出（被拒绝时的错误码与变量最终取值）。按当前记录，无法验证环境变量白名单是否真正生效，也无法在产品改动后回归。建议补充 `envWhitelist` 字段：`protected: [...]`、`precedence: [...]`、`negativeCase: { attempted, rejected, observedValue }`。
- 判断：本地核对 capturedAt="2026-10-16T15:22:59.211266+00:00"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10-settings-launcher-results.json:11；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1210 · 部分采纳

- 位置：docs/testing/M10-settings-launcher-results.json:6；OCR原严重度：medium
- 计划：R6-03 / P3
- 原意见：第 6-7 行声称“重复启动失败且不停止已有服务”“退出只关闭自有 Java 服务且不残留监听端口”，但未记录任何前后状态快照（进程 PID/启动时间、监听端口、服务归属标识）。触发条件：退出时误杀他人占用同一端口的实例，或重复启动路径先 stop 再判重导致既有服务被重启——这类竞态与顺序缺陷在本记录中不可见。建议增加 `lifecycle` 块，记录重复启动前后既有服务 PID 与启动时间不变、退出前后监听端口集合（应为空）以及服务归属判定的依据字段。
- 判断：本地核对 capturedAt="2026-10-16T15:22:59.211266+00:00"；status="PASS"。原测试确核对第二启动器非零退出和旧服务仍200，未保存两次PID/startInstant。采纳在重复启动/退出收据中登记自有实例标识与进程存活对照，不以无PID字段推定误杀已发生。
- 证据：docs/testing/M10-settings-launcher-results.json:6；scripts/test-open-workbench.py:92

## OCR-1211 · 不采纳

- 位置：docs/testing/M10-settings-launcher-results.json:17；OCR原严重度：low
- 计划：无
- 原意见：第 17-22 行的 sha256 与第 3 行的 `capturedAt` 只绑定了文件内容，没有绑定源码版本（commit sha / tag / 构建号），也没有记录校验方式。触发条件：脚本或 JAR 取自不同的构建产物但哈希恰好被手工填入，或日后需要复核时无法定位是哪个 commit 的产物，记录无法被独立重放。建议增加 `commitSha`、`buildId` 以及复算命令（例如 `sha256sum` 的目标路径），使本文件可作为可重放的证据。
- 判断：本地核对 capturedAt="2026-10-16T15:22:59.211266+00:00"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10-settings-launcher-results.json:17；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10-settings-launcher-results.json:3

## OCR-1212 · 不采纳

- 位置：docs/testing/M10-settings-first-attempt.json:3；OCR原严重度：medium
- 计划：无
- 原意见：该记录描述了成功的产品断言与失败原因（夹具在 finally 中重复响应导致 ERR_HTTP_HEADERS_SENT），但 description 未指明具体夹具文件/测试用例名，也未说明已加入的幂等释放保护（例如 resolved 标志或 checked release）。后果：资源生命周期缺陷无法被后续审查者定位复现，同一“finally 双次 resolve/release”模式可能在其它夹具中重复出现。建议在 description 中补充夹具相对路径 + 测试名，以及去重手段字段（如 "fixture": "tests/browser/settings.spec.ts#...", "fix": "guarded single release"）。
- 判断：本地核对 status="FAILED_FIXTURE_CLEANUP"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10-settings-first-attempt.json:3；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1213 · 不采纳

- 位置：docs/testing/M10-settings-first-attempt.json:5；OCR原严重度：medium
- 计划：无
- 原意见：files 中的两个摘要只给出 64 位十六进制串，既未声明哈希算法（sha256/sha3-256 等均可产生 64 hex，无法唯一判定），其路径又位于 .gitignore 第 8 行忽略的 .build-cache/ 目录下，仓库内不存在这些产物。后果：该校验记录中的完整性断言不可验证、该失败尝试不可复现——任何人都无法按记录核对哈希或重跑取证。建议显式声明算法并说明产物的留存/获取方式（例如随记录一并归档到版本控制，或注明外部归档位置与保留期）。
- 判断：本地核对 status="FAILED_FIXTURE_CLEANUP"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10-settings-first-attempt.json:5；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1214 · 不采纳

- 位置：docs/testing/M10-settings-first-attempt.json:8；OCR原严重度：medium
- 计划：无
- 原意见：该字段自己承认了跨步骤排序违规：clean 在旧 JAR 仍持有文件锁（服务未停止）时就被执行，且首次失败的原始日志“未保留”。记录只给出结论性的“final full clean verify passed”，既没有失败时的进程/JAR 绝对路径、也没有指向重跑通过结果文件（M10-settings-results.json 等）的引用。后果：这类“先清理后停服”的排序缺陷与恢复行为无法被复现或回归验证（重启后是否仍可能先 clean 再停服完全不可判断）。建议补充：被占用 JAR 路径/占用进程识别方式、harness 是否增加了“先停服再 clean”的前置检查或单实例锁探测，以及通过记录的引用。
- 判断：本地核对 status="FAILED_FIXTURE_CLEANUP"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10-settings-first-attempt.json:8；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1215 · 不采纳

- 位置：docs/testing/M1-results.json:10；OCR原严重度：medium
- 计划：无
- 原意见：该字段指向的 `.build-cache/M1-verify.log` 位于 `.gitignore` 的 `.build-cache/` 目录下，当前仓库快照中并不存在；本文件也没有记录日志的 SHA-256（后续采集脚本已增加 `logSha256`）。另外 `clean verify` 会删除 `target/surefire-reports` 下的原始 JUnit XML。结果是 `buildResult: SUCCESS`、76 项通过以及 0 失败/错误/跳过只能依赖不可见的外部文件，无法从仓库独立复核，日志或报告被替换也无法发现。建议在证据 JSON 中加入日志（以及 JUnit XML）的哈希，并把脱敏后的原始日志作为受控附件或 CI artifact 保留。
- 判断：本地核对 milestone="M1"；capturedAt="2026-10-13T20:37:21.2248830+08:00"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M1-results.json:10；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1216 · 不采纳

- 位置：docs/testing/M10-results.json:11；OCR原严重度：high
- 计划：无
- 原意见：证据完整性问题：该文件同时声明 `status: PASS` 和 `workingTreeDirtyAtCapture: true`，但没有任何字段记录脏工作区的内容标识（无 diff/patch 摘要、无 `git status --porcelain` 列表、无 stash 引用），因此仅凭 `baseCommit` 无法重跑出这 307 个通过用例。更严重的是，`sourceSha256` 只覆盖各模块 `pom.xml` 与 `src/**/*.java`（见 scripts/collect-m10-evidence.py:36），若捕获时脏改动落在资源文件、`frontend/`、`scripts/` 等非 Java 路径上，该改动完全不会被本清单约束，PASS 结论便无法归因到任何确定的源码树。建议要么要求干净工作区后再采集，要么增加 `workingTreeDiffSha256`／脏文件清单字段，使脏树可复现。
- 判断：本地核对 baseCommit="f38d645d4f89a026ced13b2b16c562c8cc14f957"；sourceSha256=对象(263键)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10-results.json:11；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10-results.json:10；docs/testing/M10-results.json:879

## OCR-1217 · 不采纳

- 位置：docs/testing/M10-results.json:12；OCR原严重度：high
- 计划：无
- 原意见：外部证据不可独立校验：`verificationLog` 指向 `.build-cache/M10-full-verify.log`，而 `.gitignore` 第 8 行已忽略 `.build-cache/`，该日志不会随仓库发布；同理 `suites[].sha256` 摘要的是各模块 `target/surefire-reports/TEST-*.xml`，它们也是构建产物。结果是任何第三方拿到仓库后都无法用 `logSha256`／套件摘要复核 307/0/0/0 这一结论（只能信任清单自述）。建议把验证日志与 surefire 汇总随 `docs/testing/` 一并提交，或在清单中内联可校验的最小摘要（套件名 + 用例名 + 源码摘要）。
- 判断：本地核对 verificationLog=".build-cache/M10-full-verify.log"；suites=数组(50项)。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10-results.json:12；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M10-results.json:20

## OCR-1218 · 不采纳

- 位置：docs/testing/M10-results.json:1146；OCR原严重度：medium
- 计划：无
- 原意见：`clientTests` 结构缺陷：1) 缺少 `errors` 字段，与 `suites[]` 的 `tests/failures/errors/skipped` 结构不一致，消费方无法区分“显式 0 错误”和“未采集”；2) 只有 `logSha256` 而没有日志路径字段，日志位于未提交的 `.build-cache/M10-client-tests.log`，该摘要因此无法定位到具体文件；3) 计数 8 是采集脚本中的字面量（scripts/collect-m10-evidence.py:87），并未从日志解析结果得出，日志若只有 `pass 8` 一行也会通过校验。建议补 `errors` 与 `log` 路径，并使计数由日志解析产生。
- 判断：本地核对 clientTests=对象(4键)；totals.errors=0。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10-results.json:1146；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M10-results.json:1145；docs/testing/M10-results.json:17

## OCR-1219 · 不采纳

- 位置：docs/testing/M10-results.json:1160；OCR原严重度：medium
- 计划：无
- 原意见：摘要字段缺少被绑定对象的路径：`jarSha256`、`browserEvidenceSha256`、`npmAuditSha256`、`npmBomSha256` 只有哈希值，没有记录被测 JAR 的路径（实际为 `harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar`，构建产物且不随仓库提交，发布版还会重建出不同哈希）。仅凭本清单无法判断“浏览器验收测的是哪一个产物”，也无法定位 audit/BOM 证据文件。建议为这些摘要各补一个路径字段（如 `jarPath`、`browserEvidence`、`npmAudit`、`npmBom`），使哈希可被定位校验。
- 判断：本地核对 jarSha256="a540f149a16e746ca73a935c0476a1d67cd2007f208df82b898b599d5177f2e4"；browserEvidenceSha256="73c55565507c3e199232ac605af259c694b96db53d487888e6544b1bd99736c5"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10-results.json:1160；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10-results.json:1144

## OCR-1220 · 不采纳

- 位置：docs/testing/M10-results.json:15；OCR原严重度：low
- 计划：无
- 原意见：`totals` 的统计口径未声明：307 仅等于 `suites[]` 各项之和（已逐项累加核对一致），不包含下方 `clientTests.tests: 8`。字段名 `totals` 容易被读成“本次里程碑全部测试数”，从而出现 307 与 315 的口径歧义。建议显式声明范围，例如 `"totalsScope": "java-surefire"`，或拆分为 `javaTotals` / `clientTotals`。
- 判断：本地核对 totals=对象(4键)；suites=数组(50项)。原意见中可见字段本身成立，但没有证明其推测的行为故障；配套源码/阶段记录已限定作用域。仅对旧收据补充元数据或统一风格不作为本轮合理修复任务，保留历史事实字节。
- 证据：docs/testing/M10-results.json:15；docs/testing/M10-validation.md；docs/testing/M10-results.json:14；docs/testing/M10-results.json:20

## OCR-1221 · 不采纳

- 位置：docs/testing/M10-settings-workbench-regression.json:179；OCR原严重度：medium
- 计划：无
- 原意见：截图证据路径使用 Windows 反斜杠分隔符，且全部指向被 Git 忽略的目录（`.gitignore:8` 为 `.build-cache/`），因此该 JSON 入库后其 `sha256` 无从复核：在其他平台（Linux/CI）上该字符串无法解析为路径，在克隆仓库中也找不到对应文件，无法区分“证据丢失”与“报告被篡改”。建议改用仓库相对的正斜杠路径，并把截图落到可提交的 `docs/testing/evidence/` 目录，或在报告中显式标注这些证据不随仓库分发（例如 `evidenceCommitted: false`），避免下游把不可解析的哈希当成已核验证据。
- 判断：本地核对 screenshots[0].sha256="338313e3c9448416025640d7c2e8edbf8d4277d184c12752639ee5ab8405705f"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10-settings-workbench-regression.json:179；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M10-settings-workbench-regression.json:180

## OCR-1222 · 不采纳

- 位置：docs/testing/M10-settings-workbench-regression.json:211；OCR原严重度：medium
- 计划：无
- 原意见：`jarSha256` 与上方 `sourceSha256` 清单之间没有任何绑定信息：缺少 `baseCommit`/`dirty` 标记、构建命令、产物清单（如包内 UI 资源哈希）以及被哈希源文件与 JAR 的对应关系。同目录的 `M10-results.json:10` 已记录 `baseCommit`，本文件却未记录，导致无法判断被测 JAR 是否由这份哈希清单构建，也无法复现本次回归。建议补充 `baseCommit`、`dirty`、构建命令与实际打包 UI 的哈希，形成“提交 → 源码哈希 → 产物哈希”的可校验链条。
- 判断：本地核对 jarSha256="71cf2b88745f042beb0ba85e215f542f14623f30d1f9bf44a5879217cf7b43d5"；sourceSha256=对象(43键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10-settings-workbench-regression.json:211；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10-settings-workbench-regression.json:127

## OCR-1223 · 不采纳

- 位置：docs/testing/M10-settings-workbench-regression.json:2；OCR原严重度：medium
- 计划：无
- 原意见：`status` 与 28 个 `passed` 布尔值是纯自证：每个 check 只有自由文本 `name` 和真假值，没有稳定 ID、断言输出、耗时、失败详情或关联日志/截图的引用，也没有由 checks 派生的失败计数。任何一次手工编辑（改 `passed` 或直接复制旧报告）都会被当作发布门禁通过，无法区分“该项从未执行”与“该项执行并通过”。建议为每个 check 增加稳定 `id`、`evidence`（日志行或截图哈希引用）与派生的 `total`/`failed` 汇总，并在采集脚本中断言 `status` 严格由 check 结果计算得出。
- 判断：本地核对 status="PASS"；checks[0].passed=true。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10-settings-workbench-regression.json:2；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29；docs/testing/M10-settings-workbench-regression.json:10

## OCR-1224 · 不采纳

- 位置：docs/testing/M10-settings-workbench-regression.json:173；OCR原严重度：medium
- 计划：无
- 原意见：报告中的多条关键断言（“lost submit response survives refresh … with no POST replay”、“offline recovery never creates a new task”、“invalid cursor pauses stream rather than silently replaying”）都依赖“没有重放”这一事实，但此处只给出聚合计数 `taskSubmissions: 9`，没有任何按场景的请求轨迹或请求日志引用，因此无法证明这些场景各自发送了多少次 POST、也未证明被拒/不重放的提交确实未产生任务。建议按场景记录请求明细（方法、路径、次数）或引用原始请求日志的哈希，使“无重放”可独立复算。
- 判断：本地核对 capturedAt="2026-10-16T15:25:56.862Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10-settings-workbench-regression.json:173；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1225 · 不采纳

- 位置：docs/testing/M10-settings-workbench-regression.json:121；OCR原严重度：low
- 计划：无
- 原意见：`runtime` 中的计数器缺少定义：`opened`（9）未说明统计对象是任务打开、页面打开还是会话打开，`modelRequests` 也未说明是真实模型还是替身请求，与 `requests.taskSubmissions` 之间不存在可推导关系，导致验收方无法判断两者一致或不一致意味着什么（例如 9 次提交与 9 次 opened 是否说明所有提交都成功，与“invalid credentials … do not connect or create a task”是否存在冲突）。建议在该对象内补充每个计数的口径说明，或改用自解释的键名（如 `tasksOpened`、`fakeModelRequests`）。
- 判断：本地核对 runtime=对象(4键)；runtime.opened=9。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10-settings-workbench-regression.json:121；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10-settings-workbench-regression.json:124

## OCR-1226 · 不采纳

- 位置：docs/testing/M10.5.1-launcher-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：第 4-13 行的 8 项 check 只是自由文本字符串，没有每项的 id/结果/退出码/耗时，也没有记录产生该文件的具体命令与运行器退出状态。触发条件：`scripts/test-open-workbench.py` 中任一断言被删改或弱化（例如第 86 行把未鉴权根路径断言改宽、第 95 行放宽 returncode），只要 check 文案不变、仍 append 8 条，本文件依旧是 `"status": "PASS"`，唯一自动消费方（`scripts/collect-m1051-evidence.py` 第 51/67 行仅校验 `len(checks)==8` 与 `paidRequests==0`）无法发现。建议改为逐项对象（id/status/evidence，例如断言失败时记录异常文本），并写入运行器退出码，避免“文案即证据”。
- 判断：本地核对 capturedAt="2026-10-17T01:28:57.519091+00:00"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.1-launcher-results.json:4；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1227 · 部分采纳

- 位置：docs/testing/M10.5.1-launcher-results.json:14；OCR原严重度：medium
- 计划：R6-03 / P2
- 原意见：第 14 行把“production opens browser directly and never copies a credential”写进 PASS 证据，但测试的两个入口（`scripts/test-open-workbench.py` 第 81、103、113 行）始终传 `-NoBrowser -NoClipboard`，`scripts/open-workbench.ps1` 第 137 行的 `Start-Process` 分支从未被执行；且 ps1 里根本没有剪贴板代码，`-NoClipboard` 是空开关。触发条件：默认双击 web-ui.cmd（不传 -NoBrowser）时浏览器路径或未来加入的剪贴板逻辑出现凭据泄漏，本清单仍会输出同样的 PASS 文案。建议明确标注该项为“未实测的声明”，或让测试真正覆盖默认（打开浏览器）路径并断言 stdout/stderr/剪贴板均无 token。
- 判断：本地核对 capturedAt="2026-10-17T01:28:57.519091+00:00"；status="PASS"。自动化明确传-NoBrowser/-NoClipboard，生产默认打开浏览器的分支不属于这8项实测。采纳把静态生产说明与自动化结果分开，增加可控浏览器调用替身验证；不要求每次验收真实改用户剪贴板。
- 证据：docs/testing/M10.5.1-launcher-results.json:14；scripts/test-open-workbench.py:82；scripts/test-open-workbench.py:137；scripts/open-workbench.ps1

## OCR-1228 · 不采纳

- 位置：docs/testing/M10.5.1-launcher-results.json:15；OCR原严重度：medium
- 计划：无
- 原意见：第 15-16 行的 `paidRequests: 0` 与“no task submissions”是测试脚本第 137-138 行硬编码的常量，不是测量结果：没有网络抓包、没有出站连接计数，也没有断言。触发条件：`open-workbench.ps1` 第 76-79 行在未传 `-Config` 且工作区无 `.jharness\config.json` 时会回退到 `docs/examples/deepseek-config.json`（该文件未在 sourceSha256 中固定，测试中也未覆盖），一旦走到真实 baseUrl 并提交任务，本清单仍会如实写出 `paidRequests: 0`。建议改为由实际出站请求记录（或对 `:9` 之外目标连接的断言）生成该字段，并在文案中区分“夹具端点”与“产品默认配置端点”。
- 判断：本地核对 capturedAt="2026-10-17T01:28:57.519091+00:00"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.1-launcher-results.json:15；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1229 · 不采纳

- 位置：docs/testing/M10.5.1-launcher-results.json:17；OCR原严重度：medium
- 计划：无
- 原意见：第 17-22 行的 SHA-256 是测试脚本在本次运行末尾（`scripts/test-open-workbench.py` 第 136-140 行）对磁盘上当前文件重新计算后写出的，且没有任何期望值与仓库基线做比对，因此它们只能证明“跑完之后文件长这样”，无法发现被替换/漂移的产物；同时缺少 git commit、OS 版本、PowerShell 版本（测试第 73 行强制 Windows PowerShell 5.1）与 Java 21 具体版本，也无 `docs/examples/deepseek-config.json` 等默认回退配置的哈希。触发条件：用被篡改的 web-ui.cmd/open-workbench.ps1 重跑测试，本清单会合法地生成一组新的哈希并仍为 PASS。建议把期望哈希作为基线（测试内比对失败即失败）并记录运行环境与提交号。
- 判断：本地核对 capturedAt="2026-10-17T01:28:57.519091+00:00"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.1-launcher-results.json:17；scripts/test-open-workbench.py:136

## OCR-1230 · 不采纳

- 位置：docs/testing/M10.5.1-launcher-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：第 4-13 行的 8 项 check 只是自由文本字符串，没有每项的 id/结果/退出码/耗时，也没有记录产生该文件的具体命令与运行器退出状态。触发条件：`scripts/test-open-workbench.py` 中任一断言被删改或弱化（例如第 86 行把未鉴权根路径断言改宽、第 95 行放宽 returncode），只要 check 文案不变、仍 append 8 条，本文件依旧是 `"status": "PASS"`；唯一自动消费方（`scripts/collect-m1051-evidence.py` 第 51/67 行仅校验 `len(checks)==8` 与 `paidRequests==0`）无法发现。建议改为逐项对象（id/status/evidence，断言失败时记录异常文本），并写入运行器退出码，避免“文案即证据”。
- 判断：本地核对 capturedAt="2026-10-17T01:28:57.519091+00:00"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.1-launcher-results.json:4；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1231 · 部分采纳

- 位置：docs/testing/M10.5.1-launcher-results.json:14；OCR原严重度：medium
- 计划：R6-03 / P2
- 原意见：第 14 行把“production opens browser directly and never copies a credential”写进 PASS 证据，但测试的三个启动点（`scripts/test-open-workbench.py` 第 81、103、113 行）始终传 `-NoBrowser -NoClipboard`，`scripts/open-workbench.ps1` 第 137 行的 `Start-Process` 分支从未执行；且 ps1 中没有剪贴板代码，`-NoClipboard` 为空开关。触发条件：默认双击 web-ui.cmd（不传 -NoBrowser）时浏览器路径或今后加入的剪贴板逻辑出现凭据泄漏，本清单仍输出同样 PASS 文案。建议将该句标注为“未实测的声明”，或让测试覆盖默认（打开浏览器）路径并断言 stdout/stderr/剪贴板中均无 token。
- 判断：本地核对 capturedAt="2026-10-17T01:28:57.519091+00:00"；status="PASS"。自动化明确传-NoBrowser/-NoClipboard，生产默认打开浏览器的分支不属于这8项实测。采纳把静态生产说明与自动化结果分开，增加可控浏览器调用替身验证；不要求每次验收真实改用户剪贴板。
- 证据：docs/testing/M10.5.1-launcher-results.json:14；scripts/test-open-workbench.py:82；scripts/test-open-workbench.py:137；scripts/open-workbench.ps1

## OCR-1232 · 采纳

- 位置：docs/testing/M10-launcher-results.json:9；OCR原严重度：medium
- 计划：R6-03 / P2
- 原意见：该 PASS 证据声称“saved workspace config is selected”，但归档运行对应的 scripts/test-open-workbench.py 在第二次启动时并未传 `-Config`，只断言根路径返回 200、以及旧 token 访问 /v1/info 得到 401。若启动器忽略工作区 `.jharness/config.json` 而回退到默认 `docs/examples/deepseek-config.json`，同样会生成一个新 token，这三条断言依然全部成立。触发条件：launcher 的配置选择逻辑回归为“总是使用默认配置”，本文件的第 5 条仍会记录 PASS。建议增加可区分断言（例如在保存配置中放入唯一的 baseUrl/model，并从 /v1/info 或启动日志核对该值），或把该条描述收窄为实际验证的内容。
- 判断：本地核对 capturedAt="2026-10-16T14:40:07.229035+00:00"；status="PASS"。saved配置分支只验证首页200及旧token401；若回退默认配置也生成新token，两断言仍通过。需在保存配置植入唯一可观察model/profile并断言实际选中。
- 证据：docs/testing/M10-launcher-results.json:9；scripts/test-open-workbench.py:110

## OCR-1233 · 部分采纳

- 位置：docs/testing/M10-launcher-results.json:11；OCR原严重度：medium
- 计划：R6-03 / P2
- 原意见：该 PASS 证据声称“token cannot overwrite protected environment variables”，但归档测试只用 `tokenEnv=PATH` 触发保护；而启动器实现是固定黑名单（PATH/JAVA_HOME/HOME/CODEX_HOME/SystemRoot/ComSpec/TEMP/TMP）。触发条件：配置文件把 `ecosystem.web.tokenEnv` 设为 USERPROFILE、JAVA_TOOL_OPTIONS、NODE_OPTIONS 等未列入黑名单的敏感变量时，token 仍会被写入子进程环境并正常启动，而本文件依然记录 PASS。按“环境变量白名单”要求，现有证据不足以支撑“受保护环境变量不可被覆盖”的通用结论；建议改为白名单校验（仅允许专用的 JHARNESS_* 名称），或至少扩展该检查覆盖上述变量后再归档 PASS。
- 判断：本地核对 capturedAt="2026-10-16T14:40:07.229035+00:00"；status="PASS"。当前负例仅tokenEnv=PATH，能证明这一变量拒绝，不能证明所有敏感变量均受保护。采纳补变量名边界反例并核对专用命名契约，不能把产品命令环境白名单与启动器tokenEnv混为同一机制。
- 证据：docs/testing/M10-launcher-results.json:11；scripts/test-open-workbench.py:128；scripts/open-workbench.ps1

## OCR-1234 · 采纳

- 位置：docs/testing/M10-launcher-results.json:7；OCR原严重度：low
- 计划：R6-03 / P2
- 原意见：“quit gracefully closes owned Java service and leaves no listening port”的证据仅来自退出后 HTTP 请求失败，而且超时也被当作“端口已释放”通过；它既未确认 launcher 进程已结束，也未确认 Java 子进程已终止或端口可重新绑定。触发条件：`:quit` 后 Java 子进程残留且挂起（仍占用监听端口但不响应），该检查仍会记录 PASS，造成资源生命周期结论失真。建议补充子进程退出码/存活检查，或尝试在同一端口重新绑定后再认定通过。
- 判断：本地核对 capturedAt="2026-10-16T14:40:07.229035+00:00"；status="PASS"。stop先等待启动器退出但端口只靠HTTP失败判断，URLError/TimeoutError均视为通过；存活但无响应的Java监听可能漏检。需核对所拥有Java进程终止与端口可重绑，不能杀无关进程。
- 证据：docs/testing/M10-launcher-results.json:7；scripts/test-open-workbench.py:62

## OCR-1235 · 不采纳

- 位置：docs/testing/M10.5.1-diagnosis-results.json:2；OCR原严重度：low
- 计划：无
- 原意见：顶层 `status` 是固定字面量，不是由 `checks[].passed` 推导。对应生成器 frontend/tests/diagnosis.mjs 第 93 行直接写 `status: 'PASS'`，而 `checks` 只在断言成功时 push（第 20 行），因此一旦某个 check 被条件跳过或从数组中丢失，本文件仍会输出 PASS 且无任何失败/跳过计数可用于交叉核对。建议同时记录 `checks.length`、`failed`/`skipped` 计数（或由 checks 推导 status），否则本文件无法自证 13 项检查全部实际执行且全部通过。
- 判断：本地核对 status="PASS"；checks=数组(13项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.1-diagnosis-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.1-diagnosis-results.json:4

## OCR-1236 · 不采纳

- 位置：docs/testing/M10.5.1-diagnosis-results.json:76；OCR原严重度：medium
- 计划：无
- 原意见：本文件缺少 `browserEngine` 字段，而当前生成器 frontend/tests/diagnosis.mjs 第 93 行无条件写入 `browserEngine: process.env.JHARNESS_BROWSER_ENGINE ?? 'chromium'`。这说明该证据文件不是由当前 fixture 产出的（陈旧或人工整理），并且无法看出这条 PASS 究竟来自 chromium/firefox/webkit 中的哪个引擎；scripts/run-workbench-matrix.mjs 第 33 行会自行重写该字段，所以缺失不会立刻报错，但会静默掩盖“同一 PASS 被复用到其他引擎”的情况，弱化跨引擎兼容性结论的可复现性。建议补上 `browserEngine`（以及生成器/日志指纹）后重新采集。
- 判断：本地核对 capturedAt="2026-10-17T01:28:51.630Z"；status="PASS"。将当前源码字段/新功能文件集套用到带日期的旧里程碑快照，不能证明旧记录陈旧造假或漏测。M10.5.9新增跨引擎runtime/transportDiagnostics及M10.5.6～.8新增UI/检索文件，旧阶段不应补填这些后来字段或重标通过。
- 证据：docs/testing/M10.5.1-diagnosis-results.json:76；.agent:当前接续状态；docs/testing/M10.5.9-validation.md:26

## OCR-1237 · 不采纳

- 位置：docs/testing/M10.5.1-diagnosis-results.json:84；OCR原严重度：medium
- 计划：无
- 原意见：`requests` 只记录 `path` + `method`，既无响应状态码、无时间戳，也没有查询串（生成器第 42 行用 `new URL(r.url()).pathname`），且只订阅 page 请求事件，通过 `context.request` 发出的校验请求不会进入该数组。结果是这份“完整请求序列”无法支撑多个关键结论：例如 check 8「refresh … without task submission or unsafe verification」仅依赖 `POST /v1/tasks` 的计数，而任何被 4xx/5xx 拒绝或重试的审批/取消写入在日志中与成功请求完全无法区分；`paidRequests: 0`、`modelRequests: 5` 也无从与请求序列对账（host 端 `paidRequests` 为常量 0）。建议在每条记录中加入 status、时间戳与完整 URL（或 query），使 PASS 结论可独立复算。
- 判断：本地核对 requests=数组(89项)；screenshots[0].path=".build-cache\\M10.5.1-shell.png"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.1-diagnosis-results.json:84；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.1-diagnosis-results.json:60

## OCR-1238 · 不采纳

- 位置：docs/testing/M10.5.1-diagnosis-results.json:60；OCR原严重度：low
- 计划：无
- 原意见：截图路径使用 Windows 反斜杠（实际值为 `.build-cache\M10.5.1-shell.png`）。路径确实位于 `.build-cache` 内、无绝对路径或 `..` 穿越，四条 `sha256` 也都是合法 64 位十六进制；但该证据文件会被跨平台消费，POSIX 工具按原值 `resolve` 时会把反斜杠当作文件名字符，导致找不到文件、哈希校验失败（run-workbench-matrix.mjs 依赖 `replaceAll('\\','/')` 才能通过）。建议统一存成 `/` 分隔的相对路径，避免只有做归一化的消费者才能读取。
- 判断：本地核对 screenshots[0].sha256="9e4b27d766c7452381926635e77ee47aeaa4e2685ac527fc4875cb6222ce292e"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.1-diagnosis-results.json:60；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M10.5.1-diagnosis-results.json:61

## OCR-1239 · 不采纳

- 位置：docs/testing/M10.5.2-history-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：顶层 `status` 只能从 checks 中的 `passed` 布尔值推导，本文件既没有 `total/failed/skipped/error` 计数，也没有绑定被测源码版本（只有 `jarSha256`，无 commit/sourceSha256）。触发条件：运行器在只执行了前 N 项检查后异常退出（浏览器崩溃、宿主未启动），若 `status` 按“已产出的 checks 全部为 true”计算，归档结果仍会被写成 `PASS`，失败或未执行项被静默吞掉；同样无法证明这份 PASS 对应哪次源码快照。建议补充 `totalChecks/failed/skipped/aborted` 与源码标识，且 `status` 仅在 totalChecks==checks.length 且无 skipped 时为 PASS。
- 判断：本地核对 status="PASS"；checks[0].passed=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.2-history-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.2-history-results.json:8

## OCR-1240 · 不采纳

- 位置：docs/testing/M10.5.2-history-results.json:27；OCR原严重度：medium
- 计划：无
- 原意见：安全类断言（导出 JSONL 不含凭据、浏览器无凭据泄漏）在此只留下一个 `passed: true`，没有任何可复核的产物引用（如被扫描的导出文件 SHA、脱敏前后控制台/网络日志 SHA），也没有植入假凭据的负向对照说明。触发条件：脱敏逻辑回归或断言被短路（例如导出/控制台拦截未真正执行）时，该文件仍会记录通过，无法区分“确实检查过”和“断言未生效”。同样，多条 `without model calls/without model request` 检查也没有逐项请求计数差，仅有全局 `modelRequests: 138`，无法证明这几次操作没有额外请求。建议为每条安全/无副作用断言补充证据路径与逐项计数快照。
- 判断：本地核对 capturedAt="2026-10-17T05:16:14.309Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.2-history-results.json:27；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1241 · 不采纳

- 位置：docs/testing/M10.5.2-history-results.json:85；OCR原严重度：medium
- 计划：无
- 原意见：截图证据使用 Windows 反斜杠分隔符且全部位于已被 `.gitignore` 忽略的 `.build-cache/`：一方面在 Linux/macOS 上按该字符串无法定位文件，另一方面仓库内不存在这些 PNG，`sha256` 只能与本地残留文件比对，缓存清理后证据链不可复现，也无法从提交历史核对是否属于本次运行。触发条件：换机器/清理构建缓存后重新核验，或该结果文件被复用到其他里程碑时，哈希校验必然失败或退化为不可验证。建议统一使用正斜杠相对路径，并把本次运行的截图哈希与运行标识（JAR/源码 SHA、capturedAt）绑定归档到可提交的清单中。
- 判断：本地核对 screenshots[0].sha256="805e1244b4957e1d072e1abce0de166b15e914883336597ab69e10837de4be66"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.2-history-results.json:85；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M10.5.2-history-results.json:86

## OCR-1242 · 不采纳

- 位置：docs/testing/M10.5.1-workbench-results.json:186；OCR原严重度：high
- 计划：无
- 原意见：M10-welcome.png（第186行）与 M10-connection.png（第182行）的 sha256 完全相同（520c5845…），即“连接态”与“欢迎/初始态”两张不同 UI 证据落到了同一份字节上，说明其中一次的截图没有被真正抓取（漏拍或复用了上一张）。采集脚本（scripts/collect-m1051-evidence.py 第63-64行）只校验“哈希与 .build-cache 源文件一致”，无法发现两条记录指向同一内容，因此该 PASS 不能证明 welcome 状态被覆盖。建议在采集端增加“所有截图哈希互不重复”的断言，并修正 welcome 截图的抓取时机后重新采集。
- 判断：本地核对 capturedAt="2026-10-17T01:32:23.188Z"；status="PASS"。相同内容会有相同摘要，不能反推未执行或复制。浏览器自动同源进入使connection/welcome可已是同一状态；夹具日志仅打印确定性的PASS名称，不同引擎/复跑可逐字相同。应核对执行入口和制品来源，不能要求截图/日志哈希强制互异。
- 证据：docs/testing/M10.5.1-workbench-results.json:186；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1243 · 不采纳

- 位置：docs/testing/M10.5.1-workbench-results.json:182；OCR原严重度：medium
- 计划：无
- 原意见：截图路径使用 Windows 反斜杠并指向 .build-cache/（.gitignore 第8行已忽略该目录），因此这份已提交的证据在克隆仓库后无法按 path 定位任何文件，只能信任记录里的哈希字符串，独立复现已不可能。建议改用正斜杠相对路径并把关键截图归档到受版本控制的目录（如 docs/testing/screenshots/M10.5.1/），或在此产物中记录可重跑的采集命令与截图基目录。
- 判断：本地核对 capturedAt="2026-10-17T01:32:23.188Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.1-workbench-results.json:182；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1244 · 不采纳

- 位置：docs/testing/M10.5.1-workbench-results.json:7；OCR原严重度：medium
- 计划：无
- 原意见：28 项检查全部被压缩为仅含 name 与 passed:true 的布尔值，没有记录期望值/实际值、请求 ID、日志行或截图索引。对于“token absent from URL, storage, page text and downloaded report”（第93行）、“UNKNOWN … no replay control”（第73行）、“configured secret is redacted”（第85行）这类断言，一旦断言被跳过（例如选择器变更导致检查体未执行、storage 枚举为空），结果仍然是 true 并整体输出 PASS，缺陷会被静默掩盖。建议每项至少记录关键观测值或对应日志/截图引用，并在采集端用“检查数 + 名称集合”做白名单校验（当前脚本只校验数量 28）。
- 判断：本地核对 capturedAt="2026-10-17T01:32:23.188Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.1-workbench-results.json:7；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1245 · 不采纳

- 位置：docs/testing/M10.5.1-workbench-results.json:214；OCR原严重度：medium
- 计划：无
- 原意见：jarSha256 与 sourceSha256 清单之间没有任何绑定：清单只覆盖 frontend/、harness-web/ 与 harness-testkit/ 三个位置，未包含根/各模块 pom.xml 之外的其它后端模块源码，也没有记录构建命令、模块列表、OS/Java 版本。因此一个包含未列出模块（或与清单源码不一致的构建参数）产出的 JAR 仍会显示同一 PASS，无法从产物反查到被审阅的源码树。建议记录构建命令与参与构建的模块清单（或直接记录 JAR 内 class 的哈希索引），并与 sourceSha256 一并校验。
- 判断：本地核对 capturedAt="2026-10-17T01:32:23.188Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.1-workbench-results.json:214；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1246 · 不采纳

- 位置：docs/testing/M10.5.1-workbench-results.json:124；OCR原严重度：low
- 计划：无
- 原意见：runtime 同时给出 modelRequests: 14 与 paidRequests: 0，且第117行断言“keyless runtime reports zero paid requests”，但产物里没有记录实际使用的模型端点/是否 stub、也没有每个请求的计费归属，所以 0 既可能代表真的没有付费调用，也可能代表计费计数未被埋点（例如仅在成功路径自增）。建议记录模型端点的 host/是否本地 stub，并按请求输出计入 paid 的判定依据（或至少记录 0 是由哪个计数器/日志统计得出）。
- 判断：本地核对 capturedAt="2026-10-17T01:32:23.188Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.1-workbench-results.json:124；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1247 · 不采纳

- 位置：docs/testing/M10.5.1-results.json:1222；OCR原严重度：medium
- 计划：无
- 原意见：`acceptanceSha256` 的键名指向的是历史 M10 回执文件名，而不是本次复制的证据文件，导致哈希不可复现/定位错误。实际核对：`docs/testing/M10-launcher-results.json` 的 `jarSha256` 为 `a540f149…`、`M10-settings-browser-results.json` 为 `71cf2b88…`，与本文件的最终 JAR `0303b883…` 不同；而本次发布的副本 `docs/testing/M10.5.1-launcher-results.json`/`M10.5.1-workbench-results.json` 的 `jarSha256` 才是 `0303b883…`。因此第三方按这些键名去仓库中计算哈希会得到不一致结果，无法确认对应证据。建议键名改为实际发布名（`M10.5.1-*-results.json`）或给出完整缓存路径（如 `.build-cache/M10-*-results.json`），使键名与可校验的文件一一对应。
- 判断：本地核对 acceptanceSha256=对象(4键)；jarSha256="0303b8837e29260f21e2a8c4e85419afd3037c1a8684c3735cafa04b09d98b00"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.1-results.json:1222；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.1-results.json:1228

## OCR-1248 · 不采纳

- 位置：docs/testing/M10.5.1-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：修复前的诊断日志与修复后的最终浏览器诊断日志哈希完全相同（均为 `fd051396…`），而中间一轮 `M10.5.1-diagnosis-browser-first.log` 又是另一个哈希。若最终日志只是先前日志的拷贝，则本 PASS 无法证明滚动修复后重新执行过诊断；若确为内容相同，也应在记录中说明（例如日志不含时间戳）。建议在采集脚本中为每次运行写入时间戳/run id，或在本文件为这两个条目添加区分字段（如 capturedAt、runId），避免重复哈希削弱证据可复现性。
- 判断：本地核对 milestone="M10.5.1"；capturedAt="2026-10-17T01:33:02.955493+00:00"。相同内容会有相同摘要，不能反推未执行或复制。浏览器自动同源进入使connection/welcome可已是同一状态；夹具日志仅打印确定性的PASS名称，不同引擎/复跑可逐字相同。应核对执行入口和制品来源，不能要求截图/日志哈希强制互异。
- 证据：docs/testing/M10.5.1-results.json:1；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1249 · 不采纳

- 位置：docs/testing/M10.5.2-launcher-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：顶层只有一个聚合的 `"status": "PASS"`，而 `checks` 数组全是无状态的自然语言字符串：没有逐项的 status/退出码/执行命令/输出摘要/日志引用。触发条件：任意一条子检查（例如重复启动不停止既有服务、退出后端口释放）失败时，整体仍可写成 PASS，且外部读者无法复现或机器校验结论。建议把 checks 改为对象数组（name/status/exitCode/command/logRef），并显式声明证据格式版本，便于后续消费者按版本解析。
- 判断：本地核对 checks=数组(8项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.2-launcher-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.2-launcher-results.json:4

## OCR-1250 · 不采纳

- 位置：docs/testing/M10.5.2-launcher-results.json:3；OCR原严重度：medium
- 计划：无
- 原意见：证据未记录运行环境：没有 OS 版本、PowerShell 版本、是否安装 JDK / JAVA_HOME 状态、自动化开关等。触发条件：`missing Java produces bounded actionable error` 与 `web-ui.cmd starts and stops the real packaged UI` 这类结果强依赖环境构造（必须在无 Java 的会话中执行），缺少环境字段时他人按本次记录无法复现，也无法判断结果是否来自真实缺 Java 场景。建议新增 environment 字段记录这些维度（不含任何凭据）。
- 判断：本地核对 capturedAt="2026-10-17T05:16:02.551797+00:00"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.2-launcher-results.json:3；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1251 · 不采纳

- 位置：docs/testing/M10.5.2-launcher-results.json:17；OCR原严重度：medium
- 计划：无
- 原意见：三个源文件哈希只有裸值，未绑定 commit SHA / buildId / 版本号 / 采集主机，也无签名。触发条件：仓库在测试后继续提交，或有人替换/重新生成 launcher 脚本时，本文件无法自证这些哈希对应的是哪一次构建，陈旧或伪造的证据同样会呈现 PASS 且哈希“自洽”。建议在顶层增加 commit 与 buildId，并让 sourceSha256 明确标注所对应的工作区版本。
- 判断：本地核对 capturedAt="2026-10-17T05:16:02.551797+00:00"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.2-launcher-results.json:17；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1252 · 不采纳

- 位置：docs/testing/M10.5.2-launcher-results.json:22；OCR原严重度：medium
- 计划：无
- 原意见：JAR 哈希只有一个字符串，缺少产物标识（JAR 文件名/路径、版本或构建任务ID），与 sourceSha256 的结构也不一致。触发条件：同一发布目录下存在多个 JAR，或构建产物被同版本重打包时，无法判定该哈希校验的是哪个文件，provenance 校验会失效。建议改为对象形式（如 { "path": ..., "sha256": ... }）或至少补充 jarVersion/buildId。
- 判断：本地核对 capturedAt="2026-10-17T05:16:02.551797+00:00"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.2-launcher-results.json:22；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1253 · 不采纳

- 位置：docs/testing/M10.5.2-launcher-results.json:11；OCR原严重度：medium
- 计划：无
- 原意见：这是一条安全关键断言（环境变量白名单防护），但记录中只有结论文字：没有列出受保护变量的名字、没有负向用例的输入与期望/实际输出、也没有脱敏后的日志引用。触发条件：白名单实现回退或新增变量绕过校验时，本证据仍为 PASS，无法区分“真的拦截了”与“根本没有发起覆盖尝试”。建议附带覆盖尝试的变量名清单（仅名称）、退出码与脱敏后的错误输出。
- 判断：本地核对 capturedAt="2026-10-17T05:16:02.551797+00:00"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.2-launcher-results.json:11；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1254 · 不采纳

- 位置：docs/testing/M10.5.2-diagnosis-results.json:60；OCR原严重度：high
- 计划：无
- 原意见：证据溯源缺陷：本文件是 M10.5.2 的诊断运行证据，但四张截图（第 60/64/68/72 行）全部沿用 `M10.5.1-*` 文件名，与 docs/testing/M10.5.1-diagnosis-results.json 中记录的路径完全相同。触发条件：任一里程碑重跑诊断场景即写入同一路径 → M10.5.1 的历史证据被 M10.5.2 的内容覆盖，而两份 JSON 仍引用同一路径却记录不同 sha256，评审时无法判定截图归属哪个里程碑，也无法回看 M10.5.1 的原始画面。建议截图文名带里程碑/运行唯一后缀。另外 `\\` 分隔符是 Windows 专有写法，在 POSIX/CI 侧按 `/` 拼接或做路径白名单校验时会解析失败，建议统一为 `/`。
- 判断：本地核对 capturedAt="2026-10-17T05:16:11.903Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.2-diagnosis-results.json:60；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1255 · 不采纳

- 位置：docs/testing/M10.5.2-diagnosis-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：顶层 `status: PASS` 是自证结论：文件内没有 milestone/runId/commit/jar 构建标识，也没有失败明细字段，且未定义「PASS 当且仅当 checks 全部 passed=true」的校验规则。触发条件：脚本只写 status 而 checks 中任一项为 false（或 checks 为空数组）时，下游校验仍会读到 PASS。建议补充 runId/milestone/commit 字段，并由 checks 聚合派生 status，同时给出 failed 列表。
- 判断：本地核对 capturedAt="2026-10-17T05:16:11.903Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.2-diagnosis-results.json:2；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1256 · 不采纳

- 位置：docs/testing/M10.5.2-diagnosis-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：13 条检查（第 4-56 行）只有 name+passed 布尔值，缺 observed/expected、断言耗时与失败原因，因此像「actual approval expiry is shown as denied and body not entered」「UI never equates environment closure or empty residuals to task success」这类语义断言无法从证据文件复核，回归时也无法定位是哪一断言退化。建议每条 check 增加 expected/observed（或 error/detail）字段，失败时保留实际观测文本。
- 判断：本地核对 capturedAt="2026-10-17T05:16:11.903Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.2-diagnosis-results.json:1；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1257 · 不采纳

- 位置：docs/testing/M10.5.2-diagnosis-results.json:81；OCR原严重度：medium
- 计划：无
- 原意见：计数语义未定义，导致「零付费请求」不可证伪：`modelRequests: 5` 与 `paidRequests: 0` 并存，但文件未说明如何区分本地/mock 路由与付费 provider（缺少 upstream/host 或 route 字段），也没记录谁是付费计数来源。同样 `writeExists: false` 是负向断言却没有记录被检查的路径或 glob，边界不明。触发条件：请求被转发到真实付费端点且未计入 paidRequests 时，此处仍会显示 0。建议为每个请求补充目标路由/主机信息，并为 writeExists 记录受检路径。
- 判断：本地核对 capturedAt="2026-10-17T05:16:11.903Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.2-diagnosis-results.json:81；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1258 · 不采纳

- 位置：docs/testing/M10.5.2-diagnosis-results.json:85；OCR原严重度：medium
- 计划：无
- 原意见：requests 日志（第 84-428 行）每条只有 path+method，缺 status/timestamp/duration/error，因此「no browser runtime errors」「actual approval expiry is shown as denied」「fixture closes with zero paid requests」全部没有可核对的支撑：无法区分 2xx/4xx/5xx，无法发现被重试或静默失败的调用。另外作用域与重放只能靠推断——4 次 `POST /v1/tasks` 与 `runtime.opened=4` 的数量巧合被当作「无重复提交」的证据，但没有把请求与会话/任务 ID 关联，也没有记录两处 `POST /approvals/{id}` 是否仅作用于各自 task ID。建议补充响应码与 taskId/sessionId 关联字段，并显式标注「submit 次数上限」断言。
- 判断：本地核对 capturedAt="2026-10-17T05:16:11.903Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.2-diagnosis-results.json:85；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1259 · 不采纳

- 位置：docs/testing/M10.5.2-diagnosis-results.json:76；OCR原严重度：low
- 计划：无
- 原意见：`browserVersion: 155.0.8059.82` 只给版本号，缺少浏览器名称/引擎（Chrome / Chromium / Edge 同号段无法区分）；`jarSha256` 也未与 jar 版本或 commit 关联，跨机器复现时无法确定应使用哪个构建产物的前端资源（日志中的 `index-BLz16l9t.js` 等指纹同样未与被测构建绑定校验）。建议补充 browserName/engine 与 buildVersion 或 commit 字段。
- 判断：本地核对 jarSha256="442fe79c6b4dc54b7a60cc45d6b52953f945d9e9f8e9084c5ec11e268ccbf336"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.2-diagnosis-results.json:76；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.2-diagnosis-results.json:77

## OCR-1260 · 不采纳

- 位置：docs/testing/M10.5.3-turns-results.json:5；OCR原严重度：medium
- 计划：无
- 原意见：该检查项仅记录 `passed: true`，缺少可复现证据字段（测试用例 ID/文件路径、执行命令、断言日志或失败诊断）。当前结构下，PASS 结论无法被独立复核，尤其涉及高风险的并发单执行、重启/UNKNOWN 不重放等断言。建议在 checks 元素中增加 `testId`/`command`/`logPath`/`durationMs` 等字段，使每条断言可追溯到具体测试实现。
- 判断：本地核对 capturedAt="2026-10-17T05:51:06.825Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.3-turns-results.json:5；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1261 · 不采纳

- 位置：docs/testing/M10.5.3-turns-results.json:68；OCR原严重度：low
- 计划：无
- 原意见：截图路径使用 Windows 反斜杠分隔符（JSON 中解析后为 `.build-cache\M10.5.3-turns-desktop.png`），在 POSIX/CI 环境无法定位文件，导致 sha256 校验与“无凭据泄漏”的复现步骤在非 Windows 平台上直接失败。建议统一使用正斜杠相对路径。
- 判断：本地核对 capturedAt="2026-10-17T05:51:06.825Z"；status="PASS"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.3-turns-results.json:68；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1262 · 不采纳

- 位置：docs/testing/M10.5.3-diagnosis-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：顶层 `status` 是硬编码的聚合结果，而 `checks` 中每条记录只有 `name` + 布尔 `passed`：没有 `skipped`/`errored` 状态区分、没有 expected/actual、断言耗时、断言代码位置，也没有 git 提交号/被测 jar 构建来源（只有 jarSha256）。触发条件：任一 check 因超时、fixture 未启动或被 `try/catch` 吞掉而“未执行”时，产物依旧只能写出 `passed: false` 或整体 PASS，审计方无法区分“通过”与“跳过”，也无法据此复现。建议顶层 status 由 checks 派生，并补充 schemaVersion、sourceRevision、每条 check 的 status(passed/failed/skipped)、expected/actual 与失败诊断字段。
- 判断：本地核对 status="PASS"；checks=数组(13项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.3-diagnosis-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.3-diagnosis-results.json:4

## OCR-1263 · 不采纳

- 位置：docs/testing/M10.5.3-diagnosis-results.json:58；OCR原严重度：high
- 计划：无
- 原意见：该产物名为 M10.5.3，但四条截图证据的文件名全部是 M10.5.1（`M10.5.1-shell.png`、`M10.5.1-guide.png`、`M10.5.1-dark.png`、`M10.5.1-mobile.png`），且每条只有 sha256、没有 capturedAt / taskId / 对应 check 名称。触发条件：M10.5.3 新增的“窄屏暗色不溢出”“验证指南键盘可达”等视觉/可访问性检查若未重新出图（或脚本复用了旧目录），当前 PASS 就会被旧快照背书，截图哈希无法与本次运行绑定。建议按里程碑命名截图并补充 capturedAt 与对应 check 名称，或在脚本中校验截图生成时间晚于 capturedAt 之前的构建时间。
- 判断：本地核对 capturedAt="2026-10-17T05:51:10.332Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.3-diagnosis-results.json:58；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1264 · 不采纳

- 位置：docs/testing/M10.5.3-diagnosis-results.json:60；OCR原严重度：medium
- 计划：无
- 原意见：截图路径使用 Windows 反斜杠（其余三条同样为 `.build-cache\\...png`），在 POSIX 的 CI/评审机上无法直接按该路径定位文件与重算 sha256，也让产物不可跨平台复现。建议统一使用 POSIX 相对路径（`/` 分隔）。
- 判断：本地核对 capturedAt="2026-10-17T05:51:10.332Z"；status="PASS"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.3-diagnosis-results.json:60；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1265 · 不采纳

- 位置：docs/testing/M10.5.3-diagnosis-results.json:79；OCR原严重度：medium
- 计划：无
- 原意见：`modelRequests: 5` 与 `paidRequests: 0` 之间没有任何可核对的证据：没有记录每次模型调用的 endpoint/模型标识/是否命中付费 provider、也没有计费标记或请求级明细，而 checks 里同时断言了“fixture closes with zero paid requests”。触发条件：被测网关把调用路由到需要计费的模型（或本地桩被真实 provider 回退）时，该产物仍会写出 paidRequests=0，使“零付费请求”结论不可信、也无法事后追责。建议补充每次模型请求的 model/provider 与计费属性，并由实际调用记录派生该计数器。
- 判断：本地核对 capturedAt="2026-10-17T05:51:10.332Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.3-diagnosis-results.json:79；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1266 · 不采纳

- 位置：docs/testing/M10.5.3-diagnosis-results.json:82；OCR原严重度：low
- 计划：无
- 原意见：`writeExists` 语义不明确（是“写请求存在”“写文件存在”还是“写能力存在”？），同级的 `opened: 4` 也未与 `screenshots` 的 4 条建立关联；产物缺失 `schemaVersion`，下游解析方无法做兼容性校验。触发条件：后续里程碑调整该字段含义或改名时，读取该产物的审计脚本会静默把旧值当成新语义。建议改用无歧义键名（如 `writeRequested` / `writeFileExists`）并增加 `schemaVersion` 与字段说明。
- 判断：本地核对 runtime.writeExists=false；screenshots=数组(4项)。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.3-diagnosis-results.json:82；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.3-diagnosis-results.json:58

## OCR-1267 · 不采纳

- 位置：docs/testing/M10.5.3-diagnosis-results.json:86；OCR原严重度：medium
- 计划：无
- 原意见：`requests` 只记录 path 与 method，不记录 HTTP 状态码、响应错误体、请求序号与所属 check：因此无法从产物证明整个流程未出现 4xx/5xx、审批被拒后未误判为进入 body、以及刷新时未重放 UNKNOWN 状态（这正是 checks 第 3、9、11 条要断言的）。触发条件：某次运行出现 500 或审批拒绝码为 4xx 时，只要断言缺陷漏判，产物仍呈现“干净”的请求轨迹。建议为每条记录补充 status、失败标志/错误摘要与关联的 check 名称，并保留去重前的序号。
- 判断：本地核对 requests=数组(112项)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.3-diagnosis-results.json:86；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.3-diagnosis-results.json:84

## OCR-1268 · 不采纳

- 位置：docs/testing/M10.5.3-history-results.json:2；OCR原严重度：high
- 计划：无
- 原意见：顶层 `status: "PASS"` 并不是由 `checks` 推导出来的：证据生成器 frontend/tests/history.mjs:122 直接写入字面量 `status: 'PASS'`，而 `check()`（history.mjs:18）在断言不成立时通过 `assert.ok` 抛异常，异常路径只会写 `M10.5.2-history-failure.txt`，根本不会生成本文件。结果是本文件中每个 `passed` 恒为 true（`checks.push({ name, passed: true })` 中 passed 不是变量），PASS 实际只表示“脚本跑到最后没有抛错”。触发条件：任一 check 被删除/改名/被条件分支跳过、或 check 列表被裁剪后仍写出结果，本文件依旧呈现 PASS，且文件内没有 `totalChecks`/期望检查项数/失败项等字段可供外部核对（19 这个期望值只存在于 scripts/collect-m1053-evidence.py 中）。建议：`status` 改为由 `checks.length === EXPECTED && checks.every(c => c.passed)` 推导，并把 expectedChecks、跳过的 check、失败原因一并落盘，避免聚合结论掩盖缺失/未执行的检查项。
- 判断：本地核对 checks=数组(19项)；checks[0].passed=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.3-history-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.3-history-results.json:5；docs/testing/M10.5.3-history-results.json:8

## OCR-1269 · 不采纳

- 位置：docs/testing/M10.5.3-history-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：本文件缺少当前生成器必然写出的 `browserEngine` 与 `transportDiagnostics` 两个 key（对比 frontend/tests/history.mjs:122）。其中 `transportDiagnostics` 是安全断言的关键审计信息：history.mjs:55 会把命中 `stoppedHostDiagnostic` 的 `pageerror` 从 `errors` 中过滤掉并单独收集，只有未被过滤的异常才进入 history.mjs:120 的 `errors.length === 0` 判定。该字段在结果文件里被丢弃后，末尾 “no browser exceptions or credential leaks” 这条 PASS 就无法区分“真的没有异常”和“异常被当作停服噪音丢弃”，也无法审计被抑制的异常类型；同时第 120 行的判定把 `errors`、host 输出含凭据、localStorage/sessionStorage 含凭据、`wire` 是否全部 authenticated 四个条件压缩成一个布尔，出问题时没有可定位的分项证据。建议保留 browserEngine 与 transportDiagnostics，并输出分项布尔（如 pageErrors / hostOutputLeak / storageLeak / unauthenticatedRequests），使该安全结论可复核。
- 判断：本地核对 capturedAt="2026-10-17T05:51:19.264Z"；status="PASS"。将当前源码字段/新功能文件集套用到带日期的旧里程碑快照，不能证明旧记录陈旧造假或漏测。M10.5.9新增跨引擎runtime/transportDiagnostics及M10.5.6～.8新增UI/检索文件，旧阶段不应补填这些后来字段或重标通过。
- 证据：docs/testing/M10.5.3-history-results.json:4；.agent:当前接续状态；docs/testing/M10.5.9-validation.md:26

## OCR-1270 · 不采纳

- 位置：docs/testing/M10.5.3-history-results.json:85；OCR原严重度：medium
- 计划：无
- 原意见：截图证据的路径与本次 M10.5.3 运行无法对齐，且不可复验：(1) 文件名前缀被生成器硬编码为 `M10.5.2-`（frontend/tests/history.mjs:51 `join(out, \`M10.5.2-${name}.png\`)`），所以 M10.5.3 结果里的截图与 M10.5.2 的截图同名但 sha256 不同（本文件 4b5a26… vs M10.5.2-history-results.json 的 805e12…），无法从文件名判断证据属于哪次运行；(2) 截图写在 `.build-cache` 目录，按 docs/testing/M10.5.2-validation.md 的约定“运行缓存不提交”，且每次运行都会覆盖同名文件，因此这里的 sha256 在仓库内没有任何可重算的对应物，无法被 CI 或其他复核者校验；(3) 路径使用 Windows 反斜杠（由 history.mjs:51 的 `path.replace(root + '\\', '')` 产生原生分隔符），在 POSIX 环境下无法直接解析。建议把证据写入受版本控制的目录并使用含里程碑/运行标识的文件名与 POSIX 相对路径。
- 判断：本地核对 capturedAt="2026-10-17T05:51:19.264Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.3-history-results.json:85；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1271 · 不采纳

- 位置：docs/testing/M10.5.3-history-results.json:101；OCR原严重度：low
- 计划：无
- 原意见：本文件缺少本次运行的唯一标识（history.mjs:10 生成的 `run = M10.5.2-history-<timestamp>` 没有写入结果），只有 `capturedAt`/`jarSha256`。而 `completedCycles`、`modelRequests` 在 history.mjs:122 中分别取 `ids.length`（fixture 创建的任务数）和 `wire.length`（拦截到的上游请求数），属确定性 fixture 产物，因此 M10.5.2/3/4/5 的 history 结果里这两项恒为 65/138，无法凭本文件区分“本次真实采集”与“复用同一 fixture 的另一轮采集”，也无法把 65 这个数字与 check 名中的 “65 completed production CLI tasks” 做交叉核对。建议补充 runId、fixture/payload 版本或其它可交叉验证的标识。
- 判断：本地核对 capturedAt="2026-10-17T05:51:19.264Z"；jarSha256="32a89a47b5f71160e9f361808bd9d078ef39da5835732f707cab9e8202065bf2"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.3-history-results.json:101；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.3-history-results.json:3；docs/testing/M10.5.3-history-results.json:105

## OCR-1272 · 不采纳

- 位置：docs/testing/M10.5.3-settings-results.json:125；OCR原严重度：high
- 计划：无
- 原意见：`paidRequests` 并非实测值：生成该文件的 frontend/tests/settings.mjs 中直接写死 `paidRequests: 0`（只有 `modelRequests` 来自 `wire.length` 实测）。因此本记录里“零付费请求”不能被当作证据，即便真实发生了付费调用，这个字段仍然是 0。建议由请求拦截/统计层真正累计付费端点命中次数（并按端点白名单判定），或至少在记录中附上上游请求日志的哈希以供复核。
- 判断：本地核对 paidRequests=0；modelRequests=4。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.3-settings-results.json:125；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.3-settings-results.json:126

## OCR-1273 · 不采纳

- 位置：docs/testing/M10.5.3-settings-results.json:128；OCR原严重度：high
- 计划：无
- 原意见：`fixtureCredentialsCleaned` 是写文件时无条件附加的常量：frontend/tests/settings.mjs 结尾 `JSON.stringify({ ...result, fixtureCredentialsCleaned: true })`，即使清理进程异常退出、系统凭据残留，本字段依旧为 true。这里的清理断言只覆盖了 `WorkbenchCredentialCleanup` 退出码，并未校验凭据库/环境变量/工作区中确实不再存在密钥，属于安全类假阳性。建议把清理结果（退出码、清理前后凭据目标列表、清理日志哈希）写入该字段的派生值，并让采集脚本校验这些证据。
- 判断：本地核对 fixtureCredentialsCleaned=true。fixtureCredentialsCleaned并非未验残留就常量通过：settings finally中启动Cleanup并assert退出0后才写报告，Cleanup删除配置引用targets后逐项vault.read为空，否则抛异常。清理失败不会落该true；原意见忽略实际回读。
- 证据：docs/testing/M10.5.3-settings-results.json:128；frontend/tests/settings.mjs:155；harness-cli/src/test/java/io/jharness/cli/WorkbenchCredentialCleanup.java:28

## OCR-1274 · 不采纳

- 位置：docs/testing/M10.5.3-settings-results.json:121；OCR原严重度：medium
- 计划：无
- 原意见：截图证据不可复核：这些路径使用 Windows 反斜杠且指向 `.build-cache/`，而 `.gitignore:8` 已忽略该目录，归档到 `docs/testing` 的 JSON 中只剩哈希、没有可获取的字节，任何评审者/其他平台都无法重算 sha256；`collect-m1053-evidence.py:64` 还必须靠 `path.replace('\\','/')` 兼容。另外该 mobile 截图的 sha256 与 M10.5.1/5.2/5.4~5.9 记录完全一致，说明多轮里程碑复用了同一张图，无法证明 5.3 当轮真的重新截图。建议使用仓库内相对 POSIX 路径并提交（或提供可获取的归档位置），同时每次运行重新生成截图。
- 判断：本地核对 "path": ".build-cache\\M10-settings-mobile.png",       "sha256": "6df524f9f60edbeff80e6cd55d6fb10df01d11c97c12f44ec7b84cd18e3。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.3-settings-results.json:121；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1275 · 不采纳

- 位置：docs/testing/M10.5.3-settings-results.json:7；OCR原严重度：medium
- 计划：无
- 原意见：所有 24 项检查都被压缩成 `name` + `passed` 布尔值，没有任何断言细节、请求/响应摘要或运行日志哈希与本文件绑定（同仓库后续的 M10.5.9 记录已有 `logSha256`/`fixtureSourceSha256`），因此本文件既无法区分“断言真的执行通过”和“写死为 true”，也无法在失败时定位到哪条协议/脱敏断言失效（如“event output excludes raw API key”）。建议为每项检查记录其断言标识与证据摘要，并在顶层加入执行日志哈希。
- 判断：本地核对 checks[0].name="settings rejects unauthenticated operator"；checks[0].passed=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.3-settings-results.json:7；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.3-settings-results.json:8

## OCR-1276 · 不采纳

- 位置：docs/testing/M10.5.3-settings-results.json:3；OCR原严重度：low
- 计划：无
- 原意见：环境信息不足以复现：`platform` 仅是 `process.platform` 的 `win32`，没有 Windows 版本/补丁位、CPU 架构，也没有浏览器引擎名（Chromium/Chrome/Edge）；而本文件的通过项恰恰依赖 Windows 凭据管理器持久化与宿主重启恢复。换到其他 Windows 版本或 Edge/WebView 宿主时，“restart restores connection and system credential”等结论无法判定是否仍成立。建议补充 OS 版本与架构、浏览器引擎与 userAgent、以及宿主 JAR 路径（现仅有 `jarSha256`，无路径/源码版本字段）。
- 判断：本地核对 platform="win32"；jarSha256="32a89a47b5f71160e9f361808bd9d078ef39da5835732f707cab9e8202065bf2"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.3-settings-results.json:3；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.3-settings-results.json:127

## OCR-1277 · 不采纳

- 位置：docs/testing/M10.5.3-workbench-results.json:2；OCR原严重度：high
- 计划：无
- 原意见：第 2 行的 `status: PASS` 仅由 28 个布尔值支撑（第 8-119 行），checks 条目没有 state(skipped/failed)、断言摘要、耗时字段，产物里也没有“期望检查数=28”的显式断言。触发条件：任一条检查因前置条件不满足被跳过（浏览器未就绪、选择器未命中导致断言根本没执行）或测试用例被删除/改名时，产物依旧写出 PASS，与真实通过无法区分，也无从复现“哪条断言通过”。建议每条检查记录 state+断言摘要，并在生成产物时校验实际执行条数等于期望条数，否则整体判 FAIL。
- 判断：本地核对 capturedAt="2026-10-17T05:51:19.249Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.3-workbench-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1278 · 不采纳

- 位置：docs/testing/M10.5.3-workbench-results.json:190；OCR原严重度：high
- 计划：无
- 原意见：第 190 行 M10-connection.png 与第 194 行 M10-welcome.png 的 sha256 完全相同（8a0c3e…9080），但两者应是不同 UI 状态。触发条件：两次截图写入同一路径、第二次截图前未导航、或文件被覆盖，都会得到同一哈希，说明其中一张是陈旧/复用的捕获，欢迎页的视觉证据实际不存在。建议不同场景使用不同文件名并在截图前断言目标元素可见，同时在产物生成阶段校验截图哈希互不相同，出现重复即判失败。
- 判断：本地核对 capturedAt="2026-10-17T05:51:19.249Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.3-workbench-results.json:190；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1279 · 不采纳

- 位置：docs/testing/M10.5.3-workbench-results.json:198；OCR原严重度：medium
- 计划：无
- 原意见：截图 path 使用 Windows 反斜杠（如第 189/193/197 行的 `.build-cache\\M10-*.png`），而同一文件的 sourceSha256 全部使用 `/`。触发条件：在 Linux/macOS 或 CI 上按该路径重算哈希/校验证据时路径无法解析，且 JSON 反斜杠需双重转义，容易与真实文件名不一致，证据不可复现。另外截图放在 .build-cache（通常被 gitignore），只提交本 JSON 时第三方无法重算哈希。建议统一为仓库相对 POSIX 路径，并把截图落到受版本控制的目录或一并归档。
- 判断：本地核对 capturedAt="2026-10-17T05:51:19.249Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.3-workbench-results.json:198；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1280 · 不采纳

- 位置：docs/testing/M10.5.3-workbench-results.json:122；OCR原严重度：medium
- 计划：无
- 原意见：运行时计数无法互相校验：modelRequests=14（第 122 行）、opened=9（第 124 行）与 requests.taskSubmissions=9、requests.total=183（第 184-185 行）之间没有任何按端点/按任务的分项。触发条件：某个任务被重复 POST（应为 9 次实际 10 次）或存在漏计/多计请求时，聚合数字仍可自洽，重复提交这类缺陷会被掩盖。同时 paidRequests=0（第 125 行）缺少请求目标（provider/base URL）依据，无法证明 14 次 modelRequests 确实未打到付费端点。建议记录按 method+path 的请求直方图、每次提交的幂等键及 model 请求的目标地址。
- 判断：本地核对 capturedAt="2026-10-17T05:51:19.249Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.3-workbench-results.json:122；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1281 · 不采纳

- 位置：docs/testing/M10.5.3-workbench-results.json:127；OCR原严重度：medium
- 计划：无
- 原意见：jarSha256（第 222 行）与 sourceSha256 清单（第 128-181 行）不构成可验证的溯源闭包：清单只覆盖 frontend、harness-testkit、harness-web 的 54 个文件，未包含根/父 pom、被打进 jar 的其它模块（如 harness-core）、JDK 版本与 Maven 构建参数。触发条件：其它模块或构建配置变更使 jar 内容变化时，清单哈希不变而 jarSha256 变化（或反之），无法证明被测 jar 由这份源码构建。建议补充 git commit、构建命令/JDK 版本，以及 jar 内全部 class 的来源模块清单。
- 判断：本地核对 capturedAt="2026-10-17T05:51:19.249Z"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.3-workbench-results.json:127；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1282 · 不采纳

- 位置：docs/testing/M10.5.3-launcher-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：`status: PASS` 是对下面 8 项检查的聚合结论，但 `checks`（第 4-13 行）只是字符串数组，没有逐项的 status/exitCode/耗时/错误信息。触发条件：任一检查被跳过、或同一检查字符串被重复追加时，下游仅凭 `len(checks)==8` 和“元素是字符串”判为通过，本文件仍显示 PASS，从而掩盖未执行/重复执行的检查。建议把 checks 改成结构化对象数组，每项至少包含 name、status，并在聚合 PASS 前校验全部 8 项 status 均为 PASS。
- 判断：本地核对 checks=数组(8项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.3-launcher-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.3-launcher-results.json:4

## OCR-1283 · 不采纳

- 位置：docs/testing/M10.5.3-launcher-results.json:3；OCR原严重度：medium
- 计划：无
- 原意见：本文件只有时间戳、3 个脚本哈希和 jar 哈希，缺少运行环境元数据（OS/版本、PowerShell 版本、Java 版本、实际执行的测试命令/参数、端口与临时工作区信息）。触发条件：在另一台机器或升级工具链后重跑时，无法判断启动器行为差异来自源码、PowerShell 版本还是 Windows/Java 环境；同批次的 M10.5.3-results.json 记录了 platform，而本文件缺失，跨环境比对会漏判。建议补充 platform、powershellVersion、javaVersion、command 等字段。
- 判断：本地核对 capturedAt="2026-10-17T05:51:07.983695+00:00"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.3-launcher-results.json:3；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1284 · 部分采纳

- 位置：docs/testing/M10.5.3-launcher-results.json:14；OCR原严重度：medium
- 计划：R6-03 / P2
- 原意见：该值声称生产环境会直接打开浏览器且不复制凭据，但本次 8 项检查在自动化中明确禁用浏览器与剪贴板，没有任何检查项对应生产默认路径。触发条件：生产路径（默认打开浏览器、以及“不复制凭据”的剪贴板隔离）发生回归时，本文件仍为 PASS，声明与证据不匹配，容易被下游当作已验证的安全行为。建议新增对应检查项，或把该值改为显式的 `notExercisedInAutomation` 结构并说明覆盖缺口。
- 判断：本地核对 capturedAt="2026-10-17T05:51:07.983695+00:00"；status="PASS"。自动化明确传-NoBrowser/-NoClipboard，生产默认打开浏览器的分支不属于这8项实测。采纳把静态生产说明与自动化结果分开，增加可控浏览器调用替身验证；不要求每次验收真实改用户剪贴板。
- 证据：docs/testing/M10.5.3-launcher-results.json:14；scripts/test-open-workbench.py:82；scripts/test-open-workbench.py:137；scripts/open-workbench.ps1

## OCR-1285 · 不采纳

- 位置：docs/testing/M10.5.3-launcher-results.json:6；OCR原严重度：medium
- 计划：无
- 原意见：该项只以字符串声明“不暴露 token”，与第 11 行“token 不能覆盖受保护环境变量”一样，缺少对日志/错误消息/配置文件/进程命令行等持久化位置的脱敏验证证据。触发条件：重复启动失败或退出分支若把 token 写入启动器日志、UI 日志或配置文件，现有记录无法发现，PASS 会被误判。建议增加对启动器输出、日志文件与工作区配置文件扫描 token 的独立检查项并记录结果。
- 判断：本地核对 capturedAt="2026-10-17T05:51:07.983695+00:00"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.3-launcher-results.json:6；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1286 · 不采纳

- 位置：docs/testing/M10.5.2-workbench-results.json:2；OCR原严重度：high
- 计划：无
- 原意见：第 2 行 `"status": "PASS"` 与第 7-120 行的 `checks` 数组之间存在聚合缺口：`checks` 只有 28 条 `passed: true` 的条目，既没有 `expectedChecks`/检查项清单，也没有 `failed`/`skipped` 计数。触发条件：任一检查在注册前抛异常、按浏览器/环境条件被跳过，或测试进程提前退出时，`checks` 只会包含已经通过的项，顶层 `status` 依然是 `PASS`（配合 `frontend/tests/browser.mjs` 中无条件写 `status: 'PASS'` 的写法）。影响：不完整运行会被当作全量通过，回归被静默掩盖，且无法事后判定是“全部跑过”还是“部分没跑”。建议补 `expectedChecks`、`failed: []`、`skipped: []`，并让 `status` 由“实际检查集合 == 期望集合且无失败”推导得出。
- 判断：本地核对 checks=数组(28项)；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.2-workbench-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.2-workbench-results.json:7

## OCR-1287 · 不采纳

- 位置：docs/testing/M10.5.2-workbench-results.json:124；OCR原严重度：high
- 计划：无
- 原意见：第 25 行的检查声称 “duplicate click creates exactly one actual task and approval writes real file”，但第 124 行只记录布尔量 `writeExists: true`（对应 `M10BrowserHost` 里的存在性标记），无法区分“恰好写 1 个文件”和“重复点击写了 2 个文件/2 个任务”。同时第 181 行 `taskSubmissions: 9` 与第 123 行 `opened: 9` 相等，说明每个场景只提交一次，去重路径并未在计数上留下可核对痕迹。影响：该 PASS 只能证明“文件存在”，不能证明“恰好一次写入/一个任务”，而这正是该检查要防的缺陷。建议记录 `writes: 1`、`distinctTaskIds: 1`、`approvalFiles: [<相对路径，脱敏>]` 等可计数证据，并断言其等于期望值。
- 判断：本地核对 capturedAt="2026-10-17T05:16:14.140Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.2-workbench-results.json:124；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1288 · 不采纳

- 位置：docs/testing/M10.5.2-workbench-results.json:122；OCR原严重度：medium
- 计划：无
- 原意见：第 122 行 `paidRequests: 0` 与第 125 行 `modelRequests: 14` 并置，说明无密钥运行期间确实发生了 14 次模型侧请求；但本文件没有记录该计数的采集点（拦截的端点/主机、探针响应），因此“0”既可能是“测得为 0”，也可能是“计数器未接入/被短路”。影响：第 117 行的 “keyless runtime reports zero paid requests” 检查无法自证，也无法发现计费流量走了未被拦截的域名。建议在 `runtime` 中记录计数器采集方式与所有出网 host 列表（脱敏），并注明期望值为 0 的断言依据。
- 判断：本地核对 runtime=对象(4键)。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.2-workbench-results.json:122；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.2-workbench-results.json:121

## OCR-1289 · 不采纳

- 位置：docs/testing/M10.5.2-workbench-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：第 4 行 `testedOrigin` 记录的是每次运行随机分配的本地端口，整个文件也没有 git commit SHA、harness 版本或 JDK/Node 版本；而文件名声明 10.5.2，`sourceSha256` 与截图却仍是 `M10*` 命名。影响：这份 PASS 无法绑定到某个可复现的代码版本，也无法判断它对应的是 10.5.2 还是更早的 M10 用例集，出现回归时不能用来二分定位。建议增加 `gitCommit`、`harnessVersion`、`jdkVersion`/`nodeVersion`，并让文件名版本与用例/截图命名保持一致。
- 判断：本地核对 testedOrigin="http://127.0.0.1:56071"；sourceSha256=对象(51键)。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.2-workbench-results.json:4；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29；docs/testing/M10.5.2-workbench-results.json:127

## OCR-1290 · 不采纳

- 位置：docs/testing/M10.5.2-workbench-results.json:178；OCR原严重度：medium
- 计划：无
- 原意见：`sourceSha256` 清单只覆盖 `frontend/` 与 `harness-web/`（外加 testkit），但第 21、49、61、69、73 行的检查声称走了 “actual Java API / actual tool pipeline / actual Java runtime / offline recovery”。这些行为依赖的工具执行、插件、运行时模块源码均未纳入清单。影响：这些模块被修改后，复用本文件仍会让人误以为 PASS 对应当前代码，溯源不成立。建议把每个检查实际依赖的模块源文件（或至少这些模块产物的 jar 哈希）纳入清单，缺失时让结果生成失败而不是静默省略。
- 判断：本地核对 sourceSha256=对象(51键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.2-workbench-results.json:178；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.2-workbench-results.json:127

## OCR-1291 · 不采纳

- 位置：docs/testing/M10.5.2-workbench-results.json:187；OCR原严重度：medium
- 计划：无
- 原意见：截图路径使用 Windows 反斜杠（`\\`）并指向 `.build-cache/`，而 `.gitignore` 第 8 行已将 `.build-cache/` 整个目录忽略。触发条件：在 Linux/CI 上复核，或在清理构建缓存后重跑比对。影响：路径在不同平台上不可解析，缓存清理后这些 sha256 无法再复核，视觉证据实际是不可持久验证的。建议改用仓库相对的 POSIX 路径（如 `docs/testing/screenshots/M10.5.2/…`）并把截图纳入版本控制。
- 判断：本地核对 capturedAt="2026-10-17T05:16:14.140Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.2-workbench-results.json:187；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1292 · 不采纳

- 位置：docs/testing/M10.5.2-workbench-results.json:191；OCR原严重度：high
- 计划：无
- 原意见：第 187 行 `M10-connection.png` 与第 191-192 行 `M10-welcome.png` 的 sha256 完全相同（`8a0c3eae…9080`），说明两个不同 UI 状态实际写入了同一张图片（后一次截图覆盖了前一次，或截图目标元素选错）。影响：这两个状态的视觉证据至少有一个不成立，若连接态/欢迎态渲染错乱本文件仍会显示 PASS。建议分别断言两张截图的像素哈希互不相同，并在文件被覆盖或内容重复时让用例直接失败，而不是静默通过。
- 判断：本地核对 capturedAt="2026-10-17T05:16:14.140Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.2-workbench-results.json:191；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1293 · 不采纳

- 位置：docs/testing/M10.5.4-diagnosis-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：门禁证据不可自证：checks 元素只有 name/passed 两个字段，缺少 total/expectedCount 以及 skipped/error 状态。触发条件：某个检查因异常或超时未被写入 checks 数组，或被执行器跳过；此时 status 仍为 PASS，消费方无法从产物判断 PASS 是否为全部检查的严格合取。建议在产物中增加 expectedChecks 总数与每个 check 的 status 字段（pass/fail/skip/error），生成时断言 checks.length === expectedChecks 且无 skip/error 才允许写 PASS。
- 判断：本地核对 capturedAt="2026-10-17T12:04:41.724Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.4-diagnosis-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1294 · 不采纳

- 位置：docs/testing/M10.5.4-diagnosis-results.json:60；OCR原严重度：medium
- 计划：无
- 原意见：截图证据路径使用 Windows 反斜杠（第 60/64/68/72 行），在 Linux/macOS 的 CI 或评审环境上无法按路径定位到文件，导致 sha256 无法复算，PASS 依赖于不可验证的产物。建议统一输出仓库相对 POSIX 路径，例如 ".build-cache/M10.5.1-shell.png"（生成时对 path.join 结果做分隔符归一化）。
- 判断：本地核对 capturedAt="2026-10-17T12:04:41.724Z"；status="PASS"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.4-diagnosis-results.json:60；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1295 · 不采纳

- 位置：docs/testing/M10.5.4-diagnosis-results.json:80；OCR原严重度：low
- 计划：无
- 原意见：计数器与请求轨迹不自洽：runtime.opened 为 4，但 requests 中顶层文档 GET "/" 只出现 2 次、POST /v1/browser-session 出现 2 次（第 84 行起第一次加载、第 372 行起第二次加载）。触发条件：以 opened 表示打开的页面/会话数则与轨迹冲突；若表示其他语义则产物中无字段说明，审计方无法核对计数是否漏记或被重复统计。建议明确定义 opened 并在生成时由轨迹重算校验。
- 判断：本地核对 capturedAt="2026-10-17T12:04:41.724Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.4-diagnosis-results.json:80；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1296 · 不采纳

- 位置：docs/testing/M10.5.4-diagnosis-results.json:84；OCR原严重度：medium
- 计划：无
- 原意见：轨迹只记录 path 与 method，缺少响应状态码、失败标记与计费/模型调用标记，导致本文件中 "no browser runtime errors"（第 50 行）和 "fixture closes with zero paid requests"（第 54 行）两项检查无法从产物本身复核：一次返回 5xx 的 POST /v1/tasks 与成功请求在该结构中完全一致，runtime.paidRequests=0 也无从重算。触发条件：被测服务返回错误但 UI 未抛异常时，检查仍会通过。建议每条请求补充 status、durationMs 以及 paid/model 标记，使 paidRequests/modelRequests 计数可由轨迹独立重算。
- 判断：本地核对 capturedAt="2026-10-17T12:04:41.724Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.4-diagnosis-results.json:84；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1297 · 不采纳

- 位置：docs/testing/M10.5.4-diagnosis-results.json:170；OCR原严重度：low
- 计划：无
- 原意见：敏感标识未脱敏：产物永久提交了完整 task id 与 approval UUID，并完整记录了审批决策端点（POST /v1/tasks/{taskId}/approvals/{approvalId}）。触发条件：若该端点没有独立于 /v1/browser-session 的鉴权校验，任何人都可凭本文件中的 taskId + approvalId 重放审批请求；同时这些 ID 使证据可关联到真实运行环境。建议对 task/approval 标识做脱敏（保留前缀 + 短哈希），或将完整轨迹保留在 .build-cache 等不入库位置，仓库内仅保留摘要。
- 判断：本地核对 capturedAt="2026-10-17T12:04:41.724Z"；status="PASS"。记录是本地夹具生成的task/approval标识，不是Bearer凭据；业务审批端点仍独立鉴权。原意见使用若未鉴权的假设，没有证明鉴权缺失，公开这些测试ID不产生所述重放授权。
- 证据：docs/testing/M10.5.4-diagnosis-results.json:170；harness-web/src/main/java/io/jharness/web/HarnessWebServer.java

## OCR-1298 · 不采纳

- 位置：docs/testing/M10.5.3-results.json:1236；OCR原严重度：medium
- 计划：无
- 原意见：证据链不完整：`frontendInputsSha256` 只覆盖了 `frontend/src/**`、`package.json`、`package-lock.json`、`frontend/tests/**`、`frontend/scripts/package-ui.mjs`，但**遗漏了直接决定产物内容的构建输入** `frontend/vite.config.ts`、`frontend/tsconfig.json`、`frontend/index.html`。触发条件：任何人尝试用本回执复现 `uiFilesSha256`/`jarSha256` 时，只要改动 vite/tsconfig（例如 target、minify、chunk 命名、define 常量）或修改 `index.html` 的脚本入口，产物哈希就会改变，而本回执记录的前端输入哈希完全不变，导致无法判定“产物是否由记录输入生成”。建议把上述三个文件（以及构建时实际使用的工具版本）纳入 `frontendInputsSha256`，或显式记录产物不可逐字节复现的声明。
- 判断：本地核对 frontendInputsSha256=对象(38键)；uiFilesSha256=对象(4键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.3-results.json:1236；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.3-results.json:1292

## OCR-1299 · 不采纳

- 位置：docs/testing/M10.5.3-results.json:15；OCR原严重度：medium
- 计划：无
- 原意见：每个 suite 的 `sha256` 记录的是 `target/surefire-reports/*.xml` 瞬时报告的内容哈希（由 `scripts/collect-m1053-evidence.py` 采集），而该目录既被 `mvn clean` 删除、也不纳入版本控制。触发条件：后续读者（或 CI 复核）想在数周后校验某条用例确实执行过、聚合数字 337 是否来自这 55 个 suite 时，无法重新计算这些 sha256，`totals` 与 `cases` 的对应关系只剩“自证”性质。建议把 Surefire XML（或精简后的用例结果清单）一并归档并对其做哈希，或改为对已提交的测试源码做哈希并在回执中说明哈希对象的类型（如增加 `hashTarget` 字段）。
- 判断：本地核对 suites[0].sha256="04389d667407c8a6bfa26a5813cd36c2db5ea7d7a91d08344e243703bfbde623"；totals=对象(4键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.3-results.json:15；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.3-results.json:16；docs/testing/M10.5.3-results.json:6

## OCR-1300 · 不采纳

- 位置：docs/testing/M10.5.3-results.json:1300；OCR原严重度：low
- 计划：无
- 原意见：`logSha256` 中 18 条日志以及 `verificationLogSha256` 指向的构建缓存日志（`.build-cache/M10.5.3-*.log`）被 `.gitignore` 排除，仓库内不存在同名文件，因此这些哈希无法被任何人独立校验（`verificationLogSha256` 与 `logSha256["M10.5.3-verify.log"]` 内部一致，`clientTests.logSha256` 与 client 日志一致，但只是自洽，不构成外部可验证性）。触发条件：审计 / 回归时想确认 PASS 来自哪次运行、失败的首次 verify 是否被正确排除，均无原始记录可查。建议至少归档被哈希的关键日志（或把其中的结果摘要内联进回执），并在回执中标注日志是否随仓库发布。
- 判断：本地核对 logSha256=对象(18键)；verificationLogSha256="4457e9137746b1d55c006b658c813831eda32d35f2babea12423302b740482ef"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.3-results.json:1300；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M10.5.3-results.json:1281；docs/testing/M10.5.3-results.json:1276

## OCR-1301 · 不采纳

- 位置：docs/testing/M10.5.3-results.json:5；OCR原严重度：low
- 计划：无
- 原意见：`platform` 仅记录 `Windows-11-10.0.26200-SP0`，未记录 JDK / Maven 版本与关键运行参数（如 `-Xlint`、并行度）。触发条件：该项目多处行为与 Java 版本相关（terminate/进程树、文件锁、字符编码），在不同 JDK 上重跑时“337 全绿”的结论不可比；出现与 JDK 相关的权限/文件锁类用例（RunStatePermissionsTest、JsonlSessionRepositoryTest 等）失败时也无法判断是否环境差异。建议补充 `toolchain` 字段（java.version、maven.version、surefire 配置摘要）。
- 判断：本地核对 platform="Windows-11-10.0.26200-SP0"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.3-results.json:5；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1302 · 不采纳

- 位置：docs/testing/M10.5.4-launcher-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：第 2 行缺少溯源字段：docs/testing 目录下其余证据文件（M0-results.json、M10.5.3-results.json、M10.5.4-results.json 等）都以 `"milestone"` 作为第一个键，本文件没有 `milestone`，也没有 `platform`/`runnerOs`/`javaVersion`。触发条件：本文件的 8 项检查全部依赖 Windows 专属入口（`web-ui.cmd`、`-Workspace`、`-NoBrowser`/`-NoClipboard`）与 Windows PowerShell 环境，一旦旧批次报告按同名覆盖、或从 `.build-cache/M10-launcher-results.json` 复制到错误里程碑目录，下游采集脚本（scripts/collect-m1059-evidence.py 仅校验 `status=='PASS'`、`jarSha256` 与 `len(checks)==8`）仍会把它当作 M10.5.4 的有效 PASS 接受。建议补充 `milestone`、`platform`、`javaVersion` 等字段，使 PASS 的作用域可自证。
- 判断：本地核对 jarSha256="22876d60e54820dc19e74c5936522f3ba72bffef7fd773c13fe387dfa8b64565"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.4-launcher-results.json:2；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29；docs/testing/M10.5.4-launcher-results.json:22

## OCR-1303 · 不采纳

- 位置：docs/testing/M10.5.4-launcher-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：第 4-13 行的 `checks` 是纯自由文本数组，没有检查 id、单项 status/exitCode、耗时或日志引用，第 2 行的全局 `"status": "PASS"` 无法与这 8 条一一对应。触发条件：只要生成逻辑发生变化（例如按平台条件跳过某条检查）或有人手工编辑字符串，报告仍会呈现“8 条检查 + PASS”的外观，审计方无法区分“实际执行通过”“被跳过”和“未执行”。建议改为对象数组（如 `{"id": "duplicate-launch", "status": "PASS", "exitCode": 1}`），以便逐项复核并与汇总状态交叉校验。
- 判断：本地核对 checks=数组(8项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.4-launcher-results.json:4；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1304 · 部分采纳

- 位置：docs/testing/M10.5.4-launcher-results.json:14；OCR原严重度：medium
- 计划：R6-03 / P2
- 原意见：第 14 行把安全相关断言（生产模式直接打开浏览器、永不复制凭据）写在自由文本字段里，但第 4-13 行的 8 项检查没有任何一项验证该行为——“browser opening disabled for automation”说明自动化路径根本没有执行打开浏览器/复制凭据的分支。触发条件：审计或合规复核若依赖此字段，会把一条未被任何检查覆盖的断言当作已验证结论。建议把该断言改为明确标注为未覆盖/人工确认的字段（如 `"productionBrowserAndClipboard": "UNVERIFIED"`），或补充对应的检查项后再声明。
- 判断：本地核对 capturedAt="2026-10-18T01:10:20.550960+00:00"；status="PASS"。自动化明确传-NoBrowser/-NoClipboard，生产默认打开浏览器的分支不属于这8项实测。采纳把静态生产说明与自动化结果分开，增加可控浏览器调用替身验证；不要求每次验收真实改用户剪贴板。
- 证据：docs/testing/M10.5.4-launcher-results.json:14；scripts/test-open-workbench.py:82；scripts/test-open-workbench.py:137；scripts/open-workbench.ps1

## OCR-1305 · 不采纳

- 位置：docs/testing/M10.5.4-launcher-results.json:15；OCR原严重度：low
- 计划：无
- 原意见：`modelEndpoint` 的值把机器字段和散文断言混在一起（`"unreachable loopback :9; no task submissions"`），既不能被程序解析，也无法独立支撑第 15 行的 `paidRequests: 0`：零付费请求依赖运行期 `:9` 恰好不可达，而不是被强制阻断或计数验证。触发条件：若执行环境把模型端点改到可达地址（或被别的进程占用 `:9`），本文件仍会原样记录 `paidRequests: 0`。建议拆分为结构化字段（如 `modelEndpoint`、`endpointReachable: false`、`requestCount: 0`），并记录阻断方式。
- 判断：本地核对 modelEndpoint="unreachable loopback :9; no task submissions"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.4-launcher-results.json:15；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.4-launcher-results.json:16

## OCR-1306 · 不采纳

- 位置：docs/testing/M10.5.4-browser-results.json:41；OCR原严重度：medium
- 计划：无
- 原意见：这一项 PASS 没有可核验的断言支撑：对应 frontend/tests/browser.mjs 第108行的调用是 `check('full sanitized session query reads real events')`，没有传入条件，而 check 的 `detail` 默认值为 `true`（第21行），因此即使全量查询没有读到任何真实事件、或返回内容未脱敏，也会被记成 `"passed": true`。触发条件：任何一次运行该脚本都会得到此结果。建议为该检查补上可失败的条件（例如断言查询结果事件数 > 0 且响应文本不含敏感值），再重新生成该产物。
- 判断：本地核对 capturedAt="2026-10-17T12:05:06.371Z"；status="PASS"。check默认true之前存在真实Playwright locator/expect断言，成功后check只注册名称。不能仅见check没有布尔参数判定无断言；需要检查前置步骤。
- 证据：docs/testing/M10.5.4-browser-results.json:41；frontend/tests/browser.mjs:100；frontend/tests/browser.mjs:108

## OCR-1307 · 不采纳

- 位置：docs/testing/M10.5.4-browser-results.json:196；OCR原严重度：medium
- 计划：无
- 原意见：该截图与第191-194行 M10-connection.png 的 sha256 完全相同（d2986ad2…），说明“连接前（connection）”与 connect() 之后“已连接（welcome）”两个不同状态截出的字节一致。触发条件：页面在连接状态文本渲染完成前截图，或产物引用了同一次/过期截图。结果是“浏览器自动进入并完成本机连接”缺少可视证据，且 PASS 结论无法由截图复核。建议在 connect() 之后显式等待连接状态节点渲染完成再截图，并确认两张截图哈希不同后再声明 PASS。
- 判断：本地核对 capturedAt="2026-10-17T12:05:06.371Z"；status="PASS"。相同内容会有相同摘要，不能反推未执行或复制。浏览器自动同源进入使connection/welcome可已是同一状态；夹具日志仅打印确定性的PASS名称，不同引擎/复跑可逐字相同。应核对执行入口和制品来源，不能要求截图/日志哈希强制互异。
- 证据：docs/testing/M10.5.4-browser-results.json:196；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1308 · 不采纳

- 位置：docs/testing/M10.5.4-browser-results.json:164；OCR原严重度：medium
- 计划：无
- 原意见：sourceSha256 清单与生成逻辑不一致，复现链不完整：browser.mjs 第225-226行用 `git ls-files` 枚举 frontend 下全部受版本控制文件来计算哈希，但这里缺少 frontend/tests/runtime.mjs，而第1行 `import { browserType as chromium } from './runtime.mjs'` 正是决定启动哪个浏览器引擎的入口；缺少其哈希时，无法用该清单还原本次运行实际使用的浏览器启动模块。另外当前 browser.mjs 第227行会写出 `browserEngine` 字段，本产物却没有该键，说明产物与清单可能并非同一版本生成或清单被裁剪。建议补齐所有被测试入口直接 import 的模块，并确认产物由清单中哈希对应的脚本生成。
- 判断：本地核对 capturedAt="2026-10-17T12:05:06.371Z"；status="PASS"。将当前源码字段/新功能文件集套用到带日期的旧里程碑快照，不能证明旧记录陈旧造假或漏测。M10.5.9新增跨引擎runtime/transportDiagnostics及M10.5.6～.8新增UI/检索文件，旧阶段不应补填这些后来字段或重标通过。
- 证据：docs/testing/M10.5.4-browser-results.json:164；.agent:当前接续状态；docs/testing/M10.5.9-validation.md:26

## OCR-1309 · 不采纳

- 位置：docs/testing/M10.5.4-browser-results.json:192；OCR原严重度：medium
- 计划：无
- 原意见：截图路径指向 .build-cache/，该目录已被 .gitignore 第8行忽略，且路径硬编码 Windows 反斜杠分隔符。触发条件：在 Linux/macOS 上按该相对路径查找会失败；仓库内也不存在这些文件，无法按 sha256 复核任何一张截图。建议把证据截图随产物提交（例如放在 docs/testing/screenshots/ 下）并使用 POSIX 相对路径记录。
- 判断：本地核对 capturedAt="2026-10-17T12:05:06.371Z"；status="PASS"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.4-browser-results.json:192；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1310 · 不采纳

- 位置：docs/testing/M10.5.4-history-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：顶层 PASS 无法自证：文件里只有 19 个恒为 true 的 passed 标记，没有任何总数/失败/跳过计数或失败明细，且生成脚本 frontend/tests/history.mjs 直接写入字面量 status:'PASS' 而不根据 checks 汇总。触发条件：任一 check 抛出后仍写出 checks 子集或 passed:false，顶层仍为 PASS，下游（如 scripts/collect-m1054-evidence.py 仅校验 status=='PASS'）会把不完整运行当作通过。建议补充 checksTotal/checksFailed/checksSkipped 并由 checks 推导 status，或直接写入失败项名称，使 PASS 可复核。
- 判断：本地核对 capturedAt="2026-10-17T12:04:23.417Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.4-history-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1311 · 不采纳

- 位置：docs/testing/M10.5.4-history-results.json:85；OCR原严重度：medium
- 计划：无
- 原意见：证据可追溯性缺失：本文件用固定文件名 .build-cache\M10.5.2-*.png 记录截图，路径位于会被每一轮测试覆盖的可变缓存目录，而同目录下 M10.5.2/M10.5.3 历史 JSON 记录的是同一路径的不同 sha256。触发条件：后续任意一轮（M10.5.5 及之后）覆盖同名 PNG 后，本文件记录的 4 个 sha256 再也无法与磁盘内容复现匹配，“文件系统边界/凭据不泄漏”等安全断言的图像证据即失去可校验性；此外 JSON 值用反斜杠分隔符，非 Windows 工具直接读取会解析失败。建议按里程碑命名输出文件（如 M10.5.4-history-session-management.png）并对证据做只写归档，路径统一为 POSIX 正斜杠。
- 判断：本地核对 capturedAt="2026-10-17T12:04:23.417Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.4-history-results.json:85；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1312 · 不采纳

- 位置：docs/testing/M10.5.4-history-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：缺少生成契约中的 browserEngine/browserChannel 与 transportDiagnostics 字段（frontend/tests/history.mjs 会写出这些键，M10.5.9 的历史结果也保留 transportDiagnostics）。后果：无法判定该证据来自 Chromium 还是 WebKit，也无法区分“无浏览器异常”与“被归类为受控传输诊断而抑制的异常”，因此第 19 项“no browser exceptions or credential leaks”这一安全断言在本文件中不可核验。建议补齐引擎标识与传输诊断数组（无诊断时写空数组），以便按引擎合并与审计。
- 判断：本地核对 capturedAt="2026-10-17T12:04:23.417Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.4-history-results.json:4；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1313 · 不采纳

- 位置：docs/testing/M10.5.4-history-results.json:105；OCR原严重度：medium
- 计划：无
- 原意见：证据仅绑定了 jarSha256，没有 sourceSha256（前端/测试源码快照标识），而采集脚本 scripts/collect-m1054-evidence.py 对 sourceSha256 只做存在才校验的处理。触发条件：在不改动 JAR 的前提下修改前端测试或夹具后重跑，本文件仍会以相同 jarSha256 通过采集，产物无法证明“工作区根隔离、凭据脱敏、分页有界”等断言来自当前源码。建议写入本次运行实际使用的 frontend 测试与夹具的 sourceSha256 清单。
- 判断：本地核对 capturedAt="2026-10-17T12:04:23.417Z"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.4-history-results.json:105；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1314 · 不采纳

- 位置：docs/testing/M10.5.4-turns-results.json:72；OCR原严重度：low
- 计划：无
- 原意见：路径分隔符使用 Windows 反斜杠，且指向 .build-cache 这类本地构建缓存目录。触发条件：清单被 POSIX 环境或 CI 读取时，`\` 会被当作文件名字符而非分隔符，导致按 path 查找并重算 sha256 的校验失败；此外缓存目录通常不入库，其他机器上无法复核这三个哈希，证据不可复现。建议统一使用 `/` 分隔符，并确认截图是否应随仓库归档。
- 判断：本地核对 capturedAt="2026-10-17T12:03:52.855Z"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.4-turns-results.json:72；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1315 · 不采纳

- 位置：docs/testing/M10.5.4-turns-results.json:68；OCR原严重度：medium
- 计划：无
- 原意见：清单本身属于 M10.5.4（文件名/docs 路径），但三条截图路径全部指向 M10.5.3 的产物（M10.5.3-turns-*.png）。触发条件：任何按里程碑校验证据的工具/评审流程读取本文件时，会把上一里程碑的截图当作本次 M10.5.4 的通过证据，从而在截图实际来自旧构建时仍然放行（status 仍是 PASS），并且清单里没有任何 milestone/runId/commit 字段可用于交叉校验该 jarSha256 与截图的对应关系。建议重新生成并改用 M10.5.4 命名的截图，并补充可追溯的构建标识。
- 判断：本地核对 capturedAt="2026-10-17T12:03:52.855Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.4-turns-results.json:68；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1316 · 不采纳

- 位置：docs/testing/M10.5.5-browser-results.json:194；OCR原严重度：medium
- 计划：无
- 原意见：截图证据路径指向临时构建缓存目录且使用 Windows 反斜杠分隔（第 183-232 行全部如此）。触发条件：清理 .build-cache 后在 CI/Linux 上复现或归档该证据。后果：路径不可跨平台解析（会被当成含反斜杠的单段文件名），且缓存目录通常是被清理/忽略的，审计方无法凭该 artifact 定位到实际截图；同时也无法判断截图来自哪次构建（缺少 jar 路径与构建号）。建议改为仓库相对、正斜杠分隔的持久化路径，并同时记录对应的 jar/构建标识。
- 判断：本地核对 capturedAt="2026-10-18T01:52:55.332Z"；status="PASS"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.5-browser-results.json:194；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1317 · 不采纳

- 位置：docs/testing/M10.5.5-browser-results.json:198；OCR原严重度：high
- 计划：无
- 原意见：第 183 与 187 行的两个截图证据 sha256 完全相同（d2986ad2a393b44c3ecf6b7e4cffc3f826396d454e699a6573619e723a0dfe33），即 M10-connection.png 与 M10-welcome.png 是同一份字节。触发条件：浏览器脚本在某一状态下截图失败/复用旧文件，或两张截图在同一时刻抓取。后果：两个被声明为独立的 UI 状态（连接中与欢迎态）实际只有一份视觉证据，可能掩盖其中一次截图失败，使“invalid API credentials rejected while browser enters automatically”等与首个渲染状态相关的断言失去视觉依据。建议对每张截图断言内容差异（尺寸/像素/视觉基线），或在生成侧校验 sha256 互不相同，重复时判定为证据缺失。
- 判断：本地核对 capturedAt="2026-10-18T01:52:55.332Z"；status="PASS"。相同内容会有相同摘要，不能反推未执行或复制。浏览器自动同源进入使connection/welcome可已是同一状态；夹具日志仅打印确定性的PASS名称，不同引擎/复跑可逐字相同。应核对执行入口和制品来源，不能要求截图/日志哈希强制互异。
- 证据：docs/testing/M10.5.5-browser-results.json:198；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1318 · 不采纳

- 位置：docs/testing/M10.5.5-browser-results.json:185；OCR原严重度：high
- 计划：无
- 原意见：可复现清单（sourceSha256，第 127-175 行）不完整：仓库中实际存在但未列入清单的文件至少包括 frontend/src/Markdown.tsx、ContentPanel.tsx、ContentSearch.tsx、Conversation.tsx、ExtensionEditor.tsx、SkillsPicker.tsx、content.ts，以及 harness-web/src/main/java/io/jharness/web/WorkbenchContentSearch.java。触发条件：任何下游/审计方用该清单去复算本 PASS 对应的源码集合。后果：直接实现“model HTML is rendered as text”“large payload omission is visible”“full sanitized session query reads real events”等被断言行为的文件（Markdown.tsx、ContentPanel/ContentSearch.tsx、WorkbenchContentSearch.java）都不在哈希覆盖内，因此 PASS 无法与本次审查的代码绑定，源文件在测试后被替换也不会被该清单发现。建议把被测试构建所读取的全部前端源码与 harness-web/harness-testkit 源码纳入清单，或在清单中声明精确的白名单/排除规则并说明理由。
- 判断：本地核对 capturedAt="2026-10-18T01:52:55.332Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.5-browser-results.json:185；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1319 · 不采纳

- 位置：docs/testing/M10.5.5-browser-results.json:226；OCR原严重度：medium
- 计划：无
- 原意见：jarSha256 只是一个裸哈希（第 233 行附近），没有 jar 文件名、构建号、源码 commit 或 Maven 版本信息。触发条件：需要把该 PASS 对应回某一 Git 提交或复现同一 jar。后果：无法确定被测试的 jar 是否由清单中的源码构建而来（哈希一致只能证明 jar 字节未变），也无法重新构建同一产物，复现链在此处断开。建议记录 jar 路径、Git commit/分支、构建命令与 JDK 版本。
- 判断：本地核对 capturedAt="2026-10-18T01:52:55.332Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.5-browser-results.json:226；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1320 · 不采纳

- 位置：docs/testing/M10.5.5-browser-results.json:25；OCR原严重度：medium
- 计划：无
- 原意见：每个 check 只有 name/passed 两个字段（如第 24-27 行），顶层 status 也是硬编码的 PASS。触发条件：任一断言在脚本里被跳过、被 try/catch 吞掉或只做了 UI 文本检查。后果：artifact 无法自证 PASS 是由 checks 计算得出的，也没有为脱敏类断言（token 不在 URL/storage/页面文本/下载报告、secret 从事件文本中抹除）保留探查结果（命中的位置与抹除计数），因此这些安全结论只是布尔自述。建议为每个 check 增加 evidence（断言对象、实际观测值/计数、耗时），并由 checks 汇总生成 status。
- 判断：本地核对 capturedAt="2026-10-18T01:52:55.332Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.5-browser-results.json:25；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1321 · 不采纳

- 位置：docs/testing/M10.5.5-browser-results.json:188；OCR原严重度：medium
- 计划：无
- 原意见：requests 只记录了 taskSubmissions=9 与 total=242 两个聚合值（第 176-179 行），缺少可验证重放不变量的原始事实：没有任务 ID、没有每个场景的 POST 次数/游标取值/审批 ID 映射。触发条件：需要复核“duplicate click creates exactly one actual task”“lost submit response … no POST replay”“offline recovery never creates a new task”“invalid cursor pauses stream rather than silently replaying”“UNKNOWN … no replay control”这些断言时。后果：仅凭 9 次提交与 242 次请求的聚合计数无法区分“正确去重”与“重复提交后 UI 隐藏”，也无法证明 UNKNOWN/游标失效路径没有发生重放；同时 runtime.modelRequests=14 与 paidRequests=0（第 121-126 行）也缺少说明这 14 次模型请求走的是哪个 keyless 通道。建议按场景输出 taskIds、per-scenario 请求计数与 cursor 前后值，使重放不变量可由 artifact 独立验证。
- 判断：本地核对 capturedAt="2026-10-18T01:52:55.332Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.5-browser-results.json:188；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1322 · 不采纳

- 位置：docs/testing/M10.5.5-history-results.json:85；OCR原严重度：medium
- 计划：无
- 原意见：截图证据不可复现且路径不可移植：四个路径都指向共享的 `.build-cache\M10.5.2-*.png`（沿用 history.mjs:51 的硬编码 M10.5.2 前缀），下一次历史回归会原地覆盖同名文件，事后无法再用此处 sha256 复核；且 Windows 反斜杠路径仅因 scripts/collect-m1055-evidence.py:59 做了 `chr(92)->'/'` 转换才可解析，其它 JSON 消费者/CI 直接拼接会失败。对比 docs/testing/M10.5.9-chromium-history-results.json 已采用 `.build-cache/M10.5.9/browser/chromium/...` 隔离目录。建议按“里程碑/浏览器/产物”分目录并为截图生成唯一文件名，路径统一用 `/`。
- 判断：本地核对 capturedAt="2026-10-18T01:51:15.792Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.5-history-results.json:85；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1323 · 不采纳

- 位置：docs/testing/M10.5.5-history-results.json:2；OCR原严重度：high
- 计划：无
- 原意见：第2行 `status: "PASS"` 无法证明聚合结论由完整套件推导：frontend/tests/history.mjs:122 在写结果时把 `status: 'PASS'` 写成字面常量，与 `checks` 里各项的 `passed` 完全无关，即任意 `passed:false`（例如第72行“model cannot escape root”、第80行“no browser exceptions or credential leaks”失败）仍会产出 PASS。同时本文件缺少 `milestone`、`browserEngine`、`sourceSha256`/commit 等身份字段（当前 harness 已写 `browserEngine`），无法区分是否来自本次运行。建议由 `checks.every(c => c.passed)` 推导 status，并补 milestone、engine、源/提交身份，否则该 PASS 不可复现验证。
- 判断：本地核对 checks=数组(19项)；checks[0].passed=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.5-history-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.5-history-results.json:5；docs/testing/M10.5.5-history-results.json:8

## OCR-1324 · 不采纳

- 位置：docs/testing/M10.5.5-history-results.json:102；OCR原严重度：high
- 计划：无
- 原意见：计费口径不可验证，存在掩盖付费请求的风险：`paidRequests` 在 history.mjs:122 中被硬编码为 `0`，并非实测值；`modelRequests` 取 `wire.length`，把本地 fixture 桩请求与真实服务商请求混在一起计数（本文件 138 次却 0 次付费，无任何来源说明）。因此该字段不能作为“无付费/无凭据外发请求”的证据。建议记录 provider/mock 模式（如 `providerMode: "local-fixture"`）、实际请求目标主机白名单摘要或请求日志哈希，`paidRequests` 由真实端点标识推导而非常量。
- 判断：本地核对 paidRequests=0；modelRequests=138。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.5-history-results.json:102；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.5-history-results.json:103

## OCR-1325 · 不采纳

- 位置：docs/testing/M10.5.4-models-results.json:96；OCR原严重度：medium
- 计划：无
- 原意见：三张截图的路径都位于 .build-cache/，而 .gitignore:8 已忽略该目录，仓库中并不存在这些文件，因此这里的 sha256 对外完全不可复核；scripts/collect-m1054-evidence.py:59 的摘要校验也只在生成机上成立。触发条件：换机核对、重新克隆仓库或清理 .build-cache 后再验证证据即会失败，无法重放该 PASS 结论。建议把截图归档到受版本控制的证据目录，或在 JSON 中记录归档（上传）位置与二次摘要。
- 判断：本地核对 status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.4-models-results.json:96；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1326 · 不采纳

- 位置：docs/testing/M10.5.4-models-results.json:109；OCR原严重度：medium
- 计划：无
- 原意见：请求计数的自证能力不足：paidRequests 是生成脚本里的硬编码常量（frontend/tests/models.mjs:104 直接写 paidRequests:0），不是按实际出站请求统计；modelRequests 只有总数 8，未按检查项归属。触发条件：若 endpoint 被改为非本机地址或脚本被改动，本字段仍会写 0，所以第 10 行“configuration check performs no provider request”与第 70 行“settings check is explicitly local and free of model calls”无法由本产物独立证实。建议补充请求目标主机/协议的分布，以及“零付费请求”的判定依据（例如记录全部出站 host 均为 127.0.0.1）。
- 判断：本地核对 status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.4-models-results.json:109；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1327 · 不采纳

- 位置：docs/testing/M10.5.4-models-results.json:74；OCR原严重度：high
- 计划：无
- 原意见：该 PASS 产物与当前生成脚本不一致，无法用现有源码复现：frontend/tests/models.mjs:98 写入的检查名是 'no unexpected browser runtime errors'，本文件第 74 行为 'no browser runtime errors'；同时 models.mjs:104 会输出 transportDiagnostics 键，本文件缺失该键。触发条件：用当前脚本重跑 models.mjs 会得到不同内容，说明本文件来自旧版脚本或被手工改过，未能绑定最终源码。而 scripts/collect-m1054-evidence.py:52 只校验 checks 数量==22 且 passed 为真，不会发现检查名/字段漂移，因此该产物本身不能证明当前源码的 22 项断言全部通过。此外缺失 transportDiagnostics 会使 models.mjs:72 中被识别为“宿主停机”的页面错误不可见，而第 74 行仍宣称“无浏览器运行时错误”，与脚本实际放宽的范围不符。建议按最终脚本重新生成，并让采集器校验检查名集合而非仅数量，同时补回 transportDiagnostics。
- 判断：本地核对 status="PASS"。将当前源码字段/新功能文件集套用到带日期的旧里程碑快照，不能证明旧记录陈旧造假或漏测。M10.5.9新增跨引擎runtime/transportDiagnostics及M10.5.6～.8新增UI/检索文件，旧阶段不应补填这些后来字段或重标通过。
- 证据：docs/testing/M10.5.4-models-results.json:74；.agent:当前接续状态；docs/testing/M10.5.9-validation.md:26

## OCR-1328 · 不采纳

- 位置：docs/testing/M10.5.4-models-results.json:3；OCR原严重度：medium
- 计划：无
- 原意见：字段命名/必填项与同批产物不一致，影响工具消费与版本绑定：同批记录使用 capturedAt/scope/milestone（见 scripts/collect-m1054-evidence.py:102 与 docs/testing/M10.5.4-native-ci-results.json:2），本文件只有 date，且缺少 milestone/scope 与采集器支持的 sourceSha256（scripts/collect-m1054-evidence.py:60）。触发条件：按 capturedAt/milestone 索引这批 JSON 的消费者读取本文件时会取到空值；同时本文件仅用 jarSha256 标识版本，无法证明该 22 项 PASS 对应哪一份前端/测试源码。建议统一时间键名并补充 milestone 与 sourceSha256（前端测试源码哈希）。
- 判断：本地核对 status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.4-models-results.json:3；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1329 · 不采纳

- 位置：docs/testing/M10.5.4-models-results.json:108；OCR原严重度：low
- 计划：无
- 原意见：环境溯源不完整：browser 只有版本号字符串，没有浏览器名/引擎（models.mjs:72 会按 JHARNESS_BROWSER_CHANNEL 选择渠道，产物未记录该值），也没有 OS/JDK 版本，无法复现“同一环境、无真实服务商流量”的运行。触发条件：换浏览器渠道或平台重跑时无法判断结果是否可比。建议补充 browserName/channel、OS 与 Java 版本字段。
- 判断：本地核对 status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.4-models-results.json:108；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1330 · 不采纳

- 位置：docs/testing/M10.5.5-launcher-results.json:15；OCR原严重度：medium
- 计划：无
- 原意见：`paidRequests: 0` 与 `modelEndpoint` 的隔离结论只有描述、没有采集依据（未记录 endpoint 是如何注入的、是否有网络抓包/代理变量白名单证明、也未记录实际使用的环境变量）。触发条件：若启动脚本没有强制覆盖模型 endpoint 环境变量，或测试进程继承了宿主机的真实 endpoint，`paidRequests` 可能为 0 但结论不成立。建议记录注入的具体环境变量名与取值来源（脱敏后）以及校验手段，使该隔离声明可复现。
- 判断：本地核对 modelEndpoint="unreachable loopback :9; no task submissions"；paidRequests=0。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.5-launcher-results.json:15；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.5-launcher-results.json:16

## OCR-1331 · 不采纳

- 位置：docs/testing/M10.5.5-launcher-results.json:22；OCR原严重度：low
- 计划：无
- 原意见：哈希清单只覆盖 3 个源文件与 1 个 JAR，且未记录采集时的 git commit 或哈希计算命令，因此无法判断这些哈希是本次实测计算得出还是从上一里程碑（M10.5.4 的 `sourceSha256` 三项与本文件完全相同）沿用。触发条件：源码在两次记录之间被改动而证据未刷新时，PASS 会绑定到错误的可执行集合。建议补充 `gitCommit`/`collectedBy` 或哈希计算脚本名，并在采集时重新计算而非手工填写。
- 判断：本地核对 sourceSha256=对象(3键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.5-launcher-results.json:22；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.5-launcher-results.json:17

## OCR-1332 · 部分采纳

- 位置：docs/testing/M10.5.5-launcher-results.json:14；OCR原严重度：medium
- 计划：R6-03 / P2
- 原意见：该条是“生产环境直接打开浏览器且从不多次复制凭据”的宽泛安全断言，但文件内没有任何指向验证方式（单测/脚本断言名、日志片段）的引用，无法证明它被测过；同时需确认本产物自身不含 token 材料（当前仅见哈希与描述，尚属安全）。触发条件：后续有人修改 `scripts/open-workbench.ps1` 的凭据输出逻辑时，该断言不会自动失效。建议把该断言拆成可执行检查项并引用对应测试用例名。
- 判断：本地核对 capturedAt="2026-10-18T01:53:23.132226+00:00"；status="PASS"。自动化明确传-NoBrowser/-NoClipboard，生产默认打开浏览器的分支不属于这8项实测。采纳把静态生产说明与自动化结果分开，增加可控浏览器调用替身验证；不要求每次验收真实改用户剪贴板。
- 证据：docs/testing/M10.5.5-launcher-results.json:14；scripts/test-open-workbench.py:82；scripts/test-open-workbench.py:137；scripts/open-workbench.ps1

## OCR-1333 · 不采纳

- 位置：docs/testing/M10.5.5-launcher-results.json:12；OCR原严重度：medium
- 计划：无
- 原意见：`checks` 数组元素是无 ID、无断言结果的自由文本，与第 2 行的 PASS 之间没有机器可校验的映射关系；后续里程碑结果聚合或回归比对时无法定位“哪一项变了/哪一项失败”。触发条件：把该文件作为门禁输入或做跨里程碑 diff 时必然失效。建议给每项加稳定 ID（如 `launcher.duplicate-launch`）并记录单项结论，散文描述另放到 `description` 字段。
- 判断：本地核对 checks=数组(8项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.5-launcher-results.json:12；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.5-launcher-results.json:4

## OCR-1334 · 不采纳

- 位置：docs/testing/M10.5.5-launcher-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：第 2 行的整体 `"status": "PASS"` 缺少可复核的依据：文件没有逐项结果、退出码、执行命令、耗时或日志路径，只有 8 条散文式描述。触发条件：任何人（或发布/审计流程）试图独立复核该里程碑时，无法区分“每项都已执行且通过”与“部分项被跳过/曾失败”。建议在保留 `checks` 的同时补充结构化结果（如 `results: [{ id, status, exitCode, logRef }]`）以及在采集时实际执行的命令。
- 判断：本地核对 checks=数组(8项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.5-launcher-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.5-launcher-results.json:4

## OCR-1335 · 不采纳

- 位置：docs/testing/M10.5.5-models-results.json:96；OCR原严重度：medium
- 计划：无
- 原意见：证据路径与报告版本不一致且跨版本复用同一文件名：本文件是 M10.5.5 的报告，但截图仍指向 `.build-cache/M10.5.4-*.png`。对比 `docs/testing/M10.5.4-models-results.json` 可见同名文件在 10.5.4 中的 sha256 为 5c90f9a8…/1bd47d09…/c21b6578…，而本文件记录的是 cf677754…/0779cdfc…/19b3d31d…，说明这些 PNG 被覆盖重写但仍沿用旧名。触发条件：任何一次重跑都会使早先版本报告中的 sha256 永久失效，且 10.5.4–10.5.8 的多份报告无法区分各自实际截图。建议将输出目录/文件名按版本隔离（10.5.9 已采用 `.build-cache/M10.5.9/browser/<engine>/...` 的做法），保证每份报告的哈希可复验。
- 判断：本地核对 status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.5-models-results.json:96；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1336 · 不采纳

- 位置：docs/testing/M10.5.5-models-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：顶层 `status` 是独立硬编码字段，与 `checks` 数组之间没有任何约束或派生关系：只要写入方给出一条 `passed: false` 而忘记同步 status，报告仍会宣称 PASS。触发条件：任一 check 失败或被跳过时手工/脚本写入 status 的路径分叉。建议由 checks 汇总派生 status（例如全部 passed 才 PASS，任一失败则 FAIL，空数组则 INCONCLUSIVE），或在文件中同时给出 `total`/`failed`/`skipped` 计数以便校验一致性。
- 判断：本地核对 status="PASS"；checks=数组(22项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.5-models-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.5-models-results.json:4

## OCR-1337 · 不采纳

- 位置：docs/testing/M10.5.5-models-results.json:109；OCR原严重度：low
- 计划：无
- 原意见：请求计数与清理结论缺少可复核依据：`modelRequests: 8` 与 `paidRequests: 0` 并列，但文件中没有字段说明这 8 次请求是本地 mock、本地模型还是真实网络调用，也没有指出对应的 harness 日志；`hostStopped: true` 同样无 PID、停止时间或日志引用。触发条件：审计方按此报告判定“未产生付费调用、host 已停止”时无法证伪。建议补充如 `requestLogPath`、`requestsMocked`/`providerHosts`（不写入任何凭据值）以及 `hostStopEvidence`（日志路径+时间戳）等可追溯字段。
- 判断：本地核对 status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.5-models-results.json:109；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1338 · 不采纳

- 位置：docs/testing/M10.5.5-diagnosis-results.json:61；OCR原严重度：medium
- 计划：无
- 原意见：截图哈希绑定无法复现（第 58–74 行）。四个 screenshot.path 指向 .build-cache 下的 M10.5.1-*.png，而 .build-cache 在 .gitignore 第 8 行被整体忽略：随仓库发布的证据里既没有图片本体，也没有可追溯的归档副本；这些文件名也不带里程碑/批次信息，每次跑 diagnosis 都会原位覆盖同名文件。仓库内现有事实即可证明哈希已失效：同一路径 .build-cache/M10.5.1-shell.png 在 M10.5.1 记录为 9e4b27d7…，本文件记录为 9d538e8a…，M10.5.2…M10.5.8 又各不相同；而 scripts/collect-m1055-evidence.py:58-59 是按 ROOT / path 去比对当前 .build-cache 文件，所以重跑任意一次诊断后本文件的四个哈希必然对不上，PASS 与具体构建产物的绑定随之失效。M10.5.9 改用子目录也无法根治，仍受 gitignore 影响。建议把截图落到随证据提交的目录（如 docs/testing/artifacts/M10.5.5/）或额外记录截图归档文件的 sha256 与提交号，并采用按运行唯一的文件名，避免跨里程碑覆盖。
- 判断：本地核对 capturedAt="2026-10-18T01:52:28.383Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.5-diagnosis-results.json:61；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1339 · 不采纳

- 位置：docs/testing/M10.5.5-diagnosis-results.json:2；OCR原严重度：low
- 计划：无
- 原意见：第 2 行的 status 是字面量，而不是由 13 项 check 聚合出的字段：文件里既没有 failed/skipped 计数，也没有状态推导依据，而 scripts/collect-m1055-evidence.py:57 只做 data['status'] == 'PASS' 的等值判断。一旦某项 check 为 false 而生成端仍写入固定字符串（frontend/tests/diagnosis.mjs:93 即为硬编码 status: 'PASS'），该证据仍会被判为通过。建议额外记录由 checks 计算出的派生状态（如 failedChecks: 0 或 derivedStatus），让上层脚本能校验聚合结果而不只信任一个字面量。
- 判断：本地核对 capturedAt="2026-10-18T01:52:28.383Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.5-diagnosis-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1340 · 不采纳

- 位置：docs/testing/M10.5.5-diagnosis-results.json:79；OCR原严重度：medium
- 计划：无
- 原意见：安全断言无法由本文件自证（第 76–83 行）。paidRequests=0、modelRequests=5、writeExists=false、opened=4 都只是 fixture 自报的聚合计数，而 requests 日志中没有任何模型调用端点（只有 /v1/info、/v1/tasks、/v1/models 列表、questions、turns、events、approvals、diagnosis、cancel），也没有逐条请求的方法+响应状态或时间戳，因此无法从证据本身区分「0 次付费调用」与「计数器未采集/被默认值填充」。同理 check「fixture closes with zero paid requests」所依赖的 host.exitCode===0（frontend/tests/diagnosis.mjs:92）在该 JSON 中完全没有落盘。建议补记模型调用端点与响应状态（或付费/免费调用分类计数及其来源字段）以及 host 退出码，使零付费、零写入结论可独立复核。
- 判断：本地核对 capturedAt="2026-10-18T01:52:28.383Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.5-diagnosis-results.json:79；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1341 · 不采纳

- 位置：docs/testing/M10.5.5-diagnosis-results.json:50；OCR原严重度：low
- 计划：无
- 原意见：第 49–52 行这条否定式断言没有对应数据字段：文件只记录 passed: true，却没有记录实际采集到的 console/page 错误数组（对比 docs/testing/M10-browser-results.json 中的 browserErrors）。这样「0 个运行时错误」与「错误采集链路根本没执行」在该证据里不可区分。建议在 schema 中补充 browserErrors: []（或错误条数），使该 check 可被独立复核。
- 判断：本地核对 capturedAt="2026-10-18T01:52:28.383Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.5-diagnosis-results.json:50；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1342 · 不采纳

- 位置：docs/testing/M10.5.5-settings-browser-results.json:113；OCR原严重度：medium
- 计划：无
- 原意见：Screenshot evidence is referenced with Windows-only backslash separators under `.build-cache`, which is a transient build directory that is normally not committed. On any non-Windows machine these paths do not resolve, and after the cache is cleaned the stored `sha256` values cannot be re-verified, so the evidence for the mobile/overflow and unavailable-service checks becomes unverifiable. Record portable POSIX relative paths to committed artifacts (e.g. `docs/testing/artifacts/M10-settings-plugins.png`).
- 判断：本地核对 screenshots[0].sha256="f16ca6de1ee0d9e0e198a6dacb0548f69edc7520d859e26bedf7068ba67e9a7f"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.5-settings-browser-results.json:113；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.5-settings-browser-results.json:114

## OCR-1343 · 不采纳

- 位置：docs/testing/M10.5.5-settings-browser-results.json:137；OCR原严重度：low
- 计划：无
- 原意见：`modelRequests`/`paidRequests` are bare aggregates with no per-check attribution. Several checks are expected to hit the real model (saved URL + vault key, plugin tools in request body, SDK permission blocking), so an omitted or double-counted call cannot be detected and the `paidRequests: 0` claim cannot be reproduced or audited. Attach the request counters to the individual check entries (or record a per-check breakdown) so the totals can be reconciled.
- 判断：本地核对 modelRequests=4；paidRequests=0。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.5-settings-browser-results.json:137；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.5-settings-browser-results.json:138

## OCR-1344 · 部分采纳

- 位置：docs/testing/M10.5.5-settings-browser-results.json:4；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：The `browser` field stores only a version string (`155.0.8059.82`) with no engine name. Because several checks assert engine-specific behaviour (mobile layout overflow, cross-origin rejection, SDK permission blocking), a reviewer cannot tell whether these results came from Chromium, Firefox or WebKit, so the record is not reproducible and cannot be attributed. Store the engine name alongside the version, e.g. `"browser": { "name": "chromium", "version": "155.0.8059.82" }`.
- 判断：本地核对 browser="155.0.8059.82"。browser仅存155.0.8059.82版本，单文件未记engine/channel；采纳新收据显式浏览器引擎/通道。配套阶段记录已明确本机Chrome，不应倒填未实际测过的Firefox/WebKit。
- 证据：docs/testing/M10.5.5-settings-browser-results.json:4；docs/testing/M10.5.5-validation.md

## OCR-1345 · 不采纳

- 位置：docs/testing/M10.5.5-questions-results.json:112；OCR原严重度：low
- 计划：无
- 原意见：三张截图的 `path` 指向 `.build-cache/`（可被清理的缓存目录），仓库内实际不存在这些 PNG，因此这些 sha256 在仓库中无法复算，无法作为持久证据校验。触发条件：清空 `.build-cache` 或重跑其他里程碑的截图（同名覆盖）后，本文件的截图哈希即失效且无法察觉。建议将截图随证据一起落到 `docs/testing/` 下的持久路径，或保留采集时对 `.build-cache` 快照的校验记录（collect-m1055-evidence.py 只在采集当次校验），避免长期证据失效。
- 判断：本地核对 screenshots[0].path=".build-cache/M10.5.5-questions-desktop.png"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.5-questions-results.json:112；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1346 · 不采纳

- 位置：docs/testing/M10.5.5-questions-results.json:124；OCR原严重度：medium
- 计划：无
- 原意见：此证据文件缺少 `transportDiagnostics` 键。生成器 frontend/tests/questions.mjs 第 102 行固定写出 `transportDiagnostics`（即使为空数组 `[]`），且 M10.5.9-*-questions-results.json 同类产物均包含该键，说明本文件与当前产物 schema 不一致（为旧版/被裁剪的证据）。影响：`no browser runtime exceptions` 这一项只统计未被 `stoppedHostDiagnostic` 归类的页面异常，被抑制的宿主停机期访问控制诊断全部写入 `transportDiagnostics`；缺少该键后，无法判断 26 项 PASS 期间是否产生过被抑制的诊断，证据无法自证完整。建议按生成器 schema 补齐该键（无诊断时为 `[]`），或明确标注该字段缺失的版本原因。
- 判断：本地核对 capturedAt="2026-10-18T01:51:34.815Z"；status="PASS"。将当前源码字段/新功能文件集套用到带日期的旧里程碑快照，不能证明旧记录陈旧造假或漏测。M10.5.9新增跨引擎runtime/transportDiagnostics及M10.5.6～.8新增UI/检索文件，旧阶段不应补填这些后来字段或重标通过。
- 证据：docs/testing/M10.5.5-questions-results.json:124；.agent:当前接续状态；docs/testing/M10.5.9-validation.md:26

## OCR-1347 · 不采纳

- 位置：docs/testing/M10.5.4-native-ci-results.json:588；OCR原严重度：low
- 计划：无
- 原意见：真实模型请求数没有期望约束：只记录 `modelRequests: 4` 与 `paidRequests: 0`，没有任何字段说明 4 是必须达到的下界；采集侧也只校验 26 项检查全为 true。触发条件：若“configured plugin tools reach actual model request”“selected permission reaches real SDK”等检查因为短路/替身实现而不再发出真实请求（`modelRequests` 降为 0 或 1），只要布尔值仍为 true，本文件依旧呈现 PASS。建议把 `modelRequests` 的期望值（如 `expectedModelRequests: 4`）写入证据并在采集时断言 `modelRequests >= 4`，同时记录检查名单摘要以防检查被替换。
- 判断：本地核对 chromeSettings.modelRequests=4。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.4-native-ci-results.json:588；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1348 · 不采纳

- 位置：docs/testing/M10.5.4-native-ci-results.json:292；OCR原严重度：medium
- 计划：无
- 原意见：产物与源码的绑定在本文件中不可验证：每个平台/用例的 `sourceSha256` 都被剔除了，只剩各平台互不相同的 `jarSha256`（windows-2022 与 ubuntu/macOS、本地 JAR 三者均不同）。触发条件：审计者仅凭本文件需要确认“某平台被测 JAR 确实由 `sourceCommit` 的 280 个源文件构建”时无法做到，只能用 runId/jobUrl 回查 CI，且平台间 JAR 不一致会被误读为源码漂移。建议在每个平台（或每个 case）中记录 `sourceCommit` 与 `sourceManifestSha256`（280 条清单的摘要），使每个产物都能独立绑定到记录的源码清单。
- 判断：本地核对 sourceSha256=对象(280键)；platforms.windows-2022.cases.credentials.jarSha256="cce44b3124b4d51cd5f4bb8631ecc9935eaa46610b69f7fd31511acd74327747"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.4-native-ci-results.json:292；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.4-native-ci-results.json:9；docs/testing/M10.5.4-native-ci-results.json:315

## OCR-1349 · 不采纳

- 位置：docs/testing/M10.5.4-native-ci-results.json:601；OCR原严重度：medium
- 计划：无
- 原意见：旧版兼容性证据的版本标识不可唯一解析：`legacyCommit` 只有 7 位缩写 `fdd326b`，与 `sourceCommit` 的 40 位全 SHA 不一致，无法排除缩写歧义；`legacySourceSha256` 只有一个值，作用域（覆盖哪些文件、是否包含 POM 与探针脚本）未说明，也无法与 `sourceSha256` 的逐文件清单比对。触发条件：需要复现 `oldWriteNewRead/newRotateOldRead/newDeleteOldRead` 结论或校验旧构建未被替换时，无法定位与校验确切的旧版源码树。建议改为 40 位全 SHA，并记录旧版的逐文件清单或清单摘要，与 `sourceSha256` 采用同一口径。
- 判断：本地核对 windowsCompatibility.legacyCommit="fdd326b"；sourceCommit="81a9deee4aea319e7f5af26ba82767791a9441ce"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.4-native-ci-results.json:601；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.4-native-ci-results.json:8

## OCR-1350 · 不采纳

- 位置：docs/testing/M10.5.4-native-ci-results.json:452；OCR原严重度：medium
- 计划：无
- 原意见：浏览器侧的安全结论没有可复核的证据载体：26 项检查只记录 `name`+`passed` 布尔值，没有浏览器报告文件的 `sha256`/`artifactId`，也没有 runId/jobId；截图是 `.build-cache\M10-settings-*.png` 的 Windows 相对路径，既未进入任何 artifact，也未随证据归档。触发条件：清理构建缓存或重跑浏览器测试后，本文件中“settings rejects unauthenticated operator / cross-origin mutation”“responses and disk exclude key and credential target”“event output excludes raw API key”等断言（456-463、476-479、488-491、504-507 行）就无法再被验证，而顶层 `status: PASS` 仍引用它们。建议记录浏览器原始报告的 `reportSha256`，并把截图纳入 artifact 或以可解析的仓库路径 + 已提交文件呈现，使脱敏与鉴权结论可在 HTTP/持久化/事件层被独立复核，而不仅依赖 UI 断言名称。
- 判断：本地核对 chromeSettings.checks[0].name="settings rejects unauthenticated operator"；platforms.windows-2022.cases.credentials.passed=true。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.4-native-ci-results.json:452；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M10.5.4-native-ci-results.json:458；docs/testing/M10.5.4-native-ci-results.json:312

## OCR-1351 · 不采纳

- 位置：docs/testing/M10.5.4-native-ci-results.json:3；OCR原严重度：medium
- 计划：无
- 原意见：顶层 `status: PASS` 与平台内的失败记录并存：Linux 的 `missing-service`(354-355 行) 和 `locked-service`(372-373 行) 都是 `passed:false`、`cleanup:false`、`failureCode:PROBE_FAILED`。它们靠 `expectationMet:true` 被计入 PASS，但本文件没有任何字段声明 PASS 的判定口径（“预期不可用用例的原始失败不计入 PASS”），也没有按平台的汇总状态。触发条件：发布门禁/看板只读顶层 `status` 时，会把 Linux 上“凭据存储不可用、清理未执行”当成全绿通过，无法区分“探测按预期失败”和“Linux 原生路径整体通过”。建议增加 `platformStatus` / `unavailableCasesAccepted` / `cleanupVerified` 等字段（或在 `limits` 中显式写明聚合口径），让 PASS 的可解释范围可在文件内自证。
- 判断：本地核对 status="PASS"；limits=数组(3项)。missing-service/locked-service是刻意不可用负例，expectationMet=true表示明确失败符合期望，非凭据成功声明；顶层专项PASS与负例passed=false不矛盾。
- 证据：docs/testing/M10.5.4-native-ci-results.json:3；docs/testing/M10.5.4-native-ci-results.json；docs/testing/M10.5.4-validation.md；docs/testing/M10.5.4-native-ci-results.json:610

## OCR-1352 · 不采纳

- 位置：docs/testing/M10.5.4-native-ci-results.json:354；OCR原严重度：medium
- 计划：无
- 原意见：不可用用例只记录 `checks: {cleanup:false}`，没有任何字段证明“本次没有写入过任何凭据目标”。触发条件：同一 Linux runner 上 `credentials` 用例（07:41:10）在 `locked-service` 用例（07:41:11）之前已成功写入/轮换过凭据；若该次写入未删净、或 locked 期间清理确实失败，本文件呈现的证据与“存储不可用、无需清理”完全一致，读者的 `expectationMet:true` 会掩盖真实残留。也就是说 `cleanup:false` 既可能是“不适用”也可能是“清理失败”，两者不可区分。建议由探针输出区分的语义（如 `cleanupResult: "not-applicable"|"failed"` 或 `wroteCredential:false`），并在采集脚本中对“不可用用例”额外断言未写入/未残留，而不是把 `cleanup:false` 本身当成预期值。
- 判断：本地核对 platforms.windows-2022.cases.credentials=对象(12键)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.4-native-ci-results.json:354；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.4-native-ci-results.json:299

## OCR-1353 · 不采纳

- 位置：docs/testing/M10.5.4-native-ci-results.json:431；OCR原严重度：medium
- 计划：无
- 原意见：本地原生证据缺少 `receiptSha256`：5 个 CI 用例（319、350、366、382、413 行）都记录了原始 receipt 的 SHA-256，而 `localNative` 只有 `jarSha256`/`probeScriptSha256`/`timestamp` 等字段，且 `sourceSha256` 已被剥离。触发条件：本地 `.build-cache/M10.5.4-native-credentials.json` 在采集后被修改（例如把 `checks` 全部改成 true）时，本文件无法察觉——`jarSha256` 只绑定 JAR，不绑定这份报告内容，采集脚本也只逐字段比对。建议为 `localNative` 记录报告文件的 `receiptSha256`（与 CI 用例同构），使本地 PASS 也可字节级复核。
- 判断：本地核对 platforms.windows-2022.cases.credentials.receiptSha256="a6280ebaa61b95d487c321901601d45de7795931364f72f7c6f816198bf3b9e1"；localNative=对象(11键)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.4-native-ci-results.json:431；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.4-native-ci-results.json:319

## OCR-1354 · 部分采纳

- 位置：docs/testing/M10.5.5-turns-results.json:72；OCR原严重度：low
- 计划：R0-02 / P3
- 原意见：跨平台兼容性问题：path 使用 Windows 反斜杠分隔符（JSON 中为 `\\`），而仓库其余 M10.5.9 产物已使用正斜杠（如 `.build-cache/M10.5.9/browser/chromium/...`）。在 Linux/macOS 或 CI 上按该字符串直接打开文件会得到字面文件名 `\.build-cache\M10.5.3-turns-dark.png` 而失败。建议统一输出 POSIX 风格路径（或在消费端做分隔符归一化）。
- 判断：本地核对 capturedAt="2026-10-18T01:52:09.747Z"；status="PASS"。相应screenshots路径确使用Windows反斜杠，collector以replace归一化后可以验证。采纳后续生成端统一POSIX相对路径；旧快照由版本兼容读取，不能称当前collector完全不可用。
- 证据：docs/testing/M10.5.5-turns-results.json:72；scripts/collect-m1058-evidence.py:55

## OCR-1355 · 不采纳

- 位置：docs/testing/M10.5.5-turns-results.json:68；OCR原严重度：medium
- 计划：无
- 原意见：证据完整性缺陷：这里 sha256 指向的截图位于 `.build-cache/`（`.gitignore` 第 8 行已忽略该目录），且文件名是固定 basename（M10.5.3-turns-desktop/dark/mobile）。结果是：(1) 仓库中不存在被哈希的图片，公证者无法复算 sha256 验证本记录；(2) 同一路径在 docs/testing/M10.5.3-turns-results.json、M10.5.4-turns-results.json 等文件中出现但哈希各不相同（68-77 行），说明后续里程碑运行会覆盖同一批 png，本文件记录的哈希一旦被覆盖就再也无法对应任何现存文件。建议把截图归档到受版本控制的目录（如 `docs/testing/M10.5.5/turns-desktop.png`）并改用里程碑唯一的文件名，再记录该归档路径的哈希。
- 判断：本地核对 capturedAt="2026-10-18T01:52:09.747Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.5-turns-results.json:68；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1356 · 不采纳

- 位置：docs/testing/M10.5.6-browser-results.json:199；OCR原严重度：medium
- 计划：无
- 原意见：截图证据路径不可稳定复现：一是使用共享目录 `.build-cache` 加固定文件名，后续任何一次 M10 运行都会覆盖同名文件，本文件记录的 sha256 事后无法再校验；二是路径使用 Windows 反斜杠分隔符（生成端为 `p.replace(root + '\\', '')`），在非 Windows 环境无法解析为相对路径，跨平台复核会失败。建议把截图落到每次运行唯一的目录（含 run id / 时间戳），仅记录规范化的 POSIX 相对路径，并在报告中注明这些文件是否随产物归档。
- 判断：本地核对 capturedAt="2026-10-18T02:57:43.527Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.6-browser-results.json:199；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1357 · 不采纳

- 位置：docs/testing/M10.5.6-browser-results.json:2；OCR原严重度：high
- 计划：无
- 原意见：该顶层 `status` 与 `checks` 并非聚合关系：生成端（frontend/tests/browser.mjs:227）无条件写入 `status: 'PASS'`，本文件也没有记录检查总数、失败数、跳过数或 `skipped/notConducted` 标记。触发条件：任一 check 在记录前抛异常、被条件跳过或 checks 数组被截断时，产物仍然输出 PASS，下游（如 docs/testing/M10-validation.md 的“28项检查均通过”结论）无法区分“全部执行并通过”和“部分未执行”。建议在产物中固化 `checkTotals: {expected, passed, failed, skipped}` 与每个 check 的 `status` 枚举，并要求 `status === 'PASS'` 由这些计数推导得出，而非硬编码。
- 判断：本地核对 status="PASS"；checks=数组(28项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.6-browser-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.6-browser-results.json:7

## OCR-1358 · 不采纳

- 位置：docs/testing/M10.5.6-browser-results.json:203；OCR原严重度：medium
- 计划：无
- 原意见：`M10-welcome.png` 与上方 `M10-connection.png` 的 sha256 完全相同（d2986ad2…），说明这两张“连接前/欢迎页”截图是逐字节相同的文件：两次 fullPage 截图之间页面没有可观测变化，或者两张图实际是同一瞬间的重复捕获。触发条件：截图调用位于 DOM 状态变更生效之前（browser.mjs 中 connection 截图在 connect() 之前、welcome 在之后，若欢迎态渲染依赖异步事件则可能尚未生效）。后果是“自动进入浏览器”这一状态缺少有效视觉证据，PASS 结论被弱化。建议为两张图增加可断言的差异点（等待目标元素可见后再截图），并在生成端断言不同名字的截图 sha256 互不相同。
- 判断：本地核对 capturedAt="2026-10-18T02:57:43.527Z"；status="PASS"。相同内容会有相同摘要，不能反推未执行或复制。浏览器自动同源进入使connection/welcome可已是同一状态；夹具日志仅打印确定性的PASS名称，不同引擎/复跑可逐字相同。应核对执行入口和制品来源，不能要求截图/日志哈希强制互异。
- 证据：docs/testing/M10.5.6-browser-results.json:203；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1359 · 不采纳

- 位置：docs/testing/M10.5.6-browser-results.json:231；OCR原严重度：medium
- 计划：无
- 原意见：来源绑定不完整：`sourceSha256` / `jarSha256` 只是事后对工作区文件与 target 下 jar 求哈希，产物中没有记录被服务的产物（frontend/dist 打包进 jar 的 HTML/JS/CSS）的摘要，也没有构建标识或 jar 绝对路径；而生成端在采集 sourceSha256 时显式排除了 `frontend/dist/`。触发条件：dist 未重新构建（stale 前端产物被打包）或 `testedOrigin` 上运行的是另一次构建的进程时，产物依然记录为 PASS 并通过哈希“背书”。建议增加被服务端的资源摘要（例如从 `/`、主 JS、CSS 抓取并计算 sha256）以及服务进程的构建 id/启动时间，使 `testedOrigin` 与实际被测构建可对齐。
- 判断：本地核对 sourceSha256=对象(63键)；jarSha256="bd4e64573a7339164918ecec6138a33452ffef2a215b2d214044f1d9fb451314"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.6-browser-results.json:231；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.6-browser-results.json:127

## OCR-1360 · 不采纳

- 位置：docs/testing/M10.5.6-browser-results.json:193；OCR原严重度：medium
- 计划：无
- 原意见：计数器缺少可交叉校验的约束：`runtime.opened`(9)、`requests.taskSubmissions`(9)、`requests.total`(306)、`runtime.modelRequests`(14) 是彼此独立的计数，产物中既没有按端点/方法的拆分，也没有记录任何不变量断言（如 total 等于各端点之和、taskSubmissions 等于 POST 任务创建次数、opened 与 taskSubmissions 的差值为 0 的原因）。触发条件：计数钩子漏计（如 fetch/XHR 之外的请求、重定向）或某次提交被重试/丢失时，这些数字仍会各自“自洽”，无法据此发现重复提交或丢失提交。建议补充 `requests.byEndpoint`（method+path→次数）并在生成端对上述不变量做断言，把断言结果写入产物。
- 判断：本地核对 runtime=对象(4键)；requests=对象(2键)。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.6-browser-results.json:193；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.6-browser-results.json:121；docs/testing/M10.5.6-browser-results.json:192

## OCR-1361 · 不采纳

- 位置：docs/testing/M10.5.4-results.json:1301；OCR原严重度：low
- 计划：无
- 原意见：`currentNative.sha256` 缺少可追溯的指向对象，无法核验：该摘要（a0caa5c9…）既不等于本文件 `jarSha256`（22876d60…），也不等于 `M10.5.4-current-native-results.json` 中的 `probeScriptSha256`（9303dc73…），且全仓库搜索仅在本行出现；同级的 `historicalNativeCI` 块带有 `path` 字段来绑定被摘要的记录，而这里只有裸的 `sha256`。作为验收证据，读者/自动化消费方无法判断它指的是当前 native 探测记录、凭据助手二进制还是别的产物，PASS 结论中这部分证据链不可复核。建议补上 `path`（或 `artifact`/`kind`）字段显式声明被哈希的文件与产物类型。
- 判断：本地核对 currentNative=对象(2键)；jarSha256="22876d60e54820dc19e74c5936522f3ba72bffef7fd773c13fe387dfa8b64565"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.4-results.json:1301；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.4-results.json:1260

## OCR-1362 · 不采纳

- 位置：docs/testing/M10.5.6-launcher-results.json:17；OCR原严重度：medium
- 计划：无
- 原意见：哈希覆盖不完整：`sourceSha256` 只锁定 `web-ui.cmd`、`scripts/open-workbench.ps1` 与测试脚本，而启动器在无保存工作区配置时会回退读取 `docs/examples/deepseek-config.json`（决定 endpoint/model/tokenEnv，直接影响 8 项检查的行为），该文件未纳入哈希。触发条件：用同一 JAR 与脚本但不同的回退配置重跑，即可得到不同结论，于是当前 PASS 无法与具体构建/配置一一对应。建议把回退配置及其它启动依赖一并纳入哈希（或记录采集环境与配置哈希）。
- 判断：本地核对 sourceSha256=对象(3键)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.6-launcher-results.json:17；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1363 · 不采纳

- 位置：docs/testing/M10.5.6-launcher-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：`status: PASS` 是结论，但 `checks` 只有自然语言字符串，缺少每项的退出码/耗时/失败输出，也没有 skip 或 failed 的记录。下游消费脚本（如 `scripts/collect-m1059-evidence.py`）只校验 `status=='PASS'` 和 `len(checks)==8`，因此某项检查被跳过、未真正执行或断言被放宽时，本文件依旧呈现 PASS，验收结论不可审计、不可复现。建议把 `checks` 改为 `{name, status, exitCode, durationMs}` 结构（保持长度 8 以免破坏现有消费方），并补充 host/Java/PowerShell 版本等环境字段。
- 判断：本地核对 checks=数组(8项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.6-launcher-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.6-launcher-results.json:4

## OCR-1364 · 部分采纳

- 位置：docs/testing/M10.5.6-history-results.json:97；OCR原严重度：low
- 计划：R0-02 / P3
- 原意见：路径分隔符不可移植：`.build-cache\\M10.5.2-mobile.png` 在 JSON 中保存的是 Windows 反斜杠，非 Windows 主机、CI 或普通文档工具按字面解析时会得到包含反斜杠的单文件名（而不是目录层级），路径解析失败。当前只有 collect-m1056-evidence.py 做了 `replace(chr(92), '/')` 归一化，其他消费方没有保证。提交进 Git 的证据应统一使用 POSIX `/`。
- 判断：本地核对 capturedAt="2026-10-18T02:51:24.142Z"；status="PASS"。相应screenshots路径确使用Windows反斜杠，collector以replace归一化后可以验证。采纳后续生成端统一POSIX相对路径；旧快照由版本兼容读取，不能称当前collector完全不可用。
- 证据：docs/testing/M10.5.6-history-results.json:97；scripts/collect-m1058-evidence.py:55

## OCR-1365 · 不采纳

- 位置：docs/testing/M10.5.6-history-results.json:85；OCR原严重度：medium
- 计划：无
- 原意见：证据路径不可复用/不可复核：本文件是 M10.5.6 的记录，但截图仍指向固定的 `.build-cache/M10.5.2-session-management.png`。该同名文件在后续 M10.5.7（2026-10-18T11:35:17Z）与 M10.5.8（12:22:03Z）运行中被覆盖，而各里程碑记录的 sha256 互不相同（M10.5.2 为 805e12…，本文件为 0b3a46…）。因此本文件里的哈希已不再对应路径上的实际字节，collect-m1056-evidence.py:59 的 “Screenshot drift” 复验会失败，也无法证明该截图属于 M10.5.6 这次运行（触发条件：任一次后续运行覆盖 .build-cache 中同名 png 后重新核对）。建议按里程碑/运行隔离路径（如 `.build-cache/M10.5.6/history-session-management.png`），并在采集时对实际字节做哈希后归档。
- 判断：本地核对 capturedAt="2026-10-18T02:51:24.142Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.6-history-results.json:85；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1366 · 不采纳

- 位置：docs/testing/M10.5.6-history-results.json:102；OCR原严重度：low
- 计划：无
- 原意见：计数含义无法核对：本文件同时记录 `modelRequests: 138` 与 `paidRequests: 0`，但没有任何字段说明这 138 次请求来自本地 Fake/loopback 还是远端提供商端点；同一文件内又有多项 check 声明 “without model calls / without model request”（如注册工作区、fork、重启恢复等）。缺少来源分类与逐项计数锚点，使“0 付费请求”只能自证，无法排除通过看似本地端点实际转发到付费服务的情况（用户规则要求不得调用付费服务）。建议补充请求来源（provider/endpoint 类别）或指向可核对的请求日志。
- 判断：本地核对 capturedAt="2026-10-18T02:51:24.142Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.6-history-results.json:102；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1367 · 不采纳

- 位置：docs/testing/M10.5.6-history-results.json:46；OCR原严重度：low
- 计划：无
- 原意见：安全类断言缺少证据锚点：每条 check 只有 `name` 与 `passed`，没有日志行号、断言 ID 或产物引用；`model cannot escape root or read sibling sentinel`、`no browser exceptions or credential leaks` 这类文件系统边界与凭据脱敏结论无法从该记录复现。collect-m1056-evidence.py:52 也只校验数量与布尔值，因此 PASS 状态无法被第三方审计。建议为安全相关检查记录可追溯的断言/日志引用（或单独的原始输出哈希）。
- 判断：本地核对 checks[0].name="operator registers canonical workspace and selects it without model calls"；checks[0].passed=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.6-history-results.json:46；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.6-history-results.json:7；docs/testing/M10.5.6-history-results.json:8

## OCR-1368 · 不采纳

- 位置：docs/testing/M10.5.6-diagnosis-results.json:58；OCR原严重度：medium
- 计划：无
- 原意见：截图证据不可复核：4 个 `path` 全部指向未入库的 `.build-cache` 构建缓存，且使用 Windows 反斜杠分隔、相对仓库根而非相对本 JSON 文件；文件内也没有 runId / commit / jar 路径等运行标识。任何人检出本仓库后都无法据此校验这 4 个 sha256，也无法确认“窄屏深色不溢出”“向导键盘可达”等视觉结论来自本次 M10.5.6 运行。另外 4 个文件名仍沿用 M10.5.1 前缀（同名 4 个文件在不同里程碑产物中以不同哈希重复出现），无法排除旧截图被复用的可能。建议使用仓库相对的 POSIX 路径并记录 runId/commit，或将截图一并入库。
- 判断：本地核对 screenshots[0].path=".build-cache\\M10.5.1-shell.png"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.6-diagnosis-results.json:58；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29；docs/testing/M10.5.6-diagnosis-results.json:60

## OCR-1369 · 不采纳

- 位置：docs/testing/M10.5.6-diagnosis-results.json:50；OCR原严重度：low
- 计划：无
- 原意见：`checks` 只有 `name`/`passed`，没有观测值、断言文本或失败原因，`status: "PASS"` 完全自证。例如本条“no browser runtime errors”与末尾“fixture closes with zero paid requests”在文件中没有任何对应证据，`requests` 也只记录 path/method 而缺少响应状态码，因此一次返回 4xx/5xx 的审批过期请求同样会被记为通过。建议为每条检查记录关键观测值/响应状态码，失败时保留错误信息，使结论可独立复核。
- 判断：本地核对 checks=数组(13项)；checks[0].name="real ARGV shell validation explains body not started and explicit modes"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.6-diagnosis-results.json:50；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.6-diagnosis-results.json:4；docs/testing/M10.5.6-diagnosis-results.json:6

## OCR-1370 · 不采纳

- 位置：docs/testing/M10.5.6-diagnosis-results.json:80；OCR原严重度：low
- 计划：无
- 原意见：`runtime.opened` 语义不明且与本文件的请求日志无法对账：`requests` 中只有 2 次 `/` 文档加载与 2 次 `/v1/browser-session` 建链，而该计数为 4（恰好等于本文件创建的 4 个任务）。该字段被整套 docs/testing 产物共用，含义不清会让下游校验脚本无法判断计数是否异常。建议改为语义明确的键名（如 `openedTasks` / `sessionsOpened`）并补充单位说明。
- 判断：本地核对 runtime=对象(4键)；requests=数组(201项)。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.6-diagnosis-results.json:80；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.6-diagnosis-results.json:78；docs/testing/M10.5.6-diagnosis-results.json:84

## OCR-1371 · 不采纳

- 位置：docs/testing/M10.5.6-questions-results.json:113；OCR原严重度：medium
- 计划：无
- 原意见：截图位于 .build-cache/ 下，而 .gitignore:8 已忽略该目录，仓库中不存在这些 PNG（按文件名检索无匹配）。因此这里记录的 sha256 无法被校验：证据消费者既拿不到图像，也无法复算哈希，截图类结论（移动端布局、深色主题、键盘可聚焦）实际不可复核。建议把截图归档到受版本控制的证据目录，或在结果中记录可复现的生成命令/运行标识。
- 判断：本地核对 capturedAt="2026-10-18T02:50:07.882Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.6-questions-results.json:113；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1372 · 不采纳

- 位置：docs/testing/M10.5.6-questions-results.json:110；OCR原严重度：medium
- 计划：无
- 原意见：截图路径硬编码为「M10.5.5-」前缀（生成器 frontend/tests/questions.mjs:45 使用 join(out,'M10.5.5-'+name+'.png')），但本文件是 M10.5.6 的证据。同一路径在 docs/testing/M10.5.5-questions-results.json:112-121 记录的是另一组哈希（15045138…/e065278b…/90bdf9cd…），与这里的 dd0b879d…/fed87908…/0103cf86… 互相矛盾：两者不可能同时对应磁盘上同一批 PNG。触发条件：在 M10.5.6 上重跑 questions.mjs 后再对照两个 results 文件，M10.5.5 记录的哈希全部失效，且无法从文件名判断证据属于哪个版本。建议按版本/运行目录隔离截图（参考 M10.5.9 的 .build-cache/M10.5.9/browser/<engine>/questions-*.png 做法），并在生成器与证据文件中同步版本号。
- 判断：本地核对 capturedAt="2026-10-18T02:50:07.882Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.6-questions-results.json:110；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1373 · 不采纳

- 位置：docs/testing/M10.5.6-questions-results.json:124；OCR原严重度：medium
- 计划：无
- 原意见：本文件与当前生成器产出的结构不一致：questions.mjs:102 的 result 对象包含 transportDiagnostics 字段（当前版本必写，M10.5.9-*-questions-results.json:124 也有），此处缺失，说明该证据并非当前生成器直接产出（陈旧或被拼接），但 M10.5.6-results.json:1360 仍将其登记为 26 项检查的正式证据。另外两处指标不可验证：paidRequests 在生成器中恒为字面量 0，从不依据实际流量推导；status 也恒为字面量 'PASS'，脚本未断言 checks.length===26 或校验预期检查名集合，只要删掉某个 check(...) 行仍会产出 PASS，导致 PASS 无法反映漏跑/被裁剪的检查项。建议用当前生成器重新生成证据，并在写文件前断言 26 条检查及其名称集合完整。
- 判断：本地核对 capturedAt="2026-10-18T02:50:07.882Z"；status="PASS"。将当前源码字段/新功能文件集套用到带日期的旧里程碑快照，不能证明旧记录陈旧造假或漏测。M10.5.9新增跨引擎runtime/transportDiagnostics及M10.5.6～.8新增UI/检索文件，旧阶段不应补填这些后来字段或重标通过。
- 证据：docs/testing/M10.5.6-questions-results.json:124；.agent:当前接续状态；docs/testing/M10.5.9-validation.md:26

## OCR-1374 · 不采纳

- 位置：docs/testing/M10.5.6-models-results.json:96；OCR原严重度：medium
- 计划：无
- 原意见：三条截图的 path 都指向 `.build-cache/M10.5.4-*.png`，这份证据无法自证： 1) `.build-cache` 是每次运行 frontend/tests/models.mjs 都会覆盖的临时目录（shot() 中硬编码 `M10.5.4-${name}.png`），且不在版本库中，因此这里记录的 sha256 在仓库内无从复核； 2) 一次重跑就会让同一路径的文件变成另一次运行的截图内容，采集脚本 scripts/collect-m1056-evidence.py:59 的 'Screenshot drift' 断言会直接失败，即本文件与生成它的脚本之间存在时序耦合； 3) 路径里的 M10.5.4 与本文件所属的 M10.5.6 里程碑脱钩，读者无法从记录判断证据对应哪次运行/哪个 UI 构建。 建议把截图落到按里程碑命名的受控目录（例如 docs/testing/M10.5.6-models-*.png）并记录该仓库相对路径。
- 判断：本地核对 status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.6-models-results.json:96；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1375 · 不采纳

- 位置：docs/testing/M10.5.6-models-results.json:109；OCR原严重度：medium
- 计划：无
- 原意见：`paidRequests` 不是观测值：绑定生成脚本 frontend/tests/models.mjs:104 把它硬编码为 0，同一对象中只有 modelRequests 取自 wire.length。因此 `"paidRequests": 0` 与 `"modelRequests": 8` 并列时并未证明这 8 次调用没有打到计费端点——只要 provider 指向真实服务，该字段依然会是 0，并且 scripts/collect-m1056-evidence.py:53 的付费校验也读这个常量，等于自证。建议由实际捕获的请求目标（仅 127.0.0.1 环回）或请求的计费标记推导该值，或明确标注为常量假定而非观测结果。
- 判断：本地核对 paidRequests=0。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.6-models-results.json:109；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1376 · 不采纳

- 位置：docs/testing/M10.5.6-models-results.json:112；OCR原严重度：medium
- 计划：无
- 原意见：记录缺少 `transportDiagnostics` 键，与本文件绑定的生成脚本输出结构不符：frontend/tests/models.mjs:104 在 result 中无条件写入 `transportDiagnostics`（数组，可能为空），且该脚本摘要 81ced6ac… 已被 docs/testing/M10.5.6-results.json:1343 固定、并与 M10.5.4/M10.5.5 记录中的摘要完全一致，说明脚本在此期间未变。由此可复现的结论是：本文件不是该脚本版本的逐字节输出（或采集后被改写），docs/testing/M10.5.6-results.json:1365 登记的 sha256 无法通过重跑复现；同时 models.mjs:72 把被 stop 的 host 产生的 pageerror 分流进 transportDiagnostics 的这段证据在本记录中彻底丢失，读者无法区分“确无浏览器错误”与“错误被过滤”。建议补齐该字段（即使为空数组）并重新采集，使记录与生成器及登记的摘要一致。
- 判断：本地核对 status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.6-models-results.json:112；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1377 · 不采纳

- 位置：docs/testing/M10.5.6-models-results.json:74；OCR原严重度：medium
- 计划：无
- 原意见：该检查项名称与绑定脚本不一致，且放大了断言范围：frontend/tests/models.mjs:98 注册的名称是 'no unexpected browser runtime errors'，因为 models.mjs:72 的 page.on('pageerror') 会先用 stoppedHostDiagnostic 把已停止 host 造成的错误分流到 transportDiagnostics，errors 只统计“非预期”错误。这里改写成 'no browser runtime errors' 后，被过滤掉的那部分错误也被算作已验证；而采集脚本只校验 checks 条数与 passed，不校验名称，这类名称漂移不会被发现。建议改回与脚本一致的表述（并补回 transportDiagnostics 证据）。
- 判断：本地核对 status="PASS"。旧M10.5.6产物不含M10.5.9引入的受控故障诊断分流属于历史版本差异，不应以当前models脚本名称和字段要求改写旧取证文件。
- 证据：docs/testing/M10.5.6-models-results.json:74；docs/testing/M10.5.9-validation.md:26

## OCR-1378 · 不采纳

- 位置：docs/testing/M10.5.6-models-results.json:3；OCR原严重度：low
- 计划：无
- 原意见：键名与仓库内其它验收记录不一致：content/questions/diagnosis/history/turns/browser/development/search 等记录（见 frontend/tests/*.mjs）统一使用 `capturedAt` 承载采集时间，models.mjs 这里用的是 `date`。任何按 `capturedAt` 读取时间戳的通用消费者（例如新鲜度/过期校验）在本文件上会取到 undefined 并静默跳过检查，造成本记录绕过时序校验。建议统一为 `capturedAt`，如确有以下游依赖 `date` 则需同步修改消费方。
- 判断：本地核对 date="2026-10-18T02:50:15.003Z"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.6-models-results.json:3；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1379 · 不采纳

- 位置：docs/testing/M10.5.6-settings-browser-results.json:113；OCR原严重度：medium
- 计划：无
- 原意见：证据链断裂：六张截图的路径全部指向 `.build-cache/`，而仓库 .gitignore:8 已忽略 `.build-cache/`，截图与 sha256 校验值分离提交；在干净克隆上 scripts/collect-m1056-evidence.py:59 的 `sha(ROOT / image['path'] ...)` 会因文件不存在直接失败，任何人都无法复核这些哈希是否对应真实截图。触发条件：在无本地构建缓存的克隆中重跑采集脚本。建议把被引用的截图随文档一起提交到 `docs/testing/`（或明确标注为不可复核的本地中间产物），并保持相对路径可移植（避免仅 Windows 可解析的反斜杠路径）。
- 判断：本地核对 "path": ".build-cache\\M10-settings-plugins.png",       "sha256": "d12b00cd8365cc88df20be69bd589e54fd8636901a433bd8d3fc1ba2e1。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.6-settings-browser-results.json:113；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1380 · 不采纳

- 位置：docs/testing/M10.5.6-settings-browser-results.json:137；OCR原严重度：low
- 计划：无
- 原意见：流量核算不可校验：`modelRequests`/`paidRequests` 仅是本文件的裸数字，没有任何字段声明计数来源（本地 fixture 端点的请求记录）、以及预期下界（本文件 4 个检查依赖真实模型调用）。采集侧 scripts/collect-m1056-evidence.py:53 只断言 `paidRequests == 0`，因此若本地请求被误归类为免费、或某次出站请求未进入 `wire` 计数，归档仍会全部通过，无法满足“不得调用付费服务/不得漏计模型流量”的证据要求。触发条件：计数器漏记或分类错误时本文件依旧 PASS。建议增加 `modelRequestExpectation`/计数器来源说明，并断言 `paidRequests <= modelRequests` 且计数与依赖模型调用的检查数量一致。
- 判断：本地核对 modelRequests=4；paidRequests=0。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.6-settings-browser-results.json:137；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.6-settings-browser-results.json:138

## OCR-1381 · 不采纳

- 位置：docs/testing/M10.5.6-settings-browser-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：证据可复现性缺陷：当前生成器 frontend/tests/settings.mjs:148 产出的检查名是 `no unexpected browser runtime errors`，且生成器会在 result 中写入 `transportDiagnostics` 字段（settings.mjs:149），而本文件既没有该字段、检查名也与生成器不一致，说明这份“全绿”归档不是当前脚本原样输出（应为更早版本产物或被手工编辑）。触发条件：重跑 `node frontend/tests/settings.mjs` 并与本文件逐字段 diff，必然出现 `transportDiagnostics` 缺失与上述名称差异；而 scripts/collect-m1056-evidence.py:52 只校验 `len(checks)==26` 与 `passed` 真值、不比对 name/字段集合，因此该漂移会被静默接受，无法区分真实执行与占位/过期结果。建议在归档中补 `transportDiagnostics`，并让采集脚本同时比对检查名集合与字段清单。
- 判断：本地核对 checks[0].passed=true。将当前源码字段/新功能文件集套用到带日期的旧里程碑快照，不能证明旧记录陈旧造假或漏测。M10.5.9新增跨引擎runtime/transportDiagnostics及M10.5.6～.8新增UI/检索文件，旧阶段不应补填这些后来字段或重标通过。
- 证据：docs/testing/M10.5.6-settings-browser-results.json:1；.agent:当前接续状态；docs/testing/M10.5.9-validation.md:26；docs/testing/M10.5.6-settings-browser-results.json:8

## OCR-1382 · 不采纳

- 位置：docs/testing/M10.5.6-settings-browser-results.json:4；OCR原严重度：low
- 计划：无
- 原意见：浏览器身份不可追溯：这里只记录了版本号，缺少引擎/通道（chromium 或 webkit，由 JHARNESS_BROWSER_CHANNEL 决定），而后续里程碑已区分 chromium/webkit 证据。触发条件：用不同通道重跑同一脚本会得到相同格式但语义不同的归档，无法判断本结果来自哪个引擎，也无法与 M10.5.9 的分引擎结果对齐。建议同时记录 browserType/channel 字段。
- 判断：本地核对 "browser": "155.0.8059.82",   "checks": [     {。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.6-settings-browser-results.json:4；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1383 · 不采纳

- 位置：docs/testing/M10.5.6-turns-results.json:76；OCR原严重度：medium
- 计划：无
- 原意见：路径使用 Windows 反斜杠分隔符，作为证据契约不可移植：POSIX 侧若直接 `Path(root) / path` 拼接，`\.build-cache\...` 会被当成文件名的一部分而报文件不存在；当前唯一消费者 scripts/collect-m1056-evidence.py 只能靠 `image['path'].replace(chr(92), '/')` 兜底。触发条件：在 Linux/macOS 或 CI 容器里校验本文件 → 截图哈希校验失败（误判为证据漂移）。建议统一写成仓库相对 POSIX 路径 `".build-cache/M10.5.6/browser/turns-desktop.png"`。
- 判断：本地核对 capturedAt="2026-10-18T02:50:45.505Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.6-turns-results.json:76；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1384 · 不采纳

- 位置：docs/testing/M10.5.6-turns-results.json:68；OCR原严重度：high
- 计划：无
- 原意见：截图路径仍带 `M10.5.3-` 前缀，与文件名/里程碑（M10.5.6）不一致；更关键的是 M10.5.4、M10.5.5、M10.5.7、M10.5.8 的 turns 证据文件声明的是**同一个** `.build-cache\M10.5.3-turns-desktop.png`，却各自给出不同 sha256。也就是说该路径在每次 turns 运行时都被同名文件覆盖。触发条件：在本轮采集之后又执行任意一轮浏览器 turns 套件（例如复跑 M10.5.7/M10.5.8），磁盘上该文件即被覆盖，本文件记录的 sha256 再也无法复核（用 scripts/collect-m1056-evidence.py 的 `Screenshot drift` 校验必然失败，或更糟：被覆盖后仍被当作本里程碑证据引用）。同样，`.build-cache/` 已被 .gitignore 忽略，干净检出下没有任何可校验的截图副本，PASS 结论不可独立复现。建议按里程碑/引擎分目录（如 M10.5.9 已采用的 `.build-cache/M10.5.9/browser/<engine>/`）并将关键截图归档到 docs/testing 下再用相对路径引用。
- 判断：本地核对 capturedAt="2026-10-18T02:50:45.505Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.6-turns-results.json:68；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1385 · 不采纳

- 位置：docs/testing/M10.5.6-turns-results.json:84；OCR原严重度：medium
- 计划：无
- 原意见：`modelRequests: 15` 与 `paidRequests: 0` 并存但两者都没有来源/单位定义，无法交叉验算：15 次请求是否全部为本地 FakeLlm 宿主请求、是否与“three actual SDK turns”等检查存在对应关系都不明确。触发条件：若某次请求实际命中付费端点而 `paidRequests` 采集逻辑未覆盖该路径（例如只统计宿主侧计费标记），0 会把真实付费调用掩盖掉。建议拆成 `localModelRequests` / `providerModelRequests` 等有明确来源的字段，并在证据中记录计数来源（宿主侧拦截器日志）。
- 判断：本地核对 paidRequests=0。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.6-turns-results.json:84；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.6-turns-results.json:82

## OCR-1386 · 不采纳

- 位置：docs/testing/M10.5.6-turns-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：文件缺少任何自描述标识（milestone / suite / schemaVersion / 原始结果文件名），身份完全依赖文件名。而归档流程恰恰是重命名（collect-m1056-evidence.py 把 `.build-cache/M10.5.3-turns-results.json` 原样复制为 `M10.5.6-turns-results.json`），因此本文件既无法自证属于 M10.5.6，也无法自证是 turns 套件；`capturedAt` 仍是原始运行时刻，与 02:58 的里程碑汇总时间不一致，审计时容易被误读为独立重跑证据。另外 `status` 是与 `checks` 冗余的常量，若 check 被删改而 status 忘记同步，消费方只看 status 会得到错误结论。建议由 checks 派生 status，并补 `milestone`/`suite`/`sourceResultSha256` 字段。
- 判断：本地核对 capturedAt="2026-10-18T02:50:45.505Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.6-turns-results.json:2；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29；docs/testing/M10.5.6-turns-results.json:3

## OCR-1387 · 不采纳

- 位置：docs/testing/M10.5.6-turns-results.json:83；OCR原严重度：medium
- 计划：无
- 原意见：资源生命周期证据不足：`hostStopped` 只是一个布尔常量（M10.5.5/M10.5.7 同样为 true），没有停止时间戳、PID、退出码或“最后一个请求结算时刻”。触发条件：若 15 次 modelRequests 尚有未结算请求时宿主被关闭，该字段依旧记录 true，残留进程/未完成请求会被误判为已清理，事后无法证伪。建议至少补 `hostStopEvidence`（停止时间戳 + 进程退出码 + 已结算请求数），并断言停止时刻晚于最后一次请求完成时刻。
- 判断：本地核对 hostStopped=true。原意见中可见字段本身成立，但没有证明其推测的行为故障；配套源码/阶段记录已限定作用域。仅对旧收据补充元数据或统一风格不作为本轮合理修复任务，保留历史事实字节。
- 证据：docs/testing/M10.5.6-turns-results.json:83；frontend/tests/turns.mjs:132

## OCR-1388 · 不采纳

- 位置：docs/testing/M10.5.6-turns-results.json:50；OCR原严重度：medium
- 计划：无
- 原意见：该检查把两条互不相关的保护合并为一个布尔值：①重启后不重放 active/queued 消息；②UNKNOWN 状态阻止继续。任一条失败都会让整项变红而无法定位，也无法分辨“UNKNOWN 被重放”这一明确禁止的失败模式——名称只断言“UNKNOWN blocks continuing”，并未断言 UNKNOWN 状态不会被重放。触发条件：仅 UNKNOWN 重放回归而重启重放正常（或反之）时，该单值无法给出可复现的失败归因。建议拆成两条独立 check，并显式增加“UNKNOWN 不被重放（无重复提交/无重复模型调用）”的断言。
- 判断：本地核对 capturedAt="2026-10-18T02:50:45.505Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.6-turns-results.json:50；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1389 · 不采纳

- 位置：docs/testing/M10.5.7-browser-results.json:202；OCR原严重度：low
- 计划：无
- 原意见：制品可移植性与留存问题（第 202-232 行）：截图路径使用 Windows 反斜杠分隔符，且指向 .build-cache/（.gitignore 第 8 行已忽略该目录），说明这些截图与日志不随仓库分发。触发条件：在非 Windows 环境或 CI 中按本报告定位证据时，反斜杠路径无法解析；克隆仓库的复核者则完全没有截图实体。建议统一使用 posix 相对路径，并在报告中说明证据制品的归档位置/保留策略，或对关键截图采用可版本化的存放目录。
- 判断：本地核对 capturedAt="2026-10-18T11:36:01.483Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.7-browser-results.json:202；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1390 · 不采纳

- 位置：docs/testing/M10.5.7-browser-results.json:206；OCR原严重度：high
- 计划：无
- 原意见：证据完整性缺陷（第 200-207 行）：M10-connection.png 与 M10-welcome.png 的 sha256 完全相同（5e565086…6b78adc28），但两者是不同 UI 状态（连接页/欢迎页）的截图，字节级不可能一致。触发条件：采集脚本只截取一次、复用同一缓冲或第二个文件被第一个覆盖时，仍会输出两条“独立”证据并通过 PASS。后果是这两项 UI 状态实际上只有一份截图可核对，报告无法证明 28 项检查中的视觉断言被执行过。建议修复脚本并在报告生成时断言 8 张截图 sha256 两两不同，同时记录每张截图对应的检查名。
- 判断：本地核对 capturedAt="2026-10-18T11:36:01.483Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.7-browser-results.json:206；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1391 · 不采纳

- 位置：docs/testing/M10.5.7-browser-results.json:193；OCR原严重度：high
- 计划：无
- 原意见：源码清单不完整（第 193 行附近）：harness-web/src/main/java/io/jharness/web/ 下当前存在 8 个类，而 sourceSha256 只收录了 7 个，缺少 WorkbenchContentSearch.java。PASS 结论声称与“所测源码”绑定，但被测服务器代码集缺一项时，第三方按清单重建的产物哈希不可能与本报告一致，也无法排除该未纳管文件对 diff/内容检索相关检查的影响。建议采集脚本改为按目录 glob 生成清单（禁止手工枚举），并在哈希数与预期文件数不符时使采集失败。
- 判断：本地核对 capturedAt="2026-10-18T11:36:01.483Z"；status="PASS"。将当前源码字段/新功能文件集套用到带日期的旧里程碑快照，不能证明旧记录陈旧造假或漏测。M10.5.9新增跨引擎runtime/transportDiagnostics及M10.5.6～.8新增UI/检索文件，旧阶段不应补填这些后来字段或重标通过。
- 证据：docs/testing/M10.5.7-browser-results.json:193；.agent:当前接续状态；docs/testing/M10.5.9-validation.md:26

## OCR-1392 · 不采纳

- 位置：docs/testing/M10.5.7-browser-results.json:127；OCR原严重度：medium
- 计划：无
- 原意见：版本定位信息缺失（第 127 行）：本文件只有逐文件 sha256，没有 baseCommit / dirty 字段，也没有指向原始运行日志（如 verificationLog）的路径，而同目录其它 M10 结果文件均带有 baseCommit+dirty 与日志字段。触发条件：工作树在采集后被修改或文件被重命名时，仅凭这份清单无法定位实际运行版本，也无法核对 313 次请求与 0 次浏览器异常的原始输出。建议补充 commit、dirty 标记与日志/XML 路径字段。
- 判断：本地核对 capturedAt="2026-10-18T11:36:01.483Z"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.7-browser-results.json:127；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1393 · 不采纳

- 位置：docs/testing/M10.5.7-browser-results.json:93；OCR原严重度：medium
- 计划：无
- 原意见：负向断言缺少可复核的证据字段（第 84-87、92-95 行以及 paidRequests）：token 是否出现在 URL/storage/页面文本/下载报告、secret 是否被脱敏，都是仅凭 passed=true 的存在性断言，报告中没有任何扫描位置清单、命中/替换计数或报告哈希来独立复核；而“token absent … and downloaded report”本身还依赖 M10-report.png/下载报告文件，报告侧也未记录该产物的 sha256。触发条件：扫描被跳过、扫描位置遗漏（例如新增的 localStorage 键或 URL query 参数）时，passed 仍为 true。建议为这类断言补充 evidence 字段（如 scannedSurfaces、redactionHits、reportSha256）。
- 判断：本地核对 capturedAt="2026-10-18T11:36:01.483Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.7-browser-results.json:93；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1394 · 不采纳

- 位置：docs/testing/M10.5.7-browser-results.json:124；OCR原严重度：medium
- 计划：无
- 原意见：计数不可核对（第 121-126、196-197 行）：报告同时声称“duplicate click creates exactly one actual task”“offline recovery never creates a new task”“lost submit response … no POST replay”，但只给出 opened=9 与 taskSubmissions=9，且 opened 的语义未定义（会话页打开次数？已建任务数？）。当提交被重放或某次提交失败被重试时，仍是 9/9，无法据此证明“每次提交恰好产生一个任务且零重放”；313 次总请求也无法拆分。建议增加 postAttempts / replayedPosts / tasksCreated / duplicateSubmissionsRejected 等显式计数字段并定义单位，否则无重放结论只能由脚本自证。
- 判断：本地核对 capturedAt="2026-10-18T11:36:01.483Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.7-browser-results.json:124；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1395 · 不采纳

- 位置：docs/testing/M10.5.7-launcher-results.json:15；OCR原严重度：low
- 计划：无
- 原意见：`paidRequests: 0` 与 `modelEndpoint: "unreachable loopback :9"` 均为验收脚本的自述值，文件中没有记录计量来源（如实际出站请求计数日志、端点解析结果或代理/防火墙证据的引用）。这使"零付费请求"无法独立复核，也无法证明被测启动器在生产配置下不会命中外部模型端点。建议增加可追溯字段（例如计数器快照/日志路径或校验摘要），让该结论可被独立审计。
- 判断：本地核对 capturedAt="2026-10-18T11:34:05.402395+00:00"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.7-launcher-results.json:15；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1396 · 不采纳

- 位置：docs/testing/M10.5.7-launcher-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：JSON 键拼写检查通过（status/capturedAt/checks/browserAndClipboardSideEffects/paidRequests/modelEndpoint/sourceSha256/jarSha256 均拼写正确、无重复键）。但 `checks` 以扁平字符串数组承载 8 项结论，顶层只有一个 `status`，一旦后续出现某项失败，证据文件无法表达"哪个检查失败、失败原因"，只能整份翻转为 FAIL 或继续写 PASS，导致审计/复现时无法区分部分失败与整体成功。建议改为结构化对象数组（如 `{"name": ..., "status": "PASS|FAIL", "detail": ...}`），保留单项结论。
- 判断：本地核对 checks=数组(8项)；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.7-launcher-results.json:4；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.7-launcher-results.json:2

## OCR-1397 · 不采纳

- 位置：docs/testing/M10.5.7-content-results.json:188；OCR原严重度：medium
- 计划：无
- 原意见：证据版本错位：本清单是 M10.5.7，但五张截图路径（第 188/192/196/200/204 行）仍是 `M10.5.6-*.png`，与 `docs/testing/M10.5.6-content-results.json` 中记录的路径完全相同而 sha256 不同。触发条件：在同一个 `.build-cache/` 目录重跑截图脚本。后果是同一路径被覆盖，M10.5.6 清单里的旧哈希在磁盘上已无法复现，也无法从路径判断这份哈希对应哪次构建，证据链不可回溯。建议文件名带上里程碑/构建标识（如 `.build-cache/M10.5.7/M10.5.7-stream.png`），每个里程碑独立目录。
- 判断：本地核对 capturedAt="2026-10-18T11:34:05.304Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.7-content-results.json:188；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1398 · 不采纳

- 位置：docs/testing/M10.5.7-content-results.json:211；OCR原严重度：low
- 计划：无
- 原意见：结论不可复现：顶层 `status: PASS` 只由 `jarSha256` 与浏览器版本背书，没有 commit/build 标识、测试命令或逐项证据引用；`all traffic uses authenticated loopback streaming`、`no secrets in rendered UI or browser persistent storage`、`artifact from another task cannot be fetched by guessing id` 等安全断言仅以布尔值记录，无请求日志、扫描输出或工件引用可独立校验。触发条件：jar 由本机即时构建、代码已继续演进时，无法确认这 44 项对应哪份源码。建议增加 `commit`、`testCommand`（或 `testRunId`）以及关键安全用例的证据文件引用。
- 判断：本地核对 jarSha256="4b717b2e518727d73c0228a18ccf9b5d1a2185908a935c66937fd8d54d7c5ed9"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.7-content-results.json:211；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1399 · 不采纳

- 位置：docs/testing/M10.5.7-content-results.json:50；OCR原严重度：medium
- 计划：无
- 原意见：UNKNOWN 成本的回放覆盖缺口：这个用例只断言展示时刻实际成本为 unknown，没有任何用例覆盖「unknown/未知成本在刷新、重连或宿主重启后不被重放、也不被升级成数值成本」。触发条件：会话重新加载后由缓存/落库值重建 usage，未持久化的未知成本可能被当作 0 或沿用上一轮数值。建议补充一条检查项（例如 `unknown cost stays unknown after refresh and restart`），否则该风险在 PASS 清单里是盲区。
- 判断：本地核对 capturedAt="2026-10-18T11:34:05.304Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.7-content-results.json:50；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1400 · 采纳

- 位置：docs/testing/M10.5.7-content-results.json:74；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：检查项重名导致覆盖结论不可区分：`denied preview private.txt` 在第 74、86、90 行共出现 3 次，而紧邻的 `denied preview config`(第 78 行)、`denied preview .env.local`(第 82 行) 都只有一次。触发条件：多条不同的拒绝场景（例如不同根目录、不同拒绝原因）复用同一用例名。后果是无法判断这 3 条分别验证了什么、失败时也无法定位，重复条目还可能掩盖某个场景实际漏测。建议像其他用例一样在名称中带上场景标识（如 `denied preview private.txt via secondary root`），保证 `checks[].name` 唯一。
- 判断：本地核对 checks=数组(45项)。content.mjs用path.split(/).at(-1)作为断言名，多个不同目录private.txt实际同名。数组未因此丢失用例，但日志无法区分场景；应使用不泄露用户根的fixture相对路径或稳定caseId，并保证必需ID集合唯一。
- 证据：docs/testing/M10.5.7-content-results.json:74；frontend/tests/content.mjs:109；docs/testing/M10.5.7-content-results.json:4

## OCR-1401 · 不采纳

- 位置：docs/testing/M10.5.6-results.json:1398；OCR原严重度：medium
- 计划：无
- 原意见：`currentNative.sha256` 无法复现核对：该哈希在仓库中只出现在本清单里，未说明它覆盖的具体工件事物（native 可执行文件？jar？探针？）。同文件的 `sourceSha256` 中 `NativeCredentials.java`/`SystemCredentials.java` 分别为 a90fbfe…/6ce7224…，与 283e3b8… 都不相同，而被引用的 current-native 证据记录只提供 `probeScriptSha256` 与 `jarSha256`。触发条件：验收者按清单重跑 native 校验时，无法确定应对哪个文件计算 SHA-256，导致 `currentNative` 证据链不可验证。建议补充 `artifact`/`sourcePath` 字段（或直接引用证据记录中的 `probeScriptSha256`/`jarSha256`），明确哈希覆盖对象。
- 判断：本地核对 currentNative=对象(2键)；sourceSha256=对象(295键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.6-results.json:1398；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.6-results.json:1397；docs/testing/M10.5.6-results.json:1005

## OCR-1402 · 不采纳

- 位置：docs/testing/M10.5.6-results.json:1388；OCR原严重度：medium
- 计划：无
- 原意见：`records` 汇总了 9 个验收记录并逐个给出 sha256，但支撑本文件 `currentNative` 块的 `M10.5.6-current-native-results.json` 未被收录，也没有任何摘要覆盖它。触发条件：其他记录被篡改可被 `records` 检出，而 current-native 证据被替换或删改时清单自身无法发现，`status: PASS` 的 native 部分失去可审计性。建议将该证据文件（及其 checks 数）一并加入 `records`，或用 `path`+`sha256` 形式像 `historicalNativeCI` 那样引用它。
- 判断：本地核对 records=对象(9键)；currentNative=对象(2键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.6-results.json:1388；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.6-results.json:1355；docs/testing/M10.5.6-results.json:1397

## OCR-1403 · 不采纳

- 位置：docs/testing/M10.5.7-diagnosis-results.json:60；OCR原严重度：medium
- 计划：无
- 原意见：截图路径不区分里程碑（第 60/64/68/72 行均为 `.build-cache\\M10.5.1-*.png`），每次重跑都会覆盖同名文件，导致该 sha256 在下次采集后即无法复核：例如 M10.5.5-diagnosis-results.json 记录同一路径的哈希为 9d538e8a…，而本文件为 b3de4be3…，说明同一路径下证据已被覆盖，历史 PASS 结论不可再验证（只能依赖采集那一刻的瞬时校验）。此外路径使用 Windows 反斜杠分隔符，POSIX 消费方（jq/脚本直接拼路径）会把它当成含字面反斜杠的文件名而校验失败，必须像 collector 那样先做 `\\`→`/` 归一化。建议按里程碑/引擎分目录并使用正斜杠，使哈希与不可变文件一一对应。
- 判断：本地核对 capturedAt="2026-10-18T11:35:35.997Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.7-diagnosis-results.json:60；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1404 · 不采纳

- 位置：docs/testing/M10.5.7-diagnosis-results.json:38；OCR原严重度：low
- 计划：无
- 原意见：该项断言只有 name/passed，没有 expected/actual、断言表达式或失败细节；collector 仅要求 `c.get('passed')` 为真（scripts/collect-m1057-evidence.py:52），因此被跳过、空跑或断言为空的分支同样会写成 passed=true 并计入“13 项全通过”，审查者无法判断“过期审批显示为拒绝且未进入 body”是否真正执行。建议为每条 check 增加期望值/实际值或失败输出字段（至少在失败态记录 error）。
- 判断：本地核对 capturedAt="2026-10-18T11:35:35.997Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.7-diagnosis-results.json:38；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1405 · 不采纳

- 位置：docs/testing/M10.5.7-diagnosis-results.json:80；OCR原严重度：medium
- 计划：无
- 原意见：runtime.paidRequests=0 / modelRequests=5（第 80-81 行）以及 checks 中“fixture closes with zero paid requests”（第 53-56 行）在本文件内无法被 corroborate：requests 数组的每一项只有 path+method，没有时间戳、HTTP 状态码、上游提供方/凭据指纹或模型目标，因此无法区分 4 次 `POST /v1/tasks` 是否命中真实付费提供方，也无法判断 `POST /v1/tasks/{id}/approvals/{approvalId}` 是返回 4xx（过期拒绝）还是 200（已受理）。同时同一 task 的 display/approvals/questions/turns/events 被反复逐条记录（单 task 十余条 GET，全文件约 200 条），既无时间戳也无去重上限，导致“expiry shown as denied”“refresh never replay”这类依赖顺序的断言无法复核，且轨迹长度随轮询次数无界增长。建议为每条请求补充 ts/status/upstream 字段并对重复轮询做聚合计数（如 `count`），使顺序与零付费结论可复现验证。
- 判断：本地核对 capturedAt="2026-10-18T11:35:35.997Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.7-diagnosis-results.json:80；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1406 · 不采纳

- 位置：docs/testing/M10.5.7-history-results.json:89；OCR原严重度：medium
- 计划：无
- 原意见：路径不可移植且无法在仓库内校验（触发条件：在 Linux/macOS CI 或任何 POSIX 校验脚本中解析这些路径）。路径使用 Windows 反斜杠分隔符，POSIX 上会把反斜杠当成文件名字面字符，导致路径解析失败；同时 .build-cache/ 已被 .gitignore 第 8 行忽略，仓库中并不存在这些图片，因此随证据提交的 sha256 对下游使用者完全不可核验（无原文可算哈希）。建议统一使用 / 作为分隔符，并在证据中记录截图归档位置或将其纳入受控存储后提供可解析引用。
- 判断：本地核对 capturedAt="2026-10-18T11:35:17.742Z"；status="PASS"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.7-history-results.json:89；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1407 · 不采纳

- 位置：docs/testing/M10.5.7-history-results.json:85；OCR原严重度：medium
- 计划：无
- 原意见：证据归属错配（第85行，触发条件：任何人用本文件追溯 M10.5.7 历史/会话截图）。文件名为 M10.5.7-history-results.json，但 4 条截图路径全部以 M10.5.2- 命名；且同一批路径被 M10.5.2/M10.5.3/M10.5.6/M10.5.8-history-results.json 反复引用而 sha256 各不相同（本文件 1ef87e1c… 与 M10.5.6 的 0b3a4608…、M10.5.8 的 61095684…），说明 .build-cache 目录下这些 png 是固定文件名、被后续周期覆盖写。后果：无法判定某张截图由哪一轮运行产生，M10.5.7 的结果会被误归到 M10.5.2；事后按此路径取文件再算哈希，得到的是最后一次运行的内容，必然与本文件记录不符，证据不可复验。建议路径中带周期唯一后缀。
- 判断：本地核对 capturedAt="2026-10-18T11:35:17.742Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.7-history-results.json:85；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1408 · 不采纳

- 位置：docs/testing/M10.5.7-history-results.json:102；OCR原严重度：medium
- 计划：无
- 原意见：请求计数缺乏可复核的来源与源码绑定（触发条件：审计人员复核 modelRequests: 138 / paidRequests: 0 是否成立）。本文件既没有 testedOrigin/browserChannel（同周期 docs/testing/M10.5.7-browser-results.json 第 4-6 行有这两个字段），也没有同级证据里的 sources 文件到 sha256 清单；仅有 jarSha256。因此无法独立判断这 138 次模型请求是否全部发往本机 loopback/替身而非付费端点，也无法把证据绑定到具体源码版本，paidRequests: 0 与 hostStopped: true 只能是被动声明。建议补充 testedOrigin、browserChannel 与源码哈希清单（或提交号），使计数与产物可交叉验证。
- 判断：本地核对 capturedAt="2026-10-18T11:35:17.742Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.7-history-results.json:102；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1409 · 不采纳

- 位置：docs/testing/M10.5.7-models-results.json:96；OCR原严重度：medium
- 计划：无
- 原意见：本报告是 M10.5.7 轮次的结果（jarSha256=4b717b2e…），但截图路径仍沿用 M10.5.4 前缀。该路径在 M10.5.4（5c90f9a8…）、M10.5.6、M10.5.8（4aadbdcc…）等多轮运行中被重复使用，且各轮 sha256 不同，说明每次运行都会覆盖同一文件；一旦 .build-cache 被后续轮次覆盖或清理，本报告记录的 sha256 将无法再对应到任何真实文件，证据链断裂，且仅凭 path 无法判断截图属于哪次构建。建议按里程碑/轮次命名（例如 .build-cache/M10.5.7-models-desktop.png），或参照 M10.5.9 已采用的 .build-cache/M10.5.9/browser/<engine>/ 目录隔离方式。
- 判断：本地核对 status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.7-models-results.json:96；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1410 · 不采纳

- 位置：docs/testing/M10.5.7-models-results.json:110；OCR原严重度：low
- 计划：无
- 原意见：顶层 modelRequests=8 与 checks 中 “configuration check performs no provider request”“settings check is explicitly local and free of model calls” 两条断言无法对账：报告没有给出计数的来源归属或分流，paidRequests=0 只能说明没有产生计费调用，不能证明没有发出模型请求（本地 mock/回环请求同样会增加 modelRequests）。消费方在看到 8 次请求时无法判断是否与上述“无模型调用”断言矛盾。建议将计数拆分为 providerRequests 与 localMockRequests（或为每个 check 记录 requestCount），使计数可与无调用断言逐项校验。
- 判断：本地核对 status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.7-models-results.json:110；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1411 · 不采纳

- 位置：docs/testing/M10.5.7-questions-results.json:113；OCR原严重度：medium
- 计划：无
- 原意见：截图证据不可复现：`.build-cache/` 已被 `.gitignore` 第 8 行忽略，因此这三份 PNG 不会进入版本库，产物只有「路径 + 哈希」而没有图像本体。其他人检出仓库后无法核对 sha256（文件不存在），也无法复核「控件适配移动端视口 / 键盘可聚焦 / 深色模式」等仅靠截图支撑的结论。触发条件：任何非测试执行机的审计或 CI 复跑；后果是被 PERF/PASS 结论缺乏可验证证据。建议将截图或至少其压缩副本纳入受版本控制的目录（如 `docs/testing/artifacts/M10.5.7/`），或在缺图时不要把 PASS 记为最终结论。
- 判断：本地核对 capturedAt="2026-10-18T11:34:24.349Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.7-questions-results.json:113；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1412 · 不采纳

- 位置：docs/testing/M10.5.7-questions-results.json:112；OCR原严重度：high
- 计划：无
- 原意见：证据溯源冲突：本文件是 M10.5.7 的结果，但截图路径仍为 `.build-cache/M10.5.5-questions-*.png`，与 `docs/testing/M10.5.5-questions-results.json` 中记录的路径完全相同，而两处 sha256 不同（如 desktop 分别为 001c1e… 与 150451…）。这说明本次运行覆盖了 M10.5.5 的同名截图文件，导致 M10.5.5 结果文件里的 sha256 已无法校验，同时 M10.5.7 的视觉证据被错误地标注为 M10.5.5 版本。触发条件：在同一 checkout 依次执行 M10.5.5 与 M10.5.7 测试；后果是任一版本的截图哈希校验都会失败，PASS 结论无法对应到具体发布版本。建议按版本命名（如 `.build-cache/M10.5.7-questions-*.png`），并避免不同运行写同一路径。
- 判断：本地核对 capturedAt="2026-10-18T11:34:24.349Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.7-questions-results.json:112；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1413 · 不采纳

- 位置：docs/testing/M10.5.7-questions-results.json:124；OCR原严重度：medium
- 计划：无
- 原意见：计费口径不可核对：`modelRequests` 为 18 而 `paidRequests` 为 0，但产物未记录 provider 模式/端点（本地 loopback mock 还是真实供应商）、每类请求的归属，也没有区分「0 次付费请求」与「未统计」。结合「all provider traffic is authenticated loopback」这一布尔检查，无法证明这 18 次模型请求确实全部走了本地回环而非计费接口。同时产物缺少 commit/revision 与测试框架版本字段，PASS 无法绑定到具体代码状态。建议补充 provider 模式/端点类别、按模式拆分的请求计数，以及 commit id 与用例实现版本。
- 判断：本地核对 modelRequests=18；paidRequests=0。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.7-questions-results.json:124；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.7-questions-results.json:125

## OCR-1414 · 不采纳

- 位置：docs/testing/M10.5.7-questions-results.json:6；OCR原严重度：medium
- 计划：无
- 原意见：全部 26 个检查只记录 `passed: true`，没有任何断言输出、观测值或请求/文件快照引用，其中包含并发（draft 写入串行化）、幂等（双击只提交一次）、恢复（host 重启标记 interrupted 且不重放）、安全（答案脱敏、题历史分页上界、仅本机回环流量）等关键结论。这些结论恰好是 UNKNOWN/重放类缺陷最容易漏检的地方，单布尔值无法区分「断言通过」与「用例被跳过/未执行」。触发条件：实现回退到重放已结算答案或跨会话关联被接受时，只要用例被跳过，本产物仍会是 PASS。建议为每个检查附带可核对的最小证据，例如观测到的状态码/响应体、写入次数、被拒绝的关联 ID 摘要，或跳过/失败原因字段。
- 判断：本地核对 capturedAt="2026-10-18T11:34:24.349Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.7-questions-results.json:6；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1415 · 不采纳

- 位置：docs/testing/M10.5.7-settings-browser-results.json:113；OCR原严重度：medium
- 计划：无
- 原意见：截图路径指向 .build-cache 目录（构建缓存，通常不入版本库），且使用 Windows 反斜杠分隔符。触发条件：在干净检出/CI 或非 Windows 平台校验这些 sha256 时文件不存在、路径不可解析，视觉证据无法复现。建议把证据截图放到受版本管理的目录（如 docs/testing/artifacts/），并使用正斜杠相对路径。
- 判断：本地核对 "path": ".build-cache\\M10-settings-plugins.png",       "sha256": "4b0bc4a49c26e090fb0d9eccb674e746f525ff5997be418b6e3bc654c1。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.7-settings-browser-results.json:113；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1416 · 不采纳

- 位置：docs/testing/M10.5.7-settings-browser-results.json:137；OCR原严重度：medium
- 计划：无
- 原意见：modelRequests=4 与 paidRequests=0 并存，但检查项声称 "configured plugin tools reach actual model request"、"selected permission reaches real SDK" 等“真实模型请求”。触发条件：无法区分这 4 次请求是否全部打向本地 stub/回环端点，还是包含真实付费服务（全部失败/被拦截后计数仍为 0）。建议补充请求目标类别（local stub / 真实端点）、是否为本地回环 base URL 等说明字段，以支撑“未使用付费服务”的结论。
- 判断：本地核对 "modelRequests": 4,   "paidRequests": 0,   "jarSha256": "4b717b2e518727d73c0228a18ccf9b5d1a2185908a935c66937fd8d54d7c5ed9",。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.7-settings-browser-results.json:137；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1417 · 不采纳

- 位置：docs/testing/M10.5.7-settings-browser-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：该结果清单只记录了日期、平台和浏览器版本，没有任何 commit / branch / build 标识，jarSha256 也未与实际代码修订关联。触发条件：后续代码提交后再引用本文件作为测试证据时，无法判断结果是否仍对应当前代码状态，容易把过期结果当作最新证据。建议补充 commit（或 tree hash）、branch、构建产物标识等字段。
- 判断：本地核对 "date": "2026-10-18T11:34:39.629Z",   "platform": "win32",   "browser": "155.0.8059.82",。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.7-settings-browser-results.json:2；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1418 · 不采纳

- 位置：docs/testing/M10.5.7-settings-browser-results.json:7；OCR原严重度：medium
- 计划：无
- 原意见：安全关键检查（鉴权拒绝、跨源拒绝、密钥不入库、权限拦截等）只以布尔 passed 记录，没有断言目标、实际观测值或失败诊断信息。触发条件：测试实现退化为空断言/被 mock 短路时，本文件仍然全绿，无法从证据侧发现。建议为这些检查附带关键断言摘要（如状态码、被拒绝的动作、扫描到的字段名），而非仅 true。
- 判断：本地核对 "name": "settings rejects unauthenticated operator",       "passed": true     },。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.7-settings-browser-results.json:7；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1419 · 不采纳

- 位置：docs/testing/M10.5.7-settings-browser-results.json:140；OCR原严重度：low
- 计划：无
- 原意见：fixtureCredentialsCleaned 是自证式布尔值，没有清理后校验的证据（例如清理后对凭据存储/环境变量的残留扫描结果）。触发条件：清理逻辑提前失败但测试仍打印 true 时，凭据可能残留在系统密钥存储或临时目录。建议记录清理后的验证动作与结果（如残留扫描次数、被清理条目数量），而非仅一个 true。
- 判断：本地核对 "fixtureCredentialsCleaned": true }。fixtureCredentialsCleaned并非未验残留就常量通过：settings finally中启动Cleanup并assert退出0后才写报告，Cleanup删除配置引用targets后逐项vault.read为空，否则抛异常。清理失败不会落该true；原意见忽略实际回读。
- 证据：docs/testing/M10.5.7-settings-browser-results.json:140；frontend/tests/settings.mjs:155；harness-cli/src/test/java/io/jharness/cli/WorkbenchCredentialCleanup.java:28

## OCR-1420 · 不采纳

- 位置：docs/testing/M10.5.7-settings-browser-results.json:4；OCR原严重度：low
- 计划：无
- 原意见：browser 字段只写入版本号 "155.0.8059.82"，缺少浏览器名称/引擎（Chromium、Edge、Chrome 等）。触发条件：当需要按浏览器回归或复现失败时，仅凭版本号无法确定实际浏览器（Chrome 与 Edge 的主版本号可能不同，且版本号本身不可区分产品）。建议拆分为 name + version 两个键，保持与其他字段的 schema 一致性。
- 判断：本地核对 "browser": "155.0.8059.82",   "checks": [     {。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.7-settings-browser-results.json:4；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1421 · 不采纳

- 位置：docs/testing/M10.5.7-extensions-results.json:156；OCR原严重度：low
- 计划：无
- 原意见：截图相对路径使用 Windows 反斜杠分隔符，在 POSIX 采集端或 CI 回放端无法按该字符串解析（会被当作文件名的一部分），哈希校验会直接失败或找不到文件。触发条件：在 Linux/macOS 上执行证据校验脚本。建议统一写成 POSIX 风格相对路径（`.build-cache/M10.5.7-tools-mobile.png`），由使用方在需要时做平台转换。
- 判断：本地核对 capturedAt="2026-10-18T11:33:47.632Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.7-extensions-results.json:156；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1422 · 不采纳

- 位置：docs/testing/M10.5.7-extensions-results.json:148；OCR原严重度：medium
- 计划：无
- 原意见：截图证据文件名是固定的里程碑名（`.build-cache\M10.5.7-*.png`），而不是按运行/内容区分的文件名：后续 M10.5.8 复跑使用了完全相同的四个路径，但记录的 sha256 已变为 `3b11634…`/`1961e14…`/`1dd6284…`/`66ccfce…`（见 M10.5.8-extensions-results.json）。触发条件：在本报告之后再次执行 extensions 浏览器套件（本仓库已有该情况），同名 PNG 被覆盖，本报告第 147-163 行登记的 sha256 将永远无法再与磁盘文件对上，校验脚本只会得到哈希不一致而无法区分“证据被篡改”和“证据被下一次运行覆盖”。同时 `.build-cache/` 在 .gitignore 第 8 行被忽略，这些截图不入库，克隆仓库后完全无法复核。建议按运行 ID 或内容哈希命名截图（如 `M10.5.7-<runId>-forms-desktop.png`），或把截图归档到随仓库提交的目录，并在结果 JSON 中同时记录归档相对路径。
- 判断：本地核对 capturedAt="2026-10-18T11:33:47.632Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.7-extensions-results.json:148；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1423 · 不采纳

- 位置：docs/testing/M10.5.7-extensions-results.json:2；OCR原严重度：low
- 计划：无
- 原意见：顶层 `status` 是静态字面量，字面上没有与 `checks` 数组的聚合结果绑定（本文件中也未记录失败数/总数）。触发条件：任何一次采集写入部分失败（`passed: false`）或人工合并/截断 `checks` 时，报告仍可能保留 `PASS`，而下游/汇总清单通常只读顶层状态，从而把失败结果当成通过。建议由采集脚本按 `checks.every(c => c.passed)` 计算该字段（并记录 `total`/`failed` 计数），而不是硬编码，确保状态与逐项断言始终一致。
- 判断：本地核对 status="PASS"；checks=数组(35项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.7-extensions-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.7-extensions-results.json:4

## OCR-1424 · 不采纳

- 位置：docs/testing/M10.5.8-content-results.json:188；OCR原严重度：medium
- 计划：无
- 原意见：该结果文件名为 M10.5.8 的内容验收产物，但全部 5 张截图路径仍指向 M10.5.6（M10.5.6-stream/artifact/conversation/mobile/dark.png），且文件内没有任何 milestone/commit/jar 构建标识字段把证据绑定到本次运行（jarSha256 只能说明 jar 内容，不能说明截图来源）。触发条件：任何自动化流程按路径前缀或时间戳校验证据新鲜度时，会把这批旧截图当作 10.5.8 的视觉证据引用，导致 PASS 结论无法追溯、复现时无法区分是本次截图还是上一版本残留。建议截图路径按本次 milestone 命名（如 .build-cache/M10.5.8-*.png），并补充 commit/milestone 字段。
- 判断：本地核对 capturedAt="2026-10-18T12:20:55.018Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.8-content-results.json:188；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1425 · 不采纳

- 位置：docs/testing/M10.5.8-content-results.json:208；OCR原严重度：low
- 计划：无
- 原意见：paidRequests=0 与 modelRequests=10 同时存在，但文件缺少 provider/endpoint/模型标识等足以自证“这 10 次请求不是付费产品 API”的字段（仅有 browser 与 jarSha256）。触发条件：审阅者或自动化门禁需要独立验证“零付费请求”时，无法从该产物判断这 10 次请求走的是本地/模拟通道还是计费通道，也无法确认是否使用了无 OAuth 鉴权的产品 API。建议补充 provider、baseUrl、认证方式（如 loopback/local）等非敏感字段，使该计数可核对。
- 判断：本地核对 capturedAt="2026-10-18T12:20:55.018Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.8-content-results.json:208；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1426 · 采纳

- 位置：docs/testing/M10.5.8-content-results.json:82；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：checks 条目只有 name 没有稳定的唯一 id，且这里连续出现同名条目 "denied preview private.txt"（第 74、86、90 行三处完全同名），中间只夹着 config / .env.local。触发条件：任一断言失败时，报告只能按名字定位，三个同名用例无法区分究竟是哪一个受保护路径被拒绝，PASS 计数会被同名条目掩盖（例如某一条被误改成通过另一路径时，结果仍然是“45 项全通过”）。建议给每个 check 增加唯一 id/被访问的目标文件字段，并让重复场景带上具体文件名。
- 判断：本地核对 capturedAt="2026-10-18T12:20:55.018Z"；status="PASS"。content.mjs用path.split(/).at(-1)作为断言名，多个不同目录private.txt实际同名。数组未因此丢失用例，但日志无法区分场景；应使用不泄露用户根的fixture相对路径或稳定caseId，并保证必需ID集合唯一。
- 证据：docs/testing/M10.5.8-content-results.json:82；frontend/tests/content.mjs:109

## OCR-1427 · 不采纳

- 位置：docs/testing/M10.5.7-turns-results.json:68；OCR原严重度：medium
- 计划：无
- 原意见：路径使用 Windows 反斜杠（`\`），本文件在非 Windows 环境不可校验。触发条件：在 Linux/macOS 上执行 `scripts/finalize-workbench-matrix.mjs`（`readFile(join(root, shot.path))`）或 `scripts/run-workbench-matrix.mjs` 读取这些截图时，`join(root, '.build-cache\\M10.5.3-turns-desktop.png')` 会得到含反斜杠的字面文件名，导致 ENOENT，进而把“截图漂移/校验失败”误判为证据缺陷。collect-m1057-evidence.py 自身做了 `replace(chr(92),'/')` 兜底，其他消费者没有，因此同一份证据在不同工具下结论不一致。建议统一写 POSIX 分隔符。
- 判断：本地核对 capturedAt="2026-10-18T11:34:52.477Z"；status="PASS"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.7-turns-results.json:68；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1428 · 不采纳

- 位置：docs/testing/M10.5.7-turns-results.json:66；OCR原严重度：medium
- 计划：无
- 原意见：截图证据的里程碑标识与本文件不一致：本文件是 M10.5.7 的 turns 证据，但三条 path 仍指向 `.build-cache/M10.5.3-turns-*.png`（生成脚本 frontend/tests/turns.mjs 固定使用 M10.5.3 前缀，collector 只改 JSON 文件名不改内容）。触发条件：`scripts/collect-m1057-evidence.py` 将 `.build-cache/M10.5.3-turns-results.json` 拷成 M10.5.7-turns-results.json 时，同名的 PNG 已被本次运行覆盖。后果：docs/testing/M10.5.3-turns-results.json 与本文件记录同一相对路径但 sha256 不同（a7a86a…/8a4eb4…/4d41e3… 对 99abe4…/37d894…/beb855…），旧记录的哈希已无对应文件，无法判断哪一份截图属于哪次运行，证据链在 `.build-cache/` 被覆盖后不可复现。建议截图文件按实际里程碑命名（如 M10.5.7-turns-*.png）后再归档，使哈希与唯一文件一一绑定。
- 判断：本地核对 capturedAt="2026-10-18T11:34:52.477Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.7-turns-results.json:66；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1429 · 不采纳

- 位置：docs/testing/M10.5.7-turns-results.json:2；OCR原严重度：low
- 计划：无
- 原意见：顶层 `status: "PASS"` 是生成脚本写死的字面量，与本文件 `checks` 数组没有任何派生关系，且记录中缺少失败/跳过计数。触发条件：任一 `passed` 被改为 false（或某检查被移除但总数仍为 15，例如用未验证事项替换），文件仍宣称 PASS，读者仅凭本文件无法发现自相矛盾。建议补充由 checks 派生的统计字段（如 passedCount/failedCount/skippedCount），或让 status 在生成时按 `checks.every(c => c.passed)` 计算，使 PASS 可被机械校验。
- 判断：本地核对 checks=数组(15项)；checks[0].passed=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.7-turns-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.7-turns-results.json:4；docs/testing/M10.5.7-turns-results.json:7

## OCR-1430 · 不采纳

- 位置：docs/testing/M10.5.7-turns-results.json:82；OCR原严重度：medium
- 计划：无
- 原意见：`paidRequests: 0` 是生成脚本里的常量（frontend/tests/turns.mjs 写死 `paidRequests:0`），并非从实际请求计数得出，因此该断言不可证伪：即使本次运行真的调用了付费 provider，本字段仍为 0。触发条件：任何回退到真实 provider 的配置泄漏或环境变量误配，本文件依然会显示 0 付费请求，下游 collector 的 `paidRequests == 0` 校验也会通过。建议由运行期计费/端点白名单计数器派生该值，并在记录中附带所用端点标识，避免用常量冒充事实。
- 判断：本地核对 capturedAt="2026-10-18T11:34:52.477Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.7-turns-results.json:82；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1431 · 不采纳

- 位置：docs/testing/M10.5.8-diagnosis-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：截图证据不可复现：`path` 使用 Windows 反斜杠分隔符（JSON 中为转义 `\\`）且指向 `.build-cache`（该目录通常被 gitignore，不在版本库内），在 Linux/macOS 或 CI 上既无法解析路径也拿不到文件；同时文件未记录 source commit SHA、fixture 版本、runner 命令与视口参数，SHA-256 无法与任何可重现输入对上。建议改用仓库相对的 POSIX 路径，并补齐 commit/fixture/命令等溯源字段（`jarSha256` 也缺 JAR 文件名与版本，同样无法定位被校验的产物）。
- 判断：本地核对 screenshots[0].path=".build-cache\\M10.5.1-shell.png"；jarSha256="197ba3803566f8cc8cb4ed3ae0d85b604e0e09055205d0fc57104aca24fd179e"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.8-diagnosis-results.json:1；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29；docs/testing/M10.5.8-diagnosis-results.json:60；docs/testing/M10.5.8-diagnosis-results.json:77

## OCR-1432 · 不采纳

- 位置：docs/testing/M10.5.8-diagnosis-results.json:0；OCR原严重度：high
- 计划：无
- 原意见：成本闸门证据自相矛盾：`modelRequests: 5` 与 `paidRequests: 0` 无法由下方 `requests[]` 佐证。请求日志中所有 `/v1/tasks/.../turns` 均为 GET，`/v1/models` 也是列举型 GET，全文没有任何模型调用型 POST（也无 completions/messages 之类端点），那么这 5 次模型请求要么未被记录，要么计数器并非从该 trace 统计得出。同时日志条目只含 path/method，没有 host、状态码或计费标记，无法判定请求是否落在本地 fixture 而非真实计费后端。这使得 “fixture closes with zero paid requests” 这项断言不可验证、也无法复现，建议在日志中记录每次模型调用的端点、目标主机与 `paid/simulated` 标记，或记录计数器的采样来源。
- 判断：本地核对 requests=数组(198项)。requests记录浏览器到业务API路径，modelRequests由Java宿主fixture计数；浏览器不直接调用provider completions接口，轨迹无模型POST是预期，两个来源不矛盾。
- 证据：docs/testing/M10.5.8-diagnosis-results.json:1；frontend/tests/diagnosis.mjs；harness-testkit/src/test/java/io/jharness/testkit/M10BrowserHost.java；docs/testing/M10.5.8-diagnosis-results.json:84

## OCR-1433 · 不采纳

- 位置：docs/testing/M10.5.8-diagnosis-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：PASS 结论缺少可核验的断言证据。`checks[]` 每项只有 `name` + `passed: true`，没有 expected/actual、断言表达式、错误堆栈或每项耗时/时间戳，也没有汇总字段（总数、失败数、跳过数）。因此顶层 `"status": "PASS"` 与 `checks` 之间不存在可推导关系：任何人改动某一项为 false 而忘记改 status，文件依然“看起来 PASS”。建议为每项补充 `assertion`/`expected`/`actual`/`startedAt` 等字段，并让 `status` 由 checks 聚合生成，否则该工件无法用于回归复现或审计。
- 判断：本地核对 checks=数组(13项)；checks[0].name="real ARGV shell validation explains body not started and explicit modes"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-diagnosis-results.json:1；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.8-diagnosis-results.json:4；docs/testing/M10.5.8-diagnosis-results.json:6

## OCR-1434 · 不采纳

- 位置：docs/testing/M10.5.8-diagnosis-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：日志条目只有 `path` 与 `method`，缺少时间戳、状态码、会话/任务归属和序号，无法验证文件里声明的跨步骤顺序与重放安全断言（“refresh restores diagnosis without task submission”、“cancelled model request retains pending recovery facts”）。具体地，这里至少有一次 `/cancel` POST 被夹在大量轮询 GET 之间，日志无法说明该 cancel 是否返回 200/409、是否被重试、以及后续 GET 是否落在同一会话；同理两次 approvals POST 也无法确认只生效一次。建议在每条记录中补 `at`、`status`、`attempt`（或幂等键）与 `sessionId`，否则审批/取消的幂等性与“UNKNOWN 不被重放”只能靠人工推断。
- 判断：本地核对 screenshots[0].path=".build-cache\\M10.5.1-shell.png"；requests[0].method="GET"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-diagnosis-results.json:1；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.8-diagnosis-results.json:60；docs/testing/M10.5.8-diagnosis-results.json:87

## OCR-1435 · 不采纳

- 位置：docs/testing/M10.5.8-diagnosis-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：该断言声称“fixture closes”，但 `requests[]` 的末尾只有常规 GET 轮询，全文没有任何关闭/释放类请求（如 `DELETE /v1/browser-session`、session close 或等价清理端点），也没有 runtime 侧的关闭记录。资源生命周期缺乏证据，无法判断 fixture、浏览器会话与后台进程是否真正被回收（尤其该文件同时声明过 “partial cleanup and uncertainty” 场景）。建议补充关闭请求与最终 runtime 状态（进程数/会话数）字段来支撑这个 check。
- 判断：本地核对 requests=数组(198项)。fixture由stdin quit和子进程退出关闭，不要求浏览器发送DELETE会话请求。末尾GET不能证明未清理；检查宿主stop/exitCode才是对应证据。
- 证据：docs/testing/M10.5.8-diagnosis-results.json:1；frontend/tests/diagnosis.mjs；docs/testing/M10.5.8-diagnosis-results.json:84

## OCR-1436 · 不采纳

- 位置：docs/testing/M10.5.8-diagnosis-results.json:0；OCR原严重度：low
- 计划：无
- 原意见：`writeExists` 键名含义不明确（是“写操作记录存在”还是“写文件存在”），配合同级的 `opened: 4` 无法判断它对应哪一条写入/打开断言，审计者无法据此确认“零写入”真的成立；建议改为语义明确的键名（如 `writeRecordPresent` / `filesWritten`）并标注取值范围。
- 判断：本地核对 runtime.writeExists=false。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.8-diagnosis-results.json:1；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.8-diagnosis-results.json:79

## OCR-1437 · 部分采纳

- 位置：docs/testing/M10.5.8-diagnosis-results.json:84；OCR原严重度：low
- 计划：R0-02 / P3
- 原意见：`requests[]` 为无上限增长的原始轮询日志：本次已累积 200+ 条，其中 `/display`、`/approvals`、`/questions` 等同路径重复出现十余次，且没有任何 `maxEntries`/`truncated` 标记。长会话或回归重跑时该工件体积会随运行时长线性膨胀（甚至再次超过 500 行的读取/总结上限），既不便于对比也无法判断是否有请求被丢弃。建议在采集侧做去重/按路径聚合，并显式记录条数上限与是否截断。
- 判断：本地核对 requests=数组(198项)。diagnosis将page request逐条追加到requests，确无条数截断说明；但夹具整体有界运行，重复轮询不是产品无限内存已证实。采纳保留有序前后窗口/计数与truncated，不能简单去重丢掉重放证据。
- 证据：docs/testing/M10.5.8-diagnosis-results.json:84；frontend/tests/diagnosis.mjs；scripts/run-workbench-matrix.mjs:19

## OCR-1438 · 不采纳

- 位置：docs/testing/M10.5.8-extensions-results.json:152；OCR原严重度：medium
- 计划：无
- 原意见：证据不可复现：截图路径使用 Windows 反斜杠并指向本地构建缓存目录 `.build-cache\`。触发条件：在 Linux/macOS 或干净检出环境中按该路径校验 sha256，路径无法解析；且该目录通常未入库，证据随环境丢失，无法独立复核。建议改为仓库相对 POSIX 路径，并把证据产物与报告一起归档。
- 判断：本地核对 capturedAt="2026-10-18T12:20:37.357Z"；status="PASS"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.8-extensions-results.json:152；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1439 · 不采纳

- 位置：docs/testing/M10.5.8-extensions-results.json:148；OCR原严重度：medium
- 计划：无
- 原意见：证据可追溯性缺陷：本文件是 M10.5.8 的验收结果，但 4 张截图全部仍标注为 M10.5.7。触发条件：审阅者或 CI 依据本文件核对 M10.5.8 验收；此时无法证明这些截图属于本次运行，顶层 PASS 缺少可追溯的视觉证据（且与 sha256 绑定的是旧产物）。建议重新生成本里程碑截图并同步更新 sha256。
- 判断：本地核对 capturedAt="2026-10-18T12:20:37.357Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.8-extensions-results.json:148；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1440 · 不采纳

- 位置：docs/testing/M10.5.8-extensions-results.json:2；OCR原严重度：low
- 计划：无
- 原意见：schema 完整性不足：该文件作为机器消费的证据契约，没有 schemaVersion，也没有由 checks 推导的汇总字段（total/passed/failed），`status` 是独立写死的字符串。触发条件：checks 数组被截断、或某条 check 遗漏 `passed` 字段时，下游解析仍会读到 PASS。建议增加 schemaVersion 与汇总字段，并在生成端校验“35 条 check 全部 passed”后才写 PASS。
- 判断：本地核对 status="PASS"；checks[0].passed=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-extensions-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.8-extensions-results.json:7

## OCR-1441 · 不采纳

- 位置：docs/testing/M10.5.8-extensions-results.json:168；OCR原严重度：medium
- 计划：无
- 原意见：聚合计数与逐条断言无法对齐：`modelRequests: 4` 未与任何 check 关联，而 checks 中多条明确断言“不发起模型请求”（如 configuration editing does not connect MCP or request model、frontmatter snapshot is viewable without model request、disconnected assembly keeps uncertainty without model request），另有多条要求发起真实模型请求。触发条件：某条“零请求”断言回归（例如快照查看时多发一次模型请求）。结果：总数仍可能是 4，顶层 PASS 无法暴露该回归。建议为每条 check 记录其 modelRequests/paidRequests 增量与观测值。
- 判断：本地核对 capturedAt="2026-10-18T12:20:37.357Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.8-extensions-results.json:168；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1442 · 不采纳

- 位置：docs/testing/M10.5.8-extensions-results.json:90；OCR原严重度：medium
- 计划：无
- 原意见：高风险安全断言缺少观测证据：脱敏、路径穿越拒绝、只读子代理、缺凭据拒绝、外部工具不重放等断言只记录 name/passed。触发条件：脱敏逻辑被绕过（事件文本或 MCP resource 中的 secret 原样透出），该 check 仍会被判定 true 而产生误报。建议为这类断言增加 evidence 字段（如 redactedFields 字段名列表、命中计数、被拒绝的根路径类别），无需输出凭据原文。
- 判断：本地核对 capturedAt="2026-10-18T12:20:37.357Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-extensions-results.json:90；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1443 · 不采纳

- 位置：docs/testing/M10.5.8-extensions-results.json:173；OCR原严重度：medium
- 计划：无
- 原意见：协议序列语义不足：`resources/list` 连续出现两次，`resources/read` 同样出现两次，但此处只有扁平方法名。触发条件：客户端因重试/重复订阅而重复发起同一请求时，审阅者无法与“分页导致的合法第二次列举”区分，check “MCP resource pagination and JSON SSE reads use real wire” 的通过无法由该序列证实。建议为每次调用记录时间戳、cursor 与返回条数，以证明第二次列举确为分页而非重复请求。
- 判断：本地核对 capturedAt="2026-10-18T12:20:37.357Z"；status="PASS"。原意见中可见字段本身成立，但没有证明其推测的行为故障；配套源码/阶段记录已限定作用域。仅对旧收据补充元数据或统一风格不作为本轮合理修复任务，保留历史事实字节。
- 证据：docs/testing/M10.5.8-extensions-results.json:173；frontend/tests/extensions.mjs

## OCR-1444 · 不采纳

- 位置：docs/testing/M10.5.8-extensions-results.json:178；OCR原严重度：medium
- 计划：无
- 原意见：证据与断言规模不匹配：整轮运行只记录了一次 `tools/call`，但 checks 中 “project user skill resource MCP resource and template calls actually complete”“approved script executes in configured workspace”“registered extension tools reach real SDK model request”等断言涉及多次外部工具/资源调用。触发条件：这些步骤部分未真正执行，或被方法名去重后统计。结果：仅凭一次调用无法支撑上述全部 passed。建议记录每次 tools/call 的工具名（脱敏后的参数摘要）及其归属的 check。
- 判断：本地核对 capturedAt="2026-10-18T12:20:37.357Z"；status="PASS"。tools/call是MCP peer方法，skill/script/read-only子任务不全走MCP tools/call；多能力通过断言与一次MCP调用没有必然数量对应关系。
- 证据：docs/testing/M10.5.8-extensions-results.json:178；frontend/tests/extensions.mjs；docs/testing/M10.5.8-extensions-results.json:mcpMethods

## OCR-1445 · 不采纳

- 位置：docs/testing/M10.5.8-browser-results.json:209；OCR原严重度：medium
- 计划：无
- 原意见：screenshots 中的路径使用 Windows 反斜杠且位于 .build-cache（构建产物目录，通常不入库）。在 Linux/macOS CI 或跨平台工具链上 ".build-cache\\M10-connection.png" 无法解析为有效路径；同时产物目录随构建被清理后，report 里的 sha256 就没有可校验对象，报告不可复现。建议改用仓库相对的正斜杠路径，并把截图归档到与报告同级的版本化目录（如 docs/testing/assets/M10.5.8/）。
- 判断：本地核对 capturedAt="2026-10-18T12:22:44.769Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.8-browser-results.json:209；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1446 · 不采纳

- 位置：docs/testing/M10.5.8-browser-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：顶层只有自述的 "PASS"，缺少聚合信息：没有失败数/总数、执行耗时、未执行（skipped）计数，也没有仓库提交 SHA 与文件版本标识（文件名是 M10.5.8，正文却无版本/commit 字段）。因此这份报告无法被绑定到具体构建，只需把 status 改成 PASS 就能通过门禁。建议补充 version/commit、checksTotal/checksFailed/checksSkipped，并让 status 由这些计数推导。
- 判断：本地核对 capturedAt="2026-10-18T12:22:44.769Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-browser-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1447 · 不采纳

- 位置：docs/testing/M10.5.8-browser-results.json:204；OCR原严重度：high
- 计划：无
- 原意见：重复的截图摘要：M10-connection.png 与 M10-welcome.png 的 sha256 完全相同（cc84a4...7123），说明这两张截图不是两次独立的真实采集，至少有一张是陈旧文件或标签错配。结果是这两个 UI 状态没有独立的视觉证据，相关 check 的 passed=true 无法复核。建议重新采集两张截图，并在 screenshots 条目中补充对应 check 名称与采集时间，采集后校验摘要互不相同。
- 判断：本地核对 capturedAt="2026-10-18T12:22:44.769Z"；status="PASS"。相同内容会有相同摘要，不能反推未执行或复制。浏览器自动同源进入使connection/welcome可已是同一状态；夹具日志仅打印确定性的PASS名称，不同引擎/复跑可逐字相同。应核对执行入口和制品来源，不能要求截图/日志哈希强制互异。
- 证据：docs/testing/M10.5.8-browser-results.json:204；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1448 · 不采纳

- 位置：docs/testing/M10.5.8-browser-results.json:92；OCR原严重度：high
- 计划：无
- 原意见：每个 check 只有 name + passed，没有 expected/actual、请求 id、事件 id 或截图引用，安全类断言（token 不出现在 URL/storage/页面文本/报告、configured secret 脱敏、model HTML 按文本渲染）与功能类断言形式上完全一样。跳过、空断言或只检查了空集合的用例都会显示为 true，无法区分真实通过。建议为每个 check 增加 evidence 字段（如请求序号、事件 id、断言摘要、截图 path+sha256），并对安全类 check 记录被检查的 canary 值出现次数。
- 判断：本地核对 capturedAt="2026-10-18T12:22:44.769Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-browser-results.json:92；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1449 · 不采纳

- 位置：docs/testing/M10.5.8-browser-results.json:121；OCR原严重度：medium
- 计划：无
- 原意见：writeExists 只有一个布尔值，没有 approval 写入文件的路径、大小、sha256，也没有任务 id 列表；同时 requests.taskSubmissions=9 无法与 “duplicate click creates exactly one actual task”“refresh … never resubmits task”“offline recovery never creates a new task” 这几条逐条对应。也就是说“重复点击只产生一个任务”“只有一次真实 POST”这两个结论在报告里不可核验（例如 9 次提交是否包含一次意外的重复提交无法判断）。建议记录 approval 文件路径/大小/sha256，以及每次提交的 task id 与对应 check 名称。
- 判断：本地核对 capturedAt="2026-10-18T12:22:44.769Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.8-browser-results.json:121；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1450 · 不采纳

- 位置：docs/testing/M10.5.8-models-results.json:96；OCR原严重度：medium
- 计划：无
- 原意见：报告文件名与检查项均为 M10.5.8，但三张截图产物路径仍指向旧里程碑 M10.5.4，且文件内没有记录本轮运行的 commit / jar 版本，无法确认 sha256 对应的是本轮产物。这样截图作为验收证据不可复现（很可能引用了过期快照）。建议产物内记录里程碑标识与运行时 commit，并让截图路径携带本轮标识（如 M10.5.8-models-desktop.png）后再重新计算哈希。
- 判断：本地核对 status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.8-models-results.json:96；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1451 · 不采纳

- 位置：docs/testing/M10.5.8-models-results.json:109；OCR原严重度：medium
- 计划：无
- 原意见：计数字段缺少口径定义：modelRequests=8 与 "configuration check performs no provider request"、"settings check is explicitly local and free of model calls" 等检查并存，报告无法说明这 8 次是发往本地 mock 还是真实服务商；paidRequests=0 同样只是自报值。若统计口径只覆盖“计费响应”或只覆盖某个客户端路径，真实出网流量会被掩盖为 0。建议拆分为 localRequests / providerRequests（并注明请求归集点），并对每条有出网可能的检查记录实际出网次数。
- 判断：本地核对 status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.8-models-results.json:109；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1452 · 不采纳

- 位置：docs/testing/M10.5.8-models-results.json:5；OCR原严重度：high
- 计划：无
- 原意见：每条检查只有布尔 passed，schema 中没有 skip / error / not-executed 状态，也没有断言证据字段。结果是：(1) 未执行或被跳过的检查同样会被汇总成 PASS 且无法区分；(2) 安全类断言（如 "all protocol calls authenticate only through headers"、"saved settings and session events contain no API key"）仅凭 true 无法复核，无法证明测试真的检查了保存设置、会话事件与请求头。建议改为 status: pass|fail|skip|error，并附脱敏后的 evidence 摘要（例如命中的 header 名列表、被扫描的持久化文件清单），汇总状态由各条 status 推导而非硬编码。
- 判断：本地核对 status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-models-results.json:5；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1453 · 不采纳

- 位置：docs/testing/M10.5.8-models-results.json:112；OCR原严重度：low
- 计划：无
- 原意见：hostStopped 只有顶层布尔值，未记录 teardown 耗时、进程退出码，也没有失败路径下的清理结论。当某条检查抛错或 CI 中断时，报告无法证明 host 仍被确定性终止（可能残留进程/占用端口）。建议记录 teardown 开始时间与耗时、退出码，并说明关闭逻辑位于 finally/on-failure 中，确保即使检查失败也会执行。
- 判断：本地核对 status="PASS"。原意见中可见字段本身成立，但没有证明其推测的行为故障；配套源码/阶段记录已限定作用域。仅对旧收据补充元数据或统一风格不作为本轮合理修复任务，保留历史事实字节。
- 证据：docs/testing/M10.5.8-models-results.json:112；frontend/tests/turns.mjs:132

## OCR-1454 · 不采纳

- 位置：docs/testing/M10.5.8-history-results.json:89；OCR原严重度：medium
- 计划：无
- 原意见：截图路径使用 Windows 反斜杠分隔符并指向 .build-cache，在 POSIX CI 上无法直接解析；且 .build-cache 属于可清理/通常被 .gitignore 忽略的构建缓存，缓存一旦清理，记录的 sha256 就无从校验。建议改用仓库相对的 POSIX 路径，并把证据落到受版本控制的目录（如 docs/testing/artifacts/），保证跨平台可解析、可复核。
- 判断：本地核对 capturedAt="2026-10-18T12:22:03.437Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.8-history-results.json:89；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1455 · 不采纳

- 位置：docs/testing/M10.5.8-history-results.json:85；OCR原严重度：high
- 计划：无
- 原意见：证据归属不一致：文件名是 M10.5.8-history-results.json，但四张截图全部命名为 M10.5.2-*。这无法证明截图来自本次 M10.5.8 运行，存在复用旧产物导致 PASS 被错误归因的风险。建议按当前里程碑命名截图，或在顶层增加 milestone 字段并与截图目录/文件名保持一致，必要时记录每张截图对应的捕获时间。
- 判断：本地核对 capturedAt="2026-10-18T12:22:03.437Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.8-history-results.json:85；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1456 · 不采纳

- 位置：docs/testing/M10.5.8-history-results.json:3；OCR原严重度：medium
- 计划：无
- 原意见：缺少运行溯源信息：没有源码 commit SHA、runner/OS 镜像、harness（或插件）版本，也没有实际执行命令。仅凭 capturedAt 无法把这次 PASS 绑定到具体构建与环境，复现性不足。建议增加 commit、runner、harnessVersion、command 等字段。
- 判断：本地核对 capturedAt="2026-10-18T12:22:03.437Z"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.8-history-results.json:3；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1457 · 不采纳

- 位置：docs/testing/M10.5.8-history-results.json:105；OCR原严重度：low
- 计划：无
- 原意见：只记录了 jar 的 sha256，却没有记录被校验 jar 的文件名、版本或来源路径，无法定位该哈希对应哪个产物，校验结果不可复核。建议补充 jarPath/jarVersion（或构建号）字段。
- 判断：本地核对 capturedAt="2026-10-18T12:22:03.437Z"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.8-history-results.json:105；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1458 · 不采纳

- 位置：docs/testing/M10.5.8-history-results.json:79；OCR原严重度：medium
- 计划：无
- 原意见：安全断言仅记录一个布尔值，没有记录检测方式（console/network 监听、页面文本扫描等）、命中计数或脱敏后的日志片段，无法自证“无凭据泄漏”。建议记录脱敏后的异常/凭据命中摘要（如 consoleErrorCount、credentialMatchCount 及已脱敏样本），既能复核又不泄露凭据值。
- 判断：本地核对 capturedAt="2026-10-18T12:22:03.437Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-history-results.json:79；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1459 · 不采纳

- 位置：docs/testing/M10.5.8-history-results.json:6；OCR原严重度：medium
- 计划：无
- 原意见：所有用例只有 name/passed 两个字段，缺少稳定的用例 ID、断言明细、执行耗时/时间戳、总用例数与失败数，也没有失败时的日志/产物引用。这样 PASS 只是自证布尔值，无法区分真实执行与人工填写，也无法在回归时定位是哪条断言失败。建议为每条检查补充 id、durationMs、assertions（或 failureArtifact）字段，并在顶层记录 total/failed 计数，便于下游解析与校验。
- 判断：本地核对 capturedAt="2026-10-18T12:22:03.437Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-history-results.json:6；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1460 · 采纳

- 位置：docs/testing/M10.5.8-launcher-results.json:7；OCR原严重度：low
- 计划：R6-03 / P2
- 原意见：该条断言“leaaves no listening port / 不残留监听端口”强于采集脚本实际观测到的内容:运行结束时仅通过 HTTP 请求失败(连接被拒)判断服务已退出,没有任何监听端口清单或进程快照被记录。触发条件:服务端口仍被其他进程/延迟关闭的套接字占用,或 HTTP 探测因超时而非端口关闭而失败时,本文件同样会记录此条为已通过,断言不可复现。建议要么记录退出后实际的监听端口/PID 证据,要么把措辞限定为“退出后 HTTP 不可达”。
- 判断：本地核对 capturedAt="2026-10-18T12:22:57.007227+00:00"；status="PASS"。stop先等待启动器退出但端口只靠HTTP失败判断，URLError/TimeoutError均视为通过；存活但无响应的Java监听可能漏检。需核对所拥有Java进程终止与端口可重绑，不能杀无关进程。
- 证据：docs/testing/M10.5.8-launcher-results.json:7；scripts/test-open-workbench.py:62

## OCR-1461 · 不采纳

- 位置：docs/testing/M10.5.8-launcher-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：该证据文件的 `checks` 仅为人类可读的描述字符串,逐项没有 `result`/`evidence`(日志路径、断言输出、端口或 PID 快照)与之绑定,顶层 `"status": "PASS"`(第 2 行)与这 8 条描述之间不存在可校验的映射关系。触发条件:任一子检查实际只做了弱断言(例如“token 未泄露”“不得覆盖受保护环境变量”这类否定性断言没有捕获任何输出)时,外部审计者无法仅凭本文件判断该项是否真正通过,任何一次不完整运行都可以按同样格式落盘并被当作全部通过。建议把 `checks` 改为对象数组,逐项记录 id、结果与证据引用(日志/断言片段),使 PASS 可由记录本身推导。
- 判断：本地核对 checks=数组(8项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-launcher-results.json:4；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1462 · 不采纳

- 位置：docs/testing/M10.5.8-sdk-results.json:2；OCR原严重度：high
- 计划：无
- 原意见：顶层 `status: PASS` 是手写常量，而不是由执行结果推导出来的：`results[]` 中每个语言只有一个聚合布尔 `passed`，`checks` 仅是名称数组，既没有逐项状态（pass/fail/skip/error），也没有失败原因、耗时和执行计数。触发条件：任一检查被跳过、进程崩溃或断言未真正运行，只要生成脚本仍写入 `passed: true`，本快照依旧输出 `PASS`，把未执行的检查伪装成通过。建议改为逐项结果对象（含 id/status/error），并由这些结果聚合出语言的 `passed` 与顶层 `status`，而不是人工声明。
- 判断：本地核对 results=数组(2项)；results[0].passed=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-sdk-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.8-sdk-results.json:6；docs/testing/M10.5.8-sdk-results.json:40

## OCR-1463 · 不采纳

- 位置：docs/testing/M10.5.8-sdk-results.json:4；OCR原严重度：high
- 计划：无
- 原意见：请求计数语义未定义且自相矛盾：`paidRequests: 0`、`requests: 12`、`allAuthorized: true` 之间没有任何关系约束，也没有拒绝/未授权请求计数。触发条件：某次运行中本地/未付费端点被调用 12 次，或其中一次走了付费端点但计数未更新，仍可同时写出 `paidRequests: 0` 与 `allAuthorized: true`，从而无法审计“禁止付费调用/无 OAuth 产品 API 鉴权”的约束。建议明确计数器作用域（paid/local/rejected/unauthorized），并断言 `requests == paidRequests + localRequests` 且 `allAuthorized` 由鉴权记录推导。
- 判断：本地核对 allAuthorized=true。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.8-sdk-results.json:4；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.8-sdk-results.json:79

## OCR-1464 · 不采纳

- 位置：docs/testing/M10.5.8-sdk-results.json:3；OCR原严重度：high
- 计划：无
- 原意见：快照缺少可复现所需的绑定信息：没有 commitSHA/构建号、harness 版本、执行命令与环境、Node/Python 运行时版本、以及 SDK 版本；`jarSha256` 也没有对应制品路径/版本。触发条件：源码或 JAR 变更后重跑，仍会得到结构相同的 `PASS` 文件，仅凭一个裸哈希无法判断它对应哪个提交、哪份构建产物，也无法复现失败。建议增加 `commitSha`、`harnessVersion`、`command`、`runtime`（语言及版本）、`jarPath`/`jarVersion` 等字段，使结果与被测源码一一对应。
- 判断：本地核对 jarSha256="197ba3803566f8cc8cb4ed3ae0d85b604e0e09055205d0fc57104aca24fd179e"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-sdk-results.json:3；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.8-sdk-results.json:5

## OCR-1465 · 不采纳

- 位置：docs/testing/M10.5.8-sdk-results.json:80；OCR原严重度：medium
- 计划：无
- 原意见：安全类结论只有标签、没有可验证证据：`modelContextRedacted`、`allAuthorized` 为顶层布尔，与具体检查项无关联；`key never enters argv`、`report and event content exclude secrets`、`child diagnostic text never appears in API result`、`artifact reads real scoped redacted content` 等都只是字符串。触发条件：argv/环境变量白名单或脱敏实现回归后，生成脚本仍会把同名检查标记为通过，审计方无法从快照证明密钥未进入 argv、脱敏确实发生。建议为这些检查附加实测证据（如 argv 快照哈希、脱敏前后字段清单、子进程环境变量白名单断言结果），并让顶层安全标志由这些证据推导。
- 判断：本地核对 modelContextRedacted=true；allAuthorized=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-sdk-results.json:80；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.8-sdk-results.json:79

## OCR-1466 · 不采纳

- 位置：docs/testing/M10.5.8-sdk-results.json:8；OCR原严重度：medium
- 计划：无
- 原意见：`checks` 是自由文本数组且仅有语言级聚合 `passed`，没有稳定 id 与逐项状态；TypeScript 与 Python 两份列表逐字相同（行 9-38 与 44-73），无法区分是两次真实运行还是复制模板。触发条件：Python 端某个检查未实现/被跳过，或检查名被改名，快照中仍会显示 29 项全通过，静默丢失覆盖率；也无法在不同运行间做 diff。建议每项使用 `{ "id": "unknown_no_replay", "status": "passed" }` 形式，并记录该语言实际执行的检查数与跳过数。
- 判断：本地核对 results[0].checks=数组(29项)；results[0].passed=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-sdk-results.json:8；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.8-sdk-results.json:9；docs/testing/M10.5.8-sdk-results.json:40

## OCR-1467 · 不采纳

- 位置：docs/testing/M10.5.8-turns-results.json:2；OCR原严重度：low
- 计划：无
- 原意见：`status` 为手工写入的固定值，与 `checks` 数组没有绑定关系；即使把某条 `passed` 改为 false，本行仍会输出 PASS。建议在生成器里按 `checks.every(passed)` 推导该字段（并加入失败计数），以保证结果文件自洽。
- 判断：本地核对 status="PASS"；checks=数组(15项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-turns-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.8-turns-results.json:4

## OCR-1468 · 不采纳

- 位置：docs/testing/M10.5.8-turns-results.json:6；OCR原严重度：medium
- 计划：无
- 原意见：检查项只有布尔值，缺少可追溯字段（时间戳、日志/用例 ID、失败原因、跳过状态）。这样 `passed:false`、被跳过、以及从未执行的检查在结构上完全无法区分，顶层的 `"status": "PASS"` 也就无法从文件内容自证。建议为每项补充 `durationMs`/`logRef`（或失败时的 `error`/`skipped` 字段），并让 `status` 由 checks 聚合计算而非手写常量，避免单点真值不一致。
- 判断：本地核对 status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-turns-results.json:6；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.8-turns-results.json:2

## OCR-1469 · 不采纳

- 位置：docs/testing/M10.5.8-turns-results.json:69；OCR原严重度：medium
- 计划：无
- 原意见：哈希不可复现：截图路径是 Windows 反斜杠绝对构建缓存路径（`.build-cache\\...`），在非 Windows 机器、CI 或干净检出中该文件不存在，且 `.build-cache` 通常不入版本库，因此这里的 sha256 无法被下游校验，"PASS" 结论不可独立复核。建议将截图作为可下载产物归档（或使用 POSIX 风格、相对仓库根、已提交的路径），并在 artifact 中记录文件大小/提交号以便交叉验证。
- 判断：本地核对 capturedAt="2026-10-18T12:21:40.396Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.8-turns-results.json:69；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1470 · 不采纳

- 位置：docs/testing/M10.5.8-turns-results.json:68；OCR原严重度：medium
- 计划：无
- 原意见：证据溯源不一致：M10.5.8 的结果文件中，三张截图仍命名为 `M10.5.3-turns-*.png`。若这些文件未被重命名/重建，则文件中的 sha256 只能证明 M10.5.3 那次运行，无法证明本文件中 15 条 M10.5.8 检查项对应的 UI 状态；若确为本次复制的旧文件，则本轮移动端/暗色主题检查实际缺少可见证据。建议改为 M10.5.8 对应的文件名，并在生成流程中断言文件名与里程碑号一致。
- 判断：本地核对 capturedAt="2026-10-18T12:21:40.396Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.8-turns-results.json:68；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1471 · 不采纳

- 位置：docs/testing/M10.5.8-turns-results.json:84；OCR原严重度：medium
- 计划：无
- 原意见：计数器与检查项口径不一致，无法相互印证：检查项声明“three actual SDK turns share original session and distinct loop turn identities”，但此处 `modelRequests` 为 15，同时 `paidRequests` 为 0。若一次“turn”包含多次模型请求，应在 artifact 中显式记录口径（请求/轮次/session 的映射）与 `paidRequests=0` 的判定依据（如全部命中本地/回放模型），否则无法排除计费请求被漏计，也无法用该数字验证“三次 turn”。
- 判断：本地核对 modelRequests=15；paidRequests=0。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.8-turns-results.json:84；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.8-turns-results.json:82

## OCR-1472 · 不采纳

- 位置：docs/testing/M10.5.8-turns-results.json:62；OCR原严重度：medium
- 计划：无
- 原意见：安全断言范围未定义：这是一个无参数的布尔项，未说明扫描了哪些存储（localStorage/sessionStorage/IndexedDB/cookie）、使用什么密钥词表/正则、以及是否覆盖 draft 与 continuation 的持久化路径。由于同批次另有一项“unsent draft ... never enters browser storage”，两条结论相互依赖却都没有给出检测方法与命中数，部分泄漏仍可能被记为通过。建议在生成流程中记录被测键清单与匹配规则（不记录具体凭据值），并把扫描结果（键名、命中计数）写入 artifact。
- 判断：本地核对 capturedAt="2026-10-18T12:21:40.396Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-turns-results.json:62；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1473 · 不采纳

- 位置：docs/testing/M10.5.9-browser-availability.json:11；OCR原严重度：medium
- 计划：无
- 原意见：该日志路径使用 Windows 反斜杠，且指向被 .gitignore 忽略的 `.dsh-tmp/` 临时目录。触发条件：在 Linux/macOS 或 CI 上复核时 `Path('.dsh-tmp\m1059-...')` 会把整个字符串当作单个文件名，路径不可解析；同时仓库内其他证据（如 M10.5.9-results.json 第 2018 行的哈希清单键）统一使用正斜杠，同一文件出现两种写法导致按清单反查 logSha256 时匹配失败。已确认仓库中不存在该日志文件，因此第 12 行记录的摘要无法被任何第三方重建或校验，摘要只能证明“当时存在过某份日志”，不能发现后续篡改/截断。建议统一为仓库相对 POSIX 路径，并把日志本身（或带 POSIX 键的哈希清单条目）纳入可提交的证据目录。
- 判断：本地核对 capturedAt="2026-10-19T08:18:18.392206+00:00"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.9-browser-availability.json:11；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1474 · 不采纳

- 位置：docs/testing/M10.5.9-browser-availability.json:6；OCR原严重度：medium
- 计划：无
- 原意见：键名拼写与全文件/同批证据的命名约定不一致：其余键（capturedAt、executableSha256、privateHeadlessLaunch）均为 camelCase，而同批次 M10.5.9-browser-runtime.json 中同一含义的字段写作 `originalWinError`。触发条件：任何按 `winError` 读取 OS 错误码的 schema 校验或下游工具会静默取不到值（Python 侧 `availability['firefox'].get('winError')` 返回 None），导致 14001（并行配置错误）这一关键结论丢失。注意当前 scripts/collect-m1059-evidence.py 第 43 行和 docs/testing/M10.5.9-results.json 第 1261 行都以 `winerror` 读取，若重命名必须同步修改这两处，否则证据采集会直接断言失败。
- 判断：本地核对 firefox.winerror=14001。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-browser-availability.json:6；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1475 · 不采纳

- 位置：docs/testing/M10.5.9-browser-availability.json:10；OCR原严重度：medium
- 计划：无
- 原意见：退出码以无符号十进制记录，未给出十六进制或 NTSTATUS 解释：3221225477 即 0xC0000005（STATUS_ACCESS_VIOLATION），而 PowerShell/CMD 报出的同一失败通常是 -1073741819。触发条件：下游恢复逻辑按有符号 int 解析（Java `int`/C# `int` 反序列化该值会溢出抛异常），或按“退出码>0 即普通非零退出”归类，就会把访问违例误判为可重试的一般失败，从而错误地重放私有 headless 启动。建议同时记录十六进制/有符号形式或写入明确的错误分类字段。
- 判断：本地核对 capturedAt="2026-10-19T08:18:18.392206+00:00"。原意见中可见字段本身成立，但没有证明其推测的行为故障；配套源码/阶段记录已限定作用域。仅对旧收据补充元数据或统一风格不作为本轮合理修复任务，保留历史事实字节。
- 证据：docs/testing/M10.5.9-browser-availability.json:10

## OCR-1476 · 不采纳

- 位置：docs/testing/M10.5.9-browser-availability.json:9；OCR原严重度：low
- 计划：无
- 原意见：同一文件出现两套状态词汇：顶层 firefox.status 用 `UNAVAILABLE`（无法启动），嵌套 privateHeadlessLaunch.status 用 `FAILED`；且文件没有 schema 版本或状态枚举，`privateHeadlessLaunch` 也没有失败原因/重试次数，无法区分“未尝试”“尝试过一次失败”“多次重试均失败”。触发条件：任何自动化校验只能按字符串精确匹配（参考 scripts/collect-m1059-evidence.py 第 43 行的硬编码比较），一旦生成端调整用词，校验会静默失效或误判；同时缺失重试计数会让“私有清单方案失败”的结论无法判定是否已穷尽重试。建议固定 status 枚举并补充 attempts/reason 字段（或在文件中声明 schema 版本）。
- 判断：本地核对 firefox.privateHeadlessLaunch=对象(4键)。UNAVAILABLE描述Firefox总体可用性，FAILED描述已执行的privateHeadlessLaunch，两层语义可同时为真；原始WinError和私有退出码已保留，不能要求所有层级使用同一状态词。
- 证据：docs/testing/M10.5.9-browser-availability.json:9；docs/testing/M10.5.9-browser-availability.json:5；docs/testing/M10.5.9-browser-availability.json:8

## OCR-1477 · 不采纳

- 位置：docs/testing/M10.5.9-candidate-smoke.json:13；OCR原严重度：medium
- 计划：无
- 原意见：该零值不可验证，无法排除付费/未计价用量：生成器 scripts/smoke-candidate.py 在收尾处（`report.update(status="PASS", modelRequests=len(received), paidRequests=0)`）无条件写死 paidRequests=0，而本证据里也没有记录实际使用的模型端点（baseUrl 主机是否为 127.0.0.1）、提供方或 usage/token 合计。触发条件：有人把 config.json 的 baseUrl 指向真实付费端点后重跑 smoke，脚本仍会产出 modelRequests=7、paidRequests=0 的 PASS 证据。建议在证据中固化端点主机标识与 usage 合计（或从实际 usage 派生 paidRequests），而不是硬编码零。
- 判断：本地核对 capturedAt="2026-10-19T09:18:42.846056+00:00"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-candidate-smoke.json:13；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1478 · 不采纳

- 位置：docs/testing/M10.5.9-candidate-smoke.json:3；OCR原严重度：low
- 计划：无
- 原意见：来源绑定信息不足：文件中只有 capturedAt 与 zipSha256，缺少制品名/版本、构建 commit、runId、执行主机与平台、生成脚本版本等字段。sha256 只能按内容绑定被测 ZIP，单独查看该文件时无法确定它对应哪个候选构建（M9-candidate-smoke.json 与本文件除时间、hash 外逐字段相同，只能靠文件名区分），一旦被复制/改名重命名或与其它候选 ZIP 混用便无法发现。建议补充 artifact/commit/runId/platform 等溯源字段，使 PASS 能被唯一归属。
- 判断：本地核对 capturedAt="2026-10-19T09:18:42.846056+00:00"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-candidate-smoke.json:3；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1479 · 不采纳

- 位置：docs/testing/M10.5.9-candidate-smoke.json:7；OCR原严重度：medium
- 计划：无
- 原意见：键名所声明的“无网络”超出实际观测口径：对应的校验只是断言 loopback fixture 服务器没有收到任何请求（smoke 脚本里 `assert not received`），无法排除 doctor 对其它主机（DNS/更新检查/遥测等）的出站调用。因此该布尔为 true 时，读者会误以为已验证完全离网行为。建议记录实际隔离方式（网络命名空间/防火墙/容器 --network=none 等）或把键名收窄为 doctorNoModelRequest 之类可被证据支撑的表述。
- 判断：本地核对 capturedAt="2026-10-19T09:18:42.846056+00:00"；status="PASS"。原意见中可见字段本身成立，但没有证明其推测的行为故障；配套源码/阶段记录已限定作用域。仅对旧收据补充元数据或统一风格不作为本轮合理修复任务，保留历史事实字节。
- 证据：docs/testing/M10.5.9-candidate-smoke.json:7；scripts/smoke-candidate.py

## OCR-1480 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-content-results.json:208；OCR原严重度：low
- 计划：无
- 原意见：PASS 与空诊断数组的语义不可区分：`transportDiagnostics: []` 与 `status: "PASS"`、`modelRequests: 10` 同时出现，但文件没有记录诊断采集是否真正执行（例如 diagnosticsCollected / diagnosticsCount / captureError）。当传输层异常被采集逻辑静默丢弃时，结果同样是空数组，审计方无法区分“确实无异常”和“没有采集到”。触发条件：传输异常被 harness 丢弃或诊断采集步骤失败。建议补充诊断采集状态字段，或在校验断言中新增一条显式的诊断非空/采集成功检查。
- 判断：本地核对 capturedAt="2026-10-19T09:12:07.118Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-chromium-content-results.json:208；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1481 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-content-results.json:188；OCR原严重度：medium
- 计划：无
- 原意见：版本不一致的截图产物：本文件是 M10.5.9 的运行证据（capturedAt 2026-10-19、jarSha256 对应 M10.5.9），但 5 个截图文件名均为 `content-M10.5.6-*.png`（行 188、192、196、200、204）。若这些是沿用的旧版本命名，则截图无法证明对应当前 jar 的构建；若只是场景名，命名中也应体现实际运行版本，否则版本回滚或截图复用无法被审计发现。触发条件：用本文件截图核对 M10.5.9 的界面行为时。建议将文件名版本号与实际运行版本对齐（如 content-M10.5.9-stream.png），或在文件中新增显式字段说明该命名含义。
- 判断：本地核对 capturedAt="2026-10-19T09:12:07.118Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.9-chromium-content-results.json:188；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1482 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-content-results.json:217；OCR原严重度：medium
- 计划：无
- 原意见：哈希与运行实例的绑定不成立/不可验证：本文件记录 logSha256，但既未给出被哈希日志的路径，也无 runId 或日志大小等辅助字段；同时该值与 42 秒后另行运行的 docs/testing/M10.5.9-webkit-content-results.json 的 logSha256 完全相同（capturedAt 分别为 09:12:07 与 09:12:49，browser 分别为 Chromium 155 与 WebKit 26.5）。两次不同引擎、不同时刻运行的日志逐字节相同在现实中不可能，说明该字段要么是固定/预制日志的哈希，要么是跨文件复制，因而无法作为本次运行的完整性证明。触发条件：审计人员按 logSha256 复核本次运行日志时。建议增加 logPath/runId 字段，并确保每次运行对各自输出日志独立计算哈希。
- 判断：本地核对 capturedAt="2026-10-19T09:12:07.118Z"；status="PASS"。相同内容会有相同摘要，不能反推未执行或复制。浏览器自动同源进入使connection/welcome可已是同一状态；夹具日志仅打印确定性的PASS名称，不同引擎/复跑可逐字相同。应核对执行入口和制品来源，不能要求截图/日志哈希强制互异。
- 证据：docs/testing/M10.5.9-chromium-content-results.json:217；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1483 · 采纳

- 位置：docs/testing/M10.5.9-chromium-content-results.json:74；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：证据可追溯性缺陷：checks 中存在三条完全同名的断言 `denied preview private.txt`（行 74、86、90）。同名断言无法区分被拒绝的到底是哪一次预览请求/哪个路径，任何按 name 聚合或去重的取证脚本（如发布证据汇总）会把 45 条折叠成 43 条，或在同名键覆盖时丢失失败项，导致“全部通过”的计数不可复核。触发条件：运行发布证据聚合或按名称匹配断言结果时。建议改为可唯一识别的名称，例如带上具体请求序号或目标标识（第 74 行即首个重复项）。
- 判断：本地核对 capturedAt="2026-10-19T09:12:07.118Z"；status="PASS"。content.mjs用path.split(/).at(-1)作为断言名，多个不同目录private.txt实际同名。数组未因此丢失用例，但日志无法区分场景；应使用不泄露用户根的fixture相对路径或稳定caseId，并保证必需ID集合唯一。
- 证据：docs/testing/M10.5.9-chromium-content-results.json:74；frontend/tests/content.mjs:109

## OCR-1484 · 不采纳

- 位置：docs/testing/M10.5.8-settings-browser-results.json:113；OCR原严重度：medium
- 计划：无
- 原意见：证据链无法自证：该文件已提交到仓库，但 6 张截图的 path 全部指向 `.build-cache/`，而 `.gitignore` 第 8 行已忽略 `.build-cache/`，采集脚本 `scripts/collect-m1058-evidence.py` 也只把 JSON 复制到 `docs/testing`（第 54-59 行仅校验、不搬运 PNG）。因此这里登记的 sha256 对应的是仓库中并不存在的文件，任何下游审阅者/CI 都无法复算校验和来证明截图确实由本次 JAR 产生，也无法证明“mobile settings have no document overflow”等结论。另外路径使用 Windows 反斜杠分隔符，POSIX 消费者（脚本第 55 行必须做 `replace(chr(92),'/')` 才能解析）容易解析失败。建议把截图落到 `docs/testing/`（或 `docs/testing/screenshots/`）并用正斜杠相对路径记录。
- 判断：本地核对 "path": ".build-cache\\M10-settings-plugins.png",       "sha256": "71f971078c146ae728774f1da260185b81cd99f562f8def189409cca27。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.8-settings-browser-results.json:113；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1485 · 不采纳

- 位置：docs/testing/M10.5.8-settings-browser-results.json:130；OCR原严重度：medium
- 计划：无
- 原意见：`M10-settings-mobile.png` 的 sha256（6df524f9…）与 M10.5.1/M10.5.2/M10.5.3/M10.5.4/M10.5.5/M10.5.6/M10.5.7/M10.5.9 各里程碑完全一致，而本次其他 5 张截图的摘要都随构建变化。在 JAR 摘要已由 4b717b2e… 变为 197ba380… 的前提下，这张图在不同构建间字节级不变说明它极可能是沿用旧产物而非本次运行生成，也就无法支撑本文件第 26 项 “mobile settings have no document overflow” 针对当前 JAR 的结论。建议采集流程对每张截图记录生成时间戳/所属 JAR 摘要并在复用截图时显式标注（或断言失败）。
- 判断：本地核对 "sha256": "6df524f9f60edbeff80e6cd55d6fb10df01d11c97c12f44ec7b84cd18e30f5dc"     },     {。相同内容会有相同摘要，不能反推未执行或复制。浏览器自动同源进入使connection/welcome可已是同一状态；夹具日志仅打印确定性的PASS名称，不同引擎/复跑可逐字相同。应核对执行入口和制品来源，不能要求截图/日志哈希强制互异。
- 证据：docs/testing/M10.5.8-settings-browser-results.json:130；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1486 · 不采纳

- 位置：docs/testing/M10.5.8-settings-browser-results.json:140；OCR原严重度：medium
- 计划：无
- 原意见：`fixtureCredentialsCleaned: true` 是自证式布尔值，文件内没有任何可复核依据：既没有列出被清理的凭据目标（vault/凭据存储条目、Windows 凭据管理器 target 名）或清理前后校验结果，也没有对应的 sha256/日志引用，而本文件恰恰声称“responses and disk exclude key and credential target”“connection deletion … clears managed credential”。一旦清理逻辑漏删，这个字段仍会以 `true` 通过采集脚本第 53 行的断言，敏感信息残留不会被发现。建议记录被清理目标的标识（脱敏后）与逐项校验结果，而不是单一布尔值。
- 判断：本地核对 "fixtureCredentialsCleaned": true }。fixtureCredentialsCleaned并非未验残留就常量通过：settings finally中启动Cleanup并assert退出0后才写报告，Cleanup删除配置引用targets后逐项vault.read为空，否则抛异常。清理失败不会落该true；原意见忽略实际回读。
- 证据：docs/testing/M10.5.8-settings-browser-results.json:140；frontend/tests/settings.mjs:155；harness-cli/src/test/java/io/jharness/cli/WorkbenchCredentialCleanup.java:28

## OCR-1487 · 不采纳

- 位置：docs/testing/M10.5.8-settings-browser-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：`browser` 键里存放的是浏览器版本号（155.0.8059.82）而不是浏览器名/通道，导致产物来源不可判定：同一批次的其他证据（如 `docs/testing/M10.5.8-browser-results.json` 第 5-6 行）使用 `browserVersion` + `browserChannel`（"chrome"），本文件只有版本号，无法确认是 Chrome、Edge 还是打包的 Chromium 变体；本文件的 `settings rejects cross-origin mutation`、`settings rejects unauthenticated operator` 两项检查又缺少 `testedOrigin`，没有记录被测源就无法复核跨源/未鉴权判定的前提。同类问题还有键名不统一：其他 M10.5.8 产物使用 `status`/`capturedAt`，这里用 `date` 且没有 `status`，按 `status == 'PASS'` 读取的通用校验脚本（如 `scripts/collect-m1059-evidence.py` 第 72 行的写法）会把本文件判为非通过。建议改为 `capturedAt`/`browserVersion`/`browserChannel`/`testedOrigin` 并补 `status: "PASS"`。
- 判断：本地核对 browser="155.0.8059.82"；date="2026-10-18T12:21:28.948Z"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-settings-browser-results.json:4；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.8-settings-browser-results.json:2

## OCR-1488 · 部分采纳

- 位置：docs/testing/M10.5.9-browser-runtime.json:8；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：第 8 行把本机绝对路径写进了提交到仓库的证据文件：`D:\Documents\my projects\J-Harness\.build-cache\m1059-firefox-1ual7_ar`，泄露主机用户名/目录布局与缓存目录随机后缀，一旦该证据随 PR 或制品外发即构成环境信息泄露。同时该值不可移植：消费方 `scripts/collect-m1059-evidence.py:47` 直接用 `Path(runtime['privateRoot'])` 拼路径做哈希校验，在其它机器上会指向不存在的路径（`sha()` 抛 FileNotFoundError）或指向他人可控目录，等同于把校验目标交给文件里的字符串决定。建议只记录相对 `.build-cache` 的目录名（并由消费方以仓库根为基准重新拼接、再做 `is_relative_to` 边界校验），或干脆不落盘绝对路径。
- 判断：privateRoot确为机器绝对缓存路径，collector直接Path(runtime.privateRoot)并重算私有目录原文件摘要。采纳改为缓存内相对位置并resolve后检查边界，使记录不能任意决定校验根；不把无密钥的D盘路径本身当凭据泄漏。
- 证据：docs/testing/M10.5.9-browser-runtime.json:8；scripts/collect-m1059-evidence.py:47

## OCR-1489 · 部分采纳

- 位置：docs/testing/M10.5.9-browser-runtime.json:73；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：第 73 行 `originalFilesUnchanged` 是无条件写入的常量（生产端 `scripts/prepare-test-browsers.py:54` 在 `report.update()` 里硬编码 True，只在拷贝阶段比对过一次），并不是运行完浏览器后重新校验官方安装目录得出的结果。触发条件：私有副本中的 `updater.exe`/`pingsender.exe` 或浏览器自身在运行期改写官方目录、或官方目录被外部改动，该字段仍为 true；消费方 `collect-m1059-evidence.py:48` 把该布尔与私有目录哈希一起参与判定，会因此得到“官方文件未变”的虚假结论。建议改为由消费方在采集时实时重算官方目录哈希再推导该字段，或在清单里删除该断言、只保留可复算的哈希集合。
- 判断：本地核对 originalFilesUnchanged=true。originalFilesUnchanged在准备时设置，表达复制前后原字节比较，并非浏览器运行后官方目录持续监测。采纳明确采集时点；如果沿用运行后不变声明，需在运行后重算来源摘要。
- 证据：docs/testing/M10.5.9-browser-runtime.json:73；scripts/prepare-test-browsers.py:54

## OCR-1490 · 不采纳

- 位置：docs/testing/M10.5.9-browser-runtime.json:72；OCR原严重度：medium
- 计划：无
- 原意见：第 72 行 `privateManifestSha256` 的语义没有在文件里被限定，实际只覆盖生产端新生成的 `mozglue.manifest` 这一个文件（消费端 `collect-m1059-evidence.py:49` 硬编码该文件名）。触发条件：生成清单文件的名称/数量变化，或私有目录被追加文件（例如更新器写入的 `updates/`、崩溃、`prefs.js` 等）、被删除文件，清单仍然“校验通过”，因为 `originalFilesSha256` 只列出拷贝时刻存在且被枚举到的文件，覆盖范围无法收敛。建议增加显式的 `privateManifestPath` 字段并补充私有目录整体清单（文件数 + 排序后 `path:sha256` 拼接的目录摘要 + 额外文件检测），使增删都能被发现。
- 判断：本地核对 privateManifestSha256="29ac264e265493dd3b9d8cc892adbb2fa60de396f518197fa888da5ec622747e"；originalFilesSha256=对象(61键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-browser-runtime.json:72；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.9-browser-runtime.json:9

## OCR-1491 · 不采纳

- 位置：docs/testing/M10.5.9-browser-runtime.json:2；OCR原严重度：medium
- 计划：无
- 原意见：第 2 行 `status` 表达的是“清单种类”而非“结果状态”，文件里也没有任何成功/失败标记（第 6 行只保留 `originalWinError` 14001，第 7 行 `version` 也只是私有副本 `--version` 的输出）。触发条件：私有装配后浏览器仍未真正可运行（例如 `--version` 通过但启动即崩）时，该文件结构与成功场景完全一致；消费方以 `runtime['status']=='PRIVATE_ASSEMBLY_MANIFEST'` 作为“绕过 14001 成功”的判据，会把不完整/未验证的装配当作可用结果继续重放。建议把状态拆成 `status: PASS|FAIL` 与 `mode: PRIVATE_ASSEMBLY_MANIFEST` 两个字段，并记录原始错误是否已在私有副本上被消除的可验证证据（如私有副本的启动探针结果与哈希）。
- 判断：本地核对 status="PRIVATE_ASSEMBLY_MANIFEST"；originalWinError=14001。PRIVATE_ASSEMBLY_MANIFEST记录装配方式，不承担浏览器验收PASS；availability单独明确UNAVAILABLE、privateHeadlessLaunch FAILED，验收说明明确版本探针成功不算实际启动成功。原意见把准备记录当通过门禁。
- 证据：docs/testing/M10.5.9-browser-runtime.json:2；docs/testing/M10.5.9-browser-availability.json:5；docs/testing/M10.5.9-validation.md:22；docs/testing/M10.5.9-browser-runtime.json:6

## OCR-1492 · 不采纳

- 位置：docs/testing/M10.5.9-browser-runtime.json:51；OCR原严重度：medium
- 计划：无
- 原意见：清单把 `updater.exe`（第 51 行）、`pingsender.exe`（第 41 行）、`nmhproxy.exe`（第 36 行）以及 `defaults/pref/00-playwright-prefs.js`（第 66 行）一并纳入私有副本，但文件里没有任何“这些可执行文件/偏好项被禁用或内容被约束”的记录。触发条件：私有副本被直接启动（非仅 `--version`）时，更新器可自行下载并替换 `xul.dll` 等二进制、pingsender 可外联上报，二者都会改写 `privateRoot` 内容并让第 72–73 行的哈希断言在事后失效，等价于绕过“官方字节不变 + 私有副本可控”的隔离边界。建议在清单中显式记录启动策略（禁用更新/遥测的证据，如 `update-settings.ini`、`playwright.cfg` 与 pref 文件的内容白名单哈希），而不是仅记录其自身哈希。
- 判断：本地核对 privateRoot="D:\\Documents\\my projects\\J-Harness\\.build-cache\\m1059-firefox-1ual7_ar"。私有副本要求完整保留官方安装字节，仅补清单；包含上游updater/prefs文件不证明这些可执行文件被运行、或安全偏好被修改。没有实际外联/降级证据，不能按猜测删改官方文件或自行偏好白名单。
- 证据：docs/testing/M10.5.9-browser-runtime.json:51；docs/testing/M10.5.9-validation.md:20；scripts/prepare-test-browsers.py；docs/testing/M10.5.9-browser-runtime.json:8

## OCR-1493 · 不采纳

- 位置：docs/testing/M10.5.9-browser-runtime.json:66；OCR原严重度：medium
- 计划：无
- 原意见：第 66 行登记的 `defaults/pref/00-playwright-prefs.js` 会在每次启动时生效，可以改变安全默认值（如远程调试、签名/证书类偏好），但这里的哈希是与拷贝时刻的现实内容自洽的——它只能检测事后漂移，不能说明被放行的是哪一组偏好。触发条件：准备脚本或上游 Playwright 包新增/改动该 pref 文件，清单依旧“校验通过”（哈希随之重新生成），自动化环境即静默继承了新的安全默认值。建议对该文件采用允许列表校验（记录解析后的键值集合并断言不含安全降级项），而非仅记录整体哈希。
- 判断：本地核对 capturedAt="2026-10-19T08:25:34.201512+00:00"；status="PRIVATE_ASSEMBLY_MANIFEST"。私有副本要求完整保留官方安装字节，仅补清单；包含上游updater/prefs文件不证明这些可执行文件被运行、或安全偏好被修改。没有实际外联/降级证据，不能按猜测删改官方文件或自行偏好白名单。
- 证据：docs/testing/M10.5.9-browser-runtime.json:66；docs/testing/M10.5.9-validation.md:20；scripts/prepare-test-browsers.py

## OCR-1494 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-development-results.json:2；OCR原严重度：high
- 计划：无
- 原意见：顶层结论 `status: PASS` 是独立硬编码值，没有与 `checks` 数组绑定（无总数、无失败/跳过计数、无必需检查清单、无派生规则）。触发条件：任一必需检查被重命名、删除、数组被截断，或某检查项未写入时，产物依旧显示 PASS。建议增加 `requiredChecks` 清单 + `checkCount/failedCount/skippedCount`，并让 `status` 由检查结果派生（如 `min(checks.passed) && checks.length == requiredChecks.length`），否则该产物无法证明 M10.5.9 的通过结论。
- 判断：本地核对 checks=数组(14项)；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-chromium-development-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.9-chromium-development-results.json:6

## OCR-1495 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-development-results.json:52；OCR原严重度：high
- 计划：无
- 原意见：“刷新读取已有构建结果而不重放”这一恢复/幂等结论仅有布尔值，没有任何请求轨迹或计数基线。当前 `modelRequests: 12` 是刷新后的总量，缺少刷新前的基线值与刷新窗口内的增量，因此“重放已发生”（例如刷新时又提交了一次任务，或重放被同一计数器掩盖）无法从产物中证伪。触发条件：刷新路径误触发一次模型请求时，本产物仍可显示 PASS。建议记录 `modelRequestsBeforeRefresh`、`modelRequestsAfterRefresh`、`refreshRequestDelta: 0`。同理，`paidRequests: 0` 若未接入实际计费/请求拦截层则是不可验证的断言。
- 判断：本地核对 capturedAt="2026-10-19T09:06:14.916Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-chromium-development-results.json:52；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1496 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-development-results.json:5；OCR原严重度：medium
- 计划：无
- 原意见：产物缺少 schema 版本与期望环境标识：没有 `schemaVersion`，`browserEngine`/`browserVersion` 也未与期望值（最低/固定引擎版本）比对，因此旧格式或被篡改的产物可被下游脚本按当前格式解析；同时无法判断该 155.x 引擎是否为 CI 声明支持的版本。触发条件：测试脚本升级字段后混用历史产物。建议增加 `schemaVersion` 与 `expectedBrowserVersion` 字段并记录比对结果。
- 判断：本地核对 browserEngine="chromium"；browserVersion="155.0.8059.82"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-chromium-development-results.json:5；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.9-chromium-development-results.json:4

## OCR-1497 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-development-results.json:56；OCR原严重度：high
- 计划：无
- 原意见：“所有模型流量均为已授权、已脱敏的回环流量”这一安全结论只记录为布尔值，产物中没有任何可复现证据：缺少实际请求目标 host/端口（是否为 127.0.0.1/::1）、允许 host 白名单命中记录、被拒绝的越界请求计数，以及脱敏命中条目数与脱敏用样例类别（不要输出凭据值）。触发条件：脱敏规则失效或流量被路由到非回环/外部端点时，该检查仍可能被判为 passed，产物无法区分。建议补充 `loopbackHosts`、`deniedRequests`、`redactionHits` 等派生字段，并说明该断言的断言点。
- 判断：本地核对 capturedAt="2026-10-19T09:06:14.916Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-chromium-development-results.json:56；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1498 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-development-results.json:60；OCR原严重度：medium
- 计划：无
- 原意见：“无浏览器运行时异常”存在真空通过风险：产物未记录异常监听是否在导航前注册、页面是否真实加载完成（如 `load`/`networkidle` 就绪态）、以及已捕获异常/console error 的计数。触发条件：页面白屏、脚本未执行或监听注册晚于异常发生，该检查会以“零捕获”判为 passed。建议补充 `pageLoadState`、`errorListenerRegisteredAt`、`capturedExceptionCount`、`capturedConsoleErrorCount` 等字段。
- 判断：本地核对 capturedAt="2026-10-19T09:06:14.916Z"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-chromium-development-results.json:60；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1499 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-development-results.json:36；OCR原严重度：medium
- 计划：无
- 原意见：“编译后的 class 与最终源码一致”缺乏可比对的定位信息：产物只给出整体 `jarSha256`/`factsSha256`/`logSha256`，没有指明被比较的 class 内部路径、对应源码路径，以及实际参与比对的摘要（例如反编译字节码摘要 vs 源文件摘要）。触发条件：比对的是过期 class、或比对目标文件与断言中的文件不一致时，结论仍为 passed。建议记录被比对文件的 `classPath`/`sourcePath` 及其摘要，使一致性结论可复算。
- 判断：本地核对 jarSha256="197ba3803566f8cc8cb4ed3ae0d85b604e0e09055205d0fc57104aca24fd179e"；factsSha256="bde14c6a8f12e84a0674f3645ddad7844d6b19688fe192811c2e55ce1afea365"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-chromium-development-results.json:36；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.9-chromium-development-results.json:74；docs/testing/M10.5.9-chromium-development-results.json:75

## OCR-1500 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-browser-results.json:9；OCR原严重度：medium
- 计划：无
- 原意见：28 个 check 都只记录 passed: true（第 9-120 行），没有 expected/actual、HTTP 状态码、DOM 命中次数或跳过标记。触发条件：某个断言因选择器未命中而提前 return、或断言抛出的异常被外层捕获后仍写入 true 时，清单与真实全 PASS 无法区分；对安全类断言（configured secret is redacted、token absent from URL/storage/page text and downloaded report、invalid cursor pauses stream）影响最大。建议每个 check 增加 detail/evidence 字段，至少记录断言是否真正执行以及关键观测值。
- 判断：本地核对 capturedAt="2026-10-19T09:11:50.804Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-chromium-browser-results.json:9；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1501 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-browser-results.json:124；OCR原严重度：medium
- 计划：无
- 原意见：paidRequests=0 与 modelRequests=14 并列（第 124-125 行），但没有记录该分类的依据：没有 provider base URL（脱敏形式）、是否存在凭据、是否有出网阻断/请求拦截计数。触发条件：运行环境把模型调用指向真实付费端点、而 harness 仍按“keyless”判定时，0 会被误记，而“keyless runtime reports zero paid requests”这条安全断言仍会 PASS。建议补充 providerEndpoint、credentialsPresent=false、blockedHosts/outboundRequests=0 以及判定逻辑版本，使 0 可被独立复核。
- 判断：本地核对 capturedAt="2026-10-19T09:11:50.804Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-chromium-browser-results.json:124；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1502 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-browser-results.json:240；OCR原严重度：medium
- 计划：无
- 原意见：jarSha256、fixture、logSha256 只记录摘要而不记录定位信息：logSha256 没有对应日志路径，jarSha256 没有构建产物路径/坐标，fixture 仅有名字 "browser"；而这些对象都位于 sourceSha256 钉住的源码树之外（.build-cache、target 产物、运行日志）。触发条件：同名 jar 被重新打包、存在多个构建产物、日志被轮转/覆盖时，验证者既无法定位被摘要的文件，也无法重算摘要，“可复现”的证据链在此处断裂。建议补充 jarPath、logPath、fixturePath 以及生成器/Playwright 版本。
- 判断：本地核对 capturedAt="2026-10-19T09:11:50.804Z"；status="PASS"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.9-chromium-browser-results.json:240；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1503 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-browser-results.json:128；OCR原严重度：medium
- 计划：无
- 原意见：sourceSha256 只钉住 71 个文件，且清单内没有仓库 commit、脏工作区标记、schemaVersion 与起止时间（第 2-3 行、第 128 行起）。触发条件：未被枚举的输入发生变化（例如其他 harness-* 模块源码、pom 之外的依赖解析结果、前端构建工具版本差异）时，同一份 sourceSha256 仍然匹配，PASS 无法绑定到确定的源码状态，事后无法判断是哪个提交通过了测试。建议补充 gitCommit、gitDirty、schemaVersion、startedAt/finishedAt（或 durationMs）。
- 判断：本地核对 capturedAt="2026-10-19T09:11:50.804Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-chromium-browser-results.json:128；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1504 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-browser-results.json:126；OCR原严重度：medium
- 计划：无
- 原意见：writeExists 只有一个布尔值（第 126 行），无法核验“审批写入的是工作区内的真实文件”这一结论：缺少写入文件的相对路径、字节数和内容 sha256。触发条件：写入落到工作区之外（文件系统边界越界）或写入的是空/占位文件时，此项仍为 true，PASS 不受影响。建议记录 path（相对任务工作区）、size 与 sha256，并对越界路径直接判失败。
- 判断：本地核对 capturedAt="2026-10-19T09:11:50.804Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-chromium-browser-results.json:126；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1505 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-browser-results.json:212；OCR原严重度：high
- 计划：无
- 原意见：第 209 行（connection）与第 213 行（welcome）的 sha256 完全相同（cc84a400…），而同一批次 docs/testing/M10.5.9-webkit-browser-results.json 中这两个状态的两份摘要不同（407c5402… / 20472b23…）。触发条件有两种：截图写入时 welcome 态没有真正切换（同一画面被重复落盘，后一次覆盖前一次），或摘要在生成时被复用/复制。后果是清单无法证明“浏览器自动进入后的 welcome 态”被观察到，且截图文件可能与摘要不匹配；“invalid API credentials rejected while browser enters automatically”这条 PASS 因此缺少独立证据。建议重新采集并显式断言两状态摘要不同，或在清单中记录 capturedAt 与“预期相同”的标记。
- 判断：本地核对 capturedAt="2026-10-19T09:11:50.804Z"；status="PASS"。相同内容会有相同摘要，不能反推未执行或复制。浏览器自动同源进入使connection/welcome可已是同一状态；夹具日志仅打印确定性的PASS名称，不同引擎/复跑可逐字相同。应核对执行入口和制品来源，不能要求截图/日志哈希强制互异。
- 证据：docs/testing/M10.5.9-chromium-browser-results.json:212；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1506 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-browser-results.json:202；OCR原严重度：medium
- 计划：无
- 原意见：请求计数器缺少口径定义（第 201-203 行）：taskSubmissions=9 与 runtime.opened=9 完全一致，但清单同时声称“duplicate click creates exactly one actual task”“lost submit response … reconciles existing task with no POST replay”。若 taskSubmissions 统计的是“被服务端接受的提交”，那么 POST 被重放但被去重、或客户端重复提交被静默丢弃时，这两个值仍会相等，全 PASS 无法区分“恰好一次”与“重复提交被吞”。建议拆分为 submitAttempts / acceptedTaskCreations / dedupedOrReplayedPosts 并注明每个字段的计数口径。
- 判断：本地核对 capturedAt="2026-10-19T09:11:50.804Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-chromium-browser-results.json:202；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1507 · 不采纳

- 位置：docs/testing/M10.5.8-search-results.json:120；OCR原严重度：medium
- 计划：无
- 原意见：截图证据不可跨平台复核：路径使用 Windows 反斜杠分隔符（第 124 行同样为 `.build-cache\\M10.5.8-search-mobile.png`），且指向被 .gitignore:8 忽略的 .build-cache/ 目录，仓库中并不存在该文件。触发条件：在 Linux/macOS CI 或任意 fresh clone 中按 path 拼接 root 后校验 sha256，会因路径含反斜杠被当作文件名的一部分（或文件缺失）而失败；本仓库的采集脚本 scripts/collect-m1058-evidence.py:55 是靠 `.replace(chr(92), '/')` 才兼容，任何直接消费该归档产物的校验器都会误报截图漂移。建议归档时写入 POSIX 相对路径（与 M10.5.9-chromium-search-results.json 一致），或将截图随证据一并归档，保证 sha256 可从仓库直接验证。
- 判断：本地核对 capturedAt="2026-10-18T12:20:45.027Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.8-search-results.json:120；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1508 · 不采纳

- 位置：docs/testing/M10.5.8-search-results.json:114；OCR原严重度：medium
- 计划：无
- 原意见：这条断言与生成它的 fixture 实际校验范围不一致，PASS 结论被夸大。frontend/tests/search.mjs:28 用 stoppedHostDiagnostic 把“宿主已停止”类 pageerror 从 errors 中剔除，:74 只断言 errors.length===0，并把被剔除的诊断写入 transportDiagnostics；而本产物既没有 transportDiagnostics 键，也把断言名写成“no browser runtime exceptions”。触发条件：浏览器在执行过程中抛出任意被 stoppedHostDiagnostic 判定为良性的异常（例如断言本身未覆盖的真实渲染错误被误判），errors 为空 → 本项仍记 passed=true，且产物中没有任何被忽略异常的证据可复核。建议将该检查名恢复为“no unexpected browser runtime exceptions”，并在产物中记录 transportDiagnostics（或至少记录被忽略条数），使 PASS 与真实断言范围一致。
- 判断：本地核对 capturedAt="2026-10-18T12:20:45.027Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.8-search-results.json:114；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1509 · 不采纳

- 位置：docs/testing/M10.5.8-search-results.json:128；OCR原严重度：low
- 计划：无
- 原意见：本记录与同一 fixture 当前产出、以及同批次兄弟记录存在键集合漂移：frontend/tests/search.mjs:75 会输出 browserEngine 与 transportDiagnostics，M10.5.9-chromium-search-results.json:128-131 也同时包含这两个键，而本文件缺失。触发条件：消费端（如 scripts/run-workbench-matrix.mjs 之外的证据核对脚本、跨浏览器汇总）按统一 schema 读取 browserEngine/transportDiagnostics 时，会在 M10.5.8 检索记录上得到 undefined，进而把缺失当作空值或直接抛错，无法判断该次运行是哪个浏览器引擎、是否有被忽略的运行时诊断。建议在此产物中补齐 browserEngine/transportDiagnostics（或增加 schemaVersion/fixtureSourceSha256 说明产出脚本版本），保证同一里程碑内各记录可按同一 schema 解析。
- 判断：本地核对 capturedAt="2026-10-18T12:20:45.027Z"；status="PASS"。将当前源码字段/新功能文件集套用到带日期的旧里程碑快照，不能证明旧记录陈旧造假或漏测。M10.5.9新增跨引擎runtime/transportDiagnostics及M10.5.6～.8新增UI/检索文件，旧阶段不应补填这些后来字段或重标通过。
- 证据：docs/testing/M10.5.8-search-results.json:128；.agent:当前接续状态；docs/testing/M10.5.9-validation.md:26

## OCR-1510 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-questions-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：第 2 行的聚合状态是硬编码值，文件中没有任何可反推该状态的计数字段（如 totalChecks / passedChecks / failedChecks）。当前 26 条 checks 全为 passed:true 时，无法从 schema 层面区分“聚合逻辑正确”与“聚合逻辑被绕过/写死”。触发条件：任一 checks[].passed 被改为 false 而 status 未同步，或生成脚本在异常路径直接写入 PASS，都会产出假绿的发布证据。建议补充 totalChecks/passedChecks/failedChecks，并在生成时断言 failedChecks === 0 且 transportDiagnostics.length === 0（可用 JSON Schema 约束），使状态可被机械校验。
- 判断：本地核对 capturedAt="2026-10-19T09:05:17.844Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-chromium-questions-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1511 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-questions-results.json:124；OCR原严重度：medium
- 计划：无
- 原意见：第 124 行的空数组被用作第 102 行“all provider traffic is authenticated loopback”这一安全断言的通过依据，但空数组无法区分“确实无异常诊断”与“诊断采集通道未生效/被中途清空”。缺少正向证据（期望请求数、实际流量来源/鉴权结果断言、鉴权失败计数），该安全项实际不可证伪。建议补充期望与实际的 transport 请求计数、采集开关状态，或记录诊断通道自身的版本/校验值，避免安全结论仅依赖“没有记录”。
- 判断：本地核对 capturedAt="2026-10-19T09:05:17.844Z"；status="PASS"。transportDiagnostics仅收受控停机/断网窗口允许诊断，不是provider鉴权流量日志。为空与模型调用存在不矛盾，不能将无诊断降级SKIP；鉴权有独立wire.every及API状态断言。
- 证据：docs/testing/M10.5.9-chromium-questions-results.json:124；frontend/tests/runtime.mjs；frontend/tests/questions.mjs；frontend/tests/search.mjs

## OCR-1512 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-questions-results.json:112；OCR原严重度：medium
- 计划：无
- 原意见：第 112-122 行的截图证据与清单版本不一致：本清单为 M10.5.9，但文件名内嵌 questions-M10.5.5-questions-*.png，且 screenshot 项只有 path/sha256，没有 runId、fixture 版本或捕获时间，无法把这些哈希绑定到本次运行。触发条件：上一里程碑的旧产物未被覆盖，或本次截图生成失败后复用了旧文件，sha256 校验仍会通过，从而产出不可复现/张冠李戴的发布证据。此外路径位于 .build-cache 这类可被清理的临时目录，缓存清理后证据即失效。建议在每项截图中写入 runId/fixtureVersion，文件名由本次运行标识生成，并把证据归档到带版本的非缓存目录。
- 判断：本地核对 capturedAt="2026-10-19T09:05:17.844Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.9-chromium-questions-results.json:112；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1513 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-questions-results.json:125；OCR原严重度：medium
- 计划：无
- 原意见：第 125-126 行仅有全局计数，没有按检查项或按提供方拆分的增量，也没有与 logSha256 对应日志的关联标识，因此无法把 18 次 modelRequests 与 26 条断言逐项核对，也无法验证 paidRequests 为 0 是否覆盖了全部出站调用。触发条件：某条检查内部发起了未被计数包装的付费请求，或计数在异常/重试路径上漏记，清单仍会显示 paidRequests: 0。建议补充按 provider/检查项统计的请求计数、计数口径说明，以及日志摘要与计数的一致性校验结果。
- 判断：本地核对 capturedAt="2026-10-19T09:05:17.844Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-chromium-questions-results.json:125；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1514 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-models-results.json:50；OCR原严重度：low
- 计划：无
- 原意见：安全相关断言（第 12 项“all protocol calls authenticate only through headers”、第 22 项“saved settings and session events contain no API key”）只记录 `name`/`passed`，没有任何断言载荷或证据指针（如被扫描的头部名、被检查的事件类型、对应截图/日志片段）。同时该 schema 只有布尔值，缺少 `skipped`/`error` 状态，消费方无法区分“断言通过”与“断言未执行”。建议为每个检查增加 `detail`/`evidence` 字段，或至少在整体上给出逐项检查的来源标识与未执行标记。
- 判断：本地核对 checks[0].name="input exposes three trusted model connections"；checks[0].passed=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-chromium-models-results.json:50；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.9-chromium-models-results.json:6；docs/testing/M10.5.9-chromium-models-results.json:7

## OCR-1515 · 部分采纳

- 位置：docs/testing/M10.5.9-chromium-models-results.json:108；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：`transportDiagnostics` 为空数组时存在歧义：既可能是“捕获已启用且无允许的传输诊断”，也可能是“页面错误处理器未挂接/未捕获”，而第 18 项 `no unexpected browser runtime errors` 的 PASS 完全依赖该捕获确实生效。建议同时记录捕获是否启用的标志（如 `diagnosticsCaptureEnabled`）或捕获到的 `pageerror` 总数，使“0 诊断”成为可判定的证据而不是缺失数据。
- 判断：本地核对 transportDiagnostics=数组(0项)。models为导航前挂pageerror监听并分流transportDiagnostics，空数组不能推定监听失效。采纳显式pageErrors/unexpectedErrors/allowedDiagnostics计数及监听自检负例，仅提高可审计性。
- 证据：docs/testing/M10.5.9-chromium-models-results.json:108；frontend/tests/models.mjs

## OCR-1516 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-models-results.json:104；OCR原严重度：medium
- 计划：无
- 原意见：三条截图均指向 `.build-cache/` 下的路径，而仓库 `.gitignore:8` 明确忽略 `.build-cache/`。因此入库的报告引用的是不随仓库发布的产物，`model controls fit mobile viewport`（第 13 项）与两张移动端视图的视觉证据在仓库内无法被任何人复核或重放，只剩 sha256 可校验（前提是本地恰好保留该缓存）。建议将关键截图（或其缩略/受控副本）纳入版本化目录，或在报告中说明截图可由哪条脚本命令重新生成以满足可复现要求。
- 判断：本地核对 status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.9-chromium-models-results.json:104；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1517 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-models-results.json:96；OCR原严重度：medium
- 计划：无
- 原意见：截图文件名仍带 `M10.5.4`，而所在目录与报告都是 M10.5.9（`models-M10.5.4-models-desktop.png` 等三条）。当前无法从命名判断这是“夹具/脚本沿用旧版本号”还是“复用了上一里程碑的旧截图”，而本报告第 13 项 `model controls fit mobile viewport` 正好依赖移动端截图，证据可复现性因此在命名层被削弱。建议改为与本次运行一致的命名（或加 `capturedAt`/`milestone` 字段），使截图与本次 PASS 一一对应。
- 判断：本地核对 status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.9-chromium-models-results.json:96；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1518 · 部分采纳

- 位置：docs/testing/M10.5.9-chromium-models-results.json:110；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：`paidRequests: 0` 是由生成脚本固定写死的常量，并非对真实请求出网的测量结果，因此它无法佐证“没有产生付费请求”这一安全/成本结论；与 `modelRequests: 8` 并列时容易被读者误当作实测数据。建议改为记录可验证的来源，例如本次运行允许访问的端点白名单（仅 loopback）与请求计数明细，或明确标注该字段为“构造性断言”。
- 判断：本地核对 status="PASS"。采纳对该统计/安全断言补来源、单位和逐场景基线/增量，使汇总值可解释。不能将本地替身modelRequests与paidRequests=0并存当矛盾：history等用createServer绑定127.0.0.1，wire仅计该替身，host opened/writeExists含义应以夹具实现为准；原意见推测付费/重放发生未获证明。
- 证据：docs/testing/M10.5.9-chromium-models-results.json:110；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1519 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-models-results.json:109；OCR原严重度：medium
- 计划：无
- 原意见：键名与同批 M10.5.9 报告不一致：这里版本号放在 `browser` 下，另有独立的 `browserEngine`；同目录其它报告使用 `browserVersion` + `browserEngine` + `browserChannel`，日期键此处为 `date` 而其它为 `capturedAt`。任何按统一 schema 解析这些证据的消费方（校验脚本、汇总表）都必须为 models 夹具写特例，否则会静默读不到浏览器版本/捕获时间。建议统一为 `browserVersion`/`capturedAt`，并补 `browserChannel`（`JHARNESS_BROWSER_CHANNEL` 会改变实际使用的可执行文件，缺该字段则无法按同样渠道复现本 PASS）。
- 判断：本地核对 browser="155.0.8059.82"；browserEngine="chromium"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-chromium-models-results.json:109；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.9-chromium-models-results.json:114

## OCR-1520 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-history-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：第2行：顶层 `status` 与 `checks` 数组相互独立，文件内既没有由 `checks` 推导 `status` 的聚合规则，也没有失败计数或 schema 版本字段。下游 CI 只读 `status` 时，即便 `checks` 中出现 `passed: false`，或某次生成把键名写错导致解析为空，仍会被判为通过。建议增加 `schemaVersion`、`totalChecks`、`failedChecks`，并在生成端由 `checks` 计算 `status`，消费端校验 `totalChecks` 与预期条数一致。
- 判断：本地核对 status="PASS"；checks=数组(19项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-chromium-history-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.9-chromium-history-results.json:6

## OCR-1521 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-history-results.json:80；OCR原严重度：medium
- 计划：无
- 原意见：第79-82行（配合第102行 `transportDiagnostics: []`）：“no browser exceptions or credential leaks”只有布尔结果，`transportDiagnostics` 恒为空数组时也意味着采集器可能根本没注册。缺少可审计证据：`consoleErrors`/`pageErrors` 条数、监听注册时间（是否在页面导航前开始监听）、脱敏扫描使用的 needle 规则版本与命中数。建议补充上述计数字段，并在监听未生效时使该检查失败而非通过。
- 判断：本地核对 transportDiagnostics=数组(0项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-chromium-history-results.json:80；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.9-chromium-history-results.json:102

## OCR-1522 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-history-results.json:86；OCR原严重度：high
- 计划：无
- 原意见：第84-100行：截图证据路径全部指向未纳入版本控制的 `.build-cache/`，且文件名仍沿用 `history-M10.5.2-*`（与 M10.5.8 结果中的 `M10.5.2-*.png` 同名不同目录）。仅凭仓库内容无法复算这些 sha256，因此无法区分“M10.5.9 本次真实执行”与“复用旧里程碑快照后重新哈希”。触发条件：任何人试图核对 PASS 结论时，先要重建该缓存目录，哈希不可复现。建议把截图归档到受控目录（如 `docs/testing/artifacts/M10.5.9/chromium/`）并随结果提交，或在结果中补充文件大小、生成时间与执行时绝对路径，并在落盘前强制校验哈希一致。
- 判断：本地核对 capturedAt="2026-10-19T09:04:43.497Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.9-chromium-history-results.json:86；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1523 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-history-results.json:104；OCR原严重度：medium
- 计划：无
- 原意见：第103-105行：`modelRequests=138` 与 `completedCycles=65` 之间没有任何分解，而检查项 1、7、16、17 断言“without model calls / without executing model / no model request”。没有按阶段或按检查记录的请求计数（例如每项检查前后的 `modelRequests` 快照、`requestsByPurpose`），就无法证明这些阶段发出的请求数为 0，也无法发现隐藏、重复或付费流量（`paidRequests` 同样只有汇总值）。触发条件：若重启恢复或冷重复提交路径意外触发一次请求，本记录仍是 `PASS` 且计数仍可写成 138。建议为每项检查增加 `modelRequestsBefore/After` 与 `paidRequestsBefore/After`。
- 判断：本地核对 modelRequests=138；paidRequests=0。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-chromium-history-results.json:104；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.9-chromium-history-results.json:105

## OCR-1524 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-history-results.json:109；OCR原严重度：medium
- 计划：无
- 原意见：第108-110行：`fixture` 只有名称 `history`，`fixtureSourceSha256` 与 `logSha256` 只有哈希没有路径，无法定位被哈希的到底是哪个 fixture 源文件和哪份日志，也无法复现校验。建议补充 `fixtureSourcePath`、`logPath`（相对仓库根）或直接把对应文件一并归档，避免同名 fixture 更新后旧哈希继续“验证通过”。
- 判断：本地核对 fixture="history"；fixtureSourceSha256="989db69229688eb279ef80c98a8624710eec2725e47823cb7bab48bfab7eebca"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-chromium-history-results.json:109；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.9-chromium-history-results.json:108

## OCR-1525 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-history-results.json:107；OCR原严重度：high
- 计划：无
- 原意见：第107行：`jarSha256` 与上一里程碑 `docs/testing/M10.5.8-history-results.json` 第105行完全相同，而里程碑已推进，说明要么被测 jar 未重新构建，要么该哈希是沿用旧值。文件中又没有 jar 相对路径、size、mtime 或构建 commit 可供交叉验证，若 M10.5.9 的改动没打进被测 jar，本次 PASS 实际覆盖的是旧构建。建议补充 `jarPath`、`jarSize`、`buildCommit`、`builtAt`，并在采集时对实际被测 jar 现算哈希（同时校验与构建产物一致）。
- 判断：本地核对 jarSha256="197ba3803566f8cc8cb4ed3ae0d85b604e0e09055205d0fc57104aca24fd179e"。M10.5.9主要扩充验证夹具，产品JAR字节在无产品变化时可与M10.5.8相同。相同JAR哈希只说明字节相同，不能证明未重新测试或错误沿用哈希，matrix/collector实际现算并绑定来源。
- 证据：docs/testing/M10.5.9-chromium-history-results.json:107；scripts/run-workbench-matrix.mjs:35；docs/testing/M10.5.9-validation.md:5

## OCR-1526 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-history-results.json:48；OCR原严重度：medium
- 计划：无
- 原意见：第47-50行：文件系统边界这类关键负向安全断言只落了一个布尔值。若断言被跳过、条件写反或选择器失效，产物与真实通过完全一致，无法审计。建议为检查项增加 `evidence` 字段，记录：选中根的绝对路径、被拒绝的越界写入/读取次数与错误码、sibling sentinel 的命中次数（应为 0），以及用于判定的断言标识。
- 判断：本地核对 capturedAt="2026-10-19T09:04:43.497Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-chromium-history-results.json:48；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1527 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-history-results.json:106；OCR原严重度：medium
- 计划：无
- 原意见：第67-77、106行：重启相关检查（registry/标签/历史恢复、冷重复提交、重启后继续提交）对执行顺序极其敏感，但 `checks` 是扁平数组，没有序号、`startedAt/finishedAt`、`hostStartId` 或重启次数，`hostStopped` 也没有退出码，因此无法证明“恢复”发生在新进程而非同一进程的残留内存状态，也无法排除对陈旧状态的误通过。建议为每项检查记录时间戳与所连主机实例标识，并补充 `hostRestartCount`、`hostExitCode`。
- 判断：本地核对 checks=数组(19项)；hostStopped=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-chromium-history-results.json:106；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.9-chromium-history-results.json:6

## OCR-1528 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-extensions-results.json:164；OCR原严重度：medium
- 计划：无
- 原意见：此处空数组与 35 项 `passed: true` 组合后，无法区分“未发生受控传输诊断”与“相关故障路径根本没有被触发”：本文件包含多条否定型断言（如 `configuration editing does not connect MCP or request model`、`saving configuration performs no execution`、`disconnected assembly keeps uncertainty without model request or successful inventory`、`unknown assembly blocks settings and never replays external tool`），而这些断言的成立条件恰恰是“没有产生诊断/请求”。触发条件：测试驱动因回归提前返回、未走到对应步骤时，checks 仍可能写成 `true`，产物中也留不下任何可核查线索。建议为这类否定型检查补充可核查计数（例如 peer wire 请求数、model wire 请求数、被拒绝的请求状态码）而不是仅记录空诊断数组，使“未执行”与“已执行且无副作用”可区分。
- 判断：本地核对 capturedAt="2026-10-19T09:05:42.623Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-chromium-extensions-results.json:164；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1529 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-extensions-results.json:148；OCR原严重度：medium
- 计划：无
- 原意见：证据来源/新鲜度缺陷：本文件是 M10.5.9 的执行记录（`capturedAt`、目录 `.build-cache/M10.5.9/`、fixture `extensions` 均为当前里程碑），但四张截图文件名仍硬编码为 `M10.5.7`（第 148、152、156、160 行）。触发条件：任何按文件名/里程碑校验证据的消费方（如 `scripts/collect-m1059-evidence.py`、人工复核）都无法从路径区分“本次 M10.5.9 新截图”与“上次 M10.5.7 遗留截图被复用”，同名的旧图片可直接冒充当前 PASS 证据；同时 sha256 只能证明图片内容，不能证明其所属运行。建议把文件名中的里程碑改为与产物一致的 M10.5.9（或改为里程碑无关的名称）并重新采集，使路径本身可反映证据来源。
- 判断：本地核对 capturedAt="2026-10-19T09:05:42.623Z"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.9-chromium-extensions-results.json:148；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29；docs/testing/M10.5.9-chromium-extensions-results.json:3

## OCR-1530 · 部分采纳

- 位置：docs/testing/M10.5.9-chromium-extensions-results.json:171；OCR原严重度：low
- 计划：R0-02 / P3
- 原意见：该 wire trace 只保留方法名，丢掉了 harness 实际已捕获的关键字段（`frontend/tests/extensions.mjs` 的 `peerWire` 同时记录 `auth`、`params`），因此本文件中的 `unauthenticated MCP service connects without false empty credential error`、`MCP resource pagination and JSON SSE reads use real wire` 等断言无法用该 trace 复核：连续两个 `resources/list` 与两个 `resources/read`（第 175、176、178、179 行）既可能是分页/不同 URI 的正常调用，也可能是失败后的重试被计为成功，产物本身无法区分；同样看不到哪次调用未携带 Authorization。触发条件：解析该字段做证据校验时会把“重试”误判为“分页覆盖”。建议改为记录 `{method, auth: 是否存在, cursor/uri, responseKind}` 等结构，或至少与 `logSha256` 指向的原始 trace 一起固化可比对的信息。
- 判断：本地核对 logSha256="31a1db1687490e5342e4c42ed46651bf3ac9d8fb0baa76a3b4cd8a5babf36156"。mcpMethods确仅记录方法名，资源list/read各两次可分别是分页和不同URI读取，transportDiagnostics无关MCP流量。采纳保存脱敏参数类别/分页标志/checkId以复核调用归属，不据重复方法名认定重试或重放。
- 证据：docs/testing/M10.5.9-chromium-extensions-results.json:171；frontend/tests/extensions.mjs；docs/testing/M10.5.9-chromium-extensions-results.json:184

## OCR-1531 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-diagnosis-results.json:2；OCR原严重度：low
- 计划：无
- 原意见：PASS 结论缺少可复核的判定输入：`checks` 只有 `name`/`passed`，没有断言详情或失败信息；而且最后一项检查 “fixture closes with zero paid requests” 的判定还依赖 fixture 侧 `host.exitCode === 0`，该退出码、`startedAt/finishedAt`、以及 `modelRequests=5` 的来源（模型调用端点未被记录在 `requests` 中）都未持久化。下游只能看到一个自证的布尔集合，无法区分“检查已执行并通过”与“结果被手工改写”。建议补充 `hostExitCode`、时间区间，并为每个 check 增加 `evidence`/`detail` 字段（失败时记录 error），使本结果文件可被脚本化复核。
- 判断：本地核对 checks=数组(13项)；checks[0].name="real ARGV shell validation explains body not started and explicit modes"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-chromium-diagnosis-results.json:2；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.9-chromium-diagnosis-results.json:4；docs/testing/M10.5.9-chromium-diagnosis-results.json:6

## OCR-1532 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-diagnosis-results.json:60；OCR原严重度：medium
- 计划：无
- 原意见：证据不可复现：这些截图路径指向 `.build-cache/M10.5.9/browser/chromium/...`，而 `.build-cache/` 已被 `.gitignore`（第 8 行）忽略，仓库中并不存在对应的 PNG 文件（按文件名检索无结果）。因此本结果文件提交的 4 个 sha256 在干净检出上无法被复核，`collect-m1059-evidence.py` 重新计算 `sha(ROOT/shot['path'])` 时会直接失败，导致 PASS 结论缺少可验证的视觉证据。建议把证据截图落到随仓库提交的目录（例如 `docs/testing/assets/M10.5.9/chromium/...`）并同步更新 path 与 sha256；若确实要保留在构建缓存中，应在校验侧给出可诊断的“证据缺失，需重新生成”错误而不是静默失配。
- 判断：本地核对 capturedAt="2026-10-19T09:04:22.906Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.9-chromium-diagnosis-results.json:60；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1533 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-diagnosis-results.json:85；OCR原严重度：low
- 计划：无
- 原意见：请求轨迹只记录 `path` 与 `method`，没有 HTTP 状态码、请求序号/时间戳和响应摘要。这意味着一次返回 4xx/5xx 的 GET 与成功的 GET 在证据中完全相同，无法据此复核 “refresh restores diagnosis without task submission or unsafe verification”、“no browser runtime errors” 等断言，也无法证明取消（`/cancel` POST）之后没有再次提交任务或审批（UNKNOWN 状态未被重放）。建议为每条记录补充 `status`、`startedAt`/`seq`（必要时再带 `ok` 摘要），使调用顺序与成功性都能被独立校验。
- 判断：本地核对 screenshots[0].path=".build-cache/M10.5.9/browser/chromium/diagnosis-M10.5.1-shell.png"；requests[0].method="GET"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-chromium-diagnosis-results.json:85；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.9-chromium-diagnosis-results.json:60；docs/testing/M10.5.9-chromium-diagnosis-results.json:88

## OCR-1534 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-search-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：28 条断言每条只记录 name + passed，没有 expected/actual、执行命令、日志行锚点，也没有 SKIP 状态。触发条件：harness 跳过该用例、用例提前 return、或对未真正发出的请求做断言时，仍会落盘 passed:true，与真实通过不可区分；结合第 128 行为空的 transportDiagnostics，这些隔离类断言（工作区隔离、会话范围隔离、游标失效）无法复现也无法证伪。建议为每条断言记录 status(PASS/FAIL/SKIP)、实际观测值和日志引用，并在生成 status 时校验条数与预期用例数一致。
- 判断：本地核对 capturedAt="2026-10-19T09:05:54.485Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-chromium-search-results.json:1；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1535 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-search-results.json:0；OCR原严重度：high
- 计划：无
- 原意见：transportDiagnostics 为空数组，但第 10 行“content search rejects cross-origin request”和第 110 行“all real requests use redacted authorized loopback context”均记为 passed。触发条件：跨源拒绝、loopback 鉴权与脱敏这三类结论完全依赖 transport 层证据，空数组既可能是“无异常”，也可能是探针根本没启动/未采集，二者不可区分，因此这类安全检查的 false pass 无法被检出（与敏感信息脱敏相关的第 14、15 行同理）。建议在 diagnostics 为空时把依赖 transport 证据的断言降级为 SKIP 并让整体 status 非 PASS，或强制每条安全断言至少产出一条请求摘要/拒绝原因。
- 判断：本地核对 capturedAt="2026-10-19T09:05:54.485Z"；status="PASS"。transportDiagnostics仅收受控停机/断网窗口允许诊断，不是provider鉴权流量日志。为空与模型调用存在不矛盾，不能将无诊断降级SKIP；鉴权有独立wire.every及API状态断言。
- 证据：docs/testing/M10.5.9-chromium-search-results.json:1；frontend/tests/runtime.mjs；frontend/tests/questions.mjs；frontend/tests/search.mjs

## OCR-1536 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-search-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：截图文件名仍带 M10.5.8 前缀，而清单本身与所在目录是 M10.5.9（第 120、124 行）。触发条件：任何按里程碑归档/复核的流程读取本清单时，无法区分这两张 PNG 是本轮 M10.5.9 生成，还是 M10.5.8 的旧图被复制过来；清单里的 sha256 只固定“读取那一刻文件的内容”，不记录来源与生成时间，因此陈旧证据会被当作本轮 PASS 依据（同目录的 webkit 清单也存在同样的 M10.5.8 文件名，说明这是生成侧固定旧标签的行为，而非一次性笔误）。建议文件名由当前里程碑/runId 生成，并在清单中附带文件 mtime 或 runId 便于校验。
- 判断：本地核对 capturedAt="2026-10-19T09:05:54.485Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.9-chromium-search-results.json:1；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1537 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-search-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：只有 paidRequests=0 与 modelRequests=39 两个总量，而第 106 行“restart rebuilds or reuses indices without model replay”记为 passed。触发条件：若重启阶段确实发生了模型回放，总量计数无法暴露该行为；同理 paidRequests=0 在没有 provider 分类与分阶段计数时也不可审计，“无付费请求/无回放”的结论超出了证据范围。建议记录 restart 前后的 modelRequests 快照（重启后必须相等）以及按 provider 拆分的请求明细。
- 判断：本地核对 capturedAt="2026-10-19T09:05:54.485Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-chromium-search-results.json:1；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1538 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-search-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：清单只给出 jarSha256 / fixtureSourceSha256 / logSha256，却没有对应的文件路径、runId 或 schemaVersion，也没有把哈希与 capturedAt 绑定的签名。触发条件：.build-cache 会被清理或被下一次运行覆盖，核验者无法定位被哈希的实际 log/jar/fixture，也无法判断这组哈希是否与 2026-10-19T09:05:54Z 这次运行同批生成，因而可以把不同 run 的哈希拼进同一份 PASS 记录而不被发现。建议为每个哈希记录其路径与 runId，并加入 schemaVersion 以便校验清单结构。
- 判断：本地核对 capturedAt="2026-10-19T09:05:54.485Z"；status="PASS"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.9-chromium-search-results.json:1；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1539 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-search-results.json:129；OCR原严重度：low
- 计划：无
- 原意见：browserEngine 声明为 chromium，但 browserChannel 为 chrome，版本 155.0.8059.82 也属于品牌 Chrome 分支。触发条件：若断言实际在 Chrome channel 上执行，Chromium 侧特有的渲染/权限/跨源差异并未被覆盖，而清单仍以 chromium 引擎归档，兼容性结论会超出实际验证范围（第 24 行“query and snippet never execute markup”这类安全断言尤其依赖引擎解析行为）。建议统一 engine 与 channel 的语义，或额外记录渠道 chromium 的具体二进制 revision。
- 判断：本地核对 capturedAt="2026-10-19T09:05:54.485Z"；status="PASS"。Playwright chromium引擎使用Chrome channel是正常配置，记录已同时声明browserEngine=chromium/browserChannel=chrome，验收说明明确本机系统Chrome替代下载失败的Chromium；未冒称其它通道已验收。
- 证据：docs/testing/M10.5.9-chromium-search-results.json:129；docs/testing/M10.5.9-validation.md:20；docs/testing/M10.5.9-chromium-browser-results.json:5

## OCR-1540 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-turns-results.json:82；OCR原严重度：medium
- 计划：无
- 原意见：第 82 行只记录 `browserEngine: "chromium"`，缺少浏览器通道（channel）字段，导致本产物无法区分“自带 Playwright Chromium”与“系统安装的 Chrome 通道”。`frontend/tests/turns.mjs` 第 70 行会读取 `JHARNESS_BROWSER_CHANNEL`，而 `scripts/run-workbench-matrix.mjs` 第 17 行只对非 chromium 引擎删除该变量；因此同一个 `browserEngine: "chromium"` 的记录可能来自两种不同二进制，配合第 83 行 `browserVersion` 也无法判定来源。同批其它夹具（如 search/extensions）已输出 `browserChannel`，本文件缺失会造成跨引擎/跨通道证据不可复现、不可比对。建议补记通道字段（或明确固定为 bundled chromium 并在产物中固定写成常量）。
- 判断：本地核对 browserVersion="155.0.8059.82"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-chromium-turns-results.json:82；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.9-chromium-turns-results.json:83

## OCR-1541 · 部分采纳

- 位置：docs/testing/M10.5.9-chromium-turns-results.json:10；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：第 10 行断言名为“never enters browser storage”，但对应实现（`frontend/tests/turns.mjs` 第 79 行）只对 `localStorage`/`sessionStorage` 做序列化检查，未覆盖 IndexedDB、CacheStorage、Cookie 等其它持久化面。触发条件：草稿或凭据被写入 IndexedDB/Cookie（例如后续把草稿或选择状态迁移到 IndexedDB）时，这个断言仍会记 `passed: true`，本产物就会给出错误的“不落盘”结论。建议要么把断言范围收窄到名称所述范围（local/sessionStorage），要么补齐 IndexedDB/Cookie 检查后再保留当前措辞。
- 判断：本地核对 capturedAt="2026-10-19T09:04:53.547Z"；status="PASS"。turns该断言扫描localStorage/sessionStorage，未检查IndexedDB/cookie/CacheStorage。采纳将断言名称和证据scope收窄到实际扫描面，或实测其它存储面后才扩展；没有证据说明当前产品已向其它面写入草稿/密钥。
- 证据：docs/testing/M10.5.9-chromium-turns-results.json:10；frontend/tests/turns.mjs:79

## OCR-1542 · 不采纳

- 位置：docs/testing/M10.5.9-chromium-turns-results.json:62；OCR原严重度：medium
- 计划：无
- 原意见：第 62 行把“无浏览器错误”和“无凭据泄漏”合并为一个布尔结论，但支撑证据只有 `pageerror` 事件和“单个合成 key 不出现在 local/sessionStorage”两项（`frontend/tests/turns.mjs` 第 128-130 行），既不覆盖 console error、失败请求/响应体、IndexedDB，也不覆盖宿主日志中的凭据回显。同时本产物（第 62-63 行）只存 `passed: true`，没有任何事实快照（错误数、请求计数、脱敏前后摘要），因此该 PASS 与“检查项被弱化/空跑后依然通过”在产物层不可区分。建议在 checks 中输出可核验的事实字段（如 `errors: 0`、`storageKeys` 摘要、`factsSha256`），或拆分该聚合断言以便失败时定位。
- 判断：本地核对 capturedAt="2026-10-19T09:04:53.547Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-chromium-turns-results.json:62；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1543 · 不采纳

- 位置：docs/testing/M10.5.9-examples-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：判定与新鲜度绑定不足：`status: "PASS"` 是生成器无条件写入的字面量，`capturedAt` 也没有任何消费方与 jar 构建时间/本次运行做比较；而该快照没有记录产生它的脚本与示例源码摘要（对比浏览器结果里有 `fixtureSourceSha256`），因此既无法发现断言被弱化，也无法排除复用上一次运行残留的 PASS 文件（本文件 `jarSha256` 与 M10.5.8 快照完全相同，正是这类场景）。触发条件：示例脚本中途失败未覆盖旧结果文件，或断言被删改后重跑。建议在快照中加入 `sdk/tests/examples.mjs`、`sdk/examples/run.ts|run.py` 的 `sourceSha256` 与唯一 runId，并在校验时要求 `capturedAt` 晚于 jar 构建时间。
- 判断：本地核对 capturedAt="2026-10-19T08:26:12.769Z"；jarSha256="197ba3803566f8cc8cb4ed3ae0d85b604e0e09055205d0fc57104aca24fd179e"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-examples-results.json:2；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20；docs/testing/M10.5.9-examples-results.json:3；docs/testing/M10.5.9-examples-results.json:4

## OCR-1544 · 部分采纳

- 位置：docs/testing/M10.5.9-examples-results.json:17；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：证据可信度缺陷：该 `paidRequests: 0` 并非可观测计数，而是生成器里写死的字面量（`sdk/tests/examples.mjs` 写入 `paidRequests:0`，整个示例运行既没有统计模型请求数，也没有像 `frontend/tests/development.mjs` 那样记录 `modelRequests`）。同时示例进程以 `{...process.env}` 继承环境变量并只覆盖 `JHARNESS_API_KEY`，若宿主环境仍存在真实 provider 端点/凭据配置，实际可能发生付费调用，而本快照依旧会声称 0。触发条件：CI/本地环境残留真实 API 配置时执行示例脚本。建议改为记录由 loopback provider 实际观测到的请求条数（并对非 loopback base URL 做断言），再由校验脚本确认该计数为 0，而不是依赖常量。
- 判断：本地核对 capturedAt="2026-10-19T08:26:12.769Z"；status="PASS"。采纳对该统计/安全断言补来源、单位和逐场景基线/增量，使汇总值可解释。不能将本地替身modelRequests与paidRequests=0并存当矛盾：history等用createServer绑定127.0.0.1，wire仅计该替身，host opened/writeExists含义应以夹具实现为准；原意见推测付费/重放发生未获证明。
- 证据：docs/testing/M10.5.9-examples-results.json:17；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1545 · 不采纳

- 位置：docs/testing/M10.5.9-examples-results.json:9；OCR原严重度：medium
- 计划：无
- 原意见：哈希不可复现绑定：`factsSha256` 只给出摘要，未记录被摘要文件的路径/名称（facts 位于 `.build-cache/M10.5.8-example-<language>-<timestamp>/.jharness/sessions/web-<32hex>-<64hex>.jsonl` 这类临时且未归档的目录）与期望事实内容，事后无法重算或交叉验证；采集脚本也只校验 `c.get('factsSha256')` 非空，任何字符串都能通过。触发条件：需要按快照复核“成功 read_file 事实”时。建议补充 facts 文件相对路径（含会话 ID 文件名）与关键断言字段（如 type/tool/status/value.path），或将其归档后给出可访问路径。
- 判断：本地核对 checks[0].factsSha256="fe71f93122b33fd495fe59384f7840d5ca0a8f712584953bd87edbaf06076cd5"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-examples-results.json:9；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1546 · 不采纳

- 位置：docs/testing/M10.5.9-osv-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：`bomSha256` 是扫描产物与受测件之间唯一的绑定，但这里只存哈希值：未记录对应 SBOM 的文件路径、该哈希是否在本次扫描时由实际文件重新计算（而非人工/上一次拷贝），也未记录 SBOM 生成工具与时间。触发条件：SBOM 重新生成后哈希已变化，而旧的 passing 产物（本文件）仍被引用为门禁证据，即“重放旧证据”场景，依赖集合已变但审查仍通过。建议在产物中写入 `bomPath`、`bomGeneratedAt`、`bomToolVersion`，并在验证时用当前 SBOM 重算 sha256 与之比对。
- 判断：本地核对 bomSha256="369204da6e89e12acfb34ea686b46140cf2390363e17801753035786909f5c1e"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-osv-results.json:4；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1547 · 不采纳

- 位置：docs/testing/M10.5.9-osv-results.json:5；OCR原严重度：high
- 计划：无
- 原意见：`status: PASS` 是派生结论，但文件中没有记录判定规则和 OSV 数据的时点依据：①未记录扫描工具/脚本版本与 OSV 返回的 `modified` 时间；②未说明 withdrawn/忽略规则是否参与计数。触发条件：`capturedAt` 之后 OSV 为同一坐标新增 advisory，或脚本版本改变过滤逻辑时，本文件仍被当作有效门禁证据复用，导致用旧的 PASS 结论为新的依赖集合背书。建议记录 `capturedAt`+工具版本+OSV 数据时点，并在复核时用当前 OSV 数据重新跑一遍 querybatch 比对。
- 判断：本地核对 capturedAt="2026-10-19T08:10:52.242319+00:00"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-osv-results.json:5；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.9-osv-results.json:2

## OCR-1548 · 不采纳

- 位置：docs/testing/M10.5.9-osv-results.json:152；OCR原严重度：high
- 计划：无
- 原意见：`records` 恒为空对象，且每个包的 `vulnerabilities` 数组未保留 OSV 原始返回，`pages`/HTTP 状态码/单查询级 error 也未落盘。OSV querybatch 的实际响应是 `results[].vulns[].id`（详情需再调 `/v1/vulns/{id}`），本文件的结构无法证明这些空数组是服务端真实返回的，而不是请求失败、限流或脚本跳过时默认填空。触发条件：任一 querybatch 返回非 200、某查询带 error 字段，或脚本异常中断——此时 `activeFindings: 0` 与真实命中无法区分，形成假阴性。建议把原始响应体（含每个 query 的 `vulns` 与 error）写入 `records`，或将 `records` 填成 `{坐标: 原始结果}`，否则该证据不可复核。
- 判断：本地核对 records=对象(0键)；packages[0].vulnerabilities=数组(0项)。扫描器逐查询跟进next_page_token、去重token并设100页上限，取所有vuln详情后排除withdrawn；零发现时records={}是预期。已有分页/网络失败离线反例，不能从records空或pages=1推定漏扫。
- 证据：docs/testing/M10.5.9-osv-results.json:152；scripts/scan-osv.py:45；scripts/scan-osv.py:64；scripts/test-release-tools.py；docs/testing/M10.5.9-osv-results.json:13

## OCR-1549 · 不采纳

- 位置：docs/testing/M10.5.9-osv-results.json:153；OCR原严重度：high
- 计划：无
- 原意见：`pages: 1` 没有任何支撑数据：querybatch 的每个查询结果都可能返回 `next_page_token`，调用方必须逐页取完（并把 token 带回下一次请求）才能保证结果完整。此处既未记录是否检查过 `next_page_token`，也未记录翻页次数/令牌快照。触发条件：某个依赖（例如 jackson-databind 这类高关注组件）的 advisory 数量超过单页上限时，第二页不会被拉取，直接漏报并输出 PASS。建议在结果中显式记录每页请求次数与是否存在残留 token（应为 0）。
- 判断：本地核对 capturedAt="2026-10-19T08:10:52.242319+00:00"；status="PASS"。扫描器逐查询跟进next_page_token、去重token并设100页上限，取所有vuln详情后排除withdrawn；零发现时records={}是预期。已有分页/网络失败离线反例，不能从records空或pages=1推定漏扫。
- 证据：docs/testing/M10.5.9-osv-results.json:153；scripts/scan-osv.py:45；scripts/scan-osv.py:64；scripts/test-release-tools.py

## OCR-1550 · 不采纳

- 位置：docs/testing/M10.5.9-osv-results.json:155；OCR原严重度：medium
- 计划：无
- 原意见：`packageCount: 18` 只是自洽计数，文件中没有与被扫描 BOM 的组件清单做比对的证据（既无 BOM 组件总数，也无“BOM 坐标 − 已扫描坐标”的差集）。触发条件：BOM 中含有非 Maven 组件（native 二进制、系统包）、需要转换坐标的 transitive 依赖，或 BOM 在本次扫描后新增依赖时，本文件依然只覆盖 18 个 Maven 坐标并输出 PASS，覆盖率缺口被隐藏。建议同时记录 BOM 组件总数、差集为空的结果，以及被排除项及排除理由。
- 判断：本地核对 capturedAt="2026-10-19T08:10:52.242319+00:00"；status="PASS"。扫描输入明确为外部Maven坐标，packageCount由BOM提取的components集合计算，M9校验另将BOM坐标和扫描包集合逐项比对；非Maven/OS/独立原生不在声明范围。
- 证据：docs/testing/M10.5.9-osv-results.json:155；scripts/scan-osv.py:29；docs/testing/tools/verify-m9-evidence.py:89；third-party/README.md:29

## OCR-1551 · 不采纳

- 位置：docs/testing/M10.5.9-osv-results.json:12；OCR原严重度：medium
- 计划：无
- 原意见：同族 Jackson 构件（core/databind）均为 `2.21.7`，此处 `jackson-annotations` 却记为 `2.21`，版本粒度不一致。需确认这是 Maven 坐标原值还是版本解析时被截断/归一化（例如丢掉补丁号、去掉 classifier 后段）。触发条件：若实际坐标是 `2.21.7` 而被记录成 `2.21`，OSV 会按另一 release 范围做匹配，返回的“无漏洞”无法代表真实构件的结论，形成假阴性。建议记录未经修改的完整 GAV 字符串，并按精确版本查询/比对，同时在产物中补一个版本合法性校验结果。
- 判断：本地核对 capturedAt="2026-10-19T08:10:52.242319+00:00"；status="PASS"。本地保留的BOM与扫描记录均为jackson-annotations 2.21，core/databind为2.21.7；同族补丁号不同不等于版本被截断，无依据把annotations改为2.21.7。
- 证据：docs/testing/M10.5.9-osv-results.json:12；docs/testing/M10.5.9-osv-results.json:13；docs/testing/M9-hosted-linux-sbom.json

## OCR-1552 · 不采纳

- 位置：docs/testing/M10.5.9-native-results.json:15；OCR原严重度：medium
- 计划：无
- 原意见：`sourceSha256` 清单只覆盖各模块 `pom.xml` 与 `*.java`，且没有 `codeVersion`/git commit、JDK 版本、Windows 构建号与架构等字段；对比 M10.5.9-results.json 的清单，它还包含 `LICENSE`、`NOTICE` 以及 `scripts/*`（如脚本化的打包/发布流程），覆盖范围与本文件不一致。触发条件：同一份清单可由不同 commit 或不同 JDK/OS 构建出的产物满足（或源码树中非 Java 资源被改动），`jarSha256` 虽绑定产物，却无法把结论回指到具体提交与工具链，证据不可独立复算。建议补充 commit/`codeVersion`、`jdkVersion`、OS 构建号与架构，并明确本清单的纳入/排除规则（或直接复用主清单的完整覆盖）。
- 判断：本地核对 sourceSha256=对象(305键)；jarSha256="197ba3803566f8cc8cb4ed3ae0d85b604e0e09055205d0fc57104aca24fd179e"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-native-results.json:15；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.9-native-results.json:324

## OCR-1553 · 部分采纳

- 位置：docs/testing/M10.5.9-native-results.json:6；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：check keys describe credential state (available/missing) rather than assertion outcome, producing apparent contradictions, and expectedUnavailable/expectationMet do not define the expectation or authority. Use outcome-oriented keys and record expected vs actual explicitly.
- 判断：本地核对 "missing": true,     "writeRestartRead": true,     "rotateRestartRead": true,。available/missing均是探针检查是否符合预期的布尔，不是凭据库同时存在/缺失。采纳命名为readMissingReturnsEmpty等自描述结果并保留expected/actual，拒绝状态逻辑矛盾指控。
- 证据：docs/testing/M10.5.9-native-results.json:6；docs/testing/M10.5.9-native-results.json:4

## OCR-1554 · 不采纳

- 位置：docs/testing/M10.5.9-native-results.json:13；OCR原严重度：low
- 计划：无
- 原意见：`failureCode` 用空字符串表示“无失败”，与其它证据文件里“字段缺失/为 null 表示无失败”的约定不一致。触发条件：下游按 `if ("failureCode" in obj)` 或按长度/格式校验判断是否发生失败时，会把空串当作“已记录失败码但内容为空”，进而误报失败或跳过校验。建议在 `passed: true` 时置为 `null`（或直接省略该字段），并保持同批次证据文件一致。
- 判断：本地核对 failureCode=""。failureCode空字符串是当前探针成功协议，与passed=true同时解析；未找到按字段存在即当失败的实际消费者。不因假想解析器将约定合法值判缺陷，未来schema统一可作为整体维护。
- 证据：docs/testing/M10.5.9-native-results.json:13；scripts/test-native-credentials.py；docs/testing/M10.5.9-native-results.json:14

## OCR-1555 · 不采纳

- 位置：docs/testing/M10.5.9-native-results.json:14；OCR原严重度：medium
- 计划：无
- 原意见：`credentialStore` 只是探针自报的后端名称，文件中缺少“未回退到文件/内存存储”的断言，也没有任何“未把凭据明文写入日志/工作区”的脱敏自检结果。触发条件：探针在 Windows Credential Manager 调用失败后走到降级路径时，7 项 `checks` 仍可能全为 true，于是本文件会把一次非原生路径的通过当成原生凭据可用性的验收证据。建议补充如 `fallbackUsed: false`、`backendProbe`（例如确认条目确实落在 Credential Manager 中）以及 `secretsLeaked: false` / 脱敏扫描结果等字段，使 `passed: true` 可区分“真原生”与“降级通过”。
- 判断：本地核对 credentialStore="Windows Credential Manager"；checks=对象(7键)。该探针调用SystemCredentials/NativeCredentials原生后端；当前实现不存在意见假设的文件/内存自动回退。应检查真实退出/不可用负例，不能仅因缺少noFallback字段认定会降级成功。
- 证据：docs/testing/M10.5.9-native-results.json:14；harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java；harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java；docs/testing/M10.5.9-native-results.json:4

## OCR-1556 · 不采纳

- 位置：docs/testing/M10.5.9-native-results.json:323；OCR原严重度：low
- 计划：无
- 原意见：`timestamp` 未说明是运行开始还是结束时间，也没有耗时或探针调用参数，配合 `platform: "Windows 11"` 这类粗粒度平台标识，无法判断该结论对应哪一次具体运行。触发条件：后续复用/引用该证据时无法区分同一平台上的多次运行（例如修复前失败运行与修复后通过运行），易把旧运行当作当前源码的验收结论。建议明确字段语义（如 `completedAt`）并补充运行耗时与 JVM/命令摘要。
- 判断：本地核对 timestamp="2026-10-19T08:25:41.640320+00:00"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-native-results.json:323；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1557 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-browser-results.json:2；OCR原严重度：low
- 计划：无
- 原意见：顶层 `status` 是独立字面量，文件中没有与 `checks` 对应的汇总计数（例如 `checksTotal`/`checksFailed`），因此单看本记录无法判断 PASS 是否覆盖了全部预期检查项——若某条检查被删除或漏跑，`status` 仍可保持 PASS 而不被文件自身发现（当前仅依赖外部 collect 脚本的 `len(checks) >= 28` 兜底）。建议由生成器写入 `checksTotal`/`checksFailed` 并与 `status` 冗余校验，使记录可自证。
- 判断：本地核对 status="PASS"；checks=数组(28项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-webkit-browser-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.9-webkit-browser-results.json:8

## OCR-1558 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-browser-results.json:208；OCR原严重度：medium
- 计划：无
- 原意见：截图证据指向的路径位于 `.build-cache/`（见 .gitignore 第 8 行已忽略该目录），因此本条记录里的 sha256 在缓存清理或换机后无法复核，记录本身不能独立证明截图存在且未被替换。建议把关键截图（或截图清单摘要）归档到受版本管理的目录，或在记录中增加“可重现方式”字段（采集脚本 + 命令 + 缓存目录保留说明），使摘要具备可验证性。
- 判断：本地核对 capturedAt="2026-10-19T09:12:32.778Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.9-webkit-browser-results.json:208；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1559 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-browser-results.json:243；OCR原严重度：low
- 计划：无
- 原意见：`logSha256` 没有配套的日志路径字段（对比 `screenshots` 是 path+sha256 成对记录），而仓库中存在多份可能对应的日志（`.build-cache/M10-full-verify.log`、`.build-cache/M10-client-tests.log`、docs/testing 下的多份记录日志），因此无法确定该摘要究竟校验哪一份文件，也无法复核。建议改为结构化对象 `{"path": ..., "sha256": ...}`，与 screenshots 保持一致，便于 `scripts/collect-m10-evidence.py` 之类的校验脚本直接重算。
- 判断：本地核对 logSha256="9972135c2e4bb994296937adfd46ddecfccd99ad17ac1bd1674639c0e7d2287b"；screenshots=数组(8项)。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.9-webkit-browser-results.json:243；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M10.5.9-webkit-browser-results.json:206

## OCR-1560 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-extensions-results.json:148；OCR原严重度：medium
- 计划：无
- 原意见：第 148 行的截图路径指向 `.build-cache/M10.5.9/...`，这属于临时构建缓存目录，通常不入库且可被清理；虽然记录了 sha256（第 149 行），但产物不存在时哈希无法复核，第 2 行的 PASS 结论以及第 90 行“secrets are redacted”这类证据都无法独立复现。建议把截图归档到可保留路径（如 `docs/testing/artifacts/M10.5.9/webkit/`）或在同级字段中记录 CI artifact 引用/保留期说明。
- 判断：本地核对 capturedAt="2026-10-19T09:08:29.237Z"；status="PASS"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.9-webkit-extensions-results.json:148；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1561 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-extensions-results.json:169；OCR原严重度：medium
- 计划：无
- 原意见：第 169-170 行 `paidRequests: 0` 与 `modelRequests: 4` 缺少定义域，无法与第 54 行“reach real SDK model request”、第 86 行“independent read-only model request”以及第 130/134 行“never triggers model request”的 check 对账：4 次请求是本地上游/桩服务还是真实计费 provider 不清楚，而 paidRequests=0 又暗示全部未计费。建议拆分为可对账的字段（如 `localModelRequests`/`providerRequests`/`billedRequests`）并注明口径，否则“请求记账一致性”这一安全/成本结论不可验证。
- 判断：本地核对 capturedAt="2026-10-19T09:08:29.237Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-webkit-extensions-results.json:169；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1562 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-extensions-results.json:2；OCR原严重度：low
- 计划：无
- 原意见：第 2 行（`"status": "PASS"`）是可由 checks 派生的冗余字段，但文件没有 schemaVersion，也没有任何一致性约束：一旦后续某条 check 被改成 `passed: false` 而顶层 status 未同步，下游解析会得到“PASS”的错误结论。建议增加 `schemaVersion` 字段，并明确 status 由 checks 派生（或由校验脚本在生成时断言 `status == "PASS" <=> 全部 checks.passed`）。
- 判断：本地核对 capturedAt="2026-10-19T09:08:29.237Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-webkit-extensions-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1563 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-extensions-results.json:142；OCR原严重度：medium
- 计划：无
- 原意见：第 142 行 check `"no browser exceptions"` 只是一个布尔值，没有覆盖范围证据（覆盖的页面/步骤列表、捕获到的异常数量、宿主重启前后各一次采集）。该套件包含 host restart（第 106、110 行）等跨进程步骤，单条布尔 check 无法证明整个运行期间（含重启后的新页面/新 context）都没有未捕获异常。建议补充 `scope`/`pages`/`exceptionCount` 等字段，或按阶段拆分该 check。
- 判断：本地核对 capturedAt="2026-10-19T09:08:29.237Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-webkit-extensions-results.json:142；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1564 · 部分采纳

- 位置：docs/testing/M10.5.9-webkit-extensions-results.json:175；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：第 175-176、178-179 行 `resources/list`、`resources/read` 各出现两次，但 `transportDiagnostics`（第 164 行）为空、`mcpMethods` 又是无关联信息的扁平字符串数组，因此无法区分这两个重复项分别来自第 82 行“分页/JSON SSE 读取”与第 78 行“resource/template 调用”的预期调用，还是来自静默重试/重放。协议层重放恰是本次评审要求防范的问题，建议把 mcpMethods 改为带归属的对象数组（如 `{method, check, requestId, ok, retry}`），或在 transportDiagnostics 中记录重试/重连事件。
- 判断：本地核对 transportDiagnostics=数组(0项)；mcpMethods=数组(9项)。mcpMethods确仅记录方法名，资源list/read各两次可分别是分页和不同URI读取，transportDiagnostics无关MCP流量。采纳保存脱敏参数类别/分页标志/checkId以复核调用归属，不据重复方法名认定重试或重放。
- 证据：docs/testing/M10.5.9-webkit-extensions-results.json:175；frontend/tests/extensions.mjs；docs/testing/M10.5.9-webkit-extensions-results.json:164；docs/testing/M10.5.9-webkit-extensions-results.json:171

## OCR-1565 · 不采纳

- 位置：docs/testing/M10.5.9-release-evidence.json:14；OCR原严重度：medium
- 计划：无
- 原意见：`verificationLog` 指向 `.dsh-tmp/`，但 `.gitignore` 第 9 行忽略了 `.dsh-tmp/`，AGENTS.md 也明确 `密钥、运行数据、构建缓存及 .dsh-tmp/ 不纳入提交`。也就是说被 `logSha256`（第 16 行）取摘要的那份构建日志既不在版本库中，也会在清理临时目录后消失，事后无法用该摘要复核 `buildResult: SUCCESS` 与 `totals` 的零失败结论，证据链在唯一一次本地构建上是断开的。建议：把最终验证日志（或从 surefire XML 提取的汇总）归档到受版本控制的路径（如 `docs/testing/M10.5.9-final-verify.log`）并在本字段引用该稳定路径，使摘要可重算。
- 判断：本地核对 verificationLog=".dsh-tmp/m1059-final-verify.log"；logSha256="9064ac6796ad92676cdb20d5edff07160d89d888b77424ae2f1100812533ca92"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.9-release-evidence.json:14；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M10.5.9-release-evidence.json:16

## OCR-1566 · 不采纳

- 位置：docs/testing/M10.5.9-release-evidence.json:1446；OCR原严重度：medium
- 计划：无
- 原意见：`jarSha256` 只有摘要值，没有记录制品路径（是 `harness-cli/target/...` 的可执行 jar 还是含依赖的发布包）、构建命令，也没有说明摘要算法与输入范围；同样地每个 `suites[].sha256` 未说明是对测试源码、surefire 报告还是 class 文件取摘要。结果是对本文件的任何字段都无法独立重算，也无法判断“被哈希的 jar”正是“被测试的 jar”（M10.5.8 的多份结果中出现过采集时机早于构建完成而 jar 不一致的情况，说明这条约束需要机器校验）。建议：增加 `artifactPath`、`buildCommand`、`hashes.semantics`（算法=sha256、输入=文件字节）等字段，或直接改用 `{path, algo, sha256}` 结构。
- 判断：本地核对 jarSha256="197ba3803566f8cc8cb4ed3ae0d85b604e0e09055205d0fc57104aca24fd179e"；suites=数组(64项)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-release-evidence.json:1446；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.9-release-evidence.json:23

## OCR-1567 · 不采纳

- 位置：docs/testing/M10.5.9-release-evidence.json:15；OCR原严重度：low
- 计划：无
- 原意见：`runnerOs: "local"` + `ciRunUrl: null` 意味着这份发布证据只来自单台 Windows-11/AMD64 主机的本地构建，没有任何独立构建/签名的 CI 证明。仓库中大量功能是跨平台语义的（进程树终止与句柄、PTY、Windows junction/盘符拒绝、文件锁在“同一进程与第二个 JVM”下的竞争者判定等），仅 Windows 一次通过不足以支撑不带平台限定的发布结论。建议：在清单中显式声明适用范围（如 `"scope": "windows-local-only"`），并在条件允许时补充 Linux/macOS 或 CI 的同一 jar 摘要的验证记录。
- 判断：本地核对 milestone="M10.5.9"；capturedAt="2026-10-19T09:18:51.160293+00:00"。LOCAL_PARTIAL_CI_PENDING、m11Eligible=false与本机runner/local/ciRunUrl=null一致；三平台真实CI已明确待授权执行，此记录不声称已完成整阶段或M11准入。
- 证据：docs/testing/M10.5.9-release-evidence.json:15；docs/testing/M10.5.9-results.json:status；docs/testing/M10.5.9-validation.md:3

## OCR-1568 · 不采纳

- 位置：docs/testing/M10.5.9-release-evidence.json:13；OCR原严重度：high
- 计划：无
- 原意见：证据记录 `workingTreeDirty: true` 的同时给出了确定的 `commit`（e778cb9…）以及 `sourceSha256`/`jarSha256`/`suites[].sha256`。工作区中未提交的改动既没有差异哈希也没有改动文件清单，因此无法证明被哈希、被构建、被测试的源码就是该 commit 的内容，也无法证明同一 commit 能复现这 410 个用例的通过结果（例如未提交的源码改动同样可能让测试变绿）。建议：以干净检出重新构建并重算全部摘要；若确实需要带脏工作区出证，补充 `dirtyFiles` 与 `dirtyDiffSha256`（并对 diff 中涉及的生产源码给出说明），否则该文件的哈希不构成可复现的发布凭据。
- 判断：本地核对 commit="e778cb9fae1b037e78a27adf95a90fc74004cdca"；sourceSha256=对象(305键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-release-evidence.json:13；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.9-release-evidence.json:12；docs/testing/M10.5.9-release-evidence.json:1139

## OCR-1569 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-content-results.json:208；OCR原严重度：low
- 计划：无
- 原意见：`transportDiagnostics` 为空数组，而第 174-179 行的安全断言（仅 authenticated loopback、UI/浏览器存储无秘密、预览已脱敏）全部为 PASS。空诊断与“诊断采集未启用”无法区分，因此这些安全结论缺少可复核的传输侧证据；若采集链本身失效（例如同样依赖未落地的 `.build-cache` 产物），也会呈现为空数组。建议在无异常时也记录观测到的请求条数/目标地址类别，或显式记录诊断采集已启用的标志。
- 判断：本地核对 transportDiagnostics=数组(0项)。transportDiagnostics仅收受控停机/断网窗口允许诊断，不是provider鉴权流量日志。为空与模型调用存在不矛盾，不能将无诊断降级SKIP；鉴权有独立wire.every及API状态断言。
- 证据：docs/testing/M10.5.9-webkit-content-results.json:208；frontend/tests/runtime.mjs；frontend/tests/questions.mjs；frontend/tests/search.mjs

## OCR-1570 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-content-results.json:188；OCR原严重度：medium
- 计划：无
- 原意见：截图证据指向 `.build-cache/...` 下的临时目录（仓库中不存在这些文件，`file_find` 对 `content-M10.5.6-stream.png` 无匹配），且文件名仍是 `content-M10.5.6-*.png`，而目录已改为 `M10.5.9`。两个问题叠加导致：(1) 报告里的 5 个 sha256 在缓存清理或换机后完全无法复核；(2) 无法确认截图确实来自本次 WebKit 运行而非沿用的旧里程碑产物。建议把截图归档到受版本控制的位置（如 `docs/testing/artifacts/M10.5.9/webkit/`），并让校验脚本同时校验文件存在性与哈希一致性。
- 判断：本地核对 capturedAt="2026-10-19T09:12:49.514Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.9-webkit-content-results.json:188；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1571 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-content-results.json:217；OCR原严重度：low
- 计划：无
- 原意见：本文件的 `logSha256` 与 `docs/testing/M10.5.9-chromium-content-results.json` 中的值逐字节相同，而两个引擎是各自独立运行（截图哈希明显不同）。若该字段代表本次运行的日志，两个不同引擎产出完全相同的日志哈希不可复现，说明日志引用很可能是复制/指向固定文件，并非本次 WebKit 运行的证据，也就无法用它判断 WebKit 侧是否存在失败或跳过。建议每次运行生成独立日志文件并记录其哈希。
- 判断：本地核对 logSha256="810be90b24df990b480840f231211dffb9536c75a5bef985373830f7a6ff5a21"。相同内容会有相同摘要，不能反推未执行或复制。浏览器自动同源进入使connection/welcome可已是同一状态；夹具日志仅打印确定性的PASS名称，不同引擎/复跑可逐字相同。应核对执行入口和制品来源，不能要求截图/日志哈希强制互异。
- 证据：docs/testing/M10.5.9-webkit-content-results.json:217；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1572 · 采纳

- 位置：docs/testing/M10.5.9-webkit-content-results.json:85；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：第 74、86、90 行三个拒绝预览用例的名称完全相同（均为 `denied preview private.txt`），但对应的是不同路径的拒绝场景。根因在测试源 `frontend/tests/content.mjs:109`：名称用 `path.split('/').at(-1)` 生成，不同目录/不同根下的同名文件会折叠成同一标识。这使本报告无法区分究竟是哪个用例通过：若其中一个被跳过、抛错被吞、或被同一名称的上一条结果覆盖，聚合结果仍会显示 45 项全部 PASS，证据不可追溯。建议名称携带 fixture 相对路径或显式 case id（如 `denied preview secondary/private.txt`）。
- 判断：本地核对 capturedAt="2026-10-19T09:12:49.514Z"；status="PASS"。content.mjs用path.split(/).at(-1)作为断言名，多个不同目录private.txt实际同名。数组未因此丢失用例，但日志无法区分场景；应使用不泄露用户根的fixture相对路径或稳定caseId，并保证必需ID集合唯一。
- 证据：docs/testing/M10.5.9-webkit-content-results.json:85；frontend/tests/content.mjs:109

## OCR-1573 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-development-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：本文件把 `status` 固定写为 `PASS`，但 `checks[]` 只有 `{name, passed}` 两个字段，缺少可表达 UNKNOWN/SKIP/NOT_RUN 的状态枚举，也没有 checks 总数、跳过数和 unknown 计数。也就是说，一旦某个检查被跳过或未执行（被直接从数组里删掉），这份记录自身仍然读作 PASS，只能依赖 scripts/collect-m1059-evidence.py:54 的 `EXPECTED[key[1]]`（development=14）外部计数来兜底，证据记录本身不满足“UNKNOWN 不得被折叠为 PASS”。建议把 status 改为由 checks 聚合得出，并新增 `checksTotal`/`skipped`/`unknown`（且要求为 0）等字段，使记录自证。
- 判断：本地核对 status="PASS"；checks=数组(14项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-webkit-development-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.9-webkit-development-results.json:6

## OCR-1574 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-development-results.json:66；OCR原严重度：medium
- 计划：无
- 原意见：截图路径指向 `.build-cache/`，而该目录在 .gitignore:8 中被整体忽略；scripts/archive-m1059-local.py:19-22 归档到 docs/testing 时只复制 JSON 记录，不复制截图。结果是这条 hash 在仓库内没有任何可核对的实体文件：任何一个干净 checkout 的复核者都无法验证 `sha256` 是否与真实截图一致，也无法确认截图确实来自 webkit 引擎。建议把截图随记录一起归档（或至少把 build-cache 快照作为发布物的一部分并在本文件记录归档路径）。
- 判断：本地核对 screenshots[0].sha256="a33378d0b44d18e9dd8876cb5d4fab0f5b81c34bb1e8684b17187c7b2a6bd60d"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.9-webkit-development-results.json:66；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M10.5.9-webkit-development-results.json:67

## OCR-1575 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-development-results.json:80；OCR原严重度：medium
- 计划：无
- 原意见：该 `logSha256` 与 docs/testing/M10.5.9-chromium-development-results.json:80 的取值完全相同（capturedAt 相差约 3 分钟，说明是两次独立运行），即 webkit 与 chromium 两个引擎的 development 运行产生了逐字节相同的日志。这意味着被哈希的日志不包含任何引擎/版本标识，日志无法用于交叉验证“这次确实是 webkit 跑的”，引擎身份只剩 `browserEngine` 一个自报字段；同时本文件没有记录 log 的路径，log 也未归档，hash 无法定位和复核。建议在 fixture 日志中输出引擎名与浏览器版本，并在记录中记录 log 路径后一并归档。
- 判断：本地核对 logSha256="51dca37e2b3882993cf4c54dfc11e45846442c6d8fd486e061b5d1fb5dcb7422"；browserEngine="webkit"。相同内容会有相同摘要，不能反推未执行或复制。浏览器自动同源进入使connection/welcome可已是同一状态；夹具日志仅打印确定性的PASS名称，不同引擎/复跑可逐字相同。应核对执行入口和制品来源，不能要求截图/日志哈希强制互异。
- 证据：docs/testing/M10.5.9-webkit-development-results.json:80；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20；docs/testing/M10.5.9-webkit-development-results.json:4

## OCR-1576 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-development-results.json:4；OCR原严重度：low
- 计划：无
- 原意见：复现元数据不足：只有引擎名与 `browserVersion: "26.5"`，没有记录操作系统/内核、依赖与工具链版本、JAR 对应提交、以及重跑命令（`scripts/run-workbench-matrix.mjs` 的命令行/环境变量如 `JHARNESS_MATRIX_ENGINES`），`"26.5"` 也没有注明是 Safari 版本还是 WebKit build。独立复核者据此无法复现同一 PASS。建议补充 platform、commit、重跑命令以及 browserVersion 的来源字段。
- 判断：本地核对 capturedAt="2026-10-19T09:09:06.698Z"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-webkit-development-results.json:4；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1577 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-development-results.json:77；OCR原严重度：low
- 计划：无
- 原意见：安全类断言的证据缺失：检查项“all model traffic is authorized redacted loopback”只有布尔 `passed`，`modelRequests: 12` / `paidRequests: 0` 也只有计数，没有记录实际命中的 endpoint 主机（脱敏后的 host/path）、脱敏断言次数或模型上下文摘要（对比 SDK 侧记录有 `allAuthorized`/`modelContextRedacted` 字段）。一旦应用回退到外部端点或脱敏失效，这份 PASS 记录无法被事后复核发现。建议增加 loopback 主机清单与脱敏命中计数字段，并断言 `modelRequests` 与日志中实际请求数一致。
- 判断：本地核对 checks[0].passed=true；modelRequests=12。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-webkit-development-results.json:77；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M10.5.9-webkit-development-results.json:9；docs/testing/M10.5.9-webkit-development-results.json:76

## OCR-1578 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-diagnosis-results.json:50；OCR原严重度：medium
- 计划：无
- 原意见：checks[] 只保留 name 与 passed，且整份 PASS 回执仅在全部 check 通过后才写出（frontend/tests/diagnosis.mjs 第 20 行 check() 断言失败即抛异常、第 93 行才落盘），因此 “no browser runtime errors”“fixture closes with zero paid requests”“never replay” 等结论在回执里没有任何可审计的观测值；下游校验（scripts/collect-m1059-evidence.py 第 54–55 行）也只核对 len(checks)==13 与 passed is True，所以 13 个 check 中某一条被替换/重命名、断言强度退化时仍会绿。触发条件：修改 diagnosis.mjs 保持 13 次 check 但去掉真实断言 → 回执照样 PASS。建议每条 check 附带观测证据（错误条目数、paidRequests、POST /v1/tasks 计数、事件 id 前后快照哈希等），并对 check 名称做白名单比对。
- 判断：本地核对 capturedAt="2026-10-19T09:07:02.262Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-webkit-diagnosis-results.json:50；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1579 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-diagnosis-results.json:60；OCR原严重度：medium
- 计划：无
- 原意见：证据不可复核（可复现性缺陷）：screenshots[].path 与文件末尾的 logSha256 都指向 .build-cache/M10.5.9/browser/webkit/…，而 .build-cache/ 已被 .gitignore 忽略，因此提交进 docs/testing 的这份 PASS 回执所引用的 PNG 与 .log 永远不会进入仓库。触发条件：缓存被清理或在新 clone 上复核时，按此路径读取截图/日志为空，sha256 必然无法校验（scripts/collect-m1059-evidence.py 第 60 行的 sha(ROOT/shot['path']) 只有在同一工作树缓存仍在时才成立）。建议把截图随回执提交到 docs/testing/<milestone>/ 并改用仓库相对路径，或显式标注“证据仅本地留存、不可离线复核”。
- 判断：本地核对 capturedAt="2026-10-19T09:07:02.262Z"；status="PASS"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.9-webkit-diagnosis-results.json:60；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1580 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-diagnosis-results.json:80；OCR原严重度：low
- 计划：无
- 原意见：runtime.modelRequests=5 与 paidRequests=0 并列存放，但回执没有标记这 5 次模型请求是由进程内假模型承接（M10BrowserHost 的 models 计数）还是外部服务，读侧无法区分“5 次真实请求但付费计数被置 0”与“0 次付费请求”这两种语义。建议在 runtime 里加一个模型来源字段（如 source: "fixture"）并记录 paidRequests 的取值依据，使两项计数可自证一致。
- 判断：本地核对 capturedAt="2026-10-19T09:07:02.262Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-webkit-diagnosis-results.json:80；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1581 · 不采纳

- 位置：docs/testing/M10.5.9-sdk-source-inventory.json:30；OCR原严重度：medium
- 计划：无
- 原意见：licenseSha256 和 bomSha256 都是裸摘要，没有记录目标工件路径：前者实际对应仓库根 LICENSE，后者对应 target/sdk-bom.json（脚本第 37、42 行生成）。触发条件：校验方把 bomSha256 与 docs/testing 下其它 BOM 文件（如 M10-frontend-sbom.json）比较会得到错误结论；同时 target/sdk-bom.json 未归档到 docs/testing（仓库中不存在 M10.5.9-sdk-bom.json），仅凭本快照无法复算 bomSha256。建议改为带路径的结构（如 bom: {path, sha256}、license: {path, sha256}），并把 sdk-bom.json 一并归档。
- 判断：本地核对 "licenseSha256": "c71d239df91726fc519c6eb72d318ec65820627232b2f796219e87dcf35d0ab4",   "protocolVersion": "1.0",   "bomSha256。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-sdk-source-inventory.json:30；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1582 · 不采纳

- 位置：docs/testing/M10.5.9-sdk-source-inventory.json:19；OCR原严重度：high
- 计划：无
- 原意见：sourceSha256 只覆盖了 9 个文件，遗漏 sdk/tests/client.py、sdk/tests/client.ts、sdk/tests/examples.mjs、sdk/tests/run.mjs。对照 docs/testing/M10.5.9-results.json 第 1770-1773 行的 SDK 哈希清单（以及 M10.5.8 的 sdkSha256），这 4 个测试文件此前都被纳入哈希，且 client.ts 的哈希在 M10.5.8→M10.5.9 之间已经变化，说明测试文件并非冻结。触发条件：任何对 sdk/tests/* 的修改都不会改变本清单，基于本文件做完整性校验会误判通过，测试中的协议版本拒绝、UNKNOWN 不重放、脱敏等断言可被静默替换。建议把 sdk/tests/*.py、*.ts、*.mjs 纳入 sourceSha256，或显式声明排除范围并要求校验方对目录做全量比对。
- 判断：本地核对 "sourceSha256": {     "sdk/README.md": "34abc52cf47cdf19ab5da87154584ffe907953f19787a1da70d93e8ad9e9c29b",     "sdk/package.j。SDK源码清单范围是可分发SDK和示例，不是所有测试夹具；总验收另外记录SDK测试/前端输入。测试文件未放入runtime inventory不能证明零运行依赖声明错误。
- 证据：docs/testing/M10.5.9-sdk-source-inventory.json:19；docs/testing/M10.5.9-results.json:sdkSha256

## OCR-1583 · 部分采纳

- 位置：docs/testing/M10.5.9-sdk-source-inventory.json:3；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：Dependency/import inventory covers only core SDK files, not examples or other hashed sources. A third-party import added in examples would leave externalRuntimeDependencyCount at 0; enumerate imports across all hashed sources.
- 判断：本地核对 "externalRuntimeDependencyCount": 0,   "pythonImports": [     "__future__",。本地库存确哈希示例但import统计只解析核心实现；新增示例外部导入可能漏计。采纳按声明分发范围解析所有Python/TS文件（测试另列）并加未知外部导入反例。
- 证据：docs/testing/M10.5.9-sdk-source-inventory.json:3；docs/testing/M10.5.9-sdk-source-inventory.json:19；scripts/sdk-inventory.py

## OCR-1584 · 不采纳

- 位置：docs/testing/M10.5.9-sdk-source-inventory.json:7；OCR原严重度：low
- 计划：无
- 原意见：该条目不是实际导入名：源码为 `import concurrent.futures`（sdk/python/jharness_sdk.py 第 4 行），按顶层包截断后丢失了 futures 子模块。触发条件：校验方若按本列表逐个 importlib 导入以确认标准库可用性，只会加载 concurrent 包，不会验证 concurrent.futures 的线程池/进程池实现。建议记录完整模块名 concurrent.futures。
- 判断：本地核对 "concurrent",     "json",     "os",。pythonImports是顶层模块用于标准库/外部依赖分类，concurrent.futures归concurrent正确；没有消费者承诺逐子模块可加载测试，截去子模块不影响外部依赖数。
- 证据：docs/testing/M10.5.9-sdk-source-inventory.json:7；sdk/python/jharness_sdk.py:4

## OCR-1585 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-settings-results.json:113；OCR原严重度：medium
- 计划：无
- 原意见：截图只记录路径与 sha256，没有记录生成时间或"本轮 WebKit 运行内重新计算并比对通过"的标记。触发条件：.build-cache 下残留上一轮的 png，路径与历史哈希依旧成立，报告无法区分"本轮产出"与"陈旧产物"，导致 UI/移动端布局类用例的证据失效。建议增加 generatedAt 与实际校验结果（verified:true / 比对时间戳），或写入产生该产物的 run id。
- 判断：本地核对 "path": ".build-cache/M10.5.9/browser/webkit/settings-M10-settings-plugins.png",       "sha256": "eb8c8b6633b938baeb7226ca457。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.9-webkit-settings-results.json:113；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1586 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-settings-results.json:140；OCR原严重度：medium
- 计划：无
- 原意见：只给出计数，没有记录每次模型请求的目标 host/endpoint 以及拦截规则命中情况。触发条件：若计数只统计被 mock/代理接管的请求，而某条请求实际发往真实供应商域名，报告依然会显示 modelRequests=4、paidRequests=0，"未产生付费请求"的结论不可验证。建议同时记录请求目标域名白名单与 allow/block 计数（或直接记录 host 列表）。
- 判断：本地核对 "modelRequests": 4,   "paidRequests": 0,   "jarSha256": "197ba3803566f8cc8cb4ed3ae0d85b604e0e09055205d0fc57104aca24fd179e",。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-webkit-settings-results.json:140；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1587 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-settings-results.json:143；OCR原严重度：medium
- 计划：无
- 原意见：仅声明清理完成，缺少清理对象与执行路径信息：没有记录被清理的凭据 target/环境变量数量，也没有标注清理发生在 finally 还是仅在成功路径；若 restart 或失败分支提前 return，该字段仍会为 true。建议补充 cleanedTargets 计数与 onFailure/onRestart 覆盖标记。
- 判断：本地核对 "fixtureCredentialsCleaned": true,   "browserEngine": "webkit",   "fixture": "settings",。fixtureCredentialsCleaned并非未验残留就常量通过：settings finally中启动Cleanup并assert退出0后才写报告，Cleanup删除配置引用targets后逐项vault.read为空，否则抛异常。清理失败不会落该true；原意见忽略实际回读。
- 证据：docs/testing/M10.5.9-webkit-settings-results.json:143；frontend/tests/settings.mjs:155；harness-cli/src/test/java/io/jharness/cli/WorkbenchCredentialCleanup.java:28

## OCR-1588 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-settings-results.json:39；OCR原严重度：high
- 计划：无
- 原意见：脱敏类用例只有通过布尔值，没有记录断言实际观测到的证据（扫描了哪些存储键、命中次数、事件流/日志中的命中条数、匹配到的凭据前缀位置等）。触发条件：选择器或扫描范围失效、断言被 skip 或用例空跑时，报告仍然输出 passed:true，无法据此复现"密钥未落入浏览器存储/事件输出/宿主 stdout"的结论。建议在该项内增加 observed/evidence 字段（如 scannedKeys 数量、secretHits=0、logOffset 等），使 39-40、63-64、75-76 三处安全断言可核验。
- 判断：本地核对 "name": "secret input cleared and browser storage empty of secret",       "passed": true     },。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-webkit-settings-results.json:39；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1589 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-settings-results.json:137；OCR原严重度：high
- 计划：无
- 原意见：该诊断是一条自由文本，既没有关联到具体 check 名称/用例 ID，也没有原始请求的 origin 与预期结果，因此无法判断它是"settings rejects cross-origin mutation"的预期负向结果，还是一条主流程请求被访问控制意外拦截后被 passed 掩盖。另外这里直接写入了完整任务 ID（web-2854cb...）与临时端口，属于内部会话标识，不应原样落库到仓库文档。建议结构化记录 {check, origin, expectBlocked:true, actualStatus}，并对 task id 做截断/哈希。
- 判断：本地核对 "transportDiagnostics": [     "/127.0.0.1:50716/v1/tasks/web-2854cb2193857b4b9c8dcf54da3f624a-f0712deac066b23485b8cadab4923af。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-webkit-settings-results.json:137；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1590 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-questions-results.json:6；OCR原严重度：medium
- 计划：无
- 原意见：第5-108行：全部 26 项检查只保留 `name` 与 `passed: true`，没有断言细节、耗时或对应日志片段。像 `draft writes serialize while prior save is delayed`（写串行化）、`double click submits one durable answer`（幂等）、`host restart marks unfinished question interrupted without replay`（重启恢复且无重放）这类时序/并发/UNKNOWN 状态断言，无法从该清单判断实际观察到的值（例如 POST 次数、串行化区间、重放前后 wire 长度）。建议为每项检查补充关键观测值（期望值/实际值或日志行号），使证据可独立复核，而不是依赖“运行时断言未抛错”这一隐含前提。
- 判断：本地核对 checks[0].name="real SDK ask_user exposes preselected recommendation without submitting"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-webkit-questions-results.json:6；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1591 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-questions-results.json:124；OCR原严重度：medium
- 计划：无
- 原意见：第124行：`transportDiagnostics` 为空，但第102行的检查 `all provider traffic is authenticated loopback` 断言为 true，且 `modelRequests` 为 18，说明确实发生过模型请求。在对应夹具中该数组只收集“被测主机停机/注入离线”的 pageerror 文本（frontend/tests/questions.mjs:37、49），并非请求鉴权/回环校验的诊断结果，因此“空数组”既可能是零违规，也可能是采集未启用，无法据此复核该安全断言。建议在此记录可复核的传输事实（如 provider 请求的 origin 集合、带鉴权头计数、拒绝的非回环请求数），而不是让安全断言只以 `passed: true` 落盘。
- 判断：本地核对 transportDiagnostics=数组(0项)；modelRequests=18。transportDiagnostics仅收受控停机/断网窗口允许诊断，不是provider鉴权流量日志。为空与模型调用存在不矛盾，不能将无诊断降级SKIP；鉴权有独立wire.every及API状态断言。
- 证据：docs/testing/M10.5.9-webkit-questions-results.json:124；frontend/tests/runtime.mjs；frontend/tests/questions.mjs；frontend/tests/search.mjs；docs/testing/M10.5.9-webkit-questions-results.json:126

## OCR-1592 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-questions-results.json:112；OCR原严重度：medium
- 计划：无
- 原意见：第112/116/120行：截图的 `path` 指向 `.build-cache/` 目录，而 `.gitignore` 第8行已将 `.build-cache/` 排除在版本库之外，因此提交到 docs/testing 的这份证据中的截图 sha256（以及第133行 `logSha256` 指向的日志）在仓库内无法被任何人重新计算校验，等于不可验证的证据。建议归档时把截图/日志一并提交到受版本控制的证据目录，或至少在 `M10.5.9-results.json` 中记录这些产物的受控存储位置与校验方式。
- 判断：本地核对 screenshots[0].path=".build-cache/M10.5.9/browser/webkit/questions-M10.5.5-questions-desktop.png"；logSha256="e2e4b84f86d5fa189d191eaabcb6683457509faf4cea80d73b3408b95b0e49b3"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.9-webkit-questions-results.json:112；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29；docs/testing/M10.5.9-webkit-questions-results.json:133

## OCR-1593 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-questions-results.json:3；OCR原严重度：low
- 计划：无
- 原意见：第2-3、127-134行：该记录缺少源提交号（commit）、平台/操作系统、Playwright 版本与执行命令，只有时间戳和若干哈希。单独归档后无法确认这份 PASS 对应哪个源码修订，也无法复现；若矩阵记录（matrix.json/runner.json）是唯一溯源来源，建议在本文件中一并携带 commit、platform 与 fixture 脚本标识，或显式引用其受控路径与摘要。
- 判断：本地核对 capturedAt="2026-10-19T09:08:02.196Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-webkit-questions-results.json:3；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1594 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-questions-results.json:128；OCR原严重度：low
- 计划：无
- 原意见：第128行：键名 `browser` 在此处存放的是引擎版本号，与同批次其他记录（如 docs/testing/M10.5.9-webkit-history-results.json 使用 `browserVersion`，且 `browserEngine` 才表示引擎名）不一致；采集脚本 scripts/collect-m1059-evidence.py:56 通过 `r.get('browserVersion', r.get('browser'))` 回退读取，会让这类旧键名记录在未做版本格式校验的情况下通过“Unknown browser version”检查。建议统一为 `browserVersion`，并保持 `browserEngine` 独立表示引擎，避免消费方误读。
- 判断：本地核对 browser="26.5"；browserEngine="webkit"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-webkit-questions-results.json:128；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.9-webkit-questions-results.json:130

## OCR-1595 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-models-results.json:109；OCR原严重度：low
- 计划：无
- 原意见：本记录沿用 fixture 内部的旧字段名 date/browser，而汇总产物与多数同批次记录使用 capturedAt/browserVersion（scripts/finalize-workbench-matrix.mjs:20 写 capturedAt，collect 侧 scripts/collect-m1059-evidence.py:56 必须写成 r.get('browserVersion', r.get('browser')) 才能读取）。任何按规范字段名（capturedAt/browserVersion）消费 docs/testing 证据的脚本会在本文件上读到 undefined，从而把该运行的浏览器版本/采集时间当作缺失或把 status 误判。建议在生成端（frontend/tests/models.mjs 的 result 对象）统一为 capturedAt 与 browserVersion，或在 documents 证据中补齐这两个规范键，避免下游依赖 fallback。
- 判断：本地核对 status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-webkit-models-results.json:109；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1596 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-models-results.json:89；OCR原严重度：medium
- 计划：无
- 原意见：安全类断言（“saved settings and session events contain no API key”“all protocol calls authenticate only through headers”）在本记录中只有 name/passed 布尔值，没有任何被检查事实的摘要。这类断言本质是“不存在/全部满足”，当采集到的 wire、settings、session 事件集合为空或退化时同样会为 true，读者无法从本文件区分真实通过和被空集合vacuous通过，只能整体重跑 fixture 才能复核。M10.5.8 的 examples 证据已在 check 上记录 factsSha256（scripts/collect-m1059-evidence.py:67 也校验该字段），建议对这两项敏感断言同样记录脱敏后事实摘要（例如 wire 认证方式集合、执行 read_file 的参数摘要、设置/事件扫描结果摘要），使 PASS 可被独立复核且能检出实现漂移。
- 判断：本地核对 status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-webkit-models-results.json:89；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1597 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-search-results.json:120；OCR原严重度：low
- 计划：无
- 原意见：该 M10.5.9 运行记录中的两张截图文件名仍是 M10.5.8 前缀（search-M10.5.8-search-desktop.png / -mobile.png），位于 M10.5.9 缓存目录内。fixture 自身（frontend/tests/search.mjs）以 M10.5.8 命名输出、由 matrix 脚本复制到本目录，因此字节级 hash 校验仍成立，不算篡改；但文件名会让人误判为上一版本的旧证据，削弱本记录的可追溯性。建议在采集时把截图复制为与当前 fixture/版本一致的文件名（或附带 runId/capturedAt），避免旧版标签在本版本证据中被复用。
- 判断：本地核对 capturedAt="2026-10-19T09:08:42.140Z"；status="PASS"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M10.5.9-webkit-search-results.json:120；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1598 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-search-results.json:136；OCR原严重度：low
- 计划：无
- 原意见：fixtureSourceSha256 仅对 frontend/tests/search.mjs 求哈希，而该 fixture 的实际行为还依赖被 import 的 frontend/tests/runtime.mjs（browserType 的引擎选择、stoppedHostDiagnostic 决定哪些 pageerror 进入 transportDiagnostics 而非 errors）。若 runtime.mjs 变更，本记录中的 fixture 源码哈希不会变化，证据无法发现共享运行时的漂移，进而可能让本文件声明的 PASS/空 transportDiagnostics 语义在复跑时不一致。建议把 fixture 及其直接依赖（runtime.mjs）的哈希一并记录，或在采集脚本中锁定依赖哈希。
- 判断：本地核对 capturedAt="2026-10-19T09:08:42.140Z"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.9-webkit-search-results.json:136；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1599 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-history-results.json:110；OCR原严重度：medium
- 计划：无
- 原意见：logSha256 是本文件全部 19 项 PASS 的唯一原始溯源（scripts/run-workbench-matrix.mjs 取 .build-cache/M10.5.9/browser/webkit/history.log 的摘要），但记录中没有该日志的路径/文件名键，jarSha256 也没有对应制品路径；而 .build-cache/ 已被 .gitignore 忽略，screenshots 中的路径同样位于该目录。因此提交到仓库后，日志与截图哈希都无法被重新计算，PASS 不可复现、不可证伪。建议增加 logPath 与 jarPath（如 harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar），或把日志/截图作为 CI artifact 与本次记录一起归档并在文件中写明引用。
- 判断：本地核对 capturedAt="2026-10-19T09:07:27.183Z"；status="PASS"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M10.5.9-webkit-history-results.json:110；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1600 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-history-results.json:104；OCR原严重度：medium
- 计划：无
- 原意见：结果同时记录了 4 条“无模型调用/无模型执行”的检查（workspace 注册、fork creates independent session without executing model、restart ... without model request、cold duplicate submission ... without execution）与全局 modelRequests=138。138 实际来自 69 个已执行任务×2 次请求（beta/alpha/outside/65 cycles/after-restart），并非上述检查项产生，因此不是直接矛盾；但本文件只保存了终值，没有记录这些检查各自的 wire 基线（beforeFork/beforeRestart），审计者无法从证据文件判断“零执行”结论是否成立，也无法发现计数器被重置或被重启后的主机重新计数。建议按检查名记录 wireBefore/wireAfter，使“无模型调用”类 PASS 可被独立复核。
- 判断：本地核对 capturedAt="2026-10-19T09:07:27.183Z"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M10.5.9-webkit-history-results.json:104；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1601 · 不采纳

- 位置：docs/testing/M10.5.9-webkit-history-results.json:5；OCR原严重度：low
- 计划：无
- 原意见：该结果只固定了引擎名与 "26.5"，缺少 browserChannel / WebKit 构建修订（同目录 search/extensions 结果均记录了 browserChannel）。不同 Playwright 版本附带的 WebKit 可能同为 "26.5" 口径但实际二进制不同，届时无法判断本 PASS 对应哪个 WebKit 构建，也无法复现。建议补充 browserChannel 与 Playwright/WebKit revision 字段。
- 判断：本地核对 capturedAt="2026-10-19T09:07:27.183Z"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M10.5.9-webkit-history-results.json:5；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1602 · 采纳

- 位置：docs/testing/M10.5.9-webkit-history-results.json:64；OCR原严重度：medium
- 计划：R4-04 / P2
- 原意见：该条 PASS 名称为“工作区选择器可用键盘聚焦”，但对应断言只检查元素 tagName === 'SELECT'（frontend/tests/history.mjs:112），<select> 恒为真，因此这条 PASS 并未证明实际可聚焦/可键盘操作（例如 disabled、tabindex=-1、被浮层遮挡或不在 tab 序列时同样会通过）。建议夹具改为断言 document.activeElement 落在该元素上、且 Tab 后焦点可进入/离开，避免该无障碍结论被高估。
- 判断：本地核对 capturedAt="2026-10-19T09:07:27.183Z"；status="PASS"。该项只验证tagName为SELECT，不能证明元素disabled/tabindex或实际Tab/focus可达。需要真实键盘聚焦并核对document.activeElement。
- 证据：docs/testing/M10.5.9-webkit-history-results.json:64；frontend/tests/history.mjs:112

## OCR-1603 · 不采纳

- 位置：docs/testing/M3-results.json:10；OCR原严重度：low
- 计划：无
- 原意见：证据可复现性不足：`logSha256` 声称绑定原始构建日志，但 `log` 指向的 `.build-cache/M3-verify.log` 位于已被 .gitignore 排除的目录（仓库中不存在该文件）。因此任何仅拿到仓库的读者都无法重算或核对 `logSha256`，该字段无法起到防篡改作用；叠加 `codeVersion` 为 "uncommitted"、`remoteCiResult` 为 "not run"，本记录实际上只能作为本机一次性证据。建议将日志作为 CI artifact 长期归档并在文件中记录可访问位置，或把日志（必要时压缩/截断）随结果一并纳入版本库。
- 判断：本地核对 logSha256="6b5b886eb27bdeb7cbd3f9067eafd80df6aa002d831e1259804471e589ff4f4e"；log=".build-cache/M3-verify.log"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M3-results.json:10；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M3-results.json:11

## OCR-1604 · 不采纳

- 位置：docs/testing/M3-results.json:350；OCR原严重度：low
- 计划：无
- 原意见：快照哈希清单不完整：`sourceSha256` 仅覆盖各模块 `src/**/*.java`、模块 pom 和固定构建文件，未包含同批次参与验证的 `scripts/jharness-cancel.ps1`（M3 验证记录明确声明该脚本另行通过 PowerShell 验证）。由于 `codeVersion` 为 "uncommitted"，快照识别完全依赖此清单，而被验证脚本的变更无法被检出，可能导致“用哈希指认的源码快照”与实际被验证内容不一致。建议把参与验证的脚本（以及生成该证据的 `scripts/collect-test-evidence.ps1`）纳入哈希清单，或在记录中显式声明清单覆盖范围。
- 判断：本地核对 sourceSha256=对象(154键)；codeVersion="uncommitted; identify this tested source snapshot by sourceSha256"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M3-results.json:350；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M3-results.json:9

## OCR-1605 · 不采纳

- 位置：docs/testing/M4-results.json:10；OCR原严重度：medium
- 计划：无
- 原意见：`.build-cache/` 已被 `.gitignore`（第 8 行）排除，因此 `log` 指向的日志不在仓库内，`logSha256` 只能证明“本地曾存在某个文件”，任何第三方都无法重算该哈希；再叠加 `remoteCiResult: "not run"`，`buildResult: SUCCESS` 与 156 项计数就没有任何可独立核对的来源，日志一旦丢失或与清单不符也无法被发现。建议将脱敏后的验证日志作为 CI artifact 归档并在清单中给出可获取位置（或至少记录日志字节数、行数及各模块 Surefire XML 的哈希）。
- 判断：本地核对 log=".build-cache/M4-verify.log"；logSha256="1ddfdc2aa110d08bc2a55a711c8ba59ae842bfd2508a85f42b9aecaae44aae60"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M4-results.json:10；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M4-results.json:11

## OCR-1606 · 不采纳

- 位置：docs/testing/M4-results.json:9；OCR原严重度：medium
- 计划：无
- 原意见：“用 sourceSha256 识别被测快照”的说法强于实际覆盖范围：该映射只包含各模块 `src/**/*.java`、模块 pom、根 pom 与少量构建脚本，`src/main/resources`、`.mvn/` 下其他配置、Surefire/编译器参数之外的其他输入都不在其中；而且哈希是在构建结束后采集，采集逻辑只比较“文件 mtime ≤ 日志写入时间”，构建期间发生的编辑不会被判为不一致。建议补充 `git write-tree`/`git stash create` 生成的工作树对象哈希，并把所有会被编译或参与测试的非 Java 输入纳入映射，这样才能在未提交状态下精确比对。
- 判断：本地核对 milestone="M4"；capturedAt="2026-10-14T18:13:14.8622322+08:00"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M4-results.json:9；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1607 · 不采纳

- 位置：docs/testing/M4-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：证据命令把格式化“写操作”`spotless:apply` 与 `clean verify` 放在同一次构建里，构建期间源码树会被改写。由于 `sourceSha256` 是构建结束后采集的，它可能描述的是格式化后的字节，而人工审查/准备提交的原始字节从未被编译与测试；同时 `codeVersion` 声明未提交，无法用 commit 交叉核对，因此清单无法证明“被审查的源码 = 被测试的源码”，也无法支撑 validation 文档中“通过格式检查”的说法（apply 只保证最终文件合规，不保证输入无需修改）。建议证据运行改用 `spotless:check`（格式化单独执行），并同时记录运行前后的工作树状态，使快照可复核。
- 判断：本地核对 sourceSha256=对象(176键)；codeVersion="uncommitted; identify this tested source snapshot by sourceSha256"。spotless:apply是显式独立步骤，随后clean verify检查格式/构建；清单记录格式化后实际被测试的字节正是正确口径。没有证明构建中途改变语义来源。
- 证据：docs/testing/M4-results.json:4；.agent:验证流程；docs/testing/M10.5.8-validation.md:7；docs/testing/M4-results.json:416；docs/testing/M4-results.json:9

## OCR-1608 · 部分采纳

- 位置：docs/testing/M4-results.json:8；OCR原严重度：low
- 计划：R0-02 / P3
- 原意见：该值不是从验证日志或 `mvn -v` 输出解析得到，而是采集脚本中的硬编码常量（scripts/collect-test-evidence.ps1:71）。一旦 wrapper 升级或宿主机改用其他 Maven，清单仍会静默记录 3.9.9，导致证据与实际工具链不一致且无法察觉。建议从验证日志解析 `Apache Maven x.y.z`，或与 `.mvn/wrapper/maven-wrapper.properties` 中 `distributionUrl` 的版本做一致性校验。
- 判断：本地核对 milestone="M4"；capturedAt="2026-10-14T18:13:14.8622322+08:00"。工具链元数据不应依赖固定字面量，采纳实测wrapper/Maven版本；这不证明既有固定3.9.9实际用错，历史值无日志不能改成推测值。
- 证据：docs/testing/M4-results.json:8；scripts/collect-test-evidence.ps1:71

## OCR-1609 · 不采纳

- 位置：docs/testing/M6-shaded-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：PASS 结论缺少可校验依据：本文件只有 `taskState: EXITED`，没有进程退出码（仓库内其它证据文件如 M4-live-results.json、M10.5.4-native-results.json 均记录 exitCode），也没有任何断言/检查项汇总。触发条件：被测 JAR 以非 0 退出码退出或在断言失败后仍正常退出时，本记录同样会呈现 PASS，导致证据无法复现该结论。建议补充 exitCode、断言通过/失败计数以及校验命令。
- 判断：本地核对 status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M6-shaded-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1610 · 不采纳

- 位置：docs/testing/M6-shaded-results.json:11；OCR原严重度：medium
- 计划：无
- 原意见：三个 SHA-256（artifactSha256/jarSha256/scriptSha256）缺少验证上下文：没有记录源码提交号（commit/revision）、执行时间戳、harness 版本以及生成这些哈希的校验命令。触发条件：源码或脚本在生成证据后被修改时，无法判断哈希对应的产物是否仍属于当前代码基线，PASS 无法与具体版本绑定。建议补充 commit、UTC 时间戳与校验命令（例如 sha256sum 的调用方式）。
- 判断：本地核对 status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M6-shaded-results.json:11；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1611 · 不采纳

- 位置：docs/testing/M6-shaded-results.json:10；OCR原严重度：low
- 计划：无
- 原意见：证据中写入了机器相关的绝对路径（含本地用户名/目录布局 `D:\Documents\my projects\...`）。触发条件：该文件随仓库分发或被外部审计时，会泄露本地目录结构，且其它机器无法用该路径复现。建议改为相对路径或基于内容哈希的 fixture 标识（该路径末尾已有哈希，可作为唯一标识），并在文档中说明重建方式。
- 判断：本地核对 status="PASS"。本机路径/Windows分隔符是历史运行平台记录，实际collector已有replace归一化，当前矩阵已输出POSIX路径。没有现行消费者失败证据；路径不含密钥，不把目录布局视作凭据泄漏，也不追溯改写历史取证记录。
- 证据：docs/testing/M6-shaded-results.json:10；docs/development/M10-frontend-reuse-source.json:4；docs/testing/M10.5.9-validation.md:3

## OCR-1612 · 不采纳

- 位置：docs/testing/M6-results.json:10；OCR原严重度：medium
- 计划：无
- 原意见：证据不可独立复核：`.build-cache/` 在 `.gitignore:8` 中被忽略，因此该日志不随报告入库，`logSha256` 指向的是本机易失文件——第三方拿到仓库后无法用它验证 198 项全绿的结论。另外 `scripts/collect-test-evidence.ps1:10` 的默认日志路径为 `.build-cache/$Milestone-verify.log`，只要重跑一次 `mvnw.cmd -B clean verify` 就会覆盖同名日志，本报告里的哈希会静默失效（而 `sourceSha256` 在首次采集时还会用“源码 mtime 晚于日志 mtime 就抛错”作为唯一护栏）。建议把验证日志与 JUnit XML 归档为不可变副本（随报告提交或存入可长期访问的 artifact），并在报告中显式声明哈希的保留期/复核方式。
- 判断：本地核对 logSha256="10cf183c9dcb8ed58fd9daca224c8b4e6867d282c1ee9667f4df9ed6671daea5"；sourceSha256=对象(201键)。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M6-results.json:10；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M6-results.json:11；docs/testing/M6-results.json:538

## OCR-1613 · 不采纳

- 位置：docs/testing/M6-results.json:2；OCR原严重度：low
- 计划：无
- 原意见：该证据文件没有格式版本标识，且与生成脚本的字段集不一致：`scripts/collect-test-evidence.ps1:63-79` 始终写出 `slice` 字段，而本文件没有；后续里程碑（如 `docs/testing/M10-results.json`）改用 `verificationLog`、`baseCommit`、`dirty` 等键名。缺少 `schemaVersion`/`slice` 会让消费方无法判断报告属于哪一代结构、也无法与原脚本输出对齐，容易把“字段缺失”误读为“测试未覆盖”或直接解析失败。建议增加显式 `schemaVersion`（以及生产脚本要求的 `slice`），使各里程碑证据可被同一解析器读取。
- 判断：本地核对 milestone="M6"；capturedAt="2026-10-14T19:35:41.9518375+08:00"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M6-results.json:2；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1614 · 不采纳

- 位置：docs/testing/M6-results.json:6；OCR原严重度：medium
- 计划：无
- 原意见：单平台证据被表述为整体通过：文件只记录 Windows 10.0.26200，且末尾 `remoteCiResult` 为 `not run`，但 `suites` 中存在强平台相关用例，例如 `realPtyAcceptsInputResizesAndPreservesItsWorkingDirectory`、`escapeLinkOrWindowsJunctionIsRejectedForReadsWritesAndSearchTraversal`、`credentialsAreOwnerOnlyAndOrdinaryFoldersAreNotRepurposed`、`cancellationKillsObservedRealChildTreeAndPreservesHonestScope`、`thousandDirectoryEntriesMeetLocalTwoSecondTarget`。这些在 Linux/macOS 上的行为（POSIX 权限、软链接、进程组终止、PTY、2 秒性能目标）未被任何证据覆盖，`totals.tests=198` 容易被下游当作跨平台通过。建议补充平台覆盖字段（如 `platformScope: "windows-only"` 或 `platformsCovered` 列表）并在 totals 旁注明仅含 Windows 证据。
- 判断：本地核对 remoteCiResult="not run"；suites=数组(32项)。M6本地验收记录明确Windows平台及remoteCiResult=not run，没有宣称Linux/macOS通过；列出跨平台测试方法只代表其Windows分支执行，不能据通用PASS倒推跨平台承诺。
- 证据：docs/testing/M6-results.json:6；docs/testing/M6-results.json；docs/testing/M6-validation.md；docs/testing/M6-results.json:741；docs/testing/M6-results.json:18

## OCR-1615 · 不采纳

- 位置：docs/testing/M7-linux-results.json:19；OCR原严重度：low
- 计划：无
- 原意见：`logSha256` 只给出哈希，未给出日志文件名/相对路径（也未说明该哈希覆盖哪一个产物：整体 mvn 输出还是某个 surefire 报告），因此无法定位对应文件复算校验值；同理 `sourceSha256` 只覆盖源码与 POM，未覆盖 surefire XML、日志与 `evidenceDirectory` 内的产物。建议补充 `logFile`、`evidenceArtifacts`（路径 + sha256）键，使证据可被独立校验。
- 判断：本地核对 logSha256="87ff1498326c3bccc51c91364b90a12c9200d087867afe946861193bf9ddca5b"；sourceSha256=对象(236键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M7-linux-results.json:19；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M7-linux-results.json:657

## OCR-1616 · 不采纳

- 位置：docs/testing/M7-linux-results.json:17；OCR原严重度：medium
- 计划：无
- 原意见：证据绑定性缺陷：`codeVersion` 为 "Uncommitted"，说明 `sourceSha256` 绑定的是工作区快照而非某个 commit/tree。这样第三方无法用任何提交检出相同的源码来复算这 260 个用例的结果；同时"Java/POM hashes equal final Windows evidence"指向另一份 Windows 证据，但本文件未给出该证据的路径或哈希，跨平台等价性无法核验。建议增加 `commit`（或工作区树哈希）与 `crossPlatformEvidence`（文件名 + sha256）键。
- 判断：本地核对 codeVersion="Uncommitted; Java/POM hashes equal final Windows evidence"；sourceSha256=对象(236键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M7-linux-results.json:17；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M7-linux-results.json:657

## OCR-1617 · 不采纳

- 位置：docs/testing/M7-linux-results.json:8；OCR原严重度：low
- 计划：无
- 原意见：字段类型不一致：`javaVersion` 是 3 行 `java -version` 原始输出的数组，而 `gitVersion`、`mavenVersion` 是单一版本字符串。同一份证据里版本字段两种形态会让下游解析脚本必须特判。建议改为 `javaVersion`（单一版本号）+ 可选 `javaVersionOutput`（原始输出数组）。
- 判断：本地核对 javaVersion=数组(3项)；gitVersion="git version 2.53.0"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M7-linux-results.json:8；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M7-linux-results.json:13

## OCR-1618 · 不采纳

- 位置：docs/testing/M7-linux-results.json:15；OCR原严重度：medium
- 计划：无
- 原意见：离网结论的支撑不足，存在过度声称风险：`buildNetwork` 说明整个 `mvn verify` 期间容器使用 default bridge（有出网能力），而 `suites` 中多处断言的是离线/不出网/不起外部进程的行为（如 offlineDryRun、nested offline Wrapper cache、MCP/Web 的不发起请求类用例），且生产沙箱是 `network=none`。当前文件没有记录嵌套 Maven fixture 是否以 `-o/--offline` 执行，也没有一次 `network=none` 的隔离复跑记录，因此这些离网断言仅依赖用例内部标志，无法证明在无网络环境下同样成立。建议增加 `offlineFlags` 与 `isolatedNetworkRun` 字段记录实际执行参数与二次验证结果。
- 判断：本地核对 buildNetwork="Default bridge for apt/Maven dependencies; separate from production sandbox netwo...；suites=数组(41项)。构建容器bridge用于Git/Maven依赖准备，生产进程隔离测试另用network=none，两种作用域文档明确区分；构建可联网不推翻生产离网反例。
- 证据：docs/testing/M7-linux-results.json:15；.agent:验证流程；third-party/README.md:29；docs/testing/M7-linux-results.json:26

## OCR-1619 · 不采纳

- 位置：docs/testing/M4-live-results.json:114；OCR原严重度：medium
- 计划：无
- 原意见：`independentOracle.argv` 把宿主机的绝对路径写入了要提交进仓库的证据文件：JDK 路径 `C:\Program Files\Java\jdk-21.0.12.1\bin\java.exe`（第 112/243/... 行）以及 `D:\Documents\my projects\J-Harness\...`，泄漏本地目录布局/安装环境；并且 classpath 用 Windows 的 `;` 拼接（生成侧依赖 `os.pathsep`），换到 Linux/macOS 上按这份 argv 无法原样复现 oracle 结果。建议只记录脱敏后的相对路径 + `javaVersion` + classpath 片段（如 `<fixture>/target/classes` 与 `<cache>/oracle` 占位符），或额外记录一份平台无关的 classpath 数组，绝对路径不入库。
- 判断：本地核对 runs[0].independentOracle=对象(5键)。本机路径/Windows分隔符是历史运行平台记录，实际collector已有replace归一化，当前矩阵已输出POSIX路径。没有现行消费者失败证据；路径不含密钥，不把目录布局视作凭据泄漏，也不追溯改写历史取证记录。
- 证据：docs/testing/M4-live-results.json:114；docs/development/M10-frontend-reuse-source.json:4；docs/testing/M10.5.9-validation.md:3；docs/testing/M4-live-results.json:110

## OCR-1620 · 不采纳

- 位置：docs/testing/M4-live-results.json:140；OCR原严重度：medium
- 计划：无
- 原意见：第 28/139/270/391/532/643/784/905 行的 `parameters.maxCost` 语义与其字段名不一致：它是传给本次 CLI 的“剩余预算”，不是单次运行的成本上限（18.00 − 0.051084 = 17.948916，此后逐次扣减前一条 `costUpperBoundCny`）。后果：1) 复跑者会把 17.89 误当作 calculator#2 可花上限；2) 任何重排/重放/补跑都会让后续 maxCost 与真实可用额度脱节，而文件里没有记录扣减前的累计已用额度，无法校验；3) 与同一条记录中的 `costUpperBoundCny` 字段并列时容易混淆“实付上界”与“剩余额度”。建议拆分为 `runCapCny`（单次上限）与 `remainingBudgetBeforeCny`（扣减前剩余），或在 `parameters` 内明确标注该字段是递减的剩余额度。
- 判断：本地核对 runs[0].parameters=对象(14键)；runs[0].costUpperBoundCny="0.051084"。parameters.maxCost是实际传给本次CLI的运行上限，将全局剩余预算作为当次上限合法且明确；costUpperBoundCny是实际该次使用估计，两者不是同一语义。没有证明重排后被自动重放，不能把历史精确argv字段改名成不同参数。
- 证据：docs/testing/M4-live-results.json:140；docs/testing/M4-live-results.json:28；docs/testing/M4-live-results.json:19；docs/testing/M4-live-results.json:38

## OCR-1621 · 不采纳

- 位置：docs/testing/M4-live-results.json:523；OCR原严重度：medium
- 计划：无
- 原意见：`runs` 的条目身份不唯一：同一文件中 `fixture`+`repetition` 出现两次（calculator#1 分别在 df7253f9 与 7e1ef448；broken-project#1/calculator#2 同理），两次构建仅靠每条的 `cliJarSha256` 区分，顶层既无 `batchId`/`campaignId`，也无顶层 `cliJarSha256` 或批次边界。后果：按 (fixture, repetition) 聚合的消费方会重复计数或互相覆盖，也无法从文件本身还原“前四次 0.244800 + 后四次 0.249788”的批次划分。建议增加顶层批次标识（或把 `runs` 改为 `batches[{cliJarSha256, runs[]}]`），并把 `cliJarSha256` 提升为批次级字段。
- 判断：本地核对 runs=数组(8项)；runs[0].fixture="calculator"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M4-live-results.json:523；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M4-live-results.json:11；docs/testing/M4-live-results.json:13

## OCR-1622 · 部分采纳

- 位置：docs/testing/M4-live-results.json:9；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：成本可复现性校验缺失：`pricingSource` 只是一个 URL，没有价格快照日期/版本，而 `costUpperBoundCny` 完全依赖硬编码的 `inputPerMillion/outputPerMillion = 2/8`（逐条核对：0.051084 = 18942×2/1e6 + 1650×8/1e6，即把全部 prompt token 按缓存未命中价计费）。一旦该页面调价、或所记录的 model 实际单价与之不同，`totalUpperBoundCny` 会被静默低估，且第三方无从证伪。建议在文件中记录 `pricingAsOf`（抓取日期/版本）与显式计费公式（并说明缓存命中 token 为何按未命中价计入上界），同时给出一条可校验的声明，例如 `requests == len(usageReceipts)`、`sum(total_tokens) == sum(prompt_tokens)+sum(completion_tokens)`、以及“所有 usageReceipts 的 cached_tokens 等于 prompt_cache_hit_tokens”。
- 判断：本地核对 pricingSource="https://api-docs.deepseek.com/zh-cn/quick_start/pricing/"；runs[0].costUpperBoundCny="0.051084"。已有定价数字和URL、usageReceipts；采纳新增asOf/币种/缓存按未命中价上界公式与usage加总验证。不能据当前可能调价推翻历史记录，也不在本轮外查或重新调用付费模型。
- 证据：docs/testing/M4-live-results.json:9；docs/testing/M4-live-results.json:pricingSource；docs/testing/M4-live-results.json:38

## OCR-1623 · 不采纳

- 位置：docs/testing/M4-live-results.json:5；OCR原严重度：low
- 计划：无
- 原意见：同一个开关有两种表示：顶层 `thinking: "disabled"`（字符串）与每条 `parameters.disableThinking: true`（布尔），字段名和类型都不一致；此外 `baseUrl`/`model`/价格也在顶层与每个 `parameters` 中重复。消费方只读其一可能得到不同解释（例如把 `thinking` 当成三态模式字符串），重复字段也可能随时间漂移。建议统一为一种表示（顶层作为默认值，`parameters` 仅在偏离时出现），或直接删除顶层重复项并保留每条的 `disableThinking`。
- 判断：本地核对 baseUrl="https://api.deepseek.com"；model="deepseek-flash"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M4-live-results.json:5；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M4-live-results.json:4；docs/testing/M4-live-results.json:3

## OCR-1624 · 不采纳

- 位置：docs/testing/M7-skills-docker-results.json:18；OCR原严重度：low
- 计划：无
- 原意见：`jarSha256` 未与 jar 文件名、版本或大小绑定，也没有被测源码的 commit/tag。触发条件：构建产物被重新打包（同名不同内容或不同名）后复核该证据时，无法判定此哈希对应哪一个 jar，哈希校验实际不可执行。建议同时记录 jarName（含版本）、jarSize 与构建 commit。
- 判断：本地核对 jarSha256="4f784f489e8d107c8822495169acbfb22372f611bfa83d4887bf5809f7bb8475"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M7-skills-docker-results.json:18；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M7-skills-docker-results.json:19

## OCR-1625 · 不采纳

- 位置：docs/testing/M7-skills-docker-results.json:7；OCR原严重度：low
- 计划：无
- 原意见：`actualScriptFileEvidence: true` 只是布尔断言，未记录实际脚本文件路径及其 sha256，`actualDocker: true` 也只附带了基础镜像 digest，缺少 docker/守护进程版本、实际执行命令与退出码。触发条件：事后需要复核该产物时，无法定位被采集的脚本或证明容器确实执行过，SUCCESS 结论不可独立复现。建议改为记录 scriptPath + scriptSha256（以及 docker 版本、运行命令、退出码）。
- 判断：本地核对 milestone="M7"；slice="skills-sdk"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M7-skills-docker-results.json:7；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1626 · 不采纳

- 位置：docs/testing/M7-skills-docker-results.json:6；OCR原严重度：medium
- 计划：无
- 原意见：键名 `skills-local` / `skills-docker` 含连字符，与文件内其余 camelCase 键（actualDocker、requestsReconstructed、jarSha256 等）不一致。触发条件：当下游用 Jackson 默认命名策略绑定到 `skillsLocal` / `skillsDocker` 字段，或用按 camelCase 生成的 JSON Schema 校验该产物时，这两个键会被静默丢弃或校验失败，导致技能运行结果缺失却被当作解析成功。建议统一为 camelCase（或在使用方显式声明 @JsonProperty/别名）。
- 判断：本地核对 milestone="M7"；slice="skills-sdk"。这些键属于不同嵌套作用域/外部API原样字段或以会话标签为键的映射；JSON不要求全树单一命名风格。未发现实际消费者按统一大小写/连续step序号解析导致失败，纯样式统一不足以立为缺陷。
- 证据：docs/testing/M7-skills-docker-results.json:6；docs/testing/tools/verify-m9-evidence.py:38；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java

## OCR-1627 · 不采纳

- 位置：docs/testing/M7-skills-docker-results.json:7；OCR原严重度：medium
- 计划：无
- 原意见：`requestsReconstructed` 只记录成功重建的数量（4），没有 expected/attempted 总数或每个请求的唯一标识。触发条件：某次运行实际应构造 N>4 个请求而只有 4 个被重建（丢包、去重或截断），此文件与完整运行的输出完全相同，仍会得到 `result: SUCCESS`，属于校验缺陷——部分失败无法与全量成功区分。建议补充 expectedRequests（或 requestIds 列表）以便比对。
- 判断：本地核对 skills-local.requestsReconstructed=4。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M7-skills-docker-results.json:7；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M7-skills-docker-results.json:9

## OCR-1628 · 不采纳

- 位置：docs/testing/M7-docker-results.json:28；OCR原严重度：medium
- 计划：无
- 原意见：第 28、34 行使用 `local-sdk`/`docker-sdk`，与同文件其余 camelCase 键（如 `localTask`、`dockerTask`、`actualIsolationChecks`）不一致。若下游脚本、严格 JSON Schema 或 Jackson 生成模型按固定属性名读取，连字符键不会被映射为 `localSdk`/`dockerSdk`，会得到空值或校验失败；且顶层结构会随运行时 session 名变化。建议统一为 `localSdk`/`dockerSdk`（同时调整生成该结果的探针），或改为带 `kind` 字段的数组以固定顶层结构。
- 判断：本地核对 localTask=对象(3键)；dockerTask=对象(3键)。这些键属于不同嵌套作用域/外部API原样字段或以会话标签为键的映射；JSON不要求全树单一命名风格。未发现实际消费者按统一大小写/连续step序号解析导致失败，纯样式统一不足以立为缺陷。
- 证据：docs/testing/M7-docker-results.json:28；docs/testing/tools/verify-m9-evidence.py:38；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java；docs/testing/M7-docker-results.json:6；docs/testing/M7-docker-results.json:11

## OCR-1629 · 不采纳

- 位置：docs/testing/M7-web-docker-results.json:26；OCR原严重度：medium
- 计划：无
- 原意见：证据格式只允许 status=COMPLETED / result=SUCCESS，没有 UNKNOWN、FAILED、RETRIED 以及 attempt 计数等状态，也没有区分“未决(UNKNOWN)提交”与“最终完成”的字段。按当前结构，重试后的结果只能写成 SUCCESS，无法排除把 UNKNOWN 提交的重放当作成功完成记录。建议显式记录 attempts[] 与每次的 status（含 UNKNOWN），并规定 UNKNOWN 未被确认前不得计入 SUCCESS。
- 判断：本地核对 milestone="M7"；slice="packaged-cli-web-sdk-docker"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M7-web-docker-results.json:26；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1630 · 不采纳

- 位置：docs/testing/M7-web-docker-results.json:29；OCR原严重度：low
- 计划：无
- 原意见：jarSha256 未绑定制品名/版本/构建输入，无法确定该哈希对应哪个 jar（CLI 与 Web SDK 打包产物是否同一文件），哈希一旦对不上也无法定位。建议增加 artifact 名称与路径（或 buildId）。
- 判断：本地核对 milestone="M7"；slice="packaged-cli-web-sdk-docker"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M7-web-docker-results.json:29；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1631 · 不采纳

- 位置：docs/testing/M7-web-docker-results.json:27；OCR原严重度：medium
- 计划：无
- 原意见：cleanupVerified 只有顶层一个布尔值，缺少清理对象与检查方式（容器、网络、临时卷、本地临时目录），无法审计资源生命周期；docker 用例也没有容器 name/ID 或 docker rm/inspect 结果作为依据。建议记录被清理资源的标识列表与清理命令输出摘要，避免“清理已声明但实际残留”。
- 判断：本地核对 milestone="M7"；slice="packaged-cli-web-sdk-docker"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M7-web-docker-results.json:27；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1632 · 部分采纳

- 位置：docs/testing/M7-web-docker-results.json:5；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：镜像仅以 digest 记录，且是唯一的全局字段：缺少 platform/architecture（如 linux/amd64）、Docker/Java 版本、实际执行命令，Docker 用例也无法与本镜像一一对应。仅有 digest 不能证明该 digest 是在哪个架构上拉取执行的，也无法复现同一次运行（例如 arm64 宿主会用不同 manifest）。建议补充 platform、工具版本、执行命令，并把镜像 digest 记录到 cases.docker 内部。
- 判断：本地核对 milestone="M7"；slice="packaged-cli-web-sdk-docker"。全局image记录固定digest但无每侧实际platform/runtime标识；采纳在新Docker收据登记镜像resolved digest、架构/engine版本和执行参数，不把同一固定image作为所有架构完全等价保证。
- 证据：docs/testing/M7-web-docker-results.json:5；docs/testing/M7-web-docker-results.json:image

## OCR-1633 · 不采纳

- 位置：docs/testing/M7-web-docker-results.json:16；OCR原严重度：medium
- 计划：无
- 原意见：local 与 docker 两个用例的 requestsReconstructedAgainstWire、各断言布尔值完全相同，且没有任何 runId、起止时间戳、容器 ID/退出信息、日志摘要可以区分两次独立执行。此时无法排除 docker 结果是从 local 复制而来，也无法证明 Docker 用例确实执行了打包后的 CLI/Web SDK（而非退化为宿主执行）。建议每个用例增加 runId、startedAt/finishedAt、实际执行入口（jar 路径或容器内路径）与产物摘要。
- 判断：本地核对 milestone="M7"；slice="packaged-cli-web-sdk-docker"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M7-web-docker-results.json:16；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1634 · 不采纳

- 位置：docs/testing/M7-web-docker-results.json:12；OCR原严重度：medium
- 计划：无
- 原意见：approvalBeforeWrite、submissionDeduplicated、environmentClosed 都只是布尔值，没有序号/时间戳/审计事件支撑，无法从该证据文件复现“先审批后写入”的必需顺序，也无法证明环境（尤其是容器与本地环境）已被真正关闭。建议补充有序事件列表（如 approvalRequested->approved->write->dedupeHit）及环境关闭的可观测证据（容器 ID + 退出/清理记录）。
- 判断：本地核对 milestone="M7"；slice="packaged-cli-web-sdk-docker"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M7-web-docker-results.json:12；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1635 · 不采纳

- 位置：docs/testing/M7-subagent-docker-results.json:23；OCR原严重度：medium
- 计划：无
- 原意见：第23行 `SUCCESS` 没有任何“已执行检查数量/跳过检查/失败明细”字段支撑；只要两处 `status` 为 COMPLETED 且现存布尔为 true 就成立。触发条件：探针中某个 `require` 被条件分支跳过、或断言集合被裁剪时，报告仍会输出 SUCCESS，聚合结论失去意义。建议记录实际执行的检查项计数与 skipped 列表（或每项断言名），使 SUCCESS 可追溯到具体检查。
- 判断：本地核对 subagent-local.status="COMPLETED"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M7-subagent-docker-results.json:23；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M7-subagent-docker-results.json:10

## OCR-1636 · 部分采纳

- 位置：docs/testing/M7-subagent-docker-results.json:22；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：第22行仅有一个顶层 `cleanupVerified`，无法表明它覆盖了哪些对象与哪次运行：容器、网络、卷、临时会话/产物目录，以及失败路径下的清理。当前实现只核对带 owner 标签的容器是否为空，若本地临时目录残留或容器异常退出，本字段仍会为 true。建议改为按运行维度记录清理结论并列出被检查的资源类别，使“清理已验证”可被审计。
- 判断：本地核对 cleanupVerified=true。cleanupVerified是探针清理检查结果，未声明覆盖所有临时文件/镜像/卷；采纳明确检查对象仅自有容器等，失败路径保存残留摘要。不能把保留失败取证目录当清理泄漏。
- 证据：docs/testing/M7-subagent-docker-results.json:22；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java

## OCR-1637 · 不采纳

- 位置：docs/testing/M7-subagent-docker-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：第4行 `actualDocker` 只是布尔断言，报告本身不含任何 Docker 引擎/容器标识（镜像 ID、容器名、engine 版本、平台架构）与分阶段时间戳，仅靠第5行镜像 digest 无法证明本次结果确实由容器内运行产生。若宿主直接执行或复用同一份输出重写该文件，外部审计无法区分。建议补充非敏感运行元数据（engine 版本、containerId 或 owner 标签、startedAt/finishedAt、读取到的镜像 digest 复核值），使证据可复现。
- 判断：本地核对 actualDocker=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M7-subagent-docker-results.json:4；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1638 · 不采纳

- 位置：docs/testing/M7-subagent-docker-results.json:14；OCR原严重度：medium
- 计划：无
- 原意见：第6-21行两个块的字段与取值完全相同（parentRequestsReconstructed=4、childRequestsReconstructed=2、四项布尔全为 true），块内没有任何信息能区分“容器内执行”与“宿主执行”。区块名又是探针里的会话标签（`container ? "subagent-docker" : "subagent-local"`），若标签写错或两次都在宿主跑，本文件仍会呈现为完全合法的对照证据。建议在每个块内记录隔离描述（isolation=container、image、user、network=none、readOnlyRoot=true）以及实际环境根路径/容器标识，参照 M7-docker-results.json 的 `descriptor`/`actualIsolationChecks` 做法，使 docker 块可被独立验证。
- 判断：本地核对 milestone="M7"；slice="read-only-subagent"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M7-subagent-docker-results.json:14；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1639 · 不采纳

- 位置：docs/testing/M7-subagent-docker-results.json:6；OCR原严重度：low
- 计划：无
- 原意见：第6、14行的键使用连字符命名（`subagent-local`/`subagent-docker`），与文件内其余 camelCase 键不一致，且这两个键并非固定 schema 字段，而是探针会话标签（环境名）的映射结果——一旦探针调整标签，证据文件的键名会静默改变，按 `report["subagent-docker"]` 读取的下游校验会失效（连字符也无法用点路径访问）。建议改为稳定语义键，如 `localRun`/`dockerRun`，并在探针中显式写入。
- 判断：本地核对 milestone="M7"；slice="read-only-subagent"。这些键属于不同嵌套作用域/外部API原样字段或以会话标签为键的映射；JSON不要求全树单一命名风格。未发现实际消费者按统一大小写/连续step序号解析导致失败，纯样式统一不足以立为缺陷。
- 证据：docs/testing/M7-subagent-docker-results.json:6；docs/testing/tools/verify-m9-evidence.py:38；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java

## OCR-1640 · 不采纳

- 位置：docs/testing/M7-subagent-docker-results.json:12；OCR原严重度：medium
- 计划：无
- 原意见：第12、20行的 `parentEditVerified` 在 `read-only-subagent` 切片中语义有歧义：实际语义是“父方自己的 edit_file 成功执行（父为唯一写者）”，而键名易被读成“已验证父方未被修改”。同时本报告没有任何字段记录只读约束的证据（子模型请求了 `edit_file` 却被拒绝），触发条件：读者/下游脚本按字面理解该键，会把“父方被编辑”当成通过项，与只读保证相矛盾。建议改用无歧义的键名，并补充子方写操作被拒绝的记录，例如 `parentSoleWriterVerified` 与 `childWriteRejected`。
- 判断：本地核对 subagent-local.parentEditVerified=true。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M7-subagent-docker-results.json:12；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1641 · 不采纳

- 位置：docs/testing/M8-closure-results.json:8；OCR原严重度：medium
- 计划：无
- 原意见：证据链缺少可复现的定位信息：本文件只记录了 `jarSha256`、`sourceSnapshotSha256` 与 `verifierSha256` 三个摘要值，但没有记录被测 JAR 的实际路径/文件名与版本（生成端为 `harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar`）、证据目录、源码提交号（git commit）以及 `sourceSnapshotSha256` 所覆盖的文件清单。触发条件：审计者或下游校验脚本仅凭本文件复核时，无法确定摘要对应哪个构建产物/哪次源码快照，也无法重新计算 `sourceSnapshotSha256`（哈希的映射表未落盘，只能间接依赖 `M8-results.json` 中的 `sourceSha256`），因此“同一份源码 + 同一个 JAR”的结论实际不可独立复现。建议补充 `jar`（相对路径+版本）、`evidenceDir`、`gitCommit`、`sourceFileCount`/`sourceFilesSha256Manifest` 等字段，使每个摘要都有可解析的锚点。
- 判断：本地核对 jarSha256="12a8c812fc90035d6d6a6e78da8f5de3af6d3dcea28d72d54af68a9ccf37207e"；sourceSnapshotSha256="5e7f76f6a4c302bffd633ffff2bc7aaabff56b4447bc19affe38246c41721035"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8-closure-results.json:8；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M8-closure-results.json:7；docs/testing/M8-closure-results.json:9

## OCR-1642 · 不采纳

- 位置：docs/testing/M8-closure-results.json:41；OCR原严重度：low
- 计划：无
- 原意见：该文件没有版本化的 schema 标识，且键名风格不一致（`macos` 全小写，与 `remoteCi`、`releaseComplete`、`actualDocker` 的 camelCase 混用；`remoteCi` 的缩写大小写也与其他键不统一）。触发条件：下游校验脚本按字段名/固定 schema 读取本闭环文件时，字段命名与语义只能靠约定维持，后续若新增平台字段（如 `linuxArm64`）极易出现同类不一致，导致消费者静默读不到字段（`undefined`/`KeyError`），而不是显式失败。建议增加 `schemaVersion` 字段并统一键名风格（如 `remoteCi`→`remoteCiStatus`、`macos`→`macosStatus`）。
- 判断：本地核对 macos="not verified"；remoteCi="not run"。这些键属于不同嵌套作用域/外部API原样字段或以会话标签为键的映射；JSON不要求全树单一命名风格。未发现实际消费者按统一大小写/连续step序号解析导致失败，纯样式统一不足以立为缺陷。
- 证据：docs/testing/M8-closure-results.json:41；docs/testing/tools/verify-m9-evidence.py:38；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java；docs/testing/M8-closure-results.json:40

## OCR-1643 · 不采纳

- 位置：docs/testing/M8-closure-results.json:4；OCR原严重度：low
- 计划：无
- 原意见：顶层 `result: "SUCCESS"` 与同文件的 `remoteCi: "not run"`、`macos: "not verified"`、`releaseComplete: false` 语义冲突风险：触发条件是任何自动化门禁只读取 `result` 字段（这是最直观的字段名），会把“仅本地契约闭环”误当成发布/远程 CI 认证通过，而 `scope` 只是自然语言说明、不具备机器可读性。虽然当前文件保留了 `releaseComplete: false`，但建议把结论字段拆分为机器可判定的形式，例如增加 `certification: "local-closure"` / `releaseCertified: false`，或在 `result` 上限定为 `LOCAL_CLOSURE_SUCCESS` 之类不会与发布通过混淆的取值。
- 判断：本地核对 result="SUCCESS"；scope="M8 contracted local closure; not a release/remote-CI certification"。记录已区分本轮选定范围SUCCESS/PASS与releaseComplete/remoteCi/scope/后续里程碑；负例非零退出也可构成验证器成功。不能假设一个忽略其余字段的消费者据此把全PRD或新里程碑授予完成。
- 证据：docs/testing/M8-closure-results.json:4；.agent:当前接续状态；docs/testing/M8-closure-results.json；docs/testing/M10.5.8-validation.md:3；docs/testing/M8-closure-results.json:5

## OCR-1644 · 不采纳

- 位置：docs/testing/M8-closure-results.json:27；OCR原严重度：medium
- 计划：无
- 原意见：`evidenceSha256` 清单不完整：它只绑定了 `M8-results.json`、`M8-linux-results.json` 和 7 个探针结果（`PROBES` 常量），而同一目录下参与 M8.4 闭环的 `M8.4-verifier-results.json`、`M8.4-audit-results.json`、`M8.4-smoke-results.json` 未被任何摘要覆盖。触发条件：这三份文件在闭环生成后被修改或替换时，本文件不会失配，校验过程无法发现篡改（其中 `M8.4-verifier-results.json` 本身是记录校验结论并绑定 Windows/Linux 证据摘要的关键文件）。建议把这些 M8.4 结果文件一并纳入 `evidenceSha256`，或显式增加字段说明哪些产物不在本清单范围内及其原因。
- 判断：本地核对 evidenceSha256=对象(9键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8-closure-results.json:27；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1645 · 不采纳

- 位置：docs/testing/M7-results.json:0；OCR原严重度：high
- 计划：无
- 原意见：证据可校验性缺陷：`log` 指向 `.build-cache/M7-verify.log`，而 `.gitignore` 第 8、11 行忽略了 `.build-cache/` 与 `*.log`，因此该日志不会被提交。仓库读者只能看到自报的 `logSha256`，无法重算或核对它，哈希等于没有约束力（触发条件：任何人 checkout 仓库后用 sha256sum 校验日志 → 文件不存在）。建议把原始日志作为受版本控制的证据文件保存（例如 `docs/testing/M7-verify.log`），或在同一 manifest 中内联日志摘要/大小并保证被引用文件可获取。
- 判断：本地核对 log=".build-cache/M7-verify.log"；logSha256="57a1950524d5cb41230bb06d5c8094eb6e4bda14cca70b94b1fb2831e33b61f1"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M7-results.json:1；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M7-results.json:10；docs/testing/M7-results.json:11

## OCR-1646 · 不采纳

- 位置：docs/testing/M7-results.json:0；OCR原严重度：low
- 计划：无
- 原意见：该证据文件没有格式/模式版本字段。同目录下其他里程碑证据已出现不同结构（例如嵌套 `logSha256`、`fixtureSourceSha256`、`verificationLogSha256`），消费端脚本无法判断本文件遵循哪一版模式，字段改名或结构演进时会被静默误读。建议增加显式 `schemaVersion`（或 `format`）字段，并让校验脚本按版本解析。
- 判断：本地核对 logSha256="57a1950524d5cb41230bb06d5c8094eb6e4bda14cca70b94b1fb2831e33b61f1"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M7-results.json:1；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M7-results.json:11

## OCR-1647 · 不采纳

- 位置：docs/testing/M7-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：复现边界缺陷：`remoteCiResult` 为 `not run`，说明 M7 结论仅来自单机 Windows 本地构建；但 `suites` 中的 M7McpTest / M7WebTest / M7SubagentTest / M6TerminalTest（realPty）等用例依赖网络、Docker、PTY 等外部环境，且全部记为 `skipped: 0`。文件没有记录 Docker 是否可用及其版本、网络前置条件、终端/pty 实现，因此“这些环境相关用例真实执行”的声明对第三方不可验证，也不能作为非 Windows 平台或 CI 已复现的依据。建议补记 Docker/网络可用性与版本、以及 CI（或多平台）运行结果字段，而不是仅保留 `not run`。
- 判断：本地核对 remoteCiResult="not run"；suites=数组(41项)。构建容器bridge用于Git/Maven依赖准备，生产进程隔离测试另用network=none，两种作用域文档明确区分；构建可联网不推翻生产离网反例。
- 证据：docs/testing/M7-results.json:1；.agent:验证流程；third-party/README.md:29；docs/testing/M7-results.json:944；docs/testing/M7-results.json:18

## OCR-1648 · 不采纳

- 位置：docs/testing/M7-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：`codeVersion` 是自然语言而不是机器可读的版本标识，且本文件未记录 git commit SHA / 工作区 dirty 清单，也没有对 manifest 自身或日志的签名。结果是：`sourceSha256` 只覆盖被枚举的文件（未枚举的构建输入完全不受约束），任何人可以连同哈希一起整体替换本文件而不被发现，无法证明“某次 SUCCESS”对应哪个源码快照。建议补充 `gitCommit`、`workingTreeDirty`/未提交文件清单，并对 manifest（含 log 哈希）做签名或至少记录 manifest 自身的哈希于独立可信位置。
- 判断：本地核对 codeVersion="uncommitted; identify this tested source snapshot by sourceSha256"；sourceSha256=对象(252键)。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M7-results.json:1；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M7-results.json:9；docs/testing/M7-results.json:690

## OCR-1649 · 不采纳

- 位置：docs/testing/M7-results.json:0；OCR原严重度：low
- 计划：无
- 原意见：平台标识自相矛盾：`Microsoft Windows 10.0.26200` 把 `os.name`（Windows 11 上 JDK 仍返回 "Windows 10"）与 `os.version` 拼在一起，而 build 26200 属于 Windows 11 25H2，导致记录的平台与实际执行环境不一致，且缺少 `os.arch`。这会让依赖平台分支的测试（PTY、路径、文件锁、Git）无法被正确归因或复现。建议分别记录 `osName`、`osVersion`、`osArch`。
- 判断：本地核对 milestone="M7"；capturedAt="2026-10-15T20:42:34.4753820+08:00"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M7-results.json:1；docs/testing/M7-results.json:platform

## OCR-1650 · 不采纳

- 位置：docs/testing/M8.2-scheduling-docker-results.json:31；OCR原严重度：medium
- 计划：无
- 原意见：evidenceDirectory 记录的是宿主机绝对路径（Windows 盘符 + 用户目录），这会泄露主机用户名/目录布局，且在其他机器或 CI 上无法访问，导致证据不可复现、不可审计，也不适合随仓库分发。触发条件：任何审阅者按该字段去取证据。建议改为仓库内相对路径（如 docs/testing/evidence/<run-id>）或仅保留一次运行的 UUID/哈希标识，并将原始日志归档到版本库或制品库。
- 判断：本地核对 milestone="M8.2"；slice="packaged-cli-web-sdk-readonly-scheduling"。本机路径/Windows分隔符是历史运行平台记录，实际collector已有replace归一化，当前矩阵已输出POSIX路径。没有现行消费者失败证据；路径不含密钥，不把目录布局视作凭据泄漏，也不追溯改写历史取证记录。
- 证据：docs/testing/M8.2-scheduling-docker-results.json:31；docs/development/M10-frontend-reuse-source.json:4；docs/testing/M10.5.9-validation.md:3

## OCR-1651 · 不采纳

- 位置：docs/testing/M8.2-scheduling-docker-results.json:16；OCR原严重度：medium
- 计划：无
- 原意见：docker 用例与 local 用例的字段和取值完全一致，缺少任何能证明“确实在容器内执行”的隔离标记（容器 ID、镜像运行模式/网络模式、挂载点），因此无法排除因 Docker 不可用而回落到本地执行却仍被记为 actualDocker=true 的情况。触发条件：容器启动失败或回退路径被执行。建议在 docker 用例中记录 containerId、runtime、network/mount 配置，或至少记录容器内环境标识。
- 判断：本地核对 milestone="M8.2"；slice="packaged-cli-web-sdk-readonly-scheduling"。local与docker对同一行为返回一致正是对照目标，Docker启动失败不会自动回退宿主；探针显式构造Docker执行环境。可加实例元数据，但一致字节本身不构成未运行/回退证据。
- 证据：docs/testing/M8.2-scheduling-docker-results.json:16；harness-testkit/src/test/java/io/jharness/testkit/M8SchedulingDockerProbe.java；harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java

## OCR-1652 · 不采纳

- 位置：docs/testing/M8.2-scheduling-docker-results.json:32；OCR原严重度：low
- 计划：无
- 原意见：jarSha256 没有伴随制品名、版本或构建引用，哈希无法与被测 jar 建立绑定关系：重建出的 jar 只要有一字节差异就无法解释，也无法判断该哈希对应的是哪一次构建产物。建议增加 artifact 字段（文件名、版本/commit、构建编号）以及校验结果，使哈希可被独立复核。
- 判断：本地核对 milestone="M8.2"；slice="packaged-cli-web-sdk-readonly-scheduling"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.2-scheduling-docker-results.json:32；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1653 · 不采纳

- 位置：docs/testing/M8.2-scheduling-docker-results.json:9；OCR原严重度：medium
- 计划：无
- 原意见：hostExitCode 在 docker 用例中语义不清：它是宿主进程（docker run 客户端）的退出码，容器内进程失败时宿主退出码仍可能为 0，从而把容器内的失败掩盖为 COMPLETED/SUCCESS。建议将其明确命名为 hostExitCode 并同时增加 containerExitCode（以及 status 的判定来源），并注明 status 由哪个退出码决定。
- 判断：本地核对 milestone="M8.2"；slice="packaged-cli-web-sdk-readonly-scheduling"。docker run正常传播容器命令退出码，特殊125/126/127为Docker错误；未发现吞掉返回码的执行入口。仅因字段名hostExitCode不能推定容器失败返回0。
- 证据：docs/testing/M8.2-scheduling-docker-results.json:9；harness-testkit/src/test/java/io/jharness/testkit/M7WebDockerProbe.java

## OCR-1654 · 不采纳

- 位置：docs/testing/M8.2-scheduling-docker-results.json:6；OCR原严重度：medium
- 计划：无
- 原意见：这些布尔结论（actualHttp、approvalBeforeWrite、parallelReadOnlyScheduling、submissionDeduplicated、cleanupVerified）均为自证字段，没有关联任何原始日志、断言计数或证据文件，审阅者既无法复现也无法证伪；尤其是“写入前审批”和“并发只读调度”这类关键安全/并发结论，缺失计数就无法判断是否真的发生了对应行为。建议为每个标志附证据引用（日志文件名/哈希），或补充可核对的计数，如 approvalRequestCount、writeAttemptCount、parallelRequestCount、dedupedSubmissionCount。
- 判断：本地核对 milestone="M8.2"；slice="packaged-cli-web-sdk-readonly-scheduling"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8.2-scheduling-docker-results.json:6；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1655 · 不采纳

- 位置：docs/testing/M8-environment-regression.json:2；OCR原严重度：medium
- 计划：无
- 原意见：文件名标注为 M8 环境回归证据，但 `milestone` 声明为 `"M7"`（该值由 M7DockerProbe 硬编码写入）。按 milestone 字段做门禁/归档的工具会把这份 M8 回归证据错误归入 M7 里程碑，导致 M8 环境回归在发布审计中“缺失”。触发条件：任何以 `milestone` 为键聚合 docs/testing/*.json 的校验脚本。建议改为 M8（或新增独立的 `probe`/`slice` 字段区分来源探针与所属里程碑）。
- 判断：本地核对 milestone="M7"；slice="execution-environment"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M8-environment-regression.json:2；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29；docs/testing/M8-environment-regression.json:3

## OCR-1656 · 不采纳

- 位置：docs/testing/M8-environment-regression.json:40；OCR原严重度：medium
- 计划：无
- 原意见：`cleanupVerified` 只是一个裸布尔值，未记录验证方式与验证对象（容器是否已删除、按 owner 名称过滤后的 `docker ps` 结果、镜像/临时卷是否残留、验证时间点）。因此无法从该工件复现“无泄漏”的结论：即使清理逻辑被跳过而布尔值仍为 true（例如断言遗漏），审计方也无从分辨。触发条件：清理流程异常退出或断言被移除。建议改为结构化对象，例如 `{"containersRemaining": 0, "volumesRemaining": 0, "checkedAt": "..."}`。
- 判断：本地核对 cleanupVerified=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8-environment-regression.json:40；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1657 · 不采纳

- 位置：docs/testing/M8-environment-regression.json:28；OCR原严重度：low
- 计划：无
- 原意见：`local-sdk` / `docker-sdk` 使用 kebab-case，而本文件其余键（actualDocker、actualIsolationChecks、cleanupVerified、jarSha256）均为 camelCase。消费方若按统一 camelCase 约定读取 `localSdk`/`dockerSdk`，会静默取到 null 而不报错，从而使“本地/Docker 双环境均已验证”的断言在空值上误判为通过。触发条件：按命名规范生成取值键的校验代码。建议统一为 camelCase，并同步修改生成该证据的探针及所有读取方。
- 判断：本地核对 milestone="M7"；slice="execution-environment"。这些键属于不同嵌套作用域/外部API原样字段或以会话标签为键的映射；JSON不要求全树单一命名风格。未发现实际消费者按统一大小写/连续step序号解析导致失败，纯样式统一不足以立为缺陷。
- 证据：docs/testing/M8-environment-regression.json:28；docs/testing/tools/verify-m9-evidence.py:38；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java

## OCR-1658 · 不采纳

- 位置：docs/testing/M8-environment-regression.json:24；OCR原严重度：medium
- 计划：无
- 原意见：descriptor 声称 `readOnlyRoot`/`network`/`capabilitiesDropped` 等隔离项，却完全没有记录 `noNewPrivileges`、`seccomp`、`tmpfs`（可写临时目录挂载）等关键控制项，而 `actualIsolationChecks` 仍为 true。一旦容器创建时 noNewPrivileges 被误置为 false 或 seccomp 未启用（例如 API 版本差异导致被忽略），本证据文件不会体现出任何降级，隔离结论依旧显示通过。触发条件：任何一次 descriptor 构建或容器创建参数回退。建议把上述控制项作为必填字段写入证据，并在探针中断言其取值（而非仅记录）。
- 判断：本地核对 descriptor.readOnlyRoot=true；descriptor.network="none"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8-environment-regression.json:24；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M8-environment-regression.json:21；docs/testing/M8-environment-regression.json:23

## OCR-1659 · 不采纳

- 位置：docs/testing/M8.2-filter-regression.json:52；OCR原严重度：medium
- 计划：无
- 原意见：evidenceDirectory 写入了绝对主机路径，暴露本机用户名/目录结构与项目本地落盘位置（D 盘用户文档目录下的项目路径）。该证据文件一旦入库或外发即为主机路径泄露（prescan 第 5 点）。建议只保留 run id，或改用仓库相对路径/对主机前缀做脱敏或哈希，不要输出真实绝对路径。
- 判断：本地核对 milestone="M8.1"；capturedAt="2026-10-15T14:32:08.328460700Z"。本机路径/Windows分隔符是历史运行平台记录，实际collector已有replace归一化，当前矩阵已输出POSIX路径。没有现行消费者失败证据；路径不含密钥，不把目录布局视作凭据泄漏，也不追溯改写历史取证记录。
- 证据：docs/testing/M8.2-filter-regression.json:52；docs/development/M10-frontend-reuse-source.json:4；docs/testing/M10.5.9-validation.md:3

## OCR-1660 · 不采纳

- 位置：docs/testing/M8.2-filter-regression.json:2；OCR原严重度：high
- 计划：无
- 原意见：文件路径标明这是 M8.2 的过滤回归证据，但记录内的 milestone 仍是 "M8.1"。除 jarSha256 外没有 git commit / 构建号 / 被测配置版本，无法把该文件绑定到 M8.2 的具体构建；如果它是从 M8.1 复制而来，M8.2 验收会把旧证据当作本里程碑通过证明，属于可复现的证据归属错误。建议改为 M8.2 并补充 commit、构建号与生成该证据的命令。
- 判断：本地核对 milestone="M8.1"；capturedAt="2026-10-15T14:32:08.328460700Z"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M8.2-filter-regression.json:2；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1661 · 不采纳

- 位置：docs/testing/M8.2-filter-regression.json:51；OCR原严重度：medium
- 计划：无
- 原意见：cleanupVerified 只是一个 true 布尔值，没有清理对象清单、清理前后目录/容器快照或校验时间戳，因此无法证明清理是针对真实文件系统核验过的：即使容器或 filter-1d7d98b6-... 临时目录残留，该字段仍可为 true。建议改为记录被清理的路径/容器清单、逐项校验结果与清理时间戳，并保证该值由实际检查推导。
- 判断：本地核对 milestone="M8.1"；capturedAt="2026-10-15T14:32:08.328460700Z"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8.2-filter-regression.json:51；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1662 · 不采纳

- 位置：docs/testing/M8.2-filter-regression.json:3；OCR原严重度：high
- 计划：无
- 原意见：result 与 actualDocker 都是常量声明，与 cases 中任何断言没有关联，cases 内也没有 status 字段：只要探针漏跑某个 case，或某个布尔（isolatedViewMatches / listMatchesSearch / metadataAndTraversalDenied 等）实际为 false 而没被写进来，顶层仍然会报 SUCCESS / actualDocker=true。触发条件是可复现的：删掉一个 case 或让探针结果为空，文件仍是 SUCCESS。建议由每个 case 的断言推导顶层 result，并为每个 case 增加 name/status 及失败详情字段。
- 判断：本地核对 milestone="M8.1"；capturedAt="2026-10-15T14:32:08.328460700Z"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8.2-filter-regression.json:3；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1663 · 不采纳

- 位置：docs/testing/M8.2-filter-regression.json:6；OCR原严重度：medium
- 计划：无
- 原意见：cases 里的元素没有 name/id 字段，两个 case 无法定位（出问题只能靠数组下标）；且两个 case 的 docker 与 local 块逐字节相同，两侧都没有任何环境标识（主机名、OS、工作目录、镜像运行时 ID、执行时间戳）。这既不能证明 docker 与 local 是两次独立执行，也无法排除复制粘贴产生的伪证据，prescan 关注的“本地行为确实匹配 Docker”实际上没有被证据链支撑。建议为每个 case 增加 id/name 与 status，并在两侧记录环境标识与执行时间戳（或产生结果的命令与日志摘要）。
- 判断：本地核对 milestone="M8.1"；capturedAt="2026-10-15T14:32:08.328460700Z"。原意见中可见字段本身成立，但没有证明其推测的行为故障；配套源码/阶段记录已限定作用域。仅对旧收据补充元数据或统一风格不作为本轮合理修复任务，保留历史事实字节。
- 证据：docs/testing/M8.2-filter-regression.json:6；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1664 · 不采纳

- 位置：docs/testing/M8-filter-docker-results.json:52；OCR原严重度：medium
- 计划：无
- 原意见：证据目录记录了本机绝对路径（含 Windows 盘符与 `.build-cache/m8-filter-probe/...` 目录结构），提交入库会泄露本地主机布局，且在其他机器/CI 上该路径必然不存在，无法据此复核。建议改为相对仓库根的路径（或仅在 CI artifact 中保留绝对路径），并对用户名等片段脱敏。
- 判断：本地核对 milestone="M8.1"；capturedAt="2026-10-15T13:35:13.059411500Z"。本机路径/Windows分隔符是历史运行平台记录，实际collector已有replace归一化，当前矩阵已输出POSIX路径。没有现行消费者失败证据；路径不含密钥，不把目录布局视作凭据泄漏，也不追溯改写历史取证记录。
- 证据：docs/testing/M8-filter-docker-results.json:52；docs/development/M10-frontend-reuse-source.json:4；docs/testing/M10.5.9-validation.md:3

## OCR-1665 · 不采纳

- 位置：docs/testing/M8-filter-docker-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：`actualDocker: true` 目前只是自声明标志：local 与 docker 两块的布尔值、grepPaths、paths 逐字相同，却没有容器标识（container id / docker inspect 结果）、容器内挂载路径或退出码。建议记录容器运行命令与退出码，或把容器侧输出摘要（logDigest）纳入证据，否则无法区分“确实在 Docker 中执行”与“把本地结果复制成 docker 块”。
- 判断：本地核对 milestone="M8.1"；capturedAt="2026-10-15T13:35:13.059411500Z"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8-filter-docker-results.json:4；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1666 · 不采纳

- 位置：docs/testing/M8-filter-docker-results.json:54；OCR原严重度：medium
- 计划：无
- 原意见：记录声明 `result: SUCCESS`，但缺少复现所必需的溯源信息：没有源码提交号（git rev）、JDK/OS/平台版本、docker 运行命令与挂载参数、以及 local 与 docker 两侧的实际命令输出或日志摘要。仅有 jarSha256 与镜像 digest，无法证明该 jar 由哪次提交构建。建议补充 `sourceRevision`、`javaVersion`、`command`、`logDigest` 等字段，否则该 SUCCESS 结论无法被第三方复现验证。
- 判断：本地核对 milestone="M8.1"；capturedAt="2026-10-15T13:35:13.059411500Z"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8-filter-docker-results.json:54；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1667 · 不采纳

- 位置：docs/testing/M8-filter-docker-results.json:18；OCR原严重度：medium
- 计划：无
- 原意见：`metadataAndTraversalDenied: true` 被断言为通过，但同一用例记录的 `paths` 只包含常规相对路径（cache/、hidden.txt、target/、中文 空格/nested.txt），没有任何 `../` 越界路径、绝对路径（如 /etc/passwd、C:\...）或符号链接探针，因此“元数据访问与目录遍历被拒绝”这一文件系统边界结论在证据中不可验证，存在把未覆盖项当作已通过的风险。建议在 cases 中显式记录 traversalProbes 及每项期望/实际结果（如 `{"probe":"../outside.txt","denied":true}`）。
- 判断：本地核对 cases[0].local.paths=数组(5项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8-filter-docker-results.json:18；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1668 · 不采纳

- 位置：docs/testing/M8.3-resilience-docker-results.json:31；OCR原严重度：medium
- 计划：无
- 原意见：安全/可复现缺陷（行 31）：`evidenceDirectory` 是绝对 Windows 主机路径，暴露了主机目录布局与用户目录信息（未脱敏），同时指向仓库外 `.build-cache`，全新检出或 CI 上该路径不存在，提交在仓库里的结果因此不可复核。建议只记录仓库相对路径或证据目录的内容哈希，并把必要证据（原始 wire 记录、日志）脱敏后归档到仓库内。
- 判断：本地核对 evidenceDirectory="D:\\Documents\\my projects\\J-Harness\\.build-cache\\m8.3-resilience-probe\\resil...。本机路径/Windows分隔符是历史运行平台记录，实际collector已有replace归一化，当前矩阵已输出POSIX路径。没有现行消费者失败证据；路径不含密钥，不把目录布局视作凭据泄漏，也不追溯改写历史取证记录。
- 证据：docs/testing/M8.3-resilience-docker-results.json:31；docs/development/M10-frontend-reuse-source.json:4；docs/testing/M10.5.9-validation.md:3

## OCR-1669 · 不采纳

- 位置：docs/testing/M8.3-resilience-docker-results.json:6；OCR原严重度：low
- 计划：无
- 原意见：可验证性缺陷（行 6-25）：`local` 与 `docker` 两个用例的全部字段（含布尔断言与请求计数）完全相同，且各自没有证据引用或期望值字段，无法区分“两次独立运行得到一致结果”与“复制粘贴/共用同一份结果对象”。建议每个用例增加各自独立的 `evidencePath`、`runId`、耗时与镜像/进程信息，使一致性本身可被审计。
- 判断：本地核对 cases.local=对象(8键)；cases.docker=对象(8键)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8.3-resilience-docker-results.json:6；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M8.3-resilience-docker-results.json:16

## OCR-1670 · 不采纳

- 位置：docs/testing/M8.3-resilience-docker-results.json:32；OCR原严重度：medium
- 计划：无
- 原意见：产物溯源缺陷（行 27、32）：`image` 与 `jarSha256` 只有摘要，没有 artifact 路径/构建命令/源码 revision（无 `gitCommit`、无 jar 文件名与相对路径），且两个用例不记录各自实际使用的镜像引用与 jar 摘要，无法证明被测二进制就是这两个摘要对应的产物（例如本地用例用了别的 jar，或 docker 用例 pull 到被重新 tag 的镜像时无法发现）。建议补充 `sourceRevision`、`jarPath`、每用例 `imageRef/jarSha256` 与平台信息，使摘要与用例证据一一绑定。
- 判断：本地核对 image="eclipse-temurin@sha256:ccbad9e7cb84c27092b12c40a4143e5733e050db9c3784d84f632dcf42...；jarSha256="868e56d14b9af17eb3346bba0e1b3ee23ad77d4b20bac4c95a2bc0337d596d9a"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.3-resilience-docker-results.json:32；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M8.3-resilience-docker-results.json:27

## OCR-1671 · 不采纳

- 位置：docs/testing/M8.3-resilience-docker-results.json:28；OCR原严重度：high
- 计划：无
- 原意见：校验/正确性缺陷（行 28）：顶层 `result` 与用例 `status` 语义不闭合——两个用例只记录了 `"status": "COMPLETED"`（行 14、24），而 `COMPLETED` 仅表示流程执行完毕，并不等于断言通过；本文件也没有任何 `passed/failed/assertions/skipReason` 字段。触发条件：探针在生成报告时先写入固定常量 `SUCCESS`、后由用例步骤失败或断言被跳过（如 Docker 内未真正发 HTTP、写审批用例未覆盖）时，产物仍会被下游判定为通过，形成“完成但失败”被表示为 SUCCESS 的误报。建议为每个用例补充断言计数与失败明细（如 `assertionsPassed/assertionsFailed/failedChecks`），并由断言结果推导 `result`，而不是写死。
- 判断：本地核对 result="SUCCESS"；cases.local.status="COMPLETED"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8.3-resilience-docker-results.json:28；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M8.3-resilience-docker-results.json:14

## OCR-1672 · 不采纳

- 位置：docs/testing/M8.3-resilience-docker-results.json:9；OCR原严重度：medium
- 计划：无
- 原意见：校验缺陷（行 9、19）：`requestsReconstructedAgainstWire` 只给了魔数 4，既没有期望值/阈值，也没有请求清单或对 wire 记录的引用，无法判断是否少采/漏断言；重试、并行只读、写审批等场景对请求次数有隐含期望（429 重试会产生额外请求），这里缺少期望值使得“是否真的重建了完整请求序列”不可验证。建议增加 `requestsExpected` 与请求种类/端点列表（脱敏后）或指向 wire 证据文件的引用。
- 判断：本地核对 cases.local.requestsReconstructedAgainstWire=4。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M8.3-resilience-docker-results.json:9；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1673 · 不采纳

- 位置：docs/testing/M8-linux-results.json:20；OCR原严重度：medium
- 计划：无
- 原意见：第20-22行的 evidenceDirectory/log 指向 .build-cache/…，而 .gitignore 第8行忽略了 .build-cache/，M0/M1 验证文档也注明原始日志“不入 Git”。因此 logSha256 在任何人克隆仓库后都无法复核，而 scripts/verify-m8-closure.py 第49-50行恰恰要求该本地文件存在且哈希一致——exitCode=0 / BUILD SUCCESS 的结论在入库证据里变成不可验证的自我声明；M8-closure.md 第23/36行还称“Linux 快照和独立日志位置见 M8-linux-results.json”，实际指向的目录不入库。建议把脱敏后的日志（或至少 TEST 统计与 BUILD SUCCESS 片段）随证据提交，或在文件中显式写明日志不随仓库分发并提供可校验的替代摘要（如 surefire 汇总的哈希）。
- 判断：本地核对 milestone="M8"；slice="M8.4"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M8-linux-results.json:20；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1674 · 不采纳

- 位置：docs/testing/M8-linux-results.json:19；OCR原严重度：medium
- 计划：无
- 原意见：第19行用 baselineCommit 断言本次结果对应提交 0ca36f18…，但同批次、相隔约5分钟的 Windows 证据 docs/testing/M8-results.json 第10行明确写着被测源码为 “uncommitted; identify this tested source snapshot by sourceSha256”，本文件也没有任何“工作区干净/脏”的说明；两侧 295 个用例名完全相同，说明大概率是同一份未提交树。若运行时存在未提交改动，此提交号并不能标识被测源码，属于虚假溯源；若确实在该提交上运行，则应显式声明工作区干净。建议增加工作区状态字段（如 "workingTree": "clean|dirty"），或像 Windows 证据一样只以 sourceSha256 标识快照，不给出无法证明的提交号。
- 判断：本地核对 milestone="M8"；slice="M8.4"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8-linux-results.json:19；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1675 · 不采纳

- 位置：docs/testing/M8-linux-results.json:10；OCR原严重度：low
- 计划：无
- 原意见：同一个键 javaVersion 在 Windows 证据（M8-results.json 第8行）是字符串 "21.0.12.1"，这里却是 3 元素数组，键名相同但类型不同。任何通用消费方（例如对 javaVersion 调用 startswith，或直接把该值拼进报告/表格）在 Linux 文件上都会抛异常；现有校验脚本只能为两侧分别硬编码 javaVersion 与 javaMajor 两条路径来规避。建议统一类型：Linux 也用字符串并另加 javaRuntimeBanner/Details 数组存放三行 banner，或两侧统一改为数组键。
- 判断：本地核对 milestone="M8"；slice="M8.4"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8-linux-results.json:10；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1676 · 不采纳

- 位置：docs/testing/M8-linux-results.json:17；OCR原严重度：low
- 计划：无
- 原意见：第17行称 bridge 网络用于 “bounded main-only Ubuntu Git installation and Maven dependencies”，第18行又称 Maven 发行版与本地仓库是从宿主只读复制进容器（即依赖可离线取得），两处对“构建是否从网络拉取 Maven 依赖”的表述互斥；command 中的 -Dmaven.repo.local=/tmp/m2 只能说明本地仓库位置，无法判断是否联网。对以可复现性为目的的证据，应明确区分哪些步骤必须联网（安装 git？下载依赖？）、哪些是离线，否则读者会把该结果误当作可在 network=none 下复现。
- 判断：本地核对 milestone="M8"；slice="M8.4"。构建容器bridge用于Git/Maven依赖准备，生产进程隔离测试另用network=none，两种作用域文档明确区分；构建可联网不推翻生产离网反例。
- 证据：docs/testing/M8-linux-results.json:17；.agent:验证流程；third-party/README.md:29

## OCR-1677 · 不采纳

- 位置：docs/testing/M8.2-results.json:11；OCR原严重度：medium
- 计划：无
- 原意见：第11-12行引用的验证日志位于被 `.gitignore` 排除的 `.build-cache/` 目录，仓库内不存在该文件，因此 `logSha256` 无法被任何复核者校验；`buildResult: SUCCESS` 以及 280 项用例结果实际上只有自报值，无法从本清单独立复现。建议将日志（或截断/压缩后的副本）随证据一起提交到 `docs/testing/`，或改为引用可长期访问的 CI artifact 地址，并同步更新 `log` 与 `logSha256`；若无法归档，应明确标注该哈希不可外部验证。
- 判断：本地核对 logSha256="a02aab52cec6172a7908aa092fce586fa25901e5980a4771d032b23680fc01ad"；log=".build-cache/M8.2-verify.log"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M8.2-results.json:11；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M8.2-results.json:12

## OCR-1678 · 不采纳

- 位置：docs/testing/M8.2-results.json:10；OCR原严重度：low
- 计划：无
- 原意见：第10行明确记录被测代码未提交，且末尾 `remoteCiResult` 为 `not run`。`sourceSha256` 只能证明“当前清单列出的这些文件”的哈希，无法把该次 280 项通过结果绑定到任何可获取的提交/标签；同时它不覆盖本地 Maven 配置、依赖缓存和真实 Maven 版本等构建输入，因此该证据仅是单机单次运行，无法作为跨环境或远程复核依据。建议补充实际 commit/tag（或归档完整源码快照），并记录从日志解析出的 Maven/JDK 供应商信息及可访问的 CI 运行链接。
- 判断：本地核对 remoteCiResult="not run"；sourceSha256=对象(270键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.2-results.json:10；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M8.2-results.json:1033；docs/testing/M8.2-results.json:761

## OCR-1679 · 不采纳

- 位置：docs/testing/M8.1-results.json:11；OCR原严重度：high
- 计划：无
- 原意见：第 11 行 `log` 指向 `.build-cache/M8-verify.log`，而仓库根 `.gitignore:8` 已忽略 `.build-cache/`，即该日志不会随代码提交、仓库检出中并不存在。触发条件：任何第三方按记录执行 `sha256sum .build-cache/M8-verify.log` 与第 12 行 `logSha256` 比对时无文件可比，也就无法复核 269 个用例（尤其是 Docker/PTY/独立 JVM 用例）是否真的在本次运行中被执行。建议把构建日志作为可提交证据保留（如 `docs/testing/M8.1-verify.log`）并同步 `log`/`logSha256`，或改为把 surefire 汇总片段内嵌进本文件。
- 判断：本地核对 log=".build-cache/M8-verify.log"；logSha256="b8f2426dad3424f21a883cd7b9632a6181af17ccf092baeaa556b4108023249e"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M8.1-results.json:11；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M8.1-results.json:12

## OCR-1680 · 不采纳

- 位置：docs/testing/M8.1-results.json:890；OCR原严重度：high
- 计划：无
- 原意见：第 730-990 行的 `sourceSha256` 清单并非全树穷举，当前仓库中至少有这些被测/被编译源码没有哈希条目：`harness-ecosystem/.../ContentPages.java`、`ModelDisplayBuffer.java`、`SkillCatalog.java`、`UserQuestionsPlugin.java`、`WorkspaceContentInspection.java`；`harness-session/.../SessionLifecycle.java`、`TokenSessionSearch.java`；`harness-cli/.../NativeCredentials.java`、`StdioSdkServer.java`、`SystemCredentials.java`、`WorkbenchSettings.java`、`WorkbenchWorkspaces.java`。触发条件：清单只是代码树的子集时，无法唯一确定“被测快照”（未覆盖文件可以是任意内容），第 10 行的身份声明不成立；若这些文件是本次记录之后新增的，则说明证据文件未随仓库刷新，仍会让读者误以为清单覆盖全树。建议生成时对 `**/src/main/**`、`**/src/test/**` 做穷举扫描，并把“仓库文件集合 - 清单条目”作为差集校验，非空即失败。
- 判断：本地核对 sourceSha256=对象(259键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.1-results.json:890；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M8.1-results.json:730

## OCR-1681 · 不采纳

- 位置：docs/testing/M8.1-results.json:10；OCR原严重度：medium
- 计划：无
- 原意见：第 10 行把被测快照身份完全委托给 `sourceSha256`，但没有记录 commit SHA、tag 或脏文件清单；而工作树本身是 uncommitted 的。触发条件：需要追溯某个用例（例如 `realPtyAcceptsInputResizesAndPreservesItsWorkingDirectory`）究竟对应哪份源码、或需要复现本次构建时，无法定位到 VCS 修订。建议补充 `commit`、`dirtyFileCount`/`dirtyFiles` 以及清单生成时间，使 `sourceSha256` 可被校验（见下一条关于清单完整性的问题）。
- 判断：本地核对 sourceSha256=对象(259键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.1-results.json:10；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M8.1-results.json:730

## OCR-1682 · 不采纳

- 位置：docs/testing/M8.1-results.json:991；OCR原严重度：low
- 计划：无
- 原意见：第 991 行声明 `remoteCiResult: "not run"`，第 17 行又声明 `skipped: 0`，但记录中没有保存环境前置条件（Docker 是否可用、PTY/终端能力、独立 JVM、真实 Maven Wrapper 子进程是否真的运行）与 CI 运行标识。而用例清单包含 `realPtyAcceptsInputResizesAndPreservesItsWorkingDirectory`、`sameProcessAndSecondJvmCannotWriteWhileOwnerHoldsTheLock`、`backgroundTaskStatusLogStopAndTimeoutAreBounded` 及多个 M7 Docker/子代理/Web 用例；由于测试源码中没有使用 JUnit assumption（`*Test.java` 中无 `assumeTrue`/`@Disabled`），前置条件缺失会以「提前 return」计为通过而不是 skip，仅凭本记录无法区分“真实执行通过”与“未真正执行”。建议增加 `environment`（docker/pty/os/arch）与 CI job 链接，并对环境依赖用例记录实际执行标志。
- 判断：本地核对 milestone="M8"；slice="M8.1"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8.1-results.json:991；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1683 · 不采纳

- 位置：docs/testing/M8.4-environment-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：里程碑标识与文件名不一致：本文件是 M8.4 的环境证据（docs/testing/M8.4-environment-results.json），但 milestone 仍为 "M7"（第 2 行）。该字段由旧夹具硬编码，属于自报数据，任何按 milestone 索引/汇总证据的脚本或审计会把 M8.4 结果的归属错记到 M7，破坏可追溯性（M8-validation.md 仅对 M8-environment-regression.json 做了例外说明，未覆盖本文件）。建议改为 "M8.4"，或额外增加 probeId/jarSha 等来源标识以避免里程碑字段被旧夹具污染。
- 判断：本地核对 milestone="M7"；slice="execution-environment"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M8.4-environment-results.json:2；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1684 · 不采纳

- 位置：docs/testing/M8.4-environment-results.json:28；OCR原严重度：low
- 计划：无
- 原意见：键名 local-sdk/docker-sdk 使用连字符，与同文件其余键的 camelCase 约定（actualIsolationChecks、readOnlyRoot 等）不一致，也与 other M8.4 产物中的 camelCase 键（parentRequestsReconstructed 等）不统一，属于 key 命名/兼容性问题：按 JSONPath $.localSdk 或 POJO 属性绑定/JSON Schema 校验的消费方会静默丢键，汇总脚本也会漏统计该两侧 SDK 结果。建议统一改为 localSdk/dockerSdk（并同步生成端）。
- 判断：本地核对 milestone="M7"；slice="execution-environment"。这些键属于不同嵌套作用域/外部API原样字段或以会话标签为键的映射；JSON不要求全树单一命名风格。未发现实际消费者按统一大小写/连续step序号解析导致失败，纯样式统一不足以立为缺陷。
- 证据：docs/testing/M8.4-environment-results.json:28；docs/testing/tools/verify-m9-evidence.py:38；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java

## OCR-1685 · 不采纳

- 位置：docs/testing/M8.4-environment-results.json:27；OCR原严重度：medium
- 计划：无
- 原意见：actualDocker/actualIsolationChecks（第 6、27 行）仅是布尔断言，descriptor（第 12-26 行）中的 network=none、capabilitiesDropped=ALL、readOnlyRoot=true、user=65534:65534 同样来自同一份自报，缺少可核对的原始检查证据（如 docker inspect 的 HostConfig.ReadonlyRootfs、CapDrop、NetworkMode、User 摘要或负例结果）。触发条件：探针仍输出硬编码预期值时，这里依旧为 true，无法区分“真实检查通过”与“断言通过”。建议在产物中固定写入实际检查的输出摘要或命令与期望值对照。
- 判断：本地核对 milestone="M7"；slice="execution-environment"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8.4-environment-results.json:27；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1686 · 不采纳

- 位置：docs/testing/M8.4-environment-results.json:24；OCR原严重度：medium
- 计划：无
- 原意见：readOnlyRoot=true（第 23 行）与 user=65534:65534 同时，localTask/dockerTask 却都报告 guardedEdit=true（第 8、14 行）。descriptor 中没有可写挂载/工作区路径字段，无法证明编辑落在挂载卷内而非 guard 绕过只读根、也无法证明只读根下越界写入会被拒绝。建议补充可写挂载路径与“只读根内写入被拒绝”的负例证据，否则该条与隔离声明构成互相削弱的自报。
- 判断：本地核对 milestone="M7"；slice="execution-environment"。readOnlyRoot指容器根文件系统，任务工作区通过专用挂载提供受保护写入，非整个容器禁止所有写。guardedEdit与只读根不矛盾。
- 证据：docs/testing/M8.4-environment-results.json:24；harness-testkit/src/test/java/io/jharness/testkit/M7DockerProbe.java；harness-ecosystem/src/main/java/io/jharness/ecosystem/DockerExecutionEnvironment.java

## OCR-1687 · 不采纳

- 位置：docs/testing/M8.4-environment-results.json:34；OCR原严重度：medium
- 计划：无
- 原意见：docker-sdk（第 34-38 行）与 local-sdk（第 28-32 行）的 status/quotedShell/requestsReconstructed/durableIntents 完全相同，而 descriptor.network=none（第 13 行）下容器内本不应有对外模型流量，4 次请求重建的流量来源（宿主回放、容器内 stub 还是本地结果被复制）没有任何标识。触发条件：Docker 路径失败时回退到本地结果，产物仍显示 SUCCESS 且 actualDocker=true。建议为每侧记录流量来源标识（回放端口/容器内服务地址）并注明 4 次请求与 3 个 durable intent 的映射关系，以证明容器侧结果确实来自容器路径。
- 判断：本地核对 milestone="M7"；slice="execution-environment"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M8.4-environment-results.json:34；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1688 · 不采纳

- 位置：docs/testing/M8.4-filter-results.json:52；OCR原严重度：medium
- 计划：无
- 原意见：证据目录写入了 Windows 绝对路径，包含工作站用户目录与本地工程布局（`D:\Documents\my projects\...`），提交到仓库后会泄露开发机信息，且在其他机器/CI 上不可复现。建议改为仓库相对路径（如 `.build-cache/m8.4-filter-probe/<uuid>`），并由脚本在拼接时做路径归一化，避免把宿主绝对路径写入产物。
- 判断：本地核对 milestone="M8.1"；capturedAt="2026-10-16T01:49:58.477263800Z"。本机路径/Windows分隔符是历史运行平台记录，实际collector已有replace归一化，当前矩阵已输出POSIX路径。没有现行消费者失败证据；路径不含密钥，不把目录布局视作凭据泄漏，也不追溯改写历史取证记录。
- 证据：docs/testing/M8.4-filter-results.json:52；docs/development/M10-frontend-reuse-source.json:4；docs/testing/M10.5.9-validation.md:3

## OCR-1689 · 不采纳

- 位置：docs/testing/M8.4-filter-results.json:2；OCR原严重度：high
- 计划：无
- 原意见：里程碑标签与产物不一致：本文件名为 `M8.4-filter-results.json`、evidenceDirectory 也指向 `m8.4-filter-probe`，但 `milestone` 声称是 `M8.1`。该字段是审计归属的唯一标识，`docs/testing/M8-closure-results.json` 又按文件名哈希引用本文件，标签错位会导致 M8.1/M8.4 结论互串、回溯时无法判定该组过滤断言究竟属于哪个验收阶段。生成器 `M8FilterDockerProbe` 中该值被硬编码为 `M8.1`，建议改为可传入的里程碑参数并重新生成本产物，同时更新闭环清单中的 SHA-256。
- 判断：本地核对 milestone="M8.1"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M8.4-filter-results.json:2；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1690 · 不采纳

- 位置：docs/testing/M8.4-filter-results.json:51；OCR原严重度：medium
- 计划：无
- 原意见：`cleanupVerified` 只给出 true，却没有登记被清理对象的清单（容器 ID/名称、临时目录、残留检查方式），资源生命周期断言无法从本产物复核；若清理实际失败但仍写出 true，SUCCESS 结论会掩盖泄漏。建议补充容器 ID、临时根目录、清理前后残留数量等可校验字段。
- 判断：本地核对 cleanupVerified=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8.4-filter-results.json:51；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1691 · 不采纳

- 位置：docs/testing/M8.4-filter-results.json:9；OCR原严重度：medium
- 计划：无
- 原意见：安全相关结论全部是自证布尔值（无命令、退出码、stdout/stderr 或断言标识），且两份用例的 docker 与 local 区块逐字节相同，无法证明隔离视图一致、显式读取保留、元数据/穿越被拒是各自独立观测到的结果；一旦实现端把断言硬编码为 true，产物仍会显示 SUCCESS 而不被发现。建议为每条断言记录实际执行的命令、退出码与关键输出摘要（或证据文件相对路径），并能区分 docker/local 两次采集的来源标识。
- 判断：本地核对 milestone="M8.1"；capturedAt="2026-10-16T01:49:58.477263800Z"。原意见中可见字段本身成立，但没有证明其推测的行为故障；配套源码/阶段记录已限定作用域。仅对旧收据补充元数据或统一风格不作为本轮合理修复任务，保留历史事实字节。
- 证据：docs/testing/M8.4-filter-results.json:9；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1692 · 不采纳

- 位置：docs/testing/M8.4-audit-results.json:4；OCR原严重度：medium
- 计划：无
- 原意见：第4行 `result: SUCCESS` 没有任何限定作用域。同仓 `docs/testing/M8-closure-results.json:5` 用 `scope` 明确写出 "not a release/remote-CI certification"，而本文件既无 `scope`/`limitations` 字段，又在第50-51行记录 `remoteCi` 未运行、`macos` 未验证，第10行还排除了新上游测试、兼容性与安全认证。下游若只读 `result`（例如按约定读取 SUCCESS 即视为关闭）会把局部审计结论当成完整验收结论。建议补 `scope`/`limitations` 字段，或把值改为 `LOCAL_AUDIT_SUCCESS` 之类带范围的取值。
- 判断：本地核对 remoteCi="not run; no Git remote"；macos="not verified"。记录已区分本轮选定范围SUCCESS/PASS与releaseComplete/remoteCi/scope/后续里程碑；负例非零退出也可构成验证器成功。不能假设一个忽略其余字段的消费者据此把全PRD或新里程碑授予完成。
- 证据：docs/testing/M8.4-audit-results.json:4；.agent:当前接续状态；docs/testing/M8-closure-results.json；docs/testing/M10.5.8-validation.md:3；docs/testing/M8.4-audit-results.json:50；docs/testing/M8.4-audit-results.json:51

## OCR-1693 · 不采纳

- 位置：docs/testing/M8.4-audit-results.json:6；OCR原严重度：medium
- 计划：无
- 原意见：第6-7行只给出计数 259 和布尔结论 `sameCurrentJavaPomSources: true`，但没有 baseline 标识（commit/快照哈希）、文件清单或可复现命令，无法判断该布尔值是否基于过期或不同的基线；同时 `javaPomFiles` 的统计口径只能从 `scripts/verify-m8-closure.py:96`（`len(pom+java)`）反推，键名本身看不出统计范围。另外该键名与同仓 `M8-closure-results.json:23` 的 `sameJavaPomSources` 不一致，按后者编写的自动化会静默漏读本文件的结论。建议改用统一键名，并补 `sourceSnapshotSha256`、基线 commit 及生成脚本名/SHA256，使结论可复现。
- 判断：本地核对 javaPomFiles=259。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8.4-audit-results.json:6；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1694 · 不采纳

- 位置：docs/testing/M8.4-audit-results.json:9；OCR原严重度：low
- 计划：无
- 原意见：第9行只记录复核了 19 个条目，没有给出条目路径/哈希清单，也没有绑定到 `M7-upstream-review.json`（该文件确有 19 条 `files[].sha256`）。因此既无法验证 19 是否覆盖完整、也无法排除沿用旧证据或漏检；一旦上游条目增删，计数不变时本记录仍会显示通过。建议补充条目清单或对证据文件的显式引用（路径+内容 SHA256+commit），例如 `upstreamEntryEvidence: {"file": "docs/testing/M7-upstream-review.json", "sha256": ...}`。
- 判断：本地核对 milestone="M8"；slice="M8.4"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.4-audit-results.json:9；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1695 · 不采纳

- 位置：docs/testing/M8.4-audit-results.json:13；OCR原严重度：low
- 计划：无
- 原意见：第13-14行把两种语义不同的摘要并排存放：`gitBlob` 是 Git 对象 ID（SHA-1，含 blob 头且基于该对象的字节），`workingTreeSha256` 是文件内容的 SHA-256；二者不可互相比较，也不可交叉校验。当前没有字段说明哈希算法、`gitBlob` 对应的提交，以及 `workingTreeSha256` 的基线来源，消费者把两者当同义"内容哈希"会得出错误的保持结论。建议显式标注算法/提交，或改为统一 `gitBlob` + `baselineCommit`，并说明校验的是工作区内容还是索引内容。
- 判断：本地核对 preservedM7Files.docs/testing/M7-closure.md.gitBlob="dfa1fc98bb269974d6744982c46d472f383768be"；preservedM7Files.docs/testing/M7-closure.md.workingTreeSha256="6f0757cc39545996a6db30e8173a512326419ba44b94a7bf906676528ec8df25"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.4-audit-results.json:13；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M8.4-audit-results.json:14

## OCR-1696 · 不采纳

- 位置：docs/testing/M8.4-audit-results.json:49；OCR原严重度：medium
- 计划：无
- 原意见：第49行的 `normalized` 没有定义归一化规则。若归一化涉及行尾（CRLF/LF）、空白或编码转换，真实内容变更会被抹平，"Git 内容保持"的结论就会过度声明；而该布尔值又是本文件唯一整体的保持性结论，故必须可复核。建议补充归一化算法说明、基线 commit 与逐文件期望 blob，或改为直接比较未归一化的 gitBlob 并保留差异明细。
- 判断：本地核对 milestone="M8"；slice="M8.4"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.4-audit-results.json:49；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1697 · 不采纳

- 位置：docs/testing/M8.4-scheduling-results.json:31；OCR原严重度：medium
- 计划：无
- 原意见：evidenceDirectory 记录的是宿主机绝对路径（D:\Documents\my projects\J-Harness\.build-cache\...），既泄露了宿主机目录结构，又在 CI / 其他评审者机器上不可访问，因此该“证据目录”实际不可复现、不可核验。建议改为仓库内相对路径，或记录归档产物的 digest/附件引用，避免提交本地绝对路径。
- 判断：本地核对 milestone="M8.2"；slice="packaged-cli-web-sdk-readonly-scheduling"。本机路径/Windows分隔符是历史运行平台记录，实际collector已有replace归一化，当前矩阵已输出POSIX路径。没有现行消费者失败证据；路径不含密钥，不把目录布局视作凭据泄漏，也不追溯改写历史取证记录。
- 证据：docs/testing/M8.4-scheduling-results.json:31；docs/development/M10-frontend-reuse-source.json:4；docs/testing/M10.5.9-validation.md:3

## OCR-1698 · 不采纳

- 位置：docs/testing/M8.4-scheduling-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：文件名为 M8.4-scheduling-results.json，但记录的 milestone 值为 "M8.2"。按 milestone 字段汇总/检索验收证据时，M8.4 的调度证据将缺失，并与已有的 M8.2-scheduling-docker-results.json 混淆，无法证明 M8.4 阶段重新执行过该探针。建议改为 "M8.4"；若确需保留来源里程碑，请另设 sourceMilestone 字段。</br>触发条件：任何按 milestone 字段做证据完整性校验的脚本/发布门禁会把本文件归档到 M8.2，导致 M8.4 判定为无调度证据。
- 判断：本地核对 milestone="M8.2"；slice="packaged-cli-web-sdk-readonly-scheduling"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M8.4-scheduling-results.json:2；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1699 · 不采纳

- 位置：docs/testing/M8.4-scheduling-results.json:6；OCR原严重度：medium
- 计划：无
- 原意见：两个用例的断言全部为 true，且没有任何可核对的计数或证据引用：submissionDeduplicated、approvalBeforeWrite、environmentClosed 只是被直接写成 true，无法证明“重复提交确实被去重”“写操作前确实经过审批”，也没有覆盖 UNKNOWN 状态被提交/重放时应被拒绝的路径（本仓库的调度/恢复语义要求 UNKNOWN 不得重放）。此外 local 与 docker 两段内容逐项相同，仅有一个顶层 capturedAt，无法区分是两次独立执行还是复制粘贴。建议至少记录：提交请求数与去重后实际写入数、每步 approval 事件序号、以及一个 UNKNOWN/重放被拒绝的负向用例结果。
- 判断：本地核对 milestone="M8.2"；slice="packaged-cli-web-sdk-readonly-scheduling"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8.4-scheduling-results.json:6；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1700 · 不采纳

- 位置：docs/testing/M8.4-scheduling-results.json:4；OCR原严重度：low
- 计划：无
- 原意见：产物缺少结构版本与执行标识：没有 schemaVersion、runId、每个用例的 startedAt/环境信息（OS、JDK、docker 是否存在）以及 jar 名称；image 只放在顶层，无法区分是 local 用例还是 docker 用例实际使用了该 digest。结果是跨里程碑无法对该 JSON 做自动结构校验，也无法把 jarSha256/镜像 digest 与某一次具体执行绑定。建议补充 schemaVersion、runId，并把 image/jarSha256 下沉到对应 case（或增加 imageDigest 字段）以便复现与校验。
- 判断：本地核对 milestone="M8.2"；slice="packaged-cli-web-sdk-readonly-scheduling"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.4-scheduling-results.json:4；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1701 · 不采纳

- 位置：docs/testing/M8.4-resilience-results.json:31；OCR原严重度：medium
- 计划：无
- 原意见：第 31 行暴露了完整的主机绝对路径（`D:\Documents\my projects\...`），会泄露评审者本机的目录结构与用户名信息，且在 CI 或其他环境中该路径无法解析；更关键的是 `.build-cache/` 已被 `.gitignore` 第 8 行忽略，说明这条记录引用的证据目录并未入库，事后无法复核 `SUCCESS`。建议改为仓库相对路径，并将证据摘要（如 `evidenceSha256`、关键日志片段或证据文件清单哈希）随记录一同提交，保证可复现验证。
- 判断：本地核对 milestone="M8.3"；slice="packaged-cli-web-sdk-model-resilience"。本机路径/Windows分隔符是历史运行平台记录，实际collector已有replace归一化，当前矩阵已输出POSIX路径。没有现行消费者失败证据；路径不含密钥，不把目录布局视作凭据泄漏，也不追溯改写历史取证记录。
- 证据：docs/testing/M8.4-resilience-results.json:31；docs/development/M10-frontend-reuse-source.json:4；docs/testing/M10.5.9-validation.md:3

## OCR-1702 · 不采纳

- 位置：docs/testing/M8.4-resilience-results.json:2；OCR原严重度：high
- 计划：无
- 原意见：第 2 行里程碑标识与文件路径/证据目录不一致：文件为 `M8.4-resilience-results.json`，`evidenceDirectory` 也位于 `m8.4-resilience-probe`，但 `milestone` 写成了 `M8.3`（与已存在的 `M8.3-resilience-docker-results.json` 冲突）。触发条件：任何按 `milestone` 字段做证据聚合/追溯的解析器或审计脚本会把这份 M8.4 运行结果归入 M8.3，导致里程碑验收结论错误或 M8.4 证据缺失。根因是该字段在探针中被硬编码为常量，发布前应改为 `M8.4` 并核对生成逻辑。
- 判断：本地核对 evidenceDirectory="D:\\Documents\\my projects\\J-Harness\\.build-cache\\m8.4-resilience-probe\\resil...；milestone="M8.3"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M8.4-resilience-results.json:2；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29；docs/testing/M8.4-resilience-results.json:31

## OCR-1703 · 不采纳

- 位置：docs/testing/M8.4-resilience-results.json:27；OCR原严重度：medium
- 计划：无
- 原意见：第 27/32 行的镜像摘要与 JAR 哈希无法唯一绑定被测制品：`eclipse-temurin@sha256:...` 未记录平台/架构（多架构 index digest 在不同 host 架构上解析到不同 manifest），JAR 也未记录构建版本/提交号，且没有证据文件本身的哈希或签名。触发条件：在 arm64 与 amd64 上重复执行会得到同一 `image` 字段但不同实际运行时，评审者无法判断本次 `SUCCESS` 具体由哪个二进制组合产生。建议补充 `platform`/`architecture`（必要时 `commit`）与证据摘要字段。
- 判断：本地核对 image="eclipse-temurin@sha256:ccbad9e7cb84c27092b12c40a4143e5733e050db9c3784d84f632dcf42...。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.4-resilience-results.json:27；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1704 · 不采纳

- 位置：docs/testing/M8.4-resilience-results.json:5；OCR原严重度：medium
- 计划：无
- 原意见：第 5-25 行的用例断言未覆盖 slice 声称的“模型韧性”能力：`packaged-cli-web-sdk-model-resilience` 的定义包含 429 重试/退避，但两个用例只有 `requestsReconstructedAgainstWire`、`actualHttp` 等通用布尔/计数，缺少“是否实际收到 429”“重试次数”“是否遵守 Retry-After/退避上限”“重试不重复提交”的可核验字段。触发条件：上游模型返回 429/5xx 时，仅凭这些字段无法区分“重试成功”与“一次就成功”，也无法发现无限重试或忽略 Retry-After 的缺陷，导致 `result: SUCCESS` 缺乏针对性证据。建议补充 429 观测、重试尝试次数与退避遵守情况等断言。
- 判断：本地核对 cases.local.requestsReconstructedAgainstWire=4；cases.local.actualHttp=true。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M8.4-resilience-results.json:5；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M8.4-resilience-results.json:10；docs/testing/M8.4-resilience-results.json:9

## OCR-1705 · 不采纳

- 位置：docs/testing/M8.4-skills-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：证据文件名/路径属于 M8.4，但 `milestone` 仍写作 `M7`，且 `slice` 为 `skills-sdk` 而非 `M8.4`。该文件不在 M8.4-audit-results.json 的 preservedM7Files 哈希清单中，按 milestone 字段做归档/审计的工具会把这份 M8.4 证据错误归到 M7 里程碑（或与 M7-skills-docker-results.json 混淆），导致证据归属不可复现。建议将 `milestone` 改为实际里程碑（如 `M8.4`）并保留来源标记（如 `originMilestone: "M7"`）。
- 判断：本地核对 milestone="M7"；slice="skills-sdk"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M8.4-skills-results.json:2；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29；docs/testing/M8.4-skills-results.json:3

## OCR-1706 · 不采纳

- 位置：docs/testing/M8.4-skills-results.json:9；OCR原严重度：low
- 计划：无
- 原意见：两个环境（local / docker）记录的 `requestsReconstructed: 4` 与 `actualScriptFileEvidence: true` 完全相同，且没有任何可独立核验的细节（每个 skill 的标记文件内容/路径、脚本执行证据摘要、容器名或会话名）。仅凭该工件无法复现或证伪“脚本文件证据存在”以及请求重建计数，弱化了证据价值。建议补充 per-skill 证据字段（marker 文件相对路径与 sha256、脚本文件名）以及容器标识，便于事后核对。
- 判断：本地核对 milestone="M7"；slice="skills-sdk"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M8.4-skills-results.json:9；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1707 · 不采纳

- 位置：docs/testing/M8.4-skills-results.json:5；OCR原严重度：low
- 计划：无
- 原意见：`image` 只按 digest 固定，但未记录解析后的平台（如 `linux/amd64`）与最终容器内实际使用的 JAR 路径；结合 `jarSha256` 也无法确认容器运行的是同一产物而非挂载宿主机 JAR。建议增加 `platform`、`jarPathInContainer`（或镜像内产物来源）字段，使 Docker 运行的可复现性可被独立确认。
- 判断：本地核对 image="eclipse-temurin@sha256:ccbad9e7cb84c27092b12c40a4143e5733e050db9c3784d84f632dcf42...；jarSha256="12a8c812fc90035d6d6a6e78da8f5de3af6d3dcea28d72d54af68a9ccf37207e"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.4-skills-results.json:5；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M8.4-skills-results.json:19

## OCR-1708 · 不采纳

- 位置：docs/testing/M8.4-skills-results.json:6；OCR原严重度：low
- 计划：无
- 原意见：JSON 键命名不一致：`skills-local`/`skills-docker` 使用连字符，而同层其它键（`actualDocker`、`requestsReconstructed`、`actualScriptFileEvidence`、`cleanupVerified`）使用 camelCase。消费方按统一约定（如 `skillsLocal`）查找时会取不到值而静默判定为缺失证据。建议统一为 camelCase（`skillsLocal`/`skillsDocker`），或同时在 schema 中显式登记这些连字符键名。
- 判断：本地核对 actualDocker=true；skills-local.requestsReconstructed=4。这些键属于不同嵌套作用域/外部API原样字段或以会话标签为键的映射；JSON不要求全树单一命名风格。未发现实际消费者按统一大小写/连续step序号解析导致失败，纯样式统一不足以立为缺陷。
- 证据：docs/testing/M8.4-skills-results.json:6；docs/testing/tools/verify-m9-evidence.py:38；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java；docs/testing/M8.4-skills-results.json:4；docs/testing/M8.4-skills-results.json:8

## OCR-1709 · 不采纳

- 位置：docs/testing/M8.3-filter-regression.json:52；OCR原严重度：medium
- 计划：无
- 原意见：第52行把本机 Windows 绝对路径写入提交到仓库的证据文件，暴露了具体磁盘、用户目录和项目目录结构；同时该路径指向 .build-cache 临时目录，其他机器/CI 上通常不存在，证据无法直接审计或复现。触发条件是共享仓库或 CI 读取该证据。建议改为仓库相对路径（与第102行命令中的 .build-cache/m8.3-filter-probe 保持一致），或只保留脱敏后的 runId/哈希，避免提交本机路径。
- 判断：本地核对 milestone="M8.1"；capturedAt="2026-10-16T00:58:58.841448900Z"。本机路径/Windows分隔符是历史运行平台记录，实际collector已有replace归一化，当前矩阵已输出POSIX路径。没有现行消费者失败证据；路径不含密钥，不把目录布局视作凭据泄漏，也不追溯改写历史取证记录。
- 证据：docs/testing/M8.3-filter-regression.json:52；docs/development/M10-frontend-reuse-source.json:4；docs/testing/M10.5.9-validation.md:3

## OCR-1710 · 不采纳

- 位置：docs/testing/M8.3-filter-regression.json:2；OCR原严重度：medium
- 计划：无
- 原意见：第2行的机器可读字段与文件名（M8.3）及 evidenceDirectory（m8.3-filter-probe）不一致。虽然 docs/testing/M8-validation.md 第98行说明旧过滤夹具内部 milestone=M8.1 是历史标识，但任何只读取本 JSON、按 milestone 聚合或做发布门禁的下游工具都会把这次 M8.3 回归误归到 M8.1：M8.3 证据会被判定缺失，M8.1 则可能被错误标记为通过。建议在 JSON 中区分 reportMilestone（M8.3）与 fixtureMilestone（M8.1），或让生成器接收实际里程碑参数，而不是依赖外部文档解释。
- 判断：本地核对 milestone="M8.1"；capturedAt="2026-10-16T00:58:58.841448900Z"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M8.3-filter-regression.json:2；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1711 · 不采纳

- 位置：docs/testing/M8.4-subagent-results.json:2；OCR原严重度：high
- 计划：无
- 原意见：里程碑标识与文件名不一致：文件路径为 M8.4-subagent-results.json，但第 2 行记录 `"milestone" : "M7"`。溯源到产出方 harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java:26 可见该字段是硬编码 `result.put("milestone", "M7")`，说明这份证据实际由 M7 探针跑出，并未覆盖 M8.4 的变更。触发条件：任何按 milestone 字段聚合/索引测试证据的流程都会把它归入 M7，造成 M8.4 验收缺失证据、M7 证据被重复计数。建议由面向 M8.4 的探针产出并把 milestone 写成 M8.4（或在结果中额外声明产出探针类名与版本，避免仅凭文件名判断）。
- 判断：本地核对 milestone="M7"；slice="read-only-subagent"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M8.4-subagent-results.json:2；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1712 · 不采纳

- 位置：docs/testing/M8.4-subagent-results.json:25；OCR原严重度：medium
- 计划：无
- 原意见：`jarSha256` 与同目录 docs/testing/M7-subagent-docker-results.json 中记录的值（4f784f48…）不同，但两者的 milestone 同为 M7，文件里又没有记录被测 jar 的文件名、构建版本或 commit，导致无法判断这个哈希对应哪个构建产物、也无法确认它与 M8.4 的源码版本一致。触发条件：后续需要验证「被测试的产物 == 发布的产物」时缺少关联键。建议同时记录 jar 路径/文件名与构建版本（或 commit id），并修正 milestone 使其与产物来源一致。
- 判断：本地核对 jarSha256="12a8c812fc90035d6d6a6e78da8f5de3af6d3dcea28d72d54af68a9ccf37207e"。旧里程碑前缀是复用夹具场景名，collector校JAR/源码/截图字节后复制成新阶段记录；当前矩阵另按引擎独立目录冻结。旧名前缀/相同缓存文件的不同历史摘要不证明旧图冒充新图，不要求为了迎合现在命名规范改写历史归档。
- 证据：docs/testing/M8.4-subagent-results.json:25；scripts/collect-m1058-evidence.py:56；scripts/run-workbench-matrix.mjs:29

## OCR-1713 · 不采纳

- 位置：docs/testing/M8.4-subagent-results.json:5；OCR原严重度：low
- 计划：无
- 原意见：镜像只固定了 digest，但未记录 docker 引擎版本与目标平台（linux/amd64 或 linux/arm64）。eclipse-temurin 的 sha256 是 manifest list 摘要时，同一 digest 在不同架构上会解析到不同镜像层，复现者无法确认本次 real-Docker 运行使用的是否为同一平台镜像，从而削弱第 5 行镜像与第 6-21 行 Docker 结论之间的绑定关系。建议补充 engine 版本与平台信息。
- 判断：本地核对 milestone="M7"；slice="read-only-subagent"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.4-subagent-results.json:5；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1714 · 不采纳

- 位置：docs/testing/M8.4-subagent-results.json:6；OCR原严重度：medium
- 计划：无
- 原意见：证据不可复现：两个块只记录了布尔/计数结论，没有记录本次运行的 subagent 根目录、父/子 sessionId、artifact id 和日志路径。M7SubagentDockerProbe.java:18 使用 `"subagent-" + UUID.randomUUID()` 作为根目录且不写入结果文件，因此 `originAndArtifactVerified`、`parentRequestsReconstructed`、`parentEditVerified` 等结论事后无法被第三方复查（找不到 session JSONL 与 artifact 落盘位置）。触发条件：需要复核或审计该次验证时，只能重跑而不能核对原始日志。建议在该块中补充 session 名、根目录、parent/child sessionId、artifact id（或至少日志文件相对路径），使每项断言可定位到具体产物。
- 判断：本地核对 subagent-local.originAndArtifactVerified=true；subagent-local.parentRequestsReconstructed=4。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8.4-subagent-results.json:6；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M8.4-subagent-results.json:7；docs/testing/M8.4-subagent-results.json:11

## OCR-1715 · 不采纳

- 位置：docs/testing/M9-candidate-smoke.json:4；OCR原严重度：medium
- 计划：无
- 原意见：`status` 是手写常量，未由 `checks` 的五个布尔值推导，也没有任何声明式的判定规则（例如 status = all(checks) 且 paidRequests == 0）。因此该文件可以被单独编辑成 `"PASS"` 而与 checks 中任意 false 值并存，作为发布放行证据时无法自校验。建议要么在文件中显式记录判定规则/生成器标识（如 `"statusRule": "all(checks)==true"`），要么由生成脚本按 checks 聚合后写入并在校验脚本中断言二者一致。
- 判断：本地核对 status="PASS"；checks=对象(5键)。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M9-candidate-smoke.json:4；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M9-candidate-smoke.json:5

## OCR-1716 · 不采纳

- 位置：docs/testing/M9-candidate-smoke.json:12；OCR原严重度：low
- 计划：无
- 原意见：`modelRequests`/`paidRequests` 缺少计数口径：未说明是否包含重试、辅助/嵌入调用、缓存的重复请求，也未记录观测来源（fixture 侧收到的请求数 vs. 客户端上报）。若某条付费路径不计入 `received`，`paidRequests: 0` 就会失真而依然 PASS。建议补充计数边界说明字段（例如 `requestCountSource`、`countsRetries`）或引用可复核的请求日志摘要。另外本文件缺少同目录其他 M9 证据文件具备的 `candidateSourceCommit`/`platform` 等溯源字段，仅凭 `zipSha256` 无法把该次 PASS 绑定到具体提交，复现性证据不完整。
- 判断：本地核对 modelRequests=7；paidRequests=0。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M9-candidate-smoke.json:12；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M9-candidate-smoke.json:13

## OCR-1717 · 不采纳

- 位置：docs/testing/M9-candidate-smoke.json:10；OCR原严重度：medium
- 计划：无
- 原意见：这是一个 fail-closed 的安全不变量（取消/UNKNOWN 引用不得重放、仅允许显式恢复），但证据只有一个布尔值：没有记录被测命令与参数、被测的拒绝路径（resume 返回码 6 且无新模型请求）、显式恢复调用（verify-request + resume 读取已交付文件）以及对应日志/事件 id。任何一侧回归（例如拒绝路径被放宽、恢复走了重放）仍可能只留下 `true`，事后无法复核。建议把关键断言的实际观测值（返回码、pending 事件数、恢复前后请求计数差值、事件文件引用）作为同级的子对象写入，而不仅是 boolean。第 6-9 行其余检查项同样存在“只有结果、没有输入/期望值”的问题。
- 判断：本地核对 capturedAt="2026-10-16T06:02:39.048290+00:00"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M9-candidate-smoke.json:10；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1718 · 不采纳

- 位置：docs/testing/M8.4-web-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：该文件路径为 docs/testing/M8.4-web-results.json，表明它是 M8.4 的 Web 结果快照，但此处 milestone 记录为 "M7"，且 slice 为 "packaged-cli-web-sdk-docker"（打包 CLI/Web SDK/Docker 切片）。M8.4 的 Web 结果却携带 M7 的里程碑与打包 CLI 切片，说明该证据可能由 M7 的结果复制/沿用而来，无法证明 M8.4 自身被执行过。触发条件：任何按 milestone 归档、比对或审计测试证据的下游流程，都会把这批证据错误归因到 M7，导致溯源与结果一致性失效。请确认并更正 milestone（与文件名一致），或注明该快照的真实来源。
- 判断：本地核对 milestone="M7"；slice="packaged-cli-web-sdk-docker"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.4-web-results.json:2；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1719 · 不采纳

- 位置：docs/testing/M8.4-verifier-results.json:74；OCR原严重度：medium
- 计划：无
- 原意见：证据目录指向临时构建缓存 `.build-cache/m8.4-verifier-...`（带随机后缀），这类目录通常不入版本库且会被清理；因此本文件中的 `verifierSha256`、`windowsEvidenceSha256`、`linuxEvidenceSha256` 对下游（其他机器/CI/审计）均无法校验，哈希校验设计实际失效。触发条件：克隆仓库或清理缓存后按哈希复核证据。建议把被哈希的证据文件纳入受控位置（或附归档哈希清单与获取方式），并避免依赖可丢弃的本地缓存路径。
- 判断：本地核对 verifierSha256="6596cfdf506cf8d518c25647c80f745eb60b073a09a4975683b3b880990c9673"；windowsEvidenceSha256="c0f42503d09b22b6bf70f13dbee4d33e9dd56e77ae4703b1e02b3af383bc4f86"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.4-verifier-results.json:74；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M8.4-verifier-results.json:6；docs/testing/M8.4-verifier-results.json:7

## OCR-1720 · 不采纳

- 位置：docs/testing/M8.4-verifier-results.json:4；OCR原严重度：high
- 计划：无
- 原意见：顶层 `result` 固定为 SUCCESS，但第 72 行 `cliFailureExitCode` 为 1，两者语义直接冲突：下游无法区分「负例被按预期拒绝」与「verifier 本次运行自身失败」。触发条件：任何消费方以 `result == SUCCESS` 作为放行依据，会把一次退出码为 1 的运行当作成功。建议把「期望拒绝退出码」与「实际运行退出码」拆成独立字段，并明确 result 的判定表达式（例如 `runExitCode == 0 && 所有负例均被拒绝`）。
- 判断：本地核对 result="SUCCESS"；cliFailureExitCode=1。记录已区分本轮选定范围SUCCESS/PASS与releaseComplete/remoteCi/scope/后续里程碑；负例非零退出也可构成验证器成功。不能假设一个忽略其余字段的消费者据此把全PRD或新里程碑授予完成。
- 证据：docs/testing/M8.4-verifier-results.json:4；.agent:当前接续状态；docs/testing/M8-closure-results.json；docs/testing/M10.5.8-validation.md:3；docs/testing/M8.4-verifier-results.json:72

## OCR-1721 · 部分采纳

- 位置：docs/testing/M8.4-verifier-results.json:72；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：单个全局 `cliFailureExitCode` 没有绑定任何一次具体运行或具体负例（无 run id、无 case 引用、无命令行），因此无法复现该退出码到底来自哪次调用；也无法证明每个负例都确实以非零码退出。建议按负例记录 `caseExitCode`，并补充被调用 verifier 的参数与运行标识。
- 判断：本地核对 cliFailureExitCode=1。cliFailureExitCode是整体命令负例退出结果，negativeCases各有rejected/reason但不是逐case完整命令收据。采纳后续按用例记录实际退出码与匹配拒绝签名，不补造历史每例退出状态。
- 证据：docs/testing/M8.4-verifier-results.json:72

## OCR-1722 · 不采纳

- 位置：docs/testing/M8.4-verifier-results.json:32；OCR原严重度：critical
- 计划：无
- 原意见：该负例并未被验证器断言拒绝：驳回原因是未捕获的 `FileNotFoundError`（第 34 行），说明 verifier 在读取探针文件时直接抛异常退出，而不是走「缺少探针 → 断言拒绝」的校验分支。触发条件：用例夹具未生成时，verifier 以 traceback 崩溃，`rejected: true` 只是由崩溃顺带满足，无法区分「校验逻辑正确拒绝」与「工具/夹具损坏」。这类假阳性会掩盖缺失探针校验的回归。建议用例应断言明确的失败语义（结构化错误码/退出码 + 不生成 success manifest），并禁止把原始异常字符串当作通过依据。
- 判断：本地核对 milestone="M8"；slice="M8.4"。缺失必需探针文件导致FileNotFoundError非零退出，是fail-closed拒绝，不能仅因异常类型非自定义assert认定该负例未验证。可改进可读诊断，但缺文件并未通过。
- 证据：docs/testing/M8.4-verifier-results.json:32；docs/testing/M8.4-verifier-results.json:34；scripts/verify-m8-closure.py

## OCR-1723 · 不采纳

- 位置：docs/testing/M8.4-verifier-results.json:0；OCR原严重度：high
- 计划：无
- 原意见：安全/脱敏问题：驳回原因内嵌主机绝对路径 `D:\Documents\my projects\J-Harness\.build-cache\...`，把本地目录结构与构建缓存路径固化进受版本控制的证据文件，属于主机文件系统信息泄露；同时该字符串在 JSON 中被双重转义（`\\\\` 解码后仍是 `\\`），已不是原始异常文本也不是合法路径，复制后无法复现。触发条件：证据文件被提交、共享或上传到工单/CI 制品。建议只记录相对路径或逻辑标识，并对异常文本做统一脱敏与单次转义。
- 判断：本地核对 milestone="M8"；slice="M8.4"。本机路径/Windows分隔符是历史运行平台记录，实际collector已有replace归一化，当前矩阵已输出POSIX路径。没有现行消费者失败证据；路径不含密钥，不把目录布局视作凭据泄漏，也不追溯改写历史取证记录。
- 证据：docs/testing/M8.4-verifier-results.json:1；docs/development/M10-frontend-reuse-source.json:4；docs/testing/M10.5.9-validation.md:3

## OCR-1724 · 不采纳

- 位置：docs/testing/M8.4-verifier-results.json:10；OCR原严重度：high
- 计划：无
- 原意见：完整性缺口：顶层哈希只覆盖 verifier 与 Windows/Linux 证据，`negativeCases` 数组本身没有哈希或签名，任一条目（包括 `rejected` 布尔值）都可被无痕篡改，而文件整体仍显示 SUCCESS。此外每个负例只标注了单一平台（Windows 或 Linux，如 skipped_tests 仅 Linux、changed_log 仅 Windows），文件没有字段说明该用例在另一平台是否执行，容易被下游误读为双平台全覆盖。建议增加 `negativeCasesSha256`（或纳入签名范围），并为每个负例记录平台、输入夹具哈希与期望结果。
- 判断：本地核对 negativeCases=数组(12项)；negativeCases[0].rejected=true。这是Git跟踪的负例验收收据，未声称可抵抗仓库写者改写。文件无需为自身negativeCases追加自身签名/摘要；没有来源信任根时该要求同样无法证明不可篡改。未发现现有门禁因缺这一自引用校验而通过篡改输入。
- 证据：docs/testing/M8.4-verifier-results.json:10；docs/testing/tools/verify-m9-evidence.py:101；docs/testing/M9-ci-artifacts.json；docs/testing/M8.4-verifier-results.json:13

## OCR-1725 · 不采纳

- 位置：docs/testing/M8.4-verifier-results.json:73；OCR原严重度：medium
- 计划：无
- 原意见：该断言不可复现：仅有一个布尔值，没有记录被检查的 manifest 路径、检查时间、对应负例的运行标识，也没有记录「目录中不存在 manifest」的证据（例如目录清单/哈希或显式不存在证明）。触发条件：任意一次被拒绝的运行只要残留了旧 manifest，本字段仍可手工写成 true 而无人能发现。建议改为可核验的记录（如 `manifestPathChecked` + `manifestExistsAfterRejectedRun: false` + 对应 case 的 run id/输出目录哈希）。
- 判断：本地核对 milestone="M8"；slice="M8.4"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8.4-verifier-results.json:73；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1726 · 不采纳

- 位置：docs/testing/M8.4-verifier-results.json:9；OCR原严重度：low
- 计划：无
- 原意见：`positiveCurrentEvidence` 是无引用的裸布尔，没有指明哪一份「当前证据」（文件路径/哈希/采集时间）使其为 true，也无法与 `capturedAt`、`verifierSha256` 建立关联，因此不可验证。建议改为引用具体证据对象（路径 + 哈希）或删除该字段，由哈希字段本身表达。
- 判断：本地核对 positiveCurrentEvidence=true；capturedAt="2026-10-16T02:04:45.268296+00:00"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M8.4-verifier-results.json:9；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M8.4-verifier-results.json:5

## OCR-1727 · 不采纳

- 位置：docs/testing/M8.4-smoke-results.json:5；OCR原严重度：low
- 计划：无
- 原意见：第5-7行只记录了 jar 的 SHA-256，未记录 jar 文件名/路径、产生该 jar 的源码提交（commit）及 OS 构建号。触发条件：同一源码在不同提交或不同 Windows 11 构建上重新构建时，无法定位本结果对应的确切制品与环境，`jarSha256` 无法独立复现验证。建议补充 `jarPath`、`sourceCommit`、`osBuild`（或等价的可复现标识）。
- 判断：本地核对 jarSha256="12a8c812fc90035d6d6a6e78da8f5de3af6d3dcea28d72d54af68a9ccf37207e"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M8.4-smoke-results.json:5；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1728 · 不采纳

- 位置：docs/testing/M8.4-smoke-results.json:47；OCR原严重度：medium
- 计划：无
- 原意见：第47-52行只保存 `.build-cache/*.log` 的哈希，但 `.build-cache/` 已被仓库 `.gitignore` 排除，且未声明哈希算法、原始字节数或与第8-10行退出码的对应关系。触发条件：后续审计者克隆仓库后无法重算或确认日志内容，`result:SUCCESS` 与四份日志之间缺少可验证的绑定。建议为每个日志记录算法、字节数，并保留受控可访问的原始日志归档或提供可复核的引用。
- 判断：本地核对 milestone="M8"；slice="M8.4"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M8.4-smoke-results.json:47；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1729 · 部分采纳

- 位置：docs/testing/M8.4-smoke-results.json:29；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：第29-46行的 `unpreparedWorkspaceDiagnostic` 只记录了 `ready:false`，没有记录该次 `doctor` 进程的退出码。触发条件：负路径实际返回非 0（源码中 `ready=false` 时返回 5），但证据文件缺失 `exitCode`，审计者无法确认负路径的退出码约定是否被执行，也无法把顶层 `result:SUCCESS` 与该次预期失败对应起来。建议补充 `exitCode`/`expectedExitCode` 及匹配断言（例如 `exitCode: 5`、`exitCodeMatched: true`）。
- 判断：本地核对 unpreparedWorkspaceDiagnostic=对象(6键)；doctor=对象(6键)。unpreparedWorkspaceDiagnostic确仅有ready=false而未记录本次doctor退出码；采纳新负例收据记录expectedExitCode=5和实际退出码并核对。不将历史未保存退出码改填5。
- 证据：docs/testing/M8.4-smoke-results.json:29；docs/testing/M8.4-smoke-results.json:11

## OCR-1730 · 不采纳

- 位置：docs/testing/M8.4-smoke-results.json:16；OCR原严重度：low
- 计划：无
- 原意见：第17、37行将宿主机的 `apiKeyPresent:true` 作为持久化证据提交。虽然未输出凭据值，但仍固化了运行主机的凭据配置状态；若该证据文件进入公开仓库或被共享，会暴露宿主机是否配置了 API Key。请确认脱敏策略允许持久化该状态；如不允许，建议改为查询时的瞬时汇总或移除该明细，并确保不伴随用户名、绝对路径等主机标识。
- 判断：本地核对 milestone="M8"；slice="M8.4"。apiKeyPresent是显式doctor诊断所需布尔状态，无密钥值，项目已有诊断脱敏契约；没有发现禁止持久化此布尔的规则，不能仅凭存在性视作密钥泄漏。
- 证据：docs/testing/M8.4-smoke-results.json:16；docs/testing/M8.4-smoke-results.json:17；harness-cli/src/main/java/io/jharness/cli/CliApplication.java

## OCR-1731 · 不采纳

- 位置：docs/testing/M8.4-smoke-results.json:11；OCR原严重度：low
- 计划：无
- 原意见：第10-12行 `doctorExitCode` 为 0 且 `ready:true`，但同一 `checks` 中 `mavenExecutableFound:false`；本文件没有记录 `ready` 的判定集合（源码中 Maven 两项被有意排除）。触发条件：仅依据本证据的审计者会误判 Maven 可用，或反过来认为 doctor 漏检。建议显式记录 `readinessCriteria`/排除项，或将 Maven 检查标注为 informational，避免 `ready` 语义歧义。
- 判断：本地核对 doctorExitCode=0；doctor.checks=对象(9键)。发行包运行doctor不强制目标工作区有Maven是M9已明确修复的产品要求，mavenExecutableFound=false和ready=true不矛盾；构建能力作为独立诊断保留。
- 证据：docs/testing/M8.4-smoke-results.json:11；.agent:M9本地候选验证；docs/testing/M8.4-smoke-results.json:29；docs/testing/M8.4-smoke-results.json:10；docs/testing/M8.4-smoke-results.json:14

## OCR-1732 · 不采纳

- 位置：docs/testing/M9-ci-first-attempt.json:63；OCR原严重度：medium
- 计划：无
- 原意见：本地验证证据不可复核：`log` 指向被 git 缓存的 `.build-cache/M9-hosted-fix-verify.log`（该文件在仓库中并不存在，也不能从 Actions 产物取得），且本节没有记录被验证的提交号、生成时间与环境。因此 296/0/0/0 与 `logSha256` 无法与本次 run 的 `head_sha = cbdbdcd6be0cf3feb1ce6864b40d26908cd601ad` 建立绑定，无法证明修复是在同一提交上验证的。建议补充 `head_sha`/`commit`、生成时间戳，或把日志作为 artifact 上传并在本文件记录其坐标（同批次快照使用 `preservedLogsSha256` 按 job id 绑定哈希，可复用该做法）。
- 判断：本地核对 localVerification.log=".build-cache/M9-hosted-fix-verify.log"；localVerification.logSha256="167ea9846b892cd7cbc6e6cf758cc5cecb2ca69420d255d9b084f46b3bd08773"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M9-ci-first-attempt.json:63；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M9-ci-first-attempt.json:64

## OCR-1733 · 不采纳

- 位置：docs/testing/M9-ci-first-attempt.json:62；OCR原严重度：low
- 计划：无
- 原意见：键名风格不一致：`run`/`jobs`/`artifacts` 全部使用 snake_case（`head_sha`、`html_url`、`size_in_bytes`、`created_at`），而本节使用 camelCase（`localVerification`、`logSha256`）。仓库内的证据校验脚本（docs/testing/tools/verify-m9-evidence.py）按 snake_case 读取 `run` 字段，混合风格会让后续自动化消费同一 schema 时出现键名遗漏。建议统一为 `local_verification` / `log_sha256`（或全文件统一 camelCase）。
- 判断：本地核对 run=对象(7键)；jobs=数组(3项)。这些键属于不同嵌套作用域/外部API原样字段或以会话标签为键的映射；JSON不要求全树单一命名风格。未发现实际消费者按统一大小写/连续step序号解析导致失败，纯样式统一不足以立为缺陷。
- 证据：docs/testing/M9-ci-first-attempt.json:62；docs/testing/tools/verify-m9-evidence.py:38；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java；docs/testing/M9-ci-first-attempt.json:2；docs/testing/M9-ci-first-attempt.json:11

## OCR-1734 · 不采纳

- 位置：docs/testing/M9-ci-first-attempt.json:66；OCR原严重度：medium
- 计划：无
- 原意见：同一文件内 `failures` 键被赋予两种互不兼容的语义与类型：顶层（第 42 行）是失败原因字符串数组，此处是计数整数。任何按顶层结构遍历 `failures` 的消费方（脚本/审计工具）拿到数字会在解析或遍历时报错；同批次快照如 `docs/testing/M9-ci-second-attempt.json` 也没有该计数键。建议把本地统计计数改为 `failed` 或 `failureCount`，并统一顶层失败原因与本地计数的命名。
- 判断：本地核对 failures=数组(2项)。这些键属于不同嵌套作用域/外部API原样字段或以会话标签为键的映射；JSON不要求全树单一命名风格。未发现实际消费者按统一大小写/连续step序号解析导致失败，纯样式统一不足以立为缺陷。
- 证据：docs/testing/M9-ci-first-attempt.json:66；docs/testing/tools/verify-m9-evidence.py:38；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java；docs/testing/M9-ci-first-attempt.json:57

## OCR-1735 · 不采纳

- 位置：docs/testing/M9-ci-first-attempt.json:46；OCR原严重度：medium
- 计划：无
- 原意见：产物体积严重失衡：ubuntu 为 10,793,483 字节，而失败的 macOS/Windows 仅 148,083 / 163,434 字节（相差约 70 倍）。失败 job 很可能只上传了截断的 `test-results`（缺少完整 surefire XML 或日志），而本文件的失败结论与后续 `fix` 推断都基于这些产物，据此定位失败原因有误判风险。同时 artifacts 未记录 `jobId`/`attempt`/`expires_at`，3 个 job 与 3 个 artifact 仅靠命名对应。建议标注产物完整性（是否含完整 XML/日志）并显式关联 job id，或对失败 job 的产物单独说明其覆盖范围。
- 判断：本地核对 fix="canonicalize three fixture roots; explicitly use UTF-8 in process probe and match...。失败job制品更小、GitHub step号跳跃、artifact数字ID和创建时间排序、历史分支后续变化均不构成快照错误。记录绑定run/head_sha，GitHub托管runner标识不等于凭据；没有实际对应API/制品不一致证据。
- 证据：docs/testing/M9-ci-first-attempt.json:46；docs/testing/M9-ci-first-attempt.json；docs/testing/M9-ci-artifacts.json；docs/testing/M9-ci-run.json；docs/testing/M9-ci-first-attempt.json:61

## OCR-1736 · 不采纳

- 位置：docs/testing/M9-ci-first-attempt.json:61；OCR原严重度：high
- 计划：无
- 原意见：修复说明称“canonicalize three fixture roots”以通过既有的 control-directory 安全校验，这实质是让真实路径去满足边界检查，存在放宽/绕过路径边界（符号链接、Windows junction、UNC 别名、`..` 相对路径）的风险。本文件仅有自由文本结论，既没有列出被规范化的三个根目录，也没有记录规范化前后仍拒绝越界路径的断言证据；而 `localVerification.tests` 仍为 296，与“add symlink/junction rejection check”新增检查难以对上，`preserve all assertions`/`preserve product behavior` 无法核验。建议在本快照中逐条记录：新增断言名称、对应测试用例（module:class#case）与计数变化，以及规范化函数是否先解析再校验、校验失败时是否仍抛拒绝（而非静默回退到宽松路径）。
- 判断：本地核对 localVerification=对象(6键)。macOS系统临时目录路径别名导致严格拒绝，夹具toRealPath规范化保留产品链接保护，并补symlink/junction反例；不是放宽产品边界。
- 证据：docs/testing/M9-ci-first-attempt.json:61；.agent:M9真实CI首次失败与修复中；docs/testing/M9-ci-first-attempt.json；docs/testing/M9-ci-first-attempt.json:62

## OCR-1737 · 不采纳

- 位置：docs/testing/M9-ci-jobs.json:87；OCR原严重度：medium
- 计划：无
- 原意见：Step numbering is not contiguous: the main steps end at `number: 8` ("Upload test evidence"), while the post-run steps start at `number: 14`. In the raw Actions jobs API the `steps` array is numbered 1..N without gaps, so this payload has had steps 9-13 stripped/edited (consistently in all three jobs). Any evidence built on this file (e.g. `verify-m9-evidence.py` only asserts the conclusions of "Validate release gates and install candidate"/"Upload test evidence" and never inspects step numbers) would therefore claim full step coverage while five steps per job are silently missing — the snapshot cannot be trusted for release gating. Re-capture the payload unmodified, or record explicitly which steps were redacted and why.
- 判断：本地核对 jobs[0].steps=数组(12项)。这些键属于不同嵌套作用域/外部API原样字段或以会话标签为键的映射；JSON不要求全树单一命名风格。未发现实际消费者按统一大小写/连续step序号解析导致失败，纯样式统一不足以立为缺陷。
- 证据：docs/testing/M9-ci-jobs.json:87；docs/testing/tools/verify-m9-evidence.py:38；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java；docs/testing/M9-ci-jobs.json:21

## OCR-1738 · 不采纳

- 位置：docs/testing/M9-ci-jobs.json:123；OCR原严重度：low
- 计划：无
- 原意见：This documentation artifact persists internal runner/infra identifiers (`runner_id`, `runner_name`, `node_id`, `check_run_url`) verbatim. No tokens or credentials are exposed here, but these values are not needed by the verifier (it only reads `name`, `id`, `conclusion`, `run_id`, `steps[].name/conclusion`), so they are needless internal-metadata exposure in a repo-committed file. Consider minimizing the snapshot to the fields actually consumed, or documenting an explicit retention/redaction decision for runner identifiers.
- 判断：本地核对 jobs[0].runner_id=1000000012；jobs[0].runner_name="GitHub Actions 1000000012"。失败job制品更小、GitHub step号跳跃、artifact数字ID和创建时间排序、历史分支后续变化均不构成快照错误。记录绑定run/head_sha，GitHub托管runner标识不等于凭据；没有实际对应API/制品不一致证据。
- 证据：docs/testing/M9-ci-jobs.json:123；docs/testing/M9-ci-first-attempt.json；docs/testing/M9-ci-artifacts.json；docs/testing/M9-ci-run.json；docs/testing/M9-ci-jobs.json:124

## OCR-1739 · 不采纳

- 位置：docs/testing/M9-ci-run.json:11；OCR原严重度：medium
- 计划：无
- 原意见：该证据文件是对 GitHub Actions 运行 API 的离线快照，但缺少可自证的来源与采集元数据：没有 schema 版本、采集时间、采集端点（如 GET /repos/{owner}/{repo}/actions/runs/{id}），也没有 repository/workflow_id。后果：下游无法判断该 JSON 是 API 原样落盘还是人工编辑，也无法在 GitHub API 字段增删（格式漂移）时区分“上游字段变化”与“本地误改”；一旦仓库改名/转移，第 9 行硬编码的 html_url 与校验脚本的字符串拼接断言会静默失配。建议补充 schemaVersion、capturedAt、sourceEndpoint/repository 等固定键（校验脚本仅按需读取现有键，新增键不破坏兼容）。
- 判断：本地核对 status="completed"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M9-ci-run.json:11；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1740 · 不采纳

- 位置：docs/testing/M9-ci-run.json:4；OCR原严重度：low
- 计划：无
- 原意见：记录了 head_branch 却没有记录该分支在采集时刻的 head 是否仍等于 head_sha（也无 run_attempt 之外的替代运行信息）。分支 codex/m9-release-validation 之后仍可能被推进或该运行被同分支新提交取代，此时本文件仍以 conclusion=success 的形式被当作发布证据引用，读者无法仅凭产物判断“此次 success 是否对应仍受验证的提交”。建议显式记录分支采集时的 head sha 并标注是否与 head_sha 一致（或去掉分支名、仅保留被固定的提交），以避免证据被当作对分支当前状态的背书。
- 判断：本地核对 status="completed"。失败job制品更小、GitHub step号跳跃、artifact数字ID和创建时间排序、历史分支后续变化均不构成快照错误。记录绑定run/head_sha，GitHub托管runner标识不等于凭据；没有实际对应API/制品不一致证据。
- 证据：docs/testing/M9-ci-run.json:4；docs/testing/M9-ci-first-attempt.json；docs/testing/M9-ci-artifacts.json；docs/testing/M9-ci-run.json

## OCR-1741 · 部分采纳

- 位置：docs/testing/M9-ci-fourth-attempt.json:37；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：该节只有聚合计数，缺少运行平台、执行时间与用例名：Windows 作业 (116832759648) 仍为 failure，而这里 9 个本地用例零失败，容易被误读为“Windows 失败已由本地用例覆盖验证”。另外整个单据没有 run_attempt / workflow 字段，无法确认这份产物确实对应第四次尝试。建议补充 `platform`、`startedAt`/`finishedAt`、`caseNames`，以及 run 级别的 `run_attempt`。
- 判断：本地核对 "localRelatedTests": {     "tests": 9,     "failures": 0,。该文件清楚保留Windows job失败和本地9项专项通过，二者不矛盾。采纳本地专项结果补caseIds/来源/命令和时间，不能用本地通过替代同提交三平台CI，最终另有第五轮真实通过。
- 证据：docs/testing/M9-ci-fourth-attempt.json:37；docs/testing/M9-ci-fourth-attempt.json；docs/testing/M9-ci-results.json

## OCR-1742 · 不采纳

- 位置：docs/testing/M9-ci-fourth-attempt.json:36；OCR原严重度：medium
- 计划：无
- 原意见：该键与同系列单据不一致：`docs/testing/M9-ci-third-attempt.json` 第36行使用 `windowsLogSha256`，此处改为 `logSha256`，且与第42行 `localRelatedTests.logSha256` 同名。读者无法判断这个哈希对应的是 Windows 失败作业日志还是本地产物日志，证据链不可追溯。建议恢复为 `windowsLogSha256`，并把本地日志哈希改名为 `localTestsLogSha256`。
- 判断：本地核对 logSha256="392473ca518e2f6f002d04e938e436ddb1463cf8495b771652ebb136ce2f55bf"；localRelatedTests=对象(5键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-ci-fourth-attempt.json:36；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M9-ci-fourth-attempt.json:37

## OCR-1743 · 不采纳

- 位置：docs/testing/M9-ci-fourth-attempt.json:34；OCR原严重度：medium
- 计划：无
- 原意见：两条记录暴露的是配置选择缺陷而非单纯时限不足：第三次尝试已声明“真实修复使用 20 秒命令 / 45 秒 agent 预算”，本次仍然出现“真实 Wrapper 命令被 short-process fixture 的 20 秒上限截断”，说明真实修复作业依旧被短进程 fixture 的错误上限约束，本次 fix 只是把真实作业放宽到 2 分钟命令 / 5 分钟 agent，并没有说明如何阻止错误 fixture 再次套用。风险有两点：(1) 真实修复路径从此不再受任何超时/取消用例约束，真实的死锁或挂起会被 5 分钟 agent 预算掩盖，TIMEOUT 语义回归无法被发现；(2) “short-process、timeout、cancellation 保留原上限”“编译失败/守护修复/重建断言保留”只是文字声明，产物中没有逐用例的名称与超时值可供核对。建议在产物中给出 job/run_attempt 与逐用例（名称 + commandTimeout + agentBudget）证据，证明只有真实修复用例使用放宽后的上限。
- 判断：本地核对 "failure": "Windows preparation confirmed cached Maven3.9.9 and took about 27 seconds; the following native Wrapper command s。这些是独立保留的第三/第四次失败尝试，后续已真实调整仅Maven场景预算并在同一提交三平台最终通过；旧失败记录不应改写成最后修复结果，也不能将历史尝试当当前未修复缺陷。
- 证据：docs/testing/M9-ci-fourth-attempt.json:34；.agent:M9第四轮Windows仍在真实Maven构建前20秒期限超时；docs/testing/M9-ci-results.json

## OCR-1744 · 不采纳

- 位置：docs/testing/M9-ci-artifacts.json:45；OCR原严重度：medium
- 计划：无
- 原意见：该 `test-results-windows-latest` 记录的 artifact id（12028196762）小于 ubuntu（12028241155）与 macos（12028640958），但其 `created_at`（06:02:54Z）晚于两者，且与 M9-ci-jobs.json 中 windows 任务 `Upload test evidence` 步骤（06:02:52Z→06:02:54Z）吻合。GitHub artifact id 为全局单调递增，按 ubuntu→macos 的 id 增速（约 1.5 万/秒）反推，12028196762 对应约 05:59:13Z，比 windows 实际上传早约 3.5 分钟。因此 id 与 created_at/url 至少有一处转录错误；`verify-m9-evidence.py` 仅按 `name` 取 artifact 再用该 id 拼接 `artifactUrl` 并写入 M9-ci-results.json，无法发现此错配，会使回执指向错误/不存在的归档，破坏证据可追溯性。请从 Actions artifacts API 重新导出该条记录，并核对 id、node_id（base64 中编码的 id）、url/archive_download_url 与 created_at 的一致性后同步修正 M9-ci-results.json。
- 判断：本地核对 artifacts[0].created_at="2026-10-16T05:59:43Z"；artifacts[0].name="test-results-macos-latest"。失败job制品更小、GitHub step号跳跃、artifact数字ID和创建时间排序、历史分支后续变化均不构成快照错误。记录绑定run/head_sha，GitHub托管runner标识不等于凭据；没有实际对应API/制品不一致证据。
- 证据：docs/testing/M9-ci-artifacts.json:45；docs/testing/M9-ci-first-attempt.json；docs/testing/M9-ci-artifacts.json；docs/testing/M9-ci-run.json；docs/testing/M9-ci-artifacts.json:13；docs/testing/M9-ci-artifacts.json:7

## OCR-1745 · 不采纳

- 位置：docs/testing/M9-ci-third-attempt.json:4；OCR原严重度：low
- 计划：无
- 原意见：键名风格混杂：run 内使用 snake_case（head_sha / html_url / created_at），而顶层却使用 camelCase（windowsLogSha256、localRelatedTests、logSha256）。若下游脚本按单一约定解析（例如统一驼峰或不区分大小写映射为 headSha / head_sha），字段将取不到值。建议统一命名约定，或在文件中显式声明 schema 版本以固定消费方契约。
- 判断：本地核对 "head_sha": "00a66862ccbb33fac68a52543a885087f88eb4fe",     "html_url": "https://github.com/nixaveq141-ai/J-Harness/actions/r。这些键属于不同嵌套作用域/外部API原样字段或以会话标签为键的映射；JSON不要求全树单一命名风格。未发现实际消费者按统一大小写/连续step序号解析导致失败，纯样式统一不足以立为缺陷。
- 证据：docs/testing/M9-ci-third-attempt.json:4；docs/testing/tools/verify-m9-evidence.py:38；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java

## OCR-1746 · 不采纳

- 位置：docs/testing/M9-ci-third-attempt.json:36；OCR原严重度：medium
- 计划：无
- 原意见：windowsLogSha256 与 localRelatedTests.logSha256 只有摘要值，缺少“摘要对象”的界定（是整个 job 日志、失败步骤片段还是提取后的日志文件）与可回溯的 artifact/job 链接。两个哈希同名不同作用域，容易被误读或互换，进而导致后续以错误日志作为根因证据。建议为每个摘要补充 scope 字段（如 windows-job-log / local-test-log）及对应 artifact URL，并说明使用的是完整日志还是裁剪后日志。
- 判断：本地核对 "windowsLogSha256": "afbba07f7799b2bfc61b84762116d591daf8a8914701a5a7a8ef635a30c7ed57",   "localRelatedTests": {     "tests":。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-ci-third-attempt.json:36；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1747 · 不采纳

- 位置：docs/testing/M9-ci-third-attempt.json:34；OCR原严重度：low
- 计划：无
- 原意见：failure 字段把「Windows 首个 20 秒截止无输出返回 TIMEOUT」这一根因与「Linux/macOS 通过但不与另一 commit 合并」这一跨平台说明拼在同一字符串里，既无法被自动化校验（例如按 job 名 / 退出码 / 超时阈值做断言），也容易在后续复盘中把“通过但证据不匹配”误读为根因之一。建议拆分为结构化字段，如 failedJob / failedStep / timeoutSeconds / observedOutput，并在其中显式标注哪些平台的证据可合并。
- 判断：本地核对 "failure": "Hosted Windows real offline Wrapper repair fixture spent its first 20-second command deadline without output and 。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-ci-third-attempt.json:34；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1748 · 不采纳

- 位置：docs/testing/M9-ci-third-attempt.json:37；OCR原严重度：medium
- 计划：无
- 原意见：本地证据（localRelatedTests）缺少可复现锚点：没有记录本地测试所针对的 commit（head_sha）、执行命令、JVM/Maven 版本与执行时间戳，而 failure 文本又写明 Linux/macOS 通过结果「不与另一个 commit 合并」，因此无法判断这 9 个通过的用例是否运行在同一份 00a66862… 代码上。建议补充 commit/命令/时间等字段，使“本地 9/9 通过”能与 CI 的 head_sha 建立可验证的对应关系。
- 判断：本地核对 "localRelatedTests": {     "tests": 9,     "failures": 0,。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-ci-third-attempt.json:37；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1749 · 不采纳

- 位置：docs/testing/M9-ci-third-attempt.json:35；OCR原严重度：high
- 计划：无
- 原意见：修复方案与已观测的失败原因自相矛盾：失败本身是「20 秒命令超时、无输出」，而 fix 保留同样的 20 秒命令预算 / 45 秒 agent 预算，同时又新增了「预置 Maven 3.9.9 Wrapper 缓存与原生 launcher」这一步。缓存/launcher 首次准备（下载 + 解压 + JVM 冷启动）通常远超 20 秒，按此方案重跑极可能再次触发同样的 TIMEOUT，无法验证修复是否成立。建议在 fix 中明确区分「预热步骤使用独立预算」与「被测命令仍限 20 秒」，或给出可行的预热产物缓存（如 actions/cache 命中后的实测耗时）。
- 判断：本地核对 "fix": "prepare the real existing Maven3.9.9 Wrapper cache and native launcher first, using the same minimal process environm。这些是独立保留的第三/第四次失败尝试，后续已真实调整仅Maven场景预算并在同一提交三平台最终通过；旧失败记录不应改写成最后修复结果，也不能将历史尝试当当前未修复缺陷。
- 证据：docs/testing/M9-ci-third-attempt.json:35；.agent:M9第四轮Windows仍在真实Maven构建前20秒期限超时；docs/testing/M9-ci-results.json

## OCR-1750 · 不采纳

- 位置：docs/testing/M9-ci-second-attempt.json:39；OCR原严重度：medium
- 计划：无
- 原意见：证据可核验性不足（第 39-43 行）：`preservedLogsSha256` 只给出 `job id → 摘要` 的映射，没有记录每个摘要对应的日志文件名/路径/字节数或 artifact id，也没有说明摘要的采集方式（原始 stdout？压缩包？），任何人都无法复算校验。同时第 36 行 failure 文本声称结论「confirmed in original JUnit XML」，但本文件未记录该 JUnit 原始件的 artifact id/digest（对比 first-attempt 文件是记录 artifacts 的），一旦 artifact/日志过期，Windows 结论就失去可核验证据。触发条件：审计人员按本文件复算摘要或事后回溯 Windows 失败原因时，无法完成校验。建议为每个 job 记录 `{log_path, size_in_bytes, sha256}` 以及对 `test-results-*-latest` artifact 的 id/digest，并统一键名风格（其余键为 snake_case，此处为 camelCase，下游解析容易漏读）。
- 判断：本地核对 preservedLogsSha256=对象(3键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-ci-second-attempt.json:39；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1751 · 不采纳

- 位置：docs/testing/M9-ci-second-attempt.json:34；OCR原严重度：medium
- 计划：无
- 原意见：失败项与 job 未建立结构化关联（第 34-38 行）：`failures` 是自由文本，不含 `jobs[].id`；且第三条使用 “Linux”，而 job 名为 `verify (ubuntu-latest)`（第 21 行），数组顺序（macOS / Windows / Linux）也与 `jobs` 顺序（macOS / ubuntu / windows，第 12-32 行）不一致。这使「每个失败是否都有对应失败 job、是否有遗漏或重复」无法自动校验，只能人工猜对应关系。触发条件：下游脚本或审计者按顺序把 failures[i] 归到 jobs[i] 时，会把 Windows 失败错配到 ubuntu job。建议改为对象数组，例如 `[{"job_id": 116826373036, "platform": "macos-latest", "summary": "..."}]`，与 `jobs[].id` 及 `preservedLogsSha256` 的键一一对齐。
- 判断：本地核对 failures=数组(3项)；jobs=数组(3项)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-ci-second-attempt.json:34；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M9-ci-second-attempt.json:11

## OCR-1752 · 部分采纳

- 位置：docs/testing/M9-ci-second-attempt.json:3；OCR原严重度：low
- 计划：R0-02 / P3
- 原意见：记录缺少尝试序号字段（第 3 行）：文件名标称 “second-attempt”，但 `run` 对象只有 `id`/`head_sha`，没有 `run_attempt`（也没有 workflow/event），因此「这是第几次尝试」这一关键语义没有落库。仓库内 first/third/fourth-attempt 文件各自对应不同 `run.id`，而 GitHub 对同一 run 的 rerun 会保持 `run.id` 不变、只递增 `run_attempt`，所以仅凭本文件既无法判断它是不是某次 run 的重跑，也无法确定与其它 attempt 文件的先后关系，回溯证据时容易错引。触发条件：需要在 M9 证据链中定位“哪个 run 的哪一次尝试”时无法区分。建议补充 `run_attempt`（例如 `"run_attempt": 2`），并明确 attempt 的计数口径（同一 run 的重跑还是新建 run）。
- 判断：本地核对 run=对象(7键)；run.id=38855644393。second-attempt是项目第2次独立run，不能直接按建议填GitHub run_attempt=2；GitHub rerun计数与项目跨run轮次不同。采纳明确projectAttempt与runAttempt两个字段，历史缺真实run_attempt不猜填。
- 证据：docs/testing/M9-ci-second-attempt.json:3；docs/testing/M9-ci-third-attempt.json；docs/testing/M9-ci-second-attempt.json:2

## OCR-1753 · 不采纳

- 位置：docs/testing/M9-candidate-manifest.json:7；OCR原严重度：medium
- 计划：无
- 原意见：sourceSha256 的构建输入覆盖不完整：清单收录了 `.github/workflows/ci.yml`、`pom.xml`、`scripts/*`，但遗漏了仓库中真实存在且直接决定构建结果的工具链文件 `mvnw`、`mvnw.cmd` 与 `.mvn/wrapper/maven-wrapper.properties`（`scripts/collect-test-evidence.ps1` 的 build-input 列表也把这三者列为构建输入）。触发条件：任何人修改 wrapper 的 distributionUrl/distributionSha256Sum 或改坏 `mvnw`，用本清单做校验时不会有任何哈希失配，仍会被判定为“干净的候选基线”，从而使“sourceSha256 覆盖全部构建输入”的可复现性断言失效（属供应链完整性缺口）。建议在 sourceSha256 中补入这三个文件（与生成脚本的白名单保持同步），保证清单键集合与构建输入集合一致。
- 判断：本地核对 ".github/workflows/ci.yml": "1be6f369a02863b4ec09199ab8c4c473fc353afe1255f587248f059915b0de66",     "examples/broken-project/。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-candidate-manifest.json:7；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1754 · 不采纳

- 位置：docs/testing/M9-hosted-linux-security.json:4；OCR原严重度：medium
- 计划：无
- 原意见：这份证据只记录了 BOM 的哈希，缺少 BOM 路径/大小以及实际发送给 OSV 的查询集合（或查询数/查询摘要），因此单看此文件无法复算“被扫描的输入到底是什么”，也无法判断该哈希对应的 BOM 是否就是本次构建产物所用的 BOM（真实校验依赖 verify-m9-evidence.py 里对 artifact zip 中 bom.json 的隐式比对）。建议补充 bomPath、bomBytes 以及查询计数（如 queriedCount/queryDigest），使证据可自校验、可重现。
- 判断：本地核对 capturedAt="2026-10-16T05:59:07.045161+00:00"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-hosted-linux-security.json:4；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1755 · 不采纳

- 位置：docs/testing/M9-hosted-linux-security.json:5；OCR原严重度：low
- 计划：无
- 原意见：该 PASS 只覆盖 18 个 Maven 坐标（endpoint 为 OSV /v1/querybatch，generator 明确只提取 group != io.jharness 的 Maven 组件），文件名却称 “hosted-linux-security”。Linux 基础镜像/OS 包（glibc、JDK 发行版、证书包等）完全不在扫描范围内，下游读者容易把 PASS 误读为整机安全通过。建议在文件中显式声明范围，例如增加 "scope": "maven-only" 与 "osPackagesCovered": false，避免超出实际证据的声明。
- 判断：本地核对 capturedAt="2026-10-16T05:59:07.045161+00:00"；status="PASS"。third-party README和M9验收明确扫描只覆盖Maven依赖、不覆盖OS/JDK/构建插件完整依赖。hosted-linux-security是来源平台文件名，不等同整机安全认证，无法据想象中的忽略scope消费者立缺陷。
- 证据：docs/testing/M9-hosted-linux-security.json:5；third-party/README.md:29；docs/testing/M9-validation.md

## OCR-1756 · 不采纳

- 位置：docs/testing/M9-hosted-linux-security.json:12；OCR原严重度：low
- 计划：无
- 原意见：Jackson 同族版本不一致：jackson-annotations 为 2.21，而 jackson-core / jackson-databind 为 2.21.7。安全门禁记录中出现同族版本错位，说明 BOM 的 Jackson 版本未统一对齐；若 annotations 实际不是与 2.21.7 匹配的版本，存在运行时 NoSuchMethodError 等兼容风险，且后续 CVE 比对是按坐标精确匹配的，版本错位会导致漏扫。建议在 BOM 中统一 Jackson 家族版本并在此记录一致的值。
- 判断：本地核对 capturedAt="2026-10-16T05:59:07.045161+00:00"；status="PASS"。本地保留的BOM与扫描记录均为jackson-annotations 2.21，core/databind为2.21.7；同族补丁号不同不等于版本被截断，无依据把annotations改为2.21.7。
- 证据：docs/testing/M9-hosted-linux-security.json:12；docs/testing/M10.5.9-osv-results.json:13；docs/testing/M9-hosted-linux-sbom.json

## OCR-1757 · 不采纳

- 位置：docs/testing/M9-evidence-audit-results.json:2；OCR原严重度：medium
- 计划：无
- 原意见：顶层 PASS/positiveVerified 未绑定任何可复核输入：没有 runId、源码提交、被审证据文件哈希、执行命令与时间戳、也没有对本文档的签名。触发条件：任何人只凭本文件（或仅引用它的 M9-closure.md:10）即可宣称离线核验通过，却无法确认对应哪次运行、哪份证据、哪个校验器；替换 .build-cache/M9-hosted-final 下的输入或修改脚本后本文件仍原样“PASS”，消费方无法发现篡改或过期。建议在此处补充 runId、commit、被审证据整体 SHA、命令、时间戳及生产者标识，并由 CI 重新计算比对。
- 判断：本地核对 status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M9-evidence-audit-results.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1758 · 不采纳

- 位置：docs/testing/M9-evidence-audit-results.json:6；OCR原严重度：high
- 计划：无
- 原意见：9 个负例只记录 rejected=true 与 exitCode=1，缺少“期望拒绝原因”和触发条件（被篡改/缺失的具体文件及前后哈希）。触发条件：docs/testing/tools/verify-m9-evidence.py 内部判定全部依赖 assert（如第 65 行 sha(raw_zip)==artifact digest、第 98 行候选 ZIP SHA），因此任何 AssertionError 以及 KeyError/FileNotFoundError（例如缺少 windows.zip、缺 job 日志、JSON 结构变化）都会以退出码 1 结束；崩溃或环境缺失会被一并记录成“按预期拒绝”，负例集合无法区分“意图失败的断言”与“意外错误”。建议每条用例记录期望错误标识或 stderr 关键片段、被改动文件及前后 SHA，并让校验器用不同于断言失败的退出码（如 2/3）表示运行错误，再断言该负例确实因预期原因被拒绝。
- 判断：本地核对 status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M9-evidence-audit-results.json:6；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1759 · 不采纳

- 位置：docs/testing/M9-evidence-audit-results.json:51；OCR原严重度：medium
- 计划：无
- 原意见：toolSha256 未绑定工具身份：没有工具路径、版本/提交信息，仓库中也没有声明该值的来源出处（verify-m9-evidence.py 本身不产出本文件与该字段）。触发条件：校验脚本被修改后本文件可原样保留仍显示“工具摘要 71bdbe…”，摘要无法阻止工具替换，也无法核实这个值对应的是哪个版本的脚本；比对时甚至可能被自哈希（被改脚本输出旧值）欺骗。建议同时记录 toolPath、toolVersion/仓库提交，以及由 CI 独立计算并与 Git 中文件内容比对的 SHA；键名与其他证据文件的 collectionToolSha256 保持一致，便于消费方解析。
- 判断：本地核对 status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-evidence-audit-results.json:51；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1760 · 不采纳

- 位置：docs/testing/M9-evidence-audit-results.json:52；OCR原严重度：medium
- 计划：无
- 原意见：metadataRestored/archiveRestored 只断言 true，没有恢复目标路径、恢复后逐文件 SHA 或执行命令。触发条件：恢复只覆盖归档而元数据（run/job/平台字段）仍为修改后状态，或恢复内容与原始 artifact 不一致时，只要布尔判定条件写得不严就会记录为 true；恢复完整性与幂等性因此完全不可复核，与 M9-validation.md:51 声称“可选原始 job 日志存在时也核对”不一致。建议记录恢复的路径清单、恢复后 SHA 及来源 artifact/job 链接，使失败可定位、可重放。
- 判断：本地核对 status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M9-evidence-audit-results.json:52；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1761 · 不采纳

- 位置：docs/testing/M9-ci-results.json:136；OCR原严重度：medium
- 计划：无
- 原意见：Summary claims are written as constant literals with no derivation or cross-validation against the recorded data: `allSourceAndTestNamesMatch` (here), `paidModelRequests: 0` (next line) and all seven `checks.*` flags in each platform block. Trigger: editing `"tests": 305` or flipping `"failures": 1` inside a platform leaves every gate `true` and the status `SUCCESS`, so the file cannot detect an internally inconsistent record; likewise `paidModelRequests` is not recomputable because per-platform `modelRequests`/`paidRequests` (7 / 0 in `M9-hosted-*-smoke.json`) are not carried over. Suggest deriving these aggregates from the per-platform fields (e.g. `all(p.totals.failures == 0 ...)`, `sum(modelRequests)`), or at minimum recording the per-platform request counts so the top-level total can be recomputed.
- 判断：本地核对 allSourceAndTestNamesMatch=true；paidModelRequests=0。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M9-ci-results.json:136；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M9-ci-results.json:137

## OCR-1762 · 不采纳

- 位置：docs/testing/M9-ci-results.json:156；OCR原严重度：medium
- 计划：无
- 原意见：`status: SUCCESS` plus all-true gates is unqualified, and the only statement of what was *not* verified is this free-text sentence at the end of the file. Automated consumers (dashboards, release tooling) cannot parse it, so the record can be read as full cross-platform / release readiness even though Windows 10, independent WSL and Release publication were never exercised. Suggest adding machine-readable limitation fields (e.g. `"releasePublished": false`, `"untestedPlatforms": ["windows-10", "wsl"], "testedRunnerLabels": [...]`) and a status such as `SUCCESS_WITH_DECLARED_SCOPE` so the disclaimer cannot be lost when the record is quoted.
- 判断：本地核对 milestone="M9"；scope="Hosted Windows/Linux/macOS on exact recorded OS/architecture; Windows10 and indep...。记录已区分本轮选定范围SUCCESS/PASS与releaseComplete/remoteCi/scope/后续里程碑；负例非零退出也可构成验证器成功。不能假设一个忽略其余字段的消费者据此把全PRD或新里程碑授予完成。
- 证据：docs/testing/M9-ci-results.json:156；.agent:当前接续状态；docs/testing/M8-closure-results.json；docs/testing/M10.5.8-validation.md:3

## OCR-1763 · 不采纳

- 位置：docs/testing/M9-ci-results.json:7；OCR原严重度：low
- 计划：无
- 原意见：Key casing is inconsistent inside one document: the `run` block copies GitHub API snake_case (`head_sha`, `html_url`, `created_at`, `updated_at`) while every other field is camelCase (`jobId`, `jobUrl`, `verifiedAt`, `testNamesSha256`), so the same concept is spelled `html_url` in one place and `jobUrl` in another. Combined with the absence of a JSON Schema, a casing drift or typo in a key (e.g. `sourceMatchCurrent` vs `sourcesMatchCurrent`) silently yields a missing field for consumers instead of an error. Suggest normalising to one convention (keeping the raw GitHub keys nested under a dedicated `github` object if the original spelling must be preserved) and adding a schema so key typos are caught.
- 判断：本地核对 run=对象(7键)；run.head_sha="ed02da0e01125d94a3c07f8862afe6c63b8f62d2"。这些键属于不同嵌套作用域/外部API原样字段或以会话标签为键的映射；JSON不要求全树单一命名风格。未发现实际消费者按统一大小写/连续step序号解析导致失败，纯样式统一不足以立为缺陷。
- 证据：docs/testing/M9-ci-results.json:7；docs/testing/tools/verify-m9-evidence.py:38；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentDockerProbe.java；docs/testing/M9-ci-results.json:5

## OCR-1764 · 不采纳

- 位置：docs/testing/M9-ci-results.json:139；OCR原严重度：medium
- 计划：无
- 原意见：The audit chain is anchored on this map, but it only covers the 15 derived `M9-hosted-*` files (approx. lines 139-156). The inputs the verifier actually trusts for run identity and artifact digests — `M9-ci-run.json` (id / head_sha / html_url), `M9-ci-jobs.json` and `M9-ci-artifacts.json` — are not hashed anywhere, and the receipt itself has no self-digest. Trigger: anyone can rewrite `run.id`, `run.head_sha` or `platforms[].artifactUrl`/`artifactId` and the receipt stays internally "valid"; a downstream auditor has no way to detect that the recorded run/source is no longer the one the evidence files were derived from. Suggest adding those three metadata files and a digest over the receipt body to `evidenceSha256` so the chain has a verifiable root.
- 判断：本地核对 run=对象(7键)；platforms=数组(3项)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-ci-results.json:139；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M9-ci-results.json:5；docs/testing/M9-ci-results.json:14

## OCR-1765 · 部分采纳

- 位置：docs/testing/M9-ci-results.json:49；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：`operatingSystemDetails` is a positional, unlabeled 3-element array that was produced by taking the three log lines following the "Operating System" marker. Consumers cannot tell which element is the OS name, version or build number, and the extraction has no validation: if the job-log format changes, the verifier silently stores three unrelated log lines here while `checks` still reports `true`. Suggest replacing it with an object (e.g. `{"name": "Microsoft Windows Server 2025", "version": "10.0.26100", "build": "Datacenter"}`) and validating that it is consistent with `platform`/`architecture` before the record is marked PASS.
- 判断：本地核对 platforms[0].operatingSystemDetails=数组(3项)；platforms[0].checks=对象(7键)。operatingSystemDetails确取Operating System之后三行，无字段标签/格式一致性校验。采纳解析name/version/build对象，保留原日志摘要和未知值，不把三行抓取错误升级为影响全测试/来源hash门禁。
- 证据：docs/testing/M9-ci-results.json:49；docs/testing/tools/verify-m9-evidence.py:143；docs/testing/M9-ci-results.json:38

## OCR-1766 · 不采纳

- 位置：docs/testing/M9-ci-results.json:137；OCR原严重度：low
- 计划：无
- 原意见：`sourceFileCount` is ambiguous and duplicated: the counted set is the root `pom.xml` plus every module `pom.xml` plus all `**/src/**/*.java`, so 260 is not a number of Java sources, yet the field name and the `sourcesMatchCurrent` check suggest a pure source count. Consumers comparing this value to a `find . -name '*.java' | wc -l` result will get a mismatch and wrongly conclude the evidence is stale. Suggest splitting the counts (`javaFileCount` / `pomFileCount`) and, more importantly, recording a digest of that source set (a `sourceSetSha256` or a sorted `{path: sha256}` map) instead of only a count, so `sourcesMatchCurrent` remains re-verifiable after the working tree moves on.
- 判断：本地核对 sourceFileCount=260；platforms[0].checks.sourcesMatchCurrent=true。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-ci-results.json:137；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M9-ci-results.json:37；docs/testing/M9-ci-results.json:42

## OCR-1767 · 不采纳

- 位置：docs/testing/M9-hosted-linux-smoke.json:4；OCR原严重度：medium
- 计划：无
- 原意见：`status` is hand-derived and unversioned: there is no `schemaVersion` and no record of the required check set, so a silently dropped/renamed check (or an empty `checks` object) would still read as a trustworthy artifact to a consumer, and PASS here can be mistaken for coverage it does not have. The mandatory risk areas called out for this project (environment-variable whitelisting, secret redaction in reports, plugin-scope rollback, filesystem boundary) are not represented among the five recorded checks. Recommend adding a `schemaVersion` plus an explicit list of required checks (and their pass state) so aggregation fails closed and the artifact states exactly what safety surface was exercised.
- 判断：本地核对 status="PASS"；checks=对象(5键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-hosted-linux-smoke.json:4；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M9-hosted-linux-smoke.json:5

## OCR-1768 · 不采纳

- 位置：docs/testing/M9-hosted-linux-smoke.json:12；OCR原严重度：medium
- 计划：无
- 原意见：The request counters cannot substantiate the cost/safety claim they are used for. `modelRequests` is a count of loopback-fixture requests only, and `paidRequests` is asserted as a constant 0 by the producing harness rather than measured; nothing in this artifact records that the model endpoint was loopback (`127.0.0.1`) or that no provider-billed call occurred. A reviewer therefore cannot verify "7 model requests, 0 paid" from the file. Recommend recording the endpoint/base-URL scheme (or an explicit `modelEndpointKind: loopback`) and how the paid counter was observed.
- 判断：本地核对 modelRequests=7；paidRequests=0。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M9-hosted-linux-smoke.json:12；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M9-hosted-linux-smoke.json:13

## OCR-1769 · 不采纳

- 位置：docs/testing/M9-hosted-linux-smoke.json:2；OCR原严重度：medium
- 计划：无
- 原意见：Evidence provenance is insufficient for a release-gating artifact. `zipSha256` is the only binding to the tested build, yet the file records no platform/host/runner identity, no source revision, and no self-hash/signature, so the "hosted Linux" claim and the tested commit rest entirely on the file name. Note also that this digest (5b2377d6...) differs from the locally recorded candidate run (82eac1fe... in M9-candidate-smoke.json) and from the other hosted artifacts, with nothing recording whether that difference is only per-platform packaging. Any edit of `status`/`checks` here is undetectable. Recommend recording `platform`, `sourceCommit`/`artifactName`, `runnerId` and a checksum/signature over the JSON body.
- 判断：本地核对 zipSha256="5b2377d6e611697b39cbe25e7ec042b53437cd7b349eab2d0fbc3bc8d6cad70d"；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-hosted-linux-smoke.json:2；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M9-hosted-linux-smoke.json:3；docs/testing/M9-hosted-linux-smoke.json:4

## OCR-1770 · 不采纳

- 位置：docs/testing/M9-hosted-macos-smoke.json:13；OCR原严重度：medium
- 计划：无
- 原意见：`paidRequests` 不是由任何计数或拦截机制派生出来的：生成脚本 scripts/smoke-candidate.py 第166行无条件写入 `paidRequests=0`，因此本字段（配合下游 verify-m9-evidence.py:87 的 `smoke['paidRequests'] == 0` 断言）只是重复同一个常量，无法作为“未发生付费调用”的证据。触发条件：CLI 的 base URL/endpoint 被环境变量或配置改指向付费端点时，冒烟脚本只会把该请求记入 `modelRequests`（本地 fixture 收不到第 7 个请求则该检查会失败，但若有其它请求被计满 7 次），本文件依旧记录 `paidRequests: 0` 且 PASS。建议在该证据中记录实际使用的 fixture 端点（host/port）与已观测请求的 host 白名单，或由真实出网审计结果派生该字段，使其成为可证伪的证据而非自报常量。
- 判断：本地核对 paidRequests=0；modelRequests=7。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M9-hosted-macos-smoke.json:13；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M9-hosted-macos-smoke.json:12

## OCR-1771 · 不采纳

- 位置：docs/testing/M9-hosted-macos-smoke.json:5；OCR原严重度：medium
- 计划：无
- 原意见：本文件的 `checks` 是一组以键名区分的布尔项，但下游校验只断言“条目数为 5 且全部为 true”（verify-m9-evidence.py:88），没有固定键名集合的比对。触发条件：生成侧某个检查项被改名或拼错（例如 `approvalEofDenies` 写成 `approvalEOFDenies`），或被替换为另一个恒为 true 的键，证据仍会通过校验，而原本的门禁覆盖将在无任何失败信号的情况下静默消失。建议校验侧对 `checks` 的键名集合做精确断言（或在此证据中显式记录期望的检查项列表），避免键名漂移被吞掉。
- 判断：本地核对 checks=对象(5键)；checks.approvalEofDenies=true。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M9-hosted-macos-smoke.json:5；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M9-hosted-macos-smoke.json:9

## OCR-1772 · 不采纳

- 位置：docs/testing/M9-hosted-macos-security.json:4；OCR原严重度：medium
- 计划：无
- 原意见：该 PASS 证据只绑定了 BOM 摘要，未绑定平台与构建产物身份（无 platform / ciRunId / commit / jarSha256 字段），仅靠文件名区分 macOS、Linux、Windows。实测三个平台的证据除此文件外内容几乎一致：本文件第 4 行 bomSha256 与 docs/testing/M9-hosted-linux-security.json 完全相同（均为 56db8215…，已在 docs/testing/M9-ci-results.json 第 75、115 行对应 ubuntu 与 macos-latest 两个作业），packages 清单也逐条相同。因此把 Linux 的 security.json 改名为 macos 后无法从文件内容上被发现，`status: PASS` 也能被无限期重放（无 scan 时间窗口/过期语义校验，capturedAt 仅作记录而不参与判定）。建议在本文件结构中补充 platform、ciRunId、jarSha256/candidateZipSha256 以及 expiresAt（或在消费方对 capturedAt 施加最大时效），使零漏洞结论与“哪次构建、哪个平台、何时扫描”唯一绑定。
- 判断：本地核对 capturedAt="2026-10-16T05:58:55.090059+00:00"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M9-hosted-macos-security.json:4；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1773 · 不采纳

- 位置：docs/testing/M9-hosted-macos-results.json:1131；OCR原严重度：medium
- 计划：无
- 原意见：产物完整性绑定不完整、哈希语义未声明：(1) 本文件的 M9DistributionDoctorTest 验证的是“打包后运行时无需 Maven”的整份发行包，而这里只对一个 jar 取 `jarSha256`；发行目录中 `lib/` 下的依赖 jar、启动脚本等均未被绑定，替换其中任一文件后本 JSON 仍能通过校验，发行包整体完整性无法证明。建议记录打包目录内各文件的相对路径 + SHA-256 清单，或对整体归档取单一哈希。(2) `logSha256`、`suites[].sha256`、`jarSha256` 都没有说明哈希算法与计算输入（例如 suite 哈希是对 surefire XML、还是对排序后的用例名拼接求值），审计方无法独立重算，只能“相信”这些值；同时 `verificationLog` 仅给出相对文件名，未记录实际执行的命令/环境变量（JAVA_HOME、是否启用 Docker 探针等），无法复现 `buildResult: SUCCESS` 与全 0 skipped 的结论。触发条件：任何人重新生成或篡改 verify.log/产物后，缺少 derivation 规则就无法判定哈希是否仍与本次提交 `ed02da0e…` 对应。建议在文件内加入 schema/derivation 版本字段并写明每个哈希字段的计算规则与输入集合。
- 判断：本地核对 jarSha256="c52a7ec3f9c3fa8dd6c6d7b8e7e83637bbd05622a0697e5e1abe23d04068c16f"；logSha256="85ec6c74aa45b8c92050bcf8aa87de0f905537c1248137c88018ef3f1c2c0265"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M9-hosted-macos-results.json:1131；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M9-hosted-macos-results.json:16

## OCR-1774 · 不采纳

- 位置：docs/testing/M9-hosted-macos-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：用例名重复计数：源码中该方法标注为 `@RepeatedTest(10)`（harness-testkit/src/test/java/io/jharness/testkit/M7SubagentTest.java:712），JUnit 只会产出 `explicitParentCancellationReachesChildAndDoesNotClaimSuccess()[1]`…`[10]` 共 10 条调用名，不会额外产生一条无下标条目。这里无下标条目与 `[1]`…`[10]` 并存，使该 suite 的 `cases` 达到 23 条、与 `tests: 23` 恰好相等；因此要么漏记了某次重复调用，要么这条无下标条目是幽灵条目。由于本文件的 `totals.tests`（305）正是由各 suite 的 `cases` 长度累加得到（49 个 suite 求和确为 305），这条重复项会让“已验证 305 个用例”多算 1（真实不同用例数为 22，全量应为 304）。触发条件：任何按 `cases` 去重或做唯一性校验的审计脚本都会发现重复名；若按 `tests` 字段核对 surefire 计数则察觉不到。建议删除无下标条目（或补齐缺失的下标），并在生成脚本中按“方法名+下标”去重后与 surefire 的 tests 计数交叉校验。
- 判断：本地核对 suites[0].cases=数组(3项)；totals=对象(4键)。@RepeatedTest(10)重复执行取消竞态是有意提高复现率，JUnit报告保留每次displayName/索引；总执行数计10不是虚增不同测试方法数，无需因没有参数而去重为1。
- 证据：docs/testing/M9-hosted-macos-results.json:1；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentTest.java:712；docs/testing/M9-hosted-windows-results.json:suites；docs/testing/M9-hosted-macos-results.json:31；docs/testing/M9-hosted-macos-results.json:17

## OCR-1775 · 不采纳

- 位置：docs/testing/M9-hosted-linux-results.json:1131；OCR原严重度：medium
- 计划：无
- 原意见：单一 jarSha256 覆盖的是多模块 reactor 构建：suites 涉及 harness-api、harness-events、harness-session、harness-plugin、harness-tools、harness-workspace、harness-process、harness-policy、harness-llm、harness-cli、harness-testkit（另有 harness-agent、harness-ecosystem、harness-web），每个模块各自产出 jar，用一个哈希无法证明各模块产物未被替换。触发条件：发行包或某个单模块 jar 被替换后，本快照仍会呈现整体“已完整性校验”的假象（校验的是另一个产物）。建议改为 per-module 映射（module -> jar sha256），或对最终分发的 zip/tar 整体再取哈希。
- 判断：本地核对 milestone="M9"；capturedAt="2026-10-16T05:59:06.826313+00:00"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-hosted-linux-results.json:1131；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1776 · 不采纳

- 位置：docs/testing/M9-hosted-linux-results.json:869；OCR原严重度：medium
- 计划：无
- 原意见：sourceSha256 遗漏了决定构建结果的可执行输入：仓库中实际存在 .github/workflows/ci.yml、.github/workflows/native-credentials.yml、mvnw/mvnw.cmd 与 .mvn/wrapper/maven-wrapper.properties，但这些文件（以及 JDK 选择、测试命令）均未列入哈希集合。触发条件：修改 workflow / wrapper 版本后重跑仍可沿用同一份 sourceSha256 与 commit，就能得到另一套 buildResult/用例结果，即“提交 + 文件哈希”并不能唯一确定如何得到 SUCCESS 与 305 用例，快照的可复现性主张不成立。建议至少纳入 CI workflow、Maven wrapper 配置，或改为对整个提交树取哈希（如 git rev-parse HEAD^{tree}）。
- 判断：本地核对 milestone="M9"；capturedAt="2026-10-16T05:59:06.826313+00:00"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-hosted-linux-results.json:869；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1777 · 不采纳

- 位置：docs/testing/M9-hosted-linux-results.json:14；OCR原严重度：medium
- 计划：无
- 原意见：verificationLog 只有一个相对文件名 "verify.log"，未说明它相对于仓库根还是某构建目录；logSha256 也未声明哈希对象与路径，且 verify.log 本身不在 sourceSha256 中。触发条件：消费方在非仓库根目录解析该字段、或想确认 logSha256 是否确实对应 verify.log 时无法判定，导致整份证据的完整性校验链断裂。建议把两者合并为结构化字段，例如 "verificationLog": {"path": "docs/testing/verify.log", "sha256": "…"}。
- 判断：本地核对 milestone="M9"；capturedAt="2026-10-16T05:59:06.826313+00:00"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-hosted-linux-results.json:14；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1778 · 不采纳

- 位置：docs/testing/M9-hosted-linux-results.json:3；OCR原严重度：medium
- 计划：无
- 原意见：证据时间线自相矛盾：capturedAt = 2026-10-16T05:59:06Z，早于本文件 ciRunUrl 所引用的同一 commit（ed02da0e…）运行的验证时间（docs/testing/M9-ci-results.json 中 verifiedAt = 2026-10-17T06:12:47Z）。触发条件：任何按 capturedAt 判断证据新鲜度/排序的下游（发布门禁、审计脚本）会把 10-16 的日志错误地绑定到 10-17 才完成的 CI 运行上，无法证明 SUCCESS 与 305 用例来自被引用的那次运行。建议 capturedAt 取本快照（含 verify.log、jar 哈希）真正生成完成的时刻，或新增独立字段区分“日志产出时间”与“快照采集时间”。
- 判断：本地核对 milestone="M9"；capturedAt="2026-10-16T05:59:06.826313+00:00"。capturedAt为runner执行/取证时刻，verifiedAt为稍后离线核验时刻，前者早于后者是正常顺序，不构成同commit时间线矛盾。
- 证据：docs/testing/M9-hosted-linux-results.json:3；docs/testing/M9-hosted-windows-results.json:capturedAt；docs/testing/M9-ci-results.json:verifiedAt

## OCR-1779 · 不采纳

- 位置：docs/testing/M9-hosted-linux-results.json:36；OCR原严重度：low
- 计划：无
- 原意见：suites[].sha256 未定义哈希的输入与序列化方式（排序后的用例名？Surefire XML 报告？class 字节码？是否含换行符），也不写算法之外的任何说明。触发条件：审计方无法重算该哈希来验证用例清单未被删改（例如把某个用例从 cases 中移除后重新计算哈希），因此每个 suite 的哈希实际起不到防篡改作用。建议在文件中新增哈希规范说明（如 "suiteHashOf": "sorted case names joined by \n, UTF-8"），或改为哈希对应的原始测试报告文件路径+哈希。
- 判断：本地核对 milestone="M9"；capturedAt="2026-10-16T05:59:06.826313+00:00"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-hosted-linux-results.json:36；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1780 · 不采纳

- 位置：docs/testing/M9-local-candidate-smoke.json:4；OCR原严重度：low
- 计划：无
- 原意见：顶层 PASS 与 checks 的聚合关系在本记录中缺少机器可核验的绑定：`scripts/smoke-candidate.py` 是在 try 体内逐个 `report["checks"][...] = True` 后由 `report.update(status="PASS", ...)` 写出的，而其真实输出（`target/candidate-smoke.json`、会话事件、日志）位于被忽略的 target/ 目录；本文件是唯一入库的本地证据，且没有 checks 覆盖“制品来源一致/摘要匹配/请求计数”这类安全项，也没有指向原始日志的路径或摘要。触发条件：任一安全项（如 `cancelUnknownRefusesReplayAndExplicitRecovery`）实际失败但记录仍被手工写成 PASS 时，仓库内没有任何脚本能发现（校验器对本文件的 5 个布尔值不做独立复核）。建议补充可校验的字段（如原始日志/会话文件的 SHA-256、请求日志摘要）或在校验器中独立重算 `status = all(checks.values())`。
- 判断：本地核对 checks.cancelUnknownRefusesReplayAndExplicitRecovery=true。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M9-local-candidate-smoke.json:4；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29；docs/testing/M9-local-candidate-smoke.json:10

## OCR-1781 · 不采纳

- 位置：docs/testing/M9-local-candidate-smoke.json:16；OCR原严重度：medium
- 计划：无
- 原意见：溯源字段未被任何校验脚本消费：全仓库检索 `candidateSourceCommit` / `candidateOrigin` 仅命中本文件（以及 .agent 说明文本），`docs/testing/tools/verify-m9-evidence.py` 只校验 `zipSha256`、`status`、`modelRequests`、`paidRequests` 和 5 个 checks，不会把这些手工新增的提交号/制品 URL 与 ZIP 摘要、CI 制品的 `candidateZipSha256` 做交叉核对。触发条件：候选 ZIP 被重新打包或提交号被误抄时，本记录仍会被当作有效 PASS 证据，且 `python docs/testing/tools/verify-m9-evidence.py` 不会失败（静默漂移）。建议把这两个键纳入校验（例如断言 `candidateSourceCommit` 与 CI run 的 head_sha 一致、`candidateOrigin` 中的 artifactId 与 hosted evidence 的 artifactUrl 一致），或在无校验手段时标注为“人工填写、未经验证”以免被自动消费者当作可信溯源。
- 判断：本地核对 candidateSourceCommit="ed02da0e01125d94a3c07f8862afe6c63b8f62d2"；candidateOrigin="https://github.com/nixaveq141-ai/J-Harness/actions/runs/38858529628/artifacts/120...。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M9-local-candidate-smoke.json:16；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117；docs/testing/M9-local-candidate-smoke.json:17

## OCR-1782 · 不采纳

- 位置：docs/testing/M9-hosted-macos-manifest.json:2；OCR原严重度：low
- 计划：无
- 原意见：清单顶层只有产品版本 `version`，缺少清单格式（schema）版本字段。触发条件：当生成器后续新增字段或改变现有字段语义时，消费方无法区分新旧格式，也无法据此安全地执行“拒绝未知字段/严格校验”策略；而 `version` 表示的是产品版本（0.1.0-SNAPSHOT），不能承担 schema 版本语义，容易被误用。建议增加显式的 `schemaVersion` 并在校验器中强制校验。
- 判断：本地核对 version="0.1.0-SNAPSHOT"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-hosted-macos-manifest.json:2；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1783 · 不采纳

- 位置：docs/testing/M9-hosted-windows-smoke.json:13；OCR原严重度：medium
- 计划：无
- 原意见：`paidRequests: 0` 不是实测计量，而是写死的常量：生成脚本收尾处直接 `report.update(status="PASS", modelRequests=len(received), paidRequests=0)`（`scripts/smoke-candidate.py` 第 166 行），审计脚本又断言它必须为 0（`docs/testing/tools/verify-m9-evidence.py` 第 87 行），于是「零付费请求」这一保证在证据链上是自我循环的。触发条件：若 smoke 期间 fixture 的 baseUrl 被配置/代理重定向到真实厂商端点（脚本第 76-78 行把 baseUrl 写进 workspace 的 config.json，任何能改写该文件的步骤都会改变实际目标），该字段仍会记录 0，报告与事实不符。建议让该值由请求侧可观测数据派生（例如统计目标主机非 127.0.0.1 的请求数并记录实际 baseUrl 主机），或在 artifact 中显式标注此字段为声明常量而非实测计数。
- 判断：本地核对 capturedAt="2026-10-16T06:02:39.048290+00:00"；status="PASS"。本条未证明计数实现错误。模型请求来自夹具显式绑定的loopback替身，付费数0与非零请求数不矛盾；opened是任务而非页面，浏览器业务轨迹不是provider轨迹。源码已有逐场景before/after与实际文件断言，不能仅因子JSON只保留汇总而认定重放/付费或计数失效。
- 证据：docs/testing/M9-hosted-windows-smoke.json:13；frontend/tests/history.mjs:19；frontend/tests/history.mjs:30；frontend/tests/turns.mjs:117

## OCR-1784 · 不采纳

- 位置：docs/testing/M9-hosted-windows-smoke.json:5；OCR原严重度：medium
- 计划：无
- 原意见：checks 只有固定 5 项，且审计器硬编码 `all(smoke['checks'].values()) and len(smoke['checks']) == 5`（`docs/testing/tools/verify-m9-evidence.py` 第 88 行），既无法承载新增检查（一加就整体失败），又使若干强制不变量在本快照中没有任何具名断言：敏感信息脱敏（脚本第 93、163-165 行的 `m9-local-fixture-only` 泄漏检查只以 assert 形式存在，失败与否不会体现在 checks 中）、环境变量白名单（脚本第 59 行 `{**os.environ, ...}` 全量继承宿主环境，报告中无任何字段记录子进程环境来源）、插件作用域回滚、文件系统边界（仅靠含空格的 install/workspace 路径隐式覆盖）。触发条件：上述任一不变量被破坏时，本文件仍可呈现全部 true + `status: PASS`，证据无法暴露回归。建议把这些不变量提升为具名 check（附带失败原因/被测环境摘要），并让聚合逻辑依赖检查项集合而非固定长度。
- 判断：本地核对 capturedAt="2026-10-16T06:02:39.048290+00:00"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M9-hosted-windows-smoke.json:5；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1785 · 不采纳

- 位置：docs/testing/M9-hosted-windows-smoke.json:2；OCR原严重度：low
- 计划：无
- 原意见：该值是「运行开始时间」而非结果捕获时间：生成脚本在初始化 report 时（任何检查执行之前，`scripts/smoke-candidate.py` 第 25 行）就写入了 `capturedAt`，而 checks/status 要数分钟后才补齐，文件中也没有结束时间、耗时、commit/runId 归属信息。触发条件：拿此时间戳与 CI job 时间线比对以判断证据归属、或判断是否复用了旧快照时会得出错误结论（例如安装+取消+恢复整段耗时，会让人误认为证据在开始时刻即已生成）；本文件只能通过 zipSha256 与外部 results 关联，无法自证来自哪次 windows-latest 构建。建议区分 `startedAt`/`finishedAt`（或 `durationSeconds`），并补充 `commit`/`runId`/`runnerLabel` 等归属字段。
- 判断：本地核对 capturedAt="2026-10-16T06:02:39.048290+00:00"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M9-hosted-windows-smoke.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1786 · 不采纳

- 位置：docs/testing/M9-hosted-windows-security.json:2；OCR原严重度：low
- 计划：无
- 原意见：溯源信息不足且与本地扫描产物不可区分：本文件的 capturedAt 与 docs/testing/M9-security-results.json 完全一致（精确到微秒 2026-10-16T06:02:37.559251+00:00），两份文件的字段内容也一致，但文件内没有任何字段标明生成该证据的运行环境（windows-latest / job id / run id）、扫描器版本或 OSV 响应元数据。结果是：(1) 无法从文件本身证明这份 "hosted Windows" 证据确实由 windows-latest 作业产生而非从本地结果复制；(2) 由于 records 为空、仅保留 bomSha256，PASS 结论无法仅凭该文件复核，只能依赖同名 SBOM 文件配合 scripts/scan-osv.py 的相同逻辑重放。建议由扫描脚本额外输出 runner 平台、run/job 标识与扫描器/OSV 交互元数据（或保留每个查询的响应摘要），并据此重新生成该证据，以支持第三方独立复核。
- 判断：本地核对 capturedAt="2026-10-16T06:02:37.559251+00:00"；status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M9-hosted-windows-security.json:2；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1787 · 不采纳

- 位置：docs/testing/M9-hosted-windows-results.json:1131；OCR原严重度：medium
- 计划：无
- 原意见：jarSha256 是单个未命名构件的哈希，未记录文件名、模块、版本、大小或相对路径，无法判定它对应哪个产物（例如 harness-cli 分发包还是某个模块 jar）；若构建产出了多个 jar 或产物改名，该哈希既不能定位也不能复核。建议改为 `{artifact: {path, module, version, size, sha256}}` 结构的列表。
- 判断：本地核对 milestone="M9"；capturedAt="2026-10-16T06:02:36.883959+00:00"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-hosted-windows-results.json:1131；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1788 · 不采纳

- 位置：docs/testing/M9-hosted-windows-results.json:869；OCR原严重度：high
- 计划：无
- 原意见：sourceSha256 未覆盖完整源码树，与“校验 jar 来源”的用途不符，遗漏项包括： - harness-session/src/main: `SessionLifecycle.java`、`TokenSessionSearch.java`；test: `TokenSessionSearchTest.java` - harness-cli/src/main: `NativeCredentials.java`、`StdioSdkServer.java`、`SystemCredentials.java`、`WorkbenchSettings.java`、`WorkbenchWorkspaces.java`；test: `CredentialStoreProbe.java`、`StdioSdkServerTest.java`、`WorkbenchCredentialCleanup.java`、`WorkbenchSettingsTest.java`、`WorkbenchWorkspacesTest.java` - harness-ecosystem/src/main: `ContentPages.java`、`ModelDisplayBuffer.java`、`SkillCatalog.java`、`UserQuestionsPlugin.java`、`WorkspaceContentInspection.java` - harness-web/src/main: `DurableTaskCatalog.java`、`TaskDiagnosis.java`、`TurnQueue.java`、`UiResources.java`、`WorkbenchContentSearch.java`、`WorkspaceLease.java` 遗漏的恰是凭据处理（NativeCredentials/SystemCredentials）与 Web 鉴权/租约（WorkspaceLease）等安全相关类：即使这些文件在快照后被改动，manifest 仍会“校验通过”。建议按模块全量生成 sourceSha256，或在文件中明确声明只覆盖某个子集及选入规则。
- 判断：本地核对 milestone="M9"；capturedAt="2026-10-16T06:02:36.883959+00:00"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-hosted-windows-results.json:869；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1789 · 不采纳

- 位置：docs/testing/M9-hosted-windows-results.json:14；OCR原严重度：medium
- 计划：无
- 原意见：verificationLog 只给出裸文件名，既无相对路径/CI artifact 存储位置，也无下载地址；而 ciRunUrl 仅是 Actions 运行页链接（运行日志会随保留期过期、部分产物需鉴权下载），因此 logSha256 无法对应到任何可获取的具体文件，审计链在最关键的一环断开——无法证明该哈希确实来自 commit ed02da0 的 M9 运行日志。建议记录可解析的路径或 artifact URL，并补充生成该日志的完整 Maven 命令与采集时间窗口。
- 判断：本地核对 milestone="M9"；capturedAt="2026-10-16T06:02:36.883959+00:00"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M9-hosted-windows-results.json:14；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1790 · 不采纳

- 位置：docs/testing/M9-hosted-windows-results.json:17；OCR原严重度：high
- 计划：无
- 原意见：totals 与 suites 自洽（305 个用例、0 失败/错误/跳过），但 suites 并不是本次提交的完整测试集：仓库中还存在未出现在 suites 里的测试类 `harness-session/src/test/java/io/jharness/session/TokenSessionSearchTest.java`、`harness-cli/src/test/java/io/jharness/cli/StdioSdkServerTest.java`、`WorkbenchSettingsTest.java`、`WorkbenchWorkspacesTest.java`。文件既没有列出这些套件，也没有记录任何 `-Dtest`/exclude/docker 过滤条件，因此“305 全绿”无法代表该 commit 的完整验证结果，成功结论存在被高估的风险。建议补齐这些套件条目，或显式记录被排除的套件名与排除原因/命令行参数。
- 判断：本地核对 milestone="M9"；capturedAt="2026-10-16T06:02:36.883959+00:00"。将当前源码字段/新功能文件集套用到带日期的旧里程碑快照，不能证明旧记录陈旧造假或漏测。M10.5.9新增跨引擎runtime/transportDiagnostics及M10.5.6～.8新增UI/检索文件，旧阶段不应补填这些后来字段或重标通过。
- 证据：docs/testing/M9-hosted-windows-results.json:17；.agent:当前接续状态；docs/testing/M10.5.9-validation.md:26

## OCR-1791 · 不采纳

- 位置：docs/testing/M9-hosted-windows-results.json:23；OCR原严重度：medium
- 计划：无
- 原意见：sourceSha256 覆盖了 harness-agent、harness-ecosystem、harness-web，但 suites 中没有任何这三个模块的用例：仓库中 harness-ecosystem/src/test 与 harness-web/src/test 目录根本不存在，harness-agent 也无测试源码，因此 WorkspaceLease、HarnessWebServer、McpTransport 等仅靠 harness-testkit 的 M7*/M8* 集成用例间接覆盖。当前文件未声明这种模块→套件映射，容易被读成“每个模块都被独立验证”。建议显式记录模块到套件的映射关系或说明这些模块仅由 testkit 集成测试间接覆盖。
- 判断：本地核对 milestone="M9"；capturedAt="2026-10-16T06:02:36.883959+00:00"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-hosted-windows-results.json:23；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67

## OCR-1792 · 不采纳

- 位置：docs/testing/M9-hosted-windows-results.json:745；OCR原严重度：low
- 计划：无
- 原意见：该套件 23 个“用例”中有 10 个是同一个参数化方法（`explicitParentCancellationReachesChildAndDoesNotClaimSuccess`）的重复调用，而显示名只有空参数列表 `()` 加索引，未记录每次调用的参数值，无法从证据文件判断 [1]..[10] 各自覆盖了哪种父子取消/挂起组合；用 23 与其它套件做覆盖率比较也会失真（独立测试方法仅约 14 个）。建议在记录中附上参数摘要（参数值或索引到场景的映射）。
- 判断：本地核对 milestone="M9"；capturedAt="2026-10-16T06:02:36.883959+00:00"。@RepeatedTest(10)重复执行取消竞态是有意提高复现率，JUnit报告保留每次displayName/索引；总执行数计10不是虚增不同测试方法数，无需因没有参数而去重为1。
- 证据：docs/testing/M9-hosted-windows-results.json:745；harness-testkit/src/test/java/io/jharness/testkit/M7SubagentTest.java:712；docs/testing/M9-hosted-windows-results.json:suites

## OCR-1793 · 不采纳

- 位置：docs/testing/M9-hosted-windows-manifest.json:5；OCR原严重度：medium
- 计划：无
- 原意见：第 5–6 行：`workingTreeDirty` 由 `git status --porcelain` 判定，而 `sourceSha256` 记录的是工作区字节（`scripts/build-candidate.py` 中 `read_bytes()`）。在 Windows 托管机上，若检出时 `core.autocrlf=true`，文本文件（含 `.yml`/`.java`/`pom.xml`）在工作区是 CRLF，但 git 因换行规范化仍报告工作区干净，于是 `workingTreeDirty=false` 与“哈希对应的字节从未存在于 commit ed02da0 中”同时成立。  触发条件：在 Windows runner 上以 autocrlf 检出后重跑清单生成；此后用 `git cat-file blob <commit>:<path>` 或与 Linux/macOS 清单逐条比对校验时会出现整批 SHA-256 不一致（误报），而反过来若有人只改换行符，也无法仅凭该清单证明字节与提交绑定。  建议：改为对 `git cat-file blob <gitCommit>:<path>` 的字节取哈希（或先按仓库 `.gitattributes` 规范化换行后再哈希），并在清单中显式记录 eol 策略，使 `gitCommit` 与两份哈希表具备可验证的绑定关系。
- 判断：本地核对 workingTreeDirty=false；sourceSha256=对象(277键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-hosted-windows-manifest.json:5；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M9-hosted-windows-manifest.json:6

## OCR-1794 · 不采纳

- 位置：docs/testing/M9-hosted-windows-manifest.json:285；OCR原严重度：low
- 计划：无
- 原意见：第 286 行 `files` 映射未包含清单自身（如 `docs/testing/M9-hosted-windows-manifest.json`），因此本文件中的 `candidateOnly`、`gitCommit`、`workingTreeDirty` 以及两张哈希表可被整体替换而不产生任何内部校验失败；当前只能依赖 `docs/testing/M9-ci-results.json` 中外部记录的 evidenceSha256 才能发现篡改。  触发条件：仅把清单作为发布证据分发、未同时提供外部摘要时，验证方无法证明拿到的清单就是生成时的那份。  建议：在生成后把清单自身摘要写入外部证据（并保证校验流程强制比对），或在 `files` 中显式记录清单的最终位置与摘要说明，避免“自证完整”的假象。
- 判断：本地核对 files=对象(50键)；candidateOnly=true。清单无需在自身files中包含自身哈希；候选ZIP/Actions artifact外层摘要已覆盖manifest字节，强行自引用不可实现。没有独立签名并不证明记录可绕过外层完整性检查。
- 证据：docs/testing/M9-hosted-windows-manifest.json:285；docs/testing/tools/verify-m9-evidence.py:101；docs/testing/M9-ci-artifacts.json；docs/testing/M9-hosted-windows-manifest.json:3

## OCR-1795 · 不采纳

- 位置：docs/testing/M9-pre-hosted-packaging-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：顶层 `status: "PASS"` 未限定范围，而真实限制（仅验证 JAR 归档重生成与 ZIP 重复打包，不是跨平台封闭构建证明）只存在于自由文本 `scope` 中；下游自动化只会读取 `status`，可能把它当作完整可复现构建证明。建议改为机器可读的结构化字段，例如 `status: "PASS_LIMITED"`（或增加 `hermeticBuildProof: false` / `scopeId: "jar-regeneration+zip-repeatability"`），并在 schema 中强制该字段，避免被误判为完全通过。
- 判断：本地核对 scope="forced JAR archive regeneration and repeated ZIP packaging; not cross-platform he...；status="PASS"。记录已区分本轮选定范围SUCCESS/PASS与releaseComplete/remoteCi/scope/后续里程碑；负例非零退出也可构成验证器成功。不能假设一个忽略其余字段的消费者据此把全PRD或新里程碑授予完成。
- 证据：docs/testing/M9-pre-hosted-packaging-results.json:1；.agent:当前接续状态；docs/testing/M8-closure-results.json；docs/testing/M10.5.8-validation.md:3；docs/testing/M9-pre-hosted-packaging-results.json:8；docs/testing/M9-pre-hosted-packaging-results.json:2

## OCR-1796 · 不采纳

- 位置：docs/testing/M9-pre-hosted-packaging-results.json:0；OCR原严重度：medium
- 计划：无
- 原意见：`repackLogSha256` 只保存了哈希，没有日志文件路径、大小、内容摘要或本次运行标识，评审者无法确认哈希对应的是哪份日志、该日志是否属于本次运行，也无法据此复核 ZIP 打包过程。建议增加 `repackLogPath`、`repackLogSize`、`runId`（或时间戳），必要时同时内联关键日志摘要。
- 判断：本地核对 repackLogSha256="00f00bc82563bb34f49a97e7d13031ccf968c23245cb77f6e6ca9637a8633675"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-pre-hosted-packaging-results.json:1；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M9-pre-hosted-packaging-results.json:7

## OCR-1797 · 不采纳

- 位置：docs/testing/M9-pre-hosted-packaging-results.json:0；OCR原严重度：high
- 计划：无
- 原意见：前后哈希完全相同无法区分「真实强制重建」与「no-op/复用已有 JAR」。当前文件既没有重建前的清理证据（如 target/ 是否删除）、构建命令、构建环境（JDK/Maven 版本）、也没有时间戳，因此 `jarBeforeSha256 == jarAfterSha256` 这一结果本身不构成可复现证据，任何人都可以用一个陈旧产物冒充重建结果。建议补充 `buildStartedAt/buildFinishedAt`、`buildCommand`、`jdkVersion`、`mavenVersion`、`targetCleaned`、`jarPath`，并记录 JAR 的 mtime 或 `META-INF/MANIFEST.MF` 中的构建属性，以便区分重建与复用。
- 判断：本地核对 scope="forced JAR archive regeneration and repeated ZIP packaging; not cross-platform he...；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-pre-hosted-packaging-results.json:1；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1798 · 不采纳

- 位置：docs/testing/M9-pre-hosted-packaging-results.json:0；OCR原严重度：high
- 计划：无
- 原意见：重复打包得到的 ZIP 哈希相同，也可能只是把已有 ZIP 复制了一遍而非真正重新打包；文件缺少打包命令、打包次数、被压缩源目录、ZIP 内部条目 mtime 归一化处理等信息。建议记录 `zipCommand`、`packageCount`、`sourceDir` 及时间戳归一化策略，并至少在两次打包之间记录中间文件的 mtime/inode 变化，才能证明结果来自真正的重复打包。
- 判断：本地核对 scope="forced JAR archive regeneration and repeated ZIP packaging; not cross-platform he...；status="PASS"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-pre-hosted-packaging-results.json:1；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20

## OCR-1799 · 不采纳

- 位置：docs/testing/M9-pre-hosted-packaging-results.json:0；OCR原严重度：high
- 计划：无
- 原意见：`candidateCommit`/`workingTreeDirty` 与哈希产物之间没有任何绑定：没有 JAR 内嵌 commit（如 `build-info.properties` / `Implementation-Version`）、没有构建输出路径、也没有说明 `workingTreeDirty=false` 是如何判定的（是否包含 untracked 文件、用的哪条 git 命令）。因此一个陈旧或替换过的 JAR 也能与本 commit 一起记录并通过校验。建议记录判定命令与输出、产物路径，并在 JAR 中内嵌 commit 后于校验步骤中比对。
- 判断：本地核对 candidateCommit="ebcf7dd284f89b28709096b282a55104a9e8a5ea"；workingTreeDirty=false。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-pre-hosted-packaging-results.json:1；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M9-pre-hosted-packaging-results.json:9；docs/testing/M9-pre-hosted-packaging-results.json:10

## OCR-1800 · 不采纳

- 位置：docs/testing/M9-release-tools-results.json:5；OCR原严重度：medium
- 计划：无
- 原意见：该证据只记录了摘要值，没有记录被摘要对象的路径：仓库内不存在可解析的日志文件引用（对比 M8.3-results.json 有 `"log": ".build-cache/M8.3-verify.log"`），`verify-m9-evidence.py` 也没有读取本文件，因此任何人都无法离线复算 `logSha256`，也无法发现日志被替换/伪造。触发条件：审计者按现有证据链复核 M9 release-tools 结论时，无法确认 `064446…4192` 对应哪份日志，也无法检出该 JSON 与日志不一致。建议补 `log` 相对路径，并在校验脚本中断言 `sha256(log) == logSha256`、把本文件自身摘要登记进 M9 收据。
- 判断：本地核对 logSha256="064446ed3539c7ecc760dae8d1ef8abb2be29845ec3b83f91d35be927f794192"。运行缓存/原始日志与构建报告按项目规则明确不入Git，历史收据保留摘要，M9真实CI另有artifact外层摘要。干净clone没有原件是声明过的留存边界，非报告失效/测试未执行证据；不采纳提交.build-cache/.dsh-tmp或重跑所有旧阶段以补档的建议。
- 证据：docs/testing/M9-release-tools-results.json:5；.agent:验证流程；docs/testing/M10.5.9-validation.md:3；scripts/run-workbench-matrix.mjs:29

## OCR-1801 · 不采纳

- 位置：docs/testing/M9-release-tools-results.json:3；OCR原严重度：medium
- 计划：无
- 原意见：汇总字段不完整：缺少 `errors`、`skipped` 与 `capturedAt`/`sourceCommit`（同目录 M9 其余结果文件均有被测源码或脚本指纹），因此这份 PASS 无法绑定到具体的 `scripts/test-release-tools.py` 版本。触发条件：脚本后续被修改（该脚本 fingerprint 在 manifest 中为 `bb9113d2…`），此文件仍会被当作当前版本 5 个用例全绿的有效证据，形成过期的错误证据。建议补充 `errors`/`skipped`/`capturedAt` 以及被测脚本的 sha256。
- 判断：本地核对 status="PASS"。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M9-release-tools-results.json:3；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1802 · 不采纳

- 位置：docs/testing/M9-release-tools-results.json:6；OCR原严重度：low
- 计划：无
- 原意见：`cases` 记录的是改写后的描述句，而不是真实测试标识。实际用例为 `OsvGateTest.test_only_pending_query_is_paginated_and_finding_blocks`、`test_network_failure_is_incomplete`、`test_truncated_batch_is_incomplete`、`test_repeated_page_token_is_incomplete`、`test_no_findings_passes`，与这里 5 条描述都无法逐字匹配（同目录其它结果文件如 M8.3-results.json 均使用精确用例名）。触发条件：需要把本结果与 unittest 输出逐条对齐或确认某个用例是否被删除/重命名时，无法建立可验证映射。建议改用精确的 `模块.类#用例` 名称，并补 `suite` 字段。
- 判断：本地核对 cases=数组(5项)。本条要求为每份布尔/字符串收据追加完整断言载荷或状态模型；现有assert先执行、失败抛错，末尾才写PASS，主采集器核数量/JAR/通过位。没有展示具体空断言/吞错路径，不能仅凭收据简洁判假绿。固定ID的实际问题另由content同名case及verifier日志/法律空集门禁处理，不追溯扩改历史JSON。
- 证据：docs/testing/M9-release-tools-results.json:6；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90

## OCR-1803 · 不采纳

- 位置：docs/testing/M9-pre-hosted-local-results.json:1122；OCR原严重度：medium
- 计划：无
- 原意见：`jarSha256` 与每个 suite 的 `sha256` 都没有定义摘要的“前像”：JAR 的文件名/路径/是否含构建时间戳未知，suite 摘要究竟是对 `.class`、surefire XML 报告还是 case 列表文本做的也不清楚，同时缺少 digest 算法与规范化规则说明。触发条件：换机器或换时区重跑同一 commit 会得到不同 JAR 摘要（zip 条目时间戳），或校验方按自己的理解重算 suite 摘要，导致证据被误判为通过或失败。建议补充 `artifactPath`（含文件名与版本）、`digestAlgorithm`/`digestScope` 字段，并说明规范化方式（例如对排序后的文件列表+内容求 Merkle 根）。
- 判断：本地核对 jarSha256="24334b12a36597722f73a299b02f1a6acfadbd76e8b9e761a3e97805ce47b76c"；suites[0].sha256="89de0987507d3cdb4d810f5e3cacfec0e65cc20a262a4b0285119c33b3ec2254"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-pre-hosted-local-results.json:1122；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M9-pre-hosted-local-results.json:36

## OCR-1804 · 不采纳

- 位置：docs/testing/M9-pre-hosted-local-results.json:14；OCR原严重度：medium
- 计划：无
- 原意见：证据链在这里断裂：`verificationLog` 只是一个相对路径，且指向通常不会入库的 `.build-cache/` 目录；`logSha256` 校验的是该外部文件，但日志内容、字节数、所属 commit 都没有随记录保存，路径基准（仓库根 / `docs/testing/` / 模块 target 目录）也未定义。触发条件：日志被删除、被覆盖或事后替换后，校验方仅凭本文件（`sourceSha256`、`totals`）仍会判定通过，无法发现证据缺失或被篡改；换一个工作目录执行校验脚本也会因相对路径解析不同而找不到日志。建议明确路径基准、记录日志 size 与 commit，或把日志/校验清单一起归档，并让校验流程在日志缺失时直接失败而不是忽略。
- 判断：本地核对 verificationLog=".build-cache/M9-verify-final.log"；logSha256="4ed000eaf1fcb57ebe28408a112f0cac8ebb3c5fc356d8368909539850b026b6"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-pre-hosted-local-results.json:14；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M9-pre-hosted-local-results.json:16

## OCR-1805 · 不采纳

- 位置：docs/testing/M9-pre-hosted-local-results.json:2；OCR原严重度：low
- 计划：无
- 原意见：缺少 `schemaVersion`（以及生成该证据的脚本名称/版本或哈希）。下游校验工具只能对当前字段名硬编码解析，一旦记录格式演进（新增/改名/删除字段，例如 `runnerOs`、`logSha256`）旧工具会静默忽略或直接失败；另外生成脚本本身不在 `sourceSha256` 中，脚本里误算测试总数或摘要的缺陷无法被发现。建议增加 `schemaVersion` 与 `generator`（名称 + 版本/`sha256`）字段。
- 判断：本地核对 runnerOs="local"；logSha256="4ed000eaf1fcb57ebe28408a112f0cac8ebb3c5fc356d8368909539850b026b6"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-pre-hosted-local-results.json:2；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M9-pre-hosted-local-results.json:7；docs/testing/M9-pre-hosted-local-results.json:16

## OCR-1806 · 不采纳

- 位置：docs/testing/M9-pre-hosted-local-results.json:7；OCR原严重度：medium
- 计划：无
- 原意见：该记录是完全自证的：`buildResult`、`workingTreeDirty=false`、`runnerOs=local` 都由被测机器自己写入，文件里既没有实际执行命令（maven/mvnw 路径、`-P` profile、`-Dtest` 过滤范围、JDK 可执行路径），也没有任何签名或可校验的第三方凭证（`ciRunUrl` 为 null）。触发条件：任何人手工编写或改写此 JSON，都能生成一份“296 全绿、工作树干净”的 M9 证据，下游校验无法区分真实运行与伪造，也无法判断是否有测试被 `-Dtest`/excludes 悄悄裁掉。建议至少记录实际命令行、工具链版本摘要以及签名或可复验的 run 标识。
- 判断：本地核对 buildResult="SUCCESS"；ciRunUrl=null。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-pre-hosted-local-results.json:7；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M9-pre-hosted-local-results.json:4；docs/testing/M9-pre-hosted-local-results.json:15

## OCR-1807 · 不采纳

- 位置：docs/testing/M9-pre-hosted-local-results.json:860；OCR原严重度：medium
- 计划：无
- 原意见：`sourceSha256` 覆盖了 `harness-agent`、`harness-ecosystem`、`harness-web` 的主源码，但 `suites` 中没有任何来自这三个模块的测试类，记录里也没有 per-module 的测试计数或“该模块无测试/被排除”的说明。触发条件：这些模块（DefaultAgentLoop、ContextCompactionPlugin、Mcp/Http 传输、HarnessWebServer 等）编译或行为回归时，本记录仍可能呈现 296 全绿，验证方无法据此判断它们的代码是否真的被执行过、也无法确认 296 这个总数覆盖了哪些模块。建议增加 per-module 汇总（模块 → suites 数/tests 数/排除原因），并在清单中区分“参与编译”与“被测试覆盖”的文件。
- 判断：本地核对 sourceSha256=对象(260键)；suites=数组(49项)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M9-pre-hosted-local-results.json:860；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M9-pre-hosted-local-results.json:23

## OCR-1820 · 不采纳

- 位置：third-party/upstream/sources.json:14；OCR原严重度：medium
- 计划：无
- 原意见：本清单混用了固定提交与可变标签两种钉法：pty4j（第4/9行）和 terminal（第34/39行）使用 40 位 commit SHA，而 kotlin（第14/19行 `v2.1.21`）、winpty（第24行 `0.4.3`）、maven-wrapper（第29行 `maven-wrapper-3.3.4`）、annotations（第44行 `24.0.1`）都指向可被上游移动、重写或删除的标签。仓库内没有流程按 `url` 重新下载并与 `sha256` 比对（`scripts/build-candidate.py` 第33-35行只校验本地已存在文件的哈希），因此标签后移时重新取证会静默替换许可证/声明字节，而新哈希仍能通过校验——“哈希正确”无法证明“来源未变”。建议把这 5 个 URL 统一改为标签对应的固定 commit SHA，使重取结果可复现。
- 判断：本地核对 [0].url="https://raw.githubusercontent.com/JetBrains/pty4j/b7554d649f6374183040e6554942b6d...；[0].sha256="3a4a6fe55f67f02b92be0ec9907e347de8ab5c8edf9214c5c8019e5820517884"。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：third-party/upstream/sources.json:14；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；third-party/upstream/sources.json:4；third-party/upstream/sources.json:5

## OCR-1821 · 部分采纳

- 位置：third-party/upstream/sources.json:28；OCR原严重度：low
- 计划：R6-04 / P3
- 原意见：许可证/声明文件覆盖不完整且未说明原因：wrapper（第28-30行）只钉了 NOTICE 而没有 LICENSE，annotations（第43-45行）只钉了 LICENSE 而没有 NOTICE。Maven Wrapper 3.3.4 为 Apache-2.0（`.mvn/wrapper/maven-wrapper.properties` 第1行确认为 3.3.4），再分发 mvnw/mvnw.cmd 时需随附许可证文本；目前只能靠根目录 Apache-2.0 LICENSE 兜底，无法从本清单证明 wrapper 的许可证来源。请核对上游是否确实不存在对应文件：若存在，应补充 `wrapper-LICENSE`、`annotations-NOTICE` 条目；若不存在，应在清单或 README 中记录依据，避免把“未采集”误当成“上游无此文件”。
- 判断：sources.json确只有wrapper-NOTICE/annotations-LICENSE，Wrapper官方3.3.4 LICENSE确为Apache-2.0且本地根LICENSE随包。只采纳在第三方来源映射明确Wrapper共用根LICENSE/官方来源的低优先说明，不宣告正文缺失或违法。annotations额外NOTICE是否存在，固定tag/NOTICE官方读取均Cache miss，未证明的子主张不采纳、不凭空补文件。
- 证据：third-party/upstream/sources.json:28；third-party/README.md:27；LICENSE；https://raw.githubusercontent.com/apache/maven-wrapper/maven-wrapper-3.3.4/LICENSE

## OCR-2016 · 采纳

- 位置：docs/testing/tools/verify-m9-evidence.py:42；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：“源码未变”这道门的覆盖面小于 receipt 声称的 `sourcesMatchCurrent`：(1) `git diff` 只比较 `*.java` 与 `*pom.xml`，`src/main/resources`、`*.properties`/`*.json` 配置、`tools/*.py`、`.github/workflows/*.yml` 等真实构建输入被修改时完全不报错；(2) `git diff` 不包含未跟踪文件，新建在 `module/src` 之外的源文件（例如新模块目录、额外 `pom` 之外的脚本）既不会出现在 diff 中，也不会进入下面的 `expected_sources` 集合；(3) 目标提交在本地缺失（浅克隆）时 `check_output` 抛 `CalledProcessError(128)` 而没有任何断言说明。触发条件：改动任一非 Java/POM 源文件后运行本脚本，仍会写出 `sourcesMatchCurrent: true` 的证据。建议把受控输入集合（资源目录、工作流、工具脚本）纳入路径规范，并用 `git ls-files --others --exclude-standard` 单独拦截未跟踪文件，同时给 git 调用加显式错误处理。
- 判断：本地核对 source_diff = subprocess.check_output(['git', 'diff', run['head_sha'], '--', '*.java', '*pom.xml'], cwd=ROOT) assert not sour。verifier只git diff *.java/*pom.xml且expected_sources只枚举Java/POM；sourcesMatchCurrent无法覆盖resources、wrapper、脚本和前端构建输入。应按声明的完整构建输入清单校验并增加非Java修改反例。
- 证据：docs/testing/tools/verify-m9-evidence.py:42；docs/testing/tools/verify-m9-evidence.py:45

## OCR-2017 · 采纳

- 位置：docs/testing/tools/verify-m9-evidence.py:147；OCR原严重度：low
- 计划：R0-02 / P2
- 原意见：证据写入是非原子的且不清理旧产物：`to_write` 逐文件 `write_bytes`、最后再写 `M9-ci-results.json`，中途失败（磁盘满、进程被杀）会留下新旧混合的证据集；同时上一轮/上一平台遗留的 `M9-hosted-*.json` 不会被删除，仍会被当作本次证据阅读。建议先写入同目录临时文件再用 `os.replace` 原子替换，并在成功路径上清理或先期校验 `M9-hosted-*` 目录内容与本次 `to_write` 键集合一致。
- 判断：本地核对 (OUT / name).write_bytes(content) (OUT / 'M9-ci-results.json').write_bytes((json.dumps(receipt, indent=2) + '\n').encode()) p。所有验证完成后逐文件write_bytes，最后写receipt；验证中失败不会写，但落盘中断可能形成派生文件新旧混合。应在临时目录完整生成、校验并原子发布整批索引/记录，保留上次有效批次。
- 证据：docs/testing/tools/verify-m9-evidence.py:147；docs/testing/tools/verify-m9-evidence.py:153

## OCR-2018 · 采纳

- 位置：docs/testing/tools/verify-m9-evidence.py:24；OCR原严重度：low
- 计划：R0-02 / P3
- 原意见：缺少参数校验：直接 `sys.argv[1]` 在无参数时抛 `IndexError: list index out of range`，非数字时抛 `ValueError`，而这两种情况应在审计开始前以明确的用法提示退出（该脚本被当作证据链的一部分，崩溃信息应可诊断）。
- 判断：本地核对 RUN_ID = int(sys.argv[1]) CACHE = ROOT / '.build-cache/M9-hosted-final' OUT = ROOT / 'docs/testing'。RUN_ID直接int(sys.argv[1])和next(...)缺少面向操作者的参数/缺项诊断；失败仍封闭退出，但IndexError/ValueError/StopIteration不利定位。采纳argparse与显式require缺项消息。
- 证据：docs/testing/tools/verify-m9-evidence.py:24；docs/testing/tools/verify-m9-evidence.py:28；docs/testing/tools/verify-m9-evidence.py:60

## OCR-2019 · 采纳

- 位置：docs/testing/tools/verify-m9-evidence.py:113；OCR原严重度：medium
- 计划：R0-01 / P1
- 原意见：“JAR 内含法律资源”的校验是空的：循环体只在 `name.startswith('third-party/')` 时断言，若 `manifest['files']` 中根本不存在任何 `third-party/` 条目（例如 prepare-legal 步骤被跳过或产出为空），循环不执行任何断言，随后 receipt 仍无条件记录 `'legalResourcesInJar': True`。建议先断言至少存在关键条目（如 `third-party/README.md`、`third-party/inventory.json` 及数量下限），再逐条比对内容。
- 判断：本地核对 if name.startswith('third-party/'):                         assert executable.read('META-INF/jharness/' + name) == bundle.rea。法律条目仅对manifest中已有third-party/循环；集合为空时循环零次仍写legalResourcesInJar=true，缺必需条目覆盖断言。应先验LICENSE/NOTICE/inventory及必需坐标全集，再核对JAR与ZIP字节。
- 证据：docs/testing/tools/verify-m9-evidence.py:113；docs/testing/tools/verify-m9-evidence.py:116

## OCR-2020 · 采纳

- 位置：docs/testing/tools/verify-m9-evidence.py:59；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：定位类断言全部使用无默认值的 `next(...)`：`f'verify ({label})'` 一旦改名或 jobs 数量变化，直接抛裸 `StopIteration`；更糟的是下一行 `all(next(...) for step in [...])` 中的 `next` 位于生成器表达式内部，按 PEP 479 会被转换成 `RuntimeError: generator raised StopIteration`，报错完全看不出是哪个 step/平台缺失。`artifact`、`cli_component` 的 `next` 同理。建议统一加默认值与断言信息，便于区分“证据缺失”与“验证失败”。
- 判断：本地核对 job = next(j for j in jobs if j['name'] == f'verify ({label})')     assert all(next(s for s in job['steps'] if s['name'] == s。RUN_ID直接int(sys.argv[1])和next(...)缺少面向操作者的参数/缺项诊断；失败仍封闭退出，但IndexError/ValueError/StopIteration不利定位。采纳argparse与显式require缺项消息。
- 证据：docs/testing/tools/verify-m9-evidence.py:59；docs/testing/tools/verify-m9-evidence.py:28；docs/testing/tools/verify-m9-evidence.py:60

## OCR-2021 · 采纳

- 位置：docs/testing/tools/verify-m9-evidence.py:133；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：可选日志这道门只是“子串存在性”检查：只要求日志里出现 `Ran 5 tests` 与 `paidRequests`，因此一个 `Ran 5 tests ... FAILED (failures=2)` 的日志同样通过，并且 `offlineGateTests=5` 被硬编码写入 receipt，等于把“5 项离线门禁通过”这一结论建立在字符串出现过而非结果正确之上。建议匹配测试运行的最终结果行（成功标记且不含 `FAILED`/`ERROR`），或直接解析门禁输出中的 PASS/FAIL 计数后写入。
- 判断：本地核对 assert 'Ran 5 tests' in job_text and 'paidRequests' in job_text             entry = receipt['platforms'][-1]             entr。可选job log只包含Ran 5 tests和paidRequests两个子串就记offlineGateTests=5；FAILED(failures=2)日志也含这些子串。需解析真实测试摘要、退出结果和必需测试标识，增加失败日志反例。
- 证据：docs/testing/tools/verify-m9-evidence.py:133；docs/testing/tools/verify-m9-evidence.py:136

## OCR-2392 · 不采纳

- 位置：LICENSE:189；OCR原严重度：medium
- 计划：无
- 原意见：LICENSE 正文（第 1–172 行）与 Apache-2.0 官方文本一致，但附录仍保留未填充的占位符 `[yyyy] [name of copyright owner]`，且仓库根目录不存在 NOTICE/COPYRIGHT 文件，源码与 pom.xml 中也未出现任何版权人声明（已检索 `Copyright (c)`/`Copyright <year>`/SPDX-FileCopyrightText`，均无匹配）。由此导致： 1) 下游无法确定“Licensor”/版权所有者，无法履行 §4(c) “保留版权、专利、商标与归属声明”的义务； 2) 自动化工具（licensee/scancode/SPDX 生成器）会把该占位符当作真实版权行写入 SBOM 与分发物元数据，产生错误归属； 3) pom.xml 仅声明 `Apache License, Version 2.0`，无 organization/inceptionYear 等归属信息，与本文件一样缺少权利主体。 建议：在 LICENSE 附录处填写实际版权行（或在根目录新增 NOTICE 文件并在 pom.xml 中补充 organization/版权信息），保持与 README、pom.xml 的 Apache-2.0 声明一致。触发条件：任何人执行发行打包或 SPDX/SBOM 生成即会暴露该缺失，无需运行时条件。
- 判断：本地核对 Copyright [yyyy] [name of copyright owner]    Licensed under the Apache License, Version 2.0 (the "License");。Apache正文附录模板应原样保留，不要求编辑[yyyy]；根NOTICE实际存在并有Copyright 2026 J-Harness contributors，原意见不存在NOTICE/版权人前提已被本地文件否定。
- 证据：LICENSE:189；LICENSE:附录；NOTICE:1

## OCR-2394 · 部分采纳

- 位置：third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-ThirdParty-LICENSE:61；OCR原严重度：low
- 计划：R6-04 / P3
- 原意见：版权年份与实际复现的许可文本不一致：此处标注 bigint 版权为 “Copyright 2020 Tim Buktu”，而同一文件中第 69 行 BSD-2-Clause 正文写的是 “Copyright 2022 Tim Buktu”。BSD-2-Clause 第 1/2 条要求源码与二进制分发都必须准确保留版权声明，年份互相矛盾会导致该声明与上游 bigint-LICENSE 无法逐字对应，属于再分发合规风险（且该文件是随 META-INF 打入 jar 的正式声明材料）。建议与上游 https://github.com/tbuktu/bigint 的 LICENSE 核对后统一为正确年份。
- 判断：本地核对 bigint, Copyright 2020 Tim Buktu. 2-clause BSD License. https://github.com/tbuktu/bigint/tree/floatfft https://github.com/tbu。本地保留材料内确有不同年份/fast_float commit引用；都是第三方原JAR提取文本，不证明本仓库修改归因。采纳在法律库存记录上游材料差异并核实相应构件/版本，保留原文，不能猜一个年份/commit覆盖所有材料。
- 证据：third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-ThirdParty-LICENSE:61；third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-LICENSE:48；third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-ThirdParty-LICENSE:69；third-party/README.md:8

## OCR-2395 · 不采纳

- 位置：third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-ThirdParty-LICENSE:82；OCR原严重度：low
- 计划：无
- 原意见：BSD-2-Clause 正文中的免责声明被改成了弯引号 “AS IS”（U+201C/U+201D），而上游 LICENSE 原文为直引号 "AS IS"。许可文本应逐字保留，任何字符层面的改动都可能被解读为对许可条款的修改，建议改回原文的直引号。
- 判断：本地核对 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS “AS IS” AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,。没有本地证据证明弯引号是本仓库修改而非原始JAR材料；法律资源按原字节保留。不能按猜测把第三方许可正文直引号化并破坏来源哈希。
- 证据：third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-ThirdParty-LICENSE:82；third-party/README.md:8

## OCR-2396 · 部分采纳

- 位置：third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-LICENSE:0；OCR原严重度：medium
- 计划：R6-04 / P3
- 原意见：bigint 的版权年份在此处写为 2020，而 `FastDoubleParser-ThirdParty-LICENSE` 第 69 行复现的 2-clause BSD 许可证正文写的是 `Copyright 2022 Tim Buktu`。BSD-2-Clause 第 1/2 条要求准确保留版权声明，两处年份冲突意味着至少有一处未忠实复现上游声明，存在合规风险。建议与上游 `bigint-LICENSE` 核对后统一。
- 判断：本地核对 MIT License Copyright (c) 2024 Werner Randelshofer, Switzerland.。本地保留材料内确有不同年份/fast_float commit引用；都是第三方原JAR提取文本，不证明本仓库修改归因。采纳在法律库存记录上游材料差异并核实相应构件/版本，保留原文，不能猜一个年份/commit覆盖所有材料。
- 证据：third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-LICENSE:1；third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-LICENSE:48；third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-ThirdParty-LICENSE:69；third-party/README.md:8

## OCR-2397 · 不采纳

- 位置：third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-LICENSE:0；OCR原严重度：medium
- 计划：无
- 原意见：此处声明 `LICENSE.BSL` 已随 jar 一并打包，但在本 artifact 的 META-INF 目录下实际只有 `LICENSE`(Apache-2.0)、`FastDoubleParser-LICENSE`、`FastDoubleParser-ThirdParty-LICENSE`、`NOTICE` 四个文件，并不存在独立的 `LICENSE.BSL`/`LICENSE-MIT`/`bigint-LICENSE`（见本文件第 45、51、59 行相同的三处声明）。BSL 1.0 要求随二进制分发时复制完整许可证文本，目前该义务仅靠 `FastDoubleParser-ThirdParty-LICENSE` 第 8-30 行内嵌的 BSL 全文来满足；一旦该文件被裁剪或后续重新打包时遗漏，就会直接构成许可证合规缺陷。建议核实打包内容，并据此修正这些“文件已包含”的表述或补齐独立许可文件。
- 判断：本地核对 MIT License Copyright (c) 2024 Werner Randelshofer, Switzerland.。FastDoubleParser-LICENSE指向的LICENSE.BSL已在FastDoubleParser-ThirdParty-LICENSE中附完整Boost正文，并明确see该文件；不要求必然有单独同名文件。
- 证据：third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-LICENSE:1；third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-ThirdParty-LICENSE:7

## OCR-2398 · 部分采纳

- 位置：third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-LICENSE:0；OCR原严重度：medium
- 计划：R6-04 / P3
- 原意见：同一 artifact 内两份说明对 fast_float 的上游 commit 引用不一致：本行固定为 `cc1e01e9eee74128e48d51488a6b1df4a767a810`，而 `FastDoubleParser-ThirdParty-LICENSE` 第 36 行固定为 `35d523195bf7d57aba0e735ad6eba1e6f71ba8d6`。两处都声称是随包代码所依据的上游许可证文本，无法判断哪个快照才与实际 shade 进来的代码对应，属于可复现的归属/版本一致性问题。建议统一为同一 commit（或写明各自对应的组件范围）。
- 判断：本地核对 MIT License Copyright (c) 2024 Werner Randelshofer, Switzerland.。本地保留材料内确有不同年份/fast_float commit引用；都是第三方原JAR提取文本，不证明本仓库修改归因。采纳在法律库存记录上游材料差异并核实相应构件/版本，保留原文，不能猜一个年份/commit覆盖所有材料。
- 证据：third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-LICENSE:1；third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-LICENSE:48；third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-ThirdParty-LICENSE:69；third-party/README.md:8

## OCR-2399 · 部分采纳

- 位置：third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-LICENSE:0；OCR原严重度：medium
- 计划：R6-04 / P3
- 原意见：本文件与第 29 行均标注 FastDoubleParser 版权年份为 2024，但同目录 `NOTICE` 第 29 行对同一段被 shade 的代码标注的是 `Copyright © 2023 Werner Randelshofer, Switzerland.`。同一 artifact 内对同一组件的版权年份自相矛盾，且随包代码版本（jackson-core 2.21.7）无法与所引 commit `522be16e...` 对应核验，至少有一处归属信息是错的。建议以实际上游 LICENSE 为准统一年份并同步 `NOTICE`。
- 判断：本地核对 MIT License Copyright (c) 2024 Werner Randelshofer, Switzerland.。本地保留材料内确有不同年份/fast_float commit引用；都是第三方原JAR提取文本，不证明本仓库修改归因。采纳在法律库存记录上游材料差异并核实相应构件/版本，保留原文，不能猜一个年份/commit覆盖所有材料。
- 证据：third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-LICENSE:1；third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-LICENSE:48；third-party/artifacts/com.fasterxml.jackson.core/jackson-core/2.21.7/META-INF/FastDoubleParser-ThirdParty-LICENSE:69；third-party/README.md:8

## OCR-2400 · 部分采纳

- 位置：third-party/artifacts/net.java.dev.jna/jna-platform/5.14.0/META-INF/LICENSE:1；OCR原严重度：medium
- 计划：R6-04 / P3
- 原意见：SPDX 标识与本文件正文及 third-party/inventory.json 中的声明不一致：正文第 3-4 行为 “LGPL, version 2.1 or later”（即 “or-later”），inventory.json 中该构件 declaredLicenses 也登记为 `LGPL-2.1-or-later`，而此处写死为 `LGPL-2.1`。触发条件：任何按 SPDX 解析该文件许可证的工具（许可证扫描、合规门禁、SBOM 生成）会将本构件判定为 “仅 LGPL-2.1” 而非 “LGPL-2.1-or-later”，从而与 inventory 记录冲突或产生误判的合规结论。建议改为 `Apache-2.0 OR LGPL-2.1-or-later` 以与正文和清单保持一致。
- 判断：本地核对 SPDX-License-Identifier: Apache-2.0 OR LGPL-2.1 Java Native Access (JNA) is licensed under the LGPL, version 2.1。保留的上游SPDX仅LGPL-2.1而正文说2.1 or later且有Apache选项，局部文本不一致确实可记录；本分发已选择Apache-2.0，不能直接手改上游LICENSE。采纳在库存/附加说明声明所选许可和原文差异。
- 证据：third-party/artifacts/net.java.dev.jna/jna-platform/5.14.0/META-INF/LICENSE:1；third-party/artifacts/net.java.dev.jna/jna/5.14.0/META-INF/LICENSE:1；third-party/README.md:23

## OCR-2401 · 不采纳

- 位置：third-party/artifacts/net.java.dev.jna/jna-platform/5.14.0/META-INF/LICENSE:13；OCR原严重度：medium
- 计划：无
- 原意见：此处声称同目录下附带 `LGPL2.1` 文件，但该构件目录 third-party/artifacts/net.java.dev.jna/jna-platform/5.14.0/META-INF/ 下仅有 LICENSE 一个文件，全仓库也检索不到 LGPL2.1。触发条件：分发该构件（或以其作为第三方法律文件来源）时，实际随包提供的许可证文本不完整，无法满足 LGPL 2.1 第 4/6 条关于随附许可证副本的要求，离线或审计环境无法核对条款。建议将 LGPL-2.1 完整文本作为同目录附属文件入库（并与 inventory.json 的 legal/supplemental 条目登记其 sha256），或修正这段指向说明。
- 判断：本地核对 A copy is also included in the downloadable source code package containing JNA, in file "LGPL2.1", under the same directory a。原JNA文件说明完整LGPL2.1/AL2.0在可下载source package，不声称本仓库artifact目录同名文件必有；本分发明确选Apache-2.0且根LICENSE完整随包存在。不能据没有LGPL文件认定当前发行缺所选许可。
- 证据：third-party/artifacts/net.java.dev.jna/jna-platform/5.14.0/META-INF/LICENSE:13；third-party/artifacts/net.java.dev.jna/jna/5.14.0/META-INF/LICENSE:14；third-party/README.md:23；LICENSE

## OCR-2402 · 不采纳

- 位置：third-party/artifacts/net.java.dev.jna/jna-platform/5.14.0/META-INF/LICENSE:22；OCR原严重度：low
- 计划：无
- 原意见：同样地，Apache-2.0 的 `AL2.0` 副本在本构件目录及全仓库均不存在，仅有的其他声明是 inventory.json 中的 URL。触发条件：仅分发 artifacts 目录内容（不联网）时，Apache-2.0 第 4(a) 条要求随附的许可证副本缺失，合规审核无法完成。建议补充 `AL2.0` 全文文件并登记到 inventory.json 的 legal 列表。
- 判断：本地核对 containing JNA, in file "AL2.0", under the same directory as this file.。原JNA文件说明完整LGPL2.1/AL2.0在可下载source package，不声称本仓库artifact目录同名文件必有；本分发明确选Apache-2.0且根LICENSE完整随包存在。不能据没有LGPL文件认定当前发行缺所选许可。
- 证据：third-party/artifacts/net.java.dev.jna/jna-platform/5.14.0/META-INF/LICENSE:22；third-party/artifacts/net.java.dev.jna/jna/5.14.0/META-INF/LICENSE:14；third-party/README.md:23；LICENSE

## OCR-2403 · 部分采纳

- 位置：third-party/artifacts/net.java.dev.jna/jna/5.14.0/META-INF/LICENSE:1；OCR原严重度：medium
- 计划：R6-04 / P3
- 原意见：SPDX 标识与正文语义不一致：正文第 3-5 行与第 8 行明确为 "LGPL, version 2.1 or later" / "from version 4.0 onward the Apache License, version 2.0"，即应为 LGPL-2.1-or-later，而这里声明为 `LGPL-2.1`（仅 2.1 版本，不含 or-later）。在依赖许可证扫描/合规审计中，该标识会把可选许可收窄为 LGPL-2.1 精确版本，导致与上游实际授权及汇总 NOTICE 产生差异。建议改为 `SPDX-License-Identifier: Apache-2.0 OR LGPL-2.1-or-later`，并与项目依赖许可证清单保持一致。
- 判断：本地核对 SPDX-License-Identifier: Apache-2.0 OR LGPL-2.1 Java Native Access (JNA) is licensed under the LGPL, version 2.1。保留的上游SPDX仅LGPL-2.1而正文说2.1 or later且有Apache选项，局部文本不一致确实可记录；本分发已选择Apache-2.0，不能直接手改上游LICENSE。采纳在库存/附加说明声明所选许可和原文差异。
- 证据：third-party/artifacts/net.java.dev.jna/jna/5.14.0/META-INF/LICENSE:1；third-party/README.md:23

## OCR-2404 · 不采纳

- 位置：third-party/artifacts/net.java.dev.jna/jna/5.14.0/META-INF/LICENSE:13；OCR原严重度：high
- 计划：无
- 原意见：许可证正文缺失：本文件声明完整许可证文本位于同目录的 "LGPL2.1" 与 "AL2.0"（本文件第 16-24 行），但仓库中该目录仅存在本 LICENSE 文件（`third-party/artifacts/net.java.dev.jna/jna/5.14.0/META-INF/` 下无 LGPL2.1 / AL2.0，全仓搜索也无匹配），jna-platform/5.14.0 同样如此。触发条件：按 LGPL-2.1 分发二进制产物时，未随附许可证完整副本即不满足 LGPL-2.1 第 1/6 条关于许可证文本随附的要求，且与文件自身的声明不一致。建议在 META-INF 下补齐 LGPL2.1 与 AL2.0 文本，或修正声明指向实际随仓的许可证文件。
- 判断：本地核对 A copy is also included in the downloadable source code package containing JNA, in file "LGPL2.1", under the same directory a。原JNA文件说明完整LGPL2.1/AL2.0在可下载source package，不声称本仓库artifact目录同名文件必有；本分发明确选Apache-2.0且根LICENSE完整随包存在。不能据没有LGPL文件认定当前发行缺所选许可。
- 证据：third-party/artifacts/net.java.dev.jna/jna/5.14.0/META-INF/LICENSE:13；third-party/artifacts/net.java.dev.jna/jna/5.14.0/META-INF/LICENSE:14；third-party/README.md:23；LICENSE

## OCR-2405 · 部分采纳

- 位置：NOTICE:0；OCR原严重度：medium
- 计划：R6-04 / P3
- 原意见：本 NOTICE 会随 harness-cli 的 shaded JAR（`META-INF/jharness/NOTICE`）和候选 ZIP 一起分发，但此处指向的 `frontend/upstream.json` 与 `frontend/LICENSE-DeepSeek-Harness.txt` 只存在于源码树：harness-web 仅把 `frontend/dist` 打包为 `ui/`，build-candidate.py 也只写入 `frontend-bom.json`，均不包含这两个文件。结果是在二进制分发物中无法按 NOTICE 给出的路径取得逐文件来源与原始 MIT 许可正文（`ui-license.txt` 只含许可全文，不含 upstream.json 的 source/commit/sha256 映射），第三方归属的可核验性被削弱。建议把 upstream.json 及 DeepSeek 许可原文一并复制进 `frontend/dist`（例如 `ui/upstream.json`、`ui/LICENSE-DeepSeek-Harness.txt`），并在 NOTICE 中改为指向包内相对路径。
- 判断：本地核对 J-Harness Copyright 2026 J-Harness contributors。根NOTICE指向frontend来源文件属于源码分发路径，运行JAR的许可已另指ui/ui-license.txt。采纳标明哪些路径仅源分发可用并给ZIP/JAR各自精确资源路径，不认定版权声明全缺。
- 证据：NOTICE:1；NOTICE:11；scripts/prepare-legal.py

## OCR-2406 · 不采纳

- 位置：NOTICE:7；OCR原严重度：medium
- 计划：无
- 原意见：此处只显式列出了仅参与构建的 Maven Wrapper 与 Web UI 的 MIT 组件，却未列出真正被 shaded 进运行时 JAR 的第三方运行时依赖。经核对，`harness-process` 的 `org.jetbrains.pty4j:pty4j:0.13.12`（EPL-1.0，并分发 WinPTY/ConPTY 原生二进制）以及 JNA、Jackson、SLF4J、Kotlin stdlib 等都会随 `lib/jharness.jar` 分发（third-party/inventory.json 中 usage=runtime）。顶层 NOTICE 作为对外最直接的归属声明，建议至少补充 pty4j 的 EPL-1.0 与内嵌原生二进制、JNA 双许可等运行时条目，并明确指向 JAR 内 `META-INF/jharness/third-party/README.md` 与上游 NOTICE 的包内位置；否则仅凭本文件会让人误以为运行时只有 Apache-2.0/MIT 组件。
- 判断：本地核对 The source distribution includes Apache Maven Wrapper 3.3.4 startup scripts, copyright The Apache Software Foundation, licens。NOTICE已明确第三方完整信息在随包third-party/README.md和坐标库存；所有运行依赖法律材料按坐标保留并打包，并非根NOTICE必须逐一重复列所有依赖。
- 证据：NOTICE:7；NOTICE:4；third-party/README.md:3；scripts/prepare-legal.py

## OCR-2407 · 采纳

- 位置：.agent:105；OCR原严重度：high
- 计划：R6-04 / P2
- 原意见：验证流程仍硬编码 M8.4 的里程碑/切片与日志名。当前入口已是 M10.5.9（证据为 docs/testing/M10.5.9-results.json / M10.5.9-validation.md，状态 LOCAL_PARTIAL_CI_PENDING）。照此命令执行会把 M10.5.x 的全量结果采集到 `-Milestone M8 -Slice M8.4` 名下，并覆盖 `.build-cache/M8.4-verify.log`；而 `scripts/verify-m8-closure.py`、`docs/testing/M8-closure-results.json` 正是以 M8.4 名称/时间关联做校验，结果是历史关闭证据被覆盖或当前结果被错标为 M8.4 通过，违反“证据不得覆盖/错标、部分结果不计通过”。建议参数与日志名改为当前切片（如 `-Milestone M10.5 -Slice M10.5.9 -VerificationLog .build-cache/M10.5.9-verify.log`），并把 M8/M7 段落显式标注为历史命令。
- 判断：本地核对 .\scripts\collect-test-evidence.ps1 -Milestone M8 -Slice M8.4 -VerificationLog .build-cache/M8.4-verify.log & "$env:JAVA_HOME。验证流程通用示例仍用Milestone M8/Slice M8.4，当前顶部却为M10.5.9；直接复制可能误标当次证据。需改成当前入口链接或参数占位并显式标历史命令。
- 证据：.agent:105；.agent:验证流程

## OCR-2408 · 部分采纳

- 位置：.agent:117；OCR原严重度：medium
- 计划：R6-04 / P3
- 原意见：.agent still presents M9 as the current entry/reading focus and M8 as the current gate, while the active phase is M10.5.9 (LOCAL_PARTIAL_CI_PENDING, m11Eligible=false) with documents such as docs/development/M10.5.9-contracts.md and docs/testing/M10.5.9-validation.md. Update the current-entry and reading-list lines to reference M10.5.9/M11 gating, and mark M8/M9 references as historical.
- 判断：本地核对 ## 历史验收与接续快照（M7/M8；当前入口见顶部和最新M9接续） 最终 Windows 命令 .\mvnw.cmd -B clean verify，Linux 命令见 M7-linux-results.json；两侧各 260 项通过，236 个。历史小节仍写最新M9接续，与顶部M10.5.9实际入口不一致；采纳修正历史章节导航，历史逐日快照本身不重写。
- 证据：.agent:117；.agent:历史验收与接续快照

## OCR-2409 · 不采纳

- 位置：.agent:0；OCR原严重度：medium
- 计划：无
- 原意见：该条与本文件后一条 2026-10-17 记录（“启动器不再复制凭据/修改剪贴板”）以及“token 仅页面内存，禁止 URL/存储”的约束直接冲突：按此描述启动器会把 Bearer token 写入系统剪贴板，剪贴板历史/其他进程可读取，构成凭据外泄路径（且与“Bearer 仅单操作者、脱敏、不进浏览器存储”的声明的安全边界不一致）。当前条目未标注已被取代，复验者可能重新引入该行为。建议将其明确标注为历史行为且已被 2026-10-17 变更取代，并声明现行启动器不复制 token。
- 判断：本地核对 # J-Harness 项目接续说明 最后更新：2026-10-20（Asia/Shanghai）。根目录 AGENTS.md 引用本文件。开始实现前阅读，并以当前源码、Git 与验收记录核实日期快照。。该条明确2026-10-16历史行为，2026-10-17自动入口新记录明确取消剪贴板；文件说明以下带日期小节是当时快照，顶部当前状态优先。当前启动器无复制代码，不能将旧描述认定现行泄漏。
- 证据：.agent:1；.agent:2026-10-17 M10直接入口已完成；scripts/open-workbench.ps1

## OCR-2410 · 不采纳

- 位置：.agent:0；OCR原严重度：medium
- 计划：无
- 原意见：本条与紧邻的上一条（2026-10-20 OCR 全项目审查进行中，基线 ebdb576、开始时工作区干净、原始哈希/规则/输出留在忽略缓存）在顺序上互相矛盾：若本次删除草稿/日志/探针发生在扫描开始之后，则扫描期间的工作区干净前提与基线哈希不再成立，引用“开始时工作区干净”的证据链会失效；且下方多条历史条目仍写“保留原审查草稿”，与“已由本条清理操作取代”并存，接续者无法判定审查证据到底是否存在。建议显式写出两件事的先后关系（清理在扫描前/后）、扫描是否需重跑基线核对，并避免删除仍在被引用的证据路径。
- 判断：本地核对 # J-Harness 项目接续说明 最后更新：2026-10-20（Asia/Shanghai）。根目录 AGENTS.md 引用本文件。开始实现前阅读，并以当前源码、Git 与验收记录核实日期快照。。记录按新到旧排列；清理旧OCR与后续新OCR是不同操作，清理条明确取代此前保留旧草稿说明。没有扫描过程中删新证据的事实，不能凭排列顺序推翻干净基线。
- 证据：.agent:1；.agent:当前接续状态

## OCR-2411 · 不采纳

- 位置：third-party/upstream/terminal-NOTICE:1；OCR原严重度：medium
- 计划：无
- 原意见：该文件是从 microsoft/terminal 固定 commit（44b440b）原样同步的第三方 NOTICE，其内容与 sha256 已在 third-party/upstream/sources.json 第 38-40 行（terminal-NOTICE，e7fbaade…）登记用于校验。因此后续任何合规修复都不应直接在本文件手改：本地修改会使哈希与 sources.json 不一致，导致同步/校验流程失败，且下次同步会被覆盖。建议把下列问题反馈到上游仓库，重新生成后再同步并更新 sources.json 中的 sha256。
- 判断：本地核对 # NOTICES AND INFORMATION Do Not Translate or Localize。这是正确的上游材料保全注意事项，当前third-party/README和sources.json已固定原文来源/hash；没有发现现有手改缺陷，无需单列修复。
- 证据：third-party/upstream/terminal-NOTICE:1；third-party/upstream/sources.json:39；third-party/README.md:18

## OCR-2412 · 不采纳

- 位置：third-party/upstream/terminal-NOTICE:17；OCR原严重度：medium
- 计划：无
- 原意见：此处保留了一条针对 "GNU Lesser General Public License" 库的反向工程许可声明，但本 NOTICE 全文未列出任何 LGPL 授权组件（其余条目均为 MIT/BSD/Apache-style/Public Domain）。仓库内唯一涉及 LGPL 的 JNA 是 LGPL-2.1-or-later OR Apache-2.0 双许可，且 third-party/README.md 第 23 行说明本分发选择 Apache-2.0。该声明要么是对不存在组件的无效授权（表述不准确），要么意味着存在未在 NOTICE 中披露的 LGPL 组件（归因缺失）。触发条件：合规审计比对授权声明与条目清单时必然出现不一致。建议与上游确认后删除该段，或补齐对应 LGPL 组件的条目。
- 判断：固定上游terminal NOTICE保留针对LGPL的附加授权原文，不等于本分发选择LGPL或声明必有该类组件。third-party README明确JNA选择Apache-2.0且Microsoft原生精确来源有限；没有证据支持删除上游声明，保留原文正确。
- 证据：third-party/upstream/terminal-NOTICE:17；third-party/README.md:18

## OCR-2413 · 不采纳

- 位置：third-party/upstream/terminal-NOTICE:269；OCR原严重度：high
- 计划：无
- 原意见：ColorBrewer 使用 Apache-Style 许可，但此处只给出 "You may obtain a copy of the License at" 及 Apache-2.0 链接（本行下文），既未附许可证全文，也未保留上游 NOTICE/致谢要求。Apache-2.0 §4(a) 要求向接收方提供 License 副本，§4(d) 要求随分发保留 NOTICE 内容；仅给 URL 在离线分发场景下不构成履约，存在分发合规缺口。触发条件：发布包含 ColorBrewer 颜色方案的产品/源码包，且接收方无法访问该 URL。建议补全许可证全文及 ColorBrewer 的版权与致谢声明。
- 判断：本地ColorBrewer条目已含2002 Cynthia Brewer/Mark Harrower/PSU版权与Apache-2.0声明，发行包另附完整根LICENSE。原意见只查看NOTICE局部便断言版权/许可全文均未提供，与分发材料全集不符；无需手改上游NOTICE。
- 证据：third-party/upstream/terminal-NOTICE:269；LICENSE；scripts/prepare-legal.py

## OCR-2414 · 不采纳

- 位置：third-party/upstream/terminal-NOTICE:493；OCR原严重度：medium
- 计划：无
- 原意见：该段以一句概括性声明把 GSL、Microsoft-UI-XAML、VirtualDesktopUtils、wil 全部归为 MIT，但随后只为 wil 附了许可证正文；第 495/499/503 行的三个项目仅有名称与源码链接，没有各自的版权声明行与许可正文。MIT 许可要求在所有副本中附带版权声明与许可声明，各项目版权主体/年份可能不同（如 GSL 为 Copyright (c) 2015 Microsoft Corporation），用单一项目的正文覆盖存在归因不准的风险。触发条件：合规审计逐项核对版权声明时无法在 NOTICE 中定位这三个项目的版权信息。建议逐项确认上游 LICENSE 确为 MIT，并为每个项目附上其自身的版权声明（或明确说明该段 MIT 正文适用于上述全部项目）。
- 判断：Microsoft Open Source段先明确上述项目均MIT，再附Microsoft Corporation完整MIT许可；不存在原意见所谓完全无版权声明。不能仅因License标题位于wil之后就证明其它项目漏归因，亦不能猜年份逐项覆盖上游文本。
- 证据：third-party/upstream/terminal-NOTICE:493；third-party/upstream/terminal-NOTICE:515

## OCR-2415 · 部分采纳

- 位置：third-party/upstream/terminal-NOTICE:369；OCR原严重度：low
- 计划：R6-04 / P3
- 原意见：文件名重复书写（"utf8.c and utf8.c"），应为 "utf8.c and utf8.h" 之类的实际文件列表。当前表述无法确定 cmark 中究竟哪些 utf8proc 派生文件受该段声明约束，属于归因描述不精确。
- 判断：本地原文确重复utf8.c and utf8.c，可记录为上游说明文字缺陷；没有证据证明正确替换是utf8.h。只采纳在归因差异清单标记、未来按固定上游修订同步，不直接改受hash约束的第三方文件。
- 证据：third-party/upstream/terminal-NOTICE:369；third-party/upstream/sources.json:39

## OCR-2416 · 不采纳

- 位置：third-party/upstream/terminal-NOTICE:462；OCR原严重度：low
- 计划：无
- 原意见：fzf 条目缺少 Source 链接与版本标识（其他条目至少给了仓库地址），且全文件各条目均未记录版本/commit。触发条件：需要确认 NOTICE 与本仓库实际内置的 third-party/upstream 代码版本是否一致时无法追溯，版本升级后也难以判断许可文本是否仍适用。建议补充 fzf 的上游仓库地址及所内置版本。
- 判断：fzf节确无独立Source行，但已保留作者和完整MIT原文；整个NOTICE来源由sources.json固定terminal commit/hash，不能说文件完全不可追溯。精确原生构建来源本已声明限制，未证明本地需要猜填fzf版本。
- 证据：third-party/upstream/terminal-NOTICE:462；third-party/upstream/sources.json:39；third-party/README.md:18

## OCR-2451 · 采纳

- 位置：docs/testing/M10.5.8-results.json:0；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：`e3b0c442…b855` 是空文件的 SHA-256，`m1058-sdk-typecheck.log` 与 `m1058-sdk-types.log` 都是空日志；本文件只记录哈希，未记录执行命令与退出码，因此无法区分“tsc 通过且无输出”与“命令未执行/被跳过”。这条证据不足以支撑 PASS，建议为每条证据记录 command 与 exitCode（或保存包含退出码的日志），使空日志可被判定为成功而非缺失。
- 判断：本地核对 milestone="M10.5.8"；scope="Local authorized content search, independent stdio 1.0, TypeScript/Python SDK and...。SDK typecheck collector仅检查日志trim为空，未记录或检查对应tsc退出码；未执行生成空文件也可满足这一门。需要命令/退出状态收据且检查成功，再允许空stdout；不能补造既有空日志的历史退出码。
- 证据：docs/testing/M10.5.8-results.json:1；scripts/collect-m1058-evidence.py:43；docs/testing/M10.5.8-results.json:developmentAttempts

## OCR-2452 · 不采纳

- 位置：docs/testing/M10.5.8-results.json:1547；OCR原严重度：medium
- 计划：无
- 原意见：大量 `m1058-final2-*` 日志与对应 `m1058-final-*` 哈希完全相同（browser/content/diagnosis/examples/extensions/history/launcher/models/questions/sdk/search/settings/turns 等，仅 native 不同），同时 `logs` 中的 `m1058-final-verify2.log`、`m1058-final-client.log` 又在 `developmentAttempts` 下重复出现。若 final2 被当作二次独立复跑的 PASS 证据，则判定被重复计数/高估；若确实复用首次日志，应显式标注（如 `reusedFrom`）并说明复用范围，否则无法证明二次验证真实发生。
- 判断：本地核对 logs=对象(2键)；developmentAttempts=对象(55键)。相同内容会有相同摘要，不能反推未执行或复制。浏览器自动同源进入使connection/welcome可已是同一状态；夹具日志仅打印确定性的PASS名称，不同引擎/复跑可逐字相同。应核对执行入口和制品来源，不能要求截图/日志哈希强制互异。
- 证据：docs/testing/M10.5.8-results.json:1547；frontend/tests/browser.mjs:43；frontend/tests/browser.mjs:54；frontend/tests/history.mjs:18；scripts/run-workbench-matrix.mjs:20；docs/testing/M10.5.8-results.json:1522；docs/testing/M10.5.8-results.json:1527

## OCR-2453 · 不采纳

- 位置：docs/testing/M10.5.8-results.json:1375；OCR原严重度：medium
- 计划：无
- 原意见：`frontendSha256` 仅覆盖 `src/` 与 `tests/`，缺少 `frontend/package.json`、依赖锁文件、`tsconfig.json` 与 Vite/构建配置；`sdkSha256` 同样缺少锁文件（只记录了 `sdk/package.json`、`sdk/typescript/package.json`）。依赖与编译配置不在哈希清单内，换环境后同一份源码哈希可能对应不同依赖树的构建结果，无法复现被测试的那一次构建。建议补齐 lockfile、tsconfig 与构建配置并纳入清单。
- 判断：本地核对 frontendSha256=对象(49键)；sdkSha256=对象(13键)。本条把阶段主清单、validation和固定源码中的上下文都要求在每份子记录重复保存；当前字段的简洁不证明来源错绑。旧报告按其当时约定取证，并非当前全树构建清单；未发现具体元数据缺失导致现有门禁错误放行，故不据泛化复现建议立独立修复。实际漏验构建输入另列2016。
- 证据：docs/testing/M10.5.8-results.json:1375；docs/testing/M10.5.8-validation.md:3；docs/testing/tools/verify-m9-evidence.py:67；docs/testing/M10.5.8-results.json:1426

## OCR-2454 · 部分采纳

- 位置：docs/testing/M10.5.8-results.json:1506；OCR原严重度：low
- 计划：R0-01 / P3
- 原意见：`checks` 中 `available: true` 与 `missing: true` 并列语义含糊：`missing` 既可被读作“凭据存储缺失（故障）”，也可能是“读取缺失凭据时正确返回未找到（通过）”，且与 `deleteMissing` 语义重叠。结合下方复用的 `historicalNativeCI`，很容易被误读为当前 native 校验失败，或把历史结果当作当前证据。建议改为自描述键名（如 `readMissingReturnsExplicitMissing`），并标注该组属于本次运行结果。
- 判断：本地核对 records.M10.5.8-search-results.json.checks=28；currentNative.checks.missing=true。available/missing均是探针检查是否符合预期的布尔，不是凭据库同时存在/缺失。采纳命名为readMissingReturnsEmpty等自描述结果并保留expected/actual，拒绝状态逻辑矛盾指控。
- 证据：docs/testing/M10.5.8-results.json:1506；docs/testing/M10.5.9-native-results.json:4；docs/testing/M10.5.8-results.json:1451

## OCR-2455 · 不采纳

- 位置：docs/testing/M10.5.8-results.json:1517；OCR原严重度：low
- 计划：无
- 原意见：`unchangedSources` 只列出两个文件路径，未内联当前哈希与 M10.5.4 中的对应哈希，“源文件未变”这一前提只能靠额外打开被引用的记录才能核验；一旦该记录被覆盖或缺失，历史 CI 证据就可能被当作当前证据支撑本次 PASS。建议在此内联两侧哈希，或明确声明历史 native CI 结果不参与本次 PASS 判定。
- 判断：本地核对 historicalNativeCI.unchangedSources=数组(2项)。historicalNativeCI已明确历史范围，currentNative单独本次验证；unchangedSources两路径与各阶段sourceSha256可交叉核对，额外打开被引用文件是正常证据链，不应等同缺证据。
- 证据：docs/testing/M10.5.8-results.json:1517；docs/testing/M10.5.8-validation.md:3；scripts/collect-m1058-evidence.py:78

## OCR-2456 · 部分采纳

- 位置：docs/testing/M10.5.8-results.json:1441；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：`jarSha256` 与 `currentNative.sha256` 都是无路径、无作用域的裸哈希，与同级以路径为键的 `uiSha256`、`sdkSha256`、`sourceSha256` schema 不一致：拿到清单也无法判定哈希对应哪个构件（例如 harness-cli 的 shaded jar 还是某个中间产物，或哪个原生凭据文件/探针输出），也无法把 `MANIFEST`/`sourceSha256` 中的输入与产物对应起来。同时未记录运行平台（macOS Keychain / Linux libsecret / Windows 凭据管理器）与后端实现（原生库 vs 文件回退），在另一台机器或 CI 上无法判断该证据是否适用、也不可复现比对。此外 jar 的输入（模块 `src/main/resources`、`mvnw`/`.mvn/wrapper` 等）未纳入任何哈希清单。建议改成形如 `"jarSha256": {"harness-cli/target/…-all.jar": "197b…"}`，记录 `backend`、`platform` 与构件路径，并把资源文件与 wrapper 纳入 `sourceSha256`。
- 判断：本地核对 jarSha256="197ba3803566f8cc8cb4ed3ae0d85b604e0e09055205d0fc57104aca24fd179e"；currentNative=对象(2键)。本条关注的记录粒度确有限，采纳新格式固定必需检查ID集合，保留失败/跳过/实际观测与异常清理结果。原有夹具是assert成功后登记passed，失败抛错不走末尾PASS写出；collector亦核对数量/布尔/JAR，故不能仅凭字面量PASS推定失败仍通过或安全缺陷已存在。
- 证据：docs/testing/M10.5.8-results.json:1441；frontend/tests/history.mjs:18；scripts/collect-m1058-evidence.py:49；scripts/test-open-workbench.py:90；docs/testing/M10.5.8-results.json:1502

## OCR-2457 · 不采纳

- 位置：docs/testing/M10.5.8-results.json:3；OCR原严重度：low
- 计划：无
- 原意见：顶层 `status: "PASS"` 与 `scope` 中“M10.5.9 remains open”并列，但没有独立的范围完整性字段（如 `milestoneComplete: false` / `openItems: [...]`）。触发条件：下游脚本或报表只读取 `status` 时，会把本里程碑判为已闭环并跳过 M10.5.9 的待办，从而高估交付状态。建议显式增加范围完整性标记，或把 `status` 改为区分“本轮验证通过”与“里程碑完成”的两个字段。
- 判断：本地核对 scope="Local authorized content search, independent stdio 1.0, TypeScript/Python SDK and...；status="PASS"。记录已区分本轮选定范围SUCCESS/PASS与releaseComplete/remoteCi/scope/后续里程碑；负例非零退出也可构成验证器成功。不能假设一个忽略其余字段的消费者据此把全PRD或新里程碑授予完成。
- 证据：docs/testing/M10.5.8-results.json:3；.agent:当前接续状态；docs/testing/M8-closure-results.json；docs/testing/M10.5.8-validation.md:3；docs/testing/M10.5.8-results.json:5
