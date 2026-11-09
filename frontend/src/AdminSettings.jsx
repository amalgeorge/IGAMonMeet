import { useEffect, useState } from 'react';
import { api } from './api.js';
import { STATUS_LABELS, SITREP_TYPES } from './labels.js';

const TOGGLES = [
  ['SELF_REGISTRATION', 'Self-registration', 'People can create an account from the sign-in screen.'],
  ['REGISTRATION_APPROVAL', 'Approval step', 'New accounts wait for an Admin to approve them.'],
  ['FORGOT_PASSWORD', 'Forgot password requests', '"Forgot password?" sends a request to the Admin queue.'],
  ['QUEUE_BADGE', 'Admin queue badge', 'Shows how many items are waiting on the Admin queue tab.']
];

/** Admin renames the three KPI statuses for everyone (spec-v2 §2.4). */
function StatusLabelsForm() {
  const [labels, setLabels] = useState(null);
  const [msg, setMsg] = useState({});
  useEffect(() => { api('/api/status-labels').then(setLabels); }, []);
  if (!labels) return null;
  const save = (e) => {
    e.preventDefault(); setMsg({});
    api('/api/admin/status-labels', { method: 'PUT', json: labels })
      .then(() => { Object.assign(STATUS_LABELS, labels); setMsg({ ok: 'Saved. All Meetings, slides and exports use the new names.' }); })
      .catch((err) => setMsg({ error: err.message }));
  };
  return (
    <form className="tpl-card" onSubmit={save}>
      <strong>KPI status names</strong>
      <div className="grid">
        {[['ON_TARGET', 'On target'], ['BELOW_TARGET', 'Below target'], ['OFF_TARGET', 'Off target']].map(([k, d]) => (
          <label key={k} className="field"><span>{d}</span><input maxLength={30} required value={labels[k]} onChange={(e) => setLabels({ ...labels, [k]: e.target.value })} /></label>
        ))}
      </div>
      {msg.error && <p className="error" role="alert">{msg.error}</p>}
      {msg.ok && <p className="notice" role="status">{msg.ok}</p>}
      <button className="btn pri">Save names</button>
    </form>
  );
}

/** SITREP types suggested in the SITREP Type column (spec-v3 §1.3); editors may still type others. */
function SitrepTypesForm() {
  const [text, setText] = useState(null);
  const [msg, setMsg] = useState({});
  useEffect(() => { api('/api/sitrep-types').then((t) => setText(t.join('\n'))); }, []);
  if (text == null) return null;
  const save = (e) => {
    e.preventDefault(); setMsg({});
    const types = text.split('\n').map((t) => t.trim()).filter(Boolean);
    api('/api/admin/sitrep-types', { method: 'PUT', json: types })
      .then(() => { SITREP_TYPES.splice(0, SITREP_TYPES.length, ...types); setMsg({ ok: 'Saved.' }); })
      .catch((err) => setMsg({ error: err.message }));
  };
  return (
    <form className="tpl-card" onSubmit={save}>
      <strong>SITREP types</strong>
      <label className="field"><span>One per line, up to 20</span>
        <textarea rows={6} value={text} onChange={(e) => setText(e.target.value)} /></label>
      {msg.error && <p className="error" role="alert">{msg.error}</p>}
      {msg.ok && <p className="notice" role="status">{msg.ok}</p>}
      <button className="btn pri">Save types</button>
    </form>
  );
}

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
      <StatusLabelsForm />
      <SitrepTypesForm />
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
