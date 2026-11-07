import { useEffect, useState } from 'react';
import { api } from './api.js';

const fmt = new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Singapore' });
const REASON = { IMPORT: 'Import', RESTORE: 'Before restore', SNAPSHOT: 'As presented', MANUAL: 'Saved by hand' };

/** Versions, field-level diff and restore (any User within the Edit window; Admins always). */
export default function HistoryPanel({ meetingId, canEdit, onRestored }) {
  const [versions, setVersions] = useState([]);
  const [from, setFrom] = useState(null);
  const [to, setTo] = useState('');
  const [changes, setChanges] = useState(null);
  const [label, setLabel] = useState('');
  const [msg, setMsg] = useState({});
  const [confirm, setConfirm] = useState(null);

  const load = () => api(`/api/meetings/${meetingId}/versions`).then((v) => { setVersions(v); if (v.length && from == null) setFrom(v[0].versionNo); });
  useEffect(() => { load(); }, [meetingId]);
  useEffect(() => {
    if (from == null) return;
    api(`/api/meetings/${meetingId}/versions/diff?from=${from}${to ? `&to=${to}` : ''}`).then(setChanges).catch(() => setChanges([]));
  }, [from, to, versions]);

  const save = (e) => {
    e.preventDefault();
    api(`/api/meetings/${meetingId}/versions`, { method: 'POST', json: { label } })
      .then((r) => { setLabel(''); setMsg({ ok: `Saved as version ${r.versionNo}.` }); setFrom(r.versionNo); load(); })
      .catch((err) => setMsg({ error: err.message }));
  };
  const restore = (n) => api(`/api/meetings/${meetingId}/versions/${n}/restore`, { method: 'POST' })
    .then((r) => {
      setConfirm(null);
      setMsg({ ok: `Restored version ${n} (${r.restored.length} section(s)). Your previous state is version ${r.beforeVersionNo}. ${r.note}${r.skipped.length ? ` Skipped: ${r.skipped.map((s) => `${s.section} (${s.reason})`).join(', ')}.` : ''}` });
      load(); onRestored?.();
    })
    .catch((err) => setMsg({ error: err.message }));

  return (
    <div className="export">
      {msg.error && <p className="error" role="alert">{msg.error}</p>}
      {msg.ok && <p className="notice" role="status">{msg.ok}</p>}
      {canEdit && (
        <form className="filters" onSubmit={save}>
          <label className="field grow"><span>Save the meeting as it is now</span><input value={label} maxLength={200} placeholder="Label, e.g. Sent to chair" onChange={(e) => setLabel(e.target.value)} /></label>
          <button className="btn pri">Save version</button>
        </form>
      )}
      {versions.length === 0 ? <p className="hint">No versions yet. Versions are saved on import, restore, at 23:59 on the meeting date, and with the button above.</p> : (
        <div className="table-wrap"><table>
          <thead><tr><th>Version</th><th>Why</th><th>When</th><th>By</th><th><span className="sr">Actions</span></th></tr></thead>
          <tbody>{versions.map((v) => (
            <tr key={v.versionNo}>
              <td className="tnum">v{v.versionNo}</td>
              <td>{v.label}<div className="hint">{REASON[v.reason]}</div></td>
              <td className="tnum">{fmt.format(new Date(v.createdAt))}</td>
              <td>{v.createdBy}</td>
              <td className="actions">
                <button className="btn" onClick={() => { setFrom(v.versionNo); setTo(''); }}>Compare with now</button>{' '}
                {canEdit && (confirm === v.versionNo
                  ? <><button className="btn pri" onClick={() => restore(v.versionNo)}>Confirm restore</button> <button className="btn" onClick={() => setConfirm(null)}>Cancel</button></>
                  : <button className="btn" onClick={() => setConfirm(v.versionNo)}>Restore</button>)}
              </td>
            </tr>
          ))}</tbody>
        </table></div>
      )}
      {from != null && changes && (
        <>
          <div className="row">
            <h2>Changes from v{from} to {to ? `v${to}` : 'now'}</h2>
            <label className="field"><span>Compare to</span>
              <select value={to} onChange={(e) => setTo(e.target.value)}><option value="">Now</option>{versions.filter((v) => v.versionNo !== from).map((v) => <option key={v.versionNo} value={v.versionNo}>v{v.versionNo}</option>)}</select></label>
          </div>
          {changes.length === 0 ? <p className="hint">No differences.</p> : (
            <div className="table-wrap"><table>
              <thead><tr><th>Section</th><th>Field</th><th>Before</th><th>After</th></tr></thead>
              <tbody>{changes.map((c, i) => (
                <tr key={i}><td>{c.section}</td><td>{c.field}</td>
                  <td className="diff-del">{c.before ?? <span className="hint">(none)</span>}</td>
                  <td className="diff-add">{c.after ?? <span className="hint">(none)</span>}</td></tr>
              ))}</tbody>
            </table></div>
          )}
        </>
      )}
    </div>
  );
}
