# M10 前端复用评估：DeepSeek Harness

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](current-state.md)及[全阶段审计](../testing/M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

本文件保留M10实施前的静态评估快照。用户随后确认视觉/交互复用并完成本地实施；当前结果见[M10契约](M10-contracts.md)和[M10验收](../testing/M10-validation.md)。下文“未开始”等描述属于评估当时。

日期：2026-10-16。结论：**适合选择性移植基础展示组件、主题样式及布局交互；不适合把完整前端原样连接到当前 J-Harness 后端。** 推荐独立前端入口与 J-Harness API 适配层，按文件引入所需组件。当前仅完成静态评估，未开始 M10 实现，也未选定最终前端框架或移植范围。

## 评估对象与边界

- 用户指定参考目录：`D:/Documents/my projects/deepseek-harness`，严格只读。没有安装依赖、构建、启动服务、修改源码或 Git 配置，也没有复制上游产品代码。
- 本地 HEAD：`d743267388641bc76f17c45ce8b4c231aed1d32c`；包清单版本 `0.2.1-alpha.2`。结论针对本地实际文件，关键文件 SHA 见 `M10-frontend-reuse-source.json`，不宣称代表网上最新版本。
- 方法：读取启动入口、构建配置、包依赖、架构说明、主题、代表性组件和 J-Harness 路由/认证源码。使用 impeccable 的代码审查维度，但本次是复用可行性评估，不是完整 UI 质量验收。
- 未实际渲染浏览器，未运行上游测试或测量 bundle 大小、性能、色彩对比度和移动端可用性；源码中存在相应机制或测试文件不等于已验证通过。未给出虚假的视觉/a11y 评分。

## 架构事实

`apps/web/src/main.ts` 主要创建 `AppWebEntry`，实际界面分布在 `packages/client/*`，该组包含 63 个包清单。技术栈使用 React 18、TypeScript、Vite 和 CSS Modules，但并非独立 React 模板。

`apps/web/vite.config.ts` 明确拒绝 standalone Vite serve：需要 Host 注入 `window.__DSH_BOOT__`。`client/web/src/boot-client.ts` 通过 vendored Cordis Loader 启动插件图；布局和业务视图依赖 Slots、注入服务、框架生成的 hooks 和 store。仅复制 apps/web 或修改 base URL 不足以启动完整 UI。

上游 Web Client 架构按 Host → Remote → Client models → UI adapters → Conversation → Slots → React 组织。业务界面消费 `ctx.remote` 生成接口和配套 Session/Workspace 模型，不直接消费 J-Harness 的任务响应。

## 复用清单

| 范围 | 可复用程度 | 必要工作 |
|---|---|---|
| Button、Input、Tag、StateDot、DisclosureRow 等基础展示组件 | 较高，适合按文件移植 | 保留 React/CSS Modules 及局部依赖；补齐所用主题变量、标签和测试；不整包导入品牌素材 |
| Modal、Menu、Tooltip、键盘/焦点辅助 | 可提取，需连同辅助代码审查 | 核对 portal、层级、Escape、焦点恢复和页面滚动锁；在新宿主实际验证 |
| DiffBlock、CodeCard、TerminalBlock、Markdown 展示 | 有价值，需数据与依赖适配 | 提取完整内部依赖闭包；审查 diff/shiki/katex/markdown 等实际引入范围、资源加载和内容安全；Diff 输入要由真实记录派生 |
| 主题 CSS、字体栈、明暗色变量 | 可提取样式，不直接搬主题插件 | `base.css`、`design-platform.css`、`focus.css` 等可作为基础；替换品牌；J-Harness 自行管理偏好/DOM 标记 |
| 三栏布局、侧栏折叠/拖拽 | 交互逻辑可参考或提取，AppFrame 非即插即用 | AppFrame 使用 Slots/store/会话 hooks，需要改成新宿主组件接口；宽屏逻辑不能直接认作手机适配通过 |
| 聊天/会话/任务列表及时间线 | 需较多适配 | 替换 Session/Workspace 模型、事件投影和分页逻辑，建立 J-Harness task/event 到展示模型的明确映射 |
| 审批面板 | 可复用展示思路，业务逻辑重接 | 上游使用 `approval/request` waterfall、PendingApproval 和 composer slot；本项目必须使用 taskId/approvalId/callId 与已决/过期检查 |
| Web 启动、认证、RPC、插件加载 | 不建议本阶段整体移植 | 本项目建立自己的静态入口、连接和认证方案；不要为使用几个 UI 组件引入完整 Host/生成器体系 |
| Goal、Schedule、Workflow、语音、浏览器/桌面控制、复杂文档预览 | 不纳入 M10 默认复用范围 | 没有后端能力的入口不展示为可用；仍按既有需求计划单独立项 |

基础组件并非完全没有依赖。例如 Button 使用 React、clsx 和局部 CSS；DiffBlock 使用 diff、代码工具栏、复制/折叠辅助和主题。`ui-primitives` 包根入口还导出品牌及重型展示能力，因此应做逐文件依赖审查，不能把“Cordis-free”理解成整个包零依赖。

## 必须适配的协议与安全语义

| 主题 | DeepSeek Harness 本地源码 | 当前 J-Harness | M10 处理要求 |
|---|---|---|---|
| 浏览器启动 | Host boot manifest + 模块图/Cordis Loader | 当前没有静态前端入口 | 独立构建及托管契约，不伪造上游完整 Host |
| 认证 | 启动 URL token 交换成带签名 HttpOnly Cookie；RPC 不接受 Authorization token | `Authorization: Bearer`；精确 Host；拒绝全部 Origin | 先设计同源/可信 Origin 和认证，不直接复制 URL token 流程；本项目要求 token 不进入 URL/日志/模型 |
| 实时通信 | 生成 Remote 方法；逻辑流复用 `/api/remote.mux` WebSocket | `/v1/tasks` HTTP API、分页与有限 SSE | 新建适配层；读取流不能触发再次提交；使用可携带认证的流读取方式，不能把 token 拼进 EventSource URL |
| 任务身份 | Session/Workspace 模型与 prompt/control streams | profile/goal/requestId，task catalog 与绑定占用 | requestId 在重复点击/网络重试中保持一致；不要照搬“重发消息”的行为 |
| 审批 | `approval/request` waterfall 和 pending interaction | `GET approvals`、精确 approvalId/callId 决策、409 STALE_APPROVAL | 审批卡基于服务端事实；超时、已决或关联错误不得补发旧决定 |
| 断线与取消 | 自有连接 generation、Session baseline/follow | 断线不重提，cancel 不代表回滚，UNKNOWN 阻止自动恢复 | 状态明确分开，不能把断线/UNKNOWN简化成失败后重试 |
| Diff 与大事件 | DiffBlock 接收 oldText/newText；自有历史模型 | diff 仅返回记录的 git_diff 工具结果；大 payload 可省略 | 适配真实数据或显示缺失/省略；不得让组件补跑 Git、读取文件或据残片伪造完整 Diff |

J-Harness 依据：`harness-web/src/main/java/io/jharness/web/HarnessWebServer.java` 的 handle/authorized/route/event 等实现，以及 M10 推进计划。当前拒绝全部 Origin 是已知接入前置问题，不能靠删除检查解决；同源托管仍需明确合法浏览器请求的准入策略。

## 许可与品牌核对

本地根 LICENSE 和相关包清单声明 MIT，原文允许使用、复制和修改，要求在副本或实质部分保留版权及许可声明。若后续实际移植，保留原有 MIT 归属与修改来源；J-Harness 自有代码 Apache-2.0 声明不替代上游 MIT。

`THIRD_PARTY_NOTICES.md` 明确外部依赖各有许可，完整 npm 传递依赖见 pnpm-lock；仓库整体依赖很多，不能把整份清单当作精简前端的实际依赖，也不能只保留根 MIT 就忽略引入的依赖。若引入 Montserrat 字体，保留其 OFL；Vite 配置已有复制 Montserrat-OFL.txt 的逻辑。

`BRAND_GUIDELINES.md` 对名称/官方素材及官方关联表达另有说明。J-Harness 使用自己的名称、图标及产品文案，避免直接复制 FishLogo、BrandWordmark、favicon 和官方品牌插件。这里记录本地许可/品牌文件内容，实际分发前按最终复制清单重新核对。

## 推荐落地顺序

1. M10.1 契约：确定同源托管、认证、静态资源分发和前端构建方式，列出任务/事件/审批/错误 DTO，明确有限 SSE 与 UNKNOWN。
2. 选取少量基础组件、必要 CSS 变量和三栏布局逻辑，在 J-Harness 中形成最小可运行界面；建立引入来源、文件 SHA、许可和本地修改记录。
3. 先跑通真实连接/创建/列表/详情，再接事件续读、审批/拒绝/取消，最后接记录型 Diff、报告和会话查询；不提前搬全部上游菜单。
4. 按 M10 验收实际浏览器的重复点击、刷新/断线、失效审批、UNKNOWN、脱敏、窄屏、键盘和空状态；移植后的功能需要重新验收，上游测试结果不替代本项目证明。

当前不承诺节省百分比或工期。可以减少基础 UI 的重复开发，但本阶段主要风险仍是协议、状态、安全语义与浏览器集成。完整原样复用通常需要兼容上游服务模型、协议、插件图与构建工具，范围明显大于按需移植。
