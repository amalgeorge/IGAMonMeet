import { useState } from 'react';
import { api, ensureCsrf, csrfToken } from './api.js';

const CONF = (c, key) => (key == null ? ['No section', 'muted'] : c >= 1 ? ['Exact', 'held'] : c >= 0.8 ? ['Close', 'held'] : ['Check', 'lock']);

/** Upload → map slides to Sections → apply (Keep mine / Replace). */
export default function ImportPanel({ meetingId, sections, canEdit, onApplied }) {
  const [proposal, setProposal] = useState(null);
  const [mapping, setMapping] = useState({});
  const [mode, setMode] = useState('KEEP');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (!canEdit) return <p className="hint">This meeting's edit window is closed. Ask an Admin to import.</p>;

  const upload = async (e) => {
    e.preventDefault();
    setError(''); setResult(null); setBusy(true);
    await ensureCsrf();
    const res = await fetch(`api/meetings/${meetingId}/imports`, { method: 'POST', body: new FormData(e.currentTarget), credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': csrfToken() } });
    const body = await res.json().catch(() => ({}));
    setBusy(false);
    if (!res.ok) { setError(body.message || 'Upload failed.'); return; }
    setProposal(body);
    setMapping(Object.fromEntries(body.slides.map((s) => [s.n, s.sectionKey || ''])));
  };

  const apply = () => {
    setBusy(true);
    const mappings = Object.entries(mapping).filter(([, k]) => k).map(([n, k]) => ({ n: Number(n), sectionKey: k }));
    api(`/api/meetings/${meetingId}/imports/${proposal.importId}/apply`, { method: 'POST', json: { mode, mappings } })
      .then((r) => { setResult(r); setProposal(null); onApplied?.(); })
      .catch((e) => setError(e.message))
      .finally(() => setBusy(false));
  };

  return (
    <div className="export">
      {error && <p className="error" role="alert">{error}</p>}
      {!proposal && (
        <form className="filters" onSubmit={upload}>
          <label className="field grow"><span>PowerPoint file (.pptx or .potx, up to 50 MB)</span><input type="file" name="file" accept=".pptx,.potx" required /></label>
          <button className="btn pri" disabled={busy}>{busy ? 'Reading…' : 'Upload'}</button>
        </form>
      )}
      {result && (
        <div className="notice" role="status">
          <strong>Import applied.</strong> {result.applied.length} section(s) filled{result.keptWithNote.length ? `, ${result.keptWithNote.length} kept with the slide added as a note` : ''}
          {result.actionItemsAdded ? `, ${result.actionItemsAdded} action item(s) added` : ''}.
          {result.skipped.map((s) => <div key={s.sectionKey}>Skipped {s.sectionKey}: {s.reason}</div>)}
          {(result.picturesNotImported + result.chartsNotImported) > 0 && <div className="hint">{result.picturesNotImported} picture(s) and {result.chartsNotImported} chart(s) were not imported.</div>}
        </div>
      )}
      {proposal && (
        <>
          <h2>Map {proposal.slides.length} slides from {proposal.fileName}</h2>
          <div className="table-wrap"><table>
            <thead><tr><th>Slide</th><th>Title</th><th>Section</th><th>Match</th></tr></thead>
            <tbody>{proposal.slides.map((s) => {
              const [label, cls] = CONF(s.confidence, mapping[s.n] || null);
              return (
                <tr key={s.n}>
                  <td className="tnum">{s.n}</td>
                  <td>{s.title || <span className="hint">(no title)</span>}<div className="hint">{s.reason}</div></td>
                  <td><select aria-label={`Section for slide ${s.n}`} value={mapping[s.n]} onChange={(e) => setMapping({ ...mapping, [s.n]: e.target.value })}>
                    <option value="">Don't import</option>{sections.map((x) => <option key={x.key} value={x.key}>{x.name}</option>)}</select></td>
                  <td><span className={`chip ${cls}`}>{mapping[s.n] === (s.sectionKey || '') ? label : 'Your choice'}</span></td>
                </tr>
              );
            })}</tbody>
          </table></div>
          <fieldset className="filters">
            <legend className="label">When a section already has content</legend>
            <label className="check"><input type="radio" name="mode" checked={mode === 'KEEP'} onChange={() => setMode('KEEP')} /> Keep mine, add the slide as a note</label>
            <label className="check"><input type="radio" name="mode" checked={mode === 'REPLACE'} onChange={() => setMode('REPLACE')} /> Replace with the slide's content</label>
          </fieldset>
          <div className="row"><span className="hint">Slides mapped to the same section are merged in order. Action items are added, never removed.</span>
            <span><button className="btn" onClick={() => setProposal(null)}>Cancel</button>{' '}<button className="btn pri" disabled={busy} onClick={apply}>Apply import</button></span></div>
        </>
      )}
    </div>
  );
}
