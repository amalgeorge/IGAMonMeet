import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { api } from './api.js';
import { ActionItems, Comments } from './Collab.jsx';
import SlidesView from './SlidesView.jsx';
import ExportPanel from './ExportPanel.jsx';
import ImportPanel from './ImportPanel.jsx';
import HistoryPanel from './HistoryPanel.jsx';

const TEAM = { ENGINEERING: 'Engineering', OPERATIONS: 'Operations', SHARED: 'Shared' };
const LABEL = { PREPARING: 'Preparing', HELD: 'Held' };
const dateFmt = new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeZone: 'Asia/Singapore' });
const timeFmt = new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Singapore' });
const EMPTY = { SUMMARY: { points: [''], stats: [] }, KPI: { values: {}, commentary: '' }, CHART: { commentary: [''] }, TABLE: { rows: [] }, TWO_COLUMN: { leftHead: 'Risks', left: [''], rightHead: 'Decisions needed', right: [''] } };
const notesOf = (s) => { try { return s.contentJson ? JSON.parse(s.contentJson).notes : ''; } catch { return ''; } };
const parse = (s) => (s.contentJson ? JSON.parse(s.contentJson) : structuredClone(EMPTY[s.kind] || {}));

export default function MeetingDocument({ id, me, onBack }) {
  const [doc, setDoc] = useState(null);
  const [editing, setEditing] = useState(null);
  const [error, setError] = useState('');
  const [threads, setThreads] = useState([]);
  const [perSection, setPerSection] = useState({});
  const [tab, setTab] = useState('document');
  const load = () => {
    api(`/api/meetings/${id}`).then(setDoc);
    api(`/api/meetings/${id}/slides`).then((p) => setPerSection(p.slidesPerSection)).catch(() => {});
  };
  const loadThreads = () => api(`/api/meetings/${id}/comments`).then(setThreads);
  useEffect(() => { loadThreads(); }, [id]);
  useEffect(() => { load(); }, [id]);

  const startEdit = (s) => {
    setError('');
    api(`/api/meetings/${id}/sections/${s.id}/lock`, { method: 'POST' })
      .then(() => setEditing(s.id))
      .catch((e) => setError(e.message));
  };
  const stopEdit = (s) => api(`/api/meetings/${id}/sections/${s.id}/lock`, { method: 'DELETE' }).finally(() => { setEditing(null); load(); });

  if (!doc) return null;
  const m = doc.meeting;
  const pct = doc.requiredSections ? Math.round((doc.requiredFilled / doc.requiredSections) * 100) : 100;
  return (
    <section>
      <button className="link" onClick={onBack}>← Meetings</button>
      <div className="row">
        <h1>{m.title}</h1>
        <span className={`chip ${m.label.toLowerCase()}`}>{LABEL[m.label]}</span>
      </div>
      <p className="hint">Meeting {dateFmt.format(new Date(`${m.meetingDate}T00:00:00+08:00`))} · {doc.canEdit ? (m.editWindowOpen ? `Editable until ${timeFmt.format(new Date(m.editableUntil))}` : 'Edit window closed: Admin editing') : 'Read only: edit window closed'}</p>
      <nav className="seg" aria-label="View">
        {[['document', 'Document'], ['slides', 'Slides'], ['import', 'Import'], ['export', 'Export'], ['history', 'History']].map(([k, label]) => (
          <button key={k} className={tab === k ? 'on' : ''} aria-current={tab === k ? 'page' : undefined} onClick={() => { setTab(k); if (k === 'document') load(); }}>{label}</button>
        ))}
      </nav>
      {error && <p className="error" role="alert">{error}</p>}
      {tab === 'slides' && <SlidesView meetingId={id} />}
      {tab === 'import' && <ImportPanel meetingId={id} sections={doc.sections} canEdit={doc.canEdit} onApplied={load} />}
      {tab === 'export' && <ExportPanel meetingId={id} sections={doc.sections} />}
      {tab === 'history' && <HistoryPanel meetingId={id} canEdit={doc.canEdit} onRestored={load} />}
      {tab === 'document' && <div className="doc">
        <nav className="outline" aria-label="Sections">
          <div className="label">Progress · {doc.requiredFilled} of {doc.requiredSections} required</div>
          <div className="bar" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}><motion.div initial={false} animate={{ width: `${pct}%` }} /></div>
          {doc.sections.map((s) => (
            <a key={s.id} href={`#sec-${s.id}`} className="outline-item">
              <span className={`dot ${s.filled ? 'done' : ''}`} aria-hidden="true">{s.filled ? '✓' : ''}</span>
              {s.name}{s.required ? '' : <span className="hint"> · optional</span>}
            </a>
          ))}
        </nav>
        <div className="doc-body">
          {doc.sections.map((s) => (
            <article key={s.id} id={`sec-${s.id}`} className={`tpl-card ${editing === s.id ? 'editing' : ''}`}>
              <div className="row">
                <h2>{s.name}</h2>
                <span>
                  <span className="chip">{TEAM[s.teamTag]}</span>{' '}
                  {perSection[s.key] > 1 && <span className="chip lock" title="Content does not fit one slide">Will use {perSection[s.key]} slides</span>}{' '}
                  {s.lockedBy && s.lockedBy !== me.username && <span className="chip lock">Being edited by {s.lockedBy}</span>}{' '}
                  {doc.canEdit && (editing === s.id
                    ? <button className="btn pri" onClick={() => stopEdit(s)}>Done</button>
                    : <button className="btn" disabled={editing !== null || (s.lockedBy && s.lockedBy !== me.username)} onClick={() => startEdit(s)}>Edit</button>)}
                </span>
              </div>
              {s.kind === 'ACTION_ITEMS' && <ActionItems meetingId={id} items={doc.actionItems} canEdit={doc.canEdit} onChange={load} />}
              {editing === s.id ? <SectionEditor meetingId={id} section={s} /> : s.kind !== 'ACTION_ITEMS' && <SectionView section={s} />}
              {editing !== s.id && notesOf(s) && <p className="notes-view"><strong>Speaker notes:</strong> {notesOf(s)}</p>}
              {s.updatedBy && <p className="hint">Last saved by {s.updatedBy}, {timeFmt.format(new Date(s.updatedAt))}</p>}
              <Comments meetingId={id} sectionKey={s.key} threads={threads} onChange={loadThreads} />
            </article>
          ))}
        </div>
      </div>}
    </section>
  );
}

