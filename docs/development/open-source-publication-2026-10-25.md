# J-Harness 开源公开记录

当前状态：仓库公开，77e8855已完成三平台验收，M11已准入未实施；最新状态见[统一索引](current-state.md)。以下是按原日期保留的公开操作、失败与接续历史，旧“进行中/待验收”不是当前状态。

## 开源过程历史记录

2026-10-25，用户明确要求将项目开源。现有仓库 https://github.com/nixaveq141-ai/J-Harness 已由private改为public，官方API和无认证读取均确认；默认main、分支内容、Release和预算设置未改。

发布前核查：根Apache-2.0许可证及第三方法律材料保留；获取所有远端分支后扫描4879个可达Git对象，其中2392个历史文本对象未检出常见密钥模式、敏感文件路径或超限对象。当前936份文本模式检查零发现；10个远端工作流版本没有public事件触发器。这是有限模式检查，不宣称穷尽所有敏感信息。用户原未提交扫描段及background草稿未纳入Git。

本次公开范围为现有远端代码、分支/Git历史与GitHub Actions历史；本地忽略缓存和未跟踪草稿不因可见性修改上传。最新运行时代码在codex/m1059-full-validation分支，提交11e4024。

公开后原私有分钟额度阻塞已解除：session专项40195176899三平台各5项、浏览器专项40195179536的Mac/Linux Chromium扩展各35项通过；5份原ZIP摘要、精确XML/JSON身份、日志和截图已独立核对。随后只启动一次同提交完整三平台验收；在严格原包核对前，M10.5.9/M11仍不标完成。

原始元数据、扫描和专项证据在忽略目录.build-cache/publication-2026-10-25保留。依据：[GitHub可见性后果](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/setting-repository-visibility)、[标准公开runner规则](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)。

公开后首轮完整运行40195474698@11e4024：Linux成功，macOS/Windows各32/33浏览器组，分别WebKit models禁用按钮超时和history最后异常/泄漏联合断言失败，原包保留。未宣称已定位根因。

保留原断言补充固定诊断后，f3d7305的macOS WebKit models专项40197604446共22项和Windows WebKit history专项40197608570共19项通过；两份原ZIP摘要、检查身份与截图已核对，原故障未复现。随后仅运行一次同提交完整验收：[40197868436](https://github.com/nixaveq141-ai/J-Harness/actions/runs/40197868436)，当前进行中。

2026-10-26最终收尾：仓库已公开；77e8855的完整CI40284862454三平台全部通过，三份原ZIP/119套件精确XML、702 Java、42客户端、33组813浏览器、66 SDK、7原生、2示例、命令回执/JAR/UI/候选ZIP及安装门禁已独立核对，严格同commit/runId/source/UI，M10.5.9完成、M11准入通过但未实施。R6按148实施/补强、18保留、20澄清的原186采纳范围收尾，不等于186个独立动态缺陷；R4/R5后续及Docker/文件系统等限制保留。Windows停机带profile轮询分类及证据CLI入口已按精确根因修正；较早macOS模型按钮失败未在后续定点和最终CI复现，仍不补猜原因。完整证据见docs/testing/M10.5.9-public-final-2026-10-26-results.json；以下旧状态均为对应日期/轮次的历史快照，不代表当前阻塞。
