import { useEffect, useState } from 'react';
import { api } from './api.js';

const fmt = new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Singapore' });

/** Shows a temporary password once, with a copy button. */
function TempPassword({ value, onDone }) {
  return (
    <div className="notice" role="alert">
      <strong>Temporary password (shown once):</strong> <code>{value}</code>{' '}
      <button className="btn" onClick={() => navigator.clipboard?.writeText(value)}>Copy</button>{' '}
      <button className="btn" onClick={onDone}>Done</button>
      <p className="hint">Give it to the user directly. They must change it at sign-in.</p>
    </div>
  );
}

export function AdminQueue({ onChange }) {
  const [q, setQ] = useState({ pendingUsers: [], resetRequests: [] });
  const [temp, setTemp] = useState('');
  const [error, setError] = useState('');
  const load = () => api('/api/admin/queue').then((d) => { setQ(d); onChange?.(d.count); });
  useEffect(() => { load(); }, []);
  const act = (p) => api(p, { method: 'POST' }).then((d) => { if (d?.temporaryPassword) setTemp(d.temporaryPassword); load(); }).catch((e) => setError(e.message));

  return (
    <section>
      <h1>Admin queue</h1>
      {error && <p className="error" role="alert">{error}</p>}
      {temp && <TempPassword value={temp} onDone={() => setTemp('')} />}
      <h2>Pending registrations</h2>
      {q.pendingUsers.length === 0 ? <p className="hint">None waiting.</p> : (
        <div className="table-wrap"><table>
          <thead><tr><th>Name</th><th>Username</th><th>Email</th><th>Registered</th><th><span className="sr">Actions</span></th></tr></thead>
          <tbody>{q.pendingUsers.map((u) => (
            <tr key={u.id}><td>{u.fullName}</td><td>{u.username}</td><td>{u.email}</td><td className="tnum">{fmt.format(new Date(u.createdAt))}</td>
              <td className="actions"><button className="btn pri" onClick={() => act(`/api/admin/users/${u.id}/approve`)}>Approve</button>{' '}
                <button className="btn" onClick={() => act(`/api/admin/users/${u.id}/reject`)}>Reject</button></td></tr>
          ))}</tbody>
        </table></div>
      )}
      <h2>Password reset requests</h2>
      {q.resetRequests.length === 0 ? <p className="hint">None waiting.</p> : (
        <div className="table-wrap"><table>
          <thead><tr><th>Name</th><th>Username</th><th>Requested</th><th><span className="sr">Actions</span></th></tr></thead>
          <tbody>{q.resetRequests.map((r) => (
            <tr key={r.id}><td>{r.fullName}</td><td>{r.username}</td><td className="tnum">{fmt.format(new Date(r.requestedAt))}</td>
              <td className="actions"><button className="btn pri" onClick={() => act(`/api/admin/users/${r.userId}/reset-password`)}>Reset password</button></td></tr>
          ))}</tbody>
        </table></div>
      )}
    </section>
  );
}

export function AdminUsers() {
  const [rows, setRows] = useState([]);
  const [temp, setTemp] = useState('');
  const [error, setError] = useState('');
  const load = () => api('/api/admin/users').then(setRows);
  useEffect(() => { load(); }, []);
  const act = (p, json) => { setError(''); return api(p, { method: 'POST', json }).then((d) => { if (d?.temporaryPassword) setTemp(d.temporaryPassword); load(); }).catch((e) => setError(e.message)); };

  return (
    <section>
      <h1>Users</h1>
      {error && <p className="error" role="alert">{error}</p>}
      {temp && <TempPassword value={temp} onDone={() => setTemp('')} />}
      <div className="table-wrap"><table>
        <thead><tr><th>Name</th><th>Username</th><th>Role</th><th>Status</th><th><span className="sr">Actions</span></th></tr></thead>
        <tbody>{rows.map((u) => (
          <tr key={u.id}>
            <td>{u.fullName}<div className="hint">{u.email}</div></td>
            <td>{u.username}</td>
            <td>
              <label className="sr" htmlFor={`role-${u.id}`}>Role for {u.username}</label>
              <select id={`role-${u.id}`} value={u.role} onChange={(e) => act(`/api/admin/users/${u.id}/role`, { role: e.target.value })}>
                <option value="END_USER">End user</option><option value="ADMIN">Admin</option>
              </select>
            </td>
            <td>{u.status.charAt(0) + u.status.slice(1).toLowerCase()}{u.locked ? ' · locked' : ''}</td>
            <td className="actions">
              {u.status === 'PENDING' && <button className="btn pri" onClick={() => act(`/api/admin/users/${u.id}/approve`)}>Approve</button>}
              {u.status === 'ACTIVE' && <button className="btn" onClick={() => act(`/api/admin/users/${u.id}/disable`)}>Disable</button>}
              {u.status === 'DISABLED' && <button className="btn" onClick={() => act(`/api/admin/users/${u.id}/enable`)}>Enable</button>}
              {u.locked && <button className="btn" onClick={() => act(`/api/admin/users/${u.id}/unlock`)}>Unlock</button>}
              {u.status === 'ACTIVE' && <button className="btn" onClick={() => act(`/api/admin/users/${u.id}/reset-password`)}>Reset password</button>}
            </td>
          </tr>
        ))}</tbody>
      </table></div>
    </section>
  );
}
