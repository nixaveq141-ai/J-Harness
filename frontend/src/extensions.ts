export type ExtensionKind = 'mcp' | 'skills' | 'skillRoots' | 'subagents'
type Row = Record<string, unknown>
export const isObject = (v: unknown): v is Row => !!v && typeof v === 'object' && !Array.isArray(v)
export const idLimit = (kind: ExtensionKind) => kind === 'subagents' ? 32 : kind === 'skills' ? 24 : 16
const tools = ['list_directory', 'read_file', 'grep', 'search_files', 'read_artifact']
const text = (v: unknown) => typeof v === 'string' && !!v.trim() && v.length <= 8192
const strings = (v: unknown): v is string[] => Array.isArray(v) && v.length <= 64 && v.every(x => typeof x === 'string')
const relative = (v: unknown) => text(v) && !/^[\\/]|:|[\u0000-\u001f\u007f]/.test(v as string) && !(v as string).split(/[\\/]/).includes('..')
const integer = (v: unknown, max: number) => typeof v === 'number' && Number.isSafeInteger(v) && v >= 1 && v <= max

/** Local feedback for the editable host fields. Filesystem and authority checks remain server-owned. */
export function extensionErrors(kind: ExtensionKind, value: unknown): string[] {
  const errors: string[] = []
  let rows: Row[]
  if (kind === 'subagents') {
    if (!isObject(value) || !Object.values(value).every(isObject)) return ['只读角色必须是以 ID 为键的对象。']
    rows = Object.entries(value).map(([id, row]) => ({ ...row as Row, id }))
  } else {
    if (!Array.isArray(value) || !value.every(isObject)) return ['扩展配置必须是对象数组。']
    rows = value
  }
  const limit = kind === 'subagents' ? 16 : 8, maxId = idLimit(kind), seen = new Set<unknown>()
  if (rows.length > limit) errors.push(`最多配置 ${limit} 条。`)
  for (const [index, row] of rows.entries()) {
    const prefix = `第 ${index + 1} 条：`, add = (message: string) => errors.push(prefix + message)
    if (typeof row.id !== 'string' || !new RegExp(`^[A-Za-z0-9_-]{1,${maxId}}$`).test(row.id) || seen.has(row.id)) add(`ID 须为1～${maxId}个字母、数字、下划线或短横线，同类不能重复。`)
    seen.add(row.id)
    const allowed = kind === 'mcp' ? ['id','transport','command','cwd','url','tokenEnv','timeoutSeconds','effects'] : kind === 'skills' ? ['id','directory','resources','scripts'] : kind === 'skillRoots' ? ['id','kind','directory'] : ['id','prompt','tools','requireRead','maxSteps','durationSeconds']
    if (Object.keys(row).some(key => !allowed.includes(key))) add('存在宿主不支持的字段，请修正高级 JSON。')
    const number = (key: string, fallback: number, max: number) => { if (!integer(row[key] === undefined ? fallback : row[key], max)) add(`${key} 须为1～${max}的整数。`) }
    if (kind === 'mcp') {
      number('timeoutSeconds', 15, 60)
      if (row.transport === 'stdio') {
        if (!strings(row.command) || !row.command.length) add('启动参数须为非空文字数组，每行一个参数，保留原始空格。')
        if (!relative(row.cwd === undefined ? '.' : row.cwd)) add('工作目录须为工作区相对路径。')
        if ('url' in row || 'tokenEnv' in row) add('stdio 不接受 URL 或凭据环境变量字段。')
      } else if (row.transport === 'http') {
        try {
          if (!text(row.url) || !/^https?:\/\//.test(row.url as string)) throw Error()
          const url = new URL(row.url as string)
          // Check the authored authority too: URL canonicalization must not turn an alias into loopback.
          const authority = (row.url as string).split('/')[2]
          if (url.username || url.password || (row.url as string).includes('#') || url.protocol === 'http:' && !/^(localhost|127\.0\.0\.1|\[::1\])(?::[0-9]+)?$/.test(authority)) throw Error()
        } catch { add('服务 URL 须为 HTTPS 或 localhost、127.0.0.1、[::1] 的 HTTP，不能包含用户凭据或片段。') }
        if ('command' in row || 'cwd' in row) add('HTTP 不接受启动参数或工作目录字段。')
        if (row.tokenEnv !== undefined && (typeof row.tokenEnv !== 'string' || !/^[A-Za-z_][A-Za-z0-9_]*$/.test(row.tokenEnv))) add('凭据环境变量须为合法变量名。')
      } else add('传输方式须为 http 或 stdio。')
      if (row.effects !== undefined && (!isObject(row.effects) || Object.values(row.effects).some(v => !['READ_ONLY','WORKSPACE_WRITE','SYSTEM_IMPACT'].includes(v as string)))) add('工具影响声明类型或取值无效。')
    }
    if (kind === 'skills') {
      if (typeof row.id === 'string' && ['help','new','exit','quit','cancel','clear','compact','model','settings','plan','status','resume'].includes(row.id.toLowerCase())) add('技能 ID 与内置命令冲突。')
      if (!relative(row.directory)) add('技能目录须为工作区相对路径。')
      for (const key of ['resources','scripts'] as const) {
        const mapping = row[key] === undefined ? {} : row[key]
        if (!isObject(mapping)) { add(`${key} 须为对象；原值保留，请修正高级 JSON。`); continue }
        if (Object.keys(mapping).length > (key === 'scripts' ? 16 : 32)) add(`${key} 条目过多。`)
        for (const [name, entry] of Object.entries(mapping)) {
          if (!/^[A-Za-z0-9_-]{1,24}$/.test(name)) add(`${key} 名称须为1～24个字母、数字、下划线或短横线。`)
          if (key === 'resources') { if (!relative(entry)) add('资源路径须为工作区相对路径。') }
          else if (!isObject(entry) || Object.keys(entry).some(k => !['path','mode','timeoutSeconds'].includes(k)) || !relative(entry.path) || !['SH','CMD','POWERSHELL'].includes(entry.mode as string) || !integer(entry.timeoutSeconds === undefined ? 10 : entry.timeoutSeconds, 60)) add('脚本须包含相对路径、SH/CMD/POWERSHELL 模式及1～60秒整数超时。')
        }
      }
    }
    if (kind === 'skillRoots') {
      if (!['project','user'].includes(row.kind as string) || !text(row.directory)) add('技能根来源或目录无效。')
      else if (row.kind === 'project' && !relative(row.directory)) add('项目技能根须为工作区相对路径。')
      // Absolute-path syntax and normalization depend on the host OS and are checked there.
    }
    if (kind === 'subagents') {
      if (row.prompt !== undefined && !text(row.prompt)) add('角色说明须为非空文字。')
      const selected = row.tools === undefined ? ['read_file'] : row.tools
      if (!strings(selected) || selected.some(v => !tools.includes(v))) add('工具须为允许的只读工具数组。')
      if (!strings(row.requireRead) || !row.requireRead.length || row.requireRead.some(v => !v.trim()) || !Array.isArray(selected) || !selected.includes('read_file')) add('至少填写一个必读文件，并允许 read_file。')
      number('maxSteps', 4, 64); number('durationSeconds', 30, 300)
    }
  }
  return errors
}

export function freshExtensionId(prefix: string, existing: unknown[]): string {
  for (let index = 1; index <= existing.length + 1; index++) {
    const id = `${prefix}-${index}`
    if (!existing.includes(id)) return id
  }
  throw Error('ID allocation failed')
}
