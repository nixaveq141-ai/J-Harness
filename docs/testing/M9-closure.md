# M9 发布验证收口

2026-10-16，M9约定范围完成。[最终CI](https://github.com/nixaveq141-ai/J-Harness/actions/runs/38858529628)绑定源码`ed02da0e01125d94a3c07f8862afe6c63b8f62d2`，Windows Server2025 AMD64、Ubuntu24.04.5 x86_64、macOS26.6.2 arm64各305项，0失败/错误/跳过，完整测试名与260个Java/POM SHA一致。

三侧均通过18个精确依赖OSV查询、5项离线门禁、最终ZIP安装及审批/取消/显式恢复/报告，候选/文件/JAR/SBOM及原始XML/日志摘要已交叉核对。证据工具正例与9个独立篡改/断言禁用负例通过。Windows本机相同源码305项及JAR重复打包通过。

- [验收与失败修复](M9-validation.md)
- [平台及AC/NFR矩阵](M9-platform-matrix.md)
- [机器结果及各平台制品摘要](M9-ci-results.json)
- [验收工具校验](M9-evidence-audit-results.json)

前四轮失败单独保留，不拼接不同提交结果。最终文档提交只保存证据，不改变已验收源码或重标候选提交。M9完成不代表Windows10/独立WSL/其他CPU认证、完整第三方原生构建认证或PRD全功能完成。正式Release未创建，未合并远端main；下一阶段M10等待用户指示。

最终Windows CI原始候选ZIP在本机Windows11也完成实际安装/doctor/审批/取消/显式恢复/报告，7次loopback请求、0付费请求；ZIP SHA与同一CI Windows制品完全相同，见M9-local-candidate-smoke.json。
