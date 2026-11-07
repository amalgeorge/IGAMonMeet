import { useEffect, useState } from 'react';
import { api } from './api.js';
import Auth from './Auth.jsx';
import AuditLog from './AuditLog.jsx';
import ChangePassword from './ChangePassword.jsx';
import Recover from './Recover.jsx';
import { AdminQueue, AdminUsers } from './AdminUsers.jsx';
import AdminSettings from './AdminSettings.jsx';

const ADMIN_VIEWS = { queue: 'Admin queue', users: 'Users', audit: 'Audit log', settings: 'Settings' };

export default function App() {
  const [me, setMe] = useState(undefined);
  const [view, setView] = useState('queue');
  const [queueCount, setQueueCount] = useState(0);
  const [features, setFeatures] = useState({});
  const loadFeatures = () => api('/api/settings/features').then(setFeatures).catch(() => {});

  const loadMe = () => api('/api/auth/me').then(setMe).catch(() => setMe(null));
  useEffect(() => { loadMe(); }, []);
  useEffect(() => {
    if (me?.role === 'ADMIN' && !me.mustChangePassword) {
      api('/api/admin/queue').then((d) => setQueueCount(d.count)).catch(() => {});
      loadFeatures();
    }
  }, [me]);

  if (window.location.pathname.endsWith('/recover')) return <Recover />;
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
              {label}{k === 'queue' && features.QUEUE_BADGE !== false && queueCount > 0 && <span className="badge" aria-label={`${queueCount} waiting`}>{queueCount}</span>}
            </button>
          ))}
        </nav>
        <span>{me.fullName} <button className="btn" onClick={signOut}>Sign out</button></span>
      </header>
      {me.role !== 'ADMIN' && <h1>Welcome, {me.fullName}</h1>}
      {me.role === 'ADMIN' && view === 'queue' && <AdminQueue onChange={setQueueCount} />}
      {me.role === 'ADMIN' && view === 'users' && <AdminUsers />}
      {me.role === 'ADMIN' && view === 'audit' && <AuditLog />}
      {me.role === 'ADMIN' && view === 'settings' && <AdminSettings onChange={loadFeatures} />}
    </main>
  );
}
