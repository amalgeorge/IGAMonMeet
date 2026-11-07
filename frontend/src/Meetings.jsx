import { useEffect, useState } from 'react';
import { motion } from 'motion/react';
import { api } from './api.js';

const dateFmt = new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeZone: 'Asia/Singapore' });
const timeFmt = new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Singapore' });
const LABEL = { PREPARING: 'Preparing', HELD: 'Held' };
const TEAM = { ENGINEERING: 'Engineering', OPERATIONS: 'Operations', SHARED: 'Shared' };
const d = (s) => dateFmt.format(new Date(`${s}T00:00:00+08:00`));

export default function Meetings({ isAdmin, onOpen }) {
  const [rows, setRows] = useState([]);
  const [deleted, setDeleted] = useState([]);
  const [q, setQ] = useState('');
  const [error, setError] = useState('');
  const [creating, setCreating] = useState(false);

  const load = () => {
    api(`/api/meetings?q=${encodeURIComponent(q)}`).then(setRows);
    if (isAdmin) api('/api/admin/meetings/deleted').then(setDeleted);
  };
  useEffect(() => { load(); }, [q]);

  const create = (e) => {
    e.preventDefault();
    setError('');
    const f = Object.fromEntries(new FormData(e.currentTarget));
    api('/api/admin/meetings', { method: 'POST', json: f }).then(() => { setCreating(false); load(); }).catch((err) => setError(err.message));
  };
  const act = (p, method = 'POST') => api(p, { method }).then(load).catch((err) => setError(err.message));

  return (
    <section>
      <div className="row">
        <h1>Meetings</h1>
        {isAdmin && <button className="btn pri" onClick={() => setCreating(!creating)}>New meeting</button>}
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      {creating && (
        <form className="card-inline filters" onSubmit={create}>
          <label className="field"><span>Month</span><input type="month" name="month" required /></label>
          <label className="field"><span>Meeting date</span><input type="date" name="meetingDate" required /></label>
          <label className="field grow"><span>Title (optional)</span><input name="title" maxLength={200} placeholder="IAM Monthly Meeting · Month Year" /></label>
          <button className="btn pri">Create</button>
          <p className="hint">Uses the current Template. Open Action items from the previous meeting are carried forward.</p>
        </form>
      )}
      <div className="filters"><label className="field grow"><span>Search</span><input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Month or title" /></label></div>
      <div className="table-wrap"><table>
        <thead><tr><th>Meeting</th><th>Date</th><th>Status</th><th>Open actions</th><th>Editable until</th><th><span className="sr">Actions</span></th></tr></thead>
        <tbody>{rows.map((m, i) => (
          <motion.tr key={m.id} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2, delay: Math.min(i, 10) * 0.03 }}>
            <td><button className="link" onClick={() => onOpen(m.id)}>{m.title}</button><div className="hint">Template v{m.templateVersionNo}</div></td>
            <td className="tnum">{d(m.meetingDate)}</td>
            <td><span className={`chip ${m.label.toLowerCase()}`}>{LABEL[m.label]}</span></td>
            <td className="tnum">{m.openActions}</td>
            <td className="tnum">{m.editWindowOpen ? timeFmt.format(new Date(m.editableUntil)) : 'Admin only'}</td>
            <td className="actions"><button className="btn" onClick={() => onOpen(m.id)}>Open</button>{' '}
              {isAdmin && <button className="btn" onClick={() => act(`/api/admin/meetings/${m.id}`, 'DELETE')}>Delete</button>}</td>
          </motion.tr>
        ))}</tbody>
      </table></div>
      {isAdmin && deleted.length > 0 && (
        <>
          <h2>Deleted meetings</h2>
          <div className="table-wrap"><table>
            <thead><tr><th>Meeting</th><th>Deleted</th><th>Removed for good</th><th><span className="sr">Actions</span></th></tr></thead>
            <tbody>{deleted.map((m) => (
              <tr key={m.id}><td>{m.title}</td><td className="tnum">{timeFmt.format(new Date(m.deletedAt))} by {m.deletedBy}</td>
                <td className="tnum">{timeFmt.format(new Date(m.purgeAfter))}</td>
                <td className="actions"><button className="btn" onClick={() => act(`/api/admin/meetings/${m.id}/restore`)}>Restore</button></td></tr>
            ))}</tbody>
          </table></div>
        </>
      )}
    </section>
  );
}

/** Read-only overview until the Document view editor (ticket 09). */
export function MeetingOverview({ id, onBack }) {
  const [m, setM] = useState(null);
  useEffect(() => { api(`/api/meetings/${id}`).then(setM); }, [id]);
  if (!m) return null;
  return (
    <section>
      <button className="link" onClick={onBack}>← Meetings</button>
      <div className="row"><h1>{m.meeting.title}</h1><span className={`chip ${m.meeting.label.toLowerCase()}`}>{LABEL[m.meeting.label]}</span></div>
      <p className="hint">Meeting {d(m.meeting.meetingDate)} · Template v{m.meeting.templateVersionNo}</p>
      <h2>Sections</h2>
      <ol className="tpl-list">{m.sections.map((s) => (
        <li key={s.id} className="tpl-card row"><strong>{s.name}</strong><span><span className="chip">{TEAM[s.teamTag]}</span>{s.required && <span className="hint"> · Required</span>}</span></li>
      ))}</ol>
      <h2>Action items</h2>
      {m.actionItems.length === 0 ? <p className="hint">None yet.</p> : (
        <div className="table-wrap"><table>
          <thead><tr><th>Action</th><th>Owner</th><th>Team</th><th>Due</th></tr></thead>
          <tbody>{m.actionItems.map((a) => (
            <tr key={a.id}><td>{a.title}{a.originMeetingId && <div className="hint">Carried forward</div>}</td><td>{a.owner}</td><td>{TEAM[a.teamTag]}</td><td className="tnum">{a.dueDate ? d(a.dueDate) : '—'}</td></tr>
          ))}</tbody>
        </table></div>
      )}
    </section>
  );
}
