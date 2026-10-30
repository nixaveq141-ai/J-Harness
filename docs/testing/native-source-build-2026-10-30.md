# 固定源码原生候选构建

本轮按用户要求继续技术闭合，转为构建可追溯替代候选。JNA固定c366bc4，pty4j固定b7554d6。三平台记录实际OS/架构、Java/Ant、每条构建命令/退出码/日志摘要、Git提交、完整已跟踪源文件摘要和构建时源码差异。

Linux/macOS尝试重编JNA和POSIX PTY；Windows尝试重编JNA。POSIX PTY先删除自有新克隆目录内的对应预编译输出，再调用固定上游Make目标，防止把原包冒称自建。原产物、依赖版本和支持矩阵保持不变；候选未通过装配与原生/完整验收前不替换产品。

这一步不声称Windows ConPTY/WinPTY已自建，也不声称OS/CPE或历史Mac根因已闭合。实际结果必须从该工作流原包核对，状态SOURCE_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE只表示源候选构建。
