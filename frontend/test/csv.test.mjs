import test from 'node:test';
import assert from 'node:assert/strict';
import { MAX_ROWS, parseCsv, planImport } from '../src/csv.js';

test('parses quotes, embedded separators, quotes and line breaks, and a BOM', () => {
  const rows = parseCsv('﻿Name,"Owner, team",Status\r\nSSO,"IAM ""Eng""",On track\r\n"PAM\nvault",Platform,At risk\n\n');
  assert.deepEqual(rows, [['Name', 'Owner, team', 'Status'], ['SSO', 'IAM "Eng"', 'On track'], ['PAM\nvault', 'Platform', 'At risk']]);
});

test('detects semicolon files from the header line', () => {
  assert.deepEqual(parseCsv('a;b\n1,5;2\n'), [['a', 'b'], ['1,5', '2']]);
});

test('maps columns by header name, ignoring case and spaces', () => {
  const plan = planImport(['Status', 'name', 'Due'], parseCsv('Name,Owner,Status\nSSO,IAM,On track\n'));
  assert.deepEqual(plan.rows, [['On track', 'SSO', '']]);
  assert.deepEqual(plan.missing, ['Due']);
  assert.deepEqual(plan.ignored, ['Owner']);
  assert.equal(plan.newColumns, false);
});

test('a fixed table refuses a file with no matching column; a table block takes the file headers', () => {
  const parsed = parseCsv('A,B\n1,2\n');
  assert.match(planImport(['X'], parsed).error, /No column in the file matches/);
  const plan = planImport(['X'], parsed, { allowNewColumns: true });
  assert.deepEqual(plan.columns, ['A', 'B']);
  assert.deepEqual(plan.rows, [['1', '2']]);
  assert.equal(plan.newColumns, true);
});

test('keeps at most MAX_ROWS rows and says so', () => {
  const body = Array.from({ length: MAX_ROWS + 5 }, (_, i) => `r${i}`).join('\n');
  const plan = planImport(['A'], parseCsv(`A\n${body}\n`));
  assert.equal(plan.rows.length, MAX_ROWS);
  assert.equal(plan.truncated, true);
});

test('an empty file is an error, not a crash', () => {
  assert.match(planImport(['A'], parseCsv('')).error, /no rows/);
});
