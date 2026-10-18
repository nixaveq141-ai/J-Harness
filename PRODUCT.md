# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

J-Harness 的本机单操作者，通过浏览器管理可信配置中的开发任务。

## Product Purpose

本机工作台打开后自动进入，用户无需输入本机连接凭据；刷新自动重连，不重复提交任务。模型API Key单独在设置中管理。

让操作者创建任务、观察真实事件、逐次审批、取消并查看记录型 Diff 和事实报告。

通过独立设置入口管理可信模型连接和扩展。模型密钥按用户明确选择保存到当前Windows用户的系统凭据库；任务内容和模型输出不能更改配置。

## Capabilities and Constraints

失败摘要、主体执行/清理证据及未决事实来自宿主记录；安全核实向导与JSON交接只读。设置提供default/read-only权限选择，仅用于后续新任务；问题回答、计划批准和执行审批互不替代。

Java Runtime 是执行、权限和持久化的事实来源。页面只调用本机受控 API；UNKNOWN 不自动重放。M10 不包含公网、多用户、Desktop 或付费模型评测。

## Brand Commitments

用户已明确确认复用 DeepSeek Harness 的视觉与交互，包括鼠标反馈和过渡效果。参考目录只读；使用 J-Harness 名称，不复制官方 Logo。TypeScript + React + Vite。

## Evidence on Hand

M9 历史验收；M10-frontend-reuse-review.md 与来源哈希清单。新界面须产生新的浏览器证据。

操作者可注册可信工作区并管理会话名称、置顶、分叉、导出、归档/恢复和分页；历史与32对象热集分离，工作区未决占用跨目录保持。

同一会话支持后续轮次、8条待执行队列与补充指令；宿主重启暂停队列，显式开始和UNKNOWN离线核实互不替代，草稿不持久化到浏览器。

扩展设置提供MCP/Skills/只读角色表单和高级JSON；技能根必须显式允许，可读取受控目录快照并使用slash候选。实际装配工具来自持久SDK清单，技能文本和MCP资源不能授予权限。
