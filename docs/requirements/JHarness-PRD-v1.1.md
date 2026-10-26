# J-Harness 开发需求规格说明书（SRS）

> 需求基线说明：保留本版需求与原范围决定，不把规划文本当作已交付事实。2026-10-26当前实现/延期/证据状态见[统一索引](../development/current-state.md)。

> 文档版本：1.1（复刻需求基线，取代 1.0）  
> 制定日期：2026-10-13  
> 修订日期：2026-10-14  
> 项目定位：基于 Java 的 DeepSeek Harness 复刻目标与可扩展 Agent Harness  
> 官方参考：https://github.com/deepseek-ai/deepseek-harness  
> 官方架构：https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/architecture.md

## 0. 文档约定

- **MUST**：相应版本验收时必须具备；**SHOULD**：推荐实现；**MAY**：可选。
- 优先级：P0（V1 必须）、P1（V2 核心）、P2（V3 扩展）。
- 状态：本文件是待评审的开发需求基线；具体 API、性能目标与版本依赖均需在实施时验证。
- “对标 DeepSeek Harness”表示目标功能和架构理念对齐，不表示二进制兼容、插件格式兼容或官方认证。
- **复刻验收口径：** V1 不再只是“具备类似操作能力的 CLI Agent”，而是“具备 Harness 基础机制的最小可用切片”——即插件容器、事件驱动 Session、请求可重建、执行前持久化必须在 V1 就位（见第 2.4 节与第 10 节）。

### 0.1 v1.0 → v1.1 修订记录

本版针对 v1.0 的评审意见做了以下结构性调整，除本节省略的历史条款外，全部原条款仍然有效。

| # | 修订内容 | 位置 |
|---|---|---|
| 1 | 插件化基座（服务注册、依赖声明、启停与注册撤销、配置装配）由 V2/M6 前置到 V1 | 2.4、FR-01、FR-02、10 |
| 2 | 会话事件日志由“附加审计”改为“模型历史的唯一依据”，并进入 P0 | FR-03、FR-08、NFR-11 |
| 3 | 补充“有副作用工具执行前意图必须成功落盘”的崩溃恢复前置保证，并补齐单写者、尾部损坏、版本迁移与未来版本拒绝规则 | FR-03.4–FR-03.8、AC-09、AC-10 |
| 4 | 事件总线区分通知 / 串行事件 / 拦截链三类语义，扩展点替代修改 Agent Loop | FR-02、FR-25.2、AC-11 |
| 5 | 第 7 节接口草案按缺口重写为可冻结契约（撤销句柄、输出 Schema、流式与能力发现、写拥有权、执行环境无关路径、Agent 隔离） | 7.2 |
| 6 | 补齐上游功能对照清单（PTC、Workflow、Goal/Todo/Plan/Schedule、检索、附件、会话分叉、输出 spill、Profile/Bundle/Preset、文件交付等），并要求建立 `upstream-capabilities.md` | 7.1、FR-17、FR-21–FR-27、14 |
| 7 | 消除验收歧义：`jharness cancel` 控制通道、进程树清理的结果保证与失败状态、`--dry-run` 能力边界、事件字段按类型规定、文件工具超时后副作用不确定语义 | FR-07.11、FR-09.4、FR-10.5、FR-11.4、7.5、AC-07、AC-14 |
| 8 | 里程碑重排为 M0 插件容器/事件/内存 Session → M1 Agent Loop → M2–M4 落地闭环 → 后续扩展 | 10 |

## 1. 项目背景和目标

### 1.1 背景

传统 LLM 聊天界面主要输出文本，不能可靠地完成软件项目中的“观察环境—行动—获得反馈—修正行动”闭环。J-Harness 需要在用户授权的本地工作区内，将模型推理与文件系统、命令执行、代码检索、测试验证连接起来，并**从第一版起就建立在可替换插件、可重建会话事件与可替换执行环境之上**，逐步扩展为与 DeepSeek Harness 能力对齐的 Agent Harness。

v1.0 的主要偏差是：把“插件”“事件日志”“崩溃恢复前置保证”当作后期增强，容易先固化一个单体核心，再回头拆成插件。本版把这三项作为 V1 基础架构。

### 1.2 项目目标

**G1—本地操作：** 发现、读取、搜索、创建和精确修改用户授权工作区内的文件。  
**G2—自主开发：** 在有限步数、时间和预算约束下，调用工具完成代码编写、编译、测试、错误修复和结果说明。  
**G3—过程可控：** 用户可看到所有工具请求与执行结果，批准或拒绝敏感操作，随时取消任务。  
**G4—状态可靠：** 有副作用操作在执行前已持久化意图；模型历史可由会话事件重建；崩溃恢复不会盲目重放未知副作用的操作。  
**G5—架构扩展：** 模型、工具注册表、会话日志、执行环境和 Agent Loop 本身都是可替换插件；后续支持 MCP、Skills、子 Agent 和远程沙箱。

### 1.3 目标用户与平台

- 首要用户：在 Windows 10/11 上使用 Java、Maven、Git、命令行开发软件的个人开发者。
- 扩展用户：需要运行 Coding Agent 的开发团队及工具集成方。
- 首发运行环境：Windows 10/11 + JDK 21 LTS（推荐）；PowerShell/CMD；Git；Maven Wrapper 优先。
- 后续兼容：Linux/macOS、WSL、Docker、远程执行容器。
- 首版交互方式：终端 CLI，不依赖 Web 前端。

### 1.4 非目标

- V1 不实现任意桌面图形界面点击、浏览器自动化、无人值守系统管理、默认全盘文件访问。
- V1 不实现热重载与隔离类加载器（这两项与“静态装载 + 生命周期管理”分离，后置到 V2/V3）。
- V1 不承诺在无沙箱的宿主操作系统上安全运行不可信命令。
- 不要求与原版 Cordis 插件运行时或插件格式直接兼容，也不要求照搬其数百个 npm 包。
- Agent Teams、语音、浏览器/桌面操作等上游实验能力不纳入本产品主线（见 7.6 对照清单）。

### 1.5 复刻原则（不可妥协项）

以下五条来自上游架构的核心主张，本项目的架构设计与验收必须服从：

1. **一切皆服务：** 模型、工具注册表、会话日志、持久化、执行环境乃至 Agent Loop 都以服务接口组合，而非硬编码在核心中。
2. **会话是事实来源：** 会话事件日志不是附加审计，而是模型请求与模型历史的依据；每次模型请求都必须能够由日志重建。
3. **先落盘，后副作用：** 有副作用的工具开始执行前，执行意图必须成功持久化；落盘失败不得进入工具主体。
4. **扩展靠扩展点：** 重试、压缩、超时、权限等能力通过拦截链与事件扩展点实现，而不是持续修改 Agent Loop。
5. **Agent 之间不串味：** 每个 Agent/会话的工具、提示词、监听器与权限贡献相互隔离。

## 2. 用户角色、任务与业务流程

### 2.1 角色

- **用户**：指定工作区、描述开发任务、批准敏感操作、检查变更、取消任务。
- **Agent**：规划下一步工具调用、阅读反馈、迭代修改、形成结论。
- **模型提供者**：提供聊天、工具调用、流式输出及使用量等能力。
- **工具运行时**：校验参数，执行授权操作并将结构化结果反馈给模型。
- **审计/策略组件**：记录决策与操作，阻止超出边界的动作。
- **插件作者**：以服务与扩展点形式提供工具、模型适配、存储或执行环境。

### 2.2 关键用户故事

| ID | 用户故事 | 版本 |
|---|---|---|
| US-01 | 我想选择本地项目并要求 Agent 概览目录和技术栈 | V1 |
| US-02 | 我想让 Agent 新建 Java 类并修改现有方法 | V1 |
| US-03 | 我想让 Agent 执行 Maven 测试，读取失败日志并修复 | V1 |
| US-04 | 我想在运行危险命令前看到原因和影响并决定放行 | V1 |
| US-05 | 我想看到每次文件修改的 Diff，并知道哪些测试被执行 | V1 |
| US-06 | 我想取消执行中的任务且不留下失控的子进程 | V1 |
| US-07 | 我想在配置里禁用或替换某个模型提供方/工具包，而不改动 Agent Loop | V1 |
| US-08 | 我想在任何一次模型请求前都确定：它发给模型的内容可以从会话事件重建 | V1 |
| US-09 | 我想关闭程序后恢复历史会话和任务轨迹 | V2 |
| US-10 | 我想切换模型服务、启用流式输出与上下文压缩 | V2 |
| US-11 | 我想安装工具插件或连接 MCP Server | V3 |
| US-12 | 我想通过 Web/SDK/桌面端驱动相同的 Agent Runtime | V3 |

