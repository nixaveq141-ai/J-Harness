# M5 验收记录

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](../development/current-state.md)及[全阶段审计](M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

日期：2026-10-14。状态：M5 本地开发与集成验收完成；不代表 V2 全部产品化或跨平台发布验收完成。

## 构建与证据

执行 `.\mvnw.cmd -B clean verify`，BUILD SUCCESS；**175 项测试，0 失败、0 错误、0 跳过**，包含 M0～M4 的 156 项回归与 M5 新增 19 项。编译使用 Java 21、-Xlint:all -Werror；Spotless 检查、模块与 shaded CLI 打包通过。

[M5-results.json](M5-results.json) 保存各测试用例、Windows/JDK/Maven 配置、源码与构建文件 SHA、日志 SHA 和远端 CI 状态。原始日志 `.build-cache/M5-verify.log`，模块 target/surefire-reports 保存 JUnit XML。源码未提交，以 sourceSha256 识别本次测试快照。

```powershell
.\mvnw.cmd -B spotless:apply
.\mvnw.cmd -B clean verify
.\scripts\collect-test-evidence.ps1 -Milestone M5
```

全量验证在标准 Windows 环境运行：受限临时目录无法完成原子 rename 和既有 junction 夹具；改用工作区临时目录时既有“非 Git 工作区”夹具会继承父仓库。没有跳过或削弱这些断言，最终使用标准临时目录完成全部测试。

## 需求与实测结果

| 关联需求 | 测试与实际结果 |
|---|---|
| FR-13.1 / 查询与导出 / NFR-04、07、13 | M5SessionTest 与 M5CliTest 验证冷扫描、workspace/model/最终状态筛选、事件 type/tool/status/包含端点时间、稳定分页、非法参数、损坏会话诊断。JSONL 导出在新仓库完整读取，目标已存在时拒绝覆盖 |
| FR-13.2 / AC-09 / NFR-12 | 恢复状态在关闭、重新实例化及分叉后保留 UNKNOWN；recover/resume 在无模型 key 时先返回 6，字节不变，无工具主体进入。人工核实后可恢复；assistant 调用落盘但 tool.requested 未落盘的窗口也可对账 |
| FR-03.7、13.2 / AC-03、09 | 已结算费用但 assistant 响应未落盘仍标记 pendingRequests。verify-request 显式放弃，不新增模型请求，不伪造响应；重复核实拒绝。迁移/分叉后的每个 model.requested 仍由日志前缀重建 |
| FR-13.3 / NFR-10、12、13 | 内存与磁盘实现共用分叉/查询/请求重建契约。复制精确前缀和来源关系，父子后续 append 隔离，事件 ID 与引用保持；活跃父写者拒绝 fork，失败后可重新获得写锁 |
| FR-13.5 / AC-10 / NFR-12 | 格式 0→1 显式规则前后事件、投影和请求相等；备份与原始字节相等。备份 force 失败不修改源，重试成功；完整坏末行与未完成末行可修复，中段损坏、未来格式/事件 schema 拒绝且字节不变 |
| 冷会话产物 / NFR-06、04 | 重新实例化 ArtifactStore 能读取分叉产物；父内容篡改被拒绝，已复制子产物仍可读；产物校验失败不发布子会话。分页、SHA、作用域与总量限制保留 |
| AC-08 / NFR-01、12 | M5RecoveryIntegrationTest 使用本地 HTTP 模型替身跑真实 CLI：父会话读取→冷分叉→用户修改→resume 用旧版本写入。写入失败、用户文件保持、任务不假成功，父日志不变，后续请求携带历史并可重建 |
| AC-09、10 回归 | M2～M4 的真实检查点故障、跨 JVM 写锁、意图/执行/提交/结果崩溃窗口、CLI 强制崩溃、人工核实后冷恢复全部通过 |

M5SessionTest 11 项、M5CliTest 7 项、M5RecoveryIntegrationTest 1 项。M4 的真实 DeepSeek 证据保留为历史基线；M5 不调用付费模型，恢复入口通过本地模拟 HTTP、真实磁盘、真实工具管线与既有跨进程夹具验证。

## 边界与后续

- 远端 Windows/Linux CI 未运行；当前结论限定本地 Windows。
- JSONL 是正式磁盘实现；SQLite、全文索引/搜索、比较、归档界面与可配置保留策略尚未实现，不将 FR-24 全部产品化标成完成。
- 迁移只支持文档定义的格式 0 导入布局，不猜测未知旧格式。未来版本只读，损坏日志不能作为安全续跑来源。
- 查询逐个加载有界冷日志，没有索引加速；export 不包含产物/报告，跨主机搬迁需一起保留其目录。
- 默认保留备份与产物，不自动 GC；失败副本可能留下未引用的产物目录或未完成备份，成功回执中的备份是正式恢复依据。
- M6 下一阶段为流式、压缩、PTY、多模型及 Profile/Bundle/Preset。

完整产品行为见 [M5 契约](../development/M5-contracts.md)。
