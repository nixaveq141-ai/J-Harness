# 固定源码原生候选构建

本轮按用户要求继续技术闭合，转为构建可追溯替代候选。JNA固定c366bc4，pty4j固定b7554d6。三平台记录实际OS/架构、Java/Ant、每条构建命令/退出码/日志摘要、Git提交、完整已跟踪源文件摘要和构建时源码差异。

Linux/macOS尝试重编JNA和POSIX PTY；Windows尝试重编JNA。POSIX PTY先删除自有新克隆目录内的对应预编译输出，再调用固定上游Make目标，防止把原包冒称自建。原产物、依赖版本和支持矩阵保持不变；候选未通过装配与原生/完整验收前不替换产品。

这一步不声称Windows ConPTY/WinPTY已自建，也不声称OS/CPE或历史Mac根因已闭合。实际结果必须从该工作流原包核对，状态SOURCE_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE只表示源候选构建。

2026-10-30 补核：40995618881 的 Linux/macOS JNI 和 libpty 源码构建成功。40997194805 的 Linux 正常加载、PTY 握手及移走候选后的禁止回退对照均通过；macOS JNI 两对照通过，PTY 失败，清理异常遮住首个错误。探针现保留首错并把清理失败作为 suppressed，不改五秒预算。

源码范围发现遗漏：pty4j 的 Darwin `pty4j-unix-spawn-helper` 没有扩展名，旧后缀扫描未包含它。当前库存修正为 48 项，旧 47 项证据保留，逐一重新核对原包摘要；新增 helper 与固定 b7554d6 Git blob b9d5684 的原字节一致。详见 M10-native-inventory-48-2026-10-30.json。新增目录与 class 魔数反例，本地五项审计测试通过。源构建增加固定上游 CMake helper 目标，删除原预编译 helper 后重编双架构；尚未拿到新轮结果，不称已解决 macOS PTY 失败。

Windows 40997196069 原 ZIP 3d43a1f9 已核对：MSYS `uname` 被旧 Makefile 识别为非 Windows，configure 得到 x86_64-pc-msys，实际 MinGW 编译触发 tramp.c 错误。改显式选择上游现有 win32/amd64/MinGW 分支，未改 libffi C 文件。只重跑 Windows/macOS 源码专项，Linux 已过证据不重跑。四项构建离线测试、一项七反例加载验证及 Java21 严格编译通过。

源码修正专项 40998915552@3571d22 的 Windows JNI 和 macOS JNI/libpty/helper 均构建成功。加载器现限定审核过的运行及源码身份，macOS 缺源码 helper 或 CMake 命令即拒绝；提取后仅恢复已核验 helper 的执行位，前后字节仍须一致。八类离线拒绝反例通过。后续三平台加载专项使用原 Linux 源轮及新 Windows/Mac 源轮，各自绑定原报告，不混作单轮全量产品验收。

40999273956 原三包核对：Windows JNI 与 Linux JNI/PTY 的正向及缺库拒绝对照通过。Mac JNI 通过，PTY 已收到标记，但五秒 waitFor 未确认退出，强制清理也未确认，首错现未再被覆盖。新增固定库存原分发/自建 JNI × PTY 四组合因果对照，仅启动 macOS，不重跑两侧。原分发对照须逐资源匹配48库存摘要，只用于定位，不作为源候选回退或通过证据。两项离线测试通过，包括对照字节篡改拒绝。

Mac 四组合 40999635249 原 ZIP 67e096f7：四种组合均相同握手后退出确认失败，包括原分发 JNI+PTY，不能认定自建库导致。进一步对照常规 class 启动与先前 source-file launcher；先 javac21 严格编译并记录 class/编译日志摘要，退出超时仅记录 OS 存活布尔和白名单类/方法名，无环境值、命令参数或正文。该试验未完成，不宣称根因。

## 本轮实际结论

2026-10-30 后续更新：41000910053@4aff934 的 macOS 四种 JNI/PTY 组合及源候选缺库拒绝全部通过；相对于上轮，子进程严格核对 ack 并增加持续读取终端输出。继续做仅推迟输出读取 500ms 的单因素对照，五秒总退出预算不变，未据此推断旧 models 按钮故障。

Windows 原生替代继续：新增固定 rprichard/winpty 0.4.3 提交 3e1ab962、Microsoft Terminal v1.22.11141.0 提交 6ffde348 与 pty4j b7554d6 的隔离源码构建，目标为 WinPTY DLL/agent、win-helper、OpenConsole/conpty.dll。只认新源码构建目录的产物，记录源码及日志摘要；三个来源/输出/失败留证离线反例通过，实际 Windows 专项待。该替代尚未装配，不将原 JetBrains build29 冒称为已证明。

40999943560@7e9cf04 的常规 class 入口也在四组合中同样失败。原 ZIP 3025356a 已核对，超时 OS 存活布尔为 true，pty4j reaper 停在 `wait_for_child_process_exit`。不能将失败归因于仅自建库或仅源码启动器，也不能把这项新探针失败解释为历史 models 按钮超时的原因。

M10-native-source-progress-2026-10-30.json 独立绑定七份加载原 ZIP、对应 Git 探针输入、每个探针日志及预编译 class 摘要。原生库存 48 项已补全官方分发字节核对；Windows/Linux/macOS JNI 已自建，Linux PTY 正反对照通过，macOS PTY 候选仍拒绝。没有替换当前产品中的库。

**两项仍未技术闭合。** 供应链尚缺 Windows ConPTY/WinPTY 等完整源构建替代、产品装配验收及完整 OS/CPE 证明；历史 macOS 原始因果状态已缺失，旧/新各十轮未复现，不能补写根因。用户未接受风险归档，本轮也没有将两项标为关闭。

35 个原包/取证回执及本轮审计文件已本地备份，19,014,177 字节，SHA256 `d08a67570e6f61f76475fcc8eaa61548709e6ac4de9c383eec85a5549ba2e0a2`，逐成员复核。路径与摘要在 M10-native-source-local-backup-2026-10-30.json。无付费请求，无 main 合并、Release 或异地备份。

41001582685@6c6f8d3 原 ZIP a6c4ba5a 单因素结果：原分发与源候选均先打印 PTY_EXIT_BEFORE_DRAIN=false，启动输出读取后，同一五秒总预算内退出0。正常四组合及移库拒绝均过。该实验补足的是本轮 PTY 探针输出消费与退出的因果链，不是历史按钮超时根因。Windows 首轮41001560186 原两包已保留：WinPTY configure 检查未请求的 MSYS adapter 编译器，NuGet 拒绝 Verbosity 选项。原生 DLL/agent 改用明确记录 SHA 的 native-only config.mk，不改上游 C++；移除 NuGet 的日志选项并记录帮助，三离线项通过，第二轮待。
