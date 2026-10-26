# J-Harness 开发需求规格说明书（SRS）

> 需求基线说明：保留本版需求与原范围决定，不把规划文本当作已交付事实。2026-10-26当前实现/延期/证据状态见[统一索引](../development/current-state.md)。

> 文档版本：1.0（需求基线草案）  
> 制定日期：2026-10-13  
> 项目定位：基于 Java 的本地 Coding Agent 与可扩展 Agent Harness  
> 官方参考：https://github.com/deepseek-ai/deepseek-harness  
> 官方架构：https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/architecture.md

## 0. 文档约定

- **MUST**：相应版本验收时必须具备；**SHOULD**：推荐实现；**MAY**：可选。
- 优先级：P0（V1 必须）、P1（V2 核心）、P2（V3 扩展）。
- 状态：本文件是待评审的开发需求基线；具体 API、性能目标与版本依赖均需在实施时验证。
- “对标 DeepSeek Harness”表示目标功能和架构理念对齐，不表示二进制兼容、插件格式兼容或官方认证。

## 1. 项目背景和目标

### 1.1 背景

传统 LLM 聊天界面主要输出文本，不能可靠地完成软件项目中的“观察环境—行动—获得反馈—修正行动”闭环。J-Harness 需要在用户授权的本地工作区内，将模型推理与文件系统、命令执行、代码检索、测试验证连接起来，逐步扩展为具备插件、事件、持久化与多种运行环境的 Agent Harness。

### 1.2 项目目标

**G1—本地操作：** 发现、读取、搜索、创建和精确修改用户授权工作区内的文件。  
**G2—自主开发：** 在有限步数、时间和预算约束下，调用工具完成代码编写、编译、测试、错误修复和结果说明。  
**G3—过程可控：** 用户可看到所有工具请求与执行结果，批准或拒绝敏感操作，随时取消任务。  
**G4—状态可靠：** 工具调用及完成状态有可审计记录；崩溃恢复不会盲目重放具有副作用的操作。  
**G5—架构扩展：** 模型、工具、存储、执行环境和前端可替换；最终支持插件、MCP、Skills、子 Agent 和远程沙箱。

### 1.3 目标用户与平台

- 首要用户：在 Windows 10/11 上使用 Java、Maven、Git、命令行开发软件的个人开发者。
- 扩展用户：需要运行 Coding Agent 的开发团队及工具集成方。
- 首发运行环境：Windows 10/11 + JDK 21 LTS（推荐）；PowerShell/CMD；Git；Maven Wrapper 优先。
- 后续兼容：Linux/macOS、WSL、Docker、远程执行容器。
- 首版交互方式：终端 CLI，不依赖 Web 前端。

### 1.4 非目标

V1 不实现任意桌面图形界面点击、浏览器自动化、无人值守系统管理、默认全盘文件访问、多 Agent 群体协作、与原版 Cordis 插件直接兼容，也不承诺在无沙箱的宿主操作系统上安全运行不可信命令。

## 2. 用户角色、任务与业务流程

### 2.1 角色

- **用户**：指定工作区、描述开发任务、批准敏感操作、检查变更、取消任务。
- **Agent**：规划下一步工具调用、阅读反馈、迭代修改、形成结论。
- **模型提供者**：提供聊天、工具调用、流式输出及使用量等能力。
- **工具运行时**：校验参数，执行授权操作并将结构化结果反馈给模型。
- **审计/策略组件**：记录决策与操作，阻止超出边界的动作。

### 2.2 关键用户故事

| ID | 用户故事 | 版本 |
|---|---|---|
| US-01 | 我想选择本地项目并要求 Agent 概览目录和技术栈 | V1 |
| US-02 | 我想让 Agent 新建 Java 类并修改现有方法 | V1 |
| US-03 | 我想让 Agent 执行 Maven 测试，读取失败日志并修复 | V1 |
| US-04 | 我想在运行危险命令前看到原因和影响并决定放行 | V1 |
| US-05 | 我想看到每次文件修改的 Diff，并知道哪些测试被执行 | V1 |
| US-06 | 我想取消执行中的任务且不留下失控的子进程 | V1 |
| US-07 | 我想关闭程序后恢复历史会话和任务轨迹 | V2 |
| US-08 | 我想切换模型服务而不修改 Agent Loop | V2 |
| US-09 | 我想安装工具插件或连接 MCP Server | V3 |
| US-10 | 我想通过 Web/SDK/桌面端驱动相同的 Agent Runtime | V3 |

