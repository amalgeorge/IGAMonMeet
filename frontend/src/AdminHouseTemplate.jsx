import { useEffect, useState } from 'react';
import { api, ensureCsrf, csrfToken } from './api.js';

const fmt = new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Singapore' });
const LAYOUTS = 'IAM_TITLE, IAM_SECTION, IAM_SUMMARY, IAM_KPI, IAM_CHART, IAM_TABLE, IAM_TWO_COLUMN, IAM_CLOSING';

export default function AdminHouseTemplate() {
  const [data, setData] = useState(null);
  const [msg, setMsg] = useState({});
  const [busy, setBusy] = useState(false);
  const load = () => api('/api/admin/house-templates').then(setData);
  useEffect(() => { load(); }, []);

  const upload = async (e) => {
    e.preventDefault();
    setMsg({});
    setBusy(true);
    await ensureCsrf();
    const res = await fetch('api/admin/house-templates', { method: 'POST', body: new FormData(e.currentTarget), credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': csrfToken() } });
    const body = await res.json().catch(() => ({}));
    setBusy(false);
    if (res.ok) { setMsg({ ok: `Uploaded as version ${body.versionNo}. Exports and in-app slides now use it.` }); e.target.reset(); load(); } else setMsg({ error: body.message || 'Upload failed.' });
  };

  if (!data) return null;
  return (
    <section>
      <h1>House template</h1>
      <p>The PowerPoint master used for exports and for how slides look in the app. It must contain the layouts {LAYOUTS}, with their named placeholders.</p>
      <p><a className="link" href="api/admin/house-templates/0/file">Download the bundled default</a> to start from it.</p>
      {msg.error && <p className="error" role="alert">{msg.error}</p>}
      {msg.ok && <p className="notice" role="status">{msg.ok}</p>}
      <form className="filters card-inline" onSubmit={upload}>
        <label className="field grow"><span>.pptx or .potx, up to 50 MB</span><input type="file" name="file" accept=".pptx,.potx" required /></label>
        <button className="btn pri" disabled={busy}>{busy ? 'Checking…' : 'Upload'}</button>
      </form>
      <h2>Versions</h2>
      <div className="table-wrap"><table>
        <thead><tr><th>Version</th><th>File</th><th>Uploaded</th><th><span className="sr">Download</span></th></tr></thead>
        <tbody>{data.versions.map((v) => (
          <tr key={v.versionNo}>
            <td>{v.versionNo}{v.versionNo === data.current && <span className="chip held"> In use</span>}</td>
            <td>{v.fileName}</td>
            <td className="tnum">{v.uploadedAt ? `${fmt.format(new Date(v.uploadedAt))} by ${v.uploadedBy}` : '—'}</td>
            <td><a className="btn" href={`api/admin/house-templates/${v.versionNo}/file`}>Download</a></td>
          </tr>
        ))}</tbody>
      </table></div>
    </section>
  );
}
