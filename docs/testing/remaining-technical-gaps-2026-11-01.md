# 严格技术缺口与当前验收边界

更新：2026-11-01。用户要求继续技术闭合，没有接受残余风险归档。此清单不是全部完成声明，也不要求用户再次选择关闭方式。

## 已有实际完成证据

[同提交三平台完整运行](https://github.com/nixaveq141-ai/J-Harness/actions/runs/41272550578)绑定 `e503377072f7e6f2c0ff8e390e69486ffb059d76`，Windows2022 x64、Ubuntu24.04 x64、macOS arm64均成功。每侧735个Java用例、124套件零失败/错误/跳过，三浏览器33夹具813检查、R4每引擎51检查、候选安装与跨作业字节一致性均通过。实际运行时JAR为 `8407dc5d6e9e2879993467e3ddf0c7524b1c75ce110567ca60056cb0d41d99e1`。

原制品已独立核验完整Git指纹、实际XML、命令参数/退出码/日志、截图、原生字节、安装候选与法律材料，见[总验收收据](M10-unified-final-acceptance-2026-11-01.json)、[Windows](M10-unified-final-windows-2026-11-01.json)、[Linux](M10-unified-final-linux-2026-11-01.json)、[macOS](M10-unified-final-macos-2026-11-01.json)。原包及审计材料已[本地备份](M10-unified-final-backup-2026-11-01.json)，每成员SHA已核对。

本轮实际修复还包括：补旧库存遗漏的两项AIX `.a`（当前50项）；补固定Console静态依赖CLI11许可证并验证进入三侧JAR；规范ZIP元数据及严格限定的内部Maven身份文件换行，使三侧运行时相同；分离单进程树限制测试与CMD退出语义测试，保留原限制断言并加强失败状态断言。新汇总门禁用实际证据取代数量判断，先撤销陈旧PASS；其加强版已在本地用上述三侧原包复验，并验证缺失XML、伪命令和JAR漂移被拒绝，见[门禁复验](M10-unified-gate-revalidation-2026-11-01.json)。加强版尚无单独远端执行声明。

2026-11-01后续实修：原生覆盖审计器拒绝空库存、缺失来源及无效摘要，审计开始先原子撤销旧成功，且拒绝输出覆盖输入JAR。四项回归通过；实际统一JAR50项复核仍为11源码/39来源未知，严格模式exit2。见[M10-native-auditor-fail-closed-2026-11-01.json](M10-native-auditor-fail-closed-2026-11-01.json)。此修复防止错误闭合，不补造39项来源或历史因果，五项技术缺口继续OPEN。

2026-11-01来源覆盖继续：新增Linux ARM64与macOS Intel专用固定源码候选构建，明确架构到对象名及输出路径的对应，五项离线控制通过；新增hostImage、系统包版本/架构、libc和JDK模块原始命令记录。工作流使用[官方标准运行器标签](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)。实际远端构建、加载与产品装配未完成前，39项计数不变，不当作额外架构已验收或完整OS/CPE证明。

2026-11-01实际扩架构构建：[41285899341](https://github.com/nixaveq141-ai/J-Harness/actions/runs/41285899341)的Linux ARM64与macOS Intel均成功。原包、固定源码全部指纹与既审源、输出字节和全部命令日志已核验，[构建收据](M10-extra-native-source-build-2026-11-01.json)及[本地备份](M10-extra-native-source-backup-2026-11-01.json)保留。新增固定该精确运行的实际加载/禁止回退专项入口，加载前不能标产品替换完成，当前39计数不改。

2026-11-01组件加载闭合：[41286273580](https://github.com/nixaveq141-ai/J-Harness/actions/runs/41286273580)的Linux ARM64与macOS Intel均通过真实JNI、PTY交互及缺库拒绝，[收据](M10-extra-native-load-2026-11-01.json)/[备份](M10-extra-native-load-backup-2026-11-01.json)已逐摘要核验。新统一候选装配14项源码、36项原版，新增仅三个独立资源，Mac通用PTY/helper仍保留先前已验输出；[实际装配控制](M10-unified14-assembly-control-2026-11-01.json)证明其他4524条目保持。五目标完整Java/应用/安装及同JAR门禁待远端实测；此前已验11/39产品记录不被覆盖，不把候选控制当产品闭合。来源审计现按实际安装字节计数，已审但未安装的新载荷不算进源码数。

2026-11-01五目标完整首轮41287020806尚未通过：ARM64工作区目录标志误用x86位，Mac Intel一个Git用例超时。原始失败包和日志已核；修正ARM64标志，另修复真实复现的非Git子工作区向上发现父仓库边界问题，原安全用例先失败后通过。API24+工作区43本地全过，五秒默认预算不改，增加可信Git前置版本回执以独立记录启动。见[修复收据](M10-workspace-architecture-boundary-fix-2026-11-01.json)；修正后完整远端验收仍待，不证明旧Mac或本轮Git超时根因。

2026-11-01修正轮41290698971：ARM64目录打开错误已越过，随后显式拒绝未认证的元数据布局。当前补ARM64 stat偏移及实际宿主C头文件ABI门禁；本机67项回归再通过，远端确认仍待，见[M10-arm64-stat-layout](M10-arm64-stat-layout-2026-11-01.json)。不能把结构布局放开等同已验收，不弱化未知CPU拒绝。

## 仍开放的五项

| ID | 缺口及已知事实 | 严格闭合还需要什么 |
| --- | --- | --- |
| NATIVE-39 | 50项实际原生文件逐字节核对，其中11项具有已审源码输出链，39项精确等于官方分发，但精确构建修订/工具链仍未知。当前功能验收只覆盖三个选定架构，不证明其他载荷构建。 | 取得39项可验证的逐文件构建证明，或在对应工具链下固定源码构建替换；保留合法来源/许可证/构建日志/输出摘要，再进行适用加载与回归。不能仅凭源码仓库地址或相似版本认定完成。 |
| OS-CPE | Maven坐标门禁、选定原生公告与CPE/OSV快照已执行；JsonCpp固定源码归档与端口SHA512已匹配。上述结果没有证明完整OS/JDK/原生组件和补丁适用性。 | 对实际声明支持的OS镜像/JDK和原生静态/动态依赖形成完整版本清单、数据库映射/分页快照及漏洞适用性结论。未知映射不能按零漏洞处理；只认证明确版本与时间，不能承诺任意用户系统永久无漏洞。 |
| MAC-HISTORY | 原 `40195474698@11e4024c` 的模型发送按钮禁用30秒；原时刻没有完整DOM/React/任务/事件联合快照，旧执行器已不可恢复。历史固定版本与当前版本各十轮未复现，最新全矩阵也通过。 | 取得原故障足够的因果证据，或固定旧版、可控条件和相同阶段复现，给出修复前失败/修复后通过及反例。其他PTY问题或“后续通过”不能替代原按钮禁用根因。 |
| WINPTY-LOCAL | 本机Windows11的旧WinPTY agent在原分发和源码变体均观察到 `0xC000013A`，自有进程退出已核实；源WinPTY远端ABI和现代源码ConPTY本机产品探针通过。 | 在仅自有子进程范围捕获可归因的控制事件/退出调用链，取得同条件对照与修复。当前不能证明是默认终端、系统安全软件或具体第三方进程触发；不能将ConPTY通过当旧WinPTY原因已修复。 |
| DEVELOPMENT-UNKNOWN | 旧统一运行 `41270037874@9ef` 的Windows Chromium development审批后后端FAILED/UNKNOWN；原始错误码/工具清理时序未冻结。固定原失败JAR的三轮专项各14项通过，本轮完整三平台也通过；已增加有界白名单错误诊断。 | 获取相同阶段可控失败及任务错误码/工具状态/清理与事件时序，关联实现并证明修复。不能用独立CMD夹具树预算问题解释这个浏览器后端失败。 |

精确载荷见[50项库存](M10-native-inventory-50-2026-11-01.json)、[当前三平台实际覆盖](M10-native-final-coverage-2026-11-01.json)、[11项源码映射](native-security/reviewed-native-source-map.json)。39项仍作为未知保留，不删除其他架构载荷来凑“全部来源已知”。

当前候选的严格来源门禁：`python scripts/audit-native-coverage.py harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar .build-cache/native-coverage.json --require-source-complete`。对当前11/39载荷应输出不完整并以2退出；正常的覆盖清点成功不能代替严格来源成功。

历史及本机调查入口：[原生构建与各轮证据](native-source-build-2026-10-30.md)、[旧运行时三轮观察](M10-development-observations-2026-11-01.json)、[本机统一ConPTY组件验收](M10-local-unified-conpty-2026-11-01.json)、[JsonCpp查询边界](M10-native-jsoncpp-query-2026-11-01.json)。不复写原失败证据，不将“当前未复现”改写为“历史根因已修复”。

## 用户已经限定的范围

额外实体OS/CPU机器验收按用户指令跳过；成本较高的性能横测后置，不能记为性能SLA认证；备份只落本地，不再要求异地保存目标。本轮模型请求全部为本地替身，新增付费模型调用为0。未合并main、未发布Release，也没有发布批准。

下一步优先推进39项来源与明确OS/JDK范围清单；历史故障只沿保留的精确版本与有界诊断继续复现，不通过重复扩大测试轮数宣称根因闭合。缺少工具链、原始因果或供应商构建证明时继续保留OPEN，而不是以文档归档代替技术完成。
