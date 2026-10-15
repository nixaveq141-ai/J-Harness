# M8.1 检索过滤配置

[m8-filter-config.json](m8-filter-config.json) 为配置示例，模型地址/名称是占位值，需要使用模型时由宿主填写并设置对应key环境变量。配置也可以放入现有M7生态配置的顶层，不放进ecosystem.environment。

```json
{
  "searchFilter": {
    "useDefaultExcludes": true,
    "useGitignore": true,
    "rules": ["cache/", "!target/", "target/*", "!target/report.txt"]
  }
}
```

默认先排除任意层级target/node_modules，再应用工作区根.gitignore，最后按顺序应用rules。示例重新包含target目录，排除其内容，最后仅保留target/report.txt；必须先重新包含父目录，否则对子文件的否定规则不会跨过已剪枝的父目录。

useDefaultExcludes=false可以关闭target/node_modules默认层；useGitignore=false可以忽略根.gitignore层。两者都不能开放.git/.jharness、链接/junction或工作区外访问。隐藏仅影响目录列举、文件搜索与显式路径grep，仍允许按原授权直接read_file，因此不能用过滤替代秘密文件隔离或Shell权限。

支持*、?、**、**/、!、目录末尾/和相对工作区的根锚定/。无内部斜杠的模式匹配任意层级名称，其他模式相对根；规则大小写敏感，默认target/node_modules名称保持大小写不敏感。空行和以#开头的注释忽略。不是完整Git wildmatch：不支持嵌套.gitignore、反斜杠转义、字符组或首尾空白的有效规则；不支持或超量时明确报错，不静默省略后继续搜索。

每个规则列表最多128条、每条256字符、有效输入总计16384字符。根.gitignore的有效规则独立受同样限制。非法配置返回INVALID_SEARCH_FILTER，数量/长度/总量超限为SEARCH_FILTER_LIMIT；默认配置缺省可继续使用。

命名Profile/Preset的settings可以配置searchFilter，按现有优先级整对象替换，不逐项合并；省略子字段时恢复该字段默认值。经典CLI记录searchFilter快照，生态入口记录解析配置；过滤配置不能由模型调用工具改写。

SDK宿主使用`new LocalWorkspace(root, filter)`，或LocalExecutionEnvironment/DockerExecutionEnvironment新增的最后一个WorkspaceSearchFilter参数。旧构造仍使用默认配置。子任务视图继承配置但保持独立版本观察缓存。非默认过滤进入环境descriptor，SDK续跑修改它会ENVIRONMENT_MISMATCH，应建立新会话；根.gitignore内容则在每次检索时读取。

Docker需使用包含M8.1 worker的当前打包JAR；非默认配置先握手并检查配置回显，旧worker明确ENVIRONMENT_FILTER_MISMATCH并清理容器，不回退本地。
