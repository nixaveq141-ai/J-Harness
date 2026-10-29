# 四项扩展验收接续

日期：2026-10-29。用户确认只验证DeepSeek，新增预算人民币5元；耗费较大的横测推迟到最终横测。原2元账本12次/预留1.769472元不改，不进行其他厂商验证或实体OS验收。

## 原生依赖取证

新增有界离线审计器与3项反例测试，不加载/执行二进制。47个原生资源均绑定既有许可库存的精确JAR摘要，并记录资源SHA256。其中pty4j的23个资源与官方固定提交`b7554d649f6374183040e6554942b6d5b028b5e5`的`os/`文件逐字节一致。[结果](M10-native-byte-provenance-2026-10-29.json)保存完整路径/摘要/官方URL。

官方[构建脚本](https://raw.githubusercontent.com/JetBrains/pty4j/b7554d649f6374183040e6554942b6d5b028b5e5/build.gradle.kts)将`os/`收录进JAR原生目录，故此次验证证明分发字节一致。它没有给出每个ConPTY/OpenConsole/WinPTY/JNA二进制的可核验构建证明；PE内嵌版本仅作候选线索。因此状态明确为`BUILD_PROVENANCE_INCOMPLETE`，OS/CPE仍`NOT_ASSESSED`。未把Maven/npm OSV门禁升级称为完整CPE认证。

重跑：`python docs/testing/tools/native-provenance-audit.py --verify-pty-upstream --output <新的报告路径>`。输出不覆盖原报告，官方文件不匹配直接拒绝。

## 历史macOS异常

重新读取40195474698的原始Mac ZIP：SHA256 `099baa31c47228d5543c4955c79c7cd1a96a1efa4c1907fe6c9f530d27d712f5`；原`webkit/models.log`摘要`6d4f7bcace972b34c550301f0f86dbc4e5c3013880dd463ab46492a29d2978df`。前两项通过，随后`models.mjs:89:193`点击“发送后续消息”时按钮持续disabled、30秒超时，未发生外层命令超时或输出溢出。

原包没有该失败瞬间的输入长度、所有禁用谓词、前端代次及后端任务状态的联合快照；没有足以重建因果关系的录制。后续诊断专项和当前完整CI通过，只证明未复现。不能把另一轮配置busy门禁、PID文件或WebKit导航异常的已知原因套用到此处。结论仍为`ROOT_CAUSE_UNDETERMINED`，不是声称已修复。原ZIP留在本地历史备份；以后相同失败必须保留有界白名单诊断，取得可控旧策略反例和修正后同条件通过才能关闭根因。

## 新DeepSeek代码任务

独立新账本身份`deepseek-fresh-code-2026-10-29-CNY5`，累计最多6次请求，每次预留0.155648元，最多0.933888元；无自动重试、不退款未知请求。按[官方人民币高峰价](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)输入2元/百万token、输出8元/百万token，输出上限2048、输入按69632保守预留。仅`deepseek-flash`、只读工具，真实key只在HTTP中继；CLI子进程不持有厂商key。

真实CLI两次HTTP200，唯一read_file读取固定需求、COMPLETED后生成新`Calculator`，包括整数运算、CSV解析/输入拒绝/long溢出。预留上界0.311296元，不是厂商账单。原始提取器因Java围栏后附说明而拒绝；保留原INCOMPLETE回执，修正为只提取唯一Java围栏、拒绝多围栏后用同一原JSONL离线恢复，无额外请求。来源见[脱敏回执](fixtures/deepseek-fresh-calculator-2026-10-29.provenance.json)。原日志/JSONL/账本仅在本地缓存，不提交。

新独立oracle包含220个CSV案例（其中200个固定随机复合输入）及原算术/隔离检查；还构造可编译错误实现，必须抵达行为断言失败。只在固定Docker镜像中编译/执行，禁网、只读宿主输入、非root、降权、内存/CPU/PID/时间/日志限额及自有清理全部保留。新专项[40844744965](https://github.com/nixaveq141-ai/J-Harness/actions/runs/40844744965)绑定`33c1eb4`已通过，原ZIP/GitHub摘要/5项源码输入/正反实现/隔离与清理已独立核对；4个源码/缺对照/可写挂载/伪日志篡改反例全部拒绝，见[验收记录](M10-fresh-code-acceptance-2026-10-29.json)。这是一个新合成编码任务的真实CLI读需求→新源码→Docker编译/功能oracle闭环；未证明所有编码任务，也不覆盖模型驱动写文件工具的全部行为。旧735回执的622项输入重算均未改变，新专项自有输入绑定新提交。

## 性能

按用户指示为`DEFERRED_TO_FINAL_BENCHMARK`。已写[最终横测方案](final-horizontal-benchmark-plan.md)，明确三平台、负载/并发、正确性、20轮采样、p95/RSS及失败保留；新增阈值待基线后确认。既有受控测量与目录两秒契约保留，不声称完成跨平台SLA。

原CLI事实/失败与恢复回执/新旧账本及CI原包已[本地成包](M10-fresh-code-local-backup-2026-10-29.json)，22739字节、13成员，逐成员摘要及密钥扫描通过。没有异地上传。
