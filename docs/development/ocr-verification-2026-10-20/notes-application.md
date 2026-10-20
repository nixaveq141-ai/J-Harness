# Application 分组核实与修复任务

日期：2026-10-20（Asia/Shanghai）。范围：harness-api、harness-cli、harness-events、harness-plugin、harness-policy、harness-testkit，共 658 条稳定编号意见。详细逐条判断见同目录 `verdicts-application.json`。

本次是源码、契约、调用链及已有测试的静态核实，没有修改产品或测试代码、运行新的产品测试、重新构建发行物、调用付费模型、推送或发布。已有本机 JAR 仅用于查看服务资源，不冒充新构建证据。accepted 表示指出的事实和修复方向获得支持；partial 只采纳 reason 明确限定的部分，尤其不能把“新增接口文档/测试”的建议解读成现有产品已出现漏洞。严重度重新按实际影响排序，不照搬 OCR 的 critical/high。

## 阶段安排与依赖

以下是本轮修复工作的切分，不重新编号既有里程碑、不改变 M10.5.9 的 LOCAL_PARTIAL_CI_PENDING 门禁，也不授权进入 M11。每一工作包实施时同步补直接回归，最后统一验证不得取代各包的就地验证。

1. **A1：执行与秘密边界（最高优先）**。先锁定不确定副作用、检查点、撤销审批、费用输入上界、事件脱敏和凭据部分生效规则。先写能够命中缺陷的受控回归，再改实现；UNKNOWN 不自动重试、补偿或删除真实凭据。
2. **A2：资源生命周期与入口配置**。在 A1 不变量下处理总线队列/串行超时、容器清理、CLI 参数及显式配置丢失、输入及异常路径回收。避免“观察失败”阻断真正资源清理；不合作线程仍如实 UNKNOWN。
3. **A3：报告、协议及存储一致性**。明确已确认/部分未知事实、完整完成证据、SDK 句柄释放、畸形事件、严格 JSON/UTF-8、路径/元数据耐久和搜索分页。依赖 A1 的状态语义，不能用默认 0/空值掩盖未知事实。
4. **A4：公共契约与证据补强**。把既有实际保证写到 API，修正版本说明、正负边界和真正的请求前缀等价断言；低风险诊断/依赖范围清理放在安全与运行修复后。不要为了契约统一引入未经需要的破坏性签名变更。
5. **A5：同一最终版本严格验证**。刷新适用的 Java、SDK、浏览器和本地/Docker/原生凭据证据；为新跨平台入口适配测试夹具并保留原断言。三平台/浏览器未执行或失败不得标通过；远端 CI 仍需本会话之外相应授权，不因本计划自动获得推送权限。

## 工作包、修复内容及验收

