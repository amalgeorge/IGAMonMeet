import { SITREP_TYPES } from './labels.js';
import CsvImport from './CsvImport.jsx';
import ColumnsEditor, { remapRows } from './ColumnsEditor.jsx';
import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { api } from './api.js';
import { ActionItems, Comments } from './Collab.jsx';
import SlidesView from './SlidesView.jsx';
import ExportPanel from './ExportPanel.jsx';
import ImportPanel from './ImportPanel.jsx';
import HistoryPanel from './HistoryPanel.jsx';
import DashboardPanel from './DashboardPanel.jsx';
import AvailablePanel from './AvailablePanel.jsx';
import { STATUS_LABELS } from './labels.js';
import TableStyleEditor from './TableStyleEditor.jsx';

const TEAM = { ENGINEERING: 'Engineering', OPERATIONS: 'Operations', SHARED: 'Shared' };
const LABEL = { PREPARING: 'Preparing', HELD: 'Held' };
const dateFmt = new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeZone: 'Asia/Singapore' });
const timeFmt = new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Singapore' });
const EMPTY = { TEXT: { blocks: [{ type: 'paragraph', text: '' }] }, SUMMARY: { points: [''], stats: [] }, KPI: { values: {}, commentary: '' }, CHART: { commentary: [''] }, TABLE: { rows: [] }, TWO_COLUMN: { leftHead: 'Risks', left: [''], rightHead: 'Decisions needed', right: [''] } };
const notesOf = (s) => { try { return s.contentJson ? JSON.parse(s.contentJson).notes : ''; } catch { return ''; } };
const parse = (s) => (s.contentJson ? JSON.parse(s.contentJson) : structuredClone(EMPTY[s.kind] || {}));

