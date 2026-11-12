# P1：原生来源补齐执行记录

- 2026-11-12 OpenBSD KVM43187469630原包217434字节SHA/CRC核过：RAMDISK早期退出0但无安装完成标记，后次KVM报Permission denied，未安装成功/未进源码。新增exit0+官方完成文本双门禁，9控制过；改qemu32显式CPU及官方kvm组+sg有效组方式替代临时ACL，早退与ACL丢失具体根因仍未证，真实修正待。SPARC交叉工具链43185951645已CI成功并链接32/64探针，390545388字节原包SHA/CRC、746精确Git输入/14回执/11199归档成员及32/64实际ELF已独立核过，工具链可复用但guest执行/JNA组件仍待，不计组件通过；正式44保持。

- 2026-11-12 OpenBSD43181269997原包546276字节SHA/CRC核过：源码08:27:38至08:52:38触1500秒总预算，采样为checkout/build subprocess wait，未回传组件，不能判通过。新增仅x86且可读写真实/dev/kvm时选KVM并记录，CI仅当前runner ACL；不可用仍TCG，预算/原生i386 ABI不变。失败后另起180秒owned局部日志/config归档，保留原失败与部分包摘要，防止取包失败覆盖主因。8bootstrap控制过，真实KVM/组件待；SPARC43185951645继续，44正式不变。

- 2026-11-12 SPARC独立交叉工具链入口：固定GNU GCC13.4官方SHA512/Binutils2.40发布SHA256及已核真实sysroot，Linux owned目录构建Solaris目标C编译器/libgcc并分别-m32/-m64链接实际探针；不解除IPS锁、不用Linux sysroot替代。sysroot先全量拒绝越界/重复/链接祖先，再把目标绝对链接迁到owned根；4项拒绝/ELF控制通过，Windows真实symlink留Linux CI执行。本机首轮sandbox临时目录权限失败，改workspace owned临时目录后通过；实际交叉构建/guest执行仍待，44正式保持。

- 2026-11-12 SPARC43179892414原包1360571字节SHA/CRC核过，实际pkg install返回1：最新userland要求2026.0.0.21764的header/linker/osnet，而官方catalog三项仅有2025.0.0.21764，确认仓库约束冲突，不再盲重跑或解除锁。官方SPARC目录另发现与guest内核31d3d510d0对应的19333056字节sysroot及独立GCC说明，原包SHA753ca4b2已核、10461成员只读检查，crt1/libc实际为大端ELF32/machine18(SPARC32PLUS)及ELF64/machine43；初始预期machine2失败已留账，完整来源/ABI/构建适用性仍待，未新增组件通过。证据sparc-repository-conflict-2026-11-12.json，正式44/50保持。

- 2026-11-12 输入路径批量检查优化：稳定owned源码树每个前缀lstat一次，仍拒绝非法路径/缺失/链接/reparse，逐文件哈希不变。确切f81快照743输入新旧哈希全等，本机单轮Path.lstat4008→993、1.3241→0.2727秒（非OpenBSD/SLA证明）。16IO/11命令证据/3来源/2OpenBSD控制过；POSIX实际symlink拒绝加入host CI，Windows为受控syscall结果验证。新native实效待；不提升44正式。

- 2026-11-12 OpenBSD43175405211栈诊断原包546327字节SHA/CRC核过：已过宿主包/编译器/JDK/clone并进入build；120秒采样明确出现evidence_run.inputs→bounded_path→lstat和实际构建subprocess wait，原源码命令总预算到期、未回传组件。源码快照743输入仅5679179字节，下一步在稳定owned树内批量校验共享路径前缀，保持逐文件哈希/拒绝链接与越界，未断言全部超时原因。正式44/50保持；SPARC43179892414原运行继续。

- 2026-11-12 SPARC43175410363原包1357527字节SHA/CRC核过：协调预览只有Solver setup进度，guest driver于600秒返回timeout/result，未形成新冲突或成功计划。移除重复dry-run，让一次正式pkg install自身求解/输出计划/执行，保留incorporation及全部版本约束，2400秒既有安装预算不变；3隔离/清理控制过。真实安装仍待，OpenBSD43175405211原运行继续，44正式保持。

- 2026-11-12 SPARC协调安装准备：依据IPS官方incorporation约束说明，将userland-incorporation@latest与工具包交给同一求解器，先-nv预览后安装，不解除版本锁。guest命令用set+e外层/严格子shell隔离，真实false控制证明保留退出1及外层采集标记，避免失败登出后空等；3隔离控制过。仅本地准备，真实协调安装待；P1 CI另构只含P1文件的提交以保留本地UX链，44正式不变。

- 2026-11-12 恢复P1核旧两原包SHA/CRC：SPARC42459097659已PPP ready且官方HTTPS catalog刷新成功；安装器明确旧userland-incorporation排除新binutils/Git/JDK17/Ant版本，需协调包版本，不再按网络故障处理。OpenBSD42459464777已/home可用2624976KiB并完成checkout，但第一host-packages附近停滞；加120秒重复Python栈快照定位具体调用，不延长预算，2控制过。工作区已有UX-A～F用户提交，保留并避免随P1远端推送；正式44/50保持。

- 2026-11-08 AIX原载荷构建线索细化：两原SHA复核并逐字节偏移定位GCC8.3.0/AIX7.1目标运行库及include-fixed路径、实际ppc/ppc64-7.0.0/libffi构建目录。上游BuildingNativeLibraries文档于2024新增并描述7.0.2在cfarm111的流程，不能倒推2023原7.0.0构建证明。新增可复核字节收据，精确命令/SDK/生成静态输入仍缺，两AIX仍OPEN，44正式/45候选保持；两原专项继续。

