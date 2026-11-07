import { useState } from 'react';
import { api } from './api.js';

/** Opened from the DBA's one-time link: .../recover#token=... (the fragment never reaches the server). */
export default function Recover() {
  const token = new URLSearchParams(window.location.hash.slice(1)).get('token') || '';
  const [state, setState] = useState(token ? 'form' : 'missing');
  const [error, setError] = useState('');

  const submit = (e) => {
    e.preventDefault();
    setError('');
    const f = Object.fromEntries(new FormData(e.currentTarget));
    api('/api/auth/recover', { method: 'POST', json: { token, ...f } })
      .then(() => { history.replaceState(null, '', window.location.pathname); setState('done'); })
      .catch((err) => setError(err.message));
  };

  return (
    <div className="auth-side">
      <div className="card">
        <h1>Admin recovery</h1>
        {state === 'missing' && <p>This link has no recovery token. Ask the DBA to run the recovery procedure again.</p>}
        {state === 'done' && (<><p>Your password is set.</p><a className="btn pri" href="./">Go to sign in</a></>)}
        {state === 'form' && (
          <form onSubmit={submit}>
            <p className="hint">Set your own password. This link works once and expires 1 hour after it was issued.</p>
            {error && <p className="error" role="alert">{error}</p>}
            <label className="field"><span>New password</span><input name="newPassword" type="password" autoComplete="new-password" required /><small>12 to 64 characters.</small></label>
            <label className="field"><span>Confirm new password</span><input name="confirmPassword" type="password" autoComplete="new-password" required /></label>
            <button className="btn pri">Set password</button>
          </form>
        )}
      </div>
    </div>
  );
}
