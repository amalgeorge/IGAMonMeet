import { useEffect, useRef, useState } from 'react';
import { motion } from 'motion/react';
import * as echarts from 'echarts/core';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { SVGRenderer } from 'echarts/renderers';
import { api } from './api.js';

echarts.use([LineChart, GridComponent, TooltipComponent, SVGRenderer]);

const STATUS = { ON_TARGET: 'On target', BELOW_TARGET: 'Below target', OFF_TARGET: 'Off target' };
const STATUS_COLOR = { ON_TARGET: '#1e7d43', BELOW_TARGET: '#a35d00', OFF_TARGET: '#c8102e' };
const HEALTH_COLOR = ['#1e7d43', '#a35d00', '#c8102e', '#5b6770', '#8a96a0'];
const dateFmt = new Intl.DateTimeFormat('en-SG', { day: 'numeric', month: 'short', timeZone: 'Asia/Singapore' });
const reduced = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

function Spark({ trend, target }) {
  const ref = useRef(null);
  useEffect(() => {
    if (!ref.current) return undefined;
    const chart = echarts.init(ref.current, null, { renderer: 'svg' });
    chart.setOption({
      animation: !reduced,
      grid: { left: 4, right: 4, top: 6, bottom: 18 },
      tooltip: { trigger: 'axis', valueFormatter: (v) => (v == null ? 'No value' : v) },
      xAxis: { type: 'category', data: trend.labels, axisTick: { show: false }, axisLine: { show: false }, axisLabel: { fontSize: 10, color: '#5b6770', interval: 'auto' } },
      yAxis: { type: 'value', show: false, scale: true },
      series: [{
        type: 'line', data: trend.values, smooth: true, connectNulls: false, symbolSize: 5,
        lineStyle: { color: '#c8102e', width: 2 }, itemStyle: { color: '#c8102e' },
        areaStyle: { color: 'rgba(200,16,46,.08)' },
        markLine: target == null ? undefined : { silent: true, symbol: 'none', label: { show: false }, lineStyle: { color: '#5b6770', type: 'dashed' }, data: [{ yAxis: target }] },
      }],
    });
    const ro = new ResizeObserver(() => chart.resize());
    ro.observe(ref.current);
    return () => { ro.disconnect(); chart.dispose(); };
  }, [trend, target]);
  return <div ref={ref} className="spark" role="img" aria-label={`Trend: ${trend.labels.map((l, i) => `${l} ${trend.values[i] ?? 'no value'}`).join(', ')}`} />;
}

/** KPI tiles under their Section group's title, in Template order (spec-v2 §1.5). */
function groupTiles(tiles) {
  const out = [];
  tiles.forEach((t) => {
    const last = out[out.length - 1];
    if (last && last.group === (t.group || null)) last.tiles.push(t); else out.push({ group: t.group || null, tiles: [t] });
  });
  return out;
}

/** Text status KPIs: one coloured block per month instead of a line (spec-v2 §2.2). */
function Strip({ trend }) {
  return (
    <div className="strip" role="img" aria-label={`History: ${trend.labels.map((l, i) => `${l} ${STATUS[trend.statuses[i]] || 'no value'}`).join(', ')}`}>
      {trend.labels.map((l, i) => (
        <span key={l + i} title={`${l}: ${STATUS[trend.statuses[i]] || 'no value'}`} style={{ background: STATUS_COLOR[trend.statuses[i]] || '#e3e6e8' }} />
      ))}
    </div>
  );
}

const rise = (i) => ({ initial: reduced ? { opacity: 0 } : { opacity: 0, y: 12 }, animate: { opacity: 1, y: 0 }, transition: { delay: reduced ? 0 : i * 0.04, duration: 0.3 } });

