# R3 模型、工具、扩展与配置修复记录

日期：2026-10-23（Asia/Shanghai）。基线 `7eeef09ce8ce8faab53f46f19db2b574beb39aae`。原计划 R3 五包的 304 条采纳范围已完成本地实现、契约澄清和关联回归，状态为 `LOCAL_IMPLEMENTATION_VALIDATED_CI_PENDING`。

[逐项台账](OCR-R3-implementation-2026-10-23.json)保留全部原裁决和理由：183 条对应实现或回归补强，121 条对应契约澄清；五包分别181/22/14/82/5条。这些是采纳范围，不是304次独立、精确的动态故障复现。公共边界见 [R3 补充契约](OCR-R3-contracts-2026-10-23.md)，机器证据见 [验收报告](../testing/OCR-R3-2026-10-23-results.json)。

## 交付内容

- R3-01：共享有界 JsonSchema 下沉 API，旧 tools 类保留兼容门面；注册/声明拒绝缺失或坏 schema 并标明工具。声明错误与实例错误分域，JSON 空键合法，深度/节点限制、精确整数、具名非空和字段限额统一。sanitizer 链拒空输出，压缩后核原始索引/冻结配置/保护消息，worker 与远端结果核字段、路径、版本及字节边界，异常 patch 结果保留 UNKNOWN 与已报告事实。借用、清理、单写者、事件等级和可信扩展责任补充成契约。
- R3-02：模型工厂移出 router 锁，防同路由递归；关闭撤销等待，迟到实例关闭，未确认构造/清理可诊断。路由保留观测/展示装饰，明确 router 所有权。URI 大小写、header key 和原生编码提前诊断；JSON finish/calls 一致，Responses 流终态不得引入未逐项完成的输出。序列兼容、Anthropic 拼接/stop 参数、reasoning summary 白名单和关闭分类补强，流式不自动重试。
- R3-03：MCP null id 结构化应答，result/error 互斥及结果总量有界；非只读执行后的 RPC error 保持 UNKNOWN。技能读取按单文件/总预算限额，空白/超长脚本在注册前拒绝，frontmatter 引号和目录别名受控。网页明确 UTF-8，拒绝其他 charset/非法编码；来源仍由可信宿主授权。
- R3-04：选项不能吞后续标志，提供等号字面量；显式缺配置拒绝，base 路由、bundle 覆盖和选中配置指纹明确。dry-run 先检查取消/schema/纯语义和敏感参数，预览按 UTF-8 字节限额且不执行。命令/终端语义在审批、intent、body 前检查。stdio 句柄显式释放、保留合法响应 id，输入早校验；客户端保留过期响应身份并设明确容量，关闭有界、升级终止，清理错误不掩盖主异常。保留用户批准的同源直接进入契约。
- R3-05：统一本地/远端 patch 路径解析，按 hunk 计数消除正文 `---` 歧义；明确 git 配对前缀与真实 a/ 目录，支持100644新增元数据。删除、执行位变更、无末尾换行等未支持格式明确拒绝；空内容不冒充删除，所有文件先预检。

## 最终验证

最终运行 `r3-20261023-final3`。统一 SDK/脚本 LF 后重新采集，没有用旧行尾回执替代提交字节证明。546个构建输入、119份本次提交实施输入的暂存字节、JUnit身份/退出码/日志/输出摘要、最终 JAR 和包内5份UI资源均独立核对；UI与现有dist字节一致。

| 验证 | 实际结果 |
| --- | --- |
| Maven clean verify | 640 Java / 104 suites；保留603、新增37；失败/错误/跳过均0 |
| 验收门禁 / 客户端 | 170 / 33通过 |
| TypeScript / Python SDK | 各33，共66通过；真实40次草稿句柄释放、65次迟到响应、未知id、关闭与请求错误等 |
| 零费用示例 / 实际启动器 | 2 / 8通过 |
| Windows凭据原生 / 兼容 | 7 / 4及父进程清理通过 |
| 固定镜像真实Docker | 子任务和Web的本地/容器/清理6项通过；与Windows专项合计18项 |
| Linux容器特殊目录 | 两个不同且非空的真实文件先确认存在；反斜杠目录精确SKILL_PATH_DENIED，临时容器已移除 |

主入口为 `scripts/run-r3-validation.py --run-id r3-20261023-final3 --output .build-cache/R3/final3 --with-product`。专项复用 `scripts/run-r1-supplemental.py` 的固定18项语义检查，并以新runId绑定当前源码/JAR。SDK、示例、启动器和Linux目录探针均有独立 evidence_run 回执。逐份验证回执/输出/源码与实际用例身份后归档；不把日志里的PASS字样作为独立通过证明。

## 失败记录与兼容修正

早期日志保留在 `.build-cache/R3-*`，报告列出摘要，不计作最终证据。包括沙箱拒绝原子改名/junction/loopback、计数反例和JSON替身构造错误、空schema旧元数据夹具、缺item帧的Responses替身、worker空版本守卫与技能错误码兼容。注册监视器/流终态/真实文件及路径原断言保持；历史TURN user.message和标准新增补丁前缀兼容已恢复。

早期示例采集器曾使用错误输出文件名，启动器语义校验曾误按对象而非字符串清单读取；不完整采集不计通过，final3实际重执行并严格核身份。前两份完整本地回执保留原状态；最终按规范化字节重新采集全部适用证据。审查原文、原分派及R0～R2历史验收JSON原字节未改。

## 边界与接续

契约澄清不等于隔离任意违约的可信Java代码。工厂必须及时返回并清理部分构造；不合作构造/清理不能被当作已经终止。目录检查不是OS沙箱，已有R2所有权/竞态/掉电边界继续适用。技能/JSON/响应历史有明确生命周期限额，达到限额拒绝，不宣称无限会话。

本轮没有重跑浏览器矩阵；R2 WebKit问答运行时断言失败仍保留给R6，完整矩阵仍FAILED。Linux容器探针不等于原生Linux/macOS桌面或完整POSIX认证。原生Linux/macOS、Firefox、远端CI、完整发布smoke未运行。M10.5.9仍为 `LOCAL_PARTIAL_CI_PENDING`，`m11Eligible=false`，下一入口R4；R4～R6未实施范围和CI余项继续保留。无推送、发布或付费请求。

用户已有 `.agent` 扫描段及 `.opencodereview/background-R0.md` 保留在工作区，运行数据/缓存不纳入提交。
