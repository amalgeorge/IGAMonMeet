import { Component } from 'react';
import { motion, useReducedMotion } from 'motion/react';

/**
 * Draws one slide of the Slide plan at any size (container query units: 1cqw = 1% of slide width),
 * using the House template theme: colours, fonts, per-layout background and placeholder boxes.
 * `step` reveals build elements (KPI tiles, summary points) one at a time in the show.
 */
const PT = 100 / 960; // cqw per point on a 960 pt wide slide
const pt = (n) => `${(n * PT).toFixed(3)}cqw`;
const STATUS = { ON_TARGET: ['On target', '#E3F3E9', '#17643A'], BELOW_TARGET: ['Below target', '#FFF3DC', '#7A4500'], OFF_TARGET: ['Off target', '#FDECEE', '#9E0B23'] };
const ROW_FILL = { ON_TARGET: '#E3F3E9', BELOW_TARGET: '#FFF3DC', OFF_TARGET: '#FDECEE' };
/** True when dark text reads better than white on this colour. */
/** RichText inline marks (spec-v3 §2) as React: \u0010 bold, \u0011 italic, \u0012url\u0013text\u0014 link. */
export function markedText(text) {
  const out = [];
  let bold = false; let italic = false; let href = null; let buf = '';
  const flush = () => {
    if (!buf) return;
    let el = buf;
    if (italic) el = <em key={`i${out.length}`}>{el}</em>;
    if (bold) el = <strong key={`b${out.length}`}>{el}</strong>;
    if (href) el = <a key={`a${out.length}`} href={href} target="_blank" rel="noopener noreferrer">{el}</a>;
    out.push(typeof el === 'string' ? <span key={`s${out.length}`}>{el}</span> : el);
    buf = '';
  };
  for (let i = 0; i < (text || '').length; i++) {
    const c = text[i];
    if (c === '\u0010') { flush(); bold = !bold; } else if (c === '\u0011') { flush(); italic = !italic; } else if (c === '\u0014') { flush(); href = null; } else if (c === '\u0012') {
      flush();
      const end = text.indexOf('\u0013', i);
      if (end < 0) continue; // broken link mark: drop it (never loop back)
      const url = text.slice(i + 1, end);
      href = /^(https?:\/\/|mailto:)/i.test(url) ? url : null;
      i = end;
    } else buf += c;
  }
  flush();
  return out;
}

/** A link to another slide: a button when the view can jump (onGo), plain text otherwise. */
function SlideLink({ target, onGo, children }) {
  if (!onGo || !target) return <span>{children}</span>;
  return (
    <button type="button" className="slide-link" onClick={(e) => { e.stopPropagation(); onGo(target); }}
      style={{ all: 'unset', cursor: 'pointer', textDecoration: 'underline', textUnderlineOffset: '0.15em' }}>{children}</button>
  );
}

function light(hex) {
  const h = (hex || '').replace('#', '');
  if (h.length !== 6) return false;
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16) / 255);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.6;
}

const RED_LAYOUTS = ['IAM_TITLE', 'IAM_SECTION', 'IAM_CLOSING'];

export function buildSteps(slide) {
  if (slide.layout === 'IAM_KPI') return (slide.data.kpis || []).length;
  if (slide.layout === 'IAM_SUMMARY') return (slide.data.blocks || slide.data.points || []).length;
  return 0;
}

/** onGo(n): jump to slide n when an agenda entry, divider link or "↩ Agenda" is clicked (spec-v3 §5); omitted in thumbnails. */
/**
 * One slide that cannot be drawn must not blank the whole app: the boundary shows a small notice on that slide only.
 */
class SlideBoundary extends Component {
  constructor(props) { super(props); this.state = { failed: false }; }
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidUpdate(prev) { if (prev.slide !== this.props.slide && this.state.failed) this.setState({ failed: false }); }
  render() {
    if (this.state.failed) {
      return <div role="alert" style={{ position: 'relative', aspectRatio: '16 / 9', width: '100%', display: 'grid', placeItems: 'center', background: '#fff', color: '#9E0B23', fontFamily: 'Arial, sans-serif' }}>
        This slide could not be drawn. Open its Section in the Document view and save it again.</div>;
    }
    return this.props.children;
  }
}

