# M10 本地收口

后续便捷入口：用户指定英文文件名web-ui.cmd，Windows启动脚本通过8项真实启动/退出/鉴权/配置检查，单独归档M10-launcher-results.json；浏览器和剪贴板桌面副作用在自动化中禁用。基础Java/UI业务代码和JAR不变，原验收保留历史。

2026-10-16，约定四切片本地验收完成。Java307项零失败/错误/跳过、客户端8项、实际Chrome28项；263份Java/POM及前端输入、最终JAR与包内UI哈希绑定。入口为Java serve同源托管的React/TypeScript工作台。

保留用户指定DeepSeek Harness的原始主题、控件与相关交互，21份移植文件原字节/MIT保留；25份前置源文件、21份移植来源和上游tracked工作区核对未变。没有修改原参考目录。

同源/鉴权、幂等核对、事件续读、审批/拒绝/过期、取消/UNKNOWN、记录型Diff、事实报告/产物引用/查询，以及刷新/断线、秘密/HTML、原鼠标反馈、主题/窄屏/键盘均有实际证据。npm审计0发现、6包生产闭包SBOM、8项证据负例归档。

详见[M10验收](M10-validation.md)、M10-results.json和M10-browser-results.json。失败与修复保留，未将未测项目写成通过。本轮只本地提交，M10远端矩阵、推送、正式发布、其他浏览器、新Docker与Desktop未执行。下一阶段M11等待用户指令。
