import { useEffect, useRef, useState } from 'react';
import { api } from './api.js';
import SlideRenderer from './SlideRenderer.jsx';

/**
 * Admin's slide layout for one Template Section (spec-v3 §6): boxes on a 12 x 8 grid over a live preview with
 * sample data. Drag a box to move it, drag its corner to resize; both snap to the grid. Saved with the Template.
 */
const COLS = 12;
const ROWS = 8;
const LABEL = { title: 'Title', content: 'Content', chart: 'Chart', left: 'Left column', right: 'Right column' };

export function itemsFor(kind) {
  if (kind === 'KPI') return ['title', 'tiles'];
  if (kind === 'CHART') return ['title', 'chart', 'content'];
  if (kind === 'TWO_COLUMN') return ['title', 'left', 'right'];
  return ['title', 'content'];
}

/** The generated layout expressed as grid boxes: where Reset goes back to, and where editing starts. */
export function defaultLayout(section) {
  const l = { title: { x: 0, y: 1, w: 12, h: 1 } };
  if (section.kind === 'KPI') {
    const n = Math.max(1, Number(section.kpiLayout) || 4);
    const cols = n <= 4 ? n : Math.ceil(n / 2);
    const rows = n <= 4 ? 1 : 2;
    const w = Math.max(1, Math.floor(12 / cols));
    const h = rows === 1 ? 4 : 2;
    l.tiles = Array.from({ length: n }, (_, i) => ({ x: (i % cols) * w, y: 2 + Math.floor(i / cols) * h, w, h }));
  } else if (section.kind === 'CHART') {
    l.chart = { x: 0, y: 2, w: 7, h: 5 };
    l.content = { x: 7, y: 2, w: 5, h: 5 };
  } else if (section.kind === 'TWO_COLUMN') {
    l.left = { x: 0, y: 2, w: 6, h: 5 };
    l.right = { x: 6, y: 2, w: 6, h: 5 };
  } else {
    l.content = { x: 0, y: 2, w: 12, h: 5 };
  }
  return l;
}

const pct = (b) => ({ x: (b.x / COLS) * 100, y: (b.y / ROWS) * 100, w: (b.w / COLS) * 100, h: (b.h / ROWS) * 100 });

function frameOf(layout) {
  const f = {};
  Object.entries(layout).forEach(([k, v]) => { f[k] = k === 'tiles' ? v.map(pct) : pct(v); });
  return f;
}

/** A sample slide of this Section so the Admin sees real-looking content in the boxes. */
function sampleSlide(section, layout) {
  const base = { team: section.teamTag === 'ENGINEERING' ? 'IAM ENGINEERING' : section.teamTag === 'OPERATIONS' ? 'IAM OPERATIONS' : 'IAM', title: section.name, frame: frameOf(layout) };
  const kind = section.kind;
  if (kind === 'KPI') {
    const n = layout.tiles?.length || 4;
    const kpis = Array.from({ length: n }, (_, i) => ({ key: `k${i}`, name: (section.kpis?.[i]?.key || `KPI ${i + 1}`).replace(/-/g, ' '), display: `${90 + i}%`,
      targetLine: 'Target ≥ 95%', status: ['ON_TARGET', 'BELOW_TARGET', 'OFF_TARGET'][i % 3], statusLabel: ['On target', 'Below target', 'Off target'][i % 3] }));
    return { n: 1, layout: 'IAM_KPI', data: { ...base, kpis, perSlide: n } };
  }
  if (kind === 'CHART') return { n: 1, layout: 'IAM_CHART', data: { ...base, labels: ['Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov'], values: [91, 93, 92, 95, 96, 97], target: 95, unit: '%', commentary: ['Third month above target'] } };
  if (kind === 'TWO_COLUMN') return { n: 1, layout: 'IAM_TWO_COLUMN', data: { ...base, leftHead: 'Risks', rightHead: 'Asks', left: ['Vendor delay', 'Audit finding open'], right: ['Budget for pilot'], fontSize: 16 } };
  if (kind === 'TABLE' || kind === 'ACTION_ITEMS') {
    const head = section.columns?.length ? section.columns : ['Action', 'Owner', 'Team', 'Due'];
    const rows = [0, 1, 2].map((r) => head.map((_, c) => (c === 0 ? `Sample row ${r + 1}` : '—')));
    return { n: 1, layout: 'IAM_TABLE', data: { ...base, head, rows, colWidths: head.map(() => 100 / head.length), fontSize: 14 } };
  }
  return { n: 1, layout: 'IAM_SUMMARY', data: { ...base, blocks: [{ type: 'p', text: 'Sample paragraph of text for this Section.' }, { type: 'li', text: 'First point' }, { type: 'li', text: 'Second point' }], fontSize: 18, wide: true } };
}

