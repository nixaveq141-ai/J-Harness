import { useEffect, useState } from 'react'
import { Api, ApiError, uncertain, type Diagnosis, type Task } from './api'
import { Button } from './upstream/Button'
import { Tag } from './upstream/Tag'

const categories: Record<string, string> = { VALIDATION: '参数或命令模式不符合要求', DENIED: '操作被拒绝', CANCELLED: '已取消', TIMEOUT: '超时', MODEL_NETWORK: '模型或网络请求失败', UNKNOWN: '操作结果不确定', FAILED: '执行失败' }
const actions: Record<string, string> = { DENIED: '本次调用未获授权。请检查权限或审批记录；迟到批准不会恢复这次调用。', CANCELLED: '取消不会撤销已发生的操作。请检查未决事实，再决定后续行动。', TIMEOUT: '请检查实际影响与未决事实；调整期限前先确认旧操作已停止，避免重复执行。', MODEL_NETWORK: '请检查模型连接与服务状态；存在未决请求时先离线核实，不要直接重新提交。', UNKNOWN: '请按下方向导检查真实资源并离线核实；页面不会自动重试。', FAILED: '请查看原始事件，确认失败原因及实际影响后再决定下一步。' }
const fields: [keyof Diagnosis['pending'], string][] = [['uncertainCalls', '待核实操作 / 环境 / 子任务'], ['unsettledCalls', '未结算工具调用'], ['pendingRequests', '未决模型请求所在步骤'], ['pendingTerminals', '未决终端']]
const steps = ['保留本页事实报告与关联；在线页面只读取，不追加核实事实。', '在原启动窗口输入 :quit，停止旧宿主及所有相关执行者。独占锁不能证明不合作线程已停止。', '检查真实进程、文件、工作区及外部影响。不要仅凭空残留列表、环境关闭成功或历史 PID 判断已完成。', '在相同工作区使用 CLI sessions inspect 读取未决事实；按原 verify / verify-request / verify-terminal / verify-environment / verify-subagent 命令逐项追加真实检查结果。', '所有底层未决事实处理后，再以原 catalog 执行 sessions verify-web；CLI 必须取得独占维护锁并通过绑定与恢复检查。重启后重新读取，核实不代表原任务成功，也不会重放。']
const text = (v: unknown, missing = '未知') => typeof v === 'string' && v !== '' ? v : typeof v === 'number' || typeof v === 'boolean' ? String(v) : missing
const json = (v: unknown) => JSON.stringify(v, null, 2)

