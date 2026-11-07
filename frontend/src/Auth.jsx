import { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { api } from './api.js';

const fade = { initial: { opacity: 0, y: 12 }, animate: { opacity: 1, y: 0 }, exit: { opacity: 0, y: -12 }, transition: { duration: 0.2 } };

export default function Auth({ onSignedIn }) {
  const [mode, setMode] = useState('signin');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [features, setFeatures] = useState({ selfRegistration: true, forgotPassword: true });
  useEffect(() => { api('/api/auth/features').then(setFeatures).catch(() => {}); }, []);

  async function submit(e, action) {
    e.preventDefault();
    setError('');
    setBusy(true);
    const f = Object.fromEntries(new FormData(e.currentTarget));
    try {
      await action(f);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  const signIn = (f) => api('/api/auth/login', { method: 'POST', form: f }).then(onSignedIn);
  const forgot = (f) => api('/api/auth/forgot', { method: 'POST', json: f }).then(() => setMode('requested'));
  const register = (f) => api('/api/auth/register', { method: 'POST', json: f }).then(() => setMode('pending'));

  return (
    <div className="auth">
      <section className="auth-hero">
        <div className="brand">IAM Monthly Review</div>
        <div>
          <h1>IAM MONTHLY MEETING</h1>
          <p>IAM ENG &amp; OPS</p>
        </div>
      </section>
      <section className="auth-side">
        <div className="card">
          {features.selfRegistration && (mode === 'signin' || mode === 'register') && (
            <div className="tabs" role="group" aria-label="Account">
              <button type="button" className={mode === 'signin' ? 'on' : ''} aria-pressed={mode === 'signin'} onClick={() => { setMode('signin'); setError(''); }}>Sign in</button>
              <button type="button" className={mode === 'register' ? 'on' : ''} aria-pressed={mode === 'register'} onClick={() => { setMode('register'); setError(''); }}>Register</button>
            </div>
          )}
          {error && <p className="error" role="alert">{error}</p>}
          <AnimatePresence mode="wait" initial={false}>
            {mode === 'signin' && (
              <motion.form key="signin" {...fade} onSubmit={(e) => submit(e, signIn)}>
                <Field label="Username" name="username" autoComplete="username" />
                <Field label="Password" name="password" type="password" autoComplete="current-password" />
                {features.forgotPassword && <button type="button" className="link" onClick={() => { setMode('forgot'); setError(''); }}>Forgot password?</button>}
                <button className="btn pri" disabled={busy}>Sign in</button>
              </motion.form>
            )}
            {mode === 'register' && (
              <motion.form key="register" {...fade} onSubmit={(e) => submit(e, register)}>
                <Field label="Full name" name="fullName" autoComplete="name" />
                <Field label="Username" name="username" autoComplete="username" />
                <Field label="Work email" name="email" type="email" autoComplete="email" />
                <Field label="Password" name="password" type="password" autoComplete="new-password" hint="12 to 64 characters." />
                <Field label="Confirm password" name="confirmPassword" type="password" autoComplete="new-password" />
                <button className="btn pri" disabled={busy}>Create account</button>
                <p className="hint">An Admin approves each new account and sets its access.</p>
              </motion.form>
            )}
            {mode === 'forgot' && (
              <motion.form key="forgot" {...fade} onSubmit={(e) => submit(e, forgot)}>
                <h2>Forgot password</h2>
                <p className="hint">An Admin will reset it and give you a temporary password.</p>
                <Field label="Username" name="username" autoComplete="username" />
                <button className="btn pri" disabled={busy}>Send request to Admin</button>
                <button type="button" className="link" onClick={() => setMode('signin')}>Back to sign in</button>
              </motion.form>
            )}
            {mode === 'requested' && (
              <motion.div key="requested" {...fade}>
                <h2>Request sent</h2>
                <p>If the username exists, an Admin will reset the password and give you a temporary one.</p>
                <button className="btn" onClick={() => setMode('signin')}>Back to sign in</button>
              </motion.div>
            )}
            {mode === 'pending' && (
              <motion.div key="pending" {...fade}>
                <h2>Awaiting admin approval</h2>
                <p>Your account is created. You can sign in once an Admin approves it.</p>
                <button className="btn" onClick={() => setMode('signin')}>Back to sign in</button>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </section>
    </div>
  );
}

function Field({ label, name, hint, ...props }) {
  return (
    <label className="field">
      <span>{label}</span>
      <input name={name} required {...props} />
      {hint && <small>{hint}</small>}
    </label>
  );
}
