import { useRef, useState } from 'react';
import { MAX_ROWS, parseCsv, planImport } from './csv.js';

/**
 * "Import CSV" for a table (spec-v3 §1.2): pick a file, see how its columns map and the first rows,
 * choose Replace or Append, then apply. Nothing changes until Apply.
 * onApply(columns, rows). allowNewColumns: Text table blocks may take the file's own headers.
 */
export default function CsvImport({ columns, rows, onApply, allowNewColumns = false }) {
  const input = useRef(null);
  const [plan, setPlan] = useState(null);
  const [mode, setMode] = useState('replace');

  const pick = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) { setPlan({ error: 'The file is larger than 2 MB.' }); return; }
    const p = planImport(columns, parseCsv(await file.text()), { allowNewColumns });
    setPlan({ ...p, fileName: file.name });
    setMode(p.newColumns || !rows.length ? 'replace' : 'replace');
  };

  const apply = () => {
    const next = mode === 'append' && !plan.newColumns ? [...rows, ...plan.rows].slice(0, MAX_ROWS) : plan.rows;
    onApply(plan.columns, next);
    setPlan(null);
  };

  return (
    <span className="csv-import">
      <input ref={input} type="file" accept=".csv,text/csv" hidden onChange={pick} />
      <button type="button" className="btn" onClick={() => input.current?.click()}>Import CSV</button>
      {plan && (
        <div className="csv-preview" role="dialog" aria-label="CSV import preview">
          <h4>Import {plan.fileName}</h4>
          {plan.error ? <p className="error" role="alert">{plan.error}</p> : (
            <>
              <p className="hint">
                {plan.rows.length} row{plan.rows.length === 1 ? '' : 's'}
                {plan.newColumns && ' · the file’s headers become the columns'}
                {plan.missing.length > 0 && ` · not in the file, left empty: ${plan.missing.join(', ')}`}
                {plan.ignored.length > 0 && ` · ignored from the file: ${plan.ignored.join(', ')}`}
                {plan.truncated && ` · only the first ${MAX_ROWS} rows are kept`}
              </p>
              <div className="table-wrap"><table>
                <thead><tr>{plan.columns.map((c, i) => <th key={i}>{c}</th>)}</tr></thead>
                <tbody>{plan.rows.slice(0, 10).map((r, i) => <tr key={i}>{r.map((x, j) => <td key={j}>{x}</td>)}</tr>)}</tbody>
              </table></div>
              {plan.rows.length > 10 && <p className="hint">… and {plan.rows.length - 10} more</p>}
              <fieldset className="row">
                <label className="check"><input type="radio" name="csv-mode" checked={mode === 'replace'} onChange={() => setMode('replace')} /> Replace the {rows.length} existing row{rows.length === 1 ? '' : 's'}</label>
                <label className="check"><input type="radio" name="csv-mode" disabled={plan.newColumns} checked={mode === 'append'} onChange={() => setMode('append')} /> Add after them</label>
              </fieldset>
            </>
          )}
          <div className="row">
            <button type="button" className="btn" onClick={() => setPlan(null)}>Cancel</button>
            {!plan.error && <button type="button" className="btn pri" onClick={apply}>Apply</button>}
          </div>
        </div>
      )}
    </span>
  );
}
