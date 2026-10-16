export interface Task {
  taskId: string; profile: string; phase: string; status: string; error: string
  cancelRequested: boolean; recovered: boolean; protocolVersion: string
}
export interface SessionEvent {
  eventId: string; type: string; timestamp: string; callId?: string | null
  payload: Record<string, unknown>
}
export interface Page { events: SessionEvent[]; next: string; more: boolean }
export interface Approval {
  approvalId: string; callId: string; sessionId: string; tool: string
  arguments: Record<string, unknown>; sideEffectLevel: string
}
export class ApiError extends Error {
  constructor(public code: string, public status = 0) { super(code) }
}
const errors = new Set(['UNAUTHORIZED', 'LOCAL_CLIENT_REQUIRED', 'UNKNOWN_CURSOR', 'STREAM_LIMIT',
  'STALE_APPROVAL', 'WORKSPACE_BUSY', 'TASK_LIMIT', 'ACTIVE_LIMIT', 'SUBMISSION_NOT_FOUND',
  'SUBMISSION_NOT_CONFIRMED', 'REQUEST_ID_CONFLICT', 'DIFF_NOT_RECORDED', 'RESPONSE_LIMIT', 'NOT_FOUND'])
export function object(value: unknown): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new ApiError('INVALID_RESPONSE')
  return value as Record<string, unknown>
}
function text(value: unknown, max = 256): string {
  if (typeof value !== 'string' || value.length > max) throw new ApiError('INVALID_RESPONSE')
  return value
}
function identifier(value: unknown): string {
  const id = text(value, 128)
  if (!/^[A-Za-z0-9_-]+$/.test(id)) throw new ApiError('INVALID_RESPONSE')
  return id
}
export function task(value: unknown): Task {
  const v = object(value)
  for (const k of ['cancelRequested', 'recovered']) if (typeof v[k] !== 'boolean') throw new ApiError('INVALID_RESPONSE')
  return { taskId: identifier(v.taskId), profile: identifier(v.profile), phase: text(v.phase),
    status: text(v.status), error: text(v.error), cancelRequested: v.cancelRequested as boolean,
    recovered: v.recovered as boolean, protocolVersion: text(v.protocolVersion) }
}
function event(value: unknown): SessionEvent {
  const v = object(value)
  return { eventId: identifier(v.eventId), type: text(v.type, 128), timestamp: text(v.timestamp),
    callId: v.callId == null ? null : text(v.callId), payload: object(v.payload) }
}
function page(value: unknown): Page {
  const v = object(value)
  if (!Array.isArray(v.events) || v.events.length > 100 || typeof v.more !== 'boolean') throw new ApiError('INVALID_RESPONSE')
  return { events: v.events.map(event), next: text(v.next, 64), more: v.more }
}
export function mergeEvents(old: SessionEvent[], incoming: SessionEvent[]): SessionEvent[] {
  const seen = new Set(old.map(e => e.eventId))
  return [...old, ...incoming.filter(e => { if (seen.has(e.eventId)) return false; seen.add(e.eventId); return true })].slice(0, 4000)
}
export const isTerminal = (t: Task) => ['FINISHED', 'UNKNOWN', 'VERIFIED'].includes(t.phase)
export const uncertain = (t: Task) => t.phase === 'UNKNOWN' || t.status === 'UNKNOWN'
export class Api {
  constructor(private token: string, private fetcher: typeof fetch = fetch) {
    if (!/^[A-Za-z0-9_-]{32,256}$/.test(token)) throw new ApiError('INVALID_TOKEN')
  }
  private async call(path: string, body?: unknown, signal?: AbortSignal, accept?: string): Promise<Response> {
    const abort = new AbortController()
    const onAbort = () => abort.abort()
    if (signal?.aborted) abort.abort()
    signal?.addEventListener('abort', onAbort, { once: true })
    const timer = setTimeout(() => abort.abort(), 12000)
    try {
      const fetcher = this.fetcher
      const response = await fetcher(path, { method: body === undefined ? 'GET' : 'POST',
        headers: { Authorization: `Bearer ${this.token}`, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
          ...(accept ? { Accept: accept } : {}) },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }), signal: abort.signal,
        cache: 'no-store', credentials: 'omit', redirect: 'error' })
      if (!response.ok) {
        let code = `HTTP_${response.status}`
        try { const v = object(await this.readJson(response)); if (typeof v.error === 'string' && errors.has(v.error)) code = v.error } catch { /* do not surface untrusted response text */ }
        throw new ApiError(code, response.status)
      }
      return response
    } catch (e) {
      if (e instanceof ApiError) throw e
      throw new ApiError(signal?.aborted ? 'ABORTED' : 'CONNECTION_LOST')
    } finally {
      clearTimeout(timer)
      signal?.removeEventListener('abort', onAbort)
    }
  }
  private async readJson(response: Response, signal?: AbortSignal): Promise<unknown> {
    if (!response.body) throw new ApiError('INVALID_RESPONSE')
    const reader = response.body.getReader(), decoder = new TextDecoder()
    let expired = false
    const abort = () => { void reader.cancel().catch(() => {}) }
    const timer = setTimeout(() => { expired = true; abort() }, 12000)
    signal?.addEventListener('abort', abort, { once: true })
    let size = 0, content = ''
    try {
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        size += value.byteLength
        if (size > 1048576) throw new ApiError('RESPONSE_LIMIT')
        content += decoder.decode(value, { stream: true })
      }
      if (signal?.aborted) throw new ApiError('ABORTED')
      if (expired) throw new ApiError('CONNECTION_LOST')
      return JSON.parse(content + decoder.decode()) as unknown
    } catch (e) { await reader.cancel().catch(() => {}); if (e instanceof ApiError) throw e; throw new ApiError('INVALID_RESPONSE') }
    finally { clearTimeout(timer); signal?.removeEventListener('abort', abort); reader.releaseLock() }
  }
  private async json(path: string, body?: unknown, signal?: AbortSignal) { return this.readJson(await this.call(path, body, signal), signal) }
  async info() {
    const v = object(await this.json('/v1/info'))
    if (!Array.isArray(v.profiles) || v.profiles.length > 16) throw new ApiError('INVALID_RESPONSE')
    return { profiles: v.profiles.map(identifier), protocolVersion: text(v.protocolVersion) }
  }
  async tasks(signal?: AbortSignal) {
    const v = object(await this.json('/v1/tasks', undefined, signal))
    if (!Array.isArray(v.tasks) || v.tasks.length > 32) throw new ApiError('INVALID_RESPONSE')
    return v.tasks.map(task)
  }
  async submit(profile: string, goal: string, requestId: string) { return task(await this.json('/v1/tasks', { profile, goal, requestId })) }
  async submission(requestId: string) { return task(await this.json(`/v1/submissions/${identifier(requestId)}`)) }
  async detail(id: string, signal?: AbortSignal) { return task(await this.json(`/v1/tasks/${identifier(id)}`, undefined, signal)) }
  async cancel(id: string) { return task(await this.json(`/v1/tasks/${identifier(id)}/cancel`, {})) }
  async approvals(id: string, signal?: AbortSignal): Promise<Approval[]> {
    const v = object(await this.json(`/v1/tasks/${identifier(id)}/approvals`, undefined, signal))
    if (!Array.isArray(v.approvals) || v.approvals.length > 8) throw new ApiError('INVALID_RESPONSE')
    return v.approvals.map(raw => {
      const a = object(raw)
      return { approvalId: identifier(a.approvalId), callId: text(a.callId), sessionId: identifier(a.sessionId),
        tool: text(a.tool), arguments: object(a.arguments), sideEffectLevel: text(a.sideEffectLevel) }
    })
  }
  async decide(id: string, approval: Approval, approved: boolean, reason: string) {
    if (approval.sessionId !== id) throw new ApiError('STALE_APPROVAL', 409)
    await this.json(`/v1/tasks/${identifier(id)}/approvals/${identifier(approval.approvalId)}`,
      { approved, reason: reason.trim() || (approved ? '用户批准本次操作' : '用户拒绝本次操作'), callId: approval.callId })
  }
  async events(id: string, after = '', signal?: AbortSignal) {
    return page(await this.json(`/v1/tasks/${identifier(id)}/events?limit=100${after ? `&after=${identifier(after)}` : ''}`, undefined, signal))
  }
  async stream(id: string, after: string, receive: (e: SessionEvent) => void, signal: AbortSignal) {
    // A stream is a bounded GET. It never shares a retry path with task creation.
    const response = await this.call(`/v1/tasks/${identifier(id)}/events?limit=100${after ? `&after=${identifier(after)}` : ''}`, undefined, signal, 'text/event-stream')
    if (!response.headers.get('content-type')?.startsWith('text/event-stream') || !response.body) throw new ApiError('INVALID_RESPONSE')
    const reader = response.body.getReader(), decoder = new TextDecoder()
    const abort = () => { void reader.cancel().catch(() => {}) }
    signal.addEventListener('abort', abort, { once: true })
    let content = '', size = 0
    const timeout = setTimeout(abort, 10000)
    try {
      for (;;) {
        if (signal.aborted) return
        const { done, value } = await reader.read()
        if (done) break
        size += value.byteLength
        if (size > 1048576) throw new ApiError('RESPONSE_LIMIT')
        content += decoder.decode(value, { stream: true })
        let boundary: number
        while ((boundary = content.indexOf('\n\n')) >= 0) {
          const frame = content.slice(0, boundary); content = content.slice(boundary + 2)
          const data = frame.split('\n').find(l => l.startsWith('data: '))
          if (!data) continue
          const row = event(JSON.parse(data.slice(6)))
          if (!frame.split('\n').includes(`id: ${row.eventId}`)) throw new ApiError('INVALID_RESPONSE')
          if (!signal.aborted) receive(row)
        }
      }
      // A truncated last frame is deliberately discarded; reconnect starts at last committed eventId.
    } catch (e) { if (!signal.aborted) throw e instanceof ApiError ? e : new ApiError('CONNECTION_LOST') }
    finally { clearTimeout(timeout); signal.removeEventListener('abort', abort); await reader.cancel().catch(() => {}); reader.releaseLock() }
  }
  async report(id: string) { return this.json(`/v1/tasks/${identifier(id)}/report`) }
  async diff(id: string) { return this.json(`/v1/tasks/${identifier(id)}/diff`) }
  async search(id: string, text: string, after = '') { return page(await this.json(`/v1/tasks/${identifier(id)}/search`, { text, after, limit: 20 })) }
}
