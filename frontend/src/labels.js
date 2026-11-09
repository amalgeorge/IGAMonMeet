import { api } from './api.js';

/** Admin-renamed KPI status names (spec-v2 §2.4). Filled in place at sign-in so every view reads the same object. */
export const STATUS_LABELS = { ON_TARGET: 'On target', BELOW_TARGET: 'Below target', OFF_TARGET: 'Off target' };

export function loadStatusLabels() {
  return api('/api/status-labels').then((l) => Object.assign(STATUS_LABELS, l)).catch(() => STATUS_LABELS);
}
