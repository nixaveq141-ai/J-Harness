export interface Task {
  taskId: string; profile: string; phase: string; status: string; error: string
  cancelRequested: boolean; recovered: boolean; protocolVersion: string
  name: string; pinned: boolean; archived: boolean; revision: string; createdAt: string
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
export interface ModelConnection {
  id: string; label: string; baseUrl: string; model: string; apiKeyEnv: string; keyConfigured: boolean
  pricing: Record<string, unknown>
  disableThinking: boolean
}
export interface SettingsData {
  revision: string; models: ModelConnection[]; selected: string; extensions: Record<string, unknown>
  policy: 'default' | 'read-only'
  credentialStore: string; corePlugins: string[]; credentialCleanupPending: boolean
}
function settings(value: unknown): SettingsData {
  const v = object(value)
  if (v.policy !== undefined && v.policy !== 'default' && v.policy !== 'read-only') throw new ApiError('INVALID_RESPONSE')
  if (!Array.isArray(v.models) || v.models.length < 1 || v.models.length > 16 || !Array.isArray(v.corePlugins)) throw new ApiError('INVALID_RESPONSE')
  const models = v.models.map(raw => {
    const m = object(raw)
    if (typeof m.keyConfigured !== 'boolean' || typeof m.disableThinking !== 'boolean') throw new ApiError('INVALID_RESPONSE')
    return { id: identifier(m.id), label: text(m.label, 80), baseUrl: text(m.baseUrl, 512), model: text(m.model, 128),
      apiKeyEnv: text(m.apiKeyEnv, 128), keyConfigured: m.keyConfigured, pricing: object(m.pricing), disableThinking: m.disableThinking }
  })
  return { revision: text(v.revision, 64), models, selected: identifier(v.selected), extensions: object(v.extensions), policy: v.policy === 'read-only' ? 'read-only' : 'default',
    credentialStore: text(v.credentialStore), corePlugins: v.corePlugins.map(x => text(x)), credentialCleanupPending: v.credentialCleanupPending === true }
}
export interface TurnSubmission { submissionId: string; kind: 'message' | 'supplement'; state: string; turnId: string; status: string; sequence: number; preview: string }
export function turnSubmission(value: unknown): TurnSubmission {
  const v = object(value)
  if (!/^[a-f0-9]{64}$/.test(text(v.submissionId, 64)) || !['message', 'supplement'].includes(text(v.kind)) || !['QUEUED','PAUSED','RUNNING','UNKNOWN','FINISHED','CANCELLED','VERIFIED'].includes(text(v.state)) || !Number.isInteger(v.sequence) || (v.sequence as number) < 2) throw new ApiError('INVALID_RESPONSE')
  return { submissionId: text(v.submissionId,64), kind: v.kind as 'message' | 'supplement', state: text(v.state), turnId: text(v.turnId,64), status: text(v.status), sequence: v.sequence as number, preview: v.preview === undefined ? '' : text(v.preview, 200) }
}
function turnReceipt(value: unknown, expectedId: string) { const v = object(value), current = task(v.task); if (current.taskId !== expectedId) throw new ApiError('INVALID_RESPONSE'); return { task: current, submission: turnSubmission(v.submission) } }
export class ApiError extends Error {
  constructor(public code: string, public status = 0) { super(code) }
}
const errors = new Set(['UNAUTHORIZED', 'LOCAL_CLIENT_REQUIRED', 'UNKNOWN_CURSOR', 'STREAM_LIMIT',
  'STALE_APPROVAL', 'WORKSPACE_BUSY', 'TASK_LIMIT', 'ACTIVE_LIMIT', 'SUBMISSION_NOT_FOUND',
  'SUBMISSION_NOT_CONFIRMED', 'REQUEST_ID_CONFLICT', 'DIFF_NOT_RECORDED', 'RESPONSE_LIMIT', 'NOT_FOUND',
  'SETTINGS_CONFLICT', 'INVALID_SETTINGS', 'SECRET_CONFIG', 'CREDENTIAL_STORE_UNAVAILABLE', 'API_KEY_MISSING', 'INVALID_CONFIG', 'COST_BUDGET_UNAVAILABLE', 'SESSION_CONFLICT', 'RECOVERY_REQUIRED', 'EXPORT_LIMIT', 'FORK_UNAVAILABLE', 'WORKSPACE_PATH_DENIED', 'WORKSPACE_OWNER_MISMATCH', 'WORKSPACE_EXISTS', 'WORKSPACE_LIMIT', 'SESSION_ALREADY_OWNED', 'SESSION_BUSY', 'INVALID_WORKSPACE_NAME', 'WEB_HISTORY_QUOTA'])
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
    recovered: v.recovered as boolean, protocolVersion: text(v.protocolVersion), name: v.name === undefined ? '' : text(v.name, 80),
    pinned: v.pinned === true, archived: v.archived === true, revision: v.revision === undefined ? '' : text(v.revision, 64), createdAt: v.createdAt === undefined ? '' : text(v.createdAt) }
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
export const uncertain = (t: Task) => t.phase !== 'VERIFIED' && (t.phase === 'UNKNOWN' || t.status === 'UNKNOWN')
export class Api {
  async turns(id: string, offset = 0) {
    const v = object(await this.json(`/v1/tasks/${identifier(id)}/turns?offset=${offset}`))
    if (!Array.isArray(v.submissions) || v.submissions.length > 32 || typeof v.more !== 'boolean' || !Number.isInteger(v.next) || !Number.isInteger(v.total)) throw new ApiError('INVALID_RESPONSE')
    return { submissions: v.submissions.map(turnSubmission), more: v.more, next: v.next as number, total: v.total as number }
  }
  async submitTurn(id: string, requestId: string, text: string, kind: 'message' | 'supplement') {
    return turnReceipt(await this.json(`/v1/tasks/${identifier(id)}/turns`, { requestId: identifier(requestId), text, kind }), id)
  }
  async turnStatus(id: string, requestId: string) { return turnReceipt(await this.json(`/v1/tasks/${identifier(id)}/turns?request=${identifier(requestId)}`), id) }
  async turnAction(id: string, hash: string, action: 'cancel' | 'start') { return turnReceipt(await this.json(`/v1/tasks/${identifier(id)}/turns/${identifier(hash)}/${action}`, {}), id) }
  async workspaces() { return workspaceData(await this.json('/v1/workspaces')) }
  async registerWorkspace(path: string, name: string) { return workspaceData(await this.json('/v1/workspaces', { path, name })) }
  async taskPage(profile = '', view = 'active', after = '', signal?: AbortSignal) {
    const params = new URLSearchParams({ limit: '32', view, ...(profile ? { profile: identifier(profile) } : {}), ...(after ? { after: identifier(after) } : {}) })
    const v = object(await this.json(`/v1/tasks?${params}`, undefined, signal))
    if (!Array.isArray(v.tasks) || v.tasks.length > 32 || typeof v.more !== 'boolean') throw new ApiError('INVALID_RESPONSE')
    return { tasks: v.tasks.map(task), next: text(v.next, 64), more: v.more }
  }
  async metadata(value: Task, patch: { name: string; pinned: boolean; archived: boolean }) {
    return task(await this.json(`/v1/tasks/${identifier(value.taskId)}/metadata`, { revision: value.revision, ...patch }))
  }
  async fork(value: Task, eventId: string, requestId: string) { return task(await this.json(`/v1/tasks/${identifier(value.taskId)}/fork`, { eventId: identifier(eventId), requestId: identifier(requestId) })) }
  async exportSession(id: string) {
    const v = object(await this.json(`/v1/tasks/${identifier(id)}/export`))
    if (v.sessionId !== id || v.artifactsIncluded !== false || !/^[a-f0-9]{64}$/.test(text(v.sha256, 64))) throw new ApiError('INVALID_RESPONSE')
    return { content: text(v.content, 220000), sha256: text(v.sha256, 64) }
  }
  static async connect(fetcher: typeof fetch = fetch): Promise<Api> {
    const transport = new Api('0'.repeat(32), fetcher)
    const result = object(await transport.json('/v1/browser-session', {}))
    return new Api(text(result.token), fetcher)
  }
  async settings() { return settings(await this.json('/v1/settings')) }
  async saveSettings(value: SettingsData, apiKeys: Record<string, string>) {
    return settings(await this.json('/v1/settings', { revision: value.revision, models: value.models, selected: value.selected, extensions: value.extensions, policy: value.policy, apiKeys }))
  }
  constructor(private token: string, private fetcher: typeof fetch = fetch) {
    if (!/^[A-Za-z0-9_-]{32,256}$/.test(token)) throw new ApiError('INVALID_TOKEN')
  }
  private async call(path: string, body?: unknown, signal?: AbortSignal, accept?: string): Promise<Response> {
    const abort = new AbortController()
    const onAbort = () => abort.abort()
    if (signal?.aborted) abort.abort()
    signal?.addEventListener('abort', onAbort, { once: true })
    const timer = setTimeout(() => abort.abort(), path === '/v1/settings' ? 45000 : 12000)
    try {
      const fetcher = this.fetcher
      const response = await fetcher(path, { method: body === undefined ? 'GET' : 'POST',
        headers: { ...(path === '/v1/browser-session' ? {} : { Authorization: `Bearer ${this.token}` }), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
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
  async diagnosis(id: string, signal?: AbortSignal) { return diagnosis(await this.json(`/v1/tasks/${identifier(id)}/diagnosis`, undefined, signal)) }
  async diff(id: string) { return this.json(`/v1/tasks/${identifier(id)}/diff`) }
  async search(id: string, text: string, after = '') { return page(await this.json(`/v1/tasks/${identifier(id)}/search`, { text, after, limit: 20 })) }
}

export interface Workspace { id: string; name: string; path: string; busy: boolean }
function workspaceData(raw: unknown) {
  const v = object(raw)
  if (!Array.isArray(v.workspaces) || v.workspaces.length > 16 || typeof v.canRegister !== 'boolean') throw new ApiError('INVALID_RESPONSE')
  return { canRegister: v.canRegister, workspaces: v.workspaces.map(raw => {
    const w = object(raw)
    return { id: identifier(w.id), name: text(w.name, 80), path: text(w.path, 1024), busy: w.busy === true }
  }) }
}

export interface Diagnosis {
  schemaVersion: number; source: string; eventCount: number; task: Task
  association: Record<string, unknown>; configuration: Record<string, unknown>; turnIds: string[]
  issues: Record<string, unknown>[]; issuesLimited: boolean; sdkFactsSettled: boolean
  pending: { uncertainCalls: string[]; unsettledCalls: string[]; pendingRequests: string[]; pendingTerminals: string[]; limited: boolean }
}
function diagnosis(value: unknown): Diagnosis {
  const v = object(value), p = object(v.pending)
  if (v.schemaVersion !== 1 || v.source !== 'session-events' || !Number.isSafeInteger(v.eventCount) || (v.eventCount as number) < 0
    || typeof v.sdkFactsSettled !== 'boolean' || typeof v.issuesLimited !== 'boolean' || typeof p.limited !== 'boolean'
    || !Array.isArray(v.issues) || v.issues.length > 12) throw new ApiError('INVALID_RESPONSE')
  function ids(raw: unknown): string[] {
    if (!Array.isArray(raw) || raw.length > 64) throw new ApiError('INVALID_RESPONSE')
    return raw.map(x => text(x))
  }
  return { schemaVersion: 1, source: 'session-events', eventCount: v.eventCount as number, task: task(v.task),
    association: object(v.association), configuration: object(v.configuration), turnIds: ids(v.turnIds),
    issues: v.issues.map(object), issuesLimited: v.issuesLimited, sdkFactsSettled: v.sdkFactsSettled,
    pending: { uncertainCalls: ids(p.uncertainCalls), unsettledCalls: ids(p.unsettledCalls), pendingRequests: ids(p.pendingRequests), pendingTerminals: ids(p.pendingTerminals), limited: p.limited } }
}
