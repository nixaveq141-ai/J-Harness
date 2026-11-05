# P1：原生来源补齐执行记录

## 当前结果（2026-11-05）

2026-11-05 P1正式进展：完整轮41989532940@20570ce五托管目标及同字节门禁全部通过，并独立核验六原ZIP、675项Git输入/侧、735项Java/124套件/侧、14命令回执/侧、813浏览器检查与R4每引擎51项；统一JAR 3e2f966a271962ecc52ad10d27b26bec74c8ee3a19cd77e99ae4ece2c76c1b1f。正式来源覆盖升至27/50，剩23项未集成闭合。Windows x8641992679907@e6独立核1297 JNA/134 PTY固定源完全一致、libffi、11构建命令和3实际32位C加载探针回执/678宿主Git输入；仅组件已验，仍待统一候选集成/产品重验。17成员583654594字节原包及审计器本地逐摘要/CRC备份通过；P1未完成，未付费，实体机器跳过。

证据：[五目标同字节独立复核](../M10-unified27-final-equality-2026-11-05.json)、[逐任务现状](progress-ledger.json)、[Win32组件独立复核](windows-x86-components-acceptance.json)、[本地原包备份](local-backup-unified27-2026-11-05.json)。五侧分别有同名前缀的完整验收JSON，包含XML/回执/候选摘要；组件额外CPU未认证完整JVM，三历史因果与完整OS/CPE不因此关闭。下文按执行日期保留历史过程，当前状态以本节和逐任务账本为准。

2026-11-04从 `6aa61ac` 的P0账本启动。[P1规划](../../requirements/remaining-gap-closure-plan-2026-11-04.md)要求保留50项资源、取得逐文件来源链或真实源码替换，并分别保留组件与产品证据。

## 首批：Linux额外架构PTY五项

固定JetBrains/pty4j提交 `b7554d649f6374183040e6554942b6d5b028b5e5`。x86、ARM hard-float、MIPS64EL、PPC64LE、RISC-V64使用目标GCC与sysroot，并保留实际已安装包版本和编译器/QEMU版本。构建前删除对应预构建文件，使用原Makefile的对象与链接规则，源码前后逐文件核对只允许目标二进制变化。

入口：[工作流](../../../.github/workflows/gap-p1-linux-pty.yml)、[构建与回执脚本](../../../scripts/build-p1-linux-pty.py)、[目标C探针](../../../scripts/probes/p1-pty.c)。实际源码列表、完整宿主输入快照、每命令参数/退出码/日志摘要、输出与探针摘要、ELF/依赖符号审查均归入原始制品。本地两项架构/ABI拒绝控制已过，首次远端实测待。

模拟组件探针验证精确路径dlopen、必要导出、文件描述符有效性、native窗口尺寸设置/读取、无效fd拒绝、双向PTY字节、native fork/exec子进程交互及退出码23、清理，以及精确文件缺失时拒绝加载。使用自有合成子进程和无模型请求；该证据不能替代目标Java产品验收或任意Linux兼容性证明。原载荷架构正确时使用同一探针对照；架构错误的原载荷直接拒绝，不在宿主执行。

## 原始载荷头部异常

[原始ELF盘点](initial-elf-inventory.json)从5df已验精确JAR读取36项未决资源，逐摘要与P0账本一致，其中24项为ELF。三项名称与ELF类/机器号不符：

| 资源 | 实际 | 名称对应预期 |
| --- | --- | --- |
| pty4j linux/mips64el/libpty.so | ELF64 / x86-64 / machine62 | ELF64 / MIPS / machine8 |
| JNA sunos-x86/libjnidispatch.so | ELF64 / x86-64 / machine62 | ELF32 / x86 / machine3 |
| JNA sunos-sparc/libjnidispatch.so | ELF64 / SPARCV9 / machine43 | ELF32 / SPARC / machine2 |

该盘点只证明当前已验JAR的具体字节与标签异常，不归因上游构建过程，也不把其他21项头部匹配视为运行已通过。MIPS异常纳入首批整改；SunOS两项需要对应工具链或上游逐文件证明，保留OPEN。

## 当前边界

当前正式已验候选仍为14项已审源码/36项来源未决；新输出在真实构建、加载、候选装配和适用回归完成前不增加正式来源覆盖。其余JNA21项、PTY FreeBSD2项及Windows8项继续按P0的准确资源路径推进；工具链与SDK未确认的不得写为可用。P1尚未完成。

本阶段不改变P0冻结账本；后续进展单独记录并与原任务ID关联。无付费模型、额外实体机器采购或发布。

