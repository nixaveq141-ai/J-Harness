# M7 本机 Web API 装配

Java 21 宿主依赖 `io.jharness:harness-web:0.1.0-SNAPSHOT`。这是嵌入式入口；CLI `serve` 的同等装配见 [m7-cli.md](m7-cli.md)。宿主需要实现可信 SDK 工厂，不能把工作区或模型工厂交给请求者配置。

```java
// workspace、sessions、artifactsPath、modelFactory、policy、budget、completion、costs
// 都由已有 Java 宿主预先选定。modelFactory 每次返回独立模型客户端。
var webProfile = new HarnessWebServer.Profile("project-a", sessions,
        (sessionId, approvals, sanitizer) -> {
            var environment = new LocalExecutionEnvironment("web-local", workspace, List.of());
            LlmClient model = null;
            try {
                model = modelFactory.get();
                return new HarnessSdk(sessionId, "project-a", sessions, environment, model,
                        new LocalArtifactStore(artifactsPath, sanitizer), sanitizer, policy,
                        approvals, budget, completion, costs, List.of());
            } catch (Exception ex) {
                try { if (model != null) model.close(); }
                finally { environment.close(); }
                throw ex;
            }
        });
try (var server = new HarnessWebServer(0, System.getenv("JHARNESS_WEB_TOKEN"),
        Map.of("project-a", webProfile), webCatalogRepository, "web-catalog",
        hostSanitizer, 1, 32, Duration.ofSeconds(30))) {
    System.out.println(server.address()); // 只打印地址，不打印 token。
    hostShutdownSignal.await();
}
```

token 至少 32 个字符，字符集为字母、数字、`_`、`-`，从秘密管理器生成并设置环境变量。hostSanitizer 包含全部模型/MCP 等已知秘密；工厂使用收到的 sanitizer 同时创建 SDK 与产物存储，它另外覆盖 Web token。仓库、工作区绑定与 catalog 需要稳定保存。每个 Profile 的绑定必须对应唯一真实目录；同目录别名也使用相同绑定。

请求均携带 `Authorization: Bearer <环境变量中的 token>`，正文 Content-Type 必须精确为 application/json，并带 Content-Length。不要把 token 放到 URL 或日志。以下为 HTTP 形状，具体客户端从环境读取 header；不能把占位值作为真实凭据。

```http
POST /v1/tasks
Content-Type: application/json
Authorization: Bearer <token>

{"profile":"project-a","goal":"读取输入并按验收条件完成任务","requestId":"client-request-001"}
```

保存返回的 taskId；超时/断线后先用同一 requestId、原目标和 Profile 查询原提交，不改 ID 自动重试。服务已经重启时也不会重放原任务，UNKNOWN 需宿主核实，不能靠换 catalog 继续。

1. GET /v1/tasks/TASK 查看状态。
2. GET /v1/tasks/TASK/events?limit=50 查询事件，以返回的 next 作为 after 续读。
3. 或 GET 同一路径并带 Accept: text/event-stream。每窗最多五秒，下一窗使用 Last-Event-ID，不能再同时传 after；重连仅续读事件。
4. GET /v1/tasks/TASK/approvals 获取待决项，审查工具与精确参数后 POST /v1/tasks/TASK/approvals/APPROVAL，正文 `{"approved":true,"reason":"已核对参数与范围","callId":"原 callId"}`。拒绝时 approved=false。
5. POST /v1/tasks/TASK/cancel，正文 `{}`，请求取消。
6. GET /v1/tasks/TASK/diff 查看已记录的 git_diff 结果；无记录返回 404，大结果保留省略标记和来源。

phase=FINISHED 表示执行与关闭已确认结束；UNKNOWN 即使 status 是 COMPLETED 也不能当作安全收尾。完整审计/请求重建使用宿主 JSONL，Web 大事件会省略 payload。详细限制见 [M7 契约](../development/M7-contracts.md)。真实 HTTP/SSE 端到端用例见 M7WebTest。

