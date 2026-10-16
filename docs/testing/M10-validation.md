# M10 Web 工作台验收记录

2026-10-16：**M10约定范围已完成本地验收**。用户确认沿用DeepSeek Harness视觉/UI/鼠标交互；原参考目录严格只读。契约见[M10-contracts](../development/M10-contracts.md)，原始调研保留为实施前历史。

## 交付与结果

| 切片/要求 | 实际结果 | 证据 |
|---|---|---|
| 连接、任务创建/列表/详情 | 同源Java托管、Bearer仅页面内存、可信profile；重复点击只一次POST，响应不明用固定requestId核对 | M10WebTest、客户端8项、浏览器记录 |
| 时间线与有限SSE | fetch携带Authorization、eventId去重、有限GET重连；断线/刷新不重提，未知游标暂停，4000条显示上限 | 客户端截断/错ID反例、实际浏览器离线/刷新/游标注入 |
| 审批/拒绝/取消/UNKNOWN | 实际SDK文件写入先ASK，精确approvalId/callId关联；错关联409、拒绝/过期不进入操作；取消与未知结果分开 | 实际Java/SDK/工作区；无在线解除UNKNOWN入口 |
| Diff/报告/产物/会话查询 | 只展示记录的git_diff；缺失明确显示；下载事实报告、产物引用不自动读取文件；全文脱敏事件有界查询 | 实际Git夹具、报告来源/成功/拒绝事实、查询分页反例 |
| 原视觉/交互 | 21份原组件/主题/布局/许可原字节保留；hover/pressed、侧栏键盘resize、明暗主题、窄屏、tabs键盘/滑块对齐及减少动态实测 | frontend/upstream.json、浏览器记录/截图SHA |
| 安全边界 | 精确Host/同源Origin；拒绝cross-site/外源/重复Origin；静态清单/CSP，不读任意文件；HTML为文本，token不入URL/存储/报告，配置秘密被脱敏 | M10WebTest、浏览器与客户端负例 |
| 原参考只读 | HEAD/tracked工作区及25个前置/21个移植来源SHA未变 | M10-evidence-audit-results.json |
| 构建/分发/许可 | npm ci、TS严格检查、Vite构建；UI嵌入CLI JAR；完整Apache/MIT和浏览器依赖许可随包 | M10-results.json、package-ui.mjs |
| 依赖与证据 | npm完整审计0发现，生产闭包6包CycloneDX含依赖/peer边；8项证据/生成器负例拒绝 | M10-npm-audit/M10-frontend-sbom/M10-evidence-audit-results.json |

Java clean verify **307项，0失败/错误/跳过**，263个Java/POM；客户端**8项**；实际Chrome**28项检查**均通过。三组计数独立，不把浏览器检查数计入JUnit。Chrome155.0.8059.82，Windows11 AMD64/Java21.0.12.1；夹具9次SDK打开、14次FakeLlm调用、0付费请求。见[M10-results.json](M10-results.json)、[M10-browser-results.json](M10-browser-results.json)。

## 失败、修复与证据

- 首次Java全量遇既有LocalProcessRunnerTest的Windows临时目录占用；原断言未修改，专项原样复验及后续完整307项通过。摘要与XML/日志SHA保留M10-first-attempt.json，原始文件在.build-cache。
- 首轮浏览器发现原生fetch作为Api对象方法调用的接收者错误，改用独立函数调用，经实际连接复验。Node单测不代替浏览器。
- 独立完成审查发现查询期间改词会混用旧响应/游标，改为查询中锁定输入并以延迟响应回归。其余审查未留下有证据阻断项。
- 测试曾因连接状态含子按钮而定位过严，以及取消旧请求时一次游标错误注入的不确定时机失败；改用实际容器定位及持续错误注入，产品暂停断言保留。最终窄屏等待原180ms过渡并验证滑块/选中tab bounds误差小于2px。
- npm发现tsx传递esbuild低危公告，限定tsx使用0.28.1修复版。全局覆盖破坏Vite的失败保留；现保留Vite自己的受支持版本，干净安装、构建、测试及审计通过。
- 当前npm原生sbom（包括package-lock-only）遗漏必需生产依赖js-tokens。未降低完整性检查，新增frontend-sbom.py按锁文件逐边解析，产出完整6包；缺失依赖边或BOM组件均拒绝。
- 来源清单规范化LF时，校验器拒绝旧CRLF摘要；未手改通过收据，重新运行同一最终制品浏览器采集后通过。原始移植文件保留原字节。

机械UI检测结果[]，不替代性能/无障碍认证。截图在.build-cache/M10-{connection,welcome,approval,report,diff,unknown,dark,mobile}.png，SHA已归档；日志/XML/构建包/运行数据不提交Git。

## 复现与限制

frontend中npm ci、npm test、npm run build；根目录Maven clean verify；安装Playwright Chromium后npm --prefix frontend run test:browser。Windows可设JHARNESS_BROWSER_CHANNEL=chrome。测试自建并关闭独立FakeLlm宿主/浏览器，不使用生产模型。

执行scripts/frontend-sbom.py .build-cache/M10-frontend-sbom.json，归档npm audit JSON后运行scripts/collect-m10-evidence.py。工具交叉核对JUnit/前端输入/原始字节/最终JAR/包内UI/许可/审计/BOM/截图。M10-results的baseCommit+dirty说明采集尚未提交，逐文件SHA绑定实际源码，不冒称旧提交运行过新代码。

产品运行见[前端说明](../../frontend/README.md)。Node仅用于源码构建；单独Maven不会下载npm依赖，未构建UI时根入口503、API仍可用。CI已接入前端/浏览器/扫描，但本次没有M10推送或远端矩阵执行证据。

仅本机Windows11/Chrome实际验收，不宣称Firefox/Safari、Linux/macOS新UI认证；M9是旧源码三平台历史。未新增Docker主动验收、Desktop、公网/TLS、多用户或付费模型评测。组件复用不等于上游完整功能/插件协议兼容；没有后端的Goal/Schedule/Workflow/语音/桌面控制入口未搬入。M11/M12等待用户指令。