export default function LayoutEditor({ section, onSave, onClose }) {
  const [layout, setLayout] = useState(() => (section.slideLayout && Object.keys(section.slideLayout).length ? structuredClone(section.slideLayout) : defaultLayout(section)));
  const [theme, setTheme] = useState({});
  const [drag, setDrag] = useState(null);
  const canvas = useRef(null);
  useEffect(() => { api('/api/house-template/theme').then(setTheme).catch(() => setTheme({})); }, []);

  const boxes = [];
  itemsFor(section.kind).forEach((k) => {
    if (k === 'tiles') (layout.tiles || []).forEach((b, i) => boxes.push({ id: `tiles.${i}`, label: `Tile ${i + 1}`, b }));
    else if (layout[k]) boxes.push({ id: k, label: LABEL[k], b: layout[k] });
  });

  const put = (id, b) => setLayout((l) => {
    const next = structuredClone(l);
    if (id.startsWith('tiles.')) next.tiles[Number(id.slice(6))] = b; else next[id] = b;
    return next;
  });

  const cell = (e) => {
    const r = canvas.current.getBoundingClientRect();
    return { cx: ((e.clientX - r.left) / r.width) * COLS, cy: ((e.clientY - r.top) / r.height) * ROWS };
  };
  const start = (e, id, b, mode) => {
    e.preventDefault(); e.stopPropagation();
    e.currentTarget.setPointerCapture?.(e.pointerId);
    const { cx, cy } = cell(e);
    setDrag({ id, mode, b0: b, cx, cy });
  };
  const move = (e) => {
    if (!drag) return;
    const { cx, cy } = cell(e);
    const dx = Math.round(cx - drag.cx);
    const dy = Math.round(cy - drag.cy);
    const b = drag.b0;
    if (drag.mode === 'move') {
      put(drag.id, { ...b, x: Math.min(Math.max(0, b.x + dx), COLS - b.w), y: Math.min(Math.max(0, b.y + dy), ROWS - b.h) });
    } else {
      put(drag.id, { ...b, w: Math.min(Math.max(1, b.w + dx), COLS - b.x), h: Math.min(Math.max(1, b.h + dy), ROWS - b.y) });
    }
  };
  const nudge = (e, id, b) => {
    const step = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[e.key];
    if (!step) return;
    e.preventDefault();
    if (e.shiftKey) put(id, { ...b, w: Math.min(Math.max(1, b.w + step[0]), COLS - b.x), h: Math.min(Math.max(1, b.h + step[1]), ROWS - b.y) });
    else put(id, { ...b, x: Math.min(Math.max(0, b.x + step[0]), COLS - b.w), y: Math.min(Math.max(0, b.y + step[1]), ROWS - b.h) });
  };
  const tiles = (n) => setLayout((l) => ({ ...l, tiles: defaultLayout({ ...section, kpiLayout: String(n) }).tiles }));

  return (
    <div className="modal-back" role="dialog" aria-modal="true" aria-label={`Slide layout: ${section.name}`}>
      <div className="modal layout-editor">
        <div className="row"><strong>Slide layout · {section.name}</strong><span className="hint">Drag to move, drag the corner to resize. Arrow keys move the focused box; Shift + arrows resize it.</span></div>
        <div className="layout-canvas" ref={canvas} onPointerMove={move} onPointerUp={() => setDrag(null)} onPointerLeave={() => setDrag(null)}>
          <div className="layout-preview" aria-hidden="true"><SlideRenderer slide={sampleSlide(section, layout)} theme={theme} /></div>
          <div className="layout-grid" aria-hidden="true" />
          {boxes.map(({ id, label, b }) => {
            const p = pct(b);
            return (
              <div key={id} className={`layout-box ${drag?.id === id ? 'active' : ''}`} tabIndex={0} aria-label={`${label}: column ${b.x + 1}, row ${b.y + 1}, ${b.w} by ${b.h}`}
                style={{ left: `${p.x}%`, top: `${p.y}%`, width: `${p.w}%`, height: `${p.h}%` }}
                onPointerDown={(e) => start(e, id, b, 'move')} onKeyDown={(e) => nudge(e, id, b)}>
                <span className="layout-label">{label}</span>
                <span className="layout-resize" onPointerDown={(e) => start(e, id, b, 'resize')} aria-hidden="true" />
              </div>
            );
          })}
        </div>
        <div className="row">
          {section.kind === 'KPI' && <label className="field"><span>Tiles per slide</span>
            <select value={layout.tiles?.length || 4} onChange={(e) => tiles(Number(e.target.value))}>
              {[1, 2, 3, 4, 5, 6, 7, 8].map((n) => <option key={n} value={n}>{n}</option>)}</select></label>}
          <span className="grow" />
          <button type="button" className="btn" onClick={() => { onSave(null); onClose(); }}>Reset to default</button>
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="button" className="btn pri" onClick={() => { onSave(layout); onClose(); }}>Use this layout</button>
        </div>
        <p className="hint">Saved with the Template when you publish it. Content that does not fit continues on more slides with the same layout.</p>
      </div>
    </div>
  );
}