### 2.3 核心流程

1. 用户在终端启动应用并选定工作区。
2. 系统加载配置、模型连接、工具定义、工作区策略与会话。
3. 用户提出任务；Agent 获取项目上下文并调用模型。
4. 模型返回文本或结构化 Tool Calls；系统校验参数和权限。
5. 可执行操作被执行，结果转换为统一 ToolResult，加入会话上下文。
6. Agent 按反馈继续调用模型，直到完成、遇到无法解决的错误、被取消或达到限制。
7. 系统展示摘要、变更文件、Diff、测试结果和未完成事项。

## 3. 总体架构需求

### 3.1 分层

```
CLI / Web / Desktop / SDK
           |
      Application API
           |
 Agent Runtime: AgentLoop / Turn / Step / Context / Events / Session
           |
 Tool Registry -- Approval Policy -- Provider Adapter
           |
 Execution Environment: Filesystem / Command / Terminal / Git / Sandbox
           |
 Local Workspace / Docker / Remote

Model Provider (DeepSeek / OpenAI-compatible / local) -> Agent Runtime
```

必须保证：UI 不直接操作文件；LLM Adapter 不直接执行 Shell；Agent Loop 仅依赖 ToolExecutor 与 LlmClient 抽象；Workspace 只表示项目边界，不能单独充当 OS 级安全沙箱。

### 3.2 推荐 Maven 模块

```
j-harness/
  pom.xml
  harness-api/          公共模型与服务接口
  harness-agent/        Agent Loop、状态机、上下文
  harness-llm/          LLM 适配器与流式解析
  harness-tools/        工具注册、Schema、执行管线
  harness-workspace/    文件读取、编辑、目录边界
  harness-process/      命令执行、进程取消
  harness-policy/       权限、审批、审计
  harness-session/      会话事件及持久化
  harness-cli/          入口、终端交互
  harness-testkit/      Fake LLM、临时项目、集成测试
  harness-plugin/       V2 插件服务、生命周期
  harness-mcp/          V3 MCP 适配
  harness-web/          V3 Web/API
```

启动开发时允许以一个 Maven 工程分包实现，但模块边界和接口名称须保持稳定；模块拆分应由复杂度和测试边界驱动。

## 4. 功能需求：V1 本地 Coding Agent（P0）

### FR-01 工作区管理

- FR-01.1 用户通过 `--workspace <path>` 指定一个授权工作区；默认不开放全盘访问。
- FR-01.2 对路径做标准化和访问控制；拒绝 `../` 穿越、非授权绝对路径；必须考虑符号链接和 Windows junction/reparse point。
- FR-01.3 支持 `.gitignore` 和应用自有排除目录（如 `.git/`、`target/`、`node_modules/`）的检索过滤，用户可配置。
- FR-01.4 支持 UTF-8 为主的文本读写；二进制文件需识别并拒绝当文本覆盖；保留文件换行风格和编码策略。
- FR-01.5 访问被拒绝的路径时返回结构化错误，不泄露不必要的文件内容。

**验收：** 工作区内读写成功；对 `../secret.txt`、链接跳转工作区外、未授权绝对路径的访问均被拒绝；不因为错误路径而修改工作区外文件。

### FR-02 文件与代码工具

必须提供可被模型调用的工具：

| 工具 ID | 输入 | 输出 | 备注 |
|---|---|---|---|
| `list_directory` | path、depth、limit | 条目/类型/相对路径 | 限流、过滤 |
| `read_file` | path、startLine、endLine | 带行号的文本 | 大文件分页 |
| `search_files` | glob、limit | 路径列表 | 优先 `git ls-files` 或 NIO |
| `grep` | pattern、paths、limit | 路径、行号、片段 | 可借助 `rg`，有降级实现 |
| `write_file` | path、content、overwrite | 结果、Diff | 新建或受控覆盖 |
| `edit_file` | path、oldText、newText、expectedOccurrences | 结果、Diff | 唯一匹配/预期次数校验 |
| `apply_patch` | patchText | 文件级变化与冲突 | 可在 V1 后半期实现 |
| `git_diff` | scope | Diff | 不假设项目一定有 Git |

