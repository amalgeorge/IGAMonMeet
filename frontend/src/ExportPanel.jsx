import { useState } from 'react';
import { ensureCsrf, csrfToken } from './api.js';

/**
 * Export options: Sections, speaker notes, comments appendix, export stamp. PDF uses the browser's print to PDF.
 * JSON is the document-shaped export for converting to Word (docs/json-export.md).
 */
export default function ExportPanel({ meetingId, sections }) {
  const [picked, setPicked] = useState(() => Object.fromEntries(sections.map((s) => [s.key, true])));
  const [opts, setOpts] = useState({ notes: true, comments: false, versionStamp: true });
  const [state, setState] = useState('');

  const download = async (format = 'pptx') => {
    setState('Building…');
    await ensureCsrf();
    const keys = sections.filter((s) => picked[s.key]).map((s) => s.key);
    const res = await fetch(`api/meetings/${meetingId}/export/${format}`, {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken() },
      body: JSON.stringify({ sections: keys.length === sections.length ? null : keys, ...opts })
    });
    if (!res.ok) { setState('Export failed. Try again.'); return; }
    const blob = await res.blob();
    const name = (res.headers.get('Content-Disposition') || '').match(/filename="?([^"]+)"?/)?.[1] || `meeting.${format}`;
    const a = Object.assign(document.createElement('a'), { href: URL.createObjectURL(blob), download: name });
    a.click();
    URL.revokeObjectURL(a.href);
    setState('Downloaded.');
  };

  return (
    <div className="export">
      <fieldset>
        <legend className="label">Sections</legend>
        {sections.map((s) => (
          <label key={s.key} className="check"><input type="checkbox" checked={picked[s.key]} onChange={(e) => setPicked({ ...picked, [s.key]: e.target.checked })} /> {s.name}</label>
        ))}
      </fieldset>
      <fieldset>
        <legend className="label">Include</legend>
        <label className="check"><input type="checkbox" checked={opts.notes} onChange={(e) => setOpts({ ...opts, notes: e.target.checked })} /> Speaker notes</label>
        <label className="check"><input type="checkbox" checked={opts.comments} onChange={(e) => setOpts({ ...opts, comments: e.target.checked })} /> Comments as appendix</label>
        <label className="check"><input type="checkbox" checked={opts.versionStamp} onChange={(e) => setOpts({ ...opts, versionStamp: e.target.checked })} /> Export stamp in footer</label>
      </fieldset>
      <div className="row">
        <span className="hint" role="status">{state}</span>
        <span>
          <a className="btn" href={`${window.location.pathname}#print/${meetingId}`} target="_blank" rel="noopener">PDF (print view)</a>{' '}
          <button className="btn" onClick={() => download('json')} title="Document-shaped JSON, ready to convert to Word">JSON (for Word)</button>{' '}
          <button className="btn pri" onClick={() => download('pptx')}>Download .pptx</button>
        </span>
      </div>
    </div>
  );
}

/** All slides one per page; the browser's "Save as PDF" makes the PDF. */
export function PrintView({ meetingId, SlideRenderer, useDeck }) {
  const { plan, theme } = useDeck(meetingId);
  if (!plan || !theme) return <p>Preparing slides…</p>;
  setTimeout(() => window.print(), 600);
  return (
    <div className="print">
      {plan.slides.map((s) => <div key={s.n} className="print-page"><SlideRenderer slide={s} theme={theme} total={plan.slides.length} /></div>)}
    </div>
  );
}