export function DiagnosisPanel({ api, task, refresh }: { api: Api; task: Task; refresh: number }) {
  const [data, setData] = useState<Diagnosis | null>(null), [error, setError] = useState(''), [loading, setLoading] = useState(false), [reload, setReload] = useState(0)
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setData(null); setError('')
    void api.diagnosis(task.taskId, abort.signal).then(v => { if (!abort.signal.aborted) {
      if (v.task.taskId !== task.taskId) throw new ApiError('INVALID_RESPONSE')
      setData(v)
    } }).catch(e => { if (!abort.signal.aborted) setError(e instanceof ApiError && e.code === 'NOT_FOUND' ? '当前宿主不支持诊断，请更新后重新读取。' : '诊断读取失败。请检查连接后重新读取；不会重新执行任务。') })
      .finally(() => { if (!abort.signal.aborted) setLoading(false) })
    return () => abort.abort()
  }, [api, task.taskId, task.phase, task.status, refresh, reload])
  if (loading) return <p className="muted" role="status">正在读取执行诊断…</p>
  if (error) return <div className="diagnosis"><p role="status">{error}</p><Button onClick={() => setReload(v => v + 1)}>重新读取诊断</Button></div>
  if (!data || data.issues.length === 0 && data.sdkFactsSettled && !uncertain(data.task)) return null
  const needsVerification = uncertain(data.task) || !data.sdkFactsSettled
  return <section className="diagnosis" aria-label="执行诊断">
    <h3>{needsVerification ? '为什么结果待核实' : '执行情况说明'}</h3>
    <p>{needsVerification ? '已记录的事实尚不足以安全结束。请先核实实际影响；不会自动重试或重放。' : '以下问题来自已记录事件，任务最终状态以宿主记录为准。'}</p>
    {data.task.error && <p>宿主记录：<code>{data.task.error}</code>。{data.task.phase === 'VERIFIED' ? '已记录离线核实，保留原任务结果，未重放；核实不代表原任务成功。' : '请结合底层未决事实核实，不能据此认定没有执行。'}</p>}
    {data.task.cancelRequested && <p>已请求取消。是否取消成功，以各操作结果及未决事实为准。</p>}
    {data.issues.map((issue, index) => <article className="diagnosis-issue" key={`${text(issue.eventId)}:${index}`}>
      <div className="diagnosis-title"><strong>{issue.operation === 'turn' ? '本轮结束' : text(issue.operation, '操作')}</strong><Tag tone="warning">{categories[text(issue.category)] ?? '执行问题'}</Tag></div>
      <dl><div><dt>错误 / 结果</dt><dd>{text(issue.code, text(issue.status))}</dd></div>
        <div><dt>耗时 / 请求期限</dt><dd>{text(issue.commandDurationMs ?? issue.durationMs)}{typeof (issue.commandDurationMs ?? issue.durationMs) === 'number' ? ' ms' : ''} / {text(issue.timeoutMs)}{typeof issue.timeoutMs === 'number' ? ' ms' : ''}</dd></div>
        <div><dt>主体执行</dt><dd>{issue.commandStarted === 'no' ? '命令主体未启动' : issue.commandStarted === 'yes' ? '命令主体已启动' : issue.bodyEntered === false ? '工具主体未进入' : issue.bodyEntered === true ? issue.operation === 'execute_command' ? '工具包装器已进入；命令启动情况未知' : '工具主体已进入' : '未知，记录不足'}</dd></div>
        <div><dt>可能影响</dt><dd>{issue.effects === 'not_started' ? '本次主体未产生执行影响' : issue.effects === 'possible' ? '可能已产生影响，需检查实际资源' : issue.effects === 'read_only' ? '工具标记为只读' : '未知，不能据此认定无影响'}</dd></div>
        <div><dt>清理证据</dt><dd>{text(issue.cleanupStatus, '未记录')}{issue.observedClean === true ? '；采样中未见残留，不保证所有进程已结束' : ''}</dd></div>
      </dl>
      {issue.code === 'SHELL_REQUIRED' && <p>ARGV 模式不能直接运行 Shell 解释器或批处理。请检查参数；若确需 CMD / POWERSHELL / SH，应显式选择模式并重新经过审批。页面不会自动切换。</p>}
      {issue.approvalReason === 'Approval expired' && <p>审批已过期，未获批准；迟到的批准不会生效。</p>}
      {issue.approvalReason === 'Approval expired or cancelled' && <p>旧记录显示审批过期或被取消，无法进一步区分；本次未获批准。</p>}
      {issue.category === 'VALIDATION' && issue.code !== 'SHELL_REQUIRED' && <p>请核对命令模式、参数类型和必填字段，依据原始事件修改；不会自动放宽权限。</p>}
      {actions[text(issue.category)] && <p>下一步：{actions[text(issue.category)]}</p>}
      <details><summary>查看事件关联</summary><pre>{json(issue)}</pre></details>
    </article>)}
    {data.issuesLimited && <p className="muted">仅展示最近 12 个问题，完整记录请使用事件查询。</p>}
    {needsVerification && <><h3>仍需核实的事实</h3>{fields.map(([key, label]) => {
      const values = data.pending[key]
      return Array.isArray(values) && values.length > 0 ? <div key={key}><strong>{label}</strong><pre>{values.join('\n')}</pre></div> : null
    })}{data.sdkFactsSettled && <p>底层 SDK 当前无未决事实，Web 目录仍待离线核实；这不证明旧执行者已停止。</p>}
      {data.pending.limited && <p>未决列表已限长，请通过 CLI inspect 查看完整集合。</p>}
      <details className="verification-guide"><summary>打开安全核实向导</summary>
        <ol>{steps.map(step => <li key={step}>{step}</li>)}</ol>
        <p>只读检查命令片段（在原工作区使用 J-Harness CLI）：</p><pre>{`sessions inspect --session ${data.task.taskId}`}</pre>
        <p>没有在线清除状态或自动执行核实的按钮。核实理由和 APPLIED / NOT_APPLIED / PARTIAL 等结果必须来自实际检查，不能预填。</p>
        <Button variant="outline" onClick={() => {
          const url = URL.createObjectURL(new Blob([json({ ...data, handoff: { readOnly: true, steps, automaticReplay: false } })], { type: 'application/json' }))
          const a = document.createElement('a'); a.href = url; a.download = `${data.task.taskId}-verification.json`; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
        }}>下载离线核实交接</Button>
      </details></>}
    <details><summary>配置与提交关联</summary><pre>{json({ ...data.association, turnIds: data.turnIds, configuration: data.configuration, task: data.task })}</pre><p className="muted">requestHash 是提交 ID 的哈希。配置以本次记录为准；缺字段表示旧记录未提供。</p></details>
    <Button onClick={() => setReload(v => v + 1)}>重新读取诊断</Button>
  </section>
}
