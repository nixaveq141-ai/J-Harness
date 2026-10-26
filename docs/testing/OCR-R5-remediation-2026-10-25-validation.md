# R5 本地修复增量验收

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](../development/current-state.md)及[全阶段审计](M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

2026-10-25，Windows / JDK 21 / Maven Wrapper 3.9.9。基线 7f5b9bf，最终源码的本地增量验证通过。原 64 条范围中 51 条本轮实施或回归补强、2 条有限实施、11 条后续；不把相关套件通过写成 64 条精确窗口全部关闭，R5/M10.5.9/M11 保持未完成。

| 验证 | 本轮实际结果 |
|---|---|
| Java `clean verify` | 700 项/117 套件，失败、错误、跳过均 0；保留原 677 个身份，新增 23 项 |
| 同输入客户端 | 42 项，精确名称集合通过 |
| 证据门禁 | 16 组184项；与 Java/客户端合计18组926项 |
| TypeScript / Python SDK | 每语言33项，共66项，真实子进程及授权脱敏 loopback 替身 |
| Chrome / WebKit 真实检索 | 每引擎28项：授权范围、稳定分页、重建、来源读取、重启无重放 |
| Chrome / WebKit 真实内容 | 每引擎45项：流式中断/取消、完整脱敏、产物回读、换页、来源边界与重启 |

最终同输入回执 `runId=r5-20261025-final-frozen2`，580 个构建输入摘要一致。最终 JAR SHA-256 `71ae2754dd54e1b92378f8fc74cb903682afeb7e6010511af8fdff3fee13c7c7`，与 SDK、两引擎检索/内容报告相同；包内 UI 逐文件核对当前 dist。浏览器源码前后摘要相同，各引擎原报告和截图在下一引擎执行前独立冻结。机器结果见 [results](OCR-R5-remediation-2026-10-25-results.json)，核对器见 [verify-r5-2026-10-25.py](tools/verify-r5-2026-10-25.py)，包含状态/JAR/源码/计数四组篡改拒绝负例。源码模式扫描只代表所列模式未发现，不代表任意秘密检测。

新增回归重点：JSON 字节/token/深度恰好界限与超限、固定缓冲流式一致性、禁止任意 toString、İ 与 emoji 原文坐标、缓存损坏/源缩短、目录替换拒绝、不同 session 同时进入 reader、256 会话范围/profile membership 次数、双查询与第三个 SEARCH_BUSY、无效 provider 时间戳、逐字段 cursor 绑定、内存配额及失败 fork 不发布、末行/EOF/grep 达限停止、真实子进程输出泵超限提前停止并保留 UNKNOWN、最近 assistant/tool 组和恰好预算保护、压缩来源关联上限与歧义未映射。

原失败/失效尝试保留在 `.build-cache/r5-*.log` 与 `.build-cache/R5-2026-10-25/`：首次编译 JsonEncoding 名称冲突；新增测试未引用 try-resource 的 -Werror；清单脚本默认 GBK 解码失败；旧终端夹具以 `pty=` 识别 Map 文本，改 JSON 后等待，线程栈确定 executor.close 正等待交互任务。只停止本轮 Python/Maven 自有子树；夹具改按 JSON 字段读取，并先关闭输入再等待 executor，原六项跨轮/清理断言通过。进度文档曾使用不支持的 task 状态，改为已有 IN_PROGRESS 并重新生成当前计划。失败尝试没有当作通过；最终回执只取修正后的同源码完整验收。

未执行本机 Firefox、新远端三平台/三引擎 CI、Docker、生产堆内存/性能剖析、推送、发布或付费调用。没有用盲缓存放弃产物完整散列与秘密更新校验。SecureDirectoryStream 和 Windows 父目录替换精确窗口、唯一值无法确定的历史来源，以及其余 11 条后续项详见[范围台账](../development/OCR-R5-scope-2026-10-25.json)。本轮不是整阶段/平台认证。
