# 文档、历史验收记录、第三方材料核实

日期：2026-10-20（Asia/Shanghai）。本组覆盖 `ocr-verify-docs.json` 分配的 675 条意见，结论见 `verdicts-docs.json`。18 条 accepted、44 条 partial、613 条 rejected、0 条 unverified。accepted/partial 合计 62 条并不代表 62 个独立缺陷；同一夹具跨多个里程碑、两个浏览器的重复意见按 repairGroup 合并。没有修改产品、重跑历史验收或补造历史退出码。

核实采用根 `.agent` 的历史范围说明、M8/M9/M10/M10.5 的验收清单和配套说明，以及实际产生/验证这些记录的脚本。每条意见均保留实际目标路径、行或字段证据。只根据本地事实判断第三方材料；不把原审查的许可证义务推测作为法律结论。

## 纳入总修复计划的阶段建议

### 阶段 A：先修证据门禁真正可能漏拒绝的路径

1. **m9-evidence-legal-gate / P1**，OCR-2019。`docs/testing/tools/verify-m9-evidence.py` 的 JAR 法律资源校验只遍历已存在的 `third-party/` 条目，空集合仍能通过。先明确发行包必需 LICENSE/NOTICE、库存和坐标材料集合，再检查 ZIP/JAR 的资源字节。验收：删除全部第三方条目、只保留部分条目、替换字节各自必须被拒绝；完整固定夹具通过。这里是校验器强度问题，不代表既有发行包实际缺失全部法律资源。
2. **m9-evidence-log-gate / P2**，OCR-2021。可选 job 日志只匹配 `Ran 5 tests` 和 `paidRequests`，含失败摘要仍满足条件。解析确定的测试结果、退出状态和必需测试 ID；日志不可解析保持未知，不能写死 offlineGateTests=5。验收：包含相同子串但 FAILED、少例、缺退出状态均拒绝，成功完整夹具通过。
3. **sdk-typecheck-evidence / P2**，OCR-2451。M10.5.8 collector 仅要求 typecheck 日志为空，空文件不能证明执行成功。新运行以命令、退出状态、stdout/stderr 摘要形成收据；允许成功且无输出。验收：未执行的空文件、非零退出且无输出、旧收据均拒绝，真实 strict/noEmit 成功收据通过。不得给历史空日志补填推测退出码。
4. **m9-evidence-inputs / P2**，OCR-2016。旧 verifier 的“当前来源匹配”只涵盖 Java/POM。明确定义完整构建输入范围，覆盖资源、Wrapper、构建配置及实际验证脚本；统一供候选、验证和校验器使用。验收：分别改一个 Java、资源、Wrapper、前端构建输入时都能发现漂移；文档/运行数据变更按明确范围处理。M10.5.9 主汇总已补大量前端配置，不应重复立项或倒改旧证据。

### 阶段 B：修真实夹具弱断言和示例语义

1. **launcher-saved-config / P2**，OCR-1232。保存配置用例只验证首页 200 和旧 token 401，不能区分保存配置与默认回退。保存配置植入唯一 model/profile 等可观察值，验证真正选中。验收：故意忽略保存配置的实现应失败，正确选择通过。
2. **launcher-stop-evidence / P2**，OCR-1234、1460（1210 为部分采纳的实例元数据补充）。HTTP 超时不是端口已释放证明。记录自有 Java 实例标识，核实退出及端口可重新绑定；保留重复启动前后的旧实例状态。验收：挂起但仍监听的自有子进程被识别，正常退出通过，不终止无关进程。
3. **history-keyboard-assertion / P2**，OCR-1602。`tagName === SELECT` 不能证明键盘可达。执行真实 focus/Tab 并检查 activeElement 和 disabled/tabindex。验收：disabled 或退出 Tab 顺序的 select 反例失败。
4. **example-filter-anchor / P2**，OCR-1177。示例的 `!target/` 匹配任意层级，后续 `target/*` 却只按根相对路径匹配，可能重新暴露嵌套 target。按文档意图锚定根规则，同步示例说明。验收：根 `target/report.txt` 保留，根其它产物与 `module/target/*` 仍按预期过滤。
5. **example-budget / P2**，OCR-1182 部分采纳。示例单次保守上界 2.032768 CNY，接近总预算 2.50。说明预留算法，核对上下文输入上界与演示预算是否合适；不承诺固定预算一定执行 20 步，不以本轮记录重查实时价格或发起付费调用。
6. **example-route-clarity / P3**，OCR-1176 部分采纳。明确 base/primary 是独立路由，改一处不会自动同步另一处；保留配置契约，不按原建议直接删除必填顶层字段。
7. **launcher-token-env / P2**，OCR-1233 部分采纳。当前 PATH 单负例不能替代全部变量边界检查；先核对专用变量命名契约，再增加多类名称反例，不把命令环境白名单与启动器 tokenEnv 当同一机制。

