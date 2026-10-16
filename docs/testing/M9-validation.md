# M9 发布验证记录

日期：2026-10-16。状态：本地发布准备和Windows候选验证已通过；真实Windows/Linux/macOS CI已执行首轮，修复夹具差异后复验中，M9整体尚未验收，未正式发布。契约见[M9契约](../development/M9-contracts.md)。

| 项目 | 当前状态 | 证据与边界 |
|---|---|---|
| 远端连接 | 已通过 | origin为私有仓库nixaveq141-ai/J-Harness，默认main，远端初始提交f90bb77，仅LICENSE；已认证fetch |
| 项目许可 | 已整合 | 根LICENSE采用origin/main原文，Apache-2.0；自有代码与第三方条款独立 |
| 第三方许可/NOTICE | 本地核对通过，原生来源限制保留 | third-party/inventory.json列出18个外部依赖（10 runtime、8 test）与原始JAR/POM SHA；原始法律文件按坐标保留并逐字进入最终JAR |
| Java21全量 | Windows通过296项，0失败/错误/跳过 | .build-cache/M9-verify-final.log；新增doctor无Maven工作区回归，完整测试名/源码/制品SHA见M9-local-results.json |
| SBOM/安全扫描 | 18个精确依赖复扫PASS，未撤回发现0 | M9-sbom.json、M9-security-results.json；仅代表所存查询时间和声明的依赖覆盖范围 |
| 重复打包 | JAR字节一致 | 强制maven.jar.forceCreation重新生成归档后，SHA256均为24334b12a36597722f73a299b02f1a6acfadbd76e8b9e761a3e97805ce47b76c；不是跨OS/完整工具链可复现证明 |
| 扫描器离线门禁 | 5项通过 | 分页仅重查未完成项、重复token拒绝、截断结果拒绝、超时拒绝、无发现通过；M9-release-tools-results.json |
| 候选安装/流程 | Windows通过 | 实际ZIP解压带空格路径，调用分发.cmd；doctor不联网、审批写入与报告、EOF拒绝、独立CLI取消、UNKNOWN禁止续跑、显式verify-request后恢复读取；M9-candidate-smoke.json |
| Wrapper | 下载完整性已核对并固定 | 官方ZIP SHA512核对后固定SHA256=4ec3f26fb1a692473aea0235c300bd20f0f9fe741947c82c1234cefd76ac3a3c；Maven缓存命中不冒充重新下载验证 |
| 三平台workflow | 门禁与归档已实现，尚未远端运行 | 同一提交clean verify→最终SBOM→法律资源漂移核对→OSV→候选打包/安装冒烟；保存runner/Java、完整测试/源码/JAR/ZIP SHA及run URL |
| 真实三平台CI | 待执行 | 本地分支codex/m9-release-validation；按AGENTS授权边界，需用户授权推送该分支。无远端通过声明 |

## 实际发现与修复

首次OSV查询在Jackson core/databind 2.21.4中报告11条未撤回公告，原始结果完整保存为[M9-security-before.json](M9-security-before.json)。核对官方修复区间及Maven Central后，将三个直接声明升级至2.21.7；全量及18个坐标复扫通过。没有用忽略规则或未评估豁免绕过扫描。

二进制安装不应依赖目标工作区的Maven：doctor仍显示Maven/Wrapper诊断，但运行就绪改由Java/JAVA_HOME、key、模型配置、Git与插件装配决定。新增回归明确没有Wrapper且PATH内无Maven时仍可就绪；doctor不发送模型请求。

第三方法律文件保留原始字节，.gitattributes禁止其自动行尾转换；Jackson内嵌数字解析器、JNA双许可、SLF4J、Kotlin、annotations、pty4j与原生组件补充通知分别保留。JNA选择Apache-2.0分支。pty4j 0.13.12源码及源JAR链接已提供，内嵌Microsoft程序的精确源码修订仍无法从其上游证明；补充terminal通知的固定来源不冒称对应二进制的精确构建版本。这是发布限制，不能据此宣称完整供应链认证。

## 复现

```powershell
.\mvnw.cmd -B -Prelease-audit clean verify org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom *> verify.log
python scripts/test-release-tools.py
python scripts/collect-release-evidence.py verify.log
python scripts/prepare-legal.py
git diff --exit-code -- third-party
python scripts/scan-osv.py target/bom.json target/osv-results.json
python scripts/build-candidate.py
python scripts/smoke-candidate.py
```

POSIX以`sh ./mvnw`替换Wrapper调用。最终SBOM在全部模块打包后再次生成，避免引用上次构建的内部JAR；profile在root的早期聚合仅作准备。候选目录为target/jharness-0.1.0-SNAPSHOT-candidate.zip，附candidate-sha256.txt；manifest区分提交、工作树状态与实际输入SHA，不把脏快照当成提交精确产物。清理缓存会删除这些本地产物，正式远端结果以同一CI run归档为准。

## 失败记录与剩余边界

首次扫描发现如上；旧中间构建不冒称最终快照。Python离线夹具首次遇沙箱临时目录权限拒绝，改在仓库target中创建自有临时目录，标准环境重验通过。候选夹具两次失败分别为未跳过JSONL格式头、把已EXITED历史控制文件算作活跃控制；均按现有持久协议修正，原产品断言未削弱，失败JSON保留.build-cache。第二次夹具异常清理曾短暂留下自有Java子进程，已核实退出；夹具改为先释放等待并等待退出，超时才终止自有进程树。

