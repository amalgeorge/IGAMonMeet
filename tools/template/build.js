const pptxgen = require('pptxgenjs');
const { applyTheme } = require('./apply_theme.js');

const THEME = {
  name: 'IAM Monthly Meeting',
  headFontFace: 'Arial',
  bodyFontFace: 'Arial',
  colors: {
    dk1: '1A1A1A', lt1: 'FFFFFF', dk2: '5C5C5C', lt2: 'F6F6F7',
    accent1: 'C8102E', accent2: '9E0B23', accent3: '17643A', accent4: 'B36B00',
    accent5: '1D4466', accent6: '7A4500', hlink: 'C8102E', folHlink: '9E0B23'
  }
};

const OUT = process.argv[2] || 'IAM_Monthly_Meeting_Template.pptx';
const pres = new pptxgen();
pres.layout = 'LAYOUT_WIDE'; // 13.333 x 7.5
pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
pres.title = 'IAM Monthly Meeting - house template';
pres.company = 'IAM Engineering &amp; IAM Operations';
const C = pres.SchemeColor;

const W = 13.333, M = 0.6;

// ---- shared pieces ----
const footer = () => ([
  { text: { text: 'IAM Monthly Meeting  |  Internal', options: { x: M, y: 6.95, w: 8, h: 0.3, fontSize: 10, color: C.text2, margin: 0 } } },
]);
const slideNum = { x: W - M - 1, y: 6.95, w: 1, h: 0.3, fontSize: 10, color: C.text2, align: 'right' };
const teamPh = () => ({ placeholder: { options: { name: 'team', type: 'body', x: M, y: 0.45, w: 8, h: 0.35, fontSize: 12, bold: true, color: C.accent1, charSpacing: 1.5, margin: 0, valign: 'middle' }, text: 'TEAM' } });
const titlePh = () => ({ placeholder: { options: { name: 'title', type: 'title', align: 'left', x: M, y: 0.85, w: W - 2 * M, h: 0.85, fontSize: 32, bold: true, color: C.text1, margin: 0, valign: 'top' }, text: 'Slide title' } });

// ---- layouts (one per section type; names are the import/export contract) ----
pres.defineSlideMaster({
  title: 'IAM_TITLE',
  background: { color: C.accent1 },
  objects: [
    { placeholder: { options: { name: 'title', type: 'title', align: 'left', x: 0.9, y: 2.2, w: 11.5, h: 1.5, fontSize: 48, bold: true, color: C.background1, margin: 0, valign: 'bottom' }, text: 'Meeting title' } },
    { placeholder: { options: { name: 'subtitle', type: 'body', x: 0.9, y: 3.85, w: 11.5, h: 0.6, fontSize: 24, color: C.background1, margin: 0 }, text: 'Month' } },
    { placeholder: { options: { name: 'meta', type: 'body', x: 0.9, y: 4.5, w: 11.5, h: 0.5, fontSize: 16, color: C.background1, margin: 0 }, text: 'Meeting date' } },
    { text: { text: 'IAM Engineering  |  IAM Operations', options: { x: 0.9, y: 6.6, w: 8, h: 0.4, fontSize: 12, bold: true, color: C.background1, margin: 0 } } }
  ]
});

pres.defineSlideMaster({
  title: 'IAM_SECTION',
  background: { color: C.accent1 },
  objects: [
    { placeholder: { options: { name: 'title', type: 'title', align: 'left', x: 0.9, y: 2.6, w: 11.5, h: 1.2, fontSize: 44, bold: true, color: C.background1, margin: 0, valign: 'bottom' }, text: 'Section' } },
    { placeholder: { options: { name: 'body', type: 'body', x: 0.9, y: 3.95, w: 11.5, h: 0.8, fontSize: 20, color: C.background1, margin: 0, valign: 'top' }, text: 'What this section covers' } }
  ],
  slideNumber: { x: W - M - 1, y: 6.95, w: 1, h: 0.3, fontSize: 10, color: C.background1, align: 'right' }
});

pres.defineSlideMaster({
  title: 'IAM_SUMMARY',
  background: { color: C.background1 },
  objects: [teamPh(), titlePh(),
    { placeholder: { options: { name: 'body', type: 'body', x: M, y: 2.0, w: 7.3, h: 4.6, fontSize: 18, color: C.text1, valign: 'top', margin: 0 }, text: 'Key points' } },
    ...footer()],
  slideNumber: slideNum
});

