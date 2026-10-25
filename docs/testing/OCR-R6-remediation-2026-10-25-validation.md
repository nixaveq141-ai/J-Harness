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

## 完整CI第22轮与定点修正

40178732629@c40c908 的Windows/macOS均在R5SessionBoundsTest.bothSearchSnippetsUseOriginalUnicodeCoordinates失败：TokenSessionSearch拒绝非真实路径祖先，未进入浏览器。夹具一处使用root.resolve(token)，同方法raw索引及其他token夹具已用toRealPath；改这一处真实路径，不改变索引链接拒绝策略、Unicode片段断言、原702身份或生产代码。Windows本地原5项通过，新增手动session-r5三平台5项定点入口。旧轮余下Linux已取消，未执行部分不算通过，修复定点通过前不重开完整矩阵。

原Windows ZIP e85c05ed92b3、macOS ZIP796495a9aa3a，原失败/元数据保留；不会用专项替代最终同提交三侧完整原包。

## 完整CI第23轮根因与定点

40179342004@45587cf三侧未通过。Windows目录替换负例没有抛异常；原实现的Windows fileKey缺失，仅比较创建时间。构造同一创建时间的替换目录后，旧策略确定性失败（identity-old-policy.log），当前两个私有索引改用既有DirectoryIdentity.bind UUID锚并在每次边界读取锚；session原55项通过。公共工作区身份没有改用用户文件，父目录替换竞态/不合作修改仍是保留边界，不能宣称OS目录句柄保证。

Mac/Linux各32/33组778项通过，唯一Chromium/extensions在点击快照后立即isVisible失败。原失败文本稍后已包含正确SHA及完整技能正文；SkillSnapshot由异步details toggle驱动React挂载，断言早于渲染。改为等待实际可见，再同时校验目录快照SHA和正文，保持“无模型请求”及原35身份。本机Chrome/WebKit各35通过；第一次本机缺Playwright Chromium没有进入测试，随后使用既有Chrome通道，未安装/运行本机Firefox。

三份原ZIP均已下载并与GitHub摘要一致：Mac c0546f6a152c、Linux123b32535666、Windowsaba2a25dfa15。先三平台session原5项及Mac/Linux Chromium扩展35项专项，定点通过并核对原包前不重开完整CI。

## 当前外部阻塞

11e4024的目录身份专项40192469338、扩展专项40192471748于启动前被GitHub拒绝。Checks注释原文：The job was not started because recent account payments have failed or your spending limit needs to be increased. 所有5个job无执行步骤，两个run制品列表为空；未执行的测试不能写成失败断言、通过或阶段收口。

原注释121166206777、121166213876及SHA已保存在结果索引；付款失败与支出上限这两种原因没有进一步账户证据，不能只断言“免费分钟耗尽”。当前用户已收到阻塞信息。恢复条件是账户允许Actions启动，再依次完成两个原专项、同提交完整三平台和原ZIP严格审计，不合并旧轮成功项。

## 当前最终本地证据与费用控制

11e4024的r6-20261025-final6同输入19组936通过并独立verify：Java702/119、客户端42、门禁192。最终JAR4a8c54f3f255的SDK66、示例2、Windows原生7、启动器8及16环境名、Chrome/WebKit扩展各35、候选真实安装5均过；法律原件18份无差异、OSV18零发现、npm零漏洞、前端生产BOM6、SDK零外部运行依赖。原JSON、日志、截图、XML与候选ZIP在final6-frozen-components冻结并逐项摘要核对。候选ZIP046e27f3fead来自本地工作树，不冒充远端clean发布；选定70项扩展不等于813项完整矩阵。

用户提供的GitHub邮件确认：本周期3000分钟已用100%，邮件显示2026-11-01重置。是否已有实际扣费没有账单证据。仓库只读库存显示累计51次工作流（主ci.yml28次）、116份现存制品约935.33MiB；这些数值不等于账户计费分钟、存储GB-hours或金额。旧timing API返回0但官方标注停用中，不能据它宣称免费或零费用。

多轮完整CI消耗控制不足已向用户说明；当前停止新云端CI，未改动任何预算、付款设置、仓库可见性或远端历史证据。建议先确认$0预算等避免额外支出，等待邮件所示额度重置；继续远端前需用户明确确认可用额度/预算。本地已完成的验证保留，M10.5.9及M11不标完成。

规则依据：[GitHub Actions计费官方说明](https://docs.github.com/en/billing/concepts/product-billing/github-actions)。本次费用相关记录仅用于解释阻塞，没有进行付费购买或代替用户作预算决定。
