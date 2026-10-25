# R6 当前验证记录

2026-10-25，状态：最终本地验证通过，当前专用分支完整 CI 待完成，M10.5.9/M11尚未准入。

原186条范围、实施/澄清归属和非逐项故障注入边界见[台账](../development/OCR-R6-scope-2026-10-25.json)。最终冻结输入以 scripts/evidence_run.py 的完整快照为准，禁止把旧JAR或其他轮次的成功组合为同轮通过。

## 保留的失败与修正

- final：M8FilterDockerProbe 测试编译引用变量早于声明；已修正声明位置。
- final2：Wrapper旧日志文字断言失败；改为核对固定版本、完成标记及实际ZIP摘要，原构建失败→修复→成功行为断言保留。定点M3RuntimeIntegrationTest原9项通过。
- final3：Java702项通过，随后CycloneDX因离线模式拒绝运行。移除R6入口的离线参数，完整重跑final4；未将前一轮标作整组成功。

原始失败/尝试均留在忽略的 .build-cache/R6-2026-10-25/，未覆盖成成功。历史第21轮Windows原生失败只证明服务不可用，未保留的具体原因不补猜。

## 收尾条件

最终本地精确用例与退出码/日志/源输入/当前JAR核对通过后，专用分支先Windows原生7项，再同提交Windows/Linux/macOS各Java702/119、客户端42、SDK66、原生7、示例2及三引擎33组813项；Windows另有启动器8项。候选安装、供应链和三侧原ZIP独立核对全部通过后才更新M10.5.9状态。未启动本机Firefox、裸npx或付费模型，没有main合并或Release。

## 当前最终JAR专项

JAR SHA256：`21f93ac6209c4a3a1226f020ded011b5b4197e5db400c887d45ec6f076de2bb3`。SDK66、文档示例2、Windows原生7和启动器8通过，原结果与摘要见[结果索引](OCR-R6-remediation-2026-10-25-results.json)。原生检查包含跨子进程写/旋转/删除/清理；启动器仅替换自有子PowerShell的浏览器调用，没有打开用户浏览器或改变剪贴板。Docker Desktop未就绪且返回无法启动，主动Docker探针未执行，不计通过。

供应链当前实测：Maven18坐标OSV零发现，前端6生产依赖BOM/npm零漏洞，SDK零外部运行依赖，秘密模式扫描935文件零发现。候选ZIP64f35cdc22ff的真实安装5项通过；本地包来自尚未提交工作树，未当作远端干净候选。

- final4：Java702/119与SBOM通过，后续r123r-evidence两项仍按旧异常传播和缺失Java夹具编写，未进入预期启动故障。更新为独立假Java文件+mock启动OSError，断言确实调用一次、SystemExit=1、failureType=OSError、旧PASS撤销及输出父目录先创建；原6身份全部定点通过。R6八项定点通过。最终final5先门禁后Java，避免在离线失败前重复全量。诊断工作流改手动，只有新增Windows原生gate按自身文件首次推送触发，随后完整CI仍手动。

## 远端授权状态

实现提交73cb125。自动审批两次拒绝推送，命令均未执行。已从原聊天的用户原文找到专用分支/Actions授权，但审批不接受跨聊天工具证据；当前对话已请求一次明确确认。当前未推送、未启动新远端CI，不能标记M10.5.9完成。

## 最终本地与定点收尾

73cb125 的 final5 冻结回执19组936项全部通过，独立verify通过；Java702/119、客户端42、17门禁192。原XML另存java-original-xml.zip，保留原始摘要，不用后续定点XML覆盖声称同次全量。

最后两条可执行测试建议补齐：M7ClosureTest四个配置负例均断言HarnessException及INVALID_ECOSYSTEM_CONFIG，原7项定点通过；首版错误地写成通用INVALID_CONFIG的失败记录closing-config.log保留。启动器原8身份增加16个tokenEnv受保护值/大小写变体，实机通过。原702个身份全部保留，未因断言补强再跑一次本地全量；最新源码的完整契约由最终CI重新验证。

当前JAR3904ed4a895e与Windows原生专项实际JAR完全一致，SDK66、示例2、本机原生7及扩展启动器8重新通过。两处补强只改测试，不改运行时Java/POM/UI；台账更正为148实施/验证补强、18既有保留、20契约澄清。

原始用户历史授权经审批拒绝后，当前对话已再次明确授权推送与CI，73cb125已推送。Windows专项40178201805真实7项通过，原ZIP473f697d9a8e与GitHub摘要一致；补强提交后才启动最终完整CI，专项不替代收尾。