没有付费模型请求、Release发布或新增Linux/macOS通过声明；M8原Windows/Linux各295项是历史证据，Java/POM已变更，不再作为M9当前源码证明。OSV范围为Maven项目依赖含tests，不涵盖所有构建插件传递依赖、JDK/OS、未独立建模的原生程序；无发现不是不存在漏洞的保证。仍需真实三平台CI和结果归档后才能决定M9收口。

重复ZIP生成及JAR重打包的实际哈希见M9-packaging-results.json；候选文件清单/源码哈希与工作树状态见M9-candidate-manifest.json。最终候选绑定已提交源码ebcf7dd，workingTreeDirty=false；5890cb3为实现提交，ebcf7dd仅合并远端许可证历史，无源码内容变化。

最终候选ZIP SHA256：11e55a44fbe9b5934579acfdfdd30d515c6972a5db56c8dd9307b5132bbec495。对应manifest/安装冒烟均已归档；本轮未推送、未运行远端CI。后续文档证据提交不改变已验证候选的源码提交标识。

2026-10-16 M9远端执行授权：用户明确要求进入下一步并把M9全部运行完成，已授权推送codex/m9-release-validation及执行真实三平台CI、修复实际失败、归档结果；不含合并远端main或发布Release。当前开始远端执行，尚无CI通过结论。

2026-10-16 M9真实CI进展：用户已授权，验证分支已推送。提交cbdbdcd6be0cf3feb1ce6864b40d26908cd601ad对应实际运行https://github.com/nixaveq141-ai/J-Harness/actions/runs/38854955517，当前进行中，尚未记为通过。Git直连两次超时后，仅本次推送使用现有系统代理成功；未修改全局网络配置。

M9真实CI首次失败与修复中：run38854955517的macOS ARM64在LocalRunControlTest/RunStatePermissionsTest遭遇系统临时目录路径别名，产品拒绝链接保护正常。仅将三处测试夹具临时根toRealPath规范化，保留产品保护；在现有权限测试中补充POSIX symlink/Windows junction拒绝断言，不新增跳过、不减弱原断言。原始日志保留.build-cache/M9-job-116824235442.log，待本地验证后重跑三平台。

首轮Windows runner另发现同类临时路径别名，以及中文参数回显依赖本机native.encoding而在英文Windows代码页丢失字符。进程测试子程序明确UTF-8输出，读取测试显式配置ProcessLimits UTF-8（公共能力已有）；中文和特殊字符原断言完整保留，产品默认native编码未修改。Ubuntu首轮已全流程通过，但不冒充最终同提交三平台通过。

2026-10-16 M9首轮归档与复验：run38854955517的Ubuntu296项及扫描/候选安装通过；Windows/macOS夹具路径和编码失败详情已保留M9-ci-first-attempt.json。修复仅影响测试，新增链接拒绝断言，产品检查和中文断言未减弱；修复后Windows本地全量296项零失败/错误/跳过。当前提交修复后重新执行同一提交三平台，不将首轮Linux结果拼接成最终三平台通过。

M9第二轮macOS已通过进程/取消控制测试，进一步发现M7ClosureTest直接创建搜索索引时也使用系统临时目录别名。已系统搜索严格路径API调用，补齐该夹具根规范化；SessionSearchIndex拒绝链接及原有搜索/增量/事实不变断言保持。run38855644393失败日志保留.build-cache/M9-job-116826373036.log，继续相关专项和同提交三平台复验。

M9第二轮进一步定位：Windows JUnit原始XML确认子Java启动器已把中文argv转成??（Cp1252），不是输出解码；按OpenJDK21 CP_ACP实现，Windows参数边界测试改用支持Unicode的PowerShell -File原生接收器，中文/空格/&/;及退出码/双流断言不变。Linux暴露真实Subagent取消事件竞争：子任务先观察共享token结束，父线程直接取完成结果时漏记cancel_requested。现于父线程等待前/结果取得后记录一次已观察的取消请求，保留单次CAS、UNKNOWN和不重放语义；原失败回归重复10次，预计全量305项，尚待验证。

M9复验修复就绪：进程/搜索/子任务相关38项通过（取消事实原回归重复10次），原断言全部保留。Windows Unicode argv接收器最终采用临时编译的.NET原生exe；直接Shell接收器按产品ARGV防护被拒绝的本地失败日志保留。Java21 CP_ACP限制不冒称被产品修复，产品Shell保护未改。父线程取消事实竞争已修复；准备第三轮同提交全量，预计305项。第二轮失败与原始日志SHA见M9-ci-second-attempt.json。

M9第三轮：Linux/macOS305项及扫描、安装、恢复全流程通过，Windows已通过中文及取消回归，剩余实际Wrapper失败→修复→重建夹具在首个20秒命令期限内零输出超时。新增Windows离线前置准备：相同最小环境运行真实Wrapper --version，确认已有缓存及固定Maven3.9.9，单独完成PowerShell/.NET冷启动；代理内20秒命令/45秒总预算和失败→修复→真实重建断言均保持。第三轮失败日志保留.build-cache/M9-job-116829194364.log，继续同提交三平台复验，不拼接通过结果。

M9第四轮准备：Windows真实Wrapper前置缓存/版本/原生启动核验及原编译失败→修复→重建流程的9项专项通过，20秒/45秒原限额未改。前置日志按Windows实际UTF-16/本机编码识别，开发中的两次日志解码失败保留.build-cache/M9-wrapper-prep*-targeted.log。第三轮运行与失败SHA已归档M9-ci-third-attempt.json；继续同一新提交三平台完整复验。
