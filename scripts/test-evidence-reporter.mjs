// Machine-readable terminal outcomes alongside the normal TAP diagnostic log.
export default async function* reporter(events) {
  const parents = []
  for await (const { type, data } of events) {
    if (type === 'test:start') parents[data.nesting] = data.name
    if (type === 'test:pass' || type === 'test:fail') {
      if (data.details?.type === 'suite') continue
      const caseId = [...parents.slice(0, data.nesting), data.name].join(' > ')
      yield JSON.stringify({ type: 'case', caseId,
        outcome: data.skip ? 'SKIP' : data.todo ? 'TODO' : type === 'test:pass' ? 'PASS' : 'FAIL' }) + '\n'
    }
    if (type === 'test:summary' && data.file === undefined) {
      yield JSON.stringify({ type: 'summary', counts: data.counts }) + '\n'
    }
  }
}