export default function SlideRenderer(props) {
  return <SlideBoundary slide={props.slide}><SlideBody {...props} /></SlideBoundary>;
}

function SlideBody({ slide, theme, total, step = Infinity, onGo }) {
  const reduce = useReducedMotion();
  const c = theme?.colors || {};
  const accent = `#${c.accent1 || 'C8102E'}`;
  const ink = `#${c.dk1 || '1A1A1A'}`;
  const muted = `#${c.dk2 || '5C5C5C'}`;
  const soft = `#${c.lt2 || 'F6F6F7'}`;
  const layoutTheme = theme?.layouts?.[slide.layout] || {};
  const red = RED_LAYOUTS.includes(slide.layout);
  const bg = layoutTheme.background ? `#${layoutTheme.background}` : red ? accent : '#FFFFFF';
  // Text on a coloured background stays readable whatever colour the Theme picks.
  const onBg = light(bg) ? ink : '#FFFFFF';
  const bgImage = layoutTheme.backgroundImage ? { backgroundImage: `url(${layoutTheme.backgroundImage})`, backgroundSize: 'cover', backgroundPosition: 'center' } : {};
  const logo = theme?.logo?.show !== false && theme?.logo?.image
    ? <img src={theme.logo.image} alt="" style={{ position: 'absolute', right: '4.5%', top: '5%', height: '7%', maxWidth: '18%', objectFit: 'contain' }} /> : null;
  const statusColor = (s) => theme?.statusColors?.[s] ? `#${theme.statusColors[s]}` : null;
  const ph = (name, fallback) => layoutTheme.placeholders?.[name] || fallback;
  const box = (b) => ({ position: 'absolute', left: `${b.x}%`, top: `${b.y}%`, width: `${b.w}%`, height: `${b.h}%` });
  const d = slide.data;
  const shown = (i) => i < step;
  const reveal = (i) => (reduce ? { initial: false, animate: { opacity: shown(i) ? 1 : 0 } }
    : { initial: false, animate: { opacity: shown(i) ? 1 : 0, y: shown(i) ? 0 : 12 }, transition: { duration: 0.3, ease: [0.2, 0, 0, 1] } });

  const frame = { position: 'relative', width: '100%', aspectRatio: '16 / 9', containerType: 'inline-size', overflow: 'hidden', background: bg, ...bgImage,
    color: red ? onBg : ink, fontFamily: `${theme?.bodyFont || 'Arial'}, Arial, Helvetica, sans-serif`, lineHeight: 1.2 };
  const head = { fontFamily: `${theme?.headFont || 'Arial'}, Arial, Helvetica, sans-serif` };

  if (red) {
    const title = ph('title', { x: 6.75, y: 29, w: 86, h: 20 });
    const sub = ph(slide.layout === 'IAM_TITLE' ? 'subtitle' : 'body', { x: 6.75, y: 52, w: 86, h: 10 });
    return (
      <div style={frame} role="img" aria-label={`Slide ${slide.n}: ${d.title}`}>
        <div style={{ ...box(title), ...head, display: 'flex', alignItems: 'flex-end', fontWeight: 700, fontSize: pt(slide.layout === 'IAM_TITLE' ? 48 : 44) }}>{d.title}</div>
        {d.links?.length
          ? <div style={{ ...box(sub), height: 'auto', fontSize: pt(18), display: 'flex', flexWrap: 'wrap', gap: `${pt(6)} ${pt(18)}` }}>
              {d.links.map((l, i) => <SlideLink key={i} target={l.target} onGo={onGo}>{l.text}</SlideLink>)}</div>
          : <div style={{ ...box(sub), fontSize: pt(slide.layout === 'IAM_TITLE' ? 24 : 20) }}>{d.subtitle || d.body}</div>}
        {d.meta && <div style={{ ...box(ph('meta', { x: 6.75, y: 60, w: 86, h: 7 })), fontSize: pt(16) }}>{d.meta}</div>}
        {slide.layout === 'IAM_TITLE' && <div style={{ position: 'absolute', left: '6.75%', top: '88%', fontSize: pt(12), fontWeight: 700 }}>IAM Engineering  |  IAM Operations</div>}
        {slide.layout === 'IAM_SECTION' && <div style={{ position: 'absolute', right: '4.5%', top: '92.7%', fontSize: pt(10) }}>{slide.n}</div>}
        {logo}
      </div>
    );
  }

  const F = d.frame || {}; // Admin's slide layout (spec-v3 §6): boxes in % of the slide
  const content = F.content ? box(F.content) : { position: 'absolute', left: '4.5%', right: '4.5%', top: '26.7%', bottom: '12%' };
  return (
    <div style={frame} role="img" aria-label={`Slide ${slide.n}: ${d.title}`}>
      {logo}
      <div style={{ ...box(ph('team', { x: 4.5, y: 6, w: 60, h: 4.7 })), color: accent, fontWeight: 700, fontSize: pt(12), letterSpacing: pt(1.5) }}>{d.team}</div>
      <div style={{ ...box(F.title || ph('title', { x: 4.5, y: 11.3, w: 91, h: 11.3 })), ...head, fontWeight: 700, fontSize: pt(32) }}>{d.title}</div>
      <div style={{ position: 'absolute', left: '4.5%', top: '92.7%', fontSize: pt(10), color: muted }}>IAM Monthly Meeting  |  Internal</div>
      <div style={{ position: 'absolute', right: '4.5%', top: '92.7%', fontSize: pt(10), color: muted }}>{slide.n}{total ? ` / ${total}` : ''}</div>

      {d.agendaTarget && <div style={{ position: 'absolute', left: '42.5%', width: '15%', top: '92.7%', textAlign: 'center', fontSize: pt(10), color: muted }}>
        <SlideLink target={d.agendaTarget} onGo={onGo}>↩ Agenda</SlideLink></div>}
      {d.agenda && (
        <ol style={{ ...content, listStyle: 'none', margin: 0, padding: 0, fontSize: pt(20), display: 'flex', flexDirection: 'column', gap: pt(6) }}>
          {d.agenda.map((e, i) => (
            <li key={i} style={{ paddingLeft: pt(28 * (e.level || 0)), fontWeight: e.level ? 400 : 700, fontSize: e.level ? pt(18) : undefined }}>
              <SlideLink target={e.target} onGo={onGo}>{e.text}</SlideLink></li>))}
        </ol>
      )}

      {d.empty && <div style={{ ...content, fontSize: pt(18), color: muted }}>{d.empty}</div>}

      {!d.empty && slide.layout === 'IAM_SUMMARY' && d.blocks && (
        <div style={{ ...box(ph('body', { x: 4.5, y: 26.7, w: 54.75, h: 61.3 })), left: '4.5%', width: '91%', ...(F.content ? box(F.content) : {}), fontSize: pt(d.fontSize || 18), display: 'flex', flexDirection: 'column', gap: pt(12) }}>
          {d.blocks.map((b, i) => (b.type === 'li' || b.type === 'oli'
            ? <motion.div key={i} {...reveal(i)} style={{ display: 'flex', gap: pt(12), paddingLeft: pt(8 + 24 * (b.level || 0)) }}>
                <span aria-hidden="true">{b.type === 'oli' ? (b.level ? String.fromCharCode(96 + Number(b.num)) + '.' : `${b.num}.`) : (b.level ? '–' : '•')}</span>
                <span>{markedText(b.text)}</span></motion.div>
            : <motion.p key={i} {...reveal(i)} style={{ margin: 0 }}>{markedText(b.text)}</motion.p>))}
        </div>
      )}

      {!d.empty && slide.layout === 'IAM_SUMMARY' && !d.blocks && !d.agenda && (
        <>
          <ol style={{ ...box(F.content || ph('body', { x: 4.5, y: 26.7, w: 54.75, h: 61.3 })), margin: 0, paddingLeft: pt(24), fontSize: pt(d.fontSize || 18), display: 'flex', flexDirection: 'column', gap: pt(14) }}>
            {(d.points || []).map((p, i) => <motion.li key={i} {...reveal(i)}>{p}</motion.li>)}
          </ol>
          <div style={{ position: 'absolute', left: '63%', top: '26.7%', width: '32.5%', display: 'flex', flexDirection: 'column', gap: pt(14) }}>
            {(d.stats || []).map((s, i) => (
              <div key={i} style={{ background: soft, borderRadius: pt(6), padding: `${pt(9)} ${pt(18)}` }}>
                <div style={{ fontSize: pt(36), fontWeight: 700 }}>{s.value}</div><div style={{ fontSize: pt(13), color: muted }}>{s.label}</div>
              </div>
            ))}
          </div>
        </>
      )}

      {!d.empty && slide.layout === 'IAM_KPI' && (
        <>
          {(() => { const per = d.perSlide || 4; const cols = per <= 4 ? per : per / 2; const compact = F.tiles ? F.tiles.some((t) => t.h < 30) : per > 4; return (
          <div style={F.tiles ? { position: 'absolute', inset: 0 } : { position: 'absolute', left: '4.5%', right: '4.5%', top: '26.7%', height: compact ? '48%' : '45.3%', display: 'grid', gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))`, gridAutoRows: '1fr', gap: pt(21.6) }}>
            {d.kpis.map((k, i) => {
              const st = STATUS[k.status] && statusColor(k.status) ? [STATUS[k.status][0], `${statusColor(k.status)}22`, statusColor(k.status)] : STATUS[k.status];
              return (
                <motion.div key={k.key} {...reveal(i)} style={{ ...(F.tiles?.[i] ? { ...box(F.tiles[i]), boxSizing: 'border-box', overflow: 'hidden' } : {}), background: soft, borderRadius: pt(6), padding: pt(compact ? 10 : 18), display: 'flex', flexDirection: 'column', minHeight: 0 }}>
                  <div style={{ fontSize: pt(compact ? 11 : 14), color: muted, minHeight: compact ? 0 : pt(40) }}>{k.name}{k.subtitle && !compact && <div style={{ fontSize: pt(11) }}>{k.subtitle}</div>}</div>
                  <div style={{ fontSize: pt(compact ? ((k.display || '').length > 9 ? 18 : 24) : ((k.display || '').length > 9 ? 30 : 44)), fontWeight: 700, marginTop: pt(compact ? 2 : 6) }}>{k.display}</div>
                  {k.targetLine && <div style={{ fontSize: pt(compact ? 10 : 13), color: muted, marginTop: pt(compact ? 2 : 6) }}>{k.targetLine}</div>}
                  {k.deltaLine && !compact && <div style={{ fontSize: pt(13), color: muted }}>{k.deltaLine}</div>}
                  {st && <div style={{ marginTop: 'auto', alignSelf: 'flex-start', fontSize: pt(compact ? 9 : 11), fontWeight: 700, padding: `${pt(compact ? 3 : 5)} ${pt(compact ? 10 : 14)}`, borderRadius: pt(99), background: st[1], color: st[2] }}>{k.statusLabel || st[0]}</div>}
                  {k.override && !compact && <div style={{ fontSize: pt(10), color: muted, marginTop: pt(4) }}>{k.reason}</div>}
                </motion.div>
              );
            })}
          </div>); })()}
          <div style={{ ...box(ph('body', { x: 4.5, y: 76.7, w: 91, h: 12 })), fontSize: pt(14), color: muted }}>{d.body}</div>
        </>
      )}

      {!d.empty && slide.layout === 'IAM_CHART' && <Chart d={d} accent={accent} ink={ink} muted={muted} box={box(F.chart || ph('chart', { x: 4.5, y: 26.7, w: 60, h: 61.3 }))} bodyBox={box(F.content || ph('body', { x: 68.3, y: 26.7, w: 27.2, h: 61.3 }))} />}

      {!d.empty && slide.layout === 'IAM_TABLE' && (
        <div style={content}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: pt(d.fontSize || 14), tableLayout: 'fixed' }}>
            <colgroup>{(d.colWidths || []).map((w, i) => <col key={i} style={{ width: `${w}%` }} />)}</colgroup>
            <thead><tr>{d.head.map((h, j) => <th key={h} style={{ textAlign: d.align?.[j] || 'left', fontSize: pt(12), color: muted, background: soft, padding: `${pt(5.75)} ${pt(10.8)}`, border: `${pt(1)} solid #E3E3E3` }}>{h}</th>)}</tr></thead>
            <tbody>{d.rows.map((r, i) => {
              const flag = d.rowFlags?.[i] || '';
              const fill = flag.startsWith('status:') ? ROW_FILL[flag.slice(7)] : flag === 'total' ? soft : d.banded && i % 2 === 1 ? '#F7F7F7' : undefined;
              return (
                <tr key={i} style={{ color: flag === 'closed' ? muted : ink, textDecoration: flag === 'closed' ? 'line-through' : 'none', background: fill }}>
                  {r.map((cell, j) => {
                    const late = flag === 'overdue' && j === r.length - 1;
                    const alert = d.alertColumn === j && Number(String(cell).replace(/,/g, '')) > 0; // spec-v3 §1.3
                    return <td key={j} style={{ padding: `${pt(5.75)} ${pt(10.8)}`, border: `${pt(1)} solid #E3E3E3`, verticalAlign: 'top', overflowWrap: 'anywhere', textAlign: d.align?.[j] || 'left',
                      fontWeight: late || alert || flag === 'total' ? 700 : 400, color: late || alert ? '#9E0B23' : undefined }}>{cell}</td>;
                  })}
                </tr>
              );
            })}</tbody>
          </table>
          {d.note && <div style={{ marginTop: pt(8), fontSize: pt(12), color: muted }}>{d.note}</div>}
        </div>
      )}

      {!d.empty && slide.layout === 'IAM_TWO_COLUMN' && ['left', 'right'].map((side, i) => (
        <div key={side} style={{ position: 'absolute', top: '26.7%', height: '61.3%', left: i === 0 ? '4.5%' : '50.85%', width: '44.6%', ...(F[side] ? box(F[side]) : {}), background: i === 0 ? '#FDECEE' : soft, borderRadius: pt(6), padding: pt(21.6), boxSizing: 'border-box' }}>
          <div style={{ fontSize: pt(20), fontWeight: 700, marginBottom: pt(14) }}>{d[`${side}Head`]}</div>
          <ul style={{ margin: 0, paddingLeft: pt(20), fontSize: pt(d.fontSize || 16), display: 'flex', flexDirection: 'column', gap: pt(10) }}>{(d[side] || []).map((t, k) => <li key={k}>{t}</li>)}</ul>
          {i === 0 && d.note && <div style={{ position: 'absolute', bottom: pt(10), fontSize: pt(11), color: muted }}>{d.note}</div>}
        </div>
      ))}
    </div>
  );
}

function Chart({ d, accent, ink, muted, box, bodyBox }) {
  const vals = (d.values || []).map(Number);
  if (vals.length === 0) return <div style={{ ...box, fontSize: pt(16), color: muted }}>No values yet.</div>;
  const target = d.target != null ? Number(d.target) : null;
  const all = target != null ? [...vals, target] : vals;
  const lo = Math.floor(Math.min(...all) - 1);
  const hi = Math.ceil(Math.max(...all) + 1);
  const X = (i) => 60 + (vals.length === 1 ? 340 : (i * 680) / (vals.length - 1));
  const Y = (v) => 400 - ((v - lo) / (hi - lo || 1)) * 370;
  return (
    <>
      <svg viewBox="0 0 800 460" style={box} aria-hidden="true">
        {[lo, (lo + hi) / 2, hi].map((g) => (
          <g key={g}><line x1="50" x2="780" y1={Y(g)} y2={Y(g)} stroke="#E3E3E3" /><text x="40" y={Y(g) + 5} fontSize="15" fill={muted} textAnchor="end">{+g.toFixed(1)}</text></g>
        ))}
        {target != null && <line x1="50" x2="780" y1={Y(target)} y2={Y(target)} stroke={muted} strokeDasharray="6 6" />}
        <polyline points={vals.map((v, i) => `${X(i)},${Y(v)}`).join(' ')} fill="none" stroke={accent} strokeWidth="4" strokeLinejoin="round" />
        {vals.map((v, i) => (
          <g key={i}>
            <circle cx={X(i)} cy={Y(v)} r="6" fill={accent} />
            <text x={X(i)} y={Y(v) - 14} fontSize="15" fill={ink} textAnchor="middle">{v}</text>
            <text x={X(i)} y="440" fontSize="15" fill={muted} textAnchor="middle">{d.labels[i]}</text>
          </g>
        ))}
      </svg>
      <div style={{ ...bodyBox, fontSize: pt(16), display: 'flex', flexDirection: 'column', gap: pt(12) }}>{(d.commentary || []).map((t, i) => <div key={i}>{t}</div>)}</div>
    </>
  );
}
