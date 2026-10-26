# R2 持久化边界补充契约

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](current-state.md)及[全阶段审计](../testing/M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

日期：2026-10-22。覆盖原计划 R2 四包；实现/验证状态见同目录修复记录。这里定义行为，不将契约文字计作动态通过证据。

- fork 同时持有源、目标的 JVM/OS 独占锁，目标已存在则拒绝。先编码并核验完整日志预算，再准备产物；复制到唯一临时目录，一次验证读取后原子发布。内置 CLI/Web 在日志发布失败时补偿新复制目录。自定义旧 Consumer 回调没有删除能力，需显式提供补偿回调；进程崩溃仍可能留下未引用目录，不自动 GC 用户数据。
- `readSnapshot` 的文本与 SHA-256 来自同一 UTF-8 内容。分页对完整有界原始字节取摘要；Local/Remote 观察更新串行化。远端键去除 `.`/重复分隔符，最多 10,000 条，淘汰后现有文件仍须显式版本。patch 只发目标版本，执行后清旧缓存并装入已确认的新版本。原子替换必须复制 POSIX mode 或 Windows ACL，访问权限不能保持时不提交。工作区文件尽力复制 owner/group；受限容器无法 chown 时，FileChange 的可选 metadata 明确给出 ownerPreserved/groupPreserved/permissionsPreserved 和 ownershipNote，单文件及 patch 事实都传递该说明。私有 JSON 改写保持严格 owner/group 复制，不静默降级。四参数 FileChange 构造器仍兼容，默认 metadata 为空。
- JSON 配置写入对文件 `force(true)` 并原子替换；没有父目录掉电屏障，因此只承诺进程异常下的完整文件替换，不把它称作断电后目录项耐久保证。显式配置路径可在工作区外；最终文件 NOFOLLOW，祖先目录须由可信宿主控制。
- 元数据段只允许单段或明确空串根。已绑定的 workspace/artifact 根在操作边界核身份；有 fileKey 的平台使用 fileKey，私有元数据/artifact 根另有 NOFOLLOW、有界读取的 UUID 身份锚。Windows fileKey 为空且 NTFS 可能将相同名称新目录的创建时间隧穿继承，因此不能仅靠时间判断身份。身份锚不构成强原生文件句柄证明；检查不能消除不合作外部进程在检查和访问之间替换父目录的所有竞态。私有元数据根由操作者保管，不是 OS 沙箱。固定 `web-workspace-owner` 是协调探针/锁日志，沿用历史过滤，失败不能删除已有同名文件。
- 新 SDK 会话先持久 `session.created.sdkInitialization=PENDING` 与稳定绑定指纹，再持久 `sdk.created`，随后才装配插件。仅一个匹配初始化事实可重入补完；其他半初始化/未知事实保持阻断。绑定递归移除环境及父环境实例 id，保留真实 kind/image/workspace/filter 等字段，用规范指纹比较，避免脱敏碰撞合并真实身份。兼容已知 `1.0..1.14` 版本形状；未来主/次版本拒绝，旧无指纹 binding 仍严格比较。
- goal/input/cancellation 的纯校验先于任何轮事实与费用 epoch。新带 submission 的输入以一条 `sdk.input.applied` 同时保存 `userMessage`、requestHash/kind、内容指纹；无悬空 userEventId。旧 `sdk.input.applied` 加独立 `user.message` 仍可读。`next-turn` 指排队后开启的下一完整 Loop，不是重放旧轮。同一 hash 重入核对内容及 kind，拒冲突，不重发模型。旧无内容指纹的重复提交保守拒绝，不猜测身份。
- request.context 仅在有前缀内匹配的 model.requested 引用时进入消息投影。多未决尝试不能被一个 step 响应全部结算。独立 pipeline 的 tool/approval.requested 也阻断恢复；未决事实不能靠显示状态变更清除。UNKNOWN 工具消息保留协议配对，人工核实另追加事实。终端核实可压住相同陈旧 snapshot，真实新工具活动或改变的 snapshot 重新进入未决状态。
- catalog 写入和回放校验身份/事件白名单/状态，允许真实 UNKNOWN。缓存读取/写入受同一锁保护；空 task record 明确诊断，不当作不存在。新迁移记录保存原始来源的规范摘要，重入跨脱敏配置变化核同一来源；无该摘要的历史部分迁移仍需精确匹配，否则保持 MIGRATION_CONFLICT，不猜测或删原事实。替换持久仓库恢复复用相同任务/轮投影，GET 轮历史可用，新轮写入仍要求对应目录能力。移除或丢失 profile 不能释放其未决占用。
- WorkspaceLease 在持有 workspace owner 后，用 NOFOLLOW 句柄读最多 65,537 字节，超限拒绝；taskId/catalogKey/requestHash 校验后再发布，重开再核验。半提交指针保留用于对账。
- 问答 HTTP 202/SUBMITTED 是内存暂存回执。必须 GET 核对最终 ANSWERED/SKIPPED；重启 INTERRUPTED、写盘失败或取消都不能冒称答案已持久。已提交答案与取消交错时保留 requestId/hash，并另记录 cancellationObserved。历史 questionId 冲突、非法 options/关联/终态拒绝。回答从不批准工具或计划。
- plan_update 是明确新修订：执行中禁止直接编辑，终态编辑生成新 DRAFT 并递增 revision，旧批准不复用。证据按当前待办或步骤位置关联，必须来自当前修订状态之后的真实成功工具；同一事实可支持多个步骤。文字目标的实际达成仍由宿主交付评估判定。revision=10000 停止增量，不能生成 10001。
- 待办最多 128 个生命周期 ID（含 REMOVED）；队列最多 8 个待执行、每任务 256 个完整幂等历史，分页 32 条；终端每 owner 最多 4 个活进程、32 个生命周期任务，包含已退出任务及其日志。达到历史上限明确拒绝并保留事实，可显式开新 owner/session。当前没有自动终态回收/墓碑滚动策略，不通过只计活跃项形成无界存储；这些上限不是无限长期会话支持声明。
