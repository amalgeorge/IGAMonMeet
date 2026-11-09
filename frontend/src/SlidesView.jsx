import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion, useReducedMotion } from 'motion/react';
import { api } from './api.js';
import SlideRenderer, { buildSteps } from './SlideRenderer.jsx';

const TRANSITIONS = { fade: 'Fade', push: 'Push', none: 'None' };
const store = { get: (k, d) => { try { return localStorage.getItem(k) ?? d; } catch { return d; } }, set: (k, v) => { try { localStorage.setItem(k, v); } catch { /* per-viewer only */ } } };

export function useDeck(meetingId, themeKey = 0) {
  const [plan, setPlan] = useState(null);
  const [theme, setTheme] = useState(null);
  useEffect(() => {
    api(`/api/meetings/${meetingId}/slides`).then(setPlan);
    // House template with the Meeting's Theme applied (spec-v2 §4.1).
    api(`/api/meetings/${meetingId}/theme`).then(setTheme).catch(() => api('/api/house-template/theme').then(setTheme).catch(() => setTheme({})));
  }, [meetingId, themeKey]);
  return { plan, theme };
}

/** Slide position shared by the show, Presenter view and the audience window. */
function useShowState(slides) {
  const [pos, setPos] = useState({ idx: 0, step: 0, blank: '' });
  const go = useCallback((idx) => setPos({ idx: Math.max(0, Math.min(idx, slides.length - 1)), step: 0, blank: '' }), [slides.length]);
  const next = useCallback(() => setPos((p) => {
    if (p.blank) return { ...p, blank: '' };
    if (p.step < buildSteps(slides[p.idx])) return { ...p, step: p.step + 1 };
    return p.idx < slides.length - 1 ? { idx: p.idx + 1, step: 0, blank: '' } : p;
  }), [slides]);
  const prev = useCallback(() => setPos((p) => (p.step > 0 ? { ...p, step: p.step - 1 } : p.idx > 0 ? { idx: p.idx - 1, step: buildSteps(slides[p.idx - 1]), blank: '' } : p)), [slides]);
  return { pos, setPos, go, next, prev };
}

