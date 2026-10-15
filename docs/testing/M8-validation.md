# M8 验收记录

日期：2026-10-15。M8.1已完成本地实现和验收；M8整阶段进行中。

| 范围 | 验收内容 | 状态 |
|---|---|---|
| FR-07.3优先级 | 默认/.gitignore/用户层、有序覆盖、父目录剪枝、配置关闭 | 通过 |
| 功能一致性 | list/search/显式grep一致，read_file授权保持 | 通过 |
| AC-05 | !规则不开放metadata/外部路径/链接，中文空格路径 | 通过 |
| 资源边界 | 规则数量/长度/总量、扫描期限、非法规则诊断 | 通过 |
| 装配 | 经典CLI、命名配置、SDK环境、子视图、worker握手/拒绝降级 | 通过 |
| AC-16 | 最终JAR本地/真实Docker使用同一规则与返回路径 | 通过 |
| 回归 | 1000条目性能、版本保护、M0～M7全量测试 | 通过 |

M8.2/M8.3未开始；远端CI/macOS仍无新增证据。本轮使用本地服务/夹具，不调用付费模型。

## 实际结果

最终 Windows 命令 `.\mvnw.cmd -B clean verify`：BUILD SUCCESS，269项测试，0失败、0错误、0跳过，包含格式检查、-Xlint:all -Werror与最终CLI打包。结构化证据为 [M8-results.json](M8-results.json)，slice=M8.1；原始日志 .build-cache/M8-verify.log。M7的260项和历史JSON保留，不用旧结果代表当前源码。

新增9项：M8SearchFilterTest四项（优先级、锚定/目录/中文空格、配置资源边界、metadata/外部路径/真实junction拒绝），M8FilterConfigTest两项（命名配置整对象覆盖与启动前校验），M8FilterIntegrationTest三项（经典/生态CLI实际HTTP模型替身与真实文件一致、真实worker进程确认及旧worker拒绝清理、SDK非默认配置冻结）。原LocalWorkspaceTest的1000条目性能断言及全部历史测试保持。

专项首先发现关闭默认排除会让遍历进入metadata目录，已修复为独立不可覆盖的保护，并保留原负面断言；另补齐经典CLI config.selected的实际过滤快照。首次全量在既有进程用例中遭遇Windows临时目录占用，测试主体断言通过但JUnit清理失败；日志/报告保存在 .build-cache/M8-verify-attempt1.log 和 M8-process-attempt1.xml。未改动进程实现、未跳过或削弱断言，复验全量通过。编译中的夹具调用签名/类型错误也先修正再计通过。

## 真实 Docker 对照

M8FilterDockerProbe使用最终打包JAR，分别在本地和实际Docker执行两种配置：默认与.gitignore再叠加用户否定/排除；关闭前两层再使用根锚定规则。核对list/search/显式grep相同、中文空格路径、子视图一致、过滤不改变直接读取授权、metadata与穿越拒绝、非默认descriptor及容器清理。结果为 [M8-filter-docker-results.json](M8-filter-docker-results.json)。

另外使用既有M7DockerProbe对同一最终JAR进行默认环境回归，验证原文件版本、编辑/Shell/cwd、本地/容器SDK、每侧4个模型请求重建及容器清理；结果独立保存于 [M8-environment-regression.json](M8-environment-regression.json)。其夹具内部milestone=M7是旧夹具标识，文件及JAR SHA表明这是M8.1最终制品的回归，不覆盖或替代M7历史证据。

两份主动实机结果均SUCCESS、cleanupVerified=true、JAR SHA一致；固定镜像及证据目录在JSON内。夹具调用如下，DOCKER和IMAGE须由宿主明确准备：

```powershell
& "$env:JAVA_HOME/bin/java.exe" -cp 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar;harness-testkit/target/test-classes;harness-testkit/target/classes' io.jharness.testkit.M8FilterDockerProbe DOCKER IMAGE_AT_SHA256 '.build-cache/m8-filter-probe' 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar' 'docs/testing/M8-filter-docker-results.json'
.\scripts\collect-test-evidence.ps1 -Milestone M8 -Slice M8.1
```

本轮没有付费模型请求，没有重新跑Linux全量、macOS或远端CI；容器实际过滤对照不等于全量Linux269项。API/SDK仍1.7。规则语法是明确子集，非完整Git wildmatch，不支持嵌套.gitignore；具体配置见 ../examples/m8-search-filter.md。

下一步为M8.2：先定义可替换只读工具调度的并发、顺序聚合、写屏障、取消/失败和持久单写者契约，再实施。当前默认串行不变，M8.3限流/重试未开始。
