# M9 发布与兼容验证契约

日期：2026-10-16。状态：本地准备和Windows候选验证已通过，真实三平台CI已获授权并执行，正在修复首轮差异后复验。用户已提供远端 https://github.com/nixaveq141-ai/J-Harness ，仓库Apache-2.0许可证已按原文采用；保留远端原有历史/许可。本地M8已收口，历史证据保持，不将其直接改标M9或CI成功。

## 范围与门禁

M9只处理发布准备、供应链与平台验证，不提前实现M10前端/M11 Desktop。API/SDK仍1.9、JSONL/schema仍1。发布候选暂保留0.1.0-SNAPSHOT坐标；没有真实CI、完整许可材料、候选制品与冒烟证据时不声明正式发布完成，不创建公开Release或自动付费模型评测。

1. 审计三平台workflow、启动器、Java21、离线Wrapper及PTY原生资源；支持声明分Windows11实测、Linux容器实测、Windows10/macOS/独立WSL未实测，真实CI通过后另记录runner实际系统及架构。
2. 根项目自有代码许可采用用户远端的Apache-2.0正文；第三方依赖、原生库及Wrapper许可独立保留，不把项目许可覆盖到第三方。记录依赖坐标、scope、来源JAR/POM哈希及license/NOTICE；不凭POM名字宣称整个分发物合规。
3. 生成固定版本CycloneDX聚合SBOM，区分生产依赖与测试/构建依赖；安全扫描通过OSV官方API检查完整版本坐标，保存查询时刻/输入哈希/发现/失败边界。查询失败、分页未完成或未评估发现不得算清洁结果。网络扫描独立于默认离线测试；CI中作为显式门禁。
4. 远端接入先fetch和检查祖先关系，保留已有LICENSE及提交，不强推覆盖。候选通过独立codex/发布验证分支进入CI，记录实际commit/run URL、完整测试/源码/制品SHA。发布权限仍限任务所需的仓库验证，不自动发布Release。
5. 候选分发物包含运行所需JAR、启动方式、配置说明、第三方法律材料和SBOM；校验文件清单与哈希，候选安装/doctor/run/审批拒绝/取消/恢复/报告用本机模型替身完成。

## 证据状态

静态配置存在不等于CI执行。最终输出M9-validation.md及结构化结果，逐项区分已通过、待验证、外部依赖和保留限制。任何外部认证/网络/runner限制照实记录，继续可独立完成的本地工作；不得降低或跳过原断言掩盖平台失败。实质进展实时更新.agent、README、契约/验收及推进计划。

M9供应链进展：首次OSV对18个依赖查询发现Jackson core/databind 2.21.4共11条未撤回公告，原始查询保留docs/testing/M9-security-before.json。官方修复区间和Maven Central均已核实，已升级到2.21.7并通过最终296项全量及18依赖复扫；未将旧结果记成清洁扫描。doctor不再要求运行目标工作区具备Maven，构建工具诊断字段保留，新增隔离回归。

## 已实施的本地发布门禁（2026-10-16）

`-Prelease-audit clean verify`生成含测试依赖的CycloneDX 1.6聚合SBOM（插件2.9.3）；常规verify仍可单独运行。`prepare-legal.py`从实际缓存JAR提取原文并生成18个坐标及JAR/POM SHA，补充固定上游法律材料。资源按坐标放入META-INF/jharness/third-party，避免Shade同名覆盖。`scan-osv.py`仅发送依赖坐标，处理逐查询分页，失败/未完成/未撤回发现均不允许候选；5项离线正反例核验该门禁。扫描不涵盖构建插件全传递依赖、JDK/OS或原生程序CPE。

`build-candidate.py`要求清洁扫描绑定同一SBOM、法律资源与原始JAR/最终JAR一致，输出文件清单、来源提交和工作树状态及逐文件SHA；不冒充签名制品。`smoke-candidate.py`实际解压到带空格路径，调用分发启动器，验证doctor不联网、审批写入/报告、EOF拒绝、独立CLI取消、UNKNOWN拒绝续跑以及显式核实后恢复。只访问loopback替身。`collect-release-evidence.py`要求296项以上、零失败/错误/跳过及源码/日志时间一致，保存完整测试名、源码/制品SHA、runner/Java/CI URL。CI静态配置不等于执行通过。

doctor行为修正：Maven/Wrapper仍作为构建能力诊断字段显示，但不再决定二进制运行就绪；Java/JAVA_HOME、key、模型配置、Git和插件装配要求保留。未进行模型网络请求。

