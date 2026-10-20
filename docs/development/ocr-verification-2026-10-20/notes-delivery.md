# Delivery 分支核实与修复阶段建议

日期：2026-10-20。范围：`.build-cache/ocr-verify-delivery.json` 的 624 条原意见。本文是修复计划输入，不是实现或验收通过记录。

主裁决文件逐项包含原始稳定 ID、具体判断、当前源码证据、修复组和优先级。本分支直接核对 544 条，另 80 条明确交叉分配给文档组完成，并已由总控按 ID 合并回主文件。最终 624 条：accepted 84、partial 372、rejected 168，0 条 unverified。`verdicts-delivery-extra.json` 保留该 80 条交叉核实记录，验证器要求它与主文件对应裁决完全一致，不允许冲突覆盖。

已读 `.agent`、M10.5.9/M10.5.8/M10.5.4 等契约，对照前端组件及调用者、Java 服务端配置/事件生产者、SDK、夹具、构建/采集器/工作流。537 条已由原意见 `existing_code` 反查当前源码实际行号。此次是源码及调用链核实，没有修改产品源码，没有重跑产品测试，也没有启动付费模型或远端 CI。下面的验收均为将来实施的要求。

## 判断规则与边界

- accepted 表示意见的主要缺口有源码支持；partial 只采纳 reason 中明确支持的部分，原意见未被支持的攻击结论、兼容范围及修改代码不能照抄。
- 相同缺口跨十余份旧 collector 出现，保留逐意见追踪，但实施优先提取共享门禁。历史计数/原 JSON/日志/摘要不重算、不重新标记通过，也不将新门禁不完整反推历史实际失败。
- 上游组件/CSS 是 `.agent` 明确要求保留的来源字节。当前可达的可用性修复优先放适配层；确需改移植文件，应同步来源变更记录/摘要。未启用的 Desktop 平台属性分支留待 M11 建契约，不能混入当前 Web 交付。
- 本机信任的 SDK/验收宿主继承调用者环境，与模型工具执行不可信命令时的环境白名单不是同一边界。没有实际外发或落盘证据，不将继承环境本身标成密钥泄漏。
- 不新增 P0。P1 是用户草稿/密钥意图、异步事实显示、受理对账及自有进程回收问题；不能沿 OCR 的 high/critical 直接排期。

## 阶段 1：保护用户编辑、受理对账和自有 SDK 进程（P1）

先修设置失败提前清空新密钥（OCR-2349）、异步旧轮询覆盖动作结果（OCR-2322/2323/2333）、旧连接响应清除受理 marker（OCR-2325）、确定拒绝之外保留消息受理 marker（OCR-2331）、回答后页核对与 storage 写入失败处理（OCR-2302/2303/2304），以及 TypeScript 并发 close 的无界裸 exit Promise（OCR-2241）。事件 4000 条窗口（OCR-2251/2248）先明确显示与历史读取语义，不直接把 slice 改为另一端而破坏分页。

修复组：`ui-settings-secret-draft`、`ui-async-state`、`ui-admission-reconcile`、`ui-answer-reconcile`、`ui-event-window`、`sdk-lifecycle`。

验收：延迟旧 GET，先完成 cancel/approval/submit，再释放旧响应，状态不得回退；延迟旧 Api 写请求后重连，已受理 ID 仍可核对；保存密钥模拟冲突/超时/密钥库不可用，不能第二次无密钥却误提示新密钥已保存；回答位于第 2 页时按精确 questionId 核对，storage 抛错后禁止丢失护栏；挂住自有子进程并并发调用 close，所有调用有界返回/失败，TERM 后升级 KILL 并回收。仍保持 UNKNOWN 不重放、回答不授予工具权限、只能终止本实例自有进程。

## 阶段 2：先使验收门禁能识别错误证据（P2）

当前 M10.5.9 门禁优先于旧分阶段脚本：run/fixture/命令退出码/完整 case 集合与输入、日志、JAR、UI、BOM 同身份；检查集合非空、唯一、准确名称/数量与严格布尔；三平台指纹先验证非空及格式，再比较一致。原生历史身份要以同一个真实运行内完整实现集合和 probe 脚本匹配，不能逐文件 any 拼接。矩阵 finalizer 不能把未读取的成本字段常量写零，也不能在新失败后留旧 PASS 可被下一采集消费。

