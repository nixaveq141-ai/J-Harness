# R1–R3 复审实施本地验收

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](../development/current-state.md)及[全阶段审计](M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

日期：2026-10-25，Windows 11 / JDK21.0.12.1。基线 `252bfd7bdf0bf121119502a386cb71a66463a2cd`。本次仅本地提交，不推送，不改变 M10.5.9 或 M11 准入。

按原174编号逐条交付：138实施、9契约澄清、8既有修复确认、19不采纳。重复意见不作为独立缺陷计数。原始评估及历史R1/R2/R3结果保持原字节；R1旧台账摘要在 `5d8ff4e` 已再次精确核对。

| 验证 | 结果 |
|---|---|
| `mvnw.cmd -B -o verify` | 671项、109套件，失败/错误/跳过均0，Spotless与打包通过 |
| `node sdk/tests/run.mjs` | TypeScript33、Python33；pending槽位、取消/未知不重放及协议回归通过 |
| 原生凭据探针，本次独立输出路径 | Windows Credential Manager 7项，包括重启读写、轮换、删除和回读清理 |
| 旧worker兼容探针，本次独立输出路径 | 4项及parentCleanup通过 |
| `run-r3-validation.py`及只读`--verify` | runId=`r123r-20261025-complete`，16组184项，同构建输入快照，通过；withProduct=false，不冒充浏览器矩阵 |
| `verify-r123r-implementation.py` | 原裁决/174覆盖、155采纳范围文件与通过用例引用、当前实施输入摘要核对 |

最终JAR：`2e9c7fe946362b3df005571922060849263bcd65f25eb329e804062d5b9146ba`，SDK/原生/兼容报告绑定相同字节。原始SDK报告另复制到本次 `.build-cache/R123R/final/`；原生/兼容报告和各命令日志独立留存，摘要、完整Java用例身份及源码绑定见 [results JSON](OCR-R1-R3-implementation-2026-10-24-results.json)。缓存不入Git，不上传扩大原始日志。

新增负例覆盖压缩工具组/索引审计、sanitizer错误域、完整UUID并发发布、配置修复、非法队列发布前校验、事件饱和/未启动serial取消、路由factory失败、stop参数、策略目录别名、损坏cleanup原件保留、结算失败不重试与继续结算其他已受理调用。设置清理helper4项证明主错误保留、清理全尝试、没有主错误时禁止假PASS；未启动浏览器即可验证这部分。纯事件预算专项注入barrier，真实force与毒化writer回归仍在全量中。

先前失败均保留 `.build-cache/r123r-*`：受限环境临时文件移动/socket拒绝、插件新增诊断的旧期望、Git正常诊断上限、状态版本负例、父路径尾空格遗漏、规划未append拒绝的旧异常期望、MCP重复close未知证据、根消失分类及新诊断夹具的非法事件层级/终态。均修正后定点核对，再完成上述全量；原有安全与业务断言保留。两次采集在修复期间检测到输入漂移，正确拒绝，未计作通过；最终fresh运行重新验证全部16组。

sanitizer合成观察：1000行、25次，single约29.6ms/线程分配59.3MB，chain约55.8ms/140.9MB。源码工具在 `docs/testing/tools/SanitizerCost.java`，结果JSON保留原始计数；这不是生产负载基准，不声称已优化内存或吞吐。

平台与覆盖限制：没有新的Linux/macOS、真实Docker或三浏览器验收，不运行用户禁止的本机Firefox。POSIX权限分支、任意文件系统、非合作宿主进程及所有异常时序未逐项独立动态注入；每条采纳范围记录关联回归和明确限制，不能据完整套件通过声称所有平台/故障位置已认证。损坏journal、header-only catalog及遗留fork预约按[离线维护流程](../development/OCR-R1-R3-implementation-2026-10-24.md)检查，未知事实不自动丢弃或重提。
