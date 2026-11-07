import { useEffect, useState } from 'react';
import { api } from './api.js';

const KINDS = { SUMMARY: 'IAM_SUMMARY', KPI: 'IAM_KPI', CHART: 'IAM_CHART', TABLE: 'IAM_TABLE', TWO_COLUMN: 'IAM_TWO_COLUMN', ACTION_ITEMS: 'IAM_TABLE' };
const KIND_LABEL = { SUMMARY: 'Summary', KPI: 'KPIs', CHART: 'KPI trend chart', TABLE: 'Table', TWO_COLUMN: 'Two columns', ACTION_ITEMS: 'Action items' };
const TEAMS = { ENGINEERING: 'Engineering', OPERATIONS: 'Operations', SHARED: 'Shared' };
const OVERFLOW = { CONTINUE: 'Continue on new slides', SHRINK_TO_FIT: 'Shrink, then continue', APPENDIX: 'First rows + appendix' };
const fmt = new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Singapore' });
const slug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 60) || 'section';

export default function AdminTemplate() {
  const [base, setBase] = useState(null);
  const [sections, setSections] = useState([]);
  const [note, setNote] = useState('');
  const [versions, setVersions] = useState([]);
  const [msg, setMsg] = useState({});

  const load = () => {
    api('/api/templates/current').then((t) => { setBase(t.versionNo); setSections(t.sections); });
    api('/api/admin/templates').then(setVersions);
  };
  useEffect(() => { load(); }, []);

  const update = (i, patch) => setSections(sections.map((s, j) => (j === i ? { ...s, ...patch } : s)));
  const move = (i, d) => { const a = [...sections]; [a[i], a[i + d]] = [a[i + d], a[i]]; setSections(a); };
  const remove = (i) => setSections(sections.filter((_, j) => j !== i));
  const add = (kind) => {
    const name = `New ${KIND_LABEL[kind].toLowerCase()}`;
    let key = slug(name); let n = 2;
    while (sections.some((s) => s.key === key)) key = `${slug(name)}-${n++}`;
    setSections([...sections, { key, name, teamTag: 'SHARED', kind, layout: KINDS[kind], overflowRule: kind === 'SUMMARY' || kind === 'TWO_COLUMN' ? 'SHRINK_TO_FIT' : 'CONTINUE',
      required: false, columns: kind === 'TABLE' ? ['Item', 'Owner', 'Status'] : [], chartKpiKey: null,
      kpis: kind === 'KPI' ? [{ key: `kpi-${Date.now() % 100000}`, name: 'New KPI', unit: '%', target: 100, direction: 'HIGHER', amberThreshold: 90, required: false }] : [] }]);
  };
  const allKpis = sections.flatMap((s) => s.kpis || []);

  const publish = () => {
    setMsg({});
    api('/api/admin/templates', { method: 'POST', json: { basedOnVersion: base, note, sections } })
      .then((v) => { setMsg({ ok: `Published version ${v.versionNo}. New meetings use it; existing meetings keep their version.` }); setNote(''); load(); })
      .catch((e) => setMsg({ error: e.message }));
  };

  if (base == null) return null;
  return (
    <section>
      <div className="row"><h1>Template <span className="hint">editing from version {base}</span></h1></div>
      {msg.error && <p className="error" role="alert">{msg.error}</p>}
      {msg.ok && <p className="notice" role="status">{msg.ok}</p>}
      <ol className="tpl-list">
        {sections.map((s, i) => (
          <li key={s.key} className="tpl-card">
            <div className="row">
              <strong>{i + 1}. {KIND_LABEL[s.kind]}</strong>
              <span className="actions">
                <button className="btn" disabled={i === 0} onClick={() => move(i, -1)} aria-label={`Move ${s.name} up`}>↑</button>{' '}
                <button className="btn" disabled={i === sections.length - 1} onClick={() => move(i, 1)} aria-label={`Move ${s.name} down`}>↓</button>{' '}
                <button className="btn" onClick={() => remove(i)}>Remove</button>
              </span>
            </div>
            <div className="grid">
              <label className="field"><span>Name</span><input value={s.name} onChange={(e) => update(i, { name: e.target.value })} /></label>
              <label className="field"><span>Team</span><select value={s.teamTag} onChange={(e) => update(i, { teamTag: e.target.value })}>
                {Object.entries(TEAMS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
              <label className="field"><span>When content overflows</span><select value={s.overflowRule} onChange={(e) => update(i, { overflowRule: e.target.value })}>
                {Object.entries(OVERFLOW).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
              <label className="check"><input type="checkbox" checked={s.required} onChange={(e) => update(i, { required: e.target.checked })} /> Required</label>
            </div>
            {s.kind === 'TABLE' && (
              <label className="field"><span>Columns (comma-separated)</span>
                <input value={s.columns.join(', ')} onChange={(e) => update(i, { columns: e.target.value.split(',').map((c) => c.trim()).filter(Boolean) })} /></label>
            )}
            {s.kind === 'CHART' && (
              <label className="field"><span>KPI shown</span><select value={s.chartKpiKey || ''} onChange={(e) => update(i, { chartKpiKey: e.target.value })}>
                <option value="">Choose a KPI</option>{allKpis.map((k) => <option key={k.key} value={k.key}>{k.name}</option>)}</select></label>
            )}
            {s.kind === 'KPI' && <KpiEditor kpis={s.kpis} onChange={(kpis) => update(i, { kpis })} />}
          </li>
        ))}
      </ol>
      <div className="row">
        <span>Add section:{' '}{Object.keys(KINDS).map((k) => <button key={k} className="btn" onClick={() => add(k)}>{KIND_LABEL[k]}</button>)}</span>
      </div>
      <div className="row">
        <label className="field grow"><span>What changed (shown in version history)</span><input value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} /></label>
        <button className="btn pri" onClick={publish}>Publish as version {base + 1}</button>
      </div>
      <h2>Versions</h2>
      <div className="table-wrap"><table>
        <thead><tr><th>Version</th><th>Published</th><th>By</th><th>Sections</th><th>Note</th></tr></thead>
        <tbody>{versions.map((v) => (
          <tr key={v.versionNo}><td>{v.versionNo}</td><td className="tnum">{fmt.format(new Date(v.createdAt))}</td><td>{v.createdBy}</td><td>{v.sectionCount}</td><td>{v.note}</td></tr>
        ))}</tbody>
      </table></div>
    </section>
  );
}

function KpiEditor({ kpis, onChange }) {
  const set = (i, patch) => onChange(kpis.map((k, j) => (j === i ? { ...k, ...patch } : k)));
  return (
    <div className="table-wrap"><table>
      <thead><tr><th>KPI</th><th>Unit</th><th>Better when</th><th>Target</th><th>Amber from</th><th>Required</th><th><span className="sr">Remove</span></th></tr></thead>
      <tbody>{kpis.map((k, i) => (
        <tr key={k.key}>
          <td><input aria-label="KPI name" value={k.name} onChange={(e) => set(i, { name: e.target.value })} /></td>
          <td><input aria-label="Unit" size={4} value={k.unit} onChange={(e) => set(i, { unit: e.target.value })} /></td>
          <td><select aria-label="Better when" value={k.direction} onChange={(e) => set(i, { direction: e.target.value })}><option value="HIGHER">Higher</option><option value="LOWER">Lower</option></select></td>
          <td><input aria-label="Target" type="number" step="any" value={k.target} onChange={(e) => set(i, { target: e.target.value })} /></td>
          <td><input aria-label="Amber threshold" type="number" step="any" value={k.amberThreshold} onChange={(e) => set(i, { amberThreshold: e.target.value })} /></td>
          <td><input aria-label="Required" type="checkbox" checked={k.required} onChange={(e) => set(i, { required: e.target.checked })} /></td>
          <td><button className="btn" onClick={() => onChange(kpis.filter((_, j) => j !== i))}>Remove</button></td>
        </tr>
      ))}</tbody>
      <tfoot><tr><td colSpan={7}><button className="btn" onClick={() => onChange([...kpis, { key: `kpi-${Date.now() % 100000}`, name: 'New KPI', unit: '%', target: 100, direction: 'HIGHER', amberThreshold: 90, required: false }])}>Add KPI</button></td></tr></tfoot>
    </table></div>
  );
}
