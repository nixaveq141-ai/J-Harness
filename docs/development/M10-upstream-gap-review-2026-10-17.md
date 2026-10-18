# 排除 M11/M12 后的 DeepSeek Harness 差距复核

2026-10-17。J-Harness基线为本地提交2c26a2b；参考为用户指定只读目录`D:/Documents/my projects/deepseek-harness`，HEAD为d743267388641bc76f17c45ce8b4c231aed1d32c。仅静态阅读源码与本地文档，没有安装、构建、启动、测试或修改参考项目，不代表网上最新版本或上游运行验收。

## 排除范围

按JHarness-next-plan-v1.0.md的实际条目排除：M11桌面宿主；M12图片/多模态、终端/进程强化、插件热重载/类加载器、Goal/Schedule、Workflow、PTC、Web搜索/LSP。Skills目录监视/动态更新和MCP热更新不重复列为本次待补项目。Teams、语音、浏览器/其他桌面软件自动化及Cordis格式兼容属于既有非目标，不作为必补差距。

以下优先级是审查建议，不改变M11/M12顺序，不自动立项或开始实现。相比完整的日常工作界面，当前Web仍偏单次任务工作台；底层已有能力不能直接算成Web用户已经能使用。

## 主要差距

| 编号 / 建议优先级 | 参考项目的实际入口 | J-Harness现状与不足 | 范围判断 |
|---|---|---|---|
| G01 / P0 多轮交互 | ui-chat、ui-conversation具备会话输入、队列/steering与历史对话装配 | App仅在未选择任务时显示目标输入框；Web创建任务后只支持读取、审批、取消等，没有同会话后续消息入口。CLI REPL/SDK已有继续运行基础，不能称整个项目没有多轮能力 | Web接口和产品流程缺口；不等同M12长期Goal |
| G02 / P0 工作区与会话管理 | ui-workspace可选择工作区、重命名、置顶、分叉、归档与恢复会话 | serve固定当前工作区/default profile；页面缺少工作区管理、会话重命名/分叉/归档等。CLI已有分叉/导出/归档部分能力，Web未接入。Web catalog硬上限32条，已结束任务也占记录，达到上限后无法继续新建，重启仍加载同catalog | 常规会话管理和容量生命周期，不属于长期目标或桌面专属 |
| G03 / P1 向用户提问 | interaction/tool-ask-user注册ask_user_question，ui-user-questions提供选择/文本答案及草稿 | 产品只有工具批准/拒绝，没有相应模型提问服务、结构化问题工具和回答卡片。审批不能替代需求澄清、方案选择 | 需要补模型工具/服务、持久状态与UI；不能把开发工具中的提问能力算入产品 |
| G04 / P1 对话、文件与产物展示 | ui-chat、ui-sidebar-files、ui-deliverables提供对话装配、文件树、点击预览、变更/交付卡及review入口 | 当前事件名称/JSON仍偏底层，助手文本未形成完整Markdown/代码阅读体验；工具多为通用事件行。没有文件树/工作区文件预览，产物主要为引用和事实报告，Diff依赖既有git_diff记录；CLI/SDK的流式能力尚不等于Web逐字输出，serve流式回调写入空输出流，Web读取持久事件 | 普通文本/代码、已有文件、逐字显示与事件投影改进；图片和复杂文档不计入 |
| G05 / P1 模型选择及协议 | ui-model-selection按会话选择模型/推理档；providers与protocol-label明确三类协议：Chat Completions、Responses、Anthropic Messages | 当前模型连接增删改/默认选择已实现，不能再说没有设置；但任务配置只显示default，缺少直接模型选择/能力引导。内置适配器固定/chat/completions，换URL/模型名不能获得Responses/Anthropic原生协议。超时/上下文/输出能力等仍多来自启动配置 | 配置体验与模型适配差距，非多模态。系统凭据目前只适配Windows，其他系统仍用环境变量 |
| G06 / P1 扩展使用便利性与MCP资源 | ui-skill提供/name候选和技能卡；skill-filesystem提供多根目录与frontmatter目录；mcp-resources注册资源/模板列表和读取工具 | Planning/压缩开关已实现，MCP/Skills/子任务主要编辑JSON；没有可视化条目表单、技能目录选择及slash调用。Skills是显式白名单快照；MCP目前桥接tools/list、tools/call，缺少资源/模板发现读取 | 不把插件安装/升级/热重载算进来。不声称上游支持MCP prompt templates：其README明确不支持；也不将OAuth列为已核实上游优势 |
| G07 / P0 错误解释与恢复引导 | ui-chat提供失败行和终止回合展示；ui-approval/ui-permission-presets有专门交互与权限选择 | 当前英文事件/底层错误/大结果省略使用户难以判断“什么失败、影响什么、下一步做什么”。UNKNOWN只给通用离线说明，没有与具体命令、超时、清理和未决事实相连的用户摘要；现有权限配置也缺少页面上的清晰选择/作用范围说明 | 改善解释、检查和受控恢复入口，不能删UNKNOWN保护或伪造核实；OS清理强化仍归M12 |
| G08 / P2 历史检索与外部SDK | session-query-sqlite提供可选跨会话ranked FTS5；SDK为stdio JSON-RPC，提供TS与Python客户端 | 现有派生索引是有界单会话payload子串检索，Web侧栏筛选ID/状态不等于跨会话内容搜索；外部集成以Java嵌入SDK与本机任务HTTP为主，没有同等TS/Python SDK与stdio协议客户端 | 可按真实集成需求选择，不要求为了Java定位复制Cordis或全上游协议 |
| G09 / P1 当前版本验证深度 | 上游存在多个UI/协议/客户端测试入口；本次未运行，不能据此宣称质量更好 | 当前版本证据是Windows11/Chrome本地314项Java、9项客户端、28项工作台/21项设置/8项入口。M9真实三平台绑定旧源码，不能代替当前前端/凭据/入口版本的跨平台认证；缺少针对多服务商与真实日常开发场景的新版本证据 | 验证缺口；不推断上游更快、更稳定或测试覆盖更高，不启动付费评测 |

