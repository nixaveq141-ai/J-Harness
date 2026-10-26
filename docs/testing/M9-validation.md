# M9 发布验证记录

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](../development/current-state.md)及[全阶段审计](M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

2026-10-21 历史入口说明：本页是当时验收快照。页内历史证据收集器/核验器现已冻结，CLI、导入及 runpy 均拒绝执行；命令仅供追溯。复现需使用记录中的原始版本与隔离副本，不得覆写历史 JSON。当前 M10.5.9 工具只收集新阶段证据，不能替代本页阶段证明。

日期：2026-10-16。状态：**M9约定范围验收完成**。同一源码提交`ed02da0e01125d94a3c07f8862afe6c63b8f62d2`在真实Windows/Linux/macOS CI中全部通过。正式Release未创建；本阶段完成的是可追溯候选与发布兼容验证。契约见[M9-contracts.md](../development/M9-contracts.md)，平台及AC/NFR适用范围见[M9-platform-matrix.md](M9-platform-matrix.md)。

[最终CI运行](https://github.com/nixaveq141-ai/J-Harness/actions/runs/38858529628)，完整结构化收口结果[M9-ci-results.json](M9-ci-results.json)。最终文档提交只归档证据，源码及候选提交标识保持上述值。

| M9要求 | 最终结果 | 实际证据 |
|---|---|---|
| 三平台workflow、启动器、Java21、原生PTY与Wrapper | 已核对并实际运行 | 三侧各305项，失败/错误/跳过均0；完整测试名与260个Java/POM SHA逐项一致；实际OS/CPU见平台矩阵 |
| 项目/第三方许可、NOTICE与依赖清单 | 已整理并核对最终JAR保留原文 | 根Apache-2.0采用远端原文；18个外部依赖（10 runtime/8 test）及JAR/POM SHA见third-party/inventory.json；原生构建来源限制如下 |
| SBOM与依赖扫描 | 三侧均通过 | 最终模块打包后生成CycloneDX 1.6；每侧18个精确Maven依赖OSV查询PASS、未撤回发现0；完整扫描与SBOM分别归档 |
| 远端/候选追溯 | 已完成 | 私有origin、保留初始LICENSE历史、不强推；codex/m9-release-validation推送并实际触发CI；每侧run/job/artifact链接及JAR/ZIP SHA已核对 |
| 候选安装/doctor/run/审批/取消/恢复/报告 | 三侧实际ZIP均通过 | 带空格路径安装、真实分发启动器、doctor零网络、审批写入与报告、EOF拒绝、独立CLI取消、UNKNOWN拒绝重放、显式verify-request后恢复读取；每侧7次loopback请求、付费请求0 |
| 本地完整复验与重复打包 | 已通过 | 同一最终源码Windows本机305项零失败/错误/跳过，见M9-local-results.json；强制重建JAR SHA前后相同，见M9-packaging-results.json |
| 发布门禁/证据核验 | 已通过 | 每侧5项扫描器离线正反例；原始归档/日志/XML/候选逐文件哈希核对；9组失败运行、缺平台、错提交/摘要、跳过门禁、漏洞/候选篡改、禁用断言负例均拒绝 |
| 文档与退出记录 | 已同步 | .agent、README、契约、验收、平台矩阵、推进计划、NFR顺序、上游范围接续及本收口记录 |

305项包含原取消事件回归的10次重复执行（原296项增加9次），不把重复次数说成新增9个功能。三平台的Java/POM与测试集合相同，JAR/ZIP按实际平台分别记录，不宣称所有OS产物字节相同。

## 最终证据

- M9-ci-run.json、M9-ci-jobs.json、M9-ci-artifacts.json：官方API运行/任务/归档元数据。
- M9-hosted-windows/linux/macos-results.json：每侧完整测试名、源码/报告/日志/JAR SHA及Java/OS信息。
- M9-hosted-各平台-sbom/security/smoke/manifest.json：每侧原始SBOM、扫描、实际安装流程和候选文件哈希。
- M9-ci-results.json：外层归档摘要、内层候选/文件/JAR、法律文件、源码与测试集合的交叉核对结果。
- M9-evidence-audit-results.json：离线核验工具的正例与9组篡改/运行模式拒绝证据。
- M9-local-results.json、M9-packaging-results.json：最终源码本机305项及强制重复JAR归档；pre-hosted文件/Git历史保留旧本地快照。

通用M9-sbom/security-results/candidate-smoke/candidate-manifest.json指向最终Windows CI候选。其他平台以独立hosted文件为准。二进制不入Git，原始归档在对应Actions artifact中；本机另保存在.build-cache/M9-hosted-final，清理缓存后可凭run/artifact链接重新下载。

## 实际失败、修复与复验

前四轮完整保留M9-ci-first/second/third/fourth-attempt.json及原始日志SHA，未拼接不同提交的通过项。最终仅使用本次同提交全绿运行。

1. macOS及hosted Windows临时目录含路径别名；直接调用严格路径API的测试夹具先toRealPath，LocalRunControl/SessionSearchIndex链接保护不变，并增加POSIX symlink/Windows junction拒绝断言。
2. 英文Windows的Java21启动器通过CP_ACP处理argv，中文在main之前变为??。参数边界测试使用临时.NET Unicode原生接收器，明确UTF-8输出配置；中文/空格/&/;、双流及退出码断言保留，产品ARGV拒绝Shell的检查不变。Java启动器来源见[OpenJDK21代码](https://github.com/openjdk/jdk21u/blob/master/src/java.base/windows/native/libjli/cmdtoargs.c)。
3. Linux暴露真实Subagent竞态：子任务先响应共享取消token并结束，父线程取得完成结果时漏记取消事实。父线程在等待前/得到结果后用一次CAS记录已观察请求；重复原回归10次。保留UNKNOWN、关闭一次和不重放语义，API/SDK1.9、JSONL/schema1不变。
4. hosted Windows真实Maven启动实测约27秒，前置缓存核验不能保证每次启动低于20秒。仅真实Maven修复场景采用已有产品默认2分钟命令上限及5分钟有界总预算；短进程20秒、750ms超时、1秒预算、取消及所有编译失败/修复/真实重建断言保留。产品默认限额未修改。
5. 首次OSV发现Jackson2.21.4的11条未撤回公告，原始M9-security-before.json保留；升级2.21.7后最终三侧18依赖复扫通过，无忽略规则或未评估豁免。

## 复现与边界

完整CI命令和顺序见.github/workflows/ci.yml：clean verify→最终SBOM→5项离线门禁→完整测试证据→法律资源漂移核对→OSV→候选打包→真实安装冒烟→归档。POSIX使用sh ./mvnw。构建源码需要JDK21；二进制运行不要求目标工作区具有Maven，doctor仍显示构建能力诊断。

离线重新核对：下载三侧原始artifact到.build-cache/M9-hosted-final/windows.zip、linux.zip、macos.zip，在当前无Java/POM变化的checkout执行：

```powershell
python docs/testing/tools/verify-m9-evidence.py 38858529628
```

工具禁止Python -O；可选原始job日志存在时也核对5项离线门禁与OS元数据。源码改变、缺失/失败任务、跳过候选门禁、摘要不符、不完整/失败扫描、错候选提交或法律文件遗漏均拒绝。

Windows10、独立WSL、macOS Intel/Linux其他架构未新增认证；完整范围见平台矩阵。pty4j内嵌Microsoft程序的精确构建来源仍无法从上游证明，补充许可来源不冒称对应二进制的精确修订。扫描覆盖Maven项目依赖含tests，不覆盖所有构建插件传递依赖、JDK/OS或未独立建模的原生程序。以上限制已评估并记录；本次不宣称完整供应链认证或PRD全部实现，没有新付费模型评测、main合并或正式Release发布。

最终Windows CI原始候选ZIP在本机Windows11也完成实际安装/doctor/审批/取消/显式恢复/报告，7次loopback请求、0付费请求；ZIP SHA与同一CI Windows制品完全相同，见M9-local-candidate-smoke.json。
