import { useState } from 'react';

/**
 * Drag-and-drop reordering for a list (spec-v3 §4.1), using the browser's own drag and drop: no library.
 * props(i) goes on each row; the row only becomes draggable while the pointer is down on its ".drag-handle", so
 * selecting text in the row's inputs still works. Keyboard users keep the ↑/↓ buttons.
 */
export function moveItem(list, from, to) {
  if (from === to || from < 0 || to < 0 || from >= list.length || to >= list.length) return list;
  const next = [...list];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

export function useDragSort(list, onChange) {
  const [drag, setDrag] = useState(null);
  const [over, setOver] = useState(null);
  const [armed, setArmed] = useState(null);
  const props = (i) => ({
    draggable: armed === i,
    onPointerDown: (e) => setArmed(e.target.closest?.('.drag-handle') ? i : null),
    onDragStart: (e) => { setDrag(i); e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', String(i)); },
    onDragOver: (e) => { if (drag === null) return; e.preventDefault(); e.dataTransfer.dropEffect = 'move'; if (over !== i) setOver(i); },
    onDragLeave: () => { if (over === i) setOver(null); },
    onDrop: (e) => { e.preventDefault(); if (drag !== null) onChange(moveItem(list, drag, i)); setDrag(null); setOver(null); },
    onDragEnd: () => { setDrag(null); setOver(null); setArmed(null); },
    'data-dragging': drag === i ? 'true' : undefined,
    'data-over': over === i && drag !== null && drag !== i ? (drag < i ? 'after' : 'before') : undefined,
  });
  return props;
}
