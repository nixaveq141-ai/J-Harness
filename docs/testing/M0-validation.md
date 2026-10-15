# M0 开发与测试验收记录

日期：2026-10-13（Asia/Shanghai）。状态：**M0 本地开发与测试完成**。远端 CI 尚未运行，不标记为已通过。

## 实际验证

在 Windows 11、Oracle JDK 21.0.12.1、Maven Wrapper 3.9.9 下执行：

```powershell
.\mvnw.cmd -B clean verify
```

结果：BUILD SUCCESS；**32 项测试，0 失败、0 错误、0 跳过**；5 个模块均完成编译与打包；编译警告视为错误；Spotless 格式检查通过。此次干净构建于 2026-10-13 19:55:59 +08:00 结束。

结构化证据：[M0-results.json](M0-results.json)，包含测试名称、计数、环境与所测试源码/构建配置的 SHA-256。当前尚无 Git 提交，以该源码清单识别本次验证版本。原始构建日志保留在 `.build-cache/M0-verify.log`，原始 JUnit XML 保留于各模块 `target/surefire-reports/`，两者不入 Git。执行 `clean` 会删除旧测试报告。

以下为 M0 阶段的证据导出方式。M1 已更新源码与测试报告，M0-results.json 保留历史快照，不应由当前源码覆盖；当前复测请使用 M1 命令及证据文件：

```powershell
.\scripts\collect-test-evidence.ps1 -Milestone M0
```

## M0 退出条件与测试映射

| 交付 / 约束 | 自动化证据 | 本阶段结论 |
|---|---|---|
| 服务接口、静态容器、依赖解析与配置视图 | PluginContainerTest：依赖顺序、缺失/禁用、循环、版本/API 不兼容、只读配置 | 通过 |
| 撤销、启动回滚与资源释放 | PluginContainerTest：部分启动失败、逆序停止、stop/close 失败仍清理、旧句柄、重复关闭、失败插件事件 | 通过 |
| 三类事件语义 | DefaultEventBusTest：异步非阻塞、异常隔离、撤销已排队订阅、串行顺序/失败/超时、拦截包装/短路/异常、类型隔离、递归拒绝 | 通过 |
| Agent Loop 与 ToolRegistry 替换 | M0ServiceReplacementTest：baseline/replacement 两插件组合通过相同 runHarness 契约 | 通过；仅测试 Loop，不代表 M1 产品 Loop 已实现 |
| 作用域隔离 | PluginContainerTest：共享服务仓库中父子 Agent 的服务/事件/拦截贡献隔离；TestkitTest：工具跨 Agent/会话不可见 | 通过 |
| 内存 Session 与基础投影 | InMemorySessionTest：并发单写者、快照读取、关闭/重开、重复事件、未来 schema、重复 fold、异常状态拒绝 | 通过；单进程内存保证 |
| 事件字段与不可变快照 | SessionEventTest：事件层级 ID、深不可变 payload、非 JSON 值拒绝 | 通过 |
| Fake LLM、事件/检查点录制、失败注入、临时项目 | TestkitTest：请求捕获、取消/耗尽/能力降级、事件顺序/负载、检查点顺序、append/flush 故障、中文空格路径夹具清理 | 通过 |
| 构建、格式与静态编译检查 | clean verify | 本地通过 |
| Windows/Linux CI | .github/workflows/ci.yml | 配置完成；上传后待远端执行，Linux 尚无本地实测证据 |

测试计数：api 2、events 7、session 6、plugin 10、testkit 7，总计 32。

## NFR / AC 进度

| 需求 | 当前状态 | 后续仍需完成 |
|---|---|---|
| NFR-07 | M0 测试基础已验证 | 随 M1～M4 补充真实管线及错误路径 |
| NFR-09 | API 版本、统一错误码、诊断、格式/编译检查已建立 | 产品日志、协议迁移及新接口持续检查 |
| NFR-13 | Agent Loop / ToolRegistry 的替换契约通过 | M1 模型/存储替换、M4 四类全量收口 |
| NFR-02 / 08 | Wrapper 本地构建通过，跨平台 CI 已配置 | doctor、Windows 10/其他平台实测及远端 CI |
| NFR-10 | 事件关联字段、插件生命周期和诊断基础已验证 | 工具耗时、审批、命令退出码、最终报告 |
| NFR-11 | 事件日志与基础投影契约已建立 | M1 对每次模型请求完整重建，AC-03 未验收 |
| NFR-12 | 检查点接口及内存单写者测试通过 | 实盘确认、跨进程单写者与 AC-09/10；当前未验收 |
| AC-11 | 三类事件及拦截机制通过 | M1 参数改写/免审批插件的真实任务管线集成 |
| AC-12 | 撤销与作用域基础通过 | M1 工具管线拒绝撤销工具、M4 全量、M7 多 Agent 产品路径 |
| AC-13 | 禁用必需提供方、缺失服务、歧义、回滚及 Loop 替换通过 | M1 真实管线配置装配，M4 V1 完整验收 |

AC-01～10、14～16 未因 M0 通过而被标记通过；M0 不执行真实模型 Coding Agent、文件修改或 Shell 命令。

## M1 接续事项

1. 实现依赖服务的默认 Agent Loop、PromptComposer 和完整请求事件投影；用 Fake LLM 多步任务验证 AC-03。
2. 实现工具 Schema、解析/作用域/策略/审批/检查点/执行/结果管线；在编码顺序测试前澄清 PRD FR-10.1 与 FR-06.3 的事件顺序差异。
3. 接入首发模型适配器，明确 prepare、取消、超时和重试边界；补模型与 Session 存储替换测试。
4. 在标准扩展点上完成 AC-11 任务级插件；不得用内存检查点宣称真实副作用可安全执行。

上游固定提交已通过官方页面核实并登记，[对照清单](../requirements/upstream-capabilities.md) 记录待源码核验项。本地官方 checkout 的 `git rev-parse HEAD` 尚未取得，GitHub Git 连接失败不影响本地 M0 构建。
