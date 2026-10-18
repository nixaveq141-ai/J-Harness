export interface Task {
  taskId: string; profile: string; phase: string; status: string; error: string
  cancelRequested: boolean; recovered: boolean; protocolVersion: string
  modelId?: string; modelName?: string; modelProtocol?: string
  name: string; pinned: boolean; archived: boolean; revision: string; createdAt: string
}
export interface SkillEntry { id: string; description: string; root: string; directory: string; text: string; sha256: string; tool: string }
export interface SessionEvent {
  eventId: string; type: string; timestamp: string; callId?: string | null; turnId?:string|null; stepId?:string|null
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
  protocol?: 'chat-completions' | 'responses' | 'anthropic'; reasoningEffort?: string
  contextTokens?: number; maxOutputTokens?: number; requestTimeoutSeconds?: number
  toolsSupported?: boolean; streamSupported?: boolean; streaming?: boolean
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
    const options: Partial<ModelConnection> = {}
    if (m.protocol !== undefined) { if (!['chat-completions','responses','anthropic'].includes(String(m.protocol))) throw new ApiError('INVALID_RESPONSE'); options.protocol = m.protocol as ModelConnection['protocol'] }
    if (m.reasoningEffort !== undefined) { if (!['none','low','medium','high'].includes(String(m.reasoningEffort))) throw new ApiError('INVALID_RESPONSE'); options.reasoningEffort = String(m.reasoningEffort) }
    for (const k of ['contextTokens','maxOutputTokens','requestTimeoutSeconds'] as const) if (m[k] !== undefined) { if (!Number.isInteger(m[k]) || (m[k] as number) < 1) throw new ApiError('INVALID_RESPONSE'); options[k] = m[k] as number }
    for (const k of ['toolsSupported','streamSupported','streaming'] as const) if (m[k] !== undefined) { if (typeof m[k] !== 'boolean') throw new ApiError('INVALID_RESPONSE'); options[k] = m[k] as boolean }
    return { ...options, id: identifier(m.id), label: text(m.label, 80), baseUrl: text(m.baseUrl, 512), model: text(m.model, 128),
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
export interface UserQuestion {
  questionId: string; sessionId: string; turnId: string; callId: string; question: string
  mode: 'single' | 'multiple' | 'text'; options: { id: string; label: string; description: string; recommended: boolean }[]
  allowText: boolean; state: string; expiresAt: string; requestId?: string
  selected?: string[]; text?: string; draftRevision?: number; draft?: { selected: string[]; text: string }
}
export function userQuestion(value: unknown): UserQuestion {
  const v = object(value)
  if (!['single','multiple','text'].includes(String(v.mode)) || !['PENDING','SUBMITTED','ANSWERED','SKIPPED','TIMED_OUT','CANCELLED','INTERRUPTED'].includes(String(v.state)) || typeof v.allowText !== 'boolean' || !Array.isArray(v.options) || v.options.length>8) throw new ApiError('INVALID_RESPONSE')
  const options = v.options.map(raw => { const o = object(raw); if (typeof o.recommended !== 'boolean') throw new ApiError('INVALID_RESPONSE'); return { id: identifier(o.id), label: text(o.label,128), description: text(o.description,512), recommended: o.recommended } })
  const draft = v.draft === undefined ? {} : object(v.draft)
  const answerList = (raw: unknown) => { if (!Array.isArray(raw) || raw.length>8) throw new ApiError('INVALID_RESPONSE'); return raw.map(identifier) }
  if (v.draftRevision !== undefined && (!Number.isInteger(v.draftRevision) || (v.draftRevision as number)<0)) throw new ApiError('INVALID_RESPONSE')
  return { draftRevision: v.draftRevision === undefined ? 0 : v.draftRevision as number, questionId: identifier(v.questionId), sessionId: identifier(v.sessionId), turnId: identifier(v.turnId), callId: text(v.callId,256), question: text(v.question,2048), mode: v.mode as UserQuestion['mode'], options, allowText: v.allowText, state: text(v.state), expiresAt: text(v.expiresAt),
    ...(v.requestId === undefined ? {} : { requestId: identifier(v.requestId) }), ...(v.selected === undefined ? {} : { selected: answerList(v.selected) }), ...(v.text === undefined ? {} : { text: text(v.text,4096) }),
    ...(draft.selected === undefined ? {} : { draft: { selected: answerList(draft.selected), text: text(draft.text,4096) } }) }
}
export class ApiError extends Error {
  constructor(public code: string, public status = 0) { super(code) }
}
const errors = new Set(['INSPECTION_UNAVAILABLE','INSPECTION_DENIED','PATH_DENIED','FILE_NOT_FOUND','FILE_LIMIT','FILE_CONFLICT','CONTENT_CHANGED','INVALID_RANGE','BINARY_FILE','UNSUPPORTED_ENCODING','ARTIFACT_NOT_FOUND','ARTIFACT_CORRUPT','ARTIFACT_DENIED','DRAFT_CONFLICT','QUESTION_ASSOCIATION_MISMATCH','QUESTION_NOT_FOUND','QUESTION_CLOSED','ANSWER_CONFLICT','INVALID_ANSWER','INVALID_QUESTION','UNAUTHORIZED', 'LOCAL_CLIENT_REQUIRED', 'UNKNOWN_CURSOR', 'STREAM_LIMIT',
  'STALE_APPROVAL', 'WORKSPACE_BUSY', 'TASK_LIMIT', 'ACTIVE_LIMIT', 'SUBMISSION_NOT_FOUND',
  'SUBMISSION_NOT_CONFIRMED', 'REQUEST_ID_CONFLICT', 'DIFF_NOT_RECORDED', 'RESPONSE_LIMIT', 'NOT_FOUND',
  'UNKNOWN_MODEL', 'MODEL_SELECTION_REQUIRED', 'QUEUE_LIMIT', 'QUEUE_PAUSED', 'QUEUE_ORDER_CONFLICT', 'SUBMISSION_ALREADY_STARTED', 'SESSION_ARCHIVED', 'SETTINGS_CONFLICT', 'INVALID_SETTINGS', 'SECRET_CONFIG', 'CREDENTIAL_STORE_UNAVAILABLE', 'API_KEY_MISSING', 'INVALID_CONFIG', 'COST_BUDGET_UNAVAILABLE', 'SESSION_CONFLICT', 'RECOVERY_REQUIRED', 'EXPORT_LIMIT', 'FORK_UNAVAILABLE', 'WORKSPACE_PATH_DENIED', 'WORKSPACE_OWNER_MISMATCH', 'WORKSPACE_EXISTS', 'WORKSPACE_LIMIT', 'SESSION_ALREADY_OWNED', 'SESSION_BUSY', 'INVALID_WORKSPACE_NAME', 'WEB_HISTORY_QUOTA'])
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
  return { modelId: v.modelId === undefined ? '' : text(v.modelId,32), modelName: v.modelName === undefined ? '' : text(v.modelName,128), modelProtocol: v.modelProtocol === undefined ? '' : text(v.modelProtocol,32), taskId: identifier(v.taskId), profile: identifier(v.profile), phase: text(v.phase),
    status: text(v.status), error: text(v.error), cancelRequested: v.cancelRequested as boolean,
    recovered: v.recovered as boolean, protocolVersion: text(v.protocolVersion), name: v.name === undefined ? '' : text(v.name, 80),
    pinned: v.pinned === true, archived: v.archived === true, revision: v.revision === undefined ? '' : text(v.revision, 64), createdAt: v.createdAt === undefined ? '' : text(v.createdAt) }
}
function event(value: unknown): SessionEvent {
  const v = object(value)
  return { eventId: identifier(v.eventId), type: text(v.type, 128), timestamp: text(v.timestamp),
    callId: v.callId == null ? null : text(v.callId), ...(v.turnId===undefined?{}:{turnId:v.turnId===null?null:identifier(v.turnId)}),...(v.stepId===undefined?{}:{stepId:v.stepId===null?null:identifier(v.stepId)}), payload: object(v.payload) }
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
  async display(id:string,signal?:AbortSignal) {
    const v=object(await this.json('/v1/tasks/'+identifier(id)+'/display',undefined,signal))
    if(v.sessionId!==id||v.source!=='temporary-model-stream'||!['IDLE','UNAVAILABLE','STREAMING','INTERRUPTED','PERSISTED'].includes(String(v.state)))throw new ApiError('INVALID_RESPONSE')
    return {state:text(v.state),text:text(v.text,65536),sourceEvent:v.sourceEvent===undefined?'':text(v.sourceEvent,64),truncated:v.truncated===true}
  }
  async files(id:string,path='.',signal?:AbortSignal) {
    const v=object(await this.json('/v1/tasks/'+identifier(id)+'/files?path='+encodedPath(path),undefined,signal))
    if(v.source!=='current-workspace'||!Array.isArray(v.entries)||v.entries.length>200||typeof v.truncated!=='boolean')throw new ApiError('INVALID_RESPONSE')
    return {entries:v.entries.map(raw=>{const e=object(raw);if(!['file','directory'].includes(String(e.type)))throw new ApiError('INVALID_RESPONSE');return {path:text(e.path,512),type:String(e.type)}}),truncated:v.truncated}
  }
  async content(id:string,kind:'file'|'artifacts'|'content',target:string,offset=0,version='',line=0,signal?:AbortSignal):Promise<ContentPage> {
    if(!Number.isSafeInteger(offset)||offset<0||offset>8388608||!Number.isSafeInteger(line)||line<0)throw new ApiError('INVALID_RESPONSE')
    if(kind==='artifacts'&&!/^[0-9a-f-]{36}-[a-f0-9]{64}$/.test(target))throw new ApiError('INVALID_RESPONSE')
    if(version&&!/^[a-f0-9]{64}$/.test(version))throw new ApiError('INVALID_RESPONSE')
    const path='/v1/tasks/'+identifier(id)+'/'+kind+(kind==='file'?'?path='+encodedPath(target)+'&line='+line:'/'+(kind==='content'?identifier(target):target)+'?')+'&offset='+offset+(version?'&version='+version:'')
    const v=contentPage(await this.json(path.replace('?&','?'),undefined,signal))
    if(kind==='file'&&(v.source!=='current-workspace'||v.path!==target||!/^[a-f0-9]{64}$/.test(String(v.fileVersion)))||kind==='artifacts'&&v.source!=='recorded-artifact'||kind==='content'&&v.source!=='recorded-event')throw new ApiError('INVALID_RESPONSE')
    if(kind!=='file'&&v.sessionId!==id||kind==='artifacts'&&v.artifactId!==target||kind==='content'&&v.eventId!==target)throw new ApiError('INVALID_RESPONSE')
    if(v.offset!==offset&&!line||version&&(kind==='file'?v.fileVersion:v.contentSha256)!==version)throw new ApiError('INVALID_RESPONSE')
    return v
  }
  async artifacts(id:string,signal?:AbortSignal) {
    const v=object(await this.json('/v1/tasks/'+identifier(id)+'/artifacts',undefined,signal))
    if(v.sessionId!==id||!Array.isArray(v.artifacts)||v.artifacts.length>64||typeof v.limited!=='boolean')throw new ApiError('INVALID_RESPONSE')
    return {artifacts:v.artifacts.map(raw=>{const a=object(raw),id=text(a.id,101);if(!/^[0-9a-f-]{36}-[a-f0-9]{64}$/.test(id)||a.sha256!==id.slice(37))throw new ApiError('INVALID_RESPONSE');return{id,sha256:String(a.sha256)}}),limited:v.limited}
  }
  async questions(id: string) { return (await this.questionPage(id)).questions }
  async questionPage(id: string, offset = 0) {
    if (!Number.isInteger(offset) || offset<0 || offset>64) throw new ApiError('INVALID_RESPONSE')
    const v = object(await this.json('/v1/tasks/' + identifier(id) + '/questions?offset=' + offset))
    if (!Array.isArray(v.questions) || v.questions.length>8) throw new ApiError('INVALID_RESPONSE')
    const rows = v.questions.map(userQuestion); if (rows.some(q => q.sessionId !== id) || !Number.isInteger(v.total) || (v.total as number)<rows.length || (v.total as number)>64 || typeof v.hasWaiting !== 'boolean') throw new ApiError('INVALID_RESPONSE'); return { questions: rows, total: v.total as number, hasWaiting: v.hasWaiting }
  }
  async answerQuestion(q: UserQuestion, requestId: string, selected: string[], text: string, skip: boolean) {
    const v = userQuestion(await this.json('/v1/tasks/' + identifier(q.sessionId) + '/questions/' + identifier(q.questionId) + '/answer', { questionId: q.questionId, sessionId: q.sessionId, turnId: q.turnId, callId: q.callId, requestId: identifier(requestId), selected, text, skip }))
    if (v.questionId !== q.questionId || v.sessionId !== q.sessionId) throw new ApiError('INVALID_RESPONSE'); return v
  }
  async questionDraft(q: UserQuestion, selected: string[], text: string, revision: number) {
    const v = object(await this.json('/v1/tasks/' + identifier(q.sessionId) + '/questions/' + identifier(q.questionId) + '/draft', { questionId: q.questionId, sessionId: q.sessionId, turnId: q.turnId, callId: q.callId, selected, text, revision }))
    if (!Array.isArray(v.selected) || typeof v.text !== 'string' || v.text.length>4096 || !Number.isInteger(v.revision) || (v.revision as number)<1) throw new ApiError('INVALID_RESPONSE')
    return { revision: v.revision as number }
  }

  async turns(id: string, offset = 0) {
    const v = object(await this.json(`/v1/tasks/${identifier(id)}/turns?offset=${offset}`))
    if (!Array.isArray(v.submissions) || v.submissions.length > 32 || typeof v.more !== 'boolean' || !Number.isInteger(v.next) || !Number.isInteger(v.total)) throw new ApiError('INVALID_RESPONSE')
    return { submissions: v.submissions.map(turnSubmission), more: v.more, next: v.next as number, total: v.total as number }
  }
  async submitTurn(id: string, requestId: string, text: string, kind: 'message' | 'supplement', modelId = '') {
    return turnReceipt(await this.json(`/v1/tasks/${identifier(id)}/turns`, { requestId: identifier(requestId), text, kind, ...(modelId ? { modelId } : {}) }), id)
  }
  async turnStatus(id: string, requestId: string) { return turnReceipt(await this.json(`/v1/tasks/${identifier(id)}/turns?request=${identifier(requestId)}`), id) }
  async turnAction(id: string, hash: string, action: 'cancel' | 'start') { return turnReceipt(await this.json(`/v1/tasks/${identifier(id)}/turns/${identifier(hash)}/${action}`, {}), id) }
  async workspaces() { return workspaceData(await this.json('/v1/workspaces')) }
  async skills(profile: string, signal?: AbortSignal): Promise<SkillEntry[]> {
    const v = object(await this.json('/v1/skills?profile=' + identifier(profile), undefined, signal))
    if (v.snapshot !== true || !Array.isArray(v.skills) || v.skills.length > 24) throw new ApiError('INVALID_RESPONSE')
    const names = new Set<string>()
    return v.skills.map(raw => {
      const s = object(raw), id = text(s.id, 24), hash = text(s.sha256, 64)
      if (!/^[A-Za-z0-9_-]+$/.test(id) || names.has(id) || !/^[a-f0-9]{64}$/.test(hash) || s.tool !== `skill_${id}__load` || s.untrusted !== true) throw new ApiError('INVALID_RESPONSE')
      names.add(id)
      return { id, description: text(s.description, 1024), root: text(s.root, 32), directory: text(s.directory, 1024), text: text(s.text, 65536), sha256: hash, tool: s.tool as string }
    })
  }
  async tools(id: string) {
    const v = object(await this.json('/v1/tasks/' + identifier(id) + '/tools'))
    if (typeof v.recorded !== 'boolean' || !Array.isArray(v.tools) || v.tools.length > 256 || !v.recorded && v.tools.length) throw new ApiError('INVALID_RESPONSE')
    const names = new Set<string>()
    return { recorded: v.recorded, tools: v.tools.map(raw => { const tool = object(raw), name = text(tool.name, 64), sideEffect = text(tool.sideEffect, 32)
      if (!/^[A-Za-z0-9_-]+$/.test(name) || names.has(name) || !['READ_ONLY','WORKSPACE_WRITE','SYSTEM_IMPACT'].includes(sideEffect)) throw new ApiError('INVALID_RESPONSE')
      names.add(name); return { name, description: text(tool.description, 512), sideEffect }
    }) }
  }
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
  async models() {
    const v = object(await this.json('/v1/models'))
    // Reuse the settings model validator without accepting arbitrary model metadata.
    return { models: settings({ ...v, extensions: {}, corePlugins: [], credentialStore: '', credentialCleanupPending: false }).models, selected: identifier(v.selected) }
  }
  async checkModel(modelId: string) {
    const v = object(await this.json('/v1/models/check', { modelId: identifier(modelId) }))
    if (v.valid !== true || v.networkChecked !== false || v.requestSent !== false) throw new ApiError('INVALID_RESPONSE')
    return v
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
  async submit(profile: string, goal: string, requestId: string, modelId = '') { return task(await this.json('/v1/tasks', { profile, goal, requestId, ...(modelId ? { modelId } : {}) })) }
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

function encodedPath(path:string) {if(!path||path.length>512)throw new ApiError('INVALID_RESPONSE');return btoa(String.fromCharCode(...new TextEncoder().encode(path))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'')}
export interface ContentPage {
  text:string;offset:number;nextOffset:number;more:boolean;totalCharacters:number;contentSha256:string;startLine:number
  source:string;sanitized:true;sessionId?:string;eventId?:string;artifactId?:string;artifactSha256?:string;path?:string;readAt?:string;fileVersion?:string
}
export function contentPage(value:unknown):ContentPage {
  const v=object(value)
  if(v.sanitized!==true||typeof v.more!=='boolean'||!['current-workspace','recorded-artifact','recorded-event'].includes(String(v.source))||!Number.isSafeInteger(v.startLine)||(v.startLine as number)<1)throw new ApiError('INVALID_RESPONSE')
  for(const k of ['offset','nextOffset','totalCharacters'])if(!Number.isSafeInteger(v[k])||(v[k] as number)<0||(v[k] as number)>8388608)throw new ApiError('INVALID_RESPONSE')
  const body=text(v.text,16384)
  if((v.nextOffset as number)!==(v.offset as number)+body.length||(v.nextOffset as number)>(v.totalCharacters as number)||v.more!==((v.nextOffset as number)<(v.totalCharacters as number))||v.more&&body.length===0||!/^[a-f0-9]{64}$/.test(String(v.contentSha256)))throw new ApiError('INVALID_RESPONSE')
  return {...v,text:body} as unknown as ContentPage
}