- 2026-11-08 OpenBSD42457557429原包582041字节SHA/CRC核过：原生32位标记与动态Python前置已达，实际源码build失败且报告/归档明确Errno28磁盘不足，未验组件。将新VM源码工作区/组件归档从/root移到/home，checkout前记录df并要求home至少2GiB空闲，不改已有用户目录；8相关控制与三个远程命令shell语法过，真实修正待。Cygwin默认关闭装配准备87ed同步保留，正式44/50、本地45候选不变。

- 2026-11-08 已验Cygwin统一装配接线准备：新增默认关闭JHARNESS_P1_CYGLAUNCH入口，复用精确双模式组件stager、1项不相交资源与验收收据绑定。2原包stager/3既有统一装配控制过；完整入口执行留待下一批，未开启workflow、未改44来源map/数量、未动本机JAR或重跑45矩阵，正式44/50/本地45候选保持。OpenBSD42457557429与SPARC42459097659同轮继续。

- 2026-11-08 SPARC42457560092原包1069905字节SHA/CRC核过：通道已连接、guest真实sppp0发送10次LCP ConfReq但无收包，内部日志明确LCP超时exit10；未到工具安装。宿主ppp.log为空，不能再用guest日志推断宿主具体原因；加载ppp_async依赖，并在自有容器用owned包装让原pppd的logfd2进宿主采集（PPP字节仍走stdin/stdout），不改产品/guest协议。3隔离/清理控制及shell语法过，修正与宿主根因待，正式44/50保持。

- 2026-11-08 两原专项终态原包SHA/CRC核过：OpenBSD42453883100源码SSH1500秒/回传600秒超时，native-tools明确Cannot find python%3.13（pkg_add仍exit0）；改复用已验x64动态Python包查询/安装/真实>=3.11执行前置，SSH保活与逐命令进展，超时完整原因仍未证。SPARC42456290633通道socket已连而PPP exit10，宿主waiting_for_ppp；上游脚本内部日志写/tmp/gppp*，补准确日志/运行脚本采集，不猜协商根因。11相关控制及动态安装shell语法过；44正式/45候选不变，真实修正待。

- 2026-11-08 SPARC42454910095原包925731字节SHA/CRC核过：宿主PPP模块/容器/真实guest启动与启动请求通过，但ppp-ready等待失败，未到包下载。现有原包只留请求/容器入口日志，不能断言具体PPP根因；补guest socket就绪等待与两侧owned有界网络日志，清理失败仍回收自有资源，3控制过。原生组件计数不变；OpenBSD42453883100同轮继续。

- 2026-11-08 SPARC共享源码入口准备：固定c366 JNA原Solaris配置扩展sparc ELF32/大端/machine2与sparcv9 ELF64/大端/machine43，强制sun4u/sun4v/sparc原生宿主家族，x86家族隔离保留；gcc分别-m32/-m64，原两SPARC库存都按实际ELF64留证，不把错误32标签当通过。3控制含字宽/字节序/机器号/宿主替代拒绝通过；实际SDK准备42454910095与OpenBSD42453883100继续，未构建/未增加来源计数。

- 2026-11-08 SPARC工具准备：官方oi-sparc HTTPS versions及p5i实核提供gcc7/10/13、JDK17、Ant、Git、GNU make/binutils/系统头/链接器（gcc14包名404）；选择gcc10。新增显式provision模式，仅CI自有容器NET_ADMIN+/dev/ppp与容器内转发，PPP ready门禁后访问官方HTTPS仓库，安装并记录完整包/manifest/工具版本；无镜像或guest文件进入产品。3默认隔离/故障清理/显式权限控制过；真实PPP/工具安装待，未增加44正式或45候选。

- 2026-11-08 SPARC42453885869原包712870字节SHA/CRC及10命令/日志回执、精确宿主Git输入核过：固定b92b镜像自动启动与root登录成功，真实SunOS5.11/illumos-31d3d510d0/sun4v/sparcv9，Python3.14路径存在、默认3.9.25；检查路径未找到gcc/JDK/Ant/Git/GNU make。owned容器/卷清理通过。下一步配齐精确编译工具/SDK再构建，不计原生来源闭合；正式44/50、本地45候选保持。OpenBSD42453883100原专项继续。

- 2026-11-08 恢复P1核两原包SHA/CRC：OpenBSD42377603545实际打印SOURCE_JNA_ABI_AND_FFI_PROBE_PASS，但BSD tar不支持--exclude，未取回组件原包，不能独立验收；改Python tarfile保留证据排除jna，6bootstrap/2JNA控制过。SPARC42377224992自动启动/登录成功，sun4v/sparcv9与make/python3/pkg路径已读，工具探测在pkg publisher后超时；改直接查可执行路径，2隔离控制过。两修正真实结果待，正式44/50、本地45候选保持。

- 2026-11-07 OpenBSD42376661936原包294166字节SHA/CRC实核：安装exit0、磁盘内核启动与SSH均已达；native-target因OpenBSD getconf不提供LONG_BIT失败，非ABI不符。改原生cc编译/执行自有C检查sizeof指针与long均4字节，uname i386门禁保留；相同探针在本机Win64实际返回1拒绝。5bootstrap/2JNA控制通过，真实i386探针/源码构建待。SPARC42377224992已启动固定镜像可行性专项，正式44/50、本地45候选保持。

- 2026-11-07 SPARC新增可行性入口：核公开OpenIndiana/illumos Niagara镜像amd64 OCI摘要b92b4014，压缩层约1.76GB，阅读固定d06825a源入口/来源说明；仅无网络、无额外cap/device的owned容器探测真实启动/系统及工具清单，不当组件验收。2控制覆盖旧资源拒绝、guest与容器清理失败仍回收自有卷并留报告。OpenBSD42376661936同一专项仍运行，正式44/50、本地45候选不变。

