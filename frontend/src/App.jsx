import { useEffect, useState } from 'react';
import { api } from './api.js';
import Auth from './Auth.jsx';

export default function App() {
  const [me, setMe] = useState(undefined);

  const loadMe = () => api('/api/auth/me').then(setMe).catch(() => setMe(null));
  useEffect(() => { loadMe(); }, []);

  if (me === undefined) return null;
  if (!me) return <Auth onSignedIn={loadMe} />;

  // Meetings screens arrive with ticket 08.
  return (
    <main className="shell">
      <h1>Welcome, {me.fullName}</h1>
      <button className="btn" onClick={() => api('/api/auth/logout', { method: 'POST' }).then(() => setMe(null))}>Sign out</button>
    </main>
  );
}
