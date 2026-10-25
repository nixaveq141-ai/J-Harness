// Run every owned cleanup. Secondary failures must not replace a pending test failure.
export async function cleanupFixture(primary, actions, diagnose = () => console.error('FIXTURE_CLEANUP_UNKNOWN')) {
  const failures = []
  for (const action of actions) {
    try { await action() } catch (error) { failures.push(error) }
  }
  if (!failures.length) return
  if (primary != null) {
    try { primary.suppressed = [...(Array.isArray(primary.suppressed) ? primary.suppressed : []), ...failures] } catch { /* frozen primary still wins */ }
    try { diagnose() } catch { /* diagnostic cannot replace the primary */ }
    return
  }
  throw new AggregateError(failures, 'Fixture cleanup failed')
}