- FR-02.1 写入之前需检查是否产生覆盖，以及文件是否自读取以来被其他进程修改；冲突需拒绝或请求重读。
- FR-02.2 原子写入尽可能使用同目录临时文件加替换；出错时尽量不损坏原文件。
- FR-02.3 对每次编辑生成 before/after 摘要、文件级 Diff、工具事件。
- FR-02.4 编辑工具执行失败不能返回假成功。

**验收：** 给定包含编译错误的项目，Agent 可以读取、精确修改指定 Java 文件，未触碰无关文件，结果可由 `git diff` 验证。

### FR-03 命令执行与进程管理

- FR-03.1 使用 `ProcessBuilder`/受控进程服务在指定工作目录执行命令；明确区分 argv 直接执行与经 `cmd`/PowerShell 的 Shell 执行，避免不必要的注入与转义问题。
- FR-03.2 返回 `exitCode`、`stdout`、`stderr`、`durationMs`、`timedOut`、`truncated` 等字段。
- FR-03.3 并发消费 stdout/stderr，避免缓冲区阻塞；设置日志大小上限并保留截断提示。
- FR-03.4 配置超时、取消 token、最大进程数、输出上限；取消时尽可能终止进程树。
- FR-03.5 支持调用 `mvnw.cmd test`、`mvn test`、`git status` 等，不依赖全局 Maven 必须存在。
- FR-03.6 不得向模型默认暴露全部宿主环境变量；敏感变量需要隔离、脱敏与授权。

**验收：** 成功捕获构建失败日志；超时命令被终止；超长输出被截断；取消可中止测试命令并返回取消状态。

### FR-04 Agent Loop 与任务状态机

状态：`CREATED → RUNNING → WAITING_APPROVAL | EXECUTING_TOOL → RUNNING → COMPLETED | FAILED | CANCELLED | LIMIT_REACHED`。

- FR-04.1 支持模型工具调用及多步循环，单次模型结果可能包含多个工具调用。
- FR-04.2 工具调用与返回结果必须通过 callId 对应，消息协议满足模型提供者要求。
- FR-04.3 需要处理纯文本完成、格式非法、工具不存在、参数不合法、工具错误、模型错误、限流、上下文溢出和取消。
- FR-04.4 默认设最大步骤数、最大运行时间和可选 API 费用预算；达到限制时明确报告状态而非伪称完成。
- FR-04.5 V1 对多工具调用默认串行，避免并发修改冲突；只读并行在后续版本按调度策略引入。
- FR-04.6 返回用户可理解的最终结果：完成/部分完成、变更文件、执行命令、测试结论、遗留风险。

**验收：** Fake LLM 依次请求 `read_file → edit_file → execute_command`，执行顺序及 toolCallId 完全一致；失败时任务可控终止。

### FR-05 LLM 接入

- FR-05.1 定义模型无关的 `LlmClient`、`ChatRequest`、`ChatResponse`、`ToolSchema`、`ToolCall`。
- FR-05.2 首发支持一个 OpenAI-compatible API 适配器；模型名称、baseUrl、API key 经配置注入。
- FR-05.3 不假设所有兼容 API 的工具调用、流式输出、消息角色与错误格式完全相同；用适配器封装差异。
- FR-05.4 HTTP 请求支持超时、取消、有限退避重试，避免对可能已处理的非幂等动作盲目重试。
- FR-05.5 API 密钥不得写入日志和 Session；避免将本地敏感文件自动送入模型上下文。
- FR-05.6 V1 可以先采用非流式调用，但 CLI 必须展示工具执行状态；SSE 流式响应纳入 V2。

### FR-06 人工审批与安全策略

