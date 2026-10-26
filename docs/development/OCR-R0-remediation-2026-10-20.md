# R0 证据门禁与回归基线修复

> 记录范围（2026-10-26核对）：本文保留原阶段设计、取证或验收快照；旧测试数、接口版本和“尚未/待授权”等描述按原日期解释。当前交付与后续任务见[当前状态](current-state.md)及[全阶段审计](../testing/M1-M10.5-gap-audit-2026-10-26.md)。77e8855的三平台完整验收已通过；专项余项以审计清单为准。

日期：2026-10-20。实施基线：`003febc91214e88292af12ea3ef970a5f4a4e3fa`。当前状态：**本地基线已建立，完整发布流水线等待新运行证据**。本记录不将 484 条关联意见全部标记为关闭。

后续 R0 复审 `eed6a134-1d3b-4e89-adaa-a0e9b2ba1f19` 的 37 条意见已核实：26 采纳、10 部分采纳、1 不作为当前缺陷采纳；确认仍有跨平台、冻结导入及读取/来源链缺口，尚未实施复审修复。[最新复审与逐条判断](OCR-R0-rereview-2026-10-20.md) 及 progress.json 为当前入口；以下测试结果保留为前轮事实，不能据此认定 R0 已收口。

R0 原分派为 R0-01 115 条、R0-02 69 条、R0-03 300 条；逐条原文、采纳边界及意见 ID 仍由 [原核实账本](ocr-verification-2026-10-20/review.json) 和 [阶段计划](../requirements/OCR-remediation-plan-2026-10-20.md) 追溯。实施状态单独存放在 [progress.json](OCR-remediation-progress.json)，不改变原始核实裁决或历史验收 JSON。

## 已交付的机制

| 工作包 | 本轮改动 | 验证边界 |
| --- | --- | --- |
| R0-01 | 当前平台/发布收集器要求同 runId 的真实命令退出码、原始日志和输出哈希、执行前后完整构建输入快照；空 typecheck 输出只有对应命令成功才有效 | 不能从旧空日志补造退出码；完整新发布流水线未执行 |
| R0-01 | CI 聚合器拒绝空/非法身份、布尔伪计数、空指纹；按模块、类和 410 个确切 Java case 检查 64 suites；三平台须同 commit/runId/源码/UI | 校验结构与一致性，不证明任意第三方上传 JSON 的 GitHub 真实性；本轮没有远端 CI |
| R0-01 | 浏览器矩阵固定每引擎 271 个独立 case，严格布尔、退出码/信号/超时、源码/JAR/日志/截图；先撤销旧 PASS，启动前移除固定旧结果 | settings 仅保留明确的 `fixtureCredentialsCleaned === true` 兼容；失败状态不可被清理标记救回 |
| R0-01 | SDK 两语言/58 case、两个示例事实、七个 native 检查、八个 launcher 标签及五个 smoke 检查固定集合；要求非空流量、严格布尔与有效哈希 | 例如 native 的 `available` / `missing` 是探针检查名，不能解释为服务同时存在/缺失；字段语义细化仍随后续夹具推进 |
| R0-02 | 共享有界路径和 JSON 字节快照，拒绝跨平台穿越、盘符/UNC/ADS、保留设备名、链接、超大/空输入、重复键、非有限数字 | 同一份 bytes 用于解析、哈希及归档，末尾复查输入未改变 |
| R0-02 | 新归档先验证全部输入，再写临时目录，manifest 最后写入，最终整体 rename；拒绝重复目标和覆盖旧归档 | 默认新目录 `docs/testing/M10.5.9-archives/<UUID>/`；不覆写旧平铺验收文件 |
| R0-02 | 15 个历史采集/归档 CLI 在业务读写前统一 exit 2，保留旧代码供查阅，提示使用当前采集器与新归档 | 冻结旧写入口不是重新认证历史结果；当前工作流无这些旧入口的调用 |
| R0-03 | 新增独立门禁负例；smoke/launcher 在启动或读 ZIP 前原子写 INCOMPLETE，拒绝 `python -O`；ZIP 完整 manifest、必需法律文件、包内法律资源及解压界限检查 | 不用空集合、仅日志成功子串或共享错误实现的两次相等充当证明 |
| R0-03 | content 的五类拒绝路径使用唯一标签，原断言与 45 项数量不变；进程超时留 INCOMPLETE/FAILED，并有界终止/等待 | 不宣称已验证所有孙进程树清理；此部分仍随 R1/R6 继续 |

命令快照包含 Java/POM/资源、Maven wrapper、前端构建入口/public/源码/夹具、SDK、脚本、工作流、法律资源及 `.gitattributes`。新增契约 JSON、属性文件和 `.gitattributes` 固定 LF，避免平台换行差异冒充源码变化。运行缓存不入 Git。

## 本地验证

可读结果及原始缓存引用/哈希见 [R0 本地记录](../testing/OCR-R0-2026-10-20-results.json)；该文件是本轮实测摘要，不是发布或 CI runner 收据。

