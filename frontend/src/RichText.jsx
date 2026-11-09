import { useEffect, useRef } from 'react';

/**
 * Formatted text (spec-v3 §2): bold, italic, bulleted and numbered lists with one sub-level, links.
 * Stored as { blocks: [{ type: 'p', runs }, { type: 'ul'|'ol', items: [{ runs, items: [{ runs }] }] }] }, never HTML.
 * The editor is a contentEditable area; its DOM is read back into that model on every change, so only
 * the supported formatting survives, and paste always comes in as plain text.
 */
const SAFE = /^(https?:\/\/|mailto:)/i;
const esc = (t) => t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

function runsHtml(runs = []) {
  return runs.map((r) => {
    let h = esc(r.text || '');
    if (r.i) h = `<i>${h}</i>`;
    if (r.b) h = `<b>${h}</b>`;
    if (r.href && SAFE.test(r.href)) h = `<a href="${esc(r.href)}">${h}</a>`;
    return h;
  }).join('');
}

export function toHtml(doc) {
  return (doc?.blocks || []).map((b) => {
    if (b.type === 'p') return `<p>${runsHtml(b.runs) || '<br>'}</p>`;
    const tag = b.type === 'ol' ? 'ol' : 'ul';
    const items = (b.items || []).map((it) => {
      const sub = (it.items || []).length ? `<${tag}>${it.items.map((s) => `<li>${runsHtml(s.runs)}</li>`).join('')}</${tag}>` : '';
      return `<li>${runsHtml(it.runs)}${sub}</li>`;
    }).join('');
    return `<${tag}>${items}</${tag}>`;
  }).join('');
}

/** Inline content of a node as runs; nested lists are skipped here (they become sub-items). */
function runsOf(node, marks = {}, out = []) {
  node.childNodes.forEach((n) => {
    if (n.nodeType === 3) {
      const text = n.nodeValue.replace(/[\u0000-\u0008\u000b-\u001f]/g, '');
      if (!text) return;
      const last = out[out.length - 1];
      const same = last && !!last.b === !!marks.b && !!last.i === !!marks.i && (last.href || null) === (marks.href || null);
      if (same) last.text += text;
      else out.push({ text, ...(marks.b && { b: true }), ...(marks.i && { i: true }), ...(marks.href && { href: marks.href }) });
      return;
    }
    if (n.nodeType !== 1) return;
    const tag = n.tagName;
    if (tag === 'UL' || tag === 'OL') return;
    if (tag === 'BR') { runsOf({ childNodes: [document.createTextNode(' ')] }, marks, out); return; }
    const next = { ...marks };
    if (tag === 'B' || tag === 'STRONG' || /bold|[6-9]00/.test(n.style?.fontWeight || '')) next.b = true;
    if (tag === 'I' || tag === 'EM' || n.style?.fontStyle === 'italic') next.i = true;
    if (tag === 'A' && SAFE.test(n.getAttribute('href') || '')) next.href = n.getAttribute('href');
    runsOf(n, next, out);
  });
  return out;
}

const blank = (runs) => !runs.some((r) => r.text.trim());

export function fromDom(root) {
  const blocks = [];
  let loose = null; // inline content directly in the root
  const flushLoose = () => { if (loose && !blank(loose)) blocks.push({ type: 'p', runs: loose }); loose = null; };
  root.childNodes.forEach((n) => {
    const tag = n.nodeType === 1 ? n.tagName : null;
    if (tag === 'UL' || tag === 'OL') {
      flushLoose();
      const items = [];
      n.querySelectorAll(':scope > li').forEach((li) => {
        const sub = [];
        li.querySelectorAll(':scope > ul > li, :scope > ol > li').forEach((s) => {
          const r = runsOf(s);
          if (!blank(r)) sub.push({ runs: r });
          s.querySelectorAll('li').forEach((deep) => { const d = runsOf(deep); if (!blank(d)) sub.push({ runs: d }); }); // deeper levels flatten
        });
        const runs = runsOf(li);
        if (!blank(runs) || sub.length) items.push({ runs, ...(sub.length && { items: sub }) });
      });
      if (items.length) blocks.push({ type: tag === 'OL' ? 'ol' : 'ul', items });
    } else if (tag === 'P' || tag === 'DIV' || /^H[1-6]$/.test(tag || '')) {
      flushLoose();
      const nested = n.querySelector('ul,ol');
      const runs = runsOf(n);
      if (!blank(runs)) blocks.push({ type: 'p', runs });
      if (nested) blocks.push(...fromDom({ childNodes: [nested] }).blocks);
    } else {
      loose = loose || [];
      runsOf({ childNodes: [n] }, {}, loose);
    }
  });
  flushLoose();
  return { blocks };
}

