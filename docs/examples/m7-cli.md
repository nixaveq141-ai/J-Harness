# M7 CLI 生态与 Web 启动

配置示例为 [m7-config.json](m7-config.json)。先将 baseUrl/model 改为已选服务商，并在宿主环境设置 JHARNESS_API_KEY；不要写入秘密值。示例不会自行创建 key、启动 MCP 或发送请求。自动化验收使用本机替身服务，没有付费请求。

```powershell
.\jharness.cmd --config docs/examples/m7-config.json run "读取 pom.xml" --require-read pom.xml
.\jharness.cmd --config docs/examples/m7-config.json repl
.\jharness.cmd --config docs/examples/m7-config.json serve --require-read pom.xml
```

serve 前另设 JHARNESS_WEB_TOKEN（32～256 位字母、数字、下划线、短横线）。输出 WEB 地址和固定 Profile default，HTTP 调用见 [Web 示例](m7-web.md)，POST 的 profile 使用 default。端口 0 由系统分配；:quit 或标准输入 EOF 关闭。它是本机单操作者后端，没有前端页面。CLI 中的验收条件同时用于该宿主的新 Web 任务。

ecosystem 配置启用同一 HarnessSdk 装配；缺省无 ecosystem 时沿用原 CLI。模型路线、命名配置组合、系统提示、参数、enabledTools、只读策略、压缩与显式证据条件沿用现有配置；不接受自定义 enabledPlugins，改由 ecosystem 选择插件。enabledTools 是精确白名单，省略时允许已注册工具；只读子任务仍受额外限制。默认串行执行，config.selected 记录该决定，不自动并行模型返回的一组调用。

Docker 替换 environment 为以下结构，填写真实绝对路径；镜像须事先拉取并固定摘要。workerJar 是当前打包的 CLI JAR。不接受模型自行指定镜像、挂载和命令；失败不回退本地。Docker 工具仅包含受支持能力，没有容器 PTY。

```json
{"kind":"docker","dockerExecutable":"C:/absolute/path/docker.exe","image":"eclipse-temurin@sha256:ccbad9e7cb84c27092b12c40a4143e5733e050db9c3784d84f632dcf423fb2ef","workerJar":"D:/absolute/path/harness-cli-0.1.0-SNAPSHOT.jar"}
```

mcp 为最多 8 项可信服务器配置。例如 HTTP 项 `{"id":"docs","transport":"http","url":"https://trusted.example.test/mcp","tokenEnv":"DOCS_MCP_TOKEN","timeoutSeconds":15,"effects":{}}`；stdio 项 `{"id":"local","transport":"stdio","command":["C:/absolute/path/server.exe"],"cwd":"D:/approved/server","timeoutSeconds":15,"effects":{}}`。effects 按原始工具名显式标注 READ_ONLY 等副作用等级，默认 SYSTEM_IMPACT；服务端提示不能改变授权。stdio/HTTP 在宿主运行，不受 Docker network=none 约束。命令和地址必须由操作者审核。

skills 项例如 `{"id":"guide","directory":"skills/guide","resources":{"reference":"reference.md"},"scripts":{"check":{"path":"check.sh","mode":"SH","timeoutSeconds":10}}}`，资源和脚本路径相对该技能目录；全部文件必须位于当前执行环境内。脚本无任意 argv 和环境注入，始终逐次审批并校验快照版本。

subagents 是固定 Profile 映射。每个新子任务有独立模型、作用域和日志；工具仅限只读探查，requireRead 列表是子完成条件，空条件不算完成。预算不超过父配置；子关闭不关闭父审批服务。planning=true 注册 Todo/Plan 工具；模型更新也经过审批和 durable intent。webSources 例如 `{"reference":"https://trusted.example.test/reference"}`，注册按别名抓取的 web_fetch；不注册任意 URL 访问或搜索提供方。

会话维护无需模型 key：

```powershell
.\jharness.cmd sessions search --session SESSION --text "关键内容" --limit 20
.\jharness.cmd sessions search --session SESSION --text "关键内容" --rebuild-index
.\jharness.cmd sessions compare --session SESSION --other-session OTHER
.\jharness.cmd sessions archive --session SESSION --reason "任务已交付"
.\jharness.cmd sessions verify-environment --session SESSION --environment-id ENV --reason "已检查真实容器及资源"
.\jharness.cmd sessions verify-subagent --session SESSION --task-id TASK --reason "已确认子任务终止并核对请求"
.\jharness.cmd sessions verify-web --session TASK --web-catalog web-catalog --reason "已停止旧宿主并核实资源"
```

Web 核实必须先停止旧宿主/工厂并检查实际资源，再处理 SDK 日志中的未决请求/环境/子任务。核实只追加事实，不重放、不伪造 COMPLETED；重启后旧任务 phase=VERIFIED，新任务才可使用释放的绑定。不能在线核实正在占用的 catalog。没有 SDK 日志时，终止证明依赖操作者的外部检查，日志不能证明任意线程已停止。

search 是单会话完整 payload 的大小写不敏感子串检索，索引可增量重建；不是 FTS5、模糊分词或全局排名。archive 只追加归档事实、保留日志和产物，并禁止续跑；需要新任务可在归档事件之前的检查点 fork。compare 展示事件公共前缀与状态/计数，不做语义合并。