### 2.3 核心流程

1. 用户在终端启动应用并选定工作区。
2. 插件容器装载启用的插件，解析依赖，创建服务注册与扩展点订阅。
3. 系统加载配置、模型连接、工具定义、工作区策略与会话（内存或磁盘）。
4. 用户提出任务；Agent 从会话事件派生请求上下文，组装提示词与工具声明。
5. 模型返回文本或结构化 Tool Calls；系统校验参数、作用域和权限，并记录执行意图。
6. 通过审批与拦截链的操作被执行，结果转换为统一 ToolResult，**以事件形式追加到会话日志**。
7. Agent 按反馈继续调用模型，直到完成、遇到无法解决的错误、被取消或达到限制。
8. 系统展示摘要、变更文件、Diff、测试结果和未完成事项。

### 2.4 V1 范围判定（复刻基线）

| 能力 | v1.0 归属 | v1.1 归属 | 理由 |
|---|---|---|---|
| 服务注册、依赖声明、插件启停、注册撤销、配置装配 | V2（M6） | **V1（P0）** | 后续所有能力（模型、工具、存储、Agent Loop）都从服务接口组合；否则要先做固定核心再返工拆分 |
| 会话事件日志 + 状态投影 | V2（FR-09） | **V1（P0）** | 模型历史必须由事件派生，避免维护“聊天数组 + 审计日志”两份易失真状态 |
| 执行前意图持久化（checkpoint） | 未明确 | **V1（P0）** | 否则可能出现“命令已执行、意图仍在内存”，崩溃后连执行过都不知道 |
| 事件总线三类语义 | V2 部分（FR-11） | **V1（P0，最小集）** | 拦截链是重试/压缩/权限/超时的承载机制 |
| 静态装载插件 | V2 | V1 | 与热重载解耦 |
| 热重载、隔离类加载器 | V2 | V2/V3 | 与静态装载分离，不影响 V1 验收 |
| 磁盘事件存储、会话恢复界面、复杂迁移 | V2 | V2 | V1 允许内存事件存储，但接口与语义不变 |
| 流式输出、压缩、持久终端、多模型 | V2 | V2 | 保持减量 |

**落地方式：** V1 先做“内存事件存储 + JSONL 追加写入”这一最小持久化组合即可满足 P0；磁盘格式迁移、恢复 UI 与查询能力后置。**但事件模型、写拥有权与执行前落盘语义必须从 V1 就固定。**

## 3. 总体架构需求

### 3.1 分层

```
CLI / Web / Desktop / SDK
           |
      Application API
           |
  Plugin Container (services / dependencies / lifecycle / scopes)
           |
  Agent Runtime: AgentLoop / Turn / Step / PromptComposer / Events / Session
           |
  Service Registry -- Tool Registry -- Approval Policy -- Provider Adapter
           |
  Execution Environment: Filesystem / Command / Terminal / Git / Sandbox
           |
  Local Workspace / Docker / Remote
```

必须保证：

- UI 不直接操作文件；LLM Adapter 不直接执行 Shell。
- Agent Loop 只通过服务接口依赖 Session、PromptComposer、ToolPipeline、EventBus，不直接依赖具体模型、工具、存储实现。
- Workspace 只表示项目边界，不能单独充当 OS 级安全沙箱。
- 模型历史由 Session 事件投影派生，不允许存在第二份权威聊天状态。
- 每个 Agent 实例拥有独立的工具视图、提示词贡献、监听器与权限贡献。

### 3.2 推荐 Maven 模块

```
j-harness/
  pom.xml
  harness-api/          公共模型与服务接口
  harness-plugin/       服务注册、依赖解析、生命周期、作用域与扩展点
  harness-events/       事件模型、事件总线（通知/串行/拦截链）
  harness-session/      会话事件日志、投影、检查点策略、持久化
  harness-agent/        Agent Loop、状态机、提示词组装
  harness-llm/          LLM 适配器与流式解析
  harness-tools/        工具注册、Schema、执行管线
  harness-workspace/    文件读取、编辑、目录边界
  harness-process/      命令执行、进程取消
  harness-policy/       权限、审批、审计
  harness-cli/          入口、终端交互
  harness-testkit/      Fake LLM、临时项目、契约与集成测试
  harness-mcp/          V3 MCP 适配
  harness-terminal/     V2 持久终端
  harness-web/          V3 Web/API
```

启动开发时允许以一个 Maven 工程分包实现，但**模块边界与接口名称须保持稳定**；模块拆分应由复杂度和测试边界驱动。`harness-plugin`、`harness-events`、`harness-session` 自 V1 即为独立模块，以保证后续能力依赖的机制先行建立。

## 4. 功能需求：V1 最小可用 Harness 切片（P0）

### FR-01 插件容器、服务注册与生命周期

- FR-01.1 定义 `HarnessPlugin`：`id`、`version`、`apiVersion`、依赖声明、`start(PluginContext)`、`stop()`；插件通过 `PluginContext` 获得注册能力与配置视图。
- FR-01.2 定义服务注册表：按服务接口类型注册/解析实现；**注册返回可撤销句柄 `Registration`**，`close()` 撤销该插件贡献的注册、扩展点订阅与资源句柄。
- FR-01.3 重复注册同一服务契约下的同一 `key` 必须按显式策略处理（拒绝或覆盖），不得静默产生歧义；解析歧义必须在启动期失败并给出可诊断错误。
- FR-01.4 依赖解析：启动顺序由声明依赖决定；检测缺失依赖、循环依赖与版本不兼容，失败时给出明确报告且不进入半启动状态。
- FR-01.5 配置决定启用哪些提供方（模型、工具包、存储、执行环境、策略）；禁用某个提供方时系统仍可启动，或在缺少必需服务时明确失败。
- FR-01.6 服务接口至少覆盖：模型提供方、工具注册表、会话日志、持久化/检查点策略、执行环境、策略引擎、Agent Loop 工厂。
- FR-01.7 **Agent Loop 本身是可替换服务**：默认实现由插件提供，替换实现不得要求修改工具、会话或 CLI 代码。
- FR-01.8 插件生命周期事件必须可被通知订阅观察（装载、启动、停止、失败）。
- FR-01.9 **V1 只要求静态装载**（配置/classpath 指定），MAY 支持基于 `ServiceLoader` 的发现；热重载、隔离类加载器与插件沙箱明确后置。
- FR-01.10 **作用域隔离**：每个 Agent/会话拥有自己的工具视图、提示词贡献、监听器与权限贡献；父子 Agent 或多个会话之间的注册不得互相污染。
- FR-01.11 插件启动失败不得留下已注册但未启动的残留注册；扫描与清理规则必须定义并有测试。

**验收：** 通过配置禁用模型提供方后系统行为明确可诊断；用测试插件替换 Agent Loop 与工具注册表实现，无需修改其他模块代码即可运行既有 FR-04 测试；插件 `stop()` 后其注册与订阅全部失效（见 AC-11、AC-12）。

### FR-02 事件模型与事件总线

- FR-02.1 定义 `SessionEvent`：`eventId`、`sessionId`、`type`、`timestamp`、`schemaVersion`、`payload`，并携带可选关联字段（见 FR-03.6）。
- FR-02.2 事件总线必须区分**三类语义**：
  - **通知（notify）**：观察已发生的事实，不阻塞发布方，不改变结果；监听器异常不得影响主流程，但必须被记录。
  - **串行事件（serial）**：按顺序并等待的初始化/停止类工作；发布方等待完成，超时与失败传播规则显式定义。
  - **拦截链（intercept）**：允许修改请求、拒绝工具、包装执行或替换结果；按优先级组成确定顺序的链，且必须支持短路与错误语义。
