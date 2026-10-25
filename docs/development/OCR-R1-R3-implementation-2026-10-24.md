# R1–R3 复审采纳范围实施

本轮基线为 `252bfd7bdf0bf121119502a386cb71a66463a2cd`。范围沿用 2026-10-24 对会话 `843b1649-766e-4b66-9a53-8183d4fcfb3e` 的裁决：280 文件、174 条意见，68 采纳、87 部分采纳、19 不采纳。编号与原裁决不改，重复意见按同一根因处理，不作为独立缺陷计数。实施跨至 2026-10-25；最终逐条状态与验证见同名 JSON 台账及 testing 结果。

## 实施边界与兼容说明

- 压缩以原始消息索引和完整工具组为单位；保留消息必须保持原序，新增摘要必须与审计摘要一致，未决工具组不能移除。畸形 ID 统一为 `INVALID_COMPACTION`。
- 工具准备成功即由调度拥有者跟踪。运行前失败只结算未进入主体的调用；运行后失败保留 UNKNOWN。每个 finish 只尝试一次，失败追加 `tool.settlement.failed`，不能凭追加失败推断原完成事实不存在。整个批次共享首次停止的两秒宽限期，UNKNOWN 可升级原因，重复 UNKNOWN 不延长截止。
- 多个模型请求未决时，assistant 不隐式结清整个 step；按请求事件 ID 逐条核实。孤立 `request.context` 不应用到投影；同 turn/step 已有最终上下文却缺引用的请求明确报 `REQUEST_CONTEXT_MISSING`，无上下文的旧事实仍兼容。
- `SessionWriter.appendPrepared` 必须返回真实持久化事实。改写 payload/session 的 writer 必须覆盖；默认实现只适用于原样保存。DurableTaskCatalog 已覆盖该接口。
- `LlmClient.supportsSingleAttempt` 保留已有 override 的兼容发现。可信装饰器必须传播真实能力，单次方法不得委托到带重试的 complete；反射不证明实现遵守契约。`retryAfterMillis` 目前仅为建议元数据，生产重试按配置的有界 backoff，未承诺消费 Retry-After 或任意提供方重试规则。
- `DataSanitizer.chain` 的快照是第三方可变对象和非 JSON 值的边界，保留每个信任交接处的验证。没有用“不可变”标记跳过第三方校验。Windows/JDK21 合成1000行JSON的25次测量，single约29.6ms/线程分配59.3MB，chain约55.8ms/140.9MB，证实有额外成本；它不是生产负载基准，不代表端到端瓶颈。本轮不宣称分配量或吞吐已改善，复现工具见 `docs/testing/tools/SanitizerCost.java`。
- 默认 `FileSystemView.readSnapshot` 仅适用于 UTF-8 内容 SHA-256 版本；使用 opaque 版本的 provider 必须覆盖快照接口（包括有界重载），保证正文/版本来自同一内容。worker 复用该接口，默认实现仍保守复核版本，不以双读冒充原子操作。
- 私有目录 UUID 锚先写完并 force，再以硬链接原子、不覆盖地发布。文件系统不支持该原语时明确失败；不自动改为有覆盖风险的 ATOMIC_MOVE。稳定 fileKey/UUID 存在时不依赖可回退的创建时间，UUID 始终参与比较。普通工作区根不解释用户 `.directory-identity`；JDK Windows provider 的 fileKey 为空，保留原生 creationTime 身份比较，其他缺 key 的 provider 明确不支持，避免接受 mtime 回退。私有根仍额外保留 UUID，损坏即拒绝。非法锚与普通权限/磁盘 IO 分开分类；公开根的时间身份不构成对非合作宿主进程的目录句柄沙箱。
- fork 发布采用私有根中的目标专属 CREATE_NEW 预约，所有合作发布方遵循同一预约。它不构成对非合作宿主进程的沙箱保证，也不宣称 ATOMIC_MOVE 天然 no-clobber。进程异常退出可能留下预约，维护流程见下节。
- 设置提交后清理失败返回已提交并标记 credentialCleanupPending；删除意图先持久化。fixture 清理工具合并当前设置与合法、有界 journal refs，分批删除并回读，不能把无法解析的引用当已清理。
- 环境变量秘密识别使用完整名称/下划线边界，避免 SSH_ASKPASS/COMPASS 等误识别。明确登记的短秘密仍脱敏。原生 worker 父端预检实际 JSON 字节数；确定未执行的 6/7 分类保留，通信不明仍 UNKNOWN；数组擦除不承诺清除 JVM String/副本。
- `CommandTransport.failureCode` 为可选安全遥测，不改变执行状态仲裁；业务依据 started/uncertain 和已验证响应。worker 仅在 dispatch 前的明确拒绝设置 executionRejected，进入副作用后异常不自动归安全失败。容器与命令进程清理分别保留 `containerCleanup`/`cleanupStatus`。
- 问答插件 stop 后撤销实例能力，list/respond 不重新开放；已保存答案由宿主静态 `recorded` 路径对账。旧指纹回退仅按原版本核对，不重新登记/执行工具。
- 路由 select 在注册/完成 future 的锁内线性化；借出的 client 生命周期受 router.close 约束，不承诺返回之后永不被宿主关闭。factory 尚未返回时保留 FACTORY_PENDING，失败并确认未返回 client 才移除该标记；factory 自身负责部分构造资源。
- Anthropic 本产品接受非空字符串或最多 64 个非空字符串，空列表归一为省略字段；类型错误统一 `UNSUPPORTED_PARAMETER`。64 是本产品有界子集，不是 Anthropic 官方最大值，不宣称真实服务任意参数兼容已验证。
- `JHARNESS_GIT` 可显式配置受信任的绝对常规可执行文件路径；不扩大为任意 PATH 搜索。Git stderr 上限为 64 KiB，超限优先 `OUTPUT_LIMIT`，正常非 Git 目录保留 `GIT_DIFF_UNAVAILABLE`。`JHARNESS_DOCKER` 为补充采集器显式运行时路径，缺失在创建新结果目录前拒绝。
- run control 新写 `version=2`；旧 version1 仅包含可验证 processStart 时兼容。旧记录缺身份/未知格式返回 UNSUPPORTED_STATE（退出7），不发取消；OUTCOME_UNKNOWN 独立退出6，UNREACHABLE 仍退出4且不承诺可安全重试。
- 终端 `outputPending` 表示安全尾部尚未排空，与真实读取/产物持久化失败分开；不持久化可能包含未脱敏秘密的尾部。已提交补丁的展示产物失败保留 changes/版本和 committed，不能按假失败重做。
- 审批测试改用已发布 prompt、真实 sendAsync 交接作为屏障。deadline monitor 竞争测试仍明确为白盒：先验证双方实际 BLOCKED，再等待截止，改锁必须同步该测试接缝；不把实现检查次数当业务证据。规划 stop 观察使用有界让出，迟到子资源必须实证退出。纯事件数量测试注入 durability barrier，独立真实 force/毒化 writer 测试保留。
- 保留历史用例身份的 null-policy 命名仅指 null decision 返回值；ToolPipelineContext 构造时已拒绝 null policy，本轮不宣称存在可执行的 null policy 放行。设置浏览器夹具的 finally 使用独立 cleanupFixture：逐个尝试所有清理，失败附于原失败并输出固定未知诊断；没有原失败时清理失败阻止输出 PASS，绝不把缺设置当作已清理。