首轮[41822880250](https://github.com/nixaveq141-ai/J-Harness/actions/runs/41822880250)@9a5863e五侧实际构建及探针编译成功，但组件探针均exit21，整轮不验收。[失败收据](first-run.json)保存五原包摘要和实际退出。修正探针在子进程重新打开slave前关闭最后一个slave造成master过早HUP的条件，保持原双向交换/退出23断言并增加有限诊断；修正结果待远端验证，不归因产品实现。

## 五组件独立验收与统一候选

修正轮[41823433458](https://github.com/nixaveq141-ai/J-Harness/actions/runs/41823433458)@1c4cc6d五侧成功，[组件独立验收](components-acceptance.json)已核原ZIP、与既审固定源完全相同的134项源码输入、每命令/日志/完整宿主Git指纹、ELF与探针摘要；四个架构原载荷同探针通过，MIPS原x64被拒而新MIPS探针通过。每侧包括缺库拒绝、native fork/exec交互及退出23。首轮失败原包保留，未放松断言。

[真实统一装配控制](assembly-control.json)将五新载荷加入既有14项，共19项来源已审候选、31项原版未决，其他4519个条目保持。操作后本机旧生成JAR逐字节恢复。新增stager锁定精确组件收据/字节/命令，真实原包控制与载荷漂移、回执篡改、重复目标拒绝通过。来源映射中的新增五项只在实际安装字节匹配时计数，不将已验5df JAR的14/36改成19/31。

五目标完整产品CI已配置复用同一轮五组件原包，新增C组件加载不宣称额外架构Java应用已认证；远端新候选完整验收仍待。P1全50来源门槛尚未达到。

## 第二批：Linux JNA八目标

新增固定JNA c366bc4源码与已有精确libffi3.4.4生成输入的交叉构建入口，覆盖x86/ARM硬浮点/ARM软浮点/MIPS64EL/PPC/PPC64LE/RISCV64/s390x。保留目标GCC、host tuple、sysroot、JDK/Ant版本、源前后指纹和具体补丁。C级探针检查JNI六类sizeof、Native malloc/free、真实静态libffi调用及closure、精确缺库拒绝；不冒称完整JVM initIDs/类加载或目标Java应用通过。字宽/端序/ARM软硬浮点两控制本地通过，真实构建与探针结果待CI。LoongArch工具链未确认，不隐含计入本批。

JNA首轮[41826192147](https://github.com/nixaveq141-ai/J-Harness/actions/runs/41826192147)@0609154八侧失败，[首轮记录](jna-first-run.json)保留原制品索引与x86作业日志摘要。x86实际Ant构建成功，但Ant会将同一JNI库复制进类目录，原枚举器要求全build目录只能出现一个文件而拒绝。改为固定native-linux-target编译产物路径，并拒绝类副本与其字节不一致；该原生输出/镜像差异控制及原两ABI控制共三项通过，修正后的真实组件探针仍待远端。

[PTY实际链接需求](compatibility-scope.json)解析原包内DT_NEEDED与GNU version-needed节，记录五个新/旧载荷的依赖。新ARM要求GLIBC_2.34、x86要求GLIBC_2.28；实际替换与后续支持范围/P2需考虑这些要求，不能以QEMU组件通过认证所有旧Linux。

## 复验中的阻断与修正

JNA[第二轮](jna-second-run.json)六侧组件通过，MIPS64EL/s390x真实探针均exit13；原八ZIP均保留。原因定位为本项目C探针回调只写int，未按[固定libffi3.4.4 closure约定](https://github.com/libffi/libffi/blob/v3.4.4/doc/libffi.texi)提升到ffi_arg。共享回调已按约定修正，保持真实调用41/回调46断言；[本机实际C红绿控制](callback-promotion-control.json)显示旧写法exit1、修正exit0且邻接保护值保持。各目标新增寄存器宽度控制，修正后的八目标仍待实测，不称libffi或产品缺陷已修复。

19项统一候选首轮41824728486的[Windows原包复核](unified19-first-windows.json)：735项Java与安装过，WebKit turns在第11项检查后发送按钮disabled30秒，整轮拒绝验收。另严格源门禁发现新C探针在Windows被转成CRLF；旧运行保留该不符，不扩大旧门禁的例外，未来通过Git属性固定scripts/probes C/H为LF。诊断专项只运行原失败精确JAR，并给turns增加有界无正文DOM/React长度、任务枚举和事件类型观察；原15项断言及UNKNOWN不重放语义不变，不把同症状当旧Mac根因。

P0/P1嵌套JSON增加原字节保留属性，避免跨平台checkout改变已有摘要；现有JSON语义未变，P0校验仍通过。完整候选验收、所有剩余来源和其他OS/CPE问题均不因诊断或字节策略调整而视为完成。

## 十三组件来源与27项候选

JNA[41830641417](https://github.com/nixaveq141-ai/J-Harness/actions/runs/41830641417)@8ad八侧全部成功，[独立验收](jna-components-acceptance.json)逐一核对1297项固定源与既审源相同、15条实际命令/宿主Git指纹/原始日志、libffi归档与生成输入、ELF/端序/浮点ABI、C探针与回调保护控制、精确缺库拒绝。加上前批五PTY，新增13组件来源已审。[逐任务进展](progress-ledger.json)对照P0的36个稳定任务ID，23项继续OPEN，未取得SDK的不能记为已构建。

[27项真实装配控制](assembly27-control.json)证明实际候选27源码/23原版，其余4511条目保持；严格全来源门禁正确exit2，本机原生成JAR已精确恢复。新增stager拒绝输出/原归档/回执漂移及重复目标，实际八原包控制通过。下一轮完整五目标才验证这套统一候选，不拼接上一轮四侧成功为新候选完成。

原Windows19候选的[单轮精确JAR观察41830641429](turn-observation-acceptance.json)通过原15检查，源码/回执/截图/JAR已独立核验；未复现故障，不作为历史Mac或本轮Windows按钮因果修复。额外CPU只有已说明的C级组件验证，完整JVM/目标Java应用与所有Linux版本兼容性均未认证。

## 2026-11-05接续

27项候选b126e6a已成功推送，[完整五目标41986781377](https://github.com/nixaveq141-ai/J-Harness/actions/runs/41986781377)运行中，未完成原包验收前不更新正式覆盖。下一步核全部目标与一致性门禁，再按进展账本推进23项未决来源。

[本地备份收据](local-backup-2026-11-05.json)：46成员188165234字节，包含本轮已保留的失败/成功组件原ZIP、原Windows失败ZIP、27项装配控制JAR、索引和审计器；逐成员SHA/整包SHA与CRC已核。审计脚本绑定各自提交输入快照，不能拿未来源码文件集合冒充原轮输入。只在本地保存；该备份与运行中的CI均不等于P1全部完成。

## 27项候选复验的实际输入槽竞态

41986781377的Linux source-verify失败，M6取消阻塞输入期望UNKNOWN却返回BUSY。生产worker先完成future再释放输入槽，串行下一次write可能被误判忙。修正先释放再发布结果；原取消用例增加可控release闸门，保持原用例身份和取消UNKNOWN断言，[旧码失败/新码通过](input-slot-red-green.json)已实测。本机全部7项M6另有background stop预期STOPPED、实际UNKNOWN的失败，未把整套记通过；完整候选仍待修正轮验收，P1未完成。

## Windows x86来源批次

新增固定JNA源的Windows x86构建专项，采用[MSYS2官方32位GCC包](https://packages.msys2.org/packages/mingw-w64-i686-gcc)。先核实际gcc dumpmachine，再指定Ant ARCH=x86/os.prefix=win32-x86；逐输出核PE machine0x14c/PE32魔数，不允许64位副本充数。六项离线控制通过；真实编译、组件加载与统一候选集成仍待，不减少未决数。默认amd64路径保留。用户不需要AIX环境，不改变随包50项范围，AIX两项继续查上游证明而非删除。

Windows x86首轮41990625821构建成功但[独立来源核验未接受](windows-x86-first-build.json)：1297源路径一致，1085字节摘要不同；隔离checkout显式core.eol=lf后重验，未将未解释差异算通过。另新增实际32位C进程核严格DLL路径、六类sizeof、malloc/free、卸载与缺库拒绝；结果仍待。来源计数及整体P1完成状态不因此改变。

## Windows x86统一候选集成

2026-11-05 P1继续集成Win32：新增精确41992679907原包stager及DLL/回执/libffi漂移、错误资源scope拒绝控制，两控制通过；真实统一28来源/22原版候选装配，其他4510条目保持，本机生成JAR逐字节恢复。五目标CI复用同一Win32原包并启用新组件，待新完整验收；正式仍20570ce27/23。AIX资源保留，P1未完成，无付费模型。 见[实际装配收据](assembly28-control.json)。已验原包scope固定为win32-x86；完整32位JVM仍未认证。首次本地装配对已有preview基线被正常拒绝；改用保存的原始基线通过，未放宽重复预览门禁。