- 2026-11-07 OpenBSD重跑前衔接审查：源码archive不带.git而共享builder要求真实HEAD，改无凭据拉取公开仓库的精确GITHUB_SHA并核HEAD；回传目录改为真实.build-cache/p1-openbsd-jna/x86，打包失败保留非零退出。两个远程shell语法检查及5bootstrap/2JNA控制通过；仅本地检查，真实guest checkout/构建待。与BUILDINFO修复合并一次专项重跑，未重跑完整矩阵。

- 2026-11-07 OpenBSD i38642376031485原包219980字节SHA/CRC实核：五个选定安装集均Get/Verify通过，随后BUILDINFO缺失404，失败检测数秒内停止而未空等。补官方BUILDINFO进入现有签名清单/逐文件SHA输入循环；官方摘要ddc2410f与清单一致，5专项控制通过。未启动完整矩阵；正式44/50、本地45候选保持，真实安装/源码验证待。

- 2026-11-07 OpenBSD i38642373750406原包219KB实核：签名输入通过，真实i386内核/网络/自动分区/基础集校验已运行，安装器默认game79未被正向列举取消而服务未提供，404后回到交互菜单，旧预算1500秒到期。修正应答为明确-game*/-xfont*/-xserv*，基础/编译器/X基础共享库仍保留；新增选择集与已提供签名文件一致性控制，安装器明确报错即时失败，5控制过。未装完/未计组件来源，正式44/50、本地45候选不变。

- 2026-11-07 Cygwin42375096503@d10双模式原包独立验收：134固定源/单文件Linux头guard全重放、126缓存输入/固定安装器、14回执/738宿主Git输入、x64 PE/原库存及有日志+默认无日志真实三管道PTY/三isatty/自有cwd/输入往返/exit23/缺文件拒绝均过。2stager漂移/scope控制过；本地45来源/5原版候选已装配并保留4493其他条目，本机JAR未动。正式44/50不变；全局已验map/完整工作流仍44，留到下一批集中启用，未重跑矩阵。

- 2026-11-07 Cygwin双模式42374668507实核小原包：有日志PASS，无日志candidate退出11/ERROR_BROKEN_PIPE109/launcher仍ACTIVE259，不能按诊断轮成功闭合。发现自有C探针CreateProcess未给标准句柄，与Java ProcessBuilder有差异；现仅给launcher显式继承owned NUL标准三流，PTY仍走原命名管道并强制三isatty/目录/双向输入/exit23，真实修正待。MinGW严格编译/缺文件拒绝及5控制过；VM进展4控制过。正式44/50不变。

- 2026-11-07 P1 VM观测提速：为后续owned i386安装新增串口字节进展与180秒无进展/1500秒总预算门禁，明确区分活进程停滞与真实终态；4控制通过。当前42373750406旧运行仍由原预算执行，未因观察超时重启；Cygwin双模式42374668507仍运行。正式44/50不变，不重复完整矩阵。

- 2026-11-07 P1专项新证据：Cygwin42374181949诊断版源码/PTY/缺库专项CI成功，尚未独立原包验收；同一构建增加无日志默认模式，与已成功有日志模式并列验证，原三流/目录/exit23断言不变，不重跑44矩阵。AIX2/SPARC2原载荷全部实核Git blob与库存SHA，对应上游767662a重建提交，未取得精确编译器/SDK/命令记录，不能据此闭合。新增来源审计收据；5本地控制/严格MinGW编译/缺文件反例过，正式44/50不变。

- 2026-11-07 P1集中专项：Cygwin42327556071已编译载荷/双探针，实际candidate-probe退出11。仅Range读取两ZIP成员并核CRC获得真实退出码（非全包验收），未重下159MB工具链缓存。新增握手失败前管道字节/错误/owned进程状态和launcher自有日志，原PTY/目录/三流/exit23断言及超时不变；MinGW严格编译/缺文件拒绝及5控制过。后续失败仅上传小型原证据，完整签名工具链缓存在成功轮单独封存，44完整验收不重跑。

- 2026-11-07 P1正式升44/50：42326568858@de五目标与同字节全部成功，六原ZIP独立核每侧735 Git输入/735 Java用例/124套件/14回执、813浏览器及R4每引擎51、50库存/44来源/6原版与候选字节全部通过；含终态审批修正的新44产品已验，不认旧Mac历史因果。剩AIX2/SPARC2/OpenBSD x86/Cyglaunch共6仍OPEN。原包只下载一次，五侧审计并发完成、统一门禁复用同一原包，不重复CI；OpenBSD仅签名base集传输预算修正专项继续。P1未完成。

- 2026-11-07 按用户提速要求集中验证：44候选42326568858@de五目标/同字节已成功，仅取六原包独立复核，不重跑该轮。OpenBSD i386首轮42327556044签名验证过但官方base79.tgz实为282575165字节，超过旧256MiB传输预算；仅该签名输入上限改320MiB，其余预算及全部签名/摘要门禁不变，3流预算/隔离控制过。真实VM仍待；正式39/50不提前提升。后续先组件专项，合并稳定后再完整回归，原包下载/备份去重。

- 2026-11-07 P1 OpenBSD i386确认官方7.9包提供JDK21/Python3.13；新增固定官方公钥/签名清单/逐文件SHA核对的owned QEMU i386 PXE全系统入口，loopback HTTP/SSH、独立磁盘、仅自有进程组退出清理，私钥不归档。共享JNA新增原生i386目标且保留原x64强制架构匹配，JNI/ffi原断言不变；2bootstrap/2ELF副本控制过，真实VM与组件待。Cygwin42326565432@de已编译载荷/子探针，但Windows探针DWORD在Cygwin为unsigned int与%lu冲突，显式cast保持格式语义，MinGW严格编译/缺文件拒绝及5控制过；新增失败探针日志回传。正式39/50、候选44不变。

