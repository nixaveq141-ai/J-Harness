# P0：基线冻结与逐项任务盘点

2026-11-04完成。规划基线为 `b134d93`，产品完整验收仍绑定 `5df2372`，JAR SHA256为 `49d4404dbe1e7b26e9842a2c765960f066f620105668cca0bb690313d1083f95`。本次只补齐[规划P0](../../requirements/remaining-gap-closure-plan-2026-11-04.md)的四项交付，五项技术缺口保持OPEN。

## 已交付

| 交付 | 内容与核验 |
| --- | --- |
| [逐文件任务账本](native-tasks.json) | 实际50项中36项未决载荷逐一绑定路径、SHA、组件、OS/CPU、官方分发证据、缺失证明、工具链现状/需求、验证方式和阻塞。JNA 21项、pty4j 15项；五批9/2/10/7/8，无重复遗漏。 |
| [环境范围矩阵](environment-scope.json) | 五个完整功能CI目标、两个最小运行容器，另列两个一次性系统包扫描宿主。分开构建、运行、JDK与捆绑原生组件，绑定运行/提交/时间/原始包与收据。 |
| [历史证据索引](historical-evidence.json) | macOS按钮、WinPTY退出、development UNKNOWN三类逐项列出精确输入、已留证据、缺失现场、下一任务与因果闭合门槛。原始失败数据只读。 |
| [最终重验差异清单](revalidation-delta.json) | 精确枚举5df至b134的32个变更文件，绑定Git字节摘要及分类；每类给出重验要求。后续提交的新增差异需在P5继续扩展，不能把本次清单当未来最终候选。 |
| [P0验收及本地备份回执](acceptance.json) | 四份JSON摘要、五份旧备份整包SHA及全部成员CRC/可读性、P0新增原始失败包备份逐成员SHA。 |

## 盘点发现与范围

五个完整目标各124份Surefire XML的JVM/OS属性一致，已从原始ZIP提取实际精确JVM构建号、发行商及OS属性，保留示例XML路径和SHA；未把工作流`java-version:21`当精确版本。完整宿主镜像版本、JDK模块与系统/静态依赖快照仍待P2。两个容器的实际image inspect配置记录了基础镜像`JAVA_VERSION`，不代表更新后执行`java -version`的实测结果。

原macOS失败ZIP已核对整包SHA及WebKit models日志SHA，但不包含JAR，故当时精确运行JAR仍未知；后续固定旧提交的重建不能补造该字节身份。原Windows development失败包内的实际JAR已按收据摘要核对。WinPTY以DLL/agent/脚本摘要及自有进程观察绑定，不虚构CI运行号。

“晚于5df的生产诊断变更”经Git核实，实际位于 `frontend/tests/development-diagnostics.mjs` 与控制脚本，属于测试夹具诊断扩充；5df之后没有Java生产源码变更。P5必须把这些诊断和后续修复纳入新的候选验证，不能声称已由5df运行覆盖。

原本地备份均未覆盖或改写。新增 `.build-cache/local-evidence-backups/2026-11-04/p0-originals-and-ledgers-v2.zip` 保存原macOS失败ZIP、原Windows development失败ZIP、四份索引和生成脚本，7成员逐SHA核验。首次盘点生成的v1本地包保留，正式验收引用v2。原WinPTY观察/脚本/日志在既有27成员备份中；旧版Mac十轮重建在既有two-gap备份中，均已重读核验。备份仍只在本地，缓存、原始包和运行日志不入Git。

## 五项后续入口

| 缺口 | 下一工作包 | 闭合证据 |
| --- | --- | --- |
| NATIVE-39（剩36） | P1先逐资源核上游证明和目标工具链，再构建替换 | 来源链、实际候选严格来源门禁及适用加载/回归；不能删除载荷凑数 |
| OS-CPE | P2补完整版本清单、映射、21条ESM逐项处理 | 固定范围的适用性和修补后证据；未知映射不按安全处理 |
| WINPTY-LOCAL | P3只跟踪自有进程，捕获终止来源 | 同条件因果链、修复前后对照和交互/清理回归 |
| MAC-HISTORY | P4-M补联合诊断并按代码路径受控复现 | 足够关联原阶段的因果复现；未复现不闭合 |
| DEVELOPMENT-UNKNOWN | P4-D固定原JAR及审批后阶段关联错误/清理时序 | 因果修复对照及UNKNOWN禁止自动重放断言 |

额外实体机器验收跳过，昂贵性能/模型横测后置。当前可独立推进P1首批工具链盘点、P2环境清单及P3/P4诊断准备；原生漏洞最终判断依赖P1实际载荷。没有进行新的产品测试、远端CI或付费模型请求。

复核入口：`python scripts/verify-gap-p0.py`。需要本地原始备份时加 `--local-evidence`，复核六份备份的SHA和可读性。脚本成功只表示P0清单一致，不表示五项技术缺口已修复。