- FR-02.3 拦截链至少覆盖这些扩展点：模型请求前（改写/压缩/附加提示）、模型响应后、工具调用前（拒绝/改写参数）、工具执行包装（超时/重试/限流）、工具结果后（替换结果/脱敏）。
- FR-02.4 新增重试、上下文压缩、超时、权限等能力**应通过扩展点实现**，不得以不断修改 Agent Loop 的方式实现。
- FR-02.5 定义监听器排序规则、优先级、超时、失败传播与移除；事件分发不得导致逐事件递归发布。
- FR-02.6 事件总线对所有已发布事件提供可测试的录制能力（testkit），用于断言顺序与负载。

**验收：** 用一个拦截器插件实现“所有 Shell 工具参数改写 + 只读工具不询问”，不修改 Agent Loop 与工具实现即可生效（见 AC-11）。

### FR-03 会话事件日志、投影与检查点（P0）

- FR-03.1 采用 Append-only `SessionEvent` 日志；V1 至少记录：`session.created`、`turn.started`、`step.started`、`model.requested`、`assistant.message`、`tool.requested`、`approval.decided`、`tool.started`、`tool.completed`、`step.ended`、`turn.ended`。
- FR-03.2 **模型历史必须由会话事件投影派生。** 模型可见的系统说明、用户输入、上下文注入、工具声明与工具结果都必须有可重建的事件记录；不允许存在与之并行的权威聊天状态。
- FR-03.3 **可重建性验收要求：** 对任意一次 `model.requested`，仅使用该事件之前（含）的日志即可重建出语义等价的请求（提示词、工具声明、消息序列、参数）。
- FR-03.4 **执行前持久化（关键前提）：** 任何有副作用的工具开始执行前，其执行意图（工具名、完整参数、callId、目标路径/命令）必须成功落盘；**落盘失败或未确认成功时不得进入工具主体**，必须以明确错误返回。
- FR-03.5 工具完成后追加结果事件（状态、耗时、输出摘要或引用）；崩溃后处于 `UNKNOWN` 的有副作用操作必须人工核实，不可自动再次执行。
- FR-03.6 **事件字段按事件类型规定**，不要求所有事件都带 `turnId`/`stepId`：
  - Session 级事件（`session.created`、插件/配置事件）：只要求 `sessionId`。
  - Turn 级事件：要求 `turnId`。
  - Step/工具/模型事件：要求 `turnId` 与 `stepId`，工具事件额外要求 `callId`。
  - 无法归属步骤的事件必须显式标记为 session 级，禁止填充占位 ID。
- FR-03.7 **检查点策略（checkpoint policy）：** 规定在哪些边界强制持久化——至少包括模型请求前、顶层工具执行前、步骤边界；策略是可替换服务，含内存实现（测试用）与 JSONL 实现。
- FR-03.8 **持久化约束：**
  - **单会话写拥有权：** 同一会话同一时刻只能有一个写入者；需定义锁/租约、冲突时行为（等待、失败或只读打开），禁止两个进程并发追加。
  - **记录完整性：** 单条记录必须原子追加（整行写入 + flush 语义）；进程崩溃不得产生半行被当作有效记录。
  - **尾部损坏处理：** 读取时遇到截断/非法尾行必须显式报告并按策略处理（截断到最后一条有效记录或拒绝加载），不得静默丢弃中段损坏。
  - **版本迁移：** 日志格式版本化；迁移规则显式定义并可测试。
  - **未来版本拒绝：** 遇到高于本实现支持版本的日志，必须拒绝写入并以只读方式报告，而不是尝试解析或覆盖。
- FR-03.9 提供状态投影：从事件重建消息序列、任务状态、工具历史、审批历史；投影必须可重复执行且结果稳定。
- FR-03.10 日志中不得出现 API key 等秘密；工具输出在入日志前按策略截断或转为引用。

**验收：** 见 AC-03、AC-08、AC-09、AC-10。

### FR-04 Agent Loop 与任务状态机

状态：`CREATED → RUNNING → WAITING_APPROVAL | EXECUTING_TOOL → RUNNING → COMPLETED | FAILED | CANCELLED | LIMIT_REACHED`。

- FR-04.1 支持模型工具调用及多步循环，单次模型结果可能包含多个工具调用。
- FR-04.2 工具调用与返回结果必须通过 callId 对应，消息协议满足模型提供者要求。
- FR-04.3 需要处理纯文本完成、格式非法、工具不存在、参数不合法、工具错误、模型错误、限流、上下文溢出和取消。
- FR-04.4 默认设最大步骤数、最大运行时间和可选 API 费用预算；达到限制时明确报告状态而非伪称完成。
- FR-04.5 V1 对多工具调用默认串行，避免并发修改冲突；只读并行在后续版本按调度策略引入。
- FR-04.6 返回用户可理解的最终结果：完成/部分完成、变更文件、执行命令、测试结论、遗留风险。
- FR-04.7 **Agent Loop 的依赖面必须显式**：除 `LlmClient` 与工具执行管线外，还依赖 `Session`（读取投影、追加事件）、`PromptComposer`（提示词组装）、`EventBus`（扩展点）与生命周期/取消服务；这些依赖全部通过接口注入。
- FR-04.8 Agent Loop 实现本身可被插件替换（FR-01.7），且替换后不得破坏会话可重建性与工具审批语义。

**验收：** Fake LLM 依次请求 `read_file → edit_file → execute_command`，执行顺序及 toolCallId 完全一致；失败时任务可控终止；每一步请求都能由事件重建（AC-03）。

### FR-05 LLM 接入

- FR-05.1 定义模型无关的 `LlmClient`、`ChatRequest`、`ChatResponse`、`ToolSchema`、`ToolCall`。
- FR-05.2 **能力发现：** `LlmClient` 必须暴露 `capabilities()`（至少：是否支持工具调用、流式、图片/附件输入、并行工具调用、最大上下文），调用方据此降级而不是假设。
- FR-05.3 **流式能力纳入接口**（V1 可只有非流式实现）：`stream()` 返回增量块序列；`complete()` 可由 `stream()` 派生或独立实现。
- FR-05.4 首发支持一个 OpenAI-compatible API 适配器；模型名称、baseUrl、API key 经配置注入。
- FR-05.5 不假设所有兼容 API 的工具调用、流式输出、消息角色与错误格式完全相同；差异由适配器封装并有契约测试。
- FR-05.6 HTTP 请求支持超时、取消、有限退避重试，避免对可能已处理的非幂等动作盲目重试。
- FR-05.7 API 密钥不得写入日志和 Session；避免将本地敏感文件自动送入模型上下文。
- FR-05.8 V1 可以先采用非流式调用，但 CLI 必须展示工具执行状态；SSE 流式响应默认纳入 V2。
- FR-05.9 **请求准备与取消语义：** 定义请求在发送前是否已完成落盘（与 FR-03.7 一致）、取消发生在发送前/发送中/响应中的不同语义，以及取消后是否可以安全重发。

### FR-06 工具注册表与执行管线

- FR-06.1 **`ToolRegistry.register()` 必须返回可撤销句柄**（`Registration`)，并规定：重复注册（同名不同实现）策略、作用域（全局 / 会话 / Agent 子树）、解析时同名冲突的处理与诊断信息。
- FR-06.2 `AgentTool` 契约必须包含：名称、描述、**输入 Schema 与输出 Schema**、结构化输出值、面向模型的内容转换（文本/多模态）、以及面向 UI 的可回放展示信息（渲染摘要、Diff、命令与退出码）。
- FR-06.3 工具执行管线固定顺序：解析 → 参数校验（Schema）→ 作用域检查 → 事件发布（`tool.requested`）→ 策略评估 → 审批 → **执行意图落盘（有副作用时，FR-03.4）** → 拦截链包装执行 → 结果标准化 → 结果事件 → 上下文引用。
- FR-06.4 工具执行必须产生统一 `ToolResult`；失败、拒绝、取消、超时、未知必须为不同状态，不得伪装成模型正常回答。
- FR-06.5 每个工具声明副作用等级（只读 / 工作区写 / 工作区外或系统级影响），供策略与 UI 使用；工具不得自报“安全”绕过策略。
- FR-06.6 工具实现不得直接依赖 CLI 或 UI；工具通过注入的上下文（工作区、执行环境、事件、取消令牌）访问能力。
- FR-06.7 多工具调用的执行顺序默认与模型返回顺序一致；串行/并行的调度由 FR-25 的调度策略决定，不由工具自身决定。

