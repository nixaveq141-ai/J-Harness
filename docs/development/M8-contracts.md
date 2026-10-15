# M8 核心补齐契约

日期：2026-10-15。M8.1已完成Windows本地与真实Docker验收；M8.2调度已完成本地与真实Docker验收，M8.3横切插件尚未开始，M8整阶段进行中。当前API/SDK协议1.8，JSONL格式/schemaVersion仍1。M8.1历史切片使用API/SDK1.7。

## M8.1：宿主检索过滤（FR-07.3）

新增CLI顶层 searchFilter 配置，由命名Profile/Preset settings沿用整对象覆盖规则：useDefaultExcludes、useGitignore（均默认true）、rules（默认空列表）。配置严格拒绝额外字段、错误类型和不支持的规则；模型工具不接受修改过滤配置的参数。

默认排除target/node_modules的任意层级，名称沿用大小写不敏感；.git/.jharness为独立不可取消的路径保护。优先级为默认排除→根目录.gitignore→宿主rules，每层规则有序、最后匹配生效；用户可关闭默认排除或.gitignore层。用户rules最多128条，每条256字符、总计16384字符；根.gitignore有效规则同样有界，超限明确SEARCH_FILTER_LIMIT，不截断后继续检索。

支持根锚定/、目录尾斜杠、*、?、**、**/和!否定；不含斜杠的规则匹配任意层级名称，含斜杠的规则相对根。目录规则匹配目录及后代。同一规则序列在每层祖先应用，不能重新包含已被剪枝的父目录内文件：需先显式重新包含父目录。注释/空行忽略；不支持反斜杠转义、字符组、双重!、路径穿越、驱动器/绝对主机路径、控制字符及首尾空白规则，不猜测Git完整wildmatch。不支持的.gitignore规则也明确诊断，不默默放宽过滤。

list_directory、search_files和grep使用相同规则；显式list一个已过滤目录返回空列表，grep显式路径也过滤。read_file/写入/命令不因检索过滤而改变授权，隐藏路径不是秘密隔离措施。祖先路径、链接/junction、保留名及元数据保护先于过滤，否定规则不能覆盖安全检查。扫描期限包含读取规则；每次操作读取当前根.gitignore，不提供嵌套.gitignore或自动监视服务。

LocalWorkspace和子任务isolatedView保留同一不可变宿主配置。CLI经典入口与生态入口/serve使用同一配置；SDK宿主通过LocalExecutionEnvironment/DockerExecutionEnvironment构造重载传入。Docker把可信配置通过有界stdin RPC交给worker，worker执行相同LocalWorkspace过滤；非默认配置须经filter-info握手和响应配置回显校验，旧worker不接受时明确失败并清理，不回退宿主或静默忽略配置。

环境descriptor只在非默认配置时追加searchFilter，默认descriptor保持兼容；SDK恢复时非默认过滤变更触发既有ENVIRONMENT_MISMATCH。CLI config.selected保存解析配置。规则来源不改变工具权限或副作用等级。

## 验收与后续边界

M8.1需有配置优先级/否定/目录/中文空格/上限/非法配置、直接读取与检索差异、metadata/链接拒绝、isolatedView、CLI与worker装配测试及实际本地/Docker对照。原有1000条目性能、文件版本保护、审批、请求重建持续回归。证据记录于M8-validation.md及独立JSON，不覆盖M7历史快照。

本切片不实现自动只读工具并行、限流/重试插件、前端、Desktop或发布CI。M8.1完成不表示M8整阶段完成。

## M8.2：有界只读调度（FR-25.1/25.3，已验收）

公共API/SDK升级1.8；JSONL/schemaVersion仍1。ToolBatchExecution扩展点和ToolScheduler服务可替换，ToolPipeline新增默认prepare方法，旧实现继续串行；SDK原三参数SdkConfiguration构造保留并默认串行。

宿主顶层toolScheduling默认serial；显式readonly-parallel才并行，可配置maxConcurrency 1～8、tools白名单最多32项、timeoutSeconds 1～60。默认并行白名单仅list_directory/read_file/search_files/grep。注册工具READ_ONLY、宿主白名单、有效策略ALLOW必须同时成立，模型声明或MCP提示不足以授权。插件被禁用时不能静默启用并行。经典CLI、SDK与生态CLI/serve使用同一装配；子SDK默认串行，独立配置不自动继承。

准备/参数拦截/Schema/策略/审批/intent与checkpoint保持原顺序，在调用线程串行完成；仅执行主体及TOOL_EXECUTION/AFTER_TOOL包装可进入有界工作线程。启用白名单的宿主须保证该工具及相关执行/结果拦截器线程安全、不会借只读声明写入环境。所有默认管线tool.completed由调用线程按原call顺序结算，模型消息同序；executionFinishedAt记录主体实际结束时刻，不能把记录顺序冒充执行先后。仍只有一个会话writer及独占写锁，不另开写者。

最多一个组的maxConcurrency个线程，无无界队列。同一作用域只允许一个batch。写入/命令/白名单外工具、非只读注册、ASK人工审批先等待前面读取结束；DENY/准备失败不会进入主体。普通已知只读失败汇总后交给模型；UNKNOWN/取消/超时/不确定副作用/检查点失败阻止后续调用和模型请求。任务取消状态优先保留，已有部分副作用标记仍需恢复核实。不做重试。

每个并行读取有宿主时限；取消/超时/卸载触发中断，最多共享两秒等待退出。已确认的实际结果照实保留；仍未退出记录UNKNOWN并拒绝新batch，不能伪造CANCELLED或清理成功。不合作Java线程不能强杀，SDK冷恢复需人工核实；晚到结果不追加第二次tool.completed。默认管线线程只返回结果，不从worker结算日志；任意自定义Java插件自发记录事件仍是可信宿主责任。

tool.scheduled记录模式、原因、并发上限与时限，tool.batch.stopped记录停止原因。工具实现/注册在执行前再次检查，单次body与单次结算均防重入。LocalWorkspace观察版本缓存改为并发Map；写屏障仍不允许子任务读取替代父方版本依据。

dry-run包装不暴露prepare能力，必须降为原串行预览管线；不能为并行而绕过dry-run。后续M8.3另做限流/重试，当前不改变网络未知结果不重放规则。
