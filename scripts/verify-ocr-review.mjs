// Validate the review ledger and render its plan; never classifies an opinion.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dir = path.join(root, 'docs/development/ocr-verification-2026-10-20');
const read = name => JSON.parse(fs.readFileSync(path.join(dir, name), 'utf8'));
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const assert = (condition, message) => { if (!condition) throw new Error(message); };
const count = (rows, field) => rows.reduce((out, row) => {
  out[row[field]] = (out[row[field]] || 0) + 1;
  return out;
}, {});
const original = read('opinions.json');
assert(original.opinions.length === 2457, 'Expected exactly 2457 original opinions');
const originals = new Map(original.opinions.map(row => [row.id, row]));
assert(originals.size === 2457, 'Duplicate original ID');
for (let i = 1; i <= 2457; i++) assert(originals.has(`OCR-${String(i).padStart(4, '0')}`), `Missing original ${i}`);
assert(original.opinions.filter(row => row.sourceExport === 'ocr-c1.json').length === 2426, 'First export count');
assert(original.opinions.filter(row => row.sourceExport === 'ocr-c2.json').length === 31, 'Retry export count');
const snapshot = read('source-snapshot.json');
assert(Object.keys(snapshot.sourceSha256).length === 569 && snapshot.missing.length === 0, 'Source snapshot coverage');
const verdicts = new Map();
for (const group of ['core', 'docs', 'application', 'delivery']) {
  for (const row of read(`verdicts-${group}.json`)) {
    assert(originals.has(row.id) && !verdicts.has(row.id), `Unknown or duplicate verdict ${row.id}`);
    verdicts.set(row.id, { ...row, reviewModule: group });
  }
}
const extraFile = path.join(dir, 'verdicts-delivery-extra.json');
if (fs.existsSync(extraFile)) for (const row of read('verdicts-delivery-extra.json')) {
  assert(verdicts.has(row.id), `Unknown extra verdict ${row.id}`);
  const previous = verdicts.get(row.id);
  const { reviewModule, ...completed } = previous;
  assert(previous.status === 'unverified' || JSON.stringify(completed) === JSON.stringify(row), `Override conflicts with completed review ${row.id}`);
  verdicts.set(row.id, { ...row, reviewModule: 'delivery' });
}
assert(verdicts.size === 2457, `Verdict coverage ${verdicts.size}`);
for (const row of verdicts.values()) {
  assert(['accepted', 'partial', 'rejected', 'unverified'].includes(row.status), `Invalid status ${row.id}`);
  assert(typeof row.reason === 'string' && row.reason.length >= 20, `Missing reason ${row.id}`);
  assert(Array.isArray(row.evidence) && row.evidence.length > 0, `Missing evidence ${row.id}`);
  for (const ref of row.evidence) {
    if (/^https?:\/\//.test(ref)) continue;
    const name = ref.split(':', 1)[0];
    assert(fs.existsSync(path.join(root, name)), `Evidence path missing for ${row.id}: ${ref}`);
  }
  if (['accepted', 'partial'].includes(row.status)) {
    assert(typeof row.repairGroup === 'string' && row.repairGroup.length > 0, `No repair group ${row.id}`);
    assert(['P0', 'P1', 'P2', 'P3'].includes(row.priority), `No priority ${row.id}`);
  }
}
assert(![...verdicts.values()].some(row => row.status === 'unverified'), 'Review remains incomplete: unverified opinions exist');
const structure = read('plan-structure.json');
const phases = new Set(structure.phases.map(row => row.id));
assert(phases.size === 7 && structure.phases.length === 7, 'Expected seven unique phases');
assert(new Set(structure.tasks.map(row => row.id)).size === structure.tasks.length, 'Duplicate task ID');
const membership = new Map();
for (const task of structure.tasks) {
  assert(phases.has(task.phase), `Invalid phase ${task.id}`);
  assert(task.change && task.validation && task.status === 'NOT_STARTED', `Incomplete task ${task.id}`);
  for (const group of task.sourceGroups) {
    assert(!membership.has(group), `Repair group assigned twice: ${group}`);
    membership.set(group, task.id);
  }
}
const tasks = structure.tasks.map(task => ({ ...task, opinionIds: [] }));
const taskById = new Map(tasks.map(task => [task.id, task]));
const rows = [...verdicts.values()].sort((a, b) => a.id.localeCompare(b.id)).map(row => {
  if (!['accepted', 'partial'].includes(row.status)) return { ...row, taskId: null, phase: null };
  const taskId = membership.get(row.repairGroup);
  assert(taskId, `Unmapped repair group ${row.repairGroup} (${row.id})`);
  const task = taskById.get(taskId);
  task.opinionIds.push(row.id);
  return { ...row, taskId, phase: task.phase };
});
const activeTasks = tasks.filter(task => task.opinionIds.length > 0);
for (const task of activeTasks) {
  task.priority = task.opinionIds.map(id => verdicts.get(id).priority).sort()[0];
}
const plan = { baseline: original.baseline, phases: structure.phases, tasks: activeTasks };
const counts = count(rows, 'status');
const result = {
  status: 'STATIC_REVIEW_COMPLETE_PLAN_ONLY',
  baseline: original.baseline,
  originalOpinions: 2457,
  opinionBearingFiles: 569,
  reportedScannedFiles: 658,
  scannedFilesEvidence: 'User reported full scan with 7 retry files; original opinion exports count findings, not zero-finding file coverage.',
  verdictCounts: counts,
  admittedOpinions: (counts.accepted || 0) + (counts.partial || 0),
  workPackages: activeTasks.length,
  phases: structure.phases.map(phase => ({ ...phase,
    opinions: rows.filter(row => row.phase === phase.id).length,
    tasks: activeTasks.filter(task => task.phase === phase.id).map(task => task.id) })),
  originalSeverity: count(original.opinions, 'severity'),
  criticalVerdicts: original.opinions.filter(row => row.severity === 'critical').map(row => {
    const verdict = rows.find(item => item.id === row.id);
    return { id: row.id, status: verdict.status, priority: verdict.priority, taskId: verdict.taskId };
  }),
  methodology: 'All original claims received individual static source/contract/test review. Accepted means supported scope; partial reason limits adoption. No claim that every scenario was dynamically reproduced.',
  productChangesImplemented: false,
  productFullSuiteRun: false,
  remotePushOrCIExecuted: false,
  m1059Closed: false,
  m11Eligible: false,
  inputs: Object.fromEntries(['opinions.json', 'source-snapshot.json', 'verdicts-core.json', 'verdicts-docs.json', 'verdicts-application.json', 'verdicts-delivery.json', ...(fs.existsSync(extraFile) ? ['verdicts-delivery-extra.json'] : []), 'plan-structure.json'].map(name => [name, hash(fs.readFileSync(path.join(dir, name)))])),
};
if (process.argv.includes('--write')) {
  fs.writeFileSync(path.join(dir, 'review.json'), JSON.stringify({ ...result, verdicts: rows }, null, 2) + '\n');
  fs.writeFileSync(path.join(dir, 'plan.json'), JSON.stringify(plan, null, 2) + '\n');
  fs.writeFileSync(path.join(root, 'docs/testing/OCR-verification-2026-10-20-results.json'), JSON.stringify(result, null, 2) + '\n');
  const labels = { accepted: '采纳', partial: '部分采纳', rejected: '不采纳', unverified: '待取证' };
  for (const module of ['core', 'docs', 'application', 'delivery']) {
    const text = [`# OCR逐条核实：${module}`, '', '原文及建议代码见 opinions.json；这里只以稳定编号连接原意见，部分采纳以“判断”限定范围。所有修复均未实施。', ''];
    for (const row of rows.filter(row => row.reviewModule === module)) {
      const opinion = originals.get(row.id);
      text.push(`## ${row.id} · ${labels[row.status]}`, '', `- 位置：${opinion.path}:${opinion.start_line}；OCR原严重度：${opinion.severity}`, `- 计划：${row.taskId || '无'}${row.priority ? ` / ${row.priority}` : ''}`, `- 原意见：${opinion.content.replace(/\r?\n/g, ' ')}`, `- 判断：${row.reason}`, `- 证据：${row.evidence.join('；')}`, '');
    }
    fs.writeFileSync(path.join(dir, `ledger-${module}.md`), text.map(line => line.trimEnd()).join('\n').trimEnd() + '\n');
  }
  const text = ['# OCR 意见分阶段修复计划', '', '日期：2026-10-20（Asia/Shanghai）。仅规划，产品修复与新验收均未执行。', '', `2457 条逐条静态核实：采纳 ${counts.accepted}、部分采纳 ${counts.partial}、不采纳 ${counts.rejected}；待取证 ${counts.unverified || 0}。${result.admittedOpinions} 条采纳范围合并为 ${activeTasks.length} 个工作包。部分采纳不代表整条原意见或原建议代码正确。`, '', '核实方法、证据边界和完整索引见 [审查报告](../development/code-review-2026-10-20.md)。机器可读完整映射见 [plan.json](../development/ocr-verification-2026-10-20/plan.json)；任何未分配采纳项都会被验证器拒绝。', '', '## 顺序与执行约束', '', 'R0 → R1 → R2 → R3 → R4 → R5 → R6。同阶段不同工作包可在契约边界明确后并行，跨模块改动必须整合验证；测试补强随相应产品修复交付，不能等到R6才检查。每个工作包先核当前Git/源码和已有契约，补命中原问题的反例，实施最小修复，再专项→适用全量→同制品证据→文档/本地提交。', '', 'R0中的测试任务跨越各阶段：先建立基线，再按计划分支持续补回归；不要求先把全部测试重写才修P1。阶段编号是本轮治理编号，不替代M10.5.9/M11/M12产品编号。各阶段当前均未开始实施，P0/P1是核实优先级而非已发生事故结论。', '', '不得自动重放UNKNOWN、吞必需持久化失败、通过跳过测试/放宽限额制造通过、把临时流当执行证据、复写历史验收JSON或改参考项目。共享Java插件是可信扩展，不扩张成抵御恶意进程内代码的沙箱承诺。用户已批准的同源直接进入保持。', ''];
  for (const phase of structure.phases) {
    text.push(`## ${phase.id} ${phase.title}`, '', `前置：${phase.dependsOn.join('、') || '本轮核实记录完成'}。退出条件：${phase.exit}`, '');
    for (const task of activeTasks.filter(task => task.phase === phase.id)) {
      text.push(`### ${task.id} ${task.title}`, '', `状态：未实施。优先级：${task.priority}。关联 ${task.opinionIds.length} 条意见。`, '', `改动：${task.change}`, '', `验收：${task.validation}`, '', `追溯：${task.opinionIds.join('、')}`, '');
    }
  }
  text.push('## 最终收口与权限边界', '', '逐项修复后新源码必须重新核验最终JAR/包内UI、Java与客户端/SDK、真实浏览器、Windows原生与候选安装/审批/取消/显式恢复；依据实际变更更新依赖/秘密扫描和制品清单。三平台/三引擎严格CI仍按M10.5.9契约执行；尚未授权推送/执行时只记本地部分结果，不能收口或进入M11。已有本地Git提交授权继续适用，不含推送、远端发布或付费模型调用。', '', '不以意见采纳数、测试数或完成百分比代替需求验收。全部已完成/已修复标记必须有对应实测证据。');
  fs.writeFileSync(path.join(root, 'docs/requirements/OCR-remediation-plan-2026-10-20.md'), text.join('\n') + '\n');
} else {
  const savedReview = read('review.json');
  assert(JSON.stringify(savedReview) === JSON.stringify({ ...result, verdicts: rows }), 'Generated review is stale; use --write');
  assert(JSON.stringify(read('plan.json')) === JSON.stringify(plan), 'Generated plan is stale; use --write');
  const savedResult = JSON.parse(fs.readFileSync(path.join(root, 'docs/testing/OCR-verification-2026-10-20-results.json'), 'utf8'));
  assert(JSON.stringify(savedResult) === JSON.stringify(result), 'Generated statistics are stale; use --write');
}
console.log(JSON.stringify({ verdicts: rows.length, counts, workPackages: activeTasks.length, phaseOpinionCounts: Object.fromEntries(structure.phases.map(phase => [phase.id, rows.filter(row => row.phase === phase.id).length])) }));
