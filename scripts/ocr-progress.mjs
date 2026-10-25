// Current implementation metadata; the original opinion/assignment snapshots stay separate.
export const taskStatuses = new Set([
  'NOT_STARTED', 'IN_PROGRESS', 'BASELINE_VALIDATED',
  'BASELINE_VALIDATED_PIPELINE_PENDING', 'BASELINE_VALIDATED_FOLLOWUPS_OPEN',
  'BASELINE_GAPS_CONFIRMED', 'BASELINE_FOLLOWUPS_OPEN',
  'IMPLEMENTED_LOCAL_VALIDATED', 'LOCAL_VALIDATED_PIPELINE_PENDING', 'COMPLETE',
])
const phaseStatuses = new Set([
  'LOCAL_BASELINE_READY', 'LOCAL_BASELINE_WITH_CONFIRMED_GAPS',
  'IMPLEMENTATION_IN_PROGRESS', 'LOCAL_VALIDATED_CI_PENDING', 'COMPLETE',
])
const snapshotStatuses = new Set(['LOCAL_IMPLEMENTATION_VALIDATED_CI_PENDING', 'LOCAL_VALIDATED_CI_PENDING'])
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value)
const requireThat = (ok, message) => { if (!ok) throw Error(message) }

export function validateProgress(progress, knownTaskIds) {
  requireThat(object(progress), 'Implementation progress must be an object')
  requireThat(typeof progress.updatedAt === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(progress.updatedAt), 'Invalid implementation update date')
  requireThat(phaseStatuses.has(progress.status), 'Invalid implementation phase status')
  requireThat(object(progress.tasks) && Object.keys(progress.tasks).length > 0, 'Implementation tasks must be a nonempty object')
  const known = new Set(knownTaskIds)
  for (const [id, row] of Object.entries(progress.tasks)) {
    requireThat(known.has(id), `Unknown implementation task ${id}`)
    requireThat(object(row) && taskStatuses.has(row.status), `Invalid implementation task status ${id}`)
    requireThat(typeof row.description === 'string' && row.description.trim().length > 0 && row.description.length <= 2000,
      `Invalid implementation description ${id}`)
  }
  for (const id of ['r1', 'r2', 'r3']) {
    if (!(id in progress)) continue
    const row = progress[id]
    requireThat(object(row) && snapshotStatuses.has(row.status), `Invalid snapshot status ${id}`)
    requireThat(typeof row.runId === 'string' && /^[A-Za-z0-9_.-]{1,128}$/.test(row.runId), `Invalid snapshot runId ${id}`)
    for (const key of ['adoptedScopes', 'implemented', 'contractClarified', 'existingGuardConfirmed'])
      requireThat(Number.isSafeInteger(row[key]) && row[key] >= 0, `Invalid snapshot count ${id}.${key}`)
    requireThat(row.implemented + row.contractClarified + row.existingGuardConfirmed === row.adoptedScopes, `Snapshot count mismatch ${id}`)
    for (const key of ['implementationLedger', 'results'])
      requireThat(typeof row[key] === 'string' && /^docs\/(development|testing)\/[A-Za-z0-9_.-]+\.json$/.test(row[key]), `Invalid snapshot evidence ${id}.${key}`)
    requireThat(typeof row.nextPhase === 'string' && /^R[0-6]$/.test(row.nextPhase), `Invalid snapshot nextPhase ${id}`)
    if ('implementedOrRegressionStrengthened' in row)
      requireThat(row.implementedOrRegressionStrengthened === row.implemented, `Snapshot alias mismatch ${id}`)
  }
  return progress
}

export function stateDescription(row) {
  return row ? row.description.replace(/[\r\n\u2028\u2029]+/g, ' ').trim() : '未实施'
}

export function implementationArtifacts({ progressPath, progressBytes, assignmentPath, assignmentBytes, markdownPath, markdown, hash }) {
  return {
    schemaVersion: 1,
    scope: 'Current implementation artifacts only; original review conclusions are unchanged',
    inputs: { [progressPath]: hash(progressBytes), [assignmentPath]: hash(assignmentBytes) },
    outputs: { [markdownPath]: hash(markdown) },
  }
}