| repairGroup | 阶段 | 要做的具体工作 | 验收要点 |
| --- | --- | --- | --- |
| execution-invariants | A1 | 统一工具已进入主体后非成功的不确定折叠；拒绝 null checkpoint；关闭 prepared 放弃分支的结算空洞；校验 ErrorInfo/结果状态。 | 副作用工具部分失败仍需核实；不新增第二次 body；durable=false/null 决策不能执行；owner 按原调用顺序恰好结算；早期 barrier 停止不伪造已执行。 |
| policy-approval | A1 | 审批决定、截止、取消、关闭在同一状态机仲裁；paths 数组等已声明文件参数统一拒绝受保护目标；SYSTEM_IMPACT 风险描述一致。 | 双应答只有一个成功；撤销后 answer 不返回成功；已过期批准不能放行；grep 的 .env 路径不能绕过 read_file 同等保护；不把普通 URL 字段误作路径。 |
| redaction-boundaries | A1 | 广播事件与持久事件脱敏一致；补明确凭据键别名/键中已知秘密；修正流式窗口与先脱敏后截断；限制纯 List 递归。 | 同 eventId 的观察/JSONL/SDK 输出一致无哨兵；跨块/跨页/截断边界秘密前缀不泄漏；碰撞键不静默覆盖；自引用 List 有界拒绝；最终完整答复不被流式窗口截坏。 |
| credential-state | A1 | 写/读 UTF-8 字节限额一致；预检 worker 能力；设置与 vault 更新的失败/部分/未知结果对账、待清理账本和复位；明确批次冲突和大小。 | 非 ASCII 1024 字节边界；每步故障均保留旧设置/原秘密及可核实新引用状态；超时不作确定未写；不盲删补偿；无秘密进入 argv/文件/错误。原外部修改需重启边界保持。 |
| model-runtime | A1 | 费用预留的 inputCeiling 必须覆盖实际可发送请求；明确适配器单次能力、守卫结算与超时原因。 | 较低输入上限不能让超过它的请求先发后报 breach；可允许较低上限但请求必须被限制；429 唯一拥有重试，UNKNOWN 不重发；不支持能力在可行时发送事实之前拒绝。 |
| runtime-lifecycle | A2 | 生命周期观察异常不阻断 cleanup；资源逐个关闭聚合错误；锁内只封存、锁外关闭外部回调；明确 scope 视图与服务所有权。 | BUS_CLOSED/插件 start/stop/owned close 分别注入，所有其它受管资源仍关闭；原错保留；清理失败不报成功；旧引用拒绝，不能用恶意同 JVM 插件假设冒称沙箱隔离。 |
| event-bus | A2 | 有界 notify 队列；串行超时未合作任务污染后拒绝后续派发；关闭/提交竞态统一错误；append 成功与观察失败区分。 | 慢监听下队列/内存有界；不合作旧任务未退出时新 serial 不并发；close/submit 竞争无裸 RejectedExecutionException；不因观察失败重放已持久事实。 |
| cli-config | A2 | Arguments 不吞 --dry-run 等已知选项；显式缺失配置拒绝；bundle 字段按显式声明合并；base 路由；精确数字/类型诊断；REPL 单次 prompt。 | 错拼/缺值无工具/model 副作用；默认可缺配置与显式配置区分；纯 policy bundle 不清空未声明工具；高精度非整数拒绝；不加未经产品要求的凭据变量前缀限制。 |
| input-and-test-lifecycle | A2 | CLI 输入关闭状态与所有权明确；异常时先 cancel/release，再关闭等待型 executor；测试 HTTP/子进程有独立上限和清理。 | 故意令中间断言失败也有界退出；仅回收本测试进程/目录；不关闭共享 System.in 破坏后续轮次；超长输入可诊断；PID 先校验归属。 |
| stdio-sdk | A3 | session handle 显式释放；严格参数及请求 ID 错误关联；非 JSON HTTP 错误保留状态；受控 transport peer 按 method 匹配。 | 超过 32 个先开后关会话仍可继续；在途不能被误回收；approved=null/错类型不发请求；shutdown 后不解释余帧；错误 id 可对应原请求；保持 SDK 现有字符串 identifier 约定。 |
| evidence-report | A3 | 变更事实区分 SUCCESS 与部分 UNKNOWN；损坏 payload 不 NPE；状态/费用缺失保持未知；终端显示来源/时间；报告输出预算。 | UNKNOWN patch 不计作完整交付；缺费用不输出伪 0；旧终端快照明确为历史最后已知状态；引用仍可定位完整事实；上限不静默丢失证据。 |
| completion-evidence | A3 | 验收 argv/exitCode/change schema 校验；两端路径一致；实际写/命令与拒绝尝试区分；todo/plan 测试前置状态真实成立。 | 最新验证失败不能借旧成功；未执行验证不能算通过；UNKNOWN 写保持阻断；畸形日志返回未验证；先 APPROVED 再修改确实降回 DRAFT；伪证据拒绝不是因为 todo 不存在。 |
| storage-paths | A3 | 明确 artifact 字符偏移、EOF/完整性责任；版本 guard/工作区恢复；对分离检查/创建窗口做受控竞态与根身份绑定；元数据权限/耐久按平台说明。 | 短块/非 ASCII/精确边界；本地与远端版本保护一致；路径替换时不越界创建；不可用注册根可诊断维护；清理不删已有用户文件；父目录 force 不支持不能仍冒称掉电耐久。 |
| json-protocol | A3 | UTF-8 严格解码、对象空 key 兼容、递归总节点/深度限额、第一方事件等级表及损坏事件明确拒绝。 | 非法编码不被替换后接受；Java 环/DAG 有界；合法外部 JSON 空 key 可按定义往返；未知插件事件不一刀切禁止；错误事件不被跳过后当已结算。 |
| dry-run-parity | A3 | 预览与真实管线共享取消/schema/秘密参数拒绝次序；预期冲突转工具级反馈；策略、ASK、仅预览状态明确区分。 | 可预览与不可预览均覆盖；dry-run 无 API key/model/进程/写副作用；preview 冲突可纠正；ASK 仅显示需审批不自动授权，也不为只读预览强求真实审批。 |
| search-pagination | A3 | 搜索与列表分页断言真实下一页、快照、scope 和 cursor 失效；rebuild 正例/冲突、256/257 边界。 | 每页 HTTP/schema 正确、无重复/遗漏且终止；cursor 跨 profile/view/limit/session 范围拒绝；授权 UNKNOWN 会话正文可只读检索，不误当工具重放。 |
| projection-performance | A3 | reader.events 在稳定节点复用快照；测量长会话 uncertainCalls 重扫，再决定不可变投影派生缓存。 | 输出事实/顺序不变；写入后刷新；提供事件规模与耗时/内存数据，不凭 O(n) 推导已违反性能门槛。 |
| api-contract | A4 | 补当前非空、所有权、线程模型、快照、精确 scope、失败/取消/UNKNOWN、prepare/barrier 及版本说明；仅必需处加守卫。 | 文档与当前生产调用及 M1/M8/M10.5 合同一致；保留兼容构造/默认 fail closed；不能要求重建 UNKNOWN 请求本身被禁止（诊断需要），禁止的是未经核实重新发送。 |
| recovery-replay-tests | A4（回归随所属实现） | 真实 request 前缀与 wire 完整等价、两边请求数一致；故障注入确实命中；恢复/核实无重放；旧句柄/Skills/MCP 拒绝码。 | 正负对照、精确 code/关联 id；不能在持独占 writer 时误把锁失败当语义拒绝；无新 tool.intent/body/model send；合法事件与线上参数逐项相等。 |
| build-and-test-maintenance | A4 | 限制可分发 third-party 输入；审慎收缩 testkit 依赖；测试诊断/共享纯 helper 与源快照证明优化。 | 构建包内法律材料/清单 SHA 不丢；不改历史证据字节；依赖范围改变后实际下游编译验证；不把错误码/数量断言机械镜像成冗余测试。 |
| platform-probes | A5 | 适配新三平台夹具路径/命令/uid 权限；前后流式制品摘要；原子且含失败的报告；准确 owner 清理及有界管道排空。 | 同一最终制品/源码真实运行；Windows 中文/空格、macOS 临时路径别名、Linux nobody bind 权限不靠削弱产品保护；不加 skip 冒充通过；证据目录保留与受管资源释放分清。 |