/** Autosaves 1.2 s after the last change; on a version conflict asks which copy to keep. */
function SectionEditor({ meetingId, section }) {
  const [content, setContent] = useState(() => parse(section));
  const [version, setVersion] = useState(section.version);
  const [state, setState] = useState('saved');
  const [conflict, setConflict] = useState(null);
  const first = useRef(true);

  const save = (c, v) => {
    setState('saving');
    return api(`/api/meetings/${meetingId}/sections/${section.id}`, { method: 'PUT', json: { version: v, content: c } })
      .then((r) => { setVersion(r.version); setState('saved'); })
      .catch((e) => {
        if (e.code === 'VERSION_CONFLICT') { setConflict(e.data?.current); setState('conflict'); } else setState(`error: ${e.message}`);
      });
  };
  useEffect(() => {
    if (first.current) { first.current = false; return undefined; }
    setState('unsaved');
    const t = setTimeout(() => save(content, version), 1200);
    return () => clearTimeout(t);
  }, [content]);

  return (
    <div>
      <p className="hint" role="status" aria-live="polite">{state === 'saved' ? 'All changes saved' : state === 'saving' ? 'Saving…' : state === 'unsaved' ? 'Unsaved changes' : state}</p>
      {conflict && (
        <div className="notice" role="alertdialog" aria-label="Version conflict">
          <p><strong>{conflict.updatedBy} saved this section while you were editing.</strong> Which version do you want to keep?</p>
          <button className="btn pri" onClick={() => { save(content, conflict.version); setConflict(null); }}>Keep mine</button>{' '}
          <button className="btn" onClick={() => { first.current = true; setContent(conflict.content ? JSON.parse(conflict.content) : parse({ kind: section.kind })); setVersion(conflict.version); setConflict(null); setState('saved'); }}>Use theirs</button>
        </div>
      )}
      <KindEditor section={section} value={content} onChange={setContent} />
      <label className="field"><span>Speaker notes (Presenter view and PPTX notes; not shown to the audience)</span>
        <textarea rows={3} maxLength={4000} value={content.notes || ''} onChange={(e) => setContent({ ...content, notes: e.target.value })} /></label>
    </div>
  );
}