export default function DashboardPanel({ meetingId }) {
  const [d, setD] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => { api(`/api/meetings/${meetingId}/dashboard`).then(setD).catch((e) => setError(e.message || 'Could not load dashboard')); }, [meetingId]);
  if (error) return <p className="error" role="alert">{error}</p>;
  if (!d) return <p className="hint">Loading dashboard…</p>;
  const pct = d.requiredSections ? Math.round((100 * d.requiredFilled) / d.requiredSections) : 100;
  return (
    <div className="dash">
      <div className="dash-stats">
        {[['Required sections', `${d.requiredFilled} / ${d.requiredSections}`, `${pct}% filled`],
          ['Open action items', d.openActions, `${d.overdueActions} overdue`],
          ['Open comments', d.openComments, 'unresolved threads']].map(([label, value, sub], i) => (
          <motion.div key={label} className={`dash-stat${label.startsWith('Open action') && d.overdueActions ? ' warn' : ''}`} {...rise(i)}>
            <span className="dash-label">{label}</span><span className="dash-big">{value}</span><span className="hint">{sub}</span>
          </motion.div>
        ))}
      </div>

      <h2>KPIs</h2>
      {d.kpis.length === 0 ? <p className="hint">No KPIs in this Meeting's Template.</p> : (
        groupTiles(d.kpis).map(({ group, tiles }) => (<div key={group || '_'}>
        {group && <h3 className="dash-group">{group}</h3>}
        <div className="dash-kpis">
          {tiles.map(({ kpi, section, trend }, i) => (
            <motion.div key={kpi.key} className="kpi-tile dash-kpi" {...rise(i + 3)}>
              <span className="dash-label">{kpi.name} <span className="hint">· {section}</span></span>
              <span className="kpi-value">{kpi.display}</span>
              {kpi.status && <span className="dash-pill" style={{ color: STATUS_COLOR[kpi.status], borderColor: STATUS_COLOR[kpi.status] }}>{STATUS[kpi.status]}{kpi.override ? ' (override)' : ''}</span>}
              <span className="hint">{[kpi.targetLine, kpi.deltaLine].filter(Boolean).join(' · ')}</span>
              {trend.statuses ? <Strip trend={trend} /> : <Spark trend={trend} target={kpi.target} />}
            </motion.div>
          ))}
        </div></div>))
      )}

      {d.health.length > 0 && <>
        <h2>Health</h2>
        {d.health.map((h) => {
          const entries = Object.entries(h.counts);
          return (
            <div key={h.sectionKey} className="dash-health">
              <span className="dash-label">{h.section} <span className="hint">· {h.total} items</span></span>
              {h.total === 0 ? <span className="hint">No rows yet</span> : <>
                <div className="dash-bar" role="img" aria-label={entries.map(([k, v]) => `${k}: ${v}`).join(', ')}>
                  {entries.map(([k, v], i) => (
                    <motion.span key={k} style={{ background: HEALTH_COLOR[i % HEALTH_COLOR.length] }} initial={{ width: 0 }} animate={{ width: `${(100 * v) / h.total}%` }} transition={{ duration: reduced ? 0 : 0.5, delay: reduced ? 0 : i * 0.05 }} />
                  ))}
                </div>
                <ul className="dash-legend">{entries.map(([k, v], i) => <li key={k}><i style={{ background: HEALTH_COLOR[i % HEALTH_COLOR.length] }} />{k} {v}</li>)}</ul>
              </>}
            </div>
          );
        })}
      </>}

      <div className="dash-cols">
        <section>
          <h2>Open action items</h2>
          {d.actions.length === 0 ? <p className="hint">No open action items.</p> : (
            <ul className="dash-list">{d.actions.map((a) => (
              <li key={a.id} className={a.overdue ? 'overdue' : ''}>
                <strong>{a.title}</strong>
                <span className="hint">{a.owner || 'No owner'} · {a.teamTag} · {a.dueDate ? `due ${dateFmt.format(new Date(`${a.dueDate}T00:00:00+08:00`))}` : 'no due date'}{a.overdue ? ' · Overdue' : ''}</span>
              </li>))}
            </ul>
          )}
        </section>
        <section>
          <h2>Open comments</h2>
          {d.comments.length === 0 ? <p className="hint">No open comments.</p> : (
            <ul className="dash-list">{d.comments.map((c, i) => (
              <li key={i}>
                <span>{c.body}</span>
                <span className="hint">{c.author} on {c.section} · {dateFmt.format(new Date(c.createdAt))}{c.replies ? ` · ${c.replies} ${c.replies === 1 ? 'reply' : 'replies'}` : ''}</span>
              </li>))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