修复组：`evidence-run-binding`、`evidence-check-schema`、`evidence-native-lineage`、`evidence-ci-origin`、`evidence-atomic-archive`、`evidence-ui-identity`、`evidence-path-boundary`、`evidence-portable-paths`、`evidence-byte-preservation`、`evidence-runtime`、`evidence-gate-tests`。

实施方式：共享验证器 + 明确 schema 版本 + 同一份 bytes 解析/摘要/归档；先完成全部验证，再 staging，最后原子发布 manifest。输入路径按角色限根（截图、源码、包内资源）且拒不可信绝对/上跳/链接；显式命令行输入允许范围按命令用途定义。旧 settings/SDK 的字符串 checks 是既有格式，迁移不能直接把历史合法记录作废。新 schema 应拒绝任意 `FAIL` 字符串和 `passed: "false"`。

验收反例：空 commit/source/UI map、错 case 集、重复 name、空截图、混合两个运行、失去先前失败的旧 PASS、不同字节读取竞争、越界/软链截图、错误状态但 cleaned=true、缺命令却空 typecheck 日志、报告写到一半中断。每个反例均非零失败并形成当前失败回执，不能更新正式 PASS。三平台同一个 ciRunId 是正确做法；独立来源通过 job/artifact 身份校验，不要求 ciRunId 互异。

## 阶段 3：修复测试真正漏断言及资源生命周期（P2）

优先补能让门禁误通过的具体断言：开发流程 JSON.stringify 换行负向检查（OCR-1861）、断连 MCP 却只数原 peer 请求（OCR-1842）、SDK stderr 调用丢返回值（OCR-2217/2237）、续轮只等旧 FINISHED 的竞态（OCR-2218）、启动器将 HTTPError 当断开（OCR-2206）、正常/取消/UNKNOWN 场景的准确终态。其他 helper 的前置/清理、provider 异常、日志预算合并为共享 fixture 生命周期。

修复组：`test-assertion-strength`、`test-redaction-coverage`、`test-a11y`、`test-process-lifecycle`、`test-pagination-budget`、`test-output-budget`、`test-fixture-protocol`、`browser-diagnostic-filter`。

验收：对目标实现做最小负向扰动，确保断言明确失败，例如返回错误审批码、丢请求 body 关联、产物挂错事件、页面不渲染 TEST_PASS、第二轮未开始、错误 stderr 进入结果、停止后仍返回 401。日志脱敏覆盖合成模型 key、Web token、动态 browser token、每次重启输出和事实日志。不能要求用户输入的原文在发往本机草稿 POST 前已脱敏；应核对宿主草稿存储和发往模型的内容。进程/流 error、close、超时和 cleanup 失败不能盖住原始失败，不能强行 kill 后仍记录正常 stopped/cleaned。

避免重复测试：`questions.mjs` 已在释放延迟请求后刷新并验证第二次草稿；`history.mjs` 已在双击后断页码 2；不能截取局部 check 便认整体无覆盖。公开随机/固定合成测试值不是生产密钥。

## 阶段 4：构建、扫描与候选分发完整性（P2，部分 P3）

明确 zip/tar 摘要分支（OCR-2422），绝不采用跳过校验；检查 Wrapper 缓存完成状态/入口及私库认证范围/URL脱敏。包 UI 清单从真实输出精确枚举、基线清单完整且唯一；许可证闭包与已分发依赖一致。SDK TS 库存要识别副作用静态 import（OCR-2140），Python/示例执行源和动态导入限制明确。秘密扫描应在读取前/过程中限额，拒非普通文件并对失败使旧结果失效（OCR-2143/2144）。候选 ZIP 显式 Unix create_system 与 launcher 权限、唯一 BOM 坐标、精确文件集和完整构建身份；私有 Firefox 副本必须完整源/目标清单及失败回执，不假称源从未变。

修复组：`ui-package-manifest`、`supplychain-provenance`、`candidate-source-binding`、`candidate-path-boundary`、`candidate-manifest`、`sdk-inventory-gate`、`source-secret-gate`、`source-secret-ignore`、`scanner-input-schema`、`scanner-response-budget`、`scanner-diagnostics`、`legal-input-boundary`、`legal-provenance`、`browser-runtime-boundary`、`browser-probe-diagnostics`、`native-probe-diagnostics`、`wrapper-cache-integrity`、`wrapper-download-integrity`、`wrapper-diagnostics`、`build-runtime-compat`、`build-plugin-pinning`。

