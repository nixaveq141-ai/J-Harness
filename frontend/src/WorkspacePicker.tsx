import { useEffect, useRef, useState } from 'react'
import { Api, ApiError, type Workspace } from './api'
import { Button } from './upstream/Button'
import { Input } from './upstream/Input'

export function WorkspacePicker({ api, value, disabled, onSelect, onProfiles }: { api: Api; value: string; disabled: boolean; onSelect: (id: string) => void; onProfiles: (rows: Workspace[]) => void }) {
  const [rows, setRows] = useState<Workspace[]>([]), [canRegister, setCanRegister] = useState(false)
  const [path, setPath] = useState(''), [name, setName] = useState(''), [error, setError] = useState(''), [busy, setBusy] = useState(false)
  useEffect(() => { let cancelled = false; void api.workspaces().then(v => { if (!cancelled) { setRows(v.workspaces); setCanRegister(v.canRegister); onProfiles(v.workspaces) } }).catch(() => { if (!cancelled) setError('工作区列表读取失败，请重新连接本机服务。') }); return () => { cancelled = true } }, [api])
  const registering = useRef(false)
  async function register() {
    if (registering.current || disabled || !path.trim() || !name.trim()) return
    registering.current = true; setBusy(true); setError('')
    try {
      const v = await api.registerWorkspace(path.trim(), name.trim()); setRows(v.workspaces); onProfiles(v.workspaces)
      onSelect(v.workspaces.at(-1)!.id); setPath(''); setName('')
    } catch (e) {
      const labels: Record<string, string> = { WORKSPACE_EXISTS: '该真实目录已注册，请直接选择。', WORKSPACE_LIMIT: '最多注册 16 个工作区。', WORKSPACE_OWNER_MISMATCH: '目录归属与当前工作区用户不一致。', SESSION_BUSY: '其他宿主正持有该工作区，请先停止旧宿主。' }
      setError(e instanceof ApiError ? labels[e.code] ?? '注册未完成。请选择你拥有的已有绝对目录，不能使用链接、元数据或秘密目录；重新读取后再决定是否重试。' : '工作区注册未完成，请检查本机连接。')
    } finally { registering.current = false; setBusy(false) }
  }
  return <div className="nav-expanded workspace-picker">
    <label>工作区<select aria-label="浏览工作区" value={value} disabled={disabled || busy} onChange={e => onSelect(e.target.value)}><option value="">全部工作区</option>{rows.map(w => <option key={w.id} value={w.id}>{w.name}{w.busy ? ' · 占用中' : ''}</option>)}</select></label>
    {value && <p className="workspace-path">{rows.find(w => w.id === value)?.path}</p>}
    {canRegister && <details><summary>注册工作区</summary><p>仅注册你拥有的已有目录；不会执行模型或加载该目录的配置。</p><label>工作区名称<Input aria-label="工作区名称" value={name} maxLength={80} disabled={disabled || busy} onChange={e => setName(e.target.value)} /></label><label>绝对目录<Input aria-label="工作区绝对目录" value={path} maxLength={1024} disabled={disabled || busy} onChange={e => setPath(e.target.value)} /></label><Button disabled={disabled || busy || !path.trim() || !name.trim()} onClick={() => void register()}>{busy ? '正在注册…' : '注册并选择'}</Button></details>}
    {error && <p className="inline-error" role="status">{error}</p>}
  </div>
}