**验收：** 工具重名注册、插件卸载后调用已撤销工具、跨会话工具视图污染均被显式拒绝或隔离（AC-12）。

### FR-07 工作区与文件/代码工具

- FR-07.1 用户通过 `--workspace <path>` 指定一个授权工作区；默认不开放全盘访问。
- FR-07.2 对路径做标准化和访问控制；拒绝 `../` 穿越、非授权绝对路径；必须考虑符号链接和 Windows junction/reparse point。
- FR-07.3 支持 `.gitignore` 和应用自有排除目录（如 `.git/`、`target/`、`node_modules/`）的检索过滤，用户可配置。
- FR-07.4 支持 UTF-8 为主的文本读写；二进制文件需识别并拒绝当文本覆盖；保留文件换行风格和编码策略。
- FR-07.5 访问被拒绝的路径时返回结构化错误，不泄露不必要的文件内容。
- FR-07.6 必须提供可被模型调用的工具：

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

- FR-07.7 写入之前需检查是否产生覆盖，以及文件是否自读取以来被其他进程修改；冲突需拒绝或请求重读。
- FR-07.8 原子写入尽可能使用同目录临时文件加替换；出错时尽量不损坏原文件。
- FR-07.9 对每次编辑生成 before/after 摘要、文件级 Diff、工具事件。
- FR-07.10 编辑工具执行失败不能返回假成功。
- FR-07.11 **文件工具超时语义：** 文件操作超时必须以“副作用不确定”语义返回（与命令的 `timedOut` + `unknown` 处理一致），不得在返回失败后仍可能产生未报告的写入；实现必须说明超时后底层操作是否可能继续完成，以及如何检测（版本/时间戳校验、后续读取确认）。
- FR-07.12 **受保护修改：** 写入必须经过统一的受保护修改通路（版本检查 + 事件记录 + 可选快照），不允许工具绕过工作区抽象直接使用 NIO 写文件。
- FR-07.13 大文件与超大输出必须按 FR-08.4 走引用/分页路径，不得整文件读入上下文。

**验收：** 工作区内读写成功；对 `../secret.txt`、链接跳转工作区外、未授权绝对路径的访问均被拒绝；不因为错误路径而修改工作区外文件；给定包含编译错误的项目，Agent 可以读取、精确修改指定 Java 文件，未触碰无关文件，结果可由 `git diff` 验证。

### FR-08 上下文、提示词与输出管理

- FR-08.1 将系统提示词、用户输入、工作区摘要、工具 schema、最近消息、历史摘要独立管理，且每项都有对应的可重建事件（FR-03.2）。
- FR-08.2 支持 token 估算、上下文裁剪与压缩策略；不可在压缩时悄悄丢失待完成工具调用及其必要结果。
- FR-08.3 压缩/裁剪必须以事件记录（注入了什么、移除了什么），保证模型请求可重建。
- FR-08.4 **输出 spill 与持久投影：** 超过阈值的大输出写入会话附属产物（文件/日志），上下文中只保留引用、摘要与读取工具指引；定义 read back 语义与冷会话读取行为。
- FR-08.5 提供 `AGENTS.md` 一类的项目指令加载能力，但其中的指令属于工作区内容，不能越权覆盖系统权限规则。
- FR-08.6 附件与图片输入（V1 视模型能力可选）：定义存储位置、会话恢复后的可用性与到模型的传输方式；不支持时必须显式降级为文本描述而非静默丢弃。

### FR-09 命令执行与进程管理

- FR-09.1 使用 `ProcessBuilder`/受控进程服务在指定工作目录执行命令；明确区分 argv 直接执行与经 `cmd`/PowerShell 的 Shell 执行，避免不必要的注入与转义问题。
- FR-09.2 返回 `exitCode`、`stdout`、`stderr`、`durationMs`、`timedOut`、`truncated`、`cleanupStatus` 等字段。
- FR-09.3 并发消费 stdout/stderr，避免缓冲区阻塞；设置日志大小上限并保留截断提示。
- FR-09.4 **取消与清理：** 配置超时、取消 token、最大进程数、输出上限；取消时**尽力终止进程树**，并保证不留下仍然受本次运行控制的子进程。必须定义：
  - 支持条件（是否具备 Job Object / 进程组 / 仅能终止直接子进程）；
  - 清理失败时的显式状态（例如 `cleanupStatus: partial|failed` 与残留 PID 列表）；
  - 超时与取消后底层进程是否可能继续运行，以及如何检测。
  不得把“尽可能终止”当作“不留下失控子进程”的结果保证；验收只对可保证的部分断言（AC-06）。
- FR-09.5 支持调用 `mvnw.cmd test`、`mvn test`、`git status` 等，不依赖全局 Maven 必须存在。
- FR-09.6 不得向模型默认暴露全部宿主环境变量；敏感变量需要隔离、脱敏与授权。
- FR-09.7 命令的完整 argv、工作目录与脱敏后的环境摘要必须进入事件日志，以支持重建与审计。

**验收：** 成功捕获构建失败日志；超时命令被终止；超长输出被截断；取消可中止测试命令并返回取消状态，且进程清理状态被显式报告。

### FR-10 人工审批与安全策略

- FR-10.1 工具执行前必须经过 `PolicyEngine`，产出 `ALLOW / ASK / DENY`；策略评估在事件发布与意图落盘之前完成（FR-06.3）。
- FR-10.2 涉及工作区外访问、删除、批量重写、Git 强制重置、外部网络传输、软件安装、系统设置更改和秘密文件读取等，默认 `ASK` 或 `DENY`。
- FR-10.3 V1 可以设置白名单式的“受信任命令模板”，但不可单纯根据字符串前缀判断安全；Shell 中的链式命令/重定向/子进程可能绕过规则。
- FR-10.4 审批信息包括工具、完整参数、目标路径、潜在影响；拒绝后返回 `permission_denied` 给 Agent，不执行动作。
- FR-10.5 **`--dry-run` / 计划预览的能力边界：** 可以预览明确文件操作（将写入/删除哪些路径、Diff 预估）并展示计划；**不能保证预测任意 Shell 命令的实际修改**。必须定义不支持预览的情形，并在遇到此类工具时明确标注“无法预览”，而不是给出可能误导的“无变更”结论。
- FR-10.6 本地无沙箱模式标注为受信任项目开发场景，不宣传为安全执行不可信代码。
- FR-10.7 审批决定必须成为会话事件（`approval.decided`），包含决定、原因与决定者（用户/自动策略），以便恢复与审计。

**验收：** 危险命令在未批准时未执行；用户拒绝后 Agent 获得结构化结果；API key 在日志中脱敏；dry-run 对含 Shell 重定向的计划明确报告“不可预览”。

### FR-11 CLI 交互

建议命令：

```
jharness --workspace D:\projects\demo
jharness doctor
jharness config set model deepseek-chat
jharness run "修复项目编译错误并运行测试"
jharness diff
jharness sessions list
jharness cancel --run <runId>
```

- FR-11.1 V1 提供 REPL 和一次性 `run` 模式、工作区路径提示、工具调用记录、审批问答、任务取消。
- FR-11.2 返回退出码：0 完成；非零为错误/取消/部分完成，详细映射在 CLI 规范中定义。
- FR-11.3 Windows 路径含空格和中文必须可正确处理。
- FR-11.4 **取消需要运行标识与控制通道：** 每个运行必须生成 `runId` 并写入运行状态文件（含进程标识与本地控制端点）。`jharness cancel` 必须定义：如何发现目标运行（同机状态目录/注册表）、通过何种控制通道请求取消（本机 socket/命名管道/文件信号）、目标不可达或已退出时的行为与返回码。不得仅描述为“取消正在运行的任务”。
- FR-11.5 `sessions list` 等轻量查询命令在 V1 MAY 提供（完整查询能力见 FR-24）。

### FR-12 最终交付报告、变更记录与文件交付

- FR-12.1 报告包含任务目标、执行摘要、修改文件列表、差异摘要、运行的测试命令、实际退出码、尚未解决的问题、未经执行的验证事项。
- FR-12.2 不得把“模型声称成功”等同于“测试通过”；所有完成声明必须可追溯到执行证据事件。
- FR-12.3 **真实记录与模型叙述分离：** 修改文件列表、Diff、命令与退出码必须来自工具与工作区记录（可追溯 `eventId`/`callId`），模型生成的报告文本只能作为说明附加，二者在输出中必须可区分。
- FR-12.4 **文件交付：** 交付物（生成的文档、补丁、报告文件）必须登记为明确的交付记录（路径、来源事件、校验），不得只依赖模型在文本中声称“已生成”。

