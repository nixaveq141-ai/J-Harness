# Windows Web UI 入口

双击项目根目录的`web-ui.cmd`。入口文件名使用英文；底层脚本为`scripts/open-workbench.ps1`。

1. 自动查找JAVA_HOME或PATH中的Java21，启动已有打包JAR。
2. 优先使用工作区的`.jharness/config.json`；没有时使用仓库`docs/examples/deepseek-config.json`。
3. 沿用配置指定的Web token环境变量；未设置时生成随机token，仅交给子进程，不写配置/文件/URL，不打印凭据。
4. 确认实际UI与带鉴权API均可访问后，复制token到剪贴板并打开默认浏览器。
5. 页面中Ctrl+V粘贴连接。保留启动窗口；输入`:quit`关闭服务。

浏览器仍需粘贴连接，不通过URL传递秘密或自动绕过鉴权。启动器本身不收集模型Key；连接后可通过左侧「设置」把模型Key保存到Windows凭据管理器，重启后继续使用，见[设置说明](web-ui-settings.md)。仍可使用所选配置的apiKeyEnv环境变量；若Key只在某个PowerShell会话内设置，从该会话运行`./web-ui.cmd`才能继承，资源管理器双击使用其自己的环境。仅打开/连接页面、读取或保存设置不会调用模型。

自定义工作区/配置，在PowerShell执行：

```powershell
.\web-ui.cmd -Workspace "D:\path with spaces\my-project" -Config "D:\configs\model.json"
```

不改变当前目录，不重新安装/构建，不覆盖用户配置，不停止已有服务。同一工作区已有服务时本次启动失败并提示，请使用原窗口的服务。新token不会替换已有服务token。

源码尚未构建时按frontend/README.md先构建前端和Java；缺JAR、缺Java、UI未打包或配置/目录占用会留下提示。启动脚本只在进程内使用PowerShell执行策略参数，不修改系统策略。可用`-JavaHome`明确指定JDK21；`-NoBrowser`/`-NoClipboard`用于无需对应桌面副作用的启动。

退出优先用`:quit`；脚本只清理自己启动的Java进程，超时会明确提示不确定状态。异常退出后的UNKNOWN检查仍遵循原CLI流程，不自动重放。该入口是M10便捷启动，不代表M11 Desktop、后台常驻服务或操作系统进程隔离保证。
