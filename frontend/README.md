# J-Harness Web 工作台

连接后点击左侧「设置」管理模型连接及插件扩展，见[使用说明](../docs/examples/web-ui-settings.md)。Windows API Key使用当前用户Credential Manager持久保存，不在浏览器保存/回显。非秘密设置独立保存到工作区元数据；设置只影响后续新任务，运行/UNKNOWN占用时不能修改。

Windows新增`npm run test:settings`：使用实际CLI JAR、系统凭据库、Chrome/Playwright与loopback模型替身验证模型/插件设置、重启、脱敏和草稿保护，最终清理合成凭据；不使用付费服务。与原浏览器测试一样，需要先build并完成Maven打包，已安装Chrome时设置JHARNESS_BROWSER_CHANNEL=chrome。当前验收见[M10设置验收](../docs/testing/M10-settings-validation.md)。

React 18 / TypeScript / Vite。视觉与基础控件沿用用户指定的 DeepSeek Harness，来源、原始 SHA 和修改标志见 upstream.json，原始 MIT 见 LICENSE-DeepSeek-Harness.txt。参考项目目录不参与安装、构建或运行。

## 构建与启动

Windows已构建后的便捷入口：双击仓库根目录`web-ui.cmd`，自动打开浏览器并直接进入工作台；不显示凭据输入框、不改剪贴板。无需手动生成token或复制端口。说明与自定义工作区参数见docs/examples/web-ui-launcher.md。

需要 Node 22.20.1+ 或24、npm、JDK21；先在本目录执行 `npm ci`、`npm test`、`npm run build`，再在仓库根目录执行 `./mvnw -B clean verify`（Windows使用mvnw.cmd）。Maven将dist内容嵌入harness-web和CLI JAR；运行二进制不需要Node。

按docs/examples/m7-cli.md配置模型/工具/工作区；设置JHARNESS_WEB_TOKEN后运行 `jharness serve`（Windows使用jharness.cmd）。在浏览器打开输出的 `http://127.0.0.1:端口/`，页面通过同源自动会话连接；刷新自动重连，无需输入Web token。会话只驻留页面内存，不进入URL或Web Storage。

标准serve仍保持可信宿主配置、权限与审批。任务目标不能更改模型地址、工作区或工具白名单。页面没有自动重试任务和在线解除UNKNOWN的入口；取消是请求，不代表撤销已发生操作。未记录Diff时明确展示缺失；事实报告/产物引用来自日志，不自动读取文件或执行Git。

## 开发与验证

不启动独立Vite服务，构建后通过同源Java服务验证；避免跨源代理绕过Origin边界。修改前端后重新build和Maven打包。资源未构建时根路径返回503说明，业务API仍可用。

`npm run test:browser` 需要先编译harness-testkit测试类并打包CLI JAR，且Playwright Chromium已安装（`npx playwright install chromium`）。也可设置JHARNESS_BROWSER_CHANNEL=chrome使用已安装Chrome。该脚本自行启动/关闭专用Java FakeLlm宿主与独立浏览器，不使用付费API；临时文件和截图保存在.build-cache。适用于源码验收，不是产品内置模拟模式。

构建时校验原始移植文件哈希，并收集实际浏览器运行依赖React/ReactDOM/scheduler/clsx的许可，供/ui-license.txt读取。npm锁文件固定传递依赖；tsx的esbuild限定为0.28.1修复公告，Vite保留自己支持的版本，不采用全局覆盖。