- FR-06.1 工具执行前必须经过 `PolicyEngine`，产出 `ALLOW / ASK / DENY`。
- FR-06.2 涉及工作区外访问、删除、批量重写、Git 强制重置、外部网络传输、软件安装、系统设置更改和秘密文件读取等，默认 `ASK` 或 `DENY`。
- FR-06.3 V1 可以设置白名单式的“受信任命令模板”，但不可单纯根据字符串前缀判断安全；Shell 中的链式命令/重定向/子进程可能绕过规则。
- FR-06.4 审批信息包括工具、完整参数、目标路径、潜在影响；拒绝后返回 `permission_denied` 给 Agent，不执行动作。
- FR-06.5 提供 `--dry-run` 或计划预览，支持展示预期文件变更；干运行不能执行真实有副作用的命令。
- FR-06.6 本地无沙箱模式标注为受信任项目开发场景，不宣传为安全执行不可信代码。

**验收：** 危险命令在未批准时未执行；用户拒绝后 Agent 获得结构化结果；API key 在日志中脱敏。

### FR-07 CLI 交互

建议命令：

```
jharness --workspace D:\\projects\\demo
jharness doctor
jharness config set model deepseek-chat
jharness run "修复项目编译错误并运行测试"
jharness diff
jharness cancel
```

- V1 提供 REPL 和一次性 `run` 模式、工作区路径提示、工具调用记录、审批问答、任务取消。
- 返回退出码：0 完成；非零为错误/取消/部分完成，详细映射在 CLI 规范中定义。
- Windows 路径含空格和中文必须可正确处理。

### FR-08 最终交付报告

包含任务目标、执行摘要、修改文件列表、差异摘要、运行的测试命令、实际退出码、尚未解决的问题、未经执行的验证事项；不得把“模型声称成功”等同于“测试通过”。

## 5. 功能需求：V2 工程化 Agent Runtime（P1）

### FR-09 事件流与持久化

- 采用 Append-only SessionEvent 日志；事件至少包括 `session.created`、`turn.started`、`step.started`、`model.requested`、`assistant.message`、`tool.requested`、`approval.decided`、`tool.completed`、`step.ended`、`turn.ended`。
- 每条事件含 `eventId`、`sessionId`、`turnId`、`stepId`、`timestamp`、`schemaVersion` 和 payload。
- 使用 JSONL 初步持久化；写入保证单条记录完整性与 crash recovery 策略；支持状态投影（重建消息、任务、工具历史）。
- 工具执行采用执行前意图记录和执行后结果记录；崩溃后处于 `UNKNOWN` 的有副作用操作必须人工核实，不可自动再次执行。
- 支持会话恢复、列表、导出；日志格式版本化。

### FR-10 上下文与提示词管理

- 将系统提示词、用户输入、工作区摘要、工具 schema、最近消息、历史摘要独立管理。
- 支持 token 估算、上下文裁剪与压缩策略；不可在压缩时悄悄丢失待完成工具调用及其必要结果。
- 大型输出采用引用/摘要/按需读取，而不是完整日志无限塞入上下文。
- 提供 `AGENTS.md` 一类的项目指令加载能力，但其中的指令属于工作区内容，不能越权覆盖系统权限规则。

### FR-11 插件与事件机制

- `HarnessPlugin` 具备 ID、版本、依赖、启动、停止、注册和清理生命周期。
- 工具/模型/存储/执行提供者通过注册表注入；卸载插件时撤销其注册、事件订阅和资源句柄。
- 事件总线支持同步监听，指定事件允许异步分发，定义超时、失败传播和监听器排序。
- V2 优先开发“启动时静态装载 + 生命周期管理”；真正热重载/隔离类加载器属于高级扩展。

### FR-12 高级命令执行

- 持久终端会话，交互式 PTY，后台任务的 start/status/log/stop。
- 进程树清理、长期任务日志轮转、超时和资源配额。
- 文件工具、Shell、Git、LSP 要共享同一个工作区/执行环境抽象，避免本地与远程路径不一致。

### FR-13 模型扩展与可观测性

- 允许多个模型适配器和每任务模型选择。
- 记录延迟、请求次数、token 使用量（提供商有返回时）、估算成本、工具失败原因；默认关闭敏感正文遥测。
- 加入结构化日志、关联 ID 和异常诊断。

