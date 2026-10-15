# M8 核心补齐契约

日期：2026-10-15。M8.1已完成Windows本地与真实Docker验收；M8.2调度、M8.3横切插件尚未开始，M8整阶段进行中。当前API/SDK协议仍1.7；新增工作区类型和构造重载，不修改harness-api接口或JSONL格式。

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

