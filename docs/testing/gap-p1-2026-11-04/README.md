# P1：原生来源补齐执行记录

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
