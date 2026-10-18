# 模型连接与逐轮选择

在 Web UI 的“设置 → 模型与连接”添加连接，填写连接名称、基础 API URL、模型 ID 和 API Key。密钥保存在当前用户系统凭据库；也可填写启动宿主时已有的密钥环境变量名。保存不会发模型请求。

API 协议可选择 Chat Completions、Responses 或 Anthropic Messages。基础 URL 可包含 /v1，不要追加具体 /chat/completions、/responses 或 /messages 路径，不要放入密钥、查询串或 URL 用户信息。仅允许 HTTPS 或本机 loopback HTTP。

展开“能力与请求限制”，按服务实际能力设置上下文 token 上限、输出 token、请求超时、工具和流式能力。Responses 可单独设置推理强度；其他协议不继承该字段。当前支持文本与函数工具调用，不支持图像、Anthropic thinking/signature 或并行工具执行。服务返回不支持内容会明确失败，不静默丢弃后执行。

费用限制由操作者核实并填写，不查询价格。切换 URL、模型或协议会保留已启用费用上限并清空旧价格，缺价格时不能保存；也不会借切换模型取消预算。设置显示的是每连接实际生效值。

“检查已保存配置”仅验证本地配置和凭据可读性，明确返回未连接服务、未发送模型请求。它不证明提供方鉴权、网络可达或模型真实支持该能力。检查期间锁定编辑与连接切换，结果带连接名称。

任务输入区的“模型”下拉框显示连接名、模型名和服务域名。续聊区另有“下一条消息模型”；上方“当前轮模型”来自已受理事实。选择仅影响接下来发送的消息，不修改全局默认或在途请求。从设置返回初始任务输入区时重新读取保存的默认连接。

每条初始/后续消息在受理时冻结模型、协议、能力、超时、费用及设置修订。忙碌时排队的消息仍使用自己的原快照；宿主重启后保持暂停，明确开始时也不改用新的默认连接。原凭据不可用时暂停并提示恢复原凭据，或取消排队后重新提交；不使用别的连接或取消预算兜底。旧队列没有快照时需取消后重新提交，不推测原模型。

CLI 也支持新增顶层 protocol 与 reasoningEffort 字段，例如：

    {
      "baseUrl": "https://provider.example/v1",
      "model": "operator-selected-model",
      "protocol": "responses",
      "apiKeyEnv": "JHARNESS_API_KEY",
      "contextTokens": 65536,
      "maxOutputTokens": 4096,
      "requestTimeoutSeconds": 90,
      "toolsSupported": true,
      "streamSupported": true,
      "streaming": true,
      "reasoningEffort": "low"
    }

protocol 可为 chat-completions（缺省）、responses、anthropic。reasoningEffort 只用于 Responses，可为 none/low/medium/high；服务不支持时按实际失败处理，不自动切模型。Responses 不使用 disableThinking，请使用适用的 reasoningEffort。保留 CLI 旧版已声明的非流式退回行为；Web 设置拒绝矛盾流式选项。

公共 API/SDK 为 1.10；ChatResponse 增加不可变 providerState，保留三参数构造器和既有访问器，旧 record 解构需适配。JSONL/schemaVersion 仍为 1；Web 目录先独占备份再升级到 4，旧宿主拒绝按默认模型执行新格式队列。Responses 的加密续接项绑定原服务地址、逻辑模型和协议保存，不能跨服务套用。

验收与限制见 [M10.5.4 验收](../testing/M10.5.4-validation.md)。三平台原生凭据另有历史专项证据；新协议验证使用本机替身，不代表服务商真实可用性或新版本三平台全量发布矩阵。
