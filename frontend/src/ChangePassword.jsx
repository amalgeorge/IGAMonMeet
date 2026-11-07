import { useState } from 'react';
import { api } from './api.js';

export default function ChangePassword({ forced, onDone }) {
  const [error, setError] = useState('');
  const submit = (e) => {
    e.preventDefault();
    setError('');
    const f = Object.fromEntries(new FormData(e.currentTarget));
    api('/api/account/password', { method: 'POST', json: f }).then(onDone).catch((err) => setError(err.message));
  };
  return (
    <div className="auth-side">
      <form className="card" onSubmit={submit}>
        <h1>Set a new password</h1>
        {forced && <p className="hint">You signed in with a temporary password. Choose your own to continue.</p>}
        {error && <p className="error" role="alert">{error}</p>}
        <label className="field"><span>Current password</span><input name="currentPassword" type="password" autoComplete="current-password" required /></label>
        <label className="field"><span>New password</span><input name="newPassword" type="password" autoComplete="new-password" required /><small>12 to 64 characters, not one of your last 5.</small></label>
        <label className="field"><span>Confirm new password</span><input name="confirmPassword" type="password" autoComplete="new-password" required /></label>
        <button className="btn pri">Save password</button>
      </form>
    </div>
  );
}
