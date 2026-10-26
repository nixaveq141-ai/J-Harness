# R5 有界处理、检索与长会话修复增量

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](current-state.md)及[全阶段审计](../testing/M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

2026-10-25。基线 `7f5b9bf66f7c63d6960e2f50cd05afe652ba4136`，按用户要求实施 R5。本轮保留原 64 条意见的完整[范围台账](OCR-R5-scope-2026-10-25.json)：51 条实施或回归补强、2 条有限实施、11 条后续项；意见包含同根因记录，不能等同于 51 个独立缺陷或 64 条逐项验收完成。R5 保持进行中，M10.5.9/M11 准入不变。

## 实现

- 新增 JSON 写出器，在产生输出时限制 UTF-8 字节、解析 token 和容器深度；内存编码与流式输出共用实现。命令协议、Web 响应/状态、SDK 配置、MCP 返回、工具摘要和索引发布接入相应预算。公开 transport Reply 在 clone 前检查上限，仍保留独占快照。
- 命令截止时间覆盖启动耗时，拒绝无效 Duration，输出/输入泵失败及时停止已启动子进程并保留不确定结算。它不承诺中断操作系统阻塞的 start。Worker 编码失败产生有界错误回执，已 dispatch 的结果不会改成 executionRejected。
- 进程模型摘要使用 JSON，保留退出、清理、不确定性、reason/limitations。终端展示及模型内容使用有界预览和原始字节 offset 回读提示，完整 value 仍保留；不把 UTF-16 字符长度加到字节 offset。
- 部分 patch 也经过产物压缩，版本、已改路径、失败/待处理路径和 UNKNOWN 保留。产物失败时部分 patch 继续 UNKNOWN/PATCH_PARTIAL；已知提交的单文件写入沿用 FAILED/ARTIFACT_WRITE_FAILED 且保留 committed 及版本事实。预览从完整脱敏产物读取，产物失败不回显原始 diff。
- 原始/正文索引损坏可丢弃重建，截短源会持久回收多余行，重建标记反映变化；正文仍逐次以当前脱敏事实校验缓存。索引编码采用固定大小缓冲流式散列/写入、临时文件 force 后原子发布；叶文件不跟随链接，增加目录身份边界检查。
- 不同 session 的索引查询独立持锁，注入 sanitizer 调用继续串行。工作台最多两个查询并发，第三个明确 SEARCH_BUSY；快照 map 仅短时持锁。每 profile 每查询只列一次 membership；已移除 profile 过滤，provider 时间戳只验证/解析一次且错误不回显原值。İ 展开和 emoji 摘要按原文 UTF-16 坐标裁剪。
- 异常工具结算、Planning 单次调用、问答 respond 复用一次事件快照；request journal 不再为所有 payload 建副本索引；不可变 projection 缓存 uncertainCalls。内存仓库有每会话 10000 事件/32 MiB、总会话 10000 上限；fork 先固定前缀，在全局锁外构造，完整成功后发布。静态 session gate 为固定 1024 条带。
- readPage 对末尾换行/无换行和越 EOF 给出 more/eof，最终非空行的 nextLine 前进；长行因字符预算截断仍显式 truncated，按行翻页不能充当长行字符回读。search 达到 limit+1 停止整个遍历；grep 达上限后不再打开后续路径，保守标记截断；默认路径发现截断传播，并报告 selectedPaths/scannedPaths。
- 压缩一次测量消息及工具声明，移除完整旧组后一次构造。保护 system、最近 user 与最近 assistant/tool 组（含尾部 user）；替换 compactor 同样校验最近 assistant。保护内容恰好放下允许空摘要，真正超限拒绝。旧摘要不递归拼接；引用保留 user 身份并明确有损、不可信。审计最多 256 个来源/关联，歧义与合成消息不猜来源，记录未映射数和完整来源数量。工具声明递归按 key 排序；没有声称提供商缓存命中或性能收益。

## 限制与后续

OCR-0060 仅对唯一精确消息值建立来源关系，重复历史/合成消息仍可能未映射。OCR-0819 只有目录身份边界复核，尚未接入 SecureDirectoryStream，Windows 路径操作仍有父目录替换竞态，不能宣称已消除。

11 条后续项详见范围台账：ContentPages/产物每页全量校验的安全快照复用、CLI 收尾读合并、ModelDisplayBuffer 锁外读取、ObservedLlmClient 显式关联设计、三组既有 HTTP/历史分页断言、DurableTaskCatalog 旧目录扫描优化、gitignore 规则逐行说明、Web 相邻读取合并。它们没有被本轮关联全套通过自动关闭。

没有峰值堆内存剖析/生产长会话性能基准；编码的预算回归和屏障并发回归不等于这些测量。保留逐次秘密更新与产物篡改检查，不以盲缓存换取速度。没有本机 Firefox、新远端三平台/三引擎、Docker、付费请求、推送或发布。验收数据和原失败见[本轮验收记录](../testing/OCR-R5-remediation-2026-10-25-validation.md)。
