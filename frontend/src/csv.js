/**
 * CSV import for tables (spec-v3 §1.2). Pure functions so they can be checked without a browser.
 * Handles a UTF-8 BOM, comma or semicolon separators, quoted fields with separators, quotes ("") and line breaks.
 */
export const MAX_ROWS = 500;
export const MAX_CELL = 500;

export function parseCsv(text) {
  let s = text.replace(/^﻿/, '');
  const firstLine = s.slice(0, s.search(/\r?\n|$/));
  const sep = (firstLine.match(/;/g) || []).length > (firstLine.match(/,/g) || []).length ? ';' : ',';
  const rows = [];
  let row = []; let field = ''; let quoted = false;
  for (let i = 0; i < s.length; i++) {
    const ch = s[i];
    if (quoted) {
      if (ch === '"' && s[i + 1] === '"') { field += '"'; i++; }
      else if (ch === '"') quoted = false;
      else field += ch;
    } else if (ch === '"' && field === '') quoted = true;
    else if (ch === sep) { row.push(field); field = ''; }
    else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && s[i + 1] === '\n') i++;
      row.push(field); rows.push(row); row = []; field = '';
    } else field += ch;
  }
  if (field !== '' || row.length) { row.push(field); rows.push(row); }
  return rows.filter((r) => r.some((c) => c.trim() !== ''));
}

const norm = (h) => h.toLowerCase().replace(/\s+/g, '');

/**
 * Maps a parsed file onto a table's columns by header name.
 * Returns { columns, rows, ignored, missing, newColumns, truncated }.
 * allowNewColumns (Text table blocks): if no header matches, the file's headers become the columns.
 */
export function planImport(columns, parsed, { allowNewColumns = false, maxColumns = 12 } = {}) {
  if (!parsed.length) return { error: 'The file has no rows.' };
  const header = parsed[0].map((h) => h.trim());
  const body = parsed.slice(1);
  const index = columns.map((c) => header.findIndex((h) => norm(h) === norm(c)));
  const matched = index.filter((i) => i >= 0).length;
  let cols = columns; let pick = index; let newColumns = false;
  if (matched === 0) {
    if (!allowNewColumns) return { error: `No column in the file matches this table (${columns.join(' | ')}).` };
    cols = header.slice(0, maxColumns).map((h, i) => h || `Column ${i + 1}`);
    pick = cols.map((_, i) => i);
    newColumns = true;
  }
  const truncated = body.length > MAX_ROWS;
  const rows = body.slice(0, MAX_ROWS).map((r) => pick.map((i) => (i >= 0 ? (r[i] ?? '').trim().slice(0, MAX_CELL) : '')));
  const ignored = newColumns ? header.slice(maxColumns) : header.filter((h) => !columns.some((c) => norm(c) === norm(h)));
  const missing = newColumns ? [] : columns.filter((_, j) => index[j] < 0);
  return { columns: cols, rows, ignored, missing, newColumns, truncated };
}