/** PowerPoint keys: → Space Enter PageDown next; ← Backspace PageUp back; number+Enter jump; B/W blank; Home/End; Esc end. */
function useShowKeys(active, { next, prev, go, setPos, end, count }) {
  const typed = useRef('');
  useEffect(() => {
    if (!active) return undefined;
    const onKey = (e) => {
      if (e.target.closest?.('input, textarea, select')) return;
      const k = e.key;
      if (/^[0-9]$/.test(k)) { typed.current += k; return; }
      if (k === 'Enter' && typed.current) { go(Number(typed.current) - 1); typed.current = ''; e.preventDefault(); return; }
      typed.current = '';
      if (['ArrowRight', 'ArrowDown', ' ', 'Enter', 'PageDown', 'n', 'N'].includes(k)) { e.preventDefault(); next(); }
      else if (['ArrowLeft', 'ArrowUp', 'Backspace', 'PageUp', 'p', 'P'].includes(k)) { e.preventDefault(); prev(); }
      else if (k === 'Home') go(0);
      else if (k === 'End') go(count - 1);
      else if (k === 'b' || k === 'B' || k === '.') setPos((p) => ({ ...p, blank: p.blank === 'b' ? '' : 'b' }));
      else if (k === 'w' || k === 'W' || k === ',') setPos((p) => ({ ...p, blank: p.blank === 'w' ? '' : 'w' }));
      else if (k === 'Escape') end();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [active, next, prev, go, setPos, end, count]);
}

function Stage({ slide, theme, total, step, blank, transition }) {
  const reduce = useReducedMotion();
  const kind = reduce || transition === 'none' ? 'fade' : transition;
  const variants = kind === 'push'
    ? { enter: { x: '100%', opacity: 1 }, center: { x: 0, opacity: 1 }, exit: { x: '-100%', opacity: 1 } }
    : { enter: { opacity: 0 }, center: { opacity: 1 }, exit: { opacity: 0 } };
  const duration = transition === 'none' ? 0 : reduce ? 0.2 : 0.45;
  return (
    <div className="stage">
      <AnimatePresence initial={false} mode="popLayout">
        <motion.div key={slide.n} className="stage-slide" variants={variants} initial="enter" animate="center" exit="exit" transition={{ duration, ease: [0.2, 0, 0, 1] }}>
          <SlideRenderer slide={slide} theme={theme} total={total} step={step} />
        </motion.div>
      </AnimatePresence>
      {blank && <div className="blank" style={{ background: blank === 'w' ? '#fff' : '#000' }} aria-label={blank === 'w' ? 'White screen' : 'Black screen'} />}
    </div>
  );
}

export default function SlidesView({ meetingId }) {
  const [themeKey, setThemeKey] = useState(0);
  const { plan, theme } = useDeck(meetingId, themeKey);
  const slides = plan?.slides || [];
  const [current, setCurrent] = useState(0);
  const [mode, setMode] = useState('edit');
  const [transition, setTransition] = useState(() => store.get(`transition-${meetingId}`, 'fade'));
  const show = useShowState(slides);
  const channel = useRef(null);
  const [idle, setIdle] = useState(false);

  useEffect(() => {
    channel.current = new BroadcastChannel(`igamonmeet-show-${meetingId}`);
    channel.current.onmessage = (e) => {
      if (e.data.type === 'key') ({ next: show.next, prev: show.prev })[e.data.key]?.();
      if (e.data.type === 'hello') channel.current.postMessage({ type: 'pos', pos: show.pos, transition });
    };
    return () => channel.current?.close();
  }, [meetingId, show.next, show.prev, show.pos, transition]);
  useEffect(() => { if (mode !== 'edit') channel.current?.postMessage({ type: 'pos', pos: show.pos, transition }); }, [show.pos, mode, transition]);

  const end = useCallback(() => {
    setCurrent(show.pos.idx);
    setMode('edit');
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
  }, [show.pos.idx]);
  useShowKeys(mode !== 'edit', { ...show, end, count: slides.length });

  useEffect(() => {
    if (mode !== 'show') return undefined;
    let t = setTimeout(() => setIdle(true), 2000);
    const move = () => { setIdle(false); clearTimeout(t); t = setTimeout(() => setIdle(true), 2000); };
    window.addEventListener('mousemove', move);
    return () => { clearTimeout(t); window.removeEventListener('mousemove', move); };
  }, [mode]);
  useEffect(() => {
    const onFs = () => { if (!document.fullscreenElement && mode === 'show') end(); };
    document.addEventListener('fullscreenchange', onFs);
    return () => document.removeEventListener('fullscreenchange', onFs);
  }, [mode, end]);

  if (!plan || !theme) return <p className="hint">Building slides…</p>;
  const startShow = (idx) => {
    show.go(idx);
    setMode('show');
    document.documentElement.requestFullscreen?.().catch(() => {});
  };
  const startPresenter = () => {
    show.go(current);
    setMode('presenter');
    window.open(`${window.location.pathname}#audience/${meetingId}`, `igamonmeet-audience-${meetingId}`, 'popup');
  };
  const pickTransition = (t) => { setTransition(t); store.set(`transition-${meetingId}`, t); };
  const slide = slides[current] || slides[0];

  if (mode === 'show') {
    const s = slides[show.pos.idx];
    return (
      <div className={`show ${idle ? 'idle' : ''}`} onClick={show.next} role="presentation">
        <Stage slide={s} theme={theme} total={slides.length} step={show.pos.step} blank={show.pos.blank} transition={transition} />
        <div className="show-ctl" onClick={(e) => e.stopPropagation()}>
          <button onClick={show.prev} aria-label="Previous">‹</button><span className="tnum">{show.pos.idx + 1} / {slides.length}</span><button onClick={show.next} aria-label="Next">›</button>
          <button onClick={end}>End show</button>
        </div>
      </div>
    );
  }
  if (mode === 'presenter') return <Presenter slides={slides} theme={theme} show={show} transition={transition} onEnd={end} />;

  return (
    <div className="slides-view">
      <div className="ribbon" role="toolbar" aria-label="Slide tools">
        <ThemePicker meetingId={meetingId} onChange={() => setThemeKey((k) => k + 1)} />
        <span className="hint">{slides.length} slides · built from the Document view · font {plan.font}</span>
        <label className="field"><span>Transition</span>
          <select value={transition} onChange={(e) => pickTransition(e.target.value)}>{Object.entries(TRANSITIONS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
        <span className="grow" />
        <button className="btn" onClick={startPresenter}>Presenter view</button>
        <button className="btn" onClick={() => startShow(current)}>From current slide</button>
        <button className="btn pri" onClick={() => startShow(0)}>▶ Present</button>
      </div>
      <div className="slides-body">
        <nav className="thumbs" aria-label="Slides">
          {slides.map((s, i) => (
            <button key={s.n} className={`thumb ${i === current ? 'on' : ''}`} aria-current={i === current} onClick={() => setCurrent(i)} aria-label={`Slide ${s.n}: ${s.data.title}`}>
              <span className="tnum">{s.n}</span><span className="thumb-frame"><SlideRenderer slide={s} theme={theme} /></span>
            </button>
          ))}
        </nav>
        <div className="canvas-area">
          <div className="canvas-slide"><SlideRenderer slide={slide} theme={theme} total={slides.length} /></div>
          <div className="row center">
            <button className="btn" disabled={current === 0} onClick={() => setCurrent(current - 1)} aria-label="Previous slide">‹</button>
            <span className="tnum">Slide {current + 1} of {slides.length}</span>
            <button className="btn" disabled={current === slides.length - 1} onClick={() => setCurrent(current + 1)} aria-label="Next slide">›</button>
          </div>
          {slide.data.notes && <p className="notes-view"><strong>Speaker notes:</strong> {slide.data.notes}</p>}
          <p className="hint">{slide.sectionKey ? `From section "${slide.sectionKey}"${slide.continuation ? ' (continued)' : ''}${slide.appendix ? ' · appendix' : ''}. Edit the content in the Document view.` : 'Generated slide.'}</p>
        </div>
      </div>
    </div>
  );
}

function Presenter({ slides, theme, show, transition, onEnd }) {
  const [start] = useState(() => Date.now());
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const t = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(t); }, []);
  const secs = Math.floor((now - start) / 1000);
  const clock = [Math.floor(secs / 3600), Math.floor(secs / 60) % 60, secs % 60].map((n) => String(n).padStart(2, '0')).join(':');
  const s = slides[show.pos.idx];
  const nextSlide = slides[show.pos.idx + 1];
  return (
    <div className="presenter">
      <div className="row">
        <span className="tnum clock">{clock}</span><span>Elapsed</span>
        <span className="grow" />
        <span className="tnum">Slide {show.pos.idx + 1} of {slides.length}{buildSteps(s) ? ` · build ${show.pos.step}/${buildSteps(s)}` : ''}</span>
        <button className="ctl" onClick={onEnd}>End show</button>
      </div>
      <div className="presenter-body">
        <div className="presenter-main">
          <Stage slide={s} theme={theme} total={slides.length} step={show.pos.step} blank={show.pos.blank} transition={transition} />
          <div className="row"><button className="ctl" onClick={show.prev}>‹ Previous</button><button className="ctl" onClick={show.next}>Next ›</button></div>
        </div>
        <div className="presenter-side">
          <div className="label">Next</div>
          {nextSlide ? <SlideRenderer slide={nextSlide} theme={theme} /> : <div className="end-box">End of show</div>}
          <div className="label">Notes</div>
          <p className="presenter-notes">{s.data.notes || 'No speaker notes for this slide.'}</p>
          <p className="hint">The audience window opened separately. Move it to the projector and press F11 there.</p>
        </div>
      </div>
    </div>
  );
}

/** Opened by Presenter view on the projector: follows the presenter, and its keys drive the presenter. */
export function AudienceWindow({ meetingId }) {
  const { plan, theme } = useDeck(meetingId);
  const [state, setState] = useState({ pos: { idx: 0, step: 0, blank: '' }, transition: 'fade' });
  const channel = useRef(null);
  useEffect(() => {
    channel.current = new BroadcastChannel(`igamonmeet-show-${meetingId}`);
    channel.current.onmessage = (e) => { if (e.data.type === 'pos') setState({ pos: e.data.pos, transition: e.data.transition }); };
    channel.current.postMessage({ type: 'hello' });
    const onKey = (e) => {
      if (['ArrowRight', ' ', 'Enter', 'PageDown'].includes(e.key)) channel.current.postMessage({ type: 'key', key: 'next' });
      if (['ArrowLeft', 'Backspace', 'PageUp'].includes(e.key)) channel.current.postMessage({ type: 'key', key: 'prev' });
      if (e.key === 'f' || e.key === 'F') document.documentElement.requestFullscreen?.().catch(() => {});
    };
    window.addEventListener('keydown', onKey);
    return () => { channel.current.close(); window.removeEventListener('keydown', onKey); };
  }, [meetingId]);
  if (!plan || !theme) return null;
  const s = plan.slides[state.pos.idx] || plan.slides[0];
  return (
    <div className="show idle" onClick={() => channel.current.postMessage({ type: 'key', key: 'next' })} role="presentation">
      <Stage slide={s} theme={theme} total={plan.slides.length} step={state.pos.step} blank={state.pos.blank} transition={state.transition} />
    </div>
  );
}

/** The Meeting's Theme (spec-v2 §4.2): Admin any time, editors while Preparing. Held Meetings keep the version they were presented with. */
function ThemePicker({ meetingId, onChange }) {
  const [c, setC] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => { api(`/api/meetings/${meetingId}/theme-choice`).then(setC).catch(() => setC(null)); }, [meetingId]);
  if (!c) return null;
  const choose = (themeId) => {
    setError('');
    api(`/api/meetings/${meetingId}/theme-choice`, { method: 'PUT', json: { themeId } }).then((n) => { setC(n); onChange(); }).catch((e) => setError(e.message));
  };
  return (
    <span className="theme-pick">
      <label>Theme{' '}
        <select value={c.themeId} disabled={!c.canChange} onChange={(e) => choose(Number(e.target.value))}>
          {c.themes.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
        </select></label>
      {c.frozen && <span className="hint" title="Held Meetings keep the Theme version in use on the Meeting date."> v{c.versionNo} · kept as presented</span>}
      {error && <span className="error" role="alert"> {error}</span>}
    </span>
  );
}
