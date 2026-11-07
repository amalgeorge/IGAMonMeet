import { useEffect, useState } from 'react';
import { api } from './api.js';

const fmt = new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeStyle: 'medium', timeZone: 'Asia/Singapore' });

export default function AuditLog() {
  const [filters, setFilters] = useState({ actor: '', action: '' });
  const [data, setData] = useState({ items: [], total: 0, page: 0 });
  const [page, setPage] = useState(0);

  const query = new URLSearchParams({ ...filters, page, size: 50 }).toString();
  useEffect(() => { api(`/api/admin/audit?${query}`).then(setData); }, [query]);

  const onFilter = (e) => {
    e.preventDefault();
    setPage(0);
    setFilters(Object.fromEntries(new FormData(e.currentTarget)));
  };

  return (
    <section>
      <div className="row">
        <h1>Audit log</h1>
        <a className="btn" href={`api/admin/audit.csv?${new URLSearchParams(filters)}`}>Download CSV</a>
      </div>
      <form className="filters" onSubmit={onFilter}>
        <label className="field"><span>User</span><input name="actor" defaultValue={filters.actor} /></label>
        <label className="field"><span>Action</span>
          <select name="action" defaultValue={filters.action}>
            <option value="">All</option>
            {['SIGN_IN', 'SIGN_IN_FAILED', 'LOCKED_OUT', 'REGISTERED', 'APPROVED', 'PASSWORD_CHANGED', 'CHANGED'].map((a) => <option key={a}>{a}</option>)}
          </select>
        </label>
        <button className="btn">Filter</button>
      </form>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Time (SGT)</th><th>User</th><th>Action</th><th>Target</th><th>Detail</th></tr></thead>
          <tbody>
            {data.items.map((e) => (
              <tr key={e.id}>
                <td className="tnum">{fmt.format(new Date(e.occurredAt))}</td>
                <td>{e.actor ?? '—'}</td>
                <td>{e.action}</td>
                <td>{e.targetType ? `${e.targetType} ${e.targetId ?? ''}` : ''}{e.field ? ` · ${e.field}` : ''}</td>
                <td>{e.field ? `${e.oldValue ?? '∅'} → ${e.newValue ?? '∅'}` : e.detail}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="row">
        <span className="hint">{data.total} entries</span>
        <span>
          <button className="btn" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>{' '}
          <button className="btn" disabled={(page + 1) * 50 >= data.total} onClick={() => setPage(page + 1)}>Next</button>
        </span>
      </div>
    </section>
  );
}