| 检查 | 实测结果 |
| --- | --- |
| CI 门禁 `test-m1059-gates.py` | 11 方法通过，含单字段变异；旧门禁被新负例命中 |
| 命令/组件门禁 `test-evidence-run.py` | 11 方法通过，覆盖失败退出仍打印 SUCCESS、空输出成功、旧输出、源码改变、超时、错误 cwd/command 及 collector 撤销旧 PASS |
| 路径/原子归档 `test-evidence-io.py` | 14 方法通过，含写入/rename 故障注入 |
| 候选/启动失败 `test-smoke-evidence.py` | 15 方法通过，含 -O、启动失败、缺失 ZIP、manifest/法律文件负例 |
| 历史冻结 `test-history-evidence.py` | 2 方法通过；45 次真实 CLI 调用全部 exit 2，历史 JSON 集合及字节哈希不变 |
| 浏览器门禁 `test-workbench-evidence.mjs` | 16 项通过，0 skip；含实际临时 Git 仓库及 junction |
| 既有 OSV / SDK inventory / source secret 单测 | 分别 5 / 4 / 2 方法通过 |
| Java `mvnw.cmd -B -o test` | 410 项，64 suites，0 failure/error/skip |
| 前端 `npm test` | 33 项通过，0 failure/skip |
| 实际命令回执集成 | Windows 下真实 `npm test` 返回 0；SDK tsc 原命令被 TS5112 拒绝，显式 `--ignoreConfig` 后严格检查返回 0，空日志与真实成功回执同时成立 |
| content 实际浏览器 | 前次 Chrome 155.0.8059.82 / WebKit 26.5 各 45 项通过；最终复验 Chrome 45 通过，WebKit 在最后的运行时异常断言失败，矩阵保持 FAILED |
| 原意见账本验证 | 2457 条覆盖及原裁决/30 工作包映射保持一致 |

环境失败也保留在忽略缓存：默认沙箱 Temp 拒绝原子移动；把 Java Temp 放在仓库内又使“非 Git 工作区”前提不成立。最终使用仓库外正常 Temp 运行 Java 全量成功，未修改断言。Node junction/Git 隔离测试使用正常本地权限后全部通过。浏览器首次默认 headless shell 缺失，随后显式使用已安装 Chrome channel 和本地 WebKit 成功，未下载浏览器。

真实 SDK typecheck 发现原 CI 命令在当前锁定的 TypeScript 下因同时存在 tsconfig 与显式文件列表而报 TS5112。按编译器要求增加 `--ignoreConfig`，保留 `--strict --noEmit` 及其余原参数；CI 与 collector 的确切命令契约同时更新。失败回执 exitCode 非零且保持 INCOMPLETE，修正后的实际命令 exitCode 为 0、日志为空；未把此前空日志解释成历史通过证据。

本次是 `test` 回归及 content 专项，**不是新的 clean verify/供应链/SDK/native/launcher/全浏览器同制品验收**；没有生成新的 RUNNER_PASS、跨平台 PASS 或 M11 资格。M10.5.9 保持 `LOCAL_PARTIAL_CI_PENDING`、`m11Eligible=false`。

最终 WebKit 失败输出为一个 `turns?offset=0` 的 CORS 诊断。夹具的断网恢复与 `offlineFault` 复位之间存在延迟事件的时间窗，但当前日志缺页面/请求身份及时间关联，不能证明本次根因。没有添加 sleep、扩大 live 错误豁免或删除断言；保留 `.build-cache/r0-content-delivery.log` 与冻结的 FAILED 矩阵。该项留在 R0-03 的夹具余项，随 R4/R6 定位复验，不计最终两引擎专项全绿。

新归档只发布 JSON 字节快照；日志、截图、XML、JAR/ZIP 在报告中保留哈希及原路径，**不是脱离工作区即可复验的完整离线制品包**。

## 运行与接续

完整新流水线须从开始设置唯一 `JHARNESS_EVIDENCE_RUN_ID`，同一次运行的各步骤复用；CI 使用 run ID + attempt + commit。`evidence_run.py --name <name> --log <log> [--cwd <dir>] [--output <file>] -- <command...>` 记录真实退出码；当前严格命令/工作目录与步骤顺序见 `.github/workflows/ci.yml`。不能仅补一个收集器命令认证旧缓存；源码或必需输出改变后必须重新执行相应流水线。

当前收集器默认撤销旧 runner PASS，缺少身份或回执即失败。矩阵只读检查：`node scripts/workbench-evidence.mjs .build-cache/M10.5.9/browser/matrix.json`；内容专项的两条记录不满足 11 夹具完整门禁，不能被收集器升级成全矩阵通过。完整本地报告产生后，再运行 `archive-m1059-local.py` 发布新目录。

R0-03 的 300 条意见按照原计划跨阶段交付，不在本轮把所有产品测试重写：执行/取消/关闭/日志收尾随 R1；存储/恢复/跨进程锁与文件字节证据随 R2；Web/查询/容量随 R3；前端真实状态与交互随 R4；模型/SDK 协议与流帧非空断言随 R5；真实启动器、原生凭据、跨平台资源及最终证据收口随 R6。原意见 ID 与采纳边界继续有效，只有具体用例与实现均验证后才能逐项关闭。

下一实施入口为 R1；R0-01 的完整同运行流水线仍待实际重采，不能用本轮离线测试或历史 PASS 代替。远端运行、推送和发布仍未授权/未执行。
