# M1至M10.5已发现缺口的当前闭合记录

日期：2026-10-29。原始审计见[M1～M10.5审计](M1-M10.5-gap-audit-2026-10-26.md)，本文更新活动状态，不改写历史JSON/OCR。范围是已交付阶段和R0～R6采纳意见的已发现缺口；未来M11/M12功能另行立项，也不宣称穷尽所有未知缺陷。

运行时代码8b9ec77：本地735项/124套件与622输入、Docker七夹具均已复核。完整三平台40838729596全部通过，原三ZIP和完整汇总已严格审计，整轮已关闭，见[最终验收](M10-gap-final-acceptance-2026-10-29.json)。

| 原编号 | 当前覆盖与状态 | 证据入口 |
|---|---|---|
| GAP-01 | 索引发布/读取/锁、工作区读写/列举/搜索/父目录、产物读写/分叉/补偿及JSONL读锁/迁移/备份已接目录能力；原生跨平台专项与本地回归通过，当前完整三平台也已通过。保留可信宿主、非内容CAS和命令cwd边界。 | [原生73项](M10-gap-posix-fix-2026-10-29.json)、[JSONL](M10-gap-jsonl-directory-2026-10-29.json)、[Docker7](M10-gap-docker-final-2026-10-29.json) |
| GAP-02 | R4客户端写后故障、卸载/代次/存储/正文/观察窗口及真实后端17操作×3响应故障已覆盖；当前完整三浏览器验收也已通过。不是所有可能线程交错的证明。 | [真实后端矩阵](M10-gap-r4-real-matrix-2026-10-28.json)、[生命周期](M10-gap-r4-lifetimes-2026-10-27.json) |
| GAP-03 | 三组分页补非空游标、真实下一页、完整身份、不重叠与终止断言；原身份保留。 | [分页实施](../development/gap-remediation-2026-10-26.md) |
| GAP-04 | 当前源码七个真实Docker夹具通过，固定镜像、同JAR/源码/类字节及自有清理核对，旧失败保留。 | [当前Docker](M10-gap-docker-final-2026-10-29.json) |
| GAP-05 | 重复消息/事实和请求上下文显式身份关联；未知来源仍明确unmapped，不猜来源、不承诺压缩可逆。 | [来源/快照](M10-gap-provenance-snapshot-2026-10-27.json) |
| GAP-06 | CLI/Web快照复用、分页/产物单快照与旧任务有界地址索引已补；权限/秘密/版本与全部字节完整性检查保留。 | [快照](M10-gap-snapshots-2026-10-27-results.json)、[索引](M10-gap-followups-index-identity-2026-10-27.json) |
| GAP-07 | 展示锁外读取/reset代次屏障和模型请求显式事实关联已补；错误保持UNKNOWN。 | [快照](M10-gap-snapshots-2026-10-27-results.json)、[请求关联](M10-gap-followups-index-identity-2026-10-27.json) |
| GAP-08 | gitignore无效规则实际行号/序号和操作说明已补，fail-closed及预算不变。 | [规则诊断](../development/gap-remediation-2026-10-26.md) |
| GAP-09 | 保留千条目录两秒目标，补受控128MiB堆、万事实、分页/产物、并发读、真实force及混合负载测量。仅列实际样本，不承诺任意负载或跨平台SLA。 | [快照测量](M10-gap-provenance-snapshot-2026-10-27.json)、[落盘](M10-gap-durable-cost-2026-10-27.json)、[混合](M10-gap-mixed-load-2026-10-28.json) |
| GAP-10 | Windows10、独立WSL、额外CPU与实体Mac验收按用户要求跳过，不伪造认证。 | [活动状态](../development/current-state.md) |
| GAP-11 | 三协议真实非流式/SSE只读闭环和历史M4生成代码的真实Docker隔离编译/功能oracle已补。代码来源仍明确是历史输出，不宣称新的代码生成任务/其他厂商普遍质量；原2元账本/12次上限不变，无新付费。 | [SSE/来源](M10-gap-isolation-and-index-2026-10-28.json)、[Responses](M10-gap-responses-sse-2026-10-28.json) |
| GAP-12 | 原macOS偶发失败与固定诊断保留，根因仍未知；当前代码定点及历史完整通过不等于历史原因已修复，当前完整也通过，历史原因仍未定。 | [原始原因记录](../development/M10.5.9-root-causes-2026-10-24.md)、[当前状态](../development/current-state.md) |
| GAP-13 | 活动入口与证据版本刷新；旧快照按日期保留。最终完整结果已回写活动入口。 | [活动入口](../development/current-state.md) |
| GAP-14 | 原生二进制精确构建来源仍为已声明供应链限制；许可/SBOM/坐标漏洞门禁不等于完整OS/CPE证明。 | [第三方来源](../../third-party/README.md) |
| GAP-15 | 用户明确只做本地备份。735源码/XML/JAR及Docker原ZIP已成包，752成员逐一核对；另已导出同轮三平台原始ZIP完整备份85752713字节，成员摘要核对；异地副本移出范围，90天CI保留策略和原包摘要仍保留。 | [本地备份](M10-local-backup-2026-10-29.json) |

R0～R6原范围、重复意见与采纳裁决继续保留。R1～R3精确补强143/150/172的实现、负例及当前证据见[patch窗口](M10-gap-r123-patch-faults-2026-10-27.json)、[共享宽限/双worker](M10-gap-r123-lifecycle-2026-10-27.json)；不以编号数或通过用例数推导同等数量的独立缺陷。