pres.defineSlideMaster({
  title: 'IAM_KPI',
  background: { color: C.background1 },
  objects: [teamPh(), titlePh(),
    { placeholder: { options: { name: 'body', type: 'body', x: M, y: 5.75, w: W - 2 * M, h: 0.9, fontSize: 14, color: C.text2, valign: 'top', margin: 0 }, text: 'Commentary' } },
    ...footer()],
  slideNumber: slideNum
});

pres.defineSlideMaster({
  title: 'IAM_CHART',
  background: { color: C.background1 },
  objects: [teamPh(), titlePh(),
    { placeholder: { options: { name: 'chart', type: 'chart', x: M, y: 2.0, w: 8.0, h: 4.6, color: C.text1 }, text: '' } },
    { placeholder: { options: { name: 'body', type: 'body', x: 9.1, y: 2.0, w: 3.63, h: 4.6, fontSize: 16, color: C.text1, valign: 'top', margin: 0 }, text: 'Commentary' } },
    ...footer()],
  slideNumber: slideNum
});

pres.defineSlideMaster({
  title: 'IAM_TABLE',
  background: { color: C.background1 },
  margin: [0.5, 0.6, 0.8, 0.6],
  objects: [teamPh(), titlePh(), ...footer()],
  slideNumber: slideNum
});

pres.defineSlideMaster({
  title: 'IAM_TWO_COLUMN',
  background: { color: C.background1 },
  objects: [teamPh(), titlePh(),
    { placeholder: { options: { name: 'leftHead', type: 'body', x: M + 0.3, y: 2.2, w: 5.4, h: 0.5, fontSize: 20, bold: true, color: C.text1, margin: 0 }, text: 'Left heading' } },
    { placeholder: { options: { name: 'left', type: 'body', x: M + 0.3, y: 2.8, w: 5.4, h: 3.6, fontSize: 16, color: C.text1, valign: 'top', margin: 0 }, text: 'Left content' } },
    { placeholder: { options: { name: 'rightHead', type: 'body', x: 7.0, y: 2.2, w: 5.4, h: 0.5, fontSize: 20, bold: true, color: C.text1, margin: 0 }, text: 'Right heading' } },
    { placeholder: { options: { name: 'right', type: 'body', x: 7.0, y: 2.8, w: 5.4, h: 3.6, fontSize: 16, color: C.text1, valign: 'top', margin: 0 }, text: 'Right content' } },
    ...footer()],
  slideNumber: slideNum
});

pres.defineSlideMaster({
  title: 'IAM_CLOSING',
  background: { color: C.accent1 },
  objects: [
    { placeholder: { options: { name: 'title', type: 'title', align: 'left', x: 0.9, y: 2.4, w: 11.5, h: 1.2, fontSize: 44, bold: true, color: C.background1, margin: 0, valign: 'bottom' }, text: 'Closing' } },
    { placeholder: { options: { name: 'body', type: 'body', x: 0.9, y: 3.75, w: 11.5, h: 1.4, fontSize: 20, color: C.background1, margin: 0, valign: 'top' }, text: 'Next meeting' } }
  ]
});

// ---- helpers for composed content ----
const STATUS = {
  on: { label: 'On target', fill: 'E3F3E9', text: C.accent3 },
  amber: { label: 'Below target', fill: 'FFF3DC', text: C.accent6 },
  off: { label: 'Off target', fill: 'FDECEE', text: C.accent2 }
};

// ---- sample slides (sample data; shows each layout in use) ----
let s;

pres.addSection({ title: 'Opening' });
s = pres.addSlide({ masterName: 'IAM_TITLE', sectionTitle: 'Opening' });
s.addText('IAM Monthly Meeting', { placeholder: 'title' });
s.addText('November 2026', { placeholder: 'subtitle' });
s.addText('Thursday 26 November 2026', { placeholder: 'meta' });
s.addNotes('Layout IAM_TITLE. Filled from meeting title, month and date.');