## 建议先解决什么

先解决G01、G02、G07，使用户能连续完成工作、管理历史且能看懂失败；尤其应先处理32条目录容量与安全归档生命周期，不能简单删除审计记录或调大数字冒充无限历史。随后完善模型选择、问题卡、文本/代码/产物查看和扩展表单。新模型协议、跨会话检索和外部SDK按实际需求决定。

UNKNOWN是正确的保守执行状态，不是单独一个应移除的缺陷。本次对话中120秒命令超时、可能已经产生副作用的例子，说明需要更好的用户解释与检查流程；不证明上游同一命令一定成功。普通命令适配/工具参数提示与模型行为评测也需改进，但不能把模型会自我修正当作产品可靠性保证。

## 主要源码依据

参考根路径以下简写为DSH；完整路径与SHA在M10-gap-review-results.json中记录。

- DSH `packages/client/ui-workspace/src/client/session-actions/{ForkSession,ArchiveSession}.tsx`、`ui-workspace/README.md`；J-Harness `HarnessWebServer.java`的路由/submit/restore与`EcosystemCli.java`的serve装配。
- DSH `packages/client/ui-conversation/README.md`、`ui-chat/README.md`与`ui-conversation/src/client/locales.ts`；J-Harness `frontend/src/App.tsx`的composer条件、事件/报告/Diff组件与`EcosystemCli.open`的流式回调。
- DSH `packages/interaction/tool-ask-user/src/index.ts`、`packages/client/ui-user-questions/README.md`；J-Harness现有工具注册及Web审批路由。
- DSH `packages/client/ui-sidebar-files/src/client/FilesBody.tsx`、`ui-deliverables/README.md`、`ui-settings-models/src/client/protocol-label.ts`、`docs/user/guide/providers.md`；J-Harness `OpenAiCompatibleConfig.java`、`Settings.tsx`与`WorkbenchSettings.java`。
- DSH `packages/mcp/mcp-resources/src/tools.ts`、`mcp-client/README.md`、`packages/skill/skill-filesystem/README.md`、`packages/client/ui-skill/README.md`；J-Harness `McpPlugin.java`和`SkillsPlugin.java`。
- DSH `packages/session-query/session-query-sqlite/README.md`、`packages/sdk/README.md`；J-Harness `SessionSearchIndex.java`、CLI会话维护与M10-auto验收记录。

核验方式：静态源码/文档交叉检查，参考目录监测集合1954份文件前后SHA一致、HEAD一致；精确范围见取证JSON，不宣称全仓所有文件已审查。没有运行产品测试或新增功能验收，没有改变M11/M12内容。

2026-10-18 G06接续：M10.5.7已完成本地验收，扩展表单、显式技能目录/候选与MCP文本资源/单变量模板链路见[M10.5.7验收](../testing/M10.5.7-validation.md)。本表仍保留2026-10-17固定源码的差距快照，不重写上游比较或宣称完整协议兼容。

2026-10-18 G08接续：M10.5.8内容检索、可替换索引、独立stdio协议和TS/Python SDK已完成本地验收，见[M10.5.8验收](../testing/M10.5.8-validation.md)。原表保留固定源码差距快照；不宣称SQLite/Cordis或上游完整协议兼容，G09整阶段跨平台门禁仍由M10.5.9完成。