**验收：** 报告中的每个变更与命令都能定位到对应事件；模型中声称但未发生的操作不会出现在变更列表中。

## 5. 功能需求：V2 工程化 Agent Runtime（P1）

### FR-13 会话持久化、恢复与查询

- FR-13.1 JSONL 事件存储落盘（在 V1 检查点与单写者语义之上），实现会话恢复、列表、导出。
- FR-13.2 恢复流程：加载日志 → 校验版本与完整性（含尾部损坏策略）→ 投影 → 标记 `UNKNOWN` 操作 → 请求人工核实。
- FR-13.3 会话分叉（fork）：从某事件点创建新会话；定义父子关系、事件归属与分叉后的写拥有权转移。
- FR-13.4 会话查询与全文搜索：按时间、工作区、工具、状态检索；定义索引位置、重建方式与冷会话读取行为（是否需要先反序列化全部事件）。
- FR-13.5 版本迁移工具与未来版本拒绝行为的产品化呈现（CLI 报错文案与退出码）。

### FR-14 流式输出与上下文压缩

- FR-14.1 SSE/增量流式解析，CLI 增量渲染，工具调用增量组装与中途取消。
- FR-14.2 模型上下文自动压缩/摘要，带事件记录与可重建性保证（FR-08.2、FR-08.3）。
- FR-14.3 压缩策略作为可替换服务与拦截链扩展点实现。

### FR-15 高级命令执行与持久终端

- FR-15.1 持久终端会话，交互式 PTY，后台任务的 start/status/log/stop。
- FR-15.2 进程树清理的产品化保证、长期任务日志轮转、超时和资源配额。
- FR-15.3 文件工具、Shell、Git、LSP 共享同一个工作区/执行环境抽象，避免本地与远程路径不一致。

### FR-16 模型扩展、可观测性与成本

- FR-16.1 允许多个模型适配器与每任务模型选择；能力差异由 `capabilities()` 驱动降级。
- FR-16.2 记录延迟、请求次数、token 使用量（提供商返回时）、估算成本、工具失败原因；默认关闭敏感正文遥测。
- FR-16.3 结构化日志、关联 ID 与异常诊断。

### FR-17 提示词、配置组合与能力选择

- FR-17.1 **Profile / Bundle / Preset：** 定义可组合的配置单元（模型+工具包+策略+提示词集合），以及“会话创建时选择了哪个组合”的可追溯记录。
- FR-17.2 配置组合决定会话可用能力（工具集、模型、沙箱、审批模式），并可在事件中重建该会话的有效配置。
- FR-17.3 组合冲突（同一工具被两个包提供）按 FR-06.1 的作用域与冲突策略解决。

### FR-18 热重载与插件隔离（高级）

- FR-18.1 插件热重载、隔离类加载器与插件级依赖边界。
- FR-18.2 重载过程中的注册迁移、事件重订阅与失败回滚。
- FR-18.3 明确 V1 静态装载与本节能力的边界，避免把热重载当作 V1 验收前提。

## 6. 功能需求：V3 上游能力对齐（P2）

### FR-19 MCP 与 Skills

- FR-19.1 MCP Client：连接可信本地/远程 MCP Server，发现工具、校验 Schema、审批调用与展示来源。
- FR-19.2 MCP 工具纳入统一工具注册表与副作用等级模型（FR-06.1、FR-06.5），并受同一策略与审批约束。
- FR-19.3 Skills：以受控文件规范加载技能说明、资源与脚本；技能内容视为非可信输入，执行仍遵守权限策略。

### FR-20 Subagent 与委派

- FR-20.1 支持父 Agent 委托任务、独立上下文、预算与取消传播、结果回传。
- FR-20.2 子 Agent 拥有隔离的工具视图、提示词、监听器与权限贡献（FR-01.10），并与父会话共享可重建的事件归属规则。
- FR-20.3 多 Agent 阶段实现任务分配、状态同步、并发限制和避免修改同一文件的冲突策略。
- FR-20.4 Agent Teams 类多 Agent 协作能力**明确判定为“延期/实验”，不纳入 V3 必交范围**（见 7.6）。

### FR-21 Workflow 编排

- FR-21.1 与普通子 Agent 委派**分开定义**：Workflow 是预定义的多阶段编排（阶段、扇出、汇总、结构化结果），子 Agent 委派是运行时动态委托。
- FR-21.2 定义 Workflow 的状态机、失败与部分失败处理、阶段间数据传递、并发上限与可观测进度。
- FR-21.3 Workflow 的每个阶段执行必须走同一工具管线、策略与事件记录，保证可重建与可审计。
- FR-21.4 V1/V2 不要求实现；范围决定为“V3 评估后实现或延期”。

### FR-22 程序化工具调用（PTC / `run_code`）

- FR-22.1 范围决定：**支持 / 延期 / 排除**必须明确（建议：V3 评估，默认延期）。
- FR-22.2 若支持：定义代码执行环境（进程/容器）、可用 API 面、超时与资源限制、工具调用的审计粒度（代码内调用的每个工具仍需独立事件与策略评估）。
- FR-22.3 若支持：必须明确 PTC 不得绕过审批、事件记录与执行前落盘（FR-03.4）。

### FR-23 Goal / Todo / Plan / Schedule

- FR-23.1 四类概念**分别定义**状态、生命周期、持久化位置与对模型暴露的工具集，不得合并为一个模糊的“任务”概念。
- FR-23.2 Goal：长期目标的建立、进度、完成/放弃判定，与 turn 的关系。
- FR-23.3 Todo：步骤级清单，增删改查工具与投影规则；与 Plan 的边界必须写清。
- FR-23.4 Plan：计划的结构化表示、批准流程、执行与偏离时的更新规则。
- FR-23.5 Schedule：定时/延迟触发语义、后台运行、无人在场时的审批策略（默认不得自动批准高风险操作）。
- FR-23.6 每类状态变化必须产生会话事件并可重建。

### FR-24 会话查询、分叉与全文搜索（产品化）

- FR-24.1 在 FR-13 的持久化基础上提供查询界面/命令：会话列表、按工作区/模型/时间过滤、事件级查看。
- FR-24.2 会话分叉、比较、导出与归档；明确删除与保留策略。
- FR-24.3 全文搜索索引的构建、增量更新与损坏重建。
- FR-24.4 **不得以“恢复、列表、导出”代替查询与搜索能力**；三者是不同需求。

### FR-25 执行调度、并发与扩展点

- FR-25.1 只读工具并行调度策略（并发上限、顺序保持、结果聚合顺序）。
- FR-25.2 重试、超时、限流、压缩等横切能力以拦截链扩展点实现（FR-02.3），并提供标准插件实现。
- FR-25.3 调度策略可替换，且其决策必须可观察（为何串行/并行、为何重试）。

### FR-26 检索、网页与 LSP

- FR-26.1 范围决定：Web 搜索 / 网页获取 / LSP 是否纳入主要能力（建议：Web 获取 V3 纳入但默认需审批；LSP 延期）。
- FR-26.2 若纳入：外部内容视为非可信输入，必须记录来源 URL，且不得因网页内容改变系统权限规则。
- FR-26.3 若纳入 LSP：诊断与符号信息的获取必须走工作区/执行环境抽象，并受同一策略约束。

### FR-27 沙箱、远程执行与多入口

- FR-27.1 抽象 `ExecutionEnvironment`，文件、命令和终端位于同一个执行环境。
- FR-27.2 至少一个 Docker/容器隔离实现；最小权限运行、网络默认受限、受控挂载、资源配额、清理策略。
- FR-27.3 不将基于路径的 Java 检查宣传为可靠隔离；需按具体操作系统独立开展安全测试。
- FR-27.4 Web API：认证、创建/取消任务、事件订阅、工具审批、Diff 查看。
- FR-27.5 Desktop：本地运行时与 UI 生命周期协同；避免无鉴权开放执行接口。
- FR-27.6 SDK：使用版本化协议调用 Agent、消费事件、传递审批和取消操作。
- FR-27.7 V3 的功能对齐按官方选定 commit 的基线逐项评估，明确“支持/部分支持/不支持/不适用”。

