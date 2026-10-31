# 固定源码原生候选构建

下一步产品覆盖：新增stage-source-native-runtime.py及源候选Windows打包应用全矩阵工作流。仅接受既验41179155433的完整load回执/预览JAR摘要，精确六原生资源，其余当前Java/UI/许可证逐字节不变；先保留当轮新构建baseline，再暂存可丢弃的工作JAR，不混原verify。原native/launcher/SDK/examples/浏览器全矩阵与三引擎R4将独立绑定同run回执及JAR。实际旧原包base/preview校验和受控漂移拒绝本地通过；远端尚待，不能称产品替换或735单测已使用源库。

2026-10-31实际复验结果：修复pkgconf归档404后，[41179155433](https://github.com/nixaveq141-ai/J-Harness/actions/runs/41179155433)@87ad109全部成功。固定Terminal1.24.12741.0源码、固定vcpkg与fmt overlay/四registry tree、签名pkgconf工具完成双原生项目构建；六资源预览JAR的其余4523条目逐字节不变，真实ConPTY ABI/缺捆绑库拒绝及产品LocalTerminalSession输入、目录切换、resize、退出、停止与超时清理通过。原ZIP61257c06/全部构建日志/源码输入摘要/端口tar/库输出/探针Git源码/class/命令回执/JAR已独立核验，见[固定Console验收](M10-console-fixed-acceptance-2026-10-31.json)。成功原包及收据已本地二成员备份逐SHA核对，见[备份](M10-console-fixed-local-backup-2026-10-31.json)。新版Console构建与组件装配专项已闭合；完整产品替代、全OS/CPE及历史macOS因果仍未闭合，未替换原735验收产物，无付费模型调用。

## 2026-10-30 安全基线补查

第三轮41035319681@5d7a1a2尚未源码构建：离线测试捕获同一临时目录RUNNER~1/runneradmin短长路径别名，overlay已resolve但根未resolve使relative_to拒绝。现统一解析根后计算全部相对身份，七构建项本地过，远端待；解码job日志SHA及无原始制品边界见[M10-console-path-alias-2026-10-30.json](M10-console-path-alias-2026-10-30.json)。安全/CPE原始响应与前两失败包及启动对照29成员本地备份逐SHA核对，见[M10-console-security-local-backup-2026-10-30.json](M10-console-security-local-backup-2026-10-30.json)。

第二轮41034613023@3aa3fe6原ZIPf77d1c22及所有日志已核对：预取器误将fmt11.1.4当作旧注册表版本，上游实际有固定Console源码overlay。已按上游规则优先绑定该overlay的版本及全部文件SHA，其余四个固定registry tree再预取；新增拒绝覆盖源漂移/缺根测试，构建共七项过，重验待。见[M10-console-overlay-failure-2026-10-30.json](M10-console-overlay-failure-2026-10-30.json)。

NVD精确CPE原始响应及分页完整性核对：旧Terminal1.22匹配54124；新候选1.24.12741.0和源码libffi3.4.4为零匹配。Microsoft Terminal的实际CPE从NVD已分析54124配置取证为microsoft:terminal，不猜为windows_terminal；59117的CNA版本门禁保留，不因NVD未返回而忽略。见[M10-native-exact-cpe-2026-10-30.json](M10-native-exact-cpe-2026-10-30.json)。这不是完整OS或未知原始二进制版本的清白证明。

首轮新基线41033417202@a98980f原ZIP35d195d5已核对全部命令日志；安全版本门禁、固定Console/vcpkg检出及恢复通过，MSBuild内vcpkg展开jsoncpp/ms-gsl历史端口树时Git promisor懒加载DNS失败，尚无新二进制或运行验收。补显式按上游版本清单预取五个固定tree的Git archive，记录版本清单/树/归档SHA并保留归档，再重验；不回退安全基线。六构建控制（含拒绝历史树漂移/归档复用/路径逃逸）及安全门禁/加载控制过。原失败见[M10-console-security-first-failure-2026-10-30.json](M10-console-security-first-failure-2026-10-30.json)。

本机启动边界补查：[WinPTY启动对照](M10-winpty-startup-controls-2026-10-30.json)的固定cmd隐藏新控制台/无窗口/继承控制台各三轮均退出0；源码agent的--version同三种模式各三轮也过。绕过Java/JNA直接ctypes创建空会话，源码和原分发仍agent died；仅放DLL/agent的全新目录仍失败。排除“所有隐藏控制台都不能创建”、仅Java/JNA及旁置ConPTY文件的单独归因，未定位终止触发者。

原关键字 Microsoft Terminal 查询包含旧 Terminal Server，并未覆盖 Windows Terminal 产品。Microsoft CNA 的 [CVE-2026-54124](https://github.com/CVEProject/cvelistV5/blob/main/cves/2026/54xxx/CVE-2026-54124.json) 与 [CVE-2026-59117](https://github.com/CVEProject/cvelistV5/blob/main/cves/2026/59xxx/CVE-2026-59117.json) 将相应 Terminal 产品低于1.24.11321.0列入影响范围。旧源码候选1.22.11141.0不能获得安全放行；整产品版本范围不等于已经证明捆绑ConPTY/OpenConsole子集可被利用。原功能通过证据保留，但不作漏洞清白证明。

候选固定到官方1.24维护版1.24.12741.0的7ff11ee107c984e236960ec948b450ccbc10187b，SDK22621/v143要求已从固定源码核实。其vcpkg基线固定fe1cde61，禁止外部二进制缓存，记录实际工具、源码、下载输入与安装文件摘要。增加公告快照摘要/数值版本/精确产品范围门禁，拒绝旧版本、错误产品、摘要漂移及未知范围语义。新加载器要求当轮源提交/实际双项目编译/安全门禁，并以既验WinPTY及JNI精确输出组装六资源预览，再跑真实ABI、禁止系统回退和产品终端。离线控制过；新候选实际构建与运行待CI。CVE-2026-96951实际为Windows Shell、CVE-2026-56171为RDP，不误写成Terminal子组件漏洞；历史Mac异常也不据此归因。见[M10-console-security-progress-2026-10-30.json](M10-console-security-progress-2026-10-30.json)。

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

增强远端41030432860@0cb0ddd已成功；原ZIP2254d7de/11754474字节经连接器下载后与GitHub摘要核对，固定五文件补丁独立重构、全部命令日志/源码输出/探针Git源码/class/运行JAR逐摘要过。真实ABI无效参数拒绝与错误对象释放现已实测，见[M10-winpty-enhanced-acceptance-2026-10-30.json](M10-winpty-enhanced-acceptance-2026-10-30.json)。

进一步使用源码预览JAR的真实LocalTerminalSession，本机Win11输入/非ASCII目录切换/resize/正常退出/主动stop/timeout及无观察到残留均通过；首个探针误用ARGV启动shell被产品门禁拒绝，保留原记录后改显式CMD模式，并未改产品实现。源WinPTY与原WinPTY+原JNI的独占、自有PID限定调试对照均实际退出0xC000013A（本机ntstatus.h定义STATUS_CONTROL_C_EXIT），触发者仍未知。47成员/36054815字节后续本地证据包逐成员SHA过，见[产品终端专项](M10-source-native-product-terminal-2026-10-30.json)。这些补齐组件真实集成证据，未替代完整产品重验、完整OS/CPE或历史macOS因果。

本机 Windows 11 装配补证：新增 `assemble-native-preview.py`，限定原库存资源及原/新摘要，拒绝覆盖原 JAR/证据，并逐条核对所有非替换条目。六个 Windows x64 资源的预览 JAR 保留其余4531条目；源码 ConPTY 在该预览通过非 ASCII 初始/变化目录、实际进程计数、resize、退出。缺捆绑库时即便系统 ConPTY 可启动也按后端身份拒绝，未冒充源码通过。WinPTY 源候选、原 WinPTY+源 JNI、原 WinPTY+原 JNI、源候选后台桌面对照均在新 cwd 调用前 agent died；不能归因新扩展，也未确定本机环境原因。72成员/60030329字节本地证据包逐成员摘要过，详见 [Windows预览实测](M10-windows-native-preview-2026-10-30.json)。这是组件装配实测，不是完整产品验收；OS/CPE 和历史 models 因果仍开放。

扩展最新：41022750231@e14d279 全部通过，原ZIP bdf90c5b 及固定源/检出字节/五文件补丁/自有header/所有编译和Java日志/三输出/class已独立重构审计。实际非ASCII初始和变化cwd、进程列表、resize、退出、缺库拒绝均过；见 M10-winpty-extension-acceptance-2026-10-30.json。两个扩展接口的兼容阻断已补齐，不等于产品装配/OS-CPE完成。后续增加真实无效参数拒绝与错误对象释放，class集合摘要；自有原生helper函数指针转换消警告，本机严格C++测试过，增强后CI仍待。

2026-10-30 最新：41019111630@93ceaaf 两组件源码构建全部成功。WinPTY原ZIP c6885046、Console原ZIP52540faa及源码身份/全部命令日志/五个输出字节均独立核对，见 M10-winpty-source-abi-2026-10-30.json 与 M10-console-source-build-2026-10-30.json。成功原包已另本地备份，前述失败包保留。

WinPTY DLL 缺少当前 pty4j 按名调用的 winpty_get_console_process_list、winpty_get_current_directory。新增必要名字缺失即拒绝的门禁；名字出现也只标ABI运行验证待，不能冒称通过。五项离线控制通过。该候选不能直接替换；产品装配、扩展接口兼容、完整OS/CPE与历史按钮因果仍未完成。

2026-10-30 后续更新：41000910053@4aff934 的 macOS 四种 JNI/PTY 组合及源候选缺库拒绝全部通过；相对于上轮，子进程严格核对 ack 并增加持续读取终端输出。继续做仅推迟输出读取 500ms 的单因素对照，五秒总退出预算不变，未据此推断旧 models 按钮故障。

Windows 原生替代继续：新增固定 rprichard/winpty 0.4.3 提交 3e1ab962、Microsoft Terminal v1.22.11141.0 提交 6ffde348 与 pty4j b7554d6 的隔离源码构建，目标为 WinPTY DLL/agent、win-helper、OpenConsole/conpty.dll。只认新源码构建目录的产物，记录源码及日志摘要；三个来源/输出/失败留证离线反例通过，实际 Windows 专项待。该替代尚未装配，不将原 JetBrains build29 冒称为已证明。

40999943560@7e9cf04 的常规 class 入口也在四组合中同样失败。原 ZIP 3025356a 已核对，超时 OS 存活布尔为 true，pty4j reaper 停在 `wait_for_child_process_exit`。不能将失败归因于仅自建库或仅源码启动器，也不能把这项新探针失败解释为历史 models 按钮超时的原因。

M10-native-source-progress-2026-10-30.json 独立绑定七份加载原 ZIP、对应 Git 探针输入、每个探针日志及预编译 class 摘要。原生库存 48 项已补全官方分发字节核对；Windows/Linux/macOS JNI 已自建，Linux PTY 正反对照通过，macOS PTY 候选仍拒绝。没有替换当前产品中的库。

**两项仍未技术闭合。** 供应链尚缺 Windows ConPTY/WinPTY 等完整源构建替代、产品装配验收及完整 OS/CPE 证明；历史 macOS 原始因果状态已缺失，旧/新各十轮未复现，不能补写根因。用户未接受风险归档，本轮也没有将两项标为关闭。

35 个原包/取证回执及本轮审计文件已本地备份，19,014,177 字节，SHA256 `d08a67570e6f61f76475fcc8eaa61548709e6ac4de9c383eec85a5549ba2e0a2`，逐成员复核。路径与摘要在 M10-native-source-local-backup-2026-10-30.json。无付费请求，无 main 合并、Release 或异地备份。

41001582685@6c6f8d3 原 ZIP a6c4ba5a 单因素结果：原分发与源候选均先打印 PTY_EXIT_BEFORE_DRAIN=false，启动输出读取后，同一五秒总预算内退出0。正常四组合及移库拒绝均过。该实验补足的是本轮 PTY 探针输出消费与退出的因果链，不是历史按钮超时根因。Windows 首轮41001560186 原两包已保留：WinPTY configure 检查未请求的 MSYS adapter 编译器，NuGet 拒绝 Verbosity 选项。原生 DLL/agent 改用明确记录 SHA 的 native-only config.mk，不改上游 C++；移除 NuGet 的日志选项并记录帮助，三离线项通过，第二轮待。

后续：Mac 两成功原包及固定 Git 探针/日志已独立核验，见 M10-mac-native-drain-2026-10-30.json；8成员本地备份逐成员核对，见 M10-native-drain-local-backup-2026-10-30.json。Windows 第二轮41017274769 Console原包a497008f：NuGet恢复已过，单项目 MSBuild 缺 SolutionDir；补明确根目录，三离线项过。仅重跑Console，保留在途WinPTY。本轮仍未完成产品替代/OS-CPE或历史按钮因果闭合。

Console 第三轮41017612132原ZIP668546d6证实 Windows-latest 的 VS18 环境缺所需 SDK22621。构建专项改为 Windows2022 / VS17，并在源码构建前检查 SDK22621 头文件；依据官方当前镜像清单 https://github.com/actions/runner-images/blob/main/images/windows/Windows2022-Readme.md 。不改上游SDK要求，不将构建环境修正计作产品验收。

41018127320 两原ZIP f00529b2/45ec0c98：Console进入C++调用后 FileTracker CommonApplicationDataPaths 报根为空，CMake 同时无法发现已安装 VS。最小构建环境漏掉 ProgramData/ALLUSERSPROFILE/ComSpec；补三项系统工具链变量，仍不继承其他凭据。新增共享根保留/任意凭据排除测试，四离线项通过，真实复验待。

41018538974两原ZIP2bcdf701/b3c63883：共享数据修正后Console不再FileTracker失败，现缺WIL；原solution restore主要恢复托管项目，补四个固定native packages.config显式恢复，记录nupkg摘要。Helper已识别VS/SDK，编译器识别仍失败，补标准共享程序文件根和系统盘变量，四离线项过；新轮仍待，不将候选编译作为产品验收。

继续兼容闭合：固定官方 WinPTY master7e59fe2（已含进程列表接口），对五个精确源码SHA校验后补 owned-child cwd RPC。目录读取只使用agent已有子进程句柄，跨位数、失效/变化描述符、读取失败、缓冲不足均明确失败；不把初始目录或任意PID结果冒充当前目录。补丁前后摘要和自有header摘要记入构建报告。两个离线patch项含原子拒绝/重复拒绝及七类加载拒绝反例通过；可信Java探针严格编译。新增实际native目录切换/坏描述符探针及Java非ASCII目录变化、进程列表、resize、退出/禁止回退验证，实际CI待，未声称产品替换。

扩展首轮41021962732原ZIPc7f17d3f：实际在补丁源码SHA门禁失败，尚未编译C++。核对检出头文件原摘要精确等于固定Git blob的LF→CRLF字节转换；现保留beforeSHA并与trackedFiles核对，另记录显式换行转换与固定canonical Git blob SHA。未知内容变化仍拒绝，新增换行反例；patch三项及加载拒绝项通过，实际重验待。
