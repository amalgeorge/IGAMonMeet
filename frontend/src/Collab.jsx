import { useState } from 'react';
import { api } from './api.js';

const TEAM = { ENGINEERING: 'Engineering', OPERATIONS: 'Operations', SHARED: 'Shared' };
const dateFmt = new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeZone: 'Asia/Singapore' });
const timeFmt = new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Singapore' });
const d = (s) => dateFmt.format(new Date(`${s}T00:00:00+08:00`));

/** Action items: overdue first; inline edit with version check. */
export function ActionItems({ meetingId, items, canEdit, onChange }) {
  const [error, setError] = useState('');
  const [editId, setEditId] = useState(null);
  const run = (p) => p.then(() => { setError(''); setEditId(null); onChange(); }).catch((e) => setError(e.message));

  const add = (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    run(api(`/api/meetings/${meetingId}/actions`, { method: 'POST', json: { ...f, dueDate: f.dueDate || null } }));
    e.currentTarget.reset();
  };
  const save = (a, e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    run(api(`/api/meetings/${meetingId}/actions/${a.id}`, { method: 'PUT', json: { ...f, dueDate: f.dueDate || null, status: a.status, version: a.version } }));
  };
  const toggle = (a) => run(api(`/api/meetings/${meetingId}/actions/${a.id}`, { method: 'PUT',
    json: { version: a.version, title: a.title, owner: a.owner, teamTag: a.teamTag, dueDate: a.dueDate, status: a.status === 'OPEN' ? 'CLOSED' : 'OPEN' } }));

  return (
    <div>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="table-wrap"><table>
        <thead><tr><th>Done</th><th>Action</th><th>Owner</th><th>Team</th><th>Due</th><th><span className="sr">Actions</span></th></tr></thead>
        <tbody>{items.map((a) => (editId === a.id ? (
          <tr key={a.id}><td colSpan={6}>
            <form className="filters" onSubmit={(e) => save(a, e)}>
              <ActionFields a={a} /><button className="btn pri">Save</button><button type="button" className="btn" onClick={() => setEditId(null)}>Cancel</button>
            </form>
          </td></tr>
        ) : (
          <tr key={a.id} className={a.status === 'CLOSED' ? 'closed' : ''}>
            <td><input type="checkbox" aria-label={`Mark "${a.title}" ${a.status === 'OPEN' ? 'done' : 'open'}`} checked={a.status === 'CLOSED'} disabled={!canEdit} onChange={() => toggle(a)} /></td>
            <td>{a.title}{a.originMeetingId && <div className="hint">Carried forward</div>}</td>
            <td>{a.owner}</td>
            <td><span className="chip">{TEAM[a.teamTag]}</span></td>
            <td className={`tnum ${a.overdue ? 'overdue' : ''}`}>{a.dueDate ? d(a.dueDate) : '—'}{a.overdue ? ' · overdue' : ''}</td>
            <td className="actions">{canEdit && <>
              <button className="btn" onClick={() => setEditId(a.id)}>Edit</button>{' '}
              <button className="btn" onClick={() => run(api(`/api/meetings/${meetingId}/actions/${a.id}`, { method: 'DELETE' }))}>Remove</button></>}</td>
          </tr>
        )))}</tbody>
      </table></div>
      {canEdit && (
        <form className="filters" onSubmit={add} aria-label="Add action item">
          <ActionFields a={{ teamTag: 'SHARED' }} /><button className="btn pri">Add action</button>
        </form>
      )}
    </div>
  );
}

function ActionFields({ a }) {
  return (
    <>
      <label className="field grow"><span>Action</span><input name="title" defaultValue={a.title} required maxLength={500} /></label>
      <label className="field"><span>Owner</span><input name="owner" defaultValue={a.owner} required maxLength={200} /></label>
      <label className="field"><span>Team</span><select name="teamTag" defaultValue={a.teamTag}>{Object.entries(TEAM).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
      <label className="field"><span>Due</span><input name="dueDate" type="date" defaultValue={a.dueDate || ''} /></label>
    </>
  );
}

/** Threaded comments for one Section. Comments stay open after the Edit window. */
export function Comments({ meetingId, sectionKey, threads, onChange }) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState('');
  const mine = threads.filter((t) => t.sectionKey === sectionKey);
  const unresolved = mine.filter((t) => !t.resolved).length;
  const post = (e, parentId) => {
    e.preventDefault();
    const body = new FormData(e.currentTarget).get('body');
    api(`/api/meetings/${meetingId}/comments`, { method: 'POST', json: { sectionKey, parentId, body } })
      .then(() => { e.target.reset(); setError(''); onChange(); }).catch((err) => setError(err.message));
  };
  const resolve = (t) => api(`/api/meetings/${meetingId}/comments/${t.id}/${t.resolved ? 'reopen' : 'resolve'}`, { method: 'POST' }).then(onChange);

  return (
    <div className="comments">
      <button className="link" aria-expanded={open} onClick={() => setOpen(!open)}>
        Comments ({mine.length}{unresolved ? `, ${unresolved} open` : ''})
      </button>
      {open && (
        <div>
          {error && <p className="error" role="alert">{error}</p>}
          {mine.map((t) => (
            <div key={t.id} className={`thread ${t.resolved ? 'resolved' : ''}`}>
              <p><strong>{t.author}</strong> <span className="hint">{timeFmt.format(new Date(t.createdAt))}</span><br />{t.body}</p>
              {t.replies.map((r) => <p key={r.id} className="reply"><strong>{r.author}</strong> <span className="hint">{timeFmt.format(new Date(r.createdAt))}</span><br />{r.body}</p>)}
              {t.resolved && <p className="hint">Resolved by {t.resolvedBy}</p>}
              <form className="filters" onSubmit={(e) => post(e, t.id)}>
                <label className="field grow"><span className="sr">Reply</span><input name="body" placeholder="Reply" required maxLength={2000} /></label>
                <button className="btn">Reply</button>
                <button type="button" className="btn" onClick={() => resolve(t)}>{t.resolved ? 'Reopen' : 'Resolve'}</button>
              </form>
            </div>
          ))}
          <form className="filters" onSubmit={(e) => post(e, null)}>
            <label className="field grow"><span className="sr">New comment</span><textarea name="body" rows={2} placeholder="Comment on this section" required maxLength={2000} /></label>
            <button className="btn pri">Comment</button>
          </form>
        </div>
      )}
    </div>
  );
}