## 7. 上游能力对照、核心数据与接口契约

### 7.1 上游功能对照清单（必须作为需求附件维护）

本节是 FR 与上游能力的对照表，也是**范围决策记录**。开发正式开始时必须建立并持续维护仓库内文件 `docs/requirements/upstream-capabilities.md`，其中每项标注：上游能力、对照 commit 下的实现证据、本项目判定（支持/部分支持/不支持/不适用）、目标版本、负责 FR 与验收方式。

| 上游能力 | 需要做出的范围决定 | 本项目判定（建议基线） | 对应 FR |
|---|---|---|---|
| PTC / `run_code` 程序化工具调用 | 支持、延期或排除 | 延期（V3 评估） | FR-22 |
| Workflow 编排 | 与普通子 Agent 委派分开定义 | 延期（V3 评估） | FR-21 |
| Goal / Todo / Plan / Schedule | 分别定义状态、生命周期与工具 | 部分支持：Todo/Plan V3；Goal/Schedule 延期 | FR-23 |
| 子 Agent 委派 | 独立上下文、预算与取消传播 | V3 支持 | FR-20 |
| Agent Teams | 多 Agent 协作 | 不适用/实验，不纳入主线 | FR-20.4 |
| MCP Client | 连接与工具发现 | V3 支持 | FR-19 |
| Skills | 受控技能文件加载 | V3 支持 | FR-19 |
| Web 搜索 / 网页获取 | 是否纳入主要能力 | V3 部分支持（默认需审批） | FR-26 |
| LSP | 是否纳入主要能力 | 延期 | FR-26.3 |
| 附件与图片输入 | 存储、历史恢复与模型传输 | V2 部分支持（按模型能力） | FR-08.6、FR-05.2 |
| 会话分叉、查询、全文搜索 | 不能仅用“恢复、列表、导出”代替 | V2 分叉/查询；V3 全文搜索 | FR-13、FR-24 |
| 输出 spill、持久投影与缓存 | 大输出与冷会话读取行为 | V1 引用/摘要；V2 持久投影 | FR-08.4、FR-13.4 |
| Profile / Bundle / Preset | 配置组合与会话能力选择 | V2 支持 | FR-17 |
| 文件交付、变更记录及展示 | 区分真实记录与模型生成的报告 | V1 支持 | FR-12 |
| 语音、浏览器/桌面操作 | 是否纳入 | 不适用 | 1.4 |
| 远程沙箱与多入口 | 执行环境与入口 | V3 支持 | FR-27 |

**范围决定规则：** 任一上游能力必须落在“支持 / 部分支持 / 延期 / 排除”之一，并绑定 FR 与验收方式；不允许以“后续再看”长期悬空。

### 7.2 Java 核心接口契约

以下接口是**可冻结的契约草案**（v1.0 的 7.1 仅作讨论草图，不足以承载文档承诺的能力）。字段级细节在实施时定型，但语义与职责不得收缩。

```java
// ---------- 插件与服务 ----------
public interface HarnessPlugin {
    String id();
    String version();
    String apiVersion();
    List<PluginDependency> dependencies();
    void start(PluginContext context);
    void stop();
}

public interface PluginContext {
    <T> Registration register(Class<T> serviceType, String key, T implementation);
    EventBus events();
    ConfigView config();
    PluginScope scope();          // 会话/Agent 级作用域，用于隔离注册与订阅
}

/** 可撤销注册句柄：close() 撤销该插件/作用域的注册、订阅与资源句柄。 */
public interface Registration extends AutoCloseable {
    void close();                 // 幂等
}

// ---------- 工具 ----------
public interface AgentTool {
    String name();
    String description();
    JsonSchema inputSchema();
    JsonSchema outputSchema();                 // 输出也需声明
    SideEffectLevel sideEffectLevel();         // READ_ONLY / WORKSPACE_WRITE / SYSTEM_IMPACT
    ToolResult execute(ToolArguments args, ToolContext context) throws Exception;
}

public interface ToolRegistry {
    Registration register(AgentTool tool, RegistrationScope scope);
    Optional<AgentTool> resolve(String name, ResolutionScope scope);
    List<AgentTool> visibleTools(ResolutionScope scope);   // 隔离的工具视图
}

public interface ToolResult {
    String callId();
    ToolStatus status();                        // SUCCESS/FAILED/DENIED/CANCELLED/TIMEOUT/UNKNOWN
    StructuredValue value();                    // 结构化输出（符合 outputSchema）
    ModelContent toModelContent();              // 面向模型的转换
    DisplayRecord display();                    // 可回放的展示信息（Diff/命令/退出码）
    Optional<ErrorInfo> error();
}

// ---------- 模型 ----------
public interface LlmClient {
    ModelCapabilities capabilities();                                 // 能力发现
    ChatResponse complete(ChatRequest request, CancellationToken cancellation);
    StreamHandle stream(ChatRequest request, CancellationToken cancellation);   // 流式能力进入接口
    PreparedRequest prepare(ChatRequest request);   // 显式准备阶段：落盘/预算/取消语义
}

// ---------- 会话 ----------
public interface SessionRepository {
    SessionWriter openWriter(String sessionId, WriteOwnership ownership); // 单写者/租约
    SessionReader openReader(String sessionId);                           // 只读、可并发
    List<SessionSummary> list();                                          // V2
    SessionId fork(String sessionId, EventId from);                       // V2
}

public interface SessionWriter extends AutoCloseable {
    EventId append(SessionEvent event);   // 追加后必须 flush；失败必须抛出让调用方阻止副作用
    void flush();
}

public interface CheckpointPolicy {
    CheckpointDecision beforeEachModelRequest(SessionState state);
    CheckpointDecision beforeTopLevelTool(ToolInvocation call);   // 有副作用时必须落盘成功
    CheckpointDecision atStepBoundary(SessionState state);
}

public interface SessionProjection {
    ChatHistory messages();
    List<ToolInvocationRecord> toolHistory();
    TaskStatus taskStatus();
    List<ApprovalRecord> approvals();
}

// ---------- 工作区与执行环境 ----------
public interface ExecutionEnvironment {
    FileSystemView files();
    ProcessRunner processes();
    TerminalSession terminal();      // V2
    EnvironmentId id();              // 本地/容器/远程
}

public interface FileSystemView {
    ExecPath root();                                   // 环境无关路径，不暴露本机 Path
    String readText(ExecPath path) throws IOException;
    FileChange writeText(ExecPath path, String content, VersionGuard guard) throws IOException;
    FileVersion versionOf(ExecPath path);
}

// ---------- 策略 ----------
public interface PolicyEngine {
    PolicyDecision evaluate(ToolInvocation call, ExecutionContext context);
}

// ---------- 事件总线 ----------
public interface EventBus {
    void publish(SessionEvent event);
    Subscription notify(Class<? extends SessionEvent> type, NotificationListener listener);
    void serial(SerialListener listener, int priority);
    <T> InterceptorChain<T> intercept(ExtensionPoint<T> point, Interceptor<T> interceptor, int priority);
}

// ---------- Agent Loop ----------
public interface AgentLoopFactory {
    AgentLoop create(AgentLoopDependencies dependencies);
}

public record AgentLoopDependencies(
    LlmClient llm,
    ToolRegistry tools,
    SessionWriter session,
    SessionProjection projection,
    PromptComposer promptComposer,
    EventBus events,
    PolicyEngine policy,
    CancellationToken cancellation,
    Budget budget
) {}
```

正式实现需进一步规定：线程安全、异常类型、同步/异步语义、生命周期、取消传播、事务边界、请求版本号、**写拥有权/租约、flush 语义、作用域解析规则、能力降级路径**。

### 7.3 ToolResult 统一结构

```json
{
  "callId": "call_001",
  "tool": "execute_command",
  "status": "success",
  "startedAt": "2026-10-13T10:00:00Z",
  "durationMs": 1500,
  "sideEffectLevel": "system_impact",
  "data": {
    "exitCode": 0,
    "stdout": "BUILD SUCCESS",
    "stderr": "",
    "truncated": false,
    "cleanupStatus": "clean"
  },
  "artifactRef": null,
  "error": null
}
```

