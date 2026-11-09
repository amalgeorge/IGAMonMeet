/** Table styling (spec-v2 §3.3), shared by Admin's TABLE Sections and editors' table blocks. Every part is optional. */
export default function TableStyleEditor({ columns, value, onChange }) {
  const v = value || {};
  const n = columns.length;
  const widths = v.widths && v.widths.length === n ? v.widths : null;
  const align = v.align && v.align.length === n ? v.align : null;
  const set = (patch) => {
    const next = { ...v, ...patch };
    Object.keys(next).forEach((k) => (next[k] == null || next[k] === false) && delete next[k]);
    onChange(Object.keys(next).length ? next : null);
  };
  return (
    <details className="table-style">
      <summary>Table style</summary>
      <div className="table-wrap"><table>
        <thead><tr><th>Column</th>{columns.map((c) => <th key={c}>{c}</th>)}</tr></thead>
        <tbody>
          <tr><td>Width (share)</td>{columns.map((c, i) => (
            <td key={c}><input aria-label={`Width of ${c}`} type="number" min={1} max={100} size={4} value={widths ? widths[i] : ''} placeholder="auto"
              onChange={(e) => { const w = widths ? [...widths] : columns.map(() => 1); w[i] = Number(e.target.value) || 1; set({ widths: w }); }} /></td>))}</tr>
          <tr><td>Alignment</td>{columns.map((c, i) => (
            <td key={c}><select aria-label={`Alignment of ${c}`} value={align ? align[i] : 'left'}
              onChange={(e) => { const a = align ? [...align] : columns.map(() => 'left'); a[i] = e.target.value; set({ align: a.every((x) => x === 'left') ? null : a }); }}>
              <option value="left">Left</option><option value="center">Centre</option><option value="right">Right</option></select></td>))}</tr>
        </tbody>
      </table></div>
      <div className="options-row">
        <label className="field"><span>Colour rows by status in</span>
          <select value={v.statusColumn ?? ''} onChange={(e) => set({ statusColumn: e.target.value === '' ? null : Number(e.target.value) })}>
            <option value="">No colouring</option>{columns.map((c, i) => <option key={c} value={i}>{c}</option>)}</select></label>
        <label className="check"><input type="checkbox" checked={!!v.total} onChange={(e) => set({ total: e.target.checked })} /> Total row</label>
        <label className="check"><input type="checkbox" checked={!!v.banded} onChange={(e) => set({ banded: e.target.checked })} /> Banded rows</label>
        {widths && <button className="btn" onClick={() => set({ widths: null })}>Reset widths</button>}
      </div>
      <p className="hint">Status colours: On track / Done / Green, At risk / Amber / Delayed, Off track / Red / Blocked. The total row adds up columns that hold only numbers.</p>
    </details>
  );
}
