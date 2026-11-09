import { useEffect, useState } from 'react';
import { api, csrfToken, ensureCsrf } from './api.js';
import SlideRenderer from './SlideRenderer.jsx';

/** Admin › Themes (spec-v2 §4.1): colours, fonts, backgrounds and logo over the House template, with a live preview. */
const COLORS = [['primary', 'Primary (title and divider slides)'], ['accent', 'Accent'], ['text', 'Text'], ['background', 'Content slide background'],
  ['onTarget', 'On target'], ['belowTarget', 'Below target'], ['offTarget', 'Off target']];
const BACKGROUNDS = [['title', 'Title slide'], ['divider', 'Divider and closing slides'], ['content', 'Content slides']];
const BLANK = { colors: { primary: 'C8102E', accent: 'ED1B2E', text: '1A1A1A', background: 'FFFFFF', onTarget: '1E7D43', belowTarget: 'A35D00', offTarget: 'C8102E' },
  fonts: { heading: 'Arial', body: 'Arial' }, backgrounds: { title: { color: 'C8102E' }, divider: { color: 'C8102E' }, content: { color: 'FFFFFF' } }, logo: { show: true } };

const SAMPLE = [
  { n: 1, layout: 'IAM_TITLE', data: { title: 'IAM Monthly Meeting', subtitle: 'November 2026', meta: 'Thursday 26 November 2026' } },
  { n: 2, layout: 'IAM_SECTION', data: { title: 'IAM Operations', body: 'Key metrics, Incidents' } },
  { n: 3, layout: 'IAM_KPI', data: { team: 'IAM OPERATIONS', title: 'Key metrics', perSlide: 4, body: '', kpis: [
    { key: 'a', name: 'Access requests within SLA', display: '96.4%', targetLine: 'Target ≥ 95%', status: 'ON_TARGET' },
    { key: 'b', name: 'Mean provisioning time', display: '5 h', targetLine: 'Target ≤ 4 h', status: 'BELOW_TARGET' },
    { key: 'c', name: 'Leaver access removed', display: '97%', targetLine: 'Target ≥ 100%', status: 'OFF_TARGET' },
    { key: 'd', name: 'Certification complete', display: '88%', targetLine: 'Target ≥ 90%', status: 'BELOW_TARGET' }] } },
  { n: 4, layout: 'IAM_TABLE', data: { team: 'IAM ENGINEERING', title: 'Roadmap', head: ['Initiative', 'Owner', 'Status'], colWidths: [50, 25, 25], fontSize: 14,
    rows: [['SSO wave 3', 'J. Tan', 'On track'], ['PAM pilot', 'R. Lim', 'At risk']], rowFlags: ['status:ON_TARGET', 'status:BELOW_TARGET'] } },
];

