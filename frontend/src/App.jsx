import { useEffect, useState } from 'react';
import { api } from './api.js';
import Auth from './Auth.jsx';
import AuditLog from './AuditLog.jsx';
import ChangePassword from './ChangePassword.jsx';
import { AdminQueue, AdminUsers } from './AdminUsers.jsx';

const ADMIN_VIEWS = { queue: 'Admin queue', users: 'Users', audit: 'Audit log' };

export default function App() {
  const [me, setMe] = useState(undefined);
  const [view, setView] = useState('queue');
  const [queueCount, setQueueCount] = useState(0);

  const loadMe = () => api('/api/auth/me').then(setMe).catch(() => setMe(null));
  useEffect(() => { loadMe(); }, []);
  useEffect(() => { if (me?.role === 'ADMIN' && !me.mustChangePassword) api('/api/admin/queue').then((d) => setQueueCount(d.count)).catch(() => {}); }, [me]);

  if (me === undefined) return null;
  if (!me) return <Auth onSignedIn={loadMe} />;
  if (me.mustChangePassword) return <ChangePassword forced onDone={loadMe} />;

  const signOut = () => api('/api/auth/logout', { method: 'POST' }).then(() => setMe(null));
  return (
    <main className="shell">
      <header className="row">
        <nav className="nav" aria-label="Main">
          {me.role === 'ADMIN' && Object.entries(ADMIN_VIEWS).map(([k, label]) => (
            <button key={k} className={view === k ? 'on' : ''} aria-current={view === k ? 'page' : undefined} onClick={() => setView(k)}>
              {label}{k === 'queue' && queueCount > 0 && <span className="badge" aria-label={`${queueCount} waiting`}>{queueCount}</span>}
            </button>
          ))}
        </nav>
        <span>{me.fullName} <button className="btn" onClick={signOut}>Sign out</button></span>
      </header>
      {me.role !== 'ADMIN' && <h1>Welcome, {me.fullName}</h1>}
      {me.role === 'ADMIN' && view === 'queue' && <AdminQueue onChange={setQueueCount} />}
      {me.role === 'ADMIN' && view === 'users' && <AdminUsers />}
      {me.role === 'ADMIN' && view === 'audit' && <AuditLog />}
    </main>
  );
}
