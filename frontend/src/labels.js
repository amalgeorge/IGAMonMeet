import { api } from './api.js';

/** Admin-renamed KPI status names (spec-v2 §2.4). Filled in place at sign-in so every view reads the same object. */
export const STATUS_LABELS = { ON_TARGET: 'On target', BELOW_TARGET: 'Below target', OFF_TARGET: 'Off target' };

export function loadStatusLabels() {
  return api('/api/status-labels').then((l) => Object.assign(STATUS_LABELS, l)).catch(() => STATUS_LABELS);
}

/** SITREP types offered in the SITREP Type column (spec-v3 §1.3). Filled in place at sign-in. */
export const SITREP_TYPES = [];

export function loadSitrepTypes() {
  return api('/api/sitrep-types').then((t) => { SITREP_TYPES.splice(0, SITREP_TYPES.length, ...t); return SITREP_TYPES; }).catch(() => SITREP_TYPES);
}