export default function AdminThemes() {
  const [themes, setThemes] = useState([]);
  const [fonts, setFonts] = useState({ safe: ['Arial'], brand: [] });
  const [sel, setSel] = useState(null); // { id, name, versionNo, settings } or { id: null } for a new Theme
  const [preview, setPreview] = useState(null);
  const [msg, setMsg] = useState({});

  const load = (pick) => api('/api/themes').then((t) => {
    setThemes(t);
    const next = pick != null ? t.find((x) => x.id === pick) : t.find((x) => x.isDefault) || t[0];
    if (next) setSel({ ...next, settings: structuredClone(next.settings) });
  });
  useEffect(() => { load(); api('/api/admin/themes/fonts').then(setFonts).catch(() => {}); }, []);
  useEffect(() => {
    if (!sel) return undefined;
    const t = setTimeout(() => api('/api/admin/themes/preview', { method: 'POST', json: sel.settings }).then(setPreview).catch((e) => setMsg({ error: e.message })), 250);
    return () => clearTimeout(t);
  }, [sel?.settings]);

  if (!sel) return null;
  const s = sel.settings;
  const set = (path, value) => {
    const next = structuredClone(s);
    let o = next;
    path.slice(0, -1).forEach((k) => { o[k] = o[k] || {}; o = o[k]; });
    o[path[path.length - 1]] = value;
    setSel({ ...sel, settings: next });
  };
  const upload = async (file, kind, path) => {
    setMsg({});
    await ensureCsrf();
    const form = new FormData();
    form.append('file', file);
    form.append('kind', kind);
    const res = await fetch('api/admin/theme-images', { method: 'POST', body: form, credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': csrfToken() } });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) { setMsg({ error: body.message || 'Upload failed.' }); return; }
    set(path, body.id);
  };
  const save = () => {
    setMsg({});
    const req = sel.id == null
      ? api('/api/admin/themes', { method: 'POST', json: { name: sel.name, settings: s } })
      : api(`/api/admin/themes/${sel.id}`, { method: 'PUT', json: { name: sel.name, settings: s, basedOnVersion: sel.versionNo } });
    req.then((t) => { setMsg({ ok: `Saved ${t.name} (version ${t.versionNo}).` }); load(t.id); }).catch((e) => setMsg({ error: e.message }));
  };
  const allFonts = [...fonts.safe, ...fonts.brand];

  return (
    <section>
      <div className="row"><h1>Themes</h1>
        <span>
          <select aria-label="Theme" value={sel.id ?? ''} onChange={(e) => load(Number(e.target.value))}>
            {themes.map((t) => <option key={t.id} value={t.id}>{t.name}{t.isDefault ? ' (default)' : ''}</option>)}
            {sel.id == null && <option value="">{sel.name || 'New Theme'}</option>}
          </select>{' '}
          <button className="btn" onClick={() => setSel({ id: null, name: 'New Theme', settings: structuredClone(s || BLANK) })}>New Theme</button>
        </span>
      </div>
      {msg.error && <p className="error" role="alert">{msg.error}</p>}
      {msg.ok && <p className="notice" role="status">{msg.ok}</p>}
      <div className="theme-edit">
        <div className="tpl-card">
          <label className="field"><span>Name</span><input maxLength={100} value={sel.name} onChange={(e) => setSel({ ...sel, name: e.target.value })} /></label>
          <h3>Colours</h3>
          <div className="grid">{COLORS.map(([k, label]) => (
            <label key={k} className="field"><span>{label}</span>
              <input type="color" value={`#${s.colors?.[k] || '000000'}`} onChange={(e) => set(['colors', k], e.target.value.slice(1).toUpperCase())} /></label>))}</div>
          <h3>Fonts</h3>
          <div className="grid">{[['heading', 'Headings'], ['body', 'Body text']].map(([k, label]) => (
            <label key={k} className="field"><span>{label}</span>
              <select value={s.fonts?.[k] || 'Arial'} onChange={(e) => set(['fonts', k], e.target.value)}>
                {allFonts.map((f) => <option key={f} value={f}>{f}{fonts.brand.includes(f) ? ' (brand)' : ''}</option>)}</select></label>))}</div>
          <p className="hint">Brand fonts appear here once they are installed on the server and listed in app.brand-fonts.</p>
          <h3>Backgrounds</h3>
          {BACKGROUNDS.map(([k, label]) => (
            <div key={k} className="options-row">
              <strong>{label}</strong>
              <input type="color" aria-label={`${label} colour`} value={`#${s.backgrounds?.[k]?.color || 'FFFFFF'}`} onChange={(e) => set(['backgrounds', k, 'color'], e.target.value.slice(1).toUpperCase())} />
              <label className="btn">Image…<input type="file" accept="image/png,image/jpeg" hidden onChange={(e) => e.target.files[0] && upload(e.target.files[0], 'background', ['backgrounds', k, 'imageId'])} /></label>
              {s.backgrounds?.[k]?.imageId && <button className="btn" onClick={() => set(['backgrounds', k, 'imageId'], null)}>Remove image</button>}
            </div>))}
          <p className="hint">PNG or JPG up to 5 MB. Images are cropped to 16:9 from the centre.</p>
          <h3>Logo</h3>
          <div className="options-row">
            <label className="check"><input type="checkbox" checked={s.logo?.show !== false} onChange={(e) => set(['logo', 'show'], e.target.checked)} /> Show logo</label>
            <label className="btn">Replace logo…<input type="file" accept="image/png,image/jpeg" hidden onChange={(e) => e.target.files[0] && upload(e.target.files[0], 'logo', ['logo', 'imageId'])} /></label>
            {s.logo?.imageId && <button className="btn" onClick={() => set(['logo', 'imageId'], null)}>Use House template logo</button>}
          </div>
          <div className="row">
            <button className="btn pri" onClick={save}>{sel.id == null ? 'Create Theme' : 'Save new version'}</button>
            {sel.id != null && !sel.isDefault && <button className="btn" onClick={() => api(`/api/admin/themes/${sel.id}/default`, { method: 'POST' }).then(() => load(sel.id))}>Make default for new Meetings</button>}
          </div>
        </div>
        <div className="theme-preview" aria-label="Preview">
          {preview && SAMPLE.map((sl) => <div key={sl.n} className="thumb-frame"><SlideRenderer slide={sl} theme={preview} /></div>)}
        </div>
      </div>
    </section>
  );
}
