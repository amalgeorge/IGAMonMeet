import { useEffect, useState } from 'react';
import { api } from './api.js';
import Auth from './Auth.jsx';
import AuditLog from './AuditLog.jsx';
import ChangePassword from './ChangePassword.jsx';
import Recover from './Recover.jsx';
import { AudienceWindow } from './SlidesView.jsx';
import { AdminQueue, AdminUsers } from './AdminUsers.jsx';
import AdminSettings from './AdminSettings.jsx';
import AdminTemplate from './AdminTemplate.jsx';
import AdminHouseTemplate from './AdminHouseTemplate.jsx';
import Meetings from './Meetings.jsx';
import MeetingDocument from './Document.jsx';

const ADMIN_VIEWS = { meetings: 'Meetings', queue: 'Admin queue', users: 'Users', template: 'Template', house: 'House template', audit: 'Audit log', settings: 'Settings' };

export default function App() {
  const [me, setMe] = useState(undefined);
  const [view, setView] = useState('meetings');
  const [openId, setOpenId] = useState(null);
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
  const audience = window.location.hash.match(/^#audience\/(\d+)$/);
  if (audience) return <AudienceWindow meetingId={Number(audience[1])} />;
  if (me === undefined) return null;
  if (!me) return <Auth onSignedIn={loadMe} />;
  if (me.mustChangePassword) return <ChangePassword forced onDone={loadMe} />;

  const signOut = () => api('/api/auth/logout', { method: 'POST' }).then(() => setMe(null));
  return (
    <main className="shell">
      <header className="row">
        <nav className="nav" aria-label="Main">
          {(me.role === 'ADMIN' ? Object.entries(ADMIN_VIEWS) : [['meetings', 'Meetings']]).map(([k, label]) => (
            <button key={k} className={view === k ? 'on' : ''} aria-current={view === k ? 'page' : undefined} onClick={() => { setView(k); setOpenId(null); }}>
              {label}{k === 'queue' && features.QUEUE_BADGE !== false && queueCount > 0 && <span className="badge" aria-label={`${queueCount} waiting`}>{queueCount}</span>}
            </button>
          ))}
        </nav>
        <span>{me.fullName} <button className="btn" onClick={signOut}>Sign out</button></span>
      </header>
      {view === 'meetings' && !openId && <Meetings isAdmin={me.role === 'ADMIN'} onOpen={setOpenId} />}
      {view === 'meetings' && openId && <MeetingDocument id={openId} me={me} onBack={() => setOpenId(null)} />}
      {me.role === 'ADMIN' && view === 'queue' && <AdminQueue onChange={setQueueCount} />}
      {me.role === 'ADMIN' && view === 'users' && <AdminUsers />}
      {me.role === 'ADMIN' && view === 'template' && <AdminTemplate />}
      {me.role === 'ADMIN' && view === 'house' && <AdminHouseTemplate />}
      {me.role === 'ADMIN' && view === 'audit' && <AuditLog />}
      {me.role === 'ADMIN' && view === 'settings' && <AdminSettings onChange={loadFeatures} />}
    </main>
  );
}
