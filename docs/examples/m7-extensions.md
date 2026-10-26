# M7 SDK 扩展装配示例

Java宿主可显式装配；CLI已提供ecosystem.mcp/skills等可信配置入口，见[m7-cli.md](m7-cli.md)；Web扩展表单、资源/模板及显式技能根见[m1057-extensions.md](m1057-extensions.md)。以下对象加入 `HarnessSdk` 构造参数的 `List<HarnessPlugin> extensions`，原有 PolicyEngine、ApprovalService、Budget、DataSanitizer 和 CompletionEvaluator 都继续生效。

## HTTP MCP

```java
var mcp = new McpPlugin(
    "catalog",
    () -> new HttpMcpTransport(
        URI.create("https://your-trusted-server.example/mcp"),
        Duration.ofSeconds(20),
        () -> System.getenv("MCP_ACCESS_TOKEN")),
    Map.of()); // 所有发现的工具默认 SYSTEM_IMPACT，需要正常审批。
```

宿主必须明确审核服务端地址，并将模型 key、MCP token 等全部已知秘密纳入同一 DataSanitizer，覆盖服务端回显。连接只通过请求头发送 token，不把 token 当作模型参数或配置字段；不得用模型生成的地址创建连接。HTTP MCP 在宿主运行，Docker network=none 不约束它。

只有宿主确认某个远程工具确实只读时，才能把原始远程工具名映射到 `SideEffectLevel.READ_ONLY`。忽略服务端 readOnlyHint，不提供面向模型的权限覆盖入口。新连接会进行初始化和工具发现；调用失败不重试。

stdio 可使用 `new StdioMcpTransport(trustedCommand, trustedCwd, Duration.ofSeconds(20))`，在 Supplier 内处理 IOException。该进程在宿主运行且不是 OS 沙箱，只允许可信程序；模型 key 不随最小进程环境继承。

## 受控 Skills

准备环境内的目录：

```text
skills/demo/SKILL.md
skills/demo/guide.txt
skills/demo/check.sh
```

在 Docker/Linux SH 环境中装配：

```java
var skill = new SkillsPlugin(new SkillsPlugin.Spec(
    "demo",
    new ExecPath("skills/demo"),
    Map.of("guide", new ExecPath("guide.txt")),
    Map.of("check", new SkillsPlugin.Script(
        new ExecPath("check.sh"), CommandRequest.Mode.SH,
        Duration.ofSeconds(15)))));
var extensions = List.<HarnessPlugin>of(mcp, skill);
```

Windows 本地环境需显式选 CMD 或 POWERSHELL，并使用匹配的脚本文本。每个插件实例只用于一个会话/作用域；新会话需新建实例。

模型可调用 skill_demo__load 取得说明及固定脚本参数，或 skill_demo__resource(resource="guide") 读取白名单资源。skill_demo__run_check 的 path/cwd/mode/revision 必须与返回模板完全一致，再通过人工审批与持久化检查点。宿主审批时应查看实际正文和 SHA；脚本版本改变后拒绝旧授权，刷新需新建插件。

SKILL.md 是未信任 Markdown，frontmatter 中写 scripts、权限或环境变量不会增加工具与授权；不会自动执行说明里的命令。脚本执行固定快照文本，没有任意 argv/参数/环境变量接口。真实完成判定仍由宿主 CompletionEvaluator 核对文件或命令证据，不能只看模型说明。

支持范围、上限及未实现能力见 ../development/M7-contracts.md。

## 只读 Subagent

```java
var inspect = new SubagentPlugin.Profile(
    "只读探查，引用实际文件内容，不执行命令或修改文件。",
    Set.of("read_file", "list_directory"),
    new Budget(4, Duration.ofSeconds(30)),
    () -> createFreshModelClient(), // 每次新建；同样只从环境取得模型凭据。
    verifiedCosts,
    childCompletion);             // 宿主按实际子任务目标提供证据判定。
var subagents = new SubagentPlugin(Map.of("inspect", inspect), 2, 8);
```

把 subagents 放入父 HarnessSdk 的 extensions 列表，父 Budget 的步数/时长至少覆盖子配置；有费用上限时子也必须设置同币种、可靠且不高于父的费用上限。省略 childCompletion 的五参数构造默认未验证，不会仅凭模型自述完成。

模型使用 delegate_task(profile="inspect", goal="明确的只读子目标")，仍需正常审批和检查点。SDK 宿主可按精确作用域获取 SubagentService 查看状态/请求取消；它没有不经审批的 launch 方法。并发工具调度必须属于同一个已开启父 turn，普通串行 Loop 仍同步等待委派结果。子没有父 transcript、父提示钩子、写入、命令或终端；结果包含独立 childSessionId 和父作用域 Artifact 引用。

遇到未知结果先检查子日志和实际资源。子日志中未决请求必须先按已有恢复流程核实；父子写锁释放、来源吻合且子日志已结算后，才可调用 SubagentPlugin.verify 追加操作者事实。不得自动重试或把 PARTIAL 改写成 SUCCESS。
