# 固定源码原生候选构建

本轮按用户要求继续技术闭合，转为构建可追溯替代候选。JNA固定c366bc4，pty4j固定b7554d6。三平台记录实际OS/架构、Java/Ant、每条构建命令/退出码/日志摘要、Git提交、完整已跟踪源文件摘要和构建时源码差异。

Linux/macOS尝试重编JNA和POSIX PTY；Windows尝试重编JNA。POSIX PTY先删除自有新克隆目录内的对应预编译输出，再调用固定上游Make目标，防止把原包冒称自建。原产物、依赖版本和支持矩阵保持不变；候选未通过装配与原生/完整验收前不替换产品。

这一步不声称Windows ConPTY/WinPTY已自建，也不声称OS/CPE或历史Mac根因已闭合。实际结果必须从该工作流原包核对，状态SOURCE_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE只表示源候选构建。

2026-10-30 补核：40995618881 的 Linux/macOS JNI 和 libpty 源码构建成功。40997194805 的 Linux 正常加载、PTY 握手及移走候选后的禁止回退对照均通过；macOS JNI 两对照通过，PTY 失败，清理异常遮住首个错误。探针现保留首错并把清理失败作为 suppressed，不改五秒预算。

源码范围发现遗漏：pty4j 的 Darwin `pty4j-unix-spawn-helper` 没有扩展名，旧后缀扫描未包含它。当前库存修正为 48 项，旧 47 项证据保留，逐一重新核对原包摘要；新增 helper 与固定 b7554d6 Git blob b9d5684 的原字节一致。详见 M10-native-inventory-48-2026-10-30.json。新增目录与 class 魔数反例，本地五项审计测试通过。源构建增加固定上游 CMake helper 目标，删除原预编译 helper 后重编双架构；尚未拿到新轮结果，不称已解决 macOS PTY 失败。

Windows 40997196069 原 ZIP 3d43a1f9 已核对：MSYS `uname` 被旧 Makefile 识别为非 Windows，configure 得到 x86_64-pc-msys，实际 MinGW 编译触发 tramp.c 错误。改显式选择上游现有 win32/amd64/MinGW 分支，未改 libffi C 文件。只重跑 Windows/macOS 源码专项，Linux 已过证据不重跑。四项构建离线测试、一项七反例加载验证及 Java21 严格编译通过。

源码修正专项 40998915552@3571d22 的 Windows JNI 和 macOS JNI/libpty/helper 均构建成功。加载器现限定审核过的运行及源码身份，macOS 缺源码 helper 或 CMake 命令即拒绝；提取后仅恢复已核验 helper 的执行位，前后字节仍须一致。八类离线拒绝反例通过。后续三平台加载专项使用原 Linux 源轮及新 Windows/Mac 源轮，各自绑定原报告，不混作单轮全量产品验收。
