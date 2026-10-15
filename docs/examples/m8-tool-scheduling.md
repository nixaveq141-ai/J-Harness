# M8.2 只读工具调度

默认串行。宿主显式设置顶层toolScheduling可启用有界并行，适用于经典CLI、生态CLI/serve。示例见 [m8-scheduling-config.json](m8-scheduling-config.json)，模型地址/名称为占位值，key仅从环境读取。

```json
{"toolScheduling":{"mode":"readonly-parallel","maxConcurrency":4,"tools":["list_directory","read_file","search_files","grep"],"timeoutSeconds":30}}
```

mode可为serial（不接受额外调度字段）或readonly-parallel。并发上限1～8，宿主工具白名单最多32项，超时1～60秒；maxConcurrency=1实际串行。省略白名单时使用上述四个内置读取工具，非默认MCP/第三方工具必须由宿主明确审核并列名；模型READ_ONLY声明或服务端提示不授予并行权限。命名Profile/Preset settings按现有整对象覆盖配置。

SDK使用`new SdkConfiguration(prompt, tools, parameters, new ToolSchedulingOptions(concurrency, trustedTools, timeout))`；原三参数构造保持串行。配置和实际调度决定写入sdk.turn.configured/config.selected及tool.scheduled，包含模式、并发上限、原因和时限。API/SDK版本1.8，日志格式/schema仍1；子SDK独立装配，默认串行。

只有“白名单内 + 注册READ_ONLY + 有效策略直接ALLOW”的调用并行。准备、Schema/参数拦截、策略/审批、intent/checkpoint仍串行。ASK、写入/命令、白名单外调用是屏障，等待之前读取全部结算再继续。原pipeline不支持prepare或dry-run包装时降为串行，绝不绕过预览/审批。没有无界任务队列。

tool.completed和模型tool消息按原call顺序聚合，executionFinishedAt保留实际执行结束时间，不能把日志顺序当作主体执行顺序。普通读取失败返回给模型；取消、超时、UNKNOWN或检查点失败停止后续调用。不合作线程中断后最多共享两秒等待，仍未退出记录UNKNOWN并拒绝新batch；晚到结果不追加第二次结算。取消任务可以同时保留部分副作用待核实，不能因此自动重放。

宿主启用工具并行时，工具和TOOL_EXECUTION/AFTER_TOOL拦截器必须线程安全且符合只读契约。框架不能隔离任意Java插件、强杀线程或撤回已发远程请求；UNKNOWN须外部核实。文件版本缓存支持并发记录，但写入仍要求父方自己的当前依据。

可替换入口是ToolScheduler服务、ToolSchedulingPlugin以及StandardExtensionPoints.TOOL_BATCH。旧ToolPipeline无需实现prepare，继续串行；新PreparedToolCall只允许主体run进入工作线程，finish由调用线程执行并防止重复/虚假SUCCESS。自定义调度器须保持同一管线、作用域、原call顺序和权限语义。

本切片不实现自动重试或限流；它们属于M8.3。实际验证和平台边界见 [M8验收](../testing/M8-validation.md)。