## 6. 功能需求：V3 DeepSeek Harness 功能对齐（P2）

### FR-14 MCP 与 Skills

- MCP Client：连接可信本地/远程 MCP Server，发现工具、校验 Schema、审批调用与展示来源。
- Skills：以受控文件规范加载技能说明、资源与脚本；技能内容视为非可信输入，执行仍遵守权限策略。

### FR-15 Subagent 与 Agent Teams

- 支持父 Agent 委托任务、独立上下文、预算与取消传播、结果回传。
- 多 Agent 阶段实现任务分配、状态同步、并发限制和避免修改同一文件的冲突策略。

### FR-16 沙箱与远程执行

- 抽象 `ExecutionEnvironment`，文件、命令和终端位于同一个执行环境。
- 至少一个 Docker/容器隔离实现；最小权限运行、网络默认受限、受控挂载、资源配额、清理策略。
- 不将基于路径的 Java 检查宣传为可靠隔离；需按具体操作系统独立开展安全测试。

### FR-17 Web/Desktop/SDK

- Web API：认证、创建/取消任务、事件订阅、工具审批、Diff 查看。
- Desktop：本地运行时与 UI 生命周期协同；避免无鉴权开放执行接口。
- SDK：使用版本化协议调用 Agent、消费事件、传递审批和取消操作。
- V3 的功能对齐按官方选定 commit 的基线逐项评估，明确“支持/部分支持/不支持/不适用”。

## 7. 核心数据与接口契约（草案）

### 7.1 Java 核心接口

```java
public interface LlmClient {
    ChatResponse complete(ChatRequest request, CancellationToken cancellation);
}

public interface AgentTool {
    String name();
    ToolSchema schema();
    ToolResult execute(ToolArguments args, ToolContext context) throws Exception;
}

public interface ToolRegistry {
    void register(AgentTool tool);
    AgentTool resolve(String name);
}

public interface PolicyEngine {
    PolicyDecision evaluate(ToolInvocation call, ExecutionContext context);
}

public interface Workspace {
    Path root();
    String readText(String relativePath) throws IOException;
    FileChange writeText(String relativePath, String content) throws IOException;
}

public interface CommandExecutor {
    CommandResult execute(CommandRequest request, CancellationToken cancellation);
}

public interface SessionRepository {
    void append(SessionEvent event);
    List<SessionEvent> load(String sessionId);
}
```

正式实现需进一步规定：线程安全、异常类型、同步/异步语义、生命周期、取消传播、事务边界及请求版本号。

### 7.2 ToolResult 统一结构

```json
{
  "callId": "call_001",
  "tool": "execute_command",
  "status": "success",
  "startedAt": "2026-10-13T10:00:00Z",
  "durationMs": 1500,
  "data": {
    "exitCode": 0,
    "stdout": "BUILD SUCCESS",
    "stderr": "",
    "truncated": false
  },
  "error": null
}
```

`status` 候选值：`success`、`failed`、`denied`、`cancelled`、`timeout`、`unknown`。工具错误不应伪装为模型正常回答。

### 7.3 配置文件样例（示意）

```yaml
model:
  provider: openai-compatible
  name: deepseek-chat
  baseUrl: ${LLM_BASE_URL}
  apiKeyEnv: DEEPSEEK_API_KEY
agent:
  maxSteps: 30
  maxDurationSeconds: 900
workspace:
  root: D:/projects/demo
  denyOutsideRoot: true
process:
  timeoutSeconds: 120
  maxOutputBytes: 1048576
security:
  approvalMode: ask-on-risk
  autoApproveReadOnly: true
```

不得将明文 API key 保存在仓库或共享配置内。

## 8. 非功能需求