- 2026-11-07 P1终态审批竞态：42原包Mac Firefox在过期审批按钮移除断言超时；新增受控旧响应后旧JAR在原15秒谓词稳定失败，新码以已读终态隐藏并清除审批，原完整浏览器同用例成功（含真实过期决定409），42客户端/26证据控制/UI构建/Maven package过。添加browser原失败文本/截图封存。仅证明受控竞态修正，不认原Mac因果或完整验收，正式39/50与候选44不变。Cygwin第三轮源码已编译成功，但调用PE门禁错误传x64而非其amd64，修正实际调用并补x64/x86/截断控制（共5控制）过，真实PTY仍待。

- 2026-11-07 Cygwin42324794762@405工具链修正已越过配置，真实固定源码在exec_pty.c无条件包含Linux sys/syscall.h而Cygwin不提供，编译失败不验收。以93cd固定源SHA绑定，仅将该include置于__linux__（既有close_range调用已有同条件），不补伪头/不改实际调用或测试；记录精确单文件补丁前后SHA。4边界控制及固定原始源码重放/漂移拒绝过，实际修正待；44候选f308已推送，完整回归待。

- 2026-11-07 Console ARM64两项独立原包验收：42322393412@ebd的3629 Console/11570 vcpkg固定源与既审原包逐字节全等，五组静态端口每成员/源码归档/NuGet/bootstrap全等，20构建日志与ARM64 PE过；42323927902@2a的6回执/728宿主输入/3严格重编译类/固定JNA-helper/原生ARM64 JVM cwd/process-list/resize/exit及缺JNI/缺bundled拒绝过。2stager漂移/scope+3统一控制过，实际44替换/6原版候选、4494其他条目保持，本机JAR未动；正式39/50不变，新完整44五目标待。Cygwin修正42324794762再次失败，原日志待定位。

- 2026-11-07 Cygwin42324410029@827首轮实际安装/固定源checkout过，但CMake自动选宿主MinGW CXX及Strawberry make，Cygwin路径无法被宿主编译器读取，配置失败不验收。显式安装Cygwin gcc-g++并固定C/CXX/make绝对POSIX路径；3本地边界/宿主工具污染控制过。首轮原包13554806720/4e258db6保留，修正待CI；正式39/50、候选42不变。

- 2026-11-07 P1 cyglaunch x64新增固定b7554d6源码/Cygwin 2.941官方SHA512与签名包索引默认验证的隔离CI入口（仅runner安装、不在本机安装）；记录实际包/编译器/运行库与缓存全部输入，原库存SHA保持。新增三命名管道原生探针检查PTY三流/独立stderr/自有cwd/双向输入/exit23与缺launcher拒绝，使用owned job退出清理。两边界控制与本机MinGW严格编译/真实缺文件拒绝过；Cygwin源码编译/PTY真实运行待，不增加正式39或候选42。Console ARM64专项42323927902已成功，独立原包审计待。

- 2026-11-07 Console ARM64源码42322393412@ebd构建成功，原ZIP13553941914下载摘要核过，两输出严格ARM64 PE且精确SHA绑定。新增原生ARM64 JVM专项，复用原ConPTY目录/进程数/resize/exit断言及缺JNI/缺bundled候选反例，固定已验JNA/helper源包，不作产品验收替代。两原包漂移控制与本机JDK21 Java严格编译过；真实ARM运行/独立3629源及静态依赖审计待，不增加正式39或候选42。42五目标42323581749@a934与40轮均运行中。

- 2026-11-07 ARM64 WinPTY42228145670@ead两原包载荷独立验：180固定上游源/134参考源、逐字节重放目录扩展/完整source-after、12回执/720宿主Git输入、精确ARM64 PE、真实owned agent/resize/process-list/退出/卸载及缺库拒绝过。两stager漂移/scope控制与3统一控制过；实际42替换/8原版候选、4496其他条目保持，未改本机JAR。正式39/50不变；40轮42323058839仍运行，下一42五目标待。Console42322393412源码构建成功原包已下载，原生加载交互/独立源审计待。

2026-11-07 P1统一39轮42226835895@904d五目标与同字节全部通过，六原ZIP独立复核：716 Git输入/侧、735 Java/124套件/侧、14命令回执、813浏览器及R4每引擎51、50项库存/39替换/11原版与全部候选内容过。正式39/50（剩11）已验；OpenBSD x64另装配第40项并通过3统一/2stager控制，40完整回归待。ARM64 Console修正轮42322393412@ebd实际运行中；ARM64 WinPTY42228145670成功原包已下载，源码/ABI独立审计待，不提前计验。P1未完成。

2026-11-07 OpenBSD x64已审原包纳入第40项候选：精确run/commit/单资源scope、16回执含mounts、运行探针SHA与/usr/local自有路径强制校验；两stager反例控制通过。实际40替换/10原版、4498其他条目保持，未动本机JAR。统一工作流锁定42227334617原包并前置要求40；全新五目标验收待。39轮五侧/同字节CI成功，四侧原包已独立核，Linux x64原包下载超时重试，正式34/50尚不变。

2026-11-07 ARM64 Console42227339528实际完成MSBuild后在ARM64 PE校验导入失败（evidence_io不在搜索路径），未验收。仅为docs工具添加仓库scripts路径，8构建控制/2安全控制及隔离Python进程真实导入通过；修正CI待，不增加来源计数。

