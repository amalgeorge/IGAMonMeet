import { useEffect, useState } from 'react';
import { api } from './api.js';

/** Available Sections and KPIs an editor can switch on for this Meeting (spec-v2 §1.3). */
export default function AvailablePanel({ meetingId, sections, canEdit, onChange }) {
  const [cat, setCat] = useState(null);
  const [error, setError] = useState('');
  const load = () => api(`/api/meetings/${meetingId}/available`).then(setCat).catch(() => setCat(null));
  useEffect(() => { load(); }, [meetingId, sections]);

  const call = (method, path) => {
    setError('');
    api(`/api/meetings/${meetingId}/available/${path}`, { method }).then(() => { onChange(); load(); }).catch((e) => setError(e.message));
  };
  const nameOf = (key) => sections.find((s) => s.key === key)?.name || key;
  const switchedSections = sections.filter((s) => s.switchedOn);
  const switchedKpis = sections.flatMap((s) => (s.switchedOnKpis || []).map((k) => ({ section: s, key: k, name: s.kpis.find((x) => x.key === k)?.name || k })));

  if (!canEdit || !cat || (!cat.open && switchedSections.length === 0 && switchedKpis.length === 0)) return null;
  const nothing = cat.sections.length === 0 && cat.kpis.length === 0;
  return (
    <div className="available">
      <div className="label">Available items</div>
      {!cat.open && <p className="hint">This Meeting has been held; its Sections and KPIs no longer change.</p>}
      {cat.open && nothing && <p className="hint">Nothing else is available for this Meeting.</p>}
      <ul>
        {cat.sections.map((s) => (
          <li key={s.key}><span>{s.name}</span><button className="btn" onClick={() => call('POST', `sections/${s.key}`)}>Add</button></li>
        ))}
        {cat.kpis.map((k) => (
          <li key={`${k.sectionKey}.${k.key}`}><span>{k.name} <span className="hint">· KPI in {nameOf(k.sectionKey)}</span></span>
            <button className="btn" onClick={() => call('POST', `sections/${k.sectionKey}/kpis/${k.key}`)}>Add</button></li>
        ))}
        {cat.open && switchedSections.map((s) => (
          <li key={`on-${s.key}`}><span>{s.name} <span className="hint">· added</span></span>
            <button className="btn" onClick={() => call('DELETE', `sections/${s.key}`)}>Remove</button></li>
        ))}
        {cat.open && switchedKpis.map((k) => (
          <li key={`on-${k.section.key}.${k.key}`}><span>{k.name} <span className="hint">· KPI added to {k.section.name}</span></span>
            <button className="btn" onClick={() => call('DELETE', `sections/${k.section.key}/kpis/${k.key}`)}>Remove</button></li>
        ))}
      </ul>
      {error && <p className="error" role="alert">{error}</p>}
    </div>
  );
}