| ID | 类别 | 需求与量化目标（项目设定，非官方基准） |
|---|---|---|
| NFR-01 | 正确性 | 必须能区分“工具执行成功”和“任务达到目标”；所有完成声明可追溯到执行证据 |
| NFR-02 | 可用性 | Windows 上通过 Maven Wrapper 一条命令构建；`doctor` 检测 Java/Git/Maven/API 设置 |
| NFR-03 | 性能 | 在本地 SSD 的测试项目上，列举 1,000 个条目目标 ≤2 秒，不含网络与杀毒软件等干扰 |
| NFR-04 | 鲁棒性 | 大文件/超长日志不导致 OOM；文件与命令工具均有上限和超时 |
| NFR-05 | 安全 | 未授权工作区外读取/修改的自动化负面测试必须全部拦截；Host Shell 仍按高风险能力管理 |
| NFR-06 | 隐私 | 默认本地存储；日志脱敏；对外部模型的数据发送在使用说明中明确告知 |
| NFR-07 | 可测试性 | 核心 Agent/Tool/Policy 支持 Fake LLM 和内存 Session；主要错误路径有自动化测试 |
| NFR-08 | 兼容性 | 优先 Windows 10/11；之后 CI 覆盖 Linux 与 macOS |
| NFR-09 | 可维护性 | 公共接口版本化；统一异常、结构化日志、自动格式化和静态检查 |
| NFR-10 | 可观察性 | 每一步含关联 ID、工具耗时、审批决定、命令退出码和最终任务状态 |

## 9. 开发技术方案

- 语言与构建：Java 21 LTS、Maven Wrapper、多模块 Maven。
- HTTP：Java HttpClient；JSON：Jackson；CLI：可选 Picocli；日志：SLF4J + Logback。
- 测试：JUnit 5 + Mockito（按需要），Windows 与 Linux CI。
- 文件：Java NIO、必要时 Git/ripgrep 加速；Git 能力可用原生命令或后期 JGit。
- 进程：`ProcessBuilder` 起步，交互终端需要额外 PTY 库/适配层。
- 持久化：V1 简易结构化任务记录；V2 JSONL Event Store，后续可接 SQLite。
- 插件：V2 自定义 Java SPI/注册表/生命周期，不直接照搬 Cordis 运行时。
- Web：后续按需求选择 Spring Boot + SSE/WebSocket；非 V1 前置条件。

## 10. 研发迭代与工作分解（建议）

以下以里程碑而非固定日历为约束；时间受个人熟练程度、模型 API 和安全要求影响。

| 里程碑 | 范围 | 必要交付物 | 退出条件 |
|---|---|---|---|
| M0 | 工程底座 | 构建、代码规范、CI、Mock LLM | 所有测试能在空环境跑通 |
| M1 | LLM + Agent Loop | 结构化模型调用、工具派发、步数控制 | Mock 多步任务通过 |
| M2 | 文件系统 | 目录、读取、搜索、写入、精确编辑、Diff | 修改项目不破坏无关文件 |
| M3 | Shell + Policy | 命令执行、超时取消、审批与审计 | Maven 错误处理与拒绝执行测试通过 |
| M4 | Coding Agent MVP | 项目修复全流程、CLI、交付报告 | 真实项目端到端验收通过 |
| M5 | 持久化与事件 | JSONL、投影、恢复与回放 | 崩溃恢复无盲目重放副作用 |
| M6 | 插件化与上下文 | Plugin、Event Bus、压缩、持久终端 | 替换实现无需改 Agent Loop |
| M7 | 高级生态 | MCP、Skills、沙箱、Subagent、Web/SDK | 逐项功能回归与安全验收 |

建议关键依赖顺序：M0 → M1 → M2 → M3 → M4 → M5 → M6 → M7。M2、M3 内的单元测试可并行实施。

## 11. 验收测试规范

### AC-01 项目探索

**Given** 授权 Maven 项目；**When** 输入“分析代码结构和构建命令”；**Then** Agent 使用读取/搜索工具找到 `pom.xml` 与主源代码目录，回答能够指向真实文件，不凭空编造。

### AC-02 新建功能

Given 简单计算器项目；When 输入“增加乘法方法和对应测试”；Then 系统创建/修改正确文件，运行测试并展示变更与退出码。

### AC-03 自主修复

Given 预置一处 Java 编译错误；When 输入“修复编译错误并运行测试”；Then Agent 能检测失败、定位并修改、重新执行测试；如果无法修复必须报告失败，不伪称成功。

### AC-04 路径越权

Given 工作区之外存在测试文件；When LLM 请求读取 `../outside.txt` 或经链接跳转；Then 被拒绝、生成事件，外部文件未被读取或修改。