### 阶段 C：当前取证的针对性增强

1. **m9-evidence-transaction / P2**，OCR-2017。验证结束后的逐文件落盘仍可能中断；临时目录完整生成并核对后发布批次索引/记录。验收：中断写入不暴露新旧混合的“完整”批次；保留上次有效批次。
2. **m9-evidence-diagnostics / P3**，OCR-2018、2020。参数解析和缺 job/step/artifact 应返回明确诊断；保留 fail-closed 行为。验收：无参数、非数字、缺少必要 job/step 均可诊断且不写成功收据。
3. **browser-runtime-provenance / P2**，OCR-1488、1489 部分采纳。Firefox 私有准备记录的路径改为缓存内相对标识并核实边界；明确 originalFilesUnchanged 是复制准备时刻的比较，若声称运行后仍未变则再核对官方来源。验收：越界 privateRoot 被拒，复制时点与运行结果分开；Firefox 版本探针仍不能计实际浏览器通过。
4. **sdk-import-inventory / P2**，OCR-1583 部分采纳。清单哈希了示例但只检查核心 SDK imports；让声称属于运行分发范围的示例也接受外部导入检查，测试夹具另列。验收：核心与示例各植入外部导入均被拒，标准库和 node: 模块正常。
5. **evidence-unique-check-ids / P3**，OCR-1400、1426、1483、1572。三条不同路径预览拒绝都叫 `denied preview private.txt`。用稳定 caseId 或夹具相对路径区分；无需把用户绝对路径写入证据。验收：三个 ID 唯一，缺失/重复必要 ID 能被识别。现有数组未因此丢失两例，不应宣称 45 项实际只有 43 项。
6. **launcher-coverage / P2**，OCR-1227、1231、1284、1304、1332 部分采纳。自动化禁用打开浏览器，生产默认说明不应被当作该分支实测；可用可控启动器替身验证打开行为并明确测试范围。
7. 以下为可合并的 P3 可维护性增强，不各自触发历史全量复验：有界且保留顺序的请求轨迹（1437）；MCP 方法关联脱敏参数类别/分页/checkId（1530、1564）；存储断言明确 local/sessionStorage 范围（1541）；原生检查结果用明确语义（1553、2454）；退出状态/负例引用（1721、1729）；真实工具版本而非常量（1608）；结构化 OS 元数据（1765）；明确 projectAttempt 与 GitHub runAttempt 是不同计数（1752）。其余 partial 仅支持各条 reason 写出的部分，不采纳原意见推定的泄漏、付费、重放或假绿结论。

### 阶段 D：接续与第三方材料说明

1. **handoff-current-entry / P2/P3**，OCR-2407、2408。`.agent` 通用命令还硬编码 M8.4，历史章节导航又称“最新 M9”。改为当前阶段入口链接/参数占位，保留带日期的历史事实。验收：照当前入口操作不会把新结果标成 M8.4；M10.5.9/M11 未完成状态保持。
2. **legal-upstream-reconciliation / P3**，OCR-2394、2396、2398、2399、2400、2403、2415 部分采纳。记录原始第三方材料中不同版权年份、fast_float 提交引用、JNA SPDX 与正文表达差异、`utf8.c and utf8.c` 上游文字重复。核实实际所用构件及上游修订后再同步；不猜年份/文件名，不直接手改受 sources.json/hash 约束的许可证。它们不是本轮已确认的分发违法结论。
3. **legal-distribution-paths / P3**，OCR-2405 部分采纳。NOTICE 标清源码路径与 JAR/ZIP 中的对应路径，保留现有 ui/ui-license.txt 和坐标法律库存，不把文件路径相对层级不同等同版权材料缺失。