export default function MeetingDocument({ id, me, onBack }) {
  const [doc, setDoc] = useState(null);
  const [editing, setEditing] = useState(null);
  const [error, setError] = useState('');
  const [threads, setThreads] = useState([]);
  const [perSection, setPerSection] = useState({});
  const [tab, setTab] = useState('document');
  const [collapsed, setCollapsed] = useState({});
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
        {[['document', 'Document'], ['slides', 'Slides'], ['dashboard', 'Dashboard'], ['import', 'Import'], ['export', 'Export'], ['history', 'History']].map(([k, label]) => (
          <button key={k} className={tab === k ? 'on' : ''} aria-current={tab === k ? 'page' : undefined} onClick={() => { setTab(k); if (k === 'document') load(); }}>{label}</button>
        ))}
      </nav>
      {error && <p className="error" role="alert">{error}</p>}
      {tab === 'slides' && <SlidesView meetingId={id} />}
      {tab === 'dashboard' && <DashboardPanel meetingId={id} />}
      {tab === 'import' && <ImportPanel meetingId={id} sections={doc.sections} canEdit={doc.canEdit} onApplied={load} />}
      {tab === 'export' && <ExportPanel meetingId={id} sections={doc.sections} />}
      {tab === 'history' && <HistoryPanel meetingId={id} canEdit={doc.canEdit} onRestored={load} />}
      {tab === 'document' && <div className="doc">
        <nav className="outline" aria-label="Sections">
          <div className="label">Progress · {doc.requiredFilled} of {doc.requiredSections} required</div>
          <div className="bar" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}><motion.div initial={false} animate={{ width: `${pct}%` }} /></div>
          {outlineGroups(doc.sections).map(({ key, name, items }) => {
            const req = items.filter((s) => s.required);
            const open = !key || !collapsed[key];
            return (<div key={key || items[0].id} className={key ? 'outline-group' : ''}>
              {key && <button className="outline-head" aria-expanded={open} onClick={() => setCollapsed({ ...collapsed, [key]: open })}>
                <span aria-hidden="true">{open ? '▾' : '▸'}</span> {name}
                {req.length > 0 && <span className="hint"> · {req.filter((s) => s.filled).length} of {req.length}</span>}
              </button>}
              {open && items.map((s) => (
                <a key={s.id} href={`#sec-${s.id}`} className="outline-item">
                  <span className={`dot ${s.filled ? 'done' : ''}`} aria-hidden="true">{s.filled ? '✓' : ''}</span>
                  {s.name}{s.required ? '' : <span className="hint"> · optional</span>}{s.onSlides === false && <span className="hint"> · not on slides</span>}
                </a>
              ))}
            </div>);
          })}
          <AvailablePanel meetingId={id} sections={doc.sections} canEdit={doc.canEdit} onChange={load} />
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
    case 'TEXT': return <BlocksEditor blocks={value.blocks || []} onChange={(blocks) => set({ blocks })} />;
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
    case 'TABLE': return (
      <>
        <TableEditor columns={section.columns} rows={value.rows || []} onChange={(rows) => set({ rows })}
          style={section.tableStyle ? JSON.parse(section.tableStyle) : null} />
        <CsvImport columns={section.columns} rows={value.rows || []} onApply={(_, rows) => set({ rows })} />
      </>
    );
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

/** Consecutive Sections that share a Section group, in Template order (spec-v2 §1.5). */
function outlineGroups(sections) {
  const out = [];
  sections.forEach((s) => {
    const last = out[out.length - 1];
    if (s.groupKey && last && last.key === s.groupKey) last.items.push(s);
    else out.push({ key: s.groupKey || null, name: s.groupName, items: [s] });
  });
  return out;
}

const STATUS = STATUS_LABELS;
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
        const text = k.valueType === 'TEXT_STATUS';
        const computed = text ? (k.options || []).find((o) => o.label === v.value)?.status : statusOf(k, num(v.value));
        return (
          <tr key={k.key}>
            <td>{k.name}{k.required && <span className="hint"> · required</span>}{!text && <div className="hint">{delta(k, num(v.value))}</div>}</td>
            <td className="tnum">{text ? '—' : k.targetLine}</td>
            <td><ValueInput k={k} value={v.value} onChange={(value) => set(k.key, { value })} /></td>
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

/** Minutes from "2h 30m", "90m", "1.5h" or a plain number of minutes; null when empty or unreadable. */
export function parseDuration(s) {
  const t = String(s || '').trim().toLowerCase();
  if (!t) return null;
  if (/^\d+(\.\d+)?$/.test(t)) return Math.round(Number(t));
  const m = t.match(/^(?:(\d+(?:\.\d+)?)\s*h)?\s*(?:(\d+)\s*m)?$/);
  if (!m || (!m[1] && !m[2])) return null;
  return Math.round(Number(m[1] || 0) * 60 + Number(m[2] || 0));
}

export function fmtDuration(min) {
  if (min == null) return '';
  const h = Math.floor(min / 60); const r = min % 60;
  return h === 0 ? `${r}m` : r === 0 ? `${h}h` : `${h}h ${r}m`;
}

/** The value field for a KPI, by value type (spec-v2 §2.2). */
function ValueInput({ k, value, onChange }) {
  const label = `${k.name} value`;
  const [draft, setDraft] = useState(k.valueType === 'DURATION' ? fmtDuration(value) : null);
  if (k.valueType === 'TEXT_STATUS') {
    return <select aria-label={label} value={value || ''} onChange={(e) => onChange(e.target.value || null)}>
      <option value="">—</option>{(k.options || []).map((o) => <option key={o.label} value={o.label}>{o.label}</option>)}</select>;
  }
  if (k.valueType === 'DURATION') {
    return <input aria-label={label} placeholder="2h 30m" value={draft} onChange={(e) => { setDraft(e.target.value); const m = parseDuration(e.target.value); if (m != null || !e.target.value) onChange(m); }} />;
  }
  return <input aria-label={label} type="number" step={k.valueType === 'COUNT' ? 1 : 'any'} value={value ?? ''} onChange={(e) => onChange(num(e.target.value))} />;
}

function KpiTiles({ kpis, commentary }) {
  return (
    <div>
      <div className="kpi-grid">
        {kpis.map((k, i) => (
          <motion.div key={k.key} className="kpi-tile" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3, delay: i * 0.1 }}>
            <div className="hint" title={k.description || undefined}>{k.name}</div>
            {k.subtitle && <div className="hint">{k.subtitle}</div>}
            <div className="kpi-value tnum">{k.display}</div>
            {k.targetLine && <div className="hint tnum">{k.targetLine}</div>}
            {k.deltaLine && <div className="hint tnum">{k.deltaLine}</div>}
            {k.status && <span className={`chip st-${k.status}`}>{k.statusLabel || STATUS[k.status]}{k.override ? ' (set by editor)' : ''}</span>}
            {k.override && <div className="hint">{k.reason}</div>}
          </motion.div>
        ))}
      </div>
      {commentary && <p>{commentary}</p>}
    </div>
  );
}