验收：缺 unzip、有/无缓存、缓存仅目录、并发下载、错误私库挑战、输出额外资产/空基线、重复 BOM 坐标、Windows 构建包在 POSIX 解压执行、副作用 import 非标准包、-O 扫描器校验、超大文件/坏链接/已删除 tracked 文件、错误 runtime status、复制中途改变源。当前依赖/BOM扫描仍须真实重新运行，不将静态检查结论充当网络扫描通过。

## 阶段 5：工作台可用性与平台入口（P2/P3）

合并模型/设置/技能/工具清单旧请求代次，保留未保存名称、双向输入折叠、精确错误提示、阅读/下载提示收尾、合法文件引用行号和简单 Markdown 顺序、结果分页一致、全局队首按钮、注册新工作区的精确身份。端点跨 origin 保留密钥需清楚提示/显式意图，不静默清空新 key；非 loopback HTTP 提示风险，保持合法代理迁移能力。修键盘/forced-colors/禁用/长文本溢出时先用当前支持视口和真实 DOM 验证，避免改未启用上游 Desktop 分支。

修复组：`ui-draft-safety`、`ui-content-reading`、`ui-error-copy`、`ui-markdown`、`ui-search-consistency`、`ui-extension-form`、`ui-primitives`、`ui-a11y-theme`、`ui-skills`、`ui-workspace-register`、`ui-turn-queue`、`ui-polling`、`ui-api-cancellation`、`ui-api-input-validation`、`ui-diagnostics`、`ui-settings-endpoint-intent`、`sdk-examples`、`sdk-response-correlation`。

平台入口修复组：`launcher-readiness`、`launcher-lifecycle`、`launcher-config-boundary`、`launcher-diagnostics`、`launcher-line-endings`、`ci-native-lifecycle`、`ci-native-readiness`、`ci-resource-control`。启动器就绪探测应重试同一候选地址，持续排空输出并传播 host 非零退出；自定义 tokenEnv 不得覆盖关键宿主变量。macOS测试结束恢复原 keychain 状态，Linux明确区分缺服务与已启动后锁定，并验证总线/锁状态就绪。CI 漏洞发现阻止候选生成是正确设计，保留 always 上传原始失败证据。

验收：相同现代浏览器矩阵、窄屏/缩放/长标签/键盘焦点/高对比、离线失败恢复、模式切换目标重置、正文来源及摘要、只读对账不创建任务。修改完整验收工具后再做 M10.5.9 同源码/JAR/UI 三平台三引擎验证；真实 Firefox 或其他环境不可用不计通过，M11 仍由总门禁决定。

## 代表性误报与禁止照抄的建议

- OCR-2224：turnId 经 identifier 校验不能含冒号，给出的碰撞例不可达。
- OCR-2306：App 对 Questions 以 taskId 做 key，切会话会重建状态。
- OCR-2332：清除已查到 UNKNOWN 的受理 marker 不解除 `uncertain(task)` 与后端未决执行护栏。
- OCR-2254：服务端模型 ID 本来上限 32，通用 identifier 128 不构成合法模型 ID 128。
- OCR-2269：GOAL_UNVERIFIED 的 CompletionDecision.achieved=false 会产生 FAILED，非 COMPLETED。
- OCR-2282：诊断 category 来自 TaskDiagnosis 封闭映射，不透传 `__proto__`。
- OCR-1996：suites.cases 是字符串名称列表，不是 dict 排序错误。
- OCR-1997：矩阵三平台同 ciRunId 合理，不能强制三个不同 run。
- OCR-2162/2164：环境由 workflow 建立，严格不可用 schema 用于避免将真实读写故障当预期不可用。
- OCR-1808/1809：源码 SDK 分发与 private:true 有明确契约，不强制升级 npm 发布包。
- OCR-2422：tar 摘要不匹配应保留完整性门禁，不允许“跳过并告警”。
- OCR-2376/2377/2097：审计失败应停止候选，Firefox 不可用例外不得无由扩大。
- OCR-2246：引用路径以 base64url 传输，百分号字面路径不等于 URL 二次解码越界。
- OCR-2272/2281：当前 Chrome/WebKit 下载已实际通过，游离 a.click 不能凭旧引擎通则认定必失败。

## 接续要求

先合并额外 80 条裁决，再由总控跨模块合并重复任务和确定依赖顺序。任何阶段只有实现、相关反例和适用回归真实通过后才能标完成；本计划不授权推送、远端 CI 或发布，也不表示现有 M10.5.9 已收口。
