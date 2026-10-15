# M2 开发与测试验收记录

日期：2026-10-14（Asia/Shanghai）。状态：**M2 本地开发与测试完成**。

Windows 11 / Oracle JDK 21.0.12.1 / Maven Wrapper 3.9.9，最终执行：

```powershell
.\mvnw.cmd -B spotless:apply clean verify
```

结果：**BUILD SUCCESS，104 项测试，0 失败、0 错误、0 跳过**；9 个模块编译及打包通过，编译警告检查和格式检查通过。包含 M0/M1 的 76 项回归和 M2 新增 28 项测试。构建结束时间：2026-10-14 08:45:47 +08:00。

结构化证据：[M2-results.json](M2-results.json)，含测试名称、环境及所测试源码/构建配置 SHA-256。原始日志 `.build-cache/M2-verify.log`，JUnit XML 位于各模块 target/surefire-reports。源码未提交，以哈希清单识别测试快照；M0/M1 JSON 保留历史结果。

```powershell
.\scripts\collect-test-evidence.ps1 -Milestone M2 -VerificationCommand '.\mvnw.cmd -B spotless:apply clean verify'
```

## 退出条件与证据

| 目标 | 测试与实际结果 |
|---|---|
| 工作区边界 / AC-05 | LocalWorkspaceTest 拒绝 ../、绝对/驱动器路径、ADS、设备名和受保护目录；在 Windows 实际创建 junction 并验证读取/写入均拒绝、检索不进入；外部文件保持原内容 |
| 受保护修改 / AC-08 | 早先读取后用户改动、提交前注入并发修改均产生 FILE_CONFLICT；用户文本保留；匹配次数、覆盖许可与版本要求验证通过 |
| 不破坏无关文件 | 真实文件 read→edit Agent 任务修改 Main.java，其他文件保持不变；BOM/CRLF、Diff 和版本证据通过 |
| 大文件、错误与取消 / NFR-04 | 有界分页、跨页 grep、二进制/非法编码/超量拒绝、提交前取消、注入 I/O 失败及临时文件清理通过 |
| 目录性能 / NFR-03 | 临时本地项目 1,000 条目，三次列举为 **287、290、283 ms**，均 ≤2,000 ms；夹具为独立平级文件，不含网络工作区，实际杀毒/缓存影响未单独隔离 |
| 真实检查点 / NFR-12、AC-09 | 实盘日志每条 force；在 write_file 意图 force 注入失败，writer poisoned、目标文件不存在、提交钩子未触发；独立诊断可见 CHECKPOINT_FAILED |
| 崩溃窗口 / AC-09 | 独立 Java 子进程在意图/开始记录确认后真实写文件，再 halt 且不记录结果；重开日志派生 crash-call 不确定状态；新 Loop 不调用模型、不重放，文件保持已提交内容 |
| 单写者 / AC-10 | 当前 JVM 第二 writer 和另一个 Java JVM 均被 WRITE_CONFLICT 拒绝；日志字节不变；原 writer 关闭后子进程可正常追加 |
| 尾部/中段/版本 / AC-10 | 尾损坏默认拒绝，显式独占修复仅截尾；中段损坏不丢弃；未来版本只读 inspect、拒绝解析/写入且原始字节不变 |
| 资源边界 | 大型稀疏日志在分配前拒绝；密集 JSON 在物化前拒绝；累计复杂度在追加前拒绝，旧日志仍可读取 |
| 可追溯性 / AC-03 | 真实读写 + JSONL Loop 捕获的每次 Fake LLM 请求，均从截至对应 model.requested 的磁盘事件重建相等；工具修改携带 before/after 版本 |
| Git / 产物 / 生命周期 | 独立临时 Git 项目真实 diff 包含修改；大 Diff 保存产物，重建存储服务后可读，跨会话/篡改拒绝；插件停止撤销工具与文件服务 |

测试计数：api 3、events 7、session 20、plugin 10、tools 4、workspace 12、llm 9、testkit 39，总计 104。Agent 与文件管线集成测试集中在 testkit。

## 验收范围

- M2 的 AC-05、AC-08 文件场景通过；NFR-03 达到当前本地夹具目标；NFR-04/05/07/10/11/12 有本阶段证据，仍需随新增命令/沙箱/入口回归。
- AC-09 的真实文件崩溃窗口与检查点失败、AC-10 的跨 JVM 单写者/损坏/未来版本最低保障已经前置验证；它们不代表完整恢复产品流程或硬件断电持久性已经验收。
- AC-01 有真实文件工具 + Fake LLM 的集成基础，真实模型探索/CLI 场景留到 M4；AC-02/04 的真实构建与自主修复也留到 M3/M4。
- grep 当前是字面搜索，常用 .gitignore 子集与检索深度/扫描上限见契约；apply_patch 按 PRD 允许的 V1 后半期安排到 M4。
- Windows/Linux CI 已配置但未远端运行，Linux 未本地实测；当前 junction 负面测试是真实 Windows 测试，Linux 分支为 symlink。没有真实付费模型的效果验证。

## M3 接续

实现同一执行世界中的通用 ProcessRunner、权限与人工审批、预算、runId/取消控制通道和进程清理状态；同步推进 AC-06、07、14。真实命令副作用必须继续满足已建立的实盘意图保障，并复验 AC-03、09、10～13。
