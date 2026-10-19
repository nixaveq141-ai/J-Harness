# J-Harness 本地验收候选包

这是0.1.0-SNAPSHOT候选包，不表示已正式发布或完成M10.5.9跨平台准入。需要Java21；TypeScript源码客户端需要支持原生TS stripping的Node，Python客户端使用标准库。当前Java API/SDK1.14、独立stdio协议1.0，JSONL/schema1、Web目录4。

解压后运行 `jharness.cmd --version`（Windows）或 `sh jharness --version`（POSIX）；配置JAVA_HOME为Java21目录。`doctor`只检查本机条件，不发送模型请求。模型凭据使用环境变量或已有系统凭据，不能写进命令参数或明文配置。选择合适权限，逐次批准写入/命令；完成依据显式读取/文件版本/验证命令证据，不依据模型总结。

包内 `lib/jharness.jar` 包含同源Web UI。CLI `serve` 使用显式配置的本机环境与loopback认证，不提供公网/多用户服务。项目根目录的便捷启动器不属于这个候选ZIP；从源码工作区启动可使用原 `web-ui.cmd`。配置/使用与保留限制以仓库README和阶段文档为准。

`sdk/`包含TypeScript/Python源码与只访问loopback的零费用示例，协议和使用见 `sdk/README.md`。可设置JAVA_HOME、JHARNESS_SDK_JAR为本包 `lib/jharness.jar` 绝对路径、JHARNESS_SDK_WORKSPACE为专用目录，在其中放入README.md，再按示例说明运行本机provider及客户端。相同示例requestId命中原受理记录；超时/断线先核对受理，UNKNOWN禁止自动重试，回答问题不等于工具审批。

manifest.json列出文件摘要；Maven、前端与SDK分别附bom.json、frontend-bom.json、sdk-bom.json，Maven扫描附osv-results.json。SDK没有第三方运行依赖，采用根LICENSE中的Apache-2.0；UI移植及其他依赖版权/许可证原样保存在JAR和third-party中。候选安装冒烟仅用本机模型替身，不产生付费请求。