## 代表性误报与保留边界

- **OCR-0328（原 critical）拒绝。** `NativeCredentials` 的 `secret_password_clear_sync` 调用顺序符合 [GNOME 官方声明](https://gnome.pages.gitlab.gnome.org/libsecret/func.password_clear_sync.html)：clear 没有 store 的 collection 参数。添加该参数反而会制造 ABI 错位。
- **OCR-1033（原 critical）拒绝。** `sdk/tests/run.mjs` 第 9/35 行生成并显式覆盖合成密钥，`SdkTransportPeer` 的 stderr 输出用于 TS/Python 验证诊断流不泄漏到 API；不是继承真实用户凭据的调用链。
- **OCR-0868 拒绝。** 现有源码已经读取两页，并直接断言拼接结果等于完整脱敏文本；不能只看后面的 contains(secret) 就称全测试恒真。
- **OCR-0850、0857、1052 拒绝。** SHA assertEquals 参数原本正确；持锁延迟 owner 是故意控制竞争；owner 线程写事实是 M8 明确合同，不是可以删除的“实现细节”。
- **OCR-0351、0427 拒绝。** M10.5.4 已写明原生可用性缓存与外部改凭据需重启，不能把历史明确边界当成未实现的热吊销义务。
- **OCR-0231、0366、2450 拒绝相应主张。** M8 规定只读 UNKNOWN 也阻断后续；根锚定检索规则不是绝对主机路径；用户显式 export 目的地允许根外文件。不能以修复之名放松门禁或删除合法功能。
- **OCR-0159、0169 等信任边界推断拒绝。** 同 JVM Java 插件是可信宿主扩展，公开构造器/策略替换不是防恶意代码的安全沙箱。保护管线强制不变量与改善契约是合理工作，不能把任意恶意 Java 代码能力当作新增漏洞。
- **部分采纳不等于原方案照搬。** 例如费用应约束实际输入，不必一律 inputCeiling>=contextTokens；凭据写超时不得盲删补偿；流式脱敏不得先切秘密再尝试完整匹配；报告缺费用不得默认零；清理失败不得吞掉后标通过。

本组无 P0 条目；P1 是重审后需要优先修的实际状态/秘密/生命周期/费用边界。P2 包括功能与关键回归，P3 为文档、诊断或可选维护。所有状态都是核实结论，不代表修复已实现或验收已通过。
