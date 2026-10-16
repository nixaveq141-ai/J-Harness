# M10 Web 前端契约

2026-10-16：约定范围本地验收完成。用户确认沿用 DeepSeek Harness 视觉/交互，在本仓库实现；参考目录严格只读。前置评估保留历史。

## 范围与部署

React/TypeScript/Vite 独立前端，Java serve 同源托管构建后的 classpath 资源。静态页面可公开读取，业务 API 仍须 Bearer。token 只驻留页面内存，不进入 URL、Web Storage、日志、模型或报告；刷新后重新连接。配置、环境、工具权限不由页面指定。

所有请求精确校验 Host，拒绝 cross-site；Origin 缺省仍兼容 CLI，存在时仅接受单一、精确等于当前 loopback HTTP origin 的值。无 CORS 通配、无代理转发信任。静态资源仅允许构建清单中的固定路径，禁止目录/工作区读取；CSP 限制同源脚本与连接，不执行事件正文 HTML。

## 切片

1. 认证/元数据/创建/任务列表及详情。requestId 单次逻辑提交固定，双击互斥；提交响应不明时仅核对，不自动重新 POST。页面刷新不创建任务。
2. 使用携带 Authorization 的 fetch 读取有限 SSE，按 eventId 去重、维护游标；有界重连只重新 GET。任务切换/断开/卸载取消旧读流；未知游标显式暂停并由用户重载历史。
3. 审批从服务端列表读取，决策绑定 taskId+approvalId+callId。拒绝/过期/已决冲突显示并刷新；取消不冒称回滚；UNKNOWN 单独显示、仅提供离线核实说明。
4. Diff 只展示既有 git_diff 事件；报告/产物引用只读取日志。提供有界、脱敏的会话事件查询，不触发 Git/文件/命令或模型请求。

## API 增量

保留已有 /v1/tasks 任务/分页/SSE/取消/审批/diff。新增受同等鉴权的 GET /v1/info（配置名称及协议）、GET /v1/submissions/{requestId}（提交核对）、GET /v1/tasks/{id}/report（基于日志的有限事实报告）、POST /v1/tasks/{id}/search（text/after/limit，全文事件匹配，返回有界脱敏事件）。没有在线解除 UNKNOWN 的接口。

## 构建与验证

frontend 中 npm ci / npm run build 产生 dist；Maven 将 dist 作为 harness-web classpath ui 资源，缺失构建时静态入口返回明确不可用，API 仍可用。完整交付和 CI 在 Maven 前构建前端。锁定 npm 依赖，保留移植源码 MIT 及浏览器分发依赖许可。

必须有实际 Java HTTP+浏览器验收，覆盖创建/观察/批准/拒绝/过期/错关联/取消/UNKNOWN/报告/Diff/查询、重复点击、刷新、断线、错误游标、token 和 HTML 注入边界、窄屏和键盘。没有证据不标记通过。所有模型夹具为本机替身，无付费请求。

## 最终证据

2026-10-16便捷入口增量：Windows英文名`web-ui.cmd`调用open-workbench.ps1，实际UI+鉴权API就绪后自动打开浏览器并复制凭据。支持既有配置/自定义工作区，token仅子进程环境和用户剪贴板，不入URL/文件/日志。服务仍由启动窗口持有，重复启动不停止既有宿主；不增加在线解除UNKNOWN或自动重放。新增启动器验证独立记录，不改Java/前端业务代码。

Java307项、客户端8项、真实Chrome28项通过；细节和平台限制见M10-validation.md/M10-results.json。API/SDK1.9及JSONL/schema1保持，新增HTTP读取接口是兼容增量。M10远端矩阵未执行，不继承M9旧源码的CI认证。
