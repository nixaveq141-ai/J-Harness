# 扩展、技能与 MCP 资源

在工作台“设置 → 插件与扩展”展开 MCP 服务、Skills、技能目录发现或只读子任务，添加条目、填写字段并保存。高级 JSON 可继续使用，两个视图编辑同一份草稿。保存不启动服务或脚本，不发送模型请求；运行中或有 UNKNOWN 待核实事实时不能修改。

MCP 支持 HTTP/SSE 和本机 stdio。stdio 的启动参数每行一个，第一行为可执行文件，不将整串命令自动改成 Shell。HTTP 凭据只填写环境变量名，留空表示匿名服务；不要粘贴密钥。工具影响声明由操作者负责，未声明工具保守按系统影响审批。

Skills 用于显式配置工作区相对目录、资源白名单和脚本白名单。目录须含 SKILL.md；脚本需要选定 SH、PowerShell 或 CMD 模式及超时，并继续经过原有审批。技能文本不能授权新脚本或改变权限。

“技能目录发现”允许添加项目相对技能根或用户绝对技能根。只扫描根的直属技能文件夹；每个文件夹放 SKILL.md，例如：

```markdown
---
name: guide
description: Read the project conventions before making changes
---
Use the registered file tools to inspect README.md.
```

name/description只支持上述单行frontmatter，可用引号，不支持完整YAML。无frontmatter时以目录名作为技能名。不要与已有技能或help/quit/settings等命令重名。扫描根是显式授权的文本来源；不自动允许其中的资源或脚本。

新建任务或继续会话时，输入 `/gu` 可看到匹配候选，也可点击“技能目录”浏览、查看快照和重新扫描。点击“使用 /guide”只填入草稿，仍需发送；模型通过 `skill_guide__load` 读取装配时冻结的文本。刷新不恢复未发送草稿。读取目录并不更新正在运行的快照。

MCP资源工具以服务ID命名，例如 `mcp_docs__resources`、`mcp_docs__resource_templates` 和 `mcp_docs__read_resource`。仅支持已发现的文本资源及单个简单变量模板，不支持blob、图片、OAuth或prompt模板。所有返回都作为不可信工具内容处理。

在会话输入区展开“实际装配工具”并读取清单，可核对最近一次成功装配的工具、说明与影响级别。已保存配置不等于连接成功；装配失败时查看执行诊断。旧会话没有清单记录时会明确显示未知，不根据当前设置虚构历史。

确定缺失的MCP环境凭据会在任务受理前提示；请修正变量名或恢复宿主环境后重启。实际服务断开或SDK装配异常仍可能保留UNKNOWN，需要遵循执行诊断中的离线核实流程；不能直接点保存或重试来清除不确定性。
