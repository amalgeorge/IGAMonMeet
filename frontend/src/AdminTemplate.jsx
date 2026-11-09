import { useEffect, useState } from 'react';
import { api } from './api.js';

const KINDS = { SUMMARY: 'IAM_SUMMARY', KPI: 'IAM_KPI', CHART: 'IAM_CHART', TABLE: 'IAM_TABLE', TWO_COLUMN: 'IAM_TWO_COLUMN', ACTION_ITEMS: 'IAM_TABLE' };
const KIND_LABEL = { SUMMARY: 'Summary', KPI: 'KPIs', CHART: 'KPI trend chart', TABLE: 'Table', TWO_COLUMN: 'Two columns', ACTION_ITEMS: 'Action items' };
const TEAMS = { ENGINEERING: 'Engineering', OPERATIONS: 'Operations', SHARED: 'Shared' };
const OVERFLOW = { CONTINUE: 'Continue on new slides', SHRINK_TO_FIT: 'Shrink, then continue', APPENDIX: 'First rows + appendix' };
const fmt = new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Singapore' });
const pick = (d) => (d ? { name: d.name, unit: d.unit, target: d.target, direction: d.direction, amberThreshold: d.amberThreshold } : {});
const slug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 60) || 'section';

export default function AdminTemplate() {
  const [base, setBase] = useState(null);
  const [sections, setSections] = useState([]);
  const [groups, setGroups] = useState([]);
  const [library, setLibrary] = useState([]);
  const [note, setNote] = useState('');
  const [versions, setVersions] = useState([]);
  const [msg, setMsg] = useState({});

  const load = () => {
    api('/api/templates/current').then((t) => { setBase(t.versionNo); setSections(t.sections); setGroups(t.groups || []); setLibrary(t.kpiLibrary || []); });
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
      kpis: kind === 'KPI' && library.length ? [{ key: library[0].key, required: false, visibility: 'SHOWN' }] : [] }]);
  };
  const allKpis = library;
  // Each placement carries a copy of its library definition, which the server also takes from the library.
  const withDefs = () => sections.map((s) => ({ ...s, kpis: (s.kpis || []).map((k) => ({ ...library.find((l) => l.key === k.key), ...k,
    ...pick(library.find((l) => l.key === k.key)) })) }));

  const publish = () => {
    setMsg({});
    api('/api/admin/templates', { method: 'POST', json: { basedOnVersion: base, note, sections: withDefs(), groups, kpiLibrary: library } })
      .then((v) => { setMsg({ ok: `Published version ${v.versionNo}. New meetings use it; existing meetings keep their version.` }); setNote(''); load(); })
      .catch((e) => setMsg({ error: e.message }));
  };

  if (base == null) return null;
  return (
    <section>
      <div className="row"><h1>Template <span className="hint">editing from version {base}</span></h1></div>
      {msg.error && <p className="error" role="alert">{msg.error}</p>}
      {msg.ok && <p className="notice" role="status">{msg.ok}</p>}
      <GroupsEditor groups={groups} onChange={setGroups} onRemove={(key) => setSections(sections.map((s) => (s.groupKey === key ? { ...s, groupKey: null } : s)))} />
      <KpiLibrary library={library} onChange={setLibrary} onRemove={(key) => setSections(sections.map((s) => ({ ...s, kpis: (s.kpis || []).filter((k) => k.key !== key) })))} />
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
              <label className="field"><span>Group</span><select value={s.groupKey || ''} onChange={(e) => update(i, { groupKey: e.target.value || null })}>
                <option value="">No group</option>{groups.map((g) => <option key={g.key} value={g.key}>{g.name}</option>)}</select></label>
              <label className="field"><span>Visibility</span><select value={s.visibility || 'SHOWN'} onChange={(e) => update(i, { visibility: e.target.value })}>
                {Object.entries(VISIBILITY).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
              <label className="check"><input type="checkbox" checked={s.required} onChange={(e) => update(i, { required: e.target.checked })} /> Required</label>
              <label className="check"><input type="checkbox" checked={s.onSlides !== false} onChange={(e) => update(i, { onSlides: e.target.checked })} /> On slides</label>
            </div>
            {(s.visibility || 'SHOWN') !== 'SHOWN' && <p className="hint">{VISIBILITY_HINT[s.visibility]}</p>}
            {s.kind === 'TABLE' && (
              <label className="field"><span>Columns (comma-separated)</span>
                <input value={s.columns.join(', ')} onChange={(e) => update(i, { columns: e.target.value.split(',').map((c) => c.trim()).filter(Boolean) })} /></label>
            )}
            {s.kind === 'CHART' && (
              <label className="field"><span>KPI shown</span><select value={s.chartKpiKey || ''} onChange={(e) => update(i, { chartKpiKey: e.target.value })}>
                <option value="">Choose a KPI</option>{allKpis.map((k) => <option key={k.key} value={k.key}>{k.name}</option>)}</select></label>
            )}
            {s.kind === 'KPI' && <KpiEditor kpis={s.kpis} library={library} onChange={(kpis) => update(i, { kpis })} />}
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

const VISIBILITY = { SHOWN: 'Shown', AVAILABLE: 'Available', RETIRED: 'Retired' };
const VISIBILITY_HINT = {
  AVAILABLE: 'Hidden in new Meetings until an editor switches it on for that Meeting.',
  RETIRED: 'Hidden from new Meetings. Past Meetings and trend history are kept.',
};

function GroupsEditor({ groups, onChange, onRemove }) {
  const set = (i, patch) => onChange(groups.map((g, j) => (j === i ? { ...g, ...patch } : g)));
  const add = () => {
    let n = groups.length + 1; let key = `group-${n}`;
    while (groups.some((g) => g.key === key)) key = `group-${++n}`;
    onChange([...groups, { key, name: 'New group', visibility: 'SHOWN', divider: true }]);
  };
  return (
    <div className="tpl-card">
      <div className="row"><strong>Section groups</strong><button className="btn" onClick={add}>Add group</button></div>
      <p className="hint">A group gathers neighbouring Sections under one heading, with an optional divider slide. Sections of a group must be next to each other.</p>
      {groups.length > 0 && <div className="table-wrap"><table>
        <thead><tr><th>Group</th><th>Visibility</th><th>Divider slide</th><th><span className="sr">Remove</span></th></tr></thead>
        <tbody>{groups.map((g, i) => (
          <tr key={g.key}>
            <td><input aria-label="Group name" value={g.name} onChange={(e) => set(i, { name: e.target.value })} /></td>
            <td><select aria-label="Group visibility" value={g.visibility || 'SHOWN'} onChange={(e) => set(i, { visibility: e.target.value })}>
              {Object.entries(VISIBILITY).map(([v, label]) => <option key={v} value={v}>{label}</option>)}</select></td>
            <td><input aria-label="Divider slide" type="checkbox" checked={g.divider !== false} onChange={(e) => set(i, { divider: e.target.checked })} /></td>
            <td><button className="btn" onClick={() => { onRemove(g.key); onChange(groups.filter((_, j) => j !== i)); }}>Remove</button></td>
          </tr>))}</tbody>
      </table></div>}
    </div>
  );
}

function KpiLibrary({ library, onChange, onRemove }) {
  const set = (i, patch) => onChange(library.map((k, j) => (j === i ? { ...k, ...patch } : k)));
  const add = () => {
    let key = 'new-kpi'; let n = 2;
    while (library.some((k) => k.key === key)) key = `new-kpi-${n++}`;
    onChange([...library, { key, name: 'New KPI', unit: '%', target: 100, direction: 'HIGHER', amberThreshold: 90, isNew: true }]);
  };
  return (
    <div className="tpl-card">
      <div className="row"><strong>KPI library</strong><button className="btn" onClick={add}>Add KPI</button></div>
      <p className="hint">Define each KPI once. KPI Sections pick from this list, and a KPI keeps one trend history wherever it is placed.</p>
      <div className="table-wrap"><table>
        <thead><tr><th>KPI</th><th>Key</th><th>Unit</th><th>Better when</th><th>Target</th><th>Amber from</th><th><span className="sr">Remove</span></th></tr></thead>
        <tbody>{library.map((k, i) => (
          <tr key={i}>
            <td><input aria-label="KPI name" value={k.name} onChange={(e) => set(i, { name: e.target.value })} /></td>
            <td>{k.isNew ? <input aria-label="KPI key" size={12} value={k.key} onChange={(e) => set(i, { key: slug(e.target.value) })} /> : <code>{k.key}</code>}</td>
            <td><input aria-label="Unit" size={4} value={k.unit} onChange={(e) => set(i, { unit: e.target.value })} /></td>
            <td><select aria-label="Better when" value={k.direction} onChange={(e) => set(i, { direction: e.target.value })}><option value="HIGHER">Higher</option><option value="LOWER">Lower</option></select></td>
            <td><input aria-label="Target" type="number" step="any" value={k.target} onChange={(e) => set(i, { target: e.target.value })} /></td>
            <td><input aria-label="Amber threshold" type="number" step="any" value={k.amberThreshold} onChange={(e) => set(i, { amberThreshold: e.target.value })} /></td>
            <td><button className="btn" onClick={() => { onRemove(k.key); onChange(library.filter((_, j) => j !== i)); }}>Remove</button></td>
          </tr>
        ))}</tbody>
      </table></div>
    </div>
  );
}

/** A KPI Section's placements: which library KPIs it shows, and each one's Required flag and Visibility. */
function KpiEditor({ kpis, library, onChange }) {
  const set = (i, patch) => onChange(kpis.map((k, j) => (j === i ? { ...k, ...patch } : k)));
  const name = (key) => library.find((l) => l.key === key)?.name || key;
  const unplaced = library.filter((l) => !kpis.some((k) => k.key === l.key));
  return (
    <div className="table-wrap"><table>
      <thead><tr><th>KPI</th><th>Required</th><th>Visibility</th><th><span className="sr">Remove</span></th></tr></thead>
      <tbody>{kpis.map((k, i) => (
        <tr key={k.key}>
          <td>{name(k.key)}</td>
          <td><input aria-label="Required" type="checkbox" checked={k.required} onChange={(e) => set(i, { required: e.target.checked })} /></td>
          <td><select aria-label="Visibility" value={k.visibility || 'SHOWN'} onChange={(e) => set(i, { visibility: e.target.value })}>
            {Object.entries(VISIBILITY).map(([v, label]) => <option key={v} value={v}>{label}</option>)}</select></td>
          <td><button className="btn" onClick={() => onChange(kpis.filter((_, j) => j !== i))}>Remove</button></td>
        </tr>
      ))}</tbody>
      {unplaced.length > 0 && <tfoot><tr><td colSpan={4}>
        <select aria-label="Add a KPI from the library" value="" onChange={(e) => e.target.value && onChange([...kpis, { key: e.target.value, required: false, visibility: 'SHOWN' }])}>
          <option value="">Add a KPI from the library…</option>{unplaced.map((l) => <option key={l.key} value={l.key}>{l.name}</option>)}</select>
      </td></tr></tfoot>}
    </table></div>
  );
}
