# J-Harness stdio SDK 1.0

本目录交付 TypeScript 源码包和零第三方依赖的 Python 模块。协议版本为1.0，独立于Java API/SDK版本。当前本机验证使用Node24、Python3.12、JDK21，不承诺Cordis或其他SDK兼容。

客户端通过 `java -jar JAR --workspace PATH --config JSON sdk-stdio` 启动并拥有一个子进程。密钥只从环境或已有系统凭据读取；不要放在argv、目标文本或配置明文中。进程内部复用本机工作台/Java SDK服务，loopback连接凭据仅内存保存，stdout只有UTF-8 JSON-RPC帧。

`openSession/open_session` 返回不执行的草稿句柄；`sendInput/send_input` 首次受理后返回真实sessionId。已有会话可通过sessionId重新打开。句柄只存在于当前进程，最多32个；持久事实与幂等记录不随句柄消失。列举、事件、提问/回答、工具审批、取消、结果/报告、分页产物及内容检索都有对应方法，也可使用严格的 `call(method, params)`。

每条输入请保留requestId。请求超时、断线或进程退出时不会自动重发；先通过 `request.get` 核对首次受理，或 `input.get` 核对后续消息。超时意味着受理可能未知，不等于取消。问题回答不批准工具；审批必须携带服务返回的精确approvalId/callId。UNKNOWN禁止自动续跑和在线清除，继续使用既有离线核实流程。

客户端最多32个待决请求，单请求64KiB、单响应512KiB，默认15秒超时、可选0.1～60秒；服务器单请求10秒边界。超时后迟到回复只匹配其原ID并丢弃，不污染新请求。stderr被排空但不保留/回显。关闭先请求shutdown，再有界等待；必要时只终止本实例拥有的子进程。关闭可能留下UNKNOWN，不能解释为所有远端操作成功或已撤销。

## 可运行的零费用示例

准备专用目录，放入README.md。先在一个终端运行 `python sdk/examples/loopback_provider.py`，它只监听127.0.0.1:8089且不会转发请求。在另一终端设置 `JAVA_HOME`、`JHARNESS_SDK_JAR`（当前打包JAR绝对路径）、`JHARNESS_SDK_WORKSPACE`（专用目录）及 `JHARNESS_API_KEY=synthetic-demo-key`，然后运行：

```powershell
node --experimental-strip-types sdk/examples/run.ts
python sdk/examples/run.py
```

示例使用本目录固定loopback配置和只读权限，真实SDK读取README、打印结果、检索及事实报告。相同示例输入ID重复执行命中原会话，不能当作新的模型调用。用完停止示例provider；客户端会关闭自己的Java进程。

集成验收入口：设置 `JHARNESS_PYTHON` 为Python解释器绝对路径后运行 `node sdk/tests/run.mjs`。测试同时使用真实SDK模型替身和独立故障peer验证帧、背压、超时及自有进程清理，不发送付费请求。