/** A Text Section's blocks in order: paragraphs and bullet lists (spec-v2 §3.1). Table blocks follow in ticket 28. */
function BlocksEditor({ blocks, onChange }) {
  const set = (i, patch) => onChange(blocks.map((b, j) => (j === i ? { ...b, ...patch } : b)));
  const move = (i, d) => { const a = [...blocks]; [a[i], a[i + d]] = [a[i + d], a[i]]; onChange(a); };
  return (
    <div className="blocks">
      {blocks.map((b, i) => (
        <div key={i} className="block">
          <div className="row">
            <span className="hint">{b.type === 'bullets' ? 'Bullet list' : b.type === 'table' ? 'Table' : 'Paragraph'}</span>
            <span>
              <button className="btn" disabled={i === 0} aria-label={`Move block ${i + 1} up`} onClick={() => move(i, -1)}>↑</button>{' '}
              <button className="btn" disabled={i === blocks.length - 1} aria-label={`Move block ${i + 1} down`} onClick={() => move(i, 1)}>↓</button>{' '}
              <button className="btn" aria-label={`Remove block ${i + 1}`} onClick={() => onChange(blocks.filter((_, j) => j !== i))}>Remove</button>
            </span>
          </div>
          {b.type === 'table' && <TableBlockEditor block={b} onChange={(patch) => set(i, patch)} />}
          {b.type === 'bullets'
            ? <ListEditor label="Bullet" items={b.items || []} onChange={(items) => set(i, { items })} />
            : b.type !== 'table' && <textarea aria-label={`Paragraph ${i + 1}`} rows={3} maxLength={5000} value={b.text || ''} onChange={(e) => set(i, { text: e.target.value })} />}
        </div>
      ))}
      <div className="row">
        <span>
          <button className="btn" onClick={() => onChange([...blocks, { type: 'paragraph', text: '' }])}>Add paragraph</button>{' '}
          <button className="btn" onClick={() => onChange([...blocks, { type: 'bullets', items: [''] }])}>Add bullet list</button>{' '}
          <button className="btn" onClick={() => onChange([...blocks, { type: 'table', columns: ['Item', 'Owner', 'Status'], rows: [] }])}>Add table</button>
        </span>
      </div>
    </div>
  );
}

/** A table block: the editor names up to 12 columns, then fills the rows (spec-v3 §1.1). */
function TableBlockEditor({ block, onChange }) {
  const cols = block.columns && block.columns.length ? block.columns : ['Column 1'];
  const setColumns = (next, from) => onChange({
    columns: next,
    rows: remapRows(block.rows, from),
    style: next.length === cols.length && from.every((j, k) => j === k) ? block.style : null
  });
  return (
    <div>
      <ColumnsEditor columns={cols} onChange={setColumns} />
      <TableEditor columns={cols} rows={block.rows || []} onChange={(rows) => onChange({ rows })} />
      <CsvImport columns={cols} rows={block.rows || []} allowNewColumns
        onApply={(columns, rows) => onChange({ columns, rows, style: columns === cols ? block.style : null })} />
      <TableStyleEditor key={cols.join('|')} columns={cols} value={block.style} onChange={(style) => onChange({ style })} />
    </div>
  );
}

function BlocksView({ blocks }) {
  return <div>{blocks.map((b, i) => (b.type === 'table'
    ? <div key={i} className="table-wrap"><table><thead><tr>{(b.columns || []).map((h) => <th key={h}>{h}</th>)}</tr></thead>
        <tbody>{(b.rows || []).map((r, j) => <tr key={j}>{r.map((x, k) => <td key={k}>{x}</td>)}</tr>)}</tbody></table></div>
    : b.type === 'bullets'
    ? <ul key={i}>{(b.items || []).filter(Boolean).map((t, j) => <li key={j}>{t}</li>)}</ul>
    : b.text ? <p key={i}>{b.text}</p> : null))}</div>;
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

function TableEditor({ columns, rows, onChange, style }) {
  const setCell = (r, c, v) => onChange(rows.map((row, i) => (i === r ? row.map((x, j) => (j === c ? v : x)) : row)));
  const whole = new Set(style?.wholeNumbers || []);
  const listId = style?.listColumn != null ? 'sitrep-types' : undefined;
  return (
    <div className="table-wrap">{listId && <datalist id={listId}>{SITREP_TYPES.map((t) => <option key={t} value={t} />)}</datalist>}<table>
      <thead><tr>{columns.map((c) => <th key={c}>{c}</th>)}<th><span className="sr">Remove</span></th></tr></thead>
      <tbody>{rows.map((row, r) => (
        <tr key={r}>{row.map((cell, c) => <td key={c}><input aria-label={`${columns[c]} row ${r + 1}`} value={cell}
          list={c === style?.listColumn ? listId : undefined}
          inputMode={whole.has(c) ? 'numeric' : undefined} pattern={whole.has(c) ? '[0-9]*' : undefined}
          onChange={(e) => setCell(r, c, whole.has(c) ? e.target.value.replace(/[^0-9]/g, '') : e.target.value)} /></td>)}
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
    case 'TEXT': return <BlocksView blocks={c.blocks || (c.text ? [{ type: 'paragraph', text: c.text }] : [])} />;
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