`status` 候选值：`success`、`failed`、`denied`、`cancelled`、`timeout`、`unknown`。工具错误不应伪装为模型正常回答。`artifactRef` 用于 FR-08.4 的输出 spill 引用。

### 7.4 配置文件样例（示意）

```yaml
plugins:
  enabled: [llm-openai, tools-core, workspace-local, process-local, policy-default, session-jsonl]
  disabled: [tools-mcp]
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
session:
  store: jsonl              # 或 memory（测试）
  ownership: exclusive
process:
  timeoutSeconds: 120
  maxOutputBytes: 1048576
  spillToArtifact: true
security:
  approvalMode: ask-on-risk
  autoApproveReadOnly: true
```

不得将明文 API key 保存在仓库或共享配置内。

### 7.5 事件与字段规则摘要

| 事件类型 | 必需关联字段 | 说明 |
|---|---|---|
| `session.created`、插件/配置事件 | `sessionId` | Session 级，不填 `turnId`/`stepId` |
| `turn.started` / `turn.ended` | `sessionId`、`turnId` | Turn 级 |
| `step.started` / `step.ended` | `sessionId`、`turnId`、`stepId` | Step 级 |
| `model.requested` / `assistant.message` | `sessionId`、`turnId`、`stepId` | 必须可由此前日志重建 |
| `tool.requested` / `tool.started` / `tool.completed` | `sessionId`、`turnId`、`stepId`、`callId` | `tool.started` 表示意图已落盘成功 |
| `approval.decided` | `sessionId`、`turnId`、`stepId`、`callId` | 含决定、原因、决定者 |
| `checkpoint.failed` | `sessionId`（+ 可用则含 step） | 落盘失败，禁止进入工具主体 |

## 8. 非功能需求

| ID | 类别 | 需求与量化目标（项目设定，非官方基准） |
|---|---|---|
| NFR-01 | 正确性 | 必须能区分“工具执行成功”和“任务达到目标”；所有完成声明可追溯到执行证据 |
| NFR-02 | 可用性 | Windows 上通过 Maven Wrapper 一条命令构建；`doctor` 检测 Java/Git/Maven/API 设置与插件装配结果 |
| NFR-03 | 性能 | 在本地 SSD 的测试项目上，列举 1,000 个条目目标 ≤2 秒，不含网络与杀毒软件等干扰 |
| NFR-04 | 鲁棒性 | 大文件/超长日志不导致 OOM；文件与命令工具均有上限、超时与不确定状态语义 |
| NFR-05 | 安全 | 未授权工作区外读取/修改的自动化负面测试必须全部拦截；Host Shell 仍按高风险能力管理 |
| NFR-06 | 隐私 | 默认本地存储；日志脱敏；对外部模型的数据发送在使用说明中明确告知 |
| NFR-07 | 可测试性 | 核心 Agent/Tool/Policy/Plugin/Session 支持 Fake LLM 与内存 Session；主要错误路径有自动化测试；事件与检查点可被 testkit 断言 |
| NFR-08 | 兼容性 | 优先 Windows 10/11；之后 CI 覆盖 Linux 与 macOS |
| NFR-09 | 可维护性 | 公共接口版本化；统一异常、结构化日志、自动格式化和静态检查 |
| NFR-10 | 可观察性 | 每一步含关联 ID、工具耗时、审批决定、命令退出码和最终任务状态 |
| NFR-11 | 可重建性 | 任意 `model.requested` 都能仅凭其之前的事件重建语义等价请求；有自动化测试守护 |
| NFR-12 | 持久性 | 有副作用工具在进入主体前意图已确认落盘；单写者约束在并发测试下被验证 |
| NFR-13 | 可替换性 | 替换 Agent Loop、模型、工具注册表、会话存储至少各有一个自动化测试，且不修改其他模块 |

## 9. 开发技术方案

- 语言与构建：Java 21 LTS、Maven Wrapper、多模块 Maven。
- HTTP：Java HttpClient；JSON：Jackson；CLI：可选 Picocli；日志：SLF4J + Logback。
- 测试：JUnit 5 + Mockito（按需要），Windows 与 Linux CI；事件录制与 Fake LLM 断言工具在 M0 建立。
- 文件：Java NIO、必要时 Git/ripgrep 加速；Git 能力可用原生命令或后期 JGit。
- 进程：`ProcessBuilder` 起步，交互终端需要额外 PTY 库/适配层；Windows 进程树清理评估 Job Object。
- 持久化：V1 内存事件存储 + JSONL 单写者追加（含检查点）；V2 完整 JSONL Event Store 与查询；后续可接 SQLite。
- 插件：V1 自定义 Java SPI/服务注册表/生命周期/作用域，采用 `Registration` 撤销模型；**不直接照搬 Cordis 运行时**；热重载与隔离类加载器后置。
- 事件：自建轻量事件总线，明确通知/串行/拦截链语义，不引入完整响应式框架。
- Web：后续按需求选择 Spring Boot + SSE/WebSocket；非 V1 前置条件。

## 10. 研发迭代与工作分解（建议）

调整原则：**先建立后续能力依赖的机制，再实现功能**。M0 不再是“空工程底座”，而是“机制底座”。

| 里程碑 | 范围 | 必要交付物 | 退出条件 |
|---|---|---|---|
| M0 | 插件容器、服务接口、事件模型、内存 Session、检查点接口、测试设施 | 服务注册与依赖解析、`Registration` 撤销、三类事件语义、事件录制、Fake LLM、临时项目夹具 | 用一个测试插件替换 Agent Loop 与工具注册表并跑通既有测试；事件总线三类语义有测试 |
| M1 | 从 Session 派生请求的 Agent Loop、模型适配器、工具执行管线 | PromptComposer、`model.requested` 可重建、工具管线顺序、能力发现 | Mock 多步任务通过；任取一次模型请求可从日志重建 |
| M2 | 文件、工作区边界、Diff 与受保护修改 | 工具集、版本检查、原子写、超时不确定语义 | 修改项目不破坏无关文件；越权访问全部被拒 |
| M3 | 进程、权限、审批、预算与取消 | 命令执行、清理状态、策略引擎、审批事件、`runId` 与控制通道 | Maven 错误处理、拒绝执行、取消与进程清理测试通过 |
| M4 | Coding Agent MVP 与 CLI 闭环 | REPL/run、审批问答、交付报告、JSONL 检查点 | 真实项目端到端验收通过；崩溃不产生未记录副作用 |
| M5 | 持久化完整化、恢复、投影与版本迁移 | 磁盘 Event Store、恢复流程、`UNKNOWN` 核实、尾部损坏与版本规则 | 崩溃恢复无盲目重放；AC-08、AC-09 通过 |
| M6 | 流式、压缩、持久终端、多模型、配置组合 | SSE、压缩策略、PTY、Profile/Bundle | 长任务可压缩续跑且请求仍可重建 |
| M7 | 生态与生态对齐 | MCP、Skills、Subagent、沙箱、Web/SDK、Workflow/PTC 决策落地 | 逐项对照 `upstream-capabilities.md` 回归与安全验收 |

建议关键依赖顺序：M0 → M1 → M2 → M3 → M4 → M5 → M6 → M7。M2、M3 内的单元测试可并行实施。M0 的产出是后续所有里程碑的前置条件，**不得为追赶 MVP 而压缩 M0 的服务与事件机制**。

## 11. 验收测试规范

### AC-01 项目探索

**Given** 授权 Maven 项目；**When** 输入“分析代码结构和构建命令”；**Then** Agent 使用读取/搜索工具找到 `pom.xml` 与主源代码目录，回答能够指向真实文件，不凭空编造。

### AC-02 新建功能

Given 简单计算器项目；When 输入“增加乘法方法和对应测试”；Then 系统创建/修改正确文件，运行测试并展示变更与退出码。

### AC-03 请求可重建

Given 一个已完成若干步的会话；When 取任意一条 `model.requested` 事件并仅使用其之前（含）的日志重建请求；Then 重建出的系统说明、消息序列、工具声明与参数与原始请求语义等价；压缩/裁剪发生时有对应事件解释差异。

### AC-04 自主修复

Given 预置一处 Java 编译错误；When 输入“修复编译错误并运行测试”；Then Agent 能检测失败、定位并修改、重新执行测试；如果无法修复必须报告失败，不伪称成功。

### AC-05 路径越权