s = pres.addSlide({ masterName: 'IAM_SUMMARY', sectionTitle: 'Opening' });
s.addText('SHARED', { placeholder: 'team' });
s.addText('Executive summary', { placeholder: 'title' });
s.addText([
  { text: 'Leaver access removal held above 99% for the third month', options: { bullet: { type: 'number' }, breakLine: true, paraSpaceAfter: 14 } },
  { text: 'Access certification campaign is behind plan; escalation needed for two business units', options: { bullet: { type: 'number' }, breakLine: true, paraSpaceAfter: 14 } },
  { text: 'JML automation phase 2 slips two weeks with no customer impact', options: { bullet: { type: 'number' } } }
], { placeholder: 'body' });
// stat callouts
[['96.4%', 'Access requests within SLA'], ['78%', 'Certification campaign complete'], ['9', 'Open action items']].forEach(([v, l], i) => {
  const y = 2.0 + i * 1.55;
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 8.4, y, w: 4.33, h: 1.35, fill: { color: C.background2 }, line: { color: C.background2 }, rectRadius: 0.08, objectName: `stat-${i + 1}` });
  s.addText(v, { x: 8.7, y: y + 0.12, w: 3.8, h: 0.7, fontSize: 36, bold: true, color: i === 1 ? C.accent1 : C.text1, margin: 0, isTextBox: true, objectName: `stat-${i + 1}-value` });
  s.addText(l, { x: 8.7, y: y + 0.82, w: 3.8, h: 0.4, fontSize: 13, color: C.text2, margin: 0, isTextBox: true, objectName: `stat-${i + 1}-label` });
});
s.addNotes('Layout IAM_SUMMARY. Section: Executive summary. Body = numbered key points; stat callouts are composed by the app.');

pres.addSection({ title: 'IAM Operations' });
s = pres.addSlide({ masterName: 'IAM_SECTION', sectionTitle: 'IAM Operations' });
s.addText('IAM Operations', { placeholder: 'title' });
s.addText('Key metrics, incidents and access reviews', { placeholder: 'body' });
s.addNotes('Layout IAM_SECTION. Divider before each team block.');

s = pres.addSlide({ masterName: 'IAM_KPI', sectionTitle: 'IAM Operations' });
s.addText('IAM OPERATIONS', { placeholder: 'team' });
s.addText('Key metrics', { placeholder: 'title' });
const kpis = [
  ['Access requests within SLA', '96.4%', 'Target ≥ 95%', '+2.3 pts vs Oct', 'on'],
  ['Mean provisioning time', '3.2 h', 'Target ≤ 4 h', '−0.7 h vs Oct', 'on'],
  ['Leaver access removed in 24 h', '99.1%', 'Target 100%', '−0.5 pts vs Oct', 'amber'],
  ['Certification campaign complete', '78%', 'Target ≥ 90%', '+17 pts vs Oct', 'off']
];
const tw = (W - 2 * M - 3 * 0.3) / 4;
kpis.forEach(([name, val, tgt, delta, st], i) => {
  const x = M + i * (tw + 0.3), y = 2.0;
  const S = STATUS[st];
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: tw, h: 3.4, fill: { color: C.background2 }, line: { color: C.background2 }, rectRadius: 0.08, objectName: `kpi-${i + 1}` });
  s.addText(name, { x: x + 0.25, y: y + 0.25, w: tw - 0.5, h: 0.75, fontSize: 14, color: C.text2, valign: 'top', margin: 0, isTextBox: true, objectName: `kpi-${i + 1}-name` });
  s.addText(val, { x: x + 0.25, y: y + 1.05, w: tw - 0.5, h: 0.9, fontSize: 44, bold: true, color: C.text1, margin: 0, isTextBox: true, objectName: `kpi-${i + 1}-value` });
  s.addText(`${tgt}\n${delta}`, { x: x + 0.25, y: y + 2.0, w: tw - 0.5, h: 0.65, fontSize: 13, color: C.text2, valign: 'top', margin: 0, isTextBox: true, objectName: `kpi-${i + 1}-target` });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: x + 0.25, y: y + 2.8, w: 1.55, h: 0.36, fill: { color: S.fill }, line: { color: S.fill }, rectRadius: 0.18, objectName: `kpi-${i + 1}-status` });
  s.addText(S.label, { x: x + 0.25, y: y + 2.8, w: 1.55, h: 0.36, fontSize: 11, bold: true, color: S.text, align: 'center', valign: 'middle', margin: 0, isTextBox: true, objectName: `kpi-${i + 1}-status-label` });
});
s.addText('Certification campaign behind plan: escalation to BU heads proposed (action 1). Leaver miss traced to one contractor batch.', { placeholder: 'body' });
s.addNotes('Layout IAM_KPI. Section: Key metrics. Up to 4 KPI tiles per slide; status computed from template targets, override reason shown in commentary.');

