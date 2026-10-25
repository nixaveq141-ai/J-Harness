# R6 发布门禁修复与 M10.5.9 收尾

2026-10-25，基线 e5fd815。原186条采纳范围按[逐项台账](OCR-R6-scope-2026-10-25.json)保留，不把意见数当作独立缺陷或186次动态故障注入。当前先完成本地最终输入验证，再核对已授权专用分支的当前原生专项和三平台完整 CI；尚无三侧最终证据时不标记 M10.5.9 完成。

## 发布链路

- Maven 3.9.9 的固定 ZIP 摘要不变；JDK-only bootstrap 保留认证用 ZIP，每次核对已解压缓存的完整文件集合和摘要。新缓存使用同一文件系统 staging/原子发布；缺完成标记、ZIP/已解压文件篡改均拒绝。只支持固定 ZIP、公开 HTTPS 镜像，不再用 ZIP 摘要校验 tar.gz，也不向 URL userinfo、跨主机 Authenticator 或日志传递镜像凭据。原缓存不当作可信新缓存，未自动删除用户缓存。
- 集中 Jackson 2.21.7/annotations 2.21，固定实际使用生命周期插件，enforcer 校验 JDK/Maven 与依赖收敛；pty4j/Kotlin 对 JetBrains annotations 的不同路径统一到原实际已审计24.0.1。slf4j-nop 由可执行宿主拥有，testkit 的集成依赖限制为 test。
- npm Node24、tsx4.23.15 与其声明兼容的 esbuild0.28.1固定组合；ui-files 严格上限/完整集合，允许合法点号 chunk。上游管理文件完整、唯一、根内常规文件且逐份摘要一致。完整生产 npm 闭包收集许可；ui-build.json 记录当前构建输入和输出，候选与 CI 收集器独立拒绝缺/陈旧 dist、额外输出、包内资源不一致。纯后端构建不被强制安装 Node。
- 候选 legal/SDK 输入使用真实边界和常规文件检查，legal 提取限坐标、POM/ZIP条目及总字节，完整group:name选择补充材料。运行JAR只包含受管法律材料；原件文本保留，不改作者年份、原文重复或未证实 NOTICE。
- OSV 的格式/输入/分页/响应/记录预算失败会撤销旧 PASS；source-pattern 在读取前判界，拒绝链接/异常路径，以结构化 INCOMPLETE保留失败，输出位置而非秘密原文。SDK 对全部分发执行源用 Python AST/TypeScript compiler AST 检查静态依赖，覆盖副作用 import、导入别名和示例；这不是任意程序行为沙箱。

## 运行与证据

- 启动器持续有界消费双流，保留 WEB 地址并在截止前重试就绪；与异步输入竞争检测宿主退出，传播自然非零退出。危险环境槽扩展，Java探测逐候选隔离失败。实测包括保存配置唯一模型标记、受控浏览器调用、自有Java PID/创建时间和原句柄退出信号、端口重绑；不打开用户浏览器或改剪贴板。
- 文档 SDK 示例使用独立演示密钥环境槽、10秒请求预算、明确终态/未完成及 UNKNOWN 只读核对提示；保留 requestId，不自动重发。过滤示例锚定根 target，明确基础与primary路由独立及费用的保守预留语义。
- 探针入口先验证输出/JAR，流式散列前后绑定；完整失败记录不沿用旧 SUCCESS，报告原子替换，清理范围限受管资源。MCP fixture按id/method处理通知并显式UTF-8；Docker脚本区分宿主平台，仅为自己新建夹具设置非root所需权限，精确owner label检查，不能把静态配置当实际沙箱证明。探针不默认在普通单测中启动Docker。
- 浏览器版本探针不等于浏览器验收。私有Firefox准备有失败收据、链接/重解析边界、复制前后源与目标完整集合验证及自有目录清理；本机Firefox禁启继续遵守，只在授权的远端runner实测。macOS恢复原默认/搜索keychain，Linux确认Secret Service owner及集合锁状态，工作流按workflow/ref取消过期执行。
- Java契约保留原700个身份，新增UI清单、探针失败与JAR变化两项；既有Wrapper集成按当前固定摘要ZIP/完成标记验收，不再依赖旧日志文字。历史迁移/所有权/内容/压缩和observer/MCP负例增强精确码和字节/快照断言。

## 保证边界

文件force与rename不等于所有平台父目录fsync或断电保证；保留此范围说明，不伪造Windows目录fsync。支持CPU以当前runner报告为准，pty4j存在原生文件不等于所有架构通过；上游Microsoft二进制的完整可重现构建来源仍未证明。来源归档是固定提交的git archive，不冒充GitHub自动tarball摘要。既有test-only诊断建议与没有证明的高严重度子主张按原裁决澄清，不能宣告由它们证明不存在的安全缺陷。

原失败与修正尝试保留 `.build-cache/R6-2026-10-25/`；历史第21轮Windows原ZIP摘要3e247175cd59，显示凭据可用性失败而没有更细原因，不事后猜测。当前新增固定分类，不扩大原始日志上传。本地和远端最终证据见[验收记录](../testing/OCR-R6-remediation-2026-10-25-validation.md)。没有main合并、Release或付费模型请求。
