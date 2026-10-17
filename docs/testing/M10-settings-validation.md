# M10 设置增量验收

2026-10-17后续入口改为无需输入本机凭据，设置与凭据库回归仍通过；当前Java314项、客户端9项、工作台28项、设置21项、入口8项，见[M10直接入口验收](M10-auto-validation.md)。本文313项结果保留为此前设置版本的历史。

2026-10-16：本地验收通过。用户要求模型/API URL/API Key可在页面管理，插件有实际入口，并明确选择系统凭据库持久保存Key。使用说明见[web-ui-settings.md](../examples/web-ui-settings.md)，契约见[M10-settings.md](../development/M10-settings.md)。

| 验收范围 | 结果与证据 |
|---|---|
| Java全量 | `mvnw.cmd -B clean verify`：313项，失败/错误/跳过均0；267份Java/POM与完整测试集合见M10-settings-results.json |
| 新增后端专项 | WorkbenchSettingsTest 5项：凭据重启与脱敏、无效配置不写入、失败保持旧值/轮换清理、独立价格与插件配置、真实Windows CredWrite/Read/Delete；M10WebTest新增鉴权/跨源/UNKNOWN占用保护1项 |
| 前端客户端 | 8项通过，无失败/跳过；哈希见总证据 |
| 设置浏览器 | 实际Chrome 155.0.8059.82 / Java CLI，21项通过，M10-settings-browser-results.json |
| 原工作台回归 | 相同最终JAR，实际Chrome28项通过，M10-settings-workbench-regression.json |
| 英文入口回归 | 相同最终JAR，真实Windows启动/退出/鉴权/重复启动8项通过，M10-settings-launcher-results.json；自动化禁用打开用户浏览器/改剪贴板 |
| 视觉与审查 | 桌面/深浅色/390px手机截图；独立只读审查发现的草稿导航丢失已修复并回归，最终选中标签/滑块/正文一致；机械detector结果为[]（其后仅补服务专用参数复选框，未重新运行detector） |

浏览器实际完成新增/编辑/删除连接、默认选择、URL/模型/Key保存，重启读回系统凭据；真实SDK请求抵达新URL，模型名和Bearer Key与已保存值一致。新连接未继承DeepSeek专用thinking参数。Planning及Skills工具进入模型请求，并经原SDK管线实际执行，产生成功事件；不是只展示配置。保存不发送模型请求，费用/限额与原权限规则保留。

反例覆盖：无鉴权/跨源拒绝、缺Key在任务受理前拒绝、旧revision冲突、带userinfo的URL拒绝、运行任务阻止修改、UNKNOWN占用禁止修改、无效价格/配置不改文件或凭据、凭据写入失败保持旧设置。密钥不出现在响应、配置文件、浏览器存储、事件或宿主输出；测试使用随机合成Key，退出清理其托管系统凭据。付费请求0。

最终JAR及包内UI、所有测试与截图哈希由`scripts/collect-m10-settings-evidence.py`核对，总证据为M10-settings-results.json。Java全量日志`.build-cache/M10-settings-verify.log`，设置/回归/启动器日志和截图在`.build-cache/M10-settings-*`，不提交运行文件或二进制。

首轮全量因旧服务占用JAR导致clean失败，用户关闭旧入口后重新完整验证成功；该首次失败未保存独立原始日志，不冒称其为通过。设置浏览器首轮产品断言通过后，夹具finally重复发响应触发ERR_HTTP_HEADERS_SENT，修复只影响夹具并重跑全部设置检查；原日志/JSON哈希见M10-settings-first-attempt.json。独立审查所报未保存草稿问题增加父级离开保护和beforeunload，浏览器回归确认导航禁用。

仅Windows11/Java21/Chrome本地证据，Windows凭据库持久化已实测；其他OS使用环境变量，系统凭据库适配未实现。CI新增Windows设置门禁但未远端执行，M9和基础M10证据保留历史。未推送、发布、测试付费模型、实施Desktop或插件市场/任意JAR安装/热加载。配置文件和系统凭据库不是跨存储事务，异常中断孤立项及人工清理边界见使用说明。