s = pres.addSlide({ masterName: 'IAM_CHART', sectionTitle: 'IAM Operations' });
s.addText('IAM OPERATIONS', { placeholder: 'team' });
s.addText('Access requests within SLA', { placeholder: 'title' });
s.addChart(pres.charts.LINE, [{ name: 'Within SLA (%)', labels: ['Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov'], values: [92.0, 93.6, 93.1, 94.8, 94.1, 96.4] }], {
  placeholder: 'chart',
  chartColors: [THEME.colors.accent1], lineSize: 3, lineDataSymbol: 'circle', lineDataSymbolSize: 8,
  showValue: true, dataLabelPosition: 't', dataLabelFormatCode: '0.0', dataLabelFontSize: 12, dataLabelColor: THEME.colors.dk1, dataLabelFontFace: '+mn-lt',
  valAxisMinVal: 90, valAxisMaxVal: 98, valAxisLabelColor: THEME.colors.dk2, catAxisLabelColor: THEME.colors.dk2,
  valAxisLabelFontFace: '+mn-lt', catAxisLabelFontFace: '+mn-lt', valAxisLabelFontSize: 12, catAxisLabelFontSize: 12,
  valGridLine: { color: 'E3E3E3', size: 1 }, catGridLine: { style: 'none' }, showLegend: false
});
s.addText([
  { text: 'Above the 95% target for the first time this year.', options: { breakLine: true, paraSpaceAfter: 12 } },
  { text: 'Driven by automated approvals for low-risk entitlements.' }
], { placeholder: 'body' });
s.addNotes('Layout IAM_CHART. Trend for one KPI, last 6 months.');

pres.addSection({ title: 'IAM Engineering' });
s = pres.addSlide({ masterName: 'IAM_SECTION', sectionTitle: 'IAM Engineering' });
s.addText('IAM Engineering', { placeholder: 'title' });
s.addText('Roadmap, releases, risks and decisions', { placeholder: 'body' });
s.addNotes('Layout IAM_SECTION.');

const hdr = (t) => ({ text: t, options: { bold: true, color: THEME.colors.dk2, fill: { color: 'F6F6F7' }, fontSize: 12 } });
const chip = (t, st) => ({ text: t, options: { bold: true, color: STATUS[st].text === C.accent3 ? '17643A' : STATUS[st].text === C.accent6 ? '7A4500' : '9E0B23', fontSize: 13 } });

s = pres.addSlide({ masterName: 'IAM_TABLE', sectionTitle: 'IAM Engineering' });
s.addText('IAM ENGINEERING', { placeholder: 'team' });
s.addText('Roadmap', { placeholder: 'title' });
s.addTable([
  [hdr('Initiative'), hdr('Owner'), hdr('Status'), hdr('Due')],
  ['Legacy app SSO migration, wave 3', 'IAM Engineering', chip('On track', 'on'), 'Dec 2026'],
  ['PAM vault upgrade', 'IAM Engineering', chip('On track', 'on'), 'Jan 2027'],
  ['JML automation, phase 2', 'IAM Engineering', chip('At risk', 'amber'), 'Dec 2026'],
  ['Role mining, finance apps', 'IAM Engineering', { text: 'Not started', options: { bold: true, color: '444444', fontSize: 13 } }, 'Q1 2027']
], { x: M, y: 2.0, w: W - 2 * M, colW: [5.6, 2.6, 2.0, 1.93], fontSize: 14, color: THEME.colors.dk1, fontFace: 'Arial', rowH: 0.62, valign: 'middle', border: { type: 'solid', pt: 1, color: 'E3E3E3' }, margin: [0.08, 0.15, 0.08, 0.15], objectName: 'roadmap-table' });
s.addNotes('Layout IAM_TABLE. Section: Roadmap. Same layout serves Incidents, Releases and Action items.');

s = pres.addSlide({ masterName: 'IAM_TWO_COLUMN', sectionTitle: 'IAM Engineering' });
s.addText('SHARED', { placeholder: 'team' });
s.addText('Risks and decisions', { placeholder: 'title' });
s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: M, y: 2.0, w: 5.95, h: 4.6, fill: { color: 'FDECEE' }, line: { color: 'FDECEE' }, rectRadius: 0.08, objectName: 'left-card' });
s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 6.78, y: 2.0, w: 5.95, h: 4.6, fill: { color: C.background2 }, line: { color: C.background2 }, rectRadius: 0.08, objectName: 'right-card' });
s.addText('Risks', { placeholder: 'leftHead' });
s.addText([
  { text: 'Vendor API change delays JML phase 2', options: { bullet: true, breakLine: true, paraSpaceAfter: 10 } },
  { text: 'Two BUs below 60% certification completion', options: { bullet: true, breakLine: true, paraSpaceAfter: 10 } },
  { text: 'PAM vault upgrade window clashes with year-end freeze', options: { bullet: true } }
], { placeholder: 'left' });
s.addText('Decisions needed', { placeholder: 'rightHead' });
s.addText([
  { text: 'Approve escalation of certification laggards to BU heads', options: { bullet: true, breakLine: true, paraSpaceAfter: 10 } },
  { text: 'Accept revised JML phase 2 date of 18 Dec', options: { bullet: true } }
], { placeholder: 'right' });
s.addNotes('Layout IAM_TWO_COLUMN. Sections: Risks and blockers, Decisions.');

