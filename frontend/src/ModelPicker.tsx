import { useEffect, useState } from 'react'
import { Api, type ModelConnection } from './api'

/** Selection is local to the next admission; it never updates the saved default. */
export function ModelPicker({ api, value, onChange, disabled, label = '模型' }: { api: Api; value: string; onChange: (id: string) => void; disabled: boolean; label?: string }) {
  const [models, setModels] = useState<ModelConnection[]>([]), [error, setError] = useState(false)
  useEffect(() => {
    let stopped = false
    void api.models().then(v => { if (!stopped) { setModels(v.models); setError(false); if (!value || !v.models.some(m => m.id === value)) onChange(v.selected) } }).catch(() => { if (!stopped) setError(true) })
    return () => { stopped = true }
  }, [api])
  const chosen = models.find(m => m.id === value)
  function service(model: ModelConnection) { try { return new URL(model.baseUrl).hostname } catch { return model.baseUrl } }
  return <label className="model-picker">{label} <select aria-label={label} title={chosen ? chosen.label + ' · ' + chosen.model + ' · ' + service(chosen) : undefined} value={value} disabled={disabled || !models.length} onChange={e => onChange(e.target.value)}>
    {!models.length && <option value="">{error ? '无法读取连接，请重连或检查设置' : '正在读取连接…'}</option>}
    {models.map(m => <option value={m.id} key={m.id}>{m.label} · {m.model} · {service(m)}{m.keyConfigured ? '' : '（缺少密钥）'}</option>)}
  </select></label>
}