依据：[Maven构建时间戳](https://maven.apache.org/guides/mini/guide-reproducible-builds.html)、[CycloneDX聚合SBOM](https://cyclonedx.github.io/cyclonedx-maven-plugin/makeAggregateBom-mojo.html)、[OSV分页协议](https://google.github.io/osv.dev/post-v1-querybatch/)。具体漏洞及修复区间见M9-security-before.json所存官方公告。

M9真实CI首次失败与修复中：run38854955517的macOS ARM64在LocalRunControlTest/RunStatePermissionsTest遭遇系统临时目录路径别名，产品拒绝链接保护正常。仅将三处测试夹具临时根toRealPath规范化，保留产品保护；在现有权限测试中补充POSIX symlink/Windows junction拒绝断言，不新增跳过、不减弱原断言。原始日志保留.build-cache/M9-job-116824235442.log，待本地验证后重跑三平台。

首轮Windows runner另发现同类临时路径别名，以及中文参数回显依赖本机native.encoding而在英文Windows代码页丢失字符。进程测试子程序明确UTF-8输出，读取测试显式配置ProcessLimits UTF-8（公共能力已有）；中文和特殊字符原断言完整保留，产品默认native编码未修改。Ubuntu首轮已全流程通过，但不冒充最终同提交三平台通过。

M9第二轮macOS已通过进程/取消控制测试，进一步发现M7ClosureTest直接创建搜索索引时也使用系统临时目录别名。已系统搜索严格路径API调用，补齐该夹具根规范化；SessionSearchIndex拒绝链接及原有搜索/增量/事实不变断言保持。run38855644393失败日志保留.build-cache/M9-job-116826373036.log，继续相关专项和同提交三平台复验。

M9第二轮进一步定位：Windows JUnit原始XML确认子Java启动器已把中文argv转成??（Cp1252），不是输出解码；按OpenJDK21 CP_ACP实现，Windows参数边界测试改用支持Unicode的PowerShell -File原生接收器，中文/空格/&/;及退出码/双流断言不变。Linux暴露真实Subagent取消事件竞争：子任务先观察共享token结束，父线程直接取完成结果时漏记cancel_requested。现于父线程等待前/结果取得后记录一次已观察的取消请求，保留单次CAS、UNKNOWN和不重放语义；原失败回归重复10次，预计全量305项，尚待验证。

M9复验修复就绪：进程/搜索/子任务相关38项通过（取消事实原回归重复10次），原断言全部保留。Windows Unicode argv接收器最终采用临时编译的.NET原生exe；直接Shell接收器按产品ARGV防护被拒绝的本地失败日志保留。Java21 CP_ACP限制不冒称被产品修复，产品Shell保护未改。父线程取消事实竞争已修复；准备第三轮同提交全量，预计305项。第二轮失败与原始日志SHA见M9-ci-second-attempt.json。

M9第三轮：Linux/macOS305项及扫描、安装、恢复全流程通过，Windows已通过中文及取消回归，剩余实际Wrapper失败→修复→重建夹具在首个20秒命令期限内零输出超时。新增Windows离线前置准备：相同最小环境运行真实Wrapper --version，确认已有缓存及固定Maven3.9.9，单独完成PowerShell/.NET冷启动；代理内20秒命令/45秒总预算和失败→修复→真实重建断言均保持。第三轮失败日志保留.build-cache/M9-job-116829194364.log，继续同提交三平台复验，不拼接通过结果。

M9第四轮准备：Windows真实Wrapper前置缓存/版本/原生启动核验及原编译失败→修复→重建流程的9项专项通过，20秒/45秒原限额未改。前置日志按Windows实际UTF-16/本机编码识别，开发中的两次日志解码失败保留.build-cache/M9-wrapper-prep*-targeted.log。第三轮运行与失败SHA已归档M9-ci-third-attempt.json；继续同一新提交三平台完整复验。

M9第四轮Windows仍在真实Maven构建前20秒期限超时；前置检查本身约27秒且已确认已有缓存/Maven3.9.9，说明预热不足以保证每次宿主启动低于20秒。仅真实Maven修复场景改用已有ProcessLimits.defaults()的2分钟单命令上限及5分钟总预算，保留所有真实失败/修复/重建断言；其他进程fixture仍20秒，专门750ms超时、1秒预算和取消用例均不改。产品默认配置未变。第四轮原始失败日志保留.build-cache/M9-job-116832759648.log；修复待专项和同提交全量验证。

M9 Maven场景预算修复的9项专项通过，真实失败→版本保护修复→离线编译成功均核验；仅构建场景使用已有产品默认预算，短时超时/取消断言原样。第四轮失败已归档M9-ci-fourth-attempt.json，准备第五轮同提交三平台全部执行。