## 代表性误报与不纳入修复的建议

- 相同日志/截图摘要不代表未执行。夹具日志通常只打印确定性的 PASS 名称；浏览器现行同源自动进入，connection/welcome 两次可已经同一画面。禁止强制所有截图/日志摘要互异（1195、1242、1482、1571、2452 等）。
- 不能用新增的 runtime.mjs、transportDiagnostics、检索类或 UI 文件反查旧阶段并判旧记录缺字段/漏测；旧记录按当时源码和契约取证。当前 M10.5.9 总汇也不应强行复制到每份历史子收据。
- PASS 是夹具中真实 assert 全部通过后才写出的字面量，布尔型收据不是“失败依然输出 PASS”的证据；对确有具体弱断言/门禁漏检的项目单独采纳，拒绝为所有 675 条机械加 schema/签名。
- 忽略 `.build-cache/`、`.dsh-tmp/` 和日志符合项目规则。缺原始缓存属于明确的历史留存边界，不能要求把运行数据提交，也不伪造“复跑后必有相同日志字节”。
- settings 的 `fixtureCredentialsCleaned` 在 Cleanup 返回 0 后才写；Cleanup 确实逐 target 回读为空，否则抛错。不能只看末尾 true 就认定没有残留检查（1273、1419、1486、1587）。
- `transportDiagnostics` 是受控停机/断网的允许诊断，不是 provider 网络轨迹。空数组与有模型调用不矛盾，不能因此降级 SKIP（1511、1535、1569、1591）。
- OSV 已逐查询翻页并取详情/排除 withdrawn；零发现时 records={} 正常。annotations 2.21 与 databind/core 2.21.7 不证明版本被截断；扫描范围明确不覆盖整机 OS/JDK。
- 重复 10 次取消竞态测试是有意执行，计执行次数 10 正确；runner capturedAt 早于离线 verifiedAt 正常；失败尝试比成功制品小、GitHub artifact ID 不按显示时间排序不构成证据造假。
- 根 NOTICE 存在且有项目版权；Apache 附录模板无需改写。JNA 说明完整文本位于可下载 source package，不要求本地同名 LGPL2.1/AL2.0 文件；分发选择 Apache-2.0，完整根 LICENSE 随包。ColorBrewer 和 Microsoft 段也已保留版权/许可，不能从单段位置推断全部缺失。

## 部分裁决中的外部未证子主张

**OCR-1821 / partial，legal-source-mapping / P3**：本地 sources.json 的 wrapper 只单独列 NOTICE、annotations 只列 LICENSE 是事实，但 Wrapper Apache 正文已有根 LICENSE，不能直接认定缺许可。只采纳补充 Wrapper 官方许可证来源及共用根 LICENSE 的映射说明。尚不能从本地证明 JetBrains java-annotations 24.0.1 官方源码分发是否另有必需 NOTICE；不能把“未采集”说成“上游不存在”。

不把 annotations 的未证子主张列为必须补文件的修复。若要进一步核实，只需查看该固定 tag 的完整官方文件树及原 artifact/source JAR 中 NOTICE/COPYING 资源，并对比 prepare-legal.py 的自动保留清单。若有遗漏，添加附加材料/库存映射并核对分发；若上游无该文件，记录依据后拒绝该部分。2026-10-20 通过官方 GitHub/raw 页面读取固定 tag/NOTICE 遇工具 Cache miss，不能把读取失败当 404 或文件不存在。已读取的 [Maven Wrapper 固定版本 LICENSE](https://raw.githubusercontent.com/apache/maven-wrapper/maven-wrapper-3.3.4/LICENSE) 确为 Apache-2.0，并不足以单独证明 annotations 一侧。

## 本组验证

检查 675 个稳定 ID 全覆盖、无重复、状态枚举/repairGroup/priority 合法，accepted/partial 有具体计划组，rejected/unverified 无伪完成修复组。逐项证据目标均在本地存在；引用的历史日志/截图只作为原记录中的对象，不宣称已重新取得全部历史字节。未运行产品测试；根接续更新、主计划整合与最终 Git 提交由总控负责。