pres.addSection({ title: 'Close' });
s = pres.addSlide({ masterName: 'IAM_TABLE', sectionTitle: 'Close' });
s.addText('SHARED', { placeholder: 'team' });
s.addText('Action items', { placeholder: 'title' });
const due = (t, late) => ({ text: t, options: { bold: late, color: late ? '9E0B23' : THEME.colors.dk1 } });
s.addTable([
  [hdr('Action'), hdr('Owner'), hdr('Team'), hdr('Due')],
  ['Close 2 orphan service accounts', 'J. Tan', 'Operations', due('31 Oct · overdue', true)],
  ['Publish SSO onboarding runbook', 'R. Lim', 'Engineering', due('30 Oct · overdue', true)],
  ['Escalate certification laggards to BU heads', 'M. Wong', 'Operations', due('14 Nov', false)],
  ['Confirm JML phase 2 revised date', 'A. Kumar', 'Engineering', due('20 Nov', false)]
], { x: M, y: 2.0, w: W - 2 * M, colW: [5.6, 2.0, 2.2, 2.33], fontSize: 14, color: THEME.colors.dk1, fontFace: 'Arial', rowH: 0.62, valign: 'middle', border: { type: 'solid', pt: 1, color: 'E3E3E3' }, margin: [0.08, 0.15, 0.08, 0.15], objectName: 'actions-table' });
s.addNotes('Layout IAM_TABLE. Section: Action items. Open items carry forward to next month.');

s = pres.addSlide({ masterName: 'IAM_CLOSING', sectionTitle: 'Close' });
s.addText('Next meeting', { placeholder: 'title' });
s.addText([
  { text: 'Thursday 17 December 2026', options: { breakLine: true, bold: true } },
  { text: 'Content cut-off: 2 days after the meeting, 23:59 SGT' }
], { placeholder: 'body' });
s.addNotes('Layout IAM_CLOSING.');

// Placeholder names per layout, in the order the layout defines them. pptxgenjs names every shape
// "Text 0"; the app finds placeholders by these names (ADR 0005), so they are written in afterwards.
const PLACEHOLDERS = {
  IAM_TITLE: ['title', 'subtitle', 'meta'],
  IAM_SECTION: ['title', 'body'],
  IAM_SUMMARY: ['team', 'title', 'body'],
  IAM_KPI: ['team', 'title', 'body'],
  IAM_CHART: ['team', 'title', 'chart', 'body'],
  IAM_TABLE: ['team', 'title'],
  IAM_TWO_COLUMN: ['team', 'title', 'leftHead', 'left', 'rightHead', 'right'],
  IAM_CLOSING: ['title', 'body']
};

async function namePlaceholders(file) {
  const fs = require('fs');
  const JSZip = require(require.resolve('jszip', { paths: [require.resolve('pptxgenjs')] }));
  const zip = await JSZip.loadAsync(fs.readFileSync(file));
  for (const path of Object.keys(zip.files).filter((p) => /^ppt\/slideLayouts\/slideLayout\d+\.xml$/.test(p))) {
    let xml = await zip.file(path).async('string');
    const layout = (xml.match(/<p:cSld name="([^"]+)"/) || [])[1];
    const names = PLACEHOLDERS[layout];
    if (!names) continue;
    let i = 0;
    // Each <p:sp> whose nvPr holds a non slide-number placeholder gets the next name.
    xml = xml.replace(/<p:sp>([\s\S]*?)<\/p:sp>/g, (sp) => {
      if (!/<p:ph\b/.test(sp) || /type="sldNum"/.test(sp)) return sp;
      const name = names[i++];
      if (!name) throw new Error(`${layout}: more placeholders than names`);
      return sp.replace(/(<p:cNvPr id="\d+" name=")[^"]*"/, `$1${name}"`);
    });
    if (i !== names.length) throw new Error(`${layout}: expected ${names.length} placeholders, found ${i}`);
    zip.file(path, xml);
  }
  fs.writeFileSync(file, await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' }));
}

(async () => {
  await pres.writeFile({ fileName: OUT });
  await applyTheme(OUT, THEME);
  await namePlaceholders(OUT);
  console.log('wrote', OUT);
})();
