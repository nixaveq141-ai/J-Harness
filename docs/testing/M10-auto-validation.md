# M10 无需输入凭据的直接入口

2026-10-17本地验收通过。用户明确要求删除打开web-ui后手动输入本机连接凭据的功能。界面已移除该输入框与连接提交表单；页面同源自动建立仅驻留内存的会话，刷新自动重连。启动器不再复制凭据、不修改剪贴板；服务不可用时显示重新连接按钮。

后台仍固定127.0.0.1并精确校验Host/Origin；POST /v1/browser-session要求Origin精确等于服务地址、Sec-Fetch-Site精确为same-origin、application/json、空对象正文、无查询参数。跨源、缺少Origin、same-site/cross-site/none拒绝；不提供CORS。业务API验证会话，旧CLI Bearer仍兼容。本机同源页面可直接操作，此机制不提供同机进程身份隔离。

自动会话随机生成、每个宿主独立；仅通过同源JSON响应交给页面内存，不进入URL、剪贴板、Web Storage或模型事件，业务输出统一脱敏。打开/刷新/重连不提交任务，也不调用模型。模型API Key的Windows凭据库和插件设置保持原有行为。UNKNOWN仍只读展示及离线核实，不自动重放。

实际验证：Java `clean verify`314项，失败/错误/跳过均0；客户端9项；Chrome155.0.8059.82工作台28项、设置21项、Windows英文启动器8项通过。同一最终JAR/UI与267份Java/POM哈希由scripts/collect-m10-auto-evidence.py核对，见M10-auto-results.json、M10-auto-workbench-results.json、M10-auto-settings-results.json、M10-auto-launcher-results.json。仅Windows11/Java21/Chrome本地验证，0付费请求，不把旧M9远端三平台结果用于新源码。

新增Java测试涵盖自动会话的无Origin/不同源/同站不同源/不同Sec-Fetch-Site拒绝、有效会话取得及实际鉴权；客户端验证无需用户输入便能建立内存会话。浏览器实际确认输入框不存在、刷新自动连接、任务不重提，自动会话与旧CLI token均不在URL/存储/页面正文/下载报告出现。模型系统凭据重启读回、插件实际调用、草稿保护仍通过；合成系统凭据已清理。启动器验证无粘贴/复制凭据提示，不操作用户剪贴板或打开用户浏览器（测试使用独立Chrome）。

已人工查看自动进入后的桌面截图，沿用原视觉；移动端、键盘、鼠标反馈、减少动态效果及无控制台异常包含在28项回归中。机械detector结果[]。原始日志和截图位于.build-cache/M10-auto-*及M10-*.png，不提交运行数据或制品。旧宿主由用户关闭后构建，无强停已有任务。

相关README、启动/设置说明、M10契约与验收、推进计划及.agent同步更新。原M10/M10-settings记录保留历史，不重标。仅创建本地提交，不推送、不发布、不实施M11。