## 离线维护与恢复

所有流程先停止该根的宿主并确认拥有者锁已经释放，备份原始字节及 SHA-256，在独立副本检查。不得对活动根执行清理，不因空文件/缺字段自动重提操作。

1. **损坏 credential-cleanup.json（048）**：启动返回 `CREDENTIAL_CLEANUP_INVALID`，原件不改。用同根可信备份、当前模型 credentialRef 和变更记录逐项重建待删除 refs；仅接受 64 位小写十六进制、最多128项。先由人工核对哪些引用仍被当前模型使用，再通过明确的凭据维护操作删除不再使用的引用并回读确认。无法确定的引用保留 pending；恢复合法 journal 后再启动，不能直接删 journal 或用空 refs 假称清理完成。当前没有自动修复损坏 JSON 的在线入口。
2. **header-only task catalog（154）**：保留 `h-<hash>.jsonl`、catalog version/迁移备份与对应 session；空任务记录继续拒绝启动。以原始提交 hash、taskId 和 session 检查是否有 accepted/applied/效果事实；只有可信备份包含同一受理身份时才离线恢复原始记录。没有备份时维持不可执行维护状态，记录“受理是否发生未知”；不要删空记录后重新提交。未知副作用必须通过原人工核实流程处理。本轮提供固定拒绝与检查流程，不提供自动跳过损坏记录的能力。
3. **锚/预约残留（015/128/130）**：非法 UUID 锚不能重建成新身份，需保留根及备份确认原身份后恢复。`.fork-publish-<target>.lock` 仅在确认没有活动发布者、目标与暂存目录均已核对并留存备份后由维护者处理；目标已存在则保留其内容，绝不覆盖或合并。缺少充分证据则停止 fork。

## 历史证据引用

R1 历史结果中绑定的旧实施台账可在 Git revision `5d8ff4e` 精确复现；不能拿当前台账字节反推旧报告不可复现。复审的隔离核对与哈希记录仍在 `OCR-R1-R3-rereview-2026-10-24`，历史结果/台账不重写。新的矩阵级失败带 `scope: matrix`，旧失败保持原字节；新 native/兼容回执保存在本次 fresh 目录，启动/编译前先写 INCOMPLETE，不能继承旧 PASS。Node 用例按 JSON/NDJSON 实际格式读取，新增组不再按名称被跳过。

本轮验证属于本地修复验收，不替代 M10.5.9 的同提交三平台/三浏览器准入。未执行的远端 CI、Firefox、Docker 或原生平台组合不能记为通过。

最终本地收口：138 条实施、9 条契约澄清、8 条既有修复确认、19 条不采纳；不是138个独立缺陷。Java完整verify 671项/109套件、两语言SDK66项、Windows原生7项/旧版兼容4项、R3证据门禁16组184项通过。逐条映射及限制见同名JSON，[验收说明](../testing/OCR-R1-R3-implementation-2026-10-24-validation.md)与[结果](../testing/OCR-R1-R3-implementation-2026-10-24-results.json)。
