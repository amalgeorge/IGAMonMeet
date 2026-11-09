/**
 * Table columns as one input per column (spec-v3 §1.1): commas allowed, add, remove and reorder, up to 12.
 * onChange(nextColumns, from) where from[j] is the old index of new column j, or -1 for a new column,
 * so callers can carry row cells along.
 */
export const MAX_COLUMNS = 12;

export default function ColumnsEditor({ columns, onChange, label = 'Columns' }) {
  const same = columns.map((_, i) => i);
  const rename = (i, name) => onChange(columns.map((c, j) => (j === i ? name.replace(/\|/g, '') : c)), same);
  const add = () => onChange([...columns, `Column ${columns.length + 1}`], [...same, -1]);
  const remove = (i) => onChange(columns.filter((_, j) => j !== i), same.filter((j) => j !== i));
  const move = (i, d) => {
    const next = [...columns]; const from = [...same];
    [next[i], next[i + d]] = [next[i + d], next[i]];
    [from[i], from[i + d]] = [from[i + d], from[i]];
    onChange(next, from);
  };
  return (
    <fieldset className="columns-editor">
      <legend>{label} <span className="hint">({columns.length} of {MAX_COLUMNS})</span></legend>
      {columns.map((c, i) => (
        <div className="row" key={i}>
          <input aria-label={`Column ${i + 1} name`} value={c} maxLength={80} onChange={(e) => rename(i, e.target.value)} />
          <button type="button" className="btn" disabled={i === 0} aria-label={`Move column ${i + 1} left`} onClick={() => move(i, -1)}>↑</button>
          <button type="button" className="btn" disabled={i === columns.length - 1} aria-label={`Move column ${i + 1} right`} onClick={() => move(i, 1)}>↓</button>
          <button type="button" className="btn" disabled={columns.length === 1} aria-label={`Remove column ${i + 1}`} onClick={() => remove(i)}>×</button>
        </div>
      ))}
      {columns.some((c) => !c.trim()) && <p className="error">Every column needs a name.</p>}
      <button type="button" className="btn" disabled={columns.length >= MAX_COLUMNS} onClick={add}>Add column</button>
    </fieldset>
  );
}

/** Rows carried along a column change: cells follow their column, new columns start empty. */
export function remapRows(rows, from) {
  return (rows || []).map((r) => from.map((j) => (j >= 0 ? r[j] ?? '' : '')));
}