2026-11-06 恢复P1：42157222835@a537五目标和同字节成功后独立核六原ZIP，实际34资源（LoongArch启用变量漏写，未计35）；705 Git输入/侧、735 Java/124套件/侧、14回执、813浏览器与R4每引擎51均过，正式升34/50、剩16。补工作流LoongArch变量及替换数量前置门禁，真正35轮待；ARM64 Console离线缺工具测试固定自身x64目标避免继承ARM环境，8项在ARM环境过。ARM helper42158936851原包独立核134源/711宿主输入/10回执/ARM64 PE/两目录/无效PID/卸载/缺库拒绝过，未装配；Solaris两侧和ARM JNA已CI成功待原包审计，OpenBSD加载探针失败待。P1未完成。

2026-11-06 P1正式进展：统一32候选42150336971@b86aaa1五托管目标与同字节门禁已完成原包独立验收，正式来源32/50、剩18未完成产品闭合。每侧689项Git输入、735项Java/124套件、14回执、813浏览器及R4各引擎51项；FreeBSD PTY两项正式闭合。34候选42151580514四侧成功、macOS Intel在浏览器矩阵失败，同字节门禁未执行，不验收；原包诊断继续。候选仍34/50、16来源OPEN。OpenBSD修正轮待明确远端写入授权。无付费模型/推送/发布。

## 2026-11-06 状态复核

32项候选五目标及同字节CI门禁成功但原包独立验收待；34项候选两Linux成功，其余运行中。正式30/50、候选34/50不变，16来源仍OPEN。OpenBSD修正轮尚未派发，推送被自动审批拒绝，待明确远端授权。5项本地控制通过不代表产品验收。见[状态收据](status-recheck-2026-11-06.json)。

2026-11-06 P1正式进展：统一30资源候选42000054331@7e2978e五托管目标和同字节门禁全部通过；六原ZIP独立复核685 Git输入/侧、735 Java/124套件/侧、14回执/侧、813浏览器及R4每引擎51检查，正式来源覆盖30/50，剩20产品来源未闭合。FreeBSD双侧42003119496@406另独立核134固定源/10回执/687 Git输入/实际ELF及native子进程exit23/缺库拒绝，两组件已验但未集成；18项仍缺组件闭合证据。两组原包/审计器本地逐成员SHA和CRC备份通过；P0本地证据门禁及两个Win32 stager各两控制过。P1整体未完成，无付费模型，实体机器跳过。

当前逐项状态见 docs/testing/gap-p1-2026-11-04/progress-ledger.json；历史快照按日期保留。

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

## Windows x86 WinPTY两项源码组件

2026-11-05 P1下一批Windows x86 WinPTY两资源：沿用固定7e59fe2及已审owned-directory补丁，新增显式x86 GCC/machine/PE门禁、Win32目录探针及实际32位C++精确DLL/owned agent/resize/process-list/退出/缺库拒绝专项。旧x64默认保留，x86不构建无对应库存任务的helper；七构建控制与三补丁控制过，远端实际构建/组件待，不增加正式27或候选28。统一28轮41997597256@71b正在运行；P1未完成，无付费模型。 本机没有i686 C++编译器，未冒称本地原生探针已运行。构建与探针通过前保持逐任务OPEN；旧WinPTY 0xC000013A历史因果不随新组件通过而关闭。

## WinPTY x86独立验收与30项候选

2026-11-05 P1 WinPTY x86两组件41998518872@7c成功并独立复核：180上游固定源字节与官方归档全等，本地重放目录补丁与报告全等，8构建日志/3探针回执/683宿主Git输入/PE32/实际owned agent生命周期/缺库拒绝过。新增成对stager和DLL/agent/回执漂移、缺agent scope反例，两控制过；实际统一30来源/20原版候选装配，4508其他条目保持，本机JAR恢复。正式27/23不变，28轮41997597256尚待；新30完整五目标待，P1未完成，无付费。 见[组件收据](windows-x86-pty-components-acceptance.json)、[真实装配](assembly30-control.json)。完整32位Java与旧0xC000013A因果均不在本次组件证据范围。

## FreeBSD原生VM来源专项

