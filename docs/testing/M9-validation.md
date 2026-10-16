# M9 发布验证记录

日期：2026-10-16。状态：本地发布准备和Windows候选验证已通过；真实Windows/Linux/macOS CI待授权推送验证分支后执行，M9整体尚未验收，未正式发布。契约见[M9契约](../development/M9-contracts.md)。

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
