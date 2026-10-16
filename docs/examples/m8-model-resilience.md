# M8.3 模型限流与重试

`modelResilience` 是宿主配置，可用于经典 CLI、生态 CLI/serve；Profile/Preset settings 按整对象覆盖。默认关闭。将 [配置片段](m8-model-resilience.json) 合并到已有模型配置，不含凭据；`doctor` 不发送请求。适用 API/SDK 1.9，JSONL/schema 仍为1。

```json
{
  "modelResilience": {
    "enabled": true,
    "maxAttempts": 3,
    "backoffMillis": 100,
    "totalTimeoutMillis": 30000,
    "minIntervalMillis": 250,
    "maxWaiters": 0
  }
}
```

maxAttempts 含首次，范围1～4；backoffMillis 0～10000，指数递增且封顶10秒。totalTimeoutMillis 50～300000，包含服务内排队、限流、退避和发送；同时遵守整轮时限。minIntervalMillis 0～60000，限制每次尝试开始的最短间隔；maxWaiters 0～32，限制等待调用者数，满额立即失败。每个插件实例仅一个执行中的请求序列，无后台线程；SDK 本身也按会话串行，不同SDK/子任务不共享限流配额。

仅完整收到的非流式 HTTP 429 可重试。传输未知、超时、SSE断流、5xx、协议错误不重试；工具调用不重试。标准插件使用适配器 single-attempt 入口，关闭旧内层429循环；禁用插件时保留旧适配器行为，CLI原本的适配器重试配置仍为0。可信自定义适配器须显式实现单次发送契约，缺少契约则拒绝执行。流式包装器只消费一次流，非流式降级则转发单次入口。

每次尝试重新准备、预留费用、写入可重建请求并checkpoint，之后才发送。429费用仍按保守上界保留，下一次尝试再次检查费用余额。model.rejected引用精确请求事件且先flush；取消/未知结果留下未决事实，需核实后才可续跑。人工verify-request只结算指定请求，不能用旧429的事件ID清除同一步中新的未知请求。

事件包含 model.attempt.started / model.rate.waiting / model.retry.scheduled / model.execution.stopped，以及有精确请求引用的 model.rejected。标准插件经 MODEL_EXECUTION 拦截链和 ModelExecution 服务装配，宿主可提供替换实现；禁用该插件同时启用配置会在CLI装配前失败。标准实现的等待可取消，卸载后已缓存扩展引用也失效；任意自定义Java代码仍需遵守取消和线程安全约定。

SDK：保留旧三/四参数构造，新增第五参数：

```java
new SdkConfiguration(prompt, tools, parameters, ToolSchedulingOptions.serial(),
    new ModelResilienceOptions(true, 3, 100, 30000, 250, 0));
```

已有 BEFORE_MODEL 压缩、整轮Budget时限和 ApiCostGuard 保持独立；子SDK默认关闭，不继承父方重试配置。测试无需付费模型，验收记录见 [M8验证](../testing/M8-validation.md)。
