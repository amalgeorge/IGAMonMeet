import { useEffect, useState } from 'react';
import { api } from './api.js';

const TOGGLES = [
  ['SELF_REGISTRATION', 'Self-registration', 'People can create an account from the sign-in screen.'],
  ['REGISTRATION_APPROVAL', 'Approval step', 'New accounts wait for an Admin to approve them.'],
  ['FORGOT_PASSWORD', 'Forgot password requests', '"Forgot password?" sends a request to the Admin queue.'],
  ['QUEUE_BADGE', 'Admin queue badge', 'Shows how many items are waiting on the Admin queue tab.']
];

export default function AdminSettings({ onChange }) {
  const [values, setValues] = useState(null);
  const [pending, setPending] = useState(null);
  const load = () => api('/api/settings/features').then(setValues);
  useEffect(() => { load(); }, []);

  const apply = () => api(`/api/admin/settings/${pending.key}`, { method: 'PUT', json: { enabled: pending.on } })
    .then(() => { setPending(null); load(); onChange?.(); });

  if (!values) return null;
  return (
    <section>
      <h1>Settings</h1>
      <ul className="toggles">
        {TOGGLES.map(([key, label, help]) => (
          <li key={key}>
            <div><strong>{label}</strong><div className="hint">{help}</div></div>
            <button role="switch" aria-checked={values[key]} aria-label={label} className={`switch ${values[key] ? 'on' : ''}`}
              onClick={() => setPending({ key, label, on: !values[key] })}><span /></button>
          </li>
        ))}
      </ul>
      {pending && (
        <div className="notice" role="alertdialog" aria-label="Confirm change">
          <p><strong>Turn {pending.on ? 'on' : 'off'} “{pending.label}”?</strong> This change is recorded in the audit log.</p>
          {pending.key === 'REGISTRATION_APPROVAL' && !pending.on && (
            <p className="error">Security warning: anyone who can reach this app will be able to create an account and read IAM meeting data without an Admin checking them.</p>
          )}
          <button className="btn pri" onClick={apply}>Confirm</button>{' '}
          <button className="btn" onClick={() => setPending(null)}>Cancel</button>
        </div>
      )}
    </section>
  );
}
