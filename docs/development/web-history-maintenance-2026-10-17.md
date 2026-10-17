# Web UI 历史清理记录

2026-10-17，已完成。用户要求清空本项目Web UI全部会话，并明确选择先核实待核实记录，再清空全部。本次为本机运行数据维护，未修改产品代码或重标阶段测试证据。

## 核实依据

清理前当前项目根 `.jharness` 有4条Web会话，其中1条命令超时UNKNOWN。只读inspect确认仅1个uncertainCalls，pendingRequests、pendingTerminals及unsettledCalls均为空。原命令通过CMD启动NoProfile PowerShell，仅枚举/读取本地文件并在内存汇总行数，没有文件写入、网络调用或另起后台任务；执行中有数组相加错误，随后超时。原清理记录为partial，未将observedClean或空残留列表单独当作完成证据。

实际CIM进程枚举确认原3个tracked PID均不存在，没有仍在运行的原统计命令。用户新打开的serve曾持锁，用户按要求 :quit 关闭后再次检查原宿主已停止。核实前Git工作区干净，原统计命令静态检查无工作区写入指令；后续仅本维护文档及.agent变更。

通过原CLI精确调用关联追加tool.verified，outcome=PARTIAL：命令确实部分执行并超时，不记为NOT_APPLIED或成功，不重放。随后inspect返回ready=true、status=FAILED，全部未决集合为空；再执行原目录sessions verify-web，返回WEB_TASK_VERIFIED_NO_REPLAY。删除前逐条inspect确认4条会话均ready，工作区占用指针为空。

## 清理与验证

在宿主停止、目录锁独占期间，校验所有删除目标的绝对路径均位于当前项目 `.jharness`，且没有链接/重解析点，移除Web旧catalog、旧备份、分任务目录、4条SDK日志及其产物，清理已结算的占用状态和四个空会话锁。没有修改模型/权限/扩展配置、工作区注册表、系统凭据、工作区文件或只读DeepSeek Harness目录。清理时settings/config没有现存文件；系统凭据未读写。

短暂启动实际CLI Web宿主，仅GET `/v1/tasks?view=all`，确认tasks=[]、historyCount=0、hotCount=0，之后通过 :quit 正常关闭。没有提交任务、调用模型、打开浏览器、修改剪贴板或遗留验证宿主。启动时重建的空catalog/占用元数据不包含旧会话。

运行数据、原日志/命令正文、凭据及备份不进入Git；仅提交.agent及本维护记录。M10.5.2验收保留历史，下一开发入口仍为M10.5.3。