2026-11-05 P1继续FreeBSD x64 PTY：核固定VM动作c46abac及其release/arch/env接口，新增14.4 amd64 CI VM内固定b7554d6真实源码构建/ELF/原生PTY/缺库拒绝专项；复用PTY探针新增显式native子进程模式，原QEMU五参数模式保留。两ELF控制/脚本编译与错误宿主拒绝过，FreeBSD实际结果待，未增加正式来源数。统一28轮41997597256五侧+同字节门禁成功待原包；30轮42000054331@7e正在运行。实体机器跳过，无付费，P1未完成。 VM动作及输入以[固定源码](https://github.com/vmactions/freebsd-vm/blob/c46abacb49f09938ca4e1702d15d836285d694cc/action.yml)核对。构建记录客户机系统/包清单/编译器和完整Git输入；固定源码删除目标预编译库后强制gmake构建，核前后只有目标载荷变化。C级探针不视为FreeBSD完整Java产品验收或OS/CPE证明。

2026-11-05 FreeBSD首轮42001243916 VM14.4及包安装成功，源码构建前因全新VM无.build-cache父目录而mkdir失败，原始作业日志摘要已保存freebsd-first-run；修为parents=True且保留拒绝复用目录。首轮无组件产物/不验收，修正远端待；P1仍未完成。

2026-11-05 FreeBSD x64 PTY42002054105@ed修正轮成功并独立核原ZIP/134固定源/10命令与日志/687宿主Git输入/ELF/实际原生fork-exec交互exit23/缺库拒绝，组件验收见freebsd-x64-components-acceptance；尚未集成，不增正式27或候选30。扩展同VM的x86编译/ELF/原生运行专项，与x64分别记录，不将宿主amd64冒称32位验收；实际双侧待。P1未完成，无付费。

## FreeBSD双组件统一集成

2026-11-06 P1 FreeBSD两组件集成：锁定42003119496原包/双字宽精确scope/报告/原始库/探针及10命令摘要；实际双原包通过，三种载荷/回执篡改拒绝。真实统一32来源/18原版候选装配，4506其他条目保持，本机生成JAR逐字节恢复。五目标工作流复用固定FreeBSD双原包，完整新候选验收待；正式仍7e2978e30/20，P1未完成，无付费，实体机器跳过。 见[真实装配收据](assembly32-control.json)。不把五个既有目标回归视为FreeBSD完整Java应用认证。

## 网络恢复与32项完整回归

2026-11-06原生Git推送已成功，远端专用分支摘要等于b86aaa1；[统一32完整CI](https://github.com/nixaveq141-ai/J-Harness/actions/runs/42150336971)已启动。DNS/HTTPS/Git读取均通过，未修改网络配置；只证明当前连接恢复，未确立此前间歇超时根因。新候选验收待，正式30/50不变。

## FreeBSD JNA双字宽来源专项

2026-11-06 P1继续FreeBSD JNA两项：新增固定c366bc4源码+既审libffi3.4.4生成输入，在14.4 amd64 VM分别cc/cc -m32、gmake/Ant强制构建，核原始库存/双副本/ELF/前后源码，实际JNI六sizeof/malloc/free与ffi call41/closure46、回调字宽保护及缺库拒绝专项。两本地字宽/端序/载荷副本控制过，实际远端结果待；正式30/50、32候选回归仍待，不提前闭合两来源。无付费模型，实体机器跳过。 复用已验证的FreeBSD VM动作和JNI/ffi探针；交叉字宽下不将64位JDK运行误作32位JVM认证。未取得原包独立复核及后续产品集成前，两项保持OPEN。

## FreeBSD JNA独立验收及34项候选

2026-11-06 FreeBSD JNA42150888437@ca两侧成功且独立核原包：1297固定源/15回执/完整Git输入/libffi归档/原始库存及ELF/回调字宽保护/JNI与ffi call41 closure46/缺库拒绝过。两stager控制过，真实统一34来源/16原版候选装配，4504其他条目保持，本机JAR恢复；原包及审计器本地逐SHA/CRC备份过。正式仍30/50；32轮和新34完整回归待，P1未完成，无付费模型。

## OpenBSD x64来源工具链实测

2026-11-06 P1继续OpenBSD x64 JNA来源：核VM动作固定f1ca8bc、增加7.9 x64隔离构建入口，固定JNA/libffi与原始库存、ELF和真实JNI/ffi回调探针；仅对自有回调探针指定wxneeded，不改产品权限。两本地ELF/副本反例过，实际工具链/构建/加载待，OpenBSD来源保持OPEN，不宣称x86或完整Java验收。统一34轮42151580514已启动，P1未完成。 VM动作修订来自[上游固定源码](https://github.com/vmactions/openbsd-vm/tree/f1ca8bc8ecc5b6ef7218e5b914836e067e1546d3)，包安装和实际原生构建通过前不计新增来源。

2026-11-06 OpenBSD首轮42151888519 VM启动且JDK21安装过，仓库无python%3.12、解释器不存在，源码构建未执行；原作业日志摘要见openbsd-first-run。改为实际pkg_info查询Python>=3.11包及其安装路径，执行前验证版本，修正结果待。统一32和34回归仍待，P1未完成。

## LoongArch64构建入口与持续远端授权

2026-11-06 P1持续完成授权：用户明确授权nixaveq141-ai/J-Harness专用codex/m1059-full-validation分支推送及P1 CI直到验收，撤销此前仅本地限制；不含main/Release/付费模型。1681bbb推送成功，OpenBSD Python修正轮已派发。新增LoongArch64固定源JNA/GCC14交叉构建入口与LP64D/object ABI v1门禁，本地4控制过，实际CI待；正式32/50、候选34/50不变，P1未完成。

Ubuntu官方包索引确认[GCC14](https://packages.ubuntu.com/gcc-14-loongarch64-linux-gnu)及[sysroot](https://packages.ubuntu.com/search?keywords=libc6-dev&suite=noble-updates)可供Noble amd64使用。ELF门禁依据[LLVM ABI定义](https://llvm.org/doxygen/BinaryFormat_2ELF_8h_source.html)核EM_LOONGARCH=258、ELF64/小端、LP64D及对象ABI v1；原已验JAR中LoongArch载荷flags实核0x43。工具链安装版本由实际CI包清单留证，不将软件包存在当构建通过。独立工作流仅运行此新目标，原八目标配置保留；固定JNA/libffi源与JNI/ffi call/closure/缺库拒绝探针沿用。

## 两新目标的首轮失败与修正

2026-11-06 P1新目标首轮：LoongArch42155339078@031613c实际GCC14/sysroot安装并进入固定源码构建，libffi3.4.4 tramp.c隐式open_temp_exec_file编译失败；保留原包13474675214/e7697c8。仅LoongArch使用上游支持disable-exec-static-tramp配置，真实ffi call/closure验证仍必须通过。OpenBSD42154858512@1681bbb包查询后未选出Python，未到源码构建；增加完整查询输出并按首字段处理包名。两修正待实测，本地4控制过，正式32/50不变。

libffi选项见[固定3.4.4 configure.ac](https://github.com/libffi/libffi/blob/v3.4.4/configure.ac)；该配置保留动态closure，不禁用回调，不关闭GCC错误。OpenBSD按[pkg_info官方格式](https://man.openbsd.org/pkg_info.1)保存原查询结果并取包名字段；是否解决实际选择问题待新轮日志，不把推测当根因。

## Windows ARM64 JNA入口

2026-11-06 P1新增Windows ARM64 JNA入口：核标准windows-11-arm及官方镜像工具/JDK21 ARM、固定MSYS动作ec48f7c，使用固定JNA原生MSVC ARM64分支与既审libffi输入；明确保留上游ASMFN_OFF行为。DLL/探针均强制ARM64 PE32+，C JNI精确加载/六sizeof/malloc/free/卸载/缺库拒绝；本地2项PE/截断控制过，实际远端构建待，正式32/50与34候选不变。

工具可用性依据：[GitHub标准ARM私有仓库支持](https://github.blog/changelog/2026-01-29-arm64-standard-runners-are-now-available-in-private-repositories/)、[官方ARM镜像清单](https://github.com/actions/partner-runner-images/blob/main/images/arm-windows-11-image.md)、[MSYS2 ARM支持](https://www.msys2.org/docs/arm64/)。这些资料不是实际构建/加载证据；实际SDK/编译器/宿主版本与输入SHA由本轮回执取得。

## LoongArch组件验收与统一35

2026-11-06 P1 LoongArch42155835171@521成功且原包独立核验：1297固定源、15实际回执、完整Git输入、libffi生成输入及ELF/LP64D、JNI六sizeof/malloc/free、ffi call41/closure46和缺库拒绝过。新增精确stager与载荷/归档/scope反例两控制过；真实35来源/15原版候选装配，4503其他条目保持，本机生成JAR未动。正式32/50；35完整验收待。OpenBSD新轮已到源码构建失败，改VM正常copyback后独立host门禁强制原退出码/报告通过，以保留原始失败证据；不冒称解决构建。Windows ARM首轮开发环境路径引用失败，修为独立cmd包装并用含空格真实batch控制通过（共3项），修正远端待。

[组件验收](loongarch-jna-component-acceptance.json)绑定实际原包13474233116 SHA 6d7f85514b4dd25fa802db6968d34048ad5dbd261535ca4546bc8cb0017befd5；[装配控制](assembly35-control.json)使用已验b86候选的原版canonical baseline，仅证明替换字节及其余条目保持，不代替新源码的五目标产品验收。

## SunOS x86两字宽入口

2026-11-06 P1继续SunOS x64/x86：固定Solaris VM动作1c16697及11.4-gcc-14，新增固定JNA/libffi源构建/ELF/真实JNI与ffi回调入口；显式区分原sunos-x86错误64位库存和新32位目标，不将原版当32位通过。VM失败回传后由host独立核退出码与报告，真实SDK/构建/加载待；本地2项ELF/副本控制过。SPARC/AIX来源未闭合，正式32/50不变。

工具链与包入口核对：[VM官方11.4 GCC14镜像](https://github.com/vmactions/solaris-vm)、[Oracle开发工具包名](https://docs.oracle.com/en/operating-systems/solaris/oracle-solaris/11.4/config-app-dev/introduction-setting-up-application-development-environment-oracle-solaris-11.html)。JDK8只用于此固定JNA组件的头文件生成和C JNI头，不声称目标Java21或完整产品验收。新32位库要求machine3/ELF32；原版仍按库存SHA及已发现machine62/ELF64保存，不修改原始证据。

## OpenBSD与Windows ARM构建故障定位

2026-11-06 P1故障继续定位：OpenBSD42157222861@a537已回传原构建日志，固定JNA头生成明确缺ggrep；增加GNU grep包，实际修正待。Windows ARM42157224796@a537已越过MSVC环境初始化/固定源准备，Ant因cmd批处理拆分OS=win32属性误识别win32目标；改直接调用已安装Ant Java launcher，保留精确参数与launcher SHA/版本回执。原构建/加载断言不弱化，本地ARM3/OpenBSD2控制过，来源计数不变。

## Solaris首轮与构建配置修正

2026-11-06 SunOS首轮42157697276@959两侧VM/包安装/固定源libffi配置已运行，依赖跟踪片段初始化失败，未验收；选择configure提示的disable-dependency-tracking用于全新隔离构建并归档config.log。两本地控制通过，真实修正待。OpenBSD与Windows ARM的e2b9ee3修正轮已派发；35候选完整轮42157222835运行中。

原包与原断言保持；关闭自动依赖跟踪不关闭任何功能或运行验证，只用于每次全新的源码构建。已保存七份失败作业的连接器解码日志到本地忽略缓存（LF规范化，非原始字节声明），以便核对各次失败阶段。

## Windows ARM64目录helper入口

2026-11-06 P1 Windows ARM64目录helper新增固定pty4j b7554d6/MSVC原生Ninja构建入口；DLL和探针强制ARM64 PE32+，仅操作自有进程，核精确DLL路径/两目录读取/无效PID/卸载/缺库拒绝，源码前后全等。共享ARM门禁/空格batch3控制和本机C++语法检查过（语法检查非ARM运行认证），实际远端待；不增加正式32或候选35计数。

沿用上游原CMake/C++源码，不将其他架构运行算作ARM证据。自有探针完成后退出，由OS回收该进程资源；不声称修复原helper源码的任意进程兼容性或完整产品功能。

OpenBSD42158136194@e2已越过ggrep头生成，libffi配置中Clang拒绝GCC专用print-multi-os-directory且depfiles初始化失败；下轮显式MAKE=gmake，并关闭dependency-tracking及multi-os-directory，归档config.log。仅全新构建配置，不放宽实际JNI/ffi回调验证。

## ARM64 Console与JNA链接配置

2026-11-06 P1 ARM64 Console两资源新增固定已审terminal/vcpkg源的x64宿主交叉构建，显式MSBuild ARM64并对OpenConsole/conpty输出强制ARM64 PE32+；只构建不冒称ARM加载或产品验收。Console目标与既有WinPTY x64/x86分开约束，原固定安全基线/端口源/禁二进制缓存保持。8构建控制+2安全门禁过，实际远端待。Windows ARM JNA已完成C/libffi编译但MSYS转换/DLL链接选项为路径，现仅该构建禁自动参数转换，修正待。

ARM64 Console构建使用已审相同7ff11ee terminal与fe1cde6 vcpkg固定源，只扩展明确ARM64目标和PE门禁；运行专项后续在Windows ARM托管目标单独做。JNA参数转换依据[MSYS2官方说明](https://www.msys2.org/docs/filesystem-paths/)，避免MSVC /DLL被当Unix路径；保留原始失败42158139003@e2。

## Solaris双字宽组件独立验收

2026-11-06 Solaris42158505948@543两侧原包独立验：1297固定JNA源码/侧与已审基线全等，15回执/侧、完整Git输入、libffi固定归档与生成输入、ELF及真实JNI/ffi call41 closure46/缺库拒绝过。原sunos-x86错误64位库存精确保留；新32位输出严格ELF32/machine3，未改原始证据。组件已验、尚未装配，不增加正式34/50；真正35候选启用修正025c8f9已推送，完整新轮待。持续目标已恢复active。

详见[sunos-jna-components-acceptance.json](sunos-jna-components-acceptance.json)。本阶段关闭这两项的组件构建/ABI证据缺口，正式来源门槛仍需统一候选完整产品验收。

## ARM64 JNA组件独立验收

2026-11-06 ARM64 JNA42159389661@3e71原包独立核验通过：1297固定源与基线全等、11回执/712宿主Git输入、固定libffi归档及生成输入、MSVC ARM64选项、DLL/探针ARM64 PE、实际JNI六sizeof/malloc/free/精确加载/卸载与缺库拒绝。上游ASMFN_OFF行为保留，不宣称ffi回调或完整Java验收；组件待集成，正式34/50不变。加Solaris两项/helper共四项已审待装配，下一候选目标39。

证据：[windows-arm64-jna-component-acceptance.json](windows-arm64-jna-component-acceptance.json)。ARM64 C ABI实际通过不能替代尚未执行的统一候选回归；原失败与source gate保持。

## 统一39来源候选

2026-11-06 P1统一39集成：Solaris双JNA与ARM64 JNA/helper四原包精确stager通过，新增四控制覆盖原包与载荷/报告/归档/资源scope漂移拒绝。真实39来源/11原版候选装配，4499其他条目保持，未动本机生成JAR。工作流固定三组件运行下载并要求39替换前置门禁；正式34/50不变，新完整五目标验收待。原包及先前验收备份沿用，新增装配和stager另作本地逐SHA/CRC备份。

[实际装配控制](assembly39-control.json)与四组件逐项来源收据共同限定范围；不将新候选装配当产品验收，也不替代余下11项来源任务。

## OpenBSD closure分配失败定位

2026-11-06 OpenBSD42158939107原ZIP实核exit11/空日志，对照固定C探针为closure_alloc返回NULL；先前JNI六sizeof/malloc/free与ffi call41已通过但不计组件验收。根据官方mmap条件，新增mount回执并只将自有wxneeded探针同SHA复制到/usr/local临时目录运行后清理，不改任何挂载或产品权限；补errno诊断，根因及修正待实际对照。两本地ELF/副本控制过，来源计数不变。

[失败收据](openbsd-closure-allocation-failure.json)。[OpenBSD mmap官方文档](https://man.openbsd.org/mmap.2)要求wxneeded与wxallowed同时满足；本轮只验证已有挂载上的执行位置，不重新挂载、不开放Java/产品权限。运行后是否通过由实际同一JNI/ffi探针决定，不因移动文件就宣称已修复。

## ARM64 WinPTY DLL/agent入口

2026-11-06 P1 ARM64 WinPTY两载荷新增原生Windows ARM runner/固定MSYS动作/CLANGARM64入口，固定7e59fe2与已审目录扩展，P4J b7554d6原始ARM库存对照；强制编译器目标与DLL/agent/探针ARM64 PE，实际owned agent/resize/process-list/退出/卸载/缺库拒绝待。新增固定配置1控制与原补丁3控制过；源码编译/运行未验，不增加正式34或候选39。

[MSYS2官方ARM支持](https://www.msys2.org/docs/arm64/)提供原生clangarm64工具链。既有上游Makefile的静态链接、版本生成和DLL/agent目标保持；仅生成明确ARM编译器配置，不构建MSYS适配器。任何源码移植需求须由实际编译错误及运行对照决定，本轮不提前声称原WinPTY支持ARM64。

## OpenBSD x64组件独立验收

2026-11-06 OpenBSD x64 42227334617@f587原包独立核验：1297固定源、16回执/完整Git输入、libffi生成输入、ELF/JNI六sizeof/malloc/free与ffi call41 closure46/缺库拒绝过。mount回执确认既有/usr/local wxallowed、/home不具该选项；自有wxneeded运行探针复制前后SHA等于归档探针，临时目录退出清理，无挂载或产品权限改动。组件已验未装配，正式34/50/候选39不变；x86仍OPEN。ARM64 WinPTY入口ead88a1已推送，实际结果待。

[原包验收](openbsd-jna-components-acceptance.json)仅证明所记录OpenBSD 7.9 x64 VM内的C JNI/libffi组件；需要其他位置/Java宿主时仍受实际W^X配置限制，不把探针执行位置对照宣传成产品权限修复。