const cmd = (c, v) => document.execCommand(c, false, v);

export default function RichTextEditor({ value, onChange, label = 'Notes and commentary' }) {
  const ref = useRef(null);
  useEffect(() => { if (ref.current) ref.current.innerHTML = toHtml(value); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const emit = () => onChange(fromDom(ref.current));
  const act = (e, c, v) => { e.preventDefault(); ref.current.focus(); cmd(c, v); emit(); };
  const link = (e) => {
    e.preventDefault();
    const url = window.prompt('Link address (https://…, http://… or mailto:…)'); // eslint-disable-line no-alert
    if (!url) return;
    if (!SAFE.test(url.trim())) return;
    ref.current.focus(); cmd('createLink', url.trim()); emit();
  };
  return (
    <div className="rich">
      <span className="rich-label">{label}</span>
      <div className="rich-bar" role="toolbar" aria-label="Formatting">
        <button type="button" className="btn" title="Bold (Ctrl+B)" aria-label="Bold" onMouseDown={(e) => act(e, 'bold')}><b>B</b></button>
        <button type="button" className="btn" title="Italic (Ctrl+I)" aria-label="Italic" onMouseDown={(e) => act(e, 'italic')}><i>I</i></button>
        <button type="button" className="btn" aria-label="Bulleted list" onMouseDown={(e) => act(e, 'insertUnorderedList')}>• List</button>
        <button type="button" className="btn" aria-label="Numbered list" onMouseDown={(e) => act(e, 'insertOrderedList')}>1. List</button>
        <button type="button" className="btn" aria-label="Indent list item" onMouseDown={(e) => act(e, 'indent')}>→</button>
        <button type="button" className="btn" aria-label="Outdent list item" onMouseDown={(e) => act(e, 'outdent')}>←</button>
        <button type="button" className="btn" aria-label="Add link" onMouseDown={link}>Link</button>
      </div>
      <div ref={ref} className="rich-area" contentEditable suppressContentEditableWarning role="textbox" aria-multiline="true" aria-label={label}
        onInput={emit} onBlur={emit}
        onPaste={(e) => { e.preventDefault(); cmd('insertText', e.clipboardData.getData('text/plain')); emit(); }} />
    </div>
  );
}

/** Read-only formatted text. */
export function RichView({ doc }) {
  const runs = (rs = []) => rs.map((r, i) => {
    let el = r.text;
    if (r.i) el = <em>{el}</em>;
    if (r.b) el = <strong>{el}</strong>;
    if (r.href && SAFE.test(r.href)) el = <a href={r.href} target="_blank" rel="noopener noreferrer">{el}</a>;
    return <span key={i}>{el}</span>;
  });
  return (
    <div className="rich-view">
      {(doc?.blocks || []).map((b, i) => {
        if (b.type === 'p') return <p key={i}>{runs(b.runs)}</p>;
        const L = b.type === 'ol' ? 'ol' : 'ul';
        return <L key={i}>{(b.items || []).map((it, j) => (
          <li key={j}>{runs(it.runs)}{(it.items || []).length > 0 && <L>{it.items.map((s, k) => <li key={k}>{runs(s.runs)}</li>)}</L>}</li>))}</L>;
      })}
    </div>
  );
}

/** Old commentary (text or lines) as a formatted-text document, so editing it moves it into the new box. */
export function fromPlain(text) {
  const lines = (Array.isArray(text) ? text : String(text || '').split('\n')).map((l) => String(l).trim()).filter(Boolean);
  return lines.length ? { blocks: lines.map((l) => ({ type: 'p', runs: [{ text: l }] })) } : null;
}