function KindEditor({ section, value, onChange }) {
  const set = (patch) => onChange({ ...value, ...patch });
  switch (section.kind) {
    case 'SUMMARY': return <ListEditor label="Key point" items={value.points || []} onChange={(points) => set({ points })} numbered />;
    case 'CHART': return <ListEditor label="Commentary line" items={value.commentary || []} onChange={(commentary) => set({ commentary })} />;
    case 'TWO_COLUMN': return (
      <div className="grid">
        <div><label className="field"><span>Left heading</span><input value={value.leftHead || ''} onChange={(e) => set({ leftHead: e.target.value })} /></label>
          <ListEditor label="Left item" items={value.left || []} onChange={(left) => set({ left })} /></div>
        <div><label className="field"><span>Right heading</span><input value={value.rightHead || ''} onChange={(e) => set({ rightHead: e.target.value })} /></label>
          <ListEditor label="Right item" items={value.right || []} onChange={(right) => set({ right })} /></div>
      </div>
    );
    case 'TABLE': return <TableEditor columns={section.columns} rows={value.rows || []} onChange={(rows) => set({ rows })} />;
    case 'KPI': return (
      <div>
        <KpiEditor kpis={section.kpis} values={value.values || {}} onChange={(values) => set({ values })} />
        <label className="field"><span>Commentary (shown under the KPI tiles)</span>
          <textarea rows={3} value={value.commentary || ''} onChange={(e) => set({ commentary: e.target.value })} /></label>
      </div>
    );
    default: return null;
  }
}

const STATUS = { ON_TARGET: 'On target', BELOW_TARGET: 'Below target', OFF_TARGET: 'Off target' };
const num = (v) => (v === '' || v == null || Number.isNaN(Number(v)) ? null : Number(v));
const statusOf = (k, v) => {
  if (v == null) return null;
  const s = k.direction === 'HIGHER' ? 1 : -1;
  if (s * (v - k.target) >= 0) return 'ON_TARGET';
  if (s * (v - k.amberThreshold) >= 0) return 'BELOW_TARGET';
  return 'OFF_TARGET';
};
const delta = (k, v) => (v == null || k.previousValue == null ? '' : `${v - k.previousValue >= 0 ? '+' : '−'}${Math.abs(+(v - k.previousValue).toFixed(2))} ${k.unit} vs last month`);
const fmtTarget = (k) => `${k.direction === 'HIGHER' ? '≥' : '≤'} ${k.target}${k.unit === '%' ? '%' : ` ${k.unit}`}`;

/** Status preview mirrors the server rule (KpiStatus); the server value is what is saved and shown. */
function KpiEditor({ kpis, values, onChange }) {
  const set = (key, patch) => {
    const next = { ...(values[key] || {}), ...patch };
    if (!next.override) { delete next.override; delete next.reason; }
    onChange({ ...values, [key]: next });
  };
  return (
    <div className="table-wrap"><table>
      <thead><tr><th>KPI</th><th>Target</th><th>This month</th><th>Status</th><th>Override</th><th>Reason (shown on slide)</th></tr></thead>
      <tbody>{kpis.map((k) => {
        const v = values[k.key] || {};
        const computed = statusOf(k, num(v.value));
        return (
          <tr key={k.key}>
            <td>{k.name}{k.required && <span className="hint"> · required</span>}<div className="hint">{delta(k, num(v.value))}</div></td>
            <td className="tnum">{fmtTarget(k)}</td>
            <td><input aria-label={`${k.name} value`} type="number" step="any" value={v.value ?? ''} onChange={(e) => set(k.key, { value: num(e.target.value) })} /></td>
            <td>{computed ? <span className={`chip st-${computed}`}>{STATUS[computed]}</span> : <span className="hint">—</span>}</td>
            <td><select aria-label={`${k.name} status override`} value={v.override || ''} onChange={(e) => set(k.key, { override: e.target.value || null })}>
              <option value="">Automatic</option>{Object.entries(STATUS).map(([s, l]) => <option key={s} value={s}>{l}</option>)}</select></td>
            <td><input aria-label={`${k.name} override reason`} maxLength={300} disabled={!v.override} required={!!v.override} value={v.reason || ''} onChange={(e) => set(k.key, { reason: e.target.value })} /></td>
          </tr>
        );
      })}</tbody>
    </table></div>
  );
}