### AC-05 危险操作审批

Given 工具提出删除目录或强制重置 Git；When 用户选择拒绝；Then 操作完全不发生，模型获得明确 denied ToolResult。

### AC-06 执行超时与取消

Given 测试程序持续输出或长时间阻塞；When 达到超时或用户取消；Then 进程服务结束任务、停止收集、释放资源，状态明确。

### AC-07 文件并发变更

Given Agent 读取文件后用户手工修改；When Agent 提交基于旧版本的编辑；Then 检测冲突或重新验证预期文本，不无提示覆盖用户修改。

### AC-08 会话恢复（V2）

Given 崩溃发生在危险工具执行与结果写入之间；When 恢复任务；Then 标记 UNKNOWN 并请求核实，不自动重试该工具。

### AC-09 插件卸载（V2）

Given 工具插件已注册事件监听和工具；When 插件卸载；Then 注册被撤销、事件监听被释放、相关资源关闭。

### AC-10 执行环境切换（V3）

Given 同一任务可在本机与容器执行；When 替换 ExecutionEnvironment；Then 文件、Shell、工作目录保持同一执行世界，不出现跨环境路径错误。

## 12. 风险、依赖与决策记录

| 风险 | 影响 | 缓解策略 |
|---|---|---|
| Shell 能力越权 | 数据丢失、泄露或系统损坏 | 工作区边界、审批、默认最小权限、V3 真正沙箱 |
| LLM 输出不可靠 | 误改代码、虚报完成 | Schema 校验、Diff、真实编译与测试验证 |
| 模型协议差异 | Tool Calling 不稳定 | Adapter 层、契约测试、明确支持矩阵 |
| 上下文膨胀 | 高成本、质量下降 | 输出截断、摘要与按需读取 |
| Windows 进程树难清理 | 遗留进程 | 进程树追踪、超时、必要时 Job Objects/隔离运行 |
| 插件系统过度设计 | MVP 推迟 | 先稳定接口，V2 再实现生命周期和热加载 |
| 直接跟随官方主分支 | 需求漂移 | 固定对照 commit，定期选择性吸收能力 |
| 第三方许可和依赖 | 发布障碍 | 记录许可证、SBOM、依赖安全扫描 |

### 待定决策（不会阻塞 M0—M2）

- 首发模型提供方与是否配置多个备用模型。
- Windows 原生 Shell 与 WSL 的默认优先级。
- 第一版自动审批的最小可信命令集合。
- 是否对 Git 工作区执行修改前自动创建可恢复快照。
- V2 持久化优先选 JSONL 还是 SQLite（建议 JSONL 优先）。

## 13. Definition of Done（完成定义）

一项 P0 功能被视为完成，当且仅当：

1. 对外功能符合对应 FR 与 AC 条款。
2. 有自动化单元测试覆盖正常与错误路径；安全敏感路径有负面测试。
3. 所有模型发起的真实本地操作可追溯到工具调用记录与用户权限决策。
4. 错误、拒绝、超时和取消能够形成明确状态，不吞异常、不伪造结果。
5. README 更新了命令、配置、风险与已知限制。
6. CI 构建与相关测试全部通过。

**V1 发布门槛：** AC-01～AC-07 全部通过；能在至少两个不同的预置 Java/Maven 示例项目上完成“探查→修改→构建/测试→必要时迭代→报告”；未经审批的高风险操作不得执行。模型效果具有随机性，实际评测必须固定任务集、记录模型与配置并多次重复，不以单次成功作普遍能力证明。

## 14. 参考资料和版本管理

1. DeepSeek AI. **DeepSeek Harness**. https://github.com/deepseek-ai/deepseek-harness
2. DeepSeek AI. **Architecture**. https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/architecture.md
3. DeepSeek AI. **Safety notice**. https://github.com/deepseek-ai/deepseek-harness/blob/master/SAFETY.md

**版本管理要求：** 开始实际复刻时记录 `git rev-parse HEAD` 的官方提交哈希，建立 `upstream-capabilities.md` 功能对照清单。本文件中的 V1/V2/V3 是 J-Harness 自定义开发阶段，不与官方版本号对应。