Given 工作区之外存在测试文件；When LLM 请求读取 `../outside.txt` 或经链接跳转；Then 被拒绝、生成事件，外部文件未被读取或修改。

### AC-06 危险操作审批

Given 工具提出删除目录或强制重置 Git；When 用户选择拒绝；Then 操作完全不发生，模型获得明确 denied ToolResult，且产生 `approval.decided` 事件。

### AC-07 执行超时与取消

Given 测试程序持续输出或长时间阻塞；When 达到超时或用户取消；Then 进程服务结束任务、停止收集、释放资源，状态明确，并显式报告 `cleanupStatus`。若清理不完整（如仅能终止直接子进程），必须报告残留 PID 与限制条件，而不是声称已清理干净。

### AC-08 文件并发变更

Given Agent 读取文件后用户手工修改；When Agent 提交基于旧版本的编辑；Then 检测冲突或重新验证预期文本，不无提示覆盖用户修改。

### AC-09 崩溃恢复（V1 起适用）

Given 崩溃发生在危险工具执行与结果写入之间；When 恢复任务；Then 该工具被标记 `UNKNOWN` 并要求人工核实，不自动重试。
**Given** 执行意图落盘失败（模拟磁盘不可写）；**When** 工具请求执行一个有副作用操作；**Then** 工具主体从未进入，返回明确失败，且日志中可看到失败记录。

### AC-10 持久化约束（V1 起适用）

Given 同一会话由两个写入者并发打开；Then 第二个写入者按写拥有权策略被拒绝或等待，日志不会出现交错/重复追加。  
Given 日志尾部存在半行或非法记录；Then 加载时按策略报告并处理，中段损坏不被静默忽略。  
Given 日志版本高于当前实现；Then 拒绝写入并以只读方式明确报告。

### AC-11 扩展点与拦截链

Given 一个仅通过拦截链与事件订阅实现的“工具参数改写 + 只读工具免审批”插件；When 运行既有任务；Then 行为生效且未修改 Agent Loop、工具实现与策略引擎代码；拦截链顺序符合优先级定义。

### AC-12 插件卸载与作用域隔离

Given 工具插件已注册事件监听和工具；When 插件卸载（或 `Registration.close()`）；Then 注册被撤销、事件监听被释放、相关资源关闭，调用已撤销工具返回明确错误；父子 Agent 或多会话的工具/监听器/权限贡献互不可见。

### AC-13 服务替换与配置装配

Given 通过配置禁用模型提供方或替换 Agent Loop 实现；Then 系统给出明确可诊断的结果（启动失败并说明缺失服务，或使用替换实现正常运行），且无需修改核心模块代码；插件启动失败不留下半启动注册残留。

### AC-14 dry-run 与未知副作用

Given 一个包含 Shell 重定向与子进程调用的工具计划；When 请求 `--dry-run`；Then 明确文件操作给出可预览变更，无法预测的命令被标注“无法预览”，系统不宣称计划“无变更”。

### AC-15 真实记录与模型叙述分离

Given Agent 声称修改了某文件但工具记录中没有对应 `tool.completed`；Then 交付报告的变更列表中不包含该文件，且报告明确区分“记录事实”与“模型说明”。

### AC-16 执行环境切换（V3）

Given 同一任务可在本机与容器执行；When 替换 `ExecutionEnvironment`；Then 文件、Shell、工作目录保持同一执行世界，不出现跨环境路径错误，且路径以环境无关形式暴露给工具。

## 12. 风险、依赖与决策记录

| 风险 | 影响 | 缓解策略 |
|---|---|---|
| Shell 能力越权 | 数据丢失、泄露或系统损坏 | 工作区边界、审批、默认最小权限、V3 真正沙箱 |
| LLM 输出不可靠 | 误改代码、虚报完成 | Schema 校验、Diff、真实编译与测试验证、记录与叙述分离（FR-12.3） |
| 模型协议差异 | Tool Calling 不稳定 | Adapter 层、能力发现（FR-05.2）、契约测试、明确支持矩阵 |
| 上下文膨胀 | 高成本、质量下降 | 输出截断、spill 引用、摘要与按需读取，且压缩以事件记录 |
| Windows 进程树难清理 | 遗留进程 | 进程树追踪、Job Object 评估、显式 `cleanupStatus` 与残留报告，不作结果保证 |
| 插件机制在 V1 过重导致 MVP 推迟 | 交付延迟 | 严格区分“静态装载 + 生命周期”（V1 必做）与“热重载/隔离类加载器”（后置）；M0 限定为最小机制集 |
| 事件日志成为性能瓶颈 | 每步额外 I/O | 批量/异步 flush 与显式检查点边界；仅在有副作用执行前强制同步落盘 |
| 多 Agent 或插件作用域串味 | 权限与工具泄漏 | 作用域化注册与解析（FR-01.10）、隔离测试（AC-12） |
| 直接跟随官方主分支 | 需求漂移 | 固定对照 commit，建立并维护 `upstream-capabilities.md`（7.1） |
| 范围决定长期悬空 | 需求不可验收 | 每项上游能力必须判定为支持/部分支持/延期/排除并绑定 FR |
| 第三方许可和依赖 | 发布障碍 | 记录许可证、SBOM、依赖安全扫描 |

### 待定决策

以下决策**允许推迟**，但必须绑定里程碑与决策期限；不再有“不会阻塞任何事”的悬空项。

| 决策 | 截止里程碑 |
|---|---|
| 首发模型提供方与是否配置多个备用模型 | M1 |
| Windows 原生 Shell 与 WSL 的默认优先级 | M3 |
| 第一版自动审批的最小可信命令集合 | M3 |
| 是否对 Git 工作区执行修改前自动创建可恢复快照 | M2 |
| JSONL 与 SQLite 的最终优先级（当前建议 JSONL 优先） | M5 |
| `jharness cancel` 的控制通道实现（socket / 命名管道 / 文件信号） | M3 |
| Windows 进程树清理是否采用 Job Object | M3 |
| PTC / `run_code` 是否纳入（当前建议延期） | M7 前 |
| Workflow 编排是否纳入（当前建议延期） | M7 前 |
| Web 搜索与 LSP 是否纳入主要能力 | M7 前 |

## 13. Definition of Done（完成定义）

一项 P0 功能被视为完成，当且仅当：

1. 对外功能符合对应 FR 与 AC 条款。
2. 有自动化单元测试覆盖正常与错误路径；安全敏感路径有负面测试。
3. 所有模型发起的真实本地操作可追溯到工具调用记录与用户权限决策。
4. 错误、拒绝、超时和取消能够形成明确状态，不吞异常、不伪造结果。
5. 有副作用操作满足“执行前意图已落盘”；模型请求满足“可由事件重建”（NFR-11、NFR-12）。
6. 新增能力通过服务注册与扩展点接入，未以修改 Agent Loop 的方式实现横切逻辑。
7. README 更新了命令、配置、风险与已知限制。
8. CI 构建与相关测试全部通过。

**V1 发布门槛：** AC-01～AC-15 全部通过（AC-16 属于 V3）；能在至少两个不同的预置 Java/Maven 示例项目上完成“探查→修改→构建/测试→必要时迭代→报告”；未经审批的高风险操作不得执行；存在一个可替换 Agent Loop 或工具注册表的测试插件证明架构可扩展。模型效果具有随机性，实际评测必须固定任务集、记录模型与配置并多次重复，不以单次成功作普遍能力证明。

## 14. 参考资料和版本管理

1. DeepSeek AI. **DeepSeek Harness**. https://github.com/deepseek-ai/deepseek-harness
2. DeepSeek AI. **Architecture**. https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/architecture.md
3. DeepSeek AI. **Safety notice**. https://github.com/deepseek-ai/deepseek-harness/blob/master/SAFETY.md

**版本管理要求：** 开始实际复刻时记录 `git rev-parse HEAD` 的官方提交哈希，建立 `docs/requirements/upstream-capabilities.md` 功能对照清单（结构见 7.1），并作为本文件的必备附件。本文件中的 V1/V2/V3 是 J-Harness 自定义开发阶段，不与官方版本号对应。

**上下游关系：** `JHarness-PRD-v1.0.md` 保留为历史基线；本文件 1.1 为当前需求基线，后续修订以本文件为准并追加修订记录（见 0.1）。