function KpiTiles({ kpis, commentary }) {
  return (
    <div>
      <div className="kpi-grid">
        {kpis.map((k, i) => (
          <motion.div key={k.key} className="kpi-tile" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3, delay: i * 0.1 }}>
            <div className="hint">{k.name}</div>
            <div className="kpi-value tnum">{k.value != null ? `${k.value}${k.unit === '%' ? '%' : ` ${k.unit}`}` : '—'}</div>
            <div className="hint tnum">Target {fmtTarget(k)}</div>
            <div className="hint tnum">{delta(k, k.value)}</div>
            {k.status && <span className={`chip st-${k.status}`}>{STATUS[k.status]}{k.override ? ' (set by editor)' : ''}</span>}
            {k.override && <div className="hint">{k.reason}</div>}
          </motion.div>
        ))}
      </div>
      {commentary && <p>{commentary}</p>}
    </div>
  );
}

function ListEditor({ label, items, onChange, numbered }) {
  const list = items.length ? items : [''];
  return (
    <ol className={`list-edit ${numbered ? '' : 'plain'}`}>
      <AnimatePresence initial={false}>
        {list.map((t, i) => (
          <motion.li key={i} initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }}>
            <input aria-label={`${label} ${i + 1}`} value={t} onChange={(e) => onChange(list.map((x, j) => (j === i ? e.target.value : x)))} />
            <button className="btn" aria-label={`Remove ${label.toLowerCase()} ${i + 1}`} onClick={() => onChange(list.filter((_, j) => j !== i))}>×</button>
          </motion.li>
        ))}
      </AnimatePresence>
      <li><button className="btn" onClick={() => onChange([...list, ''])}>Add {label.toLowerCase()}</button></li>
    </ol>
  );
}

function TableEditor({ columns, rows, onChange }) {
  const setCell = (r, c, v) => onChange(rows.map((row, i) => (i === r ? row.map((x, j) => (j === c ? v : x)) : row)));
  return (
    <div className="table-wrap"><table>
      <thead><tr>{columns.map((c) => <th key={c}>{c}</th>)}<th><span className="sr">Remove</span></th></tr></thead>
      <tbody>{rows.map((row, r) => (
        <tr key={r}>{row.map((cell, c) => <td key={c}><input aria-label={`${columns[c]} row ${r + 1}`} value={cell} onChange={(e) => setCell(r, c, e.target.value)} /></td>)}
          <td><button className="btn" aria-label={`Remove row ${r + 1}`} onClick={() => onChange(rows.filter((_, i) => i !== r))}>×</button></td></tr>
      ))}</tbody>
      <tfoot><tr><td colSpan={columns.length + 1}><button className="btn" onClick={() => onChange([...rows, columns.map(() => '')])}>Add row</button></td></tr></tfoot>
    </table></div>
  );
}

function SectionView({ section }) {
  const c = section.contentJson ? JSON.parse(section.contentJson) : null;
  if (!c) return <p className="hint">Not filled in yet.</p>;
  switch (section.kind) {
    case 'SUMMARY': return <ol>{(c.points || []).filter(Boolean).map((p, i) => <li key={i}>{p}</li>)}</ol>;
    case 'CHART': return <div>{(c.commentary || []).filter(Boolean).map((p, i) => <p key={i}>{p}</p>)}</div>;
    case 'TWO_COLUMN': return (
      <div className="grid"><div><h3>{c.leftHead}</h3><ul>{(c.left || []).filter(Boolean).map((p, i) => <li key={i}>{p}</li>)}</ul></div>
        <div><h3>{c.rightHead}</h3><ul>{(c.right || []).filter(Boolean).map((p, i) => <li key={i}>{p}</li>)}</ul></div></div>
    );
    case 'TABLE': return (
      <div className="table-wrap"><table><thead><tr>{section.columns.map((h) => <th key={h}>{h}</th>)}</tr></thead>
        <tbody>{(c.rows || []).map((r, i) => <tr key={i}>{r.map((x, j) => <td key={j}>{x}</td>)}</tr>)}</tbody></table></div>
    );
    case 'KPI': return <KpiTiles kpis={section.kpis} commentary={c.commentary} />;
    default: return null;
  }
}
