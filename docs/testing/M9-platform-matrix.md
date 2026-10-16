# M9 平台与AC/NFR适用矩阵

日期2026-10-16；最终源码ed02da0e01125d94a3c07f8862afe6c63b8f62d2，[实际CI运行](https://github.com/nixaveq141-ai/J-Harness/actions/runs/38858529628)。这是本次选定环境的验证证据，不按OS名称推断其他版本/CPU已认证。

| 环境 | 实际系统/架构 | 结果 | 证据 |
|---|---|---|---|
| windows-latest | Microsoft Windows Server 2025 / 10.0.26100 / Datacenter / AMD64 | Java 21.0.12.1；305项，0失败/错误/跳过；实际候选冒烟通过 | [job](https://github.com/nixaveq141-ai/J-Harness/actions/runs/38858529628/job/116835329886) / [artifact](https://github.com/nixaveq141-ai/J-Harness/actions/runs/38858529628/artifacts/12028196762) |
| ubuntu-latest | Ubuntu / 24.04.5 / LTS / x86_64 | Java 21.0.12.1；305项，0失败/错误/跳过；实际候选冒烟通过 | [job](https://github.com/nixaveq141-ai/J-Harness/actions/runs/38858529628/job/116835329546) / [artifact](https://github.com/nixaveq141-ai/J-Harness/actions/runs/38858529628/artifacts/12028241155) |
| macos-latest | macOS / 26.6.2 / 25G83 / arm64 | Java 21.0.12.1；305项，0失败/错误/跳过；实际候选冒烟通过 | [job](https://github.com/nixaveq141-ai/J-Harness/actions/runs/38858529628/job/116835329963) / [artifact](https://github.com/nixaveq141-ai/J-Harness/actions/runs/38858529628/artifacts/12028640958) |
| Windows本机 | Windows-11-10.0.26200-SP0 / AMD64 | Java21完整305项通过，最终JAR重复打包一致；同一Windows CI候选ZIP安装全流程通过 | M9-local-results.json / M9-packaging-results.json / M9-local-candidate-smoke.json |
| Windows10 | 未新增实机结果 | 不算认证通过 | 不用Server2025或Win11代替 |
| 独立WSL | 未执行独立发行版验证 | 不算认证通过；不隐式回退WSL | 不用Ubuntu CI/Docker替代 |
| macOS Intel / Linux其他CPU | 未测 | 不算认证通过 | native包内有对应文件不等于实机验证 |

| 验证项 | Windows | Linux | macOS | 适用证据/限制 |
|---|---|---|---|---|
| Java21全量、持久化/锁/恢复、策略/脱敏、插件边界（AC-03/08～13/16适用回归） | 305通过 | 305通过 | 305通过 | 完整方法集合及260源码/POM相同；不把单元/集成结果宣称全部PRD真实生产认证 |
| 原生PTY、stdin/resize/stop、后台任务与取消（AC-07/09/12） | 通过 | 通过 | 通过 | M6TerminalTest实际执行；Docker PTY仍不支持 |
| Shell/argv、中文/空格/特殊字符、真实Wrapper编译失败→修复→成功（AC-01/02/04/05/07） | native Unicode接收器及CMD Wrapper通过 | POSIX/Java接收器与SH Wrapper通过 | POSIX/Java接收器与SH Wrapper通过 | Shell模式按平台明确；Java21/英文Windows CP_ACP及外部程序编码限制保留，不保证任意外部程序可无损接收其代码页不能表示的字符 |
| 最终ZIP安装、doctor、run、审批/EOF拒绝（AC-06/11/15） | 通过 | 通过 | 通过 | 每侧真实分发启动器，带空格安装路径；doctor无模型网络 |
| 独立CLI取消、UNKNOWN拒绝续跑、显式verify-request恢复、报告（AC-07/09/12/15） | 通过 | 通过 | 通过 | 每侧7次loopback请求、无付费请求；不自动重放未知请求 |
| SBOM/18依赖OSV、法律资源保留（NFR-09/PRD12） | 通过所述范围 | 通过所述范围 | 通过所述范围 | 10 runtime/8 test；工具链/OS/原生CPE及精确native构建来源限制见验收记录 |
| 安装/兼容/可追溯性（NFR-02/08/09/13适用项） | 通过所述范围 | 通过所述范围 | 通过所述范围 | 实际runner/Java/source/run/artifact/hash均已归档；重复归档字节证明仅对所测本地工具链 |
| Docker专门主动夹具 | M9未新增同源码Docker主动认证 | M9未新增 | M9未新增 | M8七组真实Docker和容器295项保留历史，不能重标为本次源码认证；CI全量中的环境/子任务接口回归已执行 |
| 新真实商业模型质量/费用评测、生产公网/多租户 | 未安排 | 未安排 | 未安排 | 不自动沿用M4历史费用授权，不把loopback冒烟说成真实模型质量评测 |

M10前端、M11 Desktop、延期工具重试/完整gitignore/强杀任意不合作Java线程等不在本次M9实现范围。M9已完成发布候选验证，正式Release发布和main合并另需明确要求。
